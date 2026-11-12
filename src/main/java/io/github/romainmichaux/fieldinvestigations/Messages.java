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

// Everything sent to the client: announcements, screen opening, investigation state.
final class Messages {
    private Messages() {}

    // Announcements about the whole case: teammates receive them too.
    private static final Set<String> SHARED = Set.of("analysis_done", "sample_destroyed", "kit_calibrated", "target_moved", "contact_lost",
            "target_vanished", "sensed", "heard", "very_close", "flushed", "clue_uncovered", "clue_destroyed", "team_joined", "team_departure");

    static void send(ServerPlayer player, Packet p) {
        if (ServerPlayNetworking.canSend(player, Packet.TYPE)) ServerPlayNetworking.send(player, p);
    }

    static MutableComponent tr(String key, Object... args) {
        return Component.translatable("cobblemon_field_investigations." + key, args);
    }

    // Announcements are sent as translatable text, so each player reads them in their own game language.
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

    // Everything the client may know about the player's investigation (never the species before it is flushed out).
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
        int perfectCount = 0, specials = 0;
        Set<String> speciesSet = new HashSet<>();
        CompoundTag types = new CompoundTag();
        ListTag archives = new ListTag();
        for (int i = profile.history.size() - 1; i >= 0; i--) {
            CaseRecord entry = profile.history.get(i);
            String type = entry.special == null ? "" : entry.special;
            if (entry.stars >= 5) perfectCount++;
            if (!type.isEmpty()) specials++;
            speciesSet.add(entry.species);
            types.putInt(type.isEmpty() ? "standard" : type, types.getInt(type.isEmpty() ? "standard" : type) + 1);
            if (archives.size() >= 40) continue;
            CompoundTag c = new CompoundTag();
            c.putString("species", entry.species);
            c.putString("special", type);
            c.putInt("stars", entry.stars);
            c.putInt("xp", entry.xp);
            c.putBoolean("capture", entry.capture);
            c.putLong("date", entry.date);
            archives.add(c);
        }
        career.putInt("perfect", perfectCount);
        career.putInt("specials", specials);
        career.putInt("species_count", speciesSet.size());
        career.put("types", types);
        career.put("archives", archives);
        t.put("career", career);
        ListTag team = new ListTag(), nearby = new ListTag();
        boolean leader = d != null && player.getUUID().equals(d.leader);
        if (d != null) {
            for (UUID u : Engine.members(d)) team.add(StringTag.valueOf(Engine.name(player.server, u)));
        }
        if (leader) {
            // Players without an investigation within 64 blocks: first those this player has solved the most cases
            // with, then alphabetically.
            List<ServerPlayer> available = new ArrayList<>();
            for (ServerPlayer other : player.serverLevel().players()) {
                if (other != player && other.distanceToSqr(player) < 64 * 64 && Engine.caseFile(other) == null) available.add(other);
            }
            available.sort((a, b) -> {
                int na = profile.partners.getOrDefault(a.getUUID(), 0), nb = profile.partners.getOrDefault(b.getUUID(), 0);
                return na != nb ? Integer.compare(nb, na) : a.getGameProfile().getName().compareToIgnoreCase(b.getGameProfile().getName());
            });
            for (ServerPlayer other : available) {
                if (nearby.size() >= 80) break;
                CompoundTag c = new CompoundTag();
                c.putString("name", other.getGameProfile().getName());
                c.putInt("together", profile.partners.getOrDefault(other.getUUID(), 0));
                nearby.add(c);
            }
        }
        UUID inviter = d == null ? Team.invitation(player) : null;
        t.put("team", team);
        t.put("close", nearby);
        t.putBoolean("chef", leader);
        t.putInt("teamMax", Engine.settings.maxTeamSize);
        t.putString("invitation", inviter == null ? "" : Engine.name(player.server, inviter));
        List<CaseFile> leads = d == null ? Reports.leads(player) : null;
        if (leads != null) {
            // What the player knows about a lead before choosing it: its rarity, its kind, where the zone is.
            ListTag list = new ListTag();
            for (CaseFile o : leads) {
                CompoundTag c = new CompoundTag();
                c.putInt("rank", Math.max(0, Reports.CATEGORIES.indexOf(o.category)));
                c.putString("special", o.special == null ? "" : o.special);
                c.putBoolean("new", o.newSpecies);
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
            t.putBoolean("new", d.newSpecies);
            t.putDouble("zoneX", d.zoneX);
            t.putDouble("zoneZ", d.zoneZ);
            t.putInt("rayonZone", Engine.radius(d));
            t.putInt("required", Engine.required(d));
            t.putString("special", d.special == null ? "" : d.special);
            t.putString("pronostic", d.guess == null ? "" : d.guess);
            ListTag traces = new ListTag();
            for (Trace trace : d.traces) {
                CompoundTag c = new CompoundTag();
                c.putString("kind", trace.kind);
                c.putBoolean("harvest", trace.sampled);
                c.putBoolean("buried", trace.buried);
                c.putInt("variant", trace.variant);
                c.putBoolean("uncovered", trace.uncovered);
                c.putBoolean("lost", trace.lost);
                c.putBoolean("pin", d.pins.contains(trace.kind));
                if (d.pins.contains(trace.kind) && target != null) c.putString("value", Genetics.value(trace.kind, target));
                traces.add(c);
            }
            t.put("traces", traces);
            ListTag suspects = new ListTag();
            PokedexManager pokedex = Cobblemon.playerDataManager.getPokedexData(player);
            for (String s : d.suspects) {
                Species species = PokemonSpecies.getByName(s);
                if (species == null) continue;
                CompoundTag c = new CompoundTag();
                c.putString("species", s);
                ListTag values = new ListTag();
                for (String m : Genetics.MARKERS) values.add(StringTag.valueOf(Genetics.value(m, species)));
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
                c.putDouble("cap", r.heading);
                c.putDouble("error", r.error);
                c.putBoolean("expired", r.expired);
                bearings.add(c);
            }
            t.put("bearings", bearings);
            Tracking.CrossFix fix = Tracking.crossFix(d);
            t.putBoolean("fixValid", fix.valid());
            t.putString("fixMessage", fix.message());
            if (fix.valid()) {
                t.putDouble("fixX", fix.x());
                t.putDouble("fixZ", fix.z());
                t.putDouble("fixUncertainty", fix.uncertainty());
            }
            boolean identified = "APPROACH".equals(d.phase) || "FLUSHED".equals(d.phase);
            if (identified) t.putString("species", d.species);
            // Until the target is identified, the client must not be able to tell whether the guess is right.
            t.putInt("stars", Engine.stars(d, identified));
            t.putInt("fautes", d.faults);
            t.putInt("leaks", d.flees);
        }
        send(player, p);
    }
}
