/*
 * DammLauncher – Copyright (C) 2026 Teyro – GNU GPL v3 oder neuer, siehe LICENSE
 */
package de.oejendorferdamm.dammlauncher.nutzung

import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import de.oejendorferdamm.dammlauncher.BuildConfig
import de.oejendorferdamm.dammlauncher.HomeActivity
import de.oejendorferdamm.dammlauncher.konfig.Geraet
import de.oejendorferdamm.dammlauncher.ui.Farben
import de.oejendorferdamm.dammlauncher.ui.Fluss
import de.oejendorferdamm.dammlauncher.ui.chip
import de.oejendorferdamm.dammlauncher.ui.karte
import de.oejendorferdamm.dammlauncher.ui.knopf
import de.oejendorferdamm.dammlauncher.ui.lp
import de.oejendorferdamm.dammlauncher.ui.schalter
import de.oejendorferdamm.dammlauncher.ui.text
import de.oejendorferdamm.dammlauncher.ui.vp
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Name einer App für die Statistik (fehlt sie inzwischen: Paketname) */
fun appName(c: Context, paket: String): String = try {
    c.packageManager.getApplicationLabel(c.packageManager.getApplicationInfo(paket, 0)).toString()
} catch (_: Exception) { paket }

/** Statistik-Fenster: 7 oder 14 Tage, Kennzahlen, Zeit je App, Tagesverlauf, ein Tag im Detail, Export. */
internal fun HomeActivity.nutzungZeigen(anzahl: Int = 7, tagIndex: Int = 0) {
    val jetzt = System.currentTimeMillis()
    val tage = Auswertung.tage(Nutzung.sitzungen(jetzt - (anzahl + 1) * 24 * 60 * 60_000L), jetzt, anzahl)
    val jeApp = Auswertung.jeApp(tage)
    val starts = Auswertung.starts(tage)
    val plaetze = Auswertung.farbPlaetze(jeApp)
    val namen = HashMap<String, String>()
    fun name(p: String) = namen.getOrPut(p) { appName(this, p) }
    val f = neuesFenster("Nutzungsstatistik · ${Geraet.name.ifBlank { "dieses Board" }}", 1640)

    f.add(text(this, "Nur auf diesem Board gespeichert: wann der Bildschirm an war und welche App gestartet wurde – keine Namen, keine Inhalte, " +
        "nach ${Nutzung.AUFBEWAHREN_TAGE} Tagen gelöscht. Nach außen meldet das Board höchstens „an“ und „eine App ist offen“.", 21, Farben.TEXT2), 0)
    f.add(LinearLayout(this).apply {
        addView(chip(context, "Letzte 7 Tage", anzahl == 7) { f.schliessen(); nutzungZeigen(7) }, lp(rechts = 12.vp))
        addView(chip(context, "Letzte 14 Tage", anzahl == 14) { f.schliessen(); nutzungZeigen(14) }, lp(rechts = 12.vp))
    }, 18)

    // --- Kennzahlen
    val genutzt = tage.filter { !it.leer }
    val kacheln = Fluss(this, 16.vp)
    fun kachel(titel: String, wert: String, hinweis: String) = karte(this).apply {
        addView(text(context, titel, 20, Farben.TEXT2))
        addView(text(context, wert, 38, fett = true).apply { maxLines = 1 }, lp(oben = 4.vp))
        addView(text(context, hinweis, 18, Farben.TEXT2), lp(oben = 2.vp))
    }
    kacheln.addView(kachel("Bildschirm an", Auswertung.dauer(tage.sumOf { it.anMs }), "an ${genutzt.size} von $anzahl Tagen"), ViewGroup.LayoutParams(360.vp, ViewGroup.LayoutParams.WRAP_CONTENT))
    kacheln.addView(kachel("Ø je genutztem Tag", Auswertung.dauer(if (genutzt.isEmpty()) 0 else genutzt.sumOf { it.anMs } / genutzt.size), "Bildschirm an"), ViewGroup.LayoutParams(360.vp, ViewGroup.LayoutParams.WRAP_CONTENT))
    kacheln.addView(kachel("Apps offen", Auswertung.dauer(tage.sumOf { it.appMs }), "${starts.values.sum()} Starts"), ViewGroup.LayoutParams(360.vp, ViewGroup.LayoutParams.WRAP_CONTENT))
    val abends = Auswertung.abendsAn(tage)
    kacheln.addView(kachel("Abends noch an", "$abends ${if (abends == 1) "Tag" else "Tage"}", "nach ${Auswertung.ABEND_STUNDE} Uhr").apply {
        if (abends > 0) (getChildAt(1) as? android.widget.TextView)?.setTextColor(Farben.ROT)
    }, ViewGroup.LayoutParams(360.vp, ViewGroup.LayoutParams.WRAP_CONTENT))
    f.add(kacheln, 16)

    // --- Zeit je App
    val zeilen = jeApp.take(7).map { (p, ms) -> NutzungsGrafik.Zeile(name(p), ms, NutzungsGrafik.farbe(plaetze, p), starts[p] ?: 0) } +
        jeApp.drop(7).let { rest -> if (rest.isEmpty()) emptyList() else listOf(NutzungsGrafik.Zeile("Andere (${rest.size})", rest.sumOf { it.second }, NutzungsGrafik.ANDERE, rest.sumOf { starts[it.first] ?: 0 })) }
    val s = de.oejendorferdamm.dammlauncher.ui.Masse.faktor
    f.add(karte(this).apply {
        addView(text(context, "Zeit je App", 28, fett = true))
        addView(GrafikAnsicht(context, { NutzungsGrafik.balkenHoehe(zeilen.size, s) }) { c, b -> NutzungsGrafik.balken(c, b, zeilen, s) }, lp(ViewGroup.LayoutParams.MATCH_PARENT, oben = 14.vp))
    }, 18)

    // --- Tagesverlauf
    f.add(karte(this).apply {
        addView(text(context, "Tagesverlauf", 28, fett = true))
        addView(Fluss(context, 22.vp).apply {
            fun eintrag(farbe: Int, t: String) = LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(android.view.View(context).apply { background = de.oejendorferdamm.dammlauncher.ui.runde(farbe, 6f.vp) }, lp(22.vp, 22.vp, rechts = 8.vp))
                addView(text(context, t, 20, Farben.TEXT2))
            }
            addView(eintrag(NutzungsGrafik.NUR_AN, "Bildschirm an (Startbildschirm)"))
            zeilen.forEach { z -> addView(eintrag(z.farbe, z.name)) }
        }, lp(ViewGroup.LayoutParams.MATCH_PARENT, oben = 10.vp))
        addView(GrafikAnsicht(context, { NutzungsGrafik.zeitleisteHoehe(tage.size, s) }) { c, b -> NutzungsGrafik.zeitleiste(c, b, tage, plaetze, s) }, lp(ViewGroup.LayoutParams.MATCH_PARENT, oben = 16.vp))
    }, 18)

    // --- Ein Tag im Detail
    val uhr = SimpleDateFormat("HH:mm", Locale.GERMAN)
    val kurz = SimpleDateFormat("EE dd.MM.", Locale.GERMAN)
    val gewaehlt = tage.getOrNull(tagIndex) ?: tage.first()
    f.add(karte(this).apply {
        addView(text(context, "Tag im Detail", 28, fett = true))
        addView(android.widget.HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(LinearLayout(context).apply {
                tage.forEachIndexed { i, t ->
                    addView(chip(context, kurz.format(Date(t.beginn)), t === gewaehlt) { f.schliessen(); nutzungZeigen(anzahl, i) }.apply { alpha = if (t.leer) 0.5f else 1f }, lp(rechts = 10.vp))
                }
            })
        }, lp(oben = 12.vp))
        if (gewaehlt.leer) addView(text(context, "An diesem Tag war das Board nicht an (oder es wurde nichts aufgezeichnet).", 22, Farben.TEXT2), lp(oben = 14.vp))
        else {
            addView(text(context, "Bildschirm an: " + gewaehlt.bildschirm.joinToString(", ") { "${uhr.format(Date(it.start))}–${uhr.format(Date(it.ende))}" } +
                " · zusammen ${Auswertung.dauer(gewaehlt.anMs)}", 22, Farben.TEXT2), lp(oben = 14.vp))
            if (gewaehlt.apps.isEmpty()) addView(text(context, "Keine App gestartet.", 22, Farben.TEXT2), lp(oben = 8.vp))
            gewaehlt.apps.forEach { a ->
                addView(LinearLayout(context).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, 8.vp, 0, 8.vp)
                    addView(text(context, "${uhr.format(Date(a.start))}–${uhr.format(Date(a.ende))}", 24, fett = true), lp(200.vp))
                    addView(android.view.View(context).apply { background = de.oejendorferdamm.dammlauncher.ui.runde(NutzungsGrafik.farbe(plaetze, a.paket), 9f.vp) }, lp(18.vp, 18.vp, rechts = 12.vp))
                    addView(text(context, name(a.paket), 24), lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    addView(text(context, Auswertung.dauer(a.dauer), 22, Farben.TEXT2))
                }, lp(ViewGroup.LayoutParams.MATCH_PARENT))
            }
        }
    }, 18)

    // --- Strom: wann das Board ohne Herunterfahren aus war (Stecker gezogen)
    val ausfaelle = Strom.liste(this).takeLast(20).reversed()
    val datumUhr = SimpleDateFormat("EE dd.MM. HH:mm", Locale.GERMAN)
    f.add(karte(this).apply {
        addView(text(context, "Ausgeschaltet ohne Herunterfahren", 28, fett = true))
        addView(text(context, "Stecker gezogen oder Strom weg – auf etwa eine Minute genau. Normales Ausschalten steht grau dabei.", 22, Farben.TEXT2), lp(oben = 6.vp))
        if (ausfaelle.isEmpty()) addView(text(context, "Bisher keine.", 22, Farben.TEXT2), lp(oben = 10.vp))
        ausfaelle.forEach { e ->
            addView(text(context, "${datumUhr.format(Date(e.von))} bis ${datumUhr.format(Date(e.bis))}" + if (e.sauber) " · heruntergefahren" else " · ohne Herunterfahren",
                24, if (e.sauber) Farben.TEXT2 else Farben.TEXT, fett = !e.sauber), lp(oben = 8.vp))
        }
    }, 18)

    // --- Export, Löschen, Aufzeichnen
    f.add(schalter(this, "Nutzung auf diesem Board aufzeichnen", Nutzung.an) { an -> Nutzung.an = an }, 16)
    f.knoepfe(
        knopf(this, "Statistik löschen …") {
            f.schliessen()
            val g = neuesFenster("Statistik löschen?", 900)
            g.add(text(this, "Alle aufgezeichneten Zeiten dieses Boards werden gelöscht.", 24))
            g.knoepfe(knopf(this, "Abbrechen") { g.schliessen() }, knopf(this, "Löschen", gefahr = true) { Nutzung.loeschen(); g.schliessen(); meldung("Statistik gelöscht.") })
        },
        knopf(this, "Exportieren (USB-Stick) …", haupt = true) { nutzungExportieren(anzahl) }
    )
}

