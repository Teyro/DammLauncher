/*
 * DammLauncher – Copyright (C) 2026 Teyro – GNU GPL v3 oder neuer, siehe LICENSE
 */
package de.oejendorferdamm.dammlauncher.sicherheit

import android.util.Base64
import de.oejendorferdamm.dammlauncher.konfig.Geraet
import de.oejendorferdamm.dammlauncher.konfig.PasswortHash
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * PIN und Boss-Passwort: gespeichert wird nur ein PBKDF2-Hash (SHA-256) mit zufälligem Salt.
 * Nach 5 Fehlversuchen wird gesperrt – 1 Minute, danach jeweils doppelt so lange (höchstens 1 Stunde).
 */
object Passwort {
    /** Auf alten Boards (Android 8) dauert das gut eine halbe Sekunde – genug gegen Durchprobieren. */
    const val RUNDEN = 30_000

    fun erzeuge(text: String): PasswortHash {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return PasswortHash(b64(rechne(text, salt, RUNDEN)), b64(salt), RUNDEN)
    }

    fun stimmt(text: String, h: PasswortHash): Boolean = try {
        val salt = Base64.decode(h.salt, Base64.NO_WRAP)
        MessageDigest.isEqual(rechne(text, salt, h.runden), Base64.decode(h.hash, Base64.NO_WRAP))
    } catch (_: Exception) {
        false
    }

    private fun rechne(text: String, salt: ByteArray, runden: Int): ByteArray {
        val spec = PBEKeySpec(text.toCharArray(), salt, runden, 256)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)

    /** Noch gesperrt? Gibt die restlichen Sekunden zurück (0 = frei). */
    fun gesperrtNoch(art: String): Long = ((Geraet.sperreBis(art) - System.currentTimeMillis()) / 1000).coerceAtLeast(0)

    /** Prüfen mit Fehlversuch-Zählung. null = gesperrt. */
    fun pruefe(art: String, text: String, h: PasswortHash): Boolean? {
        if (gesperrtNoch(art) > 0) return null
        if (stimmt(text, h)) {
            Geraet.setzeFehlversuche(art, 0, 0)
            return true
        }
        val n = Geraet.fehlversuche(art) + 1
        val sperre = if (n >= 5) System.currentTimeMillis() + minOf(60_000L shl (n - 5).coerceAtMost(6), 3_600_000L) else 0L
        Geraet.setzeFehlversuche(art, n, sperre)
        return false
    }
}
