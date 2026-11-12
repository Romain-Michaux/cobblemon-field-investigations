package io.github.romainmichaux.fieldinvestigations.client;

import io.github.romainmichaux.fieldinvestigations.Genetics;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.item.PokemonItem;
import com.cobblemon.mod.common.pokemon.Species;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

// Petite boîte à outils de dessin en pixel art : panneaux, encarts, boutons, puces de couleur, tracés point par point.
// Les fonds, boutons et accessoires sont des textures en pixel art (générées avec PixelLab, préparées par outils/gen_interfaces.py) ;
// le reste (tracés, puces, jauges) est dessiné point par point par-dessus.
final class Draw {
    static final int BACKGROUND = 0xFF1D2733, EDGE = 0xFF0B1118, EDGE_LIGHT = 0xFF41566B, INSET = 0xFF28364A, INSET_LIGHT = 0xFF34475C;
    static final int RED = 0xFFD8433B, DARK_RED = 0xFF9E2B26, TEXT = 0xFFF2F5F7, GREY = 0xFF9FB0C0, DARK = 0xFF5D6E80;
    static final int ACCENT = 0xFF4FD1C5, YELLOW = 0xFFF2C93A, GREEN = 0xFF6CCB5F, ALERT = 0xFFE8543A;
    static final String[] BASES = {"A", "T", "C", "G"};
    static final int[] BASE_COLOURS = {0xFF6CCB5F, 0xFFE8543A, 0xFF4A90D9, 0xFFF2C93A};
    // Encres pour écrire sur le papier et le liège du tableau d'enquête.
    static final int INK = 0xFF3A2A1C, INK_LIGHT = 0xFF7D6248, INK_RED = 0xFFA8322A;

    // Un fond d'écran : sa texture, sa taille, et le rectangle utile à l'intérieur (l'écran de l'appareil, le liège du tableau).
    record Backdrop(ResourceLocation texture, int l, int h, int ix, int iy, int il, int ih) {
        int x(int screenWidth) {
            return (screenWidth - l) / 2 + ix;
        }

        int y(int screenHeight) {
            return (screenHeight - h) / 2 + iy;
        }
    }

    static final Backdrop KIT = new Backdrop(texture("bg_kit"), 368, 216, 24, 22, 320, 170);
    static final Backdrop BOARD = new Backdrop(texture("bg_board"), 378, 236, 20, 23, 336, 189);
    static final Backdrop SEQUENCER = new Backdrop(texture("bg_sequencer"), 324, 225, 18, 13, 288, 167);
    static final ResourceLocation PAPER = texture("paper"), POLAROID = texture("polaroid"), PIN = texture("pin"), BUTTON = texture("button"),
            PLATE = texture("plaque"), DIAL = texture("dial"), ROTOR = texture("rotor"), VIAL = texture("vial"),
            TILE = texture("tile"), RADAR = texture("radar"), VIEWFINDER = texture("viewfinder"), INSET_TEXTURE = texture("inset"), CROSS = texture("cross");

    private static final Map<String, ItemStack> PORTRAITS = new HashMap<>();

    private Draw() {}

    static Font textFont() {
        return Minecraft.getInstance().font;
    }

    static int opaque(int rgb) {
        return 0xFF000000 | rgb;
    }

    static int darken(int colour, double factor) {
        int r = (int) (((colour >> 16) & 0xFF) * factor), v = (int) (((colour >> 8) & 0xFF) * factor), b = (int) ((colour & 0xFF) * factor);
        return (colour & 0xFF000000) | (Math.min(255, r) << 16) | (Math.min(255, v) << 8) | Math.min(255, b);
    }

