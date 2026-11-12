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
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Konfetti-Explosion an der Stelle des Fingers und aufsteigende Luftballons – über allem, nimmt aber keine
 * Berührung an (darunter bleibt alles bedienbar). Zeichnet nur, solange etwas zu sehen ist.
 */
@SuppressLint("ViewConstructor")
class PartyAnsicht(context: Context) : View(context) {
    private class Konfetti(var x: Float, var y: Float, var vx: Float, var vy: Float, var winkel: Float, val dreh: Float, val farbe: Int, val breite: Float, val rund: Boolean)
    private class Ballon(var x: Float, var y: Float, val vy: Float, val phase: Float, val farbe: Int, val r: Float)

    private val konfetti = ArrayList<Konfetti>()
    private val ballons = ArrayList<Ballon>()
    private val stift = Paint(Paint.ANTI_ALIAS_FLAG)
    private val schnur = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xAAFFFFFF.toInt() }
    // je Bild wiederverwendet statt neu angelegt – auf alten Boards sonst Ruckeln durch die Speicherbereinigung
    private val oval = RectF()
    private val pfad = Path()
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; color = 0xFFFFFFFF.toInt() }
    private var zuletzt = 0L
    private var party: Pair<Long, Pair<Float, Float>>? = null
    private val FARBEN = intArrayOf(0xFFE01E3C.toInt(), 0xFFF59E0B.toInt(), 0xFF22C55E.toInt(), 0xFF3B82F6.toInt(), 0xFFA855F7.toInt(), 0xFFEC4899.toInt(), 0xFFFACC15.toInt(), 0xFF14B8A6.toInt())

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onTouchEvent(event: MotionEvent?) = false

    /** Explosion bei (x, y) in Fenster-Koordinaten */
    fun knall(x: Float, y: Float) {
        val spar = Leistung.sparsam
        val anzahl = if (spar) 50 else 140
        val s = 1f.vp
        repeat(anzahl) {
            val w = Random.nextFloat() * 6.283f
            val v = (6f + Random.nextFloat() * 16f) * s
            konfetti.add(Konfetti(x, y, cos(w) * v, sin(w) * v - 8f * s, Random.nextFloat() * 360f, (Random.nextFloat() - 0.5f) * 30f,
                FARBEN[Random.nextInt(FARBEN.size)], (8f + Random.nextFloat() * 10f) * s, Random.nextInt(4) == 0))
        }
        repeat(if (spar) 3 else 6) {
            ballons.add(Ballon(Random.nextFloat() * width, height + Random.nextFloat() * 200f * s, (3f + Random.nextFloat() * 3f) * s,
                Random.nextFloat() * 6.283f, FARBEN[Random.nextInt(FARBEN.size)], (46f + Random.nextFloat() * 26f) * s))
        }
        party = SystemClock.uptimeMillis() to (x to y)
        zuletzt = 0L
        postInvalidateOnAnimation()
    }

    override fun onDraw(c: Canvas) {
        val jetzt = SystemClock.uptimeMillis()
        val dt = if (zuletzt == 0L) 1f else ((jetzt - zuletzt) / 16.7f).coerceIn(0.2f, 3f)
        zuletzt = jetzt
        val s = 1f.vp
        // Konfetti: fällt mit Luftwiderstand, dreht sich
        val k = konfetti.iterator()
        while (k.hasNext()) {
            val t = k.next()
            t.vx *= 0.985f; t.vy = t.vy * 0.985f + 0.45f * s * dt
            t.x += t.vx * dt; t.y += t.vy * dt; t.winkel += t.dreh * dt
            if (t.y > height + 40 * s) { k.remove(); continue }
            stift.color = t.farbe
            c.save(); c.rotate(t.winkel, t.x, t.y)
            if (t.rund) c.drawCircle(t.x, t.y, t.breite / 2.5f, stift)
            else c.drawRect(t.x - t.breite / 2, t.y - t.breite / 5, t.x + t.breite / 2, t.y + t.breite / 5, stift)
            c.restore()
        }
        // Luftballons: steigen und schaukeln, Schnur darunter
        schnur.strokeWidth = 2f * s
        val b = ballons.iterator()
        while (b.hasNext()) {
            val l = b.next()
            l.y -= l.vy * dt
            val x = l.x + sin(jetzt / 600f + l.phase) * 18f * s
            if (l.y < -l.r * 4) { b.remove(); continue }
            stift.color = l.farbe
            oval.set(x - l.r * 0.82f, l.y - l.r, x + l.r * 0.82f, l.y + l.r); c.drawOval(oval, stift)
            stift.color = 0x55FFFFFF
            oval.set(x - l.r * 0.45f, l.y - l.r * 0.7f, x - l.r * 0.1f, l.y - l.r * 0.2f); c.drawOval(oval, stift)
            stift.color = l.farbe
            pfad.reset(); pfad.moveTo(x, l.y + l.r * 0.95f); pfad.lineTo(x - l.r * 0.12f, l.y + l.r * 1.15f); pfad.lineTo(x + l.r * 0.12f, l.y + l.r * 1.15f); pfad.close()
            c.drawPath(pfad, stift)
            pfad.reset(); pfad.moveTo(x, l.y + l.r * 1.15f); pfad.quadTo(x + 14 * s, l.y + l.r * 1.8f, x, l.y + l.r * 2.6f)
            c.drawPath(pfad, schnur)
        }
        // „Party!“ kurz an der Stelle des Fingers
        party?.let { (seit, ort) ->
            val alter = jetzt - seit
            if (alter > 1400) { party = null } else {
                text.textSize = 64f * s
                text.alpha = (255 * (1f - alter / 1400f)).toInt().coerceIn(0, 255)
                text.setShadowLayer(Leistung.schatten(8f * s), 0f, 2f * s, 0x99000000.toInt())
                c.drawText("🎉 Party! 🎈", ort.first, ort.second - 60 * s - alter / 12f * s, text)
            }
        }
        if (konfetti.isNotEmpty() || ballons.isNotEmpty() || party != null) postInvalidateOnAnimation() else zuletzt = 0L
    }

    fun leeren() { konfetti.clear(); ballons.clear(); party = null; invalidate() }
}
