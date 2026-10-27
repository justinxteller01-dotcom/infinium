package dev.justin.infinium.world;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.justin.infinium.Infinium;
import dev.justin.infinium.InfiniumAdvancements;
import dev.justin.infinium.InfiniumStyle;
import dev.justin.infinium.StyleTemplates;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Server-side manager for the server-wide Meteor Shower Event.
 * Occurs only at night, every 10-15 in-game days.
 * 2-minute warning siren -> 12-second countdown -> active shower with real falling meteors in a 160m bubble around players.
 */
public final class MeteorShower {
    public static final int PHASE_IDLE = 0;
    public static final int PHASE_WARNING = 1;
    public static final int PHASE_COUNTDOWN = 2;
    public static final int PHASE_ACTIVE = 3;

    public static final int WARNING_TICKS = 2400;   // 2 minutes
    public static final int COUNTDOWN_TICKS = 240;  // 12 seconds
    public static final int ACTIVE_TICKS = 6500;    // ~5.4 minutes

    private static final RandomSource RNG = RandomSource.create();
    private static MinecraftServer server;
    private static int phase = PHASE_IDLE;
    private static int phaseTicksLeft = 0;
    private static long nextShowerDay = -1;
    private static long lastShowerDay = -1;
    private static int nextMeteorId = 1;

    private static final Map<UUID, Integer> PLAYER_SPAWN_TIMERS = new HashMap<>();
    private static final List<ServerFallingMeteor> FALLING_METEORS = new ArrayList<>();
    private static final Set<UUID> ACTIVE_PARTICIPANTS = new HashSet<>();

    public static class ServerFallingMeteor {
        public final int id;
        public final ServerLevel level;
        public final double startX, startY, startZ;
        public final double targetX, targetY, targetZ;
        public final int totalTicks;
        public int remainingTicks;
        public final boolean dropsLoot;

        public ServerFallingMeteor(int id, ServerLevel level, double sx, double sy, double sz, double tx, double ty, double tz, int total, boolean loot) {
            this.id = id; this.level = level;
            this.startX = sx; this.startY = sy; this.startZ = sz;
            this.targetX = tx; this.targetY = ty; this.targetZ = tz;
            this.totalTicks = total; this.remainingTicks = total;
            this.dropsLoot = loot;
        }
    }

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(MeteorShowerStatePayload.TYPE, MeteorShowerStatePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MeteorSpawnPayload.TYPE, MeteorSpawnPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MeteorImpactPayload.TYPE, MeteorImpactPayload.CODEC);

        ServerLifecycleEvents.SERVER_STARTED.register(s -> {
            server = s;
            load();
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> {
            save();
            FALLING_METEORS.clear();
            PLAYER_SPAWN_TIMERS.clear();
            server = null;
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, srv) -> {
            ServerPlayNetworking.send(handler.getPlayer(), new MeteorShowerStatePayload(phase, phaseTicksLeft));
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, srv) -> {
            PLAYER_SPAWN_TIMERS.remove(handler.getPlayer().getUUID());
        });
        ServerTickEvents.END_SERVER_TICK.register(MeteorShower::tick);
    }

    public static int getPhase() {
        return phase;
    }

    public static int getPhaseTicksLeft() {
        return phaseTicksLeft;
    }

