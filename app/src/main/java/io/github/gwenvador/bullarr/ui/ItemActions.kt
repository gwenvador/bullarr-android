package io.github.gwenvador.bullarr.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.gwenvador.bullarr.data.VolumeRow
import io.github.gwenvador.bullarr.data.readable
import io.github.gwenvador.bullarr.data.str
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Actions sur séries et tomes (routes Bullarr existantes) : même appel pour une série ou une sélection. */
object ItemActions {
    suspend fun updateSeriesMetadata(api: io.github.gwenvador.bullarr.data.BullarrApi, id: Int) {
        api.postObject("api/bedetheque/update-metadata/series/$id", JSONObject())
    }
    suspend fun scanSeries(api: io.github.gwenvador.bullarr.data.BullarrApi, id: Int) {
        api.postObject("api/scan/series/$id", JSONObject())
    }
    suspend fun deleteSeries(api: io.github.gwenvador.bullarr.data.BullarrApi, id: Int) { api.deleteObject("api/series/$id") }
    suspend fun updateVolumeMetadata(api: io.github.gwenvador.bullarr.data.BullarrApi, id: Int) {
        api.postObject("api/bedetheque/update-metadata/volume/$id", JSONObject())
    }
    suspend fun refreshVolume(api: io.github.gwenvador.bullarr.data.BullarrApi, id: Int) { api.postObject("api/volumes/$id/refresh", JSONObject()) }
    suspend fun deleteVolume(api: io.github.gwenvador.bullarr.data.BullarrApi, id: Int) { api.deleteObject("api/volumes/$id") }
    suspend fun editVolume(api: io.github.gwenvador.bullarr.data.BullarrApi, id: Int, fields: Map<String, String>) {
        api.putObject("api/volumes/$id/manual-metadata", JSONObject(fields))
    }
}

/**
 * Lance [block] avec un message de réussite ou l'erreur lisible en toast ; [then] s'exécute dans les deux cas
 * (rechargement des données).
 */
fun launchAction(scope: CoroutineScope, context: android.content.Context, ok: String, then: () -> Unit = {}, block: suspend () -> Unit) {
    scope.launch {
        try {
            block()
            Toast.makeText(context, ok, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, e.readable(), Toast.LENGTH_LONG).show()
        }
        then()
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onDismiss(); onConfirm() }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

/** Édition manuelle des métadonnées d'un tome (PUT /api/volumes/<id>/manual-metadata). */
@Composable
fun EditVolumeDialog(v: VolumeRow, onDismiss: () -> Unit, onSave: (Map<String, String>) -> Unit) {
    val ci = v.comicInfo
    fun c(k: String) = ci?.str(k)?.takeUnless { it.startsWith("<") } ?: ""
    val fields = listOf(
        "title" to "Titre", "summary" to "Résumé", "writer" to "Scénario", "penciller" to "Dessin",
        "colorist" to "Couleurs", "publisher" to "Éditeur", "genre" to "Genre", "year" to "Année",
    )
    val values = remember { fields.associate { (k, _) -> k to mutableStateOf(if (k == "title") c("title").ifBlank { v.title ?: "" } else if (k == "year") c("year").ifBlank { v.year ?: "" } else c(k)) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Éditer ${v.label}") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                fields.forEach { (k, label) ->
                    var value by values.getValue(k)
                    OutlinedTextField(value = value, onValueChange = { value = it }, label = { Text(label) }, singleLine = k != "summary", modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDismiss(); onSave(values.mapValues { it.value.value }) }) { Text("Enregistrer") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
