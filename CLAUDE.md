# Soldaten Dashboard — Projektüberblick für Claude Code

Android-Dashboard-App für den persönlichen/familiären Gebrauch (ursprünglich
"Dienst-Cockpit"). Capacitor v7, eine einzelne HTML/CSS/JS-Datei als Frontend,
mehrere selbst geschriebene native Java-Module. Build läuft über GitHub
Actions, App wird per Sideload-APK verteilt (kein Play Store bisher).

App-ID: `de.gun.dienstcockpit`

## Aufbau

```
www/index.html          Die ganze App: HTML, CSS und JS in einer Datei (groß, >10.000 Zeilen)
package.json             Capacitor-Abhängigkeiten + Versionsnummer
capacitor.config.json    Capacitor-Grundeinstellungen
keystore/debug.keystore  Fester Signierschlüssel — NIE ersetzen, sonst verlangt
                         jedes Update eine Deinstallation auf allen Geräten
native/                  Eigene Java-Plugins + XML-Layouts für das Widget
scripts/                 Python-Skripte, die den Build vorbereiten (siehe unten)
.github/workflows/       Der eigentliche Build (build-apk.yml, startet bei jedem Push
                         und zusaetzlich von Hand ueber Actions)
```

## Native Module (alle in `native/`, alle unter dem Paket `de.gun.dienstcockpit`)

- **MainActivity.java** — registriert alle Plugins, leert bei erkanntem
  App-Update gezielt nur den WebView-Cache (`clearCache(true)` + `reload()`),
  NIEMALS Verzeichnisse selbst löschen — das würde auch `localStorage` treffen.
- **KontaktDatenPlugin.java** — liest Geburtstage/Jahrestage direkt aus den
  Android-Kontakten (nicht aus dem Kalender).
- **WiederholungPlugin.java** — liest wiederkehrende Termine direkt aus
  `CalendarContract.Events`, an der von Android vorberechneten
  Instanzen-Tabelle vorbei. Nötig, weil diese Tabelle bei manchen
  Wiederholungsregeln (z. B. aus FamilyWall/Apple-Sync) leer bleibt, obwohl
  der Ursprungseintrag existiert. Hat außerdem `alleKalenderIds()`, um auch
  Kalender zu benennen, die das normale Kalendermodul nicht auflistet.
- **AgendaWidget.java** / **AgendaWidgetService.java** — Homescreen-Widget
  mit scrollbarer Terminliste. Layout in `agenda_widget.xml` (Rahmen) und
  `agenda_zeile.xml` (eine Zeile). Widgets erlauben nur bestimmte
  Element-Typen — kein einfaches `<View>`, stattdessen `TextView` auch für
  Farbflächen.
- **WidgetAnstossPlugin.java** — lässt die App das Widget von JS aus zum
  Neuzeichnen anstoßen.
- **DruckPlugin.java** — Druck über Androids eigenes Druckmodul (nicht über
  `window.open`/Teilen-Menü, das funktioniert im WebView nicht zuverlässig).

Verwendetes Kalender-Fremdmodul: `@ebarooni/capacitor-calendar` (registriert
sich als `Cap.Plugins.CapacitorCalendar`).

## Build-Vorbereitungsskripte (`scripts/`)

Diese laufen automatisch als Schritte in `build-apk.yml`, lassen sich aber
auch einzeln zum Testen ausführen:

- **paket-entpacken.py** — falls eine ZIP-Datei im Projektstamm liegt, wird
  sie über die vorhandenen Dateien entpackt (der Ordner `.github` bleibt
  dabei unberührt).
- **projekt-pruefen.py** — prüft, ob alle nötigen Dateien vorhanden sind,
  bevor überhaupt gebaut wird. Bei neuen nativen Dateien hier eintragen.
- **signatur-eintragen.py** — trägt den festen Schlüssel ausdrücklich in
  `android/app/build.gradle` ein (verlässt sich NICHT auf Androids
  Debug-Standardpfad, der sich als unzuverlässig erwiesen hat).
