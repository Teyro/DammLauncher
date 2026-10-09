/*
 * DammLauncher – Copyright (C) 2026 Teyro – GNU GPL v3 oder neuer, siehe LICENSE
 */
package de.oejendorferdamm.dammlauncher.apps

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import de.oejendorferdamm.dammlauncher.konfig.KonfigJson
import de.oejendorferdamm.dammlauncher.konfig.KonfigSpeicher
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest

/**
 * Eigene Symbole für Apps auf dem Startbildschirm: ein Bild (PNG, JPG …) oder das Symbol einer
 * anderen App. Gespeichert wird immer ein PNG mit 512 × 512 px, Dateiname = Prüfsumme – so geht es
 * mit Export und Vorlage auch auf Geräte, auf denen die andere App fehlt.
 */
object EigeneSymbole {
    private const val GROESSE = 512
    private const val MAX_DATEI = 20L * 1024 * 1024

    /** Bild aus der Dateiauswahl übernehmen; gibt den Dateinamen zurück (null = kein lesbares Bild). */
    fun ausBild(context: Context, uri: Uri): String? {
        val daten = context.contentResolver.openInputStream(uri)?.use { ein ->
            val aus = ByteArrayOutputStream()
            val puffer = ByteArray(64 * 1024)
            while (true) {
                val n = ein.read(puffer)
                if (n < 0) break
                aus.write(puffer, 0, n)
                if (aus.size() > MAX_DATEI) return null
            }
            aus.toByteArray()
        } ?: return null
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(daten, 0, daten.size, o)
        if (o.outWidth <= 0) return null
        var probe = 1
        while (maxOf(o.outWidth, o.outHeight) / (probe * 2) >= GROESSE) probe *= 2
        val roh = BitmapFactory.decodeByteArray(daten, 0, daten.size, BitmapFactory.Options().apply { inSampleSize = probe }) ?: return null
        val b = Bitmap.createBitmap(GROESSE, GROESSE, Bitmap.Config.ARGB_8888)
        val k = minOf(GROESSE.toFloat() / roh.width, GROESSE.toFloat() / roh.height)
        val w = roh.width * k
        val h = roh.height * k
        Canvas(b).drawBitmap(roh, null, RectF((GROESSE - w) / 2, (GROESSE - h) / 2, (GROESSE + w) / 2, (GROESSE + h) / 2), Paint(Paint.FILTER_BITMAP_FLAG))
        return speichere(b)
    }

    /** Symbol einer anderen installierten App übernehmen. */
    fun ausApp(app: ComponentName): String? {
        val d = AppKatalog.originalIcon(app) ?: return null
        return speichere(AppKatalog.zeichne(d, GROESSE))
    }

    private fun speichere(b: Bitmap): String {
        val aus = ByteArrayOutputStream()
        b.compress(Bitmap.CompressFormat.PNG, 100, aus)
        val daten = aus.toByteArray()
        val name = MessageDigest.getInstance("SHA-256").digest(daten).joinToString("") { "%02x".format(it) }.take(32) + ".png"
        val ziel = File(KonfigSpeicher.iconOrdner, name)
        if (!ziel.exists()) {
            val tmp = File(KonfigSpeicher.iconOrdner, "$name.tmp")
            tmp.writeBytes(daten)
            tmp.renameTo(ziel)
        }
        return name
    }

    /** Nicht mehr benutzte Symbole löschen. */
    fun aufraeumen() {
        val benutzt = KonfigJson.icons(KonfigSpeicher.konfig)
        KonfigSpeicher.iconOrdner.listFiles()?.forEach { if (it.name !in benutzt) it.delete() }
    }
}
