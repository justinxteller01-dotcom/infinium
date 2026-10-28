package dev.justin.infinium.client.mixin;

import dev.justin.infinium.client.anim.SwordMove;
import dev.justin.infinium.client.anim.SwordPoseState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(HumanoidRenderState.class)
public abstract class SwordAnimRenderStateMixin implements SwordPoseState {
    @Unique private boolean infinium$sw;
    @Unique private SwordMove infinium$swMove = SwordMove.PLAIN;
    @Unique private float infinium$swRoll, infinium$swSwing, infinium$swEquip, infinium$swTime;
    @Override public boolean infinium$swordActive() { return infinium$sw; }
    @Override public void infinium$setSword(boolean a, SwordMove move, float roll, float swing, float equip, float time) {
        infinium$sw = a; infinium$swMove = move; infinium$swRoll = roll; infinium$swSwing = swing; infinium$swEquip = equip; infinium$swTime = time;
    }
    @Override public SwordMove infinium$swordMove() { return infinium$swMove; }
    @Override public float infinium$swordRoll() { return infinium$swRoll; }
    @Override public float infinium$swordSwing() { return infinium$swSwing; }
    @Override public float infinium$swordEquip() { return infinium$swEquip; }
    @Override public float infinium$swordTime() { return infinium$swTime; }
}
