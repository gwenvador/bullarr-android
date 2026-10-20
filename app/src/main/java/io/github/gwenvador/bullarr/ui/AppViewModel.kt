package io.github.gwenvador.bullarr.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.gwenvador.bullarr.data.BullarrApi
import io.github.gwenvador.bullarr.data.Library
import io.github.gwenvador.bullarr.data.SeriesSummary
import io.github.gwenvador.bullarr.data.Settings
import io.github.gwenvador.bullarr.data.parseLibraries
import io.github.gwenvador.bullarr.data.parseSeriesList
import io.github.gwenvador.bullarr.data.readable
import kotlinx.coroutines.launch

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val settings = Settings(app)

    var api: BullarrApi? by mutableStateOf(buildApi())
        private set

    private fun buildApi(): BullarrApi? =
        if (!settings.isConfigured) null
        else runCatching { BullarrApi(settings.serverUrl, settings.apiKey) }.getOrNull()

    fun save(url: String, key: String) {
        settings.serverUrl = url.trim()
        settings.apiKey = key.trim()
        api = buildApi()
        libraries = emptyList(); series = emptyList(); libraryError = null; selectedLibrary = null
    }

    val search = SearchState(viewModelScope) { api }

    /** Incrémenté à la fermeture du lecteur : les listes relisent l'état de lecture. */
    var progressVersion by mutableStateOf(0)
    val reads by lazy { io.github.gwenvador.bullarr.data.ReadingProgress(app) }

    /** Version installée (versionName) et build. */
    val installedVersion: String = runCatching {
        val i = app.packageManager.getPackageInfo(app.packageName, 0)
        "${i.versionName} (build ${i.longVersionCode})"
    }.getOrDefault("inconnue")
    private val installedName: String = runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull() ?: "0"

    var update by mutableStateOf<io.github.gwenvador.bullarr.data.UpdateState>(io.github.gwenvador.bullarr.data.UpdateState.Idle); private set

    fun checkUpdate() {
        update = io.github.gwenvador.bullarr.data.UpdateState.Checking
        viewModelScope.launch {
            update = try {
                val latest = io.github.gwenvador.bullarr.data.UpdateChecker.latest()
                if (latest != null && io.github.gwenvador.bullarr.data.compareVersions(latest.version, installedName) > 0)
                    io.github.gwenvador.bullarr.data.UpdateState.Available(latest)
                else io.github.gwenvador.bullarr.data.UpdateState.UpToDate(latest)
            } catch (e: Exception) {
                io.github.gwenvador.bullarr.data.UpdateState.Failed(e.readable())
            }
        }
    }

    /** Albums copiés pour la lecture hors ligne. */
    val offline = io.github.gwenvador.bullarr.data.OfflineManager(viewModelScope, { api }, io.github.gwenvador.bullarr.data.OfflineStore(app))

    /** Tome ouvert dans le lecteur plein écran, par-dessus toute l'app. */
    var reading by mutableStateOf<ReaderTarget?>(null)

    // Navigation : les 5 rubriques de la barre latérale web, chacune avec ses sous-onglets.
    var section by mutableStateOf(Section.Library)
    val subTab = androidx.compose.runtime.mutableStateMapOf<Section, Int>()
    /** Fiche série ouverte par-dessus la rubrique courante. */
    var openSeries by mutableStateOf<SeriesSummary?>(null)

    fun goToSearch() { openSeries = null; section = Section.Search }

    fun openSeriesById(id: Int, title: String) {
        openSeries = series.firstOrNull { it.id == id }
            ?: SeriesSummary(id, title, null, null, null, 0, null, 0, false, "")
    }
    val downloads = DownloadController(viewModelScope) { api }

    fun coverUrl(path: String?): String? = path?.let { p -> api?.absoluteUrl(p) }

    // Bibliothèque : gardée en mémoire entre les onglets (la liste complète pèse ~1 Mo).
    var libraries by mutableStateOf<List<Library>>(emptyList()); private set
    var selectedLibrary by mutableStateOf<Library?>(null); private set
    var series by mutableStateOf<List<SeriesSummary>>(emptyList()); private set
    var libraryLoading by mutableStateOf(false); private set
    var libraryError by mutableStateOf<String?>(null); private set
    var query by mutableStateOf("")

    fun loadLibrary(force: Boolean = false, library: Library? = selectedLibrary) {
        val client = api ?: return
        if (libraryLoading || (!force && series.isNotEmpty() && library == selectedLibrary)) return
        libraryLoading = true; libraryError = null
        viewModelScope.launch {
            try {
                val libs = if (force || libraries.isEmpty()) parseLibraries(client.getArray("api/libraries")) else libraries
                libraries = libs
                val lib = library?.let { l -> libs.firstOrNull { it.id == l.id } } ?: libs.firstOrNull()
                selectedLibrary = lib
                series = if (lib == null) emptyList() else parseSeriesList(client.getArray("api/library/${lib.id}/series"))
            } catch (e: Exception) {
                libraryError = e.readable()
            } finally {
                libraryLoading = false
            }
        }
    }

    fun selectLibrary(lib: Library) {
        if (lib.id == selectedLibrary?.id) return
        series = emptyList()
        loadLibrary(force = false, library = lib)
    }
}
