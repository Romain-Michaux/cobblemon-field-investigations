package io.github.romainmichaux.fieldinvestigations.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphics;

// Strand pairing: a half ladder of DNA slides through a lit window where the strand is zipped up. The partner bases are
// typed ahead and wait, ghosted, under the bases they will pair with; each one is laid as its base enters the window
// (the strand waits when nothing is typed). The strand repeats a short motif, and now and then a base is damaged: its partner is guessed from the
// motif. A wrong base breaks a rung: it is mended, but the strand stalls. Once zipped, the strand coils into a double
// helix that the sequencer's light reads.
final class PairingGame extends MiniGame {
    static final int LENGTH = 24, SP = 14, ZX = 64, TOP = 50, QUEUE = 6, MAX_ERRORS = 4, DURATION = 900, STALL = 22, HELIX = 90;
    final Random h;
    final int[] strand = new int[LENGTH];
    final boolean[] damaged = new boolean[LENGTH];
    final boolean[] broken = new boolean[LENGTH];
    final List<Integer> queue = new ArrayList<>();
    final int[] keys = new int[4];
    int next, errors, time = DURATION, tic, stall, flash, finale = -1;
    double offset;

    PairingGame(Random h) {
        this.h = h;
        // A motif of three to five bases, repeated, with a few one-off variations.
        int[] motif = new int[3 + h.nextInt(3)];
        for (int k = 0; k < motif.length; k++) motif[k] = h.nextInt(4);
        for (int k = 0; k < LENGTH; k++) {
            boolean variation = k > motif.length && h.nextInt(7) == 0;
            strand[k] = variation ? h.nextInt(4) : motif[k % motif.length];
            // Damaged bases sit where the motif holds, after the motif has been seen once.
            damaged[k] = !variation && k >= motif.length * 2 && h.nextInt(6) == 0;
        }
        for (int b = 0; b < 4; b++) keys[b] = keyFor(Draw.BASES[b]);
    }

    // The key that types this letter on the player's keyboard (Q for A on AZERTY becomes A, and so on).
    static int keyFor(String letter) {
        for (int k = InputConstants.KEY_A; k <= InputConstants.KEY_Z; k++) {
            if (Draw.keyLabel(k).equalsIgnoreCase(letter)) return k;
        }
        return InputConstants.KEY_A + (letter.charAt(0) - 'A');
    }

    static int complement(int b) {
        return b ^ 1;
    }

    double baseX(int k) {
        return ZX + k * SP - offset;
    }

    double speed() {
        return 0.55 + 0.6 * next / (double) LENGTH;
    }

    @Override
    void tick() {
        tic++;
        if (flash > 0) flash--;
        if (finale >= 0) {
            if (++finale == 30) Draw.beep(Sounds.SCAN, 1.2f, 0.7f);
            if (finale >= HELIX) {
                perfect = errors == 0;
                end(true);
            }
            return;
        }
        if (--time <= 0) {
            end(false);
            return;
        }
        hurryTicks(time, DURATION);
        if (stall > 0) {
            stall--;
            return;
        }
        // The strand slides until its next base reaches the window, where the first typed base is laid (or it waits).
        double target = next * SP;
        offset = Math.min(target, offset + speed());
        if (offset >= target && !queue.isEmpty()) {
            int typed = queue.remove(0);
            if (typed == complement(strand[next])) {
                Draw.beep(Sounds.CARABINER, 1.1f + next * 0.03f, 0.6f);
            } else {
                broken[next] = true;
                errors++;
                flash = 12;
                stall = STALL;
                Draw.note(0.5f);
                if (errors >= MAX_ERRORS) {
                    end(false);
                    return;
                }
            }
            if (++next >= LENGTH) {
                finale = 0;
                Draw.beep(Sounds.IDENTIFIED, 1f, 0.8f);
            }
        }
    }

