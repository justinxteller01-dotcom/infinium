#!/usr/bin/env python3
"""Guidebook v2 generator -> src/main/resources/assets/infinium/guide/pages.json.
Schema per page: id, chapter, title, subtitle, keywords[], body, visual, caption, numbers.
Numbers ONLY as {n:key} tokens (resolved by client/guide/GuideNumbers.java). {key:id} = bound key name. {C} = crystal icon.
Intro body is copied verbatim from the previous pages.json (never edited)."""
import json, os
P = 'src/main/resources/assets/infinium/guide/pages.json'
old = json.load(open(P))
intro = None
if 'pages' in old:
    intro = next(p['body'] for p in old['pages'] if p['id'] == 'intro')
else:
    intro = next(s['body'] for s in old['spreads'] if s['title'] == 'Intro')

CH = ["Start", "Find", "Make", "Armor", "Abilities", "Tools", "World"]
pages = []
def pg(id, ch, title, sub, kw, body, visual='none', caption='', numbers=''):
    pages.append(dict(id=id, chapter=ch, title=title, subtitle=sub, keywords=kw, body=body,
                      visual=visual, caption=caption, numbers=numbers))

# ---- Chapter 0: Start
pg('contents', 0, 'Contents', 'Click a page to open it', ['index', 'search'], '', 'contents')
pg('intro', 0, 'Intro', '', ['story', 'lore'], intro, 'intro')
pg('roadmap', 0, 'How Infinium Works', 'The road from fragment to Infinity', ['roadmap', 'progression', 'start', 'guide'],
   "Find Spacetime Fragments and turn them into crystals.\n"
   "Crystals make Infinium Ingots. Ingots and upgrade templates turn netherite armor into your first set.\n"
   "Charge that set with crystals, then upgrade it to tier 2. Combine both tier 2 lines to make Infinity.",
   'flow', 'Fragments, crystal, ingot, armor, upgrade, Infinity')

# ---- Chapter 1: Find
pg('fragments', 1, 'Spacetime Fragments', 'Where it all starts', ['fragment', 'loot', 'chest', 'structure'],
   "Fragments are the base of everything in this mod.\n"
   "Look in structure chests. The Nether, the End and ancient structures hide the most. Villages rarely have any.\n"
   "Dungeons and meteor craters have them too.",
   'items:infinium:spacetime_fragments', 'Spacetime Fragments')
pg('dungeons', 1, 'Roguelike Dungeons', 'Deep floors, better loot', ['dungeon', 'loot', 'refill', 'chest'],
   "Dungeons are full of fragments and templates.\n"
   "The deeper the floor, the better the loot.\n"
   "Chests you loot fill up again after a while, so come back later.",
   'items:infinium:dungeon_finder', 'Dungeon loot refills',
   "Numbers: chests refill {n:dungeon_refill_days} days after you loot them.")
pg('finder', 1, 'Dungeon Finder', 'Point the way down', ['dungeon', 'finder', 'compass', 'bones'],
   "Put 8 bones around a compass.\n"
   "Hold it to point at the nearest dungeon. Beams of light show over dungeon entrances.\n"
   "The beam color tells you how big the dungeon is.",
   'recipe:finder', 'Crafting table')
pg('meteors', 1, 'Meteor Showers', 'Look up, then take cover', ['meteor', 'shower', 'crater', 'sky'],
   "Every so often a meteor shower comes at night. You get a warning first.\n"
   "Meteors fall near players. A direct hit hurts through any armor, so get under a roof.\n"
   "Afterwards, loot the craters for fragments and, very rarely, a template.",
   'meteor', 'A meteor shower',
   "Numbers: every {n:meteor_min_days}-{n:meteor_max_days} nights, {n:meteor_warning_min} minute warning, a hit costs {n:meteor_hit_hearts} heart. "
   "Each meteor has a {n:meteor_loot} chance to drop {n:meteor_frag_min}-{n:meteor_frag_max} fragments; "
   "templates {n:meteor_tpl_t1} (tier 1) and {n:meteor_tpl_t2} (tier 2).")
