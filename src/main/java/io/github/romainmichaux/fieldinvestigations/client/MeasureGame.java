package io.github.romainmichaux.fieldinvestigations.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

// Footprint measurement: three prints in the mud, one after the other, each measured with a calliper. Its fixed jaw is
// set on one edge, then the sliding jaw brought to the opposite one, across and then along the print. The true edge is
// the hollow, not the ridge of mud pushed up around it; a loupe under the pointer magnifies the spot. Rain slowly wears
// the edges away, a leaf hides part of the second print, and the tolerance tightens. Each measure is drawn on the print
// as a dimension line; at the end the field notebook opens on the three sketches.
final class MeasureGame extends MiniGame {
    static final ResourceLocation MUD = Draw.texture("measure_mud"), LEAF = Draw.texture("measure_leaf"),
            NOTEBOOK = Draw.texture("measure_notebook");
    static final int PRINTS = 3, MAX_FAULTS = 3, LOUPE = 22, NEXT = 40, NOTES = 140;
    static final double[] TOLERANCE = {3, 2.5, 2}, WEAR = {1 / 560.0, 1 / 480.0, 1 / 420.0};
    final Random h;
    // The print: its texture and size (texels), and the creature's base scale (from its height, 0.5 to 1).
    ResourceLocation footprint = Draw.texture("footprint");
    int texW = 74, texH = 70;
    double base;
    final double[][] measured = new double[PRINTS][2];
    final boolean[][] good = new boolean[PRINTS][2];
    final List<double[]> drops = new ArrayList<>(), splashes = new ArrayList<>();
    int print, step, faults, flash, tic, pause, notes = -1;
    int left, top, w, hgt;
    double fixed, wear, mx, my, scale;
    boolean flashOk, washed;
    // The leaf: where it lies, and its flight once flicked away.
    double leafX, leafY, leafAngle, leafVx, leafVy;
    int leafState; // 0 none, 1 lying, 2 flying

    MeasureGame(Random h) {
        this.h = h;
        // Without a tracked creature (practice), any print at any size.
        setPrint(SHAPES[h.nextInt(SHAPES.length)], (float) (0.3 + h.nextDouble() * 2.2));
    }

    static final String[] SHAPES = {"paw", "claw", "talon", "webbed", "bug", "hand", "blob"};

    static final double SMALL = Math.log(0.25), LARGE = Math.log(3);

    // The tracked creature: its family's print, sized after its height (a log scale from 25 cm to 3 m).
    void setPrint(String shape, float height) {
        footprint = shape.equals("paw") ? Draw.texture("footprint") : Draw.texture("print_" + shape);
        try (var in = Minecraft.getInstance().getResourceManager().open(footprint);
                var image = com.mojang.blaze3d.platform.NativeImage.read(in)) {
            texW = image.getWidth();
            texH = image.getHeight();
        } catch (Exception e) {
            footprint = Draw.texture("footprint");
            texW = 74;
            texH = 70;
        }
        if (height > 0) base = 0.5 + 0.5 * Math.max(0, Math.min(1, (Math.log(height) - SMALL) / (LARGE - SMALL)));
        newPrint();
    }

    // The height the measured prints point to, in metres.
    double estimate() {
        double sum = 0;
        int n = 0;
        for (double[] m : measured) {
            if (m[0] <= 0 || m[1] <= 0) continue;
            sum += Math.max(m[0], m[1]) / 72;
            n++;
        }
        if (n == 0) return -1;
        double s = Math.max(0, Math.min(1, (sum / n - 0.5) / 0.5));
        return Math.exp(SMALL + s * (LARGE - SMALL));
    }

