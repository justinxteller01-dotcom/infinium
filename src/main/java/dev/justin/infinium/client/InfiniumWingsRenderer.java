package dev.justin.infinium.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.justin.infinium.CrystalCharge;
import dev.justin.infinium.GlowColor;
import dev.justin.infinium.Infinium;
import dev.justin.infinium.InfiniumStyle;
import dev.justin.infinium.SetLook;
import dev.justin.infinium.client.compat.IrisGlowCompat;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Dedicated Wing Renderer for Infinium & Infinity Chestplates:
 *  - Bypasses vanilla's flat box ElytraModel completely.
 *  - Full Set / Tier Detection: Wings change shape and design only when achieving the next full set tier.
 *    If only the chestplate is equipped without a full set, treats the elytra like the full set of that chestplate.
 *  - Linear Progression from Start to Finish (Base Astral/Cybernetic -> Max Infinity):
 *      * Opacity: Scales linearly from 0.40F (unupgraded Tier 1) up to 0.90F (max Infinity).
 *      * Pieces: Starts with little base piece (piece_0); each perk unlock adds 1 additional piece per side.
 *        Fully upgraded Astral/Cybernetic adds 4 additional pieces per side (8 total across both sides).
 *      * Texture Animation: Speed & density scale linearly (0.55x at 0 upgrades up to 1.75x at max Infinity).
 *  - 128x64 Texture Mappings:
 *      * Cybernetic & CyberPsycho: Symmetrically mirrored left-to-right.
 *      * Astral & Singularity: Asymmetric with independent random cosmic drift.
 *      * Infinity: Divine hybrid synthesis.
 *  - Sleek Non-Clipping Resting V-Stance: Permanent 3 px spinal clearance, terminates at waist (0% leg collision).
 *  - Clean Absorption: Retracts directly into chestplate matrix when toggled off (0 draw calls when folded).
 */
public final class InfiniumWingsRenderer {

    private static final String[] STYLES = {"astral", "cybernetic", "singularity", "cyberpsycho", "infinity"};
    public static final int[] GLOW_FRAMES = {40, 40, 100, 80, InfinityLook.FRAMES};
    public static final int[] GLOW_FRAME_MS = {300, 300, 100, 100, InfinityLook.FRAME_MS};

    private static final Identifier[] BASE_TEX = new Identifier[5];
    private static final Identifier[] ACCENT_GRAY_TEX = new Identifier[5];
    private static final Identifier[][] GLOW_TEX = new Identifier[5][];
    private static final Identifier[][] GLOW_GRAY_TEX = new Identifier[5][];

    static {
        for (int i = 0; i < 5; i++) {
            String s = STYLES[i];
            BASE_TEX[i] = Infinium.id("textures/entity/infinium_wings/base_" + s + ".png");
            ACCENT_GRAY_TEX[i] = Infinium.id("textures/entity/infinium_wings/accent_gray_" + s + ".png");

            int fCount = GLOW_FRAMES[i];
            GLOW_TEX[i] = new Identifier[fCount];
            GLOW_GRAY_TEX[i] = new Identifier[fCount];
            for (int f = 0; f < fCount; f++) {
                GLOW_TEX[i][f] = Infinium.id("textures/entity/infinium_wings/glow/" + s + "/" + f + ".png");
                GLOW_GRAY_TEX[i][f] = Infinium.id("textures/entity/infinium_wings/glow_gray/" + s + "/" + f + ".png");
            }
        }
    }

    public record ProgressionState(int family, int perksUnlocked, float overallProgress) {}

