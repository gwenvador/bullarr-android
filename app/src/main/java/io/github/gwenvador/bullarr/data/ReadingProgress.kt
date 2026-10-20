package io.github.gwenvador.bullarr.data

import android.content.Context

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

    /** Dernière page lue (0-based), ou null : jamais lu, ou album fini (on repart du début). */
    fun get(volumeId: Int): Int? = prefs.getInt("page_$volumeId", -1).takeIf { it >= 0 }

    fun save(volumeId: Int, page: Int, pageCount: Int) {
        prefs.edit().apply {
            if (page >= pageCount - 1) { remove("page_$volumeId"); putBoolean("read_$volumeId", true) }
            else if (page > 0) putInt("page_$volumeId", page)
        }.apply()
    }

    fun markRead(volumeId: Int) { prefs.edit().remove("page_$volumeId").putBoolean("read_$volumeId", true).apply() }
    fun markUnread(volumeId: Int) { prefs.edit().remove("page_$volumeId").remove("read_$volumeId").apply() }

    fun status(volumeId: Int): ReadStatus = when {
        get(volumeId) != null -> ReadStatus.InProgress(get(volumeId)!!)
        prefs.getBoolean("read_$volumeId", false) -> ReadStatus.Read
        else -> ReadStatus.Unread
    }
}
