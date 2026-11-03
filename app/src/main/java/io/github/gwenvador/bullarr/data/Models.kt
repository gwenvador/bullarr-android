package io.github.gwenvador.bullarr.data

import org.json.JSONArray
import org.json.JSONObject

// Formats relevés dans blueprints/*/routes.py de Bullarr (voir README).

fun JSONObject.str(key: String): String? =
    if (!has(key) || isNull(key)) null else opt(key)?.toString()?.ifBlank { null }

fun JSONObject.int(key: String): Int? =
    if (!has(key) || isNull(key)) null else when (val v = opt(key)) {
        is Number -> v.toInt()
        is String -> v.toDoubleOrNull()?.toInt()
        else -> null
    }

fun JSONObject.long(key: String): Long? =
    if (!has(key) || isNull(key)) null else when (val v = opt(key)) {
        is Number -> v.toLong()
        is String -> v.toDoubleOrNull()?.toLong()
        else -> null
    }

fun JSONObject.dbl(key: String): Double? =
    if (!has(key) || isNull(key)) null else when (val v = opt(key)) {
        is Number -> v.toDouble()
        is String -> v.toDoubleOrNull()
        else -> null
    }

/** Booléen SQLite (0/1), JSON ou null. */
fun JSONObject.bool(key: String): Boolean? =
    if (!has(key) || isNull(key)) null else when (val v = opt(key)) {
        is Boolean -> v
        is Number -> v.toInt() != 0
        is String -> v == "1" || v.equals("true", true)
        else -> null
    }

fun JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

/** Libellé d'un tome : Tome 3, Intégrale 2, HS 1, Épisode 4… (même logique que l'interface web). */
fun volumeLabel(o: JSONObject): String {
    fun n(key: String) = o.str(key)?.removeSuffix(".0") ?: ""
    return when {
        o.bool("is_integral") == true -> "Intégrale ${n("integral_number")}".trim()
        o.bool("is_hs") == true -> "HS ${n("hs_number")}".trim()
        o.bool("is_episode") == true -> "Épisode ${n("episode_number")}".trim()
        o.bool("is_special") == true -> o.str("special_label") ?: "Spécial"
        o.str("volume_number") != null -> "Tome ${n("volume_number")}${o.str("bis_suffix") ?: ""}"
        o.bool("is_oneshot") == true -> "One-shot"
        else -> ""
    }
}

// --- Activité ---------------------------------------------------------------

data class DownloadItem(
    val clientId: String?,
    val name: String,
    val progress: Double,
    val state: String?,
    val sizeBytes: Long?,
    val downloadedBytes: Long?,
    val speed: Long?,
    val eta: Long?,
    val trackingId: Int?,
    val seriesTitle: String?,
    val volume: String,
    val priority: Int,
)

data class ClientStatus(val client: String, val label: String, val error: String?, val items: List<DownloadItem>)

data class PendingDownload(
    val id: Int,
    val title: String,
    val client: String,
    val status: String,
    val seriesTitle: String?,
    val volume: String,
    val bytesDownloaded: Long?,
    val bytesTotal: Long?,
    val paused: Boolean,
    val isPack: Boolean,
    val exhausted: Boolean,
    val createdAt: String?,
    val localCopy: String?,
    val clientItemId: String?,
)

data class ActivityStatus(val clients: List<ClientStatus>, val pending: List<PendingDownload>)

