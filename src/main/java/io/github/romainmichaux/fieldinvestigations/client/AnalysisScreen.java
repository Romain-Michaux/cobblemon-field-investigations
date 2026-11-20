package io.github.romainmichaux.fieldinvestigations.client;

import java.util.Random;

import com.mojang.math.Axis;

import io.github.romainmichaux.fieldinvestigations.Genetics;
import io.github.romainmichaux.fieldinvestigations.Packet;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

// DNA sequencer: each sample kind is analysed through a different mini-game.
// Leaving the screen before the end wastes a dose, like a failure.
public class AnalysisScreen extends Screen {
    private static final int AREA_W = 280, AREA_H = 118;
    private final String kind, gameId;
    private final boolean sampling, mesh, buried, filtering;
    private final int doses, variant;
    private final MiniGame game;
    // Practice game (/hunt game command): nothing is sent to the server at the end.
    boolean attempt;
    // First time the player sees this mini-game: the rules are shown, and nothing starts until they click "Start".
    boolean intro;
    private boolean sent;
    private int endSince = -1;
    private int ticks;

    // The tracked creature's print and height (metres), sent with a sampling: the footprint measurement draws it.
    public AnalysisScreen print(String shape, float height) {
        if (game instanceof MeasureGame m && !shape.isEmpty()) m.setPrint(shape, height);
        return this;
    }

    // Demo check: does the first-time screen of this game fit above its Start button? Returns a report line.
    static String introCheck(String gameId) {
        boolean field = Genetics.FIELD_GAMES.contains(gameId);
        AnalysisScreen screen = new AnalysisScreen("stat", gameId, 1L, 2, false, false, field, 0);
        String consequence = screen.filtering ? T.f("a_failure_costs_no_star") : field ? T.f("a_failure_damages_the_sample") : T.f("a_failure_loses_one_dose");
        int lines = Draw.textFont().split(Component.literal(screen.game.instruction()), AREA_W - 32).size();
        int more = Draw.textFont().split(Component.literal(consequence), AREA_W - 32).size();
        int bottom = 24 + lines * 10 + 4 + more * 10;
        return (bottom > AREA_H - 28 ? "OVERFLOW " : "ok       ") + gameId + " : " + lines + " + " + more + " lines, bottom " + bottom + " / " + (AREA_H - 28);
    }

