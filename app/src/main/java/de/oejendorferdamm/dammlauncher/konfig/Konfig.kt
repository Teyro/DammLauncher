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
    val elemente: List<Element> = emptyList(),
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

/** Hintergrund: Farbe (ARGB) und ob ein Bild (Datei hintergrund.jpg) darüber liegt. */
data class Hintergrund(val farbe: Int = 0xFF1E3A5F.toInt(), val bild: Boolean = false)

/** PBKDF2-Hash mit Salt (beides Base64), Iterationen. */
data class PasswortHash(val hash: String, val salt: String, val runden: Int)

/** Ein Element auf dem Startbildschirm: belegt [breite] × [hoehe] Zellen ab ([spalte], [zeile]). */
sealed class Element {
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
        override val hoehe: Int
    ) : Element() {
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
}
