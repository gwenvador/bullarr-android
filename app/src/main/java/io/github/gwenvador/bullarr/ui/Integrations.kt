package io.github.gwenvador.bullarr.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.gwenvador.bullarr.data.readable
import io.github.gwenvador.bullarr.data.str
import org.json.JSONObject

/**
 * Réglages → Intégrations (lecteurs) : relier SON compte Komga. Le serveur se connecte une fois
 * avec l'identifiant et le mot de passe, crée une clé API à son nom et ne garde que cette clé ;
 * l'avancement de lecture affiché et envoyé est alors celui de ce compte (api/komga/me).
 */
@Composable
fun IntegrationsScreen(vm: AppViewModel) {
    val api = vm.api ?: return
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var state by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var useKey by remember { mutableStateOf(false) }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var askUnlink by remember { mutableStateOf(false) }

    LaunchedEffect(api) {
        try { state = api.getObject("api/komga/me") } catch (e: Exception) { error = e.readable() }
    }
    fun run(ok: String, block: suspend () -> JSONObject) {
        busy = true
        launchAction(scope, context, ok, then = { busy = false }) { state = block(); pass = ""; key = "" }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        error?.let { e -> item { ErrorCard(e) } }
        val s = state ?: return@LazyColumn
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Logo("komga", "Komga", 32.dp, tip = false)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Komga", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                when {
                                    !s.optBoolean("available") -> "Non disponible sur ce serveur"
                                    s.optBoolean("linked") -> "Relié : ${s.str("komga_user") ?: "compte Komga"}"
                                    else -> "Non relié · avancement enregistré dans Bullarr"
                                },
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (s.optBoolean("linked")) Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    when {
                        !s.optBoolean("available") -> Text(
                            "Komga n'est pas configuré sur ce serveur : votre avancement de lecture est enregistré par Bullarr, pour votre compte uniquement.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        s.optBoolean("linked") -> {
                            Text(
                                "Votre avancement de lecture est celui de votre compte Komga : ce que vous lisez dans Bullarr y est enregistré, " +
                                    "et ce que vous lisez dans Komga (ou Mihon, Tachiyomi…) apparaît ici.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            s.optJSONObject("stats")?.let { st ->
                                Text("${st.optInt("read")} lu(s) · ${st.optInt("reading")} en cours", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            s.str("last_error")?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(enabled = !busy, onClick = {
                                    run("Lecture synchronisée") { api.postObject("api/komga/read-progress/sync", JSONObject()); api.getObject("api/komga/me") }
                                }) {
                                    Icon(Icons.Outlined.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp)); Text("Synchroniser")
                                }
                                OutlinedButton(
                                    enabled = !busy, onClick = { askUnlink = true },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                ) {
                                    Icon(Icons.Outlined.LinkOff, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp)); Text("Délier")
                                }
                            }
                        }
                        else -> {
                            Text(
                                "Reliez votre compte Komga pour y retrouver et y enregistrer votre avancement de lecture. Sans compte relié, il est enregistré par Bullarr, pour votre compte uniquement.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            s.str("komga_url")?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            if (useKey) {
                                FormField(key, { key = it }, Modifier.fillMaxWidth(), label = "Clé API Komga", password = true)
                            } else {
                                FormField(user, { user = it }, Modifier.fillMaxWidth(), label = "E-mail Komga",
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                                FormField(pass, { pass = it }, Modifier.fillMaxWidth(), label = "Mot de passe Komga", password = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                                Text("Le mot de passe n'est pas enregistré : Bullarr crée une clé API à votre nom dans Komga.",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Button(
                                enabled = !busy && (if (useKey) key.isNotBlank() else user.isNotBlank() && pass.isNotEmpty()),
                                onClick = {
                                    val body = if (useKey) JSONObject().put("api_key", key.trim())
                                    else JSONObject().put("username", user.trim()).put("password", pass)
                                    run("Compte Komga relié") { api.putObject("api/komga/me", body) }
                                },
                            ) { Text("Se connecter") }
                            TextButton(onClick = { useKey = !useKey }) {
                                Text(if (useKey) "Utiliser mon e-mail et mon mot de passe" else "Utiliser une clé API")
                            }
                        }
                    }
                }
            }
        }
    }
    if (askUnlink) ConfirmDialog(
        "Délier Komga ?", "Bullarr n'affichera plus votre avancement Komga. Votre compte Komga n'est pas modifié.", "Délier",
        onConfirm = { askUnlink = false; run("Compte Komga délié") { api.deleteObject("api/komga/me") } },
        onDismiss = { askUnlink = false },
    )
}
