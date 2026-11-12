package io.github.romainmichaux.fieldinvestigations;

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
final class Samples {
    private Samples() {}

    public static void analyse(ServerPlayer player) {
        CaseFile d = Engine.caseFile(player);
        ItemStack stack = player.getMainHandItem();
        if (!stack.is(PacketContent.SAMPLE)) return;
        if (!player.getInventory().contains(new ItemStack(PacketContent.KIT))) {
            Messages.announce(player, "sample_needs_kit", 0xF2C93A);
            return;
        }
        CompoundTag data = PacketContent.data(stack);
        if (d == null || d.id != data.getLong("investigation") || data.getBoolean("analyse")) {
            Messages.announce(player, "too_old", 0xE8543A);
            return;
        }
        Engine.ANALYSES_LIST.put(player.getUUID(), Boolean.TRUE);
        Packet p = Packet.from("open");
        p.data().putString("screen", "analyse");
        p.data().putString("kind", data.getString("kind"));
        String analysisGame = Genetics.analysisGame(data.getString("kind"), data.getString("game"));
        p.data().putString("game", analysisGame);
        p.data().putBoolean("first", Engine.firstTime(player, analysisGame));
        p.data().putBoolean("buried", data.getBoolean("buried"));
        p.data().putInt("variant", data.getInt("variant"));
        p.data().putLong("seed", Engine.RANDOM.nextLong());
        p.data().putInt("doses", data.getInt("doses"));
        Messages.send(player, p);
    }

