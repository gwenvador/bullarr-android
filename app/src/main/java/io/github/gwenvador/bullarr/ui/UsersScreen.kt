package io.github.gwenvador.bullarr.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.gwenvador.bullarr.data.objects
import io.github.gwenvador.bullarr.data.readable
import io.github.gwenvador.bullarr.data.str
import org.json.JSONArray
import org.json.JSONObject

private fun JSONObject.title(): String = str("name") ?: str("username") ?: str("key") ?: "?"
private fun JSONObject.isLocal(): Boolean = str("type") == "local"
private fun JSONObject.isPending(): Boolean = str("status") == "pending"
private fun JSONObject.isAdmin(): Boolean = str("role") == "admin"
private fun JSONObject.allowedLibraries(): List<Int>? =
    if (isNull("allowed_libraries")) null else optJSONArray("allowed_libraries")?.let { a -> (0 until a.length()).map { a.optInt(it) } }
private fun JSONObject.blockedGenres(): List<String> =
    optJSONArray("blocked_genres")?.let { a -> (0 until a.length()).mapNotNull { a.optString(it).takeIf { s -> s.isNotBlank() } } } ?: emptyList()

private fun plural(n: Int, word: String) = "$n $word" + if (n > 1) "s" else ""

private fun accessSummary(u: JSONObject): String {
    val libs = u.allowedLibraries()
    val parts = mutableListOf(if (libs == null) "Toutes les bibliothèques" else plural(libs.size, "bibliothèque"))
    val genres = u.blockedGenres().size
    if (genres > 0) parts += plural(genres, "genre") + " masqué" + if (genres > 1) "s" else ""
    return parts.joinToString(" · ")
}

/**
 * Réglages → Utilisateurs (administrateurs) : comptes locaux et SSO, rôle, accès aux
 * bibliothèques, contrôle parental, avatar ; création, modification et suppression.
 */
