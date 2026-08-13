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
    "DruckPlugin.java",
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
shutil.copy(os.path.join("native", "agenda_widget_info.xml"), xml)
print("Layouts eingesetzt (Rahmen mit acht Zeilen + Beschreibung)")

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
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/agenda_widget_info" />
        </receiver>
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

# ---- Kontrolle ----
kontrolle = open(MANIFEST, encoding="utf-8").read()
if "AgendaWidget" not in kontrolle:
    print("FEHLER: Anmeldung im Manifest fehlt.")
    sys.exit(1)
for name in java_dateien:
    if not os.path.isfile(os.path.join(ZIEL_JAVA, name)):
        print("FEHLER: %s wurde nicht eingesetzt." % name)
        sys.exit(1)
print("Widget vollstaendig eingerichtet.")
