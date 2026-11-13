package dev.mcmod.hdfv.neoforge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.engine.Blackbody;
import dev.mcmod.aerocore.api.engine.Plume;
import dev.mcmod.aerocore.api.vehicle.Part;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.Vehicle;
import dev.mcmod.aerocore.neoforge.client.PartLayers;
import dev.mcmod.aerocore.neoforge.client.PlumeRenderer;
import dev.mcmod.aerocore.neoforge.vehicle.PartVehicle;
import dev.mcmod.hdfv.look.Finishes;
import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.neoforge.HdfvParticles;
import dev.mcmod.hdfv.neoforge.client.model.PistonEngineModel;
import dev.mcmod.hdfv.part.engine.Exhaust;
import dev.mcmod.hdfv.part.engine.JetEngineDesign;
import dev.mcmod.hdfv.part.engine.PistonEngineDesign;
import dev.mcmod.hdfv.part.engine.PulsejetDesign;
import dev.mcmod.hdfv.part.engine.RamjetDesign;
import dev.mcmod.hdfv.part.engine.ScramjetDesign;
import dev.mcmod.hdfv.part.engine.ShaftTurbineDesign;
import dev.mcmod.hdfv.part.engine.SolidRocketDesign;
import dev.mcmod.hdfv.part.engine.TurboRamjetDesign;
import dev.mcmod.hdfv.part.engine.TurbofanDesign;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Engines' exhaust in the world (0.11), from what each part shows (Exhaust: the gas's temperature, Mach number and
 * pressure at the exit; a piston engine's load):
 * <ul>
 * <li>the plume: AeroCore's Plume gives its shape (fully expanded jet, shock cells, Mach disk, core) and its look by
 * what burns (hydrogen nearly invisible, a lean hydrocarbon flame blue, an afterburner orange, aluminised propellant
 * white, sugar propellant violet), drawn as added light with the flame-noise finish;</li>
 * <li>hot metal: the model's hot faces (nozzles, exhaust pipes) glow as a black body at the metal's temperature, which
 * follows the gas's with a lag (heats in some seconds, cools slower);</li>
 * <li>smoke: a rocket's white cloud and sparks, a jet's faint trail, a diesel's soot under load, a cough when a piston
 * engine catches; at most 64 particles a second an engine, 1500 alive in all.</li>
 * </ul>
 * The metal's temperature (a fraction of the gas's, or the load's) and the smoke's rates are display rules.
 */
@EventBusSubscriber(modid = Hdfv.MOD_ID, value = Dist.CLIENT)
public final class Exhausts {
    private Exhausts() {}

    /** The ambient the shown pressure ratio is over (only ratios matter to the plume's shape). */
    private static final double PA = 101325, TA = 288;
    /** Seconds for hot metal to follow its target: heating, cooling. */
    private static final double HEAT_S = 4, COOL_S = 12;
    /** Where hot metal starts to show a glow, and where it is at its strongest (K). */
    private static final double GLOW_FROM = 760, GLOW_FULL = 1300;
    private static final double RANGE = 128;

    /** Metal temperatures by vehicle and part: [kelvin, nanoTime last asked]. */
    private static final Map<String, double[]> METAL = new HashMap<>();
    /** Smoke still owed by vehicle and part (fractions of a particle), and whether a piston engine ran last tick. */
    private static final Map<String, double[]> OWED = new HashMap<>();
    /** A piston engine's exhaust outlets, from its model. */
    private static final Map<Part, List<Vec3d[]>> OUTLETS = Collections.synchronizedMap(new WeakHashMap<>());
    private static ClientLevel lastLevel;

    // ---------------------------------------------------------------- what burns

    /** What a design burns, for its flame's colour: hydrogen when it takes nothing else, a solid by its propellant. */
    static Plume.Family family(PartDesign d) {
        if (d instanceof SolidRocketDesign s) {
            return s.performance().design().propellant().name().contains("apcp") ? Plume.Family.ALUMINISED_SOLID : Plume.Family.SUGAR_SOLID;
        }
        Set<String> fluids = switch (d) {
            case JetEngineDesign j -> j.acceptedFluids();
            case TurbofanDesign f -> f.control().acceptedFluids();
            case ShaftTurbineDesign s -> s.control().acceptedFluids();
            case TurboRamjetDesign t -> t.control().acceptedFluids();
            case RamjetDesign r -> r.acceptedFluids();
            case ScramjetDesign s -> s.acceptedFluids();
            case PulsejetDesign p -> p.acceptedFluids();
            default -> Set.of();
        };
        boolean hydrogen = !fluids.isEmpty() && fluids.stream().allMatch(f -> f.contains("hydrogen"));
        return hydrogen ? Plume.Family.HYDROGEN : Plume.Family.HYDROCARBON;
    }