    public static void tick(MinecraftServer srv) {
        server = srv;
        ServerLevel overworld = srv.getLevel(Level.OVERWORLD);
        if (overworld == null) return;

        long dayTime = overworld.getOverworldClockTime();
        long currentDay = dayTime / 24000L;
        long timeInDay = dayTime % 24000L;

        float showerChance = dev.justin.infinium.InfiniumConfig.getMeteorShowerChance();
        if (showerChance <= 0.001f) {
            if (phase != PHASE_IDLE) stopShower(overworld);
            return;
        }

        if (nextShowerDay < 0) {
            int minD = dev.justin.infinium.InfiniumConfig.getMeteorMinDays();
            int range = Math.max(1, dev.justin.infinium.InfiniumConfig.getMeteorMaxDays() - minD + 1);
            int baseDays = minD + RNG.nextInt(range);
            int scheduledDays = Math.max(1, Math.round(baseDays / showerChance));
            nextShowerDay = currentDay + scheduledDays;
            save();
        }

        switch (phase) {
            case PHASE_IDLE -> {
                // If scheduled day reached and night begins (13000 to 13500)
                if (currentDay >= nextShowerDay && timeInDay >= 13000L && timeInDay <= 13500L) {
                    startWarning(overworld);
                }
            }
            case PHASE_WARNING -> {
                // If daylight returns unexpectedly (slept or /time set day), cancel event cleanly
                if (timeInDay < 12500L || timeInDay > 23200L) {
                    stopShower(overworld);
                    return;
                }
                phaseTicksLeft--;
                if (phaseTicksLeft == 1200) {
                    broadcastActionBar(Component.literal("§d[Warning] Meteor shower beginning in 60s - seek overhead cover!"));
                } else if (phaseTicksLeft == 600) {
                    broadcastActionBar(Component.literal("§c[Warning] Atmospheric breach imminent in 30s!"));
                } else if (phaseTicksLeft <= COUNTDOWN_TICKS) {
                    startCountdown(overworld);
                } else if (phaseTicksLeft % 100 == 0) {
                    broadcastState();
                }
            }
            case PHASE_COUNTDOWN -> {
                if (timeInDay < 12500L || timeInDay > 23200L) {
                    stopShower(overworld);
                    return;
                }
                phaseTicksLeft--;
                if (phaseTicksLeft == 200) broadcastActionBar(Component.literal("§c§l10..."));
                else if (phaseTicksLeft == 100) broadcastActionBar(Component.literal("§c§l5..."));
                else if (phaseTicksLeft == 80) broadcastActionBar(Component.literal("§c§l4..."));
                else if (phaseTicksLeft == 60) broadcastActionBar(Component.literal("§c§l3..."));
                else if (phaseTicksLeft == 40) broadcastActionBar(Component.literal("§c§l2..."));
                else if (phaseTicksLeft == 20) broadcastActionBar(Component.literal("§4§l1..."));
                else if (phaseTicksLeft <= 0) {
                    startActiveShower(overworld);
                }
            }
            case PHASE_ACTIVE -> {
                if (timeInDay < 12500L || timeInDay > 23200L) {
                    stopShower(overworld);
                    return;
                }
                phaseTicksLeft--;
                if (phaseTicksLeft <= 0) {
                    stopShower(overworld);
                    return;
                }
                if (phaseTicksLeft % 200 == 0) {
                    broadcastState();
                }

                // Spawn real meteors in 160m bubble around players
                for (ServerPlayer player : overworld.players()) {
                    ACTIVE_PARTICIPANTS.add(player.getUUID());
                    int timer = PLAYER_SPAWN_TIMERS.getOrDefault(player.getUUID(), 30 + RNG.nextInt(60));
                    if (--timer <= 0) {
                        PLAYER_SPAWN_TIMERS.put(player.getUUID(), 90 + RNG.nextInt(35));
                        spawnMeteorNearPlayer(overworld, player);
                    } else {
                        PLAYER_SPAWN_TIMERS.put(player.getUUID(), timer);
                    }
                }

                // Tick falling meteors
                for (Iterator<ServerFallingMeteor> it = FALLING_METEORS.iterator(); it.hasNext(); ) {
                    ServerFallingMeteor m = it.next();
                    m.remainingTicks--;
                    if (m.remainingTicks <= 0) {
                        handleImpact(m);
                        it.remove();
                    }
                }
            }
        }
    }

    private static void startWarning(ServerLevel overworld) {
        phase = PHASE_WARNING;
        phaseTicksLeft = WARNING_TICKS;
        broadcastState();
        broadcastTitle(
            Component.literal("§d§lCOSMIC EVENT DETECTED"),
            Component.literal("§7Sagittarius A* debris entering atmosphere... (2:00)"),
            10, 70, 20
        );
        broadcastMessage(Component.literal("§8[§5Infinium§8] §dAstronomical anomaly detected! Remnants of the Sagittarius A* collapse are descending towards the surface in 2 minutes."));
        save();
    }

    private static void startCountdown(ServerLevel overworld) {
        phase = PHASE_COUNTDOWN;
        phaseTicksLeft = COUNTDOWN_TICKS;
        broadcastState();
        broadcastActionBar(Component.literal("§c§lImpact Imminent: 12s"));
    }

