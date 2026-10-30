package io.github.gwenvador.bullarr.ui

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
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
fun ValidationScreen(vm: AppViewModel) {
    val api = vm.api ?: return
    var reviews by remember { mutableStateOf<List<JSONObject>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    suspend fun load() {
        loading = true; error = null
        try { reviews = api.getArray("api/auto-acquire/reviews").objects(); vm.reviewCount = reviews?.size ?: 0 } catch (e: Exception) { error = e.readable() }
        loading = false
    }
    LaunchedEffect(api) { load() }

    PullToRefreshBox(isRefreshing = loading && reviews != null, onRefresh = { scope.launch { load() } }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (loading && reviews == null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            error?.let { e -> item { ErrorCard(e, onRetry = { scope.launch { load() } }) } }
            if (reviews?.isEmpty() == true) item { EmptyState("Rien à valider", "Aucune décision manuelle en attente.") }
            items(reviews.orEmpty(), key = { it.int("id") ?: 0 }) { r ->
                val id = r.int("id") ?: return@items
                val candidates = r.optJSONArray("candidates") ?: JSONArray()
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(r.str("series_title") ?: "(série inconnue)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                r.str("volume_label")?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
                            }
                            IconButton(onClick = {
                                launchAction(scope, context, "Ignoré", then = { scope.launch { load() } }) { api.postObject("api/auto-acquire/reviews/$id/resolve", JSONObject()) }
                            }) { Icon(Icons.Outlined.Close, contentDescription = "Ignorer cette validation") }
                        }
                        r.str("reason")?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        if (r.optInt("series_id", 0) == 0) Text(
                            "Série non associée : à rattacher à Bédéthèque depuis l'interface web.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary,
                        )
                        for (i in 0 until candidates.length()) {
                            val c = candidates.optJSONObject(i) ?: continue
                            val idx = c.int("candidate_index") ?: i
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(c.str("filename") ?: c.str("title") ?: "(sans nom)", style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                    val meta = listOfNotNull(
                                        c.str("source"), c.long("size")?.takeIf { it > 0 }?.let { formatBytes(it) },
                                        c.str("seeders")?.let { "$it seeds" },
                                    ).joinToString(" · ")
                                    if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = {
                                    launchAction(scope, context, "Téléchargement lancé", then = { scope.launch { load() } }) {
                                        val res = api.postObject("api/auto-acquire/reviews/$id/download", JSONObject().put("candidate_index", idx))
                                        if (res.optBoolean("success", true).not()) throw io.github.gwenvador.bullarr.data.ApiException(res.str("message") ?: "Échec du téléchargement")
                                    }
                                }) { Icon(Icons.Outlined.Download, contentDescription = "Télécharger ce candidat", tint = MaterialTheme.colorScheme.primary) }
                            }
                        }
                    }
                }
            }
        }
    }
}
