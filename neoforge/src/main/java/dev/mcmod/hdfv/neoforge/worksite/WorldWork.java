package dev.mcmod.hdfv.neoforge.worksite;

import dev.mcmod.aerocore.api.Orientation;
import dev.mcmod.aerocore.api.RigidBodyState;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.earthmoving.Blade;
import dev.mcmod.aerocore.api.earthmoving.Material;
import dev.mcmod.aerocore.api.vehicle.Part;
import dev.mcmod.aerocore.api.vehicle.Vehicle;
import dev.mcmod.aerocore.api.vehicle.VehiclePose;
import dev.mcmod.aerocore.api.vehicle.VehicleState;
import dev.mcmod.aerocore.api.vehicle.part.DrumPart;
import dev.mcmod.aerocore.neoforge.vehicle.PartVehicle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 0.26: what machines do to the world each tick (after their physics: PartVehicle.Stepped). The rules are hdfv's:
 * <ul>
 * <li>Cuts (AeroCore's tools, VehicleState.takeCuts): each takes bank cubic metres from a block's cell (DigProgress); the
 *     block goes when all of it is taken (pavement leaves its compacted base). What is cut goes into the tool's bulk hold
 *     (BulkCargo), or heaps ahead of a blade, as the block form it is carried as (EarthMaterials.dugForm); a ripper's
 *     tooth (a TOOTH with no hold) breaks it in place instead (EarthMaterials.brokenForm). A block a protection mod
 *     keeps (BlockEvent.BreakEvent as the machine's driver, or the server's fake player) is not taken.</li>
 * <li>Each tool part learns if its hold is full ("&lt;part&gt;.full") and how much its blade pushes ("&lt;part&gt;.pushed", kg).</li>
 * <li>A blade's heap beyond its capacity (Blade.prismM3) spills off the blade's ends; lifted clear of the ground it is
 *     left where the blade was.</li>
 * <li>A bulk hold tipped past its material's angle of repose (its opening turned down from up) pours, faster the more
 *     it is tipped: into another vehicle's bulk hold under its lip (a truck), into a container block (as block items: a
 *     bank cubic metre each), or onto the ground, where it is piled block by block at no steeper than a block a block
 *     (Piles).</li>
 * <li>A roller's drum (DrumPart) crossing into a cell of loose fill, soil, sand or gravel counts a pass; enough passes
 *     (4 vibrating, 8 static: Caterpillar's and Forssblad's usual numbers, a rule) make it compacted base. A pass over hot
 *     asphalt rolls it (HotAsphaltBlock).</li>
 * </ul>
 * The kinds of work tools add their own (WorkRules). Nothing here runs with "worksite.editWorld" off.
 */
public final class WorldWork {
    private WorldWork() {}

    /** Passes to compact fill: vibrating, static (rules). */
    public static final int PASSES_VIBRATING = 4, PASSES_STATIC = 8;
    /** How long a pour's slope (deg of tip past the repose) takes to empty a hold at that slope's 20 deg: the hold empties in about 12 s at full tip (a rule). */
    public static final double POUR_SECONDS = 12;

    /** Rollers' last cells by vehicle and part, and each cell's pass count (transient: a pass in progress is lost on a restart). */
    private static final Map<String, Long> LAST_CELL = new HashMap<>();
    private static final Map<Long, Integer> PASSES = new HashMap<>();

    public static void stepped(PartVehicle v) {
        if (!(v.level() instanceof ServerLevel level) || v.vehicle() == null || v.vehicleState() == null || !editsWorld()) { return; }
        Vehicle vehicle = v.vehicle();
        VehicleState st = v.vehicleState();
        BulkCargo bulk = BulkCargo.of(v);
        boolean changed = cuts(level, v, vehicle, st, bulk);
        changed |= heaps(level, v, vehicle, st, bulk);
        changed |= pours(level, v, vehicle, st, bulk);
        rollers(level, v, vehicle, st);
        changed |= WorkRules.work(level, v, vehicle, st, bulk);
        if (changed) { bulk.weigh(v); }
        feedback(v, vehicle, bulk);
        readouts(v, st, bulk);
        if (level.getGameTime() % 20 == 0) { age(bulk, 20 * dev.mcmod.hdfv.neoforge.HdfvConfig.workSecondsPerTick()); }
    }

