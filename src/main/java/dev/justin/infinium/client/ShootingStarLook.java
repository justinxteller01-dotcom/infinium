package dev.justin.infinium.client;

/**
 * pass2: ONE source for the purple shooting-star look, used by the out-of-world menu backdrop; the in-game sky (WorldSkyFx, sky-v6) only shares HEAD_RGB/TRAIL_RGB/TRAIL_FADE_TICKS and keeps its own pre-v5 sizes/speeds. Originally also
 * out-of-world menu backdrop (UiInfection.menuBackdrop, screen-space). Change values here only, so the two can't drift.
 */
public final class ShootingStarLook {
    /** Head: bright pink-purple dust at vanilla's maximum dust scale (4.0), plus a full-bright END_ROD glow core. */
    public static final int HEAD_RGB = 0xF0B0FF;
    public static final float HEAD_SCALE = 4.0F;          // was 3.2 (dust scale is clamped to 4.0 by vanilla)
    /** Trail: thicker, longer purple dust chain behind the head. */
    public static final int TRAIL_RGB = 0xB050E0;
    public static final float TRAIL_SCALE = 3.0F;         // was 1.8
    public static final int TRAIL_POINTS = 2;   // v5; was 3             // v4: trail particles per tick (each lives TRAIL_FADE_TICKS, so the streak is continuous)
    /** v4: sky stars are custom full-bright infinium:star_head / star_trail particles (StarParticle), no night dimming.
     *  On-screen sizes in blocks at the dome; the menu converts the same values to px. */
    public static final double HEAD_BLOCKS = 0.7, TRAIL_BLOCKS = 0.45;   // v5 restrained; v4 was 1.6 / 1.2   // were 0.6 / 0.45 (dust 4.0 / 3.0 x 0.15)
    public static final float TRAIL_ALPHA = 140F / 255F;   // v5; was 240
    public static final double TRAIL_STEP = 0.35;          // fraction of one tick's travel between trail points
    /** Approximate on-screen fade of the dust/glow trail in ticks (the menu draws this many past positions, fading). */
    public static final int TRAIL_FADE_TICKS = 32;
    /** Motion (blocks per tick in the sky) and life. */
    public static final double SPEED = 1.1, DROP = 0.08;   // v5 slow drift; was 1.85 / 0.22
    public static final int LIFE_MIN = 95, LIFE_RAND = 45;
    /** Sky dome radius used for the screen-space conversion (WorldSkyFx spawns at 48-72 blocks). */
    public static final double DOME = 60.0;
    /** Dust quad ~0.15 x scale blocks across (WorldSkyFx note). Menu: px per tick and px sizes for a screen of the given GUI width, as the sky version appears at ~70 deg FOV. */
    public static double pxPerBlockAtDome(int guiWidth) { return guiWidth / (2 * DOME * Math.tan(Math.toRadians(35))); }
    public static int headPx(int guiWidth) { return Math.max(3, (int) Math.round(HEAD_BLOCKS * pxPerBlockAtDome(guiWidth))); }
    public static int trailPx(int guiWidth) { return Math.max(2, (int) Math.round(TRAIL_BLOCKS * pxPerBlockAtDome(guiWidth))); }
    private ShootingStarLook() {}
}