    void newPrint() {
        // The three prints of one creature differ a little (depth, stride).
        scale = (base + (h.nextDouble() - 0.5) * 0.08) * 72 / Math.max(texW, texH);
        w = (int) Math.round(texW * scale);
        hgt = (int) Math.round(texH * scale);
        left = 112 - w / 2 + h.nextInt(21) - 10;
        top = 62 - hgt / 2 + h.nextInt(5) - 2;
        step = 0;
        wear = 0;
        washed = false;
        leafState = 0;
        if (print == 1) {
            // The leaf lies across one edge, chosen at random.
            leafState = 1;
            switch (h.nextInt(4)) {
                case 0 -> { leafX = left; leafY = top + hgt / 2.0; }
                case 1 -> { leafX = left + w; leafY = top + hgt / 2.0; }
                case 2 -> { leafX = left + w / 2.0; leafY = top; }
                default -> { leafX = left + w / 2.0; leafY = top + hgt; }
            }
            leafAngle = h.nextInt(360);
        }
    }

    // The edge the current step aims at, in play-area pixels.
    double target() {
        return switch (step) {
            case 0 -> left;
            case 1 -> left + w;
            case 2 -> top;
            default -> top + hgt;
        };
    }

    boolean horizontal() {
        return step < 2;
    }

    // Where the jaw being placed sits, following the pointer (the sliding jaw never goes past the fixed one).
    double jaw() {
        double p = horizontal() ? mx - ox : my - oy;
        if (step % 2 == 1) p = Math.max(fixed + 4, p);
        return horizontal() ? Math.max(4, Math.min(AREA_W - 30, p)) : Math.max(18, Math.min(AREA_H - 4, p));
    }

    @Override
    void tick() {
        tic++;
        if (flash > 0) flash--;
        // Rain: streaks fall across the whole scene, splashes ring on the mud.
        for (int k = 0; k < 2; k++) drops.add(new double[] {h.nextInt(AREA_W + 40) - 20, -6, 6 + h.nextInt(5)});
        drops.removeIf(d -> {
            d[0] -= 1.5;
            d[1] += 7;
            if (d[1] > 20 + h.nextInt(98)) {
                if (h.nextInt(3) == 0) splashes.add(new double[] {d[0], d[1], 0});
                return true;
            }
            return false;
        });
        splashes.removeIf(s -> ++s[2] > 8);
        if (leafState == 2) {
            leafX += leafVx;
            leafY += leafVy;
            leafVy += 0.25;
            leafAngle += 18;
            if (leafX < -30 || leafX > AREA_W + 30 || leafY > AREA_H + 30) leafState = 0;
        }
        if (notes >= 0) {
            if (++notes == 20 || notes == 40 || notes == 60 || notes == 96) Draw.beep(Sounds.PAPER, 1.1f + notes / 100f, 0.6f);
            if (notes >= NOTES) {
                perfect = faults == 0;
                end(true);
            }
            return;
        }
        if (pause > 0) {
            if (--pause == 0) {
                if (++print >= PRINTS) {
                    notes = 0;
                    Draw.beep(Sounds.PAPER, 0.9f, 0.8f);
                } else {
                    newPrint();
                    shownSound();
                }
            }
            return;
        }
        // The rain wears the print away; once gone, the measure is lost.
        wear += WEAR[print];
        if (wear > 0.75 && tic % 10 == 0) Draw.beep(Sounds.TICK, 1.2f + (float) wear, 0.5f);
        if (wear >= 1) {
            washed = true;
            fault();
            pause = NEXT;
        }
    }

    void fault() {
        Draw.note(0.5f);
        if (++faults >= MAX_FAULTS) end(false);
    }

    @Override
    void click(double x, double y) {
        if (finished || pause > 0 || notes >= 0) return;
        if (leafState == 1 && Math.hypot(x - ox - leafX, y - oy - leafY) < 14) {
            leafState = 2;
            leafVx = (x - ox < leafX ? 1 : -1) * (2.5 + h.nextDouble());
            leafVy = -3 - h.nextDouble() * 1.5;
            Draw.beep(Sounds.BRUSH_SOUND, 1.3f, 0.6f);
            return;
        }
        double here = jaw();
        flashOk = Math.abs(here - target()) <= TOLERANCE[print];
        flash = 10;
        if (flashOk) Draw.beep(Sounds.CARABINER, 1.1f + step * 0.1f, 0.7f);
        else fault();
        if (finished) return;
        int dim = step / 2;
        if (step % 2 == 0) {
            fixed = here;
            good[print][dim] = flashOk;
        } else {
            measured[print][dim] = here - fixed;
            good[print][dim] &= flashOk;
        }
        if (++step >= 4) {
            pause = NEXT;
            Draw.note(1.2f + print * 0.2f);
        }
    }

