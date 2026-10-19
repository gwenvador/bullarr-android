package io.github.gwenvador.bullarr.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.gwenvador.bullarr.data.ImportPendingCount
import io.github.gwenvador.bullarr.data.ImportProgress
import io.github.gwenvador.bullarr.data.formatDuration
import io.github.gwenvador.bullarr.data.parseImportProgress
import io.github.gwenvador.bullarr.data.parsePendingCount
import io.github.gwenvador.bullarr.data.readable

@Composable
fun ImportScreen(vm: AppViewModel) {
    val api = vm.api ?: return
    var progress by remember { mutableStateOf<ImportProgress?>(null) }
    var counts by remember { mutableStateOf<ImportPendingCount?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    // /import sonde aussi toutes les 5 s : lecture seule en base, sans scan disque.
    Polling(5000, api) {
        try {
            progress = parseImportProgress(api.getObject("api/import/progress"))
            counts = runCatching { parsePendingCount(api.getObject("api/import/pending-count")) }.getOrNull()
            error = null
        } catch (e: Exception) {
            error = e.readable()
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Import", style = MaterialTheme.typography.headlineSmall) }
        error?.let { item { ErrorCard(it) } }
        val p = progress
        if (p == null && error == null) item { EmptyState("Chargement…") }
        if (p != null) {
            item {
                val cs = MaterialTheme.colorScheme
                Card(colors = CardDefaults.cardColors(containerColor = if (p.processingFile != null) cs.primaryContainer else cs.surfaceContainer)) {
                    Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        when {
                            p.processingFile != null -> {
                                Text("Import en cours", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(p.processingFile, style = MaterialTheme.typography.bodyMedium)
                                LinearProgressIndicator(Modifier.fillMaxWidth())
                                val meta = listOfNotNull(
                                    formatDuration(p.elapsedSeconds)?.let { "depuis $it" },
                                    p.startedAt?.let { "démarré le ${it.take(16)}" },
                                ).joinToString(" · ")
                                if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodySmall)
                            }
                            p.claimedFiles.isNotEmpty() -> Text("Import en préparation", style = MaterialTheme.typography.titleMedium)
                            else -> Text("Aucun import en cours", style = MaterialTheme.typography.titleMedium)
                        }
                        if (p.paused) Tag("Import arrêté (mis en pause)", cs.tertiaryContainer, cs.onTertiaryContainer)
                    }
                }
            }
            counts?.let { c ->
                item {
                    Text(
                        "${c.waitingFiles} fichier(s) en attente d'import · ${c.awaitingDiscovery} téléchargement(s) pas encore repéré(s) sur le disque",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (p.claimedFiles.isNotEmpty()) {
                item { Text("Fichiers réservés par l'import (${p.claimedFiles.size})", style = MaterialTheme.typography.titleMedium) }
                items(p.claimedFiles) { path ->
                    Column {
                        Text(path.substringAfterLast('/'), style = MaterialTheme.typography.bodyMedium)
                        Text(path.substringBeforeLast('/', ""), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
