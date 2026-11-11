/*
 * DammLauncher – Copyright (C) 2026 Teyro – GNU GPL v3 oder neuer, siehe LICENSE
 */
package de.oejendorferdamm.dammlauncher.nutzung

import android.content.Context
import android.content.SharedPreferences
import java.io.File

/** Ein Zeitraum: Bildschirm an ([BILDSCHIRM]) oder eine App vorn ([APP], mit Paketname). */
data class Sitzung(val typ: Char, val start: Long, val ende: Long, val paket: String = "") {
    val dauer get() = ende - start

    companion object {
        const val BILDSCHIRM = 'B'
        const val APP = 'A'
    }
}

/** Was gerade offen ist (wird gespeichert, damit ein Neustart nichts verliert). */
data class Zustand(
    var offenPaket: String? = null,
    var offenSeit: Long = 0L,
    var bildschirmSeit: Long = 0L,
    /** letztes Lebenszeichen (Puls jede Minute) – nach einem Absturz/Ausschalten endet dort alles Offene */
    var lebt: Long = 0L,
    /** App, die beim Ausschalten des Bildschirms vorn war (kommt beim Einschalten zurück) */
    var pausiert: String? = null
)

/**
 * Der Ablauf als reine Regeln (ohne Android, testbar): jedes Ereignis liefert die fertigen Sitzungen.
 * Gezählt wird nur, was der Launcher selbst sieht: welche App er (oder die Seitenleiste) gestartet hat,
 * bis der Startbildschirm wieder vorn ist, und wann der Bildschirm an war.
 */
object Ablauf {
    /** Fehlt der Puls länger, war das Board aus (oder der Launcher beendet) */
    const val LUECKE = 3 * 60_000L
    /** Kürzer zählt nicht (Fehltipp, kurzes Zurück) */
    const val MINDESTENS = 3_000L

    private fun sitzung(typ: Char, start: Long, ende: Long, paket: String = "") =
        if (start > 0 && ende - start >= MINDESTENS) listOf(Sitzung(typ, start, ende, paket)) else emptyList()

    private fun appZu(z: Zustand, ende: Long): List<Sitzung> {
        val p = z.offenPaket ?: return emptyList()
        z.offenPaket = null
        return sitzung(Sitzung.APP, z.offenSeit, ende, p)
    }

    /** Erst prüfen, ob dazwischen eine Lücke lag – dann endet alles Offene beim letzten Lebenszeichen. */
    private fun luecke(z: Zustand, jetzt: Long): List<Sitzung> {
        if (z.lebt == 0L || jetzt - z.lebt <= LUECKE) return emptyList()
        // Bildschirm war aus und nichts offen: die Lücke ist der Schlaf des Boards – die pausierte App bleibt gemerkt
        if (z.bildschirmSeit == 0L && z.offenPaket == null) return emptyList()
        val ende = z.lebt
        val r = appZu(z, ende) + sitzung(Sitzung.BILDSCHIRM, z.bildschirmSeit, ende)
        z.bildschirmSeit = 0L
        z.pausiert = null
        return r
    }

    fun puls(z: Zustand, jetzt: Long, bildschirmAn: Boolean): List<Sitzung> {
        val r = luecke(z, jetzt)
        if (bildschirmAn && z.bildschirmSeit == 0L) z.bildschirmSeit = jetzt
        z.lebt = jetzt
        return r
    }

    fun appStart(z: Zustand, paket: String, jetzt: Long): List<Sitzung> {
        val r = luecke(z, jetzt) + appZu(z, jetzt)
        z.offenPaket = paket
        z.offenSeit = jetzt
        if (z.bildschirmSeit == 0L) z.bildschirmSeit = jetzt
        z.lebt = jetzt
        return r
    }

    fun launcherVorn(z: Zustand, jetzt: Long): List<Sitzung> {
        val r = luecke(z, jetzt) + appZu(z, jetzt)
        z.pausiert = null
        if (z.bildschirmSeit == 0L) z.bildschirmSeit = jetzt
        z.lebt = jetzt
        return r
    }

    fun bildschirmAus(z: Zustand, jetzt: Long): List<Sitzung> {
        val r = luecke(z, jetzt).toMutableList()
        z.pausiert = z.offenPaket
        r += appZu(z, jetzt)
        r += sitzung(Sitzung.BILDSCHIRM, z.bildschirmSeit, jetzt)
        z.bildschirmSeit = 0L
        z.lebt = jetzt
        return r
    }

    fun bildschirmAn(z: Zustand, jetzt: Long, launcherVorn: Boolean): List<Sitzung> {
        val r = luecke(z, jetzt)
        if (z.bildschirmSeit == 0L) z.bildschirmSeit = jetzt
        val p = z.pausiert
        if (!launcherVorn && p != null && z.offenPaket == null) { z.offenPaket = p; z.offenSeit = jetzt }
        z.pausiert = null
        z.lebt = jetzt
        return r
    }
}

/**
 * Nutzungs-Statistik dieses Boards: bleibt auf dem Board (Datei im App-Ordner), nach 30 Tagen
 * gelöscht. Nach außen geht nur, ob das Board an ist und ob gerade eine App vorn ist – ohne Namen.
 */
object Nutzung {
    const val AUFBEWAHREN_TAGE = 30
    private lateinit var p: SharedPreferences
    private lateinit var datei: File
    private var eigenesPaket = ""
    private val z = Zustand()

