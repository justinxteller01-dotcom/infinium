"""pass2: builds the built-in 'Infinium Dark UI' pack from the 26.2 client jar.
Low-saturation (grey) pixels of gui/container textures + container sprites become dark slate (~35-45% brightness);
coloured pixels (flames, arrows, XP green, etc.) are only mildly darkened so they stay legible. Output: resourcepacks/dark_ui."""
import io, json, os, shutil, zipfile
from PIL import Image
JAR = 'libs/minecraft-26.2-client.jar'
OUT = 'src/main/resources/resourcepacks/dark_ui'
PREFIXES = ('assets/minecraft/textures/gui/container/', 'assets/minecraft/textures/gui/sprites/container/',
            'assets/minecraft/textures/gui/sprites/recipe_book/')
SKIP = ('/bundle/', 'gamemode_switcher')

def dark(px):
    r, g, b, a = px
    if a == 0: return px
    if max(r, g, b) - min(r, g, b) < 28:          # grey UI chrome -> slate
        v = (r + g + b) / 3
        n = int(v * 0.46)
        return (n, n + 2, min(255, n + 8), a)
    return (int(r * 0.8), int(g * 0.8), int(b * 0.8), a)

shutil.rmtree(OUT, ignore_errors=True)
with zipfile.ZipFile(JAR) as z:
    n = 0
    for name in z.namelist():
        if not name.endswith(('.png', '.png.mcmeta')) or not name.startswith(PREFIXES) or any(s in name for s in SKIP): continue
        dst = os.path.join(OUT, name)
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        data = z.read(name)
        if name.endswith('.mcmeta'):
            open(dst, 'wb').write(data); continue
        im = Image.open(io.BytesIO(data)).convert('RGBA')
        im.putdata([dark(p) for p in im.getdata()])
        im.save(dst); n += 1
json.dump({"pack": {"description": "Infinium Dark UI: dark slate containers", "pack_format": 88,
                    "min_format": 88, "max_format": 88}}, open(os.path.join(OUT, 'pack.mcmeta'), 'w'))
print('dark_ui textures:', n)
