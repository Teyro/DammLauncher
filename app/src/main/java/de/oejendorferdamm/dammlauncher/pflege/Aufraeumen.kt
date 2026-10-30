/*
 * DammLauncher – Copyright (C) 2026 Teyro – GNU GPL v3 oder neuer, siehe LICENSE
 */
package de.oejendorferdamm.dammlauncher.pflege

import android.app.ActivityManager
import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import de.oejendorferdamm.dammlauncher.apps.AppKatalog
import de.oejendorferdamm.dammlauncher.konfig.Geraet
import de.oejendorferdamm.dammlauncher.konfig.KonfigSpeicher

/**
 * „Nach der Stunde aufräumen“: Apps im Hintergrund beenden (die vorne laufende bleibt – das lässt
 * Android nicht zu) und die Medien-Lautstärke zurücksetzen. Browser-Verläufe kann eine fremde App
 * nicht löschen; dafür eignet sich der private Modus des Browsers.
 */
object Aufraeumen {
    /** Gibt die Zahl der beendeten Apps zurück. */
    fun jetzt(context: Context): Int {
        Geraet.init(context)
        Geraet.aufgeraeumt = System.currentTimeMillis()
        val am = context.getSystemService(ActivityManager::class.java)
        val tastatur = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.substringBefore('/')
        val schonen = setOfNotNull(context.packageName, tastatur, "com.android.systemui")
        var n = 0
        AppKatalog.apps.map { it.paket }.distinct().filter { it !in schonen }.forEach { p ->
            try { am?.killBackgroundProcesses(p); n++ } catch (_: Exception) {}
        }
        val laut = KonfigSpeicher.konfig.aufraeumen.lautstaerke
        if (laut >= 0) try {
            val audio = context.getSystemService(AudioManager::class.java)
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(max * laut / 100f), 0)
        } catch (_: Exception) {
        }
        return n
    }

    /** Nicht zweimal kurz hintereinander automatisch (der Takt läuft alle 30 Sekunden). */
    fun eben() = System.currentTimeMillis() - Geraet.aufgeraeumt < 90_000
}
