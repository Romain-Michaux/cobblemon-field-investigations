package io.github.romainmichaux.fieldinvestigations.client;

import java.util.Random;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

// Bite mark: slide the cast under the fossil jaw, then press it up: every fang must sink into its hollow at once.
final class BiteGame extends MiniGame {
    // The jaw has 7 fangs; the cast has 13 putty bumps and can take 7 positions. Exactly one matches the bite.
    static final int N = 7, M = 13, MAX = 6, U = 5, COL = 16, REST = 18, SINK = 3, OFF = 9, TOOTH_W = 18, TOOTH_H = 39;
    static final ResourceLocation TOOTH = Draw.texture("bite_tooth"), BONE = Draw.texture("bite_bone"), PUTTY = Draw.texture("bite_putty");
    final Random h;
    final int[] bite = new int[N], slider = new int[M];
    int position, round, faults, time, duration, flash, pause, rise = -1, tic;
    // Where the cast is drawn (in columns), easing towards position; the mouse grab (start x, position then, moved).
    double shown;
    boolean fitted, dragging, moved;
    int grabX, grabPosition;

    BiteGame(Random h) {
        this.h = h;
        newRound();
        shown = position;
    }

    void newRound() {
        int good;
        do {
            for (int i = 0; i < N; i++) bite[i] = 1 + h.nextInt(5);
            for (int j = 0; j < M; j++) slider[j] = 1 + h.nextInt(5);
            good = h.nextInt(M - N + 1);
            for (int i = 0; i < N; i++) slider[good + i] = MAX - bite[i];
        } while (solutions() != 1);
        do {
            position = h.nextInt(M - N + 1);
        } while (position == good);
        duration = time = 420 - round * 80;
        rise = -1;
        fitted = false;
        dragging = false;
    }

    // Gap between fang i and the putty bump under it, at rest. All gaps equal REST means a perfect fit.
    int gap(int i, int pos) {
        return REST + (MAX - bite[i] - slider[pos + i]) * U;
    }

    int travel(int pos) {
        int min = Integer.MAX_VALUE;
        for (int i = 0; i < N; i++) min = Math.min(min, gap(i, pos));
        return min;
    }

    boolean fits(int pos) {
        for (int i = 0; i < N; i++) {
            if (gap(i, pos) != REST) return false;
        }
        return true;
    }

    int solutions() {
        int n = 0;
        for (int pos = 0; pos <= M - N; pos++) n += fits(pos) ? 1 : 0;
        return n;
    }

    @Override
    void tick() {
        tic++;
        if (flash > 0) flash--;
        if (rise >= 0) {
            // The cast rises until it hits; then every fang must be in contact.
            rise++;
            if (rise == 8) {
                if (fits(position)) {
                    fitted = true;
                    Draw.beep(Sounds.STONE, 0.6f, 1f);
                    Draw.note(1f + round * 0.3f);
                    if (++round >= 2) {
                        perfect = faults == 0;
                        end(true);
                    } else {
                        pause = 34;
                    }
                } else {
                    flash = 10;
                    Draw.beep(Sounds.STONE, 0.5f, 1f);
                    Draw.note(0.5f);
                    if (++faults >= 2) end(false);
                }
            } else if (rise >= 24 && !fitted) {
                rise = -1; // it drops back: the player can try another position
            } else if (fitted && pause > 0 && --pause == 0 && !finished) {
                newRound(); // the cast stays fitted for a moment, then the next bite comes in
                shown = position;
            }
            return;
        }
        if (pause > 0) {
            if (--pause == 0 && !finished) {
                newRound();
                shown = position;
            }
            return;
        }
        if (--time <= 0) {
            flash = 8;
            Draw.note(0.5f);
            if (++faults >= 2) end(false);
            else pause = 24;
        } else {
            hurryTicks(time, duration);
        }
    }

    void slide(int direction) {
        moveTo(position + direction);
    }

