/*
 * DammLauncher – Copyright (C) 2026 Teyro – GNU GPL v3 oder neuer, siehe LICENSE
 */
package de.oejendorferdamm.dammlauncher.gesten

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.IBinder
import android.provider.Settings
import android.text.TextPaint
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import de.oejendorferdamm.dammlauncher.HomeActivity
import de.oejendorferdamm.dammlauncher.apps.AppKatalog
import de.oejendorferdamm.dammlauncher.konfig.Element
import de.oejendorferdamm.dammlauncher.konfig.Gesten
import de.oejendorferdamm.dammlauncher.konfig.KonfigSpeicher
import de.oejendorferdamm.dammlauncher.konfig.Seitenleiste
import de.oejendorferdamm.dammlauncher.ui.Masse
import de.oejendorferdamm.dammlauncher.ui.groesse
import de.oejendorferdamm.dammlauncher.ui.runde
import de.oejendorferdamm.dammlauncher.ui.vp
import kotlin.math.abs

/**
 * Leisten über anderen Apps („Über anderen Apps einblenden“), als Vordergrunddienst, damit Android
 * sie nicht nach ein paar Minuten entfernt:
 *  - Wischgesten am Bildschirmrand (vom Rand zur Mitte → Startbildschirm bzw. alle Apps),
 *  - die Seitenleiste wie bei Clevertouch (Griff am Rand → Start, Zurück, Whiteboard, Timer …),
 *  - „Abdecken“: ein schwarzer Vorhang, der sich von unten nach oben aufziehen lässt.
 * Über den Android-Einstellungen blendet das System fremde Leisten aus – dort wirken sie nicht.
 */
