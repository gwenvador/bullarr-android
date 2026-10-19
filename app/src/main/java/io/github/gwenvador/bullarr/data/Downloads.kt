package io.github.gwenvador.bullarr.data

import org.json.JSONObject

/** Où rattacher un téléchargement dans Bullarr (série/tome), comme le font les pages web. */
data class Tracking(
    val seriesId: Int? = null,
    val volumeId: Int? = null,
    val volumeNumber: Int? = null,
    val sourceLink: String? = null,
)

/**
 * Un fichier à envoyer. Les corps reprennent ceux de search.js / ebdz-latest.js :
 * addTorrentToClient, addToEmule, downloadTelegramFile, downloadFourtoutici, downloadVkResult.
 */
sealed interface DownloadJob {
    val name: String
    val tracking: Tracking
    /** Clé qui identifie le fichier pour marquer « Envoyé » dans l'app. */
    val key: String

    data class Torrent(val url: String, override val name: String, override val tracking: Tracking) : DownloadJob {
        override val key get() = url
    }
    data class Ed2k(val link: String, override val name: String, override val tracking: Tracking) : DownloadJob {
        override val key get() = link
    }
    data class Telegram(val channel: String, val messageId: Long, val channelTitle: String?, override val name: String, override val tracking: Tracking) : DownloadJob {
        override val key get() = "tg:$channel/$messageId"
    }
    data class Fourtoutici(val fileId: String, override val name: String, override val tracking: Tracking) : DownloadJob {
        override val key get() = "4t:$fileId"
    }
    data class Vk(val url: String, override val name: String, override val tracking: Tracking) : DownloadJob {
        override val key get() = url
    }
}

data class DownloadClient(val key: String, val label: String)

val TORRENT_CLIENTS = listOf(
    DownloadClient("qbittorrent", "qBittorrent"),
    DownloadClient("rtorrent", "rTorrent"),
    DownloadClient("deluge", "Deluge"),
)

/** Client fixe pour les sources autres que torrent ; null = choisir parmi les clients torrent activés. */
fun fixedClient(job: DownloadJob): DownloadClient? = when (job) {
    is DownloadJob.Torrent -> null
    is DownloadJob.Ed2k -> DownloadClient("emule", "aMule")
    is DownloadJob.Telegram -> DownloadClient("telegram", "Telegram")
    is DownloadJob.Fourtoutici -> DownloadClient("fourtoutici", "Fourtoutici")
    is DownloadJob.Vk -> DownloadClient("vk", "VK")
}

/** Réponse de POST /api/import/replacement-required : le tome a-t-il déjà un fichier, et lequel. */
data class Replacement(val hasFile: Boolean, val volumeId: Int?)

suspend fun replacementRequired(api: BullarrApi, job: DownloadJob): Replacement? {
    val t = job.tracking
    val seriesId = t.seriesId ?: return null
    return runCatching {
        val o = api.postObject(
            "api/import/replacement-required",
            JSONObject().put("series_id", seriesId)
                .put("volume_id", t.volumeId ?: JSONObject.NULL)
                .put("volume_number", t.volumeNumber ?: JSONObject.NULL)
                .put("title", job.name),
        )
        Replacement(o.optBoolean("existing") && o.optBoolean("has_file"), o.int("volume_id"))
    }.getOrNull()
}

fun DownloadJob.withTracking(t: Tracking): DownloadJob = when (this) {
    is DownloadJob.Torrent -> copy(tracking = t)
    is DownloadJob.Ed2k -> copy(tracking = t)
    is DownloadJob.Telegram -> copy(tracking = t)
    is DownloadJob.Fourtoutici -> copy(tracking = t)
    is DownloadJob.Vk -> copy(tracking = t)
}

suspend fun sendDownload(api: BullarrApi, job: DownloadJob, client: DownloadClient, forceReplace: Boolean, qbCategory: String?) {
    val t = job.tracking
    val body = JSONObject()
        .put("series_id", t.seriesId ?: JSONObject.NULL)
        .put("volume_id", t.volumeId ?: JSONObject.NULL)
        .put("volume_number", t.volumeNumber ?: JSONObject.NULL)
        .put("force_replace", forceReplace)
    t.sourceLink?.let { body.put("source_link", it) }
    val path = when (job) {
        is DownloadJob.Torrent -> {
            body.put("torrent_url", job.url).put("title", job.name).put("source", "prowlarr")
            if (client.key == "qbittorrent" && qbCategory != null) body.put("category", qbCategory)
            "api/${client.key}/add"
        }
        is DownloadJob.Ed2k -> {
            body.put("link", job.link).put("title", job.name).put("source", "ebdz")
            "api/emule/add"
        }
        is DownloadJob.Telegram -> {
            body.put("channel", job.channel).put("message_id", job.messageId)
                .put("channel_title", job.channelTitle ?: "").put("filename", job.name).put("source", "telegram")
            "api/telegram-channels/download"
        }
        is DownloadJob.Fourtoutici -> {
            body.put("file_id", job.fileId).put("filename", job.name).put("source", "fourtoutici")
            "api/fourtoutici/download"
        }
        is DownloadJob.Vk -> {
            body.put("url", job.url).put("filename", job.name)
            "api/vk/download"
        }
    }
    api.postObject(path, body)
}