pg('templates', 1, 'Upgrade Templates', 'The shape of your armor', ['template', 'smithing', 'upgrade'],
   "Templates are in the same chests as fragments, but rarer. Very rarely a meteor drops one.\n"
   "Astral and Cybernetic templates are the common ones. Singularity and Cyber Psycho templates are rare.",
   'recipe:templates', 'Each template sets an armor line')

# ---- Chapter 2: Make
pg('crystals', 2, 'Spacetime Crystals', 'Power for your armor', ['crystal', 'craft', 'amethyst', 'charge'],
   "Put 8 fragments around an amethyst shard to make a crystal {C}.\n"
   "Crystals make ingots, and they charge your armor to unlock its perks.",
   'recipe:crystal', 'Crafting table')
pg('ingot', 2, 'Infinium Ingot', 'Used in every Infinium craft', ['ingot', 'craft', 'netherite'],
   "A crystal goes in the middle, netherite on the sides, obsidian on top and bottom.\n"
   "The four corners take an Eye of Ender, a Diamond Block, a Wither Skeleton Skull and a Redstone Block, in any order.",
   'recipe:ingot', 'Crafting table')
pg('first_set', 2, 'Your First Set', 'Pick a path', ['astral', 'cybernetic', 'smithing', 'swap', 'first'],
   "At a smithing table, use a template, a netherite piece and an ingot.\n"
   "Astral suits builders and explorers. Cybernetic suits fighters and miners.\n"
   "Changed your mind? Use the other template on a piece to swap it. The template is used up.",
   'recipe:first_set', 'Smithing table: make, or swap lines')

# ---- Chapter 3: Armor
pg('tiers', 3, 'How Tiers Work', 'Tier 1, tier 2, Infinity', ['tier', 'upgrade', 'singularity', 'cyber psycho', 'infinity'],
   "Tier 1 is Astral or Cybernetic.\n"
   "Tier 2: upgrade a fully charged tier 1 piece. Astral becomes Singularity, Cybernetic becomes Cyber Psycho.\n"
   "A tier 2 piece counts as fully charged tier 1. It keeps every tier 1 perk at full strength. Crystals then upgrade those perks.\n"
   "Tier 3: combine a Singularity piece with the same Cyber Psycho piece and an ingot to make Infinity. It counts as both lines fully charged and upgrades all 9 perks.\n"
   "Every set: fire and lava can't hurt you, no fall damage, built-in wings.",
   'recipe:tiers', 'Smithing table: upgrade, then combine')
pg('matrix', 3, 'The Crystal Matrix', 'Charge your set', ['matrix', 'crystal', 'charge', 'perk', 'unlock'],
   "Wear a full set and put crystals {C} into it to unlock its perks one by one. Click a milestone to unlock it.\n"
   "The counter tracks every crystal you have ever put in.\n"
   "More charge also means a stronger glow.",
   'matrix', 'Tier 1, tier 2 and Infinity milestones',
   "Numbers: tier 1 holds {n:crystals_t1} crystals, tier 2 holds {n:crystals_t2}, Infinity holds {n:crystals_t3}; milestones are spread evenly along the bar (Cybernetic: {n:milestones_cyber}). "
   "{n:crystals_total} for the whole journey.")

def armor(id, fam, title, sub, kw, body, caption, numbers):
    pg(id, 3, title, sub, kw, body, 'armor:%d' % fam, caption, numbers)