    // Lengths in centimetres: two play-area pixels to the centimetre, with a decimal comma in French.
    static String cm(double px) {
        String lang = Minecraft.getInstance().getLanguageManager().getSelected();
        String s = String.format(java.util.Locale.ROOT, "%.1f", px / 2);
        return (lang.startsWith("fr") ? s.replace('.', ',') : s) + T.f("cm");
    }

    @Override
    void draw(GuiGraphics g, int x, int y, float pt) {
        mx = x;
        my = y;
        int ax = ox, ay = oy;
        if (notes >= 0) {
            Draw.image(g, MUD, ax, ay, AREA_W, AREA_H);
            drawRain(g, ax, ay);
            drawNotebook(g, ax, ay, pt);
            counters(g, ax, ay);
            return;
        }
        drawScene(g, ax, ay);
        // The loupe: the scene again, twice as large, in a round lens under the pointer.
        boolean loupe = pause == 0 && !finished && Draw.in(x, y, ax, ay, AREA_W, AREA_H);
        if (loupe) {
            // Drawn in horizontal strips, each clipped to the lens's chord, so the lens is round.
            for (int r = -LOUPE; r < LOUPE; r += 3) {
                double mid = r + 1.5;
                int half = (int) Math.round(Math.sqrt(Math.max(0, LOUPE * LOUPE - mid * mid)));
                int x0 = Math.max(ax, x - half), x1 = Math.min(ax + AREA_W, x + half), y0 = Math.max(ay, y + r), y1 = Math.min(ay + AREA_H, y + r + 3);
                if (x1 <= x0 || y1 <= y0) continue;
                g.enableScissor(x0, y0, x1, y1);
                g.pose().pushPose();
                g.pose().translate(x, y, 50);
                g.pose().scale(2f, 2f, 1f);
                g.pose().translate(-x, -y, 0);
                drawScene(g, ax, ay);
                g.pose().popPose();
                g.disableScissor();
            }
            g.pose().pushPose();
            g.pose().translate(0, 0, 60);
            Draw.circle(g, x - 0.5, y - 0.5, LOUPE, 2, 0xFFC8A060);
            g.pose().popPose();
        }
        drawRain(g, ax, ay);
        g.pose().pushPose();
        g.pose().translate(0, 0, 70);
        counters(g, ax, ay);
        g.pose().popPose();
    }

    void counters(GuiGraphics g, int ax, int ay) {
        String left = T.f("print") + Math.min(PRINTS, print + 1) + " / " + PRINTS, right = T.f("faults") + faults + " / " + MAX_FAULTS;
        g.fill(ax + 2, ay + 2, ax + 10 + Draw.textFont().width(left), ay + 12, 0xB0101418);
        g.fill(ax + AREA_W - 10 - Draw.textFont().width(right), ay + 2, ax + AREA_W - 2, ay + 12, 0xB0101418);
        text(g, left, ax + 6, ay + 3, Draw.TEXT);
        text(g, right, ax + AREA_W - 6 - Draw.textFont().width(right), ay + 3, faults > 0 ? Draw.ALERT : Draw.GREY);
        String state = "";
        int sc = Draw.YELLOW;
        if (notes >= 0) {
            state = T.f("field_notebook");
            sc = Draw.TEXT;
        } else if (washed && pause > 0) {
            state = T.f("washed_away_by_the_rain");
            sc = Draw.ALERT;
        } else if (pause == 0 && !finished) {
            state = T.f(new String[] {"left_edge", "right_edge", "top_edge", "bottom_edge"}[step]);
        }
        if (!state.isEmpty()) {
            int sw = Draw.textFont().width(state);
            g.fill(ax + AREA_W / 2 - sw / 2 - 4, ay + 2, ax + AREA_W / 2 + sw / 2 + 4, ay + 12, 0xB0101418);
            Draw.centre(g, state, ax + AREA_W / 2, ay + 3, sc);
        }
        // How much of the print the rain has left.
        if (notes < 0 && pause == 0) {
            double part = 1 - wear;
            g.fill(ax + 4, ay + AREA_H - 3, ax + 4 + (int) ((AREA_W - 8) * part), ay + AREA_H - 2, part > 0.25 ? 0xFF8FC8F0 : Draw.ALERT);
        }
    }

