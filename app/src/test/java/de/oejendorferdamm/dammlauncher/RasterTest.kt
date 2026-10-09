package de.oejendorferdamm.dammlauncher

import de.oejendorferdamm.dammlauncher.konfig.Element
import de.oejendorferdamm.dammlauncher.konfig.Konfig
import de.oejendorferdamm.dammlauncher.konfig.Raender
import de.oejendorferdamm.dammlauncher.raster.Belegung
import de.oejendorferdamm.dammlauncher.raster.RasterRechner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RasterTest {
    private fun app(n: Int, s: Int, z: Int) = Element.App("de.test.app$n", "de.test.app$n.Start", s, z)

    @Test
    fun zellgroessenUndRaender() {
        // 1920 × 1080, 6 × 4, Ränder 40, Abstand 16
        val r = RasterRechner.aus(Konfig(spalten = 6, zeilen = 4, rand = Raender(40, 40, 40, 40), abstand = 16), 1920, 1080)
        assertEquals((1920 - 80 - 5 * 16) / 6f, r.zellBreite, 0.01f)
        assertEquals((1080 - 80 - 3 * 16) / 4f, r.zellHoehe, 0.01f)
        val f = r.feld(5, 3)
        assertEquals(1920f - 40f, f.rechts, 0.01f)
        assertEquals(1080f - 40f, f.unten, 0.01f)
        // Widget über 2 × 2 Zellen schließt den Abstand ein
        assertEquals(2 * r.zellBreite + 16f, r.feld(0, 0, 2, 2).breite, 0.01f)
    }

    @Test
    fun vierKSiehtGleichAus() {
        val k = Konfig(spalten = 10, zeilen = 3, rand = Raender(0, 100, 20, 20), abstand = 8)
        val hd = RasterRechner.aus(k, 1920, 1080)
        val uhd = RasterRechner.aus(k, 3840, 2160)
        assertEquals(hd.zellBreite * 2, uhd.zellBreite, 0.01f)
        assertEquals(hd.zellHoehe * 2, uhd.zellHoehe, 0.01f)
    }

    @Test
    fun randlosUndEinzelzelle() {
        val r = RasterRechner.aus(Konfig(spalten = 1, zeilen = 1, rand = Raender(0, 0, 0, 0), abstand = 0), 1920, 1080)
        assertEquals(Pair(0f, 0f), r.feld(0, 0).let { it.links to it.oben })
        assertEquals(1920f, r.zellBreite, 0.01f)
        assertEquals(0 to 0, r.zelleBei(1900f, 1000f))
    }

    @Test
    fun riesigeRaenderGebenNie0() {
        val r = RasterRechner.aus(Konfig(spalten = 10, zeilen = 10, rand = Raender(600, 600, 600, 600), abstand = 200), 1920, 1080)
        assertTrue(r.zellBreite >= 1f && r.zellHoehe >= 1f)
    }

    @Test
    fun zelleBeiPunkt() {
        val r = RasterRechner.aus(Konfig(spalten = 4, zeilen = 2, rand = Raender(100, 100, 100, 100), abstand = 20), 1920, 1080)
        assertNull(r.zelleBei(50f, 50f))
        assertEquals(0 to 0, r.zelleBei(110f, 110f))
        val f = r.feld(3, 1)
        assertEquals(3 to 1, r.zelleBei(f.links + 5, f.oben + 5))
    }

    @Test
    fun verkleinernOhneVerlust() {
        val e = listOf(app(1, 0, 0), app(2, 5, 0), app(3, 5, 3), Element.Widget("de.w.x/de.w.x.P", "w1", 2, 1, 3, 2))
        // 6 × 4 → 3 × 2: App 1 bleibt, Widget wird verkleinert, Apps 2 und 3 rücken in Lücken
        val (platziert, offen) = Belegung.anpassen(e, emptyList(), 3, 2)
        assertEquals(e.size, platziert.size + offen.size)
        assertTrue(platziert.all { Belegung.passt(it, 3, 2) })
        // nichts überlappt
        for (a in platziert) for (b in platziert) if (a !== b) assertTrue(Belegung.frei(listOf(a), b.spalte, b.zeile, b.breite, b.hoehe, 3, 2))
        // 1 × 1: nur eins passt, Rest in "nicht platziert"
        val (p2, o2) = Belegung.anpassen(platziert, offen, 1, 1)
        assertEquals(1, p2.size)
        assertEquals(e.size - 1, o2.size)
        // wieder groß: alles kommt zurück
        val (p3, o3) = Belegung.anpassen(p2, o2, 6, 4)
        assertEquals(e.size, p3.size)
        assertTrue(o3.isEmpty())
    }

    @Test
    fun ueberlappungWirdAufgeloest() {
        val (p, o) = Belegung.anpassen(listOf(app(1, 0, 0), app(2, 0, 0), app(1, 3, 3)), emptyList(), 2, 2)
        assertEquals(2, p.size) // doppelte App 1 nur einmal
        assertTrue(o.isEmpty())
        assertEquals(1 to 0, p[1].spalte to p[1].zeile)
    }
}