armor('astral', 0, 'Astral Armor', 'Tier 1, the builder line', ['astral', 'armor', 'tier 1', 'builder'],
   "Forged from fallen starlight, it hums with the memory of deep space.\n"
   "Takes about a third less damage than netherite armor. Fire and lava can't hurt you.\n"
   "Helmet: breathe underwater forever. Press {key:xray_pulse} to see ores nearby.\n"
   "Chestplate: built-in wings.\n"
   "Leggings: move slightly faster. Quantum Tunnel zips you forward, on the ground or in the air.\n"
   "Boots: no fall damage.\n"
   "Hold jump to jump higher.\n"
   "Full set (charge with crystals), in unlock order: repairs all your gear over time, sometimes gives back a placed block, unlocks the sword's energy slashes, items stack higher, and longer reach.",
   'Full Astral set, fully charged',
   "Numbers: {n:dmg_t1} of netherite damage. X-Ray {n:xray_t1} blocks. Speed +{n:speed_astral}. Tunnel {n:tunnel_t1} blocks, every {n:tunnel_cd_t1}s. "
   "Jump up to {n:jump_astral} blocks. Repair 1 durability every {n:repair_t1}s. Block back {n:keep_t1}. "
   "Stacks 64 to {n:stack64_t1}, 16 to {n:stack16_t1}, 1 to {n:stack1_t1}. Reach +{n:reach_t1}.")
armor('cybernetic', 1, 'Cybernetic Armor', 'Tier 1, the fighter line', ['cybernetic', 'armor', 'tier 1', 'fighter'],
   "Fused with cosmic energy. Everything feels a little... faster.\n"
   "Takes about a third less damage than netherite armor. Fire and lava can't hurt you.\n"
   "Helmet: breathe underwater forever. Press {key:xray_pulse} to see ores nearby.\n"
   "Chestplate: built-in jet wings.\n"
   "Leggings: faster running speed (your view won't zoom).\n"
   "Boots: no fall damage.\n"
   "Full set (charge with crystals), in unlock order: mine faster, jump again in mid-air (wings off), nearby mobs slow down when you get hit, and swing swords and draw bows faster.",
   'Full Cybernetic set, fully charged',
   "Numbers: {n:dmg_t1} of netherite damage. X-Ray {n:xray_t1} blocks. Speed +{n:speed_cyber}. Mining +{n:mining_t1}. "
   "{n:airjumps_t1} air jump. Slow {n:slow_t1} for {n:slowdur_t1}s, every {n:slowcd_t1}s, within {n:slow_radius} blocks. Attack +{n:attack_t1}.")
armor('singularity', 2, 'Singularity Armor', 'Tier 2, upgrade your Astral armor', ['singularity', 'armor', 'tier 2', 'upgrade'],
   "Compressed to the point of collapse, it bends gravity around the wearer.\n"
   "Upgrade your Astral armor with a Singularity template and an ingot. It counts as fully charged Astral and keeps every Astral perk. Crystals upgrade them.\n"
   "Takes almost half the damage netherite would.\n"
   "Helmet: X-Ray reaches farther.\n"
   "Chestplate: built-in wings. Hold {key:flight_thrust} while flying to boost, no rockets needed.\n"
   "Leggings: move slightly faster, with a longer Quantum Tunnel.\n"
   "Boots: no fall damage.\n"
   "Hold jump to jump even higher.\n"
   "Full set, in unlock order: faster repair, gets blocks back more often, energy slashes, bigger stacks, more reach.",
   'Full Singularity set, fully charged',
   "Numbers: {n:dmg_t2} of netherite damage. X-Ray {n:xray_t2} blocks. Speed +{n:speed_sing}. Tunnel {n:tunnel_t2} blocks, every {n:tunnel_cd_t2}s. "
   "Jump up to {n:jump_sing} blocks. Repair every {n:repair_t2}s. Block back {n:keep_t2}. "
   "Stacks 64 to {n:stack64_t2}, 16 to {n:stack16_t2}, 1 to {n:stack1_t2}. Reach +{n:reach_t2}.")