    /**
     * Resolves active progression state based on equipped armor set:
     * - If full set equipped: uses set tier and minimum filled crystals across all pieces.
     *   (Singularity piece counts as max Astral piece toward Astral full set).
     * - If no full set: fallback to worn chestplate.
     */
    public static ProgressionState resolveProgression(HumanoidRenderState state) {
        ItemStack c = state.chestEquipment;
        if (c == null || !Infinium.isChestplate(c)) return new ProgressionState(0, 0, 0.0F);

        ItemStack h = state.headEquipment, l = state.legsEquipment, f = state.feetEquipment;
        SetLook fullLook = null;

        if (h != null && l != null && f != null
                && Infinium.isHelmet(h) && Infinium.isLeggings(l) && Infinium.isBoots(f)) {
            if (Infinium.isInfinity(h) && Infinium.isInfinity(c) && Infinium.isInfinity(l) && Infinium.isInfinity(f)) {
                fullLook = SetLook.INFINITY;
            } else {
                for (InfiniumStyle st : new InfiniumStyle[]{InfiniumStyle.SINGULARITY, InfiniumStyle.CYBERPSYCHO, InfiniumStyle.ASTRAL, InfiniumStyle.CYBERNETIC}) {
                    if (Infinium.countsAs(h, st) && Infinium.countsAs(c, st) && Infinium.countsAs(l, st) && Infinium.countsAs(f, st)) {
                        fullLook = SetLook.values()[st.ordinal()];
                        break;
                    }
                }
            }
        }

        int fam;
        int filled;

        if (fullLook != null) {
            fam = fullLook.ordinal();
            filled = Math.min(
                Math.min(CrystalCharge.filledForFamily(h, fam), CrystalCharge.filledForFamily(c, fam)),
                Math.min(CrystalCharge.filledForFamily(l, fam), CrystalCharge.filledForFamily(f, fam))
            );
        } else {
            // Fallback: player only has chestplate on -> treat like full set of that chestplate
            fam = Infinium.isInfinity(c) ? 4 : (InfiniumStyle.get(c) != null ? InfiniumStyle.get(c).ordinal() : 0);
            filled = CrystalCharge.filled(c);
        }

        // Continuous cumulative perk count across all tiers (nothing is reset or lost):
        // Tier 1 (Astral & Cybernetic): 8 slots, 1 perk every 2 crystals -> cumulative perks 0..4
        // Tier 2 (Singularity & CyberPsycho): 16 slots, 1 perk every 4 crystals -> cumulative perks 4..8 (inherits 4 Tier 1 perks)
        // Tier 3 (Infinity): 32 slots, 1 perk every 4 crystals -> cumulative perks 8..16 (inherits 8 Tier 1+2 perks)
        int cumulativePerks;
        float overall;

        if (fam == 4) {
            int perksInTier = Math.min(8, filled / 4);
            cumulativePerks = 8 + perksInTier;
            float tierFrac = perksInTier / 8.0F;
            overall = (2.0F / 3.0F) + (1.0F / 3.0F) * tierFrac;
        } else if (fam >= 2) {
            int perksInTier = Math.min(4, filled / 4);
            cumulativePerks = 4 + perksInTier;
            float tierFrac = perksInTier / 4.0F;
            overall = (1.0F / 3.0F) + (1.0F / 3.0F) * tierFrac;
        } else {
            int perksInTier = Math.min(4, filled / 2);
            cumulativePerks = perksInTier;
            float tierFrac = perksInTier / 4.0F;
            overall = (1.0F / 3.0F) * tierFrac;
        }

        return new ProgressionState(fam, cumulativePerks, Math.max(0.0F, Math.min(1.0F, overall)));
    }

