/*
 * DammLauncher – Copyright (C) 2026 Teyro – GNU GPL v3 oder neuer, siehe LICENSE
 */
package de.oejendorferdamm.dammlauncher

import android.content.ComponentName
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import de.oejendorferdamm.dammlauncher.abgleich.Abgleich
import de.oejendorferdamm.dammlauncher.abgleich.Status
import de.oejendorferdamm.dammlauncher.abgleich.WebDav
import de.oejendorferdamm.dammlauncher.apps.AppKatalog
import de.oejendorferdamm.dammlauncher.gesten.RandGesten
import de.oejendorferdamm.dammlauncher.konfig.Element
import de.oejendorferdamm.dammlauncher.konfig.Geraet
import de.oejendorferdamm.dammlauncher.konfig.Grenzen
import de.oejendorferdamm.dammlauncher.konfig.KonfigSpeicher
import de.oejendorferdamm.dammlauncher.konfig.Nachricht
import de.oejendorferdamm.dammlauncher.konfig.Profil
import de.oejendorferdamm.dammlauncher.konfig.Profile
import de.oejendorferdamm.dammlauncher.konfig.Seitenleiste
import de.oejendorferdamm.dammlauncher.konfig.Zeiten
import de.oejendorferdamm.dammlauncher.konfig.Zeitraum
import de.oejendorferdamm.dammlauncher.pflege.Aufraeumen
import de.oejendorferdamm.dammlauncher.ui.AppUebersicht
import de.oejendorferdamm.dammlauncher.ui.Farben
import de.oejendorferdamm.dammlauncher.ui.eingabe
import de.oejendorferdamm.dammlauncher.ui.knopf
import de.oejendorferdamm.dammlauncher.ui.lp
import de.oejendorferdamm.dammlauncher.ui.regler
import de.oejendorferdamm.dammlauncher.ui.schalter
import de.oejendorferdamm.dammlauncher.ui.text
import de.oejendorferdamm.dammlauncher.ui.vp
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/*
 * Funktionen von 1.6.0, ausgelagert, damit HomeActivity.kt überschaubar bleibt: Schnelleinstellungen,
 * Abdecken, Aufräumen, Dock und Seitenleiste, Stundenplan, Nachrichten, Profile, Zufall, Bildschirm
 * teilen und die Tafel-Übersicht.
 */

private fun HomeActivity.widget(kennung: String) =
    KonfigSpeicher.konfig.elemente.firstOrNull { it is Element.Widget && it.kennung == kennung } as? Element.Widget

private fun HomeActivity.widgetText(kennung: String, text: String) = KonfigSpeicher.aendere { k ->
    k.copy(elemente = k.elemente.map { if (it is Element.Widget && it.kennung == kennung) it.copy(text = text) else it })
}

