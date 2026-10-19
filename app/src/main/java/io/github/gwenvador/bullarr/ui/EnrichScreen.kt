package io.github.gwenvador.bullarr.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.gwenvador.bullarr.data.bool
import io.github.gwenvador.bullarr.data.int
import io.github.gwenvador.bullarr.data.objects
import io.github.gwenvador.bullarr.data.parseLibraries
import io.github.gwenvador.bullarr.data.readable
import io.github.gwenvador.bullarr.data.str
import kotlinx.coroutines.launch
import org.json.JSONObject

// Page « Enrichir » de Bullarr (bedetheque-enrich.html) : Indispensables BD, Panthéon,
// Thèmes, Top 100 BDGest, Par auteur. Ajout d'une série : POST /api/bedetheque/add-series.

private val ENRICH_TABS = listOf("Indispensables BD", "Panthéon", "Thèmes", "Top 100 BDGest", "Par auteur")
private val CATEGORIES = listOf("all" to "Tous", "franco-belge" to "Franco-belge", "comics" to "Comics", "manga" to "Manga")
private val PANTHEON_CATEGORIES = CATEGORIES + ("fondateurs" to "Fondateurs")
private val BDGEST_ORIGINS = listOf("general" to "Général", "franco-belge" to "Franco-belge", "manga" to "Manga", "comics" to "Comics")

/** Série affichée dans une liste d'Enrichir, quel que soit l'onglet d'origine. */
private data class Discovery(
    val title: String,
    val meta: String?,
    val summary: String?,
    val cover: String?,
    val rank: Int?,
    val url: String?,
    val seriesId: Int?,
    val inLibrary: Boolean,
)

private fun discovery(o: JSONObject, meta: List<String?>) = Discovery(
    title = o.str("title") ?: "(sans titre)",
    meta = meta.filterNotNull().filter { it.isNotBlank() }.joinToString(" · ").ifBlank { null },
    summary = o.str("summary"),
    cover = o.str("cover"),
    rank = o.int("rank"),
    url = o.str("url") ?: o.str("bedetheque_url"),
    seriesId = o.int("series_id"),
    inLibrary = o.bool("already_in_library") == true || o.bool("already_owned") == true || o.int("series_id") != null,
)

private data class Author(val name: String, val url: String?, val meta: String?, val rank: Int?, val owned: Boolean)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EnrichScreen(vm: AppViewModel, snackbar: SnackbarHostState, onOpenSeries: (Int, String) -> Unit) {
    val api = vm.api ?: return
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var category by rememberSaveable { mutableStateOf("all") }
    var pantheonCategory by rememberSaveable { mutableStateOf("all") }
    var bdgestOrigin by rememberSaveable { mutableStateOf("general") }
    var bdgestYear by rememberSaveable { mutableIntStateOf(java.time.Year.now().value) }
    var authorQuery by rememberSaveable { mutableStateOf("") }
    // Pile de navigation interne : thème ouvert, auteur ouvert.
    var theme by remember { mutableStateOf<Pair<String, String>?>(null) }
    var author by remember { mutableStateOf<Author?>(null) }

    val cache = remember { mutableStateMapOf<String, Any>() }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val added = remember { mutableStateListOf<String>() }
    var adding by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    val focus = LocalFocusManager.current

    // Clé de la vue courante ; chaque vue n'est chargée qu'une fois (tirer pour rafraîchir côté web).
    val key = when {
        author != null -> "author:${author!!.url}"
        theme != null -> "theme:${theme!!.first}"
        tab == 0 -> "ind:$category"
        tab == 1 -> "pan:$pantheonCategory"
        tab == 2 -> "themes"
        tab == 3 -> "bdgest:$bdgestYear:$bdgestOrigin"
        else -> "authors:${authorQuery.trim()}"
    }

    LaunchedEffect(api, key, attempt) {
        if (cache[key] != null) return@LaunchedEffect
        if (key == "authors:") return@LaunchedEffect
        loading = true; error = null
        try {
            cache[key] = when {
                key.startsWith("author:") -> api.getObject("api/bedetheque/authors/albums", mapOf("url" to author!!.url!!))
                    .optJSONArray("albums").objects().map {
                        val years = listOfNotNull(it.str("year_start"), it.str("year_end")).distinct().joinToString(" – ")
                        discovery(it, listOf(years.ifBlank { null }, it.str("flag_country")))
                    }
                key.startsWith("theme:") -> api.getObject("api/bedetheque/theme", mapOf("slug" to theme!!.first))
                    .optJSONArray("items").objects().map { discovery(it, listOf(it.str("authors"), it.str("origin"), it.str("note"))) }
                key.startsWith("ind:") -> api.getObject("api/bedetheque/indispensables", mapOf("category" to category))
                    .optJSONArray("items").objects().map { discovery(it, listOf(it.str("genre"), it.str("category"))) }
                key.startsWith("pan:") -> api.getObject("api/bedetheque/pantheon", mapOf("category" to pantheonCategory))
                    .optJSONArray("items").objects().map {
                        Author(it.str("name") ?: "?", it.str("url"),
                            listOfNotNull(it.str("professions"), it.str("country"), it.str("dates"), it.str("notable_works")).joinToString(" · ").ifBlank { null },
                            it.int("rank"), it.bool("already_owned") == true)
                    }
                key == "themes" -> api.getObject("api/bedetheque/themes").optJSONArray("groups").objects().map { g ->
                    (g.str("name") ?: "") to g.optJSONArray("themes").objects().map { (it.str("slug") ?: "") to (it.str("name") ?: "") }
                }
                key.startsWith("bdgest:") -> api.getObject("api/bdgest/top-annuel", mapOf("annee" to bdgestYear.toString(), "origine" to bdgestOrigin))
                    .optJSONArray("items").objects().map { discovery(it, listOf(it.str("volume_label"), it.str("publisher"), it.str("release_date"), it.str("votes"))) }
                else -> api.getObject("api/bedetheque/authors/search", mapOf("q" to authorQuery.trim()))
                    .optJSONArray("results").objects().map { Author(it.str("name") ?: "?", it.str("url"), null, null, false) }
            }
        } catch (e: Exception) { error = e.readable() }
        loading = false
    }

