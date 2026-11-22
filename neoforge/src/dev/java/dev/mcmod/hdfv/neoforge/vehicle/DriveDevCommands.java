package dev.mcmod.hdfv.neoforge.vehicle;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.mcmod.aerocore.api.Orientation;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.vehicle.VehicleBlueprint;
import dev.mcmod.aerocore.neoforge.vehicle.PartVehicle;
import dev.mcmod.aerocore.neoforge.vehicle.VehicleRegistry;
import dev.mcmod.hdfv.drive.TestCars;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.loading.FMLEnvironment;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Development only (0.14), /hdfv dev drive spawn manual|4x4|6x6|rearsteer|air|hardpoints (0.15: the last four), 0.18:
 * tractor|lowloader|semi (the last the two coupled as soon as they are ready, the trailer behind on the tractor's jaw): the test cars
 * (TestCars) for trying the driving keys and dashboard, 4 m ahead facing the way the source looks, as /aerocore vehicle spawn sets a sample down. Not in a
 * production game (they are not samples). 0.19: mount, launch &lt;m/s&gt; and remove act on the test car the player spawned last.
 * 0.25: roofbox (the sedan with a roof rack and a roof box) and containersemi (the semi, a 20 ft container on its low-loader).
 */
public final class DriveDevCommands {
    private DriveDevCommands() {}

    private static final Map<String, Supplier<VehicleBlueprint>> CARS = Map.ofEntries(Map.entry("manual", TestCars::manualSedan), Map.entry("4x4", TestCars::fourByFour),
            Map.entry("6x6", TestCars::sixBySix), Map.entry("rearsteer", TestCars::rearSteerSedan), Map.entry("air", TestCars::airSedan),
            Map.entry("hardpoints", TestCars::hardpointSedan), Map.entry("tractor", TestCars::tractor6x4), Map.entry("lowloader", TestCars::lowLoader),
            Map.entry("semi", TestCars::tractor6x4), Map.entry("roofbox", DriveDevCommands::roofBoxSedan), Map.entry("containersemi", TestCars::tractor6x4));

    /** 0.25: the sample sedan with a roof rack and the sample roof box on it, as CargoGameTests builds it. */
    static VehicleBlueprint roofBoxSedan() {
        var racked = dev.mcmod.hdfv.part.body.SampleBodies.sedan().withKit(dev.mcmod.hdfv.design.RecordEditor.with(dev.mcmod.hdfv.part.body.BodyKitDesign.NONE,
                "roofRack", dev.mcmod.hdfv.part.body.BodyKitDesign.RoofRack.BARS));
        VehicleBlueprint layout = dev.mcmod.hdfv.assembly.SampleLayouts.sedan();
        layout = new VehicleBlueprint("roof box sedan", layout.parts().stream()
                .map(e -> e.id().equals("body") ? new VehicleBlueprint.Entry("body", racked, e.mount()) : e).toList(), java.util.List.of());
        return dev.mcmod.hdfv.assembly.AutoWiring.wire(dev.mcmod.hdfv.assembly.Placing.add(layout, dev.mcmod.hdfv.part.StorageBoxDesign.roofBox()));
    }

    /** 0.25: the low-loader with the sample 20 ft container on its well deck. */
    static VehicleBlueprint containerLowLoader() {
        return dev.mcmod.hdfv.assembly.AutoWiring.wire(dev.mcmod.hdfv.assembly.Placing.add(TestCars.lowLoader(), dev.mcmod.hdfv.part.StorageBoxDesign.container20()));
    }

    /**
     * 0.18: a test car set down parked: its handbrake on and an automatic in P (with no channels an automatic crept off in
     * gear: a tractor left on AutoShot's pad drove off its edge); a driver getting in takes these over.
     */
    public static final java.util.Map<String, Double> PARKED = java.util.Map.of("handbrake", 1.0, "selector", (double) dev.mcmod.hdfv.part.drive.Gearboxes.PARK, "brake", 0.0);

    /** 0.18: how high a blueprint's frame origin is set over the ground: its lowest tyre 5 cm clear of it, at least half a metre. */
    public static double standHeight(VehicleBlueprint b) {
        double lowest = Double.POSITIVE_INFINITY;
        for (var i : b.compile().installed()) {
            if (i.part().design() instanceof dev.mcmod.hdfv.part.wheel.AxleDesign a) {
                lowest = Math.min(lowest, i.mount().apply(new Vec3d(0, -a.layout().hubDropM(), 0)).y() - a.tyre().tyre().radiusM());
            }
        }
        return Double.isFinite(lowest) ? Math.max(0.5, 0.05 - lowest) : 0.5;
    }

    /** 0.18: the low-loader behind a tractor just spawned, its kingpin on the jaw, coupled once both are ready. */
    private static void semiTrailer(CommandSourceStack source, PartVehicle tractor, VehicleBlueprint trailer) {
        var tv = tractor.vehicle();
        var rv = trailer.compile();
        var jaw = dev.mcmod.hdfv.drive.Hitch.jaw(tractor.blueprint(), tv);
        var pin = dev.mcmod.hdfv.drive.Hitch.kingpin(trailer, rv);
        var coupling = dev.mcmod.hdfv.drive.Hitch.coupling(tractor.blueprint(), tv, jaw, trailer, rv, pin);
        var q = tractor.vehicleState().body().orientation();
        Vec3d origin = tractor.vehicleState().body().worldPoint(tv.properties(), coupling.leaderPoint()).subtract(q.rotate(coupling.followerPoint()));
        PartVehicle r = VehicleRegistry.PART_VEHICLE.get().create(source.getLevel());
        if (r == null) { return; }
        r.setBlueprint(trailer, origin, q);
        r.setControls(PARKED);
        if (source.getEntity() != null) { r.setOwner(source.getEntity().getUUID()); }
        source.getLevel().addFreshEntity(r);
        dev.mcmod.hdfv.neoforge.truck.Hitching.coupleWhenReady(tractor, r);
    }

    /** 0.19: the test car each player spawned last. */
    private static final java.util.Map<java.util.UUID, java.util.UUID> LAST = new java.util.concurrent.ConcurrentHashMap<>();

    public static LiteralArgumentBuilder<CommandSourceStack> tree() {
        return Commands.literal("drive").requires(s -> !FMLEnvironment.production)
                .then(Commands.literal("spawn").then(Commands.argument("car", StringArgumentType.word()).executes(c -> {
                    CommandSourceStack source = c.getSource();
                    // 0.26: the test machines (TestMachines) too
                    Supplier<VehicleBlueprint> car = CARS.getOrDefault(StringArgumentType.getString(c, "car"), dev.mcmod.hdfv.drive.TestMachines.ALL.get(StringArgumentType.getString(c, "car")));
                    if (car == null) { source.sendFailure(Component.literal("Test cars: " + CARS.keySet() + "; machines: " + dev.mcmod.hdfv.drive.TestMachines.ALL.keySet())); return 0; }
                    PartVehicle vehicle = VehicleRegistry.PART_VEHICLE.get().create(source.getLevel());
                    if (vehicle == null) { return 0; }
                    double yaw = Math.toRadians(source.getRotation().y);
                    Vec3 at = source.getPosition();
                    // dropped from half a metre (0.15: a car on a frame has its origin at the rails' bottom, its wheels below it;
                    // 0.18: higher where its tyres reach lower, a truck's)
                    VehicleBlueprint blueprint = car.get();
                    Vec3d origin = new Vec3d(at.x, at.y + standHeight(blueprint), at.z).add(new Vec3d(-Math.sin(yaw), 0, Math.cos(yaw)).scale(4));
                    vehicle.setBlueprint(blueprint, origin, Orientation.rotationVector(Vec3d.Y.scale(-(yaw + Math.PI / 2))));
                    vehicle.setControls(PARKED);
                    if (source.getEntity() != null) { vehicle.setOwner(source.getEntity().getUUID()); }
                    source.getLevel().addFreshEntity(vehicle);
                    if (source.getEntity() != null) { LAST.put(source.getEntity().getUUID(), vehicle.getUUID()); }
                    if (StringArgumentType.getString(c, "car").equals("semi")) { semiTrailer(source, vehicle, TestCars.lowLoader()); }
                    if (StringArgumentType.getString(c, "car").equals("containersemi")) { semiTrailer(source, vehicle, containerLowLoader()); }
                    source.sendSuccess(() -> Component.literal("Spawned the test car " + vehicle.blueprint().name()), false);
                    return 1;
                })))
                // 0.19: gets the player into the test car they spawned last (for AutoShot, where /ride would take the nearest vehicle)
                .then(Commands.literal("mount").executes(c -> {
                    PartVehicle v = last(c.getSource());
                    if (v == null) { return 0; }
                    c.getSource().getPlayerOrException().startRiding(v, true);
                    return 1;
                }))
                // 0.19: the last test car sent off along its nose at a speed (m/s), e.g. supersonic past a listener for its
                // boom; and taken away again (wherever it flew, while its chunk is loaded)
                .then(Commands.literal("launch").then(Commands.argument("speed", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0, 3000)).executes(c -> {
                    PartVehicle v = last(c.getSource());
                    if (v == null) { return 0; }
                    if (v.vehicleState() == null) { c.getSource().sendFailure(Component.literal("The test car is not ready yet")); return 0; }
                    var s = v.vehicleState().body();
                    double speed = com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(c, "speed");
                    v.vehicleState().setBody(new dev.mcmod.aerocore.api.RigidBodyState(s.position(), s.orientation().rotate(Vec3d.X).scale(speed), s.orientation(), Vec3d.ZERO));
                    c.getSource().sendSuccess(() -> Component.literal(String.format(java.util.Locale.ROOT, "Launched %s at %.0f m/s from %.1f %.1f %.1f",
                            v.blueprint().name(), speed, s.position().x(), s.position().y(), s.position().z())), false);
                    return 1;
                })))
                .then(Commands.literal("remove").executes(c -> {
                    PartVehicle v = last(c.getSource());
                    if (v == null) { return 0; }
                    var s = v.vehicleState() == null ? null : v.vehicleState().body();
                    String where = s == null ? v.blockPosition().toShortString()
                            : String.format(java.util.Locale.ROOT, "%.1f %.1f %.1f at %.0f m/s", s.position().x(), s.position().y(), s.position().z(), s.velocity().length());
                    v.discard();
                    c.getSource().sendSuccess(() -> Component.literal("Removed the test car " + v.blueprint().name() + " at " + where), false);
                    return 1;
                }));
    }

    /** The test car the source's player spawned last, if it is loaded (else a failure is sent). */
    private static PartVehicle last(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        java.util.UUID id = LAST.get(source.getPlayerOrException().getUUID());
        net.minecraft.world.entity.Entity car = id == null ? null : source.getLevel().getEntity(id);
        if (!(car instanceof PartVehicle v)) { source.sendFailure(Component.literal("No test car spawned by you is here")); return null; }
        return v;
    }
}
