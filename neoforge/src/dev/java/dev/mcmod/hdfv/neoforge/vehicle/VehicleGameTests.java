package dev.mcmod.hdfv.neoforge.vehicle;

import dev.mcmod.aerocore.api.FluidState;
import dev.mcmod.aerocore.api.Orientation;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.vehicle.Vehicle;
import dev.mcmod.aerocore.api.vehicle.VehicleBlueprint;
import dev.mcmod.aerocore.neoforge.probe.Wakes;
import dev.mcmod.aerocore.neoforge.probe.WorldWaterSampler;
import dev.mcmod.aerocore.neoforge.vehicle.PartVehicle;
import dev.mcmod.aerocore.neoforge.vehicle.VehicleRegistry;
import dev.mcmod.hdfv.part.HullDesign;
import dev.mcmod.hdfv.design.Designs;
import dev.mcmod.hdfv.design.RecordEditor;
import dev.mcmod.hdfv.part.SampleBlueprints;
import dev.mcmod.hdfv.part.engine.JetControlDesign;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;

/** Part-built vehicles in the world: the sample blueprints float, drive, turn, carry a rider and survive saving. */
@PrefixGameTestTemplate(false)
public final class VehicleGameTests {
    private VehicleGameTests() {}

    @GameTest(templateNamespace = "hdfv", template = "hull_pool", timeoutTicks = 420)
    public static void platformFromPartsFloatsWhereArchimedesSays(GameTestHelper h) {
        Wakes.synchronous = true;
        double surface = pool(h);
        PartVehicle v = spawn(h, SampleBlueprints.platform(), 11, surface + 0.1);
        final double[] sum = {0, 0};
        for (int tick = 300; tick < 400; tick++) {
            h.runAfterDelay(tick, () -> {
                double displaced = 0;
                for (String id : new String[]{"float_fl", "float_fr", "float_al", "float_ar"}) {
                    displaced += v.vehicleState().diagnostics().get(id).get("wetFraction") * 4.0 / 3 * Math.PI * Math.pow(0.45, 3);
                }
                sum[0] += displaced;
                sum[1]++;
            });
        }
        h.runAfterDelay(401, () -> {
            double mass = v.vehicle().properties().massKg(), displaced = sum[0] / sum[1] * FluidState.FRESH_WATER_20C.densityKgPerM3();
            // The rudder and screw add no buoyancy of their own; the rest of the weight hangs on the floats.
            h.assertTrue(Math.abs(displaced - mass) < 0.03 * mass, "Floats must carry the weight: " + displaced + " kg of water for " + mass + " kg");
            Vec3d up = v.vehicleState().body().orientation().rotate(Vec3d.Y);
            h.assertTrue(up.y() > 0.995, "The platform must float level: " + up);
            h.succeed();
        });
    }

    @GameTest(templateNamespace = "hdfv", template = "hull_pool", timeoutTicks = 240)
    public static void cruiserDrivesAheadAndTurnsToStarboardOnItsControls(GameTestHelper h) {
        Wakes.synchronous = true;
        double surface = pool(h);
        VehicleBlueprint blueprint = SampleBlueprints.cruiser();
        // The hull's Michell table and the screw's table (about a second), built here: the GameTest server ticks faster
        // than real time, so the vehicle's own background warm-up may not finish within the test's 100 ticks.
        blueprint.compile().warmUp();
        HullDesign hull = (HullDesign) blueprint.part("hull").design();
        double waterline = hull.designParticulars(hull.geometry()).waterlineY();
        PartVehicle v = spawn(h, blueprint, 5, surface - waterline);
        // Straight ahead on the throttle first, then the rudder.
        v.setControls(Map.of("throttle", 1.0, "rudder", 0.0));
        Vec3d start = v.vehicle().origin(v.vehicleState().body());
        final double[] turn = {0}, surge = {0};
        h.runAfterDelay(100, () -> {
            var body = v.vehicleState().body();
            surge[0] = body.velocity().dot(body.orientation().rotate(Vec3d.X));
            Vec3d moved = v.vehicle().origin(body).subtract(start);
            Map<String, Double> screw = v.vehicleState().diagnostics().get("screw"), motor = v.vehicleState().diagnostics().get("motor");
            h.assertTrue(surge[0] > 0.5 && moved.x() > 1.0, "The motor and screw must drive the boat ahead: surge " + surge[0] + " m/s, moved " + moved);
            h.assertTrue(screw.get("thrustN") > 50 && motor.get("torqueNm") > 0, "Thrust and torque must be reported: " + screw + " " + motor);
            h.assertTrue(Math.abs(v.rotorSpeed(1) * 2 - v.rotorSpeed(0)) < 1e-6 * Math.abs(v.rotorSpeed(0)) + 1e-9, "The 2:1 gear halves the screw speed");
            v.setControl("rudder", 0.6);
        });
        for (int tick = 101; tick <= 180; tick++) {
            h.runAfterDelay(tick, () -> turn[0] += v.vehicleState().body().angularVelocity(v.vehicle().properties()).y());
        }
        h.runAfterDelay(181, () -> {
            h.assertTrue(turn[0] < -0.5, "Positive rudder must turn the bow to starboard: " + turn[0]);
            h.succeed();
        });
    }

