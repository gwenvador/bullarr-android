package io.github.gwenvador.bullarr.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.height
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import io.github.gwenvador.bullarr.ui.Icon
import io.github.gwenvador.bullarr.ui.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.gwenvador.bullarr.data.DownloadJob
import io.github.gwenvador.bullarr.data.SEARCH_SOURCES
import io.github.gwenvador.bullarr.data.SearchResult
import io.github.gwenvador.bullarr.data.Tracking
import io.github.gwenvador.bullarr.data.formatBytes
import io.github.gwenvador.bullarr.data.str

/** Résultat de recherche → fichier à envoyer ; null pour Anna's Archive (lien seulement). */
fun SearchResult.toJob(ctx: SearchContext?): DownloadJob? {
    // Tome transmis pour le suivi : celui du résultat, sinon celui de la fiche (sauf intégrale/HS).
    val volume = volumeNumber ?: ctx?.takeIf { !it.isIntegral && !it.isHs && !it.isEpisode }?.volumeNumber
    val t = Tracking(ctx?.seriesId, ctx?.volumeId, volume, sourceLink)
    return when (source) {
        "prowlarr" -> (raw.str("download_url") ?: raw.str("link"))?.let { DownloadJob.Torrent(it, name, t) }
        "ebdz" -> raw.str("link")?.let { DownloadJob.Ed2k(it, name, t) }
        "telegram" -> {
            val ch = raw.str("channel"); val id = raw.str("message_id")?.toLongOrNull()
            if (ch != null && id != null) DownloadJob.Telegram(ch, id, raw.str("channel_title"), name, t) else null
        }
        "fourtoutici" -> raw.str("file_id")?.let { DownloadJob.Fourtoutici(it, name, t) }
        "vk" -> (raw.str("download_url") ?: raw.str("link"))?.let { DownloadJob.Vk(it, name, t) }
        else -> null
    }
}

