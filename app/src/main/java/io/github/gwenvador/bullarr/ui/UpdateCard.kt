package io.github.gwenvador.bullarr.ui

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

    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = cs.surfaceContainer)) {
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
