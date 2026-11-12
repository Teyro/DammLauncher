/*
 * DammLauncher – Copyright (C) 2026 Teyro – GNU GPL v3 oder neuer, siehe LICENSE
 */
package de.oejendorferdamm.dammlauncher.geburtstag

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import de.oejendorferdamm.dammlauncher.abgleich.SchulOrdner
import java.io.File

/**
 * Geburtstag (wie das Überraschungsvideo in DammBoard): Schalter an → beim ersten Antippen läuft
 * „geburtstag.mp4“, danach löst jede Berührung Konfetti und Luftballons aus – bis man ihn wieder ausschaltet.
 * Das Video bleibt auf dem Board (nie in der App): einmal gewählt oder aus dem Schul-Ordner
 * („DammLauncher/geburtstag.mp4“, sonst „DammBoard/geburtstag.mp4“ – derselbe Ordner wie „ueberraschung.mp4“).
 */
object Geburtstag {
    const val NAME = "geburtstag.mp4"
    private const val MAX = 150 * 1024 * 1024
    private val ORDNER = listOf("DammLauncher", "DammBoard")

    fun datei(context: Context) = File(context.filesDir, NAME)
    fun vorhanden(context: Context) = datei(context).let { it.exists() && it.length() > 0 }

    private fun istVideo(f: File) = try {
        MediaMetadataRetriever().run { try { setDataSource(f.absolutePath); extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes" } finally { release() } }
    } catch (_: Exception) { false }

    private fun uebernehmen(context: Context, schreiben: (File) -> Unit): Boolean {
        val ziel = datei(context)
        val tmp = File(ziel.parentFile, "$NAME.tmp")
        return try {
            schreiben(tmp)
            if (tmp.length() in 1..MAX.toLong() && istVideo(tmp)) tmp.renameTo(ziel) else { tmp.delete(); false }
        } catch (_: Exception) { tmp.delete(); false }
    }

    /** Gewähltes Video kopieren (die Freigabe der Dateiauswahl hält nicht ewig) */
    fun ausAuswahl(context: Context, quelle: Uri): Boolean = uebernehmen(context) { tmp ->
        context.contentResolver.openInputStream(quelle)?.use { ein -> tmp.outputStream().use { aus -> ein.copyTo(aus, 256 * 1024) } } ?: throw IllegalStateException()
    }

    /** Aus dem Schul-Ordner holen, falls dort eins liegt (und noch nicht dasselbe hier ist); true = vorhanden */
    fun ausSchulOrdner(context: Context): Boolean {
        val pw = SchulOrdner.passwort() ?: return vorhanden(context)
        val dav = SchulOrdner.dav(pw)
        for (o in ORDNER) {
            val namen = try { dav.liste(o) } catch (_: Exception) { continue }
            val name = namen.firstOrNull { it.equals(NAME, ignoreCase = true) } ?: continue
            val daten = try { dav.get("$o/$name", max = MAX).daten } catch (_: Exception) { null } ?: continue
            if (vorhanden(context) && datei(context).length() == daten.size.toLong()) return true
            return uebernehmen(context) { it.writeBytes(daten) }
        }
        return vorhanden(context)
    }

    fun entfernen(context: Context) = datei(context).delete()
}
