package io.github.gwenvador.bullarr.data

import org.json.JSONObject
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// Nouveautés : GET /api/ebdz/rss/latest, /api/telegram-channels/latest, /api/ebdz/latest
// (formats relevés dans ebdz/routes.py, telegram_channels/routes.py et ebdz-latest.js).

enum class NewsType(val label: String) { RSS("RSS"), TELEGRAM("Telegram"), EBDZ("EBDZ") }

data class NewsFile(val job: DownloadJob, val volumeLabel: String?, val owned: Boolean)

data class NewsItem(
    val type: NewsType,
    val title: String,
    val subtitle: String?,
    /** Date affichée et clé de tri, « yyyy-MM-dd HH:mm » à l'heure locale. */
    val date: String?,
    val source: String?,
    val sizeBytes: Long?,
    val inLibrary: Boolean,
    val owned: Boolean,
    val downloaded: Boolean,
    val link: String?,
    /** Série Bullarr déjà connue pour cette entrée. */
    val seriesId: Int?,
    /** Titre de série à ajouter (⚡) et fiche Bédéthèque connue. */
    val seriesTitle: String,
    val rawHint: String,
    val bedethequeUrl: String?,
    /** Fichiers téléchargeables (vide : rien à envoyer, ex. flux RSS sans lien). */
    val files: List<NewsFile>,
    /** Lien Prowlarr à retrouver par recherche (release RSS sans lien direct). */
    val prowlarrIndexerId: Int?,
    val raw: JSONObject,
)

private val OUT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

/** ISO avec fuseau (RSS) → heure locale ; sinon on garde les 16 premiers caractères. */
fun localDate(s: String?): String? {
    s ?: return null
    return runCatching { OffsetDateTime.parse(s).atZoneSameInstant(ZoneId.systemDefault()).format(OUT) }
        .getOrElse { s.replace("T", " ").take(16) }
}

private fun isTorrentUrl(url: String): Boolean {
    val u = url.lowercase()
    return u.startsWith("magnet:") || (u.startsWith("http") && (Regex("\\.torrent([?#]|$)").containsMatchIn(u) || "/download" in u))
}

private fun isVkUrl(url: String) = Regex("^https://(m\\.)?vk\\.(com|ru)/doc-?\\d+_\\d+").containsMatchIn(url)

private fun decodeName(s: String): String =
    runCatching { java.net.URLDecoder.decode(s.replace("+", "%2B"), "UTF-8") }.getOrDefault(s)

private fun volumeNumberOf(label: String?): Int? =
    label?.let { Regex("^Tome\\s+(\\d+)$", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1)?.toIntOrNull() }

fun parseRss(o: JSONObject): Pair<List<NewsItem>, List<String>> {
    val items = o.optJSONArray("entries").objects().map { e ->
        val title = e.str("title") ?: "(sans titre)"
        val seriesId = e.int("series_id")
        val link = e.str("page_link") ?: e.str("link") ?: e.str("comments_link")
        val tracking = Tracking(seriesId = seriesId, sourceLink = e.str("link"))
        val urls = e.optJSONArray("download_links").objects().mapNotNull { it.str("url") }
        val vk = e.bool("vk") == true
        val files = when {
            vk -> urls.firstOrNull()?.let { listOf(DownloadJob.Vk(it, e.str("filename") ?: title, tracking)) } ?: emptyList()
            else -> urls.filter { isTorrentUrl(it) }.map { DownloadJob.Torrent(it, title, tracking) } +
                urls.filter { isVkUrl(it) }.map { DownloadJob.Vk(it, e.str("filename") ?: title, tracking) }
        }.map { NewsFile(it, null, e.bool("already_owned") == true) }
        NewsItem(
            type = NewsType.RSS,
            title = title,
            subtitle = e.str("bedetheque_title") ?: e.str("series_title"),
            date = localDate(e.str("date")),
            source = e.str("feed_title") ?: e.str("feed_name"),
            sizeBytes = null,
            inLibrary = e.bool("already_in_library") == true,
            owned = e.bool("already_owned") == true,
            downloaded = e.bool("downloaded") == true,
            link = link,
            seriesId = seriesId,
            seriesTitle = e.str("series_title") ?: e.str("bedetheque_title") ?: title,
            rawHint = title,
            bedethequeUrl = e.str("bedetheque_url"),
            files = files,
            prowlarrIndexerId = e.int("prowlarr_indexer_id"),
            raw = e,
        )
    }
    val errors = o.optJSONArray("errors")?.let { arr ->
        (0 until arr.length()).map { i ->
            when (val e = arr.opt(i)) {
                is JSONObject -> listOfNotNull(e.str("feed") ?: e.str("name"), e.str("error") ?: e.str("message"))
                    .joinToString(" : ").ifBlank { e.toString() }
                else -> e.toString()
            }
        }
    } ?: emptyList()
    return items to errors
}

