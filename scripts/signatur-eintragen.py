"""
Traegt die Signierdaten fest in android/app/build.gradle ein.

Warum noetig: Verlaesst man sich auf Androids Voreinstellung fuer Debug-Builds
(~/.android/debug.keystore), kann Gradle je nach Umgebung einen eigenen
Schluessel erzeugen. Die Signatur wechselt dann von Build zu Build, und Android
verweigert die Installation ueber eine vorhandene App. Mit einem ausdruecklichen
Eintrag ist die Signatur festgelegt.
"""
import os
import re
import sys

GRADLE = os.path.join("android", "app", "build.gradle")
KEYSTORE = os.path.abspath(os.path.join("keystore", "debug.keystore"))

if not os.path.isfile(GRADLE):
    sys.exit("FEHLER: %s nicht gefunden." % GRADLE)
if not os.path.isfile(KEYSTORE):
    sys.exit("FEHLER: keystore/debug.keystore fehlt im Projekt.")

inhalt = open(GRADLE, encoding="utf-8").read()

if "dienstcockpit-signatur" in inhalt:
    print("Signierdaten sind bereits eingetragen.")
    sys.exit(0)

pfad = KEYSTORE.replace("\\", "/")

block = """
    // dienstcockpit-signatur
    signingConfigs {
        fest {
            storeFile file("%s")
            storePassword "android"
            keyAlias "androiddebugkey"
            keyPassword "android"
        }
    }
""" % pfad

neu, anzahl = re.subn(r"(android\s*\{)", r"\1" + block, inhalt, count=1)
if anzahl != 1:
    sys.exit("FEHLER: Der Abschnitt 'android {' wurde nicht gefunden.")

neu, n_debug = re.subn(
    r"(buildTypes\s*\{)",
    r"""\1
        debug {
            signingConfig signingConfigs.fest
        }""",
    neu, count=1)

if n_debug != 1:
    print("HINWEIS: 'buildTypes' nicht gefunden - Signatur nur als Konfiguration eingetragen.")

open(GRADLE, "w", encoding="utf-8").write(neu)
print("Signierdaten eingetragen, Schluessel:", pfad)

kontrolle = open(GRADLE, encoding="utf-8").read()
for muss in ["dienstcockpit-signatur", "androiddebugkey", "signingConfigs.fest"]:
    if muss not in kontrolle:
        sys.exit("FEHLER: '%s' fehlt nach dem Eintragen." % muss)
print("Eintrag geprueft.")