/** Auswahl einer App über die große App-Übersicht. */
private fun HomeActivity.appAuswaehlen(titel: String, weiter: (Element.App) -> Unit) {
    lateinit var auswahl: AppUebersicht
    auswahl = AppUebersicht(this, { _, _ -> }, {}, { wurzel.removeView(auswahl) }, titel = titel, auswahl = { app ->
        wurzel.removeView(auswahl)
        weiter(Element.App(app.paket, app.aktivitaet, 0, 0))
    })
    wurzel.addView(auswahl, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
}

// ------------------------------------------------------------ Schnelleinstellungen, Abdecken, Aufräumen

internal fun HomeActivity.schnellAktion(i: Int) {
    when (i) {
        0, 1 -> schnell()
        2 -> wlan()
        3 -> abdecken()
        4 -> aufraeumen()
        5 -> zeigeSchoner()
    }
}

private fun HomeActivity.wlan() {
    try {
        startActivity(Intent(if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_WIFI else Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        meldung("Dieses Board bietet keine WLAN-Einstellungen an.")
    }
}

/** Lautstärke, Helligkeit, WLAN, Abdecken, Aufräumen, Bildschirmschoner und Profilwechsel – groß und auf einen Blick. */
internal fun HomeActivity.schnell() {
    val f = neuesFenster("Schnelleinstellungen", 1100)
    val audio = getSystemService(AudioManager::class.java)
    if (audio != null) {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val jetzt = audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max
        f.add(regler(this, "🔊  Lautstärke", 0..100, jetzt, 5, " %") { p ->
            try { audio.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(max * p / 100f), 0) } catch (_: Exception) {}
            kachelnNeu()
        }, 4)
    }
    if (Settings.System.canWrite(this)) {
        val hell = try { Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS) * 100 / 255 } catch (_: Exception) { 70 }
        f.add(regler(this, "☀  Helligkeit", 5..100, hell, 5, " %") { p ->
            try {
                Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, (p * 255 / 100).coerceIn(5, 255))
            } catch (_: Exception) {
            }
        }, 12)
    } else {
        f.add(text(this, "☀  Helligkeit: dafür einmal „Systemeinstellungen ändern“ erlauben.", 22, Farben.TEXT2), 16)
        f.add(knopf(this, "Helligkeit erlauben …") {
            f.schliessen()
            try { startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName"))) } catch (_: Exception) { meldung("Dieses Board bietet die Einstellung nicht an.") }
        }, 8)
    }
    f.add(LinearLayout(this).apply {
        addView(knopf(context, "📶  WLAN …") { f.schliessen(); wlan() }, lp(rechts = 12.vp))
        addView(knopf(context, "▬  Abdecken") { f.schliessen(); abdecken() }, lp(rechts = 12.vp))
        addView(knopf(context, "♻  Aufräumen") { f.schliessen(); aufraeumen() }, lp(rechts = 12.vp))
        addView(knopf(context, "🌙  Schoner") { f.schliessen(); zeigeSchoner() }, lp())
    }, 22)
    val k = KonfigSpeicher.konfig
    if (k.profile.isNotEmpty()) {
        f.add(text(this, "Profil (Startbildschirm):", 22, Farben.TEXT2), 22)
        f.add(android.widget.HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(LinearLayout(context).apply {
                Profile.namen(k).forEach { n ->
                    addView(knopf(context, n, haupt = n == k.profil) { f.schliessen(); profilWechseln(n) }, lp(rechts = 12.vp))
                }
            })
        }, 6)
    }
    f.knoepfe(knopf(this, "Fertig", haupt = true) { f.schliessen() })
}

/** Schwarzer Vorhang über dem Bildschirm (auch über anderen Apps). */
internal fun HomeActivity.abdecken() {
    if (RandGesten.abdecken(this)) return
    val f = neuesFenster("Abdecken", 900)
    f.add(text(this, "Zum Abdecken braucht DammLauncher einmal die Erlaubnis „Über anderen Apps einblenden“.", 24))
    f.knoepfe(knopf(this, "Später") { f.schliessen() }, knopf(this, "Erlaubnis erteilen …", haupt = true) { f.schliessen(); gestenErlauben() })
}

internal fun HomeActivity.aufraeumen() {
    meldung("Räumt auf …")
    arbeit.execute {
        val n = Aufraeumen.jetzt(this)
        haupt.post {
            kachelnNeu()
            val laut = KonfigSpeicher.konfig.aufraeumen.lautstaerke
            meldung("Aufgeräumt: $n Apps im Hintergrund beendet" + (if (laut >= 0) ", Lautstärke $laut %." else "."))
        }
    }
}

// ------------------------------------------------------------ Dock und Seitenleiste

internal fun HomeActivity.insDock(a: Element.App) {
    val k = KonfigSpeicher.konfig
    when {
        k.dock.any { it.schluessel == a.schluessel } -> meldung("Ist schon im Dock.")
        k.dock.size >= Grenzen.DOCK -> meldung("Das Dock ist voll (höchstens ${Grenzen.DOCK} Apps).")
        else -> {
            KonfigSpeicher.aendere { it.copy(dock = it.dock + a.copy(spalte = 0, zeile = 0)) }
            meldung(if (k.aussehen == de.oejendorferdamm.dammlauncher.konfig.Aussehen.CLEVERTOUCH) "„${appName(a)}“ liegt jetzt im Dock."
                else "„${appName(a)}“ liegt im Dock – zu sehen im Clevertouch-Aussehen (Einstellungen → Aussehen).")
        }
    }
}

