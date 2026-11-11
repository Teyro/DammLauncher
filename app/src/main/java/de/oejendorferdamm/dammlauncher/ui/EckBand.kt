/*
 * DammLauncher – Copyright (C) 2026 Teyro – GNU GPL v3 oder neuer, siehe LICENSE
 */
package de.oejendorferdamm.dammlauncher.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.view.View

/**
 * Schräges Band über der Ecke unten rechts („CTOUCH-Modus“): zeigt dezent, dass hier ein älteres Board
 * mit der neuen Oberfläche läuft. Nimmt keine Berührungen an – darunter bleibt alles bedienbar.
 */
@SuppressLint("ViewConstructor")
class EckBand(context: Context, private val text: String = "CTOUCH-Modus") : View(context) {
    private val flaeche = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xB31F2124.toInt() }
    private val schrift = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xF2FFFFFF.toInt()
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        letterSpacing = 0.08f
    }

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onTouchEvent(event: android.view.MotionEvent?) = false

    override fun onDraw(c: Canvas) {
        val dicke = 46f.vp
        val abstand = 150f.vp // Abstand der Bandmitte von der Ecke, entlang beider Kanten
        schrift.textSize = 24f.vp
        c.save()
        // Ecke unten rechts: Band quer über die Ecke, 45° gedreht
        c.translate(width - abstand / 2f, height - abstand / 2f)
        c.rotate(-45f)
        c.drawRect(-abstand * 1.5f, -dicke / 2f, abstand * 1.5f, dicke / 2f, flaeche)
        c.drawText(text, 0f, -(schrift.descent() + schrift.ascent()) / 2f, schrift)
        c.restore()
    }
}
