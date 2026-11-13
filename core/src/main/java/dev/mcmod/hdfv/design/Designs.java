package dev.mcmod.hdfv.design;

import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Json;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.PartTypes;
import dev.mcmod.hdfv.part.BallastDesign;
import dev.mcmod.hdfv.part.BoxDesign;
import dev.mcmod.hdfv.part.ControlSurfaceDesign;
import dev.mcmod.hdfv.part.FloatDesign;
import dev.mcmod.hdfv.part.MotorDesign;
import dev.mcmod.hdfv.part.PlaningHullDesign;
import dev.mcmod.hdfv.part.PropellerDesign;
import dev.mcmod.hdfv.part.SeatDesign;
import dev.mcmod.hdfv.part.air.AirPropellerDesign;
import dev.mcmod.hdfv.part.air.RotorDesign;
import dev.mcmod.hdfv.part.air.SampleAirframes;
import dev.mcmod.hdfv.part.air.WingDesign;
import dev.mcmod.hdfv.part.engine.EngineComponent;
import dev.mcmod.hdfv.part.engine.JetEngineDesign;
import dev.mcmod.hdfv.part.engine.PistonEngineDesign;
import dev.mcmod.hdfv.part.engine.PulsejetDesign;
import dev.mcmod.hdfv.part.engine.RamjetDesign;
import dev.mcmod.hdfv.part.engine.SampleJets;
import dev.mcmod.hdfv.part.engine.ScramjetDesign;
import dev.mcmod.hdfv.part.engine.SolidRocketDesign;
import dev.mcmod.hdfv.part.engine.TurboRamjetDesign;
import dev.mcmod.hdfv.part.engine.SampleEngines;
import dev.mcmod.hdfv.part.engine.ShaftTurbineDesign;
import dev.mcmod.hdfv.part.engine.TurbofanDesign;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * A design as the design screen sees it: pages of parameters (an engine's components, or the design itself), sample
 * designs to start from, the figures worth showing, and the blueprint JSON with its size limit.
 */
public final class Designs {
    private Designs() {}

    /** Longest design JSON a blueprint carries, characters (an engine is about 4000). */
    public static final int MAX_JSON = 32768;
    /** The page of a design that is not made of components. */
    public static final String DESIGN_PAGE = "design";

    /** The groups the design screen sorts kinds of part into (0.6: the user asked for categories). */
    public enum Category { ENGINE, AIR, BODY, WATER, LAND, STORAGE, OTHER, COMPONENT }

    /**
     * A kind of part one can design: its id (for translations: hdfv.kind.<id>), category, and a new design of it to
     * start from (named "new <id>"; the screen gives it a translated name).
     */
    public record Kind(String id, Category category, Supplier<PartDesign> fresh) {}

    /** A design to start from, by id, and the kind it is. */
    public record Sample(String id, String kind, Supplier<PartDesign> design) {}

    private static final dev.mcmod.aerocore.api.vehicle.MassSpec STEEL = dev.mcmod.aerocore.api.vehicle.MassSpec.solid(dev.mcmod.aerocore.api.vehicle.Material.STEEL);

    public static final List<Kind> KINDS = withComponents(List.of(
            new Kind("piston_engine", Category.ENGINE, SampleEngines::inlineFour),
            new Kind("jet_engine", Category.ENGINE, SampleEngines::turbojet),
            new Kind("turbofan", Category.ENGINE, SampleEngines::turbofan),
            new Kind("turboprop", Category.ENGINE, SampleEngines::turboprop),
            new Kind("turboshaft", Category.ENGINE, SampleEngines::turboshaft),
            new Kind("ramjet", Category.ENGINE, SampleJets::bomarcRamjet),
            new Kind("scramjet", Category.ENGINE, SampleJets::x51Scramjet),
            new Kind("pulsejet", Category.ENGINE, SampleJets::argus),
            new Kind("solid_rocket", Category.ENGINE, SampleJets::knsbMotor),
            new Kind("turbo_ramjet", Category.ENGINE, SampleJets::j58),
            new Kind("electric_motor", Category.ENGINE, () -> new MotorDesign("electric motor", 250, 60000, 4000, 0.05, 0.94, 60)),
            new Kind("wing", Category.AIR, SampleAirframes::lightWing),
            new Kind("air_propeller", Category.AIR, SampleAirframes::fixedPropeller),
            new Kind("rotor", Category.AIR, SampleAirframes::mainRotor),
            new Kind("control_surface", Category.AIR, () -> new ControlSurfaceDesign("control surface", 0.3, 0.6, 30, false, 0.12, STEEL)),
            new Kind("body_aircraft", Category.BODY, () -> dev.mcmod.hdfv.part.body.BodyDesign.fresh(dev.mcmod.hdfv.part.body.BodySettingsDesign.Purpose.AIRCRAFT)),
            new Kind("body_boat", Category.BODY, () -> dev.mcmod.hdfv.part.body.BodyDesign.fresh(dev.mcmod.hdfv.part.body.BodySettingsDesign.Purpose.BOAT)),
            new Kind("body_car", Category.BODY, () -> dev.mcmod.hdfv.part.body.BodyDesign.fresh(dev.mcmod.hdfv.part.body.BodySettingsDesign.Purpose.CAR)),
            new Kind("float", Category.WATER, () -> new FloatDesign("float", 0.45, dev.mcmod.aerocore.api.vehicle.MassSpec.shell(dev.mcmod.aerocore.api.vehicle.Material.STEEL, 0.004))),
            new Kind("planing_hull", Category.WATER, () -> new PlaningHullDesign("planing hull", 6, 1.2, 20, 0.75, 48, 6, dev.mcmod.aerocore.api.vehicle.MassSpec.given(880))),
            new Kind("chassis", Category.LAND, () -> dev.mcmod.hdfv.part.chassis.ChassisDesign.fresh(dev.mcmod.hdfv.part.chassis.ChassisSettingsDesign.Kind.LADDER)),
            new Kind("axle", Category.LAND, dev.mcmod.hdfv.part.wheel.SampleWheels::sedanFront),
            new Kind("landing_gear", Category.LAND, dev.mcmod.hdfv.part.wheel.SampleWheels::lightMainGear),
            new Kind("fifth_wheel", Category.LAND, dev.mcmod.hdfv.part.truck.FifthWheelDesign::standard),
            new Kind("landing_legs", Category.LAND, dev.mcmod.hdfv.part.truck.LandingLegsDesign::standard),
            new Kind("manual_gearbox", Category.LAND, dev.mcmod.hdfv.part.drive.SampleGearboxes::fiveSpeedManual),
            new Kind("automatic", Category.LAND, dev.mcmod.hdfv.part.drive.SampleGearboxes::sixSpeedAutomatic),
            new Kind("dual_clutch", Category.LAND, dev.mcmod.hdfv.part.drive.SampleGearboxes::sevenSpeedDualClutch),
            new Kind("cvt", Category.LAND, dev.mcmod.hdfv.part.drive.SampleGearboxes::cvt),
            new Kind("transfer_case", Category.LAND, dev.mcmod.hdfv.part.drive.SampleGearboxes::partTime),
            new Kind("propeller", Category.WATER, () -> new PropellerDesign("screw", 0.35, 4, 0.70, 1.4, 0.18, 0.1,
                    dev.mcmod.aerocore.api.vehicle.MassSpec.solid(dev.mcmod.aerocore.api.vehicle.Material.BRONZE))),
            new Kind("storage_box", Category.STORAGE, dev.mcmod.hdfv.part.StorageBoxDesign::roofBox),   // 0.25
            new Kind("box", Category.OTHER, () -> new BoxDesign("box", new dev.mcmod.aerocore.api.Vec3d(1, 0.5, 1), 1.05, true, 2, dev.mcmod.aerocore.api.vehicle.MassSpec.given(100))),
            new Kind("ballast", Category.OTHER, () -> new BallastDesign("ballast", 100)),
            new Kind("fuel_tank", Category.OTHER, () -> dev.mcmod.hdfv.part.FuelTankDesign.car("gasoline")),
            new Kind("seat", Category.OTHER, () -> new SeatDesign("seat", 10))));

    /** 0.23: a kind for each standard component (its own fixed drawing), after the parts. */
    private static List<Kind> withComponents(List<Kind> parts) {
        List<Kind> out = new java.util.ArrayList<>(parts);
        for (dev.mcmod.hdfv.part.component.Component c : dev.mcmod.hdfv.part.component.Component.values()) {
            out.add(new Kind(componentKind(c), Category.COMPONENT, () -> dev.mcmod.hdfv.part.component.ComponentDesign.of(c)));
        }
        return List.copyOf(out);
    }

    /** The kind id of a standard component. */
    public static String componentKind(dev.mcmod.hdfv.part.component.Component c) { return "component_" + c.id(); }

    public static final List<Sample> SAMPLES = withComponentSamples(List.of(new Sample("inline_four", "piston_engine", SampleEngines::inlineFour),
            new Sample("inline_four_sohc", "piston_engine", SampleEngines::inlineFourSohc), new Sample("v8_ohv", "piston_engine", SampleEngines::vEightOhv),
            new Sample("turbo_diesel", "piston_engine", SampleEngines::turboDiesel), new Sample("generator_diesel", "piston_engine", SampleEngines::generatorDiesel),
            new Sample("turbojet", "jet_engine", SampleEngines::turbojet), new Sample("model_turbojet", "jet_engine", SampleEngines::modelTurbojet),
            new Sample("turbofan", "turbofan", SampleEngines::turbofan), new Sample("business_turbofan", "turbofan", SampleEngines::businessTurbofan),
            new Sample("turboprop", "turboprop", SampleEngines::turboprop), new Sample("turboshaft", "turboshaft", SampleEngines::turboshaft),
            new Sample("j79", "jet_engine", SampleJets::j79), new Sample("bomarc_ramjet", "ramjet", SampleJets::bomarcRamjet),
            new Sample("x51_scramjet", "scramjet", SampleJets::x51Scramjet), new Sample("x43_scramjet", "scramjet", SampleJets::hydrogenScramjet),
            new Sample("argus_pulsejet", "pulsejet", SampleJets::argus), new Sample("lockwood_pulsejet", "pulsejet", SampleJets::lockwood),
            new Sample("knsb_motor", "solid_rocket", SampleJets::knsbMotor), new Sample("apcp_booster", "solid_rocket", SampleJets::apcpBooster),
            new Sample("j58", "turbo_ramjet", SampleJets::j58),
            new Sample("light_wing", "wing", SampleAirframes::lightWing), new Sample("seaplane_wing", "wing", SampleAirframes::seaplaneWing),
            new Sample("tailplane", "wing", SampleAirframes::tailplane), new Sample("fin", "wing", SampleAirframes::fin),
            new Sample("turboprop_propeller", "air_propeller", SampleAirframes::turbopropPropeller), new Sample("fixed_propeller", "air_propeller", SampleAirframes::fixedPropeller),
            new Sample("main_rotor", "rotor", SampleAirframes::mainRotor), new Sample("tail_rotor", "rotor", SampleAirframes::tailRotor),
            new Sample("seaplane_fuselage", "body_aircraft", dev.mcmod.hdfv.part.body.SampleBodies::seaplaneFuselage),
            new Sample("helicopter_fuselage", "body_aircraft", dev.mcmod.hdfv.part.body.SampleBodies::helicopterFuselage),
            new Sample("flying_boat_hull", "body_aircraft", dev.mcmod.hdfv.part.body.SampleBodies::flyingBoat),
            new Sample("seaplane_float", "body_boat", dev.mcmod.hdfv.part.body.SampleBodies::seaplaneFloat),
            new Sample("runabout_hull", "body_boat", dev.mcmod.hdfv.part.body.SampleBodies::runabout),
            new Sample("sedan_body", "body_car", dev.mcmod.hdfv.part.body.SampleBodies::sedan),
            new Sample("hatchback_body", "body_car", dev.mcmod.hdfv.part.body.SampleBodies::hatchback),
            new Sample("cab_over_body", "body_car", dev.mcmod.hdfv.part.body.SampleBodies::cabOver),
            new Sample("low_loader_deck", "body_car", dev.mcmod.hdfv.part.body.SampleBodies::lowLoaderDeck),
            new Sample("pickup_ladder_frame", "chassis", dev.mcmod.hdfv.part.chassis.SampleChassis::ladderPickup),
            new Sample("truck_ladder_frame", "chassis", dev.mcmod.hdfv.part.chassis.SampleChassis::ladderTruck),
            new Sample("tractor_6x4_frame", "chassis", dev.mcmod.hdfv.part.chassis.SampleChassis::tractor6x4),
            new Sample("low_loader_3_frame", "chassis", dev.mcmod.hdfv.part.chassis.SampleChassis::lowLoader3),
            new Sample("fifth_wheel_2in", "fifth_wheel", dev.mcmod.hdfv.part.truck.FifthWheelDesign::standard),
            new Sample("landing_legs_24t", "landing_legs", dev.mcmod.hdfv.part.truck.LandingLegsDesign::standard),
            new Sample("sports_backbone", "chassis", dev.mcmod.hdfv.part.chassis.SampleChassis::backbone),
            new Sample("racer_tube_frame", "chassis", dev.mcmod.hdfv.part.chassis.SampleChassis::tubeFrame),
            new Sample("sedan_front_axle", "axle", dev.mcmod.hdfv.part.wheel.SampleWheels::sedanFront),
            new Sample("sedan_rear_axle", "axle", dev.mcmod.hdfv.part.wheel.SampleWheels::sedanRear),
            new Sample("offroad_front_axle", "axle", dev.mcmod.hdfv.part.wheel.SampleWheels::offRoadFront),
            new Sample("offroad_rear_axle", "axle", dev.mcmod.hdfv.part.wheel.SampleWheels::offRoadRear),
            new Sample("light_nose_gear", "landing_gear", dev.mcmod.hdfv.part.wheel.SampleWheels::lightNoseGear),
            new Sample("light_main_gear", "landing_gear", dev.mcmod.hdfv.part.wheel.SampleWheels::lightMainGear),
            new Sample("five_speed_manual", "manual_gearbox", dev.mcmod.hdfv.part.drive.SampleGearboxes::fiveSpeedManual),
            new Sample("six_speed_manual", "manual_gearbox", dev.mcmod.hdfv.part.drive.SampleGearboxes::sixSpeedManual),
            new Sample("six_speed_automatic", "automatic", dev.mcmod.hdfv.part.drive.SampleGearboxes::sixSpeedAutomatic),
            new Sample("seven_speed_dual_clutch", "dual_clutch", dev.mcmod.hdfv.part.drive.SampleGearboxes::sevenSpeedDualClutch),
            new Sample("belt_cvt", "cvt", dev.mcmod.hdfv.part.drive.SampleGearboxes::cvt),
            new Sample("part_time_case", "transfer_case", dev.mcmod.hdfv.part.drive.SampleGearboxes::partTime),
            new Sample("full_time_case", "transfer_case", dev.mcmod.hdfv.part.drive.SampleGearboxes::fullTimeTorsen),
            new Sample("on_demand_coupling", "transfer_case", dev.mcmod.hdfv.part.drive.SampleGearboxes::onDemand),
            new Sample("hydrogen_cylinder", "fuel_tank", dev.mcmod.hdfv.part.FuelTankDesign::hydrogenCylinder),
            new Sample("liquid_hydrogen_tank", "fuel_tank", dev.mcmod.hdfv.part.FuelTankDesign::liquidHydrogenTank),
            // 0.25: storage boxes
            new Sample("roof_box", "storage_box", dev.mcmod.hdfv.part.StorageBoxDesign::roofBox),
            new Sample("bed_box", "storage_box", dev.mcmod.hdfv.part.StorageBoxDesign::bedBox),
            new Sample("container_20ft", "storage_box", dev.mcmod.hdfv.part.StorageBoxDesign::container20)));

    /** 0.23: each standard component's drawing is a sample too (a blueprint in the creative tab), after the parts'. */
    private static List<Sample> withComponentSamples(List<Sample> parts) {
        List<Sample> out = new java.util.ArrayList<>(parts);
        for (dev.mcmod.hdfv.part.component.Component c : dev.mcmod.hdfv.part.component.Component.values()) {
            out.add(new Sample(c.id(), componentKind(c), () -> dev.mcmod.hdfv.part.component.ComponentDesign.of(c)));
        }
        return List.copyOf(out);
    }

    /** The kinds in a category, in order. */
    public static List<Kind> kinds(Category category) { return KINDS.stream().filter(k -> k.category() == category).toList(); }

    public static Kind kind(String id) {
        for (Kind k : KINDS) { if (k.id().equals(id)) { return k; } }
        throw new IllegalArgumentException("No kind " + id);
    }

    /** The samples of a kind (maybe none). */
    public static List<Sample> samples(String kind) { return SAMPLES.stream().filter(s -> s.kind().equals(kind)).toList(); }

    /** The kind a design is (by its type, a body by its purpose); the first kind when nothing matches. */
    public static Kind kindOf(PartDesign d) {
        String type = d.type();
        if (d instanceof dev.mcmod.hdfv.part.body.BodyDesign b) {
            return kind(switch (b.settings().purpose()) { case AIRCRAFT -> "body_aircraft"; case BOAT -> "body_boat"; case CAR -> "body_car"; });
        }
        if (d instanceof dev.mcmod.hdfv.part.component.ComponentDesign c) { return kind(componentKind(c.kind())); }
        for (Kind k : KINDS) {
            if (k.id().startsWith("body_")) { continue; }
            if (k.fresh().get().type().equals(type)) { return k; }
        }
        return KINDS.get(0);
    }

    /** A new design of a kind with a name. */
    public static PartDesign fresh(String kind, String name) { return named(kind(kind).fresh().get(), name); }

    public static PartDesign sample(String id) {
        for (Sample s : SAMPLES) { if (s.id().equals(id)) { return s.design().get(); } }
        throw new IllegalArgumentException("No sample " + id);
    }

    // ---------------------------------------------------------------- pages

    /** The components of an engine by key, or null for a design not made of components. */
    public static Map<String, EngineComponent> components(PartDesign d) {
        if (d instanceof PistonEngineDesign p) { return p.components(); }
        if (d instanceof JetEngineDesign j) { return j.components(); }
        if (d instanceof TurbofanDesign t) { return t.components(); }
        if (d instanceof ShaftTurbineDesign t) { return t.components(); }
        if (d instanceof RamjetDesign r) { return r.components(); }
        if (d instanceof ScramjetDesign s) { return s.components(); }
        if (d instanceof PulsejetDesign p) { return p.components(); }
        if (d instanceof SolidRocketDesign s) { return s.components(); }
        if (d instanceof TurboRamjetDesign t) { return t.components(); }
        if (d instanceof WingDesign w) { return w.components(); }
        if (d instanceof AirPropellerDesign p) { return p.components(); }
        if (d instanceof RotorDesign r) { return r.components(); }
        if (d instanceof dev.mcmod.hdfv.part.body.BodyDesign b) { return b.components(); }
        if (d instanceof dev.mcmod.hdfv.part.wheel.AxleDesign a) { return a.components(); }
        if (d instanceof dev.mcmod.hdfv.part.chassis.ChassisDesign c) { return c.components(); }
        if (d instanceof dev.mcmod.hdfv.part.wheel.LandingGearDesign l) { return l.components(); }
        if (d instanceof dev.mcmod.hdfv.part.drive.GearboxDesign g) { return g.components(); }
        return null;
    }

    /** The pages of a design: its components' keys, or the design itself. */
    public static List<String> pages(PartDesign d) {
        Map<String, EngineComponent> c = components(d);
        return c == null ? List.of(DESIGN_PAGE) : List.copyOf(c.keySet());
    }

    /** The record a page edits. */
    /** 0.15: what a blank field stands for, shown in it greyed (an axle's hardpoint: the simple geometry's point); null for none. */
    public static String hint(PartDesign d, String page, String parameter) {
        if (d instanceof dev.mcmod.hdfv.part.wheel.AxleDesign a && page.equals("hardpoints")) { return a.hardpointHint(parameter); }
        return null;
    }

    /** A page's editable parameters: its record's, less those the design hides there (0.15: RecordEditor.ShownOnPage). */
    public static List<RecordEditor.Parameter> parameters(PartDesign d, String page) {
        List<RecordEditor.Parameter> out = RecordEditor.parameters(page(d, page));
        if (d instanceof RecordEditor.ShownOnPage s) { out = out.stream().filter(p -> s.shows(page, p.name())).toList(); }
        return out;
    }

    public static Record page(PartDesign d, String page) {
        Map<String, EngineComponent> c = components(d);
        if (c == null) {
            if (page.equals(DESIGN_PAGE) && d instanceof Record r) { return r; }
        } else if (c.get(page) instanceof Record r) { return r; }
        throw new IllegalArgumentException("No page " + page);
    }

    /** The design with a page replaced (an engine with one component replaced). */
    public static PartDesign withPage(PartDesign d, String page, Record value) {
        if (components(d) == null) {
            if (!page.equals(DESIGN_PAGE)) { throw new IllegalArgumentException("No page " + page); }
            return (PartDesign) value;
        }
        if (d instanceof ListedDesign l) { return l.withPage(page, value); }
        if (!(d instanceof Record r)) { throw new IllegalArgumentException("Not a record: " + d.type()); }
        return (PartDesign) RecordEditor.with(r, page, value);
    }

    /** The design with one parameter of a page set from an editor's text. */
    public static PartDesign set(PartDesign d, String page, String parameter, String text) {
        return withPage(d, page, RecordEditor.set(page(d, page), parameter, text));
    }

    /** The design renamed. */
    public static PartDesign named(PartDesign d, String name) {
        if (!(d instanceof Record r)) { throw new IllegalArgumentException("Not a record: " + d.type()); }
        return (PartDesign) RecordEditor.with(r, "name", name);
    }

    /**
     * Where an issue belongs: the page and parameter it names ("block.massKg" is the block's massGivenKg), or the page
     * alone for a derived value of it, or null page for the design as a whole ("compressionRatio", "cycle").
     */
    public record Place(String page, String parameter) {}

    public static Place place(PartDesign d, DesignIssue issue) {
        String path = issue.parameter();
        List<String> pages = pages(d);
        String page = null, rest = path;
        if (pages.size() == 1 && pages.get(0).equals(DESIGN_PAGE)) { page = DESIGN_PAGE; }
        else {
            int dot = path.indexOf('.');
            String head = dot < 0 ? path : path.substring(0, dot);
            if (pages.contains(head)) { page = head; rest = dot < 0 ? "" : path.substring(dot + 1); }
        }
        if (page == null) { return new Place(null, path); }
        if (rest.equals("massKg")) { rest = "massGivenKg"; }
        int dot = rest.indexOf('.');
        if (dot >= 0) { rest = rest.substring(0, dot); }   // "material.density" is the material's
        for (RecordEditor.Parameter p : parameters(d, page)) { if (p.name().equals(rest)) { return new Place(page, rest); } }
        return new Place(page, null);
    }

    // ---------------------------------------------------------------- JSON

    public static String write(PartDesign d) { return Json.write(d.toJson()); }

    /** A design from blueprint JSON; IllegalArgumentException if it is too long or not a design of a known type. */
    public static PartDesign read(String json) {
        if (json.length() > MAX_JSON) { throw new IllegalArgumentException("Design JSON longer than " + MAX_JSON + " characters"); }
        return PartTypes.read(Json.parse(json), "design");
    }

    // ---------------------------------------------------------------- figures

    /** A derived figure of a design: its key (for a translation), value and unit. */
    public record Figure(String key, double value, String unit) {}

    /** What a maker wants to see at once: size, mass and, for a design that sizes, its design-point performance. */
    public static List<Figure> figures(PartDesign d) {
        List<Figure> out = new ArrayList<>();
        boolean invalid = d.check().stream().anyMatch(i -> i.kind() == DesignIssue.Kind.INVALID);
        try {
            if (d instanceof PistonEngineDesign e) {
                out.add(new Figure("displacement", e.displacement() * 1000, "L"));
                out.add(new Figure("compressionRatio", e.compressionRatio(), ""));
                out.add(new Figure("mass", e.totalMassKg(), "kg"));
                out.add(new Figure("pistonSpeedAtRevLimit", 2 * e.stroke() * e.ignition().revLimitRpm() / 60, "m/s"));
                out.add(new Figure("crankInertia", e.crankInertia(), "kg·m²"));
            } else if (d instanceof JetEngineDesign e) {
                out.add(new Figure("length", e.length(), "m"));
                out.add(new Figure("diameter", e.diameter(), "m"));
                if (!invalid) {
                    var p = e.sizing().point();
                    double mass = e.totalMassKg();
                    out.add(new Figure("mass", mass, "kg"));
                    out.add(p.thrustN() >= 1000 ? new Figure("designThrust", p.thrustN() / 1000, "kN") : new Figure("designThrust", p.thrustN(), "N"));
                    out.add(new Figure("tsfc", p.tsfc() * 1e6, "g/(kN·s)"));
                    out.add(new Figure("thrustToWeight", p.thrustN() / (mass * 9.80665), ""));
                    out.add(new Figure("exhaustTemperature", p.tt5(), "K"));
                    if (e.afterburner().fitted()) {
                        var wet = dev.mcmod.aerocore.api.engine.Reheat.turbojet(e.sizing(), p, dev.mcmod.aerocore.api.engine.Turbojet.Flight.sea(0),
                                e.afterburner().reheat(), e.afterburner().maxTemperatureK());
                        out.add(new Figure("wetThrust", wet.thrustN() / 1000, "kN"));
                        out.add(new Figure("wetTsfc", wet.fuelKgPerS() / wet.thrustN() * 1e6, "g/(kN·s)"));
                    }
                }
            } else if (d instanceof RamjetDesign e) {
                out.add(new Figure("length", e.length(), "m"));
                out.add(new Figure("diameter", e.diameter(), "m"));
                out.add(new Figure("mass", e.totalMassKg(), "kg"));
                if (!invalid) {
                    var p = e.model().point();
                    out.add(new Figure("designPointThrust", p.thrustN() / 1000, "kN"));
                    out.add(new Figure("specificImpulse", p.specificImpulse(), "s"));
                    out.add(new Figure("totalAirflow", p.airflowKgPerS(), "kg/s"));
                    out.add(new Figure("thrustToWeight", p.thrustN() / (e.totalMassKg() * 9.80665), ""));
                }
            } else if (d instanceof ScramjetDesign e) {
                out.add(new Figure("length", e.length(), "m"));
                out.add(new Figure("engineWidth", e.width(), "m"));
                out.add(new Figure("mass", e.totalMassKg(), "kg"));
                out.add(new Figure("ramTemperature", e.hottestRamK(), "K"));
                if (!invalid) { out.add(new Figure("startMach", e.selfStartMach(), "")); }
            } else if (d instanceof PulsejetDesign e) {
                out.add(new Figure("length", e.length(), "m"));
                out.add(new Figure("diameter", e.diameter(), "m"));
                out.add(new Figure("mass", e.totalMassKg(), "kg"));
                if (!invalid && !e.valve().valveless()) { out.add(new Figure("reedLife", e.valve().reedLifeCycles() / 1e6, "")); }
            } else if (d instanceof SolidRocketDesign e) {
                out.add(new Figure("length", e.length(), "m"));
                out.add(new Figure("diameter", e.diameter(), "m"));
                out.add(new Figure("dryMass", e.dryMassKg(), "kg"));
                out.add(new Figure("propellantMass", e.grain().propellantKg(), "kg"));
                if (!invalid) {
                    var p = e.performance();
                    out.add(new Figure("burnTime", p.burnTimeS(), "s"));
                    out.add(new Figure("totalImpulse", p.totalImpulse(101325) / 1000, "kN·s"));
                    out.add(new Figure("specificImpulse", p.specificImpulse(101325), "s"));
                    out.add(new Figure("chamberPressure", p.maxPressurePa() / 1e6, "MPa"));
                    out.add(new Figure("burstPressure", e.casing().burstPa(e.grain().outerDiameterM()) / 1e6, "MPa"));
                }
            } else if (d instanceof TurboRamjetDesign e) {
                out.add(new Figure("length", e.length(), "m"));
                out.add(new Figure("diameter", e.diameter(), "m"));
                out.add(new Figure("mass", e.totalMassKg(), "kg"));
                if (!invalid) {
                    var s = e.model().sizing();
                    var p = s.core().point();
                    var wet = dev.mcmod.aerocore.api.engine.TurboRamjet.at(s, dev.mcmod.aerocore.api.engine.Turbojet.Flight.sea(0), e.control().turbineInletK(),
                            e.afterburner().maxTemperatureK());
                    out.add(new Figure("designThrust", p.thrustN() / 1000, "kN"));
                    out.add(new Figure("wetThrust", wet.thrustN() / 1000, "kN"));
                    out.add(new Figure("thrustToWeight", wet.thrustN() / (e.totalMassKg() * 9.80665), ""));
                }
            } else if (d instanceof TurbofanDesign e) {
                out.add(new Figure("length", e.length(), "m"));
                out.add(new Figure("diameter", e.diameter(), "m"));
                if (!invalid) {
                    var p = e.sizing().point();
                    double mass = e.totalMassKg();
                    out.add(new Figure("mass", mass, "kg"));
                    out.add(p.thrustN() >= 1000 ? new Figure("designThrust", p.thrustN() / 1000, "kN") : new Figure("designThrust", p.thrustN(), "N"));
                    out.add(new Figure("tsfc", p.tsfc() * 1e6, "g/(kN·s)"));
                    out.add(new Figure("thrustToWeight", p.thrustN() / (mass * 9.80665), ""));
                    out.add(new Figure("overallPressureRatio", p.overallPressureRatio(), ""));
                    out.add(new Figure("totalAirflow", p.airflowKgPerS(), "kg/s"));
                    out.add(new Figure("exhaustTemperature", p.tt5(), "K"));
                }
            } else if (d instanceof ShaftTurbineDesign e) {
                out.add(new Figure("length", e.length(), "m"));
                out.add(new Figure("diameter", e.diameter(), "m"));
                if (!invalid) {
                    var p = e.sizing().point();
                    double mass = e.totalMassKg(), power = p.shaftPowerW() * e.gearbox().efficiency();
                    out.add(new Figure("mass", mass, "kg"));
                    out.add(new Figure("shaftPower", power / 1000, "kW"));
                    out.add(new Figure("psfc", p.fuelKgPerS() / power * 3.6e9, "g/kWh"));
                    out.add(new Figure("powerToWeight", power / 1000 / mass, "kW/kg"));
                    out.add(new Figure("outputRpm", e.outputRpm(), "rpm"));
                    out.add(new Figure("outputTorque", e.designOutputTorque(p), "N·m"));
                    out.add(new Figure("residualThrust", p.thrustN(), "N"));
                    out.add(new Figure("interTurbineTemperature", p.tt45(), "K"));
                }
            } else if (d instanceof WingDesign w) {
                var p = w.planform().planform();
                out.add(new Figure("span", p.spanM(), "m"));
                out.add(new Figure("wingArea", p.areaM2(), "m²"));
                out.add(new Figure("aspectRatio", p.aspectRatio(), ""));
                out.add(new Figure("meanChord", p.meanChordM(), "m"));
                if (!invalid) {
                    var s = w.structureResult();
                    out.add(new Figure("mass", w.totalMassKg(), "kg"));
                    out.add(new Figure("massPerArea", w.totalMassKg() / p.areaM2(), "kg/m²"));
                    out.add(new Figure("designLift", w.structure().designLiftN() / 1000, "kN"));
                    out.add(new Figure("capStress", s.capStressPa() / 1e6, "MPa"));
                    out.add(new Figure("tipDeflection", s.tipDeflectionM(), "m"));
                }
            } else if (d instanceof AirPropellerDesign a) {
                out.add(new Figure("diameter", a.blades().diameterM(), "m"));
                if (!invalid) {
                    var g = a.geometry();
                    out.add(new Figure("mass", a.totalMassKg(), "kg"));
                    out.add(new Figure("tipMach", a.tipMach(), ""));
                    out.add(new Figure("solidity", g.solidity(), ""));
                    out.add(new Figure("activityFactor", g.activityFactor(), ""));
                }
            } else if (d instanceof RotorDesign r) {
                out.add(new Figure("diameter", 2 * r.blades().radiusM(), "m"));
                out.add(new Figure("diskArea", r.diskAreaM2(), "m²"));
                if (!invalid) {
                    var rotor = new dev.mcmod.aerocore.api.aero.HelicopterRotor(r.geometry());
                    out.add(new Figure("mass", r.totalMassKg(), "kg"));
                    out.add(new Figure("tipMach", r.tipMach(), ""));
                    out.add(new Figure("solidity", r.geometry().solidity(), ""));
                    out.add(new Figure("lockNumber", rotor.lockNumber(1.225), ""));
                }
            } else if (d instanceof dev.mcmod.hdfv.part.chassis.ChassisDesign c) {
                // 0.15: the frame's mass, wheelbase, twist and how near its members come to yield in the standard cases
                out.add(new Figure("length", c.frame().lengthM(), "m"));
                var axles = c.axleStations();
                if (axles.size() >= 2) { out.add(new Figure("wheelbase", c.stations().get(axles.get(0)).xM() - c.stations().get(axles.get(axles.size() - 1)).xM(), "m")); }
                if (!invalid) {
                    var a = c.analysis();
                    out.add(new Figure("mass", a.massKg(), "kg"));
                    if (Double.isFinite(a.endTwistNmPerDeg())) { out.add(new Figure("twistStiffness", a.endTwistNmPerDeg() / 1000, "kN·m/°")); }
                    if (Double.isFinite(a.usage())) { out.add(new Figure("frameUsage", 100 * a.usage(), "%")); }
                }
            } else if (d instanceof dev.mcmod.hdfv.part.body.BodyDesign b) {
                var shape = b.shape();
                var p = shape.particulars();
                out.add(new Figure("length", shape.lengthM(), "m"));
                out.add(new Figure("bodyWidth", p.maxWidthM(), "m"));
                out.add(new Figure("bodyHeight", p.maxHeightM(), "m"));
                out.add(new Figure("volume", p.volumeM3(), "m³"));
                if (!invalid) {
                    var air = dev.mcmod.aerocore.api.aero.Wing.Air.SEA_LEVEL;
                    double cda = new dev.mcmod.aerocore.api.body.BodyAerodynamics(shape, b.extraDragAreaM2(shape)).zeroLiftDragAreaM2(30, air);
                    var r = b.structuralCheck();
                    out.add(new Figure("structureMass", b.structureKg(), "kg"));
                    out.add(new Figure("loadedMass", b.mass().massKg(), "kg"));
                    out.add(new Figure("seats", b.seats(shape).size(), ""));
                    out.add(new Figure("dragArea", cda, "m²"));
                    out.add(new Figure("bodyDragCoefficient", cda / p.frontalAreaM2(), ""));
                    out.add(new Figure("bendingStress", r.maxBendingPa() / 1e6, "MPa"));
                    if (r.maxHoopPa() > 0) { out.add(new Figure("hoopStress", r.maxHoopPa() / 1e6, "MPa")); }
                    double bottom = b.bottomPressurePa(shape);
                    if (bottom > 0) { out.add(new Figure("bottomPressure", bottom / 1000, "kPa")); }
                    out.add(new Figure("buoyancy", shape.waterHull().totalVolume() * 1000, "kg"));
                }
                // 0.25: its holds: their volume, their slots, the design payload (every hold full at the cargo density)
                if (b.holdM3() > 0) {
                    int slots = 0;
                    for (var h : b.holds(shape)) { slots += dev.mcmod.hdfv.storage.CargoRules.slots(b, h); }
                    out.add(new Figure("holdVolume", b.holdM3(), "m³"));
                    out.add(new Figure("holdSlots", slots, ""));
                    out.add(new Figure("designPayload", b.payloadKg(), "kg"));
                }
            } else if (d instanceof dev.mcmod.hdfv.part.wheel.AxleDesign a) {
                var t = a.tyre().tyre();
                out.add(new Figure("tyreDiameter", 2 * t.radiusM(), "m"));
                out.add(new Figure("track", a.layout().trackM(), "m"));
                out.add(new Figure("mass", a.totalMassKg(), "kg"));
                if (!invalid) {
                    var g = a.builtSuspension(t).geometry();
                    out.add(new Figure("rideFrequency", a.rideFrequencyHz(t), "Hz"));
                    out.add(new Figure("dampingRatio", a.dampingRatio(), ""));
                    out.add(new Figure("rollStiffness", a.rollStiffnessNmPerDeg(t), "N·m/°"));
                    out.add(new Figure("rollCentre", g.rollCentreHeightM(), "m"));
                    out.add(new Figure("tyreLoadShare", a.cornerLoadN() / t.maxLoadN(), ""));
                    if (a.brake().kind() != dev.mcmod.hdfv.part.wheel.BrakeDesign.Kind.NONE) {
                        out.add(new Figure("brakeTorque", a.brake().maxTorqueNm(), "N·m"));
                        out.add(new Figure("lockRatio", a.lockRatio(t), ""));
                    }
                }
            } else if (d instanceof dev.mcmod.hdfv.part.wheel.LandingGearDesign l) {
                var t = l.tyre().tyre();
                out.add(new Figure("tyreDiameter", 2 * t.radiusM(), "m"));
                out.add(new Figure("mass", l.totalMassKg(), "kg"));
                if (!invalid) {
                    out.add(new Figure("staticStroke", l.strut().staticCompressionM() / l.strut().stroke(), ""));
                    out.add(new Figure("tyreLoadShare", l.strut().designLoadKg() * 9.80665 / t.maxLoadN(), ""));
                    if (l.brake().kind() != dev.mcmod.hdfv.part.wheel.BrakeDesign.Kind.NONE) { out.add(new Figure("brakeTorque", l.brake().maxTorqueNm(), "N·m")); }
                }
            } else if (d instanceof dev.mcmod.hdfv.part.drive.GearboxDesign g) {
                out.add(new Figure("mass", g.totalMassKg(), "kg"));
                if (!invalid) {
                    double[] r = g.ratios();
                    out.add(new Figure("forwardGears", g.forwardGears(), ""));
                    out.add(new Figure("firstRatio", r[0], ""));
                    out.add(new Figure("topRatio", r[r.length - 1], ""));
                    out.add(new Figure("ratioSpread", r[0] / r[r.length - 1], ""));
                    out.add(new Figure("reverseRatio", g.reverseRatio(), ""));
                    out.add(new Figure("carryingTorque", g.carryingNm(), "N·m"));
                    if (g.kind() == dev.mcmod.hdfv.part.drive.Gearboxes.Kind.MANUAL || g.kind() == dev.mcmod.hdfv.part.drive.Gearboxes.Kind.DUAL_CLUTCH) {
                        out.add(new Figure("synchroTime", g.synchroTimeS(), "s"));
                    }
                }
            } else if (d instanceof dev.mcmod.hdfv.part.FuelTankDesign t) {
                double litres = t.capacityM3() * 1000, full = t.capacityM3() * t.fuelDensity();
                out.add(new Figure("capacity", litres, "L"));
                out.add(new Figure("mass", t.massKg(), "kg"));
                out.add(new Figure("fuelMass", full, "kg"));
                if (t.boilOffKgPerS() > 0 && full > 0) { out.add(new Figure("boilOff", t.boilOffKgPerS() * 86400 / full * 100, "%/d")); }
                if (t.kind() == dev.mcmod.hdfv.part.FuelTankDesign.Kind.HIGH_PRESSURE) { out.add(new Figure("burstPressure", t.safetyFactor() * t.pressurePa() / 1e6, "MPa")); }
            } else if (d instanceof dev.mcmod.hdfv.part.StorageBoxDesign s) {
                // 0.25: what it holds (litres and the slots of its hold), its tare, what it is rated to carry, its drag area
                out.add(new Figure("capacity", s.capacityM3() * 1000, "L"));
                out.add(new Figure("holdSlots", dev.mcmod.hdfv.storage.CargoRules.slots(s.capacityM3(), dev.mcmod.hdfv.storage.CargoRules.maxSlots(s)), ""));
                out.add(new Figure("mass", s.massKg(), "kg"));
                out.add(new Figure("ratedLoad", s.ratedLoadKg(), "kg"));
                out.add(new Figure("dragArea", s.frontalAreaM2() * s.dragCoefficient(), "m²"));
            } else if (d instanceof dev.mcmod.hdfv.part.drive.TransferCaseDesign t) {
                out.add(new Figure("mass", t.totalMassKg(), "kg"));
                if (t.lowRatio() > 1) { out.add(new Figure("lowRange", t.lowRatio(), "")); }
            }
        } catch (RuntimeException ignored) {
            // a design too broken to derive figures from; its issues say why
        }
        return out;
    }
}
