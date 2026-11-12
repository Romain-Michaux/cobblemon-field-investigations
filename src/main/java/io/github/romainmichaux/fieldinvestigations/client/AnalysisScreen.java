package io.github.romainmichaux.fieldinvestigations.client;

import java.util.Random;

import com.mojang.math.Axis;

import io.github.romainmichaux.fieldinvestigations.Genetics;
import io.github.romainmichaux.fieldinvestigations.Packet;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

// Séquenceur ADN : chaque type d'échantillon s'analyse par un mini-jeu différent.
// Quitter l'écran avant la fin gâche une dose, comme un échec.
public class AnalysisScreen extends Screen {
    private static final int ZL = 280, ZH = 118;
    private final String kind, gameId;
    private final boolean sampling, mesh, buried, filtering;
    private final int doses, variant;
    private final MiniGame game;
    // Partie d'essai (commande /hunt game) : rien n'est envoyé au serveur à la fin.
    boolean attempt;
    // Première fois que le joueur voit ce mini-jeu : les règles s'affichent, et rien ne démarre avant qu'il clique « Commencer ».
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
        // Le filtrage du cri se joue pendant la traque, avant un relevé : ni fiole ni étoile en jeu.
        this.filtering = gameId.equals("filter");
        Random h = new Random(seed);
        this.game = switch (gameId) {
            case "crystals" -> new CrystalsGame(h);
            case "pipette" -> new PipetteGame(h);
            case "measure" -> new MeasureGame(h);
            case "sorting" -> new SortingGame(h);
            case "trace" -> new TracingGame(h);
            case "brush" -> new BrushGame(h, kind, variant);
            case "sieving" -> new SieveGame(h, kind, variant);
            case "cast" -> new CastGame(h);
            case "tweezers" -> new TweezersGame(h);
            case "photo" -> new PhotoGame(h);
            case "swab" -> new SwabGame(h);
            case "dive" -> new DiveGame(h);
            case "climb" -> new ClimbGame(h);
            case "abseil" -> new AbseilGame(h);
            case "lamp" -> new UvLampGame(h);
            case "rockfall" -> new RockfallGame(h);
            case "filter" -> new FilterGame(h);
            case "microscope" -> new MicroscopeGame(h);
            case "chromatography" -> new ChromatographyGame(h);
            case "family_tree" -> new FamilyTreeGame(h);
            case "titration" -> new TitrationGame(h);
            case "bite" -> new BiteGame(h);
            case "thaw" -> new ThawGame(h);
            case "pairing" -> new PairingGame(h);
            case "centrifugation" -> new CentrifugeGame(h);
            case "fragments" -> new FragmentsGame(h);
            case "spectrum" -> new SpectrumGame(h);
            case "gel" -> new GelGame(h);
            case "colorimetry" -> new ColorimetryGame(h);
            case "pcr" -> new PcrGame(h);
            case "count" -> new CountGame(h);
            default -> new SequencingGame(h);
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
        // Rythme réglé par le serveur : en facile, le jeu saute un tic sur trois ; en difficile, il en joue un de plus sur quatre.
        int pace = FieldInvestigationsClient.state.getInt("difficulty");
        if (!game.finished && !(pace < 0 && ticks % 3 == 0)) {
            game.tick();
            if (pace > 0 && ticks % 4 == 0 && !game.finished) game.tick();
        }
        if (game.finished && endSince < 0) {
            endSince = ticks;
            Draw.note(game.succes ? 1.6f : 0.6f);
            if (game.succes) Draw.beep(Sounds.SUCCESS, game.perfect ? 1.4f : 1f, 0.6f);
            else Draw.beep(Sounds.FAILURE, 0.5f, 0.9f);
        }
        if (endSince >= 0 && ticks - endSince > 32) onClose();
    }

