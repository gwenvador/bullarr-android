package io.github.gwenvador.bullarr.ui

import androidx.compose.foundation.clickable
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
    var pickFor by remember { mutableStateOf<NewsItem?>(null) }
    // Entrée dont on crée la série (dialogue d'ajout), puis rattachée à la nouvelle série.
    var addingFor by remember { mutableStateOf<NewsItem?>(null) }

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
    pickFor?.let { entry ->
        NewsSeriesPicker(vm, entry, onDismiss = { pickFor = null }, onAddNew = {
            addingFor = entry
            pickFor = null
        }, onPick = { series ->
            pickFor = null
            launchAction(scope, context, "Série « ${series.title} » rattachée", then = { scope.launch { loadAll(refresh = false) } }) {
                setNewsSeries(api, entry, series.id)
            }
        })
    }
    addingFor?.let { entry ->
        SeriesAddDialog(vm, onDismiss = { addingFor = null }, initialQuery = entry.seriesTitle, onAdded = { sid, title ->
            addingFor = null
            launchAction(scope, context, "Série « $title » créée et rattachée", then = { scope.launch { loadAll(refresh = false) } }) {
                setNewsSeries(api, entry, sid)
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
                itemsIndexed(items, key = { i, it -> "${it.type}-$i-${it.title.hashCode()}" }) { _, item ->
                    NewsRow(vm, item, showType = tab == 0, genre = item.bedethequeUrl?.let { genres[it] }, onOpenSeries = onOpenSeries, onPickSeries = { pickFor = it })
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
private fun NewsSeriesPicker(vm: AppViewModel, item: NewsItem, onPick: (SeriesSummary) -> Unit, onAddNew: () -> Unit, onDismiss: () -> Unit) {
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
            Text(item.subtitle ?: item.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
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

@Composable
private fun NewsRow(vm: AppViewModel, item: NewsItem, showType: Boolean, genre: String?, onOpenSeries: (Int, String) -> Unit, onPickSeries: (NewsItem) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val uri = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    val dl = vm.downloads
    var menu by remember { mutableStateOf(false) }
    val sentTo = item.files.firstNotNullOfOrNull { dl.sent[it.job.key] }
    val copyable = item.files.firstOrNull()?.job?.let { j ->
        when (j) { is DownloadJob.Ed2k -> j.link; is DownloadJob.Torrent -> j.url; is DownloadJob.Vk -> j.url; else -> null }
    }

    Row(
        Modifier.fillMaxWidth().clickable(enabled = item.link != null) { item.link?.let { runCatching { uri.openUri(it) } } }
            .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            item.subtitle?.let { Text(it, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            Text(item.title, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis,
                color = if (item.subtitle != null) cs.onSurfaceVariant else cs.onSurface)
            // FlowRow : les pastilles passent à la ligne au lieu de s'écraser.
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                // Icônes plutôt que texte ; le libellé apparaît au long appui.
                if (showType) Logo(item.type.name.lowercase(), item.type.label, 20.dp)
                when (genre) {
                    "bd" -> Logo("bd", "BD", 20.dp)
                    "comics" -> Logo("comics", "Comics", 20.dp)
                    "manga" -> Logo("manga", "Manga", 20.dp)
                }
                if (item.owned) StatusIcon(Icons.Outlined.CheckCircle, "Possédé", cs.primary, 20.dp)
                else if (item.inLibrary) StatusIcon(Icons.Outlined.CollectionsBookmark, "Série en bibliothèque", cs.secondary, 20.dp)
                if (item.downloaded) StatusIcon(Icons.Outlined.DownloadDone, "Téléchargé", cs.tertiary, 20.dp)
                if (sentTo != null) {
                    val k = logoKey(sentTo)
                    if (hasLogo(k)) Logo(k, "Envoyé à $sentTo", 20.dp)
                    else StatusIcon(Icons.AutoMirrored.Outlined.Send, "Envoyé à $sentTo", cs.secondary, 20.dp)
                }
                item.bedethequeUrl?.let { bd ->
                    Logo("bedetheque", "Ouvrir sur Bédéthèque", 22.dp, Modifier.clickable { runCatching { uri.openUri(bd) } })
                }
            }
            val meta = listOfNotNull(item.source?.replace("Prowlarr · ", "")?.replace("Prowlarr — ", ""), item.date, formatBytes(item.sizeBytes)).joinToString(" · ")
            if (meta.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                logoKey(item.source)?.takeIf { it != item.type.name.lowercase() || !showType }?.let { Logo(it, item.source ?: "", 14.dp) }
                Text(meta, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            }
        }
        if (item.files.isNotEmpty()) {
            IconButton(onClick = {
                dl.choose(item.subtitle ?: item.title, item.files.map { f ->
                    f.job to listOfNotNull(f.volumeLabel, if (f.owned) "Possédé : sera remplacé" else null).joinToString(" · ").ifBlank { null }
                })
            }) { Icon(Icons.Outlined.Download, contentDescription = "Télécharger") }
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "Actions") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (item.files.isNotEmpty() && !item.owned) {
                    DropdownMenuItem(
                        text = { Text(if (item.seriesId == null) "Ajouter la série et télécharger" else "Télécharger dans la série") },
                        leadingIcon = { Icon(Icons.Outlined.Bolt, contentDescription = null) },
                        onClick = { menu = false; dl.quick = item },
                    )
                }
                DropdownMenuItem(
                    text = { Text(if (item.seriesId == null) "Choisir la série" else "Changer la série") },
                    leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                    onClick = { menu = false; onPickSeries(item) },
                )
                item.seriesId?.let { id ->
                    DropdownMenuItem(
                        text = { Text("Ouvrir la série") },
                        leadingIcon = { Icon(Icons.Outlined.CollectionsBookmark, contentDescription = null) },
                        onClick = { menu = false; onOpenSeries(id, item.seriesTitle) },
                    )
                }
                item.bedethequeUrl?.let { bd ->
                    DropdownMenuItem(
                        text = { Text("Ouvrir sur Bédéthèque") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null) },
                        onClick = { menu = false; runCatching { uri.openUri(bd) } },
                    )
                }
                item.link?.let { link ->
                    DropdownMenuItem(
                        text = { Text("Ouvrir la source") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null) },
                        onClick = { menu = false; runCatching { uri.openUri(link) } },
                    )
                }
                copyable?.let { link ->
                    DropdownMenuItem(
                        text = { Text("Copier le lien") },
                        leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                        onClick = { menu = false; clipboard.setText(AnnotatedString(link)) },
                    )
                }
                DropdownMenuItem(
                    text = { Text("Chercher ailleurs") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    onClick = {
                        menu = false
                        vm.search.context = null
                        vm.search.title = item.seriesTitle
                        vm.search.volume = ""
                        vm.search.search()
                        vm.goToSearch()
                    },
                )
            }
        }
    }
}

/** Fenêtre des nouveautés EBDZ et Telegram, en jours, et pas de « période plus ancienne » : comme le site (nouveautesDaysWindow, NOUVEAUTES_WINDOW_STEP). */
private const val NEWS_DAYS = 60

/** Premier lot affiché par source, comme le site (NOUVEAUTES_INITIAL_LIMIT). */
private const val NEWS_INITIAL_LIMIT = 100