    /**
     * 0.26: hot mix cools in its hold, TRUCK_COOLING_K_PER_H toward the air (a rule: a covered load loses a few degrees an
     * hour, its crust far more than its middle), concrete matures at CURING_K (Concrete.maturityKs) and sets in the drum.
     */
    public static final double TRUCK_COOLING_K_PER_H = 8, AIR_K = 288.15;

    static void age(BulkCargo bulk, double seconds) {
        for (BulkCargo.Load l : bulk.holds().values()) {
            if (l.isEmpty()) { continue; }
            Block b = block(l.main());
            if (b == HdfvWorksite.HOT_ASPHALT.get()) { l.temperatureK = Math.max(AIR_K, l.temperatureK - TRUCK_COOLING_K_PER_H * seconds / 3600); }
            else if (b == HdfvWorksite.FRESH_CONCRETE.get()) { l.maturityKs = dev.mcmod.aerocore.api.earthmoving.Concrete.maturityKs(l.maturityKs, FreshConcreteBlock.CURING_K, seconds); }
        }
    }

    /** 0.26: the dashboard's readouts (Dashboard.HUD_*): the highest circuit pressure to a bar, the bulk carried to a tenth of a loose m^3. */
    static void readouts(PartVehicle v, VehicleState st, BulkCargo bulk) {
        double p = 0;
        for (String c : st.hydraulicCircuits()) { p = Math.max(p, st.hydraulicPressure(c)); }
        setIfChanged(v, dev.mcmod.hdfv.drive.Dashboard.HUD_PRESSURE, Math.round(p / 1e5) * 1e5);
        double m3 = 0;
        for (BulkCargo.Load l : bulk.holds().values()) { m3 += l.looseM3(); }
        setIfChanged(v, dev.mcmod.hdfv.drive.Dashboard.HUD_BULK, Math.round(m3 * 10) / 10.0);
    }

    public static boolean editsWorld() { return dev.mcmod.hdfv.neoforge.HdfvConfig.editWorld(); }

