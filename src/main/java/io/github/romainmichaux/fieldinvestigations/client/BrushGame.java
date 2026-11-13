package io.github.romainmichaux.fieldinvestigations.client;

import java.io.InputStream;
import java.util.Random;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

// Brushing (buried clue): brush away a dry crust, then damp soil, to free the three shards of the clue. Covered shards
// sound hollow under the brush; a couple of pebbles sound dry. A stroke that is too fast snags what lies buried.
final class BrushGame extends MiniGame {
    // The field is 69 x 27 cells of 4 px (276 x 108), drawn from textures at twice that density.
    static final int COLS = 69, ROWS = 27, CELL = 4, FW = COLS * CELL, FH = ROWS * CELL, TW = FW * 2, TH = FH * 2;
    static final int DURATION = 760, SHARDS = 3, PEBBLES = 2, RADIUS = 9, CANVAS = 100, ASSEMBLY = 44;
    static final double MAX_SPEED = 17;
    static final ResourceLocation TOOL = Draw.texture("brush_tool");
    static final ResourceLocation SOIL_ID = id("soil"), BASE_ID = id("base");
    static final ResourceLocation[] SHARD_ID = {id("shard_0"), id("shard_1"), id("shard_2")};
    static final int PEBBLE = 10;

    // Soil left on each cell: 2 = untouched crust, 1 = damp soil, 0 = cleared.
    final float[] soil = new float[COLS * ROWS];
    // What lies under each cell: 0 nothing, 1..3 a shard, PEBBLE a pebble.
    final int[] under = new int[COLS * ROWS];
    final float[] noise = new float[TW * TH], pale = new float[TW * TH];
    final int[] glintX = new int[SHARDS], glintY = new int[SHARDS], shardX = new int[SHARDS], shardY = new int[SHARDS], foundAt = new int[SHARDS], shardCells = new int[SHARDS];
    final boolean[] found = new boolean[SHARDS];
    final Random h;
    NativeImage crust, damp, soilImage;
    DynamicTexture soilTexture;
    int time = DURATION, faults, flash, cooldown, foundCount, tic, assembling = -1;
    double prevX = -1, prevY = -1, cx, cy, speed, tilt;
    boolean dirty;
    // Dust kicked up by the brush.
    final double[] dx = new double[64], dy = new double[64], dvx = new double[64], dvy = new double[64];
    final int[] dlife = new int[64], dcolour = new int[64];

    BrushGame(Random h, String kind, int variant) {
        this.h = h;
        java.util.Arrays.fill(soil, 2f);
        for (int i = 0; i < noise.length; i++) noise[i] = 0;
        for (int y = 0; y < TH; y += 2) {
            for (int x = 0; x < TW; x += 2) {
                float n = (h.nextFloat() - 0.5f) * 0.44f;
                noise[y * TW + x] = noise[y * TW + x + 1] = noise[(y + 1) * TW + x] = noise[(y + 1) * TW + x + 1] = n;
            }
        }
        crust = read(Draw.texture("brush_crust"));
        damp = read(Draw.texture("brush_damp"));
        NativeImage icon = scale2x(read(Draw.buriedIcon(kind, variant)));
        NativeImage base = new NativeImage(TW, TH, true);
        for (int y = 0; y < TH; y++) {
            for (int x = 0; x < TW; x++) base.setPixelRGBA(x, y, shade(sample(damp, x, y, 0xFF2A1C14), 0.42));
        }
        java.util.List<int[]> boxes = new java.util.ArrayList<>();
        NativeImage[] shards = cutShards(icon);
        for (int s = 0; s < SHARDS; s++) {
            int[] b = bounds(shards[s]);
            int[] at = place(boxes, (b[2] - b[0]) / 2, (b[3] - b[1]) / 2);
            shardX[s] = at[0] - b[0] / 2;
            shardY[s] = at[1] - b[1] / 2;
            glintX[s] = (b[0] + b[2]) / 4;
            glintY[s] = (b[1] + b[3]) / 4;
            int[] count = new int[COLS * ROWS];
            for (int v = 0; v < CANVAS; v++) {
                for (int u = 0; u < CANVAS; u++) {
                    int p = shards[s].getPixelRGBA(u, v);
                    if ((p >>> 24) < 128) continue;
                    int x = shardX[s] * 2 + u, y = shardY[s] * 2 + v;
                    if (x < 0 || y < 0 || x >= TW || y >= TH) continue;
                    base.setPixelRGBA(x, y, shade(p, 0.82));
                    pale[y * TW + x] = 1;
                    count[(y / 8) * COLS + x / 8]++;
                }
            }
            for (int c = 0; c < count.length; c++) {
                if (count[c] >= 8) {
                    under[c] = s + 1;
                    shardCells[s]++;
                }
            }
            Minecraft.getInstance().getTextureManager().register(SHARD_ID[s], new DynamicTexture(rimmed(shards[s])));
        }
        for (int k = 0; k < PEBBLES; k++) pebble(base, boxes);
        blur(pale, 5);
        blur(pale, 5);
        Minecraft.getInstance().getTextureManager().register(BASE_ID, new DynamicTexture(base));
        soilImage = new NativeImage(TW, TH, true);
        render(0, 0, COLS, ROWS);
        soilTexture = new DynamicTexture(soilImage);
        Minecraft.getInstance().getTextureManager().register(SOIL_ID, soilTexture);
        if (icon != null) icon.close();
    }

