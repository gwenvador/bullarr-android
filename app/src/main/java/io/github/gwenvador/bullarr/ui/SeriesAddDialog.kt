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
import androidx.lifecycle.viewModelScope
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
fun SeriesAddDialog(vm: AppViewModel, onDismiss: () -> Unit, initialQuery: String = "", onRequested: () -> Unit = {}, onAdded: (Int, String) -> Unit) {
    val api = vm.api ?: return
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var query by remember { mutableStateOf(initialQuery) }
    var results by remember { mutableStateOf<List<JSONObject>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val library = vm.selectedLibrary ?: vm.libraries.firstOrNull()
    // Un lecteur ne peut pas ajouter : il demande la série (POST /api/requests), validée par un administrateur.
    val asRequest = vm.isReader
    // État de chaque résultat pour ce compte (POST /api/requests/lookup) : déjà dans la bibliothèque, déjà demandé…
    var states by remember { mutableStateOf<Map<String, JSONObject>>(emptyMap()) }

    fun search() {
        if (query.isBlank() || busy) return
        busy = true; error = null
        scope.launch {
            try { results = api.getObject("api/bedetheque/search", mapOf("q" to query.trim())).optJSONArray("results").objects() }
            catch (e: Exception) { error = e.readable() }
            busy = false
            val urls = results.orEmpty().mapNotNull { it.str("url") }
            if (urls.isNotEmpty()) runCatching {
                val items = api.postObject("api/requests/lookup", JSONObject().put("urls", org.json.JSONArray(urls))).optJSONObject("items")
                states = items?.let { o -> o.keys().asSequence().mapNotNull { k -> o.optJSONObject(k)?.let { k to it } }.toMap() }.orEmpty()
            }
        }
    }
    // Nom prérempli : la recherche part toute seule.
    androidx.compose.runtime.LaunchedEffect(Unit) { if (initialQuery.isNotBlank()) search() }
    fun request(r: JSONObject) {
        val url = r.str("url") ?: return
        val title = r.str("title") ?: ""
        val app = context.applicationContext
        onDismiss()
        vm.viewModelScope.launch {
            try {
                val res = api.postObject("api/requests", JSONObject().put("url", url).put("title", title))
                val msg = when (res.str("status")) {
                    "adding" -> "Demande validée : ajout de « $title » en cours"
                    "already" -> "« $title » est déjà dans la bibliothèque"
                    "added" -> "« $title » ajoutée"
                    else -> if (res.optBoolean("duplicate")) "« $title » est déjà demandée" else "Demande envoyée : « $title »"
                }
                android.widget.Toast.makeText(app, msg, android.widget.Toast.LENGTH_SHORT).show()
                onRequested()
            } catch (e: Exception) {
                android.widget.Toast.makeText(app, "Demande impossible : ${e.readable()}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }
    fun add(r: JSONObject) {
        if (asRequest) { request(r); return }
        val url = r.str("url") ?: return
        val lib = library ?: run { error = "Aucune bibliothèque : créez-en une dans l'interface web."; return }
        // Fermeture immédiate : l'ajout continue en arrière-plan (portée du ViewModel), résultat en toast.
        val title = r.str("title") ?: ""
        val app = context.applicationContext
        onDismiss()
        android.widget.Toast.makeText(app, "Ajout de « $title » en cours…", android.widget.Toast.LENGTH_SHORT).show()
        vm.viewModelScope.launch {
            try {
                val res = api.postObject("api/bedetheque/add-series", JSONObject().put("url", url).put("library_id", lib.id))
                val id = res.int("series_id") ?: throw io.github.gwenvador.bullarr.data.ApiException("Ajout de la série impossible : la réponse ne contient pas d'identifiant.")
                android.widget.Toast.makeText(app, "Série ajoutée : $title", android.widget.Toast.LENGTH_SHORT).show()
                vm.loadLibrary(force = true)
                onAdded(id, title)
            } catch (e: Exception) {
                android.widget.Toast.makeText(app, "Ajout de « $title » impossible : ${e.readable()}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (asRequest) "Demander une BD" else "Ajouter une série") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nom de la série (ou lien Bédéthèque)") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { search() }),
                    trailingIcon = { IconButton(onClick = { search() }) { Icon(Icons.Outlined.Search, contentDescription = "Chercher") } },
                )
                if (asRequest) Text("Un administrateur valide la demande avant l'ajout.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else library?.let { Text("Bibliothèque : ${it.name}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { ErrorCard(it) }
                if (results?.isEmpty() == true) Text("Aucun résultat sur Bédéthèque.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(results.orEmpty()) { r ->
                        val st = r.str("url")?.let { states[it] }
                        val inLibrary = st?.str("state") == "library"
                        Column(Modifier.fillMaxWidth().clickable(enabled = !busy) {
                            // Déjà dans la bibliothèque : ouvrir la fiche plutôt que l'ajouter / la demander.
                            val sid = st?.int("series_id")
                            if (inLibrary && sid != null) { onDismiss(); vm.openSeriesById(sid, r.str("title") ?: "") } else add(r)
                        }.padding(vertical = 8.dp)) {
                            Text(r.str("title") ?: "(sans titre)", style = MaterialTheme.typography.bodyLarge)
                            r.str("genre")?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            val badge = when (st?.str("state")) {
                                "library" -> "Dans la bibliothèque" + (st.int("total")?.let { tot -> " · ${st.int("owned") ?: 0}/$tot tomes" } ?: "")
                                "pending" -> "Déjà demandée"
                                "rejected" -> "Demande refusée"
                                else -> null
                            }
                            badge?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = if (st?.str("state") == "rejected") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
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
