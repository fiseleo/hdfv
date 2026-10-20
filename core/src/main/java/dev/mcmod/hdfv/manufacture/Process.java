package dev.mcmod.hdfv.manufacture;

/**
 * The steps that make a workpiece (0.7): primary forming (casting, forging, rolling or bending stock, laying up and
 * curing composites, vulcanizing rubber), cutting (turning, milling, planing, boring, drilling, grinding), joining
 * and assembling. Each has a letter the game's workpiece items record their finished steps with.
 *
 * 0.17 (all the machining machines): sawing stock off, lapping, polishing, deburring, tapping, threading, cutting a
 * plate's profile (laser, plasma, waterjet, oxy-fuel or a mill), all cutting steps; and riveting a part's linings,
 * whose time the planner gives. A step that 0.16 already had keeps its process and gains a Feature.
 */
public enum Process {
    CAST('C', false), FORGE('F', false), FORM('R', false), LAYUP('L', false), CURE('K', false), VULCANIZE('V', false),
    TURN('T', true), MILL('M', true), PLANE('P', true), BORE('B', true), DRILL('D', true), GRIND('G', true),
    WELD('W', false), ASSEMBLE('A', false),
    SAW('S', true), LAP('Q', true), POLISH('O', true), DEBURR('E', true), TAP('I', true), THREAD('Y', true), PROFILE('X', true), RIVET('U', false);

    public final char code;
    private final boolean cutting;

    Process(char code, boolean cutting) { this.code = code; this.cutting = cutting; }

    /** Whether it removes metal with a tool (its time from the cut, the machine's power and the material). */
    public boolean cutting() { return cutting; }

    public static Process of(char code) {
        for (Process p : values()) { if (p.code == code) { return p; } }
        throw new IllegalArgumentException("No process " + code);
    }
}
