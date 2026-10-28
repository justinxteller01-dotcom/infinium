# Project Map - Infinium Mod

## Environment & Build Info
- **Minecraft Version:** Java 26.2
- **Loader:** Fabric Loader 0.19.5
- **Toolchain:** Custom `build.ps1` (Python asset generators -> `javac --release 25` -> `jar.exe`). No Gradle.
- **Root Directory:** `F:\GrokBot\work\infinium_pass2`
- **Output Artifact:** `out/infinium-1.0.0+26.2.jar`
- **Deployed Target:** `C:\Users\justi\AppData\Roaming\.minecraft\mods\infinium-1.0.0+26.2.jar`

---

## Package Overview (`dev.justin.infinium`)

### 1. Core Logic & Game Rules
- `Infinium.java`: Mod entrypoint, registry holders for items, blocks, effects, sounds, components, armor set checks (`fullSet`, `styledSet`, `infinitySet`).
- `InfiniumStyle.java`: Style enum (`ASTRAL`, `CYBERNETIC`, `SINGULARITY`, `CYBERPSYCHO`).
- `SetLook.java`: Active equipped set look (`ASTRAL`, `CYBERNETIC`, `SINGULARITY`, `CYBERPSYCHO`, `INFINITY`) and `lookWith(...)` simulation helper.
- `SetBonuses.java`: Full-set passive abilities, slow aura, and equip sound dispatcher (`equipSound` / `clientEquipSound`).
- `Perk.java`, `Perks.java`, `PerkValues.java`: Armor perk definitions, bitmask toggles, scaling values per style family.
- `CrystalCharge.java`: Spacetime Crystal socketing, charge thresholds, perk unlocking milestones, visual progress calculation.
- `dungeon/DungeonHudPayload.java`: Network packet syncing dungeon presence and chest reset time remaining to client.
- `GlowColor.java`: Dye recoloring component (`infinium:glow_color`).
- `SingularityStacks.java`: Stacking capacity overrides (up to 96/24/2).
- `ModSounds.java`: Sound event registration for armor/tool pickup, place, equip, full set equip, crystal UI events, and themed sword audio suite (`sword(LivingEntity, kind, fallback)`).
- `Info.java`: Tooltip text formatting and perk descriptions.
- `InfiniumArmorTier.java`: Server and client authoritative helper for resolving full Infinium armor set tier (Tier 1 = Astral/Cybernetic, Tier 2 = Singularity/Cyberpsycho, Tier 3 = Infinity, 0 = incomplete/none). Live equipment resolution enables tier >= 1 immediately upon equipping.
- `SlashVisuals.java` (branch `sword-anim`): ALL per-armor-set slash/stab-beam visual tunables (`Profile`: accent tint, core lift, width, alpha, glow/tail, halo layers, flicker, glitch, gravity warp, particle chance/types/speed) for CYBERNETIC < CYBERPSYCHO, ASTRAL < SINGULARITY < INFINITY. Resolved client-side via `resolve(owner, tier)` from `SetLook.of(owner)`, falling back to synced TIER. Used by `EnergySlashRenderer.renderProfiled` and `EnergySlashEntity.spawnSparks`. Kill switch: `-Dinfinium.slashVisuals=false` (legacy tier look).
- `SwordSlashHandler.java`: Server-side authoritative handler for energy slashes (tier & energy-mode gating, rate limits, 1.25x stab gate, server player rotation, color clamping, roll NaN/infinite rejection & clamp, tier-scaled launch audio, per-player disconnect cleanup, no attack-cooldown reset). Zero client imports.
- `SwordSlashPayload.java`: C2S network packet for energy slash attacks.
- `SwordTheme.java`: Server and client sword sound theme resolution helper (allowed choices, resolving per-set ordinals, server-side per-player choice storage).
- `SwordThemePayload.java`: C2S network packet syncing player's sword sound theme preferences.
- `EnergySlashEntity.java`: 15-node piercing segmented energy wave entity with block surface scorch burns, dye color sync, synched TIER data accessor, and tiered damage (T1: 30%/35%, T2: 40%/55%, T3: 50%/75% of 11.0 base damage).

