/*
 * DammLauncher – Copyright (C) 2026 Teyro – GNU GPL v3 oder neuer, siehe LICENSE
 */
package de.oejendorferdamm.dammlauncher.abgleich

import de.oejendorferdamm.dammlauncher.konfig.Geraet
import de.oejendorferdamm.dammlauncher.sicherheit.Tresor

/**
 * Schul-Ordner (Schuldock-Freigabe https://cloudfiles.s5586.schuldock.de/s/mxmLySXtyzC6XFp) mit
 * DammLauncher/Master.zip, DammLauncher/Symbole und DammLauncher/Wallpaper. Das Passwort steht bewusst
 * nicht im öffentlichen Code: es wird einmal am Board eingegeben und verschlüsselt gemerkt.
 */
object SchulOrdner {
    const val WEBDAV = "https://cloudfiles.s5586.schuldock.de/public.php/webdav"
    const val FREIGABE = "mxmLySXtyzC6XFp"
    const val WALLPAPER = "DammLauncher/Wallpaper"
    val BILD = Regex("(?i).+\\.(png|jpe?g|webp)$")

    fun passwort(): String? = Tresor.entschluessle(Geraet.schulPasswortVerschluesselt)?.takeIf { it.isNotEmpty() }

    fun dav(passwort: String) = WebDav(WEBDAV, FREIGABE, passwort)

    /** Bild des Tages aus [namen]: reihum jeden Tag das nächste – alle Boards zeigen am selben Tag dasselbe. */
    fun bildDesTages(namen: List<String>, tagSeit1970: Long): String? {
        val liste = namen.filter { BILD.matches(it) }.sortedBy { it.lowercase() }
        if (liste.isEmpty()) return null
        return liste[Math.floorMod(tagSeit1970, liste.size.toLong()).toInt()]
    }
}
