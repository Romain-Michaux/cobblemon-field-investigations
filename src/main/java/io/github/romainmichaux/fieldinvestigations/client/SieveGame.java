package io.github.romainmichaux.fieldinvestigations.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

// Sieving (buried clue): the sieve hangs at a dig site and follows the mouse from side to side. Swinging it sifts the
// sand through the mesh onto a heap below; what stays in it slides with the swings, and a brusque one sends it over the
// rim, so the clue's fragments are watched as they slide. Things in the sieve push one another apart rather than pile up.
// The fragments, hidden by the sand until it thins, are picked with a click, not the pebbles; one is inside a clod, which
// a click crumbles. They fit back together into the clue, which goes into a sample bag.
final class SieveGame extends MiniGame {
    static final ResourceLocation SITE = Draw.texture("sieve_site"), TRAY = Draw.texture("sieve_tray"), CLOD = Draw.texture("sieve_clod"),
            PEBBLE = Draw.texture("sieve_pebble");
    static final int DURATION = 1100, MAX_FAULTS = 3, CY = 52, RX = 60, RY = 17, FLOOR = 113, GRAINS = 170, END = 100, PIECE = 18;
    static final double SCALE = 1.5, MIN_X = 76, MAX_X = 204;
    static final int FRAGMENT = 0, CLOD_ITEM = 1, PEBBLE_ITEM = 2;
    static final int IN = 0, FALLING = 1, GROUND = 2, PICKED = 3, GONE = 4;
    final ResourceLocation clue;
    final Random h;
    final List<Item> items = new ArrayList<>();
    final double[] gu = new double[GRAINS], gv = new double[GRAINS];
    final int[] heap = new int[AREA_W];
    final List<double[]> falling = new ArrayList<>(); // x, y, vy
    final List<double[]> dust = new ArrayList<>(); // x, y, vx, vy, age
    double sand = 100, s = 140, sv, activity, mx = Double.NaN, my;
    int time = DURATION, faults, flash, tic, ending = -1, messageTicks, messageColour, picked;
    String message = "";

    static final class Item {
        int type, piece; // piece: which third of the clue (fragment), or the one inside (clod), or -1
        double u, v, vu, x, y, vy;
        int state = IN;

        Item(int type, int piece, double u, double v) {
            this.type = type;
            this.piece = piece;
            this.u = u;
            this.v = v;
        }

        double weight() {
            return type == CLOD_ITEM ? 0.6 : type == PEBBLE_ITEM ? 0.85 : 1;
        }
    }

    SieveGame(Random h, String kind, int variant) {
        this.h = h;
        clue = Draw.buriedIcon(kind, variant);
        for (int i = 0; i < GRAINS; i++) {
            double a = h.nextDouble() * Math.PI * 2, r = Math.sqrt(h.nextDouble()) * 0.92;
            gu[i] = Math.cos(a) * r;
            gv[i] = Math.sin(a) * r;
        }
        // Two fragments loose, the third inside one of the three clods; four pebbles.
        int hidden = h.nextInt(3), inClod = h.nextInt(3);
        List<double[]> spots = new ArrayList<>();
        for (int i = 0; i < 3; i++) if (i != hidden) items.add(new Item(FRAGMENT, i, 0, 0));
        for (int i = 0; i < 3; i++) items.add(new Item(CLOD_ITEM, i == inClod ? hidden : -1, 0, 0));
        for (int i = 0; i < 4; i++) items.add(new Item(PEBBLE_ITEM, -1, 0, 0));
        for (Item it : items) {
            double u, v;
            int tries = 0;
            do {
                u = -0.6 + h.nextDouble() * 1.2;
                v = -0.55 + h.nextDouble() * 1.1;
            } while (tries++ < 40 && crowded(spots, u, v));
            spots.add(new double[] {u, v});
            it.u = u;
            it.v = v;
        }
    }

    static boolean crowded(List<double[]> spots, double u, double v) {
        for (double[] p : spots) if (Math.abs(p[0] - u) < 0.28 && Math.abs(p[1] - v) < 0.5) return true;
        return false;
    }

