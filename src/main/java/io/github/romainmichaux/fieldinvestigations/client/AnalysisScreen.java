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
            case "fragments" -> new FragmentsGame(rng);
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

    // ---------- 1. Sequencing: find 4 bases in 6 reads ----------

    private static final class SequencingGame extends MiniGame {
        final int[] code = new int[4];
        final int[][] attempts = new int[6][4];
        final int[][] feedback = new int[6][2];
        int line;

        SequencingGame(Random rng) {
            for (int k = 0; k < 4; k++) code[k] = rng.nextInt(4);
            for (int[] e : attempts) java.util.Arrays.fill(e, -1);
        }

        @Override
        void draw(GuiGraphics g, int mx, int my, float pt) {
            for (int l = 0; l < 6; l++) {
                int y = oy + 6 + l * 18;
                Draw.write(g, String.valueOf(l + 1), ox + 8, y + 4, l == line ? Draw.YELLOW : Draw.DARK, false);
                for (int k = 0; k < 4; k++) Draw.base(g, ox + 22 + k * 20, y, 15, attempts[l][k], l == line && Draw.in(mx, my, ox + 22 + k * 20, y, 15, 15));
                if (l < line) {
                    for (int k = 0; k < 4; k++) {
                        int c = k < feedback[l][0] ? Draw.GREEN : k < feedback[l][0] + feedback[l][1] ? Draw.YELLOW : Draw.INSET_LIGHT;
                        g.fill(ox + 110 + k * 11, y + 4, ox + 118 + k * 11, y + 12, Draw.EDGE);
                        g.fill(ox + 111 + k * 11, y + 5, ox + 117 + k * 11, y + 11, c);
                    }
                } else if (l == line) {
                    Draw.button(g, ox + 110, y - 2, 50, 18, T.f("read"), Draw.in(mx, my, ox + 110, y - 2, 50, 18), complete(), Draw.ACCENT);
                }
            }
            g.fill(ox + 190, oy + 10, ox + 198, oy + 18, Draw.GREEN);
            Draw.write(g, T.f("right_place"), ox + 202, oy + 10, Draw.GREY, false);
            g.fill(ox + 190, oy + 24, ox + 198, oy + 32, Draw.YELLOW);
            Draw.write(g, T.f("wrong_place"), ox + 202, oy + 24, Draw.GREY, false);
            Draw.write(g, T.f("reads_left") + (6 - line), ox + 190, oy + 46, Draw.TEXT, false);
        }

        boolean complete() {
            for (int v : attempts[line]) {
                if (v < 0) return false;
            }
            return true;
        }

        @Override
        void click(double mx, double my) {
            int y = oy + 6 + line * 18;
            for (int k = 0; k < 4; k++) {
                if (Draw.in(mx, my, ox + 22 + k * 20, y, 15, 15)) {
                    attempts[line][k] = (attempts[line][k] + 1) % 4;
                    Draw.click(1f + attempts[line][k] * 0.15f);
                    return;
                }
            }
            if (!Draw.in(mx, my, ox + 110, y - 2, 50, 18) || !complete()) return;
            int exact = 0, present = 0;
            int[] codeLeft = new int[4], attemptsLeft = new int[4];
            for (int k = 0; k < 4; k++) {
                if (attempts[line][k] == code[k]) exact++;
                else {
                    codeLeft[code[k]]++;
                    attemptsLeft[attempts[line][k]]++;
                }
            }
            for (int b = 0; b < 4; b++) present += Math.min(codeLeft[b], attemptsLeft[b]);
            feedback[line][0] = exact;
            feedback[line][1] = present;
            Draw.note(0.8f + exact * 0.2f);
            line++;
            if (exact == 4) end(true);
            else if (line >= 6) end(false);
            else attempts[line] = attempts[line - 1].clone();
        }

        @Override
        String instruction() {
            return T.f("click_the_slots_to_pick");
        }
    }

    // ---------- 2. Strand pairing: give the complementary base before time runs out ----------

    private static final class PairingGame extends MiniGame {
        static final int[] COMPLEMENT = {1, 0, 3, 2};
        static final int[] KEYS = {65, 84, 67, 71};
        final int[] strand = new int[12];
        int position, errors, time;
        int flash, flashOk;

        PairingGame(Random rng) {
            for (int k = 0; k < strand.length; k++) strand[k] = rng.nextInt(4);
        }

        int duration() {
            return Math.max(20, 38 - position * 2);
        }

        @Override
        void tick() {
            if (flash > 0) flash--;
            if (++time > duration()) fault();
            else if (time == duration() * 2 / 3) Draw.beep(Sounds.TICK, 1.6f, 0.5f);
        }

        void fault() {
            errors++;
            flash = 6;
            flashOk = 0;
            Draw.note(0.5f);
            next();
            if (errors >= 3) end(false);
        }

        void next() {
            position++;
            time = 0;
            if (position >= strand.length && !finished) end(errors < 3);
        }

        void answer(int b) {
            if (finished || position >= strand.length) return;
            if (b == COMPLEMENT[strand[position]]) {
                flash = 6;
                flashOk = 1;
                Draw.click(1.2f + position * 0.05f);
                next();
            } else fault();
        }

        @Override
        void draw(GuiGraphics g, int mx, int my, float pt) {
            Draw.write(g, T.f("strand_to_complete"), ox + 10, oy + 8, Draw.GREY, false);
            for (int k = position; k < Math.min(strand.length, position + 6); k++) {
                int i = k - position;
                int size = i == 0 ? 28 : 16;
                int bx = i == 0 ? ox + 20 : ox + 62 + (i - 1) * 22, by = i == 0 ? oy + 22 : oy + 28;
                Draw.base(g, bx, by, size, strand[k], i == 0);
            }
            int flashColour = flash > 0 ? (flashOk == 1 ? Draw.GREEN : Draw.ALERT) : Draw.INSET_LIGHT;
            g.fill(ox + 20, oy + 56, ox + AREA_W - 20, oy + 62, Draw.EDGE);
            double part = 1 - Math.min(1.0, (time + pt) / duration());
            g.fill(ox + 21, oy + 57, ox + 21 + (int) ((AREA_W - 42) * part), oy + 61, part > 0.35 ? Draw.ACCENT : Draw.ALERT);
            g.fill(ox + 20, oy + 64, ox + AREA_W - 20, oy + 65, flashColour);
            for (int b = 0; b < 4; b++) {
                int bx = ox + 30 + b * 56;
                boolean hover = Draw.in(mx, my, bx, oy + 74, 48, 24);
                Draw.button(g, bx, oy + 74, 48, 24, Draw.BASES[b], hover, true, Draw.BASE_COLOURS[b]);
            }
            Draw.write(g, (position) + " / " + strand.length, ox + AREA_W - 60, oy + 8, Draw.TEXT, false);
            Draw.write(g, T.f("mistakes") + errors + " / 3", ox + AREA_W - 84, oy + 104, errors > 1 ? Draw.ALERT : Draw.GREY, false);
            Draw.write(g, "A↔T   C↔G", ox + 10, oy + 104, Draw.YELLOW, false);
        }

        @Override
        void click(double mx, double my) {
            for (int b = 0; b < 4; b++) {
                if (Draw.in(mx, my, ox + 30 + b * 56, oy + 74, 48, 24)) answer(b);
            }
        }

        @Override
        boolean keyPressed(int key) {
            for (int b = 0; b < 4; b++) {
                if (key == KEYS[b]) {
                    answer(b);
                    return true;
                }
            }
            return false;
        }

        @Override
        String instruction() {
            return T.f("give_the_base_that_pairs");
        }
    }

    // ---------- 4. Assembly: put 5 fragments back in order using their overlaps ----------

    private static final class FragmentsGame extends MiniGame {
        final int[][] fragments = new int[5][4];
        final int[] order = {0, 1, 2, 3, 4};
        int selection = -1, triesLeft = 3, joints = -1;

        FragmentsGame(Random rng) {
            int[] seq = new int[12];
            while (true) {
                for (int k = 0; k < 12; k++) seq[k] = rng.nextInt(4);
                java.util.Set<Integer> pairs = new java.util.HashSet<>();
                for (int i = 0; i < 6; i++) pairs.add(seq[2 * i] * 4 + seq[2 * i + 1]);
                if (pairs.size() == 6) break;
            }
            for (int i = 0; i < 5; i++) System.arraycopy(seq, 2 * i, fragments[i], 0, 4);
            do {
                for (int i = 4; i > 0; i--) {
                    int j = rng.nextInt(i + 1);
                    int t = order[i];
                    order[i] = order[j];
                    order[j] = t;
                }
            } while (correctJoints() == 4);
        }

        int correctJoints() {
            int n = 0;
            for (int i = 0; i < 4; i++) {
                int[] a = fragments[order[i]], b = fragments[order[i + 1]];
                if (a[2] == b[0] && a[3] == b[1]) n++;
            }
            return n;
        }

        @Override
        void draw(GuiGraphics g, int mx, int my, float pt) {
            for (int l = 0; l < 5; l++) {
                int y = oy + 8 + l * 20;
                boolean hover = Draw.in(mx, my, ox + 16, y - 1, 100, 18);
                if (l == selection || hover) g.fill(ox + 14, y - 2, ox + 118, y + 17, l == selection ? Draw.YELLOW : Draw.EDGE_LIGHT);
                g.fill(ox + 15, y - 1, ox + 117, y + 16, Draw.INSET);
                Draw.write(g, String.valueOf(l + 1), ox + 20, y + 4, Draw.DARK, false);
                for (int k = 0; k < 4; k++) Draw.base(g, ox + 34 + k * 20, y, 15, fragments[order[l]][k], false);
            }
            Draw.write(g, T.f("the_end_of_a_fragment"), ox + 132, oy + 10, Draw.GREY, false);
            Draw.write(g, T.f("the_start_of_the_next"), ox + 132, oy + 20, Draw.GREY, false);
            for (int k = 0; k < 4; k++) Draw.base(g, ox + 132 + k * 13, oy + 36, 11, k < 2 ? -1 : k - 2, false);
            for (int k = 0; k < 4; k++) Draw.base(g, ox + 158 + k * 13, oy + 50, 11, k < 2 ? k : -1, false);
            if (joints >= 0) Draw.write(g, T.f("correct_joints") + joints + " / 4", ox + 132, oy + 68, Draw.YELLOW, false);
            Draw.write(g, T.f("tries") + triesLeft, ox + 132, oy + 80, triesLeft == 1 ? Draw.ALERT : Draw.TEXT, false);
            Draw.button(g, ox + 132, oy + 92, 110, 20, T.f("assemble"), Draw.in(mx, my, ox + 132, oy + 92, 110, 20), true, Draw.ACCENT);
        }

        @Override
        void click(double mx, double my) {
            for (int l = 0; l < 5; l++) {
                if (!Draw.in(mx, my, ox + 16, oy + 7 + l * 20, 100, 18)) continue;
                if (selection < 0) selection = l;
                else {
                    int t = order[l];
                    order[l] = order[selection];
                    order[selection] = t;
                    selection = -1;
                }
                Draw.click(1.2f);
                return;
            }
            if (Draw.in(mx, my, ox + 132, oy + 92, 110, 20)) {
                joints = correctJoints();
                if (joints == 4) end(true);
                else {
                    triesLeft--;
                    Draw.note(0.6f);
                    if (triesLeft <= 0) end(false);
                }
            }
        }

        @Override
        String instruction() {
            return T.f("click_two_fragments_to_swap");
        }
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
