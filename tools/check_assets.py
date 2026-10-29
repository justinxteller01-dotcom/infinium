#!/usr/bin/env python3
"""Infinium sanity checks (no Gradle/MC needed). Run from the repo root: python3 tools/check_assets.py
Reads the Java sources and resources and fails (exit 1) on drift from the locked batch1 values:
 - every ModSounds.reg(...) event has a sounds.json entry, and every infinium: sound file exists
 - locked tier tables (warp, slots, slash/beam %, sword base, slow, air push, leg speed, air jumps, crystal order)
 - guide pages.json: 33 pages, LF only, no unresolved slash/beam literals
 - lang has the DoT display names
"""
import json, os, re, sys, itertools
J = 'src/main/java/dev/justin/infinium/'
R = 'src/main/resources/assets/infinium/'
fails = []
def check(ok, msg):
    print(('OK   ' if ok else 'FAIL ') + msg)
    if not ok: fails.append(msg)
def src(p): return open(p, encoding='utf-8').read()
def arr(text, name):
    m = re.search(r'\b' + name + r'\s*=\s*\{([^}]*)\}', text)
    if not m: raise SystemExit('array not found: ' + name)
    return [x.strip().strip('"').rstrip('F') for x in m.group(1).split(',') if x.strip()]

# ---- sounds ----
ms, perk = src(J + 'ModSounds.java'), src(J + 'Perk.java')
arrays = {'FAMILY_IDS': arr(perk, 'FAMILY_IDS')}
for a in ('EVENTS', 'CRYSTAL_KINDS', 'SWORD_KINDS'): arrays[a] = arr(ms, a)
body = ms[ms.index('public static void register()'):]
events = set()
for expr in re.findall(r'\breg\((.*?)\);', body):
    parts = []
    for tok in [t.strip() for t in expr.split('+')]:
        if tok.startswith('"'): parts.append([tok.strip('"')])
        else:
            m = re.fullmatch(r'(?:Perk\.)?([A-Z_]+)\[\w+\]', tok)
            if not m or m.group(1) not in arrays: raise SystemExit('check_assets: cannot expand reg token ' + tok)
            parts.append(arrays[m.group(1)])
    for combo in itertools.product(*parts): events.add(''.join(combo))
sj = json.load(open(R + 'sounds.json', encoding='utf-8'))
missing = sorted(events - set(sj))
check(not missing, f'sounds.json covers all {len(events)} ModSounds events' + (f' (missing: {missing})' if missing else ''))
nofile = []
for k, v in sj.items():
    for s in v.get('sounds', []):
        n = s if isinstance(s, str) else s.get('name')
        if isinstance(s, dict) and s.get('type') == 'event': continue
        if n.startswith('infinium:') and not os.path.exists(R + 'sounds/' + n[9:] + '.ogg'): nofile.append(n)
check(not nofile, f'every infinium: sound file exists ({len(sj)} entries)' + (f' (missing: {nofile[:10]})' if nofile else ''))

# ---- locked values ----
w, pv, cc, es, inf, cfg = (src(J + f) for f in ('SpacetimeWarp.java', 'PerkValues.java', 'CrystalCharge.java', 'EnergySlashEntity.java', 'Infinium.java', 'InfiniumConfig.java'))
def nums(xs): return [float(x) for x in xs]
check(nums(arr(w, 'DISTANCE')) == [8, 12, 16], 'warp distance 8/12/16')
check(nums(arr(w, 'COOLDOWN_SEC')) == [8, 6, 4], 'warp cooldown 8/6/4 s')
check('!isMidAir' not in w and 'canWarpHere' in w, 'warp allowed on the ground')
check(re.search(r'T1_SLOTS = 8, T2_SLOTS = 16, T3_SLOTS = 32', cc) is not None, 'crystal slots 8/16/32')
check(nums(arr(es, 'SLASH_PCT')) == [0.6, 0.7, 0.8], 'slash 60/70/80%')
check(nums(arr(es, 'BEAM_PCT')) == [0.75, 0.85, 0.95], 'beam 75/85/95%')
check('SWORD_BASE_DAMAGE = 10.0F' in inf, 'sword base 10 (+0/+1/+2 = 10/11/12)')
check(nums(arr(pv, 'SLOW_DURATION_SEC')) == [2, 4, 6] and nums(arr(pv, 'SLOW_COOLDOWN_SEC')) == [12, 10, 8], 'mob slow 2/4/6 s, cd 12/10/8 s')
check('SLOW_TICKS' not in pv and 'slowDurationTicks' not in cfg, 'stale slow constants gone')
for n, v in (('AIR_PUSH_ASTRAL', 0.0015), ('AIR_PUSH_SINGULARITY', 0.0030), ('AIR_PUSH_INFINITY', 0.0050)):
    m = re.search(n + r'\s*=\s*([\d.]+)', pv); check(m and float(m.group(1)) == v, f'{n} {v}')
check('getLegSpeedSingularity' in pv, 'Singularity leggings read their own leg speed config')
for n, v in (('airJumpsT1', 1), ('airJumpsT2', 2), ('airJumpsT3', 3)):
    m = re.search(n + r'\s*=\s*(\d+);', cfg); check(m and int(m.group(1)) == v, f'{n} = {v}')
order = re.search(r'ORDER = \{(.*?)\n    \};', cc, re.S).group(1)
rows = [re.findall(r'"(\w+)"', r) for r in re.findall(r'\{([^{}]*)\}', order)]
AS = ['regen', 'noconsume', 'saber', 'stacks', 'reach']; CY = ['mining', 'jump', 'slow', 'attack']
check(rows == [AS, CY, AS, CY, ['regen', 'mining', 'noconsume', 'jump', 'saber', 'slow', 'stacks', 'attack', 'reach']], 'crystal orders (locked 10-28)')

