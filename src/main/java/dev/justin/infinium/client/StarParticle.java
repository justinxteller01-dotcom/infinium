package dev.justin.infinium.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;

/** v4: full-bright shooting-star particle (ignores night light). Head: 2-tick bright core. Trail: fades and shrinks over TRAIL_FADE_TICKS. */
public final class StarParticle extends SingleQuadParticle {
    private final boolean head;
    private final float size;

    StarParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites, boolean head) {
        super(level, x, y, z, 0, 0, 0, sprites.first());
        this.head = head;
        this.xd = 0; this.yd = 0; this.zd = 0;
        this.hasPhysics = false;
        this.gravity = 0;
        this.friction = 1;
        int rgb = head ? ShootingStarLook.HEAD_RGB : ShootingStarLook.TRAIL_RGB;
        setColor(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F);
        this.size = (float) ((head ? ShootingStarLook.HEAD_BLOCKS : ShootingStarLook.TRAIL_BLOCKS) / 2.0);   // quadSize is the half-width
        this.quadSize = size;
        setAlpha(head ? 1F : ShootingStarLook.TRAIL_ALPHA);
        setLifetime(head ? 2 : ShootingStarLook.TRAIL_FADE_TICKS);
    }

    @Override
    public void tick() {
        super.tick();
        if (head) return;
        float f = 1F - age / (float) lifetime;
        setAlpha(ShootingStarLook.TRAIL_ALPHA * Math.max(0F, f));
        this.quadSize = size * (0.5F + 0.5F * f);
    }

    @Override
    protected int getLightCoords(float partialTick) { return 0xF000F0; }

    @Override
    protected Layer getLayer() { return Layer.TRANSLUCENT; }
}
