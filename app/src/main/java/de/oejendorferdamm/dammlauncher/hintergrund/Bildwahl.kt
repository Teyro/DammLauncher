/*
 * DammLauncher – Copyright (C) 2026 Teyro – GNU GPL v3 oder neuer, siehe LICENSE
 */
package de.oejendorferdamm.dammlauncher.hintergrund

import java.util.Calendar

/**
 * Entscheidet, welches Hintergrundfoto auf eine Grundschul-Tafel darf und wonach gesucht wird.
 *
 * - [erlaubt] ist der feste Sicherheitsfilter: er lässt niemals religiöse Figuren (z. B. Jesus),
 *   Krieg/Militär/Nuklear, historische Abbildungen (Stiche, Gemälde) oder sonst Grundschul-Untaugliches
 *   durch. Kirchen, Burgen und andere Gebäude als Motiv bleiben erlaubt – nur die genannten Inhalte nicht.
 * - [jahreszeit], [unsplashQuery] und [commonsKategorien] richten die Bildwahl nach Jahreszeit (und, wenn
 *   bekannt, nach dem Wetter) aus: Landschaft/Natur und Gebäude, passend zur Zeit.
 *
 * Reine Logik, ohne Netz – deshalb testbar (BildwahlTest).
 */
object Bildwahl {
    enum class Jahreszeit(val wort: String) { FRUEHLING("spring"), SOMMER("summer"), HERBST("autumn"), WINTER("winter") }

    /** Meteorologische Jahreszeit (Nordhalbkugel): Frühling Mär–Mai, Sommer Jun–Aug, Herbst Sep–Nov, Winter Dez–Feb. */
    fun jahreszeit(monat1bis12: Int): Jahreszeit = when (monat1bis12) {
        3, 4, 5 -> Jahreszeit.FRUEHLING
        6, 7, 8 -> Jahreszeit.SOMMER
        9, 10, 11 -> Jahreszeit.HERBST
        else -> Jahreszeit.WINTER
    }

    fun jahreszeitJetzt(): Jahreszeit = jahreszeit(Calendar.getInstance().get(Calendar.MONTH) + 1)

    // ---- Sicherheitsfilter (Blacklist) ------------------------------------------------------------

    /** Kurze, mehrdeutige Wörter nur als ganzes Wort sperren (sonst träfe „war“ auch „toward“, „warm“). */
    private val WORT = Regex(
        "\\b(" + listOf(
            "war", "wars", "krieg", "bomb", "bombe", "bombs", "atom", "tank", "tanks", "panzer", "army", "armee",
            "gun", "guns", "rifle", "pistol", "missile", "soldier", "soldiers", "soldat", "battle", "nazi", "ss",
            "cross", "crosses", "nude", "naked", "nackt", "sex", "dead", "grave", "graves", "skull", "blood", "icon", "troop", "troops"
        ).joinToString("|") + ")\\b"
    )

    /** Eindeutige Zeichenketten überall sperren (auch als Teilwort, z. B. „bombing“, „kriegsschiff“). */
    private val TEIL = listOf(
        // Religiöse Figuren und Darstellungen (Gebäude wie Kirchen bleiben erlaubt)
        "jesus", "christ", "crucifix", "kruzifix", "crucifixion", "kreuzigung", "gipfelkreuz", "madonna", "buddha",
        "hindu", "biblical", "biblisch", "religion", "religiös", "religios", "prayer", "worship", "heiligenfigur",
        // Krieg / Militär / Nuklear
        "nuclear", "nuklear", "atombombe", "mushroom cloud", "hiroshima", "nagasaki", "weapon", "waffe", "military",
        "militär", "wehrmacht", "hitler", "swastika", "hakenkreuz", "holocaust", "concentration camp", "artillery",
        "warship", "warfare", "kriegs", "fighter jet", "air raid", "luftangriff", "schlacht", "grenade", "granate",
        // Historische Abbildungen / Kunstdrucke (nicht moderne Fotos von alten Gebäuden)
        "engraving", "lithograph", "woodcut", "etching", "reenactment", "manuscript", "portrait of", "painting of",
        "drawing of", "sketch of",
        // Allgemein nichts für die Grundschule
        "corpse", "leiche", "funeral", "beerdigung", "friedhof", "cemetery", "totenkopf", "erotic", "gore"
    )

    /** true = darf gezeigt werden. [text] = Titel + Beschreibung + Tags + Kategorien (Groß/Klein egal). */
    fun erlaubt(text: String): Boolean {
        val t = text.lowercase()
        if (WORT.containsMatchIn(t)) return false
        return TEIL.none { it in t }
    }

    // ---- Suche nach Jahreszeit und Wetter ---------------------------------------------------------

    /**
     * Unsplash-Suchbegriff: das eingestellte Thema (falls eigenes) plus Jahreszeit, Wetter und ein fester
     * Natur-Anker, damit nur Landschaften/Natur/Gebäude kommen und keine Menschen/Historie.
     */
    fun unsplashQuery(thema: String, jz: Jahreszeit, wetterWort: String?): String {
        val teile = LinkedHashSet<String>()
        thema.split(Regex("[ ,]+")).forEach { if (it.isNotBlank()) teile += it.lowercase() }
        teile += jz.wort
        if (!wetterWort.isNullOrBlank()) teile += wetterWort
        teile += "landscape"
        teile += "nature"
        return teile.joinToString(" ").take(90)
    }

    /**
     * Commons-Kategorien („Featured pictures of …“) passend zur Jahreszeit, gemischt mit Gebäuden.
     * [winterWetter] zieht die Auswahl unabhängig vom Kalender in Richtung Schnee/Winter.
     * Ungültige Kategorienamen sind unkritisch – FotoDesTages probiert mehrere und nimmt die erste, die trägt.
     */
    fun commonsKategorien(jz: Jahreszeit, winterWetter: Boolean): List<String> {
        val basis = listOf(
            "Featured pictures of landscapes", "Featured pictures of landscapes",
            "Featured pictures of mountains", "Featured pictures of lakes", "Featured pictures of coasts"
        )
        val season = when (if (winterWetter) Jahreszeit.WINTER else jz) {
            Jahreszeit.FRUEHLING -> listOf("Featured pictures of flowers", "Featured pictures of forests", "Featured pictures of landscapes")
            Jahreszeit.SOMMER -> listOf("Featured pictures of beaches", "Featured pictures of coasts", "Featured pictures of lakes")
            Jahreszeit.HERBST -> listOf("Featured pictures of forests", "Featured pictures of landscapes", "Featured pictures of waterfalls")
            Jahreszeit.WINTER -> listOf("Featured pictures of snow", "Featured pictures of forests", "Featured pictures of mountains")
        }
        val gebaeude = listOf("Featured pictures of buildings", "Featured pictures of architecture", "Featured pictures of castles")
        return basis + season + gebaeude
    }

    /** WMO-Wettercode (open-meteo) + Temperatur → ein Suchwort fürs Wetter, oder null (neutral). */
    fun wetterWort(code: Int, tempC: Double): String? = when {
        code in intArrayOf(71, 73, 75, 77, 85, 86).toList() || tempC <= -1 -> "snow"
        code in 95..99 -> "storm clouds"
        code in 45..48 -> "fog mist"
        code in 51..67 || code in 80..82 -> "rain"
        code in 2..3 -> "cloudy"
        code in 0..1 -> "sunny clear sky"
        else -> null
    }
}
