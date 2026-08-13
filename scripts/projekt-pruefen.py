"""
Prueft vor dem Bauen, ob alle benoetigten Projektdateien vorhanden sind.

Ohne diese Pruefung faellt eine fehlende Datei erst beim Uebersetzen auf --
nach mehreren Minuten und mit einer Meldung, die den eigentlichen Grund
(nicht hochgeladene Datei) nicht nennt.
"""
import os
import sys

NOETIG = [
    ("www/index.html",                    "Die App selbst"),
    ("package.json",                      "Liste der Module"),
    ("capacitor.config.json",             "Grundeinstellungen"),
    ("keystore/debug.keystore",           "Signierschluessel fuer Updates"),
    ("scripts/signatur-eintragen.py",     "Traegt die Signatur in Gradle ein"),
    ("scripts/widget-einrichten.py",      "Setzt Module und Widget ein"),
    ("native/MainActivity.java",          "Startpunkt der App"),
    ("native/KontaktDatenPlugin.java",    "Geburtstage aus den Kontakten"),
    ("native/AgendaWidget.java",          "Widget fuer den Startbildschirm"),
    ("native/WidgetAnstossPlugin.java",   "Aktualisierung des Widgets"),
    ("native/agenda_widget.xml",          "Aufbau des Widgets"),
    ("native/agenda_widget_info.xml",     "Groesse des Widgets"),
]
NOETIG.append(("native/DruckPlugin.java", "Druck ueber das Android-Druckmodul"))

fehlend = [(pfad, zweck) for pfad, zweck in NOETIG if not os.path.isfile(pfad)]

if fehlend:
    print("=" * 60)
    print(" FEHLENDE DATEIEN IM REPOSITORY")
    print("=" * 60)
    for pfad, zweck in fehlend:
        print("  %-34s %s" % (pfad, zweck))
    print("=" * 60)
    print(" Diese Dateien aus dem Projektpaket hochladen und den Build")
    print(" erneut starten. Ordner, die mit einem Punkt beginnen (.github),")
    print(" sind im Windows-Explorer moeglicherweise ausgeblendet.")
    print("=" * 60)
    sys.exit(1)

print("Alle %d benoetigten Dateien sind vorhanden." % len(NOETIG))

# Zusatzpruefung: MainActivity darf nur Module anmelden, die es auch gibt
haupt = open("native/MainActivity.java", encoding="utf-8").read()
import re
angemeldet = re.findall(r"registerPlugin\((\w+)\.class\)", haupt)
for name in angemeldet:
    if not os.path.isfile("native/%s.java" % name):
        print("FEHLER: MainActivity meldet '%s' an, aber native/%s.java fehlt."
              % (name, name))
        sys.exit(1)
if angemeldet:
    print("Angemeldete Module: %s" % ", ".join(angemeldet))
