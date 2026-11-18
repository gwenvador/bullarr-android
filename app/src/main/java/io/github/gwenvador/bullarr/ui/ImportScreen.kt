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
import io.github.gwenvador.bullarr.data.int
import io.github.gwenvador.bullarr.data.objects
import io.github.gwenvador.bullarr.data.parseImportProgress
import io.github.gwenvador.bullarr.data.parsePendingCount
import io.github.gwenvador.bullarr.data.readable
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.text.style.TextOverflow
import io.github.gwenvador.bullarr.data.str
import io.github.gwenvador.bullarr.data.long

/** Détail d'un fichier en attente : série assignée, raison de l'arrêt automatique, taille. */
data class WaitingDetail(val seriesId: Int?, val seriesTitle: String?, val reason: String?, val sizeBytes: Long?, val filepath: String? = null)

/** État de l'import, sondé toutes les 5 s (lecture seule en base, sans scan disque). */
class ImportInfo(
    val progress: ImportProgress?, val counts: ImportPendingCount?, val error: String?,
    val blocked: Map<Int, String> = emptyMap(),
    /** Clé « suivi|nom de fichier » (voir [waitingKey]). */
    val waitingDetail: Map<String, WaitingDetail> = emptyMap(),
    /** Relit tout de suite l'état de l'import (après « Réessayer »). */
    val reload: () -> Unit = {},
    /** Reprendre l'import automatique mis en pause (depuis le site). */
    val resume: (suspend () -> Unit)? = null,
    /** Annuler le fichier en cours d'import : il repasse en import manuel, la pause n'est pas touchée. */
    val cancel: (suspend () -> Boolean)? = null,
)

fun waitingKey(trackingId: Int?, filename: String) = "$trackingId|$filename"

@Composable
fun rememberImportInfo(vm: AppViewModel): ImportInfo {
    val api = vm.api
    var progress by remember { mutableStateOf<ImportProgress?>(null) }
    var counts by remember { mutableStateOf<ImportPendingCount?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var failures by remember { mutableStateOf(0) }
    Polling(5000, api) {
        if (api != null) try {
            progress = parseImportProgress(api.getObject("api/import/progress"))
            counts = runCatching { parsePendingCount(api.getObject("api/import/pending-count")) }.getOrNull()
            error = null; failures = 0
        } catch (e: Exception) {
            if (++failures >= 2) error = e.readable()
        }
    }
    // Téléchargements terminés que Bullarr ne peut pas importer seul (dossier d'images en vrac) : id de suivi -> explication.
    var blocked by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var waitingDetail by remember { mutableStateOf<Map<String, WaitingDetail>>(emptyMap()) }
    var tick by remember { mutableStateOf(0) }
    Polling(15_000, api to tick) {
        if (api != null) runCatching {
            val st = api.getObject("api/import/state")
            // Série assignée, raison de l'arrêt automatique et taille de chaque fichier suivi.
            waitingDetail = st.optJSONArray("files").objects().associate { f ->
                val dest = f.optJSONObject("destination")
                waitingKey(dest?.int("tracking_id"), f.str("filename") ?: "") to WaitingDetail(
                    seriesId = dest?.int("series_id"),
                    seriesTitle = dest?.str("series_title"),
                    reason = f.str("auto_import_skip_reason")?.takeIf { it.isNotBlank() },
                    sizeBytes = f.long("file_size"),
                    filepath = f.str("filepath"),
                )
            }
            blocked = st.optJSONArray("incompatible_folders").objects().mapNotNull { f ->
                val id = f.int("pack_download_id") ?: return@mapNotNull null
                val ext = f.optJSONArray("sample_extensions")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()
                    .joinToString(", ") { it.trimStart('.').uppercase() }
                id to "Non importable automatiquement : dossier de ${f.int("file_count") ?: 0} fichier(s)" +
                    (if (ext.isNotBlank()) " $ext" else "") + " au lieu d'une archive CBZ/CBR/PDF. Sélectionnez-le puis « Importer » pour le convertir en CBZ."
            }.toMap()
        }
    }
    return ImportInfo(progress, counts, error, blocked, waitingDetail, reload = { tick++ }, resume = api?.let { a -> suspend { a.postObject("api/import/resume", org.json.JSONObject()); tick++ } },
        cancel = api?.let { a -> suspend { val r = a.postObject("api/import/cancel", org.json.JSONObject()); tick++; r.optBoolean("cancelling") } })
}

/** Import en cours / fichiers en attente, intégrés en tête de l'Activité. */
fun androidx.compose.foundation.lazy.LazyListScope.importItems(info: ImportInfo) {
    val progress = info.progress; val counts = info.counts; val error = info.error
    run {
        // Discret : l'Activité garde ses données, une coupure passagère ne mérite pas une grande carte rouge.
        error?.let { item { InlineError("État de l'import indisponible", detail = it) } }
        val p = progress
        if (p != null) {
            if (p.processingFile != null || p.claimedFiles.isNotEmpty() || p.paused) item {
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
                            else -> {}
                        }
                        if (p.paused) Tag("Import automatique en pause : aucun nouveau fichier n'est pris", cs.tertiaryContainer, cs.onTertiaryContainer)
                        // Annuler : le fichier en cours s'arrête (il repasse en import manuel) ; la pause n'est pas touchée.
                        val scope = androidx.compose.runtime.rememberCoroutineScope()
                        val context = androidx.compose.ui.platform.LocalContext.current
                        var sending by remember { mutableStateOf(false) }
                        fun run(block: suspend () -> String) {
                            sending = true
                            scope.launch {
                                val msg = try { block() } catch (e: Exception) { e.readable() }
                                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                sending = false
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val cancel = info.cancel
                            if (p.processingFile != null && cancel != null) androidx.compose.material3.OutlinedButton(
                                enabled = !sending,
                                onClick = { run { if (cancel()) "Import annulé : le fichier repasse en import manuel" else "Aucun import actif à annuler" } },
                                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = cs.error),
                                border = androidx.compose.foundation.BorderStroke(1.dp, cs.error),
                            ) { Text("Annuler l'import") }
                            val resume = info.resume
                            if (p.paused && resume != null) androidx.compose.material3.OutlinedButton(
                                enabled = !sending,
                                onClick = { run { resume(); "Import automatique repris" } },
                                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = cs.onSurface),
                                border = androidx.compose.foundation.BorderStroke(1.dp, cs.onSurfaceVariant),
                            ) { Text("Reprendre l'import") }
                        }
                    }
                }
            }
            counts?.takeIf { it.waitingFiles > 0 || it.awaitingDiscovery > 0 }?.let { c ->
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
