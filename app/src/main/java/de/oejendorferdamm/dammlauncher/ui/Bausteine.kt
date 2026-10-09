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

/** Farben der Bedienung. */
object Farben {
    const val AKZENT = 0xFF1E6FD9.toInt()
    const val TEXT = 0xFF1F2328.toInt()
    const val TEXT2 = 0xFF5B6470.toInt()
    const val FLAECHE = 0xFFF4F6F9.toInt()
    const val PANEL = 0xF2FFFFFF.toInt()
    const val ROT = 0xFFC62828.toInt()
    const val GRUEN = 0xFF2E9E4F.toInt()
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
    val grund = when {
        gefahr -> Farben.ROT
        haupt -> Farben.AKZENT
        else -> Color.WHITE
    }
    setTextColor(if (haupt || gefahr) Color.WHITE else Farben.TEXT)
    background = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), runde(0xFFB8C4D2.toInt(), 18f.vp))
        addState(intArrayOf(-android.R.attr.state_enabled), runde(0xFFE3E7EC.toInt(), 18f.vp))
        addState(intArrayOf(), runde(grund, 18f.vp, if (haupt || gefahr) 0 else 2.vp, 0xFFC9D1DB.toInt()))
    }
    minHeight = 76.vp
    minWidth = 76.vp
    setPadding(26.vp, 10.vp, 26.vp, 10.vp)
    isClickable = true
    setOnClickListener { klick() }
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
        // dicker Daumen, gut zu greifen
        thumb = runde(Farben.AKZENT, 30f.vp).apply { setSize(60.vp, 60.vp) }
        progressDrawable = android.graphics.drawable.LayerDrawable(
            arrayOf(
                runde(0xFFD5DCE4.toInt(), 8f.vp),
                android.graphics.drawable.ClipDrawable(runde(Farben.AKZENT, 8f.vp), Gravity.START, android.graphics.drawable.ClipDrawable.HORIZONTAL)
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
            addView(knopf(c, "−") { setze(aktuell - schritt, false) }, lp(86.vp, 86.vp))
            addView(schieber, lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(knopf(c, "+") { setze(aktuell + schritt, false) }, lp(86.vp, 86.vp))
            addView(anzeige, lp())
        }, lp(ViewGroup.LayoutParams.MATCH_PARENT))
        setPadding(0, 10.vp, 0, 10.vp)
    }
}

/** Ein/Aus mit großer Fläche: ganze Zeile antippbar, rechts ein deutlicher Schalter. */
fun schalter(c: Context, name: String, an: Boolean, aktiv: Boolean = true, aendern: (Boolean) -> Unit): LinearLayout {
    var zustand = an
    val marke = TextView(c).apply {
        groesse(24); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
        minWidth = 130.vp; minHeight = 64.vp
    }
    fun zeige() {
        marke.text = if (zustand) "AN" else "AUS"
        marke.setTextColor(Color.WHITE)
        marke.background = runde(if (!aktiv) 0xFFB0B6BE.toInt() else if (zustand) Farben.GRUEN else 0xFF8A939E.toInt(), 32f.vp)
    }
    zeige()
    return LinearLayout(c).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, 12.vp, 0, 12.vp)
        addView(text(c, name, 26), lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(marke, lp())
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
    background = runde(Color.WHITE, 14f.vp, 2.vp, 0xFFB8C2CE.toInt())
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
        setBackgroundColor(0x99000000.toInt())
        isClickable = true // nichts darunter antippen
        setOnClickListener { schliessen() }
    }

    init {
        val kasten = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            background = runde(Farben.FLAECHE, 30f.vp)
            elevation = 24f.vp
            setPadding(40.vp, 34.vp, 40.vp, 34.vp)
            isClickable = true
            addView(LinearLayout(c).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(text(c, titel, 36, fett = true), lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(knopf(c, "✕") { schliessen() }, lp(86.vp, 86.vp))
            })
            addView(ScrollView(c).apply { addView(spalte); isVerticalScrollBarEnabled = true }, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, oben = 16.vp))
        }
        val m = c.resources.displayMetrics
        decke.addView(kasten, FrameLayout.LayoutParams(minOf(breite.vp, (m.widthPixels * 0.92f).toInt()), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER).apply {
            topMargin = 30.vp; bottomMargin = 30.vp
        })
        wurzel.addView(decke, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    fun add(v: View, oben: Int = 14) = spalte.addView(v, lp(ViewGroup.LayoutParams.MATCH_PARENT, oben = oben.vp))

    fun knoepfe(vararg k: View) = add(LinearLayout(c).apply {
        gravity = Gravity.END
        k.forEach { addView(it, lp(rechts = 14.vp)) }
    }, 26)

    var offen = true
        private set

    fun schliessen() {
        if (!offen) return
        offen = false
        (decke.parent as? ViewGroup)?.removeView(decke)
        beimSchliessen()
    }
}
