package io.github.gwenvador.bullarr.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.gwenvador.bullarr.data.ApiException
import io.github.gwenvador.bullarr.data.BullarrApi
import io.github.gwenvador.bullarr.data.NewsItem
import io.github.gwenvador.bullarr.data.NewsType
import io.github.gwenvador.bullarr.data.Tracking
import io.github.gwenvador.bullarr.data.int
import io.github.gwenvador.bullarr.data.objects
import io.github.gwenvador.bullarr.data.parseLibraries
import org.json.JSONObject
import io.github.gwenvador.bullarr.data.DownloadClient
import io.github.gwenvador.bullarr.data.DownloadJob
import io.github.gwenvador.bullarr.data.TORRENT_CLIENTS
import io.github.gwenvador.bullarr.data.fixedClient
import io.github.gwenvador.bullarr.data.readable
import io.github.gwenvador.bullarr.data.replacementRequired
import io.github.gwenvador.bullarr.data.sendDownload
import io.github.gwenvador.bullarr.data.withTracking
import io.github.gwenvador.bullarr.data.str
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Confirmation d'un envoi : fichier, clients possibles, remplacement éventuel. */
data class PendingDownload(
    val job: DownloadJob,
    val context: String?,
    val warning: String?,
    val clients: List<DownloadClient>,
    val chosen: DownloadClient?,
    val replace: Boolean,
)

/**
 * Envoi de fichiers aux clients de Bullarr, partagé par Recherche et Nouveautés.
 * Un client torrent n'est proposé que s'il est activé dans Bullarr (même règle que nav.js).
 */
class DownloadController(private val scope: CoroutineScope, private val api: () -> BullarrApi?) {
    var choices by mutableStateOf<Pair<String, List<Pair<DownloadJob, String?>>>?>(null)
    var pending by mutableStateOf<PendingDownload?>(null)
    var sending by mutableStateOf(false); private set
    /** Fichiers envoyés pendant cette session : clé → client. */
    val sent = mutableStateMapOf<String, String>()
    var snackbar: SnackbarHostState? = null

    private var torrentClients: List<DownloadClient>? = null
    private var qbCategory: String? = null

    private suspend fun enabledTorrentClients(client: BullarrApi): List<DownloadClient> {
        torrentClients?.let { return it }
        val found = TORRENT_CLIENTS.filter { c ->
            runCatching { client.getObject("api/${c.key}/config") }.getOrNull()?.let { cfg ->
                if (c.key == "qbittorrent") qbCategory = cfg.str("default_category")
                cfg.optBoolean("enabled", false)
            } == true
        }
        torrentClients = found
        return found
    }

    /** Plusieurs fichiers (fil EBDZ) : on choisit d'abord lequel. Le second élément est une note (« Possédé »). */
    fun choose(title: String, jobs: List<Pair<DownloadJob, String?>>) {
        if (jobs.size == 1) request(jobs.first().first) else choices = title to jobs
    }

    fun request(job: DownloadJob, context: String? = null, warning: String? = null) {
        val client = api() ?: return
        choices = null
        scope.launch {
            val clients = fixedClient(job)?.let { listOf(it) } ?: enabledTorrentClients(client)
            if (clients.isEmpty()) {
                snackbar?.showSnackbar("Aucun client torrent activé dans Bullarr (Paramètres → clients de téléchargement).")
                return@launch
            }
            // Fichier déjà présent : on le remplace, en visant le tome existant (comme replaceNouveautes*).
            val rep = replacementRequired(client, job)
            val target = if (rep?.hasFile == true && job.tracking.volumeId == null && rep.volumeId != null)
                job.withTracking(job.tracking.copy(volumeId = rep.volumeId)) else job
            pending = PendingDownload(target, context, warning, clients, clients.singleOrNull(), rep?.hasFile == true)
        }
    }

    /** ⚡ en attente de confirmation (Nouveautés). */
    var quick by mutableStateOf<NewsItem?>(null)