    // Its own batch: it runs for as long as the engine map takes to build, and its wake would reach the neighbours' pools.
    @GameTest(templateNamespace = "hdfv", template = "hull_pool", timeoutTicks = 2400, batch = "aerocore_engine_boat")
    public static void engineBoatStartsItsEngineAndDrivesAhead(GameTestHelper h) {
        Wakes.synchronous = true;
        double surface = pool(h);
        VehicleBlueprint blueprint = SampleBlueprints.engineBoat();
        // The GameTest server ticks faster than real time, so build the engine map (seconds) here; the vehicle's own
        // warm-up then finds it cached.
        blueprint.compile().warmUp();
        HullDesign hull = (HullDesign) blueprint.part("hull").design();
        double waterline = hull.designParticulars(hull.geometry()).waterlineY();
        PartVehicle v = spawn(h, blueprint, 5, surface - waterline);
        v.setControls(Map.of("throttle", 0.6, "rudder", 0.0));
        // The engine map is built off the game thread (seconds): count ticks from when the vehicle is ready.
        final int[] running = {0};
        final Vec3d[] start = {null};
        h.succeedWhen(() -> {
            if (!v.ready()) { throw new net.minecraft.gametest.framework.GameTestAssertException("Engine map still building"); }
            if (start[0] == null) { start[0] = v.vehicle().origin(v.vehicleState().body()); }
            running[0]++;
            // From 3 s on it passes as soon as all holds: at this throttle the boat reaches the far wall in about 7 s.
            if (running[0] < 60) { throw new net.minecraft.gametest.framework.GameTestAssertException("Running"); }
            Map<String, Double> engine = v.vehicleState().diagnostics().get("engine");
            var body = v.vehicleState().body();
            double surge = body.velocity().dot(body.orientation().rotate(Vec3d.X));
            Vec3d moved = v.vehicle().origin(body).subtract(start[0]);
            h.assertTrue(engine.get("running") == 1, "The starter must have started the engine: " + engine);
            h.assertTrue(engine.get("rpm") > 1500 && engine.get("torqueNm") > 0, "The engine must pull at part throttle: " + engine);
            h.assertTrue(surge > 0.5 && moved.x() > 1.0, "The engine and screw must drive the boat ahead: surge " + surge + " m/s, moved " + moved);
            h.assertTrue(Math.abs(v.rotorSpeed(1) * 3 - v.rotorSpeed(0)) < 1e-6 * Math.abs(v.rotorSpeed(0)) + 1e-9, "The 3:1 gear");
        });
    }