internal fun HomeActivity.dockMenue(a: Element.App) {
    if (schuelerAnsicht() || KonfigSpeicher.konfig.bearbeitenGesperrt) return
    val f = neuesFenster(appName(a), 900)
    fun schiebe(d: Int) = KonfigSpeicher.aendere { k ->
        val l = k.dock.toMutableList()
        val i = l.indexOfFirst { it.schluessel == a.schluessel }
        val j = i + d
        if (i < 0 || j !in l.indices) k else { val x = l[i]; l[i] = l[j]; l[j] = x; k.copy(dock = l) }
    }
    f.add(LinearLayout(this).apply {
        addView(knopf(context, "◀  Nach links") { schiebe(-1) }, lp(rechts = 12.vp))
        addView(knopf(context, "Nach rechts  ▶") { schiebe(1) }, lp())
    })
    f.add(knopf(this, "Aus dem Dock entfernen", gefahr = true) {
        f.schliessen()
        KonfigSpeicher.aendere { k -> k.copy(dock = k.dock.filter { it.schluessel != a.schluessel }) }
    })
}

internal fun HomeActivity.inSeitenleiste(a: Element.App) {
    val k = KonfigSpeicher.konfig
    when {
        k.seitenleiste.apps.any { it.schluessel == a.schluessel } -> meldung("Ist schon in der Seitenleiste.")
        k.seitenleiste.apps.size >= Seitenleiste.MAX_APPS -> meldung("Die Seitenleiste ist voll (höchstens ${Seitenleiste.MAX_APPS} eigene Apps).")
        else -> {
            KonfigSpeicher.aendere { it.copy(seitenleiste = it.seitenleiste.copy(apps = it.seitenleiste.apps + a.copy(spalte = 0, zeile = 0))) }
            meldung(if (k.seitenleiste.an) "„${appName(a)}“ ist jetzt in der Seitenleiste." else "„${appName(a)}“ ist in der Seitenleiste – einschalten: Einstellungen → Seitenleiste.")
        }
    }
}

internal fun HomeActivity.seitenleisteAppWaehlen() = appAuswaehlen("In die Seitenleiste …") { inSeitenleiste(it) }

// ------------------------------------------------------------ Stundenplan

/** Fächer je Wochentag: zuerst der Plan dieses Boards, sonst der aus dem Vorlagen-Ordner (Klasse des Boards). */
/** Zuletzt gelesener Plan (die Kachel fragt jede Minute – nur bei geänderten Daten neu einlesen). */
private var planZwischen: Pair<String, List<List<String>>>? = null

internal fun HomeActivity.stundenplan(): List<List<String>> {
    val eigen = Geraet.stundenplan
    val klasse = Geraet.klasse.trim().lowercase()
    val schluessel = eigen + "\u0000" + klasse + "\u0000" + Geraet.stundenplanServer.hashCode()
    planZwischen?.let { if (it.first == schluessel) return it.second }
    return planLesen(eigen, klasse).also { planZwischen = schluessel to it }
}

private fun planLesen(eigen: String, klasse: String): List<List<String>> {
    if (eigen.isNotBlank()) return Zeiten.planAus(eigen.lines())
    if (klasse.isEmpty()) return emptyList()
    return try {
        val o = JSONObject(Geraet.stundenplanServer.ifBlank { return emptyList() })
        val schluessel = o.keys().asSequence().firstOrNull { it.trim().lowercase() == klasse } ?: return emptyList()
        val tage = o.getJSONObject(schluessel)
        Zeiten.planAus(listOf("mo", "di", "mi", "do", "fr").map { t ->
            when (val v = tage.opt(t)) {
                is org.json.JSONArray -> (0 until v.length()).joinToString(",") { v.optString(it) }
                null -> ""
                else -> v.toString()
            }
        })
    } catch (_: Exception) {
        emptyList()
    }
}

/** stundenplan.json aus dem Vorlagen-Ordner holen (Lesezugang); true = neu gelesen. Im Hintergrund aufrufen. */
internal fun HomeActivity.stundenplanLaden(): Boolean = try {
    val (b, p) = Abgleich.leseZugang() ?: throw Exception()
    val a = WebDav(Geraet.server, b, p).get("${Geraet.ordner}/stundenplan.json", max = 256 * 1024)
    val d = a.daten
    if (a.code == 200 && d != null) {
        val t = String(d, Charsets.UTF_8)
        JSONObject(t) // nur speichern, was sich lesen lässt
        Geraet.stundenplanServer = t
        true
    } else false
} catch (_: Exception) {
    false
}

