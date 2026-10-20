package dev.mcmod.hdfv.neoforge.vehicle;

import dev.mcmod.aerocore.api.Orientation;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.vehicle.Mount;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.VehicleBlueprint;
import dev.mcmod.aerocore.neoforge.probe.Wakes;
import dev.mcmod.aerocore.neoforge.probe.WorldWaterSampler;
import dev.mcmod.aerocore.neoforge.vehicle.PartVehicle;
import dev.mcmod.aerocore.neoforge.vehicle.VehicleRegistry;
import dev.mcmod.hdfv.part.BallastDesign;
import dev.mcmod.hdfv.part.SampleBlueprints;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;

/**
 * The sample aircraft in the world: the seaplane floats on its floats, starts its turboprop and moves off under power;
 * the helicopter starts its turboshaft, spins its rotor up through the freewheel and lifts off its pad. The full
 * take-off run and hover are tested offline (AirframeTest): the world here is a pool and a pad, not a lake. A body of
 * modules floats on its sealed volume: the seaplane's fuselage floats, and sinks carrying more than its volume of
 * water or with every module open to the sky.
 */
@PrefixGameTestTemplate(false)
public final class AircraftGameTests {
    private AircraftGameTests() {}

    @GameTest(templateNamespace = "hdfv", template = "seaplane_pool", timeoutTicks = 900, batch = "hdfv_seaplane")
    public static void seaplaneFloatsStartsItsTurbopropAndMovesOff(GameTestHelper h) {
        Wakes.synchronous = true;
        double surface = pool(h, 40, 28);
        VehicleBlueprint blueprint = VehicleGameTests.quickStart(SampleBlueprints.seaplane());
        blueprint.compile().warmUp();   // section polars, the propeller's table, the turboprop's running lines
        PartVehicle v = spawn(h, blueprint, 8, 14, surface - 0.3);
        v.setControls(Map.of("power", 0.0, "flap", 0.5));
        final int[] running = {0};
        final Vec3d[] start = {null};
        h.succeedWhen(() -> {
            if (!v.ready()) { throw new GameTestAssertException("Tables still building"); }
            running[0]++;
            var body = v.vehicleState().body();
            Vec3d up = body.orientation().rotate(Vec3d.Y);
            h.assertTrue(up.y() > 0.95, "The floats must keep it upright: " + up);
            Map<String, Double> engine = v.vehicleState().diagnostics().get("engine"), prop = v.vehicleState().diagnostics().get("propeller");
            // idle with the quick start schedule in about 8 s; then a little power, as a pilot taxis. It passes as soon as all
            // holds (offline: 2 m in about a second, the propeller at about 1050 rpm on its fine stop), long before the far wall.
            if (running[0] < 200 || engine.get("lit") != 1) { throw new GameTestAssertException("Starting: " + engine); }
            if (start[0] == null) { start[0] = v.vehicle().origin(body); v.setControl("power", 0.3); }
            Vec3d at = v.vehicle().origin(body), moved = at.subtract(start[0]);
            double pitchDeg = Math.toDegrees(Math.asin(body.orientation().rotate(Vec3d.X).y()));
            String state = " (tick " + running[0] + ", moved " + moved + ", origin " + (at.y() - surface) + " m from the surface, pitch " + pitchDeg + ")";
            // offline the origin (the floats' sterns) rides 0.6 m deep at 3.5 deg nose up
            h.assertTrue(at.y() > surface - 1.0, "The floats must keep it on the water" + state);
            h.assertTrue(prop.get("rps") * 60 > 800 && prop.get("thrustN") > 1000, "The turboprop must turn the propeller and pull: " + prop + state);
            h.assertTrue(moved.x() > 2, "It must move off ahead" + state);
        });
    }

