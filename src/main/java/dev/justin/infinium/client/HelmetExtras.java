package dev.justin.infinium.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.justin.infinium.Infinium;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/**
 * Helmet h21 extra geometry, drawn in head space on top of the vanilla armor head:
 *  - h13 face frame: two slim side bars + a brow bar outlining the open face (glowing, emissive pass)
 *  - shard gem centerd on the forehead above the brow bar; its colours follow the glow colour (default purple, dye tint)
 * The face frame needs geometry: the armor texture border rule forbids purple on the face-hole edge pixels.
 *  - R8 13.16 A: two 45-degree chamfer strips close the cut top corners of the helmet shell (texOffs (0,16)).
 * Texture 32x32: entity/infinium_helmet_extra/base.png + glow_<family>/N.png, same frame count and clock as that family's suit glow.
 */
public final class HelmetExtras {
    private static final Identifier BASE = Infinium.id("textures/entity/infinium_helmet_extra/base.png");
    private static final Identifier ACCENT_GRAY = Infinium.id("textures/entity/infinium_helmet_extra/accent_gray.png");   // dyed: accents only, netherite stays
    /** Families: 0 Astral, 1 Cybernetic, 2 Singularity, 3 Cyber Psycho, 4 Infinity. */
    static final String[] DIRS = {"glow", "glow_cybernetic", "glow_singularity", "glow_cyberpsycho", "glow_infinity"};
    public static final int[] FRAMES = {40, 40, 100, 80, InfinityLook.FRAMES}, FRAME_MS = {300, 300, 100, 100, InfinityLook.FRAME_MS};
    private static final Identifier[][] GLOW = new Identifier[5][], GLOW_GRAY = new Identifier[5][];
    static {
        for (int i = 0; i < 5; i++) {
            GLOW[i] = new Identifier[FRAMES[i]]; GLOW_GRAY[i] = new Identifier[FRAMES[i]];
            for (int f = 0; f < FRAMES[i]; f++) {
                GLOW[i][f] = Infinium.id("textures/entity/infinium_helmet_extra/" + DIRS[i] + "/" + f + ".png");
                GLOW_GRAY[i][f] = Infinium.id("textures/entity/infinium_helmet_extra/" + DIRS[i] + "_gray/" + f + ".png");
            }
        }
    }
    public static int index(dev.justin.infinium.InfiniumStyle st) { return st == null ? 0 : st.ordinal(); }
    private static ModelPart root;

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition r = mesh.getRoot();
        // face frame (head coords: face opening x -3..3, y -5..0 on the helmet front at z = -5)
        r.addOrReplaceChild("frame_left",  CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -6.0F, -5.6F, 1.0F, 6.0F, 0.6F), PartPose.ZERO);
        r.addOrReplaceChild("frame_right", CubeListBuilder.create().texOffs(0, 0).mirror().addBox(3.0F, -6.0F, -5.6F, 1.0F, 6.0F, 0.6F), PartPose.ZERO);
        r.addOrReplaceChild("frame_brow",  CubeListBuilder.create().texOffs(4, 0).addBox(-3.0F, -6.0F, -5.6F, 6.0F, 1.0F, 0.6F), PartPose.ZERO);
        // amethyst shard on the forehead, above the brow bar
        r.addOrReplaceChild("gem_0", CubeListBuilder.create().texOffs(12, 9).addBox(-0.25F, -8.90F, -5.35F, 0.5F, 0.4F, 0.35F), PartPose.ZERO);
        r.addOrReplaceChild("gem_1", CubeListBuilder.create().texOffs(16, 9).addBox(-0.5F, -8.50F, -5.35F, 1.0F, 0.4F, 0.35F), PartPose.ZERO);
        r.addOrReplaceChild("gem_2", CubeListBuilder.create().texOffs(22, 9).addBox(-0.75F, -8.10F, -5.35F, 1.5F, 0.4F, 0.35F), PartPose.ZERO);
        r.addOrReplaceChild("gem_3", CubeListBuilder.create().texOffs(16, 12).addBox(-0.5F, -7.70F, -5.35F, 1.0F, 0.4F, 0.35F), PartPose.ZERO);
        r.addOrReplaceChild("gem_4", CubeListBuilder.create().texOffs(12, 12).addBox(-0.25F, -7.30F, -5.35F, 0.5F, 0.4F, 0.35F), PartPose.ZERO);
        // 13.16 A chamfer: 45-degree bevel from the cut top edge (+-3.75, -9) to the cut side edge (+-5, -7.75), z -4.98..4.98
        r.addOrReplaceChild("chamfer_left",  CubeListBuilder.create().texOffs(0, 16).addBox(0F, -0.9F, -4.98F, 1.768F, 0.9F, 9.96F),
            PartPose.offsetAndRotation(-3.75F, -9F, 0F, 0F, 0F, (float) Math.toRadians(135)));
        r.addOrReplaceChild("chamfer_right", CubeListBuilder.create().texOffs(0, 16).mirror().addBox(0F, 0F, -4.98F, 1.768F, 0.9F, 9.96F),
            PartPose.offsetAndRotation(3.75F, -9F, 0F, 0F, 0F, (float) Math.toRadians(45)));
        return LayerDefinition.create(mesh, 32, 32);   // was 32 x 16
    }

    /** Called from the equipment layer (HUMANOID layer, Infinium/Infinity helmet) with the armor model posed. tint = dye ARGB or -1.
     *  fam: 0..3 style family, 4 (or -2) = Infinity. Same texture/tint resolution and clock as the suit glow (EquipmentLayerRendererMixin). */
    public static void submit(HumanoidModel<?> model, PoseStack pose, SubmitNodeCollector collector, int light, int order, int tint, int fam) {
        submit(model, pose, collector, light, order, tint, fam, 1.0F);
    }
    public static void submit(HumanoidModel<?> model, PoseStack pose, SubmitNodeCollector collector, int light, int order, int tint, int fam, float speedMul) {
        int fi = fam < 0 ? 4 : Math.min(4, fam);
        if (root == null) root = createLayer().bakeRoot();
        pose.pushPose();
        model.root().translateAndRotate(pose);
        model.head.translateAndRotate(pose);
        collector.order(order + 5).submitModelPart(root, pose, RenderTypes.armorCutoutNoCull(BASE), light, OverlayTexture.NO_OVERLAY, null);
        if (tint != -1) collector.order(order + 6).submitModelPart(root, pose, RenderTypes.armorCutoutNoCull(ACCENT_GRAY), light, OverlayTexture.NO_OVERLAY, null, tint, null);
        int f = GlowAnim.frame(FRAMES[fi], FRAME_MS[fi], speedMul);
        int prevId = dev.justin.infinium.client.compat.IrisGlowCompat.pushGlowId();   // Iris: glow pass only, base stays normal
        try {
            if (tint == -1) collector.order(order + 11).submitModelPart(root, pose, RenderTypes.armorCutoutNoCull(GLOW[fi][f]), 0xF000F0, OverlayTexture.NO_OVERLAY, null);
            else collector.order(order + 11).submitModelPart(root, pose, RenderTypes.armorCutoutNoCull(GLOW_GRAY[fi][f]), 0xF000F0, OverlayTexture.NO_OVERLAY, null, tint, null);
        } finally { dev.justin.infinium.client.compat.IrisGlowCompat.pop(prevId); }
        pose.popPose();
    }
    private HelmetExtras() {}
}
