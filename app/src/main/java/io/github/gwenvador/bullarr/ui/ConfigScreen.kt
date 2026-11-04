package io.github.gwenvador.bullarr.ui

import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoveToInbox
import androidx.compose.material.icons.outlined.Numbers
import androidx.compose.material.icons.outlined.Percent
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
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
import io.github.gwenvador.bullarr.ui.FilterChip
import androidx.compose.material3.HorizontalDivider
import io.github.gwenvador.bullarr.ui.Icon
import io.github.gwenvador.bullarr.ui.IconButton
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
fun ConfigScreen(
    vm: AppViewModel, snackbar: SnackbarHostState, specs: List<Integration>,
    header: (@Composable () -> Unit)? = null, footer: (@Composable () -> Unit)? = null,
) {
    val api = vm.api ?: return
    val editors = remember(specs) { specs.map { ConfigEditor(it) } }
    LaunchedEffect(api, specs) { editors.forEach { e -> if (e.loaded == null) launch { e.load(api) } } }
    var open by remember(specs) { mutableStateOf(if (specs.size == 1) specs.first().id else null) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        header?.let { item { it() } }
        items(editors, key = { it.spec.id }) { e ->
            ConfigCard(api, snackbar, e, expanded = open == e.spec.id, onToggle = { open = if (open == e.spec.id) null else e.spec.id })
        }
        footer?.let { item { it() } }
    }
}

/** Icône d'un réglage sans logo de marque. */
private fun configIcon(id: String): ImageVector = when (id) {
    "rename" -> Icons.Outlined.DriveFileRenameOutline
    "import" -> Icons.Outlined.MoveToInbox
    "order" -> Icons.Outlined.FormatListNumbered
    "blocked" -> Icons.Outlined.Block
    "series" -> Icons.Outlined.CreateNewFolder
    "auto" -> Icons.Outlined.Autorenew
    "monitor" -> Icons.Outlined.TrackChanges
    "login" -> Icons.Outlined.Lock
    else -> Icons.Outlined.Tag
}

/** Icône en tête d'un champ texte, d'après sa clé (rien quand aucune ne s'impose). */
private fun fieldIcon(f: Field): ImageVector? {
    val k = f.key.substringAfterLast('.')
    return when {
        f is FSecret -> if ("key" in k) Icons.Outlined.VpnKey else if ("token" in k || "secret" in k) Icons.Outlined.Key else Icons.Outlined.Lock
        k == "url" || k == "base_url" || k == "issuer" -> Icons.Outlined.Link
        k == "host" -> Icons.Outlined.Dns
        k == "port" -> Icons.Outlined.Numbers
        k == "username" -> Icons.Outlined.Person
        k.endsWith("category") -> Icons.Outlined.Sell
        k.endsWith("template") -> Icons.Outlined.Title
        k == "client_id" -> Icons.Outlined.Badge
        k == "chat_id" -> Icons.Outlined.Tag
        k == "scopes" -> Icons.Outlined.Checklist
        "interval" in k -> Icons.Outlined.Schedule
        "percent" in k -> Icons.Outlined.Percent
        "extensions" in k -> Icons.Outlined.Description
        else -> null
    }
}

