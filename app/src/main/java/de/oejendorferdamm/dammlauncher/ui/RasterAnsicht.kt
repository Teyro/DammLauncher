/*
 * DammLauncher – Copyright (C) 2026 Teyro – GNU GPL v3 oder neuer, siehe LICENSE
 */
package de.oejendorferdamm.dammlauncher.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import de.oejendorferdamm.dammlauncher.apps.AppKatalog
import de.oejendorferdamm.dammlauncher.konfig.Element
import de.oejendorferdamm.dammlauncher.konfig.Konfig
import de.oejendorferdamm.dammlauncher.raster.Belegung
import de.oejendorferdamm.dammlauncher.raster.RasterRechner
import kotlin.math.abs
import kotlin.math.hypot

/** Was der Startbildschirm bei Gesten auslöst (setzt die HomeActivity um). */
interface RasterAktionen {
    fun starte(app: Element.App, v: View)
    fun langerDruckLeer()
    fun nachOben()
    fun verschiebe(e: Element, spalte: Int, zeile: Int)
    fun entferne(e: Element)
    fun aendereGroesse(w: Element.Widget, spalte: Int, zeile: Int, breite: Int, hoehe: Int)
    fun menue(e: Element)
    fun gesperrt(): Boolean
    fun widgetEinrichten(w: Element.Widget)
    /** Platzhalter angetippt: App bzw. Widget-App ist auf diesem Gerät nicht installiert. */
    fun fehlt(e: Element)
    fun nachUnten()
}

/**
 * Das Raster des Startbildschirms: legt Apps und Widgets in ihre Zellen, ganz ohne Animationen.
 * Langer Druck auf ein Element → Ziehen (oben erscheint „Entfernen“); loslassen ohne Bewegung →
 * Menü. Langer Druck auf freie Fläche → Einstellungen. Nach oben wischen → App-Übersicht.
 */
class RasterAnsicht(context: Context, private val aktionen: RasterAktionen) : ViewGroup(context) {
    private var konfig = Konfig()
    private var rechner: RasterRechner? = null
    private val ansichten = LinkedHashMap<String, View>()
    private val elementVon = HashMap<View, Element>()
    private val slop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()

    /** Hilfslinien (beim Einstellen): zeigt alle Zellen. */
    var hilfslinien = false
        set(v) { field = v; invalidate() }

    // ---- Ziehen
    private class Ziehen(val e: Element, val v: View, val startX: Float, val startY: Float) {
        var bewegt = false
        var ziel: Pair<Int, Int>? = null
        var ueberEntfernen = false
    }
    private var ziehen: Ziehen? = null
    private var letztesX = 0f
    private var letztesY = 0f

    // ---- Größe ändern (Widgets)
    private class Groesse(val w: Element.Widget, var s: Int, var z: Int, var b: Int, var h: Int) { var griff = -1 }
    private var groesse: Groesse? = null

    // ---- freie Fläche
    private var downX = 0f
    private var downY = 0f
    private var nachObenErledigt = false
    private var startInWidget = false
    private val langerDruck = Runnable {
        if (ziehen == null && groesse == null) aktionen.langerDruckLeer()
    }

