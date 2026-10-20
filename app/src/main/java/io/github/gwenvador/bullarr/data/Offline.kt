package io.github.gwenvador.bullarr.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * Albums copiés sur l'appareil pour la lecture hors ligne : les pages telles que le lecteur de
 * Bullarr les sert (GET /api/volumes/<id>/reader/page/<n>), quel que soit le format d'origine
 * (CBZ, CBR, PDF rendu en JPEG). Un album n'est « complet » qu'une fois meta.json écrit, en dernier.
 */
class OfflineStore(context: Context) {
    private val root = File(context.filesDir, "offline").apply { mkdirs() }

    data class Item(val meta: ReaderMeta, val sizeBytes: Long, val savedAt: Long)

    private fun dir(id: Int) = File(root, id.toString())
    fun pageFile(id: Int, page: Int) = File(dir(id), "$page.img")
    fun hasPage(id: Int, page: Int) = pageFile(id, page).length() > 0
    fun pagesOnDisk(id: Int): Int = dir(id).listFiles { f -> f.name.endsWith(".img") && f.length() > 0 }?.size ?: 0

    fun metaOf(id: Int): ReaderMeta? = readMeta(File(dir(id), "meta.json"))?.first

    private fun readMeta(f: File): Pair<ReaderMeta, Long>? = runCatching {
        val o = JSONObject(f.readText())
        if (!o.optBoolean("complete")) return null
        parseReaderMeta(o.getInt("volume_id"), o) to o.optLong("saved_at")
    }.getOrNull()

    fun sizeOf(id: Int): Long = dir(id).listFiles()?.sumOf { it.length() } ?: 0L

    fun list(): List<Item> = (root.listFiles() ?: emptyArray()).mapNotNull { d ->
        val (meta, at) = readMeta(File(d, "meta.json")) ?: return@mapNotNull null
        Item(meta, sizeOf(meta.volumeId), at)
    }.sortedByDescending { it.savedAt }

    fun savePage(id: Int, page: Int, bytes: ByteArray) {
        dir(id).mkdirs()
        val tmp = File(dir(id), "$page.tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(pageFile(id, page))) throw ApiException("Écriture impossible sur l'appareil (stockage plein ?) : page $page.")
    }

    fun saveMeta(meta: ReaderMeta) {
        val o = JSONObject()
            .put("volume_id", meta.volumeId).put("series_id", meta.seriesId ?: JSONObject.NULL)
            .put("series_title", meta.seriesTitle ?: JSONObject.NULL).put("label", meta.label ?: JSONObject.NULL)
            .put("title", meta.title ?: JSONObject.NULL).put("page_count", meta.pageCount)
            .put("complete", true).put("saved_at", System.currentTimeMillis())
        val tmp = File(dir(meta.volumeId), "meta.tmp")
        tmp.writeText(o.toString())
        tmp.renameTo(File(dir(meta.volumeId), "meta.json"))
    }

    fun delete(id: Int) { dir(id).deleteRecursively() }

    /** Espace libre sur l'appareil, pour prévenir avant un gros téléchargement. */
    fun freeBytes(): Long = root.usableSpace
}

data class OfflineProgress(val done: Int, val total: Int, val error: String? = null)

/** Téléchargements en cours (tant que l'app est ouverte ; un téléchargement interrompu reprend où il s'est arrêté). */
class OfflineManager(
    private val scope: CoroutineScope,
    private val api: () -> BullarrApi?,
    val store: OfflineStore,
) {
    val active = mutableStateMapOf<Int, OfflineProgress>()
    val titles = mutableStateMapOf<Int, String>()
    /** Incrémenté à chaque changement du contenu du disque : les listes se rafraîchissent. */
    var version by mutableIntStateOf(0); private set
    private val jobs = HashMap<Int, Job>()
    private val gate = Semaphore(3)

    fun isDownloaded(id: Int): Boolean { version; return store.metaOf(id) != null }

    fun start(volumeId: Int, title: String) {
        val client = api() ?: return
        if (jobs[volumeId]?.isActive == true) return
        titles[volumeId] = title
        active[volumeId] = OfflineProgress(store.pagesOnDisk(volumeId), 0)
        jobs[volumeId] = scope.launch(Dispatchers.IO) {
            try {
                val meta = parseReaderMeta(volumeId, client.getObject("api/volumes/$volumeId/reader"))
                val needed = (1..meta.pageCount).filter { !store.hasPage(volumeId, it) }
                // Prévient avant de remplir le stockage : ~1 Mo par page en moyenne.
                if (needed.size * 1_500_000L > store.freeBytes()) {
                    throw ApiException("Pas assez de place sur l'appareil pour ${meta.pageCount} pages.")
                }
                val done = AtomicInteger(meta.pageCount - needed.size)
                active[volumeId] = OfflineProgress(done.get(), meta.pageCount)
                coroutineScope {
                    for (n in needed) launch {
                        gate.withPermit {
                            store.savePage(volumeId, n, client.getBytes("api/volumes/$volumeId/reader/page/$n"))
                        }
                        active[volumeId] = OfflineProgress(done.incrementAndGet(), meta.pageCount)
                    }
                }
                store.saveMeta(meta)
                withContext(Dispatchers.Main) { active.remove(volumeId); version++ }
            } catch (e: CancellationException) {
                withContext(kotlinx.coroutines.NonCancellable + Dispatchers.Main) { active.remove(volumeId) }
                throw e
            } catch (e: Exception) {
                val p = active[volumeId]
                withContext(Dispatchers.Main) { active[volumeId] = OfflineProgress(p?.done ?: 0, p?.total ?: 0, e.readable()) }
            }
        }
    }

    /** Arrête le téléchargement ; les pages déjà reçues sont gardées pour reprendre. */
    fun cancel(volumeId: Int) { jobs.remove(volumeId)?.cancel(); active.remove(volumeId) }

    fun delete(volumeId: Int) {
        cancel(volumeId)
        scope.launch(Dispatchers.IO) { store.delete(volumeId); withContext(Dispatchers.Main) { version++ } }
    }
}
