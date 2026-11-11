package fr.cobblescout.traque.client;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import fr.cobblescout.traque.Genetique;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

// Outil de vérification visuelle, inactif en temps normal : si le fichier config/cobblemon_field_investigations_demo existe,
// le client ouvre chaque écran avec de fausses données dès l'entrée dans un monde, enregistre
// screenshots/demo_*.png puis quitte le jeu.
final class Demo {
    static boolean active, scan;
    private static final List<String> NOMS = new ArrayList<>();
    private static final List<Runnable> ETAPES = new ArrayList<>();
    private static int attente = 120, etape = -1, depuis, dureeEtape = 20;

    private Demo() {}

    static void installer() {
        if (!Files.exists(FabricLoader.getInstance().getConfigDir().resolve("cobblemon_field_investigations_demo"))) return;
        active = true;
        Minecraft mc = Minecraft.getInstance();
        // Essai d'endurance du son : si le fichier contient « ambiance », un seul écran, le séquenceur, reste ouvert deux
        // minutes, rouvert toutes les deux secondes sur un autre jeu pendant que des sons d'interface sont joués, puis le jeu se ferme.
        try {
            if (Files.readString(FabricLoader.getInstance().getConfigDir().resolve("cobblemon_field_investigations_demo")).contains("ambiance")) {
                dureeEtape = 2400;
                etape("endurance_son", () -> mc.setScreen(new EcranAnalyse("stat", "titrage", 42L, 2, false, false, false, 0)));
                ClientTickEvents.END_CLIENT_TICK.register(Demo::tick);
                ClientTickEvents.END_CLIENT_TICK.register(c -> {
                    if (etape == 0 && depuis % 7 == 0) Dessin.son(0.9f + (depuis % 5) * 0.12f);
                    if (etape == 0 && depuis % 31 == 0) Dessin.note(0.5f + (depuis % 4) * 0.25f);
                    // Toutes les deux secondes, l'écran est fermé puis rouvert sur un autre jeu.
                    if (etape == 0 && depuis % 40 == 20) c.setScreen(null);
                    if (etape == 0 && depuis % 40 == 0 && depuis > 0) {
                        String[] jeux = {"titrage", "gel", "microscope", "dosage", "parente", "chromatographie"};
                        c.setScreen(new EcranAnalyse("stat", jeux[(depuis / 40) % jeux.length], depuis, 2, false, false, false, 0));
                    }
                });
                return;
            }
        } catch (Exception e) {
            // fichier illisible : démo ordinaire
        }
        etape("tableau_vide", () -> {
            CompoundTag t = new CompoundTag();
            t.putInt("reussies", 3);
            TraqueClient.etat = t;
            mc.setScreen(new EcranTableau());
        });
        etape("tableau_pistes", () -> {
            CompoundTag t = new CompoundTag();
            t.putInt("reussies", 3);
            ListTag pistes = new ListTag();
            String[] speciaux = {"", "souterraine", "fouille"};
            for (int i = 0; i < 3; i++) {
                CompoundTag c = new CompoundTag();
                c.putInt("rang", i);
                c.putString("special", speciaux[i]);
                c.putBoolean("nouvelle", i != 0);
                c.putDouble("zoneX", mc.player.getX() + 110 - i * 90);
                c.putDouble("zoneZ", mc.player.getZ() - 60 + i * 70);
                pistes.add(c);
            }
            t.put("pistes", pistes);
            t.putLong("pistesFin", 540_000);
            TraqueClient.etat = t;
            TraqueClient.etatRecu = System.currentTimeMillis();
            mc.setScreen(new EcranTableau());
        });
        etape("tableau", () -> {
            TraqueClient.etat = etat("PRELEVEMENTS", 3);
            mc.setScreen(new EcranTableau());
        });
        etape("tableau_infobulle", () -> {
            TraqueClient.etat = etat("PRELEVEMENTS", 3);
            mc.setScreen(new EcranTableau());
            // La souris est placée sur un suspect entouré de Poké Ball : l'infobulle doit passer devant elles.
            double echelle = mc.getWindow().getGuiScale();
            int cx = (mc.getWindow().getGuiScaledWidth() - 336) / 2 + 134 + 50 + 20, cy = (mc.getWindow().getGuiScaledHeight() - 189) / 2 + 20;
            org.lwjgl.glfw.GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), cx * echelle, cy * echelle);
        });
        etape("tableau_traque", () -> {
            TraqueClient.etat = etat("TRAQUE", 5);
            mc.setScreen(new EcranTableau());
        });
        String[][] analyses = {{"type1", "sequencage"}, {"type1", "gel"}, {"type2", "appariement"}, {"type2", "pcr"}, {"taille", "centrifugation"},
                {"taille", "comptage"}, {"oeuf", "fragments"}, {"stat", "spectre"}, {"stat", "titrage"}, {"type1", "chromatographie"},
                {"type2", "microscope"}, {"taille", "morsure"}, {"oeuf", "parente"}, {"stat", "dosage"}, {"stat", "degel"}};
        for (String[] a : analyses) {
            etape("analyse_" + a[1], () -> mc.setScreen(new EcranAnalyse(a[0], a[1], 42L, 2, a[1].equals("sequencage"), false, false, 0)));
        }
        String[][] prelevements = {{"type1", "memoire"}, {"type2", "pipette"}, {"taille", "mesure"}, {"oeuf", "tri"}, {"stat", "trace"},
                {"taille", "degagement"}, {"oeuf", "degagement"}, {"stat", "degagement"}, {"oeuf", "tamisage"}, {"type1", "photo"},
                {"type2", "ecouvillon"}, {"taille", "moulage"}, {"oeuf", "pince"}, {"type1", "apnee"}, {"stat", "grimpe"}, {"stat", "rappel"},
                {"type1", "lampe"}, {"type2", "eboulis"}, {"", "filtrage"}};
        for (String[] a : prelevements) {
            boolean enfoui = a[1].equals("degagement") || a[1].equals("tamisage");
            // Les dégagements montrent les objets de base ; le tamisage, un objet ajouté en 1.1 (la plume).
            etape("prelevement_" + a[1] + (enfoui ? "_" + a[0] : ""), () -> mc.setScreen(new EcranAnalyse(a[0], a[1], 7L, 0, false, enfoui, true,
                    a[1].equals("tamisage") ? 1 : 0)));
        }
        etape("kit_prelevements", () -> {
            TraqueClient.etat = etat("PRELEVEMENTS", 2);
            mc.setScreen(new EcranKit());
        });
        etape("kit_carte", () -> {
            TraqueClient.etat = etat("TRAQUE", 5);
            mc.setScreen(new EcranKit());
        });
        etape("kit_reperage", () -> {
            TraqueClient.etat = etat("REPERAGE", 5);
            mc.setScreen(new EcranKit());
        });
        etape("kit_debusque", () -> {
            TraqueClient.etat = etat("DEBUSQUE", 5);
            mc.setScreen(new EcranKit());
        });
        etape("kit_carnet", () -> {
            TraqueClient.etat = etat("PRELEVEMENTS", 2);
            mc.setScreen(new EcranKit(1));
        });
        etape("kit_archives", () -> {
            TraqueClient.etat = etat("PRELEVEMENTS", 2);
            mc.setScreen(new EcranKit(2));
        });
        etape("kit_equipe", () -> {
            CompoundTag t = etat("PRELEVEMENTS", 2);
            ListTag membres = new ListTag(), proches = new ListTag();
            membres.add(StringTag.valueOf("Romain"));
            membres.add(StringTag.valueOf("Camille"));
            String[][] libres = {{"Sacha", "7"}, {"Alex", "3"}, {"Billie", "0"}, {"Charlie", "0"}, {"Dominique", "0"}, {"Eden", "0"}, {"Noa", "0"}};
            for (String[] l : libres) {
                CompoundTag c = new CompoundTag();
                c.putString("nom", l[0]);
                c.putInt("ensemble", Integer.parseInt(l[1]));
                proches.add(c);
            }
            t.put("equipe", membres);
            t.put("proches", proches);
            t.putBoolean("chef", true);
            t.putInt("equipeMax", 4);
            TraqueClient.etat = t;
            mc.setScreen(new EcranKit(3));
        });
        etape("premiere_fois", () -> {
            EcranAnalyse ecran = new EcranAnalyse("oeuf", "tri", 7L, 0, false, false, true, 0);
            ecran.intro = true;
            mc.setScreen(ecran);
        });
        etape("hud", () -> {
            mc.setScreen(null);
            TraqueClient.annonceTitre = net.minecraft.network.chat.Component.translatable("cobblemon_field_investigations.annonce.nouvelle_enquete.titre");
            TraqueClient.annonceTexte = net.minecraft.network.chat.Component.translatable("cobblemon_field_investigations.annonce.nouvelle_enquete.texte");
            TraqueClient.annonceCouleur = 0x4FD1C5;
            TraqueClient.annonceRecue = System.currentTimeMillis();
        });
        etape("scanner", () -> {
            mc.setScreen(null);
            scan = true;
        });
        ClientTickEvents.END_CLIENT_TICK.register(Demo::tick);
    }

    // Textes trop longs pour leur cadre, relevés pendant la démo (ils défilent en jeu) : écran -> textes et pixels en trop.
    private static final java.util.Map<String, Integer> DEBORDEMENTS = new java.util.LinkedHashMap<>();

    static void debordement(String texte, int pixels) {
        if (active && etape >= 0 && etape < NOMS.size()) DEBORDEMENTS.merge(NOMS.get(etape) + "\t" + texte, pixels, Math::max);
    }

    private static void etape(String nom, Runnable action) {
        NOMS.add(String.format("%02d_%s", NOMS.size() + 1, nom));
        ETAPES.add(action);
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) return;
        mc.options.pauseOnLostFocus = false;
        if (etape >= ETAPES.size() - 2) {
            CompoundTag s = new CompoundTag();
            s.putString("phase", scan ? "REPERAGE" : "PRELEVEMENTS");
            s.putInt("distance", 31);
            s.putInt("alerte", 18);
            s.putInt("niveau", 5);
            s.putBoolean("enfoui", true);
            s.putDouble("metres", 3.4);
            TraqueClient.signal = s;
            TraqueClient.signalRecu = System.currentTimeMillis();
        }
        if (attente > 0) {
            attente--;
            return;
        }
        depuis++;
        if (etape < 0 || depuis == dureeEtape) {
            etape++;
            depuis = 0;
            if (etape >= ETAPES.size()) {
                try {
                    StringBuilder sb = new StringBuilder();
                    DEBORDEMENTS.forEach((cle, px) -> sb.append(cle).append("\t+").append(px).append(" px\n"));
                    Files.writeString(mc.gameDirectory.toPath().resolve("screenshots").resolve("demo_debordements.txt"), sb.toString());
                } catch (Exception e) {
                    // ce relevé n'est qu'une aide à la mise au point
                }
                mc.stop();
                return;
            }
            ETAPES.get(etape).run();
        } else if (depuis == 14) {
            Screenshot.grab(mc.gameDirectory, "demo_" + NOMS.get(etape) + ".png", mc.getMainRenderTarget(), c -> {});
        }
    }

    private static CompoundTag etat(String phase, int epingles) {
        CompoundTag t = new CompoundTag();
        t.putBoolean("actif", true);
        t.putInt("reussies", 3);
        t.putString("phase", phase);
        t.putInt("rang", 2);
        Minecraft mc = Minecraft.getInstance();
        double jx = mc.player.getX(), jz = mc.player.getZ();
        t.putDouble("zoneX", jx + 120);
        t.putDouble("zoneZ", jz - 75);
        t.putInt("rayonZone", 45);
        t.putInt("requis", 3);
        t.putString("pronostic", phase.equals("PRELEVEMENTS") ? "" : "vulpix");
        if (phase.equals("TRAQUE")) t.putString("special", "nocturne");
        String[] cible = {"fire", "aucun", "1", "FIELD", "speed"};
        ListTag traces = new ListTag();
        for (int i = 0; i < 5; i++) {
            CompoundTag c = new CompoundTag();
            c.putString("genre", Genetique.MARQUEURS.get(i));
            c.putBoolean("recolte", i <= epingles);
            c.putBoolean("epingle", i < epingles);
            if (i < epingles) c.putString("valeur", cible[i]);
            if (i >= 3 && epingles < 5) {
                c.putBoolean("enfoui", true);
                c.putInt("variante", 1);
                c.putBoolean("perdue", i == 4);
            }
            traces.add(c);
        }
        t.put("traces", traces);
        String[][] fiches = {
                {"mankey", "fighting", "aucun", "1", "FIELD", "attack"}, {"growlithe", "fire", "aucun", "1", "FIELD", "attack"},
                {"rattata", "normal", "aucun", "0", "FIELD", "speed"}, {"pikachu", "electric", "aucun", "1", "FIELD", "speed"},
                {"sandshrew", "ground", "aucun", "1", "FIELD", "defence"}, {"ponyta", "fire", "aucun", "2", "FIELD", "speed"},
                {"zubat", "poison", "flying", "1", "FLYING", "speed"}, {"eevee", "normal", "aucun", "0", "FIELD", "special_defence"},
                {"vulpix", "fire", "aucun", "1", "FIELD", "speed"}, {"charmander", "fire", "aucun", "1", "MONSTER", "speed"},
                {"geodude", "rock", "ground", "1", "MINERAL", "defence"}, {"oddish", "grass", "poison", "1", "GRASS", "special_attack"}};
        ListTag suspects = new ListTag();
        for (String[] f : fiches) {
            CompoundTag c = new CompoundTag();
            c.putString("espece", f[0]);
            ListTag valeurs = new ListTag();
            boolean elimine = false;
            for (int m = 0; m < 5; m++) {
                valeurs.add(StringTag.valueOf(f[m + 1]));
                if (m < epingles && !f[m + 1].equals(cible[m])) elimine = true;
            }
            c.put("valeurs", valeurs);
            c.putBoolean("elimine", elimine);
            c.putBoolean("possede", f[0].equals("charmander") || f[0].equals("pikachu") || f[0].equals("growlithe"));
            suspects.add(c);
        }
        t.put("suspects", suspects);
        ListTag releves = new ListTag();
        double[][] points = {{-20, 30, 52, 9}, {-70, 55, 62, 12}, {25, 95, 22, 7}};
        for (double[] p : points) {
            CompoundTag c = new CompoundTag();
            c.putDouble("x", jx + p[0]);
            c.putDouble("z", jz + p[1]);
            c.putDouble("cap", p[2]);
            c.putDouble("erreur", p[3]);
            releves.add(c);
        }
        t.put("releves", releves);
        t.putBoolean("recValide", true);
        t.putString("recMessage", "");
        t.putDouble("recX", jx + 110);
        t.putDouble("recZ", jz - 60);
        t.putDouble("recIncertitude", 60);
        t.putString("espece", "vulpix");
        t.putInt("etoiles", phase.equals("PRELEVEMENTS") ? 4 : 3);
        CompoundTag carriere = new CompoundTag();
        carriere.putInt("xp", 395);
        carriere.putInt("rang", 3);
        carriere.putInt("seuil", 320);
        carriere.putInt("suivant", 560);
        carriere.putInt("parfaites", 2);
        carriere.putInt("speciales", 3);
        carriere.putInt("especes", 6);
        CompoundTag types = new CompoundTag();
        types.putInt("ordinaire", 4);
        types.putInt("nocturne", 1);
        types.putInt("aquatique", 1);
        types.putInt("cimes", 1);
        carriere.put("types", types);
        ListTag archives = new ListTag();
        String[][] dossiers = {{"vulpix", "", "5"}, {"magikarp", "aquatique", "4"}, {"zubat", "nocturne", "3"}, {"oddish", "", "5"},
                {"pikachu", "cimes", "2"}, {"geodude", "", "4"}, {"eevee", "", "3"}};
        for (int i = 0; i < dossiers.length; i++) {
            CompoundTag c = new CompoundTag();
            c.putString("espece", dossiers[i][0]);
            c.putString("special", dossiers[i][1]);
            c.putInt("etoiles", Integer.parseInt(dossiers[i][2]));
            c.putBoolean("capture", i % 3 != 2);
            c.putLong("date", System.currentTimeMillis() - i * 86_400_000L);
            archives.add(c);
        }
        carriere.put("archives", archives);
        t.put("carriere", carriere);
        return t;
    }
}