### 2. Items & Blocks
- `InfiniumSwordItem.java`: Infinium Sword with bleeding effect, sneak + right-click energy mode toggle (stored in `SWORD_ENERGY_MODE` data component, defaults to energy mode), action bar notifications, and hover tooltip.
- `InfiniumPickaxeItem.java`, `InfiniumAxeItem.java`, `InfiniumShovelItem.java`, `InfiniumHoeItem.java`: Infinium multi-mode tools.
- `InfiniumFarmlandBlock.java`: Permanent lit, saturated farmland created by the hoe.
- `NetheriteRocketItem.java`: Netherite firework rocket item.
- `DungeonFinder.java`: Compass tracking nearby roguelike dungeons.
- `InfiniumGuide.java`, `InfiniumBook.java`: Informational crystal guide item.

### 3. Client Package (`dev.justin.infinium.client`)
- `InfiniumClient.java`: Client initialization, event listeners, packet receivers.
- `InfiniumClientConfig.java`: Client settings JSON manager (`infinium-client.json`).
- `InfiniumSettingsScreen.java`: Visual & Sound Settings GUI (3-way particle toggles, world effects, sounds, controls).
- `InfiniumPerksScreen.java`: Perk toggles per armor set, with prominent button for Visual Settings.
- `CrystalScreen.java`: Spacetime Crystal Matrix upgrade UI (progress bar, 2-row milestone cards, infusion socket).
- `InventorySettingsButton.java`: Inventory gear settings gear button and player portrait cosmic energy progress bar.
- `DungeonHudClient.java`: On-screen minimalistic HUD showing remaining dungeon chest reset time when in dungeon chunks.
- `InfiniumUiSounds.java`: Sound management for inventory pickup/place/drop and full set equip sound deduplication.
- `InventoryHudTheme.java`, `UiInfection.java`: Progressive cosmic HUD ambient effects, motes, hairline cracks, and rim energy. Also `menuBackdrop` (title/out-of-world menu void via `mixin/TitleScreenFxMixin`) and `guideBackdrop`; meteor v5: max 4 small purple/pink shards drifting L->R in top/bottom bands only (centre clear), 60s crossings, seamless 120s loop (all motion periodic), 10px faint trail, fade in/out, static stars with ±8% twinkle, no camera drift. Preview mock: /workspace/meteor_preview/mock.py.
- `ArmorParticles.java`: First-person and third-person ambient particle emitters for worn gear, held tools, wing deploy/retract bursts, and in-flight wingtip trail ribbons.
- `WorldSkyFx.java`: Ambient sky effects: daytime spacetime ripples, nighttime shooting stars, and underwater shimmer. Meteor v5: MAX 5 near / 3 distant, shower spawn 0.03 & 0.015/tick; look constants in `ShootingStarLook.java` (head 0.7 / trail 0.45 blocks, speed 1.1, drop 0.08).
- `WingFold.java`: Elytra wings transition timing, ground angle lerp, and absorb retraction.
- `InfiniumWingModels.java`: Custom 3D wing geometries for all 5 styles (Astral crescent, Cybernetic vector thruster, Singularity void scythe, CyberPsycho serrated razor, Infinity divine archangel). Multi-layer piece hierarchy (up to 9 pieces) dynamically toggled per unlocked perk milestone.
- `InfiniumWingsRenderer.java`: Dedicated wing renderer completely replacing vanilla's flat box ElytraModel; handles sleek non-clipping resting V-pose, 100% walking clearance, dynamic per-crystal opacity scaling (up to 90% at max Infinity), unlocked perk milestone pieces, and synchronized armor-matching glow overlays.
- `PreviewColorProperty.java`: Model select property for GUI item rendering.
- `SwordSlashClientHandler.java`: (all sends go through `emitSlash`, which also records the sword animation move) Client-side attack combo controller (Left Cut -> Right Cut -> Stab), timing gates matching server rate limits, armor tier >= 1 & sword energy-mode gating, pre-beam clip resolution, dye/style color sync, tier-scaled audio volume and pitch, early stab animation/beam synchronization, direct integration with Punchy animation playback, and Stage 2 crit/stab spin audio (playing punchy:animationssounds/firstspinv2 by registry name on idle return motion with SoundInstance handle stopped on next attack click).
- `EnergySlashRenderer.java`: Render geometry using RenderTypes.lightning() for lightsaber arc waves and straight cylindrical laser lance beams, with tier-scaled thickness and core/aura alpha.
- `EnergySlashRenderState.java`: Client render state for energy slash projectiles, including active segments, roll/yaw/pitch, color, isStab flag, age ticks, and tier.