fun parseActivity(o: JSONObject): ActivityStatus {
    val clients = o.optJSONArray("clients").objects().map { c ->
        ClientStatus(
            client = c.str("client") ?: "?",
            label = c.str("label") ?: c.str("client") ?: "?",
            error = c.str("error"),
            items = c.optJSONArray("items").objects().map { i ->
                DownloadItem(
                    clientId = i.str("id"),
                    name = i.str("name") ?: "(sans nom)",
                    progress = i.dbl("progress") ?: 0.0,
                    state = i.str("state"),
                    sizeBytes = i.long("size_bytes"),
                    downloadedBytes = i.long("downloaded_bytes"),
                    speed = i.long("speed_bytes_s"),
                    eta = i.long("eta_seconds"),
                    trackingId = i.int("tracking_id"),
                    seriesTitle = i.str("series_title"),
                    volume = volumeLabel(i),
                    priority = i.int("priority") ?: 0,
                )
            },
        )
    }
    val pending = o.optJSONArray("pending").objects().map { p ->
        val localCopy = p.optJSONObject("local_copy")?.str("state")
        PendingDownload(
            id = p.int("id") ?: -1,
            title = p.str("title") ?: "(sans titre)",
            client = p.str("client") ?: "?",
            status = p.str("status") ?: "pending",
            seriesTitle = p.str("series_title"),
            volume = volumeLabel(p),
            bytesDownloaded = p.long("bytes_downloaded"),
            bytesTotal = p.long("bytes_total"),
            paused = p.bool("paused") == true,
            isPack = p.bool("is_pack") == true,
            exhausted = p.bool("exhausted") == true,
            createdAt = p.str("created_at"),
            localCopy = localCopy,
            clientItemId = p.str("client_item_id"),
        )
    }
    return ActivityStatus(clients, pending)
}

// --- Bibliothèque -----------------------------------------------------------

data class Library(val id: Int, val name: String, val seriesCount: Int?, val volumesCount: Int?)

fun parseLibraries(a: JSONArray): List<Library> = a.objects().map {
    Library(it.int("id") ?: -1, it.str("name") ?: "Bibliothèque", it.int("series_count"), it.int("volumes_count"))
}

data class SeriesSummary(
    val id: Int,
    val title: String,
    val author: String?,
    val year: String?,
    val coverPath: String?,
    val ownedTomes: Int,
    val totalKnown: Int?,
    val missingCount: Int,
    val isOneshot: Boolean,
    val searchKey: String,
    /** Champs de filtre de la bibliothèque. */
    val isIntegral: Boolean = false,
    val origin: String? = null,
    val yearInt: Int? = null,
    /** « yyyy-MM-dd HH:mm:ss » du dernier scan, tel que Bullarr le renvoie. */
    val lastScanned: String? = null,
    /** Tomes, intégrales, hors-séries… possédés. */
    val volumeCount: Int = 0,
    /** Taille totale des fichiers, si Bullarr la renvoie (`total_size`). */
    val sizeBytes: Long? = null,
    /** Nombre de votes Bédéthèque (`total_votes`). */
    val votes: Int = 0,
    /** Genres (humour, fantasy…) : `local_genre`, plusieurs valeurs séparées par des virgules. */
    val genres: List<String> = emptyList(),
    /** Tags libres de la série (minuscules, tirets), comme sur le site. */
    val tags: List<String> = emptyList(),
    /** Listes personnelles : « à lire plus tard » et « favori ». */
    val toReadLater: Boolean = false,
    val isFavorite: Boolean = false,
)

/** Anciens (Europe, USA, Asie) et nouveaux (Franco-Belge, Comics, Manga) libellés d'origine de Bédéthèque → un seul jeu. */
fun canonicalOrigin(raw: String?): String? = when (raw?.trim()?.lowercase()) {
    null, "" -> null
    "franco-belge", "europe" -> "Europe"
    "comics", "usa" -> "USA"
    "manga", "asie" -> "Asie"
    else -> raw.trim()
}

fun coverOf(o: JSONObject): String? =
    o.str("local_cover_path") ?: o.str("bedetheque_cover_path") ?: o.str("komga_cover_path")

/** Tableau JSON de chaînes (tags) ; absent ou vide donne une liste vide. */
fun JSONObject.strings(key: String): List<String> =
    optJSONArray(key)?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() } }.orEmpty()

