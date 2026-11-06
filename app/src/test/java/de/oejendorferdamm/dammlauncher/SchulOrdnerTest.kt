package de.oejendorferdamm.dammlauncher

import de.oejendorferdamm.dammlauncher.abgleich.SchulOrdner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SchulOrdnerTest {
    @Test
    fun bildDesTagesReihum() {
        val namen = listOf("b.JPG", "notiz.txt", "a.png", "c.webp")
        assertEquals("a.png", SchulOrdner.bildDesTages(namen, 0))
        assertEquals("b.JPG", SchulOrdner.bildDesTages(namen, 1))
        assertEquals("c.webp", SchulOrdner.bildDesTages(namen, 2))
        assertEquals("a.png", SchulOrdner.bildDesTages(namen, 3))
        assertNull(SchulOrdner.bildDesTages(listOf("liesmich.txt"), 5))
    }
}
