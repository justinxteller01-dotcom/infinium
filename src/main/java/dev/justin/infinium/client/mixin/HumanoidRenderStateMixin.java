package dev.justin.infinium.client.mixin;

import dev.justin.infinium.client.WingFoldState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(HumanoidRenderState.class)
public abstract class HumanoidRenderStateMixin implements WingFoldState {
    @Unique private float infinium$fold = -1F;
    @Unique private float infinium$flight = 0F;
    @Override public float infinium$getFold() { return infinium$fold; }
    @Override public void infinium$setFold(float t) { infinium$fold = t; }
    @Override public float infinium$getFlight() { return infinium$flight; }
    @Override public void infinium$setFlight(float t) { infinium$flight = t; }
}
