package fr.cobblescout.traque.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

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
    private Sons() {}

    private static SoundEvent son(String nom) {
        return SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("cobblemon_field_investigations", nom));
    }
}
