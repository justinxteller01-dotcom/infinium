package dev.justin.infinium;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Cyber Psycho / Infinity full set: mobs targeting the wearer are slowed 25 % per level for 3 s when the wearer is hurt (no icon/particles). */
public class CyberPsychoSlowEffect extends MobEffect {
    public CyberPsychoSlowEffect() {
        super(MobEffectCategory.HARMFUL, 0x9B3BFF);
        addAttributeModifier(Attributes.MOVEMENT_SPEED, Infinium.id("cyberpsycho_slow"), -0.25, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addAttributeModifier(Attributes.FLYING_SPEED, Infinium.id("cyberpsycho_slow_fly"), -0.25, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }
}
