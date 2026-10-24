package de.oejendorferdamm.dammlauncher

import de.oejendorferdamm.dammlauncher.abgleich.Abwesenheit
import de.oejendorferdamm.dammlauncher.konfig.Element
import de.oejendorferdamm.dammlauncher.konfig.Feierabend
import de.oejendorferdamm.dammlauncher.konfig.Hinweis
import de.oejendorferdamm.dammlauncher.konfig.Konfig
import de.oejendorferdamm.dammlauncher.konfig.KonfigJson
import de.oejendorferdamm.dammlauncher.konfig.Ordner
import de.oejendorferdamm.dammlauncher.konfig.SchuelerAnsicht
import de.oejendorferdamm.dammlauncher.konfig.Zeiten
import de.oejendorferdamm.dammlauncher.konfig.Zeitraum
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/** 1.5.0: Pausen, Feierabend, Hinweis-Band, Schüler-Ansicht, Ordner, QR- und Abwesenheits-Kachel. */
class SchulzeitenTest {
    /** Freitag, 23.10.2026 um [h]:[m] (+[tage]). */
    private fun am(h: Int, m: Int, tage: Int = 0) = Calendar.getInstance().apply {
        clear(); set(2026, Calendar.OCTOBER, 23 + tage, h, m, 0)
    }

    private val pausen = listOf(Zeitraum(9 * 60 + 35, 9 * 60 + 55), Zeitraum(11 * 60 + 25, 11 * 60 + 45))

    @Test
    fun zeitraeumeLesen() {
        assertEquals(Zeitraum(575, 595), Zeitraum.aus("9:35 - 09.55"))
        assertEquals(Zeitraum(575, 595), Zeitraum.aus("0935–0955"))
        assertEquals(Zeitraum(575, 595), Zeitraum.aus("09:35 bis 09:55"))
        assertNull(Zeitraum.aus("09:55-09:35"))
        assertNull(Zeitraum.aus("25:00-26:00"))
        assertNull(Zeitraum.aus("Pause"))
        assertEquals("09:35-09:55", Zeitraum(575, 595).toString())
    }

    @Test
    fun pausenCountdown() {
        assertEquals("1 h 25 min", Zeiten.pause(pausen, am(8, 10)).gross)
        assertEquals("um 09:35", Zeiten.pause(pausen, am(8, 10)).unten)
        assertEquals("12 min", Zeiten.pause(pausen, am(9, 23)).gross)
        assertEquals("Pause!", Zeiten.pause(pausen, am(9, 40)).oben)
        assertEquals("noch 15 min", Zeiten.pause(pausen, am(9, 40)).gross)
        assertEquals("1 h 30 min", Zeiten.pause(pausen, am(9, 55)).gross) // Ende gehört nicht mehr zur Pause
        assertEquals("Heute keine mehr", Zeiten.pause(pausen, am(12, 0)).gross)
        assertEquals("Wochenende", Zeiten.pause(pausen, am(10, 0, tage = 1)).gross)
        assertEquals("–", Zeiten.pause(emptyList(), am(10, 0)).gross)
    }

    @Test
    fun feierabend() {
        val f = Feierabend(an = true, ab = 17 * 60, bis = 7 * 60, wochenende = true)
        assertFalse(Zeiten.feierabend(f, am(16, 59)))
        assertTrue(Zeiten.feierabend(f, am(17, 0)))
        assertTrue(Zeiten.feierabend(f, am(6, 59)))
        assertFalse(Zeiten.feierabend(f, am(7, 0)))
        assertTrue(Zeiten.feierabend(f, am(12, 0, tage = 1))) // Samstag
        assertFalse(Zeiten.feierabend(f.copy(an = false), am(22, 0)))
        assertFalse(Zeiten.feierabend(f.copy(wochenende = false), am(12, 0, tage = 1)))
        // Freitagabend → frei erst Montag 07:00
        assertEquals(am(7, 0, tage = 3).timeInMillis, Zeiten.feierabendEnde(f, am(18, 30)))
        assertEquals(am(7, 0, tage = 1).timeInMillis, Zeiten.feierabendEnde(f.copy(wochenende = false), am(18, 30)))
    }

    @Test
    fun hinweis() {
        val h = Hinweis("Wandertag", Zeiten.hinweisBis(3, am(10, 0)))
        assertEquals("2026-10-25", h.bis)
        assertTrue(Zeiten.hinweisAktiv(h, am(23, 59, tage = 2)))
        assertFalse(Zeiten.hinweisAktiv(h, am(0, 0, tage = 3)))
        assertFalse(Zeiten.hinweisAktiv(null, am(10, 0)))
        assertEquals("2026-10-23", Zeiten.hinweisBis(1, am(10, 0)))
    }

