"""
Setzt das Widget in das erzeugte Android-Projekt ein:
  - kopiert Java-Dateien und Layouts
  - ergaenzt die Texte in strings.xml
  - meldet das Widget in der AndroidManifest.xml an

Aufruf nach 'npx cap add android'.
"""
import os
import shutil
import sys

ZIEL_JAVA = os.path.join("android", "app", "src", "main", "java", "de", "gun", "dienstcockpit")
RES = os.path.join("android", "app", "src", "main", "res")
MANIFEST = os.path.join("android", "app", "src", "main", "AndroidManifest.xml")
STRINGS = os.path.join(RES, "values", "strings.xml")

if not os.path.isdir(ZIEL_JAVA):
    print("FEHLER: %s nicht gefunden." % ZIEL_JAVA)
    print("Stimmt die appId in capacitor.config.json?")
    sys.exit(1)

# ---- Java-Dateien ----
java_dateien = [
    "KontaktDatenPlugin.java",
    "MainActivity.java",
    "AgendaWidget.java",
    "WidgetAnstossPlugin.java",
    "AgendaWidgetService.java",
    "DruckPlugin.java",
    "WiederholungPlugin.java",
]
for name in java_dateien:
    quelle = os.path.join("native", name)
    if not os.path.isfile(quelle):
        print("FEHLER: %s fehlt im Projekt." % quelle)
        sys.exit(1)
    shutil.copy(quelle, os.path.join(ZIEL_JAVA, name))
print("Java-Dateien eingesetzt: %s" % ", ".join(java_dateien))

# ---- Layouts ----
layout = os.path.join(RES, "layout")
xml = os.path.join(RES, "xml")
os.makedirs(layout, exist_ok=True)
os.makedirs(xml, exist_ok=True)

shutil.copy(os.path.join("native", "agenda_widget.xml"), layout)
shutil.copy(os.path.join("native", "agenda_zeile.xml"), layout)
shutil.copy(os.path.join("native", "agenda_tag.xml"), layout)
shutil.copy(os.path.join("native", "agenda_widget_info.xml"), xml)
print("Layouts eingesetzt (Rahmen, Zeile, Tagesueberschrift, Beschreibung)")

# ---- Sicherungsregeln (Android Auto Backup) ----
shutil.copy(os.path.join("native", "backup_regeln.xml"), xml)
shutil.copy(os.path.join("native", "datenregeln.xml"), xml)
print("Sicherungsregeln eingesetzt (backup_regeln.xml, datenregeln.xml)")

# ---- Texte ----
if os.path.isfile(STRINGS):
    inhalt = open(STRINGS, encoding="utf-8").read()
    if "widget_beschreibung" in inhalt:
        print("Widget-Texte bereits vorhanden")
    else:
        zusatz = ('    <string name="widget_name">Tagesuebersicht</string>\n'
                  '    <string name="widget_beschreibung">Termine und Aufgaben des Tages</string>\n')
        inhalt = inhalt.replace("</resources>", zusatz + "</resources>")
        open(STRINGS, "w", encoding="utf-8").write(inhalt)
        print("Widget-Texte ergaenzt")
else:
    print("HINWEIS: strings.xml nicht gefunden")

# ---- Manifest ----
if not os.path.isfile(MANIFEST):
    print("FEHLER: %s nicht gefunden." % MANIFEST)
    sys.exit(1)

inhalt = open(MANIFEST, encoding="utf-8").read()

if "AgendaWidget" in inhalt:
    print("Widget bereits im Manifest angemeldet")
else:
    block = """
        <receiver
            android:name=".AgendaWidget"
            android:exported="false">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
                <action android:name="android.intent.action.DATE_CHANGED" />
                <action android:name="android.intent.action.TIME_SET" />
                <action android:name="android.intent.action.TIMEZONE_CHANGED" />
                <action android:name="de.gun.dienstcockpit.TAGESWECHSEL" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/agenda_widget_info" />
        </receiver>

        <service
            android:name=".AgendaWidgetService"
            android:permission="android.permission.BIND_REMOTEVIEWS"
            android:exported="false" />
    </application>"""
    if "</application>" not in inhalt:
        print("FEHLER: </application> im Manifest nicht gefunden.")
        sys.exit(1)
    inhalt = inhalt.replace("    </application>", block, 1)
    if "</receiver>" not in inhalt:
        # Fallback, falls die Einrueckung abweicht
        inhalt = inhalt.replace("</application>", block.replace("    </application>", "</application>"), 1)
    open(MANIFEST, "w", encoding="utf-8").write(inhalt)
    print("Widget im Manifest angemeldet")

