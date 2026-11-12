package io.github.romainmichaux.fieldinvestigations.client;

import net.minecraft.client.resources.language.I18n;
import net.minecraft.locale.Language;

// Textes de l'interface : tout passe par les fichiers de langue du mod (anglais par défaut, français fourni).
final class T {
    private T() {}

    // Morceau de texte de l'interface (clé cobblemon_field_investigations.ui.<nom>), rendu tel quel.
    static String f(String name) {
        return Language.getInstance().getOrDefault("cobblemon_field_investigations.ui." + name);
    }

    // Clé complète (noms de traces, de marqueurs, de mini-jeux fournis par Genetique, messages du serveur).
    static String c(String key) {
        return Language.getInstance().getOrDefault(key);
    }

    // Texte à trous (%s) : clé cobblemon_field_investigations.<nom>.
    static String t(String name, Object... args) {
        return I18n.get("cobblemon_field_investigations." + name, args);
    }

    // IV parfaits accordés pour ce nombre d'étoiles (même règle que le serveur : aucun à 1 étoile, 4 au mieux).
    static int iv(int stars) {
        return Math.max(0, Math.min(4, stars - 1));
    }

    static String stars(int n) {
        return "★".repeat(Math.max(0, Math.min(5, n))) + "☆".repeat(Math.max(0, 5 - n));
    }
}
