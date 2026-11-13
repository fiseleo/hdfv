package dev.mcmod.hdfv.neoforge.damage;

import dev.mcmod.aerocore.api.Orientation;
import dev.mcmod.aerocore.api.RigidBodyState;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.vehicle.Vehicle;
import dev.mcmod.aerocore.api.vehicle.VehicleBlueprint;
import dev.mcmod.aerocore.neoforge.vehicle.PartVehicle;
import dev.mcmod.aerocore.neoforge.vehicle.VehicleContacts;
import dev.mcmod.aerocore.neoforge.vehicle.VehicleRegistry;
import dev.mcmod.hdfv.damage.Conditions;
import dev.mcmod.hdfv.damage.Elements;
import dev.mcmod.hdfv.damage.Impacts;
import dev.mcmod.hdfv.part.SampleBlueprints;
import dev.mcmod.hdfv.part.engine.PistonEngineDesign;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;

/**
 * 0.25: damage in the world (VehicleDamage): the sample sedan against a wall, hard and gently and with damage off; a
 * blast beside it and its own fuel exploding; lava and water; the wreck rule; those aboard; the operators' command. The
 * numbers the rules give are core's tests' (ConditionsTest, WearTest, ElementsTest); here the world's events reach them.
 * Each in its own batch (the sedan's engine tables are built first; the settings some set are global).
 */
@PrefixGameTestTemplate(false)
public final class DamageGameTests {
    private DamageGameTests() {}

    /** A stone floor over the pad; the height of its top. */
    static double floor(GameTestHelper h) {
        for (int x = 0; x < 28; x++) for (int z = 0; z < 28; z++) { h.setBlock(new BlockPos(x, 0, z), Blocks.STONE); }
        return h.absolutePos(new BlockPos(0, 1, 0)).getY();
    }

    /** A stone wall across the pad at x (its near face at x), four blocks high. */
    static void wall(GameTestHelper h, int x) {
        for (int z = 1; z < 27; z++) for (int y = 1; y <= 4; y++) for (int dx = 0; dx < 2; dx++) { h.setBlock(new BlockPos(x + dx, y, z), Blocks.STONE); }
    }

    /** The sedan set down facing +x with its frame's origin (its tail) at x, z of the pad, a hand's breadth over the floor, in neutral with nothing held. */
    static PartVehicle sedan(GameTestHelper h, double x, double z, double floor) {
        VehicleBlueprint blueprint = SampleBlueprints.sedan();
        blueprint.compile().warmUp();
        BlockPos o = h.absolutePos(BlockPos.ZERO);
        PartVehicle v = VehicleRegistry.PART_VEHICLE.get().create(h.getLevel());
        if (v == null) { throw new IllegalStateException("Vehicle factory failed"); }
        v.setBlueprint(blueprint, new Vec3d(o.getX() + x, floor + 0.1, o.getZ() + z), Orientation.IDENTITY);
        v.setControls(Map.of("selector", 0.0, "throttle", 0.0, "handbrake", 0.0, "brake", 0.0));
        h.getLevel().addFreshEntity(v);
        return v;
    }

    /** Sends a car off along its nose at a speed (m/s). */
    static void launch(PartVehicle v, double speed) {
        RigidBodyState b = v.vehicleState().body();
        v.vehicleState().setBody(new RigidBodyState(b.position(), b.orientation().rotate(Vec3d.X).scale(speed), b.orientation(), b.angularMomentum()));
    }

    static double used(PartVehicle v, String part, String condition) { return v.vehicleState().used(part, condition); }

    static String all(PartVehicle v) {
        StringBuilder out = new StringBuilder();
        for (Vehicle.Installed p : v.vehicle().installed()) {
            for (String c : p.part().conditions()) { out.append(String.format(java.util.Locale.ROOT, " %s.%s %.3f", p.id(), c, used(v, p.id(), c))); }
        }
        return out.toString().trim();
    }

    static void renew(PartVehicle v) {
        for (Vehicle.Installed p : v.vehicle().installed()) { for (String c : p.part().conditions()) { v.setUsed(p.id(), c, 0); } }
    }

