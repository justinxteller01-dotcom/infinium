"""pass2: preview RENDERS of every guidebook spread as animated GIFs (not shipped). Mirrors InfiniumGuideScreen layout at book
scale 1 (292x196 GUI px), upscaled SCALE x nearest-neighbour. Font: minecraft ascii.png from the 26.2 client jar.
Approximations: item icons = base texture + style crack overlay (no enchant glint / 3D block items), no buttons/tooltip."""
import io, json, math, os, re, sys, zipfile
from PIL import Image
R = 'src/main/resources/assets/infinium/'
JAR = zipfile.ZipFile('libs/minecraft-26.2-client.jar')
OUT = sys.argv[1] if len(sys.argv) > 1 else '/workspace/fixes/out/guide_gifs'
SCALE, MARGIN, FPS_MS = 4, 12, 100
BW, BH = 292, 196
def jimg(p):
    try: return Image.open(io.BytesIO(JAR.read(p))).convert('RGBA')
    except KeyError: return None
def mimg(p):
    return Image.open(R + p).convert('RGBA') if os.path.exists(R + p) else None

# ---- font (ascii provider, height 8, ascent 7) ----
prov = [p for p in json.loads(JAR.read('assets/minecraft/font/include/default.json'))['providers'] if p.get('file') == 'minecraft:font/ascii.png'][0]
ASCII = jimg('assets/minecraft/textures/font/ascii.png'); GW = ASCII.width // 16
GLYPH, ADV = {}, {}
for r, row in enumerate(prov['chars']):
    for c, ch in enumerate(row):
        g = ASCII.crop((c * GW, r * GW, c * GW + GW, r * GW + GW)); w = 0
        for x in range(GW):
            if any(g.getpixel((x, y))[3] for y in range(GW)): w = x + 1
        GLYPH[ch] = g; ADV[ch] = w + 1
ADV[' '] = 4
def width(s): return sum(ADV.get(c, 6) for c in s)
def split(text, wrap):   # word wrap like StringSplitter (break at last space)
    out, line = [], ''
    for word in text.split(' '):
        cand = word if not line else line + ' ' + word
        if width(cand) <= wrap or not line: line = cand
        else: out.append(line); line = word
        while width(line) > wrap:   # hard break of an overlong word
            k = len(line)
            while k > 1 and width(line[:k]) > wrap: k -= 1
            out.append(line[:k]); line = line[k:]
    out.append(line); return out

