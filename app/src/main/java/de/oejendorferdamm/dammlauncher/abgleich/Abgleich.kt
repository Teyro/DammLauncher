/*
 * DammLauncher – Copyright (C) 2026 Teyro – GNU GPL v3 oder neuer, siehe LICENSE
 */
package de.oejendorferdamm.dammlauncher.abgleich

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import de.oejendorferdamm.dammlauncher.konfig.Geraet
import de.oejendorferdamm.dammlauncher.konfig.Konfig
import de.oejendorferdamm.dammlauncher.konfig.KonfigJson
import de.oejendorferdamm.dammlauncher.konfig.KonfigSpeicher
import de.oejendorferdamm.dammlauncher.sicherheit.Tresor
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Zentrale Verteilung: Die Boss-Einrichtung liegt als vorlage.json (+ hintergrund.jpg, icons/…) in
 * einem WebDAV-Ordner. Geräte lesen sie (nur Lesezugang auf dem Gerät gespeichert), prüfen sie
 * vollständig und übernehmen sie nur, wenn sie neuer und fehlerfrei ist.
 */
object Abgleich {
    const val VORLAGE = "vorlage.json"
    const val BILD = "hintergrund.jpg"

    /** Lese-Zugang dieses Geräts: (Benutzer, Passwort) aus dem Tresor. */
    fun leseZugang(): Pair<String, String>? {
        val t = Tresor.entschluessle(Geraet.zugangVerschluesselt) ?: return null
        return try {
            JSONObject(t).let { it.getString("b") to it.getString("p") }
        } catch (_: Exception) {
            null
        }
    }

    fun speichereZugang(benutzer: String, passwort: String): Boolean {
        val v = Tresor.verschluessle(JSONObject().put("b", benutzer).put("p", passwort).toString()) ?: return false
        Geraet.zugangVerschluesselt = v
        return true
    }

    private fun zeit() = SimpleDateFormat("dd.MM. HH:mm", Locale.GERMAN).format(Date())

    @Synchronized
    fun pruefen(context: Context, vonHand: Boolean = false): String {
        KonfigSpeicher.init(context)
        Geraet.init(context)
        val ergebnis = try {
            if (!vonHand && !Geraet.abgleichAn) return "Automatischer Abgleich ist aus."
            if (Geraet.server.isBlank()) throw Exception("Noch kein Server eingetragen.")
            val (b, p) = leseZugang() ?: throw Exception("Noch keine Zugangsdaten eingetragen.")
            val dav = WebDav(Geraet.server, b, p)
            val ordner = Geraet.ordner
            val a = dav.get("$ordner/$VORLAGE", max = 2 * 1024 * 1024)
            if (a.code == 404) throw Exception("Im Ordner liegt noch keine Vorlage.")
            val neu = KonfigJson.streng(String(a.daten!!, Charsets.UTF_8))
            if (neu.stand <= Geraet.vorlageStand) "Schon aktuell (Vorlage vom ${datum(neu.stand)})." else {
                uebernehmen(dav, ordner, neu)
                "Neue Vorlage vom ${datum(neu.stand)} übernommen."
            }
        } catch (e: Exception) {
            // Alte Einrichtung bleibt einfach bestehen
            "Fehler: ${e.message ?: "Abgleich fehlgeschlagen"}"
        }
        Geraet.letzterAbgleich = System.currentTimeMillis()
        Geraet.abgleichMeldung = "${zeit()} – $ergebnis"
        return ergebnis
    }

