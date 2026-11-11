/*
 * DammLauncher – Copyright (C) 2026 Teyro – GNU GPL v3 oder neuer, siehe LICENSE
 */
package de.oejendorferdamm.dammlauncher.nutzung

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Zeichnungen der Statistik – dieselben für den Bildschirm und für den Export (als Bild).
 * [s] = Pixel je Maßeinheit (Bildschirm: Vorbild-Pixel, Export: feste Größe).
 */
object NutzungsGrafik {
    /** Feste Reihenfolge der Kategorie-Farben (geprüfte Standard-Palette), danach „Andere“ */
    val FARBEN = intArrayOf(
        0xFF2A78D6.toInt(), 0xFFEB6834.toInt(), 0xFF1BAF7A.toInt(), 0xFFEDA100.toInt(),
        0xFFE87BA4.toInt(), 0xFF008300.toInt(), 0xFF6250D6.toInt()
    )
    const val ANDERE = 0xFF9AA1AB.toInt()
    /** Bildschirm an, aber keine App vorn (Startbildschirm) */
    const val NUR_AN = 0xFFD5DAE0.toInt()
    const val SPUR = 0xFFF1F3F5.toInt()
    const val TEXT = 0xFF1F2124.toInt()
    const val TEXT2 = 0xFF6B7078.toInt()
    const val LINIE = 0xFFE1E4E8.toInt()

    fun farbe(plaetze: Map<String, Int>, paket: String): Int = plaetze[paket]?.let { FARBEN[it] } ?: ANDERE

    private fun stift(farbe: Int, groesse: Float, fett: Boolean = false, rechts: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = farbe; textSize = groesse
        typeface = Typeface.create("sans-serif", if (fett) Typeface.BOLD else Typeface.NORMAL)
        if (rechts) textAlign = Paint.Align.RIGHT
    }
    private fun flaeche(farbe: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = farbe }

    // ------------------------------------------------------------ Balken: Zeit je App

    data class Zeile(val name: String, val ms: Long, val farbe: Int, val starts: Int)

    fun balkenHoehe(zeilen: Int, s: Float) = ((zeilen.coerceAtLeast(1)) * 64f * s + 8f * s).toInt()

    fun balken(c: Canvas, breite: Int, zeilen: List<Zeile>, s: Float) {
        val name = stift(TEXT, 24f * s)
        val wert = stift(TEXT2, 22f * s, rechts = true)
        if (zeilen.isEmpty()) { c.drawText("Noch keine Apps aufgezeichnet.", 0f, 40f * s, stift(TEXT2, 24f * s)); return }
        val links = 290f * s
        val rechts = breite - 200f * s
        val max = zeilen.maxOf { it.ms }.coerceAtLeast(1L)
        zeilen.forEachIndexed { i, z ->
            val y = i * 64f * s + 8f * s
            val mitte = y + 24f * s
            c.drawCircle(12f * s, mitte, 9f * s, flaeche(z.farbe))
            c.drawText(kuerze(z.name, name, links - 46f * s), 32f * s, mitte + 8f * s, name)
            c.drawRoundRect(RectF(links, mitte - 14f * s, rechts, mitte + 14f * s), 4f * s, 4f * s, flaeche(SPUR))
            val ende = links + (rechts - links) * (z.ms.toFloat() / max)
            c.drawRoundRect(RectF(links, mitte - 14f * s, maxOf(ende, links + 8f * s), mitte + 14f * s), 4f * s, 4f * s, flaeche(z.farbe))
            c.drawText(Auswertung.dauer(z.ms) + "  ·  ${z.starts}×", breite.toFloat(), mitte + 8f * s, wert)
        }
    }

    private fun kuerze(t: String, p: Paint, max: Float): String {
        if (p.measureText(t) <= max) return t
        var n = t.length
        while (n > 1 && p.measureText(t.take(n) + "…") > max) n--
        return t.take(n) + "…"
    }

    // ------------------------------------------------------------ Zeitleiste: ein Streifen je Tag

