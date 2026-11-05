package io.github.gwenvador.bullarr.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Album en cours, pour « Continuer la lecture ». page : index 0-based. */
data class RecentRead(
    val volumeId: Int, val seriesId: Int?, val seriesTitle: String?, val label: String?, val title: String?,
    val page: Int, val total: Int, val at: Long,
    /** Couverture du tome (liste lue sur le serveur) ; sinon celle de la série. */
    val coverPath: String? = null,
)

/** État de lecture d'un album, mémorisé sur l'appareil. */
sealed interface ReadStatus {
    data object Unread : ReadStatus
    /** page : index 0-based de la dernière page lue. */
    data class InProgress(val page: Int) : ReadStatus
    data object Read : ReadStatus
}

class ReadingProgress(context: Context) {
    private val prefs = context.getSharedPreferences("reader", Context.MODE_PRIVATE)

    /** Réglage global du lecteur : page à la largeur de l'écran. */
    var fitWidth: Boolean
        get() = prefs.getBoolean("fit_width", false)
        set(value) = prefs.edit().putBoolean("fit_width", value).apply()

    /** Réglage global du lecteur : mode webtoon, les pages se suivent à la verticale et on lit en faisant défiler. */
    var webtoon: Boolean
        get() = prefs.getBoolean("webtoon", false)
        set(value) = prefs.edit().putBoolean("webtoon", value).apply()

    /** Réglage global : deux pages côte à côte quand le téléphone est en paysage. */
    var doublePage: Boolean
        get() = prefs.getBoolean("double_page", true)
        set(value) = prefs.edit().putBoolean("double_page", value).apply()

    /** Sens de lecture de droite à gauche (manga), mémorisé par série ; [default] tant que rien n'est choisi. */
    fun rtl(seriesId: Int?, default: Boolean): Boolean =
        seriesId?.let { if (prefs.contains("rtl_$it")) prefs.getBoolean("rtl_$it", false) else null } ?: default

    fun setRtl(seriesId: Int?, value: Boolean) { seriesId?.let { prefs.edit().putBoolean("rtl_$it", value).apply() } }

    /** Albums commencés et pas finis, du plus récent au plus ancien (liste de secours quand Komga n'est pas joignable). */
    fun recent(): List<RecentRead> = runCatching {
        val a = JSONArray(prefs.getString("recent", "[]"))
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            RecentRead(
                o.getInt("v"), o.optInt("s", 0).takeIf { it != 0 }, o.optString("st").ifBlank { null },
                o.optString("l").ifBlank { null }, o.optString("t").ifBlank { null },
                o.optInt("p"), o.optInt("n"), o.optLong("at"),
            )
        }.filter { get(it.volumeId) != null }
    }.getOrDefault(emptyList())

    private fun writeRecent(list: List<RecentRead>) {
        val a = JSONArray()
        list.take(20).forEach { r ->
            a.put(JSONObject().put("v", r.volumeId).put("s", r.seriesId ?: 0).put("st", r.seriesTitle ?: "")
                .put("l", r.label ?: "").put("t", r.title ?: "").put("p", r.page).put("n", r.total).put("at", r.at))
        }
        prefs.edit().putString("recent", a.toString()).apply()
    }

    private fun dropRecent(volumeId: Int) {
        val list = recent()
        if (list.any { it.volumeId == volumeId }) writeRecent(list.filter { it.volumeId != volumeId })
    }

    /** Dernière page lue (0-based), ou null : jamais lu, ou album fini (on repart du début). */
    fun get(volumeId: Int): Int? = prefs.getInt("page_$volumeId", -1).takeIf { it >= 0 }

    fun save(volumeId: Int, page: Int, pageCount: Int, meta: ReaderMeta? = null) {
        prefs.edit().apply {
            if (page >= pageCount - 1) { remove("page_$volumeId"); putBoolean("read_$volumeId", true) }
            else if (page > 0) putInt("page_$volumeId", page)
        }.apply()
        if (page >= pageCount - 1) dropRecent(volumeId)
        else if (page > 0 && meta != null) writeRecent(
            listOf(RecentRead(volumeId, meta.seriesId, meta.seriesTitle, meta.label, meta.title, page, pageCount, System.currentTimeMillis())) +
                recent().filter { it.volumeId != volumeId },
        )
    }

    fun markRead(volumeId: Int) { prefs.edit().remove("page_$volumeId").putBoolean("read_$volumeId", true).apply(); dropRecent(volumeId) }
    fun markUnread(volumeId: Int) { prefs.edit().remove("page_$volumeId").remove("read_$volumeId").apply(); dropRecent(volumeId) }

    fun status(volumeId: Int): ReadStatus = when {
        get(volumeId) != null -> ReadStatus.InProgress(get(volumeId)!!)
        prefs.getBoolean("read_$volumeId", false) -> ReadStatus.Read
        else -> ReadStatus.Unread
    }
}
