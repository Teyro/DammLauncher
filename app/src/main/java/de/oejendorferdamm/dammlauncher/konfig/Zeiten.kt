/*
 * DammLauncher – Copyright (C) 2026 Teyro – GNU GPL v3 oder neuer, siehe LICENSE
 */
package de.oejendorferdamm.dammlauncher.konfig

import java.util.Calendar

/** Drei Zeilen einer eingebauten Kachel: klein oben, groß in der Mitte, klein unten. */
data class Anzeige(val oben: String, val gross: String, val unten: String)

/** Rechnen mit Schulzeiten: Pausen-Countdown, Feierabend-Sperre, Hinweis-Band. */
object Zeiten {
    private fun minuten(c: Calendar) = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
    private fun wochenende(c: Calendar) = c.get(Calendar.DAY_OF_WEEK).let { it == Calendar.SATURDAY || it == Calendar.SUNDAY }

    fun dauer(min: Int) = if (min < 60) "$min min" else "${min / 60} h ${"%02d".format(min % 60)} min"

    /** Was die Pausen-Kachel gerade zeigt. */
    fun pause(pausen: List<Zeitraum>, jetzt: Calendar): Anzeige {
        if (pausen.isEmpty()) return Anzeige("Bis zur Pause", "–", "Pausenzeiten eintragen")
        if (wochenende(jetzt)) return Anzeige("Heute", "Wochenende", "Keine Schule")
        val m = minuten(jetzt)
        pausen.firstOrNull { m >= it.von && m < it.bis }?.let {
            return Anzeige("Pause!", "noch ${dauer(it.bis - m)}", "bis ${Zeitraum.uhrzeit(it.bis)}")
        }
        val naechste = pausen.firstOrNull { it.von > m } ?: return Anzeige("Pause", "Heute keine mehr", "")
        return Anzeige("Bis zur Pause", dauer(naechste.von - m), "um ${Zeitraum.uhrzeit(naechste.von)}")
    }

    /** Liegt [jetzt] in der Feierabend-Zeit? */
    fun feierabend(f: Feierabend, jetzt: Calendar): Boolean {
        if (!f.an) return false
        if (f.wochenende && wochenende(jetzt)) return true
        val m = minuten(jetzt)
        return when {
            f.ab == f.bis -> false
            f.ab > f.bis -> m >= f.ab || m < f.bis // über Mitternacht (üblich: 17:00–07:00)
            else -> m >= f.ab && m < f.bis
        }
    }

    /** Wann endet die Feierabend-Zeit, in der [jetzt] liegt? (ms; höchstens vier Tage voraus) */
    fun feierabendEnde(f: Feierabend, jetzt: Calendar): Long {
        val c = jetzt.clone() as Calendar
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        repeat(4 * 24 * 60) {
            c.add(Calendar.MINUTE, 1)
            if (!feierabend(f, c)) return c.timeInMillis
        }
        return c.timeInMillis
    }

    /** yyyy-MM-dd des Tages (auch für den Vergleich mit [Hinweis.bis]). */
    fun tag(c: Calendar) = "%04d-%02d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))

    fun hinweisAktiv(h: Hinweis?, jetzt: Calendar) = h != null && tag(jetzt) <= h.bis

    /** Ende eines Hinweises, der [tage] Tage gelten soll (1 = nur heute). */
    fun hinweisBis(tage: Int, jetzt: Calendar): String {
        val c = jetzt.clone() as Calendar
        c.add(Calendar.DAY_OF_MONTH, (tage - 1).coerceAtLeast(0))
        return tag(c)
    }

    /** Index der Stunde, die gerade läuft (0 = 1. Stunde), sonst null. */
    fun stundeJetzt(stunden: List<Zeitraum>, jetzt: Calendar): Int? {
        if (wochenende(jetzt)) return null
        val m = minuten(jetzt)
        return stunden.indexOfFirst { m >= it.von && m < it.bis }.takeIf { it >= 0 }
    }

    /** Fach der Stunde [i] am Wochentag von [jetzt] laut [plan] (Mo = 0 … Fr = 4), sonst "". */
    fun fach(plan: List<List<String>>, jetzt: Calendar, i: Int): String {
        val tag = (jetzt.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Montag = 0
        return plan.getOrNull(tag)?.getOrNull(i)?.trim().orEmpty()
    }

    /** Was die Stundenplan-Kachel zeigt: laufende bzw. nächste Stunde mit Fach. */
    fun stunde(stunden: List<Zeitraum>, plan: List<List<String>>, jetzt: Calendar): Anzeige {
        if (stunden.isEmpty()) return Anzeige("Stundenplan", "–", "Stundenzeiten eintragen")
        if (wochenende(jetzt)) return Anzeige("Heute", "Wochenende", "Keine Schule")
        val m = minuten(jetzt)
        stundeJetzt(stunden, jetzt)?.let { i ->
            return Anzeige("${i + 1}. Stunde", fach(plan, jetzt, i).ifEmpty { "Unterricht" }, "bis ${Zeitraum.uhrzeit(stunden[i].bis)} · noch ${dauer(stunden[i].bis - m)}")
        }
        val n = stunden.indexOfFirst { it.von > m }
        if (n < 0) return Anzeige("Heute", "Schluss", "Bis morgen!")
        val f = fach(plan, jetzt, n)
        return Anzeige("Gleich: ${n + 1}. Stunde", f.ifEmpty { "um ${Zeitraum.uhrzeit(stunden[n].von)}" }, if (f.isEmpty()) "in ${dauer(stunden[n].von - m)}" else "um ${Zeitraum.uhrzeit(stunden[n].von)}")
    }

    /** Nachricht, die gerade gezeigt werden soll (die zuerst beginnende), sonst null. */
    fun nachricht(liste: List<Nachricht>, jetzt: Calendar): Nachricht? {
        val t = tag(jetzt)
        val m = minuten(jetzt)
        return liste.filter { it.tag == t && m >= it.von && m < it.bis }.minByOrNull { it.von }
    }

    /** Beginnt in dieser Minute eine Pause? (für „Aufräumen in den Pausen“) */
    fun pausenBeginn(pausen: List<Zeitraum>, jetzt: Calendar) = !wochenende(jetzt) && pausen.any { it.von == minuten(jetzt) }

    /** Stundenplan als Text („Mathe, Deutsch, Sport“ je Wochentag) → Liste je Tag. */
    fun planAus(zeilen: List<String>): List<List<String>> = zeilen.take(5).map { z -> z.split(',', ';').map { it.trim().take(30) }.dropLastWhile { it.isEmpty() }.take(16) }
}