    /** Sichtbare Stunden: mindestens 7–17 Uhr, sonst so weit, wie das Board an war */
    fun stunden(tage: List<Tag>): IntRange {
        var von = 7; var bis = 17
        for (t in tage) {
            t.erstesAn?.let { von = minOf(von, ((it - t.beginn) / 3_600_000L).toInt()) }
            t.letztesAus?.let { bis = maxOf(bis, (((it - t.beginn) + 3_599_999L) / 3_600_000L).toInt()) }
        }
        von = (von / 2) * 2
        bis = minOf(24, ((bis + 1) / 2) * 2)
        return von..bis
    }

    fun zeitleisteHoehe(tage: Int, s: Float) = ((tage * 52f + 50f) * s).toInt()

    fun zeitleiste(c: Canvas, breite: Int, tage: List<Tag>, plaetze: Map<String, Int>, s: Float) {
        val tagStift = stift(TEXT, 22f * s)
        val summe = stift(TEXT2, 20f * s, rechts = true)
        val achse = stift(TEXT2, 18f * s).apply { textAlign = Paint.Align.CENTER }
        val links = 170f * s
        val rechts = breite - 120f * s
        val h = stunden(tage)
        val spanne = (h.last - h.first) * 3_600_000f
        val linie = flaeche(LINIE).apply { strokeWidth = 1f * s }
        val format = SimpleDateFormat("EE dd.MM.", Locale.GERMAN)
        fun x(t: Tag, ms: Long) = links + (rechts - links) * ((ms - t.beginn - h.first * 3_600_000L) / spanne).coerceIn(0f, 1f)
        // Stundenlinien und Beschriftung
        val unten = tage.size * 52f * s
        for (st in h.first..h.last step 2) {
            val xx = links + (rechts - links) * ((st - h.first) * 3_600_000f / spanne)
            c.drawLine(xx, 0f, xx, unten, linie)
            c.drawText("$st:00", xx, unten + 30f * s, achse)
        }
        tage.forEachIndexed { i, t ->
            val y = i * 52f * s + 8f * s
            val oben = y; val bis = y + 32f * s
            c.drawText(format.format(Date(t.beginn)), 0f, y + 24f * s, tagStift)
            c.drawRoundRect(RectF(links, oben, rechts, bis), 4f * s, 4f * s, flaeche(SPUR))
            t.bildschirm.forEach { b -> c.drawRoundRect(RectF(x(t, b.start), oben, maxOf(x(t, b.ende), x(t, b.start) + 2f * s), bis), 4f * s, 4f * s, flaeche(NUR_AN)) }
            // Apps etwas schmaler, mit 2 px Abstand zueinander
            t.apps.forEach { a ->
                val x1 = x(t, a.start) + 1f * s
                val x2 = maxOf(x(t, a.ende) - 1f * s, x1 + 2f * s)
                c.drawRoundRect(RectF(x1, oben + 4f * s, x2, bis - 4f * s), 3f * s, 3f * s, flaeche(farbe(plaetze, a.paket)))
            }
            c.drawText(if (t.leer) "–" else Auswertung.dauer(t.anMs), breite.toFloat(), y + 24f * s, summe)
        }
    }

    /** Als Bild (für den Export) */
    fun alsBild(breite: Int, hoehe: Int, malen: (Canvas) -> Unit): Bitmap =
        Bitmap.createBitmap(breite, hoehe.coerceAtLeast(1), Bitmap.Config.ARGB_8888).also { b ->
            Canvas(b).apply { drawColor(android.graphics.Color.WHITE); malen(this) }
        }
}

/** Eine Zeichnung als Ansicht: Höhe ergibt sich aus der Breite. */
class GrafikAnsicht(context: Context, private val hoehe: (Int) -> Int, private val malen: (Canvas, Int) -> Unit) : View(context) {
    override fun onMeasure(w: Int, h: Int) {
        val b = MeasureSpec.getSize(w)
        setMeasuredDimension(b, hoehe(b))
    }
    override fun onDraw(c: Canvas) = malen(c, width)
}