    fun add(d: Discovery) {
        val url = d.url ?: return
        adding = url
        scope.launch {
            try {
                val lib = parseLibraries(api.getArray("api/libraries")).firstOrNull() ?: throw IllegalStateException("Aucune bibliothèque")
                val o = api.postObject("api/bedetheque/add-series", JSONObject().put("url", url).put("library_id", lib.id))
                added += url
                snackbar.showSnackbar(if (o.bool("already_exists") == true) "« ${d.title} » est déjà dans ${lib.name}." else "« ${d.title} » ajoutée à ${lib.name}.")
            } catch (e: Exception) { snackbar.showSnackbar(e.readable()) }
            adding = null
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ENRICH_TABS.forEachIndexed { i, label ->
                FilterChip(selected = tab == i, onClick = { tab = i; theme = null; author = null }, label = { Text(label) })
            }
        }
        // En-tête de vue : retour quand un thème ou un auteur est ouvert, sinon filtres de l'onglet.
        when {
            author != null || theme != null -> Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (author != null) author = null else theme = null }) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Retour")
                }
                Text(author?.name ?: theme!!.second, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            tab == 0 -> FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CATEGORIES.forEach { (k, l) -> FilterChip(selected = category == k, onClick = { category = k }, label = { Text(l) }) }
            }
            tab == 1 -> FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PANTHEON_CATEGORIES.forEach { (k, l) -> FilterChip(selected = pantheonCategory == k, onClick = { pantheonCategory = k }, label = { Text(l) }) }
            }
            tab == 3 -> Column(Modifier.padding(horizontal = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { bdgestYear-- }, enabled = bdgestYear > 2000) { Icon(Icons.Outlined.ChevronLeft, contentDescription = "Année précédente") }
                    Text("$bdgestYear", style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { bdgestYear++ }, enabled = bdgestYear < java.time.Year.now().value) { Icon(Icons.Outlined.ChevronRight, contentDescription = "Année suivante") }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BDGEST_ORIGINS.forEach { (k, l) -> FilterChip(selected = bdgestOrigin == k, onClick = { bdgestOrigin = k }, label = { Text(l) }) }
                }
            }
            tab == 4 -> {
                var draft by rememberSaveable { mutableStateOf(authorQuery) }
                ClearableField(
                    draft, { draft = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    placeholder = "Nom d'un auteur…",
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); authorQuery = draft }),
                )
            }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp))
        error?.let { ErrorCard(it, Modifier.padding(16.dp), onRetry = { cache.remove(key); attempt++ }) }

        @Suppress("UNCHECKED_CAST")
        val data = cache[key]
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 6.dp, bottom = 16.dp)) {
            when {
                key == "themes" -> (data as? List<Pair<String, List<Pair<String, String>>>>)?.forEach { (group, themes) ->
                    item(key = "g-$group") { Text(group, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 6.dp)) }
                    item(key = "t-$group") {
                        FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            themes.forEach { (slug, name) -> FilterChip(selected = false, onClick = { theme = slug to name }, label = { Text(name) }) }
                        }
                    }
                }
                key.startsWith("pan:") || key.startsWith("authors:") -> {
                    if (key == "authors:") item { EmptyState("Cherchez un auteur", "Sa bibliographie s'affiche, avec les séries en français à ajouter.") }
                    (data as? List<Author>)?.let { list ->
                        if (list.isEmpty()) item { EmptyState("Aucun auteur trouvé") }
                        items(list) { a ->
                            Row(
                                Modifier.fillMaxWidth().clickable(enabled = a.url != null) { author = a }.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                a.rank?.let { Text("$it", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(36.dp)) }
                                Column(Modifier.weight(1f)) {
                                    Text(a.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    a.meta?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                                }
                                if (a.owned) Tag("En bibliothèque", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                                Icon(Icons.Outlined.ChevronRight, contentDescription = "Voir ses séries", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
                else -> (data as? List<Discovery>)?.let { list ->
                    if (list.isEmpty()) item { EmptyState("Aucune série") }
                    items(list) { d ->
                        DiscoveryRow(
                            d, inLibrary = d.inLibrary || d.url in added, busy = adding == d.url,
                            onOpen = {
                                if (d.seriesId != null) onOpenSeries(d.seriesId, d.title)
                                else d.url?.let { runCatching { uri.openUri(it) } }
                            },
                            onAdd = { add(d) },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoveryRow(d: Discovery, inLibrary: Boolean, busy: Boolean, onOpen: () -> Unit, onAdd: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        d.rank?.let { Text("$it", style = MaterialTheme.typography.titleMedium, color = cs.primary, modifier = Modifier.width(36.dp)) }
        if (d.cover != null) {
            Cover(d.cover, 44.dp)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(d.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            d.meta?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            d.summary?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
        when {
            inLibrary -> Tag("En bibliothèque", cs.secondaryContainer, cs.onSecondaryContainer)
            d.url != null -> IconButton(onClick = onAdd, enabled = !busy) { Icon(Icons.Outlined.Add, contentDescription = "Ajouter à la bibliothèque") }
        }
    }
}
