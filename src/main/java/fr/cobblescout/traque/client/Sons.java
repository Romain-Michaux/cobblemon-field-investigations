package fr.cobblescout.traque.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

// Les bruitages propres au mod (assets/cobblemon_field_investigations/sounds, fabriqués par outils/gen_sons.py).
// Ce sont des sons d'interface, joués côté client seulement : ils n'ont pas besoin d'être enregistrés auprès du serveur.
final class Sons {
    static final SoundEvent TAMIS = son("tamis"), PLATRE = son("platre"), DEBORDEMENT = son("debordement"), PINCE = son("pince"),
            PLUCK = son("pluck"), OBTURATEUR = son("obturateur"), FROTTEMENT = son("frottement"), PINCEAU = son("pinceau"),
            FIOLE = son("fiole"), BULLE = son("bulle"), COEUR = son("coeur"), CORDE = son("corde"), MOUSQUETON = son("mousqueton"),
            BRANCHE = son("branche"), CRAQUEMENT = son("craquement"), CHOC = son("choc"), EBOULEMENT = son("eboulement"),
            PIOCHE = son("pioche"), PIERRE = son("pierre"), GOUTTE = son("goutte"), MOLETTE = son("molette"), GLACE = son("glace"),
            FLAMME = son("flamme"), SIFFLEMENT = son("sifflement"), LAMPE = son("lampe"), TIC = son("tic"), CLIC = son("clic"),
            PAPIER = son("papier"), SCAN = son("scan"), CARILLON = son("carillon"), REUSSITE = son("reussite"), ECHEC = son("echec"),
            IDENTIFIE = son("identifie"), GRADE = son("grade");
    private static final ResourceLocation AMBIANCE_LABO = ResourceLocation.fromNamespaceAndPath("cobblemon_field_investigations", "ambiance_labo");
    private static SoundInstance ambiance;

    private Sons() {}

    private static SoundEvent son(String nom) {
        return SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("cobblemon_field_investigations", nom));
    }

    // Nappe sonore du séquenceur, en boucle tant qu'une analyse est ouverte. Elle suit le réglage « Musique » du jeu.
    static void ambianceLabo(boolean active) {
        Minecraft mc = Minecraft.getInstance();
        if (active && ambiance == null) {
            ambiance = new SimpleSoundInstance(AMBIANCE_LABO, SoundSource.MUSIC, 0.5f, 1f, RandomSource.create(), true, 0,
                    SoundInstance.Attenuation.NONE, 0, 0, 0, true);
            mc.getSoundManager().play(ambiance);
        } else if (!active && ambiance != null) {
            mc.getSoundManager().stop(ambiance);
            ambiance = null;
        }
    }
}
