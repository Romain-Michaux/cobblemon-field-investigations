package io.github.romainmichaux.fieldinvestigations.client;

import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

// Lampe UV (enquête nocturne) : balayer le sol dans le noir pour révéler les taches fluorescentes
final class UvLampGame extends MiniGame {
    static final ResourceLocation NIGHT = Draw.texture("night");
    static final int N = 5, RANGE = 22;
    final int[] sx = new int[N], sy = new int[N];
    final boolean[] found1 = new boolean[N];
    double battery = 100, cx, cy;
    int foundOnes, flash, tic;

    UvLampGame(Random h) {
        for (int i = 0; i < N; i++) {
            for (int attempt = 0; attempt < 100; attempt++) {
                sx[i] = 12 + h.nextInt(L - 24);
                sy[i] = 12 + h.nextInt(H - 24);
                boolean free = true;
                for (int k = 0; k < i; k++) free &= Math.hypot(sx[k] - sx[i], sy[k] - sy[i]) > 30;
                if (free) break;
            }
        }
    }

    @Override
    void tick() {
        if (flash > 0) flash--;
        tic++;
        battery -= 0.17;
        if (tic % 24 == 0) Draw.beep(Sounds.LAMP, 1.8f, 0.25f);
        if (battery <= 0) end(false);
    }

    @Override
    void click(double mx, double my) {
        int bx = ox + 4, by = oy + 3;
        if (!Draw.in(mx, my, bx, by, L, H)) return;
        for (int i = 0; i < N; i++) {
            if (found1[i] || Math.hypot(mx - bx - sx[i], my - by - sy[i]) > 9) continue;
            found1[i] = true;
            Draw.note(1f + foundOnes * 0.2f);
            if (++foundOnes >= N) {
                perfect = battery >= 35;
                end(true);
            }
            return;
        }
        // Un clic dans le vide vide la batterie.
        battery -= 8;
        flash = 6;
        Draw.beep(Sounds.FAILURE, 1.2f, 0.6f);
    }

    @Override
    void draw(GuiGraphics g, int mx, int my, float pt) {
        int bx = ox + 4, by = oy + 3;
        cx = mx;
        cy = my;
        frame(g, bx, by, flash > 0);
        g.setColor(0.45f, 0.45f, 0.6f, 1f);
        Draw.image(g, NIGHT, bx, by, L, H);
        g.setColor(1f, 1f, 1f, 1f);
        boolean inside = !finished && Draw.in(mx, my, bx, by, L, H);
        if (inside) {
            for (int dy = -RANGE; dy <= RANGE; dy++) {
                int dx = (int) Math.sqrt(RANGE * RANGE - dy * dy), y = my + dy;
                if (y < by || y >= by + H) continue;
                g.fill(Math.max(bx, mx - dx), y, Math.min(bx + L, mx + dx), y + 1, 0x389060FF);
            }
        }
        for (int i = 0; i < N; i++) {
            int x = bx + sx[i], y = by + sy[i];
            if (found1[i]) {
                disc(g, x, y, 4, 0xFF6CCB5F);
                Draw.circle(g, x, y, 7, 1, Draw.GREEN);
            } else if (inside && Math.hypot(mx - x, my - y) < RANGE) {
                boolean pulse = (tic / 3) % 2 == 0;
                disc(g, x, y, pulse ? 4 : 3, 0xFFB8FF5A);
                g.fill(x - 6, y + 3, x - 4, y + 5, 0xFFE0FF90);
                g.fill(x + 4, y - 5, x + 6, y - 3, 0xFFE0FF90);
            }
        }
        int tx = ox + 172;
        text(g, T.f("stains") + foundOnes + " / " + N, tx, oy + 10, Draw.TEXT);
        text(g, T.f("battery"), tx, oy + 28, battery < 30 ? Draw.ALERT : 0xFFB79CFF);
        gauge(g, tx, oy + 39, 100, battery / 100, battery < 30 ? Draw.ALERT : 0xFF9060FF);
        Draw.paragraph(g, T.f("stains_only_glow_under_the"), tx, oy + 54, 104, Draw.DARK);
    }

    @Override
    String instruction() {
        return T.f("sweep_the_ground_with_the");
    }
}
