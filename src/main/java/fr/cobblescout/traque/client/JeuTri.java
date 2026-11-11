package fr.cobblescout.traque.client;

import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

// Tri des fragments (nid) : lancer chaque coquille dans la boîte, vite, sans que les chocs envoient le reste n'importe où
final class JeuTri extends Jeu {
    static final ResourceLocation PLATEAU = Dessin.texture("plateau");
    static final ResourceLocation[] DEBRIS = {Dessin.texture("debris_coquille"), Dessin.texture("debris_brindille"),
            Dessin.texture("debris_feuille"), Dessin.texture("debris_caillou")};
    static final int N = 13, COQUILLES = 5, DUREE = 260, RAYON = 7, REBUT = 26, BOITE = L - 26;
    static final double FROTTEMENT = 0.94, REBOND = 0.75, VITESSE_MAX = 11;
    final double[] x = new double[N], y = new double[N], vx = new double[N], vy = new double[N];
    final int[] sorte = new int[N];
    final boolean[] sorti = new boolean[N];
    int tenu = -1, rangees, restantes = COQUILLES, fautes, temps = DUREE, flash, tic, dernierChoc;
    double cx, cy;

    JeuTri(Random h) {
        for (int i = 0; i < N; i++) {
            sorte[i] = i < COQUILLES ? 0 : 1 + h.nextInt(3);
            for (int essai = 0; essai < 200; essai++) {
                x[i] = REBUT + 14 + h.nextInt(BOITE - REBUT - 28);
                y[i] = 12 + h.nextInt(H - 24);
                boolean libre = true;
                for (int k = 0; k < i; k++) libre &= Math.hypot(x[k] - x[i], y[k] - y[i]) >= RAYON * 2 + 2;
                if (libre) break;
            }
        }
    }

    void faute() {
        flash = 8;
        Dessin.note(0.5f);
        if (++fautes >= 3) terminer(false);
    }

    @Override
    void tick() {
        if (flash > 0) flash--;
        if (--temps <= 0) {
            terminer(false);
            return;
        }
        presser(temps, DUREE);
        tic++;
        if (!appui) tenu = -1; // lâché : l'objet garde son élan et continue sur sa lancée
        if (tenu >= 0) {
            // L'objet tenu file vers la souris ; sa vitesse est celle qu'il gardera quand on le lâchera.
            double dx = (cx - ox - 4 - x[tenu]) * 0.55, dy = (cy - oy - 3 - y[tenu]) * 0.55, v = Math.hypot(dx, dy);
            if (v > VITESSE_MAX) {
                dx *= VITESSE_MAX / v;
                dy *= VITESSE_MAX / v;
            }
            vx[tenu] = dx;
            vy[tenu] = dy;
        }
        // Deux demi-pas par tic, pour qu'un objet lancé vite ne traverse pas ses voisins.
        for (int pas = 0; pas < 2; pas++) {
            for (int i = 0; i < N; i++) {
                if (sorti[i]) continue;
                x[i] += vx[i] / 2;
                y[i] += vy[i] / 2;
                // Les bords du haut et du bas renvoient l'objet.
                if (y[i] < RAYON) {
                    y[i] = RAYON;
                    vy[i] = Math.abs(vy[i]) * 0.5;
                } else if (y[i] > H - RAYON) {
                    y[i] = H - RAYON;
                    vy[i] = -Math.abs(vy[i]) * 0.5;
                }
            }
            for (int i = 0; i < N; i++) {
                for (int k = i + 1; k < N; k++) {
                    if (sorti[i] || sorti[k]) continue;
                    double dx = x[k] - x[i], dy = y[k] - y[i], d = Math.hypot(dx, dy), manque = RAYON * 2 - d;
                    if (manque <= 0) continue;
                    if (d < 0.01) {
                        dx = 1;
                        dy = 0;
                        d = 1;
                    }
                    double nx = dx / d, ny = dy / d;
                    // Celui qu'on tient ne cède pas ; sinon chacun recule de moitié.
                    double pi = i == tenu ? 0 : k == tenu ? 1 : 0.5;
                    x[i] -= nx * manque * pi;
                    y[i] -= ny * manque * pi;
                    x[k] += nx * manque * (1 - pi);
                    y[k] += ny * manque * (1 - pi);
                    // Choc : la vitesse d'approche passe de l'un à l'autre, comme au billard.
                    double approche = (vx[k] - vx[i]) * nx + (vy[k] - vy[i]) * ny;
                    if (approche >= 0) continue;
                    double elan = (1 + REBOND) * approche;
                    if (i == tenu) {
                        vx[k] -= elan * nx;
                        vy[k] -= elan * ny;
                    } else if (k == tenu) {
                        vx[i] += elan * nx;
                        vy[i] += elan * ny;
                    } else {
                        vx[i] += elan / 2 * nx;
                        vy[i] += elan / 2 * ny;
                        vx[k] -= elan / 2 * nx;
                        vy[k] -= elan / 2 * ny;
                    }
                    if (approche < -1.2 && tic - dernierChoc > 2) {
                        dernierChoc = tic;
                        Dessin.bip(Sons.CHOC, (float) Math.min(2, 1 - approche / 10), (float) Math.min(0.8, 0.2 - approche / 12));
                    }
                }
            }
        }
        for (int i = 0; i < N; i++) {
            if (sorti[i]) continue;
            if (i != tenu) {
                vx[i] *= FROTTEMENT;
                vy[i] *= FROTTEMENT;
                if (Math.hypot(vx[i], vy[i]) < 0.06) {
                    vx[i] = 0;
                    vy[i] = 0;
                }
            }
            boolean rebut = x[i] < REBUT - 2, boite = x[i] > BOITE + 2;
            if (!rebut && !boite) continue;
            // Tombé dans un compartiment : il n'en ressort plus.
            sorti[i] = true;
            if (i == tenu) tenu = -1;
            if (sorte[i] == 0) {
                restantes--;
                if (boite) {
                    rangees++;
                    Dessin.note(1f + rangees * 0.15f);
                } else {
                    faute(); // une coquille au rebut
                }
            } else if (boite) {
                faute(); // un débris dans la boîte d'échantillons
            } else {
                Dessin.bip(Sons.PLUCK, 0.7f, 0.4f);
            }
        }
        if (restantes <= 0 && !fini) {
            parfait = fautes == 0;
            terminer(rangees > 0);
        }
    }

