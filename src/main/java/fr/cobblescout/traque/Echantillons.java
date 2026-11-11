package fr.cobblescout.traque;

import java.util.List;
import java.util.Random;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.pokemon.Species;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

// Prélèvement d'une trace et analyse d'un échantillon (lancement du mini-jeu, puis son résultat).
final class Echantillons {
    private Echantillons() {}

    public static void analyser(ServerPlayer joueur) {
        Dossier d = Moteur.dossier(joueur);
        ItemStack pile = joueur.getMainHandItem();
        if (!pile.is(Contenu.ECHANTILLON)) return;
        if (!joueur.getInventory().contains(new ItemStack(Contenu.KIT))) {
            Messages.annonce(joueur, "echantillon_kit", 0xF2C93A);
            return;
        }
        CompoundTag donnees = Contenu.donnees(pile);
        if (d == null || d.id != donnees.getLong("enquete") || donnees.getBoolean("analyse")) {
            Messages.annonce(joueur, "trop_ancien", 0xE8543A);
            return;
        }
        Moteur.ANALYSES.put(joueur.getUUID(), Boolean.TRUE);
        Paquet p = Paquet.de("ouvrir");
        p.donnees().putString("ecran", "analyse");
        p.donnees().putString("genre", donnees.getString("genre"));
        String jeuAnalyse = Genetique.jeuAnalyse(donnees.getString("genre"), donnees.getString("jeu"));
        p.donnees().putString("jeu", jeuAnalyse);
        p.donnees().putBoolean("premiere", Moteur.premiereFois(joueur, jeuAnalyse));
        p.donnees().putBoolean("enfoui", donnees.getBoolean("enfoui"));
        p.donnees().putInt("variante", donnees.getInt("variante"));
        p.donnees().putLong("graine", Moteur.HASARD.nextLong());
        p.donnees().putInt("doses", donnees.getInt("doses"));
        Messages.envoyer(joueur, p);
    }

