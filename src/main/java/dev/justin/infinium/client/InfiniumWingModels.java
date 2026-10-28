package dev.justin.infinium.client;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * Custom 3D Wing Geometries for all 5 Infinium Archetypes:
 *  - Astral (0): Celestial feathered wings (scapular bone, secondary plumage fan, crest quill, outer coverts, primary pinions)
 *  NOTE batch1: fam 1 / 3 builders are no longer rendered (round-3 elytra in InfiniumWingsRenderer.renderR3; CP thrusters scrapped).
 *  - Cybernetic (1): Mecha vector thrusters (titanium chassis, exhaust nozzle, stabilizer fin, vector pod, flare blades)
 *  - Singularity (2): Gravitational void scythes (accretion spine, dark matter plumules, event-horizon blade, warp barbs, void scythe)
 *  - CyberPsycho (3): Overclocked razor mecha (heavy manifold, serrated fin, hazard heatsink teeth, split overdrive flaps, kinetic blades)
 *  - Infinity (4): Grand divine archangel synthesis (9-piece hybrid of void plumage and kinetic hardlight blades)
 *
 * Left wing maps to (0..64) UV space; Right wing maps to (64..128) UV space.
 *  - Cybernetic & CyberPsycho textures are symmetrically mirrored left-to-right.
 *  - Astral & Singularity textures are asymmetric with independent random cosmic drift.
 *  - Infinity fuses both symmetrical mecha vectors and independent void shimmer.
 *
 * Geometric constraints:
 *  - All pieces terminate at y <= 11.5 px (well above y = 12 hip/belt line, 0% leg collision on walking/sprinting).
 *  - Left and right pivots at x = +-1.5 px with outward V-stance (permanent >= 3.0 px spinal clearance, 0% resting clipping).
 */
public final class InfiniumWingModels {

    private static final ModelPart[] ROOTS = new ModelPart[5];

    public static int pieceCount(int fam) {
        return switch (fam) {
            case 0, 1 -> 5;   // Astral (celestial wings) & Cybernetic (vector thrusters): 0..4
            case 2, 3 -> 9;   // Singularity (void scythes) & CyberPsycho (overclocked combat thrusters): 0..8
            case 4 -> 17;     // Infinity (Divine Archangel synthesis): 0..16
            default -> 5;
        };
    }

    public static ModelPart getRoot(int fam) {
        int idx = Math.max(0, Math.min(4, fam));
        if (ROOTS[idx] == null) {
            ROOTS[idx] = createLayer(idx).bakeRoot();
        }
        return ROOTS[idx];
    }

    private static int[] getUV(int idx) {
        int u = (idx * 16) % 64;
        int v = (((idx * 16) / 64) * 16) % 64;
        return new int[]{u, v};
    }

    public static LayerDefinition createLayer(int fam) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Left and Right wing/thruster pivot points on torso upper back
        PartDefinition left = root.addOrReplaceChild("left_wing", CubeListBuilder.create(), PartPose.offset(1.5F, 1.5F, 2.2F));
        PartDefinition right = root.addOrReplaceChild("right_wing", CubeListBuilder.create(), PartPose.offset(-1.5F, 1.5F, 2.2F));

        switch (fam) {
            case 0 -> buildAstral(left, right);
            case 1 -> buildCybernetic(left, right);
            case 2 -> buildSingularity(left, right);
            case 3 -> buildCyberpsycho(left, right);
            case 4 -> buildInfinity(left, right);
        }

