package io.github.gwenvador.bullarr.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
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
    val filtered = remember(vm.series, q) {
        val terms = normalize(q).split(' ').filter { it.isNotBlank() }
        if (terms.isEmpty()) vm.series else vm.series.filter { s -> terms.all { it in s.searchKey } }
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Bibliothèque", style = MaterialTheme.typography.headlineSmall)
            CompactSearchField(
                value = q, onValueChange = { vm.query = it },
                placeholder = "Rechercher une série, un auteur…",
                modifier = Modifier.fillMaxWidth(),
            )
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
                if (filtered.isEmpty() && vm.series.isNotEmpty()) item { EmptyState("Aucun résultat", "pour « $q »") }
                items(filtered, key = { it.id }) { s ->
                    SeriesRow(s, vm.coverUrl(s.coverPath)) { onOpen(s) }
                    HorizontalDivider(Modifier.padding(start = 84.dp), color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun SeriesRow(s: SeriesSummary, cover: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(cover, 52.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(s.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val meta = listOfNotNull(s.author, s.year).joinToString(" · ")
            if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val cs = MaterialTheme.colorScheme
                val count = if (s.isOneshot) "One-shot" else s.totalKnown?.let { "${s.ownedTomes}/$it tomes" } ?: "${s.ownedTomes} tome(s)"
                Tag(count, cs.surfaceVariant, cs.onSurfaceVariant)
                if (s.missingCount > 0) Tag("${s.missingCount} manquant(s)", cs.tertiaryContainer, cs.onTertiaryContainer)
            }
        }
    }
}
