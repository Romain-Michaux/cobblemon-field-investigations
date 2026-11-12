package io.github.romainmichaux.fieldinvestigations.client;

import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

// Photo de terrain : cadrer un Pokémon du coin qui passe, et déclencher quand l'image est nette
final class PhotoGame extends MiniGame {
    static final ResourceLocation BACKGROUND = Draw.texture("undergrowth");
    static final String[] DEFAULT_VALUE = {"rattata", "pidgey", "oddish", "zubat", "eevee", "pikachu"};
    static final int DURATION = 560;
    final String species;
    final double p1, p2, p3;
    int time = DURATION, faults, flash, lightning, takes, tic;
    String message = "";

    PhotoGame(Random h) {
        // Le sujet est l'un des suspects de l'enquête, pris au hasard (sans rapport avec la cible).
        net.minecraft.nbt.ListTag suspects = FieldInvestigationsClient.state.getList("suspects", net.minecraft.nbt.Tag.TAG_COMPOUND);
        species = suspects.isEmpty() ? DEFAULT_VALUE[h.nextInt(DEFAULT_VALUE.length)] : suspects.getCompound(h.nextInt(suspects.size())).getString("species");
        p1 = h.nextDouble() * 6;
        p2 = h.nextDouble() * 6;
        p3 = h.nextDouble() * 6;
    }

    double speed() {
        return 1 + takes * 0.35;
    }

    double sx() {
        return 80 + 46 * Math.sin(tic * 0.021 * speed() + p1);
    }

    double sy() {
        return 58 + 22 * Math.sin(tic * 0.033 * speed() + p2);
    }

    // Flou en pixels : la mise au point « pompe » toute seule, il faut déclencher au bon moment.
    double blur() {
        return 2.5 * (1 + Math.sin(tic * 0.085 * speed() + p3));
    }

    @Override
    void tick() {
        if (flash > 0) flash--;
        if (lightning > 0) lightning--;
        if (--time <= 0) {
            end(false);
            return;
        }
        press1(time, DURATION);
        tic++;
    }

    @Override
    void click(double mx, double my) {
        int bx = ox + 4, by = oy + 3;
        if (!Draw.in(mx, my, bx, by, L, H)) return;
        Draw.beep(Sounds.SHUTTER, 1.6f, 0.7f);
        boolean frame = Math.abs(mx - bx - sx()) < 13 && Math.abs(my - by - sy()) < 13, net = blur() < 1.3;
        if (frame && net) {
            lightning = 4;
            message = T.f("sharp_photo");
            Draw.note(1f + takes * 0.25f);
            if (++takes >= 3) {
                perfect = faults == 0;
                end(true);
            }
        } else {
            flash = 8;
            message = frame ? T.f("blurry_photo") : T.f("badly_framed");
            Draw.note(0.5f);
            if (++faults >= 3) end(false);
        }
    }

    @Override
    void draw(GuiGraphics g, int mx, int my, float pt) {
        int bx = ox + 4, by = oy + 3;
        frame(g, bx, by, flash > 0);
        Draw.image(g, BACKGROUND, bx, by, L, H);
        int x = bx + (int) sx() - 24, y = by + (int) sy() - 24, f = (int) Math.round(blur());
        // Image floue : le sujet se dédouble, d'autant plus que la mise au point est loin.
        if (f >= 1) {
            Draw.portrait(g, species, Math.max(bx, x - f * 2), y, 3f);
            Draw.portrait(g, species, Math.min(bx + L - 48, x + f * 2), y, 3f);
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            g.fill(Math.max(bx, x - f * 2), y, Math.min(bx + L, x + f * 2 + 48), y + 48, (Math.min(5, f) * 22 << 24) | 0xC8D8C0);
            g.pose().popPose();
        } else {
            Draw.portrait(g, species, Math.max(bx, Math.min(bx + L - 48, x)), y, 3f);
        }
        g.pose().pushPose();
        g.pose().translate(0, 0, 250);
        if (!finished && Draw.in(mx, my, bx, by, L, H)) {
            int c = blur() < 1.3 ? Draw.GREEN : Draw.TEXT;
            for (int cx = -1; cx <= 1; cx += 2) {
                for (int cy = -1; cy <= 1; cy += 2) {
                    int ex = mx + cx * 26, ey = my + cy * 26;
                    g.fill(Math.min(ex, ex - cx * 8), cy < 0 ? ey : ey - 1, Math.max(ex, ex - cx * 8), cy < 0 ? ey + 1 : ey, c);
                    g.fill(cx < 0 ? ex : ex - 1, Math.min(ey, ey - cy * 8), cx < 0 ? ex + 1 : ex, Math.max(ey, ey - cy * 8), c);
                }
            }
            g.fill(mx - 1, my - 1, mx + 1, my + 1, c);
        }
        if (lightning > 0) g.fill(bx, by, bx + L, by + H, 0xA0FFFFFF);
        g.pose().popPose();
        int tx = ox + 172;
        text(g, T.f("photos") + takes + " / 3", tx, oy + 10, Draw.TEXT);
        text(g, T.f("failed_2") + faults + " / 3", tx, oy + 22, faults > 1 ? Draw.ALERT : Draw.GREY);
        text(g, T.f("sharpness"), tx, oy + 40, Draw.YELLOW);
        gauge(g, tx, oy + 51, 100, 1 - blur() / 5.0, blur() < 1.3 ? Draw.GREEN : Draw.DARK);
        text(g, message, tx, oy + 64, message.equals(T.f("sharp_photo")) ? Draw.GREEN : Draw.ALERT);
        text(g, T.f("light"), tx, oy + 92, Draw.GREY);
        timeGauge(g, tx, oy + 104, 100, time / (double) DURATION);
    }

    @Override
    String instruction() {
        return T.f("a_local_pok_mon_walks");
    }
}