# ---- Berechtigungen eintragen ----
# Stand frueher ausschliesslich als sed-Schleife in build-apk.yml. Wer lokal
# baute, erhielt dadurch eine APK ohne Kalender-, Kontakt- und
# Benachrichtigungsrechte - die App startete, blieb aber stillschweigend
# halb funktionsfaehig. Deshalb hier, damit CI und lokaler Build dasselbe tun.
BERECHTIGUNGEN = [
    "READ_CALENDAR",            # Termine des Geraets lesen
    "WRITE_CALENDAR",           # Termine anlegen und aendern
    "POST_NOTIFICATIONS",       # Erinnerungen anzeigen
    "SCHEDULE_EXACT_ALARM",     # Erinnerungen zur genauen Zeit
    "USE_EXACT_ALARM",
    "RECEIVE_BOOT_COMPLETED",   # Erinnerungen ueberstehen einen Neustart
    "VIBRATE",
    "READ_CONTACTS",            # Geburtstage aus den Kontakten
    "INTERNET",                 # Wetter, Schulferien, Update-Pruefung
    "ACCESS_COARSE_LOCATION",   # Wetter am eigenen Ort
    "ACCESS_FINE_LOCATION",
    "REQUEST_INSTALL_PACKAGES", # Update ueber die heruntergeladene APK
]

inhalt = open(MANIFEST, encoding="utf-8").read()
if "</manifest>" not in inhalt:
    print("FEHLER: </manifest> im Manifest nicht gefunden.")
    sys.exit(1)

neu = []
for recht in BERECHTIGUNGEN:
    voll = "android.permission." + recht
    if voll in inhalt:
        continue
    zeile = '    <uses-permission android:name="%s" />\n' % voll
    inhalt = inhalt.replace("</manifest>", zeile + "</manifest>", 1)
    neu.append(recht)

if neu:
    open(MANIFEST, "w", encoding="utf-8").write(inhalt)
    print("Berechtigungen eingetragen: %s" % ", ".join(neu))
else:
    print("Berechtigungen bereits vollstaendig vorhanden")

# ---- Sicherung ins Google-Konto anmelden ----
# Android sichert App-Daten selbsttaetig in das Google-Konto des Nutzers und
# spielt sie beim Einrichten eines neuen Geraets zurueck. Damit dabei genau
# der Preferences-Spiegel erfasst wird (und nicht die stoeranfaellige
# WebView-Datenbank), werden die beiden Regeldateien ausdruecklich eingetragen.
# fullBackupContent gilt bis Android 11, dataExtractionRules ab Android 12 -
# beide muessen gesetzt sein, damit alle Geraete abgedeckt sind.
inhalt = open(MANIFEST, encoding="utf-8").read()

start = inhalt.find("<application")
if start == -1:
    print("FEHLER: <application> im Manifest nicht gefunden.")
    sys.exit(1)
ende = inhalt.find(">", start)
if ende == -1:
    print("FEHLER: <application>-Tag im Manifest ist unvollstaendig.")
    sys.exit(1)

tag = inhalt[start:ende]
for attribut in ["android:allowBackup", "android:fullBackupContent",
                 "android:dataExtractionRules"]:
    # vorhandene Fassung entfernen, damit nichts doppelt gesetzt wird
    while True:
        stelle = tag.find(attribut + "=")
        if stelle == -1:
            break
        auf = tag.find('"', stelle)
        zu = tag.find('"', auf + 1)
        if auf == -1 or zu == -1:
            print("FEHLER: Attribut %s im Manifest ist unvollstaendig." % attribut)
            sys.exit(1)
        tag = tag[:stelle] + tag[zu + 1:]

# Leerzeilen entfernen, die beim Herausnehmen alter Attribute zurueckbleiben
tag = "\n".join(z for z in tag.split("\n") if z.strip())

tag = tag.rstrip() + (
    '\n        android:allowBackup="true"'
    '\n        android:fullBackupContent="@xml/backup_regeln"'
    '\n        android:dataExtractionRules="@xml/datenregeln"\n    '
)
inhalt = inhalt[:start] + tag + inhalt[ende:]
open(MANIFEST, "w", encoding="utf-8").write(inhalt)
print("Sicherung ins Google-Konto im Manifest angemeldet")

# ---- Kontrolle ----
kontrolle = open(MANIFEST, encoding="utf-8").read()
for muss in ['android:allowBackup="true"', "@xml/backup_regeln", "@xml/datenregeln"]:
    if muss not in kontrolle:
        print("FEHLER: '%s' fehlt im Manifest." % muss)
        sys.exit(1)
for datei in ["backup_regeln.xml", "datenregeln.xml"]:
    if not os.path.isfile(os.path.join(xml, datei)):
        print("FEHLER: %s wurde nicht eingesetzt." % datei)
        sys.exit(1)
for recht in BERECHTIGUNGEN:
    if ("android.permission." + recht) not in kontrolle:
        print("FEHLER: Berechtigung %s fehlt im Manifest." % recht)
        sys.exit(1)
print("Berechtigungen vollstaendig: %d" % len(BERECHTIGUNGEN))
for muss in ["AgendaWidget", "AgendaWidgetService", "BIND_REMOTEVIEWS"]:
    if muss not in kontrolle:
        print("FEHLER: '%s' fehlt im Manifest." % muss)
        sys.exit(1)
for name in java_dateien:
    if not os.path.isfile(os.path.join(ZIEL_JAVA, name)):
        print("FEHLER: %s wurde nicht eingesetzt." % name)
        sys.exit(1)
print("Widget vollstaendig eingerichtet.")
