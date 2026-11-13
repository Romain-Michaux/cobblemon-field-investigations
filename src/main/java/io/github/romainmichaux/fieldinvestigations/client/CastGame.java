package io.github.romainmichaux.fieldinvestigations.client;

import java.util.Random;

import com.mojang.math.Axis;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

// Casting: pour plaster into each hollow of a paw print, right up to the brim. The plaster keeps falling for a moment
// after the click is released, and the stream thickens the longer it runs: pour big, then finish with short dabs.
final class CastGame extends MiniGame {
    static final int N = 5, DURATION = 640, GROUND = 34, SPOUT = 14, MARGIN = 6, SET = 26, LIFT = 40;
    static final double GRAVITY = 0.5, MAX_FLOW = 1.2;
    static final ResourceLocation MUD = Draw.texture("cast_mud"), JUG = Draw.texture("cast_jug");
    // Each hollow (two toes, the pad, two toes): left edge in the area, width, depth, plaster level (px from the bottom).
    final int[] left = new int[N], width = new int[N], depth = new int[N];
    final double[] level = new double[N];
    final boolean[] spoiled = new boolean[N];
    final int hidden;
    final Random h;
    int time = DURATION, faults, flash, tic, hold, setting = -1;
    boolean blocked;
    double cx, spoutX = -1;
    // Plaster in the air, and the droplets of each splash.
    final double[] px = new double[160], py = new double[160], pvy = new double[160], pa = new double[160];
    final boolean[] falling = new boolean[160];
    final double[] sx = new double[40], sy = new double[40], svx = new double[40], svy = new double[40];
    final int[] slife = new int[40];
    final int[] splat = new int[24];
    int splats;

    CastGame(Random h) {
        this.h = h;
        int total = 0;
        for (int i = 0; i < N; i++) {
            boolean pad = i == 2;
            width[i] = pad ? 34 + h.nextInt(3) * 4 : 14 + h.nextInt(3) * 2;
            depth[i] = pad ? 28 + h.nextInt(16) : 34 + h.nextInt(38);
            total += width[i];
        }
        int gap = 20, x = (AREA_W - total - gap * (N - 1)) / 2;
        for (int i = 0; i < N; i++) {
            left[i] = x + (i == 2 ? 0 : h.nextInt(5) - 2);
            x += width[i] + gap;
        }
        // One toe sits under a fold of mud: only its rim shows, so it is filled by ear (the pour rises in pitch).
        int[] toes = {0, 1, 3, 4};
        hidden = toes[h.nextInt(4)];
    }

    int socketAt(double ax) {
        for (int i = 0; i < N; i++) {
            if (ax >= left[i] && ax < left[i] + width[i]) return i;
        }
        return -1;
    }

    boolean full(int i) {
        return !spoiled[i] && level[i] >= depth[i] - MARGIN;
    }

    // Inset of the hollow's wall, k px below the rim: slightly tapered, rounded at the bottom.
    int inset(int i, int k) {
        int r = Math.min(width[i] / 2, 7), d = depth[i];
        double taper = k * 0.06;
        if (k > d - r) {
            double t = (k - (d - r)) / (double) r;
            taper += width[i] / 2.0 * (1 - Math.sqrt(Math.max(0, 1 - t * t)));
        }
        return (int) Math.round(Math.min(width[i] / 2.0 - 1, taper));
    }

