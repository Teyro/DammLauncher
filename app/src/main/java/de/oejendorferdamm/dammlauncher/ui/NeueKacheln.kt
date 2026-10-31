/*
 * DammLauncher – Copyright (C) 2026 Teyro – GNU GPL v3 oder neuer, siehe LICENSE
 */
package de.oejendorferdamm.dammlauncher.ui

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.media.AudioManager
import android.text.TextPaint
import android.text.TextUtils
import android.view.MotionEvent
import de.oejendorferdamm.dammlauncher.konfig.Schrift

/** Größte Schrift, mit der [t] in [b] × [h] passt. */
private fun passe(p: Paint, t: String, b: Float, h: Float) {
    p.textSize = 100f
    val breite = p.measureText(t.ifEmpty { "x" }).coerceAtLeast(1f)
    p.textSize = minOf(100f * b / breite, h).coerceAtLeast(6f)
}

private fun kurz(t: String, p: TextPaint, b: Float) = TextUtils.ellipsize(t, p, b, TextUtils.TruncateAt.END).toString()

/**
 * Großer Lautstärke-Regler (Medien-Lautstärke): über den Balken wischen oder tippen; das Symbol
 * links schaltet stumm bzw. wieder laut. Auf vielen Boards ist der eigene Regler schwer zu finden.
 */
class LautstaerkeKachel(context: Context) : KartenAnsicht(context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val titel = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF4B5563.toInt(); textAlign = Paint.Align.LEFT }
    private val wert = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF111827.toInt(); textAlign = Paint.Align.RIGHT }
    private val symbol = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val spur = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFD5DCE4.toInt() }
    private val fuellung = Paint(Paint.ANTI_ALIAS_FLAG)
    private val balken = RectF()
    private val knopf = RectF()
    private var vorStumm = 0

    private val aenderung = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = invalidate()
    }

    init {
        titel.typeface = Schriften.von(context, Schrift.GRUNDSCHRIFT)
        wert.typeface = Schriften.von(context, Schrift.FETT, fettFallsSystem = true)
        contentDescription = "Lautstärke"
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Lautstärke von außen geändert (Tasten am Board): neu zeichnen
        try {
            val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
            if (android.os.Build.VERSION.SDK_INT >= 33) context.registerReceiver(aenderung, filter, Context.RECEIVER_EXPORTED) else context.registerReceiver(aenderung, filter)
        } catch (_: Exception) {}
    }

    override fun onDetachedFromWindow() {
        try { context.unregisterReceiver(aenderung) } catch (_: Exception) {}
        super.onDetachedFromWindow()
    }

    private fun max() = (audio?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15).coerceAtLeast(1)
    private fun jetzt() = audio?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w < 4 || h < 4) return
        karte(canvas)
        val v = jetzt()
        val prozent = v * 100 / max()
        val rand = minOf(w, h) * 0.12f
        val hb = (h * 0.22f).coerceAtMost(w * 0.12f)
        val mitte = h * 0.62f
        knopf.set(rand, mitte - hb * 0.8f, rand + hb * 1.6f, mitte + hb * 0.8f)
        balken.set(knopf.right + rand * 0.6f, mitte - hb / 2f, w - rand, mitte + hb / 2f)
        passe(titel, "Lautstärke", w * 0.5f, h * 0.16f)
        canvas.drawText("Lautstärke", rand, rand + titel.textSize, titel)
        passe(wert, "100 %", w * 0.3f, h * 0.2f)
        canvas.drawText(if (v == 0) "stumm" else "$prozent %", w - rand, rand + wert.textSize * 0.95f, wert)
        symbol.textSize = knopf.height() * 0.8f
        canvas.drawText(if (v == 0) "🔇" else "🔊", knopf.centerX(), knopf.centerY() + symbol.textSize * 0.35f, symbol)
        val r = balken.height() / 2f
        canvas.drawRoundRect(balken, r, r, spur)
        fuellung.color = Thema.akzent.takeIf { Thema.clevertouch } ?: Farben.AKZENT
        if (v > 0) canvas.drawRoundRect(balken.left, balken.top, balken.left + maxOf(balken.height(), balken.width() * v / max()), balken.bottom, r, r, fuellung)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val a = audio ?: return false
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (knopf.contains(ev.x, ev.y)) {
                    val v = jetzt()
                    if (v > 0) { vorStumm = v; setze(a, 0) } else setze(a, vorStumm.takeIf { it > 0 } ?: (max() / 3))
                    return false
                }
                setzeAus(a, ev.x)
            }
            MotionEvent.ACTION_MOVE -> setzeAus(a, ev.x)
        }
        return true
    }

    private fun setzeAus(a: AudioManager, x: Float) {
        if (balken.width() <= 0) return
        val anteil = ((x - balken.left) / balken.width()).coerceIn(0f, 1f)
        setze(a, Math.round(anteil * max()))
    }

    private fun setze(a: AudioManager, v: Int) {
        try { a.setStreamVolume(AudioManager.STREAM_MUSIC, v.coerceIn(0, max()), 0) } catch (_: Exception) {}
        invalidate()
    }
}

