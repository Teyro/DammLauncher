/*
 * DammLauncher – Copyright (C) 2026 Teyro – GNU GPL v3 oder neuer, siehe LICENSE
 */
package de.oejendorferdamm.dammlauncher.ui

import android.content.Context
import android.graphics.Typeface
import de.oejendorferdamm.dammlauncher.R
import de.oejendorferdamm.dammlauncher.konfig.Schrift

/**
 * Die eingebauten Schriften – Grundschrift von Christian Urff (www.urff.info), Lizenz CC BY 3.0.
 * Jede Datei wird nur einmal geladen.
 */
object Schriften {
    private val geladen = HashMap<String, Typeface>()

    /** Anzeigename für die Auswahl. */
    fun name(s: String) = when (s) {
        Schrift.GRUNDSCHRIFT -> "Grundschrift"
        Schrift.FETT -> "Grundschrift fett"
        Schrift.LEICHT -> "Grundschrift leicht"
        Schrift.PUNKT -> "Grundschrift Punkt"
        Schrift.KONTUR -> "Grundschrift Kontur"
        else -> "Systemschrift"
    }

    fun von(c: Context, s: String, fettFallsSystem: Boolean = false): Typeface {
        val id = when (s) {
            Schrift.GRUNDSCHRIFT -> R.font.grundschrift_regular
            Schrift.FETT -> R.font.grundschrift_bold
            Schrift.LEICHT -> R.font.grundschrift_light
            Schrift.PUNKT -> R.font.grundschrift_punkt
            Schrift.KONTUR -> R.font.grundschrift_kontur
            else -> return Typeface.create("sans-serif", if (fettFallsSystem) Typeface.BOLD else Typeface.NORMAL)
        }
        return synchronized(geladen) {
            geladen.getOrPut(s) {
                try { c.resources.getFont(id) } catch (_: Exception) { Typeface.DEFAULT }
            }
        }
    }
}
