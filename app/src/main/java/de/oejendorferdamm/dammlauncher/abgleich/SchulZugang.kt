/*
 * DammLauncher – Copyright (C) 2026 Teyro – GNU GPL v3 oder neuer, siehe LICENSE
 */
package de.oejendorferdamm.dammlauncher.abgleich

import android.content.Context
import org.json.JSONObject

/**
 * Nextcloud-Zugang der Schule aus DammTable (verschlüsselt über dessen Update-Kanal verteilt, nur für Apps mit
 * demselben Signaturschlüssel lesbar). Die Vorlagen liegen dann nach Jahrgängen sortiert:
 * DammLauncher/Alle, DammLauncher/Vorschule, DammLauncher/Jahrgang 1 … 4, DammLauncher/Klasse 2b.
 */
object SchulZugang {
    data class Zugang(val dav: String, val benutzer: String, val passwort: String, val klasse: String)

    const val WURZEL = "DammLauncher"
    const val ALLE = "$WURZEL/Alle"

    /** Jahrgang aus der Klasse: „VSK a“ → 0, „2b“ → 2, sonst null */
    fun stufe(klasse: String): Int? = if (klasse.trim().lowercase().startsWith("vs")) 0 else klasse.firstOrNull { it.isDigit() }?.digitToInt()

    fun jahrgangName(stufe: Int) = if (stufe == 0) "Vorschule" else "Jahrgang $stufe"
    fun jahrgangOrdner(klasse: String): String? = stufe(klasse)?.let { "$WURZEL/${jahrgangName(it)}" }
    fun klassenOrdner(klasse: String): String? = klasse.trim().ifBlank { null }?.let { "$WURZEL/Klasse ${it.replace('/', '-')}" }

    /** Wo dieses Board nach Vorlagen schaut: eigene Klasse, Jahrgang, Alle – die neueste gewinnt */
    fun ordner(z: Zugang): List<String> = listOfNotNull(klassenOrdner(z.klasse), jahrgangOrdner(z.klasse), ALLE)

    fun davAdresse(server: String, benutzer: String): String {
        var s = server.trim().trimEnd('/')
        if (!s.startsWith("http")) s = "https://$s"
        if (s.contains("/remote.php/")) return s
        s = s.substringBefore("/index.php").substringBefore("/s/")
        return "$s/remote.php/dav/files/" + java.net.URLEncoder.encode(benutzer.trim(), "UTF-8").replace("+", "%20")
    }

    /** Antwort von DammTable (/zugaenge) → Zugang; Passwort des Hauses nach Jahrgang der Klasse */
    fun aus(json: String): Zugang? = try {
        val o = JSONObject(json)
        val nc = o.optJSONObject("nextcloud")
        val klasse = o.optString("klasse").trim().lowercase()
        val stufe = stufe(klasse)
        val haeuser = nc?.optJSONArray("haeuser")
        val passwort = (0 until (haeuser?.length() ?: 0)).map { haeuser!!.getJSONObject(it) }.firstOrNull { h ->
            val j = h.optJSONArray("jahrgaenge"); stufe != null && (0 until (j?.length() ?: 0)).any { j!!.optInt(it, -1) == stufe }
        }?.optString("passwort").orEmpty()
        val server = nc?.optString("server").orEmpty()
        val benutzer = nc?.optString("benutzer").orEmpty()
        if (server.isBlank() || benutzer.isBlank() || passwort.isBlank() || klasse.isBlank()) null
        else Zugang(davAdresse(server, benutzer), benutzer, passwort, klasse)
    } catch (_: Exception) { null }

    fun lese(context: Context): Zugang? = try {
        context.contentResolver.query(android.net.Uri.parse("content://de.oejendorferdamm.dammtable.stundenplan/zugaenge"), null, null, null, null)?.use { c ->
            if (c.moveToFirst()) aus(c.getString(0)) else null
        }
    } catch (_: Exception) { null } // DammTable fehlt, ist zu alt oder anders signiert
}