internal fun HomeActivity.stundenEinstellen() {
    val f = neuesFenster("Stundenzeiten", 1100)
    f.add(text(this, "Wann die Stunden beginnen und enden – für die Kachel „Stundenplan“ (und „Profil nach Fach“). " +
        "Auf alle Boards kommen sie mit „Als Vorlage hochladen“ (Boss-Modus).", 22, Farben.TEXT2))
    val liste = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    f.add(liste)
    fun aufbauen() {
        liste.removeAllViews()
        val s = KonfigSpeicher.konfig.stunden
        if (s.isEmpty()) liste.addView(text(this, "Noch keine Stunden eingetragen.", 24, Farben.TEXT2))
        s.forEachIndexed { i, z ->
            liste.addView(LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(text(context, "${i + 1}. Stunde:  ${Zeitraum.uhrzeit(z.von)} – ${Zeitraum.uhrzeit(z.bis)}", 28, fett = true), lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(knopf(context, "Entfernen", gefahr = true) { KonfigSpeicher.aendere { it.copy(stunden = it.stunden - z) }; aufbauen() })
            }, lp(ViewGroup.LayoutParams.MATCH_PARENT, oben = 8.vp))
        }
    }
    aufbauen()
    val von = eingabe(this, "Beginn, z. B. 08:00")
    val bis = eingabe(this, "Ende, z. B. 08:45")
    f.add(text(this, "Neue Stunde:", 22, Farben.TEXT2), 22)
    f.add(von, 6)
    f.add(bis, 8)
    f.knoepfe(
        knopf(this, "Stundenplan dieses Boards …") { f.schliessen(); stundenplanEinstellen() },
        knopf(this, "Fertig") { f.schliessen() },
        knopf(this, "Hinzufügen", haupt = true) {
            val z = Zeitraum.aus("${von.text}-${bis.text}")
            if (z == null) { meldung("Bitte Uhrzeiten wie 08:00 und 08:45 eingeben (Ende nach Beginn)."); return@knopf }
            KonfigSpeicher.aendere { it.copy(stunden = (it.stunden + z).distinct().sortedBy { s -> s.von }.take(16)) }
            von.setText(""); bis.setText("")
            aufbauen()
            panel?.aufbauen()
        })
}

internal fun HomeActivity.stundenplanEinstellen() {
    val f = neuesFenster("Stundenplan dieses Boards", 1200)
    f.add(text(this, "Fächer je Tag mit Komma getrennt, in der Reihenfolge der Stunden – z. B. „Mathe, Deutsch, Sport, Sport“. " +
        "Leere Stunden: zwei Kommas. Bleibt alles leer, gilt der Plan aus stundenplan.json im Vorlagen-Ordner (für die Klasse dieses Boards).", 22, Farben.TEXT2))
    val alt = Geraet.stundenplan.lines()
    val felder = listOf("Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag").mapIndexed { i, t ->
        f.add(text(this, t, 22, Farben.TEXT2), 12)
        eingabe(this, "z. B. Mathe, Deutsch, Sport", alt.getOrNull(i).orEmpty()).also { f.add(it, 4) }
    }
    f.knoepfe(knopf(this, "Abbrechen") { f.schliessen() }, knopf(this, "Speichern", haupt = true) {
        val zeilen = felder.map { it.text.toString().replace('\n', ' ').trim() }
        Geraet.stundenplan = if (zeilen.all { it.isEmpty() }) "" else zeilen.joinToString("\n")
        f.schliessen()
        kachelnNeu()
        panel?.aufbauen()
    })
}

// ------------------------------------------------------------ Nachrichten an alle Boards

