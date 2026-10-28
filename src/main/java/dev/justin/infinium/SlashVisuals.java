package dev.justin.infinium;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * ALL tunables for per-armor-set energy slash / stab-beam visuals (branch sword-anim).
 * Picked CLIENT-side from the slash owner's worn set ({@link SetLook#of(LivingEntity)}); if the owner
 * is not resolvable on the client, falls back to the synced TIER (1=Cybernetic, 2=Cyber Psycho, 3=Infinity).
 * Pure data, no client-only classes, so EnergySlashEntity (client particle path) and EnergySlashRenderer share it.
 * Revert: set ENABLED=false (or JVM arg -Dinfinium.slashVisuals=false) to get the legacy tier-only look.
 */
public final class SlashVisuals {
    private SlashVisuals() {}

    public static final boolean ENABLED = !"false".equalsIgnoreCase(System.getProperty("infinium.slashVisuals", "true"));

    public static final class Profile {
        /** set accent (0xRRGGBB) blended into the sword dye color by tintMix (0 = pure dye, 1 = pure accent). */
        public final int accent; public final float tintMix;
        /** hot core color added toward white (0..255). */
        public final int coreLift;
        /** crescent ribbon thickness / beam radius scale. */
        public final float width;
        /** multiplier on core / aura alpha. */
        public final float alpha;
        /** aura (glow) height scale and tail length scale; haloLayers = extra wide faint glow shells. */
        public final float glow, tail; public final int haloLayers;
        /** flicker amplitude 0..1 and speed (cycles per tick); glitch = chance per segment per tick to drop/offset. */
        public final float flicker, flickerSpeed, glitch;
        /** gravity warp: crescent pinch/ripple amount (0 = flat). */
        public final float warp;
        /** spark chance per active node per tick, and the particle mix (primary always, secondary at secondaryChance). */
        public final float particleChance, secondaryChance; public final ParticleOptions primary, secondary;
        /** particle drift speed backwards along the travel; negative = pulled inward (gravity). */
        public final float particleSpeed;

        Profile(int accent, float tintMix, int coreLift, float width, float alpha, float glow, float tail, int haloLayers,
                float flicker, float flickerSpeed, float glitch, float warp,
                float particleChance, float secondaryChance, ParticleOptions primary, ParticleOptions secondary, float particleSpeed) {
            this.accent = accent; this.tintMix = tintMix; this.coreLift = coreLift; this.width = width; this.alpha = alpha;
            this.glow = glow; this.tail = tail; this.haloLayers = haloLayers; this.flicker = flicker; this.flickerSpeed = flickerSpeed;
            this.glitch = glitch; this.warp = warp; this.particleChance = particleChance; this.secondaryChance = secondaryChance;
            this.primary = primary; this.secondary = secondary; this.particleSpeed = particleSpeed;
        }

        public int tint(int dye) {
            int r = (dye >> 16) & 0xFF, g = (dye >> 8) & 0xFF, b = dye & 0xFF;
            int ar = (accent >> 16) & 0xFF, ag = (accent >> 8) & 0xFF, ab = accent & 0xFF;
            r = (int) (r + (ar - r) * tintMix); g = (int) (g + (ag - g) * tintMix); b = (int) (b + (ab - b) * tintMix);
            return (r << 16) | (g << 8) | b;
        }
    }

    //                                   accent    mix  core width alpha glow tail halo flick spd  glitch warp  pChance sec   primary                         secondary                    speed
    /** weaker, glitchy, flickery electric crescent. */
    public static final Profile CYBERNETIC  = new Profile(0xFF3FBF, 0.45F, 150, 0.70F, 0.75F, 0.80F, 0.70F, 0, 0.55F, 0.90F, 0.18F, 0.00F, 0.14F, 0.35F, ParticleTypes.ELECTRIC_SPARK, ParticleTypes.WAX_OFF,      0.04F);
    /** brighter, thicker, fiery laser energy. */
    public static final Profile CYBERPSYCHO = new Profile(0xFF1EB4, 0.50F, 170, 1.10F, 1.00F, 1.30F, 1.20F, 1, 0.10F, 0.60F, 0.00F, 0.00F, 0.30F, 0.45F, ParticleTypes.FLAME,          ParticleTypes.ELECTRIC_SPARK, 0.07F);
    /** dark, magical, starry purple energy. */
    public static final Profile ASTRAL      = new Profile(0x7A3CFF, 0.55F, 110, 0.95F, 0.90F, 1.25F, 1.10F, 1, 0.12F, 0.12F, 0.00F, 0.05F, 0.22F, 0.40F, ParticleTypes.END_ROD,        ParticleTypes.REVERSE_PORTAL, 0.02F);
    /** darker, denser, strong gravity warp (particles pulled inward). */
    public static final Profile SINGULARITY = new Profile(0x3A0F7A, 0.65F,  80, 1.15F, 1.00F, 1.50F, 1.30F, 2, 0.08F, 0.08F, 0.00F, 0.22F, 0.32F, 0.55F, ParticleTypes.REVERSE_PORTAL, ParticleTypes.SQUID_INK,      -0.05F);
    /** most epic: widest, layered halos, starry + fiery sparks, slow warp breathing. */
    public static final Profile INFINITY    = new Profile(0xFFA0DF, 0.35F, 200, 1.40F, 1.00F, 1.80F, 1.50F, 3, 0.06F, 0.20F, 0.00F, 0.10F, 0.45F, 0.60F, ParticleTypes.END_ROD,        ParticleTypes.FLAME,         0.05F);

    public static Profile of(SetLook look) {
        if (look == null) return null;
        return switch (look) {
            case CYBERNETIC -> CYBERNETIC; case CYBERPSYCHO -> CYBERPSYCHO; case ASTRAL -> ASTRAL;
            case SINGULARITY -> SINGULARITY; case INFINITY -> INFINITY;
        };
    }

    /** Resolve from the attacker's armor; fall back to synced tier. */
    public static Profile resolve(Entity owner, int tier) {
        Profile p = owner instanceof LivingEntity le ? of(SetLook.of(le)) : null;
        if (p != null) return p;
        return tier >= 3 ? INFINITY : (tier == 2 ? CYBERPSYCHO : CYBERNETIC);
    }

    /** Deterministic 0..1 hash noise (glitch/flicker), stable per segment and tick. */
    public static float hash(int a, int b) {
        int h = a * 0x27d4eb2d ^ b * 0x165667b1; h ^= h >>> 15; h *= 0x85ebca6b; h ^= h >>> 13;
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }
}
