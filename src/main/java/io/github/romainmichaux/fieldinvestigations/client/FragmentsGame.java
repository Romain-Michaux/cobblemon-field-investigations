package io.github.romainmichaux.fieldinvestigations.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import io.github.romainmichaux.fieldinvestigations.Genetics;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

// Fragment assembly: DNA pieces lie on a slate bench and are dragged onto a rail, end to end. A piece only holds when its
// first two or three bases cover the end of the strand exactly; it is then snapped in with a click. A wrong piece forced
// onto the rail is thrown back and costs a mistake. Tainted pieces join nothing and go in the bin. Three strands, longer
// each time, against the clock; the last one pairs up, coils into a double helix and goes into a sample tube.
final class FragmentsGame extends MiniGame {
    static final ResourceLocation BENCH = Draw.texture("frag_bench"), BIN = Draw.texture("frag_bin"), TUBE = Draw.texture("frag_tube");
    static final int LEN = 5, STEP = 13, TILE = 12, RAIL_X = 10, RAIL_Y = 22, ROUNDS = 3, MAX_ERRORS = 3;
    static final int BIN_X = 248, BIN_Y = 74, BIN_W = 25, BIN_H = 34;
    static final int[] DURATION = {620, 720, 820};
    static final int ZIP = 2, HELIX = 80, TUBE_TIME = 46;

    static final class Piece {
        final int[] bases;
        final boolean tainted;
        final int index; // order in the strand (-1 for a tainted piece)
        double x, y, homeX, homeY;
        int shake;

        Piece(int[] bases, boolean tainted, int index) {
            this.bases = bases;
            this.tainted = tainted;
            this.index = index;
        }
    }

    final Random h;
    final String kind;
    final List<Piece> bench = new ArrayList<>();
    final List<Integer> strand = new ArrayList<>();
    final List<int[]> overlaps = new ArrayList<>(); // true overlap of piece i with the strand before it
    final List<double[]> sparks = new ArrayList<>();
    int round, errors, time, duration, next, tic, flash, glowFrom, glow, zipped, phase, phaseTicks, messageTicks;
    String message = "";
    int messageColour;
    Piece held;
    double grabX, grabY, mx, my;

    // Phases: 0 assembling, 1 the strand pairs up (zip), 2 pause before the next strand, 3 double helix, 4 into the tube.
    FragmentsGame(Random h, String kind) {
        this.h = h;
        this.kind = kind;
        newRound();
    }

    void newRound() {
        int pieces = 4 + round, tainted = round == 2 ? 2 : 1;
        List<int[]> real = new ArrayList<>(), decoys = new ArrayList<>();
        for (int attempt = 0; attempt < 400; attempt++) {
            real.clear();
            decoys.clear();
            overlaps.clear();
            int[] first = new int[LEN];
            for (int k = 0; k < LEN; k++) first[k] = h.nextInt(4);
            real.add(first);
            overlaps.add(new int[] {0});
            for (int i = 1; i < pieces; i++) {
                int o = 2 + h.nextInt(2);
                int[] prev = real.get(i - 1), piece = new int[LEN];
                for (int k = 0; k < o; k++) piece[k] = prev[LEN - o + k];
                for (int k = o; k < LEN; k++) piece[k] = h.nextInt(4);
                real.add(piece);
                overlaps.add(new int[] {o});
            }
            for (int d = 0; d < tainted; d++) {
                int[] piece = new int[LEN];
                for (int k = 0; k < LEN; k++) piece[k] = h.nextInt(4);
                decoys.add(piece);
            }
            if (unambiguous(real, decoys)) break;
        }
        bench.clear();
        strand.clear();
        for (int b : real.get(0)) strand.add(b);
        List<Piece> loose = new ArrayList<>();
        for (int i = 1; i < real.size(); i++) loose.add(new Piece(real.get(i), false, i));
        for (int[] d : decoys) loose.add(new Piece(d, true, -1));
        // Shuffled into the bench slots: three rows of three, the bin in the corner.
        List<Integer> slots = new ArrayList<>();
        for (int s = 0; s < 9; s++) slots.add(s);
        java.util.Collections.shuffle(slots, h);
        for (int i = 0; i < loose.size(); i++) {
            Piece p = loose.get(i);
            int s = slots.get(i);
            p.homeX = p.x = 10 + (s % 3) * 76 + h.nextInt(6);
            p.homeY = p.y = 56 + (s / 3) * 21;
            bench.add(p);
        }
        next = 1;
        duration = time = DURATION[round];
        zipped = 0;
        glowFrom = 0;
        glow = 20;
        phase = 0;
        shownSound();
    }