internal fun HomeActivity.nachrichtenEinstellen() {
    val f = neuesFenster("Nachrichten an alle Boards", 1200)
    f.add(text(this, "Erscheinen zur eingestellten Zeit oben auf dem Startbildschirm – z. B. „Probealarm um 10:15 – bitte ruhig bleiben“. " +
        "Auf alle Boards kommen sie mit „Als Vorlage hochladen“ (Boss-Modus); ältere werden von selbst gelöscht.", 22, Farben.TEXT2))
    val liste = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    f.add(liste)
    val heute = Zeiten.tag(Calendar.getInstance())
    fun aufbauen() {
        liste.removeAllViews()
        val n = KonfigSpeicher.konfig.nachrichten.filter { it.tag >= heute }.sortedWith(compareBy({ it.tag }, { it.von }))
        if (n.isEmpty()) liste.addView(text(this, "Keine Nachrichten geplant.", 24, Farben.TEXT2))
        n.forEach { x ->
            liste.addView(LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(text(context, "${x.tag.split('-').reversed().take(2).joinToString(".")}., ${Zeitraum.uhrzeit(x.von)}–${Zeitraum.uhrzeit(x.bis)}:  ${x.text}", 24), lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(knopf(context, "Entfernen", gefahr = true) { KonfigSpeicher.aendere { it.copy(nachrichten = it.nachrichten - x) }; aufbauen() })
            }, lp(ViewGroup.LayoutParams.MATCH_PARENT, oben = 8.vp))
        }
    }
    aufbauen()
    val feld = eingabe(this, "Text (bis ${Nachricht.MAX} Zeichen)")
    var tage = 0
    val tagReihe = LinearLayout(this)
    fun tagWahl() {
        tagReihe.removeAllViews()
        listOf(0 to "Heute", 1 to "Morgen", 2 to "Übermorgen").forEach { (n, t) -> tagReihe.addView(knopf(this, t, haupt = tage == n) { tage = n; tagWahl() }, lp(rechts = 12.vp)) }
    }
    tagWahl()
    val von = eingabe(this, "Von, z. B. 10:05")
    val bis = eingabe(this, "Bis, z. B. 10:30")
    f.add(text(this, "Neue Nachricht:", 22, Farben.TEXT2), 22)
    f.add(feld, 6)
    f.add(tagReihe, 10)
    f.add(von, 10)
    f.add(bis, 8)
    f.knoepfe(knopf(this, "Fertig") { f.schliessen() }, knopf(this, "Hinzufügen", haupt = true) {
        val t = feld.text.toString().trim().replace(Regex("\\s+"), " ")
        val z = Zeitraum.aus("${von.text}-${bis.text}")
        when {
            t.isEmpty() -> meldung("Bitte einen Text eingeben.")
            z == null -> meldung("Bitte Uhrzeiten wie 10:05 und 10:30 eingeben (Ende nach Beginn).")
            else -> {
                val tag = Zeiten.tag(Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, tage) })
                KonfigSpeicher.aendere { k ->
                    val behalten = k.nachrichten.filter { it.tag >= heute }
                    k.copy(nachrichten = (behalten + Nachricht(t.take(Nachricht.MAX), tag, z.von, z.bis)).takeLast(Nachricht.ANZAHL))
                }
                feld.setText(""); von.setText(""); bis.setText("")
                aufbauen()
                panel?.aufbauen()
            }
        }
    })
}

// ------------------------------------------------------------ Profile

internal fun HomeActivity.profilWechseln(name: String) {
    if (name == KonfigSpeicher.konfig.profil) return
    KonfigSpeicher.aendere { Profile.wechseln(it, name) }
    meldung("Profil „$name“.")
}

