# Soldaten Dashboard — Android-App

Urlaub, Überstunden, Ablaufdaten, To-do, Notizen und Kalender in einer App.
Der Kalenderteil schreibt **direkt in den Gerätekalender** — liegt dort dein
Outlook-Konto, überträgt Android die Termine selbst in die Cloud, und abonnierte
Kalender (z. B. der deiner Frau) aktualisieren sich automatisch.

---

## Was du brauchst

* Ein kostenloses GitHub-Konto
* Dein Handy — sonst nichts. Es wird **nichts** auf dem PC installiert.

---

## Schritt 1 — Projekt auf GitHub hochladen

1. Auf [github.com](https://github.com) einloggen → oben rechts **+** → **New repository**
2. Name z. B. `soldaten-dashboard`, Sichtbarkeit **Private**, dann **Create repository**
3. Auf der folgenden Seite auf **uploading an existing file** klicken
4. Den **gesamten Inhalt** dieses Ordners hineinziehen — wichtig: mitsamt der
   Unterordner `www`, `resources` und `.github`

> Falls `.github` beim Hochladen fehlt: Der Ordner beginnt mit einem Punkt und wird
> vom Datei-Explorer oft ausgeblendet. Unter Windows im Explorer unter
> *Ansicht → Ein-/ausblenden → Ausgeblendete Elemente* aktivieren.

5. Unten auf **Commit changes** klicken

---

## Schritt 2 — APK bauen lassen

1. Im Repository oben auf den Reiter **Actions**
2. Links **APK bauen** auswählen
3. Rechts auf **Run workflow** → nochmal **Run workflow**
4. Warten (beim ersten Mal etwa 5–10 Minuten). Ein grüner Haken bedeutet: fertig.
5. Den abgeschlossenen Lauf anklicken → unten unter **Artifacts** liegt
   **Soldaten Dashboard-APK** → herunterladen

Heraus kommt eine ZIP-Datei mit der `app-debug.apk` darin.

### Signierschlüssel

Im Ordner `keystore/` liegt ein fester Signierschlüssel. Dadurch tragen alle
Builds dieselbe Signatur, und eine neue Version lässt sich einfach über die
vorhandene App installieren — ohne vorher zu deinstallieren, und ohne dass die
Daten verloren gehen.

**Diese Datei muss mit ins Repository hochgeladen werden**, sonst bricht der
Build mit einer entsprechenden Meldung ab.

Der Schritt „Signatur der fertigen APK prüfen" gibt im Protokoll den
Fingerabdruck aus. Er muss bei jedem Build identisch sein.

Der Schlüssel ist für den Eigengebrauch gedacht (Weitergabe per Datei). Für eine
Veröffentlichung im Play Store wäre ein eigener, geheim gehaltener Schlüssel
nötig — dieser hier liegt offen im Projekt.

---

## Schritt 3 — Auf dem Handy installieren

1. ZIP auf das Handy übertragen und entpacken
2. Die `.apk` antippen
3. Android fragt nach der Erlaubnis, Apps aus dieser Quelle zu installieren → erlauben
4. Installieren

Die Warnung ist normal: Die App ist nicht über den Play Store signiert, sondern
eine sogenannte Debug-Version. Für den Eigengebrauch ist das unproblematisch.

---

## App-Icon

Das Symbol liegt fertig im Ordner `resources/` und wird beim Bauen automatisch
in alle Android-Größen umgerechnet:

| Datei | Zweck |
|-------|-------|
| `icon.png` | Grundsymbol (1024 x 1024) |
| `icon-foreground.png` | Emblem für adaptive Symbole, mit Rand |
| `icon-background.png` | Tarnmuster als Hintergrundebene |
| `splash.png` / `splash-dark.png` | Startbildschirm |

Android beschneidet Symbole je nach Hersteller rund oder abgerundet. Deshalb ist
das Emblem im Vordergrund auf 62 % verkleinert — so bleibt der Ring in jeder
Form vollständig sichtbar.

Zum Austauschen einfach `icon.png` ersetzen (quadratisch, mindestens
1024 x 1024) und die drei abgeleiteten Dateien löschen; der Build erzeugt dann
einfache Ersatzvarianten daraus.

---

## Schritt 4 — Kalender einrichten

**Vorher:** Dein Microsoft-Konto muss in den Android-Einstellungen unter
*Konten* eingerichtet sein — nicht nur in der Outlook-App. Nur dann darf die
System-Kalenderdatenbank hineinschreiben.

Dann in der App:

1. Reiter **Kalender** öffnen
2. Im Block **Gerätekalender** auf *Kalenderzugriff erlauben* → Berechtigung erteilen
3. Bei **Zielkalender für neue Termine** deinen Outlook-Kalender auswählen
4. Fertig — neue Termine landen direkt dort

Über *Anzuzeigende Kalender* legst du fest, welche Kalender in der Monatsansicht
erscheinen. Der freigegebene iCloud-Kalender deiner Frau taucht dort automatisch
auf, sobald er auf dem Handy abonniert ist.

---

## Kalender bedienen

| Aktion | Wirkung |
|--------|---------|
| Antippen des Emblems oben links | zwischen hell und dunkel wechseln |
| Wischen nach links/rechts | vorheriger / nächster Monat |
| Tippen auf einen Tag | Termine des Tages unten einblenden |
| **Lange gedrückt halten** | Termineingabe für diesen Tag öffnen |
| **+** unten rechts | Termineingabe öffnen |
| **⚙** oben rechts | Kalender, Farben und Standardkalender einstellen |

Termine übernehmen die Farbe ihres Kalenders. Ganztägige Termine werden flächig
dargestellt, Termine mit Uhrzeit nur mit farbiger Flagge.

### Urlaub aus dem Kalender anrechnen

Legst du einen Termin in einem Kalender an, dessen Name auf Urlaub hindeutet
(enthält „Urlaub", „EU", „FvD" oder „Abwesen"), erscheint in der Eingabemaske
zusätzlich die Auswahl **Urlaub / FvD** samt Schalter **Nur geplant**:

* **Urlaub, nur geplant** — als geplanter Urlaub eingetragen, erst nach
  „Scharf schalten" abgezogen
* **Urlaub, fest** — sofort abgezogen (Werktage ohne Feiertage BW)
* **FvD, fest** — Stunden werden vom Überstundenkonto abgezogen

---

## Widget für den Startbildschirm

Im Ordner `native/` liegen zusätzlich die Dateien für das Widget:

* `AgendaWidget.java` — zeichnet die Tagesübersicht
* `WidgetAnstossPlugin.java` — lässt die App das Widget aktualisieren
* `agenda_widget.xml`, `agenda_zeile1..8.xml` — Aufbau der Anzeige
* `agenda_widget_info.xml` — Größe und Verhalten

Das Skript `scripts/widget-einrichten.py` kopiert diese Dateien beim Bauen an
die richtige Stelle und meldet das Widget im Manifest an.

Hinzufügen auf dem Gerät: Startbildschirm lange drücken → Widgets → Soldaten
Dashboard. Darstellung und Deckkraft stellst du im Menü der App ein.

Das Widget liest die Übersicht aus dem Gerätespeicher, den die App beschreibt.
Es braucht daher selbst keinen Zugriff auf Kalender oder Kontakte. Aktualisiert
wird beim Öffnen der App, bei Rückkehr in den Vordergrund und halbstündlich
durch Android.

---

## Eigenes Kontakt-Modul

Im Ordner `native/` liegen zwei Java-Dateien, die der Build in das
Android-Projekt kopiert:

* `KontaktDatenPlugin.java` — liest Geburtstage, Jahrestage und eigene Anlässe
  aus den Kontakten
* `MainActivity.java` — meldet dieses Modul bei der App an

Nötig ist das, weil Android diese Daten in der Kontakte-Datenbank speichert. Der
Kalender „Wichtige Daten der Kontakte" ist nur eine Anzeige und liefert über die
Kalender-Schnittstelle keine Einträge.

**Beide Dateien müssen mit ins Repository**, sonst bricht der Build ab.

---

## Änderungen an der App

Du bearbeitest ausschließlich `www/index.html`. Nach jedem Hochladen einer neuen
Fassung startet der Build von selbst, und unter *Actions* liegt kurz darauf die
neue APK.

---

## Sicherung

Der Reiter **Daten → Export .json** sichert alles: Urlaub, Überstunden,
Ablaufdaten, To-dos, Notizen und Kalenderquellen. Diese Datei aufbewahren —
bei einer Neuinstallation über *Import .json* zurückspielen.

Die Daten liegen ausschließlich auf dem Gerät. Es gibt keinen Server.

Zusätzlich sichert die App alles im nativen Gerätespeicher, damit nichts verloren
geht, wenn Android den Speicher der Web-Ansicht aufräumt. Erst eine
Deinstallation entfernt diese Sicherung — dann hilft nur der JSON-Import.

---

## Wenn der Build fehlschlägt

Unter *Actions* den roten Lauf anklicken; der fehlgeschlagene Schritt lässt sich
aufklappen und zeigt die Ursache.

Häufigste Ursache sind Versionskonflikte zwischen Capacitor und dem
Kalender-Plugin. In dem Fall in der `package.json` die Zeile

```
"@ebarooni/capacitor-calendar": "^7.0.0"
```

auf `"latest"` setzen, die Datei erneut hochladen und den Build neu starten.

---

## Was die App nicht kann

* **Termine ändern** — Löschen und Neuanlegen funktioniert, direktes Bearbeiten
  eines bestehenden Termins ist noch nicht eingebaut
* **Anhänge im Kalender** — Dateianhänge liegen in der App, nicht im
  Gerätekalender. Auf anderen Geräten und bei deiner Frau erscheinen sie nicht.
* **iOS** — das Projekt ist auf Android ausgelegt; für iPhone wäre ein Mac nötig
* **Automatische Synchronisation zwischen Geräten** — dafür bleibt der
  JSON-Export der Weg
