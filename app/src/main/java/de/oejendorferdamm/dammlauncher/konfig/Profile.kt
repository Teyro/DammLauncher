/*
 * DammLauncher – Copyright (C) 2026 Teyro – GNU GPL v3 oder neuer, siehe LICENSE
 */
package de.oejendorferdamm.dammlauncher.konfig

/**
 * Profile: Die Elemente des aktiven Profils stehen in [Konfig.elemente] (so funktioniert alles
 * Bearbeiten wie gewohnt), die übrigen in [Konfig.profile]. Wechseln tauscht beides aus.
 */
object Profile {
    fun namen(k: Konfig): List<String> = listOf(k.profil) + k.profile.map { it.name }

    fun finde(k: Konfig, name: String): String? = namen(k).firstOrNull { it.equals(name.trim(), ignoreCase = true) }

    /** Zum Profil [name] wechseln (unbekannt oder schon aktiv: unverändert). */
    fun wechseln(k: Konfig, name: String): Konfig {
        val ziel = k.profile.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) } ?: return k
        return k.copy(
            elemente = ziel.elemente,
            profil = ziel.name,
            profile = k.profile.filter { it !== ziel } + Profil(k.profil, k.elemente)
        )
    }

    /** Neues Profil – leer oder als Kopie des aktiven (Widgets werden nicht mitkopiert: jede Kennung gehört zu genau einer Widget-Id). */
    fun neu(k: Konfig, name: String, kopie: Boolean): Konfig? {
        val n = name.trim()
        if (!Profil.NAME.matches(n) || finde(k, n) != null || k.profile.size + 1 >= Profil.MAX) return null
        val elemente = if (kopie) k.elemente.filter { it !is Element.Widget || it.eingebaut }.map {
            // eingebaute Kacheln bekommen eine neue Kennung, sonst teilten sich zwei Profile eine
            if (it is Element.Widget) it.copy(kennung = "k" + java.util.UUID.randomUUID().toString().replace("-", "").take(8)) else it
        } else emptyList()
        return k.copy(profile = k.profile + Profil(n, elemente))
    }

    fun umbenennen(k: Konfig, alt: String, neu: String): Konfig? {
        val n = neu.trim()
        if (!Profil.NAME.matches(n)) return null
        val schon = finde(k, n)
        if (schon != null && !schon.equals(alt, ignoreCase = true)) return null
        return if (k.profil.equals(alt, ignoreCase = true)) k.copy(profil = n)
        else k.copy(profile = k.profile.map { if (it.name.equals(alt, ignoreCase = true)) it.copy(name = n) else it })
    }

    /** Ein gespeichertes (nicht das aktive) Profil löschen. */
    fun loeschen(k: Konfig, name: String): Konfig = k.copy(profile = k.profile.filter { !it.name.equals(name, ignoreCase = true) })
}
