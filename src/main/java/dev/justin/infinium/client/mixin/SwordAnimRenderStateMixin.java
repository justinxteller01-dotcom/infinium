package dev.justin.infinium.client.mixin;

import dev.justin.infinium.client.anim.SwordPoseState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(HumanoidRenderState.class)
public abstract class SwordAnimRenderStateMixin implements SwordPoseState {
    @Unique private boolean infinium$sw;
    @Unique private float infinium$swSwing, infinium$swSide = 1F, infinium$swEquip, infinium$swTime;
    @Override public boolean infinium$swordActive() { return infinium$sw; }
    @Override public void infinium$setSword(boolean a, float swing, float side, float equip, float time) {
        infinium$sw = a; infinium$swSwing = swing; infinium$swSide = side; infinium$swEquip = equip; infinium$swTime = time;
    }
    @Override public float infinium$swordSwing() { return infinium$swSwing; }
    @Override public float infinium$swordSide() { return infinium$swSide; }
    @Override public float infinium$swordEquip() { return infinium$swEquip; }
    @Override public float infinium$swordTime() { return infinium$swTime; }
}
