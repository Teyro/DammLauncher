/*
 * DammLauncher – Copyright (C) 2026 Teyro – GNU GPL v3 oder neuer, siehe LICENSE
 */
package de.oejendorferdamm.dammlauncher.hintergrund

import android.content.Context
import de.oejendorferdamm.dammlauncher.BuildConfig
import de.oejendorferdamm.dammlauncher.konfig.Datei
import de.oejendorferdamm.dammlauncher.konfig.Geraet
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
 * Jeden Tag ein neues Landschaftsfoto von Unsplash als Hintergrund. Gefragt wird die offizielle API
 * mit dem „Access Key“ aus der Einrichtung; geladen wird nur von images.unsplash.com, auf
 * Bildschirmgröße zugeschnitten und als unsplash.jpg gespeichert. Klappt etwas nicht, bleibt das
 * Foto von gestern (bzw. Farbe/eigenes Bild) einfach stehen.
 */
object Unsplash {
    private val API = BuildConfig.UNSPLASH_API
    private const val BILD_HOST = "images.unsplash.com"
    private const val MAX_BILD = 15L * 1024 * 1024
    /** Nach einem Fehlschlag frühestens so viel später noch einmal versuchen. */
    private const val PAUSE = 30 * 60_000L

    data class Foto(val bild: String, val fotograf: String, val profil: String, val seite: String, val zaehlen: String)

    fun datei(): File = File(KonfigSpeicher.hintergrundDatei.parentFile, "unsplash.jpg")

    private fun heute() = SimpleDateFormat("yyyy-MM-dd", Locale.GERMAN).format(Date())

    /** Muss heute (noch) ein Foto geholt werden? */
    fun faellig(): Boolean {
        val h = KonfigSpeicher.konfig.hintergrund
        if (!h.unsplash || h.schluessel.isEmpty()) return false
        val veraltet = Geraet.unsplashTag != heute() || Geraet.unsplashThema != h.thema || !datei().exists()
        return veraltet && System.currentTimeMillis() - Geraet.unsplashVersuch > PAUSE
    }

    /** Neues Foto holen; gibt eine Meldung für die Einstellungen zurück. */
    @Synchronized
    fun laden(context: Context, vonHand: Boolean = false): String {
        KonfigSpeicher.init(context)
        Geraet.init(context)
        val h = KonfigSpeicher.konfig.hintergrund
        if (!h.unsplash) return "Unsplash ist aus."
        if (h.schluessel.isEmpty()) return "Bitte zuerst den Unsplash-Schlüssel eintragen."
        if (!vonHand && !faellig()) return "Das Foto von heute ist schon da."
        Geraet.unsplashVersuch = System.currentTimeMillis()
        val ergebnis = try {
            val foto = suchen(h.schluessel, h.thema)
            val roh = bildHolen(foto.bild)
            Datei.einpassen(roh, Geraet.bildBreite, Geraet.bildHoehe, datei())
            Geraet.unsplashTag = heute()
            Geraet.unsplashThema = h.thema
            Geraet.unsplashFotograf = foto.fotograf
            Geraet.unsplashSeite = foto.seite
            // Unsplash-Regel: jeden „Download“ melden (zählt für den Fotografen)
            try { anfrage(foto.zaehlen, h.schluessel, 64 * 1024) } catch (_: Exception) {}
            "Neues Foto von ${foto.fotograf} geladen."
        } catch (e: Exception) {
            "Fehler: ${e.message ?: "Foto nicht geladen"}"
        }
        Geraet.unsplashMeldung = "${SimpleDateFormat("dd.MM. HH:mm", Locale.GERMAN).format(Date())} – $ergebnis"
        return ergebnis
    }

    private fun suchen(schluessel: String, thema: String): Foto {
        val q = URLEncoder.encode(thema, "UTF-8")
        val text = String(anfrage("$API/photos/random?orientation=landscape&content_filter=high&query=$q", schluessel, 256 * 1024), Charsets.UTF_8)
        return auswerten(text, Geraet.bildBreite, Geraet.bildHoehe) ?: throw IOException("Unerwartete Antwort von Unsplash")
    }

    /** Antwort von /photos/random lesen (ohne Netz, testbar). null = unbrauchbar. */
    fun auswerten(json: String, breite: Int, hoehe: Int): Foto? {
        return try {
            val o = JSONObject(json)
            val roh = o.getJSONObject("urls").getString("raw")
            val u = o.getJSONObject("user")
            val links = o.getJSONObject("links")
            val bild = "$roh${if ('?' in roh) '&' else '?'}w=$breite&h=$hoehe&fit=crop&crop=entropy&q=82&fm=jpg"
            val zaehlen = links.getString("download_location")
            if (!bildErlaubt(bild) || !zaehlen.startsWith("$API/")) null else Foto(
                bild,
                u.optString("name").trim().take(60).ifEmpty { "Unbekannt" },
                u.optJSONObject("links")?.optString("html").orEmpty(),
                links.optString("html"),
                zaehlen
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun bildErlaubt(adresse: String): Boolean = try {
        val u = URL(adresse)
        (u.protocol == "https" && u.host == BILD_HOST) || (BuildConfig.DEBUG && adresse.startsWith("http://127.0.0.1:"))
    } catch (_: Exception) {
        false
    }

    private fun bildHolen(adresse: String): ByteArray {
        if (!bildErlaubt(adresse)) throw IOException("Unerwartete Bildadresse")
        return anfrage(adresse, null, MAX_BILD)
    }

    /** GET ohne Weiterleitungen, mit Größenbremse. */
    private fun anfrage(adresse: String, schluessel: String?, max: Long): ByteArray {
        val c = URL(adresse).openConnection() as HttpURLConnection
        c.instanceFollowRedirects = false
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        c.setRequestProperty("User-Agent", "DammLauncher")
        if (schluessel != null) {
            c.setRequestProperty("Authorization", "Client-ID $schluessel")
            c.setRequestProperty("Accept-Version", "v1")
        }
        try {
            when (val code = c.responseCode) {
                200 -> Unit
                401 -> throw IOException("Unsplash-Schlüssel ungültig")
                403 -> throw IOException("Unsplash-Limit erreicht – später noch einmal")
                404 -> throw IOException("Kein Foto zu diesem Thema gefunden")
                else -> throw IOException("Unsplash antwortet nicht (HTTP $code)")
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