    @Override
    boolean keyPressed(int key) {
        if (finished || finale >= 0) return false;
        if (key == InputConstants.KEY_BACKSPACE) {
            if (!queue.isEmpty()) {
                queue.remove(queue.size() - 1);
                Draw.click(0.7f);
            }
            return true;
        }
        String label = Draw.keyLabel(key);
        for (int b = 0; b < 4; b++) {
            if (!label.equalsIgnoreCase(Draw.BASES[b])) continue;
            if (queue.size() < QUEUE && next + queue.size() < LENGTH) {
                queue.add(b);
                Draw.click(1.2f + b * 0.1f);
            }
            return true;
        }
        return false;
    }

    @Override
    void draw(GuiGraphics g, int mx, int my, float pt) {
        int ax = ox, ay = oy;
        g.fill(ax, ay, ax + AREA_W, ay + AREA_H, 0xFF0E151C);
        // Faint grid of the sequencer's reading glass.
        for (int x = 0; x < AREA_W; x += 20) g.fill(ax + x, ay + 16, ax + x + 1, ay + AREA_H - 4, 0xFF141E28);
        if (finale >= 0) {
            drawFinale(g, ax, ay, pt);
        } else {
            drawLadder(g, ax, ay, pt);
        }
        String left = T.f("paired") + next + " / " + LENGTH, right = T.f("mistakes") + errors + " / " + MAX_ERRORS;
        text(g, left, ax + 6, ay + 2, Draw.TEXT);
        text(g, right, ax + AREA_W - 6 - Draw.textFont().width(right), ay + 2, errors > 0 ? Draw.ALERT : Draw.GREY);
        double part = finale >= 0 ? 0 : time / (double) DURATION;
        g.fill(ax + 6, ay + 12, ax + 6 + (int) ((AREA_W - 12) * part), ay + 13, part > 0.25 ? Draw.ACCENT : Draw.ALERT);
        if (finale < 0 && queue.isEmpty() && offset >= next * SP && next < LENGTH) {
            Draw.centre(g, T.f("type_the_next_bases"), ax + AREA_W / 2, ay + 30, (tic / 10) % 2 == 0 ? Draw.YELLOW : Draw.GREY);
        }
    }

