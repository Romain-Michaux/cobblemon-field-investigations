package io.github.romainmichaux.fieldinvestigations;

import java.util.ArrayList;
import java.util.function.Consumer;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.pokemon.PokemonCapturedEvent;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public class FieldInvestigations implements ModInitializer {
    private int counter = 0;

    @Override
    public void onInitialize() {
        Engine.load();
        ModContent.register();

        PayloadTypeRegistry.playS2C().register(Packet.TYPE, Packet.CODEC);
        PayloadTypeRegistry.playC2S().register(Packet.TYPE, Packet.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Packet.TYPE, (packet, ctx) -> {
            ServerPlayer player = ctx.player();
            CompoundTag d = packet.data();
            ctx.server().execute(() -> {
                try {
                    switch (packet.kind()) {
                        case "open_investigation" -> Reports.openInvestigation(player);
                        case "choose" -> Reports.choose(player, d.getInt("index"));
                        case "start" -> Tracking.launch(player, d.getString("species"));
                        case "abandon" -> Tracking.abandon(player);
                        case "rule_out" -> Engine.ruleOut(player, d.getString("species"));
                        case "analyse" -> Samples.analysisResult(player, d.getBoolean("succeeded"));
                        case "invite" -> Team.invite(player, d.getString("name"));
                        case "answer" -> Team.answer(player, d.getBoolean("accepted"));
                        case "team" -> Messages.sync(player);
                        case "filter" -> Tracking.filterResult(player, d.getBoolean("succeeded"));
                        case "sampling" -> Samples.samplingResult(player, d.getBoolean("succeeded"), d.getBoolean("perfect"));
                        default -> {}
                    }
                } catch (Exception e) {
                    System.err.println("[Field Investigations] Error handling action " + packet.kind() + ": " + e);
                    e.printStackTrace();
                }
            });
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> Messages.sync(handler.player)));

        ServerLifecycleEvents.SERVER_STARTING.register(Engine::loadWorld);
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> Engine.stop());
        ServerTickEvents.END_SERVER_TICK.register(s -> {
            if (++counter < 10) return;
            counter = 0;
            for (ServerPlayer player : new ArrayList<>(s.getPlayerList().getPlayers())) {
                try {
                    Pursuit.follow(player);
                } catch (Exception e) {
                    System.err.println("[Field Investigations] Tracking update error: " + e);
                    e.printStackTrace();
                }
            }
        });
        CobblemonEvents.POKEMON_CAPTURED.subscribe(Priority.NORMAL, (Consumer<PokemonCapturedEvent>) CaseClosing::capture);

        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(Commands.literal("hunt")
                .then(Commands.literal("abandon").executes(ctx -> {
                    Tracking.abandon(ctx.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("gear").requires(src -> src.hasPermission(2)).executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    player.getInventory().placeItemBackInInventory(new ItemStack(ModContent.KIT));
                    player.getInventory().placeItemBackInInventory(new ItemStack(ModContent.VIAL, 8));
                    player.getInventory().placeItemBackInInventory(new ItemStack(net.minecraft.world.item.Items.BRUSH));
                    return 1;
                }))
                .then(Commands.literal("game").requires(src -> src.hasPermission(2))
                        .executes(ctx -> {
                            ctx.getSource().sendSuccess(() -> Component.literal("Field: " + String.join(", ", Genetics.FIELD_GAMES.stream().map(Genetics::commandName).toList())).withStyle(ChatFormatting.AQUA), false);
                            ctx.getSource().sendSuccess(() -> Component.literal("Lab: " + String.join(", ", Genetics.LAB_GAMES.stream().map(Genetics::commandName).toList())).withStyle(ChatFormatting.AQUA), false);
                            return 1;
                        })
                        .then(Commands.argument("id", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .suggests((ctx, b) -> {
                                    return net.minecraft.commands.SharedSuggestionProvider.suggest(Genetics.COMMAND_NAMES.keySet(), b);
                                })
                                .executes(ctx -> {
                                    String id = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "id");
                                    // The command name is expected; the internal game id is accepted too.
                                    if (Engine.tryGame(ctx.getSource().getPlayerOrException(), Genetics.COMMAND_NAMES.getOrDefault(id, id), -1)) return 1;
                                    ctx.getSource().sendFailure(Component.literal("Unknown mini-game: " + id + " (run /hunt game for the list)"));
                                    return 0;
                                })))
                .then(Commands.literal("reset").requires(src -> src.hasPermission(2)).executes(ctx -> {
                    Engine.reset(ctx.getSource().getPlayerOrException());
                    ctx.getSource().sendSuccess(() -> Component.translatable("cobblemon_field_investigations.command.reset").withStyle(ChatFormatting.GREEN), false);
                    return 1;
                }))
                .then(Commands.literal("reveal").requires(src -> src.hasPermission(2)).executes(ctx -> {
                    String text = Engine.reveal(ctx.getSource().getPlayerOrException());
                    ctx.getSource().sendSuccess(() -> Component.literal(text).withStyle(ChatFormatting.LIGHT_PURPLE), false);
                    return 1;
                }))));
    }
}
