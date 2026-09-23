/**
 * Regressionstest für Anhänge als echte Dateien (Dokumente, Aufgaben,
 * Termine — siehe "ANHÄNGE ALS ECHTE DATEIEN" in www/index.html und den
 * Abschnitt "Anhänge" in CLAUDE.md). Lädt die echte www/index.html in einem
 * echten Browser (Playwright) gegen eine nachgebaute Capacitor-Filesystem-
 * und -Preferences-Bridge (rein im Arbeitsspeicher, keine echten Dateien auf
 * der Platte) und prüft:
 *
 *  - Ein neuer Anhang wird beim Auswählen sofort als Datei abgelegt, bleibt
 *    nach dem Speichern erhalten, und der gespeicherte Datensatz enthält
 *    danach kein Base64 mehr, nur noch den Dateiverweis.
 *  - Die Vollbildansicht liest das Bild korrekt aus der Datei.
 *  - Löschen des ganzen Eintrags (Dokumente) löscht die zugehörige Datei mit.
 *  - Abbrechen nach Auswahl eines Anhangs (Dokumente) löscht die gerade erst
 *    angelegte, nie gespeicherte Datei wieder.
 *  - Löschen mit Rückgängig (Aufgaben, rein lokale Termine) verzögert die
 *    Dateilöschung bis zum Ablauf der Frist und lässt die Datei bei
 *    Rückgängig unangetastet.
 *  - Die manuelle Sicherung (Export/Import) bettet Anhänge beim Export
 *    wieder als Base64 ein und legt sie beim Import erneut als Datei an.
 *  - Verwaiste Anhang-Dateien (z. B. weil das Aufgaben-Formular ohne eigenen
 *    Abbrechen-Knopf verlassen wurde) werden beim nächsten Start entfernt,
 *    tatsächlich referenzierte Dateien bleiben dabei unangetastet
 *    (siehe verwaisteAnhaengeAufraeumen()).
 *
 * Da die ganze App als eine einzige IIFE eingebunden ist, sind ihre internen
 * Funktionen von außen nicht aufrufbar — deshalb treibt dieses Skript die
 * App wie ein echter Nutzer über echtes Rendern und Antippen, statt einzelne
 * Funktionen direkt aufzurufen (siehe auch scripts/kalender-pruefen.js).
 *
 * Aufruf:  node scripts/anhaenge-pruefen.js
 * Einmalig vorher nötig:  npm install  &&  npx playwright install chromium
 *
 * Optional: PRUEFUNG_CHROMIUM=/pfad/zu/chrome node scripts/anhaenge-pruefen.js
 * erzwingt eine bestimmte Chromium-Programmdatei statt der von Playwright
 * verwalteten (z. B. in Umgebungen mit vorinstalliertem Browser) - dieselbe
 * Variable wie bei scripts/kalender-pruefen.js.
 */
const path = require("path");
const zlib = require("zlib");

let chromium;
try {
  ({ chromium } = require("playwright"));
} catch (e) {
  console.error("Playwright ist nicht installiert. Einmalig ausführen:\n"
    + "  npm install\n  npx playwright install chromium");
  process.exit(1);
}

const SEITE = "file://" + path.resolve(__dirname, "..", "www", "index.html");

// Ein winziges rotes 2x2-PNG, ohne externe Bilddatei im Projekt zu brauchen.
function testBildBase64() {
  function chunk(tag, data) {
    const laenge = Buffer.alloc(4);
    laenge.writeUInt32BE(data.length, 0);
    const tagPuffer = Buffer.from(tag, "ascii");
    const crc = Buffer.alloc(4);
    crc.writeUInt32BE(zlib.crc32(Buffer.concat([tagPuffer, data])) >>> 0, 0);
    return Buffer.concat([laenge, tagPuffer, data, crc]);
  }
  const sig = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
  const ihdrDaten = Buffer.alloc(13);
  ihdrDaten.writeUInt32BE(2, 0);   // Breite
  ihdrDaten.writeUInt32BE(2, 4);   // Höhe
  ihdrDaten.writeUInt8(8, 8);      // Bit-Tiefe
  ihdrDaten.writeUInt8(2, 9);      // Farbtyp: RGB
  const ihdr = chunk("IHDR", ihdrDaten);
  const zeile = Buffer.concat([Buffer.from([0]), Buffer.from([255, 0, 0, 255, 0, 0])]);
  const roh = Buffer.concat([zeile, zeile]);
  const idat = chunk("IDAT", zlib.deflateSync(roh));
  const iend = chunk("IEND", Buffer.alloc(0));
  return Buffer.concat([sig, ihdr, idat, iend]).toString("base64");
}
const BILD_BASE64 = testBildBase64();

