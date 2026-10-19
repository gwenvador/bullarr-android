package io.github.gwenvador.bullarr.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.gwenvador.bullarr.data.bool
import io.github.gwenvador.bullarr.data.int
import io.github.gwenvador.bullarr.data.normalize
import io.github.gwenvador.bullarr.data.objects
import io.github.gwenvador.bullarr.data.parseLibraries
import io.github.gwenvador.bullarr.data.readable
import io.github.gwenvador.bullarr.data.str
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Ligne générique : titre, détail, date, état ; un appui ouvre la série si elle est connue. */
private data class Row3(
    val kind: String,
    val title: String,
    val detail: String?,
    val date: String?,
    val ok: Boolean?,
    val seriesId: Int?,
    val seriesTitle: String?,
)

@Composable
private fun SimpleRow(r: Row3, onOpenSeries: (Int, String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth()
            .clickable(enabled = r.seriesId != null) { r.seriesId?.let { onOpenSeries(it, r.seriesTitle ?: r.title) } }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Tag(r.kind, cs.surfaceVariant, cs.onSurfaceVariant)
            when (r.ok) {
                true -> Tag("OK", cs.secondaryContainer, cs.onSecondaryContainer)
                false -> Tag("Échec", cs.errorContainer, cs.onErrorContainer)
                null -> {}
            }
            r.date?.let { Text(it.take(16), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant) }
        }
        Text(r.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 3, overflow = TextOverflow.Ellipsis)
        r.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 4, overflow = TextOverflow.Ellipsis) }
    }
}

// --- Historique : /api/import/history, /api/missing-monitor/history, /api/actions/history ---

