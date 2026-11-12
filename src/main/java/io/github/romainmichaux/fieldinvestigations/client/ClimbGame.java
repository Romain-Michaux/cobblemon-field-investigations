package io.github.romainmichaux.fieldinvestigations.client;

import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

// Climb (canopy investigation): grab each hold on the right side at the right moment, without touching dead branches.
final class ClimbGame extends MiniGame {
    // The trunk is a straight 56 px band that tiles seamlessly and scrolls; the forest behind stays still.
    static final ResourceLocation TRUNK = Draw.texture("trunk"), FOREST = Draw.texture("forest");
    static final int BAND = 56, TRUNK_X = 56, TRUNK_W = 48;
    static final ResourceLocation[][] BRANCHES = {{Draw.texture("branch_left"), Draw.texture("branch_right")},
            {Draw.texture("dead_branch_left"), Draw.texture("dead_branch_right")}};
    static final int N = 20, LINE = 84, MARGIN = 7;
    final int[] side = new int[N], heightPx = new int[N];
    final boolean[] dead = new boolean[N];
    // 0 = upcoming, 1 = passed (grabbed, or dead branch avoided), 2 = missed.
    final int[] state = new int[N];
    double progress;
    int next, faults, flash, holds, liveHolds;

    ClimbGame(Random h) {
        int y = 0;
        for (int i = 0; i < N; i++) {
            side[i] = h.nextInt(2);
            // Irregular spacing: sometimes two holds back to back, sometimes a long gap.
            y += 20 + h.nextInt(26);
            heightPx[i] = y;
            dead[i] = i > 1 && h.nextInt(5) == 0;
            if (!dead[i]) liveHolds++;
        }
    }

    double y(int i) {
        return progress - 20 - heightPx[i];
    }

    double speed() {
        return 1.7 + progress / 330;
    }

    void missed() {
        state[next++] = 2;
        flash = 8;
        Draw.beep(Sounds.CRACK, 0.9f, 0.8f);
        if (++faults >= 3) end(false);
    }

    void finishIfDone() {
        if (!finished && next >= N) {
            perfect = faults == 0;
            end(true);
        }
    }

    @Override
    void tick() {
        if (flash > 0) flash--;
        progress += speed();
        if (next < N && y(next) > LINE + MARGIN) {
            if (dead[next]) state[next++] = 1; // a dead branch let past: that was the right move
            else missed();
            finishIfDone();
        }
    }

    @Override
    void click(double mx, double my) {
        int bx = ox + 4, by = oy + 3;
        if (next >= N || !Draw.in(mx, my, bx, by, L, H)) return;
        int c = mx < bx + 80 ? 0 : 1;
        boolean inRange = Math.abs(y(next) - LINE) <= MARGIN;
        if (inRange && !dead[next] && c == side[next]) {
            state[next++] = 1;
            Draw.beep(Sounds.BRANCH, 0.8f + holds * 0.05f, 0.9f);
            holds++;
        } else {
            missed(); // too early, too late, wrong side, or a dead branch that snaps
        }
        finishIfDone();
    }

    @Override
    void draw(GuiGraphics g, int mx, int my, float pt) {
        int bx = ox + 4, by = oy + 3;
        frame(g, bx, by, flash > 0);
        // Between two ticks everything is drawn slightly ahead, so the climb stays smooth.
        double offset = finished ? 0 : pt * speed(), climbed = progress + offset;
        g.enableScissor(bx, by, bx + L, by + H);
        Draw.image(g, FOREST, bx, by, L, H);
        // Branches are drawn before the trunk, which covers their base so they seem to grow out of it.
        for (int i = 0; i < N; i++) {
            double y = y(i) + offset;
            if (state[i] != 0 || y < -18 || y > H + 18) continue;
            int x = bx + (side[i] == 0 ? TRUNK_X - 26 : TRUNK_X + TRUNK_W - 6), py = by + (int) y;
            boolean ready = i == next && Math.abs(y(i) - LINE) <= MARGIN && !dead[i];
            if (ready) g.fill(x - 2, py - 10, x + 34, py + 10, 0x7040E070);
            Draw.image(g, BRANCHES[dead[i] ? 1 : 0][side[i]], x, py - 16, 32, 32);
        }
        int scroll = (int) climbed % BAND;
        g.fill(bx + TRUNK_X - 2, by, bx + TRUNK_X + TRUNK_W + 2, by + H, 0x60000000);
        for (int k = -1; k <= 1; k++) Draw.image(g, TRUNK, bx + TRUNK_X, by + scroll + k * BAND, TRUNK_W, BAND);
        g.fill(bx, by + LINE - MARGIN, bx + L, by + LINE + MARGIN, 0x30FFFFFF);
        g.fill(bx, by + LINE - MARGIN, bx + L, by + LINE - MARGIN + 1, 0xA0FFFFFF);
        g.fill(bx, by + LINE + MARGIN - 1, bx + L, by + LINE + MARGIN, 0xA0FFFFFF);
        g.disableScissor();
        int tx = ox + 172;
        text(g, T.f("holds") + holds + " / " + liveHolds, tx, oy + 10, Draw.TEXT);
        text(g, T.f("slips_2") + faults + " / 3", tx, oy + 22, faults > 1 ? Draw.ALERT : Draw.GREY);
        Draw.paragraph(g, T.f("grey_split_branches_are_dead"), tx, oy + 42, 104, Draw.DARK);
    }

    @Override
    String instruction() {
        return T.f("click_left_or_right_on");
    }
}