fun parseTelegram(o: JSONObject): List<NewsItem> = o.optJSONArray("files").objects().map { f ->
    val filename = f.str("filename") ?: "(sans nom)"
    val channel = f.str("channel")?.removePrefix("@")
    val messageId = f.long("message_id")
    val seriesId = f.int("series_id")
    val post = if (channel != null && messageId != null) "https://t.me/$channel/$messageId" else null
    val tracking = Tracking(seriesId = seriesId, volumeNumber = f.int("volume"), sourceLink = post)
    val owned = f.bool("already_owned") == true
    val vol = f.str("parsed_volume")
    NewsItem(
        type = NewsType.TELEGRAM,
        title = filename,
        subtitle = listOfNotNull(f.str("bedetheque_title") ?: f.str("series_title") ?: f.str("parsed_title"), vol)
            .joinToString(" · ").ifBlank { null },
        date = f.str("message_date")?.take(16),
        source = f.str("channel_title") ?: f.str("channel"),
        sizeBytes = f.long("file_size"),
        inLibrary = f.bool("already_in_library") == true,
        owned = owned,
        downloaded = f.bool("downloaded") == true,
        link = post,
        seriesId = seriesId,
        seriesTitle = f.str("series_title") ?: f.str("bedetheque_title") ?: f.str("parsed_title") ?: filename,
        rawHint = filename,
        bedethequeUrl = f.str("bedetheque_url"),
        files = if (channel != null && messageId != null)
            listOf(NewsFile(DownloadJob.Telegram(channel, messageId, f.str("channel_title"), filename, tracking), vol, owned))
        else emptyList(),
        prowlarrIndexerId = null,
        raw = f,
    )
}

fun parseEbdz(o: JSONObject): List<NewsItem> = o.optJSONArray("sessions").objects().flatMap { s ->
    val date = s.str("scraped_at")?.take(16)
    s.optJSONArray("results").objects().map { r ->
        val links = r.optJSONArray("links").objects()
        val seriesId = r.int("matched_series_id")
        val files = links.mapNotNull { l ->
            val ed2k = l.str("link") ?: return@mapNotNull null
            val vol = l.str("parsed_volume")
            NewsFile(
                DownloadJob.Ed2k(ed2k, decodeName(l.str("filename") ?: ed2k), Tracking(seriesId, null, l.int("volume") ?: volumeNumberOf(vol), r.str("url"))),
                vol, l.bool("already_owned") == true,
            )
        }
        val vols = files.mapNotNull { it.volumeLabel }.distinct()
        val filesLine = if (files.isNotEmpty()) "${files.size} fichier(s)" + (if (vols.isNotEmpty()) " : " + vols.take(4).joinToString(", ") else "") else null
        val title = r.str("title") ?: "(sans titre)"
        NewsItem(
            type = NewsType.EBDZ,
            // Titre du fil en gras ; série rattachée et fichiers en dessous.
            subtitle = title,
            title = listOfNotNull(r.str("matched_series_title"), filesLine).joinToString(" · ").ifBlank { "Nouveau fil EBDZ" },
            date = date,
            source = r.str("category") ?: "EBDZ",
            sizeBytes = links.sumOf { it.long("filesize") ?: 0L }.takeIf { it > 0 },
            inLibrary = r.bool("already_in_library") == true || r.bool("already_matched_thread") == true,
            owned = files.isNotEmpty() && files.all { it.owned },
            downloaded = false,
            link = r.str("url"),
            seriesId = seriesId,
            seriesTitle = r.str("matched_series_title") ?: r.str("bedetheque_title") ?: title,
            rawHint = files.firstOrNull()?.job?.name ?: title,
            bedethequeUrl = r.str("matched_bedetheque_url") ?: r.str("bedetheque_url"),
            files = files,
            prowlarrIndexerId = null,
            raw = r,
        )
    }
}
