package io.github.gwenvador.bullarr.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.ImeAction
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
import io.github.gwenvador.bullarr.data.SeriesDetail
import io.github.gwenvador.bullarr.data.VolumeRow
import io.github.gwenvador.bullarr.data.readable
import io.github.gwenvador.bullarr.data.str
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONArray
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
    /** Recherches d'un album dans les magasins (même liste que le site). */
    fun shopLinks(query: String): List<Pair<String, String>> {
        val q = java.net.URLEncoder.encode(query, "UTF-8")
        return listOf(
            "Fnac" to "https://www.fnac.com/SearchResult/ResultList.aspx?Search=$q",
            "Amazon" to "https://www.amazon.fr/s?k=$q&i=stripbooks",
            "BDfugue" to "https://www.bdfugue.com/catalogsearch/result/?q=$q",
        )
    }
    suspend fun editSeries(api: io.github.gwenvador.bullarr.data.BullarrApi, id: Int, fields: Map<String, String>) {
        api.putObject("api/series/$id/manual-metadata", JSONObject(fields))
    }
    /** Listes personnelles d'une série ; seul le booléen fourni est modifié côté Bullarr. */
    suspend fun setSeriesReadingLists(api: io.github.gwenvador.bullarr.data.BullarrApi, id: Int, toReadLater: Boolean? = null, isFavorite: Boolean? = null) {
        val body = JSONObject()
        toReadLater?.let { body.put("to_read_later", it) }
        isFavorite?.let { body.put("is_favorite", it) }
        api.putObject("api/series/$id/reading-lists", body)
    }
    /** Remplace la liste complète des tags d'une série. */
    suspend fun setSeriesTags(api: io.github.gwenvador.bullarr.data.BullarrApi, id: Int, tags: List<String>) {
        api.putObject("api/series/$id/tags", JSONObject().put("tags", JSONArray(tags)))
    }
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

/** Édition des métadonnées de série : seuls les champs modifiés sont envoyés (un champ vide = effacé). */
@Composable
fun SeriesEditDialog(d: SeriesDetail, onDismiss: () -> Unit, onSave: (Map<String, String>) -> Unit) {
    val years = d.years?.split(" – ")
    val initial = mapOf(
        "title" to d.title, "summary" to (d.summary ?: ""), "genre" to (d.genre ?: ""), "status" to (d.status ?: ""),
        "author" to (d.authors ?: ""), "year_start" to (years?.getOrNull(0) ?: ""), "year_end" to (years?.getOrNull(1) ?: ""),
    )
    val labels = linkedMapOf(
        "title" to "Titre", "summary" to "Résumé", "genre" to "Genre", "status" to "Statut",
        "author" to "Auteur(s)", "year_start" to "Année de début", "year_end" to "Année de fin",
    )
    val values = remember { initial.mapValues { androidx.compose.runtime.mutableStateOf(it.value) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Éditer la série") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                labels.forEach { (k, label) ->
                    var value by values.getValue(k)
                    OutlinedTextField(value = value, onValueChange = { value = it }, label = { Text(label) }, singleLine = k != "summary", modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val changed = values.filter { (k, st) -> st.value != initial.getValue(k) }.mapValues { it.value.value.trim() }
                onDismiss()
                if (changed.isNotEmpty()) onSave(changed)
            }) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

/** Tag comme le site le range : minuscules, espaces remplacés par des tirets. */
fun normalizeSeriesTag(raw: String): String = raw.trim().lowercase().replace(Regex("\\s+"), "-")

/** Édition des tags d'une série : une tuile retire le tag, le champ en ajoute un. Enregistre tout d'un coup. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SeriesTagsDialog(initial: List<String>, onDismiss: () -> Unit, onSave: (List<String>) -> Unit) {
    var tags by remember { mutableStateOf(initial) }
    var draft by remember { mutableStateOf("") }
    // Le champ en cours compte aussi à l'enregistrement, même sans avoir touché « + ».
    fun withDraft(): List<String> {
        val tag = normalizeSeriesTag(draft)
        return if (tag.isNotEmpty() && tag !in tags) tags + tag else tags
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tags de la série") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (tags.isEmpty()) Text("Aucun tag.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    tags.forEach { tag ->
                        InputChip(
                            selected = false,
                            onClick = { tags = tags - tag },
                            label = { Text(tag) },
                            trailingIcon = { Icon(Icons.Outlined.Close, contentDescription = "Retirer $tag", modifier = Modifier.size(18.dp)) },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = draft, onValueChange = { draft = it }, label = { Text("Nouveau tag") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { tags = withDraft(); draft = "" }),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { tags = withDraft(); draft = "" }) {
                        Icon(Icons.Outlined.Add, contentDescription = "Ajouter ce tag")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val finalTags = withDraft()
                onDismiss()
                if (finalTags != initial) onSave(finalTags)
            }) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