    @Override
    void clic(double mx, double my) {
        double lx = mx - ox - 4, ly = my - oy - 3, proche = RAYON + 4;
        tenu = -1;
        for (int i = 0; i < N; i++) {
            double d = Math.hypot(lx - x[i], ly - y[i]);
            if (!sorti[i] && d < proche) {
                proche = d;
                tenu = i;
            }
        }
        if (tenu >= 0) Dessin.bip(Sons.PLUCK, 1.3f, 0.4f);
    }

    @Override
    void dessiner(GuiGraphics g, int mx, int my, float pt) {
        int bx = ox + 4, by = oy + 3;
        cx = mx;
        cy = my;
        cadre(g, bx, by, flash > 0);
        Dessin.image(g, PLATEAU, bx, by, L, H);
        // À gauche le rebut, à droite la boîte d'échantillons.
        g.fill(bx, by, bx + REBUT, by + H, 0xB0241A12);
        g.fill(bx + REBUT - 1, by, bx + REBUT, by + H, Dessin.ALERTE);
        g.fill(bx + BOITE, by, bx + L, by + H, 0xB0102A22);
        g.fill(bx + BOITE, by, bx + BOITE + 1, by + H, Dessin.VERT);
        g.setColor(1f, 1f, 1f, 0.45f);
        Dessin.image(g, DEBRIS[1], bx + 5, by + 6, 16, 16);
        Dessin.image(g, DEBRIS[3], bx + 5, by + 26, 16, 16);
        Dessin.image(g, DEBRIS[0], bx + BOITE + 5, by + 6, 16, 16);
        g.setColor(1f, 1f, 1f, 1f);
        g.enableScissor(bx, by, bx + L, by + H);
        for (int i = 0; i < N; i++) {
            if (sorti[i]) continue;
            // Entre deux tics, l'objet est dessiné là où son élan le porte : le mouvement reste fluide.
            int x0 = bx + (int) Math.round(x[i] + (fini ? 0 : vx[i] * pt)), y0 = by + (int) Math.round(y[i] + (fini ? 0 : vy[i] * pt));
            if (i == tenu) Dessin.cercle(g, x0 - 0.5, y0 - 0.5, RAYON + 2, 1, Dessin.JAUNE);
            Dessin.image(g, DEBRIS[sorte[i]], x0 - 8, y0 - 8, 16, 16);
        }
        g.disableScissor();
        int tx = ox + 172;
        texte(g, T.f("shells_2") + rangees + " / " + COQUILLES, tx, oy + 10, Dessin.TEXTE);
        texte(g, T.f("mistakes") + fautes + " / 3", tx, oy + 22, fautes > 1 ? Dessin.ALERTE : Dessin.GRIS);
        Dessin.paragraphe(g, T.f("throw_the_shells_they_keep"), tx, oy + 42, 104, Dessin.SOMBRE);
        jaugeTemps(g, tx, oy + 104, 100, temps / (double) DUREE);
    }

    @Override
    String consigne() {
        return T.f("grab_a_shell_and_throw");
    }
}
