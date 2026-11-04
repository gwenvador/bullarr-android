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
import io.github.gwenvador.bullarr.MainActivity
import io.github.gwenvador.bullarr.R

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
 * Notifications de Bullarr sur le téléphone, reçues en instantané par ntfy (UnifiedPush, voir [Push]).
 */
object Alerts {
    private const val CHANNEL = "alerts"

    private fun channel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Alertes Bullarr", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Évènements cochés dans Configuration → Notifications (imports, surveillance, mises à jour…)."
            },
        )
    }

    /** Affiche une notification (messages reçus par ntfy, voir [Push]). */
    fun show(context: Context, id: Int, title: String, text: String) {
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
}
