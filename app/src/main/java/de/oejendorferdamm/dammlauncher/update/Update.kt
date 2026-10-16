/*
 * DammLauncher – Copyright (C) 2026 Teyro – GNU GPL v3 oder neuer, siehe LICENSE
 */
package de.oejendorferdamm.dammlauncher.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import de.oejendorferdamm.dammlauncher.BuildConfig
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Updates aus den GitHub-Releases – fest verdrahtet auf dieses Repository.
 *
 * Update-Pflicht: Gibt es eine neuere Version, die schon länger als 7 Tage veröffentlicht ist,
 * wird der Launcher gesperrt, bis aktualisiert ist. „Jetzt“ ist dabei das Datum aus der Antwort
 * von GitHub (die Uhr eines Geräts kann falsch gehen). Gesperrt wird NUR nach einem erfolgreichen
 * Check – ist GitHub nicht erreichbar, gilt die Version als aktuell.
 */
object Update {
    /** Release: fest die GitHub-API dieses Repos. (Nur die Testversion fragt einen lokalen Test-Server.) */
    private val API = BuildConfig.UPDATE_API
    private const val DOWNLOAD = "https://github.com/Teyro/DammLauncher/releases/download/"
    private const val MAX_APK = 50L * 1024 * 1024
    const val SPERRE_NACH_TAGEN = 7

    data class Info(val version: String, val url: String, val groesse: Long, val notizen: String, val veroeffentlicht: Long)
    data class Ergebnis(val info: Info, val neuer: Boolean, val gesperrt: Boolean)

    /** null = Prüfung nicht möglich (Netz, GitHub) – dann ist nichts gesperrt. */
    fun pruefen(): Ergebnis? = try {
        val c = URL(API).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 20_000
        c.instanceFollowRedirects = false
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.setRequestProperty("User-Agent", "DammLauncher")
        try {
            if (c.responseCode != 200) null else {
                val jetzt = c.getHeaderFieldDate("Date", 0L).takeIf { it > 0 } ?: System.currentTimeMillis()
                val text = c.inputStream.use { it.readBytes() }.toString(Charsets.UTF_8)
                auswerten(text, jetzt, BuildConfig.VERSION_NAME)
            }
        } finally {
            c.disconnect()
        }
    } catch (_: Exception) {
        null
    }

    /** Antwort der Releases-API auswerten (ohne Netz, testbar). */
    fun auswerten(json: String, jetzt: Long, installiert: String): Ergebnis? {
        val o = JSONObject(json)
        val version = o.optString("tag_name").removePrefix("v")
        if (!Regex("\\d+\\.\\d+\\.\\d+").matches(version)) return null
        val veroeffentlicht = iso(o.optString("published_at")) ?: return null
        var url: String? = null
        var groesse = 0L
        val assets = o.optJSONArray("assets")
        for (i in 0 until (assets?.length() ?: 0)) {
            val a = assets!!.optJSONObject(i) ?: continue
            if (a.optString("name").endsWith(".apk")) {
                url = a.optString("browser_download_url").takeIf { it.startsWith(DOWNLOAD) }
                groesse = a.optLong("size")
                break
            }
        }
        if (url == null) return null
        val neuer = istNeuer(version, installiert)
        val alt = jetzt - veroeffentlicht > SPERRE_NACH_TAGEN * 24L * 60 * 60_000
        return Ergebnis(Info(version, url, groesse, o.optString("body"), veroeffentlicht), neuer, neuer && alt)
    }

