#!/usr/bin/env python3
"""Test-Server für den Emulator-Test: ein kleiner WebDAV-Ordner (wie IServ) mit Lese- und
Schreib-Benutzer und eine nachgebaute GitHub-Releases-API für die Update-Pflicht.
Steuerung: GET /_steuer/neuer (Vorlage serverseitig „neuer“ machen), /_steuer/update_alt
(eine 30 Tage alte neue Version anbieten), /_steuer/update_aus (API antwortet 503)."""
import base64
import datetime
import json
import struct
import sys
import zlib
from email.utils import formatdate
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import unquote, urlparse

DATEIEN = {}
ORDNER = {"/webdav"}
NUTZER = {"lesen": ("lesen123", False), "schreiben": ("schreib123", True)}
ZUSTAND = {"update": "aus", "unsplash": 0}
UNSPLASH_SCHLUESSEL = "TestSchluessel_0123456789abc"


def landschaft(b=640, h=360):
    """Kleines PNG ohne Zusatzbibliothek: Himmel, Sonne, zwei Hügel."""
    zeilen = bytearray()
    for y in range(h):
        zeilen.append(0)
        for x in range(b):
            if y > h * 0.62 + 30 * ((x / b - 0.3) ** 2) * 4:
                p = (46, 125, 50)
            elif y > h * 0.5 + 40 * ((x / b - 0.75) ** 2) * 6:
                p = (102, 160, 90)
            elif (x - b * 0.7) ** 2 + (y - h * 0.25) ** 2 < 900:
                p = (255, 214, 102)
            else:
                t = y / h
                p = (int(90 + 120 * t), int(150 + 80 * t), int(230 - 10 * t))
            zeilen += bytes(p)

    def block(art, daten):
        return struct.pack(">I", len(daten)) + art + daten + struct.pack(">I", zlib.crc32(art + daten) & 0xFFFFFFFF)
    return (b"\x89PNG\r\n\x1a\n" + block(b"IHDR", struct.pack(">IIBBBBB", b, h, 8, 2, 0, 0, 0))
            + block(b"IDAT", zlib.compress(bytes(zeilen), 6)) + block(b"IEND", b""))


BILD = landschaft()


