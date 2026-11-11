/*
 * DammLauncher – Copyright (C) 2026 Teyro – GNU GPL v3 oder neuer, siehe LICENSE
 */
package de.oejendorferdamm.dammlauncher.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView

/** Farben der Bedienung – dieselben wie in DammZeit (alle Schul-Apps sehen gleich aus). */
object Farben {
    const val AKZENT = 0xFFE01E3C.toInt()
    const val AKZENT_DUNKEL = 0xFFB3182F.toInt()
    const val TEXT = 0xFF1F2124.toInt()
    const val TEXT2 = 0xFF6B7078.toInt()
    const val FLAECHE = 0xFFECEEF1.toInt()
    const val KARTE = 0xFFFFFFFF.toInt()
    const val LINIE = 0xFFE1E4E8.toInt()
    const val PANEL = 0xF7ECEEF1.toInt()
    const val ROT = 0xFFDC2626.toInt()
    const val GRUEN = 0xFF16A34A.toInt()
}

fun runde(farbe: Int, radius: Float, rand: Int = 0, randFarbe: Int = 0) = GradientDrawable().apply {
    setColor(farbe)
    cornerRadius = radius
    if (rand > 0) setStroke(rand, randFarbe)
}

/** Text in Vorbild-Pixeln. */
fun TextView.groesse(vorbild: Int) = setTextSize(TypedValue.COMPLEX_UNIT_PX, vorbild.vp.toFloat())

fun text(c: Context, inhalt: String, groesse: Int = 26, farbe: Int = Farben.TEXT, fett: Boolean = false) = TextView(c).apply {
    text = inhalt
    groesse(groesse)
    setTextColor(farbe)
    if (fett) typeface = Typeface.DEFAULT_BOLD
}

/** Großer, gut treffbarer Knopf (mindestens 72 Vorbild-Pixel hoch). */
fun knopf(c: Context, beschriftung: String, haupt: Boolean = false, gefahr: Boolean = false, klick: () -> Unit) = TextView(c).apply {
    text = beschriftung
    groesse(26)
    gravity = Gravity.CENTER
    typeface = Typeface.DEFAULT_BOLD
    // Im Clevertouch-Aussehen nimmt der Hauptknopf die Farbe des Hintergrunds an
    val akzent = if (Thema.clevertouch) Thema.akzent else Farben.AKZENT
    val grund = when {
        gefahr -> Farben.ROT
        haupt -> akzent
        else -> Color.WHITE
    }
    val gedrueckt = when {
        gefahr -> 0xFFB91C1C.toInt()
        haupt -> if (Thema.clevertouch) dunkler(akzent) else Farben.AKZENT_DUNKEL
        else -> 0xFFE5E7EB.toInt()
    }
    // Pille wie in DammZeit: weiß mit leichtem Schatten, Hauptknopf farbig, beim Drücken federt sie
    background = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), runde(gedrueckt, 40f.vp))
        addState(intArrayOf(-android.R.attr.state_enabled), runde(Farben.FLAECHE, 40f.vp, 2.vp, Farben.LINIE))
        addState(intArrayOf(), runde(grund, 40f.vp))
    }
    elevation = Leistung.hoehe(3f.vp)
    feder(this)
    setTextColor(android.content.res.ColorStateList(
        arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
        intArrayOf(0xFFA1A7B0.toInt(), if (haupt || gefahr) Color.WHITE else Farben.TEXT)
    ))
    minHeight = 76.vp
    minWidth = 76.vp
    setPadding(26.vp, 10.vp, 26.vp, 10.vp)
    isClickable = true
    setOnClickListener { klick() }
}

