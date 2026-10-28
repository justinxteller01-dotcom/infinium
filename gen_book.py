#!/usr/bin/env python3
"""Lang, guide pages, the optional guide recipe, mixin/JEI wiring, and the build hook.

Run once from the mod root:

    python3 gen_book.py
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent
RES = ROOT / "src/main/resources"

SPREADS = [
    {
        "title": "Contents",
        "body": "Click any chapter to jump directly to its page.",
        "note": "",
        "art": "contents",
        "recipe": "",
    },
    {
        "title": "Intro",
        "body": "52 thousand years ago, the black hole at the center of our galaxy, Sagittarius A*, collided with another. The immense pressure combined with the extreme warping of spacetime as they got closer created a material so dense, it had no other choice but to molecularly form as a crystal like substance. When the black holes finally merged, the energy released sent gravitational waves, as well as fragments of the crystal like substance flying out in all directions at 50% the speed of light! Some of those fragments shot straight at us, and it appears they have arrived. Some fragments get slowed down by the other planets in our Solar System before reaching us, but most of them don't. As the ultra dense fragments crash into the earth, they burrow deep underground, unscathed. \"Hmmm\" you think to yourself, \"I wonder if that material could be useful.\"... There's only 1 way to find out.",
        "note": "",
        "art": "",
        "recipe": "",
    },
    {
        "title": "Fragments & Crystals",
        "body": "Place eight spacetime fragments around an amethyst shard to craft a spacetime crystal.\n\nSpacetime crystals seem to produce their own energy from nothing. \"How is this possible?\" You wonder to yourself. You have just discovered an infinite energy source. It may not have an infinite output (that would destroy the universe), but it appears to have a small stable output for an infinite amount of time. Looks like you will just have to make enough of them.\n\nWhile wearing a full Infinium armor set, insert spacetime crystals into the set to raise its power level and activate perks.",
        "note": "",
        "art": "",
        "recipe": "crystal",
    },
    {
        "title": "The Crystal Matrix",
        "body": "Every piece of Infinium armor contains an internal Spacetime Crystal Matrix. Inserting crystals raises its power, unlocks unique perks, and accelerates your armor's cosmic glow and particle trails.\n\nMatrix Capacities (Per Full Set):\n• Tier 1 (Astral / Cybernetic): 8{C} max (4 perks @ 2{C} each).\n• Tier 2 (Singularity / Cyber Psycho): 16{C} max (4 perks @ 4{C} each).\n• Tier 3 (Infinity): 32{C} max (8 perks @ 4{C} each).\n\nThe full matrix journey across all tiers requires exactly 80 Spacetime Crystals.\n\nThe X / 80 counter across the top of your inventory portrait tracks total matrix crystals inserted into your worn set. Any Tier 2 piece counts as an 8/8 maxed Tier 1 piece, and Infinity pieces count as fully maxed lower tiers.\n\nPerks unlock in whole-perk milestones. Clicking any milestone card in the Matrix screen automatically consumes the required crystals from your inventory in a single click.",
        "note": "",
        "art": "matrix",
        "recipe": "",
    },
    {
        "title": "Roguelike Dungeons",
        "body": "Put on your exploring boots! Fragments can be found in world chests, with a better chance in higher-rarity ones like bastions, fortresses, and End Cities. Dive into the deepest levels of Roguelike Dungeons for the best luck!\n\nPlace eight bones around a compass to craft a dungeon finder. It points to the nearest roguelike dungeon. While held, shows beams over dungeon entrances. Green normal, yellow large, red extra large.",
        "note": "",
        "art": "",
        "recipe": "finder",
    },
    {
        "title": "Infinium Ingot",
        "body": "Crystal in the center. Netherite ingots on the left and right. Obsidian on the top and bottom.\n\nThe corners are an Eye of Ender, a Diamond Block, a Wither Skeleton Skull, and a Redstone Block. Those four can go in any order.\n\nInfinium ingots are required to make/upgrade Infinium gear at a smithing table.",
        "note": "",
        "art": "",
        "recipe": "ingot",
    },
    {
        "title": "Upgrade Templates",
        "body": "Upgrade templates can be found in the same chests as fragments, but are rarer. Astral and Cybernetic are the common templates, while Singularity and Cyber Psycho are the rarer ones.\n\nTemplates are required to upgrade your infinium gear. See armor pages for more information.",
        "note": "",
        "art": "templates",
        "recipe": "templates",
    },
    {
        "title": "Tools",
        "body": "Sword: 12 dmg, applies 8s bleed. 100% full attack charge unleashes a 3-block wide piercing Energy Slash (5.5 dmg, applies bleed). Scales with Cyber attack speed and compresses mob i-frames.\n\nPickaxe: Toggle between Fortune IV or Silk Touch.\n\nAxe: Make any mined log drop any log of your choice.\n\nShovel: 1x1, 3x3, and 3x1 digging modes.\n\nHoe: Tills permanently saturated, un-trampleable, 100% lit farmland.\n\nSneak + Right Click on air to change modes.",
        "note": "",
        "art": "",
        "recipe": "tools",
    },
    {
        "title": "Astral Armor",
        "body": "Combine an Astral Template, Netherite armor, and 1 Infinium Ingot in a Smithing Table.\n\nTougher than netherite.\nFire and lava can't hurt you.\n\nFull Set: ({C} = crystals needed to unlock)\n\n1{C} Slowly repairs all gear in your inventory.\n\n2{C} Sometimes doesn't use up the block you place.\n\n3{C} +1 block reach.\n\n4{C} Items stack higher (64 becomes 96).",
        "note": "Helmet: Breathe underwater; Full Bright to see in the dark (B).\nChestplate: Built-in wings you can turn on and off.\nLeggings: Run a little faster; Quantum Tunnel zips you 8 blocks forward in mid-air.\nBoots: No fall damage; +1 jump height.",
        "art": "astral",
        "recipe": "astral",
    },
    {
        "title": "Cybernetic Armor",
        "body": "Crafted the same way as Astral, just with a Cybernetic template.\n\nDamage taken is 66.7% of netherite.\nFire and lava immunity.\n\nFull Set: ({C} = required crystals to activate perk)\n\n1{C} +10% mining speed.\n\n2{C} +1 block jump height.\n\n3{C} +20% attack speed and bow draw speed.\n\n4{C} Nearby mobs become slowed by 25% for 2s whenever you take damage (12s cooldown).",
        "note": "Helmet: Infinite oxygen; X-Ray Vision 16m (X / button).\nChestplate: Built-in Jet Wings (toggleable).\nLeggings: +10% Speed (No FOV change).\nBoots: No fall damage.",
        "art": "cybernetic",
        "recipe": "cybernetic",
    },
    {
        "title": "Singularity Armor",
        "body": "Singularity armor is obtained only by upgrading Astral gear with a Singularity Template and 1 Infinium Ingot in a Smithing Table.\n\nDamage taken is 57.1% of netherite.\nFire and lava immunity.\n\nFull Set: ({C} = required crystals to activate perk)\n\n2{C} Auto repair: every 5 seconds, all gear in your inventory regains 1 durability.\n\n4{C} 22% chance to not consume most building blocks.\n\n6{C} +2 block reach.\n\n8{C} Stacks grow from 64 to 128, 16 to 32, and unstackable items to 4.",
        "note": "Helmet: Infinite oxygen; Full Bright (B / button).\nChestplate: Built-in Elytra; Flight Thrust (hold Ctrl).\nLeggings: +5% Speed; Quantum Tunnel 12m (ability key, mid-air).\nBoots: No fall damage; +2 blocks jump.",
        "art": "singularity",
        "recipe": "singularity",
    },
    {
        "title": "Cyber Psycho Armor",
        "body": "Cyber Psycho armor is obtained only by upgrading Cybernetic gear with a Cyber Psycho Template and 1 Infinium Ingot in a Smithing Table.\n\nDamage taken is 57.1% of netherite.\nFire and lava immunity.\n\nFull Set: ({C} = required crystals to activate perk)\n\n2{C} +20% mining speed.\n\n4{C} +2 blocks jump height.\n\n6{C} +50% attack speed and bow draw speed.\n\n8{C} Nearby mobs become slowed by 50% for 4s whenever you take damage (10s cooldown).",
        "note": "Helmet: Infinite oxygen; X-Ray Vision 32m (X / button).\nChestplate: Built-in Jet Wings; Flight Thrust (hold Ctrl).\nLeggings: +15% Speed (No FOV change).\nBoots: No fall damage.",
        "art": "cyberpsycho",
        "recipe": "cyberpsycho",
    },
    {
        "title": "Infinity Armor",
        "body": "Combine a piece of Singularity gear with the same piece of Cyber Psycho gear, plus 1 Infinium Ingot to craft INFINITY ARMOR! It applies all previous bonus perks from all Infinium armor types, with even more unique bonuses!\n\nDamage taken is 50% of netherite.\nFire, lava, and explosion immunity.\nRocket-free flight: Hold Left Ctrl while flying to thrust up to max speed!\n\nFull Set: ({C} = required crystals to activate perk)\n\n4{C} +30% mining speed.\n\n8{C} +3 blocks jump height.\n\n12{C} +75% attack speed and bow draw speed.\n\n16{C} Auto repair: every 2 seconds, all gear in your inventory regains 1 durability.\n\n20{C} Stacks grow from 64 to 256, 16 to 64, and unstackable items to 16.\n\n24{C} Nearby mobs become slowed by 75% for 6s whenever you take damage (8s cooldown).\n\n28{C} 33% chance to not consume most building blocks.\n\n32{C} +3 block reach.",
        "note": "Helmet: Infinite oxygen; Dual Full Bright & 48m X-Ray.\nChestplate: Hybrid Flight & Surging Thrust (hold Ctrl).\nLeggings: +20% Speed; Quantum Tunnel 16m (ability key, mid-air).\nBoots: No fall damage; +3 blocks jump.",
        "art": "infinity",
        "recipe": "infinity",
    },
    {
        "title": "Dye It Your Way",
        "body": "Armor and tools can be dyed any color, any time, as many times as you want.\n\nPut a dye in the smithing template slot and your gear in the base slot. No ingot required.",
        "note": "",
        "art": "",
        "recipe": "dyes",
    },
    {
        "title": "Settings",
        "body": "Built in Elytra default to ON, Shift + Right Click on air with an empty hand to toggle OFF or assign your own hotkey.\n\nAll armor perks, particles, armor lighting, and custom sounds can be customized by clicking the purple gear icon in your inventory, to the right of the recipe book. It opens straight to the set you are wearing.\n\nThe settings button only appears when you obtain your first piece of Infinium gear.\n\nShooting stars, sky ripples, and the underwater shimmer can be toggled in the Infinium settings, under the World Effects tab.",
        "note": "",
        "art": "settings",
        "recipe": "",
    },
    {
        "title": "Meteor Shower",
        "body": "Roughly once every 10–15 in-game days, Sagittarius A*'s ancient cosmic debris collides with the upper atmosphere, raining crystal fragments from the night sky.\n\nThe Warning:\nTwo minutes before impact, a server-wide warning banner and countdown appear on screen, accompanied by an eerie musical swell.\n\nThe Bombardment:\nPhysical meteors streak down within a moving 10-chunk radius around active players. Distant meteors across the horizon are purely cosmetic.\nIncoming meteors screech overhead with a distinct falling whistle followed by a deep, ground-shaking thud.\n\nLethal Hazard:\nDirect hits deal a flat 1 heart (2 HP) of true cosmic damage that completely ignores all armor and enchantments! Always seek solid overhead shelter during the storm.\n\nCosmic Spoils:\nA mix of cosmetic and dropping meteors hit the ground. Venturing out to harvest impact sites yields roughly 8–10 Spacetime Fragments per shower, with an ultra-rare chance (<1%) to uncover a Smithing Template.",
        "note": "",
        "art": "meteor",
        "recipe": "",
    },
    {
        "title": "Movement & Flight Dynamics",
        "body": "Flight Thrust:\nHolding the Flight Thrust key (default Left Control) while airborne triggers rocket-free directional forward propulsion, ramping up to max vanilla elytra speed without fireworks.\nAvailable exclusively on Tier 2 & 3:\n• Singularity Chestplate\n• Cyber Psycho Chestplate\n• Infinity Chestplate\n(Astral and Cybernetic behave as standard Elytras).\n\nJump Augmentation:\nEquipping higher-tier boots grants permanent vertical jump boosts:\n• Astral Boots: +1 block bonus height\n• Singularity Boots: +2 blocks bonus height\n• Infinity Boots: +3 blocks bonus height\nBoots across all Infinium tiers eliminate 100% of fall damage.\n\nQuantum Tunnel:\nPressing the Quantum Tunnel ability key (bind it in Controls) while mid-air tunnels you along your look direction and launches you forward out of it:\n• Astral Leggings: 8 blocks, 8s cooldown\n• Singularity Leggings: 12 blocks, 6s cooldown\n• Infinity Leggings: 16 blocks, 4s cooldown\nThe warp non-phases along terrain and cannot penetrate solid structures. Includes chromatic audio-visual distortion signatures.",
        "note": "",
        "art": "additional_info",
        "recipe": "",
    },
    {
        "title": "Vision Systems & Tactical HUD",
        "body": "Sensory Enhancements (X-Ray Vision):\nAdvanced Infinium helmets project 3D wireframe bounding outlines through solid terrain, highlighting high-value ores, mob spawners, trial chambers, vaults, and storage containers.\nScan Radiuses by Tier:\n• Cybernetic Helmet: 16 blocks radius\n• Cyber Psycho Helmet: 32 blocks radius\n• Infinity Helmet: 48 blocks radius\nToggled via Hotkey (default X) or the inventory screen widget. Gracefully deactivates upon un-equipping.\n\nFull Bright Illumination:\nAvailable exclusively on:\n• Astral Helmet\n• Singularity Helmet\n• Infinity Helmet\nToggled via Hotkey (default B) or the on-screen toggle button. Overrides lightmaps to 100% pure illumination with zero green tint and zero fog pulsing.\n\nInfinity Helmet Dual-Button Interface:\nBecause the composite Infinity Helmet possesses both 48-block X-Ray and Full Bright, the HUD and GUI feature separate, non-overlapping side-by-side controls with independent hover tooltips and active status indicators.\n\nTactical HUD & Dungeon Tracker:\nActive vision modes render neatly in the top-left corner, automatically offsetting below the Dungeon Loot reset timer when within dungeon chunks.",
        "note": "",
        "art": "additional_info",
        "recipe": "",
    },
    {
        "title": "Combat Arts & Weapon Discharge",
        "body": "Energy Blade Dynamics:\nThe Infinium Sword deals 12 base melee damage and inflicts 8 seconds of Bleeding damage over time.\n\nPiercing Energy Slash:\nSwinging at 100% full attack charge unleashes a 3-block wide piercing crescent wave.\n• Wave Dimensions: 3.0 blocks wide, 0.6 blocks high.\n• Velocity & Range: 1.5 blocks/tick (~30 blocks/sec) spanning up to 36 blocks before dissipating.\n• Piercing Power: Penetrates all entities along its path, dealing 50% base sword damage (5.5 dmg) and inflicting 8s Bleeding to every mob struck. Raycasts against solid blocks and explodes on terrain collision.\n• Swing Threshold: Triggers strictly at 100% charge scale (≥0.99). Incomplete swings perform standard melee only.\n\nCyber-Tier Swing Recovery:\nWearing Cybernetic (+20%), Cyber Psycho (+50%), or Infinity (+75%) armor boosts attack speed. Weapon recovery charges significantly faster, allowing rapid barrages of energy slashes.\n\nI-Frame Compression:\nAttacking while wearing Cyber-tier armor dynamically compresses enemy damage immunity frames (i-frames) in direct proportion to your swing speed. Slashing projectiles reset mob invulnerability, ensuring piercing wave damage is never nullified by prior hits.",
        "note": "",
        "art": "additional_info",
        "recipe": "",
    },
    {
        "title": "World Phenomena & Atmospheric Tuning",
        "body": "Celestial Meteor Showers:\nRoughly every 10–15 in-game days, Sagittarius A*'s ancient cosmic debris strikes the upper atmosphere, raining physical and cosmetic meteorites.\n• Warning Phase: A 2-minute countdown banner and musical swell warn players prior to impact.\n• Lethal Impact: Direct physical meteor hits inflict 1 heart (2 HP) of true cosmic damage that bypasses all armor and protection enchantments. Take overhead shelter!\n• Harvest: Impact craters yield 8–10 Spacetime Fragments with a rare chance (<1%) for Smithing Templates.\n\nWorld FX Customization:\nThe pause settings menu contains master toggles for ambient cosmic visuals:\n• Shooting star head & trail frequency\n• Daytime celestial sky ripples\n• Underwater cosmic shimmer & particles\n\nMod Menu In-Depth Tuning:\nFor server owners and enthusiasts, the deep Mod Menu configuration screen offers granular density and frequency sliders for meteor showers, chest fragment loot chances, template drop rates, and visual effect density.",
        "note": "",
        "art": "additional_info",
        "recipe": "",
    },
]

LANG = {
        "subtitles.infinium.wings_enable": "Elytra deployed",
    "subtitles.infinium.wings_disable": "Elytra retracted",
    "subtitles.infinium.tool_mode": "Tool mode switched",
    "subtitles.infinium.flight_enter": "Flight engaged",
    "subtitles.infinium.flight_thrust": "Flight thrusters surging",
    "subtitles.infinium.crystal_insert": "Crystal inserted",
    "subtitles.infinium.crystal_perk": "Crystal perk unlocked",
    "subtitles.infinium.crystal_perk_upgrade": "Crystal perk upgraded",
    "subtitles.infinium.crystal_full": "Armor fully charged",
    "subtitles.infinium.crystal_denied": "Only spacetime crystals fit",
    "item.infinium.spacetime_fragments": "Spacetime Fragments",
    "item.infinium.strange_fragments": "Spacetime Fragments",
    "item.infinium.spacetime_crystal": "Spacetime Crystal",
    "item.infinium.infinium_guide": "Informational Crystal",
    "item.infinium.informational_crystal": "Informational Crystal",
    "infinium.style.cyberpsycho": "Cyber Psycho",
    "item.infinium.cyberpsycho_upgrade_smithing_template": "Cyber Psycho Upgrade",
    "item.infinium.smithing_template.applies_to": "Netherite, Astral or Cybernetic armor",
    "item.infinium.smithing_template.base_slot_description": "Netherite, Astral or Cybernetic armor",
    "item.infinium.astral_helmet": "Astral Helmet",
    "item.infinium.astral_chestplate": "Astral Chestplate",
    "item.infinium.astral_leggings": "Astral Leggings",
    "item.infinium.astral_boots": "Astral Boots",
    "item.infinium.cybernetic_helmet": "Cybernetic Helmet",
    "item.infinium.cybernetic_chestplate": "Cybernetic Chestplate",
    "item.infinium.cybernetic_leggings": "Cybernetic Leggings",
    "item.infinium.cybernetic_boots": "Cybernetic Boots",
    "item.infinium.singularity_helmet": "Singularity Helmet",
    "item.infinium.singularity_chestplate": "Singularity Chestplate",
    "item.infinium.singularity_leggings": "Singularity Leggings",
    "item.infinium.singularity_boots": "Singularity Boots",
    "item.infinium.cyberpsycho_helmet": "Cyber Psycho Helmet",
    "item.infinium.cyberpsycho_chestplate": "Cyber Psycho Chestplate",
    "item.infinium.cyberpsycho_leggings": "Cyber Psycho Leggings",
    "item.infinium.cyberpsycho_boots": "Cyber Psycho Boots",
    "advancements.infinium.craft_crystal.title": "Infinite Potential",
    "advancements.infinium.craft_crystal.description": "Synthesize your first Spacetime Crystal from cosmic fragments",
    "advancements.infinium.full_set.title": "Harmonic Attunement",
    "advancements.infinium.full_set.description": "Don a complete set of Infinium armor of any family",
    "advancements.infinium.astral_max_charge.title": "Starlight Apex",
    "advancements.infinium.astral_max_charge.description": "Infuse a full Astral armor set to maximum capacity (8 Crystals)",
    "advancements.infinium.cybernetic_max_charge.title": "Vector Overclock",
    "advancements.infinium.cybernetic_max_charge.description": "Infuse a full Cybernetic armor set to maximum capacity (8 Crystals)",
    "advancements.infinium.singularity_max_charge.title": "Event Horizon",
    "advancements.infinium.singularity_max_charge.description": "Infuse a full Singularity armor set to maximum capacity (16 Crystals)",
    "advancements.infinium.cyberpsycho_max_charge.title": "System Overdrive",
    "advancements.infinium.cyberpsycho_max_charge.description": "Infuse a full Cyber Psycho armor set to maximum capacity (16 Crystals)",
    "advancements.infinium.infinity_max_charge.title": "Master of Spacetime",
    "advancements.infinium.infinity_max_charge.description": "Reach absolute cosmic perfection with a fully charged Infinity set (32 Crystals)",
    "advancements.infinium.survive_meteor_shower.title": "Crimson Sky Survivor",
    "advancements.infinium.survive_meteor_shower.description": "Endure and survive a celestial Sagittarius A* meteor shower",
}

HOOK = '\nexec(compile(open("gen_book.py", encoding="utf-8").read(), "gen_book.py", "exec"))\n'


def merge_lang():
    path = RES / "assets/infinium/lang/en_us.json"
    if not path.exists():
        print("gen_book: no en_us.json yet, lang merge skipped")
        return
    data = json.loads(path.read_text(encoding="utf-8"))
    data.update(LANG)
    for key, value in list(data.items()):
        if isinstance(value, str) and "CyberPsycho" in value:
            data[key] = value.replace("CyberPsycho", "Cyber Psycho")
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def patch_info_java():
    path = ROOT / "src/main/java/dev/justin/infinium/Info.java"
    if not path.exists():
        return
    text = path.read_text(encoding="utf-8")
    updated = text.replace("CyberPsycho", "Cyber Psycho")
    if updated != text:
        path.write_text(updated, encoding="utf-8")
        print("gen_book: patched CyberPsycho spacing in Info.java")


def write_pages():
    blob = json.dumps({"spreads": SPREADS}, ensure_ascii=False)
    if "centre" in blob.lower():
        raise SystemExit("gen_book: pages still say centre")
    path = RES / "assets/infinium/guide/pages.json"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps({"spreads": SPREADS}, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def write_models():
    item = {"model": {"type": "minecraft:model", "model": "infinium:item/infinium_guide"}}
    model = {"parent": "minecraft:item/generated", "textures": {"layer0": "infinium:item/infinium_guide"}}
    (RES / "assets/infinium/items").mkdir(parents=True, exist_ok=True)
    (RES / "assets/infinium/models/item").mkdir(parents=True, exist_ok=True)
    (RES / "assets/infinium/textures/item").mkdir(parents=True, exist_ok=True)
    (RES / "assets/infinium/items/infinium_guide.json").write_text(json.dumps(item, indent=2) + "\n", encoding="utf-8")
    (RES / "assets/infinium/models/item/infinium_guide.json").write_text(json.dumps(model, indent=2) + "\n", encoding="utf-8")
    mcmeta = {"animation": {"frametime": 4, "interpolate": False}}
    (RES / "assets/infinium/textures/item/infinium_guide.png.mcmeta").write_text(
        json.dumps(mcmeta, indent=2) + "\n", encoding="utf-8")


def write_recipe():
    recipe = {
        "type": "minecraft:crafting_shapeless",
        "category": "misc",
        "ingredients": ["minecraft:book", "infinium:spacetime_fragments"],
        "result": {"id": "infinium:infinium_guide", "count": 1},
    }
    path = RES / "data/infinium/recipe/infinium_guide.json"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(recipe, indent=2) + "\n", encoding="utf-8")


def _insert(path: Path, names, client=False):
    if not path.exists():
        return
    data = json.loads(path.read_text(encoding="utf-8"))
    key = "client" if client and "client" in data else "mixins"
    bucket = data.setdefault(key, [])
    for name in names:
        if name not in bucket:
            bucket.append(name)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def patch_generated_json():
    _insert(RES / "infinium.mixins.json", ["InfiniumInitMixin", "ItemNameMixin", "ItemEntityMixin"])
    _insert(RES / "infinium.client.mixins.json", ["InfiniumClientBookMixin"], client=True)
    fabric = RES / "fabric.mod.json"
    if not fabric.exists():
        return
    data = json.loads(fabric.read_text(encoding="utf-8"))
    plugins = data.setdefault("entrypoints", {}).setdefault("jei_mod_plugin", [])
    cls = "dev.justin.infinium.compat.InfiniumJeiDyeHider"
    if cls not in plugins:
        plugins.append(cls)
    fabric.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def install_hooks():
    gen_data = ROOT / "gen_data.py"
    if gen_data.exists():
        text = gen_data.read_text(encoding="utf-8")
        if "gen_book.py" not in text:
            gen_data.write_text(text.rstrip() + "\n" + HOOK, encoding="utf-8")
            print("gen_book: hooked gen_data.py")
    gen_all = ROOT / "gen_all.sh"
    if gen_all.exists() and "gen_guide_art.py" not in gen_all.read_text(encoding="utf-8"):
        lines = gen_all.read_text(encoding="utf-8").splitlines(keepends=True)
        out = []
        inserted = False
        for line in lines:
            if not inserted and "hue_fix" in line:
                out.append("python3 gen_guide_art.py\n")
                inserted = True
            out.append(line)
        if not inserted:
            out.append("\npython3 gen_guide_art.py\n")
        gen_all.write_text("".join(out), encoding="utf-8")
        print("gen_book: hooked gen_all.sh")


def main():
    merge_lang()
    patch_info_java()
    write_pages()
    write_models()
    write_recipe()
    patch_generated_json()
    install_hooks()
    print("gen_book: pages, models, recipe")


if __name__ == "__main__":
    main()
