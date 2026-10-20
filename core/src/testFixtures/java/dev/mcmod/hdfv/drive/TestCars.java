package dev.mcmod.hdfv.drive;

import dev.mcmod.aerocore.api.vehicle.Mount;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.VehicleBlueprint;
import dev.mcmod.hdfv.assembly.AutoWiring;
import dev.mcmod.hdfv.assembly.Placing;
import dev.mcmod.hdfv.assembly.SampleLayouts;
import dev.mcmod.hdfv.design.RecordEditor;
import dev.mcmod.hdfv.part.body.BodyDesign;
import dev.mcmod.hdfv.part.body.BodyModuleDesign;
import dev.mcmod.hdfv.part.body.SampleBodies;
import dev.mcmod.hdfv.part.chassis.ChassisDesign;
import dev.mcmod.hdfv.part.chassis.ChassisSettingsDesign;
import dev.mcmod.hdfv.part.chassis.ChassisStationDesign;
import dev.mcmod.hdfv.part.drive.SampleGearboxes;
import dev.mcmod.hdfv.part.engine.EngineMaterial;
import dev.mcmod.hdfv.part.wheel.AxleDesign;
import dev.mcmod.hdfv.part.wheel.SampleWheels;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Cars for testing the driving keys and dashboard (0.14): the sample sedan's layout with other parts, wired as an
 * assembly room does. Not samples (the user 2026-10-17: no new sample cars): only tests and the development command
 * /hdfv dev drive spawn use them; they are in no list a player sees.
 */
public final class TestCars {
    private TestCars() {}

    /** The sedan with a five-speed manual (and its automatic clutch). */
    public static VehicleBlueprint manualSedan() { return wired("manual sedan", Map.of("gearbox", SampleGearboxes.fiveSpeedManual()), null); }

    /** The sedan with a five-speed manual, off-road axles with lockers front and rear and a part-time case with a low range. */
    public static VehicleBlueprint fourByFour() {
        AxleDesign front = SampleWheels.offRoadFront(), rear = SampleWheels.offRoadRear();
        AxleDesign lockedFront = new AxleDesign(front.name(), front.layout(), front.tyre(), front.wheel(), front.brake(), front.suspension(), rear.differential());
        return wired("4x4", Map.of("gearbox", SampleGearboxes.fiveSpeedManual(), "front_axle", lockedFront, "rear_axle", rear), SampleGearboxes.partTime());
    }

    /**
     * 0.14: the sedan's layout (not wired) with the generator diesel in its bay, where the manual sedan's engine is: it
     * stands out of the hood, and an assembly room refuses it (/hdfv dev layout ... big_engine, for screenshots).
     */
    public static VehicleBlueprint tooBigEngine() {
        List<VehicleBlueprint.Entry> parts = new ArrayList<>();
        for (VehicleBlueprint.Entry e : SampleLayouts.sedan().parts()) {
            parts.add(e.id().equals("engine") ? new VehicleBlueprint.Entry("engine", dev.mcmod.hdfv.part.engine.SampleEngines.generatorDiesel(), e.mount()) : e);
        }
        return new VehicleBlueprint("big engine", parts, List.of());
    }

    /**
     * 0.15: a six-wheeler on a frame: the sedan's shell made to sit on one with its trunk stretched to 2.0 m and an arch
     * near its front, on a welded ladder frame with three axle stations under its arches; off-road axles, the front one
     * steered (to the tandem's middle), the inline four and six-speed automatic on the frame's engine bay, a part-time
     * case driving the front axle on its front output and the tandem on its rear. Laid out and wired as an assembly room
     * does (Placing.add, AutoWiring).
     */
    public static VehicleBlueprint sixBySix() { return sixBySix(Frame.STANDARD); }

