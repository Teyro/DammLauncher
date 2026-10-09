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
    time.sleep(0.5)
    adb("shell", "input", "text", text)
    time.sleep(0.5)


def scrolle_zu(name, x, teil=False, antippen=True):
    w, h = bildschirm()
    for _ in range(16):
        z = knoten(name, teil=teil)
        if z is not None and 120 < mitte(z)[1] < h - 120:
            if antippen:
                tap(*mitte(z))
            return True
        adb("shell", "input", "swipe", str(x), str(h * 3 // 4), str(x), str(h // 3), "600")
        time.sleep(1.2)
    print("WARNUNG: nicht gefunden (gescrollt):", name, file=sys.stderr)
    return False


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
    screenshot("01_leer.png")

    # App-Übersicht: Apps auf den Startbildschirm legen
    gelegt = 0
    for name in APPS:
        if gelegt >= 5:
            break
        tippe("▲  Alle Apps")
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
    tippe("▲  Alle Apps")
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
            adb("shell", "input", "keyevent", "111")
            time.sleep(0.5)
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
    tippe("▶")
    time.sleep(1)
    screenshot("12_panel_rechts.png")

    # Widget hinzufügen
    if scrolle_zu("Widget hinzufügen …", w * 3 // 4):
        time.sleep(2)
        screenshot("13_widget_liste.png")
        if tippe("×", teil=True):
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

    # Boss-Modus + zentrale Verteilung über den Test-WebDAV
    adb("shell", "am", "start", "-n", f"{PAKET}/.TestEinrichtungActivity",
        "--es", "server", "http://127.0.0.1:8766/webdav", "--es", "ordner", "Files/DammLauncher",
        "--es", "benutzer", "lesen", "--es", "passwort", "lesen123")
    time.sleep(3)
    lang(w * 85 // 100, h * 70 // 100)
    time.sleep(2)
    tippe("▶")
    time.sleep(1)
    if scrolle_zu("Boss-Modus …", w * 3 // 4):
        time.sleep(1.5)
        felder = eingaben()
        if len(felder) >= 2:
            tippe_text(felder[0], "Masterpass1")
            tippe_text(felder[1], "Masterpass1")
            adb("shell", "input", "keyevent", "111")
            tippe("Festlegen")
            time.sleep(4)
    if scrolle_zu("Als Vorlage für alle Geräte hochladen", w * 3 // 4, antippen=False):
        screenshot("15_boss_modus.png")
        # zuerst mit dem LESE-Zugang: muss abgelehnt werden
        for benutzer, passwort, bild in (("lesen", "lesen123", "16_hochladen_nur_lesen.png"), ("schreiben", "schreib123", "17_hochgeladen.png")):
            scrolle_zu("Als Vorlage für alle Geräte hochladen", w * 3 // 4)
            time.sleep(1.5)
            felder = eingaben()
            if len(felder) >= 2:
                tippe_text(felder[-2], benutzer)
                tippe_text(felder[-1], passwort)
                adb("shell", "input", "keyevent", "111")
                tippe("Hochladen")
                time.sleep(4)
            screenshot(bild)
    # Vorlage „auf dem Server geändert“ (5 Spalten) → Jetzt synchronisieren
    steuer("neuer")
    if scrolle_zu("Jetzt synchronisieren", w * 3 // 4):
        time.sleep(5)
        screenshot("18_abgeglichen.png")
    tippe("Fertig")
    time.sleep(2)
    screenshot("19_nach_abgleich.png")

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
    if "FATAL EXCEPTION" in log and PAKET in log:
        print("FEHLER: Absturz im Logcat", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
