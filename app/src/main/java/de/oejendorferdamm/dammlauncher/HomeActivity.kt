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
import de.oejendorferdamm.dammlauncher.abgleich.Abwesenheit
import de.oejendorferdamm.dammlauncher.konfig.Feierabend
import de.oejendorferdamm.dammlauncher.konfig.Hinweis
import de.oejendorferdamm.dammlauncher.konfig.Ordner
import de.oejendorferdamm.dammlauncher.konfig.Zeiten
import de.oejendorferdamm.dammlauncher.konfig.Zeitraum
import de.oejendorferdamm.dammlauncher.qr.QrCode
import de.oejendorferdamm.dammlauncher.ui.AppZelle
import de.oejendorferdamm.dammlauncher.ui.InfoKachel
import de.oejendorferdamm.dammlauncher.ui.QrAnsicht
import android.view.MotionEvent
import java.util.Calendar
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
import de.oejendorferdamm.dammlauncher.konfig.Aussehen
import de.oejendorferdamm.dammlauncher.konfig.Profile
import de.oejendorferdamm.dammlauncher.pflege.Aufraeumen
import de.oejendorferdamm.dammlauncher.abgleich.Status
import de.oejendorferdamm.dammlauncher.ui.Dock
import de.oejendorferdamm.dammlauncher.ui.Kopfzeile
import de.oejendorferdamm.dammlauncher.ui.KnopfKachel
import de.oejendorferdamm.dammlauncher.ui.LautstaerkeKachel
import de.oejendorferdamm.dammlauncher.ui.Thema
import de.oejendorferdamm.dammlauncher.ui.ZufallKachel
import de.oejendorferdamm.dammlauncher.widgets.WidgetVerwaltung
import java.util.UUID
import java.util.concurrent.Executors

/** Der Startbildschirm – die einzige von außen erreichbare Komponente. */
class HomeActivity : Activity(), RasterAktionen, PanelAktionen {
    internal val haupt = Handler(Looper.getMainLooper())
    internal val arbeit = Executors.newSingleThreadExecutor()
    internal lateinit var wurzel: FrameLayout
    internal lateinit var raster: RasterAnsicht
    private lateinit var kopfzeile: Kopfzeile
    private lateinit var dock: Dock
    /** Zuletzt gesehene Stunde (für „Profil nach Fach“), -2 = noch keine. */
    private var stundeZuletzt: Int? = -2
    private lateinit var alleApps: TextView
    private lateinit var fotoHinweis: TextView
    private var vorne = false
    private lateinit var widgets: WidgetVerwaltung
    internal var uebersicht: AppUebersicht? = null
    internal var panel: EinstellungenPanel? = null
    internal val fenster = ArrayList<GrossFenster>()
    private var sperre: View? = null
    private var meldungAnsicht: TextView? = null
    private lateinit var hinweisBand: TextView
    private lateinit var lehrerKnopf: TextView
    /** Feierabend-Sperre (über allem außer Update-Sperre, Fenstern und Meldungen). */
    private var ruhe: View? = null
    private var schoner: View? = null
    private var letzteBeruehrung = System.currentTimeMillis()
    /** Nach dem Wecken aus dem Bildschirmschoner: Rest dieser Berührung verschlucken. */
    private var schlucken = false

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

