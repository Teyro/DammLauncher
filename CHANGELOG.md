# Änderungen

## 1.6.5
- Einstellungen: oben rechts jetzt ein ✕ zum Schließen (statt „Fertig“) – immer sichtbar, gleich wie in DammBoard.

## 1.6.4
- **Minimalmodus** (Einstellungen → Startbildschirm): nur Hintergrundbild, große Uhr und drei große Knöpfe – Tafel (DammBoard), Timer (DammZeit) und Quelle umschalten. Alles andere bleibt gespeichert; langer Druck öffnet die Einstellungen.
- **Uhrzeit vom Zeitserver** (Einstellungen → Verwaltung): Standard PTB (amtliche Zeit). Geht die Board-Uhr falsch, zeigen die Uhren des Launchers trotzdem die richtige Zeit; mit der Erlaubnis WRITE_SECURE_SETTINGS (einmal per adb) stellt er auch die Systemuhr.
- **Quelle beim Einschalten** (Einstellungen → Bedienung): wartet nach dem Einschalten, bis der gewählte Eingang Bild liefert, und schaltet dann um – nur auf Geräten, die Apps ihre Eingänge melden.

## 1.6.3
- **Kein schwarzer Balken mehr unten** (auf Clevertouch-Boards mit „⋮“): der Startbildschirm malt randlos hinter die Navigationsleiste wie andere Apps; Raster, Dock, „Alle Apps“, Einstellungen und Fenster rücken darüber.

## 1.6.2
- **Einstellungen neu gegliedert**: Übersicht mit sechs Bereichen (Aussehen, Startbildschirm, Bedienung, Schule,
  Verwaltung, Über) im Stil der Android-Einstellungen, jede Seite mit ihren Abschnitten; Zurück-Taste führt zur Übersicht.
- **Fotos aus dem Schul-Ordner** (DammLauncher/Wallpaper): jeden Tag das nächste, auf allen Boards dasselbe.
- **Aktuelles Hintergrundbild behalten**: das Foto wechselt nicht mehr täglich.
- App-Symbole aus dem Schul-Ordner (DammLauncher/Symbole).

## 1.6.0
- **Clevertouch-Aussehen**: Kopfzeile mit Uhr, Suche und Schnelleinstellungen, Dock, Farben aus dem
  Hintergrund, Milchglas, einheitliche Symbolformen – ohne Animationen.
- **Seitenleiste** (auch über anderen Apps) mit Abdecken, Whiteboard, Timer und eigenen Apps.
- Neue Kacheln: Stundenplan, Lautstärke, Zufallsname/-zahl, Bildschirm teilen, Schnelleinstellungen.
- **Profile** je Fach oder Klasse (auch automatisch nach Stundenplan), **zeitgesteuerte Nachrichten**,
  **Aufräumen**, **Tafel-Übersicht** mit Statusberichten und Absturzberichten.
- Einstellungen links, höchstens ein Drittel der Breite – auch auf 90 Zoll gut erreichbar.
- Technisch: PIN-Prüfung im Hintergrund (kein Hänger mehr), eigener Faden fürs Netz, weniger
  Systemabfragen, Apps starten ohne Animation, Grenzen beim Import, diverse Fehler behoben.

## 1.0.0
- Erste Version: frei einstellbares Raster (1–10 × 1–10) mit Rändern und Abständen, App-Übersicht
  mit Suche, Widgets, Hintergrund, großes Einstellungs-Panel mit Live-Vorschau, PIN, Bearbeiten-Sperre,
  Name und Symbol von Apps ändern, Export/Import, Boss-Modus mit zentraler Verteilung über IServ (WebDAV).
- Updates über GitHub mit **Update-Pflicht**: Ist eine neuere Version länger als 7 Tage verfügbar,
  sperrt sich die alte Version, bis aktualisiert ist. Ist GitHub nicht erreichbar, wird nie gesperrt.
