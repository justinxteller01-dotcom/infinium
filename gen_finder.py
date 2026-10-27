"""Dungeon Finder: cosmic purple/violet compass with obsidian-violet casing, glowing violet dial, and magenta-pink spacetime crystal needle.
Item definition = vanilla compass definition with every model swapped to infinium:item/dungeon_finder_XX (lodestone_tracker target set server-side)."""
import json, os, math, colorsys
from PIL import Image

REF = 'ref/mc/assets/minecraft/'
R = 'src/main/resources/assets/infinium/'
os.makedirs(R + 'textures/item', exist_ok=True)
os.makedirs(R + 'models/item', exist_ok=True)
os.makedirs(R + 'items', exist_ok=True)

RIM_DARK = (0x16, 0x06, 0x22)
DIAL_MID = (0x4A, 0x18, 0x68)
DIAL_BRIGHT = (0xAC, 0x3E, 0xD8)

for i in range(32):
    n = f'{i:02d}'
    im = Image.open(REF + f'textures/item/compass_{n}.png').convert('RGBA')
    px = im.load()
    phase = (i / 32.0) * math.tau
    shimmer = 0.5 + 0.5 * math.sin(phase * 2.0)
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            if r > g + 35 and r > b + 35:  # needle -> glowing spacetime magenta-violet
                k = min(1.0, (r / 255.0) * 1.12)
                nr = min(255, int(0xFF * k))
                ng = min(255, int((0x44 + 0x20 * shimmer) * k))
                nb = min(255, int(0xE8 * k))
                px[x, y] = (nr, ng, nb, a)
                continue
            h, l, s = colorsys.rgb_to_hls(r / 255.0, g / 255.0, b / 255.0)
            t = min(1.0, max(0.0, (l - 0.10) / 0.72))
            if t < 0.45:
                u = t / 0.45
                cr = int(RIM_DARK[0] + (DIAL_MID[0] - RIM_DARK[0]) * u)
                cg = int(RIM_DARK[1] + (DIAL_MID[1] - RIM_DARK[1]) * u)
                cb = int(RIM_DARK[2] + (DIAL_MID[2] - RIM_DARK[2]) * u)
            else:
                u = (t - 0.45) / 0.55
                boost = 0.88 + 0.12 * shimmer
                cr = min(255, int((DIAL_MID[0] + (DIAL_BRIGHT[0] - DIAL_MID[0]) * u) * boost))
                cg = min(255, int((DIAL_MID[1] + (DIAL_BRIGHT[1] - DIAL_MID[1]) * u) * boost))
                cb = min(255, int((DIAL_MID[2] + (DIAL_BRIGHT[2] - DIAL_MID[2]) * u) * boost))
            px[x, y] = (cr, cg, cb, a)
    im.save(R + f'textures/item/dungeon_finder_{n}.png')
    json.dump(
        {"parent": "minecraft:item/generated", "textures": {"layer0": f"infinium:item/dungeon_finder_{n}"}},
        open(R + f'models/item/dungeon_finder_{n}.json', 'w')
    )

s = open(REF + 'items/compass.json').read().replace('minecraft:item/compass_', 'infinium:item/dungeon_finder_')
open(R + 'items/dungeon_finder.json', 'w').write(s)
L = R + 'lang/en_us.json'
lang = json.load(open(L))
lang['item.infinium.dungeon_finder'] = 'Dungeon Finder'
json.dump(lang, open(L, 'w'), indent=2)
print('dungeon finder: 32 cosmic purple frames OK')
