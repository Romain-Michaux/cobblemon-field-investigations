package fr.cobblescout.traque;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokedex.PokedexManager;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.pokemon.Species;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

// Tout ce qui part vers le client : annonces, ouverture d'écran, état de l'enquête.
final class Messages {
    private Messages() {}

    // Annonces qui concernent tout le dossier : les coéquipiers les reçoivent aussi.
    private static final Set<String> PARTAGEES = Set.of("analyse_reussie", "echantillon_detruit", "kit_calibre", "cible_deplacee", "contact_perdu",
            "cible_disparue", "senti", "entendu", "tout_pres", "debusque", "indice_degage", "indice_detruit", "equipe_rejoint", "equipe_depart");

    static void envoyer(ServerPlayer joueur, Paquet p) {
        if (ServerPlayNetworking.canSend(joueur, Paquet.TYPE)) ServerPlayNetworking.send(joueur, p);
    }

    static MutableComponent tr(String cle, Object... args) {
        return Component.translatable("cobblemon_field_investigations." + cle, args);
    }

    // Les annonces partent sous forme de textes traduisibles : chaque joueur les lit dans la langue de son jeu.
    public static void annonce(ServerPlayer joueur, Component titre, Component texte, int couleur) {
        Paquet p = Paquet.de("annonce");
        p.donnees().putString("titre", Component.Serializer.toJson(titre, joueur.registryAccess()));
        p.donnees().putString("texte", Component.Serializer.toJson(texte, joueur.registryAccess()));
        p.donnees().putInt("couleur", couleur);
        envoyer(joueur, p);
    }

    public static void annonce(ServerPlayer joueur, String cle, int couleur, Object... args) {
        Component titre = tr("annonce." + cle + ".titre", args), texte = tr("annonce." + cle + ".texte", args);
        annonce(joueur, titre, texte, couleur);
        if (PARTAGEES.contains(cle)) {
            for (ServerPlayer autre : Moteur.coequipiers(joueur)) annonce(autre, titre, texte, couleur);
        }
    }

    static String etoilesTexte(int n) {
        return "★".repeat(Math.max(0, Math.min(5, n))) + "☆".repeat(Math.max(0, 5 - n));
    }

    static void ouvrir(ServerPlayer joueur, String ecran) {
        synchroniser(joueur);
        Paquet p = Paquet.de("ouvrir");
        p.donnees().putString("ecran", ecran);
        envoyer(joueur, p);
    }

    // Tout ce que le client a le droit de savoir sur l'enquête du joueur (jamais l'espèce avant qu'elle soit débusquée).
    static void synchroniser(ServerPlayer joueur) {
        envoyerEtat(joueur);
        for (ServerPlayer autre : Moteur.coequipiers(joueur)) envoyerEtat(autre);
    }

