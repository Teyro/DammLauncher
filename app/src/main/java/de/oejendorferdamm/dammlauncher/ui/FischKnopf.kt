/*
 * DammLauncher – Copyright (C) 2026 Teyro – GNU GPL v3 oder neuer, siehe LICENSE
 */
package de.oejendorferdamm.dammlauncher.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View

/** Kleiner Clownfisch unten links: ein Tipp startet das Leise-Aquarium. Gezeichnet, damit er auf jedem Board scharf ist. */
@SuppressLint("ViewConstructor")
class FischKnopf(context: Context, onTipp: () -> Unit) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pfad = Path()
    private val oval = RectF()

    init {
        contentDescription = "Leise-Aquarium starten"
        alpha = 0.8f
        setOnClickListener { onTipp() }
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        // runde, halb durchsichtige Fläche, damit der Fisch auf jedem Hintergrund zu sehen ist
        p.color = 0x99000000.toInt(); c.drawCircle(w / 2, h / 2, minOf(w, h) / 2, p)
        val cx = w * 0.46f; val cy = h / 2; val r = minOf(w, h) * 0.24f
        // Schwanzflosse
        p.color = 0xFFFF8A1F.toInt()
        pfad.reset(); pfad.moveTo(cx + r * 0.8f, cy); pfad.lineTo(cx + r * 1.55f, cy - r * 0.7f); pfad.lineTo(cx + r * 1.55f, cy + r * 0.7f); pfad.close()
        c.drawPath(pfad, p)
        // Körper mit zwei weißen Streifen
        oval.set(cx - r * 1.1f, cy - r * 0.72f, cx + r * 1.0f, cy + r * 0.72f); c.drawOval(oval, p)
        c.save(); c.clipPath(Path().apply { addOval(oval, Path.Direction.CW) })
        p.color = 0xFFFFFFFF.toInt()
        c.drawRect(cx - r * 0.45f, cy - r, cx - r * 0.2f, cy + r, p)
        c.drawRect(cx + r * 0.35f, cy - r, cx + r * 0.58f, cy + r, p)
        c.restore()
        // Auge
        p.color = 0xFF1F2937.toInt(); c.drawCircle(cx - r * 0.72f, cy - r * 0.15f, r * 0.13f, p)
    }
}
