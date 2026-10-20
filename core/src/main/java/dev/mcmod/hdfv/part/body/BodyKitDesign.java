package dev.mcmod.hdfv.part.body;

import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;
import dev.mcmod.hdfv.design.RecordEditor;
import dev.mcmod.hdfv.part.engine.EngineComponent;
import dev.mcmod.hdfv.part.engine.EngineMaterial;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 0.16: a car body's add-ons (the "kit" page; the user's 「外掛空力套件」, fitted at the design table or a refit shop):
 * <ul>
 * <li>Front: the bumper's style (the body's own, a sport bumper with a lip and vents, an off-road bull bar), an air dam
 *   under it (damM below the floor), a splitter ahead of it (splitterM long).</li>
 * <li>Sides: skirts under the sills (skirtM deep; they seal the floor's sides, up to SKIRT_SEAL of it at SKIRT_FULL_M),
 *   fender flares (flareM out from each arch).</li>
 * <li>Rear: a diffuser (its ramp's angle and length), a lip spoiler on the deck (spoilerM tall), a wing (span, chord,
 *   angle of attack for downforce, its chord's height above the deck, end plates).</li>
 * <li>Roof rack (bars or a basket), a hood scoop (scoopM tall), exhaust tips (count and diameter).</li>
 * <li>Its material (moulded plastic or composite, aluminium, steel; the pieces' thickness by it) and colour (blank:
 *   the body's).</li>
 * </ul>
 * What it does to the air comes from AeroCore's RoadAero (KitLayout makes its elements from the body's shape); its mass
 * from its pieces' areas (KitLayout). All zero and the stock bumper is no kit: such a body writes no "kit" key, so its
 * JSON and everything compiled from it are as before.
 */
public record BodyKitDesign(String name, Bumper bumper, double damM, double splitterM, double skirtM, double flareM, double diffuserDeg,
                            double diffuserLengthM, double spoilerM, double wingSpanM, double wingChordM, double wingAngleDeg, double wingHeightM,
                            double endplateM, RoofRack roofRack, double scoopM, int exhaustTips, double exhaustDiameterM, EngineMaterial material,
                            String paintColor) implements EngineComponent, RecordEditor.Shown {
    public static final String TYPE = "hdfv:body_kit", PAGE = "kit";

    public enum Bumper { STOCK, SPORT, OFFROAD }
    public enum RoofRack { NONE, BARS, BASKET }

    /** Skirts this deep seal the floor's sides as far as skirts do (fixed skirts leave a gap to the road). */
    public static final double SKIRT_FULL_M = 0.1, SKIRT_SEAL = 0.6;
    /** The wing's section: camber and thickness (shares of the chord; a NACA 6412-like section). */
    public static final double WING_CAMBER = 0.06, WING_THICKNESS = 0.12;

    public static final BodyKitDesign NONE = new BodyKitDesign(PAGE, Bumper.STOCK, 0, 0, 0, 0, 0, 0.5, 0, 0, 0.25, 8, 0.25, 0.15, RoofRack.NONE, 0, 0, 0.08,
            EngineMaterial.GFRP, "");

    public BodyKitDesign {
        Objects.requireNonNull(name, "name"); Objects.requireNonNull(bumper, "bumper"); Objects.requireNonNull(roofRack, "roofRack");
        Objects.requireNonNull(material, "material");
        if (paintColor == null) { paintColor = ""; }
    }

    @Override public String type() { return TYPE; }

    /** Whether it adds nothing (the stock bumper, nothing else). */
    public boolean none() { return this.withName(PAGE).equals(NONE); }

    private BodyKitDesign withName(String n) {
        return new BodyKitDesign(n, bumper, damM, splitterM, skirtM, flareM, diffuserDeg, diffuserLengthM, spoilerM, wingSpanM, wingChordM, wingAngleDeg,
                wingHeightM, endplateM, roofRack, scoopM, exhaustTips, exhaustDiameterM, material, paintColor);
    }

    public boolean wing() { return wingSpanM > 0; }
    public boolean diffuser() { return diffuserDeg > 0; }

    /**
     * How much of the gap under the sills the skirts close where they run (0 to SKIRT_SEAL); KitLayout scales it by
     * their share of the floor's length (they run only between the arches) for AeroCore's leak area.
     */
    public double sealing() { return SKIRT_SEAL * Math.clamp(skirtM / SKIRT_FULL_M, 0, 1); }

    /** The wing's parameters and the diffuser's length mean something only when there is one. */
    @Override public boolean shows(String parameter) {
        return switch (parameter) {
            case "wingChordM", "wingAngleDeg", "wingHeightM", "endplateM" -> wing();
            case "diffuserLengthM" -> diffuser();
            case "exhaustDiameterM" -> exhaustTips > 0;
            default -> true;
        };
    }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        for (String[] p : new String[][]{{"damM"}, {"splitterM"}, {"skirtM"}, {"flareM"}, {"spoilerM"}, {"wingSpanM"}, {"scoopM"}}) {
            double v = value(p[0]);
            if (!(v >= 0) || !Double.isFinite(v)) { issues.add(DesignIssue.invalid(p[0], p[0] + " must not be negative")); }
        }
        if (!(diffuserDeg >= 0 && diffuserDeg < 30)) { issues.add(DesignIssue.invalid("diffuserDeg", "A diffuser's ramp rises 0 to 30 degrees")); }
        if (diffuser()) { Rules.positive(issues, "diffuserLengthM", diffuserLengthM); }
        if (wing()) {
            Rules.positive(issues, "wingChordM", wingChordM);
            Rules.positive(issues, "wingHeightM", wingHeightM);
            Rules.nonnegative(issues, "endplateM", endplateM);
            if (!(wingAngleDeg >= -10 && wingAngleDeg <= 30)) { issues.add(DesignIssue.invalid("wingAngleDeg", "A wing's angle is -10 to 30 degrees")); }
            if (wingChordM > 0 && wingSpanM / wingChordM < 1) { issues.add(DesignIssue.invalid("wingSpanM", "A wing spans at least its chord")); }
        }
        Rules.count(issues, "exhaustTips", exhaustTips, 0, 8);
        if (exhaustTips > 0) { Rules.positive(issues, "exhaustDiameterM", exhaustDiameterM); }
        material.check(issues, "material");
        if (!RecordEditor.isColor(paintColor, true)) { issues.add(DesignIssue.invalid("paintColor", "A colour is #RRGGBB, or blank")); }
        // the validated ranges of RoadAero's correlations (Cooper's diffusers, road cars' lips and dams)
        DesignIssue.range(issues, "diffuserDeg", diffuserDeg, 0, 20, "diffusers the floor model follows (Cooper et al.)");
        DesignIssue.range(issues, "damM", damM, 0, 0.15, "air dams on road cars");
        DesignIssue.range(issues, "spoilerM", spoilerM, 0, 0.12, "lip spoilers (Hucho)");
        return issues;
    }

    private double value(String p) {
        return switch (p) {
            case "damM" -> damM; case "splitterM" -> splitterM; case "skirtM" -> skirtM; case "flareM" -> flareM;
            case "spoilerM" -> spoilerM; case "wingSpanM" -> wingSpanM; default -> scoopM;
        };
    }

    /** Its mass comes with the body's (KitLayout's pieces); the page alone has none. */
    @Override public double massKg() { return 0; }

    @Override public Map<String, Object> parameters() {
        return Fields.object("bumper", bumper.name().toLowerCase(java.util.Locale.ROOT), "damM", damM, "splitterM", splitterM, "skirtM", skirtM,
                "flareM", flareM, "diffuserDeg", diffuserDeg, "diffuserLengthM", diffuserLengthM, "spoilerM", spoilerM, "wingSpanM", wingSpanM,
                "wingChordM", wingChordM, "wingAngleDeg", wingAngleDeg, "wingHeightM", wingHeightM, "endplateM", endplateM,
                "roofRack", roofRack.name().toLowerCase(java.util.Locale.ROOT), "scoopM", scoopM, "exhaustTips", exhaustTips,
                "exhaustDiameterM", exhaustDiameterM, "material", material.toJson(), "paintColor", paintColor);
    }

    static BodyKitDesign read(String name, Fields f) {
        BodyKitDesign d = NONE;
        return new BodyKitDesign(name, Rules.choice(f, "bumper", Bumper.class, d.bumper), f.number("damM", d.damM), f.number("splitterM", d.splitterM),
                f.number("skirtM", d.skirtM), f.number("flareM", d.flareM), f.number("diffuserDeg", d.diffuserDeg), f.number("diffuserLengthM", d.diffuserLengthM),
                f.number("spoilerM", d.spoilerM), f.number("wingSpanM", d.wingSpanM), f.number("wingChordM", d.wingChordM),
                f.number("wingAngleDeg", d.wingAngleDeg), f.number("wingHeightM", d.wingHeightM), f.number("endplateM", d.endplateM),
                Rules.choice(f, "roofRack", RoofRack.class, d.roofRack), f.number("scoopM", d.scoopM), f.integer("exhaustTips", d.exhaustTips),
                f.number("exhaustDiameterM", d.exhaustDiameterM), Rules.material(f, "material", d.material), f.string("paintColor", d.paintColor));
    }
}
