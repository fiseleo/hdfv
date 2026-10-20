package dev.mcmod.hdfv.neoforge.paint;

import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.VehicleBlueprint;
import dev.mcmod.aerocore.neoforge.vehicle.PartVehicle;
import dev.mcmod.hdfv.assembly.AssemblyPlan;
import dev.mcmod.hdfv.assembly.RoomScan;
import dev.mcmod.hdfv.look.Sticker;
import dev.mcmod.hdfv.neoforge.HdfvConfig;
import dev.mcmod.hdfv.neoforge.assembly.HdfvAssembly;
import dev.mcmod.hdfv.neoforge.assembly.HdfvVehicle;
import dev.mcmod.hdfv.neoforge.assembly.Packed;
import dev.mcmod.hdfv.neoforge.assembly.RoomDoorBlock;
import dev.mcmod.hdfv.neoforge.assembly.WorldGrid;
import dev.mcmod.hdfv.neoforge.power.Buffer;
import dev.mcmod.hdfv.neoforge.power.EnergyNetworks;
import dev.mcmod.hdfv.neoforge.power.PowerBlockEntity;
import dev.mcmod.hdfv.paint.PaintBoothRules;
import dev.mcmod.hdfv.paint.PaintJob;
import dev.mcmod.hdfv.paint.Repaint;
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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A paint booth's controller (0.13). Scans the booth like an assembly room (PaintBoothRules: closed, a door the vehicle
 * fits, extraction fans, work lights, one vehicle, power), takes the vehicle in it as the one to work on and keeps the
 * vehicle as it is to be (the pending blueprint: its looks and stickers edited from the screen, each edit checked to be
 * looks alone). Started, it takes the paint cans and film the job needs (none in creative mode), then works for the
 * job's hours drawing its power (spraying and laying film, then baking), the door shut and nobody aboard; a vehicle
 * driven or pushed out of the booth stops the job (what was taken is used up). Done, the vehicle takes the new looks
 * where it stands (PartVehicle.replaceBlueprint), and a vehicle blueprint in the controller's slot takes them too, part
 * by part (so vehicles built from it come out painted).
 */
public final class PaintBoothBlockEntity extends BlockEntity implements MenuProvider {
    public static final int CANS = 0, FILM = 1, BLUEPRINT = 2, SLOTS = 3;

    final SimpleContainer items = new SimpleContainer(SLOTS) {
        @Override public void setChanged() { super.setChanged(); PaintBoothBlockEntity.this.setChanged(); }
    };
    final Buffer energy = new Buffer((int) Math.ceil(2 * PowerBlockEntity.fePerTick(PaintJob.BAKE_KW * 1000)) + 10, Integer.MAX_VALUE, 0, this::setChanged);

    /** The last scan as the screen shows it: problems, the vehicle's name, the job. */
    CompoundTag report = new CompoundTag();
    RoomScan.Scan scan;
    /** The vehicle worked on (null: none) and, on a client, its entity id. */
    UUID target;
    int targetId = -1;
    /** The vehicle as it is to be: its blueprint as JSON ("": none). */
    String pending = "";
    /** A job going: the work done and needed (ticks of full power), the spraying's share of them; what is owed of an FE. */
    boolean working;
    double workDone, workTotal, sprayTotal, owedFe;
    boolean starved;

    /** What the screen sees of a job: 0 idle, 1 working; progress per mille; 1 when it waits for power; energy stored (FE). */
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

    public PaintBoothBlockEntity(BlockPos pos, BlockState state) { super(HdfvPaint.BOOTH.get(), pos, state); }

    public CompoundTag report() { return report; }
    public String pendingJson() { return pending; }
    public int targetId() { return targetId; }
    public boolean working() { return working; }

    /** The pending blueprint (null: none). */
    VehicleBlueprint pendingBlueprint() {
        if (pending.isEmpty()) { return null; }
        try { return VehicleBlueprint.fromJsonText(pending); } catch (RuntimeException e) { return null; }
    }

