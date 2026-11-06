package de.oejendorferdamm.dammlauncher

import de.oejendorferdamm.dammlauncher.konfig.Element
import de.oejendorferdamm.dammlauncher.konfig.Konfig
import de.oejendorferdamm.dammlauncher.konfig.KonfigJson
import de.oejendorferdamm.dammlauncher.konfig.Minimal
import de.oejendorferdamm.dammlauncher.pflege.ZeitAbgleich
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.6.4: Minimalmodus und Zeitserver. */
class MinimalZeitTest {
    @Test
    fun minimalUndZeitserverHinUndZurueck() {
        val k = Konfig(minimal = Minimal(true, Element.App("com.cvte.source", "com.cvte.source.Main", 0, 0)), zeitServer = "de.pool.ntp.org", stand = 3L)
        val text = KonfigJson.schreibe(k)
        val e = KonfigJson.lese(text)
        assertTrue(e.warnungen.toString(), e.warnungen.isEmpty())
        assertEquals(k, e.konfig)
        assertEquals(k, KonfigJson.streng(text))
    }

    @Test
    fun alteVorlageBekommtPtbUndKeinenMinimalmodus() {
        val o = JSONObject(KonfigJson.schreibe(Konfig(stand = 1L)))
        o.remove("zeitServer")
        val e = KonfigJson.lese(o.toString())
        assertEquals(ZeitAbgleich.STANDARD, e.konfig.zeitServer)
        assertFalse(e.konfig.minimal.an)
    }

    @Test
    fun unsinnigerZeitserver() {
        val o = JSONObject(KonfigJson.schreibe(Konfig(stand = 1L))).put("zeitServer", "rm -rf /")
        val e = KonfigJson.lese(o.toString())
        assertEquals(ZeitAbgleich.STANDARD, e.konfig.zeitServer)
        assertFalse(e.warnungen.isEmpty())
        assertEquals("", KonfigJson.lese(JSONObject(KonfigJson.schreibe(Konfig(stand = 1L))).put("zeitServer", "").toString()).konfig.zeitServer)
    }

    @Test
    fun ntpZeitHinUndZurueckUndVersatz() {
        val b = ByteArray(48)
        val t = 1_794_000_000_123L
        ZeitAbgleich.schreibeZeit(b, 40, t)
        assertTrue(kotlin.math.abs(ZeitAbgleich.liesZeit(b, 40) - t) <= 1)
        // Server 5 s voraus, 100 ms Laufzeit je Richtung
        assertEquals(5000L, ZeitAbgleich.versatz(0, 5100, 5100, 200))
        assertEquals(-3000L, ZeitAbgleich.versatz(10_000, 7_050, 7_060, 10_110))
    }
}