const ergebnisse = [];
function pruefen(bezeichnung, bedingung, detail) {
  ergebnisse.push({ bezeichnung, bestanden: !!bedingung, detail: detail || "" });
}

// Legt eine echte, temporäre Bilddatei an, weil <input type="file"> in
// Playwright über setInputFiles() eine tatsächliche Datei auf der Platte
// braucht, keine synthetischen File-Objekte.
const os = require("os");
const fs = require("fs");
const BILD_PFAD = path.join(os.tmpdir(), "anhaenge-pruefen-testbild.png");
fs.writeFileSync(BILD_PFAD, Buffer.from(BILD_BASE64, "base64"));

/* Baut eine frische Seite mit nachgebauter Capacitor-Bridge:
   - Preferences: funktionsfähig (sonst wartet speicherZurueckholen() bis zu
     1,5s erfolglos auf eine Bridge, siehe www/index.html).
   - Filesystem: schreibt/liest/löscht/listet rein im Arbeitsspeicher
     (window.__dateisystem, ein Map<Pfad, Base64>), keine echten Dateien.
   - isNativePlatform(): true, damit istApp gilt und die Dateispeicherung
     statt des Browser-Inline-Rückfallwegs greift.
   vorabDaten erlaubt zusätzliches Vorbelegen von localStorage und/oder
   bereits "vorhandenen" Dateien, BEVOR die Seite geladen wird (wichtig,
   da ein addInitScript nach page.goto() zu spät käme). */
async function neueSeite(browser, vorabDaten) {
  const page = await browser.newPage();
  // Nur echte, unbehandelte JS-Fehler zählen als Seitenfehler (siehe
  // kalender-pruefen.js) - blockierte Netzanfragen erzeugen selbst
  // harmlose console.error-Meldungen.
  const seitenfehler = [];
  page.on("pageerror", (err) => seitenfehler.push(err.message));
  page.on("dialog", (d) => d.accept());

  await page.addInitScript((vorab) => {
    // SETUP_BESTAND soll eine "bestehende Installation" sehen, sonst
    // erscheint der Ersteinrichtungs-Dialog und verdeckt alles.
    localStorage.setItem("nativ_einstellungen_v1", JSON.stringify({ eingerichtet: true }));
    const speicher = {};
    const dateisystem = new Map();
    window.__dateisystem = dateisystem;
    ((vorab && vorab.dateien) || []).forEach(([pfad, inhalt]) => dateisystem.set(pfad, inhalt));
    Object.keys((vorab && vorab.localStorage) || {}).forEach((k) =>
      localStorage.setItem(k, vorab.localStorage[k]));

    window.Capacitor = {
      isNativePlatform: () => true,
      Plugins: {
        Preferences: {
          get: async ({ key }) => ({ value: speicher[key] !== undefined ? speicher[key] : null }),
          set: async ({ key, value }) => { speicher[key] = value; return {}; }
        },
        Filesystem: {
          writeFile: async ({ path: p, data }) => { dateisystem.set(p, data); return { uri: "file://" + p }; },
          readFile: async ({ path: p }) => {
            if (!dateisystem.has(p)) throw new Error("ENOENT: " + p);
            return { data: dateisystem.get(p) };
          },
          deleteFile: async ({ path: p }) => { dateisystem.delete(p); return {}; },
          getUri: async ({ path: p }) => ({ uri: "file://" + p }),
          readdir: async ({ path: p }) => {
            const praefix = p.endsWith("/") ? p : p + "/";
            const namen = [...dateisystem.keys()]
              .filter((k) => k.startsWith(praefix))
              .map((k) => k.slice(praefix.length));
            return { files: namen.map((name) => ({ name })) };
          }
        }
      }
    };
  }, vorabDaten || {});

  // Kein echtes Netz nötig und keins gewünscht.
  await page.route(/^https?:/, (route) => route.abort());
  await page.goto(SEITE);
  await page.waitForFunction(() => document.getElementById("kalGrid").children.length > 0, { timeout: 10000 });
  return { page, seitenfehler };
}

async function dateisystemGroesse(page) {
  return page.evaluate(() => window.__dateisystem.size);
}

