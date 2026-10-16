/*
 * DammLauncher – Startbildschirm für interaktive Displays
 * Copyright (C) 2026 Teyro
 *
 * Dieses Programm ist freie Software: Sie können es unter den Bedingungen der GNU General Public
 * License, wie von der Free Software Foundation veröffentlicht, weitergeben und/oder verändern,
 * entweder gemäß Version 3 der Lizenz oder (nach Ihrer Wahl) jeder späteren Version.
 * Es wird OHNE JEDE GEWÄHRLEISTUNG bereitgestellt. Siehe LICENSE.
 */
package de.oejendorferdamm.dammlauncher

import android.app.Activity
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import de.oejendorferdamm.dammlauncher.abgleich.Abgleich
import de.oejendorferdamm.dammlauncher.apps.AppInfo
import de.oejendorferdamm.dammlauncher.apps.AppKatalog
import de.oejendorferdamm.dammlauncher.apps.EigeneSymbole
import de.oejendorferdamm.dammlauncher.hintergrund.FotoDesTages
import de.oejendorferdamm.dammlauncher.gesten.RandGesten
import de.oejendorferdamm.dammlauncher.konfig.Datei
import de.oejendorferdamm.dammlauncher.konfig.Element
import de.oejendorferdamm.dammlauncher.konfig.Geraet
import de.oejendorferdamm.dammlauncher.konfig.Konfig
import de.oejendorferdamm.dammlauncher.konfig.KonfigJson
import de.oejendorferdamm.dammlauncher.konfig.KonfigSpeicher
import de.oejendorferdamm.dammlauncher.raster.Belegung
import de.oejendorferdamm.dammlauncher.raster.RasterRechner
import de.oejendorferdamm.dammlauncher.sicherheit.Passwort
import de.oejendorferdamm.dammlauncher.ui.AppUebersicht
import de.oejendorferdamm.dammlauncher.ui.EinstellungenPanel
import de.oejendorferdamm.dammlauncher.ui.Farben
import de.oejendorferdamm.dammlauncher.ui.GrossFenster
import de.oejendorferdamm.dammlauncher.ui.Masse
import de.oejendorferdamm.dammlauncher.ui.PanelAktionen
import de.oejendorferdamm.dammlauncher.ui.RasterAktionen
import de.oejendorferdamm.dammlauncher.ui.RasterAnsicht
import de.oejendorferdamm.dammlauncher.ui.UhrAnsicht
import de.oejendorferdamm.dammlauncher.ui.AppNamen
import de.oejendorferdamm.dammlauncher.ui.Schriften
import de.oejendorferdamm.dammlauncher.konfig.Gesten
import de.oejendorferdamm.dammlauncher.konfig.Schrift
import de.oejendorferdamm.dammlauncher.konfig.UhrStil
import de.oejendorferdamm.dammlauncher.ui.WidgetRahmen
import de.oejendorferdamm.dammlauncher.ui.regler
import de.oejendorferdamm.dammlauncher.konfig.Grenzen
import de.oejendorferdamm.dammlauncher.ui.PunktRaster
import de.oejendorferdamm.dammlauncher.ui.eingabe
import de.oejendorferdamm.dammlauncher.ui.glasKnopf
import de.oejendorferdamm.dammlauncher.ui.groesse
import de.oejendorferdamm.dammlauncher.ui.knopf
import de.oejendorferdamm.dammlauncher.ui.lp
import de.oejendorferdamm.dammlauncher.ui.runde
import de.oejendorferdamm.dammlauncher.ui.text
import de.oejendorferdamm.dammlauncher.ui.vp
import de.oejendorferdamm.dammlauncher.update.Update
import de.oejendorferdamm.dammlauncher.widgets.WidgetVerwaltung
import java.util.UUID
import java.util.concurrent.Executors

/** Der Startbildschirm – die einzige von außen erreichbare Komponente. */
class HomeActivity : Activity(), RasterAktionen, PanelAktionen {
    private val haupt = Handler(Looper.getMainLooper())
    private val arbeit = Executors.newSingleThreadExecutor()
    private lateinit var wurzel: FrameLayout
    private lateinit var raster: RasterAnsicht
    private lateinit var alleApps: TextView
    private lateinit var fotoHinweis: TextView
    private var vorne = false
    private lateinit var widgets: WidgetVerwaltung
    private var uebersicht: AppUebersicht? = null
    private var panel: EinstellungenPanel? = null
    private val fenster = ArrayList<GrossFenster>()
    private var sperre: View? = null
    private var meldungAnsicht: TextView? = null

    /** Sicherer Start nach mehreren Abstürzen: Sicherung laden, keine Widgets. */
    private var sicher = false
    private var bossSeit = 0L
    private var updateInfo: Update.Ergebnis? = null
    private var updateStatus = ""
    private var hintergrundStand = -1L
    private var widgetFreigabeGefragt = false

    // Laufende Abläufe mit fremden Activities
    private class WartendesWidget(val id: Int, val provider: ComponentName, val kennung: String, val element: Element.Widget?)
    private var wartend: WartendesWidget? = null
    private var iconFuer: Element.App? = null
    private var bearbeitung: Bearbeitung? = null

    private class Bearbeitung(val app: Element.App, var name: String, var icon: String?, val vorschau: ImageView, val fenster: GrossFenster)

    private var gestenStand: Gesten? = null
    private val konfigBeobachter: (Konfig) -> Unit = { k ->
        zeigen()
        if (k.gesten != gestenStand) { gestenStand = k.gesten; RandGesten.abgleichen(this, neu = true) }
    }
    private val appBeobachter: (String?) -> Unit = { entfernt -> paketeGeaendert(entfernt) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Geraet.init(this)
        absturzSchutz()
        sicher = Geraet.absturzSchleife()
        if (sicher) Geraet.vergissAbstuerze()
        KonfigSpeicher.init(this, sicher)
        Masse.aktualisiere(this)
        resources.displayMetrics.let { m ->
            Geraet.bildBreite = maxOf(m.widthPixels, m.heightPixels)
            Geraet.bildHoehe = minOf(m.widthPixels, m.heightPixels)
        }
        AppKatalog.init(this)
        widgets = WidgetVerwaltung(this)

        wurzel = FrameLayout(this)
        raster = RasterAnsicht(this, this)
        wurzel.addView(raster, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        alleApps = glasKnopf(this, "Alle Apps") { oeffneUebersicht() }.apply {
            setCompoundDrawablesRelativeWithIntrinsicBounds(PunktRaster(30.vp, Color.WHITE), null, null, null)
            compoundDrawablePadding = 18.vp
        }
        wurzel.addView(alleApps, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = 18.vp })
        // Bildnachweis: klein und zurückhaltend (auf 85 Zoll sonst viel zu groß)
        fotoHinweis = text(this, "", 11, 0xCCFFFFFF.toInt()).apply {
            background = runde(0x33000000, 20f.vp)
            setPadding(12.vp, 4.vp, 12.vp, 4.vp)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            maxWidth = (resources.displayMetrics.widthPixels * 0.3f).toInt()
            visibility = View.GONE
            setOnClickListener { fotoSeiteOeffnen() }
        }
        wurzel.addView(fotoHinweis, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.END).apply { bottomMargin = 10.vp; rightMargin = 12.vp })
        setContentView(wurzel)

