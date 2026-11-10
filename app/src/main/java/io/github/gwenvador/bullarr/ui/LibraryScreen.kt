package io.github.gwenvador.bullarr.ui

import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.OfflinePin
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Search
import io.github.gwenvador.bullarr.ui.FilterChip
import androidx.compose.material3.HorizontalDivider
import io.github.gwenvador.bullarr.ui.Icon
import io.github.gwenvador.bullarr.ui.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SortByAlpha
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.gwenvador.bullarr.data.SeriesSummary
import io.github.gwenvador.bullarr.data.normalize

@Composable
fun LibraryScreen(vm: AppViewModel, onOpen: (SeriesSummary) -> Unit) {
    LaunchedEffect(vm.api) { vm.loadLibrary() }
    val q = vm.query
    var addQuery by remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    val filters = vm.filters
    // remember : sans lui, l'état était recréé à chaque recomposition et la feuille ne s'ouvrait jamais.
    var sheet by remember { androidx.compose.runtime.mutableStateOf(false) }
    val inName = vm.searchInName
    val inTags = vm.searchInTags
    val filtered = remember(vm.series, q, filters, vm.sort, vm.sortDesc, inName, inTags) {
        val terms = normalize(q).split(' ').filter { it.isNotBlank() }
        // Chaque mot doit se trouver dans le nom ou dans les tags, selon les cases cochées.
        fun hit(s: SeriesSummary, t: String) = (inName && t in s.nameKey) || (inTags && t in s.tagsKey)
        vm.sort.sorted(vm.series.filter { s -> (terms.isEmpty() || terms.all { hit(s, it) }) && filters.matches(s) }, vm.sortDesc)
    }
    androidx.compose.runtime.SideEffect { vm.visibleSeries = filtered }
    var sortMenu by remember { androidx.compose.runtime.mutableStateOf(false) }
    // Sélection multiple (appui long sur une série) : MAJ Bédéthèque, scan, suppression.
    var selected by rememberSaveable { androidx.compose.runtime.mutableStateOf(emptyList<Int>()) }
    var confirmDelete by remember { androidx.compose.runtime.mutableStateOf(false) }
    var confirmDownload by remember { androidx.compose.runtime.mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val offlineBySeries = remember(vm.offline.version) { vm.offline.downloadedBySeries() }
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.activity.compose.BackHandler(enabled = selected.isNotEmpty()) { selected = emptyList() }
    // Appui long sur une série : menu pour l'ajouter / la retirer des favoris et de « à lire plus tard ».
    var menuFor by remember { androidx.compose.runtime.mutableStateOf<SeriesSummary?>(null) }
    fun setList(sm: SeriesSummary, toReadLater: Boolean? = null, isFavorite: Boolean? = null) {
        val api = vm.api ?: return
        launchAction(scope, context, "Liste mise à jour") {
            ItemActions.setSeriesReadingLists(api, sm.id, toReadLater, isFavorite)
            vm.updateSeriesRow(sm.id) { row -> row.copy(toReadLater = toReadLater ?: row.toReadLater, isFavorite = isFavorite ?: row.isFavorite) }
        }
    }
    fun toggle(id: Int) { selected = if (id in selected) selected - id else selected + id }
    fun batch(ok: String, reload: Boolean, one: suspend (io.github.gwenvador.bullarr.data.BullarrApi, Int) -> Unit) {
        val api = vm.api ?: return
        val ids = selected
        selected = emptyList()
        launchAction(scope, context, "$ok (${ids.size})", then = { if (reload) vm.loadLibrary(force = true) }) { ids.forEach { one(api, it) } }
    }

    // En-tête masqué quand on fait défiler vers le bas, réaffiché dès qu'on remonte (seuil pour éviter
    // les sauts sur de petits mouvements) ; toujours visible pendant une sélection.
    var headerShown by remember { androidx.compose.runtime.mutableStateOf(true) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val headerScroll = remember(density) {
        val threshold = with(density) { 24.dp.toPx() }
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            var acc = 0f
            override fun onPostScroll(
                consumed: androidx.compose.ui.geometry.Offset, available: androidx.compose.ui.geometry.Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource,
            ): androidx.compose.ui.geometry.Offset {
                val dy = consumed.y
                if (dy == 0f) { if (available.y > 0f) headerShown = true; return androidx.compose.ui.geometry.Offset.Zero }
                if ((acc < 0f) != (dy < 0f)) acc = 0f
                acc += dy
                if (acc < -threshold) { headerShown = false; acc = 0f }
                else if (acc > threshold) { headerShown = true; acc = 0f }
                return androidx.compose.ui.geometry.Offset.Zero
            }
        }
    }

    Column(Modifier.fillMaxSize().nestedScroll(headerScroll)) {
        // En-tête serré : barre d'outils, portée de recherche et infos sans marges perdues.
        androidx.compose.animation.AnimatedVisibility(
            visible = headerShown || selected.isNotEmpty(),
            enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut(),
        ) {
        Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (selected.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { selected = emptyList() }) { Icon(Icons.Outlined.Clear, contentDescription = "Annuler la sélection") }
                Text("${selected.size}", style = MaterialTheme.typography.titleMedium, maxLines = 1, modifier = Modifier.weight(1f))
                IconButton(onClick = { selected = filtered.map { it.id } }) { Icon(Icons.Outlined.SelectAll, contentDescription = "Tout sélectionner") }
                IconButton(onClick = { confirmDownload = true }) { Icon(Icons.Outlined.DownloadForOffline, contentDescription = "Télécharger hors ligne") }
                // Métadonnées, scan et suppression : réservés aux administrateurs.
                if (!vm.isReader) {
                IconButton(onClick = { batch("Mise à jour lancée", false) { a, id -> ItemActions.updateSeriesMetadata(a, id) } }) { Icon(Icons.Outlined.Sync, contentDescription = "Mettre à jour les métadonnées (Bédéthèque)") }
                IconButton(onClick = { batch("Scan lancé", true) { a, id -> ItemActions.scanSeries(a, id) } }) { Icon(Icons.Outlined.Refresh, contentDescription = "Scanner les fichiers") }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, contentDescription = "Supprimer", tint = MaterialTheme.colorScheme.error) }
                }
            } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Pas de « + » ici : l'ajout de série se fait depuis Recherche.
                CompactSearchField(
                    value = q, onValueChange = { vm.query = it },
                    placeholder = when {
                        inName && inTags -> "Série, auteur, tag…"
                        inTags -> "Tag…"
                        else -> "Série, auteur…"
                    },
                    modifier = Modifier.weight(1f),
                )
                Box {
                    IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = "Trier") }
                    androidx.compose.material3.DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        LibrarySort.entries.forEach { o ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(o.label) },
                                leadingIcon = { Icon(when (o) {
                                    LibrarySort.Title -> Icons.Outlined.SortByAlpha
                                    LibrarySort.Popularity -> Icons.Outlined.ThumbUp
                                    LibrarySort.Date -> Icons.Outlined.Schedule
                                    LibrarySort.Size -> Icons.Outlined.Storage
                                }, contentDescription = null) },
                                trailingIcon = {
                                    if (vm.sort == o) Icon(if (vm.sortDesc) Icons.Outlined.ArrowDownward else Icons.Outlined.ArrowUpward, contentDescription = if (vm.sortDesc) "Décroissant" else "Croissant")
                                },
                                // Même critère : inverse l'ordre ; sinon ordre par défaut du critère.
                                onClick = {
                                    if (vm.sort == o) vm.setSort(o, !vm.sortDesc) else vm.setSort(o, o.defaultDesc)
                                    sortMenu = false
                                },
                            )
                        }
                    }
                }
                // Bouton de filtres, avec le nombre de groupes actifs.
                androidx.compose.material3.BadgedBox(badge = {
                    if (filters.activeCount > 0) androidx.compose.material3.Badge { Text("${filters.activeCount}") }
                }) {
                    IconButton(onClick = { sheet = true }) {
                        Icon(Icons.Outlined.FilterList, contentDescription = "Filtres")
                    }
                }
                // Albums hors ligne (remplace l'ancien onglet « Hors ligne »).
                IconButton(onClick = { vm.subTab[Section.Library] = 1 }) {
                    Icon(Icons.Outlined.OfflinePin, contentDescription = "Albums hors ligne")
                }
                // Liste ↔ grille de couvertures.
                IconButton(onClick = { vm.chooseLibraryGrid(!vm.libraryGrid) }) {
                    Icon(
                        if (vm.libraryGrid) Icons.AutoMirrored.Outlined.ViewList else Icons.Outlined.GridView,
                        contentDescription = if (vm.libraryGrid) "Afficher en liste" else "Afficher en grille",
                    )
                }
            }
            // Portée de la recherche (affichée dès qu'on tape) : nom de la série et/ou tags ; au moins une reste cochée.
            if (selected.isEmpty() && (q.isNotBlank() || !inName || !inTags)) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Chercher dans", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FilterChip(selected = inName, onClick = { if (!inName || inTags) vm.searchInName = !inName }, label = { Text("Nom de la série") })
                FilterChip(selected = inTags, onClick = { if (!inTags || inName) vm.searchInTags = !inTags }, label = { Text("Tags") })
            }
            // Bibliothèque : nom + menu déroulant (tient avec n'importe quel nombre de bibliothèques) ; nombre de séries et
            // taille des miniatures sur la même ligne, à droite.
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                LibraryPicker(vm, Modifier.weight(1f))
                if (vm.libraryGrid) SizeSlider(vm.libraryCellSize, { vm.chooseLibraryCellSize(it) }, 80f..200f, Modifier.padding(end = 4.dp))
            }
            vm.selectedLibrary?.let { lib ->
                Text(
                    "${filtered.size} série(s)" + (lib.volumesCount?.let { " · $it tomes" } ?: ""),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp, bottom = 2.dp), maxLines = 1,
                )
            }
        }
        }
        menuFor?.let { sm ->
            // Relit la série à jour : le menu reflète l'état courant après chaque action.
            val cur = vm.series.firstOrNull { it.id == sm.id } ?: sm
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { menuFor = null },
                title = { Text(cur.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                text = {
                    Column {
                        androidx.compose.material3.TextButton(onClick = { menuFor = null; setList(cur, isFavorite = !cur.isFavorite) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(if (cur.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder, contentDescription = null)
                            Spacer(Modifier.width(12.dp))
                            Text(if (cur.isFavorite) "Retirer des favoris" else "Ajouter aux favoris", Modifier.weight(1f))
                        }
                        androidx.compose.material3.TextButton(onClick = { menuFor = null; setList(cur, toReadLater = !cur.toReadLater) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(if (cur.toReadLater) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder, contentDescription = null)
                            Spacer(Modifier.width(12.dp))
                            Text(if (cur.toReadLater) "Retirer de « à lire plus tard »" else "Ajouter à « à lire plus tard »", Modifier.weight(1f))
                        }
                        androidx.compose.material3.TextButton(onClick = { menuFor = null; toggle(cur.id) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Outlined.CheckCircle, contentDescription = null)
                            Spacer(Modifier.width(12.dp))
                            Text("Sélectionner", Modifier.weight(1f))
                        }
                    }
                },
                confirmButton = { androidx.compose.material3.TextButton(onClick = { menuFor = null }) { Text("Fermer") } },
            )
        }
        if (confirmDownload) ConfirmDialog(
            "Télécharger ${selected.size} série(s) hors ligne ?",
            "Tous les tomes possédés des séries sélectionnées seront téléchargés sur le téléphone. Cela peut être volumineux.",
            "Télécharger", onConfirm = {
                val api = vm.api
                val picked = filtered.filter { it.id in selected }
                selected = emptyList()
                if (api != null) launchAction(scope, context, "Téléchargements lancés") {
                    var n = 0
                    picked.forEach { sm ->
                        val d = io.github.gwenvador.bullarr.data.parseSeriesDetail(api.getObject("api/series/${sm.id}"))
                        d.volumes.filter { it.owned && !vm.offline.isDownloaded(it.id) && !vm.offline.active.containsKey(it.id) }.forEach {
                            vm.offline.start(it.id, "${d.title} · ${it.label}"); n++
                        }
                    }
                    if (n == 0) throw io.github.gwenvador.bullarr.data.ApiException("Rien à télécharger : tout est déjà hors ligne.")
                }
            }, onDismiss = { confirmDownload = false },
        )
        if (confirmDelete) ConfirmDialog(
            "Supprimer ${selected.size} série(s) ?",
            "Les dossiers et fichiers des séries sélectionnées seront supprimés du disque. Cette action est irréversible.",
            "Supprimer", onConfirm = { batch("Séries supprimées", true) { a, id -> ItemActions.deleteSeries(a, id) } }, onDismiss = { confirmDelete = false },
        )
        if (sheet) LibraryFilterSheet(vm.series, filters, filtered.size, onChange = { vm.filters = it }, onDismiss = { sheet = false })
        if (vm.libraryLoading && vm.series.isEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))

        // « Continuer la lecture » lu dans Komga par le serveur, comme le site ; sans Komga (ou hors
        // réseau), les albums commencés sur l'appareil. Tirer la liste resynchronise avec Komga.
        var keepTick by remember { androidx.compose.runtime.mutableIntStateOf(0) }
        var syncFirst by remember { androidx.compose.runtime.mutableStateOf(false) }
        var syncing by remember { androidx.compose.runtime.mutableStateOf(false) }
        LaunchedEffect(vm.selectedLibrary?.id, vm.progressVersion, keepTick) {
            val api = vm.api ?: run { vm.keepReadingLocal = true; return@LaunchedEffect }
            if (syncFirst) { syncFirst = false; syncing = true; runCatching { io.github.gwenvador.bullarr.data.syncKomgaReading(api) } }
            runCatching { io.github.gwenvador.bullarr.data.loadKeepReading(api, vm.selectedLibrary?.id) }
                .onSuccess { vm.keepReading = it; vm.keepReadingLocal = it == null }
                .onFailure { if (vm.keepReading == null) vm.keepReadingLocal = true }
            syncing = false
        }
        fun syncKeepReading() { syncFirst = true; keepTick++ }
        // Le rond n'apparaît que si l'utilisateur a tiré la liste : le rafraîchissement silencieux
        // (après l'affichage du cache) ne doit pas masquer les couvertures.
        var pulled by remember { androidx.compose.runtime.mutableStateOf(false) }
        LaunchedEffect(vm.libraryLoading) { if (!vm.libraryLoading) pulled = false }
        PullToRefreshBox(
            isRefreshing = pulled && vm.libraryLoading,
            onRefresh = { pulled = true; vm.loadLibrary(force = true); syncKeepReading() },
            modifier = Modifier.fillMaxSize(),
        ) {
            // Albums commencés : en tête, tant qu'aucune recherche, filtre ni sélection n'est en cours.
            val local = remember(vm.progressVersion) { vm.reads.recent() }
            // Pendant le premier chargement, rien plutôt que la liste de l'appareil (évite le saut 4 → 20).
            val recent = vm.keepReading ?: if (vm.keepReadingLocal) local else emptyList()
            val showRecent = recent.isNotEmpty() && q.isBlank() && filters.isEmpty && selected.isEmpty()
            val header: @Composable () -> Unit = {
                Column {
                    vm.libraryError?.let {
                        ErrorCard(it, Modifier.padding(16.dp), onRetry = { vm.loadLibrary(force = true) })
                        androidx.compose.material3.TextButton(
                            onClick = { vm.subTab[Section.Library] = 1 },
                            modifier = Modifier.padding(horizontal = 8.dp),
                        ) { Text("Sans réseau ? Ouvrir les albums hors ligne") }
                    }
                    if (!vm.libraryLoading && vm.libraryError == null && vm.libraries.isEmpty()) {
                        EmptyState("Aucune bibliothèque", "Créez-en une dans l'interface web de Bullarr.")
                    }
                    if (showRecent) ContinueReading(vm, recent)
                    if (filtered.isEmpty() && vm.series.isNotEmpty()) Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        EmptyState("Aucun résultat", if (q.isNotBlank()) "pour « $q »" + (if (!filters.isEmpty) " avec ces filtres" else "") else "Aucune série ne correspond aux filtres.")
                        if (!filters.isEmpty) androidx.compose.material3.TextButton(onClick = { vm.filters = LibraryFilters() }) { Text("Réinitialiser les filtres") }
                    }
                }
            }
            // Recherche en cours : proposer d'ajouter la BD (fiche Bédéthèque) après les séries trouvées.
            val addRow: @Composable () -> Unit = {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { addQuery = q.trim() }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Outlined.LibraryAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    Text((if (vm.isReader) "Demander « " else "Ajouter « ") + "${q.trim()} »", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            fun offlineOf(s: SeriesSummary) = (offlineBySeries.first[s.id] ?: 0) + (offlineBySeries.second[s.title] ?: 0)
            fun longClick(s: SeriesSummary) { if (selected.isNotEmpty()) toggle(s.id) else menuFor = s }
            fun click(s: SeriesSummary) { if (selected.isNotEmpty()) toggle(s.id) else onOpen(s) }
            if (vm.libraryGrid) {
                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    androidx.compose.foundation.lazy.grid.GridCells.Adaptive(vm.libraryCellSize.dp), Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item(key = "header", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) { header() }
                    items(filtered.size, key = { filtered[it].id }) { i ->
                        val s = filtered[i]
                        SeriesCell(s, vm.coverUrl(s.coverPath), selected = s.id in selected, offline = offlineOf(s), onLongClick = { longClick(s) }) { click(s) }
                    }
                    if (q.isNotBlank()) item(key = "add", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) { addRow() }
                }
            } else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 0.dp, bottom = 8.dp)) {
                item(key = "header") { header() }
                items(filtered, key = { it.id }) { s ->
                    SeriesRow(s, vm.coverUrl(s.coverPath), selected = s.id in selected, offline = offlineOf(s), onLongClick = { longClick(s) }) { click(s) }
                    HorizontalDivider(Modifier.padding(start = 84.dp), color = MaterialTheme.colorScheme.outlineVariant)
                }
                if (q.isNotBlank()) item(key = "add") { addRow() }
            }
            addQuery?.let { aq -> SeriesAddDialog(vm, onDismiss = { addQuery = null }, initialQuery = aq, onAdded = { sid, title -> addQuery = null; vm.openSeriesById(sid, title) }) }
        }
    }
}