    void moveTo(int next) {
        next = Math.max(0, Math.min(M - N, next));
        if (next == position) return;
        position = next;
        Draw.beep(Sounds.STONE, 0.9f + position * 0.05f, 0.6f);
    }

    void press() {
        rise = 0;
        dragging = false;
    }

    boolean free() {
        return pause == 0 && rise < 0 && !finished;
    }

    // Grab the cast anywhere in the area: dragging slides it notch by notch, a click without moving presses it.
    @Override
    void click(double mx, double my) {
        if (!free() || !Draw.in(mx, my, ox, oy, AREA_W, AREA_H)) return;
        dragging = true;
        moved = false;
        grabX = (int) mx;
        grabPosition = position;
    }

    @Override
    void released(double mx, double my) {
        if (!dragging) return;
        dragging = false;
        if (!moved && free()) press();
    }

    @Override
    boolean keyPressed(int key) {
        if (!free()) return false;
        if (key == InputConstants.KEY_LEFT || key == InputConstants.KEY_A || key == InputConstants.KEY_Q) slide(1);
        else if (key == InputConstants.KEY_RIGHT || key == InputConstants.KEY_D) slide(-1);
        else if (key == InputConstants.KEY_SPACE || key == InputConstants.KEY_RETURN || key == InputConstants.KEY_UP
                || key == InputConstants.KEY_W || key == InputConstants.KEY_Z) press();
        else return false;
        return true;
    }