        KonfigSpeicher.beobachte(konfigBeobachter)
        AppKatalog.beobachte(appBeobachter)
        if (!sicher) widgetsAbgleichen()
        zeigen()

        if (sicher) {
            meldung("DammLauncher wurde nach mehreren Abstürzen sicher gestartet (letzte funktionierende Einrichtung, ohne Widgets).", lang = true)
        }
        // Läuft er 30 Sekunden stabil, ist die Absturzschleife vorbei
        haupt.postDelayed({ Geraet.vergissAbstuerze() }, 30_000)
        Abgleich.planen(this)
        TagesJob.planen(this)
        hintergrundPruefungen()
    }

    /** Abstürze merken: mehrere in kurzer Zeit → beim nächsten Start sicherer Modus (keine Absturzschleife). */
    private fun absturzSchutz() {
        val vorher = Thread.getDefaultUncaughtExceptionHandler()
        if (vorher is Merker) return
        Thread.setDefaultUncaughtExceptionHandler(Merker(vorher))
    }

    private class Merker(val vorher: Thread.UncaughtExceptionHandler?) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(t: Thread, e: Throwable) {
            try { Geraet.merkeAbsturz() } catch (_: Throwable) {}
            vorher?.uncaughtException(t, e)
        }
    }

    override fun onStart() {
        super.onStart()
        if (!sicher) widgets.starten()
    }

    override fun onStop() {
        super.onStop()
        widgets.stoppen()
    }

    override fun onResume() {
        super.onResume()
        // Boss-Modus endet nach einer Stunde von selbst
        if (bossSeit > 0 && System.currentTimeMillis() - bossSeit > 60 * 60_000L) {
            bossSeit = 0
            panel?.aufbauen()
        }
        vorne = true
        RandGesten.vorne(true)
        RandGesten.abgleichen(this)
        if (Geraet.veraltetGemeldet || System.currentTimeMillis() - Geraet.letzterUpdateCheck > 24 * 60 * 60_000L) updatePruefenStill()
        else bereitesUpdateInstallieren()
        if (FotoDesTages.faellig()) unsplashLaden(false)
    }

    override fun onPause() {
        vorne = false
        RandGesten.vorne(false)
        super.onPause()
    }

    override fun onDestroy() {
        KonfigSpeicher.vergiss(konfigBeobachter)
        AppKatalog.vergiss(appBeobachter)
        super.onDestroy()
    }

    /** Home-Taste, während der Launcher schon vorne ist: alles schließen. */
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        raster.abbrechen()
        fenster.toList().forEach { it.schliessen() }
        uebersichtZu()
        panelZu()
        // Randgeste aus einer anderen App: „Alle Apps“ gleich öffnen
        if (intent?.getStringExtra(EXTRA_GESTE) == Gesten.APPS) haupt.post { oeffneUebersicht() }
    }

    @Deprecated("Zurück-Taste")
    override fun onBackPressed() {
        when {
            sperre != null -> Unit // gesperrt: nur das Update hilft
            fenster.isNotEmpty() -> fenster.last().schliessen()
            uebersicht != null -> uebersichtZu()
            panel != null -> panelZu()
            else -> raster.abbrechen() // ein Launcher beendet sich nicht
        }
    }

    // ------------------------------------------------------------ Anzeigen

    private fun zeigen() {
        val k = KonfigSpeicher.konfig
        val anbieter = if (sicher) emptySet() else widgets.anbieterNamen()
        val (sichtbar, _) = KonfigJson.aufloesen(k, AppKatalog::istDa) { it == Element.UHR || it == Element.UHR_TRANSPARENT || it in anbieter }
        // Was fehlt (App nicht installiert), erscheint als Platzhalter – im sicheren Start fehlen Widgets absichtlich
        val da = sichtbar.map { it.schluessel }.toSet()
        val fehlt = k.elemente.filter { it.schluessel !in da && !(sicher && it is Element.Widget) }.map { it.schluessel }.toSet()
        val elemente = k.elemente.filter { it.schluessel in da || it.schluessel in fehlt }
        raster.setze(k, elemente, fehlt) { w ->
            if (w.istUhr) UhrAnsicht(this, w.provider == Element.UHR_TRANSPARENT, w.uhr) else if (sicher) null else widgets.ansicht(this, w)
        }
        alleApps.visibility = if (k.alleAppsKnopf) View.VISIBLE else View.GONE
        hintergrund(k)
        // Widgets ihre Größe mitteilen, sobald sie liegen
        raster.post {
            raster.widgetRahmen().values.forEach { rahmen ->
                val r = rahmen as? WidgetRahmen ?: return@forEach
                (r.getChildAt(0) as? AppWidgetHostView)?.let { widgets.groesseMelden(it, r.innenBreite, r.innenHoehe) }
            }
        }
    }

    /** Unsplash-Foto vor eigenem Bild vor Farbe; über Fotos oben und unten ein leichter Schatten (Lesbarkeit). */
    private fun hintergrund(k: Konfig) {
        val foto = FotoDesTages.datei()
        val mitFoto = k.hintergrund.unsplash && foto.exists()
        val datei = if (mitFoto) foto else KonfigSpeicher.hintergrundDatei
        fotoHinweis.visibility = if (mitFoto && Geraet.fotoNachweis.isNotEmpty()) View.VISIBLE else View.GONE
        fotoHinweis.text = "Foto: ${Geraet.fotoNachweis}"
        if (mitFoto || (k.hintergrund.bild && datei.exists())) {
            val stand = datei.lastModified() + datei.hashCode()
            if (stand == hintergrundStand) return
            hintergrundStand = stand
            arbeit.execute {
                val b = try { BitmapFactory.decodeFile(datei.absolutePath) } catch (_: Throwable) { null }
                haupt.post {
                    wurzel.background = if (b == null) ColorDrawable(k.hintergrund.farbe) else LayerDrawable(arrayOf(
                        BitmapDrawable(resources, b),
                        GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x59000000, 0x00000000, 0x00000000, 0x73000000))
                    ))
                }
            }
        } else {
            hintergrundStand = -1
            wurzel.background = ColorDrawable(k.hintergrund.farbe)
        }
    }

    private fun fotoSeiteOeffnen() {
        val s = Geraet.unsplashSeite
        val seite = when {
            s.startsWith("https://unsplash.com/") -> "$s?utm_source=DammLauncher&utm_medium=referral"
            s.startsWith("https://commons.wikimedia.org/") -> s
            else -> return
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(seite)))
        } catch (_: Exception) {
            meldung("Kein Browser vorhanden.")
        }
    }

    private var unsplashLaeuft = false

    private fun unsplashLaden(vonHand: Boolean) {
        if (unsplashLaeuft) return
        unsplashLaeuft = true
        if (vonHand) meldung("Holt ein neues Foto …")
        arbeit.execute {
            val t = FotoDesTages.laden(this, vonHand)
            haupt.post {
                unsplashLaeuft = false
                zeigen()
                panel?.aufbauen()
                if (vonHand) meldung(t, lang = t.startsWith("Fehler"))
            }
        }
    }

    override fun unsplashNeu() = unsplashLaden(true)

    /** Großer Hinweis unten (ein Toast wäre auf 75 Zoll kaum zu sehen). */
    override fun meldung(text: String) = meldung(text, false)

    private fun meldung(text: String, lang: Boolean) {
        meldungAnsicht?.let { wurzel.removeView(it) }
        val t = text(this, text, 28, Color.WHITE).apply {
            background = runde(0xEE1F2328.toInt(), 40f.vp)
            setPadding(44.vp, 24.vp, 44.vp, 24.vp)
            gravity = Gravity.CENTER
            maxWidth = (resources.displayMetrics.widthPixels * 0.8f).toInt()
            elevation = 40f.vp
            setOnClickListener { wurzel.removeView(this) }
        }
        meldungAnsicht = t
        wurzel.addView(t, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = 130.vp })
        haupt.postDelayed({ if (meldungAnsicht === t) { wurzel.removeView(t); meldungAnsicht = null } }, if (lang) 12_000 else 5_000)
    }

    private fun neuesFenster(titel: String, breite: Int = 1000): GrossFenster {
        lateinit var f: GrossFenster
        f = GrossFenster(wurzel, titel, breite) { fenster.remove(f) }
        fenster.add(f)
        sperre?.bringToFront()
        return f
    }

    // ------------------------------------------------------------ Gesten vom Raster

    override fun starte(app: Element.App, v: View) {
        val r = Rect()
        v.getGlobalVisibleRect(r)
        if (!AppKatalog.starte(ComponentName(app.paket, app.aktivitaet), r)) meldung("Die App lässt sich nicht starten.")
    }

    override fun langerDruckLeer() {
        val pin = KonfigSpeicher.konfig.pin
        if (pin == null || bossAktiv()) oeffnePanel() else frageGeheim("Einstellungen", "PIN eingeben", zahl = true) { eingabe, f ->
            when (Passwort.pruefe("pin", eingabe, pin)) {
                true -> { f.schliessen(); oeffnePanel() }
                false -> meldung("Falsche PIN.")
                null -> meldung("Zu viele Fehlversuche – gesperrt für ${Passwort.gesperrtNoch("pin")} s.")
            }
        }
    }

    override fun nachOben() = geste(KonfigSpeicher.konfig.gesten.hoch)

    override fun nachUnten() = geste(KonfigSpeicher.konfig.gesten.runter)

    private fun geste(aktion: String) {
        when (aktion) {
            Gesten.APPS -> oeffneUebersicht()
            Gesten.EINSTELLUNGEN -> langerDruckLeer()
        }
    }

    /** Platzhalter angetippt: App im App-Store (Play Store, F-Droid …) anbieten. */
    override fun fehlt(e: Element) {
        val paket = when (e) {
            is Element.App -> e.paket
            is Element.Widget -> e.provider.substringBefore('/')
        }
        val name = AppNamen.raten(paket)
        val f = neuesFenster("$name fehlt", 900)
        f.add(text(this, "„$name“ ist auf diesem Board nicht installiert. Sobald die App da ist, erscheint sie hier von selbst.", 24, Farben.TEXT2))
        f.knoepfe(
            knopf(this, "Schließen") { f.schliessen() },
            knopf(this, "Im App-Store suchen", haupt = true) {
                f.schliessen()
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$paket")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: Exception) {
                    meldung("Auf diesem Board gibt es keinen App-Store.")
                }
            }
        )
    }

    override fun gesperrt() = KonfigSpeicher.konfig.bearbeitenGesperrt

    override fun verschiebe(e: Element, spalte: Int, zeile: Int) = KonfigSpeicher.aendere { k ->
        k.copy(elemente = k.elemente.map { if (it.schluessel == e.schluessel) it.verschoben(spalte, zeile) else it })
    }

    override fun entferne(e: Element) {
        KonfigSpeicher.aendere { k -> k.copy(elemente = k.elemente.filter { it.schluessel != e.schluessel }) }
        if (e is Element.Widget) {
            widgets.loesche(Geraet.widgetId(e.kennung))
            Geraet.setzeWidgetId(e.kennung, 0)
        }
        EigeneSymbole.aufraeumen()
        meldung("Vom Startbildschirm entfernt.")
    }

    override fun aendereGroesse(w: Element.Widget, spalte: Int, zeile: Int, breite: Int, hoehe: Int) = KonfigSpeicher.aendere { k ->
        k.copy(elemente = k.elemente.map { if (it.schluessel == w.schluessel) it.verschoben(spalte, zeile).mitGroesse(breite, hoehe) else it })
    }

    /** Langer Druck und loslassen ohne Bewegung: großes Menü. */
    override fun menue(e: Element) {
        when (e) {
            is Element.App -> {
                val name = e.titel ?: AppKatalog.finde(e.paket, e.aktivitaet)?.name ?: e.paket
                val f = neuesFenster(name, 900)
                f.add(text(this, "Zum Verschieben: lange drücken und auf eine freie Zelle ziehen.", 24, Farben.TEXT2))
                f.add(knopf(this, "Name und Symbol ändern …", haupt = true) { f.schliessen(); bearbeiten(e) })
                f.add(knopf(this, "App-Info") { f.schliessen(); AppKatalog.zeigeAppInfo(ComponentName(e.paket, e.aktivitaet)) })
                f.add(knopf(this, "Vom Startbildschirm entfernen", gefahr = true) { f.schliessen(); entferne(e) })
            }
            is Element.Widget -> {
                val f = neuesFenster(if (e.istUhr) "Uhr" else "Widget", 900)
                f.add(knopf(this, "Größe ändern", haupt = true) { f.schliessen(); raster.beginneGroesse(e) })
                if (!e.istUhr) f.add(knopf(this, "Inhalt vergrößern …") { f.schliessen(); zoomEinstellen(e) })
                if (e.istUhr) f.add(knopf(this, "Uhr einstellen …") { f.schliessen(); uhrEinstellen(e) })
                val id = Geraet.widgetId(e.kennung)
                if (id != 0 && widgets.info(id)?.configure != null) f.add(knopf(this, "Widget einrichten …") { f.schliessen(); konfigurieren(id, null) })
                f.add(knopf(this, "Vom Startbildschirm entfernen", gefahr = true) { f.schliessen(); entferne(e) })
            }
        }
    }

    /** Aussehen der Uhr: Fläche, Schrift, Datum – jede Wahl wirkt sofort. */
    private fun uhrEinstellen(e: Element.Widget) {
        val f = neuesFenster("Uhr einstellen", 1300)
        fun aktuell() = KonfigSpeicher.konfig.elemente.firstOrNull { it.schluessel == e.schluessel } as? Element.Widget ?: e
        fun aendere(neu: (Element.Widget) -> Element.Widget) = KonfigSpeicher.aendere { k ->
            k.copy(elemente = k.elemente.map { if (it.schluessel == e.schluessel) neu(it as Element.Widget) else it })
        }
        val zeilen = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        f.add(zeilen, 0)
        fun aufbauen() {
            zeilen.removeAllViews()
            val u = aktuell()
            fun reihe(titel: String, vararg k: View) {
                zeilen.addView(text(this, titel, 22, Farben.TEXT2), lp(oben = 18.vp))
                zeilen.addView(android.widget.HorizontalScrollView(this).apply {
                    isHorizontalScrollBarEnabled = false
                    addView(LinearLayout(context).apply { k.forEach { addView(it, lp(rechts = 12.vp)) } })
                }, lp(ViewGroup.LayoutParams.MATCH_PARENT, oben = 8.vp))
            }
            fun wahl(t: String, an: Boolean, klick: () -> Unit) = knopf(this, t, haupt = an) { klick(); aufbauen() }
            reihe("Aussehen",
                wahl("Weiße Fläche", !u.istUhr || u.provider == Element.UHR) { aendere { it.copy(provider = Element.UHR) } },
                wahl("Transparent", u.provider == Element.UHR_TRANSPARENT) { aendere { it.copy(provider = Element.UHR_TRANSPARENT) } })
            reihe("Schrift", *Schrift.ALLE.map { s ->
                wahl(Schriften.name(s), u.uhr.schrift == s) { aendere { it.copy(uhr = it.uhr.copy(schrift = s)) } }.apply { typeface = Schriften.von(context, s, fettFallsSystem = true) }
            }.toTypedArray())
            reihe("Datum",
                wahl("Unten", u.uhr.datum == UhrStil.UNTEN) { aendere { it.copy(uhr = it.uhr.copy(datum = UhrStil.UNTEN)) } },
                wahl("Oben", u.uhr.datum == UhrStil.OBEN) { aendere { it.copy(uhr = it.uhr.copy(datum = UhrStil.OBEN)) } },
                wahl("Ausblenden", u.uhr.datum == UhrStil.AUS) { aendere { it.copy(uhr = it.uhr.copy(datum = UhrStil.AUS)) } })
            if (u.uhr.datum != UhrStil.AUS) reihe("Datumsform",
                wahl("Freitag, 16. Oktober", u.uhr.format == UhrStil.LANG) { aendere { it.copy(uhr = it.uhr.copy(format = UhrStil.LANG)) } },
                wahl("16. Oktober 2026", u.uhr.format == UhrStil.OHNE_WOCHENTAG) { aendere { it.copy(uhr = it.uhr.copy(format = UhrStil.OHNE_WOCHENTAG)) } },
                wahl("16.10.2026", u.uhr.format == UhrStil.ZAHL) { aendere { it.copy(uhr = it.uhr.copy(format = UhrStil.ZAHL)) } },
                wahl("Freitag", u.uhr.format == UhrStil.WOCHENTAG) { aendere { it.copy(uhr = it.uhr.copy(format = UhrStil.WOCHENTAG)) } })
        }
        aufbauen()
        f.knoepfe(knopf(this, "Fertig", haupt = true) { f.schliessen() })
    }

    /** Inhalt eines Widgets vergrößern – wirkt sofort (Live-Vorschau hinter dem Fenster). */
    private fun zoomEinstellen(w: Element.Widget) {
        val f = neuesFenster("Inhalt vergrößern", 1000)
        f.add(text(this, "Viele Widgets (z. B. Suchleisten) haben eine feste Größe und wirken auf großen Boards winzig. " +
            "Hier wird der Inhalt im Feld hochskaliert.", 22, Farben.TEXT2))
        fun setze(z: Int) = KonfigSpeicher.aendere { k ->
            k.copy(elemente = k.elemente.map { if (it.schluessel == w.schluessel) (it as Element.Widget).copy(zoom = z) else it })
        }
        val aktuell = (KonfigSpeicher.konfig.elemente.firstOrNull { it.schluessel == w.schluessel } as? Element.Widget)?.zoom ?: w.zoom
        f.add(regler(this, "Vergrößerung", Grenzen.ZOOM, aktuell, 25, " %") { z -> setze(z) })
        f.knoepfe(
            knopf(this, "Automatisch anpassen") { f.schliessen(); setze(passenderZoom(w)) },
            knopf(this, "Fertig", haupt = true) { f.schliessen() }
        )
    }

    /** So weit vergrößern, dass das Widget in seiner Mindestgröße das Feld ausfüllt. */
    private fun passenderZoom(w: Element.Widget): Int {
        val r = raster.widgetRahmen()[w] ?: raster.widgetRahmen().entries.firstOrNull { it.key.schluessel == w.schluessel }?.value ?: return 100
        val info = widgets.info(Geraet.widgetId(w.kennung)) ?: return 100
        val minB = info.minWidth.coerceAtLeast(1)
        val minH = info.minHeight.coerceAtLeast(1)
        val z = minOf(r.width * 100 / minB, r.height * 100 / minH)
        return (z / 25 * 25).coerceIn(100, 300)
    }

    // ------------------------------------------------------------ Name und Symbol einer App ändern

    private fun bearbeiten(e: Element.App) {
        val komp = ComponentName(e.paket, e.aktivitaet)
        val original = AppKatalog.finde(e.paket, e.aktivitaet)?.name ?: e.paket
        val f = neuesFenster("Name und Symbol", 1100)
        val vorschau = ImageView(this).apply { background = runde(0x33000000, 24f.vp); setPadding(16.vp, 16.vp, 16.vp, 16.vp) }
        val name = eingabe(this, original, e.titel ?: "")
        val b = Bearbeitung(e, e.titel ?: "", e.icon, vorschau, f)
        bearbeitung = b
        f.add(LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(vorschau, lp(200.vp, 200.vp, rechts = 30.vp))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(text(context, "Name (leer = „$original“)", 24, Farben.TEXT2))
                addView(name, lp(ViewGroup.LayoutParams.MATCH_PARENT, oben = 8.vp))
            }, lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        })
        f.add(text(this, "Symbol:", 24, Farben.TEXT2), 24)
        f.add(LinearLayout(this).apply {
            addView(knopf(context, "Bild wählen (PNG, JPG …)") { iconFuer = e; bildAuswahl(REQ_ICON, "image/*") }, lp(rechts = 14.vp))
            addView(knopf(context, "Symbol einer anderen App …") { andereAppWaehlen() }, lp(rechts = 14.vp))
            addView(knopf(context, "Originalsymbol") { b.icon = null; vorschauZeigen(b, komp) }, lp())
        })
        f.knoepfe(
            knopf(this, "Abbrechen") { f.schliessen() },
            knopf(this, "Speichern", haupt = true) {
                val titel = name.text.toString().trim().take(60).ifEmpty { null }
                KonfigSpeicher.aendere { k ->
                    k.copy(elemente = k.elemente.map { if (it.schluessel == e.schluessel) (it as Element.App).copy(titel = titel, icon = b.icon) else it })
                }
                EigeneSymbole.aufraeumen()
                f.schliessen()
                bearbeitung = null
            }
        )
        vorschauZeigen(b, komp)
    }

    private fun vorschauZeigen(b: Bearbeitung, komp: ComponentName) {
        val bild = AppKatalog.icon(komp, b.icon, 184.vp) { fertig -> if (bearbeitung === b) b.vorschau.setImageBitmap(fertig) }
        if (bild != null) b.vorschau.setImageBitmap(bild)
    }

    private fun andereAppWaehlen() {
        val b = bearbeitung ?: return
        lateinit var auswahl: AppUebersicht
        auswahl = AppUebersicht(this, { _, _ -> }, {}, { wurzel.removeView(auswahl) }, titel = "Symbol von …", auswahl = { app ->
            wurzel.removeView(auswahl)
            arbeit.execute {
                val name = try { EigeneSymbole.ausApp(app.komponente) } catch (_: Throwable) { null }
                haupt.post {
                    if (name == null) meldung("Dieses Symbol lässt sich nicht übernehmen.") else {
                        b.icon = name
                        vorschauZeigen(b, ComponentName(b.app.paket, b.app.aktivitaet))
                    }
                }
            }
        })
        wurzel.addView(auswahl, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    // ------------------------------------------------------------ App-Übersicht

    private fun oeffneUebersicht() {
        if (uebersicht != null || sperre != null) return
        raster.abbrechen()
        val u = AppUebersicht(this,
            starten = { app, v -> uebersichtZu(); val r = Rect(); v.getGlobalVisibleRect(r); if (!AppKatalog.starte(app.komponente, r)) meldung("Die App lässt sich nicht starten.") },
            langerDruck = { app -> appMenue(app) },
            schliessen = { uebersichtZu() },
            zusatzKnopf = knopf(this, "Widgets …") { uebersichtZu(); widgetHinzufuegen() }
        )
        uebersicht = u
        wurzel.addView(u, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        if (KonfigSpeicher.konfig.animationen) {
            u.translationY = resources.displayMetrics.heightPixels.toFloat()
            u.animate().translationY(0f).setDuration(220).start()
        }
    }

    private fun uebersichtZu() {
        val u = uebersicht ?: return
        u.tastaturWeg()
        wurzel.removeView(u)
        uebersicht = null
    }

    private fun appMenue(app: AppInfo) {
        val f = neuesFenster(app.name, 900)
        f.add(knopf(this, "Zum Startbildschirm hinzufügen", haupt = true) {
            f.schliessen()
            if (gesperrt()) { meldung("Der Startbildschirm ist gegen Verändern gesperrt (Einstellungen)."); return@knopf }
            val k = KonfigSpeicher.konfig
            val neu = Element.App(app.paket, app.aktivitaet, 0, 0)
            if (k.elemente.any { it.schluessel == neu.schluessel }) { meldung("Ist schon auf dem Startbildschirm."); return@knopf }
            val platz = Belegung.ersteFreie(k.elemente, 1, 1, k.spalten, k.zeilen)
            if (platz == null) meldung("Kein freier Platz – Raster vergrößern oder etwas entfernen.") else {
                KonfigSpeicher.aendere { it.copy(elemente = it.elemente + neu.verschoben(platz.first, platz.second)) }
                uebersichtZu()
                meldung("„${app.name}“ liegt jetzt auf dem Startbildschirm.")
            }
        })
        f.add(knopf(this, "App-Info") { f.schliessen(); AppKatalog.zeigeAppInfo(app.komponente) })
    }

    private fun paketeGeaendert(entfernt: String?) {
        // Deinstallierte Apps und deren Widgets sauber vom Startbildschirm nehmen
        if (entfernt != null) KonfigSpeicher.aendere { k ->
            fun bleibt(e: Element) = when (e) {
                is Element.App -> e.paket != entfernt
                is Element.Widget -> !e.provider.startsWith("$entfernt/")
            }
            k.elemente.filterNot(::bleibt).filterIsInstance<Element.Widget>().forEach { w -> widgets.loesche(Geraet.widgetId(w.kennung)); Geraet.setzeWidgetId(w.kennung, 0) }
            k.copy(elemente = k.elemente.filter(::bleibt), nichtPlatziert = k.nichtPlatziert.filter(::bleibt))
        }
        uebersicht?.neuLaden()
        zeigen()
    }

    // ------------------------------------------------------------ Widgets

    override fun widgetHinzufuegen() {
        if (sicher) { meldung("Im sicheren Start sind Widgets aus."); return }
        if (gesperrt()) { meldung("Der Startbildschirm ist gegen Verändern gesperrt (Einstellungen)."); return }
        val f = neuesFenster("Widget hinzufügen", 1100)
        f.add(knopf(this, "🕒  Digitaluhr mit weißer Fläche (DammLauncher, 3×2)", haupt = true) { f.schliessen(); uhrAnlegen(Element.UHR) }.apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL }, 10)
        f.add(knopf(this, "🕒  Digitaluhr transparent (DammLauncher, 3×2)", haupt = true) { f.schliessen(); uhrAnlegen(Element.UHR_TRANSPARENT) }.apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL }, 10)
        val liste = widgets.anbieter()
        if (liste.isEmpty()) f.add(text(this, "Auf diesem Gerät gibt es keine Widgets.", 26))
        val k = KonfigSpeicher.konfig
        val r = RasterRechner.aus(k, raster.width.coerceAtLeast(1), raster.height.coerceAtLeast(1))
        for (info in liste) {
            val (b, h) = zellenFuer(info, r, k)
            val name = try { info.loadLabel(packageManager) } catch (_: Exception) { info.provider.className }
            val app = try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(info.provider.packageName, 0)) } catch (_: Exception) { "" }
            f.add(knopf(this, "$name  ($app, ${b}×$h)") { f.schliessen(); widgetAnlegen(info, b, h) }.apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL }, 10)
        }
    }

    private fun uhrAnlegen(art: String) {
        val k = KonfigSpeicher.konfig
        var b = 3
        var h = 2
        var platz = Belegung.ersteFreie(k.elemente, b, h, k.spalten, k.zeilen)
        while (platz == null && (b > 1 || h > 1)) {
            if (b >= h && b > 1) b-- else h--
            platz = Belegung.ersteFreie(k.elemente, b, h, k.spalten, k.zeilen)
        }
        if (platz == null) { meldung("Kein freier Platz für die Uhr."); return }
        val kennung = "uhr" + UUID.randomUUID().toString().replace("-", "").take(8)
        KonfigSpeicher.aendere { it.copy(elemente = it.elemente + Element.Widget(art, kennung, platz.first, platz.second, b, h)) }
    }

    /** Wie viele Zellen braucht das Widget mindestens? */
    private fun zellenFuer(info: AppWidgetProviderInfo, r: RasterRechner, k: Konfig): Pair<Int, Int> {
        val b = Math.ceil((info.minWidth / r.zellBreite).toDouble()).toInt().coerceIn(1, k.spalten)
        val h = Math.ceil((info.minHeight / r.zellHoehe).toDouble()).toInt().coerceIn(1, k.zeilen)
        return b to h
    }

    private fun widgetAnlegen(info: AppWidgetProviderInfo, b: Int, h: Int) {
        val k = KonfigSpeicher.konfig
        var bb = b
        var hh = h
        var platz = Belegung.ersteFreie(k.elemente, bb, hh, k.spalten, k.zeilen)
        while (platz == null && (bb > 1 || hh > 1)) {
            if (bb >= hh && bb > 1) bb-- else hh--
            platz = Belegung.ersteFreie(k.elemente, bb, hh, k.spalten, k.zeilen)
        }
        if (platz == null) { meldung("Kein freier Platz für das Widget."); return }
        val kennung = UUID.randomUUID().toString().replace("-", "").take(12)
        val element = Element.Widget(info.provider.flattenToString(), kennung, platz.first, platz.second, bb, hh)
        val id = widgets.neueId()
        wartend = WartendesWidget(id, info.provider, kennung, element)
        if (widgets.bindeStill(id, info.provider)) gebunden() else bindenFragen(id, info.provider)
    }

    private fun bindenFragen(id: Int, provider: ComponentName) {
        try {
            startActivityForResult(Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider), REQ_BIND)
        } catch (_: Exception) {
            widgets.loesche(id)
            wartend = null
            meldung("Dieses Gerät erlaubt keine Widgets für DammLauncher.")
        }
    }

    private fun gebunden() {
        val w = wartend ?: return
        val info = widgets.info(w.id)
        if (w.element != null && info?.configure != null) konfigurieren(w.id, w) else platzieren()
    }

    private fun konfigurieren(id: Int, w: WartendesWidget?) {
        try {
            widgets.host.startAppWidgetConfigureActivityForResult(this, id, 0, REQ_KONF, null)
        } catch (_: Exception) {
            // Kein Einrichten möglich: trotzdem ablegen (viele Widgets kommen ohne aus)
            if (w != null) platzieren()
        }
    }

    private fun platzieren() {
        val w = wartend ?: return
        wartend = null
        Geraet.setzeWidgetId(w.kennung, w.id)
        val e = w.element
        if (e != null) KonfigSpeicher.aendere { it.copy(elemente = it.elemente + e) } else {
            raster.vergiss("widget:${w.kennung}")
            zeigen()
        }
    }

    /** Widgets einer Vorlage auf diesem Gerät binden; fehlt die Freigabe, einmal nachfragen. */
    private fun widgetsAbgleichen() {
        val offen = widgets.abgleichen(KonfigSpeicher.konfig)
        offen.forEach { raster.vergiss(it.schluessel) }
        if (offen.isEmpty() || widgetFreigabeGefragt) return
        widgetFreigabeGefragt = true
        val f = neuesFenster("Widgets einrichten", 1000)
        f.add(text(this,
            "Für die Widgets der Vorlage braucht DammLauncher einmalig Ihre Erlaubnis.\n\n" +
                "Im nächsten Fenster bitte „Immer erlauben“ anhaken und „Erstellen“ tippen – danach richtet sich alles ohne Rückfragen ein.", 26))
        f.knoepfe(knopf(this, "Später") { f.schliessen() }, knopf(this, "Erlauben …", haupt = true) {
            f.schliessen()
            val w = offen.first()
            val provider = ComponentName.unflattenFromString(w.provider) ?: return@knopf
            val id = widgets.neueId()
            wartend = WartendesWidget(id, provider, w.kennung, null)
            bindenFragen(id, provider)
        })
    }

    override fun widgetEinrichten(w: Element.Widget) {
        widgetFreigabeGefragt = false
        val provider = ComponentName.unflattenFromString(w.provider) ?: return
        val id = widgets.neueId()
        wartend = WartendesWidget(id, provider, w.kennung, null)
        if (widgets.bindeStill(id, provider)) platzieren() else bindenFragen(id, provider)
    }

    // ------------------------------------------------------------ Ergebnisse fremder Activities

    @Deprecated("Activity-Ergebnis")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        when (requestCode) {
            REQ_BIND -> {
                val w = wartend
                if (resultCode == RESULT_OK && w != null) {
                    if (w.element == null) {
                        // Freigabe für Vorlagen-Widgets: dieses ablegen, die übrigen jetzt still binden
                        platzieren()
                        widgetFreigabeGefragt = true
                        widgetsAbgleichen()
                        zeigen()
                    } else gebunden()
                } else {
                    w?.let { widgets.loesche(it.id) }
                    wartend = null
                }
            }
            REQ_KONF -> {
                val w = wartend
                if (w != null) {
                    if (resultCode == RESULT_OK) platzieren() else { widgets.loesche(w.id); wartend = null }
                }
            }
            REQ_BILD -> if (resultCode == RESULT_OK && uri != null) {
                meldung("Bild wird vorbereitet …")
                val m = resources.displayMetrics
                arbeit.execute {
                    val fehler = try {
                        Datei.hintergrundSpeichern(this, uri, maxOf(m.widthPixels, m.heightPixels), minOf(m.widthPixels, m.heightPixels)); null
                    } catch (e: Throwable) { e.message ?: "Bild unbrauchbar" }
                    haupt.post {
                        if (fehler != null) meldung("Hintergrund: $fehler") else {
                            hintergrundStand = -1
                            KonfigSpeicher.aendere { it.copy(hintergrund = it.hintergrund.copy(bild = true, unsplash = false)) }
                            panel?.aufbauen()
                            zeigen()
                        }
                    }
                }
            }
            REQ_ICON -> if (resultCode == RESULT_OK && uri != null) {
                val b = bearbeitung ?: return
                arbeit.execute {
                    val name = try { EigeneSymbole.ausBild(this, uri) } catch (_: Throwable) { null }
                    haupt.post {
                        if (name == null) meldung("Das Bild lässt sich nicht als Symbol verwenden.") else {
                            b.icon = name
                            vorschauZeigen(b, ComponentName(b.app.paket, b.app.aktivitaet))
                        }
                    }
                }
            }
            REQ_EXPORT -> if (resultCode == RESULT_OK && uri != null) arbeit.execute {
                val t = try { Datei.exportieren(this, uri); "Einrichtung exportiert." } catch (e: Exception) { "Export fehlgeschlagen: ${e.message}" }
                haupt.post { meldung(t) }
            }
            REQ_IMPORT -> if (resultCode == RESULT_OK && uri != null) arbeit.execute {
                val ergebnis = try { Datei.importieren(this, uri) } catch (e: Exception) { haupt.post { meldung("Import fehlgeschlagen: ${e.message}") }; return@execute }
                haupt.post {
                    KonfigSpeicher.ersetze(ergebnis.konfig)
                    hintergrundStand = -1
                    widgetFreigabeGefragt = false
                    widgetsAbgleichen()
                    panel?.aufbauen()
                    meldung(if (ergebnis.warnungen.isEmpty()) "Einrichtung importiert." else "Importiert, ${ergebnis.warnungen.size} Werte korrigiert.")
                }
            }
        }
    }

    private fun bildAuswahl(code: Int, typ: String) {
        try {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(typ), code)
        } catch (_: Exception) {
            meldung("Auf diesem Gerät gibt es keine Dateiauswahl.")
        }
    }

    // ------------------------------------------------------------ Einstellungen

    private fun oeffnePanel() {
        if (panel != null) return
        raster.abbrechen()
        uebersichtZu()
        raster.hilfslinien = true
        panel = EinstellungenPanel(wurzel, this)
        sperre?.bringToFront()
    }

    override fun panelZu() {
        panel?.schliessen()
        panel = null
        raster.hilfslinien = false
    }

    override fun bildWaehlen() = bildAuswahl(REQ_BILD, "image/*")

    override fun exportieren() {
        try {
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/zip")
                .putExtra(Intent.EXTRA_TITLE, "DammLauncher-Einrichtung.zip"), REQ_EXPORT)
        } catch (_: Exception) {
            meldung("Auf diesem Gerät gibt es keine Dateiauswahl.")
        }
    }

    override fun importieren() = bildAuswahl(REQ_IMPORT, "*/*")

    override fun pinFestlegen() {
        val f = neuesFenster("PIN für die Einstellungen", 900)
        val a = eingabe(this, "Neue PIN (4–8 Ziffern)", geheim = true, zahl = true)
        val b = eingabe(this, "PIN wiederholen", geheim = true, zahl = true)
        f.add(a); f.add(b)
        f.knoepfe(knopf(this, "Abbrechen") { f.schliessen() }, knopf(this, "Festlegen", haupt = true) {
            val p = a.text.toString()
            when {
                !p.matches(Regex("\\d{4,8}")) -> meldung("Bitte 4 bis 8 Ziffern.")
                p != b.text.toString() -> meldung("Die beiden PINs sind verschieden.")
                else -> {
                    KonfigSpeicher.aendere { it.copy(pin = Passwort.erzeuge(p)) }
                    f.schliessen()
                    panel?.aufbauen()
                    meldung("PIN festgelegt.")
                }
            }
        })
    }

    /** Fenster mit Passwortfeld; [weiter] bekommt Eingabe und Fenster (rechnet im Hintergrund – PBKDF2 dauert). */
    private fun frageGeheim(titel: String, hinweis: String, zahl: Boolean = false, weiter: (String, GrossFenster) -> Unit) {
        val f = neuesFenster(titel, 800)
        val feld = eingabe(this, hinweis, geheim = true, zahl = zahl)
        f.add(feld)
        f.knoepfe(knopf(this, "Abbrechen") { f.schliessen() }, knopf(this, "OK", haupt = true) {
            val t = feld.text.toString()
            arbeit.execute { haupt.post { weiter(t, f) } }
        })
        feld.requestFocus()
    }

    override fun bossAktiv() = bossSeit > 0 && System.currentTimeMillis() - bossSeit < 60 * 60_000L

    override fun bossAnmelden() {
        val hash = KonfigSpeicher.konfig.boss
        if (hash == null) {
            val f = neuesFenster("Master-Passwort festlegen", 1000)
            f.add(text(this, "Mit dem Master-Passwort öffnet sich der Boss-Modus auf allen Geräten, die diese Einrichtung als Vorlage bekommen.", 24, Farben.TEXT2))
            val a = eingabe(this, "Master-Passwort (mindestens 8 Zeichen)", geheim = true)
            val b = eingabe(this, "Wiederholen", geheim = true)
            f.add(a); f.add(b)
            f.knoepfe(knopf(this, "Abbrechen") { f.schliessen() }, knopf(this, "Festlegen", haupt = true) {
                val p = a.text.toString()
                when {
                    p.length < 8 -> meldung("Bitte mindestens 8 Zeichen.")
                    p != b.text.toString() -> meldung("Die beiden Eingaben sind verschieden.")
                    else -> {
                        KonfigSpeicher.aendere { it.copy(boss = Passwort.erzeuge(p)) }
                        bossSeit = System.currentTimeMillis()
                        f.schliessen()
                        panel?.aufbauen()
                        meldung("Boss-Modus aktiv.")
                    }
                }
            })
            return
        }
        frageGeheim("Boss-Modus", "Master-Passwort") { eingabe, f ->
            when (Passwort.pruefe("boss", eingabe, hash)) {
                true -> {
                    bossSeit = System.currentTimeMillis()
                    f.schliessen()
                    panel?.aufbauen()
                    meldung("Boss-Modus aktiv.")
                }
                false -> meldung("Falsches Passwort.")
                null -> meldung("Zu viele Fehlversuche – gesperrt für ${Passwort.gesperrtNoch("boss")} s.")
            }
        }
    }

    override fun bossAbmelden() {
        val f = neuesFenster("Boss-Modus beenden", 1000)
        f.add(text(this, "Soll die aktuelle Einrichtung vorher als Vorlage für alle Geräte hochgeladen werden?", 26))
        f.knoepfe(
            knopf(this, "Abbrechen") { f.schliessen() },
            knopf(this, "Nur beenden") { f.schliessen(); bossBeenden() },
            knopf(this, "Hochladen und beenden", haupt = true) { f.schliessen(); hochladen(beendenDanach = true) }
        )
    }

    private fun bossBeenden() {
        bossSeit = 0
        panel?.aufbauen()
        meldung("Boss-Modus beendet.")
    }

    override fun hochladen() = hochladen(false)

    /** Schreib-Zugang wird nur für diesen Upload abgefragt und nicht gespeichert. */
    private fun hochladen(beendenDanach: Boolean) {
        if (!bossAktiv()) return
        val f = neuesFenster("Als Vorlage hochladen", 1000)
        f.add(text(this, "Zugang mit SCHREIB-Rechten für ${Geraet.server.ifBlank { "(noch kein Server eingetragen)" }} – wird nicht gespeichert.", 24, Farben.TEXT2))
        val b = eingabe(this, "Benutzername (Schreibzugang)")
        val p = eingabe(this, "Passwort", geheim = true)
        f.add(b); f.add(p)
        f.knoepfe(knopf(this, "Abbrechen") { f.schliessen() }, knopf(this, "Hochladen", haupt = true) {
            val benutzer = b.text.toString().trim()
            val passwort = p.text.toString()
            f.schliessen()
            meldung("Lädt hoch …")
            arbeit.execute {
                val t = Abgleich.hochladen(this, benutzer, passwort)
                haupt.post {
                    meldung(t, lang = true)
                    if (beendenDanach && !t.startsWith("Fehler")) bossBeenden()
                    panel?.aufbauen()
                }
            }
        })
    }

    override fun zugangSpeichern(server: String, ordner: String, benutzer: String, passwort: String) {
        if (!bossAktiv()) return
        if (server.isNotBlank() && !server.trim().startsWith("https://") && !(BuildConfig.DEBUG && server.trim().startsWith("http://127.0.0.1:"))) {
            meldung("Der Server muss mit https:// beginnen."); return
        }
        Geraet.server = server
        Geraet.ordner = ordner
        if (benutzer.isNotBlank() && passwort.isNotEmpty()) {
            if (!Abgleich.speichereZugang(benutzer.trim(), passwort)) {
                meldung("Zugangsdaten konnten auf diesem Gerät nicht sicher gespeichert werden."); return
            }
        }
        Abgleich.planen(this)
        panel?.aufbauen()
        meldung("Zugang gespeichert.")
    }

    override fun jetztAbgleichen() {
        meldung("Gleicht ab …")
        arbeit.execute {
            val t = Abgleich.pruefen(this, vonHand = true)
            haupt.post {
                hintergrundStand = -1
                widgetFreigabeGefragt = false
                widgetsAbgleichen()
                zeigen()
                panel?.aufbauen()
                meldung(t, lang = true)
            }
        }
    }

    // ------------------------------------------------------------ Hintergrund-Prüfungen beim Start

    private fun hintergrundPruefungen() {
        if (Abgleich.faellig()) arbeit.execute {
            Abgleich.pruefen(this)
            haupt.post { widgetsAbgleichen(); zeigen() }
        }
    }

    // ------------------------------------------------------------ Updates

    override fun updateText() = updateStatus

    override fun updatePruefen() {
        updateStatus = "Prüft …"
        panel?.aufbauen()
        updatePruefenStill(vonHand = true)
    }

    private var pruefungLaeuft = false

    private fun updatePruefenStill(vonHand: Boolean = false) {
        if (pruefungLaeuft) return
        pruefungLaeuft = true
        val automatisch = Geraet.autoUpdate && Update.darfInstallieren(this)
        arbeit.execute {
            // Automatisch: gleich mitladen und prüfen, installiert wird danach ohne Nachfrage
            val e = if (automatisch) TagesJob.updateVorladen(this) else Update.pruefen()
            haupt.post {
                pruefungLaeuft = false
                if (e == null) {
                    // Nicht erreichbar: niemals sperren
                    updateStatus = "GitHub ist gerade nicht erreichbar."
                    sperreAufheben()
                } else {
                    Geraet.letzterUpdateCheck = System.currentTimeMillis()
                    Geraet.veraltetGemeldet = e.gesperrt
                    updateInfo = e
                    updateStatus = if (e.neuer) "Neue Version ${e.info.version} verfügbar." else "Auf dem neuesten Stand."
                    if (e.gesperrt) zeigeSperre(e) else sperreAufheben()
                    when {
                        !e.neuer -> Unit
                        automatisch && Geraet.updateBereit == e.info.version -> bereitesUpdateInstallieren()
                        !e.gesperrt -> updateAnbieten(e)
                    }
                }
                if (vonHand) panel?.aufbauen()
            }
        }
    }

    /**
     * Liegt ein geprüftes Update bereit: installieren, sobald der Startbildschirm ein paar Sekunden
     * unbenutzt vorne ist (nie mitten in einer Eingabe).
     */
    private fun bereitesUpdateInstallieren() {
        val v = Geraet.updateBereit
        if (v.isEmpty()) return
        if (!Update.istNeuer(v, BuildConfig.VERSION_NAME) || !Update.apkDatei(this).exists()) {
            // schon installiert (oder Datei weg): aufräumen
            Geraet.updateBereit = ""
            Update.apkDatei(this).delete()
            return
        }
        if (!Geraet.autoUpdate || !Update.darfInstallieren(this)) return
        // Abgelehnt (Android fragt bis Version 11 nach)? Dann nicht bei jedem Zurückkehren neu fragen
        if (System.currentTimeMillis() - letzteAutoInstallation < 4 * 60 * 60_000L && !Geraet.veraltetGemeldet) return
        haupt.removeCallbacks(autoInstallation)
        haupt.postDelayed(autoInstallation, 5_000)
    }

    private val autoInstallation = Runnable {
        val ruhig = vorne && fenster.isEmpty() && panel == null && uebersicht == null && !laedt
        if (!ruhig) return@Runnable
        letzteAutoInstallation = System.currentTimeMillis()
        meldung("DammLauncher wird aktualisiert …")
        try { Update.installieren(this) } catch (_: Exception) { Geraet.updateBereit = "" }
    }

    private fun updateAnbieten(e: Update.Ergebnis) {
        val f = neuesFenster("Neue Version ${e.info.version}", 1100)
        if (e.info.notizen.isNotBlank()) f.add(text(this, e.info.notizen.take(1500), 22, Farben.TEXT2))
        f.add(text(this, "Wichtig: 7 Tage nach Erscheinen einer neuen Version sperrt sich die alte aus Sicherheitsgründen.", 22, Farben.ROT), 18)
        if (Geraet.autoUpdate && !Update.darfInstallieren(this))
            f.add(text(this, "Für automatische Updates einmal „Apps installieren“ erlauben – „Jetzt aktualisieren“ führt dorthin.", 22, Farben.TEXT2), 12)
        f.knoepfe(knopf(this, "Später") { f.schliessen() }, knopf(this, "Jetzt aktualisieren", haupt = true) { f.schliessen(); updateLaden(e, null) })
    }

    /** Ganzseitige Sperre – nur der Update-Knopf hilft. */
    private fun zeigeSperre(e: Update.Ergebnis) {
        if (sperre != null) return
        raster.abbrechen()
        uebersichtZu()
        panelZu()
        val status = text(this, "", 26, Color.WHITE)
        val ebene = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xFF7A1F1F.toInt())
            isClickable = true
            elevation = 100f.vp
            setPadding(120.vp, 60.vp, 120.vp, 60.vp)
            addView(text(context, "Diese Version ist veraltet und wird aus Sicherheitsgründen gesperrt. Bitte aktualisieren.", 48, Color.WHITE, fett = true).apply { gravity = Gravity.CENTER })
            addView(text(context, "Installiert: ${BuildConfig.VERSION_NAME} · Neu: ${e.info.version}", 30, Color.WHITE).apply { gravity = Gravity.CENTER }, lp(oben = 30.vp))
            addView(knopf(context, "Jetzt aktualisieren", haupt = true) { updateLaden(e, status) }.apply { groesse(40); minHeight = 130.vp; setPadding(80.vp, 0, 80.vp, 0) }, lp(oben = 60.vp))
            addView(status.apply { gravity = Gravity.CENTER }, lp(oben = 30.vp))
        }
        sperre = ebene
        wurzel.addView(ebene, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun sperreAufheben() {
        sperre?.let { wurzel.removeView(it) }
        sperre = null
    }

    private var laedt = false

    private fun updateLaden(e: Update.Ergebnis, status: TextView?) {
        if (laedt) return
        if (!Update.darfInstallieren(this)) {
            meldung("Bitte DammLauncher erlauben, Apps zu installieren – dann noch einmal tippen.", lang = true)
            status?.text = "Bitte in den Android-Einstellungen „Apps installieren“ erlauben, dann noch einmal tippen."
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            } catch (_: Exception) {
            }
            return
        }
        laedt = true
        status?.text = "Lädt …"
        arbeit.execute {
            val fehler = Update.vorbereiten(this, e.info) { p -> haupt.post { status?.text = "Lädt … ${(p * 100).toInt()} %" } }
            if (fehler == null) Geraet.updateBereit = e.info.version
            haupt.post {
                laedt = false
                if (fehler != null) {
                    status?.text = fehler
                    meldung(fehler, lang = true)
                } else {
                    status?.text = "Installation wird gestartet …"
                    try { Update.installieren(this) } catch (x: Exception) { meldung("Installation nicht möglich: ${x.message}") }
                }
            }
        }
    }

    override fun gestenErlaubt() = RandGesten.erlaubt(this)

    override fun gestenErlauben() {
        try {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        } catch (_: Exception) {
            meldung("Dieses Board bietet die Einstellung „Über anderen Apps einblenden“ nicht an.")
        }
    }

    companion object {
        const val EXTRA_GESTE = "geste"
        /** Wann zuletzt automatisch installiert wurde (hält, solange der Prozess lebt). */
        private var letzteAutoInstallation = 0L
        private const val REQ_BIND = 11
        private const val REQ_KONF = 12
        private const val REQ_BILD = 13
        private const val REQ_ICON = 14
        private const val REQ_EXPORT = 15
        private const val REQ_IMPORT = 16
    }
}
