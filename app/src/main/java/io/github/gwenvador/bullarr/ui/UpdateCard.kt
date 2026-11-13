package io.github.gwenvador.bullarr.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import io.github.gwenvador.bullarr.data.UpdateState
import io.github.gwenvador.bullarr.data.formatBytes

/** Version installée et état de la mise à jour, d'après les releases GitHub. */
@Composable
fun UpdateCard(vm: AppViewModel) {
    val cs = MaterialTheme.colorScheme
    val uri = LocalUriHandler.current
    // Une vérification par lancement de l'app ; le bouton relance à la demande.
    LaunchedEffect(Unit) { if (vm.update == UpdateState.Idle) vm.checkUpdate() }
    val state = vm.update

    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Application", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Version installée : ${vm.installedVersion}", style = MaterialTheme.typography.bodyLarge)
            when (state) {
                UpdateState.Idle, UpdateState.Checking -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("Recherche d'une mise à jour…", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                }
                is UpdateState.UpToDate -> Text(
                    "À jour" + (state.latest?.let { " · dernière version publiée : ${it.version}" } ?: " · aucune version publiée trouvée"),
                    style = MaterialTheme.typography.bodyMedium, color = cs.primary,
                )
                is UpdateState.Available -> {
                    val r = state.release
                    Text(
                        "Mise à jour disponible : ${r.version}" + (r.publishedAt?.take(10)?.let { " (publiée le $it)" } ?: ""),
                        style = MaterialTheme.typography.bodyMedium, color = cs.tertiary, fontWeight = FontWeight.Medium,
                    )
                    Button(onClick = { runCatching { uri.openUri(r.url) } }) { Text("Télécharger l'APK" + (formatBytes(r.sizeBytes)?.let { " ($it)" } ?: "")) }
                    Text("Ouvrez le fichier téléchargé pour l'installer par-dessus la version actuelle.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
                is UpdateState.Failed -> ErrorCard(state.message)
            }
            if (state !is UpdateState.Checking && state != UpdateState.Idle) {
                OutlinedButton(onClick = { vm.checkUpdate() }) { Text("Vérifier maintenant") }
            }
        }
    }
}

/** Réglages → À propos : version, mise à jour et liens vers les dépôts GitHub. */
@Composable
fun AboutScreen(vm: AppViewModel) {
    val cs = MaterialTheme.colorScheme
    val uri = LocalUriHandler.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Image(painterResource(io.github.gwenvador.bullarr.R.drawable.bullarr_logo), contentDescription = null, modifier = Modifier.size(56.dp))
            Column {
                Text("Bullarr", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = cs.primary)
                Text("Version ${vm.installedVersion}", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            }
        }
        UpdateCard(vm)
        MihonCard(vm)
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors()) {
            Column(Modifier.padding(vertical = 8.dp)) {
                Text("Liens", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                listOf(
                    Triple(Icons.Outlined.PhoneAndroid, "Application Android", "https://github.com/gwenvador/bullarr-android"),
                    Triple(Icons.Outlined.Dns, "Serveur Bullarr", "https://github.com/gwenvador/bullarr"),
                    Triple(Icons.Outlined.NewReleases, "Versions de l'application", "https://github.com/gwenvador/bullarr-android/releases"),
                    Triple(Icons.Outlined.BugReport, "Signaler un problème", "https://github.com/gwenvador/bullarr-android/issues"),
                ).forEach { (icon, label, url) ->
                    ListItem(
                        headlineContent = { Text(label) },
                        supportingContent = { Text(url.removePrefix("https://"), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingContent = { Icon(icon, contentDescription = null, tint = cs.primary) },
                        trailingContent = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, tint = cs.onSurfaceVariant, modifier = Modifier.size(18.dp)) },
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                        modifier = Modifier.clickable { runCatching { uri.openUri(url) } },
                    )
                }
            }
        }
    }
}

/** Extension Mihon / Tachiyomi servie par le serveur Bullarr, et clé API personnelle à y saisir. */
@Composable
fun MihonCard(vm: AppViewModel) {
    val cs = MaterialTheme.colorScheme
    val uri = LocalUriHandler.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val server = vm.settings.serverUrl.trim().trimEnd('/')
    val sessionLogin = vm.settings.authMode != "key"
    var busy by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var message by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }

    fun copy(label: String, text: String, sensitive: Boolean) {
        val cm = context.getSystemService(android.content.ClipboardManager::class.java)
        val clip = android.content.ClipData.newPlainText(label, text)
        if (sensitive) clip.description.extras = android.os.PersistableBundle().apply {
            putBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
        cm.setPrimaryClip(clip)
    }

    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Extension Mihon / Tachiyomi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Pour lire votre bibliothèque Bullarr dans Mihon. Après l'installation : Sources → Bullarr → réglages, " +
                    "puis saisissez l'adresse du serveur et votre clé API personnelle (ou un identifiant et un mot de passe).",
                style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
            )
            Button(onClick = { runCatching { uri.openUri("$server/static/extensions/bullarr-mihon.apk") } }, enabled = server.isNotBlank()) {
                Text("Télécharger l'extension")
            }
            Text("Ouvrez le fichier téléchargé pour l'installer.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { copy("Adresse Bullarr", server, false); message = "Adresse copiée" }, enabled = server.isNotBlank()) {
                    Text("Copier l'adresse")
                }
                if (sessionLogin) OutlinedButton(enabled = !busy, onClick = {
                    busy = true; message = null
                    scope.launch {
                        message = try {
                            val api = vm.api ?: throw IllegalStateException("Non connecté")
                            var key = api.getObject("/api/auth/profile/api-key").optString("api_key")
                            if (key.isBlank()) key = api.postObject("/api/auth/profile/api-key", org.json.JSONObject()).optString("api_key")
                            if (key.isBlank()) "Le serveur n'a pas renvoyé de clé"
                            else { copy("Clé API Bullarr", key, true); "Clé API copiée" }
                        } catch (e: Exception) {
                            "Clé indisponible : ${e.message ?: "erreur"}"
                        } finally { busy = false }
                    }
                }) {
                    if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text("Copier ma clé API")
                }
            }
            if (!sessionLogin) Text(
                "Vous êtes connecté par clé API : réutilisez-la, ou créez une clé personnelle sur le site (Mon profil → Clé API).",
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
            )
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = cs.primary) }
        }
    }
}