async function testDokumenteAnlegenUndAnzeigen(browser) {
  const { page, seitenfehler } = await neueSeite(browser);
  await page.click('[data-tab="tabDokumente"]');
  await page.click("#dokAddToggleBtn");
  await page.fill("#dokArt", "Testausweis");
  await page.fill("#dokBis", "31.12.2030");
  await page.setInputFiles("#dokDatei", BILD_PFAD);
  await page.waitForTimeout(400);
  const vorSubmit = await dateisystemGroesse(page);
  pruefen("Dokumente: Anhang beim Auswählen sofort als Datei abgelegt", vorSubmit === 1, "Dateien: " + vorSubmit);

  await page.click("#dokSubmitBtn");
  await page.waitForTimeout(300);
  const nachSubmit = await dateisystemGroesse(page);
  pruefen("Dokumente: Datei bleibt nach dem Speichern erhalten", nachSubmit === 1, "Dateien: " + nachSubmit);

  const gespeichertRoh = await page.evaluate(() => localStorage.getItem("dokumente_eintraege_v1"));
  pruefen("Dokumente: localStorage enthält KEIN Base64 mehr, nur den Dateiverweis",
    !gespeichertRoh.includes('"daten"') && gespeichertRoh.includes('"dateiname"'));

  await page.click("#dokListContainer .dok-kopie");
  await page.waitForTimeout(300);
  const bildSrc = await page.evaluate(() => document.getElementById("dokBildAnzeige").src);
  pruefen("Dokumente: Vollbildansicht liest Bild aus der Datei (data:-URL gesetzt)", bildSrc.startsWith("data:image"));
  await page.click("#dokBildZu");

  await page.click("#dokListContainer [data-dok-del]");
  await page.waitForTimeout(300);
  const nachLoeschen = await dateisystemGroesse(page);
  pruefen("Dokumente: Datei wird beim Löschen des Eintrags entfernt", nachLoeschen === 0, "Dateien: " + nachLoeschen);
  pruefen("Dokumente: keine Seitenfehler", seitenfehler.length === 0, seitenfehler.join(" | "));
  await page.close();
}

async function testDokumenteAbbrechen(browser) {
  const { page, seitenfehler } = await neueSeite(browser);
  await page.click('[data-tab="tabDokumente"]');
  await page.click("#dokAddToggleBtn");
  await page.fill("#dokArt", "Wird abgebrochen");
  await page.fill("#dokBis", "31.12.2030");
  await page.setInputFiles("#dokDatei", BILD_PFAD);
  await page.waitForTimeout(400);
  const vor = await dateisystemGroesse(page);
  await page.click("#dokCancelBtn");
  await page.waitForTimeout(300);
  const nach = await dateisystemGroesse(page);
  pruefen("Dokumente: Abbrechen löscht neu angelegte Anhang-Datei wieder", vor === 1 && nach === 0, "vor:" + vor + " nach:" + nach);
  pruefen("Dokumente Abbrechen: keine Seitenfehler", seitenfehler.length === 0, seitenfehler.join(" | "));
  await page.close();
}

async function todoFormOeffnen(page) {
  await page.evaluate(() => {
    const form = document.getElementById("todoForm");
    if (form.style.display === "none" && form.previousElementSibling) form.previousElementSibling.click();
  });
}

async function testAufgabenLoeschenMitFrist(browser) {
  const { page, seitenfehler } = await neueSeite(browser);
  await page.click('[data-tab="tabTodo"]');
  await todoFormOeffnen(page);
  await page.fill("#tText", "Testaufgabe mit Anhang");
  await page.setInputFiles("#tAnhangDatei", BILD_PFAD);
  await page.waitForTimeout(400);
  const vorSubmit = await dateisystemGroesse(page);
  pruefen("Aufgaben: Anhang beim Auswählen sofort als Datei abgelegt", vorSubmit === 1, "Dateien: " + vorSubmit);
  await page.click('#todoForm button[type="submit"]');
  await page.waitForTimeout(300);

  await page.click("[data-del-todo]");
  await page.waitForTimeout(300);
  const waehrendFrist = await dateisystemGroesse(page);
  pruefen("Aufgaben: Datei bleibt während der Rückgängig-Frist erhalten", waehrendFrist === 1, "Dateien: " + waehrendFrist);
  await page.waitForTimeout(6300);
  const nachAblauf = await dateisystemGroesse(page);
  pruefen("Aufgaben: Datei wird nach Ablauf der Rückgängig-Frist gelöscht", nachAblauf === 0, "Dateien: " + nachAblauf);
  pruefen("Aufgaben Löschen: keine Seitenfehler", seitenfehler.length === 0, seitenfehler.join(" | "));
  await page.close();
}

