package io.github.romainmichaux.fieldinvestigations;

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
    private static final Set<String> SHARED = Set.of("analysis_done", "sample_destroyed", "kit_calibrated", "target_moved", "contact_lost",
            "target_vanished", "sensed", "heard", "very_close", "flushed", "clue_uncovered", "clue_destroyed", "team_joined", "team_departure");

    static void send(ServerPlayer player, Packet p) {
        if (ServerPlayNetworking.canSend(player, Packet.TYPE)) ServerPlayNetworking.send(player, p);
    }

    static MutableComponent tr(String key, Object... args) {
        return Component.translatable("cobblemon_field_investigations." + key, args);
    }

    // Les annonces partent sous forme de textes traduisibles : chaque joueur les lit dans la langue de son jeu.
    public static void announce(ServerPlayer player, Component title, Component text, int colour) {
        Packet p = Packet.from("announce");
        p.data().putString("title", Component.Serializer.toJson(title, player.registryAccess()));
        p.data().putString("text", Component.Serializer.toJson(text, player.registryAccess()));
        p.data().putInt("colour", colour);
        send(player, p);
    }

    public static void announce(ServerPlayer player, String key, int colour, Object... args) {
        Component title = tr("announce." + key + ".title", args), text = tr("announce." + key + ".text", args);
        announce(player, title, text, colour);
        if (SHARED.contains(key)) {
            for (ServerPlayer other : Engine.teammates(player)) announce(other, title, text, colour);
        }
    }

    static String starsText(int n) {
        return "★".repeat(Math.max(0, Math.min(5, n))) + "☆".repeat(Math.max(0, 5 - n));
    }

    static void open(ServerPlayer player, String screen) {
        sync(player);
        Packet p = Packet.from("open");
        p.data().putString("screen", screen);
        send(player, p);
    }

    // Tout ce que le client a le droit de savoir sur l'enquête du joueur (jamais l'espèce avant qu'elle soit débusquée).
    static void sync(ServerPlayer player) {
        sendState(player);
        for (ServerPlayer other : Engine.teammates(player)) sendState(other);
    }

    static void sendState(ServerPlayer player) {
        Packet p = Packet.from("state");
        CompoundTag t = p.data();
        CaseFile d = Engine.caseFile(player);
        t.putBoolean("active", d != null);
        t.putInt("passed", Engine.state.succeeded.getOrDefault(player.getUUID(), 0));
        t.putLong("waiting", Math.max(0, Engine.state.nextInvestigation.getOrDefault(player.getUUID(), 0L) - System.currentTimeMillis()));
        t.putInt("difficulty", "easy".equalsIgnoreCase(Engine.settings.miniGameDifficulty) ? -1 : "hard".equalsIgnoreCase(Engine.settings.miniGameDifficulty) ? 1 : 0);
        Profile profile = Engine.profile(player.getUUID());
        int rank = Engine.rank(profile.xp);
        CompoundTag career = new CompoundTag();
        career.putInt("xp", profile.xp);
        career.putInt("rank", rank);
        career.putInt("threshold", Engine.THRESHOLDS[rank]);
        career.putInt("next", rank + 1 < Engine.THRESHOLDS.length ? Engine.THRESHOLDS[rank + 1] : -1);
        int perfectOnes = 0, specials = 0;
        Set<String> speciesSet = new HashSet<>();
        CompoundTag types = new CompoundTag();
        ListTag archives = new ListTag();
        for (int i = profile.history.size() - 1; i >= 0; i--) {
            CaseRecord f = profile.history.get(i);
            String type = f.special == null ? "" : f.special;
            if (f.stars >= 5) perfectOnes++;
            if (!type.isEmpty()) specials++;
            speciesSet.add(f.species);
            types.putInt(type.isEmpty() ? "standard" : type, types.getInt(type.isEmpty() ? "standard" : type) + 1);
            if (archives.size() >= 40) continue;
            CompoundTag c = new CompoundTag();
            c.putString("species", f.species);
            c.putString("special", type);
            c.putInt("stars", f.stars);
            c.putInt("xp", f.xp);
            c.putBoolean("capture", f.capture);
            c.putLong("date", f.date);
            archives.add(c);
        }
        career.putInt("perfect", perfectOnes);
        career.putInt("specials", specials);
        career.putInt("species_count", speciesSet.size());
        career.put("types", types);
        career.put("archives", archives);
        t.put("career", career);
        ListTag team = new ListTag(), closeOnes = new ListTag();
        boolean leader = d != null && player.getUUID().equals(d.leader);
        if (d != null) {
            for (UUID u : Engine.members(d)) team.add(StringTag.valueOf(Engine.name(player.server, u)));
        }
        if (leader) {
            // Les joueurs sans enquête à moins de 64 blocs : d'abord ceux avec qui ce joueur a déjà résolu le plus
            // d'enquêtes, puis par ordre alphabétique.
            List<ServerPlayer> freeOnes = new ArrayList<>();
            for (ServerPlayer other : player.serverLevel().players()) {
                if (other != player && other.distanceToSqr(player) < 64 * 64 && Engine.caseFile(other) == null) freeOnes.add(other);
            }
            freeOnes.sort((a, b) -> {
                int na = profile.partners.getOrDefault(a.getUUID(), 0), nb = profile.partners.getOrDefault(b.getUUID(), 0);
                return na != nb ? Integer.compare(nb, na) : a.getGameProfile().getName().compareToIgnoreCase(b.getGameProfile().getName());
            });
            for (ServerPlayer other : freeOnes) {
                if (closeOnes.size() >= 80) break;
                CompoundTag c = new CompoundTag();
                c.putString("name", other.getGameProfile().getName());
                c.putInt("together", profile.partners.getOrDefault(other.getUUID(), 0));
                closeOnes.add(c);
            }
        }
        UUID inviter = d == null ? Team.invitation(player) : null;
        t.put("team", team);
        t.put("close", closeOnes);
        t.putBoolean("chef", leader);
        t.putInt("teamMax", Engine.settings.maxTeamSize);
        t.putString("invitation", inviter == null ? "" : Engine.name(player.server, inviter));
        List<CaseFile> leads = d == null ? Reports.leads(player) : null;
        if (leads != null) {
            // Ce que le joueur sait d'un signalement avant de le choisir : sa rareté, son type, où se trouve la zone.
            ListTag list = new ListTag();
            for (CaseFile o : leads) {
                CompoundTag c = new CompoundTag();
                c.putInt("rank", Math.max(0, Reports.CATEGORIES.indexOf(o.category)));
                c.putString("special", o.special == null ? "" : o.special);
                c.putBoolean("new", o.newB);
                c.putDouble("zoneX", o.zoneX);
                c.putDouble("zoneZ", o.zoneZ);
                list.add(c);
            }
            t.put("leads", list);
            t.putLong("leadsEnd", Math.max(0, Engine.state.offersEnd.getOrDefault(player.getUUID(), 0L) - System.currentTimeMillis()));
        }
        if (d != null) {
            Species target = PokemonSpecies.getByName(d.species);
            t.putString("phase", d.phase);
            t.putInt("rank", Math.max(0, Reports.CATEGORIES.indexOf(d.category)));
            t.putBoolean("new", d.newB);
            t.putDouble("zoneX", d.zoneX);
            t.putDouble("zoneZ", d.zoneZ);
            t.putInt("rayonZone", Engine.radius(d));
            t.putInt("required", Engine.required(d));
            t.putString("special", d.special == null ? "" : d.special);
            t.putString("pronostic", d.guess == null ? "" : d.guess);
            ListTag traces = new ListTag();
            for (Trace tr : d.traces) {
                CompoundTag c = new CompoundTag();
                c.putString("kind", tr.kind);
                c.putBoolean("harvest", tr.harvest);
                c.putBoolean("buried", tr.buried);
                c.putInt("variant", tr.variant);
                c.putBoolean("uncovered", tr.uncovered);
                c.putBoolean("lost", tr.lost);
                c.putBoolean("pin", d.pins.contains(tr.kind));
                if (d.pins.contains(tr.kind) && target != null) c.putString("value", Genetics.value(tr.kind, target));
                traces.add(c);
            }
            t.put("traces", traces);
            ListTag suspects = new ListTag();
            PokedexManager pokedex = Cobblemon.playerDataManager.getPokedexData(player);
            for (String s : d.suspects) {
                Species esp = PokemonSpecies.getByName(s);
                if (esp == null) continue;
                CompoundTag c = new CompoundTag();
                c.putString("species", s);
                ListTag values = new ListTag();
                for (String m : Genetics.MARKERS) values.add(StringTag.valueOf(Genetics.value(m, esp)));
                c.put("values", values);
                c.putBoolean("ruled_out", d.ruledOut != null && d.ruledOut.contains(s));
                c.putBoolean("owned", Reports.owns(pokedex, s));
                suspects.add(c);
            }
            t.put("suspects", suspects);
            ListTag bearings = new ListTag();
            for (Bearing r : d.bearings) {
                CompoundTag c = new CompoundTag();
                c.putDouble("x", r.x);
                c.putDouble("z", r.z);
                c.putDouble("cap", r.cap);
                c.putDouble("error", r.error);
                c.putBoolean("expired", r.expired);
                bearings.add(c);
            }
            t.put("bearings", bearings);
            Tracking.CrossFix fix1 = Tracking.crossFix(d);
            t.putBoolean("fixValid", fix1.valid());
            t.putString("fixMessage", fix1.message());
            if (fix1.valid()) {
                t.putDouble("fixX", fix1.x());
                t.putDouble("fixZ", fix1.z());
                t.putDouble("fixUncertainty", fix1.uncertainty());
            }
            boolean identified = "APPROACH".equals(d.phase) || "FLUSHED".equals(d.phase);
            if (identified) t.putString("species", d.species);
            // Tant que la cible n'est pas identifiée, le client ne doit pas pouvoir deviner si le pronostic est juste.
            t.putInt("stars", Engine.stars(d, identified));
            t.putInt("fautes", d.faults);
            t.putInt("leaks", d.leaks);
        }
        send(player, p);
    }
}
