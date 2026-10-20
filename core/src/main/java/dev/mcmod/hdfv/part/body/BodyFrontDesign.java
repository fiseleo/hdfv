package dev.mcmod.hdfv.part.body;

import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;
import dev.mcmod.hdfv.design.RecordEditor;
import dev.mcmod.hdfv.part.engine.EngineComponent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 0.18: a car body's front end (the "front" page; the user's 「車頭應該是長這樣」, with an Astra-like saloon, a Panda-like
 * box and a cab-over truck to go by): the grille, the headlamps, the bumper, the lower intake, the fog lamps, a bulge on
 * the hood. Every choice may be AUTO and every number blank (NaN, RecordEditor.BlankIsAuto): look/FrontLayout then
 * picks them from the front's shape (a rounded nose: wrap-round lamp clusters, a slim grille with a badge, a bumper in
 * the body's colour with the plate, a wide intake and round fog lamps; a square front: square lamps flush with a grille
 * across it, a black bumper with a slot and square corner lamps; a cab-over cab: a grille panel, lamps in a stepped
 * bumper's corners). Looks only: nothing here weighs or meets the air.
 * <p>
 * All automatic is how every car's front was drawn from 0.18 on, and writes no "front" key: a body's JSON, its
 * workpieces' hashes and everything compiled from it are as before. Otherwise only what is not automatic is written.
 */
