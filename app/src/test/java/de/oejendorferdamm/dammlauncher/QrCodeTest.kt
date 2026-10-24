package de.oejendorferdamm.dammlauncher

import de.oejendorferdamm.dammlauncher.qr.QrCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class QrCodeTest {
    private val beispiele = listOf(
        "https://oejendorfer-damm.de",
        QrCode.wlan("Gäste;WLAN", "geheim:123"),
        "x".repeat(150),
        "Ä".repeat(160) + "https://example.org/lang?a=1&b=2"
    )

    @Test
    fun groesseUndSuchmuster() {
        val q = QrCode.aus("https://oejendorfer-damm.de")!!
        assertEquals(29, q.groesse) // 27 Bytes + Kopf passen nicht mehr in Version 2 (28 Bytes) → Version 3
        // Suchmuster oben links: 7×7 Rahmen, heller Ring, dunkler Kern
        for (i in 0..6) { assertTrue(q.dunkel(i, 0)); assertTrue(q.dunkel(0, i)); assertTrue(q.dunkel(6, i)) }
        assertTrue(!q.dunkel(1, 1) && q.dunkel(3, 3) && !q.dunkel(7, 7))
        // Taktlinie
        for (i in 8 until q.groesse - 8) assertEquals(i % 2 == 0, q.dunkel(i, 6))
    }

    @Test
    fun zuLang() {
        assertNull(QrCode.aus("x".repeat(QrCode.datenBytes(QrCode.MAX_VERSION))))
        assertTrue(QrCode.aus("x".repeat(QrCode.datenBytes(QrCode.MAX_VERSION) - 3)) != null)
    }

    @Test
    fun wlanText() {
        val t = QrCode.wlan("Gäste;WLAN", "geheim:123")
        assertEquals("WIFI:T:WPA;S:Gäste\\;WLAN;P:geheim\\:123;;", t)
        assertEquals("Gäste;WLAN", QrCode.wlanName(t))
        assertEquals("Offen", QrCode.wlanName(QrCode.wlan("Offen", "")))
        assertNull(QrCode.wlanName("https://x.de"))
    }

    /** Schreibt die Muster als Text – scripts/qr_pruefen.mjs liest sie mit einem fremden Decoder. */
    @Test
    fun ausgabeFuerDecoder() {
        val ordner = File(System.getProperty("qrAusgabe") ?: return).apply { mkdirs() }
        beispiele.forEachIndexed { n, text ->
            val q = QrCode.aus(text)!!
            val zeilen = (0 until q.groesse).joinToString("\n") { y -> (0 until q.groesse).joinToString("") { x -> if (q.dunkel(x, y)) "1" else "0" } }
            File(ordner, "qr$n.txt").writeText(text + "\n" + zeilen)
        }
    }
}