    /** Who a machine's work is done as (its driver, or the server's fake player). */
    static Player operator(ServerLevel level, PartVehicle v) {
        Entity e = v == null ? null : v.getControllingPassenger();
        if (e instanceof ServerPlayer p) { return p; }
        return net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);
    }

    /** Whether the machine may change the block at pos (spawn protection, other mods' claims). */
    static boolean may(ServerLevel level, PartVehicle v, BlockPos pos, BlockState s) {
        Player p = operator(level, v);
        if (!level.mayInteract(p, pos)) { return false; }
        net.neoforged.neoforge.event.level.BlockEvent.BreakEvent e = new net.neoforged.neoforge.event.level.BlockEvent.BreakEvent(level, pos, s, p);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(e);
        return !e.isCanceled();
    }

    static String id(Block b) { return BuiltInRegistries.BLOCK.getKey(b).toString(); }
    static Block block(String id) { return BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id)); }

    private static Part.Tool tool(Vehicle vehicle, String part, String name) {
        for (Vehicle.Tool t : vehicle.tools()) { if (t.tool().name().equals(name) && vehicle.installed().get(t.part()).id().equals(part)) { return t.tool(); } }
        return null;
    }

    // ---------------------------------------------------------------- cuts

    private static boolean cuts(ServerLevel level, PartVehicle v, Vehicle vehicle, VehicleState st, BulkCargo bulk) {
        List<VehicleState.Cut> cuts = st.takeCuts();
        if (cuts.isEmpty()) { return false; }
        DigProgress progress = DigProgress.of(level);
        boolean changed = false;
        for (VehicleState.Cut c : cuts) {
            BlockPos pos = new BlockPos(c.x(), c.y(), c.z());
            if (!level.isLoaded(pos)) { continue; }
            BlockState s = level.getBlockState(pos);
            Material m = EarthMaterials.material(s);
            if (m == null || EarthMaterials.undiggable(level, pos, s)) { continue; }
            Part.Tool tool = tool(vehicle, c.part(), c.tool());
            double cell = s.getBlock() == Blocks.SNOW ? s.getValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS) / 8.0 : 1;
            double was = progress.dug(pos, s), now = progress.add(pos, s, c.m3() / cell);
            if (tool != null && tool.kind() == Part.Tool.Kind.TOOTH && c.hold().isEmpty()) {
                // a ripper's tooth breaks the block in place
                Block broken = EarthMaterials.brokenForm(s);
                if (now >= 1 && broken != null && may(level, v, pos, s)) { level.setBlock(pos, broken.defaultBlockState(), Block.UPDATE_ALL); }
                continue;
            }
            String form = id(EarthMaterials.dugForm(s));
            double m3 = Math.min(c.m3(), (1 - Math.min(was, 1)) * cell);
            BulkCargo.Load into = c.hold().isEmpty() ? bulk.heap(c.part() + "." + c.tool()) : bulk.hold(c.hold());
            if (now >= 1) {
                if (!may(level, v, pos, s)) { progress.add(pos, s, -1); continue; }   // kept: nothing taken
                boolean pavement = s.getBlock() == dev.mcmod.hdfv.neoforge.petroleum.HdfvRefinery.ASPHALT.get() || s.getBlock() == HdfvWorksite.HOT_ASPHALT.get()
                        || s.getBlock() == dev.mcmod.hdfv.neoforge.airport.HdfvAirports.CONCRETE.get();
                level.levelEvent(2001, pos, Block.getId(s));   // the breaking particles and sound
                level.setBlock(pos, pavement ? HdfvWorksite.COMPACTED_BASE.get().defaultBlockState() : Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                progress.clear(pos);
            }
            into.add(form, Math.max(m3, 0));
            changed = true;
        }
        return changed;
    }

    // ---------------------------------------------------------------- blades' heaps

    private static boolean heaps(ServerLevel level, PartVehicle v, Vehicle vehicle, VehicleState st, BulkCargo bulk) {
        if (bulk.heaps().isEmpty()) { return false; }
        boolean changed = false;
        VehiclePose pose = st.pose();
        RigidBodyState b = st.body();
        for (Map.Entry<String, BulkCargo.Load> e : bulk.heaps().entrySet()) {
            BulkCargo.Load heap = e.getValue();
            if (heap.isEmpty()) { continue; }
            String key = e.getKey();
            int dot = key.lastIndexOf('.');
            Part.Tool tool = tool(vehicle, key.substring(0, dot), key.substring(dot + 1));
            Vehicle.Installed part = vehicle.installed(key.substring(0, dot));
            if (tool == null || part == null) { heap.take(heap.bankM3()); changed = true; continue; }
            Vec3d a = b.worldPoint(st.properties(), pose.apply(part.index(), tool.a())), z = b.worldPoint(st.properties(), pose.apply(part.index(), tool.b()));
            Vec3d cut = b.orientation().rotate(pose.rotation(part.index()).rotate(tool.cut()));
            Material m = BulkCargo.material(heap.main());
            double capacity = capacity(part.part().design(), tool, m);
            double over = heap.looseM3() - capacity;
            if (over > 0) {
                // spills off both ends of the blade
                BulkCargo.Load spill = heap.take(over / Math.max(heap.looseM3(), 1e-9) * heap.bankM3());
                BulkCargo.Load half = spill.take(spill.bankM3() / 2);
                Vec3d out = z.subtract(a).normalized();
                Piles.deposit(level, v, a.subtract(out.scale(0.8)), half);
                Piles.deposit(level, v, z.add(out.scale(0.8)), spill);
                changed = true;
            }
            // lifted clear of the ground: left in front of where the blade was
            Vec3d mid = a.add(z).scale(0.5);
            BlockPos under = BlockPos.containing(mid.x(), mid.y() - 0.6, mid.z());
            if (level.getBlockState(under).isAir() && level.getBlockState(under.below()).isAir()) {
                Vec3d ahead = mid.add(cut.scale(0.6));
                int n = Math.max(1, (int) Math.round(z.subtract(a).length()));
                for (int i = 0; i < n; i++) {
                    Vec3d at = a.add(z.subtract(a).scale((i + 0.5) / n)).add(cut.scale(0.6));
                    Piles.deposit(level, v, at, heap.take(heap.bankM3() / (n - i)));
                }
                changed = true;
            }
        }
        bulk.heaps().values().removeIf(BulkCargo.Load::isEmpty);
        return changed;
    }

    /** What a blade holds ahead of it (loose m^3): the work tool's own capacity, or the prism of its edge at the material's repose. */
    static double capacity(dev.mcmod.aerocore.api.vehicle.PartDesign design, Part.Tool tool, Material m) {
        double given = WorkRules.bladeCapacityM3(design);
        if (given > 0) { return given; }
        return Blade.prismM3(tool.widthM(), tool.depthM(), m.reposeRad());
    }

    // ---------------------------------------------------------------- pouring

    private static boolean pours(ServerLevel level, PartVehicle v, Vehicle vehicle, VehicleState st, BulkCargo bulk) {
        boolean changed = false;
        VehiclePose pose = st.pose();
        RigidBodyState b = st.body();
        for (int k = 0; k < vehicle.holds().size(); k++) {
            Vehicle.Hold h = vehicle.holds().get(k);
            if (!BulkCargo.bulk(h)) { continue; }
            BulkCargo.Load load = bulk.holds().get(h.id());
            if (load == null || load.isEmpty()) { continue; }
            Vehicle.Installed part = vehicle.installed().get(h.part());
            Vec3d opening = b.orientation().rotate(pose.rotation(part.index()).rotate(WorkRules.openingPart(part.part().design())));
            Material m = BulkCargo.material(load.main());
            double tip = Math.toDegrees(Math.acos(Math.clamp(opening.y(), -1, 1))), from = 90 - Math.toDegrees(m.reposeRad());
            if (tip <= from || !WorkRules.pours(part.part().design(), v, part.id())) { continue; }
            double share = Math.min((tip - from) / 20, 1) * 0.05 / POUR_SECONDS;   // of the hold's capacity a tick
            double bank = Math.min(load.bankM3(), share * h.hold().capacityM3() / Math.max(m.swell(), 1));
            Vec3d centre = b.worldPoint(st.properties(), pose.holdCentre(k));
            Vec3d lip = centre.add(opening.scale(Math.cbrt(h.hold().capacityM3()) / 2 + 0.3));
            changed |= pourFrom(level, v, lip, load.take(bank), load);
        }
        return changed;
    }

    /** Pours a load from a point: into a vehicle's bulk hold under it, a container block, or onto the ground; what finds no place goes back. */
    static boolean pourFrom(ServerLevel level, PartVehicle from, Vec3d lip, BulkCargo.Load load, BulkCargo.Load back) {
        if (load.isEmpty()) { return false; }
        // a vehicle's bulk hold under the lip (a truck being loaded)
        AABB below = new AABB(lip.x() - 4, lip.y() - 12, lip.z() - 4, lip.x() + 4, lip.y(), lip.z() + 4);
        for (PartVehicle other : level.getEntitiesOfClass(PartVehicle.class, below, o -> o != from && o.vehicle() != null && o.vehicleState() != null)) {
            Vehicle ov = other.vehicle();
            VehicleState os = other.vehicleState();
            for (int k = 0; k < ov.holds().size(); k++) {
                Vehicle.Hold h = ov.holds().get(k);
                if (!BulkCargo.bulk(h)) { continue; }
                Vec3d c = os.body().worldPoint(os.properties(), os.pose().holdCentre(k));
                double reach = Math.cbrt(h.hold().capacityM3()) * 0.6 + 0.5;
                if (Math.hypot(c.x() - lip.x(), c.z() - lip.z()) > reach || c.y() > lip.y()) { continue; }
                BulkCargo ob = BulkCargo.of(other);
                BulkCargo.Load into = ob.hold(h.id());
                double room = h.hold().capacityM3() - into.looseM3();
                if (room <= 0) { continue; }
                double fits = Math.min(1, room / Math.max(load.looseM3(), 1e-9));
                into.add(load.take(load.bankM3() * fits));
                ob.weigh(other);
                if (!load.isEmpty()) { back.add(load); }
                return true;
            }
        }
        // a container block where it lands (a hopper, a chest)
        BlockPos land = Piles.landing(level, lip);
        if (land != null) {
            var handler = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, land.below(), Direction.UP);
            if (handler != null) {
                Block form = block(load.main());
                double whole = Math.floor(load.bankM3() + 1e-9);
                if (whole >= 1 && form.asItem() != net.minecraft.world.item.Items.AIR) {
                    ItemStack left = net.neoforged.neoforge.items.ItemHandlerHelper.insertItem(handler, new ItemStack(form.asItem(), (int) whole), false);
                    int put = (int) whole - left.getCount();
                    load.take(put);
                }
                if (!load.isEmpty()) { back.add(load); }
                return true;
            }
        }
        // the ground
        Piles.deposit(level, from, lip, load);
        if (!load.isEmpty()) { back.add(load); }
        return true;
    }

    // ---------------------------------------------------------------- rollers

    private static void rollers(ServerLevel level, PartVehicle v, Vehicle vehicle, VehicleState st) {
        VehiclePose pose = st.pose();
        RigidBodyState b = st.body();
        for (Vehicle.Installed i : vehicle.installed()) {
            if (!(i.part() instanceof DrumPart drum)) { continue; }
            Vec3d bottom = b.worldPoint(st.properties(), pose.apply(i.index(), new Vec3d(0, -drum.spec().radiusM() - 0.05, 0)));
            BlockPos cell = BlockPos.containing(bottom.x(), bottom.y(), bottom.z());
            String key = v.getUUID() + "|" + i.id();
            Long last = LAST_CELL.put(key, cell.asLong());
            if (last != null && last == cell.asLong()) { continue; }
            double vibration = v.vehicleState().diagnostics().getOrDefault(i.id(), Map.of()).getOrDefault("dynamicForceN", 0.0);
            int half = (int) Math.round(drum.spec().widthM() / 2);
            Vec3d across = b.orientation().rotate(pose.rotation(i.index()).rotate(Vec3d.Z));
            for (int w = -half; w <= half; w++) {
                Vec3d at = bottom.add(across.scale(w));
                BlockPos p = BlockPos.containing(at.x(), at.y(), at.z());
                BlockState s = level.getBlockState(p);
                if (s.getBlock() instanceof HotAsphaltBlock) { HotAsphaltBlock.rolled(level, p, s); continue; }
                if (!(s.getBlock() == HdfvWorksite.LOOSE_FILL.get() || s.is(net.minecraft.tags.BlockTags.DIRT) || s.getBlock() == Blocks.GRAVEL
                        || s.is(net.minecraft.tags.BlockTags.SAND))) { continue; }
                int passes = PASSES.merge(p.asLong(), 1, Integer::sum);
                if (passes >= (vibration > 0 ? PASSES_VIBRATING : PASSES_STATIC) && may(level, v, p, s)) {
                    level.setBlock(p, HdfvWorksite.COMPACTED_BASE.get().defaultBlockState(), Block.UPDATE_ALL);
                    PASSES.remove(p.asLong());
                }
            }
            if (PASSES.size() > 1 << 16) { PASSES.clear(); }
            if (LAST_CELL.size() > 4096) { LAST_CELL.clear(); }
        }
    }

    // ---------------------------------------------------------------- feedback to the tools

    private static void feedback(PartVehicle v, Vehicle vehicle, BulkCargo bulk) {
        for (Vehicle.Tool t : vehicle.tools()) {
            Vehicle.Installed part = vehicle.installed().get(t.part());
            String hold = t.tool().hold();
            boolean full = false;
            if (!hold.isEmpty()) {
                String id = part.id() + "." + hold;
                for (Vehicle.Hold h : vehicle.holds()) {
                    if (!h.id().equals(id)) { continue; }
                    BulkCargo.Load l = bulk.holds().get(id);
                    full = l != null && l.looseM3() >= h.hold().capacityM3();
                }
            }
            BulkCargo.Load heap = bulk.heaps().get(part.id() + "." + t.tool().name());
            setIfChanged(v, part.id() + ".full", full ? 1 : 0);
            setIfChanged(v, part.id() + ".pushed", heap == null ? 0 : Math.round(heap.massKg()));
        }
    }

    private static void setIfChanged(PartVehicle v, String channel, double value) {
        Double was = v.controls().get(channel);
        if (was == null ? value != 0 : was != value) { v.setControl(channel, value); }
    }
}
