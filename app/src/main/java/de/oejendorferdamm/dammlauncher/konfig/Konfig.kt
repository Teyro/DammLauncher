/*
 * DammLauncher – Startbildschirm für interaktive Displays
 * Copyright (C) 2026 Teyro
 * Lizenz: GNU GPL v3 oder neuer, siehe LICENSE
 */
package de.oejendorferdamm.dammlauncher.konfig

/**
 * Die komplette Einrichtung des Startbildschirms – das, was als Vorlage auf andere Geräte geht.
 * Längen sind in „Vorbild-Pixeln“ angegeben: bezogen auf ein 1920 px breites Bild. Auf einem
 * 4K-Display wird alles doppelt so groß gerechnet, so sieht es überall gleich aus.
 */
data class Konfig(
    val spalten: Int = 6,
    val zeilen: Int = 4,
    val rand: Raender = Raender(40, 40, 40, 40),
    val abstand: Int = 16,
    /** Icongröße in Prozent der Zelle (20–100). */
    val iconProzent: Int = 60,
    /** Schriftgröße der Beschriftung in Vorbild-Pixeln (10–60). */
    val textGroesse: Int = 22,
    val beschriftung: Boolean = true,
    val animationen: Boolean = false,
    /** Knopf „Alle Apps“ unten in der Mitte (Wischen klappt auf großen Displays nicht immer). */
    val alleAppsKnopf: Boolean = true,
    /** Schrift der App-Namen: [Schrift.SYSTEM] oder eine der Grundschriften. */
    val schrift: String = Schrift.SYSTEM,
    val gesten: Gesten = Gesten(),
    /** In der App-Übersicht ausgeblendet („paket/aktivität“) – z. B. System-Apps, die niemand braucht. */
    val ausgeblendet: Set<String> = emptySet(),
    /** Neu eingerichtet: oben links die Digitaluhr (3 × 2). */
    val elemente: List<Element> = listOf(Element.Widget(Element.UHR, "uhr", 0, 0, 3, 2)),
    /** Was nach dem Verkleinern des Rasters keinen Platz mehr hatte. */
    val nichtPlatziert: List<Element> = emptyList(),
    val hintergrund: Hintergrund = Hintergrund(),
    /** Layout gegen Verschieben/Entfernen gesperrt. */
    val bearbeitenGesperrt: Boolean = false,
    /** PIN für die Einstellungen (Hash), null = keine PIN. */
    val pin: PasswortHash? = null,
    /** Master-Passwort für den Boss-Modus (Hash), null = noch nicht festgelegt. */
    val boss: PasswortHash? = null,
    /** Pausen des Tages (Minuten seit Mitternacht) – für die Pausen-Kachel. */
    val pausen: List<Zeitraum> = emptyList(),
    /** Hinweis-Band oben auf allen Boards, null = keiner. */
    val hinweis: Hinweis? = null,
    /** Abends und am Wochenende gesperrt (Entsperren mit der PIN). */
    val feierabend: Feierabend = Feierabend(),
    /** Bildschirmschoner nach so vielen Minuten ohne Berührung (0 = aus). */
    val schoner: Int = 0,
    val schueler: SchuelerAnsicht = SchuelerAnsicht(),
    /** [Aussehen.KLASSISCH] oder [Aussehen.CLEVERTOUCH] (Optik der neuen Clevertouch-Boards, ohne Animationen). */
    val aussehen: String = Aussehen.KLASSISCH,
    /** Form aller App-Symbole: [SymbolForm.ORIGINAL], [SymbolForm.RUND] oder [SymbolForm.KREIS]. */
    val symbolForm: String = SymbolForm.ORIGINAL,
    /** Leiste unten mit Lieblings-Apps (im Clevertouch-Aussehen). */
    val dock: List<Element.App> = emptyList(),
    val seitenleiste: Seitenleiste = Seitenleiste(),
    /** Unterrichtsstunden des Tages (1. Stunde, 2. Stunde …) – für die Stundenplan-Kachel. */
    val stunden: List<Zeitraum> = emptyList(),
    /** Zeitgesteuerte Nachrichten an alle Boards (z. B. „Probealarm um 10:15“). */
    val nachrichten: List<Nachricht> = emptyList(),
    val aufraeumen: Aufraeumen = Aufraeumen(),
    /** Name des aktiven Profils – seine Elemente stehen in [elemente]. */
    val profil: String = Profil.STANDARD,
    /** Die übrigen Profile (Startbildschirme je Klasse oder Fach) mit ihren Elementen. */
    val profile: List<Profil> = emptyList(),
    /** Beim Stundenwechsel das Profil wählen, das so heißt wie das Fach laut Stundenplan. */
    val profilNachFach: Boolean = false,
    /**
     * Status-Zugang für die Tafel-Übersicht, der mit der Vorlage an alle Boards geht (null = keiner).
     * Steht lesbar in der Vorlage – nur einen Zugang verwenden, der ausschließlich in den Ordner
     * „status“ schreiben darf.
     */
    val statusZugang: Zugang? = null,
    /** Wann diese Einrichtung zuletzt geändert wurde (ms seit 1970) – dient auch als Vorlagen-Version. */
    val stand: Long = 0L
)

