package dev.mcmod.hdfv.neoforge.repair;

import dev.mcmod.aerocore.api.vehicle.Vehicle;
import dev.mcmod.aerocore.api.vehicle.VehicleState;
import dev.mcmod.aerocore.neoforge.vehicle.PartVehicle;
import dev.mcmod.hdfv.assembly.RoomScan;
import dev.mcmod.hdfv.manufacture.Alloy;
import dev.mcmod.hdfv.neoforge.HdfvConfig;
import dev.mcmod.hdfv.neoforge.assembly.HdfvAssembly;
import dev.mcmod.hdfv.neoforge.assembly.HdfvVehicle;
import dev.mcmod.hdfv.neoforge.assembly.RoomDoorBlock;
import dev.mcmod.hdfv.neoforge.assembly.Staging;
import dev.mcmod.hdfv.neoforge.assembly.WorldGrid;
import dev.mcmod.hdfv.neoforge.material.HdfvComponents;
import dev.mcmod.hdfv.neoforge.material.Stock;
import dev.mcmod.hdfv.neoforge.power.Buffer;
import dev.mcmod.hdfv.neoforge.power.EnergyNetworks;
import dev.mcmod.hdfv.neoforge.power.PowerBlockEntity;
import dev.mcmod.hdfv.repair.RepairJob;
import dev.mcmod.hdfv.repair.RepairRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * A repair shop's controller (0.25). Scans the shop as the refit shop scans its own (RepairRules: closed, a door, work
 * lights, power, one vehicle, assembled, standing and empty, a welding station for welded work, the wreck rule), takes
 * the vehicle in it, and keeps the picks: the conditions of its parts chosen on the screen. The job (RepairJob) is
 * worked out from the vehicle as it is and shown with what the stores at the room have of what it needs. Started, it
 * takes those materials (nothing in creative mode), then works for the job's hours drawing the shop's power, the doors
 * shut and nobody aboard; a vehicle that leaves stops the job (what was taken is used up). Done, the picked conditions
 * are new (a part replaced whole, every condition of it) and what came off goes to the stores as salvage (or the floor).
 * The job going (its tasks and salvage) is kept with the world.
 */
public final class RepairShopBlockEntity extends BlockEntity implements MenuProvider {
    /** Ticks a job waits for its vehicle to be loaded again (after the world loads) before it is given up. */
    public static final int GRACE_TICKS = 100;

    final Buffer energy = new Buffer((int) Math.ceil(2 * PowerBlockEntity.fePerTick(RepairJob.SHOP_KW * 1000)) + 10, Integer.MAX_VALUE, 0, this::setChanged);

    /** The last scan as the screen shows it: problems, the vehicle, the job, its needs. */
    CompoundTag report = new CompoundTag();
    RoomScan.Scan scan;
    UUID target;
    int targetId = -1;
    /** The picks ("part/condition"), for the vehicle in the shop. */
    final Set<String> picks = new LinkedHashSet<>();
    /** The last scan's counts (not kept). */
    private int lights, vehicles, riders;
    private double speed;
    private boolean building;
    /** The job going was started in creative mode: it took nothing, so it gives nothing back. */
    boolean free;
    /** A job going: the work done and needed (ticks of full power), its power (W); what is owed of an FE; ticks its vehicle was not found. */
    boolean working;
    double workDone, workTotal, watts, owedFe;
    boolean starved;
    int missing;
    /** The job going: its tasks (part, condition, whole) and its salvage (alloy id to kg). */
    ListTag started = new ListTag();
    CompoundTag salvage = new CompoundTag();

    final net.minecraft.world.inventory.ContainerData data = new net.minecraft.world.inventory.ContainerData() {
        @Override public int get(int i) {
            return switch (i) {
                case 0 -> working ? 1 : 0;
                case 1 -> workTotal > 0 ? (int) Math.round(1000 * Math.min(1, workDone / workTotal)) : 0;
                case 2 -> starved ? 1 : 0;
                default -> energy.getEnergyStored();
            };
        }
        @Override public void set(int i, int v) { }
        @Override public int getCount() { return 4; }
    };

