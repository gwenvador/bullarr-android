package io.github.gwenvador.bullarr.ui

import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Book
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.OfflinePin
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Sync
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
    val filters = vm.filters
    // remember : sans lui, l'état était recréé à chaque recomposition et la feuille ne s'ouvrait jamais.
    var sheet by remember { androidx.compose.runtime.mutableStateOf(false) }
    val filtered = remember(vm.series, q, filters, vm.sort, vm.sortDesc) {
        val terms = normalize(q).split(' ').filter { it.isNotBlank() }
        vm.sort.sorted(vm.series.filter { s -> (terms.isEmpty() || terms.all { it in s.searchKey }) && filters.matches(s) }, vm.sortDesc)
    }
    var sortMenu by remember { androidx.compose.runtime.mutableStateOf(false) }
    // Sélection multiple (appui long sur une série) : MAJ Bédéthèque, scan, suppression.
    var selected by rememberSaveable { androidx.compose.runtime.mutableStateOf(emptyList<Int>()) }
    var addSeries by remember { androidx.compose.runtime.mutableStateOf(false) }
    var confirmDelete by remember { androidx.compose.runtime.mutableStateOf(false) }
    var confirmDownload by remember { androidx.compose.runtime.mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val offlineBySeries = remember(vm.offline.version) { vm.offline.downloadedBySeries() }
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.activity.compose.BackHandler(enabled = selected.isNotEmpty()) { selected = emptyList() }
    fun toggle(id: Int) { selected = if (id in selected) selected - id else selected + id }
    fun batch(ok: String, reload: Boolean, one: suspend (io.github.gwenvador.bullarr.data.BullarrApi, Int) -> Unit) {
        val api = vm.api ?: return
        val ids = selected
        selected = emptyList()
        launchAction(scope, context, "$ok (${ids.size})", then = { if (reload) vm.loadLibrary(force = true) }) { ids.forEach { one(api, it) } }
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (selected.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { selected = emptyList() }) { Icon(Icons.Outlined.Clear, contentDescription = "Annuler la sélection") }
                Text("${selected.size}", style = MaterialTheme.typography.titleMedium, maxLines = 1, modifier = Modifier.weight(1f))
                IconButton(onClick = { selected = filtered.map { it.id } }) { Icon(Icons.Outlined.SelectAll, contentDescription = "Tout sélectionner") }
                IconButton(onClick = { confirmDownload = true }) { Icon(Icons.Outlined.DownloadForOffline, contentDescription = "Télécharger hors ligne") }
                IconButton(onClick = { batch("Mise à jour lancée", false) { a, id -> ItemActions.updateSeriesMetadata(a, id) } }) { Icon(Icons.Outlined.Sync, contentDescription = "Mettre à jour les métadonnées (Bédéthèque)") }
                IconButton(onClick = { batch("Scan lancé", true) { a, id -> ItemActions.scanSeries(a, id) } }) { Icon(Icons.Outlined.Refresh, contentDescription = "Scanner les fichiers") }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, contentDescription = "Supprimer", tint = MaterialTheme.colorScheme.error) }
            } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                CompactSearchField(
                    value = q, onValueChange = { vm.query = it },
                    placeholder = "Rechercher une série, un auteur…",
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
                                    if (vm.sort == o) vm.sortDesc = !vm.sortDesc else { vm.sort = o; vm.sortDesc = o.defaultDesc }
                                    sortMenu = false
                                },
                            )
                        }
                    }
                }
                IconButton(onClick = { addSeries = true }) { Icon(Icons.Outlined.Add, contentDescription = "Ajouter une série") }
                // Bouton de filtres, avec le nombre de groupes actifs.
                androidx.compose.material3.BadgedBox(badge = {
                    if (filters.activeCount > 0) androidx.compose.material3.Badge { Text("${filters.activeCount}") }
                }) {
                    IconButton(onClick = { sheet = true }) {
                        Icon(Icons.Outlined.FilterList, contentDescription = "Filtres")
                    }
                }
            }
            if (vm.libraries.size > 1) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(vm.libraries, key = { it.id }) { lib ->
                        FilterChip(selected = lib.id == vm.selectedLibrary?.id, onClick = { vm.selectLibrary(lib) }, label = { Text(lib.name) })
                    }
                }
            }
            vm.selectedLibrary?.let { lib ->
                Text(
                    "${lib.name} · ${filtered.size} série(s)" + (lib.volumesCount?.let { " · $it tomes" } ?: ""),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (addSeries) SeriesAddDialog(vm, onDismiss = { addSeries = false }, onAdded = { _, _ -> })
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

        PullToRefreshBox(
            isRefreshing = vm.libraryLoading && vm.series.isNotEmpty(),
            onRefresh = { vm.loadLibrary(force = true) },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                vm.libraryError?.let { item {
                    Column {
                        ErrorCard(it, Modifier.padding(16.dp), onRetry = { vm.loadLibrary(force = true) })
                        androidx.compose.material3.TextButton(
                            onClick = { vm.subTab[Section.Library] = 1 },
                            modifier = Modifier.padding(horizontal = 8.dp),
                        ) { Text("Sans réseau ? Ouvrir les albums hors ligne") }
                    }
                } }
                if (!vm.libraryLoading && vm.libraryError == null && vm.libraries.isEmpty()) {
                    item { EmptyState("Aucune bibliothèque", "Créez-en une dans l'interface web de Bullarr.") }
                }
                if (filtered.isEmpty() && vm.series.isNotEmpty()) item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        EmptyState("Aucun résultat", if (q.isNotBlank()) "pour « $q »" + (if (!filters.isEmpty) " avec ces filtres" else "") else "Aucune série ne correspond aux filtres.")
                        if (!filters.isEmpty) androidx.compose.material3.TextButton(onClick = { vm.filters = LibraryFilters() }) { Text("Réinitialiser les filtres") }
                    }
                }
                items(filtered, key = { it.id }) { s ->
                    SeriesRow(
                        s, vm.coverUrl(s.coverPath), selected = s.id in selected,
                        offline = (offlineBySeries.first[s.id] ?: 0) + (offlineBySeries.second[s.title] ?: 0),
                        onLongClick = { toggle(s.id) },
                    ) { if (selected.isNotEmpty()) toggle(s.id) else onOpen(s) }
                    HorizontalDivider(Modifier.padding(start = 84.dp), color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
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
            }
        }
        if (selected) Icon(Icons.Outlined.CheckCircle, contentDescription = "Sélectionnée", tint = MaterialTheme.colorScheme.primary)
    }
}
