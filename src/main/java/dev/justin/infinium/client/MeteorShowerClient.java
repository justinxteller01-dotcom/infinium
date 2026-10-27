package dev.justin.infinium.client;

import dev.justin.infinium.Infinium;
import dev.justin.infinium.ModSounds;
import dev.justin.infinium.world.MeteorImpactPayload;
import dev.justin.infinium.world.MeteorShower;
import dev.justin.infinium.world.MeteorShowerStatePayload;
import dev.justin.infinium.world.MeteorSpawnPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Client-side handler for the Meteor Shower event:
 * - Manages ambient looping drone soundscape.
 * - Renders 3D ballistic falling streaks approaching the ground.
 * - Renders cosmetic impact craters and particle shockwaves.
 */
public final class MeteorShowerClient {
    private static final RandomSource RNG = RandomSource.create();
    private static int currentPhase = MeteorShower.PHASE_IDLE;
    private static int ticksRemaining = 0;
    private static MeteorAmbientSoundInstance ambientSoundInstance = null;

    private static final List<ClientFallingMeteor> METEORS = new ArrayList<>();

    public static class ClientFallingMeteor {
        public final int id;
        public double x, y, z;
        public final double vx, vy, vz;
        public int ticksLeft;
        public final boolean dropsLoot;

        public ClientFallingMeteor(int id, double sx, double sy, double sz, double tx, double ty, double tz, int flightTicks, boolean dropsLoot) {
            this.id = id;
            this.x = sx;
            this.y = sy;
            this.z = sz;
            this.ticksLeft = flightTicks;
            this.dropsLoot = dropsLoot;
            double f = Math.max(1, flightTicks);
            this.vx = (tx - sx) / f;
            this.vy = (ty - sy) / f;
            this.vz = (tz - sz) / f;
        }
    }

    public static class MeteorAmbientSoundInstance extends AbstractTickableSoundInstance {
        public MeteorAmbientSoundInstance() {
            super(ModSounds.METEOR_AMBIENT.value(), SoundSource.WEATHER, SoundInstance.createUnseededRandom());
            this.looping = true;
            this.relative = true;
            this.delay = 0;
            this.volume = 0.85F * InfiniumClientConfig.soundVolumeMaster() * InfiniumClientConfig.soundVolumeMeteor();
            this.pitch = 1.0F;
            this.attenuation = SoundInstance.Attenuation.NONE;
        }

        @Override
        public void tick() {
            Minecraft mc = Minecraft.getInstance();
            if (!MeteorShowerClient.isActive() || mc.level == null) {
                this.stop();
            } else {
                this.volume = 0.85F * InfiniumClientConfig.soundVolumeMaster() * InfiniumClientConfig.soundVolumeMeteor();
            }
        }

