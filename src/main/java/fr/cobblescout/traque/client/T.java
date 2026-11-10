package fr.cobblescout.traque.client;

import net.minecraft.client.resources.language.I18n;
import net.minecraft.locale.Language;

// Textes de l'interface : tout passe par les fichiers de langue du mod (anglais par défaut, français fourni).
final class T {
    private T() {}

    // Morceau de texte de l'interface (clé cobblemon_field_investigations.ui.<nom>), rendu tel quel.
    static String f(String nom) {
        return Language.getInstance().getOrDefault("cobblemon_field_investigations.ui." + nom);
    }

    // Clé complète (noms de traces, de marqueurs, de mini-jeux fournis par Genetique, messages du serveur).
    static String c(String cle) {
        return Language.getInstance().getOrDefault(cle);
    }

    // Texte à trous (%s) : clé cobblemon_field_investigations.<nom>.
    static String t(String nom, Object... args) {
        return I18n.get("cobblemon_field_investigations." + nom, args);
    }

    // IV parfaits accordés pour ce nombre d'étoiles (même règle que le serveur : aucun à 1 étoile, 4 au mieux).
    static int iv(int etoiles) {
        return Math.max(0, Math.min(4, etoiles - 1));
    }

    static String etoiles(int n) {
        return "★".repeat(Math.max(0, Math.min(5, n))) + "☆".repeat(Math.max(0, 5 - n));
    }
}