    // Mud, the ridge and the print (blurring as the rain wears it), the leaf, the dimension lines and the calliper.
    void drawScene(GuiGraphics g, int ax, int ay) {
        Draw.image(g, MUD, ax, ay, AREA_W, AREA_H);
        int px = ax + left, py = ay + top;
        double fade = Math.min(1, wear);
        // The ridge of pushed-up mud, a few pixels outside the true edge.
        g.setColor(0.78f, 0.62f, 0.45f, (float) (0.55 * (1 - fade * 0.8)));
        Draw.image(g, footprint, px - 4, py - 4, w + 8, hgt + 8);
        g.setColor(0.2f, 0.14f, 0.1f, (float) (0.5 * (1 - fade * 0.8)));
        Draw.image(g, footprint, px - 1, py - 1, w + 2, hgt + 2);
        // The print: sharp at first, then smeared as the rain wears it.
        double spread = fade * 3.5;
        if (spread > 0.3) {
            g.setColor(1f, 1f, 1f, (float) (0.3 * fade));
            for (int k = 0; k < 4; k++) {
                int dx = (int) Math.round(Math.cos(k * Math.PI / 2 + 0.6) * spread), dy = (int) Math.round(Math.sin(k * Math.PI / 2 + 0.6) * spread);
                Draw.image(g, footprint, px + dx, py + dy, w, hgt);
            }
        }
        g.setColor(1f, 1f, 1f, (float) (1 - fade * 0.7));
        Draw.image(g, footprint, px, py, w, hgt);
        g.setColor(1f, 1f, 1f, 1f);
        for (double[] s : splashes) Draw.circle(g, ax + s[0], ay + s[1], 1 + s[2] * 0.4, 1, ((int) (0x90 * (1 - s[2] / 9)) << 24) | 0xC8E0F0);

        // Dimension lines already measured.
        if (step >= 2 || pause > 0 && !washed) dimension(g, ax, ay, true);
        if (step >= 4 && !washed) dimension(g, ax, ay, false);

        if (leafState > 0) {
            g.pose().pushPose();
            g.pose().translate(ax + (float) leafX, ay + (float) leafY, 0);
            g.pose().mulPose(Axis.ZP.rotationDegrees((float) leafAngle));
            g.pose().scale(1.5f, 1.5f, 1f);
            Draw.image(g, LEAF, -8, -14, 16, 28);
            g.pose().popPose();
        }
        if (pause == 0 && !finished) calliper(g, ax, ay);
    }

    // An architect's dimension line: extension ticks, arrows and the value, green when both jaws were right.
    void dimension(GuiGraphics g, int ax, int ay, boolean across) {
        int dim = across ? 0 : 1;
        double len = measured[print][dim];
        if (len <= 0) return;
        int c = good[print][dim] ? 0xFF8CF0A8 : 0xFFFF8A70;
        if (across) {
            int y = ay + top + hgt + 6, x0 = ax + left, x1 = ax + left + (int) Math.round(len);
            g.fill(x0, y - 3, x0 + 1, y + 4, c);
            g.fill(x1, y - 3, x1 + 1, y + 4, c);
            g.fill(x0, y, x1 + 1, y + 1, c);
            g.fill(x0 + 1, y - 1, x0 + 3, y + 2, c);
            g.fill(x1 - 2, y - 1, x1, y + 2, c);
            String v = cm(len);
            int vw = Draw.textFont().width(v);
            g.fill((x0 + x1) / 2 - vw / 2 - 2, y + 3, (x0 + x1) / 2 + vw / 2 + 2, y + 12, 0xC0101418);
            Draw.centre(g, v, (x0 + x1) / 2, y + 4, c);
        } else {
            int x = ax + left + w + 6, y0 = ay + top, y1 = ay + top + (int) Math.round(len);
            g.fill(x - 3, y0, x + 4, y0 + 1, c);
            g.fill(x - 3, y1, x + 4, y1 + 1, c);
            g.fill(x, y0, x + 1, y1 + 1, c);
            g.fill(x - 1, y0 + 1, x + 2, y0 + 3, c);
            g.fill(x - 1, y1 - 2, x + 2, y1, c);
            String v = cm(len);
            int vw = Draw.textFont().width(v), ty = (y0 + y1) / 2 - 4;
            g.fill(x + 4, ty - 1, x + 8 + vw, ty + 9, 0xC0101418);
            g.drawString(Draw.textFont(), v, x + 6, ty, c, false);
        }
    }