    boolean sorting() {
        if (sand > 0) return false;
        for (Item it : items) if (it.type == CLOD_ITEM && it.state == IN) return false;
        return true;
    }

    void say(String text, int colour) {
        message = text;
        messageColour = colour;
        messageTicks = 45;
    }

    void fault(String why) {
        flash = 10;
        say(why, Draw.ALERT);
        Draw.note(0.5f);
        if (++faults >= MAX_FAULTS) end(false);
    }

    double itemX(Item it) {
        return s + it.u * RX;
    }

    double itemY(Item it) {
        return CY + it.v * RY;
    }

    @Override
    void tick() {
        tic++;
        if (flash > 0) flash--;
        if (messageTicks > 0) messageTicks--;
        for (double[] d : dust) {
            d[0] += d[2];
            d[1] += d[3];
            d[4]++;
        }
        dust.removeIf(d -> d[4] > 24);
        // Sand falling onto the heap.
        for (double[] p : falling) {
            p[2] += 0.3;
            p[1] += p[2];
            if (p[1] >= FLOOR - heap[(int) p[0]]) {
                for (int dx = -1; dx <= 1; dx++) {
                    int x = (int) p[0] + dx;
                    if (x >= 0 && x < AREA_W && heap[x] < 22) heap[x] += dx == 0 ? 1 : 0;
                }
                p[1] = 999;
            }
        }
        falling.removeIf(p -> p[1] >= 999);
        // Things falling over the rim.
        for (Item it : items) {
            if (it.state != FALLING) continue;
            it.vy += 0.35;
            it.y += it.vy;
            if (it.y >= FLOOR - 4) {
                it.y = FLOOR - 4;
                it.state = GROUND;
                Draw.beep(Sounds.STONE, 1.3f, 0.4f);
            }
        }
        if (ending >= 0) {
            if (ending == 30) Draw.beep(Sounds.CHIME, 1.4f, 0.6f);
            if (ending == 60) Draw.beep(Sounds.PAPER, 1f, 0.8f);
            if (++ending >= END) {
                perfect = faults == 0;
                end(true);
            }
            return;
        }
        if (finished) return;
        if (--time <= 0) {
            end(false);
            return;
        }
        hurryTicks(time, DURATION);
        // The sieve follows the mouse, a little behind; while sorting it rests.
        double prev = sv;
        if (!sorting() && !Double.isNaN(mx)) {
            double target = Math.max(MIN_X, Math.min(MAX_X, mx - ox));
            double ns = s + (target - s) * 0.3;
            sv = ns - s;
            s = ns;
        } else {
            sv = 0;
        }
        double accel = sv - prev;
        activity = activity * 0.85 + Math.abs(sv) * 0.15;
        // Sand goes through while the sieve swings; no faster when swung hard.
        if (sand > 0 && activity > 0.3) {
            double rate = Math.min(activity, 4) * 0.1;
            sand = Math.max(0, sand - rate);
            int n = (int) (rate * 6 + h.nextDouble());
            for (int i = 0; i < n; i++) falling.add(new double[] {s + (h.nextDouble() * 2 - 1) * RX * 0.8, CY + RY * 0.6 + 6, 0.5});
            if (tic % 4 == 0) Draw.beep(Sounds.SIEVE, (float) (0.8 + activity / 10), 0.4f);
        }
        // What stays in the sieve lags behind its swings; sand slows it.
        double friction = 0.80 + 0.12 * (1 - sand / 100);
        for (Item it : items) {
            if (it.state != IN) continue;
            it.vu += -accel / RX * 0.8 * it.weight();
            it.vu *= friction;
            it.u += it.vu;
            double limit = Math.sqrt(Math.max(0, 1 - it.v * it.v));
            if (Math.abs(it.u) > limit) {
                // Over the rim.
                it.state = FALLING;
                it.x = itemX(it);
                it.y = itemY(it);
                it.vy = -1;
                if (it.type == FRAGMENT || it.type == CLOD_ITEM && it.piece >= 0) fault(T.f("a_fragment_fell_out"));
                else Draw.beep(Sounds.BRANCH, 1.4f, 0.4f);
                checkDone();
            }
        }
        // Things don't pile on one another: overlapping ones push apart.
        for (Item a : items) {
            for (Item b : items) {
                if (a == b || a.state != IN || b.state != IN) continue;
                double dx = (b.u - a.u) * RX, dy = (b.v - a.v) * RY, d = Math.hypot(dx, dy);
                double min = (a.type == CLOD_ITEM ? 9 : 6) + (b.type == CLOD_ITEM ? 9 : 6);
                if (d >= min) continue;
                if (d < 0.01) {
                    dx = 1;
                    d = 1;
                }
                double push = (min - d) / 2 / d;
                a.u -= dx * push / RX;
                b.u += dx * push / RX;
                a.v = Math.max(-0.8, Math.min(0.8, a.v - dy * push / RY));
                b.v = Math.max(-0.8, Math.min(0.8, b.v + dy * push / RY));
            }
        }
        if (sand < 40 && tic % 60 == 0 && messageTicks == 0) say(T.f("pick_out_the_fragments"), Draw.YELLOW);
    }

