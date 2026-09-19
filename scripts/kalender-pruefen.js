/**
 * Regressionstest für die Kalenderlogik (Wiederholungsregeln, ganztägige
 * Termine, Monatsraster). Lädt die echte www/index.html in einem echten
 * Browser (Playwright) und prüft anhand mehrerer Testfälle, ob bereits
 * behobene Fehler nicht wieder auftreten:
 *
 *  - Monatliche Wiederholung, die auf einen Tag fällt, den der Zielmonat
 *    nicht hat (z. B. 31. Januar): das Vorkommen muss in diesem Monat
 *    ausfallen und darf nicht auf einen falschen Tag im übernächsten Monat
 *    rutschen (siehe expandiereEvent, Kommentar "Fester Anker").
 *  - Wöchentliche Wiederholung mit BYDAY und INTERVAL > 1 (z. B. alle zwei
 *    Wochen montags): darf nicht jede Woche feuern.
 *  - Ganztägige Termine, deren Ende auf UTC-Mitternacht oder wenige
 *    Millisekunden danach liegt (FamilyWall-Eigenart): dürfen nicht einen
 *    Tag zu lang erscheinen (siehe ganztagsSpanne).
 *  - Das Monatsraster muss auch mit mehreren/überlappenden Terminen ohne
 *    Laufzeitfehler zeichnen (Regression für die spurHoehe-Namenskollision
 *    aus v10.18/v10.20, die renderKalender() mitten im Aufbau abstürzen
 *    ließ, sobald ein Monat mindestens einen Termin enthielt).
 *
 * Da die ganze App als eine einzige IIFE eingebunden ist, sind ihre
 * internen Funktionen von außen nicht aufrufbar — deshalb treibt dieses
 * Skript die App wie ein echter Nutzer über echtes Rendern und Antippen,
 * statt einzelne Funktionen direkt aufzurufen.
 *
 * Aufruf:  node scripts/kalender-pruefen.js
 * Einmalig vorher nötig:  npm install  &&  npx playwright install chromium
 *
 * Optional: KALENDER_TEST_CHROMIUM=/pfad/zu/chrome node scripts/kalender-pruefen.js
 * erzwingt eine bestimmte Chromium-Programmdatei statt der von Playwright
 * verwalteten (z. B. in Umgebungen mit vorinstalliertem Browser).
 */
const path = require("path");

let chromium;
try {
  ({ chromium } = require("playwright"));
} catch (e) {
  console.error("Playwright ist nicht installiert. Einmalig ausführen:\n"
    + "  npm install\n  npx playwright install chromium");
  process.exit(1);
}

const SEITE = "file://" + path.resolve(__dirname, "..", "www", "index.html");

// Minimale, aber funktionsfaehige Preferences-Mock, damit speicherZurueckholen()
// nicht bis zu 1,5s lang erfolglos auf eine Capacitor-Bruecke wartet (siehe
// prefPlugin()/speicherZurueckholen() in www/index.html) - ohne das liefen
// fruehere Ad-hoc-Tests in dieser Sitzung faelschlich "kaputt" aus, weil der
// Testaufbau, nicht die App, zu langsam war. isNativePlatform() bleibt
// bewusst weg, damit istApp weiterhin false ist und keine native-only
// Codepfade (Geraetekalender/-kontakte) anspringen, die hier nicht
// nachgebildet sind.
function capacitorMockEinsetzen(page) {
  return page.addInitScript(() => {
    const speicher = {};
    window.Capacitor = {
      Plugins: {
        Preferences: {
          get: async ({ key }) => ({ value: speicher[key] !== undefined ? speicher[key] : null }),
          set: async ({ key, value }) => { speicher[key] = value; return {}; }
        }
      }
    };
  });
}

function kalenderDatenEinsetzen(page, kalenderV1) {
  return page.addInitScript((daten) => {
    localStorage.setItem("kalender_v1", JSON.stringify(daten));
  }, kalenderV1);
}

async function seiteLaden(browser, kalenderV1) {
  const page = await browser.newPage();
  // Nur echte, unbehandelte JS-Fehler zaehlen als Seitenfehler. Blockierte
  // Netzanfragen (siehe page.route unten) erzeugen selbst harmlose
  // console.error-Meldungen ("Failed to load resource") - die waeren sonst
  // faelschlich als Regression aufgefallen.
  const seitenfehler = [];
  page.on("pageerror", (err) => seitenfehler.push(err.message));

  // Kein echtes Netz noetig und keins gewuenscht - macht den Test schnell
  // und unabhaengig davon, ob eine Gegenstelle erreichbar ist.
  await page.route(/^https?:/, (route) => route.abort());

  await capacitorMockEinsetzen(page);
  await kalenderDatenEinsetzen(page, kalenderV1);
  await page.goto(SEITE);
  await page.waitForFunction(() => document.getElementById("kalGrid").children.length > 0, { timeout: 10000 });
  // Kalender-Reiter aktivieren - das Raster wird zwar auch im Hintergrund
  // gezeichnet, aber Navigation/Tag-Klicks brauchen sichtbare Elemente.
  await page.click('[data-tab="tabKalender"]');
  return { page, seitenfehler };
}

