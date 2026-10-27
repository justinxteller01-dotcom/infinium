package dev.justin.infinium.client;

import net.minecraft.client.renderer.entity.state.EntityRenderState;

public class EnergySlashRenderState extends EntityRenderState {
    public int activeSegments = 0x7FFF;
    public float roll = 0.0F;
    public float yaw = 0.0F;
    public float pitch = 0.0F;
    public int color = 0xB04CFF;
    public boolean isStab = false;
    public int tier = 1;
    public int ageTicks = 0;
}
