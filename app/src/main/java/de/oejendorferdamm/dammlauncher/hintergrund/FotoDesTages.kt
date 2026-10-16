/*
 * DammLauncher – Copyright (C) 2026 Teyro – GNU GPL v3 oder neuer, siehe LICENSE
 */
package de.oejendorferdamm.dammlauncher.hintergrund

import android.content.Context
import de.oejendorferdamm.dammlauncher.BuildConfig
import de.oejendorferdamm.dammlauncher.konfig.Datei
import de.oejendorferdamm.dammlauncher.konfig.Geraet
import de.oejendorferdamm.dammlauncher.konfig.Hintergrund
import de.oejendorferdamm.dammlauncher.konfig.KonfigSpeicher
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Jeden Tag ein neues Landschaftsfoto als Hintergrund – aus einer von zwei Quellen:
 *
 * - **Wikimedia Commons** (Standard): „Exzellente Bilder“ aus Landschafts-Kategorien, alle frei
 *   lizenziert (meist CC BY-SA). Kein Schlüssel nötig.
 * - **Unsplash**: offizielle API mit dem „Access Key“ aus der Einrichtung.
 *
 * Geladen wird nur von den Bildservern der Quelle, auf Bildschirmgröße zugeschnitten und als
 * foto_des_tages.jpg gespeichert. Klappt etwas nicht, bleibt das Foto von gestern einfach stehen.
 */
object FotoDesTages {
    private val UNSPLASH_API = BuildConfig.UNSPLASH_API
    private val COMMONS_API = BuildConfig.COMMONS_API
    private const val MAX_BILD = 15L * 1024 * 1024
    /** Nach einem Fehlschlag frühestens so viel später noch einmal versuchen. */
    private const val PAUSE = 30 * 60_000L
    private val KENNUNG = "DammLauncher/${BuildConfig.VERSION_NAME} (https://github.com/Teyro/DammLauncher)"

    /** Exzellente Bilder auf Commons – Landschaften doppelt, damit sie am häufigsten kommen. */
    private val KATEGORIEN = listOf(
        "Featured pictures of landscapes", "Featured pictures of landscapes", "Featured pictures of mountains",
        "Featured pictures of lakes", "Featured pictures of coasts", "Featured pictures of forests",
        "Featured pictures of beaches", "Featured pictures of waterfalls"
    )

    /** [nachweis] steht klein unten rechts (Fotograf, Lizenz, Quelle) – die Lizenzen verlangen das. */
    data class Foto(val bild: String, val nachweis: String, val seite: String, val zaehlen: String = "")

    fun datei(): File = File(KonfigSpeicher.hintergrundDatei.parentFile, "foto_des_tages.jpg")

    private fun heute() = SimpleDateFormat("yyyy-MM-dd", Locale.GERMAN).format(Date())

    /** Woran man merkt, dass das gespeicherte Foto nicht mehr zur Einstellung passt. */
    private fun art(h: Hintergrund) = if (h.quelle == Hintergrund.UNSPLASH) "unsplash:${h.thema}" else "commons"

    private fun bereit(h: Hintergrund) = h.unsplash && (h.quelle == Hintergrund.COMMONS || h.schluessel.isNotEmpty())

    /** Muss heute (noch) ein Foto geholt werden? */
    fun faellig(): Boolean {
        val h = KonfigSpeicher.konfig.hintergrund
        if (!bereit(h)) return false
        val veraltet = Geraet.unsplashTag != heute() || Geraet.unsplashThema != art(h) || !datei().exists()
        return veraltet && System.currentTimeMillis() - Geraet.unsplashVersuch > PAUSE
    }

