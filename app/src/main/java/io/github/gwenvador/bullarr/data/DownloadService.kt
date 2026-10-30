package io.github.gwenvador.bullarr.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.compose.runtime.snapshotFlow
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.github.gwenvador.bullarr.MainActivity
import io.github.gwenvador.bullarr.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Service au premier plan qui garde les téléchargements hors ligne en vie écran éteint : sans lui,
 * Android gèle l'app en arrière-plan. Il tient le processeur et le Wi-Fi éveillés, affiche
 * l'avancement dans une notification et s'arrête dès qu'il n'y a plus rien à télécharger.
 */
class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watcher: Job? = null
    private var wake: PowerManager.WakeLock? = null
    private var wifi: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val mgr = manager
        if (intent?.action == ACTION_PAUSE) { mgr?.active?.keys?.toList()?.forEach { mgr.pause(it) }; return START_NOT_STICKY }
        if (intent?.action == ACTION_RESUME) { mgr?.active?.filter { it.value.paused }?.keys?.toList()?.forEach { mgr.resume(it) }; return START_NOT_STICKY }
        if (intent?.action == ACTION_STOP) {
            mgr?.active?.keys?.toList()?.forEach { mgr.cancel(it) }
            stopSelf()
            return START_NOT_STICKY
        }
        if (mgr == null) { stopSelf(); return START_NOT_STICKY }
        createChannel()
        val first = build(mgr.running())
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF_ID, first, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(NOTIF_ID, first)
        acquireLocks()
        if (watcher == null) watcher = scope.launch {
            snapshotFlow { mgr.running() }.collect { st ->
                if (st.count + st.paused == 0) { stopSelf() } else {
                    if (st.count == 0) releaseLocks() else acquireLocks()
                    getSystemService(NotificationManager::class.java).notify(NOTIF_ID, build(st))
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun build(st: Running): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val pause = PendingIntent.getService(this, 2, Intent(this, DownloadService::class.java).setAction(ACTION_PAUSE), PendingIntent.FLAG_IMMUTABLE)
        val resume = PendingIntent.getService(this, 3, Intent(this, DownloadService::class.java).setAction(ACTION_RESUME), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, DownloadService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(when {
                st.count == 0 -> "Téléchargement en pause"
                st.count > 1 -> "${st.count} albums en cours de téléchargement"
                else -> "Téléchargement hors ligne"
            })
            .setContentText(if (st.total > 0) "${st.done} / ${st.total} pages" else "Préparation…")
            .setProgress(st.total, st.done, st.total == 0 && st.count > 0)
            .setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
            .setContentIntent(open)
            .addAction(0, if (st.count == 0) "Reprendre" else "Pause", if (st.count == 0) resume else pause)
            .addAction(0, "Arrêter", stop)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "Téléchargements hors ligne", NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun acquireLocks() {
        if (wake == null) wake = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "bullarr:download").apply { acquire(6 * 60 * 60 * 1000L) }
        if (wifi == null) wifi = (applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
            .createWifiLock(if (Build.VERSION.SDK_INT >= 29) WifiManager.WIFI_MODE_FULL_LOW_LATENCY else WifiManager.WIFI_MODE_FULL_HIGH_PERF, "bullarr:download")
            .apply { acquire() }
    }

    @Suppress("DEPRECATION")
    private fun releaseLocks() {
        runCatching { wake?.takeIf { it.isHeld }?.release() }; wake = null
        runCatching { wifi?.takeIf { it.isHeld }?.release() }; wifi = null
    }

    override fun onTimeout(startId: Int, fgsType: Int) { stopSelf() }

    override fun onDestroy() {
        watcher?.cancel(); scope.cancel()
        releaseLocks()
        super.onDestroy()
    }

    data class Running(val count: Int, val done: Int, val total: Int, val paused: Int = 0)

    companion object {
        private const val CHANNEL = "downloads"
        private const val NOTIF_ID = 42
        private const val ACTION_PAUSE = "io.github.gwenvador.bullarr.PAUSE_DOWNLOADS"
        private const val ACTION_RESUME = "io.github.gwenvador.bullarr.RESUME_DOWNLOADS"
        private const val ACTION_STOP = "io.github.gwenvador.bullarr.STOP_DOWNLOADS"
        @Volatile var manager: OfflineManager? = null

        private fun OfflineManager.running(): Running {
            val r = active.values.filter { it.error == null && !it.paused }
            val p = active.values.filter { it.error == null && it.paused }
            return Running(r.size, (r + p).sumOf { it.done }, (r + p).sumOf { it.total }, p.size)
        }

        /** À appeler quand un téléchargement démarre (app au premier plan). */
        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java)) }
        }
    }
}
