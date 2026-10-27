package dev.justin.infinium.qa;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.justin.infinium.Infinium;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Native Minecraft 26.2 QA & Telemetry Bridge.
 * Replaces incompatible 1.21.x mods (which crash on 26.2's render/entity pipeline).
 * Connects to the local QA endpoint (http://localhost:8080/api/telemetry) to sync:
 *  - Player info (position, eye height, yaw, pitch, dimension, gamemode)
 *  - Block area inspection
 *  - Safe server command execution
 *  - Viewport screenshots
 */
public final class QaTelemetryBridge {
    private static final String TELEMETRY_URL = "http://localhost:8080/api/telemetry";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(500))
            .build();
    private static int tickCounter = 0;
    private static boolean enabled = true;

    public static void register() {
        if (!FabricLoader.getInstance().isDevelopmentEnvironment()) {
            return;
        }
        ServerTickEvents.END_SERVER_TICK.register(QaTelemetryBridge::onServerTick);
    }

    private static void onServerTick(MinecraftServer server) {
        if (!enabled) return;
        // Sync telemetry every 20 ticks (~1 s)
        if (++tickCounter % 20 != 0) return;

        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) return;

        ServerPlayer player = players.get(0);
        JsonObject payload = new JsonObject();
        payload.add("player", serializePlayer(player));

        // Asynchronous non-blocking dispatch to local bridge server
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(TELEMETRY_URL))
                    .timeout(Duration.ofMillis(800))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8))
                    .build();

            HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response.statusCode() == 200) {
                            handleBridgeResponse(server, response.body());
                        }
                    })
                    .exceptionally(ex -> null);
        } catch (Exception ignored) {
        }
    }

    public static JsonObject serializePlayer(ServerPlayer player) {
        JsonObject o = new JsonObject();
        o.addProperty("x", player.getX());
        o.addProperty("y", player.getY());
        o.addProperty("z", player.getZ());
        o.addProperty("eyeHeight", player.getEyeHeight());
        o.addProperty("yaw", player.getYRot());
        o.addProperty("pitch", player.getXRot());
        o.addProperty("dimension", player.level().dimension().identifier().toString());
        o.addProperty("gamemode", player.gameMode.getGameModeForPlayer().getName());
        return o;
    }

    public static JsonArray scanBlocksInArea(Level level, BlockPos from, BlockPos to, int maxBlocks) {
        JsonArray arr = new JsonArray();
        int minX = Math.min(from.getX(), to.getX());
        int maxX = Math.max(from.getX(), to.getX());
        int minY = Math.min(from.getY(), to.getY());
        int maxY = Math.max(from.getY(), to.getY());
        int minZ = Math.min(from.getZ(), to.getZ());
        int maxZ = Math.max(from.getZ(), to.getZ());

        int count = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    JsonObject b = new JsonObject();
                    b.addProperty("x", x);
                    b.addProperty("y", y);
                    b.addProperty("z", z);
                    b.addProperty("id", BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
                    arr.add(b);
                    if (++count >= maxBlocks) return arr;
                }
            }
        }
        return arr;
    }

    public static String executeCommand(MinecraftServer server, String command) {
        try {
            CommandSourceStack stack = server.createCommandSourceStack().withSuppressedOutput();
            server.getCommands().performPrefixedCommand(stack, command);
            return "Executed: " + command;
        } catch (Exception e) {
            return "Command error: " + e.getMessage();
        }
    }

    private static void handleBridgeResponse(MinecraftServer server, String responseBody) {
        // Handle pending commands from QA / agent
        try {
            com.google.gson.JsonObject res = com.google.gson.JsonParser.parseString(responseBody).getAsJsonObject();
            if (res.has("commands") && res.get("commands").isJsonArray()) {
                JsonArray cmds = res.getAsJsonArray("commands");
                for (var elem : cmds) {
                    if (elem.isJsonObject()) {
                        String cmd = elem.getAsJsonObject().get("command").getAsString();
                        server.execute(() -> executeCommand(server, cmd));
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    private QaTelemetryBridge() {}
}
