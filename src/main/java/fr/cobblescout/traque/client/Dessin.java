package fr.cobblescout.traque.client;

import fr.cobblescout.traque.Genetique;

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
final class Dessin {
    static final int FOND = 0xFF1D2733, BORD = 0xFF0B1118, BORD_CLAIR = 0xFF41566B, ENCART = 0xFF28364A, ENCART_CLAIR = 0xFF34475C;
    static final int ROUGE = 0xFFD8433B, ROUGE_SOMBRE = 0xFF9E2B26, TEXTE = 0xFFF2F5F7, GRIS = 0xFF9FB0C0, SOMBRE = 0xFF5D6E80;
    static final int ACCENT = 0xFF4FD1C5, JAUNE = 0xFFF2C93A, VERT = 0xFF6CCB5F, ALERTE = 0xFFE8543A;
    static final String[] BASES = {"A", "T", "C", "G"};
    static final int[] COULEURS_BASES = {0xFF6CCB5F, 0xFFE8543A, 0xFF4A90D9, 0xFFF2C93A};
    // Encres pour écrire sur le papier et le liège du tableau d'enquête.
    static final int ENCRE = 0xFF3A2A1C, ENCRE_CLAIRE = 0xFF7D6248, ENCRE_ROUGE = 0xFFA8322A;

    // Un fond d'écran : sa texture, sa taille, et le rectangle utile à l'intérieur (l'écran de l'appareil, le liège du tableau).
    record Fond(ResourceLocation texture, int l, int h, int ix, int iy, int il, int ih) {
        int x(int largeurEcran) {
            return (largeurEcran - l) / 2 + ix;
        }

        int y(int hauteurEcran) {
            return (hauteurEcran - h) / 2 + iy;
        }
    }

    static final Fond KIT = new Fond(texture("fond_kit"), 368, 216, 24, 22, 320, 170);
    static final Fond TABLEAU = new Fond(texture("fond_tableau"), 378, 236, 20, 23, 336, 189);
    static final Fond SEQUENCEUR = new Fond(texture("fond_sequenceur"), 324, 225, 18, 13, 288, 167);
    static final ResourceLocation PAPIER = texture("papier"), POLAROID = texture("polaroid"), EPINGLE = texture("epingle"), BOUTON = texture("bouton"),
            PLAQUE = texture("plaque"), CADRAN = texture("cadran"), ROTOR = texture("rotor"), FIOLE = texture("fiole"),
            TUILE = texture("tuile"), RADAR = texture("radar"), VISEUR = texture("viseur"), ENCART_TEXTURE = texture("encart"), CROIX = texture("croix");

    private static final Map<String, ItemStack> PORTRAITS = new HashMap<>();

    private Dessin() {}

    static Font police() {
        return Minecraft.getInstance().font;
    }

    static int opaque(int rvb) {
        return 0xFF000000 | rvb;
    }

    static int assombrir(int couleur, double facteur) {
        int r = (int) (((couleur >> 16) & 0xFF) * facteur), v = (int) (((couleur >> 8) & 0xFF) * facteur), b = (int) ((couleur & 0xFF) * facteur);
        return (couleur & 0xFF000000) | (Math.min(255, r) << 16) | (Math.min(255, v) << 8) | Math.min(255, b);
    }

