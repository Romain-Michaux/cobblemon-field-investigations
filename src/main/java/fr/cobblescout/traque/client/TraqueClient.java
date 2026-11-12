package fr.cobblescout.traque.client;

import java.util.List;

import fr.cobblescout.traque.Contenu;
import fr.cobblescout.traque.Paquet;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

// Côté client : reçoit l'état de l'enquête, ouvre les écrans, dessine l'instrument du kit et les annonces à l'écran.
public class TraqueClient implements ClientModInitializer {
    static CompoundTag etat = new CompoundTag();
    static long etatRecu;
    static CompoundTag signal = new CompoundTag();
    static long signalRecu;
    static CompoundTag releve = new CompoundTag();
    static long releveRecu;
    static Component annonceTitre, annonceTexte;
    static int annonceCouleur;
    static long annonceRecue;
    // Réglage propre à ce joueur (config/cobblemon_field_investigations_client.json) : chiffrer les teintes des mini-jeux de couleur.
    static boolean aideCouleurs;

    private static java.nio.file.Path fichierReglages() {
        return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("cobblemon_field_investigations_client.json");
    }

    private static void chargerReglages() {
        try {
            if (!java.nio.file.Files.exists(fichierReglages())) return;
            var json = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(fichierReglages())).getAsJsonObject();
            aideCouleurs = json.has("colourAid") && json.get("colourAid").getAsBoolean();
        } catch (Exception e) {
            // fichier illisible : on garde les réglages par défaut
        }
    }

    static void basculerAideCouleurs() {
        aideCouleurs = !aideCouleurs;
        try {
            var json = new com.google.gson.JsonObject();
            json.addProperty("colourAid", aideCouleurs);
            java.nio.file.Files.writeString(fichierReglages(), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(json));
        } catch (Exception e) {
            // le réglage vaudra au moins pour cette session
        }
    }

    @Override
    public void onInitializeClient() {
        chargerReglages();
        BlockRenderLayerMap.INSTANCE.putBlock(Contenu.TRACE, RenderType.translucent());
        BlockRenderLayerMap.INSTANCE.putBlock(Contenu.SOL, RenderType.cutout());
        ClientPlayNetworking.registerGlobalReceiver(Paquet.TYPE, (paquet, ctx) -> {
            CompoundTag d = paquet.donnees();
            Minecraft mc = ctx.client();
            mc.execute(() -> {
                long maintenant = System.currentTimeMillis();
                switch (paquet.genre()) {
                    case "etat" -> {
                        // La cible vient d'être identifiée, ou un rang vient d'être gagné : le client le souligne d'un son.
                        if (etat.getString("phase").equals("REPERAGE") && d.getString("phase").equals("APPROCHE")) Dessin.bip(Sons.IDENTIFIE, 1f, 0.9f);
                        if (etat.contains("carriere") && d.getCompound("carriere").getInt("rang") > etat.getCompound("carriere").getInt("rang")) Dessin.bip(Sons.GRADE, 1f, 0.9f);
                        etat = d;
                        etatRecu = maintenant;
                        Contenu.scanCoteClient = d.getBoolean("actif") && d.getString("phase").equals("REPERAGE");
                    }
                    case "signal" -> {
                        signal = d;
                        signalRecu = maintenant;
                    }
                    case "releve" -> {
                        releve = d;
                        releveRecu = maintenant;
                    }
                    case "annonce" -> {
                        if (mc.level == null) return;
                        annonceTitre = Component.Serializer.fromJson(d.getString("titre"), mc.level.registryAccess());
                        annonceTexte = Component.Serializer.fromJson(d.getString("texte"), mc.level.registryAccess());
                        annonceCouleur = d.getInt("couleur");
                        annonceRecue = maintenant;
                    }
                    case "ouvrir" -> {
                        switch (d.getString("ecran")) {
                            case "tableau" -> mc.setScreen(new EcranTableau());
                            case "kit" -> mc.setScreen(new EcranKit());
case "analyse" -> jouer(mc, new EcranAnalyse(d.getString("genre"), d.getString("jeu"), d.getLong("graine"),
                                    d.getInt("doses"), d.getBoolean("filet"), d.getBoolean("enfoui"), false, d.getInt("variante")), d, false);
                            case "prelevement" -> jouer(mc, new EcranAnalyse(d.getString("genre"), d.getString("jeu"), d.getLong("graine"),
                                    0, false, d.getBoolean("enfoui"), true, d.getInt("variante")), d, false);
                            case "essai" -> {
                                boolean terrain = d.getBoolean("terrain");
                                jouer(mc, new EcranAnalyse(d.getString("genre"), d.getString("jeu"), d.getLong("graine"), terrain ? 0 : 2,
                                        false, d.getBoolean("enfoui"), terrain, d.getInt("variante")), d, true);
                            }
                            case "filtrage" -> jouer(mc, new EcranAnalyse("", "filtrage", d.getLong("graine"), 0, false, false, true, 0), d, false);
                            default -> {}
                        }
                    }
                    default -> {}
                }
            });
        });
        Demo.installer();
        HudRenderCallback.EVENT.register((g, delta) -> {
            try {
                hud(g);
            } catch (Exception e) {
                // un souci d'affichage ne doit jamais faire planter le jeu
            }
        });
    }

    // Ouvre un mini-jeu ; la première fois que le joueur le voit, l'écran commence par en expliquer les règles.
    private static void jouer(Minecraft mc, EcranAnalyse ecran, CompoundTag d, boolean essai) {
        ecran.essai = essai;
        ecran.intro = d.getBoolean("premiere");
        mc.setScreen(ecran);
    }

    // Vrai tant que le joueur maintient le clic droit du kit sur une cible à identifier.
    public static boolean scanActif() {
        Minecraft mc = Minecraft.getInstance();
        return Demo.scan || (mc.player != null && Contenu.scanCoteClient && mc.player.isUsingItem() && mc.player.getUseItem().is(Contenu.KIT));
    }

    private static double scanLisse;
    private static long prochainBip, dernierRefus;

    // Un Pokémon est-il dans le viseur ? (même calcul que côté serveur, pour réagir sans attendre sa réponse)
    private static boolean pokemonVise(Minecraft mc) {
        if (mc.level == null || mc.player == null) return false;
        Vec3 oeil = mc.player.getEyePosition(), regard = mc.player.getLookAngle();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof PokemonEntity)) continue;
            Vec3 vers = e.getBoundingBox().getCenter().subtract(oeil);
            double dist = vers.length();
            if (dist < 0.5 || dist > 64) continue;
            double angle = Math.acos(Math.max(-1, Math.min(1, regard.dot(vers.normalize()))));
            if (angle <= Math.atan((Math.max(e.getBbWidth(), e.getBbHeight()) / 2 + 0.6) / dist) + Math.toRadians(2.5) && mc.player.hasLineOfSight(e)) return true;
        }
        return false;
    }

    // Appelé à la place de toute l'interface du jeu (voir HudScanMixin) : vrai si la vue du scanner a été dessinée.
    public static boolean dessinerScanner(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || !scanActif()) return false;
        try {
            scanner(g, mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), System.currentTimeMillis());
        } catch (Exception e) {
            return false; // un souci d'affichage ne doit jamais faire planter le jeu
        }
        return true;
    }

    // Vue du scanner : l'écran s'assombrit autour d'une fenêtre de visée, le viseur change de couleur selon ce qu'il
    // accroche et un anneau se remplit pendant les trois secondes d'analyse.
    private static void scanner(GuiGraphics g, Minecraft mc, int l, int h, long maintenant) {
        Dessin.sansZone();
        boolean frais = maintenant - signalRecu < 1500 && signal.getString("phase").equals("REPERAGE");
        int scan = Demo.scan ? 3 : frais ? signal.getInt("scan") : 0, distance = frais ? signal.getInt("distance") : 0;
        boolean autre = frais && signal.getBoolean("autre"), tropPres = frais && distance < signal.getInt("alerte") + 6;
        boolean vise = scan > 0 || pokemonVise(mc), clignote = (maintenant / 250) % 2 == 0;
        // Le scan dure 6 demi-secondes, ou 4 avec le scan rapide du rang d'inspecteur.
        double dureeScan = frais && signal.getInt("scanMax") > 0 ? signal.getInt("scanMax") : 6;
        scanLisse += (Math.min(1, scan / dureeScan) - scanLisse) * 0.12;
        // Bips du scanner : de plus en plus rapprochés et aigus à mesure que l'analyse avance ; un bourdonnement grave si ce n'est pas la cible.
        if (!Demo.scan && scan > 0 && maintenant >= prochainBip) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(Sons.SCAN, (float) (0.75 + scanLisse * 1.25), 0.5f));
            prochainBip = maintenant + (long) (400 - scanLisse * 280);
        } else if (!Demo.scan && autre && maintenant - dernierRefus > 1200) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(Sons.ECHEC, 0.6f, 0.5f));
            dernierRefus = maintenant;
        }
        // La fenêtre de visée occupe environ 60 % de l'écran, quelle que soit sa taille.
        int cx = l / 2, cy = h / 2, vl = Math.min(l / 2 - 16, Math.max(150, l * 3 / 10)), vh = Math.min(h / 2 - 34, Math.max(86, h * 3 / 10));
        int couleur = autre ? Dessin.ALERTE : scan > 0 ? Dessin.VERT : vise ? Dessin.JAUNE : Dessin.ACCENT;
        g.fill(0, 0, l, cy - vh, 0xC0060A0E);
        g.fill(0, cy + vh, l, h, 0xC0060A0E);
        g.fill(0, cy - vh, cx - vl, cy + vh, 0x90060A0E);
        g.fill(cx + vl, cy - vh, l, cy + vh, 0x90060A0E);
        g.fill(cx - vl, cy - vh, cx + vl, cy + vh, 0x1830D8C8);
        int by = cy - vh + (int) ((maintenant % 1800) / 1800.0 * (2 * vh));
        g.fill(cx - vl, by, cx + vl, by + 1, 0x5060F0D0);
        g.fill(cx - vl, by + 1, cx + vl, by + 4, 0x1860F0D0);
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sy = -1; sy <= 1; sy += 2) {
                int x = cx + sx * vl, y = cy + sy * vh;
                g.fill(Math.min(x, x - sx * 14), sy < 0 ? y : y - 2, Math.max(x, x - sx * 14), sy < 0 ? y + 2 : y, couleur);
                g.fill(sx < 0 ? x : x - 2, Math.min(y, y - sy * 14), sx < 0 ? x + 2 : x, Math.max(y, y - sy * 14), couleur);
            }
        }
        Dessin.teinte(g, couleur);
        Dessin.image(g, Dessin.VISEUR, cx - 48, cy - 48, 96, 96);
        g.setColor(1f, 1f, 1f, 1f);
        for (int i = 0; i < 48; i++) {
            double a = Math.PI * 2 * i / 48;
            boolean plein = i / 48.0 < scanLisse;
            Dessin.point(g, cx - 1 + Math.sin(a) * 56, cy - 1 - Math.cos(a) * 56, plein ? 3 : 1, plein ? Dessin.VERT : 0x80FFFFFF);
        }
        g.fill(cx - 1, cy - 1, cx + 1, cy + 1, 0xFFFFFFFF);
        // Bandeau du haut : nom de l'instrument et distance ; bandeau du bas : ce que voit le scanner.
        Dessin.ecrire(g, T.f("scanner"), cx - vl, cy - vh - 13, Dessin.ACCENT, false);
        if (frais) {
            String d = distance + " m";
            Dessin.ecrire(g, d, cx + vl - Dessin.police().width(d), cy - vh - 13, tropPres && clignote ? Dessin.ALERTE : Dessin.TEXTE, false);
        }
        String etat = autre ? T.f("this_is_not_your_target") : tropPres ? T.f("too_close_back_off") : scan > 0 ? T.f("scanning") + Math.round(scanLisse * 100) + " %"
                : vise ? T.f("locking_on") : T.f("aim_at_the_pok_mon");
        Dessin.centre(g, etat, cx, cy + vh + 6, autre || (tropPres && clignote) ? Dessin.ALERTE : couleur);
    }

    static void envoyer(Paquet p) {
        if (ClientPlayNetworking.canSend(Paquet.TYPE)) ClientPlayNetworking.send(p);
    }

    private static void hud(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        Dessin.sansZone();
        long maintenant = System.currentTimeMillis();
        int l = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();

        long age = maintenant - annonceRecue;
        if (age < 5500 && annonceTitre != null && annonceTexte != null) {
            String titre = annonceTitre.getString();
            List<FormattedCharSequence> lignes = Dessin.police().split(annonceTexte, 250);
            int w = Dessin.police().width(titre);
            for (FormattedCharSequence s : lignes) w = Math.max(w, Dessin.police().width(s));
            w += 30;
            int haut = 16 + lignes.size() * 10;
            double glisse = age < 250 ? age / 250.0 : age > 5200 ? (5500 - age) / 300.0 : 1;
            int x = (l - w) / 2, y = (int) (-haut + (haut + 8) * glisse);
            int couleur = Dessin.opaque(annonceCouleur);
            Dessin.plaque(g, x - 5, y - 6, w + 10, haut + 11);
            g.fill(x, y + 1, x + 4, y + haut - 1, couleur);
            Dessin.ecrire(g, titre, x + 11, y + 4, couleur, false);
            for (int i = 0; i < lignes.size(); i++) Dessin.ecrire(g, lignes.get(i), x + 11, y + 15 + i * 10, Dessin.TEXTE, false);
        }

        // Instrument du kit de terrain, au-dessus de la barre d'objets : un détecteur avec son écran sonar (à gauche)
        // et son afficheur (à droite). La texture vient de PixelLab ; le balayage, l'écho et le texte sont dessinés par-dessus.
        if (!(mc.player.isHolding(Contenu.KIT) || Demo.active) || mc.screen != null || maintenant - signalRecu > 1500) return;
        String phase = signal.getString("phase");
        int x = (l - 200) / 2, y = h - 100;
        Dessin.image(g, Dessin.RADAR, x, y, 200, 48);
        int cx = x + 33, cy = y + 24, ex = x + 57, ey = y + 15;
        String ligne1, ligne2 = "";
        int couleur1 = Dessin.TEXTE, couleur2 = Dessin.GRIS;
        double balayage = (maintenant % 2000) / 2000.0 * Math.PI * 2;
        boolean aiguille = false;
        switch (phase) {
            case "PRELEVEMENTS" -> {
                int niveau = Math.min(5, signal.getInt("niveau"));
                String[] libelles = {T.f("no_sign"), T.f("faint_signs"), T.f("clear_signs"), T.f("fresh_signs"), T.f("trace_very_close"), T.f("right_here")};
                int[] couleurs = {Dessin.GRIS, Dessin.GRIS, Dessin.JAUNE, 0xFFE9A15B, Dessin.VERT, Dessin.ACCENT};
                if (signal.getBoolean("jour")) {
                    // Enquête nocturne, en plein jour : il n'y a rien à relever.
                    ligne1 = T.f("wait_for_nightfall");
                    couleur1 = 0xFF8FA8FF;
                    ligne2 = T.f("no_traces_by_day");
                } else if (niveau < 0) {
                    ligne1 = T.f("all_sampled");
                    couleur1 = Dessin.VERT;
                } else {
                    ligne1 = libelles[niveau];
                    couleur1 = couleurs[niveau];
                    if (signal.getBoolean("grotte") && signal.contains("metres")) {
                        // Enquête souterraine : la trace est dans une grotte, à cette distance et tant de blocs plus bas (ou plus haut).
                        int dessous = signal.getInt("dessous");
                        ligne2 = T.f("underground_2") + String.format("%.0f", signal.getDouble("metres")) + " m" + (dessous > 1 ? " ▼" + dessous : dessous < -1 ? " ▲" + -dessous : "");
                        couleur2 = 0xFFD0A070;
                    } else if (signal.getBoolean("enfoui") && signal.contains("metres")) {
                        ligne2 = T.f("buried") + String.format("%.1f", signal.getDouble("metres")) + " m";
                        couleur2 = Dessin.JAUNE;
                    } else if (signal.getBoolean("enfoui")) {
                        ligne2 = T.f("buried_clue");
                        couleur2 = Dessin.JAUNE;
                    } else if (niveau == 0) {
                        ligne2 = T.f("zone_at") + signal.getInt("zone") + T.f("blocks_2");
                    }
                    // Force du signal : six barres au bord de l'afficheur, et un écho qui grossit et bat plus vite sur le sonar.
                    for (int i = 0; i < 6; i++) {
                        int hb = 3 + i * 2;
                        g.fill(x + 152 + i * 4, y + 32 - hb, x + 155 + i * 4, y + 32, i <= niveau ? couleurs[niveau] : 0xFF12303A);
                    }
                    if (niveau > 0) {
                        long periode = 1300 - niveau * 200L;
                        double battement = (maintenant % periode) / (double) periode;
                        int rayon = 1 + (int) Math.round(battement * (2 + niveau * 1.4));
                        Dessin.cercle(g, cx - 0.5, cy - 0.5, rayon, 1, Dessin.assombrir(0xFF6CF0A0, 1 - battement * 0.6));
                        g.fill(cx - 1, cy - 1, cx + 1, cy + 1, 0xFFB8FFD0);
                    }
                }
            }
            case "TRAQUE" -> {
                if (maintenant - releveRecu < 9000 && releve.contains("cap")) {
                    double cap = releve.getDouble("cap");
                    // Aiguille relative au regard du joueur : elle tourne quand il tourne la tête.
                    balayage = Math.toRadians(cap - (mc.player.getYRot() + 180));
                    aiguille = true;
                    String[] forces = {T.f("very_far"), T.f("far"), T.f("clear"), T.f("close")};
                    ligne1 = T.f("cry") + forces[Math.min(3, releve.getInt("force"))];
                    couleur1 = Dessin.ACCENT;
                    ligne2 = Dessin.vent(cap) + " · " + Math.round(cap) + "° ± " + Math.round(releve.getDouble("erreur")) + "°";
                    couleur2 = Dessin.TEXTE;
                } else if (signal.getBoolean("jour")) {
                    ligne1 = T.f("wait_for_nightfall");
                    couleur1 = 0xFF8FA8FF;
                    ligne2 = T.f("target_asleep_no_cry");
                } else {
                    ligne1 = signal.getBoolean("pret") ? T.f("right_click_listen") : T.f("recalibrating");
                    ligne2 = T.f("sneak_map");
                }
            }
            case "REPERAGE" -> {
                // La cible est en vue : il faut la scanner de loin (clic droit maintenu, viseur dessus) avant d'approcher.
                int scan = signal.getInt("scan"), distance = signal.getInt("distance");
                boolean tropPres = distance < signal.getInt("alerte") + 6, clignote = (maintenant / 250) % 2 == 0;
                ligne1 = scan > 0 ? T.f("scanning") + Math.min(100, scan * 100 / Math.max(1, signal.getInt("scanMax") > 0 ? signal.getInt("scanMax") : 6)) + " %" : T.f("scan_the_target");
                couleur1 = scan > 0 ? Dessin.ACCENT : Dessin.JAUNE;
                ligne2 = signal.getBoolean("autre") ? T.f("not_your_target") : tropPres ? T.f("too_close_back_off") : T.f("right_click") + distance + " m";
                couleur2 = signal.getBoolean("autre") || (tropPres && clignote) ? Dessin.ALERTE : Dessin.GRIS;
                for (int i = 0; i < scan; i++) Dessin.cercle(g, cx - 0.5, cy - 0.5, 2 + i * 1.4, 1, 0xFF6CF0A0);
            }
            case "APPROCHE" -> {
                boolean course = signal.getBoolean("course");
                boolean clignote = (maintenant / 250) % 2 == 0;
                ligne1 = T.f("target_at") + signal.getInt("distance") + T.f("blocks_2");
                couleur1 = Dessin.JAUNE;
                ligne2 = course ? T.f("don_t_run") : T.f("approach_quietly");
                couleur2 = course && clignote ? Dessin.ALERTE : Dessin.GRIS;
            }
            default -> {
                ligne1 = T.f("flushed_out_3");
                couleur1 = Dessin.VERT;
                ligne2 = T.f("catch_it");
            }
        }
        Dessin.segment(g, cx - 0.5, cy - 0.5, cx - 0.5 + Math.sin(balayage) * 8, cy - 0.5 - Math.cos(balayage) * 8, 1, aiguille ? Dessin.JAUNE : 0xFF3EA162);
        int place = ligne1.equals(T.f("all_sampled")) || signal.getBoolean("jour") || !phase.equals("PRELEVEMENTS") ? 119 : 93;
        Dessin.defile(g, ligne1, ex, ey, place, couleur1);
        Dessin.defile(g, ligne2, ex, ey + 9, place, couleur2);
    }
}
