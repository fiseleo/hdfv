package dev.mcmod.hdfv.part.wheel;

import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;
import dev.mcmod.hdfv.part.engine.EngineComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A wheel (輪圈): its width (the diameter is the tyre's), how it is made, and its spokes (the look). Mass, unless given:
 * MASS_PER_AREA of the material times rim diameter times width (estimates fitted to 16 x 6.5 in wheels: pressed steel
 * 9.5 kg, cast aluminium 8.5 kg, forged aluminium 7 kg; magnesium below them). Inertia about the axle: three quarters of
 * the mass in the barrel at the rim's radius, the rest a disc (0.875 m r^2).
 * <p>
 * 0.12, looks only: the wheel's finish (automatic: by how it is made, as before; chrome, silver, black or gold paint).
 * <p>
 * 0.16, looks only (the user's 「輪圈與輪胎外觀」; the refit shop and the paint booth change them): the face's style
 * (automatic: as before, a pressed steel disc or straight spokes by how it is made; a steel wheel's dish, plain spokes,
 * twin spokes, many thin spokes, a mesh, turbine fins, a flat disc, an off-road beadlock), its colour (blank: by the
 * finish) and how deep the face sits in the rim (the lip; the track is the axle's). Defaults are not written, so a
 * wheel without them has 0.12's JSON.
 */
public record RimDesign(String name, double rimWidthIn, Material material, int spokes, double massGivenKg, Look rimFinish, Style rimStyle, String rimColor,
                        double lipDepthM) implements EngineComponent, dev.mcmod.hdfv.design.RecordEditor.Shown {
    public static final String TYPE = "hdfv:rim";

    /** 0.12: the wheel's finish. */
    public enum Look { AUTO, CHROME, SILVER, BLACK, GOLD }

    /** 0.16: the face's style. */
    public enum Style { AUTO, STEEL, SPOKE, TWIN_SPOKE, MULTI_SPOKE, MESH, TURBINE, DISC, BEADLOCK }

    /** 0.11's wheel, finished by how it is made. */
    public RimDesign(String name, double rimWidthIn, Material material, int spokes, double massGivenKg) {
        this(name, rimWidthIn, material, spokes, massGivenKg, Look.AUTO);
    }

    /** 0.12's wheel, its face by how it is made. */
    public RimDesign(String name, double rimWidthIn, Material material, int spokes, double massGivenKg, Look rimFinish) {
        this(name, rimWidthIn, material, spokes, massGivenKg, rimFinish, Style.AUTO, "", 0);
    }

    /** How a wheel is made, and its mass per rim diameter x width (kg/m^2). */
    public enum Material {
        STEEL(142), CAST_ALLOY(127), FORGED_ALLOY(105), MAGNESIUM(90);

        final double perArea;

        Material(double perArea) { this.perArea = perArea; }
    }

    public RimDesign {
        Objects.requireNonNull(name, "name"); Objects.requireNonNull(material, "material"); Objects.requireNonNull(rimFinish, "rimFinish");
        Objects.requireNonNull(rimStyle, "rimStyle");
        if (rimColor == null) { rimColor = ""; }
    }

    /** The spoke count means nothing to a disc, a mesh or a steel wheel's dish. */
    @Override public boolean shows(String parameter) {
        return !parameter.equals("spokes") || rimStyle != Style.DISC && rimStyle != Style.MESH;
    }

    @Override public String type() { return TYPE; }

    public double widthM() { return rimWidthIn * TyreDesign.INCH; }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        if (!(rimWidthIn > 1 && rimWidthIn < 40)) { issues.add(DesignIssue.invalid("rimWidthIn", "The rim must be 1 to 40 in wide")); }
        Rules.count(issues, "spokes", spokes, 3, 40);
        Rules.mass(issues, massGivenKg);
        if (!dev.mcmod.hdfv.design.RecordEditor.isColor(rimColor, true)) { issues.add(DesignIssue.invalid("rimColor", "A colour is #RRGGBB, or blank")); }
        if (!(lipDepthM >= 0 && lipDepthM <= 0.4 * widthM())) { issues.add(DesignIssue.invalid("lipDepthM", "The face sits 0 to 40 % of the rim's width in")); }
        return issues;
    }

    /** Mass of one wheel on a rim of a diameter (m), kg. */
    public double massKg(double rimDiameterM) { return Double.isNaN(massGivenKg) ? material.perArea * rimDiameterM * widthM() : massGivenKg; }
    @Override public double massKg() { return Double.isNaN(massGivenKg) ? 0 : massGivenKg; }

    /** Its moment of inertia about the axle on a rim of a diameter (m), kg m^2. */
    public double inertiaKgM2(double rimDiameterM) { return 0.875 * massKg(rimDiameterM) * Math.pow(rimDiameterM / 2, 2); }

    @Override public Map<String, Object> parameters() {
        Map<String, Object> m = Fields.object("rimWidthIn", rimWidthIn, "material", material.name().toLowerCase(), "spokes", spokes, "massKg", Rules.mass(massGivenKg),
                "rimFinish", rimFinish.name().toLowerCase());
        if (rimStyle != Style.AUTO) { m.put("rimStyle", rimStyle.name().toLowerCase()); }
        if (!rimColor.isEmpty()) { m.put("rimColor", rimColor); }
        if (lipDepthM != 0) { m.put("lipDepthM", lipDepthM); }
        return m;
    }

    public static RimDesign read(String name, Fields f) {
        return new RimDesign(name, f.number("rimWidthIn", 6.5), Rules.choice(f, "material", Material.class, Material.STEEL), f.integer("spokes", 5), Rules.massOr(f),
                Rules.choice(f, "rimFinish", Look.class, Look.AUTO), Rules.choice(f, "rimStyle", Style.class, Style.AUTO), f.string("rimColor", ""),
                f.number("lipDepthM", 0));
    }
}