    fun istNeuer(neu: String, alt: String): Boolean {
        val a = neu.split('.').map { it.toIntOrNull() ?: 0 }
        val b = alt.split('.').map { it.substringBefore('-').toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun iso(t: String): Long? = try {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(t)?.time
    } catch (_: Exception) {
        null
    }

    /** APK laden: nur von den Release-Adressen dieses Repos (GitHub leitet auf seinen Speicher weiter). */
    fun laden(context: Context, info: Info, fortschritt: (Float) -> Unit): File {
        if (!info.url.startsWith(DOWNLOAD)) throw IOException("Unerwartete Download-Adresse")
        val ziel = apkDatei(context)
        var url = URL(info.url)
        var c: HttpURLConnection
        var sprung = 0
        while (true) {
            c = url.openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 15_000
            c.readTimeout = 30_000
            c.setRequestProperty("User-Agent", "DammLauncher")
            val code = c.responseCode
            if (code in 300..399) {
                val weiter = URL(url, c.getHeaderField("Location") ?: throw IOException("Weiterleitung ohne Ziel"))
                c.disconnect()
                // nur https und nur GitHub
                if (weiter.protocol != "https" || !(weiter.host == "github.com" || weiter.host.endsWith(".githubusercontent.com"))) throw IOException("Unerwartete Weiterleitung")
                if (++sprung > 5) throw IOException("Zu viele Weiterleitungen")
                url = weiter
                continue
            }
            if (code != 200) throw IOException("Herunterladen fehlgeschlagen (HTTP $code)")
            break
        }
        try {
            var gelesen = 0L
            val gesamt = if (info.groesse > 0) info.groesse else c.contentLengthLong
            c.inputStream.use { ein ->
                ziel.outputStream().use { aus ->
                    val puffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = ein.read(puffer)
                        if (n < 0) break
                        aus.write(puffer, 0, n)
                        gelesen += n
                        if (gelesen > MAX_APK) throw IOException("Datei ist unerwartet groß")
                        if (gesamt > 0) fortschritt((gelesen.toFloat() / gesamt).coerceIn(0f, 1f))
                    }
                }
            }
            if (info.groesse > 0 && gelesen != info.groesse) throw IOException("Download unvollständig")
        } catch (e: Exception) {
            ziel.delete()
            throw e
        } finally {
            c.disconnect()
        }
        return ziel
    }

    /** Stimmt die Signatur der geladenen APK mit der installierten überein (gleiches Paket, neuere Version)? */
    @Suppress("DEPRECATION")
    fun signaturPasst(context: Context, apk: File): Boolean = try {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val neu = pm.getPackageArchiveInfo(apk.absolutePath, flags)
        val alt = pm.getPackageInfo(context.packageName, flags)
        neu != null && neu.packageName == context.packageName && versionCode(neu) > versionCode(alt) && fingerabdruecke(neu) == fingerabdruecke(alt) && fingerabdruecke(alt).isNotEmpty()
    } catch (_: Exception) {
        false
    }

    @Suppress("DEPRECATION")
    private fun versionCode(p: PackageInfo): Long = if (Build.VERSION.SDK_INT >= 28) p.longVersionCode else p.versionCode.toLong()

    @Suppress("DEPRECATION")
    private fun fingerabdruecke(p: PackageInfo): Set<String> {
        val sig = if (Build.VERSION.SDK_INT >= 28) p.signingInfo?.let { if (it.hasMultipleSigners()) it.apkContentsSigners else it.signingCertificateHistory } else p.signatures
        return sig.orEmpty().map { s -> MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) } }.toSet()
    }

    fun darfInstallieren(context: Context) = context.packageManager.canRequestPackageInstalls()

    fun apkDatei(context: Context) = File(context.cacheDir, "update.apk")

    /** Laden und prüfen; null = fertig zum Installieren, sonst die Fehlermeldung. */
    fun vorbereiten(context: Context, info: Info, fortschritt: (Float) -> Unit = {}): String? = try {
        val apk = laden(context, info, fortschritt)
        if (!signaturPasst(context, apk)) {
            apk.delete()
            "Die geladene Datei ist nicht korrekt signiert – Update abgebrochen."
        } else null
    } catch (x: Exception) {
        "Herunterladen fehlgeschlagen: ${x.message}"
    }

    /**
     * Über den PackageInstaller installieren. Ab Android 12 geht das ohne Rückfrage, sobald
     * DammLauncher sich einmal selbst aktualisiert hat; vorher zeigt Android einmal „Installieren?“.
     */
    fun installieren(context: Context) {
        val apk = apkDatei(context)
        if (!signaturPasst(context, apk)) throw IOException("Update-Datei fehlt oder ist nicht korrekt signiert")
        val installer = context.packageManager.packageInstaller
        val p = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(p)
        try {
            installer.openSession(id).use { sitzung ->
                sitzung.openWrite("DammLauncher.apk", 0, apk.length()).use { aus ->
                    apk.inputStream().use { it.copyTo(aus) }
                    sitzung.fsync(aus)
                }
                // Das System füllt den Status ein – daher veränderbar
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                val antwort = PendingIntent.getBroadcast(context, id, Intent(context, InstallErgebnis::class.java), flags)
                sitzung.commit(antwort.intentSender)
            }
        } catch (e: Exception) {
            try { installer.abandonSession(id) } catch (_: Exception) {}
            throw e
        }
    }
}

/** Antwort des PackageInstallers – nicht exportiert, kommt nur über das eigene PendingIntent. */
class InstallErgebnis : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val bestaetigen = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                try { context.startActivity(bestaetigen.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
            }
            PackageInstaller.STATUS_SUCCESS -> Unit // der Prozess wird gleich neu gestartet
            else -> Log.w("DammLauncher", "Update nicht installiert: " + intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
        }
    }
}