    // Done once no fragment is left to pick (in the sieve or still inside a clod).
    void checkDone() {
        if (ending >= 0 || finished) return;
        for (Item o : items) if (o.state == IN && (o.type == FRAGMENT || o.type == CLOD_ITEM && o.piece >= 0)) return;
        if (picked > 0) ending = 0;
        else end(false);
    }

    Item itemAt(double px, double py) {
        for (int i = items.size() - 1; i >= 0; i--) {
            Item it = items.get(i);
            if (it.state != IN) continue;
            double r = it.type == CLOD_ITEM ? 9 : 7;
            if (Math.abs(px - ox - itemX(it)) < r && Math.abs(py - oy - itemY(it)) < r) return it;
        }
        return null;
    }

    @Override
    void click(double px, double py) {
        if (finished || ending >= 0) return;
        Item it = itemAt(px, py);
        if (it == null) return;
        if (it.type == CLOD_ITEM) {
            // Crumbled: its earth joins the sand, and what it held drops into the sieve.
            it.state = GONE;
            sand = Math.min(100, sand + 5);
            for (int i = 0; i < 8; i++) dust.add(new double[] {itemX(it), itemY(it), h.nextGaussian() * 0.8, -0.5 - h.nextDouble(), 0});
            Draw.beep(Sounds.CRACK, 0.8f, 0.6f);
            if (it.piece >= 0) {
                Item f = new Item(FRAGMENT, it.piece, it.u, it.v);
                items.add(f);
                say(T.f("a_fragment_in_the_clod"), Draw.GREEN);
                Draw.note(1.3f);
            }
            return;
        }
        if (it.type == FRAGMENT) {
            it.state = PICKED;
            picked++;
            Draw.beep(Sounds.CHIME, 1.2f + picked * 0.15f, 0.5f);
            say(T.f("fragment_picked"), Draw.GREEN);
            checkDone();
        } else {
            it.state = FALLING;
            it.x = itemX(it);
            it.y = itemY(it);
            it.vy = -2;
            fault(T.f("just_a_pebble"));
        }
    }

