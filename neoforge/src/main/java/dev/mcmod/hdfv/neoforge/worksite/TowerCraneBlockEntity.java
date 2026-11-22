package dev.mcmod.hdfv.neoforge.worksite;

import dev.mcmod.aerocore.api.Orientation;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.vehicle.MassSpec;
import dev.mcmod.aerocore.api.vehicle.Mount;
import dev.mcmod.aerocore.api.vehicle.VehicleBlueprint;
import dev.mcmod.aerocore.api.vehicle.part.HangPart;
import dev.mcmod.aerocore.neoforge.vehicle.PartVehicle;
import dev.mcmod.aerocore.neoforge.vehicle.VehicleRegistry;
import dev.mcmod.hdfv.drive.Dashboard;
import dev.mcmod.hdfv.neoforge.multiblock.ControllerBlockEntity;
import dev.mcmod.hdfv.part.BoxDesign;
import dev.mcmod.hdfv.part.SeatDesign;
import dev.mcmod.hdfv.part.machine.HookDesign;
import dev.mcmod.hdfv.structure.Structures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 0.26: a flat-top tower crane (Structures.TOWER_CRANE_PATTERN, the user: the tower crane is a multiblock). Formed, it
 * keeps two vehicles: its cab (a seat the operator rides at the slewing unit, held there each tick: the cab is part of
 * the structure, not a vehicle that moves) and its hook block, hung from the trolley by AeroCore's HangPart (the trolley
 * is a kinematic anchor: its point and velocity given each tick). The operator's work keys (WorkInputs: a crane's
 * layout, the cab binds "swing", "telescope" for the trolley and "hoist", and the hook latch "hook") slew the jib at
 * SLEW_RAD_PER_S, run the trolley at TROLLEY_MPS between MIN_RADIUS_M and the jib's reach, and hoist at HOIST_MPS.
 * <p>
 * Its load chart (a rule of the order of a 6 t flat-top crane's): MAX_KG to the radius where the moment line
 * TIP_KG x REACH_M takes over. The load moment limiter stops hoisting up and the trolley running out while the hook's
 * pull is at the chart's load (the dashboard shows its share); in wind over WIND_STOP_MPS at the jib (WorldAir) the
 * crane stops working (it weathervanes in reality: here it holds).
 */
public final class TowerCraneBlockEntity extends ControllerBlockEntity {
    public static final double REACH_M = 30, MIN_RADIUS_M = 2.5, MAX_KG = 6000, TIP_KG = 1500, SLEW_RAD_PER_S = 0.08, TROLLEY_MPS = 1.0, HOIST_MPS = 1.0,
            WIND_STOP_MPS = 20, RATED_W = 40_000;
    /** The cab's blueprint name. */
    public static final String CAB = "tower crane cab";
    /**
     * The cab vehicle's boxes, by name: MachineLooks draws each as what it is (the slewing unit, a lattice jib with its
     * trolley, the counter-jib's walkway and winch, ballast slabs, a glazed cab). HEAD_M: the jibs' roots from the mast's
     * axis (the slewing unit's half length).
     */
    public static final String SLEWING_UNIT = "tower crane slewing unit", JIB = "tower crane jib", COUNTER_JIB = "tower crane counter jib",
            BALLAST = "tower crane ballast", CABIN = "tower crane cabin";
    public static final double HEAD_M = 0.8;
    /** The control channel the trolley's radius (m from the mast's axis) goes to clients on, for drawing it. */
    public static final String TROLLEY = "crane.trolley";
    /** The cab vehicle's origin from the mast's top face on its axis (the cab vehicle is placed there, slewed). */
    private static final Vec3d ORIGIN = new Vec3d(1.5, 0.7, 0);

    private double slew, radius = 8, rope = 6;
    private UUID cab, hook;
    private String state = "";

    public TowerCraneBlockEntity(BlockPos pos, BlockState s) {
        super(HdfvWorksite.TOWER_CRANE_ENTITY.get(), pos, s, Structures.TOWER_CRANE_PATTERN, RATED_W);
    }

    /** The chart's load at a radius (kg). */
    public static double chartKg(double radiusM) { return Math.min(MAX_KG, TIP_KG * REACH_M / Math.max(radiusM, 1e-3)); }

    /** The slewing unit's top (the jib's level), from the formed pattern. */
    private Vec3d top() {
        int y = worldPosition.getY();
        if (multiblock.match() != null) { for (var c : multiblock.match().cells()) { y = Math.max(y, c.y()); } }
        BlockPos base = worldPosition.relative(facing().getOpposite());
        return new Vec3d(base.getX() + 0.5, y + 1.5, base.getZ() + 0.5);
    }

    private net.minecraft.core.Direction facing() {
        BlockState s = getBlockState();
        return s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)
                ? s.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING) : net.minecraft.core.Direction.NORTH;
    }

    /**
     * The cab's blueprint: the slewing unit on the mast's top, the jib (the mast's axis to its reach) and the counter-jib
     * on it, the ballast at the counter-jib's end, the cab hung under the jib's root in front of the mast, looking along
     * the jib (its front short of the trolley's least radius and its roof under the trolley), and its seat (no part reads
     * the work channels: the controller does, so the client gives a vehicle of this name a crane's keys:
     * DriveControls.machine). The sizes are what MachineLooks draws (0.26.0's looks; before, the boxes were drawn as
     * crates in other places).
     */
    static VehicleBlueprint cabBlueprint() {
        return new VehicleBlueprint(CAB, List.of(
                box("slewing_unit", SLEWING_UNIT, new Vec3d(2 * HEAD_M, 1.0, 2 * HEAD_M), new Vec3d(0, 0.5, 0), true, 100),
                box("jib", JIB, new Vec3d(REACH_M, 1.3, 1.1), new Vec3d(REACH_M / 2, 1.65, 0), false, 100),
                box("counter_jib", COUNTER_JIB, new Vec3d(10, 1.4, 1.8), new Vec3d(-5, 1.7, 0), false, 100),
                box("ballast", BALLAST, new Vec3d(1.6, 2.2, 1.5), new Vec3d(-9, 1.1, 0), true, 100),
                box("cabin", CABIN, new Vec3d(1.3, 2.2, 1.2), new Vec3d(1.5, -0.5, 0), true, 800),
                new VehicleBlueprint.Entry("seat", new SeatDesign("seat", 10), Mount.at(new Vec3d(1.4, -1.05, 0).subtract(ORIGIN)))), List.of());
    }

    /** One of the cab vehicle's boxes, its centre from the mast's top face on its axis. */
    private static VehicleBlueprint.Entry box(String id, String name, Vec3d size, Vec3d centre, boolean closed, double kg) {
        return new VehicleBlueprint.Entry(id, new BoxDesign(name, size, 1.05, closed, 1, MassSpec.given(kg)), Mount.at(centre.subtract(ORIGIN)));
    }

    /**
     * Whether a cab vehicle has this version's parts where they are now (one saved with an earlier layout, the crates
     * before 0.26.0's looks or the cab at the jib's side, is made anew): every part of cabBlueprint by id, its design's
     * name, its box's size and its mount within a millimetre.
     */
    static boolean current(PartVehicle cab) { return current(cab.blueprint()); }

    /** current for a blueprint (null: not loaded yet, left alone). */
    static boolean current(VehicleBlueprint bp) {
        if (bp == null) { return true; }
        for (VehicleBlueprint.Entry want : cabBlueprint().parts()) {
            VehicleBlueprint.Entry has = bp.parts().stream().filter(e -> e.id().equals(want.id())).findFirst().orElse(null);
            if (has == null || has.mount().position().subtract(want.mount().position()).length() > 1e-3) { return false; }
            if (want.design() instanceof BoxDesign w) {
                if (!(has.design() instanceof BoxDesign b) || !w.name().equals(b.name()) || w.sizeM().subtract(b.sizeM()).length() > 1e-3) { return false; }
            }
        }
        return true;
    }

    /** A failure in the controller's own work is logged once and stops its work, not the server. */
    private boolean failed;

    @Override protected void work(ServerLevel level, boolean formed) {
        if (failed) { return; }
        try { run(level, formed); }
        catch (RuntimeException e) {
            failed = true;
            com.mojang.logging.LogUtils.getLogger().error("hdfv: {} at {} stopped", getClass().getSimpleName(), worldPosition, e);
        }
    }

    private void run(ServerLevel level, boolean formed) {
        if (!formed) { state = "hdfv.tower_crane.not_formed"; return; }
        Vec3d top = top();
        PartVehicle cabV = cab == null ? null : level.getEntity(cab) instanceof PartVehicle v ? v : null;
        if (cabV != null && !cabV.isRemoved() && !current(cabV)) { cabV.discard(); cabV = null; }
        if (cabV == null || cabV.isRemoved()) {
            cabV = VehicleRegistry.PART_VEHICLE.get().create(level);
            if (cabV == null) { return; }
            cabV.setBlueprint(cabBlueprint(), top.add(new Vec3d(1.5, 0.2, 0)), Orientation.IDENTITY);
            level.addFreshEntity(cabV);
            cab = cabV.getUUID();
            setChanged();
        }
        PartVehicle hookV = hook == null ? null : level.getEntity(hook) instanceof PartVehicle v ? v : null;
        Vec3d trolley = top.add(new Vec3d(radius * Math.cos(slew), 0, -radius * Math.sin(slew)));
        if (hookV == null || hookV.isRemoved()) {
            hookV = VehicleRegistry.PART_VEHICLE.get().create(level);
            if (hookV == null) { return; }
            HookDesign d = new HookDesign("tower crane hook", MAX_KG, 2, 0.012, HookDesign.HookKind.SINGLE, 0.3);
            // wired, so its anchor and rope inputs read the channels of their names (HangPart.controls)
            hookV.setBlueprint(dev.mcmod.hdfv.assembly.AutoWiring.wire(new VehicleBlueprint("hook", List.of(new VehicleBlueprint.Entry("hook", d, Mount.ORIGIN)), List.of())),
                    trolley.subtract(new Vec3d(0, rope, 0)), Orientation.IDENTITY);
            level.addFreshEntity(hookV);
            hook = hookV.getUUID();
            setChanged();
        }
        // the cab rides on the slewing unit, turned with the jib
        Orientation turn = Orientation.rotationVector(Vec3d.Y.scale(slew));
        if (cabV.ready()) { cabV.place(top.add(turn.rotate(new Vec3d(1.5, 0.2, 0))), turn); }
        Map<String, Double> c = cabV.controls();
        double dt = 0.05;
        double power = draw(RATED_W);
        double wind = dev.mcmod.aerocore.neoforge.probe.WorldAir.at(level, top.x(), top.y(), top.z()).wind().length();
        double pull = 0;
        if (hookV.vehicleState() != null) {
            Map<String, Double> diag = hookV.vehicleState().diagnostics().get("hook");
            if (diag != null) { pull = diag.getOrDefault("tensionN", 0.0); }
        }
        double loadKg = pull / 9.80665, share = loadKg / chartKg(radius);
        boolean stopped = power < 1 || wind > WIND_STOP_MPS;
        state = wind > WIND_STOP_MPS ? "hdfv.tower_crane.wind" : power < 1 ? "hdfv.tower_crane.no_power" : share >= 1 ? "hdfv.tower_crane.limit" : "";
        double was = radius, wasSlew = slew;
        if (!stopped) {
            slew += SLEW_RAD_PER_S * Math.clamp(c.getOrDefault("swing", 0.0), -1, 1) * dt;
            double out = Math.clamp(c.getOrDefault("telescope", 0.0), -1, 1);
            if (share >= 1 && out > 0) { out = 0; }   // the limiter: no farther out under the chart's load
            radius = Math.clamp(radius + TROLLEY_MPS * out * dt, MIN_RADIUS_M, REACH_M);
            double up = Math.clamp(c.getOrDefault("hoist", 0.0), -1, 1);
            if (share >= 1 && up > 0) { up = 0; }     // nor hoisting up
            rope = Math.clamp(rope - HOIST_MPS * up * dt, 0.5, top.y() - level.getMinBuildHeight() - 1);
        }
        Vec3d now = top.add(new Vec3d(radius * Math.cos(slew), 0, -radius * Math.sin(slew)));
        Vec3d velocity = now.subtract(top.add(new Vec3d(was * Math.cos(wasSlew), 0, -was * Math.sin(wasSlew)))).scale(1 / dt);
        hookV.setControls(HangPart.controls(now, velocity, hookV.tickCount * dt, rope));
        // the latch on the hook key, the readouts on the cab's dashboard
        Cranes.sling(level, hookV);
        Cranes.latch(level, cabV, hookV, c.getOrDefault("hook", 0.0) > 0.5);
        cabV.setControl(Dashboard.HUD_HOOK, Math.round(loadKg / 10) * 10.0);
        cabV.setControl(Dashboard.HUD_LMI, Math.round(100 * share) / 100.0);
        double drawn = Math.round(radius * 100) / 100.0;
        if (!Double.valueOf(drawn).equals(c.get(TROLLEY))) { cabV.setControl(TROLLEY, drawn); }
        if (level.getGameTime() % 100 == 0) { setChanged(); }
    }

    @Override public Component status() {
        Component st = state.isEmpty() ? Component.translatable("hdfv.tower_crane.working") : Component.translatable(state);
        return Component.translatable("hdfv.tower_crane.status", String.format(java.util.Locale.ROOT, "%.1f", radius),
                String.format(java.util.Locale.ROOT, "%.1f", chartKg(radius) / 1000), String.format(java.util.Locale.ROOT, "%.0f", Math.toDegrees(slew) % 360), st);
    }

    @Override protected void broken(ServerLevel level) {
        for (UUID id : new UUID[]{cab, hook}) { if (id != null && level.getEntity(id) instanceof PartVehicle v) { v.discard(); } }
    }

    @Override protected void save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putDouble("Slew", slew);
        tag.putDouble("Radius", radius);
        tag.putDouble("Rope", rope);
        if (cab != null) { tag.putUUID("Cab", cab); }
        if (hook != null) { tag.putUUID("Hook", hook); }
    }

    @Override protected void load(CompoundTag tag, HolderLookup.Provider registries) {
        slew = tag.getDouble("Slew");
        radius = tag.contains("Radius") ? tag.getDouble("Radius") : 8;
        rope = tag.contains("Rope") ? tag.getDouble("Rope") : 6;
        cab = tag.hasUUID("Cab") ? tag.getUUID("Cab") : null;
        hook = tag.hasUUID("Hook") ? tag.getUUID("Hook") : null;
    }
}
