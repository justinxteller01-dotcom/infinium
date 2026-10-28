package dev.justin.infinium.client;

import dev.justin.infinium.AirJumps;
import dev.justin.infinium.ModSounds;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/** G4b client side of {@link AirJumps}: prediction, local sound, and the server's absolute resyncs. */
public final class AirJumpClient {
    private static int remaining = -1;          // -1 = full (unknown max until first ground tick)
    private static boolean wasHeld;
    private static boolean releasedSinceTakeoff;

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(AirJumps.Sync.TYPE, (pl, ctx) -> ctx.client().execute(() -> remaining = pl.remaining()));
    }

    /** Start of LocalPlayer.aiStep. */
    public static void tick(LocalPlayer p) {
        boolean held = p.input != null && p.input.keyPresses.jump();
        int max = AirJumps.max(p);
        if (p.onGround() || remaining < 0) remaining = max;
        if (p.onGround()) releasedSinceTakeoff = false;
        else if (!held) releasedSinceTakeoff = true;
        if (remaining > max) remaining = max;
        boolean press = held && !wasHeld;
        wasHeld = held;
        if (!press || !releasedSinceTakeoff || remaining <= 0 || max <= 0 || !AirJumps.canAirJump(p)) return;
        if (!ClientPlayNetworking.canSend(AirJumps.Use.TYPE)) return;
        remaining--;
        Vec3 v = p.getDeltaMovement();
        p.setDeltaMovement(v.x, 0.42, v.z);        // vanilla jump velocity, no hold boost
        VariableJump.cancel();
        VariableJump.startAir(p, 0.42);   // batch1: Infinity air jumps scale with hold; Cyb/CP stay plain height. Also arms the forward push for every air jump
        p.resetFallDistance();
        p.level().playLocalSound(p.getX(), p.getY(), p.getZ(), (remaining == 0 ? ModSounds.BONUS_JUMP_LAST : ModSounds.BONUS_JUMP).value(),
            SoundSource.PLAYERS, 0.8F, 1.0F, false);
        ClientPlayNetworking.send(new AirJumps.Use());
    }

    private AirJumpClient() {}
}
