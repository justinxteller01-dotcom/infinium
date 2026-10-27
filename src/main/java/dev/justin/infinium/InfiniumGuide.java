package dev.justin.infinium;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;

/**
 * Right-click opens the guide. The client opener is installed by {@code InfiniumGuideClient}
 * so this common class never names a client type (dedicated server safe).
 */
public final class InfiniumGuide {
    /** Set from the client initializer. Null on a dedicated server. */
    public static Opener OPENER;

    @FunctionalInterface
    public interface Opener {
        void open(Player player);
    }

    private InfiniumGuide() {}

    public static Item createItem(Item.Properties props) {
        return new Item(props) {
            @Override
            public InteractionResult use(Level level, Player player, InteractionHand hand) {
                if (level.isClientSide() && OPENER != null) OPENER.open(player);
                return InteractionResult.SUCCESS;
            }
        };
    }

    /** One copy, ever. Flag is persistent and copied on death, respawn, and dimension change. */
    public static void giveOnce(ServerPlayer player) {
        var target = (net.fabricmc.fabric.api.attachment.v1.AttachmentTarget) (Object) player;
        if (Boolean.TRUE.equals(target.getAttached(GuideGiven.TYPE))) return;
        ItemStack book = new ItemStack(InfiniumBook.GUIDE);
        if (!player.getInventory().add(book) && player.level() instanceof ServerLevel level) {
            // LivingEntity.spawnAtLocation(ServerLevel, ItemStack) is public. Player.drop(ItemStack, boolean) is not.
            player.spawnAtLocation(level, book);
        }
        target.setAttached(GuideGiven.TYPE, true);
    }
}
