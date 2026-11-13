package io.github.gwenvador.bullarr.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.gwenvador.bullarr.data.BullarrApi
import io.github.gwenvador.bullarr.data.Upscaler
import io.github.gwenvador.bullarr.data.bool
import io.github.gwenvador.bullarr.data.int
import io.github.gwenvador.bullarr.data.long
import io.github.gwenvador.bullarr.data.objects
import io.github.gwenvador.bullarr.data.readable
import io.github.gwenvador.bullarr.data.str
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** Catalogue des modèles du serveur : ids dans l'ordre, libellé, présence. */
internal class ServerModels(o: JSONObject) {
    val order: List<String> = o.optJSONArray("order")?.let { a -> List(a.length()) { a.optString(it) } }.orEmpty()
    private val catalog = o.optJSONObject("catalog") ?: JSONObject()
    private val models = o.optJSONObject("models") ?: JSONObject()
    val present: List<String> get() = order.filter { models.optJSONObject(it)?.optBoolean("present") == true }
    fun label(id: String?): String {
        if (id == null) return "?"
        if (id.startsWith("downscale:")) return "Réduction à ${id.split(':').getOrNull(1) ?: "?"} px"
        return catalog.optJSONObject(id)?.str("label") ?: id
    }
    fun detail(id: String): String? = catalog.optJSONObject(id)?.str("detail")
}

private fun bytes(n: Long?): String = when {
    n == null -> "?"
    n >= 1L shl 30 -> String.format("%.1f Go", n / 1073741824.0)
    n >= 1L shl 20 -> String.format("%.0f Mo", n / 1048576.0)
    else -> String.format("%.0f Ko", n / 1024.0)
}

private fun JSONObject.dims(): String? {
    val w = int("page_width"); val h = int("page_height")
    return if (w != null && h != null && w > 0 && h > 0) "$w×$h px" else null
}

private fun JSONObject?.active() = this?.str("status") in setOf("queued", "running")

private fun jobLine(j: JSONObject, models: ServerModels): String {
    val m = models.label(j.str("model"))
    return when (j.str("status")) {
        "queued" -> "En file · $m"
        "running" -> {
            val p = j.int("page"); val t = j.int("total")
            if (p != null && t != null && t > 0) "En cours · page $p/$t · $m" else "En cours · $m"
        }
        "done" -> "Terminé · $m"
        "cancelled" -> "Annulé · $m"
        "error" -> "Échec · ${j.str("error") ?: m}"
        else -> j.str("status") ?: ""
    }
}

private val TABS = listOf("low" to "Basse déf.", "jobs" to "File d'attente", "backups" to "Versions SD", "heavy" to "Lourds")

