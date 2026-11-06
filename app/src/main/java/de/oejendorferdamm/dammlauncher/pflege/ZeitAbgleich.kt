/*
 * DammLauncher – Copyright (C) 2026 Teyro – GNU GPL v3 oder neuer, siehe LICENSE
 */
package de.oejendorferdamm.dammlauncher.pflege

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Uhrzeit vom Zeitserver (Standard: PTB, die amtliche Zeit in Deutschland). Ist die Board-Uhr falsch,
 * zeigen die Uhren des Launchers trotzdem die richtige Zeit ([versatzMs]). Hat DammLauncher die Erlaubnis
 * WRITE_SECURE_SETTINGS (einmal: adb shell pm grant de.oejendorferdamm.dammlauncher
 * android.permission.WRITE_SECURE_SETTINGS – oder über die Geräteverwaltung), stellt er zusätzlich den
 * Zeitserver des Systems ein und schaltet „Uhrzeit automatisch“ an, dann stimmt die Zeit überall.
 */
object ZeitAbgleich {
    const val STANDARD = "ptbtime1.ptb.de"
    val SERVER = Regex("^[A-Za-z0-9.\\-]{1,253}$")

    /** Board-Uhr minus richtige Zeit wird abgezogen: richtige Zeit = currentTimeMillis() + versatzMs. */
    @Volatile var versatzMs = 0L
        private set
    @Volatile var zuletzt = 0L
        private set
    @Volatile var fehler: String? = null
        private set
    @Volatile var systemGesetzt = false
        private set

    fun jetzt() = System.currentTimeMillis() + versatzMs

    fun darfSystem(c: Context) = c.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED

    /** Im Hintergrund aufrufen (Netz). */
    fun abgleichen(c: Context, server: String) {
        if (server.isEmpty() || !SERVER.matches(server)) return
        try {
            val v = versatzVon(server)
            // Unter einer Sekunde: nichts korrigieren (Netzlaufzeit), sonst springt die Uhr ohne Grund
            versatzMs = if (kotlin.math.abs(v) < 1000L) 0L else v
            fehler = null
        } catch (e: Exception) {
            fehler = "Zeitserver $server antwortet nicht"
        }
        zuletzt = System.currentTimeMillis()
        if (darfSystem(c)) systemGesetzt = try {
            val cr = c.contentResolver
            if (Settings.Global.getString(cr, "ntp_server") != server) Settings.Global.putString(cr, "ntp_server", server)
            if (Settings.Global.getInt(cr, Settings.Global.AUTO_TIME, 0) != 1) Settings.Global.putInt(cr, Settings.Global.AUTO_TIME, 1)
            true
        } catch (_: Exception) { false }
    }

    /** SNTP (RFC 4330): Versatz = ((T2 − T1) + (T3 − T4)) / 2 in Millisekunden. */
    fun versatzVon(server: String, zeitlimitMs: Int = 5000): Long {
        DatagramSocket().use { s ->
            s.soTimeout = zeitlimitMs
            val paket = ByteArray(48)
            paket[0] = 0x1B // LI 0, Version 3, Modus 3 (Client)
            val adresse = InetAddress.getByName(server)
            val t1 = System.currentTimeMillis()
            schreibeZeit(paket, 40, t1)
            s.send(DatagramPacket(paket, paket.size, adresse, 123))
            val antwort = DatagramPacket(ByteArray(48), 48)
            s.receive(antwort)
            val t4 = System.currentTimeMillis()
            val b = antwort.data
            if (antwort.length < 48 || (b[0].toInt() and 0x07) !in 4..5 || b[1].toInt() == 0) throw IllegalStateException("ungültige Antwort")
            return versatz(t1, liesZeit(b, 32), liesZeit(b, 40), t4)
        }
    }

    fun versatz(t1: Long, t2: Long, t3: Long, t4: Long) = ((t2 - t1) + (t3 - t4)) / 2

    private const val SEIT_1900 = 2_208_988_800L

    fun liesZeit(b: ByteArray, o: Int): Long {
        var sek = 0L
        var bruch = 0L
        for (i in 0..3) sek = (sek shl 8) or (b[o + i].toLong() and 0xFF)
        for (i in 4..7) bruch = (bruch shl 8) or (b[o + i].toLong() and 0xFF)
        return (sek - SEIT_1900) * 1000L + bruch * 1000L / 0x1_0000_0000L
    }

    fun schreibeZeit(b: ByteArray, o: Int, ms: Long) {
        val sek = ms / 1000L + SEIT_1900
        val bruch = (ms % 1000L) * 0x1_0000_0000L / 1000L
        for (i in 0..3) b[o + i] = (sek shr (24 - 8 * i)).toByte()
        for (i in 0..3) b[o + 4 + i] = (bruch shr (24 - 8 * i)).toByte()
    }
}
