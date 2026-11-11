/*
 * DammLauncher – Copyright (C) 2026 Teyro – GNU GPL v3 oder neuer, siehe LICENSE
 */
package de.oejendorferdamm.dammlauncher.nutzung

import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Nutzungs-Bericht als OpenOffice-/LibreOffice-Textdokument (.odt) – ohne Zusatzbibliothek:
 * ein ZIP mit content.xml, styles.xml und den Grafiken als PNG.
 */
object OdtBericht {
    const val MIME = "application/vnd.oasis.opendocument.text"

    data class Bild(val name: String, val png: ByteArray, val breitePx: Int, val hoehePx: Int)

    data class Daten(
        val board: String,
        val version: String,
        val tage: List<Tag>,
        val name: (String) -> String,
        val balken: Bild?,
        val zeitleiste: Bild?,
        val erstellt: Long = System.currentTimeMillis()
    )

    fun erzeuge(d: Daten): ByteArray {
        val bilder = listOfNotNull(d.balken, d.zeitleiste)
        val aus = ByteArrayOutputStream()
        ZipOutputStream(aus).use { zip ->
            // „mimetype“ muss als erste Datei unkomprimiert drinstehen
            val mt = MIME.toByteArray(Charsets.US_ASCII)
            zip.putNextEntry(ZipEntry("mimetype").apply {
                method = ZipEntry.STORED; size = mt.size.toLong(); compressedSize = mt.size.toLong()
                crc = CRC32().apply { update(mt) }.value
            })
            zip.write(mt); zip.closeEntry()
            fun datei(n: String, b: ByteArray) { zip.putNextEntry(ZipEntry(n)); zip.write(b); zip.closeEntry() }
            datei("META-INF/manifest.xml", manifest(bilder).toByteArray())
            datei("styles.xml", STYLES.toByteArray())
            datei("content.xml", inhalt(d).toByteArray())
            bilder.forEach { datei("Pictures/${it.name}", it.png) }
        }
        return aus.toByteArray()
    }

    fun esc(t: String) = t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        .filter { it == '\n' || it == '\t' || it >= ' ' }

    private fun manifest(bilder: List<Bild>) = """<?xml version="1.0" encoding="UTF-8"?>
<manifest:manifest xmlns:manifest="urn:oasis:names:tc:opendocument:xmlns:manifest:1.0" manifest:version="1.2">
 <manifest:file-entry manifest:full-path="/" manifest:version="1.2" manifest:media-type="$MIME"/>
 <manifest:file-entry manifest:full-path="content.xml" manifest:media-type="text/xml"/>
 <manifest:file-entry manifest:full-path="styles.xml" manifest:media-type="text/xml"/>
${bilder.joinToString("\n") { " <manifest:file-entry manifest:full-path=\"Pictures/${it.name}\" manifest:media-type=\"image/png\"/>" }}
</manifest:manifest>"""

    private const val NS = """xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0" xmlns:style="urn:oasis:names:tc:opendocument:xmlns:style:1.0" xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0" xmlns:table="urn:oasis:names:tc:opendocument:xmlns:table:1.0" xmlns:draw="urn:oasis:names:tc:opendocument:xmlns:drawing:1.0" xmlns:fo="urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0" xmlns:xlink="http://www.w3.org/1999/xlink" xmlns:svg="urn:oasis:names:tc:opendocument:xmlns:svg-compatible:1.0" office:version="1.2""""

    private val STYLES = """<?xml version="1.0" encoding="UTF-8"?>
<office:document-styles $NS>
 <office:font-face-decls><style:font-face style:name="Liberation Sans" svg:font-family="'Liberation Sans', Arial, sans-serif" style:font-family-generic="swiss"/></office:font-face-decls>
 <office:styles>
  <style:default-style style:family="paragraph"><style:paragraph-properties fo:margin-top="0cm" fo:margin-bottom="0.15cm"/><style:text-properties style:font-name="Liberation Sans" fo:font-size="10.5pt" fo:color="#1F2124" fo:language="de" fo:country="DE"/></style:default-style>
 </office:styles>
 <office:automatic-styles>
  <style:page-layout style:name="Seite"><style:page-layout-properties fo:page-width="21cm" fo:page-height="29.7cm" fo:margin-top="1.8cm" fo:margin-bottom="1.8cm" fo:margin-left="2cm" fo:margin-right="2cm"/></style:page-layout>
 </office:automatic-styles>
 <office:master-styles><style:master-page style:name="Standard" style:page-layout-name="Seite"/></office:master-styles>
</office:document-styles>"""

