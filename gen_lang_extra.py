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
    "key.infinium.toggle_fullbright": "Toggle Full Bright",
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
