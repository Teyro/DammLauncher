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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Was das Panel auslöst, aber die Activity erledigt (Dateiauswahl, Dialoge, Netz). */
interface PanelAktionen {
    fun bildWaehlen()
    fun widgetHinzufuegen()
    fun exportieren()
    fun importieren()
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
    private var lage = 1 // 0 links, 1 Mitte, 2 rechts
    private var raenderGleich = KonfigSpeicher.konfig.rand.gleich

    init {
        karte.addView(LinearLayout(c).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(text(c, "Einstellungen", 40, fett = true), lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(knopf(c, "◀") { lage = (lage - 1).coerceAtLeast(0); setzeLage() }.apply { background = rundKnopf() }, lp(86.vp, 86.vp, rechts = 10.vp))
            addView(knopf(c, "▶") { lage = (lage + 1).coerceAtMost(2); setzeLage() }.apply { background = rundKnopf() }, lp(86.vp, 86.vp, rechts = 20.vp))
            addView(knopf(c, "Fertig", haupt = true) { a.panelZu() }, lp())
        })
        karte.addView(scroll, lp(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f, oben = 16.vp))
        ebene.addView(karte)
        setzeLage()
        aufbauen()
        wurzel.addView(ebene, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun setzeLage() {
        val m = c.resources.displayMetrics
        val breite = minOf(880.vp, (m.widthPixels * 0.9f).toInt())
        karte.layoutParams = FrameLayout.LayoutParams(breite, ViewGroup.LayoutParams.MATCH_PARENT, when (lage) {
            0 -> Gravity.START
            2 -> Gravity.END
            else -> Gravity.CENTER_HORIZONTAL
        }).apply { setMargins(30.vp, 30.vp, 30.vp, 30.vp) }
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

    private fun knoepfe(vararg k: View) = add(LinearLayout(c).apply {
        orientation = LinearLayout.HORIZONTAL
        k.forEach { addView(it, lp(rechts = 14.vp)) }
    }, 12)

    /** Inhalt (neu) aufbauen – z. B. nach Boss-Anmeldung oder „Ränder gleich“. */
    fun aufbauen() {
        val pos = scroll.scrollY
        inhalt.removeAllViews()
        aktuelleKarte = null
        val k = KonfigSpeicher.konfig

        abschnitt("Raster")
        add(regler(c, "Spalten", Grenzen.RASTER, k.spalten) { n -> aendereRaster(n, null) })
        add(regler(c, "Zeilen", Grenzen.RASTER, k.zeilen) { n -> aendereRaster(null, n) })

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

        abschnitt("Symbole und Schrift")
        add(regler(c, "Symbolgröße", Grenzen.ICON, k.iconProzent, 5, " %") { n -> aendere { it.copy(iconProzent = n) } })
        add(schalter(c, "Namen unter den Symbolen", k.beschriftung) { an -> aendere { it.copy(beschriftung = an) } })
        add(regler(c, "Schriftgröße", Grenzen.TEXT, k.textGroesse) { n -> aendere { it.copy(textGroesse = n) } })
        auswahl("Schrift der App-Namen", Schrift.ALLE.map { it to Schriften.name(it) }, k.schrift, schriftVorschau = true) { s -> aendere { it.copy(schrift = s) } }

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

        abschnitt("Bedienung")
        add(schalter(c, "Knopf „Alle Apps“ unten anzeigen", k.alleAppsKnopf) { an -> aendere { it.copy(alleAppsKnopf = an) } })
        add(schalter(c, "Animationen", k.animationen) { an -> aendere { it.copy(animationen = an) } })
        add(schalter(c, "Startbildschirm gegen Verändern sperren", k.bearbeitenGesperrt) { an -> aendere { it.copy(bearbeitenGesperrt = an) } })
        knoepfe(
            knopf(c, if (k.pin == null) "PIN für Einstellungen festlegen …" else "PIN ändern …") { a.pinFestlegen() },
            knopf(c, "PIN entfernen", gefahr = k.pin != null) { aendere { it.copy(pin = null) }; aufbauen() }.apply { isEnabled = k.pin != null }
        )

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

        abschnitt("Schulzeiten")
        add(text(c, "Pausen: " + (if (k.pausen.isEmpty()) "noch keine" else k.pausen.joinToString(", ") { "${Zeitraum.uhrzeit(it.von)}–${Zeitraum.uhrzeit(it.bis)}" }), 24, Farben.TEXT2))
        knoepfe(knopf(c, "Pausenzeiten …") { a.pausenEinstellen() })
        val fa = k.feierabend
        add(text(c, "Feierabend-Sperre: " + (if (!fa.an) "aus" else "${Zeitraum.uhrzeit(fa.ab)} bis ${Zeitraum.uhrzeit(fa.bis)}" + if (fa.wochenende) ", Wochenende gesperrt" else ""), 24, Farben.TEXT2), 18)
        knoepfe(knopf(c, "Feierabend-Sperre …") { a.feierabendEinstellen() })
        auswahl("Bildschirmschoner (dunkel, Uhr und Foto) nach", listOf("0" to "Aus", "5" to "5 min", "10" to "10 min", "15" to "15 min", "30" to "30 min", "60" to "1 Stunde"), k.schoner.toString()) { v ->
            aendere { it.copy(schoner = v.toInt()) }
        }

        abschnitt("Hinweis-Band")
        val hw = k.hinweis
        add(text(c, if (hw == null) "Kein Hinweis." else "„${hw.text}“ – bis ${hw.bis.split('-').reversed().joinToString(".")}" +
            (if (!Zeiten.hinweisAktiv(hw, java.util.Calendar.getInstance())) " (abgelaufen)" else ""), 24, Farben.TEXT2))
        knoepfe(knopf(c, if (hw == null) "Hinweis schreiben …" else "Hinweis ändern …") { a.hinweisEinstellen() })

        abschnitt("Abwesenheiten")
        add(text(c, "Klasse dieses Boards: " + Geraet.klasse.ifBlank { "noch keine" } +
            "\nDie Kachel „Heute fehlen …“ zeigt nur die Zahl – die Kinder raten, wer fehlt.", 24, Farben.TEXT2))
        if (Geraet.klasse.isNotBlank()) add(text(c, "Jetzt: " + Abwesenheit.anzeige(Geraet.klasse).let { "${it.gross} ${it.unten}" }, 22, Farben.TEXT2), 6)
        knoepfe(knopf(c, "Klasse wählen …") { a.klasseEinstellen() })

        abschnitt("Hintergrund")
        knoepfe(
            knopf(c, "Bild wählen …") { a.bildWaehlen() },
            knopf(c, "Kein Bild") { aendere { it.copy(hintergrund = it.hintergrund.copy(bild = false, unsplash = false)) }; aufbauen() }.apply { isEnabled = k.hintergrund.bild || k.hintergrund.unsplash }
        )
        add(text(c, "Farbe:", 24, Farben.TEXT2), 14)
        add(LinearLayout(c).apply {
            FARBEN.forEach { f ->
                addView(View(c).apply {
                    background = runde(f, 40f.vp, if (f == k.hintergrund.farbe) 6.vp else 2.vp, if (f == k.hintergrund.farbe) Farben.AKZENT else 0xFFB8C2CE.toInt())
                    setOnClickListener { aendere { it.copy(hintergrund = it.hintergrund.copy(farbe = f, bild = false, unsplash = false)) }; aufbauen() }
                }, lp(80.vp, 80.vp, rechts = 14.vp))
            }
        })

        val h = k.hintergrund
        add(schalter(c, "Jeden Tag ein neues Landschaftsfoto", h.unsplash) { an ->
            aendere { it.copy(hintergrund = it.hintergrund.copy(unsplash = an)) }
            if (an && (h.quelle == Hintergrund.COMMONS || h.schluessel.isNotEmpty())) a.unsplashNeu()
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
                }
            )
            if (Geraet.unsplashMeldung.isNotEmpty()) add(text(c, "Zuletzt: " + Geraet.unsplashMeldung, 21, Farben.TEXT2), 6)
            if (h.quelle == Hintergrund.COMMONS) {
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

        abschnitt("Datei")
        knoepfe(knopf(c, "Einrichtung exportieren …") { a.exportieren() }, knopf(c, "Einrichtung importieren …") { a.importieren() })

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
            knoepfe(knopf(c, "Als Vorlage für alle Geräte hochladen", haupt = true) { a.hochladen() })
            knoepfe(knopf(c, "Boss-Modus beenden", gefahr = true) { a.bossAbmelden() })
        } else {
            knoepfe(knopf(c, "Boss-Modus …") { a.bossAnmelden() })
        }

        abschnitt("Updates")
        add(text(c, "Installiert: ${BuildConfig.VERSION_NAME}\n" + a.updateText(), 24, Farben.TEXT2))
        add(schalter(c, "Updates automatisch installieren", Geraet.autoUpdate) { an -> Geraet.autoUpdate = an })
        knoepfe(knopf(c, "Jetzt nach Updates suchen") { a.updatePruefen() })
        add(text(c,
            "Automatisch: Neue Versionen werden im Hintergrund geladen, geprüft und installiert, sobald der Startbildschirm unbenutzt vorne ist " +
                "(bis Android 11 fragt Android dabei einmal „Installieren?“).\n" +
                "Sicherheit: Ist eine neuere Version schon länger als 7 Tage veröffentlicht, sperrt sich der Launcher, bis aktualisiert ist. " +
                "Ist GitHub nicht erreichbar, wird nie gesperrt.", 21, Farben.TEXT2))

        abschnitt("Über DammLauncher")
        add(text(c, "Freie Software unter der GNU GPL v3.\nQuelltext: github.com/Teyro/DammLauncher\n" +
            "Schrift: Grundschrift von Christian Urff (www.urff.info), Lizenz CC BY 3.0.\n" +
            "Fotos des Tages: Wikimedia Commons bzw. Unsplash – Fotograf und Lizenz stehen unten rechts.", 22, Farben.TEXT2))
        aktuelleKarte = null
        add(View(c), 40)
        scroll.post { scroll.scrollTo(0, pos); karte.requestFocus() }
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