/** « Continuer la lecture » : les albums commencés, couverture et avancement ; un appui reprend la lecture. */
@Composable
private fun ContinueReading(vm: AppViewModel, recent: List<io.github.gwenvador.bullarr.data.RecentRead>) {
    val cs = MaterialTheme.colorScheme
    // Replié par défaut ; l'état est mémorisé sur l'appareil (changer de page ou relancer l'app le garde).
    val prefs = androidx.compose.ui.platform.LocalContext.current.getSharedPreferences("library_ui", android.content.Context.MODE_PRIVATE)
    var expanded by remember { androidx.compose.runtime.mutableStateOf(prefs.getBoolean("keep_reading_open", false)) }
    Column {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded; prefs.edit().putBoolean("keep_reading_open", expanded).apply() }.padding(start = 16.dp, end = 12.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Continuer la lecture", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(" · ${recent.size}", style = MaterialTheme.typography.titleSmall, color = cs.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            androidx.compose.material3.Icon(
                if (expanded) androidx.compose.material.icons.Icons.Outlined.ExpandLess else androidx.compose.material.icons.Icons.Outlined.ExpandMore,
                contentDescription = if (expanded) "Replier" else "Déplier", tint = cs.onSurfaceVariant,
            )
        }
        if (expanded) LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(recent, key = { it.volumeId }) { r ->
                val series = vm.series.firstOrNull { it.id == r.seriesId }
                Column(
                    Modifier.width(92.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp)).clickable {
                        // Ouvrir la fiche sous la liseuse : le retour ramène à la série, pas à la bibliothèque.
                        r.seriesId?.takeIf { it > 0 }?.let { vm.openSeriesById(it, r.seriesTitle ?: "") }
                        vm.reading = ReaderTarget(r.volumeId, listOfNotNull(r.seriesTitle, r.label).joinToString(" · "))
                    },
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Cover((r.coverPath ?: series?.coverPath)?.let { vm.coverUrl(it) }, 92.dp)
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { ((r.page + 1).toFloat() / r.total.coerceAtLeast(1)).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(), color = cs.primary, trackColor = cs.surfaceVariant,
                    )
                    Text(r.seriesTitle ?: r.title ?: "", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(r.label, "p. ${r.page + 1}/${r.total}").joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        HorizontalDivider(Modifier.padding(top = if (expanded) 8.dp else 0.dp, bottom = 4.dp), color = cs.outlineVariant)
    }
}

