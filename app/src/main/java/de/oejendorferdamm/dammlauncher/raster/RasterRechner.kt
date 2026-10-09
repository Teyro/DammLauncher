/*
 * DammLauncher – Copyright (C) 2026 Teyro – GNU GPL v3 oder neuer, siehe LICENSE
 */
package de.oejendorferdamm.dammlauncher.raster

import de.oejendorferdamm.dammlauncher.konfig.Konfig

/** Ein Rechteck in Bildschirm-Pixeln. */
data class Feld(val links: Float, val oben: Float, val rechts: Float, val unten: Float) {
    val breite get() = rechts - links
    val hoehe get() = unten - oben
}

/**
 * Teilt die Fläche gleichmäßig in Spalten × Zeilen: erst die Ränder ab, dann die Abstände zwischen
 * den Zellen, der Rest wird gleich verteilt. Alle Werte aus der Konfiguration sind Vorbild-Pixel
 * (1920 breit) und werden mit [faktor] (= Bildschirmbreite / 1920) umgerechnet.
 */
class RasterRechner(
    val breite: Int,
    val hoehe: Int,
    val spalten: Int,
    val zeilen: Int,
    private val links: Float,
    private val oben: Float,
    private val rechts: Float,
    private val unten: Float,
    private val abstand: Float
) {
    /** Breite und Höhe einer Zelle (nie kleiner als 1 px, auch bei absurden Rändern). */
    val zellBreite: Float = ((breite - links - rechts - abstand * (spalten - 1)) / spalten).coerceAtLeast(1f)
    val zellHoehe: Float = ((hoehe - oben - unten - abstand * (zeilen - 1)) / zeilen).coerceAtLeast(1f)

    /** Feld für ein Element ab ([spalte], [zeile]) mit [b] × [h] Zellen (Abstände dazwischen gehören dazu). */
    fun feld(spalte: Int, zeile: Int, b: Int = 1, h: Int = 1): Feld {
        val x = links + spalte * (zellBreite + abstand)
        val y = oben + zeile * (zellHoehe + abstand)
        return Feld(x, y, x + b * zellBreite + (b - 1) * abstand, y + h * zellHoehe + (h - 1) * abstand)
    }

    /** Welche Zelle liegt unter dem Punkt? null = außerhalb des Rasters. Abstände zählen zur nächstliegenden Zelle. */
    fun zelleBei(x: Float, y: Float): Pair<Int, Int>? {
        if (x < links || y < oben || x > breite - rechts || y > hoehe - unten) return null
        val s = ((x - links + abstand / 2f) / (zellBreite + abstand)).toInt().coerceIn(0, spalten - 1)
        val z = ((y - oben + abstand / 2f) / (zellHoehe + abstand)).toInt().coerceIn(0, zeilen - 1)
        return s to z
    }

    companion object {
        const val VORBILD_BREITE = 1920f

        fun aus(konfig: Konfig, breite: Int, hoehe: Int): RasterRechner {
            val f = faktor(breite, hoehe)
            // Ränder dürfen zusammen nie mehr als 90 % der Fläche wegnehmen
            var l = konfig.rand.links * f
            var r = konfig.rand.rechts * f
            var o = konfig.rand.oben * f
            var u = konfig.rand.unten * f
            if (l + r > breite * 0.9f) { val k = breite * 0.9f / (l + r); l *= k; r *= k }
            if (o + u > hoehe * 0.9f) { val k = hoehe * 0.9f / (o + u); o *= k; u *= k }
            return RasterRechner(breite, hoehe, konfig.spalten, konfig.zeilen, l, o, r, u, konfig.abstand * f)
        }

        /** Umrechnung Vorbild-Pixel → echte Pixel; im Hochformat zählt die längere Seite. */
        fun faktor(breite: Int, hoehe: Int) = maxOf(breite, hoehe) / VORBILD_BREITE
    }
}
