package io.github.gwenvador.bullarr.ui

import io.github.gwenvador.bullarr.data.NewsItem
import io.github.gwenvador.bullarr.data.NewsType
import io.github.gwenvador.bullarr.data.newsGuessSeriesTitle
import io.github.gwenvador.bullarr.data.newsPrettyName
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/** Regroupement des Nouveautés RSS sans série rattachée (même règle que static/js/ebdz-latest.js). */
class NewsGroupKeyTest {
    private fun rss(title: String) = NewsItem(
        type = NewsType.RSS, title = title, subtitle = null, date = null, source = null, sizeBytes = null,
        inLibrary = false, owned = false, downloaded = false, link = null, seriesId = null, seriesTitle = title,
        rawHint = title, bedethequeUrl = null, files = emptyList(), prowlarrIndexerId = null, raw = JSONObject().put("title", title),
    )

    @Test
    fun sameSeriesDifferentVolumesShareAKey() {
        val t05 = rss("Dragonball Perfect Edition T05 [CBZ]-Grp.cbz")
        val t06 = rss("Dragonball.Perfect.Edition.T06.cbz")
        val a = newsGroupKey(t05)
        assertNotNull(a)
        assertEquals(a, newsGroupKey(t06))
        assertEquals(2, groupConsecutiveSeries(listOf(t05, t06)).single().size)
    }

    @Test
    fun differentSeriesHaveDifferentKeys() {
        val onePiece = newsGroupKey(rss("One Piece Tome 101"))
        val naruto = newsGroupKey(rss("Naruto T01"))
        assertNotNull(onePiece)
        assertNotNull(naruto)
        assertNotEquals(onePiece, naruto)
    }

    @Test
    fun prettyNameAndGuessedTitle() {
        assertEquals("Dragonball Perfect Edition T05", newsPrettyName("Dragonball Perfect Edition T05 [CBZ]-Grp.cbz"))
        assertEquals("Dragonball Perfect Edition T06", newsPrettyName("Dragonball.Perfect.Edition.T06.cbz"))
        assertEquals("Blacksad", newsGuessSeriesTitle("Blacksad Vol.3"))
        assertEquals("One Piece", newsGuessSeriesTitle("One Piece Tome 101"))
        // Sans numéro de tome : rien n'est deviné (pas de regroupement hasardeux).
        assertEquals("", newsGuessSeriesTitle("Astérix 5.5 Le Grand Fossé"))
    }

    @Test
    fun groupHeaderShowsTheGuessedName() {
        assertEquals("Dragonball Perfect Edition", newsGroupName(rss("Dragonball.Perfect.Edition.T06.cbz")))
    }
}