    private static void startActiveShower(ServerLevel overworld) {
        phase = PHASE_ACTIVE;
        phaseTicksLeft = ACTIVE_TICKS;
        ACTIVE_PARTICIPANTS.clear();
        lastShowerDay = overworld.getOverworldClockTime() / 24000L;
        int minD = dev.justin.infinium.InfiniumConfig.getMeteorMinDays();
        int range = Math.max(1, dev.justin.infinium.InfiniumConfig.getMeteorMaxDays() - minD + 1);
        int baseDays = minD + RNG.nextInt(range);
        float sc = dev.justin.infinium.InfiniumConfig.getMeteorShowerChance();
        int scheduledDays = sc > 0.001f ? Math.max(1, Math.round(baseDays / sc)) : 999999;
        nextShowerDay = lastShowerDay + scheduledDays;
        broadcastState();
        broadcastTitle(
            Component.literal("§d§lMETEOR SHOWER IN PROGRESS"),
            Component.literal("§fSagittarius A* cosmic fragments descending!"),
            10, 70, 20
        );
        broadcastMessage(Component.literal("§8[§5Infinium§8] §5The celestial storm has arrived! Meteors are bombarding the surface."));
        save();
    }

    private static void stopShower(ServerLevel overworld) {
        phase = PHASE_IDLE;
        phaseTicksLeft = 0;
        FALLING_METEORS.clear();
        broadcastState();
        broadcastMessage(Component.literal("§8[§5Infinium§8] §7The Sagittarius A* meteor shower has concluded."));
        for (ServerPlayer player : overworld.players()) {
            if (player.isAlive()) {
                InfiniumAdvancements.award(player, "survive_meteor_shower");
            }
        }
        for (UUID uid : ACTIVE_PARTICIPANTS) {
            ServerPlayer p = overworld.getServer().getPlayerList().getPlayer(uid);
            if (p != null && p.isAlive()) {
                InfiniumAdvancements.award(p, "survive_meteor_shower");
            }
        }
        ACTIVE_PARTICIPANTS.clear();
        save();
    }

