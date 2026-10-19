package io.github.gwenvador.bullarr.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * Couvertures servies par Bullarr sous /covers/… (route publique serve_cover). Cache
 * mémoire réduit en taille ; une couverture absente donne simplement null.
 */
object CoverLoader {
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val failed = HashSet<String>()
    private val gate = Semaphore(6)

    fun cached(url: String): Bitmap? = cache.get(url)

    suspend fun load(url: String, targetWidthPx: Int): Bitmap? {
        cache.get(url)?.let { return it }
        synchronized(failed) { if (url in failed) return null }
        return gate.withPermit {
            withContext(Dispatchers.IO) {
                try {
                    val bytes = BullarrApi.client.newCall(Request.Builder().url(url).build()).execute().use { r ->
                        if (!r.isSuccessful) null else r.body?.bytes()
                    } ?: return@withContext null.also { synchronized(failed) { failed += url } }
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    var sample = 1
                    while (bounds.outWidth / (sample * 2) >= targetWidthPx) sample *= 2
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                    bmp?.also { cache.put(url, it) }
                } catch (_: Exception) {
                    null
                }
            }
        }
    }
}