    @Synchronized
    fun init(context: Context) {
        if (::p.isInitialized) return
        p = context.getSharedPreferences("nutzung", Context.MODE_PRIVATE)
        datei = File(context.filesDir, "nutzung.tsv")
        eigenesPaket = context.packageName
        z.offenPaket = p.getString("offenPaket", null)
        z.offenSeit = p.getLong("offenSeit", 0L)
        z.bildschirmSeit = p.getLong("bildschirmSeit", 0L)
        z.lebt = p.getLong("lebt", 0L)
        z.pausiert = p.getString("pausiert", null)
    }

    var an: Boolean
        get() = p.getBoolean("an", true)
        set(v) { p.edit().putBoolean("an", v).apply(); if (!v) synchronized(this) { z.offenPaket = null; z.bildschirmSeit = 0L; merke() } }

    /** Gerade eine App vorn (nicht der Startbildschirm)? */
    val appVorn: Boolean get() = synchronized(this) { z.offenPaket != null }

    private fun merke() = p.edit()
        .putString("offenPaket", z.offenPaket).putLong("offenSeit", z.offenSeit)
        .putLong("bildschirmSeit", z.bildschirmSeit).putLong("lebt", z.lebt).putString("pausiert", z.pausiert)
        .apply()

    @Synchronized
    private fun ereignis(f: (Zustand) -> List<Sitzung>) {
        if (!::p.isInitialized || !an) return
        val neu = try { f(z) } catch (_: Exception) { emptyList() }
        merke()
        if (neu.isNotEmpty()) try {
            datei.appendText(neu.joinToString("") { "${it.typ}\t${it.start}\t${it.ende}\t${it.paket.replace('\t', ' ')}\n" })
        } catch (_: Exception) {}
    }

    fun puls(bildschirmAn: Boolean) = ereignis { Ablauf.puls(it, System.currentTimeMillis(), bildschirmAn) }
    fun appStart(paket: String) { if (paket != eigenesPaket) ereignis { Ablauf.appStart(it, paket, System.currentTimeMillis()) } }
    fun launcherVorn() = ereignis { Ablauf.launcherVorn(it, System.currentTimeMillis()) }
    fun bildschirmAus() = ereignis { Ablauf.bildschirmAus(it, System.currentTimeMillis()) }
    fun bildschirmAn(launcherVorn: Boolean) = ereignis { Ablauf.bildschirmAn(it, System.currentTimeMillis(), launcherVorn) }

    /**
     * Alle Sitzungen ab [von] – inklusive der gerade offenen (bis jetzt). Räumt dabei Älteres als
     * 30 Tage aus der Datei.
     */
    @Synchronized
    fun sitzungen(von: Long): List<Sitzung> {
        if (!::p.isInitialized) return emptyList()
        val jetzt = System.currentTimeMillis()
        val grenze = jetzt - AUFBEWAHREN_TAGE * 24 * 60 * 60_000L
        val alle = try { if (datei.exists()) datei.readLines().mapNotNull(::lies) else emptyList() } catch (_: Exception) { emptyList() }
        if (alle.any { it.ende < grenze }) try {
            datei.writeText(alle.filter { it.ende >= grenze }.joinToString("") { "${it.typ}\t${it.start}\t${it.ende}\t${it.paket}\n" })
        } catch (_: Exception) {}
        val offen = buildList {
            if (z.bildschirmSeit > 0) add(Sitzung(Sitzung.BILDSCHIRM, z.bildschirmSeit, jetzt))
            z.offenPaket?.let { add(Sitzung(Sitzung.APP, z.offenSeit, jetzt, it)) }
        }
        return (alle + offen).filter { it.ende >= von }
    }

    @Synchronized
    fun loeschen() {
        try { datei.delete() } catch (_: Exception) {}
        val jetzt = System.currentTimeMillis()
        if (z.bildschirmSeit > 0) z.bildschirmSeit = jetzt
        if (z.offenPaket != null) z.offenSeit = jetzt
        merke()
    }

    fun lies(zeile: String): Sitzung? {
        val t = zeile.split('\t')
        if (t.size < 3 || t[0].length != 1) return null
        val s = t[1].toLongOrNull() ?: return null
        val e = t[2].toLongOrNull() ?: return null
        if (e < s) return null
        return Sitzung(t[0][0], s, e, t.getOrElse(3) { "" })
    }

    /** Nur für Bildschirmfotos im Emulator (Debug): 14 Tage Beispieldaten */
    @Synchronized
    fun beispiel(pakete: List<String>) {
        if (!::p.isInitialized || pakete.isEmpty()) return
        val kal = java.util.Calendar.getInstance()
        val zufall = java.util.Random(7)
        val sb = StringBuilder()
        for (t in 14 downTo 1) {
            kal.timeInMillis = System.currentTimeMillis(); kal.add(java.util.Calendar.DAY_OF_YEAR, -t)
            val wt = kal.get(java.util.Calendar.DAY_OF_WEEK)
            if (wt == java.util.Calendar.SATURDAY || wt == java.util.Calendar.SUNDAY) continue
            kal.set(java.util.Calendar.HOUR_OF_DAY, 7); kal.set(java.util.Calendar.MINUTE, 40 + zufall.nextInt(15)); kal.set(java.util.Calendar.SECOND, 0)
            val an = kal.timeInMillis
            val aus = an + (7 + zufall.nextInt(4)) * 3_600_000L + (if (t % 5 == 0) 6 * 3_600_000L else 0L)
            sb.append("B\t$an\t$aus\t\n")
            var s = an + 5 * 60_000L
            while (s < aus - 30 * 60_000L) {
                val d = (10 + zufall.nextInt(50)) * 60_000L
                sb.append("A\t$s\t${minOf(s + d, aus)}\t${pakete[zufall.nextInt(pakete.size)]}\n")
                s += d + (5 + zufall.nextInt(25)) * 60_000L
            }
        }
        datei.appendText(sb.toString())
    }
}
