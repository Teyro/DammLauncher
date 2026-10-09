/*
 * DammLauncher – Copyright (C) 2026 Teyro – GNU GPL v3 oder neuer, siehe LICENSE
 */
package de.oejendorferdamm.dammlauncher.konfig

import de.oejendorferdamm.dammlauncher.raster.Belegung
import org.json.JSONArray
import org.json.JSONObject

class KonfigFehler(meldung: String) : Exception(meldung)

/**
 * Konfiguration als JSON. Einlesen ist fehlertolerant: Werte außerhalb der Grenzen werden auf den
 * nächsten gültigen Wert gesetzt, unbrauchbare Einträge weggelassen – jeweils mit Warnung. Für
 * Vorlagen aus dem Netz gibt es [streng]: dann führt jede Warnung zur Ablehnung.
 */
object KonfigJson {
    const val FORMAT = 1

    data class Ergebnis(val konfig: Konfig, val warnungen: List<String>)

    fun schreibe(k: Konfig): String = JSONObject().apply {
        put("format", FORMAT)
        put("app", "DammLauncher")
        put("stand", k.stand)
        put("spalten", k.spalten)
        put("zeilen", k.zeilen)
        put("rand", JSONObject().put("oben", k.rand.oben).put("unten", k.rand.unten).put("links", k.rand.links).put("rechts", k.rand.rechts))
        put("abstand", k.abstand)
        put("iconProzent", k.iconProzent)
        put("textGroesse", k.textGroesse)
        put("beschriftung", k.beschriftung)
        put("animationen", k.animationen)
        put("alleAppsKnopf", k.alleAppsKnopf)
        put("elemente", JSONArray().apply { k.elemente.forEach { put(element(it)) } })
        put("nichtPlatziert", JSONArray().apply { k.nichtPlatziert.forEach { put(element(it)) } })
        put("hintergrund", JSONObject().put("farbe", "#%08X".format(k.hintergrund.farbe)).put("bild", k.hintergrund.bild))
        put("bearbeitenGesperrt", k.bearbeitenGesperrt)
        put("pin", k.pin?.let { hash(it) } ?: JSONObject.NULL)
        put("boss", k.boss?.let { hash(it) } ?: JSONObject.NULL)
    }.toString(2)

    private fun hash(h: PasswortHash) = JSONObject().put("hash", h.hash).put("salt", h.salt).put("runden", h.runden)

    private fun element(e: Element): JSONObject = when (e) {
        is Element.App -> JSONObject().put("typ", "app").put("paket", e.paket).put("aktivitaet", e.aktivitaet).put("spalte", e.spalte).put("zeile", e.zeile)
            .apply { e.titel?.let { put("titel", it) }; e.icon?.let { put("icon", it) } }
        is Element.Widget -> JSONObject().put("typ", "widget").put("provider", e.provider).put("kennung", e.kennung)
            .put("spalte", e.spalte).put("zeile", e.zeile).put("breite", e.breite).put("hoehe", e.hoehe)
    }

    private val PAKET = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")
    private val KLASSE = Regex("^[A-Za-z0-9_.$]+$")
    private val KENNUNG = Regex("^[A-Za-z0-9_-]{1,40}$")
    /** Eigene Symbole: nur harmlose Dateinamen (Prüfsumme + .png). */
    val ICON_DATEI = Regex("^[a-f0-9]{8,64}\\.png$")

    /** Alle eigenen Symbol-Dateien, die eine Konfiguration braucht (für Export und Vorlage). */
    fun icons(k: Konfig): Set<String> = (k.elemente + k.nichtPlatziert).mapNotNull { (it as? Element.App)?.icon }.toSet()

    /** Liest; wirft [KonfigFehler] nur, wenn es gar keine DammLauncher-Konfiguration ist. */
    fun lese(text: String): Ergebnis {
        val o = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw KonfigFehler("Keine gültige JSON-Datei.")
        }
        val format = o.optInt("format", -1)
        if (format < 1) throw KonfigFehler("Keine DammLauncher-Konfiguration (Formatangabe fehlt).")
        if (format > FORMAT) throw KonfigFehler("Konfiguration ist von einer neueren DammLauncher-Version (Format $format) – bitte erst aktualisieren.")
        val w = ArrayList<String>()
        val std = Konfig()

        fun zahl(quelle: JSONObject, name: String, grenze: IntRange, vorgabe: Int): Int {
            if (!quelle.has(name)) {
                w.add("$name fehlt – Standard $vorgabe"); return vorgabe
            }
            val v = quelle.opt(name)
            val n = (v as? Number)?.toInt() ?: run { w.add("$name ist keine Zahl – Standard $vorgabe"); return vorgabe }
            if (n !in grenze) w.add("$name = $n liegt außerhalb ${grenze.first}–${grenze.last}")
            return n.coerceIn(grenze)
        }
        fun schalter(name: String, vorgabe: Boolean): Boolean {
            val v = o.opt(name)
            if (v == null) { w.add("$name fehlt"); return vorgabe }
            return v as? Boolean ?: run { w.add("$name ist kein Ja/Nein"); vorgabe }
        }

        val spalten = zahl(o, "spalten", Grenzen.RASTER, std.spalten)
        val zeilen = zahl(o, "zeilen", Grenzen.RASTER, std.zeilen)
        val r = o.optJSONObject("rand")
        val rand = if (r == null) {
            w.add("rand fehlt"); std.rand
        } else Raender(zahl(r, "oben", Grenzen.RAND, 40), zahl(r, "unten", Grenzen.RAND, 40), zahl(r, "links", Grenzen.RAND, 40), zahl(r, "rechts", Grenzen.RAND, 40))

