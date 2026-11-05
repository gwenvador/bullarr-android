package io.github.gwenvador.bullarr.ui

import androidx.compose.material.icons.outlined.Link
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
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Star
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.gwenvador.bullarr.data.CoverLoader
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.AutoStories
import kotlin.math.abs
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.foundation.lazy.itemsIndexed
import io.github.gwenvador.bullarr.data.ApiException
import androidx.lifecycle.viewModelScope
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.VisibilityOff
import io.github.gwenvador.bullarr.data.BullarrApi
import io.github.gwenvador.bullarr.data.int
import io.github.gwenvador.bullarr.data.objects
import org.json.JSONObject
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
    var reviewsFor by remember { mutableStateOf<VolumeRow?>(null) }
    var readAlso by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var authorSheet by remember { mutableStateOf<Pair<String, String>?>(null) }
    var seriesMenu by remember { mutableStateOf(false) }
    var editingSeries by remember { mutableStateOf(false) }
    var confirmDeleteSeries by remember { mutableStateOf(false) }
    var editingTags by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    // Sections repliées, mémorisées par type (comme le web) : valable pour toutes les séries.
    val groupPrefs = remember { context.getSharedPreferences("volume_groups", android.content.Context.MODE_PRIVATE) }
    var collapsed by remember { mutableStateOf(VOLUME_GROUP_LABELS.keys.filter { groupPrefs.getBoolean(it, false) }.toSet()) }
    fun toggleGroup(key: String) {
        val folded = key !in collapsed
        collapsed = if (folded) collapsed + key else collapsed - key
        groupPrefs.edit().putBoolean(key, folded).apply()
    }
    fun setMonitored(on: Boolean) = launchAction(scope, context, if (on) "Série surveillée" else "Surveillance arrêtée", then = { attempt++ }) {
        api.postObject("api/missing-monitor/series/${summary.id}/monitor", JSONObject().put("enabled", on))
    }
    // Liste perso (favori ou à lire) : la fiche et la ligne de la bibliothèque suivent sans rechargement complet.
    fun setList(toReadLater: Boolean? = null, isFavorite: Boolean? = null) = launchAction(scope, context, "Liste mise à jour", then = { attempt++ }) {
        ItemActions.setSeriesReadingLists(api, summary.id, toReadLater, isFavorite)
        vm.updateSeriesRow(summary.id) { row -> row.copy(toReadLater = toReadLater ?: row.toReadLater, isFavorite = isFavorite ?: row.isFavorite) }
    }
    fun saveTags(tags: List<String>) = launchAction(scope, context, "Tags enregistrés", then = { attempt++ }) {
        ItemActions.setSeriesTags(api, summary.id, tags)
        vm.updateSeriesRow(summary.id) { row -> row.copy(tags = tags) }
    }
    var refreshing by remember { mutableStateOf(false) }
    // Avancement Komga de chaque tome (comme le site), relu à chaque rechargement et en sortant du lecteur.
    var komga by remember(summary.id) { mutableStateOf<Map<Int, io.github.gwenvador.bullarr.data.KomgaVolumeProgress>>(emptyMap()) }
    LaunchedEffect(summary.id, attempt) {
        error = null
        try { detail = parseSeriesDetail(api.getObject("api/series/${summary.id}")) } catch (e: Exception) { error = e.readable() }
        refreshing = false
    }
    LaunchedEffect(summary.id, attempt, vm.progressVersion) {
        runCatching { io.github.gwenvador.bullarr.data.loadSeriesKomgaProgress(api, summary.id) }.onSuccess { komga = it }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    summary.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { seriesMenu = true },
                )
            },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Retour") } },
            windowInsets = WindowInsets(0),
        )
        var dragX by remember { mutableFloatStateOf(0f) }
        val d = detail
        LaunchedEffect(d) { if (d != null) vm.offline.adopt(d.id, d.volumes.filter { it.owned }.map { it.id to it.label }) }
        if (d == null && error == null) LinearProgressIndicator(Modifier.fillMaxWidth())
        // Tirer la liste vers le bas recharge la fiche.
        androidx.compose.material3.pulltorefresh.PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { refreshing = true; attempt++ },
            modifier = Modifier.fillMaxSize(),
        ) {
        LazyColumn(
            Modifier.fillMaxSize().pointerInput(summary.id) {
                detectHorizontalDragGestures(
                    onDragStart = { dragX = 0f },
                    onHorizontalDrag = { _, dx -> dragX += dx },
                    onDragEnd = {
                        val list = vm.visibleSeries
                        val i = list.indexOfFirst { it.id == summary.id }
                        if (i >= 0 && abs(dragX) > 120f) list.getOrNull(if (dragX < 0) i + 1 else i - 1)?.let { vm.openSeries = it }
                    },
                )
            },
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            error?.let { item { ErrorCard(it, Modifier.padding(16.dp), onRetry = { attempt++ }) } }
            item {
                Header(
                    vm, summary, d, onToggleMonitor = { setMonitored(it) },
                    onAuthor = { name -> d?.authorLinks?.get(name)?.let { authorSheet = name to it } },
                    onFavorite = { setList(isFavorite = it) },
                    onToRead = { setList(toReadLater = it) },
                    onEditTags = { editingTags = true },
                    onTag = { tag ->
                        // Tag cliqué : la bibliothèque s'ouvre filtrée sur ce tag.
                        vm.filters = vm.filters.copy(tags = setOf(tag))
                        vm.openSeries = null
                        vm.section = Section.Library
                    },
                ) { seriesMenu = true }
            }
            if (d != null) {
                val owned = d.volumes.count { it.owned }
                item {
                    Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Tomes · $owned/${d.volumes.size}",
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
                val grouped = volumeGroups(ordered(d.volumes, vm, sortKey, sortDesc, status, format), d.isOneshot)
                grouped.forEach { (key, list) ->
                    if (grouped.size > 1) item(key = "group-$key") {
                        VolumeGroupHeader(
                            VOLUME_GROUP_LABELS.getValue(key), owned = list.count { it.owned }, total = list.size,
                            collapsed = key in collapsed, onToggle = { toggleGroup(key) },
                        )
                    }
                    if (grouped.size == 1 || key !in collapsed) items(list, key = { it.id }) { v ->
                        VolumeItem(
                            v, vm.coverUrl(v.coverPath), onOpen = { shown = v }, onLongClick = { menu = v }, offline = vm.offline.isDownloaded(v.id), reading = komga[v.id],
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
        }
    }
    menu?.let { v ->
        val title = "${detail?.title ?: summary.title} · ${v.label}"
        fun run(ok: String, block: suspend () -> Unit) = launchAction(scope, context, ok, then = { attempt++ }) { block() }
        VolumeActions(
            v, title, onDismiss = { menu = null },
            onEdit = { editing = v },
            onRefresh = { run("Fichier actualisé") { ItemActions.refreshVolume(api, v.id) } },
            onMeta = { run("Fiche Bédéthèque rechargée") { ItemActions.updateVolumeMetadata(api, v.id) } },
            onReviews = { reviewsFor = v },
            onHistory = { showHistory = true },
            onDelete = { deleting = v },
        )
    }
    reviewsFor?.let { v -> ReviewsSheet(api, v, "${detail?.title ?: summary.title} · ${v.label}", onDismiss = { reviewsFor = null }) }
    if (readAlso) ReadAlsoSheet(vm, api, summary.id, detail?.title ?: summary.title, onDismiss = { readAlso = false })
    authorSheet?.let { (name, link) -> AuthorAlbumsSheet(vm, api, name, link, onDismiss = { authorSheet = null }) }
    if (showHistory) SeriesHistorySheet(api, summary.id, detail?.title ?: summary.title, onDismiss = { showHistory = false })
    if (editingTags) detail?.let { d ->
        SeriesTagsDialog(d.tags, onDismiss = { editingTags = false }) { saveTags(it) }
    }
    if (seriesMenu) SeriesActions(
        title = detail?.title ?: summary.title, onDismiss = { seriesMenu = false },
        onReadAlso = { readAlso = true },
        onHistory = { showHistory = true },
        onUpdate = { launchAction(scope, context, "Mise à jour de la série lancée en arrière-plan") { ItemActions.updateSeriesMetadata(api, summary.id) } },
        onScan = { launchAction(scope, context, "Scan lancé") { ItemActions.scanSeries(api, summary.id) } },
        onEdit = { editingSeries = true },
        onDelete = { confirmDeleteSeries = true },
    )
    if (editingSeries) detail?.let { d ->
        SeriesEditDialog(d, onDismiss = { editingSeries = false }) { changed ->
            launchAction(scope, context, "Série modifiée", then = { attempt++ }) { ItemActions.editSeries(api, d.id, changed) }
        }
    }
    if (confirmDeleteSeries) ConfirmDialog(
        title = "Supprimer la série ?",
        text = "Les fichiers de « ${detail?.title ?: summary.title} » seront supprimés du disque. C'est irréversible.",
        confirm = "Supprimer",
        onConfirm = {
            confirmDeleteSeries = false
            // viewModelScope : la suppression doit aller au bout même après la sortie de la fiche.
            launchAction(vm.viewModelScope, context, "Série supprimée", then = { vm.loadLibrary(force = true) }) { ItemActions.deleteSeries(api, summary.id) }
            onBack()
        },
        onDismiss = { confirmDeleteSeries = false },
    )
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
        }, onAuthor = { name ->
            shown = null
            val link = detail?.authorLinks?.get(name)
            if (link != null) authorSheet = name to link
            else {
                // Pas de fiche auteur connue : recherche de son nom dans la bibliothèque.
                vm.query = name
                vm.openSeries = null
                vm.section = Section.Library
            }
        }, onReviews = {
            shown = null
            reviewsFor = v
        }, onSearch = {
            shown = null
            detail?.let { d ->
                onSearch(SearchContext(d.id, d.title, v.id, v.label, v.number, v.isIntegral, v.isHs, v.isEpisode))
            }
        })
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun Header(
    vm: AppViewModel, s: SeriesSummary, d: SeriesDetail?, onToggleMonitor: (Boolean) -> Unit, onAuthor: (String) -> Unit,
    onFavorite: (Boolean) -> Unit, onToRead: (Boolean) -> Unit, onEditTags: () -> Unit, onTag: (String) -> Unit, onTitleClick: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var actionsOpen by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row {
            val coverUrl = vm.coverUrl(d?.coverPath ?: s.coverPath)
            var big by remember { mutableStateOf(false) }
            Cover(coverUrl, 104.dp, Modifier.clickable { big = true })
            if (big) FullCover(coverUrl, onDismiss = { big = false })
            Spacer(Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        d?.title ?: s.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier.weight(1f, fill = false).clickable { onTitleClick() },
                    )
                    // Listes personnelles, à côté du titre : toucher pour ajouter ou retirer.
                    if (d != null) {
                        val tint = MaterialTheme.colorScheme
                        Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
                            WithTip(if (d.isFavorite) "Favori : toucher pour retirer" else "Pas en favori : toucher pour ajouter") {
                                IconButton(onClick = { onFavorite(!d.isFavorite) }) {
                                    Icon(
                                        if (d.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                        contentDescription = if (d.isFavorite) "Favori" else "Pas en favori",
                                        tint = if (d.isFavorite) tint.tertiary else tint.outline,
                                    )
                                }
                            }
                        }
                        Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
                            WithTip(if (d.toReadLater) "À lire plus tard : toucher pour retirer" else "Pas à lire plus tard : toucher pour ajouter") {
                                IconButton(onClick = { onToRead(!d.toReadLater) }) {
                                    Icon(
                                        if (d.toReadLater) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                        contentDescription = if (d.toReadLater) "À lire plus tard" else "Pas à lire plus tard",
                                        tint = if (d.toReadLater) tint.primary else tint.outline,
                                    )
                                }
                            }
                        }
                    }
                }
                // Chaque auteur qui a une fiche Bédéthèque est cliquable : ouvre ses autres séries.
                (d?.authors ?: s.author)?.let { names ->
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        names.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { name ->
                            val linked = d?.authorLinks?.containsKey(name) == true
                            Text(
                                name, style = MaterialTheme.typography.bodyMedium,
                                color = if (linked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                textDecoration = if (linked) TextDecoration.Underline else null,
                                modifier = if (linked) Modifier.clickable { onAuthor(name) } else Modifier,
                            )
                        }
                    }
                }
                val meta = listOfNotNull(d?.years ?: s.year, d?.genre, d?.publishers).joinToString(" · ")
                if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val cs = MaterialTheme.colorScheme
                val uri = androidx.compose.ui.platform.LocalUriHandler.current
                val links = listOfNotNull(
                    d?.komgaUrl?.let { Triple("komga", "Komga", it) }, d?.bedethequeUrl?.let { Triple("bedetheque", "Bédéthèque", it) }, d?.ebdzUrl?.let { Triple("ebdz", "EBDZ", it) },
                )
                // Surveillance, téléchargements et liens : repliés par défaut pour laisser la place à la fiche.
                if (d != null || links.isNotEmpty()) Row(
                    Modifier.clickable { actionsOpen = !actionsOpen }.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (actionsOpen) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = if (actionsOpen) "Replier" else "Déplier", tint = cs.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Plus", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                }
                // Une seule ligne défilante : statut, surveillance, hors ligne et liens.
                if (actionsOpen) Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    d?.status?.let { st ->
                        val l = st.lowercase()
                        when {
                            "cours" in l -> IconTag(Icons.Outlined.HourglassTop, "$st (série toujours publiée)", cs.tertiary)
                            "termin" in l || "fini" in l -> IconTag(Icons.Outlined.TaskAlt, st, cs.secondary)
                            "arrêt" in l || "stopp" in l || "abandon" in l -> IconTag(Icons.Outlined.Block, st, cs.error)
                            else -> Tag(st, cs.secondaryContainer, cs.onSecondaryContainer)
                        }
                    }
                    // Boîte de 30 dp : le bouton ne réserve pas sa zone tactile de 48 dp, l'écart reste serré.
                    if (d != null) Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
                        WithTip(if (d.monitored) "Surveillée : toucher pour arrêter" else "Non surveillée : toucher pour surveiller") {
                            IconButton(onClick = { onToggleMonitor(!d.monitored) }) {
                                Icon(
                                    if (d.monitored) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                                    contentDescription = if (d.monitored) "Surveillée" else "Non surveillée",
                                    tint = if (d.monitored) cs.primary else cs.outline,
                                )
                            }
                        }
                    }
                    if (d != null) {
                        val owned = d.volumes.filter { it.owned }
                        val got = owned.count { vm.offline.isDownloaded(it.id) }
                        if (got > 0) IconTag(
                            Icons.Outlined.OfflinePin,
                            if (got == owned.size) "Tout est disponible hors ligne" else "$got sur ${owned.size} hors ligne",
                            cs.primary, if (owned.size == 1) "Téléchargé" else "$got/${owned.size} téléchargé(s)",
                        )
                    }
                    if (d != null) Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
                        SeriesOfflineIcon(vm, d.volumes.filter { it.owned }.map { OfflineVolume(it.id, "${d.title} · ${it.label}", it.pageCount) })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        links.forEach { (key, name, url) ->
                            Logo(key, name, 22.dp, Modifier.clickable { runCatching { uri.openUri(url) } }, tip = false)
                        }
                    }
                }
                // Tags dans « Plus » avec le reste : chacun ouvre la bibliothèque filtrée, le crayon ouvre l'édition.
                if (actionsOpen && d != null) androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
                        WithTip("Modifier les tags") {
                            IconButton(onClick = onEditTags) { Icon(Icons.Outlined.Label, contentDescription = "Modifier les tags", tint = cs.primary) }
                        }
                    }
                    d.tags.forEach { tag ->
                        Tag(tag, cs.secondaryContainer, cs.onSecondaryContainer, Modifier.clickable { onTag(tag) })
                    }
                }
            }
        }
        d?.volumes?.filter { !it.owned && it.label != "Tome" && volumeGroup(it, d.isOneshot) == "volume" }
            ?.map { it.label.removePrefix("Tome ") }?.takeIf { it.isNotEmpty() }?.let { numbers ->
            Text(
                "Tome ${numbers.joinToString(", ")} manquant",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary,
            )
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
private fun VolumeItem(v: VolumeRow, cover: String?, onOpen: () -> Unit, onLongClick: () -> Unit, offline: Boolean, reading: io.github.gwenvador.bullarr.data.KomgaVolumeProgress?, offlineIcon: (@Composable () -> Unit)?, onRead: (() -> Unit)?, onSearch: () -> Unit) {
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
            // État de lecture Komga : « Lu » ou la page atteinte, avec une barre d'avancement.
            reading?.let { r ->
                if (r.completed) IconTag(Icons.Outlined.DoneAll, "Lu (Komga)", cs.onSurfaceVariant, "Lu")
                else {
                    val pages = r.pages ?: v.pageCount
                    IconTag(Icons.Outlined.AutoStories, "En cours de lecture (Komga)", cs.tertiary, "p. ${r.page}" + (pages?.let { "/$it" } ?: ""))
                    if (pages != null && pages > 0) LinearProgressIndicator(
                        progress = { (r.page.toFloat() / pages).coerceIn(0f, 1f) },
                        modifier = Modifier.width(120.dp).padding(top = 2.dp), color = cs.tertiary, trackColor = cs.surfaceVariant,
                    )
                }
            }
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
private fun VolumeSheet(vm: AppViewModel, v: VolumeRow, detail: SeriesDetail?, summary: SeriesSummary, onDismiss: () -> Unit, onRead: () -> Unit, onReviews: () -> Unit, onAuthor: (String) -> Unit, onSearch: () -> Unit) {
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
                            Text("★ $r / 5" + (c("communityratingcount")?.let { " ($it avis)" } ?: ""), style = MaterialTheme.typography.bodyMedium, color = cs.tertiary, modifier = Modifier.clickable(onClick = onReviews))
                        }
                        if (vm.offline.isDownloaded(v.id)) IconTag(Icons.Outlined.OfflinePin, "Disponible hors ligne", cs.primary, "Téléchargé sur le téléphone")
                        if (v.owned) IconTag(Icons.Outlined.CheckCircle, "Possédé", cs.secondary, "Possédé")
                        else IconTag(Icons.Outlined.ReportProblem, "Manquant", cs.tertiary, "Manquant")
                    }
                }
            }
            item {
                androidx.compose.foundation.layout.FlowRow(
                    Modifier.padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    if (v.owned) WithTip("Lire") {
                        androidx.compose.material3.FilledIconButton(onClick = onRead) { Icon(Icons.Outlined.PlayArrow, contentDescription = "Lire") }
                    }
                    if (v.owned) OfflineButton(vm, v.id, "${detail?.title ?: summary.title} · ${v.label}", iconOnly = true)
                    WithTip(if (v.owned) "Chercher (remplacer)" else "Chercher ce tome") {
                        androidx.compose.material3.FilledTonalIconButton(onClick = onSearch) { Icon(Icons.Outlined.Search, contentDescription = if (v.owned) "Chercher (remplacer)" else "Chercher ce tome") }
                    }
                    c("web")?.let { web -> Logo("bedetheque", "Bédéthèque", 28.dp, Modifier.clickable { runCatching { uri.openUri(web) } }, tip = false) }
                    v.komgaUrl?.let { k -> Logo("komga", "Lire dans Komga", 28.dp, Modifier.clickable { runCatching { uri.openUri(k) } }, tip = false) }
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
                        if (k in setOf("Scénario", "Dessin", "Couleurs", "Auteurs")) {
                            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                value!!.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { name ->
                                    Text(
                                        name, style = MaterialTheme.typography.bodyMedium, color = cs.primary,
                                        textDecoration = TextDecoration.Underline, modifier = Modifier.clickable { onAuthor(name) },
                                    )
                                }
                            }
                        } else Text(value!!, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

/** Sections des tomes, dans l'ordre du web : tomes d'abord, les à-côtés ensuite. */
private val VOLUME_GROUP_LABELS = linkedMapOf(
    "volume" to "Tomes", "episode" to "Épisodes", "integral" to "Intégrales", "hs" to "Hors-série", "special" to "Spéciaux",
)

/** Un one-shot reste un tome, même si une vieille ligne porte encore la marque « spécial ». */
private fun volumeGroup(v: VolumeRow, oneshot: Boolean): String = when {
    oneshot && !v.isIntegral && !v.isHs && !v.isEpisode -> "volume"
    v.isIntegral -> "integral"
    v.isHs -> "hs"
    v.isEpisode -> "episode"
    v.isSpecial -> "special"
    else -> "volume"
}

private fun volumeGroups(list: List<VolumeRow>, oneshot: Boolean): List<Pair<String, List<VolumeRow>>> {
    val byKey = list.groupBy { volumeGroup(it, oneshot) }
    return VOLUME_GROUP_LABELS.keys.filter { byKey.containsKey(it) }.map { it to byKey.getValue(it) }
}

/** En-tête d'une section de tomes : appui pour replier, compte possédés / total. */
@Composable
private fun VolumeGroupHeader(label: String, owned: Int, total: Int, collapsed: Boolean, onToggle: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (collapsed) Icons.Outlined.ChevronRight else Icons.Outlined.ExpandMore, contentDescription = if (collapsed) "Déplier" else "Replier", tint = cs.onSurfaceVariant)
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text("$owned / $total", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
    }
}

/** Menu d'une série (appui long sur son nom) : mise à jour, scan, édition, suppression. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeriesActions(
    title: String, onDismiss: () -> Unit, onReadAlso: () -> Unit, onHistory: () -> Unit, onUpdate: () -> Unit, onScan: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit,
) {
    @Composable
    fun Entry(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color? = null, action: () -> Unit) {
        androidx.compose.material3.ListItem(
            headlineContent = { Text(label, color = tint ?: Color.Unspecified) },
            leadingContent = { Icon(icon, contentDescription = null, tint = tint ?: MaterialTheme.colorScheme.onSurfaceVariant) },
            modifier = Modifier.clickable { onDismiss(); action() },
        )
    }
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            Entry(Icons.Outlined.Sync, "Mettre à jour (Bédéthèque)", action = onUpdate)
            Entry(Icons.Outlined.Refresh, "Scanner les fichiers", action = onScan)
            Entry(Icons.Outlined.Edit, "Éditer la série", action = onEdit)
            Entry(Icons.Outlined.MenuBook, "À lire aussi", action = onReadAlso)
            Entry(Icons.Outlined.History, "Historique", action = onHistory)
            Entry(Icons.Outlined.Delete, "Supprimer la série", MaterialTheme.colorScheme.error, onDelete)
        }
    }
}

/** Avis des lecteurs d'un tome (Bédéthèque), comme la fenêtre du site. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReviewsSheet(api: BullarrApi, v: VolumeRow, title: String, onDismiss: () -> Unit) {
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    var data by remember(v.id) { mutableStateOf<JSONObject?>(null) }
    var error by remember(v.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(v.id) {
        try { data = api.getObject("api/bedetheque/reviews/volume/${v.id}") } catch (e: Exception) { error = e.readable() }
    }
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        val d = data
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp)) {
            item {
                Text("Avis des lecteurs", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            error?.let { item { ErrorCard(it, Modifier.padding(top = 12.dp)) } }
            if (error == null && d == null) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp)) }
            if (d != null && !d.optBoolean("success", true)) item { EmptyState(d.str("error") ?: "Avis introuvables") }
            if (d != null && d.optBoolean("success", true)) {
                val reviews = d.optJSONArray("reviews").objects()
                if (reviews.isEmpty()) item { EmptyState("Aucun avis publié pour ce tome.") }
                items(reviews) { r ->
                    val rating = r.int("rating")
                    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(r.str("author") ?: "Anonyme", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text(
                                listOfNotNull(rating?.let { "★".repeat(it) + "☆".repeat(5 - it) }, r.str("date")).joinToString(" • "),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        r.str("text")?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                item {
                    d.str("album_url")?.let { url ->
                        TextButton(onClick = { runCatching { uri.openUri(url) } }, modifier = Modifier.padding(top = 12.dp)) { Text("Voir la page sur Bédéthèque") }
                    }
                }
            }
        }
    }
}

/** « À lire aussi » de la série (Bédéthèque) : un appui ouvre la fiche, l'éclair ajoute la série et lance son téléchargement. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadAlsoSheet(vm: AppViewModel, api: BullarrApi, seriesId: Int, seriesTitle: String, onDismiss: () -> Unit) {
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    val context = androidx.compose.ui.platform.LocalContext.current
    var readAlso by remember(seriesId) { mutableStateOf<List<JSONObject>?>(null) }
    var error by remember(seriesId) { mutableStateOf<String?>(null) }
    LaunchedEffect(seriesId) {
        try { readAlso = api.getObject("api/bedetheque/read-also/$seriesId").optJSONArray("items").objects() } catch (e: Exception) { error = e.readable() }
    }
    // Comme le bouton « + » du site, mais l'ajout lance aussi la recherche et le téléchargement.
    fun addAndDownload(index: Int, item: JSONObject) {
        val title = item.str("title") ?: "(sans titre)"
        val libraryId = vm.selectedLibrary?.id ?: vm.libraries.firstOrNull()?.id
        launchAction(vm.viewModelScope, context, "« $title » ajoutée, recherche et téléchargement lancés") {
            val added = api.postObject(
                "api/bedetheque/add-series",
                JSONObject().put("url", item.str("url")).put("library_id", libraryId ?: throw ApiException("Aucune bibliothèque disponible")),
            )
            val id = added.int("series_id")
            readAlso = readAlso?.mapIndexed { i, r -> if (i == index) r.apply { put("series_id", id) } else r }
        }
    }
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        val list = readAlso
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp)) {
            item {
                Text("À lire aussi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Sélection de Bédéthèque pour les lecteurs de « $seriesTitle ».", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            error?.let { item { ErrorCard(it, Modifier.padding(top = 12.dp)) } }
            if (error == null && list == null) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp)) }
            if (list != null && list.isEmpty()) item { EmptyState("Aucune recommandation trouvée sur cette fiche Bédéthèque.") }
            if (list != null) itemsIndexed(list) { index, r ->
                val ownId = r.int("series_id")
                Row(
                    Modifier.fillMaxWidth().clickable {
                        if (ownId != null) {
                            onDismiss()
                            vm.openSeriesById(ownId, r.str("title") ?: "")
                        } else r.str("url")?.let { u -> runCatching { uri.openUri(u) } }
                    }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(r.str("title") ?: "(sans titre)", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    if (ownId != null) Icon(Icons.Outlined.ChevronRight, contentDescription = "Ouvrir la série", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    else {
                        Logo("bedetheque", "Ouvrir sur Bédéthèque", 20.dp, tip = false)
                        IconButton(onClick = { addAndDownload(index, r) }) {
                            Icon(Icons.Outlined.Bolt, contentDescription = "Ajouter la série et la télécharger", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

/** Autres séries d'un auteur (sa fiche Bédéthèque), comme la fenêtre du site. L'ajout suit le réglage du serveur. */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun AuthorAlbumsSheet(vm: AppViewModel, api: BullarrApi, name: String, link: String, onDismiss: () -> Unit) {
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    val context = androidx.compose.ui.platform.LocalContext.current
    var albums by remember(link) { mutableStateOf<List<JSONObject>?>(null) }
    var error by remember(link) { mutableStateOf<String?>(null) }
    LaunchedEffect(link) {
        try { albums = api.getObject("api/bedetheque/authors/albums", mapOf("url" to link)).optJSONArray("albums").objects() } catch (e: Exception) { error = e.readable() }
    }
    // Lignes en cours d'ajout (plusieurs possibles) : leur bouton devient un indicateur.
    var pending by remember { mutableStateOf(setOf<Int>()) }
    // Déjà en bibliothèque, ou ajoutée pendant la session (mémorisé dans le ViewModel : survit à la fermeture de la fenêtre).
    fun isAdded(a: JSONObject) = a.optBoolean("already_in_library") || a.str("bedetheque_url")?.let { it in vm.addedBedethequeUrls } == true
    // Sélection multiple : appui long sur un album, puis appuis pour ajouter ou retirer d'autres.
    var selected by remember { mutableStateOf(setOf<Int>()) }
    fun toggle(index: Int) { selected = if (index in selected) selected - index else selected + index }
    suspend fun add(index: Int, a: JSONObject) {
        val libraryId = vm.selectedLibrary?.id ?: vm.libraries.firstOrNull()?.id ?: throw ApiException("Aucune bibliothèque disponible")
        val url = a.str("bedetheque_url")
        pending = pending + index
        try {
            api.postObject("api/bedetheque/add-series", JSONObject().put("url", url).put("library_id", libraryId))
            url?.let { vm.markAdded(it) }
        } finally {
            pending = pending - index
        }
    }
    fun addSelected() {
        val chosen = selected.sorted().mapNotNull { i -> albums?.getOrNull(i)?.takeIf { !isAdded(it) && i !in pending }?.let { i to it } }
        selected = emptySet()
        if (chosen.isEmpty()) return
        launchAction(vm.viewModelScope, context, "${chosen.size} série(s) ajoutée(s)") {
            var failed = 0
            chosen.forEach { (index, a) ->
                try { add(index, a) }
                catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    failed++
                }
            }
            if (failed > 0) throw ApiException("$failed série(s) n'ont pas pu être ajoutée(s)")
        }
    }
    fun addAlbum(index: Int, a: JSONObject) {
        val title = a.str("title") ?: "(sans titre)"
        launchAction(vm.viewModelScope, context, "« $title » ajoutée") { add(index, a) }
    }
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        val list = albums
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp)) {
            item {
                Text("Autres séries", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selected.isNotEmpty()) item {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${selected.size} sélectionné(s)", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = { addSelected() }) {
                        Icon(Icons.Outlined.LibraryAdd, contentDescription = "Ajouter la sélection", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            error?.let { item { ErrorCard(it, Modifier.padding(top = 12.dp)) } }
            if (error == null && list == null) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp)) }
            if (list != null && list.isEmpty()) item { EmptyState("Aucune autre série trouvée pour cet auteur.") }
            if (list != null) itemsIndexed(list) { index, a ->
                val years = listOfNotNull(a.str("year_start"), a.str("year_end")).distinct().joinToString(" – ")
                Row(
                    Modifier.fillMaxWidth()
                        .background(if (index in selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                        .combinedClickable(onClick = { if (selected.isNotEmpty()) toggle(index) }, onLongClick = { toggle(index) })
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(a.str("title") ?: "(sans titre)", style = MaterialTheme.typography.bodyMedium)
                        if (years.isNotEmpty()) Text(years, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    // Lien vers la fiche de l'album sur Bédéthèque.
                    a.str("bedetheque_url")?.let { u ->
                        Logo("bedetheque", "Ouvrir sur Bédéthèque", 20.dp, Modifier.clickable { runCatching { uri.openUri(u) } }, tip = false)
                    }
                    when {
                        isAdded(a) -> Icon(Icons.Outlined.CheckCircle, contentDescription = "Dans la bibliothèque", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(start = 8.dp))
                        index in pending -> CircularProgressIndicator(Modifier.padding(start = 8.dp).size(20.dp), strokeWidth = 2.dp)
                        else -> IconButton(onClick = { addAlbum(index, a) }) {
                            Icon(Icons.Outlined.LibraryAdd, contentDescription = "Ajouter cette série", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

/** Couverture en grand, plein écran ; un appui la ferme. */
@Composable
private fun FullCover(url: String?, onDismiss: () -> Unit) {
    var bitmap by remember(url) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(url) { if (url != null) bitmap = CoverLoader.load(url, 1600, key = "$url#big") }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
            val b = bitmap
            if (b != null) Image(b.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            else CircularProgressIndicator(color = Color.White)
        }
    }
}

/** Menu d'actions d'un tome (appui long). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VolumeActions(
    v: VolumeRow, title: String, onDismiss: () -> Unit, onEdit: () -> Unit,
    onRefresh: () -> Unit, onMeta: () -> Unit, onReviews: () -> Unit, onHistory: () -> Unit, onDelete: () -> Unit,
) {
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
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
            ItemActions.shopLinks(v.isbn ?: title.replace(" · ", " ")).forEach { (shop, url) ->
                Entry(Icons.Outlined.ShoppingCart, "Acheter sur $shop") { runCatching { uri.openUri(url) } }
            }
            Entry(Icons.Outlined.Star, "Lire les avis", action = onReviews)
            Entry(Icons.Outlined.History, "Historique", action = onHistory)
            Entry(Icons.Outlined.Edit, "Éditer les métadonnées", action = onEdit)
            if (v.owned) Entry(Icons.Outlined.Refresh, "Actualiser taille et pages", action = onRefresh)
            Entry(Icons.Outlined.Sync, "Recharger la fiche Bédéthèque", action = onMeta)
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
