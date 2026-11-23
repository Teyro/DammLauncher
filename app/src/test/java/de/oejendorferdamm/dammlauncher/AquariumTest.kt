package de.oejendorferdamm.dammlauncher

import de.oejendorferdamm.dammlauncher.ui.AquariumAnsicht
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AquariumTest {
    /** 40 Fische in 10 Minuten: zusammen genau 600 s Ruhe, erst langsam (≈ 44 s), am Ende schnell (≈ 7 s) */
    @Test fun abstaendeWerdenKuerzer() {
        val a = (0 until 40).map { AquariumAnsicht.abstand(it, 40, 600f) }
        assertEquals(600f, a.sum(), 2f)
        assertTrue(a.first() in 40f..48f)
        assertTrue(a.last() in 5f..9f)
        assertTrue(a.zipWithNext().all { (x, y) -> y <= x })
    }
}