# ---- guide + lang ----
raw = open(R + 'guide/pages.json', 'rb').read()
g = json.loads(raw)
check(len(g['pages']) == 33, f'guide has 33 pages ({len(g["pages"])})')
check(b'\r' not in raw, 'pages.json is LF only')
txt = raw.decode('utf-8')
check('{n:slashdmg_t1}' in txt and '{n:beamdmg_t1}' in txt, 'guide slash/beam damage comes from code tokens')
check('4 crystal slots' not in txt, 'guide has no "4 crystal slots"')
lang = json.load(open(R + 'lang/en_us.json', encoding='utf-8'))
for k, v in (('effect.infinium.cosmic_radiation', 'Cosmic Radiation'), ('effect.infinium.burning', 'Cyberburn'), ('effect.infinium.bleeding', 'Bleeding')):
    check(lang.get(k) == v, f'lang {k} = {v}')
check(os.path.exists(R + 'textures/mob_effect/cosmic_radiation.png'), 'cosmic_radiation effect icon')

# ---- mixin registration (batch1: VariableJump mixins were never listed, so tap/hold + air jumps were dead) ----
for mj, d in (('src/main/resources/infinium.mixins.json', J + 'mixin/'), ('src/main/resources/infinium.client.mixins.json', J + 'client/mixin/')):
    cfg = json.load(open(mj, encoding='utf-8'))
    listed = set(cfg.get('mixins', []) + cfg.get('client', []) + cfg.get('server', []))
    classes = {f[:-5] for f in os.listdir(d) if f.endswith('.java') and '@Mixin' in src(d + f)}
    miss = sorted(classes - listed)
    check(not miss, f'{os.path.basename(mj)} registers all {len(classes)} @Mixin classes' + (f' (missing: {miss})' if miss else ''))

# ---- Bind-key pill click (batch1: JEI ate the click; Spatial GUI remapped it; Controlling has its own headers) ----
bk = src(J + 'client/ability/BindKeyHandler.java')
check('addPhaseOrdering(EARLY, Event.DEFAULT_PHASE)' in bk and 'allow.register(EARLY' in bk, 'Bind-key pill click registered in an early phase (before JEI)')
check(bk.count('hudCursor(mc,') >= 2 and 'getSourceX' in src(J + 'compat/SpatialMargin.java'), 'Bind-key pill click + hover use HUD-space cursor (Spatial GUI aware)')
check('getMethod("category")' in bk, 'Key Binds scroll finds Controlling category headers too')

# ---- sword combo (2026-10-29) ----
sc, ssh, sch, hud = (src(J + f) for f in ('SwordCombo.java', 'SwordSlashHandler.java', 'client/SwordSlashClientHandler.java', 'client/mixin/HudOverlayMixin.java'))
check('STAB_WINDOW_TICKS = 40' in sc and 'STAB_WINDOW_MIN_TICKS = 32' in sc, 'stab window 2 s (40 ticks), shrinks to >= 1.6 s with attack speed')
check('* 1.25' not in ssh and 'isStab ? (int) Math.round' not in ssh and 'SERVER_SLACK_TICKS' in ssh and 'LOCK_UNTIL_TICK' in ssh, 'server: no exact 1.25x stab gate, packet slack + post-stab lockout')
cl = sch[sch.index('public static void clientTick'):]
check('SPIN' not in cl.split('}')[0].replace('PENDING_SPIN', '').replace('SPIN_SOUND', '') and 'CRIT_TWIRL,' not in sch, 'twirl / crit-twirl sounds no longer played')
check('SLASH2_LAUNCH' in sch and 'SLASH2_LAUNCH' in ssh and 'slash2_launch' in arrays['SWORD_KINDS'], 'slash 1 / slash 2 / stab 3 each have their own sound')
check('SwordCooldownHud.render' in hud, 'energy-mode cooldown meter drawn on the HUD')

# elytra: solid 1.5px plate + edge walls (tools/gen_elytra_plate.py); every frame on the 128x64 plate layout
wr = src(J + 'client/InfiniumWingsRenderer.java'); wpd = src(J + 'client/WingPlateData.java')
check('plateLayer(fam).bakeRoot()' in wr and 'scaled(1.0F, 1.0F, 0.75F)' in wr and 'ElytraModel.createLayer()' not in wr
      and all(f'FAM{k} =' in wpd for k in (1, 3, 4)), 'elytra wings use the solid plate layer (1.5px, edge walls), not the 4px vanilla slab')
import glob as _g, json as _js, numpy as _np
from PIL import Image as _Im
_lay = _js.load(open(os.path.join(os.path.dirname(__file__), 'elytra_plate_layout.json')))
_bad = []
for _fam, _L in _lay.items():
    _fs = _g.glob(f"src/main/resources/assets/infinium/textures/entity/infinium_wings/{_L['dir']}/**/*.png", recursive=True)
    for _p in _fs:
        _a = _np.asarray(_Im.open(_p).convert('RGBA'))
        if _a.shape[:2] != (64, 128): _bad.append(_p); continue
        if '/base/' in _p and any(_a[q['v'] + (2 if q['dir'] in 'WE' else 0), q['u'] + (0 if q['dir'] in 'WE' else 2), 3] != 255 for q in _L['walls']):
            _bad.append(_p)
check(not _bad, f'elytra frames on the 128x64 plate layout, base edge walls opaque ({len(_bad)} bad)')

print(f'\n{len(fails)} failure(s)')
sys.exit(1 if fails else 0)