    /** The vehicle worked on, if it is still here (server). */
    PartVehicle vehicle() {
        if (target == null || !(level instanceof ServerLevel server)) { return null; }
        return server.getEntity(target) instanceof PartVehicle v && v.isAlive() ? v : null;
    }

    // ---------------------------------------------------------------- the booth

    /** Scans the booth, takes the vehicle in it, and keeps the report. */
    public RoomScan.Scan scan() {
        if (level == null) { return null; }
        Direction f = getBlockState().getValue(PaintBoothBlock.FACING);
        scan = RoomScan.scan(new WorldGrid(level), worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), f.getStepX(), f.getStepZ());
        int exhausts = 0, lights = 0;
        List<PartVehicle> vehicles = List.of();
        if (scan.closed()) {
            for (BlockPos p : BlockPos.betweenClosed(scan.minX() - 1, scan.minY() - 1, scan.minZ() - 1, scan.maxX() + 1, scan.maxY() + 1, scan.maxZ() + 1)) {
                Block b = level.getBlockState(p).getBlock();
                if (b == HdfvPaint.EXHAUST.get()) { exhausts++; }
                else if (b == HdfvAssembly.WORK_LIGHT.get()) { lights++; }
            }
            vehicles = level.getEntitiesOfClass(PartVehicle.class, box());
        }
        PartVehicle v = vehicles.size() == 1 ? vehicles.get(0) : null;
        double width = 0, height = 0;
        if (v != null && v.blueprint() != null) {
            try {
                AssemblyPlan plan = AssemblyPlan.of(v.blueprint());
                width = plan.widthM();
                height = plan.heightM();
            } catch (RuntimeException ignored) { }
        }
        EnergyNetworks.pull(level, worldPosition, energy);
        boolean building = v instanceof HdfvVehicle hv && !hv.assembled();
        List<PaintBoothRules.Problem> problems = PaintBoothRules.check(new PaintBoothRules.Booth(scan, exhausts, lights, vehicles.size(), width, height, building,
                energy.getEnergyStored() > 0));
        if (!working) {
            if (v == null) { target = null; pending = ""; }
            else if (!v.getUUID().equals(target) || pending.isEmpty()) {
                target = v.getUUID();
                pending = v.blueprint() == null ? "" : v.blueprint().toJsonText();
            }
        }
        CompoundTag r = new CompoundTag();
        ListTag list = new ListTag();
        for (PaintBoothRules.Problem p : problems) {
            CompoundTag t = new CompoundTag();
            t.putString("Key", p.key());
            ListTag args = new ListTag();
            for (String a : p.args()) { args.add(StringTag.valueOf(a)); }
            t.put("Args", args);
            list.add(t);
        }
        r.put("Problems", list);
        r.putIntArray("Counts", new int[]{exhausts, lights, vehicles.size()});
        if (v != null && v.blueprint() != null) { r.putString("Vehicle", v.blueprint().name()); }
        report = r;
        summarize();
        changed();
        return scan;
    }

    /** The booth's inside as a box (the scan's). */
    AABB box() { return new AABB(scan.minX(), scan.minY(), scan.minZ(), scan.maxX() + 1, scan.maxY() + 1, scan.maxZ() + 1); }

    /** Puts the job (from the vehicle as it is to as it is to be) in the report. */
    void summarize() {
        PartVehicle v = vehicle();
        VehicleBlueprint next = pendingBlueprint();
        PaintJob job = v == null || v.blueprint() == null || next == null ? PaintJob.NONE : PaintJob.of(v.blueprint(), next);
        CompoundTag j = new CompoundTag();
        j.putDouble("Repaint", job.repaintM2());
        j.putDouble("Film", job.stickerM2());
        j.putInt("Cans", job.cans());
        j.putInt("Films", job.films());
        j.putDouble("Hours", job.hours());
        j.putDouble("KWh", job.kWh());
        j.putInt("Stickers", next == null ? 0 : Repaint.count(next));
        report.put("Job", j);
    }

    public static List<Component> problems(CompoundTag report) {
        List<Component> out = new ArrayList<>();
        for (Tag t : report.getList("Problems", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            Object[] args = c.getList("Args", Tag.TAG_STRING).stream().map(Tag::getAsString).toArray();
            out.add(Component.translatable("hdfv.paint_booth.problem." + c.getString("Key"), args));
        }
        return out;
    }

    // ---------------------------------------------------------------- edits from the screen

    /** What the screen asks. */
    public enum Op { LOOK, ADD, SET, REMOVE, RESET, START, CANCEL, SCAN, DOOR }

    /** An edit: a part's look (page, param, text), or a sticker (by index) of a part; or one of the booth's actions. */
    public record Edit(Op op, String part, String page, String param, String text, int index, Sticker sticker) {
        public static Edit of(Op op) { return new Edit(op, "", "", "", "", -1, null); }
    }

    /** Applies an edit; a refusal to show, or null. */
    public Component apply(Edit e, ServerPlayer player) {
        if (e.op() == Op.SCAN) { scan(); return null; }
        if (e.op() == Op.DOOR) { return toggleDoor() ? null : Component.translatable("hdfv.paint_booth.problem.no_door"); }
        if (e.op() == Op.CANCEL) { return cancel(Component.translatable("hdfv.paint_booth.cancelled")); }
        if (working) { return Component.translatable("hdfv.paint_booth.busy"); }
        if (e.op() == Op.START) { return start(player); }
        PartVehicle v = vehicle();
        if (v == null || v.blueprint() == null) { return Component.translatable("hdfv.paint_booth.problem.no_vehicle"); }
        VehicleBlueprint current = v.blueprint(), next = pendingBlueprint();
        if (e.op() == Op.RESET || next == null) {
            pending = current.toJsonText();
            summarize();
            changed();
            return null;
        }
        VehicleBlueprint edited;
        try {
            PartDesign d = next.parts().stream().filter(p -> p.id().equals(e.part())).map(VehicleBlueprint.Entry::design).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("no part " + e.part()));
            PartDesign changed = switch (e.op()) {
                case LOOK -> Repaint.setLook(d, e.page(), e.param(), e.text());
                case ADD -> {
                    if (Repaint.count(next) >= Sticker.MAX) { throw new IllegalArgumentException("at most " + Sticker.MAX + " stickers"); }
                    List<Sticker> s = new ArrayList<>(Repaint.stickers(d));
                    s.add(e.sticker());
                    yield Repaint.withStickers(d, s);
                }
                case SET -> {
                    List<Sticker> s = new ArrayList<>(Repaint.stickers(d));
                    s.set(e.index(), e.sticker());
                    yield Repaint.withStickers(d, s);
                }
                case REMOVE -> {
                    List<Sticker> s = new ArrayList<>(Repaint.stickers(d));
                    s.remove(e.index());
                    yield Repaint.withStickers(d, s);
                }
                default -> d;
            };
            if (changed.check().stream().anyMatch(i -> i.kind() == DesignIssue.Kind.INVALID)) {
                return Component.translatable("hdfv.paint_booth.invalid", changed.check().stream().filter(i -> i.kind() == DesignIssue.Kind.INVALID)
                        .map(DesignIssue::message).findFirst().orElse(""));
            }
            edited = Repaint.withPart(next, e.part(), changed);
        } catch (RuntimeException ex) {
            return Component.translatable("hdfv.paint_booth.invalid", String.valueOf(ex.getMessage()));
        }
        if (!Repaint.looksOnly(current, edited)) { return Component.translatable("hdfv.paint_booth.not_looks"); }
        pending = edited.toJsonText();
        summarize();
        changed();
        return null;
    }

    /** Opens or shuts the booth's door. */
    boolean toggleDoor() {
        if (scan == null) { scan(); }
        if (scan == null || scan.doors().isEmpty() || level == null) { return false; }
        RoomScan.Door d = scan.doors().get(0);
        BlockPos at = new BlockPos(d.minX(), d.minY(), d.minZ());
        RoomDoorBlock.setOpen(level, at, !level.getBlockState(at).getValue(RoomDoorBlock.OPEN));
        return true;
    }

    /** Starts the job: the booth must be ready, the vehicle changed, the materials here (taken now, none in creative mode). */
    Component start(ServerPlayer player) {
        scan();
        List<Component> problems = problems(report);
        if (!problems.isEmpty()) { return problems.get(0); }
        PartVehicle v = vehicle();
        VehicleBlueprint next = pendingBlueprint();
        if (v == null || v.blueprint() == null || next == null) { return Component.translatable("hdfv.paint_booth.problem.no_vehicle"); }
        if (!Repaint.looksOnly(v.blueprint(), next)) { return Component.translatable("hdfv.paint_booth.not_looks"); }
        PaintJob job = PaintJob.of(v.blueprint(), next);
        if (job.empty()) { return Component.translatable("hdfv.paint_booth.nothing"); }
        boolean free = player != null && player.getAbilities().instabuild;
        if (!free) {
            if (count(CANS, HdfvPaint.PAINT_CAN.get()) < job.cans()) { return Component.translatable("hdfv.paint_booth.need_cans", job.cans()); }
            if (count(FILM, HdfvPaint.STICKER_FILM.get()) < job.films()) { return Component.translatable("hdfv.paint_booth.need_film", job.films()); }
            items.removeItem(CANS, job.cans());
            items.removeItem(FILM, job.films());
        }
        double ticksPerHour = HdfvConfig.ticksPerWorkHour();
        workTotal = Math.max(1, Math.ceil(job.hours() * ticksPerHour));
        sprayTotal = Math.ceil((job.sprayHours() + job.filmHours()) * ticksPerHour);
        workDone = 0;
        owedFe = 0;
        working = true;
        for (RoomScan.Door d : scan.doors()) { RoomDoorBlock.setOpen(level, new BlockPos(d.minX(), d.minY(), d.minZ()), false); }
        changed();
        return null;
    }

    private int count(int slot, net.minecraft.world.item.Item item) {
        ItemStack s = items.getItem(slot);
        return s.is(item) ? s.getCount() : 0;
    }

    /** Stops a job (what it took is used up); the reason in the report. */
    Component cancel(Component why) {
        if (!working) { return null; }
        working = false;
        workDone = 0;
        report.putString("Message", Component.Serializer.toJson(why, level.registryAccess()));
        changed();
        return why;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, PaintBoothBlockEntity be) {
        boolean was = be.working;
        tickJob(level, be);
        if (was && !be.working && be.workDone >= be.workTotal) { dev.mcmod.hdfv.neoforge.sound.SoundSync.event(level, net.minecraft.world.phys.Vec3.atCenterOf(pos), dev.mcmod.hdfv.sound.MachineSound.Event.DONE, 0); }
        boolean spraying = be.workDone < be.sprayTotal;
        dev.mcmod.hdfv.neoforge.sound.SoundSync.report(be, be.working ? dev.mcmod.hdfv.sound.MachineSound.plant(spraying ? dev.mcmod.aerocore.api.acoustics.ProcessNoise.Process.SPRAY : dev.mcmod.aerocore.api.acoustics.ProcessNoise.Process.CURING, (spraying ? PaintJob.SPRAY_KW : PaintJob.BAKE_KW) * 1000, 0.9, true) : null);
    }

    private static void tickJob(Level level, PaintBoothBlockEntity be) {
        if (!be.working) { return; }
        if (be.scan == null) { be.scan(); }
        PartVehicle v = be.vehicle();
        if (v == null || be.scan == null || !be.scan.closed() || !be.box().contains(v.position())) {
            be.cancel(Component.translatable("hdfv.paint_booth.left"));
            return;
        }
        v.ejectPassengers();
        double watts = (be.workDone < be.sprayTotal ? PaintJob.SPRAY_KW : PaintJob.BAKE_KW) * 1000;
        double rate = be.power(watts);
        be.starved = rate < 1;
        be.workDone += rate;
        if (be.workDone >= be.workTotal) { be.finish(v); }
        else if (level.getGameTime() % 20 == 0) { be.setChanged(); }
    }

    /** The share of this tick's work its power allows (0 to 1), drawing it. */
    private double power(double watts) {
        double need = PowerBlockEntity.fePerTick(watts);
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

    /** The job done: the vehicle takes its new looks where it stands, and so does a vehicle blueprint in the slot. */
    void finish(PartVehicle v) {
        working = false;
        workDone = workTotal;
        VehicleBlueprint next = pendingBlueprint();
        Component message;
        if (next == null || !v.replaceBlueprint(next)) {
            message = Component.translatable("hdfv.paint_booth.failed");
        } else {
            message = Component.translatable("hdfv.paint_booth.done");
            ItemStack drawing = items.getItem(BLUEPRINT);
            String layout = drawing.is(HdfvAssembly.VEHICLE_BLUEPRINT.get()) ? drawing.get(HdfvAssembly.VEHICLE_LAYOUT.get()) : null;
            if (layout != null) {
                try {
                    VehicleBlueprint l = VehicleBlueprint.fromJsonText(layout);
                    for (VehicleBlueprint.Entry e : next.parts()) {
                        VehicleBlueprint.Entry was = l.parts().stream().filter(p -> p.id().equals(e.id())).findFirst().orElse(null);
                        if (was != null && !was.design().equals(e.design()) && Repaint.looksOnly(was.design(), e.design())) { l = Repaint.withPart(l, e.id(), e.design()); }
                    }
                    drawing.set(HdfvAssembly.VEHICLE_LAYOUT.get(), l.toJsonText());
                    items.setChanged();
                } catch (RuntimeException ignored) { }
            }
        }
        report.putString("Message", Component.Serializer.toJson(message, level.registryAccess()));
        summarize();
        changed();
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
        if (target != null) { tag.putUUID("Target", target); }
        ContainerHelper.saveAllItems(tag, items.getItems(), registries);
        tag.putInt("Energy", energy.getEnergyStored());
        if (working) {
            tag.putBoolean("Working", true);
            tag.putDouble("WorkDone", workDone);
            tag.putDouble("WorkTotal", workTotal);
            tag.putDouble("SprayTotal", sprayTotal);
        }
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        report = tag.getCompound("Report");
        pending = unpack(tag);
        target = tag.hasUUID("Target") ? tag.getUUID("Target") : null;
        NonNullList<ItemStack> list = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, list, registries);
        for (int i = 0; i < SLOTS; i++) { items.setItem(i, list.get(i)); }
        energy.set(tag.getInt("Energy"));
        working = tag.getBoolean("Working");
        workDone = tag.getDouble("WorkDone");
        workTotal = tag.getDouble("WorkTotal");
        sprayTotal = tag.getDouble("SprayTotal");
    }

    private static String unpack(CompoundTag tag) {
        try { return Packed.unpack(tag.getByteArray("Pending")); } catch (IllegalArgumentException e) { return ""; }
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.put("Report", report);
        tag.putByteArray("Pending", Packed.pack(pending));
        PartVehicle v = vehicle();
        tag.putInt("Target", v == null ? -1 : v.getId());
        tag.putBoolean("Working", working);
        return tag;
    }

    @Override public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        report = tag.getCompound("Report");
        pending = unpack(tag);
        targetId = tag.getInt("Target");
        working = tag.getBoolean("Working");
    }

    @Override public void onDataPacket(net.minecraft.network.Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
        handleUpdateTag(pkt.getTag(), registries);
    }

    @Override public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @Override public Component getDisplayName() { return Component.translatable("block.hdfv.paint_booth_controller"); }

    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) { return new PaintBoothMenu(id, inventory, this); }

    /** What a slot takes: paint cans, sticker film, a vehicle blueprint. */
    static boolean fits(int slot, ItemStack s) {
        return switch (slot) {
            case CANS -> s.is(HdfvPaint.PAINT_CAN.get());
            case FILM -> s.is(HdfvPaint.STICKER_FILM.get());
            default -> s.is(HdfvAssembly.VEHICLE_BLUEPRINT.get());
        };
    }
}
