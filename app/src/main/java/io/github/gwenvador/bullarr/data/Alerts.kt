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
 * Notifications de Bullarr sur le téléphone, reçues en instantané par ntfy (voir [Push]) :
 * les mêmes évènements que les notifications Telegram de la page de réglages web.
 */
object Alerts {
    private const val CHANNEL = "alerts"

    private fun prefs(context: Context) = context.getSharedPreferences("alerts", Context.MODE_PRIVATE)

    /** Évènements notifiables, comme sur la page web (Configuration → Notifications) ; [telegramKey] : réglage Telegram du serveur. */
    enum class Kind(val key: String, val pushKind: String, val label: String, val detail: String, val telegramKey: String) {
        ImportCompleted(
            "imports", "import_completed", "Import effectué",
            "Quand un import (manuel ou automatique) déplace des fichiers dans la bibliothèque.", "notify_import_completed",
        ),
        ImportAvailable(
            "import_available", "import_available", "Import disponible",
            "Quand de nouveaux fichiers apparaissent dans les répertoires surveillés (nécessite l'import automatique, onglet Imports).", "notify_import_available",
        ),
    }

    fun isOn(context: Context, kind: Kind) = prefs(context).getBoolean("on_${kind.key}", true)

    fun set(context: Context, kind: Kind, on: Boolean) { prefs(context).edit().putBoolean("on_${kind.key}", on).apply() }

    private fun channel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Alertes Bullarr", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Imports effectués et fichiers disponibles pour l'import."
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