armor('cyberpsycho', 3, 'Cyber Psycho Armor', 'Tier 2, upgrade your Cybernetic armor', ['cyber psycho', 'cyberpsycho', 'armor', 'tier 2', 'upgrade'],
   "Putting this much energy into one suit of armor shouldn't be possible. It's like it knows your movements before you do.\n"
   "Upgrade your Cybernetic armor with a Cyber Psycho template and an ingot. It counts as fully charged Cybernetic and keeps every Cybernetic perk. Crystals upgrade them.\n"
   "Takes almost half the damage netherite would.\n"
   "Helmet: X-Ray reaches farther.\n"
   "Chestplate: jet wings. Hold {key:flight_thrust} while flying to boost.\n"
   "Leggings: super fast running speed.\n"
   "Boots: no fall damage.\n"
   "Full set, in unlock order: mine faster, more air jumps, a stronger slow, much faster attacks.",
   'Full Cyber Psycho set, fully charged',
   "Numbers: {n:dmg_t2} of netherite damage. X-Ray {n:xray_t2} blocks. Speed +{n:speed_cp}. Mining +{n:mining_t2}. "
   "{n:airjumps_t2} air jumps. Slow {n:slow_t2} for {n:slowdur_t2}s, every {n:slowcd_t2}s. Attack +{n:attack_t2}.")
armor('infinity', 4, 'Infinity Armor', 'Tier 3, upgrade both lines into one', ['infinity', 'armor', 'tier 3', 'upgrade'],
   "No edge, no limit — it stretches as far as the wearer dares to go.\n"
   "Upgrade a Singularity piece with the same Cyber Psycho piece and an ingot. It counts as both lines fully charged and has all 9 perks. Crystals upgrade them to Infinity strength.\n"
   "Takes half the damage netherite would. Fire, lava and explosions can't hurt you.\n"
   "Helmet: the widest X-Ray.\n"
   "Chestplate: hybrid wings. Hold {key:flight_thrust} to boost, no rockets needed.\n"
   "Leggings: maximum running speed and the longest tunnel.\n"
   "Boots: no fall damage.\n"
   "Hold jump to reach ridiculous heights.\n"
   "Full set, in unlock order: repair, mining, blocks back, air jumps, energy slashes, slow, stacks, attack speed, reach, all at their best.",
   'Full Infinity set, fully charged',
   "Numbers: {n:dmg_t3} of netherite damage. X-Ray {n:xray_t3} blocks. Speed +{n:speed_inf}. Tunnel {n:tunnel_t3} blocks, every {n:tunnel_cd_t3}s. "
   "Jump up to {n:jump_inf} blocks. Repair every {n:repair_t3}s. Mining +{n:mining_t3}. Block back {n:keep_t3}. {n:airjumps_t3} air jumps. "
   "Slow {n:slow_t3} for {n:slowdur_t3}s, every {n:slowcd_t3}s. Stacks 64 to {n:stack64_t3}, 16 to {n:stack16_t3}, 1 to {n:stack1_t3}. "
   "Attack +{n:attack_t3}. Reach +{n:reach_t3}.")
armor('bread', 5, 'Bread Armor', 'A joke set', ['bread', 'joke', 'armor'],
   "Made of bread. Really.\n"
   "It protects about as well as leather and it never breaks.\n"
   "Craft it from bread like any armor.",
   'Full Bread set', '')
pg('care', 3, 'Care & Durability', 'Keep your gear working', ['durability', 'repair', 'enchant', 'broken', 'dye'],
   "When a piece is almost broken, its enchantments stop working. Its perks keep going.\n"
   "Repair it to turn the enchantments back on.\n"
   "Dyeing never changes perks.",
   'items:infinium:infinium_chestplate', 'Repair before it breaks',
   "Numbers: enchantments stop at {n:durability_floor} durability left.")

