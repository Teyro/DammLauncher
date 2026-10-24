/*
 * DammLauncher – Copyright (C) 2026 Teyro – GNU GPL v3 oder neuer, siehe LICENSE
 */
package de.oejendorferdamm.dammlauncher.ui

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.text.TextPaint
import android.text.TextUtils
import android.view.View
import android.view.ViewOutlineProvider
import de.oejendorferdamm.dammlauncher.apps.AppKatalog
import de.oejendorferdamm.dammlauncher.konfig.Anzeige
import de.oejendorferdamm.dammlauncher.konfig.Ordner
import de.oejendorferdamm.dammlauncher.konfig.Schrift
import de.oejendorferdamm.dammlauncher.qr.QrCode

/** Weiße Fläche mit dunklem Rahmen wie bei der Uhr – gemeinsam für alle eingebauten Kacheln. */
abstract class KartenAnsicht(context: Context) : View(context) {
    private val flaeche = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val rahmen = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1F2937.toInt(); style = Paint.Style.STROKE }
    private val feld = RectF()

    init {
        elevation = 10f.vp
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, o: Outline) = o.setRoundRect(0, 0, v.width, v.height, radius())
        }
    }

    protected fun radius() = minOf(width, height) * 0.14f

    protected fun karte(canvas: Canvas) {
        val dick = maxOf(2f, minOf(width, height) * 0.025f)
        rahmen.strokeWidth = dick
        feld.set(dick / 2, dick / 2, width - dick / 2, height - dick / 2)
        canvas.drawRoundRect(feld, radius(), radius(), flaeche)
        canvas.drawRoundRect(feld, radius(), radius(), rahmen)
    }
}

/**
 * Kachel mit drei Zeilen (klein – GROSS – klein), z. B. „Bis zur Pause / 12 min / um 09:35“ oder
 * „Klasse 5a / 2 / fehlen heute“. [quelle] wird bei jedem Zeichnen gefragt; zum Minutenwechsel
 * zeichnet die Kachel von selbst neu (Systemtakt, kein eigener Wecker).
 */
class InfoKachel(context: Context, var quelle: () -> Anzeige) : KartenAnsicht(context) {
    private val oben = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF4B5563.toInt(); textAlign = Paint.Align.CENTER }
    private val gross = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF111827.toInt(); textAlign = Paint.Align.CENTER }
    private val unten = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF4B5563.toInt(); textAlign = Paint.Align.CENTER }
    private val masse = Rect()

    private val takt = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = invalidate()
    }

    init {
        oben.typeface = Schriften.von(context, Schrift.GRUNDSCHRIFT)
        gross.typeface = Schriften.von(context, Schrift.FETT, fettFallsSystem = true)
        unten.typeface = Schriften.von(context, Schrift.GRUNDSCHRIFT)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        context.registerReceiver(takt, IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
        })
        invalidate()
    }

    override fun onDetachedFromWindow() {
        try { context.unregisterReceiver(takt) } catch (_: Exception) {}
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w < 4 || h < 4) return
        karte(canvas)
        val a = quelle()
        contentDescription = listOf(a.oben, a.gross, a.unten).filter { it.isNotEmpty() }.joinToString(", ")
        // Flache Kachel (z. B. 2×1): die Zeilen bekommen anteilig mehr Höhe
        val flach = h < w * 0.45f
        passe(oben, a.oben, w * 0.86f, h * (if (flach) 0.2f else 0.13f))
        passe(gross, a.gross, w * 0.86f, h * (if (flach) 0.38f else 0.4f))
        passe(unten, a.unten, w * 0.86f, h * (if (flach) 0.18f else 0.12f))
        val lo = if (a.oben.isEmpty()) 0f else oben.textSize * 1.25f
        gross.getTextBounds("0123456789Ag", 0, 12, masse)
        val lg = masse.height().toFloat()
        val lu = if (a.unten.isEmpty()) 0f else unten.textSize * 1.25f
        val luecke = h * 0.06f
        var y = (h - lo - lg - lu - luecke * 2) / 2f
        if (a.oben.isNotEmpty()) { canvas.drawText(kurz(a.oben, oben, w * 0.9f), w / 2, y + oben.textSize, oben); y += lo + luecke }
        canvas.drawText(kurz(a.gross, gross, w * 0.92f), w / 2, y - masse.top, gross)
        y += lg + luecke
        if (a.unten.isNotEmpty()) canvas.drawText(kurz(a.unten, unten, w * 0.9f), w / 2, y + unten.textSize, unten)
    }

    private fun kurz(t: String, p: TextPaint, b: Float) = TextUtils.ellipsize(t, p, b, TextUtils.TruncateAt.END).toString()

    /** Größte Schrift, mit der [t] in [b]×[h] passt (Höhe begrenzt auch kurze Texte). */
    private fun passe(p: TextPaint, t: String, b: Float, h: Float) {
        p.textSize = 100f
        val breite = p.measureText(t.ifEmpty { "x" }).coerceAtLeast(1f)
        p.textSize = minOf(100f * b / breite, h).coerceAtLeast(6f)
    }
}

