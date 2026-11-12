package io.github.romainmichaux.fieldinvestigations;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

// Enquêtes à plusieurs : invitations, arrivées et départs.
final class Team {
    private Team() {}

    // Invitations en attente : invité -> chef d'enquête, et leur date d'expiration.
    static final Map<UUID, UUID> INVITATIONS = new HashMap<>();

    static final Map<UUID, Long> INVITATION_EXPIRY = new HashMap<>();

    static UUID invitation(ServerPlayer player) {
        UUID id = player.getUUID(), leader = INVITATIONS.get(id);
        if (leader == null) return null;
        CaseFile d = Engine.state.caseFiles.get(leader);
        if (INVITATION_EXPIRY.getOrDefault(id, 0L) < System.currentTimeMillis() || d == null || Engine.members(d).size() >= Engine.settings.maxTeamSize) {
            INVITATIONS.remove(id);
            INVITATION_EXPIRY.remove(id);
            return null;
        }
        return leader;
    }

    static void invite(ServerPlayer player, String inviteeName) {
        CaseFile d = Engine.state.caseFiles.get(player.getUUID());
        ServerPlayer invitee = player.server.getPlayerList().getPlayerByName(inviteeName);
        if (d == null || invitee == null || invitee == player || Engine.caseFile(invitee) != null || "FLUSHED".equals(d.phase)) {
            Messages.announce(player, "invitation_impossible", 0xE8543A);
            return;
        }
        if (Engine.members(d).size() >= Engine.settings.maxTeamSize) {
            Messages.announce(player, "team_full", 0xE8543A, Engine.settings.maxTeamSize);
            return;
        }
        INVITATIONS.put(invitee.getUUID(), player.getUUID());
        INVITATION_EXPIRY.put(invitee.getUUID(), System.currentTimeMillis() + 120_000L);
        invitee.playNotifySound(SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 1f, 1.2f);
        Messages.announce(invitee, "invitation", 0x4FD1C5, player.getGameProfile().getName());
        Messages.announce(player, "invitation_sent", 0x4FD1C5, invitee.getGameProfile().getName());
        Messages.sync(invitee);
        Messages.sync(player);
    }

    static void answer(ServerPlayer player, boolean accepted) {
        UUID leader = invitation(player);
        INVITATIONS.remove(player.getUUID());
        INVITATION_EXPIRY.remove(player.getUUID());
        CaseFile d = leader == null ? null : Engine.state.caseFiles.get(leader);
        if (accepted && d != null && Engine.caseFile(player) == null) {
            d.team.add(player.getUUID());
            Engine.state.offers.remove(player.getUUID());
            Engine.state.offersEnd.remove(player.getUUID());
            Engine.save();
            player.playNotifySound(SoundEvents.SPYGLASS_USE, SoundSource.PLAYERS, 1f, 0.8f);
            Messages.announce(player, "team_joined", 0x6CCB5F, player.getGameProfile().getName());
        }
        Messages.sync(player);
    }

    // Un coéquipier quitte le dossier (le chef, lui, ne peut que le classer pour tout le monde).
    static void leave(ServerPlayer player, CaseFile d) {
        d.team.remove(player.getUUID());
        Engine.SCANS.remove(player.getUUID());
        Engine.save();
        Messages.announce(player, "team_left", 0x9AA0A6);
        for (ServerPlayer m : Engine.online(d, player.server)) {
            Messages.announce(m, "team_departure", 0x9AA0A6, player.getGameProfile().getName());
            Messages.sendState(m);
        }
        Messages.sendState(player);
    }
}
