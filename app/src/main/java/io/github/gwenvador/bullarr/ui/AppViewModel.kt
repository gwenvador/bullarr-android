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
import io.github.gwenvador.bullarr.data.int
import io.github.gwenvador.bullarr.data.parseLibraries
import io.github.gwenvador.bullarr.data.parseSeriesList
import io.github.gwenvador.bullarr.data.SessionAuth
import io.github.gwenvador.bullarr.data.readable
import kotlinx.coroutines.launch

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val settings = Settings(app)

    var api: BullarrApi? by mutableStateOf(buildApi())
        private set

    private fun buildApi(): BullarrApi? = io.github.gwenvador.bullarr.data.apiFromSettings(settings)

    /** Enregistre le serveur et le mode d'accès ; [cookie] = session déjà ouverte (identifiant ou SSO). */
    fun save(url: String, key: String, mode: String = "key", user: String = "", pass: String = "", cookie: String = "") {
        settings.serverUrl = url.trim()
        settings.apiKey = if (mode == "key") key.trim() else ""
        settings.authMode = mode
        settings.username = user.trim()
        settings.password = pass
        settings.sessionCookie = cookie
        api = buildApi()
        libraries = emptyList(); series = emptyList(); libraryError = null; selectedLibrary = null
    }

    /** Oublie la clé, la session et les identifiants (et prévient Bullarr si une session était ouverte). */
    fun logout() {
        val old = api
        val hadSession = settings.authMode != "key" && settings.sessionCookie.isNotBlank()
        settings.lastUrl = settings.serverUrl
        if (hadSession && old != null) {
            val cookie = settings.sessionCookie
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    BullarrApi.client.newCall(okhttp3.Request.Builder().url(old.absoluteUrl("logout")).header("Cookie", cookie).build()).execute().close()
                }
            }
        }
        runCatching { android.webkit.CookieManager.getInstance().removeAllCookies(null) }
        settings.serverUrl = ""; settings.apiKey = ""; settings.authMode = "key"
        settings.username = ""; settings.password = ""; settings.sessionCookie = ""
        api = null
        libraries = emptyList(); series = emptyList(); libraryError = null; selectedLibrary = null
    }

    /** Connexion SSO par navigateur : état affiché sur l'écran de connexion. */
    var ssoBusy by mutableStateOf(false)
    var ssoError by mutableStateOf<String?>(null)

    /** Appelé quand le navigateur renvoie bullarr://auth?code=… : échange le code et ouvre la session. */
    fun completeSso(code: String) {
        val url = settings.serverUrl.ifBlank { settings.lastUrl }
        if (url.isBlank()) { ssoError = "URL du serveur inconnue : saisissez-la puis relancez le SSO."; return }
        ssoBusy = true; ssoError = null
        viewModelScope.launch {
            try {
                val probe = BullarrApi(url, "", SessionAuth(""))
                val cookie = probe.exchangeAppCode(code)
                val check = BullarrApi(url, "", SessionAuth(cookie))
                if (check.getObject("api/auth/me").optJSONObject("user") == null) throw io.github.gwenvador.bullarr.data.ApiException("Le serveur n'a pas reconnu la session SSO.")
                save(url, "", "oidc", cookie = cookie)
            } catch (e: Exception) {
                ssoError = e.readable()
            } finally {
                ssoBusy = false
            }
        }
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
    /** Portée propre à l'app (pas au ViewModel) : les téléchargements survivent à la fermeture de l'écran. */
    val offline = io.github.gwenvador.bullarr.data.OfflineManager(
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate),
        { api }, io.github.gwenvador.bullarr.data.OfflineStore(app),
    ).also { io.github.gwenvador.bullarr.data.DownloadService.manager = it }

    init {
        // Démarre le service au premier plan dès qu'un téléchargement tourne.
        viewModelScope.launch {
            androidx.compose.runtime.snapshotFlow { offline.active.values.any { it.error == null && !it.paused } }
                .collect { running -> if (running) io.github.gwenvador.bullarr.data.DownloadService.start(app) }
        }
    }

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

    /** Séries ajoutées depuis Bédéthèque pendant la session (url) : restent « ajoutées » même si le serveur ne le sait pas encore. */
    var addedBedethequeUrls by mutableStateOf(setOf<String>()); private set
    fun markAdded(url: String) { addedBedethequeUrls = addedBedethequeUrls + url }
    var libraryLoading by mutableStateOf(false); private set
    var libraryError by mutableStateOf<String?>(null); private set
    /** Validations manuelles en attente (badge de l'onglet Suivi). */
    var reviewCount by androidx.compose.runtime.mutableIntStateOf(0)
    var importCount by androidx.compose.runtime.mutableIntStateOf(0)
    var newsCount by androidx.compose.runtime.mutableIntStateOf(0)
    var activityCount by androidx.compose.runtime.mutableIntStateOf(0)
    private val badgePrefs = app.getSharedPreferences("badges", android.content.Context.MODE_PRIVATE)

    /** Nouveautés : comme sur le web, « nouveau » = arrivé depuis la dernière ouverture de l'onglet. */
    fun markNewsSeen() {
        badgePrefs.edit().putString("newsSeen", java.time.Instant.now().toString()).apply()
        newsCount = 0
    }

    /** Pastilles des onglets de Suivi (mêmes sources que les badges du web : nav.js). */
    suspend fun refreshBadges() {
        val client = api ?: return
        try { reviewCount = client.getObject("api/auto-acquire/reviews/count").int("count") ?: 0 } catch (_: Exception) {}
        try { importCount = client.getObject("api/import/pending-count").int("waiting_file_count") ?: 0 } catch (_: Exception) {}
        try {
            val since = badgePrefs.getString("newsSeen", null) ?: java.time.Instant.now().toString().also { badgePrefs.edit().putString("newsSeen", it).apply() }
            newsCount = client.getObject("api/ebdz/nouveautes/new-count", mapOf("since" to since)).int("count") ?: 0
        } catch (_: Exception) {}
        try {
            val st = io.github.gwenvador.bullarr.data.parseActivity(client.getObject("api/activity/status"))
            activityCount = st.clients.sumOf { it.items.size } + st.pending.size
        } catch (_: Exception) {}
    }
    var query by mutableStateOf("")
    var filters by mutableStateOf(LibraryFilters())
    /** Séries dans l'ordre affiché par la bibliothèque : le glissement passe à la voisine. */
    var visibleSeries: List<SeriesSummary> = emptyList()
    private val libPrefs = app.getSharedPreferences("library", android.content.Context.MODE_PRIVATE)
    /** Tri de la bibliothèque, conservé entre les ouvertures (par défaut : date, plus récent d'abord). */
    var sort by mutableStateOf(LibrarySort.entries.firstOrNull { it.name == libPrefs.getString("sort", null) } ?: LibrarySort.Date)
        private set
    var sortDesc by mutableStateOf(libPrefs.getBoolean("sortDesc", LibrarySort.Date.defaultDesc))
        private set
    /** Bibliothèque en grille de couvertures plutôt qu'en liste ; conservé entre les ouvertures. */
    var libraryGrid by mutableStateOf(libPrefs.getBoolean("grid", false))
        private set
    fun setLibraryGrid(on: Boolean) { libraryGrid = on; libPrefs.edit().putBoolean("grid", on).apply() }
    fun setSort(o: LibrarySort, desc: Boolean) {
        sort = o; sortDesc = desc
        libPrefs.edit().putString("sort", o.name).putBoolean("sortDesc", desc).apply()
    }

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

    /** Met à jour une ligne de la bibliothèque après une modification faite sur sa fiche, sans recharger. */
    fun updateSeriesRow(id: Int, change: (SeriesSummary) -> SeriesSummary) {
        series = series.map { if (it.id == id) change(it) else it }
    }

    fun selectLibrary(lib: Library) {
        if (lib.id == selectedLibrary?.id) return
        series = emptyList()
        loadLibrary(force = false, library = lib)
    }
}
