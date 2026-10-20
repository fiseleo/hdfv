package dev.mcmod.hdfv.part;

import dev.mcmod.aerocore.api.HullModel;
import dev.mcmod.aerocore.api.TestHull;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.vehicle.Binding;
import dev.mcmod.aerocore.api.vehicle.Connection;
import dev.mcmod.aerocore.api.vehicle.MassSpec;
import dev.mcmod.aerocore.api.vehicle.Material;
import dev.mcmod.aerocore.api.vehicle.Mount;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.VehicleBlueprint;
import dev.mcmod.hdfv.part.air.SampleAirframes;
import dev.mcmod.hdfv.part.engine.SampleEngines;

import java.util.List;
import java.util.Map;

/**
 * Example blueprints built from AeroCore's own parts, on the scale of its test bodies: starting points to copy and the
 * vehicles commands and tests spawn. Channels: "throttle" (-1..1) and "rudder" (-1..1, positive turns to starboard);
 * the aircraft also use hdfv's flight keys: "power" and "collective" (levers, 0..1), "pitch" (+1 nose up), "roll" (+1
 * to starboard), "flap" (0..1).
 * They are examples of the format and the physics, not designs a vehicle mod should ship.
 */
public final class SampleBlueprints {
    private SampleBlueprints() {}

    /** Name to blueprint, for /aerocore vehicle samples. */
    public static Map<String, VehicleBlueprint> all() {
        return Map.ofEntries(Map.entry("platform", platform()), Map.entry("launch", launch()), Map.entry("cruiser", cruiser()), Map.entry("engineboat", engineBoat()),
                Map.entry("jetboat", jetBoat()), Map.entry("fanboat", fanBoat()), Map.entry("turbineboat", turbineBoat()), Map.entry("seaplane", seaplane()),
                Map.entry("helicopter", helicopter()), Map.entry("pulsejetboat", pulsejetBoat()), Map.entry("boosterstand", boosterStand()),
                Map.entry("jetstand", jetStand()), Map.entry("sedan", sedan()));
    }

    /**
     * 0.12: the assembly room's sample sedan (SampleLayouts: the sedan body, the 2.0 l four and six-speed automatic,
     * the family car's axles, two seats) wired as a room wires it, so the world has a car to look at: its wheels,
     * suspension, lamps and the marks its tyres leave. Driving it is 0.6's H4-H6.
     */
    public static VehicleBlueprint sedan() { return dev.mcmod.hdfv.assembly.AutoWiring.wire(dev.mcmod.hdfv.assembly.SampleLayouts.sedan()); }

    /**
     * The test platform's layout as parts: four 0.45 m steel floats at the corners of a 1.7 m square, an open 300 kg
     * frame, 200 kg of ballast, a 3 kW motor turning a 0.3 m B3-65 screw at the stern and a rudder in its race.
     */
    public static VehicleBlueprint platform() {
        PartDesign float1 = new FloatDesign("steel float", 0.45, MassSpec.shell(Material.STEEL, 0.004));
        return new VehicleBlueprint("platform", List.of(
                new VehicleBlueprint.Entry("float_fl", float1, Mount.at(new Vec3d(0.85, 0, -0.85))),
                new VehicleBlueprint.Entry("float_fr", float1, Mount.at(new Vec3d(0.85, 0, 0.85))),
                new VehicleBlueprint.Entry("float_al", float1, Mount.at(new Vec3d(-0.85, 0, -0.85))),
                new VehicleBlueprint.Entry("float_ar", float1, Mount.at(new Vec3d(-0.85, 0, 0.85))),
                new VehicleBlueprint.Entry("frame", new BoxDesign("frame", new Vec3d(2.2, 0.1, 2.2), 1.17, false, 2, MassSpec.given(300)), Mount.at(new Vec3d(0, 0.5, 0))),
                new VehicleBlueprint.Entry("ballast", new BallastDesign("ballast", 200), Mount.at(new Vec3d(0, -0.35, 0))),
                new VehicleBlueprint.Entry("motor", new MotorDesign("3 kW motor", 40, 3000, 3000, 0.004, 0.9, 15), Mount.at(new Vec3d(-1.1, 0.3, 0)),
                        List.of(Binding.of("throttle", "throttle"))),
                new VehicleBlueprint.Entry("screw", new PropellerDesign("B3-65", 0.3, 3, 0.65, 1.0, 0.18, 0.1, MassSpec.solid(Material.BRONZE)),
                        Mount.at(new Vec3d(-1.4, -0.4, 0))),
                new VehicleBlueprint.Entry("rudder", new ControlSurfaceDesign("rudder", 0.25, 0.35, 35, true, 0.15, MassSpec.solid(Material.STEEL)),
                        Mount.at(new Vec3d(-1.65, -0.4, 0)), List.of(Binding.of("deflection", "rudder"))),
                new VehicleBlueprint.Entry("seat", new SeatDesign("seat", 5), Mount.at(new Vec3d(0, 0.55, 0)))),
                List.of(new Connection.Shaft("motor", "screw", 1, 0.97), new Connection.Slipstream("screw", "rudder")));
    }