    /**
     * ⚡ « Ajouter la série et télécharger » (addSeriesAndDownloadNouveautes, ebdz-latest.js) :
     * crée la série depuis Bédéthèque si Bullarr ne la connaît pas, ou l'envoie en
     * validation si aucune fiche ne correspond, puis télécharge la release.
     */
    fun runQuick() {
        val item = quick ?: return
        val client = api() ?: return
        sending = true
        scope.launch {
            try {
                var seriesId = item.seriesId
                val known = seriesId != null
                if (seriesId == null) {
                    seriesId = try {
                        resolveSeries(client, item)
                    } catch (e: Exception) {
                        val queued = runCatching { queueReview(client, item, e.readable()) }.isSuccess
                        quick = null
                        snackbar?.showSnackbar("« ${item.seriesTitle} » : ${e.readable()}" + if (queued) " Envoyé en validation." else "")
                        return@launch
                    }
                }
                val dest = downloadRelease(client, item, seriesId)
                quick = null
                snackbar?.showSnackbar("« ${item.seriesTitle} » : " + (if (known) "" else "série ajoutée, ") + "téléchargement envoyé à $dest.")
            } catch (e: Exception) {
                quick = null
                snackbar?.showSnackbar("« ${item.seriesTitle} » : ${e.readable()}")
            } finally {
                sending = false
            }
        }
    }

    private suspend fun resolveSeries(client: BullarrApi, item: NewsItem): Int {
        var url = item.bedethequeUrl?.takeIf { it.startsWith("https://www.bedetheque.com/") }
        if (url == null) {
            val info = client.getObject("api/bedetheque/info", mapOf("title" to item.seriesTitle, "raw" to item.rawHint))
            url = info.optJSONObject("info")?.str("url") ?: throw ApiException(info.str("error") ?: "Aucune fiche Bédéthèque confirmée")
        }
        val libraryId = parseLibraries(client.getArray("api/libraries")).firstOrNull()?.id
            ?: throw ApiException("Aucune bibliothèque disponible")
        val added = client.postObject(
            "api/bedetheque/add-series",
            JSONObject().put("url", url).put("library_id", libraryId).put("skip_auto_acquire", true),
        )
        return added.int("series_id") ?: throw ApiException("Ajout de la série impossible")
    }

    private suspend fun queueReview(client: BullarrApi, item: NewsItem, reason: String) {
        val first = item.files.firstOrNull { !it.owned }?.job
        val candidate = JSONObject()
            .put("source", if (item.type == NewsType.RSS) "prowlarr" else item.type.name.lowercase())
            .put("title", item.title).put("filename", first?.name ?: item.title)
            .put("info_url", item.link ?: "")
            .put("bedetheque_url", item.bedethequeUrl?.takeIf { it.startsWith("https://www.bedetheque.com/") } ?: JSONObject.NULL)
        when (first) {
            is DownloadJob.Torrent -> candidate.put("link", first.url)
            is DownloadJob.Vk -> candidate.put("link", first.url)
            is DownloadJob.Ed2k -> candidate.put("link", first.link)
            is DownloadJob.Telegram -> candidate.put("channel", first.channel).put("message_id", first.messageId).put("channel_title", first.channelTitle ?: "")
            else -> {}
        }
        client.postObject(
            "api/auto-acquire/reviews/series-match",
            JSONObject().put("series_title", item.seriesTitle).put("candidates", org.json.JSONArray().put(candidate)).put("reason", reason),
        )
    }

    private suspend fun downloadRelease(client: BullarrApi, item: NewsItem, seriesId: Int): String {
        fun tracked(job: DownloadJob) = job.withTracking(job.tracking.copy(seriesId = seriesId))
        when (item.type) {
            NewsType.EBDZ -> {
                // Un fichier par tome, sans ceux déjà possédés (comme _downloadNouveautesRelease).
                val seen = HashSet<String>()
                val files = item.files.filter { f -> !f.owned && (f.volumeLabel == null || seen.add(f.volumeLabel)) }
                if (files.isEmpty()) throw ApiException("Aucun fichier à télécharger : tout est déjà possédé")
                files.forEach { f ->
                    val job = tracked(f.job)
                    sendDownload(client, job, fixedClient(job)!!, false, null)
                    sent[job.key] = "aMule"
                }
                return if (files.size > 1) "aMule (${files.size} fichiers)" else "aMule"
            }
            NewsType.TELEGRAM, NewsType.RSS -> {
                var job = item.files.firstOrNull()?.job?.let { tracked(it) }
                if (job == null && item.prowlarrIndexerId != null) job = findProwlarrRelease(client, item)?.let { tracked(it) }
                job ?: throw ApiException("Lien de téléchargement introuvable pour cette release")
                val target = fixedClient(job) ?: enabledTorrentClients(client).firstOrNull()
                    ?: throw ApiException("Aucun client torrent activé")
                sendDownload(client, job, target, false, qbCategory)
                sent[job.key] = target.label
                return target.label
            }
        }
    }

