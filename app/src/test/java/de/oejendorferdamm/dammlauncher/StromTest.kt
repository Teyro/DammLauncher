package de.oejendorferdamm.dammlauncher

import de.oejendorferdamm.dammlauncher.nutzung.Strom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StromTest {
    private val min = 60_000L

    @Test
    fun steckerGezogen() {
        // letzter Puls 10:00, kein Herunterfahren, um 10:30 läuft das Board seit 2 Minuten
        val e = Strom.bewerte(letzterPuls = 1000 * min, herunterGefahren = 0, jetzt = 1030 * min, laufzeit = 2 * min)!!
        assertEquals(1000 * min, e.von)
        assertEquals(1028 * min, e.bis)
        assertEquals(false, e.sauber)
    }

    @Test
    fun normalHeruntergefahren() {
        val e = Strom.bewerte(1000 * min, herunterGefahren = 1000 * min + 20_000, jetzt = 1100 * min, laufzeit = 3 * min)!!
        assertEquals(true, e.sauber)
    }

    @Test
    fun nurLauncherNeuGestartet() {
        // Gerät läuft seit Stunden – nur der Launcher war kurz weg
        assertNull(Strom.bewerte(1000 * min, 0, 1010 * min, laufzeit = 300 * min))
        assertNull(Strom.bewerte(0, 0, 1010 * min, 1 * min))
    }
}