async function monatGehenBis(page, jahrZiel, monatZielNull) {
  // monatZielNull: 0-basiert (Januar=0), wie kalAnsichtMonat intern zaehlt.
  for (let sicherung = 0; sicherung < 36; sicherung++) {
    const text = await page.locator("#kalMonat").textContent();
    const [monatName, jahrText] = text.trim().split(/\s+/);
    const MONATE = ["Januar","Februar","März","April","Mai","Juni","Juli","August","September","Oktober","November","Dezember"];
    const monatIst = MONATE.indexOf(monatName);
    const jahrIst = parseInt(jahrText, 10);
    if (monatIst === monatZielNull && jahrIst === jahrZiel) return;
    const zielIndex = jahrZiel * 12 + monatZielNull;
    const istIndex = jahrIst * 12 + monatIst;
    // monatWechseln() sperrt sich waehrend einer Animationsphase von
    // 140ms + 190ms = 330ms gegen weitere Klicks (monWechselLaeuft, siehe
    // www/index.html) - ein zu kurzer Abstand liess hier frueh angeklickte
    // Monatswechsel stillschweigend verpuffen.
    await page.click(istIndex < zielIndex ? "#kalNext" : "#kalPrev");
    await page.waitForTimeout(400);
  }
  throw new Error("Monatsnavigation kam nicht am Ziel an (" + jahrZiel + "-" + (monatZielNull + 1) + ")");
}

async function balkenAnzahlMitTitel(page, titelAusschnitt) {
  return page.locator(".kal-balken", { hasText: titelAusschnitt }).count();
}

/* Oeffnet eine EIGENE, frische Seite fuer jede Tagesabfrage, statt auf einer
   gemeinsamen Seite mehrere Tage nacheinander anzuklicken. Grund: die
   Tagesdetail-Ansicht (#kalTagDetail) bleibt nach dem ersten Antippen eines
   Tages dauerhaft offen (position:absolute, unterer Bereich des Rasters,
   kein Schliessen-Knopf in der App) und ueberdeckt dann bei spaeteren
   Monaten/Tagen echte Terminzellen in den unteren Wochenreihen. Ein
   erzwungener Klick durch die Ueberdeckung hindurch trifft in Wirklichkeit
   die Ueberdeckung selbst, nicht die Tageszelle darunter, und liefert ein
   falsches "kein Termin gefunden". Bei einer frischen Seite ist die
   Detailansicht beim ersten Antippen immer noch geschlossen, das Problem
   entfaellt vollstaendig. */
async function tagInhaltEnthaelt(browser, kalenderV1, jahr, monatNull, tagSchluessel, text) {
  const { page, seitenfehler } = await seiteLaden(browser, kalenderV1);
  await monatGehenBis(page, jahr, monatNull);
  await page.click('[data-tag="' + tagSchluessel + '"]');
  await page.waitForTimeout(150);
  const inhalt = await page.locator("#kalTagEvents").textContent();
  await page.close();
  return { gefunden: inhalt.includes(text), seitenfehler };
}

const ergebnisse = [];
function pruefen(bezeichnung, bedingung, detail) {
  ergebnisse.push({ bezeichnung, bestanden: !!bedingung, detail: detail || "" });
}

