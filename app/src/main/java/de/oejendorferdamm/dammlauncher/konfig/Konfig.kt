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
    /** Wann diese Einrichtung zuletzt geändert wurde (ms seit 1970) – dient auch als Vorlagen-Version. */
    val stand: Long = 0L
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
        val uhr: UhrStil = UhrStil()
    ) : Element() {
        /** Die eingebaute Digitaluhr – kein Android-Widget, braucht keine Freigabe. */
        val istUhr get() = provider == UHR || provider == UHR_TRANSPARENT
        override val schluessel get() = "widget:$kennung"
        override fun verschoben(spalte: Int, zeile: Int) = copy(spalte = spalte, zeile = zeile)
        override fun mitGroesse(breite: Int, hoehe: Int) = copy(breite = breite, hoehe = hoehe)
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
}
