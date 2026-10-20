package dev.mcmod.hdfv.neoforge.vehicle;

import dev.mcmod.aerocore.api.Orientation;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.vehicle.VehicleBlueprint;
import dev.mcmod.aerocore.neoforge.vehicle.PartVehicle;
import dev.mcmod.aerocore.neoforge.vehicle.VehicleRegistry;
import dev.mcmod.hdfv.drive.TestCars;
import dev.mcmod.hdfv.part.SampleBlueprints;
import dev.mcmod.hdfv.part.engine.PistonEngineDesign;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;

/**
 * Driving a car in the world (0.14), through the channels the car keys send: "steer" turns it (left positive), the
 * handbrake holds an automatic creeping in D, a 4x4's drive modes and lockers reach its transfer case and axles, and a
 * manual's automatic clutch moves it off. Each in its own batch: the cars' engine tables are built first.
 */
@PrefixGameTestTemplate(false)
public final class DriveGameTests {
    private DriveGameTests() {}

    static double floor(GameTestHelper h) {
        for (int x = 0; x < 28; x++) for (int z = 0; z < 28; z++) { h.setBlock(new BlockPos(x, 0, z), Blocks.STONE); }
        return h.absolutePos(new BlockPos(0, 1, 0)).getY();
    }

    static PartVehicle spawn(GameTestHelper h, VehicleBlueprint blueprint, double x, double z, double originY) {
        BlockPos o = h.absolutePos(BlockPos.ZERO);
        PartVehicle v = VehicleRegistry.PART_VEHICLE.get().create(h.getLevel());
        if (v == null) { throw new IllegalStateException("Vehicle factory failed"); }
        v.setBlueprint(blueprint, new Vec3d(o.getX() + x, originY, o.getZ() + z), Orientation.IDENTITY);
        h.getLevel().addFreshEntity(v);
        return v;
    }

    /** Facing +x (east), the sedan in D with some throttle and the wheel turned left: it heads off to the north (-z). */
    @GameTest(templateNamespace = "hdfv", template = "helipad", timeoutTicks = 1200, batch = "hdfv_drive_steer")
    public static void aSedanTurnsLeftOnSteer(GameTestHelper h) {
        double floor = floor(h);
        VehicleBlueprint blueprint = SampleBlueprints.sedan();
        blueprint.compile().warmUp();
        PartVehicle v = spawn(h, blueprint, 5, 20, floor + 0.7);
        v.setControls(Map.of("selector", 1.0, "throttle", 0.3, "steer", 1.0, "brake", 0.0));
        h.succeedWhen(() -> {
            if (!v.ready()) { throw new GameTestAssertException("Tables still building"); }
            var body = v.vehicleState().body();
            double vz = body.velocity().z(), speed = body.velocity().length();
            if (speed < 1 || vz > -0.5) { throw new GameTestAssertException("Turning: " + body.velocity()); }
            h.assertTrue(body.angularVelocity(v.vehicle().properties()).y() > 0, "yawing to the left: " + body.angularVelocity(v.vehicle().properties()));
        });
    }

    /**
     * Two sedans rolling at 3 m/s in N: the one with its handbrake pulled up stops (the rear wheels at 0.35 of their brakes'
     * torque: about 1.1 m/s^2), the other rolls on. (In D a little throttle through the converter overcomes a handbrake,
     * as in a real car.)
     */
    @GameTest(templateNamespace = "hdfv", template = "helipad", timeoutTicks = 1200, batch = "hdfv_drive_handbrake")
    public static void theHandbrakeStopsARollingCar(GameTestHelper h) {
        double floor = floor(h);
        VehicleBlueprint blueprint = SampleBlueprints.sedan();
        blueprint.compile().warmUp();
        PartVehicle held = spawn(h, blueprint, 3, 7, floor + 0.7), free = spawn(h, blueprint, 3, 21, floor + 0.7);
        for (PartVehicle v : new PartVehicle[]{held, free}) { v.setControls(Map.of("selector", 0.0, "throttle", 0.0, "handbrake", 0.0, "brake", 0.0)); }
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(held.ready() && free.ready(), "Tables still building"))
                .thenIdle(40)
                .thenExecute(() -> {
                    for (PartVehicle v : new PartVehicle[]{held, free}) {
                        var b = v.vehicleState().body();
                        v.vehicleState().setBody(new dev.mcmod.aerocore.api.RigidBodyState(b.position(), b.orientation().rotate(Vec3d.X).scale(3), b.orientation(), b.angularMomentum()));
                    }
                    held.setControl("handbrake", 1.0);
                })
                .thenIdle(80)
                .thenExecute(() -> {
                    double stopped = held.vehicleState().body().velocity().length(), rolling = free.vehicleState().body().velocity().length();
                    h.assertTrue(stopped < 0.2, "The handbrake stopped it in 4 s: " + stopped + " m/s");
                    h.assertTrue(rolling > 2, "The other rolls on: " + rolling + " m/s");
                })
                .thenSucceed();
    }

    /**
     * The test 4x4 (TestCars): its part-time case goes into 4H and, standing, into 4L; "diff_lock" 1 locks the rear
     * axle, 2 the front as well; then with the automatic clutch, first gear and some throttle it moves off without stalling.
     */
    @GameTest(templateNamespace = "hdfv", template = "helipad", timeoutTicks = 2400, batch = "hdfv_drive_4x4")
    public static void aFourByFoursModesLockersAndClutch(GameTestHelper h) {
        double floor = floor(h);
        VehicleBlueprint blueprint = TestCars.fourByFour();
        blueprint.compile().warmUp();
        PartVehicle v = spawn(h, blueprint, 5, 14, floor + 0.8);
        v.setControls(Map.of("mode", 1.0, "diff_lock", 1.0, "gear", 0.0, "auto_clutch", 1.0, "throttle", 0.0, "brake", 0.0));
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(v.ready(), "Tables still building"))
                .thenIdle(40)
                .thenExecute(() -> {
                    h.assertTrue(Math.round(v.shown("transfer", 1, 1)) == 1, "4H: " + v.shown("transfer", 1, 1));
                    h.assertTrue(v.input("rear_axle", "lock") > 0.5 && v.input("front_axle", "lock") < 0.5, "the rear locked, the front not");
                    v.setControls(Map.of("mode", 2.0, "diff_lock", 2.0));
                })
                .thenIdle(40)
                .thenExecute(() -> {
                    h.assertTrue(Math.round(v.shown("transfer", 0, 1)) == 2, "low range standing: " + v.shown("transfer", 0, 1));
                    h.assertTrue(v.input("rear_axle", "lock") > 0.5 && v.input("front_axle", "lock") > 0.5, "both locked");
                    v.setControls(Map.of("gear", 1.0, "throttle", 0.4));
                })
                .thenWaitUntil(() -> {
                    h.assertTrue(v.vehicleState().internal("engine", PistonEngineDesign.STATE_RUNNING) > 0.5, "Running");
                    h.assertTrue(v.vehicleState().body().velocity().length() > 1, "Moving off: " + v.vehicleState().body().velocity().length());
                })
                .thenExecute(() -> h.assertTrue(v.vehicleState().internal("engine", PistonEngineDesign.STATE_RUNNING) > 0.5, "Not stalled"))
                .thenSucceed();
    }
}
