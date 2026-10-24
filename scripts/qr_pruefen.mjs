// Gegenprobe des QR-Erzeugers: QR_AUSGABE=/tmp/qr ./gradlew testDebugUnitTest, dann
// npm install jsqr@1.4.0 && node scripts/qr_pruefen.mjs /tmp/qr
import fs from "fs"; import jsQR from "jsqr";
const dir = process.argv[2];
for (const f of fs.readdirSync(dir).sort()) {
  const [text, ...zeilen] = fs.readFileSync(dir + "/" + f, "utf8").split("\n");
  const n = zeilen.length, s = 8, rand = 4, w = (n + 2 * rand) * s;
  const px = new Uint8ClampedArray(w * w * 4).fill(255);
  for (let y = 0; y < n; y++) for (let x = 0; x < n; x++) if (zeilen[y][x] === "1")
    for (let dy = 0; dy < s; dy++) for (let dx = 0; dx < s; dx++) { const i = (((y + rand) * s + dy) * w + (x + rand) * s + dx) * 4; px[i] = px[i+1] = px[i+2] = 0; }
  const r = jsQR(px, w, w);
  const got = r ? new TextDecoder().decode(new Uint8Array(r.binaryData)) : null;
  console.log(f, "Version", (n - 17) / 4, got === text ? "OK" : "FEHLER: " + JSON.stringify(got));
}
