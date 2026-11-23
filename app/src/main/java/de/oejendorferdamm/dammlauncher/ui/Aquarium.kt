/*
 * DammLauncher – Copyright (C) 2026 Teyro – GNU GPL v3 oder neuer, siehe LICENSE
 */
package de.oejendorferdamm.dammlauncher.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Leise-Aquarium: Ist die Klasse eine Weile leise, schwimmt ein neuer Fisch herein. Wird es zu laut,
 * kommt ein Hai und frisst einen. Ist das Aquarium voll, öffnet sich die Schatztruhe.
 * Lärm über das Mikrofon (nur der Pegel, nichts wird aufgenommen oder gespeichert); ohne Mikrofon
 * per Fingertipp auf „+ Fisch“ bzw. „Hai“. Volle Darstellung mit Lichtstrahlen, Blasen, wiegenden
 * Pflanzen und gestreiften Fischen – im Sparmodus schlicht und mit weniger Bildern pro Sekunde.
 */
@SuppressLint("ViewConstructor")
class AquariumAnsicht(context: Context, private val mitMikrofon: Boolean, private val onZu: () -> Unit) : View(context) {

    // ---------------------------------------------------------------- Einstellungen & Stand

    companion object {
        /** Stärke der Beschleunigung: der letzte Fisch kommt etwa 7-mal schneller als der erste */
        private const val A = 2.0

        /** Abstand (Sekunden Ruhe) vom Fisch [k] zum nächsten, wenn [n] Fische in [dauer] Sekunden kommen sollen: t(k) = D·ln(1 + (e^A − 1)·k/n)/A */
        fun abstand(k: Int, n: Int, dauer: Float): Float {
            fun t(i: Int) = dauer * kotlin.math.ln(1 + (kotlin.math.exp(A) - 1) * i.coerceIn(0, n) / n) / A
            return (t(k + 1) - t(k)).toFloat().coerceAtLeast(1f)
        }

        /** Minuten bis zum Ende der laufenden Stunde (Stundenzeiten des Launchers, sonst Raster der Schule); null = gerade keine Stunde */
        fun bisStundenende(jetzt: java.util.Calendar): Int? {
            val m = jetzt.get(java.util.Calendar.HOUR_OF_DAY) * 60 + jetzt.get(java.util.Calendar.MINUTE)
            val stunden = de.oejendorferdamm.dammlauncher.konfig.KonfigSpeicher.konfig.stunden.map { it.von to it.bis }
                .ifEmpty { listOf(510 to 555, 555 to 600, 625 to 670, 670 to 715, 740 to 785, 785 to 810) }
            return stunden.firstOrNull { m >= it.first && m < it.second }?.let { it.second - m }
        }
    }

    object Einstellung {
        private fun p(c: Context): SharedPreferences = c.applicationContext.getSharedPreferences("aquarium", Context.MODE_PRIVATE)
        fun ziel(c: Context) = p(c).getInt("ziel", 40)
        fun setzeZiel(c: Context, v: Int) = p(c).edit().putInt("ziel", v.coerceIn(10, 60)).apply()
        /** Sekunden Ruhe je Fisch */
        fun takt(c: Context) = p(c).getInt("takt", 20)
        fun setzeTakt(c: Context, v: Int) = p(c).edit().putInt("takt", v.coerceIn(5, 120)).apply()
        /** 1 = verzeiht viel, 10 = sehr streng */
        fun strenge(c: Context) = p(c).getInt("strenge", 5)
        fun setzeStrenge(c: Context, v: Int) = p(c).edit().putInt("strenge", v.coerceIn(1, 10)).apply()
        /** Dauer bis zur Schatztruhe (bei Ruhe): 0 = fester Takt je Fisch, 10/20 = Minuten, -1 = bis zum Ende der Stunde */
        fun dauer(c: Context) = p(c).getInt("dauer", -1)
        fun setzeDauer(c: Context, v: Int) = p(c).edit().putInt("dauer", v).apply()
        fun anzahl(c: Context) = p(c).getInt("anzahl", 3)
        fun setzeAnzahl(c: Context, v: Int) = p(c).edit().putInt("anzahl", v).apply()
    }

    private val ziel = Einstellung.ziel(context)
    private val takt = Einstellung.takt(context).toFloat()
    /** Mit Dauer: Sekunden bis zur Truhe (bei durchgehender Ruhe), sonst null = fester [takt] */
    private val dauerSek: Float? = Einstellung.dauer(context).let { d ->
        when {
            d > 0 -> d * 60f
            d == -1 -> (bisStundenende(java.util.Calendar.getInstance()) ?: 20) * 60f
            else -> null
        }?.coerceAtLeast(180f)
    }
    /** Ruhe, die der nächste Fisch braucht: mit Dauer anfangs lang, dann immer kürzer – nach [dauerSek] ist das Aquarium voll */
    private fun naechsterTakt(): Float = dauerSek?.let { abstand(anzahl, ziel, it) } ?: takt
    private val strenge = Einstellung.strenge(context)
    // Schwellen in „dB“ (grob, je Board verschieden): strenger = niedrigere Schwellen
    private val grenzeGelb = 66f - strenge * 2.2f
    private val grenzeRot = grenzeGelb + 9f

    private val spar = Leistung.sparsam

    // ---------------------------------------------------------------- Lebewesen

