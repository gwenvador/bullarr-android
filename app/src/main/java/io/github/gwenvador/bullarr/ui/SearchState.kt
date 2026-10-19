package io.github.gwenvador.bullarr.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.gwenvador.bullarr.data.BullarrApi
import io.github.gwenvador.bullarr.data.SEARCH_SOURCES
import io.github.gwenvador.bullarr.data.SearchResult
import io.github.gwenvador.bullarr.data.parseSearchResults
import io.github.gwenvador.bullarr.data.readable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Tome visé quand la recherche part d'une fiche série : rattache le téléchargement à ce tome. */
data class SearchContext(
    val seriesId: Int,
    val seriesTitle: String,
    val volumeId: Int?,
    val volumeLabel: String?,
    val volumeNumber: Int?,
    val isIntegral: Boolean = false,
    val isHs: Boolean = false,
    val isEpisode: Boolean = false,
)

sealed interface SourceState {
    data object Loading : SourceState
    data class Done(val results: List<SearchResult>) : SourceState
    data class Failed(val message: String) : SourceState
}

class SearchState(private val scope: CoroutineScope, private val api: () -> BullarrApi?) {
    var title by mutableStateOf("")
    var volume by mutableStateOf("")
    var context by mutableStateOf<SearchContext?>(null)
    val enabledSources = mutableStateMapOf<String, Boolean>().apply { SEARCH_SOURCES.keys.forEach { put(it, true) } }
    val sources = mutableStateMapOf<String, SourceState>()
    /** Sections repliées dans la liste des résultats (gardé d'une recherche à l'autre). */
    val collapsed = mutableStateMapOf<String, Boolean>()
    var lastQuery by mutableStateOf<String?>(null); private set
    private var jobs = emptyList<Job>()

    fun prefill(ctx: SearchContext) {
        context = ctx
        title = ctx.seriesTitle
        volume = ctx.volumeNumber?.toString() ?: ""
        search()
    }

    fun clearContext() { context = null }

    fun search() {
        val client = api() ?: return
        val t = title.trim()
        if (t.isEmpty()) return
        jobs.forEach { it.cancel() }
        sources.clear()
        val vol = volume.trim().toIntOrNull()
        val ctx = context
        lastQuery = listOfNotNull(t, vol?.let { "tome $it" }).joinToString(" · ")
        jobs = SEARCH_SOURCES.keys.filter { enabledSources[it] == true }.map { source ->
            sources[source] = SourceState.Loading
            scope.launch {
                val body = JSONObject()
                    .put("title", t)
                    .put("volume_num", vol ?: JSONObject.NULL)
                    .put("sources", org.json.JSONArray().put(source))
                if (ctx != null) {
                    body.put("series_id", ctx.seriesId)
                        .put("is_integral", ctx.isIntegral).put("is_hs", ctx.isHs).put("is_episode", ctx.isEpisode)
                }
                sources[source] = try {
                    // Tomes confirmés d'abord ; l'ordre du serveur est gardé dans chaque groupe.
                    SourceState.Done(parseSearchResults(client.postObject("api/missing-monitor/search", body)).sortedBy { it.unconfirmed != null })
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    SourceState.Failed(e.readable())
                }
            }
        }
    }
}
