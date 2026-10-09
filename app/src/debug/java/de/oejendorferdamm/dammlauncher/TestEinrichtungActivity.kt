package de.oejendorferdamm.dammlauncher

import android.app.Activity
import android.os.Bundle
import de.oejendorferdamm.dammlauncher.abgleich.Abgleich
import de.oejendorferdamm.dammlauncher.konfig.Geraet

/**
 * NUR in der Testversion: trägt den Test-Server als WebDAV-Zugang ein (Extras server, ordner,
 * benutzer, passwort) – das Abtippen am Emulator wäre unzuverlässig. Gibt es in der Release-APK nicht.
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
        finish()
    }
}
