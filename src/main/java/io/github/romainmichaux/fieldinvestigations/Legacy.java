package io.github.romainmichaux.fieldinvestigations;

import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

// Up to 1.1.0 the code, the saved state and the sample items used French names. Saves written by those builds are
// translated on load: the state file once at server start (then saved again in English), a sample item each time
// its data is read. Generated from the git history of the rename; do not edit by hand.
final class Legacy {
    private Legacy() {}

    // State JSON field names.
    static final Map<String, String> FIELDS = Map.ofEntries(
            Map.entry("annonceApproche", "announceApproach"), Map.entry("brossage", "brushing"), Map.entry("cap", "heading"), Map.entry("categorie", "category"),
            Map.entry("chef", "leader"), Map.entry("degage", "uncovered"), Map.entry("dossiers", "caseFiles"), Map.entry("ecartes", "ruledOut"),
            Map.entry("enfoui", "buried"), Map.entry("entite", "entity"), Map.entry("epingles", "pins"), Map.entry("equipe", "team"),
            Map.entry("erreur", "error"), Map.entry("espece", "species"), Map.entry("etoiles", "stars"), Map.entry("fautes", "faults"),
            Map.entry("finLueur", "glowEnd"), Map.entry("finOffres", "offersEnd"), Map.entry("fioleOfferte", "spareVialUsed"), Map.entry("fuites", "flees"),
            Map.entry("genre", "kind"), Map.entry("graine", "seed"), Map.entry("grotte", "cave"), Map.entry("historique", "history"),
            Map.entry("jeuxVus", "gamesSeen"), Map.entry("monde", "dimension"), Map.entry("niveau", "level"), Map.entry("nouvelle", "newSpecies"),
            Map.entry("offres", "offers"), Map.entry("origine", "origin"), Map.entry("pardon", "forgiven"), Map.entry("partenaires", "partners"),
            Map.entry("perdue", "lost"), Map.entry("perime", "expired"), Map.entry("posee", "placed"), Map.entry("prochainDeplacement", "nextMove"),
            Map.entry("prochainReleve", "nextBearing"), Map.entry("prochaineEnquete", "nextInvestigation"), Map.entry("profils", "profiles"), Map.entry("pronostic", "guess"),
            Map.entry("recolte", "sampled"), Map.entry("releves", "bearings"), Map.entry("reussies", "succeeded"), Map.entry("taniereX", "lairX"),
            Map.entry("taniereZ", "lairZ"), Map.entry("variante", "variant"));

    // NBT keys and id values (phases, kinds of investigation, markers, mini-game ids).
    static final Map<String, String> VALUES = Map.ofEntries(
            Map.entry("APPROCHE", "APPROACH"), Map.entry("DEBUSQUE", "FLUSHED"), Map.entry("PRELEVEMENTS", "SAMPLING"), Map.entry("REPERAGE", "SPOTTING"),
            Map.entry("TRAQUE", "TRACKING"), Map.entry("altitude", "highlands"), Map.entry("apnee", "dive"), Map.entry("appariement", "pairing"),
            Map.entry("aquatique", "aquatic"), Map.entry("brume", "mist"), Map.entry("chromatographie", "chromatography"), Map.entry("cimes", "canopy"),
            Map.entry("comptage", "count"), Map.entry("craintive", "skittish"), Map.entry("degagement", "brush"), Map.entry("degel", "thaw"),
            Map.entry("dosage", "titration"), Map.entry("eboulis", "rockfall"), Map.entry("ecouvillon", "swab"), Map.entry("ecran", "screen"),
            Map.entry("enfoui", "buried"), Map.entry("enquete", "investigation"), Map.entry("exceptionnel", "exceptional"), Map.entry("filtrage", "filter"),
            Map.entry("fouille", "dig_site"), Map.entry("genre", "kind"), Map.entry("givre", "frost"), Map.entry("graine", "seed"),
            Map.entry("grimpe", "climb"), Map.entry("jeu", "game"), Map.entry("lampe", "lamp"), Map.entry("memoire", "crystals"),
            Map.entry("mesure", "measure"), Map.entry("migratrice", "restless"), Map.entry("morsure", "bite"), Map.entry("moulage", "cast"),
            Map.entry("nocturne", "nocturnal"), Map.entry("oeuf", "egg"), Map.entry("parente", "family_tree"), Map.entry("pince", "tweezers"),
            Map.entry("prelevement", "sampling"), Map.entry("premiere", "first"), Map.entry("rappel", "abseil"), Map.entry("sequencage", "sequencing"),
            Map.entry("souterraine", "underground"), Map.entry("spectre", "spectrum"), Map.entry("taille", "size"), Map.entry("tamisage", "sieving"),
            Map.entry("titrage", "colorimetry"), Map.entry("tri", "sorting"), Map.entry("valeur", "value"), Map.entry("variante", "variant"));

    static JsonElement state(JsonElement e) {
        if (e instanceof JsonObject o) {
            JsonObject out = new JsonObject();
            for (Map.Entry<String, JsonElement> en : o.entrySet()) out.add(FIELDS.getOrDefault(en.getKey(), en.getKey()), state(en.getValue()));
            return out;
        }
        if (e instanceof JsonArray a) {
            JsonArray out = new JsonArray();
            for (JsonElement x : a) out.add(state(x));
            return out;
        }
        if (e instanceof JsonPrimitive p && p.isString() && VALUES.containsKey(p.getAsString())) return new JsonPrimitive(VALUES.get(p.getAsString()));
        return e;
    }

    static CompoundTag item(CompoundTag tag) {
        boolean old = false;
        for (String k : tag.getAllKeys()) {
            if (VALUES.containsKey(k)) { old = true; break; }
        }
        if (!old) return tag;
        CompoundTag out = new CompoundTag();
        for (String k : tag.getAllKeys()) {
            Tag v = tag.get(k);
            if (v instanceof StringTag s && VALUES.containsKey(s.getAsString())) v = StringTag.valueOf(VALUES.get(s.getAsString()));
            out.put(VALUES.getOrDefault(k, k), v);
        }
        return out;
    }
}
