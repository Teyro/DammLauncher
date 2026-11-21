/*
 * DammLauncher – Copyright (C) 2026 Teyro – GNU GPL v3 oder neuer, siehe LICENSE
 */
package de.oejendorferdamm.dammlauncher.nutzung

import android.content.Context
import android.content.SharedPreferences
import de.oejendorferdamm.dammlauncher.BuildConfig
import de.oejendorferdamm.dammlauncher.abgleich.WebDav
import de.oejendorferdamm.dammlauncher.konfig.Geraet
import de.oejendorferdamm.dammlauncher.sicherheit.Tresor
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Nutzungsstatistik auf einen eigenen Webserver (WebDAV, z. B. Nextcloud oder IServ):
 * je Board ein Ordner mit dem Bericht als .odt, den Rohdaten als .csv (zum Einlesen in Tabellen)
 * und einer Zusammenfassung als .json (für ein Dashboard). Zugang verschlüsselt, nur https.
 */
object StatistikServer {
    private lateinit var p: SharedPreferences
    fun init(c: Context) { if (!::p.isInitialized) p = c.applicationContext.getSharedPreferences("statistik", Context.MODE_PRIVATE) }

    var adresse: String
        get() = p.getString("adresse", "") ?: ""
        set(v) = p.edit().putString("adresse", v.trim().trimEnd('/')).apply()
    var automatisch: Boolean
        get() = p.getBoolean("auto", false)
        set(v) = p.edit().putBoolean("auto", v).apply()
    var meldung: String
        get() = p.getString("meldung", "") ?: ""
        private set(v) = p.edit().putString("meldung", v).apply()
    private var letzterTag: String
        get() = p.getString("tag", "") ?: ""
        set(v) = p.edit().putString("tag", v).apply()

    fun zugang(): Pair<String, String>? = Tresor.entschluessle(p.getString("zugang", "") ?: "")?.let { t ->
        try { JSONObject(t).let { it.getString("b") to it.getString("p") } } catch (_: Exception) { null }
    }
    fun speichereZugang(benutzer: String, passwort: String): Boolean {
        val v = Tresor.verschluessle(JSONObject().put("b", benutzer).put("p", passwort).toString()) ?: return false
        p.edit().putString("zugang", v).apply(); return true
    }
    fun eingerichtet() = adresse.startsWith("https://") && zugang() != null

    private fun heute() = SimpleDateFormat("yyyy-MM-dd", Locale.GERMAN).format(Date())
    /** Einmal am Tag (TagesJob), wenn eingeschaltet */
    fun faellig() = automatisch && eingerichtet() && letzterTag != heute()

    /** Ordnername des Boards auf dem Server */
    fun boardOrdner() = Geraet.name.ifBlank { Geraet.kennung }.replace(Regex("[^A-Za-z0-9ÄÖÜäöüß _-]+"), "_").trim().take(40).ifBlank { "Board" }

    /** Rohdaten der letzten [tage] Tage als CSV (Semikolon, wie es Calc/Excel auf Deutsch erwartet) */
    fun csv(c: Context, tage: Int): String {
        val von = System.currentTimeMillis() - tage * 24 * 60 * 60_000L
        val f = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.GERMAN)
        val namen = HashMap<String, String>()
        fun feld(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
        return buildString {
            append("board;kennung;art;start;ende;minuten;paket;app\n")
            for (s in Nutzung.sitzungen(von)) {
                val app = if (s.paket.isEmpty()) "" else namen.getOrPut(s.paket) { appName(c, s.paket) }
                append(listOf(feld(Geraet.name), Geraet.kennung, if (s.typ == Sitzung.BILDSCHIRM) "bildschirm" else "app", f.format(Date(s.start)), f.format(Date(s.ende)),
                    "%.1f".format(Locale.GERMAN, s.dauer / 60_000.0), s.paket, feld(app)).joinToString(";")).append('\n')
            }
        }
    }

    /** Zusammenfassung je Tag als JSON */
    fun json(c: Context, tage: Int): JSONObject {
        val jetzt = System.currentTimeMillis()
        val liste = Auswertung.tage(Nutzung.sitzungen(jetzt - (tage + 1) * 24 * 60 * 60_000L), jetzt, tage)
        val f = SimpleDateFormat("yyyy-MM-dd", Locale.GERMAN)
        return JSONObject().put("board", Geraet.name).put("kennung", Geraet.kennung).put("version", BuildConfig.VERSION_NAME).put("erstellt", jetzt)
            .put("tage", JSONArray().apply {
                for (t in liste) put(JSONObject().put("datum", f.format(Date(t.beginn))).put("anMinuten", t.anMs / 60_000).put("appMinuten", t.appMs / 60_000)
                    .put("erstesAn", t.erstesAn ?: JSONObject.NULL).put("letztesAus", t.letztesAus ?: JSONObject.NULL)
                    .put("apps", JSONObject().apply { t.apps.groupBy { it.paket }.forEach { (pk, s) -> put(appName(c, pk), s.sumOf { it.dauer } / 60_000) } }))
            })
    }

    /** Alles hochladen; gibt eine Meldung zurück (wirft nie) */
    fun hochladen(c: Context, tage: Int = 14): String {
        init(c)
        val z = zugang() ?: return "Kein Zugang eingetragen.".also { meldung = it }
        return try {
            val dav = WebDav(adresse, z.first, z.second)
            val ordner = boardOrdner()
            dav.ordner(ordner)
            dav.putAtomar("$ordner/${nutzungDateiname()}", c.nutzungBericht(tage), OdtBericht.MIME)
            dav.putAtomar("$ordner/nutzung.csv", csv(c, tage).toByteArray(Charsets.UTF_8), "text/csv")
            dav.putAtomar("$ordner/nutzung.json", json(c, tage).toString(1).toByteArray(Charsets.UTF_8), "application/json")
            letzterTag = heute()
            "Hochgeladen ${SimpleDateFormat("dd.MM. HH:mm", Locale.GERMAN).format(Date())} nach „$ordner“ (.odt, .csv, .json).".also { meldung = it }
        } catch (e: Exception) {
            "Hochladen fehlgeschlagen: ${e.message}".also { meldung = it }
        }
    }
}
