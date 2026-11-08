package io.github.gwenvador.bullarr.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MergeType
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.gwenvador.bullarr.data.ApiException
import io.github.gwenvador.bullarr.data.BullarrApi
import io.github.gwenvador.bullarr.data.bool
import io.github.gwenvador.bullarr.data.dbl
import io.github.gwenvador.bullarr.data.int
import io.github.gwenvador.bullarr.data.objects
import io.github.gwenvador.bullarr.data.readable
import io.github.gwenvador.bullarr.data.str
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

// IA de Bullarr (routes /api/ai, voir blueprints/ai/routes.py côté serveur) : réglages du fournisseur,
// création et normalisation des tags, historique. Les clés API ne sont jamais relues en clair :
// le serveur renvoie « **** » quand une clé est enregistrée, et renvoyer « **** » la garde.

/** Onglet de réglages des tags, à ouvrir depuis la fenêtre Tags d'une série (IA désactivée). */
val AI_CONFIG_TAB = Section.Config.subs.indexOf("IA")

private const val MASK = "****"

/** Message d'erreur du serveur tel quel, sans le préfixe « POST /api/... : » ni l'aide ajoutée par l'app. */
fun aiErrorText(e: Exception): String =
    if (e is ApiException) e.message.orEmpty().substringAfter(" : ").substringBefore("\n") else e.readable()

data class AiModel(val id: String, val name: String)

data class AiProvider(
    val id: String, val name: String, val model: String, val models: List<AiModel>, val dynamicModels: Boolean,
    val hasKey: Boolean, val keyHint: String, val keyPlaceholder: String?, val keyUrl: String?,
    /** « required », « optional » ou « none ». */
    val keyMode: String, val llm: Boolean, val baseUrl: String?, val defaultBaseUrl: String?,
)

private fun parseProviders(o: JSONObject): List<AiProvider> = o.optJSONArray("providers").objects().map { p ->
    AiProvider(
        p.str("id") ?: "", p.str("name") ?: p.str("id") ?: "", p.str("model") ?: "",
        p.optJSONArray("models").objects().map { AiModel(it.str("id") ?: "", it.str("name") ?: it.str("id") ?: "") },
        p.bool("dynamic_models") == true, p.bool("has_api_key") == true, p.str("api_key_hint") ?: "",
        p.str("key_placeholder"), p.str("key_url"), p.str("key") ?: "required", p.bool("llm") != false,
        p.str("base_url"), p.str("default_base_url"),
    )
}

/** Formulaire des réglages : modèle, clé et adresse se règlent par fournisseur, comme sur le site. */
private class AiForm(o: JSONObject) {
    val providers = parseProviders(o)
    var enabled by mutableStateOf(o.bool("enabled") == true)
    var autoTagNew by mutableStateOf(o.bool("auto_tag_new") == true)
    var provider by mutableStateOf(o.str("provider") ?: providers.firstOrNull()?.id ?: "")
    val models = mutableStateMapOf<String, String>().apply { providers.forEach { put(it.id, it.model) } }
    val extraModels = mutableStateMapOf<String, List<AiModel>>()
    /** Clé saisie ; vide = garder la clé enregistrée. */
    val keys = mutableStateMapOf<String, String>()
    var baseUrl by mutableStateOf(providers.firstOrNull { it.id == "local" }?.baseUrl ?: "")

    val current: AiProvider? get() = providers.firstOrNull { it.id == provider }

