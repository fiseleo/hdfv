package dev.mcmod.hdfv.drive;

import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.hydraulic.Pump;
import dev.mcmod.aerocore.api.vehicle.Mount;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.VehicleBlueprint;
import dev.mcmod.aerocore.api.vehicle.part.TrackPart;
import dev.mcmod.hdfv.assembly.AutoWiring;
import dev.mcmod.hdfv.part.BallastDesign;
import dev.mcmod.hdfv.part.FuelTankDesign;
import dev.mcmod.hdfv.part.SeatDesign;
import dev.mcmod.hdfv.part.drive.SampleGearboxes;
import dev.mcmod.hdfv.part.engine.SampleEngines;
import dev.mcmod.hdfv.part.machine.HydraulicPumpDesign;
import dev.mcmod.hdfv.part.machine.HydraulicUnit;
import dev.mcmod.hdfv.part.machine.OperatorCabDesign;
import dev.mcmod.hdfv.part.machine.EngineHoodDesign;
import dev.mcmod.hdfv.part.machine.SampleMachinery;
import dev.mcmod.hdfv.part.machine.SampleTools;
import dev.mcmod.hdfv.part.machine.TrackDesign;
import dev.mcmod.hdfv.part.machine.WorkToolDesign;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 0.26: whole test machines put together from the sample parts (the user: only sample parts in the game; whole machines
 * only here and in /hdfv dev drive spawn), wired as an assembly room wires them (AutoWiring). Frames: x forward, y up,
 * z to the right; a crawler's origin between its tracks at the rollers' hubs. The places are rough (a test rig, not a
 * maker's general arrangement): each machine has what its kind works with, on the circuits its parts name.
 */
public final class TestMachines {
    private TestMachines() {}

    private static final double CC = 1e-6, BAR = 1e5;

    /** A power-limited axial piston pump of a displacement (cm^3) on a circuit at a relief (bar) and power (kW). */
    static HydraulicPumpDesign pump(String name, String circuit, double cm3, double bar, double kw) {
        return new HydraulicPumpDesign(name, circuit, HydraulicUnit.AXIAL_PISTON, Pump.Control.POWER_LIMITED, cm3 * CC, bar * BAR, kw * 1000, 1800);
    }

    record Part(String id, PartDesign design, Mount mount) {}

    static VehicleBlueprint wired(String name, List<Part> parts) {
        List<VehicleBlueprint.Entry> entries = new ArrayList<>();
        for (Part p : parts) { entries.add(new VehicleBlueprint.Entry(p.id(), p.design(), p.mount())); }
        return AutoWiring.wire(new VehicleBlueprint(name, entries, List.of()));
    }

    static Mount at(double x, double y, double z) { return Mount.at(new Vec3d(x, y, z)); }

    static Mount on(String part, String point, double x, double y, double z) { return Mount.on(part, point, at(x, y, z)); }

    static Mount on(String part, String point) { return Mount.on(part, point); }

    // ---------------------------------------------------------------- building blocks

    /** A crawler's undercarriage: tracks, a travel motor a side (pumped on "travel" by the house's travel pump). */
    static void crawler(List<Part> p, PartDesign tracks) {
        p.add(new Part("tracks", tracks, Mount.ORIGIN));
        p.add(new Part("travel_left", SampleMachinery.travelMotor(), at(-1.9, 0, -1.1)));
        p.add(new Part("travel_right", SampleMachinery.travelMotor(), at(-1.9, 0, 1.1)));
    }

    /**
     * The power unit on a mount (the house on a slewing ring, or the frame): the diesel, the main pump and pumps for the
     * other circuits the machine has, a fuel tank, the operator's seat and a counterweight (kg, 0 for none); on a house,
     * a cab round the seat (2026-11-22: a mini excavator's up to 1 t of counterweight, else a 20 t excavator's) and the
     * 20 t excavator's hood behind it (every house's diesel is the same 11.9 L one).
     */
    static void powerUnit(List<Part> p, String part, String point, double counterweightKg, String... circuits) {
        p.add(new Part("engine", SampleEngines.generatorDiesel(), mount(part, point, -1.7, 0.5, 0.5)));
        p.add(new Part("pump", SampleMachinery.excavatorPump(), mount(part, point, -1.0, 0.45, 0.5)));
        double z = 0.2;
        for (String c : circuits) {
            p.add(new Part(c + "_pump", pump(c + " pump", c, c.equals("travel") ? 71 : 45, c.equals("travel") ? 350 : 280, 40), mount(part, point, -1.0, 0.45, z)));
            z -= 0.3;
        }
        // on a house the tank goes behind the engine on the right, under the hood (2026-11-22); on a frame, ahead of the engine
        p.add(new Part("tank", FuelTankDesign.car("diesel"), part != null ? on(part, point, -2.5, 0.35, 0.8) : mount(part, point, -0.6, 0.35, 1.0)));
        p.add(new Part("seat", new SeatDesign("seat", 10), mount(part, point, SEAT_X, SEAT_Y, -0.8)));
        if (counterweightKg > 0) { p.add(new Part("counterweight", new BallastDesign("counterweight", counterweightKg), mount(part, point, -2.5, 0.5, 0))); }
        if (part != null) {
            // 2026-11-22: a house's cab round its seat at the front left, beside the boom (a crane's set back from its boom's foot),
            // its floor SEAT_FLOOR_M under the seat; the hood behind it over the engine, pumps, tank and counterweight, its bottom
            // just under the engine's sump
            OperatorCabDesign cab = counterweightKg <= 1000 ? SampleMachinery.miniExcavatorCab() : SampleMachinery.excavatorCab();
            double cabX = counterweightKg >= 8000 ? 0.0 : 0.15, floor = SEAT_Y - SEAT_FLOOR_M;
            p.add(new Part("cab", cab, mount(part, point, cabX, floor + 0.5 * cab.heightM(), -0.8)));
            EngineHoodDesign hood = SampleMachinery.excavatorHood();
            double hoodFront = cabX - 0.5 * cab.lengthM() - 0.03;
            p.add(new Part("hood", hood, mount(part, point, hoodFront - 0.5 * hood.lengthM(), HOUSE_HOOD_BOTTOM_M + 0.5 * hood.heightM(), 0)));
        }
    }

    /** The power unit's seat on its mount (its cushion), m. */
    static final double SEAT_X = 0.3, SEAT_Y = 0.4;

    /** A house's hood's bottom over the house's point (the slewing ring's top), m: just under the diesel's sump. */
    static final double HOUSE_HOOD_BOTTOM_M = 0.05;

    /**
     * A loader's (or a roller's) hood behind its cab (2026-11-22): its front just behind the cab's rear (the cab round
     * the seat at seatX, loaderCab), its bottom at y.
     */
    static Part loaderHood(EngineHoodDesign hood, double seatX, double y) {
        double cabRear = seatX - 0.15 - 0.5 * SampleMachinery.loaderCab().lengthM();
        return new Part("hood", hood, at(cabRear - 0.02 - 0.5 * hood.lengthM(), y + 0.5 * hood.heightM(), 0));
    }

    /** How far the seat's point (its cushion) stands over the floor it is on, m. */
    static final double SEAT_FLOOR_M = 0.32;

    /** A loader's (or a roller's) cab round its seat at (x, y) on the frame's centre line, the seat a third from its rear. */
    static Part loaderCab(double seatX, double seatY) {
        OperatorCabDesign cab = SampleMachinery.loaderCab();
        return new Part("cab", cab, at(seatX - 0.15, seatY - SEAT_FLOOR_M + 0.5 * cab.heightM(), 0));
    }

    private static Mount mount(String part, String point, double x, double y, double z) { return part == null ? at(x, y, z) : on(part, point, x, y, z); }

    /** A slewing ring on the undercarriage at a height, the house riding on its "upper". */
    static void slew(List<Part> p, double y) { p.add(new Part("swing", SampleMachinery.swingRing(), at(0, y, 0))); }

    /** The parts of the 6x4 test truck (TestCars.tractor6x4) without its fifth wheel, unwired: a carrier for a body or a superstructure. */
    static List<Part> truck() {
        List<Part> p = new ArrayList<>();
        for (VehicleBlueprint.Entry e : AutoWiring.stripped(TestCars.tractor6x4()).parts()) {
            if (e.design() instanceof dev.mcmod.hdfv.part.truck.FifthWheelDesign) { continue; }
            p.add(new Part(e.id(), e.design(), e.mount()));
        }
        return p;
    }

    /** Where the truck's fifth wheel stood (its frame's top over the tandem): bodies and superstructures go from there. */
    static Vec3d truckDeck() {
        for (VehicleBlueprint.Entry e : TestCars.tractor6x4().parts()) {
            if (e.design() instanceof dev.mcmod.hdfv.part.truck.FifthWheelDesign) {
                var frame = dev.mcmod.hdfv.part.chassis.SampleChassis.tractor6x4().mountPoints();
                Mount m = frame.get(e.mount().attachPoint());
                return (m == null ? Vec3d.ZERO : m.position()).add(e.mount().position());
            }
        }
        return new Vec3d(-2, 1.1, 0);
    }

    static Part truckPart(String id, PartDesign d, double dx, double dy, double dz) {
        Vec3d deck = truckDeck();
        return new Part(id, d, at(deck.x() + dx, deck.y() + dy, deck.z() + dz));
    }

    /** A wheeled machine's frame: a front and a rear driven axle (the truck's), the diesel through the automatic box. */
    static void wheeled(List<Part> p, double front, double rear, boolean steers) {
        p.add(new Part("front_axle", TestCars.truckAxle("front axle", steers, true, 3500, front - rear), at(front, 0, 0)));
        p.add(new Part("rear_axle", TestCars.truckAxle("rear axle", false, true, 3500, 0), at(rear, 0, 0)));
        p.add(new Part("engine", SampleEngines.generatorDiesel(), at(rear - 0.6, 0.7, 0)));
        p.add(new Part("gearbox", SampleGearboxes.sixSpeedAutomatic(), at(rear + 0.3, 0.5, 0)));
        p.add(new Part("pump", SampleMachinery.excavatorPump(), at(rear - 0.2, 0.7, 0.4)));
        p.add(new Part("tank", FuelTankDesign.car("diesel"), at(rear, 0.6, -0.8)));
        p.add(new Part("seat", new SeatDesign("seat", 10), at(0.2, 1.2, 0)));
    }

    // ---------------------------------------------------------------- earthmoving

    /**
     * A 20 t crawler excavator (CAT 320 class): the sample tracks with a travel motor a side, a slewing ring with the
     * house on it (the diesel, the main, swing and travel pumps, the fuel tank, the operator's seat and 3.5 t of
     * counterweight), the boom at its foot, the stick and a 1 m^3 bucket.
     */
    public static VehicleBlueprint excavator() { return excavatorWith("test excavator", SampleTools.excavatorBucket1m3()); }

    static VehicleBlueprint excavatorWith(String name, WorkToolDesign tool) {
        List<Part> p = new ArrayList<>();
        crawler(p, SampleMachinery.excavatorTracks20t());
        slew(p, 0.75);
        powerUnit(p, "swing", "upper", 3500, "swing", "travel", "auxiliary");
        p.add(new Part("boom", SampleMachinery.excavatorBoom(), on("swing", "upper", 0.6, 0.7, 0.3)));
        p.add(new Part("stick", SampleMachinery.excavatorStick(), on("boom", "tip")));
        p.add(new Part("bucket", tool, on("stick", "tip")));
        return wired(name, p);
    }

    /** A mini excavator on rubber tracks with the mini bucket. */
    public static VehicleBlueprint miniExcavator() {
        List<Part> p = new ArrayList<>();
        crawler(p, SampleMachinery.miniExcavatorTracks());
        slew(p, 0.45);
        powerUnit(p, "swing", "upper", 400, "swing", "travel");
        p.add(new Part("boom", SampleMachinery.excavatorBoom(), on("swing", "upper", 0.5, 0.5, 0.2)));
        p.add(new Part("stick", SampleMachinery.excavatorStick(), on("boom", "tip")));
        p.add(new Part("bucket", SampleTools.miniExcavatorBucket(), on("stick", "tip")));
        return wired("test mini excavator", p);
    }

    /** The excavator with a hydraulic hammer. */
    public static VehicleBlueprint hammerExcavator() { return excavatorWith("test hammer excavator", SampleTools.hydraulicHammer()); }

    /** The excavator with a grapple. */
    public static VehicleBlueprint grappleExcavator() { return excavatorWith("test grapple excavator", SampleTools.grapple()); }

    /** A face-shovel mining excavator (the 30 m^3 dipper on the excavator's front: a scaled rig). */
    public static VehicleBlueprint miningShovel() { return excavatorWith("test mining shovel", SampleTools.miningDipper30m3()); }

    /** A crawler dozer (D6 class): dozer tracks, the push frame (a lift arm) with the SU blade, a single-shank ripper behind. */
    public static VehicleBlueprint dozer() {
        List<Part> p = new ArrayList<>();
        crawler(p, SampleMachinery.dozerTracksD6());
        powerUnit(p, null, null, 0, "travel");
        p.add(new Part("push_frame", SampleMachinery.loaderArm(), at(0.8, 0.3, 0)));
        p.add(new Part("blade", SampleTools.dozerSuBlade(), on("push_frame", "tip")));
        p.add(new Part("ripper", SampleTools.ripperSingle(), at(-2.6, 0.2, 0)));
        return wired("test dozer", p);
    }

    /** A crawler tractor: the dozer's tracks and power unit alone. */
    public static VehicleBlueprint crawlerTractor() {
        List<Part> p = new ArrayList<>();
        crawler(p, SampleMachinery.dozerTracksD6());
        powerUnit(p, null, null, 0, "travel");
        return wired("test crawler tractor", p);
    }

    /** A wheel loader (CAT 950 class): two axles on a centre hinge, the lift arms and the 3 m^3 bucket on the front frame. */
    public static VehicleBlueprint wheelLoader() {
        List<Part> p = new ArrayList<>();
        p.add(new Part("rear_axle", TestCars.truckAxle("rear axle", false, true, 4500, 0), at(-1.7, 0, 0)));
        p.add(new Part("hinge", SampleMachinery.articulationHinge(), at(0, 0.5, 0)));
        p.add(new Part("front_axle", TestCars.truckAxle("front axle", false, true, 4500, 0), on("hinge", "front", 1.3, -0.5, 0)));
        p.add(new Part("engine", SampleEngines.generatorDiesel(), at(-2.3, 0.8, 0)));
        p.add(new Part("gearbox", SampleGearboxes.sixSpeedAutomatic(), at(-1.2, 0.6, 0)));
        p.add(new Part("pump", SampleMachinery.excavatorPump(), at(-1.6, 0.8, 0.4)));
        p.add(new Part("steering_pump", pump("steering pump", "steering", 45, 240, 25), at(-1.6, 0.8, -0.4)));
        p.add(new Part("tank", FuelTankDesign.car("diesel"), at(-1.4, 0.6, -0.9)));
        p.add(new Part("seat", new SeatDesign("seat", 10), at(-0.6, 1.4, 0)));
        p.add(loaderCab(-0.6, 1.4));
        p.add(loaderHood(SampleMachinery.loaderHood(), -0.6, 0.35));
        p.add(new Part("counterweight", new BallastDesign("counterweight", 1500), at(-3.0, 0.6, 0)));
        p.add(new Part("arm", SampleMachinery.loaderArm(), on("hinge", "front", 0.4, 1.0, 0)));
        p.add(new Part("bucket", SampleTools.loaderBucket3m3(), on("arm", "tip")));
        return wired("test wheel loader", p);
    }

    /** A backhoe loader: a loader on the front, a hoe on a slewing post at the rear. */
    public static VehicleBlueprint backhoeLoader() {
        List<Part> p = new ArrayList<>();
        wheeled(p, 1.2, -1.0, true);
        p.add(new Part("swing_pump", pump("swing pump", "swing", 30, 250, 20), at(-1.3, 0.7, -0.4)));
        p.add(new Part("arm", SampleMachinery.loaderArm(), at(0.6, 1.0, 0)));
        p.add(new Part("bucket", SampleTools.loaderBucket3m3(), on("arm", "tip")));
        p.add(new Part("swing", SampleMachinery.swingRing(), at(-2.2, 0.6, 0)));
        p.add(new Part("boom", SampleMachinery.excavatorBoom(), on("swing", "upper", -0.3, 0.2, 0)));
        p.add(new Part("stick", SampleMachinery.excavatorStick(), on("boom", "tip")));
        p.add(new Part("hoe", SampleTools.miniExcavatorBucket(), on("stick", "tip")));
        return wired("test backhoe loader", p);
    }

    /** A motor grader: a long frame on two axles, the moldboard under the middle. */
    public static VehicleBlueprint grader() {
        List<Part> p = new ArrayList<>();
        wheeled(p, 3.0, -1.5, true);
        p.add(new Part("circle", SampleMachinery.loaderArm(), at(1.5, 0.6, 0)));
        p.add(new Part("moldboard", SampleTools.graderMoldboard(), on("circle", "tip")));
        return wired("test grader", p);
    }

    /** A scraper: a tractor on a centre hinge drawing the 15 m^3 bowl on its rear axle. */
    public static VehicleBlueprint scraper() {
        List<Part> p = new ArrayList<>();
        p.add(new Part("front_axle", TestCars.truckAxle("tractor axle", false, true, 5000, 0), at(2.5, 0, 0)));
        p.add(new Part("hinge", SampleMachinery.articulationHinge(), at(1.5, 0.6, 0)));
        p.add(new Part("rear_axle", TestCars.truckAxle("bowl axle", false, false, 6000, 0), at(-4, 0, 0)));
        p.add(new Part("engine", SampleEngines.generatorDiesel(), at(3.2, 0.8, 0)));
        p.add(new Part("gearbox", SampleGearboxes.sixSpeedAutomatic(), at(2.4, 0.6, 0)));
        p.add(new Part("pump", SampleMachinery.excavatorPump(), at(2.8, 0.9, 0.4)));
        p.add(new Part("steering_pump", pump("steering pump", "steering", 45, 240, 25), at(2.8, 0.9, -0.4)));
        p.add(new Part("tank", FuelTankDesign.car("diesel"), at(2.6, 0.7, -0.9)));
        p.add(new Part("seat", new SeatDesign("seat", 10), at(2.2, 1.6, 0)));
        p.add(new Part("bowl", SampleTools.scraperBowl15m3(), at(-4.2, 0.4, 0)));
        return wired("test scraper", p);
    }

    // ---------------------------------------------------------------- haulage

    /** A 6x4 dump truck: the test truck with the 10 m^3 body over its tandem. */
    public static VehicleBlueprint dumpTruck() {
        List<Part> p = truck();
        p.add(truckPart("body", SampleTools.dumpTruckBody10m3(), -2.6, 0.1, 0));
        p.add(truckPart("pump", SampleMachinery.excavatorPump(), 2.5, -0.3, 0.5));
        return wired("test dump truck", p);
    }

    /** An off-highway haul truck (the 60 m^3 body on the test truck: a scaled rig). */
    public static VehicleBlueprint haulTruck() {
        List<Part> p = truck();
        p.add(truckPart("body", SampleTools.haulTruckBody60m3(), -3.0, 0.2, 0));
        p.add(truckPart("pump", SampleMachinery.excavatorPump(), 2.5, -0.3, 0.5));
        return wired("test haul truck", p);
    }

    /** An articulated dump truck: a tractor on a centre hinge, the 18 m^3 body on the trailer's axle. */
    public static VehicleBlueprint articulatedTruck() {
        List<Part> p = new ArrayList<>();
        p.add(new Part("front_axle", TestCars.truckAxle("front axle", false, true, 4000, 0), at(2.0, 0, 0)));
        p.add(new Part("hinge", SampleMachinery.articulationHinge(), at(0.8, 0.6, 0)));
        p.add(new Part("rear_axle", TestCars.truckAxle("rear axle", false, true, 6000, 0), on("hinge", "front", -4.0, -0.6, 0)));
        p.add(new Part("engine", SampleEngines.generatorDiesel(), at(2.6, 0.9, 0)));
        p.add(new Part("gearbox", SampleGearboxes.sixSpeedAutomatic(), at(1.6, 0.6, 0)));
        p.add(new Part("pump", SampleMachinery.excavatorPump(), at(2.3, 1.0, 0.4)));
        p.add(new Part("steering_pump", pump("steering pump", "steering", 45, 240, 25), at(2.3, 1.0, -0.4)));
        p.add(new Part("tank", FuelTankDesign.car("diesel"), at(2.0, 0.8, -0.9)));
        p.add(new Part("seat", new SeatDesign("seat", 10), at(1.9, 1.7, 0)));
        p.add(new Part("body", SampleTools.articulatedTruckBody18m3(), on("hinge", "front", -5.5, 0.4, 0)));
        return wired("test articulated truck", p);
    }

    // ---------------------------------------------------------------- roads

    /** A single-drum roller: the smooth drum in front on the hinge, the drive axle behind. */
    public static VehicleBlueprint roller() { return roller("test roller", SampleMachinery.rollerDrum11t()); }

    /** The roller with the padfoot drum (a sheepsfoot roller). */
    public static VehicleBlueprint padfootRoller() { return roller("test padfoot roller", SampleMachinery.padfootDrum11t()); }

    /** A single-drum roller's hood (2026-11-22): the loader's shorter, 1.6 m (no counterweight behind the engine). */
    static final EngineHoodDesign ROLLER_HOOD = new EngineHoodDesign("roller hood", 1.6, 1.5, 1.35, 0.2, 0.2, EngineHoodDesign.Grille.REAR, 0.4);

    static VehicleBlueprint roller(String name, PartDesign drum) {
        List<Part> p = new ArrayList<>();
        p.add(new Part("rear_axle", TestCars.truckAxle("drive axle", false, true, 3000, 0), at(-1.4, 0, 0)));
        p.add(new Part("hinge", SampleMachinery.articulationHinge(), at(0, 0.4, 0)));
        p.add(new Part("drum", drum, on("hinge", "front", 1.2, -0.4, 0)));
        // 2026-11-22: the engine behind the cab (at -1.9 its radiator stood in the cab), under its hood
        p.add(new Part("engine", SampleEngines.generatorDiesel(), at(-2.3, 0.8, 0)));
        p.add(new Part("gearbox", SampleGearboxes.sixSpeedAutomatic(), at(-0.9, 0.6, 0)));
        p.add(new Part("steering_pump", pump("steering pump", "steering", 45, 240, 25), at(-1.6, 0.8, -0.4)));
        p.add(new Part("tank", FuelTankDesign.car("diesel"), at(-1.2, 0.6, -0.8)));
        p.add(new Part("seat", new SeatDesign("seat", 10), at(-0.6, 1.4, 0)));
        p.add(loaderCab(-0.6, 1.4));
        p.add(loaderHood(ROLLER_HOOD, -0.6, 0.35));
        return wired(name, p);
    }

    /** A tandem roller: two drums, front one on the hinge. */
    public static VehicleBlueprint tandemRoller() {
        List<Part> p = new ArrayList<>();
        p.add(new Part("rear_drum", SampleMachinery.tandemDrum(), at(-1.2, 0, 0)));
        p.add(new Part("hinge", SampleMachinery.articulationHinge(), at(0, 0.4, 0)));
        p.add(new Part("drum", SampleMachinery.tandemDrum(), on("hinge", "front", 0.9, -0.4, 0)));
        powerUnit(p, null, null, 0, "steering");   // a test rig: its drums are not driven
        return wired("test tandem roller", p);
    }

    /** An asphalt paver on tracks: the hopper in front, the conveyor through, the screed behind. */
    public static VehicleBlueprint paver() {
        List<Part> p = new ArrayList<>();
        crawler(p, SampleMachinery.miniExcavatorTracks());
        powerUnit(p, null, null, 0, "travel", "auxiliary");
        p.add(new Part("hopper", SampleTools.paverHopper(), at(1.2, 0.3, 0)));
        p.add(new Part("conveyor", SampleTools.conveyor1m(), at(0.8, 0.2, 0)));
        p.add(new Part("screed", SampleTools.screed3m(), at(-2.2, -0.3, 0)));
        return wired("test paver", p);
    }

    /** A cold planer on tracks: the 2 m milling drum under it, the loading conveyor ahead. */
    public static VehicleBlueprint coldPlaner() {
        List<Part> p = new ArrayList<>();
        crawler(p, SampleMachinery.excavatorTracks20t());
        powerUnit(p, null, null, 0, "travel", "auxiliary");
        p.add(new Part("drum", SampleTools.millingDrum2m(), at(0, -0.1, 0)));
        p.add(new Part("drum_motor", cutterMotor(), at(0, 0.4, 0.8)));
        p.add(new Part("conveyor", SampleTools.conveyor1m(), at(1.8, 0.4, 0)));
        return wired("test cold planer", p);
    }

    /** A hydraulic motor for a cutter's drive (the travel motor on the auxiliary circuit). */
    static PartDesign cutterMotor() {
        var m = SampleMachinery.travelMotor();
        return new dev.mcmod.hdfv.part.machine.HydraulicMotorDesign("cutter drive", "auxiliary", m.motorKind(), m.displacementM3(), m.reliefPa(), m.maxSpeedRpm(),
                m.reductionRatio(), m.brakeNm(), false, m.drumDiameterM(), m.drumWidthM(), m.ropeDiameterM(), m.partsOfLine(), m.ropeMinM(), m.ropeMaxM(), m.ropeStartM());
    }

    // ---------------------------------------------------------------- lifting

    /** A truck crane: the test truck with a slewing ring, the crane's base boom, a telescopic section, a hoist winch and four outriggers. */
    public static VehicleBlueprint truckCrane() {
        List<Part> p = truck();
        p.add(truckPart("swing", SampleMachinery.swingRing(), -1.0, 0.1, 0));
        p.add(truckPart("pump", SampleMachinery.excavatorPump(), 2.5, -0.3, 0.5));
        p.add(truckPart("swing_pump", pump("swing pump", "swing", 45, 280, 30), 2.5, -0.3, -0.5));
        p.add(new Part("boom", SampleMachinery.craneBoomSection(), on("swing", "upper", -1.0, 0.6, 0)));
        p.add(new Part("telescope", SampleMachinery.craneBoomSection(), on("boom", "nest")));
        p.add(new Part("winch", SampleMachinery.hoistWinch(), on("swing", "upper", -1.8, 0.6, 0)));
        int i = 1;
        for (double x : new double[]{2.0, -4.0}) for (double z : new double[]{-1.2, 1.2}) {
            p.add(truckPart("outrigger_" + i++, SampleMachinery.outrigger(), x, -0.4, z));
        }
        return wired("test truck crane", p);
    }

    /**
     * 2026-11-22: a 100 t crawler crane's crawlers (Kobelco CKE900G specification sheet: 5.44 m between the tumblers,
     * 6.28 m over the crawlers, 800 mm flat shoes, 5.13 m over the crawlers extended and 3.5 m retracted, so a 4.33 m
     * gauge extended; 90.1 t operating weight on 101 kPa). Nine road wheels a side, a 0.25 m pitch and 0.25 m tumblers
     * are assumed (0.42 m over their shoes, which gives the 6.28 m); the parking brakes scaled from the 20 t excavator's
     * by the weight. Before, the crane stood on the 20 t excavator's tracks with 8 t of counterweight: the 30 m boom put
     * its centre of gravity 2.36 m ahead of the slewing centre, past the tracks' front end (1.83 m), and it fell onto its
     * boom as it was set down (the same for the dragline, 4.5 m ahead).
     */
    static final TrackDesign CRANE_TRACKS = new TrackDesign("crane crawlers 100 t", 90000, 4.33, 5.44, 0.8, 9, 0.18, TrackPart.Shoe.STEEL,
            0.02, 0.25, 0.25, 0.25, 0.05, 0, 0, 0.6, 0.05, 0, 140000);

    /**
     * The crane's counterweights (CKE900G: 31.9 t on the upper and 14.4 t on the car body): the house's own 8 t under its
     * hood and the rest hung behind the hood, across the house (its tail about 4.4 m from the slewing centre; the
     * CKE900G's 4.5 m).
     */
    static final double CRANE_COUNTERWEIGHT_KG = 31900, CRANE_HOUSE_COUNTERWEIGHT_KG = 8000, CARBODY_WEIGHT_KG = 14400;

    /**
     * A crawler crane's base: the crane crawlers with a travel motor at each sprocket, the car body weight between them,
     * the slewing ring, the house with its power unit (the swing and travel pumps), and the counterweight behind the
     * hood. The boom (its foot at BOOM_FOOT on the house) is the caller's.
     */
    static void craneBase(List<Part> p) {
        p.add(new Part("tracks", CRANE_TRACKS, Mount.ORIGIN));
        double sprocketX = -0.5 * CRANE_TRACKS.contactLengthM() - 0.08, side = 0.5 * CRANE_TRACKS.gaugeM() - 0.1;
        p.add(new Part("travel_left", SampleMachinery.travelMotor(), at(sprocketX, 0, -side)));
        p.add(new Part("travel_right", SampleMachinery.travelMotor(), at(sprocketX, 0, side)));
        p.add(new Part("carbody_weight", new BallastDesign("carbody weight", CARBODY_WEIGHT_KG), at(0, 0.3, 0)));
        slew(p, 0.75);
        powerUnit(p, "swing", "upper", CRANE_HOUSE_COUNTERWEIGHT_KG, "swing", "travel");
        // behind the hood (the house's cab at x 0 for a counterweight of 8 t or more), turned across the house: the block
        // is drawn of lead, twice as long as high (PartLooks), so it stands its length across and its height deep
        BallastDesign rear = new BallastDesign("crane counterweight", CRANE_COUNTERWEIGHT_KG - CRANE_HOUSE_COUNTERWEIGHT_KG);
        double depth = Math.cbrt(rear.massKg() / 11340 / 2);
        double hoodRear = -0.5 * SampleMachinery.excavatorCab().lengthM() - 0.03 - SampleMachinery.excavatorHood().lengthM();
        p.add(new Part("rear_counterweight", rear, Mount.on("swing", "upper", new Mount(new Vec3d(hoodRear - 0.03 - 0.5 * depth, HOUSE_HOOD_BOTTOM_M + 0.5 * depth, 0), 90, 0, 0))));
    }

    /** Where a crawler crane's boom foot pin stands on its house (the upper point), m. */
    static final Vec3d BOOM_FOOT = new Vec3d(0.8, 0.8, 0);

    /** A crawler crane: a 100 t crane's crawlers and counterweights (craneBase), a lattice boom luffed on its hoist and a winch. */
    public static VehicleBlueprint crawlerCrane() {
        List<Part> p = new ArrayList<>();
        craneBase(p);
        p.add(new Part("boom", SampleMachinery.latticeBoom(), on("swing", "upper", BOOM_FOOT.x(), BOOM_FOOT.y(), BOOM_FOOT.z())));
        p.add(new Part("winch", SampleMachinery.hoistWinch(), on("swing", "upper", -1.2, 0.6, 0)));
        return wired("test crawler crane", p);
    }

    /** A dragline: the crawler crane with its bucket hung from the boom tip on a free hitch. */
    public static VehicleBlueprint dragline() {
        List<Part> p = new ArrayList<>();
        craneBase(p);
        p.add(new Part("boom", SampleMachinery.latticeBoom(), on("swing", "upper", BOOM_FOOT.x(), BOOM_FOOT.y(), BOOM_FOOT.z())));
        p.add(new Part("bucket", SampleTools.draglineBucket(), on("boom", "tip")));
        return wired("test dragline", p);
    }

    /** A telehandler: two axles, a boom with a telescopic section and pallet forks. */
    public static VehicleBlueprint telehandler() {
        List<Part> p = new ArrayList<>();
        wheeled(p, 1.4, -1.4, true);
        p.add(new Part("boom", SampleMachinery.craneBoomSection(), at(-1.8, 1.0, 0.4)));
        p.add(new Part("telescope", SampleMachinery.craneBoomSection(), on("boom", "nest")));
        p.add(new Part("forks", SampleTools.palletForks(), on("telescope", "tip")));
        return wired("test telehandler", p);
    }

    /** A forklift: two axles, a mast stage and the forks on it. */
    public static VehicleBlueprint forklift() {
        List<Part> p = new ArrayList<>();
        wheeled(p, 0.8, -0.8, false);
        p.add(new Part("counterweight", new BallastDesign("counterweight", 2000), at(-1.4, 0.5, 0)));
        p.add(new Part("mast", SampleMachinery.mastStage(), at(1.3, 0.2, 0)));
        p.add(new Part("forks", SampleTools.palletForks(), on("mast", "tip", 0.1, -1.5, 0)));
        return wired("test forklift", p);
    }

    /** An aerial work platform: a slewing ring on two axles, the platform boom and the basket. */
    public static VehicleBlueprint workPlatform() {
        List<Part> p = new ArrayList<>();
        wheeled(p, 1.2, -1.2, true);
        p.add(new Part("swing_pump", pump("swing pump", "swing", 30, 250, 20), at(-1.3, 0.7, -0.4)));
        p.add(new Part("aux_pump", pump("platform pump", "auxiliary", 30, 250, 20), at(-1.3, 0.7, 0.0)));
        p.add(new Part("swing", SampleMachinery.swingRing(), at(0, 0.8, 0)));
        p.add(new Part("boom", SampleMachinery.platformBoom(), on("swing", "upper", 0, 0.3, 0)));
        p.add(new Part("basket", SampleTools.workPlatformBasket(), on("boom", "tip")));
        return wired("test work platform", p);
    }

    // ---------------------------------------------------------------- other construction machines

    /** A pile driver: a crawler with a mast and the 5 t pile hammer hung in it. */
    public static VehicleBlueprint pileDriver() {
        List<Part> p = new ArrayList<>();
        crawler(p, SampleMachinery.excavatorTracks20t());
        powerUnit(p, null, null, 3000, "travel");
        p.add(new Part("mast", SampleMachinery.mastStage(), at(2.2, 0.8, 0)));
        p.add(new Part("hammer", SampleTools.pileHammer5t(), on("mast", "tip")));
        return wired("test pile driver", p);
    }

    /** A drill rig: a crawler with a mast and the auger. */
    public static VehicleBlueprint drillRig() {
        List<Part> p = new ArrayList<>();
        crawler(p, SampleMachinery.excavatorTracks20t());
        powerUnit(p, null, null, 3000, "travel", "auxiliary");
        p.add(new Part("mast", SampleMachinery.mastStage(), at(2.2, 0.8, 0)));
        p.add(new Part("auger", SampleTools.drillAuger(), on("mast", "tip")));
        p.add(new Part("auger_motor", cutterMotor(), on("mast", "tip", 0, 0.3, 0)));
        return wired("test drill rig", p);
    }

    /** A chain trencher: a crawler with the trencher's boom behind. */
    public static VehicleBlueprint trencher() {
        List<Part> p = new ArrayList<>();
        crawler(p, SampleMachinery.dozerTracksD6());
        powerUnit(p, null, null, 0, "travel", "auxiliary");
        p.add(new Part("chain", SampleTools.trencherChain(), at(-2.8, 0.6, 0)));
        p.add(new Part("chain_motor", cutterMotor(), at(-2.6, 0.9, 0.5)));
        return wired("test trencher", p);
    }

    /** A concrete mixer truck: the test truck with the 9 m^3 drum. */
    public static VehicleBlueprint mixerTruck() {
        List<Part> p = truck();
        p.add(truckPart("mixer", SampleTools.mixerDrum9m3(), -1.0, 1.0, 0));
        p.add(truckPart("pump", SampleMachinery.excavatorPump(), 2.5, -0.3, 0.5));
        p.add(truckPart("aux_pump", pump("drum pump", "auxiliary", 45, 280, 30), 2.5, -0.3, -0.5));
        p.add(truckPart("drum_motor", cutterMotor(), -3.5, 0.6, 0));
        return wired("test mixer truck", p);
    }

    /** A concrete pump truck: the test truck with a slewing ring, two placing boom sections and outriggers. */
    public static VehicleBlueprint pumpTruck() {
        List<Part> p = truck();
        p.add(truckPart("swing", SampleMachinery.swingRing(), 0.5, 0.1, 0));
        p.add(truckPart("pump", SampleMachinery.excavatorPump(), 2.5, -0.3, 0.5));
        p.add(truckPart("swing_pump", pump("swing pump", "swing", 45, 280, 30), 2.5, -0.3, -0.5));
        p.add(new Part("boom", SampleMachinery.placingBoom(), on("swing", "upper", 0, 0.5, 0)));
        p.add(new Part("boom_2", SampleMachinery.placingBoom(), on("boom", "tip")));
        p.add(truckPart("hopper", SampleTools.paverHopper(), -3.5, 0.2, 0));
        int i = 1;
        for (double x : new double[]{1.5, -3.5}) for (double z : new double[]{-1.2, 1.2}) { p.add(truckPart("outrigger_" + i++, SampleMachinery.outrigger(), x, -0.4, z)); }
        return wired("test pump truck", p);
    }

    /** A water truck: the dump truck's carrier with the spray bar at its rear. */
    public static VehicleBlueprint waterTruck() {
        List<Part> p = truck();
        p.add(truckPart("tank_body", SampleTools.dumpTruckBody10m3(), -2.6, 0.1, 0));
        p.add(truckPart("spray", SampleTools.waterSprayBar(), -3.4, -0.5, 0));
        return wired("test water truck", p);
    }

    /** A street sweeper: the test truck with the broom under it and its motor. */
    public static VehicleBlueprint sweeper() {
        List<Part> p = truck();
        p.add(truckPart("broom", SampleTools.broom(), -0.5, -0.8, 0));
        p.add(truckPart("aux_pump", pump("broom pump", "auxiliary", 30, 250, 20), 2.5, -0.3, -0.5));
        p.add(truckPart("broom_motor", cutterMotor(), -0.5, -0.3, 0.6));
        return wired("test sweeper", p);
    }

    /** A snow blower truck: the snow blower on the truck's front, its motor. */
    public static VehicleBlueprint snowBlower() {
        List<Part> p = truck();
        p.add(truckPart("blower", SampleTools.snowBlower(), 6.0, -0.6, 0));
        p.add(truckPart("aux_pump", pump("blower pump", "auxiliary", 45, 280, 30), 2.5, -0.3, -0.5));
        p.add(truckPart("blower_motor", cutterMotor(), 5.5, -0.2, 0.6));
        return wired("test snow blower", p);
    }

    // ---------------------------------------------------------------- mining and tunnels

    /** A bucket wheel excavator (the 10 m wheel on a boom over a crawler: a scaled rig) with its conveyor. */
    public static VehicleBlueprint bucketWheelExcavator() {
        List<Part> p = new ArrayList<>();
        crawler(p, SampleMachinery.excavatorTracks20t());
        slew(p, 0.75);
        powerUnit(p, "swing", "upper", 8000, "swing", "travel", "auxiliary");
        p.add(new Part("boom", SampleMachinery.latticeBoom(), on("swing", "upper", 0.8, 0.8, 0)));
        p.add(new Part("wheel", SampleTools.bucketWheel10m(), on("boom", "tip")));
        p.add(new Part("wheel_motor", cutterMotor(), on("boom", "tip", -0.5, 0.5, 0)));
        p.add(new Part("conveyor", SampleTools.conveyor1m(), on("swing", "upper", 0.5, 0.5, 0)));
        return wired("test bucket wheel excavator", p);
    }

    /** A roadheader: a crawler with its cutting boom (the excavator's boom) and the transverse head, its loading conveyor. */
    public static VehicleBlueprint roadheader() {
        List<Part> p = new ArrayList<>();
        crawler(p, SampleMachinery.excavatorTracks20t());
        powerUnit(p, null, null, 0, "travel", "auxiliary");
        p.add(new Part("boom", SampleMachinery.excavatorBoom(), at(1.5, 0.8, 0)));
        p.add(new Part("head", SampleTools.roadheaderHead(), on("boom", "tip")));
        p.add(new Part("head_motor", cutterMotor(), on("boom", "tip", -0.4, 0.3, 0)));
        p.add(new Part("conveyor", SampleTools.conveyor1m(), at(-1.0, 0.3, 0)));
        return wired("test roadheader", p);
    }

    /** A tunnel boring machine (the 6 m cutterhead on a crawler shield: a test rig), its drive and the segment erector. */
    public static VehicleBlueprint tunnelBoringMachine() {
        List<Part> p = new ArrayList<>();
        crawler(p, SampleMachinery.excavatorTracks20t());
        powerUnit(p, null, null, 0, "travel", "auxiliary");
        p.add(new Part("cutterhead", SampleTools.tbmCutterhead6m(), at(4.0, 2.4, 0)));
        p.add(new Part("head_motor", cutterMotor(), at(3.2, 2.4, 0)));
        p.add(new Part("erector", SampleTools.segmentErector(), at(0.5, 1.5, 0)));
        p.add(new Part("conveyor", SampleTools.conveyor1m(), at(1.5, 0.8, 0)));
        return wired("test tunnel boring machine", p);
    }

    /** A plate compactor on the excavator's stick. */
    public static VehicleBlueprint compactorExcavator() { return excavatorWith("test compactor excavator", SampleTools.plateCompactor()); }

    /** A clamshell on the excavator. */
    public static VehicleBlueprint clamshellExcavator() { return excavatorWith("test clamshell excavator", SampleTools.clamshell()); }

    /** A snow plow on the dozer's push frame. */
    public static VehicleBlueprint snowPlow() {
        List<Part> p = new ArrayList<>();
        crawler(p, SampleMachinery.dozerTracksD6());
        powerUnit(p, null, null, 0, "travel");
        p.add(new Part("push_frame", SampleMachinery.loaderArm(), at(0.8, 0.3, 0)));
        p.add(new Part("plow", SampleTools.snowPlow(), on("push_frame", "tip")));
        return wired("test snow plow", p);
    }

    // ---------------------------------------------------------------- by name

    /** The test machines by name (/hdfv dev drive spawn). */
    public static final Map<String, Supplier<VehicleBlueprint>> ALL = all();

    private static Map<String, Supplier<VehicleBlueprint>> all() {
        Map<String, Supplier<VehicleBlueprint>> m = new LinkedHashMap<>();
        m.put("excavator", TestMachines::excavator);
        m.put("miniexcavator", TestMachines::miniExcavator);
        m.put("hammer", TestMachines::hammerExcavator);
        m.put("grapple", TestMachines::grappleExcavator);
        m.put("clamshell", TestMachines::clamshellExcavator);
        m.put("compactor", TestMachines::compactorExcavator);
        m.put("miningshovel", TestMachines::miningShovel);
        m.put("dozer", TestMachines::dozer);
        m.put("crawlertractor", TestMachines::crawlerTractor);
        m.put("snowplow", TestMachines::snowPlow);
        m.put("wheelloader", TestMachines::wheelLoader);
        m.put("backhoe", TestMachines::backhoeLoader);
        m.put("grader", TestMachines::grader);
        m.put("scraper", TestMachines::scraper);
        m.put("dumptruck", TestMachines::dumpTruck);
        m.put("haultruck", TestMachines::haulTruck);
        m.put("articulatedtruck", TestMachines::articulatedTruck);
        m.put("roller", TestMachines::roller);
        m.put("padfoot", TestMachines::padfootRoller);
        m.put("tandemroller", TestMachines::tandemRoller);
        m.put("paver", TestMachines::paver);
        m.put("coldplaner", TestMachines::coldPlaner);
        m.put("truckcrane", TestMachines::truckCrane);
        m.put("crawlercrane", TestMachines::crawlerCrane);
        m.put("dragline", TestMachines::dragline);
        m.put("telehandler", TestMachines::telehandler);
        m.put("forklift", TestMachines::forklift);
        m.put("workplatform", TestMachines::workPlatform);
        m.put("piledriver", TestMachines::pileDriver);
        m.put("drillrig", TestMachines::drillRig);
        m.put("trencher", TestMachines::trencher);
        m.put("mixertruck", TestMachines::mixerTruck);
        m.put("pumptruck", TestMachines::pumpTruck);
        m.put("watertruck", TestMachines::waterTruck);
        m.put("sweeper", TestMachines::sweeper);
        m.put("snowblower", TestMachines::snowBlower);
        m.put("bucketwheel", TestMachines::bucketWheelExcavator);
        m.put("roadheader", TestMachines::roadheader);
        m.put("tbm", TestMachines::tunnelBoringMachine);
        return java.util.Collections.unmodifiableMap(m);
    }
}
