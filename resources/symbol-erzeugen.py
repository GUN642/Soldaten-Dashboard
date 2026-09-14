"""
Erzeugt App-Symbol, Startbild und Kopfzeilen-Logo aus
resources/symbol-vorlage.png.

Aufruf:  python3 resources/symbol-erzeugen.py
"""
import os
from PIL import Image

hier = os.path.dirname(os.path.abspath(__file__))
projekt = os.path.dirname(hier) if os.path.basename(hier) == "resources" else hier
res = os.path.join(projekt, "resources")
www = os.path.join(projekt, "www")
VORLAGE = os.path.join(res, "symbol-vorlage.png")

GRUND = (14, 19, 25, 255)     # dunkler Grund, passend zur App


def motiv_laden():
    bild = Image.open(VORLAGE).convert("RGBA")
    kasten = bild.getchannel("A").getbbox()
    return bild.crop(kasten) if kasten else bild


def flaeche(motiv, groesse, anteil, hintergrund=None):
    bild = Image.new("RGBA", (groesse, groesse), hintergrund or (0, 0, 0, 0))
    f = (groesse * anteil) / max(motiv.size)
    neu = motiv.resize((max(1, int(motiv.width * f)), max(1, int(motiv.height * f))),
                       Image.LANCZOS)
    bild.paste(neu, ((groesse - neu.width) // 2, (groesse - neu.height) // 2), neu)
    return bild


def randfarbe_ermitteln(motiv):
    """
    Ermittelt eine zur Vorlage passende Farbe für den schmalen Rand, der bei
    manchen Launcher-Formen (Kreis, Tropfen) rund um den vergrößerten
    Vordergrund sichtbar bleiben kann. Gemittelt wird über den äußeren Rand
    des Motivs selbst, damit die Farbe zum Bildrand passt statt beliebig zu
    wirken.
    """
    motiv = motiv.convert("RGBA")
    b, h = motiv.size
    pixel = motiv.load()
    rot = gruen = blau = anzahl = 0
    rand = max(2, min(b, h) // 40)
    for y in range(h):
        for x in range(b):
            am_rand = x < rand or x >= b - rand or y < rand or y >= h - rand
            if not am_rand:
                continue
            r, g, bl, a = pixel[x, y]
            if a < 40:
                continue
            rot += r; gruen += g; blau += bl; anzahl += 1
    if anzahl == 0:
        return (30, 34, 40, 255)      # Ersatzwert, falls der Rand leer ist
    return (rot // anzahl, gruen // anzahl, blau // anzahl, 255)


def komprimiert_speichern(bild, pfad, farben=160):
    """
    Speichert mit reduzierter Farbpalette. Android skaliert diese Quellbilder
    beim Bauen ohnehin auf viel kleinere Auflösungen (höchstens rund 192 px
    für Startsymbole) herunter — eine Bandbildung im großen Ausgangsbild ist
    danach nicht mehr sichtbar, verkleinert die Datei im Projekt aber deutlich.
    Der Alphakanal (die Form) bleibt in voller Genauigkeit erhalten, nur die
    Farben werden reduziert.
    """
    bild = bild.convert("RGBA")
    alpha = bild.getchannel("A")
    rgb = bild.convert("RGB")
    pal = rgb.quantize(colors=farben, method=Image.MEDIANCUT, dither=Image.FLOYDSTEINBERG)
    ergebnis = pal.convert("RGBA")
    ergebnis.putalpha(alpha)
    ergebnis.save(pfad, "PNG", optimize=True, compress_level=9)


if __name__ == "__main__":
    motiv = motiv_laden()
    print("Vorlage:", motiv.size)

    # App-Symbol: das Wappen füllt die Fläche weitgehend aus.
    # 512 px reicht für alle Android-Dichten mit Reserve; Android skaliert
    # beim Bauen ohnehin auf die tatsächlich benötigten Größen herunter.
    ICON_GROESSE = 512
    SPLASH_GROESSE = 1200

    komprimiert_speichern(flaeche(motiv, ICON_GROESSE, 1.12),
                          os.path.join(res, "icon.png"))

    # Adaptives Symbol (wird auf modernen Android-Homescreens tatsächlich
    # angezeigt): Vordergrund deutlich größer als zuvor. Android beschneidet
    # je nach Launcher rund, quadratisch mit runden Ecken oder als Tropfen —
    # 0.88 ist ein guter Kompromiss zwischen "groß" und "wird an keiner Form
    # abgeschnitten".
    komprimiert_speichern(flaeche(motiv, ICON_GROESSE, 0.88),
                          os.path.join(res, "icon-foreground.png"))

    # Für den Fall, dass durch die runde oder eckige Maske noch ein schmaler
    # Rand des Hintergrunds sichtbar bleibt: eine zur Vorlage passende Farbe
    # verwenden statt der fast schwarzen Standardfarbe. Ein rein durchsichtiger
    # Hintergrund ist bei adaptiven Symbolen nicht vorgesehen — je nach
    # Launcher würde dahinter Schwarz oder Weiß durchscheinen.
    rand_farbe = randfarbe_ermitteln(motiv)
    Image.new("RGBA", (ICON_GROESSE, ICON_GROESSE), rand_farbe).save(
        os.path.join(res, "icon-background.png"), "PNG", optimize=True)

    # Startbild
    for name in ("splash.png", "splash-dark.png"):
        sp = Image.new("RGBA", (SPLASH_GROESSE, SPLASH_GROESSE), GRUND)
        k = flaeche(motiv, int(SPLASH_GROESSE * 0.34), 1.0)
        sp.paste(k, ((SPLASH_GROESSE - k.width) // 2, (SPLASH_GROESSE - k.height) // 2), k)
        komprimiert_speichern(sp, os.path.join(res, name))

    # Kopfzeile: nur der obere Teil des Wappens ohne das Schriftband.
    # Bei 28 Bildpunkten Hoehe waere der Schriftzug ohnehin unlesbar, und der
    # Name steht in der Kopfzeile bereits daneben.

    oben = motiv.crop((0, 0, motiv.width, int(motiv.height * 0.73)))
    # Mittig quadratisch fassen
    seite = min(oben.width, oben.height)
    links = (oben.width - seite) // 2
    quadrat = oben.crop((links, 0, links + seite, seite))
    kopf = quadrat.resize((72, 72), Image.LANCZOS)
    kopf.save(os.path.join(www, "logo-header.png"), "PNG", optimize=True)
    kopf.save(os.path.join(www, "logo-header-light.png"), "PNG", optimize=True)

    print("Symbole erzeugt")
