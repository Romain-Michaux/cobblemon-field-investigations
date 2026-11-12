package io.github.romainmichaux.fieldinvestigations.client;

import net.minecraft.client.resources.language.I18n;
import net.minecraft.locale.Language;

// UI text: everything goes through the mod's lang files (English by default, French provided).
final class T {
    private T() {}

    // UI text fragment (key cobblemon_field_investigations.ui.<name>), returned as is.
    static String f(String name) {
        return Language.getInstance().getOrDefault("cobblemon_field_investigations.ui." + name);
    }

    // Full key (trace, marker and mini-game names supplied by Genetics, server messages).
    static String c(String key) {
        return Language.getInstance().getOrDefault(key);
    }

    // Text with %s placeholders: key cobblemon_field_investigations.<name>.
    static String t(String name, Object... args) {
        return I18n.get("cobblemon_field_investigations." + name, args);
    }

    // Perfect IVs granted for this star count (same rule as the server: none at 1 star, 4 at most).
    static int iv(int stars) {
        return Math.max(0, Math.min(4, stars - 1));
    }

    static String stars(int n) {
        return "★".repeat(Math.max(0, Math.min(5, n))) + "☆".repeat(Math.max(0, 5 - n));
    }
}
