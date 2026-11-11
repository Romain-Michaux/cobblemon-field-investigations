package fr.cobblescout.traque;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.cobblemon.mod.common.api.events.pokemon.PokemonCapturedEvent;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

// Fin d'une enquête : capture ou KO, récompenses, expérience, archives.
final class Cloture {
    private Cloture() {}

    static void capture(PokemonCapturedEvent evt) {
        UUID capture = evt.getPokemon().getUuid();
        for (Map.Entry<UUID, Dossier> en : new ArrayList<>(Moteur.etat.dossiers.entrySet())) {
            Dossier d = en.getValue();
            if (!capture.equals(d.pokemon)) continue;
            ServerPlayer auteur = evt.getPlayer();
            List<ServerPlayer> presents = Moteur.enLigne(d, auteur.server);
            Moteur.ENTITES.remove(en.getKey());
            if (Moteur.membres(d).contains(auteur.getUUID())) {
                resoudre(auteur, d, true); // capturée par le chef ou par un coéquipier : l'enquête est résolue pour toute l'équipe
            } else if (presents.isEmpty()) {
                Moteur.etat.dossiers.remove(en.getKey());
                Moteur.sauver();
            } else {
                finir(presents.get(0), d, Messages.tr("annonce.trop_tard.titre"), Messages.tr("annonce.trop_tard.texte"), 0xE8543A);
            }
            return;
        }
    }

    // L'enquête est résolue : chaque enquêteur présent reçoit son butin et son expérience, puis le dossier est classé.
    static void resoudre(ServerPlayer joueur, Dossier d, boolean capture) {
        List<ServerPlayer> presents = Moteur.enLigne(d, joueur.server);
        for (ServerPlayer m : presents) recompenser(m, d, capture);
        // Chacun note une enquête de plus avec chacun de ses coéquipiers.
        for (ServerPlayer m : presents) {
            for (ServerPlayer autre : presents) {
                if (autre != m) Moteur.profil(m.getUUID()).partenaires.merge(autre.getUUID(), 1, Integer::sum);
            }
        }
        cloturer(joueur, d);
    }

