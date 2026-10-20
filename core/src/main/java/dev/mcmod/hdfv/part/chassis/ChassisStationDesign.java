package dev.mcmod.hdfv.part.chassis;

import dev.mcmod.aerocore.api.frame.Section;
import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;
import dev.mcmod.hdfv.design.RecordEditor;
import dev.mcmod.hdfv.part.engine.EngineComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A station along a chassis (0.15, the pages "station1"...): where along the frame (x from its rear end) and what it
 * carries: an axle (its mount "axle_N" heightM above the rails' bottom, negative under them), a crossmember, the engine
 * (its bay "engine_bay" heightM above the rails' top), the body. Every station has a crossmember of its section (a
 * backbone's outriggers; a tube frame's ring of tubes), and a brace to the next station forward: none, one diagonal, an
 * X (a ladder's X-member; a tube frame's bay triangulated in its sides, floor and top). A tube frame's ring stands
 * bayHeightM high and bayTopWidthM wide at its top, open above for a cockpit (openTop: no top tubes into the next bay).
 * 0.18, trucks: a tractor's fifth wheel ("fifth_wheel" heightM above the rails' top: where its base bolts on; the
 * coupling face is the fifth wheel part's height over that), a semi-trailer's kingpin
 * ("kingpin" on its axis heightM from the rails' bottom: the plate the fifth wheel carries; negative under them) and
 * its landing legs ("legs_1" port and "legs_2" starboard just outside the rails, heightM from the rails' bottom).
 */
public record ChassisStationDesign(String name, Kind kind, double xM, double heightM, ChassisSettingsDesign.RailSection crossSection, double crossHeightM,
                                   double crossWidthM, double crossWallM, Brace brace, double bayHeightM, double bayTopWidthM, boolean openTop)
        implements EngineComponent, RecordEditor.Shown {
    public static final String TYPE = "hdfv:chassis_station";

    public enum Kind { AXLE, CROSSMEMBER, ENGINE_MOUNT, BODY_MOUNT, FIFTH_WHEEL, KINGPIN, LANDING_LEGS }

    public enum Brace { NONE, SINGLE, X }

    public ChassisStationDesign {
        Objects.requireNonNull(name, "name"); Objects.requireNonNull(kind, "kind"); Objects.requireNonNull(crossSection, "crossSection");
        Objects.requireNonNull(brace, "brace");
    }

    @Override public String type() { return TYPE; }

    @Override public boolean shows(String parameter) {
        return switch (parameter) {
            case "heightM" -> kind != Kind.CROSSMEMBER && kind != Kind.BODY_MOUNT;
            default -> true;
        };
    }

    /** The crossmember's section. */
    public Section cross() { return crossSection.section(crossHeightM, crossWidthM, crossWallM); }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        if (!Double.isFinite(xM)) { issues.add(DesignIssue.invalid("xM", "A station is somewhere along the frame")); }
        if (!(Math.abs(heightM) <= 1.5)) { issues.add(DesignIssue.invalid("heightM", "A mount is within 1.5 m of the rails")); }
        Rules.positive(issues, "crossHeightM", crossHeightM);
        Rules.positive(issues, "crossWidthM", crossWidthM);
        if (!(crossWallM > 0 && crossWallM < 0.5 * Math.min(crossHeightM, crossWidthM))) { issues.add(DesignIssue.invalid("crossWallM", "The wall is thinner than half the section")); }
        if (!(bayHeightM >= 0.1 && bayHeightM <= 3)) { issues.add(DesignIssue.invalid("bayHeightM", "A tube frame's ring is 0.1 to 3 m high")); }
        if (!(bayTopWidthM >= 0.1 && bayTopWidthM <= 3)) { issues.add(DesignIssue.invalid("bayTopWidthM", "A tube frame's ring is 0.1 to 3 m wide at its top")); }
        return issues;
    }

    @Override public double massKg() { return 0; }

    @Override public Map<String, Object> parameters() {
        return Fields.object("kind", lower(kind), "xM", xM, "heightM", heightM, "crossSection", lower(crossSection), "crossHeightM", crossHeightM,
                "crossWidthM", crossWidthM, "crossWallM", crossWallM, "brace", lower(brace), "bayHeightM", bayHeightM, "bayTopWidthM", bayTopWidthM, "openTop", openTop);
    }

    private static String lower(Enum<?> e) { return e.name().toLowerCase(java.util.Locale.ROOT); }

    static ChassisStationDesign read(String name, Fields f) {
        ChassisStationDesign d = defaults(Rules.choice(f, "kind", Kind.class, Kind.CROSSMEMBER), 0);
        return new ChassisStationDesign(name, d.kind, f.number("xM", d.xM), f.number("heightM", d.heightM),
                Rules.choice(f, "crossSection", ChassisSettingsDesign.RailSection.class, d.crossSection), f.number("crossHeightM", d.crossHeightM),
                f.number("crossWidthM", d.crossWidthM), f.number("crossWallM", d.crossWallM), Rules.choice(f, "brace", Brace.class, d.brace),
                f.number("bayHeightM", d.bayHeightM), f.number("bayTopWidthM", d.bayTopWidthM), f.bool("openTop", d.openTop));
    }

    /** A new station of a kind at x: a 100 x 50 x 3 mm tube crossmember (an axle's 120 mm, hung 0.1 m under the rails). */
    public static ChassisStationDesign defaults(Kind kind, double x) {
        String name = kind.name().toLowerCase(java.util.Locale.ROOT);
        ChassisSettingsDesign.RailSection tube = ChassisSettingsDesign.RailSection.RECT_TUBE;
        return switch (kind) {
            case AXLE -> new ChassisStationDesign(name, kind, x, -0.1, tube, 0.12, 0.06, 0.003, Brace.NONE, 0.5, 0.9, false);
            case CROSSMEMBER -> new ChassisStationDesign(name, kind, x, 0, tube, 0.1, 0.05, 0.003, Brace.NONE, 0.5, 0.9, false);
            case ENGINE_MOUNT -> new ChassisStationDesign(name, kind, x, 0.05, tube, 0.1, 0.05, 0.003, Brace.NONE, 0.5, 0.9, false);
            case BODY_MOUNT -> new ChassisStationDesign(name, kind, x, 0, tube, 0.08, 0.04, 0.002, Brace.NONE, 0.5, 0.9, false);
            // 0.18: a fifth wheel's base on a 30 mm mounting plate over a heavy crossmember; a kingpin plate flush under the rails;
            // landing legs hung 0.1 m under them
            case FIFTH_WHEEL -> new ChassisStationDesign(name, kind, x, 0.03, tube, 0.2, 0.1, 0.006, Brace.NONE, 0.5, 0.9, false);
            case KINGPIN -> new ChassisStationDesign(name, kind, x, 0, tube, 0.2, 0.12, 0.008, Brace.NONE, 0.5, 0.9, false);
            case LANDING_LEGS -> new ChassisStationDesign(name, kind, x, -0.1, tube, 0.15, 0.08, 0.005, Brace.NONE, 0.5, 0.9, false);
        };
    }

    /** The same moved along the frame. */
    public ChassisStationDesign at(double x) {
        return new ChassisStationDesign(name, kind, x, heightM, crossSection, crossHeightM, crossWidthM, crossWallM, brace, bayHeightM, bayTopWidthM, openTop);
    }
}