    static void envoyerEtat(ServerPlayer joueur) {
        Paquet p = Paquet.de("etat");
        CompoundTag t = p.donnees();
        Dossier d = Moteur.dossier(joueur);
        t.putBoolean("actif", d != null);
        t.putInt("reussies", Moteur.etat.reussies.getOrDefault(joueur.getUUID(), 0));
        t.putLong("attente", Math.max(0, Moteur.etat.prochaineEnquete.getOrDefault(joueur.getUUID(), 0L) - System.currentTimeMillis()));
        t.putInt("difficulte", "easy".equalsIgnoreCase(Moteur.reglages.difficulteMiniJeux) ? -1 : "hard".equalsIgnoreCase(Moteur.reglages.difficulteMiniJeux) ? 1 : 0);
        Profil profil = Moteur.profil(joueur.getUUID());
        int rang = Moteur.rang(profil.xp);
        CompoundTag carriere = new CompoundTag();
        carriere.putInt("xp", profil.xp);
        carriere.putInt("rang", rang);
        carriere.putInt("seuil", Moteur.SEUILS[rang]);
        carriere.putInt("suivant", rang + 1 < Moteur.SEUILS.length ? Moteur.SEUILS[rang + 1] : -1);
        int parfaites = 0, speciales = 0;
        Set<String> especes = new HashSet<>();
        CompoundTag types = new CompoundTag();
        ListTag archives = new ListTag();
        for (int i = profil.historique.size() - 1; i >= 0; i--) {
            Fiche f = profil.historique.get(i);
            String type = f.special == null ? "" : f.special;
            if (f.etoiles >= 5) parfaites++;
            if (!type.isEmpty()) speciales++;
            especes.add(f.espece);
            types.putInt(type.isEmpty() ? "ordinaire" : type, types.getInt(type.isEmpty() ? "ordinaire" : type) + 1);
            if (archives.size() >= 40) continue;
            CompoundTag c = new CompoundTag();
            c.putString("espece", f.espece);
            c.putString("special", type);
            c.putInt("etoiles", f.etoiles);
            c.putInt("xp", f.xp);
            c.putBoolean("capture", f.capture);
            c.putLong("date", f.date);
            archives.add(c);
        }
        carriere.putInt("parfaites", parfaites);
        carriere.putInt("speciales", speciales);
        carriere.putInt("especes", especes.size());
        carriere.put("types", types);
        carriere.put("archives", archives);
        t.put("carriere", carriere);
        ListTag equipe = new ListTag(), proches = new ListTag();
        boolean chef = d != null && joueur.getUUID().equals(d.chef);
        if (d != null) {
            for (UUID u : Moteur.membres(d)) equipe.add(StringTag.valueOf(Moteur.nom(joueur.server, u)));
        }
        if (chef) {
            // Les joueurs sans enquête à moins de 64 blocs : d'abord ceux avec qui ce joueur a déjà résolu le plus
            // d'enquêtes, puis par ordre alphabétique.
            List<ServerPlayer> libres = new ArrayList<>();
            for (ServerPlayer autre : joueur.serverLevel().players()) {
                if (autre != joueur && autre.distanceToSqr(joueur) < 64 * 64 && Moteur.dossier(autre) == null) libres.add(autre);
            }
            libres.sort((a, b) -> {
                int na = profil.partenaires.getOrDefault(a.getUUID(), 0), nb = profil.partenaires.getOrDefault(b.getUUID(), 0);
                return na != nb ? Integer.compare(nb, na) : a.getGameProfile().getName().compareToIgnoreCase(b.getGameProfile().getName());
            });
            for (ServerPlayer autre : libres) {
                if (proches.size() >= 80) break;
                CompoundTag c = new CompoundTag();
                c.putString("nom", autre.getGameProfile().getName());
                c.putInt("ensemble", profil.partenaires.getOrDefault(autre.getUUID(), 0));
                proches.add(c);
            }
        }
        UUID invitant = d == null ? Equipe.invitation(joueur) : null;
        t.put("equipe", equipe);
        t.put("proches", proches);
        t.putBoolean("chef", chef);
        t.putInt("equipeMax", Moteur.reglages.tailleEquipe);
        t.putString("invitation", invitant == null ? "" : Moteur.nom(joueur.server, invitant));
        List<Dossier> pistes = d == null ? Signalement.pistes(joueur) : null;
        if (pistes != null) {
            // Ce que le joueur sait d'un signalement avant de le choisir : sa rareté, son type, où se trouve la zone.
            ListTag liste = new ListTag();
            for (Dossier o : pistes) {
                CompoundTag c = new CompoundTag();
                c.putInt("rang", Math.max(0, Signalement.CATEGORIES.indexOf(o.categorie)));
                c.putString("special", o.special == null ? "" : o.special);
                c.putBoolean("nouvelle", o.nouvelle);
                c.putDouble("zoneX", o.zoneX);
                c.putDouble("zoneZ", o.zoneZ);
                liste.add(c);
            }
            t.put("pistes", liste);
            t.putLong("pistesFin", Math.max(0, Moteur.etat.finOffres.getOrDefault(joueur.getUUID(), 0L) - System.currentTimeMillis()));
        }
        if (d != null) {
            Species cible = PokemonSpecies.getByName(d.espece);
            t.putString("phase", d.phase);
            t.putInt("rang", Math.max(0, Signalement.CATEGORIES.indexOf(d.categorie)));
            t.putBoolean("nouvelle", d.nouvelle);
            t.putDouble("zoneX", d.zoneX);
            t.putDouble("zoneZ", d.zoneZ);
            t.putInt("rayonZone", Moteur.rayon(d));
            t.putInt("requis", Moteur.requis(d));
            t.putString("special", d.special == null ? "" : d.special);
            t.putString("pronostic", d.pronostic == null ? "" : d.pronostic);
            ListTag traces = new ListTag();
            for (Trace tr : d.traces) {
                CompoundTag c = new CompoundTag();
                c.putString("genre", tr.genre);
                c.putBoolean("recolte", tr.recolte);
                c.putBoolean("enfoui", tr.enfoui);
                c.putInt("variante", tr.variante);
                c.putBoolean("degage", tr.degage);
                c.putBoolean("perdue", tr.perdue);
                c.putBoolean("epingle", d.epingles.contains(tr.genre));
                if (d.epingles.contains(tr.genre) && cible != null) c.putString("valeur", Genetique.valeur(tr.genre, cible));
                traces.add(c);
            }
            t.put("traces", traces);
            ListTag suspects = new ListTag();
            PokedexManager pokedex = Cobblemon.playerDataManager.getPokedexData(joueur);
            for (String s : d.suspects) {
                Species esp = PokemonSpecies.getByName(s);
                if (esp == null) continue;
                CompoundTag c = new CompoundTag();
                c.putString("espece", s);
                ListTag valeurs = new ListTag();
                for (String m : Genetique.MARQUEURS) valeurs.add(StringTag.valueOf(Genetique.valeur(m, esp)));
                c.put("valeurs", valeurs);
                c.putBoolean("elimine", d.ecartes != null && d.ecartes.contains(s));
                c.putBoolean("possede", Signalement.possede(pokedex, s));
                suspects.add(c);
            }
            t.put("suspects", suspects);
            ListTag releves = new ListTag();
            for (Releve r : d.releves) {
                CompoundTag c = new CompoundTag();
                c.putDouble("x", r.x);
                c.putDouble("z", r.z);
                c.putDouble("cap", r.cap);
                c.putDouble("erreur", r.erreur);
                c.putBoolean("perime", r.perime);
                releves.add(c);
            }
            t.put("releves", releves);
            Pistage.Recoupement rec = Pistage.recouper(d);
            t.putBoolean("recValide", rec.valide());
            t.putString("recMessage", rec.message());
            if (rec.valide()) {
                t.putDouble("recX", rec.x());
                t.putDouble("recZ", rec.z());
                t.putDouble("recIncertitude", rec.incertitude());
            }
            boolean identifie = "APPROCHE".equals(d.phase) || "DEBUSQUE".equals(d.phase);
            if (identifie) t.putString("espece", d.espece);
            // Tant que la cible n'est pas identifiée, le client ne doit pas pouvoir deviner si le pronostic est juste.
            t.putInt("etoiles", Moteur.etoiles(d, identifie));
            t.putInt("fautes", d.fautes);
            t.putInt("fuites", d.fuites);
        }
        envoyer(joueur, p);
    }
}
