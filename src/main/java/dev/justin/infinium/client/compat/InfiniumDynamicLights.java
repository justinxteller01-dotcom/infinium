package dev.justin.infinium.client.compat;

import dev.justin.infinium.Infinium;
import dev.justin.infinium.SetBonuses;
import dev.lambdaurora.lambdynlights.api.DynamicLightsContext;
import dev.lambdaurora.lambdynlights.api.DynamicLightsInitializer;
import dev.lambdaurora.lambdynlights.api.entity.luminance.EntityLuminance;
import dev.lambdaurora.lambdynlights.api.item.ItemLightSourceManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

/** Section 9 / 12.9: real (uncolored) light 4 / 8 / 12 for full sets through LambDynamicLights. Only loaded via its entrypoint. */
public final class InfiniumDynamicLights implements DynamicLightsInitializer {
    static final EntityLuminance SET_GLOW = new EntityLuminance() {
        @Override public EntityLuminance.Type type() { return TYPE; }
        @Override public int getLuminance(ItemLightSourceManager items, Entity e) {
            return e instanceof LivingEntity le ? SetBonuses.light(le) : 0;
        }
    };
    static final EntityLuminance.Type TYPE = EntityLuminance.Type.registerSimple(Infinium.id("full_set_glow"), SET_GLOW);

    @Override public void onInitializeDynamicLights(DynamicLightsContext ctx) {
        ctx.entityLightSourceManager().onRegisterEvent().register(reg -> {
            reg.register(net.minecraft.world.entity.EntityTypes.PLAYER, SET_GLOW);
            reg.register(net.minecraft.world.entity.EntityTypes.ARMOR_STAND, SET_GLOW);
        });
    }
}
