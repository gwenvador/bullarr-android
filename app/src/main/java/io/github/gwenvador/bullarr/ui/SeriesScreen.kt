package io.github.gwenvador.bullarr.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
            if (d != null) item {
                OutlinedButton(
                    onClick = { onSearch(SearchContext(seriesId = d.id, seriesTitle = d.title, volumeId = null, volumeLabel = null, volumeNumber = null)) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                    Text("Chercher cette série")
                }
            }
            if (d != null) {
                val owned = d.volumes.count { it.owned }
                item {
                    Text(
                        "Tomes · $owned possédé(s) sur ${d.volumes.size}" + (d.totalKnown?.let { " (Bédéthèque : $it)" } ?: ""),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                    )
                }
                if (d.volumes.isEmpty()) item { EmptyState("Aucun tome connu pour cette série") }
                item {
                    Text(
                        "Touchez un tome pour voir sa fiche ; la loupe le cherche pour le télécharger.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                items(d.volumes, key = { it.id }) { v ->
                    VolumeItem(
                        v, vm.coverUrl(v.coverPath), onOpen = { shown = v },
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
    shown?.let { v ->
        VolumeSheet(vm, v, onDismiss = { shown = null }, onRead = {
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
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val cs = MaterialTheme.colorScheme
                    d?.status?.let { Tag(it, cs.secondaryContainer, cs.onSecondaryContainer) }
                    if (d?.monitored == true) Tag("Surveillée", cs.primaryContainer, cs.onPrimaryContainer)
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
private fun VolumeItem(v: VolumeRow, cover: String?, onOpen: () -> Unit, onRead: (() -> Unit)?, onSearch: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Cover(cover, 44.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(v.label, style = MaterialTheme.typography.labelLarge, color = cs.primary)
            v.title?.let { Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            val meta = if (v.owned) listOfNotNull(v.format?.uppercase(), formatBytes(v.fileSize), v.pageCount?.let { "$it p." }, v.year)
            else listOfNotNull(v.year)
            if (meta.isNotEmpty()) Text(meta.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        if (v.owned) Tag("Possédé", cs.secondaryContainer, cs.onSecondaryContainer)
        else Tag("Manquant", cs.tertiaryContainer, cs.onTertiaryContainer)
        if (onRead != null) IconButton(onClick = onRead) { Icon(Icons.Outlined.PlayArrow, contentDescription = "Lire ce tome", tint = cs.primary) }
        IconButton(onClick = onSearch) { Icon(Icons.Outlined.Search, contentDescription = "Chercher ce tome") }
    }
}

/** Fiche d'un tome : couverture, résumé et métadonnées ComicInfo, fichier ; la recherche est un bouton à part. */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun VolumeSheet(vm: AppViewModel, v: VolumeRow, onDismiss: () -> Unit, onRead: () -> Unit, onSearch: () -> Unit) {
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
                        if (v.owned) Tag("Possédé", cs.secondaryContainer, cs.onSecondaryContainer)
                        else Tag("Manquant", cs.tertiaryContainer, cs.onTertiaryContainer)
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
