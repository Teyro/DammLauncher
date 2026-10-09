/*
 * DammLauncher – Copyright (C) 2026 Teyro – GNU GPL v3 oder neuer, siehe LICENSE
 */
package de.oejendorferdamm.dammlauncher.sicherheit

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Verschlüsselt Zugangsdaten mit einem AES-Schlüssel, der den Android Keystore nie verlässt.
 * Klappt etwas nicht (manche alten Geräte), wird NICHTS gespeichert – nie Klartext.
 */
object Tresor {
    private const val ALIAS = "dammlauncher_zugang"

    private fun schluessel(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    fun verschluessle(klartext: String): String? = try {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, schluessel())
        val iv = c.iv
        val daten = c.doFinal(klartext.toByteArray(Charsets.UTF_8))
        Base64.encodeToString(iv + daten, Base64.NO_WRAP)
    } catch (_: Exception) {
        null
    }

    fun entschluessle(text: String): String? = try {
        if (text.isEmpty()) null else {
            val roh = Base64.decode(text, Base64.NO_WRAP)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, schluessel(), GCMParameterSpec(128, roh, 0, 12))
            String(c.doFinal(roh, 12, roh.size - 12), Charsets.UTF_8)
        }
    } catch (_: Exception) {
        null
    }
}
