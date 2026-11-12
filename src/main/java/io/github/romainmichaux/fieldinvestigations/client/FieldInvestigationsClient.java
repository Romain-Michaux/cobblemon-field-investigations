package io.github.romainmichaux.fieldinvestigations.client;

import java.util.List;

import io.github.romainmichaux.fieldinvestigations.ModContent;
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

// Client side: receives the investigation state, opens screens, draws the kit instrument and on-screen announcements.
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
    // Per-player setting (config/cobblemon_field_investigations_client.json): show hues as numbers in the colour mini-games.
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
            // unreadable file: keep the default settings
        }
    }

    static void toggleColourAid() {
        colourAid = !colourAid;
        try {
            var json = new com.google.gson.JsonObject();
            json.addProperty("colourAid", colourAid);
            java.nio.file.Files.writeString(settingsFile(), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(json));
        } catch (Exception e) {
            // the setting still applies for this session
        }
    }

    @Override
    public void onInitializeClient() {
        loadSettings();
        BlockRenderLayerMap.INSTANCE.putBlock(ModContent.TRACE, RenderType.translucent());
        BlockRenderLayerMap.INSTANCE.putBlock(ModContent.DISTURBED_SOIL, RenderType.cutout());
        ClientPlayNetworking.registerGlobalReceiver(Packet.TYPE, (packet, ctx) -> {
            CompoundTag d = packet.data();
            Minecraft mc = ctx.client();
            mc.execute(() -> {
                long now = System.currentTimeMillis();
                switch (packet.kind()) {
                    case "state" -> {
                        // The target has just been identified, or a rank has just been gained: play a sound.
                        if (state.getString("phase").equals("SPOTTING") && d.getString("phase").equals("APPROACH")) Draw.beep(Sounds.IDENTIFIED, 1f, 0.9f);
                        if (state.contains("career") && d.getCompound("career").getInt("rank") > state.getCompound("career").getInt("rank")) Draw.beep(Sounds.RANK_UP, 1f, 0.9f);
                        state = d;
                        stateReceived = now;
                        ModContent.clientScan = d.getBoolean("active") && d.getString("phase").equals("SPOTTING");
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
                                    d.getInt("doses"), d.getBoolean("mesh"), d.getBoolean("buried"), false, d.getInt("variant")), d, false);
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
                // a rendering problem must never crash the game
            }
        });
    }

    // Opens a mini-game; the first time the player sees it, the screen starts by explaining the rules.
    private static void play(Minecraft mc, AnalysisScreen screen, CompoundTag d, boolean attempt) {
        screen.attempt = attempt;
        screen.intro = d.getBoolean("first");
        mc.setScreen(screen);
    }

    // True while the player holds right click with the kit on a target to identify.
    public static boolean scanActive() {
        Minecraft mc = Minecraft.getInstance();
        return Demo.scan || (mc.player != null && ModContent.clientScan && mc.player.isUsingItem() && mc.player.getUseItem().is(ModContent.KIT));
    }

    private static double scanSmooth;
    private static long nextBeep, lastRefusal;

    // Is a Pokémon in the viewfinder? (same computation as the server, to react without waiting for its answer)
    private static boolean pokemonAimed(Minecraft mc) {
        if (mc.level == null || mc.player == null) return false;
        Vec3 eye = mc.player.getEyePosition(), look = mc.player.getLookAngle();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof PokemonEntity)) continue;
            Vec3 towards = e.getBoundingBox().getCenter().subtract(eye);
            double dist = towards.length();
            if (dist < 0.5 || dist > 64) continue;
            double angle = Math.acos(Math.max(-1, Math.min(1, look.dot(towards.normalize()))));
            if (angle <= Math.atan((Math.max(e.getBbWidth(), e.getBbHeight()) / 2 + 0.6) / dist) + Math.toRadians(2.5) && mc.player.hasLineOfSight(e)) return true;
        }
        return false;
    }

    // Called instead of the whole game HUD (see HudScanMixin): true if the scanner view was drawn.
    public static boolean drawScanner(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || !scanActive()) return false;
        try {
            scanner(g, mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), System.currentTimeMillis());
        } catch (Exception e) {
            return false; // a rendering problem must never crash the game
        }
        return true;
    }

    // Scanner view: the screen darkens around an aiming window, the viewfinder changes colour depending on what it
    // locks onto, and a ring fills during the three seconds of analysis.
    private static void scanner(GuiGraphics g, Minecraft mc, int l, int h, long now) {
        Draw.withoutZone();
        boolean fresh = now - signalReceived < 1500 && signal.getString("phase").equals("SPOTTING");
        int scan = Demo.scan ? 3 : fresh ? signal.getInt("scan") : 0, distance = fresh ? signal.getInt("distance") : 0;
        boolean other = fresh && signal.getBoolean("other"), tooClose = fresh && distance < signal.getInt("alert") + 6;
        boolean aimed = scan > 0 || pokemonAimed(mc), blink = (now / 250) % 2 == 0;
        // The scan lasts 6 half-seconds, or 4 with the inspector rank's fast scan.
        double scanDuration = fresh && signal.getInt("scanMax") > 0 ? signal.getInt("scanMax") : 6;
        scanSmooth += (Math.min(1, scan / scanDuration) - scanSmooth) * 0.12;
        // Scanner beeps get faster and higher as the analysis progresses; a low buzz if it is not the target.
        if (!Demo.scan && scan > 0 && now >= nextBeep) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(Sounds.SCAN, (float) (0.75 + scanSmooth * 1.25), 0.5f));
            nextBeep = now + (long) (400 - scanSmooth * 280);
        } else if (!Demo.scan && other && now - lastRefusal > 1200) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(Sounds.FAILURE, 0.6f, 0.5f));
            lastRefusal = now;
        }
        // The aiming window takes about 60% of the screen, whatever its size.
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
        // Top strip: instrument name and distance; bottom strip: what the scanner sees.
        Draw.write(g, T.f("scanner"), cx - vl, cy - vh - 13, Draw.ACCENT, false);
        if (fresh) {
            String d = distance + " m";
            Draw.write(g, d, cx + vl - Draw.textFont().width(d), cy - vh - 13, tooClose && blink ? Draw.ALERT : Draw.TEXT, false);
        }
        String status = other ? T.f("this_is_not_your_target") : tooClose ? T.f("too_close_back_off") : scan > 0 ? T.f("scanning") + Math.round(scanSmooth * 100) + " %"
                : aimed ? T.f("locking_on") : T.f("aim_at_the_pok_mon");
        Draw.centre(g, status, cx, cy + vh + 6, other || (tooClose && blink) ? Draw.ALERT : colour);
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
            double slide = age < 250 ? age / 250.0 : age > 5200 ? (5500 - age) / 300.0 : 1;
            int x = (l - w) / 2, y = (int) (-top + (top + 8) * slide);
            int colour = Draw.opaque(announceColour);
            Draw.plate(g, x - 5, y - 6, w + 10, top + 11);
            g.fill(x, y + 1, x + 4, y + top - 1, colour);
            Draw.write(g, title, x + 11, y + 4, colour, false);
            for (int i = 0; i < lines.size(); i++) Draw.write(g, lines.get(i), x + 11, y + 15 + i * 10, Draw.TEXT, false);
        }

        // Field kit instrument, above the hotbar: a detector with its sonar screen (left) and its display (right).
        // The texture comes from PixelLab; the sweep, echo and text are drawn on top.
        if (!(mc.player.isHolding(ModContent.KIT) || Demo.active) || mc.screen != null || now - signalReceived > 1500) return;
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
                int level = Math.min(5, signal.getInt("level"));
                String[] captions = {T.f("no_sign"), T.f("faint_signs"), T.f("clear_signs"), T.f("fresh_signs"), T.f("trace_very_close"), T.f("right_here")};
                int[] colours = {Draw.GREY, Draw.GREY, Draw.YELLOW, 0xFFE9A15B, Draw.GREEN, Draw.ACCENT};
                if (signal.getBoolean("day")) {
                    // Nocturnal investigation in daylight: there is nothing to sample.
                    line1 = T.f("wait_for_nightfall");
                    colour1 = 0xFF8FA8FF;
                    line2 = T.f("no_traces_by_day");
                } else if (level < 0) {
                    line1 = T.f("all_sampled");
                    colour1 = Draw.GREEN;
                } else {
                    line1 = captions[level];
                    colour1 = colours[level];
                    if (signal.getBoolean("cave") && signal.contains("metres")) {
                        // Cave trace: at this distance and this many blocks lower (or higher).
                        int below = signal.getInt("below");
                        line2 = T.f("in_a_cave") + String.format("%.0f", signal.getDouble("metres")) + " m" + (below > 1 ? " ▼" + below : below < -1 ? " ▲" + -below : "");
                        colour2 = 0xFFD0A070;
                    } else if (signal.getBoolean("buried") && signal.contains("metres")) {
                        line2 = T.f("buried") + String.format("%.1f", signal.getDouble("metres")) + " m";
                        colour2 = Draw.YELLOW;
                    } else if (signal.getBoolean("buried")) {
                        line2 = T.f("buried_clue");
                        colour2 = Draw.YELLOW;
                    } else if (level == 0) {
                        line2 = T.f("zone_at") + signal.getInt("zone") + T.f("blocks_2");
                    }
                    // Signal strength: six bars at the edge of the display, and an echo on the sonar that grows and pulses faster.
                    for (int i = 0; i < 6; i++) {
                        int hb = 3 + i * 2;
                        g.fill(x + 152 + i * 4, y + 32 - hb, x + 155 + i * 4, y + 32, i <= level ? colours[level] : 0xFF12303A);
                    }
                    if (level > 0) {
                        long period = 1300 - level * 200L;
                        double beat = (now % period) / (double) period;
                        int radius = 1 + (int) Math.round(beat * (2 + level * 1.4));
                        Draw.circle(g, cx - 0.5, cy - 0.5, radius, 1, Draw.darken(0xFF6CF0A0, 1 - beat * 0.6));
                        g.fill(cx - 1, cy - 1, cx + 1, cy + 1, 0xFFB8FFD0);
                    }
                }
            }
            case "TRACKING" -> {
                if (now - bearingReceived < 9000 && bearing.contains("heading")) {
                    double heading = bearing.getDouble("heading");
                    // Needle relative to where the player looks: it turns when they turn their head.
                    sweep = Math.toRadians(heading - (mc.player.getYRot() + 180));
                    needle = true;
                    String[] strengths = {T.f("very_far"), T.f("far"), T.f("clear"), T.f("close")};
                    line1 = T.f("cry") + strengths[Math.min(3, bearing.getInt("force"))];
                    colour1 = Draw.ACCENT;
                    line2 = Draw.wind(heading) + " · " + Math.round(heading) + "° ± " + Math.round(bearing.getDouble("error")) + "°";
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
                // Target in sight: it must be scanned from afar (hold right click, viewfinder on it) before approaching.
                int scan = signal.getInt("scan"), distance = signal.getInt("distance");
                boolean tooClose = distance < signal.getInt("alert") + 6, blink = (now / 250) % 2 == 0;
                line1 = scan > 0 ? T.f("scanning") + Math.min(100, scan * 100 / Math.max(1, signal.getInt("scanMax") > 0 ? signal.getInt("scanMax") : 6)) + " %" : T.f("scan_the_target");
                colour1 = scan > 0 ? Draw.ACCENT : Draw.YELLOW;
                line2 = signal.getBoolean("other") ? T.f("not_your_target") : tooClose ? T.f("too_close_back_off") : T.f("right_click") + distance + " m";
                colour2 = signal.getBoolean("other") || (tooClose && blink) ? Draw.ALERT : Draw.GREY;
                for (int i = 0; i < scan; i++) Draw.circle(g, cx - 0.5, cy - 0.5, 2 + i * 1.4, 1, 0xFF6CF0A0);
            }
            case "APPROACH" -> {
                boolean course = signal.getBoolean("sprinting");
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
        int textWidth = line1.equals(T.f("all_sampled")) || signal.getBoolean("day") || !phase.equals("SAMPLING") ? 119 : 93;
        Draw.scroll(g, line1, ex, ey, textWidth, colour1);
        Draw.scroll(g, line2, ex, ey + 9, textWidth, colour2);
    }
}