    // Enquête menée à son terme (cible capturée ou mise K.O.) : le butin est tiré dans les tables de butin du mod.
    // Plus l'enquête a d'étoiles, plus la table est fournie ; la rareté de l'espèce et les paliers ajoutent leur part.
    private static void recompenser(ServerPlayer joueur, Dossier d, boolean capture) {
        int total = Moteur.etat.reussies.merge(joueur.getUUID(), 1, Integer::sum);
        int etoiles = Moteur.etoiles(d);
        // Expérience : dix points par étoile, plus la rareté de l'espèce, le type d'enquête et la nouveauté.
        Profil profil = Moteur.profil(joueur.getUUID());
        int rangAvant = Moteur.rang(profil.xp);
        int gain = 10 * etoiles + new int[] {0, 5, 10, 20}[Math.max(0, Signalement.CATEGORIES.indexOf(d.categorie))] + (d.special != null ? 10 : 0) + (d.nouvelle ? 5 : 0);
        profil.xp += gain;
        Fiche fiche = new Fiche();
        fiche.espece = d.espece;
        fiche.special = d.special;
        fiche.categorie = d.categorie;
        fiche.etoiles = etoiles;
        fiche.xp = gain;
        fiche.capture = capture;
        fiche.date = System.currentTimeMillis();
        profil.historique.add(fiche);
        while (profil.historique.size() > 200) profil.historique.remove(0);
        int rangApres = Moteur.rang(profil.xp);
        if (rangApres >= 3) Moteur.progres(joueur, "rang_inspecteur");
        boolean palier = Moteur.reglages.palierBonus > 0 && total % Moteur.reglages.palierBonus == 0;
        List<ItemStack> gains = new ArrayList<>(butin(joueur, "traque/etoiles_" + etoiles));
        gains.addAll(butin(joueur, "traque/rarete_" + d.categorie.replace('-', '_')));
        if (palier) gains.addAll(butin(joueur, "traque/palier"));
        if (d.special != null) {
            gains.addAll(butin(joueur, "traque/speciale"));
            if (Moteur.est(d, "craintive") || Moteur.est(d, "migratrice")) gains.addAll(butin(joueur, "traque/speciale"));
            gains.addAll(butin(joueur, "traque/type_" + d.special)); // table propre au type, vide si elle n'existe pas
            Moteur.progres(joueur, "enquete_speciale");
        }
        Moteur.progres(joueur, "premiere_enquete");
        if (etoiles >= 5) Moteur.progres(joueur, "cinq_etoiles");
        if (total >= 10) Moteur.progres(joueur, "dix_enquetes");
        MutableComponent liste = Component.empty();
        boolean premier = true;
        for (ItemStack pile : gains) {
            if (pile.isEmpty()) continue;
            if (!premier) liste.append(", ");
            premier = false;
            liste.append(pile.getCount() + "× ").append(pile.getHoverName());
            joueur.getInventory().placeItemBackInInventory(pile);
        }
        joueur.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8f, 1f);
        Messages.annonce(joueur, Messages.tr("annonce.resolue.titre", Messages.etoilesTexte(etoiles)),
                Messages.tr("annonce.resolue.texte", Moteur.nomEspece(d.espece), Messages.tr(capture ? "annonce.resolue.capture" : "annonce.resolue.vaincu"), total,
                        palier ? Messages.tr("annonce.resolue.palier") : Component.empty(), premier ? Component.empty() : Messages.tr("annonce.resolue.butin", liste),
                        rangApres > rangAvant ? Messages.tr("annonce.resolue.rang", gain, Messages.tr("rang." + rangApres)) : Messages.tr("annonce.resolue.xp", gain)), 0x6CCB5F);
        if (rangApres > rangAvant) joueur.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1f, 0.8f);
    }

    private static List<ItemStack> butin(ServerPlayer joueur, String table) {
        try {
            ResourceKey<LootTable> cle = ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.fromNamespaceAndPath("cobblemon_field_investigations", table));
            LootTable t = joueur.server.reloadableRegistries().getLootTable(cle);
            LootParams params = new LootParams.Builder(joueur.serverLevel()).withParameter(LootContextParams.ORIGIN, joueur.position())
                    .withOptionalParameter(LootContextParams.THIS_ENTITY, joueur).create(LootContextParamSets.CHEST);
            return t.getRandomItems(params);
        } catch (Exception e) {
            System.err.println("[Field Investigations] Table de butin illisible : " + table + " : " + e);
            return List.of();
        }
    }

    // Classe le dossier pour toute l'équipe : traces retirées, délai avant la prochaine enquête pour chacun.
    static void cloturer(ServerPlayer joueur, Dossier d) {
        if (d.monde.equals(Moteur.dimension(joueur))) Traces.retirerTraces(joueur.serverLevel(), d);
        Moteur.etat.dossiers.remove(d.chef);
        Moteur.ENTITES.remove(d.chef);
        for (UUID u : Moteur.membres(d)) {
            Moteur.SCANS.remove(u);
            Moteur.etat.prochaineEnquete.put(u, System.currentTimeMillis() + Moteur.reglages.minutesEntreEnquetes * 60_000L / (Moteur.rang(Moteur.profil(u).xp) >= 6 ? 2 : 1));
        }
        Moteur.sauver();
        for (ServerPlayer m : Moteur.enLigne(d, joueur.server)) Messages.envoyerEtat(m);
    }

    static void finir(ServerPlayer joueur, Dossier d, Component titre, Component texte, int couleur) {
        cloturer(joueur, d);
        for (ServerPlayer m : Moteur.enLigne(d, joueur.server)) Messages.annonce(m, titre, texte, couleur);
    }
}