object Aussehen {
    const val KLASSISCH = "klassisch"
    const val CLEVERTOUCH = "clevertouch"
    val ALLE = setOf(KLASSISCH, CLEVERTOUCH)
}

object SymbolForm {
    const val ORIGINAL = "original"
    const val RUND = "rund"
    const val KREIS = "kreis"
    val ALLE = setOf(ORIGINAL, RUND, KREIS)
}

/**
 * Seitenleiste wie bei Clevertouch: ein schmaler Griff am Rand – auch über anderen Apps – öffnet
 * Startbildschirm, Zurück, Whiteboard, Timer, Abdecken und bis zu [MAX_APPS] eigene Apps.
 */
data class Seitenleiste(val an: Boolean = false, val seite: String = RECHTS, val apps: List<Element.App> = emptyList()) {
    companion object {
        const val LINKS = "links"
        const val RECHTS = "rechts"
        const val MAX_APPS = 6
    }
}

/** Nachricht an alle Boards: am [tag] (yyyy-MM-dd) von [von] bis [bis] (Minuten) oben eingeblendet. */
data class Nachricht(val text: String, val tag: String, val von: Int, val bis: Int) {
    companion object {
        const val MAX = 200
        const val ANZAHL = 20
    }
}

/**
 * „Aufräumen“: Apps im Hintergrund schließen und die Lautstärke zurücksetzen – auf Knopfdruck, zu
 * Beginn jeder Pause ([pausen], nur wenn der Startbildschirm vorne ist) und zum [feierabend].
 * [lautstaerke] in Prozent, -1 = nicht ändern.
 */
data class Aufraeumen(val pausen: Boolean = false, val feierabend: Boolean = true, val lautstaerke: Int = 40)

data class Zugang(val benutzer: String, val passwort: String)

/** Ein gespeicherter Startbildschirm (z. B. „Mathe“ oder „5a“). */
data class Profil(val name: String, val elemente: List<Element>) {
    companion object {
        const val STANDARD = "Standard"
        const val MAX = 12
        val NAME = Regex("^[\\p{L}0-9 ._-]{1,30}$")
    }
}

/** Uhrzeiten als Minuten seit Mitternacht, [von] bis ausschließlich [bis]. */
data class Zeitraum(val von: Int, val bis: Int) {
    override fun toString() = "${uhrzeit(von)}-${uhrzeit(bis)}"

    companion object {
        fun uhrzeit(min: Int) = "%02d:%02d".format(min / 60, min % 60)

        /** „9:35“, „09.35“ oder „0935“ → Minuten; null, wenn es keine Uhrzeit ist. */
        fun minuten(t: String): Int? {
            val m = Regex("^\\s*(\\d{1,2})[:.]?(\\d{2})\\s*$").find(t) ?: return null
            val h = m.groupValues[1].toInt()
            val mi = m.groupValues[2].toInt()
            return if (h in 0..23 && mi in 0..59) h * 60 + mi else null
        }

        /** „09:35-09:55“ (auch mit – oder „bis“) → Zeitraum; null bei Unsinn oder Ende vor Beginn. */
        fun aus(t: String): Zeitraum? {
            val teile = t.split(Regex("\\s*(-|–|bis)\\s*"))
            if (teile.size != 2) return null
            val a = minuten(teile[0]) ?: return null
            val b = minuten(teile[1]) ?: return null
            return if (b > a) Zeitraum(a, b) else null
        }
    }
}

