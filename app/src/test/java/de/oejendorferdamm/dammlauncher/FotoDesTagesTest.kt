package de.oejendorferdamm.dammlauncher

import de.oejendorferdamm.dammlauncher.hintergrund.FotoDesTages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FotoDesTagesTest {
    private fun antwort(raw: String, zaehlen: String = "http://127.0.0.1:8766/unsplash/photos/abc/download") = """
        {"urls": {"raw": "$raw"},
         "user": {"name": "Anna Berg", "links": {"html": "https://unsplash.com/@anna"}},
         "links": {"html": "https://unsplash.com/photos/abc", "download_location": "$zaehlen"}}
    """

    @Test
    fun unsplashFotoMitPassenderGroesse() {
        val f = FotoDesTages.auswerten(antwort("http://127.0.0.1:8766/unsplash/bild.jpg?ixid=1"), 1920, 1080)!!
        assertEquals("http://127.0.0.1:8766/unsplash/bild.jpg?ixid=1&w=1920&h=1080&fit=crop&crop=entropy&q=82&fm=jpg", f.bild)
        assertEquals("Anna Berg · Unsplash", f.nachweis)
        assertEquals("https://unsplash.com/photos/abc", f.seite)
    }

    @Test
    fun unsplashFremdeAdressenWerdenAbgelehnt() {
        assertNull(FotoDesTages.auswerten(antwort("https://boese.example/bild.jpg"), 1920, 1080))
        assertNull(FotoDesTages.auswerten(antwort("http://127.0.0.1:8766/unsplash/bild.jpg", "https://boese.example/zaehlen"), 1920, 1080))
        assertNull(FotoDesTages.auswerten("{}", 1920, 1080))
    }

    private fun seite(url: String, b: Int, h: Int, mime: String = "image/jpeg") = """
        {"imageinfo": [{"width": $b, "height": $h, "mime": "$mime", "thumburl": "$url",
          "descriptionurl": "https://commons.wikimedia.org/wiki/File:Berg.jpg",
          "extmetadata": {"Artist": {"value": "<a href=\"//commons.wikimedia.org/wiki/User:Kim\">Kim&nbsp;Lee</a>"},
                          "LicenseShortName": {"value": "CC BY-SA 4.0"}}}]}
    """

    @Test
    fun commonsNurBreiteJpegsVomBildserver() {
        val gut = "https://thumb.wikimedia.org/wikipedia/commons/thumb/a/ab/Berg.jpg/1920px-Berg.jpg?utm_source=commons.wikimedia.org"
        val json = """{"query": {"pages": [
            ${seite(gut, 6000, 4000)},
            ${seite(gut, 3000, 4000)},
            ${seite(gut, 1200, 600)},
            ${seite(gut, 6000, 3000, "image/png")},
            ${seite("https://boese.example/x.jpg", 6000, 3000)}
        ]}}"""
        val fotos = FotoDesTages.commonsAuswerten(json)
        assertEquals(1, fotos.size)
        assertEquals(gut, fotos[0].bild)
        assertEquals("Kim Lee · CC BY-SA 4.0 · Wikimedia Commons", fotos[0].nachweis)
        assertEquals(0, FotoDesTages.commonsAuswerten("kaputt").size)
    }

    @Test
    fun echteCommonsAntwort() {
        val json = javaClass.classLoader!!.getResource("commons_antwort.json").readText()
        val fotos = FotoDesTages.commonsAuswerten(json)
        assertEquals(true, fotos.isNotEmpty())
        fotos.forEach {
            assertEquals(true, it.bild.startsWith("https://thumb.wikimedia.org/"))
            assertEquals(true, it.nachweis.endsWith("· Wikimedia Commons"))
            assertEquals(true, it.seite.startsWith("https://commons.wikimedia.org/"))
        }
    }
}
