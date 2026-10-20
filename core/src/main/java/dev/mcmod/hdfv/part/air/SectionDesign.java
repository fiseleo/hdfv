package dev.mcmod.hdfv.part.air;

import dev.mcmod.aerocore.api.aero.Airfoil;
import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;
import dev.mcmod.hdfv.part.engine.EngineComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * An airfoil section (翼型) of a wing, propeller or rotor: a NACA four-digit section (maximum camber, its position,
 * thickness) or a CST shape (Kulfan: four weights on each surface and a trailing-edge thickness). Its performance comes
 * from AeroCore (SectionPolar: panel method, boundary layers, stall, compressibility). No mass of its own.
 */
public record SectionDesign(String name, Family family, double camber, double camberPosition, double thickness,
                            double upper1, double upper2, double upper3, double upper4,
                            double lower1, double lower2, double lower3, double lower4, double trailingEdgeThickness) implements EngineComponent {
    public static final String TYPE = "hdfv:section";
    private static final String NACA = "NACA four-digit sections (Abbott and von Doenhoff, Theory of Wing Sections)";

    public enum Family { NACA4, CST }

    public SectionDesign { Objects.requireNonNull(name, "name"); Objects.requireNonNull(family, "family"); }

    /** A NACA four-digit section, with CST weights fitted to it (so switching the family keeps the shape). */
    public static SectionDesign naca(String name, double camber, double position, double thickness) {
        Airfoil a = Airfoil.naca4(camber, position, thickness);
        double[][] w = Airfoil.cstFit(a, 3);
        return new SectionDesign(name, Family.NACA4, camber, position, thickness, round(w[0][0]), round(w[0][1]), round(w[0][2]), round(w[0][3]),
                round(w[1][0]), round(w[1][1]), round(w[1][2]), round(w[1][3]), 0);
    }
    private static double round(double v) { return Math.round(v * 10000) / 10000.0; }

    @Override public String type() { return TYPE; }

    /** The shape. */
    public Airfoil airfoil() {
        return family == Family.NACA4 ? Airfoil.naca4(camber, camberPosition, thickness)
                : Airfoil.cst(new double[]{upper1, upper2, upper3, upper4}, new double[]{lower1, lower2, lower3, lower4}, trailingEdgeThickness);
    }

    /** The shape at another thickness ratio (a blade thinning to its tip): NACA thickness, or CST weights scaled about the camber line. */
    public Airfoil airfoil(double thicknessRatio) {
        if (family == Family.NACA4) { return Airfoil.naca4(camber, camberPosition, thicknessRatio); }
        Airfoil a = airfoil();
        double k = thicknessRatio / a.thickness();
        double[] u = {upper1, upper2, upper3, upper4}, l = {lower1, lower2, lower3, lower4}, uu = new double[4], ll = new double[4];
        for (int i = 0; i < 4; i++) {
            double mid = 0.5 * (u[i] + l[i]), half = 0.5 * (u[i] - l[i]) * k;
            uu[i] = mid + half; ll[i] = mid - half;
        }
        return Airfoil.cst(uu, ll, trailingEdgeThickness * k);
    }

    /** Thickness ratio of the shape. */
    public double thicknessRatio() {
        try { return airfoil().thickness(); } catch (IllegalArgumentException e) { return Double.NaN; }
    }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        if (family == Family.NACA4) {
            if (!(camber >= 0 && camber < 0.2)) { issues.add(DesignIssue.invalid("camber", "Camber must be at least 0 and below 0.2")); }
            else { DesignIssue.range(issues, "camber", camber, 0, 0.06, NACA); }
            if (camber > 0 && !(camberPosition > 0.05 && camberPosition < 0.95)) { issues.add(DesignIssue.invalid("camberPosition", "The camber's position must be within the chord")); }
            else if (camber > 0) { DesignIssue.range(issues, "camberPosition", camberPosition, 0.2, 0.6, NACA); }
            if (!(thickness > 0.01 && thickness < 0.5)) { issues.add(DesignIssue.invalid("thickness", "Thickness must be between 0.01 and 0.5 of the chord")); }
            else { DesignIssue.range(issues, "thickness", thickness, 0.06, 0.24, NACA); }
        } else {
            try {
                Airfoil a = airfoil();
                DesignIssue.range(issues, "thickness", a.thickness(), 0.06, 0.24, "sections like those tested by Abbott and von Doenhoff");
                DesignIssue.range(issues, "camber", a.camber(), 0, 0.06, "sections like those tested by Abbott and von Doenhoff");
            } catch (IllegalArgumentException e) {
                issues.add(DesignIssue.invalid("upper1", "The upper surface must lie above the lower one"));
            }
            if (!(trailingEdgeThickness >= 0 && trailingEdgeThickness < 0.05)) { issues.add(DesignIssue.invalid("trailingEdgeThickness", "Trailing edge thickness must be 0 to 0.05 of the chord")); }
        }
        return issues;
    }

    @Override public double massKg() { return 0; }

    @Override public Map<String, Object> parameters() {
        return Fields.object("family", family.name().toLowerCase(), "camber", camber, "camberPosition", camberPosition, "thickness", thickness,
                "upper", List.of(upper1, upper2, upper3, upper4), "lower", List.of(lower1, lower2, lower3, lower4), "trailingEdgeThickness", trailingEdgeThickness);
    }

    static SectionDesign read(String name, Fields f) {
        SectionDesign base = naca(name, f.number("camber", 0.02), f.number("camberPosition", 0.4), f.number("thickness", 0.12));
        List<Object> u = f.list("upper", List.of(base.upper1, base.upper2, base.upper3, base.upper4));
        List<Object> l = f.list("lower", List.of(base.lower1, base.lower2, base.lower3, base.lower4));
        if (u.size() != 4 || l.size() != 4) { throw new IllegalArgumentException(f.path() + ": CST needs four weights on each surface"); }
        return new SectionDesign(name, EngineComponent.Rules.choice(f, "family", Family.class, Family.NACA4), base.camber, base.camberPosition, base.thickness,
                num(u, 0), num(u, 1), num(u, 2), num(u, 3), num(l, 0), num(l, 1), num(l, 2), num(l, 3), f.number("trailingEdgeThickness", 0));
    }
    private static double num(List<Object> list, int i) { return ((Number) list.get(i)).doubleValue(); }

}