/** Hinweis-Band: [text] wird bis einschließlich [bis] (yyyy-MM-dd) gezeigt. */
data class Hinweis(val text: String, val bis: String) {
    companion object {
        val DATUM = Regex("^\\d{4}-\\d{2}-\\d{2}$")
        const val MAX = 200
    }
}

/** Feierabend-Sperre von [ab] bis [bis] (Minuten, über Mitternacht) und auf Wunsch das ganze Wochenende. */
data class Feierabend(val an: Boolean = false, val ab: Int = 17 * 60, val bis: Int = 7 * 60, val wochenende: Boolean = true)

/**
 * Schüler-Ansicht: zeigt nur, was nicht als [nurLehrer] markiert ist (Schlüssel der Elemente), und
 * auf Wunsch keine App-Übersicht. Die Lehrer-Ansicht öffnet sich mit der PIN und kehrt nach
 * [zurueck] Minuten ohne Berührung von selbst zurück.
 */
data class SchuelerAnsicht(
    val an: Boolean = false,
    val nurLehrer: Set<String> = emptySet(),
    val ohneAlleApps: Boolean = true,
    val zurueck: Int = 20
)

data class Raender(val oben: Int, val unten: Int, val links: Int, val rechts: Int) {
    fun alle(wert: Int) = Raender(wert, wert, wert, wert)
    val gleich get() = oben == unten && oben == links && oben == rechts
}

/**
 * Hintergrund: Farbe (ARGB) und ob ein Bild (Datei hintergrund.jpg) darüber liegt. [unsplash] = jeden
 * Tag ein neues Foto (liegt über Farbe und eigenem Bild) – aus der [quelle] Wikimedia Commons
 * (ausgezeichnete, frei lizenzierte Landschaftsfotos, kein Schlüssel) oder Unsplash zum Suchbegriff
 * [thema] (braucht einen kostenlosen [schluessel], den „Access Key“ einer Unsplash-App).
 */
data class Hintergrund(
    val farbe: Int = 0xFF1E3A5F.toInt(),
    val bild: Boolean = false,
    val unsplash: Boolean = false,
    val thema: String = Hintergrund.STANDARD_THEMA,
    val schluessel: String = "",
    /** Woher das Foto des Tages kommt: [COMMONS] (frei, ohne Schlüssel) oder [UNSPLASH]. */
    val quelle: String = COMMONS
) {
    companion object {
        const val COMMONS = "commons"
        const val UNSPLASH = "unsplash"
        const val STANDARD_THEMA = "landscape"
        val THEMA = Regex("^[\\p{L}0-9 ,-]{1,40}$")
        val SCHLUESSEL = Regex("^[A-Za-z0-9_-]{20,80}$")
    }
}

/** Aussehen der eingebauten Uhr. */
data class UhrStil(
    val schrift: String = Schrift.GRUNDSCHRIFT,
    /** [OBEN], [UNTEN] oder [AUS]. */
    val datum: String = UNTEN,
    /** [LANG] „Freitag, 16. Oktober“, [OHNE_WOCHENTAG] „16. Oktober 2026“, [ZAHL] „16.10.2026“, [WOCHENTAG] „Freitag“. */
    val format: String = LANG,
    /** Uhrzeit mit Sekunden (zeichnet dann jede Sekunde neu). */
    val sekunden: Boolean = false
) {
    companion object {
        const val OBEN = "oben"
        const val UNTEN = "unten"
        const val AUS = "aus"
        const val LANG = "lang"
        const val OHNE_WOCHENTAG = "ohne_wochentag"
        const val ZAHL = "zahl"
        const val WOCHENTAG = "wochentag"
        val DATUM = setOf(OBEN, UNTEN, AUS)
        val FORMATE = setOf(LANG, OHNE_WOCHENTAG, ZAHL, WOCHENTAG)
    }
}

