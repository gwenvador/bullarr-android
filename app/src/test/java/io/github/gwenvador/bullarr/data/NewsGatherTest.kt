package io.github.gwenvador.bullarr.data

import io.github.gwenvador.bullarr.ui.gatherRecentSeries
import io.github.gwenvador.bullarr.ui.groupConsecutiveSeries
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class NewsGatherTest {
    private fun item(title: String, date: String?) = NewsItem(
        type = NewsType.RSS, title = title, subtitle = null, date = date, source = null, sizeBytes = null,
        inLibrary = false, owned = false, downloaded = false, link = null, seriesId = null,
        seriesTitle = newsGuessSeriesTitle(title).ifEmpty { title }, rawHint = title, bedethequeUrl = null,
        files = emptyList(), prowlarrIndexerId = null, raw = JSONObject().put("title", title),
    )

    @Test fun interleavedSeriesWithinTwoHoursAreGathered() {
        val list = listOf(
            item("Vesper T04.cbz", "2026-11-13 10:00"),
            item("Caravan T08.cbz", "2026-11-13 09:50"),
            item("Vesper T03.cbz", "2026-11-13 09:40"),
            item("Caravan T07.cbz", "2026-11-13 09:30"),
            item("Vesper T02.cbz", "2026-11-13 09:20"),
        )
        val groups = groupConsecutiveSeries(gatherRecentSeries(list))
        assertEquals(listOf(3, 2), groups.map { it.size })
        assertEquals("Vesper T02.cbz", groups[0].last().title)
    }

    @Test fun moreThanTwoHoursApartStaysSeparate() {
        val list = listOf(
            item("Vesper T04.cbz", "2026-11-13 12:00"),
            item("Caravan T08.cbz", "2026-11-13 11:00"),
            item("Vesper T03.cbz", "2026-11-13 09:30"),
        )
        assertEquals(listOf(1, 1, 1), groupConsecutiveSeries(gatherRecentSeries(list)).map { it.size })
    }

    @Test fun missingDateGathersNothing() {
        val list = listOf(item("Vesper T04.cbz", null), item("Caravan T08.cbz", null), item("Vesper T03.cbz", null))
        assertEquals(list, gatherRecentSeries(list))
    }

    @Test fun windowSlidesFromLastGatheredAlbum() {
        val list = listOf(
            item("Caravan T09.cbr", "2026-11-13 20:03"),
            item("Vesper T04.cbz", "2026-11-13 19:10"),
            item("Caravan T08.cbr", "2026-11-13 19:01"),
            item("Gaston T20.cbz", "2026-11-13 17:25"),
            item("Caravan T07.cbr", "2026-11-13 17:20"),
        )
        assertEquals(listOf(3, 1, 1), groupConsecutiveSeries(gatherRecentSeries(list)).map { it.size })
    }
}
