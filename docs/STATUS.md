# Infinium: project status (living file)
Updated 2026-10-28 ~4:00 PM ET. Branch `batch1`, private repo `justinxteller01-dotcom/infinium`.
Notation: x/y/z = tier 1/2/3 = Astral+Cybernetic / Singularity+Cyber Psycho / Infinity.
Sources: `/home/box/shared/minecraft/{MINECRAFT_BRIEF,PERK_VALUES_MASTER}.md`, `PROJECT_MAP.md`, `review_verification_2026-10-26.md`, git log, GitHub releases.
Update rule: on every release, edit section 1 and add a line to section 6.

## 1. Current release: `batch1-b1bc2b6` (pre-release, 2026-10-28 4:22 PM ET)
- **Crystal order**: Astral/Singularity repair, block saver, saber, stacks, reach; Cybernetic/Cyber Psycho mining, jump, slow, attack; Infinity repair, mining, block saver, jump, saber, slow, stacks, attack, reach. Saber = energy slash milestone (Astral needs it before slashes fire). Crystal screen lays 5/9 cards in two rows.
- **Energy slash / beam**: slash 60/70/80% of the tier base = 6 / 7.7 / 9.6; beam 75/85/95% = 7.5 / 9.35 / 11.4.
- **Sword damage by 3 tiers**: Tier 1 (no armor, Astral, Cybernetic) 10, Tier 2 (Singularity, Cyber Psycho) 11, Tier 3 (Infinity) 12. 
- **Cyberburn**: the Burn DoT display name is now "Cyberburn" (lang, tooltip, guide, docs). Registry id `infinium:burning` is unchanged.
- **DoT durations** (slash/beam only, by owner set): Astral and Cybernetic 3s, Singularity and Cyber Psycho 4s, Infinity 5s.
- **DoT ticks never give i-frames**: shared `DotDamage` helper resets `invulnerableTime` and lastHurt for Bleed and Cyberburn ticks.
- **Hoe 3x3**: Standard and 3x3 modes (Sneak + Right-Click air), set-based mode-switch sound helper.
- Also in batch1 (from earlier pre-releases): melee has no DoT; slash/beam DoT is Cyberburn (pink embers, no fire) for Cybernetic and Cyber Psycho owners, Bleed otherwise; guide v2 (paginated book); lore lines; leggings and boots wording; title-menu village backdrop; sky v7 meteors.
- Status: **untested in game.**

