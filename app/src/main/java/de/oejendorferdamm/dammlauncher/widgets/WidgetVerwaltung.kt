/*
 * DammLauncher – Copyright (C) 2026 Teyro – GNU GPL v3 oder neuer, siehe LICENSE
 */
package de.oejendorferdamm.dammlauncher.widgets

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.graphics.Canvas
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.RemoteViews
import android.widget.TextView
import de.oejendorferdamm.dammlauncher.konfig.Element
import de.oejendorferdamm.dammlauncher.konfig.Geraet
import de.oejendorferdamm.dammlauncher.konfig.Konfig

/**
 * Widgets fremder Apps. Ein fehlerhaftes Widget darf den Launcher nie mitreißen: Erzeugen,
 * Aktualisieren, Messen und Zeichnen sind abgesichert, im Fehlerfall erscheint ein Hinweis.
 */
class WidgetVerwaltung(context: Context) {
    private val kontext = context.applicationContext
    val manager: AppWidgetManager = AppWidgetManager.getInstance(kontext)
    val host = SichererHost(kontext, HOST_ID)

    fun starten() = try { host.startListening() } catch (e: Exception) { Log.w("DammLauncher", "Widgets: ${e.message}") }
    fun stoppen() = try { host.stopListening() } catch (_: Exception) {}

    fun info(id: Int): AppWidgetProviderInfo? = try { manager.getAppWidgetInfo(id) } catch (_: Exception) { null }

    /** Alle Widget-Anbieter auf diesem Gerät (für die Auswahl), nach Name sortiert. */
    fun anbieter(): List<AppWidgetProviderInfo> = try {
        manager.installedProviders.sortedBy { it.loadLabel(kontext.packageManager)?.lowercase() ?: "" }
    } catch (_: Exception) {
        emptyList()
    }

    fun anbieterDa(provider: String): Boolean {
        val c = ComponentName.unflattenFromString(provider) ?: return false
        return anbieter().any { it.provider == c }
    }

    fun neueId(): Int = host.allocateAppWidgetId()
    fun loesche(id: Int) {
        try { if (id != 0) host.deleteAppWidgetId(id) } catch (_: Exception) {}
    }

    /** Binden ohne Rückfrage – klappt nur, wenn „Immer erlauben“ schon einmal bestätigt wurde. */
    fun bindeStill(id: Int, provider: ComponentName): Boolean = try {
        manager.bindAppWidgetIdIfAllowed(id, provider)
    } catch (_: Exception) {
        false
    }

    /**
     * Widgets der Konfiguration mit diesem Gerät abgleichen: fehlende Ids anlegen und still binden,
     * Ids entfernter Widgets löschen. Gibt die Widgets zurück, die noch eine Freigabe brauchen.
     */
    fun abgleichen(k: Konfig): List<Element.Widget> {
        val gewollt = (k.elemente + k.nichtPlatziert).filterIsInstance<Element.Widget>()
        val kennungen = gewollt.map { it.kennung }.toSet()
        // Verwaiste Ids: entfernte Widgets und Ids, die zu keiner Kennung mehr gehören
        Geraet.alleWidgetKennungen().forEach { (kennung, id) ->
            if (kennung !in kennungen) {
                loesche(id); Geraet.setzeWidgetId(kennung, 0)
            }
        }
        val bekannt = Geraet.alleWidgetKennungen().values.toSet()
        try {
            host.appWidgetIds.filter { it !in bekannt }.forEach { loesche(it) }
        } catch (_: Exception) {
        }
        val offen = ArrayList<Element.Widget>()
        for (w in gewollt) {
            val provider = ComponentName.unflattenFromString(w.provider) ?: continue
            if (!anbieter().any { it.provider == provider }) continue // App fehlt: Zelle bleibt leer
            val id = Geraet.widgetId(w.kennung)
            val inf = if (id != 0) info(id) else null
            if (inf != null && inf.provider == provider) continue
            if (id != 0) loesche(id)
            val neu = neueId()
            if (bindeStill(neu, provider)) Geraet.setzeWidgetId(w.kennung, neu) else {
                loesche(neu)
                Geraet.setzeWidgetId(w.kennung, 0)
                offen.add(w)
            }
        }
        return offen
    }

    /** Ansicht für ein Widget; null, wenn es (noch) keine gebundene Id gibt. */
    fun ansicht(activity: Activity, w: Element.Widget): AppWidgetHostView? {
        val id = Geraet.widgetId(w.kennung)
        if (id == 0) return null
        val inf = info(id) ?: return null
        return try {
            host.createView(activity, id, inf).apply { setPadding(0, 0, 0, 0) }
        } catch (e: Exception) {
            Log.w("DammLauncher", "Widget ${w.provider}: ${e.message}")
            null
        }
    }

    /** Dem Widget seine Größe in dp mitteilen (wichtig für Widgets, die sich anpassen). */
    fun groesseMelden(v: AppWidgetHostView, breitePx: Int, hoehePx: Int) {
        val d = kontext.resources.displayMetrics.density
        val b = (breitePx / d).toInt()
        val h = (hoehePx / d).toInt()
        try {
            v.updateAppWidgetSize(Bundle(), b, h, b, h)
        } catch (_: Exception) {
        }
    }

    companion object {
        const val HOST_ID = 1958
    }
}

/** Host, der nur abgesicherte Widget-Ansichten erzeugt. */
class SichererHost(context: Context, id: Int) : AppWidgetHost(context, id) {
    override fun onCreateView(context: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo?): AppWidgetHostView = SichereWidgetAnsicht(context)
}

/** Fängt Fehler fremder Widgets ab und zeigt stattdessen einen Hinweis. */
class SichereWidgetAnsicht(context: Context) : AppWidgetHostView(context) {
    private var kaputt = false

    private fun fehler(e: Throwable) {
        Log.w("DammLauncher", "Widget-Fehler: ${e.message}")
        if (kaputt) return
        kaputt = true
        post {
            try {
                removeAllViews()
                addView(TextView(context).apply {
                    text = "Widget nicht verfügbar"
                    gravity = Gravity.CENTER
                    setTextColor(0xFFFFFFFF.toInt())
                    setBackgroundColor(0x66000000)
                })
            } catch (_: Throwable) {
            }
        }
    }

    override fun updateAppWidget(remoteViews: RemoteViews?) {
        try {
            kaputt = false
            super.updateAppWidget(remoteViews)
        } catch (e: Throwable) {
            fehler(e)
        }
    }

    override fun onMeasure(w: Int, h: Int) {
        try {
            super.onMeasure(w, h)
        } catch (e: Throwable) {
            setMeasuredDimension(MeasureSpec.getSize(w), MeasureSpec.getSize(h))
            fehler(e)
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        try {
            super.onLayout(changed, l, t, r, b)
        } catch (e: Throwable) {
            fehler(e)
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        try {
            super.dispatchDraw(canvas)
        } catch (e: Throwable) {
            fehler(e)
        }
    }
}
