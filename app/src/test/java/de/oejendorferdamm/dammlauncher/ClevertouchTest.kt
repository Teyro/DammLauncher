package de.oejendorferdamm.dammlauncher

import de.oejendorferdamm.dammlauncher.abgleich.Status
import de.oejendorferdamm.dammlauncher.abgleich.WebDav
import de.oejendorferdamm.dammlauncher.konfig.Aufraeumen
import de.oejendorferdamm.dammlauncher.konfig.Aussehen
import de.oejendorferdamm.dammlauncher.konfig.Element
import de.oejendorferdamm.dammlauncher.konfig.Konfig
import de.oejendorferdamm.dammlauncher.konfig.KonfigJson
import de.oejendorferdamm.dammlauncher.konfig.Nachricht
import de.oejendorferdamm.dammlauncher.konfig.Profil
import de.oejendorferdamm.dammlauncher.konfig.Profile
import de.oejendorferdamm.dammlauncher.konfig.Seitenleiste
import de.oejendorferdamm.dammlauncher.konfig.SymbolForm
import de.oejendorferdamm.dammlauncher.konfig.Zeiten
import de.oejendorferdamm.dammlauncher.konfig.Zeitraum
import de.oejendorferdamm.dammlauncher.konfig.Zugang
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/** 1.6.0: Clevertouch-Aussehen, Dock, Seitenleiste, Stundenplan, Nachrichten, Aufräumen, Profile, Tafel-Übersicht. */
class ClevertouchTest {
    /** Freitag, 23.10.2026 um [h]:[m] (+[tage]). */
    private fun am(h: Int, m: Int, tage: Int = 0) = Calendar.getInstance().apply {
        clear(); set(2026, Calendar.OCTOBER, 23 + tage, h, m, 0)
    }

    private val firefox = Element.App("org.mozilla.firefox", "org.mozilla.fenix.HomeActivity", 0, 0)
    private val board = Element.App("de.oejendorferdamm.dammboard", "de.oejendorferdamm.dammboard.MainActivity", 0, 0, titel = "Tafel")

    private val voll = Konfig(
        aussehen = Aussehen.CLEVERTOUCH,
        symbolForm = SymbolForm.KREIS,
        dock = listOf(firefox, board),
        seitenleiste = Seitenleiste(true, Seitenleiste.LINKS, listOf(board)),
        stunden = listOf(Zeitraum(8 * 60, 8 * 60 + 45), Zeitraum(8 * 60 + 50, 9 * 60 + 35)),
        nachrichten = listOf(Nachricht("Probealarm um 10:15", "2026-10-23", 10 * 60, 10 * 60 + 30)),
        aufraeumen = Aufraeumen(pausen = true, feierabend = false, lautstaerke = -1),
        elemente = listOf(firefox.copy(spalte = 1), Element.Widget(Element.ZUFALL, "k12345678", 2, 0, 2, 2, text = "1-28")),
        profil = "Mathe",
        profile = listOf(Profil("Standard", listOf(Element.Widget(Element.UHR, "uhr", 0, 0, 3, 2))), Profil("Deutsch", listOf(board.copy(spalte = 3)))),
        profilNachFach = true,
        statusZugang = Zugang("status", "geheim123"),
        stand = 1760000000000L
    )

    @Test
    fun hinUndZurueck() {
        val text = KonfigJson.schreibe(voll)
        val e = KonfigJson.lese(text)
        assertTrue(e.warnungen.toString(), e.warnungen.isEmpty())
        assertEquals(voll, e.konfig)
        assertEquals(voll, KonfigJson.streng(text))
    }

    @Test
    fun aeltereVorlageOhneNeueFelderGibtKeineWarnung() {
        val o = JSONObject(KonfigJson.schreibe(Konfig(stand = 5L)))
        listOf("aussehen", "symbolForm", "dock", "seitenleiste", "stunden", "nachrichten", "aufraeumen", "profil", "profile", "profilNachFach").forEach { o.remove(it) }
        val e = KonfigJson.lese(o.toString())
        assertTrue(e.warnungen.toString(), e.warnungen.isEmpty())
        assertEquals(Aussehen.KLASSISCH, e.konfig.aussehen)
        assertEquals(Profil.STANDARD, e.konfig.profil)
        assertEquals(Aufraeumen(), e.konfig.aufraeumen)
    }

    @Test
    fun unsinnWirdRepariert() {
        val o = JSONObject(KonfigJson.schreibe(voll))
        o.put("aussehen", "lila")
        o.getJSONArray("dock").put(JSONObject().put("typ", "widget").put("provider", Element.UHR).put("kennung", "x").put("spalte", 0).put("zeile", 0))
        o.getJSONArray("nachrichten").put(JSONObject().put("text", "Kaputt").put("tag", "gestern").put("von", "10:00").put("bis", "09:00"))
        // dasselbe Widget in zwei Profilen: nur einmal
        o.getJSONArray("profile").getJSONObject(1).getJSONArray("elemente")
            .put(JSONObject().put("typ", "widget").put("provider", "com.x.y/com.x.y.W").put("kennung", "w1").put("spalte", 0).put("zeile", 0).put("breite", 1).put("hoehe", 1))
        o.put("elemente", o.getJSONArray("elemente").put(JSONObject().put("typ", "widget").put("provider", "com.x.y/com.x.y.W").put("kennung", "w1").put("spalte", 5).put("zeile", 3).put("breite", 1).put("hoehe", 1)))
        val e = KonfigJson.lese(o.toString())
        assertTrue(e.warnungen.any { it.startsWith("aussehen") })
        assertTrue(e.warnungen.any { it.startsWith("dock") })
        assertTrue(e.warnungen.any { it.startsWith("nachrichten") })
        assertTrue(e.warnungen.any { it.contains("doppelte Widgets") })
        assertEquals(Aussehen.KLASSISCH, e.konfig.aussehen)
        assertEquals(2, e.konfig.dock.size)
        assertEquals(1, e.konfig.nachrichten.size)
        assertEquals(1, KonfigJson.alleElemente(e.konfig).count { it.schluessel == "widget:w1" })
    }