## 2. Locked decisions
| Area | Decision | Date |
|---|---|---|
| Lore lines (top of guide armor pages) | Astral: "Forged from fallen starlight, it hums with the memory of deep space." / Cybernetic: "Fused with cosmic energy. Everything feels a little... faster." / Singularity: "Compressed to the point of collapse, it bends gravity around the wearer." / Cyber Psycho: "Putting this much energy into one suit of armor shouldn't be possible. It's like it knows your movements before you do." / Infinity: "No edge, no limit — it stretches as far as the wearer dares to go." | 10-28 |
| Wording | Plain language a casual player gets right away; say what it does; no percentage dumps; keep only simple numbers (`+1 jump height`, `8 blocks`); one short line. Tier 2/3 crystals **UPGRADE** perks (tier 1 **UNLOCKS**). Leggings: Astral/Singularity "move slightly faster", escalating up to Infinity "maximum running speed". Cybernetic: "increased attack speed with swords and bows". | 10-28 |
| Colors | Mod-wide: purple, pink, magenta, black (+ shades) only. No cyan/teal, gold, red-orange, green or blue. Exempt: player dye tints and X-Ray Pulse target colors. | 10-28 |
| Flight picks | Cybernetic: option 4 jet wings, clean, no thrusters. Cyber Psycho: round-7 variant A armor_hue plates (inner wing pair removed), dye-colored border glow + afterglow trail on thrust. Infinity: Event Horizon + echo outline, fixed 0.80 scale. Astral and Singularity: no elytra (magic flight; purple wave / tight chaotic particles). Particle limit on all wings; mirrored wings; one linear opacity ramp. | 10-28 |
| Warp (Quantum Tunnel) | Distance 8/12/16 blocks; cooldown **8/6/4 s** (replaces 12/9/6); launch forward along the look direction, small/medium/large. | 10-28 |
| HUD | Settings button appears on first Infinium item and stays. Energy bar only with a full set. "x/80" counter appears on first full set and stays. Abilities are named "X-Ray Pulse" and "Quantum Tunnel"; settings has an "Open Controls" button. | 10-28 |
| Jump + warp | Boots tap/hold height: +1/+2/+3. In-air push tiny/small/largest. Air jumps only with elytra off: Cybernetic 1, Cyber Psycho 2, Infinity 3 (4 jumps total). A warp refunds the 3 air jumps, not the ground jump. Only Infinity has both multi-jump and warp. | 10-28 |
| Audio | Every set gets its own sound family (flight, slash, mode-switch, air jump). Cyber/Psycho: energy-charge sounds (Cyberpunk + Fire Force flair), not 8-bit electric. Infinity = a blend of Astral + Singularity. Cyber Psycho thrust: ramp-up, then a subtle hum, then a fade. Built one piece at a time, at Justin's PC. | 10-28 |
| Other values | Mob slow 25/50/75%, 2/4/6 s, cd 12/10/8 s. X-Ray Pulse 10/20/30 blocks, cd 40/30/20 s. Crystal slots 8/16/32. Full Bright removed. | 10-26 to 10-28 |
| Sword damage | Sword damage (2026-10-28): 3 tiers. Tier 1 (no armor, Astral or Cybernetic) 10, Tier 2 (Singularity or Cyber Psycho) 11, Tier 3 (Infinity) 12. Item attribute 10 + set bonus +0/+1/+2 on hit; slash/beam % use the tier value. | 10-28 |
| Crystal order (2026-10-28) | Astral and Singularity: repair, block saver, saber, stacks, reach. Cybernetic and Cyber Psycho: mining, jump, slow, attack. Infinity (alternates the two): repair, mining, block saver, jump, saber, slow, stacks, attack, reach. "Saber" = the sword's energy slash/beam milestone (only gates Astral; tier 2/3 perks are always active). Replaces the older PERK_VALUES_MASTER order. | 10-28 |
| Energy slash / beam | Energy slash and beam (2026-10-28): % of the tier base (10/11/12). Slash 60/70/80% = 6 / 7.7 / 9.6. Beam 75/85/95% = 7.5 / 9.35 / 11.4. | 10-28 |
| GitHub | Push and upload the jar to the private repo after every patch with no need to ask. Public publishing still needs Justin's OK. | 10-28 |

## 3. Open questions for Justin
1. **Astral and Singularity DoT**: name and visual. He floated "Cosmic Radiation" for Astral. They use Bleed for now.
2. **Infinity DoT**: name and visual (Bleed for now).
3. **Forward push** on Cybernetic and Cyber Psycho jumps: the amounts (Cyber Psycho > Cybernetic). Not implemented yet.
4. **Rest of his testing list**: the raw list (10-28 8:25 AM ET) has only items 1–2, and all of it is already covered by Batches A–D (HUD, warp, ability HUD and keybinds, mob slow). Nothing else is left to copy. Waiting on Justin for any more items.
5. **"Saber" perk**: implemented as the energy slash/beam milestone (no saber perk existed in code). Confirm, or name the perk he meant.
6. From the brief and the review: are gold and red-orange accents banned (treated as banned)? Should warp stay on Ctrl (same as Sprint)? Should X-ray show containers on BreadCraft, with a server kill switch? Should auto-repair reach into backpacks and Accessories? Allow solo-chestplate infusion? Overall power level (slash + melee double hit)? Flight sounds for Cybernetic, Singularity, Infinity and Astral.

## 4. Review findings to fix (2026-10-28 review)
**Build and assets**
1. `sounds.json` has only 55 entries. 75 of the 117 sound files have no entry, though all of them are registered in code. Add the missing entries; don't delete the files.
2. `gen_data.py:92` calls `gen_r8.py`, which is missing from the repo.
3. 400 unused old wing frames (about 1.6 MB; Cybernetic, Cyber Psycho, Infinity) still load at startup. `particle/star.png` is unused. The wings-layer glow frames are probably unused.
4. Punchy and roguelike are listed as hard dependencies.
5. The local clone is missing 4 tags, and the `out/` jar is stale.

**Values and text**
6. The guide says 4 crystal slots instead of 8/16/32.
7. Old slow values are still in `PerkValues` and `InfiniumConfig`.
8. Seven guide numbers are hard-coded. The Quantum Tunnel and X-Ray numbers are defined but not shown.