    /** Release RSS sans lien direct : retrouvée par recherche Prowlarr exacte (_resolveRssTorrentUrl). */
    private suspend fun findProwlarrRelease(client: BullarrApi, item: NewsItem): DownloadJob? {
        val res = client.getObject("api/search/prowlarr", mapOf("q" to item.seriesTitle)).optJSONArray("results").objects()
        val exact = res.filter { it.str("title").equals(item.title, ignoreCase = true) }
        val pageLink = item.raw.str("link")
        val release = if (pageLink != null) exact.firstOrNull { it.str("info_url") == pageLink } else exact.singleOrNull()
        return release?.str("download_url")?.let { DownloadJob.Torrent(it, item.title, Tracking(sourceLink = pageLink)) }
    }

    fun confirm() {
        val p = pending ?: return
        val target = p.chosen ?: return
        val client = api() ?: return
        sending = true
        scope.launch {
            try {
                sendDownload(client, p.job, target, p.replace, qbCategory)
                sent[p.job.key] = target.label
                pending = null
                snackbar?.showSnackbar("Envoyé à ${target.label} — suivez-le dans Activité.")
            } catch (e: Exception) {
                pending = null
                snackbar?.showSnackbar(e.readable())
            } finally {
                sending = false
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DownloadDialogs(c: DownloadController) {
    c.quick?.let { item ->
        val toSend = when (item.type) {
            NewsType.EBDZ -> item.files.count { !it.owned }
            else -> 1
        }
        AlertDialog(
            onDismissRequest = { if (!c.sending) c.quick = null },
            title = { Text(if (item.seriesId == null) "Ajouter la série et télécharger ?" else "Télécharger ?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(item.seriesTitle, fontWeight = FontWeight.SemiBold)
                    Text(item.title, style = MaterialTheme.typography.bodySmall)
                    if (item.seriesId == null) Text("La série sera créée depuis Bédéthèque, ou envoyée en validation si aucune fiche ne correspond.")
                    Text(if (toSend > 1) "$toSend fichiers seront envoyés." else "Le fichier sera envoyé au client de téléchargement.")
                }
            },
            confirmButton = {
                TextButton(enabled = !c.sending, onClick = { c.runQuick() }) {
                    if (c.sending) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Lancer")
                }
            },
            dismissButton = { TextButton(enabled = !c.sending, onClick = { c.quick = null }) { Text("Annuler") } },
        )
    }
    c.choices?.let { (title, jobs) ->
        AlertDialog(
            onDismissRequest = { c.choices = null },
            title = { Text(title) },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(jobs) { (job, note) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(job.name, style = MaterialTheme.typography.bodyMedium)
                                val sentTo = c.sent[job.key]
                                val line = listOfNotNull(note, sentTo?.let { "Envoyé à $it" }).joinToString(" · ")
                                if (line.isNotEmpty()) Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { c.request(job) }, enabled = c.sent[job.key] == null) {
                                Icon(Icons.Outlined.Download, contentDescription = "Télécharger")
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { c.choices = null }) { Text("Fermer") } },
        )
    }
    c.pending?.let { p ->
        AlertDialog(
            onDismissRequest = { if (!c.sending) c.pending = null },
            title = { Text("Télécharger ?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(p.job.name, fontWeight = FontWeight.SemiBold)
                    p.context?.let { Text(it) }
                    p.warning?.let { Text(it, color = MaterialTheme.colorScheme.tertiary) }
                    if (p.replace) Text("Cet album existe déjà dans Bullarr : son fichier sera remplacé.", color = MaterialTheme.colorScheme.error)
                    if (p.clients.size > 1) {
                        Text("Client :")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            p.clients.forEach { t ->
                                FilterChip(selected = p.chosen == t, onClick = { c.pending = p.copy(chosen = t) }, label = { Text(t.label) })
                            }
                        }
                    } else {
                        Text("Client : ${p.clients.first().label}.")
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = !c.sending && p.chosen != null, onClick = { c.confirm() }) {
                    if (c.sending) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Télécharger")
                }
            },
            dismissButton = { TextButton(enabled = !c.sending, onClick = { c.pending = null }) { Text("Annuler") } },
        )
    }
}
