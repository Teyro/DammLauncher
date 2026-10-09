# DammLauncher

Ein schlanker Android-Startbildschirm (Launcher) für interaktive 75-Zoll-Displays an Schulen –
Clevertouch-Einschubgeräte und ältere CTOUCH-Panels ab Android 8. Ziele in dieser Reihenfolge:
**Stabilität, Geschwindigkeit, Sicherheit, kleine APK** (Release-APK unter 100 KB).

Freie Software unter der **GNU GPL v3** (siehe `LICENSE`).

## Funktionen

- **Frei einstellbares Raster**: Spalten und Zeilen je 1–10, Randabstand oben/unten/links/rechts
  einzeln oder „alle gleich“ (0 = randlos), Abstand zwischen den Zellen, Symbol- und Schriftgröße,
  Namen ein-/ausblendbar. Sieht auf 1920×1080 und 3840×2160 gleich aus.
- **Apps ablegen und verschieben**: aus der App-Übersicht per langem Druck „Zum Startbildschirm
  hinzufügen“; auf dem Startbildschirm lange drücken und auf eine freie Zelle ziehen, oben auf
  „Entfernen“ ziehen oder loslassen für das Menü.
- **Name und Symbol ändern**: eigener Name, eigenes Bild (PNG, JPG …) oder das Symbol einer
  anderen App. Die Symbole wandern mit Export und Vorlage auf andere Geräte.
- **Raster verkleinern ohne Verlust**: Was nicht mehr passt, rückt in freie Zellen, Widgets werden
  notfalls kleiner; der Rest kommt in die Ablage „nicht platziert“ und kehrt zurück, sobald Platz ist.
- **Widgets** aller Apps; mehrere Zellen groß, Größe per Griffen änderbar. Ein abstürzendes Widget
  reißt den Launcher nicht mit.
- **Hintergrund**: Bild (einmalig auf Bildschirmgröße verkleinert gespeichert) oder Farbe.
- **App-Übersicht**: nach oben wischen oder großer Knopf „Alle Apps“; alphabetisch, mit Suche.
  Schließen: nach unten wischen, Zurück oder Knopf.
- **Bedienung für 75 Zoll**: großes Einstellungs-Panel mit Reglern und +/−-Knöpfen,
  Live-Vorschau (Panel lässt sich zur Seite schieben). Einstellungen per langem Druck auf eine
  freie Fläche, optional mit **PIN**. Schalter „Startbildschirm gegen Verändern sperren“.
- **Export/Import** der kompletten Einrichtung als ZIP-Datei.
- **Zentrale Verteilung über IServ (WebDAV), „Boss-Modus“** – siehe unten.
- **Updates über GitHub** mit Update-Pflicht – siehe unten.
- Keine Animationen (abschaltbar, Standard: aus), kein Tracking, keine Werbung, keine Datenbank.
- **Absturzschutz**: Stürzt der Launcher mehrmals kurz hintereinander ab, startet er mit der letzten
  funktionierenden Einrichtung und ohne Widgets – nie eine Absturzschleife. Unlesbare oder unsinnige
  Einstellungen werden auf Standardwerte gesetzt.

## Installation