public record BodyFrontDesign(String name, Grille grille, double grilleWidth, double grilleHeight, int grilleSlats, String grilleColor, boolean emblem,
                              Headlamps headlamps, double lampWidth, double lampHeight, double lampWrapM,
                              Bumper bumper, double bumperHeight, double bumperOutM, String bumperColor,
                              Intake intake, double intakeHeightM, Fog fog, double bulgeM)
        implements EngineComponent, RecordEditor.Shown, RecordEditor.BlankIsAuto {
    public static final String TYPE = "hdfv:body_front", PAGE = "front";

    /** The grille: slats across, a mesh, slats across the whole front between the lamps, twin kidneys, a truck's panel. */
    public enum Grille { AUTO, NONE, SLATS, MESH, FULL_WIDTH, SPLIT, PANEL }
    /** The headlamps: clusters wrapping round the front's corners, square, round or twin round lamps on the front. */
    public enum Headlamps { AUTO, NONE, WRAP, SQUARE, ROUND, TWIN_ROUND }
    /** The bumper: the body's colour, black plastic, chrome, a truck's black bumper with steps. */
    public enum Bumper { AUTO, NONE, BODY, BLACK, CHROME, STEP }
    /** The intake low in the bumper: a slot, a wide mouth, a mesh. */
    public enum Intake { AUTO, NONE, SLOT, WIDE, MESH }
    /** The fog lamps in the bumper's corners. */
    public enum Fog { AUTO, NONE, ROUND, SQUARE }

    /** Every choice automatic, every number blank (and no bulge): FrontLayout's from the shape. */
    public static final BodyFrontDesign AUTO = new BodyFrontDesign(PAGE, Grille.AUTO, Double.NaN, Double.NaN, 0, "", true,
            Headlamps.AUTO, Double.NaN, Double.NaN, Double.NaN, Bumper.AUTO, Double.NaN, Double.NaN, "", Intake.AUTO, Double.NaN, Fog.AUTO, 0);

    public BodyFrontDesign {
        Objects.requireNonNull(name, "name"); Objects.requireNonNull(grille, "grille"); Objects.requireNonNull(headlamps, "headlamps");
        Objects.requireNonNull(bumper, "bumper"); Objects.requireNonNull(intake, "intake"); Objects.requireNonNull(fog, "fog");
        if (grilleColor == null) { grilleColor = ""; }
        if (bumperColor == null) { bumperColor = ""; }
        if (Double.isNaN(bulgeM)) { bulgeM = 0; }
    }

    @Override public String type() { return TYPE; }

    /** Whether everything is automatic (written as no "front" key). */
    public boolean auto() { return this.withName(PAGE).equals(AUTO); }

    private BodyFrontDesign withName(String n) {
        return new BodyFrontDesign(n, grille, grilleWidth, grilleHeight, grilleSlats, grilleColor, emblem, headlamps, lampWidth, lampHeight, lampWrapM,
                bumper, bumperHeight, bumperOutM, bumperColor, intake, intakeHeightM, fog, bulgeM);
    }

    /** What a choice's own numbers mean only with it. */
    @Override public boolean shows(String parameter) {
        return switch (parameter) {
            case "grilleWidth", "grilleHeight", "grilleSlats", "grilleColor", "emblem" -> grille != Grille.NONE;
            case "lampWidth", "lampHeight" -> headlamps != Headlamps.NONE;
            case "lampWrapM" -> headlamps == Headlamps.WRAP || headlamps == Headlamps.AUTO;
            case "bumperHeight", "bumperOutM", "bumperColor" -> bumper != Bumper.NONE;
            case "intakeHeightM" -> intake != Intake.NONE;
            default -> true;
        };
    }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        for (String[] s : new String[][]{{"grilleWidth"}, {"grilleHeight"}, {"lampWidth"}, {"lampHeight"}, {"bumperHeight"}}) {
            double v = share(s[0]);
            if (!Double.isNaN(v) && !(v > 0 && v <= 1)) { issues.add(DesignIssue.invalid(s[0], "A share is more than 0 and at most 1, or blank")); }
        }
        for (String[] s : new String[][]{{"lampWrapM", "0.6"}, {"bumperOutM", "0.3"}, {"intakeHeightM", "0.6"}, {"bulgeM", "0.15"}}) {
            double v = metres(s[0]), max = Double.parseDouble(s[1]);
            if (!Double.isNaN(v) && !(v >= 0 && v <= max)) { issues.add(DesignIssue.invalid(s[0], s[0] + " is 0 to " + s[1] + " m, or blank")); }
        }
        EngineComponent.Rules.count(issues, "grilleSlats", grilleSlats, 0, 16);
        if (!RecordEditor.isColor(grilleColor, true)) { issues.add(DesignIssue.invalid("grilleColor", "A colour is #RRGGBB, or blank")); }
        if (!RecordEditor.isColor(bumperColor, true)) { issues.add(DesignIssue.invalid("bumperColor", "A colour is #RRGGBB, or blank")); }
        return issues;
    }

    private double share(String p) {
        return switch (p) { case "grilleWidth" -> grilleWidth; case "grilleHeight" -> grilleHeight; case "lampWidth" -> lampWidth; case "lampHeight" -> lampHeight; default -> bumperHeight; };
    }

    private double metres(String p) {
        return switch (p) { case "lampWrapM" -> lampWrapM; case "bumperOutM" -> bumperOutM; case "intakeHeightM" -> intakeHeightM; default -> bulgeM; };
    }

    /** Looks only. */
    @Override public double massKg() { return 0; }

    /** Only what is not automatic (NaN is not JSON; a missing key reads back automatic). */
    @Override public Map<String, Object> parameters() {
        Map<String, Object> m = new LinkedHashMap<>();
        if (grille != Grille.AUTO) { m.put("grille", grille.name().toLowerCase(Locale.ROOT)); }
        if (!Double.isNaN(grilleWidth)) { m.put("grilleWidth", grilleWidth); }
        if (!Double.isNaN(grilleHeight)) { m.put("grilleHeight", grilleHeight); }
        if (grilleSlats != 0) { m.put("grilleSlats", grilleSlats); }
        if (!grilleColor.isEmpty()) { m.put("grilleColor", grilleColor); }
        if (!emblem) { m.put("emblem", false); }
        if (headlamps != Headlamps.AUTO) { m.put("headlamps", headlamps.name().toLowerCase(Locale.ROOT)); }
        if (!Double.isNaN(lampWidth)) { m.put("lampWidth", lampWidth); }
        if (!Double.isNaN(lampHeight)) { m.put("lampHeight", lampHeight); }
        if (!Double.isNaN(lampWrapM)) { m.put("lampWrapM", lampWrapM); }
        if (bumper != Bumper.AUTO) { m.put("bumper", bumper.name().toLowerCase(Locale.ROOT)); }
        if (!Double.isNaN(bumperHeight)) { m.put("bumperHeight", bumperHeight); }
        if (!Double.isNaN(bumperOutM)) { m.put("bumperOutM", bumperOutM); }
        if (!bumperColor.isEmpty()) { m.put("bumperColor", bumperColor); }
        if (intake != Intake.AUTO) { m.put("intake", intake.name().toLowerCase(Locale.ROOT)); }
        if (!Double.isNaN(intakeHeightM)) { m.put("intakeHeightM", intakeHeightM); }
        if (fog != Fog.AUTO) { m.put("fog", fog.name().toLowerCase(Locale.ROOT)); }
        if (bulgeM != 0) { m.put("bulgeM", bulgeM); }
        return m;
    }

    static BodyFrontDesign read(String name, Fields f) {
        BodyFrontDesign d = AUTO;
        return new BodyFrontDesign(name, Rules.choice(f, "grille", Grille.class, d.grille), f.number("grilleWidth", d.grilleWidth),
                f.number("grilleHeight", d.grilleHeight), f.integer("grilleSlats", d.grilleSlats), f.string("grilleColor", d.grilleColor),
                f.bool("emblem", d.emblem), Rules.choice(f, "headlamps", Headlamps.class, d.headlamps), f.number("lampWidth", d.lampWidth),
                f.number("lampHeight", d.lampHeight), f.number("lampWrapM", d.lampWrapM), Rules.choice(f, "bumper", Bumper.class, d.bumper),
                f.number("bumperHeight", d.bumperHeight), f.number("bumperOutM", d.bumperOutM), f.string("bumperColor", d.bumperColor),
                Rules.choice(f, "intake", Intake.class, d.intake), f.number("intakeHeightM", d.intakeHeightM), Rules.choice(f, "fog", Fog.class, d.fog),
                f.number("bulgeM", d.bulgeM));
    }
}
