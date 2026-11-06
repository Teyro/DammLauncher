/*
 * DammLauncher – Copyright (C) 2026 Teyro – GNU GPL v3 oder neuer, siehe LICENSE
 */
package de.oejendorferdamm.dammlauncher.pflege

import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import de.oejendorferdamm.dammlauncher.konfig.Geraet

/**
 * Quelle beim Einschalten: Nach dem Hochfahren wartet DammLauncher, bis der gewählte Eingang (z. B. der
 * PC im Board) wirklich Signal hat, und schaltet erst dann um – das Clevertouch selbst wechselt zu früh,
 * wenn der PC noch nicht läuft. Zusätzlich gilt eine Mindest-Wartezeit nach dem Start (manche Boards
 * melden einen Eingang auch ohne Bild als verbunden). Nur einmal je Einschalten, höchstens 10 Minuten lang.
 * Umgeschaltet wird über die TV-Eingänge von Android (TvInputManager) – ob ein Board sie anbietet,
 * zeigt die Liste in den Einstellungen.
 */
object StartQuelle {
    data class Eingang(val id: String, val name: String, val art: String, val signal: Boolean?)

    private const val SPAETESTENS_MS = 10 * 60_000L

    /** Läuft schon ein Warten? (Launcher kann während der Wartezeit neu aufgebaut werden) */
    private var laeuft = false

    fun eingaenge(c: Context): List<Eingang> {
        val tv = c.getSystemService(TvInputManager::class.java) ?: return emptyList()
        return try {
            tv.tvInputList.map { i ->
                val name = try { i.loadLabel(c)?.toString() } catch (_: Exception) { null } ?: i.id
                val signal = try {
                    when (tv.getInputState(i.id)) {
                        TvInputManager.INPUT_STATE_CONNECTED -> true
                        TvInputManager.INPUT_STATE_DISCONNECTED -> false
                        else -> null // CONNECTED_STANDBY: angeschlossen, aber (noch) kein Bild
                    }
                } catch (_: Exception) { null }
                Eingang(i.id, name, art(i.type), signal)
            }.sortedBy { it.name.lowercase() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun art(t: Int) = when (t) {
        TvInputInfo.TYPE_HDMI -> "HDMI"
        TvInputInfo.TYPE_DISPLAY_PORT -> "DisplayPort"
        TvInputInfo.TYPE_VGA -> "VGA"
        TvInputInfo.TYPE_COMPONENT -> "Component"
        TvInputInfo.TYPE_COMPOSITE -> "AV"
        TvInputInfo.TYPE_TUNER -> "TV"
        else -> "Eingang"
    }

    /** Jetzt auf [id] umschalten; false = das Board bietet dafür keine App an. */
    fun wechseln(c: Context, id: String): Boolean = try {
        c.startActivity(Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(id)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: Exception) {
        false
    }

    /** Darf nach diesem Einschalten noch umgeschaltet werden? (Einschaltzeitpunkt = jetzt − Laufzeit seit dem Start) */
    fun nochOffen(): Boolean {
        val seitStart = SystemClock.elapsedRealtime()
        val start = System.currentTimeMillis() - seitStart
        // ±1 Minute: die Uhr kann sich nach dem Start noch stellen (Netzzeit)
        return seitStart < SPAETESTENS_MS && kotlin.math.abs(Geraet.startQuelleErledigt - start) > 60_000L
    }

    private fun erledigt() {
        Geraet.startQuelleErledigt = System.currentTimeMillis() - SystemClock.elapsedRealtime()
    }

    /**
     * Nach dem Einschalten aufrufen (Launcher startet als Erstes). Schaltet auf [id] um, sobald der Eingang
     * Signal hat und mindestens [mindestensSek] Sekunden seit dem Start vergangen sind. [gewechselt] meldet es.
     */
    fun beimStart(c: Context, id: String, mindestensSek: Int, aufSignalWarten: Boolean, gewechselt: () -> Unit) {
        if (id.isEmpty() || laeuft || !nochOffen()) return
        val tv = c.getSystemService(TvInputManager::class.java) ?: return
        laeuft = true
        val haupt = Handler(Looper.getMainLooper())
        var fertig = false
        var rueckruf: TvInputManager.TvInputCallback? = null
        val pruefen = object : Runnable {
            override fun run() {
                if (fertig) return
                val seitStart = SystemClock.elapsedRealtime()
                if (seitStart > SPAETESTENS_MS) { fertig = true; laeuft = false; rueckruf?.let { tv.unregisterCallback(it) }; return }
                val noch = mindestensSek * 1000L - seitStart
                if (noch > 0) { haupt.postDelayed(this, noch); return }
                val zustand = if (!aufSignalWarten) TvInputManager.INPUT_STATE_CONNECTED
                    else try { tv.getInputState(id) } catch (_: Exception) { TvInputManager.INPUT_STATE_CONNECTED }
                if (zustand != TvInputManager.INPUT_STATE_CONNECTED) {
                    // auf das Signal warten (Rückruf) – zur Sicherheit alle 15 Sekunden selbst nachsehen
                    haupt.postDelayed(this, 15_000L)
                    return
                }
                fertig = true; laeuft = false
                rueckruf?.let { tv.unregisterCallback(it) }
                erledigt()
                if (wechseln(c, id)) gewechselt()
            }
        }
        rueckruf = object : TvInputManager.TvInputCallback() {
            override fun onInputStateChanged(inputId: String, state: Int) {
                if (inputId == id && state == TvInputManager.INPUT_STATE_CONNECTED) { haupt.removeCallbacks(pruefen); pruefen.run() }
            }
        }
        try { tv.registerCallback(rueckruf!!, haupt) } catch (_: Exception) {}
        pruefen.run()
    }
}
