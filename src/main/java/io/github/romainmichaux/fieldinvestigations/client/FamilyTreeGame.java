package io.github.romainmichaux.fieldinvestigations.client;

import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

// Family tree: a key inked on parchment branches three times, on three traits. The traits of the sample are not given:
// they are marks on the egg shell beside the key, too faint to read without the magnifier. Held over a mark a moment,
// the magnifier reads it and the trait is noted; some specks are only scratches. At each fork, the branch of the trait
// found is clicked; guessing is allowed, but a wrong branch is a mistake. Each fork has its own short time:
// the traits are best read ahead, at the forks above. Three lineages are traced; the last ends in the seal.
final class FamilyTreeGame extends MiniGame {
    // Three traits, two possible forms each: covering, limb, weapon.
    static final ResourceLocation[][] ICONS = {
            {Draw.texture("buried_egg_1"), Draw.texture("buried_stat_2")},
            {Draw.texture("trait_wing"), Draw.texture("trait_fin")},
            {Draw.texture("buried_stat_1"), Draw.texture("buried_stat")}};
    static final ResourceLocation PARCHMENT = Draw.texture("tree_parchment"), SHELL = Draw.texture("tree_shell"), LENS = Draw.texture("tree_lens");
    static final String[][] NAMES = {{T.f("feathers"), T.f("scales")}, {T.f("wings"), T.f("fins")}, {T.f("claws"), T.f("fangs")}};
    static final int[] YS = {14, 40, 66, 94};
    static final int TREE_X = 10, TREE_W = 140, SLOT_X = 164, SHELL_X = 186, SHELL_Y = 6, SHELL_W = 90, SHELL_H = 108;
    static final int ROUNDS = 3, MAX_FAULTS = 3, READ = 28, DECOYS = 5, END = 70;
    final Random h;
    // order[n]: the trait tested at level n; left[n]: the form placed on the left branch; values[t]: the sample's form of trait t.
    final int[] order = new int[3], left = new int[3], values = new int[3];
    // The marks on the shell (three traits, then scratches): position, reading progress, read.
    final int[][] marks = new int[3 + DECOYS][2];
    final int[] reading = new int[3 + DECOYS];
    final boolean[] read = new boolean[3 + DECOYS];
    int depth, node, round, faults, time, duration, flash, pause, tic, ending = -1, messageTicks, messageColour;
    double mx = -100, my = -100;
    String message = "";

    FamilyTreeGame(Random h) {
        this.h = h;
        newRound();
    }

    void newRound() {
        int[][] permutations = {{0, 1, 2}, {0, 2, 1}, {1, 0, 2}, {1, 2, 0}, {2, 0, 1}, {2, 1, 0}};
        System.arraycopy(permutations[h.nextInt(6)], 0, order, 0, 3);
        for (int l = 0; l < 3; l++) {
            left[l] = h.nextInt(2);
            values[l] = h.nextInt(2);
        }
        // Marks spread over the shell, inside its oval, not too close to each other.
        for (int m = 0; m < marks.length; m++) {
            for (int attempt = 0; attempt < 200; attempt++) {
                double a = h.nextDouble() * Math.PI * 2, r = Math.sqrt(h.nextDouble()) * 0.72;
                marks[m][0] = SHELL_W / 2 + (int) (Math.cos(a) * r * 36);
                marks[m][1] = SHELL_H / 2 + 4 + (int) (Math.sin(a) * r * 42);
                boolean free = true;
                for (int k = 0; k < m; k++) free &= Math.hypot(marks[k][0] - marks[m][0], marks[k][1] - marks[m][1]) > 16;
                if (free) break;
            }
            reading[m] = 0;
            read[m] = false;
        }
        depth = 0;
        node = 0;
        startFork();
    }

    // Each fork has its own time, shorter lineage after lineage.
    void startFork() {
        duration = time = 210 - round * 30;
    }

    void say(String s, int colour) {
        message = s;
        messageColour = colour;
        messageTicks = 36;
    }

    @Override
    void tick() {
        tic++;
        if (flash > 0) flash--;
        if (messageTicks > 0) messageTicks--;
        if (ending >= 0) {
            if (ending == 24) Draw.beep(Sounds.PAPER, 0.9f, 0.6f);
            if (++ending >= END) {
                perfect = faults == 0;
                end(true);
            }
            return;
        }
        if (finished) return;
        if (pause > 0) {
            if (--pause == 0) newRound();
            return;
        }
        if (--time <= 0) {
            say(T.f("too_slow"), Draw.ALERT);
            fault();
            startFork();
            return;
        }
        hurryTicks(time, duration);
        // The magnifier reads the mark it is held over.
        for (int m = 0; m < marks.length; m++) {
            if (read[m]) continue;
            if (Math.hypot(mx - (ox + SHELL_X + marks[m][0]), my - (oy + SHELL_Y + marks[m][1])) < 9) {
                if (++reading[m] % 6 == 0) Draw.beep(Sounds.SCAN, 1.2f + reading[m] * 0.03f, 0.3f);
                if (reading[m] >= READ) {
                    read[m] = true;
                    if (m < 3) {
                        Draw.note(1.1f + m * 0.1f);
                        say(NAMES[m][values[m]], Draw.GREEN);
                    } else {
                        say(T.f("nothing_here"), Draw.GREY);
                    }
                }
            } else {
                reading[m] = Math.max(0, reading[m] - 1);
            }
        }
    }

