package dev.justin.infinium;

import com.mojang.serialization.Codec;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** Sneak + use cycles the log conversion mode (0 = no effect). Logs/wood mined with the axe drop as the chosen type, same form. */
public class InfiniumAxeItem extends AxeItem {
    public static DataComponentType<Integer> LOG_MODE;
    /** {display name, log, wood, stripped log, stripped wood} (bamboo has no wood form: wood -> block). */
    static final String[][] TYPES = {
        {"No effect"},
        {"Oak", "oak_log", "oak_wood", "stripped_oak_log", "stripped_oak_wood"},
        {"Spruce", "spruce_log", "spruce_wood", "stripped_spruce_log", "stripped_spruce_wood"},
        {"Birch", "birch_log", "birch_wood", "stripped_birch_log", "stripped_birch_wood"},
        {"Jungle", "jungle_log", "jungle_wood", "stripped_jungle_log", "stripped_jungle_wood"},
        {"Acacia", "acacia_log", "acacia_wood", "stripped_acacia_log", "stripped_acacia_wood"},
        {"Dark Oak", "dark_oak_log", "dark_oak_wood", "stripped_dark_oak_log", "stripped_dark_oak_wood"},
        {"Mangrove", "mangrove_log", "mangrove_wood", "stripped_mangrove_log", "stripped_mangrove_wood"},
        {"Cherry", "cherry_log", "cherry_wood", "stripped_cherry_log", "stripped_cherry_wood"},
        {"Pale Oak", "pale_oak_log", "pale_oak_wood", "stripped_pale_oak_log", "stripped_pale_oak_wood"},
        {"Crimson Stem", "crimson_stem", "crimson_hyphae", "stripped_crimson_stem", "stripped_crimson_hyphae"},
        {"Warped Stem", "warped_stem", "warped_hyphae", "stripped_warped_stem", "stripped_warped_hyphae"},
        {"Bamboo Block", "bamboo_block", "bamboo_block", "stripped_bamboo_block", "stripped_bamboo_block"},
    };
    private static final Pattern FORM = Pattern.compile("^(stripped_)?(?:.+_)?(log|wood|stem|hyphae|block)$");

    public InfiniumAxeItem(ToolMaterial m, float d, float s, Properties p) { super(m, d, s, p); }

    public static void registerComponent() {
        LOG_MODE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Infinium.id("log_mode"),
            DataComponentType.<Integer>builder().persistent(Codec.intRange(0, TYPES.length - 1)).networkSynchronized(ByteBufCodecs.VAR_INT).build());
    }

    public static int mode(ItemStack s) { Integer m = s.get(LOG_MODE); return m == null ? 0 : m; }

    private InteractionResult cycle(Level level, Player player, ItemStack stack) {
        if (!level.isClientSide()) {
            int m = (mode(stack) + 1) % TYPES.length;
            stack.set(LOG_MODE, m); // component change: Singularity split rule applies via ItemStackSplitMixin
            ModeFx.burst(player, ModeFx.AXE, m, stack);
            player.sendOverlayMessage(Component.literal("Mode: " + (m == 0 ? "No effect" : TYPES[m][0] + (m == 12 ? "s" : " Logs"))).withStyle(ChatFormatting.LIGHT_PURPLE));
            level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.TOOL_MODE.value(), SoundSource.PLAYERS, 0.65F, 0.90F + 0.03F * m);
        }
        return InteractionResult.SUCCESS;
    }

    @Override public InteractionResult useOn(UseOnContext ctx) {
        if (InfiniumDurability.disabled(ctx.getItemInHand())) return InteractionResult.PASS;   // R4: a tool at the floor acts like an empty hand
        Player p = ctx.getPlayer();
        return super.useOn(ctx);
    }

    @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (InfiniumDurability.disabled(player.getItemInHand(hand))) return InteractionResult.PASS;   // R4: a tool at the floor acts like an empty hand
        if (player.isShiftKeyDown() && ModeFx.aimingAtAir(level, player) && ModeFx.holdingShield(player)) return InteractionResult.PASS; // no mode change while holding a shield
        if (player.isShiftKeyDown() && ModeFx.aimingAtAir(level, player)) return cycle(level, player, player.getItemInHand(hand)); // air only
        return super.use(level, player, hand);
    }

    @Override public void appendHoverText(ItemStack stack, TooltipContext ctx, TooltipDisplay display, Consumer<Component> b, TooltipFlag flag) {
        b.accept(Component.literal("Mode: " + (mode(stack) == 0 ? "No effect" : TYPES[mode(stack)][0] + " Logs")).withStyle(ChatFormatting.DARK_PURPLE));
    }

    /** Server-side drop rewrite. Only touches items tagged minecraft:logs whose form can be read from the id. */
    public static void convertDrops(ItemStack tool, List<ItemStack> drops) {
        int m = mode(tool);
        if (m == 0) return;
        for (int i = 0; i < drops.size(); i++) {
            ItemStack d = drops.get(i);
            if (d.isEmpty() || !d.is(ItemTags.LOGS)) continue;
            String path = BuiltInRegistries.ITEM.getKey(d.getItem()).getPath();
            Matcher mt = FORM.matcher(path);
            if (!mt.matches()) continue;
            boolean stripped = mt.group(1) != null;
            String f = mt.group(2);
            boolean wood = f.equals("wood") || f.equals("hyphae");
            if (f.equals("block") && !path.contains("bamboo")) continue; // unknown modded form
            String target = TYPES[m][1 + (stripped ? 2 : 0) + (wood ? 1 : 0)];
            Item out = BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(target));
            if (out == Items.AIR || out == d.getItem()) continue;
            drops.set(i, d.transmuteCopy(out, d.getCount()));
        }
    }
}
