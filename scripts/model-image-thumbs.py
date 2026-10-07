#!/usr/bin/env python3
"""Erzeugt die kleinen Varianten der Modellbilder für die Rangliste.

Quelle: frontend/public/model-images/<MODEL>.jpg (900 px, Wikimedia Commons, siehe
frontend/src/config/modelImages.ts). Ziel: frontend/public/model-images/thumbs/<MODEL>.jpg,
240 px breit (Zelle 96 bis 112 px, zweifache Pixeldichte), rund 10 KB statt 150 KB.

Aufruf nach jedem neuen Bild: python3 scripts/model-image-thumbs.py
Braucht Pillow (pip install pillow).
"""
from pathlib import Path

from PIL import Image

WIDTH = 240
ROOT = Path(__file__).resolve().parent.parent / "frontend" / "public" / "model-images"
OUT = ROOT / "thumbs"


def main() -> None:
    OUT.mkdir(exist_ok=True)
    made = 0
    for src in sorted(ROOT.glob("*.jpg")):
        dst = OUT / src.name
        if dst.exists() and dst.stat().st_mtime >= src.stat().st_mtime:
            continue
        with Image.open(src) as im:
            im = im.convert("RGB")
            im.thumbnail((WIDTH, WIDTH * 2), Image.LANCZOS)
            im.save(dst, "JPEG", quality=82, optimize=True, progressive=False)
        made += 1
    print(f"{made} Thumbnails erzeugt, {len(list(OUT.glob('*.jpg')))} vorhanden in {OUT}")


if __name__ == "__main__":
    main()