    /** Neues Foto holen; gibt eine Meldung für die Einstellungen zurück. */
    @Synchronized
    fun laden(context: Context, vonHand: Boolean = false): String {
        KonfigSpeicher.init(context)
        Geraet.init(context)
        val h = KonfigSpeicher.konfig.hintergrund
        if (!h.unsplash) return "Foto des Tages ist aus."
        if (!bereit(h)) return "Bitte zuerst den Unsplash-Schlüssel eintragen."
        if (!vonHand && !faellig()) return "Das Foto von heute ist schon da."
        Geraet.unsplashVersuch = System.currentTimeMillis()
        File(datei().parentFile, "unsplash.jpg").delete() // Datei von 1.1.0
        val ergebnis = try {
            val foto = if (h.quelle == Hintergrund.UNSPLASH) unsplash(h.schluessel, h.thema) else commons()
            Datei.einpassen(bildHolen(foto.bild), Geraet.bildBreite, Geraet.bildHoehe, datei())
            Geraet.unsplashTag = heute()
            Geraet.unsplashThema = art(h)
            Geraet.fotoNachweis = foto.nachweis
            Geraet.unsplashSeite = foto.seite
            // Unsplash-Regel: jeden „Download“ melden (zählt für den Fotografen)
            if (foto.zaehlen.isNotEmpty()) try { anfrage(foto.zaehlen, h.schluessel, 64 * 1024) } catch (_: Exception) {}
            "Neues Foto geladen (${foto.nachweis})."
        } catch (e: Exception) {
            "Fehler: ${e.message ?: "Foto nicht geladen"}"
        }
        Geraet.unsplashMeldung = "${SimpleDateFormat("dd.MM. HH:mm", Locale.GERMAN).format(Date())} – $ergebnis"
        return ergebnis
    }

    // ------------------------------------------------------------ Wikimedia Commons

    private fun commons(): Foto {
        // Commons liefert Vorschaubilder nur in festen Breiten – 1920 oder 3840
        val breite = if (Geraet.bildBreite > 1920) 3840 else 1920
        repeat(4) {
            val kategorie = URLEncoder.encode("Category:" + KATEGORIEN.random(), "UTF-8")
            val start = ('A'..'Z').random()
            val text = String(anfrage(
                "$COMMONS_API?action=query&format=json&formatversion=2&generator=categorymembers&gcmtitle=$kategorie" +
                    "&gcmtype=file&gcmlimit=40&gcmstartsortkeyprefix=$start&prop=imageinfo&iiprop=url%7Csize%7Cmime%7Cextmetadata" +
                    "&iiurlwidth=$breite&iiextmetadatafilter=Artist%7CLicenseShortName", null, 512 * 1024
            ), Charsets.UTF_8)
            commonsAuswerten(text).randomOrNull()?.let { return it }
        }
        throw IOException("Kein passendes Foto gefunden")
    }

    /** Antwort der Commons-API lesen: nur breite Querformat-JPEGs von den Wikimedia-Bildservern (testbar). */
    fun commonsAuswerten(json: String): List<Foto> = try {
        val seiten = JSONObject(json).optJSONObject("query")?.optJSONArray("pages")
        (0 until (seiten?.length() ?: 0)).mapNotNull { i ->
            val info = seiten!!.optJSONObject(i)?.optJSONArray("imageinfo")?.optJSONObject(0) ?: return@mapNotNull null
            val b = info.optInt("width")
            val h = info.optInt("height")
            val bild = info.optString("thumburl").ifEmpty { info.optString("url") }
            val meta = info.optJSONObject("extmetadata")
            val wer = ohneHtml(meta?.optJSONObject("Artist")?.optString("value").orEmpty()).ifEmpty { "Unbekannt" }.take(50)
            val lizenz = ohneHtml(meta?.optJSONObject("LicenseShortName")?.optString("value").orEmpty()).take(30)
            if (info.optString("mime") != "image/jpeg" || b < 1600 || b < h * 1.3 || !commonsBild(bild)) null
            else Foto(bild, listOf(wer, lizenz, "Wikimedia Commons").filter { it.isNotEmpty() }.joinToString(" · "), info.optString("descriptionurl"))
        }
    } catch (_: Exception) {
        emptyList()
    }

