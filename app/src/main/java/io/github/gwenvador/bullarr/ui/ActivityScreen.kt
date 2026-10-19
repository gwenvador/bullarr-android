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
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.VerticalAlignTop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
private fun DownloadCard(item: DownloadItem, clientLabel: String, controllable: Boolean, busy: Boolean, onAction: (String) -> Unit) {
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
                clientLabel,
            )
            Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
            if (controllable) {
                Controls(paused = isPausedState(item.state), busy = busy, onAction = onAction)
            }
        }
    }
}

@Composable
private fun PendingCard(p: PendingDownload, controllable: Boolean, busy: Boolean, onAction: (String) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    listOfNotNull(p.seriesTitle, p.volume.ifBlank { null }).joinToString(" · ").ifBlank { p.title },
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
                )
                val (label, tone) = statusLabel(p)
                Tag(label, tone.first, tone.second)
            }
            Text(p.title, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            val total = p.bytesTotal
            if (p.status == "pending" && total != null && total > 0) {
                val done = (p.bytesDownloaded ?: 0L).toFloat() / total
                LinearProgressIndicator(progress = { done.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            }
            val parts = listOfNotNull(
                p.client,
                if (total != null && total > 0) "${formatBytes(p.bytesDownloaded) ?: "0"} / ${formatBytes(total)}" else null,
                if (p.isPack) "pack" else null,
                when (p.localCopy) { "present" -> "fichiers arrivés"; "settling" -> "copie en cours"; "absent" -> "fichiers pas encore là"; else -> null },
                if (p.exhausted) "tentatives épuisées" else null,
                p.createdAt?.take(16),
            )
            Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
            if (controllable) Controls(paused = p.paused, busy = busy, onAction = onAction)
        }
    }
}

@Composable
private fun statusLabel(p: PendingDownload): Pair<String, Pair<androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color>> {
    val cs = MaterialTheme.colorScheme
    return when {
        p.paused -> "En pause" to (cs.tertiaryContainer to cs.onTertiaryContainer)
        p.status == "completed" -> "Prêt à importer" to (cs.secondaryContainer to cs.onSecondaryContainer)
        p.status == "importing" -> "Import…" to (cs.primaryContainer to cs.onPrimaryContainer)
        else -> "En attente" to (cs.surfaceVariant to cs.onSurfaceVariant)
    }
}

@Composable
private fun Controls(paused: Boolean, busy: Boolean, onAction: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (paused) ActionButton("Reprendre", Icons.Outlined.PlayArrow, busy) { onAction("resume") }
        else ActionButton("Pause", Icons.Outlined.Pause, busy) { onAction("pause") }
        ActionButton("En premier", Icons.Outlined.VerticalAlignTop, busy) { onAction("top") }
        if (!paused) {
            // L'état exact n'est pas toujours connu (aMule, Telegram) : la reprise reste accessible.
            ActionButton("Reprendre", Icons.Outlined.PlayArrow, busy) { onAction("resume") }
        }
    }
}

@Composable
private fun ActionButton(label: String, icon: ImageVector, busy: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = !busy) {
        Icon(icon, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
        Text(label)
    }
}