/**
 * Zufallsname bzw. Zufallszahl: antippen zieht – ohne Animation – den nächsten, der noch nicht dran
 * war. Sind alle durch, wird neu gemischt. [quelle] liefert die Liste und merkt sich die Gezogenen.
 */
class ZufallKachel(context: Context, private val quelle: () -> List<String>, private val gezogen: () -> Set<String>, private val merke: (Set<String>) -> Unit) : KartenAnsicht(context) {
    private val oben = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF4B5563.toInt(); textAlign = Paint.Align.CENTER }
    private val gross = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF111827.toInt(); textAlign = Paint.Align.CENTER }
    private val unten = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF4B5563.toInt(); textAlign = Paint.Align.CENTER }
    private var aktuell = ""

    init {
        oben.typeface = Schriften.von(context, Schrift.GRUNDSCHRIFT)
        gross.typeface = Schriften.von(context, Schrift.FETT, fettFallsSystem = true)
        unten.typeface = Schriften.von(context, Schrift.GRUNDSCHRIFT)
        setOnClickListener { ziehen() }
        contentDescription = "Zufall – antippen"
    }

    fun ziehen() {
        val alle = quelle()
        if (alle.isEmpty()) { aktuell = ""; invalidate(); return }
        var schon = gezogen().intersect(alle.toSet())
        if (schon.size >= alle.size) schon = emptySet()
        val rest = alle.filter { it !in schon }
        aktuell = rest[(Math.random() * rest.size).toInt().coerceIn(0, rest.size - 1)]
        merke(schon + aktuell)
        contentDescription = "Zufall: $aktuell"
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w < 4 || h < 4) return
        karte(canvas)
        val alle = quelle()
        val rest = alle.size - gezogen().intersect(alle.toSet()).size
        val obenT = if (alle.isEmpty()) "Zufall" else "Wer ist dran?"
        val grossT = when {
            alle.isEmpty() -> "Namen eintragen"
            aktuell.isEmpty() -> "Antippen"
            else -> aktuell
        }
        val untenT = if (alle.isEmpty()) "lange drücken → einrichten" else if (aktuell.isEmpty()) "${alle.size} zur Auswahl" else "noch $rest übrig"
        passe(oben, obenT, w * 0.86f, h * 0.13f)
        passe(gross, grossT, w * 0.88f, h * 0.38f)
        passe(unten, untenT, w * 0.86f, h * 0.12f)
        canvas.drawText(kurz(obenT, oben, w * 0.9f), w / 2, h * 0.08f + oben.textSize, oben)
        canvas.drawText(kurz(grossT, gross, w * 0.92f), w / 2, h / 2f + gross.textSize * 0.35f, gross)
        canvas.drawText(kurz(untenT, unten, w * 0.9f), w / 2, h * 0.92f, unten)
    }
}

