/*
 * DammLauncher – Copyright (C) 2026 Teyro – GNU GPL v3 oder neuer, siehe LICENSE
 */
package de.oejendorferdamm.dammlauncher.konfig

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Export/Import als ZIP (konfig.json, hintergrund.jpg, icons/…) und das Hintergrundbild. */
object Datei {
    private const val MAX_EINTRAG = 25L * 1024 * 1024
    /** Gegen „ZIP-Bomben“: höchstens so viele Einträge und Bytes insgesamt. */
    private const val MAX_EINTRAEGE = 500
    private const val MAX_GESAMT = 120L * 1024 * 1024

    fun exportieren(context: Context, ziel: Uri) {
        val k = KonfigSpeicher.konfig
        context.contentResolver.openOutputStream(ziel)?.use { roh ->
            ZipOutputStream(roh.buffered()).use { zip ->
                zip.putNextEntry(ZipEntry("konfig.json"))
                zip.write(KonfigJson.schreibe(k).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                if (k.hintergrund.bild && KonfigSpeicher.hintergrundDatei.exists()) {
                    zip.putNextEntry(ZipEntry("hintergrund.jpg"))
                    KonfigSpeicher.hintergrundDatei.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
                for (name in KonfigJson.icons(k)) {
                    val f = File(KonfigSpeicher.iconOrdner, name)
                    if (!f.exists()) continue
                    zip.putNextEntry(ZipEntry("icons/$name"))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        } ?: throw IOException("Datei lässt sich nicht anlegen")
    }

    /** Liest eine Export-Datei; gibt die Konfiguration und Warnungen zurück. Übernommen wird erst danach. */
    fun importieren(context: Context, quelle: Uri): KonfigJson.Ergebnis {
        var ergebnis: KonfigJson.Ergebnis? = null
        val bilder = HashMap<String, ByteArray>()
        context.contentResolver.openInputStream(quelle)?.use { roh ->
            ZipInputStream(roh.buffered()).use { zip ->
                var anzahl = 0
                var gesamt = 0L
                while (true) {
                    val e = zip.nextEntry ?: break
                    if (e.isDirectory) continue
                    // Was wir nicht brauchen, gar nicht erst lesen
                    val gebraucht = e.name == "konfig.json" || e.name == "hintergrund.jpg" || (e.name.startsWith("icons/") && KonfigJson.ICON_DATEI.matches(e.name.removePrefix("icons/")))
                    if (!gebraucht) continue
                    if (++anzahl > MAX_EINTRAEGE) throw IOException("Zu viele Einträge in der Datei")
                    val daten = lies(zip)
                    gesamt += daten.size
                    if (gesamt > MAX_GESAMT) throw IOException("Datei ist zu groß")
                    when {
                        e.name == "konfig.json" -> ergebnis = KonfigJson.lese(String(daten, Charsets.UTF_8))
                        e.name == "hintergrund.jpg" -> if (istBild(daten)) bilder["hintergrund.jpg"] = daten
                        e.name.startsWith("icons/") && KonfigJson.ICON_DATEI.matches(e.name.removePrefix("icons/")) -> if (istBild(daten)) bilder[e.name] = daten
                    }
                }
            }
        } ?: throw IOException("Datei lässt sich nicht öffnen")
        var e = ergebnis ?: throw KonfigFehler("In der Datei steckt keine DammLauncher-Einrichtung.")
        bilder.forEach { (name, daten) ->
            val ziel = if (name == "hintergrund.jpg") KonfigSpeicher.hintergrundDatei else File(KonfigSpeicher.iconOrdner, name.removePrefix("icons/"))
            File(ziel.parentFile, ziel.name + ".tmp").apply { writeBytes(daten) }.renameTo(ziel)
        }
        if (e.konfig.hintergrund.bild && "hintergrund.jpg" !in bilder) e = e.copy(konfig = e.konfig.copy(hintergrund = e.konfig.hintergrund.copy(bild = false)))
        return e
    }

    private fun lies(zip: ZipInputStream): ByteArray {
        val aus = ByteArrayOutputStream()
        val puffer = ByteArray(64 * 1024)
        while (true) {
            val n = zip.read(puffer)
            if (n < 0) break
            aus.write(puffer, 0, n)
            if (aus.size() > MAX_EINTRAG) throw IOException("Eintrag zu groß")
        }
        return aus.toByteArray()
    }

    private fun istBild(d: ByteArray): Boolean {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(d, 0, d.size, o)
        return o.outWidth > 0
    }

    /**
     * Hintergrundbild einmalig auf Bildschirmgröße bringen (füllend, mittig zugeschnitten) und als
     * JPEG speichern – spart Speicher und RAM bei jedem Start.
     */
    fun hintergrundSpeichern(context: Context, quelle: Uri, breite: Int, hoehe: Int) {
        val roh = context.contentResolver.openInputStream(quelle)?.use { it.readBytes() } ?: throw IOException("Bild lässt sich nicht öffnen")
        if (roh.size > 60 * 1024 * 1024) throw IOException("Bild ist zu groß")
        einpassen(roh, breite, hoehe, KonfigSpeicher.hintergrundDatei)
    }

    /** Bild füllend auf [breite]×[hoehe] zuschneiden und als JPEG nach [ziel] schreiben (über eine .tmp-Datei). */
    fun einpassen(roh: ByteArray, breite: Int, hoehe: Int, ziel: File) {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(roh, 0, roh.size, o)
        if (o.outWidth <= 0) throw IOException("Das ist kein lesbares Bild")
        var probe = 1
        while (o.outWidth / (probe * 2) >= breite && o.outHeight / (probe * 2) >= hoehe) probe *= 2
        val bild = BitmapFactory.decodeByteArray(roh, 0, roh.size, BitmapFactory.Options().apply { inSampleSize = probe }) ?: throw IOException("Das ist kein lesbares Bild")
        val fertig = Bitmap.createBitmap(breite, hoehe, Bitmap.Config.ARGB_8888)
        val k = maxOf(breite.toFloat() / bild.width, hoehe.toFloat() / bild.height)
        val w = bild.width * k
        val h = bild.height * k
        Canvas(fertig).drawBitmap(bild, null, RectF((breite - w) / 2f, (hoehe - h) / 2f, (breite + w) / 2f, (hoehe + h) / 2f), Paint(Paint.FILTER_BITMAP_FLAG))
        bild.recycle()
        val tmp = File(ziel.parentFile, ziel.name + ".tmp")
        tmp.outputStream().use { fertig.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        fertig.recycle()
        if (!tmp.renameTo(ziel)) throw IOException("Bild lässt sich nicht speichern")
    }
}
