package io.github.gwenvador.bullarr.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import io.github.gwenvador.bullarr.data.int
import io.github.gwenvador.bullarr.data.objects
import io.github.gwenvador.bullarr.data.readable
import io.github.gwenvador.bullarr.data.str
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Ajout d'une série à la bibliothèque par son nom : GET /api/bedetheque/search?q= puis
 * POST /api/bedetheque/add-series (url Bédéthèque + bibliothèque). [onAdded] reçoit l'id de la série créée.
 */
@Composable
fun SeriesAddDialog(vm: AppViewModel, onDismiss: () -> Unit, initialQuery: String = "", onAdded: (Int, String) -> Unit) {
    val api = vm.api ?: return
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var query by remember { mutableStateOf(initialQuery) }
    var results by remember { mutableStateOf<List<JSONObject>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val library = vm.selectedLibrary ?: vm.libraries.firstOrNull()

    fun search() {
        if (query.isBlank() || busy) return
        busy = true; error = null
        scope.launch {
            try { results = api.getObject("api/bedetheque/search", mapOf("q" to query.trim())).optJSONArray("results").objects() }
            catch (e: Exception) { error = e.readable() }
            busy = false
        }
    }
    // Nom prérempli : la recherche part toute seule.
    androidx.compose.runtime.LaunchedEffect(Unit) { if (initialQuery.isNotBlank()) search() }
    fun add(r: JSONObject) {
        val url = r.str("url") ?: return
        val lib = library ?: run { error = "Aucune bibliothèque : créez-en une dans l'interface web."; return }
        busy = true; error = null
        scope.launch {
            try {
                val res = api.postObject("api/bedetheque/add-series", JSONObject().put("url", url).put("library_id", lib.id))
                val id = res.int("series_id") ?: throw io.github.gwenvador.bullarr.data.ApiException("Ajout de la série impossible : la réponse ne contient pas d'identifiant.")
                android.widget.Toast.makeText(context, "Série ajoutée : ${r.str("title") ?: ""}", android.widget.Toast.LENGTH_SHORT).show()
                vm.loadLibrary(force = true)
                onDismiss(); onAdded(id, r.str("title") ?: "")
            } catch (e: Exception) { error = e.readable(); busy = false }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajouter une série") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nom de la série (ou lien Bédéthèque)") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { search() }),
                    trailingIcon = { IconButton(onClick = { search() }) { Icon(Icons.Outlined.Search, contentDescription = "Chercher") } },
                )
                library?.let { Text("Bibliothèque : ${it.name}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { ErrorCard(it) }
                if (results?.isEmpty() == true) Text("Aucun résultat sur Bédéthèque.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(results.orEmpty()) { r ->
                        Column(Modifier.fillMaxWidth().clickable(enabled = !busy) { add(r) }.padding(vertical = 8.dp)) {
                            Text(r.str("title") ?: "(sans titre)", style = MaterialTheme.typography.bodyLarge)
                            r.str("genre")?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
    )
}

/** Choix d'une série de la bibliothèque (filtre par nom) et, en option, d'un numéro de tome. */
@Composable
fun AssignSeriesDialog(vm: AppViewModel, count: Int, initialQuery: String, onDismiss: () -> Unit, onAddNew: () -> Unit, onPick: (seriesId: Int, volume: Int?) -> Unit) {
    androidx.compose.runtime.LaunchedEffect(vm.api) { vm.loadLibrary() }
    var query by remember { mutableStateOf(initialQuery) }
    var volume by remember { mutableStateOf("") }
    val terms = io.github.gwenvador.bullarr.data.normalize(query).split(' ').filter { it.isNotBlank() }
    val matches = vm.series.filter { s -> terms.isEmpty() || terms.all { it in s.searchKey } }.take(60)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Assigner $count téléchargement(s) à une série") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true, label = { Text("Série") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(
                        value = volume, onValueChange = { volume = it.filter(Char::isDigit) }, singleLine = true,
                        label = { Text("Tome") }, modifier = Modifier.width(84.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    )
                }
                Text("Le numéro de tome est facultatif ; vide, le tome suivi est effacé.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (vm.series.isEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth())
                LazyColumn(Modifier.heightIn(max = 300.dp)) {
                    items(matches, key = { it.id }) { s ->
                        Text(s.title, modifier = Modifier.fillMaxWidth().clickable { onDismiss(); onPick(s.id, volume.toIntOrNull()) }.padding(vertical = 10.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDismiss(); onAddNew() }) { Text("Nouvelle série…") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