### 3b. Infinium Sword Animations (`dev.justin.infinium.client.anim`, branch `sword-anim`)
Original, client-only sword animations for the Infinium Sword (main hand) only. Kill switch: JVM arg `-Dinfinium.swordAnim=false`.
- `SwordAnimTuning.java`: ALL tunable values (swing timing/arc/follow-through, idle breath/sway, equip flourish, third-person arm angles) + `ENABLED`, `OVERRIDE_PUNCHY`, `overridesPunchy()`.
- `SwordAnimCurves.java`: Pure easing math (windup, strike with damped overshoot, equip easeOutBack, breath/sway).
- `SwordMove.java`: Enum LEFT_CUT (roll -35), RIGHT_CUT (+35), STAB (isStab), OVERHEAD_CLEAVE (90), FLAT_SWEEP (0, crouch), PLAIN (no slash emitted). `fromPayload(roll, isStab)`.
- `SwordAnimState.java`: Per-entity weak-map clocks. Move + clock are set ONLY by `recordEmittedSlash`, called from `SwordSlashClientHandler.emitSlash` (the single choke point that sends every `SwordSlashPayload`, used by both the Punchy and no-Punchy paths). `onSwing` only starts PLAIN swings when nothing was emitted this click and no slash animation is still playing. No separate combo counter.
- `InfiniumSwordAnimator.java`: Per-move first-person PoseStack transform and third-person arm rotations. Arc moves sweep the arm in the plane of the emitted crescent (camera ZP(payload roll), same convention as `EnergySlashEntity`/`EnergySlashRenderer`); STAB is a forward thrust; PLAIN is a small tap.
- `SwordPoseState.java`: Interface stamped onto `HumanoidRenderState`.
- Mixins (`client.mixin`, in `infinium.client.mixins.json` via `gen_data.py`):
  - `SwordAnimItemInHandMixin` (priority 500): HEAD of `ItemInHandRenderer.submitHandsWithItems`, cancellable. With an Infinium sword in main hand it draws main hand with our transform, off hand via vanilla `submitArmWithItem`, then cancels (so Punchy's first-person renderer, which also hooks HEAD at default priority, does not run that frame).
  - `SwordAnimSwingMixin` (priority 500): HEAD of `LivingEntity.swing(hand, boolean)` on client, records our swing clock.
  - `SwordAnimRenderStateMixin` / `SwordAnimAvatarRendererMixin`: copy clocks into the player render state in `AvatarRenderer.extractRenderState` TAIL.
  - `SwordAnimHumanoidModelMixin` (priority 1500): TAIL of `HumanoidModel.setupAnim`, overwrites the sword arm's rotations.
- Punchy: `PunchyAttackStateMachineMixin.infinium$skipPunchyForInfiniumSword` cancels Punchy `playAttack` for main-hand Infinium sword when `overridesPunchy()`; `MinecraftAttackMixin` then fires energy slashes on click (the no-Punchy path).

### 4. Client Guide (`dev.justin.infinium.client.guide`)
- `InfiniumGuideScreen.java`: 2-page Informational Crystal guidebook UI.
- `GuideCards.java`: Right-page card-stack data per guide page (modelled on the Crystal Matrix page's milestone cards). Rendered by `InfiniumGuideScreen#drawCardStack` (area x150 w124 y10-166, auto-shrinks recipe card 42->26, text 0.57->0.45, gap 3->1 to fit). Purple/pink/black only. Matrix page (`drawMatrixShowcase`) is the original; Contents/Intro/flow/Additional Info pages have no right stack.
- `FlatGuideScreen.java`: 2D fallback guide screen.

### 5. Mixins (`dev.justin.infinium.mixin` & `client.mixin`)
- `LivingEntityMixin.java`: Intercepts `onEquipItem` for sound triggers (server & client).
- `EquipmentLayerRendererMixin.java`: Cancels vanilla WINGS layers for Infinium/Infinity assets and renders animated glow overlays for armor.
- `WingsLayerMixin.java`: Intercepts `submit()` at HEAD for Infinium chestplates, bypassing vanilla ElytraModel and delegating directly to `InfiniumWingsRenderer`.
- `ElytraModelMixin.java`: Ensures vanilla/non-Infinium elytras retain valid initial scales and poses.
- `ContainerScreenMixin.java`: Renders progressive UI infection inside inventory screens.
- `EffectsInInventoryMixin.java`: Positions status effect badges level with container edge.
- `SlotClickSoundMixin.java`, `CreativeSlotClickSoundMixin.java`: Triggers pickup/place UI sounds on container slots.
- `SoundManagerMuteMixin.java`: Silences sounds muted in client settings or suppressed during full set equip.
- `MinecraftAttackMixin.java`: Intercepts attack start; records queued click target for block detection at HEAD before missTime/handsBusy early returns (no stale flags); delegates to vanilla click handler when Punchy is absent, or defers to Punchy's animation playback when Punchy is present. Registered in `infinium.client.mixins.json` via `gen_data.py`. Blocks are left untouched.

### 6. Compatibility & QA (`dev.justin.infinium.compat` & `dev.justin.infinium.qa`)
- `punchy/PunchyBridge.java`: Soft-dependency bridge to Punchy animation mod (combo clip resolution; critical clip resolution reading "critical_hit" from the attack entry with player's preferred option and fallback chain critical_hit -> "attack_critical"; stack attack entry lookup via `findAttackEntry`; flourish twirl trigger neutralized to prevent triggering Punchy's 19s `sword_inspect_rework`; Stage 3 verified Punchy cannot load clips from foreign namespaces without modification).
- `punchy/PunchyAttackStateMachineMixin.java`: Hooks Punchy's `playAttack` and `resolveCustomAttackClip`. Synchronizes Punchy's visual attacks with Infinium energy slashes, checks client rate-limit throttle before returning true (preventing crit animation without beam), preserves unconsumed crit state on block clicks while leaving mining thrust intact using click-time `isQueuedAttackTargetBlock` with zero play-time `mc.hitResult` (ITEM 1e), fires crit cleave only when Punchy's crit clip actually resolves and plays via preferred entry option (falling back safely to combo beam, never both), looks up current stack's "attack" entry at crit time with fallback to LAST_ENTRY then null (ITEM 1d), keeps Punchy's `comboIndex` meaning as the next stage, and registered in `infinium.compat.mixins.json` via `gen_data.py`.
- `CompatMixinPlugin.java`: Soft mixin configuration plugin gating compat mixins (tb, spatial, jei, punchy) on active mod presence.
- `SpatialMargin.java`: Offscreen buffer scaling and scissor support for Spatial GUI.
- `GuiRendererOverscanMixin.java`: Wraps OpenGL `enableScissor` for Spatial GUI offscreen targets.
- `InfiniumJeiPlugin.java`, `InfiniumJeiDyeHider.java`: JEI integration.
- `qa/QaTelemetryBridge.java`: Native 26.2 Fabric QA & telemetry bridge syncing player info, block queries, commands, and screenshots with the local MCP bridge.

### 7. Asset Generators (Python Root)
- `gen_textures.py`, `gen_cracks.py`, `gen_styles.py`, `gen_tool_styles.py`: Full-color animated texture generators.
- `gen_crystal_sounds.py`, `gen_flight_sounds.py`, `gen_sword_sounds.py`: Procedural audio suite synthesizers for crystals, flight thrusters, and default/themed sword sound effects.
- `gen_dye.py`: Grayscale and tinted dye variant texture generators.
- `white_fix.py`, `hue_fix.py`, `r8_post.py`: Texture safety passes (hue guards, chamfers, corner cuts).
- `gen_book.py`: Guidebook text, recipe, and page structure generator (`pages.json`).
- `build.ps1`: Orchestrates all generator scripts, runs `javac`, and packages the JAR.

### 8. Productivity & QA Tools
- `tools/voice_bridge.py`: Background microphone bridge. Press hotkey (`Numpad -`) in Minecraft to record and dictate; sends directly to Antigravity chat with auto-focus restoration so you never need to click or switch windows manually.
- `tools/start_voice_bridge.bat`: One-click runner for the voice bridge script.
- `tools/telemetry_bridge.js`: Native 26.2 QA & Telemetry MCP Server exposing `http://localhost:8080/mcp` for live in-game testing.
- `tools/start_telemetry_bridge.bat`: One-click background launcher for the telemetry bridge.
- `tools/sync_sqlite.py`: Syncs runtime dungeon reset timers, player stats, and refill logs into `breadcraft.sqlite`.
- `breadcraft.sqlite`: Local SQLite database containing weekly chest resets, refills, and player statistics for the SQLite MCP server.

### 9. Batch 1 additions (branch `batch1`, 2026-10-28)
- `UiUnlocks` (attachment `infinium:ui_unlocks`, persistent, copyOnDeath, synced to owner): bit 1 = any Infinium gear seen (settings gear button stays), bit 2 = full set seen (x/80 counter stays). Energy bar still needs a full set worn.
- `SpacetimeWarp` = **Quantum Tunnel**: tunables `DISTANCE {8,12,16}`, `COOLDOWN_SEC {8,6,4}`, `LAUNCH_SPEED`, `CUE_PITCH` (tier 1/2/3 = Astral+Cybernetic / Singularity+CP / Infinity). Placeholder cue = vanilla enderman teleport. Client screen fx: `client/ability/WarpScreenFx`.
- `PerkValues.SLOW_DURATION_SEC {2,4,6}` / `SLOW_COOLDOWN_SEC {12,10,8}` (ModMenu sliders removed; config fields legacy).
- `BindKeyHandler.openControls` always opens vanilla KeyBindsScreen; settings screen has an "Open Controls" button.
- OptionsSubScreen rebuilds (Perks / Settings / ModMenu config): never call vanilla `rebuildWidgets()`/`init()` again; its `layout` is final, so re-init stacks a second OptionsList + title/footer and the old list stays drawn as a frozen layer (darker each time). All three override `rebuildWidgets()` to `list.replaceEntries(List.of()); addOptions();` keeping scroll.
- Wings: `InfiniumWingsRenderer.renderR3` = Cybernetic round-3 option-4 elytra on vanilla `ElytraModel` geometry, frames `textures/entity/infinium_wings/r3/cybernetic/{base,glow,glow_gray}/N.png` (50 ms/frame). Cyber Psycho (batch1, Justin's final): round-7 variant A **armor_hue** = angular plates on the option-4 base (plates baked into the texture, fixed geometry), plum base #2e102e, seams/edge armor crack purple #a21aa3, frames `r7/cyberpsycho/{base,glow,glow_gray}/N.png` (80 fr x `CP_FRAME_MS` 100 ms via `GlowAnim.frame(.., speedMul)` so wing frame k = armor glow frame k); CP inner wing pair REMOVED (single pass). CP thrust border glow (`border.png`, dye tint, default pink `CP_THRUST_DEFAULT`). `WingThrustTrail` = CP afterglow ribbon (LevelRenderEvents.COLLECT_SUBMITS). Infinity rig: fixed 0.80 scale, fixed piece set. Opacity = 0.40 + 0.50 * overallProgress (linear, unupgraded tier 1 -> max Infinity). Wings never gain geometry with upgrades.
- Infinity wings (batch1): round-5 **Event Horizon + A1 echo_outline**, drawn by `renderR3` (fam 4) on vanilla `ElytraModel` geometry. Frames `textures/entity/infinium_wings/r5/infinity/{base,glow,glow_gray,echo}/N.png` (100, 50 ms) + `border.png`. Echo outline = separate emissive pass (`INF_ECHO`, scale 1.08/1.04/1.08, +0.02 z) shown only when fold > 0.9. Fixed `INF_SCALE` 0.80 x wingScaleMultiplier, same opacity ramp. Thrust: dye border glow (default purple `INF_THRUST_DEFAULT` 0xBD45FF) + `WingThrustTrail` ribbon. `renderR3` draws nothing at fold < 0.05 (stray-dot fix). Old Infinity rig code below the fam-4 branch is now unreachable.
- Sounds: `cyberpsycho_thrust_{start,loop,stop}` (placeholders -> vanilla beacon activate/ambient/deactivate via sounds.json; played from `InfinityFlightClient`).
- Generators: `gen_lang_extra.py` (merges batch lang keys + CP thrust sounds into en_us.json / sounds.json), `gen_wings_r3.py` (r3 Cybernetic elytra frames from `ref/wings_r3`), `gen_wings_r5_infinity.py` (Infinity frames from `ref/wings_r5/infinity`, exported by `/workspace/elytra_designs/round5_infinity/export_mod.py`), `gen_wings_r7_cyberpsycho.py` (Cyber Psycho frames from `ref/wings_r7/cyberpsycho/armor_hue_anim(_glow).png`, exported by `/workspace/elytra_designs/round7_cyberpsycho/export_mod.py`; palette-clean so `gen_palette_fix.py` leaves it unchanged; `gen_wings_r3.py` now emits Cybernetic only), `palette_guard.py` (shared colour rule helper), `gen_palette_fix.py` (LAST build step: forces every asset PNG to purple/pink/magenta/black; skips `_n`/`_s` PBR maps).
- **Colour rule**: no cyan/teal, gold, red-orange, green or blue anywhere. Only purple, pink, magenta, black (+ shades). Player dye palette (`GlowColor.RGB`) is exempt.
- **Colour-rule EXCEPTION: X-Ray Pulse** (`ability/XrayPulse.color()`, `client/ability/XrayPulseClient.HOSTILE_COLOR`): outlines + sparkle particles use the real colour of the target. Coal 0x242424, iron 0xD8B48C, copper 0xE0702A, gold/nether gold 0xFFD700, redstone 0xE00000, lapis 0x1A3CC8, diamond 0x4DE8FF, emerald 0x00D040, ancient debris 0x6B3A1E, nether quartz 0xFFFFFF, budding amethyst 0x9A5CFF (now pulsed), modded c:ores 0xA0A0A0, all containers (chest/barrel/ender/shulker/Lootr) 0xFF00FF magenta, mobs/hostiles 0xFF0000. Players stay pink 0xFF40BF; scan-ring stays purple 0xB07CFF. `gen_palette_fix.py` SKIP list excludes `xray_pulse*` textures (gui/ability, gui/matrix).
- Known gaps: `gen_r8.py` missing from repo (gen_data.py can't fully regenerate); `gen_book.py` not in build.ps1 (guide pages.json edited directly).
- **Text style (player-facing ability text)**: plain language a casual player gets instantly; say what it does, no percentage dumps; keep only simple numbers that matter (`+1 jump height`, `8 blocks`); short, one line. Applied so far to Astral only (`Info.pieceLine` case 0, `Info.astralSetLine`, `PerkValues.shortPerkDesc` Astral branch, `gen_book.py` Astral Armor page -> `guide/pages.json`). Other sets still use the shared wording.
- **Tier 2/3 'upgrade' wording**: Singularity/Cyber Psycho/Infinity armor already has every lower-tier perk active; crystals UPGRADE them. `Info.tierNote(fam)` (full line) / `Info.tierNoteShort(fam)` (matrix sub-header) return null for tier 1. Used in: armor tooltip under the 'Full set' header (`Info`), matrix sub-header + extra card tooltip line (`CrystalScreen`), perk settings under the 'full set (matrix)' header (`InfiniumPerksScreen`, split into 2 lines). `CrystalScreen` card status/button/tooltip say UPGRADE/UPGRADED for fam >= 2 (tier 1 keeps UNLOCK). Sound subtitle split: `subtitles.infinium.crystal_perk` (Astral/Cybernetic, 'unlocked') vs `crystal_perk_upgrade` (singularity/cyberpsycho/infinity_perk, 'upgraded'); also in `gen_book.py`.
- **Crystal insert (batch1 redesign)**: `CrystalScreen` has ONE 'Insert Crystal (N left)' button (disabled as 'No crystals' / 'Fully powered', 150 ms anti-spam); each click sends `CrystalCharge.Insert(target = filled + 1)`; server (`CrystalCharge.insert`) ignores stale targets, takes exactly 1 crystal, raises all set pieces by 1. Perks unlock/upgrade when the total crosses `crystalsRequired` (marks unchanged, saved `crystal_filled` compatible). Perk cards are info-only (status UNLOCKED/UPGRADED, NEXT, NEED n; unlocked card click still toggles lights). Sounds: per click `crystal.<set>.insert` (pitch climbs); on crossing a mark `perk_unlock_<set>`; on first max (gated by `<set>_max_charge` advancement, so once ever) `set_maxed_<set>` + `crystal.<set>.full`. `perk_unlock_*`/`set_maxed_*` are vanilla PLACEHOLDERS written by `gen_lang_extra.py` (`PERK_PH`/`MAX_PH`); lang keys `gui.infinium.crystal.*`.

- Full Bright was removed (batch1). It was client-only (static toggle, B key, HUD pill, inventory/perks-screen buttons, lightmap mixin, unused C2S `helmet_vision` packet). No perk mask bit or saved data existed, so old saves need nothing; a stale `key.infinium.toggle_fullbright` line in options.txt is ignored by vanilla.
- **Jumping (batch1, Justin 2026-10-28)**: `client/VariableJump` = tap/hold height for Astral/Singularity/Infinity boots (takeoff cut to vanilla 0.42 = ~1.25 blk; holding keeps vy >= the full-power jump's vy, so apex caps at full height; release stops the boost). Max apex: Astral ~2.25 (+0.16310), Singularity ~3.25 (+0.29539), Infinity ~4.25 (+0.41076) from `PerkValues.JUMP_*` via `Infinium.bootJumpBoost` (no crystal dependence; boots jump M5 toggle). `VariableJump.airPush` = in-air forward push along move input (forward/strafe, not backward) while airborne from a jump, boots family: `PerkValues.AIR_PUSH_ASTRAL/SINGULARITY/INFINITY` 0.0015/0.0030/0.0050 b/t^2 (~+0.017/+0.033/+0.056 b/t steady); none while gliding/swimming/flying/riding/climbing. Air jumps (`AirJumps`, `client/AirJumpClient`): `AirJumps.canAirJump` requires Elytra toggled OFF (`elytraOn` = chest has GLIDER), checked on both sides; with it on, vanilla elytra open. `VariableJump.startAir` gives air jumps the same tap/hold scaling using the worn boots' bonus (only Infinity boots have one; Cybernetic/Cyber Psycho air jumps stay plain 0.42).
