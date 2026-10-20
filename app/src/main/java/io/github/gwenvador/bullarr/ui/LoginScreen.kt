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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import io.github.gwenvador.bullarr.data.BullarrApi
import io.github.gwenvador.bullarr.data.readable
import io.github.gwenvador.bullarr.data.str
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(vm: AppViewModel, firstRun: Boolean, onSaved: () -> Unit) {
    var url by remember { mutableStateOf(vm.settings.serverUrl) }
    var key by remember { mutableStateOf(vm.settings.apiKey) }
    var showKey by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var success by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var savedNote by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
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
            Text("Connexion", style = MaterialTheme.typography.headlineSmall)
        }

        ClearableField(
            value = url, onValueChange = { url = it; success = null; error = null },
            label = "URL du serveur",
            placeholder = "http://192.168.1.10:4000",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
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

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                enabled = !testing,
                onClick = {
                    testing = true; success = null; error = null; savedNote = null
                    scope.launch {
                        try {
                            val me = BullarrApi(url, key).getObject("api/auth/me")
                            val user = me.optJSONObject("user")
                            val who = user?.let { it.str("name") ?: it.str("preferred_username") ?: it.str("email") ?: it.str("username") }
                            success = when {
                                key.isBlank() -> "Serveur joignable, mais sans clé API : cela ne marchera que si " +
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
                    try {
                        BullarrApi.normalizeBaseUrl(url)
                        vm.save(url, key)
                        savedNote = "Enregistré."
                        onSaved()
                    } catch (e: Exception) {
                        error = e.readable()
                    }
                },
            ) { Text("Enregistrer") }
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
            "L'URL et la clé sont chiffrées sur l'appareil (EncryptedSharedPreferences). " +
                "Exemple d'URL : http://192.168.1.10:4000 ou https://bullarr.mondomaine.fr.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!firstRun) UpdateCard(vm)
    }
}
