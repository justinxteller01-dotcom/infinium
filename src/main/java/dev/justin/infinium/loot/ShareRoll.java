package dev.justin.infinium.loot;

import dev.justin.infinium.Infinium;
import dev.justin.infinium.InfiniumStyle;
import dev.justin.infinium.StyleLoot;
import dev.justin.infinium.StyleTemplates;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/**
 * Natural-chest share. Weights Astral 3, Cybernetic 3, Singularity 1, Cyber Psycho 1.
 * No Infinity template. No crystals. Hint kind is the strongest thing this roll produced.
 */
public final class ShareRoll {
    public enum Kind { NONE, FRAGMENTS, TIER1, RARE }

    public record Result(Kind kind, List<ItemStack> items) {}

    public static Result natural(long seed, StyleLoot.Tier tier) {
        List<ItemStack> items = new ArrayList<>(2);
        Kind kind = Kind.NONE;
        if (tier == null) return new Result(kind, items);
        RandomSource r = RandomSource.create(seed);

        float wAstral = 3.0f * (float) dev.justin.infinium.InfiniumConfig.getTemplateRateAstral();
        float wCyber = 3.0f * (float) dev.justin.infinium.InfiniumConfig.getTemplateRateCybernetic();
        float wSing = 1.0f * (float) dev.justin.infinium.InfiniumConfig.getTemplateRateSingularity();
        float wPsycho = 1.0f * (float) dev.justin.infinium.InfiniumConfig.getTemplateRateCyberpsycho();
        float totalWeight = wAstral + wCyber + wSing + wPsycho;
        float effectiveTemplateChance = totalWeight > 0 ? (tier.pT() * (totalWeight / 8.0f)) : 0.0f;

        if (effectiveTemplateChance > 0 && r.nextFloat() < effectiveTemplateChance) {
            float roll = r.nextFloat() * totalWeight;
            InfiniumStyle style;
            if (roll < wAstral) {
                style = InfiniumStyle.ASTRAL;
            } else if (roll < wAstral + wCyber) {
                style = InfiniumStyle.CYBERNETIC;
            } else if (roll < wAstral + wCyber + wSing) {
                style = InfiniumStyle.SINGULARITY;
            } else {
                style = InfiniumStyle.CYBERPSYCHO;
            }
            kind = style == InfiniumStyle.SINGULARITY || style == InfiniumStyle.CYBERPSYCHO ? Kind.RARE : Kind.TIER1;
            var template = StyleTemplates.TEMPLATES.get(style);
            if (template != null) items.add(new ItemStack(template));
        }

        float fragRate = (float) dev.justin.infinium.InfiniumConfig.getWorldChestFragmentRate();
        float effectiveFragChance = fragRate > 0 ? tier.pF() * fragRate : 0.0f;
        if (effectiveFragChance > 0 && r.nextFloat() < effectiveFragChance) {
            if (kind == Kind.NONE) kind = Kind.FRAGMENTS;
            int span = Math.max(0, tier.fMax() - tier.fMin());
            int count = tier.fMin() + (span == 0 ? 0 : r.nextInt(span + 1));
            items.add(new ItemStack(Infinium.SPACETIME_FRAGMENTS, Math.max(1, count)));
        }
        return new Result(kind, items);
    }

    /** 1 fragments, 2 Astral, 3 Cybernetic, 4 Singularity, 5 Cyber Psycho. 0 = no hint. */
    public static int hintCode(Result result) {
        return result == null ? 0 : hintCode(result.items());
    }

    /** Strongest item in the share: rare template (4/5) over tier-1 template (2/3) over fragments (1). */
    public static int hintCode(List<ItemStack> items) {
        int best = 0;
        for (ItemStack stack : items) {
            int c = stack.is(template(InfiniumStyle.CYBERPSYCHO)) ? 5 : stack.is(template(InfiniumStyle.SINGULARITY)) ? 4
                    : stack.is(template(InfiniumStyle.CYBERNETIC)) ? 3 : stack.is(template(InfiniumStyle.ASTRAL)) ? 2
                    : stack.is(Infinium.SPACETIME_FRAGMENTS) ? 1 : 0;
            int rank = c >= 4 ? 3 : c >= 2 ? 2 : c;
            int bestRank = best >= 4 ? 3 : best >= 2 ? 2 : best;
            if (rank > bestRank) best = c;
        }
        return best;
    }

    private static net.minecraft.world.item.Item template(InfiniumStyle style) {
        return StyleTemplates.TEMPLATES.get(style);
    }

    private ShareRoll() {}
}