    /**
     * A 6 m planing launch on Fridsma's hull (B 1.2 m, 20 deg deadrise), about the test planing boat's 1048 kg: a 60 kW
     * motor through a 1.5 reduction to a 0.35 m B4-70 P/D 1.4 screw under the transom, a spade rudder in its race.
     */
    public static VehicleBlueprint launch() {
        return new VehicleBlueprint("launch", List.of(
                new VehicleBlueprint.Entry("hull", new PlaningHullDesign("fridsma hull", 6, 1.2, 20, 0.75, 48, 6, MassSpec.given(880)), Mount.ORIGIN),
                new VehicleBlueprint.Entry("motor", new MotorDesign("60 kW motor", 250, 60000, 4000, 0.05, 0.94, 60), Mount.at(new Vec3d(1.2, 0.25, 0)),
                        List.of(Binding.of("throttle", "throttle"))),
                new VehicleBlueprint.Entry("screw", new PropellerDesign("B4-70 P/D 1.4", 0.35, 4, 0.70, 1.4, 0.18, 0.1, MassSpec.solid(Material.BRONZE)),
                        Mount.at(new Vec3d(-0.25, -0.2, 0))),
                new VehicleBlueprint.Entry("rudder", new ControlSurfaceDesign("rudder", 0.22, 0.32, 35, true, 0.15, MassSpec.solid(Material.STEEL)),
                        Mount.at(new Vec3d(-0.5, -0.22, 0)), List.of(Binding.of("deflection", "rudder"))),
                new VehicleBlueprint.Entry("ballast", new BallastDesign("fittings", 80), Mount.at(new Vec3d(2.2, 0.3, 0))),
                new VehicleBlueprint.Entry("seat", new SeatDesign("helm", 10), Mount.at(new Vec3d(2.0, 0.35, 0)))),
                List.of(new Connection.Shaft("motor", "screw", 1.5, 0.96), new Connection.Slipstream("screw", "rudder")));
    }

