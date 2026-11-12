package fr.cobblescout.traque;

public class Trace {
    public String genre;
    public double x, z;
    public boolean recolte, posee;
    public int px, py, pz;
    // Indice enfoui : d'abord un bloc de sol remué (qui remplace le bloc d'origine), puis l'indice une fois dégagé au pinceau.
    public boolean enfoui, degage, perdue;
    public String origine;
    public int brossage;
    // Quel objet est enfoui (voir Genetique.VARIANTES) : os ou dent, coquilles, plume ou mue, croc, griffe ou écaille.
    public int variante;
    // Enquête souterraine : nombre de fois où aucune grotte n'a été trouvée pour cette trace (à six, elle est posée en surface).
    public transient int echecs;

    boolean finie() {
        return recolte || perdue;
    }
}
