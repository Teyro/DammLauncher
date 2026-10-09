/*
 * DammLauncher – Copyright (C) 2026 Teyro – GNU GPL v3 oder neuer, siehe LICENSE
 */
package de.oejendorferdamm.dammlauncher.ui

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.LinearLayout
import de.oejendorferdamm.dammlauncher.apps.AppInfo
import de.oejendorferdamm.dammlauncher.apps.AppKatalog
import java.text.Normalizer
import kotlin.math.abs

/**
 * Alle Apps alphabetisch mit Suchfeld oben – wie bei Android. Schließen: nach unten wischen (wenn
 * die Liste oben steht), Zurück-Taste oder der große Knopf. Langer Druck auf eine App → Menü
 * (zum Startbildschirm hinzufügen). [auswahl] ≠ null: Auswahlmodus (z. B. „Symbol einer anderen App“).
 */
class AppUebersicht(
    context: Context,
    private val starten: (AppInfo, View) -> Unit,
    private val langerDruck: (AppInfo) -> Unit,
    private val schliessen: () -> Unit,
    titel: String = "Alle Apps",
    private val auswahl: ((AppInfo) -> Unit)? = null,
    zusatzKnopf: View? = null
) : FrameLayout(context) {
    private val suche = eingabe(context, "Suchen …")
    private val raster = GridView(context)
    private var liste: List<AppInfo> = AppKatalog.apps
    private var y0 = 0f
    private var x0 = 0f

    private val adapter = object : BaseAdapter() {
        override fun getCount() = liste.size
        override fun getItem(i: Int) = liste[i]
        override fun getItemId(i: Int) = i.toLong()
        override fun getView(i: Int, alt: View?, eltern: ViewGroup): View {
            val z = (alt as? AppZelle) ?: AppZelle(context).apply {
                dunkel = true
                isClickable = false // Klicks übernimmt das GridView
                isLongClickable = false
                layoutParams = AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 200.vp)
            }
            val a = liste[i]
            z.setze(a.komponente, a.name, null, 62, 22.vp.toFloat(), true)
            return z
        }
    }

    init {
        setBackgroundColor(0xF5F4F6F9.toInt())
        isClickable = true
        val spalte = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(50.vp, 30.vp, 50.vp, 0) }
        spalte.addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(text(context, titel, 40, fett = true), lp(rechts = 30.vp))
            addView(suche, lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f, rechts = 20.vp))
            zusatzKnopf?.let { addView(it, lp(rechts = 20.vp)) }
            addView(knopf(context, "✕  Schließen", haupt = true) { schliessen() }, lp())
        })
        raster.apply {
            numColumns = ((resources.displayMetrics.widthPixels - 100.vp) / 210.vp).coerceIn(3, 12)
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            verticalSpacing = 10.vp
            isFastScrollEnabled = true
            selector = runde(0x221E6FD9, 20f.vp)
            this.adapter = this@AppUebersicht.adapter
            setOnItemClickListener { _, v, i, _ ->
                val a = liste[i]
                if (auswahl != null) auswahl.invoke(a) else starten(a, v)
            }
            setOnItemLongClickListener { _, _, i, _ ->
                if (auswahl == null) langerDruck(liste[i])
                true
            }
        }
        spalte.addView(raster, lp(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f, oben = 20.vp))
        addView(spalte, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        suche.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) = filtern()
        })
    }

    private fun normal(t: String) = Normalizer.normalize(t.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")

    fun filtern() {
        val f = normal(suche.text.toString().trim())
        liste = if (f.isEmpty()) AppKatalog.apps else AppKatalog.apps.filter { normal(it.name).contains(f) }
        adapter.notifyDataSetChanged()
    }

    /** Paketliste hat sich geändert. */
    fun neuLaden() = filtern()

    fun tastaturWeg() {
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.hideSoftInputFromWindow(suche.windowToken, 0)
    }

    // Nach unten wischen schließt – aber nur, wenn die Liste ganz oben steht
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> { y0 = ev.y; x0 = ev.x }
            MotionEvent.ACTION_MOVE -> {
                val dy = ev.y - y0
                if (dy > 160f.vp && dy > 2 * abs(ev.x - x0) && !raster.canScrollVertically(-1) && ev.y > 0) {
                    tastaturWeg()
                    schliessen()
                    return true
                }
            }
        }
        return false
    }
}
