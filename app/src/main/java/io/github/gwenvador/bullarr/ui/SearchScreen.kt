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
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandMore
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
    // Vue par source (repliable) ou tableau unique de tous les résultats.
    var table by rememberSaveable { mutableStateOf(true) }
    // Recherche Bédéthèque pour ajouter une série (même boîte que le « + » de la bibliothèque).
    var addSeries by remember { mutableStateOf(false) }
    // Sources masquées (appui sur leur logo) : leurs résultats disparaissent, sans rien relancer.
    val hidden = remember { mutableStateMapOf<String, Boolean>() }
    var sortKey by rememberSaveable { mutableStateOf<String?>(null) }
    var sortDesc by rememberSaveable { mutableStateOf(true) }
    var formatFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var onlyConfirmed by rememberSaveable { mutableStateOf(false) }
    // Un appui sur le logo de la source ouvre la page du résultat (tracker, message Telegram…).
    fun sourceAction(r: SearchResult): (() -> Unit)? = r.sourceLink?.let { link -> { runCatching { uri.openUri(link) } } }
    fun onSortKey(k: String) { if (sortKey == k) sortDesc = !sortDesc else { sortKey = k; sortDesc = true } }
    fun downloadFor(r: SearchResult, j: DownloadJob) {
        val ctx = s.context
        val vol = if (r.volumeNumber != null) r.volumeLabel else ctx?.volumeLabel
        dl.request(j, ctx?.let { "Rattaché à ${it.seriesTitle}" + (vol?.let { v -> " · $v" } ?: "") }, r.unconfirmed)
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Petite flèche retour : revient à l'album recherché, sans carte dans la liste.
                s.context?.let { ctx ->
                    IconButton(onClick = { vm.section = Section.Library; vm.openSeriesById(ctx.seriesId, ctx.seriesTitle) }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Retour à l'album", modifier = Modifier.size(20.dp))
                    }
                }
                Text("Recherche", style = MaterialTheme.typography.headlineSmall)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                CompactSearchField(
                    value = s.title, onValueChange = { s.title = it },
                    placeholder = "Série ou titre…",
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); s.search() }),
                    modifier = Modifier.weight(1f),
                )
                CompactSearchField(
                    value = s.volume, onValueChange = { v -> s.volume = v.filter { it.isDigit() }.take(4) },
                    placeholder = "Tome", showIcon = false,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); s.search() }),
                    modifier = Modifier.width(88.dp),
                )
                IconButton(onClick = { focus.clearFocus(); s.search() }, enabled = s.title.isNotBlank()) {
                    Icon(Icons.Outlined.Search, contentDescription = "Rechercher")
                }
                IconButton(onClick = { focus.clearFocus(); addSeries = true }) {
                    Icon(Icons.Outlined.LibraryAdd, contentDescription = "Ajouter une série depuis Bédéthèque")
                }
            }
        }
        if (addSeries) item(key = "add-series-dialog") {
            SeriesAddDialog(vm, onDismiss = { addSeries = false }, initialQuery = s.title, onAdded = { _, _ -> })
        }
        item {
            // Une seule ligne : logos sans contour, atténués quand la source est désactivée.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                SEARCH_SOURCES.forEach { (key, label) ->
                    val count = (s.sources[key] as? SourceState.Done)?.results?.size
                    val on = s.enabledSources[key] == true
                    val isHidden = hidden[key] == true
                    Row(
                        Modifier.clip(RoundedCornerShape(8.dp))
                            // Appui : affiche/masque les résultats ; appui long : active/désactive la source dans les recherches.
                            .combinedClickable(
                                onClick = {
                                    val allVisible = SEARCH_SOURCES.keys.all { hidden[it] != true }
                                    val onlyThis = SEARCH_SOURCES.keys.all { it == key || hidden[it] == true }
                                    when {
                                        // Tout affiché : ce logo seul reste (ex. Telegram seul).
                                        allVisible -> SEARCH_SOURCES.keys.forEach { hidden[it] = it != key }
                                        // Seul ce logo affiché : on réaffiche tout.
                                        onlyThis -> SEARCH_SOURCES.keys.forEach { hidden[it] = false }
                                        else -> hidden[key] = !isHidden
                                    }
                                },
                                onLongClick = { s.enabledSources[key] = !on },
                            )
                            .alpha(if (on && !isHidden) 1f else 0.3f)
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Logo(key, label, 26.dp, tip = false)
                        if (count != null) Text("$count", style = MaterialTheme.typography.labelSmall)
                    }
                }
                IconButton(onClick = { table = !table }) {
                    Icon(Icons.AutoMirrored.Outlined.List, contentDescription = if (table) "Afficher par source" else "Afficher en tableau")
                }
            }
        }
        if (s.lastQuery == null) {
            item { EmptyState("Cherchez un album", "Sur Prowlarr, EBDZ, Telegram, Fourtoutici, VK et Anna's Archive. Depuis une fiche série, touchez un tome pour le chercher.") }
        }
        // Vue par type : même en-tête de tri, appliqué à chaque groupe.
        if (!table && s.lastQuery != null) item(key = "g-head") {
            SearchTableHeader(sortKey, sortDesc, onSort = { onSortKey(it) })
        }
        if (table) {
            val all = SEARCH_SOURCES.keys.filter { hidden[it] != true }.flatMap { key -> (s.sources[key] as? SourceState.Done)?.results.orEmpty() }
            val formats = all.mapNotNull { it.format }.distinct().sorted()
            val rows = sortResults(
                all.filter { formatFilter == null || it.format == formatFilter }.filter { !onlyConfirmed || it.unconfirmed == null },
                sortKey, sortDesc,
            )
            if (s.lastQuery != null) item(key = "t-tools") {
                SearchTableTools(
                    sortKey, sortDesc,
                    onSort = { onSortKey(it) },
                    formats = formats, format = formatFilter, onFormat = { formatFilter = it },
                    onlyConfirmed = onlyConfirmed, onConfirmed = { onlyConfirmed = it },
                )
            }
            if (s.sources.values.any { it == SourceState.Loading }) item(key = "t-loading") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            s.sources.forEach { (key, st) ->
                if (st is SourceState.Failed && hidden[key] != true) item(key = "t-e-$key") { ErrorCard("${SEARCH_SOURCES[key] ?: key} : ${st.message}") }
            }
            if (rows.isNotEmpty()) {
                item(key = "t-head") { SearchTableHeader(sortKey, sortDesc, onSort = { onSortKey(it) }) }
                items(rows.withIndex().toList(), key = { "t-${it.index}" }) { (_, r) ->
                    val job = r.toJob(s.context)
                    SearchTableRow(r, sentTo = job?.let { dl.sent[it.key] }, onDownload = job?.let { j -> { downloadFor(r, j) } }, onSource = sourceAction(r))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            } else if (s.lastQuery != null && s.sources.values.none { it == SourceState.Loading }) {
                item(key = "t-none") { Text("Aucun résultat.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        } else SEARCH_SOURCES.forEach { (key, label) ->
            if (hidden[key] == true) return@forEach
            val state = s.sources[key] ?: return@forEach
            val closed = s.collapsed[key] != false
            item(key = "h-$key") {
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp)
                        .clickable { s.collapsed[key] = !closed }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (closed) Icons.Outlined.ChevronRight else Icons.Outlined.ExpandMore,
                        contentDescription = if (closed) "Déplier $label" else "Replier $label",
                        modifier = Modifier.padding(end = 4.dp),
                    )
                    Logo(key, label, 22.dp, Modifier.padding(end = 8.dp))
                    Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    when (state) {
                        SourceState.Loading -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        is SourceState.Done -> Text("${state.results.size} résultat(s)", style = MaterialTheme.typography.bodySmall)
                        is SourceState.Failed -> Text("Erreur", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            if (!closed) when (state) {
                is SourceState.Failed -> item(key = "e-$key") { ErrorCard(state.message) }
                is SourceState.Done -> {
                    if (state.results.isEmpty()) item(key = "n-$key") {
                        Text("Aucun résultat.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items(sortResults(state.results, sortKey, sortDesc).withIndex().toList(), key = { "r-$key-${it.index}" }) { (_, r) ->
                        val job = r.toJob(s.context)
                        SearchTableRow(
                            r,
                            sentTo = job?.let { dl.sent[it.key] },
                            onDownload = job?.let { j -> { downloadFor(r, j) } },
                            onSource = sourceAction(r),
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
                SourceState.Loading -> {}
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

@Composable
private fun SearchTableTools(
    sortKey: String?, sortDesc: Boolean, onSort: (String) -> Unit,
    formats: List<String>, format: String?, onFormat: (String?) -> Unit,
    onlyConfirmed: Boolean, onConfirmed: (Boolean) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = "Trier les résultats") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                SEARCH_SORTS.forEach { k ->
                    DropdownMenuItem(
                        text = { Text(if (sortKey == k) "$k ${if (sortDesc) "↓" else "↑"}" else k) },
                        onClick = { onSort(k); menu = false },
                    )
                }
            }
        }
        FilterChip(selected = onlyConfirmed, onClick = { onConfirmed(!onlyConfirmed) }, label = { Text("Tome confirmé") })
        formats.forEach { f ->
            FilterChip(selected = format == f, onClick = { onFormat(if (format == f) null else f) }, label = { Text(f) })
        }
    }
}

private val SRC_W = 28.dp
private val TOME_W = 56.dp
private val SIZE_W = 64.dp
private val ACT_W = 48.dp

@Composable
private fun SearchTableHeader(sortKey: String?, sortDesc: Boolean, onSort: (String) -> Unit) {
    val style = MaterialTheme.typography.labelSmall
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    // Un appui trie sur la colonne ; l'indicateur donne la colonne et le sens actifs.
    fun label(key: String, text: String) = if (sortKey == key) "$text ${if (sortDesc) "↓" else "↑"}" else text
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label("Source", "Source"), style = style, color = color, maxLines = 1, modifier = Modifier.width(SRC_W).clickable { onSort("Source") })
        Text(label("Titre", "Titre"), style = style, color = color, maxLines = 1, modifier = Modifier.weight(1f).clickable { onSort("Titre") })
        Text(label("Tome", "Tome"), style = style, color = color, maxLines = 1, modifier = Modifier.width(TOME_W).clickable { onSort("Tome") })
        Text(label("Taille", "Taille"), style = style, color = color, maxLines = 1, modifier = Modifier.width(SIZE_W).clickable { onSort("Taille") })
        Spacer(Modifier.width(ACT_W))
    }
}

@Composable
private fun SearchTableRow(r: SearchResult, sentTo: String?, onDownload: (() -> Unit)?, onSource: (() -> Unit)?) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(SRC_W).clickable(enabled = onSource != null, onClick = { onSource?.invoke() })) { Logo(r.source, SEARCH_SOURCES[r.source] ?: r.source, 20.dp, tip = false) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(r.name, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            r.unconfirmed?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = cs.tertiary) }
        }
        Text(r.volumeLabel.orEmpty(), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.width(TOME_W))
        Text(formatBytes(r.sizeBytes).orEmpty(), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, modifier = Modifier.width(SIZE_W))
        if (onDownload != null) IconButton(onClick = onDownload) {
            Icon(
                if (sentTo != null) Icons.Outlined.TaskAlt else Icons.Outlined.Download,
                contentDescription = if (sentTo != null) "Envoyé" else "Télécharger",
            )
        }
        else Spacer(Modifier.width(ACT_W))
    }
}

@Composable
private fun ResultRow(r: SearchResult, sentTo: String?, onDownload: (() -> Unit)?, onOpen: (() -> Unit)?) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(r.name, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            val meta = listOfNotNull(r.volumeLabel, formatBytes(r.sizeBytes), r.detail).joinToString(" · ")
            if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            r.unconfirmed?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = cs.tertiary) }
            if (sentTo != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                StatusIcon(Icons.AutoMirrored.Outlined.Send, "Envoyé à $sentTo", cs.secondary, 16.dp)
                Logo(logoKey(sentTo), "Envoyé à $sentTo", 16.dp)
            }
        }
        if (onOpen != null) IconButton(onClick = onOpen) { Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = "Voir la source") }
        if (onDownload != null) IconButton(onClick = onDownload, enabled = sentTo == null) { Icon(Icons.Outlined.Download, contentDescription = "Télécharger") }
    }
}