    // The calliper: a steel beam with a scale, the fixed jaw and the sliding one with its reading.
    void calliper(GuiGraphics g, int ax, int ay) {
        boolean across = horizontal();
        double f = step % 2 == 0 ? jaw() : fixed, m = step % 2 == 0 ? jaw() + 18 : jaw();
        int steel = 0xFFC8D2DA, dark = 0xFF5E6A76, light = 0xFFEFF4F8;
        if (across) {
            int beam = ay + top - 10, fx = ax + (int) Math.round(f), mxj = ax + (int) Math.round(m), tip = ay + top + hgt + 2;
            g.fill(fx - 8, beam - 2, Math.max(mxj + 26, fx + 70), beam + 3, dark);
            g.fill(fx - 7, beam - 1, Math.max(mxj + 25, fx + 69), beam + 2, steel);
            for (int t = fx; t < Math.max(mxj + 24, fx + 68); t += 2) g.fill(t, beam - 1, t + 1, beam + ((t - fx) % 10 == 0 ? 1 : 0), dark);
            // Fixed jaw: its inner edge is the measuring line.
            g.fill(fx - 4, beam, fx, tip, dark);
            g.fill(fx - 3, beam, fx, tip - 1, steel);
            g.fill(fx - 1, beam, fx, tip - 1, light);
            // Sliding jaw: a block on the beam, its blade down to the print.
            g.fill(mxj, beam - 4, mxj + 12, beam + 5, dark);
            g.fill(mxj + 1, beam - 3, mxj + 11, beam + 4, 0xFF9AA6B2);
            g.fill(mxj, beam, mxj + 4, tip, dark);
            g.fill(mxj, beam, mxj + 3, tip - 1, steel);
            g.fill(mxj, beam, mxj + 1, tip - 1, light);
            if (step % 2 == 1) reading(g, mxj + 14, beam - 4, m - f);
            if (flash > 0) g.fill((step % 2 == 0 ? fx - 1 : mxj), beam, (step % 2 == 0 ? fx : mxj + 1), tip, flashOk ? Draw.GREEN : Draw.ALERT);
        } else {
            int beam = ax + left - 10, fy = ay + (int) Math.round(f), myj = ay + (int) Math.round(m), tip = ax + left + w + 2;
            g.fill(beam - 2, fy - 8, beam + 3, Math.max(myj + 14, fy + 50), dark);
            g.fill(beam - 1, fy - 7, beam + 2, Math.max(myj + 13, fy + 49), steel);
            for (int t = fy; t < Math.max(myj + 12, fy + 48); t += 2) g.fill(beam - 1, t, beam + ((t - fy) % 10 == 0 ? 1 : 0), t + 1, dark);
            g.fill(beam, fy - 4, tip, fy, dark);
            g.fill(beam, fy - 3, tip - 1, fy, steel);
            g.fill(beam, fy - 1, tip - 1, fy, light);
            g.fill(beam - 4, myj, beam + 5, myj + 12, dark);
            g.fill(beam - 3, myj + 1, beam + 4, myj + 11, 0xFF9AA6B2);
            g.fill(beam, myj, tip, myj + 4, dark);
            g.fill(beam, myj, tip - 1, myj + 3, steel);
            g.fill(beam, myj, tip - 1, myj + 1, light);
            if (step % 2 == 1) reading(g, beam - 4 - Draw.textFont().width(cm(m - f)) - 4, myj + 3, m - f);
            if (flash > 0) g.fill(beam, (step % 2 == 0 ? fy - 1 : myj), tip, (step % 2 == 0 ? fy : myj + 1), flashOk ? Draw.GREEN : Draw.ALERT);
        }
    }