    private val voll = Konfig(
        elemente = listOf(
            Element.Widget(Element.PAUSE, "p1", 0, 0, 2, 1),
            Element.Widget(Element.QR, "q1", 2, 0, 2, 2, text = "WIFI:T:WPA;S:Gäste;P:geheim;;"),
            Element.Widget(Element.ABWESEND, "a1", 4, 0, 2, 2),
            Ordner("o1", "Mathe", listOf(
                Element.App("de.oejendorferdamm.dammzahlen", "de.oejendorferdamm.dammzahlen.MainActivity", 0, 0, titel = "Zahlen", icon = "00ff00ff.png"),
                Element.App("com.solocode.anton", "com.solocode.anton.MainActivity", 0, 0)
            ), 0, 2)
        ),
        pausen = pausen,
        hinweis = Hinweis("Morgen Wandertag!", "2026-10-30"),
        feierabend = Feierabend(true, 16 * 60 + 30, 6 * 60 + 45, false),
        schoner = 15,
        schueler = SchuelerAnsicht(true, setOf("ordner:o1", "app:com.android.settings/com.android.settings.Settings"), false, 45),
        stand = 1761200000000L
    )

    @Test
    fun hinUndZurueck() {
        val t = KonfigJson.schreibe(voll)
        val e = KonfigJson.lese(t)
        assertTrue(e.warnungen.toString(), e.warnungen.isEmpty())
        assertEquals(voll, e.konfig)
        assertEquals(voll, KonfigJson.streng(t))
        assertEquals(setOf("00ff00ff.png"), KonfigJson.icons(voll))
    }

    @Test
    fun alteVorlageOhneNeueFelderIstSauber() {
        val o = JSONObject(KonfigJson.schreibe(voll))
        listOf("pausen", "hinweis", "feierabend", "schoner", "schueler").forEach { o.remove(it) }
        val k = KonfigJson.streng(o.toString())
        assertEquals(Konfig().pausen, k.pausen)
        assertNull(k.hinweis)
        assertEquals(Feierabend(), k.feierabend)
        assertEquals(0, k.schoner)
        assertEquals(SchuelerAnsicht(), k.schueler)
    }

    @Test
    fun unsinnWirdGemeldet() {
        val o = JSONObject(KonfigJson.schreibe(voll))
        o.put("pausen", org.json.JSONArray().put("10:00-09:00").put("09:35-09:55"))
        o.put("hinweis", JSONObject().put("text", "x").put("bis", "morgen"))
        o.getJSONObject("feierabend").put("ab", "25:99")
        o.put("schoner", 999)
        o.getJSONObject("schueler").put("nurLehrer", org.json.JSONArray().put("<script>"))
        val e = KonfigJson.lese(o.toString())
        assertEquals(e.warnungen.toString(), 5, e.warnungen.size)
        assertEquals(listOf(Zeitraum(575, 595)), e.konfig.pausen)
        assertNull(e.konfig.hinweis)
        assertEquals(17 * 60, e.konfig.feierabend.ab)
        assertEquals(120, e.konfig.schoner)
        assertTrue(e.konfig.schueler.nurLehrer.isEmpty())
    }

    private fun meldung(datum: String) = JSONObject("""{"format":1,"stand":1761200000000,"datum":"$datum","klassen":{"5a":{"fehlen":2,"stunden":1},"5B":{"fehlen":0},"6c":{"fehlen":1}}}""")

    @Test
    fun abwesendKachel() {
        val heute = am(9, 0)
        val o = meldung("2026-10-23")
        assertEquals("2", Abwesenheit.anzeige(o, "5a", heute).gross)
        assertEquals("fehlen heute (1 nur stundenweise)", Abwesenheit.anzeige(o, "5a", heute).unten)
        assertEquals("Alle da!", Abwesenheit.anzeige(o, "5b", heute).gross) // Groß-/Kleinschreibung egal
        assertEquals("fehlt heute", Abwesenheit.anzeige(o, "6c", heute).unten)
        assertEquals("Klasse fehlt in der Meldung", Abwesenheit.anzeige(o, "7a", heute).unten)
        assertEquals("heute noch keine Meldung", Abwesenheit.anzeige(meldung("2026-10-22"), "5a", heute).unten)
        assertEquals("noch keine Meldung", Abwesenheit.anzeige(null, "5a", heute).unten)
        assertEquals("?", Abwesenheit.anzeige(o, "", heute).gross)
    }
}
