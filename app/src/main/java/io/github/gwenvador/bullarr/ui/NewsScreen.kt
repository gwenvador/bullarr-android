package io.github.gwenvador.bullarr.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.SolidColor
import io.github.gwenvador.bullarr.data.normalize
import io.github.gwenvador.bullarr.data.newsGuessSeriesTitle
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import io.github.gwenvador.bullarr.ui.Icon
import io.github.gwenvador.bullarr.ui.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.gwenvador.bullarr.data.str
import io.github.gwenvador.bullarr.data.bool
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.gwenvador.bullarr.data.DownloadJob
import io.github.gwenvador.bullarr.data.NewsItem
import io.github.gwenvador.bullarr.data.NewsType
import io.github.gwenvador.bullarr.data.formatBytes
import io.github.gwenvador.bullarr.data.parseEbdz
import io.github.gwenvador.bullarr.data.parseRss
import io.github.gwenvador.bullarr.data.parseTelegram
import io.github.gwenvador.bullarr.data.readable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import io.github.gwenvador.bullarr.data.SeriesSummary
import io.github.gwenvador.bullarr.data.parseLibraries
import io.github.gwenvador.bullarr.data.parseSeriesList
import io.github.gwenvador.bullarr.data.setNewsSeries
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.outlined.Add
import androidx.compose.foundation.layout.width

// Onglet 0 = Tout ; les suivants suivent NewsType.
private val TABS = listOf("Tout") + NewsType.entries.map { it.label }