    // Box blur, horizontal then vertical, radius r texels.
    static void blur(float[] a, int r) {
        float[] t = new float[a.length];
        for (int y = 0; y < TH; y++) {
            for (int x = 0; x < TW; x++) {
                float sum = 0;
                for (int k = -r; k <= r; k++) sum += a[y * TW + Math.max(0, Math.min(TW - 1, x + k))];
                t[y * TW + x] = sum / (2 * r + 1);
            }
        }
        for (int y = 0; y < TH; y++) {
            for (int x = 0; x < TW; x++) {
                float sum = 0;
                for (int k = -r; k <= r; k++) sum += t[Math.max(0, Math.min(TH - 1, y + k)) * TW + x];
                a[y * TW + x] = sum / (2 * r + 1);
            }
        }
    }

    // Scale2x: doubles a pixel-art image while keeping its diagonals smooth.
    static NativeImage scale2x(NativeImage in) {
        if (in == null) return null;
        int w = in.getWidth(), hh = in.getHeight();
        NativeImage out = new NativeImage(w * 2, hh * 2, true);
        for (int y = 0; y < hh; y++) {
            for (int x = 0; x < w; x++) {
                int p = in.getPixelRGBA(x, y);
                int a = y > 0 ? in.getPixelRGBA(x, y - 1) : p, b = x < w - 1 ? in.getPixelRGBA(x + 1, y) : p;
                int c = x > 0 ? in.getPixelRGBA(x - 1, y) : p, d = y < hh - 1 ? in.getPixelRGBA(x, y + 1) : p;
                boolean ok = c != b && a != d;
                out.setPixelRGBA(x * 2, y * 2, ok && c == a ? a : p);
                out.setPixelRGBA(x * 2 + 1, y * 2, ok && a == b ? b : p);
                out.setPixelRGBA(x * 2, y * 2 + 1, ok && d == c ? c : p);
                out.setPixelRGBA(x * 2 + 1, y * 2 + 1, ok && b == d ? d : p);
            }
        }
        in.close();
        return out;
    }

    static ResourceLocation id(String name) {
        return ResourceLocation.fromNamespaceAndPath("cobblemon_field_investigations", "dynamic/brush_" + name);
    }

    static NativeImage read(ResourceLocation texture) {
        try (InputStream in = Minecraft.getInstance().getResourceManager().open(texture)) {
            return NativeImage.read(in);
        } catch (Exception e) {
            return null;
        }
    }

    // Pixels are ABGR in NativeImage.
    static int sample(NativeImage tile, int x, int y, int fallback) {
        return tile == null ? fallback : tile.getPixelRGBA(Math.floorMod(x, tile.getWidth()), Math.floorMod(y, tile.getHeight()));
    }

    static int shade(int abgr, double k) {
        int a = abgr >>> 24, b = (int) (((abgr >> 16) & 0xFF) * k), g = (int) (((abgr >> 8) & 0xFF) * k), r = (int) ((abgr & 0xFF) * k);
        return (a << 24) | (Math.min(255, b) << 16) | (Math.min(255, g) << 8) | Math.min(255, r);
    }