class RandGesten : Service() {
    private val leisten = ArrayList<View>()
    private var griff: View? = null
    private var leiste: View? = null
    private var vorhang: View? = null
    private val wm get() = getSystemService(WindowManager::class.java)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        laufend = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            startForeground(1, hinweis())
        } catch (_: Exception) {
            // Ab Android 12 darf ein Dienst, den das System im Hintergrund neu startet, nicht in den
            // Vordergrund – dann beenden; beim nächsten Öffnen des Launchers startet er wieder.
            stopSelf()
            return START_NOT_STICKY
        }
        KonfigSpeicher.init(this)
        Masse.aktualisiere(this)
        AppKatalog.init(this)
        // erst den Vorhang, sonst beendet sich der Dienst beim Aufbauen, wenn sonst nichts zu zeigen ist
        if (intent?.action == ABDECKEN) zeigeVorhang()
        aufbauen()
        return START_STICKY
    }

    override fun onDestroy() {
        abbauen()
        vorhangWeg()
        if (laufend === this) laufend = null
        super.onDestroy()
    }

    private fun hinweis(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm?.createNotificationChannel(NotificationChannel(KANAL, "Wischgesten", NotificationManager.IMPORTANCE_MIN).apply { setShowBadge(false) })
        return Notification.Builder(this, KANAL)
            .setSmallIcon(android.R.drawable.ic_menu_revert)
            .setContentTitle("DammLauncher-Leisten aktiv")
            .setContentText("Wischgesten bzw. Seitenleiste am Bildschirmrand.")
            .setOngoing(true)
            .build()
    }

    private fun entferne(v: View?) {
        if (v != null) try { wm?.removeView(v) } catch (_: Exception) {}
    }

    private fun abbauen() {
        leisten.forEach { entferne(it) }
        leisten.clear()
        entferne(griff); griff = null
        leisteZu()
    }

    private fun aufbauen() {
        abbauen()
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return }
        val k = KonfigSpeicher.konfig
        val g = k.gesten
        val m = resources.displayMetrics
        val dick = 22.vp
        if (g.randUnten != Gesten.AUS) leiste(Rand.UNTEN, g.randUnten, (m.widthPixels * 0.6f).toInt(), dick, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        // Die Seitenleiste braucht ihren Rand für den Griff – dort keine Wischleiste
        val slSeite = if (k.seitenleiste.an) k.seitenleiste.seite else ""
        if (g.randLinks != Gesten.AUS && slSeite != Seitenleiste.LINKS) leiste(Rand.LINKS, g.randLinks, dick, (m.heightPixels * 0.6f).toInt(), Gravity.START or Gravity.CENTER_VERTICAL)
        if (g.randRechts != Gesten.AUS && slSeite != Seitenleiste.RECHTS) leiste(Rand.RECHTS, g.randRechts, dick, (m.heightPixels * 0.6f).toInt(), Gravity.END or Gravity.CENTER_VERTICAL)
        zeigen(!launcherVorne)
        if (k.seitenleiste.an) griffZeigen(k.seitenleiste.seite == Seitenleiste.LINKS)
        if (leisten.isEmpty() && griff == null && vorhang == null) stopSelf()
    }

    private fun overlay(b: Int, h: Int, lage: Int, flags: Int = 0) = WindowManager.LayoutParams(
        b, h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or flags,
        PixelFormat.TRANSLUCENT
    ).apply { gravity = lage; title = "DammLauncher-Leiste" }

    private fun leiste(rand: Rand, aktion: String, b: Int, h: Int, lage: Int) {
        val v = Leiste(this, rand) { ausloesen(aktion) }
        try {
            wm?.addView(v, overlay(b, h, lage))
            leisten.add(v)
        } catch (_: Exception) {
            // Erlaubnis zurückgenommen o. Ä. – dann eben ohne diese Leiste
        }
    }

    private fun zeigen(an: Boolean) {
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

    // ------------------------------------------------------------ Seitenleiste

    private fun griffZeigen(links: Boolean) {
        val v = Griff(this, links) { leisteAuf(links) }
        try {
            wm?.addView(v, overlay(34.vp, 190.vp, (if (links) Gravity.START else Gravity.END) or Gravity.CENTER_VERTICAL))
            griff = v
        } catch (_: Exception) {
        }
    }

    private fun leisteZu() {
        entferne(leiste)
        leiste = null
        griff?.visibility = View.VISIBLE
    }

    private class Knopf(val zeichen: String, val name: String, val app: Element.App? = null, val aktion: () -> Unit)

    private fun knoepfe(): List<Knopf> {
        val liste = ArrayList<Knopf>()
        liste.add(Knopf("⌂", "Start") { ausloesen(Gesten.START) })
        if (SeitenleisteHilfe.an) {
            liste.add(Knopf("◀", "Zurück") { SeitenleisteHilfe.zurueck() })
            liste.add(Knopf("❐", "Übersicht") { SeitenleisteHilfe.uebersicht() })
        }
        liste.add(Knopf("▦", "Alle Apps") { ausloesen(Gesten.APPS) })
        if (startbar(DAMMBOARD)) liste.add(Knopf("✏", "Whiteboard") { starte(DAMMBOARD) })
        if (startbar(DAMMZEIT)) liste.add(Knopf("⏱", "Timer") { starte(DAMMZEIT) })
        liste.add(Knopf("▬", "Abdecken") { zeigeVorhang() })
        KonfigSpeicher.konfig.seitenleiste.apps.filter { AppKatalog.istDa(it.paket, it.aktivitaet) }.forEach { a ->
            liste.add(Knopf("", a.titel ?: AppKatalog.finde(a.paket, a.aktivitaet)?.name ?: a.paket, a) {
                AppKatalog.starte(ComponentName(a.paket, a.aktivitaet), null)
            })
        }
        return liste
    }

    private fun startbar(paket: String) = packageManager.getLaunchIntentForPackage(paket) != null

    private fun starte(paket: String) {
        try { packageManager.getLaunchIntentForPackage(paket)?.let { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } } catch (_: Exception) {}
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun leisteAuf(links: Boolean) {
        if (leiste != null) { leisteZu(); return }
        val spalte = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = runde(0xF01F2328.toInt(), 36f.vp)
            setPadding(14.vp, 18.vp, 14.vp, 18.vp)
            // Tippen außerhalb schließt die Leiste
            setOnTouchListener { _, ev -> if (ev.actionMasked == MotionEvent.ACTION_OUTSIDE) { leisteZu(); true } else false }
        }
        for (k in knoepfe()) {
            spalte.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = android.graphics.drawable.StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_pressed), runde(0x33FFFFFF, 24f.vp))
                    addState(intArrayOf(), runde(0x00000000, 24f.vp))
                }
                isClickable = true
                contentDescription = k.name
                setPadding(16.vp, 0, 22.vp, 0)
                if (k.app != null) {
                    val bild = ImageView(context)
                    AppKatalog.icon(ComponentName(k.app.paket, k.app.aktivitaet), k.app.icon, 60.vp) { b -> bild.setImageBitmap(b) }?.let { bild.setImageBitmap(it) }
                    addView(bild, LinearLayout.LayoutParams(60.vp, 60.vp))
                } else addView(TextView(context).apply {
                    text = k.zeichen
                    groesse(38)
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                }, LinearLayout.LayoutParams(60.vp, 60.vp))
                addView(TextView(context).apply {
                    text = k.name
                    groesse(26)
                    setTextColor(Color.WHITE)
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = 18.vp })
                setOnClickListener { leisteZu(); k.aktion() }
            }, LinearLayout.LayoutParams(300.vp, 96.vp))
        }
        val p = overlay(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            (if (links) Gravity.START else Gravity.END) or Gravity.CENTER_VERTICAL, WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH)
            .apply { x = 16.vp }
        try {
            wm?.addView(spalte, p)
            leiste = spalte
            griff?.visibility = View.GONE
        } catch (_: Exception) {
        }
    }

    // ------------------------------------------------------------ Abdecken

    private fun zeigeVorhang() {
        if (vorhang != null || !Settings.canDrawOverlays(this)) return
        val m = resources.displayMetrics
        val p = overlay(ViewGroup.LayoutParams.MATCH_PARENT, m.heightPixels, Gravity.TOP)
        val v = Vorhang(this, { neu -> p.height = neu.coerceIn(120.vp, m.heightPixels); try { wm?.updateViewLayout(vorhang, p) } catch (_: Exception) {} }) { vorhangWeg(); if (leisten.isEmpty() && griff == null) stopSelf() }
        try {
            wm?.addView(v, p)
            vorhang = v
        } catch (_: Exception) {
        }
    }

    private fun vorhangWeg() {
        entferne(vorhang)
        vorhang = null
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

    /** Griff der Seitenleiste: halbe Pille am Rand mit Pfeil; antippen oder zur Mitte wischen öffnet. */
    @SuppressLint("ViewConstructor")
    private class Griff(c: Context, private val links: Boolean, private val oeffnen: () -> Unit) : View(c) {
        private val flaeche = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xB31F2328.toInt() }
        private val pfeil = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textAlign = Paint.Align.CENTER }
        private val feld = RectF()
        private var x0 = 0f
        private var erledigt = false

        init { contentDescription = "Seitenleiste öffnen" }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            val r = w
            // halbe Pille: die Seite am Rand ragt über den Bildschirm hinaus
            if (links) feld.set(-r, 0f, w, h) else feld.set(0f, 0f, w + r, h)
            canvas.drawRoundRect(feld, r, r, flaeche)
            pfeil.textSize = w * 0.9f
            canvas.drawText(if (links) "›" else "‹", w / 2f, h / 2f + pfeil.textSize * 0.32f, pfeil)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> { x0 = ev.rawX; erledigt = false }
                MotionEvent.ACTION_MOVE -> if (!erledigt && abs(ev.rawX - x0) > 40f.vp) { erledigt = true; oeffnen() }
                MotionEvent.ACTION_UP -> if (!erledigt) { erledigt = true; oeffnen() }
            }
            return true
        }
    }

    /**
     * Vorhang zum Abdecken: schwarze Fläche von oben; der Griff an der Unterkante zieht sie hoch
     * oder runter (das Fenster selbst wird kleiner – darunter bleibt alles bedienbar), ✕ schließt.
     */
    @SuppressLint("ViewConstructor")
    private class Vorhang(c: Context, private val hoehe: (Int) -> Unit, private val zu: () -> Unit) : View(c) {
        private val flaeche = Paint().apply { color = 0xFA0B0F14.toInt() }
        private val griff = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3B4250.toInt() }
        private val schrift = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textAlign = Paint.Align.CENTER }
        private val knopf = RectF()
        private var ziehen = false
        private var y0 = 0f
        private var h0 = 0

        init { contentDescription = "Abdeckung – Griff unten ziehen, ✕ schließt" }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            canvas.drawRect(0f, 0f, w, h, flaeche)
            val gh = 90f.vp
            canvas.drawRoundRect(w / 2 - 220f.vp, h - gh, w / 2 + 220f.vp, h - 12f.vp, 30f.vp, 30f.vp, griff)
            schrift.textSize = 30f.vp
            canvas.drawText("⇕  ziehen", w / 2, h - gh / 2 + 4f.vp, schrift)
            knopf.set(w - 130f.vp, 30f.vp, w - 30f.vp, 130f.vp)
            canvas.drawRoundRect(knopf, 50f.vp, 50f.vp, griff)
            schrift.textSize = 52f.vp
            canvas.drawText("✕", knopf.centerX(), knopf.centerY() + 18f.vp, schrift)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (knopf.contains(ev.x, ev.y)) { zu(); return true }
                    ziehen = ev.y > height - 160f.vp
                    y0 = ev.rawY
                    h0 = height
                }
                MotionEvent.ACTION_MOVE -> if (ziehen) hoehe((h0 + ev.rawY - y0).toInt())
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> ziehen = false
            }
            return true
        }
    }

    companion object {
        private const val KANAL = "gesten"
        const val ABDECKEN = "de.oejendorferdamm.dammlauncher.ABDECKEN"
        const val DAMMBOARD = "de.oejendorferdamm.dammboard"
        const val DAMMZEIT = "de.oejendorferdamm.dammzeit"
        private var laufend: RandGesten? = null

        /** Ist der Startbildschirm vorne, stören die Wischleisten nur – dann ausblenden (die Seitenleiste bleibt). */
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
            val k = KonfigSpeicher.konfig
            val an = (k.gesten.randAn || k.seitenleiste.an) && erlaubt(c)
            try {
                when {
                    an && (laufend == null || neu) -> c.startForegroundService(Intent(c, RandGesten::class.java))
                    !an && laufend != null && laufend?.vorhang == null -> c.stopService(Intent(c, RandGesten::class.java))
                }
            } catch (_: Exception) {
                // z. B. Start aus dem Hintergrund verboten – beim nächsten Öffnen des Launchers klappt es
            }
        }

        /** Bildschirm abdecken (auch über anderen Apps). false = Erlaubnis fehlt. */
        fun abdecken(c: Context): Boolean {
            if (!erlaubt(c)) return false
            return try { c.startForegroundService(Intent(c, RandGesten::class.java).setAction(ABDECKEN)); true } catch (_: Exception) { false }
        }
    }
}
