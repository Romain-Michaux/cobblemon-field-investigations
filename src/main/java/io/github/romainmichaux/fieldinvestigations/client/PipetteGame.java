package io.github.romainmichaux.fieldinvestigations.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

// Pipetting (strange deposit): a micropipette shares the sample out into three wells, each labelled with its volume.
// Holding draws liquid up the tip, faster at first; on release it keeps rising a little (the plunger springs back), so
// the release must come before the line, read on the tip's graduations. A wrong dose is pushed back into the dish,
// minus a little that is spoiled, so the deposit runs low with each miss. A right dose is carried over its well and pushed out: hold until the tip is empty, and let go
// before air blows a bubble. At the end the three wells react, one after the other.
final class PipetteGame extends MiniGame {
    static final ResourceLocation BENCH = Draw.texture("pip_bench"), BODY = Draw.texture("pip_body");
    static final int MAX_FAULTS = 3, TIP = 52, MOVE = 24, BUBBLE = 14;
    static final double CAPACITY = 40, MARGIN = 18, LOSS = 4;
    static final double[] TOLERANCE = {3, 2.5, 2};
    static final int[] WELL_X = {162, 204, 246};
    static final int SOURCE_X = 96, WORKTOP = 97, SAMPLE = 0xFF9B5FC0;
    static final int[] REACTION = {0xFF5FCB6C, 0xFF4F9BE0, 0xFFE06FA8};
    // What the pipette is doing.
    static final int READY = 0, DRAW = 1, SETTLE = 2, CARRY = 3, DISPENSE = 4, BACK = 5, WASTE = 6, ENDING = 7;
    final Random h;
    final int[] doses = new int[3];
    final double[] wells = new double[3];
    final boolean[] bubbles = new boolean[3];
    final double full;
    double level, v, source;
    int phase = READY, t, round, faults, emptyTicks, tic, messageTicks, messageColour;
    boolean held;
    String message = "";

    PipetteGame(Random h) {
        this.h = h;
        // Three different volumes, in steps of 5 µL.
        List<Integer> pool = new ArrayList<>(List.of(10, 15, 20, 25, 30, 35));
        double sum = 0;
        for (int i = 0; i < 3; i++) {
            doses[i] = pool.remove(h.nextInt(pool.size()));
            sum += doses[i];
        }
        full = sum + MARGIN;
        source = full;
    }

    void say(String s, int colour) {
        message = s;
        messageColour = colour;
        messageTicks = 40;
    }

    // Liquid drawn from the tube into the tip.
    void draw(double dv) {
        dv = Math.min(dv, source);
        level += dv;
        source -= dv;
        if (tic % 2 == 0) Draw.beep(Sounds.SCAN, (float) (0.6 + level / CAPACITY * 1.3), 0.3f);
        if (level >= CAPACITY) {
            level = CAPACITY;
            judge();
        } else if (source <= 0 && phase == DRAW) {
            phase = SETTLE;
            v = 0;
        }
    }

    void judge() {
        v = 0;
        if (Math.abs(level - doses[round]) <= TOLERANCE[round]) {
            phase = CARRY;
            t = 0;
            say(T.f("right_dose"), Draw.GREEN);
            Draw.beep(Sounds.CHIME, 1.5f, 0.45f);
        } else {
            say(level >= CAPACITY ? T.f("overflow") : level < doses[round] ? T.f("too_little") : T.f("too_much"), Draw.ALERT);
            fault();
            // Pushed back into the dish, all but a little that is spoiled.
            source += Math.max(0, level - LOSS);
            phase = WASTE;
            t = 0;
        }
    }

    void fault() {
        Draw.note(0.5f);
        if (++faults >= MAX_FAULTS) end(false);
    }