    private static void spawnMeteorNearPlayer(ServerLevel level, ServerPlayer player) {
        // Distance within configurable bubble
        double bubble = (double) dev.justin.infinium.InfiniumConfig.getMeteorBubbleRadius();
        double dist = 15.0 + RNG.nextDouble() * Math.max(10.0, bubble - 20.0);
        double angle = RNG.nextDouble() * Math.PI * 2;
        double tx = player.getX() + Math.cos(angle) * dist;
        double tz = player.getZ() + Math.sin(angle) * dist;
        int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) tx, (int) tz);
        if (groundY <= level.getMinY()) return;

        double ty = groundY;
        // Ballistic angle: spawn 45-70 blocks up and offset horizontally 25-45 blocks
        double slantDir = RNG.nextDouble() * Math.PI * 2;
        double slantDist = 25.0 + RNG.nextDouble() * 20.0;
        double sx = tx + Math.cos(slantDir) * slantDist;
        double sz = tz + Math.sin(slantDir) * slantDist;
        double sy = ty + 45.0 + RNG.nextDouble() * 25.0;

        int flightTicks = 24 + RNG.nextInt(8); // ~1.2 - 1.6 seconds
        boolean dropsLoot = RNG.nextFloat() < dev.justin.infinium.InfiniumConfig.getMeteorLootChance();

        int id = nextMeteorId++;
        ServerFallingMeteor meteor = new ServerFallingMeteor(id, level, sx, sy, sz, tx, ty, tz, flightTicks, dropsLoot);
        FALLING_METEORS.add(meteor);

        MeteorSpawnPayload spawnPayload = new MeteorSpawnPayload(id, sx, sy, sz, tx, ty, tz, flightTicks, dropsLoot);
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(tx, ty, tz) <= 256.0 * 256.0) {
                ServerPlayNetworking.send(p, spawnPayload);
            }
        }
    }

    private static void handleImpact(ServerFallingMeteor m) {
        ServerLevel level = m.level;
        double tx = m.targetX;
        double ty = m.targetY;
        double tz = m.targetZ;

        // Send impact packet to nearby players within 192 blocks
        MeteorImpactPayload impactPayload = new MeteorImpactPayload(tx, ty, tz, m.dropsLoot);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(tx, ty, tz) <= 192.0 * 192.0) {
                ServerPlayNetworking.send(player, impactPayload);
            }
        }

        // Damage check: player within 4.0 blocks horizontal and 3.0 blocks vertical
        for (ServerPlayer player : level.players()) {
            if (player.isCreative() || player.isSpectator()) continue;
            double dx = player.getX() - tx;
            double dz = player.getZ() - tz;
            double dy = Math.abs(player.getY() - ty);
            if (dx * dx + dz * dz <= 16.0 && dy <= 3.0) {
                // Overhead cover check: roof, ceiling, cave protects player completely
                if (isSheltered(level, player.blockPosition())) continue;

                // Line of sight check between impact point and player eye
                Vec3 from = new Vec3(tx, ty + 0.5, tz);
                Vec3 to = player.getEyePosition();
                HitResult hr = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
                if (hr.getType() == HitResult.Type.BLOCK) continue;

                // Direct damage ignoring armor, enchantments, and all protection
                float dmg = dev.justin.infinium.InfiniumConfig.getMeteorDirectDamage();
                if (dmg > 0.0F) {
                    float prev = player.getHealth();
                    player.invulnerableTime = 0;
                    player.hurtServer(level, level.damageSources().generic(), dmg);
                    if (player.isAlive() && player.getHealth() > prev - dmg) {
                        player.setHealth(Math.max(0.0F, prev - dmg));
                    }
                    player.invulnerableTime = 10;
                }
            }
        }

        // Loot drop
        if (m.dropsLoot) {
            int minF = dev.justin.infinium.InfiniumConfig.getMeteorMinFragments();
            int maxF = dev.justin.infinium.InfiniumConfig.getMeteorMaxFragments();
            int fragCount = minF + (maxF > minF ? RNG.nextInt(maxF - minF + 1) : 0);
            if (fragCount > 0) {
                ItemEntity frag = new ItemEntity(level, tx, ty + 0.35, tz, new ItemStack(Infinium.STRANGE_FRAGMENTS, fragCount));
                frag.setDefaultPickUpDelay();
                frag.setDeltaMovement((RNG.nextDouble() - 0.5) * 0.1, 0.2, (RNG.nextDouble() - 0.5) * 0.1);
                level.addFreshEntity(frag);
            }

            // Configurable template drop rates
            float tRoll = RNG.nextFloat();
            float wAstral = 3.0f * (float) dev.justin.infinium.InfiniumConfig.getTemplateRateAstral();
            float wCyber = 3.0f * (float) dev.justin.infinium.InfiniumConfig.getTemplateRateCybernetic();
            float wT1 = wAstral + wCyber;
            float t1Chance = wT1 > 0 ? dev.justin.infinium.InfiniumConfig.getMeteorTier1TemplateChance() * (wT1 / 6.0f) : 0.0f;

            float wSing = 1.0f * (float) dev.justin.infinium.InfiniumConfig.getTemplateRateSingularity();
            float wPsycho = 1.0f * (float) dev.justin.infinium.InfiniumConfig.getTemplateRateCyberpsycho();
            float wT2 = wSing + wPsycho;
            float t2Chance = wT2 > 0 ? dev.justin.infinium.InfiniumConfig.getMeteorTier2TemplateChance() * (wT2 / 2.0f) : 0.0f;

            Item template = null;
            if (t1Chance > 0 && tRoll < t1Chance) {
                template = (RNG.nextFloat() * wT1 < wAstral) ? StyleTemplates.TEMPLATES.get(InfiniumStyle.ASTRAL) : StyleTemplates.TEMPLATES.get(InfiniumStyle.CYBERNETIC);
            } else if (t2Chance > 0 && tRoll < t1Chance + t2Chance) {
                template = (RNG.nextFloat() * wT2 < wSing) ? StyleTemplates.TEMPLATES.get(InfiniumStyle.SINGULARITY) : StyleTemplates.TEMPLATES.get(InfiniumStyle.CYBERPSYCHO);
            }
            if (template != null) {
                ItemEntity tEntity = new ItemEntity(level, tx, ty + 0.45, tz, new ItemStack(template));
                tEntity.setDefaultPickUpDelay();
                tEntity.setDeltaMovement((RNG.nextDouble() - 0.5) * 0.08, 0.25, (RNG.nextDouble() - 0.5) * 0.08);
                level.addFreshEntity(tEntity);
            }
        }
    }

    public static boolean isSheltered(ServerLevel level, BlockPos pos) {
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ());
        if (surfaceY > pos.getY() + 1) return true;
        return !level.canSeeSkyFromBelowWater(pos);
    }

    public static void forceStart(MinecraftServer srv) {
        server = srv;
        ServerLevel overworld = srv.getLevel(Level.OVERWORLD);
        if (overworld == null) return;
        startWarning(overworld);
    }

    public static void forceStop(MinecraftServer srv) {
        server = srv;
        ServerLevel overworld = srv.getLevel(Level.OVERWORLD);
        if (overworld != null) {
            stopShower(overworld);
        } else {
            phase = PHASE_IDLE;
            phaseTicksLeft = 0;
            FALLING_METEORS.clear();
            broadcastState();
        }
    }

    public static String status(MinecraftServer srv) {
        ServerLevel overworld = srv.getLevel(Level.OVERWORLD);
        if (overworld == null) return "Overworld not loaded.";
        long dayTime = overworld.getOverworldClockTime();
        long currentDay = dayTime / 24000L;
        long timeInDay = dayTime % 24000L;
        String pName = switch (phase) {
            case PHASE_WARNING -> "WARNING (" + (phaseTicksLeft / 20) + "s left)";
            case PHASE_COUNTDOWN -> "COUNTDOWN (" + (phaseTicksLeft / 20) + "s left)";
            case PHASE_ACTIVE -> "ACTIVE (" + (phaseTicksLeft / 20) + "s left)";
            default -> "IDLE (Next shower scheduled day " + nextShowerDay + ", current day " + currentDay + ")";
        };
        return "Meteor Shower: " + pName + " [Day " + currentDay + ", Time " + timeInDay + "]";
    }

    private static void sendTitle(ServerPlayer player, Component title, Component subtitle, int in, int stay, int out) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(in, stay, out));
        if (title != null) player.connection.send(new ClientboundSetTitleTextPacket(title));
        if (subtitle != null) player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
    }

    private static void sendActionBar(ServerPlayer player, Component text) {
        player.connection.send(new ClientboundSetActionBarTextPacket(text));
    }

    private static void broadcastTitle(Component title, Component subtitle, int in, int stay, int out) {
        if (server == null) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            sendTitle(p, title, subtitle, in, stay, out);
        }
    }

    private static void broadcastActionBar(Component text) {
        if (server == null) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            sendActionBar(p, text);
        }
    }

    private static void broadcastMessage(Component msg) {
        if (server == null) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.sendSystemMessage(msg);
        }
    }

    private static void broadcastState() {
        if (server == null) return;
        MeteorShowerStatePayload pl = new MeteorShowerStatePayload(phase, phaseTicksLeft);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(p, pl);
        }
    }

    private static Path configPath() {
        return server.getWorldPath(LevelResource.ROOT).resolve("infinium_meteor_shower.json");
    }

    private static void load() {
        try {
            Path p = configPath();
            if (!Files.exists(p)) return;
            String str = Files.readString(p, StandardCharsets.UTF_8);
            JsonObject obj = JsonParser.parseString(str).getAsJsonObject();
            if (obj.has("nextShowerDay")) nextShowerDay = obj.get("nextShowerDay").getAsLong();
            if (obj.has("lastShowerDay")) lastShowerDay = obj.get("lastShowerDay").getAsLong();
            if (obj.has("phase")) phase = obj.get("phase").getAsInt();
            if (obj.has("phaseTicksLeft")) phaseTicksLeft = obj.get("phaseTicksLeft").getAsInt();
        } catch (Throwable t) {
            Infinium.LOG.warn("Failed to load infinium_meteor_shower.json: {}", t.getMessage());
        }
    }

    private static void save() {
        if (server == null) return;
        try {
            Path p = configPath();
            JsonObject obj = new JsonObject();
            obj.addProperty("nextShowerDay", nextShowerDay);
            obj.addProperty("lastShowerDay", lastShowerDay);
            obj.addProperty("phase", phase);
            obj.addProperty("phaseTicksLeft", phaseTicksLeft);
            Files.writeString(p, obj.toString(), StandardCharsets.UTF_8);
        } catch (Throwable t) {
            Infinium.LOG.warn("Failed to save infinium_meteor_shower.json: {}", t.getMessage());
        }
    }

    private MeteorShower() {}
}