/** Knopf gibt beim Drücken nach (wie in DammZeit) – nicht im Sparmodus. */
fun feder(v: View) {
    if (Leistung.sparsam) return
    fun anim(ziel: Float) = android.animation.AnimatorSet().apply {
        playTogether(
            android.animation.ObjectAnimator.ofFloat(v, View.SCALE_X, ziel),
            android.animation.ObjectAnimator.ofFloat(v, View.SCALE_Y, ziel)
        )
        duration = if (ziel < 1f) 80 else 220
        interpolator = if (ziel < 1f) android.view.animation.DecelerateInterpolator() else android.view.animation.OvershootInterpolator(3f)
    }
    v.stateListAnimator = android.animation.StateListAnimator().apply {
        addState(intArrayOf(android.R.attr.state_pressed), anim(0.93f))
        addState(intArrayOf(), anim(1f))
    }
}

/** Auswahl-Chip wie in DammZeit: gewählt dunkel mit weißer Schrift, sonst hellgrau. */
fun chip(c: Context, beschriftung: String, gewaehlt: Boolean, klick: () -> Unit) = TextView(c).apply {
    text = beschriftung
    groesse(24)
    gravity = Gravity.CENTER
    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    setTextColor(if (gewaehlt) Color.WHITE else Farben.TEXT)
    background = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), runde(if (gewaehlt) Farben.TEXT else 0xFFD3D7DD.toInt(), 40f.vp))
        addState(intArrayOf(), runde(if (gewaehlt) Farben.TEXT else Farben.LINIE, 40f.vp))
    }
    minHeight = 68.vp
    setPadding(30.vp, 10.vp, 30.vp, 10.vp)
    isClickable = true
    feder(this)
    setOnClickListener { klick() }
}

/** Dieselbe Farbe, 20 % dunkler (gedrückter Knopf). */
fun dunkler(farbe: Int): Int {
    val hsv = FloatArray(3)
    Color.colorToHSV(farbe, hsv)
    hsv[2] *= 0.8f
    return Color.HSVToColor(Color.alpha(farbe), hsv)
}

/** Halbdurchsichtige Pille für den Startbildschirm (liest sich auf jedem Hintergrund). */
fun glasKnopf(c: Context, beschriftung: String, klick: () -> Unit) = TextView(c).apply {
    text = beschriftung
    contentDescription = beschriftung
    groesse(26)
    gravity = Gravity.CENTER
    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    setTextColor(Color.WHITE)
    background = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), runde(0x99000000.toInt(), 50f.vp, 2.vp, 0xB3FFFFFF.toInt()))
        addState(intArrayOf(), runde(0x59000000, 50f.vp, 2.vp, 0x66FFFFFF))
    }
    setPadding(40.vp, 18.vp, 46.vp, 18.vp)
    isClickable = true
    setOnClickListener { klick() }
}

/** 3 × 3 Punkte – das Zeichen für „alle Apps“, ohne Bilddatei. */
class PunktRaster(private val g: Int, farbe: Int) : android.graphics.drawable.Drawable() {
    private val malen = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = farbe }
    override fun getIntrinsicWidth() = g
    override fun getIntrinsicHeight() = g
    override fun draw(canvas: android.graphics.Canvas) {
        val b = bounds
        val r = b.width() / 9f
        for (i in 0..2) for (j in 0..2) canvas.drawCircle(b.left + r * (1.5f + 3 * i), b.top + r * (1.5f + 3 * j), r, malen)
    }
    override fun setAlpha(alpha: Int) { malen.alpha = alpha }
    override fun setColorFilter(cf: android.graphics.ColorFilter?) { malen.colorFilter = cf }
    @Deprecated("Drawable")
    override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
}

/**
 * Knöpfe nebeneinander, die bei Platzmangel in die nächste Zeile umbrechen (schmale Einstellungen
 * links am Rand) – statt abgeschnitten zu werden.
 */
