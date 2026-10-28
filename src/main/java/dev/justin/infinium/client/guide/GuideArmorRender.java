package dev.justin.infinium.client.guide;

import dev.justin.infinium.CrystalCharge;
import dev.justin.infinium.Infinium;
import dev.justin.infinium.InfiniumStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.ArmorStandRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Live armor render for guide armor pages: a client-only armor stand (never added to the level) wearing display
 * copies of a full set with crystal charge forced to max (CrystalCharge.FILLED = slot cap) and wings deployed
 * (GLIDER component). Glow / wing renderers read charge from the equipment stacks on the render state, so this
 * shows the max-charge look. Rotates slowly; drag to rotate. Particles do not spawn in screens, so the book draws
 * its own sparkles (drawSparkles). Uses GuiGraphicsExtractor.entity (picture-in-picture, SCREEN coords).
 */
public final class GuideArmorRender {
    private static ArmorStand stand;
    private static String standKey = "";
    private static float dragYaw;
    private static long startMs = System.currentTimeMillis();
    public static boolean wings = true;

    private GuideArmorRender() {}

    public static void addDrag(double dxScreen) { dragYaw += (float) dxScreen * 1.6f; }

    /** family 0..4 = Astral..Infinity, 5 = Bread. */
    public static ItemStack[] set(int family, boolean wingsOn) {
        Item[] items = family == 4 ? new Item[]{Infinium.INF_HELMET, Infinium.INF_CHESTPLATE, Infinium.INF_LEGGINGS, Infinium.INF_BOOTS}
                : family == 5 ? new Item[]{Infinium.BREAD_HELMET, Infinium.BREAD_CHESTPLATE, Infinium.BREAD_LEGGINGS, Infinium.BREAD_BOOTS}
                : new Item[]{Infinium.HELMET, Infinium.CHESTPLATE, Infinium.LEGGINGS, Infinium.BOOTS};
        ItemStack[] out = new ItemStack[4];
        for (int i = 0; i < 4; i++) {
            ItemStack s = new ItemStack(items[i]);
            if (family <= 3) s.set(InfiniumStyle.STYLE, InfiniumStyle.values()[family]);
            if (family <= 4) {
                int cap = CrystalCharge.slots(s);
                if (cap > 0) s.set(CrystalCharge.FILLED, cap);
            }
            if (i == 1 && family <= 4) {
                if (wingsOn) s.set(DataComponents.GLIDER, Unit.INSTANCE); else s.remove(DataComponents.GLIDER);
            }
            out[i] = s;
        }
        return out;
    }

    private static ArmorStand stand(int family) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        String key = family + ":" + wings + ":" + System.identityHashCode(mc.level);
        if (stand == null || !key.equals(standKey)) {
            ArmorStand a = new ArmorStand(mc.level, 0, 0, 0);
            a.setShowArms(true);
            a.setNoBasePlate(true);
            ItemStack[] s = set(family, wings);
            a.setItemSlot(EquipmentSlot.HEAD, s[0]);
            a.setItemSlot(EquipmentSlot.CHEST, s[1]);
            a.setItemSlot(EquipmentSlot.LEGS, s[2]);
            a.setItemSlot(EquipmentSlot.FEET, s[3]);
            stand = a;
            standKey = key;
        }
        return stand;
    }

    /**
     * Draws the set inside the screen-space box. Returns false (caller draws a placeholder) when no level is loaded
     * or the renderer throws.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static boolean draw(GuiGraphicsExtractor g, int family, int x0, int y0, int x1, int y1) {
        ArmorStand a = stand(family);
        if (a == null) return false;
        try {
            a.tickCount = (int) ((System.currentTimeMillis() - startMs) / 50L);
            EntityRenderer r = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(a);
            EntityRenderState st = r.createRenderState(a, 1.0f);
            st.shadowPieces.clear();
            st.outlineColor = 0;
            st.lightCoords = 0xF000F0;
            float yaw = ((System.currentTimeMillis() - startMs) / 1000f * 20f + dragYaw + 200f) % 360f;   // ~20 deg/s
            if (st instanceof LivingEntityRenderState l) {
                l.bodyRot = yaw;
                l.yRot = 0;
                l.xRot = 0;
            }
            if (st instanceof ArmorStandRenderState as) as.yRot = yaw;
            float h = (y1 - y0);
            float scale = h / 2.35f;   // stand ~2 blocks + wing tips
            Quaternionf rot = new Quaternionf().rotateZ((float) Math.PI);
            Quaternionf cam = new Quaternionf().rotateX(-8f * 0.017453292f);
            rot.mul(cam);
            Vector3f off = new Vector3f(0, st.boundingBoxHeight / 2f + 0.06f, 0);
            g.entity(st, scale, off, rot, cam, x0, y0, x1, y1);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Charge sparkles around the render (book-local coords, under the book pose). Purple/pink only. */
    public static void drawSparkles(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        long ms = System.currentTimeMillis();
        for (int i = 0; i < 14; i++) {
            long seed = i * 7919L;
            float period = 1600f + (seed % 900);
            float t = ((ms + seed * 13) % (long) period) / period;
            float px = x + 6 + (float) (((seed * 31) % 1000) / 1000.0) * (w - 12);
            float py = y + h * 0.85f - t * h * 0.7f;
            float a = (float) Math.sin(t * Math.PI);
            int alpha = (int) (200 * a);
            if (alpha < 10) continue;
            int col = (i % 3 == 0) ? 0xFF66CC : (i % 3 == 1) ? 0xD84CFF : 0xB040E0;
            int ix = (int) (px + Math.sin(ms / 500.0 + i) * 2), iy = (int) py;
            g.fill(ix, iy, ix + 1, iy + 1, (alpha << 24) | col);
            if (i % 4 == 0 && a > 0.6f) {   // small cross twinkle
                int a2 = (int) (alpha * 0.5f) << 24;
                g.fill(ix - 1, iy, ix, iy + 1, a2 | col);
                g.fill(ix + 1, iy, ix + 2, iy + 1, a2 | col);
                g.fill(ix, iy - 1, ix + 1, iy, a2 | col);
                g.fill(ix, iy + 1, ix + 1, iy + 2, a2 | col);
            }
        }
    }
}
