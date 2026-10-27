package dev.justin.infinium;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

/** {@code infinium:guide_given}. Same attachment shape as {@code Perks}, without client sync. */
public final class GuideGiven {
    public static final AttachmentType<Boolean> TYPE = AttachmentRegistry.create(
            Infinium.id("guide_given"),
            builder -> builder.persistent(Codec.BOOL).copyOnDeath().initializer(() -> false));

    private GuideGiven() {}

    public static void registerEvents() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> InfiniumGuide.giveOnce(handler.getPlayer()));
        // CopyFrom is (oldPlayer, newPlayer, alive) in fabric-api 26.2 ServerPlayerEvents.
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
            var from = (net.fabricmc.fabric.api.attachment.v1.AttachmentTarget) (Object) oldPlayer;
            var to = (net.fabricmc.fabric.api.attachment.v1.AttachmentTarget) (Object) newPlayer;
            if (Boolean.TRUE.equals(from.getAttached(TYPE))) to.setAttached(TYPE, true);
        });
    }
}
