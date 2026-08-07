# Dienst-Cockpit — Android-App

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
2. Name z. B. `dienst-cockpit`, Sichtbarkeit **Private**, dann **Create repository**
3. Auf der folgenden Seite auf **uploading an existing file** klicken
4. Den **gesamten Inhalt** dieses Ordners hineinziehen — wichtig: mitsamt der
   Unterordner `www`, `scripts` und `.github`

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
   **Dienst-Cockpit-APK** → herunterladen

Heraus kommt eine ZIP-Datei mit der `app-debug.apk` darin.

---

## Schritt 3 — Auf dem Handy installieren

1. ZIP auf das Handy übertragen und entpacken
2. Die `.apk` antippen
3. Android fragt nach der Erlaubnis, Apps aus dieser Quelle zu installieren → erlauben
4. Installieren

Die Warnung ist normal: Die App ist nicht über den Play Store signiert, sondern
eine sogenannte Debug-Version. Für den Eigengebrauch ist das unproblematisch.

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

## Urlaubsverrechnung

Wie gehabt über die Kennungen im Termintitel:

| Kennung | Wirkung |
|---------|---------|
| `EU`    | fest eingetragener Urlaub |
| `EU?`   | geplanter Urlaub (erst nach „Scharf schalten" abgezogen) |
| `FvD`   | Abzug vom Überstundenkonto |

Im Block **Kalenderquellen** einen Kalender als *Urlaubskalender* markieren,
danach im Block **Urlaubsverrechnung** auf *Jetzt verrechnen*.
Gezählt werden nur Werktage; Feiertage in Baden-Württemberg fallen heraus.

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
* **iOS** — das Projekt ist auf Android ausgelegt; für iPhone wäre ein Mac nötig
* **Automatische Synchronisation zwischen Geräten** — dafür bleibt der
  JSON-Export der Weg