    static int blend(int abgr, int argb, double t) {
        int b = (int) (((abgr >> 16) & 0xFF) * (1 - t) + (argb & 0xFF) * t), g = (int) (((abgr >> 8) & 0xFF) * (1 - t) + ((argb >> 8) & 0xFF) * t);
        int r = (int) ((abgr & 0xFF) * (1 - t) + ((argb >> 16) & 0xFF) * t);
        return (abgr & 0xFF000000) | (b << 16) | (g << 8) | r;
    }

    // The clue's icon, 6 texels per pixel, broken into three shards around its centre along a jagged line.
    NativeImage[] cutShards(NativeImage icon) {
        int n = icon == null ? 0 : icon.getWidth();
        double mx = 0, my = 0;
        int opaque = 0;
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                if ((icon.getPixelRGBA(x, y) >>> 24) < 128) continue;
                mx += x + 0.5;
                my += y + 0.5;
                opaque++;
            }
        }
        mx /= Math.max(1, opaque);
        my /= Math.max(1, opaque);
        int[] sector = new int[n * n];
        for (int attempt = 0; attempt < 20; attempt++) {
            double offset = h.nextDouble() * Math.PI * 2, wobble = h.nextDouble() * 6;
            int[] sizes = new int[SHARDS];
            for (int y = 0; y < n; y++) {
                for (int x = 0; x < n; x++) {
                    double ax = x + 0.5 - mx, ay = y + 0.5 - my, d = Math.hypot(ax, ay);
                    double a = Math.atan2(ay, ax) + offset + 0.35 * Math.sin(d * 1.9 + wobble);
                    sector[y * n + x] = (int) (Math.floorMod((long) Math.floor(a / (Math.PI * 2 / SHARDS) * 1000), SHARDS * 1000L) / 1000);
                    if ((icon.getPixelRGBA(x, y) >>> 24) >= 128) sizes[sector[y * n + x]]++;
                }
            }
            if (Math.min(sizes[0], Math.min(sizes[1], sizes[2])) >= opaque / 5) break;
        }
        NativeImage[] out = new NativeImage[SHARDS];
        for (int s = 0; s < SHARDS; s++) {
            out[s] = new NativeImage(CANVAS, CANVAS, true);
            for (int y = 0; y < n; y++) {
                for (int x = 0; x < n; x++) {
                    int p = icon.getPixelRGBA(x, y);
                    if ((p >>> 24) < 128 || sector[y * n + x] != s) continue;
                    out[s].fillRect(2 + x * 3, 2 + y * 3, 3, 3, p | 0xFF000000);
                }
            }
        }
        return out;
    }

    // Texel box of the opaque part of a shard canvas: x0, y0, x1, y1.
    static int[] bounds(NativeImage im) {
        int x0 = CANVAS, y0 = CANVAS, x1 = 0, y1 = 0;
        for (int y = 0; y < CANVAS; y++) {
            for (int x = 0; x < CANVAS; x++) {
                if ((im.getPixelRGBA(x, y) >>> 24) < 128) continue;
                x0 = Math.min(x0, x);
                y0 = Math.min(y0, y);
                x1 = Math.max(x1, x + 1);
                y1 = Math.max(y1, y + 1);
            }
        }
        return x0 >= x1 ? new int[] {0, 0, 12, 12} : new int[] {x0, y0, x1, y1};
    }

    // A free spot (GUI px, top left) for a box of w x hh, away from the other buried things and the counters.
    int[] place(java.util.List<int[]> boxes, int w, int hh) {
        int x = 0, y = 0;
        for (int attempt = 0; attempt < 400; attempt++) {
            x = 4 + h.nextInt(Math.max(1, FW - w - 8));
            y = 4 + h.nextInt(Math.max(1, FH - hh - 8));
            boolean free = !(y < 14 && (x < 70 || x + w > FW - 70));
            for (int[] b : boxes) free &= x + w + 6 < b[0] || b[0] + b[2] + 6 < x || y + hh + 6 < b[1] || b[1] + b[3] + 6 < y;
            if (free) break;
        }
        boxes.add(new int[] {x, y, w, hh});
        return new int[] {x, y};
    }

    // A rounded grey pebble: under the brush it looks like a shard at first, but it sounds dry.
    void pebble(NativeImage base, java.util.List<int[]> boxes) {
        int w = 14 + h.nextInt(8), hh = 10 + h.nextInt(6);
        int[] at = place(boxes, w, hh);
        double rx = w, ry = hh, cxp = at[0] * 2 + rx, cyp = at[1] * 2 + ry;
        int tone = 0x7E7A72 + h.nextInt(3) * 0x060606;
        int[] count = new int[COLS * ROWS];
        for (int y = at[1] * 2; y < at[1] * 2 + hh * 2; y++) {
            for (int x = at[0] * 2; x < at[0] * 2 + w * 2; x++) {
                double ex = (x + 0.5 - cxp) / rx, ey = (y + 0.5 - cyp) / ry, e = ex * ex + ey * ey;
                if (e > 1 || x >= TW || y >= TH) continue;
                double light = 1.15 - 0.35 * (ex + ey) - (e > 0.8 ? 0.3 : 0) + (h.nextInt(6) == 0 ? -0.08 : 0);
                int r = (int) Math.min(255, ((tone >> 16) & 0xFF) * light), g = (int) Math.min(255, ((tone >> 8) & 0xFF) * light);
                int b = (int) Math.min(255, (tone & 0xFF) * light);
                base.setPixelRGBA(x, y, 0xFF000000 | (b << 16) | (g << 8) | r);
                pale[y * TW + x] = 1;
                count[(y / 8) * COLS + x / 8]++;
            }
        }
        for (int c = 0; c < count.length; c++) {
            if (count[c] >= 8 && under[c] == 0) under[c] = PEBBLE;
        }
    }

    // The shard with a pale gold rim, shown once it is free and when the clue comes back together.
    static NativeImage rimmed(NativeImage shard) {
        NativeImage out = new NativeImage(CANVAS, CANVAS, true);
        for (int y = 0; y < CANVAS; y++) {
            for (int x = 0; x < CANVAS; x++) {
                int p = shard.getPixelRGBA(x, y);
                if ((p >>> 24) >= 128) {
                    out.setPixelRGBA(x, y, p);
                    continue;
                }
                boolean near = false;
                for (int oy = -2; oy <= 2 && !near; oy++) {
                    for (int ox = -2; ox <= 2 && !near; ox++) {
                        int nx = x + ox, ny = y + oy;
                        near = ox * ox + oy * oy <= 5 && nx >= 0 && ny >= 0 && nx < CANVAS && ny < CANVAS && (shard.getPixelRGBA(nx, ny) >>> 24) >= 128;
                    }
                }
                if (near) out.setPixelRGBA(x, y, 0xFF8CD6F2); // ABGR of 0xF2D68C
            }
        }
        shard.close();
        return out;
    }

    float soilAt(int c, int r) {
        return soil[Math.max(0, Math.min(ROWS - 1, r)) * COLS + Math.max(0, Math.min(COLS - 1, c))];
    }

    // Redraws the soil texture over a block of cells: the crust and the damp layer blend with a grainy edge.
    void render(int c0, int r0, int c1, int r1) {
        int x0 = Math.max(0, c0 * 8), y0 = Math.max(0, r0 * 8), x1 = Math.min(TW, c1 * 8), y1 = Math.min(TH, r1 * 8);
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                double fx = (x + 0.5) / 8 - 0.5, fy = (y + 0.5) / 8 - 0.5;
                int c = (int) Math.floor(fx), r = (int) Math.floor(fy);
                double tx = fx - c, ty = fy - r;
                double v = (soilAt(c, r) * (1 - tx) + soilAt(c + 1, r) * tx) * (1 - ty) + (soilAt(c, r + 1) * (1 - tx) + soilAt(c + 1, r + 1) * tx) * ty;
                double e = v + noise[y * TW + x];
                int p;
                if (e > 1) {
                    p = sample(crust, x, y, 0xFF7AB2D9);
                    if (e < 1.12) p = shade(p, 0.72);
                    else if (v < 1.9) p = shade(p, 0.9 + 0.05 * (v - 1));
                } else if (e > 0) {
                    p = sample(damp, x, y, 0xFF2A3D5A);
                    float k = pale[y * TW + x];
                    if (k > 0.05f && e < 0.9) p = blend(p, 0xE6D8B4, Math.min(1, k * 1.6) * (0.9 - e) / 0.9 * 0.6);
                    if (e < 0.1) p = shade(p, 0.7);
                } else {
                    p = 0;
                }
                soilImage.setPixelRGBA(x, y, p);
            }
        }
    }

    boolean covered(int s) {
        int clear = 0;
        for (int c = 0; c < soil.length; c++) {
            if (under[c] == s + 1 && soil[c] <= 0.05f) clear++;
        }
        return clear * 100 < shardCells[s] * 80;
    }

    @Override
    void tick() {
        tic++;
        if (flash > 0) flash--;
        if (cooldown > 0) cooldown--;
        for (int k = 0; k < dlife.length; k++) {
            if (dlife[k] <= 0) continue;
            dlife[k]--;
            dx[k] += dvx[k];
            dy[k] += dvy[k];
            dvx[k] *= 0.85;
            dvy[k] = dvy[k] * 0.85 + 0.05;
        }
        if (assembling >= 0) {
            if (++assembling == 20) Draw.beep(Sounds.SUCCESS, 1.2f, 0.5f);
            if (assembling >= ASSEMBLY) {
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
        int bx = ox + 2, by = oy + 2;
        double moveX = prevX < 0 ? 0 : cx - prevX, moveY = prevY < 0 ? 0 : cy - prevY;
        speed = speed * 0.5 + Math.hypot(moveX, moveY) * 0.5;
        tilt = tilt * 0.6 + Math.max(-25, Math.min(25, moveX * 2.5)) * 0.4;
        boolean overSoil = Draw.in(cx, cy, bx - RADIUS, by - RADIUS, FW + 2 * RADIUS, FH + 2 * RADIUS);
        if (mouseDown && overSoil && prevX >= 0) {
            double stroke = Math.hypot(moveX, moveY);
            if (cooldown == 0 && stroke > MAX_SPEED) {
                // A stroke that is too rough snags what lies buried.
                flash = 8;
                cooldown = 12;
                Draw.beep(Sounds.CRACK, 1.3f, 0.6f);
                Draw.note(0.5f);
                if (++faults >= 3) end(false);
            } else if (cooldown == 0 && stroke > 0.3) {
                // The brush wears the soil away gradually, more at its centre than at its edge, so the player has to linger.
                double lx = cx - bx, ly = cy - by;
                int c0 = (int) ((lx - RADIUS) / CELL) - 1, c1 = (int) ((lx + RADIUS) / CELL) + 2;
                int r0 = (int) ((ly - RADIUS) / CELL) - 1, r1 = (int) ((ly + RADIUS) / CELL) + 2;
                boolean removed = false;
                for (int r = Math.max(0, r0); r < Math.min(ROWS, r1); r++) {
                    for (int c = Math.max(0, c0); c < Math.min(COLS, c1); c++) {
                        int i = r * COLS + c;
                        if (soil[i] <= 0) continue;
                        double d = Math.hypot(lx - (c * CELL + 2), ly - (r * CELL + 2));
                        if (d >= RADIUS) continue;
                        soil[i] = (float) Math.max(0, soil[i] - (0.5 * (1 - d / RADIUS) + 0.05));
                        removed = true;
                    }
                }
                if (removed) {
                    render(c0 - 1, r0 - 1, c1 + 1, r1 + 1);
                    dirty = true;
                    dust(moveX, moveY);
                }
                if (tic % 5 == 0) {
                    Draw.beep(Sounds.BRUSH_SOUND, (float) (0.9 + Math.min(0.5, stroke / 30)), 0.7f);
                    // What is still covered right under the bristles answers: hollow for a shard, dry for a pebble.
                    int kind = 0;
                    int cc = (int) (lx / CELL), cr = (int) (ly / CELL);
                    for (int r = cr - 1; r <= cr + 1; r++) {
                        for (int c = cc - 1; c <= cc + 1; c++) {
                            if (r < 0 || c < 0 || r >= ROWS || c >= COLS || soil[r * COLS + c] <= 0.3f) continue;
                            int u = under[r * COLS + c];
                            if (u > 0 && u <= SHARDS) kind = 1;
                            else if (u == PEBBLE && kind == 0) kind = 2;
                        }
                    }
                    if (kind == 1) Draw.beep(Sounds.KNOCK, 0.55f, 0.45f);
                    else if (kind == 2) Draw.beep(Sounds.STONE, 1.8f, 0.4f);
                }
                for (int s = 0; s < SHARDS; s++) {
                    if (found[s] || covered(s)) continue;
                    found[s] = true;
                    foundAt[s] = tic;
                    Draw.note(1f + foundCount * 0.25f);
                    if (++foundCount >= SHARDS) assembling = 0;
                }
            }
        }
        prevX = cx;
        prevY = cy;
    }

    void dust(double moveX, double moveY) {
        for (int n = 0; n < 2; n++) {
            for (int k = 0; k < dlife.length; k++) {
                if (dlife[k] > 0) continue;
                double a = h.nextDouble() * Math.PI * 2, r = h.nextDouble() * RADIUS * 0.7;
                dx[k] = cx + Math.cos(a) * r;
                dy[k] = cy + Math.sin(a) * r;
                dvx[k] = -moveX * 0.25 + (h.nextDouble() - 0.5) * 1.6;
                dvy[k] = -moveY * 0.25 + (h.nextDouble() - 0.5) * 1.6 - 0.6;
                dlife[k] = 10 + h.nextInt(8);
                int c = Math.max(0, Math.min(COLS - 1, (int) ((cx - ox - 2) / CELL))), row = Math.max(0, Math.min(ROWS - 1, (int) ((cy - oy - 2) / CELL)));
                dcolour[k] = soil[row * COLS + c] > 1 ? 0xD9B27A : 0x6A4A34;
                break;
            }
        }
    }

    @Override
    void draw(GuiGraphics g, int mx, int my, float pt) {
        int bx = ox + 2, by = oy + 2;
        cx = mx;
        cy = my;
        if (dirty) {
            soilTexture.upload();
            dirty = false;
        }
        g.fill(bx - 1, by - 1, bx + FW + 1, by + FH + 1, flash > 0 ? Draw.ALERT : Draw.EDGE);
        g.pose().pushPose();
        g.pose().translate(bx, by, 0);
        g.pose().scale(0.5f, 0.5f, 1f);
        Draw.image(g, BASE_ID, 0, 0, TW, TH);
        Draw.image(g, SOIL_ID, 0, 0, TW, TH);
        g.pose().popPose();

        // Freed shards lift a little off the ground, with a glint now and then.
        if (assembling < 0) {
            for (int s = 0; s < SHARDS; s++) {
                if (!found[s]) continue;
                int lift = Math.min(2, (tic - foundAt[s]) / 3);
                shard(g, s, bx + shardX[s], by + shardY[s], lift);
            }
        } else {
            // All three shards found: they fly together into the whole clue.
            g.fill(bx, by, bx + FW, by + FH, (Math.min(0xA0, assembling * 8)) << 24);
            double t = Math.min(1, assembling / 22.0), ease = 1 - (1 - t) * (1 - t) * (1 - t);
            int targetX = bx + (FW - CANVAS / 2) / 2, targetY = by + (FH - CANVAS / 2) / 2;
            if (assembling > 22) {
                int glow = (int) (0x60 * Math.sin((assembling - 22) / 22.0 * Math.PI));
                disc(g, targetX + CANVAS / 4, targetY + CANVAS / 4, 30, glow << 24 | 0xF2D68C);
            }
            for (int s = 0; s < SHARDS; s++) {
                int x = (int) Math.round(bx + shardX[s] + (targetX - bx - shardX[s]) * ease);
                int y = (int) Math.round(by + shardY[s] + (targetY - by - shardY[s]) * ease);
                shard(g, s, x, y, 0);
            }
        }

        for (int k = 0; k < dlife.length; k++) {
            if (dlife[k] <= 0) continue;
            int a = Math.min(255, dlife[k] * 20);
            g.fill((int) dx[k], (int) dy[k], (int) dx[k] + 1, (int) dy[k] + 1, a << 24 | dcolour[k]);
        }

        // The brush: its bristles sit on the pointer and lean with the stroke; they glow orange when the stroke gets too fast.
        if (!finished && assembling < 0 && Draw.in(mx, my, bx, by, FW, FH)) {
            double warn = Math.max(0, Math.min(1, (speed / MAX_SPEED - 0.55) / 0.45));
            int ring = warn > 0 ? mix(0xFFF2F5F7, 0xFFFF8A30, warn) & 0x90FFFFFF : 0x50FFFFFF;
            Draw.circle(g, mx - 1, my - 1, RADIUS, 1, ring);
            g.pose().pushPose();
            g.pose().translate(mx, my, 0);
            g.pose().mulPose(Axis.ZP.rotationDegrees((float) tilt + (mouseDown ? 8 : 0)));
            g.pose().scale(0.5f, 0.5f, 1f);
            if (warn > 0) g.setColor(1f, (float) (1 - 0.45 * warn), (float) (1 - 0.75 * warn), 1f);
            Draw.image(g, TOOL, -3, -27, 32, 32);
            g.setColor(1f, 1f, 1f, 1f);
            g.pose().popPose();
        }

        // Counters on dark tabs in the top corners, the time as a thin line along the bottom.
        String left = T.f("shards") + foundCount + " / " + SHARDS, right = T.f("snags") + faults + " / 3";
        int lw = Draw.textFont().width(left), rw = Draw.textFont().width(right);
        g.fill(bx, by, bx + lw + 8, by + 12, 0xB0101418);
        g.fill(bx + FW - rw - 8, by, bx + FW, by + 12, 0xB0101418);
        text(g, left, bx + 4, by + 2, foundCount > 0 ? Draw.GREEN : Draw.TEXT);
        text(g, right, bx + FW - rw - 4, by + 2, faults > 0 ? Draw.ALERT : Draw.GREY);
        if (flash > 0) {
            String rough = T.f("too_rough");
            int w = Draw.textFont().width(rough);
            g.fill(bx + FW / 2 - w / 2 - 4, by, bx + FW / 2 + w / 2 + 4, by + 12, 0xC0401010);
            text(g, rough, bx + FW / 2 - w / 2, by + 2, Draw.ALERT);
        }
        double part = time / (double) DURATION;
        g.fill(ox + 6, oy + AREA_H - 5, ox + AREA_W - 6, oy + AREA_H - 2, Draw.EDGE);
        g.fill(ox + 7, oy + AREA_H - 4, ox + 7 + (int) ((AREA_W - 14) * Math.max(0, Math.min(1, part))), oy + AREA_H - 3,
                part > 0.25 ? Draw.ACCENT : Draw.ALERT);
    }

    void shard(GuiGraphics g, int s, int x, int y, int lift) {
        if (lift > 0) {
            g.setColor(0f, 0f, 0f, 0.4f);
            g.pose().pushPose();
            g.pose().translate(x + 1, y + 1, 0);
            g.pose().scale(0.5f, 0.5f, 1f);
            Draw.image(g, SHARD_ID[s], 0, 0, CANVAS, CANVAS);
            g.pose().popPose();
            g.setColor(1f, 1f, 1f, 1f);
        }
        g.pose().pushPose();
        g.pose().translate(x, y - lift, 0);
        g.pose().scale(0.5f, 0.5f, 1f);
        Draw.image(g, SHARD_ID[s], 0, 0, CANVAS, CANVAS);
        g.pose().popPose();
        int phase = (tic + s * 17) % 50;
        if (lift > 0 && phase < 10) {
            int size = phase < 5 ? phase / 2 : (10 - phase) / 2, gx = x + glintX[s], gy = y - lift + glintY[s];
            g.fill(gx - size, gy, gx + size + 1, gy + 1, 0xFFFFF6D8);
            g.fill(gx, gy - size, gx + 1, gy + size + 1, 0xFFFFF6D8);
        }
    }

    @Override
    String instruction() {
        return T.f("the_earth_keeps_what_it");
    }

    @Override
    Control[] controls() {
        return new Control[] {new Control(T.f("brush_hold"), Draw.MOUSE)};
    }
}