    @GameTest(templateNamespace = "hdfv", template = "seaplane_pool", timeoutTicks = 900, batch = "hdfv_body")
    public static void aBodyFloatsOnItsSealedVolume(GameTestHelper h) {
        Wakes.synchronous = true;
        double surface = pool(h, 40, 28);
        PartDesign sealed = dev.mcmod.hdfv.part.body.SampleBodies.seaplaneFuselage(), open = sealed;
        for (int i = 1; i <= ((dev.mcmod.hdfv.part.body.BodyDesign) sealed).modules().size(); i++) {
            open = dev.mcmod.hdfv.design.Designs.set(open, "module" + i, "open", "true");
            open = dev.mcmod.hdfv.design.Designs.set(open, "module" + i, "sillM", "0");
        }
        // 21 m^3 sealed and 1.4 t: it floats; with 25 t aboard, or open everywhere, it sinks to the pool's floor 4 m down
        VehicleBlueprint sealedOne = alone("sealed", sealed), openOne = alone("open", open),
                heavyOne = new VehicleBlueprint("heavy", List.of(new VehicleBlueprint.Entry("fuselage", sealed, Mount.at(Vec3d.ZERO), List.of()),
                        new VehicleBlueprint.Entry("ballast", new BallastDesign("ballast", 25000), Mount.at(new Vec3d(6.2, 0.6, 0)), List.of())), List.of());
        // the hulls' tables, built here so that the vehicles do not wait for them (the test server ticks faster than real time)
        for (VehicleBlueprint b : List.of(sealedOne, openOne, heavyOne)) { b.compile().warmUp(); }
        PartVehicle floating = spawn(h, sealedOne, 8, 5, surface - 0.3);
        PartVehicle heavy = spawn(h, heavyOne, 8, 14, surface - 0.3);
        PartVehicle flooded = spawn(h, openOne, 8, 23, surface - 0.3);
        final int[] ticks = {0};
        h.succeedWhen(() -> {
            if (!floating.ready() || !heavy.ready() || !flooded.ready()) { throw new GameTestAssertException("Tables still building"); }
            ticks[0]++;
            var f = floating.vehicleState().body();
            double floatingY = f.position().y() - surface, heavyY = heavy.vehicleState().body().position().y() - surface,
                    floodedY = flooded.vehicleState().body().position().y() - surface;
            String state = " (tick " + ticks[0] + ": centres of mass " + floatingY + ", " + heavyY + ", " + floodedY + " m from the surface)";
            if (ticks[0] < 200) { throw new GameTestAssertException("Settling" + state); }
            h.assertTrue(floatingY > -1 && Math.abs(f.velocity().y()) < 0.05, "The sealed fuselage must float" + state);
            h.assertTrue(heavyY < -2, "With 25 t aboard it must sink" + state);
            h.assertTrue(floodedY < -2, "Open everywhere it must sink" + state);
        });
    }

    private static VehicleBlueprint alone(String name, PartDesign body) {
        return new VehicleBlueprint(name, List.of(new VehicleBlueprint.Entry("body", body, Mount.at(Vec3d.ZERO), List.of())), List.of());
    }

    /**
     * 0.12: the new channels (lights, doors, landing gear, wipers, selector) are kept with the vehicle like the others
     * (they reach clients with the controls; the client side is checked by eye).
     */
    @GameTest(templateNamespace = "hdfv", template = "helipad", timeoutTicks = 60, batch = "hdfv_channels")
    public static void lightsDoorsGearChannelsAreKept(GameTestHelper h) {
        for (int x = 0; x < 28; x++) for (int z = 0; z < 28; z++) { h.setBlock(new BlockPos(x, 0, z), Blocks.STONE); }
        double floor = h.absolutePos(new BlockPos(0, 1, 0)).getY();
        // its tables built here and in a batch of its own: left to the shared background thread, the seaplane's engine
        // map held up the tables of the vehicles in the pool tests running beside it
        VehicleBlueprint blueprint = SampleBlueprints.seaplane();
        blueprint.compile().warmUp();
        PartVehicle v = spawn(h, blueprint, 14, 14, floor + 0.5);
        Map<String, Double> set = Map.of("lights", 2.0, "doors", 1.0, "landing_gear", 1.0, "wipers", 2.0, "selector", -1.0);
        v.setControls(set);
        h.runAfterDelay(5, () -> {
            net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
            v.saveWithoutId(tag);
            PartVehicle copy = dev.mcmod.aerocore.neoforge.vehicle.VehicleRegistry.PART_VEHICLE.get().create(h.getLevel());
            if (copy == null) { throw new IllegalStateException("Vehicle factory failed"); }
            copy.load(tag);
            for (Map.Entry<String, Double> e : set.entrySet()) {
                h.assertTrue(e.getValue().equals(copy.controls().get(e.getKey())), e.getKey() + " kept: " + copy.controls().get(e.getKey()));
            }
            h.succeed();
        });
    }