**Testing**
9. There are no tests. Suggestion: a headless boot test (start a dedicated or gametest server in CI and fail on mixin, registry or missing-sound errors).

**Unfinished since Oct 11 (modpack)**
10. Player config bundle.
11. Spatial GUI tuning for 1080p and other FOVs.
12. JEI cursor fix and the Accessories+Artifacts backpack slot: both built, neither installed.
13. Inventory gets bigger but JEI doesn't.
14. ZGC question.

**Compatibility**
15. The LowOnFire resource pack may hide Cyberburn visuals.

**Recheck in game (from Oct 26)**
16. Solo-chestplate infusion bug.
17. Slash + melee double hit.
18. Particle-map growth.

## 5. In-game test checklist (`batch1-b1bc2b6`)
- [ ] Crystal screen: Astral/Singularity 5 cards (3+2), Infinity 9 (5+4), in the new order; saber card icon is a placeholder (attack icon).
- [ ] Astral: no energy slash until the saber milestone (5 crystals); Cybernetic and tier 2/3 slash from the start.
- [ ] Slash does 6 / 7.7 / 9.6 and beam 7.5 / 9.35 / 11.4 by tier.
- [ ] Sword hits 10/11/12 by tier; tooltip shows 10 Attack Damage plus the tier line; slash/beam numbers match.
- [ ] Slash/beam DoT lasts 3/4/5 s by set; plain melee applies none.
- [ ] Cybernetic and Cyber Psycho show "Cyberburn" (pink embers, no fire); other sets show Bleed.
- [ ] DoT ticks don't block the next melee hit (no i-frames); stacking with the slash feels fair.
- [ ] Hoe: Sneak + Right-Click air toggles Standard/3x3, the per-set sound plays, and 3x3 tills correctly.
- [ ] Warp 8/12/16 blocks, cooldown 8/6/4 s, forward launch; the cue and screen effect show.
- [ ] Jumps: tap/hold heights; air jumps only with elytra off; Infinity's warp refunds 3 air jumps.
- [ ] HUD: settings button, energy bar and x/80 visibility rules; no raw lang keys; Open Controls works.
- [ ] Mob slow 2/4/6 s, cd 12/10/8 s; X-Ray Pulse radius and cooldown.
- [ ] Wings: Cybernetic, Cyber Psycho and Infinity look right, thrust glow and trail work, no stray dots, limited particles.
- [ ] Guide v2: lore lines, wording, search, tabs.
- [ ] Sounds actually play (check the log for "Unable to play empty soundEvent").
- [ ] Multiplayer: everything also works for other players on the server.

## 6. Changelog (GitHub pre-releases, ET)
- `batch1-b1bc2b6` 10-28 4:22 PM: crystal unlock order (Astral/Singularity 5 perks incl. saber, Cyber lines 4, Infinity 9 alternating); supersedes `batch1-aad53c6` (4:20 PM, 4-perk order without saber or Infinity).
- `batch1-9d9fece` 10-28 4:15 PM: energy slash 60/70/80% (6 / 7.7 / 9.6), beam 75/85/95% (7.5 / 9.35 / 11.4) of the tier base.
- `batch1-8c89f58` 10-28 4:11 PM: sword damage by 3 tiers, 10/11/12 (Tier 1 no armor/Astral/Cybernetic, Tier 2 Singularity/Cyber Psycho, Tier 3 Infinity); slash/beam scale with it.
- `batch1-89b8713` 10-28 3:51 PM: Burn DoT renamed to Cyberburn.
- `batch1-bf0e9a9` 10-28 3:46 PM: DoT duration by set, 3/4/5 s.
- `batch1-a336ea2` 10-28 3:45 PM: DotDamage helper, so DoT ticks never give i-frames.
- `batch1-1da708a` 10-28 2:40 PM: melee has no bleed; slash/beam DoT is Burn for Cybernetic and Cyber Psycho, Bleed otherwise.
- `batch1-39ddd4a` 10-28 2:36 PM: hoe Standard/3x3 modes, set-based mode sound, guide wording.
- `batch1-ef7eab0` 10-28 1:58 PM: sky v7 meteors and ripples in the vanilla sky pass, AUTO/SKY/WORLD mode.
- `batch1-4c1fb8e` 10-28 12:01 PM: first batch1 build (untested).
