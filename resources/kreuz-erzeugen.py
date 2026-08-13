"""
Erzeugt die Symbole aus der Vorlage resources/kreuz-vorlage.png.

Die Vorlage ist ein Eisernes Kreuz. Der weiße Hintergrund wird freigestellt,
die Form selbst bleibt unverändert.

Aufruf:  python3 resources/kreuz-erzeugen.py
"""
import os
import numpy as np
from PIL import Image

hier = os.path.dirname(os.path.abspath(__file__))
projekt = os.path.dirname(hier) if os.path.basename(hier) == "resources" else hier
res = os.path.join(projekt, "resources")
www = os.path.join(projekt, "www")
VORLAGE = os.path.join(res, "kreuz-vorlage.png")


def freistellen(pfad, schwelle=170):
    """Weißen Hintergrund durchsichtig machen, Form unverändert lassen."""
    bild = Image.open(pfad).convert("RGB")
    arr = np.asarray(bild).astype(np.float32)
    hell = arr.max(axis=2)

    # Deckkraft aus der Helligkeit: weiß -> durchsichtig, dunkel -> deckend
    alpha = np.clip((schwelle - hell) / (schwelle * 0.55), 0, 1)

    aus = np.zeros((arr.shape[0], arr.shape[1], 4), dtype=np.uint8)
    aus[..., 0:3] = arr.astype(np.uint8)
    aus[..., 3] = (alpha * 255).astype(np.uint8)
    bild = Image.fromarray(aus, "RGBA")
    return bild.crop(bild.getchannel("A").getbbox())


def umfaerben(bild, farbe):
    """Form beibehalten, nur die Farbe ersetzen."""
    neu = Image.new("RGBA", bild.size, farbe)
    neu.putalpha(bild.getchannel("A"))
    return neu


def flaeche(motiv, groesse, anteil):
    """Motiv zentriert auf durchsichtige Fläche legen."""
    bild = Image.new("RGBA", (groesse, groesse), (0, 0, 0, 0))
    f = (groesse * anteil) / max(motiv.size)
    neu = motiv.resize((max(1, int(motiv.width * f)), max(1, int(motiv.height * f))),
                       Image.LANCZOS)
    bild.paste(neu, ((groesse - neu.width) // 2, (groesse - neu.height) // 2), neu)
    return bild


if __name__ == "__main__":
    motiv = freistellen(VORLAGE)
    print("Vorlage freigestellt:", motiv.size)

    WEISS  = (245, 248, 252, 255)
    DUNKEL = (26, 32, 40, 255)

    # App-Symbol: helle Fassung, damit es auf dunklem Untergrund sichtbar ist
    hell = umfaerben(motiv, WEISS)
    flaeche(hell, 1024, 0.96).save(os.path.join(res, "icon.png"), "PNG")
    # Adaptiv: kleiner, damit der runde Beschnitt die Arme nicht abschneidet
    flaeche(hell, 1024, 0.66).save(os.path.join(res, "icon-foreground.png"), "PNG")
    Image.new("RGBA", (1024, 1024), DUNKEL).save(
        os.path.join(res, "icon-background.png"), "PNG")

    for name in ("splash.png", "splash-dark.png"):
        sp = Image.new("RGBA", (2732, 2732), DUNKEL)
        k = flaeche(hell, int(2732 * 0.32), 1.0)
        sp.paste(k, ((2732 - k.width) // 2, (2732 - k.height) // 2), k)
        sp.save(os.path.join(res, name), "PNG")

    # Kopfzeile: zwei Fassungen für dunklen und hellen Modus
    umfaerben(motiv, WEISS).resize((72, 72), Image.LANCZOS)\
        .save(os.path.join(www, "logo-header.png"), "PNG", optimize=True)
    umfaerben(motiv, DUNKEL).resize((72, 72), Image.LANCZOS)\
        .save(os.path.join(www, "logo-header-light.png"), "PNG", optimize=True)

    print("Symbole erzeugt")
