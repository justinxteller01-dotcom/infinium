package dev.justin.infinium;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SmithingTemplateItem;

/** The four Infinium style smithing templates. One is required for netherite -> Infinium; it decides the style. */
public final class StyleTemplates {
    public static final Map<InfiniumStyle, Item> TEMPLATES = new EnumMap<>(InfiniumStyle.class);
    private static Identifier slot(String n) { return Identifier.withDefaultNamespace("container/slot/" + n); }

    public static void register() {
        Component appliesTo = Component.translatable("item.infinium.smithing_template.applies_to").withStyle(ChatFormatting.LIGHT_PURPLE);
        Component ingredients = Component.translatable("item.infinium.smithing_template.ingredients").withStyle(ChatFormatting.LIGHT_PURPLE);
        Component baseDesc = Component.translatable("item.infinium.smithing_template.base_slot_description");
        Component addDesc = Component.translatable("item.infinium.smithing_template.additions_slot_description");
        List<Identifier> baseIcons = List.of(slot("helmet"), slot("chestplate"), slot("leggings"), slot("boots"));
        List<Identifier> addIcons = List.of(slot("ingot"));
        for (InfiniumStyle s : InfiniumStyle.values()) {
            String k = "item.infinium." + s.id + "_upgrade_smithing_template.";
            appliesTo = Component.translatable(k + "applies_to").withStyle(ChatFormatting.LIGHT_PURPLE);
            baseDesc = Component.translatable(k + "base_slot_description"); addDesc = Component.translatable(k + "additions_slot_description");
            Component ap = appliesTo, bd = baseDesc, ad = addDesc;
            Item.Properties p = new Item.Properties().fireResistant().rarity(s.rarer ? Rarity.EPIC : Rarity.RARE);
            TEMPLATES.put(s, Infinium.reg(s.id + "_upgrade_smithing_template",
                pp -> new SmithingTemplateItem(ap, ingredients, bd, ad, baseIcons, addIcons, pp), p));
        }
    }
    public static InfiniumStyle styleOf(Item template) {
        for (Map.Entry<InfiniumStyle, Item> e : TEMPLATES.entrySet()) if (e.getValue() == template) return e.getKey();
        return null;
    }
    public static int tier(InfiniumStyle s) { return s == null || !s.rarer ? 1 : 2; }
    /** Infinium armor of style base (null = legacy, counts as Astral) + template t. withIngot = addition slot holds the ingot.
     *  Astral<->Cybernetic and Singularity<->Cyber Psycho, no ingot: same-tier swap, template is consumed. Singularity only on Astral, CyberPsycho only on Cybernetic. Cybernetic->CyberPsycho / Astral->Singularity with ingot. */
    public static boolean allowed(InfiniumStyle base, InfiniumStyle t, boolean withIngot) {
        if (base == null && !withIngot) return tier(t) == 1;   // legacy unstyled armor may take a tier-1 style
        InfiniumStyle b = base == null ? InfiniumStyle.ASTRAL : base;
        if (!withIngot) return b != t && tier(b) == tier(t);          // same-tier swap (template consumed): Astral <-> Cybernetic, Singularity <-> Cyber Psycho
        return (b == InfiniumStyle.CYBERNETIC && t == InfiniumStyle.CYBERPSYCHO) || (b == InfiniumStyle.ASTRAL && t == InfiniumStyle.SINGULARITY);
    }
    private StyleTemplates() {}
}
