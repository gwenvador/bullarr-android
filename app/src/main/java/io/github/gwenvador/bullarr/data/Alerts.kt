package io.github.gwenvador.bullarr.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.gwenvador.bullarr.MainActivity
import io.github.gwenvador.bullarr.R
import java.util.concurrent.TimeUnit

/** Client Bullarr d'après les réglages enregistrés (clé API, identifiant ou session SSO) ; null si rien n'est configuré. */
fun apiFromSettings(settings: Settings): BullarrApi? =
    if (!settings.isConfigured) null
    else runCatching {
        if (settings.authMode == "key") BullarrApi(settings.serverUrl, settings.apiKey)
        else BullarrApi(settings.serverUrl, "", SessionAuth(
            settings.sessionCookie,
            if (settings.authMode == "password") settings.username else "",
            if (settings.authMode == "password") settings.password else "",
        ) { settings.sessionCookie = it })
    }.getOrNull()

/**
 * Alertes du téléphone, application fermée : imports terminés, validations en attente, nouveautés.
 * Une vérification toutes les 30 minutes environ (WorkManager), seulement avec du réseau.
 */
object Alerts {
    private const val WORK = "bullarr-alerts"
    private const val CHANNEL = "alerts"

    private fun prefs(context: Context) = context.getSharedPreferences("alerts", Context.MODE_PRIVATE)

    enum class Kind(val key: String, val label: String, val detail: String) {
        Imports("imports", "Imports terminés", "Un album vient d'être importé dans la bibliothèque."),
        Reviews("reviews", "Validations en attente", "La recherche automatique attend un choix manuel."),
        News("news", "Nouveautés", "De nouvelles sorties sont arrivées (RSS, Telegram, EBDZ)."),
    }

    fun isOn(context: Context, kind: Kind) = prefs(context).getBoolean("on_${kind.key}", kind != Kind.News)

    fun set(context: Context, kind: Kind, on: Boolean) {
        prefs(context).edit().putBoolean("on_${kind.key}", on).apply()
        schedule(context)
    }

    /** Programme (ou annule) la vérification périodique selon les alertes activées. */
    fun schedule(context: Context) {
        val wm = WorkManager.getInstance(context)
        if (Kind.entries.none { isOn(context, it) }) { wm.cancelUniqueWork(WORK); return }
        val request = PeriodicWorkRequestBuilder<AlertsWorker>(30, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private fun channel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Alertes Bullarr", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Imports terminés, validations en attente et nouveautés, application fermée."
            },
        )
    }

    private fun notify(context: Context, id: Int, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        channel(context)
        val open = PendingIntent.getActivity(
            context, id, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, n) }
    }

    /** Une vérification : compare au dernier état vu et prévient de ce qui est nouveau. La première fois, mémorise sans prévenir. */
    suspend fun check(context: Context) {
        val api = apiFromSettings(Settings(context)) ?: return
        val p = prefs(context)

        if (isOn(context, Kind.Imports)) runCatching {
            val history = api.getObject("api/import/history", mapOf("limit" to "20")).optJSONArray("history").objects()
            val last = p.getInt("last_import_id", -1)
            val maxId = history.maxOfOrNull { it.int("id") ?: 0 } ?: 0
            if (last >= 0) {
                val fresh = history.filter { (it.int("id") ?: 0) > last && (it.int("files_imported") ?: 0) + (it.int("files_replaced") ?: 0) > 0 }
                if (fresh.isNotEmpty()) {
                    val count = fresh.sumOf { (it.int("files_imported") ?: 0) + (it.int("files_replaced") ?: 0) }
                    val names = fresh.mapNotNull { it.str("file_names") }.joinToString(" · ").take(300)
                    notify(context, 1001, if (count > 1) "$count albums importés" else "Album importé", names.ifBlank { "Bibliothèque mise à jour." })
                }
            }
            if (maxId > last) p.edit().putInt("last_import_id", maxId).apply()
        }

        if (isOn(context, Kind.Reviews)) runCatching {
            val count = api.getObject("api/auto-acquire/reviews/count").int("count") ?: 0
            val last = p.getInt("last_reviews", -1)
            if (last >= 0 && count > last) {
                notify(context, 1002, "Validation en attente", if (count > 1) "$count choix attendent une décision dans Suivi → Validation." else "Un choix attend une décision dans Suivi → Validation.")
            }
            p.edit().putInt("last_reviews", count).apply()
        }

        if (isOn(context, Kind.News)) runCatching {
            // Même repère que la pastille de l'app : nouveautés depuis la dernière visite de l'onglet.
            val since = context.getSharedPreferences("badges", Context.MODE_PRIVATE).getString("newsSeen", null) ?: return@runCatching
            val count = api.getObject("api/ebdz/nouveautes/new-count", mapOf("since" to since)).int("count") ?: 0
            val last = p.getInt("last_news", -1)
            if (last >= 0 && count > last) {
                notify(context, 1003, "Nouveautés", "$count nouvelle(s) sortie(s) depuis votre dernière visite.")
            }
            p.edit().putInt("last_news", count).apply()
        }
    }
}

class AlertsWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runCatching { Alerts.check(applicationContext) }
        return Result.success()
    }
}