    void fault() {
        flash = 8;
        Draw.note(0.5f);
        if (++faults >= MAX_FAULTS) end(false);
    }

    double x(int l, int n) {
        return ox + TREE_X + TREE_W * (n + 0.5) / (1 << l);
    }

    // The form shown on the branch on this side (0 = left, 1 = right) at the given level.
    int shape(int l, int side) {
        return side == 0 ? left[l] : 1 - left[l];
    }

    @Override
    void click(double px, double py) {
        if (pause > 0 || depth >= 3 || ending >= 0 || finished) return;
        for (int side = 0; side < 2; side++) {
            int cx = (int) x(depth + 1, node * 2 + side), cy = oy + YS[depth + 1];
            if (!Draw.in(px, py, cx - 12, cy - 12, 24, 24)) continue;
            if (shape(depth, side) == values[order[depth]]) {
                node = node * 2 + side;
                depth++;
                startFork();
                Draw.beep(Sounds.PAPER, 1.2f + depth * 0.1f, 0.4f);
                if (depth >= 3) {
                    Draw.beep(Sounds.CHIME, 1.5f, 0.6f);
                    if (++round >= ROUNDS) {
                        ending = 0;
                    } else {
                        pause = 40;
                    }
                }
            } else {
                say(T.f("wrong_branch"), Draw.ALERT);
                fault();
            }
            return;
        }
    }

    void thumbnail(GuiGraphics g, int cx, int cy, ResourceLocation icon, int ink, boolean known) {
        g.fill(cx - 10, cy - 10, cx + 10, cy + 10, ink);
        g.fill(cx - 9, cy - 9, cx + 9, cy + 9, 0xFFEFE2C4);
        if (known) Draw.image(g, icon, cx - 8, cy - 8, 16, 16);
        else g.drawString(Draw.textFont(), "?", cx - 2, cy - 4, Draw.INK_LIGHT, false);
    }

    @Override
    void draw(GuiGraphics g, int px, int py, float pt) {
        mx = px;
        my = py;
        int ax = ox, ay = oy;
        Draw.image(g, PARCHMENT, ax, ay, AREA_W, AREA_H);
        // The key, inked: the whole tree faintly, the path taken boldly.
        for (int l = 0; l < 3; l++) {
            for (int n = 0; n < (1 << l); n++) {
                for (int side = 0; side < 2; side++) {
                    Draw.segment(g, x(l, n), ay + YS[l], x(l + 1, n * 2 + side), ay + YS[l + 1], 1, 0x60705030);
                }
            }
        }
        for (int f = 0; f < 8; f++) g.fill((int) x(3, f) - 2, ay + YS[3] - 2, (int) x(3, f) + 3, ay + YS[3] + 3, 0x80705030);
        for (int l = 0; l < depth; l++) {
            int n0 = node >> (depth - l), n1 = node >> (depth - l - 1);
            Draw.segment(g, x(l, n0), ay + YS[l], x(l + 1, n1), ay + YS[l + 1], 2, Draw.INK);
        }
        for (int l = 0; l < depth; l++) {
            int n1 = node >> (depth - l - 1);
            thumbnail(g, (int) x(l + 1, n1), ay + YS[l + 1], ICONS[order[l]][values[order[l]]], Draw.INK, true);
        }
        g.fill((int) x(0, 0) - 3, ay + YS[0] - 3, (int) x(0, 0) + 4, ay + YS[0] + 4, Draw.INK);
        if (depth < 3 && pause == 0 && ending < 0 && !finished) {
            for (int side = 0; side < 2; side++) {
                int cx = (int) x(depth + 1, node * 2 + side), cy = ay + YS[depth + 1];
                boolean hover = Draw.in(px, py, cx - 12, cy - 12, 24, 24);
                Draw.segment(g, x(depth, node), ay + YS[depth], cx, cy, 1, Draw.INK_RED);
                thumbnail(g, cx, cy, ICONS[order[depth]][shape(depth, side)], hover ? Draw.INK : Draw.INK_RED, true);
            }
        }
        // The seal once the lineage is traced.
        if (ending >= 20) seal(g, (int) x(3, node), ay + YS[3] - 6);
        // The trait slots: what the shell has told so far; the trait asked at this fork is underlined in red.
        for (int t = 0; t < 3; t++) {
            int cy = ay + 22 + t * 30;
            boolean asked = depth < 3 && order[depth] == t && ending < 0;
            thumbnail(g, ax + SLOT_X, cy, ICONS[t][values[t]], asked ? Draw.INK_RED : Draw.INK_LIGHT, read[t]);
            if (asked) g.fill(ax + SLOT_X - 10, cy + 12, ax + SLOT_X + 10, cy + 13, Draw.INK_RED);
        }
        drawShell(g, ax + SHELL_X, ay + SHELL_Y, px, py);
        // Counters.
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        String s = T.f("lineage") + Math.min(ROUNDS, round + 1) + " / " + ROUNDS + "   " + T.f("mistakes") + faults + " / " + MAX_FAULTS;
        g.drawString(Draw.textFont(), s, ax + 8, ay + 104, faults > 0 ? Draw.INK_RED : Draw.INK, false);
        if (depth < 3 && ending < 0 && pause == 0) {
            g.fill(ax + 6, ay + 3, ax + 52, ay + 11, 0x60705030);
            timeGauge(g, ax + 8, ay + 4, 42, time / (double) duration);
        }
        if (messageTicks > 0) {
            int w = Draw.textFont().width(message);
            g.fill(ax + 80 - w / 2 - 4, ay + 2, ax + 80 + w / 2 + 4, ay + 12, 0xC0101418);
            g.drawString(Draw.textFont(), message, ax + 80 - w / 2, ay + 3, messageColour, false);
        }
        g.pose().popPose();
        if (flash > 0) {
            g.fill(ax, ay, ax + AREA_W, ay + 1, Draw.ALERT);
            g.fill(ax, ay + AREA_H - 1, ax + AREA_W, ay + AREA_H, Draw.ALERT);
        }
    }