async function testAufgabenRueckgaengig(browser) {
  const { page, seitenfehler } = await neueSeite(browser);
  await page.click('[data-tab="tabTodo"]');
  await todoFormOeffnen(page);
  await page.fill("#tText", "Testaufgabe Rückgängig");
  await page.setInputFiles("#tAnhangDatei", BILD_PFAD);
  await page.waitForTimeout(400);
  await page.click('#todoForm button[type="submit"]');
  await page.waitForTimeout(300);
  await page.click("[data-del-todo]");
  await page.waitForTimeout(200);
  await page.click(".rueckgaengig-toast button");
  await page.waitForTimeout(6300);
  const nachRueckgaengig = await dateisystemGroesse(page);
  pruefen("Aufgaben: Rückgängig behält die Anhang-Datei (nicht gelöscht)", nachRueckgaengig === 1, "Dateien: " + nachRueckgaengig);
  pruefen("Aufgaben Rückgängig: keine Seitenfehler", seitenfehler.length === 0, seitenfehler.join(" | "));
  await page.close();
}

async function testTermineLoeschenMitFrist(browser) {
  // Erzeugung über die UI erfordert in der App zusätzlich das native
  // Kalender-Plugin (jeder istApp-Termin versucht beim Anlegen, sich mit
  // dem Gerätekalender zu synchronisieren, siehe nvSpeichern) - hier direkt
  // ein bereits bestehender rein lokaler Termin (kein nativId, z. B. aus
  // einer alten Fassung) mit Anhang eingerichtet, um gezielt den
  // Lösch-/Rückgängig-Pfad zu prüfen.
  const { page, seitenfehler } = await neueSeite(browser, {
    dateien: [["anhaenge/vorab.jpg", "Zg=="]],
    localStorage: {
      kalender_v1: JSON.stringify({
        quellen: [], eigene: [{
          id: "termin1", titel: "Bestehender lokaler Termin", von: "20.09.2026", ganztags: true,
          anhaenge: [{ name: "vorab.jpg", groesse: 3, typ: "image/jpeg", dateiname: "anhaenge/vorab.jpg" }]
        }]
      })
    }
  });
  await page.click('[data-tab="tabKalender"]');
  await page.waitForTimeout(150);
  await page.click("#kalFab");   // öffnet die Maske, die "eigene Termine"-Liste liegt darin
  await page.waitForTimeout(200);
  const vorLoeschen = await dateisystemGroesse(page);
  pruefen("Termine: vorab eingerichtete Anhang-Datei vorhanden", vorLoeschen === 1, "Dateien: " + vorLoeschen);

  await page.click('[data-del-nv="termin1"]');
  await page.waitForTimeout(300);
  const waehrendFrist = await dateisystemGroesse(page);
  pruefen("Termine: Datei bleibt während der Rückgängig-Frist erhalten", waehrendFrist === 1, "Dateien: " + waehrendFrist);
  await page.waitForTimeout(6300);
  const nachAblauf = await dateisystemGroesse(page);
  pruefen("Termine: Datei wird nach Ablauf der Rückgängig-Frist gelöscht", nachAblauf === 0, "Dateien: " + nachAblauf);
  pruefen("Termine Löschen: keine Seitenfehler", seitenfehler.length === 0, seitenfehler.join(" | "));
  await page.close();
}

async function testSicherungExportImport(browser) {
  const { page, seitenfehler } = await neueSeite(browser);
  await page.click('[data-tab="tabDokumente"]');
  await page.click("#dokAddToggleBtn");
  await page.fill("#dokArt", "Exporttest");
  await page.fill("#dokBis", "31.12.2030");
  await page.setInputFiles("#dokDatei", BILD_PFAD);
  await page.waitForTimeout(400);
  await page.click("#dokSubmitBtn");
  await page.waitForTimeout(300);

  await page.click("#menueBtn");
  await page.waitForTimeout(150);
  await page.click("#exportBtn");
  await page.waitForTimeout(400);
  const exportJson = await page.evaluate(() => {
    // dateiAusgeben() schreibt die JSON-Sicherung mit encoding "utf8" (kein Base64)
    for (const [pfad, inhalt] of window.__dateisystem.entries()) {
      if (pfad.endsWith(".json")) return inhalt;
    }
    return null;
  });
  pruefen("Sicherung-Export: JSON-Datei wurde erzeugt", !!exportJson);

  if (exportJson) {
    const payload = JSON.parse(exportJson);
    const anhang = payload.dokumente && payload.dokumente[0] && payload.dokumente[0].dateien && payload.dokumente[0].dateien[0];
    pruefen('Sicherung-Export: Anhang enthält wieder eingebettetes Base64 ("daten")',
      !!(anhang && anhang.daten && anhang.daten.startsWith("data:")));

    const { page: page2, seitenfehler: seitenfehler2 } = await neueSeite(browser);
    await page2.click('[data-tab="tabDokumente"]');
    await page2.evaluate((json) => {
      const blob = new Blob([json], { type: "application/json" });
      const dt = new DataTransfer();
      dt.items.add(new File([blob], "import.json", { type: "application/json" }));
      const input = document.getElementById("importFile");
      input.files = dt.files;
      input.dispatchEvent(new Event("change", { bubbles: true }));
    }, exportJson);
    await page2.waitForTimeout(600);
    const importierteDateien = await dateisystemGroesse(page2);
    pruefen("Sicherung-Import: Anhang wird in der App wieder als Datei angelegt", importierteDateien === 1, "Dateien: " + importierteDateien);
    const importRoh = await page2.evaluate(() => localStorage.getItem("dokumente_eintraege_v1"));
    pruefen("Sicherung-Import: gespeicherte Daten enthalten kein Base64 mehr, nur den Dateiverweis",
      !!importRoh && !importRoh.includes('"daten"') && importRoh.includes('"dateiname"'));
    pruefen("Sicherung-Import: keine Seitenfehler", seitenfehler2.length === 0, seitenfehler2.join(" | "));
    await page2.close();
  }
  pruefen("Sicherung-Export: keine Seitenfehler", seitenfehler.length === 0, seitenfehler.join(" | "));
  await page.close();
}