/** Bericht als .odt erzeugen (Grafiken in fester Größe, unabhängig vom Bildschirm) */
internal fun HomeActivity.nutzungBericht(anzahl: Int): ByteArray {
    val jetzt = System.currentTimeMillis()
    val tage = Auswertung.tage(Nutzung.sitzungen(jetzt - (anzahl + 1) * 24 * 60 * 60_000L), jetzt, anzahl)
    val jeApp = Auswertung.jeApp(tage)
    val starts = Auswertung.starts(tage)
    val plaetze = Auswertung.farbPlaetze(jeApp)
    val namen = HashMap<String, String>()
    fun name(p: String) = namen.getOrPut(p) { appName(this, p) }
    val zeilen = jeApp.take(7).map { (p, ms) -> NutzungsGrafik.Zeile(name(p), ms, NutzungsGrafik.farbe(plaetze, p), starts[p] ?: 0) } +
        jeApp.drop(7).let { rest -> if (rest.isEmpty()) emptyList() else listOf(NutzungsGrafik.Zeile("Andere (${rest.size})", rest.sumOf { it.second }, NutzungsGrafik.ANDERE, rest.sumOf { starts[it.first] ?: 0 })) }
    val s = 1.6f
    val breite = 1700
    fun png(b: android.graphics.Bitmap, n: String) = OdtBericht.Bild(n, ByteArrayOutputStream().also { b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray(), b.width, b.height).also { b.recycle() }
    val balken = png(NutzungsGrafik.alsBild(breite, NutzungsGrafik.balkenHoehe(zeilen.size, s)) { NutzungsGrafik.balken(it, breite, zeilen, s) }, "zeit_je_app.png")
    val leiste = png(NutzungsGrafik.alsBild(breite, NutzungsGrafik.zeitleisteHoehe(tage.size, s)) { NutzungsGrafik.zeitleiste(it, breite, tage, plaetze, s) }, "tagesverlauf.png")
    return OdtBericht.erzeuge(OdtBericht.Daten(Geraet.name.ifBlank { "Board" }, BuildConfig.VERSION_NAME, tage, ::name, balken, leiste))
}

fun nutzungDateiname(): String {
    val name = Geraet.name.ifBlank { "Board" }.replace(Regex("[^A-Za-z0-9ÄÖÜäöüß_-]+"), "_").take(40)
    return "Nutzung_${name}_${SimpleDateFormat("yyyy-MM-dd", Locale.GERMAN).format(Date())}.odt"
}
