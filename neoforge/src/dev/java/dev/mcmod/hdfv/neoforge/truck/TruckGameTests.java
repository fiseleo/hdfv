package dev.mcmod.hdfv.neoforge.truck;

import dev.mcmod.aerocore.api.Orientation;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.vehicle.VehicleBlueprint;
import dev.mcmod.aerocore.api.wheel.WheelCorner;
import dev.mcmod.aerocore.neoforge.vehicle.PartVehicle;
import dev.mcmod.aerocore.neoforge.vehicle.VehicleRegistry;
import dev.mcmod.hdfv.drive.Hitch;
import dev.mcmod.hdfv.drive.TestCars;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;

/**
 * 0.18: a tractor and a semi-trailer in the world (Hitching, AeroCore's couplings): the coupling key couples a trailer
 * standing on its legs with its kingpin in the jaw, its legs go up and the tractor tows it, straight and in a turn, the
 * kingpin staying in the jaw; let down on its legs it is let go and stays where it stands as the tractor drives away; a
 * trailer standing too far back is refused.
 */
@PrefixGameTestTemplate(false)
public final class TruckGameTests {
    private TruckGameTests() {}

    /** A stone floor over the 28 m pad (the test is walled in with barriers there: a semi of 20 m has 8 m to drive), its top's height. */
    static double floor(GameTestHelper h) {
        for (int x = 0; x < 28; x++) for (int z = 0; z < 28; z++) { h.setBlock(new BlockPos(x, 0, z), Blocks.STONE); }
        return h.absolutePos(new BlockPos(0, 1, 0)).getY();
    }

    static PartVehicle spawn(GameTestHelper h, VehicleBlueprint b, Vec3d origin) {
        PartVehicle v = VehicleRegistry.PART_VEHICLE.get().create(h.getLevel());
        if (v == null) { throw new IllegalStateException("Vehicle factory failed"); }
        v.setBlueprint(b, origin, Orientation.IDENTITY);
        h.getLevel().addFreshEntity(v);
        return v;
    }

    /** The trailer's origin for its kingpin to stand back (m, along -x) and down from the tractor's jaw, both facing +x. */
    static Vec3d behind(VehicleBlueprint tractor, Vec3d tractorOrigin, VehicleBlueprint trailer, double back, double down) {
        var jaw = Hitch.jaw(tractor, tractor.compile());
        var pin = Hitch.kingpin(trailer, trailer.compile());
        return tractorOrigin.add(jaw.point()).subtract(pin.point()).add(new Vec3d(-back, -down, 0));
    }

    static double gap(PartVehicle tractor, PartVehicle trailer) {
        var c = trailer.coupling();
        return c.geometry(tractor.vehicleState().body(), tractor.vehicle().properties(), trailer.vehicleState().body(), trailer.vehicle().properties()).gap().length();
    }

    /** A press of the coupling key: the next count. */
    static void press(PartVehicle tractor) { tractor.setControl("couple", tractor.controls().getOrDefault("couple", 0.0) + 1); }

