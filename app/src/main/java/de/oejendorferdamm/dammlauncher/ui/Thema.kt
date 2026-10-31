/*
 * DammLauncher – Copyright (C) 2026 Teyro – GNU GPL v3 oder neuer, siehe LICENSE
 */
package de.oejendorferdamm.dammlauncher.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/**
 * Farben im Clevertouch-Aussehen – wie bei Android 15 („Material You“) aus dem Hintergrund
 * abgeleitet, aber nur EINMAL je Hintergrund berechnet (keine laufenden Effekte). Dazu ein winziges,
 * stark weichgezeichnetes Abbild des Hintergrunds als Milchglas für Dock und Leisten: echte
 * Unschärfe gibt es erst ab Android 12, und sie würde jedes Bild Rechenzeit kosten.
 */
object Thema {
    /** Clevertouch-Aussehen aktiv (setzt die HomeActivity bei jeder Anzeige). */
    @Volatile
    var clevertouch = false

    /** Kräftige Farbe (Knöpfe, Markierungen) – dunkel genug für weiße Schrift. */
    var akzent = Farben.AKZENT
        private set
    /** Helle, leicht getönte Fläche der Kacheln. */
    var karte = 0xF7FFFFFF.toInt()
        private set
    /** Dunkle Tönung über dem Milchglas (Dock, Kopfzeile, App-Übersicht). */
    var glasTon = 0x66101820
        private set
    /** Weichgezeichneter Hintergrund (ca. 48 × 27 Pixel), null = einfarbiger Hintergrund. */
    var glas: Bitmap? = null
        private set
    /** Zähler: ändert sich mit jedem neuen Hintergrund (Glasflächen zeichnen dann neu). */
    var stand = 0
        private set

    class Ergebnis(val akzent: Int, val karte: Int, val glasTon: Int, val glas: Bitmap?)

    /** Im Hintergrund rechnen (Bild), auf dem Haupt-Faden [setze]n. */
    fun ausBild(b: Bitmap): Ergebnis {
        val klein = Bitmap.createScaledBitmap(b, 48, 27, true)
        val farbe = hauptfarbe(klein)
        val glas = weich(klein)
        if (klein !== b && klein !== glas) klein.recycle()
        return ausFarbe(farbe, glas)
    }

    /** Für einen einfarbigen Hintergrund. */
    fun ausFarbe(farbe: Int, glas: Bitmap? = null): Ergebnis {
        val hsv = FloatArray(3)
        Color.colorToHSV(farbe, hsv)
        val h = hsv[0]
        // Graue Hintergründe bekommen ein ruhiges Blau statt eines zufälligen Farbtons
        val bunt = hsv[1] > 0.12f
        val ton = if (bunt) h else 215f
        val akzent = Color.HSVToColor(floatArrayOf(ton, if (bunt) maxOf(0.5f, minOf(hsv[1], 0.8f)) else 0.55f, 0.58f))
        val karte = Color.HSVToColor(0xF7, floatArrayOf(ton, 0.07f, 0.99f))
        val glasTon = Color.HSVToColor(0x73, floatArrayOf(ton, 0.45f, 0.16f))
        return Ergebnis(akzent, karte, glasTon, glas)
    }

    fun setze(e: Ergebnis) {
        akzent = e.akzent
        karte = e.karte
        glasTon = e.glasTon
        glas = e.glas
        stand++
    }

    /** Häufigster kräftiger Farbton (Farbkreis in 12 Abschnitte, gewichtet nach Sättigung und Helligkeit). */
    internal fun hauptfarbe(b: Bitmap): Int {
        val gewicht = FloatArray(12)
        val summe = Array(12) { FloatArray(3) }
        val hsv = FloatArray(3)
        var grau = 0
        for (y in 0 until b.height) for (x in 0 until b.width) {
            val p = b.getPixel(x, y)
            Color.colorToHSV(p, hsv)
            if (hsv[2] < 0.12f || hsv[1] < 0.12f) { grau++; continue }
            val i = ((hsv[0] / 30f).toInt()).coerceIn(0, 11)
            val g = hsv[1] * hsv[2]
            gewicht[i] += g
            summe[i][0] += Color.red(p) * g; summe[i][1] += Color.green(p) * g; summe[i][2] += Color.blue(p) * g
        }
        val i = gewicht.indices.maxByOrNull { gewicht[it] } ?: return Color.GRAY
        val g = gewicht[i]
        if (g <= 0f || grau > b.width * b.height * 0.85f) return Color.GRAY
        return Color.rgb((summe[i][0] / g).toInt(), (summe[i][1] / g).toInt(), (summe[i][2] / g).toInt())
    }

    /** Dreimal Kastenunschärfe auf dem winzigen Bild – kostet einmal ein paar Millisekunden. */
    internal fun weich(b: Bitmap): Bitmap {
        val w = b.width
        val h = b.height
        var px = IntArray(w * h)
        b.getPixels(px, 0, w, 0, 0, w, h)
        repeat(3) { px = kasten(kasten(px, w, h, true), w, h, false) }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun kasten(ein: IntArray, w: Int, h: Int, waagerecht: Boolean): IntArray {
        val aus = IntArray(ein.size)
        val r = 2
        val n = if (waagerecht) w else h
        val m = if (waagerecht) h else w
        for (j in 0 until m) for (i in 0 until n) {
            var rr = 0; var gg = 0; var bb = 0; var k = 0
            for (d in -r..r) {
                val t = (i + d).coerceIn(0, n - 1)
                val p = if (waagerecht) ein[j * w + t] else ein[t * w + j]
                rr += Color.red(p); gg += Color.green(p); bb += Color.blue(p); k++
            }
            aus[if (waagerecht) j * w + i else i * w + j] = Color.rgb(rr / k, gg / k, bb / k)
        }
        return aus
    }
}

/**
 * Milchglas-Fläche: zeichnet den passenden Ausschnitt des weichgezeichneten Hintergrunds,
 * abgerundet und dunkel getönt. Ohne Bild bleibt nur die Tönung.
 */
class GlasFlaeche(context: Context, private val radius: () -> Float) : View(context) {
    private val glasPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val ton = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rand = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0x33FFFFFF }
    private val feld = RectF()
    private val ort = IntArray(2)
    private val matrix = android.graphics.Matrix()
    private var shaderFuer: Bitmap? = null

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w < 2 || h < 2) return
        val r = radius()
        feld.set(0f, 0f, w, h)
        val g = Thema.glas
        val wurzel = rootView
        if (g != null && wurzel.width > 0 && wurzel.height > 0) {
            // Das winzige Bild über einen Shader auf Fenstergröße strecken: glatte Ecken (Kantenglättung), kein Zuschneiden
            if (shaderFuer !== g) {
                glasPaint.shader = android.graphics.BitmapShader(g, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP)
                shaderFuer = g
            }
            getLocationInWindow(ort)
            matrix.setScale(wurzel.width.toFloat() / g.width, wurzel.height.toFloat() / g.height)
            matrix.postTranslate(-ort[0].toFloat(), -ort[1].toFloat())
            glasPaint.shader.setLocalMatrix(matrix)
            canvas.drawRoundRect(feld, r, r, glasPaint)
        }
        ton.color = Thema.glasTon
        canvas.drawRoundRect(feld, r, r, ton)
        val d = maxOf(1f, 1.5f.vp)
        rand.strokeWidth = d
        feld.inset(d / 2, d / 2)
        canvas.drawRoundRect(feld, r, r, rand)
    }
}