# ---- Chapter 4: Abilities
pg('wings', 4, 'Wings & Flight Thrust', 'Fly without rockets', ['wings', 'elytra', 'flight', 'thrust', 'boost', 'toggle'],
   "Your wings are on by default. Shift + Right-Click on air with an empty hand to turn them on or off. You can also set a key in Controls.\n"
   "Tier 2 and 3: hold {key:flight_thrust} while flying to speed up to full elytra speed. No rockets needed.",
   'armor:4', 'Infinity wings deployed',
   "Numbers: Flight Thrust builds up to full speed over {n:thrust_ramp_s}s.")
pg('jumping', 4, 'Jumping', 'Higher and again', ['jump', 'air jump', 'double jump', 'boots'],
   "Astral-line boots: tap jump for 1 block, hold it to go higher.\n"
   "Cyber-line full sets: jump again in mid-air. This only works with your wings off.\n"
   "Astral, Singularity and Infinity also give you a small push forward in the air. Every air jump gets a forward push too.",
   'items:infinium:infinium_boots', 'Boots',
   "Numbers: hold-jump up to {n:jump_astral} / {n:jump_sing} / {n:jump_inf} blocks (Astral / Singularity / Infinity). "
   "Air jumps {n:airjumps_t1} / {n:airjumps_t2} / {n:airjumps_t3} (Cybernetic / Cyber Psycho / Infinity).")
pg('tunnel', 4, 'Quantum Tunnel', 'Zip forward through the air', ['tunnel', 'warp', 'dash', 'leggings'],
   "Astral-line leggings. Press {key:warp} on the ground or in mid-air to tunnel forward and shoot out the end.\n"
   "It stops at solid walls.\n"
   "The key is not set at first. Pick one in Controls.",
   'items:infinium:infinium_leggings', 'Leggings',
   "Numbers: {n:tunnel_t1} / {n:tunnel_t2} / {n:tunnel_t3} blocks, every {n:tunnel_cd_t1} / {n:tunnel_cd_t2} / {n:tunnel_cd_t3}s.")
pg('xray', 4, 'X-Ray Pulse', 'See what is hidden', ['xray', 'x-ray', 'ores', 'pulse', 'helmet'],
   "Every Infinium helmet has it.\n"
   "Press {key:xray_pulse} to show nearby ores and mobs for a moment.\n"
   "Higher tiers see farther.",
   'items:infinium:infinium_helmet', 'Helmet',
   "Numbers: {n:xray_t1} / {n:xray_t2} / {n:xray_t3} blocks (tier 1 / tier 2 / Infinity).")
pg('slow', 4, 'Slow Aura', 'Make them back off', ['slow', 'aura', 'mobs', 'cybernetic'],
   "Cyber-line full sets.\n"
   "When you get hit, nearby mobs slow down for a few seconds.\n"
   "It needs a short rest before it works again.",
   'items:infinium:infinium_chestplate', 'Cyber-line full set',
   "Numbers: {n:slow_t1} / {n:slow_t2} / {n:slow_t3} slower, for {n:slowdur_t1} / {n:slowdur_t2} / {n:slowdur_t3}s, "
   "every {n:slowcd_t1} / {n:slowcd_t2} / {n:slowcd_t3}s, within {n:slow_radius} blocks.")