    /** Corps envoyé à /config, /test ou /models pour le fournisseur choisi. */
    fun body(withModel: Boolean = true): JSONObject {
        val p = current
        val b = JSONObject().put("provider", provider)
        if (withModel) b.put("model", models[provider] ?: p?.model ?: "")
        if (p != null && p.keyMode != "none") {
            val typed = keys[provider].orEmpty().trim()
            b.put("api_key", typed.ifEmpty { if (p.hasKey) MASK else "" })
        }
        if (provider == "local") b.put("base_url", baseUrl.trim())
        return b
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiSettingsScreen(vm: AppViewModel, snackbar: SnackbarHostState) {
    val api = vm.api ?: return
    val scope = rememberCoroutineScope()
    var form by remember { mutableStateOf<AiForm?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var stats by remember { mutableStateOf<JSONObject?>(null) }
    var job by remember { mutableStateOf<JSONObject?>(null) }
    var poll by remember { mutableIntStateOf(0) }
    var history by remember { mutableStateOf<List<JSONObject>?>(null) }
    var historyTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(reload) {
        loadError = null
        try { form = AiForm(api.getObject("api/ai/config")) } catch (e: Exception) { loadError = e.readable() }
    }
    LaunchedEffect(reload, historyTick) { runCatching { stats = api.getObject("api/ai/stats") } }
    LaunchedEffect(historyTick) { runCatching { history = api.getObject("api/ai/history").optJSONArray("entries").objects() } }
    // Suivi du traitement en cours : toutes les 2 s tant qu'il tourne.
    LaunchedEffect(poll) {
        while (true) {
            val j = runCatching { api.getObject("api/ai/job") }.getOrNull() ?: break
            val was = job?.str("status")
            job = j
            if (j.str("status") != "running") {
                if (was == "running") historyTick++
                break
            }
            delay(2000)
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text(
                "L'IA propose des tags pour les séries (genre, thèmes, ambiance) et regroupe les tags en double. " +
                    "Elle ajoute des tags, elle n'en retire jamais.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        loadError?.let { item { ErrorCard(it, onRetry = { reload++ }) } }
        form?.let { f ->
            item { AiProviderCard(api, snackbar, f, onSaved = { reload++ }) }
            item {
                AiJobsCard(
                    api, f, stats, job,
                    onStarted = { poll++ },
                    onApplied = { job = null; historyTick++; reload++ },
                )
            }
            item { AiHistoryCard(api, history, onCleared = { historyTick++ }, onOpenSeries = { id, title -> vm.openSeriesById(id, title) }) }
        }
        if (form == null && loadError == null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
    }
}

@Composable
private fun AiCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, subtitle: String? = null, content: @Composable () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = cs.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant) }
                }
            }
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AiProviderCard(api: BullarrApi, snackbar: SnackbarHostState, f: AiForm, onSaved: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    var busy by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var loadingModels by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    val p = f.current

    AiCard("IA", Icons.Outlined.SmartToy, if (f.enabled) "Activée · ${p?.name ?: f.provider}" else "Désactivée") {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Activer l'IA", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Switch(checked = f.enabled, onCheckedChange = { f.enabled = it })
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Taguer les nouvelles séries", style = MaterialTheme.typography.bodyLarge)
                Text("Les séries ajoutées reçoivent leurs tags automatiquement.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            Switch(checked = f.autoTagNew, onCheckedChange = { f.autoTagNew = it })
        }
        Text("Fournisseur", style = MaterialTheme.typography.bodyMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            f.providers.forEach { pr ->
                FilterChip(selected = f.provider == pr.id, onClick = { f.provider = pr.id; message = null }, label = { Text(pr.name) })
            }
        }
        if (p != null) {
            if (!p.llm) Text(
                "Ce fournisseur propose des tags mais ne sait pas regrouper les tags en double.",
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
            )
            // Modèle : liste du fournisseur, rechargeable quand elle est dynamique.
            val models = (f.extraModels[p.id] ?: p.models).let { list ->
                val sel = f.models[p.id] ?: p.model
                if (sel.isNotEmpty() && list.none { it.id == sel }) list + AiModel(sel, sel) else list
            }
            if (models.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
                var open by remember(p.id) { mutableStateOf(false) }
                val sel = f.models[p.id] ?: p.model
                Box(Modifier.weight(1f)) {
                    ClearableField(
                        models.firstOrNull { it.id == sel }?.name ?: sel, {}, Modifier.fillMaxWidth(), label = "Modèle",
                        trailing = { IconButton(onClick = { open = true }) { Icon(Icons.Outlined.ArrowDropDown, contentDescription = "Choisir le modèle") } },
                    )
                    Box(Modifier.matchParentSize().clickable { open = true })
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                        models.forEach { m ->
                            DropdownMenuItem(text = { Text(m.name) }, onClick = { f.models[p.id] = m.id; open = false })
                        }
                    }
                }
                if (p.dynamicModels) {
                    if (loadingModels) CircularProgressIndicator(Modifier.padding(12.dp).size(20.dp), strokeWidth = 2.dp)
                    else IconButton(onClick = {
                        scope.launch {
                            loadingModels = true; message = null
                            try {
                                val res = api.postObject("api/ai/models", f.body(withModel = false))
                                f.extraModels[p.id] = res.optJSONArray("models").objects().map { AiModel(it.str("id") ?: "", it.str("name") ?: it.str("id") ?: "") }
                            } catch (e: Exception) { message = aiErrorText(e) to true }
                            loadingModels = false
                        }
                    }) { Icon(Icons.Outlined.Refresh, contentDescription = "Recharger la liste des modèles") }
                }
            }
            if (p.keyMode != "none") {
                var show by remember(p.id) { mutableStateOf(false) }
                ClearableField(
                    f.keys[p.id].orEmpty(), { f.keys[p.id] = it }, Modifier.fillMaxWidth(),
                    label = if (p.keyMode == "optional") "Clé API (facultative)" else "Clé API",
                    placeholder = if (p.hasKey) "Enregistrée ${p.keyHint} — laisser vide pour la garder" else p.keyPlaceholder,
                    leadingIcon = { Icon(Icons.Outlined.VpnKey, contentDescription = null) },
                    visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailing = {
                        IconButton(onClick = { show = !show }) {
                            Icon(if (show) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, contentDescription = if (show) "Masquer" else "Afficher")
                        }
                    },
                )
                p.keyUrl?.let { link ->
                    TextButton(onClick = { runCatching { uri.openUri(link) } }) {
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp)); Text("Obtenir une clé")
                    }
                }
            }
            if (p.id == "local") ClearableField(
                f.baseUrl, { f.baseUrl = it }, Modifier.fillMaxWidth(), label = "Adresse du LLM local", placeholder = p.defaultBaseUrl,
                leadingIcon = { Icon(Icons.Outlined.Link, contentDescription = null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
        }
        message?.let { (m, err) -> if (err) ErrorCard(m) else Text(m, color = cs.primary, style = MaterialTheme.typography.bodyMedium) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !busy, onClick = {
                scope.launch {
                    busy = true; message = null
                    try {
                        api.postObject("api/ai/config", f.body().put("enabled", f.enabled).put("auto_tag_new", f.autoTagNew))
                        snackbar.showSnackbar("IA : enregistré.")
                        onSaved()
                    } catch (e: Exception) { message = aiErrorText(e) to true }
                    busy = false
                }
            }) { if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Enregistrer") }
            OutlinedButton(enabled = !testing && !busy, onClick = {
                scope.launch {
                    testing = true; message = null
                    try {
                        val res = api.postObject("api/ai/test", f.body())
                        message = (res.str("message") ?: "Connexion réussie.") to false
                    } catch (e: Exception) { message = aiErrorText(e) to true }
                    testing = false
                }
            }) { if (testing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Tester") }
        }
        Text("Le test utilise les valeurs saisies, sans les enregistrer.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
    }
}

/** Création des tags (séries sans tags ou toutes) et normalisation, avec l'avancement du traitement. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AiJobsCard(api: BullarrApi, f: AiForm, stats: JSONObject?, job: JSONObject?, onStarted: () -> Unit, onApplied: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    var confirmAll by remember { mutableStateOf(false) }
    val running = job?.str("status") == "running"
    val untagged = stats?.int("untagged")
    val total = stats?.int("total")
    val pending = stats?.int("auto_pending") ?: 0
    val llm = f.providers.firstOrNull { it.id == f.provider }?.llm != false

    fun start(path: String, body: JSONObject) = scope.launch {
        error = null
        try { api.postObject(path, body); onStarted() } catch (e: Exception) { error = aiErrorText(e) }
    }

    AiCard(
        "Tags", Icons.Outlined.Sell,
        listOfNotNull(
            total?.let { "$it séries" }, untagged?.let { "$it sans tags" },
            pending.takeIf { it > 0 }?.let { "$it nouvelle(s) en attente" },
        ).joinToString(" · ").ifEmpty { null },
    ) {
        if (!f.enabled) Text("Activez l'IA et enregistrez pour lancer un traitement.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = f.enabled && !running, onClick = { start("api/ai/tags/generate", JSONObject().put("scope", "untagged")) }) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp)); Text("Séries sans tags" + (untagged?.let { " ($it)" } ?: ""))
            }
            OutlinedButton(enabled = f.enabled && !running, onClick = { confirmAll = true }) { Text("Toutes les séries") }
            OutlinedButton(enabled = f.enabled && !running && llm, onClick = { start("api/ai/tags/normalize", JSONObject()) }) {
                Icon(Icons.Outlined.MergeType, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp)); Text("Regrouper les doublons")
            }
        }
        if (!llm) Text("Le regroupement demande un modèle de langage : choisissez un autre fournisseur.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        error?.let { ErrorCard(it) }
        if (job != null && job.str("status") != "idle") {
            HorizontalDivider(color = cs.outlineVariant)
            val kind = when (job.str("kind")) { "generate" -> "Création des tags"; "normalize" -> "Regroupement des tags"; "auto" -> "Tags des nouvelles séries"; else -> "Traitement" }
            Text(kind, style = MaterialTheme.typography.titleSmall)
            val progress = job.int("progress") ?: 0
            val totalJob = job.int("total") ?: 0
            if (running) {
                if (totalJob > 0) LinearProgressIndicator(progress = { progress.toFloat() / totalJob }, modifier = Modifier.fillMaxWidth())
                else LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            job.str("message")?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = if (job.str("status") == "error") cs.error else cs.onSurface)
            }
            val groups = if (job.str("kind") == "normalize" && job.str("status") == "done") job.optJSONObject("result")?.optJSONArray("groups").objects() else emptyList()
            if (groups.isNotEmpty()) NormalizeGroups(api, groups, onApplied)
        }
    }
    if (confirmAll) ConfirmDialog(
        "Taguer toutes les séries ?",
        "L'IA propose des tags pour les ${total ?: ""} séries, y compris celles qui en ont déjà. Elle ajoute des tags sans en retirer. Cela peut être long.",
        "Lancer", onConfirm = { start("api/ai/tags/generate", JSONObject().put("scope", "all")) }, onDismiss = { confirmAll = false },
    )
}

/** Regroupements proposés par l'IA : chacun se coche, puis « Appliquer » fusionne les variantes. */
@Composable
private fun NormalizeGroups(api: BullarrApi, groups: List<JSONObject>, onApplied: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val checked = remember(groups) { mutableStateMapOf<Int, Boolean>().apply { groups.indices.forEach { put(it, true) } } }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    groups.forEachIndexed { i, g ->
        Row(verticalAlignment = Alignment.Top) {
            Checkbox(checked = checked[i] == true, onCheckedChange = { checked[i] = it })
            Column(Modifier.weight(1f).padding(top = 12.dp)) {
                Text(g.str("canonical") ?: "", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                val variants = g.optJSONArray("variants").objects().joinToString(", ") { v -> (v.str("tag") ?: "") + (v.int("series")?.let { " ($it)" } ?: "") }
                Text("← $variants", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                g.str("reason")?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant) }
            }
        }
    }
    error?.let { ErrorCard(it) }
    val n = checked.count { it.value }
    Button(enabled = n > 0 && !busy, onClick = {
        scope.launch {
            busy = true; error = null
            try {
                val body = JSONArray()
                groups.forEachIndexed { i, g ->
                    if (checked[i] == true) body.put(JSONObject().put("canonical", g.str("canonical")).put("variants", JSONArray(g.optJSONArray("variants").objects().mapNotNull { it.str("tag") })))
                }
                api.postObject("api/ai/tags/normalize/apply", JSONObject().put("groups", body))
                onApplied()
            } catch (e: Exception) { error = aiErrorText(e) }
            busy = false
        }
    }) { if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Appliquer ($n)") }
}

@Composable
private fun AiHistoryCard(api: BullarrApi, entries: List<JSONObject>?, onCleared: () -> Unit, onOpenSeries: ((Int, String) -> Unit)? = null) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf(false) }
    var all by remember { mutableStateOf(false) }
    var openIds by remember { mutableStateOf(emptySet<String>()) }
    AiCard("Historique", Icons.Outlined.History, entries?.let { "${it.size} traitement(s)" }) {
        when {
            entries == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
            entries.isEmpty() -> Text("Aucun traitement.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            else -> {
                val fmt = remember { java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.FRANCE) }
                (if (all) entries else entries.take(10)).forEach { e ->
                    val kind = aiKindLabel(e)
                    val id = e.str("id") ?: ""
                    val open = id in openIds
                    val hasDetails = (e.optJSONArray("details")?.length() ?: 0) > 0
                    val at = e.dbl("finished_at")?.let { fmt.format(java.util.Date((it * 1000).toLong())) }
                    Column(
                        Modifier.fillMaxWidth().then(if (hasDetails) Modifier.clickable { openIds = if (open) openIds - id else openIds + id } else Modifier),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(listOfNotNull(at, kind).joinToString(" · "), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                            if (hasDetails) Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = if (open) "Replier" else "Voir les tags", tint = cs.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        }
                        Text(
                            listOfNotNull(e.str("message")?.ifBlank { null }, e.str("model")).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = if (e.str("status") == "error") cs.error else cs.onSurfaceVariant,
                        )
                        if (open) Box(Modifier.padding(start = 8.dp, top = 6.dp, bottom = 4.dp)) { AiEntryDetails(e, onOpenSeries) }
                    }
                }
                Row {
                    if (entries.size > 10) TextButton(onClick = { all = !all }) { Text(if (all) "Voir moins" else "Tout voir (${entries.size})") }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { confirm = true }) {
                        Icon(Icons.Outlined.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp)); Text("Vider")
                    }
                }
            }
        }
    }
    if (confirm) ConfirmDialog("Vider l'historique ?", "Les tags déjà ajoutés restent en place.", "Vider", onConfirm = {
        scope.launch { runCatching { api.deleteObject("api/ai/history") }; onCleared() }
    }, onDismiss = { confirm = false })
}

/** Libellé d'un traitement de l'historique IA. */
internal fun aiKindLabel(e: JSONObject): String = when (e.str("kind")) {
    "generate" -> "Création des tags"; "normalize" -> "Regroupement"; "apply" -> "Regroupement appliqué"
    "auto" -> "Nouvelles séries"; "series" -> "Série" + (e.str("title")?.let { " : $it" } ?: ""); else -> e.str("kind") ?: ""
}

/**
 * Détail d'un traitement IA : tags ajoutés (ou proposés) par série, ou regroupements de tags.
 * [onOpenSeries] rend le titre de chaque série cliquable.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun AiEntryDetails(e: JSONObject, onOpenSeries: ((Int, String) -> Unit)? = null, limit: Int = Int.MAX_VALUE) {
    val cs = MaterialTheme.colorScheme
    val details = e.optJSONArray("details").objects()
    val total = e.optInt("details_total", details.size)
    if (details.isEmpty()) {
        Text("Pas de détail enregistré.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant); return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        details.take(limit).forEach { d ->
            val canonical = d.str("canonical")
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (canonical != null) {
                    Text("→ $canonical", style = MaterialTheme.typography.labelLarge, color = cs.primary)
                    val variants = d.optJSONArray("variants").let { a -> if (a == null) emptyList() else (0 until a.length()).map { a.optString(it) } }.filter { it != canonical }
                    if (variants.isNotEmpty()) androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        variants.forEach { Tag(it, cs.surfaceVariant, cs.onSurfaceVariant) }
                    }
                } else {
                    val id = d.int("series_id")
                    val title = d.str("title") ?: "(série)"
                    Text(
                        title, style = MaterialTheme.typography.labelLarge,
                        color = if (id != null && onOpenSeries != null) cs.primary else cs.onSurface,
                        modifier = if (id != null && onOpenSeries != null) Modifier.clickable { onOpenSeries(id, title) } else Modifier,
                    )
                    val added = d.optJSONArray("added").let { a -> if (a == null) emptyList() else (0 until a.length()).map { a.optString(it) } }
                    if (added.isEmpty()) Text("Aucun tag", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    else androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        added.forEach { TopicTag(it) }
                    }
                }
            }
        }
        val hidden = total - minOf(limit, details.size)
        if (hidden > 0) Text("… et $hidden autre(s)", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
    }
}
