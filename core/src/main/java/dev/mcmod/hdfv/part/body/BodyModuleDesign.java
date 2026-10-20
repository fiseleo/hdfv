package dev.mcmod.hdfv.part.body;

import dev.mcmod.aerocore.api.body.BodyStructure;
import dev.mcmod.aerocore.api.body.SectionShape;
import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;
import dev.mcmod.hdfv.design.RecordEditor;
import dev.mcmod.hdfv.part.engine.EngineComponent;
import dev.mcmod.hdfv.part.engine.EngineMaterial;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * One module of a body (模組): a length of fuselage, hull or car body from where the module ahead of it ends to its own
 * rear section.
 * <ul>
 * <li>Shape: kind, length, the rear section (family, width, height, bottom above the body's reference line, and the
 *   family's own shape: superellipse exponents, deadrise and flare, belt line, roof width and floor corners), how full the
 *   taper is (1 straight, 2 elliptical) and a step at its front (the bottom rises there).</li>
 * <li>Structure: build, material, skin, stringers (share of the skin), frame spacing and section, the stiffeners' spacing
 *   (the plating's panel width, for a hull's bottom), floor mass per area, cabin pressure over ambient.</li>
 * <li>Interior: seats (abreast, pitch), cargo hold and fuel tank volumes.</li>
 * <li>Openings: doors (count, width, height), window share of the sides, open to the sky and how high the open edge
 *   (sill) is above the module's bottom.</li>
 * <li>Mount points: a wing (low, mid, high) and a rotor mast here; the kind adds its own (a nose's engine, a tail cone's
 *   tail, a stern's transom, a car's axles). Any module may be an engine bay ("m2_engine": front, mid or rear engine) and
 *   carry an axle ("m3_axle", axleAt of its length back from its front end), whatever its kind (0.6; a hood is both, a
 *   trunk carries the rear axle, by default).</li>
 * </ul>
 */
public record BodyModuleDesign(String name, Kind kind, double lengthM, Section section, double widthM, double heightM, double bottomM,
                               double upperExponent, double lowerExponent, double deadriseDeg, double flareDeg, double beltFraction,
                               double roofWidthRatio, double cornerRadiusM, double fullness, double stepM,
                               BodyStructure.Build build, EngineMaterial material, double skinM, double stringerShare, double frameSpacingM,
                               double frameAreaM2, double stiffenerSpacingM, double floorKgM2, double pressurePa,
                               int seats, int seatsAbreast, double seatPitchM, double cargoM3, double fuelM3,
                               int doors, double doorWidthM, double doorHeightM, double windowShare, boolean open, double sillM,
                               WingMount wing, boolean mast, String paintColor, boolean engineBay, boolean axle, double axleAt)
        implements EngineComponent, RecordEditor.Shown {
    public static final String TYPE = "hdfv:body_module";

    /**
     * What the module is: its family (aircraft, boat, car) and the mount points it brings. 0.18: a truck's cab-over cab
     * (CAB_OVER: its front flat over the front axle, the windscreen, grille panel and stepped bumper on it: FrontLayout;
     * seats like a passenger cell's, the front axle under it, the engine under its floor on the chassis) and a trailer's
     * or a lorry's deck (DECK: a flat platform, no glass, no seats).
     */
    public enum Kind {
        NOSE(Family.AIRCRAFT), COCKPIT(Family.AIRCRAFT), CABIN(Family.AIRCRAFT), CARGO(Family.AIRCRAFT), TAIL_CONE(Family.AIRCRAFT), TAIL_BOOM(Family.AIRCRAFT),
        BOW(Family.BOAT), FOREBODY(Family.BOAT), STEP(Family.BOAT), AFTERBODY(Family.BOAT), STERN(Family.BOAT),
        HOOD(Family.CAR), PASSENGER_CELL(Family.CAR), TRUNK(Family.CAR), CAB_OVER(Family.CAR), DECK(Family.CAR);

        public final Family family;
        Kind(Family family) { this.family = family; }
    }

    public enum Family { AIRCRAFT, BOAT, CAR }

    /** The rear section's family (SectionShape). */
    public enum Section { ROUNDED, CHINED, CABIN }

    public enum WingMount { NONE, LOW, MID, HIGH }

    public BodyModuleDesign {
        Objects.requireNonNull(name, "name"); Objects.requireNonNull(kind, "kind"); Objects.requireNonNull(section, "section");
        Objects.requireNonNull(build, "build"); Objects.requireNonNull(material, "material"); Objects.requireNonNull(wing, "wing");
        if (paintColor == null) { paintColor = ""; }
    }

    @Override public String type() { return TYPE; }

    /** The rear section. */
    public SectionShape rear() {
        return switch (section) {
            case ROUNDED -> new SectionShape.Rounded(widthM, heightM, bottomM, upperExponent, lowerExponent);
            case CHINED -> new SectionShape.Chined(widthM, heightM, bottomM, deadriseDeg, flareDeg);
            case CABIN -> new SectionShape.Cabin(widthM, heightM, bottomM, beltFraction, roofWidthRatio, cornerRadiusM);
        };
    }

    public BodyStructure.Module structure() {
        return new BodyStructure.Module(build, material.densityKgPerM3(), material.yieldPa(), material.modulusPa(), skinM, stringerShare, frameSpacingM,
                frameAreaM2, floorKgM2, doors, doorWidthM, doorHeightM, windowShare, pressurePa);
    }

    /** Rows of seats. */
    public int rows() { return seats <= 0 ? 0 : (seats + Math.max(seatsAbreast, 1) - 1) / Math.max(seatsAbreast, 1); }

    /** Parameters that mean something for this module (the rest stay hidden). */
    @Override public boolean shows(String parameter) {
        return switch (parameter) {
            case "upperExponent", "lowerExponent" -> section == Section.ROUNDED;
            case "deadriseDeg", "flareDeg" -> section == Section.CHINED;
            case "beltFraction", "roofWidthRatio", "cornerRadiusM" -> section == Section.CABIN;
            case "pressurePa" -> build != BodyStructure.Build.SPACE_FRAME;
            case "seatsAbreast", "seatPitchM" -> seats > 0;
            case "doorWidthM", "doorHeightM" -> doors > 0;
            case "sillM" -> open;
            case "axleAt" -> axle;
            default -> true;
        };
    }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        Rules.positive(issues, "lengthM", lengthM);
        Rules.positive(issues, "widthM", widthM);
        Rules.positive(issues, "heightM", heightM);
        if (!Double.isFinite(bottomM)) { issues.add(DesignIssue.invalid("bottomM", "The bottom must be a height")); }
        if (section == Section.ROUNDED && !(upperExponent >= 1 && upperExponent <= 20 && lowerExponent >= 1 && lowerExponent <= 20)) {
            issues.add(DesignIssue.invalid("upperExponent", "Section exponents are 1 to 20"));
        }
        if (section == Section.CHINED) {
            if (!(deadriseDeg >= 0 && deadriseDeg < 60)) { issues.add(DesignIssue.invalid("deadriseDeg", "Deadrise must be 0 to 60 degrees")); }
            if (!(flareDeg >= -30 && flareDeg <= 45)) { issues.add(DesignIssue.invalid("flareDeg", "Flare must be -30 to 45 degrees")); }
        }
        if (section == Section.CABIN) {
            if (!(beltFraction > 0.2 && beltFraction <= 1)) { issues.add(DesignIssue.invalid("beltFraction", "The belt line is 0.2 to 1 of the height")); }
            if (!(roofWidthRatio > 0.2 && roofWidthRatio <= 1)) { issues.add(DesignIssue.invalid("roofWidthRatio", "The roof is 0.2 to 1 of the width")); }
            Rules.nonnegative(issues, "cornerRadiusM", cornerRadiusM);
        }
        if (!(fullness >= 0.25 && fullness <= 4)) { issues.add(DesignIssue.invalid("fullness", "Fullness is 0.25 to 4")); }
        Rules.nonnegative(issues, "stepM", stepM);
        if (stepM > 0.3 * heightM) { issues.add(DesignIssue.invalid("stepM", "A step is at most 30 % of the depth")); }
        Rules.positive(issues, "skinM", skinM);
        Rules.nonnegative(issues, "stringerShare", stringerShare);
        Rules.positive(issues, "frameSpacingM", frameSpacingM);
        Rules.nonnegative(issues, "frameAreaM2", frameAreaM2);
        Rules.positive(issues, "stiffenerSpacingM", stiffenerSpacingM);
        Rules.nonnegative(issues, "floorKgM2", floorKgM2);
        Rules.nonnegative(issues, "pressurePa", pressurePa);
        Rules.count(issues, "seats", seats, 0, 400);
        if (seats > 0) {
            Rules.count(issues, "seatsAbreast", seatsAbreast, 1, 12);
            Rules.positive(issues, "seatPitchM", seatPitchM);
            if (rows() * seatPitchM > lengthM + 1e-9) { issues.add(DesignIssue.invalid("seats", rows() + " rows at " + seatPitchM + " m need " + rows() * seatPitchM + " m; the module is " + lengthM + " m")); }
            if (seatsAbreast * 0.45 > widthM) { issues.add(DesignIssue.invalid("seatsAbreast", seatsAbreast + " seats abreast need " + seatsAbreast * 0.45 + " m of width")); }
        }
        Rules.nonnegative(issues, "cargoM3", cargoM3);
        Rules.nonnegative(issues, "fuelM3", fuelM3);
        Rules.count(issues, "doors", doors, 0, 8);
        if (doors > 0) {
            Rules.positive(issues, "doorWidthM", doorWidthM);
            Rules.positive(issues, "doorHeightM", doorHeightM);
            if (doors * doorWidthM > 2 * lengthM) { issues.add(DesignIssue.invalid("doors", "The doors do not fit along the module's sides")); }
            if (doorHeightM > heightM) { issues.add(DesignIssue.invalid("doorHeightM", "A door is higher than the module")); }
        }
        if (!(windowShare >= 0 && windowShare <= 0.6)) { issues.add(DesignIssue.invalid("windowShare", "Windows are 0 to 60 % of the sides")); }
        if (open && !(sillM >= 0 && sillM <= heightM)) { issues.add(DesignIssue.invalid("sillM", "The open edge is within the module's height")); }
        if (!RecordEditor.isColor(paintColor, true)) { issues.add(DesignIssue.invalid("paintColor", "A colour is #RRGGBB, or blank for the body's main colour")); }
        if (axle && !(axleAt >= 0 && axleAt <= 1)) { issues.add(DesignIssue.invalid("axleAt", "The axle is 0 (front end) to 1 (rear end) along the module")); }
        return issues;
    }

    /** No mass of its own as a component; the body's structure analysis gives each module's. */
    @Override public double massKg() { return 0; }

    @Override public Map<String, Object> parameters() {
        Map<String, Object> m = Fields.object("kind", lower(kind), "lengthM", lengthM, "section", lower(section), "widthM", widthM, "heightM", heightM, "bottomM", bottomM,
                "upperExponent", upperExponent, "lowerExponent", lowerExponent, "deadriseDeg", deadriseDeg, "flareDeg", flareDeg, "beltFraction", beltFraction,
                "roofWidthRatio", roofWidthRatio, "cornerRadiusM", cornerRadiusM, "fullness", fullness, "stepM", stepM, "build", lower(build));
        m.put("material", material.toJson());
        m.putAll(Fields.object("skinM", skinM, "stringerShare", stringerShare, "frameSpacingM", frameSpacingM, "frameAreaM2", frameAreaM2,
                "stiffenerSpacingM", stiffenerSpacingM,
                "floorKgM2", floorKgM2, "pressurePa", pressurePa, "seats", seats, "seatsAbreast", seatsAbreast, "seatPitchM", seatPitchM,
                "cargoM3", cargoM3, "fuelM3", fuelM3, "doors", doors, "doorWidthM", doorWidthM, "doorHeightM", doorHeightM, "windowShare", windowShare,
                "open", open, "sillM", sillM, "wing", lower(wing), "mast", mast, "paintColor", paintColor, "engineBay", engineBay, "axle", axle, "axleAt", axleAt));
        return m;
    }

    private static String lower(Enum<?> e) { return e.name().toLowerCase(java.util.Locale.ROOT); }

    static BodyModuleDesign read(String name, Fields f) {
        Kind kind = Rules.choice(f, "kind", Kind.class, Kind.CABIN);
        BodyModuleDesign d = defaults(kind, null);
        return new BodyModuleDesign(name, kind, f.number("lengthM", d.lengthM), Rules.choice(f, "section", Section.class, d.section),
                f.number("widthM", d.widthM), f.number("heightM", d.heightM), f.number("bottomM", d.bottomM), f.number("upperExponent", d.upperExponent),
                f.number("lowerExponent", d.lowerExponent), f.number("deadriseDeg", d.deadriseDeg), f.number("flareDeg", d.flareDeg),
                f.number("beltFraction", d.beltFraction), f.number("roofWidthRatio", d.roofWidthRatio), f.number("cornerRadiusM", d.cornerRadiusM),
                f.number("fullness", d.fullness), f.number("stepM", d.stepM), Rules.choice(f, "build", BodyStructure.Build.class, d.build),
                Rules.material(f, "material", d.material), f.number("skinM", d.skinM), f.number("stringerShare", d.stringerShare),
                f.number("frameSpacingM", d.frameSpacingM), f.number("frameAreaM2", d.frameAreaM2), f.number("stiffenerSpacingM", d.stiffenerSpacingM),
                f.number("floorKgM2", d.floorKgM2),
                f.number("pressurePa", d.pressurePa), f.integer("seats", d.seats), f.integer("seatsAbreast", d.seatsAbreast), f.number("seatPitchM", d.seatPitchM),
                f.number("cargoM3", d.cargoM3), f.number("fuelM3", d.fuelM3), f.integer("doors", d.doors), f.number("doorWidthM", d.doorWidthM),
                f.number("doorHeightM", d.doorHeightM), f.number("windowShare", d.windowShare), f.bool("open", d.open), f.number("sillM", d.sillM),
                Rules.choice(f, "wing", WingMount.class, d.wing), f.bool("mast", d.mast), f.string("paintColor", ""), f.bool("engineBay", d.engineBay),
                f.bool("axle", d.axle), f.number("axleAt", d.axleAt));
    }

    /** The kinds whose modules start where the bottom rises (a hull's step). */
    static final Set<Kind> STEPPED = Set.of(Kind.STEP);

    /**
     * A new module of a kind, sized to follow the given one (its rear section) or with the kind's own proportions:
     * a light aircraft's fuselage, a planing float's or boat's hull, a family car's body.
     */
    public static BodyModuleDesign defaults(Kind kind, BodyModuleDesign after) {
        double w = after != null ? after.widthM : switch (kind.family) { case AIRCRAFT -> 1.4; case BOAT -> 1.6; case CAR -> 1.8; };
        double h = after != null ? after.heightM : switch (kind.family) { case AIRCRAFT -> 1.5; case BOAT -> 0.9; case CAR -> 1.4; };
        double b = after != null ? after.bottomM : kind.family == Family.CAR ? 0.15 : 0;
        String name = kind.name().toLowerCase(java.util.Locale.ROOT);
        EngineMaterial alu = EngineMaterial.ALUMINIUM_2024, glass = EngineMaterial.GFRP, steel = EngineMaterial.STEEL_1045;
        BodyStructure.Build semi = BodyStructure.Build.SEMI_MONOCOQUE, mono = BodyStructure.Build.MONOCOQUE;
        return switch (kind) {
            case NOSE -> new BodyModuleDesign(name, kind, 1.5, Section.ROUNDED, w, h, b, 2.5, 2.5, 20, 0, 0.55, 0.75, 0.1, 2, 0, semi, alu, 0.001, 0.3, 0.5, 1.2e-4, 0.5, 0, 0,
                    0, 2, 0.9, 0, 0, 0, 0.9, 1.1, 0, false, 0, WingMount.NONE, false, "", false, false, 0.5);
            case COCKPIT -> new BodyModuleDesign(name, kind, 2.0, Section.ROUNDED, w, h, b, 2.5, 2.5, 20, 0, 0.55, 0.75, 0.1, 1, 0, semi, alu, 0.001, 0.3, 0.5, 1.2e-4, 0.5, 10, 0,
                    2, 2, 0.9, 0, 0, 2, 0.9, 1.1, 0.3, false, 0, WingMount.NONE, false, "", false, false, 0.5);
            case CABIN -> new BodyModuleDesign(name, kind, 3.0, Section.ROUNDED, w, h, b, 2.5, 2.5, 20, 0, 0.55, 0.75, 0.1, 1, 0, semi, alu, 0.001, 0.3, 0.5, 1.2e-4, 0.5, 12, 0,
                    6, 2, 0.8, 0, 0, 1, 1.2, 1.3, 0.15, false, 0, WingMount.NONE, false, "", false, false, 0.5);
            case CARGO -> new BodyModuleDesign(name, kind, 3.0, Section.ROUNDED, w, h, b, 3, 3, 20, 0, 0.55, 0.75, 0.1, 1, 0, semi, alu, 0.0012, 0.3, 0.5, 1.5e-4, 0.5, 20, 0,
                    0, 2, 0.8, 6, 0, 1, 1.6, 1.4, 0, false, 0, WingMount.NONE, false, "", false, false, 0.5);
            case TAIL_CONE -> new BodyModuleDesign(name, kind, 3.5, Section.ROUNDED, 0.25 * w, 0.3 * h, b + 0.55 * h, 2, 2, 20, 0, 0.55, 0.75, 0.1, 1.3, 0, semi, alu, 0.0008,
                    0.25, 0.5, 1.0e-4, 0.5, 0, 0, 0, 2, 0.8, 0, 0, 0, 0.8, 1.0, 0, false, 0, WingMount.NONE, false, "", false, false, 0.5);
            case TAIL_BOOM -> new BodyModuleDesign(name, kind, 6.0, Section.ROUNDED, 0.45, 0.5, b + h - 0.6, 2, 2, 20, 0, 0.55, 0.75, 0.1, 1, 0, mono, alu, 0.0012,
                    0.2, 0.6, 0.8e-4, 0.6, 0, 0, 0, 2, 0.8, 0, 0, 0, 0.8, 1.0, 0, false, 0, WingMount.NONE, false, "", false, false, 0.5);
            case BOW -> new BodyModuleDesign(name, kind, 1.5, Section.CHINED, w, h, b, 2, 2, 28, 8, 0.55, 0.75, 0.1, 2, 0, mono, glass, 0.006, 0, 0.5, 0, 0.5, 0, 0,
                    0, 2, 0.8, 0, 0, 0, 0.8, 1.0, 0, false, 0, WingMount.NONE, false, "", false, false, 0.5);
            case FOREBODY -> new BodyModuleDesign(name, kind, 2.5, Section.CHINED, w, h, b, 2, 2, 22, 6, 0.55, 0.75, 0.1, 1, 0, mono, glass, 0.006, 0, 0.5, 0, 0.5, 0, 0,
                    0, 2, 0.8, 0, 0, 0, 0.8, 1.0, 0, false, 0, WingMount.NONE, false, "", false, false, 0.5);
            case STEP -> new BodyModuleDesign(name, kind, 0.2, Section.CHINED, w, h - 0.04 * w, b + 0.04 * w, 2, 2, 20, 6, 0.55, 0.75, 0.1, 1, 0.04 * w, mono, glass, 0.006, 0, 0.5,
                    0, 0.5, 0, 0, 0, 2, 0.8, 0, 0, 0, 0.8, 1.0, 0, false, 0, WingMount.NONE, false, "", false, false, 0.5);
            case AFTERBODY -> new BodyModuleDesign(name, kind, 2.5, Section.CHINED, 0.85 * w, h - 0.08 * h, b + 0.08 * h, 2, 2, 18, 6, 0.55, 0.75, 0.1, 1, 0, mono, glass, 0.005,
                    0, 0.5, 0, 0.5, 0, 0, 0, 2, 0.8, 0, 0, 0, 0.8, 1.0, 0, false, 0, WingMount.NONE, false, "", false, false, 0.5);
            case STERN -> new BodyModuleDesign(name, kind, 0.8, Section.CHINED, w, h, b, 2, 2, 16, 6, 0.55, 0.75, 0.1, 1, 0, mono, glass, 0.006, 0, 0.5, 0, 0.5, 0, 0,
                    0, 2, 0.8, 0, 0, 0, 0.8, 1.0, 0, false, 0, WingMount.NONE, false, "", false, false, 0.5);
            case HOOD -> new BodyModuleDesign(name, kind, 1.1, Section.CABIN, w, 0.65 * h, b, 2, 2, 20, 0, 1.0, 0.9, 0.12, 2, 0, mono, steel, 0.0008, 0.2, 0.5, 2e-4, 0.5, 0, 0,
                    0, 2, 0.9, 0, 0.06, 0, 0.9, 1.1, 0, false, 0, WingMount.NONE, false, "", true, true, 0.7);
            case PASSENGER_CELL -> new BodyModuleDesign(name, kind, 2.4, Section.CABIN, w, h, b, 2, 2, 20, 0, 0.55, 0.75, 0.12, 1, 0, mono, steel, 0.0008, 0.2, 0.5, 2e-4, 0.5, 15, 0,
                    4, 2, 0.95, 0, 0, 4, 0.95, 1.05, 0.35, false, 0, WingMount.NONE, false, "", false, false, 0.5);
            case TRUNK -> new BodyModuleDesign(name, kind, 1.0, Section.CABIN, w, 0.75 * h, b, 2, 2, 20, 0, 0.75, 0.85, 0.12, 1, 0, mono, steel, 0.0008, 0.2, 0.5, 2e-4, 0.5, 10, 0,
                    0, 2, 0.9, 0.45, 0, 0, 0.9, 1.0, 0, false, 0, WingMount.NONE, false, "", false, true, 0.3);
            // 0.18: a sleeper cab 2.25 m long, 2.45 m wide, 2.6 m tall over its floor, glass from 42 % up; the front axle a third back
            case CAB_OVER -> new BodyModuleDesign(name, kind, 2.25, Section.CABIN, Math.max(w, 2.45), Math.max(h, 2.6), b, 2, 2, 20, 0, 0.42, 0.94, 0.15, 1, 0, mono,
                    steel, 0.0012, 0.2, 0.5, 2e-4, 0.5, 15, 0, 2, 2, 0.9, 0, 0, 2, 1.0, 1.35, 0.3, false, 0, WingMount.NONE, false, "", false, true, 0.32);
            // a flat deck 0.25 m deep: no glass (belt at the top), square edges
            case DECK -> new BodyModuleDesign(name, kind, 3.0, Section.CABIN, Math.max(w, 2.5), 0.25, b, 2, 2, 20, 0, 1.0, 1.0, 0.02, 1, 0, mono, steel, 0.006, 0.2, 0.5,
                    2e-4, 0.5, 60, 0, 0, 2, 0.9, 0, 0, 0, 0.9, 1.0, 0, false, 0, WingMount.NONE, false, "", false, false, 0.5);
        };
    }
}
