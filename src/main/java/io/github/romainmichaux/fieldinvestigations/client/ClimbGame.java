package io.github.romainmichaux.fieldinvestigations.client;

import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

// Grimpe (enquête des cimes) : attraper chaque prise du bon côté, au bon moment, sans toucher aux branches mortes
final class ClimbGame extends MiniGame {
    // Le tronc est une bande de 56 px de haut, bien droite, qui se répète sans couture et défile ; la forêt du fond reste fixe.
    static final ResourceLocation TRUNK = Draw.texture("trunk"), FOREST = Draw.texture("forest");
    static final int BAND = 56, TRUNK_X = 56, TRUNK_W = 48;
    static final ResourceLocation[][] BRANCHES = {{Draw.texture("branch_left"), Draw.texture("branch_right")},
            {Draw.texture("dead_branch_left"), Draw.texture("dead_branch_right")}};
    static final int N = 20, LINE = 84, MARGIN = 7;
    final int[] side = new int[N], heightPx = new int[N];
    final boolean[] dead = new boolean[N];
    // 0 = à venir, 1 = passée (attrapée, ou branche morte évitée), 2 = manquée.
    final int[] state = new int[N];
    double progress;
    int nextOne, faults, flash, takes, goodOnes;

    ClimbGame(Random h) {
        int y = 0;
        for (int i = 0; i < N; i++) {
            side[i] = h.nextInt(2);
            // Des écarts irréguliers : parfois deux prises coup sur coup, parfois un long vide.
            y += 20 + h.nextInt(26);
            heightPx[i] = y;
            dead[i] = i > 1 && h.nextInt(5) == 0;
            if (!dead[i]) goodOnes++;
        }
    }

    double y(int i) {
        return progress - 20 - heightPx[i];
    }

    double speed() {
        return 1.7 + progress / 330;
    }

    void missed() {
        state[nextOne++] = 2;
        flash = 8;
        Draw.beep(Sounds.CRACK, 0.9f, 0.8f);
        if (++faults >= 3) end(false);
    }

    void rest1() {
        if (!finished && nextOne >= N) {
            perfect = faults == 0;
            end(true);
        }
    }

    @Override
    void tick() {
        if (flash > 0) flash--;
        progress += speed();
        if (nextOne < N && y(nextOne) > LINE + MARGIN) {
            if (dead[nextOne]) state[nextOne++] = 1; // une branche morte qu'on a laissée passer : c'est ce qu'il fallait
            else missed();
            rest1();
        }
    }

    @Override
    void click(double mx, double my) {
        int bx = ox + 4, by = oy + 3;
        if (nextOne >= N || !Draw.in(mx, my, bx, by, L, H)) return;
        int c = mx < bx + 80 ? 0 : 1;
        boolean inRange = Math.abs(y(nextOne) - LINE) <= MARGIN;
        if (inRange && !dead[nextOne] && c == side[nextOne]) {
            state[nextOne++] = 1;
            Draw.beep(Sounds.BRANCH, 0.8f + takes * 0.05f, 0.9f);
            takes++;
        } else {
            missed(); // trop tôt, trop tard, du mauvais côté, ou une branche morte qui casse
        }
        rest1();
    }

    @Override
    void draw(GuiGraphics g, int mx, int my, float pt) {
        int bx = ox + 4, by = oy + 3;
        frame(g, bx, by, flash > 0);
        // Entre deux tics, tout est dessiné un peu plus loin : la montée reste fluide.
        double slides = finished ? 0 : pt * speed(), rises = progress + slides;
        g.enableScissor(bx, by, bx + L, by + H);
        Draw.image(g, FOREST, bx, by, L, H);
        // Les branches sont dessinées avant le tronc, qui recouvre leur base : elles ont l'air d'en sortir.
        for (int i = 0; i < N; i++) {
            double y = y(i) + slides;
            if (state[i] != 0 || y < -18 || y > H + 18) continue;
            int x = bx + (side[i] == 0 ? TRUNK_X - 26 : TRUNK_X + TRUNK_W - 6), py = by + (int) y;
            boolean ready = i == nextOne && Math.abs(y(i) - LINE) <= MARGIN && !dead[i];
            if (ready) g.fill(x - 2, py - 10, x + 34, py + 10, 0x7040E070);
            Draw.image(g, BRANCHES[dead[i] ? 1 : 0][side[i]], x, py - 16, 32, 32);
        }
        int trunk = (int) rises % BAND;
        g.fill(bx + TRUNK_X - 2, by, bx + TRUNK_X + TRUNK_W + 2, by + H, 0x60000000);
        for (int k = -1; k <= 1; k++) Draw.image(g, TRUNK, bx + TRUNK_X, by + trunk + k * BAND, TRUNK_W, BAND);
        g.fill(bx, by + LINE - MARGIN, bx + L, by + LINE + MARGIN, 0x30FFFFFF);
        g.fill(bx, by + LINE - MARGIN, bx + L, by + LINE - MARGIN + 1, 0xA0FFFFFF);
        g.fill(bx, by + LINE + MARGIN - 1, bx + L, by + LINE + MARGIN, 0xA0FFFFFF);
        g.disableScissor();
        int tx = ox + 172;
        text(g, T.f("holds") + takes + " / " + goodOnes, tx, oy + 10, Draw.TEXT);
        text(g, T.f("slips_2") + faults + " / 3", tx, oy + 22, faults > 1 ? Draw.ALERT : Draw.GREY);
        Draw.paragraph(g, T.f("grey_split_branches_are_dead"), tx, oy + 42, 104, Draw.DARK);
    }

    @Override
    String instruction() {
        return T.f("click_left_or_right_on");
    }
}