    public AnalysisScreen(String kind, String gameId, long seed, int doses, boolean mesh, boolean buried, boolean sampling, int variant) {
        super(Component.literal(T.f("dna_sequencer")));
        this.kind = kind;
        this.gameId = gameId;
        this.sampling = sampling;
        this.buried = buried;
        this.doses = doses;
        this.mesh = mesh;
        this.variant = variant;
        // Cry filtering is played during tracking, before a bearing: no vial or star at stake.
        this.filtering = gameId.equals("filter");
        Random rng = new Random(seed);
        this.game = switch (gameId) {
            case "crystals" -> new CrystalsGame(rng);
            case "pipette" -> new PipetteGame(rng);
            case "measure" -> new MeasureGame(rng);
            case "sorting" -> new SortingGame(rng);
            case "trace" -> new TracingGame(rng);
            case "brush" -> new BrushGame(rng, kind, variant);
            case "sieving" -> new SieveGame(rng, kind, variant);
            case "cast" -> new CastGame(rng);
            case "tweezers" -> new TweezersGame(rng);
            case "photo" -> new PhotoGame(rng);
            case "swab" -> new SwabGame(rng);
            case "dive" -> new DiveGame(rng);
            case "climb" -> new ClimbGame(rng, kind);
            case "abseil" -> new AbseilGame(rng, kind);
            case "lamp" -> new UvLampGame(rng);
            case "rockfall" -> new RockfallGame(rng);
            case "filter" -> new FilterGame(rng);
            case "microscope" -> new MicroscopeGame(rng);
            case "chromatography" -> new ChromatographyGame(rng);
            case "family_tree" -> new FamilyTreeGame(rng);
            case "titration" -> new TitrationGame(rng);
            case "bite" -> new BiteGame(rng);
            case "thaw" -> new ThawGame(rng);
            case "pairing" -> new PairingGame(rng);
            case "centrifugation" -> new CentrifugeGame(rng);
            case "fragments" -> new FragmentsGame(rng, kind);
            case "spectrum" -> new SpectrumGame(rng);
            case "gel" -> new GelGame(rng);
            case "colorimetry" -> new ColorimetryGame(rng);
            case "pcr" -> new PcrGame(rng);
            case "count" -> new CountGame(rng);
            default -> new SequencingGame(rng);
        };
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0xA0070B10);
    }

    @Override
    public void tick() {
        if (intro) return;
        ticks++;
        // Pace set by the server: on easy, the game skips one tick in three; on hard, it plays an extra one every four.
        int pace = FieldInvestigationsClient.state.getInt("difficulty");
        if (!game.finished && !(pace < 0 && ticks % 3 == 0)) {
            game.tick();
            if (pace > 0 && ticks % 4 == 0 && !game.finished) game.tick();
        }
        if (game.finished && endSince < 0) {
            endSince = ticks;
            Draw.note(game.success ? 1.6f : 0.6f);
            if (game.success) Draw.beep(Sounds.SUCCESS, game.perfect ? 1.4f : 1f, 0.6f);
            else Draw.beep(Sounds.FAILURE, 0.5f, 0.9f);
        }
        if (endSince >= 0 && ticks - endSince > 32) onClose();
    }

    @Override
    public void removed() {
        // Closing the screen during the explanation costs nothing: the game has not started.
        if (!sent && !attempt && !intro) {
            sent = true;
            Packet p = Packet.from(filtering ? "filter" : sampling ? "sampling" : "analyse");
            p.data().putBoolean("succeeded", game.finished && game.success);
            p.data().putBoolean("perfect", game.finished && game.success && game.perfect);
            FieldInvestigationsClient.send(p);
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g, mx, my, pt);
        // Sampling happens in the field, on the kit screen; analysis, on the sequencer screen.
        Draw.Backdrop backdrop = sampling ? Draw.KIT : Draw.SEQUENCER;
        int x = backdrop.x(width), y = backdrop.y(height), w = backdrop.il();
        Draw.background(g, backdrop, width, height);
        int zx = x + (w - AREA_W) / 2, zy;
        if (sampling) {
            Draw.banner(g, x, y, w, T.c(Genetics.gameName(gameId)).toUpperCase(), filtering ? T.f("cry_bearing") : T.c(Genetics.traceName(kind, buried, variant)));
            zy = y + 18;
        } else {
            String gameName = T.c(Genetics.gameName(gameId)).toUpperCase();
            String title = T.f("dna_sequencer_2") + gameName;
            if (Draw.textFont().width(title) > 220) title = gameName;
            Draw.centre(g, title, x + w / 2, y + 176, 0xFF0E3B3A);
            Draw.write(g, T.c(Genetics.traceName(kind, buried, variant)) + T.f("doses") + doses + (mesh ? T.f("first_mistake_is_free") : ""),
                    zx, y + 3, Draw.GREY, false);
            zy = y + 15;
        }
        Draw.inset(g, zx, zy, AREA_W, AREA_H);
        game.ox = zx;
        game.oy = zy;
        game.draw(g, mx, my, pt);
        MiniGame.Control[] controls = game.controls();
        if (controls != null) Draw.controls(g, controls, zx + AREA_W / 2, zy + AREA_H + 4, AREA_W + 24);
        else if (!intro) Draw.paragraph(g, game.instruction(), zx, zy + AREA_H + 3, AREA_W, Draw.GREY);
        if (intro) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 350);
            g.fill(zx, zy, zx + AREA_W, zy + AREA_H, 0xEA0C141C);
            Draw.centre(g, T.f("first_time") + T.c(Genetics.gameName(gameId)).toUpperCase(), zx + AREA_W / 2, zy + 8, Draw.YELLOW);
            int ly = Draw.paragraph(g, game.instruction(), zx + 16, zy + 24, AREA_W - 32, Draw.TEXT);
            Draw.paragraph(g, filtering ? T.f("a_failure_costs_no_star")
                    : sampling ? T.f("a_failure_damages_the_sample")
                    : T.f("a_failure_loses_one_dose"), zx + 16, ly + 4, AREA_W - 32, Draw.GREY);
            boolean hover = Draw.in(mx, my, zx + AREA_W / 2 - 60, zy + AREA_H - 26, 120, 20);
            Draw.button(g, zx + AREA_W / 2 - 60, zy + AREA_H - 26, 120, 20, T.f("start"), hover, true, Draw.GREEN);
            g.pose().popPose();
        }
        if (endSince >= 0) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 300);
            g.fill(zx + 40, zy + 40, zx + AREA_W - 40, zy + 78, Draw.EDGE);
            g.fill(zx + 42, zy + 42, zx + AREA_W - 42, zy + 76, game.success ? 0xFF245C3A : 0xFF6B2420);
            if (filtering) {
                Draw.centre(g, game.success ? T.f("cry_isolated") : T.f("cry_lost_in_noise"), zx + AREA_W / 2, zy + 49, Draw.TEXT);
                Draw.centre(g, game.success ? T.f("precise_bearing") : T.f("less_precise_bearing"), zx + AREA_W / 2, zy + 62, Draw.GREY);
            } else if (sampling) {
                Draw.centre(g, game.success ? (game.perfect ? T.f("perfect_sample") : T.f("sample_taken")) : T.f("sample_damaged"), zx + AREA_W / 2, zy + 49, Draw.TEXT);
                Draw.centre(g, game.success ? (game.perfect ? T.f("one_extra_dose") : T.f("full_sample")) : T.f("a_single_dose_half_star"), zx + AREA_W / 2, zy + 62, Draw.GREY);
            } else {
                Draw.centre(g, game.success ? T.f("analysis_complete") : T.f("analysis_failed"), zx + AREA_W / 2, zy + 49, Draw.TEXT);
                Draw.centre(g, game.success ? T.f("marker") + T.c(Genetics.MARKER_NAMES.get(kind)) + T.f("added_to_the_case_file")
                        : mesh ? T.f("the_sequencer_saved_the_dose") : T.f("one_dose_lost_half_star"), zx + AREA_W / 2, zy + 62, Draw.GREY);
            }
            g.pose().popPose();
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (intro) {
            Draw.Backdrop backdrop = sampling ? Draw.KIT : Draw.SEQUENCER;
            int zx = backdrop.x(width) + (backdrop.il() - AREA_W) / 2, zy = backdrop.y(height) + (sampling ? 18 : 15);
            if (Draw.in(mx, my, zx + AREA_W / 2 - 60, zy + AREA_H - 26, 120, 20)) {
                intro = false;
                Draw.click(1.2f);
            }
            return true;
        }
        game.mouseDown = true;
        if (!game.finished) game.click(mx, my);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (!intro && !game.finished) game.scrolled(dy);
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        game.mouseDown = false;
        if (!game.finished) game.released(mx, my);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (intro && (key == 257 || key == 32)) {
            intro = false;
            Draw.click(1.2f);
            return true;
        }
        if (intro) return super.keyPressed(key, scan, mods);
        if (!game.finished && game.keyPressed(key)) return true;
        return super.keyPressed(key, scan, mods);
    }

    // ---------- 5. Spectrum tuning: adjust three parameters so your curve overlays the sample's ----------

    private static final class SpectrumGame extends MiniGame {
        static final String[] NAMES = {T.f("frequency"), T.f("amplitude"), T.f("phase")};
        final int[] target = new int[3], setting = new int[3];
        int time = 900, tunedTicks;

        SpectrumGame(Random rng) {
            do {
                for (int k = 0; k < 3; k++) {
                    target[k] = rng.nextInt(7);
                    setting[k] = rng.nextInt(7);
                }
            } while (gap() < 5);
        }

        int gap() {
            int n = 0;
            for (int k = 0; k < 3; k++) n += Math.abs(target[k] - setting[k]);
            return n;
        }

        static double curve(int[] p, double t) {
            return Math.sin(t * (1 + p[0] * 0.5) * Math.PI * 2 + p[2] * Math.PI * 2 / 7) * (5 + p[1] * 2.5);
        }

        double likeness() {
            double total = 0;
            for (int i = 0; i < 64; i++) total += Math.abs(curve(target, i / 64.0) - curve(setting, i / 64.0));
            return Math.max(0, 1 - total / 64 / 20);
        }

        @Override
        void tick() {
            if (--time <= 0) end(false);
            hurryTicks(time, 900);
            if (gap() == 0) {
                if (tunedTicks == 0) Draw.beep(Sounds.CHIME, 1.5f, 0.6f);
                if (++tunedTicks > 6) end(true);
            }
        }

        @Override
        void draw(GuiGraphics g, int mx, int my, float pt) {
            int px = ox + 8, py = oy + 6, pl = 150, ph = 54;
            g.fill(px, py, px + pl, py + ph, Draw.EDGE);
            g.fill(px + 1, py + 1, px + pl - 1, py + ph - 1, 0xFF10231F);
            g.fill(px + 1, py + ph / 2, px + pl - 1, py + ph / 2 + 1, 0xFF1D3B34);
            for (int i = 2; i < pl - 3; i++) {
                double t = i / (double) pl;
                Draw.point(g, px + i, py + ph / 2 - curve(target, t), 2, 0xFF7A8A99);
                Draw.point(g, px + i, py + ph / 2 - curve(setting, t), 2, gap() == 0 ? Draw.GREEN : Draw.ACCENT);
            }
            Draw.write(g, T.f("grey_sample"), px, py + ph + 3, Draw.DARK, false);
            int percent = (int) Math.round(likeness() * 100);
            Draw.write(g, T.f("match") + percent + " %", px, py + ph + 14, percent > 85 ? Draw.GREEN : Draw.YELLOW, false);
            g.fill(px, oy + AREA_H - 12, px + pl, oy + AREA_H - 6, Draw.EDGE);
            g.fill(px + 1, oy + AREA_H - 11, px + 1 + (int) ((pl - 2) * time / 900.0), oy + AREA_H - 7, time > 200 ? Draw.ACCENT : Draw.ALERT);
            for (int k = 0; k < 3; k++) {
                int y = oy + 8 + k * 34;
                Draw.write(g, NAMES[k], ox + 170, y, Draw.TEXT, false);
                Draw.button(g, ox + 168, y + 10, 22, 18, "-", Draw.in(mx, my, ox + 168, y + 10, 22, 18), setting[k] > 0, 0xFF5E7890);
                for (int i = 0; i < 7; i++) g.fill(ox + 194 + i * 8, y + 15, ox + 200 + i * 8, y + 23, i == setting[k] ? Draw.ACCENT : Draw.EDGE);
                Draw.button(g, ox + 252, y + 10, 22, 18, "+", Draw.in(mx, my, ox + 252, y + 10, 22, 18), setting[k] < 6, 0xFF5E7890);
            }
        }

        @Override
        void click(double mx, double my) {
            for (int k = 0; k < 3; k++) {
                int y = oy + 8 + k * 34;
                if (Draw.in(mx, my, ox + 168, y + 10, 22, 18) && setting[k] > 0) {
                    setting[k]--;
                    tunedTicks = 0;
                    Draw.click(0.9f + setting[k] * 0.08f);
                } else if (Draw.in(mx, my, ox + 252, y + 10, 22, 18) && setting[k] < 6) {
                    setting[k]++;
                    tunedTicks = 0;
                    Draw.click(0.9f + setting[k] * 0.08f);
                }
            }
        }

        @Override
        String instruction() {
            return T.f("tune_frequency_amplitude_and_phase");
        }
    }
}
