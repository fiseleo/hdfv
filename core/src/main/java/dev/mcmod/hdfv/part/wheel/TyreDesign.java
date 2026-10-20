package dev.mcmod.hdfv.part.wheel;

import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;
import dev.mcmod.aerocore.api.wheel.Tyre;
import dev.mcmod.hdfv.part.engine.EngineComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A tyre (輪胎) as its sidewall reads: construction, section width, aspect ratio and rim diameter (205/55R16 is
 * radial, 0.205 m, 0.55, 16 in), inflation and rated pressure, tread and compound. Everything it does on the ground is
 * AeroCore's Tyre (stiffness, grip on each surface, rolling resistance, hydroplaning) from these. Mass: the tyre's own
 * estimate (AeroCore: 240 kg per cubic metre of its annulus), one tyre.
 * <p>
 * 0.16, looks only: lettering round its sidewalls (Lettering's letters, 10 at most; blank: the plain moulding as
 * before) and its colour (blank: white). Not written when blank, so a tyre without them has 0.15's JSON.
 */
public record TyreDesign(String name, Tyre.Construction construction, double sectionWidthM, double aspectRatio, double rimDiameterIn, double pressurePa,
                         double ratedPressurePa, Tyre.Tread tread, double compoundGrip, String sideText, String sideTextColor)
        implements EngineComponent, dev.mcmod.hdfv.design.RecordEditor.Shown {
    public static final String TYPE = "hdfv:tyre";
    public static final double INCH = 0.0254;

    public TyreDesign {
        Objects.requireNonNull(name, "name"); Objects.requireNonNull(construction, "construction"); Objects.requireNonNull(tread, "tread");
        if (sideText == null) { sideText = ""; }
        if (sideTextColor == null) { sideTextColor = ""; }
    }

    /** 0.15's tyre, its sidewalls plain. */
    public TyreDesign(String name, Tyre.Construction construction, double sectionWidthM, double aspectRatio, double rimDiameterIn, double pressurePa,
                      double ratedPressurePa, Tyre.Tread tread, double compoundGrip) {
        this(name, construction, sectionWidthM, aspectRatio, rimDiameterIn, pressurePa, ratedPressurePa, tread, compoundGrip, "", "");
    }

    /** The lettering's colour means something only with lettering. */
    @Override public boolean shows(String parameter) { return !parameter.equals("sideTextColor") || !sideText.isEmpty(); }

    @Override public String type() { return TYPE; }

    public double rimDiameterM() { return rimDiameterIn * INCH; }

    public Tyre.Spec spec() {
        return new Tyre.Spec(construction, sectionWidthM, aspectRatio, rimDiameterM(), pressurePa, ratedPressurePa, tread, compoundGrip);
    }

    public Tyre tyre() { return new Tyre(spec()); }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        if (!(sectionWidthM > 0.02 && sectionWidthM < 2)) { issues.add(DesignIssue.invalid("sectionWidthM", "The section width must be 20 mm to 2 m")); }
        if (!(aspectRatio > 0.15 && aspectRatio <= 1.5)) { issues.add(DesignIssue.invalid("aspectRatio", "The aspect ratio must be 0.15 to 1.5")); }
        if (!(rimDiameterIn > 2 && rimDiameterIn < 118)) { issues.add(DesignIssue.invalid("rimDiameterIn", "The rim must be 2 to 118 in")); }
        if (!(pressurePa > 10e3 && pressurePa < 3e6)) { issues.add(DesignIssue.invalid("pressurePa", "The pressure must be 10 kPa to 3 MPa")); }
        if (!(ratedPressurePa > 10e3 && ratedPressurePa < 3e6)) { issues.add(DesignIssue.invalid("ratedPressurePa", "The rated pressure must be 10 kPa to 3 MPa")); }
        if (!(compoundGrip > 0.3 && compoundGrip < 2)) { issues.add(DesignIssue.invalid("compoundGrip", "The compound's grip must be 0.3 to 2")); }
        if (!sideText.equals(dev.mcmod.hdfv.look.Lettering.clean(sideText))) { issues.add(DesignIssue.invalid("sideText", "Letters A-Z, digits, hyphens and spaces, 10 at most")); }
        if (!dev.mcmod.hdfv.design.RecordEditor.isColor(sideTextColor, true)) { issues.add(DesignIssue.invalid("sideTextColor", "A colour is #RRGGBB, or blank")); }
        if (!issues.isEmpty()) { return issues; }
        if (pressurePa > ratedPressurePa * 1.0001) { issues.add(DesignIssue.invalid("pressurePa", "Inflated above the tyre's rated pressure")); }
        if (construction == Tyre.Construction.AIRCRAFT) {
            DesignIssue.range(issues, "pressurePa", pressurePa, 150e3, 1.6e6, "aircraft tyres (Goodyear aircraft tire data book: 6.00-6 at 200-300 kPa to airliners near 1.5 MPa)");
        } else {
            DesignIssue.range(issues, "pressurePa", pressurePa, 120e3, 450e3, "road tyres (passenger 180-300 kPa, light truck up to 450 kPa)");
            DesignIssue.range(issues, "aspectRatio", aspectRatio, 0.25, 0.85, "road tyre series /25 to /85");
        }
        return issues;
    }

    /** Mass of one tyre, kg (0 for a tyre too broken to size). */
    @Override public double massKg() {
        try { return tyre().massKg(); } catch (IllegalArgumentException e) { return 0; }
    }

    @Override public Map<String, Object> parameters() {
        Map<String, Object> m = Fields.object("construction", construction.name().toLowerCase(), "sectionWidthM", sectionWidthM, "aspectRatio", aspectRatio,
                "rimDiameterIn", rimDiameterIn, "pressurePa", pressurePa, "ratedPressurePa", ratedPressurePa, "tread", tread.name().toLowerCase(),
                "compoundGrip", compoundGrip);
        if (!sideText.isEmpty()) { m.put("sideText", sideText); }
        if (!sideTextColor.isEmpty()) { m.put("sideTextColor", sideTextColor); }
        return m;
    }

    public static TyreDesign read(String name, Fields f) {
        return new TyreDesign(name, Rules.choice(f, "construction", Tyre.Construction.class, Tyre.Construction.RADIAL), f.number("sectionWidthM", 0.205),
                f.number("aspectRatio", 0.55), f.number("rimDiameterIn", 16), f.number("pressurePa", 230e3), f.number("ratedPressurePa", 300e3),
                Rules.choice(f, "tread", Tyre.Tread.class, Tyre.Tread.ROAD), f.number("compoundGrip", 1), f.string("sideText", ""), f.string("sideTextColor", ""));
    }
}
