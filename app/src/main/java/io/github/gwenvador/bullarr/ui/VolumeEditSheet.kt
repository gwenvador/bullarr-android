package io.github.gwenvador.bullarr.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.gwenvador.bullarr.data.ApiException
import io.github.gwenvador.bullarr.data.BullarrApi
import io.github.gwenvador.bullarr.data.SeriesSummary
import io.github.gwenvador.bullarr.data.VolumeRow
import io.github.gwenvador.bullarr.data.bool
import io.github.gwenvador.bullarr.data.int
import io.github.gwenvador.bullarr.data.objects
import io.github.gwenvador.bullarr.data.readable
import io.github.gwenvador.bullarr.data.str
import io.github.gwenvador.bullarr.data.volumeLabel
import kotlinx.coroutines.launch
import org.json.JSONObject

private enum class VolType(val key: String, val label: String) {
    Volume("volume", "Tome"), Integral("integral", "Intégrale"), Hs("hs", "HS"), Episode("episode", "Épisode")
}

private fun VolumeRow.volType() = when {
    isIntegral -> VolType.Integral
    isHs -> VolType.Hs
    isEpisode -> VolType.Episode
    else -> VolType.Volume
}

/**
 * « Éditer le tome », comme la fenêtre d'édition du site : numéro / type, métadonnées (ComicInfo, CBZ seulement),
 * renommage du fichier au format standard et déplacement vers une autre série de la même bibliothèque.
 * [onChanged] recharge la fiche ; [onMoved] est appelé quand le tome a quitté la série.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VolumeEditSheet(
    vm: AppViewModel, api: BullarrApi, v: VolumeRow, seriesId: Int, seriesTitle: String, libraryId: Int?, others: List<VolumeRow>,
    onDismiss: () -> Unit, onChanged: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var moving by remember { mutableStateOf(false) }
    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        if (moving) {
            MoveVolumePane(vm, api, v, seriesId, libraryId, onBack = { moving = false }, onDone = { msg -> toast(msg); onDismiss(); onChanged() })
            return@ModalBottomSheet
        }
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column {
                Text("Éditer ${v.label}", style = MaterialTheme.typography.titleLarge)
                Text(seriesTitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                v.filename?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }

            if (v.owned) {
                RenumberSection(api, v, others) { msg -> toast(msg); onDismiss(); onChanged() }
                HorizontalDivider()
                if (v.format?.lowercase() == "cbz") {
                    MetadataSection(v) { fields ->
                        launchAction(scope, context, "Tome modifié", then = onChanged, running = "Enregistrement des métadonnées de ${v.label}") { ItemActions.editVolume(api, v.id, fields) }
                    }
                } else {
                    SectionTitle(Icons.Outlined.Edit, "Métadonnées")
                    Text("Convertissez ce tome en CBZ pour pouvoir éditer ses métadonnées.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider()
                RenameSection(api, v, seriesId) { msg -> toast(msg); onChanged() }
                HorizontalDivider()
            } else {
                Text("Tome non possédé : rien à éditer tant qu'aucun fichier n'a été importé pour ce numéro.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SectionTitle(Icons.AutoMirrored.Outlined.DriveFileMove, "Déplacer vers une autre série")
            Text("Le fichier va dans le dossier de la série choisie (même bibliothèque).", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { moving = true }, enabled = libraryId != null) { Text("Choisir la série…") }
        }
    }
}

@Composable
private fun SectionTitle(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

/** Numéro / type (PUT /api/volumes/<id>/renumber) ; un tome de la série peut servir de modèle. */
@Composable
private fun RenumberSection(api: BullarrApi, v: VolumeRow, others: List<VolumeRow>, onDone: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var type by remember { mutableStateOf(v.volType()) }
    var number by remember { mutableStateOf(v.number?.toString() ?: "") }
    var suffix by remember { mutableStateOf("") }
    var pickOpen by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val changed = type != v.volType() || number.trim() != (v.number?.toString() ?: "") || suffix.isNotBlank()

    SectionTitle(Icons.Outlined.Tag, "Numéro / type")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        VolType.entries.forEach { t -> FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t.label) }) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(number, { number = it.filter(Char::isDigit) }, label = { Text("Numéro") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
        if (type == VolType.Volume) OutlinedTextField(suffix, { suffix = it.take(20) }, label = { Text("Suffixe (Bis…)") }, singleLine = true, modifier = Modifier.weight(1f))
    }
    val choices = others.filter { it.id != v.id && !it.isSpecial }
    if (choices.isNotEmpty()) Box {
        TextButton(onClick = { pickOpen = true }) {
            Text("Reprendre le numéro d'un tome de la série"); Icon(Icons.Outlined.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = pickOpen, onDismissRequest = { pickOpen = false }, modifier = Modifier.heightIn(max = 360.dp)) {
            choices.forEach { o ->
                DropdownMenuItem(
                    text = { Text(o.label + (o.title?.let { " · $it" } ?: "") + if (o.owned) " (en bibliothèque)" else "", maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    onClick = { type = o.volType(); number = o.number?.toString() ?: ""; suffix = ""; pickOpen = false },
                )
            }
        }
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    FilledTonalButton(enabled = changed && !busy, onClick = {
        busy = true; error = null
        scope.launch {
            try {
                val body = JSONObject().put("type", type.key).put("number", number.trim().toIntOrNull() ?: JSONObject.NULL)
                    .put("bis_suffix", suffix.trim().ifBlank { null } ?: JSONObject.NULL)
                val r = RunningActions.track("Changement du numéro de ${v.label}") { api.putObject("api/volumes/${v.id}/renumber", body) }
                if (!r.optBoolean("success", true)) throw ApiException(r.str("error") ?: "Numéro non changé")
                onDone(r.str("duplicate_filename")?.let { "Numéro changé. « $it » a aussi ce numéro : supprimez celui qui est en trop." } ?: "Numéro changé")
            } catch (e: Exception) { error = e.readable() } finally { busy = false }
        }
    }) { if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Changer le numéro") }
}

/** Métadonnées ComicInfo du tome (PUT /api/volumes/<id>/manual-metadata). */
@Composable
private fun MetadataSection(v: VolumeRow, onSave: (Map<String, String>) -> Unit) {
    val ci = v.comicInfo
    fun c(k: String) = ci?.str(k)?.takeUnless { it.startsWith("<") } ?: ""
    val fields = listOf(
        "title" to "Titre", "year" to "Année", "writer" to "Scénario", "penciller" to "Dessin",
        "colorist" to "Couleurs", "publisher" to "Éditeur", "genre" to "Genre", "summary" to "Résumé",
    )
    val values = remember(v.id) {
        fields.associate { (k, _) ->
            k to mutableStateOf(when (k) { "title" -> c("title").ifBlank { v.title ?: "" }; "year" -> c("year").ifBlank { v.year ?: "" }; else -> c(k) })
        }
    }
    SectionTitle(Icons.Outlined.Edit, "Métadonnées")
    fields.forEach { (k, label) ->
        var value by values.getValue(k)
        OutlinedTextField(value, { value = it }, label = { Text(label) }, singleLine = k != "summary",
            minLines = if (k == "summary") 3 else 1, modifier = Modifier.fillMaxWidth())
    }
    FilledTonalButton(onClick = { onSave(values.mapValues { it.value.value }) }) { Text("Enregistrer les métadonnées") }
}

/** Renommage du fichier au format standard, ou avec un nom choisi (aperçu puis exécution). */
@Composable
private fun RenameSection(api: BullarrApi, v: VolumeRow, seriesId: Int, onDone: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var custom by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<Pair<String, String>?>(null) }
    var unchanged by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun body() = JSONObject().put("volume_id", v.id).also { b -> custom.trim().takeIf { it.isNotEmpty() }?.let { b.put("custom_name", it) } }

    SectionTitle(Icons.Outlined.DriveFileRenameOutline, "Renommer le fichier")
    OutlinedTextField(custom, { custom = it; preview = null; unchanged = false }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        label = { Text("Nom personnalisé (facultatif)") }, supportingText = { Text("Vide : format standard des réglages") })
    preview?.let { (old, new) ->
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Avant : $old", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Après : $new", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
    }
    if (unchanged) Text("Déjà au format standard, rien à renommer.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(enabled = !busy, onClick = {
            busy = true; error = null
            scope.launch {
                try {
                    val r = api.postObject("api/series/$seriesId/rename/preview", body())
                    r.str("error")?.let { throw ApiException(it) }
                    val f = r.optJSONArray("files").objects().firstOrNull()
                    if (f == null || !f.optBoolean("changed")) { preview = null; unchanged = true }
                    else { preview = (f.str("old_name") ?: "") to (f.str("new_name") ?: ""); unchanged = false }
                } catch (e: Exception) { error = e.readable() } finally { busy = false }
            }
        }) { Text("Aperçu") }
        FilledTonalButton(enabled = preview != null && !busy, onClick = {
            busy = true; error = null
            scope.launch {
                try {
                    val r = RunningActions.track("Renommage du fichier de ${v.label}") { api.postObject("api/series/$seriesId/rename/execute", body()) }
                    r.str("error")?.let { throw ApiException(it) }
                    val f = r.optJSONArray("files").objects().firstOrNull()
                    if (f != null && !f.optBoolean("success", true)) throw ApiException(f.str("error") ?: "Renommage impossible")
                    preview = null
                    onDone("Fichier renommé")
                } catch (e: Exception) { error = e.readable() } finally { busy = false }
            }
        }) { if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Renommer") }
    }
}

/** Déplacement : choix de la série (même bibliothèque), puis du tome qu'il y prend (PUT move-to-series). */
@Composable
private fun MoveVolumePane(vm: AppViewModel, api: BullarrApi, v: VolumeRow, seriesId: Int, libraryId: Int?, onBack: () -> Unit, onDone: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var all by remember { mutableStateOf<List<SeriesSummary>?>(null) }
    var filter by remember { mutableStateOf("") }
    var target by remember { mutableStateOf<SeriesSummary?>(null) }
    var slots by remember { mutableStateOf<List<JSONObject>?>(null) }
    var slot by remember { mutableStateOf<JSONObject?>(null) }
    var busy by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(libraryId) {
        if (libraryId != null) try { all = vm.librarySeries(libraryId).filter { it.id != seriesId } } catch (e: Exception) { error = e.readable() }
    }
    LaunchedEffect(target?.id) {
        val t = target ?: return@LaunchedEffect
        slots = null; slot = null
        try { slots = api.getArray("api/series/${t.id}/volumes").objects() } catch (e: Exception) { error = e.readable() }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Déplacer ${v.label}", style = MaterialTheme.typography.titleLarge)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        val t = target
        if (t == null) {
            OutlinedTextField(filter, { filter = it }, label = { Text("Filtrer les séries") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            val list = all
            if (list == null) CircularProgressIndicator(Modifier.padding(16.dp))
            else {
                val needle = normalizeText(filter.trim())
                val matches = list.filter { normalizeText(it.title).contains(needle) }.take(60)
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    items(matches, key = { it.id }) { s ->
                        Column(Modifier.fillMaxWidth().clickable { target = s }.padding(vertical = 10.dp)) {
                            Text(s.title, style = MaterialTheme.typography.bodyLarge)
                            Text("${s.ownedTomes} album(s)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        HorizontalDivider()
                    }
                }
            }
            TextButton(onClick = onBack) { Text("Retour") }
        } else {
            Text("Vers « ${t.title} ». Quel tome devient-il dans cette série ?", style = MaterialTheme.typography.bodyMedium)
            val list = slots
            if (list == null) CircularProgressIndicator(Modifier.padding(16.dp))
            else LazyColumn(Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
                item {
                    Row(Modifier.fillMaxWidth().clickable { slot = null }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = slot == null, onClick = { slot = null })
                        Text("Même numéro que dans la série actuelle")
                    }
                }
                items(list) { o ->
                    val owned = o.bool("is_owned") == true
                    val title = o.str("bedetheque_title") ?: o.optJSONObject("comicinfo")?.str("title")
                    Row(Modifier.fillMaxWidth().clickable(enabled = !owned) { slot = o }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = slot === o, onClick = { slot = o }, enabled = !owned)
                        Text(
                            listOfNotNull(volumeLabel(o).ifBlank { null }, title).joinToString(" · ") + if (owned) " (déjà possédé)" else "",
                            color = if (owned) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { target = null }) { Text("Retour") }
                Button(enabled = !busy && slots != null, onClick = { confirm = true }) {
                    if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Déplacer")
                }
            }
            if (confirm) androidx.compose.material3.AlertDialog(
                onDismissRequest = { confirm = false },
                title = { Text("Déplacer ${v.label} ?") },
                text = { Text("Le fichier ira dans le dossier de « ${t.title} ».") },
                dismissButton = { TextButton(onClick = { confirm = false }) { Text("Annuler") } },
                confirmButton = { TextButton(onClick = {
                    confirm = false; busy = true; error = null
                    scope.launch {
                        try {
                            val body = JSONObject().put("target_series_id", t.id)
                            slot?.let { s ->
                                fun n(k: String) = s.int(k) ?: JSONObject.NULL
                                body.put("target_volume", JSONObject().put("volume_number", n("volume_number"))
                                    .put("is_integral", s.bool("is_integral") == true).put("integral_number", n("integral_number"))
                                    .put("is_hs", s.bool("is_hs") == true).put("hs_number", n("hs_number"))
                                    .put("is_episode", s.bool("is_episode") == true).put("episode_number", n("episode_number")))
                            }
                            val r = RunningActions.track("Déplacement de ${v.label} vers « ${t.title} »") { api.putObject("api/volumes/${v.id}/move-to-series", body) }
                            if (!r.optBoolean("success", true)) throw ApiException(r.str("error") ?: "Déplacement impossible")
                            vm.loadLibrary(force = true)
                            onDone("${v.label} déplacé vers « ${t.title} »")
                        } catch (e: Exception) { error = e.readable(); busy = false }
                    }
                }) { Text("Déplacer") } },
            )
        }
    }
}

/** Minuscules sans accents, pour filtrer comme le site. */
private fun normalizeText(s: String): String =
    java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