    // The shell, enlarged; its marks are mere specks until the magnifier has read them.
    void drawShell(GuiGraphics g, int x, int y, int px, int py) {
        Draw.image(g, SHELL, x, y, SHELL_W, SHELL_H);
        for (int m = 0; m < marks.length; m++) {
            int cx = x + marks[m][0], cy = y + marks[m][1];
            if (read[m] && m < 3) {
                g.fill(cx - 9, cy - 9, cx + 9, cy + 9, 0xA0705030);
                g.fill(cx - 8, cy - 8, cx + 8, cy + 8, 0xFFF4ECD8);
                Draw.image(g, ICONS[m][values[m]], cx - 8, cy - 8, 16, 16);
            } else if (read[m]) {
                g.fill(cx - 3, cy, cx + 3, cy + 1, 0x80706050);
            } else {
                g.fill(cx - 1, cy - 1, cx + 1, cy + 1, 0x70504030);
                g.fill(cx + 1, cy, cx + 2, cy + 1, 0x50504030);
            }
        }
        // The magnifier over the shell: a clearer circle, with the reading progress around it.
        if (!finished && ending < 0 && pause == 0 && Draw.in(px, py, x - 10, y - 10, SHELL_W + 20, SHELL_H + 20)) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 150);
            for (int dy = -10; dy <= 10; dy++) {
                int w = (int) Math.sqrt(100 - dy * dy);
                g.fill(px - w, py + dy, px + w, py + dy + 1, 0x30FFFFFF);
            }
            for (int m = 0; m < marks.length; m++) {
                if (read[m] || reading[m] == 0) continue;
                int cx = x + marks[m][0], cy = y + marks[m][1];
                int r = 2 + reading[m] * 6 / READ;
                Draw.circle(g, cx, cy, r, 1, Draw.INK_RED);
            }
            Draw.image(g, LENS, px - 15, py - 15, 31, 32);
            g.pose().popPose();
        }
    }

    void seal(GuiGraphics g, int cx, int cy) {
        String s = T.f("lineage_established");
        int w = Draw.textFont().width(s) + 12;
        double k = Math.min(1, (ending - 20) / 6.0);
        cx = Math.max(ox + w / 2 + 4, Math.min(ox + TREE_X + TREE_W - w / 2, cx));
        g.pose().pushPose();
        g.pose().translate(cx, cy, 300);
        float sc = (float) (1.6 - 0.6 * k);
        g.pose().scale(sc, sc, 1);
        g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-6));
        g.fill(-w / 2 - 2, -9, w / 2 + 2, 9, 0xFFA8322A);
        g.fill(-w / 2, -7, w / 2, 7, 0xF0F4ECD8);
        g.drawString(Draw.textFont(), s, -Draw.textFont().width(s) / 2, -4, 0xFFA8322A, false);
        g.pose().popPose();
    }

    @Override
    String instruction() {
        return T.f("hold_the_magnifier_over_the");
    }

    @Override
    Control[] controls() {
        return new Control[] {new Control(T.f("move_magnifier_click_branch"), Draw.MOUSE)};
    }
}