class Fluss(c: Context, private val abstand: Int = 14.vp) : ViewGroup(c) {
    override fun onMeasure(wSpec: Int, hSpec: Int) {
        val breite = MeasureSpec.getSize(wSpec).let { if (MeasureSpec.getMode(wSpec) == MeasureSpec.UNSPECIFIED) Int.MAX_VALUE else it }
        var x = 0
        var y = 0
        var zeile = 0
        var max = 0
        for (i in 0 until childCount) {
            val k = getChildAt(i)
            if (k.visibility == View.GONE) continue
            val p = k.layoutParams
            k.measure(
                if (p != null && p.width >= 0) MeasureSpec.makeMeasureSpec(p.width, MeasureSpec.EXACTLY) else MeasureSpec.makeMeasureSpec(breite.coerceAtMost(100_000), MeasureSpec.AT_MOST),
                if (p != null && p.height >= 0) MeasureSpec.makeMeasureSpec(p.height, MeasureSpec.EXACTLY) else MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            )
            if (x > 0 && x + k.measuredWidth > breite) { x = 0; y += zeile + abstand; zeile = 0 }
            x += k.measuredWidth + abstand
            max = maxOf(max, x - abstand)
            zeile = maxOf(zeile, k.measuredHeight)
        }
        setMeasuredDimension(if (breite == Int.MAX_VALUE) max else MeasureSpec.getSize(wSpec), y + zeile)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val breite = r - l
        var x = 0
        var y = 0
        var zeile = 0
        for (i in 0 until childCount) {
            val k = getChildAt(i)
            if (k.visibility == View.GONE) continue
            if (x > 0 && x + k.measuredWidth > breite) { x = 0; y += zeile + abstand; zeile = 0 }
            k.layout(x, y, x + k.measuredWidth, y + k.measuredHeight)
            x += k.measuredWidth + abstand
            zeile = maxOf(zeile, k.measuredHeight)
        }
    }
}

fun lp(b: Int = ViewGroup.LayoutParams.WRAP_CONTENT, h: Int = ViewGroup.LayoutParams.WRAP_CONTENT, gewicht: Float = 0f, oben: Int = 0, rechts: Int = 0) =
    LinearLayout.LayoutParams(b, h, gewicht).apply { topMargin = oben; rightMargin = rechts }

fun zeile(c: Context, vararg kinder: View, abstand: Int = 14) = LinearLayout(c).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
    kinder.forEach { addView(it, lp(rechts = abstand.vp)) }
}

/** Zahl einstellen: Name, großer „−“-Knopf, breiter Schieberegler, großer „+“-Knopf, Wert. */
fun regler(c: Context, name: String, bereich: IntRange, wert: Int, schritt: Int = 1, einheit: String = "", aendern: (Int) -> Unit): LinearLayout {
    var aktuell = wert.coerceIn(bereich)
    val anzeige = text(c, "", 28, fett = true).apply { minWidth = 110.vp; gravity = Gravity.END }
    val schieber = SeekBar(c).apply {
        max = (bereich.last - bereich.first) / schritt
        progress = (aktuell - bereich.first) / schritt
        minimumHeight = 70.vp
        setPadding(36.vp, 0, 36.vp, 0)
        // dicker Daumen, gut zu greifen (weißer Rand wie in DammZeit)
        thumb = runde(if (Thema.clevertouch) Thema.akzent else Farben.AKZENT, 30f.vp, 5.vp, Color.WHITE).apply { setSize(60.vp, 60.vp) }
        progressDrawable = android.graphics.drawable.LayerDrawable(
            arrayOf(
                runde(Farben.LINIE, 8f.vp),
                android.graphics.drawable.ClipDrawable(runde(if (Thema.clevertouch) Thema.akzent else Farben.AKZENT, 8f.vp), Gravity.START, android.graphics.drawable.ClipDrawable.HORIZONTAL)
            )
        ).apply {
            setId(0, android.R.id.background); setId(1, android.R.id.progress)
            setLayerHeight(0, 16.vp); setLayerHeight(1, 16.vp)
            setLayerGravity(0, Gravity.CENTER_VERTICAL); setLayerGravity(1, Gravity.CENTER_VERTICAL)
        }
    }
    fun setze(v: Int, vomSchieber: Boolean) {
        val n = v.coerceIn(bereich)
        anzeige.text = "$n$einheit"
        if (!vomSchieber) schieber.progress = (n - bereich.first) / schritt
        if (n != aktuell) {
            aktuell = n
            aendern(n)
        }
    }
    anzeige.text = "$aktuell$einheit"
    schieber.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(s: SeekBar, p: Int, vomNutzer: Boolean) { if (vomNutzer) setze(bereich.first + p * schritt, true) }
        override fun onStartTrackingTouch(s: SeekBar) {}
        override fun onStopTrackingTouch(s: SeekBar) {}
    })
    return LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        addView(text(c, name, 24, Farben.TEXT2))
        addView(LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(knopf(c, "−") { setze(aktuell - schritt, false) }.apply { background = rundKnopf() }, lp(86.vp, 86.vp))
            addView(schieber, lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(knopf(c, "+") { setze(aktuell + schritt, false) }.apply { background = rundKnopf() }, lp(86.vp, 86.vp))
            addView(anzeige, lp())
        }, lp(ViewGroup.LayoutParams.MATCH_PARENT))
        setPadding(0, 10.vp, 0, 10.vp)
    }
}

