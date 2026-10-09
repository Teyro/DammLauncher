/*
 * DammLauncher – Copyright (C) 2026 Teyro – GNU GPL v3 oder neuer, siehe LICENSE
 */
package de.oejendorferdamm.dammlauncher.apps

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.util.LruCache
import de.oejendorferdamm.dammlauncher.konfig.KonfigSpeicher
import java.io.File
import java.text.Collator
import java.util.Locale
import java.util.concurrent.Executors

/** Eine startbare App: Name und Activity. */
data class AppInfo(val name: String, val komponente: ComponentName) {
    val paket get() = komponente.packageName
    val aktivitaet get() = komponente.className
}

/**
 * Alle Apps mit Startsymbol – über LauncherApps (für Launcher gedacht, braucht keine Rundum-
 * Berechtigung). Die Liste wird nur bei Paketänderungen neu geladen, Symbole werden in passender
 * Größe einmal gezeichnet und zwischengespeichert.
 */
object AppKatalog {
    private lateinit var la: LauncherApps
    private lateinit var kontext: Context
    private val haupt = Handler(Looper.getMainLooper())
    private val iconFaden = Executors.newSingleThreadExecutor { r -> Thread(r, "Icons").apply { priority = Thread.NORM_PRIORITY - 1 } }
    private val beobachter = ArrayList<(entfernt: String?) -> Unit>()

    @Volatile
    var apps: List<AppInfo> = emptyList()
        private set
    private var nachKomponente: Map<ComponentName, AppInfo> = emptyMap()

    /** Symbole: Schlüssel = Komponente bzw. Datei + Größe; höchstens 40 MB. */
    private val cache = object : LruCache<String, Bitmap>(40 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun init(context: Context) {
        if (::la.isInitialized) return
        kontext = context.applicationContext
        la = kontext.getSystemService(LauncherApps::class.java)
        laden()
        la.registerCallback(object : LauncherApps.Callback() {
            override fun onPackageRemoved(p: String, u: UserHandle) = geaendert(p, entfernt = true)
            override fun onPackageAdded(p: String, u: UserHandle) = geaendert(p, entfernt = false)
            override fun onPackageChanged(p: String, u: UserHandle) = geaendert(p, entfernt = false)
            override fun onPackagesAvailable(p: Array<out String>, u: UserHandle, r: Boolean) = geaendert(null, false)
            override fun onPackagesUnavailable(p: Array<out String>, u: UserHandle, r: Boolean) = geaendert(null, false)
        }, haupt)
    }

    private fun laden() {
        val liste = try {
            la.getActivityList(null, Process.myUserHandle())
        } catch (_: Exception) {
            emptyList<LauncherActivityInfo>()
        }
        val collator = Collator.getInstance(Locale.GERMAN)
        apps = liste.filter { it.componentName.packageName != kontext.packageName }
            .map { AppInfo(it.label?.toString()?.trim().orEmpty().ifEmpty { it.componentName.packageName }, it.componentName) }
            .sortedWith { a, b -> collator.compare(a.name, b.name) }
        nachKomponente = apps.associateBy { it.komponente }
    }

    private fun geaendert(paket: String?, entfernt: Boolean) {
        laden()
        // Symbole dieses Pakets neu zeichnen lassen
        cache.snapshot().keys.filter { paket == null || it.startsWith("$paket/") }.forEach { cache.remove(it) }
        val liste = synchronized(beobachter) { beobachter.toList() }
        liste.forEach { it(if (entfernt) paket else null) }
    }

    fun beobachte(b: (String?) -> Unit) = synchronized(beobachter) { beobachter.add(b) }
    fun vergiss(b: (String?) -> Unit) = synchronized(beobachter) { beobachter.remove(b) }

    fun finde(paket: String, aktivitaet: String): AppInfo? = nachKomponente[ComponentName(paket, aktivitaet)]
    fun istDa(paket: String, aktivitaet: String) = finde(paket, aktivitaet) != null

    fun starte(app: ComponentName, quelle: android.graphics.Rect?): Boolean = try {
        la.startMainActivity(app, Process.myUserHandle(), quelle, null)
        true
    } catch (_: Exception) {
        false
    }

    fun zeigeAppInfo(app: ComponentName) = try {
        la.startAppDetailsActivity(app, Process.myUserHandle(), null, null)
    } catch (_: Exception) {
    }

    /** Originalsymbol einer App als Drawable (im Hintergrund aufrufen). */
    fun originalIcon(app: ComponentName): Drawable? = try {
        val dpi = kontext.resources.displayMetrics.densityDpi
        la.resolveActivity(android.content.Intent().setComponent(app), Process.myUserHandle())?.getIcon(dpi)
    } catch (_: Exception) {
        null
    }

    /**
     * Symbol in [groesse] px liefern. Steht es im Zwischenspeicher, sofort; sonst wird es im
     * Hintergrund gezeichnet und [fertig] auf dem Haupt-Faden aufgerufen. [eigenes] = eigene Symboldatei.
     */
    fun icon(app: ComponentName, eigenes: String?, groesse: Int, fertig: (Bitmap?) -> Unit): Bitmap? {
        val g = groesse.coerceIn(16, 512)
        val schluessel = (eigenes ?: "${app.packageName}/${app.className}") + "@" + g
        cache.get(schluessel)?.let { return it }
        iconFaden.execute {
            val b = try {
                if (eigenes != null) ladeDatei(File(KonfigSpeicher.iconOrdner, eigenes), g) else originalIcon(app)?.let { zeichne(it, g) }
            } catch (_: Throwable) {
                null
            }
            if (b != null) cache.put(schluessel, b)
            haupt.post { fertig(b) }
        }
        return null
    }

    fun zeichne(d: Drawable, g: Int): Bitmap {
        val b = Bitmap.createBitmap(g, g, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, g, g)
        d.draw(Canvas(b))
        return b
    }

    private fun ladeDatei(f: File, g: Int): Bitmap? {
        if (!f.exists()) return null
        val roh = BitmapFactory.decodeFile(f.absolutePath) ?: return null
        if (roh.width == g && roh.height == g) return roh
        // Seitenverhältnis bleibt, in ein Quadrat eingepasst
        val b = Bitmap.createBitmap(g, g, Bitmap.Config.ARGB_8888)
        val k = minOf(g.toFloat() / roh.width, g.toFloat() / roh.height)
        val w = roh.width * k
        val h = roh.height * k
        Canvas(b).drawBitmap(roh, null, android.graphics.RectF((g - w) / 2, (g - h) / 2, (g + w) / 2, (g + h) / 2), android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
        roh.recycle()
        return b
    }

    fun cacheLeeren() = cache.evictAll()
}