    private val malen = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linie = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val schrift = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textAlign = Paint.Align.CENTER; isFakeBoldText = true }

    init {
        setWillNotDraw(false)
        clipChildren = false
    }

    /** Neue Konfiguration bzw. sichtbare Elemente übernehmen – vorhandene Ansichten werden wiederverwendet. */
    /** Elemente, die gerade als Platzhalter gezeigt werden (App bzw. Widget fehlt). */
    private val platzhalter = HashSet<String>()

    /**
     * Neue Konfiguration übernehmen – vorhandene Ansichten werden wiederverwendet. Elemente aus
     * [fehlt] (App nicht installiert) erscheinen als Platzhalter.
     */
    fun setze(k: Konfig, elemente: List<Element>, fehlt: Set<String>, widgetAnsicht: (Element.Widget) -> View?) {
        konfig = k
        rechner = null
        val schluessel = elemente.map { it.schluessel }.toSet()
        // Weg damit, was nicht mehr da ist – und Platzhalter, deren App jetzt da ist (bzw. umgekehrt)
        ansichten.keys.filter { it !in schluessel || (it in platzhalter) != (it in fehlt) }.forEach { s ->
            ansichten.remove(s)?.let { removeView(it); elementVon.remove(it) }
            platzhalter.remove(s)
        }
        val textPx = k.textGroesse.vp.toFloat()
        val schrift = Schriften.von(context, k.schrift)
        for (e in elemente) {
            var v = ansichten[e.schluessel]
            if (e.schluessel in fehlt) {
                if (v == null) {
                    v = PlatzhalterAnsicht(context, e).also { neu ->
                        neu.setOnClickListener { elementVon[neu]?.let { aktionen.fehlt(it) } }
                        neu.setOnLongClickListener { elementVon[neu]?.let { beginneZiehen(it, neu) }; true }
                        addView(neu)
                        ansichten[e.schluessel] = neu
                        platzhalter.add(e.schluessel)
                    }
                }
                (v as PlatzhalterAnsicht).setze(e, textPx, schrift, k.beschriftung)
                elementVon[v] = e
                continue
            }
            when (e) {
                is Element.App -> {
                    val zelle = (v as? AppZelle) ?: AppZelle(context).also { neu ->
                        neu.setOnClickListener { (elementVon[neu] as? Element.App)?.let { aktionen.starte(it, neu) } }
                        neu.setOnLongClickListener { elementVon[neu]?.let { beginneZiehen(it, neu) }; true }
                        addView(neu)
                        ansichten[e.schluessel] = neu
                    }
                    val info = AppKatalog.finde(e.paket, e.aktivitaet)
                    zelle.setze(android.content.ComponentName(e.paket, e.aktivitaet), e.titel ?: info?.name ?: e.paket, e.icon, k.iconProzent, textPx, k.beschriftung, schrift)
                    v = zelle
                }
                is Element.Widget -> {
                    if (v == null) {
                        val inhalt = widgetAnsicht(e)
                        val rahmen = WidgetRahmen(context, this)
                        if (inhalt != null) rahmen.addView(inhalt, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
                        else rahmen.addView(TextView(context).apply {
                            text = "Widget\n(antippen zum Einrichten)"
                            gravity = Gravity.CENTER
                            setTextColor(Color.WHITE)
                            groesse(22)
                            background = runde(0x55000000, 20f.vp, 3.vp, 0x88FFFFFF.toInt())
                            setOnClickListener { (elementVon[rahmen] as? Element.Widget)?.let { aktionen.widgetEinrichten(it) } }
                        }, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
                        v = rahmen
                        addView(v)
                        ansichten[e.schluessel] = v
                    }
                    (v as? WidgetRahmen)?.let { r ->
                        r.zoom = if (e.istUhr) 100 else e.zoom
                        (r.getChildAt(0) as? UhrAnsicht)?.let { u ->
                            u.transparent = e.provider == Element.UHR_TRANSPARENT
                            u.stil = e.uhr
                        }
                    }
                }
            }
            elementVon[v] = e
        }
        requestLayout()
        invalidate()
    }

    /** Ein Widget-Rahmen muss neu gebaut werden (z. B. nach dem Binden). */
    fun vergiss(schluessel: String) {
        ansichten.remove(schluessel)?.let { removeView(it); elementVon.remove(it) }
    }

    fun widgetRahmen(): Map<Element.Widget, View> = elementVon.filterKeys { it is WidgetRahmen }.entries.associate { (v, e) -> (e as Element.Widget) to v }

    private fun r(): RasterRechner = rechner ?: RasterRechner.aus(konfig, width.coerceAtLeast(1), height.coerceAtLeast(1)).also { rechner = it }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        rechner = null
    }

    /** Feld eines Elements (während der Größenänderung das vorläufige). */
    private fun feldVon(e: Element): RectF {
        val g = groesse
        val f = if (g != null && e is Element.Widget && g.w.kennung == e.kennung) r().feld(g.s, g.z, g.b, g.h) else r().feld(e.spalte, e.zeile, e.breite, e.hoehe)
        return RectF(f.links, f.oben, f.rechts, f.unten)
    }

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        val w = MeasureSpec.getSize(wSpec)
        val h = MeasureSpec.getSize(hSpec)
        setMeasuredDimension(w, h)
        rechner = RasterRechner.aus(konfig, w.coerceAtLeast(1), h.coerceAtLeast(1))
        for ((v, e) in elementVon) {
            val f = feldVon(e)
            v.measure(MeasureSpec.makeMeasureSpec(f.width().toInt().coerceAtLeast(1), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(f.height().toInt().coerceAtLeast(1), MeasureSpec.EXACTLY))
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, rr: Int, b: Int) {
        for ((v, e) in elementVon) {
            val f = feldVon(e)
            v.layout(f.left.toInt(), f.top.toInt(), f.left.toInt() + v.measuredWidth, f.top.toInt() + v.measuredHeight)
        }
    }

    // ------------------------------------------------------------ Zeichnen (Hilfslinien, Ziel, Entfernen)

    private fun entfernenFeld(): RectF {
        val b = 520f.vp
        return RectF((width - b) / 2f, 18f.vp, (width + b) / 2f, 18f.vp + 96f.vp)
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (hilfslinien) {
            linie.color = 0xAAFFFFFF.toInt()
            linie.strokeWidth = 2f.vp
            linie.pathEffect = android.graphics.DashPathEffect(floatArrayOf(10f.vp, 8f.vp), 0f)
            for (z in 0 until konfig.zeilen) for (s in 0 until konfig.spalten) {
                val f = r().feld(s, z)
                canvas.drawRoundRect(f.links, f.oben, f.rechts, f.unten, 12f.vp, 12f.vp, linie)
            }
            linie.pathEffect = null
        }
        val zz = ziehen
        if (zz != null && zz.bewegt) {
            zz.ziel?.let { (s, z) ->
                val frei = Belegung.frei(konfig.elemente, s, z, zz.e.breite, zz.e.hoehe, konfig.spalten, konfig.zeilen, zz.e.schluessel)
                val f = r().feld(s, z, zz.e.breite, zz.e.hoehe)
                malen.color = if (frei) 0x5532C864 else 0x55E04040
                canvas.drawRoundRect(f.links, f.oben, f.rechts, f.unten, 16f.vp, 16f.vp, malen)
            }
        }
        super.dispatchDraw(canvas)
        if (zz != null && zz.bewegt) {
            val f = entfernenFeld()
            malen.color = if (zz.ueberEntfernen) 0xFFC62828.toInt() else 0xDD8E1C1C.toInt()
            canvas.drawRoundRect(f, 48f.vp, 48f.vp, malen)
            schrift.textSize = 38f.vp
            canvas.drawText("✕  Vom Startbildschirm entfernen", f.centerX(), f.centerY() + 13f.vp, schrift)
        }
        groesse?.let { g ->
            val f = r().feld(g.s, g.z, g.b, g.h)
            linie.color = 0xFF2563EB.toInt()
            linie.strokeWidth = 6f.vp
            canvas.drawRoundRect(f.links, f.oben, f.rechts, f.unten, 14f.vp, 14f.vp, linie)
            malen.color = Color.WHITE
            griffe(g).forEach { (x, y) ->
                malen.color = 0xFF2563EB.toInt()
                canvas.drawCircle(x, y, 34f.vp, malen)
                malen.color = Color.WHITE
                canvas.drawCircle(x, y, 22f.vp, malen)
            }
            schrift.textSize = 30f.vp
            canvas.drawText("Griffe ziehen – daneben tippen = fertig", width / 2f, height - 30f.vp, schrift)
        }
    }

    /** Griffe: links, oben, rechts, unten (Mitte der Kanten). */
    private fun griffe(g: Groesse): List<Pair<Float, Float>> {
        val f = r().feld(g.s, g.z, g.b, g.h)
        val mx = (f.links + f.rechts) / 2f
        val my = (f.oben + f.unten) / 2f
        return listOf(f.links to my, mx to f.oben, f.rechts to my, mx to f.unten)
    }

    // ------------------------------------------------------------ Ziehen

    fun beginneZiehen(e: Element, v: View) {
        if (aktionen.gesperrt() || groesse != null) return
        removeCallbacks(langerDruck)
        ziehen = Ziehen(e, v, letztesX, letztesY)
        v.alpha = 0.85f
        v.elevation = 30f.vp
        v.bringToFront()
        // Kinder bekommen jetzt ein CANCEL, die Bewegung gehört dem Raster
        parent?.requestDisallowInterceptTouchEvent(true)
        invalidate()
    }

    private fun zieheZu(x: Float, y: Float) {
        val zz = ziehen ?: return
        val dx = x - zz.startX
        val dy = y - zz.startY
        if (!zz.bewegt && hypot(dx, dy) > slop * 2) zz.bewegt = true
        zz.v.translationX = dx
        zz.v.translationY = dy
        zz.ueberEntfernen = zz.bewegt && entfernenFeld().contains(x, y)
        // Zielzelle: obere linke Zelle unter der Mitte der ersten Zelle des gezogenen Elements
        val f = r().feld(zz.e.spalte, zz.e.zeile)
        zz.ziel = r().zelleBei(f.links + dx + r().zellBreite / 2f, f.oben + dy + r().zellHoehe / 2f)
        invalidate()
    }

    private fun beendeZiehen(abbrechen: Boolean) {
        val zz = ziehen ?: return
        ziehen = null
        zz.v.translationX = 0f
        zz.v.translationY = 0f
        zz.v.alpha = 1f
        zz.v.elevation = 0f
        invalidate()
        if (abbrechen) return
        when {
            !zz.bewegt -> aktionen.menue(zz.e)
            zz.ueberEntfernen -> aktionen.entferne(zz.e)
            else -> zz.ziel?.let { (s, z) ->
                if ((s != zz.e.spalte || z != zz.e.zeile) &&
                    Belegung.frei(konfig.elemente, s, z, zz.e.breite, zz.e.hoehe, konfig.spalten, konfig.zeilen, zz.e.schluessel)
                ) aktionen.verschiebe(zz.e, s, z)
            }
        }
        requestLayout()
    }

    // ------------------------------------------------------------ Größe ändern

    fun beginneGroesse(w: Element.Widget) {
        groesse = Groesse(w, w.spalte, w.zeile, w.breite, w.hoehe)
        invalidate()
    }

    fun beendeGroesse() {
        val g = groesse ?: return
        groesse = null
        if (g.s != g.w.spalte || g.z != g.w.zeile || g.b != g.w.breite || g.h != g.w.hoehe) aktionen.aendereGroesse(g.w, g.s, g.z, g.b, g.h)
        requestLayout()
        invalidate()
    }

    private fun groesseZiehen(g: Groesse, x: Float, y: Float) {
        val zelle = r().zelleBei(x.coerceIn(0f, width - 1f), y.coerceIn(0f, height - 1f)) ?: return
        var s = g.s; var z = g.z; var b = g.b; var h = g.h
        when (g.griff) {
            0 -> { val rechts = s + b; s = minOf(zelle.first, rechts - 1); b = rechts - s }
            1 -> { val unten = z + h; z = minOf(zelle.second, unten - 1); h = unten - z }
            2 -> b = maxOf(zelle.first - s + 1, 1)
            3 -> h = maxOf(zelle.second - z + 1, 1)
        }
        if (Belegung.frei(konfig.elemente, s, z, b, h, konfig.spalten, konfig.zeilen, g.w.schluessel)) {
            g.s = s; g.z = z; g.b = b; g.h = h
            requestLayout()
            invalidate()
        }
    }

    // ------------------------------------------------------------ Berührung

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        letztesX = ev.x
        letztesY = ev.y
        return super.dispatchTouchEvent(ev)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        // Ziehen begann im Kind (langer Druck): das erste abgefangene Ereignis erreicht onTouchEvent
        // nicht – deshalb hier schon auswerten (sonst ginge z. B. ein sofortiges Loslassen verloren).
        ziehen?.let {
            when (ev.actionMasked) {
                MotionEvent.ACTION_MOVE -> zieheZu(ev.x, ev.y)
                MotionEvent.ACTION_UP -> { zieheZu(ev.x, ev.y); beendeZiehen(false) }
                MotionEvent.ACTION_CANCEL -> beendeZiehen(true)
            }
            return true
        }
        if (groesse != null) return true
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x; downY = ev.y
                nachObenErledigt = false
                // Über der eingebauten Uhr darf gewischt werden, über fremden Widgets nicht (die scrollen evtl. selbst)
                startInWidget = elementVon.any { (v, e) -> v is WidgetRahmen && !(e as Element.Widget).istUhr && ev.x >= v.left && ev.x < v.right && ev.y >= v.top && ev.y < v.bottom }
            }
            MotionEvent.ACTION_MOVE -> if (istNachOben(ev) || istNachUnten(ev)) return true
        }
        return false
    }

    private fun istNachUnten(ev: MotionEvent): Boolean {
        if (startInWidget || nachObenErledigt) return false
        val dy = ev.y - downY
        return dy > 140f.vp && dy > 2 * abs(ev.x - downX)
    }

    /** Deutlich nach oben gewischt (nicht in einem Widget, das selbst scrollen könnte)? */
    private fun istNachOben(ev: MotionEvent): Boolean {
        if (startInWidget || nachObenErledigt) return false
        val dy = downY - ev.y
        return dy > 140f.vp && dy > 2 * abs(ev.x - downX)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        ziehen?.let {
            when (ev.actionMasked) {
                MotionEvent.ACTION_MOVE -> zieheZu(ev.x, ev.y)
                MotionEvent.ACTION_UP -> { zieheZu(ev.x, ev.y); beendeZiehen(false) }
                MotionEvent.ACTION_CANCEL -> beendeZiehen(true)
            }
            return true
        }
        groesse?.let { g ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    g.griff = griffe(g).indexOfFirst { (x, y) -> hypot(ev.x - x, ev.y - y) < 70f.vp }
                    if (g.griff < 0) {
                        // Tippen neben das Widget beendet die Größenänderung
                        val f = r().feld(g.s, g.z, g.b, g.h)
                        if (!(ev.x in f.links..f.rechts && ev.y in f.oben..f.unten)) beendeGroesse()
                    }
                }
                MotionEvent.ACTION_MOVE -> if (g.griff >= 0) groesseZiehen(g, ev.x, ev.y)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> g.griff = -1
            }
            return true
        }
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x; downY = ev.y
                nachObenErledigt = false
                startInWidget = false
                postDelayed(langerDruck, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE -> {
                if (hypot(ev.x - downX, ev.y - downY) > slop) removeCallbacks(langerDruck)
                if (istNachOben(ev)) {
                    nachObenErledigt = true
                    removeCallbacks(langerDruck)
                    aktionen.nachOben()
                } else if (istNachUnten(ev)) {
                    nachObenErledigt = true
                    removeCallbacks(langerDruck)
                    aktionen.nachUnten()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> removeCallbacks(langerDruck)
        }
        return true
    }

    /** Ziehen/Größe abbrechen (Home-Taste, Fenster geht auf). */
    fun abbrechen() {
        removeCallbacks(langerDruck)
        beendeZiehen(true)
        if (groesse != null) beendeGroesse()
    }
}

