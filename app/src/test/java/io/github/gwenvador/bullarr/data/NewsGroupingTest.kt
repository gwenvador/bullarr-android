package io.github.gwenvador.bullarr.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Regroupement des Nouveautés RSS sans série reconnue (comme ebdz-latest.js du site). */
class NewsGroupingTest {
    private fun key(raw: String) = "t:" + normalize(newsGuessSeriesTitle(raw)).trim()

    @Test fun prettyName() {
        assertEquals("Dragonball Perfect Edition T05", newsPrettyName("Dragonball Perfect Edition T05 [CBZ]-Grp.cbz"))
        assertEquals("Dragonball Perfect Edition T06", newsPrettyName("Dragonball.Perfect.Edition.T06.cbz"))
        assertEquals("Spirou 4.5", newsPrettyName("Spirou_4.5.cbr"))
    }

    @Test fun sameSeriesSameKey() {
        assertEquals("Dragonball Perfect Edition", newsGuessSeriesTitle("Dragonball Perfect Edition T05 [CBZ]-Grp.cbz"))
        assertEquals(key("Dragonball Perfect Edition T05 [CBZ]-Grp.cbz"), key("Dragonball.Perfect.Edition.T06.cbz"))
        assertEquals("Dragonball Perfect Edition", newsGuessSeriesTitle("Dragonball Perfect Edition T21-mep"))
    }

    @Test fun differentSeriesDifferentKeys() {
        assertEquals("One Piece", newsGuessSeriesTitle("One Piece Tome 101"))
        assertEquals("Naruto", newsGuessSeriesTitle("Naruto T01"))
        assertNotEquals(key("One Piece Tome 101"), key("Naruto T01"))
    }

    @Test fun noTomeNoGuess() {
        assertEquals("", newsGuessSeriesTitle("Le Grand Livre"))
        assertEquals("", newsGuessSeriesTitle("T05"))
        assertEquals("", newsGuessSeriesTitle(null))
    }

    @Test fun compactNameIgnoresSpacesAndParens() {
        assertEquals(
            io.github.gwenvador.bullarr.ui.compactSeriesName("Dragon Ball (Perfect Edition)"),
            io.github.gwenvador.bullarr.ui.compactSeriesName(newsGuessSeriesTitle("Dragonball Perfect Edition T05.cbz")),
        )
    }
}
