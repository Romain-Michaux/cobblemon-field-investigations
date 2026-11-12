package io.github.romainmichaux.fieldinvestigations;

public class Trace {
    public String kind;
    public double x, z;
    public boolean harvest, placedOne;
    public int px, py, pz;
    // Indice enfoui : d'abord un bloc de sol remué (qui remplace le bloc d'origine), puis l'indice une fois dégagé au pinceau.
    public boolean buried, uncovered, lost;
    public String origin;
    public int brushing;
    // Quel objet est enfoui (voir Genetique.VARIANTES) : os ou dent, coquilles, plume ou mue, croc, griffe ou écaille.
    public int variant;
    // Une trace par enquête peut se trouver dans une grotte peu profonde reliée à l'extérieur (voir Traces.grotteOuverte).
    // Elle redevient une trace de surface si aucune grotte de ce genre n'existe près de l'endroit prévu.
    public boolean cave;
    // Enquête souterraine : nombre de fois où aucune grotte n'a été trouvée pour cette trace (à six, elle est posée en surface).
    public transient int failures;

    boolean over() {
        return harvest || lost;
    }
}
