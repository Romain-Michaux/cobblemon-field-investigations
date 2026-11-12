package fr.cobblescout.traque;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.CobblemonEntities;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.cobblemon.mod.common.pokemon.properties.HiddenAbilityProperty;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

// Boucle exécutée deux fois par seconde pour chaque enquêteur, et apparition de la cible.
final class Suivi {
    private Suivi() {}

    static void suivre(ServerPlayer joueur) {
        Dossier d = Moteur.dossier(joueur);
        if (d == null || !d.monde.equals(Moteur.dimension(joueur))) return;
        boolean kit = joueur.isHolding(Contenu.KIT);
        long maintenant = System.currentTimeMillis();
        ServerLevel monde = joueur.serverLevel();
        Paquet signal = Paquet.de("signal");
        signal.donnees().putString("phase", d.phase);
        switch (d.phase) {
            case "PRELEVEMENTS": {
                boolean jour = Moteur.dort(d, monde);
                int portee = Moteur.rang(joueur) >= 1 ? 14 : 9; // radar affiné
                double min = Double.MAX_VALUE;
                Trace proche = null;
                for (Trace t : d.traces) {
                    if (t.finie()) continue;
                    double dist = Moteur.distance(joueur, t.x, t.z);
                    if (dist < 56 && !jour) Traces.poser(monde, d, t);
                    if (dist < min) {
                        min = dist;
                        proche = t;
                    }
                    // Les traces sont discrètes : kit en main, celles qui sont à portée scintillent (pour ce joueur seulement).
                    boolean cache = t.enfoui && !t.degage;
                    if (kit && !jour && t.posee && dist < portee && (!cache || monde.getBlockState(new BlockPos(t.px, t.py + 1, t.pz)).isAir())) {
                        monde.sendParticles(joueur, ParticleTypes.WAX_OFF, false, t.px + 0.5, t.py + (cache ? 1.15 : 0.4), t.pz + 0.5,
                                2, 0.25, 0.1, 0.25, 0.0);
                    }
                }
                if (joueur.isUsingItem() && joueur.getUseItem().is(Items.BRUSH)) Traces.brosser(joueur, d, monde);
                if (!kit) return;
                // Trace en grotte (ou enquête souterraine) : la distance compte la hauteur, sinon le radar crierait « juste
                // ici » à un joueur qui marche quinze blocs au-dessus.
                boolean enGrotte = proche != null && proche.posee && (proche.grotte || (Moteur.est(d, "souterraine") && proche.echecs < 6));
                if (enGrotte) min = Math.sqrt(joueur.distanceToSqr(proche.px + 0.5, proche.py + 0.5, proche.pz + 0.5));
                signal.donnees().putBoolean("jour", jour);
                signal.donnees().putInt("niveau", min == Double.MAX_VALUE ? -1 : min > 90 ? 0 : min > 60 ? 1 : min > 35 ? 2 : min > 14 ? 3 : min > 5 ? 4 : 5);
                boolean sousTerre = proche != null && proche.enfoui && !proche.degage;
                signal.donnees().putBoolean("enfoui", sousTerre);
                // Pour un indice enfoui, le kit donne la distance exacte (en trois dimensions) : on creuse au plus près.
                if (sousTerre && proche.posee) {
                    signal.donnees().putDouble("metres", Math.sqrt(joueur.distanceToSqr(proche.px + 0.5, proche.py + 0.5, proche.pz + 0.5)));
                    signal.donnees().putInt("dessous", (int) Math.round(joueur.getY() - (proche.py + 1)));
                }
                // Le radar donne aussi la distance réelle et la profondeur de la trace en grotte.
                if (enGrotte) {
                    signal.donnees().putBoolean("grotte", true);
                    signal.donnees().putDouble("metres", Math.sqrt(joueur.distanceToSqr(proche.px + 0.5, proche.py + 0.5, proche.pz + 0.5)));
                    signal.donnees().putInt("dessous", (int) Math.round(joueur.getY() - proche.py));
                }
                signal.donnees().putInt("zone", (int) Moteur.distance(joueur, d.zoneX, d.zoneZ));
                Messages.envoyer(joueur, signal);
                return;
            }
            case "TRAQUE": {
                if (d.prochainDeplacement > 0 && maintenant > d.prochainDeplacement) {
                    Pistage.deplacer(d, monde, 70, 120);
                    Moteur.sauver();
                    Messages.annonce(joueur, "cible_deplacee", 0xF2C93A);
                    Messages.synchroniser(joueur);
                }
                boolean jour = Moteur.dort(d, monde);
                if (!jour && Moteur.distance(joueur, d.taniereX, d.taniereZ) < Moteur.reglages.rayonApproche) {
                    apparaitre(joueur, d, monde);
                } else if (kit) {
                    signal.donnees().putBoolean("jour", jour);
                    signal.donnees().putBoolean("pret", maintenant >= d.prochainReleve);
                    Messages.envoyer(joueur, signal);
                }
                return;
            }
            case "REPERAGE":
            case "APPROCHE":
            case "DEBUSQUE": {
                PokemonEntity e = Moteur.ENTITES.get(d.chef);
                if (e == null && d.entite != null && monde.getEntity(d.entite) instanceof PokemonEntity retrouve) {
                    e = retrouve; // serveur relancé : le spécimen est toujours là
                    Moteur.ENTITES.put(d.chef, e);
                }
                if (e == null || e.isRemoved() || !e.isAlive()) {
                    Moteur.ENTITES.remove(d.chef);
                    boolean morte = e != null && e.getHealth() <= 0f;
                    if (morte && vaincueParLeJoueur(joueur, d, e, maintenant)) {
                        Cloture.resoudre(joueur, d, false);
                    } else if (Moteur.dossier(joueur) == d) {
                        // Disparue sans que le joueur l'ait vaincue ni capturée (trop loin, serveur relancé, accident, autre
                        // créature...) : l'enquête continue, sans pénalité ni butin. Si elle est morte, elle est repérée ailleurs.
                        d.phase = "TRAQUE";
                        d.annonceApproche = false;
                        Moteur.razScan(d);
                        if (morte) Pistage.deplacer(d, monde, 60, 110);
                        Moteur.sauver();
                        Messages.annonce(joueur, morte ? "cible_disparue" : "contact_perdu", 0xF2C93A);
                        Messages.synchroniser(joueur);
                    }
                    return;
                }
                double dist = Math.sqrt(joueur.distanceToSqr(e));
                boolean reperage = "REPERAGE".equals(d.phase);
                if (e.isBattling() && dist < 64) d.dernierCombat = maintenant;
                if (reperage || "APPROCHE".equals(d.phase)) {
                    // La cible file si on l'approche avant de l'avoir identifiée, ou en courant une fois identifiée.
                    boolean senti = reperage && dist < Moteur.alerte(d);
                    boolean entendu = !reperage && dist < Moteur.alerte(d) && joueur.isSprinting();
                    if ((senti || entendu) && !e.isBattling()) {
                        e.discard();
                        Moteur.ENTITES.remove(d.chef);
                        d.phase = "TRAQUE";
                        d.fuites++;
                        Moteur.razScan(d);
                        Pistage.deplacer(d, monde, 80, 130);
                        Moteur.sauver();
                        joueur.playNotifySound(SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 1f, 1.4f);
                        Messages.annonce(joueur, senti ? "senti" : "entendu", 0xE8543A, Moteur.alerte(d), Messages.etoilesTexte(Moteur.etoiles(d, false)));
                        Messages.synchroniser(joueur);
                        return;
                    }
                    if (reperage) {
                        int dureeScan = Moteur.dureeScan(joueur);
                        int[] scan = Moteur.SCANS.computeIfAbsent(joueur.getUUID(), k -> new int[2]);
                        if (e.isBattling()) scan[0] = dureeScan; // combat engagé de loin : la cible est de fait identifiée
                        else if (joueur.isUsingItem() && joueur.getUseItem().is(Contenu.KIT)) scanner(joueur, scan, monde, e);
                        else scan[0] = Math.max(0, scan[0] - 1);
                        if (scan[0] >= dureeScan) {
                            d.phase = "APPROCHE";
                            Moteur.razScan(d);
                            e.setInvulnerable(false);
                            Moteur.sauver();
                            joueur.stopUsingItem();
                            joueur.playNotifySound(SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1f, 1.5f);
                            joueur.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.7f, 1.3f);
                            boolean juste = d.espece.equals(d.pronostic);
                            if (juste) Moteur.progres(joueur, "bon_flair");
                            Component titreId = Messages.tr("annonce.identifie.titre", Messages.etoilesTexte(Moteur.etoiles(d)));
                            Component texteId = Messages.tr("annonce.identifie.texte", Moteur.nomEspece(d.espece), Messages.tr(juste ? "annonce.identifie.juste" : "annonce.identifie.faux"), Moteur.ivParfaits(Moteur.etoiles(d)));
                            for (ServerPlayer m : Moteur.enLigne(d, joueur.server)) Messages.annonce(m, titreId, texteId, 0xF2C93A);
                            Messages.synchroniser(joueur);
                            return;
                        }
                        signal.donnees().putInt("distance", (int) Math.round(dist));
                        signal.donnees().putInt("scan", scan[0]);
                        signal.donnees().putInt("scanMax", dureeScan);
                        signal.donnees().putBoolean("autre", scan[1] > 0);
                        signal.donnees().putInt("alerte", Moteur.alerte(d));
                        Messages.envoyer(joueur, signal);
                        return;
                    }
                    if (dist < Moteur.reglages.rayonDebusque || e.isBattling()) {
                        d.phase = "DEBUSQUE";
                        d.finLueur = maintenant + 12_000;
                        e.setGlowingTag(true);
                        Moteur.sauver();
                        joueur.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1f);
                        Messages.annonce(joueur, "debusque", 0xF2C93A, Moteur.nomEspece(d.espece), Moteur.ivParfaits(Moteur.etoiles(d)));
                        Messages.synchroniser(joueur);
                        return;
                    }
                    signal.donnees().putInt("distance", (int) Math.round(dist));
                    signal.donnees().putBoolean("course", joueur.isSprinting());
                    Messages.envoyer(joueur, signal);
                } else if (d.finLueur > 0 && maintenant > d.finLueur) {
                    d.finLueur = 0;
                    e.setGlowingTag(false);
                }
                return;
            }
            default:
        }
    }

    // Une cible K.O. ne vaut victoire que si elle était identifiée et que c'est bien le joueur qui l'a vaincue :
    // en combat Cobblemon juste avant, ou frappée par lui (ou par un de ses Pokémon).
    private static boolean vaincueParLeJoueur(ServerPlayer joueur, Dossier d, PokemonEntity e, long maintenant) {
        if ("REPERAGE".equals(d.phase)) return false;
        if (maintenant - d.dernierCombat < 5000) return true;
        var auteur = e.getKillCredit();
        for (UUID u : Moteur.membres(d)) {
            if (auteur instanceof ServerPlayer p && p.getUUID().equals(u)) return true;
            if (auteur instanceof PokemonEntity autre && u.equals(autre.getOwnerUUID())) return true;
        }
        return false;
    }

    private static boolean vise(ServerPlayer joueur, PokemonEntity e) {
        Vec3 oeil = joueur.getEyePosition(), vers = e.getBoundingBox().getCenter().subtract(oeil);
        double dist = vers.length();
        if (dist < 0.5 || dist > 64) return false;
        double angle = Math.acos(Math.max(-1, Math.min(1, joueur.getLookAngle().dot(vers.normalize()))));
        double tolerance = Math.atan((Math.max(e.getBbWidth(), e.getBbHeight()) / 2 + 0.6) / dist) + Math.toRadians(2.5);
        return angle <= tolerance && joueur.hasLineOfSight(e);
    }

    // Scan au kit (clic droit maintenu, viseur sur le Pokémon) : trois secondes sur la cible pour l'identifier.
    // Un autre Pokémon dans le viseur est simplement signalé comme n'étant pas la cible.
    private static void scanner(ServerPlayer joueur, int[] scan, ServerLevel monde, PokemonEntity cible) {
        if (vise(joueur, cible)) {
            scan[1] = 0;
            scan[0]++; // les bips sont joués par le client, qui les accélère et les rend plus aigus au fil de l'analyse
            return;
        }
        scan[0] = Math.max(0, scan[0] - 1);
        boolean autre = false;
        for (PokemonEntity p : monde.getEntitiesOfClass(PokemonEntity.class, joueur.getBoundingBox().inflate(48), x -> x != cible)) {
            if (vise(joueur, p)) {
                autre = true;
                break;
            }
        }
        scan[1] = autre ? 3 : Math.max(0, scan[1] - 1);
    }

    private static void apparaitre(ServerPlayer joueur, Dossier d, ServerLevel monde) {
        Species s = PokemonSpecies.getByName(d.espece);
        if (s == null) {
            Cloture.finir(joueur, d, Messages.tr("annonce.annulee.titre"), Messages.tr("annonce.annulee.texte"), 0xE8543A);
            return;
        }
        Pokemon p = new Pokemon();
        p.setSpecies(s);
        p.setLevel(Math.max(1, d.niveau));
        p.initialize();
        int etoiles = Moteur.etoiles(d);
        List<Stat> stats = new ArrayList<>(List.of(Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED));
        Collections.shuffle(stats, Moteur.HASARD);
        // Mieux l'enquête est menée, meilleur est le Pokémon : une statistique parfaite (IV 31) par étoile au-delà de la première.
        for (int i = 0; i < Math.min(stats.size(), Moteur.ivParfaits(etoiles)); i++) p.setIV(stats.get(i), 31);
        float taux = Cobblemon.INSTANCE.getConfig().getShinyRate();
        double chroma = (1 + Moteur.reglages.multChromaParEtoile * etoiles) * (Moteur.est(d, "nocturne") ? 2 : 1);
        if (taux > 0 && Moteur.HASARD.nextDouble() < chroma / taux) p.setShiny(true);
        if (Moteur.est(d, "exceptionnel")) {
            try {
                new HiddenAbilityProperty(true).apply(p); // sans effet si l'espèce n'a pas de talent caché
            } catch (Exception e) {
                System.err.println("[Field Investigations] Talent caché impossible à donner : " + e);
            }
        }
        // Elle apparaît sur la terre ferme : jamais dans l'eau, la lave ou au-dessus du vide.
        int tx = (int) Math.floor(d.taniereX), tz = (int) Math.floor(d.taniereZ);
        BlockPos sol = Moteur.est(d, "aquatique") ? pleineEau(monde, tx, tz) : Moteur.est(d, "souterraine") ? Traces.grotte(monde, tx, tz, 12) : solFerme(monde, tx, tz);
        if (sol == null && Moteur.est(d, "souterraine")) sol = solFerme(monde, tx, tz); // aucune grotte par ici : elle sort en surface
        if (sol == null) {
            Pistage.deplacer(d, monde, 40, 80); // rien de praticable ici : elle est repérée un peu plus loin
            Moteur.sauver();
            Messages.synchroniser(joueur);
            return;
        }
        PokemonEntity e = new PokemonEntity(monde, p, CobblemonEntities.POKEMON);
        e.moveTo(sol.getX() + 0.5, sol.getY(), sol.getZ() + 0.5, Moteur.HASARD.nextFloat() * 360f, 0f);
        // Tant qu'elle n'est pas identifiée, rien ne peut la blesser (chute, noyade, autre créature).
        e.setInvulnerable(true);
        monde.addFreshEntity(e);
        Moteur.ENTITES.put(d.chef, e);
        d.pokemon = p.getUuid();
        d.entite = e.getUUID();
        d.phase = "REPERAGE";
        Moteur.razScan(d);
        Moteur.sauver();
        if (!d.annonceApproche) {
            d.annonceApproche = true;
            joueur.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 0.6f);
            Messages.annonce(joueur, "tout_pres", 0xF2C93A, Moteur.alerte(d));
        }
        Messages.synchroniser(joueur);
    }

    // Pour une cible aquatique : un endroit où il y a au moins deux blocs d'eau au-dessus du fond.
    private static BlockPos pleineEau(ServerLevel monde, int bx, int bz) {
        for (int rayon = 0; rayon <= 16; rayon++) {
            for (int dx = -rayon; dx <= rayon; dx++) {
                for (int dz = -rayon; dz <= rayon; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != rayon) continue;
                    int x = bx + dx, z = bz + dz;
                    if (!monde.hasChunk(x >> 4, z >> 4)) continue;
                    int y = monde.getChunk(x >> 4, z >> 4).getHeight(Heightmap.Types.OCEAN_FLOOR, x & 15, z & 15) + 1;
                    BlockPos pos = new BlockPos(x, y, z);
                    if (monde.getBlockState(pos).is(Blocks.WATER) && monde.getBlockState(pos.above()).is(Blocks.WATER)) return pos;
                }
            }
        }
        return null;
    }

    private static BlockPos solFerme(ServerLevel monde, int bx, int bz) {
        for (int rayon = 0; rayon <= 10; rayon++) {
            for (int dx = -rayon; dx <= rayon; dx++) {
                for (int dz = -rayon; dz <= rayon; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != rayon) continue;
                    int x = bx + dx, z = bz + dz;
                    if (!monde.hasChunk(x >> 4, z >> 4)) continue;
                    int y = monde.getChunk(x >> 4, z >> 4).getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15) + 1;
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!monde.getBlockState(pos.below()).isFaceSturdy(monde, pos.below(), Direction.UP)) continue;
                    if (!monde.getBlockState(pos).getFluidState().isEmpty() || !monde.getBlockState(pos.above()).getFluidState().isEmpty()) continue;
                    if (!monde.getBlockState(pos).getCollisionShape(monde, pos).isEmpty()) continue;
                    return pos;
                }
            }
        }
        return null;
    }
}
