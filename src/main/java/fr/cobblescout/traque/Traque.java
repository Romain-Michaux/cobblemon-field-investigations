package fr.cobblescout.traque;

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

// CobbleScout - Traque : enquête de terrain sur un Pokémon inconnu (voir Moteur pour le déroulé).
public class Traque implements ModInitializer {
    private int compteur = 0;

    @Override
    public void onInitialize() {
        Moteur.charger();
        Contenu.enregistrer();

        PayloadTypeRegistry.playS2C().register(Paquet.TYPE, Paquet.CODEC);
        PayloadTypeRegistry.playC2S().register(Paquet.TYPE, Paquet.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Paquet.TYPE, (paquet, ctx) -> {
            ServerPlayer joueur = ctx.player();
            CompoundTag d = paquet.donnees();
            ctx.server().execute(() -> {
                try {
                    switch (paquet.genre()) {
                        case "ouvrir_enquete" -> Moteur.ouvrirEnquete(joueur);
                        case "lancer" -> Moteur.lancer(joueur, d.getString("espece"));
                        case "abandonner" -> Moteur.abandonner(joueur);
                        case "analyse" -> Moteur.resultatAnalyse(joueur, d.getBoolean("succes"));
                        case "prelevement" -> Moteur.resultatPrelevement(joueur, d.getBoolean("succes"), d.getBoolean("parfait"));
                        default -> {}
                    }
                } catch (Exception e) {
                    System.err.println("[Field Investigations] Erreur sur l'action " + paquet.genre() + " : " + e);
                    e.printStackTrace();
                }
            });
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, serveur) -> serveur.execute(() -> Moteur.synchroniser(handler.player)));

        ServerLifecycleEvents.SERVER_STOPPED.register(s -> Moteur.arret());
        ServerTickEvents.END_SERVER_TICK.register(s -> {
            if (++compteur < 10) return;
            compteur = 0;
            for (ServerPlayer joueur : new ArrayList<>(s.getPlayerList().getPlayers())) {
                try {
                    Moteur.suivre(joueur);
                } catch (Exception e) {
                    System.err.println("[Field Investigations] Erreur de suivi : " + e);
                    e.printStackTrace();
                }
            }
        });
        CobblemonEvents.POKEMON_CAPTURED.subscribe(Priority.NORMAL, (Consumer<PokemonCapturedEvent>) Moteur::capture);

        CommandRegistrationCallback.EVENT.register((dispatcher, acces, environnement) -> dispatcher.register(Commands.literal("hunt")
                .then(Commands.literal("abandon").executes(ctx -> {
                    Moteur.abandonner(ctx.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("gear").requires(src -> src.hasPermission(2)).executes(ctx -> {
                    ServerPlayer j = ctx.getSource().getPlayerOrException();
                    j.getInventory().placeItemBackInInventory(new ItemStack(Contenu.KIT));
                    j.getInventory().placeItemBackInInventory(new ItemStack(Contenu.FIOLE, 8));
                    j.getInventory().placeItemBackInInventory(new ItemStack(net.minecraft.world.item.Items.BRUSH));
                    return 1;
                }))
                .then(Commands.literal("reset").requires(src -> src.hasPermission(2)).executes(ctx -> {
                    Moteur.reinitialiser(ctx.getSource().getPlayerOrException());
                    ctx.getSource().sendSuccess(() -> Component.translatable("cobblemon_field_investigations.commande.reset").withStyle(ChatFormatting.GREEN), false);
                    return 1;
                }))
                .then(Commands.literal("reveal").requires(src -> src.hasPermission(2)).executes(ctx -> {
                    String texte = Moteur.reveler(ctx.getSource().getPlayerOrException());
                    ctx.getSource().sendSuccess(() -> Component.literal(texte).withStyle(ChatFormatting.LIGHT_PURPLE), false);
                    return 1;
                }))));
    }
}