    // Its own batch too: the jet drives the boat across the pool and its wake would reach the neighbours.
    @GameTest(templateNamespace = "hdfv", template = "hull_pool", timeoutTicks = 400, batch = "aerocore_jet_boat")
    public static void jetBoatLightsItsTurbojetAndDrivesAhead(GameTestHelper h) {
        Wakes.synchronous = true;
        double surface = pool(h);
        VehicleBlueprint blueprint = quickStart(SampleBlueprints.jetBoat());
        blueprint.compile().warmUp();   // the running lines (a fraction of a second), so the vehicle finds them cached
        HullDesign hull = (HullDesign) blueprint.part("hull").design();
        double waterline = hull.designParticulars(hull.geometry()).waterlineY();
        PartVehicle v = spawn(h, blueprint, 5, surface - waterline);
        v.setControls(Map.of("throttle", 0.0, "rudder", 0.0));
        final int[] running = {0};
        final Vec3d[] start = {null};
        h.succeedWhen(() -> {
            if (!v.ready()) { throw new net.minecraft.gametest.framework.GameTestAssertException("Running lines still building"); }
            if (start[0] == null) { start[0] = v.vehicle().origin(v.vehicleState().body()); }
            running[0]++;
            // From 10 s on (it lights at about 3 s and, with the quick start schedule, idles at about 8 s) it passes as soon as all holds.
            if (running[0] < 200) { throw new net.minecraft.gametest.framework.GameTestAssertException("Running"); }
            Map<String, Double> jet = v.vehicleState().diagnostics().get("jet");
            var body = v.vehicleState().body();
            double surge = body.velocity().dot(body.orientation().rotate(Vec3d.X));
            Vec3d moved = v.vehicle().origin(body).subtract(start[0]);
            h.assertTrue(jet.get("lit") == 1 && jet.get("speedFraction") > 0.5, "The starter must have lit the jet and it must idle: " + jet);
            h.assertTrue(jet.get("thrustN") > 1000, "Idle thrust: " + jet);
            h.assertTrue(surge > 0.5 && moved.x() > 1.0, "The jet must drive the boat ahead: surge " + surge + " m/s, moved " + moved);
        });
    }

    // Its own batch: the pulsejet drives the boat across the pool.
    @GameTest(templateNamespace = "hdfv", template = "hull_pool", timeoutTicks = 400, batch = "hdfv_pulsejet_boat")
    public static void pulsejetBoatIsBlownToStartThenBuzzesAhead(GameTestHelper h) {
        Wakes.synchronous = true;
        double surface = pool(h);
        VehicleBlueprint blueprint = SampleBlueprints.pulsejetBoat();
        blueprint.compile().warmUp();   // the pulsejet's table (a few seconds), so the vehicle finds it built
        HullDesign hull = (HullDesign) blueprint.part("hull").design();
        double waterline = hull.designParticulars(hull.geometry()).waterlineY();
        PartVehicle v = spawn(h, blueprint, 5, surface - waterline);
        v.setControls(Map.of("throttle", 1.0, "rudder", 0.0, "start", 0.0));
        final int[] ticks = {0};
        final Vec3d[] start = {null};
        h.succeedWhen(() -> {
            if (!v.ready()) { throw new net.minecraft.gametest.framework.GameTestAssertException("Table still building"); }
            ticks[0]++;
            Map<String, Double> pj = v.vehicleState().diagnostics().get("pulsejet");
            if (ticks[0] == 20) { h.assertTrue(pj.get("lit") == 0, "Standing, fuelled, it must not start by itself: " + pj); }
            if (ticks[0] == 21) { v.setControl("start", 1); start[0] = v.vehicle().origin(v.vehicleState().body()); }
            if (ticks[0] == 31) { v.setControl("start", 0); }   // half a second of starting air
            if (ticks[0] < 120) { throw new net.minecraft.gametest.framework.GameTestAssertException("Running"); }
            var body = v.vehicleState().body();
            double surge = body.velocity().dot(body.orientation().rotate(Vec3d.X));
            Vec3d moved = v.vehicle().origin(body).subtract(start[0]);
            h.assertTrue(pj.get("lit") == 1 && pj.get("frequencyHz") > 30, "Blown, it runs on by itself: " + pj);
            h.assertTrue(pj.get("thrustN") > 1000, "Its thrust: " + pj);
            h.assertTrue(surge > 0.5 && moved.x() > 1.0, "The pulsejet must drive the boat ahead: surge " + surge + " m/s, moved " + moved);
        });
    }

