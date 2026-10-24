/*
 * DammLauncher – Copyright (C) 2026 Teyro – GNU GPL v3 oder neuer, siehe LICENSE
 */
package de.oejendorferdamm.dammlauncher.qr

/**
 * Kleiner QR-Code-Erzeuger ohne Bibliothek: Byte-Modus (UTF-8), Fehlerkorrektur M, Version 1–20
 * (bis 666 Bytes – reicht für Links und WLAN-Zugänge). Aufbau nach ISO/IEC 18004, angelehnt an
 * den freien Referenz-Erzeuger von Project Nayuki (MIT).
 */
class QrCode private constructor(val groesse: Int, private val module: Array<BooleanArray>) {
    /** Dunkles Modul bei Spalte [x], Zeile [y]? */
    fun dunkel(x: Int, y: Int) = x in 0 until groesse && y in 0 until groesse && module[y][x]

    companion object {
        const val MAX_VERSION = 20

        // Fehlerkorrektur M: Prüfbytes je Block und Anzahl Blöcke (Index = Version)
        private val ECC_JE_BLOCK = intArrayOf(-1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24, 28, 28, 26, 26, 26)
        private val BLOECKE = intArrayOf(-1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5, 5, 8, 9, 9, 10, 10, 11, 13, 14, 16)

        /** Wie viele Datenbytes passen in [version] (nach Abzug der Prüfbytes)? */
        fun datenBytes(version: Int) = rohBits(version) / 8 - ECC_JE_BLOCK[version] * BLOECKE[version]

        /** QR-Code für [text]; null, wenn er zu lang ist. */
        fun aus(text: String): QrCode? {
            val daten = text.toByteArray(Charsets.UTF_8)
            for (v in 1..MAX_VERSION) {
                val bits = 4 + (if (v < 10) 8 else 16) + daten.size * 8
                if (bits <= datenBytes(v) * 8) return baue(v, daten)
            }
            return null
        }

        private fun rohBits(v: Int): Int {
            var r = (16 * v + 128) * v + 64
            if (v >= 2) {
                val n = v / 7 + 2
                r -= (25 * n - 10) * n - 55
                if (v >= 7) r -= 36
            }
            return r
        }

        private fun baue(v: Int, daten: ByteArray): QrCode {
            // ---- Bitfolge: Modus „Byte“, Länge, Daten, Abschluss, Füllbytes
            val bits = ArrayList<Boolean>()
            fun schreibe(wert: Int, n: Int) { for (i in n - 1 downTo 0) bits.add((wert ushr i) and 1 != 0) }
            schreibe(0b0100, 4)
            schreibe(daten.size, if (v < 10) 8 else 16)
            daten.forEach { schreibe(it.toInt() and 0xFF, 8) }
            val kapazitaet = datenBytes(v) * 8
            schreibe(0, minOf(4, kapazitaet - bits.size))
            schreibe(0, (8 - bits.size % 8) % 8)
            var fuell = 0xEC
            while (bits.size < kapazitaet) { schreibe(fuell, 8); fuell = fuell xor 0xEC xor 0x11 }
            val woerter = IntArray(bits.size / 8) { i -> (0 until 8).fold(0) { a, j -> (a shl 1) or (if (bits[i * 8 + j]) 1 else 0) } }

            val alles = mitFehlerkorrektur(v, woerter)
            val g = v * 4 + 17
            val m = Array(g) { BooleanArray(g) }
            val fest = Array(g) { BooleanArray(g) }
            fun setze(x: Int, y: Int, d: Boolean) { m[y][x] = d; fest[y][x] = true }

            // ---- Feste Muster: Taktlinien, Suchmuster, Ausrichtungsmuster, Platz für Format/Version
            for (i in 0 until g) { setze(6, i, i % 2 == 0); setze(i, 6, i % 2 == 0) }
            for ((cx, cy) in listOf(3 to 3, g - 4 to 3, 3 to g - 4)) {
                for (dy in -4..4) for (dx in -4..4) {
                    val x = cx + dx; val y = cy + dy
                    if (x in 0 until g && y in 0 until g) {
                        val d = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy))
                        setze(x, y, d != 2 && d != 4)
                    }
                }
            }
            val lagen = ausrichtung(v, g)
            for (i in lagen.indices) for (j in lagen.indices) {
                if ((i == 0 && j == 0) || (i == 0 && j == lagen.size - 1) || (i == lagen.size - 1 && j == 0)) continue
                for (dy in -2..2) for (dx in -2..2) setze(lagen[i] + dx, lagen[j] + dy, maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy)) != 1)
            }
            format(g, 0) { x, y, d -> setze(x, y, d) }
            if (v >= 7) {
                var rest = v
                repeat(12) { rest = (rest shl 1) xor ((rest ushr 11) * 0x1F25) }
                val vb = (v shl 12) or rest
                for (i in 0 until 18) {
                    val d = (vb ushr i) and 1 != 0
                    val a = g - 11 + i % 3; val b = i / 3
                    setze(a, b, d); setze(b, a, d)
                }
            }

            // ---- Daten im Zickzack von unten rechts
            var i = 0
            var rechts = g - 1
            while (rechts >= 1) {
                if (rechts == 6) rechts = 5
                for (senk in 0 until g) for (j in 0..1) {
                    val x = rechts - j
                    val hoch = ((rechts + 1) and 2) == 0
                    val y = if (hoch) g - 1 - senk else senk
                    if (!fest[y][x] && i < alles.size * 8) {
                        m[y][x] = (alles[i ushr 3] ushr (7 - (i and 7))) and 1 != 0
                        i++
                    }
                }
                rechts -= 2
            }

            // ---- Maske mit den wenigsten Strafpunkten
            var beste = 0
            var bestePunkte = Int.MAX_VALUE
            for (maske in 0 until 8) {
                maskiere(m, fest, maske)
                format(g, maske) { x, y, d -> m[y][x] = d }
                val p = strafe(m)
                if (p < bestePunkte) { bestePunkte = p; beste = maske }
                maskiere(m, fest, maske) // zurück (XOR)
            }
            maskiere(m, fest, beste)
            format(g, beste) { x, y, d -> m[y][x] = d }
            return QrCode(g, m)
        }

        /** Formatbits (Fehlerkorrektur M = 00, Maske) an beide Stellen; plus das immer dunkle Modul. */
        private fun format(g: Int, maske: Int, setze: (Int, Int, Boolean) -> Unit) {
            val d = maske // M: 00 << 3 | maske
            var rest = d
            repeat(10) { rest = (rest shl 1) xor ((rest ushr 9) * 0x537) }
            val b = ((d shl 10) or rest) xor 0x5412
            fun bit(i: Int) = (b ushr i) and 1 != 0
            for (i in 0..5) setze(8, i, bit(i))
            setze(8, 7, bit(6)); setze(8, 8, bit(7)); setze(7, 8, bit(8))
            for (i in 9 until 15) setze(14 - i, 8, bit(i))
            for (i in 0 until 8) setze(g - 1 - i, 8, bit(i))
            for (i in 8 until 15) setze(8, g - 15 + i, bit(i))
            setze(8, g - 8, true)
        }

        private fun ausrichtung(v: Int, g: Int): IntArray {
            if (v == 1) return IntArray(0)
            val n = v / 7 + 2
            val schritt = (v * 8 + n * 3 + 5) / (n * 4 - 4) * 2
            val r = IntArray(n)
            r[0] = 6
            var pos = g - 7
            for (k in n - 1 downTo 1) { r[k] = pos; pos -= schritt }
            return r
        }

        private fun maskiere(m: Array<BooleanArray>, fest: Array<BooleanArray>, maske: Int) {
            for (y in m.indices) for (x in m.indices) {
                if (fest[y][x]) continue
                val an = when (maske) {
                    0 -> (x + y) % 2 == 0
                    1 -> y % 2 == 0
                    2 -> x % 3 == 0
                    3 -> (x + y) % 3 == 0
                    4 -> (x / 3 + y / 2) % 2 == 0
                    5 -> x * y % 2 + x * y % 3 == 0
                    6 -> (x * y % 2 + x * y % 3) % 2 == 0
                    else -> ((x + y) % 2 + x * y % 3) % 2 == 0
                }
                if (an) m[y][x] = !m[y][x]
            }
        }

        /** Vereinfachte Strafpunkte (lange Reihen, 2×2-Blöcke, Hell/Dunkel-Verhältnis) – jede Maske ist lesbar, diese nur besser. */
        private fun strafe(m: Array<BooleanArray>): Int {
            val g = m.size
            var p = 0
            for (a in 0 until g) {
                var lz = 1; var ls = 1
                for (b in 1 until g) {
                    if (m[a][b] == m[a][b - 1]) { lz++; if (lz == 5) p += 3 else if (lz > 5) p++ } else lz = 1
                    if (m[b][a] == m[b - 1][a]) { ls++; if (ls == 5) p += 3 else if (ls > 5) p++ } else ls = 1
                }
            }
            for (y in 0 until g - 1) for (x in 0 until g - 1) {
                val c = m[y][x]
                if (c == m[y][x + 1] && c == m[y + 1][x] && c == m[y + 1][x + 1]) p += 3
            }
            val dunkel = m.sumOf { r -> r.count { it } }
            val gesamt = g * g
            p += ((kotlin.math.abs(dunkel * 20 - gesamt * 10) + gesamt - 1) / gesamt - 1).coerceAtLeast(0) * 10
            return p
        }

        /** Daten in Blöcke teilen, je Block Reed-Solomon-Prüfbytes anhängen, verschränken. */
        private fun mitFehlerkorrektur(v: Int, daten: IntArray): IntArray {
            val n = BLOECKE[v]
            val eccLaenge = ECC_JE_BLOCK[v]
            val roh = rohBits(v) / 8
            val kurze = n - roh % n
            val kurzLaenge = roh / n
            val teiler = rsTeiler(eccLaenge)
            val bloecke = ArrayList<IntArray>()
            var k = 0
            for (i in 0 until n) {
                val laenge = kurzLaenge - eccLaenge + (if (i < kurze) 0 else 1)
                val dat = daten.copyOfRange(k, k + laenge)
                k += laenge
                val ecc = rsRest(dat, teiler)
                // kurze Blöcke bekommen eine Lücke, damit alle gleich lang verschränkt werden
                bloecke.add(if (i < kurze) dat + intArrayOf(0) + ecc else dat + ecc)
            }
            val r = ArrayList<Int>(roh)
            for (i in bloecke[0].indices) for (j in bloecke.indices) {
                if (i != kurzLaenge - eccLaenge || j >= kurze) r.add(bloecke[j][i])
            }
            return r.toIntArray()
        }

        private fun mal(x: Int, y: Int): Int {
            var z = 0
            for (i in 7 downTo 0) {
                z = (z shl 1) xor ((z ushr 7) * 0x11D)
                z = z xor (((y ushr i) and 1) * x)
            }
            return z
        }

        private fun rsTeiler(grad: Int): IntArray {
            val r = IntArray(grad)
            r[grad - 1] = 1
            var wurzel = 1
            repeat(grad) {
                for (j in 0 until grad) {
                    r[j] = mal(r[j], wurzel)
                    if (j + 1 < grad) r[j] = r[j] xor r[j + 1]
                }
                wurzel = mal(wurzel, 0x02)
            }
            return r
        }

        private fun rsRest(daten: IntArray, teiler: IntArray): IntArray {
            val r = IntArray(teiler.size)
            for (b in daten) {
                val f = b xor r[0]
                System.arraycopy(r, 1, r, 0, r.size - 1)
                r[r.size - 1] = 0
                for (i in r.indices) r[i] = r[i] xor mal(teiler[i], f)
            }
            return r
        }

        /** Text für einen WLAN-Zugang (versteht die Kamera-App von iPhone und Android). */
        fun wlan(ssid: String, passwort: String): String {
            fun esc(s: String) = s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace(":", "\\:").replace("\"", "\\\"")
            return if (passwort.isEmpty()) "WIFI:T:nopass;S:${esc(ssid)};;" else "WIFI:T:WPA;S:${esc(ssid)};P:${esc(passwort)};;"
        }

        /** WLAN-Name aus einem WIFI:-Text (für die Beschriftung), sonst null. */
        fun wlanName(text: String): String? = wlanFeld(text, "S")

        /** Ein Feld (S = Name, P = Passwort) aus einem WIFI:-Text, sonst null. */
        fun wlanFeld(text: String, feld: String): String? {
            if (!text.startsWith("WIFI:")) return null
            val m = Regex("(?:^WIFI:|;)$feld:((?:\\\\.|[^;\\\\])*)").find(text) ?: return null
            return m.groupValues[1].replace(Regex("\\\\(.)"), "$1")
        }
    }
}