internal fun HomeActivity.profileVerwalten() {
    val f = neuesFenster("Profile", 1200)
    f.add(text(this, "Ein Profil ist ein eigener Startbildschirm – z. B. je Fach oder Klasse. Gewechselt wird hier, in den Schnelleinstellungen " +
        "oder von selbst beim Stundenwechsel, wenn das Profil so heißt wie das Fach im Stundenplan.", 22, Farben.TEXT2))
    val liste = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    f.add(liste)
    fun aufbauen() {
        liste.removeAllViews()
        val k = KonfigSpeicher.konfig
        Profile.namen(k).forEach { n ->
            val aktiv = n == k.profil
            liste.addView(LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(text(context, (if (aktiv) "✓  " else "") + n, 28, fett = aktiv), lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                if (!aktiv) addView(knopf(context, "Wechseln", haupt = true) { profilWechseln(n); aufbauen() }, lp(rechts = 10.vp))
                addView(knopf(context, "Umbenennen …") { f.schliessen(); profilUmbenennen(n) }, lp(rechts = 10.vp))
                if (!aktiv) addView(knopf(context, "Löschen", gefahr = true) {
                    KonfigSpeicher.aendere { Profile.loeschen(it, n) }
                    aufbauen()
                })
            }, lp(ViewGroup.LayoutParams.MATCH_PARENT, oben = 10.vp))
        }
    }
    aufbauen()
    f.add(schalter(this, "Beim Stundenwechsel das Profil des Fachs wählen", KonfigSpeicher.konfig.profilNachFach) { an ->
        KonfigSpeicher.aendere { it.copy(profilNachFach = an) }
        if (an && KonfigSpeicher.konfig.stunden.isEmpty()) meldung("Dafür noch die Stundenzeiten eintragen (Einstellungen → Stundenplan).")
    }, 18)
    f.knoepfe(
        knopf(this, "Neu (leer) …") { f.schliessen(); profilNeu(false) },
        knopf(this, "Kopie des aktuellen …") { f.schliessen(); profilNeu(true) },
        knopf(this, "Fertig", haupt = true) { f.schliessen(); panel?.aufbauen() }
    )
}

private fun HomeActivity.profilNeu(kopie: Boolean) {
    val f = neuesFenster(if (kopie) "Kopie des aktuellen Profils" else "Neues Profil", 900)
    if (kopie) f.add(text(this, "Apps, Ordner und eingebaute Kacheln werden kopiert; Widgets anderer Apps bitte im neuen Profil neu hinzufügen.", 22, Farben.TEXT2))
    val feld = eingabe(this, "Name, z. B. Mathe oder 5a")
    f.add(feld)
    f.knoepfe(knopf(this, "Abbrechen") { f.schliessen() }, knopf(this, "Anlegen", haupt = true) {
        val n = feld.text.toString().trim()
        val neu = Profile.neu(KonfigSpeicher.konfig, n, kopie)
        if (neu == null) { meldung(if (Profile.finde(KonfigSpeicher.konfig, n) != null) "Ein Profil mit diesem Namen gibt es schon." else "Bitte einen kurzen Namen (Buchstaben, Ziffern) – höchstens ${Profil.MAX} Profile."); return@knopf }
        f.schliessen()
        KonfigSpeicher.aendere { neu }
        profilWechseln(n)
        profileVerwalten()
    })
    feld.requestFocus()
}

private fun HomeActivity.profilUmbenennen(alt: String) {
    val f = neuesFenster("Profil umbenennen", 900)
    val feld = eingabe(this, "Neuer Name", alt)
    f.add(feld)
    f.knoepfe(knopf(this, "Abbrechen") { f.schliessen(); profileVerwalten() }, knopf(this, "Speichern", haupt = true) {
        val neu = Profile.umbenennen(KonfigSpeicher.konfig, alt, feld.text.toString())
        if (neu == null) { meldung("Name ungültig oder schon vergeben."); return@knopf }
        f.schliessen()
        KonfigSpeicher.aendere { neu }
        profileVerwalten()
    })
}

// ------------------------------------------------------------ Zufallsname

/** Namen (nur auf diesem Board gespeichert) bzw. Zahlen „1-28“ aus dem Text der Kachel. */
internal fun HomeActivity.zufallListe(kennung: String): List<String> {
    val t = widget(kennung)?.text.orEmpty()
    Regex("^\\s*(\\d{1,3})\\s*-\\s*(\\d{1,3})\\s*$").find(t)?.let { m ->
        val a = m.groupValues[1].toInt()
        val b = m.groupValues[2].toInt()
        if (b >= a && b - a < 500) return (a..b).map { it.toString() }
    }
    return Geraet.namen(Geraet.klasse.ifBlank { "alle" }).split('\n', ',', ';').map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(200)
}

