package io.github.gwenvador.bullarr.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.gwenvador.bullarr.data.int
import io.github.gwenvador.bullarr.data.objects
import io.github.gwenvador.bullarr.data.readable
import io.github.gwenvador.bullarr.data.str
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Date SQLite du serveur (UTC, « yyyy-MM-dd HH:mm:ss ») en heure locale « dd/MM/yyyy HH:mm ». */
fun utcToLocal(s: String?): String? {
    s ?: return null
    return runCatching {
        java.time.LocalDateTime.parse(s.trim().replace(' ', 'T').take(19))
            .atZone(java.time.ZoneOffset.UTC).withZoneSameInstant(java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
    }.getOrElse { s.take(16) }
}

/** Libellé et couleur d'un statut de demande de BD. */
@Composable
private fun requestStatus(status: String?): Pair<String, Color> {
    val cs = MaterialTheme.colorScheme
    return when (status) {
        "pending" -> "En attente" to cs.tertiary
        "adding" -> "Ajout en cours" to cs.primary
        "added" -> "Ajoutée" to cs.primary
        "already" -> "Déjà présente" to cs.primary
        "rejected" -> "Refusée" to cs.error
        "error" -> "Erreur" to cs.error
        "cancelled" -> "Annulée" to cs.onSurfaceVariant
        else -> (status ?: "?") to cs.onSurfaceVariant
    }
}

/** Une demande de BD : couverture (si la série existe), titre, statut, détails ; [actions] à droite. */
@Composable
private fun RequestRow(vm: AppViewModel, r: JSONObject, showUser: Boolean, onOpen: ((Int, String) -> Unit)?, actions: @Composable () -> Unit = {}) {
    val cs = MaterialTheme.colorScheme
    val title = r.str("title") ?: r.str("url") ?: "(sans titre)"
    val sid = r.int("series_id")
    val (label, color) = requestStatus(r.str("status"))
    Row(
        Modifier.fillMaxWidth().clickable(enabled = sid != null && onOpen != null) { sid?.let { onOpen?.invoke(it, title) } }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(width = 44.dp, height = 60.dp).clip(RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
            val cover = r.str("cover_url")
            if (cover != null) Cover(vm.coverUrl(cover), 44.dp, Modifier.fillMaxSize())
            else Box(Modifier.fillMaxSize().border(1.dp, cs.outlineVariant, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.LibraryAdd, contentDescription = null, tint = cs.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(label, style = MaterialTheme.typography.labelMedium, color = color)
            val meta = listOfNotNull(
                if (showUser) r.str("user_name") else null,
                utcToLocal(r.str("created_at")),
                r.str("library_name"),
                r.int("total")?.takeIf { sid != null }?.let { "${r.int("owned") ?: 0}/$it tomes" },
            ).joinToString(" · ")
            if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            r.str("error")?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = if (r.str("status") == "rejected") cs.onSurfaceVariant else cs.error, maxLines = 3, overflow = TextOverflow.Ellipsis) }
        }
        actions()
    }
}

/** Rubrique « Demandes » d'un lecteur : ses demandes de BD, l'annulation de celles en attente, et « Demander une BD ». */
@Composable
fun RequestsScreen(vm: AppViewModel, onOpenSeries: (Int, String) -> Unit) {
    val api = vm.api ?: return
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var list by remember { mutableStateOf<List<JSONObject>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var asking by remember { mutableStateOf(false) }
    var autoApprove by remember { mutableStateOf(false) }
    suspend fun load() {
        loading = true; error = null
        try {
            val o = api.getObject("api/requests")
            list = o.optJSONArray("requests").objects(); autoApprove = o.optBoolean("auto_approve")
        } catch (e: Exception) { error = e.readable() }
        loading = false
    }
    LaunchedEffect(api) { load() }
    // Un ajout validé se fait en arrière-plan : on relit tant qu'une demande est « en cours ».
    LaunchedEffect(list) {
        if (list.orEmpty().any { it.str("status") == "adding" }) { kotlinx.coroutines.delay(4000); load() }
    }
    PullToRefreshBox(isRefreshing = loading && list != null, onRefresh = { scope.launch { load() } }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            item {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { asking = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.LibraryAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp)); Text("Demander une BD")
                    }
                    Text(
                        if (autoApprove) "Vos demandes sont ajoutées tout de suite à la bibliothèque."
                        else "Un administrateur valide chaque demande avant l'ajout à la bibliothèque.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (loading && list == null) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp)) }
            error?.let { e -> item { ErrorCard(e, Modifier.padding(16.dp), onRetry = { scope.launch { load() } }) } }
            if (list?.isEmpty() == true) item { EmptyState("Aucune demande", "Cherchez une série sur Bédéthèque et demandez-la.") }
            items(list.orEmpty(), key = { it.int("id") ?: it.hashCode() }) { r ->
                RequestRow(vm, r, showUser = false, onOpen = onOpenSeries) {
                    if (r.str("status") == "pending") IconButton(onClick = {
                        val id = r.int("id") ?: return@IconButton
                        launchAction(scope, context, "Demande annulée", then = { scope.launch { load() } }) { api.deleteObject("api/requests/$id") }
                    }) { Icon(Icons.Outlined.Close, contentDescription = "Annuler la demande") }
                }
                HorizontalDivider(Modifier.padding(start = 68.dp), color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
    if (asking) SeriesAddDialog(vm, onDismiss = { asking = false }, onRequested = { scope.launch { load() } }, onAdded = { _, _ -> })
}

/**
 * Haut de Validation (administrateurs) : comptes en attente et demandes de BD des lecteurs.
 * La validation d'une demande rend la main tout de suite (statut « adding ») ; l'ajout se fait côté serveur.
 */
@Composable
fun AdminRequestsBlock(vm: AppViewModel, onOpenSeries: (Int, String) -> Unit) {
    val api = vm.api ?: return
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var requests by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var accounts by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var autoApprove by remember { mutableStateOf<Boolean?>(null) }
    var approving by remember { mutableStateOf<JSONObject?>(null) }
    var rejecting by remember { mutableStateOf<JSONObject?>(null) }
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(api, tick) {
        runCatching { api.getObject("api/requests", mapOf("status" to "pending")) }.onSuccess {
            requests = it.optJSONArray("requests").objects(); autoApprove = it.optBoolean("auto_approve")
        }
        runCatching { api.getObject("api/auth/users") }.onSuccess { o ->
            accounts = o.optJSONArray("users").objects().filter { it.str("status") == "pending" }
        }
        vm.requestCount = requests.size + accounts.size
        if (vm.libraries.isEmpty()) vm.loadLibrary()
    }
    fun reload() { tick++ }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (accounts.isNotEmpty()) Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(vertical = 8.dp)) {
                Text("Comptes en attente · ${accounts.size}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                accounts.forEach { u ->
                    val key = u.str("key") ?: return@forEach
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(api, u.str("avatar_url"), 36.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(u.str("name") ?: u.str("username") ?: key, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(listOfNotNull(u.str("email"), utcToLocal(u.str("first_seen"))).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = {
                            launchAction(scope, context, "Demande d'inscription refusée", then = { reload() }) { api.deleteObject("api/auth/users", JSONObject().put("key", key)) }
                        }) { Icon(Icons.Outlined.Close, contentDescription = "Refuser") }
                        IconButton(onClick = {
                            launchAction(scope, context, "Compte validé (lecteur)", then = { reload() }) {
                                api.postObject("api/auth/users/approve", JSONObject().put("key", key).put("role", "reader"))
                            }
                        }) { Icon(Icons.Outlined.Check, contentDescription = "Valider comme lecteur", tint = MaterialTheme.colorScheme.primary) }
                    }
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(vertical = 8.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Demandes de BD" + if (requests.isNotEmpty()) " · ${requests.size}" else "", style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("Auto", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                    Switch(
                        checked = autoApprove == true, enabled = autoApprove != null,
                        modifier = Modifier.scale(0.7f).height(28.dp),
                        onCheckedChange = { on ->
                            autoApprove = on
                            launchAction(scope, context, if (on) "Validation automatique activée" else "Validation automatique désactivée") {
                                api.putObject("api/requests/settings", JSONObject().put("auto_approve", on))
                            }
                        },
                    )
                }
                if (requests.isEmpty()) Text(
                    if (autoApprove == true) "Les demandes des lecteurs sont ajoutées automatiquement." else "Aucune demande en attente.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
                requests.forEach { r ->
                    RequestRow(vm, r, showUser = true, onOpen = onOpenSeries) {
                        IconButton(onClick = { rejecting = r }) { Icon(Icons.Outlined.Close, contentDescription = "Refuser") }
                        IconButton(onClick = { approving = r }) { Icon(Icons.Outlined.Check, contentDescription = "Valider", tint = MaterialTheme.colorScheme.primary) }
                    }
                }
            }
        }
    }

    approving?.let { r ->
        val id = r.int("id") ?: return@let
        var lib by remember(r) { mutableStateOf(r.int("library_id") ?: vm.selectedLibrary?.id ?: vm.libraries.firstOrNull()?.id) }
        AlertDialog(
            onDismissRequest = { approving = null },
            title = { Text("Valider la demande") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("« ${r.str("title") ?: r.str("url")} », demandée par ${r.str("user_name") ?: "?"}.")
                    if (vm.libraries.size > 1) {
                        Text("Bibliothèque", style = MaterialTheme.typography.labelLarge)
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            vm.libraries.forEach { l -> FilterChip(selected = lib == l.id, onClick = { lib = l.id }, label = { Text(l.name) }) }
                        }
                    }
                    Text("La série est ajoutée en arrière-plan depuis Bédéthèque, puis ses tomes sont recherchés.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    approving = null
                    launchAction(scope, context, "Demande validée : ajout en cours", then = { reload() }) {
                        api.postObject("api/requests/$id/approve", JSONObject().apply { lib?.let { put("library_id", it) } })
                    }
                }) { Text("Valider") }
            },
            dismissButton = { TextButton(onClick = { approving = null }) { Text("Annuler") } },
        )
    }
    rejecting?.let { r ->
        val id = r.int("id") ?: return@let
        var reason by remember(r) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { rejecting = null },
            title = { Text("Refuser la demande") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("« ${r.str("title") ?: r.str("url")} », demandée par ${r.str("user_name") ?: "?"}.")
                    OutlinedTextField(value = reason, onValueChange = { reason = it.take(300) }, label = { Text("Motif (facultatif)") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    rejecting = null
                    launchAction(scope, context, "Demande refusée", then = { reload() }) {
                        api.postObject("api/requests/$id/reject", JSONObject().put("reason", reason.trim()))
                    }
                }) { Text("Refuser", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { rejecting = null }) { Text("Annuler") } },
        )
    }
}

/** Réglages → Profil : avatar (proposés, photo, par défaut), puis nom, e-mail et mot de passe des comptes locaux. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(vm: AppViewModel) {
    val api = vm.api ?: return
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var profile by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var presets by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var current by remember { mutableStateOf("") }
    var newPass by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var avatarsOpen by remember { mutableStateOf(false) }

    fun applyProfile(p: JSONObject) {
        profile = p; name = p.str("name").orEmpty(); email = p.str("email").orEmpty()
        vm.avatarUrl = p.str("avatar_url"); vm.profileName = p.str("name") ?: vm.profileName
    }
    LaunchedEffect(api) {
        try { applyProfile(api.getObject("api/auth/profile").getJSONObject("profile")) } catch (e: Exception) { error = e.readable() }
        runCatching { presets = api.getObject("api/auth/avatar-presets").optJSONArray("presets").objects() }
    }
    fun avatarAction(ok: String, block: suspend () -> JSONObject) {
        busy = true
        launchAction(scope, context, ok, then = { busy = false }) {
            val res = block()
            vm.avatarUrl = res.str("avatar_url")
            profile = profile?.put("avatar_url", res.str("avatar_url"))?.put("avatar_kind", res.str("avatar_kind"))
        }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val resolver = context.contentResolver
        avatarAction("Photo de profil enregistrée") {
            val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { resolver.openInputStream(uri)?.use { it.readBytes() } }
                ?: throw io.github.gwenvador.bullarr.data.ApiException("Image illisible.")
            val mime = resolver.getType(uri) ?: "image/jpeg"
            api.postMultipart("api/auth/profile/avatar", "avatar", "avatar." + mime.substringAfter('/'), bytes, mime)
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        error?.let { e ->
            item {
                // Clé API : aucun compte derrière, donc pas de profil.
                if (e.contains("Aucun compte")) EmptyState("Pas de profil", "L'app est connectée par clé API : connectez-vous avec un compte pour avoir un profil.")
                else ErrorCard(e)
            }
            item { LogoutCard(vm) }
        }
        val p = profile ?: return@LazyColumn
        item {
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(api, vm.avatarUrl ?: p.str("avatar_url"), 72.dp)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.str("name") ?: p.str("username") ?: "?", style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(if (p.str("role") == "reader") "Lecteur" else "Administrateur", if (p.str("type") == "local") "compte local" else "SSO").joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        p.str("email")?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
            }
        }
        item {
            // Repliée par défaut : un toucher sur l'en-tête déplie les avatars proposés.
            Card(Modifier.fillMaxWidth().animateContentSize()) {
                Row(
                    Modifier.fillMaxWidth().clickable { avatarsOpen = !avatarsOpen }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Avatar", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Icon(
                        if (avatarsOpen) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = if (avatarsOpen) "Replier" else "Déplier", tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (avatarsOpen || busy) Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        presets.forEach { pr ->
                            val url = pr.str("url")
                            val chosen = url != null && url == (vm.avatarUrl ?: p.str("avatar_url")) && p.str("avatar_kind") != "photo"
                            Avatar(
                                api, url, 52.dp,
                                Modifier.border(if (chosen) 3.dp else 0.dp, if (chosen) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape)
                                    .clip(CircleShape).clickable(enabled = !busy) {
                                        val n = pr.str("name") ?: return@clickable
                                        avatarAction("Avatar changé") { api.putObject("api/auth/profile/avatar", JSONObject().put("preset", n)) }
                                    },
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(enabled = !busy, onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                            Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp)); Text("Photo")
                        }
                        if (p.str("avatar_kind") != "default") OutlinedButton(enabled = !busy, onClick = {
                            avatarAction("Avatar par défaut") { api.deleteObject("api/auth/profile/avatar") }
                        }) {
                            Icon(Icons.Outlined.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp)); Text("Par défaut")
                        }
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Compte", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    if (p.optBoolean("can_edit_identity")) {
                        FormField(name, { name = it }, Modifier.fillMaxWidth(), label = "Nom")
                        FormField(email, { email = it }, Modifier.fillMaxWidth(), label = "E-mail", placeholder = "facultatif",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                        Text("Changer le mot de passe (facultatif)", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
                        listOf(
                            Triple("Mot de passe actuel", current) { v: String -> current = v },
                            Triple("Nouveau mot de passe", newPass) { v: String -> newPass = v },
                            Triple("Confirmer", confirm) { v: String -> confirm = v },
                        ).forEach { (label, value, set) ->
                            FormField(value, set, Modifier.fillMaxWidth(), label = label, password = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                        }
                        Button(
                            enabled = !busy && name.isNotBlank() && (newPass.isEmpty() || (newPass == confirm && current.isNotEmpty())),
                            onClick = {
                                busy = true
                                launchAction(scope, context, "Profil enregistré", then = { busy = false }) {
                                    val body = JSONObject().put("name", name.trim()).put("email", email.trim())
                                    if (newPass.isNotEmpty()) body.put("current_password", current).put("new_password", newPass).put("new_password_confirm", confirm)
                                    val res = api.putObject("api/auth/profile", body)
                                    res.optJSONObject("profile")?.let { applyProfile(it) }
                                    current = ""; newPass = ""; confirm = ""
                                }
                            },
                        ) { Text("Enregistrer") }
                        if (newPass.isNotEmpty() && newPass != confirm) Text("Les deux mots de passe ne correspondent pas.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    } else Text(
                        "Votre nom, votre e-mail et votre mot de passe se changent chez votre fournisseur SSO.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    utcToLocal(p.str("first_seen"))?.let { Text("Compte créé le $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
        item { LogoutCard(vm) }
    }
}

/** Déconnexion, avec confirmation : l'app revient à l'écran de connexion. */
@Composable
private fun LogoutCard(vm: AppViewModel) {
    var ask by remember { mutableStateOf(false) }
    OutlinedButton(
        onClick = { ask = true }, modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
    ) {
        Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp)); Text("Se déconnecter")
    }
    if (ask) AlertDialog(
        onDismissRequest = { ask = false },
        title = { Text("Se déconnecter ?") },
        text = { Text("Vous reviendrez à l'écran de connexion.") },
        confirmButton = { TextButton(onClick = { ask = false; vm.logout() }) { Text("Se déconnecter") } },
        dismissButton = { TextButton(onClick = { ask = false }) { Text("Annuler") } },
    )
}
