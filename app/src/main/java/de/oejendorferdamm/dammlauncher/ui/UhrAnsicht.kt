/*
 * DammLauncher – Copyright (C) 2026 Teyro – GNU GPL v3 oder neuer, siehe LICENSE
 */
package de.oejendorferdamm.dammlauncher.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Die eingebaute Digitaluhr: weiße Fläche mit dunklem Rahmen, große Uhrzeit, darunter das Datum.
 * Alles wird aus der Größe des Feldes berechnet – die Uhr wächst mit, wenn man sie größer zieht.
 */
class UhrAnsicht(context: Context) : View(context) {
    private val flaeche = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    private val rahmen = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1F2937.toInt(); style = Paint.Style.STROKE }
    private val zeit = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF111827.toInt()
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        fontFeatureSettings = "tnum" // gleich breite Ziffern: nichts springt beim Minutenwechsel
    }
    private val datum = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF4B5563.toInt()
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }
    private val feld = RectF()
    private val uhrzeit = SimpleDateFormat("HH:mm", Locale.GERMAN)
    private val tag = SimpleDateFormat("EEEE, d. MMMM", Locale.GERMAN)

    /** Minutenwechsel, Uhr gestellt, Zeitzone – das System meldet es, ohne eigenen Wecker. */
    private val takt = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = invalidate()
    }

    init {
        contentDescription = "Uhr"
        elevation = 10f.vp
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        context.registerReceiver(takt, IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        })
        invalidate()
    }

    override fun onDetachedFromWindow() {
        try { context.unregisterReceiver(takt) } catch (_: Exception) {}
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(v: View, o: android.graphics.Outline) = o.setRoundRect(0, 0, v.width, v.height, radius())
        }
    }

    private fun radius() = minOf(width, height) * 0.14f

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w < 4 || h < 4) return
        val jetzt = Date()
        val dick = maxOf(2f, minOf(w, h) * 0.025f)
        rahmen.strokeWidth = dick
        feld.set(dick / 2, dick / 2, w - dick / 2, h - dick / 2)
        canvas.drawRoundRect(feld, radius(), radius(), flaeche)
        canvas.drawRoundRect(feld, radius(), radius(), rahmen)

        val z = uhrzeit.format(jetzt)
        val d = tag.format(jetzt)
        // Flach und breit: nur die Uhrzeit; sonst Uhrzeit groß, Datum darunter
        val mitDatum = h > w * 0.28f
        val innenB = w * 0.84f
        val zeitH = if (mitDatum) h * 0.5f else h * 0.66f
        zeit.textSize = passend(zeit, z, innenB, zeitH)
        val zm = zeit.fontMetrics
        if (mitDatum) {
            datum.textSize = passend(datum, d, innenB, h * 0.14f)
            val dm = datum.fontMetrics
            val luecke = h * 0.04f
            val zHoehe = -zm.ascent * 0.72f // Ziffern haben keine Unterlänge
            val dHoehe = dm.descent - dm.ascent
            val oben = (h - zHoehe - luecke - dHoehe) / 2f
            canvas.drawText(z, w / 2f, oben + zHoehe, zeit)
            canvas.drawText(d, w / 2f, oben + zHoehe + luecke - dm.ascent, datum)
        } else {
            canvas.drawText(z, w / 2f, h / 2f - zm.ascent * 0.36f, zeit)
        }
    }

    /** Größte Schrift, mit der [text] in [b]×[h] passt. */
    private fun passend(p: Paint, text: String, b: Float, h: Float): Float {
        p.textSize = 100f
        val breite = p.measureText(text).coerceAtLeast(1f)
        val hoehe = (p.fontMetrics.descent - p.fontMetrics.ascent).coerceAtLeast(1f)
        return minOf(100f * b / breite, 100f * h / hoehe * 1.25f)
    }
}