/** Case de la grille : couverture arrondie, pastilles (favori, à lire, hors ligne) et titre. */
@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun SeriesCell(s: SeriesSummary, cover: String?, selected: Boolean, offline: Int, onLongClick: () -> Unit, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.clip(RoundedCornerShape(10.dp)).combinedClickable(onClick = onClick, onLongClick = onLongClick),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
            Cover(
                cover, maxWidth,
                Modifier.shadow(3.dp, RoundedCornerShape(8.dp)).clip(RoundedCornerShape(8.dp))
                    .then(if (selected) Modifier.border(3.dp, cs.primary, RoundedCornerShape(8.dp)) else Modifier),
            )
            if (s.isFavorite || s.toReadLater || offline > 0) Row(
                Modifier.align(Alignment.TopEnd).padding(5.dp)
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                val white = androidx.compose.ui.graphics.Color.White
                if (s.isFavorite) Icon(Icons.Filled.Star, contentDescription = "Favori", tint = white, modifier = Modifier.size(13.dp))
                if (s.toReadLater) Icon(Icons.Filled.Bookmark, contentDescription = "À lire plus tard", tint = white, modifier = Modifier.size(13.dp))
                if (offline > 0) Icon(Icons.Outlined.OfflinePin, contentDescription = "$offline album(s) hors ligne", tint = white, modifier = Modifier.size(13.dp))
            }
            if (selected) Icon(
                Icons.Filled.CheckCircle, contentDescription = "Sélectionnée", tint = cs.primary,
                modifier = Modifier.align(Alignment.TopStart).padding(5.dp).size(24.dp).background(cs.surface, androidx.compose.foundation.shape.CircleShape),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(s.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis,
            lineHeight = 16.sp, modifier = Modifier.padding(horizontal = 2.dp))
        val count = if (s.isOneshot) "One-shot" else s.totalKnown?.let { "${s.ownedTomes}/$it tomes" } ?: "${s.ownedTomes} tome(s)"
        Text(count, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, maxLines = 1, modifier = Modifier.padding(start = 2.dp, end = 2.dp, bottom = 4.dp))
    }
}

