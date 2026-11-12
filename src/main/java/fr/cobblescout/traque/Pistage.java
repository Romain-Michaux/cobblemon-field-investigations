package fr.cobblescout.traque;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Species;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

// Phase de traque : relevés de cri au kit, recoupement, déplacements de la cible.
final class Pistage {
    private Pistage() {}

    record Recoupement(boolean valide, double x, double z, double incertitude, String message) {}

    static void lancer(ServerPlayer joueur, String pronostic) {
        Dossier d = Moteur.dossier(joueur);
        if (d == null || !"PRELEVEMENTS".equals(d.phase) || d.epingles.size() < Moteur.requis(d)) return;
        Species cible = PokemonSpecies.getByName(d.espece), choix = PokemonSpecies.getByName(pronostic);
        // Le joueur peut désigner n'importe quel suspect qu'il n'a pas barré, même si les analyses le contredisent :
        // se tromper lui coûtera des étoiles.
        if (cible == null || choix == null || !d.suspects.contains(pronostic) || (d.ecartes != null && d.ecartes.contains(pronostic))) return;
        d.pronostic = pronostic;
        d.phase = "TRAQUE";
        d.prochainDeplacement = Moteur.prochainDeplacement(d);
        if (d.monde.equals(Moteur.dimension(joueur))) Traces.retirerTraces(joueur.serverLevel(), d);
        Moteur.sauver();
        joueur.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.6f, 1.3f);
        Messages.annonce(joueur, "kit_calibre", 0x4FD1C5);
        Messages.synchroniser(joueur);
    }

    static void abandonner(ServerPlayer joueur) {
        Dossier d = Moteur.dossier(joueur);
        if (d == null) return;
        if (!joueur.getUUID().equals(d.chef)) {
            Equipe.quitter(joueur, d);
            return;
        }
        PokemonEntity e = Moteur.ENTITES.remove(d.chef);
        if (e != null && !e.isBattling() && ("APPROCHE".equals(d.phase) || "REPERAGE".equals(d.phase))) e.discard();
        Cloture.finir(joueur, d, Messages.tr("annonce.classee.titre"), Messages.tr("annonce.classee.texte"), 0x9AA0A6);
    }

    public static void utiliserKit(ServerPlayer joueur, boolean accroupi) {
        Dossier d = Moteur.dossier(joueur);
        if (d == null) {
            Messages.ouvrir(joueur, "tableau");
            return;
        }
        if ("TRAQUE".equals(d.phase) && !accroupi && d.monde.equals(Moteur.dimension(joueur))) releve(joueur, d);
        else Messages.ouvrir(joueur, "kit");
    }

    // La cible est apparue mais n'est pas encore identifiée : le kit sert alors de scanner (clic droit maintenu).
    public static boolean enReperage(ServerPlayer joueur) {
        Dossier d = Moteur.dossier(joueur);
        return d != null && "REPERAGE".equals(d.phase) && d.monde.equals(Moteur.dimension(joueur));
    }

    private static void releve(ServerPlayer joueur, Dossier d) {
        long maintenant = System.currentTimeMillis();
        if (maintenant < d.prochainReleve) return;
        if (Moteur.dort(d, joueur.serverLevel())) {
            Messages.annonce(joueur, "nocturne_silence", 0xF2C93A);
            return;
        }
        if (Moteur.est(d, "brume")) {
            // Dans la brume, il faut d'abord isoler le cri du bruit : le relevé se fait à l'issue du mini-jeu.
            Moteur.FILTRAGES.put(joueur.getUUID(), Boolean.TRUE);
            Paquet p = Paquet.de("ouvrir");
            p.donnees().putString("ecran", "filtrage");
            p.donnees().putBoolean("premiere", Moteur.premiereFois(joueur, "filtrage"));
            p.donnees().putLong("graine", Moteur.HASARD.nextLong());
            Messages.envoyer(joueur, p);
            return;
        }
        relever(joueur, d, 1);
    }

    // Résultat du filtrage du cri : réussi, le relevé est aussi précis qu'ailleurs ; raté, il est nettement plus flou.
    static void resultatFiltrage(ServerPlayer joueur, boolean succes) {
        if (Moteur.FILTRAGES.remove(joueur.getUUID()) == null) return;
        Dossier d = Moteur.dossier(joueur);
        if (d == null || !"TRAQUE".equals(d.phase) || !d.monde.equals(Moteur.dimension(joueur))) return;
        relever(joueur, d, succes ? 1 : 1.8);
    }

    private static void relever(ServerPlayer joueur, Dossier d, double flou) {
        long maintenant = System.currentTimeMillis();
        Releve dernier = d.releves.isEmpty() ? null : d.releves.get(d.releves.size() - 1);
        if (dernier != null && !dernier.perime && Moteur.distance(joueur, dernier.x, dernier.z) < Moteur.reglages.distanceEntreReleves) {
            Messages.annonce(joueur, "meme_ecoute", 0xF2C93A, Moteur.reglages.distanceEntreReleves);
            return;
        }
        d.prochainReleve = maintenant + Moteur.reglages.secondesEntreReleves * 1000L;
        joueur.getCooldowns().addCooldown(Contenu.KIT, Moteur.reglages.secondesEntreReleves * 20);
        double dx = d.taniereX - joueur.getX(), dz = d.taniereZ - joueur.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        double vrai = Math.toDegrees(Math.atan2(dx, -dz));
        double erreur = Math.max(Moteur.reglages.erreurMinDegres, Math.min(Moteur.reglages.erreurMaxDegres, dist / 22)) * flou * (Moteur.rang(joueur) >= 5 ? 0.75 : 1);
        // Le bruit dépend de l'endroit (et pas du moment) : refaire le relevé au même endroit ne l'améliore pas.
        Random bruit = new Random(d.graine ^ (((long) joueur.getBlockX() >> 3) * 73428767L) ^ (((long) joueur.getBlockZ() >> 3) * 912931L) ^ d.fuites);
        Releve r = new Releve();
        r.x = joueur.getX();
        r.z = joueur.getZ();
        r.cap = ((vrai + (bruit.nextDouble() * 2 - 1) * erreur) % 360 + 360) % 360;
        r.erreur = erreur;
        d.releves.add(r);
        Moteur.sauver();

        double rad = Math.toRadians(r.cap);
        double ux = Math.sin(rad), uz = -Math.cos(rad);
        float volume = (float) Math.max(0.25, Math.min(1.0, 1.25 - dist / 320));
        SoundEvent cri = SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("cobblemon", "pokemon." + d.espece + ".cry"));
        joueur.connection.send(new ClientboundSoundPacket(Holder.direct(cri), SoundSource.NEUTRAL,
                joueur.getX() + ux * 14, joueur.getEyeY(), joueur.getZ() + uz * 14, volume * 2.5f, 1f, Moteur.HASARD.nextLong()));
        Paquet p = Paquet.de("releve");
        p.donnees().putDouble("cap", r.cap);
        p.donnees().putDouble("erreur", erreur);
        p.donnees().putInt("force", dist > 260 ? 0 : dist > 170 ? 1 : dist > 100 ? 2 : 3);
        Messages.envoyer(joueur, p);
        Messages.synchroniser(joueur);
    }

    // Recoupement des relevés valides (moindres carrés pondérés) : position estimée et incertitude.
    static Recoupement recouper(Dossier d) {
        List<Releve> valides = new ArrayList<>();
        for (Releve r : d.releves) {
            if (!r.perime) valides.add(r);
        }
        if (valides.size() < 2) return new Recoupement(false, 0, 0, 0, "cobblemon_field_investigations.rec.deux");
        double ex = 0, ez = 0, incertitude = 0;
        for (int passe = 0; passe < 2; passe++) {
            double a11 = 0, a12 = 0, a22 = 0, b1 = 0, b2 = 0;
            for (Releve r : valides) {
                double rad = Math.toRadians(r.cap);
                double nx = Math.cos(rad), nz = Math.sin(rad);
                double w = 1;
                if (passe == 1) {
                    double sigma = Math.max(8, Math.hypot(ex - r.x, ez - r.z) * Math.toRadians(r.erreur) * 0.6);
                    w = 1 / (sigma * sigma);
                }
                double np = nx * r.x + nz * r.z;
                a11 += w * nx * nx;
                a12 += w * nx * nz;
                a22 += w * nz * nz;
                b1 += w * nx * np;
                b2 += w * nz * np;
            }
            double det = a11 * a22 - a12 * a12;
            if (passe == 0 && det < 0.03) return new Recoupement(false, 0, 0, 0, "cobblemon_field_investigations.rec.alignes");
            if (Math.abs(det) < 1e-12) return new Recoupement(false, 0, 0, 0, "cobblemon_field_investigations.rec.impossible");
            ex = (a22 * b1 - a12 * b2) / det;
            ez = (a11 * b2 - a12 * b1) / det;
            if (passe == 1) incertitude = 2.5 * Math.sqrt((a11 + a22) / det);
        }
        int devant = 0;
        for (Releve r : valides) {
            double rad = Math.toRadians(r.cap);
            if ((ex - r.x) * Math.sin(rad) - (ez - r.z) * Math.cos(rad) > 0) devant++;
        }
        if (devant * 2 <= valides.size()) return new Recoupement(false, 0, 0, 0, "cobblemon_field_investigations.rec.contredisent");
        return new Recoupement(true, ex, ez, Math.max(10, incertitude), "cobblemon_field_investigations.rec.ok");
    }

    // La cible change d'endroit, en restant dans son milieu : une cible aquatique reste dans l'eau, les autres à terre.
    static void deplacer(Dossier d, ServerLevel monde, double min, double max) {
        double x = d.taniereX, z = d.taniereZ;
        for (int essai = 0; essai < 16; essai++) {
            double a = Moteur.HASARD.nextDouble() * Math.PI * 2, dist = min + Moteur.HASARD.nextDouble() * (max - min);
            x = d.taniereX + Math.cos(a) * dist;
            z = d.taniereZ + Math.sin(a) * dist;
            if (Signalement.aquatique(monde, x, z) == Moteur.est(d, "aquatique")) break;
        }
        d.taniereX = x;
        d.taniereZ = z;
        for (Releve r : d.releves) r.perime = true;
        d.prochainDeplacement = Moteur.prochainDeplacement(d);
        d.annonceApproche = false;
    }
}