    // Each step of the strand must have one answer only: no other piece (nor the right one at another overlap) may cover
    // two or three bases of its end.
    boolean unambiguous(List<int[]> real, List<int[]> decoys) {
        for (int i = 0; i + 1 < real.size(); i++) {
            int[] end = real.get(i);
            for (int j = 1; j < real.size() + decoys.size(); j++) {
                int[] c = j < real.size() ? real.get(j) : decoys.get(j - real.size());
                if (j < real.size() && j <= i) continue;
                for (int k = 2; k <= 3; k++) {
                    boolean right = j == i + 1 && k == overlaps.get(j)[0];
                    if (!right && covers(end, c, k)) return false;
                }
            }
        }
        // Tainted pieces must not look identical to a real one.
        for (int[] d : decoys) for (int[] r : real) if (java.util.Arrays.equals(d, r)) return false;
        return true;
    }

    static boolean covers(int[] end, int[] piece, int k) {
        for (int m = 0; m < k; m++) if (end[LEN - k + m] != piece[m]) return false;
        return true;
    }

    int strandEnd() {
        return ox + RAIL_X + strand.size() * STEP;
    }

    // Overlap the held piece would take if dropped now (0 when not over the rail).
    int snap() {
        if (held == null || Math.abs(my - grabY - (oy + RAIL_Y)) > 14) return 0;
        int k = (int) Math.round((strandEnd() - (mx - grabX)) / STEP);
        return k >= 1 && k <= LEN - 1 ? k : 0;
    }

    @Override
    void tick() {
        tic++;
        if (flash > 0) flash--;
        if (glow > 0) glow--;
        if (messageTicks > 0) messageTicks--;
        sparks.removeIf(s -> {
            s[0] += s[2];
            s[1] += s[3];
            s[3] += 0.15;
            return --s[4] <= 0;
        });
        for (Piece p : bench) {
            if (p != held) {
                p.x += (p.homeX - p.x) * 0.25;
                p.y += (p.homeY - p.y) * 0.25;
            }
            if (p.shake > 0) p.shake--;
        }
        switch (phase) {
            case 0 -> {
                if (--time <= 0) {
                    end(false);
                    return;
                }
                hurryTicks(time, duration);
                if (next >= overlaps.size() && bench.isEmpty()) {
                    phase = 1;
                    phaseTicks = 0;
                }
            }
            case 1 -> {
                if (++phaseTicks % ZIP == 0 && zipped < strand.size()) {
                    zipped++;
                    Draw.beep(Sounds.TICK, 1f + zipped * 0.04f, 0.5f);
                }
                if (zipped >= strand.size()) {
                    phaseTicks = 0;
                    if (round + 1 >= ROUNDS) {
                        phase = 3;
                        Draw.beep(Sounds.IDENTIFIED, 1f, 0.8f);
                    } else {
                        phase = 2;
                        Draw.note(1f + round * 0.25f);
                    }
                }
            }
            case 2 -> {
                if (++phaseTicks >= 24) {
                    round++;
                    newRound();
                }
            }
            case 3 -> {
                if (++phaseTicks >= HELIX) {
                    phase = 4;
                    phaseTicks = 0;
                    Draw.beep(Sounds.VIAL, 1f, 0.8f);
                }
            }
            case 4 -> {
                if (++phaseTicks >= TUBE_TIME) {
                    perfect = errors == 0;
                    end(true);
                }
            }
            default -> {}
        }
    }

    @Override
    void click(double x, double y) {
        if (phase != 0 || finished) return;
        for (int i = bench.size() - 1; i >= 0; i--) {
            Piece p = bench.get(i);
            if (Draw.in(x, y, ox + (int) p.x - 2, oy + (int) p.y - 5, LEN * STEP + 3, TILE + 8)) {
                held = p;
                grabX = x - (ox + p.x);
                grabY = y - (oy + p.y);
                bench.remove(i);
                bench.add(p);
                Draw.click(1.3f);
                return;
            }
        }
    }