    // The live reading of the calliper, on a small dark display.
    void reading(GuiGraphics g, int x, int y, double px) {
        String v = cm(px);
        int vw = Draw.textFont().width(v);
        g.fill(x - 2, y - 1, x + vw + 2, y + 9, 0xE0101418);
        g.drawString(Draw.textFont(), v, x, y, 0xFF8CF0A8, false);
    }

    void drawRain(GuiGraphics g, int ax, int ay) {
        for (double[] d : drops) {
            int x = ax + (int) d[0], y = ay + (int) d[1];
            if (y + d[2] < ay || y > ay + AREA_H || x < ax || x >= ax + AREA_W) continue;
            Draw.segment(g, x, Math.max(ay, y), x - d[2] * 0.2, Math.min(ay + AREA_H - 1, y + d[2]), 1, 0x60C8E0F0);
        }
    }

    // The field notebook: the three prints sketched with their measures, then the average.
    void drawNotebook(GuiGraphics g, int ax, int ay, float pt) {
        double rise = Math.min(1, (notes + pt) / 14.0);
        int nx = ax + AREA_W / 2 - 60, ny = ay + 16 + (int) ((1 - rise) * 100);
        Draw.image(g, NOTEBOOK, nx, ny, 120, 94);
        double sumW = 0, sumL = 0;
        int counted = 0;
        for (int i = 0; i < PRINTS; i++) {
            if (notes < 20 + i * 20) continue;
            int sx = i < 2 ? nx + 12 : nx + 66, sy = i < 2 ? ny + 8 + i * 40 : ny + 8;
            g.setColor(0.23f, 0.16f, 0.11f, 0.85f);
            double f = 22.0 / Math.max(texW, texH);
            int sw = (int) Math.round(texW * f), sh = (int) Math.round(texH * f);
            Draw.image(g, footprint, sx + 15 - sw / 2, sy + 11 - sh / 2, sw, sh);
            g.setColor(1f, 1f, 1f, 1f);
            boolean ok = measured[i][0] > 0 && measured[i][1] > 0;
            String v = ok ? Math.round(measured[i][0] / 2) + "×" + Math.round(measured[i][1] / 2) + T.f("cm") : "—";
            g.drawString(Draw.textFont(), v, sx, sy + 24, good[i][0] && good[i][1] ? Draw.INK : Draw.INK_RED, false);
            if (ok) {
                sumW += measured[i][0];
                sumL += measured[i][1];
                counted++;
            }
        }
        if (notes >= 80 && counted > 0) {
            int sx = nx + 66, sy = ny + 52;
            g.drawString(Draw.textFont(), T.f("average"), sx, sy, Draw.INK_LIGHT, false);
            g.drawString(Draw.textFont(), Math.round(sumW / counted / 2) + "×" + Math.round(sumL / counted / 2) + T.f("cm"), sx, sy + 11, Draw.INK, false);
        }
        // What the prints say of the creature: its height, estimated from their size.
        double m = estimate();
        if (notes >= 96 && m > 0) {
            String lang = Minecraft.getInstance().getLanguageManager().getSelected();
            String v = String.format(java.util.Locale.ROOT, "%.1f", m);
            if (lang.startsWith("fr")) v = v.replace('.', ',');
            g.drawString(Draw.textFont(), "≈ " + v + " m", nx + 66, ny + 76, Draw.INK_RED, false);
        }
    }

    @Override
    String instruction() {
        return T.f("a_print_in_the_mud");
    }

    @Override
    Control[] controls() {
        return new Control[] {new Control(T.f("aim_then_lock_a_jaw"), Draw.MOUSE)};
    }
}