    /**
     * The 6x6's frames: the standard one (welded 140 x 70 x 4 mm box rails); a soft one (riveted 200 x 75 x 6 mm
     * channel: open, so it twists easily; designed for 2800 kg); a stiff one (welded 200 x 100 x 6 mm tube with an
     * X-member in each bay).
     */
    public enum Frame {
        STANDARD(ChassisSettingsDesign.RailSection.BOX, 0.14, 0.07, 0.004, ChassisSettingsDesign.Joint.WELDED, ChassisStationDesign.Brace.NONE, 3500),
        SOFT(ChassisSettingsDesign.RailSection.CHANNEL, 0.2, 0.075, 0.006, ChassisSettingsDesign.Joint.RIVETED, ChassisStationDesign.Brace.NONE, 2800),
        STIFF(ChassisSettingsDesign.RailSection.RECT_TUBE, 0.2, 0.1, 0.006, ChassisSettingsDesign.Joint.WELDED, ChassisStationDesign.Brace.X, 3500);

        final ChassisSettingsDesign.RailSection rails;
        final double heightM, widthM, wallM;
        final ChassisSettingsDesign.Joint joint;
        final ChassisStationDesign.Brace brace;
        final double grossKg;

        Frame(ChassisSettingsDesign.RailSection rails, double heightM, double widthM, double wallM, ChassisSettingsDesign.Joint joint, ChassisStationDesign.Brace brace,
              double grossKg) {
            this.rails = rails; this.heightM = heightM; this.widthM = widthM; this.wallM = wallM; this.joint = joint; this.brace = brace; this.grossKg = grossKg;
        }
    }

    /** The 6x6 on one of its frames. */
    public static VehicleBlueprint sixBySix(Frame kind) { return sixBySix(kind, AXLE_HEIGHT_M); }

    /** 0.15: the 6x6 with its axles hung 0.4 m higher on its frame: its rails sit in the ground (an assembly room's touches_ground). */
    public static VehicleBlueprint sunkSixBySix() { return sixBySix(Frame.STANDARD, AXLE_HEIGHT_M + 0.4); }

    /** 0.15: the 6x6's shell, made to sit on a frame, alone (an assembly room's body_needs_frame). */
    public static VehicleBlueprint shellAlone() {
        VehicleBlueprint.Entry body = null;
        for (VehicleBlueprint.Entry e : sixBySix().parts()) { if (e.design() instanceof BodyDesign) { body = e; } }
        return new VehicleBlueprint("6x6 shell", List.of(new VehicleBlueprint.Entry("body", body.design(), Mount.ORIGIN)), List.of());
    }

