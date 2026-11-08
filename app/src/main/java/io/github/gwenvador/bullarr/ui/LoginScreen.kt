package io.github.gwenvador.bullarr.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import io.github.gwenvador.bullarr.ui.Icon
import io.github.gwenvador.bullarr.ui.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import io.github.gwenvador.bullarr.R
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.gwenvador.bullarr.data.ApiException
import io.github.gwenvador.bullarr.data.BullarrApi
import io.github.gwenvador.bullarr.data.SessionAuth
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.ExperimentalMaterial3Api
import io.github.gwenvador.bullarr.data.readable
import io.github.gwenvador.bullarr.data.str
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(vm: AppViewModel, firstRun: Boolean, onSaved: () -> Unit, embedded: Boolean = false) {
    var url by remember { mutableStateOf(vm.settings.serverUrl.ifBlank { vm.settings.lastUrl }) }
    var key by remember { mutableStateOf(vm.settings.apiKey) }
    var mode by remember { mutableStateOf(vm.settings.authMode) }
    var user by remember { mutableStateOf(vm.settings.username) }
    var pass by remember { mutableStateOf(vm.settings.password) }
    var cookie by remember { mutableStateOf(vm.settings.sessionCookie) }
    var sso by remember { mutableStateOf(false) }
    var showPass by remember { mutableStateOf(false) }
    var showKey by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var success by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var savedNote by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        if (embedded) Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
        else Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (firstRun) {
            Spacer(Modifier.height(16.dp))
            Image(
                painterResource(R.drawable.bullarr_logo), contentDescription = null,
                modifier = Modifier.size(132.dp).align(Alignment.CenterHorizontally),
            )
            Text(
                "Bullarr", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Text("Pilotez votre collection de BD depuis le téléphone.", style = MaterialTheme.typography.bodyLarge)
        } else {
            Text("Connexion de l'app", style = MaterialTheme.typography.titleLarge)
        }

        ClearableField(
            value = url, onValueChange = { url = it; success = null; error = null },
            label = "URL du serveur",
            placeholder = "http://192.168.1.10:4000",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf("key" to ("Clé API" to Icons.Outlined.VpnKey), "password" to ("Compte" to Icons.Outlined.Person), "oidc" to ("SSO" to Icons.Outlined.Shield))
                .forEachIndexed { i, (m, p) ->
                    SegmentedButton(
                        selected = mode == m, onClick = { mode = m; showKey = false; showPass = false; success = null; error = null },
                        shape = SegmentedButtonDefaults.itemShape(i, 3),
                        icon = { Icon(p.second, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    ) { Text(p.first) }
                }
        }
        if (mode == "key") {
            ClearableField(
                value = key, onValueChange = { key = it; success = null; error = null },
                label = "Clé API",
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailing = {
                    IconButton(onClick = { showKey = !showKey }) {
                        Icon(
                            if (showKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = if (showKey) "Masquer la clé" else "Afficher la clé",
                        )
                    }
                },
                supportingText = "Dans Bullarr : Paramètres → Login → carte « Accès API ».",
                modifier = Modifier.fillMaxWidth(),
            )

}
        if (mode == "password") {
            ClearableField(
                value = user, onValueChange = { user = it; success = null; error = null },
                label = "Identifiant", modifier = Modifier.fillMaxWidth(),
            )
            ClearableField(
                value = pass, onValueChange = { pass = it; success = null; error = null },
                label = "Mot de passe",
                visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailing = {
                    IconButton(onClick = { showPass = !showPass }) {
                        Icon(if (showPass) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, contentDescription = "Afficher ou masquer")
                    }
                },
                supportingText = "Compte défini dans Bullarr : Paramètres → Login.",
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (mode == "oidc") {
            val context = androidx.compose.ui.platform.LocalContext.current
            // Navigateur du téléphone (passkeys possibles) : Bullarr renvoie ensuite vers bullarr://auth?code=…
            Button(
                enabled = url.isNotBlank() && !vm.ssoBusy,
                onClick = {
                    try {
                        val base = BullarrApi.normalizeBaseUrl(url)
                        vm.settings.lastUrl = url.trim()
                        vm.ssoError = null
                        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(base.toString().trimEnd('/') + "/login/redirect?app=1")))
                    } catch (e: Exception) { vm.ssoError = e.readable() }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (vm.ssoBusy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Icon(Icons.Outlined.Shield, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(if (vm.ssoBusy) "Connexion en cours…" else "Se connecter avec le SSO (navigateur)")
            }
            vm.ssoError?.let { ErrorCard(it) }
            // Secours : fenêtre intégrée, sans passkey (code de connexion ou autre appareil seulement).
            androidx.compose.material3.TextButton(enabled = url.isNotBlank(), onClick = { sso = true }) {
                Text(if (cookie.isBlank()) "Dans l'app (sans passkey)" else "Se reconnecter dans l'app")
            }
            if (cookie.isNotBlank()) Text("Session SSO ouverte.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (sso) OidcLoginDialog(
            serverUrl = url,
            onCookie = { c, origin ->
                // Le serveur redirige peut-être vers https : on garde l'adresse réellement atteinte.
                url = origin
                cookie = c; sso = false; success = "Session SSO ouverte : appuyez sur Se connecter."; error = null },
            onCancel = { sso = false },
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!firstRun) OutlinedButton(
                enabled = !testing,
                onClick = {
                    testing = true; success = null; error = null; savedNote = null
                    scope.launch {
                        try {
                            val api = when (mode) {
                                "key" -> BullarrApi(url, key)
                                else -> BullarrApi(url, "", SessionAuth(cookie, user, pass))
                            }
                            if (mode == "password") cookie = api.loginPassword(user, pass)
                            if (mode == "oidc" && cookie.isBlank()) throw ApiException("Ouvrez d'abord la session avec « Se connecter avec le SSO ».")
                            val me = api.getObject("api/auth/me")
                            val user = me.optJSONObject("user")
                            val who = user?.let { it.str("name") ?: it.str("preferred_username") ?: it.str("email") ?: it.str("username") }
                            if (mode != "key" && user0(me) == null) throw ApiException(
                                "Session non ouverte : le serveur ne vous reconnaît pas (identifiants ou SSO).")
                            success = when {
                                mode == "key" && key.isBlank() -> "Serveur joignable, mais sans clé API : cela ne marchera que si " +
                                    "l'authentification de Bullarr est désactivée."
                                who != null -> "Connexion réussie (utilisateur : $who)."
                                else -> "Connexion réussie : la clé API est acceptée."
                            }
                        } catch (e: Exception) {
                            error = e.readable()
                        } finally {
                            testing = false
                        }
                    }
                },
            ) {
                if (testing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("Tester")
            }
            Button(
                enabled = url.isNotBlank(),
                onClick = {
                    scope.launch {
                        try {
                            BullarrApi.normalizeBaseUrl(url)
                            if (mode == "password") cookie = BullarrApi(url, "", SessionAuth("", user, pass)).loginPassword(user, pass)
                            if (mode == "oidc" && cookie.isBlank()) throw ApiException("Ouvrez d'abord la session avec « Se connecter avec le SSO ».")
                            vm.save(url, key, mode, user, pass, cookie)
                            savedNote = "Connecté."
                            onSaved()
                        } catch (e: Exception) {
                            error = e.readable()
                        }
                    }
                },
            ) { Text("Se connecter") }
        }

        success?.let {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null)
                    Spacer(Modifier.size(12.dp))
                    Text(it)
                }
            }
        }
        error?.let { ErrorCard(it) }
        savedNote?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }

        Text(
            "L'URL, la clé et la session sont chiffrées sur l'appareil (EncryptedSharedPreferences). " +
                "Exemple d'URL : http://192.168.1.10:4000 ou https://bullarr.mondomaine.fr.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!firstRun) {
            OutlinedButton(onClick = { vm.logout() }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.size(8.dp))
                Text("Se déconnecter", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private fun user0(me: org.json.JSONObject): String? =
    me.optJSONObject("user")?.let { it.str("name") ?: it.str("preferred_username") ?: it.str("email") ?: it.str("username") }
