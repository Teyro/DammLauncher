/*
 * DammLauncher – Copyright (C) 2026 Teyro – GNU GPL v3 oder neuer, siehe LICENSE
 */
package de.oejendorferdamm.dammlauncher.konfig

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File

/**
 * Hält die aktuelle Konfiguration und speichert sie als kleine JSON-Datei. Vor jedem Überschreiben
 * wird die bisherige Datei als „letzte funktionierende“ Sicherung aufgehoben. Lässt sich die Datei
 * nicht lesen, wird die Sicherung genommen, sonst die Standard-Einrichtung – nie ein Absturz.
 */
object KonfigSpeicher {
    private lateinit var ordner: File
    private val haupt = Handler(Looper.getMainLooper())
    private val beobachter = ArrayList<(Konfig) -> Unit>()

    @Volatile
    var konfig: Konfig = Konfig()
        private set

    /** Warnungen beim letzten Einlesen (für die Einstellungen). */
    @Volatile
    var warnungen: List<String> = emptyList()
        private set

    val datei get() = File(ordner, "konfig.json")
    val sicherung get() = File(ordner, "konfig_sicherung.json")
    val hintergrundDatei get() = File(ordner, "hintergrund.jpg")
    /** Eigene App-Symbole (Dateiname = Prüfsumme). */
    val iconOrdner get() = File(ordner, "icons").apply { mkdirs() }

    @Synchronized
    fun init(context: Context, sicher: Boolean = false) {
        if (::ordner.isInitialized && !sicher) return
        ordner = context.applicationContext.filesDir
        konfig = if (sicher) ladeAus(sicherung) ?: Konfig() else ladeAus(datei) ?: ladeAus(sicherung) ?: Konfig()
    }

    private fun ladeAus(f: File): Konfig? = try {
        if (!f.exists()) null else KonfigJson.lese(f.readText()).also { warnungen = it.warnungen }.konfig
    } catch (e: Exception) {
        Log.w("DammLauncher", "Konfiguration ${f.name} unlesbar: ${e.message}")
        null
    }

    /** Ändern und speichern; Beobachter (Startbildschirm) werden auf dem Haupt-Faden benachrichtigt. */
    @Synchronized
    fun aendere(aenderung: (Konfig) -> Konfig) {
        val neu = aenderung(konfig)
        if (neu == konfig) return
        konfig = neu.copy(stand = System.currentTimeMillis())
        schreibe(konfig)
        melde()
    }

    /** Ganze Konfiguration ersetzen (Import, Vorlage) – ohne den Zeitstempel zu ändern. */
    @Synchronized
    fun ersetze(neu: Konfig) {
        // Die bisherige, funktionierende Einrichtung als Sicherung aufheben
        if (datei.exists()) datei.copyTo(sicherung, overwrite = true)
        konfig = neu
        schreibe(neu)
        melde()
    }

    private fun schreibe(k: Konfig) {
        try {
            val tmp = File(ordner, "konfig.json.tmp")
            tmp.writeText(KonfigJson.schreibe(k))
            if (!tmp.renameTo(datei)) {
                datei.delete()
                tmp.renameTo(datei)
            }
        } catch (e: Exception) {
            Log.w("DammLauncher", "Speichern fehlgeschlagen: ${e.message}")
        }
    }

    fun beobachte(b: (Konfig) -> Unit) = synchronized(beobachter) { beobachter.add(b) }
    fun vergiss(b: (Konfig) -> Unit) = synchronized(beobachter) { beobachter.remove(b) }

    private fun melde() {
        val k = konfig
        val liste = synchronized(beobachter) { beobachter.toList() }
        haupt.post { liste.forEach { it(k) } }
    }
}
