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
 * A machine's operator cab (hdfv:operator_cab, 2026-11-22, 機械駕駛室): the closed cab an excavator, loader, roller or
 * crane is worked from, centred on the part origin (lengthM along x, its front at +x where the operator looks; heightM
 * from its floor to its roof; widthM across), its door on one side. The operator's seat is a part of its own set inside
 * it. In the world it is AeroCore's BoxPart: closed (it keeps water out), a cube's drag, contact spheres round it.
 *
 * <p>Its glazing: the front from the floor to the roof (FRONT_GLASS of it, the rest the frame), the sides and rear above
 * the sill (SILL_SHARE of the height up, the door's window included); its front's upper part leans back by frontRakeDeg
 * (an excavator's cab, to see the bucket over its head), drawn so and taking nothing off the box.
 *
 * <p>Its mass: the frame (four posts, the roof and floor rings) of tube by the protection (CAB_FRAME_KG_PER_M for a cab
 * with no rating; ROPS_FRAME_KG_PER_M for a roll-over protective structure, ISO 3471 / ISO 12117-2: a rule of the order
 * of 120 x 80 x 6 mm hollow sections; ROPS and FOPS add a falling-object guard over the roof, ISO 3449 level II,
 * FOPS_KG_PER_M2), the walls and roof not glazed as double-skinned panels (PANEL_M), the floor plate (FLOOR_M), the glass
 * (GLASS_M at GLASS_KG_M3) and the inside (INTERIOR_KG, INTERIOR_KG_PER_M2 of floor: trim, heating and air, the
 * controls' consoles). The samples: a 20 t excavator's ROPS cab about 0.71 t, a mini excavator's unrated cab about
 * 0.33 t, an 18 t loader's ROPS/FOPS cab about 1.08 t.
 *
 * <p>Ranges (EXTRAPOLATION) from makers' cabs, mini excavators to large wheel loaders and dozers (CAT, Komatsu, Volvo
 * data sheets): 0.9 to 2.5 m long, 0.8 to 2.0 m wide, 1.3 to 2.3 m from floor to roof, a front leaning back 0 to 30
 * degrees. INVALID: a side not positive, a rake outside 0 to 45 degrees.
 */
public record OperatorCabDesign(String name, double lengthM, double widthM, double heightM, double frontRakeDeg, Protection protection, DoorSide doorSide)
        implements PartDesign {
    public static final String TYPE = "hdfv:operator_cab";

    /** The frame's rating: none, roll-over protection (ROPS), roll-over and falling-object protection (ROPS and FOPS). */
    public enum Protection { CAB, ROPS, ROPS_FOPS }

    /** The side the door is on, looking forward. */
    public enum DoorSide { LEFT, RIGHT }

    /** The sill over the cab's height; the share of the front that is glass (the rest its frame). */
    public static final double SILL_SHARE = 0.45, FRONT_GLASS = 0.9;
    /** Frame tube, kg per metre, by protection; the FOPS guard over the roof, kg/m^2. */
    public static final double CAB_FRAME_KG_PER_M = 6, ROPS_FRAME_KG_PER_M = 22, FOPS_KG_PER_M2 = 60;
    /** Panels (double-skinned, m of steel), floor plate (m), glass (m, kg/m^3). */
    public static final double PANEL_M = 0.003, FLOOR_M = 0.006, GLASS_M = 0.006, GLASS_KG_M3 = 2500;
    /** The inside: kg, and kg per m^2 of floor. */
    public static final double INTERIOR_KG = 50, INTERIOR_KG_PER_M2 = 25;
    /** A cube's drag coefficient (BoxDesign's). */
    public static final double DRAG = 1.05;

    public OperatorCabDesign {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(protection, "protection");
        Objects.requireNonNull(doorSide, "doorSide");
    }

    @Override public String type() { return TYPE; }

    public Vec3d sizeM() { return new Vec3d(lengthM, heightM, widthM); }

    /** The glazed area, m^2: the front, the sides and rear above the sill. */
    public double glassM2() {
        double above = (1 - SILL_SHARE) * heightM;
        return FRONT_GLASS * widthM * heightM + 2 * lengthM * above + widthM * above;
    }

    /** The walls, roof and floor not glazed, m^2 (panels; the floor is its plate). */
    public double panelM2() { return 2 * lengthM * heightM + 2 * widthM * heightM - glassM2() + lengthM * widthM; }

    /** The frame's tube, m: four posts and the roof's and floor's rings. */
    public double frameM() { return 4 * heightM + 4 * (lengthM + widthM); }

    public double frameKgPerM() { return protection == Protection.CAB ? CAB_FRAME_KG_PER_M : ROPS_FRAME_KG_PER_M; }

    /** The pieces' masses, kg (frame, guard, panels, floor, glass, interior). */
    public Map<String, Double> massBreakdownKg() {
        double floor = lengthM * widthM;
        Map<String, Double> m = new LinkedHashMap<>();
        m.put("cab_frame", frameM() * frameKgPerM());
        if (protection == Protection.ROPS_FOPS) { m.put("cab_fops_guard", FOPS_KG_PER_M2 * floor); }
        m.put("cab_panels", panelM2() * PANEL_M * MachineRules.STEEL_KG_PER_M3);
        m.put("cab_floor", floor * FLOOR_M * MachineRules.STEEL_KG_PER_M3);
        m.put("cab_glass", glassM2() * GLASS_M * GLASS_KG_M3);
        m.put("cab_interior", INTERIOR_KG + INTERIOR_KG_PER_M2 * floor);
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
        if (!(frontRakeDeg >= 0 && frontRakeDeg < 45)) { issues.add(DesignIssue.invalid("frontRakeDeg", "The front leans back 0 to 45 degrees")); }
        if (!issues.isEmpty()) { return issues; }
        String makers = "makers' cabs, mini excavators to large wheel loaders and dozers (CAT, Komatsu, Volvo data sheets)";
        DesignIssue.range(issues, "lengthM", lengthM, 0.9, 2.5, makers);
        DesignIssue.range(issues, "widthM", widthM, 0.8, 2.0, makers);
        DesignIssue.range(issues, "heightM", heightM, 1.3, 2.3, makers);
        DesignIssue.range(issues, "frontRakeDeg", frontRakeDeg, 0, 30, makers);
        return issues;
    }

    @Override public Part compile() {
        List<DesignIssue> issues = check();
        DesignException.throwIfInvalid(issues);
        return new BoxPart(this, sizeM(), DRAG, true, 1, mass());
    }

    @Override public Map<String, Object> parameters() {
        return Fields.object("lengthM", lengthM, "widthM", widthM, "heightM", heightM, "frontRakeDeg", frontRakeDeg,
                "protection", protection.name().toLowerCase(Locale.ROOT), "doorSide", doorSide.name().toLowerCase(Locale.ROOT));
    }

    public static PartDesign read(Fields f) {
        OperatorCabDesign d = standard();
        return new OperatorCabDesign(f.string("name", d.name), f.number("lengthM", d.lengthM), f.number("widthM", d.widthM), f.number("heightM", d.heightM),
                f.number("frontRakeDeg", d.frontRakeDeg), EngineComponent.Rules.choice(f, "protection", Protection.class, d.protection),
                EngineComponent.Rules.choice(f, "doorSide", DoorSide.class, d.doorSide));
    }

    /** The standard cab: a 20 t excavator's (SampleMachinery.excavatorCab). */
    public static OperatorCabDesign standard() { return SampleMachinery.excavatorCab(); }
}
