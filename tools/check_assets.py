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

print(f'\n{len(fails)} failure(s)')
sys.exit(1 if fails else 0)
