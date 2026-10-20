package dev.mcmod.hdfv.neoforge.client;

import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.vehicle.Vehicle;
import dev.mcmod.aerocore.api.vehicle.part.AxlePart;
import dev.mcmod.aerocore.api.vehicle.part.LegPart;
import dev.mcmod.aerocore.api.wheel.WheelCorner;
import dev.mcmod.aerocore.neoforge.client.GroundMarks;
import dev.mcmod.aerocore.neoforge.client.RotorWash;
import dev.mcmod.aerocore.neoforge.client.SprayRenderer;
import dev.mcmod.aerocore.neoforge.vehicle.PartVehicle;
import dev.mcmod.hdfv.look.GroundEffects;
import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.neoforge.HdfvParticles;
import dev.mcmod.hdfv.part.air.RotorDesign;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The wheels' and rotors' effects on the ground (0.12), each client tick for the vehicles near the camera: for every
 * wheel touching the ground (its load shown over GroundEffects.MIN_LOAD_N), what GroundEffects gives for the block under
 * it, its slip speed and load (AeroCore's shown values) and the vehicle's speed: tyre smoke, dust (hdfv's cloud in the
 * block's colour and some of the block's own particles), thrown mud, powder snow, road spray, and its mark (AeroCore's
 * GroundMarks, lifted when it leaves the ground or stops marking). A wheel touching down fast spins up sliding: a
 * puff of what the ground gives at that slip. A wheel running through water faster than 2 m/s throws spray
 * (SprayRenderer); a helicopter rotor's wash blows dust or spray off what is under it (RotorWash, its induced velocity
 * shown). hdfv's particles keep to ExhaustParticle's budget.
 */
@EventBusSubscriber(modid = Hdfv.MOD_ID, value = Dist.CLIENT)
public final class WheelEffects {
    private WheelEffects() {}

    /** How near the camera a wheel's effects are made (m), a rotor's wash. */
    static final double RANGE = 64, WASH_RANGE = 96;
    /** A touch-down is a wheel landing this fast (m/s) after a moment in the air. */
    static final double TOUCHDOWN_MPS = 12;