    private static VehicleBlueprint sixBySix(Frame kind, double axleHeightM) {
        BodyDesign sedan = SampleBodies.sedan();
        List<BodyModuleDesign> modules = new ArrayList<>(sedan.modules());
        BodyModuleDesign trunk = modules.getLast();
        trunk = RecordEditor.with(RecordEditor.with(RecordEditor.with(trunk, "lengthM", 2.0), "axle", true), "axleAt", 0.1);
        modules.set(modules.size() - 1, trunk);
        BodyDesign body = new BodyDesign("6x6 body", RecordEditor.with(sedan.settings(), "onFrame", true), modules);
        var shape = body.shape();
        var points = body.mountPoints(shape);
        double rear = points.get("rear_axle").position().x(), middle = points.get("m5_axle").position().x(), front = points.get("front_axle").position().x();
        var box = ChassisSettingsDesign.RailSection.RECT_TUBE;
        var frame = new ChassisSettingsDesign("6x6 frame", ChassisSettingsDesign.Kind.LADDER, EngineMaterial.STEEL_1045, shape.lengthM() - 0.2, 0.9, 0.9,
                kind.rails, kind.heightM, kind.widthM, kind.wallM, 0, 0, kind.joint, 0.038, 0.0016, kind.grossKg, "");
        var b0 = kind.brace;
        ChassisDesign chassis = new ChassisDesign("6x6 frame", frame, List.of(
                station(ChassisStationDesign.Kind.CROSSMEMBER, 0.1, 0, box, b0),
                station(ChassisStationDesign.Kind.AXLE, rear, axleHeightM, box, b0),
                station(ChassisStationDesign.Kind.AXLE, middle, axleHeightM, box, b0),
                station(ChassisStationDesign.Kind.BODY_MOUNT, 0.5 * (middle + front) - 0.4, 0, box, b0),
                station(ChassisStationDesign.Kind.ENGINE_MOUNT, front - 0.35, 0.05, box, b0),
                station(ChassisStationDesign.Kind.AXLE, front, axleHeightM, box, b0),
                station(ChassisStationDesign.Kind.CROSSMEMBER, shape.lengthM() - 0.3, 0, box, ChassisStationDesign.Brace.NONE)));
        AxleDesign f = SampleWheels.offRoadFront(), r = SampleWheels.offRoadRear();
        AxleDesign steered = new AxleDesign(f.name(), RecordEditor.with(f.layout(), "wheelbaseM", front - 0.5 * (rear + middle)), f.tyre(), f.wheel(), f.brake(),
                f.suspension(), f.differential());
        VehicleBlueprint b = new VehicleBlueprint("6x6", List.of(new VehicleBlueprint.Entry("chassis", chassis, Mount.ORIGIN)), List.of());
        for (PartDesign d : List.of(body, steered, r, r, dev.mcmod.hdfv.part.engine.SampleEngines.inlineFour(), SampleGearboxes.sixSpeedAutomatic(), SampleGearboxes.partTime())) {
            b = Placing.add(b, d);
        }
        return AutoWiring.wire(b);
    }

    /**
     * 0.18: a truck axle of 315/80R22.5 tyres at 830 kPa on 22.5 x 9 steel rims, leaf springs of a 1.4 Hz ride at its corner
     * load (0.3 of critical damping), 410 x 200 mm air-actuated drums with a spring parking brake of half, ABS, 2.05 m track;
     * steered 45 degrees for a wheelbase, or driven through a 4.1 hypoid with a locker, or neither (a trailer's).
     */
    public static AxleDesign truckAxle(String name, boolean steered, boolean driven, double cornerKg, double wheelbaseM) {
        var tyre = new dev.mcmod.hdfv.part.wheel.TyreDesign("315/80R22.5", dev.mcmod.aerocore.api.wheel.Tyre.Construction.RADIAL, 0.315, 0.8, 22.5, 830e3, 900e3,
                dev.mcmod.aerocore.api.wheel.Tyre.Tread.ROAD, 1);
        var rim = new dev.mcmod.hdfv.part.wheel.RimDesign("22.5 x 9 steel", 9.0, dev.mcmod.hdfv.part.wheel.RimDesign.Material.STEEL, 10, Double.NaN);
        var brake = new dev.mcmod.hdfv.part.wheel.BrakeDesign("air drum", dev.mcmod.hdfv.part.wheel.BrakeDesign.Kind.DRUM_LEADING_TRAILING, 0.41, 0.2, 0.06, 1, 8e6,
                steered ? 0 : 0.5, true, Double.NaN);
        double rate = cornerKg * Math.pow(2 * Math.PI * 1.4, 2), c = 2 * 0.3 * Math.sqrt(rate * cornerKg);
        var springs = new dev.mcmod.hdfv.part.wheel.SuspensionDesign("leaf springs", dev.mcmod.aerocore.api.wheel.Suspension.Type.SOLID_LEAF, 0.2, 0.1, rate, cornerKg,
                0.7 * c, 1.6 * c, 0, -1, 20, 0, 0, Double.NaN);
        var layout = new dev.mcmod.hdfv.part.wheel.AxleLayoutDesign("layout", 2.05, 0, steered, steered ? 45 : 0, steered ? 0.8 : 0, steered ? wheelbaseM : 0, 0);
        var diff = driven ? new dev.mcmod.hdfv.part.wheel.DifferentialDesign("hypoid", true, 4.1, 0.95, false, dev.mcmod.aerocore.api.vehicle.part.AxlePart.Differential.OPEN,
                50, 0.3, 3, 30, true, Double.NaN) : dev.mcmod.hdfv.part.wheel.DifferentialDesign.none();
        return new AxleDesign(name, layout, tyre, rim, brake, springs, diff);
    }

