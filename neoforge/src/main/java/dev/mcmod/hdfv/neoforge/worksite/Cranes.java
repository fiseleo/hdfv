package dev.mcmod.hdfv.neoforge.worksite;

import dev.mcmod.aerocore.api.Orientation;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.earthmoving.Material;
import dev.mcmod.aerocore.api.vehicle.Coupling;
import dev.mcmod.aerocore.api.vehicle.MassSpec;
import dev.mcmod.aerocore.api.vehicle.Mount;
import dev.mcmod.aerocore.api.vehicle.Vehicle;
import dev.mcmod.aerocore.api.vehicle.VehicleBlueprint;
import dev.mcmod.aerocore.neoforge.vehicle.PartVehicle;
import dev.mcmod.aerocore.neoforge.vehicle.VehicleRegistry;
import dev.mcmod.hdfv.drive.Dashboard;
import dev.mcmod.hdfv.part.BoxDesign;
import dev.mcmod.hdfv.part.machine.HookDesign;
import dev.mcmod.hdfv.part.machine.HydraulicMotorDesign;
import dev.mcmod.hdfv.part.machine.LinkDesign;
import dev.mcmod.hdfv.part.machine.SampleMachinery;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * 0.26: a crane's hook and what it lifts (the user: hooks lift blocks, vehicles, containers, large parts). A machine
 * with a winch (a hydraulic motor with a drum) and a boom gets its hook block as a vehicle of its own, hung on a rope
 * (AeroCore's ROPE coupling) reeved over its boom's tip ("tip" of the outermost boom link) to the winch: the winch's
 * drum gives the rope its length, the rope's pull comes back to the winch (rope_tension.&lt;winch&gt;). The hook is the
 * machine's first HookDesign part when it has one laid out (else the sample 40 t block), spawned below the tip.
 * <p>
 * The hook key ("hook", a toggle) latches what is under the hook's "load" point within LATCH_M: another vehicle, slung
 * on a short rope; or a block (not a block entity, not undiggable), lifted as a hung load (a box of the block's mass:
 * its material's bank density, a metre cube, or SOLID_KG_PER_M3), set back as the block where it is let down. The
 * dashboard shows the hook's load and the load moment limiter's share: the hook's pull over the hook block's rating
 * (a rule: the rating is the chart's at the least radius, so this is the share at best).
 */
public final class Cranes {
    private Cranes() {}

    public static final double LATCH_M = 1.5, SLING_M = 1.0, ROPE_STIFFNESS_N = 5e6, ROPE_DAMPING_NS = 2e5, SOLID_KG_PER_M3 = 2000;
    /** A hung block's blueprint name starts so (the block's id follows). */
    public static final String HUNG = "hung:";

    /** A crane's hoisting gear: the winch part, the boom part whose tip carries the sheave, the hook design. */
    record Gear(String winch, String sheave, HookDesign hook) {}

    /** The crane gear of a vehicle, or null when it is not a crane. */
    static Gear gear(Vehicle vehicle) {
        String winch = null, sheave = null;
        HookDesign hook = null;
        for (Vehicle.Installed p : vehicle.installed()) {
            if (p.part().design() instanceof HydraulicMotorDesign m && m.winch() && winch == null) { winch = p.id(); }
            if (p.part().design() instanceof HookDesign h && hook == null) { hook = h; }
            if (p.part().design() instanceof LinkDesign l && (l.linkKind() == LinkDesign.LinkKind.BOOM || l.linkKind() == LinkDesign.LinkKind.TELESCOPIC
                    || l.linkKind() == LinkDesign.LinkKind.LATTICE) && !carriesBoom(vehicle, p)) { sheave = p.id(); }
        }
        return winch == null || sheave == null ? null : new Gear(winch, sheave, hook == null ? SampleMachinery.craneHook40t() : hook);
    }

    private static boolean carriesBoom(Vehicle vehicle, Vehicle.Installed p) {
        for (Vehicle.Installed o : vehicle.installed()) {
            if (o != p && p.id().equals(o.mount().attachPart()) && o.part().design() instanceof LinkDesign l
                    && (l.linkKind() == LinkDesign.LinkKind.BOOM || l.linkKind() == LinkDesign.LinkKind.TELESCOPIC || l.linkKind() == LinkDesign.LinkKind.LATTICE)) {
                return true;
            }
        }
        return false;
    }

    /** The crane's hook vehicle (its follower on a reeved rope), or null. */
    static PartVehicle hook(PartVehicle crane) {
        for (PartVehicle f : crane.followers()) { if (f.reeving() != null) { return f; } }
        return null;
    }

    /** Each step of a machine (WorldWork's listener): hangs its hook, latches and lets go, reads the load. */
    public static void stepped(PartVehicle v) {
        if (!(v.level() instanceof ServerLevel level) || v.vehicle() == null || v.vehicleState() == null) { return; }
        Gear g = gear(v.vehicle());
        if (g == null) { return; }
        PartVehicle hook = hook(v);
        if (hook == null) {
            if (level.getGameTime() % 20 == 0 && v.ready()) { hang(level, v, g); }
            return;
        }
        sling(level, hook);
        latch(level, v, hook, v.controls().getOrDefault("hook", 0.0) > 0.5);
        double pull = v.controls().getOrDefault(Coupling.tensionChannel(g.winch()), 0.0);
        setIfChanged(v, Dashboard.HUD_HOOK, Math.round(pull / 9.80665 / 10) * 10.0);
        setIfChanged(v, Dashboard.HUD_LMI, Math.round(100 * pull / (9.80665 * g.hook().ratedLoadKg())) / 100.0);
    }

    /** The outermost boom's tip in the crane's frame now. */
    static Vec3d tip(PartVehicle crane, String sheave) {
        Vehicle.Installed p = crane.vehicle().installed(sheave);
        var points = p.part().mountPoints();
        Mount m = points.get("tip");
        return crane.vehicleState().pose().apply(p.index(), m == null ? Vec3d.ZERO : m.position());
    }

    /** Spawns the hook block under the boom's tip, hung on its rope. */
    static void hang(ServerLevel level, PartVehicle crane, Gear g) {
        PartVehicle hook = VehicleRegistry.PART_VEHICLE.get().create(level);
        if (hook == null) { return; }
        Vec3d tipLocal = tip(crane, g.sheave());
        Vec3d tipWorld = crane.vehicleState().body().worldPoint(crane.vehicleState().properties(), tipLocal);
        double drop = 3;
        VehicleBlueprint b = dev.mcmod.hdfv.assembly.AutoWiring.wire(new VehicleBlueprint("hook", List.of(new VehicleBlueprint.Entry("hook", g.hook(), Mount.ORIGIN)), List.of()));
        hook.setBlueprint(b, tipWorld.subtract(new Vec3d(0, drop, 0)), Orientation.IDENTITY);
        level.addFreshEntity(hook);
        hook.setReeving(new Coupling.Reeving(g.sheave(), "tip", g.winch()));
        crane.couple(hook, Coupling.rope(tipLocal, Vec3d.ZERO, drop, ROPE_STIFFNESS_N, ROPE_DAMPING_NS));
    }

    /** Latches what is under the hook while the hook key is on, lets it go when it goes off. */
    static void latch(ServerLevel level, PartVehicle crane, PartVehicle hook, boolean on) {
        if (hook.vehicle() == null || hook.vehicleState() == null) { return; }
        PartVehicle held = hook.followers().isEmpty() ? null : hook.followers().getFirst();
        if (!on) {
            if (held != null) {
                held.uncouple();
                if (held.blueprint() != null && held.blueprint().name().startsWith(HUNG)) { setDown(level, held); }
            }
            return;
        }
        if (held != null) { return; }
        HookDesign design = (HookDesign) hook.vehicle().installed().getFirst().part().design();
        Vec3d loadLocal = new Vec3d(0, -design.heightM(), 0);
        Vec3d load = hook.vehicleState().body().worldPoint(hook.vehicleState().properties(), loadLocal);
        // a vehicle under the hook
        for (PartVehicle other : level.getEntitiesOfClass(PartVehicle.class, new net.minecraft.world.phys.AABB(load.x() - LATCH_M, load.y() - LATCH_M - 2, load.z() - LATCH_M,
                load.x() + LATCH_M, load.y() + 0.5, load.z() + LATCH_M))) {
            if (other == crane || other == hook || other.vehicle() == null || other.leader() != null) { continue; }
            var body = other.vehicleState().body();
            Vec3d top = body.position(), want = new Vec3d(top.x(), Math.min(load.y() - SLING_M, top.y() + 0.5), top.z());
            Vec3d local = body.orientation().inverse().rotate(want.subtract(top)).add(other.vehicleState().properties().centerOfMassLocal());
            hook.couple(other, Coupling.rope(loadLocal, local, SLING_M, ROPE_STIFFNESS_N, ROPE_DAMPING_NS));
            return;
        }
        // a block under the hook
        BlockPos pos = BlockPos.containing(load.x(), load.y() - 0.6, load.z());
        BlockState s = level.getBlockState(pos);
        if (s.isAir() || s.liquid() || EarthMaterials.undiggable(level, pos, s) || !WorldWork.may(level, crane, pos, s)) { return; }
        Material m = EarthMaterials.material(s);
        double kg = m == null ? SOLID_KG_PER_M3 : m.bankKgM3();
        PartVehicle hung = VehicleRegistry.PART_VEHICLE.get().create(level);
        if (hung == null) { return; }
        String id = WorldWork.id(s.getBlock());
        BoxDesign box = new BoxDesign("block", new Vec3d(0.98, 0.98, 0.98), 1.05, true, 1, MassSpec.given(kg));
        hung.setBlueprint(new VehicleBlueprint(HUNG + id, List.of(new VehicleBlueprint.Entry("block", box, Mount.ORIGIN)), List.of()),
                new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5), Orientation.IDENTITY);
        level.removeBlock(pos, false);
        level.addFreshEntity(hung);
        pending(hook, hung);
    }

    /** The hung load waits a tick or two to be ready before it can be slung (its tables build). */
    private static final java.util.Map<java.util.UUID, java.util.UUID> WAITING = new java.util.concurrent.ConcurrentHashMap<>();

    private static void pending(PartVehicle hook, PartVehicle hung) { WAITING.put(hook.getUUID(), hung.getUUID()); }

    /** Slings a hung block that has become ready (called from the crane's steps). */
    static void sling(ServerLevel level, PartVehicle hook) {
        java.util.UUID id = WAITING.get(hook.getUUID());
        if (id == null || !(level.getEntity(id) instanceof PartVehicle hung) || !hung.ready()) { return; }
        WAITING.remove(hook.getUUID());
        HookDesign design = (HookDesign) hook.vehicle().installed().getFirst().part().design();
        hook.couple(hung, Coupling.rope(new Vec3d(0, -design.heightM(), 0), new Vec3d(0, 0.5, 0), SLING_M, ROPE_STIFFNESS_N, ROPE_DAMPING_NS));
    }

    /** Puts a hung block back as its block where it is, or the nearest free cell above. */
    static void setDown(ServerLevel level, PartVehicle hung) {
        String id = hung.blueprint().name().substring(HUNG.length());
        Block b = WorldWork.block(id);
        Vec3d at = hung.vehicleState().body().position();
        BlockPos pos = BlockPos.containing(at.x(), at.y(), at.z());
        for (int k = 0; k < 4 && !level.getBlockState(pos).canBeReplaced(); k++) { pos = pos.above(); }
        level.setBlock(pos, b.defaultBlockState(), Block.UPDATE_ALL);
        hung.discard();
    }

    private static void setIfChanged(PartVehicle v, String channel, double value) {
        Double was = v.controls().get(channel);
        if (was == null ? value != 0 : was != value) { v.setControl(channel, value); }
    }

}