    /**
     * The 6 m test hull as a displacement launch of 1200 kg: a 10 kW motor through a 2:1 reduction to a 0.4 m B3-65
     * screw, a rudder behind it.
     */
    public static VehicleBlueprint cruiser() {
        HullDesign hull = new HullDesign("test hull", HullModel.Type.SURFACE, HullDesign.stationsOf(TestHull.HULL), TestHull.MASS, 0, 6,
                new MassSpec.Given(1050, TestHull.CENTER_OF_MASS));
        return new VehicleBlueprint("cruiser", List.of(
                new VehicleBlueprint.Entry("hull", hull, Mount.ORIGIN),
                new VehicleBlueprint.Entry("motor", new MotorDesign("10 kW motor", 60, 10000, 3000, 0.01, 0.92, 30), Mount.at(new Vec3d(-2.2, -0.1, 0)),
                        List.of(Binding.of("throttle", "throttle"))),
                new VehicleBlueprint.Entry("screw", new PropellerDesign("B3-65", 0.4, 3, 0.65, 1.0, 0.18, 0.1, MassSpec.solid(Material.BRONZE)),
                        Mount.at(new Vec3d(-3.15, -0.38, 0))),
                new VehicleBlueprint.Entry("rudder", new ControlSurfaceDesign("rudder", 0.30, 0.50, 35, true, 0.15, MassSpec.solid(Material.STEEL)),
                        Mount.at(new Vec3d(-3.45, -0.42, 0)), List.of(Binding.of("deflection", "rudder"))),
                new VehicleBlueprint.Entry("ballast", new BallastDesign("keel ballast", 100), Mount.at(new Vec3d(0, -0.3, 0))),
                new VehicleBlueprint.Entry("seat", new SeatDesign("helm", 10), Mount.at(new Vec3d(-1.5, 0.45, 0)))),
                List.of(new Connection.Shaft("motor", "screw", 2, 0.97), new Connection.Slipstream("screw", "rudder")));
    }

    /**
     * The cruiser's hull with the sample 2.0 l petrol engine (SampleEngines.inlineFour) in place of the motor, through a
     * 3:1 reduction to the same screw; the ignition is not bound, so the engine starts by itself. The engine stands on
     * the after deck (0.11: inside the hull its head and intake stood out through the deck, and the round bilge is too
     * narrow to hold it), the helm ahead of it.
     */
    public static VehicleBlueprint engineBoat() {
        HullDesign hull = new HullDesign("test hull", HullModel.Type.SURFACE, HullDesign.stationsOf(TestHull.HULL), TestHull.MASS, 0, 6,
                new MassSpec.Given(1000, TestHull.CENTER_OF_MASS));
        return new VehicleBlueprint("engine boat", List.of(
                new VehicleBlueprint.Entry("hull", hull, Mount.ORIGIN),
                new VehicleBlueprint.Entry("engine", SampleEngines.inlineFour(), Mount.at(new Vec3d(-1.5, 0.76, 0)), List.of(Binding.of("throttle", "throttle"))),
                new VehicleBlueprint.Entry("screw", new PropellerDesign("B3-65", 0.4, 3, 0.65, 1.0, 0.18, 0.1, MassSpec.solid(Material.BRONZE)),
                        Mount.at(new Vec3d(-3.15, -0.38, 0))),
                new VehicleBlueprint.Entry("rudder", new ControlSurfaceDesign("rudder", 0.30, 0.50, 35, true, 0.15, MassSpec.solid(Material.STEEL)),
                        Mount.at(new Vec3d(-3.45, -0.42, 0)), List.of(Binding.of("deflection", "rudder"))),
                new VehicleBlueprint.Entry("ballast", new BallastDesign("keel ballast", 60), Mount.at(new Vec3d(0, -0.3, 0))),
                new VehicleBlueprint.Entry("tank", FuelTankDesign.car("gasoline"), Mount.at(new Vec3d(-0.4, -0.1, 0))),
                new VehicleBlueprint.Entry("seat", new SeatDesign("helm", 10), Mount.at(new Vec3d(-0.6, 0.45, 0)))),
                List.of(new Connection.Shaft("engine", "screw", 3, 0.97), new Connection.Slipstream("screw", "rudder")));
    }

