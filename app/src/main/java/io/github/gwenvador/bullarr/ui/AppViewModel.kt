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
    init { io.github.gwenvador.bullarr.data.CoverLoader.init(app) }

    // Cache disque de la bibliothèque (liste des bibliothèques et séries de chacune), propre au
    // serveur et au compte : affichée tout de suite au lancement, puis rafraîchie par le réseau.
    private val libCacheDir = java.io.File(app.filesDir, "library_cache").apply { mkdirs() }
    private fun libCacheFile(name: String): java.io.File {
        val who = "${settings.serverUrl}|${settings.authMode}|${settings.username}|${settings.apiKey.hashCode()}"
        val h = java.security.MessageDigest.getInstance("SHA-1").digest(who.toByteArray()).take(8).joinToString("") { "%02x".format(it) }
        return java.io.File(libCacheDir, "${h}_$name.json")
    }
    private fun readLibCache(name: String): org.json.JSONArray? =
        runCatching { libCacheFile(name).takeIf { it.isFile }?.readText()?.let { org.json.JSONArray(it) } }.getOrNull()
    private fun writeLibCache(name: String, a: org.json.JSONArray) { runCatching { libCacheFile(name).writeText(a.toString()) } }
    private fun clearLibCache() {
        libCacheDir.listFiles()?.forEach { it.delete() }
        io.github.gwenvador.bullarr.data.CoverLoader.clearDisk()
    }
    /** Séries déjà chargées, par bibliothèque : revenir sur une bibliothèque est instantané. */
    private val seriesByLibrary = HashMap<Int, List<SeriesSummary>>()
    /** Dernières fiches de série ouvertes (albums compris), pour les rouvrir sans attendre. */
    val seriesDetailCache = object : java.util.LinkedHashMap<Int, io.github.gwenvador.bullarr.data.SeriesDetail>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, io.github.gwenvador.bullarr.data.SeriesDetail>?) = size > 60
    }

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
        seriesByLibrary.clear(); seriesDetailCache.clear()
        libraries = emptyList(); series = emptyList(); libraryError = null; selectedLibrary = null
        role = null; profileName = null; avatarUrl = null
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
        clearLibCache(); seriesByLibrary.clear(); seriesDetailCache.clear()
        settings.serverUrl = ""; settings.apiKey = ""; settings.authMode = "key"
        settings.username = ""; settings.password = ""; settings.sessionCookie = ""
        api = null
        libraries = emptyList(); series = emptyList(); libraryError = null; selectedLibrary = null
        role = null; profileName = null; avatarUrl = null; section = Section.Library
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

    /** « Continuer la lecture » lu dans Komga, gardé d'une page à l'autre ; null tant qu'il n'est pas chargé. */
    var keepReading by mutableStateOf<List<io.github.gwenvador.bullarr.data.RecentRead>?>(null)
    /** Komga désactivé ou serveur injoignable : on montre les albums commencés sur l'appareil. */
    var keepReadingLocal by mutableStateOf(false)
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

    // Compte connecté (/api/auth/me) : rôle, nom et avatar. Un lecteur ne voit que la
    // bibliothèque, ses demandes et quelques réglages (le serveur lui refuse le reste).
    var role by mutableStateOf<String?>(null); private set
    var profileName by mutableStateOf<String?>(null)
    var avatarUrl by mutableStateOf<String?>(null)
    val isReader get() = role == "reader"

    suspend fun loadMe() {
        val client = api ?: return
        runCatching { client.getObject("api/auth/me") }.onSuccess { me ->
            role = me.optString("role").ifBlank { "admin" }
            val p = me.optJSONObject("profile")
            val u = me.optJSONObject("user")
            profileName = p?.optString("name")?.ifBlank { null } ?: u?.optString("name")?.ifBlank { null } ?: u?.optString("email")?.ifBlank { null }
            avatarUrl = p?.optString("avatar_url")?.ifBlank { null }
            if (isReader && section !in sections()) { section = Section.Library; openSeries = null }
        }
    }

    /** Rubriques de la barre du bas selon le rôle. */
    fun sections(): List<Section> =
        if (isReader) listOf(Section.Library, Section.Requests, Section.Config)
        else listOf(Section.Library, Section.Search, Section.Tracking, Section.Actions, Section.Config)

    /** Sous-onglets d'une rubrique selon le rôle (les réglages du serveur sont réservés aux administrateurs). */
    fun subsOf(s: Section): List<String> =
        if (s == Section.Config && isReader) listOf("Profil", "Intégrations", "Apparence", "À propos") else s.subs

    /** Demandes de BD et de comptes à valider (administrateurs). */
    var requestCount by androidx.compose.runtime.mutableIntStateOf(0)

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
        if (isReader) return
        try { requestCount = client.getObject("api/requests/pending-count").int("total") ?: 0 } catch (_: Exception) {}
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
    /** Où cherche la recherche de la bibliothèque : nom de la série (titre, auteur, univers) et/ou tags. */
    var searchInName by mutableStateOf(true)
    var searchInTags by mutableStateOf(true)
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
    fun chooseLibraryGrid(on: Boolean) { libraryGrid = on; libPrefs.edit().putBoolean("grid", on).apply() }
    /** Largeur minimale d'une miniature de la grille (dp), réglée par le curseur ; conservée sur l'appareil. */
    var libraryCellSize by mutableStateOf(libPrefs.getFloat("cellSize", 108f))
        private set
    fun chooseLibraryCellSize(dp: Float) { libraryCellSize = dp; libPrefs.edit().putFloat("cellSize", dp).apply() }
    fun setSort(o: LibrarySort, desc: Boolean) {
        sort = o; sortDesc = desc
        libPrefs.edit().putString("sort", o.name).putBoolean("sortDesc", desc).apply()
    }

    fun loadLibrary(force: Boolean = false, library: Library? = selectedLibrary) {
        val client = api ?: return
        if (libraryLoading || (!force && series.isNotEmpty() && library == selectedLibrary)) return
        // Affichage immédiat depuis le cache (mémoire, sinon disque), puis rafraîchissement réseau.
        if (libraries.isEmpty()) readLibCache("libraries")?.let { runCatching { libraries = parseLibraries(it) } }
        val target = library?.let { l -> libraries.firstOrNull { it.id == l.id } } ?: libraries.firstOrNull()
        if (target != null && series.isEmpty()) {
            selectedLibrary = target
            (seriesByLibrary[target.id] ?: readLibCache("series_${target.id}")?.let { runCatching { parseSeriesList(it) }.getOrNull() })
                ?.let { series = it; seriesByLibrary[target.id] = it }
        }
        libraryLoading = true; libraryError = null
        viewModelScope.launch {
            try {
                val libs = run {
                    client.getArray("api/libraries").also { writeLibCache("libraries", it) }.let { parseLibraries(it) }
                }
                libraries = libs
                val lib = library?.let { l -> libs.firstOrNull { it.id == l.id } } ?: libs.firstOrNull()
                if (lib?.id != selectedLibrary?.id) series = emptyList()
                selectedLibrary = lib
                if (lib == null) series = emptyList() else {
                    val raw = client.getArray("api/library/${lib.id}/series")
                    writeLibCache("series_${lib.id}", raw)
                    val fresh = parseSeriesList(raw)
                    seriesByLibrary[lib.id] = fresh
                    // L'utilisateur a pu changer de bibliothèque pendant le chargement.
                    if (selectedLibrary?.id == lib.id) series = fresh
                }
            } catch (e: Exception) {
                // Hors réseau avec un cache : on garde la liste en cache sans bandeau d'erreur bloquant.
                if (series.isEmpty()) libraryError = e.readable()
            } finally {
                libraryLoading = false
            }
        }
    }

    /** Met à jour une ligne de la bibliothèque après une modification faite sur sa fiche, sans recharger. */
    fun updateSeriesRow(id: Int, change: (SeriesSummary) -> SeriesSummary) {
        series = series.map { if (it.id == id) change(it) else it }
        selectedLibrary?.let { seriesByLibrary[it.id] = series }
    }

    fun selectLibrary(lib: Library) {
        if (lib.id == selectedLibrary?.id) return
        series = emptyList()
        // Affiche aussitôt la version en cache puis la rafraîchit.
        loadLibrary(force = true, library = lib)
    }
}
