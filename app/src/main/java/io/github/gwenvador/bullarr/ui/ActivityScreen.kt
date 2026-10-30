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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.MoveToInbox
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.saveable.rememberSaveable
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
import io.github.gwenvador.bullarr.data.objects
import io.github.gwenvador.bullarr.data.int
import io.github.gwenvador.bullarr.data.str

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
    val importInfo = rememberImportInfo(vm)

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

    // Sélection multiple (appui long) : suppression groupée des téléchargements choisis.
    var selected by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var confirmBatch by remember { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(enabled = selected.isNotEmpty()) { selected = emptyList() }
    fun toggle(key: String) { selected = if (key in selected) selected - key else selected + key }
    val deleters = HashMap<String, suspend () -> Unit>()
    val trackers = HashMap<String, Int>()
    val names = HashMap<String, String>()
    var assigning by remember { mutableStateOf(false) }
    var confirmImport by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var assignVolume by remember { mutableStateOf<Int?>(null) }
    fun assign(seriesId: Int, volume: Int?) {
        val ids = selected.mapNotNull { trackers[it] }
        selected = emptyList()
        scope.launch {
            var failed: String? = null
            ids.forEach { tid ->
                try {
                    api.postObject("api/activity/update-tracking", JSONObject().put("tracking_id", tid).put("series_id", seriesId).apply { if (volume != null) put("volume_number", volume) })
                } catch (e: Exception) { failed = e.readable() }
            }
            snackbar.showSnackbar(failed ?: "${ids.size} téléchargement(s) assigné(s)")
            refresh()
        }
    }
    status?.let { st ->
        st.clients.forEach { c -> c.items.forEach { item ->
            val key = "a-${c.client}-${item.clientId ?: item.name}"
            val tid = item.trackingId
            names[key] = item.seriesTitle ?: item.name
            if (tid != null) trackers[key] = tid
            if (tid != null) deleters[key] = { api.postObject("api/activity/pending/remove", JSONObject().put("id", tid)); Unit }
            else if (item.clientId != null && c.client in removeUrls) deleters[key] = { api.postObject(removeUrls.getValue(c.client), JSONObject().put("id", item.clientId)); Unit }
        } }
        st.pending.forEach { p -> trackers["p-${p.id}"] = p.id; names["p-${p.id}"] = p.seriesTitle ?: p.title }
        st.pending.forEach { p -> deleters["p-${p.id}"] = { api.postObject("api/activity/pending/remove", JSONObject().put("id", p.id)); Unit } }
    }
    if (importing) androidx.compose.material3.AlertDialog(
        onDismissRequest = {}, confirmButton = {},
        title = { Text("Import manuel en cours") },
        text = { LinearProgressIndicator(Modifier.fillMaxWidth()) },
    )
    if (confirmImport) androidx.compose.material3.AlertDialog(
        onDismissRequest = { confirmImport = false },
        title = { Text(if (selected.count { it in trackers } > 1) "Importer ces albums ?" else "Importer cet album ?") },
        text = { Text("Les fichiers JPG seront convertis en CBZ.") },
        confirmButton = {
            TextButton(onClick = {
                confirmImport = false
                val ids = selected.mapNotNull { trackers[it] }.toSet(); selected = emptyList(); importing = true
                scope.launch {
                    val message = try { manualImport(api, ids) } catch (e: Exception) { e.readable() }
                    importing = false
                    snackbar.showSnackbar(message, duration = androidx.compose.material3.SnackbarDuration.Long)
                    refresh()
                }
            }) { Text("Importer") }
        },
        dismissButton = { TextButton(onClick = { confirmImport = false }) { Text("Annuler") } },
    )
    if (assigning) AssignSeriesDialog(vm, selected.count { it in trackers }, guessSeriesName(selected.firstOrNull()?.let { names[it] }), onDismiss = { assigning = false }, onAddNew = { adding = true }, onPick = { sid, vol -> assign(sid, vol) })
    if (adding) SeriesAddDialog(vm, onDismiss = { adding = false }, initialQuery = guessSeriesName(selected.firstOrNull()?.let { names[it] }), onAdded = { sid, _ -> assign(sid, null) })
    if (confirmBatch) androidx.compose.material3.AlertDialog(
        onDismissRequest = { confirmBatch = false },
        title = { Text("Supprimer ${selected.size} téléchargement(s) ?") },
        text = { Text("Ils sont annulés chez le client et retirés de la file de Bullarr.") },
        confirmButton = {
            TextButton(onClick = {
                confirmBatch = false
                val keys = selected; selected = emptyList()
                scope.launch {
                    var failed: String? = null
                    keys.forEach { k -> try { deleters[k]?.invoke() } catch (e: Exception) { failed = e.readable() } }
                    snackbar.showSnackbar(failed ?: "${keys.size} téléchargement(s) supprimé(s)")
                    refresh()
                }
            }) { Text("Supprimer") }
        },
        dismissButton = { TextButton(onClick = { confirmBatch = false }) { Text("Annuler") } },
    )
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
            item {
                if (selected.isEmpty()) Text("Activité", style = MaterialTheme.typography.headlineSmall)
                else Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { selected = emptyList() }) { Icon(Icons.Outlined.Close, contentDescription = "Annuler la sélection") }
                    Text("${selected.size}", style = MaterialTheme.typography.titleMedium, maxLines = 1, modifier = Modifier.weight(1f))
                    IconButton(onClick = { selected = deleters.keys.toList() }) { Icon(Icons.Outlined.SelectAll, contentDescription = "Tout sélectionner") }
                    IconButton(onClick = { confirmImport = true }, enabled = selected.any { it in trackers }) { Icon(Icons.Outlined.MoveToInbox, contentDescription = "Importer maintenant (import manuel)") }
                    IconButton(onClick = { assigning = true }, enabled = selected.any { it in trackers }) { Icon(Icons.Outlined.Link, contentDescription = "Assigner ou corriger la série") }
                    IconButton(onClick = { confirmBatch = true }) { Icon(Icons.Outlined.DeleteOutline, contentDescription = "Supprimer la sélection", tint = MaterialTheme.colorScheme.error) }
                }
            }
            importItems(importInfo)
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
                    val key = "a-${c.client}-${item.clientId ?: item.name}"
                    DownloadCard(
                        item, c.label, selected = key in selected,
                        onLongClick = { if (key in deleters) toggle(key) }, onTap = { if (selected.isNotEmpty() && key in deleters) toggle(key) },
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
                item { SectionTitle("En attente d'import", st.pending.size) }
                if (st.pending.isEmpty()) item { Text("Rien en attente.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(st.pending, key = { "p-${it.id}" }) { p ->
                    PendingCard(
                        p, problem = importInfo.blocked[p.id], selected = "p-${p.id}" in selected,
                        onLongClick = { toggle("p-${p.id}") }, onTap = { if (selected.isNotEmpty()) toggle("p-${p.id}") },
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
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun DownloadCard(item: DownloadItem, clientLabel: String, selected: Boolean, onLongClick: () -> Unit, onTap: () -> Unit, controllable: Boolean, busy: Boolean, onAction: (String) -> Unit, onDelete: (() -> Unit)?) {
    Card(
        modifier = Modifier.selectable(selected, onLongClick, onTap),
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer),
    ) {
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
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun PendingCard(p: PendingDownload, problem: String?, selected: Boolean, onLongClick: () -> Unit, onTap: () -> Unit, controllable: Boolean, busy: Boolean, onAction: (String) -> Unit, onDelete: (() -> Unit)?) {
    Card(
        modifier = Modifier.selectable(selected, onLongClick, onTap),
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    listOfNotNull(p.seriesTitle, p.volume.ifBlank { null }).joinToString(" · ").ifBlank { p.title },
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
                )
                val (label, icon, tint) = if (problem != null) Triple("Import impossible", Icons.Outlined.ErrorOutline, MaterialTheme.colorScheme.error) else statusLabel(p)
                StatusIcon(icon, label, tint, 22.dp)
            }
            Text(p.title, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            problem?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
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

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun Modifier.selectable(selected: Boolean, onLongClick: () -> Unit, onTap: () -> Unit): Modifier =
    this.combinedClickable(onClick = onTap, onLongClick = onLongClick)

/** Nom de série probable à partir d'un nom de fichier de release (« Tomka.le.gitan.FR.[JPG]-NOTAG » → « Tomka le gitan »). */
private fun guessSeriesName(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    var t = raw.replace(Regex("\\[[^]]*]|\\([^)]*\\)"), " ").replace(Regex("-[A-Za-z0-9]+$"), "")
    t = t.replace('.', ' ').replace('_', ' ')
    val noise = setOf("fr", "french", "notag", "cbz", "cbr", "pdf", "scan", "comics", "bd", "ebook", "hd", "jpg")
    return t.split(Regex("\\s+")).filter { it.isNotBlank() && it.lowercase() !in noise }.joinToString(" ").trim()
}

/**
 * Import manuel des téléchargements suivis [trackingIds] (comme la page /import du web) : les dossiers de pages
 * en vrac sont convertis en CBZ, puis les fichiers prêts sont importés (POST /api/import/execute).
 * Renvoie un résumé lisible, avec le détail de chaque échec.
 */
private suspend fun manualImport(api: io.github.gwenvador.bullarr.data.BullarrApi, trackingIds: Set<Int>): String {
    fun JSONObject.dest() = optJSONObject("destination")
    var state = api.getObject("api/import/state")
    val notes = mutableListOf<String>()
    val folders = state.optJSONArray("incompatible_folders").objects().filter { (it.int("pack_download_id") ?: -1) in trackingIds }
    for (f in folders) {
        try {
            api.postObject("api/import/incompatible-folder/convert-to-cbz", JSONObject().put("import_root", f.str("import_root") ?: "").put("relative_path", f.str("relative_path") ?: ""))
            notes += "${f.str("folder_name")} converti en CBZ"
        } catch (e: Exception) { notes += "${f.str("folder_name")} : conversion impossible (${e.readable()})" }
    }
    if (folders.isNotEmpty()) state = api.getObject("api/import/state")
    val files = state.optJSONArray("files").objects().filter { (it.dest()?.int("tracking_id") ?: -1) in trackingIds }
    val ready = files.filter { it.dest()?.int("series_id") != null }
    val unassigned = files.size - ready.size
    if (unassigned > 0) notes += "$unassigned fichier(s) sans série assignée (utilisez « Assigner la série » d'abord)"
    if (ready.isEmpty()) {
        return (listOf("Rien à importer pour cette sélection.") + notes).joinToString("\n")
    }
    val res = api.postObject("api/import/execute", JSONObject().put("files", org.json.JSONArray(ready)))
    val parts = listOfNotNull(
        res.int("imported_count")?.takeIf { it > 0 }?.let { "$it importé(s)" },
        res.int("replaced_count")?.takeIf { it > 0 }?.let { "$it remplacé(s)" },
        res.int("skipped_count")?.takeIf { it > 0 }?.let { "$it ignoré(s)" },
        res.int("failed_count")?.takeIf { it > 0 }?.let { "$it en échec" },
    )
    val failures = res.optJSONArray("failures")?.let { a -> (0 until a.length()).map { a.opt(it).toString() } }.orEmpty()
    return (listOf(parts.joinToString(" · ").ifBlank { "Import terminé." }) + failures.take(3) + notes).joinToString("\n")
}
