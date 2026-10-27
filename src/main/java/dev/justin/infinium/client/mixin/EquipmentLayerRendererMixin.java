package dev.justin.infinium.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.justin.infinium.GlowColor;
import dev.justin.infinium.Infinium;
import dev.justin.infinium.InfiniumStyle;
import dev.justin.infinium.client.GlowAnim;
import dev.justin.infinium.client.HelmetExtras;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.EquipmentAssetManager;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.EquipmentAsset;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Infinium armor/wings: full-bright animated glow overlay.
 *
 * The glow_marker layer in infinium.json acts as a sentinel. At TAIL of renderLayers()
 * we detect it and submit the current animation-frame texture at full-bright (0xF000F0).
 * Dyed armor routes through grayscale glow textures; undyed uses full-RGB glow textures.
 */
@Mixin(EquipmentLayerRenderer.class)
public abstract class EquipmentLayerRendererMixin {
    @Accessor("equipmentAssets")
    protected abstract EquipmentAssetManager infinium$equipmentAssets();

    /* ── pre-computed glow-frame Identifiers ── */
    @Unique private static final int FRAMES = GlowAnim.FRAMES;
    @Unique private static final Identifier[][] TEX = new Identifier[3][FRAMES];
    @Unique private static final Identifier[][] GRAY = new Identifier[3][FRAMES];
    @Unique private static final Identifier[][][] STYLE_TEX = new Identifier[InfiniumStyle.values().length][3][];
    @Unique private static final Identifier[][][] STYLE_GRAY = new Identifier[InfiniumStyle.values().length][3][];
    @Unique private static final Identifier[][] INF_TEX = new Identifier[3][dev.justin.infinium.client.InfinityLook.FRAMES];
    @Unique private static final Identifier[][] INF_GRAY = new Identifier[3][dev.justin.infinium.client.InfinityLook.FRAMES];
    @Unique private static final Identifier GLOW_MARKER = Infinium.id("textures/entity/equipment/humanoid/glow_marker.png");

    static {
        String[] NAMES = {"humanoid", "humanoid_leggings", "wings"};
        for (int l = 0; l < 3; l++) for (int f = 0; f < FRAMES; f++) {
            TEX[l][f] = Infinium.id("textures/entity/infinium_glow/" + NAMES[l] + "/" + f + ".png");
            GRAY[l][f] = Infinium.id("textures/entity/infinium_glow_gray/" + NAMES[l] + "/" + f + ".png");
        }
        for (int l = 0; l < 3; l++) for (int f = 0; f < dev.justin.infinium.client.InfinityLook.FRAMES; f++) {
            INF_TEX[l][f] = Infinium.id("textures/entity/infinium_glow/infinity/" + NAMES[l] + "/" + f + ".png");
            INF_GRAY[l][f] = Infinium.id("textures/entity/infinium_glow_gray/infinity/" + NAMES[l] + "/" + f + ".png");
        }
        for (InfiniumStyle st : InfiniumStyle.values()) {
            for (int l = 0; l < 3; l++) {
                STYLE_TEX[st.ordinal()][l] = new Identifier[st.frames];
                STYLE_GRAY[st.ordinal()][l] = new Identifier[st.frames];
                for (int f = 0; f < st.frames; f++) {
                    STYLE_TEX[st.ordinal()][l][f] = Infinium.id("textures/entity/infinium_glow/" + st.id + "/" + NAMES[l] + "/" + f + ".png");
                    STYLE_GRAY[st.ordinal()][l][f] = Infinium.id("textures/entity/infinium_glow_gray/" + st.id + "/" + NAMES[l] + "/" + f + ".png");
                }
            }
        }
    }

    /* ── glow_marker sentinel check ── */
    @Unique
    private static boolean infinium$isGlowMarker(EquipmentClientInfo.Layer layer) {
        return layer.textureId().getPath().endsWith("glow_marker");
    }

