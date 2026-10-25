package io.github.gwenvador.bullarr.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Sync
import io.github.gwenvador.bullarr.ui.IconButton
import androidx.compose.material.icons.outlined.VerticalAlignTop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import io.github.gwenvador.bullarr.ui.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.gwenvador.bullarr.data.ActivityStatus
import io.github.gwenvador.bullarr.data.DownloadItem
import io.github.gwenvador.bullarr.data.PendingDownload
import io.github.gwenvador.bullarr.data.formatBytes
import io.github.gwenvador.bullarr.data.formatDuration
import io.github.gwenvador.bullarr.data.parseActivity
import io.github.gwenvador.bullarr.data.readable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Rejoue [block] toutes les [intervalMs] tant que l'écran est visible (pas en arrière-plan). */
@Composable
fun Polling(intervalMs: Long, key: Any? = Unit, block: suspend () -> Unit) {
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner, key) {
        owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                block()
                delay(intervalMs)
            }
        }
    }
}

private val CONTROLLABLE = setOf("qbittorrent", "amule", "telegram")

@Composable
fun ActivityScreen(vm: AppViewModel, snackbar: SnackbarHostState) {
    val api = vm.api ?: return
    var status by remember { mutableStateOf<ActivityStatus?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        try {
            status = parseActivity(api.getObject("api/activity/status"))
            error = null
        } catch (e: Exception) {
            error = e.readable()
        }
    }
    Polling(4000, api) { refresh() }

    var toDelete by remember { mutableStateOf<Pair<String, suspend () -> Unit>?>(null) }
    toDelete?.let { (label, run) ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Supprimer ce téléchargement ?") },
            text = { Text("$label\n\nIl est annulé chez le client et retiré de la file de Bullarr.") },
            confirmButton = {
                TextButton(onClick = {
                    toDelete = null
                    scope.launch {
                        try { run(); snackbar.showSnackbar("Téléchargement supprimé"); refresh() }
                        catch (e: Exception) { snackbar.showSnackbar(e.readable()) }
                    }
                }) { Text("Supprimer") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Annuler") } },
        )
    }
    // Ligne suivie par Bullarr : /pending/remove (annule aussi chez le client) ; sinon suppression directe chez le client.
    val removeUrls = mapOf("qbittorrent" to "api/qbittorrent/remove", "rtorrent" to "api/rtorrent/remove",
        "deluge" to "api/deluge/remove", "amule" to "api/emule/remove")

    fun control(trackingId: Int, action: String) {
        busy = trackingId
        scope.launch {
            try {
                api.postObject(
                    "api/activity/download-control",
                    JSONObject().put("tracking_id", trackingId).put("action", action),
                )
                snackbar.showSnackbar(
                    when (action) { "pause" -> "Mis en pause"; "resume" -> "Reprise demandée"; else -> "Passé en premier" },
                )
                refresh()
            } catch (e: Exception) {
                snackbar.showSnackbar(e.readable())
            } finally {
                busy = null
            }
        }
    }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { refreshing = true; scope.launch { refresh(); refreshing = false } },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { Text("Activité", style = MaterialTheme.typography.headlineSmall) }
            error?.let { item { ErrorCard(it, onRetry = { scope.launch { refresh() } }) } }
            val st = status
            if (st == null && error == null) item { EmptyState("Chargement…") }
            if (st != null) {
                val active = st.clients.flatMap { c -> c.items.map { c to it } }
                st.clients.filter { it.error != null }.forEach { c ->
                    item { ErrorCard("${c.label} : ${c.error}") }
                }
                item { SectionTitle("En cours", active.size) }
                if (active.isEmpty()) item { Text("Aucun téléchargement actif chez les clients.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(active, key = { "a-${it.first.client}-${it.second.clientId ?: it.second.name}" }) { (c, item) ->
                    DownloadCard(
                        item, c.label,
                        controllable = item.trackingId != null && c.client in CONTROLLABLE,
                        busy = busy == item.trackingId,
                        onAction = { a -> item.trackingId?.let { control(it, a) } },
                        onDelete = if (item.trackingId != null || (item.clientId != null && c.client in removeUrls)) ({
                            toDelete = (item.name) to suspend {
                                val tid = item.trackingId
                                if (tid != null) api.postObject("api/activity/pending/remove", JSONObject().put("id", tid))
                                else api.postObject(removeUrls.getValue(c.client), JSONObject().put("id", item.clientId))
                                Unit
                            }
                        }) else null,
                    )
                }
                item { SectionTitle("En attente d'import ou de téléchargement", st.pending.size) }
                if (st.pending.isEmpty()) item { Text("Rien en attente.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(st.pending, key = { "p-${it.id}" }) { p ->
                    PendingCard(
                        p,
                        controllable = p.status == "pending" && p.client in CONTROLLABLE &&
                            (p.client == "telegram" || p.clientItemId != null),
                        busy = busy == p.id,
                        onAction = { a -> control(p.id, a) },
                        onDelete = {
                            toDelete = p.title to suspend { api.postObject("api/activity/pending/remove", JSONObject().put("id", p.id)); Unit }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, count: Int) {
    Text(
        "$text ($count)", style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 8.dp),
    )
}

private fun isPausedState(state: String?): Boolean {
    val s = state?.lowercase() ?: return false
    return "paus" in s || "stop" in s
}

@Composable
private fun DownloadCard(item: DownloadItem, clientLabel: String, controllable: Boolean, busy: Boolean, onAction: (String) -> Unit, onDelete: (() -> Unit)?) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                listOfNotNull(item.seriesTitle, item.volume.ifBlank { null }).joinToString(" · ").ifBlank { item.name },
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
            )
            Text(item.name, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            LinearProgressIndicator(progress = { (item.progress / 100.0).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            val parts = listOfNotNull(
                String.format(java.util.Locale.FRANCE, "%.1f %%", item.progress),
                if (item.sizeBytes != null) "${formatBytes(item.downloadedBytes) ?: "0"} / ${formatBytes(item.sizeBytes)}" else null,
                item.speed?.takeIf { it > 0 }?.let { "${formatBytes(it)}/s" },
                formatDuration(item.eta)?.let { "reste $it" },
                item.state,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Logo(logoKey(clientLabel), clientLabel, 20.dp)
                if (!hasLogo(logoKey(clientLabel))) Text(clientLabel, style = MaterialTheme.typography.bodySmall)
                Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
            }
            if (controllable || onDelete != null) {
                Controls(paused = isPausedState(item.state), busy = busy, onAction = onAction, controllable = controllable, onDelete = onDelete)
            }
        }
    }
}

@Composable
private fun PendingCard(p: PendingDownload, controllable: Boolean, busy: Boolean, onAction: (String) -> Unit, onDelete: (() -> Unit)?) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    listOfNotNull(p.seriesTitle, p.volume.ifBlank { null }).joinToString(" · ").ifBlank { p.title },
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
                )
                val (label, icon, tint) = statusLabel(p)
                StatusIcon(icon, label, tint, 22.dp)
            }
            Text(p.title, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            val total = p.bytesTotal
            if (p.status == "pending" && total != null && total > 0) {
                val done = (p.bytesDownloaded ?: 0L).toFloat() / total
                LinearProgressIndicator(progress = { done.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            }
            val parts = listOfNotNull(
                if (total != null && total > 0) "${formatBytes(p.bytesDownloaded) ?: "0"} / ${formatBytes(total)}" else null,
                if (p.isPack) "pack" else null,
                when (p.localCopy) { "present" -> "fichiers arrivés"; "settling" -> "copie en cours"; "absent" -> "fichiers pas encore là"; else -> null },
                if (p.exhausted) "tentatives épuisées" else null,
                p.createdAt?.take(16),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                p.client?.let { c ->
                    Logo(logoKey(c), c, 20.dp)
                    if (!hasLogo(logoKey(c))) Text(c, style = MaterialTheme.typography.bodySmall)
                }
                Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
            }
            Controls(paused = p.paused, busy = busy, onAction = onAction, controllable = controllable, onDelete = onDelete)
        }
    }
}

@Composable
private fun statusLabel(p: PendingDownload): Triple<String, ImageVector, androidx.compose.ui.graphics.Color> {
    val cs = MaterialTheme.colorScheme
    return when {
        p.paused -> Triple("En pause", Icons.Outlined.Pause, cs.tertiary)
        p.status == "completed" -> Triple("Prêt à importer", Icons.Outlined.CheckCircle, cs.secondary)
        p.status == "importing" -> Triple("Import en cours", Icons.Outlined.Sync, cs.primary)
        else -> Triple("En attente", Icons.Outlined.Schedule, cs.onSurfaceVariant)
    }
}

@Composable
private fun Controls(paused: Boolean, busy: Boolean, onAction: (String) -> Unit, controllable: Boolean, onDelete: (() -> Unit)?) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (controllable) {
        if (paused) ActionButton("Reprendre", Icons.Outlined.PlayArrow, busy) { onAction("resume") }
        else ActionButton("Pause", Icons.Outlined.Pause, busy) { onAction("pause") }
        ActionButton("En premier", Icons.Outlined.VerticalAlignTop, busy) { onAction("top") }
        if (!paused) {
            // L'état exact n'est pas toujours connu (aMule, Telegram) : la reprise reste accessible.
            ActionButton("Reprendre", Icons.Outlined.PlayArrow, busy) { onAction("resume") }
        }
        }
        if (onDelete != null) IconButton(onClick = onDelete, enabled = !busy) {
            Icon(Icons.Outlined.DeleteOutline, contentDescription = "Supprimer", tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun ActionButton(label: String, icon: ImageVector, busy: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = !busy) { Icon(icon, contentDescription = label) }
}