/** Ein/Aus mit großer Fläche: ganze Zeile antippbar, rechts ein deutlicher Schalter. */
fun schalter(c: Context, name: String, an: Boolean, aktiv: Boolean = true, aendern: (Boolean) -> Unit): LinearLayout {
    var zustand = an
    // Schalter wie in DammZeit: Spur rot (an) bzw. hellgrau (aus), weißer runder Knopf
    val marke = View(c).apply { minimumWidth = 112.vp; minimumHeight = 64.vp }
    fun zeige() {
        val spur = runde(if (!aktiv) Farben.LINIE else if (zustand) Farben.AKZENT else 0xFFC4C9D0.toInt(), 32f.vp)
        val knopf = runde(if (!aktiv) 0xFFF3F4F6.toInt() else Color.WHITE, 26f.vp, 1.vp, 0x22000000)
        marke.background = android.graphics.drawable.LayerDrawable(arrayOf(spur, knopf)).apply {
            if (zustand) setLayerInset(1, 54.vp, 6.vp, 6.vp, 6.vp) else setLayerInset(1, 6.vp, 6.vp, 54.vp, 6.vp)
        }
        marke.contentDescription = if (zustand) "an" else "aus"
    }
    zeige()
    return LinearLayout(c).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, 12.vp, 0, 12.vp)
        addView(text(c, name, 26), lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(marke, lp(112.vp, 64.vp))
        isClickable = aktiv
        isEnabled = aktiv
        setOnClickListener {
            zustand = !zustand
            zeige()
            aendern(zustand)
        }
    }
}

fun eingabe(c: Context, hinweis: String, wert: String = "", geheim: Boolean = false, zahl: Boolean = false) = EditText(c).apply {
    hint = hinweis
    setText(wert)
    groesse(28)
    setSingleLine(true)
    setTextColor(Farben.TEXT)
    setHintTextColor(0xFF9AA3AE.toInt())
    background = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_focused), runde(Color.WHITE, 18f.vp, 3.vp, Farben.AKZENT))
        addState(intArrayOf(), runde(Color.WHITE, 18f.vp, 2.vp, 0xFFD1D5DB.toInt()))
    }
    setPadding(20.vp, 16.vp, 20.vp, 16.vp)
    inputType = when {
        zahl && geheim -> InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        geheim -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        zahl -> InputType.TYPE_CLASS_NUMBER
        else -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
    }
    imeOptions = android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI or android.view.inputmethod.EditorInfo.IME_FLAG_NO_FULLSCREEN
}

/**
 * Großes Fenster in der Mitte über einem abgedunkelten Hintergrund (eigene Ansicht statt
 * AlertDialog – der wäre auf 75 Zoll winzig). [inhalt] bekommt die Spalte zum Füllen.
 */
class GrossFenster(private val wurzel: FrameLayout, titel: String, breite: Int = 1000, private val beimSchliessen: () -> Unit = {}) {
    val c: Context = wurzel.context
    val spalte = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
    private val decke = FrameLayout(c).apply {
        setBackgroundColor(0xA6000000.toInt())
        isClickable = true // nichts darunter antippen
        setOnClickListener { schliessen() }
    }