/**
 * Kachel mit großen Knöpfen (z. B. Schnelleinstellungen oder „Bildschirm teilen“): [knoepfe] =
 * (Symbol, Beschriftung); antippen meldet den Index. Bei einem Knopf füllt er die ganze Kachel.
 */
class KnopfKachel(context: Context, private val titel: String, private val quelle: () -> List<Pair<String, String>>, private val klick: (Int) -> Unit) : KartenAnsicht(context) {
    private val kopf = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF4B5563.toInt(); textAlign = Paint.Align.LEFT }
    private val zeichen = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val name = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF111827.toInt(); textAlign = Paint.Align.CENTER }
    private val flaeche = Paint(Paint.ANTI_ALIAS_FLAG)
    private val felder = ArrayList<RectF>()
    private var gedrueckt = -1

    init {
        kopf.typeface = Schriften.von(context, Schrift.GRUNDSCHRIFT)
        name.typeface = Schriften.von(context, Schrift.FETT, fettFallsSystem = true)
        contentDescription = titel
    }

    private var knoepfe: List<Pair<String, String>> = emptyList()

    private fun anordnen(w: Float, h: Float, oben: Float) {
        felder.clear()
        val n = knoepfe.size.coerceAtLeast(1)
        val spalten = when {
            n == 1 -> 1
            w > h * 1.6f -> n.coerceAtMost(4)
            else -> if (n <= 4) 2 else 3
        }
        val zeilen = (n + spalten - 1) / spalten
        val rand = minOf(w, h) * 0.07f
        val lw = (w - rand * (spalten + 1)) / spalten
        val lh = (h - oben - rand * (zeilen + 1)) / zeilen
        for (i in 0 until n) {
            val x = rand + (i % spalten) * (lw + rand)
            val y = oben + rand + (i / spalten) * (lh + rand)
            felder.add(RectF(x, y, x + lw, y + lh))
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w < 4 || h < 4) return
        karte(canvas)
        knoepfe = quelle()
        val oben = if (knoepfe.size > 1) h * 0.17f else 0f
        if (oben > 0) {
            passe(kopf, titel, w * 0.7f, oben * 0.62f)
            canvas.drawText(titel, minOf(w, h) * 0.08f, oben * 0.82f, kopf)
        }
        anordnen(w, h, oben)
        val akzent = if (Thema.clevertouch) Thema.akzent else Farben.AKZENT
        knoepfe.forEachIndexed { i, (sym, text) ->
            val f = felder.getOrNull(i) ?: return@forEachIndexed
            flaeche.color = if (i == gedrueckt) (akzent and 0x00FFFFFF) or 0x40000000 else (akzent and 0x00FFFFFF) or 0x1C000000
            val r = minOf(f.width(), f.height()) * 0.22f
            canvas.drawRoundRect(f, r, r, flaeche)
            val einzeln = knoepfe.size == 1
            zeichen.textSize = f.height() * if (einzeln) 0.34f else 0.4f
            passe(name, text, f.width() * 0.86f, f.height() * if (einzeln) 0.16f else 0.2f)
            val gesamt = zeichen.textSize + name.textSize * 1.2f
            val y0 = f.centerY() - gesamt / 2f
            canvas.drawText(sym, f.centerX(), y0 + zeichen.textSize * 0.85f, zeichen)
            canvas.drawText(kurz(text, name, f.width() * 0.9f), f.centerX(), y0 + zeichen.textSize + name.textSize, name)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val i = felder.indexOfFirst { it.contains(ev.x, ev.y) }
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> { gedrueckt = i; invalidate(); return i >= 0 }
            MotionEvent.ACTION_MOVE -> if (i != gedrueckt) { gedrueckt = -1; invalidate() }
            MotionEvent.ACTION_UP -> {
                val war = gedrueckt
                gedrueckt = -1
                invalidate()
                if (war >= 0 && war == i) klick(war)
            }
            MotionEvent.ACTION_CANCEL -> { gedrueckt = -1; invalidate() }
        }
        return true
    }
}