1. `DammLauncher.apk` aus den [Releases](https://github.com/Teyro/DammLauncher/releases) laden und
   installieren (dafür „Unbekannte Quellen“ für den Dateimanager bzw. Browser erlauben).
2. Home-Taste drücken und **DammLauncher** wählen, dann **Immer**.
   Alternativ: Android-Einstellungen → Apps → Standard-Apps → Start-App → DammLauncher.
   Per ADB: `adb shell cmd package set-home-activity de.oejendorferdamm.dammlauncher/.HomeActivity`

## Zentrale Verteilung (IServ / WebDAV)

Ein Gerät wird als Vorlage eingerichtet, alle anderen übernehmen das automatisch.

1. Einstellungen → **Boss-Modus …** → beim ersten Mal ein Master-Passwort festlegen (es wird nur als
   PBKDF2-Hash mit Salt gespeichert; nach 5 Fehlversuchen Sperre mit wachsender Wartezeit).
2. Im Boss-Modus Server (z. B. `https://<iserv-adresse>/webdav`), Ordner (z. B.
   `Files/DammLauncher`) und einen Zugang **nur mit Leserechten** eintragen. Benutzername und
   Passwort werden mit einem Schlüssel aus dem Android Keystore verschlüsselt gespeichert.
3. **Als Vorlage für alle Geräte hochladen**: Dafür werden Zugangsdaten **mit Schreibrechten**
   abgefragt – nur für diesen Vorgang, sie werden nicht gespeichert. Hochgeladen wird erst in eine
   `.tmp`-Datei, die dann umbenannt wird; Geräte laden so nie eine halbe Datei.
4. Jedes Gerät prüft einmal täglich (und beim Start, wenn es länger als 24 Stunden her ist), ob es
   eine neuere Vorlage gibt. Die Vorlage wird vollständig geprüft; ist sie fehlerhaft, bleibt die
   alte Einrichtung. Die letzte funktionierende Einrichtung wird als Sicherung aufgehoben.

Im Ordner liegen `vorlage.json`, ggf. `hintergrund.jpg` und `icons/…`. Apps sind über Paketname +
Activity gespeichert, Widgets über den Anbieter + Position + Größe (nicht über die gerätespezifische
Widget-Id). Fehlt eine App oder ein Widget-Anbieter auf einem Gerät, bleibt die Zelle leer.

**Widgets aus der Vorlage**: Android verlangt pro Gerät einmal die Zustimmung, dass DammLauncher
Widgets anlegen darf. Beim ersten Mal erscheint ein Hinweis – im System-Fenster „Immer erlauben“
anhaken; danach geht alles ohne Rückfragen. Widgets, die beim Anlegen eine Einrichtung brauchen,
lassen sich über langen Druck → „Widget einrichten“ nachträglich einrichten.

Gerätespezifisch und vom Abgleich nie überschrieben: Gerätename, Zugangsdaten, Widget-Ids,
Ein/Aus des automatischen Abgleichs.

## Updates und Update-Pflicht

DammLauncher prüft beim Start höchstens einmal täglich (und auf Knopfdruck in den Einstellungen)
die GitHub-Releases **dieses** Repositorys. Eine neue Version wird mit Änderungen angezeigt und auf
Wunsch geladen; vor der Installation wird geprüft, dass die APK mit **demselben Zertifikat** signiert
ist wie die installierte Version – sonst wird abgebrochen. Geladen wird nur über HTTPS von GitHub.

**Update-Pflicht aus Sicherheitsgründen:** Ist eine neuere Version schon **länger als 7 Tage**
veröffentlicht und läuft auf dem Gerät noch die alte, sperrt sich der Launcher mit dem Hinweis
„Diese Version ist veraltet und wird aus Sicherheitsgründen gesperrt. Bitte aktualisieren.“ und
einem großen Update-Knopf. Maßgeblich ist das Alter der neuen Version (Datum von GitHub, nicht die
Uhr des Geräts). **Ist GitHub nicht erreichbar, wird nie gesperrt** – eine Störung des Servers führt
nicht zur Sperre.

## Berechtigungen

| Berechtigung | Wofür |
|---|---|
| `INTERNET` | Update-Check bei GitHub und Vorlagen-Abgleich mit dem IServ-WebDAV – sonst nichts |
| `ACCESS_NETWORK_STATE` | Der tägliche Abgleich startet erst, wenn Netz da ist |
| `REQUEST_INSTALL_PACKAGES` | Die geladene, geprüfte Update-APK an den System-Installer übergeben |
| `<queries>` (MAIN/LAUNCHER, Widget-Anbieter) | Apps mit Startsymbol und Widgets anzeigen – statt der weitreichenden `QUERY_ALL_PACKAGES` |

Exportiert ist nur die Startbildschirm-Activity. Es werden keine persönlichen Daten gespeichert.

## Bauen

Voraussetzungen: JDK 17, Android SDK (Plattform 34).

```
./gradlew testDebugUnitTest   # Unit-Tests (Raster, Konfiguration, Update-Regel)
./gradlew assembleDebug       # Testversion
./gradlew assembleRelease     # Release (R8, Shrinking)
```

Ein Tag `vX.Y.Z` baut über GitHub Actions die signierte Release-APK und hängt sie an das Release
(Signaturschlüssel als Secrets `RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD`,
`RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`). Der Emulator-Test (`screenshots.yml`) prüft
Android 8 und 11 in Full HD und 4K gegen einen Test-Server (`scripts/testserver.py`).

Die Testversion (debug) fragt statt GitHub und IServ einen lokalen Test-Server auf dem Gerät –
das betrifft nur die Testversion, die Release-Version kennt ausschließlich HTTPS.
