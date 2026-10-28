# Lang keys that no other generator owns (batch1, 2026-10-28): ability names, ability/thrust key names,
# the Infinium key category, ability HUD strings, warp/thrust subtitles. Merges into the existing
# en_us.json (same read-modify-write + indent=2 as gen_finder.py), so it is idempotent.
import json
L = 'src/main/resources/assets/infinium/lang/en_us.json'
lang = json.load(open(L))
lang.update({
    # abilities (Ability.nameKey / Ability.keyName)
    "ability.infinium.warp": "Quantum Tunnel",
    "ability.infinium.xray_pulse": "X-Ray Pulse",
    "key.infinium.ability.warp": "Quantum Tunnel",
    "key.infinium.ability.xray_pulse": "X-Ray Pulse",
    # other Infinium key mappings (InfiniumKeys)
    "key.infinium.toggle_wings": "Toggle wings",
    "key.infinium.flight_thrust": "Flight thrust",
    # category label (KeyMapping.Category infinium:controls -> key.category.infinium.controls)
    "key.category.infinium.controls": "Infinium",
    # ability HUD
    "hud.infinium.ability.bind_key": "Bind key",
    "gui.infinium.settings.open_controls": "Open Controls (key binds)",
    # sounds
    "subtitles.infinium.cyberpsycho_thrust_start": "Cyber Psycho thrust powers up",
    "subtitles.infinium.cyberpsycho_thrust_loop": "Cyber Psycho thrust hums",
    "subtitles.infinium.cyberpsycho_thrust_stop": "Cyber Psycho thrust powers down",
})
json.dump(lang, open(L, 'w'), indent=2)
print('gen_lang_extra: lang merged')

# ---- batch1: Cyber Psycho thrust sound events (PLACEHOLDERS). Each points at a vanilla event until the real clips
# exist; to drop real files in, replace the entry with {"sounds": ["infinium:cyberpsycho/thrust_start"], ...}
# and add assets/infinium/sounds/cyberpsycho/thrust_start.ogg (same for _loop / _stop).
S = 'src/main/resources/assets/infinium/sounds.json'
snd = json.load(open(S))
for ev, vanilla in (('start', 'minecraft:block.beacon.activate'), ('loop', 'minecraft:block.beacon.ambient'),
                    ('stop', 'minecraft:block.beacon.deactivate')):
    snd[f'cyberpsycho_thrust_{ev}'] = {"sounds": [{"name": vanilla, "type": "event"}],
                                       "subtitle": f"subtitles.infinium.cyberpsycho_thrust_{ev}"}
json.dump(snd, open(S, 'w'), indent=2)
print('gen_lang_extra: sounds merged')