class H(BaseHTTPRequestHandler):
    def log_message(self, *a):
        sys.stderr.write("testserver: " + (a[0] % a[1:]) + "\n")

    def antwort(self, code, daten=b"", typ="application/octet-stream", kopf=None):
        self.send_response(code)
        self.send_header("Content-Type", typ)
        self.send_header("Content-Length", str(len(daten)))
        self.send_header("Date", formatdate(usegmt=True))
        for k, v in (kopf or {}).items():
            self.send_header(k, v)
        self.end_headers()
        self.wfile.write(daten)

    def wer(self):
        k = self.headers.get("Authorization", "")
        if not k.startswith("Basic "):
            return None
        b, _, p = base64.b64decode(k[6:]).decode().partition(":")
        n = NUTZER.get(b)
        return n[1] if n and n[0] == p else None

    def pfad(self):
        return unquote(urlparse(self.path).path).rstrip("/")

    def koerper(self):
        n = int(self.headers.get("Content-Length", 0) or 0)
        return self.rfile.read(n) if n else b""

    def do_GET(self):
        p = self.pfad()
        if p.startswith("/_steuer/"):
            befehl = p.split("/")[-1]
            if befehl == "neuer":
                for name in list(DATEIEN):
                    if name.endswith("/vorlage.json"):
                        k = json.loads(DATEIEN[name])
                        k["stand"] += 1000
                        k["spalten"] = 5
                        DATEIEN[name] = json.dumps(k).encode()
            else:
                ZUSTAND["update"] = befehl.replace("update_", "")
            return self.antwort(200, b"ok")
        if p == "/api/releases/latest":
            if ZUSTAND["update"] != "alt":
                return self.antwort(503)
            alt = (datetime.datetime.utcnow() - datetime.timedelta(days=30)).strftime("%Y-%m-%dT%H:%M:%SZ")
            r = {"tag_name": "v9.9.9", "published_at": alt, "body": "Testversion",
                 "assets": [{"name": "DammLauncher.apk", "size": 100,
                             "browser_download_url": "https://github.com/Teyro/DammLauncher/releases/download/v9.9.9/DammLauncher.apk"}]}
            return self.antwort(200, json.dumps(r).encode(), "application/json")
        if p.startswith("/unsplash/"):
            return self.unsplash(p)
        if p == "/commons/api.php":
            seite = {"imageinfo": [{"width": 6000, "height": 4000, "mime": "image/jpeg",
                                    "thumburl": "http://127.0.0.1:8766/unsplash/bild.png",
                                    "descriptionurl": "https://commons.wikimedia.org/wiki/File:Test.jpg",
                                    "extmetadata": {"Artist": {"value": "<a href='//commons.wikimedia.org/wiki/User:Kim'>Kim Lee</a>"},
                                                    "LicenseShortName": {"value": "CC BY-SA 4.0"}}}]}
            return self.antwort(200, json.dumps({"query": {"pages": [seite]}}).encode(), "application/json")
        if self.wer() is None:
            return self.antwort(401, kopf={"WWW-Authenticate": 'Basic realm="test"'})
        if p in DATEIEN:
            return self.antwort(200, DATEIEN[p])
        self.antwort(404)

    def unsplash(self, p):
        if p == "/unsplash/bild.png":
            return self.antwort(200, BILD, "image/png")
        if self.headers.get("Authorization") != "Client-ID " + UNSPLASH_SCHLUESSEL:
            return self.antwort(401, b'{"errors":["OAuth error: The access token is invalid"]}', "application/json")
        if p == "/unsplash/photos/random":
            ZUSTAND["unsplash"] += 1
            r = {"urls": {"raw": "http://127.0.0.1:8766/unsplash/bild.png?ixid=test"},
                 "user": {"name": "Erika Muster", "links": {"html": "https://unsplash.com/@erika"}},
                 "links": {"html": "https://unsplash.com/photos/test", "download_location": "http://127.0.0.1:8766/unsplash/photos/test/download"}}
            return self.antwort(200, json.dumps(r).encode(), "application/json")
        if p == "/unsplash/photos/test/download":
            return self.antwort(200, b'{"url":"x"}', "application/json")
        self.antwort(404)

    def schreibend(self):
        w = self.wer()
        if w is None:
            self.antwort(401, kopf={"WWW-Authenticate": 'Basic realm="test"'})
            return False
        if not w:
            self.antwort(403)
            return False
        return True

    def do_PUT(self):
        d = self.koerper()
        if not self.schreibend():
            return
        p = self.pfad()
        if p.rsplit("/", 1)[0] not in ORDNER:
            return self.antwort(409)
        DATEIEN[p] = d
        self.antwort(201)

    def do_MKCOL(self):
        if not self.schreibend():
            return
        p = self.pfad()
        if p in ORDNER:
            return self.antwort(405)
        ORDNER.add(p)
        self.antwort(201)

    def do_MOVE(self):
        if not self.schreibend():
            return
        von = self.pfad()
        nach = unquote(urlparse(self.headers.get("Destination", "")).path).rstrip("/")
        if von not in DATEIEN:
            return self.antwort(404)
        DATEIEN[nach] = DATEIEN.pop(von)
        self.antwort(201)

    def do_PROPFIND(self):
        if self.wer() is None:
            return self.antwort(401)
        p = self.pfad()
        self.antwort(207 if (p in DATEIEN or p in ORDNER) else 404, b"<d:multistatus xmlns:d='DAV:'/>", "application/xml")


if __name__ == "__main__":
    ThreadingHTTPServer(("127.0.0.1", 8766), H).serve_forever()
