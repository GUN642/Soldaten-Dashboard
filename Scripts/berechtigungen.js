/**
 * Trägt die Kalenderberechtigungen in die AndroidManifest.xml ein.
 *
 * Der Ordner "android/" wird erst beim Bauen erzeugt (npx cap add android),
 * deshalb kann die Berechtigung nicht fest im Projekt hinterlegt werden.
 * Dieses Skript läuft im Build-Ablauf direkt danach.
 */

const fs = require("fs");
const path = require("path");

const manifestPfad = path.join("android", "app", "src", "main", "AndroidManifest.xml");

if (!fs.existsSync(manifestPfad)) {
  console.error("FEHLER: " + manifestPfad + " nicht gefunden.");
  console.error("Wurde 'npx cap add android' vorher ausgeführt?");
  process.exit(1);
}

let manifest = fs.readFileSync(manifestPfad, "utf8");

const berechtigungen = [
  "android.permission.READ_CALENDAR",
  "android.permission.WRITE_CALENDAR",
];

const fehlende = berechtigungen.filter((b) => !manifest.includes(b));

if (fehlende.length === 0) {
  console.log("Alle Kalenderberechtigungen sind bereits eingetragen.");
} else {
  const block = fehlende
    .map((b) => '    <uses-permission android:name="' + b + '" />')
    .join("\n");

  // Vor dem schließenden </manifest> einfügen
  manifest = manifest.replace(/<\/manifest>/, block + "\n</manifest>");
  fs.writeFileSync(manifestPfad, manifest, "utf8");
  console.log("Eingetragen: " + fehlende.join(", "));
}

// Kontrolle
const kontrolle = fs.readFileSync(manifestPfad, "utf8");
berechtigungen.forEach((b) => {
  if (!kontrolle.includes(b)) {
    console.error("FEHLER: " + b + " fehlt weiterhin im Manifest.");
    process.exit(1);
  }
});
console.log("Manifest geprüft — Kalenderzugriff ist eingerichtet.");