class Canvas:
    def __init__(s):
        s.im = Image.new('RGBA', ((BW + 2 * MARGIN) * SCALE, (BH + 2 * MARGIN) * SCALE), (0, 0, 0, 255))
    def px(s, v): return int(round((v + MARGIN) * SCALE))
    def fill(s, x0, y0, x1, y1, argb):
        a = (argb >> 24) & 255; col = ((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, a)
        X0, Y0, X1, Y1 = s.px(x0), s.px(y0), s.px(x1), s.px(y1)
        if X1 <= X0 or Y1 <= Y0: return
        s.im.alpha_composite(Image.new('RGBA', (X1 - X0, Y1 - Y0), col), (X0, Y0))
    def blit(s, img, x, y, w, h, tint=None):
        W, H = s.px(x + w) - s.px(x), s.px(y + h) - s.px(y)
        if W <= 0 or H <= 0: return
        im = img.resize((W, H), Image.NEAREST)
        if tint is not None:
            r, g, b = (tint >> 16) & 255, (tint >> 8) & 255, tint & 255
            ch = im.split(); im = Image.merge('RGBA', (ch[0].point(lambda v: v * r // 255), ch[1].point(lambda v: v * g // 255), ch[2].point(lambda v: v * b // 255), ch[3]))
        s.im.alpha_composite(im, (s.px(x), s.px(y)))
    def text(s, t, x, y, argb, sc=1.0):
        cx = x
        for ch in t:
            g = GLYPH.get(ch)
            if g is not None and ch != ' ': s.blit(g, cx, y, GW * sc, GW * sc, argb & 0xFFFFFF)
            cx += ADV.get(ch, 6) * sc

def hash_(a, b):
    M = (1 << 64) - 1
    h = (a * 0x9E3779B97F4A7C15 + b * 0xC2B2AE3D27D4EB4F) & M
    h ^= h >> 29; h = (h * 0xBF58476D1CE4E5B9) & M
    v = (h ^ (h >> 32)) & 0xFFFFFFFF
    return v - (1 << 32) if v >= 1 << 31 else v

def title_color(art): return {'astral': 0xFFC45AD4, 'cybernetic': 0xFFD24A8A, 'singularity': 0xFFA14AD0, 'cyberpsycho': 0xFFE0235A, 'infinity': 0xFFC43A7A}.get(art, 0xFFE070C0)

def page_anim(c, ms):
    for i in range(7):
        h = hash_(ms // 80, i); x = 18 + (h & 1023) % 112; y = 36 + ((h >> 10) & 1023) % 120; a = 18 + (h & 15)
        c.fill(x, y, x + 1, y + 1, (a << 24) | 0xC45AD4)
    band = (ms // 40) % 140
    for y in range(20, 160, 7):
        yy = 20 + (y + band) % 140
        if yy <= 158: c.fill(156, yy, 276, yy + 1, 0x14D24A8A)

def edge_particles(c, ms):
    n = 28
    for i in range(n):
        e = i % 4; t = (i // 4) / (n / 4); wob = math.sin(ms / 400 + i) * 3
        if e == 0: px, py, ox, oy = t * BW, 0, 0, -1
        elif e == 1: px, py, ox, oy = BW, t * BH, 1, 0
        elif e == 2: px, py, ox, oy = (1 - t) * BW, BH, 0, 1
        else: px, py, ox, oy = 0, (1 - t) * BH, -1, 0
        dist = 3 + (hash_(ms // 200, i) & 7) + wob * 0.2; a = 70 - int(dist * 3.5)
        if a < 8: continue
        x, y = int(px + ox * dist), int(py + oy * dist)
        c.fill(x, y, x + 2, y + 2, (a << 24) | (0xC45AD4 if i % 2 == 0 else 0xE070C0))

def draw_body(c, body, x, y, w):
    paras = body.split('\n'); avail = BH - 36 - y; sc = 0.75
    def height(sc):
        wrap = max(20, int(w / sc)); return int(sum(10 if not p else len(split(p, wrap)) * 10 for p in paras) * sc)
    while sc > 0.45 and height(sc) > avail: sc -= 0.05
    view = int(avail / sc); wrap = int(w / sc); ly = 0
    for p in paras:
        if not p: ly += 10; continue
        for line in split(p, wrap):
            if ly >= 0 and ly + 9 <= view: c.text(line, x + 0, y + ly * sc, 0xFFD2C0D4, sc)
            ly += 10

def welcome_lines(body):
    wrap = int(124 / 0.75); lines = []
    for p in body.split('\n'): lines += [None] if not p else split(p, wrap)
    lv = int((BH - 36 - 28) / 0.75); rv = int((BH - 36 - 16) / 0.75)
    left = min(len(lines), max(0, (lv - 9) // 10 + 1)); start = left
    if start < len(lines) and lines[start] is None: start += 1
    return lines, left, start, max(0, (len(lines) - start) * 10 - rv), rv

def draw_welcome(c, body, scroll):
    lines, left, start, _, rv = welcome_lines(body); sc = 0.75
    for i in range(left):
        if lines[i] is not None: c.text(lines[i], 16, 28 + i * 10 * sc, 0xFFD2C0D4, sc)
    ly = -scroll
    for i in range(start, len(lines)):
        if lines[i] is not None and ly >= 0 and ly + 9 <= rv: c.text(lines[i], 152, 16 + ly * sc, 0xFFD2C0D4, sc)
        ly += 10

# ---- items ----
def anim_frame(img, ms, frametime=6):
    if img is None: return None
    n = img.height // img.width
    if n <= 1: return img
    f = (ms // (frametime * 50)) % n; return img.crop((0, f * img.width, img.width, (f + 1) * img.width))
def vanilla(name):
    return jimg(f'assets/minecraft/textures/item/{name}.png') or jimg(f'assets/minecraft/textures/block/{name}.png')
def icon(spec, ms):
    kind, name, style = spec
    if kind == 'mc':
        if name == 'wither_skeleton_skull': return None
        return anim_frame(vanilla(name), ms)
    base = anim_frame(mimg(f'textures/item/{name}.png'), ms)
    if base is None: return None
    if style:
        ov = mimg(f'textures/item/cracks_{style}/{name}.png')
        if ov is not None: base = base.copy(); base.alpha_composite(anim_frame(ov, ms))
    return base
def M(n): return ('mc', n, None)
def I(n, st=None): return ('inf', n, st)
def T(st): return I(f'{st}_upgrade_smithing_template')
def A(piece, st): return I(f'infinium_{piece}', st)
def crafting(cells, res): return ('crafting', [(30 + (i % 3) * 18, 17 + (i // 3) * 18, s) for i, s in enumerate(cells)] + [(124, 35, res)])
def smithing(t, b, a, r): return ('smithing', [(8, 48, t), (26, 48, b), (44, 48, a), (98, 48, r)])
FR, ING, CRY = I('spacetime_fragments'), I('infinium_ingot'), I('spacetime_crystal')
LAYOUTS = {
 'crystal': [crafting([FR, FR, FR, FR, M('amethyst_shard'), FR, FR, FR, FR], CRY)],
 'ingot': [crafting([M('ender_eye'), M('obsidian'), M('diamond_block'), M('netherite_ingot'), CRY, M('netherite_ingot'), M('wither_skeleton_skull'), M('obsidian'), M('redstone_block')], ING)],
 'tools': [smithing(None, M('netherite_' + t), ING, I('infinium_' + t)) for t in ('sword', 'pickaxe', 'axe', 'shovel', 'hoe')],
 'finder': [crafting([M('bone')] * 4 + [M('compass')] + [M('bone')] * 4, I('dungeon_finder_00'))],
 'templates': [smithing(T('astral'), M('netherite_helmet'), ING, A('helmet', 'astral')), smithing(T('cybernetic'), M('netherite_helmet'), ING, A('helmet', 'cybernetic')),
               smithing(T('singularity'), A('helmet', 'astral'), ING, A('helmet', 'singularity')), smithing(T('cyberpsycho'), A('helmet', 'cybernetic'), ING, A('helmet', 'cyberpsycho'))],
 'astral': [smithing(T('astral'), M('netherite_helmet'), ING, A('helmet', 'astral'))],
 'cybernetic': [smithing(T('cybernetic'), M('netherite_chestplate'), ING, A('chestplate', 'cybernetic'))],
 'singularity': [smithing(T('singularity'), A('helmet', 'astral'), ING, A('helmet', 'singularity'))],
 'cyberpsycho': [smithing(T('cyberpsycho'), A('chestplate', 'cybernetic'), ING, A('chestplate', 'cyberpsycho'))],
 'infinity': [smithing(A('chestplate', 'singularity'), A('chestplate', 'cyberpsycho'), ING, I('infinity_chestplate'))],
 'dyes': [smithing(M(d), A(p, 'cyberpsycho'), None, A(p, 'cyberpsycho')) for d, p in (('blue_dye', 'helmet'), ('red_dye', 'chestplate'), ('green_dye', 'leggings'), ('orange_dye', 'boots'), ('purple_dye', 'chestplate'))],
 'rocket': [smithing(None, M('firework_rocket'), ING, I('infinium_rocket'))],
}
GUI = {'crafting': jimg('assets/minecraft/textures/gui/container/crafting_table.png'), 'smithing': jimg('assets/minecraft/textures/gui/container/smithing.png')}
def station(c, lay, x, y, aw, ah, ms):
    st, slots = lay; gs = min(aw / 176, ah / 78)
    c.blit(GUI[st].crop((0, 0, 176, 78)), x, y, 176 * gs, 78 * gs)
    for sx, sy, spec in slots:
        if spec is None or sy + 18 > 78: continue
        im = icon(spec, ms)
        if im is not None: c.blit(im, x + sx * gs, y + sy * gs, 16 * gs, 16 * gs)
def recipes(c, rec, x, y, ah, ms):
    lays = LAYOUTS.get(rec, [])
    if not lays: return
    if rec not in ('tools', 'dyes'):
        idx = 0 if len(lays) == 1 or rec != 'templates' else (ms // 2200) % len(lays)
        station(c, lays[idx], x, y, 124, ah, ms); return
    rh = max(16, (ah - 2 * (len(lays) - 1)) // len(lays))
    for i, l in enumerate(lays): station(c, l, x, y + i * (rh + 2), 124, rh, ms)

SHEETS = json.load(open(R + 'guide/strips.json'))
SHEET_IMG = {}
def sheet(c, art, x, y, w, h, ms):
    sh = SHEETS.get(art)
    if not sh: return False
    if art not in SHEET_IMG: SHEET_IMG[art] = mimg('textures/gui/guide/' + sh['file'] + '.png')
    im = SHEET_IMG[art]
    if im is None: return False
    k = (ms // sh['ms']) % sh['frames']; fw, fh = sh['fw'], sh['fh']
    c.blit(im.crop(((k % sh['cols']) * fw, (k // sh['cols']) * fh, (k % sh['cols'] + 1) * fw, (k // sh['cols'] + 1) * fh)), x, y, w, h); return True
def strip(c, name, x, y, size, frame, frames, ms):
    im = mimg(f'textures/gui/guide/{name}.png')
    if im is None: return False
    k = (ms // 120) % frames; c.blit(im.crop((0, k * frame, frame, (k + 1) * frame)), x, y, size, size); return True
def settings(c, x, y, ms):
    files = ['settings_particles_on', 'settings_particles_off', 'settings_wings_on', 'settings_wings_off']
    labels = ['Particles on', 'Particles off', 'Wings on', 'Wings off']
    for i in range(4):
        cx, cy = x + (i % 2) * 64, y + (i // 2) * 78
        if not strip(c, files[i], cx, cy, 52, 64, 8, ms):
            c.fill(cx, cy, cx + 52, cy + 52, 0xFF1A0C16); c.fill(cx, cy, cx + 52, cy + 1, 0xFF6A2A78)
        c.text(labels[i], cx, cy + 54, 0xFFD2C0D4)
def set_items(c, art, x, y, ms):
    if art == 'infinity': specs = [I('infinity_' + p) for p in ('helmet', 'chestplate', 'leggings', 'boots')]
    elif art in ('astral', 'cybernetic', 'singularity', 'cyberpsycho'): specs = [A(p, art) for p in ('helmet', 'chestplate', 'leggings', 'boots')]
    else: return
    for i, s in enumerate(specs):
        im = icon(s, ms)
        if im is not None: c.blit(im, x + i * 18, y, 16, 16)

BOOK = mimg('textures/gui/guide/book.png')
def render(i, sp, n, ms, scroll=0):
    c = Canvas(); c.blit(BOOK, 0, 0, BW, BH)   # book.png is 292x196
    page_anim(c, ms)
    c.text(sp['title'], 16, 14, title_color(sp.get('art') or ''))
    art = sp.get('art') or ''; rec = sp.get('recipe') or ''
    if sp['title'] == 'Welcome': draw_welcome(c, sp['body'], scroll)
    else:
        draw_body(c, sp['body'], 16, 28, 124)
        if art == 'welcome': strip(c, art, 168, 18, 100, 128, 16, ms)
        elif art == 'settings': settings(c, 152, 16, ms)
        else:
            ry = 16
            if art:
                if not sheet(c, art, 151, ry, 124, 62, ms): strip(c, art, 158, ry, 64, 128, 16, ms)
                set_items(c, art, 158, ry + 66, ms); ry = 108
            recipes(c, rec, 150, ry, 156 if not art else 64, ms)
    cnt = f'{i + 1} / {n}'; c.text(cnt, BW // 2 - width(cnt) // 2, BH - 20, 0xFFD24A8A)
    for lab, bx in (('Back', 16), ('Next', BW - 62)):   # GuideButton at scale 1: 46x14
        c.fill(bx, BH - 24, bx + 46, BH - 10, 0xFF3A1830); c.text(lab, bx + (46 - width(lab)) / 2, BH - 21, 0xFFE8C8DE)
    edge_particles(c, ms)
    return c.im

def save(frames, path, ms):
    pal = [f.convert('RGB').quantize(colors=128, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE) for f in frames]
    pal[0].save(path, save_all=True, append_images=pal[1:], duration=ms, loop=0, optimize=True, disposal=1)
    return os.path.getsize(path)

os.makedirs(OUT, exist_ok=True)
for f in os.listdir(OUT):
    if f.endswith('.gif'): os.remove(os.path.join(OUT, f))
spreads = json.load(open(R + 'guide/pages.json'))['spreads']
for i, sp in enumerate(spreads):
    art = sp.get('art') or ''
    if sp['title'] == 'Welcome':
        _, _, _, mx, rv = welcome_lines(sp['body']); total = max(mx, 0)
        # pan the right page down then back up through ALL its text (forced, even if it fits in-game): pad to show every line
        frames_n = 60; seq = []
        for k in range(frames_n):
            ph = k / (frames_n - 1); tri = 1 - abs(2 * ph - 1)
            seq.append((k * FPS_MS, int(round(tri * total))))
        frames = [render(i, sp, len(spreads), t, s) for t, s in seq]
    else:
        if art in SHEETS: dur = min(SHEETS[art]['frames'] * SHEETS[art]['ms'], 6000)
        elif sp.get('recipe') == 'templates': dur = 4 * 2200
        else: dur = 3000
        frames = [render(i, sp, len(spreads), t) for t in range(0, dur, FPS_MS)]
    safe = re.sub(r'[^A-Za-z0-9]+', '_', sp['title']).strip('_')
    path = os.path.join(OUT, f'{i + 1:02d}_{safe}.gif')
    size = save(frames, path, FPS_MS)
    while size > 5_000_000 and len(frames) > 10:
        frames = frames[::2]; size = save(frames, path, FPS_MS * 2)
    print(path, len(frames), size)