async function testMonatlicherTagesueberlauf(browser) {
  // Historischer Fehler: cursor.setMonth() wurde kumulativ weitergeschoben,
  // wodurch der 31. Januar ueber Februar (28/29 Tage) in den Maerz rollte
  // UND sich der Fehler auf alle weiteren Vorkommen fortpflanzte. Fest
  // verankert am Ursprungstag muss stattdessen jedes Vorkommen einzeln
  // berechnet werden: 31.01., Februar entfaellt ganz, 31.03., ...
  const kalenderV1 = {
    quellen: [{
      id: "q1", name: "Testquelle", farbe: "#ff8a3d", aktiv: true,
      events: [{
        uid: "e-monatlich", titel: "MonatsUeberlaufTest",
        start: "2027-01-31T00:00:00.000Z", end: "2027-02-01T00:00:00.000Z",
        allDay: true, rrule: "FREQ=MONTHLY;INTERVAL=1;COUNT=6"
      }]
    }],
    eigene: []
  };
  const alleFehler = [];

  const jan = await tagInhaltEnthaelt(browser, kalenderV1, 2027, 0, "2027-01-31", "MonatsUeberlaufTest");
  alleFehler.push(...jan.seitenfehler);
  pruefen("Monatlich: Vorkommen am 31.01.2027 vorhanden", jan.gefunden);

  // Februar - hat keinen 31., muss ganz ausfallen
  const { page: febSeite, seitenfehler: febFehler } = await seiteLaden(browser, kalenderV1);
  await monatGehenBis(febSeite, 2027, 1);
  const febAnzahl = await balkenAnzahlMitTitel(febSeite, "MonatsUeberlaufTest");
  await febSeite.close();
  alleFehler.push(...febFehler);
  pruefen("Monatlich: kein Vorkommen im Februar 2027 (kein 31. Tag)", febAnzahl === 0,
    "gefunden: " + febAnzahl);

  // Maerz - muss wieder exakt am 31. erscheinen, nicht auf einen falschen
  // fruehen Tag verschoben (Signatur des historischen cursor-Drift-Fehlers)
  const maerzRichtig = await tagInhaltEnthaelt(browser, kalenderV1, 2027, 2, "2027-03-31", "MonatsUeberlaufTest");
  const maerzFalsch = await tagInhaltEnthaelt(browser, kalenderV1, 2027, 2, "2027-03-03", "MonatsUeberlaufTest");
  alleFehler.push(...maerzRichtig.seitenfehler, ...maerzFalsch.seitenfehler);
  pruefen("Monatlich: Vorkommen im Maerz exakt am 31., nicht verschoben",
    maerzRichtig.gefunden && !maerzFalsch.gefunden,
    "am 31.: " + maerzRichtig.gefunden + ", faelschlich am 03.: " + maerzFalsch.gefunden);

  pruefen("Monatlich: keine Seitenfehler", alleFehler.length === 0, alleFehler.join(" | "));
}

async function testWoechentlichIntervall(browser) {
  // Historischer Fehler: bei FREQ=WEEKLY mit BYDAY wurde INTERVAL ignoriert,
  // ein "alle zwei Wochen montags"-Termin erschien jede Woche.
  // 04.01.2027 ist ein Montag.
  const kalenderV1 = {
    quellen: [{
      id: "q1", name: "Testquelle", farbe: "#4dd0c4", aktiv: true,
      events: [{
        uid: "e-woechentlich", titel: "ZweiWochenTest",
        start: "2027-01-04T18:00:00.000Z", end: "2027-01-04T19:00:00.000Z",
        allDay: false, rrule: "FREQ=WEEKLY;INTERVAL=2;BYDAY=MO;COUNT=4"
      }]
    }],
    eigene: []
  };
  const woche0 = await tagInhaltEnthaelt(browser, kalenderV1, 2027, 0, "2027-01-04", "ZweiWochenTest"); // muss vorkommen
  const woche1 = await tagInhaltEnthaelt(browser, kalenderV1, 2027, 0, "2027-01-11", "ZweiWochenTest"); // darf NICHT vorkommen
  const woche2 = await tagInhaltEnthaelt(browser, kalenderV1, 2027, 0, "2027-01-18", "ZweiWochenTest"); // muss vorkommen
  const woche3 = await tagInhaltEnthaelt(browser, kalenderV1, 2027, 0, "2027-01-25", "ZweiWochenTest"); // darf NICHT vorkommen

  pruefen("Woechentlich alle 2 Wochen: richtiges Muster (Mo, -, Mo, -)",
    woche0.gefunden && !woche1.gefunden && woche2.gefunden && !woche3.gefunden,
    "04.:" + woche0.gefunden + " 11.:" + woche1.gefunden + " 18.:" + woche2.gefunden + " 25.:" + woche3.gefunden);
  pruefen("Woechentlich: keine Seitenfehler",
    [woche0, woche1, woche2, woche3].every(w => w.seitenfehler.length === 0),
    [woche0, woche1, woche2, woche3].flatMap(w => w.seitenfehler).join(" | "));
}

