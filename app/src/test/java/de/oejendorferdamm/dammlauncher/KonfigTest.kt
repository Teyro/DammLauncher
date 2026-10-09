package de.oejendorferdamm.dammlauncher

import de.oejendorferdamm.dammlauncher.konfig.Element
import de.oejendorferdamm.dammlauncher.konfig.Hintergrund
import de.oejendorferdamm.dammlauncher.konfig.Konfig
import de.oejendorferdamm.dammlauncher.konfig.KonfigFehler
import de.oejendorferdamm.dammlauncher.konfig.KonfigJson
import de.oejendorferdamm.dammlauncher.konfig.PasswortHash
import de.oejendorferdamm.dammlauncher.konfig.Raender
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class KonfigTest {
    private val voll = Konfig(
        spalten = 8, zeilen = 3, rand = Raender(0, 120, 10, 10), abstand = 4, iconProzent = 70, textGroesse = 30,
        beschriftung = false, alleAppsKnopf = true,
        elemente = listOf(
            Element.App("org.mozilla.firefox", "org.mozilla.fenix.HomeActivity", 0, 0, titel = "Internet", icon = "0123abcd.png"),
            Element.Widget("com.android.deskclock/com.android.alarmclock.DigitalAppWidgetProvider", "uhr1", 2, 0, 3, 2)
        ),
        nichtPlatziert = listOf(Element.App("com.android.settings", "com.android.settings.Settings", 0, 0)),
        hintergrund = Hintergrund(0xFF336699.toInt(), bild = true),
        bearbeitenGesperrt = true,
        pin = PasswortHash("aGFzaA==", "c2FsdA==", 20000),
        boss = PasswortHash("Ym9zcw==", "c2FsejI=", 30000),
        stand = 1760000000000L
    )

    @Test
    fun hinUndZurueck() {
        val e = KonfigJson.lese(KonfigJson.schreibe(voll))
        assertTrue(e.warnungen.toString(), e.warnungen.isEmpty())
        assertEquals(voll, e.konfig)
        assertEquals(voll, KonfigJson.streng(KonfigJson.schreibe(voll)))
    }

    @Test
    fun eigeneSymboleNurMitHarmlosemNamen() {
        assertEquals(setOf("0123abcd.png"), KonfigJson.icons(voll))
        val text = KonfigJson.schreibe(voll).replace("0123abcd.png", "../../etc/passwd")
        val e = KonfigJson.lese(text)
        assertEquals(null, (e.konfig.elemente[0] as Element.App).icon)
        assertEquals("Internet", (e.konfig.elemente[0] as Element.App).titel)
        assertTrue(e.warnungen.isNotEmpty())
    }

    @Test
    fun kaputteDateien() {
        for (text in listOf("", "kein json", "[]", "{}", """{"format":0}""")) {
            try {
                KonfigJson.lese(text); fail("muss scheitern: $text")
            } catch (_: KonfigFehler) {
            }
        }
        try {
            KonfigJson.lese("""{"format":99}"""); fail("neueres Format muss scheitern")
        } catch (e: KonfigFehler) {
            assertTrue(e.message!!.contains("neueren"))
        }
    }

    @Test
    fun unsinnWirdKorrigiert() {
        val text = """{"format":1,"spalten":50,"zeilen":0,"rand":{"oben":-5,"unten":"x","links":10,"rechts":10},"abstand":3,
            "iconProzent":500,"textGroesse":22,"beschriftung":"ja","animationen":false,"alleAppsKnopf":true,
            "elemente":[{"typ":"app","paket":"../böse","aktivitaet":"x","spalte":0,"zeile":0},{"typ":"app","paket":"de.a.b","aktivitaet":"de.a.b.C","spalte":9,"zeile":9},{"typ":"rakete"}],
            "hintergrund":{"farbe":"#123456"},"bearbeitenGesperrt":false,"pin":{"hash":"","salt":"x","runden":5},"stand":5}"""
        val e = KonfigJson.lese(text)
        val k = e.konfig
        assertEquals(10, k.spalten)
        assertEquals(1, k.zeilen)
        assertEquals(0, k.rand.oben)
        assertEquals(40, k.rand.unten)
        assertEquals(100, k.iconProzent)
        assertEquals(true, k.beschriftung) // Standard statt "ja"
        assertEquals(1, k.elemente.size) // nur die gültige App, auf 10 × 1 verschoben
        assertEquals(0, k.elemente[0].zeile)
        assertEquals(0xFF123456.toInt(), k.hintergrund.farbe)
        assertEquals(null, k.pin)
        assertTrue(e.warnungen.size >= 6)
        try {
            KonfigJson.streng(text); fail("streng muss ablehnen")
        } catch (_: KonfigFehler) {
        }
    }

    @Test
    fun fehlendeAppsUndWidgets() {
        val (sichtbar, protokoll) = KonfigJson.aufloesen(voll, { p, _ -> p != "org.mozilla.firefox" }, { false })
        assertTrue(sichtbar.isEmpty())
        assertEquals(2, protokoll.size)
        assertTrue(protokoll[0].contains("firefox"))
        // Konfiguration selbst bleibt unverändert (App kann später installiert werden)
        assertEquals(2, voll.elemente.size)
        val (alle, leer) = KonfigJson.aufloesen(voll, { _, _ -> true }, { true })
        assertEquals(2, alle.size)
        assertTrue(leer.isEmpty())
    }
}
