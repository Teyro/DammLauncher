package de.oejendorferdamm.dammlauncher

import de.oejendorferdamm.dammlauncher.hintergrund.Bildwahl
import de.oejendorferdamm.dammlauncher.hintergrund.Bildwahl.Jahreszeit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BildwahlTest {
    @Test
    fun grundschulFilterSperrtUngeeignetes() {
        // genau die Fälle, die der Lehrer gemeldet hat
        assertFalse(Bildwahl.erlaubt("Jesus statue covered in snow"))
        assertFalse(Bildwahl.erlaubt("Nuclear explosion mushroom cloud over Hiroshima"))
        assertFalse(Bildwahl.erlaubt("Old English engraving of a battle, 1850"))
        // weiteres Militär/Religion/Historie
        assertFalse(Bildwahl.erlaubt("Soldiers with a tank in the war"))
        assertFalse(Bildwahl.erlaubt("Crucifix in a cemetery"))
        assertFalse(Bildwahl.erlaubt("Portrait of a king, vintage painting of the court"))
    }

    @Test
    fun grundschulFilterLaesstLandschaftUndGebaeudeDurch() {
        assertTrue(Bildwahl.erlaubt("Snowy mountain landscape at sunrise"))
        assertTrue(Bildwahl.erlaubt("Spring meadow with blossoming trees"))
        assertTrue(Bildwahl.erlaubt("Modern glass office building in the city"))
        assertTrue(Bildwahl.erlaubt("Autumn forest with a waterfall"))
        assertTrue(Bildwahl.erlaubt("Old castle on a hill above the river"))
        // kurze Wörter dürfen harmlose Begriffe nicht fälschlich treffen (war in toward/warm, cross in across)
        assertTrue(Bildwahl.erlaubt("A warm path leading toward the lake, across the valley"))
        // häufige Landschaften, die ein zu grober Filter verlieren würde
        assertTrue(Bildwahl.erlaubt("Christmas market in the snow"))
        assertTrue(Bildwahl.erlaubt("Lake Bled|Gorenjska|Featured pictures of Slovenia"))
        assertTrue(Bildwahl.erlaubt("Dead Vlei at sunrise, Namibia"))
        assertTrue(Bildwahl.erlaubt("Christchurch botanic gardens in autumn"))
    }

    @Test
    fun religioeseMotiveInLandschaften() {
        assertFalse(Bildwahl.erlaubt("Christ the Redeemer above Rio"))
        assertFalse(Bildwahl.erlaubt("Kalvarienberg im Winter"))
        assertFalse(Bildwahl.erlaubt("Wegkreuz am Feldrand"))
        assertFalse(Bildwahl.erlaubt("Interior with main altar"))
    }

    @Test
    fun unsplashSucheWirdSchrittweiseWeiter() {
        val q = Bildwahl.unsplashQueries("landscape", Jahreszeit.HERBST, "rain")
        assertEquals(listOf("landscape autumn rain nature", "landscape autumn nature", "landscape"), q)
        // ohne Wetter keine doppelten Versuche
        assertEquals(2, Bildwahl.unsplashQueries("landscape", Jahreszeit.HERBST, null).size)
    }

    @Test
    fun jahreszeitMeteorologisch() {
        assertEquals(Jahreszeit.WINTER, Bildwahl.jahreszeit(12))
        assertEquals(Jahreszeit.WINTER, Bildwahl.jahreszeit(2))
        assertEquals(Jahreszeit.FRUEHLING, Bildwahl.jahreszeit(4))
        assertEquals(Jahreszeit.SOMMER, Bildwahl.jahreszeit(7))
        assertEquals(Jahreszeit.HERBST, Bildwahl.jahreszeit(11))
    }

    @Test
    fun querysUndKategorienFolgenJahreszeitUndWetter() {
        val q = Bildwahl.unsplashQuery("mountains", Jahreszeit.WINTER, "snow")
        assertTrue(q.contains("winter"))
        assertTrue(q.contains("snow"))
        assertTrue(q.contains("mountains"))   // eingestelltes Thema bleibt erhalten
        assertTrue(q.contains("landscape"))

        // Wetter „Schnee“ zieht die Commons-Auswahl in den Winter, auch im Herbst
        assertTrue(Bildwahl.commonsKategorien(Jahreszeit.HERBST, winterWetter = true).any { it.contains("snow", true) })
        assertTrue(Bildwahl.commonsKategorien(Jahreszeit.FRUEHLING, winterWetter = false).any { it.contains("flowers", true) })
        // Gebäude sind immer dabei
        assertTrue(Bildwahl.commonsKategorien(Jahreszeit.SOMMER, winterWetter = false).any { it.contains("building", true) })
    }

    @Test
    fun wetterCodeZuWort() {
        assertEquals("snow", Bildwahl.wetterWort(73, -3.0))
        assertEquals("snow", Bildwahl.wetterWort(0, -5.0))      // klar, aber Frost → Schnee-Motiv
        assertEquals("rain", Bildwahl.wetterWort(61, 8.0))
        assertEquals("fog mist", Bildwahl.wetterWort(45, 6.0))
        assertEquals("sunny clear sky", Bildwahl.wetterWort(0, 20.0))
    }
}