    @Test
    fun profileWechselnUndVerwalten() {
        val k = voll
        assertEquals(listOf("Mathe", "Standard", "Deutsch"), Profile.namen(k))
        val d = Profile.wechseln(k, "deutsch")
        assertEquals("Deutsch", d.profil)
        assertEquals(listOf(board.copy(spalte = 3)), d.elemente)
        assertTrue(d.profile.any { it.name == "Mathe" && it.elemente == k.elemente })
        // hin und zurück: alles wie vorher (nur die Reihenfolge der gespeicherten Profile ändert sich)
        val zurueck = Profile.wechseln(d, "Mathe")
        assertEquals(k.elemente, zurueck.elemente)
        assertEquals(k.profile.toSet(), zurueck.profile.toSet())
        assertEquals(k, Profile.wechseln(k, "Gibt es nicht"))
        // neu: Kopie ohne fremde Widgets, eingebaute Kacheln mit neuer Kennung
        val n = Profile.neu(k, "Sport", kopie = true)!!
        val sport = n.profile.first { it.name == "Sport" }
        assertEquals(2, sport.elemente.size)
        assertFalse(sport.elemente.any { it is Element.Widget && it.kennung == "k12345678" })
        assertNull(Profile.neu(k, "mathe", kopie = false))
        assertNull(Profile.neu(k, "  ", kopie = false))
        assertEquals("Algebra", Profile.umbenennen(k, "Mathe", "Algebra")!!.profil)
        assertNull(Profile.umbenennen(k, "Mathe", "Deutsch"))
        assertEquals(listOf("Mathe", "Standard"), Profile.namen(Profile.loeschen(k, "Deutsch")))
    }

    @Test
    fun stundenplanKachel() {
        val s = voll.stunden
        val plan = Zeiten.planAus(listOf("Mo1", "Di1", "Mi1", "Do1", "Mathe, Deutsch"))
        assertEquals(Zeiten.stunde(s, plan, am(8, 10)).let { it.oben to it.gross }, "1. Stunde" to "Mathe")
        assertEquals("bis 08:45 · noch 35 min", Zeiten.stunde(s, plan, am(8, 10)).unten)
        assertEquals("Gleich: 2. Stunde", Zeiten.stunde(s, plan, am(8, 46)).oben)
        assertEquals("Deutsch", Zeiten.stunde(s, plan, am(8, 46)).gross)
        assertEquals("Schluss", Zeiten.stunde(s, plan, am(12, 0)).gross)
        assertEquals("Wochenende", Zeiten.stunde(s, plan, am(9, 0, tage = 1)).gross)
        assertEquals("Unterricht", Zeiten.stunde(s, emptyList(), am(8, 10)).gross)
        assertEquals(1, Zeiten.stundeJetzt(s, am(9, 0)))
        assertNull(Zeiten.stundeJetzt(s, am(8, 47)))
        assertEquals("Deutsch", Zeiten.fach(plan, am(9, 0), 1))
        assertEquals("", Zeiten.fach(plan, am(9, 0), 7))
    }

    @Test
    fun nachrichtenUndPausen() {
        val n = voll.nachrichten
        assertNotNull(Zeiten.nachricht(n, am(10, 0)))
        assertNull(Zeiten.nachricht(n, am(10, 30)))
        assertNull(Zeiten.nachricht(n, am(10, 5, tage = 3)))
        val p = listOf(Zeitraum(9 * 60 + 35, 9 * 60 + 55))
        assertTrue(Zeiten.pausenBeginn(p, am(9, 35)))
        assertFalse(Zeiten.pausenBeginn(p, am(9, 36)))
        assertFalse(Zeiten.pausenBeginn(p, am(9, 35, tage = 1)))
    }

    @Test
    fun webDavListeUndStatusBericht() {
        val xml = """<?xml version="1.0"?><d:multistatus xmlns:d="DAV:">
            <d:response><d:href>/webdav/Files/DammLauncher/status/</d:href></d:response>
            <d:response><d:href>/webdav/Files/DammLauncher/status/abc123.json</d:href></d:response>
            <d:response><D:href>/webdav/Files/DammLauncher/status/Raum%202b.json</D:href></d:response>
            <d:response><d:href>/webdav/Files/DammLauncher/status/unter/</d:href></d:response></d:multistatus>"""
        assertEquals(listOf("abc123.json", "Raum 2b.json"), WebDav.namenAus(xml))
        val b = Status.lies("""{"kennung":"abc123","name":"Raum 2b","version":"1.6.0","zeit":5,"freiMb":1200,"abstuerze":["x","y"]}""")!!
        assertEquals("Raum 2b", b.name)
        assertEquals(1200L, b.freiMb)
        assertEquals(listOf("x", "y"), b.abstuerze)
        assertNull(Status.lies("kein json"))
    }
}
