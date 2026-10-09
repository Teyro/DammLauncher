package de.oejendorferdamm.dammlauncher

import de.oejendorferdamm.dammlauncher.update.Update
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class UpdateTest {
    private fun ms(t: String) = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(t)!!.time

    private fun release(tag: String, datum: String, url: String = "https://github.com/Teyro/DammLauncher/releases/download/$tag/DammLauncher.apk") =
        """{"tag_name":"$tag","published_at":"$datum","body":"Neu","assets":[{"name":"DammLauncher.apk","browser_download_url":"$url","size":1234}]}"""

    @Test
    fun siebenTageRegel() {
        val jetzt = ms("2026-10-20T12:00:00Z")
        // neuer, aber erst 3 Tage alt → Hinweis, keine Sperre
        val a = Update.auswerten(release("v1.1.0", "2026-10-17T12:00:00Z"), jetzt, "1.0.0")!!
        assertTrue(a.neuer)
        assertFalse(a.gesperrt)
        // neuer und 8 Tage alt → Sperre
        val b = Update.auswerten(release("v1.1.0", "2026-10-12T11:00:00Z"), jetzt, "1.0.0")!!
        assertTrue(b.gesperrt)
        // gleiche Version, egal wie alt → nichts
        val c = Update.auswerten(release("v1.0.0", "2025-01-01T00:00:00Z"), jetzt, "1.0.0")!!
        assertFalse(c.neuer)
        assertFalse(c.gesperrt)
        // ältere Version auf GitHub → nichts
        assertFalse(Update.auswerten(release("v0.9.0", "2025-01-01T00:00:00Z"), jetzt, "1.0.0")!!.gesperrt)
    }

    @Test
    fun fremdeOderKaputteAntwortenSperrenNie() {
        val jetzt = ms("2026-10-20T12:00:00Z")
        assertNull(Update.auswerten(release("v9.0.0", "2025-01-01T00:00:00Z", "https://boese.example/x.apk"), jetzt, "1.0.0"))
        assertNull(Update.auswerten("""{"tag_name":"v9.0.0","published_at":"2025-01-01T00:00:00Z","assets":[]}""", jetzt, "1.0.0"))
        assertNull(Update.auswerten(release("kaputt", "2025-01-01T00:00:00Z"), jetzt, "1.0.0"))
        assertNull(Update.auswerten(release("v9.0.0", "gestern"), jetzt, "1.0.0"))
    }

    @Test
    fun versionen() {
        assertTrue(Update.istNeuer("1.0.10", "1.0.9"))
        assertTrue(Update.istNeuer("2.0.0", "1.9.9"))
        assertFalse(Update.istNeuer("1.0.0", "1.0.0"))
        assertFalse(Update.istNeuer("1.0.0", "1.0.1-debug"))
        assertEquals(7, Update.SPERRE_NACH_TAGEN)
    }
}