- **widget-einrichten.py** — kopiert alle `native/*.java`, die Widget-Layouts
  und die beiden Sicherungsregeln in das von `cap add android` erzeugte
  Projekt und trägt ins Manifest ein: Widget-Receiver/-Service, **alle
  Berechtigungen** und die Sicherung ins Google-Konto. Prüft am Ende, ob
  wirklich alles angekommen ist. Bei neuen nativen Dateien hier die
  Kopierliste erweitern, bei neuen Berechtigungen die Liste
  `BERECHTIGUNGEN`.
  Die Berechtigungen standen früher ausschließlich als `sed`-Schleife in
  `build-apk.yml`. Ein lokaler Build erzeugte dadurch eine APK ohne
  Kalender-, Kontakt- und Benachrichtigungsrechte — die App startete und
  blieb stillschweigend halb funktionsfähig. Alles, was ins Manifest muss,
  gehört deshalb in dieses Skript, nicht in den Workflow.

## Lokal bauen

```bash
npm install
npx cap add android          # einmalig, falls android/ noch nicht existiert
python3 scripts/signatur-eintragen.py
python3 scripts/widget-einrichten.py
npx cap sync android
cd android
./gradlew assembleDebug      # unter Windows: gradlew.bat assembleDebug
```

Die fertige APK liegt danach unter
`android/app/build/outputs/apk/debug/app-debug.apk`.

Zwei Stolpersteine, die beim ersten lokalen Bau auftraten:

- **JDK-Fassung**: Gradle 8.11 (das Capacitor 7 mitbringt) verträgt höchstens
  Java 23. Das JBR von Android Studio ist inzwischen Java 25 und lässt den
  Build mit „Unsupported class file major version 69" scheitern, noch bevor
  Projektcode angesehen wird. Nötig ist ein JDK 21 wie in der CI, per
  `JAVA_HOME` gesetzt. Toolchains helfen nicht — das Build-Skript selbst
  wird von der startenden JVM übersetzt.
- **BOM unter Windows**: `Set-Content`/`Out-File -Encoding utf8` schreibt in
  PowerShell 5.1 eine BOM an den Dateianfang. In `build.gradle` führt das zu
  „Unexpected character" in Zeile 1. Für Dateien, die andere Werkzeuge lesen,
  `[System.IO.File]::WriteAllText` mit `UTF8Encoding($false)` verwenden.
  In der CI tritt das nicht auf, dort setzt `sed` die Version.

Eine lokal gebaute APK ist **kein GitHub-Release** — die Update-Prüfung der
App findet sie nicht. Zum Verteilen weiterhin den Workflow benutzen.

## Testen ohne echtes Gerät

```bash
node scripts/app-pruefen.js www/index.html
```

Führt das komplette JavaScript der App in einer nachgebauten
Browser-Umgebung aus und meldet synchrone wie verzögerte Fehler. Vor jeder
Änderung an `www/index.html` ausführen.

### Kalender-Regressionstest

```bash
npx playwright install chromium   # einmalig
node scripts/kalender-pruefen.js
```

Lädt die echte `www/index.html` in einem echten Browser (Playwright) und
prüft mehrere Testfälle rund um Wiederholungsregeln, ganztägige Termine und
das Monatsraster — jeweils genau die Fehlerarten, die in früheren Versionen
tatsächlich aufgetreten sind (Datums-Drift bei monatlicher Wiederholung über
Monate mit weniger Tagen, ignoriertes INTERVAL bei wöchentlicher Wiederholung
mit BYDAY, ganztägige Termine mit Millisekunden-Versatz am Ende, sowie
Laufzeitfehler beim Zeichnen des Monatsrasters). Läuft unabhängig vom Netz
(blockiert eigene Netzanfragen der App). Sinnvoll nach jeder Änderung an den
Kalenderfunktionen in `www/index.html` (`expandiereEvent`, `ganztagsSpanne`,
`renderKalender` und Umfeld).

## Bekannte Stolperfallen (bitte beachten)

