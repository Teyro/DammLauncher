#!/usr/bin/env python3
"""Test-Server für den Emulator-Test: ein kleiner WebDAV-Ordner (wie IServ) mit Lese- und
Schreib-Benutzer und eine nachgebaute GitHub-Releases-API für die Update-Pflicht.
Steuerung: GET /_steuer/neuer (Vorlage serverseitig „neuer“ machen), /_steuer/update_alt
(eine 30 Tage alte neue Version anbieten), /_steuer/update_aus (API antwortet 503)."""
import base64
import datetime
import json
import sys
from email.utils import formatdate
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import unquote, urlparse

DATEIEN = {}
ORDNER = {"/webdav"}
NUTZER = {"lesen": ("lesen123", False), "schreiben": ("schreib123", True)}
ZUSTAND = {"update": "aus"}


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
        if self.wer() is None:
            return self.antwort(401, kopf={"WWW-Authenticate": 'Basic realm="test"'})
        if p in DATEIEN:
            return self.antwort(200, DATEIEN[p])
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
