package io.github.gwenvador.bullarr.ui

import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import io.github.gwenvador.bullarr.data.ApiException
import io.github.gwenvador.bullarr.data.BullarrApi
import io.github.gwenvador.bullarr.data.FChoice
import io.github.gwenvador.bullarr.data.FList
import io.github.gwenvador.bullarr.data.FMulti
import io.github.gwenvador.bullarr.data.FNumber
import io.github.gwenvador.bullarr.data.FOrder
import io.github.gwenvador.bullarr.data.FSecret
import io.github.gwenvador.bullarr.data.FSwitch
import io.github.gwenvador.bullarr.data.FText
import io.github.gwenvador.bullarr.data.Field
import io.github.gwenvador.bullarr.data.Integration
import io.github.gwenvador.bullarr.data.buildPayload
import io.github.gwenvador.bullarr.data.getPath
import io.github.gwenvador.bullarr.data.initialValue
import io.github.gwenvador.bullarr.data.readable
import io.github.gwenvador.bullarr.data.secretIsSet
import io.github.gwenvador.bullarr.data.str
import kotlinx.coroutines.launch
import org.json.JSONObject

/** État d'édition d'un réglage : valeurs de départ, valeurs modifiées, enregistrement, test. */
class ConfigEditor(val spec: Integration) {
    var loaded by mutableStateOf<JSONObject?>(null); private set
    var loadError by mutableStateOf<String?>(null); private set
    var busy by mutableStateOf(false); private set
    var testing by mutableStateOf(false); private set
    var message by mutableStateOf<String?>(null)
    var messageIsError by mutableStateOf(false)
    private var initial: Map<String, Any> = emptyMap()
    val values = mutableStateMapOf<String, Any>()

    val dirty: Boolean get() = spec.fields.any { f ->
        if (f is FSecret) (values[f.key] as? String).orEmpty().isNotEmpty() else values[f.key] != initial[f.key]
    }

    private fun adopt(o: JSONObject) {
        loaded = o
        initial = spec.fields.associate { it.key to initialValue(it, o) }
        values.clear(); values.putAll(initial)
    }

    suspend fun load(api: BullarrApi) {
        loadError = null
        try { adopt(api.getObject(spec.path)) } catch (e: Exception) { loadError = e.readable() }
    }

    fun reset() { values.clear(); values.putAll(initial); message = null }

    /** Relit le serveur juste avant d'écrire : deux cartes sur le même réglage ne s'écrasent pas. */
    suspend fun save(api: BullarrApi): Boolean {
        busy = true; message = null
        return try {
            val fresh = api.getObject(spec.path)
            api.postObject(spec.path, buildPayload(spec, fresh, initial, values.toMap()))
            adopt(api.getObject(spec.path))
            message = "Enregistré."; messageIsError = false
            true
        } catch (e: Exception) {
            message = e.readable(); messageIsError = true
            false
        } finally { busy = false }
    }

    suspend fun test(api: BullarrApi) {
        val t = spec.test ?: return
        testing = true; message = null
        try {
            val o = if (t.post) api.postObject(t.path, JSONObject()) else api.getObject(t.path)
            message = o.str("message")?.replace(Regex("^[^\\p{L}\\p{N}]+"), "")
                ?: o.str("topics")?.let { "Forum joignable : $it sujets." } ?: "Connexion réussie."
            messageIsError = false
        } catch (e: Exception) {
            message = e.readable(); messageIsError = true
        } finally { testing = false }
    }
}