- **Ganztägige Termine**: Kalender-Anbieter legen Mitternacht mal in UTC,
  mal in Ortszeit ab, und manche (beobachtet bei FamilyWall) mit einer
  Abweichung von wenigen Millisekunden. Die zentrale Funktion dafür heißt
  `ganztagsSpanne()` in `www/index.html` — alle Kalender-Lesewege sollen
  diese eine Funktion nutzen, keine eigene Berechnung mitbringen.
- **Zeichenkodierung beim Bearbeiten von Java-Dateien**: Schon einmal ist aus
  der Escape-Sequenz `\n` versehentlich ein echter Zeilenumbruch mitten in
  einem String geworden (ungültige Java-Syntax, Build brach ab). Nach jeder
  Bearbeitung einer `.java`-Datei die Klammerbilanz und auf so etwas prüfen.
- **Speicherung**: Alles läuft über `localStorage` im WebView, zusätzlich
  gespiegelt in native Preferences (`saveJSON`/`speicherSpiegeln`) als
  Rückfallebene, falls der WebView-Speicher geleert wird. Beim Start
  versucht `speicherZurueckholen()`, die reichhaltigere der beiden Fassungen
  zu übernehmen (nicht nur bei komplett fehlendem Schlüssel).
- **Anhänge (Dokumente, Aufgaben, Termine)**: Nur diese drei Bereiche haben
  Anhänge; Akte und Ablaufregister haben trotz gelegentlich anderslautender
  Texte keine Datei-Anhänge im Code. Ein neuer Anhang läuft immer zuerst durch
  die eine gemeinsame Funktion `dokDateiEinlesen()`: Bilder werden vor dem
  Speichern über ein Canvas auf max. 1600 px lange Kante und JPEG-Qualität
  0,78 heruntergerechnet (aus 5 MB werden typisch 200–400 KB), andere Dateien
  (PDF) bleiben unverändert, aber auf 2 MB begrenzt — für alle drei Bereiche
  gleich, es gibt keine strengere oder losere Variante mehr.
  In der App (`istApp`) legt `anhangAblegen()` das Ergebnis anschließend als
  echte Datei unter Capacitor `Directory.DATA` (Ordner `anhaenge/`) ab; im
  Datensatz bleibt nur noch `dateiname` als Verweis stehen, kein Base64 mehr
  im WebView-Speicher. Anzeige/Download läuft über `anhangQuelle()`, das bei
  Bedarf aus der Datei liest. Im Browser (kein Filesystem-Plugin) bleibt ein
  Anhang wie bisher inline als Data-URL (Feld `daten`) — beide Formen kommen
  nebeneinander vor und werden überall gleich behandelt (`if(a.daten) … else
  anhangQuelle(a)…`). `anhangSitzung()` verwaltet pro Formular, welche Dateien
  in der aktuellen Bearbeitung neu hinzugekommen sind, damit ein Abbrechen sie
  wieder löscht, ohne an einem nur vorübergehend entfernten, aber bereits
  gespeicherten Anhang etwas anzutasten. Löschen des ganzen Eintrags löscht
  auch dessen Anhang-Dateien — bei Aufgaben und rein lokalen Terminen (siehe
  `mitRueckgaengigLoeschen()`) immer erst NACH Ablauf der Rückgängig-Frist,
  nie sofort. Die manuelle Sicherung (Export/Import) bettet Anhänge beim
  Export wieder als Base64 ein und legt sie beim Import erneut als Datei an
  (`anhaengeFuerExport()`/`anhaengeAusImport()`); die Android-Sicherung deckt
  den Ordner `anhaenge/` über eine eigene `<include domain="file">`-Regel in
  `backup_regeln.xml`/`datenregeln.xml` ab (zusätzlich zu `domain="sharedpref"`).