    /** The reheat's share a part shows (0 without an afterburner). */
    static double reheat(Part part, PartVehicle v, String id, float pt) {
        PartDesign d = part.design();
        boolean fitted = d instanceof TurboRamjetDesign || d instanceof JetEngineDesign j && j.afterburner().fitted() || d instanceof TurbofanDesign f && f.afterburner().fitted();
        return fitted ? v.shown(id, JetEngineDesign.SHOWN_REHEAT, pt) : 0;
    }

    /** How strong a part's flame is now (0-1), from what it shows; 0 without a plume of its own. */
    static double level(Part part, PartVehicle v, String id, float pt, double kelvin) {
        return switch (part.design()) {
            case RamjetDesign r -> v.shown(id, RamjetDesign.SHOWN_FLAME, pt);
            case ScramjetDesign s -> v.shown(id, RamjetDesign.SHOWN_FLAME, pt);
            case SolidRocketDesign s -> v.shown(id, RamjetDesign.SHOWN_FLAME, pt);
            case PulsejetDesign p -> {
                double hz = v.shown(id, PulsejetDesign.SHOWN_FREQUENCY, pt);
                yield v.shown(id, PulsejetDesign.SHOWN_LIT, pt) > 0.5 ? 0.55 + 0.45 * Math.sin(2 * Math.PI * hz * Util.getMillis() / 1000.0) : 0;
            }
            case ShaftTurbineDesign s -> 0;   // its stacks' gas is barely visible
            default -> {
                double share = reheat(part, v, id, pt);
                yield share > 0.02 ? 0.5 + 0.5 * share : Math.clamp((kelvin - 550) / 650, 0.15, 1);
            }
        };
    }

    // ---------------------------------------------------------------- plume

    /** Draws a burning engine's plume from its nozzle (along -x in the part's frame), as its shown exhaust says. */
    static void plume(Part part, PartVehicle v, String id, float pt, PoseStack stack, PartLayers layers) {
        int first = Exhaust.first(part.design());
        if (first < 0) { return; }
        double kelvin = v.shown(id, first + Exhaust.KELVIN, pt);
        if (!(kelvin > 0)) { return; }
        double level = level(part, v, id, pt, kelvin);
        if (!(level > 0.02)) { return; }
        double mach = v.shown(id, first + Exhaust.MACH, pt), ratio = v.shown(id, first + Exhaust.PRESSURE, pt);
        double[] exit = JetFamilyRenderers.exit(part);
        double g = Exhaust.GAMMA, staticK = kelvin / (1 + 0.5 * (g - 1) * mach * mach);
        Plume.Shape shape = Plume.shape(new Plume.Exit(mach, Math.max(0.05, ratio) * PA, staticK, 2 * exit[1], g, PA, TA));
        Plume.Look look = Plume.look(family(part.design()), kelvin, reheat(part, v, id, pt) > 0.02);
        PlumeRenderer.draw(layers, stack, shape, look, Finishes.FX_FLAME, exit[0], exit[1], level, Util.getMillis() / 1000.0);
    }

    // ---------------------------------------------------------------- hot metal

    /** The temperature a part's hot faces head for now (K): a share of its gas's, a pulsejet's tube, a piston engine's pipes by load. */
    static double metalTarget(Part part, PartVehicle v, String id) {
        PartDesign d = part.design();
        if (d instanceof PistonEngineDesign p) {
            return pistonTarget(p, v.shown(id, PistonEngineDesign.SHOWN_RUNNING, 1f) > 0.5, v.shown(id, PistonEngineDesign.SHOWN_LOAD, 1f));
        }
        int first = Exhaust.first(d);
        if (first < 0) { return TA; }
        double kelvin = v.shown(id, first + Exhaust.KELVIN, 1f);
        if (!(kelvin > 0)) { return TA; }
        return switch (d) {
            case PulsejetDesign p -> 1050;   // the tube of a running pulsejet glows dull red (the V-1's at night)
            case SolidRocketDesign s -> 0.55 * kelvin;
            case RamjetDesign r -> 0.55 * kelvin;
            case ScramjetDesign s -> 0.5 * kelvin;
            default -> 0.75 * kelvin;   // a gas turbine's nozzle and tail cone: most of the gas's
        };
    }

