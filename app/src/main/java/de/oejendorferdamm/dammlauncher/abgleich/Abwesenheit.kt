/*
 * DammLauncher – Copyright (C) 2026 Teyro – GNU GPL v3 oder neuer, siehe LICENSE
 */
package de.oejendorferdamm.dammlauncher.abgleich

import android.content.Context
import de.oejendorferdamm.dammlauncher.konfig.Anzeige
import de.oejendorferdamm.dammlauncher.konfig.Geraet
import de.oejendorferdamm.dammlauncher.konfig.Zeiten
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * „Heute fehlen 2“: Das iPhone-Widget „IServ-Abwesenheiten“ legt nur die ANZAHL je Klasse als
 * abwesend.json in den Vorlagen-Ordner – keine Namen. Das Board liest die Datei mit seinem
 * Lese-Zugang und zeigt die Zahl für seine Klasse ([Geraet.klasse]).
 *
 * Format: {"format":1, "stand":ms, "datum":"2026-10-24", "klassen":{"5a":{"fehlen":2,"stunden":1}}}
 */
object Abwesenheit {
    const val DATEI = "abwesend.json"

    /** Gelesene Datei (Text) – liegt in den Geräte-Einstellungen, damit die Kachel sofort etwas zeigt. */
    @Volatile
    private var zwischen: Pair<String, JSONObject?>? = null

    private fun daten(): JSONObject? {
        val t = Geraet.abwesendDaten
        zwischen?.let { if (it.first == t) return it.second }
        val o = try { if (t.isEmpty()) null else JSONObject(t) } catch (_: Exception) { null }
        zwischen = t to o
        return o
    }

    /** Klassen der letzten Meldung (für die Auswahl in den Einstellungen). */
    fun klassen(): List<String> = daten()?.optJSONObject("klassen")?.keys()?.asSequence()?.toList()?.sorted().orEmpty()

    fun anzeige(klasse: String, jetzt: Calendar = Calendar.getInstance()) = anzeige(daten(), klasse, jetzt)

    /** Reine Rechnung (testbar): was die Kachel für [klasse] zeigt. */
    fun anzeige(o: JSONObject?, klasse: String, jetzt: Calendar): Anzeige {
        if (klasse.isBlank()) return Anzeige("Heute fehlen", "?", "Klasse in den Einstellungen wählen")
        val titel = "Klasse $klasse"
        if (o == null) return Anzeige(titel, "–", "noch keine Meldung")
        if (o.optString("datum") != Zeiten.tag(jetzt)) return Anzeige(titel, "–", "heute noch keine Meldung")
        val ks = o.optJSONObject("klassen") ?: return Anzeige(titel, "–", "Meldung unlesbar")
        val name = ks.keys().asSequence().firstOrNull { it.trim().equals(klasse.trim(), ignoreCase = true) }
            ?: return Anzeige(titel, "–", "Klasse fehlt in der Meldung")
        val k = ks.optJSONObject(name)
        val n = k?.optInt("fehlen", -1) ?: ks.optInt(name, -1)
        if (n < 0) return Anzeige(titel, "–", "Meldung unlesbar")
        val stunden = k?.optInt("stunden", 0) ?: 0
        val stand = o.optLong("stand", 0L).takeIf { it > 0 }?.let { " · " + SimpleDateFormat("HH:mm", Locale.GERMAN).format(Date(it)) } ?: ""
        return when (n) {
            0 -> Anzeige(titel + stand, "Alle da!", "heute fehlt niemand")
            else -> Anzeige(titel + stand, "$n", (if (n == 1) "fehlt heute" else "fehlen heute") + if (stunden > 0) " ($stunden nur stundenweise)" else "")
        }
    }

    /** Neu laden (im Hintergrund-Faden). Gibt zurück, ob sich etwas geändert hat. */
    fun laden(context: Context): Boolean {
        Geraet.init(context)
        if (Geraet.server.isBlank()) return false
        val (b, p) = Abgleich.leseZugang() ?: return false
        return try {
            val a = WebDav(Geraet.server, b, p).get("${Geraet.ordner}/$DATEI", max = 256 * 1024)
            val t = if (a.code == 404) "" else String(a.daten ?: return false, Charsets.UTF_8)
            if (t.isNotEmpty()) JSONObject(t) // nur Gültiges merken
            Geraet.abwesendGeladen = System.currentTimeMillis()
            if (t == Geraet.abwesendDaten) false else { Geraet.abwesendDaten = t; true }
        } catch (_: Exception) {
            false
        }
    }
}
