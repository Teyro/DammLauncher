# DammLauncher – Plan

## Struktur
```
app/src/main/java/de/oejendorferdamm/dammlauncher/
  HomeActivity.kt          einzige exportierte Komponente (HOME/LAUNCHER)
  raster/RasterRechner.kt  Zellrechtecke aus Bildschirm, Rändern, Abstand (rein, getestet)
  raster/Belegung.kt       Plätze, freie Zellen, Verkleinern ohne Verlust (rein, getestet)
  konfig/Konfig.kt         Datenmodell (Raster, Ränder, Apps, Widgets, Hintergrund, Sperren)
  konfig/KonfigJson.kt     JSON lesen/schreiben + Prüfung, Standardwerte bei Unsinn (getestet)
  konfig/KonfigSpeicher.kt Datei + letzte funktionierende Sicherung, Export/Import
  apps/AppKatalog.kt       LauncherApps, Icon-Cache, Neuladen nur bei Paketänderungen
  ui/RasterAnsicht.kt      eigenes ViewGroup: Apps + Widgets ins Raster, Ziehen, Größe ändern
  ui/AppZelle.kt           ein View zeichnet Icon + Text (schnell, wenig Objekte)
  ui/AppUebersicht.kt      App-Übersicht (GridView + Suche), Wischen hoch/runter + Knopf
  ui/EinstellungenPanel.kt großes Panel mit Reglern und +/−, Live-Vorschau
  widgets/WidgetVerwaltung.kt AppWidgetHost, Binden, Konfiguration, absturzsicher
  sicherheit/Passwort.kt   PBKDF2 + Salt, Fehlversuch-Sperre (PIN und Boss-Passwort)
  sicherheit/Tresor.kt     AES-GCM-Schlüssel im Android Keystore für Zugangsdaten
  abgleich/WebDav.kt       PROPFIND/GET/PUT/MKCOL/MOVE über HttpURLConnection, nur HTTPS
  abgleich/Abgleich.kt     Vorlage laden/prüfen/übernehmen, Hochladen (tmp → MOVE)
  abgleich/AbgleichJob.kt  täglich (JobScheduler, nur mit Netz)
  update/Update.kt         GitHub-Releases, 7-Tage-Pflicht, Signaturprüfung, Installer
```

## Reihenfolge
Grundgerüst → Raster/Ränder → App-Übersicht → Einstellungen → Widgets → Hintergrund →
Konfiguration/Export → Boss-Modus/WebDAV → Updates → GitHub-Workflow. Nach jedem Schritt `assembleDebug`.

## Entscheidungen
- **Keine Bibliotheken außer Kotlin**: kein AndroidX, kein OkHttp (HttpURLConnection), kein RecyclerView
  (GridView) → APK klein. Statt WorkManager der eingebaute JobScheduler (gleiche Wirkung, 0 KB).
- **Apps sehen**: `<queries>` für MAIN/LAUNCHER und Widget-Provider statt QUERY_ALL_PACKAGES.
- **Update-Pflicht**: „jetzt“ = Datum aus der Antwort von GitHub (Uhr alter Boards ist oft falsch).
  Gesperrt wird nur nach erfolgreichem Check; Netzfehler heben eine Sperre sofort auf.
- **Signatur**: gleicher Schlüssel wie DammBoard/DammZeit (Secrets im Repo). Release bei Tag `v*`.
- **Tests**: Unit-Tests (Raster, Belegung, Konfiguration) + Emulator-Test (Android 8 und 11, 1080p und 4K).
