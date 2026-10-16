package de.oejendorferdamm.dammlauncher

import de.oejendorferdamm.dammlauncher.hintergrund.Unsplash
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UnsplashTest {
    private fun antwort(raw: String, zaehlen: String = "http://127.0.0.1:8766/unsplash/photos/abc/download") = """
        {"urls": {"raw": "$raw"},
         "user": {"name": "Anna Berg", "links": {"html": "https://unsplash.com/@anna"}},
         "links": {"html": "https://unsplash.com/photos/abc", "download_location": "$zaehlen"}}
    """

    @Test
    fun fotoMitPassenderGroesse() {
        val f = Unsplash.auswerten(antwort("http://127.0.0.1:8766/unsplash/bild.jpg?ixid=1"), 1920, 1080)!!
        assertEquals("http://127.0.0.1:8766/unsplash/bild.jpg?ixid=1&w=1920&h=1080&fit=crop&crop=entropy&q=82&fm=jpg", f.bild)
        assertEquals("Anna Berg", f.fotograf)
        assertEquals("https://unsplash.com/photos/abc", f.seite)
    }

    @Test
    fun fremdeAdressenWerdenAbgelehnt() {
        assertNull(Unsplash.auswerten(antwort("https://boese.example/bild.jpg"), 1920, 1080))
        assertNull(Unsplash.auswerten(antwort("http://127.0.0.1:8766/unsplash/bild.jpg", "https://boese.example/zaehlen"), 1920, 1080))
        assertNull(Unsplash.auswerten("{}", 1920, 1080))
    }
}