        fun elemente(name: String): List<Element> {
            val a = o.optJSONArray(name) ?: run { if (o.has(name)) w.add("$name ist keine Liste"); return emptyList() }
            return (0 until a.length()).mapNotNull { i ->
                val e = a.optJSONObject(i) ?: run { w.add("$name[$i] ist kein Eintrag"); return@mapNotNull null }
                val s = e.optInt("spalte", -1)
                val z = e.optInt("zeile", -1)
                when (e.optString("typ")) {
                    "app" -> {
                        val p = e.optString("paket")
                        val akt = e.optString("aktivitaet")
                        val titel = if (e.has("titel")) e.optString("titel").trim().take(60).ifEmpty { null } else null
                        val icon = if (e.has("icon")) e.optString("icon").takeIf { ICON_DATEI.matches(it) } ?: run { w.add("$name[$i]: ungültiges Symbol"); null } else null
                        if (!PAKET.matches(p) || !KLASSE.matches(akt)) { w.add("$name[$i]: ungültige App"); null } else Element.App(p, akt, s, z, titel, icon)
                    }
                    "widget" -> {
                        val prov = e.optString("provider")
                        val ken = e.optString("kennung")
                        val teile = prov.split('/')
                        if (teile.size != 2 || !PAKET.matches(teile[0]) || !KLASSE.matches(teile[1]) || !KENNUNG.matches(ken)) {
                            w.add("$name[$i]: ungültiges Widget"); null
                        } else Element.Widget(prov, ken, s, z, e.optInt("breite", 1).coerceIn(1, 10), e.optInt("hoehe", 1).coerceIn(1, 10))
                    }
                    else -> { w.add("$name[$i]: unbekannter Typ"); null }
                }
            }
        }

        // Beim Lesen nur reparieren (Überlappung, außerhalb), nichts eigenmächtig neu einsortieren
        val roh = elemente("elemente")
        val (platziert, herausgefallen) = Belegung.anpassen(roh, emptyList(), spalten, zeilen)
        if (platziert.size + herausgefallen.size < roh.size) w.add("doppelte Einträge entfernt")
        if (platziert.zip(roh).any { (x, y) -> x != y } || herausgefallen.isNotEmpty()) w.add("Elemente mussten verschoben werden")
        val belegt = platziert.map { it.schluessel }.toMutableSet()
        val offen = (herausgefallen + elemente("nichtPlatziert")).filter { belegt.add(it.schluessel) }

        val h = o.optJSONObject("hintergrund")
        val farbe = h?.optString("farbe")?.let { t ->
            runCatching { java.lang.Long.parseLong(t.removePrefix("#"), 16).toInt() }.getOrNull()?.let { if (t.length == 7) it or 0xFF000000.toInt() else it }
        } ?: run { w.add("hintergrund.farbe fehlt oder ungültig"); std.hintergrund.farbe }

        fun passwort(name: String) = o.optJSONObject(name)?.let { p ->
            val hash = p.optString("hash")
            val salt = p.optString("salt")
            val runden = p.optInt("runden", 0)
            if (hash.isEmpty() || salt.isEmpty() || runden !in 1000..1_000_000) { w.add("$name ungültig – entfernt"); null } else PasswortHash(hash, salt, runden)
        }
        val pin = passwort("pin")
        val boss = passwort("boss")

        val k = Konfig(
            spalten = spalten,
            zeilen = zeilen,
            rand = rand,
            abstand = zahl(o, "abstand", Grenzen.ABSTAND, std.abstand),
            iconProzent = zahl(o, "iconProzent", Grenzen.ICON, std.iconProzent),
            textGroesse = zahl(o, "textGroesse", Grenzen.TEXT, std.textGroesse),
            beschriftung = schalter("beschriftung", std.beschriftung),
            animationen = schalter("animationen", std.animationen),
            alleAppsKnopf = schalter("alleAppsKnopf", std.alleAppsKnopf),
            elemente = platziert,
            nichtPlatziert = offen,
            hintergrund = Hintergrund(farbe, h?.optBoolean("bild", false) ?: false),
            bearbeitenGesperrt = schalter("bearbeitenGesperrt", false),
            pin = pin,
            boss = boss,
            stand = o.optLong("stand", 0L)
        )
        return Ergebnis(k, w)
    }

    /** Für Vorlagen aus dem Netz: nur ganz saubere Dateien werden übernommen. */
    fun streng(text: String): Konfig {
        val e = lese(text)
        if (e.warnungen.isNotEmpty()) throw KonfigFehler("Vorlage fehlerhaft: " + e.warnungen.take(3).joinToString("; "))
        if (e.konfig.stand <= 0L) throw KonfigFehler("Vorlage ohne Zeitstempel")
        return e.konfig
    }

    /**
     * Was auf diesem Gerät gezeigt werden kann: Elemente, deren App bzw. Widget-Provider fehlt,
     * bleiben in der Konfiguration (vielleicht wird die App noch installiert), werden aber nicht
     * gezeigt – die Zelle bleibt leer. Gibt die sichtbaren Elemente und ein Protokoll zurück.
     */
    fun aufloesen(k: Konfig, appDa: (String, String) -> Boolean, providerDa: (String) -> Boolean): Pair<List<Element>, List<String>> {
        val protokoll = ArrayList<String>()
        val sichtbar = k.elemente.filter { e ->
            val da = when (e) {
                is Element.App -> appDa(e.paket, e.aktivitaet)
                is Element.Widget -> providerDa(e.provider)
            }
            if (!da) protokoll.add(
                when (e) {
                    is Element.App -> "App fehlt: ${e.paket}"
                    is Element.Widget -> "Widget fehlt: ${e.provider}"
                }
            )
            da
        }
        return sichtbar to protokoll
    }
}
