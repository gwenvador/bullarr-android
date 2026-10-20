package io.github.gwenvador.bullarr.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.OfflinePin
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.gwenvador.bullarr.data.OfflineStore
import io.github.gwenvador.bullarr.data.formatBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Albums copiés sur l'appareil : lisibles sans réseau. Cet écran n'appelle jamais le serveur. */
@Composable
fun OfflineScreen(vm: AppViewModel) {
    val mgr = vm.offline
    val items = remember(mgr.version, mgr.active.size) { mgr.store.list() }
    var toDelete by remember { mutableStateOf<OfflineStore.Item?>(null) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            val total = items.sumOf { it.sizeBytes }
            Text(
                if (items.isEmpty()) "Aucun album sur l'appareil."
                else "${items.size} album(s) · ${formatBytes(total) ?: "0 o"} sur l'appareil",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
        if (mgr.active.isNotEmpty()) {
            item { Text("En cours", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
            items(mgr.active.keys.sorted(), key = { "a$it" }) { id ->
                val p = mgr.active[id] ?: return@items
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(mgr.titles[id] ?: "Album $id", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (p.error != null) IconButton(onClick = { mgr.start(id, mgr.titles[id] ?: "Album $id") }) { Icon(Icons.Outlined.Refresh, contentDescription = "Reprendre") }
                        IconButton(onClick = { mgr.cancel(id) }) { Icon(Icons.Outlined.Close, contentDescription = "Arrêter") }
                    }
                    if (p.error != null) ErrorCard(p.error)
                    else {
                        LinearProgressIndicator(
                            progress = { if (p.total > 0) p.done.toFloat() / p.total else 0f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(if (p.total > 0) "${p.done} / ${p.total} pages" else "Préparation…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        if (items.isNotEmpty()) item { Text("Téléchargés", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)) }
        items(items, key = { it.meta.volumeId }) { it ->
            val title = listOfNotNull(it.meta.seriesTitle, it.meta.label?.takeIf { l -> l.isNotBlank() }).joinToString(" · ").ifBlank { it.meta.title ?: "Album ${it.meta.volumeId}" }
            Row(
                Modifier.fillMaxWidth().clickable { vm.reading = ReaderTarget(it.meta.volumeId, title) }.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Thumb(mgr.store.pageFile(it.meta.volumeId, 1))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    it.meta.title?.takeIf { t -> t != it.meta.seriesTitle }?.let { t ->
                        Text(t, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text("${it.meta.pageCount} pages · ${formatBytes(it.sizeBytes) ?: ""}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { vm.reading = ReaderTarget(it.meta.volumeId, title) }) { Icon(Icons.Outlined.PlayArrow, contentDescription = "Lire", tint = MaterialTheme.colorScheme.primary) }
                IconButton(onClick = { toDelete = it }) { Icon(Icons.Outlined.Delete, contentDescription = "Supprimer la copie") }
            }
            HorizontalDivider(Modifier.padding(start = 76.dp), color = MaterialTheme.colorScheme.outlineVariant)
        }
        if (items.isEmpty() && mgr.active.isEmpty()) {
            item { EmptyState("Rien hors ligne", "Dans la fiche d'un tome, touchez « Télécharger hors ligne » pour le lire sans réseau.") }
        }
    }

    toDelete?.let { it ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Supprimer la copie ?") },
            text = { Text("${it.meta.seriesTitle ?: "Album"} ${it.meta.label ?: ""}\n${formatBytes(it.sizeBytes) ?: ""} seront libérés. L'album reste sur Bullarr.") },
            confirmButton = { TextButton(onClick = { mgr.delete(it.meta.volumeId); toDelete = null }) { Text("Supprimer", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Annuler") } },
        )
    }
}

/** Miniature : première page de la copie locale. */
@Composable
private fun Thumb(file: File) {
    val bmp by produceState<Bitmap?>(null, file) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, o)
                var s = 1
                while (o.outWidth / (s * 2) >= 200) s *= 2
                BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = s })
            }.getOrNull()
        }
    }
    Box(
        Modifier.size(44.dp, 61.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val b = bmp
        if (b != null) Image(b.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Icon(Icons.Outlined.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
    }
}

/** Bouton d'état hors ligne d'un tome, partagé par la fiche du tome et le one-shot. */
@Composable
fun OfflineButton(vm: AppViewModel, volumeId: Int, title: String, modifier: Modifier = Modifier) {
    val mgr = vm.offline
    val progress = mgr.active[volumeId]
    val done = mgr.isDownloaded(volumeId)
    var confirm by remember { mutableStateOf(false) }
    when {
        progress != null -> Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            androidx.compose.material3.OutlinedButton(
                onClick = { if (progress.error != null) mgr.start(volumeId, title) else mgr.cancel(volumeId) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (progress.error != null) Text("Réessayer le téléchargement")
                else Text(if (progress.total > 0) "Téléchargement ${progress.done}/${progress.total} · Arrêter" else "Préparation… · Arrêter")
            }
            progress.error?.let { ErrorCard(it) }
        }
        done -> androidx.compose.material3.OutlinedButton(onClick = { confirm = true }, modifier = modifier) {
            Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
            Text("Supprimer la copie hors ligne")
        }
        else -> androidx.compose.material3.OutlinedButton(onClick = { mgr.start(volumeId, title) }, modifier = modifier) {
            Icon(Icons.Outlined.DownloadForOffline, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
            Text("Télécharger hors ligne")
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Supprimer la copie ?") },
        text = { Text("$title\nL'album reste sur Bullarr ; seule la copie de l'appareil est supprimée.") },
        confirmButton = { TextButton(onClick = { mgr.delete(volumeId); confirm = false }) { Text("Supprimer", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Annuler") } },
    )
}

/** Icône d'état hors ligne d'une ligne d'album : télécharger, progression (appui = arrêter), copie présente (appui = supprimer). */
@Composable
fun OfflineIcon(vm: AppViewModel, volumeId: Int, title: String) {
    val mgr = vm.offline
    val progress = mgr.active[volumeId]
    val done = mgr.isDownloaded(volumeId)
    var confirm by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    when {
        progress != null && progress.error == null -> IconButton(onClick = { mgr.cancel(volumeId) }) {
            Box(contentAlignment = Alignment.Center) {
                androidx.compose.material3.CircularProgressIndicator(
                    progress = { if (progress.total > 0) progress.done.toFloat() / progress.total else 0f },
                    modifier = Modifier.size(24.dp), strokeWidth = 2.dp,
                )
                Icon(Icons.Outlined.Close, contentDescription = "Arrêter le téléchargement", modifier = Modifier.size(12.dp))
            }
        }
        progress != null -> IconButton(onClick = { mgr.start(volumeId, title) }) {
            Icon(Icons.Outlined.Refresh, contentDescription = "Réessayer le téléchargement", tint = cs.error)
        }
        done -> IconButton(onClick = { confirm = true }) {
            Icon(Icons.Outlined.OfflinePin, contentDescription = "Disponible hors ligne — supprimer la copie", tint = cs.primary)
        }
        else -> IconButton(onClick = { mgr.start(volumeId, title) }) {
            Icon(Icons.Outlined.DownloadForOffline, contentDescription = "Télécharger hors ligne")
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Supprimer la copie ?") },
        text = { Text("$title\nL'album reste sur Bullarr ; seule la copie de l'appareil est supprimée.") },
        confirmButton = { TextButton(onClick = { mgr.delete(volumeId); confirm = false }) { Text("Supprimer", color = cs.error) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Annuler") } },
    )
}

/** Un tome possédé d'une série : identifiant, titre affiché, nombre de pages connu. */
data class OfflineVolume(val id: Int, val title: String, val pages: Int?)

/**
 * Icône unique de l'en-tête d'une série, à côté de son statut : télécharger tous les albums,
 * suivre l'avancement (appui = tout arrêter) ou, une fois tout copié, supprimer les copies.
 */
@Composable
fun SeriesOfflineIcon(vm: AppViewModel, volumes: List<OfflineVolume>) {
    if (volumes.isEmpty()) return
    val mgr = vm.offline
    val cs = MaterialTheme.colorScheme
    val downloaded = volumes.count { mgr.isDownloaded(it.id) }
    val running = volumes.filter { mgr.active[it.id]?.error == null && mgr.active.containsKey(it.id) }
    val failed = volumes.filter { mgr.active[it.id]?.error != null }
    val todo = volumes.filter { !mgr.isDownloaded(it.id) && !mgr.active.containsKey(it.id) }
    var confirm by remember { mutableStateOf(false) }
    // Estimation : ~1 Mo par page quand le nombre de pages est connu, 40 pages sinon.
    val estimate = todo.sumOf { (it.pages ?: 40) * 1_000_000L }

    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = {
                when {
                    running.isNotEmpty() -> running.forEach { mgr.cancel(it.id) }
                    failed.isNotEmpty() -> failed.forEach { mgr.start(it.id, it.title) }
                    else -> confirm = true
                }
            },
            modifier = Modifier.size(32.dp),
        ) {
            when {
                running.isNotEmpty() -> Box(contentAlignment = Alignment.Center) {
                    val total = volumes.sumOf { mgr.active[it.id]?.total ?: 0 }.coerceAtLeast(1)
                    val done = volumes.sumOf { mgr.active[it.id]?.done ?: 0 }
                    androidx.compose.material3.CircularProgressIndicator(progress = { done.toFloat() / total }, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    Icon(Icons.Outlined.Close, contentDescription = "Arrêter les téléchargements", modifier = Modifier.size(11.dp))
                }
                failed.isNotEmpty() -> Icon(Icons.Outlined.Refresh, contentDescription = "Réessayer les téléchargements en erreur", tint = cs.error)
                todo.isEmpty() -> Icon(Icons.Outlined.OfflinePin, contentDescription = "Tous les albums sont hors ligne — supprimer les copies", tint = cs.primary)
                else -> Icon(Icons.Outlined.DownloadForOffline, contentDescription = "Télécharger tous les albums hors ligne")
            }
        }
        // Compteur discret : « 3/12 » albums copiés.
        if (downloaded > 0 || running.isNotEmpty()) {
            Text("$downloaded/${volumes.size}", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
        }
    }

    if (confirm && todo.isNotEmpty()) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Tout télécharger hors ligne ?") },
        text = { Text("${todo.size} album(s), environ ${formatBytes(estimate) ?: ""} (estimation). Les téléchargements continuent tant que l'app est ouverte.") },
        confirmButton = { TextButton(onClick = { todo.forEach { mgr.start(it.id, it.title) }; confirm = false }) { Text("Télécharger") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Annuler") } },
    )
    if (confirm && todo.isEmpty()) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Supprimer les copies ?") },
        text = { Text("$downloaded album(s) seront supprimés de l'appareil. Ils restent sur Bullarr.") },
        confirmButton = { TextButton(onClick = { volumes.forEach { mgr.delete(it.id) }; confirm = false }) { Text("Supprimer", color = cs.error) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Annuler") } },
    )
}
