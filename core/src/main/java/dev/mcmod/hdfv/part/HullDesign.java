package dev.mcmod.hdfv.part;

import dev.mcmod.aerocore.api.FluidState;
import dev.mcmod.aerocore.api.HullModel;
import dev.mcmod.aerocore.api.HullSection;
import dev.mcmod.aerocore.api.SlenderHull;
import dev.mcmod.aerocore.api.vehicle.DesignException;
import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;
import dev.mcmod.aerocore.api.vehicle.MassShapes;
import dev.mcmod.aerocore.api.vehicle.MassSpec;
import dev.mcmod.aerocore.api.vehicle.Part;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.part.HullPart;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A displacement hull (or a submerged body) drawn by its sections: stations along the part's x (forward), each a
 * convex outline (y up, z starboard, counter-clockwise seen from the bow). HullDynamics gives its loads: strip
 * buoyancy on the local surface, ITTC-1957 friction with Holtrop-Mennen form factor plus Michell wave resistance
 * (surface hulls), strip added mass with the ideal-fluid terms, the Clarke-calibrated lift element and Hoerner
 * cross-flow drag; heave radiation from equal-length segments as point absorbers.
 * <ul>
 * <li>designDisplacementKg: the load the resistance and manoeuvring coefficients are built for (its waterline);
 *     they hold near it. Submerged hulls ignore it.</li>
 * <li>sternShape: Holtrop's C_stern (-10 V-shaped sections, 0 normal, +10 U-shaped).</li>
 * </ul>
 * Holtrop and Mennen's (1982) regression covers 3.9 &lt; L/B &lt; 15, 2.1 &lt; B/T &lt; 4 and 0.55 &lt; C_P &lt; 0.85; outside
 * it the form factor is extrapolated. Not for planing (volumetric Froude number above about 1.2; see PlaningHullDesign).
 * Sections must be convex: hollows and tunnels are not represented. Mass: a thin shell over the sections and both
 * end faces (thin-wall approximation), or a given mass with that distribution.
 */
