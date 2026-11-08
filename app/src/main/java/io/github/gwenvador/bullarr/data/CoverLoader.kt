package io.github.gwenvador.bullarr.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.security.MessageDigest

/**
 * Couvertures servies par Bullarr sous /covers/… (route publique serve_cover). Cache
 * mémoire réduit en taille ; une couverture absente donne simplement null.
 */
object CoverLoader {
    // Mémoire : 1/6 du tas (la grille de plusieurs centaines de séries tenait mal dans 24 Mo).
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 6).toInt().coerceAtLeast(24 * 1024 * 1024)) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val failed = HashSet<String>()
    private val gate = Semaphore(6)

    // Disque : les octets de chaque couverture, pour ne pas tout retélécharger à chaque lancement.
    // Relue au-delà de 7 jours (une couverture peut changer) ; l'ancienne sert si le réseau manque.
    private var dir: File? = null
    private const val MAX_AGE_MS = 7L * 24 * 3600 * 1000
    private const val MAX_DISK_BYTES = 250L * 1024 * 1024

    fun init(context: Context) {
        if (dir != null) return
        dir = File(context.cacheDir, "covers").apply { mkdirs() }
        Thread { trim() }.start()
    }

    /** Vide le cache disque (déconnexion, changement de serveur). */
    fun clearDisk() { dir?.listFiles()?.forEach { it.delete() } }

    private fun fileOf(url: String): File? = dir?.let { d ->
        val h = MessageDigest.getInstance("SHA-1").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
        File(d, h)
    }

    private fun trim() {
        val files = dir?.listFiles()?.sortedByDescending { it.lastModified() } ?: return
        var total = 0L
        files.forEach { f -> total += f.length(); if (total > MAX_DISK_BYTES) f.delete() }
    }

    fun cached(url: String): Bitmap? = cache.get(url)

    /** [key] sépare une version agrandie de la miniature mise en cache sous [url]. */
    suspend fun load(url: String, targetWidthPx: Int, key: String = url): Bitmap? {
        cache.get(key)?.let { return it }
        synchronized(failed) { if (url in failed) return null }
        return gate.withPermit {
            withContext(Dispatchers.IO) {
                try {
                    val file = fileOf(url)
                    val stored = file?.takeIf { it.isFile && it.length() > 0 }
                    val fresh = stored != null && System.currentTimeMillis() - stored.lastModified() < MAX_AGE_MS
                    val bytes = (if (fresh) runCatching { stored!!.readBytes() }.getOrNull() else null)
                        ?: runCatching {
                            BullarrApi.client.newCall(Request.Builder().url(url).build()).execute().use { r ->
                                if (!r.isSuccessful) null else r.body?.bytes()
                            }
                        }.getOrNull()?.also { b -> runCatching { file?.writeBytes(b) } }
                        ?: stored?.let { runCatching { it.readBytes() }.getOrNull() }
                        ?: return@withContext null.also { synchronized(failed) { failed += url } }
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    var sample = 1
                    while (bounds.outWidth / (sample * 2) >= targetWidthPx) sample *= 2
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                    bmp?.also { cache.put(key, it) }
                } catch (_: Exception) {
                    null
                }
            }
        }
    }
}
