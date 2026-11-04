package io.github.gwenvador.bullarr.data

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

/**
 * Notifications instantanées par UnifiedPush : un distributeur installé sur le téléphone (l'app
 * ntfy, en général) donne à Bullarr une adresse personnelle ; l'app l'enregistre auprès du serveur
 * (POST /api/push/register), qui y envoie chaque évènement. Le distributeur le remet aussitôt,
 * même application fermée, sans que le téléphone interroge le serveur.
 *
 * Protocole UnifiedPush implémenté directement (intents Android), sans bibliothèque.
 */
object Push {
    private const val REGISTER = "org.unifiedpush.android.distributor.REGISTER"
    private const val UNREGISTER = "org.unifiedpush.android.distributor.UNREGISTER"
    const val NEW_ENDPOINT = "org.unifiedpush.android.connector.NEW_ENDPOINT"
    const val MESSAGE = "org.unifiedpush.android.connector.MESSAGE"
    const val UNREGISTERED = "org.unifiedpush.android.connector.UNREGISTERED"
    const val REGISTRATION_FAILED = "org.unifiedpush.android.connector.REGISTRATION_FAILED"

    /** Lien d'installation de ntfy (distributeur UnifiedPush libre). */
    const val NTFY_URL = "https://f-droid.org/packages/io.heckel.ntfy/"

    /** Change à chaque évolution de l'état : l'écran de réglage se redessine. */
    var version by mutableIntStateOf(0)
        private set

    private fun prefs(context: Context) = context.getSharedPreferences("push", Context.MODE_PRIVATE)

    /** Distributeurs connus, retenus même si Android ne liste pas leur récepteur (filtre de visibilité, version). */
    private val KNOWN = listOf("io.heckel.ntfy", "org.unifiedpush.distributor.nextpush", "org.unifiedpush.distributor.sunup")

    /** Distributeurs UnifiedPush installés (paquet, nom affiché) ; ntfy en premier. */
    fun distributors(context: Context): List<Pair<String, String>> {
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val found = runCatching { pm.queryBroadcastReceivers(Intent(REGISTER), 0).map { it.activityInfo.packageName } }.getOrDefault(emptyList())
        @Suppress("DEPRECATION")
        val known = KNOWN.filter { runCatching { pm.getApplicationInfo(it, 0) }.isSuccess }
        return (known + found)
            .filter { it != context.packageName }
            .distinct()
            .map { pkg -> pkg to runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg) }
    }

    fun enabled(context: Context) = prefs(context).getBoolean("enabled", false)
    fun endpoint(context: Context): String? = prefs(context).getString("endpoint", null)
    fun distributor(context: Context): String? = prefs(context).getString("distributor", null)
    fun error(context: Context): String? = prefs(context).getString("error", null)
    internal fun token(context: Context): String? = prefs(context).getString("token", null)

    /** Abonnement actif : le serveur a accepté l'adresse du téléphone. */
    fun active(context: Context) = enabled(context) && endpoint(context) != null && prefs(context).getBoolean("registered", false)

    private fun authIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(context, 0, Intent("org.unifiedpush.dummy_app"), PendingIntent.FLAG_IMMUTABLE)

    /** Demande une adresse au distributeur ; la réponse arrive dans [PushReceiver]. */
    fun enable(context: Context, distributor: String) {
        val token = token(context) ?: UUID.randomUUID().toString()
        prefs(context).edit().putBoolean("enabled", true).putString("distributor", distributor).putString("token", token)
            .putBoolean("registered", false).remove("error").apply()
        context.sendBroadcast(
            Intent(REGISTER).setPackage(distributor)
                .putExtra("token", token)
                .putExtra("application", context.packageName)
                .putExtra("message", "Notifications de Bullarr")
                .putExtra("pi", authIntent(context)),
        )
        version++
    }

    fun disable(context: Context) {
        val p = prefs(context)
        val endpoint = endpoint(context)
        val distributor = distributor(context)
        val token = token(context)
        if (distributor != null && token != null) runCatching {
            context.sendBroadcast(Intent(UNREGISTER).setPackage(distributor).putExtra("token", token).putExtra("pi", authIntent(context)))
        }
        p.edit().clear().apply()
        version++
        if (endpoint != null) CoroutineScope(Dispatchers.IO).launch {
            runCatching { apiFromSettings(Settings(context))?.postObject("api/push/unregister", JSONObject().put("endpoint", endpoint)) }
        }
    }

    internal fun onEndpoint(context: Context, endpoint: String): String? {
        val previous = endpoint(context)
        prefs(context).edit().putString("endpoint", endpoint).putBoolean("registered", false).remove("error").apply()
        version++
        return previous?.takeIf { it != endpoint }
    }

    internal fun onLost(context: Context, reason: String?) {
        prefs(context).edit().remove("endpoint").putBoolean("registered", false).apply { if (reason != null) putString("error", reason) }.apply()
        version++
    }

    /** Enregistre l'adresse du téléphone auprès du serveur Bullarr (aussi au démarrage et après une reconnexion). */
    suspend fun syncServer(context: Context, previous: String? = null): Boolean {
        if (!enabled(context)) return false
        val endpoint = endpoint(context) ?: return false
        val api = apiFromSettings(Settings(context)) ?: return false
        val ok = try {
            api.postObject(
                "api/push/register",
                JSONObject().put("endpoint", endpoint).put("device", "${Build.MANUFACTURER} ${Build.MODEL}".trim())
                    .apply { if (previous != null) put("previous_endpoint", previous) },
            )
            prefs(context).edit().putBoolean("registered", true).remove("error").apply()
            true
        } catch (e: Exception) {
            val msg = e.readable()
            prefs(context).edit().putBoolean("registered", false).putString(
                "error",
                if ("404" in msg) "Le serveur Bullarr ne connaît pas encore les notifications instantanées : mettez-le à jour." else msg,
            ).apply()
            false
        }
        version++
        return ok
    }

    /** Message reçu ({kind, title, body}) : affiché tel quel (import, surveillance, validation, mise à jour, test). */
    internal fun onMessage(context: Context, raw: ByteArray) {
        val o = runCatching { JSONObject(String(raw, Charsets.UTF_8)) }.getOrNull() ?: return
        val id = 2000 + ((System.currentTimeMillis() / 1000) % 100_000).toInt()
        Alerts.show(context, id, o.optString("title").ifBlank { "Bullarr" }, o.optString("body"))
    }
}

/** Réception des messages du distributeur UnifiedPush (ntfy). Le jeton secret écarte les messages d'autres apps. */
class PushReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val token = intent.getStringExtra("token")
        if (token == null || token != Push.token(context)) return
        when (intent.action) {
            Push.NEW_ENDPOINT -> {
                val endpoint = intent.getStringExtra("endpoint") ?: return
                val previous = Push.onEndpoint(context, endpoint)
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try { Push.syncServer(context.applicationContext, previous) } finally { pending.finish() }
                }
            }
            Push.MESSAGE -> {
                val raw = intent.getByteArrayExtra("bytesMessage") ?: intent.getStringExtra("message")?.toByteArray() ?: return
                Push.onMessage(context, raw)
            }
            Push.UNREGISTERED -> Push.onLost(context, "Le distributeur a retiré l'abonnement.")
            Push.REGISTRATION_FAILED -> Push.onLost(context, intent.getStringExtra("message") ?: "Le distributeur a refusé l'abonnement.")
        }
    }
}