    @Override
    void tick() {
        tic++;
        if (flash > 0) flash--;
        for (int k = 0; k < slife.length; k++) {
            if (slife[k] <= 0) continue;
            slife[k]--;
            sx[k] += svx[k];
            sy[k] += svy[k];
            svy[k] += 0.4;
        }
        if (setting >= 0) {
            setting++;
            if (setting == SET) Draw.beep(Sounds.STONE, 0.8f, 0.8f);
            if (setting == SET + 6) Draw.beep(Sounds.SUCCESS, 1.1f, 0.5f);
            if (setting >= SET + LIFT + 10) {
                perfect = faults == 0;
                end(true);
            }
            return;
        }
        if (--time <= 0) {
            end(false);
            return;
        }
        hurryTicks(time, DURATION);
        double ax = cx - ox;
        boolean over = ax >= 0 && ax < AREA_W;
        if (spoutX < 0) spoutX = ax;
        spoutX += (Math.max(4, Math.min(AREA_W - 4, ax)) - spoutX) * 0.6;
        if (mouseDown && !blocked && over) {
            // The stream thickens while the click is held.
            hold++;
            double flow = Math.min(MAX_FLOW, 0.3 + hold * 0.05);
            for (int k = 0; k < falling.length; k++) {
                if (falling[k]) continue;
                falling[k] = true;
                px[k] = spoutX;
                py[k] = SPOUT;
                pvy[k] = 1.2;
                pa[k] = flow;
                break;
            }
            if (tic % 6 == 0) Draw.beep(Sounds.PLASTER, 0.7f, 0.25f);
        } else {
            hold = 0;
        }
        boolean landedSound = false;
        for (int k = 0; k < falling.length; k++) {
            if (!falling[k]) continue;
            py[k] += pvy[k];
            pvy[k] += GRAVITY;
            int i = socketAt(px[k]);
            double surface = i >= 0 ? GROUND + depth[i] - level[i] : GROUND;
            if (py[k] < surface) continue;
            falling[k] = false;
            splash(px[k], surface, pa[k]);
            if (i < 0 || spoiled[i]) {
                if (splats < splat.length) splat[splats++] = (int) px[k];
                continue;
            }
            level[i] += pa[k] * 16 / width[i];
            if (!landedSound && tic % 4 == 0) {
                // The pitch rises as the hollow fills: the only clue for the one under the mud.
                Draw.beep(Sounds.PLASTER, (float) (0.6 + 1.1 * level[i] / depth[i]), 0.5f);
                landedSound = true;
            }
            if (level[i] > depth[i] + 1.5) {
                // Overflow: plaster spills over the rim and the hollow has to be scraped before pouring again.
                spoiled[i] = true;
                flash = 8;
                Draw.beep(Sounds.OVERFLOW, 1f, 0.9f);
                if (++faults >= 3) end(false);
            }
        }
        boolean airborne = false;
        for (boolean f : falling) airborne |= f;
        boolean all = !mouseDown && !airborne;
        for (int k = 0; k < N; k++) all &= full(k);
        if (all && !finished) setting = 0;
    }

    void splash(double x, double y, double amount) {
        int n = amount > 0.8 ? 2 : 1;
        for (int k = 0; k < slife.length && n > 0; k++) {
            if (slife[k] > 0) continue;
            sx[k] = x;
            sy[k] = y - 1;
            svx[k] = (h.nextDouble() - 0.5) * 2.4;
            svy[k] = -1 - h.nextDouble() * 1.5;
            slife[k] = 5 + h.nextInt(4);
            n--;
        }
    }

    @Override
    void click(double mx, double my) {
        int i = socketAt(mx - ox);
        if (i >= 0 && spoiled[i] && setting < 0) {
            // Scrape the spill: the hollow keeps a third of its plaster.
            spoiled[i] = false;
            level[i] = depth[i] * 0.3;
            blocked = true;
            Draw.beep(Sounds.RUB, 1f, 0.8f);
        }
    }

    @Override
    void released(double mx, double my) {
        blocked = false;
    }

