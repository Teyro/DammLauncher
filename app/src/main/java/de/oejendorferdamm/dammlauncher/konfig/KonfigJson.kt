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
        put("schrift", k.schrift)
        put("ausgeblendet", JSONArray().apply { k.ausgeblendet.sorted().forEach { put(it) } })
        put("gesten", JSONObject().put("hoch", k.gesten.hoch).put("runter", k.gesten.runter)
            .put("randUnten", k.gesten.randUnten).put("randLinks", k.gesten.randLinks).put("randRechts", k.gesten.randRechts))
        put("elemente", JSONArray().apply { k.elemente.forEach { put(element(it)) } })
        put("nichtPlatziert", JSONArray().apply { k.nichtPlatziert.forEach { put(element(it)) } })
        put("hintergrund", JSONObject().put("farbe", "#%08X".format(k.hintergrund.farbe)).put("bild", k.hintergrund.bild)
            .put("unsplash", k.hintergrund.unsplash).put("thema", k.hintergrund.thema).put("schluessel", k.hintergrund.schluessel).put("quelle", k.hintergrund.quelle))
        put("bearbeitenGesperrt", k.bearbeitenGesperrt)
        put("pin", k.pin?.let { hash(it) } ?: JSONObject.NULL)
        put("boss", k.boss?.let { hash(it) } ?: JSONObject.NULL)
        put("pausen", JSONArray().apply { k.pausen.forEach { put(it.toString()) } })
        k.hinweis?.let { put("hinweis", JSONObject().put("text", it.text).put("bis", it.bis)) }
        put("feierabend", JSONObject().put("an", k.feierabend.an).put("ab", Zeitraum.uhrzeit(k.feierabend.ab))
            .put("bis", Zeitraum.uhrzeit(k.feierabend.bis)).put("wochenende", k.feierabend.wochenende))
        put("schoner", k.schoner)
        put("schueler", JSONObject().put("an", k.schueler.an).put("nurLehrer", JSONArray().apply { k.schueler.nurLehrer.sorted().forEach { put(it) } })
            .put("ohneAlleApps", k.schueler.ohneAlleApps).put("zurueck", k.schueler.zurueck))
        // 1.6.0
        put("aussehen", k.aussehen)
        put("symbolForm", k.symbolForm)
        put("dock", JSONArray().apply { k.dock.forEach { put(element(it)) } })
        put("seitenleiste", JSONObject().put("an", k.seitenleiste.an).put("seite", k.seitenleiste.seite)
            .put("apps", JSONArray().apply { k.seitenleiste.apps.forEach { put(element(it)) } }))
        put("stunden", JSONArray().apply { k.stunden.forEach { put(it.toString()) } })
        put("nachrichten", JSONArray().apply {
            k.nachrichten.forEach { n -> put(JSONObject().put("text", n.text).put("tag", n.tag).put("von", Zeitraum.uhrzeit(n.von)).put("bis", Zeitraum.uhrzeit(n.bis))) }
        })
        put("aufraeumen", JSONObject().put("pausen", k.aufraeumen.pausen).put("feierabend", k.aufraeumen.feierabend).put("lautstaerke", k.aufraeumen.lautstaerke))
        put("profil", k.profil)
        put("profile", JSONArray().apply {
            k.profile.forEach { p -> put(JSONObject().put("name", p.name).put("elemente", JSONArray().apply { p.elemente.forEach { put(element(it)) } })) }
        })
        put("profilNachFach", k.profilNachFach)
        k.statusZugang?.let { put("statusZugang", JSONObject().put("benutzer", it.benutzer).put("passwort", it.passwort)) }
    }.toString(2)

    private fun hash(h: PasswortHash) = JSONObject().put("hash", h.hash).put("salt", h.salt).put("runden", h.runden)

    private fun element(e: Element): JSONObject = when (e) {
        is Element.App -> JSONObject().put("typ", "app").put("paket", e.paket).put("aktivitaet", e.aktivitaet).put("spalte", e.spalte).put("zeile", e.zeile)
            .apply { e.titel?.let { put("titel", it) }; e.icon?.let { put("icon", it) } }
        is Element.Widget -> JSONObject().put("typ", "widget").put("provider", e.provider).put("kennung", e.kennung)
            .put("spalte", e.spalte).put("zeile", e.zeile).put("breite", e.breite).put("hoehe", e.hoehe).apply {
                if (e.zoom != 100) put("zoom", e.zoom)
                if (e.istUhr) put("uhr", JSONObject().put("schrift", e.uhr.schrift).put("datum", e.uhr.datum).put("format", e.uhr.format).put("sekunden", e.uhr.sekunden))
                if (e.provider in Element.MIT_TEXT) put("text", e.text)
            }
        is Ordner -> JSONObject().put("typ", "ordner").put("kennung", e.kennung).put("name", e.name).put("spalte", e.spalte).put("zeile", e.zeile)
            .put("apps", JSONArray().apply { e.apps.forEach { put(element(it)) } })
    }

    private val PAKET = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")
    private val KLASSE = Regex("^[A-Za-z0-9_.$]+$")
    private val KENNUNG = Regex("^[A-Za-z0-9_-]{1,40}$")
    /** Eigene Symbole: nur harmlose Dateinamen (Prüfsumme + .png). */
    val ICON_DATEI = Regex("^[a-f0-9]{8,64}\\.png$")

    /** Alle eigenen Symbol-Dateien, die eine Konfiguration braucht (für Export und Vorlage). */
    fun icons(k: Konfig): Set<String> = (alleElemente(k) + k.dock + k.seitenleiste.apps).flatMap { e ->
        when (e) {
            is Element.App -> listOf(e.icon)
            is Ordner -> e.apps.map { it.icon }
            else -> emptyList()
        }
    }.filterNotNull().toSet()

    /** Elemente aller Profile (das aktive und die gespeicherten) samt Ablage – z. B. damit keine Widgets verloren gehen. */
    fun alleElemente(k: Konfig): List<Element> = k.elemente + k.nichtPlatziert + k.profile.flatMap { it.elemente }

    /** Schlüssel eines Elements (für die Schüler-Ansicht). */
    private val SCHLUESSEL = Regex("^(app|widget|ordner):[A-Za-z0-9_.$/-]{1,300}$")

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

        fun elementeAus(a: JSONArray, name: String): List<Element> {
            return (0 until a.length()).mapNotNull { i ->
                val e = a.optJSONObject(i) ?: run { w.add("$name[$i] ist kein Eintrag"); return@mapNotNull null }
                val s = e.optInt("spalte", -1)
                val z = e.optInt("zeile", -1)
                fun app(e: JSONObject, wo: String, s: Int, z: Int): Element.App? {
                    val p = e.optString("paket")
                    val akt = e.optString("aktivitaet")
                    val titel = if (e.has("titel")) e.optString("titel").trim().take(60).ifEmpty { null } else null
                    val icon = if (e.has("icon")) e.optString("icon").takeIf { ICON_DATEI.matches(it) } ?: run { w.add("$wo: ungültiges Symbol"); null } else null
                    return if (!PAKET.matches(p) || !KLASSE.matches(akt)) { w.add("$wo: ungültige App"); null } else Element.App(p, akt, s, z, titel, icon)
                }
                when (e.optString("typ")) {
                    "app" -> app(e, "$name[$i]", s, z)
                    "ordner" -> {
                        val ken = e.optString("kennung")
                        val titel = e.optString("name").trim().take(40)
                        val liste = e.optJSONArray("apps")
                        if (!KENNUNG.matches(ken) || liste == null) { w.add("$name[$i]: ungültiger Ordner"); null } else {
                            val apps = (0 until liste.length()).mapNotNull { j ->
                                liste.optJSONObject(j)?.let { app(it, "$name[$i].apps[$j]", 0, 0) } ?: run { w.add("$name[$i].apps[$j] ungültig"); null }
                            }.distinctBy { it.schluessel }
                            if (apps.size > Ordner.MAX_APPS) w.add("$name[$i]: zu viele Apps im Ordner")
                            Ordner(ken, titel.ifEmpty { "Ordner" }, apps.take(Ordner.MAX_APPS), s, z)
                        }
                    }
                    "widget" -> {
                        val prov = e.optString("provider")
                        val ken = e.optString("kennung")
                        val teile = prov.split('/')
                        if (teile.size != 2 || !PAKET.matches(teile[0]) || !KLASSE.matches(teile[1]) || !KENNUNG.matches(ken)) {
                            w.add("$name[$i]: ungültiges Widget"); null
                        } else {
                            val zoom = e.optInt("zoom", 100)
                            if (zoom !in Grenzen.ZOOM) w.add("$name[$i]: zoom = $zoom liegt außerhalb")
                            val u = e.optJSONObject("uhr")
                            fun wahl(n: String, erlaubt: Collection<String>, std: String): String {
                                val v = u?.optString(n, "").orEmpty()
                                return if (v.isEmpty()) std else if (v in erlaubt) v else { w.add("$name[$i]: uhr.$n unbekannt"); std }
                            }
                            val uhr = UhrStil(wahl("schrift", Schrift.ALLE, Schrift.GRUNDSCHRIFT), wahl("datum", UhrStil.DATUM, UhrStil.UNTEN), wahl("format", UhrStil.FORMATE, UhrStil.LANG), u?.optBoolean("sekunden", false) ?: false)
                            val text = if (prov in Element.MIT_TEXT) e.optString("text", "").also { if (it.length > Grenzen.QR_TEXT) w.add("$name[$i]: Text zu lang") }.take(Grenzen.QR_TEXT) else ""
                            Element.Widget(prov, ken, s, z, e.optInt("breite", 1).coerceIn(1, 10), e.optInt("hoehe", 1).coerceIn(1, 10), zoom.coerceIn(Grenzen.ZOOM), uhr, text)
                        }
                    }
                    else -> { w.add("$name[$i]: unbekannter Typ"); null }
                }
            }
        }
        fun elemente(name: String): List<Element> {
            val a = o.optJSONArray(name) ?: run { if (o.has(name)) w.add("$name ist keine Liste"); return emptyList() }
            return elementeAus(a, name)
        }
        /** Nur Apps (Dock, Seitenleiste), ohne Doppelte. */
        fun appsAus(a: JSONArray?, name: String, max: Int): List<Element.App> {
            if (a == null) return emptyList()
            val liste = elementeAus(a, name)
            if (liste.any { it !is Element.App }) w.add("$name: nur Apps erlaubt")
            val apps = liste.filterIsInstance<Element.App>().distinctBy { it.schluessel }.map { it.verschoben(0, 0) as Element.App }
            if (apps.size > max) w.add("$name: zu viele Apps")
            return apps.take(max)
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

        // Unsplash kam mit 1.1.0 dazu – fehlt es (ältere Vorlage), ist das keine Warnung
        val thema = h?.optString("thema", "")?.trim().orEmpty().let { t ->
            if (t.isEmpty()) Hintergrund.STANDARD_THEMA else if (Hintergrund.THEMA.matches(t)) t else { w.add("hintergrund.thema ungültig"); Hintergrund.STANDARD_THEMA }
        }
        val schluessel = h?.optString("schluessel", "")?.trim().orEmpty().let { t ->
            if (t.isEmpty() || Hintergrund.SCHLUESSEL.matches(t)) t else { w.add("hintergrund.schluessel ungültig"); "" }
        }

        // Quelle kam mit 1.2.0 – wer schon einen Unsplash-Schlüssel hat, bleibt bei Unsplash
        val quelle = when (val q = h?.optString("quelle", "").orEmpty()) {
            "" -> if (schluessel.isNotEmpty()) Hintergrund.UNSPLASH else Hintergrund.COMMONS
            Hintergrund.COMMONS, Hintergrund.UNSPLASH -> q
            else -> { w.add("hintergrund.quelle unbekannt"); Hintergrund.COMMONS }
        }

        // Schrift und Gesten kamen mit 1.3.0 – fehlen sie, gilt der Standard (keine Warnung)
        fun wahl(quelle: JSONObject?, n: String, erlaubt: Collection<String>, std: String): String {
            val v = quelle?.optString(n, "").orEmpty()
            return if (v.isEmpty()) std else if (v in erlaubt) v else { w.add("$n unbekannt"); std }
        }
        val schrift = wahl(o, "schrift", Schrift.ALLE, Schrift.SYSTEM)
        // 1.4.0: ausgeblendete Apps (fehlt = keine)
        val ausgeblendet = o.optJSONArray("ausgeblendet")?.let { a ->
            (0 until a.length()).mapNotNull { i ->
                val t = a.optString(i)
                val teile = t.split('/')
                if (teile.size == 2 && PAKET.matches(teile[0]) && KLASSE.matches(teile[1])) t else { w.add("ausgeblendet[$i] ungültig"); null }
            }.toSet()
        } ?: emptySet()
        val g = o.optJSONObject("gesten")
        val sg = Gesten()
        val gesten = Gesten(
            wahl(g, "hoch", Gesten.IM_LAUNCHER, sg.hoch), wahl(g, "runter", Gesten.IM_LAUNCHER, sg.runter),
            wahl(g, "randUnten", Gesten.AM_RAND, sg.randUnten), wahl(g, "randLinks", Gesten.AM_RAND, sg.randLinks), wahl(g, "randRechts", Gesten.AM_RAND, sg.randRechts)
        )

        fun passwort(name: String) = o.optJSONObject(name)?.let { p ->
            val hash = p.optString("hash")
            val salt = p.optString("salt")
            val runden = p.optInt("runden", 0)
            if (hash.isEmpty() || salt.isEmpty() || runden !in 1000..1_000_000) { w.add("$name ungültig – entfernt"); null } else PasswortHash(hash, salt, runden)
        }
        val pin = passwort("pin")
        val boss = passwort("boss")

        // 1.5.0: Pausen, Hinweis-Band, Feierabend, Bildschirmschoner, Schüler-Ansicht – fehlt etwas, gilt der Standard
        val pausen = o.optJSONArray("pausen")?.let { a ->
            (0 until a.length()).mapNotNull { i -> Zeitraum.aus(a.optString(i)) ?: run { w.add("pausen[$i] ungültig"); null } }
        }.orEmpty().distinct().sortedBy { it.von }.take(20)
        val hinweis = o.optJSONObject("hinweis")?.let { hw ->
            val t = hw.optString("text").trim().take(Hinweis.MAX)
            val bis = hw.optString("bis")
            if (t.isEmpty() || !Hinweis.DATUM.matches(bis)) { w.add("hinweis ungültig"); null } else Hinweis(t, bis)
        }
        val fa = o.optJSONObject("feierabend")
        val sf = Feierabend()
        fun uhr(n: String, std: Int): Int {
            val t = fa?.optString(n, "").orEmpty()
            return if (t.isEmpty()) std else Zeitraum.minuten(t) ?: run { w.add("feierabend.$n ungültig"); std }
        }
        val feierabend = Feierabend(fa?.optBoolean("an", false) ?: false, uhr("ab", sf.ab), uhr("bis", sf.bis), fa?.optBoolean("wochenende", true) ?: true)
        val schoner = if (o.has("schoner")) zahl(o, "schoner", Grenzen.SCHONER, 0) else 0
        val sa = o.optJSONObject("schueler")
        val schueler = if (sa == null) SchuelerAnsicht() else SchuelerAnsicht(
            sa.optBoolean("an", false),
            sa.optJSONArray("nurLehrer")?.let { a ->
                (0 until a.length()).mapNotNull { i -> a.optString(i).takeIf { SCHLUESSEL.matches(it) } ?: run { w.add("schueler.nurLehrer[$i] ungültig"); null } }.toSet()
            }.orEmpty(),
            sa.optBoolean("ohneAlleApps", true),
            if (sa.has("zurueck")) zahl(sa, "zurueck", Grenzen.ZURUECK, 20) else 20
        )

        // 1.6.0: Clevertouch-Aussehen, Dock, Seitenleiste, Stunden, Nachrichten, Aufräumen, Profile – fehlt etwas, gilt der Standard
        val aussehen = wahl(o, "aussehen", Aussehen.ALLE, Aussehen.KLASSISCH)
        val symbolForm = wahl(o, "symbolForm", SymbolForm.ALLE, SymbolForm.ORIGINAL)
        val dock = appsAus(o.optJSONArray("dock"), "dock", Grenzen.DOCK)
        val sl = o.optJSONObject("seitenleiste")
        val seitenleiste = if (sl == null) Seitenleiste() else Seitenleiste(
            sl.optBoolean("an", false),
            wahl(sl, "seite", setOf(Seitenleiste.LINKS, Seitenleiste.RECHTS), Seitenleiste.RECHTS),
            appsAus(sl.optJSONArray("apps"), "seitenleiste.apps", Seitenleiste.MAX_APPS)
        )
        val stunden = o.optJSONArray("stunden")?.let { a ->
            (0 until a.length()).mapNotNull { i -> Zeitraum.aus(a.optString(i)) ?: run { w.add("stunden[$i] ungültig"); null } }
        }.orEmpty().distinct().sortedBy { it.von }.take(16)
        val nachrichten = o.optJSONArray("nachrichten")?.let { a ->
            (0 until a.length()).mapNotNull { i ->
                val n = a.optJSONObject(i)
                val t = n?.optString("text")?.trim()?.take(Nachricht.MAX).orEmpty()
                val tag = n?.optString("tag").orEmpty()
                val von = n?.optString("von")?.let { Zeitraum.minuten(it) }
                val bis = n?.optString("bis")?.let { Zeitraum.minuten(it) }
                if (t.isEmpty() || !Hinweis.DATUM.matches(tag) || von == null || bis == null || bis <= von) { w.add("nachrichten[$i] ungültig"); null } else Nachricht(t, tag, von, bis)
            }
        }.orEmpty().take(Nachricht.ANZAHL)
        val au = o.optJSONObject("aufraeumen")
        val aufraeumen = if (au == null) Aufraeumen() else Aufraeumen(
            au.optBoolean("pausen", false), au.optBoolean("feierabend", true),
            if (au.has("lautstaerke")) zahl(au, "lautstaerke", Grenzen.LAUTSTAERKE, 40) else 40
        )
        val profilName = o.optString("profil", "").trim().let { p ->
            if (p.isEmpty()) Profil.STANDARD else if (Profil.NAME.matches(p)) p else { w.add("profil ungültig"); Profil.STANDARD }
        }
        val schonDa = (platziert + offen).map { it.schluessel }.toMutableSet()
        val profile = o.optJSONArray("profile")?.let { a ->
            val namen = mutableSetOf(profilName.lowercase())
            (0 until a.length()).mapNotNull { i ->
                val p = a.optJSONObject(i)
                val n = p?.optString("name")?.trim().orEmpty()
                val liste = p?.optJSONArray("elemente")
                if (!Profil.NAME.matches(n) || liste == null || !namen.add(n.lowercase())) { w.add("profile[$i] ungültig"); return@mapNotNull null }
                val roh2 = elementeAus(liste, "profile[$i].elemente")
                // Widget-Kennungen dürfen nur einmal vorkommen (sie gehören zu je einer Widget-Id)
                val eindeutig = roh2.filter { it !is Element.Widget || it.eingebaut || schonDa.add(it.schluessel) }
                if (eindeutig.size < roh2.size) w.add("profile[$i]: doppelte Widgets entfernt")
                Profil(n, Belegung.anpassen(eindeutig, emptyList(), spalten, zeilen).first)
            }
        }.orEmpty().take(Profil.MAX)

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
            schrift = schrift,
            gesten = gesten,
            ausgeblendet = ausgeblendet,
            elemente = platziert,
            nichtPlatziert = offen,
            hintergrund = Hintergrund(farbe, h?.optBoolean("bild", false) ?: false, h?.optBoolean("unsplash", false) ?: false, thema, schluessel, quelle),
            bearbeitenGesperrt = schalter("bearbeitenGesperrt", false),
            pin = pin,
            boss = boss,
            pausen = pausen,
            hinweis = hinweis,
            feierabend = feierabend,
            schoner = schoner,
            schueler = schueler,
            aussehen = aussehen,
            symbolForm = symbolForm,
            dock = dock,
            seitenleiste = seitenleiste,
            stunden = stunden,
            nachrichten = nachrichten,
            aufraeumen = aufraeumen,
            profil = profilName,
            profile = profile,
            profilNachFach = o.optBoolean("profilNachFach", false),
            statusZugang = o.optJSONObject("statusZugang")?.let { z ->
                val b = z.optString("benutzer").trim()
                val p = z.optString("passwort")
                if (b.isEmpty() || p.isEmpty() || b.length > 100 || p.length > 200) { w.add("statusZugang ungültig"); null } else Zugang(b, p)
            },
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
                is Ordner -> true
            }
            if (!da) protokoll.add(
                when (e) {
                    is Element.App -> "App fehlt: ${e.paket}"
                    is Element.Widget -> "Widget fehlt: ${e.provider}"
                    is Ordner -> ""
                }
            )
            da
        }
        return sichtbar to protokoll
    }
}
