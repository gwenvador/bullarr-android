package io.github.gwenvador.bullarr.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Pages du lecteur : GET /api/volumes/<id>/reader/page/<n> (images de ~1 Mo, 1920×2650).
 * Cache en mémoire borné en octets, téléchargements dédoublonnés, 3 à la fois.
 */
object PageCache {
    private val cache = object : LruCache<String, Bitmap>(
        // Les bitmaps matériels vivent côté GPU, hors du tas Java : on peut en garder davantage.
        160 * 1024 * 1024,
    ) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    private val inflight = HashMap<String, Deferred<Bitmap>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gate = Semaphore(3)

    /** Au-delà, l'image est réduite : le rendu à l'écran n'en a pas besoin et la mémoire non plus. */
    private const val MAX_SIDE = 3200

    fun cached(volumeId: Int, page: Int): Bitmap? = cache.get("$volumeId:$page")

    suspend fun get(api: BullarrApi, volumeId: Int, page: Int, offline: OfflineStore? = null): Bitmap {
        val key = "$volumeId:$page"
        cache.get(key)?.let { return it }
        val job = synchronized(inflight) {
            inflight.getOrPut(key) {
                scope.async {
                    try {
                        gate.withPermit {
                            // Copie hors ligne d'abord : aucun réseau nécessaire.
                            val local = offline?.pageFile(volumeId, page)?.takeIf { it.length() > 0 }
                            val bytes = local?.readBytes() ?: api.getBytes("api/volumes/$volumeId/reader/page/$page")
                            decode(bytes, page).also { cache.put(key, it) }
                        }
                    } finally {
                        synchronized(inflight) { inflight.remove(key) }
                    }
                }
            }
        }
        return job.await()
    }

    private fun decode(bytes: ByteArray, page: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE) sample *= 2
        // Décodé ET envoyé au GPU ici, hors du fil principal : afficher la page ne coûte plus rien.
        val hardware = runCatching {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
                inSampleSize = sample; inPreferredConfig = Bitmap.Config.HARDWARE
            })
        }.getOrNull()
        return hardware ?: BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: throw ApiException("Page $page : l'image reçue est illisible (${bytes.size} octets).")
    }

    // Miniatures : petites images (≈ 300 px) dérivées des pages, cache à part et plus léger.
    private val thumbs = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    private val thumbGate = Semaphore(2)
    private const val THUMB_SIDE = 320

    fun cachedThumb(volumeId: Int, page: Int): Bitmap? = thumbs.get("$volumeId:$page")

    /** Miniature d'une page : page déjà en mémoire, copie hors ligne, miniature du serveur ou, à défaut, page réduite. */
    suspend fun thumb(api: BullarrApi, volumeId: Int, page: Int, offline: OfflineStore? = null): Bitmap {
        val key = "$volumeId:$page"
        thumbs.get(key)?.let { return it }
        return thumbGate.withPermit {
            thumbs.get(key)?.let { return@withPermit it }
            withContext(Dispatchers.IO) {
                val full = cache.get(key)
                val small = if (full != null) {
                    val soft = full.copy(Bitmap.Config.ARGB_8888, false)
                    val ratio = THUMB_SIDE.toFloat() / maxOf(soft.width, soft.height)
                    if (ratio >= 1f) soft
                    else Bitmap.createScaledBitmap(soft, (soft.width * ratio).toInt().coerceAtLeast(1), (soft.height * ratio).toInt().coerceAtLeast(1), true)
                } else {
                    val local = offline?.pageFile(volumeId, page)?.takeIf { it.length() > 0 }
                    // Miniature servie par Bullarr (petite image) ; à défaut (serveur plus ancien), la page entière.
                    val bytes = local?.readBytes()
                        ?: runCatching { api.getBytes("api/volumes/$volumeId/reader/thumb/$page") }.getOrNull()
                        ?: api.getBytes("api/volumes/$volumeId/reader/page/$page")
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    var sample = 1
                    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= THUMB_SIDE) sample *= 2
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                        ?: throw ApiException("Page $page : l'image reçue est illisible (${bytes.size} octets).")
                }
                small.also { thumbs.put(key, it) }
            }
        }
    }

    fun clear() {
        thumbs.evictAll()
        synchronized(inflight) { inflight.values.forEach { it.cancel() }; inflight.clear() }
        cache.evictAll()
    }
}

data class ReaderMeta(val volumeId: Int, val seriesTitle: String?, val label: String?, val title: String?, val pageCount: Int, val seriesId: Int? = null)

fun parseReaderMeta(volumeId: Int, o: org.json.JSONObject) = ReaderMeta(
    volumeId, o.str("series_title"), o.str("label"), o.str("title"),
    o.int("page_count") ?: throw ApiException("GET /api/volumes/$volumeId/reader : le nombre de pages est absent de la réponse."),
    o.int("series_id"),
)
