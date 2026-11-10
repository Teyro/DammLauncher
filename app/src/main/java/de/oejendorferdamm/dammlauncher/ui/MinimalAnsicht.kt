/*
 * DammLauncher – Copyright (C) 2026 Teyro – GNU GPL v3 oder neuer, siehe LICENSE
 */
package de.oejendorferdamm.dammlauncher.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import de.oejendorferdamm.dammlauncher.konfig.UhrStil

/**
 * Minimalmodus: über dem Hintergrundbild nur eine große Uhr und drei große Knöpfe –
 * Tafel (DammBoard), Timer (DammZeit) und Quelle umschalten. Langer Druck auf die freie
 * Fläche öffnet wie sonst die Einstellungen (mit PIN, falls gesetzt).
 */
@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class MinimalAnsicht(
    context: Context,
    private val tafel: () -> Unit,
    private val timer: () -> Unit,
    private val quelle: () -> Unit,
    private val langerDruck: () -> Unit
) : FrameLayout(context) {
    private val uhr = UhrAnsicht(context, transparent = true, stil = UhrStil())
    private val reihe = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
    /** Hinweis-Band unten: hellgrau, schwarze Schrift – auf jedem Hintergrundbild lesbar. */
    private val band = TextView(context).apply {
        setTextColor(Color.BLACK)
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 30f.vp)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        gravity = Gravity.CENTER
        setPadding(40.vp, 18.vp, 40.vp, 18.vp)
        background = runde(0xF0E5E7EB.toInt(), 24f.vp)
        visibility = View.GONE
    }

    init {
        val spalte = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val m = resources.displayMetrics
        val breite = maxOf(m.widthPixels, m.heightPixels)
        spalte.addView(uhr, LinearLayout.LayoutParams((breite * 0.5f).toInt(), (minOf(m.widthPixels, m.heightPixels) * 0.32f).toInt()))
        spalte.addView(reihe, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { topMargin = 40.vp })
        addView(spalte, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        addView(band, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = 48.vp })
        val lang = Runnable { langerDruck() }
        setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> postDelayed(lang, 900)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> removeCallbacks(lang)
            }
            true
        }
    }

    /** Knöpfe neu setzen (Symbole der Apps, sofern installiert). */
    fun setze(tafelSymbol: Drawable?, timerSymbol: Drawable?, quelleName: String, mitQuelle: Boolean = true, hinweis: String? = null) {
        reihe.removeAllViews()
        reihe.addView(knopf(tafelSymbol, "✏", "Tafel", tafel))
        reihe.addView(knopf(timerSymbol, "⏱", "Timer", timer))
        if (mitQuelle) reihe.addView(knopf(null, "⇄", quelleName, quelle))
        band.text = hinweis ?: ""
        band.visibility = if (hinweis.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    private fun knopf(symbol: Drawable?, ersatz: String, name: String, klick: () -> Unit): View {
        val g = 150.vp
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(30.vp, 26.vp, 30.vp, 22.vp)
            background = runde(0x59000000, 36f.vp, 2.vp, 0x55FFFFFF)
            isClickable = true
            contentDescription = name
            setOnClickListener { klick() }
            if (symbol != null) addView(ImageView(context).apply { setImageDrawable(symbol); scaleType = ImageView.ScaleType.FIT_CENTER }, LinearLayout.LayoutParams(g, g))
            else addView(TextView(context).apply {
                text = ersatz
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 96f.vp)
                background = runde(0x33FFFFFF, 34f.vp)
            }, LinearLayout.LayoutParams(g, g))
            addView(TextView(context).apply {
                text = name
                setTextColor(Color.WHITE)
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 34f.vp)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                gravity = Gravity.CENTER
                setShadowLayer(Leistung.schatten(8f), 0f, 2f, 0x99000000.toInt())
                maxLines = 1
            }, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { topMargin = 14.vp })
        }.also { v -> v.layoutParams = LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { leftMargin = 24.vp; rightMargin = 24.vp } }
    }
}