    private val AUTO = """
  <style:style style:name="Titel" style:family="paragraph"><style:paragraph-properties fo:margin-bottom="0.1cm"/><style:text-properties fo:font-size="22pt" fo:font-weight="bold"/></style:style>
  <style:style style:name="Unter" style:family="paragraph"><style:paragraph-properties fo:margin-bottom="0.5cm" fo:border-bottom="1.5pt solid #E01E3C" fo:padding-bottom="0.25cm"/><style:text-properties fo:font-size="10pt" fo:color="#6B7078"/></style:style>
  <style:style style:name="H1" style:family="paragraph"><style:paragraph-properties fo:margin-top="0.6cm" fo:margin-bottom="0.25cm" fo:keep-with-next="always"/><style:text-properties fo:font-size="15pt" fo:font-weight="bold"/></style:style>
  <style:style style:name="H2" style:family="paragraph"><style:paragraph-properties fo:margin-top="0.4cm" fo:margin-bottom="0.1cm" fo:keep-with-next="always"/><style:text-properties fo:font-size="11.5pt" fo:font-weight="bold"/></style:style>
  <style:style style:name="P" style:family="paragraph"><style:text-properties fo:font-size="10pt"/></style:style>
  <style:style style:name="Klein" style:family="paragraph"><style:text-properties fo:font-size="9pt" fo:color="#6B7078"/></style:style>
  <style:style style:name="Zahl" style:family="paragraph"><style:text-properties fo:font-size="16pt" fo:font-weight="bold"/></style:style>
  <style:style style:name="KopfP" style:family="paragraph"><style:text-properties fo:font-size="9.5pt" fo:font-weight="bold" fo:color="#FFFFFF"/></style:style>
  <style:style style:name="Tab" style:family="table"><style:table-properties style:width="17cm" table:align="left" fo:margin-bottom="0.2cm"/></style:style>
  <style:style style:name="Kopf" style:family="table-cell"><style:table-cell-properties fo:background-color="#1F2124" fo:padding="0.12cm" fo:border="none"/></style:style>
  <style:style style:name="Z1" style:family="table-cell"><style:table-cell-properties fo:background-color="#FFFFFF" fo:padding="0.1cm" fo:border-bottom="0.5pt solid #E1E4E8" fo:border-top="none" fo:border-left="none" fo:border-right="none"/></style:style>
  <style:style style:name="Z2" style:family="table-cell"><style:table-cell-properties fo:background-color="#F4F5F7" fo:padding="0.1cm" fo:border-bottom="0.5pt solid #E1E4E8" fo:border-top="none" fo:border-left="none" fo:border-right="none"/></style:style>
  <style:style style:name="Kachel" style:family="table-cell"><style:table-cell-properties fo:background-color="#ECEEF1" fo:padding="0.25cm" fo:border="2pt solid #FFFFFF"/></style:style>
  <style:style style:name="Rahmen" style:family="graphic"><style:graphic-properties style:wrap="none" style:vertical-pos="top" style:horizontal-pos="center" style:horizontal-rel="paragraph" fo:border="none"/></style:style>
"""

    private fun p(stil: String, t: String) = "<text:p text:style-name=\"$stil\">${esc(t)}</text:p>"

    private fun tabelle(kopf: List<String>, breitenCm: List<Double>, zeilen: List<List<String>>): String {
        val sb = StringBuilder("<table:table table:style-name=\"Tab\">")
        breitenCm.forEachIndexed { i, b -> sb.append("<table:table-column table:style-name=\"Sp$i-${b}\"/>") }
        sb.append("<table:table-header-rows><table:table-row>")
        kopf.forEach { sb.append("<table:table-cell table:style-name=\"Kopf\" office:value-type=\"string\">${p("KopfP", it)}</table:table-cell>") }
        sb.append("</table:table-row></table:table-header-rows>")
        zeilen.forEachIndexed { i, z ->
            sb.append("<table:table-row>")
            z.forEach { sb.append("<table:table-cell table:style-name=\"${if (i % 2 == 0) "Z1" else "Z2"}\" office:value-type=\"string\">${p("P", it)}</table:table-cell>") }
            sb.append("</table:table-row>")
        }
        return sb.append("</table:table>").toString()
    }

    private fun bild(b: Bild): String {
        val breite = 17.0
        val hoehe = breite * b.hoehePx / b.breitePx
        return "<text:p><draw:frame draw:style-name=\"Rahmen\" draw:name=\"${b.name}\" text:anchor-type=\"as-char\" svg:width=\"${"%.2f".format(Locale.US, breite)}cm\" svg:height=\"${"%.2f".format(Locale.US, hoehe)}cm\"><draw:image xlink:href=\"Pictures/${b.name}\" xlink:type=\"simple\" xlink:show=\"embed\" xlink:actuate=\"onLoad\"/></draw:frame></text:p>"
    }

