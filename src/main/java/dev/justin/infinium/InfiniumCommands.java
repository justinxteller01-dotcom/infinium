package dev.justin.infinium;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.justin.infinium.dungeon.DungeonReset;
import dev.justin.infinium.loot.LootTuning;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** Server commands. /infinium settings stays a client command and is not registered here. Permission level 2. */
public final class InfiniumCommands {
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> dispatcher.register(
                Commands.literal("infinium")
                        .then(Commands.literal("loot")
                                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .then(Commands.literal("reload").executes(ctx -> {
                                    LootTuning.load();
                                    DungeonLevels.load();   // R5c roguelike/tables keys
                                    ctx.getSource().sendSuccess(() -> Component.literal("Reloaded config/infinium_dungeon_loot.json."), false);
                                    return 1;
                                })))
                        .then(Commands.literal("dungeon")
                                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .then(Commands.literal("list").executes(ctx -> {
                                    var all = DungeonReset.list();
                                    if (all.isEmpty()) ctx.getSource().sendSuccess(() -> Component.literal("No dungeons recorded yet."), false);
                                    for (String line : all) ctx.getSource().sendSuccess(() -> Component.literal(line), false);
                                    return all.size();
                                }))
                                .then(Commands.literal("info").executes(ctx -> {
                                    String msg = DungeonReset.infoNearest(ctx.getSource().getLevel(), BlockPos.containing(ctx.getSource().getPosition()));
                                    ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
                                    return 1;
                                }))
                                .then(Commands.literal("reset")
                                        .executes(ctx -> reset(ctx.getSource(), null))
                                        .then(Commands.argument("id", StringArgumentType.greedyString()).executes(ctx ->
                                                reset(ctx.getSource(), StringArgumentType.getString(ctx, "id"))))))
                        .then(Commands.literal("meteor_shower")
                                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .then(Commands.literal("start").executes(ctx -> {
                                    dev.justin.infinium.world.MeteorShower.forceStart(ctx.getSource().getLevel().getServer());
                                    ctx.getSource().sendSuccess(() -> Component.literal("Meteor shower initiated."), true);
                                    return 1;
                                }))
                                .then(Commands.literal("stop").executes(ctx -> {
                                    dev.justin.infinium.world.MeteorShower.forceStop(ctx.getSource().getLevel().getServer());
                                    ctx.getSource().sendSuccess(() -> Component.literal("Meteor shower stopped."), true);
                                    return 1;
                                }))
                                .then(Commands.literal("status").executes(ctx -> {
                                    String st = dev.justin.infinium.world.MeteorShower.status(ctx.getSource().getLevel().getServer());
                                    ctx.getSource().sendSuccess(() -> Component.literal(st), false);
                                    return 1;
                                })))));
    }

    private static int reset(CommandSourceStack src, String id) {
        String msg = DungeonReset.forceReset(src.getLevel(), BlockPos.containing(src.getPosition()), id);
        src.sendSuccess(() -> Component.literal(msg), true);
        return 1;
    }

    private InfiniumCommands() {}
}