    /**
     * The test hull pushed by the J85-class turbojet of SampleEngines on its after deck (inlet clear of the water): even
     * at idle (about 1.6 kN) it drives the boat; a rudder steers.
     */
    public static VehicleBlueprint jetBoat() {
        HullDesign hull = new HullDesign("test hull", HullModel.Type.SURFACE, HullDesign.stationsOf(TestHull.HULL), TestHull.MASS, 0, 6,
                new MassSpec.Given(1000, TestHull.CENTER_OF_MASS));
        return new VehicleBlueprint("jet boat", List.of(
                new VehicleBlueprint.Entry("hull", hull, Mount.ORIGIN),
                new VehicleBlueprint.Entry("jet", SampleEngines.turbojet(), Mount.at(new Vec3d(-1.6, 0.75, 0)),
                        List.of(Binding.of("throttle", "throttle"))),   // ignition unbound: always on
                new VehicleBlueprint.Entry("rudder", new ControlSurfaceDesign("rudder", 0.30, 0.50, 35, true, 0.15, MassSpec.solid(Material.STEEL)),
                        Mount.at(new Vec3d(-3.45, -0.42, 0)), List.of(Binding.of("deflection", "rudder"))),
                new VehicleBlueprint.Entry("ballast", new BallastDesign("keel ballast", 120),Mount.at(new Vec3d(0, -0.3, 0))),
                new VehicleBlueprint.Entry("tank", FuelTankDesign.car("kerosene"), Mount.at(new Vec3d(-0.4, -0.1, 0))),
                new VehicleBlueprint.Entry("seat", new SeatDesign("helm", 10), Mount.at(new Vec3d(-0.5, 0.45, 0)))),
                List.of());
    }

    /**
     * A booster test stand (0.10): a 9 t block on the ground with the APCP booster and the KNSB motor of SampleJets on
     * top, both firing aft ("fire", the X key); held by its weight. The APCP booster is cast off when burnt out and drops
     * as a part; the KNSB motor stays to be loaded again with grains.
     */
    public static VehicleBlueprint boosterStand() {
        return new VehicleBlueprint("booster stand", List.of(
                new VehicleBlueprint.Entry("stand", new BoxDesign("stand", new Vec3d(1.6, 0.6, 1.0), 1.05, true, 2, MassSpec.given(9000)), Mount.ORIGIN),
                new VehicleBlueprint.Entry("apcp", dev.mcmod.hdfv.part.engine.SampleJets.apcpBooster(), Mount.at(new Vec3d(0, 0.39, -0.25)),
                        List.of(Binding.of("fire", "fire"))),
                new VehicleBlueprint.Entry("knsb", dev.mcmod.hdfv.part.engine.SampleJets.knsbMotor(), Mount.at(new Vec3d(0, 0.37, 0.25)),
                        List.of(Binding.of("fire", "fire")))),
                List.of());
    }

    /**
     * A jet test stand (0.11, for seeing a jet's plume and afterburner in the world): the J79 of SampleJets on a 150 t
     * block (heavy enough that the afterburner's 80 kN does not slide it), a 900 l kerosene tank; throttle and reheat
     * (the B key) bound, ignition always on. It starts as the real engine does, in about forty seconds.
     */
    public static VehicleBlueprint jetStand() {
        return new VehicleBlueprint("jet stand", List.of(
                new VehicleBlueprint.Entry("stand", new BoxDesign("stand", new Vec3d(4.0, 0.8, 1.4), 1.05, true, 2, MassSpec.given(150_000)), Mount.ORIGIN),
                new VehicleBlueprint.Entry("jet", dev.mcmod.hdfv.part.engine.SampleJets.j79(), Mount.at(new Vec3d(0, 1.05, 0)),
                        List.of(Binding.of("throttle", "throttle"), Binding.of("reheat", "reheat"))),
                new VehicleBlueprint.Entry("tank", new FuelTankDesign("stand tank", 1.5, 1.0, 0.6, 0.002, dev.mcmod.hdfv.part.engine.EngineMaterial.STEEL_1045, "kerosene", 1),
                        Mount.at(new Vec3d(0, 0.1, 0)))),
                List.of());
    }