    @Override
    void tick() {
        tic++;
        if (messageTicks > 0) messageTicks--;
        if (finished) return;
        switch (phase) {
            case READY -> {
                if (source < doses[round] - TOLERANCE[round]) {
                    say(T.f("not_enough_sample_left"), Draw.ALERT);
                    end(false);
                } else if (held) {
                    phase = DRAW;
                    v = 0;
                    Draw.beep(Sounds.VIAL, 1.3f, 0.5f);
                }
            }
            case DRAW -> {
                if (!held) {
                    phase = SETTLE;
                } else {
                    // Fast at first, slower as the tip fills.
                    v = Math.min(v + 0.07, Math.max(0.45, 1.0 - level * 0.01));
                    draw(v);
                }
            }
            case SETTLE -> {
                // The plunger springs back: the liquid still rises a little.
                v *= 0.85;
                draw(v);
                if (phase == SETTLE && v < 0.03) judge();
            }
            case CARRY, BACK -> {
                if (++t >= MOVE) {
                    if (phase == CARRY) {
                        phase = DISPENSE;
                        emptyTicks = 0;
                    } else {
                        phase = READY;
                        level = 0;
                    }
                    t = 0;
                }
            }
            case DISPENSE -> {
                if (held) {
                    if (level > 0) {
                        double dv = Math.min(level, 1.3);
                        level -= dv;
                        wells[round] += dv;
                        if (tic % 3 == 0) Draw.beep(Sounds.VIAL, 1.6f, 0.25f);
                    } else if (++emptyTicks > BUBBLE) {
                        // Pushed on with an empty tip: air blows a bubble into the well.
                        bubbles[round] = true;
                        say(T.f("bubble"), Draw.ALERT);
                        Draw.beep(Sounds.BRANCH, 1.8f, 0.4f);
                        fault();
                        filled();
                    }
                } else if (level <= 0 && emptyTicks > 0) {
                    if (!bubbles[round]) say(T.f("well_filled"), Draw.GREEN);
                    Draw.note(1f + round * 0.25f);
                    filled();
                }
            }
            case WASTE -> {
                // The wrong dose is pushed back out.
                level = Math.max(0, level - 2.5);
                if (++t >= 24) {
                    phase = READY;
                    level = 0;
                    t = 0;
                }
            }
            case ENDING -> {
                t++;
                if (t % 20 == 10 && t <= 50) Draw.beep(Sounds.CHIME, 1.2f + t / 50f * 0.6f, 0.45f);
                if (t == 76) Draw.beep(Sounds.PAPER, 1f, 0.8f);
                if (t >= 110) {
                    perfect = faults == 0;
                    end(true);
                }
            }
            default -> {}
        }
    }

    void filled() {
        if (finished) return;
        level = 0;
        t = 0;
        if (++round >= 3) {
            phase = ENDING;
        } else {
            phase = BACK;
        }
    }

    @Override
    void click(double mx, double my) {
        held = true;
    }

    @Override
    void released(double mx, double my) {
        held = false;
    }

    // The pipette's place: the x of its tip, and the y of the tip's end (dipped, or raised while carried).
    double tipX() {
        double a = phase == CARRY ? ease() : phase == BACK ? 1 - ease() : 0;
        int from = SOURCE_X, to = WELL_X[Math.min(2, phase == BACK ? round - 1 : round)];
        if (phase == DISPENSE || phase == ENDING) return WELL_X[Math.min(2, phase == ENDING ? 2 : round)];
        return from + (to - from) * a;
    }

    double tipY() {
        double lift = phase == CARRY || phase == BACK ? Math.sin(Math.min(1, t / (double) MOVE) * Math.PI) : 0;
        if (phase == ENDING) lift = Math.min(1, t / 20.0);
        return 93 - lift * 40;
    }

    double ease() {
        double a = Math.min(1, t / (double) MOVE);
        return a * a * (3 - 2 * a);
    }

    @Override
    void draw(GuiGraphics g, int mx, int my, float pt) {
        int ax = ox, ay = oy;
        Draw.image(g, BENCH, ax, ay, AREA_W, AREA_H);
        drawPlate(g, ax, ay);
        drawSource(g, ax, ay, false);
        g.enableScissor(ax, ay, ax + AREA_W, ay + AREA_H);
        drawPipette(g, ax + (int) Math.round(tipX()), ay + (int) Math.round(tipY()));
        g.disableScissor();
        // The dish's front glass over a dipped tip.
        drawSource(g, ax, ay, true);
        // Counters and the last message.
        String left = T.f("wells") + Math.min(3, round) + " / 3";
        g.fill(ax + 2, ay + 2, ax + 10 + Draw.textFont().width(left), ay + 12, 0xC0101418);
        g.drawString(Draw.textFont(), left, ax + 6, ay + 3, Draw.TEXT, false);
        String right = T.f("faults") + faults + " / " + MAX_FAULTS;
        int rw = Draw.textFont().width(right);
        g.fill(ax + AREA_W - 10 - rw, ay + 2, ax + AREA_W - 2, ay + 12, 0xC0101418);
        g.drawString(Draw.textFont(), right, ax + AREA_W - 6 - rw, ay + 3, faults > 0 ? Draw.ALERT : Draw.GREY, false);
        if (messageTicks > 0) {
            int w = Draw.textFont().width(message);
            int cx = ax + AREA_W / 2;
            g.fill(cx - w / 2 - 4, ay + 2, cx + w / 2 + 4, ay + 12, 0xC0101418);
            Draw.centre(g, message, cx, ay + 3, messageColour);
        }
        if (phase == ENDING && t >= 76) stamp(g, ax + WELL_X[1], ay + 52);
    }