    @Override
    void released(double x, double y) {
        if (held == null) return;
        Piece p = held;
        held = null;
        if (phase != 0) return;
        int k = snapAt(x, y, p);
        if (Draw.in(x, y, ox + BIN_X - 6, oy + BIN_Y - 6, BIN_W + 12, BIN_H + 12)) {
            if (p.tainted) {
                bench.remove(p);
                Draw.beep(Sounds.DROP, 0.8f, 0.8f);
                say(T.f("tainted_into_the_bin"), Draw.GREEN);
            } else {
                mistake(p, T.f("this_one_belongs_to_the"));
            }
            return;
        }
        if (k == 0) {
            Draw.click(0.8f);
            return;
        }
        if (!p.tainted && p.index == next && k == overlaps.get(next)[0]) {
            bench.remove(p);
            glowFrom = strand.size() - k;
            for (int m = k; m < LEN; m++) strand.add(p.bases[m]);
            glow = 16;
            next++;
            for (int s = 0; s < 10; s++) {
                sparks.add(new double[] {strandEnd() - ox - (LEN - k) * STEP / 2.0, RAIL_Y + 4, h.nextGaussian() * 1.2, -1 - h.nextDouble() * 1.5, 14});
            }
            Draw.beep(Sounds.CARABINER, 1.2f + next * 0.06f, 0.8f);
        } else {
            mistake(p, T.f("it_doesn_t_hold"));
        }
    }

    int snapAt(double x, double y, Piece p) {
        if (Math.abs(y - grabY - (oy + RAIL_Y)) > 14) return 0;
        int k = (int) Math.round((strandEnd() - (x - grabX)) / STEP);
        return k >= 1 && k <= LEN - 1 ? k : 0;
    }

    void mistake(Piece p, String why) {
        p.shake = 12;
        flash = 10;
        Draw.note(0.5f);
        say(why, Draw.ALERT);
        if (++errors >= MAX_ERRORS) end(false);
    }

    void say(String s, int colour) {
        message = s;
        messageColour = colour;
        messageTicks = 40;
    }

    // A base: a flat tile in its colour, with a darker rim, a highlight and a crisp letter.
    static void tile(GuiGraphics g, int x, int y, int b) {
        int c = Draw.BASE_COLOURS[b];
        g.fill(x, y, x + TILE, y + TILE, Draw.darken(c, 0.4));
        g.fill(x + 1, y + 1, x + TILE - 1, y + TILE - 1, c);
        g.fill(x + 1, y + 1, x + TILE - 1, y + 2, Draw.darken(c, 1.3));
        g.fill(x + 1, y + TILE - 2, x + TILE - 1, y + TILE - 1, Draw.darken(c, 0.75));
        String l = Draw.BASES[b];
        g.drawString(Draw.textFont(), l, x + (TILE - Draw.textFont().width(l) + 1) / 2, y + 3, 0xFF10161C, false);
    }

    // A fragment: a pale backbone ribbon over its row of base tiles.
    static void fragment(GuiGraphics g, int x, int y, int[] bases) {
        int n = bases.length;
        g.fill(x - 1, y - 4, x + n * STEP, y - 1, 0xFF4E5E6E);
        g.fill(x - 1, y - 4, x + n * STEP, y - 3, 0xFFC9D3DD);
        for (int m = 0; m < n; m++) {
            g.fill(x + m * STEP + 5, y - 2, x + m * STEP + 7, y, 0xFF8FA0B0);
            tile(g, x + m * STEP, y, bases[m]);
        }
    }

