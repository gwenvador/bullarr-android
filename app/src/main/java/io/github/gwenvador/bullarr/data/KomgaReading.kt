package io.github.gwenvador.bullarr.data

import org.json.JSONObject

/**
 * Avancement de lecture lu dans Komga par le serveur, comme sur le site : « Continuer la lecture »
 * (GET api/komga/keep-reading), état de chaque tome (GET api/series/<id>/volumes) et
 * synchronisation à la demande (POST api/komga/read-progress/sync).
 * Les pages de Komga sont numérotées à partir de 1.
 */
data class KomgaVolumeProgress(val page: Int, val pages: Int?, val completed: Boolean)

/** Albums commencés dans Komga (ou le lecteur), du plus récent au plus ancien ; null si Komga n'est pas activé. */
suspend fun loadKeepReading(api: BullarrApi, libraryId: Int?): List<RecentRead>? {
    val o = api.getObject("api/komga/keep-reading", libraryId?.let { mapOf("library_id" to it.toString()) } ?: emptyMap())
    if (o.bool("enabled") != true) return null
    return o.optJSONArray("items").objects().mapNotNull { i ->
        val id = i.int("volume_id") ?: return@mapNotNull null
        val page = i.int("page") ?: 0
        RecentRead(
            volumeId = id, seriesId = i.int("series_id"), seriesTitle = i.str("series_title"), label = i.str("label"), title = null,
            page = (page - 1).coerceAtLeast(0), total = i.int("pages_count") ?: page.coerceAtLeast(1), at = 0L,
            coverPath = i.str("cover_path"),
        )
    }
}

/** Relit tout de suite l'avancement dans Komga (sinon le serveur le fait toutes les 15 minutes). */
suspend fun syncKomgaReading(api: BullarrApi) { api.postObject("api/komga/read-progress/sync", JSONObject()) }

/** État Komga des tomes d'une série, par identifiant de tome ; seuls les tomes lus ou commencés y figurent. */
suspend fun loadSeriesKomgaProgress(api: BullarrApi, seriesId: Int): Map<Int, KomgaVolumeProgress> =
    api.getArray("api/series/$seriesId/volumes").objects().mapNotNull { v ->
        val id = v.int("id") ?: return@mapNotNull null
        val completed = (v.int("komga_read_completed") ?: 0) != 0
        val page = v.int("komga_read_page") ?: 0
        if (!completed && page <= 0) null
        else id to KomgaVolumeProgress(page, v.int("komga_pages_count") ?: v.int("page_count"), completed)
    }.toMap()
