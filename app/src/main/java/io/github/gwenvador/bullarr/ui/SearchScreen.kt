package io.github.gwenvador.bullarr.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(vm: AppViewModel) {
    vm.api ?: return
    val s = vm.search
    val dl = vm.downloads
    val focus = LocalFocusManager.current
    val uri = LocalUriHandler.current

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Recherche", style = MaterialTheme.typography.headlineSmall) }
        s.context?.let { ctx ->
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Row(Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Rattaché à ${ctx.seriesTitle}" + (ctx.volumeLabel?.let { " · $it" } ?: ""),
                            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { s.clearContext() }) { Icon(Icons.Outlined.Close, contentDescription = "Détacher de la série") }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                ClearableField(
                    value = s.title, onValueChange = { s.title = it },
                    label = "Série ou titre",
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); s.search() }),
                    modifier = Modifier.weight(1f),
                )
                ClearableField(
                    value = s.volume, onValueChange = { v -> s.volume = v.filter { it.isDigit() }.take(4) },
                    label = "Tome",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); s.search() }),
                    modifier = Modifier.width(112.dp),
                )
            }
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SEARCH_SOURCES.forEach { (key, label) ->
                    val count = (s.sources[key] as? SourceState.Done)?.results?.size
                    FilterChip(
                        selected = s.enabledSources[key] == true,
                        onClick = { s.enabledSources[key] = s.enabledSources[key] != true },
                        label = { Text(if (count != null) "$label ($count)" else label) },
                    )
                }
            }
        }
        item {
            Button(onClick = { focus.clearFocus(); s.search() }, enabled = s.title.isNotBlank()) {
                Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                Text("Rechercher")
            }
        }
        if (s.lastQuery == null) {
            item { EmptyState("Cherchez un album", "Sur Prowlarr, EBDZ, Telegram, Fourtoutici, VK et Anna's Archive. Depuis une fiche série, touchez un tome pour le chercher.") }
        }
        SEARCH_SOURCES.forEach { (key, label) ->
            val state = s.sources[key] ?: return@forEach
            val closed = s.collapsed[key] == true
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
                    items(state.results.withIndex().toList(), key = { "r-$key-${it.index}" }) { (_, r) ->
                        val job = r.toJob(s.context)
                        ResultRow(
                            r,
                            sentTo = job?.let { dl.sent[it.key] },
                            onDownload = job?.let { j ->
                                {
                                    val ctx = s.context
                                    val vol = if (r.volumeNumber != null) r.volumeLabel else ctx?.volumeLabel
                                    dl.request(j, ctx?.let { "Rattaché à ${it.seriesTitle}" + (vol?.let { v -> " · $v" } ?: "") }, r.unconfirmed)
                                }
                            },
                            onOpen = r.sourceLink?.let { link -> { runCatching { uri.openUri(link) } } },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
                SourceState.Loading -> {}
            }
        }
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
            if (sentTo != null) Tag("Envoyé à $sentTo", cs.secondaryContainer, cs.onSecondaryContainer)
        }
        if (onOpen != null) IconButton(onClick = onOpen) { Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = "Voir la source") }
        if (onDownload != null) IconButton(onClick = onDownload, enabled = sentTo == null) { Icon(Icons.Outlined.Download, contentDescription = "Télécharger") }
    }
}
