package dev.justin.infinium.client;

import dev.justin.infinium.GlowColor;
import dev.justin.infinium.Infinium;
import dev.justin.infinium.InfiniumStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Client-only ambient particles. No blue / cyan anywhere.
 *  Armor (full set): ambient reference look in third person; soft viewmodel/peripheral aura in first person.
 *  Tools (full set + Infinium tool held): sparks and motes matching equipped set look, locked to tool in first person with short lifetime.
 */
public final class ArmorParticles {
    static final int[] PSYCHO = {0xB04CFF, 0xD24CFF, 0xFF4CE0, 0xFF7AD9, 0xE03CC8, 0xFF5FB0};
    static final int[] SING = {0x5A1A9E, 0x7A2BD0, 0x8E3CE6, 0x4A0F86, 0x6B23B8};
    static final int SING_BLACK = 0x0E0612;

    static int psycho(RandomSource r, int dye) { return PSYCHO[r.nextInt(PSYCHO.length)]; }   // dye mapping happens in dust()
    /** Dye for the tool pass (-1 = undyed). Armor uses per-piece dyeAt instead. Client thread only. */
    static int DYE = -1;
    /** True while a held tool is being processed: dust uses the tool's own dye (DYE) instead of the per-piece armor tint. */
    static boolean TOOL_TINT;
    /** Dyed: the style palette is gradient-mapped onto the dye: same brightness (max channel) as the original colour, dye hue. */
    static int dyeMap(int rgb) {
        if (DYE == -1) return rgb;
        int v = Math.max(rgb >> 16 & 255, Math.max(rgb >> 8 & 255, rgb & 255));
        int r = (DYE >> 16 & 255) * v / 255, g = (DYE >> 8 & 255) * v / 255, b = (DYE & 255) * v / 255;
        return r << 16 | g << 8 | b;
    }
    /** Body-local (ox right, oz forward, oy world y): head height and within ~0.6 blocks in front of the face, head width. */
    static boolean faceBox(Player p, double ox, double oy, double oz) {
        double hy = oy - p.getY();
        return hy > 1.30 && hy < 2.05 && oz > 0.0 && oz < 0.85 && Math.abs(ox) < 0.35;
    }
    /** Armor pass (CUR set): raw palette colour, tinted per height in exact(). Tools / no player: the tool's own dye via dyeMap. */
    static DustParticleOptions dust(int rgb, float size) {
        if (CUR != null && !TOOL_TINT) return new DustParticleOptions(rgb & 0xFFFFFF, size);
        return new DustParticleOptions(dyeMap(rgb), size);
    }
    /** Undyed stays the normal reverse-portal speck; any dye (piece or tool) switches to a tintable purple dust. */
    static ParticleOptions portal() {
        if (TOOL_TINT) return DYE != -1 ? new DustParticleOptions(dyeMap(0x9B4CFF), 0.6F) : ParticleTypes.REVERSE_PORTAL;
        if (!InfiniumClientConfig.show("dye_aura", false)) return ParticleTypes.REVERSE_PORTAL;
        boolean dyed = CUR != null ? anyPieceDyed(CUR) : DYE != -1;
        return dyed ? new DustParticleOptions(0x9B4CFF, 0.6F) : ParticleTypes.REVERSE_PORTAL;
    }
    /** 26.2 DustParticleOptions has getColor() (Vector3f 0..1) and getScale(), not record accessors. */
    static int rgbOf(DustParticleOptions d) {
        var c = d.getColor();
        return Math.round(Math.min(1f, Math.max(0f, c.x())) * 255) << 16 | Math.round(Math.min(1f, Math.max(0f, c.y())) * 255) << 8 | Math.round(Math.min(1f, Math.max(0f, c.z())) * 255);
    }
    static boolean anyPieceDyed(Player p) {
        for (EquipmentSlot sl : new EquipmentSlot[]{EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD})
            if (pieceRgb(p, sl) >= 0) return true;
        return false;
    }
    static int lerpRgb(int c0, int c1, float t) {
        int r = (int) ((c0 >> 16 & 255) * (1f - t) + (c1 >> 16 & 255) * t);
        int g = (int) ((c0 >> 8 & 255) * (1f - t) + (c1 >> 8 & 255) * t);
        int b = (int) ((c0 & 255) * (1f - t) + (c1 & 255) * t);
        return r << 16 | g << 8 | b;
    }
    static int applyDye(int rgb, int dye, float mix) {
        if (dye < 0 || mix <= 0f) return rgb;
        int v = Math.max(rgb >> 16 & 255, Math.max(rgb >> 8 & 255, rgb & 255));
        int dyed = ((dye >> 16 & 255) * v / 255) << 16 | ((dye >> 8 & 255) * v / 255) << 8 | ((dye & 255) * v / 255);
        return mix >= 1f ? dyed : lerpRgb(rgb, dyed, mix);
    }
    static int pieceRgb(Player p, EquipmentSlot slot) {
        boolean fp = Minecraft.getInstance().player == p && Minecraft.getInstance().options.getCameraType().isFirstPerson();
        if (!InfiniumClientConfig.show("dye_aura", fp)) return -1;
        int a = GlowColor.argb(p.getItemBySlot(slot));
        return a == -1 ? -1 : a & 0xFFFFFF;
    }
    /** Dye of the piece at this height (boots / leggings / chestplate / helmet), 0.25-block (4 px) blend centred on each seam.
     *  Undyed piece = palette colour unchanged. Seams match the 13.10 bands (0.30 / 0.75 / 1.45). */
    static int dyeAt(Player p, double worldY, int rgb) {
        boolean fp = Minecraft.getInstance().player == p && Minecraft.getInstance().options.getCameraType().isFirstPerson();
        if (!InfiniumClientConfig.show("dye_aura", fp)) return rgb;
        double hy = (worldY - p.getY()) * 1.8 / Math.max(0.1, p.getBbHeight());
        EquipmentSlot[] slots = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};
        double[] seam = {0.30, 0.75, 1.45};
        int band = hy < seam[0] ? 0 : hy < seam[1] ? 1 : hy < seam[2] ? 2 : 3;
        for (int s = 0; s < seam.length; s++) {
            double d = hy - seam[s];
            if (d >= -0.125 && d <= 0.125) {
                float t = (float) ((d + 0.125) / 0.25);
                int lo = pieceRgb(p, slots[s]), hi = pieceRgb(p, slots[s + 1]);
                if (lo < 0 && hi < 0) return rgb;
                if (lo < 0) return applyDye(rgb, hi, t);
                if (hi < 0) return applyDye(rgb, lo, 1f - t);
                return applyDye(rgb, lerpRgb(lo, hi, t), 1f);
            }
        }
        return applyDye(rgb, pieceRgb(p, slots[band]), 1f);
    }
    static final int[] SING_NEB = {0x320960, 0x440D83, 0x621CAC, 0x7526C8, 0x852FE3, 0x9B41F0, 0xA84EF4};   // +9% brightness (user)   // deep -> mid -> brighter violet
    static final int SING_PINK = 0xB73F9B;                                                              // muted pink, brightest only
    static final int[] CYB = {0xB048F0, 0xC854FF, 0xC850E8, 0xDC54D0};   // slightly brighter (user)
    static int sing(RandomSource r) {
        float k = r.nextFloat();
        return k < 0.2F ? SING_BLACK : k < 0.26F ? SING_PINK : SING_NEB[r.nextInt(SING_NEB.length)];
    }
    /** CyberPsycho-style glitch event (streak or flicker), mirrored L/R; y range limited by the caller. */
    static void glitch(ClientLevel level, RandomSource r, Player p, int col, double rad, double y0, boolean streak, float size,
                       double rx, double rz, double fx, double fz) {
        var dust = dust(col, size);
        double a = r.nextDouble() * Math.PI * 2, rr = rad + (r.nextDouble() - 0.4) * 0.2, lx = Math.cos(a) * rr, lz = Math.sin(a) * rr;
        int n = streak ? 4 + r.nextInt(3) : 1 + r.nextInt(2);
        double dx = (r.nextDouble() - 0.5) * 0.12, dy = (r.nextDouble() - 0.5) * 0.12, dz = (r.nextDouble() - 0.5) * 0.12;
        for (int i = 0; i < n; i++) {
            double zig = (i % 2 == 0 ? 1 : -1) * 0.05;
            double ox = lx + dx * i + zig * Math.sin(a), oy = y0 + dy * i + (n > 2 ? zig : 0), oz = lz + dz * i - zig * Math.cos(a);
            if (faceBox(p, ox, oy, oz)) continue;
            for (int s2 = -1; s2 <= 1; s2 += 2)
                emit(level, dust, p.getX() + rx * ox * s2 + fx * oz, oy, p.getZ() + rz * ox * s2 + fz * oz, 0, 0, 0);
        }
    }

    /** Emission multiplier for the current armor() pass (R8 13.4: Infinity = Singularity x0.8 + Cyber Psycho x1.2). */
    static float MULT = 1F;
    static Player CUR;
    static dev.justin.infinium.SetLook CUR_LOOK;
    static boolean chance(RandomSource r, float p) { return r.nextFloat() < p * MULT * InfiniumClientConfig.particleDensityMultiplier(); }
    /** Armor particle sink: honours the per-piece toggles by body band (13.10: boots < 0.30, legs < 0.75, chest < 1.45, helmet above). */
    /** R4: spawn with EXACTLY the given velocity. level.addParticle lets vanilla add a random +-0.4 jitter (x0.1 for dust),
     *  which smeared the streaks / orbits / rings; the preview renders (python ports of this class) use the exact motion. */
    static void exact(ClientLevel level, ParticleOptions o, double x, double y, double z, double vx, double vy, double vz) {
        exact(level, o, x, y, z, vx, vy, vz, -1);
    }
    static void exact(ClientLevel level, ParticleOptions o, double x, double y, double z, double vx, double vy, double vz, int lifetime) {
        if (o instanceof DustParticleOptions d && CUR != null && !TOOL_TINT)
            o = new DustParticleOptions(dyeAt(CUR, y, rgbOf(d)), d.getScale());
        Minecraft mc = Minecraft.getInstance();
        var st = mc.options.particles().get();
        if (st == net.minecraft.server.level.ParticleStatus.MINIMAL) return;
        if (st == net.minecraft.server.level.ParticleStatus.DECREASED && level.getRandom().nextInt(3) == 0) return;
        Vec3 cam = mc.gameRenderer.mainCamera().position();
        if (cam.distanceToSqr(x, y, z) > 32 * 32) return;
        net.minecraft.client.particle.Particle pt = mc.particleEngine.createParticle(o, x, y, z, vx, vy, vz);
        if (pt != null) {
            pt.setParticleSpeed(vx, vy, vz);
            if (lifetime > 0) pt.setLifetime(lifetime);
        }
    }
    static void emit(ClientLevel level, ParticleOptions o, double x, double y, double z, double vx, double vy, double vz) {
        if (CUR != null) {
            double hy = (y - CUR.getY()) * 1.8 / Math.max(0.1, CUR.getBbHeight());   // 13.10 bands, scaled by height
            int band = hy < 0.30 ? 3 : hy < 0.75 ? 2 : hy < 1.45 ? 1 : 0;           // 0 helmet .. 3 boots
            if (CUR_LOOK != null && !InfiniumClientConfig.setArmorParticles(CUR_LOOK, false)) return;   // R2: one armor toggle per full set
        }
        exact(level, o, x, y, z, vx, vy, vz);
    }

    /** Linear armor particle scaling across the entire armor spectrum (0.0 = unupgraded Tier 1 .. 1.0 = max Infinity). */
    static float armorFactor(float prog) {
        float p = Math.max(0F, Math.min(1F, prog));
        return 0.65F + 1.85F * p;
    }

    /** Linear tool particle scaling across the entire armor spectrum. */
    static float toolFactor(float prog) {
        float p = Math.max(0F, Math.min(1F, prog));
        return 0.65F + 0.85F * p;
    }

    public static void tick(Minecraft mc, ClientLevel level) {
        RandomSource r = level.getRandom();
        DYE = -1; CUR = null; MULT = 1F;
        if (InfiniumClientConfig.show("fragments", false)) itemFx(mc, level, r);
        for (Player p : level.players()) {
            if (p.isInvisible()) continue;
            if (mc.player != null && p.distanceToSqr(mc.player) > 48 * 48) continue;
            boolean fp = p == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
            boolean selfFp = fp && p == mc.player;
            dev.justin.infinium.SetLook fullLook = dev.justin.infinium.SetLook.of(p);
            switchBurst(mc, level, r, p, fullLook, selfFp);
            float prog = dev.justin.infinium.CrystalCharge.visualProgress(p);
            if (fullLook != null) {
                InfiniumStyle cst = fullLook == dev.justin.infinium.SetLook.INFINITY ? InfiniumStyle.SINGULARITY : InfiniumStyle.values()[fullLook.ordinal()];
                boolean armorOn = InfiniumClientConfig.show("armor." + fullLook.id, selfFp);
                float armorMul = armorFactor(prog);
                CUR = p;   // armor-side effects below are tinted per piece by height (exact -> dyeAt)
                if (armorOn && !selfFp) wingFx(level, r, p, cst, armorMul);
                if (armorOn) flightTrailFx(level, r, p, fullLook, armorMul, selfFp);
                if (armorOn && !selfFp && (fullLook == dev.justin.infinium.SetLook.ASTRAL || fullLook == dev.justin.infinium.SetLook.SINGULARITY)
                        && InfiniumClientConfig.show("dye_aura", selfFp)) dyeAura(mc, level, r, p, selfFp);
                CUR = null;
                if (armorOn) {
                    CUR = p; CUR_LOOK = fullLook;
                    if (selfFp) {
                        armorFirstPerson(mc, level, r, p, fullLook, armorMul);
                    } else {
                        if (fullLook == dev.justin.infinium.SetLook.INFINITY) {
                            MULT = 0.8F * armorMul; armor(level, r, p, InfiniumStyle.SINGULARITY);
                            MULT = 1.2F * armorMul; armor(level, r, p, InfiniumStyle.CYBERPSYCHO);
                        } else {
                            MULT = armorMul;
                            armor(level, r, p, InfiniumStyle.values()[fullLook.ordinal()]);
                        }
                    }
                    MULT = 1F; CUR = null; CUR_LOOK = null;
                }
            }
            if (fullLook == null) {
                if (p.isFallFlying()) {
                    ItemStack chest = p.getItemBySlot(EquipmentSlot.CHEST);
                    if (chest != null && Infinium.isChestplate(chest) && chest.has(net.minecraft.core.component.DataComponents.GLIDER)) {
                        dev.justin.infinium.SetLook fallbackLook = Infinium.isInfinity(chest) ? dev.justin.infinium.SetLook.INFINITY
                            : (InfiniumStyle.get(chest) != null ? dev.justin.infinium.SetLook.values()[InfiniumStyle.get(chest).ordinal()] : dev.justin.infinium.SetLook.ASTRAL);
                        if (InfiniumClientConfig.show("armor." + fallbackLook.id, selfFp)) {
                            flightTrailFx(level, r, p, fallbackLook, 1.0F, selfFp);
                        }
                    }
                }
                continue;
            }
            dev.justin.infinium.SetLook toolLook = fullLook;
            if (!InfiniumClientConfig.show("weapon." + toolLook.id, selfFp)) continue;
            float toolMul = toolFactor(prog);
            for (ItemStack held : new ItemStack[]{p.getMainHandItem(), p.getOffhandItem()})
            if ((Infinium.isTool(held) || held.is(Infinium.NETHERITE_ROCKET)) && !dev.justin.infinium.InfiniumDurability.disabled(held)) {
                boolean prev = TOOL_TINT; int prevDye = DYE;
                TOOL_TINT = true;   // worn armor's particle style, coloured independently by the TOOL's own dye
                int td = GlowColor.argb(held);
                DYE = td == -1 ? -1 : td & 0xFFFFFF;
                boolean offhand = held == p.getOffhandItem();
                boolean isPick = held.is(Infinium.PICKAXE);
                if (toolLook == dev.justin.infinium.SetLook.INFINITY) {
                    MULT = 0.8F * toolMul; tool(mc, level, r, p, InfiniumStyle.SINGULARITY, fp, offhand, isPick);
                    MULT = 1.2F * toolMul; tool(mc, level, r, p, InfiniumStyle.CYBERPSYCHO, fp, offhand, isPick);
                    MULT = 1F;
                } else {
                    MULT = toolMul;
                    tool(mc, level, r, p, InfiniumStyle.values()[toolLook.ordinal()], fp, offhand, isPick);
                    MULT = 1F;
                }
                TOOL_TINT = prev; DYE = prevDye;
            }
        }
    }

    // ---- set-switch burst (13.10 set_switch_burst; R3 SEEN set: no burst the first time a player is seen / on join) ----
    private static final java.util.Map<Integer, dev.justin.infinium.SetLook> LAST_LOOK = new java.util.HashMap<>();
    private static final java.util.Map<Integer, int[]> BURST = new java.util.HashMap<>();   // id -> {ticks left, look ordinal}
    static void switchBurst(Minecraft mc, ClientLevel level, RandomSource r, Player p, dev.justin.infinium.SetLook look, boolean fp) {
        int id = p.getId();
        if (LAST_LOOK.size() > 512) { LAST_LOOK.clear(); BURST.clear(); }
        boolean seen = LAST_LOOK.containsKey(id);
        dev.justin.infinium.SetLook prev = LAST_LOOK.put(id, look);
        if (seen && look != null && look != prev) BURST.put(id, new int[]{10, look.ordinal()});   // 0.5 s
        int[] b = BURST.get(id);
        if (b == null) return;
        if (--b[0] <= 0) BURST.remove(id);
        if (!InfiniumClientConfig.show("set_switch", fp)) return;
        Vec3 c = toolPos(mc, p, fp);
        boolean inf = b[1] == dev.justin.infinium.SetLook.INFINITY.ordinal();
        for (int i = 0; i < 6; i++) {
            InfiniumStyle st = inf ? (i % 2 == 0 ? InfiniumStyle.SINGULARITY : InfiniumStyle.CYBERPSYCHO) : InfiniumStyle.values()[b[1]];   // Infinity: half of each
            double a = r.nextDouble() * Math.PI * 2, e = (r.nextDouble() - 0.5) * Math.PI, sp = 0.06 + r.nextDouble() * 0.05;
            double vx = Math.cos(a) * Math.cos(e) * sp, vy = Math.sin(e) * sp, vz = Math.sin(a) * Math.cos(e) * sp;
            exact(level, styleDust(r, st, fp ? 0.35F : 0.55F), c.x, c.y, c.z, vx, vy, vz, fp ? (3 + r.nextInt(2)) : -1);
        }
    }

    /** 12.9 (b): dye aura while the set light is on and any piece is dyed; coloured per piece at its height via dyeAt. */
    static void dyeAura(Minecraft mc, ClientLevel level, RandomSource r, Player p, boolean fp) {
        if (fp) return; // In first person, dye aura is integrated into armorFirstPerson
        if (!InfiniumClientConfig.show("dye_aura", fp)) return;
        if (!anyPieceDyed(p) || dev.justin.infinium.SetBonuses.light(p) <= 0) return;
        var ps = mc.options.particles().get();
        int every = ps == net.minecraft.server.level.ParticleStatus.MINIMAL ? 0 : ps == net.minecraft.server.level.ParticleStatus.DECREASED ? 6 : 3;
        if (every == 0 || (p.tickCount + p.getId()) % every != 0) return;
        double th = r.nextDouble() * Math.PI * 2, ph = Math.acos(2 * r.nextDouble() - 1), rad = 0.45;
        exact(level, new DustParticleOptions(0xC45AD4, 0.6F), p.getX() + Math.sin(ph) * Math.cos(th) * rad,
            p.getY() + p.getBbHeight() * 0.55 + Math.cos(ph) * rad, p.getZ() + Math.sin(ph) * Math.sin(th) * rad, 0, 0.01, 0);
    }

    /** First-person ambient armor particles: renders soft viewmodel/peripheral aura locked to camera. */
    static void armorFirstPerson(Minecraft mc, ClientLevel level, RandomSource r, Player p, dev.justin.infinium.SetLook look, float mult) {
        float dens = InfiniumClientConfig.particleDensityMultiplier();
        if (dens <= 0.01F) return;
        int tc = p.tickCount + p.getId();
        double yaw = Math.toRadians(p.getYRot()), pitch = Math.toRadians(p.getXRot());
        Vec3 fwd = new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
        Vec3 right = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
        Vec3 up = fwd.cross(right).normalize();
        Vec3 eye = p.getEyePosition();
        Vec3 vel = p.getDeltaMovement();

        switch (look) {
            case SINGULARITY -> {
                if (tc % 2 != 0) return;
                int count = (r.nextFloat() < 0.8F * mult * dens) ? 2 : 1;
                for (int i = 0; i < count; i++) {
                    double side = r.nextBoolean() ? 1.0 : -1.0;
                    double rx = side * (0.28 + r.nextDouble() * 0.22);
                    double ry = -0.32 + r.nextDouble() * 0.35;
                    double rz = 0.45 + r.nextDouble() * 0.25;
                    Vec3 pos = eye.add(fwd.scale(rz)).add(right.scale(rx)).add(up.scale(ry));
                    exact(level, dust(sing(r), 0.55F), pos.x, pos.y, pos.z, vel.x, vel.y + 0.005, vel.z, 3 + r.nextInt(3));
                }
            }
            case CYBERNETIC -> {
                if (tc % 3 != 0) return;
                if (r.nextFloat() > 0.70F * mult * dens) return;
                double side = r.nextBoolean() ? 1.0 : -1.0;
                double rx = side * (0.30 + r.nextDouble() * 0.20);
                double ry = -0.30 + r.nextDouble() * 0.30;
                double rz = 0.45 + r.nextDouble() * 0.20;
                var d = dust(CYB[r.nextInt(CYB.length)], 0.50F);
                Vec3 pos = eye.add(fwd.scale(rz)).add(right.scale(rx)).add(up.scale(ry));
                exact(level, d, pos.x, pos.y, pos.z, vel.x, vel.y, vel.z, 2 + r.nextInt(2));
            }
            case CYBERPSYCHO -> {
                if (tc % 2 != 0) return;
                if (r.nextFloat() > 0.80F * mult * dens) return;
                int dye = GlowColor.argb(p.getItemBySlot(EquipmentSlot.CHEST));
                int col = psycho(r, dye);
                double side = r.nextBoolean() ? 1.0 : -1.0;
                double rx = side * (0.28 + r.nextDouble() * 0.24);
                double ry = -0.32 + r.nextDouble() * 0.38;
                double rz = 0.45 + r.nextDouble() * 0.25;
                Vec3 pos = eye.add(fwd.scale(rz)).add(right.scale(rx)).add(up.scale(ry));
                exact(level, dust(col, 0.45F), pos.x, pos.y, pos.z, vel.x, vel.y, vel.z, 2 + r.nextInt(2));
            }
            case INFINITY -> {
                if (tc % 2 == 0) {
                    double side = r.nextBoolean() ? 1.0 : -1.0;
                    double rx = side * (0.28 + r.nextDouble() * 0.22);
                    double ry = -0.32 + r.nextDouble() * 0.35;
                    double rz = 0.45 + r.nextDouble() * 0.25;
                    Vec3 pos = eye.add(fwd.scale(rz)).add(right.scale(rx)).add(up.scale(ry));
                    exact(level, dust(sing(r), 0.55F), pos.x, pos.y, pos.z, vel.x, vel.y + 0.005, vel.z, 3 + r.nextInt(3));
                } else if (r.nextFloat() < 0.65F * mult * dens) {
                    double side = r.nextBoolean() ? 1.0 : -1.0;
                    double rx = side * (0.30 + r.nextDouble() * 0.20);
                    double ry = -0.30 + r.nextDouble() * 0.30;
                    double rz = 0.45 + r.nextDouble() * 0.20;
                    Vec3 pos = eye.add(fwd.scale(rz)).add(right.scale(rx)).add(up.scale(ry));
                    exact(level, dust(psycho(r, -1), 0.45F), pos.x, pos.y, pos.z, vel.x, vel.y, vel.z, 2 + r.nextInt(2));
                }
            }
            default -> { // ASTRAL
                if (tc % 3 != 0) return;
                double side = r.nextBoolean() ? 1.0 : -1.0;
                double rx = side * (0.28 + r.nextDouble() * 0.22);
                double ry = -0.35 + r.nextDouble() * 0.30;
                double rz = 0.45 + r.nextDouble() * 0.25;
                int col = r.nextBoolean() ? 0x9B4CFF : 0xBF70FF;
                Vec3 pos = eye.add(fwd.scale(rz)).add(right.scale(rx)).add(up.scale(ry));
                exact(level, dust(col, 0.45F), pos.x, pos.y, pos.z, vel.x, vel.y + 0.008, vel.z, 3 + r.nextInt(3));
            }
        }
    }

    /** Reference look (preview/particles/ambient.gif = "closest so far"), at ~1.75x its emission rate. */
    static final float RATE = 1.75F;
    static void armor(ClientLevel level, RandomSource r, Player p, InfiniumStyle st) {
        int tc = p.tickCount + p.getId();
        double rad = p.getBbWidth() * 0.5 + 0.25, h = p.getBbHeight();
        double by = Math.toRadians(p.yBodyRot), fx = -Math.sin(by), fz = Math.cos(by), rx = -fz, rz = fx;
        switch (st) {
            case SINGULARITY -> {
                // +50% singularity/astral particles applied evenly across the entire spectrum
                float budget = 1.5F * (1.2F * 1.3F * 3.12F / 3F) * MULT;
                for (int pn = 0, cap = Math.max(2, (int) Math.ceil(3.0F * MULT)); pn < cap && r.nextFloat() < budget; pn++, budget -= 1F) {
                int band = r.nextInt(3); double[] hy = {0.30, 0.81, 1.30}; double[] sp = {0.035, -0.045, 0.04};
                double a = tc * Math.abs(sp[band]) + band * (2 * Math.PI / 3) + (r.nextDouble() - 0.5) * 0.6;
                double rr = rad + 0.15 + r.nextDouble() * 0.25, y = p.getY() + hy[band] + (r.nextDouble() - 0.5) * 0.15;
                double vs = Math.signum(sp[band]) * 0.04;
                ParticleOptions o = dust(sing(r), 0.7F);   // nebula violets, 20% black, 6% muted pink (brightest)
                emit(level, o, p.getX() + Math.cos(a) * rr, y, p.getZ() + Math.sin(a) * rr, -Math.sin(a) * vs, 0, Math.cos(a) * vs);
                }
            }
            case CYBERNETIC -> {
                // lessened CyberPsycho glitch: ~1 event / 1.3 s, 30% streaks, ONLY legs/feet + chest band (nothing near the head)
                if (chance(r, 1.7F / 20F)) {                         // ~2.3x (user): 1.7 events/s
                    double y0 = p.getY() + (r.nextBoolean() ? 0.08 + r.nextDouble() * 0.6 : 0.8 + r.nextDouble() * 0.45);   // legs/feet | chest
                    glitch(level, r, p, CYB[r.nextInt(CYB.length)], rad, y0, r.nextFloat() < 0.4F, 0.45F, rx, rz, fx, fz);
                }
                // (no reverse-portal specks: Cybernetic = glitch only, user)
            }
            case CYBERPSYCHO -> {
                // small short spark specks on the armor surface, mirrored L/R (reference: 1 event per 4 ticks, 1-2 or 4-6 points)
                // feedback: a couple of small glitchy line streaks every second or so, a bit more visible, chaotic, mirrored
                // 1.4x the 1.75x pass (user: +35-50%). R5b: probability above 1 (MULT x1.5 / Infinity) becomes extra passes
                float pr = 2.45F / 4F * MULT;
                int passes = (int) pr + (r.nextFloat() < pr - (int) pr ? 1 : 0);
                for (int pass = 0; pass < passes; pass++) {
                    int dye = GlowColor.argb(p.getItemBySlot(EquipmentSlot.CHEST));
                    int events = r.nextFloat() < 0.2F ? 2 : 1;
                    boolean streak = r.nextFloat() < 0.45F;
                    for (int e = 0; e < events; e++) {
                        var dust = dust(psycho(r, dye), streak ? 0.5F : 0.35F);
                        double a = r.nextDouble() * Math.PI * 2, y0 = p.getY() + 0.1 + r.nextDouble() * h * 0.85;
                        double rr = rad + (r.nextDouble() - 0.4) * 0.2, lx = Math.cos(a) * rr, lz = Math.sin(a) * rr;
                        int n = streak ? 5 + r.nextInt(3) : 1 + r.nextInt(2);
                        double dx = (r.nextDouble() - 0.5) * 0.12, dy = (r.nextDouble() - 0.5) * 0.12, dz = (r.nextDouble() - 0.5) * 0.12;
                        for (int i = 0; i < n; i++) {
                            double zig = (i % 2 == 0 ? 1 : -1) * 0.05;
                            double ox = lx + dx * i + zig * Math.sin(a), oy = y0 + dy * i + (n > 2 ? zig : 0), oz = lz + dz * i - zig * Math.cos(a);
                            if (faceBox(p, ox, oy, oz)) continue;   // never directly in front of the face (user)
                            for (int s2 = -1; s2 <= 1; s2 += 2)
                                emit(level, dust, p.getX() + rx * ox * s2 + fx * oz, oy, p.getZ() + rz * ox * s2 + fz * oz, 0, 0, 0);
                        }
                    }
                }
            }
            default -> {
                // +50% astral particles applied evenly across the entire spectrum
                float pr = (RATE / 8F) * 1.5F * MULT;
                int passes = (int) pr + (r.nextFloat() < pr - (int) pr ? 1 : 0);
                for (int pass = 0; pass < passes; pass++) {
                    double a = r.nextDouble() * Math.PI * 2;
                    double y = p.getY() + 0.1 + r.nextDouble() * h * 0.55;
                    emit(level, portal(), p.getX() + Math.cos(a) * rad, y, p.getZ() + Math.sin(a) * rad, 0, 0.01, 0);
                }
            }
        }
    }

    static Vec3 toolPos(Minecraft mc, Player p, boolean fp) {
        return toolPos(mc, p, fp, false);
    }

    static Vec3 toolPos(Minecraft mc, Player p, boolean fp, boolean offhand) {
        double yaw = Math.toRadians(p.getYRot()), pitch = Math.toRadians(p.getXRot());
        Vec3 look = new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
        Vec3 right = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
        Vec3 up = look.cross(right).normalize();
        boolean rightHand = (p.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT) != offhand;
        if (fp) {
            // First person: positioned right at the held tool in the lower right of the screen (or left for offhand)
            // (cos,0,sin) is the player's LEFT in MC (yaw 0 faces +Z, right hand is -X), so screen-right is a NEGATIVE scale
            return p.getEyePosition().add(look.scale(0.42)).add(right.scale(rightHand ? -0.24 : 0.24)).add(up.scale(-0.16));
        }
        double by = Math.toRadians(p.yBodyRot);
        Vec3 bf = new Vec3(-Math.sin(by), 0, Math.cos(by)), br = new Vec3(-bf.z, 0, bf.x);
        // br = (-cos,0,-sin) is the body's RIGHT. Old code used -0.22 for the main hand (= left side) at y 0.62 (= knee), which is the
        // "clumped on the left kneecap" bug. Now: the actual hand side (respects left-handed players), at hand/tool height.
        return p.position().add(bf.scale(0.30)).add(br.scale(rightHand ? 0.36 : -0.36)).add(0, p.getBbHeight() * 0.5, 0);
    }

    static void tool(Minecraft mc, ClientLevel level, RandomSource r, Player p, InfiniumStyle st, boolean fp) {
        tool(mc, level, r, p, st, fp, false, p.getMainHandItem().is(Infinium.PICKAXE));
    }

    static void tool(Minecraft mc, ClientLevel level, RandomSource r, Player p, InfiniumStyle st, boolean fp, boolean offhand, boolean isPick) {
        int tc = p.tickCount + p.getId();
        Vec3 c = toolPos(mc, p, fp, offhand);
        double s = fp ? 0.35 : 0.6;                                  // tighter around the tool
        double yaw = Math.toRadians(p.getYRot()), pitch = Math.toRadians(p.getXRot());
        Vec3 look = new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
        Vec3 right = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
        Vec3 up = look.cross(right).normalize();
        Vec3 vel = fp ? p.getDeltaMovement() : Vec3.ZERO;
        int life = fp ? (2 + r.nextInt(2)) : -1;

        switch (st) {
            case SINGULARITY -> {
                if (tc % 4 != 0) return;
                if (MULT < 1F && r.nextFloat() >= MULT) return;
                double a = tc * 0.25;
                float want = 2.4F * 1.5F * Math.max(0.75F, MULT);   // +50% singularity tool specks
                int nk = (int) want + (r.nextFloat() < (want - (int) want) ? 1 : 0);
                for (int k = 0; k < nk; k++) {
                    double aa = a + k * (2 * Math.PI / Math.max(1, nk));
                    double rx = Math.cos(aa) * 0.16 * s;
                    double ry = Math.sin(aa) * 0.16 * s;
                    Vec3 pos = fp ? c.add(right.scale(rx)).add(up.scale(ry)) : c.add(rx, ry * 0.3, Math.sin(aa) * 0.16 * s);
                    exact(level, dust(sing(r), 0.6F), pos.x, pos.y, pos.z, vel.x, vel.y, vel.z, life);
                }
            }
            case CYBERPSYCHO -> {
                int interval = isPick ? 2 : 4;
                if ((MULT > 1F ? tc + 2 : tc) % interval != 0) return;             // Infinity: offset 2 ticks from Singularity
                int bursts = (MULT > 1F || isPick) ? 2 : 1;
                for (int b = 0; b < bursts; b++) {
                    int n = (isPick ? 3 : 2) + r.nextInt(3);
                    var d = dust(psycho(r, -1), isPick ? 0.65F : 0.55F);
                    for (int i = 0; i < n; i++) {
                        double ox = (r.nextDouble() - 0.5) * 0.28 * s, oy = (r.nextDouble() - 0.5) * 0.28 * s, oz = (r.nextDouble() - 0.5) * 0.28 * s;
                        Vec3 pos = fp ? c.add(right.scale(ox)).add(up.scale(oy)).add(look.scale(oz)) : c.add(ox, oy, oz);
                        exact(level, d, pos.x, pos.y, pos.z, vel.x, vel.y, vel.z, life);
                    }
                }
            }
            case CYBERNETIC -> {
                if (tc % 6 != 0) return;
                double ox = (r.nextDouble() - 0.5) * 0.25 * s, oy = (r.nextDouble() - 0.5) * 0.25 * s, oz = (r.nextDouble() - 0.5) * 0.25 * s;
                var d = dust(CYB[r.nextInt(CYB.length)], 0.6F); int n = 2 + r.nextInt(2);
                for (int i = 0; i < n; i++) {
                    Vec3 pos = fp ? c.add(right.scale(ox)).add(up.scale(oy)).add(look.scale(oz)) : c.add(ox, oy, oz);
                    exact(level, d, pos.x, pos.y, pos.z, vel.x, vel.y, vel.z, life);
                }
            }
            default -> {
                if (tc % 4 != 0) return;
                ParticleOptions o = dust(DYE != -1 ? dyeMap(0x9B4CFF) : (r.nextBoolean() ? 0x9B4CFF : 0xBF70FF), 0.55F);
                double ox = (r.nextDouble() - 0.5) * 0.25 * s, oy = (r.nextDouble() - 0.5) * 0.25 * s, oz = (r.nextDouble() - 0.5) * 0.25 * s;
                Vec3 pos = fp ? c.add(right.scale(ox)).add(up.scale(oy)).add(look.scale(oz)) : c.add(ox, oy, oz);
                exact(level, o, pos.x, pos.y, pos.z, vel.x, vel.y + (fp ? 0 : 0.005), vel.z, life);
            }
        }
    }
    // ---- wing absorb / emerge particles (0.7 s after a glider toggle), style coloured, pulled into / out of the back plate ----
    private static final java.util.Map<Integer, long[]> WING = new java.util.HashMap<>();   // id -> {glider 0/1, toggleTimeMs}
    static ParticleOptions styleDust(RandomSource r, InfiniumStyle st, float scale) {
        return switch (st) {
            case SINGULARITY -> dust(sing(r), scale);
            case CYBERPSYCHO -> dust(psycho(r, -1), scale);
            case CYBERNETIC -> dust(r.nextBoolean() ? 0xC04CFF : 0xE04CC8, scale);
            default -> dust(r.nextBoolean() ? 0x8A3CE0 : 0xB070FF, scale);   // Astral (Singularity-family purples)
        };
    }
    static void wingFx(ClientLevel level, RandomSource r, Player p, InfiniumStyle st, float mult) {
        if (st == InfiniumStyle.ASTRAL || st == InfiniumStyle.SINGULARITY) return; // Astral & Singularity have no physical wings
        ItemStack chest = p.getItemBySlot(EquipmentSlot.CHEST);
        long on = chest.has(net.minecraft.core.component.DataComponents.GLIDER) ? 1 : 0, now = System.currentTimeMillis();
        if (WING.size() > 512) WING.clear();
        long[] w = WING.get(p.getId());
        if (w == null) { WING.put(p.getId(), new long[]{on, 0}); return; }
        if (w[0] != on) { w[0] = on; w[1] = now; }
        float e = (now - w[1]) / WingFold.DURATION_MS;
        if (e >= 1F) return;
        float k = on == 1 ? e : 1F - e;                               // current wing size
        double by = Math.toRadians(p.yBodyRot), fx = -Math.sin(by), fz = Math.cos(by), rx = -fz, rz = fx;
        double cx = p.getX() - fx * 0.32, cy = p.getY() + 1.15, cz = p.getZ() - fz * 0.32;   // chestplate back center
        for (int i = 0; i < Math.max(1, Math.round(8 * mult)); i++) {                                 // 8 / tick, times the R5 armor factor
            double side = r.nextBoolean() ? 1 : -1, span = (0.15 + r.nextDouble() * 1.0) * Math.max(0.15, k);
            double down = (0.1 + r.nextDouble() * 1.0) * Math.max(0.15, k);
            double px = cx + rx * side * span - fx * (0.15 + 0.25 * k), py = cy - down, pz = cz + rz * side * span - fz * (0.15 + 0.25 * k);
            double vx = (cx - px) * 0.12, vy = (cy - py) * 0.12, vz = (cz - pz) * 0.12;
            if (on == 1) { vx = -vx; vy = -vy; vz = -vz; px = cx + (px - cx) * 0.3; py = cy + (py - cy) * 0.3; pz = cz + (pz - cz) * 0.3; }  // emerge: outward
            exact(level, styleDust(r, st, 0.6F), px, py, pz, vx, vy, vz);
        }
    }

    /** batch1 flight particle tunables. Astral/Singularity magic-flight density factor (toned down from 1.0). */
    static final float FLIGHT_MAGIC_DENSITY = 0.5F;
    /** Astral/Singularity flight particles closer than this to the camera (third person) are skipped. */
    static final double CAMERA_CLEAR_RADIUS = 1.5;
    /** Cybernetic / Cyber Psycho wingtip trail lifetimes in ticks (were 9 idle / 14 thrust). */
    static final int CYBER_LIFE = 5, CYBER_LIFE_THRUST = 8;

    static boolean camClear(Minecraft mc, double x, double y, double z) {
        var cam = mc.gameRenderer.mainCamera();
        if (cam == null) return true;
        Vec3 c = cam.position();
        double dx = x - c.x, dy = y - c.y, dz = z - c.z;
        return dx * dx + dy * dy + dz * dz > CAMERA_CLEAR_RADIUS * CAMERA_CLEAR_RADIUS;
    }

    static void flightTrailFx(ClientLevel level, RandomSource r, Player p, dev.justin.infinium.SetLook look, float mult) {
        flightTrailFx(level, r, p, look, mult, false);
    }

    static void flightTrailFx(ClientLevel level, RandomSource r, Player p, dev.justin.infinium.SetLook look, float mult, boolean fp) {
        if (!p.isFallFlying()) return;
        ItemStack chest = p.getItemBySlot(EquipmentSlot.CHEST);
        if (chest == null || !Infinium.isChestplate(chest) || !chest.has(net.minecraft.core.component.DataComponents.GLIDER)) return;

        Minecraft mc = Minecraft.getInstance();
        boolean isLocal = (p == mc.player);
        boolean thrustHeld = isLocal ? InfiniumKeys.isThrustHeld() : InfinityFlightClient.isRemoteThrusting(p);   // M6: synced, not sneak pose

        float dens = InfiniumClientConfig.particleDensityMultiplier();
        if (dens <= 0.01F) return;

        double yaw = Math.toRadians(p.getYRot());
        double pitch = Math.toRadians(p.getXRot());
        Vec3 fwd = new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
        Vec3 right = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
        Vec3 up = fwd.cross(right).normalize().scale(-1);

        Vec3 center = p.position().add(0, p.getBbHeight() * 0.45, 0);
        Vec3 vel = p.getDeltaMovement();
        float prog = dev.justin.infinium.CrystalCharge.visualProgress(p);

        // 1. ASTRAL & SINGULARITY: Pure particle wings (no physical wings).
        // Heavy swirling particles circle the player.
        // While thrust is held: more particles emit and are pushed downward (telekinetic thrust) with a trail behind.
        if (look == dev.justin.infinium.SetLook.ASTRAL || look == dev.justin.infinium.SetLook.SINGULARITY) {
            boolean isAstral = (look == dev.justin.infinium.SetLook.ASTRAL);
            // batch1: no Astral/Singularity flight particles in first person for the camera player
            if (p == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson()) return;
            // Particle amount and density scale linearly with progression (Astral has fewer than Singularity)
            float baseDensity = (isAstral ? (0.85F + 0.65F * prog) : (1.50F + 1.20F * prog)) * FLIGHT_MAGIC_DENSITY;   // batch1: toned down
            float countMul = baseDensity * mult * dens * (thrustHeld ? 2.2F : 1.0F);

            // A. Swirling particles circling the flying player
            int swirlCount = Math.max(2, Math.round((isAstral ? 3 : 5) * countMul));
            for (int i = 0; i < swirlCount; i++) {
                double angle = (p.tickCount * 0.40) + (i * (Math.PI * 2.0 / swirlCount)) + (r.nextDouble() - 0.5) * 0.35;
                double rad = (isAstral ? 0.65 : 0.85) + r.nextDouble() * 0.45;
                double along = (r.nextDouble() - 0.5) * 1.2;
                Vec3 pt = center.add(right.scale(Math.cos(angle) * rad)).add(up.scale(Math.sin(angle) * rad)).add(fwd.scale(along));
                double orbitSpeed = 0.14 + r.nextDouble() * 0.08;
                Vec3 orbitVel = right.scale(-Math.sin(angle) * orbitSpeed).add(up.scale(Math.cos(angle) * orbitSpeed)).add(vel.scale(0.30));

                ParticleOptions particle;
                if (isAstral) {
                    particle = r.nextFloat() < 0.35F ? ParticleTypes.PORTAL : dust(r.nextBoolean() ? 0x9B4CFF : 0xBF70FF, 0.55F);
                } else {
                    particle = r.nextFloat() < 0.25F ? ParticleTypes.WITCH : dust(sing(r), 0.65F);
                }
                if (camClear(mc, pt.x, pt.y, pt.z)) exact(level, particle, pt.x, pt.y, pt.z, orbitVel.x, orbitVel.y, orbitVel.z, 6 + r.nextInt(5));
            }

            // B. While Shift is held: telekinetic downward thrust & trail behind
            if (thrustHeld) {
                int downCount = Math.max(2, Math.round((isAstral ? 4 : 7) * countMul));
                for (int i = 0; i < downCount; i++) {
                    Vec3 downPos = center.add(up.scale(-0.25 - r.nextDouble() * 0.20))
                        .add(right.scale((r.nextDouble() - 0.5) * 0.45))
                        .add(fwd.scale(-0.15 + r.nextDouble() * 0.40));
                    Vec3 downVel = up.scale(-0.25 - r.nextDouble() * 0.20)
                        .add(fwd.scale(-0.10))
                        .add(vel.scale(0.15));

                    ParticleOptions downPart = isAstral
                        ? (r.nextBoolean() ? dust(0xBF70FF, 0.65F) : dust(0x8A3CE0, 0.60F))
                        : dust(sing(r), 0.70F);
                    if (camClear(mc, downPos.x, downPos.y, downPos.z)) exact(level, downPart, downPos.x, downPos.y, downPos.z, downVel.x, downVel.y, downVel.z, 8 + r.nextInt(6));
                }

                // Streaming cosmic trail behind the player
                int trailCount = Math.max(1, Math.round((isAstral ? 2 : 4) * countMul));
                Vec3 trailVel = fwd.scale(-0.45).add(0, 0.02, 0);
                for (int i = 0; i < trailCount; i++) {
                    Vec3 trailPos = center.add(fwd.scale(-0.85))
                        .add(right.scale((r.nextDouble() - 0.5) * 0.40))
                        .add(up.scale((r.nextDouble() - 0.5) * 0.40));
                    ParticleOptions trailPart = isAstral
                        ? dust(r.nextBoolean() ? 0x9B4CFF : 0xC45AD4, 0.60F)
                        : (r.nextFloat() < 0.3F ? ParticleTypes.PORTAL : dust(sing(r), 0.70F));
                    if (camClear(mc, trailPos.x, trailPos.y, trailPos.z)) exact(level, trailPart, trailPos.x, trailPos.y, trailPos.z, trailVel.x, trailVel.y, trailVel.z, 12 + r.nextInt(8));
                }
            }
            return;
        }

        // 2. CYBERNETIC & CYBERPSYCHO: Jet-wing style with energy radiation and afterburner boost
        if (look == dev.justin.infinium.SetLook.CYBERNETIC || look == dev.justin.infinium.SetLook.CYBERPSYCHO) {
            boolean isPsycho = (look == dev.justin.infinium.SetLook.CYBERPSYCHO);
            float baseDensity = isPsycho ? (1.30F + 0.90F * prog) : (0.90F + 0.70F * prog);
            float countMul = baseDensity * mult * dens * (thrustHeld ? 2.5F : 1.0F);

            int count = Math.max(1, Math.round(2 * countMul));
            Vec3 trailVel = fwd.scale(thrustHeld ? -0.55 : -0.28).add(0, 0.02, 0);

            for (int i = 0; i < count; i++) {
                for (int side = -1; side <= 1; side += 2) {
                    double span = 1.05 + (r.nextDouble() - 0.5) * 0.15;
                    Vec3 tip = center.add(right.scale(side * span)).add(fwd.scale(-0.70)).add(up.scale(0.1));
                    double jx = (r.nextDouble() - 0.5) * 0.05;
                    double jy = (r.nextDouble() - 0.5) * 0.05;
                    double jz = (r.nextDouble() - 0.5) * 0.05;

                    ParticleOptions particle;
                    if (isPsycho) {
                        particle = dust(psycho(r, -1), thrustHeld ? 0.65F : 0.55F);
                    } else {
                        particle = r.nextFloat() < 0.35F ? ParticleTypes.ELECTRIC_SPARK : dust(r.nextBoolean() ? 0xFF00AA : 0xD24CFF, thrustHeld ? 0.60F : 0.50F);
                    }
                    exact(level, particle, tip.x + jx, tip.y + jy, tip.z + jz, trailVel.x, trailVel.y, trailVel.z, thrustHeld ? CYBER_LIFE_THRUST : CYBER_LIFE);   // batch1: shorter (was 14 / 9)
                }
            }
            return;
        }

        // 3. INFINITY: Combination of Astral/Singularity particles + thrusters.
        if (look == dev.justin.infinium.SetLook.INFINITY) {
            float ramp = dev.justin.infinium.InfinityFlight.getRampProgress(p);
            float countMul = (1.8F + 1.2F * prog) * mult * dens * (thrustHeld ? 2.2F : 1.0F);

            // A. Swirling cosmic void/starlight particles circling the player
            int swirlCount = Math.max(3, Math.round(5 * countMul));
            for (int i = 0; i < swirlCount; i++) {
                double angle = (p.tickCount * 0.40) + (i * (Math.PI * 2.0 / swirlCount)) + (r.nextDouble() - 0.5) * 0.30;
                double rad = 0.75 + r.nextDouble() * 0.45;
                double along = (r.nextDouble() - 0.5) * 1.2;
                Vec3 pt = center.add(right.scale(Math.cos(angle) * rad)).add(up.scale(Math.sin(angle) * rad)).add(fwd.scale(along));
                double orbitSpeed = 0.15 + r.nextDouble() * 0.08;
                Vec3 orbitVel = right.scale(-Math.sin(angle) * orbitSpeed).add(up.scale(Math.cos(angle) * orbitSpeed)).add(vel.scale(0.30));

                ParticleOptions particle = (r.nextFloat() < 0.45F)
                    ? dust(sing(r), 0.65F)
                    : dust(psycho(r, -1), 0.55F);
                exact(level, particle, pt.x, pt.y, pt.z, orbitVel.x, orbitVel.y, orbitVel.z, 6 + r.nextInt(5));
            }

            // B. Dual back thrusters firing high-energy exhaust
            Vec3 trailVel = fwd.scale(thrustHeld ? -0.58 : -0.30).add(0, 0.02, 0);
            int thrusterCount = Math.max(1, Math.round(2 * countMul));
            for (int i = 0; i < thrusterCount; i++) {
                for (int side = -1; side <= 1; side += 2) {
                    Vec3 nozzle = center.add(right.scale(side * 0.28)).add(fwd.scale(-0.45)).add(up.scale(0.12));
                    double jx = (r.nextDouble() - 0.5) * 0.04;
                    double jy = (r.nextDouble() - 0.5) * 0.04;
                    double jz = (r.nextDouble() - 0.5) * 0.04;

                    ParticleOptions particle;
                    if (ramp > 0.15F && r.nextFloat() < ramp * 0.75F) {
                        particle = ParticleTypes.END_ROD;
                    } else {
                        particle = r.nextBoolean() ? dust(sing(r), 0.65F) : dust(psycho(r, -1), 0.60F);
                    }
                    exact(level, particle, nozzle.x + jx, nozzle.y + jy, nozzle.z + jz, trailVel.x, trailVel.y, trailVel.z, thrustHeld ? 15 : 10);
                }
            }

            // C. While thrust is held: telekinetic downward thrust column
            if (thrustHeld) {
                int downCount = Math.max(3, Math.round(6 * countMul));
                for (int i = 0; i < downCount; i++) {
                    Vec3 downPos = center.add(up.scale(-0.25 - r.nextDouble() * 0.20))
                        .add(right.scale((r.nextDouble() - 0.5) * 0.45))
                        .add(fwd.scale(-0.15 + r.nextDouble() * 0.40));
                    Vec3 downVel = up.scale(-0.28 - r.nextDouble() * 0.20)
                        .add(fwd.scale(-0.12))
                        .add(vel.scale(0.15));
                    ParticleOptions downPart = (ramp > 0.3F && r.nextFloat() < 0.3F)
                        ? ParticleTypes.END_ROD
                        : dust(sing(r), 0.70F);
                    exact(level, downPart, downPos.x, downPos.y, downPos.z, downVel.x, downVel.y, downVel.z, 8 + r.nextInt(6));
                }
            }
        }
    }
    // ---- Strange Fragments / Spacetime Crystal: dropped item entities and held stacks (subtle; held = hidden in own first person) ----
    static void itemFx(Minecraft mc, ClientLevel level, RandomSource r) {
        if (mc.player == null) return;
        for (net.minecraft.world.entity.Entity e : level.entitiesForRendering()) {
            if (!(e instanceof net.minecraft.world.entity.item.ItemEntity) && !(e instanceof Player)) continue;
            if (e.distanceToSqr(mc.player) > 32 * 32) continue;
            if (e instanceof net.minecraft.world.entity.item.ItemEntity ie) stackFx(level, r, ie.getItem(), ie.getX(), ie.getY() + 0.25, ie.getZ(), e.tickCount);
            else if (e instanceof Player p && !(p == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson())) {
                double by = Math.toRadians(p.yBodyRot), fx = -Math.sin(by), fz = Math.cos(by);
                for (ItemStack s : new ItemStack[]{p.getMainHandItem(), p.getOffhandItem()})
                    stackFx(level, r, s, p.getX() + fx * 0.4 - fz * 0.35, p.getY() + 0.85, p.getZ() + fz * 0.4 + fx * 0.35, p.tickCount);
            }
        }
    }
    static void stackFx(ClientLevel level, RandomSource r, ItemStack s, double x, double y, double z, int tc) {
        if (s.is(Infinium.STRANGE_FRAGMENTS)) {                      // Astral-style faint speck, ~1 per 1.5 s
            if (r.nextFloat() < 1F / 30F) exact(level, ParticleTypes.REVERSE_PORTAL, x + (r.nextDouble() - .5) * .4, y + r.nextDouble() * .2, z + (r.nextDouble() - .5) * .4, 0, 0.01, 0);
        } else if (s.is(Infinium.SPACETIME_CRYSTAL)) {
            if (r.nextFloat() < 1F / 12F) {                           // CyberPsycho-style glitch spark: 2-4 point mini streak
                int n = 2 + r.nextInt(3); var d = dust(PSYCHO[r.nextInt(PSYCHO.length)], 0.3F);
                double ox = (r.nextDouble() - .5) * .35, oy = (r.nextDouble() - .5) * .3, oz = (r.nextDouble() - .5) * .35, dx = (r.nextDouble() - .5) * .08, dy = (r.nextDouble() - .5) * .08;
                for (int i = 0; i < n; i++) exact(level, d, x + ox + dx * i, y + oy + dy * i + (i % 2) * .03, z + oz, 0, 0, 0);
            }
            if (tc % 6 == 0) {                                        // Singularity-style spinning speck
                double a = tc * 0.2;
                exact(level, dust(sing(r), 0.4F), x + Math.cos(a) * .3, y + Math.sin(a * .5) * .08, z + Math.sin(a) * .3, -Math.sin(a) * .02, 0, Math.cos(a) * .02);
            }
        } else if ((dev.justin.infinium.DungeonFinder.ITEM != null && s.is(dev.justin.infinium.DungeonFinder.ITEM))
                || (dev.justin.infinium.InfiniumBook.GUIDE != null && s.is(dev.justin.infinium.InfiniumBook.GUIDE))) {
            if (r.nextFloat() < 1F / 18F) {
                exact(level, ParticleTypes.REVERSE_PORTAL, x + (r.nextDouble() - .5) * .35, y + r.nextDouble() * .2, z + (r.nextDouble() - .5) * .35, 0, 0.01, 0);
            }
            if (tc % 10 == 0) {
                double a = tc * 0.18;
                exact(level, dust(sing(r), 0.35F), x + Math.cos(a) * .25, y + Math.sin(a * .5) * .06, z + Math.sin(a) * .25, 0, 0.005, 0);
            }
        }
    }
    private ArmorParticles() {}
}