private val ACTION_LABELS = mapOf(
    "search" to "Recherche", "rename" to "Renommage", "add_series" to "Série ajoutée",
    "missing_volume_match" to "Tome trouvé", "auto_acquire" to "Acquisition auto",
    "delete_volume" to "Tome supprimé", "move_volume" to "Tome déplacé", "convert" to "Conversion",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HistoryScreen(vm: AppViewModel, onOpenSeries: (Int, String) -> Unit) {
    val api = vm.api ?: return
    var rows by remember { mutableStateOf<List<Row3>?>(null) }
    val errors = remember { mutableStateMapOf<String, String>() }
    var filter by rememberSaveable { mutableStateOf("Tout") }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        loading = true; errors.clear()
        suspend fun part(name: String, block: suspend () -> List<Row3>) =
            try { block() } catch (e: Exception) { errors[name] = e.readable(); emptyList() }
        rows = coroutineScope {
            listOf(
                async {
                    part("Imports") {
                        api.getObject("api/import/history").optJSONArray("history").objects().map { h ->
                            val counts = listOfNotNull(
                                h.int("files_imported")?.takeIf { it > 0 }?.let { "$it importé(s)" },
                                h.int("files_replaced")?.takeIf { it > 0 }?.let { "$it remplacé(s)" },
                                h.int("files_skipped")?.takeIf { it > 0 }?.let { "$it ignoré(s)" },
                                h.int("files_failed")?.takeIf { it > 0 }?.let { "$it en échec" },
                            ).joinToString(" · ")
                            Row3(
                                "Import", h.str("file_names") ?: "(aucun fichier)",
                                listOfNotNull(if (h.str("operation_type") == "auto_import") "automatique" else "manuel", counts.ifBlank { null }, h.str("status")).joinToString(" · "),
                                h.str("created_at"), h.str("status") != "failed" && (h.int("files_failed") ?: 0) == 0, null, null,
                            )
                        }
                    }
                },
                async {
                    part("Téléchargements") {
                        api.getObject("api/missing-monitor/history").optJSONArray("history").objects().map { h ->
                            Row3(
                                "Téléchargement", h.str("title") ?: "(sans titre)",
                                listOfNotNull(h.str("series_title"), h.str("client"), h.str("message")).joinToString(" · "),
                                h.str("created_at"), h.bool("success"), h.int("series_id"), h.str("series_title"),
                            )
                        }
                    }
                },
                async {
                    part("Actions") {
                        api.getObject("api/actions/history").optJSONArray("history").objects().map { h ->
                            val type = h.str("action_type") ?: "action"
                            Row3(
                                ACTION_LABELS[type] ?: type, h.str("series_title") ?: "(sans série)",
                                listOfNotNull(h.str("detail"), h.str("error")).joinToString(" · ").ifBlank { null },
                                h.str("created_at"), h.bool("success"), h.int("series_id"), h.str("series_title"),
                            )
                        }
                    }
                },
            ).awaitAll().flatten().sortedByDescending { it.date ?: "" }
        }
        loading = false
    }
    LaunchedEffect(api) { if (rows == null) load() }

    val filters = listOf("Tout", "Import", "Téléchargement", "Actions")
    val shown = rows.orEmpty().filter {
        when (filter) {
            "Tout" -> true
            "Actions" -> it.kind != "Import" && it.kind != "Téléchargement"
            else -> it.kind == filter
        }
    }
    PullToRefreshBox(isRefreshing = loading && rows != null, onRefresh = { scope.launch { load() } }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            item {
                FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    filters.forEach { f -> FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f) }) }
                }
            }
            if (loading && rows == null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            errors.forEach { (k, v) -> item(key = "e-$k") { ErrorCard("$k : $v", Modifier.padding(16.dp)) } }
            if (rows != null && shown.isEmpty()) item { EmptyState("Rien dans l'historique") }
            items(shown) { r ->
                SimpleRow(r, onOpenSeries)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

// --- Surveillance : /api/missing-monitor/libraries/<id>/series + POST …/series/<id>/monitor ---

private data class Monitored(val id: Int, val title: String, val enabled: Boolean, val owned: Int, val total: Int?, val missing: Int, val status: String?, val key: String)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MonitorScreen(vm: AppViewModel, snackbar: SnackbarHostState, onOpenSeries: (Int, String) -> Unit) {
    val api = vm.api ?: return
    var list by remember { mutableStateOf<List<Monitored>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var onlyEnabled by rememberSaveable { mutableStateOf(true) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        loading = true; error = null
        try {
            val libs = api.getObject("api/missing-monitor/libraries").optJSONArray("libraries").objects()
            list = libs.flatMap { lib ->
                api.getObject("api/missing-monitor/libraries/${lib.int("id")}/series").optJSONArray("series").objects()
            }.map { s ->
                val missing = runCatching { org.json.JSONArray(s.str("missing_volumes") ?: "[]").length() }.getOrDefault(0)
                val title = s.str("title") ?: "(sans titre)"
                Monitored(s.int("id") ?: -1, title, s.bool("enabled") == true, s.int("total_local") ?: 0,
                    s.int("bedetheque_total_volumes"), missing, s.str("bedetheque_status"), normalize(title))
            }
        } catch (e: Exception) { error = e.readable() }
        loading = false
    }
    LaunchedEffect(api) { if (list == null) load() }

    fun toggle(m: Monitored, on: Boolean) {
        scope.launch {
            try {
                api.postObject("api/missing-monitor/series/${m.id}/monitor", JSONObject().put("enabled", on))
                list = list?.map { if (it.id == m.id) it.copy(enabled = on) else it }
            } catch (e: Exception) { snackbar.showSnackbar(e.readable()) }
        }
    }

    val terms = normalize(query).split(' ').filter { it.isNotBlank() }
    val shown = list.orEmpty().filter { (!onlyEnabled || it.enabled) && terms.all { t -> t in it.key } }
    PullToRefreshBox(isRefreshing = loading && list != null, onRefresh = { scope.launch { load() } }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ClearableField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = "Filtrer les séries…",
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) })
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = onlyEnabled, onClick = { onlyEnabled = true }, label = { Text("Surveillées (${list.orEmpty().count { it.enabled }})") })
                        FilterChip(selected = !onlyEnabled, onClick = { onlyEnabled = false }, label = { Text("Toutes (${list.orEmpty().size})") })
                    }
                }
            }
            if (loading && list == null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            error?.let { item { ErrorCard(it, Modifier.padding(16.dp), onRetry = { scope.launch { load() } }) } }
            items(shown, key = { it.id }) { m ->
                Row(
                    Modifier.fillMaxWidth().clickable { onOpenSeries(m.id, m.title) }.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(m.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(
                            listOfNotNull(m.total?.let { "${m.owned}/$it tomes" } ?: "${m.owned} tome(s)",
                                m.missing.takeIf { it > 0 }?.let { "$it manquant(s)" }, m.status).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = m.enabled, onCheckedChange = { toggle(m, it) })
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

// --- Vérification : GET /api/settings/verification?type=… + corrections de verification.js ---

private val VERIFICATIONS = listOf(
    "missing_metadata" to "Métadonnées manquantes",
    "misnamed" to "Noms non conformes",
    "misplaced_folders" to "Dossiers mal placés",
    "missing_files" to "Fichiers absents",
    "unmatched_owned_volumes" to "Tomes non rattachés",
    "duplicate_series" to "Séries en double",
    "invalid_files" to "Fichiers invalides (lent)",
)

private data class VerifItem(val type: String, val raw: JSONObject) {
    val seriesId get() = raw.int("series_id")
    val volumeId get() = raw.int("volume_id")
    val seriesTitle get() = raw.str("series_title") ?: "(sans série)"
    val detail: String? get() = listOfNotNull(
        raw.str("reason"),
        raw.str("current_name")?.let { "Actuel : $it" }, raw.str("expected_name")?.let { "Attendu : $it" },
        raw.str("current_path")?.let { "Actuel : $it" }, raw.str("expected_path")?.let { "Attendu : $it" },
        raw.str("filename"), raw.str("filepath"),
    ).distinct().joinToString("\n").ifBlank { null }
}

/** Correction proposée pour une ligne ; [danger] = irréversible, demande une confirmation. */
private data class Fix(val label: String, val danger: String? = null, val run: suspend (io.github.gwenvador.bullarr.data.BullarrApi) -> String)

/** Mêmes boutons que la page Vérification (verification.js), ligne par ligne. */
private fun fixesFor(v: VerifItem): List<Fix> {
    val sid = v.seriesId ?: return emptyList()
    val vid = v.volumeId
    fun ok(o: JSONObject, fallback: String): String {
        if (o.has("success") && !o.optBoolean("success")) throw io.github.gwenvador.bullarr.data.ApiException(o.str("error") ?: fallback)
        return o.str("message") ?: fallback
    }
    return when (v.type) {
        "missing_metadata" -> if (v.raw.str("type") == "series" || vid == null) listOf(
            Fix("Matcher sur Bédéthèque (par titre)") { api ->
                ok(api.postObject("api/bedetheque/enrich/$sid", JSONObject().put("search_by", "title").put("value", v.seriesTitle).put("write_volumes", false)), "Série rattachée à Bédéthèque.")
            },
        ) else listOf(
            Fix("Mettre à jour les métadonnées du tome") { api -> ok(api.postObject("api/bedetheque/update-metadata/volume/$vid", JSONObject()), "Métadonnées du tome mises à jour.") },
            Fix("Mettre à jour toute la série") { api ->
                ok(api.postObject("api/bedetheque/update-metadata/series/$sid", JSONObject().put("scope", "all")), "Mise à jour de la série lancée en arrière-plan.")
            },
        )
        "misnamed" -> listOf(Fix(if (v.raw.bool("is_folder") == true) "Renommer le dossier" else "Renommer le fichier") { api ->
            val body = if (v.raw.bool("is_folder") == true || vid == null) JSONObject() else JSONObject().put("volume_id", vid)
            ok(api.postObject("api/series/$sid/rename/execute", body), "Renommé.")
        })
        "misplaced_folders" -> if (v.raw.bool("can_reconcile") == true) listOf(Fix("Déplacer vers l'emplacement attendu") { api ->
            val o = api.postObject("api/series/$sid/rename/execute", JSONObject())
            val folder = o.optJSONObject("folder")
            if (folder != null && !folder.optBoolean("success")) throw io.github.gwenvador.bullarr.data.ApiException(folder.str("error") ?: "Déplacement impossible")
            "Dossier déplacé vers son emplacement attendu."
        }) else emptyList()
        "missing_files" -> listOf(
            Fix("Rechercher et télécharger automatiquement") { api ->
                val o = api.postObject("api/settings/verification/missing-files/$sid/auto-acquire", JSONObject())
                ok(o, "Recherche automatique lancée pour ${o.int("count") ?: 0} tome(s).")
                "Recherche automatique lancée pour ${o.int("count") ?: 0} tome(s)."
            },
            Fix("Supprimer la série de la base", danger = "La série et ses tomes seront retirés de Bullarr. Les fichiers sur le disque ne sont pas touchés.") { api ->
                ok(api.deleteObject("api/settings/verification/missing-files/$sid/remove-record", JSONObject().put("confirm", true)), "Série retirée de la base.")
            },
        )
        "unmatched_owned_volumes" -> if (vid != null) listOf(Fix("Rattacher à Bédéthèque") { api ->
            ok(api.postObject("api/bedetheque/link-volume/$vid", JSONObject()), "Tome rattaché à son album Bédéthèque.")
        }) else emptyList()
        "invalid_files" -> if (vid != null) listOf(Fix("Supprimer le fichier", danger = "Le fichier sera supprimé définitivement du disque. Irréversible.") { api ->
            ok(api.deleteObject("api/volumes/$vid"), "Fichier supprimé.")
        }) else emptyList()
        "duplicate_series" -> listOf(Fix("Supprimer cette fiche vide", danger = "La fiche de série vide sera supprimée.") { api ->
            ok(api.postObject("api/settings/verification/duplicate-series/cleanup", JSONObject().put("series_ids", org.json.JSONArray().put(sid))), "Fiche supprimée.")
        })
        else -> emptyList()
    }
}

@OptIn(ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun VerificationScreen(vm: AppViewModel, snackbar: SnackbarHostState, onOpenSeries: (Int, String) -> Unit) {
    val api = vm.api ?: return
    var type by rememberSaveable { mutableStateOf(VERIFICATIONS.first().first) }
    val results = remember { mutableStateMapOf<String, List<VerifItem>>() }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<VerifItem?>(null) }
    var confirmFix by remember { mutableStateOf<Pair<VerifItem, Fix>?>(null) }
    var running by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun load(t: String) {
        loading = true; error = null
        try {
            val o = api.getObject("api/settings/verification", mapOf("type" to t))
            results[t] = o.optJSONArray(t).objects().map { VerifItem(t, it) }
        } catch (e: Exception) { error = e.readable() }
        loading = false
    }
    LaunchedEffect(api, type) { if (results[type] == null) load(type) }

    fun run(item: VerifItem, fix: Fix) {
        running = true
        scope.launch {
            try {
                val msg = fix.run(api)
                selected = null
                // La ligne corrigée disparaît tout de suite ; la catégorie est recalculée ensuite.
                results[item.type] = results[item.type].orEmpty().filterNot { it === item }
                snackbar.showSnackbar(msg)
                load(item.type)
            } catch (e: Exception) {
                snackbar.showSnackbar(e.readable())
            } finally {
                running = false
                confirmFix = null
            }
        }
    }

    PullToRefreshBox(isRefreshing = loading && results[type] != null, onRefresh = { scope.launch { load(type) } }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            item {
                FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VERIFICATIONS.forEach { (k, label) ->
                        val n = results[k]?.size
                        FilterChip(selected = type == k, onClick = { type = k }, label = { Text(if (n != null) "$label ($n)" else label) })
                    }
                }
            }
            if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            error?.let { item { ErrorCard(it, Modifier.padding(16.dp), onRetry = { scope.launch { load(type) } }) } }
            val list = results[type]
            if (list != null && list.isEmpty()) item { EmptyState("Rien à signaler") }
            items(list.orEmpty()) { v ->
                val cs = MaterialTheme.colorScheme
                Row(
                    Modifier.fillMaxWidth().clickable { selected = v }.padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(v.seriesTitle, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        v.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 4, overflow = TextOverflow.Ellipsis) }
                    }
                    Icon(Icons.Outlined.ChevronRight, contentDescription = "Corriger", tint = cs.onSurfaceVariant)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }

    selected?.let { v ->
        val fixes = fixesFor(v)
        androidx.compose.material3.ModalBottomSheet(onDismissRequest = { if (!running) selected = null }) {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(v.seriesTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(VERIFICATIONS.first { it.first == v.type }.second, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                v.detail?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                if (running) LinearProgressIndicator(Modifier.fillMaxWidth())
                fixes.forEach { fix ->
                    androidx.compose.material3.Button(
                        onClick = { if (fix.danger != null) confirmFix = v to fix else run(v, fix) },
                        enabled = !running, modifier = Modifier.fillMaxWidth(),
                        colors = if (fix.danger != null) androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError,
                        ) else androidx.compose.material3.ButtonDefaults.buttonColors(),
                    ) { Text(fix.label) }
                }
                if (v.type == "missing_files") {
                    androidx.compose.material3.OutlinedButton(
                        onClick = {
                            selected = null
                            vm.search.prefill(SearchContext(v.seriesId!!, v.seriesTitle, null, null, null))
                            vm.goToSearch()
                        },
                        enabled = !running && v.seriesId != null, modifier = Modifier.fillMaxWidth(),
                    ) { Text("Recherche manuelle") }
                }
                v.seriesId?.let { sid ->
                    androidx.compose.material3.OutlinedButton(
                        onClick = { selected = null; onOpenSeries(sid, v.seriesTitle) },
                        enabled = !running, modifier = Modifier.fillMaxWidth(),
                    ) { Text("Ouvrir la série") }
                }
                if (fixes.isEmpty() && v.type == "misplaced_folders") {
                    Text("Conflit à résoudre : le dossier attendu existe déjà. À corriger depuis l'interface web.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    confirmFix?.let { (v, fix) ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { if (!running) confirmFix = null },
            title = { Text(fix.label + " ?") },
            text = { Text("${v.seriesTitle}\n\n${fix.danger}") },
            confirmButton = { androidx.compose.material3.TextButton(enabled = !running, onClick = { run(v, fix) }) { Text("Confirmer", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { androidx.compose.material3.TextButton(enabled = !running, onClick = { confirmFix = null }) { Text("Annuler") } },
        )
    }
}