    static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath("cobblemon_field_investigations", "textures/gui/" + name + ".png");
    }

    static void image(GuiGraphics g, ResourceLocation texture, int x, int y, int l, int h) {
        RenderSystem.enableBlend();
        g.blit(texture, x, y, 0, 0, l, h, l, h);
    }

    static void piece(GuiGraphics g, ResourceLocation texture, int x, int y, int u, int v, int l, int h, int tl, int th) {
        RenderSystem.enableBlend();
        g.blit(texture, x, y, u, v, l, h, tl, th);
    }

    static void pieceEnlarged(GuiGraphics g, ResourceLocation texture, int x, int y, int l, int h, int u, int v, int ul, int vh, int tl, int th) {
        RenderSystem.enableBlend();
        g.blit(texture, x, y, l, h, u, v, ul, vh, tl, th);
    }

    static void tint(GuiGraphics g, int colour) {
        g.setColor(((colour >> 16) & 0xFF) / 255f, ((colour >> 8) & 0xFF) / 255f, (colour & 0xFF) / 255f, 1f);
    }

    // Texture découpée en neuf cases : les coins gardent leur taille, les bords et le centre s'étirent.
    static void nineSlice(GuiGraphics g, ResourceLocation texture, int x, int y, int l, int h, int tl, int th, int left, int top, int right, int bottom) {
        RenderSystem.enableBlend();
        int[] xs = {x, x + left, x + l - right}, ls = {left, l - left - right, right}, us = {0, left, tl - right}, ul = {left, tl - left - right, right};
        int[] ys = {y, y + top, y + h - bottom}, hs = {top, h - top - bottom, bottom}, vs = {0, top, th - bottom}, vh = {top, th - top - bottom, bottom};
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                if (ls[i] > 0 && hs[j] > 0) g.blit(texture, xs[i], ys[j], ls[i], hs[j], us[i], vs[j], ul[i], vh[j], tl, th);
            }
        }
    }

    // Fond d'un écran, centré ; l'intérieur utile commence en (fond.x(largeur), fond.y(hauteur)).
    static void background(GuiGraphics g, Backdrop background, int screenWidth, int screenHeight) {
        zone(background.x(screenWidth) + 2, background.x(screenWidth) + background.il() - 2);
        image(g, background.texture(), (screenWidth - background.l()) / 2, (screenHeight - background.h()) / 2, background.l(), background.h());
    }

    // Plaque bleu nuit à liseré d'acier : annonces, instrument du kit, cartouches.
    static void plate(GuiGraphics g, int x, int y, int l, int h) {
        nineSlice(g, PLATE, x, y, l, h, 128, 40, 8, 8, 8, 8);
    }

    // ---------- Texte borné ----------
    // Chaque cadre (fond d'écran, encart, carte) déclare la zone où l'on écrit ensuite. Un texte qui tiendrait en dehors
    // n'est jamais coupé : il défile de lui-même, en aller-retour, dans la largeur qui lui reste.
    private static int zoneLeft = Integer.MIN_VALUE, zoneRight = Integer.MAX_VALUE;

    static void zone(int left, int right) {
        zoneLeft = left;
        zoneRight = right;
    }

    static void withoutZone() {
        zoneLeft = Integer.MIN_VALUE;
        zoneRight = Integer.MAX_VALUE;
    }

    // Écrit un texte dans une largeur donnée ; s'il est trop long, il défile (une seconde de pause à chaque bout).
    static void scroll(GuiGraphics g, String text, int x, int y, int widthPx, int colour) {
        int l = textFont().width(text);
        if (l <= widthPx || widthPx < 12) {
            g.drawString(textFont(), text, x, y, colour, false);
            return;
        }
        Demo.overflow(text, l - widthPx);
        int excess = l - widthPx + 2, route = excess * 45;
        long t = System.currentTimeMillis() % (2000L + 2L * route);
        double pos = t < 1000 ? 0 : t < 1000 + route ? (t - 1000) / 45.0 : t < 2000 + route ? excess : excess - (t - 2000 - route) / 45.0;
        g.enableScissor(x, y - 1, x + widthPx, y + 10);
        g.drawString(textFont(), text, x - (int) Math.round(pos), y, colour, false);
        g.disableScissor();
    }

    // Écrit un texte à cet endroit ; s'il sort de la zone courante par la droite, il défile.
    static void write(GuiGraphics g, String text, int x, int y, int colour, boolean shadow) {
        if (x >= zoneLeft && x < zoneRight) scroll(g, text, x, y, zoneRight - x, colour);
        else g.drawString(textFont(), text, x, y, colour, false);
    }

    static void write(GuiGraphics g, net.minecraft.util.FormattedCharSequence text, int x, int y, int colour, boolean shadow) {
        g.drawString(textFont(), text, x, y, colour, false);
    }

    static void write(GuiGraphics g, Component text, int x, int y, int colour, boolean shadow) {
        g.drawString(textFont(), text, x, y, colour, false);
    }

    static void banner(GuiGraphics g, int x, int y, int l, String title, String right) {
        g.fill(x + 5, y + 4, x + 11, y + 10, RED);
        g.fill(x + 6, y + 5, x + 8, y + 7, 0xFFFFB0A8);
        g.drawString(textFont(), title, x + 15, y + 3, TEXT, false);
        if (right != null) g.drawString(textFont(), right, x + l - 6 - textFont().width(right), y + 3, GREY, false);
        zone(x + 4, x + l - 4);
        g.fill(x + 4, y + 14, x + l - 4, y + 15, EDGE_LIGHT);
    }

    static void inset(GuiGraphics g, int x, int y, int w, int h) {
        zone(x + 4, x + w - 4);
        nineSlice(g, INSET_TEXTURE, x, y, w, h, 88, 54, 5, 5, 5, 5);
    }

    // Dessin de l'objet enfoui : enfoui_<marqueur>.png pour le premier, enfoui_<marqueur>_<n>.png pour les autres.
    static ResourceLocation buriedIcon(String kind, int variant) {
        return texture("buried_" + kind + (variant > 0 && variant < Genetics.VARIANTS.getOrDefault(kind, 1) ? "_" + variant : ""));
    }

    static ResourceLocation traceIcon(String kind, boolean buried) {
        return texture((buried ? "buried_" : "trace_") + kind);
    }

    static void button(GuiGraphics g, int x, int y, int w, int h, String label, boolean hover, boolean active, int colour) {
        int background = !active ? 0xFF4A5868 : hover ? darken(colour, 1.18) : colour;
        tint(g, background);
        nineSlice(g, BUTTON, x, y, w, h, 32, 20, 9, 3, 9, 4);
        g.setColor(1f, 1f, 1f, 1f);
        int lt = textFont().width(label), textTint = active ? (light(background) ? EDGE : TEXT) : 0xFFAEBBC8;
        if (lt > w - 8) scroll(g, label, x + 4, y + 3 + (h - 14) / 2, w - 8, textTint);
        else g.drawString(textFont(), label, x + (w - lt) / 2, y + 3 + (h - 14) / 2, textTint, false);
    }

    private static boolean light(int c) {
        return ((c >> 16) & 0xFF) * 3 + ((c >> 8) & 0xFF) * 6 + (c & 0xFF) > 1500;
    }

    // Puce de couleur avec un libellé : une « bande » du profil génétique.
    static void chip(GuiGraphics g, int x, int y, int w, int h, int rgb, String label) {
        int c = opaque(rgb);
        g.fill(x, y, x + w, y + h, darken(c, 0.45));
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, c);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, darken(c, 1.25));
        if (label != null) scroll(g, label, x + 4, y + (h - 8) / 2, w - 7, light(c) ? EDGE : TEXT);
    }

    // Une base (A, T, C, G) : une touche teintée à sa couleur ; -1 = case encore vide.
    static void base(GuiGraphics g, int x, int y, int size, int b, boolean frame) {
        if (frame) g.fill(x - 1, y - 1, x + size + 1, y + size + 1, TEXT);
        tint(g, b < 0 ? 0xFF56677A : BASE_COLOURS[b]);
        nineSlice(g, TILE, x, y, size, size, 16, 16, 3, 3, 3, 4);
        g.setColor(1f, 1f, 1f, 1f);
        g.drawString(textFont(), b < 0 ? "?" : BASES[b], x + (size - 5) / 2, y + (size - 9) / 2, b < 0 ? 0xFFB4C0CC : EDGE, false);
    }

    // Texte centré ; s'il déborde de la zone courante, il défile sur toute sa largeur.
    static void centre(GuiGraphics g, String text, int cx, int y, int colour) {
        int l = textFont().width(text);
        boolean inside = cx >= zoneLeft && cx < zoneRight;
        if (inside && (cx - l / 2 < zoneLeft || cx + (l + 1) / 2 > zoneRight)) scroll(g, text, zoneLeft, y, zoneRight - zoneLeft, colour);
        else g.drawString(textFont(), text, cx - l / 2, y, colour, false);
    }

    static void point(GuiGraphics g, double x, double y, int size, int colour) {
        int px = (int) Math.round(x), py = (int) Math.round(y);
        g.fill(px, py, px + size, py + size, colour);
    }

    // Segment tracé point par point (pas de lignes lissées : on reste en pixels).
    static void segment(GuiGraphics g, double x1, double y1, double x2, double y2, int size, int colour) {
        double length = Math.max(1, Math.hypot(x2 - x1, y2 - y1));
        for (double t = 0; t <= length; t += Math.max(1, size)) {
            point(g, x1 + (x2 - x1) * t / length, y1 + (y2 - y1) * t / length, size, colour);
        }
    }

    static void circle(GuiGraphics g, double cx, double cy, double radius, int size, int colour) {
        int stepSize = (int) Math.max(12, radius * 3);
        for (int i = 0; i < stepSize; i++) {
            double a = Math.PI * 2 * i / stepSize;
            point(g, cx + Math.cos(a) * radius, cy + Math.sin(a) * radius, size, colour);
        }
    }

    static void portrait(GuiGraphics g, String species, int x, int y, float scale) {
        ItemStack stack = PORTRAITS.computeIfAbsent(species, e -> {
            Species s = PokemonSpecies.getByName(e);
            return s == null ? ItemStack.EMPTY : PokemonItem.from(s, new String[0]);
        });
        if (stack.isEmpty()) return;
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1f);
        g.renderItem(stack, 0, 0);
        g.pose().popPose();
    }

    private static ItemStack pokeBall;

    // Petite Poké Ball (l'objet de Cobblemon, aux trois quarts de sa taille) : marque une espèce que le joueur possède déjà.
    static void pokeBall(GuiGraphics g, int x, int y) {
        if (pokeBall == null) {
            pokeBall = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("cobblemon", "poke_ball")));
        }
        if (pokeBall.isEmpty()) return;
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.75f, 0.75f, 1f);
        g.renderItem(pokeBall, 0, 0);
        g.pose().popPose();
    }

    static String speciesName(String species) {
        Species s = PokemonSpecies.getByName(species);
        return s == null ? species : s.getTranslatedName().getString();
    }

    static void tooltip(GuiGraphics g, List<Component> lines, int mx, int my, int screenWidth) {
        int w = 0;
        for (Component c : lines) w = Math.max(w, textFont().width(c));
        int x = Math.min(mx + 10, screenWidth - w - 10), y = my - 6;
        int h = lines.size() * 10 + 5;
        // L'infobulle passe devant tout, y compris les objets dessinés en relief (portraits, Poké Ball), qui se trouvent
        // entre 400 et 600 de profondeur sur le tableau.
        g.flush();
        g.pose().pushPose();
        g.pose().translate(0, 0, 900);
        plate(g, x - 7, y - 8, w + 14, h + 12);
        for (int i = 0; i < lines.size(); i++) g.drawString(textFont(), lines.get(i), x, y + i * 10, TEXT, false);
        g.pose().popPose();
    }

    static int paragraph(GuiGraphics g, String text, int x, int y, int widthPx, int colour) {
        for (FormattedCharSequence line : textFont().split(Component.literal(text), widthPx)) {
            g.drawString(textFont(), line, x, y, colour, false);
            y += 10;
        }
        return y;
    }

    static String wind(double cap) {
        return T.f("vents").split(",")[(int) Math.round((((cap % 360) + 360) % 360) / 22.5) % 16];
    }

    // Cap boussole (0 = nord, 90 = est) du point (x, z) vu depuis (ox, oz).
    static double cap(double ox, double oz, double x, double z) {
        return (Math.toDegrees(Math.atan2(x - ox, -(z - oz))) + 360) % 360;
    }

    static void sound1(float heightPx) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(Sounds.CLICK, heightPx, 0.8f));
    }

    static void beep(net.minecraft.sounds.SoundEvent sound1, float heightPx, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound1, heightPx, volume));
    }

    static void note(float heightPx) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(Sounds.CHIME, heightPx, 0.6f));
    }
}