    /**
     * 0.18: a 6x4 tractor: the sample tractor frame, the sample cab-over cab on it, a steered front axle (to the tandem's
     * middle) and a driven tandem, the turbodiesel with a six-speed automatic, the sample fifth wheel; laid out and wired
     * as an assembly room does, with a car's fuel tank for its diesel beside the frame.
     */
    public static VehicleBlueprint tractor6x4() {
        ChassisDesign frame = dev.mcmod.hdfv.part.chassis.SampleChassis.tractor6x4();
        var points = frame.mountPoints();
        double front = points.get("axle_1").position().x(), tandem = 0.5 * (points.get("axle_2").position().x() + points.get("axle_3").position().x());
        AxleDesign steer = truckAxle("tractor front axle", true, false, 2400, front - tandem), drive = truckAxle("tractor drive axle", false, true, 2800, 0);
        VehicleBlueprint b = new VehicleBlueprint("6x4 tractor", List.of(new VehicleBlueprint.Entry("chassis", frame, Mount.ORIGIN)), List.of());
        for (PartDesign d : List.of(SampleBodies.cabOver(), steer, drive, drive, dev.mcmod.hdfv.part.engine.SampleEngines.turboDiesel(), SampleGearboxes.sixSpeedAutomatic(),
                dev.mcmod.hdfv.part.truck.FifthWheelDesign.standard())) {
            b = Placing.add(b, d);
        }
        String fuel = dev.mcmod.hdfv.fluid.FuelGrades.takenBy(dev.mcmod.hdfv.part.engine.SampleEngines.turboDiesel()).iterator().next();
        List<VehicleBlueprint.Entry> parts = new ArrayList<>(b.parts());
        parts.add(new VehicleBlueprint.Entry("tank", dev.mcmod.hdfv.part.FuelTankDesign.car(fuel), Mount.at(new dev.mcmod.aerocore.api.Vec3d(3.0, 0, 0.75))));
        return AutoWiring.wire(new VehicleBlueprint(b.name(), parts, List.of()));
    }

    /**
     * 0.18: a three-axle gooseneck low-loader: the sample frame, the sample low-loader decks on it, a tridem of trailer
     * axles (braked, a parking brake of half) and a pair of the sample landing legs; laid out and wired as an assembly
     * room does.
     */
    public static VehicleBlueprint lowLoader() {
        AxleDesign axle = truckAxle("trailer axle", false, false, 3000, 0);
        VehicleBlueprint b = new VehicleBlueprint("low-loader", List.of(new VehicleBlueprint.Entry("chassis", dev.mcmod.hdfv.part.chassis.SampleChassis.lowLoader3(),
                Mount.ORIGIN)), List.of());
        for (PartDesign d : List.of(SampleBodies.lowLoaderDeck(), axle, axle, axle, dev.mcmod.hdfv.part.truck.LandingLegsDesign.standard())) { b = Placing.add(b, d); }
        return AutoWiring.wire(b);
    }

    /** The 6x6's axle stations' height over its rails' bottom, m (about 0.25 m from the rails to the ground on its tyres). */
    static final double AXLE_HEIGHT_M = 0.1;

