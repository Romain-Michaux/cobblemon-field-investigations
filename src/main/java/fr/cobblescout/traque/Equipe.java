package fr.cobblescout.traque;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

// Enquêtes à plusieurs : invitations, arrivées et départs.
final class Equipe {
    private Equipe() {}

    // Invitations en attente : invité -> chef d'enquête, et leur date d'expiration.
    static final Map<UUID, UUID> INVITATIONS = new HashMap<>();

    static final Map<UUID, Long> FIN_INVITATIONS = new HashMap<>();

    static UUID invitation(ServerPlayer joueur) {
        UUID id = joueur.getUUID(), chef = INVITATIONS.get(id);
        if (chef == null) return null;
        Dossier d = Moteur.etat.dossiers.get(chef);
        if (FIN_INVITATIONS.getOrDefault(id, 0L) < System.currentTimeMillis() || d == null || Moteur.membres(d).size() >= Moteur.reglages.tailleEquipe) {
            INVITATIONS.remove(id);
            FIN_INVITATIONS.remove(id);
            return null;
        }
        return chef;
    }

    static void inviter(ServerPlayer joueur, String nomInvite) {
        Dossier d = Moteur.etat.dossiers.get(joueur.getUUID());
        ServerPlayer invite = joueur.server.getPlayerList().getPlayerByName(nomInvite);
        if (d == null || invite == null || invite == joueur || Moteur.dossier(invite) != null || "DEBUSQUE".equals(d.phase)) {
            Messages.annonce(joueur, "invitation_impossible", 0xE8543A);
            return;
        }
        if (Moteur.membres(d).size() >= Moteur.reglages.tailleEquipe) {
            Messages.annonce(joueur, "equipe_pleine", 0xE8543A, Moteur.reglages.tailleEquipe);
            return;
        }
        INVITATIONS.put(invite.getUUID(), joueur.getUUID());
        FIN_INVITATIONS.put(invite.getUUID(), System.currentTimeMillis() + 120_000L);
        invite.playNotifySound(SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 1f, 1.2f);
        Messages.annonce(invite, "invitation", 0x4FD1C5, joueur.getGameProfile().getName());
        Messages.annonce(joueur, "invitation_envoyee", 0x4FD1C5, invite.getGameProfile().getName());
        Messages.synchroniser(invite);
        Messages.synchroniser(joueur);
    }

    static void repondre(ServerPlayer joueur, boolean accepte) {
        UUID chef = invitation(joueur);
        INVITATIONS.remove(joueur.getUUID());
        FIN_INVITATIONS.remove(joueur.getUUID());
        Dossier d = chef == null ? null : Moteur.etat.dossiers.get(chef);
        if (accepte && d != null && Moteur.dossier(joueur) == null) {
            d.equipe.add(joueur.getUUID());
            Moteur.etat.offres.remove(joueur.getUUID());
            Moteur.etat.finOffres.remove(joueur.getUUID());
            Moteur.sauver();
            joueur.playNotifySound(SoundEvents.SPYGLASS_USE, SoundSource.PLAYERS, 1f, 0.8f);
            Messages.annonce(joueur, "equipe_rejoint", 0x6CCB5F, joueur.getGameProfile().getName());
        }
        Messages.synchroniser(joueur);
    }

    // Un coéquipier quitte le dossier (le chef, lui, ne peut que le classer pour tout le monde).
    static void quitter(ServerPlayer joueur, Dossier d) {
        d.equipe.remove(joueur.getUUID());
        Moteur.SCANS.remove(joueur.getUUID());
        Moteur.sauver();
        Messages.annonce(joueur, "equipe_quittee", 0x9AA0A6);
        for (ServerPlayer m : Moteur.enLigne(d, joueur.server)) {
            Messages.annonce(m, "equipe_depart", 0x9AA0A6, joueur.getGameProfile().getName());
            Messages.envoyerEtat(m);
        }
        Messages.envoyerEtat(joueur);
    }
}
