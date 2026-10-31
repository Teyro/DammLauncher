#!/usr/bin/env python3
"""Oberflächentest im CI-Emulator (screenshots.yml): Startbildschirm, App-Übersicht, Apps ablegen,
Menü, Name/Symbol ändern, Einstellungen mit Live-Vorschau, Widget, Boss-Modus mit Hochladen und
Abgleich gegen einen Test-WebDAV-Server, Update-Sperre und PIN."""
import os
import subprocess
import sys
import time
import urllib.request
import xml.etree.ElementTree as ET

PAKET = "de.oejendorferdamm.dammlauncher"
ORDNER = "screenshots"


def adb(*args):
    return subprocess.run(["adb", *args], capture_output=True, text=True)


def screenshot(name):
    daten = subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True).stdout
    with open(os.path.join(ORDNER, name), "wb") as f:
        f.write(daten)
    print("Screenshot:", name)


def knoten_alle():
    for _ in range(4):
        if adb("shell", "uiautomator", "dump", "/sdcard/d.xml").returncode == 0 and adb("pull", "/sdcard/d.xml", "d.xml").returncode == 0:
            try:
                return list(ET.parse("d.xml").iter("node"))
            except ET.ParseError:
                pass
        time.sleep(1.5)
    return []


def grenzen(k):
    return [int(v) for v in k.get("bounds").replace("][", ",").strip("[]").split(",")]


def knoten(*namen, teil=False):
    for k in knoten_alle():
        t, d = k.get("text") or "", k.get("content-desc") or ""
        for n in namen:
            if t == n or d == n or (teil and (n in t or n in d)):
                return grenzen(k)
    return None


def mitte(z):
    return (z[0] + z[2]) // 2, (z[1] + z[3]) // 2


def tap(x, y):
    adb("shell", "input", "tap", str(x), str(y))


def tippe(*namen, teil=False):
    z = knoten(*namen, teil=teil)
    if z is None:
        print("WARNUNG: nicht gefunden:", namen, file=sys.stderr)
        return False
    tap(*mitte(z))
    return True


def lang(x, y, ms=1400):
    adb("shell", "input", "swipe", str(x), str(y), str(x), str(y), str(ms))


def bildschirm():
    zeilen = adb("shell", "wm", "size").stdout.splitlines()
    for z in sorted(zeilen, key=lambda z: "Override" not in z):
        if "x" in z:
            w, h = z.split()[-1].split("x")
            w, h = int(w), int(h)
            return max(w, h), min(w, h)
    return 1920, 1080


def eingaben():
    return [grenzen(k) for k in knoten_alle() if k.get("class") == "android.widget.EditText"]


def tippe_text(feld, text):
    tap(*mitte(feld))
    time.sleep(0.7)
    adb("shell", "input", "text", text)
    time.sleep(0.5)
    time.sleep(0.5)


def zwei_felder(felder, a, b):
    """Erstes der beiden letzten Felder antippen, tippen, mit Tab ins zweite, tippen."""
    tap(*mitte(felder[-2]))
    time.sleep(2.5)  # bis Fokus und Tastatur wirklich da sind
    adb("shell", "input", "text", a)
    time.sleep(0.4)
    adb("shell", "input", "keyevent", "61")  # Tab → nächstes Feld
    time.sleep(0.4)
    adb("shell", "input", "text", b)
    time.sleep(0.6)


def feld_mit_hinweis(hinweis, text):
    """Leeres Eingabefeld über seinen Hinweistext finden (uiautomator meldet ihn als Text), antippen, tippen."""
    z = knoten(hinweis)
    if z is None:
        print("WARNUNG: Feld nicht gefunden:", hinweis, file=sys.stderr)
        return False
    tap(*mitte(z))
    time.sleep(2)
    adb("shell", "input", "text", text)
    time.sleep(0.8)
    return True


