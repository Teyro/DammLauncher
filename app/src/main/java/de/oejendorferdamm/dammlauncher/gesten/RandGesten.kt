/*
 * DammLauncher – Copyright (C) 2026 Teyro – GNU GPL v3 oder neuer, siehe LICENSE
 */
package de.oejendorferdamm.dammlauncher.gesten

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import de.oejendorferdamm.dammlauncher.HomeActivity
import de.oejendorferdamm.dammlauncher.konfig.Gesten
import de.oejendorferdamm.dammlauncher.konfig.KonfigSpeicher
import de.oejendorferdamm.dammlauncher.ui.Masse
import de.oejendorferdamm.dammlauncher.ui.vp
import kotlin.math.abs

/**
 * Wischgesten am Bildschirmrand – auch während eine andere App (Firefox, Anton …) vorne ist. Dazu
 * liegen schmale, unsichtbare Leisten am Rand („Über anderen Apps einblenden“). Wer vom Rand zur
 * Mitte wischt, kommt zum Startbildschirm bzw. zu allen Apps. Läuft als Vordergrunddienst, damit
 * Android die Leisten nicht nach ein paar Minuten entfernt.
 */
class RandGesten : Service() {
    private val leisten = ArrayList<View>()
    private val wm get() = getSystemService(WindowManager::class.java)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        laufend = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, hinweis())
        KonfigSpeicher.init(this)
        Masse.aktualisiere(this)
        aufbauen()
        return START_STICKY
    }

    override fun onDestroy() {
        abbauen()
        if (laufend === this) laufend = null
        super.onDestroy()
    }

    private fun hinweis(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm?.createNotificationChannel(NotificationChannel(KANAL, "Wischgesten", NotificationManager.IMPORTANCE_MIN).apply { setShowBadge(false) })
        return Notification.Builder(this, KANAL)
            .setSmallIcon(android.R.drawable.ic_menu_revert)
            .setContentTitle("Wischgesten am Bildschirmrand aktiv")
            .setContentText("Vom Rand zur Mitte wischen führt zum Startbildschirm.")
            .setOngoing(true)
            .build()
    }

    private fun abbauen() {
        leisten.forEach { try { wm?.removeView(it) } catch (_: Exception) {} }
        leisten.clear()
    }

    private fun aufbauen() {
        abbauen()
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return }
        val g = KonfigSpeicher.konfig.gesten
        val m = resources.displayMetrics
        val dick = 22.vp
        if (g.randUnten != Gesten.AUS) leiste(Rand.UNTEN, g.randUnten, (m.widthPixels * 0.6f).toInt(), dick, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        if (g.randLinks != Gesten.AUS) leiste(Rand.LINKS, g.randLinks, dick, (m.heightPixels * 0.6f).toInt(), Gravity.START or Gravity.CENTER_VERTICAL)
        if (g.randRechts != Gesten.AUS) leiste(Rand.RECHTS, g.randRechts, dick, (m.heightPixels * 0.6f).toInt(), Gravity.END or Gravity.CENTER_VERTICAL)
        zeigen(!launcherVorne)
    }

    private fun leiste(rand: Rand, aktion: String, b: Int, h: Int, lage: Int) {
        val v = Leiste(this, rand) { ausloesen(aktion) }
        val p = WindowManager.LayoutParams(
            b, h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = lage; title = "DammLauncher-Geste" }
        try {
            wm?.addView(v, p)
            leisten.add(v)
        } catch (_: Exception) {
            // Erlaubnis zurückgenommen o. Ä. – dann eben ohne diese Leiste
        }
    }

    private fun zeigen(an: Boolean) {
        android.util.Log.i("DammLauncher", "Randgeste: Leisten ${if (an) "sichtbar" else "aus"} (${leisten.size})")
        leisten.forEach { it.visibility = if (an) View.VISIBLE else View.GONE }
    }

    private fun ausloesen(aktion: String) {
        try {
            startActivity(Intent(this, HomeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                .putExtra(HomeActivity.EXTRA_GESTE, aktion))
        } catch (_: Exception) {
        }
    }

    private enum class Rand { UNTEN, LINKS, RECHTS }

    /** Unsichtbare Leiste; unten mit einem dezenten Strich als Hinweis. Löst einmal pro Wischen aus. */
    @SuppressLint("ViewConstructor")
    private class Leiste(c: Context, private val rand: Rand, private val aktion: () -> Unit) : View(c) {
        private var x0 = 0f
        private var y0 = 0f
        private var erledigt = false
        private val strich = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x59FFFFFF }

        override fun onDraw(canvas: Canvas) {
            if (rand != Rand.UNTEN) return
            val b = 140f.vp
            val h = 5f.vp
            canvas.drawRoundRect((width - b) / 2f, height - h - 5f.vp, (width + b) / 2f, height - 5f.vp, h, h, strich)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(ev: MotionEvent): Boolean {
            if (ev.actionMasked == MotionEvent.ACTION_DOWN) android.util.Log.i("DammLauncher", "Randgeste: Berührung $rand")
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> { x0 = ev.rawX; y0 = ev.rawY; erledigt = false }
                MotionEvent.ACTION_MOVE -> if (!erledigt) {
                    val dx = ev.rawX - x0
                    val dy = ev.rawY - y0
                    val weit = 90f.vp
                    val ok = when (rand) {
                        Rand.UNTEN -> -dy > weit && -dy > 1.5f * abs(dx)
                        Rand.LINKS -> dx > weit && dx > 1.5f * abs(dy)
                        Rand.RECHTS -> -dx > weit && -dx > 1.5f * abs(dy)
                    }
                    if (ok) { erledigt = true; aktion() }
                }
            }
            return true
        }
    }

    companion object {
        private const val KANAL = "gesten"
        private var laufend: RandGesten? = null

        /** Ist der Startbildschirm vorne, stören die Leisten nur – dann ausblenden. */
        @Volatile
        var launcherVorne = false
            private set

        fun vorne(vorne: Boolean) {
            launcherVorne = vorne
            laufend?.zeigen(!vorne)
        }

        fun erlaubt(c: Context) = Settings.canDrawOverlays(c)

        /** Dienst passend zur Einrichtung starten, beenden oder ([neu]) die Leisten neu aufbauen. */
        fun abgleichen(c: Context, neu: Boolean = false) {
            val an = KonfigSpeicher.konfig.gesten.randAn && erlaubt(c)
            try {
                when {
                    an && (laufend == null || neu) -> c.startForegroundService(Intent(c, RandGesten::class.java))
                    !an && laufend != null -> c.stopService(Intent(c, RandGesten::class.java))
                }
            } catch (_: Exception) {
                // z. B. Start aus dem Hintergrund verboten – beim nächsten Öffnen des Launchers klappt es
            }
        }
    }
}
