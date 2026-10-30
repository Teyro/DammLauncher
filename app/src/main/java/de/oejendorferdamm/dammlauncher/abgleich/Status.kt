/*
 * DammLauncher – Copyright (C) 2026 Teyro – GNU GPL v3 oder neuer, siehe LICENSE
 */
package de.oejendorferdamm.dammlauncher.abgleich

import android.content.Context
import android.os.Build
import android.os.StatFs
import de.oejendorferdamm.dammlauncher.BuildConfig
import de.oejendorferdamm.dammlauncher.konfig.Geraet
import de.oejendorferdamm.dammlauncher.konfig.KonfigSpeicher
import de.oejendorferdamm.dammlauncher.sicherheit.Tresor
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tafel-Übersicht: Jedes Board legt einmal am Tag (und nach dem Start) einen kleinen Bericht als
 * status/<kennung>.json in den Vorlagen-Ordner – Version, letzter Abgleich, freier Speicher,
 * Abstürze. Dafür gibt es einen eigenen Zugang, der nur in diesen Ordner schreiben können sollte;
 * er wird verschlüsselt gespeichert. Im Boss-Modus liest die Übersicht alle Berichte (Lesezugang).
 */
object Status {
    const val ORDNER = "status"

    data class Bericht(
        val kennung: String, val name: String, val version: String, val zeit: Long, val klasse: String,
        val abgleich: String, val vorlage: Long, val freiMb: Long, val modell: String, val android: Int,
        val profil: String, val abstuerze: List<String>
    )

    /** Zugang dieses Boards, sonst der aus der Vorlage. */
    fun zugang(): Pair<String, String>? {
        Tresor.entschluessle(Geraet.statusZugangVerschluesselt)?.let { t ->
            try { return JSONObject(t).let { it.getString("b") to it.getString("p") } } catch (_: Exception) {}
        }
        return KonfigSpeicher.konfig.statusZugang?.let { it.benutzer to it.passwort }
    }

    fun hatZugang() = Geraet.statusZugangVerschluesselt.isNotEmpty() || KonfigSpeicher.konfig.statusZugang != null

    fun speichereZugang(benutzer: String, passwort: String): Boolean {
        val v = Tresor.verschluessle(JSONObject().put("b", benutzer).put("p", passwort).toString()) ?: return false
        Geraet.statusZugangVerschluesselt = v
        return true
    }

    fun vergissZugang() { Geraet.statusZugangVerschluesselt = "" }

    fun bericht(context: Context): JSONObject {
        val frei = try { StatFs(context.filesDir.absolutePath).availableBytes / (1024 * 1024) } catch (_: Exception) { -1L }
        return JSONObject()
            .put("kennung", Geraet.kennung)
            .put("name", Geraet.name)
            .put("version", BuildConfig.VERSION_NAME)
            .put("zeit", System.currentTimeMillis())
            .put("klasse", Geraet.klasse)
            .put("abgleich", Geraet.abgleichMeldung)
            .put("vorlage", Geraet.vorlageStand)
            .put("freiMb", frei)
            .put("modell", (Build.MANUFACTURER + " " + Build.MODEL).take(60))
            .put("android", Build.VERSION.SDK_INT)
            .put("profil", KonfigSpeicher.konfig.profil)
            .put("abstuerze", JSONArray(Geraet.absturzTexte()))
    }

    /** Fällig: kein Bericht in den letzten 6 Stunden. */
    fun faellig() = Geraet.server.isNotBlank() && hatZugang() && System.currentTimeMillis() - Geraet.letzterStatus > 6 * 60 * 60_000L

    /** Bericht hochladen; gibt eine Meldung zurück. */
    @Synchronized
    fun senden(context: Context): String {
        Geraet.init(context)
        KonfigSpeicher.init(context)
        val t = try {
            if (Geraet.server.isBlank()) throw Exception("Noch kein Server eingetragen.")
            val (b, p) = zugang() ?: throw Exception("Kein Status-Zugang eingetragen.")
            val dav = WebDav(Geraet.server, b, p)
            val ordner = "${Geraet.ordner}/$ORDNER"
            try { dav.ordner(ordner) } catch (_: Exception) { /* darf schon da sein oder nicht anlegbar */ }
            dav.putAtomar("$ordner/${Geraet.kennung}.json", bericht(context).toString(2).toByteArray(Charsets.UTF_8), "application/json")
            Geraet.absturzTexteVergessen()
            "Bericht gesendet"
        } catch (e: Exception) {
            "Fehler: ${e.message ?: "Senden fehlgeschlagen"}"
        }
        Geraet.letzterStatus = System.currentTimeMillis()
        Geraet.statusMeldung = SimpleDateFormat("dd.MM. HH:mm", Locale.GERMAN).format(Date()) + " – " + t
        return t
    }

    /** Alle Berichte lesen (Lesezugang dieses Geräts), neueste zuerst. */
    fun alle(): List<Bericht> {
        if (Geraet.server.isBlank()) throw Exception("Noch kein Server eingetragen.")
        val (b, p) = Abgleich.leseZugang() ?: throw Exception("Noch kein Lesezugang eingetragen.")
        val dav = WebDav(Geraet.server, b, p)
        val ordner = "${Geraet.ordner}/$ORDNER"
        return dav.liste(ordner).filter { it.endsWith(".json") && Regex("^[A-Za-z0-9_-]{1,40}\\.json$").matches(it) }.take(200).mapNotNull { n ->
            try { lies(String(dav.get("$ordner/$n", max = 64 * 1024).daten ?: return@mapNotNull null, Charsets.UTF_8)) } catch (_: Exception) { null }
        }.sortedByDescending { it.zeit }
    }

    fun lies(text: String): Bericht? = try {
        val o = JSONObject(text)
        val a = o.optJSONArray("abstuerze")
        Bericht(
            o.optString("kennung").take(40), o.optString("name").take(40), o.optString("version").take(20), o.optLong("zeit"),
            o.optString("klasse").take(20), o.optString("abgleich").take(300), o.optLong("vorlage"), o.optLong("freiMb", -1),
            o.optString("modell").take(60), o.optInt("android"), o.optString("profil").take(30),
            if (a == null) emptyList() else (0 until minOf(a.length(), 3)).map { a.optString(it).take(3000) }
        )
    } catch (_: Exception) {
        null
    }
}