internal fun HomeActivity.zufallEinrichten(kennung: String) {
    val klasse = Geraet.klasse.ifBlank { "alle" }
    val f = neuesFenster("Zufall", 1200)
    f.add(text(this, "Entweder Zahlen (z. B. die Nummern der Plätze) – oder Vornamen. Namen bleiben NUR auf diesem Board und gehen nicht mit der Vorlage an andere Geräte." +
        (if (Geraet.klasse.isBlank()) "" else " Liste für die Klasse ${Geraet.klasse}."), 22, Farben.TEXT2))
    val alt = widget(kennung)?.text.orEmpty()
    val zahlen = eingabe(this, "Zahlen von–bis, z. B. 1-28 (leer = Namen)", alt)
    f.add(zahlen, 14)
    val namen = eingabe(this, "Vornamen, mit Komma getrennt", Geraet.namen(klasse).replace("\n", ", ")).apply {
        setSingleLine(false)
        minLines = 3
        maxLines = 8
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_WORDS
    }
    f.add(namen, 12)
    f.knoepfe(
        knopf(this, "Neu mischen") { Geraet.setzeGezogen(kennung, emptySet()); kachelnNeu(); meldung("Alle sind wieder dabei.") },
        knopf(this, "Abbrechen") { f.schliessen() },
        knopf(this, "Speichern", haupt = true) {
            val z = zahlen.text.toString().trim()
            if (z.isNotEmpty() && !Regex("^\\d{1,3}\\s*-\\s*\\d{1,3}$").matches(z)) { meldung("Zahlen bitte wie „1-28“ eingeben."); return@knopf }
            Geraet.setzeNamen(klasse, namen.text.toString().split('\n', ',', ';').map { it.trim().take(30) }.filter { it.isNotEmpty() }.joinToString("\n"))
            Geraet.setzeGezogen(kennung, emptySet())
            f.schliessen()
            widgetText(kennung, z.replace(" ", ""))
            kachelnNeu()
        })
}

// ------------------------------------------------------------ Bildschirm teilen

internal fun HomeActivity.freigabeName(kennung: String): String {
    val t = widget(kennung)?.text.orEmpty()
    val c = ComponentName.unflattenFromString(t) ?: return "App wählen"
    return AppKatalog.finde(c.packageName, c.className)?.name ?: "App fehlt"
}

internal fun HomeActivity.freigabeStarten(kennung: String) {
    val c = ComponentName.unflattenFromString(widget(kennung)?.text.orEmpty())
    if (c == null || !AppKatalog.istDa(c.packageName, c.className)) { freigabeWaehlen(kennung); return }
    if (!AppKatalog.starte(c, null)) meldung("Die App lässt sich nicht starten.")
}

internal fun HomeActivity.freigabeWaehlen(kennung: String) {
    if (schuelerAnsicht()) return
    appAuswaehlen("Freigabe-App wählen …") { a ->
        widgetText(kennung, "${a.paket}/${a.aktivitaet}")
        kachelnNeu()
        meldung("„${appName(a)}“ startet jetzt mit „Bildschirm teilen“.")
    }
}

// ------------------------------------------------------------ Tafel-Übersicht

internal fun HomeActivity.statusZugang() {
    val f = neuesFenster("Status-Zugang", 1100)
    f.add(text(this, "Damit jedes Board einmal am Tag einen kleinen Bericht (Version, letzter Abgleich, freier Speicher, Abstürze – keine Inhalte) " +
        "in den Unterordner „status“ des Vorlagen-Ordners legt. Am besten ein eigener Zugang, der NUR dort schreiben darf. Wird verschlüsselt gespeichert.", 22, Farben.TEXT2))
    if (Geraet.statusMeldung.isNotEmpty()) f.add(text(this, "Zuletzt: ${Geraet.statusMeldung}", 22, Farben.TEXT2), 10)
    val b = eingabe(this, "Benutzername (Schreibrecht auf status/)")
    val p = eingabe(this, "Passwort", geheim = true)
    f.add(b, 14); f.add(p, 8)
    var mitVorlage = KonfigSpeicher.konfig.statusZugang != null
    f.add(schalter(this, "Mit der Vorlage an alle Boards geben", mitVorlage) { mitVorlage = it }, 14)
    f.add(text(this, "Dann muss er nicht an jedem Board eingetippt werden – steht aber lesbar in der Vorlage. Nur mit einem Zugang, der ausschließlich in „status“ schreiben darf!", 21, Farben.ROT), 4)
    f.knoepfe(
        knopf(this, "Entfernen", gefahr = Status.hatZugang()) {
            Status.vergissZugang()
            KonfigSpeicher.aendere { it.copy(statusZugang = null) }
            f.schliessen(); panel?.aufbauen(); meldung("Status-Zugang entfernt.")
        }.apply { isEnabled = Status.hatZugang() },
        knopf(this, "Abbrechen") { f.schliessen() },
        knopf(this, "Speichern und senden", haupt = true) {
            val nb = b.text.toString().trim()
            val np = p.text.toString()
            if (nb.isNotEmpty() && np.isNotEmpty() && !Status.speichereZugang(nb, np)) { meldung("Konnte nicht sicher gespeichert werden."); return@knopf }
            val z = Status.zugang()
            KonfigSpeicher.aendere { it.copy(statusZugang = if (mitVorlage && z != null) de.oejendorferdamm.dammlauncher.konfig.Zugang(z.first, z.second) else null) }
            f.schliessen()
            meldung("Sendet …")
            netz.execute {
                val t = Status.senden(this)
                haupt.post { meldung(t, lang = true); panel?.aufbauen() }
            }
        })
}

