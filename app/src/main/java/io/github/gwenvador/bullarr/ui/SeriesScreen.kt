package io.github.gwenvador.bullarr.ui

import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.OfflinePin
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import io.github.gwenvador.bullarr.ui.Icon
import io.github.gwenvador.bullarr.ui.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.gwenvador.bullarr.data.SeriesDetail
import io.github.gwenvador.bullarr.data.SeriesSummary
import io.github.gwenvador.bullarr.data.VolumeRow
import io.github.gwenvador.bullarr.data.formatBytes
import io.github.gwenvador.bullarr.data.parseSeriesDetail
import io.github.gwenvador.bullarr.data.readable
import io.github.gwenvador.bullarr.data.str

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesScreen(vm: AppViewModel, summary: SeriesSummary, onBack: () -> Unit, onSearch: (SearchContext) -> Unit) {
    val api = vm.api ?: return
    var detail by remember(summary.id) { mutableStateOf<SeriesDetail?>(null) }
    var error by remember(summary.id) { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var shown by remember { mutableStateOf<VolumeRow?>(null) }
    var sortKey by rememberSaveable { mutableStateOf("Numéro") }
    var sortDesc by rememberSaveable { mutableStateOf(false) }
    var status by rememberSaveable { mutableStateOf("Tous") }
    var format by rememberSaveable { mutableStateOf("Tous") }
    var menu by remember { mutableStateOf<VolumeRow?>(null) }
    var editing by remember { mutableStateOf<VolumeRow?>(null) }
    var deleting by remember { mutableStateOf<VolumeRow?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(summary.id, attempt) {
        error = null
        try { detail = parseSeriesDetail(api.getObject("api/series/${summary.id}")) } catch (e: Exception) { error = e.readable() }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(summary.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Retour") } },
            windowInsets = WindowInsets(0),
        )
        val d = detail
        LaunchedEffect(d) { if (d != null) vm.offline.adopt(d.id, d.volumes.filter { it.owned }.map { it.id to it.label }) }
        if (d == null && error == null) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            error?.let { item { ErrorCard(it, Modifier.padding(16.dp), onRetry = { attempt++ }) } }
            item { Header(vm, summary, d) }
            // One-shot : un seul album, lisible directement sous la couverture.
            val oneshot = d?.takeIf { it.isOneshot }?.volumes?.firstOrNull { it.owned }
            if (d != null && oneshot != null) item {
                androidx.compose.material3.Button(
                    onClick = { vm.reading = ReaderTarget(oneshot.id, d.title) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                    Text("Lire")
                }
            }
            if (d != null) {
                val owned = d.volumes.count { it.owned }
                item {
                    Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Tomes · $owned/${d.volumes.size}" + (d.totalKnown?.let { " (Bédéthèque : $it)" } ?: ""),
                            style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { onSearch(SearchContext(seriesId = d.id, seriesTitle = d.title, volumeId = null, volumeLabel = null, volumeNumber = null)) }) {
                            Icon(Icons.Outlined.Search, contentDescription = "Chercher la série")
                        }
                        VolumeOrdering(vm, d.volumes, sortKey, sortDesc, status, format,
                            onSort = { k -> if (sortKey == k) sortDesc = !sortDesc else { sortKey = k; sortDesc = k == "Taille" } },
                            onStatus = { status = it }, onFormat = { format = it })
                    }
                }
                if (d.volumes.isEmpty()) item { EmptyState("Aucun tome connu pour cette série") }
                item {
                    Text(
                        "Touchez un tome pour sa fiche, maintenez pour les actions ; la loupe le cherche pour le télécharger.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                items(ordered(d.volumes, vm, sortKey, sortDesc, status, format), key = { it.id }) { v ->
                    VolumeItem(
                        v, vm.coverUrl(v.coverPath), onOpen = { shown = v }, onLongClick = { menu = v }, offline = vm.offline.isDownloaded(v.id),
                        offlineIcon = if (v.owned) ({ OfflineIcon(vm, v.id, "${d.title} · ${v.label}") }) else null,
                        onRead = if (v.owned) ({ vm.reading = ReaderTarget(v.id, "${d.title} · ${v.label}") }) else null,
                    ) {
                        onSearch(
                            SearchContext(
                                seriesId = d.id, seriesTitle = d.title, volumeId = v.id, volumeLabel = v.label,
                                volumeNumber = v.number, isIntegral = v.isIntegral, isHs = v.isHs, isEpisode = v.isEpisode,
                            ),
                        )
                    }
                    HorizontalDivider(Modifier.padding(start = 76.dp), color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
    menu?.let { v ->
        val title = "${detail?.title ?: summary.title} · ${v.label}"
        fun run(ok: String, block: suspend () -> Unit) = launchAction(scope, context, ok, then = { attempt++ }) { block() }
        VolumeActions(
            v, vm, title, onDismiss = { menu = null },
            onOpen = { shown = v },
            onRead = { vm.reading = ReaderTarget(v.id, title) },
            onSearch = { detail?.let { d -> onSearch(SearchContext(d.id, d.title, v.id, v.label, v.number, v.isIntegral, v.isHs, v.isEpisode)) } },
            onEdit = { editing = v },
            onRefresh = { run("Fichier actualisé") { ItemActions.refreshVolume(api, v.id) } },
            onMeta = { run("Métadonnées mises à jour") { ItemActions.updateVolumeMetadata(api, v.id) } },
            onDelete = { deleting = v },
        )
    }
    editing?.let { v ->
        EditVolumeDialog(v, onDismiss = { editing = null }) { f ->
            launchAction(scope, context, "Tome modifié", then = { attempt++ }) { ItemActions.editVolume(api, v.id, f) }
        }
    }
    deleting?.let { v ->
        ConfirmDialog(
            "Supprimer ${v.label} ?",
            if (v.owned) "Le fichier du tome sera supprimé du disque ; la fiche reste en « manquant » pour pouvoir le retélécharger." else "La fiche de ce tome manquant sera retirée de la série.",
            "Supprimer", onConfirm = { launchAction(scope, context, "Tome supprimé", then = { attempt++ }) { ItemActions.deleteVolume(api, v.id) } },
            onDismiss = { deleting = null },
        )
    }
    shown?.let { v ->
        VolumeSheet(vm, v, detail, summary, onDismiss = { shown = null }, onRead = {
            shown = null
            vm.reading = ReaderTarget(v.id, "${detail?.title ?: summary.title} · ${v.label}")
        }, onSearch = {
            shown = null
            detail?.let { d ->
                onSearch(SearchContext(d.id, d.title, v.id, v.label, v.number, v.isIntegral, v.isHs, v.isEpisode))
            }
        })
    }
}

@Composable
private fun Header(vm: AppViewModel, s: SeriesSummary, d: SeriesDetail?) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row {
            Cover(vm.coverUrl(d?.coverPath ?: s.coverPath), 104.dp)
            Spacer(Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(d?.title ?: s.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                (d?.authors ?: s.author)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                val meta = listOfNotNull(d?.years ?: s.year, d?.genre, d?.publishers).joinToString(" · ")
                if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    val cs = MaterialTheme.colorScheme
                    d?.status?.let { st ->
                        val l = st.lowercase()
                        when {
                            "cours" in l -> IconTag(Icons.Outlined.HourglassTop, "$st (série toujours publiée)", cs.tertiary)
                            "termin" in l || "fini" in l -> IconTag(Icons.Outlined.TaskAlt, st, cs.secondary)
                            "arrêt" in l || "stopp" in l || "abandon" in l -> IconTag(Icons.Outlined.Block, st, cs.error)
                            else -> Tag(st, cs.secondaryContainer, cs.onSecondaryContainer)
                        }
                    }
                    if (d?.monitored == true) IconTag(Icons.Outlined.Visibility, "Surveillée", cs.primary)
                    if (d != null) {
                        val owned = d.volumes.filter { it.owned }
                        val got = owned.count { vm.offline.isDownloaded(it.id) }
                        if (got > 0) IconTag(
                            Icons.Outlined.OfflinePin,
                            if (got == owned.size) "Tout est disponible hors ligne" else "$got sur ${owned.size} hors ligne",
                            cs.primary, if (owned.size == 1) "Téléchargé" else "$got/${owned.size} téléchargé(s)",
                        )
                    }
                    if (d != null) SeriesOfflineIcon(vm, d.volumes.filter { it.owned }.map { OfflineVolume(it.id, "${d.title} · ${it.label}", it.pageCount) })
                }
            }
        }
        d?.completeReason?.let {
            Text(it.replaceFirstChar { c -> c.uppercase() }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
        }
        d?.summary?.let {
            Text(
                it, style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else 4, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.animateContentSize().clickable { expanded = !expanded },
            )
        }
    }
}

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun VolumeItem(v: VolumeRow, cover: String?, onOpen: () -> Unit, onLongClick: () -> Unit, offline: Boolean, offlineIcon: (@Composable () -> Unit)?, onRead: (() -> Unit)?, onSearch: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().combinedClickable(onClick = onOpen, onLongClick = onLongClick).padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Cover(cover, 44.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(v.label, style = MaterialTheme.typography.labelLarge, color = cs.primary)
            v.title?.let { Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            val meta = if (v.owned) listOfNotNull(v.format?.uppercase(), formatBytes(v.fileSize), v.pageCount?.let { "$it p." }, v.year)
            else listOfNotNull(v.year)
            if (meta.isNotEmpty()) Text(meta.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            if (offline) IconTag(Icons.Outlined.OfflinePin, "Disponible hors ligne", cs.primary, "Téléchargé sur le téléphone")
        }
        Spacer(Modifier.width(8.dp))
        if (!v.owned) IconTag(Icons.Outlined.ReportProblem, "Manquant", cs.tertiary)
        offlineIcon?.invoke()
        if (onRead != null) IconButton(onClick = onRead) { Icon(Icons.Outlined.PlayArrow, contentDescription = "Lire ce tome", tint = cs.primary) }
        IconButton(onClick = onSearch) { Icon(Icons.Outlined.Search, contentDescription = "Chercher ce tome") }
    }
}

/** Fiche d'un tome : couverture, résumé et métadonnées ComicInfo, fichier ; la recherche est un bouton à part. */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun VolumeSheet(vm: AppViewModel, v: VolumeRow, detail: SeriesDetail?, summary: SeriesSummary, onDismiss: () -> Unit, onRead: () -> Unit, onSearch: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    val ci = v.comicInfo
    fun c(key: String) = ci?.str(key)?.takeUnless { it.startsWith("<") }
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp)) {
            item {
                Row {
                    Cover(vm.coverUrl(v.coverPath), 110.dp)
                    Spacer(Modifier.width(16.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(v.label, style = MaterialTheme.typography.labelLarge, color = cs.primary)
                        Text(v.title ?: c("series") ?: v.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        val date = listOfNotNull(c("day"), c("month"), c("year") ?: v.year).joinToString("/")
                        val meta = listOfNotNull(c("publisher"), date.ifBlank { null }, c("genre")).joinToString(" · ")
                        if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        c("communityrating")?.let { r ->
                            Text("★ $r / 5" + (c("communityratingcount")?.let { " ($it avis)" } ?: ""), style = MaterialTheme.typography.bodyMedium, color = cs.tertiary)
                        }
                        if (vm.offline.isDownloaded(v.id)) IconTag(Icons.Outlined.OfflinePin, "Disponible hors ligne", cs.primary, "Téléchargé sur le téléphone")
                        if (v.owned) IconTag(Icons.Outlined.CheckCircle, "Possédé", cs.secondary, "Possédé")
                        else IconTag(Icons.Outlined.ReportProblem, "Manquant", cs.tertiary, "Manquant")
                    }
                }
            }
            c("summary")?.let { item { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 16.dp)) } }
            val people = listOf("Scénario" to c("writer"), "Dessin" to c("penciller"), "Couleurs" to c("colorist"), "Auteurs" to v.author.takeIf { c("writer") == null })
                .filter { it.second != null }
            val file = listOf(
                "Fichier" to v.filename, "Format" to v.format?.uppercase(), "Taille" to formatBytes(v.fileSize),
                "Pages" to v.pageCount?.toString(), "ISBN" to v.isbn,
            ).filter { it.second != null }
            (people + file).forEach { (k, value) ->
                item {
                    Row(Modifier.padding(top = 10.dp)) {
                        Text(k, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.width(88.dp))
                        Text(value!!, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item {
                androidx.compose.foundation.layout.FlowRow(
                    Modifier.padding(top = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (v.owned) androidx.compose.material3.Button(onClick = onRead) {
                        Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                        Text("Lire")
                    }
                    if (v.owned) OfflineButton(vm, v.id, "${detail?.title ?: summary.title} · ${v.label}")
                    OutlinedButton(onClick = onSearch) {
                        Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                        Text(if (v.owned) "Chercher (remplacer)" else "Chercher ce tome")
                    }
                    c("web")?.let { web -> OutlinedButton(onClick = { runCatching { uri.openUri(web) } }) { Text("Bédéthèque") } }
                    v.komgaUrl?.let { k -> OutlinedButton(onClick = { runCatching { uri.openUri(k) } }) { Text("Lire dans Komga") } }
                }
            }
        }
    }
}

/** Menu d'actions d'un tome (appui long). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VolumeActions(
    v: VolumeRow, vm: AppViewModel, title: String, onDismiss: () -> Unit,
    onOpen: () -> Unit, onRead: () -> Unit, onSearch: () -> Unit, onEdit: () -> Unit,
    onRefresh: () -> Unit, onMeta: () -> Unit, onDelete: () -> Unit,
) {
    val mgr = vm.offline
    val downloaded = mgr.isDownloaded(v.id)
    @Composable
    fun Entry(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: androidx.compose.ui.graphics.Color? = null, action: () -> Unit) {
        androidx.compose.material3.ListItem(
            headlineContent = { Text(label, color = tint ?: androidx.compose.ui.graphics.Color.Unspecified) },
            leadingContent = { Icon(icon, contentDescription = null, tint = tint ?: MaterialTheme.colorScheme.onSurfaceVariant) },
            modifier = Modifier.clickable { onDismiss(); action() },
        )
    }
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            Entry(Icons.Outlined.Info, "Fiche du tome", action = onOpen)
            if (v.owned) Entry(Icons.Outlined.PlayArrow, "Lire", action = onRead)
            if (v.owned) {
                if (downloaded) Entry(Icons.Outlined.DeleteSweep, "Supprimer la copie hors ligne") { mgr.delete(v.id) }
                else if (mgr.active[v.id] == null) Entry(Icons.Outlined.DownloadForOffline, "Télécharger hors ligne") { mgr.start(v.id, title) }
            }
            Entry(Icons.Outlined.Search, if (v.owned) "Chercher (remplacer)" else "Chercher ce tome", action = onSearch)
            Entry(Icons.Outlined.Edit, "Éditer les métadonnées", action = onEdit)
            if (v.owned) Entry(Icons.Outlined.Refresh, "Actualiser taille et pages", action = onRefresh)
            Entry(Icons.Outlined.Sync, "Mettre à jour (Bédéthèque)", action = onMeta)
            Entry(Icons.Outlined.Delete, "Supprimer", MaterialTheme.colorScheme.error, onDelete)
        }
    }
}

private val SORT_KEYS = listOf("Numéro", "Titre", "Taille", "Année")
private val STATUS_KEYS = listOf("Tous", "Possédés", "Manquants", "Hors ligne", "Non téléchargés")

private fun ordered(all: List<VolumeRow>, vm: AppViewModel, sortKey: String, desc: Boolean, status: String, format: String): List<VolumeRow> {
    val filtered = all.filter { v ->
        when (status) {
            "Possédés" -> v.owned
            "Manquants" -> !v.owned
            "Hors ligne" -> vm.offline.isDownloaded(v.id)
            "Non téléchargés" -> v.owned && !vm.offline.isDownloaded(v.id)
            else -> true
        } && (format == "Tous" || v.format?.uppercase() == format)
    }
    val sorted = when (sortKey) {
        "Titre" -> filtered.sortedBy { (it.title ?: it.label).lowercase() }
        "Taille" -> filtered.sortedBy { it.fileSize ?: -1L }
        "Année" -> filtered.sortedBy { it.year ?: "" }
        else -> filtered
    }
    return if (desc) sorted.reversed() else sorted
}

/** Tri et filtre des tomes d'une série, comme dans la bibliothèque : deux icônes, un menu chacune. */
@Composable
private fun VolumeOrdering(
    vm: AppViewModel, volumes: List<VolumeRow>, sortKey: String, desc: Boolean, status: String, format: String,
    onSort: (String) -> Unit, onStatus: (String) -> Unit, onFormat: (String) -> Unit,
) {
    var sortMenu by remember { mutableStateOf(false) }
    var filterMenu by remember { mutableStateOf(false) }
    val formats = listOf("Tous") + volumes.mapNotNull { it.format?.uppercase() }.distinct().sorted()
    val active = (if (status != "Tous") 1 else 0) + (if (format != "Tous") 1 else 0)
    androidx.compose.foundation.layout.Box {
        IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = "Trier les tomes") }
        androidx.compose.material3.DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
            SORT_KEYS.forEach { k ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(k) },
                    trailingIcon = { if (sortKey == k) Icon(if (desc) Icons.Outlined.ArrowDownward else Icons.Outlined.ArrowUpward, contentDescription = if (desc) "Décroissant" else "Croissant") },
                    onClick = { onSort(k); sortMenu = false },
                )
            }
        }
    }
    androidx.compose.foundation.layout.Box {
        androidx.compose.material3.BadgedBox(badge = { if (active > 0) androidx.compose.material3.Badge { Text("$active") } }) {
            IconButton(onClick = { filterMenu = true }) { Icon(Icons.Outlined.FilterList, contentDescription = "Filtrer les tomes") }
        }
        androidx.compose.material3.DropdownMenu(expanded = filterMenu, onDismissRequest = { filterMenu = false }) {
            STATUS_KEYS.forEach { k ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(k) },
                    leadingIcon = { if (status == k) Icon(Icons.Outlined.Check, contentDescription = "Sélectionné") },
                    onClick = { onStatus(k); filterMenu = false },
                )
            }
            if (formats.size > 2) {
                HorizontalDivider()
                formats.forEach { k ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(if (k == "Tous") "Tous formats" else k) },
                        leadingIcon = { if (format == k) Icon(Icons.Outlined.Check, contentDescription = "Sélectionné") },
                        onClick = { onFormat(k); filterMenu = false },
                    )
                }
            }
        }
    }
}
