package de.oejendorferdamm.dammlauncher

import de.oejendorferdamm.dammlauncher.abgleich.SchulZugang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SchulZugangTest {
    private fun json(klasse: String) = """{"klasse":"$klasse","nextcloud":{"server":"https://cloud.example","benutzer":"tafel",
        "haeuser":[{"jahrgaenge":[4],"passwort":"p4"},{"jahrgaenge":[1,2],"passwort":"p12"},{"jahrgaenge":[0],"passwort":"p0"}]}}"""

    @Test
    fun hausNachJahrgangUndOrdner() {
        val z = SchulZugang.aus(json("2B"))!!
        assertEquals("p12", z.passwort)
        assertEquals("https://cloud.example/remote.php/dav/files/tafel", z.dav)
        assertEquals(listOf("DammLauncher/Klasse 2b", "DammLauncher/Jahrgang 2", "DammLauncher/Alle"), SchulZugang.ordner(z))
        val v = SchulZugang.aus(json("VSK 1"))!!
        assertEquals("p0", v.passwort) // Vorschule, nicht Jahrgang 1
        assertEquals("DammLauncher/Vorschule", SchulZugang.jahrgangOrdner(v.klasse))
        assertNull(SchulZugang.aus(json("3a"))) // kein Haus für Jahrgang 3
        assertNull(SchulZugang.aus("kaputt"))
    }
}