    static boolean dans(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    static ResourceLocation texture(String nom) {
        return ResourceLocation.fromNamespaceAndPath("cobblemon_field_investigations", "textures/gui/" + nom + ".png");
    }

    // Texture entière, un texel par pixel d'interface.
    static void image(GuiGraphics g, ResourceLocation texture, int x, int y, int l, int h) {
        RenderSystem.enableBlend();
        g.blit(texture, x, y, 0, 0, l, h, l, h);
    }

    // Un morceau (u, v, l, h) d'une texture de tl x th.
    static void morceau(GuiGraphics g, ResourceLocation texture, int x, int y, int u, int v, int l, int h, int tl, int th) {
        RenderSystem.enableBlend();
        g.blit(texture, x, y, u, v, l, h, tl, th);
    }

    // Un morceau (u, v, ul, vh) d'une texture de tl x th, agrandi à l x h.
    static void morceauAgrandi(GuiGraphics g, ResourceLocation texture, int x, int y, int l, int h, int u, int v, int ul, int vh, int tl, int th) {
        RenderSystem.enableBlend();
        g.blit(texture, x, y, l, h, u, v, ul, vh, tl, th);
    }

    static void teinte(GuiGraphics g, int couleur) {
        g.setColor(((couleur >> 16) & 0xFF) / 255f, ((couleur >> 8) & 0xFF) / 255f, (couleur & 0xFF) / 255f, 1f);
    }

    // Texture découpée en neuf cases : les coins gardent leur taille, les bords et le centre s'étirent.
    static void neufCases(GuiGraphics g, ResourceLocation texture, int x, int y, int l, int h, int tl, int th, int gauche, int haut, int droite, int bas) {
        RenderSystem.enableBlend();
        int[] xs = {x, x + gauche, x + l - droite}, ls = {gauche, l - gauche - droite, droite}, us = {0, gauche, tl - droite}, ul = {gauche, tl - gauche - droite, droite};
        int[] ys = {y, y + haut, y + h - bas}, hs = {haut, h - haut - bas, bas}, vs = {0, haut, th - bas}, vh = {haut, th - haut - bas, bas};
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                if (ls[i] > 0 && hs[j] > 0) g.blit(texture, xs[i], ys[j], ls[i], hs[j], us[i], vs[j], ul[i], vh[j], tl, th);
            }
        }
    }

    // Fond d'un écran, centré ; l'intérieur utile commence en (fond.x(largeur), fond.y(hauteur)).
    static void fond(GuiGraphics g, Fond fond, int largeurEcran, int hauteurEcran) {
        zone(fond.x(largeurEcran) + 2, fond.x(largeurEcran) + fond.il() - 2);
        image(g, fond.texture(), (largeurEcran - fond.l()) / 2, (hauteurEcran - fond.h()) / 2, fond.l(), fond.h());
    }

    // Plaque bleu nuit à liseré d'acier : annonces, instrument du kit, cartouches.
    static void plaque(GuiGraphics g, int x, int y, int l, int h) {
        neufCases(g, PLAQUE, x, y, l, h, 128, 40, 8, 8, 8, 8);
    }

    // ---------- Texte borné ----------
    // Chaque cadre (fond d'écran, encart, carte) déclare la zone où l'on écrit ensuite. Un texte qui tiendrait en dehors
    // n'est jamais coupé : il défile de lui-même, en aller-retour, dans la largeur qui lui reste.
    private static int zoneGauche = Integer.MIN_VALUE, zoneDroite = Integer.MAX_VALUE;

    static void zone(int gauche, int droite) {
        zoneGauche = gauche;
        zoneDroite = droite;
    }

    static void sansZone() {
        zoneGauche = Integer.MIN_VALUE;
        zoneDroite = Integer.MAX_VALUE;
    }

    // Écrit un texte dans une largeur donnée ; s'il est trop long, il défile (une seconde de pause à chaque bout).
    static void defile(GuiGraphics g, String texte, int x, int y, int largeur, int couleur) {
        int l = police().width(texte);
        if (l <= largeur || largeur < 12) {
            g.drawString(police(), texte, x, y, couleur, false);
            return;
        }
        Demo.debordement(texte, l - largeur);
        int exces = l - largeur + 2, trajet = exces * 45;
        long t = System.currentTimeMillis() % (2000L + 2L * trajet);
        double pos = t < 1000 ? 0 : t < 1000 + trajet ? (t - 1000) / 45.0 : t < 2000 + trajet ? exces : exces - (t - 2000 - trajet) / 45.0;
        g.enableScissor(x, y - 1, x + largeur, y + 10);
        g.drawString(police(), texte, x - (int) Math.round(pos), y, couleur, false);
        g.disableScissor();
    }

    // Écrit un texte à cet endroit ; s'il sort de la zone courante par la droite, il défile.
    static void ecrire(GuiGraphics g, String texte, int x, int y, int couleur, boolean ombre) {
        if (x >= zoneGauche && x < zoneDroite) defile(g, texte, x, y, zoneDroite - x, couleur);
        else g.drawString(police(), texte, x, y, couleur, false);
    }

    static void ecrire(GuiGraphics g, net.minecraft.util.FormattedCharSequence texte, int x, int y, int couleur, boolean ombre) {
        g.drawString(police(), texte, x, y, couleur, false);
    }

    static void ecrire(GuiGraphics g, Component texte, int x, int y, int couleur, boolean ombre) {
        g.drawString(police(), texte, x, y, couleur, false);
    }

    // Bandeau de titre en haut de l'écran d'un appareil.
    static void bandeau(GuiGraphics g, int x, int y, int l, String titre, String droite) {
        g.fill(x + 5, y + 4, x + 11, y + 10, ROUGE);
        g.fill(x + 6, y + 5, x + 8, y + 7, 0xFFFFB0A8);
        g.drawString(police(), titre, x + 15, y + 3, TEXTE, false);
        if (droite != null) g.drawString(police(), droite, x + l - 6 - police().width(droite), y + 3, GRIS, false);
        zone(x + 4, x + l - 4);
        g.fill(x + 4, y + 14, x + l - 4, y + 15, BORD_CLAIR);
    }

    static void encart(GuiGraphics g, int x, int y, int w, int h) {
        zone(x + 4, x + w - 4);
        neufCases(g, ENCART_TEXTURE, x, y, w, h, 88, 54, 5, 5, 5, 5);
    }

    // Dessin de l'objet enfoui : enfoui_<marqueur>.png pour le premier, enfoui_<marqueur>_<n>.png pour les autres.
    static ResourceLocation iconeEnfoui(String genre, int variante) {
        return texture("enfoui_" + genre + (variante > 0 && variante < Genetique.VARIANTES.getOrDefault(genre, 1) ? "_" + variante : ""));
    }

    static ResourceLocation iconeTrace(String genre, boolean enfoui) {
        return texture((enfoui ? "enfoui_" : "trace_") + genre);
    }

    static void bouton(GuiGraphics g, int x, int y, int w, int h, String libelle, boolean survol, boolean actif, int couleur) {
        int fond = !actif ? 0xFF4A5868 : survol ? assombrir(couleur, 1.18) : couleur;
        teinte(g, fond);
        neufCases(g, BOUTON, x, y, w, h, 32, 20, 9, 3, 9, 4);
        g.setColor(1f, 1f, 1f, 1f);
        int lt = police().width(libelle), teinteTexte = actif ? (clair(fond) ? BORD : TEXTE) : 0xFFAEBBC8;
        if (lt > w - 8) defile(g, libelle, x + 4, y + 3 + (h - 14) / 2, w - 8, teinteTexte);
        else g.drawString(police(), libelle, x + (w - lt) / 2, y + 3 + (h - 14) / 2, teinteTexte, false);
    }

    private static boolean clair(int c) {
        return ((c >> 16) & 0xFF) * 3 + ((c >> 8) & 0xFF) * 6 + (c & 0xFF) > 1500;
    }

    // Puce de couleur avec un libellé : une « bande » du profil génétique.
    static void puce(GuiGraphics g, int x, int y, int w, int h, int rvb, String libelle) {
        int c = opaque(rvb);
        g.fill(x, y, x + w, y + h, assombrir(c, 0.45));
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, c);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, assombrir(c, 1.25));
        if (libelle != null) defile(g, libelle, x + 4, y + (h - 8) / 2, w - 7, clair(c) ? BORD : TEXTE);
    }

    // Une base (A, T, C, G) : une touche teintée à sa couleur ; -1 = case encore vide.
    static void base(GuiGraphics g, int x, int y, int taille, int b, boolean cadre) {
        if (cadre) g.fill(x - 1, y - 1, x + taille + 1, y + taille + 1, TEXTE);
        teinte(g, b < 0 ? 0xFF56677A : COULEURS_BASES[b]);
        neufCases(g, TUILE, x, y, taille, taille, 16, 16, 3, 3, 3, 4);
        g.setColor(1f, 1f, 1f, 1f);
        g.drawString(police(), b < 0 ? "?" : BASES[b], x + (taille - 5) / 2, y + (taille - 9) / 2, b < 0 ? 0xFFB4C0CC : BORD, false);
    }

    // Texte centré ; s'il déborde de la zone courante, il défile sur toute sa largeur.
    static void centre(GuiGraphics g, String texte, int cx, int y, int couleur) {
        int l = police().width(texte);
        boolean dedans = cx >= zoneGauche && cx < zoneDroite;
        if (dedans && (cx - l / 2 < zoneGauche || cx + (l + 1) / 2 > zoneDroite)) defile(g, texte, zoneGauche, y, zoneDroite - zoneGauche, couleur);
        else g.drawString(police(), texte, cx - l / 2, y, couleur, false);
    }

    static void point(GuiGraphics g, double x, double y, int taille, int couleur) {
        int px = (int) Math.round(x), py = (int) Math.round(y);
        g.fill(px, py, px + taille, py + taille, couleur);
    }

    // Segment tracé point par point (pas de lignes lissées : on reste en pixels).
    static void segment(GuiGraphics g, double x1, double y1, double x2, double y2, int taille, int couleur) {
        double longueur = Math.max(1, Math.hypot(x2 - x1, y2 - y1));
        for (double t = 0; t <= longueur; t += Math.max(1, taille)) {
            point(g, x1 + (x2 - x1) * t / longueur, y1 + (y2 - y1) * t / longueur, taille, couleur);
        }
    }

    static void cercle(GuiGraphics g, double cx, double cy, double rayon, int taille, int couleur) {
        int pas = (int) Math.max(12, rayon * 3);
        for (int i = 0; i < pas; i++) {
            double a = Math.PI * 2 * i / pas;
            point(g, cx + Math.cos(a) * rayon, cy + Math.sin(a) * rayon, taille, couleur);
        }
    }

    static void portrait(GuiGraphics g, String espece, int x, int y, float echelle) {
        ItemStack pile = PORTRAITS.computeIfAbsent(espece, e -> {
            Species s = PokemonSpecies.getByName(e);
            return s == null ? ItemStack.EMPTY : PokemonItem.from(s, new String[0]);
        });
        if (pile.isEmpty()) return;
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(echelle, echelle, 1f);
        g.renderItem(pile, 0, 0);
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

    static String nomEspece(String espece) {
        Species s = PokemonSpecies.getByName(espece);
        return s == null ? espece : s.getTranslatedName().getString();
    }

    static void infobulle(GuiGraphics g, List<Component> lignes, int mx, int my, int largeurEcran) {
        int w = 0;
        for (Component c : lignes) w = Math.max(w, police().width(c));
        int x = Math.min(mx + 10, largeurEcran - w - 10), y = my - 6;
        int h = lignes.size() * 10 + 5;
        // L'infobulle passe devant tout, y compris les objets dessinés en relief (portraits, Poké Ball), qui se trouvent
        // entre 400 et 600 de profondeur sur le tableau.
        g.flush();
        g.pose().pushPose();
        g.pose().translate(0, 0, 900);
        plaque(g, x - 7, y - 8, w + 14, h + 12);
        for (int i = 0; i < lignes.size(); i++) g.drawString(police(), lignes.get(i), x, y + i * 10, TEXTE, false);
        g.pose().popPose();
    }

    static int paragraphe(GuiGraphics g, String texte, int x, int y, int largeur, int couleur) {
        for (FormattedCharSequence ligne : police().split(Component.literal(texte), largeur)) {
            g.drawString(police(), ligne, x, y, couleur, false);
            y += 10;
        }
        return y;
    }

    static String vent(double cap) {
        return T.f("vents").split(",")[(int) Math.round((((cap % 360) + 360) % 360) / 22.5) % 16];
    }

    // Cap boussole (0 = nord, 90 = est) du point (x, z) vu depuis (ox, oz).
    static double cap(double ox, double oz, double x, double z) {
        return (Math.toDegrees(Math.atan2(x - ox, -(z - oz))) + 360) % 360;
    }

    static void son(float hauteur) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(Sons.CLIC, hauteur, 0.8f));
    }

    // Un son d'interface quelconque, à la hauteur et au volume voulus.
    static void bip(net.minecraft.sounds.SoundEvent son, float hauteur, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(son, hauteur, volume));
    }

    static void note(float hauteur) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(Sons.CARILLON, hauteur, 0.6f));
    }
}