fun parseSeriesList(a: JSONArray): List<SeriesSummary> = a.objects().map {
    val title = it.str("title") ?: "(sans titre)"
    val author = it.str("local_author")
    val tags = it.strings("tags")
    SeriesSummary(
        id = it.int("id") ?: -1,
        title = title,
        author = author,
        year = it.str("year"),
        coverPath = coverOf(it),
        ownedTomes = it.int("owned_tomes") ?: 0,
        totalKnown = it.int("bedetheque_total_volumes"),
        missingCount = it.optJSONArray("missing_volumes")?.length() ?: 0,
        isOneshot = it.bool("is_oneshot") == true,
        // Un tag se cherche avec ou sans tiret : « chat-surdoue » trouve aussi « chat surdoué ».
        searchKey = normalize("$title ${author ?: ""} ${it.str("universe_name") ?: ""} ${tags.joinToString(" ") { t -> "$t ${t.replace('-', ' ')}" }}"),
        isIntegral = it.bool("oneshot_is_integral") == true,
        origin = canonicalOrigin(it.str("bedetheque_origin")),
        yearInt = it.str("year")?.toIntOrNull(),
        lastScanned = it.str("last_scanned"),
        volumeCount = listOf("owned_tomes", "owned_integrals", "owned_hs", "owned_episodes", "owned_specials").sumOf { k -> it.int(k) ?: 0 },
        sizeBytes = it.long("total_size"),
        votes = it.int("total_votes") ?: 0,
        genres = it.str("local_genre")?.split(',')?.map { g -> g.trim() }?.filter { g -> g.isNotEmpty() }.orEmpty(),
        tags = tags,
        toReadLater = it.bool("to_read_later") == true,
        isFavorite = it.bool("is_favorite") == true,
    )
}

/** Minuscules sans accents, pour une recherche tolérante. */
fun normalize(s: String): String =
    java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase()

data class VolumeRow(
    val id: Int,
    val label: String,
    val title: String?,
    val owned: Boolean,
    val coverPath: String?,
    val fileSize: Long?,
    val pageCount: Int?,
    val format: String?,
    val year: String?,
    val filename: String?,
    val number: Int?,
    val isIntegral: Boolean,
    val isHs: Boolean,
    val isEpisode: Boolean,
    val isSpecial: Boolean,
    /** ComicInfo du tome (titre, résumé, auteurs, éditeur, note…), tel que Bullarr le renvoie. */
    val comicInfo: JSONObject?,
    val isbn: String?,
    val author: String?,
    val komgaUrl: String?,
)

data class SeriesDetail(
    val id: Int,
    val title: String,
    val libraryName: String?,
    val coverPath: String?,
    val summary: String?,
    val status: String?,
    val authors: String?,
    val publishers: String?,
    val genre: String?,
    val years: String?,
    val totalKnown: Int?,
    val monitored: Boolean,
    val completeReason: String?,
    val isOneshot: Boolean,
    val bedethequeUrl: String?,
    val komgaUrl: String?,
    val ebdzUrl: String?,
    /** Fiche Bédéthèque de chaque auteur (nom → URL) : ouvre ses autres séries. */
    val authorLinks: Map<String, String>,
    val tags: List<String>,
    val toReadLater: Boolean,
    val isFavorite: Boolean,
    val volumes: List<VolumeRow>,
)