    /** A piston engine's exhaust pipes' temperature at a load (K): a spark engine's gas is hotter than a diesel's. */
    static double pistonTarget(PistonEngineDesign p, boolean running, double load) {
        if (!running) { return TA; }
        double l = Math.clamp(load, 0, 1.4);
        return p.diesel() ? 520 + 330 * l : 560 + 420 * l;
    }

    /** The light a part's hot faces give off now (ARGB, PartRenderer.glow): a black body at the metal's lagging temperature. */
    static int glow(Part part, PartVehicle v, String id, float pt) { return glowAt(v.getId() + ":" + id, metalTarget(part, v, id)); }

    /** The light of hot metal under a key heading for a temperature (K), with its lag. */
    static int glowAt(String key, double target) {
        long now = System.nanoTime();
        double kelvin;
        synchronized (METAL) {
            double[] m = METAL.get(key);
            if (m == null) { m = new double[]{target, now}; METAL.put(key, m); }
            double dt = Math.min(1, (now - (long) m[1]) * 1e-9);
            m[0] += (target - m[0]) * (1 - Math.exp(-dt / (target > m[0] ? HEAT_S : COOL_S)));
            m[1] = now;
            kelvin = m[0];
        }
        if (kelvin < GLOW_FROM) { return 0; }
        int alpha = (int) Math.round(230 * Math.clamp((kelvin - GLOW_FROM) / (GLOW_FULL - GLOW_FROM), 0, 1));
        return Blackbody.argb(kelvin, alpha);
    }