    // Fills a rectangle with the ground strip (128 x 168, drawn at half size), from depth v0 texels below the surface.
    static void mud(GuiGraphics g, int x, int y, int l, int hh, int u0, int v0) {
        if (l <= 0 || hh <= 0) return;
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.5f, 0.5f, 1f);
        for (int ty = 0; ty < hh * 2; ) {
            int v = Math.min(167, v0 + ty), ph = Math.max(1, Math.min(168 - v, hh * 2 - ty));
            for (int tx = 0; tx < l * 2; ) {
                int u = Math.floorMod(u0 + x * 2 + tx, 128), pl = Math.min(128 - u, l * 2 - tx);
                Draw.piece(g, MUD, tx, ty, u, v, pl, ph, 128, 168);
                tx += pl;
            }
            ty += ph;
        }
        g.pose().popPose();
    }

    int plaster(double setPart) {
        return mix(0xFFCFD3D2, 0xFFF4F0E6, setPart);
    }

    @Override
    void draw(GuiGraphics g, int mx, int my, float pt) {
        cx = mx;
        int ax = ox, ay = oy, ground = ay + GROUND;
        double setPart = setting < 0 ? 0 : Math.min(1, setting / (double) SET);
        int lift = setting < SET ? 0 : (int) Math.round(30 * (1 - Math.pow(1 - Math.min(1, (setting - SET) / (double) LIFT), 3)));
        g.fill(ax, ay, ax + AREA_W, ay + AREA_H, 0xFF18202A);
        g.fill(ax, ground - 10, ax + AREA_W, ground, 0xFF1D2733);
        g.setColor(0.82f, 0.72f, 0.64f, 1f);
        mud(g, ax, ground - 2, AREA_W, AREA_H - GROUND + 2, 0, 0);
        g.setColor(1f, 1f, 1f, 1f);
        g.fill(ax, ground, ax + AREA_W, ground + 2, 0xFF6E4E38);
        g.fill(ax, ground, ax + AREA_W, ground + 1, 0xFF8E6A4C);
        for (int s = 0; s < splats; s++) g.fill(ax + splat[s] - 1, ground - 1, ax + splat[s] + 2, ground, 0xFFDAD6CC);

        // The hollows, then the plaster in them (lifted out of the ground at the end).
        for (int i = 0; i < N; i++) {
            int x = ax + left[i];
            for (int k = 0; k < depth[i]; k++) {
                int r = inset(i, k);
                // A dark outline carved into the mud, then the hollow, its left wall catching a little light.
                g.fill(x + r - 1, ground + k, x + width[i] - r + 1, ground + k + 2, 0xFF120A06);
                g.fill(x + r, ground + k, x + width[i] - r, ground + k + 1, 0xFF22160F);
                g.fill(x + r, ground + k, x + r + 1, ground + k + 1, 0xFF3E2C1F);
            }
        }
        if (lift > 0) {
            // The cast comes out in one piece: a slab joins the hollows on the surface.
            int l0 = ax + left[0] - 4, l1 = ax + left[N - 1] + width[N - 1] + 4;
            g.fill(l0, ground - 4 - lift, l1, ground - lift, plaster(1));
            g.fill(l0, ground - 4 - lift, l1, ground - 3 - lift, 0xFFFFFFFF);
        }
        for (int i = 0; i < N; i++) {
            int x = ax + left[i], filled = (int) Math.min(depth[i] + 1, level[i]);
            int colour = spoiled[i] ? 0xFFA8A49C : plaster(setPart);
            for (int k = depth[i] - filled; k < depth[i]; k++) {
                int r = inset(i, Math.max(0, k)), y = ground + k - lift;
                g.fill(x + r, y, x + width[i] - r, y + 1, colour);
                g.fill(x + r, y, x + r + 1, y + 1, 0x50FFFFFF);
                g.fill(x + width[i] - r - 2, y, x + width[i] - r, y + 1, 0x28000000);
            }
            if (filled > 0) {
                int top = ground + depth[i] - filled - lift, wobble = setting < 0 && tic % 6 < 3 ? 1 : 0, r = inset(i, Math.max(0, depth[i] - filled));
                g.fill(x + r + wobble, top, x + width[i] - r - 1 + wobble, top + 1, spoiled[i] ? 0xFFC8C4BC : 0xFFFFFFFF);
            }
            if (spoiled[i]) {
                // The spill: a puddle on the rim and drips down the mud on both sides.
                g.fill(x - 6, ground - 2, x + width[i] + 6, ground + 1, 0xFFB8B4AC);
                g.fill(x - 6, ground + 1, x - 4, ground + 7, 0xFFB8B4AC);
                g.fill(x + width[i] + 3, ground + 1, x + width[i] + 5, ground + 10, 0xFFB8B4AC);
                Draw.centre(g, T.f("scrape"), x + width[i] / 2, ground - 13, tic % 20 < 14 ? Draw.ALERT : Draw.YELLOW);
            }
            if (i == hidden && lift == 0) {
                // A fold of wet mud hides the toe below its rim.
                int top = ground + 8;
                g.setColor(0.74f, 0.65f, 0.58f, 1f);
                mud(g, x - 3, top + 2, width[i] + 6, depth[i] - 6, 17, 40);
                g.setColor(1f, 1f, 1f, 1f);
                for (int c = -3; c < width[i] + 3; c++) {
                    int bump = (int) Math.round(1.5 * Math.sin((c + i * 7) * 0.7));
                    g.fill(x + c, top + bump, x + c + 1, top + 2, 0xFF5A3E2C);
                    g.fill(x + c, top + bump, x + c + 1, top + bump + 1, 0xFF7E5E44);
                }
            }
            if (lift == 0) {
                // Fill marks: the last six pixels under the rim, on both walls; a tick once the hollow is full.
                int mark = full(i) ? Draw.GREEN : 0xFF86C27A;
                g.fill(x - 1, ground, x + 1, ground + MARGIN, mark);
                g.fill(x + width[i] - 1, ground, x + width[i] + 1, ground + MARGIN, mark);
                if (full(i) && setting < 0) text(g, "✔", x + width[i] / 2 - 3, ground - 11, Draw.GREEN);
            }
        }

        // Plaster in the air: a stream that gets thicker as the flow grows, then splashes.
        for (int k = 0; k < falling.length; k++) {
            if (!falling[k]) continue;
            int w = pa[k] > 0.9 ? 3 : pa[k] > 0.55 ? 2 : 1, x = ax + (int) Math.round(px[k]) - w / 2;
            g.fill(x, ay + (int) (py[k] - pvy[k]) - 1, x + w, ay + (int) py[k] + 1, 0xFFE9E6DE);
        }
        for (int k = 0; k < slife.length; k++) {
            if (slife[k] > 0) g.fill(ax + (int) sx[k], ay + (int) sy[k], ax + (int) sx[k] + 1, ay + (int) sy[k] + 1, 0xFFF4F0E6);
        }

        // The jug follows the pointer and tips its spout down while pouring.
        if (!finished && setting < 0 && spoutX >= 0) {
            boolean pouring = mouseDown && !blocked;
            g.pose().pushPose();
            g.pose().translate(ax + (float) spoutX, ay + SPOUT, 0);
            g.pose().mulPose(Axis.ZP.rotationDegrees(pouring ? -32 : -8));
            g.pose().scale(0.5f, 0.5f, 1f);
            Draw.image(g, JUG, -JUG_X, -JUG_Y, 32, 32);
            g.pose().popPose();
        }

        int filled = 0;
        for (int i = 0; i < N; i++) filled += full(i) ? 1 : 0;
        text(g, T.f("hollows_filled") + filled + " / " + N, ax + 6, ay + 4, filled > 0 ? Draw.GREEN : Draw.TEXT);
        String spilled = T.f("spilled") + faults + " / 3";
        text(g, spilled, ax + AREA_W - 6 - Draw.textFont().width(spilled), ay + 4, faults > 0 ? Draw.ALERT : Draw.GREY);
        double part = setting >= 0 ? 1 : time / (double) DURATION;
        g.fill(ax + 6, ay + AREA_H - 5, ax + AREA_W - 6, ay + AREA_H - 2, Draw.EDGE);
        g.fill(ax + 7, ay + AREA_H - 4, ax + 7 + (int) ((AREA_W - 14) * Math.max(0, Math.min(1, part))), ay + AREA_H - 3,
                part > 0.25 ? Draw.ACCENT : Draw.ALERT);
        if (flash > 0) {
            g.fill(ax, ay, ax + AREA_W, ay + 1, Draw.ALERT);
            g.fill(ax, ay + AREA_H - 1, ax + AREA_W, ay + AREA_H, Draw.ALERT);
            g.fill(ax, ay, ax + 1, ay + AREA_H, Draw.ALERT);
            g.fill(ax + AREA_W - 1, ay, ax + AREA_W, ay + AREA_H, Draw.ALERT);
        }
    }

    // The jug sprite's spout, in texels: the point that sits over the pour.
    static final int JUG_X = 6, JUG_Y = 8;

    @Override
    String instruction() {
        return T.f("the_mud_remembers_a_passing");
    }

    @Override
    Control[] controls() {
        return new Control[] {new Control(T.f("pour_hold"), Draw.MOUSE), new Control(T.f("scrape_a_spill"), Draw.MOUSE)};
    }
}