    // Its own batch: the turbofan drives the boat across the pool.
    @GameTest(templateNamespace = "hdfv", template = "hull_pool", timeoutTicks = 400, batch = "hdfv_fan_boat")
    public static void fanBoatLightsItsTurbofanAndDrivesAhead(GameTestHelper h) {
        Wakes.synchronous = true;
        double surface = pool(h);
        VehicleBlueprint blueprint = quickStart(SampleBlueprints.fanBoat());
        blueprint.compile().warmUp();   // the running lines, so the vehicle finds them cached
        HullDesign hull = (HullDesign) blueprint.part("hull").design();
        double waterline = hull.designParticulars(hull.geometry()).waterlineY();
        PartVehicle v = spawn(h, blueprint, 5, surface - waterline);
        v.setControls(Map.of("throttle", 0.0, "rudder", 0.0));
        final int[] running = {0};
        final Vec3d[] start = {null};
        h.succeedWhen(() -> {
            if (!v.ready()) { throw new net.minecraft.gametest.framework.GameTestAssertException("Running lines still building"); }
            if (start[0] == null) { start[0] = v.vehicle().origin(v.vehicleState().body()); }
            running[0]++;
            // From 10 s on (it lights within two seconds and, with the quick start schedule, idles within eight) it passes as soon as all holds.
            if (running[0] < 200) { throw new net.minecraft.gametest.framework.GameTestAssertException("Running"); }
            Map<String, Double> fan = v.vehicleState().diagnostics().get("fan");
            var body = v.vehicleState().body();
            double surge = body.velocity().dot(body.orientation().rotate(Vec3d.X));
            Vec3d moved = v.vehicle().origin(body).subtract(start[0]);
            h.assertTrue(fan.get("lit") == 1 && fan.get("coreSpeedFraction") > 0.4 && fan.get("speedFraction") > 0.15, "The starter must have lit the fan and it must idle: " + fan);
            h.assertTrue(fan.get("thrustN") > 250, "Idle thrust: " + fan);
            h.assertTrue(surge > 0.3 && moved.x() > 0.5, "The fan must drive the boat ahead: surge " + surge + " m/s, moved " + moved);
        });
    }

    // Its own batch: the screw's race and the boat's wake.
    @GameTest(templateNamespace = "hdfv", template = "hull_pool", timeoutTicks = 400, batch = "hdfv_turbine_boat")
    public static void turbineBoatTurnsItsScrewThroughTheGearAndDrivesAhead(GameTestHelper h) {
        Wakes.synchronous = true;
        double surface = pool(h);
        VehicleBlueprint blueprint = quickStart(SampleBlueprints.turbineBoat());
        blueprint.compile().warmUp();
        HullDesign hull = (HullDesign) blueprint.part("hull").design();
        double waterline = hull.designParticulars(hull.geometry()).waterlineY();
        PartVehicle v = spawn(h, blueprint, 5, surface - waterline);
        v.setControls(Map.of("throttle", 0.0, "rudder", 0.0));
        final int[] running = {0};
        final Vec3d[] start = {null};
        h.succeedWhen(() -> {
            if (!v.ready()) { throw new net.minecraft.gametest.framework.GameTestAssertException("Running lines still building"); }
            if (start[0] == null) { start[0] = v.vehicle().origin(v.vehicleState().body()); }
            running[0]++;
            if (running[0] < 200) { throw new net.minecraft.gametest.framework.GameTestAssertException("Running"); }
            Map<String, Double> engine = v.vehicleState().diagnostics().get("engine");
            var body = v.vehicleState().body();
            double surge = body.velocity().dot(body.orientation().rotate(Vec3d.X));
            Vec3d moved = v.vehicle().origin(body).subtract(start[0]);
            h.assertTrue(engine.get("lit") == 1 && engine.get("speedFraction") > 0.55, "The starter must have lit the gas generator and it must idle: " + engine);
            h.assertTrue(engine.get("outputRpm") > 1000 && engine.get("torqueNm") > 0, "The power turbine must turn the output: " + engine);
            h.assertTrue(Math.abs(v.rotorSpeed(2) * 14 - v.rotorSpeed(0)) < 1e-6 * Math.abs(v.rotorSpeed(0)) + 1e-9, "The 14:1 gear joins the output (rotor 0) and the screw");
            h.assertTrue(surge > 0.3 && moved.x() > 0.5, "The screw must drive the boat ahead: surge " + surge + " m/s, moved " + moved);
        });
    }