    // ---------------------------------------------------------------- smoke

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level != lastLevel) {   // particles of a level left behind went without being removed
            lastLevel = level;
            ExhaustParticle.LIVE.set(0);
            synchronized (METAL) { METAL.clear(); }
            OWED.clear();
        }
        if (level == null || mc.player == null || mc.isPaused()) { return; }
        long now = System.nanoTime();
        synchronized (METAL) { METAL.values().removeIf(m -> now - (long) m[1] > 30_000_000_000L); }
        var camera = mc.gameRenderer.getMainCamera().getPosition();
        for (PartVehicle v : level.getEntitiesOfClass(PartVehicle.class, mc.player.getBoundingBox().inflate(RANGE))) {
            Vehicle vehicle = v.vehicle();
            if (vehicle == null || v.distanceToSqr(camera) > RANGE * RANGE) { continue; }
            Vec3d drift = new Vec3d(v.getX() - v.xo, v.getY() - v.yo, v.getZ() - v.zo);   // blocks a tick: the smoke leaves with the vehicle's speed
            for (Vehicle.Installed p : vehicle.installed()) {
                PartDesign d = p.part().design();
                if (d instanceof PistonEngineDesign e) { pistonSmoke(level, v, p, e, drift); }
                else if (Exhaust.first(d) >= 0 && !(d instanceof ShaftTurbineDesign)) { jetSmoke(level, v, p, drift); }
            }
            if (v.worstUsed() > DAMAGE_SMOKE_FROM) { damage(level, v, vehicle, drift); }   // 0.25
        }
        standSmoke(level);
        OWED.keySet().removeIf(k -> !k.startsWith("stand") && level.getEntity(Integer.parseInt(k.substring(0, k.indexOf(':')))) == null);
    }

    /** An engine stand seen lately: its pipes' ends and their ways out in the world, its engine, whether it runs and its load. */
    record Stand(List<Vec3d[]> outlets, PistonEngineDesign design, boolean running, double load, long seenNanos) {}
    private static final Map<net.minecraft.core.BlockPos, Stand> STANDS = new HashMap<>();

    /** EngineStandRenderer: a stand drawn this frame (its pipes' ends in the world). */
    static void standSeen(net.minecraft.core.BlockPos pos, List<Vec3d[]> outlets, PistonEngineDesign design, boolean running, double load) {
        synchronized (STANDS) { STANDS.put(pos.immutable(), new Stand(outlets, design, running, load, System.nanoTime())); }
    }

    private static void standSmoke(ClientLevel level) {
        long now = System.nanoTime();
        synchronized (STANDS) {
            STANDS.values().removeIf(s -> now - s.seenNanos() > 2_000_000_000L);
            for (Map.Entry<net.minecraft.core.BlockPos, Stand> e : STANDS.entrySet()) {
                Stand s = e.getValue();
                String key = "stand" + e.getKey().asLong();
                double[] state = OWED.computeIfAbsent(key + ":run", k -> new double[]{s.running() ? 1 : 0});
                boolean caught = s.running() && state[0] < 0.5;
                state[0] = s.running() ? 1 : 0;
                for (Vec3d[] o : s.outlets()) { pistonPuffs(level, key, o[0], o[1], Vec3d.ZERO, s.design(), caught, s.running(), s.load()); }
            }
        }
    }

    private static void jetSmoke(ClientLevel level, PartVehicle v, Vehicle.Installed p, Vec3d drift) {
        Part part = p.part();
        String id = p.id();
        int first = Exhaust.first(part.design());
        double kelvin = v.shown(id, first + Exhaust.KELVIN, 1f);
        if (!(kelvin > 0)) { return; }
        double flame = level(part, v, id, 1f, kelvin);
        Plume.Family family = family(part.design());
        Plume.Look look = Plume.look(family, kelvin, reheat(part, v, id, 1f) > 0.02);
        double[] exit = JetFamilyRenderers.exit(part);
        Vec3d out = v.partDirection(p, new Vec3d(-1, 0, 0), 1f);
        double rate = Math.min(64, 64 * look.smoke() * Math.max(flame, 0.3));
        boolean solid = family == Plume.Family.ALUMINISED_SOLID || family == Plume.Family.SUGAR_SOLID;
        // a motor's smoke billows (its oxide and carbonate particles cool into a cloud many nozzles across); a jet's trail is thin
        float size = (float) (solid ? Math.max(0.3, 6 * exit[1]) : 2.4 * exit[1]), life = solid ? 5f : 2f;
        emit(level, v, p, new Vec3d(exit[0] - 2 * exit[1], 0, 0), exit[1], out.scale(0.35).add(drift), drift, HdfvParticles.SMOKE.get(), rate, size, look.smokeColour(), life, "s");
        if (family == Plume.Family.ALUMINISED_SOLID) {   // burning aluminium drops
            emit(level, v, p, new Vec3d(exit[0], 0, 0), 0.5 * exit[1], out.scale(0.9).add(drift), drift, HdfvParticles.SPARK.get(), 14 * flame, 0.05f, 0xffffe8b0, 0.7f, "k");
        }
    }

    private static void pistonSmoke(ClientLevel level, PartVehicle v, Vehicle.Installed p, PistonEngineDesign e, Vec3d drift) {
        String key = v.getId() + ":" + p.id();
        boolean running = v.shown(p.id(), PistonEngineDesign.SHOWN_RUNNING, 1f) > 0.5;
        double load = v.shown(p.id(), PistonEngineDesign.SHOWN_LOAD, 1f);
        double[] state = OWED.computeIfAbsent(key + ":run", k -> new double[]{running ? 1 : 0});
        boolean caught = running && state[0] < 0.5;
        state[0] = running ? 1 : 0;
        List<Vec3d[]> outlets = OUTLETS.computeIfAbsent(p.part(), part -> List.copyOf(PistonEngineModel.build(e, null, false).exhausts));
        for (Vec3d[] o : outlets) { pistonPuffs(level, key, v.partPoint(p, o[0], 1f), v.partDirection(p, o[1], 1f), drift, e, caught, running, load); }
    }

    /** A piston engine's smoke at a pipe's end (in the world): a cough when it catches, a diesel's soot near full load. */
    private static void pistonPuffs(ClientLevel level, String key, Vec3d at, Vec3d out, Vec3d drift, PistonEngineDesign e, boolean caught, boolean running, double load) {
        if (caught) {   // the cough of an engine catching: unburnt fuel and oil in the first strokes
            emitAt(level, key + ":c", at, 0.02, out.scale(0.12).add(drift), drift, e.diesel() ? HdfvParticles.SOOT.get() : HdfvParticles.SMOKE.get(), 20 * 6, 0.18f,
                    e.diesel() ? 0xc0222222 : 0x909a9a9a, 2.5f);
        }
        if (running && e.diesel() && load > 0.6) {   // a diesel near full load (and while its turbo catches up) puffs soot
            emitAt(level, key + ":d", at, 0.02, out.scale(0.15).add(drift), drift, HdfvParticles.SOOT.get(), 40 * Math.clamp((load - 0.6) / 0.4, 0, 1), 0.14f, 0xb0181818, 2.2f);
        }
    }

    /** 0.25: from this much used an engine's damage smokes (display rules: the rate and the darkness rise with the damage). */
    private static final double DAMAGE_SMOKE_FROM = 0.5;

    /**
     * 0.25: what damage shows: smoke rising from an engine whose ancillaries, engine or hot section are past half used
     * (grey, blacker and thicker toward failure), and fuel dripping under a leaking tank that still holds some.
     */
    private static void damage(ClientLevel level, PartVehicle v, Vehicle vehicle, Vec3d drift) {
        for (Vehicle.Installed p : vehicle.installed()) {
            double worst = 0;
            for (String c : p.part().conditions()) {
                if (c.equals(dev.mcmod.hdfv.damage.Conditions.ANCILLARIES) || c.equals(dev.mcmod.hdfv.damage.Conditions.ENGINE)
                        || c.equals(dev.mcmod.hdfv.damage.Conditions.HOT_SECTION) || c.equals(dev.mcmod.hdfv.damage.Conditions.MOTOR)) {
                    worst = Math.max(worst, v.used(p.id(), c));
                }
            }
            if (!(worst > DAMAGE_SMOKE_FROM)) { continue; }
            double share = Math.clamp((worst - DAMAGE_SMOKE_FROM) / (1 - DAMAGE_SMOKE_FROM), 0, 1);
            int grey = (int) Math.round(0x9a - (0x9a - 0x20) * share), alpha = (int) Math.round(0x70 + 0x60 * share);
            emit(level, v, p, p.part().mass().centre(), 0.15, new Vec3d(0, 0.05, 0).add(drift), drift, HdfvParticles.SMOKE.get(), 3 + 20 * share, (float) (0.35 + 0.35 * share),
                    alpha << 24 | grey << 16 | grey << 8 | grey, 2.8f, "dmg");
        }
        for (int k = 0; k < vehicle.tanks().size(); k++) {
            Vehicle.Tank t = vehicle.tanks().get(k);
            if (t.tank().own() || !(v.tankKg(k) > 0)) { continue; }
            double leak = dev.mcmod.hdfv.damage.EngineWear.leakPerS(dev.mcmod.hdfv.neoforge.damage.VehicleDamage.tankUsed(v, k));
            if (!(leak > 0)) { continue; }
            Vehicle.Installed p = vehicle.installed().get(t.part());
            emit(level, v, p, t.tank().centre(), 0.1, new Vec3d(0, -0.05, 0).add(drift), drift, HdfvParticles.MUD.get(), 4 + 200 * Math.min(leak, 0.05), 0.05f, 0xd0b08a3a, 1.2f,
                    "leak" + k);
        }
    }

    /**
     * Lets out particles at a rate (a second) from a point in a part's frame, scattered over a radius, moving at a
     * velocity (blocks a tick) with some spread; fractions are carried to the next tick.
     */
    private static void emit(ClientLevel level, PartVehicle v, Vehicle.Installed p, Vec3d local, double scatter, Vec3d velocity, Vec3d drift,
                             net.minecraft.core.particles.ParticleType<HdfvParticles.Options> type, double perSecond, float size, int colour, float life, String tag) {
        if (!(perSecond > 0)) { return; }
        emitAt(level, v.getId() + ":" + p.id() + ":" + tag, v.partPoint(p, local, 1f), scatter, velocity, drift, type, perSecond, size, colour, life);
    }

    /** Lets out particles at a rate (a second) from a point in the world, scattered over a radius; fractions are carried by key. */
    private static void emitAt(ClientLevel level, String key, Vec3d point, double scatter, Vec3d velocity, Vec3d drift,
                               net.minecraft.core.particles.ParticleType<HdfvParticles.Options> type, double perSecond, float size, int colour, float life) {
        if (!(perSecond > 0)) { return; }
        double[] owed = OWED.computeIfAbsent(key, k -> new double[1]);
        owed[0] += Math.min(64, perSecond) / 20;
        HdfvParticles.Options options = new HdfvParticles.Options(type, size, colour, life);
        var random = level.random;
        while (owed[0] >= 1) {
            owed[0] -= 1;
            if (ExhaustParticle.LIVE.get() >= ExhaustParticle.BUDGET) { owed[0] = 0; return; }
            Vec3d jitter = new Vec3d((random.nextDouble() - 0.5) * 2 * scatter, (random.nextDouble() - 0.5) * 2 * scatter, (random.nextDouble() - 0.5) * 2 * scatter);
            Vec3d at = point.add(jitter).add(drift.scale(random.nextDouble()));
            double spread = 0.08 * velocity.subtract(drift).length() + 0.01;
            // always visible: a rocket's cloud is seen from far beyond the 32 blocks ordinary particles are kept to
            level.addAlwaysVisibleParticle(options, true, at.x(), at.y(), at.z(), velocity.x() + (random.nextDouble() - 0.5) * spread,
                    velocity.y() + (random.nextDouble() - 0.5) * spread, velocity.z() + (random.nextDouble() - 0.5) * spread);
        }
    }
}