    public static void render(PoseStack pose, SubmitNodeCollector collector, int light, HumanoidRenderState state, float yRot, float xRot) {
        ItemStack chest = state.chestEquipment;
        if (chest == null || !Infinium.isChestplate(chest)) return;

        boolean on = chest.has(DataComponents.GLIDER);
        if (!on) {
            if (state instanceof AvatarRenderState a) {
                WingFold.reset(a.id);
            }
            return; // Wings toggled off: disappear immediately
        }

        float foldT = state instanceof AvatarRenderState a ? WingFold.progress(a.id, true) : 1F;
        float flightT = state instanceof AvatarRenderState a ? WingFold.flightProgress(a.id, state.isFallFlying) : (state.isFallFlying ? 1F : 0F);

        // Store state on duck interface
        if (state instanceof WingFoldState wfs) {
            wfs.infinium$setFold(foldT);
            wfs.infinium$setFlight(flightT);
        }

        // Fully absorbed: nothing rendered
        if (foldT <= 0.001F) return;

        ProgressionState prog = resolveProgression(state);
        int fam = prog.family();
        int cumulativePerks = prog.perksUnlocked();
        float overallProgress = prog.overallProgress();

        // Astral, Singularity, and Infinity: No physical wings. Pure cosmic energy / particle flight.
        // Only Cybernetic (1) and CyberPsycho (3) keep physical jet-thruster wings.
        // M6: Infinity (4) now also renders its physical booster rig (bigger than Cyber Psycho's) on top of its particles.
        if (fam != 1 && fam != 3 && fam != 4) return;

        // 1. Dynamic Opacity: Linear scale from 0.40F (unupgraded Tier 1) up to 0.90F (max Infinity)
        float baseAlpha = 0.40F + 0.50F * overallProgress;
        float alpha = Math.max(0.0F, Math.min(1.0F, baseAlpha * InfiniumClientConfig.wingOpacityMultiplier()));

        // 2. Dynamic Pieces: piece_0 (base core) + 1 piece per unlocked perk across the continuum
        // Cybernetic & CyberPsycho keep jet-wing style. Infinity becomes compact thrusters (physical wings no longer necessary).
        ModelPart root = InfiniumWingModels.getRoot(fam);
        ModelPart leftWing = root.getChild("left_wing");
        ModelPart rightWing = root.getChild("right_wing");

        int totalPieces = InfiniumWingModels.pieceCount(fam);
        for (int i = 0; i < totalPieces; i++) {
            boolean pieceVis = i <= cumulativePerks;   // M6: Infinity = 0..8 baseline + 1 per Infinity perk (cumulative 8..16)
            leftWing.getChild("piece_" + i).visible = pieceVis;
            rightWing.getChild("piece_" + i).visible = pieceVis;
        }

        // Smooth animations
        float k = WingFold.smooth(foldT);
        float fl = WingFold.smooth(flightT);

        // Angular poses: Resting sleek V-stance vs Flight expansive wingspan
        float restRx = 0.16F, restRy = -0.15F, restRz = -0.12F;
        float flightRx = 0.35F, flightRy = -0.22F, flightRz = -0.85F;

        float rx = WingFold.lerp(restRx, flightRx, fl);
        float ry = WingFold.lerp(restRy, flightRy, fl);
        float rz = WingFold.lerp(restRz, flightRz, fl);

        float cy = state.isCrouching ? 3.0F : 0.0F;
        float cz = state.isCrouching ? 1.5F : 0.0F;
        float crouchRx = state.isCrouching ? 0.5F : 0.0F;

        // Smooth fold scale & continuous progression sizing
        float baseScale = (0.72F + 0.33F * overallProgress) * (fam == 4 ? 1.12F : 1F) * InfiniumClientConfig.wingScaleMultiplier();   // M6: Infinity rig bigger
        float s = Math.max(0.001F, baseScale * k);

        leftWing.xRot = rx + crouchRx;
        leftWing.yRot = ry;
        leftWing.zRot = rz;
        leftWing.xScale = s; leftWing.yScale = s; leftWing.zScale = s;
        leftWing.x = WingFold.lerp(0.0F, 1.5F, k);
        leftWing.y = WingFold.lerp(5.5F, 1.5F + cy, k);
        leftWing.z = WingFold.lerp(1.5F, 2.2F + cz, k);

        rightWing.xRot = rx + crouchRx;
        rightWing.yRot = -ry;
        rightWing.zRot = -rz;
        rightWing.xScale = s; rightWing.yScale = s; rightWing.zScale = s;
        rightWing.x = WingFold.lerp(0.0F, -1.5F, k);
        rightWing.y = WingFold.lerp(5.5F, 1.5F + cy, k);
        rightWing.z = WingFold.lerp(1.5F, 2.2F + cz, k);

        int dye = GlowColor.argb(chest);
        int alphaBits = ((int) (alpha * 255.0F)) & 0xFF;
        int baseColor = (alphaBits << 24) | 0xFFFFFF;

        pose.pushPose();

        // Pass 1: Translucent Base Wing
        collector.order(110).submitModelPart(
            root, pose,
            RenderTypes.armorTranslucent(BASE_TEX[fam]),
            light, OverlayTexture.NO_OVERLAY, null,
            baseColor, null
        );

        // Pass 1b: Accent dye layer if dyed
        if (dye != -1) {
            int accentColor = (alphaBits << 24) | (dye & 0xFFFFFF);
            collector.order(111).submitModelPart(
                root, pose,
                RenderTypes.armorTranslucent(ACCENT_GRAY_TEX[fam]),
                light, OverlayTexture.NO_OVERLAY, null,
                accentColor, null
            );
        }

        // Pass 2: Animated emissive glow overlay (speed & density scaled by overallProgress)
        float speedMul = GlowAnim.speedMul(overallProgress);
        int f = GlowAnim.frame(GLOW_FRAMES[fam], GLOW_FRAME_MS[fam], speedMul);

        Identifier glowTex = dye != -1 ? GLOW_GRAY_TEX[fam][f] : GLOW_TEX[fam][f];

        int prevItem = IrisGlowCompat.pushGlowId();
        try {
            collector.order(115).submitModelPart(
                root, pose,
                RenderTypes.armorCutoutNoCull(glowTex),
                0xF000F0, OverlayTexture.NO_OVERLAY, null,
                dye != -1 ? dye : -1, null
            );
        } finally {
            IrisGlowCompat.pop(prevItem);
        }

        pose.popPose();
    }

    private InfiniumWingsRenderer() {}
}