    /**
     * The blueprint with its gas turbines on a quick start schedule (10 % per second instead of their real 1-2 %): a
     * real start takes tens of seconds, in which a boat would drift across the pool; the bench tests the real start.
     */
    public static VehicleBlueprint quickStart(VehicleBlueprint b) {
        List<VehicleBlueprint.Entry> parts = new java.util.ArrayList<>();
        for (VehicleBlueprint.Entry e : b.parts()) {
            var design = e.design();
            if (Designs.components(design) != null && Designs.components(design).get("control") instanceof JetControlDesign c) {
                design = Designs.withPage(design, "control", RecordEditor.with(c, "startRate", 0.1));
            }
            parts.add(new VehicleBlueprint.Entry(e.id(), design, e.mount(), e.bindings()));
        }
        return new VehicleBlueprint(b.name(), parts, b.connections());
    }

    @GameTest(templateNamespace = "hdfv", template = "hull_pool", timeoutTicks = 120)
    public static void riderSitsInTheSeatAndTheVehicleSurvivesSaving(GameTestHelper h) {
        Wakes.synchronous = true;
        double surface = pool(h);
        PartVehicle v = spawn(h, SampleBlueprints.platform(), 11, surface + 0.1);
        ArmorStand stand = EntityType.ARMOR_STAND.create(h.getLevel());
        if (stand == null) { throw new IllegalStateException("Armor stand factory failed"); }
        stand.moveTo(v.getX(), v.getY() + 1, v.getZ());
        h.getLevel().addFreshEntity(stand);
        h.assertTrue(stand.startRiding(v, true), "The seat must take a rider");
        v.setControl("throttle", 0.5);
        h.runAfterDelay(80, () -> {
            // AeroCore 0.30: a rider sits with the hips on the seat's point (its position that far below it, along the vehicle's up)
            Vec3d seat = v.toWorld(v.vehicle().seats().getFirst().subtract(new Vec3d(0, PartVehicle.SEATED_HIP_M, 0)));
            h.assertTrue(new Vec3d(stand.getX(), stand.getY(), stand.getZ()).subtract(seat).length() < 0.15, "The rider sits on the seat: "
                    + stand.position() + " vs " + seat);
            CompoundTag tag = new CompoundTag();
            v.saveWithoutId(tag);
            PartVehicle copy = VehicleRegistry.PART_VEHICLE.get().create(h.getLevel());
            if (copy == null) { throw new IllegalStateException("Vehicle factory failed"); }
            copy.load(tag);
            Vehicle a = v.vehicle(), b = copy.vehicle();
            h.assertTrue(b != null && b.properties().massKg() == a.properties().massKg(), "The blueprint must come back");
            h.assertTrue(copy.vehicleState().body().position().subtract(v.vehicleState().body().position()).length() < 1e-9, "The state must come back");
            h.assertTrue(copy.vehicleState().trainSpeed(0) == v.vehicleState().trainSpeed(0), "Shaft speeds must come back");
            h.assertTrue(copy.controls().equals(v.controls()), "Controls must come back");
            h.succeed();
        });
    }

    public static PartVehicle spawn(GameTestHelper h, VehicleBlueprint blueprint, double x, double originY) {
        BlockPos o = h.absolutePos(BlockPos.ZERO);
        PartVehicle v = VehicleRegistry.PART_VEHICLE.get().create(h.getLevel());
        if (v == null) { throw new IllegalStateException("Vehicle factory failed"); }
        v.setBlueprint(blueprint, new Vec3d(o.getX() + x, originY, o.getZ() + 8), Orientation.IDENTITY);
        h.getLevel().addFreshEntity(v);
        return v;
    }

    /** Stone floor and walls with 4 m of water inside (as HullGameTests); returns the absolute still-water level. */
    public static double pool(GameTestHelper h) {
        for (int x = 0; x < 24; x++) for (int z = 0; z < 16; z++) {
            h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            for (int y = 1; y <= 5; y++) {
                boolean wall = x == 0 || z == 0 || x == 23 || z == 15;
                if (wall) { h.setBlock(new BlockPos(x, y, z), Blocks.STONE); }
                else if (y <= 4) { h.setBlock(new BlockPos(x, y, z), Blocks.WATER); }
            }
        }
        return new WorldWaterSampler(h.getLevel()).stillSurface(h.absolutePos(new BlockPos(11, 4, 8)));
    }
}
