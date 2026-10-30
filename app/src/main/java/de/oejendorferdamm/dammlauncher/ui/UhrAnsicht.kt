/*
 * DammLauncher – Copyright (C) 2026 Teyro – GNU GPL v3 oder neuer, siehe LICENSE
 */
package de.oejendorferdamm.dammlauncher.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import android.view.ViewOutlineProvider
import de.oejendorferdamm.dammlauncher.konfig.Schrift
import de.oejendorferdamm.dammlauncher.konfig.UhrStil
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Die eingebaute Digitaluhr: große Uhrzeit, Datum darüber, darunter oder gar nicht – auf weißer
 * Fläche mit dunklem Rahmen oder [transparent] (weiße Schrift mit Schatten). Alle Größen werden
 * aus dem Feld berechnet: die Uhr wächst mit, wenn man sie größer zieht.
 */
class UhrAnsicht(context: Context, transparent: Boolean = false, stil: UhrStil = UhrStil()) : View(context) {
    private val flaeche = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    private val rahmen = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1F2937.toInt(); style = Paint.Style.STROKE }
    private val zeit = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val datum = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val feld = RectF()
    private val masse = Rect()
    private var uhrzeit = SimpleDateFormat("HH:mm", Locale.GERMAN)

    /** Mit Sekunden: genau zum Sekundenwechsel neu zeichnen, solange die Uhr zu sehen ist. */
    private val sekundenTakt = object : Runnable {
        override fun run() {
            invalidate()
            if (stil.sekunden && isAttachedToWindow) postDelayed(this, 1000L - System.currentTimeMillis() % 1000L + 5L)
        }
    }
    private var tag = SimpleDateFormat("EEEE, d. MMMM", Locale.GERMAN)

    /** Zuletzt berechnete Schriftgrößen – nur neu rechnen, wenn sich Größe oder Text ändern. */
    private var gemessenFuer = ""

    var transparent = transparent
        set(v) {
            if (field == v) return
            field = v
            farben()
        }

    var stil = stil
        set(v) {
            if (field == v) return
            field = v
            schrift()
        }

    /** Minutenwechsel, Uhr gestellt, Zeitzone – das System meldet es, ohne eigenen Wecker. */
    private val takt = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = invalidate()
    }

    init {
        contentDescription = "Uhr"
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, o: Outline) = o.setRoundRect(0, 0, v.width, v.height, radius())
        }
        schrift()
        farben()
    }

    private fun schrift() {
        zeit.typeface = Schriften.von(context, stil.schrift, fettFallsSystem = true)
        datum.typeface = Schriften.von(context, if (stil.schrift == Schrift.FETT) Schrift.GRUNDSCHRIFT else stil.schrift)
        // gleich breite Ziffern, wo die Schrift sie kennt: nichts springt beim Minutenwechsel
        zeit.fontFeatureSettings = "tnum"
        uhrzeit = SimpleDateFormat(if (stil.sekunden) "HH:mm:ss" else "HH:mm", Locale.GERMAN)
        removeCallbacks(sekundenTakt)
        if (stil.sekunden && isAttachedToWindow) post(sekundenTakt)
        tag = SimpleDateFormat(
            when (stil.format) {
                UhrStil.OHNE_WOCHENTAG -> "d. MMMM yyyy"
                UhrStil.ZAHL -> "dd.MM.yyyy"
                UhrStil.WOCHENTAG -> "EEEE"
                else -> "EEEE, d. MMMM"
            }, Locale.GERMAN
        )
        gemessenFuer = ""
        invalidate()
    }

    private fun farben() {
        elevation = if (transparent || Thema.clevertouch) 0f else 10f.vp
        zeit.color = if (transparent) 0xFFFFFFFF.toInt() else 0xFF111827.toInt()
        datum.color = if (transparent) 0xF2FFFFFF.toInt() else 0xFF4B5563.toInt()
        if (transparent) {
            zeit.setShadowLayer(10f.vp, 0f, 3f.vp, 0xB3000000.toInt())
            datum.setShadowLayer(8f.vp, 0f, 2f.vp, 0xB3000000.toInt())
        } else {
            zeit.clearShadowLayer()
            datum.clearShadowLayer()
        }
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        context.registerReceiver(takt, IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        })
        if (stil.sekunden) post(sekundenTakt)
        invalidate()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(sekundenTakt)
        try { context.unregisterReceiver(takt) } catch (_: Exception) {}
        super.onDetachedFromWindow()
    }

    private fun radius() = minOf(width, height) * 0.14f

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w < 4 || h < 4) return
        if (!transparent) {
            val dick = maxOf(2f, minOf(w, h) * 0.025f)
            rahmen.strokeWidth = dick
            feld.set(dick / 2, dick / 2, w - dick / 2, h - dick / 2)
            // Clevertouch: getönte Fläche ohne dunklen Rahmen, wie die Kacheln bei Android 15
            flaeche.color = if (Thema.clevertouch) Thema.karte else 0xFFFFFFFF.toInt()
            canvas.drawRoundRect(feld, radius(), radius(), flaeche)
            if (!Thema.clevertouch) canvas.drawRoundRect(feld, radius(), radius(), rahmen)
        }

        val jetzt = Date()
        val z = uhrzeit.format(jetzt)
        val d = tag.format(jetzt)
        // Flach und breit: nur die Uhrzeit
        val mitDatum = stil.datum != UhrStil.AUS && h > w * 0.24f
        val schluessel = "$width×$height|$mitDatum|${gruppen()}|$d"
        if (schluessel != gemessenFuer) {
            gemessenFuer = schluessel
            // „00:00“ als Maß: alle Uhrzeiten gleich groß, auch wenn die Schrift keine gleich breiten Ziffern hat
            zeit.textSize = passendeZeit(w * 0.84f, if (mitDatum) h * 0.48f else h * 0.66f)
            if (mitDatum) datum.textSize = passend(datum, d, w * 0.86f, h * 0.15f)
        }
        zeit.getTextBounds("0123456789", 0, 10, masse)
        val zOben = -masse.top.toFloat() // Höhe der Ziffern über der Grundlinie
        if (!mitDatum) {
            zeitMalen(canvas, z, w / 2f, (h + zOben) / 2f)
            return
        }
        val dm = datum.fontMetrics
        val dHoehe = dm.descent - dm.ascent
        val luecke = h * 0.05f
        val oben = (h - zOben - luecke - dHoehe) / 2f
        if (stil.datum == UhrStil.OBEN) {
            canvas.drawText(d, w / 2f, oben - dm.ascent, datum)
            zeitMalen(canvas, z, w / 2f, oben + dHoehe + luecke + zOben)
        } else {
            zeitMalen(canvas, z, w / 2f, oben + zOben)
            canvas.drawText(d, w / 2f, oben + zOben + luecke - dm.ascent, datum)
        }
    }

    /**
     * Uhrzeit aus Gruppen setzen (Stunden, Minuten, ggf. Sekunden) mit engen, festen Doppelpunkten.
     * Manche Schriften (z. B. die Grundschrift) haben einen sehr breiten Doppelpunkt; so sieht
     * „11:27“ in jeder Schrift gleich aus. Jede Gruppe bekommt die Breite von „00“.
     */
    private fun gruppen() = if (stil.sekunden) 3 else 2

    private fun zeitBreite(): Float {
        val ziffern = zeit.measureText("00")
        zeit.getTextBounds(":", 0, 1, masse)
        val luecke = zeit.textSize * 0.07f
        val n = gruppen()
        return n * ziffern + (n - 1) * (masse.width() + 2 * luecke)
    }

    private fun zeitMalen(canvas: Canvas, z: String, mitte: Float, grundlinie: Float) {
        val ziffern = zeit.measureText("00")
        val luecke = zeit.textSize * 0.07f
        zeit.getTextBounds(":", 0, 1, masse)
        val punkt = masse.width().toFloat()
        var x = mitte - zeitBreite() / 2f
        z.split(':').forEachIndexed { i, teil ->
            if (i > 0) {
                // Doppelpunkt: sichtbarer Teil genau in die Lücke (seine eigene Laufweite zählt nicht)
                zeit.textAlign = Paint.Align.LEFT
                canvas.drawText(":", x + luecke - masse.left, grundlinie, zeit)
                zeit.textAlign = Paint.Align.CENTER
                x += punkt + 2 * luecke
            }
            canvas.drawText(teil, x + ziffern / 2f, grundlinie, zeit)
            x += ziffern
        }
    }

    private fun passendeZeit(b: Float, h: Float): Float {
        zeit.textSize = 100f
        val breite = zeitBreite().coerceAtLeast(1f)
        zeit.getTextBounds("0123456789", 0, 10, masse)
        val hoehe = maxOf(masse.height().toFloat(), 1f)
        return minOf(100f * b / breite, 100f * h / hoehe)
    }

    /** Größte Schrift, mit der [text] in [b]×[h] passt (Höhe: sichtbare Zeichen). */
    private fun passend(p: Paint, text: String, b: Float, h: Float): Float {
        p.textSize = 100f
        val breite = p.measureText(text).coerceAtLeast(1f)
        p.getTextBounds(text, 0, text.length, masse)
        val hoehe = maxOf(masse.height().toFloat(), 1f)
        return minOf(100f * b / breite, 100f * h / hoehe)
    }
}