    private static final Map<String, double[]> OWED = new HashMap<>();
    /** Each wheel's load the tick before (N), by key: a touch-down is from under MIN_LOAD_N. */
    private static final Map<String, Double> LOAD = new HashMap<>();
    private static ClientLevel lastLevel;

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level != lastLevel) { lastLevel = level; OWED.clear(); LOAD.clear(); }
        if (level == null || mc.player == null || mc.isPaused()) { return; }
        var camera = mc.gameRenderer.getMainCamera().getPosition();
        Set<String> seen = new HashSet<>();
        for (PartVehicle v : level.getEntitiesOfClass(PartVehicle.class, mc.player.getBoundingBox().inflate(WASH_RANGE))) {
            Vehicle vehicle = v.vehicle();
            if (vehicle == null) { continue; }
            double distance = Math.sqrt(v.distanceToSqr(camera));
            // blocks a tick, and m/s
            Vec3d drift = new Vec3d(v.getX() - v.xo, v.getY() - v.yo, v.getZ() - v.zo);
            double speed = drift.length() * 20;
            for (Vehicle.Installed p : vehicle.installed()) {
                String id = p.id();
                AxlePart axle = dev.mcmod.hdfv.part.wheel.SteerSchedule.axleOf(p.part());
                if (distance < RANGE && axle != null) {
                    corner(level, v, p, axle.left(), 0, axle.extraShown(), drift, speed, v.getId() + ":" + id + ":l", seen);
                    corner(level, v, p, axle.right(), WheelCorner.SHOWN, axle.extraShown() + WheelCorner.EXTRA, drift, speed, v.getId() + ":" + id + ":r", seen);
                } else if (distance < RANGE && p.part() instanceof LegPart leg) {
                    corner(level, v, p, leg.wheel(), 0, WheelCorner.SHOWN, drift, speed, v.getId() + ":" + id, seen);
                } else if (p.part().design() instanceof RotorDesign r && p.part().shownValues() > 6) {
                    Vec3d hub = v.partPoint(p, Vec3d.ZERO, 1f), down = v.partDirection(p, new Vec3d(0, -1, 0), 1f);
                    RotorWash.tick(level, new Vec3(hub.x(), hub.y(), hub.z()), new Vec3(down.x(), down.y(), down.z()), r.blades().radiusM(), v.shown(id, 6, 1f), level.random);
                }
            }
        }
        // wheels gone (out of range, their vehicle removed): their marks end, their counts go
        for (String key : Set.copyOf(LOAD.keySet())) {
            if (!seen.contains(key)) { LOAD.remove(key); GroundMarks.lift(key); OWED.keySet().removeIf(k -> k.startsWith(key + "#")); }
        }
    }

    /** One wheel: its shown compression at an index, slip and load at another. */
    private static void corner(ClientLevel level, PartVehicle v, Vehicle.Installed p, WheelCorner wheel, int shown, int extra, Vec3d drift, double speed,
                               String key, Set<String> seen) {
        String id = p.id();
        if (p.part().shownValues() <= extra + 1) { return; }
        seen.add(key);
        double compression = v.shown(id, shown + 2, 1f), slip = v.shown(id, extra, 1f), load = v.shown(id, extra + 1, 1f) * 1000;
        double before = LOAD.getOrDefault(key, 0.0);
        LOAD.put(key, load);
        double radius = wheel.spec().tyre().radiusM();
        Vec3d contact = v.partPoint(p, wheel.hub(compression).subtract(new Vec3d(0, radius, 0)), 1f);
        BlockPos below = BlockPos.containing(contact.x(), contact.y() - 0.05, contact.z());
        // in water: spray once the wheel runs through it fast
        if (!level.getFluidState(BlockPos.containing(contact.x(), contact.y() + 0.05, contact.z())).isEmpty()) {
            GroundMarks.lift(key);
            if (speed > 2) {
                int n = (int) Math.min(8, speed);
                for (int i = 0; i < n; i++) {
                    SprayRenderer.emit(contact.x(), contact.y() + 0.1, contact.z(), drift.x() * 10 + (level.random.nextDouble() - 0.5) * 2,
                            0.5 + speed * 0.12 * level.random.nextDouble(), drift.z() * 10 + (level.random.nextDouble() - 0.5) * 2, contact.y() - 0.2);
                }
            }
            return;
        }
        BlockState state = level.getBlockState(below);
        GroundEffects.Ground ground = GroundEffects.ground(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
        boolean wet = level.isRainingAt(below.above());
        GroundEffects.Effect e = GroundEffects.of(ground, wet, slip, load, speed);
        if (e.markArgb() != 0) {
            double width = wheel.spec().tyre().spec().sectionWidthM();
            GroundMarks.mark(key, new Vec3(contact.x(), below.getY() + 1.0, contact.z()), Math.max(0.08, 0.85 * width), e.markArgb(), e.markLifeS());
        } else {
            GroundMarks.lift(key);
        }
        // a touch-down: the wheel spins up sliding over the ground
        double burst = 0;
        if (before < GroundEffects.MIN_LOAD_N && load >= GroundEffects.MIN_LOAD_N && speed > TOUCHDOWN_MPS) {
            GroundEffects.Effect spin = GroundEffects.of(ground, wet, speed, Math.max(load, GroundEffects.LOAD_N), speed);
            if (spin.any()) { e = spin; burst = 0.6; }
        }
        if (!e.any()) { return; }
        double r = wheel.spec().tyre().spec().sectionWidthM();
        Vec3d at = contact.add(new Vec3d(0, 0.08, 0));
        Vec3d slow = drift.scale(0.3);
        emit(level, key + "#t", at, r, slow.add(new Vec3d(0, 0.015, 0)), HdfvParticles.TYRE_SMOKE.get(), e.smoke(), burst, (float) Math.max(0.5, 2.5 * radius), 0xc8e9e9e9, 3f);
        if (e.dust() > 0) {
            int colour = state.getMapColor(level, below).col;
            emit(level, key + "#d", at, r, drift.scale(0.4).add(new Vec3d(0, 0.03, 0)), HdfvParticles.DUST.get(), e.dust(), burst, (float) Math.max(0.4, 2 * radius),
                    0x90000000 | colour, 2.5f);
            if (level.random.nextDouble() < Math.min(1, 0.3 * e.dust() / 20)) {
                level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state), at.x(), at.y(), at.z(), drift.x() * 0.5, 0.12, drift.z() * 0.5);
            }
        }
        emit(level, key + "#m", at, r, drift.scale(0.5).add(new Vec3d(0, 0.18, 0)), HdfvParticles.MUD.get(), e.mud(), burst, 0.07f, 0xff3b2a1a, 1.2f);
        emit(level, key + "#s", at, r, drift.scale(0.4).add(new Vec3d(0, 0.06, 0)), HdfvParticles.SNOW_PUFF.get(), e.snow(), burst, (float) Math.max(0.3, 1.6 * radius),
                0xd8ffffff, 1.6f);
        emit(level, key + "#w", at.add(new Vec3d(0, 0.1, 0)), r, drift.scale(0.6).add(new Vec3d(0, 0.04, 0)), HdfvParticles.ROAD_SPRAY.get(), e.spray(), burst,
                (float) Math.max(0.5, 2.5 * radius), 0x70d8dde2, 1.0f);
    }

    /**
     * Particles at a rate (a second; at least a burst's share of a second's worth at once) near a point, moving at a
     * velocity (blocks a tick) with some spread; fractions are carried by key.
     */
    private static void emit(ClientLevel level, String key, Vec3d point, double scatter, Vec3d velocity,
                             net.minecraft.core.particles.ParticleType<HdfvParticles.Options> type, double perSecond, double burst, float size, int colour, float life) {
        if (!(perSecond > 0)) { return; }
        double[] owed = OWED.computeIfAbsent(key, k -> new double[1]);
        owed[0] += Math.min(64, perSecond) * Math.max(1 / 20.0, burst);
        HdfvParticles.Options options = new HdfvParticles.Options(type, size, colour, life);
        var random = level.random;
        while (owed[0] >= 1) {
            owed[0] -= 1;
            if (ExhaustParticle.LIVE.get() >= ExhaustParticle.BUDGET) { owed[0] = 0; return; }
            double spread = 0.04 + 0.1 * velocity.length();
            level.addParticle(options, point.x() + (random.nextDouble() - 0.5) * scatter, point.y() + random.nextDouble() * 0.5 * scatter,
                    point.z() + (random.nextDouble() - 0.5) * scatter, velocity.x() + (random.nextDouble() - 0.5) * spread,
                    velocity.y() + random.nextDouble() * 0.5 * spread, velocity.z() + (random.nextDouble() - 0.5) * spread);
        }
    }
}
