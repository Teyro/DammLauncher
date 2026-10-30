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

    /** Updates im Hintergrund laden und gleich installieren (sonst nur anbieten). */
    var autoUpdate: Boolean
        get() = p.getBoolean("autoUpdate", true)
        set(v) = p.edit().putBoolean("autoUpdate", v).apply()

    /** Version, deren APK schon geladen und geprüft im Cache liegt ("" = keine). */
    var updateBereit: String
        get() = p.getString("updateBereit", "") ?: ""
        set(v) = p.edit().putString("updateBereit", v).apply()

    // ---- Tägliches Unsplash-Foto (gehört zu diesem Gerät, jedes Board hat sein eigenes)
    var unsplashTag: String
        get() = p.getString("unsplashTag", "") ?: ""
        set(v) = p.edit().putString("unsplashTag", v).apply()
    var unsplashThema: String
        get() = p.getString("unsplashThema", "") ?: ""
        set(v) = p.edit().putString("unsplashThema", v).apply()
    var unsplashVersuch: Long
        get() = p.getLong("unsplashVersuch", 0)
        set(v) = p.edit().putLong("unsplashVersuch", v).apply()
    /** Fotograf · Lizenz · Quelle – steht klein unten rechts. */
    var fotoNachweis: String
        get() = p.getString("fotoNachweis", null) ?: p.getString("unsplashFotograf", "")?.takeIf { it.isNotEmpty() }?.let { "$it · Unsplash" } ?: ""
        set(v) = p.edit().putString("fotoNachweis", v.take(140)).apply()
    var unsplashSeite: String
        get() = p.getString("unsplashSeite", "") ?: ""
        set(v) = p.edit().putString("unsplashSeite", v.take(300)).apply()
    var unsplashMeldung: String
        get() = p.getString("unsplashMeldung", "") ?: ""
        set(v) = p.edit().putString("unsplashMeldung", v.take(300)).apply()

    /** Bildschirmgröße (quer) – damit auch der Hintergrund-Job passende Fotos holt. */
    var bildBreite: Int
        get() = p.getInt("bildBreite", 1920)
        set(v) = p.edit().putInt("bildBreite", v.coerceIn(640, 3840)).apply()
    var bildHoehe: Int
        get() = p.getInt("bildHoehe", 1080)
        set(v) = p.edit().putInt("bildHoehe", v.coerceIn(360, 2160)).apply()

    /**
     * Wie oft welche App gestartet wurde (nur dieses Gerät) – für „Häufig benutzt“. Ältere Starts
     * zählen weniger: bei jedem Start schrumpfen alle Werte ein wenig.
     */
    fun merkeStart(schluessel: String) {
        val alt = starts()
        val neu = alt.mapValues { it.value * 0.97f }.filterValues { it > 0.2f }.toMutableMap()
        neu[schluessel] = (neu[schluessel] ?: 0f) + 1f
        p.edit().putString("starts", neu.entries.joinToString(";") { "${it.key}=${"%.2f".format(java.util.Locale.US, it.value)}" }).apply()
    }

    fun startsVergessen() = p.edit().remove("starts").apply()

    fun starts(): Map<String, Float> = (p.getString("starts", "") ?: "").split(';').mapNotNull { e ->
        val i = e.lastIndexOf('=')
        if (i <= 0) null else e.substring(0, i) to (e.substring(i + 1).toFloatOrNull() ?: return@mapNotNull null)
    }.toMap()

    // ---- Abwesenheiten: Klasse dieses Boards und die zuletzt gelesene Meldung (nur Zahlen)
    var klasse: String
        get() = p.getString("klasse", "") ?: ""
        set(v) = p.edit().putString("klasse", v.trim().take(20)).apply()
    var abwesendDaten: String
        get() = p.getString("abwesend", "") ?: ""
        set(v) = p.edit().putString("abwesend", v.take(64 * 1024)).apply()
    var abwesendGeladen: Long
        get() = p.getLong("abwesendGeladen", 0)
        set(v) = p.edit().putLong("abwesendGeladen", v).apply()

    /** Lehrer-Ansicht offen (sonst Schüler-Ansicht, wenn diese eingeschaltet ist). */
    var lehrerAnsicht: Boolean
        get() = p.getBoolean("lehrer", false)
        set(v) = p.edit().putBoolean("lehrer", v).apply()

    /** Feierabend-Sperre mit der PIN aufgehoben bis (ms). */
    var feierabendFreiBis: Long
        get() = p.getLong("feierabendFrei", 0)
        set(v) = p.edit().putLong("feierabendFrei", v).apply()

    /** Einmalige Widget-Freigabe erledigt? */
    var widgetFreigabeGefragt: Boolean
        get() = p.getBoolean("widgetFreigabe", false)
        set(v) = p.edit().putBoolean("widgetFreigabe", v).apply()

    // ---- Stundenplan dieses Boards (Fächer je Wochentag, Mo–Fr, eine Zeile je Tag) und aus dem Vorlagen-Ordner
    var stundenplan: String
        get() = p.getString("stundenplan", "") ?: ""
        set(v) = p.edit().putString("stundenplan", v.take(4000)).apply()
    var stundenplanServer: String
        get() = p.getString("stundenplanServer", "") ?: ""
        set(v) = p.edit().putString("stundenplanServer", v.take(256 * 1024)).apply()
    var stundenplanGeladen: Long
        get() = p.getLong("stundenplanGeladen", 0)
        set(v) = p.edit().putLong("stundenplanGeladen", v).apply()

    // ---- Zufallsname: Namen bleiben NUR auf diesem Board (nie in der Vorlage), schon gezogene merken
    fun namen(klasse: String): String = p.getString("namen_" + klasse.lowercase(), "") ?: ""
    fun setzeNamen(klasse: String, text: String) = p.edit().putString("namen_" + klasse.lowercase(), text.take(4000)).apply()
    fun gezogen(kennung: String): Set<String> = (p.getString("gezogen_$kennung", "") ?: "").split('\n').filter { it.isNotEmpty() }.toSet()
    fun setzeGezogen(kennung: String, s: Set<String>) = p.edit().putString("gezogen_$kennung", s.joinToString("\n")).apply()

    // ---- Tafel-Übersicht: Status-Zugang (nur Schreibrecht auf den Status-Ordner, verschlüsselt) und letzter Bericht
    var statusZugangVerschluesselt: String
        get() = p.getString("statusZugang", "") ?: ""
        set(v) = p.edit().putString("statusZugang", v).apply()
    var letzterStatus: Long
        get() = p.getLong("letzterStatus", 0)
        set(v) = p.edit().putLong("letzterStatus", v).apply()
    var statusMeldung: String
        get() = p.getString("statusMeldung", "") ?: ""
        set(v) = p.edit().putString("statusMeldung", v.take(300)).apply()
    /** Zufällige, feste Kennung dieses Boards (für den Dateinamen des Status-Berichts). */
    val kennung: String
        get() = p.getString("kennung", null) ?: java.util.UUID.randomUUID().toString().replace("-", "").take(12).also { p.edit().putString("kennung", it).apply() }

    /** Zuletzt aufgeräumt (ms) – nicht zweimal in derselben Minute. */
    var aufgeraeumt: Long
        get() = p.getLong("aufgeraeumt", 0)
        set(v) = p.edit().putLong("aufgeraeumt", v).apply()

    // ---- Absturzberichte: die letzten drei (gekürzt), gehen mit dem Status-Bericht in den Vorlagen-Ordner
    fun merkeAbsturzText(t: String) {
        val alt = absturzTexte()
        val neu = (alt + t.take(3000)).takeLast(3)
        p.edit().putString("absturzTexte", org.json.JSONArray(neu).toString()).commit()
    }

    fun absturzTexte(): List<String> = try {
        val a = org.json.JSONArray(p.getString("absturzTexte", "[]"))
        (0 until a.length()).map { a.getString(it) }
    } catch (_: Exception) { emptyList() }

    fun absturzTexteVergessen() = p.edit().remove("absturzTexte").apply()

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