    /**
     * 0.12: a car standing still in P with the brake on sends no display updates: every shown value (wheels' spin,
     * steer, compression, slip and load; the engine's) stays the same tick after tick once it has settled (AeroCore
     * quantizes them where they are made, with hysteresis).
     */
    @GameTest(templateNamespace = "hdfv", template = "helipad", timeoutTicks = 2400, batch = "hdfv_parked_car")
    public static void aParkedCarSendsNoDisplayUpdates(GameTestHelper h) {
        for (int x = 0; x < 28; x++) for (int z = 0; z < 28; z++) { h.setBlock(new BlockPos(x, 0, z), Blocks.STONE); }
        double floor = h.absolutePos(new BlockPos(0, 1, 0)).getY();
        VehicleBlueprint blueprint = SampleBlueprints.sedan();
        blueprint.compile().warmUp();
        PartVehicle v = spawn(h, blueprint, 14, 14, floor + 0.7);
        v.setControls(Map.of("selector", -2.0, "brake", 1.0, "throttle", 0.0));
        final int[] tick = {0};
        final java.util.List<double[]> seen = new java.util.ArrayList<>();
        h.succeedWhen(() -> {
            if (!v.ready()) { throw new GameTestAssertException("Tables still building"); }
            tick[0]++;
            java.util.List<double[]> now = new java.util.ArrayList<>();
            for (var installed : v.vehicle().installed()) {
                double[] values = new double[installed.part().shownValues()];
                for (int i = 0; i < values.length; i++) { values[i] = v.shown(installed.id(), i, 1); }
                now.add(values);
            }
            // settled after 15 s; then 5 s without a change
            if (tick[0] < 300) { seen.clear(); seen.addAll(now); throw new GameTestAssertException("Settling"); }
            for (int k = 0; k < now.size(); k++) {
                if (!java.util.Arrays.equals(now.get(k), seen.get(k))) {
                    throw new GameTestAssertException("A shown value changed at rest: " + v.vehicle().installed().get(k).id() + " "
                            + java.util.Arrays.toString(seen.get(k)) + " -> " + java.util.Arrays.toString(now.get(k)));
                }
            }
            if (tick[0] < 400) { throw new GameTestAssertException("Watching"); }
        });
    }

    /**
     * 0.12: a car's wheels show what their tyres do (the shown slip and load the ground effects go by): standing, each
     * carries its share and none slides; set rolling at 8 m/s with its wheels still and the brake on, they slide; once it
     * has stopped they slide no more.
     */
    @GameTest(templateNamespace = "hdfv", template = "helipad", timeoutTicks = 1200, batch = "hdfv_braking_car")
    public static void aBrakingCarShowsWheelSlip(GameTestHelper h) {
        for (int x = 0; x < 28; x++) for (int z = 0; z < 28; z++) { h.setBlock(new BlockPos(x, 0, z), Blocks.STONE); }
        double floor = h.absolutePos(new BlockPos(0, 1, 0)).getY();
        VehicleBlueprint blueprint = SampleBlueprints.sedan();
        blueprint.compile().warmUp();
        PartVehicle v = spawn(h, blueprint, 4, 14, floor + 0.7);
        v.setControls(Map.of("selector", 0.0, "brake", 0.0, "throttle", 0.0));
        final int[] tick = {0}, rolled = {-1};
        final double[] most = {0};
        final StringBuilder trace = new StringBuilder();
        h.succeedWhen(() -> {
            if (!v.ready()) { throw new GameTestAssertException("Tables still building"); }
            tick[0]++;
            double slip = 0, load = 0;
            int touching = 0;
            for (var installed : v.vehicle().installed()) {
                if (!(installed.part() instanceof dev.mcmod.aerocore.api.vehicle.part.AxlePart axle)) { continue; }
                for (int extra : new int[]{axle.extraShown(), axle.extraShown() + dev.mcmod.aerocore.api.wheel.WheelCorner.EXTRA}) {
                    slip = Math.max(slip, v.shown(installed.id(), extra, 1));
                    double kN = v.shown(installed.id(), extra + 1, 1);
                    load += kN;
                    if (kN > 1) { touching++; }
                }
            }
            // 0.14: standing from 3 s on, however long it takes to come to rest (the sample sedan with its engine 0.15 m
            // farther back rests at about 4.3 s; its wheels' shown slip dies away from 1.3 m/s after the drop), a failure
            // here retried, not thrown out of the server: an IllegalStateException in succeedWhen stops every test
            if (rolled[0] < 0) {
                double weight = v.vehicle().properties().massKg() * 9.80665 / 1000 + v.vehicleState().fuelKg() * 9.80665 / 1000;
                if (tick[0] % 10 == 0) { trace.append(String.format(" %d:%d/%.1f/%.3f/%.3f", tick[0], touching, load, slip, v.vehicleState().body().velocity().length())); }
                if (tick[0] < 60 || touching != 4 || Math.abs(load - weight) > 0.15 * weight || slip > 0.05) {
                    throw new GameTestAssertException("Standing: " + touching + " wheels down, " + load + " kN for " + weight + " kN, slip " + slip + "; ticks (wheels/kN/slip/speed):" + trace);
                }
                rolled[0] = tick[0];
                var b = v.vehicleState().body();
                v.vehicleState().setBody(new dev.mcmod.aerocore.api.RigidBodyState(b.position(), b.orientation().rotate(Vec3d.X).scale(8), b.orientation(), b.angularMomentum()));
                v.setControl("brake", 1.0);
                throw new GameTestAssertException("Rolling");
            }
            most[0] = Math.max(most[0], slip);
            double speed = v.vehicleState().body().velocity().length();
            if (tick[0] < rolled[0] + 40 || speed > 0.2) { throw new GameTestAssertException("Braking at " + speed + " m/s, slip " + slip); }
            if (most[0] < 1) { throw new GameTestAssertException("The wheels must have slid: at most " + most[0] + " m/s"); }
            if (slip > 0.05) { throw new GameTestAssertException("Stopped, still sliding " + slip); }
        });
    }