    private var leistenStand: Any? = null
    private val konfigBeobachter: (Konfig) -> Unit = { k ->
        zeigen()
        val stand = k.gesten to k.seitenleiste
        if (stand != leistenStand) { leistenStand = stand; RandGesten.abgleichen(this, neu = true) }
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
        // Clevertouch-Aussehen: Kopfzeile oben, Dock unten (sonst ausgeblendet)
        kopfzeile = Kopfzeile(this, { oeffneUebersicht(suche = true) }, { schnell() }).apply { visibility = View.GONE }
        wurzel.addView(kopfzeile, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Kopfzeile.HOEHE.vp, Gravity.TOP))
        dock = Dock(this, { a, v -> starte(a, v) }, { a -> dockMenue(a) }, { oeffneUebersicht() }).apply { visibility = View.GONE }
        wurzel.addView(dock, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, 140.vp, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = 22.vp })
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
        // Hinweis-Band für alle Boards (aus der Vorlage), oben in der Mitte
        hinweisBand = text(this, "", 30, 0xFF1F2328.toInt(), fett = true).apply {
            background = runde(0xF2FDE68A.toInt(), 30f.vp, 2.vp, 0xFFF59E0B.toInt())
            setPadding(40.vp, 16.vp, 40.vp, 16.vp)
            gravity = Gravity.CENTER
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            maxWidth = (resources.displayMetrics.widthPixels * 0.8f).toInt()
            visibility = View.GONE
        }
        wurzel.addView(hinweisBand, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = 14.vp })
        lehrerKnopf = glasKnopf(this, "Lehrer-Ansicht  ✕") { zurSchuelerAnsicht() }.apply { groesse(22); setPadding(30.vp, 12.vp, 30.vp, 12.vp); visibility = View.GONE }
        wurzel.addView(lehrerKnopf, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END).apply { topMargin = 14.vp; rightMargin = 14.vp })
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
            try {
                val zeit = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.GERMAN).format(java.util.Date())
                Geraet.merkeAbsturzText("$zeit · ${BuildConfig.VERSION_NAME} · ${t.name}: $e\n" + e.stackTrace.take(12).joinToString("\n") { "  at $it" })
            } catch (_: Throwable) {}
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
        haupt.removeCallbacks(wartung)
        haupt.postDelayed(wartung, WARTUNG_MS)
        haupt.removeCallbacks(takt)
        takt.run()
    }

    /**
     * Alle 30 Sekunden (nur solange der Startbildschirm vorne ist, kostet fast nichts): Feierabend,
     * Bildschirmschoner, Rückkehr zur Schüler-Ansicht, Hinweis-Band und Abwesenheits-Zahl.
     */
    private val takt = object : Runnable {
        override fun run() {
            if (!vorne) return
            zeitenPruefen()
            haupt.postDelayed(this, 30_000)
        }
    }

    private fun zeitenPruefen() {
        val k = KonfigSpeicher.konfig
        val jetzt = Calendar.getInstance()
        val ruhig = System.currentTimeMillis() - letzteBeruehrung
        if (Zeiten.feierabend(k.feierabend, jetzt) && System.currentTimeMillis() >= Geraet.feierabendFreiBis) zeigeRuhe() else ruheWeg()
        if (k.schueler.an && Geraet.lehrerAnsicht && ruhig > k.schueler.zurueck * 60_000L) zurSchuelerAnsicht()
        if (k.schoner > 0 && schoner == null && sperre == null && !laedt && ruhig > k.schoner * 60_000L) zeigeSchoner()
        hinweisZeigen(k)
        // Aufräumen zu Beginn jeder Pause – nur, wenn der Startbildschirm vorne ist (also nichts unterbrochen wird)
        if (k.aufraeumen.pausen && vorne && Zeiten.pausenBeginn(k.pausen, jetzt) && !Aufraeumen.eben()) arbeit.execute { Aufraeumen.jetzt(this) }
        // Profil nach Fach: beim Stundenwechsel das Profil, das so heißt wie das Fach
        val stunde = Zeiten.stundeJetzt(k.stunden, jetzt)
        if (k.profilNachFach && stunde != stundeZuletzt) {
            val ruhig2 = fenster.isEmpty() && panel == null && uebersicht == null && ruhe == null
            if (ruhig2) {
                stundeZuletzt = stunde
                if (stunde != null) Profile.finde(k, Zeiten.fach(stundenplan(), jetzt, stunde))?.let { p ->
                    if (p != k.profil) { KonfigSpeicher.aendere { Profile.wechseln(it, p) }; meldung("Profil „$p“", false) }
                }
            }
        }
        if (k.elemente.any { it is Element.Widget && it.provider == Element.STUNDE } && Geraet.server.isNotBlank() && System.currentTimeMillis() - Geraet.stundenplanGeladen > 6 * 60 * 60_000L) {
            Geraet.stundenplanGeladen = System.currentTimeMillis()
            arbeit.execute { if (stundenplanLaden()) haupt.post { kachelnNeu() } }
        }
        if (Status.faellig()) arbeit.execute { Status.senden(this) }
        if (k.elemente.any { it is Element.Widget && it.provider == Element.ABWESEND } && System.currentTimeMillis() - Geraet.abwesendGeladen > 5 * 60_000L) {
            Geraet.abwesendGeladen = System.currentTimeMillis() // auch bei Fehler nicht gleich wieder
            arbeit.execute { if (Abwesenheit.laden(this)) haupt.post { kachelnNeu() } }
        }
    }

    /** Eingebaute Kacheln neu zeichnen (z. B. neue Abwesenheits-Meldung). */
    internal fun kachelnNeu() = raster.widgetRahmen().values.forEach { r -> (r as? WidgetRahmen)?.getChildAt(0)?.invalidate() }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        letzteBeruehrung = System.currentTimeMillis()
        if (ev.actionMasked == MotionEvent.ACTION_DOWN && schoner != null) {
            schonerWeg()
            schlucken = true
        }
        if (schlucken) {
            if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) schlucken = false
            return true
        }
        return super.dispatchTouchEvent(ev)
    }

    /**
     * Steht das Board den ganzen Tag auf dem Startbildschirm, gibt es kein onResume – deshalb alle
     * 15 Minuten: neues Tagesfoto anzeigen bzw. holen und ein bereitliegendes Update installieren.
     */
    private val wartung = object : Runnable {
        override fun run() {
            if (!vorne) return
            if (FotoDesTages.faellig()) unsplashLaden(false) else hintergrund(KonfigSpeicher.konfig)
            if (Geraet.veraltetGemeldet || System.currentTimeMillis() - Geraet.letzterUpdateCheck > 24 * 60 * 60_000L) updatePruefenStill()
            else bereitesUpdateInstallieren()
            haupt.postDelayed(this, WARTUNG_MS)
        }
    }

    override fun onPause() {
        vorne = false
        haupt.removeCallbacks(wartung)
        haupt.removeCallbacks(takt)
        schonerWeg()
        RandGesten.vorne(false)
        super.onPause()
    }

    override fun onDestroy() {
        haupt.removeCallbacksAndMessages(null)
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
            ruhe != null -> Unit // Feierabend: nur „Entsperren“ hilft
            uebersicht != null -> uebersichtZu()
            panel != null -> panelZu()
            else -> raster.abbrechen() // ein Launcher beendet sich nicht
        }
    }

    // ------------------------------------------------------------ Anzeigen

    internal fun zeigen() {
        val k = KonfigSpeicher.konfig
        Thema.clevertouch = k.aussehen == Aussehen.CLEVERTOUCH
        AppKatalog.form = k.symbolForm
        val anbieter = if (sicher) emptySet() else widgets.anbieterNamen()
        val (sichtbar, _) = KonfigJson.aufloesen(k, AppKatalog::istDa) { it in Element.EINGEBAUT || it in anbieter }
        // Was fehlt (App nicht installiert), erscheint als Platzhalter – im sicheren Start fehlen Widgets absichtlich
        val da = sichtbar.map { it.schluessel }.toSet()
        // Schüler-Ansicht: was nur für Lehrkräfte ist, bleibt weg
        val schueler = schuelerAnsicht()
        val alle = k.elemente.filter { !schueler || it.schluessel !in k.schueler.nurLehrer }
        val fehlt = alle.filter { it.schluessel !in da && !(sicher && it is Element.Widget) }.map { it.schluessel }.toSet()
        val elemente = alle.filter { it.schluessel in da || it.schluessel in fehlt }
        raster.setze(k, elemente, fehlt) { w ->
            when (w.provider) {
                Element.UHR, Element.UHR_TRANSPARENT -> UhrAnsicht(this, w.provider == Element.UHR_TRANSPARENT, w.uhr)
                Element.PAUSE -> InfoKachel(this) { Zeiten.pause(KonfigSpeicher.konfig.pausen, Calendar.getInstance()) }
                Element.ABWESEND -> InfoKachel(this) { Abwesenheit.anzeige(Geraet.klasse) }
                Element.QR -> QrAnsicht(this).apply { text = w.text }
                Element.STUNDE -> InfoKachel(this) { Zeiten.stunde(KonfigSpeicher.konfig.stunden, stundenplan(), Calendar.getInstance()) }
                Element.LAUTSTAERKE -> LautstaerkeKachel(this)
                Element.ZUFALL -> ZufallKachel(this, { zufallListe(w.kennung) }, { Geraet.gezogen(w.kennung) }, { Geraet.setzeGezogen(w.kennung, it) })
                Element.FREIGABE -> KnopfKachel(this, "Bildschirm teilen", { listOf("📡" to freigabeName(w.kennung)) }) { freigabeStarten(w.kennung) }
                Element.SCHNELL -> KnopfKachel(this, "Schnelleinstellungen", { SCHNELL_KNOEPFE }) { schnellAktion(it) }
                else -> if (sicher) null else widgets.ansicht(this, w)
            }
        }
        val ohneApps = schueler && k.schueler.ohneAlleApps
        alleApps.visibility = if (k.alleAppsKnopf && !ohneApps && !Thema.clevertouch) View.VISIBLE else View.GONE
        kopfzeile.visibility = if (Thema.clevertouch) View.VISIBLE else View.GONE
        dock.visibility = if (Thema.clevertouch && !(ohneApps && k.dock.isEmpty())) View.VISIBLE else View.GONE
        if (Thema.clevertouch) dock.setze(k.dock)
        lehrerKnopf.visibility = if (k.schueler.an && !schueler) View.VISIBLE else View.GONE
        hinweisZeigen(k)
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
                // Farben und Milchglas einmal je Bild ausrechnen (wenige Millisekunden, im Hintergrund)
                val thema = try { b?.let { Thema.ausBild(it) } } catch (_: Throwable) { null } ?: Thema.ausFarbe(k.hintergrund.farbe)
                haupt.post {
                    Thema.setze(thema)
                    themaNeu()
                    wurzel.background = if (b == null) ColorDrawable(k.hintergrund.farbe) else LayerDrawable(arrayOf(
                        BitmapDrawable(resources, b),
                        GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x59000000, 0x00000000, 0x00000000, 0x73000000))
                    ))
                }
            }
        } else {
            if (hintergrundStand != -1L - k.hintergrund.farbe) {
                Thema.setze(Thema.ausFarbe(k.hintergrund.farbe))
                themaNeu()
            }
            hintergrundStand = -1L - k.hintergrund.farbe
            wurzel.background = ColorDrawable(k.hintergrund.farbe)
        }
    }

    /** Neue Farben: Glasflächen und Kacheln neu zeichnen. */
    private fun themaNeu() {
        if (!Thema.clevertouch) return
        kopfzeile.invalidateAll()
        dock.invalidateAll()
        kachelnNeu()
    }

    private fun View.invalidateAll() {
        invalidate()
        if (this is ViewGroup) for (i in 0 until childCount) getChildAt(i).invalidateAll()
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

    internal fun meldung(text: String, lang: Boolean) {
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

    internal fun neuesFenster(titel: String, breite: Int = 1000): GrossFenster {
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
        // Schüler-Ansicht: langer Druck + PIN öffnet die Lehrer-Ansicht (Einstellungen danach wie gewohnt)
        if (schuelerAnsicht()) { mitErlaubnis { zurLehrerAnsicht() }; return }
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
            is Ordner -> return
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

    override fun gesperrt() = KonfigSpeicher.konfig.bearbeitenGesperrt || schuelerAnsicht()

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
        if (schuelerAnsicht()) return
        when (e) {
            is Element.App -> {
                val name = e.titel ?: AppKatalog.finde(e.paket, e.aktivitaet)?.name ?: e.paket
                val f = neuesFenster(name, 900)
                f.add(text(this, "Zum Verschieben: lange drücken und auf eine freie Zelle ziehen – auf einen Ordner gezogen, landet die App darin.", 24, Farben.TEXT2))
                f.add(knopf(this, "Name und Symbol ändern …", haupt = true) { f.schliessen(); bearbeiten(e) })
                f.add(knopf(this, "In einen Ordner legen …") { f.schliessen(); ordnerWaehlen(e) })
                f.add(knopf(this, "Ins Dock legen (Clevertouch-Aussehen)") { f.schliessen(); insDock(e) })
                f.add(knopf(this, "App-Info") { f.schliessen(); AppKatalog.zeigeAppInfo(ComponentName(e.paket, e.aktivitaet)) })
                schuelerKnopf(f, e)
                f.add(knopf(this, "Vom Startbildschirm entfernen", gefahr = true) { f.schliessen(); entferne(e) })
            }
            is Element.Widget -> {
                val f = neuesFenster(when (e.provider) {
                    Element.UHR, Element.UHR_TRANSPARENT -> "Uhr"
                    Element.PAUSE -> "Pausen-Countdown"
                    Element.ABWESEND -> "Heute fehlen …"
                    Element.QR -> "QR-Code"
                    Element.STUNDE -> "Stundenplan"
                    Element.LAUTSTAERKE -> "Lautstärke"
                    Element.ZUFALL -> "Zufall"
                    Element.FREIGABE -> "Bildschirm teilen"
                    Element.SCHNELL -> "Schnelleinstellungen"
                    else -> "Widget"
                }, 900)
                f.add(knopf(this, "Größe ändern", haupt = true) { f.schliessen(); raster.beginneGroesse(e) })
                when (e.provider) {
                    Element.UHR, Element.UHR_TRANSPARENT -> f.add(knopf(this, "Uhr einstellen …") { f.schliessen(); uhrEinstellen(e) })
                    Element.PAUSE -> f.add(knopf(this, "Pausenzeiten …") { f.schliessen(); pausenEinstellen() })
                    Element.ABWESEND -> f.add(knopf(this, "Klasse dieses Boards …") { f.schliessen(); klasseEinstellen() })
                    Element.QR -> f.add(knopf(this, "QR-Code ändern …") { f.schliessen(); qrEinrichten(e) })
                    Element.STUNDE -> {
                        f.add(knopf(this, "Stundenzeiten …") { f.schliessen(); stundenEinstellen() })
                        f.add(knopf(this, "Stundenplan dieses Boards …") { f.schliessen(); stundenplanEinstellen() })
                    }
                    Element.ZUFALL -> f.add(knopf(this, "Namen bzw. Zahlen …") { f.schliessen(); zufallEinrichten(e.kennung) })
                    Element.FREIGABE -> f.add(knopf(this, "Freigabe-App wählen …") { f.schliessen(); freigabeWaehlen(e.kennung) })
                    Element.LAUTSTAERKE, Element.SCHNELL -> Unit
                    else -> f.add(knopf(this, "Inhalt vergrößern …") { f.schliessen(); zoomEinstellen(e) })
                }
                val id = Geraet.widgetId(e.kennung)
                if (!e.eingebaut && id != 0 && widgets.info(id)?.configure != null) f.add(knopf(this, "Widget einrichten …") { f.schliessen(); konfigurieren(id, null) })
                schuelerKnopf(f, e)
                f.add(knopf(this, "Vom Startbildschirm entfernen", gefahr = true) { f.schliessen(); entferne(e) })
            }
            is Ordner -> {
                val f = neuesFenster("Ordner „${e.name}“", 900)
                f.add(knopf(this, "Öffnen", haupt = true) { f.schliessen(); oeffneOrdner(e, raster) })
                f.add(knopf(this, "Umbenennen …") { f.schliessen(); ordnerUmbenennen(e) })
                f.add(knopf(this, "App hinzufügen …") { f.schliessen(); ordnerAppWaehlen(e) })
                schuelerKnopf(f, e)
                f.add(knopf(this, "Ordner auflösen (Apps auf den Startbildschirm)") { f.schliessen(); ordnerAufloesen(e) })
                f.add(knopf(this, "Ordner mit allen Apps entfernen", gefahr = true) { f.schliessen(); entferne(e) })
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
            reihe("Uhrzeit",
                wahl("11:42", !u.uhr.sekunden) { aendere { it.copy(uhr = it.uhr.copy(sekunden = false)) } },
                wahl("11:42:30 (mit Sekunden)", u.uhr.sekunden) { aendere { it.copy(uhr = it.uhr.copy(sekunden = true)) } })
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

    internal fun oeffneUebersicht(suche: Boolean = false) {
        if (uebersicht != null || sperre != null || ruhe != null) return
        if (schuelerAnsicht() && KonfigSpeicher.konfig.schueler.ohneAlleApps) return
        raster.abbrechen()
        val u = AppUebersicht(this,
            starten = { app, v -> uebersichtZu(); val r = Rect(); v.getGlobalVisibleRect(r); if (!AppKatalog.starte(app.komponente, r)) meldung("Die App lässt sich nicht starten.") },
            langerDruck = { app -> appMenue(app) },
            schliessen = { uebersichtZu() },
            zusatzKnopf = knopf(this, "Widgets …") { uebersichtZu(); widgetHinzufuegen() }
        )
        uebersicht = u
        wurzel.addView(u, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        if (suche) u.sucheOeffnen()
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
        f.add(knopf(this, "Ins Dock legen (Clevertouch-Aussehen)") { f.schliessen(); insDock(Element.App(app.paket, app.aktivitaet, 0, 0)) })
        f.add(knopf(this, "In die Seitenleiste legen") { f.schliessen(); inSeitenleiste(Element.App(app.paket, app.aktivitaet, 0, 0)) })
        f.add(knopf(this, "App-Info") { f.schliessen(); AppKatalog.zeigeAppInfo(app.komponente) })
        f.add(knopf(this, "In der Übersicht ausblenden") {
            f.schliessen()
            mitErlaubnis {
                KonfigSpeicher.aendere { it.copy(ausgeblendet = it.ausgeblendet + AppKatalog.schluessel(app.komponente)) }
                uebersicht?.neuLaden()
                meldung("„${app.name}“ ist ausgeblendet. Wieder einblenden: Einstellungen → Apps.")
            }
        })
    }

    /** Wie die Einstellungen: mit PIN (falls festgelegt) – sonst direkt. */
    internal fun mitErlaubnis(weiter: () -> Unit) {
        val pin = KonfigSpeicher.konfig.pin
        if (pin == null || bossAktiv()) { weiter(); return }
        frageGeheim("PIN", "PIN eingeben", zahl = true) { eingabe, f ->
            when (Passwort.pruefe("pin", eingabe, pin)) {
                true -> { f.schliessen(); weiter() }
                false -> meldung("Falsche PIN.")
                null -> meldung("Zu viele Fehlversuche – gesperrt für ${Passwort.gesperrtNoch("pin")} s.")
            }
        }
    }

    private fun paketeGeaendert(entfernt: String?) {
        // Deinstallierte Apps und deren Widgets sauber vom Startbildschirm nehmen
        if (entfernt != null) KonfigSpeicher.aendere { k ->
            fun bleibt(e: Element) = when (e) {
                is Element.App -> e.paket != entfernt
                is Element.Widget -> !e.provider.startsWith("$entfernt/")
                is Ordner -> true
            }
            fun ohne(e: Element) = if (e is Ordner) e.copy(apps = e.apps.filter { it.paket != entfernt }) else e
            k.elemente.filterNot(::bleibt).filterIsInstance<Element.Widget>().forEach { w -> widgets.loesche(Geraet.widgetId(w.kennung)); Geraet.setzeWidgetId(w.kennung, 0) }
            k.profile.flatMap { it.elemente }.filterNot(::bleibt).filterIsInstance<Element.Widget>().forEach { w -> widgets.loesche(Geraet.widgetId(w.kennung)); Geraet.setzeWidgetId(w.kennung, 0) }
            k.copy(
                elemente = k.elemente.filter(::bleibt).map(::ohne), nichtPlatziert = k.nichtPlatziert.filter(::bleibt).map(::ohne),
                dock = k.dock.filter { it.paket != entfernt },
                seitenleiste = k.seitenleiste.copy(apps = k.seitenleiste.apps.filter { it.paket != entfernt }),
                profile = k.profile.map { p -> p.copy(elemente = p.elemente.filter(::bleibt).map(::ohne)) }
            )
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
        f.add(knopf(this, "⏰  Noch … bis zur Pause (DammLauncher, 2×1)", haupt = true) { f.schliessen(); eingebautAnlegen(Element.PAUSE, 2, 1) }.apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL }, 10)
        f.add(knopf(this, "🧒  Heute fehlen … – nur die Zahl (DammLauncher, 2×2)", haupt = true) { f.schliessen(); eingebautAnlegen(Element.ABWESEND, 2, 2) }.apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL }, 10)
        f.add(knopf(this, "🔳  QR-Code: Link oder Gäste-WLAN (DammLauncher, 2×2)", haupt = true) { f.schliessen(); qrEinrichten(null) }.apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL }, 10)
        f.add(knopf(this, "📅  Stundenplan: welche Stunde, welches Fach (DammLauncher, 3×1)", haupt = true) { f.schliessen(); eingebautAnlegen(Element.STUNDE, 3, 1) }.apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL }, 10)
        f.add(knopf(this, "🔊  Großer Lautstärke-Regler (DammLauncher, 3×1)", haupt = true) { f.schliessen(); eingebautAnlegen(Element.LAUTSTAERKE, 3, 1) }.apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL }, 10)
        f.add(knopf(this, "🎲  Zufallsname bzw. Zufallszahl (DammLauncher, 2×2)", haupt = true) { f.schliessen(); eingebautAnlegen(Element.ZUFALL, 2, 2) }.apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL }, 10)
        f.add(knopf(this, "📡  Bildschirm teilen (DammLauncher, 2×2)", haupt = true) { f.schliessen(); eingebautAnlegen(Element.FREIGABE, 2, 2) }.apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL }, 10)
        f.add(knopf(this, "⚡  Schnelleinstellungen (DammLauncher, 3×2)", haupt = true) { f.schliessen(); eingebautAnlegen(Element.SCHNELL, 3, 2) }.apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL }, 10)
        f.add(knopf(this, "📁  Leerer Ordner (1×1)", haupt = true) { f.schliessen(); ordnerAnlegen(null) }.apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL }, 10)
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

    private fun uhrAnlegen(art: String) = eingebautAnlegen(art, 3, 2)

    /** Eingebaute Kachel anlegen – passt sie nicht, wird sie kleiner. Gibt die Kennung zurück (null = kein Platz). */
    internal fun eingebautAnlegen(art: String, breite: Int, hoehe: Int, text: String = ""): String? {
        val k = KonfigSpeicher.konfig
        var b = breite
        var h = hoehe
        var platz = Belegung.ersteFreie(k.elemente, b, h, k.spalten, k.zeilen)
        while (platz == null && (b > 1 || h > 1)) {
            if (b >= h && b > 1) b-- else h--
            platz = Belegung.ersteFreie(k.elemente, b, h, k.spalten, k.zeilen)
        }
        if (platz == null) { meldung("Kein freier Platz – Raster vergrößern oder etwas entfernen."); return null }
        val kennung = (if (art == Element.UHR || art == Element.UHR_TRANSPARENT) "uhr" else "k") + UUID.randomUUID().toString().replace("-", "").take(8)
        KonfigSpeicher.aendere { it.copy(elemente = it.elemente + Element.Widget(art, kennung, platz.first, platz.second, b, h, text = text)) }
        if (art == Element.PAUSE && k.pausen.isEmpty()) pausenEinstellen()
        if (art == Element.ABWESEND && Geraet.klasse.isBlank()) klasseEinstellen()
        if (art == Element.STUNDE && k.stunden.isEmpty()) stundenEinstellen()
        if (art == Element.ZUFALL) zufallEinrichten(kennung)
        if (art == Element.FREIGABE && text.isEmpty()) freigabeWaehlen(kennung)
        return kennung
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

    // ------------------------------------------------------------ Schüler- und Lehrer-Ansicht

    /** Läuft gerade die Schüler-Ansicht (eingeschaltet und nicht mit der PIN geöffnet)? */
    internal fun schuelerAnsicht() = KonfigSpeicher.konfig.schueler.an && !Geraet.lehrerAnsicht

    private fun zurLehrerAnsicht() {
        Geraet.lehrerAnsicht = true
        letzteBeruehrung = System.currentTimeMillis()
        zeigen()
        meldung("Lehrer-Ansicht. Zurück: Knopf oben rechts – oder von selbst nach ${KonfigSpeicher.konfig.schueler.zurueck} min ohne Berührung.", lang = true)
    }

    override fun zurSchuelerAnsicht() {
        if (!Geraet.lehrerAnsicht) return
        Geraet.lehrerAnsicht = false
        raster.abbrechen()
        fenster.toList().forEach { it.schliessen() }
        uebersichtZu()
        panelZu()
        zeigen()
    }

    /** Im Menü eines Elements: in der Schüler-Ansicht zeigen oder nicht. */
    private fun schuelerKnopf(f: GrossFenster, e: Element) {
        val sa = KonfigSpeicher.konfig.schueler
        if (!sa.an) return
        val nur = e.schluessel in sa.nurLehrer
        f.add(knopf(this, if (nur) "👁  In der Schüler-Ansicht wieder zeigen" else "🙈  In der Schüler-Ansicht ausblenden") {
            f.schliessen()
            KonfigSpeicher.aendere { k -> k.copy(schueler = k.schueler.copy(nurLehrer = if (nur) k.schueler.nurLehrer - e.schluessel else k.schueler.nurLehrer + e.schluessel)) }
            meldung(if (nur) "Ist jetzt auch in der Schüler-Ansicht zu sehen." else "Nur noch in der Lehrer-Ansicht zu sehen.")
        })
    }

    // ------------------------------------------------------------ Hinweis-Band

    private fun hinweisZeigen(k: Konfig) {
        val jetzt = Calendar.getInstance()
        // Zeitgesteuerte Nachricht an alle Boards geht dem Hinweis vor
        val n = Zeiten.nachricht(k.nachrichten, jetzt)
        val h = k.hinweis
        val an = n != null || Zeiten.hinweisAktiv(h, jetzt)
        val t = when {
            n != null -> "⏰  ${n.text}"
            an && h != null -> "📢  ${h.text}"
            else -> ""
        }
        val farbe = if (n != null) 0xF2FECACA.toInt() else 0xF2FDE68A.toInt()
        val bandOben = if (Thema.clevertouch) Kopfzeile.HOEHE.vp + 6.vp else 14.vp
        val lpBand = hinweisBand.layoutParams as FrameLayout.LayoutParams
        if (lpBand.topMargin != bandOben) { lpBand.topMargin = bandOben; hinweisBand.layoutParams = lpBand }
        if (an == (hinweisBand.visibility == View.VISIBLE) && hinweisBand.text.toString() == t && hinweisBand.tag == farbe) { rasterRaender(); return }
        hinweisBand.text = t
        hinweisBand.tag = farbe
        hinweisBand.background = runde(farbe, 30f.vp, 2.vp, if (n != null) 0xFFDC2626.toInt() else 0xFFF59E0B.toInt())
        hinweisBand.visibility = if (an) View.VISIBLE else View.GONE
        if (an) hinweisBand.post { rasterRaender() } else rasterRaender()
    }

    /** Das Raster rückt unter Kopfzeile und Hinweis-Band und über das Dock – nichts wird verdeckt. */
    private fun rasterRaender() {
        val lp = raster.layoutParams as FrameLayout.LayoutParams
        val band = if (hinweisBand.visibility == View.VISIBLE) hinweisBand.height + 20.vp else 0
        val oben = if (Thema.clevertouch) Kopfzeile.HOEHE.vp + (if (band > 0) band + 6.vp else 0) else band
        val unten = if (Thema.clevertouch && dock.visibility == View.VISIBLE) Dock.HOEHE.vp else 0
        if (lp.topMargin != oben || lp.bottomMargin != unten) {
            lp.topMargin = oben
            lp.bottomMargin = unten
            raster.layoutParams = lp
        }
    }

    override fun hinweisEinstellen() {
        val k = KonfigSpeicher.konfig
        val f = neuesFenster("Hinweis-Band", 1150)
        f.add(text(this, "Erscheint oben auf dem Startbildschirm, z. B. „Morgen Wandertag – Regenjacke mitbringen!“. " +
            "Auf alle Boards kommt er mit „Als Vorlage hochladen“ (Boss-Modus).", 22, Farben.TEXT2))
        val feld = eingabe(this, "Text (bis ${Hinweis.MAX} Zeichen)", k.hinweis?.text ?: "")
        f.add(feld)
        var tage = 1
        val reihe = LinearLayout(this)
        fun dauer() {
            reihe.removeAllViews()
            listOf(1 to "Nur heute", 3 to "3 Tage", 7 to "1 Woche", 14 to "2 Wochen").forEach { (n, t) ->
                reihe.addView(knopf(this, t, haupt = tage == n) { tage = n; dauer() }, lp(rechts = 12.vp))
            }
        }
        dauer()
        f.add(text(this, "Wie lange?" + (k.hinweis?.let { " (bisher bis ${it.bis.split('-').reversed().joinToString(".")})" } ?: ""), 22, Farben.TEXT2), 18)
        f.add(reihe, 6)
        f.knoepfe(
            knopf(this, "Entfernen", gefahr = k.hinweis != null) { f.schliessen(); KonfigSpeicher.aendere { it.copy(hinweis = null) } }.apply { isEnabled = k.hinweis != null },
            knopf(this, "Abbrechen") { f.schliessen() },
            knopf(this, "Anzeigen", haupt = true) {
                val t = feld.text.toString().trim().replace(Regex("\\s+"), " ")
                if (t.isEmpty()) { meldung("Bitte einen Text eingeben."); return@knopf }
                f.schliessen()
                KonfigSpeicher.aendere { it.copy(hinweis = Hinweis(t.take(Hinweis.MAX), Zeiten.hinweisBis(tage, Calendar.getInstance()))) }
                panel?.aufbauen()
            }
        )
    }

    // ------------------------------------------------------------ Feierabend-Sperre

    private fun wann(ms: Long): String {
        val e = Calendar.getInstance().apply { timeInMillis = ms }
        val uhr = Zeitraum.uhrzeit(e.get(Calendar.HOUR_OF_DAY) * 60 + e.get(Calendar.MINUTE))
        val heute = Calendar.getInstance()
        val morgen = (heute.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 1) }
        return when (Zeiten.tag(e)) {
            Zeiten.tag(heute) -> "heute um $uhr"
            Zeiten.tag(morgen) -> "morgen um $uhr"
            else -> java.text.SimpleDateFormat("EEEE", java.util.Locale.GERMAN).format(e.time) + " um " + uhr
        }
    }

    /** Abends: nur Uhr und „Entsperren“ (PIN) – Apps und Einstellungen sind nicht erreichbar. */
    private fun zeigeRuhe() {
        if (ruhe != null || sperre != null) return
        raster.abbrechen()
        fenster.toList().forEach { it.schliessen() }
        uebersichtZu()
        panelZu()
        if (Geraet.lehrerAnsicht) { Geraet.lehrerAnsicht = false; zeigen() }
        val ende = Zeiten.feierabendEnde(KonfigSpeicher.konfig.feierabend, Calendar.getInstance())
        if (KonfigSpeicher.konfig.aufraeumen.feierabend && !Aufraeumen.eben()) arbeit.execute { Aufraeumen.jetzt(this) }
        val ebene = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xF20B1220.toInt())
            isClickable = true
            addView(UhrAnsicht(context, transparent = true, stil = UhrStil()), lp(1000.vp, 440.vp))
            addView(text(context, "Feierabend 🌙", 52, Color.WHITE, fett = true).apply { gravity = Gravity.CENTER }, lp(oben = 20.vp))
            addView(text(context, "Das Board ist bis ${wann(ende)} gesperrt.", 30, 0xDDFFFFFF.toInt()).apply { gravity = Gravity.CENTER }, lp(oben = 14.vp))
            addView(knopf(context, "Entsperren", haupt = true) { ruheEntsperren(ende) }.apply { minWidth = 360.vp }, lp(oben = 50.vp))
        }
        ruhe = ebene
        wurzel.addView(ebene, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        meldungAnsicht?.bringToFront()
    }

    private fun ruheWeg() {
        ruhe?.let { wurzel.removeView(it) }
        ruhe = null
    }

    /** Mit der PIN (falls festgelegt) bis zum Ende dieses Feierabends freigeben. */
    private fun ruheEntsperren(ende: Long) {
        val frei = {
            Geraet.feierabendFreiBis = ende
            ruheWeg()
            meldung("Entsperrt bis ${wann(ende)}.")
        }
        val pin = KonfigSpeicher.konfig.pin
        if (pin == null) { frei(); return }
        frageGeheim("Entsperren", "PIN eingeben", zahl = true) { eingabe, f ->
            when (Passwort.pruefe("pin", eingabe, pin)) {
                true -> { f.schliessen(); frei() }
                false -> meldung("Falsche PIN.")
                null -> meldung("Zu viele Fehlversuche – gesperrt für ${Passwort.gesperrtNoch("pin")} s.")
            }
        }
    }

    override fun feierabendEinstellen() {
        val fa = KonfigSpeicher.konfig.feierabend
        val f = neuesFenster("Feierabend-Sperre", 1150)
        f.add(text(this, "Abends und am Wochenende zeigt das Board nur eine Uhr. „Entsperren“ fragt die PIN – danach bleibt es bis zum Ende dieses Feierabends offen.", 22, Farben.TEXT2))
        if (KonfigSpeicher.konfig.pin == null) f.add(text(this, "Noch keine PIN festgelegt (Einstellungen → Bedienung): dann reicht ein Tipp auf „Entsperren“.", 22, Farben.ROT), 10)
        var an = fa.an
        var wochenende = fa.wochenende
        f.add(de.oejendorferdamm.dammlauncher.ui.schalter(this, "Feierabend-Sperre an", an) { an = it })
        val ab = eingabe(this, "Sperren ab, z. B. 17:00", Zeitraum.uhrzeit(fa.ab))
        val bis = eingabe(this, "Wieder frei ab, z. B. 07:00", Zeitraum.uhrzeit(fa.bis))
        f.add(text(this, "Sperren ab:", 22, Farben.TEXT2), 16); f.add(ab, 4)
        f.add(text(this, "Wieder frei ab:", 22, Farben.TEXT2), 12); f.add(bis, 4)
        f.add(de.oejendorferdamm.dammlauncher.ui.schalter(this, "Samstag und Sonntag ganz gesperrt", wochenende) { wochenende = it }, 16)
        f.knoepfe(knopf(this, "Abbrechen") { f.schliessen() }, knopf(this, "Speichern", haupt = true) {
            val a = Zeitraum.minuten(ab.text.toString())
            val b = Zeitraum.minuten(bis.text.toString())
            if (a == null || b == null) { meldung("Bitte Uhrzeiten wie 17:00 und 07:00 eingeben."); return@knopf }
            f.schliessen()
            val neu = Feierabend(an, a, b, wochenende)
            KonfigSpeicher.aendere { it.copy(feierabend = neu) }
            // Wer gerade abends einrichtet, wird nicht sofort ausgesperrt: gilt ab dem nächsten Feierabend
            val jetzt = Calendar.getInstance()
            if (Zeiten.feierabend(neu, jetzt)) {
                Geraet.feierabendFreiBis = Zeiten.feierabendEnde(neu, jetzt)
                meldung("Gespeichert – gilt ab dem nächsten Feierabend.")
            } else Geraet.feierabendFreiBis = 0
            panel?.aufbauen()
        })
    }

    // ------------------------------------------------------------ Bildschirmschoner

    /** Dunkel, gedimmt, Foto des Tages und eine Uhr, die jede Minute ein Stück wandert (kein Einbrennen). */
    internal fun zeigeSchoner() {
        if (schoner != null || !vorne) return
        raster.abbrechen()
        val ebene = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            elevation = 95f.vp
            contentDescription = "Bildschirmschoner"
        }
        val foto = FotoDesTages.datei().takeIf { KonfigSpeicher.konfig.hintergrund.unsplash && it.exists() }
            ?: KonfigSpeicher.hintergrundDatei.takeIf { KonfigSpeicher.konfig.hintergrund.bild && it.exists() }
        if (foto != null) {
            val bild = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; alpha = 0.55f }
            ebene.addView(bild, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            arbeit.execute {
                val b = try { BitmapFactory.decodeFile(foto.absolutePath, BitmapFactory.Options().apply { inSampleSize = 2 }) } catch (_: Throwable) { null }
                haupt.post { if (schoner === ebene) bild.setImageBitmap(b) }
            }
        }
        val uhr = UhrAnsicht(this, transparent = true, stil = UhrStil())
        val m = resources.displayMetrics
        val ub = minOf(760.vp, m.widthPixels * 7 / 10)
        val uh = ub * 9 / 19
        val lpu = FrameLayout.LayoutParams(ub, uh, Gravity.TOP or Gravity.START)
        ebene.addView(uhr, lpu)
        val wandern = object : Runnable {
            override fun run() {
                lpu.leftMargin = (Math.random() * (m.widthPixels - ub).coerceAtLeast(1)).toInt()
                lpu.topMargin = (Math.random() * (m.heightPixels - uh).coerceAtLeast(1)).toInt()
                uhr.layoutParams = lpu
                if (schoner === ebene) ebene.postDelayed(this, 60_000)
            }
        }
        schoner = ebene
        wandern.run()
        wurzel.addView(ebene, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        window.attributes = window.attributes.apply { screenBrightness = 0.05f }
    }

    private fun schonerWeg() {
        val s = schoner ?: return
        schoner = null
        wurzel.removeView(s)
        window.attributes = window.attributes.apply { screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
        letzteBeruehrung = System.currentTimeMillis()
    }

    // ------------------------------------------------------------ Ordner

    private fun ordnerAktuell(kennung: String) = KonfigSpeicher.konfig.elemente.firstOrNull { it is Ordner && it.kennung == kennung } as? Ordner

    internal fun appName(a: Element.App) = a.titel ?: AppKatalog.finde(a.paket, a.aktivitaet)?.name ?: AppNamen.raten(a.paket)

    override fun oeffneOrdner(o: Ordner, v: View) {
        val aktuell = ordnerAktuell(o.kennung) ?: return
        val k = KonfigSpeicher.konfig
        val f = neuesFenster(aktuell.name, 1400)
        val apps = aktuell.apps.filter { AppKatalog.istDa(it.paket, it.aktivitaet) }
        if (apps.isEmpty()) f.add(text(this, "Der Ordner ist leer. Apps hineinlegen: eine App auf den Ordner ziehen – oder „App hinzufügen“.", 24, Farben.TEXT2))
        val schrift = Schriften.von(this, k.schrift)
        apps.chunked(5).forEach { reihe ->
            f.add(LinearLayout(this).apply {
                reihe.forEach { a ->
                    addView(AppZelle(context).apply {
                        dunkel = true
                        setze(ComponentName(a.paket, a.aktivitaet), appName(a), a.icon, 70, 22f.vp, true, schrift)
                        setOnClickListener { zelle ->
                            f.schliessen()
                            val r = Rect(); zelle.getGlobalVisibleRect(r)
                            if (!AppKatalog.starte(ComponentName(a.paket, a.aktivitaet), r)) meldung("Die App lässt sich nicht starten.")
                        }
                        setOnLongClickListener { if (!gesperrt()) { f.schliessen(); ordnerAppMenue(aktuell, a) }; true }
                    }, lp(240.vp, 230.vp))
                }
            }, 6)
        }
        if (!gesperrt()) f.knoepfe(
            knopf(this, "Umbenennen …") { f.schliessen(); ordnerUmbenennen(aktuell) },
            knopf(this, "App hinzufügen …") { f.schliessen(); ordnerAppWaehlen(aktuell) }
        )
    }

    override fun inOrdner(app: Element.App, o: Ordner) {
        val vorher = ordnerAktuell(o.kennung) ?: return
        if (vorher.apps.size >= Ordner.MAX_APPS) { meldung("Der Ordner ist voll (höchstens ${Ordner.MAX_APPS} Apps)."); return }
        KonfigSpeicher.aendere { k ->
            k.copy(elemente = k.elemente.filter { it.schluessel != app.schluessel }.map {
                if (it is Ordner && it.kennung == o.kennung && it.apps.none { a -> a.schluessel == app.schluessel }) it.copy(apps = it.apps + app.verschoben(0, 0) as Element.App) else it
            })
        }
        meldung("„${appName(app)}“ liegt jetzt im Ordner „${vorher.name}“.")
    }

    private fun ordnerWaehlen(app: Element.App) {
        val ordner = KonfigSpeicher.konfig.elemente.filterIsInstance<Ordner>()
        if (ordner.isEmpty()) { ordnerAnlegen(app); return }
        val f = neuesFenster("In welchen Ordner?", 900)
        ordner.forEach { o -> f.add(knopf(this, "📁  ${o.name}") { f.schliessen(); inOrdner(app, o) }) }
        f.add(knopf(this, "Neuer Ordner …", haupt = true) { f.schliessen(); ordnerAnlegen(app) })
    }

    /** Neuer Ordner – mit [app] darin an deren Stelle, sonst leer in der ersten freien Zelle. */
    private fun ordnerAnlegen(app: Element.App?) {
        if (gesperrt()) { meldung("Der Startbildschirm ist gegen Verändern gesperrt (Einstellungen)."); return }
        val k = KonfigSpeicher.konfig
        val platz = if (app != null) app.spalte to app.zeile else Belegung.ersteFreie(k.elemente, 1, 1, k.spalten, k.zeilen)
        if (platz == null) { meldung("Kein freier Platz – Raster vergrößern oder etwas entfernen."); return }
        val neu = Ordner(UUID.randomUUID().toString().replace("-", "").take(12), "Ordner", listOfNotNull(app?.verschoben(0, 0) as Element.App?), platz.first, platz.second)
        KonfigSpeicher.aendere { it.copy(elemente = it.elemente.filter { e -> e.schluessel != app?.schluessel } + neu) }
        ordnerUmbenennen(neu)
    }

    private fun ordnerUmbenennen(o: Ordner) {
        val f = neuesFenster("Name des Ordners", 900)
        val feld = eingabe(this, "z. B. Mathe, Lesen, Spiele", if (o.name == "Ordner") "" else o.name)
        f.add(feld)
        f.knoepfe(knopf(this, "Abbrechen") { f.schliessen() }, knopf(this, "Speichern", haupt = true) {
            val n = feld.text.toString().trim().take(40).ifEmpty { "Ordner" }
            f.schliessen()
            KonfigSpeicher.aendere { k -> k.copy(elemente = k.elemente.map { if (it is Ordner && it.kennung == o.kennung) it.copy(name = n) else it }) }
        })
        feld.requestFocus()
    }

    private fun ordnerAppWaehlen(o: Ordner) {
        lateinit var auswahl: AppUebersicht
        auswahl = AppUebersicht(this, { _, _ -> }, {}, { wurzel.removeView(auswahl) }, titel = "In „${o.name}“ legen …", auswahl = { app ->
            wurzel.removeView(auswahl)
            inOrdner(Element.App(app.paket, app.aktivitaet, 0, 0), o)
            ordnerAktuell(o.kennung)?.let { oeffneOrdner(it, raster) }
        })
        wurzel.addView(auswahl, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun ordnerAppMenue(o: Ordner, a: Element.App) {
        val f = neuesFenster(appName(a), 900)
        f.add(knopf(this, "Auf den Startbildschirm legen", haupt = true) {
            f.schliessen()
            val k = KonfigSpeicher.konfig
            if (k.elemente.any { it.schluessel == a.schluessel }) { ausOrdner(o, a); return@knopf }
            val platz = Belegung.ersteFreie(k.elemente, 1, 1, k.spalten, k.zeilen) ?: run { meldung("Kein freier Platz auf dem Startbildschirm."); return@knopf }
            KonfigSpeicher.aendere { kk ->
                kk.copy(elemente = kk.elemente.map { if (it is Ordner && it.kennung == o.kennung) it.copy(apps = it.apps.filter { x -> x.schluessel != a.schluessel }) else it } + a.verschoben(platz.first, platz.second))
            }
        })
        f.add(knopf(this, "Aus dem Ordner entfernen", gefahr = true) { f.schliessen(); ausOrdner(o, a) })
    }

    private fun ausOrdner(o: Ordner, a: Element.App) = KonfigSpeicher.aendere { k ->
        k.copy(elemente = k.elemente.map { if (it is Ordner && it.kennung == o.kennung) it.copy(apps = it.apps.filter { x -> x.schluessel != a.schluessel }) else it })
    }

    /** Ordner weg, seine Apps in freie Zellen (der Platz des Ordners zuerst) – was nicht passt, in die Ablage. */
    private fun ordnerAufloesen(o: Ordner) {
        KonfigSpeicher.aendere { k ->
            val liste = k.elemente.filter { it.schluessel != o.schluessel }.toMutableList()
            val ablage = k.nichtPlatziert.toMutableList()
            for (a in o.apps) {
                if (liste.any { it.schluessel == a.schluessel } || ablage.any { it.schluessel == a.schluessel }) continue
                val platz = if (liste.none { it.spalte == o.spalte && it.zeile == o.zeile }) o.spalte to o.zeile else Belegung.ersteFreie(liste, 1, 1, k.spalten, k.zeilen)
                if (platz == null) ablage.add(a) else liste.add(a.verschoben(platz.first, platz.second))
            }
            k.copy(elemente = liste, nichtPlatziert = ablage)
        }
        if (KonfigSpeicher.konfig.nichtPlatziert.isNotEmpty()) meldung("Nicht alle Apps hatten Platz – sie liegen in der Ablage (Einstellungen → Widgets und Ablage).", lang = true)
    }

    // ------------------------------------------------------------ Pausen, Klasse, QR-Code

    override fun pausenEinstellen() {
        val f = neuesFenster("Pausenzeiten", 1100)
        f.add(text(this, "Für die Kachel „Noch … bis zur Pause“. Auf alle Boards kommen sie mit „Als Vorlage hochladen“ (Boss-Modus).", 22, Farben.TEXT2))
        val liste = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        f.add(liste)
        fun aufbauen() {
            liste.removeAllViews()
            val p = KonfigSpeicher.konfig.pausen
            if (p.isEmpty()) liste.addView(text(this, "Noch keine Pausen eingetragen.", 24, Farben.TEXT2))
            p.forEach { z ->
                liste.addView(LinearLayout(this).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    addView(text(context, "${Zeitraum.uhrzeit(z.von)} – ${Zeitraum.uhrzeit(z.bis)}", 30, fett = true), lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    addView(knopf(context, "Entfernen", gefahr = true) { KonfigSpeicher.aendere { it.copy(pausen = it.pausen - z) }; aufbauen() })
                }, lp(ViewGroup.LayoutParams.MATCH_PARENT, oben = 10.vp))
            }
        }
        aufbauen()
        val von = eingabe(this, "Beginn, z. B. 09:35")
        val bis = eingabe(this, "Ende, z. B. 09:55")
        f.add(text(this, "Neue Pause:", 22, Farben.TEXT2), 22)
        f.add(von, 6)
        f.add(bis, 8)
        f.knoepfe(knopf(this, "Fertig") { f.schliessen() }, knopf(this, "Hinzufügen", haupt = true) {
            val z = Zeitraum.aus("${von.text}-${bis.text}")
            if (z == null) { meldung("Bitte Uhrzeiten wie 09:35 und 09:55 eingeben (Ende nach Beginn)."); return@knopf }
            KonfigSpeicher.aendere { it.copy(pausen = (it.pausen + z).distinct().sortedBy { p -> p.von }.take(20)) }
            von.setText(""); bis.setText("")
            aufbauen()
            panel?.aufbauen()
        })
    }

    override fun klasseEinstellen() {
        val f = neuesFenster("Klasse dieses Boards", 1100)
        f.add(text(this, "Die Kachel „Heute fehlen …“ zeigt nur die ZAHL der fehlenden Kinder dieser Klasse – keine Namen. " +
            "Die Zahlen legt das iPhone-Widget „IServ-Abwesenheiten“ als abwesend.json in den Vorlagen-Ordner.", 22, Farben.TEXT2))
        val bekannt = Abwesenheit.klassen()
        if (bekannt.isNotEmpty()) {
            f.add(text(this, "Klassen aus der letzten Meldung:", 22, Farben.TEXT2), 16)
            f.add(android.widget.HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                addView(LinearLayout(context).apply {
                    bekannt.forEach { kl -> addView(knopf(context, kl, haupt = kl.equals(Geraet.klasse, true)) { Geraet.klasse = kl; f.schliessen(); kachelnNeu(); panel?.aufbauen() }, lp(rechts = 12.vp)) }
                })
            }, 6)
        }
        val feld = eingabe(this, "Klasse, z. B. 5a", Geraet.klasse)
        f.add(feld, 16)
        val g = Geraet.abwesendGeladen
        f.add(text(this, when {
            Geraet.server.isBlank() -> "Noch kein Server eingetragen (Boss-Modus → Zentrale Verteilung)."
            g == 0L -> "Noch nichts gelesen."
            Geraet.abwesendDaten.isEmpty() -> "Zuletzt gesucht ${wann(g)} – im Ordner liegt noch keine Meldung."
            else -> "Zuletzt gelesen ${wann(g)}."
        }, 21, Farben.TEXT2), 10)
        f.knoepfe(
            knopf(this, "Jetzt laden") {
                meldung("Lädt …")
                arbeit.execute {
                    Abwesenheit.laden(this)
                    haupt.post { kachelnNeu(); meldung(if (Geraet.abwesendDaten.isEmpty()) "Keine Meldung gefunden." else "Meldung gelesen.") }
                }
            },
            knopf(this, "Speichern", haupt = true) { Geraet.klasse = feld.text.toString(); f.schliessen(); kachelnNeu(); panel?.aufbauen() }
        )
    }

    /** QR-Code anlegen ([w] = null) oder ändern: Link oder WLAN-Zugang. */
    private fun qrEinrichten(w: Element.Widget?, wlanWahl: Boolean? = null) {
        val alt = w?.text.orEmpty()
        val wlan = wlanWahl ?: (QrCode.wlanName(alt) != null)
        val f = neuesFenster(if (w == null) "QR-Code anlegen" else "QR-Code ändern", 1100)
        f.add(LinearLayout(this).apply {
            addView(knopf(context, "🔗  Link", haupt = !wlan) { if (wlan) { f.schliessen(); qrEinrichten(w, false) } }, lp(rechts = 12.vp))
            addView(knopf(context, "📶  Gäste-WLAN", haupt = wlan) { if (!wlan) { f.schliessen(); qrEinrichten(w, true) } })
        })
        val felder = if (!wlan) {
            f.add(text(this, "Kinder und Gäste scannen den Code mit der Kamera und landen auf dieser Seite.", 22, Farben.TEXT2), 16)
            listOf(eingabe(this, "Adresse, z. B. https://schule.de/elternbrief", if (QrCode.wlanName(alt) == null) alt else ""))
        } else {
            f.add(text(this, "Der Zugang steht lesbar in der Einrichtung (und in der Vorlage) – nur für ein Gäste-WLAN verwenden.", 22, Farben.ROT), 16)
            listOf(eingabe(this, "WLAN-Name", QrCode.wlanFeld(alt, "S") ?: ""), eingabe(this, "Passwort (leer = offenes WLAN)", QrCode.wlanFeld(alt, "P") ?: ""))
        }
        felder.forEach { f.add(it, 10) }
        f.knoepfe(knopf(this, "Abbrechen") { f.schliessen() }, knopf(this, "Speichern", haupt = true) {
            val inhalt = if (!wlan) {
                val t = felder[0].text.toString().trim()
                if (t.contains("://") || t.isEmpty()) t else "https://$t"
            } else {
                val ssid = felder[0].text.toString()
                if (ssid.isBlank()) "" else QrCode.wlan(ssid, felder[1].text.toString())
            }
            when {
                inhalt.isEmpty() -> meldung(if (wlan) "Bitte den WLAN-Namen eingeben." else "Bitte eine Adresse eingeben.")
                inhalt.length > Grenzen.QR_TEXT || QrCode.aus(inhalt) == null -> meldung("Das ist zu lang für einen QR-Code.")
                else -> {
                    f.schliessen()
                    if (w == null) eingebautAnlegen(Element.QR, 2, 2, inhalt)
                    else KonfigSpeicher.aendere { k -> k.copy(elemente = k.elemente.map { if (it.schluessel == w.schluessel) (it as Element.Widget).copy(text = inhalt) else it }) }
                }
            }
        })
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

    // ------------------------------------------------------------ 1.6.0 (umgesetzt in HomeFunktionen.kt)

    override fun stundenZeiten() = stundenEinstellen()
    override fun stundenplanBoard() = stundenplanEinstellen()
    override fun nachrichten() = nachrichtenEinstellen()
    override fun profileOeffnen() = profileVerwalten()
    override fun seitenleisteApp() = seitenleisteAppWaehlen()
    override fun hilfeAn() = de.oejendorferdamm.dammlauncher.gesten.SeitenleisteHilfe.an
    override fun hilfeErlauben() { if (!de.oejendorferdamm.dammlauncher.gesten.SeitenleisteHilfe.einstellungenOeffnen(this)) meldung("Dieses Board bietet die Bedienungshilfen-Einstellungen nicht an.") }
    override fun statusZugangEinstellen() = statusZugang()
    override fun tafelnZeigen() = tafelUebersicht()
    override fun aufraeumenJetzt() = aufraeumen()
    override fun schnellOeffnen() = schnell()

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
        /** Knöpfe der Kachel „Schnelleinstellungen“ (Reihenfolge = Index in schnellAktion). */
        val SCHNELL_KNOEPFE = listOf("🔊" to "Lautstärke", "☀" to "Helligkeit", "📶" to "WLAN", "▬" to "Abdecken", "🧹" to "Aufräumen", "🌙" to "Schoner")
        private const val WARTUNG_MS = 15 * 60_000L
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