async function testVerwaisteAnhaengeAufraeumen(browser) {
  // Zwei "vorhandene" Dateien: eine wird von einer gespeicherten Aufgabe
  // referenziert (muss bleiben), die andere von nichts mehr (verwaist,
  // z. B. weil das Aufgaben-Formular ohne Speichern verlassen wurde - siehe
  // verwaisteAnhaengeAufraeumen() in www/index.html).
  const { page, seitenfehler } = await neueSeite(browser, {
    dateien: [
      ["anhaenge/referenziert.jpg", "Zg=="],
      ["anhaenge/verwaist.jpg", "Zg=="]
    ],
    localStorage: {
      todo_liste_v1: JSON.stringify({
        eintraege: [{
          id: "todo1", text: "Aufgabe mit Anhang", erledigt: false,
          anhaenge: [{ name: "referenziert.jpg", groesse: 3, typ: "image/jpeg", dateiname: "anhaenge/referenziert.jpg" }]
        }]
      })
    }
  });
  pruefen("Aufräumen: beide Dateien vor dem Start vorhanden", (await dateisystemGroesse(page)) === 2);

  // verwaisteAnhaengeAufraeumen() läuft 2s nach appStarten() verzögert im
  // Hintergrund (siehe Init-Abschnitt in www/index.html).
  await page.waitForTimeout(2800);
  const pfade = await page.evaluate(() => [...window.__dateisystem.keys()]);
  pruefen("Aufräumen: referenzierte Datei bleibt erhalten", pfade.includes("anhaenge/referenziert.jpg"), pfade.join(", "));
  pruefen("Aufräumen: verwaiste Datei wird entfernt", !pfade.includes("anhaenge/verwaist.jpg"), pfade.join(", "));
  pruefen("Aufräumen: keine Seitenfehler", seitenfehler.length === 0, seitenfehler.join(" | "));
  await page.close();
}

(async () => {
  const launchOptions = { args: ["--no-sandbox"] };
  if (process.env.PRUEFUNG_CHROMIUM) {
    launchOptions.executablePath = process.env.PRUEFUNG_CHROMIUM;
  }
  const browser = await chromium.launch(launchOptions);
  try {
    await testDokumenteAnlegenUndAnzeigen(browser);
    await testDokumenteAbbrechen(browser);
    await testAufgabenLoeschenMitFrist(browser);
    await testAufgabenRueckgaengig(browser);
    await testTermineLoeschenMitFrist(browser);
    await testSicherungExportImport(browser);
    await testVerwaisteAnhaengeAufraeumen(browser);
  } finally {
    await browser.close();
    try { fs.unlinkSync(BILD_PFAD); } catch (e) { /* egal */ }
  }

  const fehlgeschlagen = ergebnisse.filter((r) => !r.bestanden);
  ergebnisse.forEach((r) => {
    console.log((r.bestanden ? "  OK  " : "  FEHLER  ") + r.bezeichnung + (r.detail ? "  (" + r.detail + ")" : ""));
  });
  console.log("");
  if (fehlgeschlagen.length) {
    console.log(fehlgeschlagen.length + " von " + ergebnisse.length + " Pruefungen fehlgeschlagen.");
    process.exit(1);
  }
  console.log("Alle " + ergebnisse.length + " Pruefungen bestanden.");
})().catch((e) => {
  console.error("Testlauf abgebrochen:", e && e.stack || e);
  process.exit(1);
});