- **Standardwerte beim Start**: Fast jeder Bereich schreibt beim ersten
  Aufbau seine Voreinstellung in `localStorage` (Urlaubskonto, Akte,
  Kalender u. a.). Wer prüfen will, ob eine Installation *neu* ist, muss das
  daher ganz am Anfang von `appStarten()` tun — dort steht `SETUP_BESTAND`.
  Eine später laufende Prüfung sieht immer schon Daten. Beim Testen im
  Browser dasselbe beachten: `localStorage.clear()` auf einer laufenden
  Seite wird von deren eigenen Speichervorgängen sofort wieder gefüllt —
  erst auf eine Seite ohne App wechseln, dort leeren, dann die App laden.
- **Sicherung ins Google-Konto**: Android sichert die App-Daten selbsttätig
  und spielt sie beim Einrichten eines neuen Geräts zurück. Angemeldet wird
  das in `widget-einrichten.py` über zwei Regeldateien: `backup_regeln.xml`
  (bis Android 11) und `datenregeln.xml` (ab Android 12) — **beide** sind
  nötig, sonst fehlt die Abdeckung auf einem Teil der Geräte. Gesichert wird
  `domain="sharedpref"` (der native Spiegel) sowie `domain="file"` nur für den
  Ordner `anhaenge` (die Anhang-Dateien, siehe Abschnitt "Anhänge" oben); die
  WebView-Ablage (`app_webview`) ist ausgeschlossen, weil eine
  zurückgespielte LevelDB inkonsistent werden kann — sie wird von
  `speicherZurueckholen()` ohnehin aus dem Spiegel neu aufgebaut.
  Voraussetzung ist derselbe Signierschlüssel. Rückspielung geschieht nur
  bei der Installation, nicht auf Knopfdruck.
  Eine echte Google-Drive-Anbindung wurde geprüft und verworfen: Die
  Drive-Berechtigungen gelten als sensibel, ohne Googles Überprüfung bleibt
  man im Testmodus, und dort verfallen die Token nach etwa einer Woche —
  eine „automatische" Sicherung würde also wöchentlich stehenbleiben.
- **Update-Mechanismus**: Die App lädt Updates NICHT mehr selbst herunter
  (das native Modul dafür wurde entfernt, es verursachte Abstürze).
  Stattdessen öffnet ein Knopf die GitHub-Releases-Seite im Browser
  (`window.open(url, "_system")`). Das feste Repository steht im Code als
  `UPDATE_REPO_STANDARD`.
- **Diagnose-Werkzeuge**: Im Kalendermenü gibt es „🔍 Diagnose", eine
  Rohdaten-Suche nach Titel und „Alle Termine eines Tages anzeigen" — bei
  Kalender-Fehlern immer zuerst diese nutzen, um mit echten Rohdaten zu
  arbeiten statt zu vermuten. Mehrere frühere Korrekturen an der
  Ganztags-Logik waren zunächst zu weit oder zu eng gefasst, weil auf
  Vermutung statt auf echten Daten korrigiert wurde.

## Versionierung

Bei jeder Änderung an `www/index.html`:

1. `const APP_VERSION = "X.Y-beta";` oben im Skript anpassen
2. Denselben Stand als Text bei „Version X.Y Beta · Stand TT.MM.JJJJ" im
   Menü eintragen
3. Einen neuen Eintrag ganz oben im Änderungsprotokoll (`.changelog-body`)
   ergänzen
4. `package.json` → `version` auf denselben Stand (mit drei Stellen, z. B.
   `9.3.0-beta`) setzen

## Kontext aus der bisherigen Entwicklung

Dieses Projekt wurde bisher über viele Sitzungen mit Claude im
Browser/der App weiterentwickelt (kein direkter Compiler-Zugriff dort,
daher mehrfach Build-Fehler, die erst hier lokal auffallen würden). Größere
abgeschlossene Themen: Kalendersynchronisierung (Google/Outlook/FamilyWall),
IGF/BFT/DUZ/AVZ-Rechner in der Akte bzw. unter Tools, Homescreen-Widget,
automatische Update-Prüfung. Ausführliche Historie bei Bedarf im
Browser-Chat verfügbar, nicht in dieser Datei dupliziert.
