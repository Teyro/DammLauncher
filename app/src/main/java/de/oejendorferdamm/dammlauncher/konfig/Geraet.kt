/*
 * DammLauncher – Copyright (C) 2026 Teyro – GNU GPL v3 oder neuer, siehe LICENSE
 */
package de.oejendorferdamm.dammlauncher.konfig

import android.content.Context
import android.content.SharedPreferences

/**
 * Was nur zu DIESEM Gerät gehört und beim Abgleich nie überschrieben wird: Gerätename, Widget-Ids,
 * (verschlüsselte) Lese-Zugangsdaten, Abgleich-Stand, Fehlversuche, Update-Prüfung.
 */
object Geraet {
    private lateinit var p: SharedPreferences

    fun init(context: Context) {
        if (!::p.isInitialized) p = context.applicationContext.getSharedPreferences("geraet", Context.MODE_PRIVATE)
    }

    var name: String
        get() = p.getString("name", "") ?: ""
        set(v) = p.edit().putString("name", v.take(40)).apply()

    /** Automatischer Vorlagen-Abgleich (nur im Boss-Modus umschaltbar). */
    var abgleichAn: Boolean
        get() = p.getBoolean("abgleichAn", true)
        set(v) = p.edit().putBoolean("abgleichAn", v).apply()

    var letzterAbgleich: Long
        get() = p.getLong("letzterAbgleich", 0)
        set(v) = p.edit().putLong("letzterAbgleich", v).apply()

    /** Zeitstempel der zuletzt übernommenen Vorlage. */
    var vorlageStand: Long
        get() = p.getLong("vorlageStand", 0)
        set(v) = p.edit().putLong("vorlageStand", v).apply()

    var abgleichMeldung: String
        get() = p.getString("abgleichMeldung", "") ?: ""
        set(v) = p.edit().putString("abgleichMeldung", v.take(500)).apply()

    /** Server und Ordner (nicht geheim), Benutzer und Passwort verschlüsselt (Tresor). */
    var server: String
        get() = p.getString("server", "") ?: ""
        set(v) = p.edit().putString("server", v.trim().take(300)).apply()
    var ordner: String
        get() = p.getString("ordner", "") ?: ""
        set(v) = p.edit().putString("ordner", v.trim().trim('/').take(300)).apply()
    var zugangVerschluesselt: String
        get() = p.getString("zugang", "") ?: ""
        set(v) = p.edit().putString("zugang", v).apply()

    /** Kennung eines Widgets (aus der Konfiguration) → Widget-Id auf diesem Gerät. */
    fun widgetId(kennung: String): Int = p.getInt("w_$kennung", 0)
    fun setzeWidgetId(kennung: String, id: Int) = p.edit().apply { if (id == 0) remove("w_$kennung") else putInt("w_$kennung", id) }.apply()
    fun alleWidgetKennungen(): Map<String, Int> = p.all.filterKeys { it.startsWith("w_") }.mapKeys { it.key.removePrefix("w_") }.mapValues { (it.value as? Int) ?: 0 }

    /** Fehlversuche bei PIN/Boss-Passwort und Sperre bis (ms). */
    fun fehlversuche(art: String) = p.getInt("fehl_$art", 0)
    fun sperreBis(art: String) = p.getLong("sperre_$art", 0)
    fun setzeFehlversuche(art: String, n: Int, sperreBis: Long) = p.edit().putInt("fehl_$art", n).putLong("sperre_$art", sperreBis).apply()

    var letzterUpdateCheck: Long
        get() = p.getLong("updateCheck", 0)
        set(v) = p.edit().putLong("updateCheck", v).apply()

    /** Ergebnis des letzten ERFOLGREICHEN Update-Checks: veraltet → beim nächsten Start sofort neu prüfen. */
    var veraltetGemeldet: Boolean
        get() = p.getBoolean("veraltet", false)
        set(v) = p.edit().putBoolean("veraltet", v).apply()

    /** Einmalige Widget-Freigabe erledigt? */
    var widgetFreigabeGefragt: Boolean
        get() = p.getBoolean("widgetFreigabe", false)
        set(v) = p.edit().putBoolean("widgetFreigabe", v).apply()

    // ---- Absturzschutz: Zeitpunkte der letzten Abstürze
    fun merkeAbsturz() {
        val alt = (p.getString("abstuerze", "") ?: "").split(',').mapNotNull { it.toLongOrNull() }
        val neu = (alt + System.currentTimeMillis()).takeLast(5)
        p.edit().putString("abstuerze", neu.joinToString(",")).commit() // commit: der Prozess stirbt gleich
    }

    /** Mehrere Abstürze in kurzer Zeit? Dann sicher starten (Sicherung, ohne Widgets). */
    fun absturzSchleife(): Boolean {
        val jetzt = System.currentTimeMillis()
        val liste = (p.getString("abstuerze", "") ?: "").split(',').mapNotNull { it.toLongOrNull() }
        return liste.count { jetzt - it in 0..120_000 } >= 3
    }

    fun vergissAbstuerze() = p.edit().remove("abstuerze").apply()
}