    static void resultatAnalyse(ServerPlayer joueur, boolean succes) {
        if (Moteur.ANALYSES.remove(joueur.getUUID()) == null) return;
        Dossier d = Moteur.dossier(joueur);
        ItemStack pile = joueur.getMainHandItem();
        if (d == null || !pile.is(Contenu.ECHANTILLON)) return;
        CompoundTag donnees = Contenu.donnees(pile);
        if (donnees.getBoolean("analyse") || donnees.getLong("enquete") != d.id) return;
        String genre = donnees.getString("genre");
        Species cible = PokemonSpecies.getByName(d.espece);
        if (succes && cible != null) {
            pile.shrink(1);
            if (!d.epingles.contains(genre)) d.epingles.add(genre);
            Moteur.sauver();
            joueur.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5f, 1.6f);
            Messages.annonce(joueur, "analyse_reussie", 0x6CCB5F, Component.translatable(Genetique.NOMS_MARQUEURS.get(genre)),
                    Genetique.libelle(genre, Genetique.valeur(genre, cible)), d.epingles.size(), Moteur.requis(d));
            Moteur.progres(joueur, "premiere_analyse");
            Messages.synchroniser(joueur);
            return;
        }
        int doses = donnees.getInt("doses") - 1;
        Moteur.faute(joueur, d);
        joueur.playNotifySound(SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.6f, 1.2f);
        if (doses <= 0) {
            pile.shrink(1);
            Messages.annonce(joueur, "echantillon_detruit", 0xE8543A, Messages.etoilesTexte(Moteur.etoiles(d, false)));
            Messages.synchroniser(joueur);
        } else {
            donnees.putInt("doses", doses);
            pile.set(DataComponents.CUSTOM_DATA, CustomData.of(donnees));
            Messages.annonce(joueur, "dose_perdue", 0xE8543A, doses, Messages.etoilesTexte(Moteur.etoiles(d, false)));
            Messages.synchroniser(joueur);
        }
    }

    public static void prelever(ServerPlayer joueur, BlockPos pos) {
        Dossier d = Moteur.dossier(joueur);
        ServerLevel monde = joueur.serverLevel();
        Trace trace = null;
        if (d != null && "PRELEVEMENTS".equals(d.phase)) {
            for (Trace t : d.traces) {
                if (t.posee && !t.recolte && t.px == pos.getX() && t.py == pos.getY() && t.pz == pos.getZ()) trace = t;
            }
        }
        if (trace == null) {
            boolean aQuelquun = false;
            for (Dossier autre : Moteur.etat.dossiers.values()) {
                for (Trace t : autre.traces) aQuelquun |= t.posee && !t.recolte && t.px == pos.getX() && t.py == pos.getY() && t.pz == pos.getZ();
            }
            if (aQuelquun) {
                Messages.annonce(joueur, "trace_autre", 0xF2C93A);
            } else {
                monde.removeBlock(pos, false);
                Messages.annonce(joueur, "trace_ancienne", 0x9AA0A6);
            }
            return;
        }
        if (Moteur.dort(d, monde)) {
            Messages.annonce(joueur, "nocturne_trace", 0xF2C93A);
            return;
        }
        boolean secours = Moteur.rang(joueur) >= 2 && !d.fioleOfferte;
        if (joueur.getInventory().findSlotMatchingItem(new ItemStack(Contenu.FIOLE)) < 0 && !joueur.isCreative() && !secours) {
            Messages.annonce(joueur, "fiole_requise", 0xE8543A);
            return;
        }
        Moteur.PRELEVEMENTS.put(joueur.getUUID(), pos.immutable());
        Paquet p = Paquet.de("ouvrir");
        p.donnees().putString("ecran", "prelevement");
        p.donnees().putString("genre", trace.genre);
        String jeuTerrain = Genetique.jeuPrelevement(trace.genre, trace.enfoui, d.special, new Random(d.graine ^ trace.genre.hashCode()));
        p.donnees().putString("jeu", jeuTerrain);
        p.donnees().putBoolean("premiere", Moteur.premiereFois(joueur, jeuTerrain));
        p.donnees().putBoolean("enfoui", trace.enfoui);
        p.donnees().putInt("variante", trace.variante);
        p.donnees().putLong("graine", Moteur.HASARD.nextLong());
        Messages.envoyer(joueur, p);
    }

    // Résultat du mini-jeu de prélèvement : réussi = échantillon complet (une dose de plus si sans faute), raté = une seule dose.
    static void resultatPrelevement(ServerPlayer joueur, boolean succes, boolean parfait) {
        BlockPos pos = Moteur.PRELEVEMENTS.remove(joueur.getUUID());
        Dossier d = Moteur.dossier(joueur);
        if (pos == null || d == null || !"PRELEVEMENTS".equals(d.phase)) return;
        ServerLevel monde = joueur.serverLevel();
        Trace trace = null;
        for (Trace t : d.traces) {
            if (t.posee && !t.recolte && t.px == pos.getX() && t.py == pos.getY() && t.pz == pos.getZ()) trace = t;
        }
        if (trace == null || joueur.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 100) return;
        int fiole = joueur.getInventory().findSlotMatchingItem(new ItemStack(Contenu.FIOLE));
        // Fiole de secours (rang 2) : le premier prélèvement de l'enquête n'en consomme pas.
        boolean secours = Moteur.rang(joueur) >= 2 && !d.fioleOfferte;
        if (fiole < 0 && !joueur.isCreative() && !secours) return;
        if (secours) d.fioleOfferte = true;
        else if (fiole >= 0 && !joueur.isCreative()) joueur.getInventory().getItem(fiole).shrink(1);
        int doses = succes ? Moteur.reglages.dosesParEchantillon + (parfait ? 1 : 0) : 1;
        if (Moteur.est(d, "givre")) doses = Math.max(1, doses - 1); // échantillon gelé, fragile
        ItemStack echantillon = new ItemStack(Contenu.ECHANTILLON);
        CompoundTag donnees = new CompoundTag();
        donnees.putLong("enquete", d.id);
        donnees.putString("genre", trace.genre);
        donnees.putInt("doses", doses);
        donnees.putBoolean("enfoui", trace.enfoui);
        donnees.putInt("variante", trace.variante);
        List<String> jeux = Genetique.JEUX_ANALYSE.getOrDefault(trace.genre, List.of("sequencage"));
        // Piste gelée : une fois sur deux, l'échantillon doit d'abord être dégelé.
        donnees.putString("jeu", Moteur.est(d, "givre") && Moteur.HASARD.nextBoolean() ? "degel" : jeux.get(Moteur.HASARD.nextInt(jeux.size())));
        echantillon.set(DataComponents.CUSTOM_DATA, CustomData.of(donnees));
        joueur.getInventory().placeItemBackInInventory(echantillon);
        trace.recolte = true;
        if (!succes) d.fautes++;
        monde.removeBlock(pos, false);
        if (trace.enfoui) Traces.reboucher(monde, trace);
        Moteur.sauver();
        joueur.playNotifySound(SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS, 1f, 1.1f);
        Moteur.progres(joueur, "premier_prelevement");
        long reste = d.traces.stream().filter(t -> !t.finie()).count();
        Messages.annonce(joueur, Messages.tr("annonce." + (succes ? (parfait ? "prelevement_parfait" : "prelevement_ok") : "prelevement_abime") + ".titre"),
                Messages.tr("annonce.prelevement.texte", Component.translatable(Genetique.nomTrace(trace.genre, trace.enfoui, trace.variante)), doses,
                        reste > 0 ? Messages.tr("annonce.prelevement.reste", reste) : Messages.tr("annonce.prelevement.fini")), succes ? 0x6CCB5F : 0xF2C93A);
        Messages.synchroniser(joueur);
    }
}