    @GameTest(templateNamespace = "hdfv", template = "helipad", timeoutTicks = 3000, batch = "hdfv_truck_tow")
    public static void aTractorCouplesItsTrailerTowsItAndLetsItGo(GameTestHelper h) {
        double floor = floor(h);
        VehicleBlueprint tb = TestCars.tractor6x4(), rb = TestCars.lowLoader();
        tb.compile().warmUp();
        rb.compile().warmUp();
        BlockPos o = h.absolutePos(BlockPos.ZERO);
        // the rig from x 3.2 to 19.9: 8 m to the barriers ahead
        Vec3d at = new Vec3d(o.getX() + 14, floor + 0.8, o.getZ() + 14);
        PartVehicle tractor = spawn(h, tb, at), trailer = spawn(h, rb, behind(tb, at, rb, 0, 0.04));
        tractor.setControls(Map.of("brake", 1.0, "throttle", 0.0, "selector", 0.0));
        final double[] mark = new double[2];
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(tractor.ready() && trailer.ready(), "Tables still building"))
                .thenIdle(60)
                .thenExecute(() -> press(tractor))
                .thenIdle(5)
                .thenExecute(() -> {
                    h.assertTrue(trailer.leader() == tractor && tractor.followers().contains(trailer), "coupled by the key");
                    h.assertTrue(tractor.controls().getOrDefault("coupling", 0.0) == 1 && trailer.controls().getOrDefault("landing_gear", 0.0) == 1,
                            "the tractor shows it, the legs go up");
                })
                .thenIdle(140)
                .thenExecute(() -> {
                    h.assertTrue(trailer.shown("landing_legs_1", WheelCorner.SHOWN + WheelCorner.EXTRA, 1) > 0.99, "the legs are up");
                    mark[0] = trailer.vehicleState().body().position().x();
                    // the test tractor has a car's turbodiesel for 28 t: it pulls away slowly
                    tractor.setControls(Map.of("brake", 0.0, "throttle", 0.8, "selector", 1.0, "steer", 0.6));
                })
                .thenIdle(120)
                .thenExecute(() -> {
                    double moved = trailer.vehicleState().body().position().x() - mark[0];
                    h.assertTrue(moved > 1.0, "the trailer is towed: " + moved + " m");
                    h.assertTrue(gap(tractor, trailer) < 0.01, "the kingpin stays in the jaw: " + gap(tractor, trailer));
                    tractor.setControls(Map.of("steer", 0.7, "throttle", 0.8));
                })
                .thenIdle(60)
                .thenExecute(() -> {
                    double a = Math.abs(trailer.articulationRad());
                    h.assertTrue(a > Math.toRadians(0.5) && a < Math.PI / 2, "articulated in the turn: " + Math.toDegrees(a) + " deg");
                    h.assertTrue(gap(tractor, trailer) < 0.01, "in the turn too: " + gap(tractor, trailer));
                    tractor.setControls(Map.of("steer", 0.0, "throttle", 0.0, "brake", 1.0, "selector", 0.0));
                })
                .thenWaitUntil(() -> h.assertTrue(tractor.vehicleState().body().velocity().length() < 0.1, "stopping"))
                .thenExecute(() -> press(tractor))
                .thenIdle(2)
                .thenExecute(() -> h.assertTrue(tractor.controls().getOrDefault("coupling", 0.0) == 2, "letting the trailer down"))
                .thenWaitUntil(() -> h.assertTrue(tractor.followers().isEmpty() && trailer.coupling() == null, "let go"))
                .thenExecute(() -> {
                    h.assertTrue(tractor.controls().getOrDefault("coupling", 0.0) == 0 && trailer.controls().getOrDefault("handbrake", 0.0) == 1, "the brake on");
                    mark[1] = trailer.vehicleState().body().worldPoint(trailer.vehicle().properties(), Hitch.kingpin(rb, trailer.vehicle()).point()).y();
                    tractor.setControls(Map.of("brake", 0.0, "throttle", 0.3, "selector", 1.0));
                })
                .thenIdle(60)
                .thenExecute(() -> {
                    double plate = trailer.vehicleState().body().worldPoint(trailer.vehicle().properties(), Hitch.kingpin(rb, trailer.vehicle()).point()).y();
                    h.assertTrue(trailer.vehicleState().body().velocity().length() < 0.05, "the trailer stays put: " + trailer.vehicleState().body().velocity());
                    h.assertTrue(plate > mark[1] - 0.05, "standing on its legs: its plate from " + mark[1] + " to " + plate);
                })
                .thenSucceed();
    }

    /**
     * The semi as /hdfv dev drive spawn semi sets it down (no controls, its lowest tyre 5 cm over the floor, the trailer's
     * kingpin on the jaw, coupled once both are ready) on a one-block floor 10 m up (AutoShot's pad in the air): both stay
     * where they were set, coupled.
     */
    @GameTest(templateNamespace = "hdfv", template = "helipad", timeoutTicks = 2400, batch = "hdfv_truck_spawn")
    public static void aSemiSetDownLikeTheDevCommandStaysPut(GameTestHelper h) {
        for (int x = 0; x < 28; x++) for (int z = 0; z < 28; z++) { h.setBlock(new BlockPos(x, 10, z), Blocks.STONE); }
        double floor = h.absolutePos(new BlockPos(0, 11, 0)).getY();
        VehicleBlueprint tb = TestCars.tractor6x4(), rb = TestCars.lowLoader();
        tb.compile().warmUp();
        rb.compile().warmUp();
        BlockPos o = h.absolutePos(BlockPos.ZERO);
        Vec3d at = new Vec3d(o.getX() + 14, floor + dev.mcmod.hdfv.neoforge.vehicle.DriveDevCommands.standHeight(tb), o.getZ() + 14);
        PartVehicle tractor = spawn(h, tb, at), trailer = spawn(h, rb, behind(tb, at, rb, 0, 0));
        tractor.setControls(dev.mcmod.hdfv.neoforge.vehicle.DriveDevCommands.PARKED);
        trailer.setControls(dev.mcmod.hdfv.neoforge.vehicle.DriveDevCommands.PARKED);
        Hitching.coupleWhenReady(tractor, trailer);
        final Vec3d[] start = new Vec3d[2];
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(tractor.ready() && trailer.ready(), "Tables still building"))
                .thenExecute(() -> { start[0] = tractor.vehicleState().body().position(); start[1] = trailer.vehicleState().body().position(); })
                .thenIdle(200)
                .thenExecute(() -> {
                    Vec3d t = tractor.vehicleState().body().position(), r = trailer.vehicleState().body().position();
                    h.assertTrue(t.subtract(start[0]).length() < 0.5, "the tractor stays: " + start[0] + " -> " + t + " at " + tractor.vehicleState().body().velocity());
                    h.assertTrue(r.subtract(start[1]).length() < 0.5, "the trailer stays: " + start[1] + " -> " + r);
                    h.assertTrue(trailer.leader() == tractor, "coupled when ready");
                })
                .thenSucceed();
    }

    @GameTest(templateNamespace = "hdfv", template = "helipad", timeoutTicks = 1200, batch = "hdfv_truck_refuse")
    public static void aTrailerTooFarBackIsNotCoupled(GameTestHelper h) {
        double floor = floor(h);
        VehicleBlueprint tb = TestCars.tractor6x4(), rb = TestCars.lowLoader();
        tb.compile().warmUp();
        rb.compile().warmUp();
        BlockPos o = h.absolutePos(BlockPos.ZERO);
        Vec3d at = new Vec3d(o.getX() + 14, floor + 0.8, o.getZ() + 14);
        PartVehicle tractor = spawn(h, tb, at), trailer = spawn(h, rb, behind(tb, at, rb, 1.5, 0.04));
        tractor.setControls(Map.of("brake", 1.0));
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(tractor.ready() && trailer.ready(), "Tables still building"))
                .thenIdle(40)
                .thenExecute(() -> press(tractor))
                .thenIdle(5)
                .thenExecute(() -> {
                    h.assertTrue(tractor.followers().isEmpty() && trailer.coupling() == null, "not coupled 1.5 m short of the jaw");
                    h.assertTrue(tractor.controls().getOrDefault("coupling", 0.0) == 0, "the tractor shows none");
                })
                .thenSucceed();
    }
}
