# The Plinth icon

A mint play disc on a stepped plinth: a wide cap, a recessed body and a base. The disc is
the playback the app drives; the plinth is the base the name promises. Flat, one accent,
no text — it has to read at 48 dp before it reads as an illustration.

- Palette: `#0A0A0C` base, `#3A3A45` / `#2E2E37` / `#1E1E25` plinth, `#7ED9C3` accent.
- Source: `plinth-icon.svg` (square, for stores) and `plinth-icon-round.svg` (legacy round).
- Android: `drawable/ic_launcher_foreground.xml` and `ic_launcher_monochrome.xml` are the
  adaptive layers; the legacy PNGs are generated from the SVGs at 48/72/96/144/192.

Regenerate the previews:

```bash
rsvg-convert -w 1024 -h 1024 docs/icon/plinth-icon.svg -o docs/icon/plinth-icon-1024.png
for pair in "mdpi 48" "hdpi 72" "xhdpi 96" "xxhdpi 144" "xxxhdpi 192"; do
  set -- $pair
  rsvg-convert -w $2 -h $2 docs/icon/plinth-icon.svg -o app/src/main/res/mipmap-$1/ic_launcher.png
  rsvg-convert -w $2 -h $2 docs/icon/plinth-icon-round.svg -o app/src/main/res/mipmap-$1/ic_launcher_round.png
done
```