    static void analysisResult(ServerPlayer player, boolean succes) {
        if (Engine.ANALYSES_LIST.remove(player.getUUID()) == null) return;
        CaseFile d = Engine.caseFile(player);
        ItemStack stack = player.getMainHandItem();
        if (d == null || !stack.is(PacketContent.SAMPLE)) return;
        CompoundTag data = PacketContent.data(stack);
        if (data.getBoolean("analyse") || data.getLong("investigation") != d.id) return;
        String kind = data.getString("kind");
        Species target = PokemonSpecies.getByName(d.species);
        if (succes && target != null) {
            stack.shrink(1);
            if (!d.pins.contains(kind)) d.pins.add(kind);
            Engine.save();
            player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5f, 1.6f);
            Messages.announce(player, "analysis_done", 0x6CCB5F, Component.translatable(Genetics.MARKER_NAMES.get(kind)),
                    Genetics.label(kind, Genetics.value(kind, target)), d.pins.size(), Engine.required(d));
            Engine.progress1(player, "first_analysis");
            Messages.sync(player);
            return;
        }
        int doses = data.getInt("doses") - 1;
        Engine.fault(player, d);
        player.playNotifySound(SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.6f, 1.2f);
        if (doses <= 0) {
            stack.shrink(1);
            Messages.announce(player, "sample_destroyed", 0xE8543A, Messages.starsText(Engine.stars(d, false)));
            Messages.sync(player);
        } else {
            data.putInt("doses", doses);
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
            Messages.announce(player, "dose_lost", 0xE8543A, doses, Messages.starsText(Engine.stars(d, false)));
            Messages.sync(player);
        }
    }

    public static void takeSample(ServerPlayer player, BlockPos pos) {
        CaseFile d = Engine.caseFile(player);
        ServerLevel level1 = player.serverLevel();
        Trace trace = null;
        if (d != null && "SAMPLING".equals(d.phase)) {
            for (Trace t : d.traces) {
                if (t.placedOne && !t.harvest && t.px == pos.getX() && t.py == pos.getY() && t.pz == pos.getZ()) trace = t;
            }
        }
        if (trace == null) {
            boolean hasSomeone = false;
            for (CaseFile other : Engine.state.caseFiles.values()) {
                for (Trace t : other.traces) hasSomeone |= t.placedOne && !t.harvest && t.px == pos.getX() && t.py == pos.getY() && t.pz == pos.getZ();
            }
            if (hasSomeone) {
                Messages.announce(player, "other_trace", 0xF2C93A);
            } else {
                level1.removeBlock(pos, false);
                Messages.announce(player, "old_trace", 0x9AA0A6);
            }
            return;
        }
        if (Engine.asleep(d, level1)) {
            Messages.announce(player, "nocturnal_trace", 0xF2C93A);
            return;
        }
        boolean spare = Engine.rank(player) >= 2 && !d.vialGiven;
        if (player.getInventory().findSlotMatchingItem(new ItemStack(PacketContent.VIAL)) < 0 && !player.isCreative() && !spare) {
            Messages.announce(player, "vial_required", 0xE8543A);
            return;
        }
        Engine.SAMPLINGS.put(player.getUUID(), pos.immutable());
        Packet p = Packet.from("open");
        p.data().putString("screen", "sampling");
        p.data().putString("kind", trace.kind);
        String fieldGame = Genetics.samplingGame(trace.kind, trace.buried, trace.cave, d.special, new Random(d.seed ^ trace.kind.hashCode()));
        p.data().putString("game", fieldGame);
        p.data().putBoolean("first", Engine.firstTime(player, fieldGame));
        p.data().putBoolean("buried", trace.buried);
        p.data().putInt("variant", trace.variant);
        p.data().putLong("seed", Engine.RANDOM.nextLong());
        Messages.send(player, p);
    }

    // Résultat du mini-jeu de prélèvement : réussi = échantillon complet (une dose de plus si sans faute), raté = une seule dose.
    static void samplingResult(ServerPlayer player, boolean succes, boolean perfect) {
        BlockPos pos = Engine.SAMPLINGS.remove(player.getUUID());
        CaseFile d = Engine.caseFile(player);
        if (pos == null || d == null || !"SAMPLING".equals(d.phase)) return;
        ServerLevel level1 = player.serverLevel();
        Trace trace = null;
        for (Trace t : d.traces) {
            if (t.placedOne && !t.harvest && t.px == pos.getX() && t.py == pos.getY() && t.pz == pos.getZ()) trace = t;
        }
        if (trace == null || player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 100) return;
        int vial = player.getInventory().findSlotMatchingItem(new ItemStack(PacketContent.VIAL));
        // Fiole de secours (rang 2) : le premier prélèvement de l'enquête n'en consomme pas.
        boolean spare = Engine.rank(player) >= 2 && !d.vialGiven;
        if (vial < 0 && !player.isCreative() && !spare) return;
        if (spare) d.vialGiven = true;
        else if (vial >= 0 && !player.isCreative()) player.getInventory().getItem(vial).shrink(1);
        int doses = succes ? Engine.settings.dosesPerSample + (perfect ? 1 : 0) : 1;
        if (Engine.is(d, "frost")) doses = Math.max(1, doses - 1); // échantillon gelé, fragile
        ItemStack sample = new ItemStack(PacketContent.SAMPLE);
        CompoundTag data = new CompoundTag();
        data.putLong("investigation", d.id);
        data.putString("kind", trace.kind);
        data.putInt("doses", doses);
        data.putBoolean("buried", trace.buried);
        data.putInt("variant", trace.variant);
        List<String> games = Genetics.ANALYSIS_GAMES.getOrDefault(trace.kind, List.of("sequencing"));
        // Piste gelée : une fois sur deux, l'échantillon doit d'abord être dégelé.
        data.putString("game", Engine.is(d, "frost") && Engine.RANDOM.nextBoolean() ? "thaw" : games.get(Engine.RANDOM.nextInt(games.size())));
        sample.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        player.getInventory().placeItemBackInInventory(sample);
        trace.harvest = true;
        if (!succes) d.faults++;
        level1.removeBlock(pos, false);
        if (trace.buried) Traces.reboucher(level1, trace);
        Engine.save();
        player.playNotifySound(SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS, 1f, 1.1f);
        Engine.progress1(player, "first_sample");
        long left1 = d.traces.stream().filter(t -> !t.over()).count();
        Messages.announce(player, Messages.tr("announce." + (succes ? (perfect ? "sample_perfect" : "sample_ok") : "sample_damaged") + ".title"),
                Messages.tr("announce.sampling.text", Component.translatable(Genetics.traceName(trace.kind, trace.buried, trace.variant)), doses,
                        left1 > 0 ? Messages.tr("announce.sampling.left", left1) : Messages.tr("announce.sampling.finished")), succes ? 0x6CCB5F : 0xF2C93A);
        Messages.sync(player);
    }
}