    @Override
    void draw(GuiGraphics g, int x, int y, float pt) {
        mx = x;
        my = y;
        int ax = ox, ay = oy;
        Draw.image(g, BENCH, ax, ay, AREA_W, AREA_H);
        if (flash > 0) {
            int c = Draw.ALERT;
            g.fill(ax, ay, ax + AREA_W, ay + 1, c);
            g.fill(ax, ay + AREA_H - 1, ax + AREA_W, ay + AREA_H, c);
            g.fill(ax, ay, ax + 1, ay + AREA_H, c);
            g.fill(ax + AREA_W - 1, ay, ax + AREA_W, ay + AREA_H, c);
        }
        if (phase >= 3) {
            drawFinale(g, ax, ay, pt);
        } else {
            drawRail(g, ax, ay);
            // The bin, and the loose fragments on the bench (the held one last, over everything).
            Draw.image(g, BIN, ax + BIN_X, ay + BIN_Y, BIN_W, BIN_H);
            for (Piece p : bench) {
                if (p == held) continue;
                int sx = p.shake > 0 ? (int) (Math.sin(p.shake * 1.7) * 2) : 0;
                fragment(g, ax + (int) p.x + sx, ay + (int) p.y, p.bases);
            }
            if (held != null) drawHeld(g, ax, ay);
        }
        for (double[] s : sparks) g.fill(ax + (int) s[0], ay + (int) s[1], ax + (int) s[0] + 1, ay + (int) s[1] + 1, 0xFFFFF4B0);

        // Counters, time line and the last message.
        String left = T.f("strand") + Math.min(ROUNDS, round + 1) + " / " + ROUNDS, right = T.f("mistakes") + errors + " / " + MAX_ERRORS;
        text(g, left, ax + 6, ay + 3, Draw.TEXT);
        text(g, right, ax + AREA_W - 6 - Draw.textFont().width(right), ay + 3, errors > 0 ? Draw.ALERT : Draw.GREY);
        if (phase == 0) {
            double part = time / (double) duration;
            g.fill(ax + 6, ay + 13, ax + 6 + (int) ((AREA_W - 12) * part), ay + 14, part > 0.25 ? Draw.ACCENT : Draw.ALERT);
            if (next >= overlaps.size() && !bench.isEmpty() && messageTicks == 0) say(T.f("throw_away_the_tainted_pieces"), Draw.YELLOW);
        }
        if (messageTicks > 0) Draw.centre(g, message, ax + AREA_W / 2, ay + 3, messageColour);
        else if (phase == 1 || phase == 2) Draw.centre(g, T.f("strand_complete"), ax + AREA_W / 2, ay + 3, Draw.GREEN);
    }

    // The rail with the strand so far; the new bases glow for a moment, then (strand complete) the partner strand zips in.
    void drawRail(GuiGraphics g, int ax, int ay) {
        int rx = ax + RAIL_X - 3, ry = ay + RAIL_Y - 7;
        g.fill(rx, ry, ax + AREA_W - 6, ry + 23, 0x90101820);
        g.fill(rx, ry, ax + AREA_W - 6, ry + 1, 0xFF6F8394);
        g.fill(rx, ry + 22, ax + AREA_W - 6, ry + 23, 0xFF2A3644);
        for (int t = 0; t < 20; t++) g.fill(ax + RAIL_X + t * STEP + 5, ry + 1, ax + RAIL_X + t * STEP + 7, ry + 3, 0xFF4E6070);
        int[] bases = strand.stream().mapToInt(Integer::intValue).toArray();
        fragment(g, ax + RAIL_X, ay + RAIL_Y, bases);
        if (glow > 0) {
            for (int m = Math.max(0, glowFrom); m < strand.size(); m++) {
                Draw.circle(g, ax + RAIL_X + m * STEP + 5, ay + RAIL_Y + 5, 6 + (16 - glow) / 4.0, 1, ((glow * 10) << 24) | 0xFFF4B0);
            }
        }
        // The primer: the first piece, laid by the lab.
        if (phase == 0) g.fill(ax + RAIL_X - 1, ay + RAIL_Y + TILE + 1, ax + RAIL_X + LEN * STEP - 1, ay + RAIL_Y + TILE + 2, Draw.ACCENT);
        for (int m = 0; m < zipped; m++) {
            int bx = ax + RAIL_X + m * STEP;
            g.fill(bx + 5, ay + RAIL_Y + TILE, bx + 7, ay + RAIL_Y + TILE + 2, 0xFFE8F0F6);
            tile(g, bx, ay + RAIL_Y + TILE + 2, strand.get(m) ^ 1);
        }
    }