    /**
     * The test hull pushed by an Argus As 014 pulsejet (0.10; the user asked for a pulsejet boat in the world) on its
     * after deck: standing, it must be blown to start ("start", the starting-air key), then it buzzes on by itself
     * (about 2.2 kN) and drives the boat; past 60 m/s the ram air would start it. Gasoline in a car's tank, a rudder in
     * the water.
     */
    public static VehicleBlueprint pulsejetBoat() {
        HullDesign hull = new HullDesign("test hull", HullModel.Type.SURFACE, HullDesign.stationsOf(TestHull.HULL), TestHull.MASS, 0, 6,
                new MassSpec.Given(1000, TestHull.CENTER_OF_MASS));
        return new VehicleBlueprint("pulsejet boat", List.of(
                new VehicleBlueprint.Entry("hull", hull, Mount.ORIGIN),
                new VehicleBlueprint.Entry("pulsejet", dev.mcmod.hdfv.part.engine.SampleJets.argus(), Mount.at(new Vec3d(-1.2, 0.9, 0)),
                        List.of(Binding.of("throttle", "throttle"), Binding.of("start", "start"))),   // ignition unbound: always on
                new VehicleBlueprint.Entry("rudder", new ControlSurfaceDesign("rudder", 0.30, 0.50, 35, true, 0.15, MassSpec.solid(Material.STEEL)),
                        Mount.at(new Vec3d(-3.45, -0.42, 0)), List.of(Binding.of("deflection", "rudder"))),
                new VehicleBlueprint.Entry("ballast", new BallastDesign("keel ballast", 120), Mount.at(new Vec3d(0, -0.3, 0))),
                new VehicleBlueprint.Entry("tank", FuelTankDesign.car("gasoline"), Mount.at(new Vec3d(-0.4, -0.1, 0))),
                new VehicleBlueprint.Entry("seat", new SeatDesign("helm", 10), Mount.at(new Vec3d(0.4, 0.45, 0)))),
                List.of());
    }

    /**
     * The test hull pushed by the business-jet turbofan of SampleEngines (8.8 kN, mixed exhaust) on its after deck: at
     * idle (about 0.5 kN) it moves the boat; a rudder steers.
     */
    public static VehicleBlueprint fanBoat() {
        HullDesign hull = new HullDesign("test hull", HullModel.Type.SURFACE, HullDesign.stationsOf(TestHull.HULL), TestHull.MASS, 0, 6,
                new MassSpec.Given(1000, TestHull.CENTER_OF_MASS));
        return new VehicleBlueprint("fan boat", List.of(
                new VehicleBlueprint.Entry("hull", hull, Mount.ORIGIN),
                new VehicleBlueprint.Entry("fan", SampleEngines.businessTurbofan(), Mount.at(new Vec3d(-1.6, 0.8, 0)),
                        List.of(Binding.of("throttle", "throttle"))),   // ignition unbound: always on
                new VehicleBlueprint.Entry("rudder", new ControlSurfaceDesign("rudder", 0.30, 0.50, 35, true, 0.15, MassSpec.solid(Material.STEEL)),
                        Mount.at(new Vec3d(-3.45, -0.42, 0)), List.of(Binding.of("deflection", "rudder"))),
                new VehicleBlueprint.Entry("ballast", new BallastDesign("keel ballast", 120), Mount.at(new Vec3d(0, -0.3, 0))),
                new VehicleBlueprint.Entry("tank", FuelTankDesign.car("kerosene"), Mount.at(new Vec3d(-0.4, -0.1, 0))),
                new VehicleBlueprint.Entry("seat", new SeatDesign("helm", 10), Mount.at(new Vec3d(-0.5, 0.45, 0)))),
                List.of());
    }