    @Override
    void draw(GuiGraphics g, int px, int py, float pt) {
        mx = px;
        my = py;
        int ax = ox, ay = oy;
        Draw.image(g, SITE, ax, ay, AREA_W, AREA_H);
        // The heap of sifted sand.
        for (int x = 0; x < AREA_W; x++) {
            if (heap[x] <= 0) continue;
            g.fill(ax + x, ay + FLOOR - heap[x], ax + x + 1, ay + FLOOR, x % 3 == 0 ? 0xFFC89A58 : 0xFFD9B36A);
            g.fill(ax + x, ay + FLOOR - heap[x], ax + x + 1, ay + FLOOR - heap[x] + 1, 0xFFEACB88);
        }
        for (Item it : items) if (it.state == GROUND || it.state == FALLING) drawItem(g, it, ax + (int) it.x, ay + (int) it.y);
        for (double[] p : falling) g.fill(ax + (int) p[0], ay + (int) p[1], ax + (int) p[0] + 1, ay + (int) p[1] + 2, 0xFFD9B36A);
        if (ending >= 0) {
            drawSieve(g, ax, ay);
            drawFinale(g, ax, ay);
        } else {
            drawSieve(g, ax, ay);
        }
        g.pose().pushPose();
        g.pose().translate(0, 0, 50);
        for (double[] d : dust) {
            int a = (int) (200 * (1 - d[4] / 24));
            g.fill(ax + (int) d[0], ay + (int) d[1], ax + (int) d[0] + 2, ay + (int) d[1] + 2, (a << 24) | 0x6A4A30);
        }
        if (flash > 0) {
            g.fill(ax, ay, ax + AREA_W, ay + 1, Draw.ALERT);
            g.fill(ax, ay + AREA_H - 1, ax + AREA_W, ay + AREA_H, Draw.ALERT);
        }
        // Counters and the last message.
        String right = T.f("lost") + faults + " / " + MAX_FAULTS;
        int rw = Draw.textFont().width(right);
        g.fill(ax + AREA_W - 10 - rw, ay + 2, ax + AREA_W - 2, ay + 12, 0xC0101418);
        g.drawString(Draw.textFont(), right, ax + AREA_W - 6 - rw, ay + 3, faults > 0 ? Draw.ALERT : Draw.GREY, false);
        String left = T.f("sand") + (int) Math.ceil(sand) + " %";
        g.fill(ax + 2, ay + 2, ax + 10 + Draw.textFont().width(left), ay + 12, 0xC0101418);
        g.drawString(Draw.textFont(), left, ax + 6, ay + 3, Draw.TEXT, false);
        g.fill(ax + 2, ay + 13, ax + 66, ay + 21, 0xC0101418);
        timeGauge(g, ax + 5, ay + 14, 58, time / (double) DURATION);
        if (messageTicks > 0) {
            int w = Draw.textFont().width(message);
            g.fill(ax + AREA_W / 2 - w / 2 - 4, ay + 2, ax + AREA_W / 2 + w / 2 + 4, ay + 12, 0xC0101418);
            Draw.centre(g, message, ax + AREA_W / 2, ay + 3, messageColour);
        }
        g.pose().popPose();
    }

    // The sieve with its sand and what is left in it: fragments and pebbles under the sand, clods on top.
    void drawSieve(GuiGraphics g, int ax, int ay) {
        int w = (int) Math.round(106 * SCALE), hgt = (int) Math.round(50 * SCALE);
        int x0 = ax + (int) Math.round(s - 53 * SCALE), y0 = ay + CY - (int) Math.round(23 * SCALE);
        Draw.image(g, TRAY, x0, y0, w, hgt);
        // The bed of sand over the mesh, thinning as it goes through.
        double bed = Math.min(1, sand / 35);
        if (bed > 0) {
            int a = (int) (bed * 225) << 24;
            for (int dy = -RY; dy <= RY; dy++) {
                int half = (int) Math.round(RX * 0.95 * Math.sqrt(Math.max(0, 1 - (dy / (double) RY) * (dy / (double) RY))));
                g.fill(ax + (int) Math.round(s) - half, ay + CY + dy, ax + (int) Math.round(s) + half, ay + CY + dy + 1, a | 0xC89A58);
            }
        }
        for (Item it : items) if (it.state == IN && it.type != CLOD_ITEM) drawItem(g, it, ax + (int) Math.round(itemX(it)), ay + (int) Math.round(itemY(it)));
        int n = (int) (sand / 100 * GRAINS);
        for (int i = 0; i < n; i++) {
            int x = ax + (int) Math.round(s + gu[i] * RX), y = ay + (int) Math.round(CY + gv[i] * RY);
            g.fill(x, y, x + 3, y + 2, i % 3 == 0 ? 0xFFC89A58 : 0xFFD9B36A);
        }
        for (Item it : items) if (it.state == IN && it.type == CLOD_ITEM) drawItem(g, it, ax + (int) Math.round(itemX(it)), ay + (int) Math.round(itemY(it)));
        // While sorting, the thing under the pointer is outlined.
        Item hover = finished || ending >= 0 ? null : itemAt(mx, my);
        if (hover != null) {
            int hx = ax + (int) Math.round(itemX(hover)), hy = ay + (int) Math.round(itemY(hover));
            int r = hover.type == CLOD_ITEM ? 10 : 8;
            g.fill(hx - r, hy + r, hx + r, hy + r + 1, Draw.YELLOW);
        }
    }