        public void cancel() {
            this.stop();
        }
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(MeteorShowerStatePayload.TYPE, (payload, ctx) -> {
            ctx.client().execute(() -> {
                int oldPhase = currentPhase;
                currentPhase = payload.phase();
                ticksRemaining = payload.ticksRemaining();

                Minecraft mc = ctx.client();
                float meteorVol = InfiniumClientConfig.soundVolumeMaster() * InfiniumClientConfig.soundVolumeMeteor();
                if (currentPhase == MeteorShower.PHASE_WARNING && oldPhase != MeteorShower.PHASE_WARNING) {
                    mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.METEOR_WARNING.value(), 1.0F, meteorVol));
                } else if (currentPhase == MeteorShower.PHASE_COUNTDOWN && oldPhase != MeteorShower.PHASE_COUNTDOWN) {
                    mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.METEOR_COUNTDOWN.value(), 1.0F, meteorVol));
                } else if (currentPhase == MeteorShower.PHASE_ACTIVE) {
                    startAmbientSound(mc);
                } else if (currentPhase == MeteorShower.PHASE_IDLE) {
                    stopAmbientSound();
                    METEORS.clear();
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(MeteorSpawnPayload.TYPE, (payload, ctx) -> {
            ctx.client().execute(() -> {
                Minecraft mc = ctx.client();
                if (mc.level == null) return;
                ClientFallingMeteor meteor = new ClientFallingMeteor(
                    payload.meteorId(),
                    payload.startX(), payload.startY(), payload.startZ(),
                    payload.targetX(), payload.targetY(), payload.targetZ(),
                    payload.flightTicks(), payload.dropsLoot()
                );
                METEORS.add(meteor);
                // 3D ballistic sound approaching ground
                float meteorVol = InfiniumClientConfig.soundVolumeMaster() * InfiniumClientConfig.soundVolumeMeteor();
                mc.level.playLocalSound(
                    payload.startX(), payload.startY(), payload.startZ(),
                    ModSounds.METEOR_FALLING.value(), SoundSource.WEATHER,
                    3.5F * meteorVol, 0.95F + RNG.nextFloat() * 0.1F, false
                );
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(MeteorImpactPayload.TYPE, (payload, ctx) -> {
            ctx.client().execute(() -> {
                Minecraft mc = ctx.client();
                if (mc.level == null) return;
                double x = payload.x();
                double y = payload.y();
                double z = payload.z();
                boolean loot = payload.dropsLoot();

                // 3D deep impact rumble & explosion
                float meteorVol = InfiniumClientConfig.soundVolumeMaster() * InfiniumClientConfig.soundVolumeMeteor();
                mc.level.playLocalSound(
                    x, y, z,
                    ModSounds.METEOR_IMPACT.value(), SoundSource.WEATHER,
                    4.0F * meteorVol, 0.9F + RNG.nextFloat() * 0.2F, false
                );

                // Cosmetic impact blast VFX (no block destruction)
                // 1. Core flash burst
                mc.level.addParticle(ParticleTypes.EXPLOSION, x, y + 0.5, z, 0, 0, 0);
                for (int i = 0; i < 6; i++) {
                    mc.level.addAlwaysVisibleParticle(
                        Infinium.STAR_HEAD,
                        x, y + 0.3, z,
                        (RNG.nextDouble() - 0.5) * 0.4,
                        0.2 + RNG.nextDouble() * 0.3,
                        (RNG.nextDouble() - 0.5) * 0.4
                    );
                }

                // 2. Shockwave dust ring radiating outwards
                int ringPoints = 20;
                for (int i = 0; i < ringPoints; i++) {
                    double angle = (Math.PI * 2 / ringPoints) * i;
                    double vx = Math.cos(angle) * 0.35;
                    double vz = Math.sin(angle) * 0.35;
                    mc.level.addAlwaysVisibleParticle(
                        Infinium.STAR_TRAIL,
                        x + Math.cos(angle) * 0.5, y + 0.1, z + Math.sin(angle) * 0.5,
                        vx, 0.05, vz
                    );
                    mc.level.addParticle(
                        ParticleTypes.POOF,
                        x + Math.cos(angle) * 0.5, y + 0.1, z + Math.sin(angle) * 0.5,
                        vx * 0.8, 0.02, vz * 0.8
                    );
                }

                // 3. Smoke column
                for (int i = 0; i < 4; i++) {
                    mc.level.addParticle(
                        ParticleTypes.CAMPFIRE_COSY_SMOKE,
                        x + (RNG.nextDouble() - 0.5) * 0.4,
                        y + 0.1,
                        z + (RNG.nextDouble() - 0.5) * 0.4,
                        (RNG.nextDouble() - 0.5) * 0.02,
                        0.08 + RNG.nextDouble() * 0.06,
                        (RNG.nextDouble() - 0.5) * 0.02
                    );
                }

                // 4. If dropped loot: celestial glint burst
                if (loot) {
                    for (int i = 0; i < 12; i++) {
                        mc.level.addParticle(
                            ParticleTypes.END_ROD,
                            x + (RNG.nextDouble() - 0.5) * 0.8,
                            y + 0.4 + RNG.nextDouble() * 0.5,
                            z + (RNG.nextDouble() - 0.5) * 0.8,
                            (RNG.nextDouble() - 0.5) * 0.15,
                            0.1 + RNG.nextDouble() * 0.15,
                            (RNG.nextDouble() - 0.5) * 0.15
                        );
                        mc.level.addParticle(
                            ParticleTypes.ENCHANT,
                            x, y + 0.5, z,
                            (RNG.nextDouble() - 0.5) * 1.5,
                            0.5 + RNG.nextDouble() * 0.8,
                            (RNG.nextDouble() - 0.5) * 1.5
                        );
                    }
                }
            });
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> {
            currentPhase = MeteorShower.PHASE_IDLE;
            ticksRemaining = 0;
            stopAmbientSound();
            METEORS.clear();
        });
    }

    private static void startAmbientSound(Minecraft mc) {
        if (ambientSoundInstance == null || ambientSoundInstance.isStopped()) {
            ambientSoundInstance = new MeteorAmbientSoundInstance();
            mc.getSoundManager().play(ambientSoundInstance);
        }
    }

    private static void stopAmbientSound() {
        if (ambientSoundInstance != null) {
            ambientSoundInstance.cancel();
            ambientSoundInstance = null;
        }
    }

    public static boolean isActive() {
        return currentPhase == MeteorShower.PHASE_ACTIVE;
    }

    public static int getPhase() {
        return currentPhase;
    }

    public static int getTicksRemaining() {
        return ticksRemaining;
    }

    public static void tick(Minecraft mc, ClientLevel level) {
        if (mc.isPaused() || level == null) return;

        // Ambient sound check
        if (isActive() && (ambientSoundInstance == null || ambientSoundInstance.isStopped())) {
            startAmbientSound(mc);
        }

        // Advance client falling meteors
        for (Iterator<ClientFallingMeteor> it = METEORS.iterator(); it.hasNext(); ) {
            ClientFallingMeteor m = it.next();
            m.x += m.vx;
            m.y += m.vy;
            m.z += m.vz;
            m.ticksLeft--;

            // Render high-intensity streak
            level.addAlwaysVisibleParticle(Infinium.STAR_HEAD, m.x, m.y, m.z, 0, 0, 0);
            for (int k = 1; k <= 3; k++) {
                double frac = k * 0.25;
                level.addAlwaysVisibleParticle(
                    Infinium.STAR_TRAIL,
                    m.x - m.vx * frac,
                    m.y - m.vy * frac,
                    m.z - m.vz * frac,
                    0, 0, 0
                );
            }

            if (m.dropsLoot) {
                level.addParticle(
                    ParticleTypes.END_ROD,
                    m.x, m.y, m.z,
                    (RNG.nextDouble() - 0.5) * 0.02,
                    (RNG.nextDouble() - 0.5) * 0.02,
                    (RNG.nextDouble() - 0.5) * 0.02
                );
            }

            if (m.ticksLeft <= 0) {
                it.remove();
            }
        }
    }

    private MeteorShowerClient() {}
}