/** Die eingebauten Schriften (Grundschrift von Christian Urff, CC BY 3.0). */
object Schrift {
    const val SYSTEM = "system"
    const val GRUNDSCHRIFT = "grundschrift"
    const val FETT = "grundschrift_fett"
    const val LEICHT = "grundschrift_leicht"
    const val PUNKT = "grundschrift_punkt"
    const val KONTUR = "grundschrift_kontur"
    val ALLE = listOf(GRUNDSCHRIFT, FETT, LEICHT, PUNKT, KONTUR, SYSTEM)
}

/**
 * Wischgesten. Im Startbildschirm: [hoch]/[runter] aus der Mitte. Am Bildschirmrand (auch über
 * anderen Apps, braucht „Über anderen Apps einblenden“): vom Rand zur Mitte hin.
 */
data class Gesten(
    val hoch: String = APPS,
    val runter: String = AUS,
    val randUnten: String = START,
    val randLinks: String = AUS,
    val randRechts: String = AUS
) {
    val randAn get() = listOf(randUnten, randLinks, randRechts).any { it != AUS }

    companion object {
        const val AUS = "aus"
        const val START = "start"
        const val APPS = "apps"
        const val EINSTELLUNGEN = "einstellungen"
        val IM_LAUNCHER = setOf(AUS, APPS, EINSTELLUNGEN)
        val AM_RAND = setOf(AUS, START, APPS)
    }
}

/** PBKDF2-Hash mit Salt (beides Base64), Iterationen. */
data class PasswortHash(val hash: String, val salt: String, val runden: Int)

/** Ein Element auf dem Startbildschirm: belegt [breite] × [hoehe] Zellen ab ([spalte], [zeile]). */
sealed class Element {
    companion object {
        /** „Anbieter“ der eingebauten Uhr. */
        const val UHR = "de.oejendorferdamm.dammlauncher/Uhr"
        /** Dieselbe Uhr ohne Fläche: weiße Schrift mit Schatten direkt auf dem Hintergrund. */
        const val UHR_TRANSPARENT = "de.oejendorferdamm.dammlauncher/UhrTransparent"
        /** „Noch 12 min bis zur Pause“ (Zeiten aus [Konfig.pausen]). */
        const val PAUSE = "de.oejendorferdamm.dammlauncher/Pause"
        /** „Heute fehlen 2“ – nur die Zahl für die Klasse dieses Boards. */
        const val ABWESEND = "de.oejendorferdamm.dammlauncher/Abwesend"
        /** QR-Code mit Link oder WLAN-Zugang ([Widget.text]). */
        const val QR = "de.oejendorferdamm.dammlauncher/QrCode"
        /** „3. Stunde · Mathe · bis 10:15“ (Zeiten aus [Konfig.stunden], Fächer aus dem Stundenplan). */
        const val STUNDE = "de.oejendorferdamm.dammlauncher/Stunde"
        /** Großer Lautstärke-Regler. */
        const val LAUTSTAERKE = "de.oejendorferdamm.dammlauncher/Lautstaerke"
        /** Zufallsname bzw. Zufallszahl ([Widget.text] = Bereich, z. B. „1-28“). */
        const val ZUFALL = "de.oejendorferdamm.dammlauncher/Zufall"
        /** Bildschirm teilen: startet die Freigabe-App ([Widget.text] = „paket/aktivität“) mit großem Hinweis. */
        const val FREIGABE = "de.oejendorferdamm.dammlauncher/Freigabe"
        /** Schnelleinstellungen: Lautstärke, Helligkeit, WLAN, Abdecken, Aufräumen. */
        const val SCHNELL = "de.oejendorferdamm.dammlauncher/Schnell"
        val EINGEBAUT = setOf(UHR, UHR_TRANSPARENT, PAUSE, ABWESEND, QR, STUNDE, LAUTSTAERKE, ZUFALL, FREIGABE, SCHNELL)
        /** Eingebaute Kacheln, die [Widget.text] benutzen. */
        val MIT_TEXT = setOf(QR, ZUFALL, FREIGABE)
    }