/** Nom de la bibliothèque courante ; s'il y en a plusieurs, un appui ouvre la liste pour en changer. */
@Composable
private fun LibraryPicker(vm: AppViewModel, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val many = vm.libraries.size > 1
    var open by remember { androidx.compose.runtime.mutableStateOf(false) }
    Box(modifier) {
        Row(
            Modifier.clip(RoundedCornerShape(8.dp)).then(if (many) Modifier.clickable { open = true } else Modifier)
                .padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(vm.selectedLibrary?.name.orEmpty(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            if (many) Icon(Icons.Filled.ArrowDropDown, contentDescription = "Changer de bibliothèque", tint = cs.onSurfaceVariant)
        }
        androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            vm.libraries.forEach { lib ->
                val cur = lib.id == vm.selectedLibrary?.id
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(lib.name, fontWeight = if (cur) FontWeight.SemiBold else FontWeight.Normal) },
                    trailingIcon = { if (cur) Icon(Icons.Filled.Check, contentDescription = "Bibliothèque affichée", tint = cs.primary) },
                    onClick = { open = false; if (!cur) vm.selectLibrary(lib) },
                )
            }
        }
    }
}

/** Petit curseur discret (piste fine, pastille de 12 dp) pour la taille des miniatures. */
@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
private fun SizeSlider(value: Float, onChange: (Float) -> Unit, range: ClosedFloatingPointRange<Float>, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.GridView, contentDescription = null, tint = cs.onSurfaceVariant, modifier = Modifier.size(14.dp))
        androidx.compose.material3.Slider(
            value = value, onValueChange = onChange, valueRange = range,
            modifier = Modifier.width(88.dp).height(24.dp).padding(horizontal = 6.dp),
            thumb = { Box(Modifier.size(12.dp).background(cs.primary, androidx.compose.foundation.shape.CircleShape)) },
            track = { st ->
                val f = ((st.value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
                Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(cs.outlineVariant)) {
                    Box(Modifier.fillMaxWidth(f).height(3.dp).background(cs.primary))
                }
            },
        )
        Icon(Icons.Outlined.Image, contentDescription = "Taille des miniatures", tint = cs.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun SeriesRow(s: SeriesSummary, cover: String?, selected: Boolean, offline: Int, onLongClick: () -> Unit, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(cover, 52.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(s.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val meta = listOfNotNull(s.author, s.year).joinToString(" · ")
            if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                val cs = MaterialTheme.colorScheme
                s.origin?.let { Logo(it.lowercase(), it, 20.dp) }
                val count = if (s.isOneshot) "One-shot" else s.totalKnown?.let { "${s.ownedTomes}/$it tomes" } ?: "${s.ownedTomes} tome(s)"
                val owned = if (s.isOneshot) null else s.totalKnown?.let { "${s.ownedTomes}/$it" } ?: "${s.ownedTomes}"
                IconTag(if (s.isOneshot) Icons.Outlined.Book else Icons.Outlined.CollectionsBookmark, count, cs.onSurfaceVariant, owned)
                if (offline > 0) IconTag(Icons.Outlined.OfflinePin, "$offline album(s) hors ligne", cs.primary, "$offline")
                if (s.missingCount > 0) IconTag(Icons.Outlined.ReportProblem, "${s.missingCount} manquant(s)", cs.tertiary, "${s.missingCount}")
                if (s.isFavorite) IconTag(Icons.Filled.Star, "Favori", cs.secondary)
                if (s.toReadLater) IconTag(Icons.Filled.Bookmark, "À lire plus tard", cs.secondary)
            }
        }
        if (selected) Icon(Icons.Outlined.CheckCircle, contentDescription = "Sélectionnée", tint = MaterialTheme.colorScheme.primary)
    }
}
