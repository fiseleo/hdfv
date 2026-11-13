package dev.mcmod.hdfv.neoforge.refit;

import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.vehicle.VehicleBlueprint;
import dev.mcmod.aerocore.neoforge.vehicle.PartVehicle;
import dev.mcmod.hdfv.assembly.AssemblyPlan;
import dev.mcmod.hdfv.assembly.AutoWiring;
import dev.mcmod.hdfv.assembly.RoomScan;
import dev.mcmod.hdfv.look.LampSpec;
import dev.mcmod.hdfv.manufacture.Alloy;
import dev.mcmod.hdfv.manufacture.Workpiece;
import dev.mcmod.hdfv.neoforge.HdfvConfig;
import dev.mcmod.hdfv.neoforge.assembly.HdfvAssembly;
import dev.mcmod.hdfv.neoforge.assembly.HdfvVehicle;
import dev.mcmod.hdfv.neoforge.assembly.Packed;
import dev.mcmod.hdfv.neoforge.assembly.RoomDoorBlock;
import dev.mcmod.hdfv.neoforge.assembly.Staging;
import dev.mcmod.hdfv.neoforge.assembly.WorldGrid;
import dev.mcmod.hdfv.neoforge.design.Blueprints;
import dev.mcmod.hdfv.neoforge.material.Stock;
import dev.mcmod.hdfv.neoforge.paint.HdfvPaint;
import dev.mcmod.hdfv.neoforge.power.Buffer;
import dev.mcmod.hdfv.neoforge.power.EnergyNetworks;
import dev.mcmod.hdfv.neoforge.power.PowerBlockEntity;
import dev.mcmod.hdfv.paint.PaintJob;
import dev.mcmod.hdfv.part.body.BodyDesign;
import dev.mcmod.hdfv.refit.Refit;
import dev.mcmod.hdfv.refit.RefitJob;
import dev.mcmod.hdfv.refit.RefitRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
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
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
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
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * A refit shop's controller (0.16). Scans the shop like an assembly room (RefitRules: closed, a door the vehicle leaves
 * by as it came and as it will be, room, work lights, power, one vehicle, a welding station for welded work), takes
 * the vehicle in it as the one to refit and keeps the vehicle as it is to be (the pending blueprint, edited from the
 * screen one Refit edit at a time: its parts' shape, size, kit, rims, tuning and lamps, and the whole vehicle's scale),
 * with the refitted vehicle's own problems (the assembly room's). Started, it takes from the shop's chests what the job
 * needs (RefitJob: the new components, stock, paint cans, lamp units; nothing in creative mode), then works for the
 * job's hours drawing its power, the doors shut and nobody aboard; a vehicle that leaves stops the job (what was taken
 * is used up). Done, the vehicle is refitted where it stands (PartVehicle.refit) and set on the floor, what came off
 * goes to the chests (or the floor), and a vehicle blueprint in the controller's slot takes the refit.
 */
public final class RefitShopBlockEntity extends BlockEntity implements MenuProvider {
    public static final int BLANKS = 0, VEHICLE = 1, SLOTS = 2;

    final SimpleContainer items = new SimpleContainer(SLOTS) {
        @Override public void setChanged() { super.setChanged(); RefitShopBlockEntity.this.setChanged(); }
    };
    final Buffer energy = new Buffer((int) Math.ceil(2 * PowerBlockEntity.fePerTick(PaintJob.BAKE_KW * 1000)) + 10, Integer.MAX_VALUE, 0, this::setChanged);

    /** The last scan as the screen shows it: problems, the vehicle, the job, figures. */
    CompoundTag report = new CompoundTag();
    RoomScan.Scan scan;
    UUID target;
    int targetId = -1;
    /** The vehicle as it is to be (wired, as the vehicle's own blueprint): JSON ("": none); its scale against the vehicle as it came. */
    String pending = "";
    double scale = 1;
    /** The job going was started in creative mode: it took nothing from the chests, so it gives nothing back. */
    boolean free;
    /** A job going: the work done and needed (ticks of full power), its power (W); what is owed of an FE. */
    boolean working;
    double workDone, workTotal, watts, owedFe;
    boolean starved;

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