@Composable
fun ConfigScreen(vm: AppViewModel, snackbar: SnackbarHostState, specs: List<Integration>, footer: (@Composable () -> Unit)? = null) {
    val api = vm.api ?: return
    val editors = remember(specs) { specs.map { ConfigEditor(it) } }
    LaunchedEffect(api, specs) { editors.forEach { e -> if (e.loaded == null) launch { e.load(api) } } }
    var open by remember(specs) { mutableStateOf(if (specs.size == 1) specs.first().id else null) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(editors, key = { it.spec.id }) { e ->
            ConfigCard(api, snackbar, e, expanded = open == e.spec.id, onToggle = { open = if (open == e.spec.id) null else e.spec.id })
        }
        footer?.let { item { it() } }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConfigCard(api: BullarrApi, snackbar: SnackbarHostState, e: ConfigEditor, expanded: Boolean, onToggle: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val o = e.loaded
    val hasEnabled = e.spec.fields.any { it.key == "enabled" }
    val enabled = (o?.getPath("enabled") as? Boolean)

    Card(Modifier.fillMaxWidth().animateContentSize(), colors = CardDefaults.cardColors(containerColor = cs.surfaceContainer)) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(e.spec.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                val status = when {
                    e.loadError != null -> "Erreur de chargement"
                    o == null -> "Chargement…"
                    else -> listOfNotNull(if (hasEnabled) (if (enabled == true) "Activé" else "Désactivé") else null, e.spec.info?.invoke(o)).joinToString(" · ")
                }
                if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodySmall, color = if (e.loadError != null) cs.error else cs.onSurfaceVariant)
            }
            if (o != null && hasEnabled) Tag(if (enabled == true) "ON" else "OFF", if (enabled == true) cs.secondaryContainer else cs.surfaceVariant, if (enabled == true) cs.onSecondaryContainer else cs.onSurfaceVariant)
            Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = if (expanded) "Replier" else "Déplier")
        }
        if (!expanded) return@Card
        e.loadError?.let { ErrorCard(it, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), onRetry = { scope.launch { e.load(api) } }) }
        if (o == null) return@Card
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            e.spec.help?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant) }
            e.spec.fields.forEach { f -> FieldEditor(f, e, o) }
            e.message?.let { m ->
                if (e.messageIsError) ErrorCard(m) else Text(m, color = cs.primary, style = MaterialTheme.typography.bodyMedium)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = e.dirty && !e.busy,
                    onClick = {
                        scope.launch { if (e.save(api)) snackbar.showSnackbar("${e.spec.title} : enregistré.") }
                    },
                ) { if (e.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Enregistrer") }
                if (e.dirty) OutlinedButton(onClick = { e.reset() }, enabled = !e.busy) { Text("Annuler") }
                e.spec.test?.let { t ->
                    OutlinedButton(onClick = { scope.launch { e.test(api) } }, enabled = !e.testing && !e.busy) {
                        if (e.testing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(t.label)
                    }
                }
            }
            e.spec.test?.let { t ->
                Text(
                    t.note ?: "Le test utilise les réglages enregistrés." + if (e.dirty) " Enregistrez d'abord vos modifications." else "",
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FieldEditor(f: Field, e: ConfigEditor, o: JSONObject) {
    val cs = MaterialTheme.colorScheme
    when (f) {
        is FSwitch -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(f.label, style = MaterialTheme.typography.bodyLarge)
                f.help?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant) }
            }
            Switch(checked = e.values[f.key] as? Boolean ?: false, onCheckedChange = { e.values[f.key] = it })
        }
        is FText -> ClearableField(
            e.values[f.key] as? String ?: "", { e.values[f.key] = it }, Modifier.fillMaxWidth(), label = f.label, placeholder = f.hint,
            keyboardOptions = KeyboardOptions(keyboardType = if (f.url) KeyboardType.Uri else KeyboardType.Text),
        )
        is FNumber -> ClearableField(
            e.values[f.key] as? String ?: "", { v -> e.values[f.key] = v.filter { it.isDigit() } }, Modifier.fillMaxWidth(), label = f.label,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        is FList -> ClearableField(
            e.values[f.key] as? String ?: "", { e.values[f.key] = it }, Modifier.fillMaxWidth(), label = f.label, placeholder = f.hint,
            supportingText = "Séparées par des virgules.",
        )
        is FSecret -> {
            var show by remember { mutableStateOf(false) }
            ClearableField(
                e.values[f.key] as? String ?: "", { e.values[f.key] = it }, Modifier.fillMaxWidth(), label = f.label,
                placeholder = if (secretIsSet(f, o)) "Enregistré — laisser vide pour le garder" else null,
                visualTransformation = if (show) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailing = {
                    IconButton(onClick = { show = !show }) {
                        Icon(
                            if (show) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = if (show) "Masquer" else "Afficher",
                        )
                    }
                },
            )
        }
        is FChoice -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(f.label, style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                f.options.forEach { (k, l) -> FilterChip(selected = e.values[f.key] == k, onClick = { e.values[f.key] = k }, label = { Text(l) }) }
            }
        }
        is FMulti -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(f.label, style = MaterialTheme.typography.bodyMedium)
            @Suppress("UNCHECKED_CAST") val sel = e.values[f.key] as? List<String> ?: emptyList()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                f.options.forEach { (k, l) ->
                    FilterChip(selected = k in sel, onClick = { e.values[f.key] = if (k in sel) sel - k else sel + k }, label = { Text(l) })
                }
            }
        }
        is FOrder -> Column {
            Text(f.label, style = MaterialTheme.typography.bodyMedium)
            f.help?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant) }
            @Suppress("UNCHECKED_CAST") val list = e.values[f.key] as? List<String> ?: emptyList()
            list.forEachIndexed { i, name ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}", style = MaterialTheme.typography.titleSmall, color = cs.primary, modifier = Modifier.padding(end = 12.dp))
                    Text(f.names[name] ?: name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    IconButton(onClick = { e.values[f.key] = list.toMutableList().also { it.add(i - 1, it.removeAt(i)) } }, enabled = i > 0) {
                        Icon(Icons.Outlined.ArrowUpward, contentDescription = "Monter")
                    }
                    IconButton(onClick = { e.values[f.key] = list.toMutableList().also { it.add(i + 1, it.removeAt(i)) } }, enabled = i < list.lastIndex) {
                        Icon(Icons.Outlined.ArrowDownward, contentDescription = "Descendre")
                    }
                }
                if (i < list.lastIndex) HorizontalDivider(color = cs.outlineVariant)
            }
        }
    }
}

// Réglages qui n'ont pas (encore) d'éditeur dans l'app : ils s'ouvrent dans l'interface web.
private val WEB_SETTINGS = listOf(
    "indexeurs" to "EBDZ : identifiants, forums, scraping automatique",
    "indexeurs" to "Canaux Telegram : compte, canaux, indexation",
    "oidc" to "Login (SSO, mot de passe) et clé API",
    "libraries" to "Format de renommage",
    "backup" to "Sauvegarde et restauration",
)

@Composable
fun WebSettingsScreen(vm: AppViewModel) {
    val uri = LocalUriHandler.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
        item {
            Text(
                "Ces réglages se modifient dans l'interface web de Bullarr : ils touchent à la sécurité ou ont des options trop riches pour le téléphone.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
        items(WEB_SETTINGS) { (anchor, label) ->
            Row(
                Modifier.fillMaxWidth().clickable { vm.api?.let { runCatching { uri.openUri(it.absoluteUrl("settings") + "#$anchor") } } }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}