    // The held piece follows the pointer; over the rail it snaps to the overlap it would take, showing which of its bases
    // cover the strand (green when they match, red when not).
    void drawHeld(GuiGraphics g, int ax, int ay) {
        int k = snap();
        if (k == 0) {
            fragment(g, (int) (mx - grabX), (int) (my - grabY), held.bases);
            return;
        }
        // Drawn just under the rail so both rows stay readable, joined base to base where they overlap.
        int px = strandEnd() - k * STEP, py = ay + RAIL_Y, gy = py + 20;
        for (int m = 0; m < k; m++) {
            int bx = px + m * STEP, c = strand.get(strand.size() - k + m) == held.bases[m] ? Draw.GREEN : Draw.ALERT;
            g.fill(bx + 4, py + TILE, bx + 8, gy - 4, c);
            g.fill(bx - 1, gy - 1, bx + TILE + 1, gy + TILE + 1, c);
        }
        fragment(g, px, gy, held.bases);
    }

    // The finished strand coils into a double helix, then shrinks into the sample tube.
    void drawFinale(GuiGraphics g, int ax, int ay, float pt) {
        int n = strand.size();
        double t = phaseTicks + pt;
        double grow = phase == 3 ? Math.min(1, t / 20) : 1, squeeze = phase == 4 ? Math.min(1, t / (TUBE_TIME * 0.6)) : 0;
        int tubeX = ax + AREA_W / 2 - 5, tubeY = ay + 44;
        double cx = ax + RAIL_X + n * STEP / 2.0, cy = ay + 46;
        double width = n * STEP * (1 - squeeze * 0.92), amp = 14 * grow * (1 - squeeze * 0.8);
        double centreX = cx + (tubeX + 5 - cx) * squeeze, centreY = cy + (tubeY + 30 - cy) * squeeze;
        if (phase == 4) {
            Draw.image(g, TUBE, tubeX - 2 + 2, tubeY, 11, 53);
        }
        for (int m = 0; m < n; m++) {
            double u = n == 1 ? 0.5 : m / (double) (n - 1);
            double px = centreX - width / 2 + u * width, a = m * 0.62 + tic * 0.12 + pt * 0.12;
            double y1 = centreY + Math.sin(a) * amp, y2 = centreY - Math.sin(a) * amp;
            boolean front = Math.cos(a) > 0;
            int c1 = Draw.BASE_COLOURS[strand.get(m)], c2 = Draw.BASE_COLOURS[strand.get(m) ^ 1];
            int fade = phase == 4 ? (int) (255 * (1 - Math.min(1, Math.max(0, (t - TUBE_TIME * 0.55) / (TUBE_TIME * 0.3))))) : 255;
            if (fade <= 0) continue;
            int a8 = fade << 24;
            // Rung: half in each base's colour.
            double mid = (y1 + y2) / 2;
            g.fill((int) px, (int) Math.min(y1, mid), (int) px + 2, (int) Math.max(y1, mid) + 1, (c1 & 0xFFFFFF) | a8);
            g.fill((int) px, (int) Math.min(y2, mid), (int) px + 2, (int) Math.max(y2, mid) + 1, (c2 & 0xFFFFFF) | a8);
            int back = (0x7A8C9E) | a8, fore = 0xE8F0F6 | a8;
            g.fill((int) px - 1, (int) y1 - 1, (int) px + 3, (int) y1 + 2, front ? fore : back);
            g.fill((int) px - 1, (int) y2 - 1, (int) px + 3, (int) y2 + 2, front ? back : fore);
        }
        if (phase == 4) {
            // The tube fills with the glow of the strand; its label carries the marker.
            int fill = (int) (30 * Math.min(1, t / TUBE_TIME));
            g.fill(tubeX + 2, tubeY + 50 - fill, tubeX + 9, tubeY + 50, 0xA0B8FFD0);
            String label = kind != null && Genetics.MARKER_NAMES.containsKey(kind) ? T.c(Genetics.MARKER_NAMES.get(kind)) : "";
            if (!label.isEmpty()) Draw.centre(g, label, tubeX + 5, tubeY + 56, Draw.GREEN);
        }
        Draw.centre(g, T.f("strand_complete"), ax + AREA_W / 2, ay + 22, Draw.GREEN);
    }

    @Override
    String instruction() {
        return T.f("the_code_came_out_of");
    }

    @Override
    Control[] controls() {
        return new Control[] {new Control(T.f("move_a_piece"), Draw.MOUSE)};
    }
}
