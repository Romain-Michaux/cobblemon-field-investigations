package io.github.romainmichaux.fieldinvestigations;

public class Trace {
    public String kind;
    public double x, z;
    public boolean sampled, placed;
    public int px, py, pz;
    // Buried clue: first a disturbed soil block (replacing the original block), then the clue itself once brushed clear.
    public boolean buried, uncovered, lost;
    public String origin;
    public int brushing;
    // Which object is buried (see Genetics.VARIANTS): bone or tooth, shells, feather or shed skin, fang, claw or scale.
    public int variant;
    // One trace per case may be in a shallow cave connected to the outside (see Traces.openCave).
    // It reverts to a surface trace if no such cave exists near the intended spot.
    public boolean cave;
    // Challenge trace (see Challenges): "track" for now. The radar leads to the start (sx, sz); the trace appears at the end
    // of the challenge, damaged after three failed tries.
    public String challenge;
    public boolean challengeDone, damaged;
    public int tries;
    public double sx, sz;
    public java.util.List<double[]> course;
    public transient boolean running;
    public transient int stage;
    public transient long deadline;
    // Underground case: how many times no cave was found for this trace (after six, it is placed on the surface).
    public transient int failures;

    boolean over() {
        return sampled || lost;
    }
}
