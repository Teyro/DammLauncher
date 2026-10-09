/*
 * DammLauncher – Copyright (C) 2026 Teyro – GNU GPL v3 oder neuer, siehe LICENSE
 */
package de.oejendorferdamm.dammlauncher.raster

import de.oejendorferdamm.dammlauncher.konfig.Element

/** Welche Zellen belegt sind, und wohin etwas passt. */
object Belegung {
    fun passt(e: Element, spalten: Int, zeilen: Int) =
        e.spalte >= 0 && e.zeile >= 0 && e.breite >= 1 && e.hoehe >= 1 && e.spalte + e.breite <= spalten && e.zeile + e.hoehe <= zeilen

    private fun ueberlappt(a: Element, s: Int, z: Int, b: Int, h: Int) =
        a.spalte < s + b && s < a.spalte + a.breite && a.zeile < z + h && z < a.zeile + a.hoehe

    /** Ist der Bereich frei (ohne [ausser], z. B. das gerade gezogene Element)? */
    fun frei(elemente: List<Element>, s: Int, z: Int, b: Int, h: Int, spalten: Int, zeilen: Int, ausser: String? = null): Boolean {
        if (s < 0 || z < 0 || s + b > spalten || z + h > zeilen) return false
        return elemente.none { it.schluessel != ausser && ueberlappt(it, s, z, b, h) }
    }

    /** Erste freie Stelle (zeilenweise von oben links) für b × h Zellen, sonst null. */
    fun ersteFreie(elemente: List<Element>, b: Int, h: Int, spalten: Int, zeilen: Int): Pair<Int, Int>? {
        for (z in 0..zeilen - h) for (s in 0..spalten - b) if (frei(elemente, s, z, b, h, spalten, zeilen)) return s to z
        return null
    }

    /**
     * Raster hat sich geändert: Was weiter passt, bleibt an seinem Platz. Was herausfällt (oder sich
     * überlappt), kommt in die nächste freie Lücke – Widgets notfalls verkleinert. Was gar nicht
     * passt, landet in „nicht platziert“. Bisher nicht Platziertes wird wieder eingesetzt, wenn Platz
     * frei wird. Es geht also nie etwas verloren.
     */
    fun anpassen(elemente: List<Element>, nichtPlatziert: List<Element>, spalten: Int, zeilen: Int): Pair<List<Element>, List<Element>> {
        val bleibt = ArrayList<Element>()
        val wandert = ArrayList<Element>()
        val gesehen = HashSet<String>()
        for (e in elemente) {
            if (!gesehen.add(e.schluessel)) continue // doppelt: nur einmal
            if (passt(e, spalten, zeilen) && frei(bleibt, e.spalte, e.zeile, e.breite, e.hoehe, spalten, zeilen)) bleibt.add(e) else wandert.add(e)
        }
        val uebrig = ArrayList<Element>()
        for (e in wandert + nichtPlatziert.filter { gesehen.add(it.schluessel) }) {
            var b = minOf(e.breite.coerceAtLeast(1), spalten)
            var h = minOf(e.hoehe.coerceAtLeast(1), zeilen)
            var platz = ersteFreie(bleibt, b, h, spalten, zeilen)
            // Widget kleiner machen, bis es irgendwo hinpasst
            while (platz == null && (b > 1 || h > 1)) {
                if (b >= h && b > 1) b-- else h--
                platz = ersteFreie(bleibt, b, h, spalten, zeilen)
            }
            if (platz != null) bleibt.add(e.mitGroesse(b, h).verschoben(platz.first, platz.second)) else uebrig.add(e)
        }
        return bleibt to uebrig
    }
}
