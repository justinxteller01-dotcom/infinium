package dev.justin.infinium;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.LodestoneTracker;

/** Dungeon Finder: compass + 8 bones. Points at the nearest Roguelike Dungeons entrance; while held, the client draws
 *  beacon beams on the entrance towers: green = normal, yellow = large, red = extra large (see RoguelikeCompat sizes). */
public final class DungeonFinder {
    public static Item ITEM;
    public static final int BEAM_RANGE = 4096;   // blocks; beams are sent for every known dungeon within this distance

    /** One beam: x, base y (tower top), z, size class 0/1/2. */
    public record Beam(int x, int y, int z, int size) {
        static final StreamCodec<RegistryFriendlyByteBuf, Beam> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Beam::x,
            ByteBufCodecs.VAR_INT, Beam::y, ByteBufCodecs.VAR_INT, Beam::z, ByteBufCodecs.VAR_INT, Beam::size, Beam::new);
    }
    public record BeamsPayload(List<Beam> beams) implements CustomPacketPayload {
        public static final Type<BeamsPayload> TYPE = new Type<>(Infinium.id("dungeon_beams"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BeamsPayload> CODEC =
            Beam.CODEC.apply(ByteBufCodecs.list(512)).map(BeamsPayload::new, BeamsPayload::beams);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static List<Component> info() {
        return List.of(
            Component.literal("Dungeon Finder").withStyle(ChatFormatting.LIGHT_PURPLE),
            Component.literal("Crafted from a compass surrounded by 8 bones.").withStyle(ChatFormatting.GRAY),
            Component.literal("Points to the nearest Roguelike dungeon entrance.").withStyle(ChatFormatting.GRAY),
            Component.literal("While held: beacon beams rise from dungeon entrance towers.").withStyle(ChatFormatting.GRAY),
            Component.literal("Green = normal, yellow = large, red = extra large.").withStyle(ChatFormatting.GRAY));
    }

    public static void register() {
        ITEM = Infinium.reg("dungeon_finder", p -> new Item(p) {
            @Override public void appendHoverText(ItemStack s, TooltipContext c, net.minecraft.world.item.component.TooltipDisplay d, java.util.function.Consumer<Component> b, net.minecraft.world.item.TooltipFlag f) {
                for (Component x : info().subList(2, 5)) b.accept(x);
            }
        }, new Item.Properties().stacksTo(1).fireResistant().rarity(Rarity.UNCOMMON));
        PayloadTypeRegistry.clientboundPlay().register(BeamsPayload.TYPE, BeamsPayload.CODEC);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 != 0) return;
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                boolean main = p.getMainHandItem().is(ITEM), off = p.getOffhandItem().is(ITEM);
                boolean inv = main || off;
                if (!inv) for (int i = 0; i < p.getInventory().getContainerSize() && !inv; i++) inv = p.getInventory().getItem(i).is(ITEM);
                if (!inv) continue;
                ServerLevel level = p.level();
                List<RoguelikeCompat.Site> sites = RoguelikeCompat.sites(level);
                RoguelikeCompat.Site best = null; double bd = Double.MAX_VALUE;
                List<Beam> beams = new ArrayList<>();
                for (RoguelikeCompat.Site s : sites) {
                    double dx = s.x() + 0.5 - p.getX(), dz = s.z() + 0.5 - p.getZ(), d = dx * dx + dz * dz;
                    if (d < bd) { bd = d; best = s; }
                    if (d < (double) BEAM_RANGE * BEAM_RANGE) beams.add(new Beam(s.x(), s.topY(), s.z(), s.size()));
                }
                // point every finder the player carries
                Optional<GlobalPos> tgt = best == null ? Optional.empty() : Optional.of(GlobalPos.of(level.dimension(), new BlockPos(best.x(), best.topY(), best.z())));
                for (int i = 0; i < p.getInventory().getContainerSize(); i++) aim(p.getInventory().getItem(i), tgt);
                if (main || off) {
                    if (ServerPlayNetworking.canSend(p, BeamsPayload.TYPE)) ServerPlayNetworking.send(p, new BeamsPayload(beams.size() > 512 ? beams.subList(0, 512) : beams));
                }
            }
        });
    }
    static void aim(ItemStack s, Optional<GlobalPos> tgt) {
        if (!s.is(ITEM)) return;
        LodestoneTracker cur = s.get(DataComponents.LODESTONE_TRACKER);
        LodestoneTracker nw = new LodestoneTracker(tgt, false);
        if (!nw.equals(cur)) s.set(DataComponents.LODESTONE_TRACKER, nw);
    }
    private DungeonFinder() {}
}
