package io.github.romainmichaux.fieldinvestigations.client;

import java.util.List;

import io.github.romainmichaux.fieldinvestigations.PacketContent;
import io.github.romainmichaux.fieldinvestigations.Packet;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

// Côté client : reçoit l'état de l'enquête, ouvre les écrans, dessine l'instrument du kit et les annonces à l'écran.
public class FieldInvestigationsClient implements ClientModInitializer {
    static CompoundTag state = new CompoundTag();
    static long stateReceived;
    static CompoundTag signal = new CompoundTag();
    static long signalReceived;
    static CompoundTag bearing = new CompoundTag();
    static long bearingReceived;
    static Component announceTitle, announceText;
    static int announceColour;
    static long announceReceived;
    // Réglage propre à ce joueur (config/cobblemon_field_investigations_client.json) : chiffrer les teintes des mini-jeux de couleur.
    static boolean colourAid;

    private static java.nio.file.Path settingsFile() {
        return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("cobblemon_field_investigations_client.json");
    }

    private static void loadSettings() {
        try {
            if (!java.nio.file.Files.exists(settingsFile())) return;
            var json = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(settingsFile())).getAsJsonObject();
            colourAid = json.has("colourAid") && json.get("colourAid").getAsBoolean();
        } catch (Exception e) {
            // fichier illisible : on garde les réglages par défaut
        }
    }

    static void toggleColourAid() {
        colourAid = !colourAid;
        try {
            var json = new com.google.gson.JsonObject();
            json.addProperty("colourAid", colourAid);
            java.nio.file.Files.writeString(settingsFile(), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(json));
        } catch (Exception e) {
            // le réglage vaudra au moins pour cette session
        }
    }

    @Override
    public void onInitializeClient() {
        loadSettings();
        BlockRenderLayerMap.INSTANCE.putBlock(PacketContent.TRACE, RenderType.translucent());
        BlockRenderLayerMap.INSTANCE.putBlock(PacketContent.SOIL, RenderType.cutout());
        ClientPlayNetworking.registerGlobalReceiver(Packet.TYPE, (packet, ctx) -> {
            CompoundTag d = packet.data();
            Minecraft mc = ctx.client();
            mc.execute(() -> {
                long now = System.currentTimeMillis();
                switch (packet.kind()) {
                    case "state" -> {
                        // La cible vient d'être identifiée, ou un rang vient d'être gagné : le client le souligne d'un son.
                        if (state.getString("phase").equals("SPOTTING") && d.getString("phase").equals("APPROACH")) Draw.beep(Sounds.IDENTIFIED, 1f, 0.9f);
                        if (state.contains("career") && d.getCompound("career").getInt("rank") > state.getCompound("career").getInt("rank")) Draw.beep(Sounds.RANK_UP, 1f, 0.9f);
                        state = d;
                        stateReceived = now;
                        PacketContent.clientScan = d.getBoolean("active") && d.getString("phase").equals("SPOTTING");
                    }
                    case "signal" -> {
                        signal = d;
                        signalReceived = now;
                    }
                    case "bearing" -> {
                        bearing = d;
                        bearingReceived = now;
                    }
                    case "announce" -> {
                        if (mc.level == null) return;
                        announceTitle = Component.Serializer.fromJson(d.getString("title"), mc.level.registryAccess());
                        announceText = Component.Serializer.fromJson(d.getString("text"), mc.level.registryAccess());
                        announceColour = d.getInt("colour");
                        announceReceived = now;
                    }
                    case "open" -> {
                        switch (d.getString("screen")) {
                            case "board" -> mc.setScreen(new BoardScreen());
                            case "kit" -> mc.setScreen(new KitScreen());
case "analyse" -> play(mc, new AnalysisScreen(d.getString("kind"), d.getString("game"), d.getLong("seed"),
                                    d.getInt("doses"), d.getBoolean("filet"), d.getBoolean("buried"), false, d.getInt("variant")), d, false);
                            case "sampling" -> play(mc, new AnalysisScreen(d.getString("kind"), d.getString("game"), d.getLong("seed"),
                                    0, false, d.getBoolean("buried"), true, d.getInt("variant")), d, false);
                            case "attempt" -> {
                                boolean field = d.getBoolean("field");
                                play(mc, new AnalysisScreen(d.getString("kind"), d.getString("game"), d.getLong("seed"), field ? 0 : 2,
                                        false, d.getBoolean("buried"), field, d.getInt("variant")), d, true);
                            }
                            case "filter" -> play(mc, new AnalysisScreen("", "filter", d.getLong("seed"), 0, false, false, true, 0), d, false);
                            default -> {}
                        }
                    }
                    default -> {}
                }
            });
        });
        Demo.install();
        HudRenderCallback.EVENT.register((g, delta) -> {
            try {
                hud(g);
            } catch (Exception e) {
                // un souci d'affichage ne doit jamais faire planter le jeu
            }
        });
    }

    // Ouvre un mini-jeu ; la première fois que le joueur le voit, l'écran commence par en expliquer les règles.
    private static void play(Minecraft mc, AnalysisScreen screen, CompoundTag d, boolean attempt) {
        screen.attempt = attempt;
        screen.intro = d.getBoolean("first");
        mc.setScreen(screen);
    }

    // Vrai tant que le joueur maintient le clic droit du kit sur une cible à identifier.
    public static boolean scanActive() {
        Minecraft mc = Minecraft.getInstance();
        return Demo.scan || (mc.player != null && PacketContent.clientScan && mc.player.isUsingItem() && mc.player.getUseItem().is(PacketContent.KIT));
    }

    private static double scanSmooth;
    private static long nextBeep, lastRefusal;

    // Un Pokémon est-il dans le viseur ? (même calcul que côté serveur, pour réagir sans attendre sa réponse)
    private static boolean pokemonVise(Minecraft mc) {
        if (mc.level == null || mc.player == null) return false;
        Vec3 eye = mc.player.getEyePosition(), regard = mc.player.getLookAngle();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof PokemonEntity)) continue;
            Vec3 towards = e.getBoundingBox().getCenter().subtract(eye);
            double dist = towards.length();
            if (dist < 0.5 || dist > 64) continue;
            double angle = Math.acos(Math.max(-1, Math.min(1, regard.dot(towards.normalize()))));
            if (angle <= Math.atan((Math.max(e.getBbWidth(), e.getBbHeight()) / 2 + 0.6) / dist) + Math.toRadians(2.5) && mc.player.hasLineOfSight(e)) return true;
        }
        return false;
    }

    // Appelé à la place de toute l'interface du jeu (voir HudScanMixin) : vrai si la vue du scanner a été dessinée.
    public static boolean drawScanner(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || !scanActive()) return false;
        try {
            scanner(g, mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), System.currentTimeMillis());
        } catch (Exception e) {
            return false; // un souci d'affichage ne doit jamais faire planter le jeu
        }
        return true;
    }

    // Vue du scanner : l'écran s'assombrit autour d'une fenêtre de visée, le viseur change de couleur selon ce qu'il
    // accroche et un anneau se remplit pendant les trois secondes d'analyse.
    private static void scanner(GuiGraphics g, Minecraft mc, int l, int h, long now) {
        Draw.withoutZone();
        boolean fresh = now - signalReceived < 1500 && signal.getString("phase").equals("SPOTTING");
        int scan = Demo.scan ? 3 : fresh ? signal.getInt("scan") : 0, distance = fresh ? signal.getInt("distance") : 0;
        boolean other = fresh && signal.getBoolean("other"), tooClose = fresh && distance < signal.getInt("alert") + 6;
        boolean aimed = scan > 0 || pokemonVise(mc), blink = (now / 250) % 2 == 0;
        // Le scan dure 6 demi-secondes, ou 4 avec le scan rapide du rang d'inspecteur.
        double scanDuration = fresh && signal.getInt("scanMax") > 0 ? signal.getInt("scanMax") : 6;
        scanSmooth += (Math.min(1, scan / scanDuration) - scanSmooth) * 0.12;
        // Bips du scanner : de plus en plus rapprochés et aigus à mesure que l'analyse avance ; un bourdonnement grave si ce n'est pas la cible.
        if (!Demo.scan && scan > 0 && now >= nextBeep) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(Sounds.SCAN, (float) (0.75 + scanSmooth * 1.25), 0.5f));
            nextBeep = now + (long) (400 - scanSmooth * 280);
        } else if (!Demo.scan && other && now - lastRefusal > 1200) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(Sounds.FAILURE, 0.6f, 0.5f));
            lastRefusal = now;
        }
        // La fenêtre de visée occupe environ 60 % de l'écran, quelle que soit sa taille.
        int cx = l / 2, cy = h / 2, vl = Math.min(l / 2 - 16, Math.max(150, l * 3 / 10)), vh = Math.min(h / 2 - 34, Math.max(86, h * 3 / 10));
        int colour = other ? Draw.ALERT : scan > 0 ? Draw.GREEN : aimed ? Draw.YELLOW : Draw.ACCENT;
        g.fill(0, 0, l, cy - vh, 0xC0060A0E);
        g.fill(0, cy + vh, l, h, 0xC0060A0E);
        g.fill(0, cy - vh, cx - vl, cy + vh, 0x90060A0E);
        g.fill(cx + vl, cy - vh, l, cy + vh, 0x90060A0E);
        g.fill(cx - vl, cy - vh, cx + vl, cy + vh, 0x1830D8C8);
        int by = cy - vh + (int) ((now % 1800) / 1800.0 * (2 * vh));
        g.fill(cx - vl, by, cx + vl, by + 1, 0x5060F0D0);
        g.fill(cx - vl, by + 1, cx + vl, by + 4, 0x1860F0D0);
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sy = -1; sy <= 1; sy += 2) {
                int x = cx + sx * vl, y = cy + sy * vh;
                g.fill(Math.min(x, x - sx * 14), sy < 0 ? y : y - 2, Math.max(x, x - sx * 14), sy < 0 ? y + 2 : y, colour);
                g.fill(sx < 0 ? x : x - 2, Math.min(y, y - sy * 14), sx < 0 ? x + 2 : x, Math.max(y, y - sy * 14), colour);
            }
        }
        Draw.tint(g, colour);
        Draw.image(g, Draw.VIEWFINDER, cx - 48, cy - 48, 96, 96);
        g.setColor(1f, 1f, 1f, 1f);
        for (int i = 0; i < 48; i++) {
            double a = Math.PI * 2 * i / 48;
            boolean full = i / 48.0 < scanSmooth;
            Draw.point(g, cx - 1 + Math.sin(a) * 56, cy - 1 - Math.cos(a) * 56, full ? 3 : 1, full ? Draw.GREEN : 0x80FFFFFF);
        }
        g.fill(cx - 1, cy - 1, cx + 1, cy + 1, 0xFFFFFFFF);
        // Bandeau du haut : nom de l'instrument et distance ; bandeau du bas : ce que voit le scanner.
        Draw.write(g, T.f("scanner"), cx - vl, cy - vh - 13, Draw.ACCENT, false);
        if (fresh) {
            String d = distance + " m";
            Draw.write(g, d, cx + vl - Draw.textFont().width(d), cy - vh - 13, tooClose && blink ? Draw.ALERT : Draw.TEXT, false);
        }
        String state = other ? T.f("this_is_not_your_target") : tooClose ? T.f("too_close_back_off") : scan > 0 ? T.f("scanning") + Math.round(scanSmooth * 100) + " %"
                : aimed ? T.f("locking_on") : T.f("aim_at_the_pok_mon");
        Draw.centre(g, state, cx, cy + vh + 6, other || (tooClose && blink) ? Draw.ALERT : colour);
    }

    static void send(Packet p) {
        if (ClientPlayNetworking.canSend(Packet.TYPE)) ClientPlayNetworking.send(p);
    }

    private static void hud(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        Draw.withoutZone();
        long now = System.currentTimeMillis();
        int l = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();

        long age = now - announceReceived;
        if (age < 5500 && announceTitle != null && announceText != null) {
            String title = announceTitle.getString();
            List<FormattedCharSequence> lines = Draw.textFont().split(announceText, 250);
            int w = Draw.textFont().width(title);
            for (FormattedCharSequence s : lines) w = Math.max(w, Draw.textFont().width(s));
            w += 30;
            int top = 16 + lines.size() * 10;
            double slides = age < 250 ? age / 250.0 : age > 5200 ? (5500 - age) / 300.0 : 1;
            int x = (l - w) / 2, y = (int) (-top + (top + 8) * slides);
            int colour = Draw.opaque(announceColour);
            Draw.plate(g, x - 5, y - 6, w + 10, top + 11);
            g.fill(x, y + 1, x + 4, y + top - 1, colour);
            Draw.write(g, title, x + 11, y + 4, colour, false);
            for (int i = 0; i < lines.size(); i++) Draw.write(g, lines.get(i), x + 11, y + 15 + i * 10, Draw.TEXT, false);
        }

        // Instrument du kit de terrain, au-dessus de la barre d'objets : un détecteur avec son écran sonar (à gauche)
        // et son afficheur (à droite). La texture vient de PixelLab ; le balayage, l'écho et le texte sont dessinés par-dessus.
        if (!(mc.player.isHolding(PacketContent.KIT) || Demo.active) || mc.screen != null || now - signalReceived > 1500) return;
        String phase = signal.getString("phase");
        int x = (l - 200) / 2, y = h - 100;
        Draw.image(g, Draw.RADAR, x, y, 200, 48);
        int cx = x + 33, cy = y + 24, ex = x + 57, ey = y + 15;
        String line1, line2 = "";
        int colour1 = Draw.TEXT, colour2 = Draw.GREY;
        double sweep = (now % 2000) / 2000.0 * Math.PI * 2;
        boolean needle = false;
        switch (phase) {
            case "SAMPLING" -> {
                int level2 = Math.min(5, signal.getInt("level"));
                String[] captions = {T.f("no_sign"), T.f("faint_signs"), T.f("clear_signs"), T.f("fresh_signs"), T.f("trace_very_close"), T.f("right_here")};
                int[] colours = {Draw.GREY, Draw.GREY, Draw.YELLOW, 0xFFE9A15B, Draw.GREEN, Draw.ACCENT};
                if (signal.getBoolean("day")) {
                    // Enquête nocturne, en plein jour : il n'y a rien à relever.
                    line1 = T.f("wait_for_nightfall");
                    colour1 = 0xFF8FA8FF;
                    line2 = T.f("no_traces_by_day");
                } else if (level2 < 0) {
                    line1 = T.f("all_sampled");
                    colour1 = Draw.GREEN;
                } else {
                    line1 = captions[level2];
                    colour1 = colours[level2];
                    if (signal.getBoolean("cave") && signal.contains("metres")) {
                        // Trace en grotte : à cette distance et tant de blocs plus bas (ou plus haut).
                        int below = signal.getInt("below");
                        line2 = T.f("in_a_cave") + String.format("%.0f", signal.getDouble("metres")) + " m" + (below > 1 ? " ▼" + below : below < -1 ? " ▲" + -below : "");
                        colour2 = 0xFFD0A070;
                    } else if (signal.getBoolean("buried") && signal.contains("metres")) {
                        line2 = T.f("buried") + String.format("%.1f", signal.getDouble("metres")) + " m";
                        colour2 = Draw.YELLOW;
                    } else if (signal.getBoolean("buried")) {
                        line2 = T.f("buried_clue");
                        colour2 = Draw.YELLOW;
                    } else if (level2 == 0) {
                        line2 = T.f("zone_at") + signal.getInt("zone") + T.f("blocks_2");
                    }
                    // Force du signal : six barres au bord de l'afficheur, et un écho qui grossit et bat plus vite sur le sonar.
                    for (int i = 0; i < 6; i++) {
                        int hb = 3 + i * 2;
                        g.fill(x + 152 + i * 4, y + 32 - hb, x + 155 + i * 4, y + 32, i <= level2 ? colours[level2] : 0xFF12303A);
                    }
                    if (level2 > 0) {
                        long period = 1300 - level2 * 200L;
                        double beat = (now % period) / (double) period;
                        int radius = 1 + (int) Math.round(beat * (2 + level2 * 1.4));
                        Draw.circle(g, cx - 0.5, cy - 0.5, radius, 1, Draw.darken(0xFF6CF0A0, 1 - beat * 0.6));
                        g.fill(cx - 1, cy - 1, cx + 1, cy + 1, 0xFFB8FFD0);
                    }
                }
            }
            case "TRACKING" -> {
                if (now - bearingReceived < 9000 && bearing.contains("cap")) {
                    double cap = bearing.getDouble("cap");
                    // Aiguille relative au regard du joueur : elle tourne quand il tourne la tête.
                    sweep = Math.toRadians(cap - (mc.player.getYRot() + 180));
                    needle = true;
                    String[] forces = {T.f("very_far"), T.f("far"), T.f("clear"), T.f("close")};
                    line1 = T.f("cry") + forces[Math.min(3, bearing.getInt("force"))];
                    colour1 = Draw.ACCENT;
                    line2 = Draw.wind(cap) + " · " + Math.round(cap) + "° ± " + Math.round(bearing.getDouble("error")) + "°";
                    colour2 = Draw.TEXT;
                } else if (signal.getBoolean("day")) {
                    line1 = T.f("wait_for_nightfall");
                    colour1 = 0xFF8FA8FF;
                    line2 = T.f("target_asleep_no_cry");
                } else {
                    line1 = signal.getBoolean("ready") ? T.f("right_click_listen") : T.f("recalibrating");
                    line2 = T.f("sneak_map");
                }
            }
            case "SPOTTING" -> {
                // La cible est en vue : il faut la scanner de loin (clic droit maintenu, viseur dessus) avant d'approcher.
                int scan = signal.getInt("scan"), distance = signal.getInt("distance");
                boolean tooClose = distance < signal.getInt("alert") + 6, blink = (now / 250) % 2 == 0;
                line1 = scan > 0 ? T.f("scanning") + Math.min(100, scan * 100 / Math.max(1, signal.getInt("scanMax") > 0 ? signal.getInt("scanMax") : 6)) + " %" : T.f("scan_the_target");
                colour1 = scan > 0 ? Draw.ACCENT : Draw.YELLOW;
                line2 = signal.getBoolean("other") ? T.f("not_your_target") : tooClose ? T.f("too_close_back_off") : T.f("right_click") + distance + " m";
                colour2 = signal.getBoolean("other") || (tooClose && blink) ? Draw.ALERT : Draw.GREY;
                for (int i = 0; i < scan; i++) Draw.circle(g, cx - 0.5, cy - 0.5, 2 + i * 1.4, 1, 0xFF6CF0A0);
            }
            case "APPROACH" -> {
                boolean course = signal.getBoolean("course");
                boolean blink = (now / 250) % 2 == 0;
                line1 = T.f("target_at") + signal.getInt("distance") + T.f("blocks_2");
                colour1 = Draw.YELLOW;
                line2 = course ? T.f("don_t_run") : T.f("approach_quietly");
                colour2 = course && blink ? Draw.ALERT : Draw.GREY;
            }
            default -> {
                line1 = T.f("flushed_out_3");
                colour1 = Draw.GREEN;
                line2 = T.f("catch_it");
            }
        }
        Draw.segment(g, cx - 0.5, cy - 0.5, cx - 0.5 + Math.sin(sweep) * 8, cy - 0.5 - Math.cos(sweep) * 8, 1, needle ? Draw.YELLOW : 0xFF3EA162);
        int place = line1.equals(T.f("all_sampled")) || signal.getBoolean("day") || !phase.equals("SAMPLING") ? 119 : 93;
        Draw.scroll(g, line1, ex, ey, place, colour1);
        Draw.scroll(g, line2, ex, ey + 9, place, colour2);
    }
}