    /** The block a part's centre is in. */
    static BlockPos blockOf(PartVehicle v, String part) {
        Vec3d at = VehicleDamage.world(v, v.vehicle().installed(part));
        return BlockPos.containing(at.x(), at.y(), at.z());
    }

    @AfterBatch(batch = "hdfv_damage_crash") public static void afterCrash(ServerLevel level) { VehicleDamage.override(null); }
    @AfterBatch(batch = "hdfv_damage_gentle") public static void afterGentle(ServerLevel level) { VehicleDamage.override(null); }
    @AfterBatch(batch = "hdfv_damage_off") public static void afterOff(ServerLevel level) { VehicleDamage.override(null); }
    @AfterBatch(batch = "hdfv_damage_blast") public static void afterBlast(ServerLevel level) { VehicleDamage.override(null); }
    @AfterBatch(batch = "hdfv_damage_elements") public static void afterElements(ServerLevel level) { VehicleDamage.override(null); }
    @AfterBatch(batch = "hdfv_damage_wreck") public static void afterWreck(ServerLevel level) { VehicleDamage.override(null); }
    @AfterBatch(batch = "hdfv_damage_riders") public static void afterRiders(ServerLevel level) { VehicleDamage.override(null); }

    /**
     * At 15 m/s (54 km/h) into a stone wall: the body's structure loses between a tenth and six tenths (the offline
     * calibration has about three tenths at this speed; the world's contact gives its own impulse and rebound), the front
     * suspension more than the rear, the engine's ancillaries are hit; and a saved copy of the vehicle has the same.
     */
    @GameTest(templateNamespace = "hdfv", template = "helipad", timeoutTicks = 1200, batch = "hdfv_damage_crash")
    public static void aSedanDrivenIntoAWallIsDamagedAndKeepsItWhenSaved(GameTestHelper h) {
        VehicleDamage.override(VehicleDamage.Settings.DEFAULT);
        double floor = floor(h);
        wall(h, 22);
        PartVehicle v = sedan(h, 3, 14, floor);
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(v.ready(), "Tables still building"))
                .thenIdle(30)
                .thenExecute(() -> {
                    h.assertTrue(used(v, "body", Conditions.STRUCTURE) == 0 && used(v, "front_axle", Conditions.SUSPENSION) < 1e-3 && used(v, "engine", Conditions.ANCILLARIES) == 0,
                            "Set down, nothing is damaged: " + all(v));
                    launch(v, 15);
                })
                .thenIdle(60)
                .thenExecute(() -> {
                    double body = used(v, "body", Conditions.STRUCTURE), front = used(v, "front_axle", Conditions.SUSPENSION), rear = used(v, "rear_axle", Conditions.SUSPENSION);
                    System.out.println("hdfv damage: a sedan into a wall at 15 m/s: " + all(v) + "; now at " + v.vehicleState().body().velocity().length() + " m/s");
                    h.assertTrue(body > 0.1 && body < 0.6, "The body's structure: " + body + " (" + all(v) + ")");
                    h.assertTrue(front > 0.1 && front > 3 * rear, "The front suspension took it, the rear little: " + front + ", " + rear);
                    h.assertTrue(used(v, "engine", Conditions.ANCILLARIES) > 0.15, "The engine's ancillaries: " + used(v, "engine", Conditions.ANCILLARIES));
                    h.assertTrue(v.vehicleState().body().velocity().x() < 1 && v.vehicleState().origin().x() + 4.9 < h.absolutePos(BlockPos.ZERO).getX() + 22.5,
                            "The wall stopped it: " + v.vehicleState().origin() + " at " + v.vehicleState().body().velocity());
                    // the clients hear of it in whole per cent, and a saved copy has it to the last digit
                    net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
                    v.saveWithoutId(tag);
                    PartVehicle copy = VehicleRegistry.PART_VEHICLE.get().create(h.getLevel());
                    if (copy == null) { throw new IllegalStateException("Vehicle factory failed"); }
                    copy.load(tag);
                    for (Vehicle.Installed p : v.vehicle().installed()) {
                        for (String c : p.part().conditions()) {
                            h.assertTrue(used(copy, p.id(), c) == used(v, p.id(), c), p.id() + "." + c + " saved: " + used(copy, p.id(), c) + " for " + used(v, p.id(), c));
                        }
                    }
                })
                .thenSucceed();
    }

    /** At 1.8 m/s (6.5 km/h, under the 8 km/h a bumper takes) against the wall: nothing. */
    @GameTest(templateNamespace = "hdfv", template = "helipad", timeoutTicks = 1200, batch = "hdfv_damage_gentle")
    public static void aSedanNudgingAWallIsNotDamaged(GameTestHelper h) {
        VehicleDamage.override(VehicleDamage.Settings.DEFAULT);
        double floor = floor(h);
        wall(h, 12);
        PartVehicle v = sedan(h, 3, 14, floor);
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(v.ready(), "Tables still building"))
                .thenIdle(30)
                .thenExecute(() -> launch(v, 1.8))
                .thenIdle(80)
                .thenExecute(() -> {
                    h.assertTrue(v.vehicleState().origin().x() > h.absolutePos(BlockPos.ZERO).getX() + 5.5, "It rolled to the wall: " + v.vehicleState().origin());
                    for (Vehicle.Installed p : v.vehicle().installed()) {
                        for (String c : p.part().conditions()) {
                            // tyres, an idling engine and its gearbox wear a hair in six seconds; nothing is damaged
                            h.assertTrue(used(v, p.id(), c) < 1e-3, p.id() + "." + c + " after a nudge: " + used(v, p.id(), c));
                        }
                    }
                    h.assertTrue(used(v, "body", Conditions.STRUCTURE) == 0 && used(v, "engine", Conditions.ANCILLARIES) == 0, "Nothing from the wall: " + all(v));
                })
                .thenSucceed();
    }

    /** With damage off in the settings the same hard crash, lava and an hour of weather damage nothing (the parts' own wear aside, which is compiled in). */
    @GameTest(templateNamespace = "hdfv", template = "helipad", timeoutTicks = 1200, batch = "hdfv_damage_off")
    public static void withDamageOffNothingIsDamaged(GameTestHelper h) {
        VehicleDamage.override(VehicleDamage.Settings.OFF);
        double floor = floor(h);
        wall(h, 22);
        PartVehicle v = sedan(h, 3, 14, floor);
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(v.ready(), "Tables still building"))
                .thenIdle(30)
                .thenExecute(() -> launch(v, 15))
                .thenIdle(60)
                .thenExecute(() -> {
                    h.assertTrue(v.vehicleState().body().velocity().x() < 1, "It hit the wall: " + v.vehicleState().body().velocity());
                    BlockPos engine = blockOf(v, "engine");
                    h.getLevel().setBlockAndUpdate(engine, Blocks.LAVA.defaultBlockState());
                    VehicleDamage.elements(v, VehicleDamage.settings(), 5);
                    h.getLevel().setBlockAndUpdate(engine, Blocks.AIR.defaultBlockState());
                    h.assertFalse(VehicleDamage.burning(v), "Lava does not set it burning");
                    h.getLevel().explode(null, v.getX(), v.getY() + 0.5, v.getZ() + 1.8, 4f, Level.ExplosionInteraction.NONE);
                    h.assertTrue(used(v, "body", Conditions.STRUCTURE) == 0 && used(v, "engine", Conditions.ANCILLARIES) == 0 && used(v, "front_axle", Conditions.SUSPENSION) == 0
                            && used(v, "front_axle", Conditions.BRAKES) < 1e-6, "Nothing is damaged: " + all(v));
                    h.assertTrue(v.tankKg(0) > 1, "And its fuel is where it was");
                })
                .thenSucceed();
    }

    /**
     * A block of TNT's blast beside the car damages it; then, its body used past half (its tank leaks), fire sets off the
     * fuel: one explosion that breaks no block, the tanks empty, everything about the car at nine tenths at least, the
     * engine dead and the car burning.
     */
    @GameTest(templateNamespace = "hdfv", template = "helipad", timeoutTicks = 1200, batch = "hdfv_damage_blast")
    public static void aBlastDamagesACarAndItsFuelExplodesWithoutBreakingBlocks(GameTestHelper h) {
        VehicleDamage.override(VehicleDamage.Settings.DEFAULT);
        double floor = floor(h);
        PartVehicle v = sedan(h, 10, 14, floor);
        BlockPos glass = new BlockPos(12, 1, 17);
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(v.ready(), "Tables still building"))
                .thenIdle(30)
                .thenExecute(() -> {
                    h.setBlock(glass, Blocks.GLASS);
                    h.getLevel().explode(null, v.getX(), v.getY() + 0.3, v.getZ() - 1.6, 4f, Level.ExplosionInteraction.NONE);
                    double body = used(v, "body", Conditions.STRUCTURE);
                    System.out.println("hdfv damage: a TNT blast 1.6 m beside a sedan: " + all(v));
                    h.assertTrue(body > 0.05 && body < 0.8, "The blast damaged the body: " + body);
                    h.assertTrue(v.tankKg(0) > 1, "Its fuel is still aboard: " + v.tankKg(0));
                    v.setUsed("body", Conditions.STRUCTURE, 0.6);
                    h.assertTrue(VehicleConditions.leaking(v), "A body used past half leaks its fuel");
                    VehicleDamage.ignite(v, 5);
                    VehicleDamage.elements(v, VehicleDamage.Settings.DEFAULT, 1);
                    h.assertTrue(v.tankKg(0) == 0, "The tank is empty: " + v.tankKg(0));
                    for (Vehicle.Installed p : v.vehicle().installed()) {
                        for (String c : p.part().conditions()) { h.assertTrue(used(v, p.id(), c) >= Elements.BURNT_OUT, p.id() + "." + c + " burnt out: " + used(v, p.id(), c)); }
                    }
                    h.assertTrue(VehicleDamage.burning(v), "It burns");
                    h.assertBlockPresent(Blocks.GLASS, glass);
                    h.assertBlockPresent(Blocks.STONE, new BlockPos(12, 0, 14));
                    // and no second explosion: there is no fuel left
                    VehicleDamage.elements(v, VehicleDamage.Settings.DEFAULT, 1);
                    h.assertBlockPresent(Blocks.GLASS, glass);
                })
                .thenSucceed();
    }

    /**
     * Standing in the weather rusts a little, in steps; lava at a part burns it and sets the car alight, and burning it
     * loses its tyres and wiring; an engine under water drowns, a running one hydrolocks once. (The vehicle's entity type
     * is immune to vanilla's fire: the game's own look finds the lava.)
     */
    @GameTest(templateNamespace = "hdfv", template = "helipad", timeoutTicks = 1200, batch = "hdfv_damage_elements")
    public static void lavaWaterAndWeatherHarmACar(GameTestHelper h) {
        VehicleDamage.override(VehicleDamage.Settings.DEFAULT);
        double floor = floor(h);
        PartVehicle v = sedan(h, 10, 14, floor);
        h.startSequence()
                .thenWaitUntil(() -> {
                    h.assertTrue(v.ready(), "Tables still building");
                    h.assertTrue(v.vehicleState().internal("engine", PistonEngineDesign.STATE_RUNNING) > 0.5, "The engine starts and idles");
                })
                .thenExecute(() -> {
                    // a second standing: exactly new still (corrosion is kept until it makes a step); thirty real days: rust on the body
                    VehicleDamage.elements(v, VehicleDamage.Settings.DEFAULT, 1);
                    h.assertTrue(used(v, "body", Conditions.STRUCTURE) == 0, "A second's rust is not yet a step: " + all(v));
                    VehicleDamage.elements(v, VehicleDamage.Settings.DEFAULT, 30 * 86400);
                    double rust = used(v, "body", Conditions.STRUCTURE);
                    System.out.println("hdfv damage: a sedan thirty real days on the test pad: " + all(v));
                    h.assertTrue(rust >= 0.005 && rust < 0.5, "Thirty days' rust on a painted body: " + rust);
                    h.assertTrue(used(v, "engine", Conditions.ANCILLARIES) < 0.3 && used(v, "front_axle", Conditions.TYRES) < 0.01, "A dry engine is not drowned, tyres do not rust: " + all(v));
                    renew(v);
                    // lava round the engine for a second: its wiring burns and the car is set alight
                    BlockPos engine = blockOf(v, "engine");
                    h.assertFalse(VehicleDamage.burning(v), "Not burning yet");
                    h.getLevel().setBlockAndUpdate(engine, Blocks.LAVA.defaultBlockState());
                    VehicleDamage.elements(v, VehicleDamage.Settings.DEFAULT, 1);
                    h.getLevel().setBlockAndUpdate(engine, Blocks.AIR.defaultBlockState());
                    double ancillaries = used(v, "engine", Conditions.ANCILLARIES);
                    h.assertTrue(ancillaries > 0.3 && ancillaries < 0.5, "A second of lava on the engine: " + all(v));
                    h.assertTrue(VehicleDamage.burning(v), "The lava set it alight");
                    // (the front axle's centre is in the engine's block: its tyres burnt with it)
                    h.assertTrue(used(v, "rear_axle", Conditions.TYRES) == 0 && used(v, "body", Conditions.STRUCTURE) == 0, "It has not burnt anywhere else yet: " + all(v));
                    // three seconds burning: tyres, wiring, and the body more slowly
                    VehicleDamage.elements(v, VehicleDamage.Settings.DEFAULT, 3);
                    double tyres = used(v, "rear_axle", Conditions.TYRES), body = used(v, "body", Conditions.STRUCTURE);
                    h.assertTrue(Math.abs(tyres - 3 * Conditions.FIRE.get(Conditions.TYRES)) < 1e-9 && body > 0.005 && body < tyres, "Burning: " + all(v));
                    VehicleDamage.ignite(v, 0);
                    h.assertFalse(VehicleDamage.burning(v), "Put out");
                    ancillaries = used(v, "engine", Conditions.ANCILLARIES);
                    h.assertTrue(ancillaries < 0.5 && v.vehicleState().internal("engine", PistonEngineDesign.STATE_RUNNING) > 0.5, "The engine still runs: " + all(v));
                    // water over the engine
                    Vec3d at = VehicleDamage.world(v, v.vehicle().installed("engine"));
                    h.assertTrue(VehicleDamage.depth(h.getLevel(), at) == 0, "The engine is dry");
                    h.getLevel().setBlockAndUpdate(engine, Blocks.WATER.defaultBlockState());
                    h.getLevel().setBlockAndUpdate(engine.above(), Blocks.WATER.defaultBlockState());
                    h.assertTrue(VehicleDamage.depth(h.getLevel(), at) > 0.5, "The engine is under water: " + VehicleDamage.depth(h.getLevel(), at));
                    double wear = used(v, "engine", Conditions.ENGINE);
                    VehicleDamage.elements(v, VehicleDamage.Settings.DEFAULT, 4);
                    h.assertTrue(Math.abs(used(v, "engine", Conditions.ENGINE) - wear - Elements.HYDROLOCK) < 1e-9, "A running engine going under hydrolocks: " + all(v));
                    h.assertTrue(Math.abs(used(v, "engine", Conditions.ANCILLARIES) - ancillaries - 4 * Elements.DROWNING_PER_S) < 2e-3, "and its ancillaries drown: " + all(v));
                    VehicleDamage.elements(v, VehicleDamage.Settings.DEFAULT, 20);
                    h.assertTrue(Math.abs(used(v, "engine", Conditions.ENGINE) - wear - Elements.HYDROLOCK) < 1e-9, "Once: " + all(v));
                    h.assertTrue(used(v, "engine", Conditions.ANCILLARIES) >= 1, "Twenty seconds more and it is dead: " + all(v));
                    h.getLevel().setBlockAndUpdate(engine, Blocks.AIR.defaultBlockState());
                    h.getLevel().setBlockAndUpdate(engine.above(), Blocks.AIR.defaultBlockState());
                })
                .thenIdle(40)
                .thenExecute(() -> h.assertTrue(v.vehicleState().internal("engine", PistonEngineDesign.STATE_RUNNING) < 0.5, "The drowned engine has stopped"))
                .thenSucceed();
    }

    private static int command(GameTestHelper h, PartVehicle v, String command) {
        var server = h.getLevel().getServer();
        int[] result = {0};
        var source = server.createCommandSourceStack().withLevel(h.getLevel()).withPosition(v.position()).withSuppressedOutput().withCallback((success, value) -> result[0] = success ? value : -1);
        server.getCommands().performPrefixedCommand(source, command);
        return result[0];
    }

    /**
     * The operators' command sets and repairs conditions; a body's structure used up makes a wreck (its engine's
     * ancillaries fail with it and it stops), and repairing the lot gives a car that runs again.
     */
    @GameTest(templateNamespace = "hdfv", template = "helipad", timeoutTicks = 1600, batch = "hdfv_damage_wreck")
    public static void aWreckedBodyTakesItsEngineWithItAndTheCommandSetsAndRepairs(GameTestHelper h) {
        VehicleDamage.override(VehicleDamage.Settings.DEFAULT);
        double floor = floor(h);
        PartVehicle v = sedan(h, 10, 14, floor);
        String[] fuel = {""};
        h.startSequence()
                .thenWaitUntil(() -> {
                    h.assertTrue(v.ready(), "Tables still building");
                    h.assertTrue(v.vehicleState().internal("engine", PistonEngineDesign.STATE_RUNNING) > 0.5, "The engine starts and idles");
                })
                .thenExecute(() -> {
                    fuel[0] = v.tankFuel(0);
                    h.assertTrue(!fuel[0].isEmpty() && v.tankKg(0) > 1, "It has fuel: " + v.tankKg(0) + " kg of " + fuel[0]);
                    h.assertTrue(command(h, v, "hdfv vehicle condition") == 5, "The report lists the sedan's five parts");
                    h.assertTrue(command(h, v, "hdfv vehicle condition set front_axle brakes 0.85") == 1, "Set");
                    h.assertTrue(used(v, "front_axle", Conditions.BRAKES) == 0.85, "The brakes are 85 % used: " + all(v));
                    h.assertTrue(command(h, v, "hdfv vehicle condition set engine no_such_thing 0.5") <= 0, "An unknown condition is refused");
                    VehicleConditions.Warnings w = VehicleConditions.warnings(v);
                    h.assertTrue(w.brakes() && !w.engine() && !w.structure() && !w.tyres() && !w.leak(), "The dashboard warns of the brakes only: " + w);
                    h.assertTrue(command(h, v, "hdfv vehicle condition set body structure 1") == 1, "Set");
                })
                .thenIdle(25)   // the wreck rule is looked at once a second
                .thenExecute(() -> {
                    h.assertTrue(used(v, "engine", Conditions.ANCILLARIES) == 1, "A wreck's engine has failed: " + all(v));
                    VehicleConditions.Warnings w = VehicleConditions.warnings(v);
                    h.assertTrue(w.engine() && w.structure(), "The dashboard says so: " + w);
                    h.assertTrue(v.tankKg(0) == 0, "The tank in a body used up has run dry: " + v.tankKg(0));
                })
                .thenIdle(40)
                .thenExecute(() -> {
                    h.assertTrue(v.vehicleState().internal("engine", PistonEngineDesign.STATE_RUNNING) < 0.5, "and has stopped");
                    h.assertTrue(command(h, v, "hdfv vehicle condition repair") >= 3, "Repaired");
                    h.assertTrue(v.vehicleState().worstUsed() == 0, "Everything is new: " + all(v));
                    h.assertFalse(VehicleConditions.warnings(v).any(), "No warnings");
                    v.setTank(0, fuel[0], 20, dev.mcmod.hdfv.neoforge.fuel.Fuelling.density(fuel[0]));
                })
                .thenWaitUntil(() -> h.assertTrue(v.vehicleState().internal("engine", PistonEngineDesign.STATE_RUNNING) > 0.5, "The repaired engine starts again"))
                .thenExecute(() -> h.assertTrue(used(v, "engine", Conditions.ANCILLARIES) == 0, "and the wreck rule leaves it alone: " + all(v)))
                .thenSucceed();
    }

    /** A player of the test's own that never joins (see TutorialGameTests), past the three seconds a new player cannot be hurt. */
    private static ServerPlayer player(GameTestHelper h) {
        ServerPlayer p = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "hdfv_crash"),
                net.minecraft.server.level.ClientInformation.createDefault());
        net.neoforged.fml.util.ObfuscationReflectionHelper.setPrivateValue(ServerPlayer.class, p, 0, "spawnInvulnerableTime");
        return p;
    }

    /**
     * Those aboard. Two sedans, a (sturdy) pig in each: the one driven into the wall at 15 m/s hurts its pig, the one
     * nudging it does not. And a player aboard (one that never joined: it cannot ride through a tick, so the
     * crash is the event AeroCore posts for one, with the impulse of the car stopping from 12 m/s and from 2 m/s): hurt
     * by the hard one by what Impacts.injury gives, not by the gentle one.
     */
    @GameTest(templateNamespace = "hdfv", template = "helipad", timeoutTicks = 1200, batch = "hdfv_damage_riders")
    public static void thoseAboardAreHurtByAHardCrashOnly(GameTestHelper h) {
        VehicleDamage.override(VehicleDamage.Settings.DEFAULT);
        double floor = floor(h);
        wall(h, 22);
        PartVehicle hard = sedan(h, 3, 8, floor), gentle = sedan(h, 12, 20, floor);
        Pig[] pigs = new Pig[2];
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(hard.ready() && gentle.ready(), "Tables still building"))
                .thenIdle(30)
                .thenExecute(() -> {
                    PartVehicle[] cars = {hard, gentle};
                    for (int k = 0; k < 2; k++) {
                        Pig pig = EntityType.PIG.create(h.getLevel());
                        if (pig == null) { throw new IllegalStateException("No pig"); }
                        pig.setNoAi(true);
                        pig.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(60);   // it is to live through it
                        pig.setHealth(60);
                        pig.setPos(cars[k].getX(), cars[k].getY() + 1, cars[k].getZ());
                        h.getLevel().addFreshEntity(pig);
                        h.assertTrue(pig.startRiding(cars[k], true), "The pig gets in");
                        pigs[k] = pig;
                    }
                    launch(hard, 15);
                    launch(gentle, 1.8);
                })
                .thenIdle(80)
                .thenExecute(() -> {
                    System.out.println("hdfv damage: pigs aboard after a 15 m/s and a 1.8 m/s crash: " + pigs[0].getHealth() + ", " + pigs[1].getHealth() + " of " + pigs[0].getMaxHealth());
                    h.assertTrue(pigs[0].isAlive() && pigs[0].getHealth() < pigs[0].getMaxHealth() - 1, "The hard crash hurt its pig: " + pigs[0].getHealth());
                    h.assertTrue(pigs[1].getHealth() == pigs[1].getMaxHealth(), "The nudge did not: " + pigs[1].getHealth());
                    // a player, within this tick
                    ServerPlayer player = player(h);
                    player.setPos(gentle.getX(), gentle.getY() + 1, gentle.getZ());
                    try { player.startRiding(gentle, true); } catch (NullPointerException noConnection) { /* aboard already; the teleport packet had nowhere to go */ }
                    try {
                        h.assertTrue(gentle.getPassengers().contains(player), "The player is aboard");
                        double mass = gentle.vehicle().properties().massKg() + gentle.vehicleState().fuelKg();
                        Vec3d nose = new Vec3d(gentle.getX() + 2, gentle.getY(), gentle.getZ());
                        NeoForge.EVENT_BUS.post(new VehicleContacts.Contacted(gentle, new VehicleContacts.Impact(nose, mass * 2, 2, 0, false), null));
                        h.assertTrue(player.getHealth() == 20, "A 2 m/s bump does not hurt: " + player.getHealth());
                        double body = used(gentle, "body", Conditions.STRUCTURE), expected = Impacts.injury(mass * 12, mass, body);
                        // (a player with no connection is hurt, and then has nowhere to send the screen's tilt)
                        try { NeoForge.EVENT_BUS.post(new VehicleContacts.Contacted(gentle, new VehicleContacts.Impact(nose, mass * 12, 12, 0, false), null)); }
                        catch (NullPointerException noConnection) { /* hurt already */ }
                        h.assertTrue(expected > 1 && expected < 6 && Math.abs(20 - player.getHealth() - expected) < 0.01, "Stopping from 12 m/s costs " + expected + ": " + player.getHealth());
                        h.assertTrue(used(gentle, "body", Conditions.STRUCTURE) > body + 0.05, "and the car is damaged too: " + all(gentle));
                    } finally {
                        try { player.stopRiding(); } catch (NullPointerException noConnection) { /* off already */ }
                        if (gentle.getPassengers().contains(player)) { gentle.discard(); }
                    }
                })
                .thenSucceed();
    }
}
