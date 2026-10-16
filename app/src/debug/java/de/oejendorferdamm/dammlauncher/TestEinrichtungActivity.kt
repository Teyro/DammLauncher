package de.oejendorferdamm.dammlauncher

import android.app.Activity
import android.os.Bundle
import de.oejendorferdamm.dammlauncher.abgleich.Abgleich
import de.oejendorferdamm.dammlauncher.konfig.Element
import de.oejendorferdamm.dammlauncher.konfig.Geraet
import de.oejendorferdamm.dammlauncher.konfig.Konfig
import de.oejendorferdamm.dammlauncher.konfig.KonfigSpeicher

/**
 * NUR in der Testversion: trägt den Test-Server als WebDAV-Zugang ein (Extras server, ordner,
 * benutzer, passwort) bzw. setzt mit „leer“ eine schlichte Einrichtung – das Abtippen am Emulator wäre unzuverlässig. Gibt es in der Release-APK nicht.
 */
class TestEinrichtungActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Geraet.init(this)
        intent.getStringExtra("server")?.let { Geraet.server = it }
        intent.getStringExtra("ordner")?.let { Geraet.ordner = it }
        val b = intent.getStringExtra("benutzer")
        val p = intent.getStringExtra("passwort")
        if (b != null && p != null) Abgleich.speichereZugang(b, p)
        // „leer“: schlichte Einrichtung (6 × 4, nur die Uhr, Knopf „Alle Apps“) für den Oberflächentest
        if (intent.getBooleanExtra("leer", false)) {
            KonfigSpeicher.init(this)
            KonfigSpeicher.ersetze(Konfig(elemente = listOf(Element.Widget(Element.UHR, "uhr", 0, 0, 3, 2))))
        }
        finish()
    }
}
