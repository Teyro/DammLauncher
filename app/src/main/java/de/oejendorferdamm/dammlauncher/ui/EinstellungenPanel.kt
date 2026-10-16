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
import de.oejendorferdamm.dammlauncher.konfig.Grenzen
import de.oejendorferdamm.dammlauncher.konfig.Hintergrund
import de.oejendorferdamm.dammlauncher.konfig.Konfig
import de.oejendorferdamm.dammlauncher.konfig.KonfigSpeicher
import de.oejendorferdamm.dammlauncher.raster.Belegung
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
    fun updateText(): String
    fun meldung(text: String)
    fun panelZu()
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

        abschnitt("Bedienung")
        add(schalter(c, "Knopf „Alle Apps“ unten anzeigen", k.alleAppsKnopf) { an -> aendere { it.copy(alleAppsKnopf = an) } })
        add(schalter(c, "Animationen", k.animationen) { an -> aendere { it.copy(animationen = an) } })
        add(schalter(c, "Startbildschirm gegen Verändern sperren", k.bearbeitenGesperrt) { an -> aendere { it.copy(bearbeitenGesperrt = an) } })
        knoepfe(
            knopf(c, if (k.pin == null) "PIN für Einstellungen festlegen …" else "PIN ändern …") { a.pinFestlegen() },
            knopf(c, "PIN entfernen", gefahr = k.pin != null) { aendere { it.copy(pin = null) }; aufbauen() }.apply { isEnabled = k.pin != null }
        )

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
        add(text(c, "Freie Software unter der GNU GPL v3.\nQuelltext: github.com/Teyro/DammLauncher", 22, Farben.TEXT2))
        aktuelleKarte = null
        add(View(c), 40)
        scroll.post { scroll.scrollTo(0, pos); karte.requestFocus() }
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