    fun inhalt(d: Daten): String {
        val tage = d.tage
        val datum = SimpleDateFormat("dd.MM.yyyy", Locale.GERMAN)
        val tag = SimpleDateFormat("EEEE, dd.MM.yyyy", Locale.GERMAN)
        val uhr = SimpleDateFormat("HH:mm", Locale.GERMAN)
        val jeApp = Auswertung.jeApp(tage)
        val starts = Auswertung.starts(tage)
        val gesamtApp = tage.sumOf { it.appMs }.coerceAtLeast(1L)
        val genutzt = tage.filter { !it.leer }
        val body = StringBuilder()
        body.append(p("Titel", "Nutzung: ${d.board}"))
        val zeitraum = if (tage.isEmpty()) "" else "${datum.format(Date(tage.last().beginn))} – ${datum.format(Date(tage.first().beginn))}"
        body.append(p("Unter", "Zeitraum $zeitraum (${tage.size} Tage) · erstellt ${datum.format(Date(d.erstellt))} um ${uhr.format(Date(d.erstellt))} Uhr · DammLauncher ${d.version}"))

        // Kennzahlen als Kacheln (2 × 3)
        val kennzahlen = listOf(
            "Bildschirm an" to Auswertung.dauer(tage.sumOf { it.anMs }),
            "Tage mit Nutzung" to "${genutzt.size} von ${tage.size}",
            "Ø je genutztem Tag" to Auswertung.dauer(if (genutzt.isEmpty()) 0 else genutzt.sumOf { it.anMs } / genutzt.size),
            "Apps offen" to Auswertung.dauer(tage.sumOf { it.appMs }),
            "Meistgenutzt" to (jeApp.firstOrNull()?.let { d.name(it.first) } ?: "–"),
            "Abends noch an (nach ${Auswertung.ABEND_STUNDE} Uhr)" to "${Auswertung.abendsAn(tage)} Tage"
        )
        body.append("<table:table table:style-name=\"Tab\"><table:table-column table:number-columns-repeated=\"3\"/>")
        kennzahlen.chunked(3).forEach { reihe ->
            body.append("<table:table-row>")
            reihe.forEach { (k, v) -> body.append("<table:table-cell table:style-name=\"Kachel\" office:value-type=\"string\">${p("Klein", k)}${p("Zahl", v)}</table:table-cell>") }
            body.append("</table:table-row>")
        }
        body.append("</table:table>")

        body.append(p("H1", "Zeit je App"))
        d.balken?.let { body.append(bild(it)) }
        body.append(tabelle(listOf("App", "Zeit", "Starts", "Anteil"), listOf(8.0, 3.5, 2.5, 3.0),
            jeApp.map { (pk, ms) -> listOf(d.name(pk), Auswertung.dauer(ms), "${starts[pk] ?: 0}", "${(ms * 100 / gesamtApp)} %") }))

        body.append(p("H1", "Tagesverlauf"))
        body.append(p("Klein", "Grau: Bildschirm an (Startbildschirm). Farbig: eine App war vorn."))
        d.zeitleiste?.let { body.append(bild(it)) }

        body.append(p("H1", "Tage im Detail"))
        if (genutzt.isEmpty()) body.append(p("P", "Keine Nutzung in diesem Zeitraum."))
        genutzt.forEach { t ->
            body.append(p("H2", tag.format(Date(t.beginn))))
            body.append(p("Klein", "Bildschirm an: " + t.bildschirm.joinToString(", ") { "${uhr.format(Date(it.start))}–${uhr.format(Date(it.ende))}" } +
                " · zusammen ${Auswertung.dauer(t.anMs)}"))
            if (t.apps.isNotEmpty()) body.append(tabelle(listOf("Von", "Bis", "Dauer", "App"), listOf(2.5, 2.5, 3.0, 9.0),
                t.apps.map { listOf(uhr.format(Date(it.start)), uhr.format(Date(it.ende)), Auswertung.dauer(it.dauer), d.name(it.paket)) }))
        }
        body.append(p("Klein", "Aufgezeichnet nur auf diesem Board: wann der Bildschirm an war und welche App der Launcher (oder die Seitenleiste) geöffnet hat. " +
            "Keine Namen, keine Inhalte. Einträge werden nach ${Nutzung.AUFBEWAHREN_TAGE} Tagen gelöscht."))

        // Spaltenbreiten der Tabellen
        val spalten = Regex("Sp(\\d+)-([0-9.]+)").findAll(body).map { it.value }.toSet()
        val spaltenStile = spalten.joinToString("") { s -> "<style:style style:name=\"$s\" style:family=\"table-column\"><style:table-column-properties style:column-width=\"${s.substringAfter('-')}cm\"/></style:style>" }

        return """<?xml version="1.0" encoding="UTF-8"?>
<office:document-content $NS>
 <office:automatic-styles>$AUTO$spaltenStile</office:automatic-styles>
 <office:body><office:text>$body</office:text></office:body>
</office:document-content>"""
    }
}