    // The strange deposit, gathered in a shallow glass dish: its level goes down with each draw. Drawn in two passes:
    // the back and the liquid, then (over the dipped tip) the front glass. Shallow, so the tip stays in full view.
    void drawSource(GuiGraphics g, int ax, int ay, boolean front) {
        int cx = ax + SOURCE_X, half = 26, top = ay + 82, bottom = ay + WORKTOP;
        if (!front) {
            // The glass dish: back rim, then the strange liquid, its surface lower as it is drawn.
            g.fill(cx - half + 2, bottom, cx + half - 2, bottom + 2, 0x50000000);
            g.fill(cx - half, top, cx + half, top + 2, 0x80E8F0F8);
            g.fill(cx - half + 1, top + 2, cx + half - 1, bottom - 1, 0x30D8E8F0);
            int depth = (int) Math.round((bottom - top - 4) * source / full);
            int liquidTop = bottom - 2 - depth;
            if (depth > 0) {
                for (int y = liquidTop; y < bottom - 1; y++) {
                    int c = mix(0xFFB070D8, 0xFF5A2A80, (y - liquidTop) / (double) Math.max(1, bottom - 1 - liquidTop));
                    g.fill(cx - half + 1, y, cx + half - 1, y + 1, c);
                }
                g.fill(cx - half + 1, liquidTop, cx + half - 1, liquidTop + 1, 0xFFD8A8F4);
                // Motes glowing in the strange liquid.
                for (int i = 0; i < 5; i++) {
                    int sx = cx - half + 5 + (int) ((i * 11 + tic * 0.2) % (2 * half - 10));
                    int sy = liquidTop + 1 + (i * 3) % Math.max(1, depth);
                    if (sy < bottom - 2) g.fill(sx, sy, sx + 1, sy + 1, (tic / 6 + i) % 3 == 0 ? 0xFFFFF0FF : 0xA0F4D8FF);
                }
            }
            return;
        }
        // Front glass over the dipped tip: the front rim, the walls and a glint.
        g.fill(cx - half - 1, top, cx - half + 1, bottom, 0xE0E8F0F8);
        g.fill(cx + half - 1, top, cx + half + 1, bottom, 0xE0B8C8D8);
        g.fill(cx - half, bottom - 1, cx + half, bottom, 0xE0B8C8D8);
        g.fill(cx - half, top + 3, cx + half, top + 4, 0x50FFFFFF);
        g.fill(cx - half + 4, top + 5, cx - half + 10, top + 6, 0x80FFFFFF);
    }

    // The plate with its three wells; each well's volume is printed on the plate's front edge.
    void drawPlate(GuiGraphics g, int ax, int ay) {
        int x0 = ax + WELL_X[0] - 24, x1 = ax + WELL_X[2] + 24, y0 = ay + 80, y1 = ay + WORKTOP + 12;
        g.fill(x0 + 2, y1, x1 + 2, y1 + 2, 0x50000000);
        g.fill(x0, y0, x1, y1, 0xFFDDE3EA);
        g.fill(x0, y0, x1, y0 + 2, 0xFFF6FAFD);
        g.fill(x0, y0 + 16, x1, y0 + 17, 0xFFAEB8C4);
        g.fill(x0, y1 - 1, x1, y1, 0xFF8A96A4);
        for (int i = 0; i < 3; i++) {
            int cx = ax + WELL_X[i], wx = cx - 14, wy = y0 + 3;
            g.fill(wx, wy, wx + 28, wy + 12, 0xFF2A3440);
            g.fill(wx + 1, wy + 1, wx + 27, wy + 11, 0xFF3C4854);
            int fill = (int) Math.round(10 * Math.min(1, wells[i] / CAPACITY * 1.3));
            if (fill > 0) {
                int c = SAMPLE;
                if (phase == ENDING) c = mix(SAMPLE, REACTION[i], Math.max(0, Math.min(1, (t - 10 - i * 20) / 20.0)));
                g.fill(wx + 1, wy + 11 - fill, wx + 27, wy + 11, c);
                g.fill(wx + 1, wy + 11 - fill, wx + 27, wy + 12 - fill, Draw.darken(c, 1.3));
            }
            if (bubbles[i]) g.fill(wx + 18, wy + 9 - fill, wx + 22, wy + 12 - fill, 0xD0F0F8FF);
            // The volume, printed on the front edge; the well being filled is marked.
            String label = doses[i] + " µL";
            int w = Draw.textFont().width(label);
            boolean current = i == round && phase != ENDING;
            if (current) g.fill(cx - w / 2 - 3, y0 + 18, cx + w / 2 + 3, y0 + 28, 0xFFF2D04A);
            g.drawString(Draw.textFont(), label, cx - w / 2, y0 + 19, i < round ? 0xFF2E7D32 : current ? Draw.INK : 0xFF5D6E80, false);
        }
    }