# ---- Chapter 5: Tools
pg('sword', 5, 'Sword & Energy Slash', 'Slash, slash, beam', ['sword', 'slash', 'bleed', 'cosmic', 'radiation', 'cyberburn', 'burn', 'energy'],
   "The sword hits hard. Its slashes and beam inflict Cosmic Radiation with Astral armor, Cyberburn with Cybernetic or Cyber Psycho armor, and make enemies bleed with Singularity or Infinity armor.\n"
   "Your first two swings send out energy slashes. The third swing fires an energy beam that hits even harder.\n"
   "Energy mode only works while you wear a full Infinium armor set, and each set has its own slash sounds. A fully charged swing then fires a piercing energy slash.\n"
   "Without a full set there's no energy slash. It's just a normal sword.\n"
   "Sneak + Right-Click on air to switch between Energy and Normal mode.\n"
   "Cyber armor lets you swing faster and hit fast enemies again sooner.",
   'recipe:sword', 'Smithing table',
   "Damage by armor tier: Tier 1 (no set, Astral, Cybernetic) {n:sword_dmg}, Tier 2 (Singularity, Cyber Psycho) {n:sword_dmg_t2}, Tier 3 (Infinity) {n:sword_dmg_t3}. Cosmic Radiation/Bleed/Cyberburn {n:bleed_s}s (3s Astral/Cybernetic, 4s Singularity/Cyber Psycho, 5s Infinity). Slash deals {n:slash_t1} / {n:slash_t2} / {n:slash_t3} of the tier damage ({n:slashdmg_t1} / {n:slashdmg_t2} / {n:slashdmg_t3}); the beam deals {n:beam_t1} / {n:beam_t2} / {n:beam_t3} ({n:beamdmg_t1} / {n:beamdmg_t2} / {n:beamdmg_t3}).")
pg('tools', 5, 'Mining Tools', 'Pick, axe and shovel', ['pickaxe', 'axe', 'shovel', 'mode', 'fortune', 'silk'],
   "Pickaxe: switch between Fortune IV and Silk Touch.\n"
   "Axe: logs drop the wood type you choose.\n"
   "Shovel: dig 1x1, 3x3 or 3x1.\n"
   "Sneak + Right-Click on air to switch modes. Tools copy your armor's look.",
   'recipe:tools', 'Smithing table: netherite tool + ingot')
pg('hoe', 5, 'Hoe', 'Perfect farmland', ['hoe', 'farm', 'farmland'],
   "Farmland you make stays wet and lit, and it can't be trampled.\n"
   "Two modes: standard, or hoe a 3x3 area into Infinium farmland at once.\n"
   "Sneak + Right-Click on air to switch modes.",
   'items:infinium:infinium_hoe', 'Infinium Hoe')
pg('rocket', 5, 'Infinium Rocket', 'One rocket, forever', ['rocket', 'firework', 'netherite rocket', 'elytra'],
   "Put any firework rocket and an ingot in a smithing table.\n"
   "The Infinium Rocket is never used up.",
   'recipe:rocket', 'Smithing table')

# ---- Chapter 6: World & Settings
pg('dye', 6, 'Dye It Your Way', 'Your colors', ['dye', 'color', 'glow'],
   "Put a dye in the template slot and your gear in the base slot. No ingot needed.\n"
   "Re-dye any time. Dye never changes perks.",
   'recipe:dyes', 'Smithing table')
pg('settings', 6, 'Settings', 'Make it yours', ['settings', 'config', 'particles', 'sound', 'toggle', 'perks'],
   "Click the purple gear next to the recipe book. It shows up once you own a piece.\n"
   "Turn perks, particles, lighting and sounds on or off for your set.\n"
   "The World Effects tab has shooting stars, sky ripples and the underwater shimmer.",
   'settings', 'Particles and wings, on and off')
pg('keys', 6, 'Keys & HUD', 'Every key in one place', ['keys', 'controls', 'hotkey', 'hud', 'bind'],
   "Wings: Shift + Right-Click on air, or {key:toggle_wings}.\n"
   "Flight Thrust: {key:flight_thrust}.\n"
   "Quantum Tunnel: {key:warp}.\n"
   "X-Ray Pulse: {key:xray_pulse}.\n"
   "Change any of them in Controls.\n"
   "The HUD sits top left, below the dungeon timer.",
   'items:infinium:infinium_helmet', 'Set keys in Options > Controls')
pg('fair_play', 6, 'Fair Play', 'On servers', ['server', 'trade', 'rules', 'admin'],
   "Some servers limit trading Infinium gear or block certain areas. Ask your server admin if something won't work.",
   'none')

json.dump({"chapters": CH, "pages": pages}, open(P, 'w'), indent=1, ensure_ascii=False)
print(len(pages), 'pages')