/** Actions → Upscaling : équivalent de la page web /upscaling (upscaling des tomes sur le serveur). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun UpscalingScreen(vm: AppViewModel, snackbar: SnackbarHostState, onOpenSeries: (Int, String) -> Unit) {
    val api = vm.api ?: return
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf("low") }
    val selected = remember { mutableStateListOf<Int>() }
    var model by rememberSaveable { mutableStateOf<String?>(null) }
    var target by rememberSaveable { mutableStateOf<Int?>(null) }
    var keep by rememberSaveable { mutableStateOf(true) }
    var confirmDelete by remember { mutableStateOf<JSONObject?>(null) }

    suspend fun load() {
        loading = true
        try {
            data = api.getObject("api/upscale/volumes"); error = null
        } catch (e: Exception) { error = e.readable() }
        loading = false
    }
    val d = data
    val jobs = d?.optJSONArray("jobs").objects()
    val running = jobs.any { it.active() }
    // Rafraîchit vite tant qu'un travail tourne, lentement sinon.
    Polling(if (running) 3000L else 30000L, key = running) { load() }
    LaunchedEffect(tab) { selected.clear() }

    fun act(block: suspend () -> String?) {
        busy = true
        scope.launch {
            try { block()?.let { snackbar.showSnackbar(it) } } catch (e: Exception) { snackbar.showSnackbar(e.readable()) }
            busy = false
            load()
        }
    }

    PullToRefreshBox(isRefreshing = loading && d != null, onRefresh = { scope.launch { load() } }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            if (d == null) {
                if (error == null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                error?.let { item { ErrorCard(it, Modifier.padding(16.dp), onRetry = { scope.launch { load() } }) } }
                return@LazyColumn
            }
            val models = ServerModels(d)
            val low = d.optJSONArray("low_definition").objects()
            val heavy = d.optJSONArray("heavy").objects()
            val backups = d.optJSONArray("backups").objects()
            val maxSide = d.int("max_side") ?: 1600
            val serverOn = d.bool("server_enabled") == true
            val runtime = d.bool("runtime") == true

            if (!runtime || !serverOn) item {
                Card(
                    Modifier.fillMaxWidth().padding(16.dp, 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                ) {
                    Text(
                        if (!runtime) "Le moteur d'upscaling n'est pas installé sur le serveur (onnxruntime ou aucun modèle)."
                        else "L'upscaling serveur est désactivé. Activez-le dans Configuration → Upscaling.",
                        Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
            item {
                FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TABS.forEach { (k, label) ->
                        val n = when (k) {
                            "low" -> low.size; "jobs" -> jobs.size; "backups" -> backups.size; else -> heavy.size
                        }
                        FilterChip(selected = tab == k, onClick = { tab = k }, label = { Text("$label ($n)") })
                    }
                }
            }
            if (loading || busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            error?.let { item { ErrorCard(it, Modifier.padding(16.dp), onRetry = { scope.launch { load() } }) } }

            when (tab) {
                "low" -> {
                    val choices = models.present
                    val chosen = model?.takeIf { it in choices } ?: d.str("model")?.takeIf { it in choices } ?: choices.firstOrNull()
                    val pickable = low.filter { it.bool("upscalable") == true && !it.optJSONObject("job").active() }
                    item {
                        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "Albums dont les pages font au plus $maxSide px : l'upscaling les passe en HD sur le serveur. La version SD est gardée et peut être remise.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (choices.isNotEmpty()) ModelPicker(models, choices, chosen) { model = it }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(enabled = pickable.isNotEmpty(), onClick = {
                                    val ids = pickable.mapNotNull { it.int("volume_id") }
                                    if (selected.containsAll(ids)) selected.clear() else { selected.clear(); selected.addAll(ids) }
                                }) { Text(if (pickable.isNotEmpty() && selected.size == pickable.size) "Tout désélectionner" else "Tout sélectionner") }
                                Box(Modifier.weight(1f))
                                Button(
                                    enabled = !busy && selected.isNotEmpty() && chosen != null && runtime && serverOn,
                                    onClick = {
                                        val ids = selected.toList()
                                        act { batch(api, JSONObject().put("items", JSONArray(ids.map { JSONObject().put("volume_id", it).put("model", chosen) }))).also { selected.clear() } }
                                    },
                                ) { Text("Upscaler (${selected.size})") }
                            }
                        }
                    }
                    if (low.isEmpty()) item { EmptyState("Aucun album en basse définition", "Toutes les pages dépassent $maxSide px.") }
                    items(low, key = { "l" + it.int("volume_id") }) { v ->
                        VolumeRow(v, models, selected, selectable = v in pickable, extra = v.optJSONObject("job")?.let { jobLine(it, models) }
                            ?: v.takeIf { it.bool("upscalable") != true }?.str("reason"), onOpenSeries = onOpenSeries)
                    }
                }
                "heavy" -> {
                    val targets = d.optJSONArray("downscale_choices")?.let { a -> List(a.length()) { a.optInt(it) } }.orEmpty()
                    val t = target?.takeIf { it in targets } ?: targets.firstOrNull()
                    val pickable = heavy.filter { it.bool("reducible") == true && !it.optJSONObject("job").active() }
                    item {
                        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "Albums dont des pages dépassent ${bytes(d.long("heavy_page_bytes"))} : la réduction allège le fichier pour la lecture sur mobile.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (targets.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                targets.forEach { n -> FilterChip(selected = t == n, onClick = { target = n }, label = { Text("$n px") }) }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Garder l'original", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Switch(checked = keep, onCheckedChange = { keep = it })
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(enabled = pickable.isNotEmpty(), onClick = {
                                    val ids = pickable.mapNotNull { it.int("volume_id") }
                                    if (selected.containsAll(ids)) selected.clear() else { selected.clear(); selected.addAll(ids) }
                                }) { Text(if (pickable.isNotEmpty() && selected.size == pickable.size) "Tout désélectionner" else "Tout sélectionner") }
                                Box(Modifier.weight(1f))
                                Button(enabled = !busy && selected.isNotEmpty() && t != null, onClick = {
                                    val ids = selected.toList()
                                    act {
                                        batch(api, JSONObject().put("volume_ids", JSONArray(ids)).put("downscale", JSONObject().put("target", t).put("keep", keep)))
                                            .also { selected.clear() }
                                    }
                                }) { Text("Réduire (${selected.size})") }
                            }
                        }
                    }
                    if (heavy.isEmpty()) item { EmptyState("Aucun album trop lourd") }
                    items(heavy, key = { "h" + it.int("volume_id") }) { v ->
                        VolumeRow(v, models, selected, selectable = v in pickable, extra = v.optJSONObject("job")?.let { jobLine(it, models) }
                            ?: v.takeIf { it.bool("reducible") != true }?.str("reason"), onOpenSeries = onOpenSeries)
                    }
                }
                "jobs" -> {
                    if (jobs.isEmpty()) item { EmptyState("File d'attente vide", "Aucun upscaling ni réduction en attente, en cours ou récent.") }
                    items(jobs, key = { "j" + it.int("volume_id") }) { j ->
                        val vid = j.int("volume_id")
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(j.str("series_title") ?: "?", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text(j.str("filename") ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(jobLine(j, models), style = MaterialTheme.typography.bodySmall,
                                    color = if (j.str("status") == "error") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                                if (j.str("status") == "running") {
                                    val p = j.int("page") ?: 0; val tot = j.int("total") ?: 0
                                    if (tot > 0) LinearProgressIndicator(progress = { p.toFloat() / tot }, modifier = Modifier.fillMaxWidth().padding(top = 2.dp))
                                    else LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 2.dp))
                                }
                            }
                            if (j.active() && vid != null) IconButton(enabled = !busy, onClick = {
                                act { api.deleteObject("api/volumes/$vid/upscale"); "Upscaling annulé" }
                            }) { Icon(Icons.Outlined.Close, contentDescription = "Annuler") }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
                else -> {
                    item {
                        Text(
                            "Albums upscalés dont la version SD d'origine est gardée (${bytes(d.long("backup_bytes"))}). Restaurer remet la SD ; supprimer libère la place.",
                            Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (backups.isEmpty()) item { EmptyState("Aucune version SD gardée") }
                    items(backups, key = { "b" + it.int("volume_id") }) { b ->
                        val vid = b.int("volume_id")
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(b.str("series_title") ?: "?", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text(b.str("filename") ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    listOfNotNull(models.label(b.str("model")), b.dims()?.let { "SD $it" }, bytes(b.long("bytes")),
                                        "fichier SD absent".takeIf { b.bool("exists") == false }).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (vid != null) {
                                IconButton(enabled = !busy && b.bool("exists") != false, onClick = {
                                    act { api.postObject("api/volumes/$vid/upscale/restore", JSONObject()).let { it.str("error") ?: "Version SD remise" } }
                                }) { Icon(Icons.Outlined.Restore, contentDescription = "Restaurer la SD") }
                                IconButton(enabled = !busy, onClick = { confirmDelete = b }) {
                                    Icon(Icons.Outlined.Delete, contentDescription = "Supprimer la SD", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }

    confirmDelete?.let { b ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Supprimer la version SD ?") },
            text = { Text("${b.str("series_title")}\n${b.str("filename")}\n\nLa version d'origine sera effacée définitivement ; seule la version upscalée restera.") },
            confirmButton = {
                TextButton(onClick = {
                    val vid = b.int("volume_id"); confirmDelete = null
                    if (vid != null) act { api.deleteObject("api/volumes/$vid/upscale/backup"); "Version SD supprimée" }
                }) { Text("Supprimer", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Annuler") } },
        )
    }
}

private suspend fun batch(api: BullarrApi, body: JSONObject): String {
    val r = api.postObject("api/upscale/batch", body)
    val n = r.optJSONArray("queued")?.length() ?: 0
    val errs = r.optJSONArray("errors")?.length() ?: 0
    return when {
        n == 0 -> r.str("error") ?: "Rien n'a été mis en file"
        errs > 0 -> "$n album(s) en file, $errs refusé(s)"
        else -> "$n album(s) en file"
    }
}

@Composable
private fun ModelPicker(models: ServerModels, choices: List<String>, chosen: String?, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.AutoFixHigh, contentDescription = null)
            Text("  Modèle : ${models.label(chosen)}")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            choices.forEach { id ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(models.label(id), fontWeight = if (id == chosen) FontWeight.SemiBold else FontWeight.Normal)
                            models.detail(id)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    },
                    onClick = { onPick(id); open = false },
                )
            }
        }
    }
}

@Composable
private fun VolumeRow(
    v: JSONObject, models: ServerModels, selected: MutableList<Int>, selectable: Boolean, extra: String?,
    onOpenSeries: (Int, String) -> Unit,
) {
    val vid = v.int("volume_id") ?: return
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth()
            .clickable(enabled = selectable) { if (vid in selected) selected.remove(vid) else selected.add(vid) }
            .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = vid in selected, enabled = selectable, onCheckedChange = { if (it) selected.add(vid) else selected.remove(vid) })
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val title = v.str("series_title") ?: "?"
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable { v.int("series_id")?.let { onOpenSeries(it, title) } })
            Text(v.str("filename") ?: "", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(v.dims(), v.int("page_count")?.let { "$it p." }, bytes(v.long("file_size")), v.str("format")?.uppercase()).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
            )
            extra?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = if (v.optJSONObject("job") != null) cs.primary else cs.error) }
        }
    }
    HorizontalDivider(color = cs.outlineVariant)
}

/** Réglages → Upscaling, partie serveur (administrateurs) : équivalent de Configuration → Upscaling du web. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ServerUpscaleSettings(vm: AppViewModel) {
    val api = vm.api ?: return
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var cfg by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(api) {
        try { cfg = api.getObject("api/settings/upscale") } catch (e: Exception) { error = e.readable() }
    }
    fun save(change: JSONObject.() -> Unit) {
        val c = cfg ?: return
        val body = JSONObject().put("server_enabled", c.optBoolean("server_enabled")).put("model", c.opt("model")).put("max_side", c.opt("max_side"))
        body.change()
        saving = true; saved = null
        scope.launch {
            try {
                val r = api.postObject("api/settings/upscale", body)
                if (r.optBoolean("success", true) && r.str("error") == null) {
                    cfg = if (r.has("order")) r else api.getObject("api/settings/upscale")
                    saved = "Enregistré"
                    Upscaler.syncThreshold(context, api)
                } else error = r.str("error") ?: "Échec de l'enregistrement"
            } catch (e: Exception) { error = e.readable() }
            saving = false
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Sur le serveur", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text("Réglages partagés par tous les lecteurs : upscaling des tomes sur le serveur (Actions → Upscaling) et seuil de basse définition.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        val c = cfg ?: run { if (error == null) LinearProgressIndicator(Modifier.fillMaxWidth()); return@Column }
        val models = ServerModels(c)
        if (c.bool("runtime") == false) Text("Moteur absent sur le serveur (onnxruntime ou aucun modèle) : l'upscaling serveur ne peut pas tourner.",
            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Upscaling serveur autorisé", style = MaterialTheme.typography.bodyMedium)
                Text("Permet de passer des albums en HD sur le serveur.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = c.optBoolean("server_enabled"), enabled = !saving, onCheckedChange = { on -> save { put("server_enabled", on) } })
        }
        val present = models.present
        Text("Modèle par défaut", style = MaterialTheme.typography.bodyMedium)
        if (present.isEmpty()) Text("Aucun modèle présent sur le serveur.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        else ModelPicker(models, present, c.str("model")) { id -> if (!saving) save { put("model", id) } }
        val choices = c.optJSONArray("max_side_choices")?.let { a -> List(a.length()) { a.optInt(it) } }.orEmpty()
        if (choices.isNotEmpty()) {
            Text("Seuil de basse définition", style = MaterialTheme.typography.bodyMedium)
            Text("Une page dont le plus grand côté dépasse ce seuil est déjà assez fine : pas d'upscaling (serveur et téléphone).",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                choices.forEach { n ->
                    FilterChip(selected = c.optInt("max_side") == n, enabled = !saving, onClick = { save { put("max_side", n) } }, label = { Text("$n px") })
                }
            }
        }
        if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
        saved?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
    }
}