    private enum class Art(val name_: String, val groesse: Float, val tempo: Float) {
        CLOWN("Clownfisch", 1f, 1f), DOKTOR("Doktorfisch", 1.15f, 1.1f), FALTER("Falterfisch", 1.05f, 0.8f),
        NEON("Neonfisch", 0.6f, 1.5f), KUGEL("Kugelfisch", 0.95f, 0.6f), SCHILDKROETE("Schildkröte", 1.8f, 0.45f)
    }

    private class Fisch(val art: Art, var x: Float, var y: Float, var vx: Float, val phase: Float, val amp: Float, var richtung: Float = if (vx > 0) 1f else -1f, var gefressen: Float = -1f)
    private class Blase(var x: Float, var y: Float, val r: Float, val v: Float, val phase: Float)
    private class Schrift(val text: String, var x: Float, var y: Float, var alter: Float, val farbe: Int, val gross: Boolean)
    private class Muenze(var x: Float, var y: Float, var vx: Float, var vy: Float, var alter: Float)
    private class Hai(var x: Float, var y: Float, val vx: Float, var ziel: Fisch?, var hatGefressen: Boolean = false)

    private val fische = ArrayList<Fisch>()
    private val blasen = ArrayList<Blase>()
    private val schriften = ArrayList<Schrift>()
    private val muenzen = ArrayList<Muenze>()
    private var hai: Hai? = null
    // mit Dauer beginnt jede Runde leer, damit die Zeit stimmt
    private var anzahl = if (dauerSek != null) 0 else Einstellung.anzahl(context).coerceIn(0, ziel)

    // ---------------------------------------------------------------- Lärm

    @Volatile private var pegel = 0f       // geglättet, „dB“
    private var ruhe = 0f                  // Sekunden leise am Stück
    private var laut = 0f                  // Sekunden zu laut
    private var haiPause = 0f
    private var pausiert = false
    private var mikroFehler = false
    @Volatile private var hoeren = false
    private var hoerer: Thread? = null

