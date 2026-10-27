package dev.justin.infinium.client;

import dev.justin.infinium.InfinityFlight;
import net.minecraft.world.entity.player.Player;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Continuous loop flight thrust audio for Astral, Singularity, and Infinity.
 * Plays only while the flight thrust key is held in flight (local) or the synced thrust state is on (remote).
 * Pitch dynamically scales with speed (instantly matching current speed on start/resume).
 */
public class FlightThrustSoundInstance extends AbstractTickableSoundInstance {
    private final Player player;
    private final int family;
    private int ticksActive = 0;

    public FlightThrustSoundInstance(Player player, int family, SoundEvent event) {
        super(event, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
        this.player = player;
        this.family = family;
        this.looping = true;
        this.delay = 0;
        this.x = player.getX();
        this.y = player.getY();
        this.z = player.getZ();
        this.volume = 0.05F;
        updatePitch();
    }

    public void stopFlightSound() {
        stop();
    }

    public void updatePitch() {
        double speed = player.getDeltaMovement().length();
        // Vanilla max elytra speed is ~1.50 blocks/tick
        float ratio = Mth.clamp((float) (speed / 1.50), 0.0F, 1.0F);
        // Base pitch 0.85 ramps smoothly to 1.35 at full speed
        this.pitch = 0.85F + 0.50F * ratio;
    }

    @Override
    public void tick() {
        if (player.isRemoved() || !player.isAlive()) {
            stop();
            return;
        }

        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (!player.isFallFlying() || !InfinityFlight.grantsThrustFlight(chest) || !InfinityFlightClient.thrusting(player)) {
            stop();
            return;
        }

        this.x = player.getX();
        this.y = player.getY();
        this.z = player.getZ();

        ticksActive++;
        float targetVol = (family == 4 ? 0.85F : (family == 2 || family == 3 ? 0.75F : 0.65F))
            * InfiniumClientConfig.soundVolumeMaster();
        float fadeIn = Math.min(1.0F, ticksActive / 4.0F);
        this.volume = targetVol * fadeIn;

        updatePitch();
    }
}
