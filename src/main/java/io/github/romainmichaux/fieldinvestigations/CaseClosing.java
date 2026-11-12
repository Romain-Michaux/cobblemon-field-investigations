package io.github.romainmichaux.fieldinvestigations;

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

// End of an investigation: capture or KO, rewards, experience, archives.
final class CaseClosing {
    // Loot table name per investigation kind (data/cobblemon_field_investigations/loot_table/investigation/kind_<name>).
    static final Map<String, String> KIND_NAMES = Map.ofEntries(Map.entry("nocturnal", "nocturnal"), Map.entry("skittish", "skittish"),
            Map.entry("exceptional", "exceptional"), Map.entry("restless", "restless"), Map.entry("aquatic", "aquatic"),
            Map.entry("dig_site", "dig_site"), Map.entry("canopy", "canopy"), Map.entry("altitude", "highlands"), Map.entry("frost", "frost"),
            Map.entry("mist", "mist"), Map.entry("underground", "underground"));

    private CaseClosing() {}

    static void capture(PokemonCapturedEvent evt) {
        UUID caught = evt.getPokemon().getUuid();
        for (Map.Entry<UUID, CaseFile> en : new ArrayList<>(Engine.state.caseFiles.entrySet())) {
            CaseFile d = en.getValue();
            if (!caught.equals(d.pokemon)) continue;
            ServerPlayer catcher = evt.getPlayer();
            List<ServerPlayer> present = Engine.online(d, catcher.server);
            Engine.ENTITIES.remove(en.getKey());
            if (Engine.members(d).contains(catcher.getUUID())) {
                solve(catcher, d, true); // caught by the leader or a teammate: solved for the whole team
            } else if (present.isEmpty()) {
                Engine.state.caseFiles.remove(en.getKey());
                Engine.save();
            } else {
                finish(present.get(0), d, Messages.tr("announce.too_late.title"), Messages.tr("announce.too_late.text"), 0xE8543A);
            }
            return;
        }
    }

    // Investigation solved: each investigator present gets their loot and experience, then the case is closed.
    static void solve(ServerPlayer player, CaseFile d, boolean capture) {
        List<ServerPlayer> present = Engine.online(d, player.server);
        for (ServerPlayer m : present) reward(m, d, capture);
        for (ServerPlayer m : present) {
            for (ServerPlayer other : present) {
                if (other != m) Engine.profile(m.getUUID()).partners.merge(other.getUUID(), 1, Integer::sum);
            }
        }
        closeCase(player, d);
    }

    // Investigation completed (target caught or knocked out): loot is rolled from the mod's loot tables.
    // More stars means a richer table; species rarity and milestones add their share.
    private static void reward(ServerPlayer player, CaseFile d, boolean capture) {
        int total = Engine.state.succeeded.merge(player.getUUID(), 1, Integer::sum);
        int stars = Engine.stars(d);
        // Experience: ten points per star, plus species rarity, investigation kind and novelty.
        Profile profile = Engine.profile(player.getUUID());
        int rankBefore = Engine.rank(profile.xp);
        int gain = 10 * stars + new int[] {0, 5, 10, 20}[Math.max(0, Reports.CATEGORIES.indexOf(d.category))] + (d.special != null ? 10 : 0) + (d.newSpecies ? 5 : 0);
        profile.xp += gain;
        CaseRecord entry = new CaseRecord();
        entry.species = d.species;
        entry.special = d.special;
        entry.category = d.category;
        entry.stars = stars;
        entry.xp = gain;
        entry.capture = capture;
        entry.date = System.currentTimeMillis();
        profile.history.add(entry);
        while (profile.history.size() > 200) profile.history.remove(0);
        int rankAfter = Engine.rank(profile.xp);
        if (rankAfter >= 3) Engine.grantAdvancement(player, "rank_inspector");
        boolean milestone = Engine.settings.milestoneEveryCases > 0 && total % Engine.settings.milestoneEveryCases == 0;
        List<ItemStack> gains = new ArrayList<>(loot(player, "investigation/stars_" + stars));
        gains.addAll(loot(player, "investigation/rarity_" + d.category.replace('-', '_')));
        if (milestone) gains.addAll(loot(player, "investigation/milestone"));
        if (d.special != null) {
            gains.addAll(loot(player, "investigation/special"));
            if (Engine.is(d, "skittish") || Engine.is(d, "restless")) gains.addAll(loot(player, "investigation/special"));
            gains.addAll(loot(player, "investigation/kind_" + KIND_NAMES.getOrDefault(d.special, d.special))); // kind-specific table, empty if it does not exist
            Engine.grantAdvancement(player, "special_investigation");
        }
        Engine.grantAdvancement(player, "first_investigation");
        if (stars >= 5) Engine.grantAdvancement(player, "five_stars");
        if (total >= 10) Engine.grantAdvancement(player, "ten_investigations");
        MutableComponent list = Component.empty();
        boolean first = true;
        for (ItemStack stack : gains) {
            if (stack.isEmpty()) continue;
            if (!first) list.append(", ");
            first = false;
            list.append(stack.getCount() + "× ").append(stack.getHoverName());
            player.getInventory().placeItemBackInInventory(stack);
        }
        player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8f, 1f);
        Messages.announce(player, Messages.tr("announce.solved.title", Messages.starsText(stars)),
                Messages.tr("announce.solved.text", Engine.speciesName(d.species), Messages.tr(capture ? "announce.solved.capture" : "announce.solved.defeated"), total,
                        milestone ? Messages.tr("announce.solved.milestone") : Component.empty(), first ? Component.empty() : Messages.tr("announce.solved.loot", list),
                        rankAfter > rankBefore ? Messages.tr("announce.solved.rank", gain, Messages.tr("rank." + rankAfter)) : Messages.tr("announce.solved.xp", gain)), 0x6CCB5F);
        if (rankAfter > rankBefore) player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1f, 0.8f);
    }

    private static List<ItemStack> loot(ServerPlayer player, String table) {
        try {
            ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.fromNamespaceAndPath("cobblemon_field_investigations", table));
            LootTable t = player.server.reloadableRegistries().getLootTable(key);
            LootParams params = new LootParams.Builder(player.serverLevel()).withParameter(LootContextParams.ORIGIN, player.position())
                    .withOptionalParameter(LootContextParams.THIS_ENTITY, player).create(LootContextParamSets.CHEST);
            return t.getRandomItems(params);
        } catch (Exception e) {
            System.err.println("[Field Investigations] Unreadable loot table: " + table + ": " + e);
            return List.of();
        }
    }

    // Closes the case for the whole team: traces removed, cooldown before the next investigation for each member.
    static void closeCase(ServerPlayer player, CaseFile d) {
        if (d.dimension.equals(Engine.dimension(player))) Traces.removeTraces(player.serverLevel(), d);
        Engine.state.caseFiles.remove(d.leader);
        Engine.ENTITIES.remove(d.leader);
        for (UUID u : Engine.members(d)) {
            Engine.SCANS.remove(u);
            Engine.state.nextInvestigation.put(u, System.currentTimeMillis() + Engine.settings.minutesBetweenCases * 60_000L / (Engine.rank(Engine.profile(u).xp) >= 6 ? 2 : 1));
        }
        Engine.save();
        for (ServerPlayer m : Engine.online(d, player.server)) Messages.sendState(m);
    }

    static void finish(ServerPlayer player, CaseFile d, Component title, Component text, int colour) {
        closeCase(player, d);
        for (ServerPlayer m : Engine.online(d, player.server)) Messages.announce(m, title, text, colour);
    }
}