    private fun mikrofonStarten() {
        if (!mitMikrofon || hoerer != null) return
        hoeren = true
        hoerer = Thread {
            val rate = 16000
            val puffer = max(AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT), 2048)
            val rec = try {
                @SuppressLint("MissingPermission")
                val r = AudioRecord(MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, puffer)
                if (r.state != AudioRecord.STATE_INITIALIZED) { r.release(); null } else r
            } catch (_: Exception) { null }
            if (rec == null) { mikroFehler = true; return@Thread }
            val daten = ShortArray(puffer / 2)
            try {
                rec.startRecording()
                while (hoeren) {
                    val n = rec.read(daten, 0, daten.size)
                    if (n <= 0) continue
                    var summe = 0.0
                    for (i in 0 until n) { val v = daten[i].toDouble(); summe += v * v }
                    val rms = sqrt(summe / n).coerceAtLeast(1.0)
                    val db = (20 * log10(rms) + 10).toFloat()   // ~ 30 (Stille) … 100 (Schreien)
                    // schnell hoch, langsam runter – wie eine Lärmampel
                    pegel = if (db > pegel) pegel * 0.6f + db * 0.4f else pegel * 0.93f + db * 0.07f
                }
            } catch (_: Exception) { mikroFehler = true }
            try { rec.stop() } catch (_: Exception) {}
            rec.release()
        }.apply { isDaemon = true; start() }
    }

    private fun mikrofonStoppen() { hoeren = false; hoerer = null }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); mikrofonStarten(); zuletzt = 0L; invalidate() }
    override fun onDetachedFromWindow() { mikrofonStoppen(); Einstellung.setzeAnzahl(context, anzahl); super.onDetachedFromWindow() }

    // ---------------------------------------------------------------- Zeichenwerkzeug (einmal angelegt)

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strich = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val schrift = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); textAlign = Paint.Align.CENTER }
    private val pfad = Path()
    private val oval = RectF()
    private var wasser: Shader? = null
    private var sandShader: Shader? = null
    private var s = 1f      // Maßstab: 1 = 1080 px Höhe
    private var zeit = 0f
    private var zuletzt = 0L

    private data class Pflanze(val x: Float, val hoehe: Float, val farbe: Int, val phase: Float, val dicke: Float)
    private val pflanzen = ArrayList<Pflanze>()
    private val knoepfe = HashMap<String, RectF>()

    init {
        isClickable = true
        isFocusable = true
        contentDescription = "Leise-Aquarium"
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        s = min(w, h) / 1080f
        wasser = LinearGradient(0f, 0f, 0f, h.toFloat(), intArrayOf(0xFF5CC8F0.toInt(), 0xFF2595D4.toInt(), 0xFF0E5C9E.toInt()), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        sandShader = LinearGradient(0f, h * 0.84f, 0f, h.toFloat(), 0xFFF3DCA6.toInt(), 0xFFD8B877.toInt(), Shader.TileMode.CLAMP)
        pflanzen.clear()
        val farben = intArrayOf(0xFF2FA84F.toInt(), 0xFF46C46A.toInt(), 0xFF1E8C45.toInt(), 0xFF79D27A.toInt())
        val n = if (spar) 8 else 16
        repeat(n) { i ->
            val x = w * (0.03f + 0.94f * i / (n - 1)) + (Random.nextFloat() - 0.5f) * 40 * s
            // in der Mitte niedriger, damit die Fische gut zu sehen sind
            val mitte = 1f - 0.55f * (1f - abs(x / w - 0.5f) * 2f)
            pflanzen.add(Pflanze(x, h * (0.22f + Random.nextFloat() * 0.2f) * mitte, farben[i % farben.size], Random.nextFloat() * 6.28f, (14f + Random.nextFloat() * 10f) * s))
        }
        if (fische.isEmpty()) repeat(anzahl) { fische.add(neuerFisch(w, h, it, rein = false)) }
    }

    private fun neuerFisch(w: Int, h: Int, nr: Int, rein: Boolean): Fisch {
        val art = if ((nr + 1) % 10 == 0) Art.SCHILDKROETE else listOf(Art.CLOWN, Art.DOKTOR, Art.FALTER, Art.NEON, Art.KUGEL, Art.NEON, Art.CLOWN)[Random.nextInt(7)]
        val links = Random.nextBoolean()
        val v = (1.6f + Random.nextFloat() * 1.4f) * art.tempo * s * (if (links) 1 else -1)
        val x = if (rein) (if (links) -120f * s else w + 120f * s) else Random.nextFloat() * w
        return Fisch(art, x, h * (0.16f + Random.nextFloat() * 0.6f), v, Random.nextFloat() * 6.28f, (8f + Random.nextFloat() * 18f) * s)
    }

    // ---------------------------------------------------------------- Ablauf

    private fun fischDazu(manuell: Boolean = false) {
        if (anzahl >= ziel) return
        anzahl++
        val f = neuerFisch(width, height, anzahl - 1, rein = true)
        fische.add(f)
        schriften.add(Schrift("+1 ${f.art.name_}", width * 0.5f, height * 0.3f, 0f, Color.WHITE, true))
        if (anzahl >= ziel) schatz()
        Einstellung.setzeAnzahl(context, anzahl)
        if (!manuell) ruhe = 0f
    }

    private fun haiKommt() {
        if (hai != null || anzahl <= 0) return
        val links = Random.nextBoolean()
        val opfer = fische.filter { it.gefressen < 0 }.maxByOrNull { if (links) -it.x else it.x } ?: fische.lastOrNull()
        hai = Hai(if (links) -500f * s else width + 500f * s, opfer?.y ?: (height * 0.4f), (if (links) 1 else -1) * 13f * s, opfer)
        schriften.add(Schrift("Achtung, Hai!", width * 0.5f, height * 0.22f, 0f, 0xFFFFE0E0.toInt(), true))
        laut = 0f; ruhe = 0f; haiPause = 10f
    }

    private fun schatz() {
        repeat(if (spar) 25 else 70) {
            muenzen.add(Muenze(width * 0.5f, height * 0.86f, (Random.nextFloat() - 0.5f) * 22 * s, -(10f + Random.nextFloat() * 16f) * s, 0f))
        }
        schriften.add(Schrift("Super leise!", width * 0.5f, height * 0.4f, 0f, 0xFFFFE27A.toInt(), true))
    }

    private fun schritt(dt: Float) {
        zeit += dt / 60f
        val sek = dt / 60f
        if (!pausiert) {
            // Lärm auswerten
            if (mitMikrofon && !mikroFehler && anzahl < ziel) {
                when {
                    pegel >= grenzeRot -> { laut += sek; if (laut > 1.5f && haiPause <= 0f) haiKommt() }
                    pegel >= grenzeGelb -> { laut = max(0f, laut - sek) }
                    else -> { laut = max(0f, laut - sek * 2); ruhe += sek; if (ruhe >= naechsterTakt()) fischDazu() }
                }
            }
            haiPause = max(0f, haiPause - sek)
        }
        // Fische schwimmen, wenden weich an den Rändern
        val w = width.toFloat()
        val it = fische.iterator()
        while (it.hasNext()) {
            val f = it.next()
            if (f.gefressen >= 0f) { f.gefressen += sek; if (f.gefressen > 0.4f) it.remove(); continue }
            f.x += f.vx * dt
            val rand = 90f * s * f.art.groesse
            if ((f.x > w - rand && f.vx > 0) || (f.x < rand && f.vx < 0)) f.vx = -f.vx
            val soll = if (f.vx > 0) 1f else -1f
            f.richtung += (soll - f.richtung) * min(1f, 0.08f * dt)
            // vor dem Hai fliehen
            hai?.let { h -> if (abs(h.x - f.x) < 400 * s && f != h.ziel) f.y += (if (f.y < h.y) -1.5f else 1.5f) * s * dt }
            f.y = f.y.coerceIn(height * 0.12f, height * 0.8f)
        }
        // Hai: schwimmt quer durch, schnappt sich sein Ziel
        hai?.let { h ->
            h.x += h.vx * dt
            h.ziel?.let { z -> if (z.gefressen < 0) h.y += (z.y - h.y) * 0.05f * dt }
            val z = h.ziel
            if (!h.hatGefressen && z != null && abs((h.x + h.vx.compareTo(0) * 160 * s) - z.x) < 70 * s) {
                z.gefressen = 0f; h.hatGefressen = true
                anzahl = max(0, anzahl - 1); Einstellung.setzeAnzahl(context, anzahl)
                schriften.add(Schrift("Happs! −1", z.x, z.y - 60 * s, 0f, 0xFFFFC2C2.toInt(), true))
                if (!spar) repeat(10) { blasen.add(Blase(z.x, z.y, (5f + Random.nextFloat() * 8f) * s, (2f + Random.nextFloat() * 2f) * s, Random.nextFloat() * 6f)) }
            }
            if (h.x < -700 * s || h.x > w + 700 * s) hai = null
        }
        // Blasen
        if (!spar && Random.nextFloat() < 0.08f * dt) blasen.add(Blase(Random.nextFloat() * w, height * 0.9f, (4f + Random.nextFloat() * 9f) * s, (1.2f + Random.nextFloat() * 1.8f) * s, Random.nextFloat() * 6f))
        blasen.removeAll { b -> b.y -= b.v * dt; b.y < -20 }
        schriften.removeAll { t -> t.alter += sek; t.y -= 0.6f * s * dt; t.alter > 2.2f }
        muenzen.removeAll { m -> m.alter += sek; m.vy += 0.5f * s * dt; m.x += m.vx * dt; m.y += m.vy * dt; m.alter > 3f }
    }

    // ---------------------------------------------------------------- Zeichnen

    override fun onDraw(c: Canvas) {
        val jetzt = SystemClock.uptimeMillis()
        val dt = if (zuletzt == 0L) 1f else ((jetzt - zuletzt) / 16.7f).coerceIn(0.2f, 4f)
        zuletzt = jetzt
        schritt(dt)
        val w = width.toFloat(); val h = height.toFloat()

        // Wasser
        // deckend: der Pinsel trägt sonst die Durchsichtigkeit der Blasen vom letzten Bild mit
        p.color = Color.WHITE
        if (spar) { c.drawColor(0xFF1F86C6.toInt()) } else { p.shader = wasser; c.drawRect(0f, 0f, w, h, p); p.shader = null }
        if (!spar) lichtstrahlen(c, w, h)
        sand(c, w, h)
        for (pf in pflanzen) pflanze(c, pf, h)
        korallen(c, w, h)
        truhe(c, w * 0.5f, h * 0.87f)
        for (f in fische) fisch(c, f)
        hai?.let { haiZeichnen(c, it) }
        if (!spar) for (b in blasen) blase(c, b)
        for (m in muenzen) { p.color = 0xFFFFD34D.toInt(); c.drawCircle(m.x, m.y, 9 * s, p); p.color = 0xFFFFF1A8.toInt(); c.drawCircle(m.x - 3 * s, m.y - 3 * s, 3 * s, p) }
        for (t in schriften) texte(c, t)
        anzeige(c, w, h)

        if (spar) postInvalidateDelayed(50) else postInvalidateOnAnimation()
    }

    private fun lichtstrahlen(c: Canvas, w: Float, h: Float) {
        p.color = Color.WHITE
        for (i in 0 until 5) {
            val x = w * (0.1f + i * 0.2f) + sin(zeit * 0.3f + i) * 40 * s
            p.alpha = (18 + 12 * sin(zeit * 0.5f + i * 1.7f)).toInt().coerceIn(4, 40)
            pfad.reset(); pfad.moveTo(x - 30 * s, 0f); pfad.lineTo(x + 50 * s, 0f); pfad.lineTo(x + 260 * s, h * 0.85f); pfad.lineTo(x + 60 * s, h * 0.85f); pfad.close()
            c.drawPath(pfad, p)
        }
        p.alpha = 255
    }

    private fun sand(c: Canvas, w: Float, h: Float) {
        pfad.reset(); pfad.moveTo(0f, h)
        pfad.lineTo(0f, h * 0.86f)
        var x = 0f
        while (x <= w) { pfad.lineTo(x, h * 0.86f + sin(x / (180 * s)) * 12 * s); x += 20 * s }
        pfad.lineTo(w, h); pfad.close()
        p.color = if (spar) 0xFFE6C98C.toInt() else Color.WHITE
        if (!spar) p.shader = sandShader
        c.drawPath(pfad, p); p.shader = null
        if (!spar) {
            // Seestern und Muscheln
            stern(c, w * 0.12f, h * 0.94f, 26 * s, 0xFFF0715A.toInt())
            stern(c, w * 0.81f, h * 0.95f, 20 * s, 0xFFF59E0B.toInt())
            p.color = 0xFFFFF4E0.toInt(); oval.set(w * 0.3f, h * 0.93f, w * 0.3f + 34 * s, h * 0.93f + 22 * s); c.drawArc(oval, 180f, 180f, true, p)
        }
    }

    private fun stern(c: Canvas, x: Float, y: Float, r: Float, farbe: Int) {
        pfad.reset()
        for (i in 0 until 10) {
            val a = -Math.PI / 2 + i * Math.PI / 5
            val rr = if (i % 2 == 0) r else r * 0.45f
            val px = x + (rr * kotlin.math.cos(a)).toFloat(); val py = y + (rr * sin(a)).toFloat()
            if (i == 0) pfad.moveTo(px, py) else pfad.lineTo(px, py)
        }
        pfad.close(); p.color = farbe; c.drawPath(pfad, p)
    }

    private fun pflanze(c: Canvas, pf: Pflanze, h: Float) {
        val boden = h * 0.9f
        val wiegen = if (spar) 0f else sin(zeit * 1.1f + pf.phase) * 26 * s
        strich.color = pf.farbe; strich.strokeWidth = pf.dicke
        pfad.reset(); pfad.moveTo(pf.x, boden)
        pfad.cubicTo(pf.x - 30 * s + wiegen * 0.3f, boden - pf.hoehe * 0.35f, pf.x + 30 * s + wiegen * 0.7f, boden - pf.hoehe * 0.7f, pf.x + wiegen, boden - pf.hoehe)
        c.drawPath(pfad, strich)
        if (!spar) {
            // Blätter
            strich.strokeWidth = pf.dicke * 0.55f
            for (k in 1..3) {
                val t = k / 4f
                val bx = pf.x + wiegen * t; val by = boden - pf.hoehe * t
                val seite = if (k % 2 == 0) 1 else -1
                pfad.reset(); pfad.moveTo(bx, by); pfad.quadTo(bx + seite * 40 * s, by - 30 * s, bx + seite * 55 * s + wiegen * 0.2f, by - 70 * s)
                c.drawPath(pfad, strich)
            }
        }
    }

    private fun korallen(c: Canvas, w: Float, h: Float) {
        val boden = h * 0.9f
        // Röhrenkorallen (lila/pink)
        p.color = 0xFFC2399E.toInt()
        for ((i, hx) in listOf(0.2f, 0.225f, 0.25f).withIndex()) {
            val hh = (110f + i * 45f) * s
            oval.set(w * hx, boden - hh, w * hx + 34 * s, boden); c.drawRoundRect(oval, 17 * s, 17 * s, p)
            p.color = 0xFF8E1F72.toInt(); c.drawOval(w * hx + 4 * s, boden - hh, w * hx + 30 * s, boden - hh + 14 * s, p); p.color = 0xFFC2399E.toInt()
        }
        // Verzweigte Koralle (orange)
        strich.color = 0xFFF2804B.toInt(); strich.strokeWidth = 14 * s
        astKoralle(c, w * 0.68f, boden, 150 * s, -90f, 3)
        strich.color = 0xFFE85C8A.toInt()
        astKoralle(c, w * 0.88f, boden, 110 * s, -90f, 3)
        // Anemone
        p.color = 0xFF9B5DE5.toInt()
        for (i in 0 until if (spar) 6 else 12) {
            val a = Math.toRadians((-170 + i * (160.0 / (if (spar) 5 else 11))))
            val wellen = if (spar) 0f else sin(zeit * 2 + i) * 4 * s
            c.drawCircle(w * 0.36f + (60 * s * kotlin.math.cos(a)).toFloat() + wellen, boden - 10 * s + (50 * s * sin(a)).toFloat(), 13 * s, p)
        }
    }

    private fun astKoralle(c: Canvas, x: Float, y: Float, laenge: Float, winkel: Float, tiefe: Int) {
        if (tiefe == 0) return
        val r = Math.toRadians(winkel.toDouble())
        val ex = x + (laenge * kotlin.math.cos(r)).toFloat(); val ey = y + (laenge * sin(r)).toFloat()
        strich.strokeWidth = (5f + tiefe * 4f) * s
        c.drawLine(x, y, ex, ey, strich)
        astKoralle(c, ex, ey, laenge * 0.62f, winkel - 28f, tiefe - 1)
        astKoralle(c, ex, ey, laenge * 0.62f, winkel + 30f, tiefe - 1)
    }

    private fun truhe(c: Canvas, x: Float, y: Float) {
        val b = 190 * s; val hh = 110 * s
        val offen = anzahl >= ziel
        // Kiste
        p.color = 0xFF8A5A2B.toInt(); oval.set(x - b / 2, y - hh, x + b / 2, y); c.drawRoundRect(oval, 10 * s, 10 * s, p)
        p.color = 0xFF6B4320.toInt(); c.drawRect(x - b / 2, y - hh * 0.55f, x + b / 2, y - hh * 0.45f, p)
        p.color = 0xFFE8B931.toInt(); c.drawRect(x - 14 * s, y - hh * 0.7f, x + 14 * s, y - hh * 0.3f, p)
        // Deckel (öffnet sich, wenn das Aquarium voll ist)
        c.save()
        c.rotate(if (offen) -35f else 0f, x - b / 2, y - hh)
        p.color = 0xFF9C6A35.toInt(); oval.set(x - b / 2 - 6 * s, y - hh - 55 * s, x + b / 2 + 6 * s, y - hh + 4 * s); c.drawRoundRect(oval, 40 * s, 40 * s, p)
        p.color = 0xFFE8B931.toInt(); c.drawRect(x - b / 2 - 6 * s, y - hh - 6 * s, x + b / 2 + 6 * s, y - hh + 4 * s, p)
        c.restore()
        if (offen) {
            // Goldschimmer
            if (!spar) { p.shader = RadialGradient(x, y - hh, b, 0x88FFE27A.toInt(), 0x00FFE27A, Shader.TileMode.CLAMP); c.drawCircle(x, y - hh, b, p); p.shader = null }
            p.color = 0xFFFFD34D.toInt()
            for (i in 0 until 9) c.drawCircle(x - b * 0.35f + i * b * 0.09f, y - hh - 6 * s - (i % 3) * 9 * s, 13 * s, p)
        }
    }

    private fun blase(c: Canvas, b: Blase) {
        strich.color = 0x99FFFFFF.toInt(); strich.strokeWidth = 2.5f * s
        val x = b.x + sin(zeit * 3 + b.phase) * 6 * s
        c.drawCircle(x, b.y, b.r, strich)
        p.color = 0x66FFFFFF; c.drawCircle(x - b.r * 0.35f, b.y - b.r * 0.35f, b.r * 0.25f, p)
    }

    private fun texte(c: Canvas, t: Schrift) {
        val a = (if (t.alter < 0.2f) t.alter / 0.2f else 1f - max(0f, t.alter - 1.4f) / 0.8f).coerceIn(0f, 1f)
        schrift.textSize = (if (t.gross) 64 else 40) * s
        schrift.color = 0x55000000; schrift.alpha = (a * 90).toInt(); c.drawText(t.text, t.x + 3 * s, t.y + 4 * s, schrift)
        schrift.color = t.farbe; schrift.alpha = (a * 255).toInt(); c.drawText(t.text, t.x, t.y, schrift)
        schrift.alpha = 255
    }

    // ---------------------------------------------------------------- Fische

    private fun fisch(c: Canvas, f: Fisch) {
        val g = 70 * s * f.art.groesse * (if (f.gefressen >= 0) 1f - f.gefressen * 2.5f else 1f)
        if (g <= 0) return
        val y = f.y + sin(zeit * 1.8f + f.phase) * f.amp
        val schwanz = sin(zeit * 10f * f.art.tempo + f.phase) * (if (spar) 0.15f else 0.35f)
        c.save()
        c.translate(f.x, y)
        c.scale(f.richtung.coerceIn(-1f, 1f).let { if (abs(it) < 0.1f) 0.1f * it.compareTo(0) else it } , 1f)
        c.scale(g, g)
        when (f.art) {
            Art.CLOWN -> koerper(c, 0xFFFF7A1A.toInt(), 1f, 0.55f, schwanz) { streifen(c, listOf(-0.35f, 0.05f, 0.45f), 0.13f, 0.55f, Color.WHITE, Color.BLACK) }
            Art.DOKTOR -> koerper(c, 0xFF2563EB.toInt(), 1.05f, 0.6f, schwanz, schwanzFarbe = 0xFFFACC15.toInt()) {
                if (!spar) { p.color = 0xFF0F1E5A.toInt(); pfad.reset(); pfad.moveTo(-0.3f, -0.25f); pfad.quadTo(0.2f, -0.45f, 0.5f, -0.1f); pfad.quadTo(0.1f, -0.05f, -0.3f, 0.1f); pfad.close(); c.drawPath(pfad, p) }
            }
            Art.FALTER -> koerper(c, 0xFFFACC15.toInt(), 0.9f, 0.85f, schwanz) { streifen(c, listOf(-0.25f, 0.1f), 0.08f, 0.8f, 0xFF1F2937.toInt(), 0) }
            Art.NEON -> koerper(c, 0xFFB8C7D9.toInt(), 1.2f, 0.32f, schwanz, schwanzFarbe = 0xFFE5E7EB.toInt()) {
                p.color = 0xFF22D3EE.toInt(); c.drawRect(-0.9f, -0.1f, 0.75f, 0.0f, p)
                p.color = 0xFFEF4444.toInt(); c.drawRect(-0.9f, 0.0f, -0.05f, 0.12f, p)
            }
            Art.KUGEL -> koerper(c, 0xFFFCD34D.toInt(), 0.85f, 0.8f, schwanz) {
                if (!spar) { p.color = 0xFF92400E.toInt(); for ((dx, dy) in listOf(-0.3f to -0.3f, 0f to -0.45f, 0.25f to -0.2f, -0.1f to 0.0f, -0.45f to 0.05f)) c.drawCircle(dx, dy, 0.07f, p) }
                p.color = 0xFFFFF7D6.toInt(); oval.set(-0.6f, 0.1f, 0.6f, 0.75f); c.drawArc(oval, 0f, 180f, false, p)
            }
            Art.SCHILDKROETE -> schildkroete(c, schwanz)
        }
        c.restore()
    }

    /** Fischkörper (Ellipse), Schwanz, Flosse, Auge; [muster] zeichnet zusätzlich in Körperkoordinaten */
    private inline fun koerper(c: Canvas, farbe: Int, laenge: Float, hoehe: Float, schwanz: Float, schwanzFarbe: Int = farbe, muster: () -> Unit) {
        // Schwanzflosse (wedelt)
        c.save(); c.rotate(Math.toDegrees(schwanz.toDouble()).toFloat(), -laenge * 0.85f, 0f)
        p.color = if (spar) schwanzFarbe else dunkler(schwanzFarbe)
        pfad.reset(); pfad.moveTo(-laenge * 0.8f, 0f); pfad.lineTo(-laenge * 1.35f, -hoehe * 0.65f); pfad.quadTo(-laenge * 1.2f, 0f, -laenge * 1.35f, hoehe * 0.65f); pfad.close()
        c.drawPath(pfad, p); c.restore()
        // Rückenflosse
        p.color = dunkler(farbe)
        pfad.reset(); pfad.moveTo(-laenge * 0.4f, -hoehe * 0.8f); pfad.quadTo(0f, -hoehe * 1.35f, laenge * 0.35f, -hoehe * 0.8f); pfad.close(); c.drawPath(pfad, p)
        // Körper
        p.color = farbe; oval.set(-laenge, -hoehe, laenge, hoehe); c.drawOval(oval, p)
        c.save(); pfad.reset(); pfad.addOval(oval, Path.Direction.CW); c.clipPath(pfad)
        muster()
        if (!spar) { p.color = 0x22FFFFFF; oval.set(-laenge * 0.8f, -hoehe * 0.9f, laenge * 0.9f, -hoehe * 0.1f); c.drawOval(oval, p) }
        c.restore()
        // Brustflosse
        if (!spar) { p.color = 0x55FFFFFF; c.save(); c.rotate(20f + Math.toDegrees(schwanz.toDouble()).toFloat() * 0.5f, laenge * 0.1f, hoehe * 0.2f); oval.set(laenge * 0.0f, hoehe * 0.1f, laenge * 0.35f, hoehe * 0.45f); c.drawOval(oval, p); c.restore() }
        // Auge
        p.color = Color.WHITE; c.drawCircle(laenge * 0.62f, -hoehe * 0.2f, hoehe * 0.24f, p)
        p.color = 0xFF111827.toInt(); c.drawCircle(laenge * 0.67f, -hoehe * 0.2f, hoehe * 0.13f, p)
        if (!spar) { p.color = Color.WHITE; c.drawCircle(laenge * 0.7f, -hoehe * 0.25f, hoehe * 0.05f, p) }
    }

    private fun streifen(c: Canvas, lagen: List<Float>, breite: Float, hoehe: Float, farbe: Int, rand: Int) {
        for (x in lagen) {
            if (rand != 0 && !spar) { p.color = rand; c.drawRect(x - breite - 0.03f, -hoehe, x + breite + 0.03f, hoehe, p) }
            p.color = farbe; c.drawRect(x - breite, -hoehe, x + breite, hoehe, p)
        }
    }

    private fun schildkroete(c: Canvas, schwanz: Float) {
        val paddel = sin(zeit * 3f) * 25f
        p.color = 0xFF65A30D.toInt()
        for ((x, y, w) in listOf(Triple(0.35f, 0.35f, paddel), Triple(-0.35f, 0.35f, -paddel), Triple(0.35f, -0.35f, -paddel), Triple(-0.35f, -0.35f, paddel))) {
            c.save(); c.rotate(w, x, y); oval.set(x - 0.18f, y - 0.1f + (if (y > 0) 0.15f else -0.15f), x + 0.25f, y + 0.1f + (if (y > 0) 0.15f else -0.15f)); c.drawOval(oval, p); c.restore()
        }
        oval.set(0.55f, -0.18f, 0.95f, 0.16f); c.drawOval(oval, p)       // Kopf
        p.color = 0xFF3F6212.toInt(); oval.set(-0.6f, -0.45f, 0.6f, 0.45f); c.drawOval(oval, p)   // Panzer
        if (!spar) {
            p.color = 0xFF84CC16.toInt()
            for ((x, y) in listOf(0f to 0f, -0.28f to -0.15f, 0.28f to -0.15f, -0.28f to 0.15f, 0.28f to 0.15f)) c.drawCircle(x, y, 0.12f, p)
        }
        p.color = 0xFF111827.toInt(); c.drawCircle(0.82f, -0.05f, 0.04f, p)
    }

    private fun haiZeichnen(c: Canvas, h: Hai) {
        val g = 150 * s
        val schwanz = sin(zeit * 9f) * 0.25f
        c.save(); c.translate(h.x, h.y); c.scale(if (h.vx > 0) g else -g, g)
        // Schwanz
        c.save(); c.rotate(Math.toDegrees(schwanz.toDouble()).toFloat(), -1.1f, 0f)
        p.color = 0xFF64748B.toInt(); pfad.reset(); pfad.moveTo(-1.0f, 0f); pfad.lineTo(-1.6f, -0.65f); pfad.lineTo(-1.4f, 0f); pfad.lineTo(-1.55f, 0.45f); pfad.close(); c.drawPath(pfad, p)
        c.restore()
        // Rückenflosse
        pfad.reset(); pfad.moveTo(-0.3f, -0.3f); pfad.lineTo(0.05f, -0.95f); pfad.lineTo(0.3f, -0.3f); pfad.close(); c.drawPath(pfad, p)
        // Körper
        pfad.reset(); pfad.moveTo(1.25f, 0.02f); pfad.quadTo(0.9f, -0.45f, 0f, -0.38f); pfad.quadTo(-0.9f, -0.3f, -1.15f, 0f); pfad.quadTo(-0.9f, 0.3f, 0f, 0.36f); pfad.quadTo(0.9f, 0.4f, 1.25f, 0.02f); pfad.close()
        p.color = 0xFF7C8BA1.toInt(); c.drawPath(pfad, p)
        p.color = 0xFFE2E8F0.toInt(); oval.set(-0.6f, 0.02f, 1.05f, 0.36f); c.drawOval(oval, p)   // Bauch
        // Brustflosse
        p.color = 0xFF64748B.toInt(); pfad.reset(); pfad.moveTo(0.2f, 0.2f); pfad.lineTo(-0.15f, 0.7f); pfad.lineTo(0.45f, 0.25f); pfad.close(); c.drawPath(pfad, p)
        // Kiemen, Auge, Maul mit Zähnen
        strich.color = 0xFF475569.toInt(); strich.strokeWidth = 0.025f
        for (i in 0 until 3) c.drawLine(0.45f - i * 0.08f, -0.12f, 0.42f - i * 0.08f, 0.12f, strich)
        p.color = 0xFF0F172A.toInt(); c.drawCircle(0.85f, -0.12f, 0.055f, p)
        p.color = Color.WHITE; c.drawCircle(0.865f, -0.135f, 0.018f, p)
        strich.color = 0xFF334155.toInt(); strich.strokeWidth = 0.035f
        pfad.reset(); pfad.moveTo(0.7f, 0.14f); pfad.quadTo(0.95f, 0.22f, 1.15f, 0.1f); c.drawPath(pfad, strich)
        if (!spar) { p.color = Color.WHITE; for (i in 0 until 5) { val x = 0.75f + i * 0.08f; pfad.reset(); pfad.moveTo(x, 0.15f); pfad.lineTo(x + 0.03f, 0.22f); pfad.lineTo(x + 0.06f, 0.15f); pfad.close(); c.drawPath(pfad, p) } }
        c.restore()
    }

    // ---------------------------------------------------------------- Anzeige: Zähler, Ampel, Hinweis, Knöpfe

    private fun anzeige(c: Canvas, w: Float, h: Float) {
        // Zähler oben links
        schrift.textAlign = Paint.Align.LEFT
        schrift.textSize = 130 * s
        val zahl = anzahl.toString()
        schrift.color = 0x55000000; c.drawText(zahl, 44 * s, 150 * s, schrift)
        schrift.color = Color.WHITE; c.drawText(zahl, 40 * s, 145 * s, schrift)
        val zb = schrift.measureText(zahl)
        schrift.textSize = 44 * s; c.drawText(if (anzahl == 1) "Fisch" else "Fische", 56 * s + zb, 140 * s, schrift)
        schrift.textSize = 32 * s; schrift.color = 0xCCFFFFFF.toInt(); c.drawText("Ziel: $ziel", 46 * s, 190 * s, schrift)
        // Fortschritt
        p.color = 0x44FFFFFF; oval.set(44 * s, 208 * s, 344 * s, 222 * s); c.drawRoundRect(oval, 7 * s, 7 * s, p)
        p.color = 0xFFFFD34D.toInt(); oval.set(44 * s, 208 * s, 44 * s + 300 * s * anzahl / ziel, 222 * s); c.drawRoundRect(oval, 7 * s, 7 * s, p)
        schrift.textAlign = Paint.Align.CENTER

        // Ampel oben rechts
        val stufe = when { !mitMikrofon || mikroFehler -> -1; pegel >= grenzeRot -> 2; pegel >= grenzeGelb -> 1; else -> 0 }
        val ax = w - 250 * s; val ay = 40 * s
        p.color = 0xCC1F2937.toInt(); oval.set(ax, ay, ax + 210 * s, ay + 76 * s); c.drawRoundRect(oval, 38 * s, 38 * s, p)
        val farben = intArrayOf(0xFF22C55E.toInt(), 0xFFFACC15.toInt(), 0xFFEF4444.toInt())
        for (i in 0..2) {
            val cx = ax + 40 * s + i * 65 * s; val cy = ay + 38 * s
            val aktiv = when (i) { 0 -> stufe == 2; 1 -> stufe == 1; else -> stufe == 0 }
            p.color = if (aktiv) farben[2 - i] else (farben[2 - i] and 0x00FFFFFF) or 0x40000000
            if (aktiv && !spar) { p.setShadowLayer(18 * s, 0f, 0f, farben[2 - i]) }
            c.drawCircle(cx, cy, 24 * s, p)
            p.clearShadowLayer()
        }

        // Hinweis unten
        val text = when {
            anzahl >= ziel -> "Das Aquarium ist voll – ihr habt den Schatz gefunden!"
            hai != null -> "Zu laut – Achtung, es kommt ein Hai!"
            pausiert -> "Pause"
            !mitMikrofon || mikroFehler -> "Ohne Mikrofon: unten „+ Fisch“ oder „Hai“ tippen."
            stufe == 2 -> "Zu laut – gleich kommt der Hai!"
            stufe == 1 -> "Etwas leiser, bitte …"
            else -> "Schön leise – gleich kommt ein Fisch."
        }
        schrift.textSize = 34 * s
        val tb = schrift.measureText(text) + 80 * s
        val ty = h - 70 * s
        p.color = 0xEEFFFFFF.toInt(); oval.set(w / 2 - tb / 2, ty - 50 * s, w / 2 + tb / 2, ty + 22 * s); c.drawRoundRect(oval, 36 * s, 36 * s, p)
        schrift.color = 0xFF1F2937.toInt(); c.drawText(text, w / 2, ty - 2 * s, schrift)
        // Fortschritt zum nächsten Fisch
        if (mitMikrofon && !mikroFehler && anzahl < ziel && hai == null) {
            p.color = 0xFF22C55E.toInt(); val breite = (tb - 80 * s) * (ruhe / naechsterTakt()).coerceIn(0f, 1f)
            oval.set(w / 2 - tb / 2 + 40 * s, ty + 10 * s, w / 2 - tb / 2 + 40 * s + breite, ty + 16 * s); c.drawRoundRect(oval, 3 * s, 3 * s, p)
        }

        // Knöpfe: rechts Schließen, Neu, Pause – links „+ Fisch“ und „Hai“ (von Hand, auch ohne Mikrofon)
        val r = 44 * s
        val ky = h - 82 * s
        fun knopf(name: String, symbol: String, x: Float) {
            p.color = 0x66000000; c.drawCircle(x, ky, r, p)
            schrift.color = Color.WHITE; schrift.textSize = (if (symbol.length > 1) 30 else 38) * s
            c.drawText(symbol, x, ky + schrift.textSize * 0.35f, schrift)
            knoepfe[name] = RectF(x - r, ky - r, x + r, ky + r)
        }
        listOf("zu" to "✕", "neu" to "↺", "pause" to (if (pausiert) "▶" else "❚❚")).forEachIndexed { i, (n, sy) -> knopf(n, sy, w - 70 * s - i * 2.3f * r) }
        listOf("fisch" to "+🐟", "hai" to "🦈").forEachIndexed { i, (n, sy) -> knopf(n, sy, 70 * s + i * 2.3f * r) }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked != MotionEvent.ACTION_UP) return true
        val name = knoepfe.entries.firstOrNull { it.value.contains(e.x, e.y) }?.key ?: return true
        when (name) {
            "zu" -> onZu()
            "neu" -> { anzahl = 0; fische.clear(); hai = null; ruhe = 0f; muenzen.clear(); Einstellung.setzeAnzahl(context, 0) }
            "pause" -> pausiert = !pausiert
            "hai" -> { haiPause = 0f; haiKommt() }
            "fisch" -> fischDazu(manuell = true)
        }
        return true
    }
}