    /** Commons liefert Vorschaubilder von thumb.wikimedia.org, Originale von upload.wikimedia.org. */
    private fun commonsBild(a: String) = bildErlaubt(a, "thumb.wikimedia.org") || bildErlaubt(a, "upload.wikimedia.org")

    private fun ohneHtml(t: String) = t.replace(Regex("<[^>]*>"), " ").replace("&amp;", "&").replace("&nbsp;", " ")
        .replace(Regex("&[a-z#0-9]+;"), "").replace(Regex("\\s+"), " ").trim()

    // ------------------------------------------------------------ Unsplash

    private fun unsplash(schluessel: String, thema: String): Foto {
        val q = URLEncoder.encode(thema, "UTF-8")
        val text = String(anfrage("$UNSPLASH_API/photos/random?orientation=landscape&content_filter=high&query=$q", schluessel, 256 * 1024), Charsets.UTF_8)
        return auswerten(text, Geraet.bildBreite, Geraet.bildHoehe) ?: throw IOException("Unerwartete Antwort von Unsplash")
    }

    /** Antwort von /photos/random lesen (ohne Netz, testbar). null = unbrauchbar. */
    fun auswerten(json: String, breite: Int, hoehe: Int): Foto? {
        return try {
            val o = JSONObject(json)
            val roh = o.getJSONObject("urls").getString("raw")
            val links = o.getJSONObject("links")
            val bild = "$roh${if ('?' in roh) '&' else '?'}w=$breite&h=$hoehe&fit=crop&crop=entropy&q=82&fm=jpg"
            val zaehlen = links.getString("download_location")
            val wer = o.getJSONObject("user").optString("name").trim().take(50).ifEmpty { "Unbekannt" }
            if (!bildErlaubt(bild, "images.unsplash.com") || !zaehlen.startsWith("$UNSPLASH_API/")) null
            else Foto(bild, "$wer · Unsplash", links.optString("html"), zaehlen)
        } catch (_: Exception) {
            null
        }
    }

    // ------------------------------------------------------------ Netz

    private fun bildErlaubt(adresse: String, host: String): Boolean = try {
        val u = URL(adresse)
        (u.protocol == "https" && u.host == host) || (BuildConfig.DEBUG && adresse.startsWith("http://127.0.0.1:"))
    } catch (_: Exception) {
        false
    }

    private fun bildHolen(adresse: String): ByteArray {
        if (!commonsBild(adresse) && !bildErlaubt(adresse, "images.unsplash.com")) throw IOException("Unerwartete Bildadresse")
        return anfrage(adresse, null, MAX_BILD)
    }

    /** GET ohne Weiterleitungen, mit Größenbremse. */
    private fun anfrage(adresse: String, schluessel: String?, max: Long): ByteArray {
        val c = URL(adresse).openConnection() as HttpURLConnection
        c.instanceFollowRedirects = false
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        // Wikimedia verlangt eine erkennbare Kennung mit Kontakt
        c.setRequestProperty("User-Agent", KENNUNG)
        if (schluessel != null) {
            c.setRequestProperty("Authorization", "Client-ID $schluessel")
            c.setRequestProperty("Accept-Version", "v1")
        }
        try {
            when (val code = c.responseCode) {
                200 -> Unit
                401 -> throw IOException("Unsplash-Schlüssel ungültig")
                403, 429 -> throw IOException("Zu viele Anfragen – später noch einmal")
                404 -> throw IOException("Kein Foto zu diesem Thema gefunden")
                else -> throw IOException("Bildquelle antwortet nicht (HTTP $code)")
            }
            val aus = ByteArrayOutputStream()
            c.inputStream.use { ein ->
                val puffer = ByteArray(64 * 1024)
                while (true) {
                    val n = ein.read(puffer)
                    if (n < 0) break
                    aus.write(puffer, 0, n)
                    if (aus.size() > max) throw IOException("Antwort ist unerwartet groß")
                }
            }
            return aus.toByteArray()
        } finally {
            c.disconnect()
        }
    }
}
