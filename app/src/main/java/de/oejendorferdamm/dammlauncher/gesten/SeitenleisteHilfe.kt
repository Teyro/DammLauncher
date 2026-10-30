/*
 * DammLauncher – Copyright (C) 2026 Teyro – GNU GPL v3 oder neuer, siehe LICENSE
 */
package de.oejendorferdamm.dammlauncher.gesten

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/**
 * Bedienungshilfe nur für „Zurück“ und „Übersicht“ der Seitenleiste – Android erlaubt diese Tasten
 * sonst keiner App. Liest keine Fensterinhalte (siehe res/xml/seitenleiste_hilfe.xml) und muss in
 * den Android-Einstellungen unter „Bedienungshilfen“ einmal eingeschaltet werden.
 */
class SeitenleisteHilfe : AccessibilityService() {
    override fun onServiceConnected() {
        aktiv = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        if (aktiv === this) aktiv = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (aktiv === this) aktiv = null
        super.onDestroy()
    }

    companion object {
        @Volatile
        private var aktiv: SeitenleisteHilfe? = null

        val an get() = aktiv != null

        fun zurueck() = aktiv?.performGlobalAction(GLOBAL_ACTION_BACK) ?: false
        fun uebersicht() = aktiv?.performGlobalAction(GLOBAL_ACTION_RECENTS) ?: false

        fun einstellungenOeffnen(c: Context) = try {
            c.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
        } catch (_: Exception) { false }
    }
}