/**
 * Hülle um ein Widget: erkennt den langen Druck selbst (Widgets schlucken sonst alle Berührungen)
 * und reicht ihn ans Raster weiter.
 */
class WidgetRahmen(context: Context, private val raster: RasterAnsicht) : FrameLayout(context) {
    /**
     * Inhalt vergrößern (Prozent): Das Widget bekommt ein entsprechend kleineres Feld und wird
     * hochskaliert – so füllt z. B. eine Suchleiste mit fester Höhe auch auf 75 Zoll ihr Feld.
     */
    var zoom = 100
        set(v) {
            if (field == v) return
            field = v
            requestLayout()
        }

    /** Größe, die das Widget selbst sieht (vor dem Hochskalieren). */
    val innenBreite get() = width * 100 / zoom
    val innenHoehe get() = height * 100 / zoom

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        val b = MeasureSpec.getSize(wSpec)
        val h = MeasureSpec.getSize(hSpec)
        setMeasuredDimension(b, h)
        for (i in 0 until childCount) getChildAt(i).measure(
            MeasureSpec.makeMeasureSpec((b * 100 / zoom).coerceAtLeast(1), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec((h * 100 / zoom).coerceAtLeast(1), MeasureSpec.EXACTLY)
        )
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val f = zoom / 100f
        for (i in 0 until childCount) getChildAt(i).apply {
            layout(0, 0, measuredWidth, measuredHeight)
            pivotX = 0f
            pivotY = 0f
            scaleX = f
            scaleY = f
        }
    }

