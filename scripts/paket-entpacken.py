"""
Entpackt ein hochgeladenes Projektpaket, sofern eines im Repository liegt.

Damit genuegt es, vom Handy aus eine einzige Datei hochzuladen, statt jede
einzeln. Gesucht wird nach einer ZIP-Datei im Hauptverzeichnis; der Inhalt
wird ueber die vorhandenen Dateien gelegt.

Nicht ueberschrieben wird der Ordner .github -- eine Arbeitsanweisung kann
sich waehrend ihrer eigenen Ausfuehrung nicht selbst ersetzen.
"""
import os
import shutil
import sys
import zipfile

# Kandidaten im Hauptverzeichnis suchen
pakete = sorted(
    d for d in os.listdir(".")
    if d.lower().endswith(".zip") and os.path.isfile(d)
)

if not pakete:
    print("Kein Projektpaket gefunden - es werden die Dateien im Repository verwendet.")
    sys.exit(0)

paket = pakete[0]
if len(pakete) > 1:
    print("Mehrere Pakete gefunden, verwendet wird: %s" % paket)
    print("  (weitere: %s)" % ", ".join(pakete[1:]))

print("Projektpaket gefunden: %s" % paket)

with zipfile.ZipFile(paket) as z:
    eintraege = [n for n in z.namelist() if not n.endswith("/")]
    if not eintraege:
        print("FEHLER: Das Paket ist leer.")
        sys.exit(1)

    # Viele Pakete enthalten einen umschliessenden Ordner - diesen abziehen
    erste = [n.split("/")[0] for n in eintraege]
    gemeinsam = erste[0] if len(set(erste)) == 1 else None
    if gemeinsam:
        print("Umschliessender Ordner wird abgezogen: %s/" % gemeinsam)

    uebernommen = 0
    uebersprungen = 0

    for name in eintraege:
        ziel = name
        if gemeinsam:
            ziel = name[len(gemeinsam) + 1:]
        if not ziel:
            continue

        # Arbeitsanweisungen nicht anfassen
        if ziel.startswith(".github/"):
            uebersprungen += 1
            continue
        # Keine Ausbrueche aus dem Projektordner zulassen
        if ziel.startswith("/") or ".." in ziel.split("/"):
            print("  uebersprungen (unzulaessiger Pfad): %s" % ziel)
            uebersprungen += 1
            continue

        ordner = os.path.dirname(ziel)
        if ordner:
            os.makedirs(ordner, exist_ok=True)

        with z.open(name) as quelle, open(ziel, "wb") as senke:
            shutil.copyfileobj(quelle, senke)
        uebernommen += 1

print("%d Dateien uebernommen, %d uebersprungen." % (uebernommen, uebersprungen))

if uebersprungen:
    print("")
    print("Hinweis: Aenderungen an .github/workflows muessen weiterhin von Hand")
    print("hochgeladen werden - die Arbeitsanweisung laeuft gerade selbst.")