    void drawItem(GuiGraphics g, Item it, int x, int y) {
        if (it.type == CLOD_ITEM) Draw.image(g, CLOD, x - 8, y - 7, 17, 13);
        else if (it.type == PEBBLE_ITEM) Draw.image(g, PEBBLE, x - 5, y - 5, 11, 10);
        else piece(g, it.piece, x, y, PIECE);
    }

    // One third of the clue, cut out of its icon.
    void piece(GuiGraphics g, int i, int x, int y, int size) {
        int third = size / 3;
        g.enableScissor(x - third / 2 - 1, y - size / 2, x - third / 2 + third + 1, y + size / 2);
        Draw.image(g, clue, x - third / 2 - i * third, y - size / 2, size, size);
        g.disableScissor();
    }

    // The end: the fragments fit back together into the clue, which slips into a labelled sample bag.
    void drawFinale(GuiGraphics g, int ax, int ay) {
        double t = Math.min(1, ending / 30.0), e = t * t * (3 - 2 * t);
        int size = (int) Math.round(PIECE + (40 - PIECE) * e);
        int cx = ax + 140, cy = ay + 52;
        g.pose().pushPose();
        g.pose().translate(0, 0, 40);
        if (ending > 40) {
            // The bag, slid under the clue.
            double b = Math.min(1, (ending - 40) / 15.0);
            int by = cy - 32 + (int) ((1 - b) * 40);
            g.fill(cx - 27, by - 1, cx + 27, by + 61, 0x60000000);
            g.fill(cx - 26, by, cx + 26, by + 60, 0x70E8F0F8);
            g.fill(cx - 26, by, cx + 26, by + 4, 0xC0C8D8E8);
            g.fill(cx - 26, by + 5, cx + 26, by + 6, 0xA0D84848);
            g.fill(cx - 20, by + 46, cx + 20, by + 56, 0xFFF4F0E6);
            for (int i = 0; i < 3; i++) g.fill(cx - 17, by + 48 + i * 3, cx + 17 - i * 8, by + 49 + i * 3, 0xFF9AA4B0);
        }
        int n = 0;
        for (Item it : items) {
            if (it.state != PICKED) continue;
            int third = size / 3;
            int tx = cx + (it.piece - 1) * third, ty = cy - 4;
            double sx = ax + 140 + (n - 1) * 30, sy = ay + 40;
            int x = (int) Math.round(sx + (tx - sx) * e), y = (int) Math.round(sy + (ty - sy) * e);
            piece(g, it.piece, x, y, size);
            n++;
        }
        if (ending > 60) {
            String s2 = T.f("clue_bagged");
            int w = Draw.textFont().width(s2);
            g.fill(cx - w / 2 - 4, ay + 102, cx + w / 2 + 4, ay + 112, 0xC0101418);
            g.drawString(Draw.textFont(), s2, cx - w / 2, ay + 103, Draw.GREEN, false);
        }
        g.pose().popPose();
    }

    @Override
    String instruction() {
        return T.f("swing_the_sieve_with_the");
    }

    @Override
    Control[] controls() {
        return new Control[] {new Control(T.f("move_swing_the_sieve"), Draw.MOUSE), new Control(T.f("click_crumble_pick"), Draw.MOUSE)};
    }
}
