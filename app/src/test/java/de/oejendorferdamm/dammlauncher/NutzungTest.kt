package de.oejendorferdamm.dammlauncher

import de.oejendorferdamm.dammlauncher.nutzung.Ablauf
import de.oejendorferdamm.dammlauncher.nutzung.Auswertung
import de.oejendorferdamm.dammlauncher.nutzung.OdtBericht
import de.oejendorferdamm.dammlauncher.nutzung.Sitzung
import de.oejendorferdamm.dammlauncher.nutzung.Zustand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class NutzungTest {
    private val min = 60_000L
    private val zone = TimeZone.getTimeZone("Europe/Berlin")
    // Montag, 9. November 2026, 00:00 Berlin
    private val tag0 = java.util.Calendar.getInstance(zone).apply { clear(); set(2026, 10, 9) }.timeInMillis
    private fun um(h: Int, m: Int = 0) = tag0 + h * 60 * min + m * min
    /** Wie im Betrieb: jede Minute ein Lebenszeichen bis kurz vor [bis] */
    private fun pulse(z: Zustand, bis: Long, an: Boolean = true) { var t = z.lebt + min; while (t < bis) { Ablauf.puls(z, t, an); t += min } }

    @Test
    fun appBisStartbildschirm() {
        val z = Zustand()
        Ablauf.puls(z, um(8), true); pulse(z, um(8, 5))
        val r = Ablauf.appStart(z, "board", um(8, 5)).also { pulse(z, um(8, 50)) } + Ablauf.launcherVorn(z, um(8, 50)).also { pulse(z, um(9)) } + Ablauf.bildschirmAus(z, um(9))
        assertEquals(listOf(Sitzung('A', um(8, 5), um(8, 50), "board"), Sitzung('B', um(8), um(9))), r)
    }

    @Test
    fun appWechselUndBildschirmAusUndAn() {
        val z = Zustand()
        Ablauf.puls(z, um(8), true)
        val r = Ablauf.appStart(z, "a", um(8, 1)).also { pulse(z, um(8, 10)) } + Ablauf.appStart(z, "b", um(8, 10)).also { pulse(z, um(8, 20)) } + Ablauf.bildschirmAus(z, um(8, 20))
        assertEquals(listOf(Sitzung('A', um(8, 1), um(8, 10), "a"), Sitzung('A', um(8, 10), um(8, 20), "b"), Sitzung('B', um(8), um(8, 20))), r)
        // wieder an, App b noch vorn → läuft weiter
        Ablauf.bildschirmAn(z, um(9), launcherVorn = false)
        pulse(z, um(9, 30))
        val r2 = Ablauf.launcherVorn(z, um(9, 30))
        assertEquals(listOf(Sitzung('A', um(9), um(9, 30), "b")), r2)
    }

    @Test
    fun ohnePulsEndetAllesBeimLetztenLebenszeichen() {
        val z = Zustand()
        Ablauf.puls(z, um(14), true)
        Ablauf.appStart(z, "a", um(14, 1))
        pulse(z, um(14, 30)); Ablauf.puls(z, um(14, 30), true)
        // Board hart ausgeschaltet, nächster Puls am nächsten Morgen
        val r = Ablauf.puls(z, um(31), true)
        assertEquals(listOf(Sitzung('A', um(14, 1), um(14, 30), "a"), Sitzung('B', um(14), um(14, 30))), r)
        assertEquals(um(31), z.bildschirmSeit)
        assertEquals(null, z.offenPaket)
    }

    @Test
    fun kurzeTippsZaehlenNicht() {
        val z = Zustand()
        Ablauf.puls(z, um(8), true)
        assertTrue((Ablauf.appStart(z, "a", um(8, 1)) + Ablauf.launcherVorn(z, um(8, 1) + 1000)).isEmpty())
    }

    @Test
    fun tageTeilenUeberMitternacht() {
        val s = listOf(Sitzung('B', um(22), um(26)), Sitzung('A', um(23), um(25), "a"))
        val tage = Auswertung.tage(s, um(30), 2, zone)
        assertEquals(2, tage.size)
        assertEquals(tag0 + 24 * 60 * min, tage[0].beginn)
        assertEquals(2 * 60 * min, tage[0].anMs)
        assertEquals(2 * 60 * min, tage[1].anMs)
        assertEquals(60 * min, tage[1].appMs)
        assertEquals(1, Auswertung.abendsAn(tage, zone))
        assertEquals(listOf("a" to 2 * 60 * min), Auswertung.jeApp(tage))
    }

    @Test
    fun dauerText() {
        assertEquals("< 1 min", Auswertung.dauer(30_000))
        assertEquals("42 min", Auswertung.dauer(42 * min))
        assertEquals("3 h 05 min", Auswertung.dauer(185 * min))
    }

    @Test
    fun odtIstGueltig() {
        val s = listOf(Sitzung('B', um(7, 40), um(15)), Sitzung('A', um(8), um(9), "de.board"), Sitzung('A', um(10), um(10, 45), "x<&>\"y"))
        val tage = Auswertung.tage(s, um(16), 7, zone)
        val png = byteArrayOf(1, 2, 3)
        val d = OdtBericht.Daten("Raum 2b & Co", "1.10.0", tage, { if (it == "de.board") "DammBoard" else it },
            OdtBericht.Bild("a.png", png, 100, 50), OdtBericht.Bild("b.png", png, 100, 80), erstellt = um(16))
        val zip = OdtBericht.erzeuge(d)
        val eintraege = LinkedHashMap<String, Pair<Int, ByteArray>>()
        ZipInputStream(zip.inputStream()).use { z ->
            while (true) { val e = z.nextEntry ?: break; eintraege[e.name] = e.method to z.readBytes() }
        }
        assertEquals("mimetype", eintraege.keys.first())
        assertEquals(ZipEntry.STORED, eintraege["mimetype"]!!.first)
        assertEquals(OdtBericht.MIME, String(eintraege["mimetype"]!!.second))
        assertTrue(eintraege.keys.containsAll(listOf("META-INF/manifest.xml", "content.xml", "styles.xml", "Pictures/a.png", "Pictures/b.png")))
        val f = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        for (n in listOf("META-INF/manifest.xml", "content.xml", "styles.xml")) f.newDocumentBuilder().parse(eintraege[n]!!.second.inputStream())
        val inhalt = String(eintraege["content.xml"]!!.second)
        assertTrue(inhalt.contains("Raum 2b &amp; Co"))
        assertTrue(inhalt.contains("x&lt;&amp;&gt;&quot;y"))
        assertTrue(inhalt.contains("DammBoard"))
        assertTrue(inhalt.contains("08:00"))
        // zum Ansehen: build/nutzung-test.odt
        java.io.File("build/nutzung-test.odt").writeBytes(zip)
    }
}
