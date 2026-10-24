package de.oejendorferdamm.dammlauncher

import android.app.Activity
import android.os.Bundle
import de.oejendorferdamm.dammlauncher.abgleich.Abgleich
import de.oejendorferdamm.dammlauncher.konfig.Element
import de.oejendorferdamm.dammlauncher.konfig.Geraet
import de.oejendorferdamm.dammlauncher.konfig.Konfig
import de.oejendorferdamm.dammlauncher.konfig.KonfigSpeicher
import de.oejendorferdamm.dammlauncher.konfig.Feierabend
import de.oejendorferdamm.dammlauncher.konfig.Hinweis
import de.oejendorferdamm.dammlauncher.konfig.Ordner
import de.oejendorferdamm.dammlauncher.konfig.Zeiten
import de.oejendorferdamm.dammlauncher.konfig.Zeitraum
import java.util.Calendar

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
        // „neu15“: die Kacheln und Ordner von 1.5.0 – Pause beginnt in 12 Minuten, Hinweis 3 Tage
        intent.getStringExtra("klasse")?.let { Geraet.klasse = it }
        if (intent.getBooleanExtra("neu15", false)) {
            KonfigSpeicher.init(this)
            val jetzt = Calendar.getInstance()
            val m = (jetzt.get(Calendar.HOUR_OF_DAY) * 60 + jetzt.get(Calendar.MINUTE) + 12).coerceAtMost(23 * 60)
            KonfigSpeicher.ersetze(Konfig(
                elemente = listOf(
                    Element.Widget(Element.UHR_TRANSPARENT, "uhr", 0, 0, 2, 1),
                    Element.Widget(Element.PAUSE, "pause", 2, 0, 2, 1),
                    Element.Widget(Element.ABWESEND, "abw", 4, 0, 2, 2),
                    Element.Widget(Element.QR, "qr", 0, 1, 2, 2, text = "https://oejendorfer-damm.de"),
                    Ordner("mathe", "Mathe", listOf(
                        Element.App("com.android.settings", "com.android.settings.Settings", 0, 0),
                        Element.App("com.android.deskclock", "com.android.deskclock.DeskClock", 0, 0),
                        Element.App("com.google.android.deskclock", "com.android.deskclock.DeskClock", 0, 0),
                        Element.App("com.android.chrome", "com.google.android.apps.chrome.Main", 0, 0)
                    ), 2, 1)
                ),
                pausen = listOf(Zeitraum(m, (m + 20).coerceAtMost(24 * 60 - 1))),
                hinweis = Hinweis("Morgen Wandertag – bitte Regenjacke mitbringen!", Zeiten.hinweisBis(3, jetzt)),
                stand = System.currentTimeMillis()
            ))
        }
        // „feierabend“: Sperre ab jetzt; „schoner“: Bildschirmschoner nach so vielen Minuten
        if (intent.getBooleanExtra("feierabend", false)) {
            KonfigSpeicher.init(this)
            val jetzt = Calendar.getInstance()
            val m = jetzt.get(Calendar.HOUR_OF_DAY) * 60 + jetzt.get(Calendar.MINUTE)
            Geraet.feierabendFreiBis = 0
            KonfigSpeicher.aendere { it.copy(feierabend = Feierabend(true, (m + 1439) % 1440, (m + 120) % 1440, false)) }
        }
        if (intent.hasExtra("schoner")) {
            KonfigSpeicher.init(this)
            KonfigSpeicher.aendere { it.copy(schoner = intent.getIntExtra("schoner", 0), feierabend = Feierabend()) }
        }
        finish()
    }
}
