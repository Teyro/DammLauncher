/*
 * DammLauncher – Copyright (C) 2026 Teyro – GNU GPL v3 oder neuer, siehe LICENSE
 */
package de.oejendorferdamm.dammlauncher.nutzung

import java.util.Calendar
import java.util.TimeZone

/** Ein Tag der Statistik: Sitzungen auf diesen Tag zugeschnitten. */
data class Tag(
    val beginn: Long,
    val bildschirm: List<Sitzung>,
    val apps: List<Sitzung>
) {
    val anMs: Long get() = bildschirm.sumOf { it.dauer }
    val appMs: Long get() = apps.sumOf { it.dauer }
    val erstesAn: Long? get() = bildschirm.minOfOrNull { it.start }
    val letztesAus: Long? get() = bildschirm.maxOfOrNull { it.ende }
    val leer: Boolean get() = bildschirm.isEmpty() && apps.isEmpty()
}

/** Reine Rechnungen für Bildschirm, Export und Tests. */
object Auswertung {
    /** Ab dieser Uhrzeit gilt ein Board als „abends noch an“ */
    const val ABEND_STUNDE = 18

    fun tagesBeginn(t: Long, zone: TimeZone = TimeZone.getDefault()): Long = Calendar.getInstance(zone).apply {
        timeInMillis = t
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun naechsterTag(beginn: Long, zone: TimeZone): Long = Calendar.getInstance(zone).apply {
        timeInMillis = beginn; add(Calendar.DAY_OF_YEAR, 1)
    }.timeInMillis

    /** Die letzten [anzahl] Tage bis einschließlich heute, neuester zuerst; Sitzungen über Mitternacht werden geteilt. */
    fun tage(sitzungen: List<Sitzung>, jetzt: Long, anzahl: Int, zone: TimeZone = TimeZone.getDefault()): List<Tag> {
        val heute = tagesBeginn(jetzt, zone)
        val beginne = ArrayList<Long>()
        var b = heute
        repeat(anzahl) {
            beginne.add(b)
            b = Calendar.getInstance(zone).apply { timeInMillis = b; add(Calendar.DAY_OF_YEAR, -1) }.timeInMillis
        }
        return beginne.map { start ->
            val ende = naechsterTag(start, zone)
            fun zu(typ: Char) = sitzungen.filter { it.typ == typ && it.ende > start && it.start < ende }
                .map { it.copy(start = maxOf(it.start, start), ende = minOf(it.ende, ende)) }
                .sortedBy { it.start }
            Tag(start, zu(Sitzung.BILDSCHIRM), zu(Sitzung.APP))
        }
    }

    /** Zeit je App über alle Tage, größte zuerst */
    fun jeApp(tage: List<Tag>): List<Pair<String, Long>> =
        tage.flatMap { it.apps }.groupBy { it.paket }.map { (p, l) -> p to l.sumOf { it.dauer } }.sortedByDescending { it.second }

    /** Wie oft eine App gestartet wurde */
    fun starts(tage: List<Tag>): Map<String, Int> = tage.flatMap { it.apps }.groupingBy { it.paket }.eachCount()

    /** Tage, an denen der Bildschirm nach [ABEND_STUNDE] Uhr noch an war */
    fun abendsAn(tage: List<Tag>, zone: TimeZone = TimeZone.getDefault()): Int = tage.count { t ->
        val abend = Calendar.getInstance(zone).apply { timeInMillis = t.beginn; set(Calendar.HOUR_OF_DAY, ABEND_STUNDE) }.timeInMillis
        t.bildschirm.any { it.ende > abend }
    }

    /** Die Top-[n] Apps behalten ihre Farbe, der Rest wird „Andere“ */
    fun farbPlaetze(jeApp: List<Pair<String, Long>>, n: Int = 7): Map<String, Int> =
        jeApp.take(n).mapIndexed { i, (p, _) -> p to i }.toMap()

    /** „3 h 05 min“, „42 min“, „< 1 min“ */
    fun dauer(ms: Long): String {
        val min = ms / 60_000L
        return when {
            min < 1 -> "< 1 min"
            min < 60 -> "$min min"
            else -> "${min / 60} h %02d min".format(min % 60)
        }
    }
}
