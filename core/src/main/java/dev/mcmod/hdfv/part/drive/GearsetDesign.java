package dev.mcmod.hdfv.part.drive;

import dev.mcmod.aerocore.api.drive.Driveline;
import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;
import dev.mcmod.hdfv.design.RecordEditor;
import dev.mcmod.hdfv.part.engine.EngineComponent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A gearbox's gears (齒輪組): the ratios themselves (a layshaft box's pairs), or planetary sets from their teeth by
 * Willis' equation (AeroCore Driveline): a Simpson three-speed (common sun, front and rear rings), a Ravigneaux
 * four-speed (forward and reverse suns, one ring) or a Lepelletier six-speed (a simple set's sun and ring, then a
 * Ravigneaux's forward sun, reverse sun and ring). Efficiency: the whole box's in gear (manuals 0.96-0.98, automatics
 * 0.93-0.96 without the converter).
 * <p>
 * A planetary set needs room for its planets: the ring at least 24 teeth more than the sun (planets of 12 or more).
 */
public record GearsetDesign(String name, Source source, double[] forwardRatios, double reverseRatio, int sunTeeth, int ringTeeth, int rearRingTeeth,
                            int reverseSunTeeth, int simpleSunTeeth, int simpleRingTeeth, double efficiency) implements EngineComponent, RecordEditor.Shown {
    public static final String TYPE = "hdfv:gearset";

    /** Where the ratios come from. */
    public enum Source { RATIOS, SIMPSON, RAVIGNEAUX, LEPELLETIER }

    public GearsetDesign {
        Objects.requireNonNull(name, "name"); Objects.requireNonNull(source, "source");
        forwardRatios = forwardRatios.clone();
    }

    @Override public double[] forwardRatios() { return forwardRatios.clone(); }

    @Override public boolean equals(Object o) {
        return o instanceof GearsetDesign g && name.equals(g.name) && source == g.source && Arrays.equals(forwardRatios, g.forwardRatios)
                && Double.compare(reverseRatio, g.reverseRatio) == 0 && sunTeeth == g.sunTeeth && ringTeeth == g.ringTeeth && rearRingTeeth == g.rearRingTeeth
                && reverseSunTeeth == g.reverseSunTeeth && simpleSunTeeth == g.simpleSunTeeth && simpleRingTeeth == g.simpleRingTeeth
                && Double.compare(efficiency, g.efficiency) == 0;
    }

    @Override public int hashCode() { return Objects.hash(name, source, Arrays.hashCode(forwardRatios), reverseRatio, sunTeeth, ringTeeth, efficiency); }

    @Override public String type() { return TYPE; }

    @Override public boolean shows(String parameter) {
        return switch (parameter) {
            case "forwardRatios", "reverseRatio" -> source == Source.RATIOS;
            case "sunTeeth", "ringTeeth" -> source != Source.RATIOS;
            case "rearRingTeeth" -> source == Source.SIMPSON;
            case "reverseSunTeeth" -> source == Source.RAVIGNEAUX || source == Source.LEPELLETIER;
            case "simpleSunTeeth", "simpleRingTeeth" -> source == Source.LEPELLETIER;
            default -> true;
        };
    }

    /** {forward ratios..., reverse} as built. */
    private double[] all() {
        return switch (source) {
            case RATIOS -> {
                double[] r = Arrays.copyOf(forwardRatios, forwardRatios.length + 1);
                r[forwardRatios.length] = reverseRatio;
                yield r;
            }
            case SIMPSON -> Driveline.simpson(sunTeeth, ringTeeth, rearRingTeeth);
            case RAVIGNEAUX -> Driveline.ravigneaux(sunTeeth, reverseSunTeeth, ringTeeth);
            case LEPELLETIER -> Driveline.lepelletier(simpleSunTeeth, simpleRingTeeth, sunTeeth, reverseSunTeeth, ringTeeth);
        };
    }

    /** The forward ratios, 1st first. */
    public double[] ratios() { double[] a = all(); return Arrays.copyOf(a, a.length - 1); }
    /** The reverse ratio (negative). */
    public double reverse() { double[] a = all(); return a[a.length - 1]; }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        if (!(efficiency > 0.5 && efficiency <= 1)) { issues.add(DesignIssue.invalid("efficiency", "The efficiency must be 0.5 to 1")); }
        switch (source) {
            case RATIOS -> {
                if (forwardRatios.length < 1 || forwardRatios.length > 12) { issues.add(DesignIssue.invalid("forwardRatios", "1 to 12 forward gears")); }
                for (int i = 0; i < forwardRatios.length; i++) {
                    if (!(forwardRatios[i] > 0.2 && forwardRatios[i] < 20)) { issues.add(DesignIssue.invalid("forwardRatios", "Each ratio must be 0.2 to 20")); break; }
                    if (i > 0 && !(forwardRatios[i] < forwardRatios[i - 1])) { issues.add(DesignIssue.invalid("forwardRatios", "Each gear must be taller than the one before")); break; }
                }
                if (!(reverseRatio < -0.2 && reverseRatio > -20)) { issues.add(DesignIssue.invalid("reverseRatio", "The reverse ratio must be -0.2 to -20")); }
            }
            case SIMPSON -> { planetary(issues, "ringTeeth", sunTeeth, ringTeeth); planetary(issues, "rearRingTeeth", sunTeeth, rearRingTeeth); }
            case RAVIGNEAUX -> { planetary(issues, "ringTeeth", sunTeeth, ringTeeth); planetary(issues, "reverseSunTeeth", reverseSunTeeth, ringTeeth); }
            case LEPELLETIER -> {
                planetary(issues, "simpleRingTeeth", simpleSunTeeth, simpleRingTeeth);
                planetary(issues, "ringTeeth", sunTeeth, ringTeeth);
                planetary(issues, "reverseSunTeeth", reverseSunTeeth, ringTeeth);
            }
        }
        if (!issues.isEmpty()) { return issues; }
        double[] r = ratios();
        if (r.length > 1) { DesignIssue.range(issues, "ratioSpread", r[0] / r[r.length - 1], 2.5, 10.5, "ratio spreads (five-speed manuals 4-5, eight to ten-speed automatics 7-10)"); }
        for (int i = 1; i < r.length; i++) {
            double stepRatio = r[i - 1] / r[i];
            if (stepRatio > 2.2 || stepRatio < 1.05) {
                DesignIssue.range(issues, "gearStep", stepRatio, 1.05, 2.2, "steps between gears (progressive: about 1.7 low down to 1.15 at the top)");
                break;
            }
        }
        return issues;
    }

    private static void planetary(List<DesignIssue> issues, String parameter, int sun, int ring) {
        if (sun < 12 || ring < sun + 24 || ring > 200) { issues.add(DesignIssue.invalid(parameter, "A planetary set needs at least 12 sun teeth and a ring 24 teeth larger (planets of 12 or more)")); }
    }

    @Override public double massKg() { return 0; }

    @Override public Map<String, Object> parameters() {
        List<Object> ratios = new ArrayList<>();
        for (double d : forwardRatios) { ratios.add(d); }
        return Fields.object("source", source.name().toLowerCase(), "forwardRatios", ratios, "reverseRatio", reverseRatio, "sunTeeth", sunTeeth, "ringTeeth", ringTeeth,
                "rearRingTeeth", rearRingTeeth, "reverseSunTeeth", reverseSunTeeth, "simpleSunTeeth", simpleSunTeeth, "simpleRingTeeth", simpleRingTeeth,
                "efficiency", efficiency);
    }

    public static GearsetDesign read(String name, Fields f) {
        List<Object> list = f.list("forwardRatios", List.of(3.6, 2.1, 1.4, 1.0, 0.8));
        double[] ratios = new double[list.size()];
        for (int i = 0; i < ratios.length; i++) { ratios[i] = ((Number) list.get(i)).doubleValue(); }
        return new GearsetDesign(name, Rules.choice(f, "source", Source.class, Source.RATIOS), ratios, f.number("reverseRatio", -3.5), f.integer("sunTeeth", 30),
                f.integer("ringTeeth", 72), f.integer("rearRingTeeth", 72), f.integer("reverseSunTeeth", 34), f.integer("simpleSunTeeth", 37),
                f.integer("simpleRingTeeth", 71), f.number("efficiency", 0.96));
    }

    /** A layshaft box's ratios. */
    public static GearsetDesign of(double reverse, double efficiency, double... forward) {
        return new GearsetDesign("gears", Source.RATIOS, forward, reverse, 30, 72, 72, 34, 37, 71, efficiency);
    }
}