    private lateinit var kasten: LinearLayout

    init {
        kasten = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            background = runde(Farben.FLAECHE, 36f.vp)
            elevation = 30f.vp
            setPadding(44.vp, 36.vp, 44.vp, 38.vp)
            isClickable = true
            addView(LinearLayout(c).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(text(c, titel, 36, fett = true), lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(knopf(c, "✕") { schliessen() }.apply { background = rundKnopf() }, lp(86.vp, 86.vp))
            })
            addView(ScrollView(c).apply { addView(spalte); isVerticalScrollBarEnabled = true }, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, oben = 16.vp))
        }
        val m = c.resources.displayMetrics
        decke.addView(kasten, FrameLayout.LayoutParams(minOf(breite.vp, (m.widthPixels * 0.92f).toInt()), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER).apply {
            topMargin = 30.vp; bottomMargin = 30.vp + Masse.navUnten
        })
        wurzel.addView(decke, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        // Einblenden nur mit eingeschalteten Animationen – ältere Boards ruckeln sonst
        if (Leistung.animationen) {
            decke.alpha = 0f
            decke.animate().alpha(1f).setDuration(120).start()
            kasten.scaleX = 0.96f; kasten.scaleY = 0.96f
            kasten.animate().scaleX(1f).scaleY(1f).setDuration(160).start()
        }
    }

    fun add(v: View, oben: Int = 14) {
        spalte.addView(v, lp(ViewGroup.LayoutParams.MATCH_PARENT, oben = oben.vp))
        // Mit Eingabefeld: oben hinsetzen, damit die Bildschirmtastatur die Knöpfe nicht verdeckt
        if (v is EditText) (kasten.layoutParams as? FrameLayout.LayoutParams)?.let {
            it.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            kasten.layoutParams = it
        }
    }

    fun knoepfe(vararg k: View) = add(LinearLayout(c).apply {
        gravity = Gravity.END
        k.forEach { addView(it, lp(rechts = 14.vp)) }
    }, 26)

    var offen = true
        private set

    fun schliessen() {
        if (!offen) return
        offen = false
        // Tastatur mit dem Fenster zumachen – sonst verdeckt sie den Startbildschirm
        (c.getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager)?.hideSoftInputFromWindow(decke.windowToken, 0)
        (decke.parent as? ViewGroup)?.removeView(decke)
        beimSchliessen()
    }
}

/** Runder weißer Symbolknopf (✕, ‹, ◀, ▶) wie in DammZeit. */
fun rundKnopf() = StateListDrawable().apply {
    addState(intArrayOf(android.R.attr.state_pressed), runde(Farben.LINIE, 60f.vp))
    addState(intArrayOf(), runde(Color.WHITE, 60f.vp, if (Leistung.sparsam) 2.vp else 0, Farben.LINIE))
}

/** Weiße Karte mit runden Ecken – fasst einen Abschnitt der Einstellungen zusammen. */
fun karte(c: Context) = LinearLayout(c).apply {
    orientation = LinearLayout.VERTICAL
    background = runde(Farben.KARTE, 26f.vp)
    elevation = Leistung.hoehe(2f.vp)
    setPadding(30.vp, 22.vp, 30.vp, 24.vp)
}

/** Öffnet F-Droid mit der Frage „Paketquelle hinzufügen?“ (alle Damm-Apps) – ohne F-Droid die Anleitungsseite mit QR-Code. */
fun oeffneFDroidRepo(c: Context): Boolean {
    val repo = "teyro.github.io/damm-fdroid/repo?fingerprint=db7f158b6eb2c7eb17e79aa4a9ae0e9487c3c2228fe7de60b37a1979408c0604"
    for (adresse in listOf("fdroidrepos://$repo", "https://teyro.github.io/damm-fdroid/")) {
        try {
            c.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(adresse)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            return true
        } catch (_: android.content.ActivityNotFoundException) {}
    }
    return false
}