    /**
     * The test hull with the turboshaft of SampleEngines (T700 class) turning the 0.4 m screw through a 14:1 reduction
     * (its 20,900 rpm output to 1,490 rpm): throttle caps its governor's demand, so at none it idles and the screw turns
     * slowly.
     */
    public static VehicleBlueprint turbineBoat() {
        HullDesign hull = new HullDesign("test hull", HullModel.Type.SURFACE, HullDesign.stationsOf(TestHull.HULL), TestHull.MASS, 0, 6,
                new MassSpec.Given(1000, TestHull.CENTER_OF_MASS));
        return new VehicleBlueprint("turbine boat", List.of(
                new VehicleBlueprint.Entry("hull", hull, Mount.ORIGIN),
                new VehicleBlueprint.Entry("engine", SampleEngines.turboshaft(), Mount.at(new Vec3d(-1.6, 0.1, 0)), List.of(Binding.of("throttle", "throttle"))),
                new VehicleBlueprint.Entry("screw", new PropellerDesign("B3-65", 0.4, 3, 0.65, 1.0, 0.18, 0.1, MassSpec.solid(Material.BRONZE)),
                        Mount.at(new Vec3d(-3.15, -0.38, 0))),
                new VehicleBlueprint.Entry("rudder", new ControlSurfaceDesign("rudder", 0.30, 0.50, 35, true, 0.15, MassSpec.solid(Material.STEEL)),
                        Mount.at(new Vec3d(-3.45, -0.42, 0)), List.of(Binding.of("deflection", "rudder"))),
                new VehicleBlueprint.Entry("ballast", new BallastDesign("keel ballast", 120), Mount.at(new Vec3d(0, -0.3, 0))),
                new VehicleBlueprint.Entry("tank", FuelTankDesign.car("kerosene"), Mount.at(new Vec3d(-0.4, -0.1, 0))),
                new VehicleBlueprint.Entry("seat", new SeatDesign("helm", 10), Mount.at(new Vec3d(-1.0, 0.45, 0)))),
                List.of(new Connection.Shaft("engine", "screw", 14, 0.97), new Connection.Slipstream("screw", "rudder")));
    }

    /** Where the seaplane's floats sit: the step this far forward of the seaplane's origin, 1.5 m either side. */
    public static final double FLOAT_X = 2.2;

    /**
     * A single-turboprop float seaplane of about 2.6 t (DHC-3T Turbo Otter class on floats), built from bodies of modules:
     * two stepped 7.6 m floats (SampleBodies.seaplaneFloat) 3 m apart, their steps 0.38 m behind the centre of gravity (12
     * degrees aft of the vertical from it to the keels); the 12.4 m fuselage (SampleBodies.seaplaneFuselage) 1.15 m above the
     * keels, with its pilots, passengers and cargo; on its mount points the Turbo Otter-class wing (on the cockpit's roof,
     * 1.6 m back: the centre of gravity at about 30 % of its mean chord), the PT6A-class turboprop in the nose turning the
     * 2.59 m constant-speed propeller at the nose's tip, the tailplane and the fin at the tail, and a water rudder under
     * each float's stern (0.25 by 0.35 m, as on amphibious floats of this size). Power lever "power", flaps "flap", elevator
     * "pitch" (+1 nose up), ailerons "roll", rudder "rudder" (the fin's rudder and the water rudders together).
     */
    public static VehicleBlueprint seaplane() {
        var floatBody = dev.mcmod.hdfv.part.body.SampleBodies.seaplaneFloat();
        double step = floatBody.shape().moduleRearX(1);   // the step: where the forebody ends
        return new VehicleBlueprint("seaplane", List.of(
                new VehicleBlueprint.Entry("float_port", floatBody, Mount.at(new Vec3d(FLOAT_X - step, 0, -1.5))),
                new VehicleBlueprint.Entry("float_starboard", floatBody, Mount.at(new Vec3d(FLOAT_X - step, 0, 1.5))),
                new VehicleBlueprint.Entry("fuselage", dev.mcmod.hdfv.part.body.SampleBodies.seaplaneFuselage(), Mount.at(new Vec3d(-5.1, 1.15, 0))),
                new VehicleBlueprint.Entry("wing", SampleAirframes.seaplaneWing(), Mount.on("fuselage", "wing", Mount.at(new Vec3d(-1.6, 0, 0))),
                        List.of(Binding.of("flap", "flap"), Binding.of("aileron", "roll"))),
                new VehicleBlueprint.Entry("engine", SampleEngines.turboprop(), Mount.on("fuselage", "engine", Mount.at(new Vec3d(-1.6, 0, 0))),
                        List.of(Binding.of("throttle", "power"))),
                new VehicleBlueprint.Entry("propeller", SampleAirframes.turbopropPropeller(), Mount.on("fuselage", "engine")),
                new VehicleBlueprint.Entry("tailplane", SampleAirframes.tailplane(), Mount.on("fuselage", "tail", Mount.at(new Vec3d(0.4, 0, 0))),
                        List.of(new Binding("elevator", "pitch", -1, 0))),
                new VehicleBlueprint.Entry("fin", SampleAirframes.fin(), Mount.on("fuselage", "tail", new Mount(new Vec3d(0.6, 0.2, 0), 0, 0, -90)),
                        List.of(Binding.of("rudder", "rudder"))),
                waterRudder("float_port"), waterRudder("float_starboard")),
                List.of(new Connection.Shaft("engine", "propeller", 1, 1)));
    }