    // Fills a rectangle with a 64 x 64 tile drawn at half size, starting at texel (u0, v0) of the tile.
    static void tile(GuiGraphics g, ResourceLocation texture, int x, int y, int l, int hh, int u0, int v0) {
        if (l <= 0 || hh <= 0) return;
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.5f, 0.5f, 1f);
        for (int ty = 0; ty < hh * 2; ) {
            int v = Math.floorMod(v0 + ty, 64), ph = Math.min(64 - v, hh * 2 - ty);
            for (int tx = 0; tx < l * 2; ) {
                int u = Math.floorMod(u0 + tx, 64), pl = Math.min(64 - u, l * 2 - tx);
                Draw.piece(g, texture, tx, ty, u, v, pl, ph, 64, 64);
                tx += pl;
            }
            ty += ph;
        }
        g.pose().popPose();
    }

    @Override
    void draw(GuiGraphics g, int mx, int my, float pt) {
        // Mouse grab: one notch per column dragged.
        if (dragging && free()) {
            int notches = (int) Math.round((mx - grabX) / (double) COL);
            if (notches != 0) moved = true;
            moveTo(grabPosition - notches);
        }
        shown += (position - shown) * 0.35;
        if (Math.abs(position - shown) < 0.02) shown = position;

        int x0 = ox + (AREA_W - N * COL) / 2, top = oy + 34;
        boolean hitStop = rise >= 8 && !fitted;
        int travel = travel(position);
        int raised = rise < 0 ? 0 : Math.min(travel + (fits(position) ? SINK : 0), rise * 4);
        // The jaw shakes for a moment when the cast jams against it.
        int shake = hitStop && rise < 14 ? (rise % 2 == 0 ? 1 : -1) : 0;
        boolean hint = round == 0 && free();

        g.enableScissor(ox + 1, oy + 1, ox + AREA_W - 1, oy + AREA_H - 1);
        g.fill(ox, oy, ox + AREA_W, oy + AREA_H, 0xFF1A222C);
        // A soft lamp halo behind the jaw.
        for (int k = 0; k < 4; k++) {
            g.fill(x0 - 30 + k * 8, oy + 2 + k * 3, x0 + N * COL + 30 - k * 8, oy + AREA_H - 20 - k * 6, 0x0AE8D8B0);
        }

        // The cast: putty bumps on a putty bed in a steel tray, all sliding together.
        int base = top + OFF + MAX * U + REST - raised, sx = x0 - (int) Math.round(shown * COL);
        for (int j = 0; j < M; j++) {
            int x = sx + j * COL + 1, l = COL - 2, tip = base - slider[j] * U;
            int slot = j - position;
            boolean under = slot >= 0 && slot < N && Math.abs(position - shown) < 0.2;
            boolean contact = hitStop && under && gap(slot, position) == travel;
            boolean match = hint && under && gap(slot, position) == REST;
            int squash = fitted && under ? 1 : 0;
            g.setColor(0.86f, 0.62f, 0.68f, 1f);
            tile(g, PUTTY, x + 1 - squash, tip, l - 2 + squash * 2, 2, j * 13, 0);
            tile(g, PUTTY, x, tip + 1, l, base - tip, j * 13, 2);
            g.setColor(1f, 1f, 1f, 1f);
            g.fill(x + 1, tip, x + 2, base, 0x40FFFFFF);
            g.fill(x + l - 3, tip + 1, x + l, base, 0x30000000);
            if (match) g.fill(x + 1, tip, x + l - 1, tip + 3, 0x50FFF2B0);
            if (fitted && under) g.fill(x + 4, tip, x + l - 4, tip + SINK, 0x50401020);
            if (contact) g.fill(x + 1, tip, x + l - 1, tip + 2, 0x90FFB0A0);
        }
        int bedL = sx - 3, bedR = sx + M * COL + 3;
        g.setColor(0.86f, 0.62f, 0.68f, 1f);
        tile(g, PUTTY, bedL + 2, base, bedR - bedL - 4, 4, 7, 20);
        g.setColor(1f, 1f, 1f, 1f);
        g.fill(bedL + 2, base + 3, bedR - 2, base + 4, 0x40000000);
        // Steel tray: rim, body with a bright line and a dark underside, a handle on the right.
        g.fill(bedL, base - 2, bedL + 2, base + 4, 0xFFB8BDC2);
        g.fill(bedR - 2, base - 2, bedR, base + 4, 0xFF8A9096);
        g.fill(bedL, base + 4, bedR, base + 5, 0xFFE2E6EA);
        g.fill(bedL, base + 5, bedR, base + 9, 0xFF9AA1A8);
        g.fill(bedL, base + 9, bedR, base + 11, 0xFF5C636A);
        g.fill(bedL + 1, base + 11, bedR - 1, base + 12, 0xFF33393F);
        g.fill(bedR, base + 6, bedR + 26, base + 9, 0xFF8A9096);
        g.fill(bedR, base + 9, bedR + 26, base + 10, 0xFF4A5056);
        for (int k = 0; k < M; k += 3) g.fill(sx + k * COL + COL / 2, base + 7, sx + k * COL + COL / 2 + 1, base + 8, 0xFF6E757C);

        // The jaw: fangs first (their roots hide under the gum), then the bone and the gum line.
        g.pose().pushPose();
        g.pose().translate(shake, 0, 0);
        for (int i = 0; i < N; i++) {
            int tip = top + OFF + bite[i] * U, length = tip - top + 4;
            boolean contact = hitStop && gap(i, position) == travel;
            if (contact && rise < 16) g.setColor(1f, 0.55f, 0.5f, 1f);
            else if (fitted) g.setColor(0.85f, 1f, 0.88f, 1f);
            g.pose().pushPose();
            // The fang sprite keeps its width and stretches to its length: short ones read as stubby, long ones as fangs.
            g.pose().translate(x0 + i * COL + 1, top - 4, 0);
            g.pose().scale((COL - 2f) / TOOTH_W, length / (float) TOOTH_H, 1f);
            Draw.image(g, TOOTH, 0, 0, TOOTH_W, TOOTH_H);
            g.pose().popPose();
            g.setColor(1f, 1f, 1f, 1f);
        }
        int jl = x0 - 10, jr = x0 + N * COL + 10, jt = top - 18;
        g.setColor(0.86f, 0.80f, 0.70f, 1f);
        tile(g, BONE, jl + 3, jt, jr - jl - 6, 1, 0, 0);
        tile(g, BONE, jl + 1, jt + 1, jr - jl - 2, 1, 0, 2);
        tile(g, BONE, jl, jt + 2, jr - jl, top - jt - 6, 0, 4);
        g.setColor(1f, 1f, 1f, 1f);
        g.fill(jl + 3, jt + 1, jr - 3, jt + 2, 0x60FFFFFF);
        g.fill(jl, top - 8, jr, top - 6, 0x30000000);
        // Gum: a dark pink band with a scalloped edge between the fangs.
        g.fill(jl + 1, top - 6, jr - 1, top - 1, 0xFF8E3E4C);
        g.fill(jl + 1, top - 6, jr - 1, top - 5, 0xFFB45A68);
        for (int i = 0; i < N; i++) {
            int x = x0 + i * COL;
            g.fill(x + 3, top - 1, x + COL - 3, top + 1, 0xFF8E3E4C);
            g.fill(x + 4, top + 1, x + COL - 4, top + 2, 0xFF6E2C38);
        }
        g.pose().popPose();

        // Sparks where the cast jams.
        if (hitStop && rise < 16) {
            int r = 2 + (rise - 8) / 2, spark = rise % 4 < 2 ? 0xFFFFF2C0 : 0xFFFFC060;
            for (int i = 0; i < N; i++) {
                if (gap(i, position) != travel) continue;
                int cx = x0 + i * COL + COL / 2 + shake, cy = top + OFF + bite[i] * U;
                g.fill(cx - r, cy, cx + r + 1, cy + 1, spark);
                g.fill(cx, cy - r, cx + 1, cy + r + 1, spark);
                g.fill(cx - r + 1, cy - r + 1, cx - r + 2, cy - r + 2, spark);
                g.fill(cx + r - 1, cy - r + 1, cx + r, cy - r + 2, spark);
            }
        }
        g.disableScissor();

        // Counters in the top corners, the outcome under them, and the time as a thin line along the bottom.
        text(g, T.f("bite") + Math.min(2, round + 1) + " / 2", ox + 6, oy + 5, Draw.TEXT);
        String mistakes = T.f("mistakes") + faults + " / 2";
        text(g, mistakes, ox + AREA_W - 6 - Draw.textFont().width(mistakes), oy + 5, faults > 0 ? Draw.ALERT : Draw.GREY);
        if (fitted) text(g, T.f("it_fits"), ox + AREA_W - 6 - Draw.textFont().width(T.f("it_fits")), oy + 16, Draw.GREEN);
        else if (hitStop) text(g, T.f("it_jams"), ox + AREA_W - 6 - Draw.textFont().width(T.f("it_jams")), oy + 16, Draw.ALERT);
        double part = pause > 0 && !fitted ? 0 : time / (double) duration;
        g.fill(ox + 6, oy + AREA_H - 6, ox + AREA_W - 6, oy + AREA_H - 3, Draw.EDGE);
        g.fill(ox + 7, oy + AREA_H - 5, ox + 7 + (int) ((AREA_W - 14) * Math.max(0, Math.min(1, part))), oy + AREA_H - 4,
                part > 0.25 ? Draw.ACCENT : Draw.ALERT);
        if (flash > 0) {
            int c = Draw.ALERT;
            g.fill(ox, oy, ox + AREA_W, oy + 1, c);
            g.fill(ox, oy + AREA_H - 1, ox + AREA_W, oy + AREA_H, c);
            g.fill(ox, oy, ox + 1, oy + AREA_H, c);
            g.fill(ox + AREA_W - 1, oy, ox + AREA_W, oy + AREA_H, c);
        }
    }

    @Override
    String instruction() {
        return T.f("every_bite_leaves_its_signature");
    }

    @Override
    Control[] controls() {
        return new Control[] {
                new Control(T.f("slide_2"), InputConstants.KEY_A, InputConstants.KEY_D, Draw.OR, Draw.ARROW_LEFT, Draw.ARROW_RIGHT, Draw.OR, Draw.MOUSE),
                new Control(T.f("press"), InputConstants.KEY_SPACE, Draw.OR, Draw.ARROW_UP, Draw.OR, Draw.MOUSE)};
    }
}