    @GameTest(templateNamespace = "hdfv", template = "helipad", timeoutTicks = 2400, batch = "hdfv_helicopter")
    public static void helicopterSpinsItsRotorUpAndLiftsOff(GameTestHelper h) {
        for (int x = 0; x < 28; x++) for (int z = 0; z < 28; z++) { h.setBlock(new BlockPos(x, 0, z), Blocks.STONE); }
        double floor = h.absolutePos(new BlockPos(0, 1, 0)).getY();
        VehicleBlueprint blueprint = VehicleGameTests.quickStart(SampleBlueprints.helicopter());
        blueprint.compile().warmUp();
        PartVehicle v = spawn(h, blueprint, 14, 14, floor + 0.02);
        v.setControls(Map.of("power", 1.0, "collective", 0.0, "pitch", 0.0, "roll", 0.0, "rudder", 0.0));
        final double[] integral = {0};
        h.succeedWhen(() -> {
            if (!v.ready()) { throw new GameTestAssertException("Tables still building"); }
            var body = v.vehicleState().body();
            var p = v.vehicle().properties();
            Vec3d w = body.orientation().inverse().rotate(body.angularVelocity(p));
            double pitchDeg = Math.toDegrees(Math.asin(body.orientation().rotate(Vec3d.X).y()));
            double rollDeg = Math.toDegrees(Math.asin(body.orientation().rotate(Vec3d.Z).y()));
            double height = v.vehicle().origin(body).y() - floor;
            Map<String, Double> rotor = v.vehicleState().diagnostics().get("rotor");
            double rpm = rotor == null ? 0 : rotor.get("rpm");
            h.assertTrue(Math.abs(pitchDeg) < 20 && Math.abs(rollDeg) < 20, "It must stay upright: pitch " + pitchDeg + " roll " + rollDeg);
            if (rpm < 245) {
                // spinning up, the pilot holds the heading on the pedals against the rotor's torque (left alone it turned
                // the helicopter on its skids at 1.6 rad/s, and whether it then tipped over hung on the smallest change)
                v.setControls(Map.of("collective", 0.0, "pitch", 0.0, "roll", 0.0, "rudder", Math.clamp(1.5 * w.y(), -1, 1)));
                throw new GameTestAssertException("Rotor at " + rpm + " rpm");
            }
            // a pilot: climb at 1 m/s on the collective, hold the attitude level with the cyclic, the heading with the pedals
            double vy = body.velocity().y();
            integral[0] += (1.0 - vy) * 0.05;
            double collective = Math.clamp(0.25 + 0.08 * (1.0 - vy) + 0.04 * integral[0], 0, 1);
            v.setControls(Map.of("collective", collective, "pitch", Math.clamp(-0.05 * pitchDeg, -1, 1), "roll", Math.clamp(0.05 * rollDeg, -1, 1),
                    "rudder", Math.clamp(1.5 * w.y(), -1, 1)));
            if (height < 3) { throw new GameTestAssertException("Lifting off: " + height + " m"); }
            h.assertTrue(rotor.get("thrustN") > 0.8 * p.massKg() * 9.80665, "The rotor carries it: " + rotor);
        });
    }

    private static PartVehicle spawn(GameTestHelper h, VehicleBlueprint blueprint, double x, double z, double originY) {
        BlockPos o = h.absolutePos(BlockPos.ZERO);
        PartVehicle v = VehicleRegistry.PART_VEHICLE.get().create(h.getLevel());
        if (v == null) { throw new IllegalStateException("Vehicle factory failed"); }
        v.setBlueprint(blueprint, new Vec3d(o.getX() + x, originY, o.getZ() + z), Orientation.IDENTITY);
        h.getLevel().addFreshEntity(v);
        return v;
    }

    /** Stone floor and walls with 4 m of water inside, x by z blocks; returns the absolute still-water level. */
    private static double pool(GameTestHelper h, int sx, int sz) {
        for (int x = 0; x < sx; x++) for (int z = 0; z < sz; z++) {
            h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            for (int y = 1; y <= 5; y++) {
                boolean wall = x == 0 || z == 0 || x == sx - 1 || z == sz - 1;
                if (wall) { h.setBlock(new BlockPos(x, y, z), Blocks.STONE); }
                else if (y <= 4) { h.setBlock(new BlockPos(x, y, z), Blocks.WATER); }
            }
        }
        return new WorldWaterSampler(h.getLevel()).stillSurface(h.absolutePos(new BlockPos(sx / 2, 4, sz / 2)));
    }
}