    @Override
    public void removed() {
        // Fermer l'écran pendant l'explication ne coûte rien : la partie n'a pas commencé.
        if (!sent && !attempt && !intro) {
            sent = true;
            Packet p = Packet.from(filtering ? "filter" : sampling ? "sampling" : "analyse");
            p.data().putBoolean("succeeded", game.finished && game.succes);
            p.data().putBoolean("perfect", game.finished && game.succes && game.perfect);
            FieldInvestigationsClient.send(p);
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g, mx, my, pt);
        // Un prélèvement se fait sur le terrain, dans l'écran du kit ; une analyse, dans celui du séquenceur.
        Draw.Backdrop f = sampling ? Draw.KIT : Draw.SEQUENCER;
        int x = f.x(width), y = f.y(height), l = f.il();
        Draw.background(g, f, width, height);
        int zx = x + (l - ZL) / 2, zy;
        if (sampling) {
            Draw.banner(g, x, y, l, T.c(Genetics.gameName(gameId)).toUpperCase(), filtering ? T.f("cry_bearing") : T.c(Genetics.traceName(kind, buried, variant)));
            zy = y + 18;
        } else {
            String gameName = T.c(Genetics.gameName(gameId)).toUpperCase();
            String title = T.f("dna_sequencer_2") + gameName;
            if (Draw.textFont().width(title) > 220) title = gameName;
            Draw.centre(g, title, x + l / 2, y + 176, 0xFF0E3B3A);
            Draw.write(g, T.c(Genetics.traceName(kind, buried, variant)) + T.f("doses") + doses + (mesh ? T.f("first_mistake_is_free") : ""),
                    zx, y + 3, Draw.GREY, false);
            zy = y + 15;
        }
        Draw.inset(g, zx, zy, ZL, ZH);
        game.ox = zx;
        game.oy = zy;
        game.draw(g, mx, my, pt);
        if (!intro) Draw.paragraph(g, game.instruction(), zx, zy + ZH + 3, ZL, Draw.GREY);
        if (intro) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 350);
            g.fill(zx, zy, zx + ZL, zy + ZH, 0xEA0C141C);
            Draw.centre(g, T.f("first_time") + T.c(Genetics.gameName(gameId)).toUpperCase(), zx + ZL / 2, zy + 8, Draw.YELLOW);
            int ly = Draw.paragraph(g, game.instruction(), zx + 16, zy + 24, ZL - 32, Draw.TEXT);
            Draw.paragraph(g, filtering ? T.f("a_failure_costs_no_star")
                    : sampling ? T.f("a_failure_damages_the_sample")
                    : T.f("a_failure_loses_one_dose"), zx + 16, ly + 4, ZL - 32, Draw.GREY);
            boolean hover = Draw.in(mx, my, zx + ZL / 2 - 60, zy + ZH - 26, 120, 20);
            Draw.button(g, zx + ZL / 2 - 60, zy + ZH - 26, 120, 20, T.f("start"), hover, true, Draw.GREEN);
            g.pose().popPose();
        }
        if (endSince >= 0) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 300);
            g.fill(zx + 40, zy + 40, zx + ZL - 40, zy + 78, Draw.EDGE);
            g.fill(zx + 42, zy + 42, zx + ZL - 42, zy + 76, game.succes ? 0xFF245C3A : 0xFF6B2420);
            if (filtering) {
                Draw.centre(g, game.succes ? T.f("cry_isolated") : T.f("cry_lost_in_noise"), zx + ZL / 2, zy + 49, Draw.TEXT);
                Draw.centre(g, game.succes ? T.f("precise_bearing") : T.f("less_precise_bearing"), zx + ZL / 2, zy + 62, Draw.GREY);
            } else if (sampling) {
                Draw.centre(g, game.succes ? (game.perfect ? T.f("perfect_sample") : T.f("sample_taken")) : T.f("sample_damaged"), zx + ZL / 2, zy + 49, Draw.TEXT);
                Draw.centre(g, game.succes ? (game.perfect ? T.f("one_extra_dose") : T.f("full_sample")) : T.f("a_single_dose_half_star"), zx + ZL / 2, zy + 62, Draw.GREY);
            } else {
                Draw.centre(g, game.succes ? T.f("analysis_complete") : T.f("analysis_failed"), zx + ZL / 2, zy + 49, Draw.TEXT);
                Draw.centre(g, game.succes ? T.f("marker") + T.c(Genetics.MARKER_NAMES.get(kind)) + T.f("added_to_the_case_file")
                        : mesh ? T.f("the_sequencer_saved_the_dose") : T.f("one_dose_lost_half_star"), zx + ZL / 2, zy + 62, Draw.GREY);
            }
            g.pose().popPose();
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (intro) {
            Draw.Backdrop f = sampling ? Draw.KIT : Draw.SEQUENCER;
            int zx = f.x(width) + (f.il() - ZL) / 2, zy = f.y(height) + (sampling ? 18 : 15);
            if (Draw.in(mx, my, zx + ZL / 2 - 60, zy + ZH - 26, 120, 20)) {
                intro = false;
                Draw.sound1(1.2f);
            }
            return true;
        }
        game.press = true;
        if (!game.finished) game.click(mx, my);
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        game.press = false;
        if (!game.finished) game.released(mx, my);
        return true;
    }

    @Override
    public boolean keyPressed(int pressedKey, int scan, int mods) {
        if (intro && (pressedKey == 257 || pressedKey == 32)) {
            intro = false;
            Draw.sound1(1.2f);
            return true;
        }
        if (intro) return super.keyPressed(pressedKey, scan, mods);
        if (!game.finished && game.pressedKey(pressedKey)) return true;
        return super.keyPressed(pressedKey, scan, mods);
    }

    // ---------- 1. Séquençage : retrouver 4 bases en 6 lectures ----------

    private static final class SequencingGame extends MiniGame {
        final int[] code = new int[4];
        final int[][] attempts = new int[6][4];
        final int[][] returns = new int[6][2];
        int line;

        SequencingGame(Random h) {
            for (int k = 0; k < 4; k++) code[k] = h.nextInt(4);
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
                        int c = k < returns[l][0] ? Draw.GREEN : k < returns[l][0] + returns[l][1] ? Draw.YELLOW : Draw.INSET_LIGHT;
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
                    Draw.sound1(1f + attempts[line][k] * 0.15f);
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
            returns[line][0] = exact;
            returns[line][1] = present;
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

    // ---------- 2. Appariement des brins : donner la base complémentaire avant la fin du temps ----------

    private static final class PairingGame extends MiniGame {
        static final int[] COMPLEMENT = {1, 0, 3, 2};
        static final int[] KEYS = {65, 84, 67, 71};
        final int[] strand = new int[12];
        int position, errors, time;
        int flash, flashOk;

        PairingGame(Random h) {
            for (int k = 0; k < strand.length; k++) strand[k] = h.nextInt(4);
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
                Draw.sound1(1.2f + position * 0.05f);
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
            int banner = flash > 0 ? (flashOk == 1 ? Draw.GREEN : Draw.ALERT) : Draw.INSET_LIGHT;
            g.fill(ox + 20, oy + 56, ox + ZL - 20, oy + 62, Draw.EDGE);
            double part = 1 - Math.min(1.0, (time + pt) / duration());
            g.fill(ox + 21, oy + 57, ox + 21 + (int) ((ZL - 42) * part), oy + 61, part > 0.35 ? Draw.ACCENT : Draw.ALERT);
            g.fill(ox + 20, oy + 64, ox + ZL - 20, oy + 65, banner);
            for (int b = 0; b < 4; b++) {
                int bx = ox + 30 + b * 56;
                boolean hover = Draw.in(mx, my, bx, oy + 74, 48, 24);
                Draw.button(g, bx, oy + 74, 48, 24, Draw.BASES[b], hover, true, Draw.BASE_COLOURS[b]);
            }
            Draw.write(g, (position) + " / " + strand.length, ox + ZL - 60, oy + 8, Draw.TEXT, false);
            Draw.write(g, T.f("mistakes") + errors + " / 3", ox + ZL - 84, oy + 104, errors > 1 ? Draw.ALERT : Draw.GREY, false);
            Draw.write(g, "A↔T   C↔G", ox + 10, oy + 104, Draw.YELLOW, false);
        }

        @Override
        void click(double mx, double my) {
            for (int b = 0; b < 4; b++) {
                if (Draw.in(mx, my, ox + 30 + b * 56, oy + 74, 48, 24)) answer(b);
            }
        }

        @Override
        boolean pressedKey(int pressedKey) {
            for (int b = 0; b < 4; b++) {
                if (pressedKey == KEYS[b]) {
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

    // ---------- 3. Centrifugation : arrêter l'aiguille dans la zone verte, trois fois ----------

    private static final class CentrifugeGame extends MiniGame {
        final Random h;
        double angle, speed = 7, start, widthPx = 56;
        int successes, misses, flash;

        CentrifugeGame(Random h) {
            this.h = h;
            start = h.nextInt(360);
        }

        @Override
        void tick() {
            double before = angle;
            angle = (angle + speed) % 360;
            if (angle < before) Draw.beep(Sounds.TICK, (float) (0.7 + speed * 0.07), 0.5f);
            if (flash > 0) flash--;
        }

        void stopNow(float pt) {
            double a = (angle + speed * pt) % 360;
            double gap = ((a - start) % 360 + 360) % 360;
            if (gap <= widthPx) {
                successes++;
                Draw.note(1f + successes * 0.25f);
                if (successes >= 3) {
                    end(true);
                    return;
                }
                speed += 2.5;
                widthPx -= 13;
                start = h.nextInt(360);
            } else {
                misses++;
                flash = 6;
                Draw.note(0.5f);
                if (misses >= 3) end(false);
            }
        }

        @Override
        void draw(GuiGraphics g, int mx, int my, float pt) {
            int cx = ox + 70, cy = oy + ZH / 2, radius = 51;
            double degrees = finished ? angle : angle + speed * pt;
            g.pose().pushPose();
            g.pose().translate(cx, cy, 0);
            // Le rotor tourne à contresens de l'aiguille.
            g.pose().mulPose(Axis.ZP.rotationDegrees((float) -degrees));
            Draw.image(g, Draw.ROTOR, -48, -48, 96, 96);
            g.pose().popPose();
            Draw.circle(g, cx - 1, cy - 1, radius, 2, flash > 0 ? Draw.ALERT : Draw.EDGE_LIGHT);
            for (double a = start; a <= start + widthPx; a += 2) {
                double r = Math.toRadians(a);
                Draw.point(g, cx + Math.sin(r) * radius - 2, cy - Math.cos(r) * radius - 2, 4, Draw.GREEN);
            }
            double r = Math.toRadians(degrees);
            Draw.segment(g, cx - 1 + Math.sin(r) * 14, cy - 1 - Math.cos(r) * 14, cx - 1 + Math.sin(r) * (radius + 2), cy - 1 - Math.cos(r) * (radius + 2), 2, Draw.YELLOW);
            for (int i = 0; i < 3; i++) {
                g.fill(ox + 150 + i * 30, oy + 20, ox + 172 + i * 30, oy + 42, Draw.EDGE);
                g.fill(ox + 152 + i * 30, oy + 22, ox + 170 + i * 30, oy + 40, i < successes ? Draw.GREEN : Draw.INSET_LIGHT);
            }
            Draw.write(g, T.f("phases_separated") + successes + " / 3", ox + 150, oy + 50, Draw.TEXT, false);
            Draw.write(g, T.f("misses") + misses + " / 3", ox + 150, oy + 62, misses > 1 ? Draw.ALERT : Draw.GREY, false);
            Draw.button(g, ox + 150, oy + 82, 110, 24, T.f("stop_space"), Draw.in(mx, my, ox + 150, oy + 82, 110, 24), true, Draw.ACCENT);
        }

        @Override
        void click(double mx, double my) {
            stopNow(0f);
        }

        @Override
        boolean pressedKey(int pressedKey) {
            if (pressedKey == 32) {
                stopNow(0f);
                return true;
            }
            return false;
        }

        @Override
        String instruction() {
            return T.f("click_or_space_when_the");
        }
    }

    // ---------- 4. Assemblage : remettre 5 fragments dans l'ordre grâce à leurs chevauchements ----------

    private static final class FragmentsGame extends MiniGame {
        final int[][] fragments = new int[5][4];
        final int[] order = {0, 1, 2, 3, 4};
        int selection = -1, attempts = 3, joints = -1;

        FragmentsGame(Random h) {
            int[] seq = new int[12];
            while (true) {
                for (int k = 0; k < 12; k++) seq[k] = h.nextInt(4);
                java.util.Set<Integer> pairs = new java.util.HashSet<>();
                for (int i = 0; i < 6; i++) pairs.add(seq[2 * i] * 4 + seq[2 * i + 1]);
                if (pairs.size() == 6) break;
            }
            for (int i = 0; i < 5; i++) System.arraycopy(seq, 2 * i, fragments[i], 0, 4);
            do {
                for (int i = 4; i > 0; i--) {
                    int j = h.nextInt(i + 1);
                    int t = order[i];
                    order[i] = order[j];
                    order[j] = t;
                }
            } while (goodOnes() == 4);
        }

        int goodOnes() {
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
            Draw.write(g, T.f("tries") + attempts, ox + 132, oy + 80, attempts == 1 ? Draw.ALERT : Draw.TEXT, false);
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
                Draw.sound1(1.2f);
                return;
            }
            if (Draw.in(mx, my, ox + 132, oy + 92, 110, 20)) {
                joints = goodOnes();
                if (joints == 4) end(true);
                else {
                    attempts--;
                    Draw.note(0.6f);
                    if (attempts <= 0) end(false);
                }
            }
        }

        @Override
        String instruction() {
            return T.f("click_two_fragments_to_swap");
        }
    }

    // ---------- 5. Accord du spectre : régler trois paramètres pour superposer sa courbe à celle de l'échantillon ----------

    private static final class SpectrumGame extends MiniGame {
        static final String[] NAMES = {T.f("frequency"), T.f("amplitude"), T.f("phase")};
        final int[] target = new int[3], setting = new int[3];
        int time = 900, tuning;

        SpectrumGame(Random h) {
            do {
                for (int k = 0; k < 3; k++) {
                    target[k] = h.nextInt(7);
                    setting[k] = h.nextInt(7);
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
            double sum1 = 0;
            for (int i = 0; i < 64; i++) sum1 += Math.abs(curve(target, i / 64.0) - curve(setting, i / 64.0));
            return Math.max(0, 1 - sum1 / 64 / 20);
        }

        @Override
        void tick() {
            if (--time <= 0) end(false);
            press1(time, 900);
            if (gap() == 0) {
                if (tuning == 0) Draw.beep(Sounds.CHIME, 1.5f, 0.6f);
                if (++tuning > 6) end(true);
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
            g.fill(px, oy + ZH - 12, px + pl, oy + ZH - 6, Draw.EDGE);
            g.fill(px + 1, oy + ZH - 11, px + 1 + (int) ((pl - 2) * time / 900.0), oy + ZH - 7, time > 200 ? Draw.ACCENT : Draw.ALERT);
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
                    tuning = 0;
                    Draw.sound1(0.9f + setting[k] * 0.08f);
                } else if (Draw.in(mx, my, ox + 252, y + 10, 22, 18) && setting[k] < 6) {
                    setting[k]++;
                    tuning = 0;
                    Draw.sound1(0.9f + setting[k] * 0.08f);
                }
            }
        }

        @Override
        String instruction() {
            return T.f("tune_frequency_amplitude_and_phase");
        }
    }
}
