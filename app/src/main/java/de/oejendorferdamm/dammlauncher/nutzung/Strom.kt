/*
 * DammLauncher – Copyright (C) 2026 Teyro – GNU GPL v3 oder neuer, siehe LICENSE
 */
package de.oejendorferdamm.dammlauncher.nutzung

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Wann war das Board ohne Herunterfahren aus (Stecker gezogen, Strom weg)? Mit Schulleitung und Personalrat abgestimmt:
 * erfasst wird nur das Board, keine Person.
 *
 * So geht es: jede Minute ein Puls. Fährt Android normal herunter, kommt vorher ACTION_SHUTDOWN – das wird vermerkt.
 * Startet das Board neu, ohne dass dieser Vermerk nach dem letzten Puls steht, war der Strom weg – ab dem letzten Puls
 * (auf etwa eine Minute genau) bis zum Neustart.
 */
object Strom {
    /** Ein Ausfall: [von] = letzter Puls, [bis] = Neustart; [sauber] = normal heruntergefahren */
    data class Ereignis(val von: Long, val bis: Long, val sauber: Boolean)

    /** Reine Regel (testbar): beim Start entscheiden, ob und wie das Board aus war. [laufzeit] = ms seit dem Einschalten. */
    fun bewerte(letzterPuls: Long, herunterGefahren: Long, jetzt: Long, laufzeit: Long): Ereignis? {
        if (letzterPuls <= 0) return null
        // Board lief durch (nur der Launcher wurde neu gestartet) – kein Ausfall
        if (laufzeit > jetzt - letzterPuls) return null
        val bootZeit = jetzt - laufzeit
        if (bootZeit - letzterPuls < 2 * 60_000L) return null // zu kurz zum Zählen (schneller Neustart)
        return Ereignis(letzterPuls, bootZeit, sauber = herunterGefahren >= letzterPuls - 60_000L)
    }

    private const val MAX = 200
    private fun p(c: Context) = c.getSharedPreferences("strom", Context.MODE_PRIVATE)

    fun puls(c: Context) = p(c).edit().putLong("puls", System.currentTimeMillis()).apply()

    /** Aus dem Empfänger für ACTION_SHUTDOWN – synchron schreiben, das Gerät geht gleich aus */
    fun herunterfahren(c: Context) { p(c).edit().putLong("herunter", System.currentTimeMillis()).commit() }

    /** Beim Start des Launchers: Ausfall erkennen und vermerken */
    fun beimStart(c: Context) {
        val e = bewerte(p(c).getLong("puls", 0), p(c).getLong("herunter", 0), System.currentTimeMillis(), android.os.SystemClock.elapsedRealtime())
        if (e != null) {
            val alt = liste(c)
            speichere(c, (alt + e).takeLast(MAX))
        }
        puls(c)
    }

    fun liste(c: Context): List<Ereignis> = try {
        val a = JSONArray(p(c).getString("liste", "[]"))
        (0 until a.length()).map { a.getJSONObject(it) }.map { Ereignis(it.getLong("von"), it.getLong("bis"), it.optBoolean("sauber")) }
    } catch (_: Exception) { emptyList() }

    private fun speichere(c: Context, l: List<Ereignis>) = p(c).edit().putString("liste", JSONArray(l.map { e ->
        JSONObject().put("von", e.von).put("bis", e.bis).put("sauber", e.sauber)
    }).toString()).apply()

    fun alsJson(l: List<Ereignis>) = JSONArray(l.map { JSONObject().put("von", it.von).put("bis", it.bis).put("sauber", it.sauber) })
}