    // The pipette, seen close: the end of its body coming from above, and a wide clear tip with its graduations every
    // 5 µL and the line to reach; the liquid rises inside (red while being thrown away).
    void drawPipette(GuiGraphics g, int x, int tipEnd) {
        int tipTop = tipEnd - TIP;
        g.pose().pushPose();
        g.pose().translate(x - 9, tipTop - 122, 0);
        g.pose().scale(2, 2, 1);
        Draw.image(g, BODY, 0, 0, 9, 61);
        g.pose().popPose();
        int liquid = (int) Math.round(TIP * level / CAPACITY);
        int colour = phase == WASTE ? Draw.ALERT : SAMPLE;
        for (int i = 0; i < TIP; i++) {
            int y = tipTop + i, half = 7 - (int) (i * 5.5 / TIP);
            boolean wet = TIP - i <= liquid;
            g.fill(x - half, y, x + half + 1, y + 1, wet ? colour : 0xB0DCE6EE);
            if (wet && TIP - i == liquid) g.fill(x - half, y, x + half + 1, y + 1, phase == WASTE ? 0xFFFF9090 : 0xFFC890E8);
            g.fill(x - half - 1, y, x - half, y + 1, 0xE0E8F0F8);
            g.fill(x + half + 1, y, x + half + 2, y + 1, 0xE0B8C8D8);
            if (i < TIP - 6) g.fill(x - half + 1, y, x - half + 2, y + 1, 0x70FFFFFF);
        }
        // Graduations every 5 µL on the tip.
        for (int v = 5; v < CAPACITY; v += 5) {
            int i = TIP - (int) Math.round(TIP * v / CAPACITY), half = 7 - (int) (i * 5.5 / TIP);
            g.fill(x + half - (v % 10 == 0 ? 4 : 2), tipTop + i, x + half + 1, tipTop + i + 1, 0xC0384858);
        }
        // The line to reach, with its volume.
        if (phase == READY || phase == DRAW || phase == SETTLE) {
            int i = TIP - (int) Math.round(TIP * doses[round] / CAPACITY), half = 7 - (int) (i * 5.5 / TIP);
            int ly = tipTop + i;
            g.fill(x - half - 3, ly, x + half + 6, ly + 1, Draw.YELLOW);
            String s = String.valueOf(doses[round]);
            int w = Draw.textFont().width(s);
            g.fill(x + half + 6, ly - 5, x + half + 10 + w, ly + 5, 0xE0101418);
            g.drawString(Draw.textFont(), s, x + half + 8, ly - 4, Draw.YELLOW, false);
        }
        // A drop falling while the wrong dose goes to waste.
        if (phase == WASTE && level > 0) g.fill(x, tipEnd + 2 + (tic % 6), x + 2, tipEnd + 5 + (tic % 6), Draw.ALERT);
    }

    static int mix(int a, int b, double k) {
        int r = 0xFF000000;
        for (int sh = 0; sh < 24; sh += 8) {
            int ca = (a >> sh) & 0xFF, cb = (b >> sh) & 0xFF;
            r |= ((int) Math.round(ca + (cb - ca) * k)) << sh;
        }
        return r;
    }

    // The validation stamp over the plate.
    void stamp(GuiGraphics g, int cx, int cy) {
        String s = T.f("sample_validated");
        int w = Draw.textFont().width(s) + 12;
        double k = Math.min(1, (t - 76) / 6.0);
        g.pose().pushPose();
        g.pose().translate(cx, cy, 300);
        float sc = (float) (1.6 - 0.6 * k);
        g.pose().scale(sc, sc, 1);
        g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-8));
        g.fill(-w / 2 - 2, -9, w / 2 + 2, 9, 0xFF2E7D32);
        g.fill(-w / 2, -7, w / 2, 7, 0xF0F4F0E6);
        g.drawString(Draw.textFont(), s, -Draw.textFont().width(s) / 2, -4, 0xFF2E7D32, false);
        g.pose().popPose();
    }

    @Override
    String instruction() {
        return T.f("hold_to_draw_the_liquid");
    }

    @Override
    Control[] controls() {
        return new Control[] {new Control(T.f("hold_draw_then_dispense"), Draw.MOUSE)};
    }
}
