package io.github.gwenvador.bullarr.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
    val scope = rememberCoroutineScope()

    suspend fun load(type: NewsType, refresh: Boolean) {
        errors.remove(type)
        try {
            feeds[type] = when (type) {
                NewsType.RSS -> {
                    val (items, errs) = parseRss(api.getObject("api/ebdz/rss/latest", if (refresh) mapOf("refresh" to "1") else emptyMap()))
                    rssNote = errs.takeIf { it.isNotEmpty() }?.joinToString("\n", prefix = "Flux RSS en erreur :\n")
                    items
                }
                NewsType.TELEGRAM -> parseTelegram(api.getObject("api/telegram-channels/latest", mapOf("days" to "15")))
                NewsType.EBDZ -> parseEbdz(api.getObject("api/ebdz/latest"))
            }
        } catch (e: Exception) {
            errors[type] = e.readable()
        }
    }

    suspend fun loadAll(refresh: Boolean) {
        loading = true
        kotlinx.coroutines.coroutineScope { NewsType.entries.map { t -> async { load(t, refresh) } }.awaitAll() }
        loading = false
    }
    LaunchedEffect(api) { if (feeds.isEmpty()) loadAll(refresh = false) }

    val shownTypes = if (tab == 0) NewsType.entries else listOf(NewsType.entries[tab - 1])
    val items = remember(tab, feeds.toMap()) {
        shownTypes.flatMap { feeds[it].orEmpty() }.let { list ->
            if (tab == 0) list.sortedByDescending { it.date ?: "" } else list
        }
    }

    Column(Modifier.fillMaxSize()) {
        PrimaryScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
            TABS.forEachIndexed { i, t ->
                val count = if (i == 0) NewsType.entries.sumOf { feeds[it]?.size ?: 0 } else feeds[NewsType.entries[i - 1]]?.size
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(if (count != null && count > 0) "$t ($count)" else t) })
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
                    NewsRow(vm, item, showType = tab == 0, onOpenSeries = onOpenSeries)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun NewsRow(vm: AppViewModel, item: NewsItem, showType: Boolean, onOpenSeries: (Int, String) -> Unit) {
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
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (showType) Tag(item.type.label, cs.surfaceVariant, cs.onSurfaceVariant)
                if (item.owned) Tag("Possédé", cs.secondaryContainer, cs.onSecondaryContainer)
                else if (item.inLibrary) Tag("Série en bibliothèque", cs.primaryContainer, cs.onPrimaryContainer)
                if (item.downloaded) Tag("Téléchargé", cs.tertiaryContainer, cs.onTertiaryContainer)
                if (sentTo != null) Tag("Envoyé à $sentTo", cs.secondaryContainer, cs.onSecondaryContainer)
                item.bedethequeUrl?.let { bd ->
                    androidx.compose.foundation.layout.Box(Modifier.clickable { runCatching { uri.openUri(bd) } }) {
                        Tag("Bédéthèque ↗", cs.tertiaryContainer, cs.onTertiaryContainer)
                    }
                }
            }
            val meta = listOfNotNull(item.source, item.date, formatBytes(item.sizeBytes)).joinToString(" · ")
            if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
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
