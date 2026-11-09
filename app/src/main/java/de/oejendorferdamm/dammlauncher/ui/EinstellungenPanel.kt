/*
 * DammLauncher – Copyright (C) 2026 Teyro – GNU GPL v3 oder neuer, siehe LICENSE
 */
package de.oejendorferdamm.dammlauncher.ui

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import de.oejendorferdamm.dammlauncher.BuildConfig
import de.oejendorferdamm.dammlauncher.konfig.Geraet
import de.oejendorferdamm.dammlauncher.apps.AppKatalog
import de.oejendorferdamm.dammlauncher.konfig.Grenzen
import de.oejendorferdamm.dammlauncher.konfig.Hintergrund
import de.oejendorferdamm.dammlauncher.konfig.Gesten
import de.oejendorferdamm.dammlauncher.konfig.Schrift
import de.oejendorferdamm.dammlauncher.konfig.Konfig
import de.oejendorferdamm.dammlauncher.konfig.KonfigSpeicher
import de.oejendorferdamm.dammlauncher.raster.Belegung
import de.oejendorferdamm.dammlauncher.abgleich.Abwesenheit
import de.oejendorferdamm.dammlauncher.konfig.Zeitraum
import de.oejendorferdamm.dammlauncher.konfig.Zeiten
import de.oejendorferdamm.dammlauncher.konfig.Aussehen
import de.oejendorferdamm.dammlauncher.konfig.Seitenleiste
import de.oejendorferdamm.dammlauncher.konfig.SymbolForm
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Was das Panel auslöst, aber die Activity erledigt (Dateiauswahl, Dialoge, Netz). */
interface PanelAktionen {
    fun bildWaehlen()
    fun widgetHinzufuegen()
    fun exportieren()
    fun importieren()
    fun schulVorlage()
    fun pinFestlegen()
    fun bossAnmelden()
    fun bossAbmelden()
    fun hochladen()
    fun jetztAbgleichen()
    fun zugangSpeichern(server: String, ordner: String, benutzer: String, passwort: String)
    fun updatePruefen()
    fun unsplashNeu()
    fun bossAktiv(): Boolean
    fun gestenErlaubt(): Boolean
    fun gestenErlauben()
    fun updateText(): String
    fun meldung(text: String)
    fun panelZu()
    fun pausenEinstellen()
    fun hinweisEinstellen()
    fun feierabendEinstellen()
    fun klasseEinstellen()
    fun zurSchuelerAnsicht()
    fun stundenZeiten()
    fun stundenplanBoard()
    fun nachrichten()
    fun profileOeffnen()
    fun seitenleisteApp()
    fun hilfeAn(): Boolean
    fun hilfeErlauben()
    fun statusZugangEinstellen()
    fun tafelnZeigen()
    fun aufraeumenJetzt()
    fun schnellOeffnen()
    fun schulPasswort()
    fun minimalQuelleApp()
    fun minimalQuelleAktivitaet()
    fun zeitJetzt()
}

/**
 * Einstellungen als großes Panel (nicht als kleine Liste in der Ecke). Jede Änderung wirkt sofort
 * auf den Startbildschirm dahinter (Live-Vorschau, mit Hilfslinien). Das Panel lässt sich nach
 * links oder rechts schieben, damit man das Raster dahinter sieht.
 */
