/*
 * DammLauncher – Copyright (C) 2026 Teyro – GNU GPL v3 oder neuer, siehe LICENSE
 */
package de.oejendorferdamm.dammlauncher.ui

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextPaint
import android.text.TextUtils
import android.view.View
import de.oejendorferdamm.dammlauncher.apps.AppKatalog

/**
 * Eine App als EINE Ansicht: Symbol oben, Name darunter – selbst gezeichnet, ohne ImageView und
 * TextView. Das hält den Startbildschirm schnell, auch bei 100 Zellen.
 */
class AppZelle(context: Context) : View(context) {
    var komponente: ComponentName? = null
        private set
    private var eigenesIcon: String? = null
    var name: String = ""
        private set
    private var bild: Bitmap? = null
    private var iconProzent = 60
    private var mitText = true
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        setShadowLayer(4f, 0f, 1.5f, 0xCC000000.toInt())
    }
    private val bildPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val ziel = RectF()
    private var geladenFuer = ""
    /** Textfarbe für dunkle Schrift auf hellem Grund (App-Übersicht). */
    var dunkel = false
        set(v) {
            field = v
            textPaint.color = if (v) 0xFF1F2328.toInt() else Color.WHITE
            if (v) textPaint.clearShadowLayer() else textPaint.setShadowLayer(4f, 0f, 1.5f, 0xCC000000.toInt())
            invalidate()
        }

    init {
        isClickable = true
        isLongClickable = true
        isHapticFeedbackEnabled = false
    }

    fun setze(app: ComponentName, name: String, eigenesIcon: String?, iconProzent: Int, textGroesse: Float, mitText: Boolean) {
        val neu = komponente != app || this.eigenesIcon != eigenesIcon
        komponente = app
        this.eigenesIcon = eigenesIcon
        this.name = name
        this.iconProzent = iconProzent
        this.mitText = mitText
        textPaint.textSize = textGroesse
        contentDescription = name
        if (neu) {
            bild = null
            geladenFuer = ""
        }
        requestLayout()
        invalidate()
    }

    private fun iconGroesse(): Int {
        val textH = if (mitText) textPaint.textSize * 1.5f else 0f
        val platz = minOf(width.toFloat(), height - textH)
        return (platz * iconProzent / 100f).toInt().coerceAtLeast(8)
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        geladenFuer = ""
        lade()
    }

    private fun lade() {
        val app = komponente ?: return
        if (width == 0) return
        val g = iconGroesse()
        val schluessel = "$app|$eigenesIcon|$g"
        if (geladenFuer == schluessel) return
        geladenFuer = schluessel
        val sofort = AppKatalog.icon(app, eigenesIcon, g) { b ->
            if (geladenFuer == schluessel) {
                bild = b
                invalidate()
            }
        }
        if (sofort != null) bild = sofort
    }

    override fun onDraw(canvas: Canvas) {
        lade()
        val g = iconGroesse().toFloat()
        val textH = if (mitText) textPaint.textSize * 1.5f else 0f
        val gesamt = g + textH
        val oben = (height - gesamt) / 2f
        ziel.set((width - g) / 2f, oben, (width + g) / 2f, oben + g)
        bild?.let { canvas.drawBitmap(it, null, ziel, bildPaint) }
        if (mitText && name.isNotEmpty()) {
            val t = TextUtils.ellipsize(name, textPaint, width * 0.96f, TextUtils.TruncateAt.END).toString()
            val x = (width - textPaint.measureText(t)) / 2f
            canvas.drawText(t, x, oben + g + textPaint.textSize * 1.15f, textPaint)
        }
    }
}