    /**
     * 0.15: the sedan with its rear wheels steered: up to 8 degrees against the front below 12 m/s (a tighter turn),
     * with it at 0.3 of that above (a steadier lane change), blended over SteerSchedule.BLEND_MPS.
     */
    public static VehicleBlueprint rearSteerSedan() {
        AxleDesign r = SampleWheels.sedanRear();
        var l = r.layout();
        var steered = new dev.mcmod.hdfv.part.wheel.AxleLayoutDesign(l.name(), l.trackM(), l.hubDropM(), true, 8, 1, -0.4, l.antiRollNPerM(), -1, 0.3, 12);
        AxleDesign rear = new AxleDesign(r.name(), steered, r.tyre(), r.wheel(), r.brake(), r.suspension(), r.differential());
        return wired("rear-steer sedan", Map.of("rear_axle", rear), null);
    }

    private static ChassisStationDesign station(ChassisStationDesign.Kind kind, double x, double height, ChassisSettingsDesign.RailSection section,
                                                ChassisStationDesign.Brace brace) {
        return new ChassisStationDesign(kind.name().toLowerCase(java.util.Locale.ROOT), kind, x, height, section, 0.1, 0.05, 0.003, brace, 0.5, 0.9, false);
    }

    /**
     * 0.15: the sedan with both axles in the hardpoints mode (the points the simple geometry gives: it drives as the
     * sample does, its arms and links drawn where the points are and moved by the kinematic table).
     */
    public static VehicleBlueprint hardpointSedan() {
        Map<String, PartDesign> swapped = new java.util.HashMap<>();
        for (var e : Map.of("front_axle", SampleWheels.sedanFront(), "rear_axle", SampleWheels.sedanRear()).entrySet()) {
            AxleDesign a = e.getValue();
            var on = RecordEditor.with(a.hardpoints(), "mode", dev.mcmod.hdfv.part.wheel.HardpointsDesign.Mode.HARDPOINTS);
            swapped.put(e.getKey(), new AxleDesign(a.name(), a.layout(), a.tyre(), a.wheel(), a.brake(), a.suspension(), a.differential(), on));
        }
        return wired("hardpoint sedan", swapped, null);
    }

    /** 0.15: the manual sedan with air springs at the rear (a levelling valve: "ride_height" steps it 4 cm down or 5 cm up). */
    public static VehicleBlueprint airSedan() {
        AxleDesign r = SampleWheels.sedanRear();
        var air = RecordEditor.with(r.suspension(), "springKind", dev.mcmod.hdfv.part.wheel.SuspensionDesign.SpringKind.AIR);
        AxleDesign rear = new AxleDesign(r.name(), r.layout(), r.tyre(), r.wheel(), r.brake(), air, r.differential());
        return wired("air sedan", Map.of("gearbox", SampleGearboxes.fiveSpeedManual(), "rear_axle", rear), null);
    }

    /** The sedan's layout with parts swapped (a gearbox put behind the engine again: 0.14), and a transfer case behind the gearbox. */
    private static VehicleBlueprint wired(String name, Map<String, PartDesign> swapped, PartDesign transfer) {
        List<VehicleBlueprint.Entry> parts = new ArrayList<>();
        VehicleBlueprint.Entry engine = null, gearbox = null;
        for (VehicleBlueprint.Entry e : SampleLayouts.sedan().parts()) {
            PartDesign d = swapped.get(e.id());
            VehicleBlueprint.Entry x = d == null ? e : new VehicleBlueprint.Entry(e.id(), d, e.mount());
            if (x.id().equals("engine")) { engine = x; }
            if (x.id().equals("gearbox")) {
                gearbox = x = new VehicleBlueprint.Entry(x.id(), x.design(), Mount.on("body", "engine_bay", Placing.behind(engine.design(), engine.mount().detached(), x.design())));
            }
            parts.add(x);
        }
        if (transfer != null) {
            parts.add(new VehicleBlueprint.Entry("transfer", transfer, Mount.on("body", "engine_bay", Placing.behind(gearbox.design(), gearbox.mount().detached(), transfer))));
        }
        return AutoWiring.wire(new VehicleBlueprint(name, parts, List.of()));
    }
}