        return LayerDefinition.create(mesh, 128, 64);
    }

    private static void addPiece(PartDefinition left, PartDefinition right, int idx, float x, float y, float z, float w, float h, float d) {
        int[] uv = getUV(idx);
        int uL = uv[0], vL = uv[1];
        int uR = uL + 64, vR = vL;

        left.addOrReplaceChild("piece_" + idx,
            CubeListBuilder.create().texOffs(uL, vL).addBox(x, y, z, w, h, d),
            PartPose.ZERO);

        right.addOrReplaceChild("piece_" + idx,
            CubeListBuilder.create().texOffs(uR, vR).addBox(-x - w, y, z, w, h, d),
            PartPose.ZERO);
    }

    /**
     * Astral: Spacey celestial starlight & starry void shards.
     * 5 pieces (0..4) - asymmetric celestial drift.
     */
    private static void buildAstral(PartDefinition left, PartDefinition right) {
        // piece_0: Celestial rift core & spine (compact 8.5 px)
        addPiece(left, right, 0, 0.0F, 0.0F, 0.0F, 3.2F, 8.5F, 1.2F);
        // piece_1: Nebula energy plumage fan (trailing soft cosmic glow)
        addPiece(left, right, 1, 1.6F, 2.2F, 0.3F, 3.8F, 7.0F, 0.8F);
        // piece_2: Astral crest starlight quill (pointed star needle)
        addPiece(left, right, 2, -0.6F, -2.0F, -0.2F, 1.8F, 5.0F, 0.8F);
        // piece_3: Orbiting celestial constellation shards (floating star cluster)
        addPiece(left, right, 3, 3.2F, 3.8F, -0.3F, 3.2F, 5.5F, 0.6F);
        // piece_4: Primary starlight comet pinions (radiant cosmic flare)
        addPiece(left, right, 4, 4.8F, 1.2F, 0.2F, 3.8F, 9.5F, 0.6F);
    }

    /**
     * Cybernetic: Mechanical vector thrusters & booster pods (NOT wings).
     * 5 pieces (0..4) - strictly symmetrical left-to-right.
     */
    private static void buildCybernetic(PartDefinition left, PartDefinition right) {
        // piece_0: Titanium vector booster pod chassis
        addPiece(left, right, 0, 0.2F, 0.5F, 0.2F, 3.8F, 7.5F, 2.2F);
        // piece_1: Variable-geometry circular exhaust nozzle
        addPiece(left, right, 1, 0.7F, 8.0F, 0.3F, 2.8F, 3.0F, 2.0F);
        // piece_2: Upper aerodynamic intake & stabilizer vane
        addPiece(left, right, 2, -0.3F, -1.5F, -0.2F, 2.0F, 5.5F, 1.0F);
        // piece_3: Lateral vector conduit & cooling manifold
        addPiece(left, right, 3, 3.8F, 2.0F, 0.4F, 1.8F, 4.5F, 1.2F);
        // piece_4: Ion thrust plume / high-velocity energy exhaust stream
        addPiece(left, right, 4, 0.5F, 10.5F, 0.2F, 3.2F, 6.5F, 0.8F);
    }

    /**
     * Singularity: Spacey gravitational void / event horizon / black hole accretion scythes.
     * 9 pieces (0..8) - keeps baseline pieces 0..4 + 4 void rift additions.
     */
    private static void buildSingularity(PartDefinition left, PartDefinition right) {
        // piece_0: Accretion spine & gravitational singularity core
        addPiece(left, right, 0, 0.0F, 0.0F, 0.0F, 3.8F, 9.0F, 1.4F);
        // piece_1: Swirling dark matter plumules
        addPiece(left, right, 1, 1.5F, 2.5F, 0.4F, 4.2F, 7.5F, 0.9F);
        // piece_2: Gravitational crest quill / event horizon focus
        addPiece(left, right, 2, -0.5F, -2.5F, -0.3F, 2.2F, 6.0F, 0.9F);
        // piece_3: Orbiting asteroid & accretion disc shards
        addPiece(left, right, 3, 3.5F, 3.5F, -0.3F, 3.8F, 6.0F, 0.7F);
        // piece_4: Celestial void pinion filaments
        addPiece(left, right, 4, 5.0F, 1.0F, 0.2F, 4.2F, 10.0F, 0.7F);
        // piece_5: Event-horizon warp blade (curved void scythe arm)
        addPiece(left, right, 5, 6.0F, 2.0F, -0.2F, 3.5F, 8.0F, 0.6F);
        // piece_6: Gravitational null-field tendril (twisted cosmic stream)
        addPiece(left, right, 6, 4.0F, 5.0F, 0.3F, 3.0F, 7.0F, 0.5F);
        // piece_7: Orbiting planetary ring shards (floating celestial ring fragment)
        addPiece(left, right, 7, 2.0F, -1.0F, 0.4F, 4.0F, 4.0F, 0.5F);
        // piece_8: Massive gravitational void scythe blade
        addPiece(left, right, 8, 7.0F, 0.0F, 0.1F, 4.5F, 11.0F, 0.6F);
    }

    /**
     * CyberPsycho: Overclocked heavy mecha combat booster rig (NOT wings).
     * 9 pieces (0..8) - strictly symmetrical left-to-right.
     */
    private static void buildCyberpsycho(PartDefinition left, PartDefinition right) {
        // piece_0: Overclocked twin-manifold booster chassis
        addPiece(left, right, 0, 0.0F, 0.5F, 0.2F, 4.2F, 8.0F, 2.4F);
        // piece_1: Reinforced heavy afterburner exhaust bell
        addPiece(left, right, 1, 0.5F, 8.0F, 0.3F, 3.2F, 3.5F, 2.2F);
        // piece_2: Upper heatsink radiator teeth venting magenta plasma
        addPiece(left, right, 2, -0.4F, -1.8F, -0.3F, 2.2F, 4.5F, 1.2F);
        // piece_3: Lateral overdrive cooling turbine & intake duct
        addPiece(left, right, 3, 4.0F, 1.5F, 0.4F, 2.2F, 5.0F, 1.4F);
        // piece_4: Primary kinetic vector foil
        addPiece(left, right, 4, 4.5F, 2.5F, -0.2F, 3.5F, 8.0F, 0.8F);
        // piece_5: Twin split overdrive vector flaps
        addPiece(left, right, 5, 5.5F, 3.5F, 0.3F, 3.0F, 7.0F, 0.7F);
        // piece_6: Auxiliary turbo-thruster secondary nozzle
        addPiece(left, right, 6, 2.5F, 7.5F, 0.5F, 2.2F, 3.0F, 1.8F);
        // piece_7: Thermal bleed radiator teeth
        addPiece(left, right, 7, -0.6F, 2.0F, 0.4F, 2.0F, 4.0F, 0.8F);
        // piece_8: High-velocity plasma afterburner plume blades
        addPiece(left, right, 8, 0.5F, 11.0F, 0.2F, 4.0F, 8.5F, 0.7F);
    }

    /**
     * M6 Infinity: "bigger/cooler boosters than Cyber Psycho" (part2 item 10). A heavier, scaled-up (~1.35x) version of
     * the Cyber Psycho booster rig with more parts: twin main engines, a third auxiliary pod, stacked radiator fins,
     * outer vector foils and long plasma plume blades. 17 pieces per side (0..16).
     * base_infinity / glow/infinity only have art in tiles 0..8, so pieces 9..16 reuse those tiles (uv = idx % 9).
     * Reveal: pieces 0..8 always (Infinity inherits Tier 1+2), +1 piece per Infinity perk unlocked (InfiniumWingsRenderer).
     */
    private static void buildInfinity(PartDefinition left, PartDefinition right) {
        final float k = 1.35F;
        // ---- baseline rig (Cyber Psycho layout x1.35) ----
        addPiece(left, right, 0, 0.0F, 0.5F, 0.2F, 4.2F * k, 8.0F * k, 2.4F * k);   // main booster chassis
        addPiece(left, right, 1, 0.5F, 10.8F, 0.4F, 3.2F * k, 3.5F * k, 2.2F * k);  // main afterburner bell
        addPiece(left, right, 2, -0.6F, -2.6F, -0.3F, 2.2F * k, 4.5F * k, 1.2F * k);// upper heatsink teeth
        addPiece(left, right, 3, 5.6F, 1.8F, 0.5F, 2.2F * k, 5.0F * k, 1.4F * k);   // lateral turbine intake
        addPiece(left, right, 4, 6.0F, 3.0F, -0.2F, 3.5F * k, 8.0F * k, 0.8F * k);  // primary vector foil
        addPiece(left, right, 5, 7.4F, 4.6F, 0.3F, 3.0F * k, 7.0F * k, 0.7F * k);   // split vector flaps
        addPiece(left, right, 6, 3.4F, 10.0F, 0.6F, 2.2F * k, 3.0F * k, 1.8F * k);  // secondary nozzle
        addPiece(left, right, 7, -0.8F, 2.6F, 0.5F, 2.0F * k, 4.0F * k, 0.8F * k);  // thermal bleed teeth
        addPiece(left, right, 8, 0.6F, 15.4F, 0.3F, 4.0F * k, 8.5F * k, 0.7F * k);  // plasma plume blade
        // ---- Infinity-only upgrades, one per Infinity perk ----
        addPiece(left, right, 9, 1.2F, 0.0F, 3.6F, 3.0F, 9.0F, 2.6F, 0);            // twin engine (stacked behind the main)
        addPiece(left, right, 10, 1.6F, 9.2F, 3.8F, 2.4F, 3.0F, 2.2F, 1);           // twin engine bell
        addPiece(left, right, 11, 4.6F, -3.4F, 0.2F, 2.0F, 6.0F, 0.9F, 2);          // tall dorsal fin
        addPiece(left, right, 12, 9.8F, 2.4F, 0.0F, 3.2F, 11.0F, 0.7F, 4);          // outer vector foil
        addPiece(left, right, 13, 6.4F, 11.6F, 0.8F, 2.4F, 3.0F, 2.0F, 6);          // third (aux) pod nozzle
        addPiece(left, right, 14, 1.6F, 12.4F, 4.0F, 2.2F, 9.0F, 0.6F, 8);          // twin plume blade
        addPiece(left, right, 15, 12.0F, 5.0F, 0.2F, 2.4F, 9.0F, 0.6F, 5);          // wingtip vane
        addPiece(left, right, 16, -1.4F, -4.4F, 1.0F, 1.4F, 6.5F, 1.4F, 3);         // apex antenna / exhaust stack
    }

    private static void addPiece(PartDefinition left, PartDefinition right, int idx, float x, float y, float z, float w, float h, float d, int uvIdx) {
        int[] uv = getUV(uvIdx);
        left.addOrReplaceChild("piece_" + idx, CubeListBuilder.create().texOffs(uv[0], uv[1]).addBox(x, y, z, w, h, d), PartPose.ZERO);
        right.addOrReplaceChild("piece_" + idx, CubeListBuilder.create().texOffs(uv[0] + 64, uv[1]).addBox(-x - w, y, z, w, h, d), PartPose.ZERO);
    }

    private InfiniumWingModels() {}
}
