package dev.mcmod.hdfv.part.machine;

import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.vehicle.DesignException;
import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;
import dev.mcmod.aerocore.api.vehicle.MassProperties;
import dev.mcmod.aerocore.api.vehicle.Part;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.part.BoxPart;
import dev.mcmod.hdfv.part.air.Components;
import dev.mcmod.hdfv.part.engine.EngineComponent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * A machine's engine hood (hdfv:engine_hood, 2026-11-22, 引擎蓋; the user: 「引擎那邊應該要有殻才對」): the sheet-metal
 * enclosure over a machine's engine, pumps and tanks (an excavator's rear behind its cab, a loader's or a roller's
 * behind its cab, a dozer's ahead of it), centred on the part origin: lengthM along x (its front at +x), heightM from its
 * bottom edge to its top at its highest, widthM across. It has no floor (it stands on the deck or the frame) and the parts
 * it covers are parts of their own set inside it. In the world it is AeroCore's BoxPart, not closed (water comes in under
 * it: no buoyancy), a cube's drag, contact spheres round it; people can stand on it.
 *
 * <p>Its shape: the top falls by topDropM toward the grille's end (a loader's hood toward its rear grille so the operator
 * sees behind; a dozer's toward its front grille; with side grilles, toward the rear), drawn so and taking nothing off the
 * box; the rear corners are rounded in plan by tailRadiusM (an excavator's tail following its swing circle). The
 * radiator breathes through the grille: louvred doors on both sides (SIDES), the rear (REAR) or the front (FRONT). The
 * exhaust stack stands stackM over the top (0: none).
 *
 * <p>Its mass: the frame round its edges (FRAME_KG_PER_M, pressed channel), the panels and doors not louvred (PANEL_M of
 * steel), the louvres (GRILLE_KG_PER_M2), the sound lining inside the walls and top (LINING_KG_PER_M2), the stack
 * (STACK_KG_PER_M over its length and STACK_INSIDE_M under the top, its rain cap) and the hinges, gas struts and latches
 * (FITTINGS_KG). The samples: a 20 t excavator's about 0.46 t, a mini excavator's about 0.12 t, an 18 t wheel loader's
 * about 0.30 t.
 *
 * <p>Ranges (EXTRAPOLATION) from makers' machines, mini excavators to 50 t excavators, wheel loaders, dozers and rollers
 * (CAT, Komatsu, Volvo data sheets): 0.8 to 4 m long, 0.8 to 3.5 m wide, 0.5 to 2 m high, a top falling up to 0.5 m, a
 * tail radius up to 1.5 m, a stack up to 1.5 m. INVALID: a side not positive, a drop outside 0 to 0.6 of the height, a
 * tail radius outside 0 to half the width or half the length, a stack below 0 or over 3 m.
 */
public record EngineHoodDesign(String name, double lengthM, double widthM, double heightM, double topDropM, double tailRadiusM, Grille grille, double stackM)
        implements PartDesign {
    public static final String TYPE = "hdfv:engine_hood";

    /** Where the radiator breathes: louvred doors on both sides, the rear or the front. */
    public enum Grille { SIDES, REAR, FRONT }

    /** Panels (m of steel), the frame round the edges (kg/m), louvres and the sound lining (kg/m^2). */
    public static final double PANEL_M = 0.002, FRAME_KG_PER_M = 4, GRILLE_KG_PER_M2 = 12, LINING_KG_PER_M2 = 3;
    /** The stack (kg/m, m of it under the top), its rain cap (kg); the hinges, struts and latches (kg). */
    public static final double STACK_KG_PER_M = 6.5, STACK_INSIDE_M = 0.3, RAIN_CAP_KG = 3, FITTINGS_KG = 15;
    /** The louvred share: of each side's straight wall (SIDES), of the end wall (REAR, FRONT). */
    public static final double SIDE_GRILLE_SHARE = 0.35, END_GRILLE_SHARE = 0.6;
    /** A cube's drag coefficient (BoxDesign's). */
    public static final double DRAG = 1.05;

    public EngineHoodDesign {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(grille, "grille");
    }

    @Override public String type() { return TYPE; }

    public Vec3d sizeM() { return new Vec3d(lengthM, heightM, widthM); }

    /** Whether the top falls toward the front (a front grille) rather than the rear. */
    public boolean fallsForward() { return grille == Grille.FRONT; }

    /** The top's height over the bottom edge at x along the length (-lengthM/2 to lengthM/2), m. */
    public double topAt(double x) {
        double f = Math.max(0, Math.min(1, x / lengthM + 0.5));   // 0 at the rear, 1 at the front
        return heightM - topDropM * (fallsForward() ? f : 1 - f);
    }

    /** The half width at x along the length, m: narrowing round the rear corners. */
    public double halfWidthAt(double x) {
        double r = tailRadiusM, d = x + lengthM / 2;   // from the rear end
        if (r <= 0 || d >= r) { return widthM / 2; }
        double c = r - Math.max(d, 0);
        return widthM / 2 - r + Math.sqrt(Math.max(0, r * r - c * c));
    }

    /** The area it covers, m^2. */
    public double planM2() { return lengthM * widthM - 2 * (1 - Math.PI / 4) * tailRadiusM * tailRadiusM; }

    /** Round its edge, m. */
    public double perimeterM() { return 2 * (lengthM + widthM) - 2 * (2 - Math.PI / 2) * tailRadiusM; }

    /** The walls (at their mean height) and the top, m^2. */
    public double skinM2() { return perimeterM() * (heightM - topDropM / 2) + planM2() * Math.hypot(1, topDropM / lengthM); }

    /** The louvred area, m^2. */
    public double grilleM2() {
        double end = heightM - topDropM;   // the grille's end is the low one
        return switch (grille) {
            case SIDES -> 2 * SIDE_GRILLE_SHARE * (lengthM - tailRadiusM) * (heightM - topDropM / 2);
            case REAR -> END_GRILLE_SHARE * (widthM - 2 * tailRadiusM) * end;
            case FRONT -> END_GRILLE_SHARE * widthM * end;
        };
    }

    /** The frame's channel, m: four corner posts and the top's and bottom's rings. */
    public double frameM() { return 4 * heightM + 2 * perimeterM(); }

    /** The pieces' masses, kg (frame, panels, louvres, lining, stack, fittings). */
    public Map<String, Double> massBreakdownKg() {
        Map<String, Double> m = new LinkedHashMap<>();
        m.put("hood_frame", frameM() * FRAME_KG_PER_M);
        m.put("hood_panels", (skinM2() - grilleM2()) * PANEL_M * MachineRules.STEEL_KG_PER_M3);
        m.put("hood_grille", grilleM2() * GRILLE_KG_PER_M2);
        m.put("hood_lining", skinM2() * LINING_KG_PER_M2);
        if (stackM > 0) { m.put("hood_stack", (stackM + STACK_INSIDE_M) * STACK_KG_PER_M + RAIN_CAP_KG); }
        m.put("hood_fittings", FITTINGS_KG);
        return m;
    }

    public double massKg() {
        double sum = 0;
        for (double x : massBreakdownKg().values()) { sum += x; }
        return sum;
    }

    public MassProperties mass() { return Components.box(massKg(), Vec3d.ZERO, sizeM()); }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        EngineComponent.Rules.positive(issues, "lengthM", lengthM);
        EngineComponent.Rules.positive(issues, "widthM", widthM);
        EngineComponent.Rules.positive(issues, "heightM", heightM);
        if (!issues.isEmpty()) { return issues; }
        if (!(topDropM >= 0 && topDropM <= 0.6 * heightM)) { issues.add(DesignIssue.invalid("topDropM", "The top falls 0 to 0.6 of the height")); }
        if (!(tailRadiusM >= 0 && tailRadiusM <= Math.min(widthM, lengthM) / 2)) {
            issues.add(DesignIssue.invalid("tailRadiusM", "The tail's corners are rounded by 0 to half the width or the length"));
        }
        if (!(stackM >= 0 && stackM <= 3)) { issues.add(DesignIssue.invalid("stackM", "The stack stands 0 to 3 m over the top")); }
        if (!issues.isEmpty()) { return issues; }
        String makers = "makers' machines, mini excavators to 50 t excavators, wheel loaders, dozers and rollers (CAT, Komatsu, Volvo data sheets)";
        DesignIssue.range(issues, "lengthM", lengthM, 0.8, 4.0, makers);
        DesignIssue.range(issues, "widthM", widthM, 0.8, 3.5, makers);
        DesignIssue.range(issues, "heightM", heightM, 0.5, 2.0, makers);
        DesignIssue.range(issues, "topDropM", topDropM, 0, 0.5, makers);
        DesignIssue.range(issues, "tailRadiusM", tailRadiusM, 0, 1.5, makers);
        DesignIssue.range(issues, "stackM", stackM, 0, 1.5, makers);
        return issues;
    }

    @Override public Part compile() {
        List<DesignIssue> issues = check();
        DesignException.throwIfInvalid(issues);
        return new BoxPart(this, sizeM(), DRAG, false, 1, mass());
    }

    @Override public Map<String, Object> parameters() {
        return Fields.object("lengthM", lengthM, "widthM", widthM, "heightM", heightM, "topDropM", topDropM, "tailRadiusM", tailRadiusM,
                "grille", grille.name().toLowerCase(Locale.ROOT), "stackM", stackM);
    }

    public static PartDesign read(Fields f) {
        EngineHoodDesign d = standard();
        return new EngineHoodDesign(f.string("name", d.name), f.number("lengthM", d.lengthM), f.number("widthM", d.widthM), f.number("heightM", d.heightM),
                f.number("topDropM", d.topDropM), f.number("tailRadiusM", d.tailRadiusM), EngineComponent.Rules.choice(f, "grille", Grille.class, d.grille),
                f.number("stackM", d.stackM));
    }

    /** The standard hood: a 20 t excavator's (SampleMachinery.excavatorHood). */
    public static EngineHoodDesign standard() { return SampleMachinery.excavatorHood(); }
}
