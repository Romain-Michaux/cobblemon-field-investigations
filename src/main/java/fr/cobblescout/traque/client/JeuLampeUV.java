package fr.cobblescout.traque.client;

import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

// Lampe UV (enquête nocturne) : balayer le sol dans le noir pour révéler les taches fluorescentes
final class JeuLampeUV extends Jeu {
    static final ResourceLocation NUIT = Dessin.texture("nuit");
    static final int N = 5, PORTEE = 22;
    final int[] sx = new int[N], sy = new int[N];
    final boolean[] trouve = new boolean[N];
    double batterie = 100, cx, cy;
    int trouves, flash, tic;

    JeuLampeUV(Random h) {
        for (int i = 0; i < N; i++) {
            for (int essai = 0; essai < 100; essai++) {
                sx[i] = 12 + h.nextInt(L - 24);
                sy[i] = 12 + h.nextInt(H - 24);
                boolean libre = true;
                for (int k = 0; k < i; k++) libre &= Math.hypot(sx[k] - sx[i], sy[k] - sy[i]) > 30;
                if (libre) break;
            }
        }
    }

    @Override
    void tick() {
        if (flash > 0) flash--;
        tic++;
        batterie -= 0.17;
        if (tic % 24 == 0) Dessin.bip(Sons.LAMPE, 1.8f, 0.25f);
        if (batterie <= 0) terminer(false);
    }

    @Override
    void clic(double mx, double my) {
        int bx = ox + 4, by = oy + 3;
        if (!Dessin.dans(mx, my, bx, by, L, H)) return;
        for (int i = 0; i < N; i++) {
            if (trouve[i] || Math.hypot(mx - bx - sx[i], my - by - sy[i]) > 9) continue;
            trouve[i] = true;
            Dessin.note(1f + trouves * 0.2f);
            if (++trouves >= N) {
                parfait = batterie >= 35;
                terminer(true);
            }
            return;
        }
        // Un clic dans le vide vide la batterie.
        batterie -= 8;
        flash = 6;
        Dessin.bip(Sons.ECHEC, 1.2f, 0.6f);
    }

    @Override
    void dessiner(GuiGraphics g, int mx, int my, float pt) {
        int bx = ox + 4, by = oy + 3;
        cx = mx;
        cy = my;
        cadre(g, bx, by, flash > 0);
        g.setColor(0.45f, 0.45f, 0.6f, 1f);
        Dessin.image(g, NUIT, bx, by, L, H);
        g.setColor(1f, 1f, 1f, 1f);
        boolean dedans = !fini && Dessin.dans(mx, my, bx, by, L, H);
        if (dedans) {
            for (int dy = -PORTEE; dy <= PORTEE; dy++) {
                int dx = (int) Math.sqrt(PORTEE * PORTEE - dy * dy), y = my + dy;
                if (y < by || y >= by + H) continue;
                g.fill(Math.max(bx, mx - dx), y, Math.min(bx + L, mx + dx), y + 1, 0x389060FF);
            }
        }
        for (int i = 0; i < N; i++) {
            int x = bx + sx[i], y = by + sy[i];
            if (trouve[i]) {
                disque(g, x, y, 4, 0xFF6CCB5F);
                Dessin.cercle(g, x, y, 7, 1, Dessin.VERT);
            } else if (dedans && Math.hypot(mx - x, my - y) < PORTEE) {
                boolean pulse = (tic / 3) % 2 == 0;
                disque(g, x, y, pulse ? 4 : 3, 0xFFB8FF5A);
                g.fill(x - 6, y + 3, x - 4, y + 5, 0xFFE0FF90);
                g.fill(x + 4, y - 5, x + 6, y - 3, 0xFFE0FF90);
            }
        }
        int tx = ox + 172;
        texte(g, T.f("stains") + trouves + " / " + N, tx, oy + 10, Dessin.TEXTE);
        texte(g, T.f("battery"), tx, oy + 28, batterie < 30 ? Dessin.ALERTE : 0xFFB79CFF);
        jauge(g, tx, oy + 39, 100, batterie / 100, batterie < 30 ? Dessin.ALERTE : 0xFF9060FF);
        Dessin.paragraphe(g, T.f("stains_only_glow_under_the"), tx, oy + 54, 104, Dessin.SOMBRE);
    }

    @Override
    String consigne() {
        return T.f("sweep_the_ground_with_the");
    }
}