    // A damaged base: a grey, cracked tile with a question mark.
    static void damagedTile(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 12, y + 12, 0xFF3A4450);
        g.fill(x + 1, y + 1, x + 11, y + 11, 0xFF6E7A86);
        g.fill(x + 2, y + 3, x + 4, y + 4, 0xFF4A5560);
        g.fill(x + 8, y + 8, x + 10, y + 9, 0xFF4A5560);
        g.drawString(Draw.textFont(), "?", x + 4, y + 3, 0xFFE8EEF2, false);
    }

    void drawLadder(GuiGraphics g, int ax, int ay, float pt) {
        double smooth = stall > 0 ? 0 : Math.min(next * SP - offset, speed() * pt);
        int shake = stall > 0 ? (int) Math.round(Math.sin(stall * 1.9) * 1.5) : 0;
        int top = ay + TOP, low = top + 18;
        // Backbones: the template's all along, the new strand's up to the window.
        g.fill(ax, top - 4, ax + AREA_W, top - 1, 0xFF4E5E6E);
        g.fill(ax, top - 4, ax + AREA_W, top - 3, 0xFFC9D3DD);
        int zx = ax + ZX + shake;
        g.fill(ax, low + 13, zx + 6, low + 16, 0xFF4E5E6E);
        g.fill(ax, low + 15, zx + 6, low + 16, 0xFFC9D3DD);
        for (int k = 0; k < LENGTH; k++) {
            int x = ax + (int) Math.round(baseX(k) - smooth);
            if (x < ax - 14 || x > ax + AREA_W - 2) continue;
            g.fill(x + 5, top - 2, x + 7, top, 0xFF8FA0B0);
            if (damaged[k] && k >= next) damagedTile(g, x, top);
            else FragmentsGame.tile(g, x, top, strand[k]);
            if (k < next) {
                // Zipped: the rung and the partner base; a mended rung stays red.
                int rung = broken[k] ? Draw.ALERT : 0xFFE8F0F6;
                g.fill(x + 5, top + 12, x + 7, low, rung);
                FragmentsGame.tile(g, x, low, complement(strand[k]));
                g.fill(x + 5, low + 12, x + 7, low + 14, 0xFF8FA0B0);
            } else {
                int q = k - next;
                if (q < queue.size()) {
                    // Typed ahead: a ghost of the base waiting under its partner.
                    g.fill(x - 1, low - 1, x + 13, low + 13, 0x60FFFFFF);
                    FragmentsGame.tile(g, x, low, queue.get(q));
                    g.fill(x, low, x + 12, low + 12, 0x500E151C);
                }
            }
        }
        // The next slot to fill: a blinking yellow frame under its base.
        int slotK = next + queue.size();
        if (slotK < LENGTH && (tic / 8) % 2 == 0) {
            int x = ax + (int) Math.round(baseX(slotK) - smooth);
            if (x < ax + AREA_W - 2) {
                g.fill(x - 1, low - 1, x + 13, low, Draw.YELLOW);
                g.fill(x - 1, low + 12, x + 13, low + 13, Draw.YELLOW);
                g.fill(x - 1, low, x, low + 12, Draw.YELLOW);
                g.fill(x + 12, low, x + 13, low + 12, Draw.YELLOW);
            }
        }
        // The zipping point: a lit window where the two strands close; red when a rung breaks.
        int c = flash > 0 ? Draw.ALERT : Draw.ACCENT;
        g.fill(zx - 2, top - 7, zx + 14, top - 6, c);
        g.fill(zx - 2, low + 17, zx + 14, low + 18, c);
        g.fill(zx - 2, top - 7, zx - 1, low + 18, c);
        g.fill(zx + 13, top - 7, zx + 14, low + 18, c);
        g.fill(zx + 4, low + 19, zx + 8, low + 21, c);
        g.fill(zx + 5, low + 21, zx + 7, low + 22, c);
    }

    // The zipped strand coils into a turning double helix; the sequencer's light sweeps along it and reads it.
    void drawFinale(GuiGraphics g, int ax, int ay, float pt) {
        double t = finale + pt, grow = Math.min(1, t / 20);
        double cy = ay + 56, width = 250, amp = 16 * grow;
        double scan = ax + 10 + (t - 30) / (HELIX - 40.0) * (width + 10);
        for (int m = 0; m < LENGTH; m++) {
            double px = ax + 15 + m * width / (LENGTH - 1), a = m * 0.55 + t * 0.12;
            double y1 = cy + Math.sin(a) * amp, y2 = cy - Math.sin(a) * amp, mid = (y1 + y2) / 2;
            boolean read = px < scan;
            int c1 = Draw.BASE_COLOURS[strand[m]], c2 = Draw.BASE_COLOURS[complement(strand[m])];
            if (read) {
                c1 = mix(c1, 0xFFFFFFFF, 0.35);
                c2 = mix(c2, 0xFFFFFFFF, 0.35);
            }
            g.fill((int) px, (int) Math.min(y1, mid), (int) px + 3, (int) Math.max(y1, mid) + 1, c1);
            g.fill((int) px, (int) Math.min(y2, mid), (int) px + 3, (int) Math.max(y2, mid) + 1, c2);
            boolean front = Math.cos(a) > 0;
            int fore = read ? 0xFFFFFFFF : 0xFFE8F0F6, back = 0xFF7A8C9E;
            g.fill((int) px - 1, (int) y1 - 1, (int) px + 4, (int) y1 + 2, front ? fore : back);
            g.fill((int) px - 1, (int) y2 - 1, (int) px + 4, (int) y2 + 2, front ? back : fore);
        }
        if (t > 30 && scan < ax + width + 30) {
            int sx = (int) scan;
            g.fill(sx - 3, ay + 30, sx + 3, ay + 82, 0x308CFAEC);
            g.fill(sx, ay + 30, sx + 1, ay + 82, 0xC08CFAEC);
        }
        Draw.centre(g, T.f("strand_complete"), ax + AREA_W / 2, ay + 92, Draw.GREEN);
    }

    @Override
    String instruction() {
        return T.f("the_strand_slides_into_the");
    }

    @Override
    Control[] controls() {
        return new Control[] {new Control(T.f("type_ahead"), keys[0], keys[1], keys[2], keys[3]), new Control(T.f("undo"), InputConstants.KEY_BACKSPACE)};
    }
}
