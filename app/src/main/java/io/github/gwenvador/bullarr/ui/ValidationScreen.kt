package io.github.gwenvador.bullarr.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.gwenvador.bullarr.data.formatBytes
import io.github.gwenvador.bullarr.data.int
import io.github.gwenvador.bullarr.data.long
import io.github.gwenvador.bullarr.data.objects
import io.github.gwenvador.bullarr.data.readable
import io.github.gwenvador.bullarr.data.str
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** Validation : /api/auto-acquire/reviews — choix manuels que la recherche automatique n'a pas pu trancher. */
@Composable
fun ValidationScreen(vm: AppViewModel, onOpenSeries: (Int, String) -> Unit = { id, title -> vm.openSeriesById(id, title) }) {
    val api = vm.api ?: return
    var reviews by remember { mutableStateOf<List<JSONObject>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    // Séries dépliées (repliées par défaut) : l'en-tête suffit pour parcourir la liste.
    var expanded by remember { mutableStateOf(setOf<String>()) }
    // Une entrée par série : tous ses tomes à valider y sont regroupés, dans l'ordre de la liste.
    val groups = remember(reviews) {
        reviews.orEmpty().filter { it.int("id") != null }
            .groupBy { r -> r.int("series_id")?.takeIf { it != 0 }?.let { "id:$it" } ?: "t:${r.str("series_title") ?: ""}" }
            .toList()
    }

    // Candidats en cours d'envoi : le serveur peut chercher le lien chez Prowlarr pendant ~15 s.
    var sending by remember { mutableStateOf(setOf<Pair<Int, Int>>()) }

    suspend fun load() {
        loading = true; error = null
        try { reviews = api.getArray("api/auto-acquire/reviews").objects(); vm.reviewCount = reviews?.size ?: 0 } catch (e: Exception) { error = e.readable() }
        loading = false
    }
    LaunchedEffect(api) { load() }
    fun ignore(ids: List<Int>, ok: String) = launchAction(scope, context, ok, then = { scope.launch { load() } }) {
        ids.forEach { api.postObject("api/auto-acquire/reviews/$it/resolve", JSONObject()) }
    }

    PullToRefreshBox(isRefreshing = loading && reviews != null, onRefresh = { scope.launch { load() } }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Comptes et demandes de BD à valider (il n'y a plus de page Demandes pour les administrateurs).
            item(key = "requests") { AdminRequestsBlock(vm, onOpenSeries) }
            item(key = "reviews-title") {
                Text("Choix de téléchargement", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
            }
            if (loading && reviews == null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            error?.let { e -> item { ErrorCard(e, onRetry = { scope.launch { load() } }) } }
            if (reviews?.isEmpty() == true) item { EmptyState("Rien à valider", "Aucun choix de téléchargement en attente.") }
            items(groups, key = { it.first }) { (key, entries) ->
                val open = key in expanded
                val first = entries.first()
                val candidateCount = entries.sumOf { it.optJSONArray("candidates")?.length() ?: 0 }
                Card(Modifier.fillMaxWidth().animateContentSize()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            Modifier.fillMaxWidth().clickable { expanded = if (open) expanded - key else expanded + key },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                contentDescription = if (open) "Replier" else "Déplier",
                                modifier = Modifier.padding(end = 8.dp),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(first.str("series_title") ?: "(série inconnue)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                // Auteurs Bédéthèque (comme le site) : un même nom une seule fois.
                                val authors = listOfNotNull(first.str("bedetheque_scenaristes"), first.str("bedetheque_dessinateurs"))
                                    .flatMap { it.split(Regex("""\s*,\s*""")) }.filter { it.isNotBlank() }.distinct().joinToString(", ")
                                if (authors.isNotEmpty()) Text(
                                    authors, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                )
                                val labels = entries.mapNotNull { it.str("volume_label") }
                                val sub = if (entries.size == 1) listOfNotNull(labels.firstOrNull(), "$candidateCount candidat(s)")
                                else listOf("${entries.size} tomes", "$candidateCount candidat(s)")
                                Text(sub.joinToString(" · "), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                if (entries.size > 1 && !open && labels.isNotEmpty()) Text(
                                    labels.joinToString(", "), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                            // Lien vers la série : sa fiche dans l'app, ou sa fiche Bédéthèque si elle n'est pas encore en bibliothèque.
                            val sid = first.int("series_id")?.takeIf { it != 0 }
                            val bdUrl = if (sid == null) entries.firstNotNullOfOrNull { r ->
                                r.optJSONArray("candidates").objects().firstNotNullOfOrNull { it.str("bedetheque_url")?.takeIf { u -> u.startsWith("http") } }
                            } else null
                            if (sid != null) IconButton(onClick = { onOpenSeries(sid, first.str("series_title") ?: "") }) {
                                Icon(Icons.Outlined.CollectionsBookmark, contentDescription = "Ouvrir la série")
                            } else if (bdUrl != null) IconButton(onClick = { runCatching { uri.openUri(bdUrl) } }) {
                                Icon(Icons.Outlined.OpenInNew, contentDescription = "Fiche Bédéthèque")
                            }
                            IconButton(onClick = {
                                ignore(entries.mapNotNull { it.int("id") }, if (entries.size > 1) "Série ignorée (${entries.size} tomes)" else "Ignoré")
                            }) { Icon(Icons.Outlined.Close, contentDescription = if (entries.size > 1) "Ignorer tous les tomes de cette série" else "Ignorer cette validation") }
                        }
                        if (open) {
                            if (first.optInt("series_id", 0) == 0) Text(
                                "Série non associée : à rattacher à Bédéthèque depuis l'interface web.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary,
                            )
                            entries.forEach { r -> ReviewEntry(r, showHeader = entries.size > 1, sending = sending, onIgnore = { ignore(listOf(it), "Ignoré") }) { id, idx ->
                                if (id to idx in sending) return@ReviewEntry
                                sending = sending + (id to idx)
                                android.widget.Toast.makeText(context, "Envoi au client… (recherche du lien jusqu'à ~20 s)", android.widget.Toast.LENGTH_SHORT).show()
                                launchAction(scope, context, "Téléchargement lancé", then = { sending = sending - (id to idx); scope.launch { load() } }) {
                                    val res = api.postObject("api/auto-acquire/reviews/$id/download", JSONObject().put("candidate_index", idx))
                                    if (res.optBoolean("success", true).not()) throw io.github.gwenvador.bullarr.data.ApiException(res.str("message") ?: "Échec du téléchargement")
                                }
                            } }
                        }
                    }
                }
            }
        }
    }
}

/** Un tome à valider dans l'entrée de sa série : raison, candidats et leur bouton de téléchargement. */
@Composable
private fun ReviewEntry(r: JSONObject, showHeader: Boolean, sending: Set<Pair<Int, Int>>, onIgnore: (Int) -> Unit, onDownload: (Int, Int) -> Unit) {
    val id = r.int("id") ?: return
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    val candidates = r.optJSONArray("candidates") ?: JSONArray()
    if (showHeader) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(r.str("volume_label") ?: "(tome inconnu)", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            IconButton(onClick = { onIgnore(id) }) { Icon(Icons.Outlined.Close, contentDescription = "Ignorer ce tome") }
        }
    }
    r.str("reason")?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    // « une recherche par album à valider » : filtre les fichiers trouvés de cet album pour
    // repérer le bon (tous les mots, sans accents ni ligatures), comme sur le site.
    var filter by androidx.compose.runtime.saveable.rememberSaveable(id) { mutableStateOf("") }
    val words = reviewFilterNormalize(filter).split(' ').filter { it.isNotEmpty() }
    if (candidates.length() > 1) CompactSearchField(
        value = filter, onValueChange = { filter = it },
        placeholder = "Filtrer les fichiers",
        modifier = Modifier.height(36.dp),
    )
    var shown = 0
    for (i in 0 until candidates.length()) {
        val c = candidates.optJSONObject(i) ?: continue
        val idx = c.int("candidate_index") ?: i
        if (words.isNotEmpty()) {
            val text = reviewFilterNormalize((c.str("filename") ?: c.str("title"))?.let { io.github.gwenvador.bullarr.data.decodeName(it) }.orEmpty())
            if (!words.all { it in text }) continue
        }
        shown++
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text((c.str("filename") ?: c.str("title"))?.let { io.github.gwenvador.bullarr.data.decodeName(it) } ?: "(sans nom)", style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                val meta = listOfNotNull(
                    c.str("source"), c.long("size")?.takeIf { it > 0 }?.let { formatBytes(it) },
                    c.str("seeders")?.let { "$it seeds" },
                ).joinToString(" · ")
                if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // Page d'origine du fichier (fil EBDZ, page de l'indexeur…), comme le bouton Source du site.
            val source = listOf("thread_url", "info_url", "source_link", "link").firstNotNullOfOrNull { k -> c.str(k)?.takeIf { it.startsWith("http") } }
            if (source != null) IconButton(onClick = { runCatching { uri.openUri(source) } }) {
                Icon(Icons.Outlined.OpenInNew, contentDescription = "Ouvrir la source")
            }
            if (id to idx in sending) Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                androidx.compose.material3.CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            }
            else IconButton(onClick = { onDownload(id, idx) }) { Icon(Icons.Outlined.Download, contentDescription = "Télécharger ce candidat", tint = MaterialTheme.colorScheme.primary) }
        }
    }
    if (words.isNotEmpty() && shown == 0) Text("Aucun fichier ne correspond.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun reviewFilterNormalize(s: String): String =
    io.github.gwenvador.bullarr.data.normalize(s.replace(Regex("[–—]"), " ")).trim()
