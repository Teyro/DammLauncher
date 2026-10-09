/*
 * DammLauncher – Copyright (C) 2026 Teyro – GNU GPL v3 oder neuer, siehe LICENSE
 */
package de.oejendorferdamm.dammlauncher.ui

import android.content.Context

/**
 * Alle Größen der Bedienung sind „Vorbild-Pixel“ auf einem 1920 px breiten Display und werden auf
 * die echte Breite umgerechnet – auf 4K doppelt so groß, damit es auf 75 Zoll gleich gut treffbar ist.
 */
object Masse {
    @Volatile
    var faktor: Float = 1f
        private set

    fun aktualisiere(context: Context) {
        val m = context.resources.displayMetrics
        faktor = maxOf(m.widthPixels, m.heightPixels) / 1920f
    }
}

/** Vorbild-Pixel → echte Pixel. */
val Int.vp: Int get() = (this * Masse.faktor).toInt().coerceAtLeast(if (this > 0) 1 else 0)
val Float.vp: Float get() = this * Masse.faktor