async function testGanztagsSpanne(browser) {
  // Historischer Fehler: ein Ende, das nicht exakt, sondern wenige
  // Millisekunden nach Mitternacht liegt (FamilyWall), liess einen
  // eintaegigen Termin faelschlich zwei Tage belegen.
  const kalenderV1 = {
    quellen: [{
      id: "q1", name: "Testquelle", farbe: "#35d488", aktiv: true,
      events: [
        {
          uid: "e-exakt", titel: "GanztagsExakt",
          start: "2027-06-10T00:00:00.000Z", end: "2027-06-11T00:00:00.000Z",
          allDay: true
        },
        {
          uid: "e-versatz", titel: "GanztagsVersatz",
          start: "2027-06-15T00:00:00.000Z", end: "2027-06-16T00:00:00.001Z",
          allDay: true
        }
      ]
    }],
    eigene: []
  };
  const exakt10 = await tagInhaltEnthaelt(browser, kalenderV1, 2027, 5, "2027-06-10", "GanztagsExakt");
  const exakt11 = await tagInhaltEnthaelt(browser, kalenderV1, 2027, 5, "2027-06-11", "GanztagsExakt");
  pruefen("Ganztags exakt: nur am 10.06., nicht am 11.06.", exakt10.gefunden && !exakt11.gefunden,
    "10.:" + exakt10.gefunden + " 11.:" + exakt11.gefunden);

  const versatz15 = await tagInhaltEnthaelt(browser, kalenderV1, 2027, 5, "2027-06-15", "GanztagsVersatz");
  const versatz16 = await tagInhaltEnthaelt(browser, kalenderV1, 2027, 5, "2027-06-16", "GanztagsVersatz");
  pruefen("Ganztags mit 1ms-Versatz: nur am 15.06., nicht am 16.06. (FamilyWall-Fall)",
    versatz15.gefunden && !versatz16.gefunden, "15.:" + versatz15.gefunden + " 16.:" + versatz16.gefunden);

  pruefen("Ganztagsspanne: keine Seitenfehler",
    [exakt10, exakt11, versatz15, versatz16].every(w => w.seitenfehler.length === 0),
    [exakt10, exakt11, versatz15, versatz16].flatMap(w => w.seitenfehler).join(" | "));
}

async function testMonatsrasterMitVielenTerminen(browser) {
  // Regression fuer die spurHoehe-Namenskollision (v10.18-v10.20): sobald ein
  // Monat mindestens einen Termin enthielt, stuerzte renderKalender() mitten
  // im Aufbau der Balken ab und liess das Monatsraster leer. Ein Monat mit
  // vielen, teils ueberlappenden Terminen prueft genau diesen Aufbau-Pfad.
  const eigene = [];
  for (let tag = 1; tag <= 20; tag++) {
    eigene.push({
      id: "t" + tag, titel: "Termin " + tag,
      von: String(tag).padStart(2, "0") + ".07.2027",
      bis: String(Math.min(tag + 2, 31)).padStart(2, "0") + ".07.2027",
      ganztags: true
    });
  }
  const kalenderV1 = { quellen: [], eigene };
  const { page, seitenfehler } = await seiteLaden(browser, kalenderV1);
  await monatGehenBis(page, 2027, 6); // Juli

  const anzahlZellen = await page.locator(".kal-tag").count();
  const anzahlBalken = await page.locator(".kal-balken").count();
  pruefen("Monatsraster mit vielen ueberlappenden Terminen zeichnet Tageszellen",
    anzahlZellen >= 28, "Zellen: " + anzahlZellen);
  pruefen("Monatsraster mit vielen ueberlappenden Terminen zeichnet Terminbalken",
    anzahlBalken >= 20, "Balken: " + anzahlBalken);
  pruefen("Monatsraster mit vielen Terminen: keine Seitenfehler (Regression spurHoehe)",
    seitenfehler.length === 0, seitenfehler.join(" | "));
  await page.close();
}

async function testLeererKalender(browser) {
  // Grundfall: ganz ohne Termine muss das Raster trotzdem erscheinen (siehe
  // den urspruenglichen Fehlerbericht "Kalender komplett leer").
  const { page, seitenfehler } = await seiteLaden(browser, { quellen: [], eigene: [] });
  const anzahlZellen = await page.locator(".kal-tag").count();
  pruefen("Leerer Kalender zeichnet das Monatsraster", anzahlZellen >= 28, "Zellen: " + anzahlZellen);
  pruefen("Leerer Kalender: keine Seitenfehler", seitenfehler.length === 0, seitenfehler.join(" | "));
  await page.close();
}

(async () => {
  const launchOptions = { args: ["--no-sandbox"] };
  if (process.env.KALENDER_TEST_CHROMIUM) {
    launchOptions.executablePath = process.env.KALENDER_TEST_CHROMIUM;
  }
  const browser = await chromium.launch(launchOptions);
  try {
    await testLeererKalender(browser);
    await testMonatlicherTagesueberlauf(browser);
    await testWoechentlichIntervall(browser);
    await testGanztagsSpanne(browser);
    await testMonatsrasterMitVielenTerminen(browser);
  } finally {
    await browser.close();
  }

  const fehlgeschlagen = ergebnisse.filter(r => !r.bestanden);
  ergebnisse.forEach(r => {
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