    private var x0 = 0f
    private var y0 = 0f
    private var abgefangen = false
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val lang = Runnable {
        abgefangen = true
        raster.widgetRahmen().entries.firstOrNull { it.value === this }?.key?.let { raster.beginneZiehen(it, this) }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                x0 = ev.x; y0 = ev.y; abgefangen = false
                postDelayed(lang, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE -> if (abs(ev.x - x0) > slop || abs(ev.y - y0) > slop) removeCallbacks(lang)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> removeCallbacks(lang)
        }
        return abgefangen
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) removeCallbacks(lang)
        return abgefangen || super.onTouchEvent(ev)
    }
}

/**
 * Platzhalter für ein Element, dessen App auf diesem Gerät fehlt: gestrichelte Fläche mit Namen.
 * Antippen bietet die Installation an; verschieben und entfernen geht wie bei allen Elementen.
 */
class PlatzhalterAnsicht(context: Context, private var e: Element) : View(context) {
    private val flaeche = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33FFFFFF }
    private val rand = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xB3FFFFFF.toInt(); style = Paint.Style.STROKE
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(14f.vp, 10f.vp), 0f)
    }
    private val schrift = android.text.TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textAlign = Paint.Align.CENTER
        setShadowLayer(6f, 0f, 2f, 0xE6000000.toInt())
    }
    private val plus = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xE6FFFFFF.toInt(); textAlign = Paint.Align.CENTER }
    private val feld = RectF()
    private var mitText = true

    init {
        isClickable = true
        isLongClickable = true
        isHapticFeedbackEnabled = false
    }

    fun setze(e: Element, textPx: Float, typeface: android.graphics.Typeface, mitText: Boolean) {
        this.e = e
        this.mitText = mitText
        schrift.textSize = textPx
        schrift.typeface = typeface
        contentDescription = name() + " (nicht installiert)"
        invalidate()
    }

    private fun name(): String = when (val x = e) {
        is Element.App -> x.titel ?: AppNamen.raten(x.paket)
        is Element.Widget -> AppNamen.raten(x.provider.substringBefore('/')) + "-Widget"
    }

    override fun onDraw(canvas: Canvas) {
        val textH = if (mitText) schrift.textSize * 1.6f else 0f
        val g = if (e is Element.App) minOf(width.toFloat(), height - textH) * 0.6f else 0f
        rand.strokeWidth = maxOf(2f, 3f.vp)
        if (e is Element.App) {
            val oben = (height - g - textH) / 2f
            feld.set((width - g) / 2f, oben, (width + g) / 2f, oben + g)
            canvas.drawRoundRect(feld, g * 0.22f, g * 0.22f, flaeche)
            canvas.drawRoundRect(feld, g * 0.22f, g * 0.22f, rand)
            plus.textSize = g * 0.6f
            canvas.drawText("+", feld.centerX(), feld.centerY() + plus.textSize * 0.35f, plus)
            if (mitText) {
                val t = android.text.TextUtils.ellipsize(name(), schrift, width * 0.96f, android.text.TextUtils.TruncateAt.END).toString()
                canvas.drawText(t, width / 2f, oben + g + schrift.textSize * 1.25f, schrift)
            }
        } else {
            val r = minOf(width, height) * 0.08f
            feld.set(rand.strokeWidth, rand.strokeWidth, width - rand.strokeWidth, height - rand.strokeWidth)
            canvas.drawRoundRect(feld, r, r, flaeche)
            canvas.drawRoundRect(feld, r, r, rand)
            val t = android.text.TextUtils.ellipsize(name(), schrift, width * 0.9f, android.text.TextUtils.TruncateAt.END).toString()
            canvas.drawText(t, width / 2f, height / 2f - schrift.textSize * 0.2f, schrift)
            val zeile2 = android.text.TextUtils.ellipsize("nicht installiert – antippen", schrift, width * 0.9f, android.text.TextUtils.TruncateAt.END).toString()
            canvas.drawText(zeile2, width / 2f, height / 2f + schrift.textSize * 1.1f, schrift)
        }
    }
}

/** Lesbare Namen für Apps, die (noch) nicht installiert sind – bekannte zuerst, sonst aus dem Paketnamen. */
object AppNamen {
    private val BEKANNT = mapOf(
        "de.oejendorferdamm.dammboard" to "DammBoard", "de.oejendorferdamm.dammzeit" to "DammZeit",
        "de.oejendorferdamm.dammtable" to "DammTable", "org.mozilla.firefox" to "Firefox",
        "com.spotify.music" to "Spotify", "com.solocode.anton" to "Anton", "com.seewo.easifinder" to "EasiFinder",
        "com.android.chrome" to "Chrome", "com.google.android.youtube" to "YouTube"
    )

    fun raten(paket: String): String = BEKANNT[paket] ?: paket.split('.').lastOrNull { it !in setOf("android", "app", "mobile", "music") }
        ?.replaceFirstChar { it.uppercase() } ?: paket
}
