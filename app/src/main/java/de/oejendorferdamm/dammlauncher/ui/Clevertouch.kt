/*
 * DammLauncher – Copyright (C) 2026 Teyro – GNU GPL v3 oder neuer, siehe LICENSE
 */
package de.oejendorferdamm.dammlauncher.ui

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import de.oejendorferdamm.dammlauncher.apps.AppKatalog
import de.oejendorferdamm.dammlauncher.konfig.Element
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Glas-Pille mit Inhalt (Suchleiste, Knöpfe in Kopfzeile und Dock). */
private fun glasPille(c: Context, inhalt: View, radius: () -> Float): FrameLayout = FrameLayout(c).apply {
    addView(GlasFlaeche(c, radius), FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    addView(inhalt, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
}

/**
 * Kopfzeile im Clevertouch-Aussehen: links Uhrzeit und Datum, in der Mitte die Suchleiste (öffnet
 * die App-Übersicht mit Tastatur), rechts die Schnelleinstellungen. Aktualisiert sich nur zum
 * Minutenwechsel (Systemtakt).
 */
class Kopfzeile(context: Context, suchen: () -> Unit, schnell: () -> Unit) : LinearLayout(context) {
    private val uhr = TextView(context).apply {
        groesse(46)
        setTextColor(Color.WHITE)
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        setShadowLayer(8f, 0f, 2f, 0x99000000.toInt())
    }
    private val datum = TextView(context).apply {
        groesse(22)
        setTextColor(0xE6FFFFFF.toInt())
        setShadowLayer(6f, 0f, 2f, 0x99000000.toInt())
    }
    private val takt = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = zeit()
    }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(40.vp, 14.vp, 40.vp, 0)
        addView(LinearLayout(context).apply {
            orientation = VERTICAL
            addView(uhr)
            addView(datum)
        }, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val such = TextView(context).apply {
            text = "🔍   Apps durchsuchen"
            groesse(26)
            setTextColor(0xF2FFFFFF.toInt())
            gravity = Gravity.CENTER_VERTICAL
            setPadding(40.vp, 0, 40.vp, 0)
        }
        addView(glasPille(context, such) { 40f.vp }.apply {
            isClickable = true
            contentDescription = "Apps durchsuchen"
            setOnClickListener { suchen() }
        }, LayoutParams(720.vp, 80.vp))
        val rechts = LinearLayout(context).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL }
        val sk = TextView(context).apply {
            text = "⚡  Schnell"
            groesse(24)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(30.vp, 0, 30.vp, 0)
        }
        rechts.addView(glasPille(context, sk) { 40f.vp }.apply {
            isClickable = true
            contentDescription = "Schnelleinstellungen"
            setOnClickListener { schnell() }
        }, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, 80.vp))
        addView(rechts, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        zeit()
    }

    fun zeit() {
        val jetzt = Date()
        uhr.text = SimpleDateFormat("HH:mm", Locale.GERMAN).format(jetzt)
        datum.text = SimpleDateFormat("EEEE, d. MMMM", Locale.GERMAN).format(jetzt)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        context.registerReceiver(takt, IntentFilter().apply { addAction(Intent.ACTION_TIME_TICK); addAction(Intent.ACTION_TIME_CHANGED) })
        zeit()
    }

    override fun onDetachedFromWindow() {
        try { context.unregisterReceiver(takt) } catch (_: Exception) {}
        super.onDetachedFromWindow()
    }

    companion object {
        /** Höhe in Vorbild-Pixeln (das Raster rückt so weit nach unten). */
        const val HOEHE = 120
    }
}

/**
 * Dock unten wie bei Android 15: Milchglas-Leiste mit Lieblings-Apps ohne Beschriftung und am Ende
 * „Alle Apps“. Langer Druck auf eine App → [menue].
 */
class Dock(context: Context, private val starten: (Element.App, View) -> Unit, private val menue: (Element.App) -> Unit, private val alle: () -> Unit) : FrameLayout(context) {
    private val reihe = LinearLayout(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(22.vp, 0, 22.vp, 0)
    }
    private var apps: List<Element.App> = emptyList()

    init {
        addView(GlasFlaeche(context) { 44f.vp }, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        addView(reihe, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER))
    }

    fun setze(liste: List<Element.App>) {
        val sichtbar = liste.filter { AppKatalog.istDa(it.paket, it.aktivitaet) }
        if (sichtbar == apps && reihe.childCount > 0) {
            for (i in 0 until reihe.childCount) reihe.getChildAt(i).invalidate()
            return
        }
        apps = sichtbar
        reihe.removeAllViews()
        for (a in sichtbar) {
            reihe.addView(AppZelle(context).apply {
                setze(ComponentName(a.paket, a.aktivitaet), a.titel ?: AppKatalog.finde(a.paket, a.aktivitaet)?.name ?: a.paket, a.icon, 82, 0f, false)
                setOnClickListener { v -> starten(a, v) }
                setOnLongClickListener { menue(a); true }
            }, LinearLayout.LayoutParams(118.vp, 118.vp).apply { rightMargin = 10.vp })
        }
        if (sichtbar.isNotEmpty()) reihe.addView(View(context).apply { setBackgroundColor(0x40FFFFFF) }, LinearLayout.LayoutParams(2.vp, 70.vp).apply { rightMargin = 22.vp; leftMargin = 10.vp })
        reihe.addView(TextView(context).apply {
            contentDescription = "Alle Apps"
            background = runde(0x26FFFFFF, 59f.vp)
            setCompoundDrawablesRelativeWithIntrinsicBounds(null, PunktRaster(38.vp, Color.WHITE), null, null)
            setPadding(0, 40.vp, 0, 0)
            gravity = Gravity.CENTER_HORIZONTAL
            isClickable = true
            setOnClickListener { alle() }
        }, LinearLayout.LayoutParams(118.vp, 118.vp))
    }

    companion object {
        /** Höhe in Vorbild-Pixeln samt Abstand nach unten. */
        const val HOEHE = 170
    }
}
