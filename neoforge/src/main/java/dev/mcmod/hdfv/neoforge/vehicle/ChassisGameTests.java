package dev.mcmod.hdfv.neoforge.vehicle;

import dev.mcmod.aerocore.api.vehicle.VehicleBlueprint;
import dev.mcmod.aerocore.api.vehicle.part.AxlePart;
import dev.mcmod.aerocore.api.wheel.WheelCorner;
import dev.mcmod.aerocore.neoforge.vehicle.PartVehicle;
import dev.mcmod.hdfv.drive.TestCars;
import dev.mcmod.hdfv.part.SampleBlueprints;
import dev.mcmod.hdfv.part.chassis.ChassisDesign;
import dev.mcmod.hdfv.part.wheel.AxleDesign;
import dev.mcmod.hdfv.part.wheel.SteerSchedule;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Cars on a chassis and the 0.15 suspensions in the world: a six-wheeler on a ladder frame stands on all six wheels
 * and drives off; with a front wheel on a slab a soft frame twists and keeps the other front wheel loaded where a stiff
 * one lifts it; air springs raise and lower a car on "ride_height"; rear wheels steered against the front turn a car
 * tighter. Each in its own batch: the cars' engine tables are built first.
 */
@PrefixGameTestTemplate(false)
public final class ChassisGameTests {
    private ChassisGameTests() {}

    /** The ids of a blueprint's parts of a kind, in its order. */
    static List<String> ids(VehicleBlueprint b, Class<?> kind) {
        List<String> out = new ArrayList<>();
        for (VehicleBlueprint.Entry e : b.parts()) { if (kind.isInstance(e.design())) { out.add(e.id()); } }
        return out;
    }

    /** A wheel's load (kN, as shown): side 0 left, 1 right. */
    static double load(PartVehicle v, String axle, int side) {
        AxlePart a = SteerSchedule.axleOf(v.vehicle().installed(axle).part());
        return v.vehicleState().shown(axle, a.extraShown() + 2 * side + 1);
    }

    /** A wheel's compression (m from droop, as shown): side 0 left, 1 right. */
    static double compression(PartVehicle v, String axle, int side) { return v.vehicleState().shown(axle, side * WheelCorner.SHOWN + 2); }

    /** The frame's twist between its end axle stations (rad; its internal states are the stations' twists, rear first). */
    static double twist(PartVehicle v, String chassis, int stations) {
        return v.vehicleState().internal(chassis, stations - 1) - v.vehicleState().internal(chassis, 0);
    }