    @Unique
    private static float infinium$visualProgress(Object state, ItemStack stack) {
        if (state instanceof net.minecraft.client.renderer.entity.state.HumanoidRenderState hrs) {
            ItemStack h = hrs.headEquipment, c = hrs.chestEquipment, l = hrs.legsEquipment, f = hrs.feetEquipment;
            if (h != null && c != null && l != null && f != null
                    && Infinium.isHelmet(h) && Infinium.isChestplate(c) && Infinium.isLeggings(l) && Infinium.isBoots(f)) {
                boolean allInf = Infinium.isInfinity(h) && Infinium.isInfinity(c) && Infinium.isInfinity(l) && Infinium.isInfinity(f);
                dev.justin.infinium.SetLook look = null;
                if (allInf) look = dev.justin.infinium.SetLook.INFINITY;
                else {
                    for (InfiniumStyle st : new InfiniumStyle[]{InfiniumStyle.SINGULARITY, InfiniumStyle.CYBERPSYCHO, InfiniumStyle.ASTRAL, InfiniumStyle.CYBERNETIC}) {
                        if (Infinium.countsAs(h, st) && Infinium.countsAs(c, st) && Infinium.countsAs(l, st) && Infinium.countsAs(f, st)) {
                            look = dev.justin.infinium.SetLook.values()[st.ordinal()];
                            break;
                        }
                    }
                }
                if (look != null) {
                    int fam = look.ordinal();
                    int cap = dev.justin.infinium.CrystalCharge.setSlots(look);
                    int min = Math.min(
                            Math.min(dev.justin.infinium.CrystalCharge.filledForFamily(h, fam), dev.justin.infinium.CrystalCharge.filledForFamily(c, fam)),
                            Math.min(dev.justin.infinium.CrystalCharge.filledForFamily(l, fam), dev.justin.infinium.CrystalCharge.filledForFamily(f, fam)));
                    float tierFrac = cap <= 0 ? 0F : Math.min(1F, (float) min / cap);
                    float base = fam == 4 ? (2F / 3F) : (fam == 2 || fam == 3) ? (1F / 3F) : 0F;
                    return base + tierFrac * (1F / 3F);
                }
            }
        }
        return dev.justin.infinium.CrystalCharge.pieceVisualProgress(stack);
    }

    @Inject(method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V",
            at = @At("HEAD"), cancellable = true)
    private <S> void infinium$hideAbsorbedWings(EquipmentClientInfo.LayerType layerType, ResourceKey<EquipmentAsset> assetId, Model<? super S> model, S state,
                                                ItemStack stack, PoseStack pose, SubmitNodeCollector collector,
                                                int light, Identifier playerTex, int outline, int order, CallbackInfo ci) {
        if (layerType == EquipmentClientInfo.LayerType.WINGS && (Infinium.INFINITY_ASSET.equals(assetId) || Infinium.ASSET.equals(assetId))) {
            ci.cancel(); // Infinium wings are rendered exclusively by InfiniumWingsRenderer
        }
    }

    /* ── TAIL injection — submit the animated glow overlay ── */
    @Inject(method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V",
            at = @At("TAIL"))
    private <S> void infinium$glow(EquipmentClientInfo.LayerType layerType, ResourceKey<EquipmentAsset> assetId, Model<? super S> model, S state,
                                   ItemStack stack, PoseStack pose, SubmitNodeCollector collector,
                                   int light, Identifier playerTex, int outline, int order, CallbackInfo ci) {
        boolean infinity = Infinium.INFINITY_ASSET.equals(assetId);
        if (!infinity && !Infinium.ASSET.equals(assetId)) return;

        int layerIdx = layerType == EquipmentClientInfo.LayerType.HUMANOID ? 0
                        : layerType == EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS ? 1 : -1;
        if (layerIdx < 0) return;

        int dye = GlowColor.argb(stack);
        boolean isGray = dye != -1;   // dyed -> grayscale glow tinted by the dye (12.9 a)
        float speedMul = GlowAnim.speedMul(infinium$visualProgress(state, stack));

        /* ── helmet extras (only for full humanoid layer) ── */
        if (layerIdx == 0 && Infinium.isHelmet(stack) && model instanceof HumanoidModel<?> hm) {
            model.setupAnim(state);
            HelmetExtras.submit(hm, pose, collector, light, order, dye, infinity ? -2 : HelmetExtras.index(InfiniumStyle.get(stack)), speedMul);
        }

        /* ── resolve animation frame ── */
        Identifier tex;
        if (infinity) {
            int frame = GlowAnim.frame(dev.justin.infinium.client.InfinityLook.FRAMES, dev.justin.infinium.client.InfinityLook.FRAME_MS, speedMul);
            tex = isGray ? INF_GRAY[layerIdx][frame] : INF_TEX[layerIdx][frame];
        } else {
            InfiniumStyle style = InfiniumStyle.get(stack);
            if (style == null) style = InfiniumStyle.ASTRAL;   // defensive: no unstyled armor exists (R4)
            int frame = GlowAnim.frame(style.frames, style.frameMs, speedMul);
            tex = isGray ? STYLE_GRAY[style.ordinal()][layerIdx][frame] : STYLE_TEX[style.ordinal()][layerIdx][frame];
        }

        /* ── submit full-bright glow overlay ── */
        int infinium$prevItem = dev.justin.infinium.client.compat.IrisGlowCompat.pushGlowId(); // Iris: tag overlay as glowstone_dust -> emissive in shaderpacks
        try {
            collector.order(order + 10).submitModel(
                    model, state, pose,
                    RenderTypes.armorCutoutNoCull(tex),
                    0xF000F0,                          // full-bright — ignores ambient/ao
                    OverlayTexture.NO_OVERLAY,
                    isGray ? dye : -1,                 // dyed: gray glow tinted by the dye; undyed: texture colour
                    null, outline, null
            );
        } finally {
            dev.justin.infinium.client.compat.IrisGlowCompat.pop(infinium$prevItem);
        }
    }
}