    /** A float's water rudder: hanging from its stern below the bottom, its trailing edge to starboard on right rudder. */
    private static VehicleBlueprint.Entry waterRudder(String floatId) {
        return new VehicleBlueprint.Entry(floatId.replace("float", "water_rudder"),
                new ControlSurfaceDesign("water rudder", 0.25, 0.35, 30, true, 0.12, MassSpec.solid(Material.ALUMINIUM)),
                Mount.on(floatId, "rudder", Mount.at(new Vec3d(-0.1, -0.15, 0))), List.of(Binding.of("deflection", "rudder")));
    }

    /**
     * A single-turboshaft helicopter of about 4.4 t built round the UH-60-class fuselage of modules
     * (SampleBodies.helicopterFuselage: cockpit, an eleven-seat cabin under the mast, the aft fuselage with the fuel and
     * cargo, the tail boom): the T700-class sample turboshaft behind the mast turning the UH-60-class main rotor on the
     * mast through an 81:1 gearbox (its 20,900 rpm output to 258 rpm) and a freewheel (the rotor can autorotate), the tail
     * rotor at the end of the boom geared from the main rotor (1,190 rpm), its thrust to starboard against the main rotor's
     * torque; skids. Power lever "power" (the engine's governor demand cap: 1 to fly), collective "collective", cyclic
     * "pitch" (+1 nose up) and "roll", pedals "rudder" (+1 nose right, around a trim of the tail rotor's collective).
     */
    public static VehicleBlueprint helicopter() {
        return new VehicleBlueprint("helicopter", List.of(
                new VehicleBlueprint.Entry("fuselage", dev.mcmod.hdfv.part.body.SampleBodies.helicopterFuselage(), Mount.at(new Vec3d(-9.2, 0.5, 0))),
                new VehicleBlueprint.Entry("skid_port", new BoxDesign("skid", new Vec3d(3.6, 0.12, 0.12), 1.0, false, 1, MassSpec.given(30)),
                        Mount.on("fuselage", "m3_bottom", Mount.at(new Vec3d(0, -0.44, -1.1)))),
                new VehicleBlueprint.Entry("skid_starboard", new BoxDesign("skid", new Vec3d(3.6, 0.12, 0.12), 1.0, false, 1, MassSpec.given(30)),
                        Mount.on("fuselage", "m3_bottom", Mount.at(new Vec3d(0, -0.44, 1.1)))),
                new VehicleBlueprint.Entry("engine", SampleEngines.turboshaft(), Mount.on("fuselage", "mast", Mount.at(new Vec3d(-1.4, 0.0, 0))),
                        List.of(Binding.of("throttle", "power"))),
                new VehicleBlueprint.Entry("rotor", SampleAirframes.mainRotor(), Mount.on("fuselage", "mast", Mount.at(new Vec3d(-0.75, 0.9, 0))),
                        List.of(Binding.of("collective", "collective"), new Binding("cyclicPitch", "pitch", -1, 0), Binding.of("cyclicRoll", "roll"))),
                new VehicleBlueprint.Entry("tail_rotor", SampleAirframes.tailRotor(), Mount.on("fuselage", "tail_rotor", new Mount(new Vec3d(0.2, 0.4, 0.05), 0, 0, 90)),
                        List.of(new Binding("collective", "rudder", -0.35, 0.55)))),
                List.of(new Connection.Shaft("engine", "rotor", 81, 0.97, true), new Connection.Shaft("rotor", "tail_rotor", 258.0 / 1190, 0.98)));
    }

}