    private fun datum(ms: Long) = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMAN).format(Date(ms))

    private fun uebernehmen(dav: WebDav, ordner: String, neu: Konfig) {
        val protokoll = ArrayList<String>()
        // Eigene Symbole holen (fehlt eines, zeigt die App eben ihr Original)
        for (name in KonfigJson.icons(neu)) {
            val ziel = File(KonfigSpeicher.iconOrdner, name)
            if (ziel.exists()) continue
            try {
                val d = dav.get("$ordner/icons/$name", max = 4 * 1024 * 1024).daten
                if (d != null && BitmapFactory.decodeByteArray(d, 0, d.size) != null) {
                    File(KonfigSpeicher.iconOrdner, "$name.tmp").apply { writeBytes(d) }.renameTo(ziel)
                } else protokoll.add("Symbol $name fehlt")
            } catch (e: Exception) {
                protokoll.add("Symbol $name: ${e.message}")
            }
        }
        var k = neu
        if (neu.hintergrund.bild) {
            val ok = try {
                val d = dav.get("$ordner/$BILD", max = 20 * 1024 * 1024).daten
                if (d != null && pruefeBild(d)) {
                    val tmp = File(KonfigSpeicher.hintergrundDatei.parentFile, "hintergrund.tmp")
                    tmp.writeBytes(d)
                    tmp.renameTo(KonfigSpeicher.hintergrundDatei)
                } else false
            } catch (_: Exception) {
                false
            }
            if (!ok) {
                protokoll.add("Hintergrundbild nicht geladen – nur Farbe")
                k = k.copy(hintergrund = k.hintergrund.copy(bild = false))
            }
        }
        KonfigSpeicher.ersetze(k)
        Geraet.vorlageStand = neu.stand
        if (protokoll.isNotEmpty()) Log.i("DammLauncher", "Abgleich: " + protokoll.joinToString("; "))
    }

    private fun pruefeBild(d: ByteArray): Boolean {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(d, 0, d.size, o)
        return o.outWidth > 0 && o.outHeight > 0
    }

    /**
     * Boss-Modus: aktuelle Einrichtung als Vorlage hochladen – mit den eben eingegebenen
     * SCHREIB-Zugangsdaten (werden nicht gespeichert). Reihenfolge: Symbole, Bild, zuletzt die Vorlage.
     */
    fun hochladen(context: Context, benutzer: String, passwort: String): String = try {
        KonfigSpeicher.init(context)
        if (Geraet.server.isBlank()) throw Exception("Bitte zuerst Server und Ordner eintragen.")
        val dav = WebDav(Geraet.server, benutzer, passwort)
        val ordner = Geraet.ordner
        if (ordner.isNotEmpty()) dav.ordner(ordner)
        val k = KonfigSpeicher.konfig.copy(stand = System.currentTimeMillis())
        val icons = KonfigJson.icons(k)
        if (icons.isNotEmpty()) dav.ordner("$ordner/icons")
        for (name in icons) {
            val f = File(KonfigSpeicher.iconOrdner, name)
            if (f.exists()) dav.putAtomar("$ordner/icons/$name", f.readBytes(), "image/png")
        }
        if (k.hintergrund.bild && KonfigSpeicher.hintergrundDatei.exists()) dav.putAtomar("$ordner/$BILD", KonfigSpeicher.hintergrundDatei.readBytes(), "image/jpeg")
        dav.putAtomar("$ordner/$VORLAGE", KonfigJson.schreibe(k).toByteArray(Charsets.UTF_8), "application/json")
        // Dieses Gerät hat die Vorlage ja schon
        KonfigSpeicher.ersetze(k)
        Geraet.vorlageStand = k.stand
        "Vorlage hochgeladen (${datum(k.stand)})."
    } catch (e: Exception) {
        "Fehler beim Hochladen: ${e.message ?: "unbekannt"}"
    }

    // ------------------------------------------------------------ Zeitsteuerung

    private const val JOB_ID = 3101

    /** Täglicher Abgleich mit Netz (bei jedem Start neu angemeldet, daher ohne Boot-Berechtigung). */
    fun planen(context: Context) {
        Geraet.init(context)
        val js = context.getSystemService(JobScheduler::class.java) ?: return
        if (!Geraet.abgleichAn || Geraet.server.isBlank()) {
            js.cancel(JOB_ID); return
        }
        if (js.getPendingJob(JOB_ID) != null) return
        js.schedule(
            JobInfo.Builder(JOB_ID, ComponentName(context, AbgleichJob::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(24 * 60 * 60_000L)
                .build()
        )
    }

    /** Beim Start: letzte Prüfung über 24 Stunden her? */
    fun faellig(): Boolean = Geraet.abgleichAn && Geraet.server.isNotBlank() && System.currentTimeMillis() - Geraet.letzterAbgleich > 24 * 60 * 60_000L
}

class AbgleichJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        Thread {
            try {
                Abgleich.pruefen(applicationContext)
            } catch (_: Throwable) {
            }
            jobFinished(params, false)
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters) = true
}