    public RefitShopBlockEntity(BlockPos pos, BlockState state) { super(HdfvRefit.SHOP.get(), pos, state); }

    public CompoundTag report() { return report; }
    public String pendingJson() { return pending; }
    public double scale() { return scale; }
    public int targetId() { return targetId; }
    public boolean working() { return working; }

    VehicleBlueprint pendingBlueprint() {
        if (pending.isEmpty()) { return null; }
        try { return VehicleBlueprint.fromJsonText(pending); } catch (RuntimeException e) { return null; }
    }

    PartVehicle vehicle() {
        if (target == null || !(level instanceof ServerLevel server)) { return null; }
        return server.getEntity(target) instanceof PartVehicle v && v.isAlive() ? v : null;
    }

    // ---------------------------------------------------------------- the shop

    /** Scans the shop, takes the vehicle in it, and keeps the report. */
    public RoomScan.Scan scan() {
        if (level == null) { return null; }
        Direction f = getBlockState().getValue(RefitShopBlock.FACING);
        scan = RoomScan.scan(new WorldGrid(level), worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), f.getStepX(), f.getStepZ());
        int lights = 0;
        List<PartVehicle> vehicles = List.of();
        if (scan.closed()) {
            for (BlockPos p : BlockPos.betweenClosed(scan.minX() - 1, scan.minY() - 1, scan.minZ() - 1, scan.maxX() + 1, scan.maxY() + 1, scan.maxZ() + 1)) {
                Block b = level.getBlockState(p).getBlock();
                if (b == HdfvAssembly.WORK_LIGHT.get()) { lights++; }
            }
            vehicles = level.getEntitiesOfClass(PartVehicle.class, box());
        }
        PartVehicle v = vehicles.size() == 1 ? vehicles.get(0) : null;
        if (!working) {
            if (v == null) { target = null; pending = ""; scale = 1; }
            else if (!v.getUUID().equals(target) || pending.isEmpty()) {
                target = v.getUUID();
                pending = v.blueprint() == null ? "" : v.blueprint().toJsonText();
                scale = 1;
            }
        }
        EnergyNetworks.pull(level, worldPosition, energy);
        boolean building = v instanceof HdfvVehicle hv && !hv.assembled();
        report = new CompoundTag();
        if (v != null && v.blueprint() != null) { report.putString("Vehicle", v.blueprint().name()); }
        report.putIntArray("Counts", new int[]{lights, vehicles.size()});
        summarize(lights, vehicles.size(), building);
        changed();
        return scan;
    }

    /** The shop's inside as a box (the scan's). */
    AABB box() { return new AABB(scan.minX(), scan.minY(), scan.minZ(), scan.maxX() + 1, scan.maxY() + 1, scan.maxZ() + 1); }

    /** The job, the problems and the figures, from the vehicle as it is to as it is to be, into the report. */
    void summarize(int lights, int vehicles, boolean building) {
        PartVehicle v = vehicle();
        VehicleBlueprint before = v == null ? null : v.blueprint(), next = pendingBlueprint();
        AssemblyPlan was = plan(before), will = plan(next);
        RefitJob job = before == null || next == null ? RefitJob.NONE : RefitJob.of(before, next);
        List<RefitRules.Problem> problems = scan == null ? List.of()
                : RefitRules.check(new RefitRules.Shop(scan, lights, vehicles, building, energy.getEnergyStored() > 0, was, next == null ? null : will, job, scale));
        ListTag list = new ListTag();
        for (RefitRules.Problem p : problems) {
            CompoundTag t = new CompoundTag();
            t.putString("Key", (p.vehicle() ? "hdfv.room.problem." : "hdfv.refit_shop.problem.") + p.key());
            ListTag args = new ListTag();
            for (Object a : p.args()) { args.add(StringTag.valueOf(String.valueOf(a))); }
            t.put("Args", args);
            list.add(t);
        }
        report.put("Problems", list);
        CompoundTag j = new CompoundTag();
        j.putString("Rebuilt", String.join(", ", job.rebuilt()));
        j.putString("Adjusted", String.join(", ", job.adjusted()));
        j.putInt("Pieces", job.pieces().stream().mapToInt(p -> p.workpiece().count()).sum());
        j.putInt("Back", job.returned().stream().mapToInt(p -> p.workpiece().count()).sum());
        j.putString("Stock", kg(job.stockKg()));
        j.putString("Refund", kg(job.refundKg()));
        j.putInt("Cans", job.cans());
        j.putInt("Lamps", job.lampUnits());
        j.putInt("LampsBack", job.lampsBack());
        j.putDouble("Hours", job.hours());
        j.putDouble("KWh", job.kWh());
        j.putBoolean("Welding", job.welding());
        ListTag needs = new ListTag();
        for (Staging.Need n : needs(job)) { needs.add(StringTag.valueOf(Component.Serializer.toJson(n.name().copy().append(" × " + fmt(n.amount())), level.registryAccess()))); }
        j.put("Needs", needs);
        report.put("Job", j);
        report.putDouble("Scale", scale);
        // what changes: mass, size, the kit's downforce at 200 km/h
        if (before != null && next != null) {
            CompoundTag fig = new CompoundTag();
            fig.putDouble("Mass0", mass(before)); fig.putDouble("Mass1", mass(next));
            if (was != null && will != null) {
                fig.putDouble("Length0", was.lengthM()); fig.putDouble("Length1", will.lengthM());
                fig.putDouble("Width0", was.widthM()); fig.putDouble("Width1", will.widthM());
                fig.putDouble("Height0", was.heightM()); fig.putDouble("Height1", will.heightM());
            }
            fig.putDouble("Downforce0", downforce(before)); fig.putDouble("Downforce1", downforce(next));
            report.put("Figures", fig);
        }
    }

    private static String kg(Map<Alloy, Double> m) {
        StringBuilder b = new StringBuilder();
        m.forEach((a, v) -> b.append(b.length() == 0 ? "" : ", ").append(a.id()).append(' ').append(fmt(v)).append(" kg"));
        return b.toString();
    }

    private static AssemblyPlan plan(VehicleBlueprint b) {
        if (b == null) { return null; }
        try { return AssemblyPlan.of(b); } catch (RuntimeException e) { return null; }
    }

    private static double mass(VehicleBlueprint b) {
        try { return b.compile().properties().massKg(); } catch (RuntimeException e) { return Double.NaN; }
    }

    /** The kits' downforce at 200 km/h with the floor 12 cm off the road, N (0 without a kit). */
    private static double downforce(VehicleBlueprint b) {
        double n = 0, v = 200 / 3.6;
        for (VehicleBlueprint.Entry e : b.parts()) {
            if (!(e.design() instanceof BodyDesign d) || !d.kitted()) { continue; }
            try {
                var aero = dev.mcmod.hdfv.look.KitLayout.of(d).aero;
                if (aero != null) { n += aero.loads(new Vec3d(-v, 0, 0), 1.225, p -> 0.12).downforceN(); }
            } catch (RuntimeException ignored) { }
        }
        return n;
    }

    public static List<Component> problems(CompoundTag report) {
        List<Component> out = new ArrayList<>();
        for (Tag t : report.getList("Problems", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            String key = c.getString("Key");
            List<Object> args = new ArrayList<>();
            for (Tag a : c.getList("Args", Tag.TAG_STRING)) {
                // the side a part sticks out by, in words (as the assembly room's)
                args.add(key.equals("hdfv.room.problem.part_does_not_fit") && args.size() == 1 ? Component.translatable("hdfv.room.side." + a.getAsString()) : a.getAsString());
            }
            out.add(Component.translatable(key, args.toArray()));
        }
        return out;
    }

    /** An edit refused: shown on the screen of whoever is looking for a few seconds (the report's "Refused"). */
    void refused(Component why) {
        if (level == null) { return; }
        report.putString("Refused", Component.Serializer.toJson(why, level.registryAccess()));
        report.putLong("RefusedAt", level.getGameTime());
        changed();
    }

    /** What the job takes from the chests: components, stock, paint cans, lamp units. */
    static List<Staging.Need> needs(RefitJob job) {
        List<Staging.Need> out = new ArrayList<>(Staging.needs(List.of(), job.pieces(), job.stockKg()));
        if (job.cans() > 0) { out.add(Staging.Need.items(Component.translatable("item.hdfv.paint_can"), job.cans(), HdfvPaint.PAINT_CAN.get())); }
        if (job.lampUnits() > 0) { out.add(Staging.Need.items(Component.translatable("item.hdfv.lamp_unit"), job.lampUnits(), HdfvRefit.LAMP_UNIT.get())); }
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

    // ---------------------------------------------------------------- edits from the screen

    public enum Op { SET, SCALE, LAMP_ADD, LAMP_SET, LAMP_REMOVE, LAMPS_KIT, RESET, START, CANCEL, SCAN, DOOR, WRITE }

    /**
     * An edit: an adjustable of a part (page, param, text), the vehicle's scale (value), a lamp of a body (by index), or
     * one of the shop's actions (WRITE: a part's design onto a blank blueprint).
     */
    public record Edit(Op op, String part, String page, String param, String text, int index, double value, LampSpec lamp) {
        public static Edit of(Op op) { return new Edit(op, "", "", "", "", -1, 0, null); }
    }

    /** Applies an edit; a refusal to show, or null. */
    public Component apply(Edit e, ServerPlayer player) {
        if (e.op() == Op.SCAN) { scan(); return null; }
        if (e.op() == Op.DOOR) { return toggleDoor() ? null : Component.translatable("hdfv.refit_shop.problem.no_door"); }
        if (e.op() == Op.CANCEL) { return cancel(Component.translatable("hdfv.refit_shop.cancelled")); }
        if (working) { return Component.translatable("hdfv.refit_shop.busy"); }
        if (e.op() == Op.START) { return start(player); }
        PartVehicle v = vehicle();
        if (v == null || v.blueprint() == null) { return Component.translatable("hdfv.refit_shop.problem.no_vehicle"); }
        VehicleBlueprint current = v.blueprint(), next = pendingBlueprint();
        if (e.op() == Op.RESET || next == null) {
            pending = current.toJsonText();
            scale = 1;
            rescan();
            return null;
        }
        if (e.op() == Op.WRITE) { return write(e.part(), next, player); }
        VehicleBlueprint edited;
        try {
            edited = switch (e.op()) {
                case SET -> Refit.set(next, e.part(), e.page(), e.param(), e.text());
                case SCALE -> {
                    double to = Math.clamp(e.value(), 0.25, 4);
                    VehicleBlueprint scaled = Refit.rescale(next, scale, to);
                    scale = to;
                    yield scaled;
                }
                case LAMP_ADD, LAMP_SET, LAMP_REMOVE, LAMPS_KIT -> lamps(next, e);
                default -> next;
            };
        } catch (RuntimeException ex) {
            return Component.translatable("hdfv.refit_shop.invalid", String.valueOf(ex.getMessage()));
        }
        if (!Refit.sameVehicle(current, edited)) { return Component.translatable("hdfv.refit_shop.not_refit"); }
        pending = edited.toJsonText();
        rescan();
        return null;
    }

    private VehicleBlueprint lamps(VehicleBlueprint next, Edit e) {
        VehicleBlueprint.Entry body = next.part(e.part());
        if (body == null || !(body.design() instanceof BodyDesign d)) { throw new IllegalArgumentException("not a body: " + e.part()); }
        if (e.op() == Op.LAMPS_KIT) { return Refit.withLamps(next, e.part(), null); }   // back to the kit's
        List<LampSpec> lamps = new ArrayList<>(Refit.lamps(d));
        switch (e.op()) {
            case LAMP_ADD -> lamps.add(e.lamp());
            case LAMP_SET -> lamps.set(e.index(), e.lamp());
            default -> lamps.remove(e.index());   // the last one taken off leaves none (not the kit's)
        }
        return Refit.withLamps(next, e.part(), lamps);
    }

    /** A part's refitted design onto a blank blueprint from the slot (to make its new components on the machines). */
    private Component write(String part, VehicleBlueprint next, ServerPlayer player) {
        VehicleBlueprint.Entry e = next.part(part);
        if (e == null) { return Component.translatable("hdfv.refit_shop.invalid", part); }
        ItemStack blanks = items.getItem(BLANKS);
        if (!blanks.is(dev.mcmod.hdfv.neoforge.HdfvRegistry.BLANK_BLUEPRINT.get())) { return Component.translatable("hdfv.refit_shop.need_blank"); }
        blanks.shrink(1);
        items.setChanged();
        ItemStack drawn = Blueprints.of(e.design());
        if (player == null || !player.getInventory().add(drawn)) { net.minecraft.world.Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1, worldPosition.getZ() + 0.5, drawn); }
        return Component.translatable("hdfv.refit_shop.written", e.design().name());
    }

    private void rescan() {
        int[] counts = report.getIntArray("Counts");
        summarize(counts.length > 0 ? counts[0] : 0, counts.length > 1 ? counts[1] : 0, false);
        changed();
    }

    boolean toggleDoor() {
        if (scan == null) { scan(); }
        if (scan == null || scan.doors().isEmpty() || level == null) { return false; }
        RoomScan.Door d = scan.doors().get(0);
        BlockPos at = new BlockPos(d.minX(), d.minY(), d.minZ());
        RoomDoorBlock.setOpen(level, at, !level.getBlockState(at).getValue(RoomDoorBlock.OPEN));
        return true;
    }

    /** Starts the job: the shop ready, the vehicle changed, the materials in the chests (taken now, none in creative mode). */
    Component start(ServerPlayer player) {
        scan();
        List<Component> problems = problems(report);
        if (!problems.isEmpty()) { return problems.get(0); }
        PartVehicle v = vehicle();
        VehicleBlueprint next = pendingBlueprint();
        if (v == null || v.blueprint() == null || next == null) { return Component.translatable("hdfv.refit_shop.problem.no_vehicle"); }
        if (!Refit.sameVehicle(v.blueprint(), next)) { return Component.translatable("hdfv.refit_shop.not_refit"); }
        RefitJob job = RefitJob.of(v.blueprint(), next);
        if (job.empty()) { return Component.translatable("hdfv.refit_shop.nothing"); }
        free = player != null && player.getAbilities().instabuild;
        if (!free) {
            List<Staging.Need> needs = needs(job);
            List<IItemHandler> stores = stores();
            List<Component> missing = Staging.missing(needs, stores);
            if (!missing.isEmpty()) {
                net.minecraft.network.chat.MutableComponent m = Component.translatable("hdfv.refit_shop.missing");
                for (int i = 0; i < missing.size() && i < 6; i++) { m.append("\n- ").append(missing.get(i)); }
                if (missing.size() > 6) { m.append(Component.translatable("hdfv.build.more", missing.size() - 6)); }
                return m;
            }
            Staging.take(needs, stores);
        }
        double ticksPerHour = HdfvConfig.ticksPerWorkHour();
        workTotal = Math.max(1, Math.ceil(job.hours() * ticksPerHour));
        watts = job.hours() > 0 ? 1000 * job.kWh() / job.hours() : 0;
        workDone = 0;
        owedFe = 0;
        working = true;
        for (RoomScan.Door d : scan.doors()) { RoomDoorBlock.setOpen(level, new BlockPos(d.minX(), d.minY(), d.minZ()), false); }
        changed();
        if (player != null) { dev.mcmod.hdfv.neoforge.tutorial.Milestones.reach(player, "refit_started"); }   // 0.24: the tutorial
        return null;
    }

    Component cancel(Component why) {
        if (!working) { return null; }
        working = false;
        workDone = 0;
        report.putString("Message", Component.Serializer.toJson(why, level.registryAccess()));
        changed();
        return why;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, RefitShopBlockEntity be) {
        boolean was = be.working;
        tickJob(level, be);
        if (was && !be.working && be.workDone >= be.workTotal) { dev.mcmod.hdfv.neoforge.sound.SoundSync.event(level, net.minecraft.world.phys.Vec3.atCenterOf(pos), dev.mcmod.hdfv.sound.MachineSound.Event.DONE, 0); }
        if (be.working && level.getGameTime() % 50 == 0) {
            PartVehicle v = be.vehicle();
            if (v != null) { dev.mcmod.hdfv.neoforge.sound.SoundSync.event(level, v.position().add(0, 0.5, 0), dev.mcmod.hdfv.sound.MachineSound.Event.RIVET, 0); }
        }
        dev.mcmod.hdfv.neoforge.sound.SoundSync.report(be, be.working ? dev.mcmod.hdfv.sound.MachineSound.plant(dev.mcmod.aerocore.api.acoustics.ProcessNoise.Process.COMPRESSOR, Math.max(be.watts, 3000), 0.7, true) : null);
    }

    private static void tickJob(Level level, RefitShopBlockEntity be) {
        if (!be.working) { return; }
        if (be.scan == null) { be.scan(); }
        PartVehicle v = be.vehicle();
        if (v == null || be.scan == null || !be.scan.closed() || !be.box().contains(v.position())) {
            be.cancel(Component.translatable("hdfv.refit_shop.left"));
            return;
        }
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

    /**
     * The job done: the vehicle refitted where it stands and set on the floor; what came off to the chests (or the
     * floor); fuel that no longer fits said; a vehicle blueprint in the slot takes the refit.
     */
    void finish(PartVehicle v) {
        working = false;
        workDone = workTotal;
        VehicleBlueprint before = v.blueprint(), next = pendingBlueprint();
        RefitJob job = before == null || next == null ? RefitJob.NONE : RefitJob.of(before, next);
        Map<String, Double> spilled = next == null ? null : v.refit(next, fuel -> {
            var spec = dev.mcmod.hdfv.fluid.HdfvFluids.of(fuel);
            return spec == null ? 1000 : spec.densityKgPerM3();
        });
        Component message;
        if (spilled == null) {
            message = Component.translatable("hdfv.refit_shop.failed");
        } else {
            settle(v, next);
            // 0.25: a hold made smaller or taken away: what no longer fits to the stores (or the floor)
            List<IItemHandler> stores = stores();
            dev.mcmod.hdfv.neoforge.storage.VehicleCargo.relayout(v, s -> Staging.give(s, stores, left -> net.minecraft.world.Containers.dropItemStack(level,
                    worldPosition.getX() + 0.5, worldPosition.getY() + 1, worldPosition.getZ() + 0.5, left)));
            if (!free) { giveBack(job); }   // a creative-mode job took nothing and gives nothing back
            double kg = spilled.values().stream().mapToDouble(Double::doubleValue).sum();
            message = kg > 0.05 ? Component.translatable("hdfv.refit_shop.done_spilled", fmt(kg)) : Component.translatable("hdfv.refit_shop.done");
            ItemStack drawing = items.getItem(VEHICLE);
            if (drawing.is(HdfvAssembly.VEHICLE_BLUEPRINT.get())) {
                drawing.set(HdfvAssembly.VEHICLE_LAYOUT.get(), AutoWiring.stripped(next).toJsonText());
                items.setChanged();
            }
            pending = next.toJsonText();
            scale = 1;
        }
        report.putString("Message", Component.Serializer.toJson(message, level.registryAccess()));
        rescan();
    }

    /** Sets the refitted vehicle on the shop's floor where it stands (its lowest point 2 cm over it, as a room rolls one out). */
    private void settle(PartVehicle v, VehicleBlueprint next) {
        AssemblyPlan p = plan(next);
        var st = v.vehicleState();
        if (p == null || st == null || scan == null) { return; }
        Vec3d origin = st.origin();
        v.place(new Vec3d(origin.x(), scan.minY() - p.min().y() + 0.02, origin.z()), st.body().orientation());
    }

    /** What came off: components whole, the old structure's metal as stock, lamps; into the chests, the rest on the floor. */
    private void giveBack(RefitJob job) {
        List<IItemHandler> stores = stores();
        java.util.function.Consumer<ItemStack> drop = s -> net.minecraft.world.Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1,
                worldPosition.getZ() + 0.5, s);
        for (AssemblyPlan.Piece p : job.returned()) {
            Workpiece w = p.workpiece();
            for (int left = w.count(); left > 0; ) {
                ItemStack s = Staging.workpieceOf(p.design(), w);
                s.setCount(Math.min(left, s.getMaxStackSize()));
                left -= s.getCount();
                Staging.give(s, stores, drop);
            }
        }
        Map<Alloy, Double> refund = new EnumMap<>(Alloy.class);
        refund.putAll(job.refundKg());
        refund.forEach((a, kg) -> {
            int bits = (int) Math.floor(kg / (HdfvConfig.kgPerIngot() / 10));
            while (bits > 0) {
                int n = Math.min(bits, 64);
                Staging.give(Stock.bits(a, n), stores, drop);
                bits -= n;
            }
        });
        if (job.lampsBack() > 0) { Staging.give(new ItemStack(HdfvRefit.LAMP_UNIT.get(), job.lampsBack()), stores, drop); }
    }

    void changed() {
        setChanged();
        if (level != null && !level.isClientSide) { level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3); }
    }

    // ---------------------------------------------------------------- saving and the client's copy

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Report", report);
        tag.putByteArray("Pending", Packed.pack(pending));
        tag.putDouble("Scale", scale);
        if (target != null) { tag.putUUID("Target", target); }
        ContainerHelper.saveAllItems(tag, items.getItems(), registries);
        tag.putInt("Energy", energy.getEnergyStored());
        if (working) {
            tag.putBoolean("Working", true);
            tag.putDouble("WorkDone", workDone);
            tag.putDouble("WorkTotal", workTotal);
            tag.putDouble("Watts", watts);
            if (free) { tag.putBoolean("Free", true); }
        }
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        report = tag.getCompound("Report");
        pending = unpack(tag);
        scale = tag.contains("Scale") ? tag.getDouble("Scale") : 1;
        target = tag.hasUUID("Target") ? tag.getUUID("Target") : null;
        NonNullList<ItemStack> list = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, list, registries);
        for (int i = 0; i < SLOTS; i++) { items.setItem(i, list.get(i)); }
        energy.set(tag.getInt("Energy"));
        working = tag.getBoolean("Working");
        workDone = tag.getDouble("WorkDone");
        workTotal = tag.getDouble("WorkTotal");
        watts = tag.getDouble("Watts");
        free = tag.getBoolean("Free");
    }

    private static String unpack(CompoundTag tag) {
        try { return Packed.unpack(tag.getByteArray("Pending")); } catch (IllegalArgumentException e) { return ""; }
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.put("Report", report);
        tag.putByteArray("Pending", Packed.pack(pending));
        tag.putDouble("Scale", scale);
        PartVehicle v = vehicle();
        tag.putInt("Target", v == null ? -1 : v.getId());
        tag.putBoolean("Working", working);
        return tag;
    }

    @Override public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        report = tag.getCompound("Report");
        pending = unpack(tag);
        scale = tag.getDouble("Scale");
        targetId = tag.getInt("Target");
        working = tag.getBoolean("Working");
    }

    @Override public void onDataPacket(net.minecraft.network.Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
        handleUpdateTag(pkt.getTag(), registries);
    }

    @Override public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @Override public Component getDisplayName() { return Component.translatable("block.hdfv.refit_shop_controller"); }

    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) { return new RefitShopMenu(id, inventory, this); }

    /** What a slot takes: blank blueprints, a vehicle blueprint. */
    static boolean fits(int slot, ItemStack s) {
        return slot == BLANKS ? s.is(dev.mcmod.hdfv.neoforge.HdfvRegistry.BLANK_BLUEPRINT.get()) : s.is(HdfvAssembly.VEHICLE_BLUEPRINT.get());
    }

    static String fmt(double v) { return String.format(Locale.ROOT, v < 10 ? "%.2f" : "%.1f", v); }
}
