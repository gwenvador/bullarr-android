package io.github.gwenvador.bullarr.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.gwenvador.bullarr.data.objects
import io.github.gwenvador.bullarr.data.readable
import io.github.gwenvador.bullarr.data.str
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date

/** Un droit possible d'une clé API (api/auth/profile/api-keys → available). */
private data class Scope(val code: String, val label: String, val help: String)

/**
 * Réglages → Profil → Clés API, comme la carte « Clés API » de Mon profil sur le web : une clé par
 * application (Mihon, script, domotique…), avec ses droits. Une clé agit avec les droits du compte
 * (un lecteur reste limité à ses bibliothèques), réduits aux droits cochés.
 * Le serveur refuse de gérer les clés quand l'app est elle-même connectée par clé API.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ApiKeysCard(vm: AppViewModel) {
    val api = vm.api ?: return
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var keys by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var available by remember { mutableStateOf<List<Scope>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var shown by remember { mutableStateOf(setOf<Int>()) }
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<JSONObject?>(null) }
    var deleting by remember { mutableStateOf<JSONObject?>(null) }
    var created by remember { mutableStateOf<String?>(null) }

    fun apply(o: JSONObject) {
        keys = o.optJSONArray("keys").objects()
        available = o.optJSONArray("available").objects().map { Scope(it.str("code").orEmpty(), it.str("label").orEmpty(), it.str("help").orEmpty()) }
        loaded = true
    }
    LaunchedEffect(api) {
        try { apply(api.getObject("api/auth/profile/api-keys")) } catch (e: Exception) { error = e.readable() }
    }
    fun run(ok: String, block: suspend () -> JSONObject) {
        busy = true
        launchAction(scope, context, ok, then = { busy = false }) { apply(block()) }
    }
    fun copy(value: String) {
        val cm = context.getSystemService(android.content.ClipboardManager::class.java)
        val clip = android.content.ClipData.newPlainText("Clé API Bullarr", value)
        clip.description.extras = android.os.PersistableBundle().apply { putBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE, true) }
        cm.setPrimaryClip(clip)
        android.widget.Toast.makeText(context, "Clé copiée", android.widget.Toast.LENGTH_SHORT).show()
    }
    fun summary(scopes: JSONArray?): String {
        val codes = (0 until (scopes?.length() ?: 0)).map { scopes!!.optString(it) }
        if (codes.isEmpty() || "full" in codes) return "Accès complet"
        return codes.mapNotNull { c -> available.firstOrNull { it.code == c }?.label ?: c }.joinToString(", ")
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Clés API", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (loaded) TextButton(enabled = !busy, onClick = { creating = true }) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp)); Text("Nouvelle clé")
                }
            }
            Text(
                "Une clé par application (Mihon, script, domotique…), envoyée dans l'en-tête X-Api-Key. Elle agit avec les droits de votre compte, réduits à ceux que vous choisissez.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { e ->
                Text(
                    if (e.contains("clé API")) "L'app est connectée par clé API : connectez-vous avec un compte pour gérer vos clés." else e,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                )
            }
            if (loaded && keys.isEmpty()) Text("Aucune clé pour l'instant.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            keys.forEach { k ->
                val id = k.optInt("id")
                val value = k.str("api_key").orEmpty()
                val visible = id in shown
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(k.str("name") ?: "Clé API", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull(
                                    epochDate(k.optLong("created_at"))?.let { "créée le $it" },
                                    epochDate(k.optLong("last_used_at"))?.let { "utilisée le $it" } ?: "jamais utilisée",
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { shown = if (visible) shown - id else shown + id }) {
                            Icon(if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, contentDescription = if (visible) "Masquer la clé" else "Afficher la clé")
                        }
                        IconButton(enabled = value.isNotEmpty(), onClick = { copy(value) }) { Icon(Icons.Outlined.ContentCopy, contentDescription = "Copier la clé") }
                        IconButton(enabled = !busy, onClick = { deleting = k }) { Icon(Icons.Outlined.Delete, contentDescription = "Supprimer la clé", tint = MaterialTheme.colorScheme.error) }
                    }
                    if (visible) Text(value, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    TextButton(enabled = !busy, onClick = { editing = k }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                        Text("Droits : ${summary(k.optJSONArray("scopes"))}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }

    if (creating) ScopesDialog(
        title = "Nouvelle clé", available = available, initialName = "", initial = setOf("full"), withName = true,
        onDismiss = { creating = false },
        onConfirm = { name, codes ->
            creating = false
            busy = true
            launchAction(scope, context, "Clé créée", then = { busy = false }) {
                val res = api.postObject("api/auth/profile/api-keys", JSONObject().put("name", name).put("scopes", JSONArray(codes.toList())))
                apply(res)
                created = res.str("api_key")
            }
        },
    )
    editing?.let { k ->
        val arr = k.optJSONArray("scopes")
        val codes = (0 until (arr?.length() ?: 0)).map { arr!!.optString(it) }.toSet().ifEmpty { setOf("full") }
        ScopesDialog(
            title = "Droits de « ${k.str("name") ?: "Clé API"} »", available = available, initialName = k.str("name").orEmpty(), initial = codes, withName = true,
            onDismiss = { editing = null },
            onConfirm = { name, chosen ->
                editing = null
                run("Clé modifiée") { api.putObject("api/auth/profile/api-keys/${k.optInt("id")}", JSONObject().put("name", name).put("scopes", JSONArray(chosen.toList()))) }
            },
        )
    }
    deleting?.let { k ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Supprimer la clé ?") },
            text = { Text("« ${k.str("name") ?: "Clé API"} » ne fonctionnera plus : les applications qui l'utilisent devront en recevoir une nouvelle.") },
            confirmButton = { TextButton(onClick = { deleting = null; run("Clé supprimée") { api.deleteObject("api/auth/profile/api-keys/${k.optInt("id")}") } }) { Text("Supprimer") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Annuler") } },
        )
    }
    created?.let { value ->
        AlertDialog(
            onDismissRequest = { created = null },
            title = { Text("Clé créée") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Copiez-la dans l'application qui doit l'utiliser. Vous la retrouverez aussi dans cette liste.")
                    Text(value, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { copy(value); created = null }) { Text("Copier") } },
            dismissButton = { TextButton(onClick = { created = null }) { Text("Fermer") } },
        )
    }
}

/** Nom + droits d'une clé. « Accès complet » exclut les autres droits, et inversement. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScopesDialog(
    title: String, available: List<Scope>, initialName: String, initial: Set<String>, withName: Boolean,
    onDismiss: () -> Unit, onConfirm: (String, Set<String>) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var chosen by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (withName) FormField(name, { name = it.take(60) }, Modifier.fillMaxWidth(), label = "Nom", placeholder = "ex. Mihon")
                Text("Droits", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    available.forEach { s ->
                        FilterChip(
                            selected = s.code in chosen,
                            onClick = {
                                chosen = when {
                                    s.code == "full" -> setOf("full")
                                    s.code in chosen -> (chosen - s.code).ifEmpty { setOf("full") }
                                    else -> chosen - "full" + s.code
                                }
                            },
                            label = { Text(s.label) },
                        )
                    }
                }
                available.filter { it.code in chosen }.forEach { s ->
                    Text("${s.label} : ${s.help}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onConfirm(name.trim(), chosen) }) { Text("Enregistrer") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

private fun epochDate(seconds: Long): String? =
    if (seconds <= 0) null else DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(seconds * 1000))