public record HullDesign(String name, HullModel.Type hullType, List<Station> stations, double designDisplacementKg, double sternShape,
                         int radiationSegments, MassSpec mass) implements PartDesign {
    public static final String TYPE = "hdfv:hull";
    private static final String HOLTROP = "Holtrop and Mennen 1982 regression";

    /** A section outline point: y up, z starboard (m). */
    public record Point(double y, double z) {}
    /** A section at station x (m, forward). */
    public record Station(double xM, List<Point> points) {
        public Station { points = List.copyOf(points); }
    }

    public HullDesign {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(hullType, "hullType");
        stations = List.copyOf(stations);
        Objects.requireNonNull(mass, "mass");
    }

    @Override public String type() { return TYPE; }

    /** Sections of a SlenderHull as stations. */
    public static List<Station> stationsOf(SlenderHull hull) {
        List<Station> out = new ArrayList<>();
        for (HullSection s : hull.sections()) {
            List<Point> points = new ArrayList<>();
            for (double[] v : s.vertices()) { points.add(new Point(v[0], v[1])); }
            out.add(new Station(s.xM(), points));
        }
        return out;
    }

    /**
     * A quick start: SlenderHull.tapered with fullness 1 - |s|^power (s from -1 aft to 1 forward), length L centred on
     * the part origin, keel at keelY, deck at topY, round bilge of the given radius.
     */
    public static HullDesign tapered(String name, double length, double halfBeam, double keelY, double topY, double bilgeRadius,
                                     double power, int stationCount, double designDisplacementKg, MassSpec mass) {
        SlenderHull hull = SlenderHull.tapered(length, halfBeam, keelY, topY, bilgeRadius, stationCount, s -> 1 - Math.pow(Math.abs(s), power));
        return new HullDesign(name, HullModel.Type.SURFACE, stationsOf(hull), designDisplacementKg, 0, 6, mass);
    }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        if (stations.size() < 3) { issues.add(DesignIssue.invalid("stations", "A hull needs at least three stations")); }
        for (int i = 0; i < stations.size(); i++) {
            if (i > 0 && !(stations.get(i).xM() > stations.get(i - 1).xM())) {
                issues.add(DesignIssue.invalid("stations[" + i + "].xM", "Stations must go forward in increasing x"));
            }
            try { section(stations.get(i)); } catch (IllegalArgumentException e) {
                issues.add(DesignIssue.invalid("stations[" + i + "]", e.getMessage()));
            }
        }
        if (hullType == HullModel.Type.SURFACE && (!(designDisplacementKg > 0) || !Double.isFinite(designDisplacementKg))) {
            issues.add(DesignIssue.invalid("designDisplacementKg", "Design displacement must be positive"));
        }
        if (!Double.isFinite(sternShape)) { issues.add(DesignIssue.invalid("sternShape", "Stern shape must be finite")); }
        if (radiationSegments < 1 || radiationSegments > 16) { issues.add(DesignIssue.invalid("radiationSegments", "Radiation segments must be 1 to 16")); }
        mass.check(issues, "mass", true);
        if (issues.isEmpty()) { DesignIssue.range(issues, "sternShape", sternShape, -25, 10, HOLTROP); }
        return issues;
    }

    private static HullSection section(Station s) {
        List<double[]> vertices = new ArrayList<>();
        for (Point p : s.points()) { vertices.add(new double[]{p.y(), p.z()}); }
        return new HullSection(s.xM(), vertices);
    }

    @Override public Part compile() {
        List<DesignIssue> issues = check();
        DesignException.throwIfInvalid(issues);
        SlenderHull hull = geometry();
        if (hullType == HullModel.Type.SURFACE) {
            if (!(designDisplacementKg / FluidState.FRESH_WATER_20C.densityKgPerM3() < hull.totalVolume())) {
                issues.add(DesignIssue.invalid("designDisplacementKg", "The hull is too small to float this displacement"));
                throw new DesignException(issues);
            }
            SlenderHull.Particulars d = designParticulars(hull);
            DesignIssue.range(issues, "lengthBeamRatio", d.lengthM() / d.beamM(), 3.9, 15, HOLTROP);
            DesignIssue.range(issues, "beamDraftRatio", d.beamM() / d.draftM(), 2.1, 4, HOLTROP);
            DesignIssue.range(issues, "prismaticCoefficient", d.prismaticCoefficient(), 0.55, 0.85, HOLTROP);
        }
        return new HullPart(this, hull, hullType, designDisplacementKg, sternShape, radiationSegments,
                mass.resolve(t -> MassShapes.hullShell(hull, Double.isFinite(t) ? t : 1e-3)),
                issues.stream().filter(i -> i.kind() == DesignIssue.Kind.EXTRAPOLATION).toList());
    }

    /** The hull geometry the stations describe (stations must be valid). */
    public SlenderHull geometry() {
        List<HullSection> sections = new ArrayList<>();
        for (Station s : stations) { sections.add(section(s)); }
        return new SlenderHull(sections);
    }

    /** Hull particulars at the design waterline (fresh water). */
    public SlenderHull.Particulars designParticulars(SlenderHull hull) {
        return hull.particulars(hull.waterlineFor(designDisplacementKg / FluidState.FRESH_WATER_20C.densityKgPerM3()));
    }

    @Override public Map<String, Object> parameters() {
        List<Object> list = new ArrayList<>();
        for (Station s : stations) {
            List<Object> points = new ArrayList<>();
            for (Point p : s.points()) { points.add(List.of(p.y(), p.z())); }
            list.add(Fields.object("x", s.xM(), "points", points));
        }
        return Fields.object("hullType", hullType.name().toLowerCase(), "designDisplacementKg", designDisplacementKg, "sternShape", sternShape,
                "radiationSegments", radiationSegments, "stations", list, "mass", mass.toJson());
    }

    public static PartDesign read(Fields f) {
        List<Station> stations = new ArrayList<>();
        for (Fields s : f.objects("stations")) {
            List<Point> points = new ArrayList<>();
            List<Object> raw = s.list("points");
            for (int i = 0; i < raw.size(); i++) {
                if (!(raw.get(i) instanceof List<?> p) || p.size() != 2 || !(p.get(0) instanceof Number y) || !(p.get(1) instanceof Number z)) {
                    throw new IllegalArgumentException(s.path() + ".points[" + i + "]: expected [y, z]");
                }
                points.add(new Point(y.doubleValue(), z.doubleValue()));
            }
            stations.add(new Station(s.number("x"), points));
        }
        HullModel.Type type = switch (f.string("hullType", "surface")) {
            case "surface" -> HullModel.Type.SURFACE;
            case "submerged" -> HullModel.Type.SUBMERGED;
            default -> throw new IllegalArgumentException(f.path() + ".hullType: surface or submerged");
        };
        return new HullDesign(f.string("name", "hull"), type, stations, f.number("designDisplacementKg", 0), f.number("sternShape", 0),
                f.integer("radiationSegments", 6), MassSpec.fromJson(f.child("mass")));
    }
}
