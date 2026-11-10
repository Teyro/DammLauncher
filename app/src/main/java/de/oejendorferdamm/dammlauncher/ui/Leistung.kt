/*
 * DammLauncher – Copyright (C) 2026 Teyro – GNU GPL v3 oder neuer, siehe LICENSE
 */
package de.oejendorferdamm.dammlauncher.ui

import android.os.Build
import de.oejendorferdamm.dammlauncher.konfig.Geraet
import de.oejendorferdamm.dammlauncher.konfig.KonfigSpeicher

/**
 * Leistung je Board: Ab Android 10 volle Darstellung, darunter (Android 8/9, oft schwache Grafikchips) sparsam:
 * keine Animationen, keine weichen Schatten und Höhen-Schatten, kein Milchglas, Hintergrundbild in Bildschirmgröße
 * mit halber Farbtiefe. In den Einstellungen (Bedienung → Leistung) lässt es sich von Hand festlegen.
 */
object Leistung {
    const val AUTO = "auto"
    const val VOLL = "voll"
    const val SPAR = "spar"

    /** Was die Automatik für dieses Board wählt */
    val automatischSparsam: Boolean get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    val sparsam: Boolean get() = when (Geraet.leistung) {
        VOLL -> false
        SPAR -> true
        else -> automatischSparsam
    }

    /** Animationen nur, wenn eingeschaltet UND nicht sparsam */
    val animationen: Boolean get() = KonfigSpeicher.konfig.animationen && !sparsam

    /** Weicher Textschatten: sparsam nur ein schmaler Rand (bleibt auf Fotos lesbar, kostet kaum) */
    fun schatten(radius: Float): Float = if (sparsam) minOf(radius, 2f) else radius

    /** Höhen-Schatten (elevation): sparsam keiner */
    fun hoehe(wert: Float): Float = if (sparsam) 0f else wert
}