    /** The 6x6 parked: all six wheels carry it (their loads sum to its weight); then in D it drives off. */
    @GameTest(templateNamespace = "hdfv", template = "helipad", timeoutTicks = 2400, batch = "hdfv_chassis_6x6")
    public static void aSixBySixStandsOnSixWheelsAndDrivesOff(GameTestHelper h) {
        double floor = DriveGameTests.floor(h);
        VehicleBlueprint blueprint = TestCars.sixBySix();
        blueprint.compile().warmUp();
        List<String> axles = ids(blueprint, AxleDesign.class);
        PartVehicle v = DriveGameTests.spawn(h, blueprint, 3, 12, floor + 1.0);
        v.setControls(Map.of("selector", -2.0, "throttle", 0.0, "brake", 1.0));
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(v.ready(), "Tables still building"))
                .thenIdle(80)
                .thenExecute(() -> {
                    double sum = 0;
                    StringBuilder loads = new StringBuilder();
                    for (String a : axles) {
                        for (int side = 0; side < 2; side++) {
                            double l = load(v, a, side);
                            loads.append(a).append(side == 0 ? " L " : " R ").append(String.format("%.1f", l)).append("; ");
                            h.assertTrue(l > 1, "every wheel carries the car: " + loads);
                            sum += l;
                        }
                    }
                    double weight = (v.vehicle().properties().massKg() + v.vehicleState().fuelKg()) * 9.80665 / 1000;
                    h.assertTrue(Math.abs(sum - weight) < 0.15 * weight, "the loads sum to the weight " + String.format("%.1f", weight) + " kN: " + loads);
                    com.mojang.logging.LogUtils.getLogger().info("hdfv 6x6 loads: {} weight {} kN", loads, String.format("%.1f", weight));
                    v.setControls(Map.of("selector", 1.0, "throttle", 0.4, "brake", 0.0));
                })
                .thenWaitUntil(() -> h.assertTrue(v.vehicleState().body().velocity().length() > 1.5, "Driving off: " + v.vehicleState().body().velocity().length()))
                .thenSucceed();
    }

    /**
     * Two 6x6s parked with the front left wheel on a slab: the soft frame (open channel rails) twists several times as
     * far as the stiff one (tubes with X-members), and its front right wheel keeps more of its load.
     */
    @GameTest(templateNamespace = "hdfv", template = "helipad", timeoutTicks = 2400, batch = "hdfv_chassis_twist")
    public static void aSoftFrameTwistsOverASlabAndKeepsItsWheelsDown(GameTestHelper h) {
        double floor = DriveGameTests.floor(h);
        PartVehicle[] cars = new PartVehicle[2];
        String[] frames = new String[2];
        String front = null;
        int stations = 0;
        TestCars.Frame[] kinds = {TestCars.Frame.SOFT, TestCars.Frame.STIFF};
        for (int k = 0; k < 2; k++) {
            VehicleBlueprint blueprint = TestCars.sixBySix(kinds[k]);
            blueprint.compile().warmUp();
            frames[k] = ids(blueprint, ChassisDesign.class).getFirst();
            ChassisDesign chassis = (ChassisDesign) blueprint.part(frames[k]).design();
            stations = chassis.axleStations().size();
            // the front left wheel (axle_1, the frontmost, on its left: -z) over the middle of a slab's block
            front = ids(blueprint, AxleDesign.class).getFirst();
            double fx = chassis.mountPoints().get("axle_1").position().x(), half = ((AxleDesign) blueprint.part(front).design()).layout().trackM() / 2;
            int bx = 10, bz = 6 + 12 * k;
            h.setBlock(new BlockPos(bx, 1, bz), Blocks.STONE_SLAB);
            cars[k] = DriveGameTests.spawn(h, blueprint, bx + 0.5 - fx, bz + 0.5 + half, floor + 1.4);
            cars[k].setControls(Map.of("selector", -2.0, "throttle", 0.0, "brake", 1.0));
        }
        String axle = front;
        int n = stations;
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(cars[0].ready() && cars[1].ready(), "Tables still building"))
                .thenIdle(100)
                .thenExecute(() -> {
                    double soft = Math.abs(twist(cars[0], frames[0], n)), stiff = Math.abs(twist(cars[1], frames[1], n));
                    double softRight = load(cars[0], axle, 1), stiffRight = load(cars[1], axle, 1);
                    String said = String.format("twist soft %.2f, stiff %.2f deg; front right wheel soft %.1f, stiff %.1f kN; front left soft %.1f, stiff %.1f kN",
                            Math.toDegrees(soft), Math.toDegrees(stiff), softRight, stiffRight, load(cars[0], axle, 0), load(cars[1], axle, 0));
                    com.mojang.logging.LogUtils.getLogger().info("hdfv chassis twist: {}", said);
                    h.assertTrue(load(cars[0], axle, 0) > 1 && load(cars[1], axle, 0) > 1, "both front left wheels on their slabs: " + said);
                    h.assertTrue(soft > 3 * stiff, "the soft frame twists more: " + said);
                    h.assertTrue(softRight > stiffRight, "the soft frame's front right wheel keeps more load: " + said);
                })
                .thenSucceed();
    }

    /** The air-sprung sedan raised ("ride_height" +0.5: high) and lowered (-0.5): its rear rises and sinks on its air. */
    @GameTest(templateNamespace = "hdfv", template = "helipad", timeoutTicks = 2400, batch = "hdfv_chassis_air")
    public static void airSpringsRaiseAndLowerACar(GameTestHelper h) {
        double floor = DriveGameTests.floor(h);
        VehicleBlueprint blueprint = TestCars.airSedan();
        blueprint.compile().warmUp();
        PartVehicle v = DriveGameTests.spawn(h, blueprint, 5, 12, floor + 0.7);
        v.setControls(Map.of("gear", 0.0, "throttle", 0.0, "brake", 1.0, "ride_height", 0.0));
        double[] start = new double[1];
        java.util.function.Supplier<Double> rear = () -> 0.5 * (compression(v, "rear_axle", 0) + compression(v, "rear_axle", 1));
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(v.ready(), "Tables still building"))
                .thenIdle(100)
                .thenExecute(() -> { start[0] = rear.get(); v.setControl("ride_height", 0.5); })
                .thenWaitUntil(() -> h.assertTrue(rear.get() < start[0] - 0.03, "Rising: " + rear.get() + " from " + start[0]))
                .thenExecute(() -> v.setControl("ride_height", -0.5))
                .thenWaitUntil(() -> h.assertTrue(rear.get() > start[0] + 0.025, "Sinking: " + rear.get() + " from " + start[0]))
                .thenSucceed();
    }

    /** Two sedans at full lock and a little throttle: the one with its rear wheels steered against the front turns tighter. */
    @GameTest(templateNamespace = "hdfv", template = "helipad", timeoutTicks = 1600, batch = "hdfv_chassis_rearsteer")
    public static void rearWheelSteeringTurnsTighter(GameTestHelper h) {
        double floor = DriveGameTests.floor(h);
        VehicleBlueprint plain = SampleBlueprints.sedan(), steered = TestCars.rearSteerSedan();
        plain.compile().warmUp();
        steered.compile().warmUp();
        PartVehicle a = DriveGameTests.spawn(h, plain, 4, 12, floor + 0.7), b = DriveGameTests.spawn(h, steered, 4, 25, floor + 0.7);
        for (PartVehicle v : new PartVehicle[]{a, b}) { v.setControls(Map.of("selector", 1.0, "throttle", 0.15, "steer", 1.0, "brake", 0.0)); }
        java.util.function.Function<PartVehicle, Double> curvature = v -> {
            var body = v.vehicleState().body();
            return body.angularVelocity(v.vehicle().properties()).y() / Math.max(body.velocity().length(), 1e-3);
        };
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(a.ready() && b.ready(), "Tables still building"))
                .thenWaitUntil(() -> h.assertTrue(a.vehicleState().body().velocity().length() > 2 && b.vehicleState().body().velocity().length() > 2, "Moving"))
                .thenIdle(20)
                .thenExecute(() -> {
                    double ka = curvature.apply(a), kb = curvature.apply(b);
                    com.mojang.logging.LogUtils.getLogger().info("hdfv rear steer: radius {} m against {} m", String.format("%.1f", 1 / kb), String.format("%.1f", 1 / ka));
                    h.assertTrue(kb > 1.15 * ka, String.format("rear steering turns tighter: radius %.1f m against %.1f m (at %.1f and %.1f m/s)", 1 / kb, 1 / ka,
                            b.vehicleState().body().velocity().length(), a.vehicleState().body().velocity().length()));
                })
                .thenSucceed();
    }
}
