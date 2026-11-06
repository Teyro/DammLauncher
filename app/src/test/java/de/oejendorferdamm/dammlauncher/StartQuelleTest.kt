package de.oejendorferdamm.dammlauncher

import de.oejendorferdamm.dammlauncher.konfig.Konfig
import de.oejendorferdamm.dammlauncher.konfig.KonfigJson
import de.oejendorferdamm.dammlauncher.konfig.StartQuelleEinstellung
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.6.4: Quelle beim Einschalten wird gespeichert, verteilt und Unsinn verworfen. */
class StartQuelleTest {
    private val k = Konfig(startQuelle = StartQuelleEinstellung("com.mediatek.tvinput/.hdmi.HDMIInputService/HW5", "OPS", 60, false), stand = 7L)

    @Test
    fun hinUndZurueck() {
        val text = KonfigJson.schreibe(k)
        val e = KonfigJson.lese(text)
        assertTrue(e.warnungen.toString(), e.warnungen.isEmpty())
        assertEquals(k, e.konfig)
        assertEquals(k, KonfigJson.streng(text))
    }

    @Test
    fun ausWirdNichtGeschrieben() {
        assertFalse(JSONObject(KonfigJson.schreibe(Konfig(stand = 1L))).has("startQuelle"))
    }

    @Test
    fun unsinnWirdVerworfen() {
        val o = JSONObject(KonfigJson.schreibe(k))
        o.getJSONObject("startQuelle").put("id", "rm -rf /;\"").put("wartenSek", 99999)
        val e = KonfigJson.lese(o.toString())
        assertEquals(StartQuelleEinstellung(), e.konfig.startQuelle)
        assertFalse(e.warnungen.isEmpty())
    }
}