@Composable
fun UsersScreen(vm: AppViewModel) {
    val api = vm.api ?: return
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var users by remember { mutableStateOf<List<JSONObject>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<JSONObject?>(null) }
    var creating by remember { mutableStateOf(false) }
    var accessFor by remember { mutableStateOf<JSONObject?>(null) }
    var avatarFor by remember { mutableStateOf<JSONObject?>(null) }
    var deleting by remember { mutableStateOf<JSONObject?>(null) }

    LaunchedEffect(api, tick) {
        try {
            val o = api.getObject("api/auth/users")
            users = o.optJSONArray("users").objects(); error = null
        } catch (e: Exception) { error = e.readable() }
        refreshing = false
        if (vm.libraries.isEmpty()) vm.loadLibrary()
    }
    fun reload() { tick++ }

    PullToRefreshBox(isRefreshing = refreshing, onRefresh = { refreshing = true; reload() }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Utilisateurs", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        users?.let { list ->
                            Text(
                                listOf(plural(list.count { it.isAdmin() && !it.isPending() }, "admin"),
                                    plural(list.count { !it.isAdmin() && !it.isPending() }, "lecteur"))
                                    .plus(list.count { it.isPending() }.takeIf { it > 0 }?.let { "$it en attente" } ?: "")
                                    .filter { it.isNotEmpty() }.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Button(onClick = { creating = true }) {
                        Icon(Icons.Outlined.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp)); Text("Nouveau")
                    }
                }
            }
            error?.let { e -> item { ErrorCard(e, onRetry = { reload() }) } }
            val list = users
            if (list == null && error == null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (list != null) items(list, key = { it.str("key") ?: it.title() }) { u ->
                val key = u.str("key") ?: return@items
                UserCard(
                    vm, u, open = expanded == key,
                    onToggle = { expanded = if (expanded == key) null else key },
                    onEdit = { editing = u }, onAccess = { accessFor = u }, onAvatar = { avatarFor = u }, onDelete = { deleting = u },
                    onApprove = { role ->
                        launchAction(scope, context, "Compte validé", then = { reload() }) {
                            api.postObject("api/auth/users/approve", JSONObject().put("key", key).put("role", role))
                        }
                    },
                )
            }
        }
    }

    if (creating || editing != null) UserEditDialog(
        vm, editing, onDismiss = { creating = false; editing = null },
        onSaved = { creating = false; editing = null; reload() },
    )
    accessFor?.let { u -> AccessDialog(vm, u, onDismiss = { accessFor = null }, onSaved = { accessFor = null; reload() }) }
    avatarFor?.let { u -> UserAvatarDialog(vm, u, onDismiss = { avatarFor = null }, onChanged = { reload() }) }
    deleting?.let { u ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(if (u.isPending()) "Refuser la demande ?" else "Supprimer ce compte ?") },
            text = {
                Text(
                    if (u.isPending()) "La demande d'inscription de ${u.title()} sera refusée."
                    else "Le compte ${u.title()} sera supprimé et ses sessions ouvertes coupées." +
                        if (u.isLocal()) "" else " Il sera recréé (avec le rôle par défaut) s'il se reconnecte par SSO.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val key = u.str("key") ?: return@TextButton
                    deleting = null
                    launchAction(scope, context, if (u.isPending()) "Demande refusée" else "Compte supprimé", then = { reload() }) {
                        api.deleteObject("api/auth/users", JSONObject().put("key", key))
                    }
                }) { Text(if (u.isPending()) "Refuser" else "Supprimer", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Annuler") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UserCard(
    vm: AppViewModel, u: JSONObject, open: Boolean, onToggle: () -> Unit,
    onEdit: () -> Unit, onAccess: () -> Unit, onAvatar: () -> Unit, onDelete: () -> Unit, onApprove: (String) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val me = u.optBoolean("is_me")
    Card(Modifier.fillMaxWidth().animateContentSize()) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(vm.api, u.str("avatar_url"), 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(u.title(), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (me) Tag("vous", cs.surfaceContainerHighest, cs.onSurfaceVariant)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    when {
                        u.isPending() -> Tag("En attente", cs.errorContainer, cs.onErrorContainer)
                        u.isAdmin() -> Tag("Admin", cs.primaryContainer, cs.onPrimaryContainer)
                        else -> Tag("Lecteur", cs.secondaryContainer, cs.onSecondaryContainer)
                    }
                    Tag(if (u.isLocal()) "Local" else "SSO", cs.surfaceContainerHighest, cs.onSurfaceVariant)
                    if (!u.isLocal() && !u.isPending() && u.isNull("role_override")) Tag("rôle auto", cs.surfaceContainerHighest, cs.onSurfaceVariant)
                }
                val detail = listOfNotNull(
                    if (u.isLocal()) u.str("username")?.let { "@$it" } else null,
                    u.str("email"),
                ).joinToString(" · ")
                if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!u.isAdmin() && !u.isPending()) Text(accessSummary(u), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
        }
        if (open) {
            HorizontalDivider()
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val seen = utcToLocal(u.str("last_seen"))
                val first = utcToLocal(u.str("first_seen"))
                Text(
                    listOfNotNull(first?.let { "Créé le $it" }, seen?.let { "vu le $it" }).joinToString(" · ").ifEmpty { "Jamais connecté" },
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                )
                if (!u.isLocal()) {
                    val groups = u.optJSONArray("groups")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()
                    if (groups.isNotEmpty()) Text("Groupes : " + groups.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (u.isPending()) {
                        Button(onClick = { onApprove("reader") }) {
                            Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Valider (lecteur)")
                        }
                        OutlinedButton(onClick = { onApprove("admin") }) { Text("Valider (admin)") }
                        OutlinedButton(onClick = onDelete, colors = ButtonDefaults.outlinedButtonColors(contentColor = cs.error)) {
                            Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Refuser")
                        }
                    } else {
                        OutlinedButton(onClick = onEdit) {
                            Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Modifier")
                        }
                        OutlinedButton(onClick = onAccess) {
                            Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Accès")
                        }
                        OutlinedButton(onClick = onAvatar) {
                            Icon(Icons.Outlined.Face, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Avatar")
                        }
                        if (!me) OutlinedButton(onClick = onDelete, colors = ButtonDefaults.outlinedButtonColors(contentColor = cs.error)) {
                            Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Supprimer")
                        }
                    }
                }
            }
        }
    }
}

/** Choix du rôle : « auto » (rôle calculé par les groupes SSO) n'existe que pour un compte SSO. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RolePicker(role: String?, sso: Boolean, onChange: (String?) -> Unit) {
    Text("Rôle", style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (sso) FilterChip(selected = role == null, onClick = { onChange(null) }, label = { Text("Automatique") })
        FilterChip(selected = role == "admin", onClick = { onChange("admin") }, label = { Text("Administrateur") })
        FilterChip(selected = role == "reader", onClick = { onChange("reader") }, label = { Text("Lecteur") })
    }
    Text(
        when (role) {
            "admin" -> "Accès complet : réglages, ajouts, téléchargements."
            "reader" -> "Lit et demande des BD ; ne voit que les bibliothèques permises."
            else -> "Rôle donné par le groupe administrateur du SSO."
        },
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Création (user = null) ou modification d'un compte. Un compte SSO ne change que de rôle. */
@Composable
private fun UserEditDialog(vm: AppViewModel, user: JSONObject?, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val api = vm.api ?: return
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val creating = user == null
    val local = user?.isLocal() ?: true
    var username by remember { mutableStateOf("") }
    var name by remember { mutableStateOf(user?.str("name").orEmpty()) }
    var email by remember { mutableStateOf(user?.str("email").orEmpty()) }
    var password by remember { mutableStateOf("") }
    var role by remember {
        mutableStateOf(if (user == null) "reader" else if (local) user.str("role") else user.str("role_override")?.takeIf { it == "admin" || it == "reader" })
    }
    var busy by remember { mutableStateOf(false) }
    val valid = !busy && if (creating) username.isNotBlank() && password.length >= 8 else !local || name.isNotBlank()
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (creating) "Nouveau compte local" else user!!.title()) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (creating) FormField(username, { username = it.trim() }, Modifier.fillMaxWidth(), label = "Identifiant")
                if (local) {
                    FormField(name, { name = it }, Modifier.fillMaxWidth(), label = "Nom", placeholder = if (creating) "facultatif" else null)
                    FormField(email, { email = it }, Modifier.fillMaxWidth(), label = "E-mail", placeholder = "facultatif",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                    FormField(password, { password = it }, Modifier.fillMaxWidth(),
                        label = if (creating) "Mot de passe" else "Nouveau mot de passe", placeholder = if (creating) "8 caractères minimum" else "laisser vide pour le garder",
                        password = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                    if (password.isNotEmpty() && password.length < 8) Text("8 caractères minimum.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                } else Text("Nom, e-mail et mot de passe se gèrent chez le fournisseur SSO.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (user?.optBoolean("is_me") != true) RolePicker(role, sso = !local, onChange = { role = it })
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = valid && (password.isEmpty() || password.length >= 8), onClick = {
                busy = true
                launchAction(scope, context, if (creating) "Compte créé" else "Compte modifié", then = { busy = false; onSaved() }) {
                    if (creating) {
                        api.postObject("api/auth/users", JSONObject().put("username", username).put("password", password)
                            .put("name", name.trim()).put("email", email.trim()).put("role", role ?: "reader"))
                    } else {
                        val body = JSONObject().put("key", user!!.str("key"))
                        if (user.optBoolean("is_me") != true) body.put("role", role ?: JSONObject.NULL)
                        if (local) {
                            body.put("name", name.trim()).put("email", email.trim())
                            if (password.isNotEmpty()) body.put("password", password)
                        }
                        api.putObject("api/auth/users", body)
                    }
                }
            }) { Text(if (creating) "Créer" else "Enregistrer") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Annuler") } },
    )
}

/** Bibliothèques permises et genres Bédéthèque masqués (contrôle parental). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccessDialog(vm: AppViewModel, user: JSONObject, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val api = vm.api ?: return
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val libraries = vm.libraries
    val initial = user.allowedLibraries()
    val allowed = remember { mutableStateListOf<Int>().apply { addAll(initial ?: libraries.map { it.id }) } }
    val blocked = remember { mutableStateListOf<String>().apply { addAll(user.blockedGenres()) } }
    var genres by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
    var preset by remember { mutableStateOf<List<String>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(api) {
        runCatching { api.getObject("api/auth/genres") }.onSuccess { o ->
            genres = o.optJSONArray("genres").objects().mapNotNull { g -> g.str("name")?.let { it to g.optInt("count") } }
            preset = o.optJSONArray("preset")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()
        }
    }
    fun isBlocked(n: String) = blocked.any { it.equals(n, ignoreCase = true) }
    val names = remember(genres, preset) {
        (genres.map { it.first } + preset + user.blockedGenres()).distinctBy { it.lowercase() }
            .sortedWith(compareBy<String>({ g -> user.blockedGenres().none { it.equals(g, ignoreCase = true) } }, { it.lowercase() }))
    }
    val counts = genres.associate { it.first.lowercase() to it.second }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Accès de ${user.title()}") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (user.isAdmin()) Text("Ce compte est administrateur : ces limites ne s'appliquent qu'aux lecteurs.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                Text("Bibliothèques", style = MaterialTheme.typography.labelLarge)
                libraries.forEach { lib ->
                    Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable {
                        if (lib.id in allowed) allowed.remove(lib.id) else allowed.add(lib.id)
                    }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = lib.id in allowed, onCheckedChange = { if (it) allowed.add(lib.id) else allowed.remove(lib.id) })
                        Text(lib.name, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (allowed.isEmpty()) Text("Aucune bibliothèque : ce lecteur ne verra rien.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text("Contrôle parental : genres masqués", style = MaterialTheme.typography.labelLarge)
                Text("Les séries de ces genres (Bédéthèque) n'apparaissent pas pour ce lecteur.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled = preset.isNotEmpty(), onClick = { preset.forEach { p -> if (!isBlocked(p)) blocked.add(p) } }) { Text("Préréglage enfant") }
                    TextButton(enabled = blocked.isNotEmpty(), onClick = { blocked.clear() }) { Text("Aucun") }
                }
                if (names.isEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth())
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    names.forEach { n ->
                        val on = isBlocked(n)
                        FilterChip(
                            selected = on,
                            onClick = { if (on) blocked.removeAll { it.equals(n, ignoreCase = true) } else blocked.add(n) },
                            label = { Text(counts[n.lowercase()]?.let { "$n · $it" } ?: n) },
                        )
                    }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && libraries.isNotEmpty(), onClick = {
                busy = true
                launchAction(scope, context, "Accès enregistrés", then = { busy = false; onSaved() }) {
                    val all = libraries.all { it.id in allowed }
                    api.putObject("api/auth/users/access", JSONObject().put("key", user.str("key"))
                        .put("allowed_libraries", if (all) JSONObject.NULL else JSONArray(allowed.sorted()))
                        .put("blocked_genres", JSONArray(blocked.toList())))
                }
            }) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Annuler") } },
    )
}

/** Avatar d'un autre compte : avatars proposés, photo de la galerie ou avatar par défaut. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UserAvatarDialog(vm: AppViewModel, user: JSONObject, onDismiss: () -> Unit, onChanged: () -> Unit) {
    val api = vm.api ?: return
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val key = user.str("key") ?: return
    var presets by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var current by remember { mutableStateOf(user.str("avatar_url")) }
    var kind by remember { mutableStateOf(user.str("avatar_kind")) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(api) { runCatching { presets = api.getObject("api/auth/avatar-presets").optJSONArray("presets").objects() } }
    fun act(ok: String, block: suspend () -> JSONObject) {
        busy = true
        launchAction(scope, context, ok, then = { busy = false; onChanged() }) {
            val res = block()
            current = res.str("avatar_url"); kind = res.str("avatar_kind")
            if (user.optBoolean("is_me")) vm.avatarUrl = current
        }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val resolver = context.contentResolver
        act("Photo enregistrée") {
            val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { resolver.openInputStream(uri)?.use { it.readBytes() } }
                ?: throw io.github.gwenvador.bullarr.data.ApiException("Image illisible.")
            val mime = resolver.getType(uri) ?: "image/jpeg"
            api.postMultipart("api/auth/users/avatar", "avatar", "avatar." + mime.substringAfter('/'), bytes, mime, mapOf("key" to key))
        }
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(api, current, 40.dp); Spacer(Modifier.width(12.dp)); Text(user.title(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    presets.forEach { pr ->
                        val url = pr.str("url")
                        val chosen = url != null && url == current && kind != "photo"
                        Avatar(
                            api, url, 48.dp,
                            Modifier.border(if (chosen) 3.dp else 0.dp, if (chosen) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape)
                                .clip(CircleShape).clickable(enabled = !busy) {
                                    val n = pr.str("name") ?: return@clickable
                                    act("Avatar changé") { api.putObject("api/auth/users/avatar", JSONObject().put("key", key).put("preset", n)) }
                                },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled = !busy, onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Photo")
                    }
                    if (kind != "default") OutlinedButton(enabled = !busy, onClick = {
                        act("Avatar par défaut") { api.deleteObject("api/auth/users/avatar", JSONObject().put("key", key)) }
                    }) {
                        Icon(Icons.Outlined.Restore, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Par défaut")
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Fermer") } },
    )
}