    public RepairShopBlockEntity(BlockPos pos, BlockState state) { super(HdfvRepair.SHOP.get(), pos, state); }

    public CompoundTag report() { return report; }
    public int targetId() { return targetId; }
    public boolean working() { return working; }
    public Set<String> picks() { return picks; }

    PartVehicle vehicle() {
        if (target == null || !(level instanceof ServerLevel server)) { return null; }
        return server.getEntity(target) instanceof PartVehicle v && v.isAlive() ? v : null;
    }

    /** The picks as core's. */
    List<RepairJob.Pick> pickList() {
        List<RepairJob.Pick> out = new ArrayList<>();
        for (String k : picks) { RepairJob.Pick p = RepairJob.Pick.of(k); if (p != null) { out.add(p); } }
        return out;
    }

    /** The job of the picks on a vehicle as it is now. */
    RepairJob job(PartVehicle v) {
        if (v == null || v.vehicle() == null || v.vehicleState() == null) { return RepairJob.NONE; }
        return RepairJob.of(v.vehicle(), v::used, pickList());
    }

    // ---------------------------------------------------------------- the shop

    /** Scans the shop, takes the vehicle in it, and keeps the report. */
    public RoomScan.Scan scan() {
        if (level == null) { return null; }
        Direction f = getBlockState().getValue(RepairShopBlock.FACING);
        scan = RoomScan.scan(new WorldGrid(level), worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), f.getStepX(), f.getStepZ());
        lights = 0;
        List<PartVehicle> found = List.of();
        if (scan.closed()) {
            for (BlockPos p : BlockPos.betweenClosed(scan.minX() - 1, scan.minY() - 1, scan.minZ() - 1, scan.maxX() + 1, scan.maxY() + 1, scan.maxZ() + 1)) {
                Block b = level.getBlockState(p).getBlock();
                if (b == HdfvAssembly.WORK_LIGHT.get()) { lights++; }
            }
            found = level.getEntitiesOfClass(PartVehicle.class, box());
        }
        vehicles = found.size();
        PartVehicle v = vehicles == 1 ? found.get(0) : null;
        if (!working) {
            if (v == null) { target = null; picks.clear(); }
            else if (!v.getUUID().equals(target)) { target = v.getUUID(); picks.clear(); }
        }
        EnergyNetworks.pull(level, worldPosition, energy);
        building = v instanceof HdfvVehicle hv && !hv.assembled();
        riders = v == null ? 0 : v.getPassengers().size();
        speed = v == null ? 0 : v.velocity().length();
        summarize();
        changed();
        return scan;
    }

    /** The shop's inside as a box (the scan's). */
    AABB box() { return new AABB(scan.minX(), scan.minY(), scan.minZ(), scan.maxX() + 1, scan.maxY() + 1, scan.maxZ() + 1); }

    /** The job, the problems and what the stores have of the needs, into the report. */
    void summarize() {
        CompoundTag keep = new CompoundTag();
        for (String k : new String[]{"Message", "Refused", "RefusedAt"}) { if (report.contains(k)) { keep.put(k, report.get(k)); } }
        report = keep;
        PartVehicle v = vehicle();
        if (v != null && v.blueprint() != null) { report.putString("Vehicle", v.blueprint().name()); }
        RepairJob job = working ? RepairJob.NONE : job(v);
        List<String> wrecks = v == null || v.vehicle() == null ? List.of() : RepairRules.wrecks(v.vehicle(), v::used, job);
        List<RepairRules.Problem> problems = scan == null || working ? List.of()
                : RepairRules.check(new RepairRules.Shop(scan, lights, vehicles, building, energy.getEnergyStored() > 0, speed, riders, job, wrecks));
        ListTag list = new ListTag();
        for (RepairRules.Problem p : problems) {
            CompoundTag t = new CompoundTag();
            t.putString("Key", "hdfv.repair_shop.problem." + p.key());
            ListTag args = new ListTag();
            for (Object a : p.args()) { args.add(StringTag.valueOf(String.valueOf(a))); }
            t.put("Args", args);
            list.add(t);
        }
        report.put("Problems", list);
        CompoundTag j = new CompoundTag();
        ListTag tasks = new ListTag();
        for (RepairJob.Task t : job.tasks()) {
            CompoundTag c = new CompoundTag();
            c.putString("Key", t.pick().key());
            c.putString("Kind", t.kind().name().toLowerCase(Locale.ROOT));
            c.putString("Reason", t.reason());
            tasks.add(c);
        }
        j.put("Tasks", tasks);
        j.putDouble("Hours", job.hours());
        j.putDouble("KWh", job.kWh());
        j.putBoolean("Welding", job.welding());
        if (level != null) {
            ListTag needs = new ListTag();
            List<IItemHandler> stores = stores();
            for (Staging.Need n : needs(job)) {
                CompoundTag c = new CompoundTag();
                c.putString("Name", Component.Serializer.toJson(n.name(), level.registryAccess()));
                c.putDouble("Amount", n.amount());
                c.putDouble("Have", Staging.have(n, stores));
                needs.add(c);
            }
            j.put("Needs", needs);
        }
        j.putString("Salvage", kg(job.salvageKg()));
        report.put("Job", j);
    }

    private static String kg(java.util.Map<Alloy, Double> m) {
        StringBuilder b = new StringBuilder();
        m.forEach((a, v) -> b.append(b.length() == 0 ? "" : ", ").append(a.id()).append(' ').append(fmt(v)).append(" kg"));
        return b.toString();
    }

    public static List<Component> problems(CompoundTag report) {
        List<Component> out = new ArrayList<>();
        for (Tag t : report.getList("Problems", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            List<Object> args = new ArrayList<>();
            for (Tag a : c.getList("Args", Tag.TAG_STRING)) { args.add(a.getAsString()); }
            out.add(Component.translatable(c.getString("Key"), args.toArray()));
        }
        return out;
    }

    /** An action refused: shown on the screen of whoever is looking for a few seconds (the report's "Refused"). */
    void refused(Component why) {
        if (level == null) { return; }
        report.putString("Refused", Component.Serializer.toJson(why, level.registryAccess()));
        report.putLong("RefusedAt", level.getGameTime());
        changed();
    }

    /** What the job takes from the stores: new parts, pieces and stock (as the assembly room's), standard components. */
    static List<Staging.Need> needs(RepairJob job) {
        List<Staging.Need> out = new ArrayList<>(Staging.needs(job.parts(), job.pieces(), job.stockKg()));
        job.components().forEach((c, n) -> out.add(Staging.Need.items(Component.translatable("item.hdfv." + c.id()), n, HdfvComponents.COMPONENTS.get(c).get())));
        return out;
    }

    List<IItemHandler> stores() {
        List<IItemHandler> out = new ArrayList<>();
        if (scan == null || level == null) { return out; }
        for (int[] c : scan.storage()) {
            var h = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, new BlockPos(c[0], c[1], c[2]), null);
            if (h != null) { out.add(h); }
        }
        return out;
    }

    // ---------------------------------------------------------------- the screen's actions

    public enum Op { TOGGLE, ALL, NONE, START, CANCEL, SCAN, DOOR }

    /** An action: a pick toggled (its key "part/condition"), all damaged or none picked, or one of the shop's actions. */
    public record Edit(Op op, String key) {
        public static Edit of(Op op) { return new Edit(op, ""); }
    }

    /** Applies an action; a refusal to show, or null. */
    public Component apply(Edit e, ServerPlayer player) {
        if (e.op() == Op.SCAN) { scan(); return null; }
        if (e.op() == Op.DOOR) { return toggleDoor() ? null : Component.translatable("hdfv.repair_shop.problem.no_door"); }
        if (e.op() == Op.CANCEL) { return cancel(Component.translatable("hdfv.repair_shop.cancelled")); }
        if (working) { return Component.translatable("hdfv.repair_shop.busy"); }
        if (e.op() == Op.START) { return start(player); }
        PartVehicle v = vehicle();
        if (v == null || v.vehicle() == null) { return Component.translatable("hdfv.repair_shop.problem.no_vehicle"); }
        switch (e.op()) {
            case TOGGLE -> {
                RepairJob.Pick p = RepairJob.Pick.of(e.key());
                Vehicle.Installed part = p == null ? null : v.vehicle().installed(p.part());
                if (part == null || !part.part().conditions().contains(p.condition())) { return Component.translatable("hdfv.repair_shop.no_such", e.key()); }
                if (!picks.remove(p.key())) { picks.add(p.key()); }
            }
            case ALL -> {
                picks.clear();
                for (RepairJob.Pick p : RepairJob.damaged(v.vehicle(), v::used)) { picks.add(p.key()); }
            }
            case NONE -> picks.clear();
            default -> { }
        }
        summarize();
        changed();
        return null;
    }

    boolean toggleDoor() {
        if (scan == null) { scan(); }
        if (scan == null || scan.doors().isEmpty() || level == null) { return false; }
        RoomScan.Door d = scan.doors().get(0);
        BlockPos at = new BlockPos(d.minX(), d.minY(), d.minZ());
        RoomDoorBlock.setOpen(level, at, !level.getBlockState(at).getValue(RoomDoorBlock.OPEN));
        return true;
    }

    /** Starts the job: the shop ready, something to do, the materials in the stores (taken now, none in creative mode). */
    Component start(ServerPlayer player) {
        scan();
        List<Component> problems = problems(report);
        if (!problems.isEmpty()) { return problems.get(0); }
        PartVehicle v = vehicle();
        if (v == null || v.vehicle() == null) { return Component.translatable("hdfv.repair_shop.problem.no_vehicle"); }
        RepairJob job = job(v);
        if (job.empty()) { return Component.translatable("hdfv.repair_shop.problem.nothing"); }
        free = player != null && player.getAbilities().instabuild;
        if (!free) {
            List<Staging.Need> needs = needs(job);
            List<IItemHandler> stores = stores();
            List<Component> missing = Staging.missing(needs, stores);
            if (!missing.isEmpty()) {
                net.minecraft.network.chat.MutableComponent m = Component.translatable("hdfv.repair_shop.missing");
                for (int i = 0; i < missing.size() && i < 6; i++) { m.append("\n- ").append(missing.get(i)); }
                if (missing.size() > 6) { m.append(Component.translatable("hdfv.build.more", missing.size() - 6)); }
                return m;
            }
            Staging.take(needs, stores);
        }
        started = new ListTag();
        for (RepairJob.Task t : job.tasks()) {
            CompoundTag c = new CompoundTag();
            c.putString("Part", t.part());
            c.putString("Condition", t.condition());
            c.putBoolean("Whole", t.wholePart());
            started.add(c);
        }
        salvage = new CompoundTag();
        job.salvageKg().forEach((a, kg) -> salvage.putDouble(a.id(), kg));
        workTotal = Math.max(1, Math.ceil(job.hours() * HdfvConfig.ticksPerWorkHour()));
        watts = job.hours() > 0 ? 1000 * job.kWh() / job.hours() : 0;
        workDone = 0;
        owedFe = 0;
        missing = 0;
        working = true;
        for (RoomScan.Door d : scan.doors()) { RoomDoorBlock.setOpen(level, new BlockPos(d.minX(), d.minY(), d.minZ()), false); }
        report.remove("Message");
        summarize();
        changed();
        return null;
    }

    Component cancel(Component why) {
        if (!working) { return null; }
        working = false;
        workDone = 0;
        started = new ListTag();
        salvage = new CompoundTag();
        report.putString("Message", Component.Serializer.toJson(why, level.registryAccess()));
        summarize();
        changed();
        return why;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, RepairShopBlockEntity be) {
        boolean was = be.working;
        tickJob(level, be);
        if (was && !be.working && be.workDone >= be.workTotal) {
            dev.mcmod.hdfv.neoforge.sound.SoundSync.event(level, net.minecraft.world.phys.Vec3.atCenterOf(pos), dev.mcmod.hdfv.sound.MachineSound.Event.DONE, 0);
        }
        if (be.working && level.getGameTime() % 50 == 0) {
            PartVehicle v = be.vehicle();
            if (v != null) { dev.mcmod.hdfv.neoforge.sound.SoundSync.event(level, v.position().add(0, 0.5, 0), dev.mcmod.hdfv.sound.MachineSound.Event.RIVET, 0); }
        }
        dev.mcmod.hdfv.neoforge.sound.SoundSync.report(be, be.working ? dev.mcmod.hdfv.sound.MachineSound.plant(dev.mcmod.aerocore.api.acoustics.ProcessNoise.Process.COMPRESSOR,
                Math.max(be.watts, 3000), 0.7, true) : null);
    }

    private static void tickJob(Level level, RepairShopBlockEntity be) {
        if (!be.working) { return; }
        if (be.scan == null) { be.scan(); }
        PartVehicle v = be.vehicle();
        if (v == null && ++be.missing <= GRACE_TICKS) { return; }   // a world just loaded: its entities come after the block entities
        if (v == null || be.scan == null || !be.scan.closed() || !be.box().contains(v.position())) {
            be.cancel(Component.translatable("hdfv.repair_shop.left"));
            return;
        }
        be.missing = 0;
        v.ejectPassengers();
        double rate = be.power(be.watts);
        be.starved = rate < 1;
        be.workDone += rate;
        if (be.workDone >= be.workTotal) { be.finish(v); }
        else if (level.getGameTime() % 20 == 0) { be.setChanged(); }
    }

    private double power(double w) {
        double need = PowerBlockEntity.fePerTick(w);
        EnergyNetworks.pull(level, worldPosition, energy);
        double owed = need + owedFe;
        if (energy.getEnergyStored() >= Math.ceil(owed - 1e-9)) {
            int whole = (int) Math.floor(owed);
            energy.use(whole);
            owedFe = owed - whole;
            return 1;
        }
        int have = energy.getEnergyStored();
        energy.use(have);
        owedFe = 0;
        return need > 0 ? have / need : 1;
    }

    /** The job done: the picked conditions new (a part replaced whole, all of it); the salvage to the stores (or the floor). */
    void finish(PartVehicle v) {
        working = false;
        workDone = workTotal;
        Vehicle vehicle = v.vehicle();
        VehicleState st = v.vehicleState();
        int mended = 0;
        if (vehicle != null && st != null) {
            for (Tag t : started) {
                CompoundTag c = (CompoundTag) t;
                Vehicle.Installed p = vehicle.installed(c.getString("Part"));
                if (p == null) { continue; }
                List<String> names = p.part().conditions();
                for (int i = 0; i < names.size(); i++) {
                    if (c.getBoolean("Whole") || names.get(i).equals(c.getString("Condition"))) {
                        if (st.used(p.id(), i) > 0) { st.setUsed(p.id(), i, 0); }
                        mended++;
                    }
                }
            }
            for (Vehicle.Installed p : vehicle.installed()) {
                if (!p.part().conditions().isEmpty()) { v.addUsed(p.id(), p.part().conditions().get(0), 0); break; }   // publishes the lot
            }
        }
        if (!free) { giveBack(); }
        started = new ListTag();
        salvage = new CompoundTag();
        picks.clear();
        report.putString("Message", Component.Serializer.toJson(Component.translatable("hdfv.repair_shop.done", mended), level.registryAccess()));
        summarize();
        changed();
        if (level instanceof net.minecraft.server.level.ServerLevel server) {
            dev.mcmod.hdfv.neoforge.tutorial.Milestones.near(server, worldPosition, TUTORIAL_REACH_M, "repaired", "shop");   // the tutorial (a job has no player)
        }
    }

    /** Players this near the controller when a job is done get the tutorial's repair (a hangar-sized shop's far corner, m). */
    public static final double TUTORIAL_REACH_M = 32;

    /** The salvage as stock bits into the stores, the rest on the floor by the controller. */
    private void giveBack() {
        List<IItemHandler> stores = stores();
        java.util.function.Consumer<ItemStack> drop = s -> net.minecraft.world.Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1,
                worldPosition.getZ() + 0.5, s);
        for (String id : salvage.getAllKeys()) {
            Alloy a;
            try { a = Alloy.byId(id); } catch (IllegalArgumentException unknown) { continue; }
            int bits = (int) Math.floor(salvage.getDouble(id) / (HdfvConfig.kgPerIngot() / 10) + 1e-9);
            while (bits > 0) {
                int n = Math.min(bits, 64);
                Staging.give(Stock.bits(a, n), stores, drop);
                bits -= n;
            }
        }
    }

    void changed() {
        setChanged();
        if (level != null && !level.isClientSide) { level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3); }
    }

    // ---------------------------------------------------------------- saving and the client's copy

    private static ListTag strings(Set<String> s) {
        ListTag out = new ListTag();
        for (String k : s) { out.add(StringTag.valueOf(k)); }
        return out;
    }

    private void readPicks(CompoundTag tag) {
        picks.clear();
        for (Tag t : tag.getList("Picks", Tag.TAG_STRING)) { picks.add(t.getAsString()); }
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Report", report);
        tag.put("Picks", strings(picks));
        if (target != null) { tag.putUUID("Target", target); }
        tag.putInt("Energy", energy.getEnergyStored());
        if (working) {
            tag.putBoolean("Working", true);
            tag.putDouble("WorkDone", workDone);
            tag.putDouble("WorkTotal", workTotal);
            tag.putDouble("Watts", watts);
            tag.putDouble("OwedFe", owedFe);
            tag.put("Started", started);
            tag.put("Salvage", salvage);
            if (free) { tag.putBoolean("Free", true); }
        }
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        report = tag.getCompound("Report");
        readPicks(tag);
        target = tag.hasUUID("Target") ? tag.getUUID("Target") : null;
        energy.set(tag.getInt("Energy"));
        working = tag.getBoolean("Working");
        workDone = tag.getDouble("WorkDone");
        workTotal = tag.getDouble("WorkTotal");
        watts = tag.getDouble("Watts");
        owedFe = tag.getDouble("OwedFe");
        started = tag.getList("Started", Tag.TAG_COMPOUND);
        salvage = tag.getCompound("Salvage");
        free = tag.getBoolean("Free");
        missing = 0;
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.put("Report", report);
        tag.put("Picks", strings(picks));
        PartVehicle v = vehicle();
        tag.putInt("Target", v == null ? -1 : v.getId());
        tag.putBoolean("Working", working);
        return tag;
    }

    @Override public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        report = tag.getCompound("Report");
        readPicks(tag);
        targetId = tag.getInt("Target");
        working = tag.getBoolean("Working");
    }

    @Override public void onDataPacket(net.minecraft.network.Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
        handleUpdateTag(pkt.getTag(), registries);
    }

    @Override public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @Override public Component getDisplayName() { return Component.translatable("block.hdfv.repair_shop_controller"); }

    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) { return new RepairShopMenu(id, inventory, this); }

    static String fmt(double v) { return String.format(Locale.ROOT, v < 10 ? "%.2f" : "%.1f", v); }
}