/** Pastille en tête de carte : logo de la marque, sinon une icône. */
@Composable
private fun ConfigBadge(spec: Integration) {
    val logo = logoKey(spec.title) ?: logoKey(spec.id)
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (hasLogo(logo)) Logo(logo, spec.title, 26.dp, tip = false)
        else Icon(configIcon(spec.id), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
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
            ConfigBadge(e.spec)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(e.spec.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                val status = when {
                    e.loadError != null -> "Erreur de chargement"
                    o == null -> "Chargement…"
                    else -> listOfNotNull(if (hasEnabled) (if (enabled == true) "Activé" else "Désactivé") else null, e.spec.info?.invoke(o)).joinToString(" · ")
                }
                if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodySmall, color = if (e.loadError != null) cs.error else cs.onSurfaceVariant)
            }
            if (o != null && hasEnabled) IconTag(Icons.Outlined.PowerSettingsNew, if (enabled == true) "Activé" else "Désactivé", if (enabled == true) cs.secondary else cs.outline, size = 22.dp)
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
            leadingIcon = fieldIcon(f)?.let { { Icon(it, contentDescription = null) } },
            keyboardOptions = KeyboardOptions(keyboardType = if (f.url) KeyboardType.Uri else KeyboardType.Text),
        )
        is FNumber -> ClearableField(
            e.values[f.key] as? String ?: "", { v -> e.values[f.key] = v.filter { it.isDigit() } }, Modifier.fillMaxWidth(), label = f.label,
            leadingIcon = fieldIcon(f)?.let { { Icon(it, contentDescription = null) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        is FList -> ClearableField(
            e.values[f.key] as? String ?: "", { e.values[f.key] = it }, Modifier.fillMaxWidth(), label = f.label, placeholder = f.hint,
            supportingText = "Séparées par des virgules.",
            leadingIcon = fieldIcon(f)?.let { { Icon(it, contentDescription = null) } },
        )
        is FSecret -> {
            var show by remember { mutableStateOf(false) }
            ClearableField(
                e.values[f.key] as? String ?: "", { e.values[f.key] = it }, Modifier.fillMaxWidth(), label = f.label,
                placeholder = if (secretIsSet(f, o)) "Enregistré — laisser vide pour le garder" else null,
                leadingIcon = fieldIcon(f)?.let { { Icon(it, contentDescription = null) } },
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
    "indexeurs" to "EBDZ : choix des forums à indexer",
    "indexeurs" to "Canaux Telegram : compte, canaux, indexation",
    "oidc" to "Clé API de Bullarr (affichage et régénération)",
    "backup" to "Sauvegarde et restauration (contient les secrets)",
)

@Composable
private fun WebSettingIcon(label: String) {
    val logo = logoKey(label.substringBefore(' '))
    Box(Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        when {
            hasLogo(logo) -> Logo(logo, label, 22.dp, tip = false)
            "Clé" in label -> Icon(Icons.Outlined.VpnKey, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            else -> Icon(Icons.Outlined.Backup, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun WebSettingsScreen(vm: AppViewModel) {
    val uri = LocalUriHandler.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
        item {
            Text(
                "Ces réglages se modifient dans l'interface web de Bullarr : connexion Telegram par code, fichiers de sauvegarde et clé API ne se prêtent pas au téléphone.",
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
                WebSettingIcon(label)
                Spacer(Modifier.width(16.dp))
                Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

/** Alertes du téléphone (application fermée) : imports, validations, nouveautés. Réglage propre à l'appareil. */
@Composable
fun PhoneAlertsCard(vm: AppViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val cs = MaterialTheme.colorScheme
    val uri = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val push = io.github.gwenvador.bullarr.data.Push
    // Lu ici : tout l'encart se redessine quand l'abonnement évolue (réponse de ntfy, du serveur).
    @Suppress("UNUSED_VARIABLE") val pushState = push.version
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = cs.surfaceContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(cs.surfaceVariant), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.NotificationsActive, contentDescription = null, tint = cs.primary)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Alertes sur ce téléphone", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (push.active(context)) "Instantanées via ntfy (nouveautés : toutes les 30 minutes environ)."
                        else "Même application fermée, vérifiées environ toutes les 30 minutes.",
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                    )
                }
            }
            // Notifications instantanées : le serveur pousse chaque évènement via ntfy (UnifiedPush).
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(cs.surfaceVariant.copy(alpha = 0.5f)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                @Suppress("UNUSED_VARIABLE") val tick = push.version
                val distributors = remember(tick) { push.distributors(context) }
                val enabled = push.enabled(context)
                var testing by remember { mutableStateOf(false) }
                var testResult by remember { mutableStateOf<String?>(null) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Notifications instantanées", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Reçues tout de suite, sans vider la batterie, grâce à l'app gratuite ntfy.",
                            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                        )
                    }
                    if (distributors.isNotEmpty()) Switch(
                        checked = enabled,
                        onCheckedChange = { on ->
                            testResult = null
                            if (on) push.enable(context, push.distributor(context)?.takeIf { d -> distributors.any { it.first == d } } ?: distributors.first().first)
                            else push.disable(context)
                        },
                    )
                }
                when {
                    distributors.isEmpty() -> {
                        Text(
                            "Installez ntfy (F-Droid ou Play Store), ouvrez-le une fois, puis revenez ici.",
                            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                        )
                        OutlinedButton(onClick = { runCatching { uri.openUri(io.github.gwenvador.bullarr.data.Push.NTFY_URL) } }) { Text("Installer ntfy") }
                    }
                    !enabled -> {}
                    else -> {
                        val name = distributors.firstOrNull { it.first == push.distributor(context) }?.second ?: "ntfy"
                        val error = push.error(context)
                        Text(
                            when {
                                error != null -> error
                                push.active(context) -> "Actif via $name : imports et validations arrivent dès qu'ils ont lieu."
                                push.endpoint(context) != null -> "Enregistrement auprès du serveur Bullarr…"
                                else -> "En attente de $name… (ouvrez $name s'il ne répond pas)"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (error != null) cs.error else if (push.active(context)) cs.primary else cs.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (push.active(context)) OutlinedButton(
                                enabled = !testing,
                                onClick = {
                                    val api = vm.api ?: return@OutlinedButton
                                    testing = true; testResult = null
                                    scope.launch {
                                        testResult = try { api.postObject("api/push/test", JSONObject()); "Envoyé : la notification doit arriver dans un instant." }
                                        catch (e: Exception) { e.readable() }
                                        testing = false
                                    }
                                },
                            ) { if (testing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Tester") }
                            if (error != null || (push.endpoint(context) != null && !push.active(context))) OutlinedButton(onClick = {
                                scope.launch { push.syncServer(context.applicationContext) }
                            }) { Text("Réessayer") }
                        }
                        testResult?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant) }
                    }
                }
            }
            io.github.gwenvador.bullarr.data.Alerts.Kind.entries.forEach { kind ->
                var on by remember { mutableStateOf(io.github.gwenvador.bullarr.data.Alerts.isOn(context, kind)) }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(kind.label, style = MaterialTheme.typography.bodyLarge)
                        Text(kind.detail, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                    Switch(checked = on, onCheckedChange = { on = it; io.github.gwenvador.bullarr.data.Alerts.set(context, kind, it) })
                }
            }
            if (android.os.Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) Text(
                "Les notifications de Bullarr sont bloquées : autorisez-les dans les réglages Android de l'application.",
                style = MaterialTheme.typography.bodySmall, color = cs.error,
            )
            Text(
                "Les alertes Telegram ci-dessous sont envoyées par le serveur Bullarr ; celles-ci viennent du téléphone.",
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
            )
        }
    }
}