def scrolle_zu(name, x, teil=False, antippen=True):
    w, h = bildschirm()
    for _ in range(40):  # die Einstellungen sind seit 1.6 lang
        z = knoten(name, teil=teil)
        if z is not None and 120 < mitte(z)[1] < h - 120:
            if antippen:
                tap(*mitte(z))
            return True
        adb("shell", "input", "swipe", str(x), str(h * 3 // 4), str(x), str(h // 3), "600")
        time.sleep(1.2)
    print("WARNUNG: nicht gefunden (gescrollt):", name, file=sys.stderr)
    return False


def tastatur_zu():
    """Bildschirmtastatur schließen – nur wenn sie offen ist (sonst schlösse Zurück das Fenster)."""
    if "mInputShown=true" in adb("shell", "dumpsys", "input_method").stdout:
        adb("shell", "input", "keyevent", "4")
        time.sleep(1)


def geste_leiste():
    """Lage der unteren Gesten-Leiste (Overlay-Fenster) aus dumpsys window – oder None."""
    import re
    text = adb("shell", "dumpsys", "window", "windows").stdout
    i = text.find("DammLauncher-Geste")
    if i < 0:
        return None
    # nur Rahmen, die nicht den ganzen Bildschirm umfassen (Eltern-/Anzeigefenster überspringen)
    w, h = bildschirm()
    for m in re.finditer(r"(?:mFrame|frame)=\[(\d+),(\d+)\]\[(\d+),(\d+)\]", text[i:i + 6000]):
        r = [int(x) for x in m.groups()]
        if (r[3] - r[1]) < h // 4 and (r[2] - r[0]) > 0:
            return r
    return None


def steuer(befehl):
    try:
        urllib.request.urlopen("http://127.0.0.1:8766/_steuer/" + befehl, timeout=5).read()
    except Exception as e:
        print("WARNUNG: Steuerung", befehl, e, file=sys.stderr)


def home():
    adb("shell", "input", "keyevent", "3")
    time.sleep(3)


def laeuft():
    r = adb("shell", "pidof", PAKET)
    return r.returncode == 0 and r.stdout.strip() != ""


APPS = ["Settings", "Einstellungen", "Clock", "Uhr", "Contacts", "Kontakte", "Files", "Dateien", "Calendar", "Kalender", "Camera", "Kamera", "Chrome", "Phone", "Messages", "Gallery", "Photos"]


def main():
    os.makedirs(ORDNER, exist_ok=True)
    w, h = bildschirm()
    server = subprocess.Popen([sys.executable, os.path.join(os.path.dirname(os.path.abspath(__file__)), "testserver.py")])
    adb("reverse", "tcp:8766", "tcp:8766")
    adb("shell", "settings", "put", "secure", "immersive_mode_confirmations", "confirmed")
    adb("shell", "cmd", "package", "set-home-activity", f"{PAKET}/.HomeActivity")
    home()
    adb("shell", "am", "start", "-n", f"{PAKET}/.HomeActivity")
    time.sleep(4)
    if not laeuft():
        print("FEHLER: Launcher läuft nicht", file=sys.stderr)
        sys.exit(1)
    screenshot("00_standard_einrichtung.png")  # Schul-Standard: fehlende Apps als Platzhalter

    # Aus der Mitte nach oben wischen → Alle Apps
    adb("shell", "input", "swipe", str(w * 58 // 100), str(h * 70 // 100), str(w * 58 // 100), str(h * 25 // 100), "400")
    time.sleep(2)
    screenshot("00b_wischen_alle_apps.png")
    adb("shell", "input", "keyevent", "4")
    time.sleep(1)

    # Platzhalter antippen → Hinweis „fehlt“
    if tippe("Anton (nicht installiert)", "DammBoard (nicht installiert)"):
        time.sleep(1.5)
        screenshot("00c_platzhalter.png")
        tippe("Schließen")
        time.sleep(1)

    # Uhr einstellen: Datum oben, als Zahl, Grundschrift fett
    uhr = knoten("Uhr")
    if uhr:
        lang(*mitte(uhr))
        time.sleep(1.5)
        if tippe("Uhr einstellen …"):
            time.sleep(1.5)
            tippe("Oben")
            time.sleep(0.8)
            tippe("16.10.2026")
            time.sleep(0.8)
            tippe("Grundschrift fett")
            time.sleep(1)
            screenshot("00d_uhr_einstellen.png")
            tippe("Fertig")
            time.sleep(1.5)
            screenshot("00e_uhr_eingestellt.png")

    # 1.6.0: Clevertouch-Aussehen mit allen neuen Kacheln, Dock, Seitenleiste und Nachricht
    adb("shell", "appops", "set", PAKET, "SYSTEM_ALERT_WINDOW", "allow")
    adb("shell", "am", "start", "-n", f"{PAKET}/.TestEinrichtungActivity", "--ez", "neu16", "true")
    time.sleep(3)
    home()
    time.sleep(4)
    screenshot("40_clevertouch.png")
    if tippe("Apps durchsuchen"):
        time.sleep(2)
        screenshot("41_clevertouch_suche.png")
        tastatur_zu()
        adb("shell", "input", "keyevent", "4")
        time.sleep(1.5)
    if tippe("Zufall – antippen", teil=True):
        time.sleep(1)
        screenshot("42_zufall.png")
    if tippe("Schnelleinstellungen"):
        time.sleep(1.5)
        screenshot("43_schnelleinstellungen.png")
        tippe("Fertig")
        time.sleep(1)
    # Seitenleiste: Griff am rechten Rand (Überlagerung – nicht im Oberflächenbaum)
    tap(w - 8, h // 2)
    time.sleep(2)
    screenshot("44_seitenleiste.png")
    tap(w // 4, h // 2)  # außerhalb tippen schließt
    time.sleep(1)
    if tippe("Schnelleinstellungen"):
        time.sleep(1.5)
        tippe("▬  Abdecken")
        time.sleep(2.5)
        screenshot("45_abdecken.png")
        f = w / 1920
        tap(int(w - 80 * f), int(80 * f))  # ✕
        time.sleep(1.5)
        screenshot("46_abdecken_zu.png")
    # Langer Druck auf eine freie Zelle (unten in der Mitte) → Einstellungen, links und schmal
    lang(int(w * 0.55), int(h * 0.64))
    time.sleep(1.5)
    screenshot("47_clevertouch_einstellungen.png")
    adb("shell", "input", "keyevent", "4")
    time.sleep(1)

    # Für die folgenden Schritte: schlichte Einrichtung (6 × 4, Knopf „Alle Apps“)
    adb("shell", "am", "start", "-n", f"{PAKET}/.TestEinrichtungActivity", "--ez", "leer", "true")
    time.sleep(3)
    screenshot("01_leer.png")

    # App-Übersicht: Apps auf den Startbildschirm legen
    gelegt = 0
    for name in APPS:
        if gelegt >= 5:
            break
        tippe("Alle Apps")
        time.sleep(2)
        if gelegt == 0:
            screenshot("02_uebersicht.png")
        z = knoten(name)
        if z is None:
            adb("shell", "input", "keyevent", "4")
            time.sleep(1)
            continue
        lang(*mitte(z))
        time.sleep(1.5)
        if tippe("Zum Startbildschirm hinzufügen"):
            gelegt += 1
        time.sleep(1.5)
        adb("shell", "input", "keyevent", "4")
        time.sleep(1)
    screenshot("03_apps_abgelegt.png")

    # Suche
    tippe("Alle Apps")
    time.sleep(2)
    felder = eingaben()
    if felder:
        tippe_text(felder[0], "se")
        time.sleep(1)
        screenshot("04_suche.png")
    adb("shell", "input", "keyevent", "4")
    time.sleep(1)
    adb("shell", "input", "keyevent", "4")
    time.sleep(1)

    # Langer Druck auf die erste App ohne Bewegung → Menü → Name und Symbol ändern
    erste = None
    for name in APPS:
        erste = knoten(name)
        if erste:
            break
    if erste:
        lang(*mitte(erste))
        time.sleep(1.5)
        screenshot("05_menue.png")
        if tippe("Name und Symbol ändern …"):
            time.sleep(1.5)
            felder = eingaben()
            if felder:
                tippe_text(felder[0], "Testname")
                adb("shell", "input", "keyevent", "111")  # Tastatur zu (Fenster bleibt)
                time.sleep(0.6)
            if tippe("Symbol einer anderen App …"):
                time.sleep(2)
                for name in reversed(APPS):
                    if tippe(name):
                        break
                time.sleep(2.5)
            screenshot("06_name_symbol.png")
            tippe("Speichern")
            time.sleep(2)
        screenshot("07_umbenannt.png")
        # Ziehen (nur wenn der Emulator einzelne Berührungen kennt: Android 10+)
        if "motionevent" in adb("shell", "input").stdout + adb("shell", "input").stderr:
            z = knoten("Testname") or erste
            x, y = mitte(z)
            adb("shell", "input", "motionevent", "DOWN", str(x), str(y))
            time.sleep(1.5)
            for i in range(1, 11):
                adb("shell", "input", "motionevent", "MOVE", str(x + i * w // 40), str(y + i * h // 30))
            time.sleep(0.5)
            screenshot("08_ziehen.png")
            adb("shell", "input", "motionevent", "UP", str(x + w // 4), str(y + h // 3))
            time.sleep(1.5)
            screenshot("09_verschoben.png")

    # Einstellungen: langer Druck auf freie Fläche (unten rechts ist im 6×4-Raster leer)
    lang(w * 85 // 100, h * 70 // 100)
    time.sleep(2)
    screenshot("10_einstellungen.png")
    tippe("+")  # erster „+“ = Spalten
    time.sleep(0.6)
    tippe("+")
    time.sleep(1)
    screenshot("11_spalten_live.png")
    pass  # Panel steht links (Standard)
    time.sleep(1)
    screenshot("12_panel_rechts.png")

    # Widget hinzufügen
    if scrolle_zu("Widget hinzufügen …", w // 6):
        time.sleep(2)
        screenshot("13_widget_liste.png")
        # erstes Android-Widget (nicht die eingebaute Uhr, die steht ganz oben)
        eintrag = next((grenzen(k) for k in knoten_alle() if "×" in (k.get("text") or "") and "Digitaluhr" not in (k.get("text") or "")), None)
        if eintrag:
            tap(*mitte(eintrag))
            time.sleep(2.5)
            for k in knoten_alle():
                if k.get("class") == "android.widget.CheckBox":
                    tap(*mitte(grenzen(k)))
                    break
            time.sleep(0.5)
            tippe("CREATE", "Create", "ERSTELLEN", "Erstellen")
            time.sleep(3)
            if not laeuft() or knoten("Einstellungen") is None:
                adb("shell", "input", "keyevent", "4")  # Einrichten-Fenster eines Widgets
                time.sleep(2)
    tippe("Fertig")
    time.sleep(2)
    screenshot("14_mit_widget.png")

    # Inhalt eines Android-Widgets vergrößern (Zoom)
    hv = next((grenzen(k) for k in knoten_alle() if "AppWidgetHostView" in (k.get("class") or "")), None)
    if hv is None:
        print("WARNUNG: kein Android-Widget gefunden", file=sys.stderr)
    else:
        lang(*mitte(hv))
        time.sleep(1.5)
        if tippe("Inhalt vergrößern …"):
            time.sleep(1.5)
            for _ in range(4):
                tippe("+")
                time.sleep(0.6)
            screenshot("14b_widget_zoom.png")
            tippe("Fertig")
            time.sleep(1.5)
        else:
            adb("shell", "input", "keyevent", "4")
            time.sleep(1)

    # Boss-Modus + zentrale Verteilung über den Test-WebDAV
    adb("shell", "am", "start", "-n", f"{PAKET}/.TestEinrichtungActivity",
        "--es", "server", "http://127.0.0.1:8766/webdav", "--es", "ordner", "Files/DammLauncher",
        "--es", "benutzer", "lesen", "--es", "passwort", "lesen123")
    time.sleep(3)
    lang(w * 85 // 100, h * 70 // 100)
    time.sleep(2)
    pass  # Panel steht links (Standard)
    time.sleep(1)
    if scrolle_zu("Boss-Modus …", w // 6):
        time.sleep(1.5)
        if feld_mit_hinweis("Master-Passwort (mindestens 8 Zeichen)", "Masterpass1"):
            feld_mit_hinweis("Wiederholen", "Masterpass1")
            screenshot("15a_master_passwort.png")
            tippe("Festlegen")
            time.sleep(4)
    if scrolle_zu("Als Vorlage für alle Geräte hochladen", w // 6, antippen=False):
        screenshot("15_boss_modus.png")
        # zuerst mit dem LESE-Zugang: muss abgelehnt werden
        for benutzer, passwort, bild in (("lesen", "lesen123", "16_hochladen_nur_lesen.png"), ("schreiben", "schreib123", "17_hochgeladen.png")):
            scrolle_zu("Als Vorlage für alle Geräte hochladen", w // 6)
            time.sleep(1.5)
            if feld_mit_hinweis("Benutzername (Schreibzugang)", benutzer):
                feld_mit_hinweis("Passwort", passwort)
                tippe("Hochladen")
                time.sleep(4)
            screenshot(bild)
    # Vorlage „auf dem Server geändert“ (5 Spalten) → Jetzt synchronisieren
    steuer("neuer")
    for _ in range(8):  # zurück nach oben scrollen
        adb("shell", "input", "swipe", str(w // 6), str(h // 3), str(w // 6), str(h * 3 // 4), "400")
        time.sleep(0.6)
    if scrolle_zu("Jetzt synchronisieren", w // 6):
        time.sleep(5)
        screenshot("18_abgeglichen.png")
    tippe("Fertig")
    time.sleep(2)
    screenshot("19_nach_abgleich.png")

    # Foto des Tages: erst Wikimedia Commons (ohne Schlüssel), dann Unsplash mit Schlüssel
    lang(w * 85 // 100, h * 70 // 100)
    time.sleep(2)
    pass  # Panel steht links (Standard)
    time.sleep(1)
    if scrolle_zu("Jeden Tag ein neues Landschaftsfoto", w // 6):
        time.sleep(6)
        scrolle_zu("Neues Foto jetzt", w // 6, antippen=False)
        screenshot("19_commons_geladen.png")
        tippe("Unsplash (Schlüssel)")
        time.sleep(2)
        scrolle_zu("Unsplash Access Key", w // 6, antippen=False)
        if feld_mit_hinweis("Unsplash Access Key", "TestSchluessel_0123456789abc"):
            screenshot("19a_unsplash_einstellungen.png")
            tastatur_zu()
            scrolle_zu("Speichern", w // 6)
            time.sleep(6)
            scrolle_zu("Neues Foto jetzt", w // 6, antippen=False)
            screenshot("19b_unsplash_geladen.png")
    tippe("Fertig")
    time.sleep(2)
    screenshot("19c_unsplash_startbildschirm.png")
    # Uhr: über das Menü auf „transparent“ umstellen
    uhr = knoten("Uhr")
    if uhr:
        lang(*mitte(uhr))
        time.sleep(1.5)
        if tippe("Uhr einstellen …"):
            time.sleep(1.5)
            tippe("Transparent")
            time.sleep(1)
            tippe("Fertig")
            time.sleep(2)
            screenshot("19e_uhr_transparent.png")
        else:
            adb("shell", "input", "keyevent", "4")
            time.sleep(1)
    else:
        print("WARNUNG: Uhr nicht gefunden", file=sys.stderr)
    # Randgeste über einer anderen App (nicht die Einstellungen – über denen blendet Android fremde
    # Leisten absichtlich aus): Uhr-App öffnen, vom unteren Rand nach oben wischen → Launcher
    adb("shell", "appops", "set", PAKET, "SYSTEM_ALERT_WINDOW", "allow")
    home()
    time.sleep(2)
    adb("shell", "am", "start", "-a", "android.intent.action.SHOW_ALARMS")
    time.sleep(3)
    leiste = geste_leiste()
    if leiste is None:
        print("WARNUNG: Gesten-Leiste nicht gefunden", file=sys.stderr)
    else:
        x = (leiste[0] + leiste[2]) // 2
        y = (leiste[1] + leiste[3]) // 2
        print("Gesten-Leiste:", leiste)
        screenshot("19f_andere_app.png")
        adb("shell", "input", "swipe", str(x), str(y), str(x), str(h // 2), "350")
        time.sleep(2.5)
        screenshot("19g_randgeste_zurueck.png")
        if PAKET not in adb("shell", "dumpsys", "window").stdout.split("mCurrentFocus", 1)[-1][:300]:
            print("WARNUNG: Randgeste hat nicht zum Launcher geführt", file=sys.stderr)
    home()

    # Eine App über die Übersicht starten → erscheint unter „Häufig benutzt“
    tippe("Alle Apps")
    time.sleep(2)
    if tippe("Clock", "Uhr"):
        time.sleep(3)
    home()
    tippe("Alle Apps")
    time.sleep(2)
    screenshot("19d_uebersicht_dunkel.png")
    # App ausblenden (ohne PIN direkt)
    z = knoten("Camera") or knoten("Kamera")
    if z:
        lang(*mitte(z))
        time.sleep(1.5)
        if tippe("In der Übersicht ausblenden"):
            time.sleep(2)
            screenshot("19h_app_ausgeblendet.png")
    adb("shell", "input", "keyevent", "4")
    time.sleep(1)
    # Uhr mit Sekunden
    uhr = knoten("Uhr")
    if uhr:
        lang(*mitte(uhr))
        time.sleep(1.5)
        if tippe("Uhr einstellen …"):
            time.sleep(1.5)
            tippe("11:42:30 (mit Sekunden)")
            time.sleep(1)
            tippe("Fertig")
            time.sleep(2)
            screenshot("19i_uhr_sekunden.png")

    # 1.5.0: Pausen-Countdown, „Heute fehlen …“ (Zahl aus abwesend.json), QR-Code, Ordner, Hinweis-Band
    adb("shell", "am", "start", "-n", f"{PAKET}/.TestEinrichtungActivity", "--ez", "neu15", "true", "--es", "klasse", "5a")
    time.sleep(3)
    home()
    time.sleep(5)
    screenshot("22_kacheln_ordner_hinweis.png")
    if tippe("Ordner Mathe", teil=True):
        time.sleep(2)
        screenshot("23_ordner_offen.png")
        adb("shell", "input", "keyevent", "4")
        time.sleep(1)
    z = knoten("Ordner Mathe", teil=True)
    if z:
        lang(*mitte(z))
        time.sleep(1.5)
        screenshot("23b_ordner_menue.png")
        adb("shell", "input", "keyevent", "4")
        time.sleep(1)
    # Neues Widget-Menü mit den eingebauten Kacheln
    lang(w * 85 // 100, h * 80 // 100)
    time.sleep(2)
    # Panel steht links (Standard) – dort wird gescrollt
    time.sleep(1)
    if scrolle_zu("Widget hinzufügen …", w // 6):
        time.sleep(2)
        screenshot("23c_widget_liste_neu.png")
        adb("shell", "input", "keyevent", "4")
        time.sleep(1)
    # Schüler-Ansicht einschalten und wechseln (Panel neu öffnen – es steht sonst weiter unten)
    tippe("Fertig")
    time.sleep(1.5)
    lang(w * 85 // 100, h * 80 // 100)
    time.sleep(2)
    pass  # Panel steht links (Standard)
    time.sleep(1)
    if scrolle_zu("Schüler-Ansicht einschalten", w // 6):
        time.sleep(1.5)
        scrolle_zu("Jetzt zur Schüler-Ansicht", w // 6, antippen=False)
        screenshot("24_schueler_einstellungen.png")
        # Ordner nur für Lehrkräfte – erst zur Schüler-Ansicht, wenn markiert
        tippe("Fertig")
        time.sleep(1.5)
        z = knoten("Ordner Mathe", teil=True)
        if z:
            lang(*mitte(z))
            time.sleep(1.5)
            tippe("🙈  In der Schüler-Ansicht ausblenden")
            time.sleep(1.5)
        screenshot("25_lehrer_ansicht.png")
        tippe("Lehrer-Ansicht  ✕")
        time.sleep(2)
        screenshot("26_schueler_ansicht.png")
        lang(w * 85 // 100, h * 80 // 100)  # ohne PIN: gleich zurück zur Lehrer-Ansicht
        time.sleep(2)
        screenshot("27_zurueck_lehrer.png")
    else:
        tippe("Fertig")
        time.sleep(1)
    # Feierabend-Sperre: ab jetzt gesperrt, „Entsperren“ (ohne PIN) gibt frei
    adb("shell", "am", "start", "-n", f"{PAKET}/.TestEinrichtungActivity", "--ez", "feierabend", "true")
    time.sleep(3)
    home()
    time.sleep(3)
    screenshot("28_feierabend.png")
    if tippe("Entsperren"):
        time.sleep(2)
        screenshot("29_entsperrt.png")
    # Bildschirmschoner nach 1 Minute
    adb("shell", "am", "start", "-n", f"{PAKET}/.TestEinrichtungActivity", "--ei", "schoner", "1")
    time.sleep(3)
    home()
    time.sleep(100)
    screenshot("30_bildschirmschoner.png")
    tap(w // 2, h // 2)
    time.sleep(2)
    screenshot("31_geweckt.png")

    # Update-Pflicht: neue Version seit 30 Tagen → Sperre; Server weg → keine Sperre
    steuer("update_alt")
    adb("shell", "am", "start", "-a", "android.settings.SETTINGS")
    time.sleep(3)
    home()
    time.sleep(4)
    screenshot("20_update_sperre.png")
    steuer("update_aus")
    adb("shell", "am", "start", "-a", "android.settings.SETTINGS")
    time.sleep(3)
    home()
    time.sleep(4)
    screenshot("21_server_weg_keine_sperre.png")

    server.terminate()
    log = adb("logcat", "-d").stdout
    with open("logcat.txt", "w") as f:
        f.write(log)
    if not laeuft():
        print("FEHLER: Launcher läuft am Ende nicht", file=sys.stderr)
        sys.exit(1)
    if "FATAL EXCEPTION" in log and f"Process: {PAKET}," in log:
        print("FEHLER: Absturz im Logcat", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