@Composable
fun NewsScreen(vm: AppViewModel, onOpenSeries: (Int, String) -> Unit) {
    val api = vm.api ?: return
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val feeds = remember { mutableStateMapOf<NewsType, List<NewsItem>>() }
    val errors = remember { mutableStateMapOf<NewsType, String>() }
    var rssNote by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var rssRefreshing by remember { mutableStateOf(false) }
    var rssPass by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    // Entrée dont on choisit ou change la série (menu ⋮).
    var pickFor by remember { mutableStateOf<List<NewsItem>?>(null) }
    // Appui long sur l'en-tête d'un groupe : actions de tous ses albums.
    var groupMenu by remember { mutableStateOf<List<NewsItem>?>(null) }
    // Entrée dont on crée la série (dialogue d'ajout), puis rattachée à la nouvelle série.
    var addingFor by remember { mutableStateOf<List<NewsItem>?>(null) }

    // Mêmes réglages que la page web : premier lot des 100 entrées les plus récentes par source,
    // « Charger plus » récupère toute la fenêtre de 60 jours, puis « Charger une période plus
    // ancienne » ajoute 60 jours de plus (EBDZ et Telegram ; le RSS n'a pas d'historique).
    var daysWindow by rememberSaveable { mutableIntStateOf(NEWS_DAYS) }
    var fullWindow by remember { mutableStateOf(false) }
    var hasMoreEbdz by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }

    suspend fun load(type: NewsType, refresh: Boolean, limit: Int? = NEWS_INITIAL_LIMIT, olderThanDays: Int? = null) {
        errors.remove(type)
        val window = buildMap {
            put("days", daysWindow.toString())
            limit?.let { put("limit", it.toString()) }
            olderThanDays?.let { put("older_than_days", it.toString()) }
        }
        try {
            val loaded = when (type) {
                NewsType.RSS -> {
                    if (olderThanDays != null) return
                    val o = api.getObject("api/ebdz/rss/latest", if (refresh) mapOf("limit" to "${limit ?: NEWS_INITIAL_LIMIT}", "refresh" to "1") else mapOf("limit" to "${limit ?: NEWS_INITIAL_LIMIT}"))
                    val (items, errs) = parseRss(o)
                    rssNote = errs.takeIf { it.isNotEmpty() }?.joinToString("\n", prefix = "Flux RSS en erreur :\n")
                    rssRefreshing = o.bool("refreshing") == true
                    items
                }
                NewsType.TELEGRAM -> parseTelegram(api.getObject("api/telegram-channels/latest", window))
                NewsType.EBDZ -> api.getObject("api/ebdz/latest", window).let { o ->
                    hasMoreEbdz = o.bool("has_more") == true
                    parseEbdz(o)
                }
            }
            feeds[type] = if (olderThanDays == null) loaded
            else (feeds[type].orEmpty() + loaded).distinctBy { listOf(it.type, it.title, it.subtitle, it.date) }
        } catch (e: Exception) {
            errors[type] = e.readable()
        }
    }

    suspend fun loadAll(refresh: Boolean) {
        loading = true
        if (refresh) {
            // Bouton Actualiser du site : relance aussi le scrape EBDZ et Telegram.
            kotlinx.coroutines.coroutineScope {
                listOf(
                    async { runCatching { api.postObject("api/ebdz/scrape", org.json.JSONObject()) } },
                    async { runCatching { api.postObject("api/telegram-channels/scrape", org.json.JSONObject()) } },
                ).awaitAll()
            }
            daysWindow = NEWS_DAYS
        }
        fullWindow = false
        kotlinx.coroutines.coroutineScope { NewsType.entries.map { t -> async { load(t, refresh) } }.awaitAll() }
        loading = false
        // Seule une actualisation demandée relit les flux ; le serveur calcule ensuite les séries.
        if (refresh) rssPass++
    }

    suspend fun loadMore() {
        loadingMore = true
        if (!fullWindow) {
            kotlinx.coroutines.coroutineScope { listOf(NewsType.EBDZ, NewsType.TELEGRAM).map { t -> async { load(t, false, limit = null) } }.awaitAll() }
            fullWindow = true
        } else {
            val older = daysWindow
            daysWindow += NEWS_DAYS
            kotlinx.coroutines.coroutineScope { listOf(NewsType.EBDZ, NewsType.TELEGRAM).map { t -> async { load(t, false, limit = null, olderThanDays = older) } }.awaitAll() }
        }
        loadingMore = false
    }
    // L'ouverture affiche ce que le serveur a en base (actualisé toutes les 15 min, séries déjà
    // calculées) sans rien redemander. Après « Actualiser » seulement, le serveur calcule les séries
    // des nouvelles releases : on relit alors le flux après 4, 8 puis 12 s pour les afficher.
    LaunchedEffect(rssPass) {
        if (rssPass == 0) return@LaunchedEffect
        var attempt = 1
        while (rssRefreshing && attempt <= 3) {
            kotlinx.coroutines.delay(4000L * attempt++)
            load(NewsType.RSS, refresh = false)
        }
    }
    LaunchedEffect(api) { if (feeds.isEmpty()) loadAll(refresh = false) }
    // Un album, ou tous les albums d'un groupe (crayon de l'en-tête) : la série choisie s'applique à chacun.
    suspend fun applySeries(entries: List<NewsItem>, seriesId: Int) {
        val threads = mutableSetOf<String>()
        entries.forEach { e ->
            if (e.type == NewsType.EBDZ && !threads.add(e.raw.str("thread_id") ?: "")) return@forEach
            setNewsSeries(api, e, seriesId)
        }
    }
    groupMenu?.let { members -> NewsGroupActions(vm, members, onDismiss = { groupMenu = null }, onPickSeries = { pickFor = members }, onOpenSeries = onOpenSeries) }
    fun attached(entries: List<NewsItem>) = if (entries.size > 1) " à ${entries.size} albums" else ""
    pickFor?.let { entries ->
        NewsSeriesPicker(vm, entries.first(), count = entries.size, onDismiss = { pickFor = null }, onAddNew = {
            addingFor = entries
            pickFor = null
        }, onPick = { series ->
            pickFor = null
            launchAction(scope, context, "Série « ${series.title} » rattachée${attached(entries)}", then = { scope.launch { loadAll(refresh = false) } }) {
                applySeries(entries, series.id)
            }
        })
    }
    addingFor?.let { entries ->
        SeriesAddDialog(vm, onDismiss = { addingFor = null }, initialQuery = entries.first().seriesTitle, onAdded = { sid, title ->
            addingFor = null
            launchAction(scope, context, "Série « $title » créée et rattachée${attached(entries)}", then = { scope.launch { loadAll(refresh = false) } }) {
                applySeries(entries, sid)
            }
        })
    }

    // Genre (BD / Comics / Manga) d'après l'origine de la fiche Bédéthèque, comme la page web :
    // POST /api/bedetheque/origins, par lots de 100, en redemandant tant que le serveur lit des pages.
    val genres = remember { mutableStateMapOf<String, String?>() }
    var genreFilter by rememberSaveable { mutableStateOf<String?>(null) }
    // Pays masqués par défaut ; « Tous » les replie. La recherche filtre titre, sous-titre et série.
    var countriesOpen by rememberSaveable { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val searchFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    // Ouvrir la recherche place le curseur dans le champ, sans second appui.
    LaunchedEffect(searchOpen) { if (searchOpen) searchFocus.requestFocus() }
    val wantedUrls = remember(feeds.toMap()) { feeds.values.flatten().mapNotNull { it.bedethequeUrl }.distinct() }
    LaunchedEffect(wantedUrls) {
        var missing = wantedUrls.filter { it !in genres }
        var rounds = 0
        while (missing.isNotEmpty() && rounds++ < 40) {
            val batch = missing.take(100)
            val o = runCatching {
                api.postObject("api/bedetheque/origins", org.json.JSONObject().put("urls", org.json.JSONArray(batch)))
            }.getOrNull() ?: break
            val answered = o.optJSONObject("origins")
            answered?.keys()?.forEach { url -> genres[url] = answered.optJSONObject(url)?.str("kind") }
            if ((answered == null || answered.length() == 0) && o.optInt("pending") == 0) batch.forEach { genres[it] = null }
            missing = wantedUrls.filter { it !in genres }
            if (missing.isNotEmpty()) kotlinx.coroutines.delay(300)
        }
    }

    val shownTypes = if (tab == 0) NewsType.entries else listOf(NewsType.entries[tab - 1])
    val items = remember(tab, feeds.toMap(), genreFilter, genres.toMap(), query) {
        val q = normalize(query).trim()
        shownTypes.flatMap { feeds[it].orEmpty() }
            .filter { genreFilter == null || it.bedethequeUrl?.let { u -> genres[u] } == genreFilter }
            .filter { q.isEmpty() || normalize(listOfNotNull(it.title, it.subtitle, it.seriesTitle).joinToString(" ")).contains(q) }
            .let { list -> if (tab == 0) list.sortedByDescending { it.date ?: "" } else list }
    }

    val groups = remember(items) { groupConsecutiveSeries(items) }
    val expandedGroups = remember { mutableStateMapOf<String, Boolean>() }

    Column(Modifier.fillMaxSize()) {
        PrimaryScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
            TABS.forEachIndexed { i, t ->
                val count = if (i == 0) NewsType.entries.sumOf { feeds[it]?.size ?: 0 } else feeds[NewsType.entries[i - 1]]?.size
                Tab(selected = tab == i, onClick = { tab = i }, text = {
                    // Logo de la source (Tout garde son mot), suivi du nombre d'entrées.
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (i == 0) Text(t) else Logo(NewsType.entries[i - 1].name.lowercase(), t, 22.dp, tip = false)
                        if (count != null && count > 0) Text("$count")
                    }
                })
            }
        }
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (countriesOpen && !searchOpen) {
                Row(
                    Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(null to "Genre", "bd" to "BD", "comics" to "Comics", "manga" to "Manga").forEach { (k, l) ->
                        // Drapeau du genre (BD = France, Comics = USA, Manga = Japon), nombre d'entrées à côté.
                        val n = if (k == null) null else feeds.values.flatten().count { it.bedethequeUrl?.let { u -> genres[u] } == k }
                        FilterChip(selected = genreFilter == k, onClick = { genreFilter = k; if (k == null) countriesOpen = false }, label = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (k == null) Text(l) else Logo(k, l, 20.dp, tip = false)
                                if (n != null && n > 0) Text("$n")
                            }
                        })
                    }
                }
            } else {
                // Pendant une recherche, un appui sur « Genre » ferme la recherche.
                FilterChip(selected = genreFilter != null, onClick = { if (searchOpen) { searchOpen = false; query = "" } else countriesOpen = true }, label = {
                    if (genreFilter == null) Text("Genre") else Logo(genreFilter, genreFilter.orEmpty(), 20.dp, tip = false)
                })
                if (searchOpen) BasicTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    // Même hauteur que les chips (32 dp) : le champ Material fait 56 dp de haut minimum.
                    decorationBox = { inner ->
                        Box(
                            Modifier.fillMaxWidth().height(32.dp)
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (query.isEmpty()) Text("Titre ou série", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            inner()
                        }
                    },
                    modifier = Modifier.weight(1f).focusRequester(searchFocus),
                )
                else Spacer(Modifier.weight(1f))
            }
            // Actualiser : relit les flux RSS et relance les scrapes EBDZ / Telegram (comme le bouton du site).
            IconButton(onClick = { if (!loading) scope.launch { loadAll(refresh = true) } }) {
                if (loading && feeds.isNotEmpty()) androidx.compose.material3.CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Icon(Icons.Outlined.Refresh, contentDescription = "Actualiser les nouveautés", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { searchOpen = !searchOpen; if (!searchOpen) query = "" }) {
                Icon(
                    Icons.Outlined.Search, contentDescription = "Rechercher dans les nouveautés",
                    tint = if (searchOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (loading && feeds.isEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth())
        PullToRefreshBox(
            isRefreshing = loading && feeds.isNotEmpty(),
            onRefresh = { scope.launch { loadAll(refresh = true) } },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                shownTypes.forEach { t ->
                    errors[t]?.let { msg -> item(key = "err-$t") { ErrorCard("${t.label} : $msg", Modifier.padding(16.dp), onRetry = { scope.launch { load(t, false) } }) } }
                }
                if (NewsType.RSS in shownTypes) rssNote?.let { item(key = "rss-note") { ErrorCard(it, Modifier.padding(16.dp)) } }
                if (!loading && items.isEmpty() && shownTypes.none { errors[it] != null }) {
                    item { EmptyState("Rien de neuf", "Aucune entrée récente.") }
                }
                // Albums consécutifs d'une même série : une seule ligne « série », dépliable (comme le site).
                groups.forEachIndexed { gi, members ->
                    val first = members.first()
                    val genreOf = { it: NewsItem -> it.bedethequeUrl?.let { u -> genres[u] } }
                    if (members.size == 1) item(key = "${first.type}-$gi-${first.title.hashCode()}") {
                        NewsRow(vm, first, showType = tab == 0, genre = genreOf(first), onActions = { groupMenu = listOf(it) })
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    } else {
                        val groupId = "${newsGroupKey(first)}|${first.date}"
                        val open = expandedGroups[groupId] == true
                        item(key = "g-$gi-$groupId") {
                            NewsGroupRow(first, members.size, showType = tab == 0, genre = genreOf(first), expanded = open, onPickSeries = { groupMenu = members }) {
                                if (open) expandedGroups.remove(groupId) else expandedGroups[groupId] = true
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        if (open) members.forEachIndexed { mi, m ->
                            item(key = "g-$gi-$groupId-$mi") {
                                // Date, type et origine laissés vides quand ils répètent ceux de la ligne série.
                                NewsRow(
                                    vm, m, showType = tab == 0 && m.type != first.type,
                                    genre = genreOf(m).takeIf { it != genreOf(first) },
                                    onActions = { groupMenu = listOf(it) },
                                    hideDate = m.date == first.date, nested = true,
                                )
                                HorizontalDivider(Modifier.padding(start = 28.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
                val moreAvailable = !fullWindow || (hasMoreEbdz && shownTypes != listOf(NewsType.RSS))
                if (feeds.isNotEmpty() && !loading && moreAvailable && shownTypes != listOf(NewsType.RSS)) item(key = "load-more") {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        if (loadingMore) androidx.compose.material3.CircularProgressIndicator(Modifier.size(24.dp))
                        else androidx.compose.material3.OutlinedButton(onClick = { scope.launch { loadMore() } }) {
                            Text(if (!fullWindow) "Charger plus" else "Charger une période plus ancienne")
                        }
                    }
                }
            }
        }
    }
}

/** Choix ou changement de la série d'une entrée : toutes les bibliothèques, recherche par titre. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewsSeriesPicker(vm: AppViewModel, item: NewsItem, count: Int = 1, onPick: (SeriesSummary) -> Unit, onAddNew: () -> Unit, onDismiss: () -> Unit) {
    val api = vm.api
    var all by remember { mutableStateOf<List<SeriesSummary>?>(null) }
    var failed by remember { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(api) {
        if (api == null) return@LaunchedEffect
        try {
            val libs = parseLibraries(api.getArray("api/libraries"))
            all = libs.flatMap { lib -> parseSeriesList(api.getArray("api/library/${lib.id}/series")) }.sortedBy { it.title.lowercase() }
        } catch (e: Exception) {
            failed = e.readable()
        }
    }
    val terms = normalize(query).split(' ').filter { it.isNotBlank() }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (count > 1) {
                Text(item.seriesTitle, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("La série choisie s'applique aux $count albums du groupe.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else Text(item.subtitle ?: item.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            item.seriesId?.let {
                Text("Rattachée actuellement à : ${item.seriesTitle}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedTextField(
                value = query, onValueChange = { query = it }, label = { Text("Chercher une série") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            failed?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            val list = all
            if (list == null && failed == null) LinearProgressIndicator(Modifier.fillMaxWidth())
            TextButton(onClick = onAddNew, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Créer une nouvelle série")
            }
            LazyColumn(Modifier.heightIn(max = 480.dp)) {
                items(list.orEmpty().filter { s -> terms.isEmpty() || terms.all { it in s.searchKey } }.take(200), key = { it.id }) { s ->
                    ListItem(
                        headlineContent = { Text(s.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { s.author?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
                        trailingContent = { if (s.id == item.seriesId) Icon(Icons.Outlined.Check, contentDescription = "Série actuelle") },
                        modifier = Modifier.clickable { onPick(s) },
                    )
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun NewsRow(
    vm: AppViewModel, item: NewsItem, showType: Boolean, genre: String?, onActions: (NewsItem) -> Unit,
    hideDate: Boolean = false, nested: Boolean = false,
) {
    val cs = MaterialTheme.colorScheme
    val uri = LocalUriHandler.current
    val dl = vm.downloads
    val sentTo = item.files.firstNotNullOfOrNull { dl.sent[it.job.key] }

    Row(
        // Appui : ouvre la source ; appui long : actions de l'album (même fenêtre qu'un groupe).
        Modifier.fillMaxWidth().combinedClickable(
            onClick = { item.link?.let { runCatching { uri.openUri(it) } } },
            onLongClick = { onActions(item) }, onLongClickLabel = "Actions de l'album",
        )
            .padding(start = if (nested) 28.dp else 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Présentation : 1) série + drapeau d'origine, 2) nom du fichier, 3) une seule ligne
        // « source · heure · taille » suivie des états (possédé, envoyé…). Bédéthèque et les
        // autres liens sont dans le menu ⋮, plus de rangée d'icônes à déchiffrer.
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Dans un groupe déplié, la série est déjà sur la ligne au-dessus : le fichier passe en titre.
                Text(if (nested) item.title else item.subtitle ?: item.title,
                    style = if (nested) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleSmall,
                    fontWeight = if (nested) FontWeight.Medium else FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                GenreFlag(genre)
            }
            if (item.subtitle != null && !nested) Text(item.title, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            androidx.compose.foundation.layout.FlowRow(
                Modifier.padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                SourceLine(item, showType, hideDate)
                if (item.owned) Tag("Possédé", cs.primaryContainer, cs.onPrimaryContainer)
                else if (item.inLibrary) Tag("Série suivie", cs.secondaryContainer, cs.onSecondaryContainer)
                if (sentTo != null) Tag("Envoyé · $sentTo", cs.secondaryContainer, cs.onSecondaryContainer)
            }
        }
        if (item.files.isNotEmpty()) {
            IconButton(onClick = {
                dl.choose(item.subtitle ?: item.title, item.files.map { f ->
                    f.job to listOfNotNull(f.volumeLabel, if (f.owned) "Possédé : sera remplacé" else null).joinToString(" · ").ifBlank { null }
                })
            }) { Icon(Icons.Outlined.Download, contentDescription = "Télécharger") }
        }
    }
}

/** Fenêtre des nouveautés EBDZ et Telegram, en jours, et pas de « période plus ancienne » : comme le site (nouveautesDaysWindow, NOUVEAUTES_WINDOW_STEP). */
private const val NEWS_DAYS = 60

/** Premier lot affiché par source, comme le site (NOUVEAUTES_INITIAL_LIMIT). */
private const val NEWS_INITIAL_LIMIT = 100


/**
 * Clé de série d'une entrée (comme _nouveautesSeriesGroupKey du site) : série locale
 * (matched_series_id pour EBDZ, series_id sinon), sinon fiche Bédéthèque, sinon titre de
 * série normalisé ; null si rien ne permet de regrouper.
 */
internal fun newsGroupKey(item: NewsItem): String? {
    item.seriesId?.let { return "s:$it" }
    item.bedethequeUrl?.takeIf { it.isNotBlank() }?.let { return "b:$it" }
    val r = item.raw
    fun str(k: String) = r.optString(k, "").takeIf { it.isNotBlank() && it != "null" }
    // VK sans série reconnue : les fichiers d'un même sujet vont ensemble.
    str("vk_topic")?.let { return "vk:" + normalize(it).trim() }
    // Release RSS sans série : nom deviné avant le numéro de tome (« … T05 », « ….T06 »).
    val title = if (item.type == NewsType.EBDZ) str("matched_series_title") ?: str("bedetheque_title") ?: newsGuessSeriesTitle(str("title")).ifEmpty { null } ?: str("title")
    else str("series_title") ?: str("bedetheque_title") ?: str("parsed_title") ?: newsGuessSeriesTitle(str("title") ?: str("filename")).ifEmpty { null }
    return title?.let { normalize(it).trim() }?.takeIf { it.isNotEmpty() }?.let { "t:$it" }
}

/**
 * Clés d'une entrée pour le regroupement : sa clé de série, plus ses noms compactés (sans
 * espaces, ponctuation ni parenthèses) pour qu'un album rattaché à sa fiche (« Dragon Ball
 * (Perfect Edition) ») rejoigne les releases voisines au nom seulement deviné (« Dragonball
 * Perfect Edition T05 »).
 */
internal fun newsGroupKeys(item: NewsItem): Set<String> {
    val r = item.raw
    fun str(k: String) = r.optString(k, "").takeIf { it.isNotBlank() && it != "null" }
    val names = listOfNotNull(
        item.seriesTitle,
        newsGuessSeriesTitle(str("title") ?: str("filename") ?: item.title).ifEmpty { null },
    )
    return buildSet {
        newsGroupKey(item)?.let { add(it) }
        names.map { compactSeriesName(it) }.filter { it.length >= 4 }.forEach { add("n:$it") }
    }
}

internal fun compactSeriesName(name: String): String = normalize(name).filter { it.isLetterOrDigit() }

/** Regroupe les entrées CONSÉCUTIVES d'une même série ; un album d'une autre série coupe le groupe. */
internal fun groupConsecutiveSeries(items: List<NewsItem>): List<List<NewsItem>> {
    val out = mutableListOf<MutableList<NewsItem>>()
    var groupKeys: MutableSet<String> = mutableSetOf()
    for (it in items) {
        val keys = newsGroupKeys(it)
        if (out.isNotEmpty() && keys.any { k -> k in groupKeys }) {
            out.last().add(it); groupKeys.addAll(keys)
        } else {
            out.add(mutableListOf(it)); groupKeys = keys.toMutableSet()
        }
    }
    return out
}

/** Ligne « série » d'un groupe replié : nom, nombre d'albums, date / type / origine du plus récent. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun NewsGroupRow(first: NewsItem, count: Int, showType: Boolean, genre: String?, expanded: Boolean, onPickSeries: () -> Unit, onToggle: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        // Appui : déplier ; appui long : actions du groupe (tout importer, série, fiche…).
        Modifier.fillMaxWidth().combinedClickable(onClick = onToggle, onLongClick = onPickSeries, onLongClickLabel = "Actions des $count albums")
            .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(first.seriesTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                GenreFlag(genre)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tag("$count albums", cs.secondaryContainer, cs.onSecondaryContainer)
                SourceLine(first, showType, hideDate = false, withSize = false)
            }
        }
        Icon(
            if (expanded) androidx.compose.material.icons.Icons.Outlined.ExpandLess else androidx.compose.material.icons.Icons.Outlined.ExpandMore,
            contentDescription = if (expanded) "Replier" else "Déplier les albums", tint = cs.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}


/** Drapeau d'origine (BD = France, Comics = USA, Manga = Japon), discret, à côté du titre. */
@Composable
private fun GenreFlag(genre: String?) {
    when (genre) {
        "bd" -> Logo("bd", "BD", 14.dp)
        "comics" -> Logo("comics", "Comics", 14.dp)
        "manga" -> Logo("manga", "Manga", 14.dp)
    }
}

/** « [logo] C411 · 14:21 · 37 Mo » : un seul logo (celui de la source, sinon le type RSS / Telegram / EBDZ). */
@Composable
private fun SourceLine(item: NewsItem, showType: Boolean, hideDate: Boolean, withSize: Boolean = true) {
    val cs = MaterialTheme.colorScheme
    val source = item.source?.replace("Prowlarr · ", "")?.replace("Prowlarr — ", "")
    val srcKey = logoKey(item.source)?.takeIf { hasLogo(it) }
    val text = listOfNotNull(source ?: item.type.label, item.date?.takeUnless { hideDate }?.let { shortNewsDate(it) },
        if (withSize) formatBytes(item.sizeBytes) else null).joinToString(" · ")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        when {
            srcKey != null -> Logo(srcKey, source ?: "", 14.dp)
            showType || source == null -> Logo(item.type.name.lowercase(), item.type.label, 14.dp)
        }
        Text(text, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** « 2026-11-09 14:21 » → « 14:21 » aujourd'hui, « hier 14:21 », sinon « 7 nov. 14:21 ». */
internal fun shortNewsDate(d: String): String {
    val date = runCatching { java.time.LocalDate.parse(d.take(10)) }.getOrNull() ?: return d
    val time = d.drop(11).take(5)
    val today = java.time.LocalDate.now()
    val day = when (date) {
        today -> null
        today.minusDays(1) -> "hier"
        else -> date.format(java.time.format.DateTimeFormatter.ofPattern(if (date.year == today.year) "d MMM" else "d MMM yyyy", java.util.Locale.FRENCH))
    }
    return listOfNotNull(day, time.ifBlank { null }).joinToString(" ")
}

/** Actions d'un groupe d'albums (appui long sur son en-tête), comme l'en-tête de groupe du site. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewsGroupActions(vm: AppViewModel, members: List<NewsItem>, onDismiss: () -> Unit, onPickSeries: () -> Unit, onOpenSeries: (Int, String) -> Unit) {
    val uri = LocalUriHandler.current
    val dl = vm.downloads
    val first = members.first()
    val seriesId = members.firstNotNullOfOrNull { it.seriesId }
    val bd = members.firstNotNullOfOrNull { it.bedethequeUrl?.takeIf { u -> u.isNotBlank() } }
    val withFiles = members.filter { it.files.isNotEmpty() }
    val single = members.singleOrNull()
    val clipboard = LocalClipboardManager.current
    val copyable = single?.files?.firstOrNull()?.job?.let { j ->
        when (j) { is DownloadJob.Ed2k -> j.link; is DownloadJob.Torrent -> j.url; is DownloadJob.Vk -> j.url; else -> null }
    }
    @Composable
    fun Entry(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, detail: String? = null, enabled: Boolean = true, action: () -> Unit) {
        val cs = MaterialTheme.colorScheme
        // Grisée : l'action reste visible, la raison est écrite en dessous.
        ListItem(
            headlineContent = { Text(label, color = if (enabled) androidx.compose.ui.graphics.Color.Unspecified else cs.onSurface.copy(alpha = 0.38f)) },
            supportingContent = detail?.let { { Text(it, style = MaterialTheme.typography.bodySmall, color = if (enabled) cs.onSurfaceVariant else cs.onSurfaceVariant.copy(alpha = 0.7f)) } },
            leadingContent = { Icon(icon, contentDescription = null, tint = if (enabled) cs.onSurfaceVariant else cs.onSurfaceVariant.copy(alpha = 0.38f)) },
            modifier = Modifier.clickable(enabled = enabled) { onDismiss(); action() },
        )
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text(first.seriesTitle, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
            Text(single?.title ?: "${members.size} albums", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 8.dp))
            // Éclair de l'en-tête du site : ajoute la série si besoin et télécharge ses tomes manquants.
            val missing = withFiles.filter { !it.owned }
            val why = when {
                missing.isNotEmpty() -> null
                withFiles.isEmpty() -> "Aucun fichier téléchargeable dans ces albums : seulement un lien vers la source"
                else -> "Tous les albums du groupe sont déjà possédés (« Choisir les fichiers » permet de les remplacer)"
            }
            if (single != null) Entry(Icons.Outlined.Bolt, if (seriesId == null) "Ajouter la série et télécharger" else "Télécharger dans la série",
                why?.replace("Tous les albums du groupe sont déjà possédés", "Album déjà possédé")?.replace("ces albums", "cet album"),
                enabled = why == null) { dl.quick = single }
            else Entry(Icons.Outlined.Bolt, if (seriesId == null) "Ajouter la série et tout importer" else "Tout importer",
                why ?: "${missing.size} album(s) non possédé(s) envoyé(s) au téléchargement", enabled = why == null) { dl.quickAll = missing }
            if (withFiles.isNotEmpty()) Entry(Icons.Outlined.Download, if (single != null) "Télécharger" else "Choisir les fichiers à télécharger") {
                dl.choose(single?.let { it.subtitle ?: it.title } ?: first.seriesTitle, withFiles.flatMap { m ->
                    m.files.map { f -> f.job to listOfNotNull(f.volumeLabel ?: m.title, if (f.owned) "Possédé : sera remplacé" else null).joinToString(" · ").ifBlank { null } }
                })
            }
            Entry(Icons.Outlined.Edit, if (seriesId == null) "Choisir la série" else "Changer la série", if (members.size > 1) "S'applique aux ${members.size} albums" else null, action = onPickSeries)
            seriesId?.let { id -> Entry(Icons.Outlined.CollectionsBookmark, "Ouvrir la série") { onOpenSeries(id, first.seriesTitle) } }
            bd?.let { url -> Entry(Icons.AutoMirrored.Outlined.OpenInNew, "Fiche Bédéthèque") { runCatching { uri.openUri(url) } } }
            single?.link?.let { link -> Entry(Icons.AutoMirrored.Outlined.OpenInNew, "Ouvrir la source") { runCatching { uri.openUri(link) } } }
            copyable?.let { link -> Entry(Icons.Outlined.ContentCopy, "Copier le lien") { clipboard.setText(AnnotatedString(link)) } }
            Entry(Icons.Outlined.Search, "Chercher ailleurs") {
                vm.search.context = null
                vm.search.title = first.seriesTitle
                vm.search.volume = ""
                vm.search.search()
                vm.goToSearch()
            }
        }
    }
}