    abstract val spalte: Int
    abstract val zeile: Int
    abstract val breite: Int
    abstract val hoehe: Int
    abstract fun verschoben(spalte: Int, zeile: Int): Element
    abstract fun mitGroesse(breite: Int, hoehe: Int): Element
    /** Eindeutig auf diesem Homescreen. */
    abstract val schluessel: String

    /**
     * App über Paketname + Activity (gerätunabhängig). [titel] = eigener Name (null = Name der App),
     * [icon] = eigenes Symbol als Datei im Ordner „icons“ (null = Symbol der App).
     */
    data class App(
        val paket: String,
        val aktivitaet: String,
        override val spalte: Int,
        override val zeile: Int,
        val titel: String? = null,
        val icon: String? = null
    ) : Element() {
        override val breite get() = 1
        override val hoehe get() = 1
        override val schluessel get() = "app:$paket/$aktivitaet"
        override fun verschoben(spalte: Int, zeile: Int) = copy(spalte = spalte, zeile = zeile)
        override fun mitGroesse(breite: Int, hoehe: Int) = this
    }

    /**
     * Widget über den Provider (ComponentName als Text) – NICHT über die Widget-Id, die ist je Gerät
     * anders. [kennung] verbindet das Widget mit der gerätespezifischen Id (lokal gespeichert).
     */
    data class Widget(
        val provider: String,
        val kennung: String,
        override val spalte: Int,
        override val zeile: Int,
        override val breite: Int,
        override val hoehe: Int,
        /** Inhalt vergrößern (Prozent): viele Widgets haben feste Größen und wirken auf großen Boards winzig. */
        val zoom: Int = 100,
        /** Nur für die eingebaute Uhr: Schrift und Datum. */
        val uhr: UhrStil = UhrStil(),
        /** QR-Code: sein Inhalt; Zufall: Zahlenbereich; Freigabe: „paket/aktivität“ der Freigabe-App. */
        val text: String = ""
    ) : Element() {
        /** Die eingebaute Digitaluhr – kein Android-Widget, braucht keine Freigabe. */
        val istUhr get() = provider == UHR || provider == UHR_TRANSPARENT
        /** Uhr, Pause, Abwesend, QR: von DammLauncher selbst gezeichnet. */
        val eingebaut get() = provider in EINGEBAUT
        override val schluessel get() = "widget:$kennung"
        override fun verschoben(spalte: Int, zeile: Int) = copy(spalte = spalte, zeile = zeile)
        override fun mitGroesse(breite: Int, hoehe: Int) = copy(breite = breite, hoehe = hoehe)
    }
}

/**
 * Ordner (1 × 1) mit mehreren Apps – antippen öffnet ihn groß. Die Apps darin behalten eigene Namen
 * und Symbole; ihre Zellen-Angaben sind ohne Bedeutung.
 */
data class Ordner(
    val kennung: String,
    val name: String,
    val apps: List<Element.App>,
    override val spalte: Int,
    override val zeile: Int
) : Element() {
    override val breite get() = 1
    override val hoehe get() = 1
    override val schluessel get() = "ordner:$kennung"
    override fun verschoben(spalte: Int, zeile: Int) = copy(spalte = spalte, zeile = zeile)
    override fun mitGroesse(breite: Int, hoehe: Int) = this

    companion object {
        const val MAX_APPS = 24
    }
}

/** Grenzen aller Zahlenwerte – alles außerhalb wird beim Einlesen auf den nächsten gültigen Wert gesetzt. */
object Grenzen {
    val RASTER = 1..10
    val RAND = 0..600
    val ABSTAND = 0..200
    val ICON = 20..100
    val TEXT = 10..60
    val ZOOM = 50..400
    val SCHONER = 0..120
    val ZURUECK = 1..240
    const val QR_TEXT = 600
    const val DOCK = 10
    val LAUTSTAERKE = -1..100
}
