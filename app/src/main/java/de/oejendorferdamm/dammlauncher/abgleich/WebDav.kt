/*
 * DammLauncher – Copyright (C) 2026 Teyro – GNU GPL v3 oder neuer, siehe LICENSE
 */
package de.oejendorferdamm.dammlauncher.abgleich

import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Schlanker WebDAV-Zugriff (IServ & Co.) über HttpURLConnection: GET, PUT, MKCOL, MOVE, PROPFIND.
 * Nur HTTPS, die Zertifikatsprüfung des Systems bleibt immer an, Weiterleitungen werden nicht
 * verfolgt (Zugangsdaten sollen nie bei einer anderen Adresse landen).
 */
class WebDav(basis: String, benutzer: String, passwort: String) {
    private val basis: String = basis.trim().trimEnd('/')
    private val auth = "Basic " + Base64.encodeToString("$benutzer:$passwort".toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

    init {
        // Nur im Testbuild: http zum eigenen Gerät (Test-Server im Emulator). Die Release-Version kennt nur https.
        val testLokal = de.oejendorferdamm.dammlauncher.BuildConfig.DEBUG && this.basis.startsWith("http://127.0.0.1:")
        if (!this.basis.startsWith("https://") && !testLokal) throw IOException("Nur sichere Adressen (https://) sind erlaubt.")
    }

    class Antwort(val code: Int, val daten: ByteArray?, val etag: String?)

    fun url(pfad: String): String = basis + "/" + pfad.split('/').filter { it.isNotEmpty() }.joinToString("/") {
        URLEncoder.encode(it, "UTF-8").replace("+", "%20")
    }

    private fun oeffne(pfad: String, methode: String): HttpURLConnection {
        val c = URL(url(pfad)).openConnection() as HttpURLConnection
        if (methode == "PROPFIND" || methode == "MKCOL" || methode == "MOVE") {
            // HttpURLConnection kennt diese Methoden nicht – über die Methode-Feld setzen
            try {
                val feld = HttpURLConnection::class.java.getDeclaredField("method")
                feld.isAccessible = true
                feld.set(c, methode)
            } catch (_: Exception) {
                c.requestMethod = "POST"
                c.setRequestProperty("X-HTTP-Method-Override", methode)
            }
        } else c.requestMethod = methode
        c.instanceFollowRedirects = false
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        c.useCaches = false
        c.setRequestProperty("Authorization", auth)
        c.setRequestProperty("User-Agent", "DammLauncher")
        return c
    }

    private fun pruefeCode(c: HttpURLConnection): Int {
        val code = c.responseCode
        when {
            code in 300..399 -> throw IOException("Server leitet weiter – Adresse prüfen (HTTP $code).")
            code == 401 -> throw IOException("Anmeldung abgelehnt – Benutzername/Passwort prüfen.")
            code == 403 -> throw IOException("Keine Berechtigung für diesen Ordner.")
        }
        return code
    }

    /** Datei holen; bei [etag] und unverändert kommt Code 304 ohne Daten. 404 → Code 404. */
    fun get(pfad: String, etag: String? = null, max: Int = 30 * 1024 * 1024): Antwort {
        val c = oeffne(pfad, "GET")
        try {
            etag?.let { c.setRequestProperty("If-None-Match", it) }
            val code = pruefeCode(c)
            if (code == 304 || code == 404) return Antwort(code, null, null)
            if (code !in 200..299) throw IOException("Server antwortet mit Fehler $code.")
            val aus = ByteArrayOutputStream()
            c.inputStream.use { ein ->
                val puffer = ByteArray(32 * 1024)
                while (true) {
                    val n = ein.read(puffer)
                    if (n < 0) break
                    aus.write(puffer, 0, n)
                    if (aus.size() > max) throw IOException("Datei ist zu groß.")
                }
            }
            return Antwort(code, aus.toByteArray(), c.getHeaderField("ETag"))
        } finally {
            c.disconnect()
        }
    }

    fun put(pfad: String, daten: ByteArray, typ: String) {
        val c = oeffne(pfad, "PUT")
        try {
            c.doOutput = true
            c.setFixedLengthStreamingMode(daten.size)
            c.setRequestProperty("Content-Type", typ)
            c.outputStream.use { it.write(daten) }
            val code = pruefeCode(c)
            if (code !in 200..299) throw IOException("Hochladen fehlgeschlagen (HTTP $code).")
        } finally {
            c.disconnect()
        }
    }

    /** Ordner anlegen (auch verschachtelt); „gibt es schon“ ist kein Fehler. */
    fun ordner(pfad: String) {
        var bisher = ""
        for (teil in pfad.split('/').filter { it.isNotEmpty() }) {
            bisher = if (bisher.isEmpty()) teil else "$bisher/$teil"
            val c = oeffne("$bisher/", "MKCOL")
            try {
                val code = pruefeCode(c)
                if (code !in 200..299 && code != 405 && code != 301) throw IOException("Ordner „$bisher“ lässt sich nicht anlegen (HTTP $code).")
            } finally {
                c.disconnect()
            }
        }
    }

    /** Umbenennen auf dem Server (ersetzt das Ziel) – macht das Hochladen atomar. */
    fun verschiebe(von: String, nach: String) {
        val c = oeffne(von, "MOVE")
        try {
            c.setRequestProperty("Destination", url(nach))
            c.setRequestProperty("Overwrite", "T")
            val code = pruefeCode(c)
            if (code !in 200..299) throw IOException("Umbenennen fehlgeschlagen (HTTP $code).")
        } finally {
            c.disconnect()
        }
    }

    /** Hochladen erst als .tmp, dann umbenennen: andere Geräte sehen nie eine halbe Datei. */
    fun putAtomar(pfad: String, daten: ByteArray, typ: String) {
        val tmp = "$pfad.tmp"
        put(tmp, daten, typ)
        verschiebe(tmp, pfad)
    }

    /** Gibt es den Pfad? */
    fun gibtEs(pfad: String): Boolean {
        val c = oeffne(pfad, "PROPFIND")
        try {
            c.setRequestProperty("Depth", "0")
            val code = pruefeCode(c)
            return code == 207 || code in 200..299
        } finally {
            c.disconnect()
        }
    }
}
