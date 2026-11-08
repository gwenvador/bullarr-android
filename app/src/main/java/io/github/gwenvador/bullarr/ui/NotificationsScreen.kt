package io.github.gwenvador.bullarr.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.gwenvador.bullarr.data.NOTIFICATIONS
import io.github.gwenvador.bullarr.data.readable
import kotlinx.coroutines.launch
import org.json.JSONObject

private val COL_W = 76.dp

/** Tableau « Événements » du serveur : une ligne par type, une case par canal (comme la page de réglages web). */
private data class NotifEvent(val id: String, val label: String, val description: String)
private data class NotifChannel(val enabled: Boolean, val configured: Boolean, val events: Map<String, Boolean>)

/**
 * Onglet Configuration → Notifications, comme la page de réglages web : le tableau « Événements »
 * (une ligne par type, une case Telegram et une case ntfy, enregistrées aussitôt), puis les cartes
 * Telegram et ntfy. Les notifications sont envoyées par le serveur ; l'app ne fait que les régler.
 */
@Composable
fun NotificationsScreen(vm: AppViewModel, snackbar: SnackbarHostState) {
    val api = vm.api ?: return
    val telegram = remember { ConfigEditor(NOTIFICATIONS.first()) }
    val ntfy = remember { ConfigEditor(io.github.gwenvador.bullarr.data.NTFY_CHANNEL) }
    LaunchedEffect(api) {
        if (telegram.loaded == null) launch { telegram.load(api) }
        if (ntfy.loaded == null) launch { ntfy.load(api) }
    }
    var open by remember { mutableStateOf<String?>(null) }
    // Une carte enregistrée (canal activé, topic…) change l'état affiché en tête du tableau.
    val reload = remember(telegram.loaded, ntfy.loaded) { Any() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { EventsTable(vm, reload) }
        item { SectionTitle("Canaux") }
        item { ConfigCard(api, snackbar, telegram, expanded = open == "telegram", onToggle = { open = if (open == "telegram") null else "telegram" }) }
        item { ConfigCard(api, snackbar, ntfy, expanded = open == "ntfy", onToggle = { open = if (open == "ntfy") null else "ntfy" }) }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp))
}

@Composable
private fun EventsTable(vm: AppViewModel, reload: Any) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var events by remember { mutableStateOf<List<NotifEvent>?>(null) }
    val channels = remember { mutableStateMapOf<String, NotifChannel>() }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    val api = vm.api

    LaunchedEffect(api, reload) {
        api ?: return@LaunchedEffect
        try {
            val o = api.getObject("api/notifications/events")
            val list = o.optJSONArray("events")
            events = (0 until (list?.length() ?: 0)).mapNotNull { k ->
                list?.optJSONObject(k)?.let { NotifEvent(it.optString("id"), it.optString("label"), it.optString("description")) }
            }
            val ch = o.optJSONObject("channels")
            listOf("telegram", "ntfy").forEach { name ->
                val c = ch?.optJSONObject(name) ?: return@forEach
                val ev = c.optJSONObject("events")
                channels[name] = NotifChannel(
                    c.optBoolean("enabled"), c.optBoolean("configured"),
                    ev?.keys()?.asSequence()?.associateWith { ev.optBoolean(it) }.orEmpty(),
                )
            }
            error = null
        } catch (e: Exception) {
            val msg = e.readable()
            error = if ("404" in msg) "Ce serveur Bullarr n'a pas encore le tableau des notifications : mettez-le à jour." else msg
        }
    }

    fun toggle(channel: String, kind: String, value: Boolean) {
        val a = vm.api ?: return
        val before = channels[channel] ?: return
        channels[channel] = before.copy(events = before.events + (kind to value))
        busy = "$channel/$kind"
        scope.launch {
            try { a.postObject("api/notifications/events", JSONObject().put(channel, JSONObject().put(kind, value))) }
            catch (e: Exception) { channels[channel] = before; error = e.readable() }
            busy = null
        }
    }

    @Composable
    fun ChannelHead(name: String, label: String, logo: String) {
        val c = channels[name]
        Column(Modifier.width(COL_W).alpha(if (c?.enabled == true) 1f else 0.55f), horizontalAlignment = Alignment.CenterHorizontally) {
            Logo(logo, label, 20.dp, tip = false)
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Text(
                when { c == null -> ""; c.enabled -> "activé"; c.configured -> "désactivé"; else -> "à configurer" },
                style = MaterialTheme.typography.labelSmall, color = if (c?.enabled == true) cs.primary else cs.onSurfaceVariant,
            )
        }
    }

    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors()) {
        Column(Modifier.padding(vertical = 12.dp)) {
            Text("Événements", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp))
            Text(
                "Choisissez ce que chaque canal reçoit. Un canal se configure et s'active dans sa carte, plus bas.",
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp),
            )
            val list = events
            when {
                error != null && list == null -> Text(error!!, style = MaterialTheme.typography.bodySmall, color = cs.error, modifier = Modifier.padding(16.dp))
                list == null -> CircularProgressIndicator(Modifier.padding(16.dp).size(24.dp), strokeWidth = 2.dp)
                else -> {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Événement", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
                        ChannelHead("telegram", "Telegram", "telegram")
                        ChannelHead("ntfy", "ntfy", "ntfy")
                    }
                    HorizontalDivider(color = cs.outlineVariant)
                    list.forEachIndexed { i, ev ->
                        Row(
                            Modifier.fillMaxWidth().background(if (i % 2 == 1) cs.surfaceVariant.copy(alpha = 0.35f) else androidx.compose.ui.graphics.Color.Transparent)
                                .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(ev.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                if (ev.description.isNotBlank()) Text(ev.description, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                            }
                            listOf("telegram", "ntfy").forEach { name ->
                                val c = channels[name]
                                Box(Modifier.width(COL_W).alpha(if (c?.enabled == true) 1f else 0.55f), contentAlignment = Alignment.Center) {
                                    if (busy == "$name/${ev.id}") CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                    else Checkbox(checked = c?.events?.get(ev.id) == true, enabled = c != null, onCheckedChange = { toggle(name, ev.id, it) })
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = cs.outlineVariant)
                    error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = cs.error, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) }
                }
            }
        }
    }
}