fun parseSeriesDetail(o: JSONObject): SeriesDetail {
    val bd = o.optJSONObject("bedetheque") ?: JSONObject()
    val authors = listOfNotNull(o.str("manual_author"), bd.str("scenaristes"), bd.str("dessinateurs"))
        .flatMap { it.split(",") }.map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("<") }
        .distinct().take(8).joinToString(", ").ifBlank { o.str("local_author") }
    val start = o.str("manual_year_start") ?: bd.str("year_start") ?: o.str("local_year")
    val end = o.str("manual_year_end") ?: bd.str("year_end")
    val volumes = o.optJSONArray("volumes").objects().map { v ->
        val ci = v.optJSONObject("comicinfo")
        VolumeRow(
            id = v.int("id") ?: -1,
            label = volumeLabel(v).ifBlank { "Tome" },
            title = ci?.str("title") ?: v.str("bedetheque_title"),
            owned = v.str("filepath") != null && v.bool("filesystem_present") != false,
            coverPath = v.str("cover_path"),
            fileSize = v.long("file_size"),
            pageCount = v.int("page_count"),
            format = v.str("format"),
            year = v.str("year"),
            filename = v.str("filename"),
            number = when {
                v.bool("is_integral") == true -> v.int("integral_number")
                v.bool("is_hs") == true -> v.int("hs_number")
                v.bool("is_episode") == true -> v.int("episode_number")
                else -> v.int("volume_number")
            },
            isIntegral = v.bool("is_integral") == true,
            isHs = v.bool("is_hs") == true,
            isEpisode = v.bool("is_episode") == true,
            isSpecial = v.bool("is_special") == true,
            comicInfo = ci,
            isbn = v.str("isbn"),
            author = v.str("author"),
            komgaUrl = v.str("komga_book_url"),
        )
    }
    return SeriesDetail(
        id = o.int("id") ?: -1,
        title = o.str("title") ?: "(sans titre)",
        libraryName = o.optJSONObject("library")?.str("name"),
        coverPath = coverOf(o) ?: bd.str("cover_path") ?: o.optJSONObject("komga")?.str("cover_path"),
        summary = o.str("manual_summary") ?: bd.str("description") ?: o.str("local_summary"),
        status = o.str("manual_status") ?: bd.str("status"),
        authors = authors,
        publishers = bd.str("editeurs"),
        genre = o.str("manual_genre") ?: bd.str("genre") ?: o.str("local_genre"),
        years = when {
            start != null && end != null && start != end -> "$start – $end"
            else -> start
        },
        totalKnown = bd.int("total_volumes"),
        monitored = o.bool("monitored") == true,
        completeReason = o.str("bedetheque_complete_reason"),
        isOneshot = o.bool("is_oneshot") == true,
        bedethequeUrl = bd.str("url") ?: o.str("bedetheque_url"),
        komgaUrl = o.optJSONObject("komga")?.str("url"),
        ebdzUrl = o.optJSONObject("ebdz")?.str("thread_url"),
        authorLinks = bd.optJSONObject("author_links").let { links ->
            buildMap { links?.keys()?.forEach { name -> put(name, links.optString(name)) } }.filterValues { it.isNotBlank() }
        },
        tags = o.strings("tags"),
        toReadLater = o.bool("to_read_later") == true,
        isFavorite = o.bool("is_favorite") == true,
        volumes = volumes,
    )
}

// --- Import -----------------------------------------------------------------

data class ImportProgress(
    val processingFile: String?,
    val elapsedSeconds: Long?,
    val startedAt: String?,
    val claimedFiles: List<String>,
    val paused: Boolean,
)

fun parseImportProgress(o: JSONObject): ImportProgress {
    val cur = o.optJSONObject("currently_processing")
    val claimed = o.optJSONArray("importing_filepaths")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList()
    return ImportProgress(
        processingFile = cur?.str("filename"),
        elapsedSeconds = cur?.long("elapsed_seconds"),
        startedAt = cur?.str("started_at"),
        claimedFiles = claimed,
        paused = o.bool("import_paused") == true,
    )
}

/** Fichier en attente d'import : [trackingId] = téléchargement suivi, sans lui rien ne peut être fait depuis l'app. */
data class WaitingFile(val key: String, val filename: String, val trackingId: Int?)

data class ImportPendingCount(val waitingFiles: Int, val awaitingDiscovery: Int, val waiting: List<WaitingFile> = emptyList())

fun parsePendingCount(o: JSONObject) = ImportPendingCount(
    o.int("waiting_file_count") ?: 0,
    o.int("awaiting_discovery_count") ?: 0,
    // Fichiers en attente d'import (état « waiting » de Bullarr), agissables depuis l'Activité.
    o.optJSONArray("items").objects().map { WaitingFile(it.str("item_key") ?: it.str("source_path") ?: "", it.str("filename") ?: "(sans nom)", it.int("tracking_id")) },
)

// --- Formatage --------------------------------------------------------------