/** QR-Code mit Beschriftung darunter (WLAN-Name bzw. Adresse ohne https://). */
class QrAnsicht(context: Context) : KartenAnsicht(context) {
    private val dunkel = Paint().apply { color = 0xFF111827.toInt() }
    private val schrift = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF111827.toInt(); textAlign = Paint.Align.CENTER }
    private var code: QrCode? = null

    init {
        schrift.typeface = Schriften.von(context, Schrift.FETT, fettFallsSystem = true)
    }

    var text: String = ""
        set(v) {
            if (field == v && code != null) return
            field = v
            code = if (v.isBlank()) null else QrCode.aus(v)
            contentDescription = "QR-Code: " + beschriftung()
            invalidate()
        }

    fun beschriftung(): String = QrCode.wlanName(text)?.let { "WLAN „$it“" }
        ?: text.removePrefix("https://").removePrefix("http://").removePrefix("www.").substringBefore('?').trimEnd('/').ifBlank { "QR-Code einrichten" }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w < 4 || h < 4) return
        karte(canvas)
        val q = code
        val textH = h * 0.14f
        // Ruhezone: zwei Module; der weiße Rand der Fläche kommt dazu
        val platz = minOf(w * 0.86f, h - textH * 1.6f - h * 0.08f)
        if (q == null) {
            schrift.textSize = textH
            canvas.drawText(if (text.isBlank()) "QR-Code einrichten" else "Text zu lang", w / 2, h / 2, schrift)
            return
        }
        val modul = (platz / (q.groesse + 4)).let { if (it >= 2f) kotlin.math.floor(it) else it }
        val seite = modul * q.groesse
        val x0 = (w - seite) / 2f
        val y0 = (h - seite - textH * 1.4f) / 2f
        for (y in 0 until q.groesse) for (x in 0 until q.groesse) {
            if (q.dunkel(x, y)) canvas.drawRect(x0 + x * modul, y0 + y * modul, x0 + (x + 1) * modul + 0.5f, y0 + (y + 1) * modul + 0.5f, dunkel)
        }
        schrift.textSize = textH
        val t = beschriftung()
        val breite = schrift.measureText(t)
        if (breite > w * 0.88f) schrift.textSize = textH * w * 0.88f / breite
        canvas.drawText(TextUtils.ellipsize(t, schrift, w * 0.9f, TextUtils.TruncateAt.END).toString(), w / 2, y0 + seite + textH * 1.25f, schrift)
    }
}

/** Ordner auf dem Startbildschirm: helle Fläche mit bis zu neun kleinen Symbolen, Name darunter. */
class OrdnerZelle(context: Context) : View(context) {
    var ordner: Ordner? = null
        private set
    private var iconProzent = 60
    private var mitText = true
    private val bilder = HashMap<String, Bitmap>()
    private var geladenFuer = ""
    private val flaeche = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66FFFFFF }
    private val rand = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x99FFFFFF.toInt(); style = Paint.Style.STROKE }
    private val bildPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        setShadowLayer(6f, 0f, 2f, 0xE6000000.toInt())
    }
    private val feld = RectF()

    init {
        isClickable = true
        isLongClickable = true
        isHapticFeedbackEnabled = false
    }

    fun setze(o: Ordner, iconProzent: Int, textGroesse: Float, mitText: Boolean, schrift: android.graphics.Typeface) {
        ordner = o
        this.iconProzent = iconProzent
        this.mitText = mitText
        textPaint.textSize = textGroesse
        textPaint.typeface = schrift
        contentDescription = "Ordner ${o.name}, ${o.apps.size} Apps"
        geladenFuer = ""
        invalidate()
    }

    private fun groesse(): Float {
        val textH = if (mitText) textPaint.textSize * 1.5f else 0f
        return minOf(width.toFloat(), height - textH) * iconProzent / 100f
    }

    private fun sichtbar() = ordner?.apps.orEmpty().filter { AppKatalog.istDa(it.paket, it.aktivitaet) }.take(9)

    private fun spalten(n: Int) = if (n <= 4) 2 else 3

    private fun lade() {
        val apps = sichtbar()
        if (width == 0 || apps.isEmpty()) return
        val klein = (groesse() / spalten(apps.size) * 0.78f).toInt().coerceAtLeast(8)
        val schluessel = apps.joinToString { it.schluessel + it.icon } + "@$klein"
        if (schluessel == geladenFuer) return
        geladenFuer = schluessel
        bilder.clear()
        for (a in apps) {
            val sofort = AppKatalog.icon(ComponentName(a.paket, a.aktivitaet), a.icon, klein) { b ->
                if (geladenFuer == schluessel && b != null) { bilder[a.schluessel] = b; invalidate() }
            }
            if (sofort != null) bilder[a.schluessel] = sofort
        }
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val o = ordner ?: return
        lade()
        if (isPressed) canvas.scale(0.92f, 0.92f, width / 2f, height / 2f)
        val g = groesse()
        val textH = if (mitText) textPaint.textSize * 1.5f else 0f
        val obenY = (height - g - textH) / 2f
        feld.set((width - g) / 2f, obenY, (width + g) / 2f, obenY + g)
        rand.strokeWidth = maxOf(2f, g * 0.015f)
        canvas.drawRoundRect(feld, g * 0.22f, g * 0.22f, flaeche)
        canvas.drawRoundRect(feld, g * 0.22f, g * 0.22f, rand)
        val apps = sichtbar()
        val n = spalten(apps.size)
        val zelle = g * 0.86f / n
        val klein = zelle * 0.78f
        apps.forEachIndexed { i, a ->
            val b = bilder[a.schluessel] ?: return@forEachIndexed
            val x = feld.left + g * 0.07f + (i % n) * zelle + (zelle - klein) / 2
            val y = feld.top + g * 0.07f + (i / n) * zelle + (zelle - klein) / 2
            canvas.drawBitmap(b, null, RectF(x, y, x + klein, y + klein), bildPaint)
        }
        if (mitText) {
            val t = TextUtils.ellipsize(o.name, textPaint, width * 0.96f, TextUtils.TruncateAt.END).toString()
            canvas.drawText(t, (width - textPaint.measureText(t)) / 2f, obenY + g + textPaint.textSize * 1.15f, textPaint)
        }
    }
}