@OptIn(ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SearchScreen(vm: AppViewModel) {
    vm.api ?: return
    val s = vm.search
    val dl = vm.downloads
    val focus = LocalFocusManager.current
    val uri = LocalUriHandler.current
    // Tous les résultats en une liste, ou groupés par source (repliables).
    var table by rememberSaveable { mutableStateOf(true) }
    // Sources masquées (appui sur leur puce) : leurs résultats disparaissent, sans rien relancer.
    val hidden = remember { mutableStateMapOf<String, Boolean>() }
    var sortKey by rememberSaveable { mutableStateOf<String?>(null) }
    var sortDesc by rememberSaveable { mutableStateOf(true) }
    var sortMenu by remember { mutableStateOf(false) }
    var formatFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var onlyConfirmed by rememberSaveable { mutableStateOf(false) }
    fun sourceAction(r: SearchResult): (() -> Unit)? = r.sourceLink?.let { link -> { runCatching { uri.openUri(link) } } }
    fun downloadFor(r: SearchResult, j: DownloadJob) {
        val ctx = s.context
        val vol = if (r.volumeNumber != null) r.volumeLabel else ctx?.volumeLabel
        dl.request(j, ctx?.let { "Rattaché à ${it.seriesTitle}" + (vol?.let { v -> " · $v" } ?: "") }, r.unconfirmed)
    }
    fun keep(r: SearchResult) = (formatFilter == null || r.format == formatFilter) && (!onlyConfirmed || r.unconfirmed == null)
    val loading = s.sources.values.any { it == SourceState.Loading }
    val all = SEARCH_SOURCES.keys.filter { hidden[it] != true }.flatMap { key -> (s.sources[key] as? SourceState.Done)?.results.orEmpty() }
    val formats = all.mapNotNull { it.format }.distinct().sorted()
    val rows = sortResults(all.filter { keep(it) }, sortKey, sortDesc)

    Column(Modifier.fillMaxSize()) {
        // En-tête fixe, comme la bibliothèque : champ de recherche, tri, ajout de série.
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Retour à l'album recherché (recherche lancée depuis une fiche).
                s.context?.let { ctx ->
                    IconButton(onClick = { vm.section = Section.Library; vm.openSeriesById(ctx.seriesId, ctx.seriesTitle) }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Retour à l'album")
                    }
                }
                CompactSearchField(
                    value = s.title, onValueChange = { s.title = it },
                    placeholder = "Série ou album…",
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); if (s.title.isNotBlank()) s.search() }),
                    modifier = Modifier.weight(1f),
                )
                CompactSearchField(
                    value = s.volume, onValueChange = { v -> s.volume = v.filter { it.isDigit() }.take(4) },
                    placeholder = "Tome", showIcon = false,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); if (s.title.isNotBlank()) s.search() }),
                    modifier = Modifier.width(72.dp),
                )
                Box {
                    IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = "Trier") }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        SEARCH_SORTS.forEach { k ->
                            DropdownMenuItem(
                                text = { Text(k) },
                                trailingIcon = {
                                    if (sortKey == k) Icon(
                                        if (sortDesc) Icons.Outlined.ArrowDownward else Icons.Outlined.ArrowUpward,
                                        contentDescription = if (sortDesc) "Décroissant" else "Croissant",
                                    )
                                },
                                onClick = { if (sortKey == k) sortDesc = !sortDesc else { sortKey = k; sortDesc = true }; sortMenu = false },
                            )
                        }
                        if (sortKey != null) DropdownMenuItem(text = { Text("Ordre par défaut") }, onClick = { sortKey = null; sortMenu = false })
                    }
                }
            }
            // Sources : appui pour afficher / masquer leurs résultats, appui long pour les activer / désactiver.
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SEARCH_SOURCES.forEach { (key, label) ->
                    val st = s.sources[key]
                    SourceChip(
                        key, label,
                        count = (st as? SourceState.Done)?.results?.count { keep(it) },
                        loading = st == SourceState.Loading, failed = st is SourceState.Failed,
                        enabled = s.enabledSources[key] == true, shown = hidden[key] != true,
                        onClick = {
                            val allVisible = SEARCH_SOURCES.keys.all { hidden[it] != true }
                            val onlyThis = SEARCH_SOURCES.keys.all { it == key || hidden[it] == true }
                            when {
                                allVisible -> SEARCH_SOURCES.keys.forEach { hidden[it] = it != key }
                                onlyThis -> SEARCH_SOURCES.keys.forEach { hidden[it] = false }
                                else -> hidden[key] = hidden[key] != true
                            }
                        },
                        onLongClick = { s.enabledSources[key] = s.enabledSources[key] != true },
                    )
                }
            }
            // Filtres : mêmes puces que la bibliothèque.
            if (s.lastQuery != null) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !table, onClick = { table = !table }, label = { Text("Par source") })
                FilterChip(selected = onlyConfirmed, onClick = { onlyConfirmed = !onlyConfirmed }, label = { Text("Tome confirmé") })
                formats.forEach { f -> FilterChip(selected = formatFilter == f, onClick = { formatFilter = if (formatFilter == f) null else f }, label = { Text(f) }) }
            }
            s.lastQuery?.let { q ->
                Text(
                    "« $q » · ${rows.size} résultat(s)" + (s.context?.let { " · ${it.seriesTitle}" } ?: ""),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
            if (s.lastQuery == null) item {
                EmptyState("Cherchez un album", "Sur Prowlarr, EBDZ, Telegram, Fourtoutici, VK et Anna's Archive. Depuis une fiche série, touchez un tome pour le chercher.")
            }
            s.sources.forEach { (key, st) ->
                if (st is SourceState.Failed && hidden[key] != true) item(key = "e-$key") {
                    ErrorCard("${SEARCH_SOURCES[key] ?: key} : ${st.message}", Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                }
            }
            if (table) {
                items(rows.withIndex().toList(), key = { "t-${it.index}" }) { (_, r) ->
                    val job = r.toJob(s.context)
                    ResultRow(r, sentTo = job?.let { dl.sent[it.key] }, onDownload = job?.let { j -> { downloadFor(r, j) } }, onOpen = sourceAction(r))
                    HorizontalDivider(Modifier.padding(start = 68.dp), color = MaterialTheme.colorScheme.outlineVariant)
                }
                if (rows.isEmpty() && s.lastQuery != null && !loading) item { EmptyState("Aucun résultat", if (formatFilter != null || onlyConfirmed) "avec ces filtres" else null) }
            } else SEARCH_SOURCES.forEach { (key, label) ->
                if (hidden[key] == true) return@forEach
                val state = s.sources[key] as? SourceState.Done ?: return@forEach
                val list = sortResults(state.results.filter { keep(it) }, sortKey, sortDesc)
                val closed = s.collapsed[key] != false
                item(key = "h-$key") {
                    Row(
                        Modifier.fillMaxWidth().clickable { s.collapsed[key] = !closed }.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Logo(key, label, 22.dp, Modifier.padding(end = 12.dp), tip = false)
                        Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text("${list.size}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Icon(
                            if (closed) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess,
                            contentDescription = if (closed) "Déplier $label" else "Replier $label",
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                if (!closed) items(list.withIndex().toList(), key = { "r-$key-${it.index}" }) { (_, r) ->
                    val job = r.toJob(s.context)
                    ResultRow(r, sentTo = job?.let { dl.sent[it.key] }, onDownload = job?.let { j -> { downloadFor(r, j) } }, onOpen = sourceAction(r))
                    HorizontalDivider(Modifier.padding(start = 68.dp), color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

private val SEARCH_SORTS = listOf("Source", "Taille", "Date", "Seeders", "Tome", "Titre")

/** Tri des résultats ; sans clé, l'ordre du serveur avec les tomes confirmés d'abord. */
private fun sortResults(list: List<SearchResult>, key: String?, desc: Boolean): List<SearchResult> {
    val sorted = when (key) {
        "Source" -> list.sortedBy { it.source }
        "Taille" -> list.sortedBy { it.sizeBytes ?: -1L }
        "Date" -> list.sortedBy { it.date ?: "" }
        "Seeders" -> list.sortedBy { it.seeders ?: -1 }
        "Tome" -> list.sortedBy { it.volumeNumber ?: -1 }
        "Titre" -> list.sortedBy { it.name.lowercase() }
        else -> return list.sortedBy { it.unconfirmed != null }
    }
    return if (desc) sorted.reversed() else sorted
}

/** Puce d'une source : logo et nombre de résultats ; atténuée si la source est désactivée ou ses résultats masqués. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun SourceChip(
    key: String, label: String, count: Int?, loading: Boolean, failed: Boolean,
    enabled: Boolean, shown: Boolean, onClick: () -> Unit, onLongClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val active = enabled && shown
    Row(
        Modifier.height(32.dp).clip(RoundedCornerShape(8.dp))
            // Active : fond relevé, bordure contrastée et coche ; inactive : transparente et très estompée.
            .background(if (active) cs.surfaceContainerHighest else androidx.compose.ui.graphics.Color.Transparent)
            .border(if (active) 1.5.dp else 1.dp, if (active) cs.onSurfaceVariant else cs.outlineVariant, RoundedCornerShape(8.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .alpha(if (active) 1f else 0.4f)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (active) Icon(Icons.Outlined.Check, contentDescription = "Affichée", tint = cs.onSurface, modifier = Modifier.size(14.dp))
        Logo(key, label, 18.dp, tip = false)
        when {
            loading -> CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp)
            failed -> Text("!", style = MaterialTheme.typography.labelLarge, color = cs.error)
            count != null -> Text("$count", style = MaterialTheme.typography.labelLarge, color = if (active) cs.onSurface else cs.onSurfaceVariant)
            else -> Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

/** Ligne de résultat, dans le style des listes de l'app : logo de la source, titre, détails, téléchargement. */
@Composable
private fun ResultRow(r: SearchResult, sentTo: String?, onDownload: (() -> Unit)?, onOpen: (() -> Unit)?) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clickable(enabled = onOpen != null) { onOpen?.invoke() }.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(cs.surfaceVariant), contentAlignment = Alignment.Center) {
            Logo(r.source, SEARCH_SOURCES[r.source] ?: r.source, 22.dp, tip = false)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(r.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val meta = listOfNotNull(
                r.volumeLabel, formatBytes(r.sizeBytes), r.format, r.seeders?.let { "$it seeds" }, r.date, r.detail,
            ).distinct().joinToString(" · ")
            if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            r.unconfirmed?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = cs.tertiary, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            if (sentTo != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                StatusIcon(Icons.AutoMirrored.Outlined.Send, "Envoyé à $sentTo", cs.secondary, 16.dp)
                Logo(logoKey(sentTo), "Envoyé à $sentTo", 16.dp)
                Text("Envoyé à $sentTo", style = MaterialTheme.typography.bodySmall, color = cs.secondary)
            }
        }
        if (onDownload != null) IconButton(onClick = onDownload) {
            Icon(
                if (sentTo != null) Icons.Outlined.TaskAlt else Icons.Outlined.Download,
                contentDescription = if (sentTo != null) "Envoyé, renvoyer" else "Télécharger",
                tint = if (sentTo != null) cs.secondary else cs.primary,
            )
        }
    }
}