class EinstellungenPanel(private val wurzel: FrameLayout, private val a: PanelAktionen) {
    private val c = wurzel.context
    private val ebene = FrameLayout(c).apply { isClickable = false }
    private val karte = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        background = runde(Farben.PANEL, 36f.vp)
        elevation = 30f.vp
        isClickable = true
        setPadding(36.vp, 28.vp, 36.vp, 20.vp)
        // Kein Eingabefeld bekommt von selbst den Fokus – sonst springt beim Scrollen die Tastatur auf
        isFocusableInTouchMode = true
        descendantFocusability = ViewGroup.FOCUS_BEFORE_DESCENDANTS
    }
    private val inhalt = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
    private val scroll = ScrollView(c).apply { addView(inhalt); isFillViewport = false }
    /** 0 links (Standard: auf großen Boards gut erreichbar), 1 Mitte, 2 rechts. */
    private var lage = 0
    private var raenderGleich = KonfigSpeicher.konfig.rand.gleich

    // vor init: init baut den Inhalt schon auf
    /** Eine Seite der Einstellungen mit ihren Abschnitten (Karten). */
    private class Seite(val titel: String, val symbol: String, val farbe: Int, val zusammenfassung: (Konfig) -> String, val teile: List<(Konfig) -> Unit>)

    private val seiten: List<Seite> by lazy {
        listOf(
            Seite("Aussehen", "🎨", 0xFF7C3AED.toInt(), { k -> (if (k.aussehen == Aussehen.CLEVERTOUCH) "Clevertouch" else "Klassisch") + " · Hintergrund · Schrift" },
                listOf(::teilAussehen, ::teilHintergrund, ::teilSymboleundSchrift)),
            Seite("Startbildschirm", "▦", 0xFF2563EB.toInt(), { k -> (if (k.minimal.an) "Minimalmodus · " else "") + "${k.spalten} × ${k.zeilen} Raster · Ränder · Widgets · Apps" },
                listOf(::teilMinimal, ::teilRaster, ::teilAbstandzumBildschirmrand, ::teilWidgetsundAblage, ::teilApps)),
            Seite("Bedienung", "👆", 0xFF0891B2.toInt(), { k -> (if (k.pin == null) "ohne PIN" else "mit PIN") + (if (k.startQuelle.id.isNotEmpty()) " · Startquelle " + k.startQuelle.name.ifBlank { "an" } else "") + " · Gesten · Seitenleiste " + (if (k.seitenleiste.an) "an" else "aus") },
                listOf(::teilBedienung, ::teilStartQuelle, ::teilGesten, ::teilSeitenleiste)),
            Seite("Schule", "🏫", 0xFF16A34A.toInt(), { k -> "Stundenplan, Pausen, Hinweise, Schüler-Ansicht" + (if (k.schueler.an) " (an)" else "") },
                listOf(::teilStundenplanundProfile, ::teilSchulzeiten, ::teilHinweisBand, ::teilSchuelerAnsicht, ::teilAbwesenheiten, ::teilAufraeumen)),
            Seite("Verwaltung", "☁", 0xFFEA580C.toInt(), { _ -> "Vorlage für alle Boards, Datei, Updates" + (if (a.bossAktiv()) " · Boss-Modus aktiv" else "") },
                listOf(::teilZentraleVerteilungIServ, ::teilDatei, ::teilUpdates, ::teilUhrzeit)),
            Seite("Über DammLauncher", "ℹ", 0xFF6B7280.toInt(), { _ -> "Version ${BuildConfig.VERSION_NAME} · Lizenzen" }, listOf(::teilUeberDammLauncher))
        )
    }

    /** null = Übersicht, sonst die geöffnete Seite. */
    private var seite: Seite? = null
    private var letzteSeite: Seite? = null

    private val titel = text(c, "Einstellungen", 36, fett = true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
    private val zurueck = knopf(c, "‹") { oeffne(null) }.apply { background = rundKnopf(); contentDescription = "Zurück zur Übersicht"; groesse(40) }

    init {
        karte.addView(LinearLayout(c).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(zurueck, lp(86.vp, 86.vp, rechts = 16.vp))
            addView(titel, lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(knopf(c, "◀") { lage = (lage - 1).coerceAtLeast(0); setzeLage() }.apply { background = rundKnopf() }, lp(86.vp, 86.vp, rechts = 10.vp))
            addView(knopf(c, "▶") { lage = (lage + 1).coerceAtMost(2); setzeLage() }.apply { background = rundKnopf() }, lp(86.vp, 86.vp, rechts = 20.vp))
            // Schließen immer oben rechts sichtbar – gleich wie in DammBoard (die Kopfzeile scrollt nicht mit)
            addView(knopf(c, "✕") { a.panelZu() }.apply { background = rundKnopf(); contentDescription = "Schließen"; groesse(34) }, lp(86.vp, 86.vp))
        })
        karte.addView(scroll, lp(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f, oben = 16.vp))
        ebene.addView(karte)
        setzeLage()
        aufbauen()
        wurzel.addView(ebene, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun setzeLage() {
        val m = c.resources.displayMetrics
        // höchstens ein Drittel des Bildschirms – so bleibt der Startbildschirm daneben sichtbar und auf 90 Zoll alles erreichbar
        val breite = minOf(880.vp, maxOf(m.widthPixels / 3, minOf(560.vp, (m.widthPixels * 0.9f).toInt())))
        karte.layoutParams = FrameLayout.LayoutParams(breite, ViewGroup.LayoutParams.MATCH_PARENT, when (lage) {
            0 -> Gravity.START
            2 -> Gravity.END
            else -> Gravity.CENTER_HORIZONTAL
        }).apply { setMargins(30.vp, 30.vp, 30.vp, 30.vp + Masse.navUnten) }
    }

    fun schliessen() = (ebene.parent as? ViewGroup)?.removeView(ebene)

    private fun aendere(f: (Konfig) -> Konfig) = KonfigSpeicher.aendere(f)

    /** Jeder Abschnitt ist eine eigene weiße Karte; [add] füllt immer die zuletzt begonnene. */
    private var aktuelleKarte: LinearLayout? = null

    private fun abschnitt(titel: String) {
        val k = karte(c)
        k.addView(text(c, titel.uppercase(Locale.GERMAN), 21, Farben.AKZENT, fett = true).apply { letterSpacing = 0.06f }, lp(ViewGroup.LayoutParams.MATCH_PARENT))
        inhalt.addView(k, lp(ViewGroup.LayoutParams.MATCH_PARENT, oben = 22.vp))
        aktuelleKarte = k
    }

    private fun add(v: View, oben: Int = 8) = (aktuelleKarte ?: inhalt).addView(v, lp(ViewGroup.LayoutParams.MATCH_PARENT, oben = oben.vp))

    private fun knoepfe(vararg k: View) = add(Fluss(c).apply { k.forEach { addView(it) } }, 12)


    /** Zurück-Taste: von einer Seite zur Übersicht; false = schon in der Übersicht. */
    fun zurueck(): Boolean {
        if (seite == null) return false
        oeffne(null)
        return true
    }

    private fun oeffne(s: Seite?) {
        seite = s
        aufbauen()
    }

    /** Übersicht im Stil der Android-Einstellungen: je Bereich eine Zeile mit Symbol, Titel und Kurzinfo. */
    private fun uebersicht(k: Konfig) {
        listOf(seiten.take(3), seiten.drop(3)).forEach { gruppe ->
            val kk = karte(c).apply { setPadding(10.vp, 10.vp, 10.vp, 10.vp) }
            gruppe.forEachIndexed { i, s ->
                if (i > 0) kk.addView(View(c).apply { setBackgroundColor(Farben.LINIE) }, lp(ViewGroup.LayoutParams.MATCH_PARENT, 2.vp).apply { leftMargin = 112.vp })
                kk.addView(LinearLayout(c).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(16.vp, 18.vp, 16.vp, 18.vp)
                    background = android.graphics.drawable.StateListDrawable().apply {
                        addState(intArrayOf(android.R.attr.state_pressed), runde(0xFFE8EDF5.toInt(), 22f.vp))
                        addState(intArrayOf(), runde(0x00000000, 22f.vp))
                    }
                    isClickable = true
                    contentDescription = s.titel
                    setOnClickListener { oeffne(s) }
                    addView(text(c, s.symbol, 34, android.graphics.Color.WHITE).apply {
                        gravity = Gravity.CENTER
                        background = runde(s.farbe, 40f.vp)
                    }, lp(80.vp, 80.vp, rechts = 24.vp))
                    addView(LinearLayout(c).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(text(c, s.titel, 28, fett = true))
                        addView(text(c, s.zusammenfassung(k), 21, Farben.TEXT2), lp(oben = 2.vp))
                    }, lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    addView(text(c, "›", 44, Farben.TEXT2), lp())
                }, lp(ViewGroup.LayoutParams.MATCH_PARENT))
            }
            inhalt.addView(kk, lp(ViewGroup.LayoutParams.MATCH_PARENT, oben = 22.vp))
        }
        add(text(c, "Jede Änderung wirkt sofort auf den Startbildschirm daneben. ◀ ▶ schiebt dieses Fenster zur Seite.", 20, Farben.TEXT2).apply { setPadding(12.vp, 0, 12.vp, 0) }, 18)
    }

    /** Inhalt (neu) aufbauen – z. B. nach Boss-Anmeldung oder „Ränder gleich“. Bleibt auf der geöffneten Seite. */
    fun aufbauen() {
        val pos = if (seite === letzteSeite) scroll.scrollY else 0
        letzteSeite = seite
        inhalt.removeAllViews()
        aktuelleKarte = null
        val k = KonfigSpeicher.konfig
        val s = seite
        titel.text = s?.titel ?: "Einstellungen"
        zurueck.visibility = if (s == null) View.GONE else View.VISIBLE
        if (s == null) uebersicht(k) else s.teile.forEach { it(k) }
        aktuelleKarte = null
        add(View(c), 40)
        scroll.post { scroll.scrollTo(0, pos); karte.requestFocus() }
    }

    private fun teilAussehen(k: Konfig) {
        abschnitt("Aussehen")
        val ct = k.aussehen == Aussehen.CLEVERTOUCH
        add(schalter(c, "Clevertouch-Aussehen (wie die neuen Boards)", ct) { an ->
            aendere {
                // Einschalten: einheitliche runde Symbole und keine Animationen – so wirkt es wie Android 15, bleibt aber schnell
                if (an) it.copy(aussehen = Aussehen.CLEVERTOUCH, animationen = false, symbolForm = if (it.symbolForm == SymbolForm.ORIGINAL) SymbolForm.RUND else it.symbolForm)
                else it.copy(aussehen = Aussehen.KLASSISCH)
            }
            aufbauen()
        })
        add(text(c, "Oben Uhr, Suchleiste und Schnelleinstellungen, unten ein Dock mit Lieblings-Apps, Farben passend zum Hintergrund, " +
            "Milchglas – ohne Animationen, damit es auch auf älteren Boards flüssig bleibt. Apps kommen ins Dock über langes Drücken → „Ins Dock legen“.", 21, Farben.TEXT2), 6)
        auswahl("Form der App-Symbole", listOf(SymbolForm.ORIGINAL to "Original", SymbolForm.RUND to "Abgerundet", SymbolForm.KREIS to "Kreis"), k.symbolForm) { v -> aendere { it.copy(symbolForm = v) } }
        if (ct) add(text(c, "Im Dock: " + (if (k.dock.isEmpty()) "noch keine Apps" else k.dock.joinToString(", ") { d -> d.titel ?: AppKatalog.finde(d.paket, d.aktivitaet)?.name ?: d.paket }), 22, Farben.TEXT2), 10)
    }

    private fun teilRaster(k: Konfig) {
        abschnitt("Raster")
        add(regler(c, "Spalten", Grenzen.RASTER, k.spalten) { n -> aendereRaster(n, null) })
        add(regler(c, "Zeilen", Grenzen.RASTER, k.zeilen) { n -> aendereRaster(null, n) })
    }

    private fun teilAbstandzumBildschirmrand(k: Konfig) {
        abschnitt("Abstand zum Bildschirmrand")
        add(schalter(c, "Alle Ränder gleich", raenderGleich) { an ->
            raenderGleich = an
            if (an) aendere { it.copy(rand = it.rand.alle(it.rand.oben)) }
            aufbauen()
        })
        if (raenderGleich) {
            add(regler(c, "Rand (0 = randlos)", Grenzen.RAND, k.rand.oben, 5) { n -> aendere { it.copy(rand = it.rand.alle(n)) } })
        } else {
            add(regler(c, "Oben", Grenzen.RAND, k.rand.oben, 5) { n -> aendere { it.copy(rand = it.rand.copy(oben = n)) } })
            add(regler(c, "Unten", Grenzen.RAND, k.rand.unten, 5) { n -> aendere { it.copy(rand = it.rand.copy(unten = n)) } })
            add(regler(c, "Links", Grenzen.RAND, k.rand.links, 5) { n -> aendere { it.copy(rand = it.rand.copy(links = n)) } })
            add(regler(c, "Rechts", Grenzen.RAND, k.rand.rechts, 5) { n -> aendere { it.copy(rand = it.rand.copy(rechts = n)) } })
        }
        add(regler(c, "Abstand zwischen den Zellen", Grenzen.ABSTAND, k.abstand, 2) { n -> aendere { it.copy(abstand = n) } })
    }

    private fun teilSymboleundSchrift(k: Konfig) {
        abschnitt("Symbole und Schrift")
        add(regler(c, "Symbolgröße", Grenzen.ICON, k.iconProzent, 5, " %") { n -> aendere { it.copy(iconProzent = n) } })
        add(schalter(c, "Namen unter den Symbolen", k.beschriftung) { an -> aendere { it.copy(beschriftung = an) } })
        add(regler(c, "Schriftgröße", Grenzen.TEXT, k.textGroesse) { n -> aendere { it.copy(textGroesse = n) } })
        auswahl("Schrift der App-Namen", Schrift.ALLE.map { it to Schriften.name(it) }, k.schrift, schriftVorschau = true) { s -> aendere { it.copy(schrift = s) } }
    }

    private fun teilGesten(k: Konfig) {
        abschnitt("Gesten")
        val wahlLauncher = listOf(Gesten.APPS to "Alle Apps", Gesten.EINSTELLUNGEN to "Einstellungen", Gesten.AUS to "Nichts")
        auswahl("Auf dem Startbildschirm nach oben wischen", wahlLauncher, k.gesten.hoch) { v -> aendere { it.copy(gesten = it.gesten.copy(hoch = v)) } }
        auswahl("Auf dem Startbildschirm nach unten wischen", wahlLauncher, k.gesten.runter) { v -> aendere { it.copy(gesten = it.gesten.copy(runter = v)) } }
        add(text(c, "Vom Bildschirmrand zur Mitte wischen – wirkt auch in anderen Apps (Firefox, Anton …):", 24, Farben.TEXT), 20)
        val wahlRand = listOf(Gesten.START to "Startbildschirm", Gesten.APPS to "Alle Apps", Gesten.AUS to "Aus")
        auswahl("Vom unteren Rand", wahlRand, k.gesten.randUnten) { v -> aendere { it.copy(gesten = it.gesten.copy(randUnten = v)) } }
        auswahl("Vom linken Rand", wahlRand, k.gesten.randLinks) { v -> aendere { it.copy(gesten = it.gesten.copy(randLinks = v)) } }
        auswahl("Vom rechten Rand", wahlRand, k.gesten.randRechts) { v -> aendere { it.copy(gesten = it.gesten.copy(randRechts = v)) } }
        if (k.gesten.randAn && !a.gestenErlaubt()) {
            add(text(c, "Dafür braucht DammLauncher einmal die Erlaubnis „Über anderen Apps einblenden“ (auf jedem Board).", 22, Farben.ROT), 12)
            knoepfe(knopf(c, "Erlaubnis erteilen …", haupt = true) { a.gestenErlauben() })
        } else if (k.gesten.randAn) {
            add(text(c, "Aktiv. Unten am Rand zeigt ein feiner Strich, wo man wischen kann.", 21, Farben.TEXT2), 8)
        }
    }

    /** Zeitserver: richtige Uhrzeit, auch wenn die Board-Uhr falsch geht. */
    private fun teilUhrzeit(k: Konfig) {
        abschnitt("Uhrzeit (Zeitserver)")
        val z = de.oejendorferdamm.dammlauncher.pflege.ZeitAbgleich
        add(text(c, "Standard ist der Zeitserver der PTB (amtliche Zeit in Deutschland). Leer = aus. Gilt mit der Vorlage für alle Boards.", 21, Farben.TEXT2))
        val feld = eingabe(c, "Zeitserver, z. B. ${z.STANDARD}", k.zeitServer)
        feld.imeOptions = feld.imeOptions or android.view.inputmethod.EditorInfo.IME_ACTION_DONE
        add(feld, 10)
        knoepfe(knopf(c, "Speichern") {
            val s = feld.text.toString().trim()
            if (s.isNotEmpty() && !z.SERVER.matches(s)) a.meldung("Das ist kein gültiger Servername.")
            else { aendere { it.copy(zeitServer = s) }; a.zeitJetzt(); aufbauen() }
        }, knopf(c, "PTB") { aendere { it.copy(zeitServer = z.STANDARD) }; a.zeitJetzt(); aufbauen() }, knopf(c, "Jetzt abgleichen") { a.zeitJetzt() })
        val stand = when {
            k.zeitServer.isEmpty() -> "Aus."
            z.zuletzt == 0L -> "Noch nicht abgeglichen."
            z.fehler != null -> z.fehler!!
            z.versatzMs == 0L -> "Board-Uhr stimmt (weniger als 1 s Abweichung)."
            else -> "Board-Uhr geht " + (if (z.versatzMs > 0) "nach" else "vor") + " um " + dauer(kotlin.math.abs(z.versatzMs)) + " – die Uhren des Launchers zeigen die richtige Zeit."
        }
        add(text(c, stand, 22, if (z.fehler != null) Farben.ROT else Farben.TEXT), 10)
        add(text(c, if (z.darfSystem(c)) (if (z.systemGesetzt) "Systemzeit: Zeitserver eingestellt, „Uhrzeit automatisch“ an." else "Systemzeit: wird beim nächsten Abgleich eingestellt.")
            else "Damit auch die Systemuhr (alle Apps) stimmt, braucht DammLauncher einmal eine Erlaubnis – per USB-Debugging: adb shell pm grant ${c.packageName} android.permission.WRITE_SECURE_SETTINGS (oder über die Geräteverwaltung).", 21, Farben.TEXT2), 6)
    }

    private fun dauer(ms: Long): String {
        val s = ms / 1000
        return when { s < 60 -> "$s s"; s < 3600 -> "${s / 60} min ${s % 60} s"; else -> "${s / 3600} h ${s % 3600 / 60} min" }
    }

    /** Minimalmodus: nur Uhr, Tafel, Timer und Quelle über dem Hintergrund. */
    private fun teilMinimal(k: Konfig) {
        abschnitt("Minimalmodus")
        val m = k.minimal
        // Auf Android 8 gleich mit Leichtmodus (dort ruckelt sonst alles)
        add(schalter(c, "Minimalmodus: nur Hintergrund, große Uhr, Tafel, Timer und Quelle", m.an) { an ->
            aendere { it.copy(minimal = it.minimal.copy(an = an, leicht = if (an && !it.minimal.an && android.os.Build.VERSION.SDK_INT < 28) true else it.minimal.leicht)) }; aufbauen()
        })
        add(text(c, "Alles andere (Raster, Dock, Alle Apps) ist ausgeblendet, aber gespeichert – ausschalten holt es zurück. Langer Druck auf die freie Fläche öffnet die Einstellungen.", 21, Farben.TEXT2), 6)
        if (!m.an) return
        add(schalter(c, "Leichtmodus für langsame Boards (Android 8)", m.leicht) { an -> aendere { it.copy(minimal = it.minimal.copy(leicht = an)) }; aufbauen() })
        add(text(c, "Wirft alles ab, was nicht nötig ist: keine Widgets, keine Randleisten, kein Tagesfoto-Abruf, kein Bildschirmschoner, keine Abrufe im Hintergrund. Nur Uhr, Tafel, Timer und Quelle bleiben.", 21, Farben.TEXT2), 6)
        val eingaenge = de.oejendorferdamm.dammlauncher.pflege.StartQuelle.eingaenge(c)
        add(text(c, "Quelle-Knopf: " + when {
            eingaenge.isNotEmpty() && k.startQuelle.id.isNotEmpty() -> "schaltet auf „${k.startQuelle.name.ifBlank { k.startQuelle.id }}“ (Bedienung → Quelle beim Einschalten)."
            eingaenge.isNotEmpty() -> "zeigt die Eingänge dieses Boards zur Auswahl."
            m.quelle != null -> "öffnet „${m.quelle.titel ?: AppKatalog.finde(m.quelle.paket, m.quelle.aktivitaet)?.name ?: m.quelle.paket}“."
            else -> "Dieses Board meldet Apps keine Eingänge. Der Knopf ist ausgeblendet, unten steht stattdessen ein Hinweis zum Quelle-Wechseln am Board. " +
                "Ist die Quellen-Auswahl des Boards ein Systemfenster ohne eigene App, lässt sie sich unter „Systemfenster …“ wählen (mit ⇄ markiert)."
        }, 22, if (eingaenge.isEmpty() && m.quelle == null) Farben.ROT else Farben.TEXT), 12)
        if (eingaenge.isEmpty()) knoepfe(knopf(c, if (m.quelle == null) "App …" else "Andere App …") { a.minimalQuelleApp() },
            knopf(c, "Systemfenster …") { a.minimalQuelleAktivitaet() },
            *(if (m.quelle != null) arrayOf(knopf(c, "Keine App") { aendere { it.copy(minimal = it.minimal.copy(quelle = null)) }; aufbauen() }) else emptyArray()))
    }

    /** Quelle beim Einschalten: auf den OPS-PC umschalten, sobald er Bild liefert (er fährt länger hoch als das Board). */
    private fun teilStartQuelle(k: Konfig) {
        abschnitt("Quelle beim Einschalten")
        val q = k.startQuelle
        add(text(c, "Nach dem Einschalten wartet der Launcher, bis der gewählte Eingang (z. B. der PC im Einschub) Bild liefert, und schaltet dann um – wie beim Einstecken eines HDMI-Kabels. Einmal je Einschalten, höchstens 10 Minuten lang.", 21, Farben.TEXT2))
        val eingaenge = de.oejendorferdamm.dammlauncher.pflege.StartQuelle.eingaenge(c)
        if (eingaenge.isEmpty()) {
            add(text(c, "Dieses Gerät meldet Apps keine Eingänge – hier geht es nicht.", 22, Farben.ROT), 10)
            return
        }
        add(text(c, "Eingang wählen:", 22, Farben.TEXT2), 12)
        val knoepfeListe = eingaenge.map { e ->
            val signal = when (e.signal) { true -> "Bild da"; false -> "kein Bild"; null -> "bereit" }
            knopf(c, "${e.name} (${e.art}) · $signal", haupt = e.id == q.id) {
                aendere { it.copy(startQuelle = it.startQuelle.copy(id = e.id, name = e.name.take(60))) }
                aufbauen()
            }
        } + knopf(c, "Aus", haupt = q.id.isEmpty()) {
            aendere { it.copy(startQuelle = it.startQuelle.copy(id = "", name = "")) }
            aufbauen()
        }
        knoepfe(*knoepfeListe.toTypedArray())
        if (q.id.isEmpty()) return
        auswahl("Frühestens nach dem Einschalten", listOf("0" to "sofort", "30" to "30 s", "60" to "1 min", "90" to "1,5 min", "120" to "2 min", "180" to "3 min"), q.wartenSek.toString()) { v ->
            aendere { it.copy(startQuelle = it.startQuelle.copy(wartenSek = v.toInt())) }
        }
        add(schalter(c, "Erst umschalten, wenn der Eingang Bild hat", q.aufSignal) { an -> aendere { it.copy(startQuelle = it.startQuelle.copy(aufSignal = an)) } })
        add(text(c, "Schaltet das Board trotzdem zu früh oder nie um: hier ausschalten und die Wartezeit erhöhen.", 21, Farben.TEXT2), 4)
        knoepfe(knopf(c, "Jetzt umschalten (Test)") {
            if (!de.oejendorferdamm.dammlauncher.pflege.StartQuelle.wechseln(c, q.id)) a.meldung("Das Gerät lässt das Umschalten durch Apps nicht zu.")
        })
    }

    private fun teilBedienung(k: Konfig) {
        abschnitt("Bedienung")
        add(schalter(c, "Knopf „Alle Apps“ unten anzeigen", k.alleAppsKnopf) { an -> aendere { it.copy(alleAppsKnopf = an) } })
        add(schalter(c, "Animationen", k.animationen) { an -> aendere { it.copy(animationen = an) } })
        add(schalter(c, "Startbildschirm gegen Verändern sperren", k.bearbeitenGesperrt) { an -> aendere { it.copy(bearbeitenGesperrt = an) } })
        knoepfe(
            knopf(c, if (k.pin == null) "PIN für Einstellungen festlegen …" else "PIN ändern …") { a.pinFestlegen() },
            knopf(c, "PIN entfernen", gefahr = k.pin != null) { aendere { it.copy(pin = null) }; aufbauen() }.apply { isEnabled = k.pin != null }
        )
    }

    private fun teilSeitenleiste(k: Konfig) {
        abschnitt("Seitenleiste")
        val sl = k.seitenleiste
        add(schalter(c, "Seitenleiste am Bildschirmrand (auch über anderen Apps)", sl.an) { an -> aendere { it.copy(seitenleiste = it.seitenleiste.copy(an = an)) }; aufbauen() })
        add(text(c, "Ein schmaler Griff am Rand öffnet: Startbildschirm, Alle Apps, Whiteboard (DammBoard), Timer (DammZeit), Abdecken und eigene Apps.", 21, Farben.TEXT2), 6)
        if (sl.an) {
            auswahl("Seite", listOf(Seitenleiste.RECHTS to "Rechts", Seitenleiste.LINKS to "Links"), sl.seite) { v -> aendere { it.copy(seitenleiste = it.seitenleiste.copy(seite = v)) } }
            if (!a.gestenErlaubt()) {
                add(text(c, "Dafür braucht DammLauncher einmal die Erlaubnis „Über anderen Apps einblenden“.", 22, Farben.ROT), 12)
                knoepfe(knopf(c, "Erlaubnis erteilen …", haupt = true) { a.gestenErlauben() })
            }
            add(text(c, if (a.hilfeAn()) "„Zurück“ und „Übersicht“ sind eingeschaltet." else
                "„Zurück“ und „Übersicht“ brauchen einmal die Bedienungshilfe „DammLauncher-Seitenleiste“ (liest keine Inhalte).", 21, if (a.hilfeAn()) Farben.TEXT2 else Farben.TEXT), 12)
            if (!a.hilfeAn()) knoepfe(knopf(c, "Bedienungshilfe einschalten …") { a.hilfeErlauben() })
            add(text(c, "Eigene Apps (antippen = entfernen):", 22, Farben.TEXT2), 14)
            sl.apps.forEach { app ->
                knoepfe(knopf(c, "✕  " + (app.titel ?: AppKatalog.finde(app.paket, app.aktivitaet)?.name ?: app.paket)) {
                    aendere { it.copy(seitenleiste = it.seitenleiste.copy(apps = it.seitenleiste.apps.filter { x -> x.schluessel != app.schluessel })) }
                    aufbauen()
                })
            }
            if (sl.apps.size < Seitenleiste.MAX_APPS) knoepfe(knopf(c, "App hinzufügen …") { a.seitenleisteApp() })
        }
    }

    private fun teilStundenplanundProfile(k: Konfig) {
        abschnitt("Stundenplan und Profile")
        add(text(c, "Stunden: " + (if (k.stunden.isEmpty()) "noch keine" else "${k.stunden.size} (${Zeitraum.uhrzeit(k.stunden.first().von)}–${Zeitraum.uhrzeit(k.stunden.last().bis)})") +
            "\nStundenplan dieses Boards: " + (if (Geraet.stundenplan.isBlank()) "keiner (sonst stundenplan.json im Vorlagen-Ordner)" else "eingetragen") +
            "\nProfile: " + listOf(k.profil).plus(k.profile.map { it.name }).joinToString(", ") + " – aktiv: ${k.profil}" +
            (if (k.profilNachFach) "\nWechselt beim Stundenwechsel zum Profil des Fachs." else ""), 24, Farben.TEXT2))
        knoepfe(knopf(c, "Stundenzeiten …") { a.stundenZeiten() }, knopf(c, "Stundenplan …") { a.stundenplanBoard() }, knopf(c, "Profile …") { a.profileOeffnen() })
    }

    private fun teilSchuelerAnsicht(k: Konfig) {
        abschnitt("Schüler-Ansicht")
        val sa = k.schueler
        add(schalter(c, "Schüler-Ansicht einschalten", sa.an) { an ->
            // Wer gerade einrichtet, bleibt in der Lehrer-Ansicht
            Geraet.lehrerAnsicht = true
            aendere { it.copy(schueler = it.schueler.copy(an = an)) }
            aufbauen()
        })
        add(text(c, "Kinder sehen nur, was für sie gedacht ist. Lehrer-Ansicht: lange auf eine freie Fläche drücken und die PIN eingeben.", 21, Farben.TEXT2), 6)
        if (sa.an) {
            add(text(c, "Ausblenden: in der Lehrer-Ansicht lange auf eine App, ein Widget oder einen Ordner drücken → „In der Schüler-Ansicht ausblenden“. " +
                "Ausgeblendet: ${sa.nurLehrer.size}.", 21, Farben.TEXT2), 6)
            if (k.pin == null) add(text(c, "Noch keine PIN festgelegt – dann kann jeder in die Lehrer-Ansicht wechseln.", 22, Farben.ROT), 8)
            add(schalter(c, "Keine App-Übersicht in der Schüler-Ansicht", sa.ohneAlleApps) { an -> aendere { it.copy(schueler = it.schueler.copy(ohneAlleApps = an)) } })
            add(regler(c, "Zurück zur Schüler-Ansicht nach … ohne Berührung", 5..120, sa.zurueck.coerceIn(5, 120), 5, " min") { n -> aendere { it.copy(schueler = it.schueler.copy(zurueck = n)) } })
            knoepfe(knopf(c, "Jetzt zur Schüler-Ansicht", haupt = true) { a.zurSchuelerAnsicht() })
        }
    }

    private fun teilSchulzeiten(k: Konfig) {
        abschnitt("Schulzeiten")
        add(text(c, "Pausen: " + (if (k.pausen.isEmpty()) "noch keine" else k.pausen.joinToString(", ") { "${Zeitraum.uhrzeit(it.von)}–${Zeitraum.uhrzeit(it.bis)}" }), 24, Farben.TEXT2))
        knoepfe(knopf(c, "Pausenzeiten …") { a.pausenEinstellen() })
        val fa = k.feierabend
        add(text(c, "Feierabend-Sperre: " + (if (!fa.an) "aus" else "${Zeitraum.uhrzeit(fa.ab)} bis ${Zeitraum.uhrzeit(fa.bis)}" + if (fa.wochenende) ", Wochenende gesperrt" else ""), 24, Farben.TEXT2), 18)
        knoepfe(knopf(c, "Feierabend-Sperre …") { a.feierabendEinstellen() })
        auswahl("Bildschirmschoner (dunkel, Uhr und Foto) nach", listOf("0" to "Aus", "5" to "5 min", "10" to "10 min", "15" to "15 min", "30" to "30 min", "60" to "1 Stunde"), k.schoner.toString()) { v ->
            aendere { it.copy(schoner = v.toInt()) }
        }
    }

    private fun teilHinweisBand(k: Konfig) {
        abschnitt("Hinweis-Band")
        val hw = k.hinweis
        add(text(c, if (hw == null) "Kein Hinweis." else "„${hw.text}“ – bis ${hw.bis.split('-').reversed().joinToString(".")}" +
            (if (!Zeiten.hinweisAktiv(hw, java.util.Calendar.getInstance())) " (abgelaufen)" else ""), 24, Farben.TEXT2))
        knoepfe(knopf(c, if (hw == null) "Hinweis schreiben …" else "Hinweis ändern …") { a.hinweisEinstellen() })
        val heute = Zeiten.tag(java.util.Calendar.getInstance())
        val geplant = k.nachrichten.count { it.tag >= heute }
        add(text(c, "Zeitgesteuerte Nachrichten (z. B. „Probealarm um 10:15“): " + (if (geplant == 0) "keine geplant" else "$geplant geplant"), 24, Farben.TEXT2), 18)
        knoepfe(knopf(c, "Nachrichten …") { a.nachrichten() })
    }

    private fun teilAufraeumen(k: Konfig) {
        abschnitt("Aufräumen")
        add(text(c, "Schließt Apps im Hintergrund und setzt die Lautstärke zurück – damit die nächste Klasse ein ordentliches Board vorfindet.", 21, Farben.TEXT2))
        add(schalter(c, "Zu Beginn jeder Pause (nur wenn der Startbildschirm vorne ist)", k.aufraeumen.pausen) { an -> aendere { it.copy(aufraeumen = it.aufraeumen.copy(pausen = an)) } })
        add(schalter(c, "Zum Feierabend (mit der Feierabend-Sperre)", k.aufraeumen.feierabend) { an -> aendere { it.copy(aufraeumen = it.aufraeumen.copy(feierabend = an)) } })
        auswahl("Lautstärke danach", listOf("-1" to "Nicht ändern", "20" to "20 %", "30" to "30 %", "40" to "40 %", "50" to "50 %", "60" to "60 %"), k.aufraeumen.lautstaerke.toString()) { v ->
            aendere { it.copy(aufraeumen = it.aufraeumen.copy(lautstaerke = v.toInt())) }
        }
        knoepfe(knopf(c, "Jetzt aufräumen") { a.aufraeumenJetzt() }, knopf(c, "Schnelleinstellungen …") { a.schnellOeffnen() })
    }

    private fun teilAbwesenheiten(k: Konfig) {
        abschnitt("Abwesenheiten")
        add(text(c, "Klasse dieses Boards: " + Geraet.klasse.ifBlank { "noch keine" } +
            "\nDie Kachel „Heute fehlen …“ zeigt nur die Zahl – die Kinder raten, wer fehlt.", 24, Farben.TEXT2))
        if (Geraet.klasse.isNotBlank()) add(text(c, "Jetzt: " + Abwesenheit.anzeige(Geraet.klasse).let { "${it.gross} ${it.unten}" }, 22, Farben.TEXT2), 6)
        knoepfe(knopf(c, "Klasse wählen …") { a.klasseEinstellen() })
    }

    private fun teilHintergrund(k: Konfig) {
        abschnitt("Hintergrund")
        knoepfe(
            knopf(c, "Bild wählen …") { a.bildWaehlen() },
            knopf(c, "Kein Bild") { aendere { it.copy(hintergrund = it.hintergrund.copy(bild = false, unsplash = false)) }; aufbauen() }.apply { isEnabled = k.hintergrund.bild || k.hintergrund.unsplash }
        )
        add(text(c, "Farbe:", 24, Farben.TEXT2), 14)
        add(Fluss(c).apply {
            FARBEN.forEach { f ->
                addView(View(c).apply {
                    background = runde(f, 40f.vp, if (f == k.hintergrund.farbe) 6.vp else 2.vp, if (f == k.hintergrund.farbe) Farben.AKZENT else 0xFFB8C2CE.toInt())
                    setOnClickListener { aendere { it.copy(hintergrund = it.hintergrund.copy(farbe = f, bild = false, unsplash = false)) }; aufbauen() }
                }, ViewGroup.LayoutParams(80.vp, 80.vp))
            }
        })

        val h = k.hintergrund
        add(schalter(c, "Jeden Tag ein neues Foto", h.unsplash) { an ->
            aendere { it.copy(hintergrund = it.hintergrund.copy(unsplash = an)) }
            if (an && (h.quelle == Hintergrund.COMMONS || h.schluessel.isNotEmpty() || (h.quelle == Hintergrund.SCHULE && de.oejendorferdamm.dammlauncher.abgleich.SchulOrdner.passwort() != null))) a.unsplashNeu()
            aufbauen()
        }, 18)
        if (h.unsplash) {
            add(text(c, "Quelle:", 24, Farben.TEXT2), 8)
            knoepfe(
                knopf(c, "Wikimedia Commons (frei)", haupt = h.quelle == Hintergrund.COMMONS) {
                    aendere { it.copy(hintergrund = it.hintergrund.copy(quelle = Hintergrund.COMMONS)) }
                    a.unsplashNeu()
                    aufbauen()
                },
                knopf(c, "Unsplash (Schlüssel)", haupt = h.quelle == Hintergrund.UNSPLASH) {
                    aendere { it.copy(hintergrund = it.hintergrund.copy(quelle = Hintergrund.UNSPLASH)) }
                    if (h.schluessel.isNotEmpty()) a.unsplashNeu()
                    aufbauen()
                },
                knopf(c, "Schul-Ordner", haupt = h.quelle == Hintergrund.SCHULE) {
                    aendere { it.copy(hintergrund = it.hintergrund.copy(quelle = Hintergrund.SCHULE)) }
                    if (de.oejendorferdamm.dammlauncher.abgleich.SchulOrdner.passwort() != null) a.unsplashNeu() else a.schulPasswort()
                    aufbauen()
                }
            )
            if (Geraet.unsplashMeldung.isNotEmpty()) add(text(c, "Zuletzt: " + Geraet.unsplashMeldung, 21, Farben.TEXT2), 6)
            add(schalter(c, "Aktuelles Hintergrundbild behalten (nicht mehr täglich wechseln)", Geraet.fotoBehalten) { an -> Geraet.fotoBehalten = an }, 8)
            if (h.quelle == Hintergrund.SCHULE) {
                val pw = de.oejendorferdamm.dammlauncher.abgleich.SchulOrdner.passwort() != null
                add(text(c, "Bilder aus dem Ordner DammLauncher/Wallpaper der Schul-Cloud – jeden Tag das nächste, auf allen Boards dasselbe. " +
                    "Neue Bilder einfach in den Ordner legen.", 21, Farben.TEXT2), 8)
                if (!pw) add(text(c, "Dafür einmal das Passwort des Schul-Ordners eingeben.", 22, Farben.ROT), 8)
                knoepfe(knopf(c, if (pw) "Passwort ändern …" else "Passwort eingeben …", haupt = !pw) { a.schulPasswort() },
                    knopf(c, "Neues Foto jetzt") { a.unsplashNeu() }.apply { isEnabled = pw })
            } else if (h.quelle == Hintergrund.COMMONS) {
                add(text(c, "Ausgezeichnete Landschaftsfotos von Wikimedia Commons – frei lizenziert, ohne Anmeldung und Schlüssel. " +
                    "Fotograf und Lizenz stehen klein unten rechts.", 21, Farben.TEXT2), 8)
                knoepfe(knopf(c, "Neues Foto jetzt") { a.unsplashNeu() })
            } else {
                val thema = eingabe(c, "Thema (englisch), z. B. landscape, mountains, sea", h.thema)
                val schluessel = eingabe(c, if (h.schluessel.isEmpty()) "Unsplash Access Key" else "Access Key gespeichert – leer lassen = behalten")
                add(text(c, "Den kostenlosen Access Key gibt es unter unsplash.com/developers → „New Application“. Er geht mit der Vorlage an alle Geräte.", 21, Farben.TEXT2), 8)
                add(thema, 10)
                add(schluessel, 10)
                // Taste „Fertig“ auf der Bildschirmtastatur schließt sie (sonst verdeckt sie die Knöpfe)
                schluessel.imeOptions = schluessel.imeOptions or android.view.inputmethod.EditorInfo.IME_ACTION_DONE
                knoepfe(
                    knopf(c, "Speichern") {
                        val t = thema.text.toString().trim().ifEmpty { Hintergrund.STANDARD_THEMA }
                        val sch = schluessel.text.toString().trim()
                        when {
                            !Hintergrund.THEMA.matches(t) -> a.meldung("Thema: nur Buchstaben, Ziffern, Leerzeichen und Kommas (bis 40 Zeichen).")
                            sch.isNotEmpty() && !Hintergrund.SCHLUESSEL.matches(sch) -> a.meldung("Das sieht nicht wie ein Unsplash Access Key aus.")
                            else -> {
                                aendere { it.copy(hintergrund = it.hintergrund.copy(thema = t, schluessel = sch.ifEmpty { it.hintergrund.schluessel })) }
                                a.unsplashNeu()
                            }
                        }
                    },
                    knopf(c, "Neues Foto jetzt", haupt = true) { a.unsplashNeu() }.apply { isEnabled = h.schluessel.isNotEmpty() }
                )
            }
        }
    }

    private fun teilApps(k: Konfig) {
        abschnitt("Apps")
        val versteckt = k.ausgeblendet.mapNotNull { sch -> AppKatalog.apps.firstOrNull { AppKatalog.schluessel(it.komponente) == sch } }
        add(text(c, if (versteckt.isEmpty()) "In der App-Übersicht ist nichts ausgeblendet. Ausblenden: dort lange auf eine App drücken."
            else "Ausgeblendet (antippen = wieder einblenden):", 22, Farben.TEXT2), 8)
        versteckt.forEach { a ->
            knoepfe(knopf(c, "👁  ${a.name}") {
                aendere { it.copy(ausgeblendet = it.ausgeblendet - AppKatalog.schluessel(a.komponente)) }
                aufbauen()
            })
        }
        if (Geraet.starts().isNotEmpty()) knoepfe(knopf(c, "„Häufig benutzt“ zurücksetzen") { Geraet.startsVergessen(); a.meldung("Zurückgesetzt.") })
    }

    private fun teilWidgetsundAblage(k: Konfig) {
        abschnitt("Widgets und Ablage")
        knoepfe(knopf(c, "Widget hinzufügen …") { a.widgetHinzufuegen() })
        if (k.nichtPlatziert.isNotEmpty()) {
            add(text(c, "${k.nichtPlatziert.size} Elemente haben gerade keinen Platz (Raster zu klein).", 24, Farben.ROT))
            knoepfe(
                knopf(c, "Wieder einsetzen") {
                    aendere { val (p, o) = Belegung.anpassen(it.elemente, it.nichtPlatziert, it.spalten, it.zeilen); it.copy(elemente = p, nichtPlatziert = o) }
                    aufbauen()
                },
                knopf(c, "Verwerfen", gefahr = true) { aendere { it.copy(nichtPlatziert = emptyList()) }; aufbauen() }
            )
        }
    }

    private fun teilDatei(k: Konfig) {
        abschnitt("Datei")
        knoepfe(knopf(c, "Einrichtung exportieren …") { a.exportieren() }, knopf(c, "Einrichtung importieren …") { a.importieren() })
        knoepfe(knopf(c, "Vorlage der Schule laden …") { a.schulVorlage() })
    }

    private fun teilZentraleVerteilungIServ(k: Konfig) {
        abschnitt("Zentrale Verteilung (IServ)")
        val boss = a.bossAktiv()
        val stand = Geraet.vorlageStand
        add(text(c, "Vorlage: " + (if (stand > 0) SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMAN).format(Date(stand)) else "noch keine") +
            "\nLetzter Abgleich: " + Geraet.abgleichMeldung.ifBlank { "–" } +
            "\nAutomatischer Abgleich: " + (if (Geraet.abgleichAn) "an (täglich)" else "aus"), 24, Farben.TEXT2))
        val name = eingabe(c, "Name dieses Geräts (z. B. Raum 2b)", Geraet.name)
        add(name, 12)
        knoepfe(
            knopf(c, "Namen speichern") { Geraet.name = name.text.toString(); a.meldung("Gerätename gespeichert.") },
            knopf(c, "Jetzt synchronisieren", haupt = true) { a.jetztAbgleichen() }
        )
        if (boss) {
            add(text(c, "Boss-Modus: Zugang dieses Geräts (nur LESE-Rechte eintragen)", 26, fett = true), 20)
            val server = eingabe(c, "Server, z. B. https://schule.de/webdav/", Geraet.server)
            val ordner = eingabe(c, "Ordner, z. B. Files/DammLauncher", Geraet.ordner)
            val benutzer = eingabe(c, "Benutzername (Lesezugang)")
            val passwort = eingabe(c, "Passwort (Lesezugang)", geheim = true)
            listOf(server, ordner, benutzer, passwort).forEach { add(it, 10) }
            add(text(c, "Benutzername und Passwort werden verschlüsselt gespeichert und nie angezeigt. Leer lassen = bisherige behalten.", 22, Farben.TEXT2))
            knoepfe(knopf(c, "Zugang speichern") {
                a.zugangSpeichern(server.text.toString(), ordner.text.toString(), benutzer.text.toString(), passwort.text.toString())
                passwort.setText("")
            })
            add(schalter(c, "Automatisch täglich abgleichen", Geraet.abgleichAn) { an -> Geraet.abgleichAn = an })
            add(text(c, "Tafel-Übersicht – Bericht dieses Boards: " + (if (!de.oejendorferdamm.dammlauncher.abgleich.Status.hatZugang()) "kein Status-Zugang" else Geraet.statusMeldung.ifBlank { "noch nicht gesendet" }), 22, Farben.TEXT2), 16)
            knoepfe(knopf(c, "Status-Zugang …") { a.statusZugangEinstellen() }, knopf(c, "Tafel-Übersicht …", haupt = true) { a.tafelnZeigen() })
            knoepfe(knopf(c, "Als Vorlage für alle Geräte hochladen", haupt = true) { a.hochladen() })
            knoepfe(knopf(c, "Boss-Modus beenden", gefahr = true) { a.bossAbmelden() })
        } else {
            knoepfe(knopf(c, "Boss-Modus …") { a.bossAnmelden() })
        }
    }

    private fun teilUpdates(k: Konfig) {
        abschnitt("Updates")
        add(text(c, "Installiert: ${BuildConfig.VERSION_NAME}\n" + a.updateText(), 24, Farben.TEXT2))
        add(schalter(c, "Updates automatisch installieren", Geraet.autoUpdate) { an -> Geraet.autoUpdate = an })
        knoepfe(knopf(c, "Jetzt nach Updates suchen") { a.updatePruefen() }, knopf(c, "Damm-Apps in F-Droid hinzufügen") { if (!oeffneFDroidRepo(c)) a.meldung("Weder F-Droid noch ein Browser ist installiert.") })
        add(text(c,
            "Automatisch: Neue Versionen werden im Hintergrund geladen, geprüft und installiert, sobald der Startbildschirm unbenutzt vorne ist " +
                "(bis Android 11 fragt Android dabei einmal „Installieren?“).\n" +
                "Sicherheit: Ist eine neuere Version schon länger als 7 Tage veröffentlicht, sperrt sich der Launcher, bis aktualisiert ist. " +
                "Ist GitHub nicht erreichbar, wird nie gesperrt.", 21, Farben.TEXT2))
    }

    private fun teilUeberDammLauncher(k: Konfig) {
        abschnitt("Über DammLauncher")
        add(text(c, "Freie Software unter der GNU GPL v3.\nQuelltext: github.com/Teyro/DammLauncher\n" +
            "Schrift: Grundschrift von Christian Urff (www.urff.info), Lizenz CC BY 3.0.\n" +
            "Fotos des Tages: Wikimedia Commons, Unsplash bzw. der Schul-Ordner – Fotograf und Lizenz stehen unten rechts.", 22, Farben.TEXT2))
    }


    /**
     * Auswahl als Reihe von Knöpfen (die gewählte Möglichkeit blau). [schriftVorschau]: jeder Knopf
     * in seiner eigenen Schrift.
     */
    private fun auswahl(titel: String, moeglich: List<Pair<String, String>>, wert: String, schriftVorschau: Boolean = false, setze: (String) -> Unit) {
        add(text(c, titel, 22, Farben.TEXT2), 14)
        add(android.widget.HorizontalScrollView(c).apply {
            isHorizontalScrollBarEnabled = false
            addView(LinearLayout(c).apply {
                moeglich.forEach { (schluessel, name) ->
                    addView(knopf(c, name, haupt = schluessel == wert) { setze(schluessel); aufbauen() }.apply {
                        if (schriftVorschau) typeface = Schriften.von(c, schluessel, fettFallsSystem = true)
                    }, lp(rechts = 12.vp))
                }
            })
        }, 6)
    }

    /** Raster ändern – was nicht mehr passt, rückt in freie Zellen oder in die Ablage. */
    private fun aendereRaster(spalten: Int?, zeilen: Int?) = aendere {
        val s = spalten ?: it.spalten
        val z = zeilen ?: it.zeilen
        val (p, o) = Belegung.anpassen(it.elemente, it.nichtPlatziert, s, z)
        it.copy(spalten = s, zeilen = z, elemente = p, nichtPlatziert = o)
    }

    companion object {
        val FARBEN = intArrayOf(
            0xFF1E3A5F.toInt(), 0xFF0B4F6C.toInt(), 0xFF1B5E20.toInt(), 0xFF4A148C.toInt(), 0xFF7A1F1F.toInt(),
            0xFF263238.toInt(), 0xFF000000.toInt(), 0xFF5D4037.toInt(), 0xFF00695C.toInt(), 0xFFECEFF1.toInt()
        )
    }

}