fun formatBytes(b: Long?): String? {
    if (b == null || b <= 0) return null
    val units = listOf("o", "Ko", "Mo", "Go", "To")
    var v = b.toDouble(); var i = 0
    while (v >= 1024 && i < units.size - 1) { v /= 1024; i++ }
    return if (i == 0) "$b o" else String.format(java.util.Locale.FRANCE, "%.1f %s", v, units[i])
}

fun formatDuration(sec: Long?): String? {
    if (sec == null || sec < 0) return null
    val h = sec / 3600; val m = (sec % 3600) / 60; val s = sec % 60
    return when {
        h > 0 -> "${h} h ${m} min"
        m > 0 -> "${m} min ${s} s"
        else -> "${s} s"
    }
}

// --- Recherche --------------------------------------------------------------
// POST /api/missing-monitor/search, une requête par source (comme la page web, chaque
// source s'affiche dès qu'elle répond au lieu d'attendre la plus lente).

val SEARCH_SOURCES = linkedMapOf(
    "prowlarr" to "Prowlarr",
    "ebdz" to "EBDZ",
    "telegram" to "Telegram",
    "fourtoutici" to "Fourtoutici",
    "vk" to "VK",
    "annas_archive" to "Anna's Archive",
)

data class SearchResult(
    val source: String,
    val name: String,
    val sizeBytes: Long?,
    val volumeLabel: String?,
    val volumeNumber: Int?,
    val unconfirmed: String?,
    val detail: String?,
    val sourceLink: String?,
    val raw: JSONObject,
) {
    /** Seeders (Prowlarr) : tri et départage. */
    val seeders: Int? get() = raw.int("seeders")
    /** Date de publication (Prowlarr, VK), au format AAAA-MM-JJ. */
    val date: String? get() = (raw.str("publish_date") ?: raw.str("date"))?.take(10)?.ifBlank { null }
    /** Format lu dans le nom (CBZ, CBR, PDF, EPUB) : filtre. */
    val format: String? get() = Regex("\\b(cbz|cbr|pdf|epub)\\b", RegexOption.IGNORE_CASE).find(name)?.value?.uppercase()
    /** Lien sur lequel le serveur reconnaît un ajout (torrent, ed2k, VK…). */
    val downloadLink: String? get() = raw.str("download_url") ?: raw.str("link")
}

private fun decodeName(s: String): String = runCatching { java.net.URLDecoder.decode(s.replace("+", "%2B"), "UTF-8") }.getOrDefault(s)

fun parseSearchResults(o: JSONObject): List<SearchResult> = o.optJSONArray("results").objects().map { r ->
    val source = r.str("source") ?: "?"
    val name = when (source) {
        "ebdz" -> decodeName(r.str("filename") ?: r.str("title") ?: "")
        "telegram", "fourtoutici", "vk" -> r.str("filename") ?: r.str("title") ?: ""
        else -> r.str("title") ?: r.str("filename") ?: ""
    }.ifBlank { "(sans nom)" }
    val detail = when (source) {
        "prowlarr" -> listOfNotNull(r.str("indexer"), r.int("seeders")?.let { "$it seeders" }, r.str("publish_date")?.take(10)).joinToString(" · ")
        "ebdz" -> r.str("title")
        "telegram" -> r.str("channel_title") ?: r.str("channel")
        "vk" -> listOfNotNull(r.str("topic_title"), r.str("date")?.take(10)).joinToString(" · ")
        else -> null
    }?.ifBlank { null }
    val sourceLink = when (source) {
        "prowlarr", "annas_archive" -> r.str("info_url")
        "telegram" -> if (r.str("channel") != null && r.str("message_id") != null) "https://t.me/${r.str("channel")}/${r.str("message_id")}" else null
        "fourtoutici" -> null
        else -> r.str("thread_url")
    }
    SearchResult(
        source = source, name = name, sizeBytes = r.long("size"),
        volumeLabel = r.str("parsed_volume"), volumeNumber = r.int("volume"),
        unconfirmed = if (r.bool("unconfirmed_volume") == true) r.str("unconfirmed_reason") ?: "Tome non confirmé" else null,
        detail = detail, sourceLink = sourceLink, raw = r,
    )
}
