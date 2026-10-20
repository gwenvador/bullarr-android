package io.github.gwenvador.bullarr.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import java.io.IOException

/** APK publié sur GitHub (pièce jointe d'une release nommée bullarr-android-<version>.apk). */
data class AppRelease(val version: String, val url: String, val sizeBytes: Long?, val publishedAt: String?, val page: String?)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val latest: AppRelease?) : UpdateState
    data class Available(val release: AppRelease) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/**
 * Cherche la dernière version de l'app dans les releases GitHub (API publique, sans clé).
 * Les deux dépôts sont consultés : les APK peuvent être joints aux releases de l'app ou à
 * celles de Bullarr (voir README).
 */
object UpdateChecker {
    private val REPOS = listOf("gwenvador/bullarr-android", "gwenvador/bullarr")
    private val APK = Regex("^bullarr-android-(\\d+(?:\\.\\d+)*)\\.apk$")

    suspend fun latest(): AppRelease? = withContext(Dispatchers.IO) {
        var best: AppRelease? = null
        var firstError: Exception? = null
        var answered = false
        for (repo in REPOS) {
            try {
                for (r in fetch(repo)) if (best == null || compareVersions(r.version, best.version) > 0) best = r
                answered = true
            } catch (e: Exception) {
                if (firstError == null) firstError = e
            }
        }
        if (!answered) throw (firstError ?: ApiException("GitHub n'a répondu pour aucun dépôt."))
        best
    }

    private fun fetch(repo: String): List<AppRelease> {
        val url = "https://api.github.com/repos/$repo/releases?per_page=30"
        val req = Request.Builder().url(url).header("Accept", "application/vnd.github+json").build()
        val text = try {
            BullarrApi.client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (resp.code == 403 || resp.code == 429) throw ApiException("GitHub limite les requêtes (HTTP ${resp.code}) : réessayez dans quelques minutes.")
                if (resp.code == 404) throw ApiException("Dépôt GitHub $repo introuvable ou privé (HTTP 404).")
                if (!resp.isSuccessful) throw ApiException("GitHub a répondu HTTP ${resp.code} pour $repo.")
                body
            }
        } catch (e: IOException) {
            throw ApiException("GitHub injoignable : ${e.message ?: e.javaClass.simpleName}. Vérifiez la connexion internet.")
        }
        val arr = JSONArray(text)
        val out = ArrayList<AppRelease>()
        for (i in 0 until arr.length()) {
            val r = arr.optJSONObject(i) ?: continue
            if (r.optBoolean("draft") || r.optBoolean("prerelease")) continue
            val assets = r.optJSONArray("assets") ?: continue
            for (j in 0 until assets.length()) {
                val a = assets.optJSONObject(j) ?: continue
                val m = APK.matchEntire(a.optString("name")) ?: continue
                out += AppRelease(
                    version = m.groupValues[1], url = a.optString("browser_download_url"),
                    sizeBytes = a.optLong("size").takeIf { it > 0 }, publishedAt = r.optString("published_at").takeIf { it.isNotBlank() },
                    page = r.optString("html_url").takeIf { it.isNotBlank() },
                )
            }
        }
        return out
    }
}

/** Compare « 1.10 » et « 1.2 » numériquement, segment par segment. */
fun compareVersions(a: String, b: String): Int {
    val x = a.removePrefix("v").split('.').map { it.toIntOrNull() ?: 0 }
    val y = b.removePrefix("v").split('.').map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(x.size, y.size)) {
        val c = (x.getOrElse(i) { 0 }).compareTo(y.getOrElse(i) { 0 })
        if (c != 0) return c
    }
    return 0
}