internal fun HomeActivity.tafelUebersicht() {
    meldung("Liest die Berichte …")
    netz.execute {
        val ergebnis = try { Result.success(Status.alle()) } catch (e: Exception) { Result.failure(e) }
        haupt.post {
            val liste = ergebnis.getOrNull()
            if (liste == null) { meldung("Fehler: ${ergebnis.exceptionOrNull()?.message ?: "Berichte nicht lesbar"}", lang = true); return@post }
            val f = neuesFenster("Tafel-Übersicht (${liste.size})", 1500)
            if (liste.isEmpty()) f.add(text(this, "Noch keine Berichte. Jedes Board braucht dafür den Status-Zugang (Boss-Modus → Tafel-Übersicht).", 24, Farben.TEXT2))
            val neueste = liste.map { it.version }.maxWithOrNull { a, b -> if (de.oejendorferdamm.dammlauncher.update.Update.istNeuer(a, b)) 1 else if (a == b) 0 else -1 }
            val format = SimpleDateFormat("dd.MM. HH:mm", Locale.GERMAN)
            for (r in liste) {
                val alt = System.currentTimeMillis() - r.zeit > 2 * 24 * 60 * 60_000L
                val veraltet = neueste != null && r.version != neueste
                f.add(LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    background = de.oejendorferdamm.dammlauncher.ui.runde(Farben.KARTE, 22f.vp, 1.vp, Farben.LINIE)
                    setPadding(24.vp, 16.vp, 24.vp, 16.vp)
                    addView(text(context, "${r.name.ifBlank { "(ohne Namen)" }}" + (if (r.klasse.isNotBlank()) " · Klasse ${r.klasse}" else "") + " · Version ${r.version}" +
                        (if (veraltet) "  ⚠ veraltet" else ""), 26, if (veraltet) Farben.ROT else Farben.TEXT, fett = true))
                    addView(text(context, "Bericht ${format.format(Date(r.zeit))}" + (if (alt) " (älter als 2 Tage)" else "") +
                        " · ${if (r.freiMb >= 0) "${r.freiMb} MB frei" else "Speicher ?"} · ${r.modell}, Android-API ${r.android}" +
                        (if (r.profil.isNotBlank()) " · Profil ${r.profil}" else ""), 21, if (alt) Farben.ROT else Farben.TEXT2), lp(oben = 4.vp))
                    addView(text(context, "Abgleich: ${r.abgleich.ifBlank { "–" }}", 21, if (r.abgleich.contains("Fehler")) Farben.ROT else Farben.TEXT2), lp(oben = 2.vp))
                    if (r.abstuerze.isNotEmpty()) addView(knopf(context, "💥  ${r.abstuerze.size} Absturz-Bericht(e) ansehen") {
                        val g = neuesFenster("Abstürze: ${r.name}", 1500)
                        r.abstuerze.forEach { a -> g.add(text(this@tafelUebersicht, a, 18, Farben.TEXT2).apply { setTextIsSelectable(true) }, 12) }
                    }, lp(oben = 10.vp))
                }, 12)
            }
        }
    }
}
