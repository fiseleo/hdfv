package dev.mcmod.hdfv.neoforge.assembly;

import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.vehicle.DesignException;
import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.VehicleBlueprint;
import dev.mcmod.hdfv.assembly.AssemblyPlan;
import dev.mcmod.hdfv.assembly.AutoWiring;
import dev.mcmod.hdfv.assembly.Placing;
import dev.mcmod.hdfv.assembly.Room;
import dev.mcmod.hdfv.assembly.RoomRules;
import dev.mcmod.hdfv.assembly.RoomScan;
import dev.mcmod.hdfv.neoforge.HdfvRegistry;
import dev.mcmod.hdfv.neoforge.machine.Workpieces;
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
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * An assembly room's controller (0.7): scans the room in front of it and keeps what it found; holds the drawings of the
 * parts (eight part blueprints and a vehicle blueprint) and the vehicle laid out from them, which it wires (AutoWiring),
 * plans (AssemblyPlan) and checks the room against; opens and closes the room's door. What it holds reaches players
 * looking at its screen with its block entity data.
 * <p>
 * 0.8: it rolls the finished vehicle out once the door has finished opening; while the crew works the sparks fly and
 * the tools sound where the build is (BuildSequence); the room's box and whether it has a crane go to the clients for
 * its renderer (the crane and the console's screen).
 */
public final class RoomControllerBlockEntity extends BlockEntity implements MenuProvider {
    public static final int PART_DRAWINGS = 8, VEHICLE_DRAWING = 8, DRAWINGS = 9;
    public final Room room;
    final SimpleContainer drawings = new SimpleContainer(DRAWINGS) {
        @Override public void setChanged() { super.setChanged(); RoomControllerBlockEntity.this.setChanged(); }
    };
    /** The last scan as the screen shows it (empty: never scanned). */
    CompoundTag report = new CompoundTag();
    /** The last scan itself (not saved: scanned again when needed). */
    RoomScan.Scan scan;
    /** The layout: the vehicle's parts and mounts as JSON, unwired ("": none). */
    String layout = "";
    private String planFor;
    private AssemblyPlan plan;
    private List<String> issues = List.of();
    /** The vehicle being built here (null: none), the work done and needed (crew-ticks) and the cure (ticks). */
    java.util.UUID building;
    long workDone, workTotal, cureDone, cureTotal;
    /** People working (welding stations being worked), recounted once a second. */
    int crew;
    private int clock;
    /** Rolling the vehicle out: waiting for the door to open. */
    boolean launching;
    /** The client's copy of the room's box (min x, y, z, max x, y, z; empty: unknown) and whether it has a crane. */
    int[] shownBox = new int[0];
    boolean shownCrane;

    public int[] shownBox() { return shownBox; }
    public boolean shownCrane() { return shownCrane; }
    /** The client's copy of the build: 0 none, 1 building, 2 assembled; and how far. */
    int shownState;
    float shownBuilt;
    public int shownState() { return shownState; }
    public float shownBuilt() { return shownBuilt; }
    /** What the screen sees of the build: state (0 none, 1 building, 2 assembled), progress per mille, crew. */
    final net.minecraft.world.inventory.ContainerData data = new net.minecraft.world.inventory.ContainerData() {
        @Override public int get(int i) {
            return switch (i) {
                case 0 -> building == null ? 0 : finished() ? 2 : 1;
                case 1 -> (int) Math.round(1000 * built());
                default -> crew;
            };
        }
        @Override public void set(int i, int v) { }
        @Override public int getCount() { return 3; }
    };

    public RoomControllerBlockEntity(BlockPos pos, BlockState state) {
        super(HdfvAssembly.ROOM_CONTROLLER.get(), pos, state);
        this.room = ((RoomControllerBlock) state.getBlock()).room;
    }

    public CompoundTag report() { return report; }
    public String layoutJson() { return layout; }

    /** The layout (an empty one when there is none). */
    public VehicleBlueprint layout() {
        if (layout.isEmpty()) { return new VehicleBlueprint("vehicle", List.of(), List.of()); }
        try { return VehicleBlueprint.fromJsonText(layout); } catch (RuntimeException e) { return new VehicleBlueprint("vehicle", List.of(), List.of()); }
    }

    /** The plan of the laid-out vehicle, wired (null: none, or it does not hold together: see issues). */
    public AssemblyPlan plan() {
        if (layout.equals(planFor)) { return plan; }
        planFor = layout;
        plan = null;
        issues = List.of();
        VehicleBlueprint l = layout();
        if (l.parts().isEmpty()) { return null; }
        try {
            plan = AssemblyPlan.of(AutoWiring.wire(l));
        } catch (DesignException e) {
            issues = e.issues().stream().filter(i -> i.kind() == DesignIssue.Kind.INVALID).map(i -> i.parameter() + ": " + i.message()).limit(6).toList();
        } catch (RuntimeException e) {
            issues = List.of(String.valueOf(e.getMessage()));
        }
        return plan;
    }

    public List<String> issues() { plan(); return issues; }

    /** 0.17: the riveting machine at the room that sets rivets soonest (null: none; the rivets are set by hand). */
    private dev.mcmod.hdfv.manufacture.Head riveter;

    public dev.mcmod.hdfv.manufacture.Head riveter() { return riveter; }

    /** The best formed riveting machine in the room's bounds or within two blocks of them (null: none). */
    private dev.mcmod.hdfv.manufacture.Head riveterAt(RoomScan.Scan s) {
        if (level == null || s == null || !s.closed()) { return null; }
        dev.mcmod.hdfv.manufacture.Head best = null;
        for (int cx = (s.minX() - 2) >> 4; cx <= (s.maxX() + 2) >> 4; cx++) {
            for (int cz = (s.minZ() - 2) >> 4; cz <= (s.maxZ() + 2) >> 4; cz++) {
                if (!level.hasChunk(cx, cz)) { continue; }
                for (var e : level.getChunk(cx, cz).getBlockEntities().values()) {
                    if (!(e instanceof dev.mcmod.hdfv.neoforge.machine.MachineBlockEntity m) || m.station != dev.mcmod.hdfv.manufacture.Station.RIVETER || !m.formed()) { continue; }
                    BlockPos p = e.getBlockPos();
                    if (p.getX() < s.minX() - 2 || p.getX() > s.maxX() + 2 || p.getZ() < s.minZ() - 2 || p.getZ() > s.maxZ() + 2
                            || p.getY() < s.minY() - 2 || p.getY() > s.maxY() + 1) { continue; }
                    var t = m.tool();
                    if (t == null || t.head() == null) { continue; }
                    if (best == null || dev.mcmod.hdfv.manufacture.Machining.roomRivetS(t.head()) < dev.mcmod.hdfv.manufacture.Machining.roomRivetS(best)) { best = t.head(); }
                }
            }
        }
        return best;
    }

    /** Scans the room and keeps the report. */
    public RoomScan.Scan scan() {
        if (level == null) { return null; }
        Direction f = getBlockState().getValue(RoomControllerBlock.FACING);
        scan = RoomScan.scan(new WorldGrid(level), worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), f.getStepX(), f.getStepZ());
        riveter = riveterAt(scan);
        AssemblyPlan p = plan();
        List<RoomRules.Problem> problems = RoomRules.check(room, scan, p);
        CompoundTag r = new CompoundTag();
        r.putBoolean("Closed", scan.closed());
        r.putIntArray("Leak", new int[]{scan.leakX(), scan.leakY(), scan.leakZ()});
        r.putIntArray("Size", new int[]{scan.lengthX(), scan.heightY(), scan.widthZ(), scan.cells()});
        RoomScan.Door door = RoomRules.door(scan, p);
        r.putInt("Doors", scan.doors().size());
        if (door != null) { r.putIntArray("Door", new int[]{door.widthM(), door.heightM()}); }
        r.putInt("CraneKg", (int) Math.round(scan.craneCells() * RoomRules.CRANE_KG_PER_CELL));
        r.putInt("Stations", scan.weldingStations());
        r.putInt("Slipway", scan.slipwayCells());
        ListTag list = new ListTag();
        for (RoomRules.Problem pr : problems) {
            CompoundTag t = new CompoundTag();
            t.putString("Key", pr.key());
            ListTag args = new ListTag();
            for (Object a : pr.args()) { args.add(StringTag.valueOf(String.valueOf(a))); }
            t.put("Args", args);
            list.add(t);
        }
        r.put("Problems", list);
        if (p != null) {
            CompoundTag v = new CompoundTag();
            v.putString("Room", p.room().name().toLowerCase(Locale.ROOT));
            v.putIntArray("Size", new int[]{(int) Math.round(10 * p.lengthM()), (int) Math.round(10 * p.widthM()), (int) Math.round(10 * p.heightM())});
            v.putInt("Hours", (int) Math.ceil(p.laborHours(riveter)));
            v.putInt("Heaviest", (int) Math.round(p.heaviestKg()));
            v.putInt("Parts", p.parts().stream().mapToInt(AssemblyPlan.Need::count).sum());
            v.putInt("Pieces", p.pieces().size());
            r.put("Vehicle", v);
        }
        report = r;
        changed();
        return scan;
    }

    /** The room's problems from the last scan (keys). */
    public List<String> problemKeys() {
        return report.getList("Problems", Tag.TAG_COMPOUND).stream().map(t -> ((CompoundTag) t).getString("Key")).toList();
    }

    /** A problem of the report as a message (a room's name, and 0.14 the side a part sticks out by, in words). */
    public static Component problem(CompoundTag t) {
        String key = t.getString("Key");
        List<Object> args = new ArrayList<>();
        for (Tag a : t.getList("Args", Tag.TAG_STRING)) {
            String s = a.getAsString();
            if (key.equals("wrong_room")) { args.add(Component.translatable("hdfv.room." + s)); }
            else if (key.equals("part_does_not_fit") && args.size() == 1) { args.add(Component.translatable("hdfv.room.side." + s)); }
            else { args.add(s); }
        }
        return Component.translatable("hdfv.room.problem." + key, args.toArray());
    }

    /** Opens or closes the door the vehicle leaves by (scanning first if need be). */
    public boolean toggleDoor() {
        if (scan == null) { scan(); }
        RoomScan.Door door = scan == null ? null : RoomRules.door(scan, plan());
        if (door == null || level == null) { return false; }
        BlockPos at = new BlockPos(door.minX(), door.minY(), door.minZ());
        boolean open = !level.getBlockState(at).getValue(RoomDoorBlock.OPEN);
        RoomDoorBlock.setOpen(level, at, open);
        return true;
    }

    // ---------------------------------------------------------------- building

    public boolean finished() { return building != null && workDone >= workTotal && cureDone >= cureTotal; }
    public float built() { return workTotal + cureTotal <= 0 ? 1f : (float) Math.min(1.0, (double) (Math.min(workDone, workTotal) + cureDone) / (workTotal + cureTotal)); }

    /** The chests and other stores at the room (from the last scan). */
    List<net.neoforged.neoforge.items.IItemHandler> stores() {
        List<net.neoforged.neoforge.items.IItemHandler> out = new ArrayList<>();
        if (scan == null || level == null) { return out; }
        for (int[] c : scan.storage()) {
            var h = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, new BlockPos(c[0], c[1], c[2]), null);
            if (h != null) { out.add(h); }
        }
        return out;
    }

    /** The vehicle being built (null when it is gone). */
    public HdfvVehicle vehicle() {
        return building != null && level instanceof net.minecraft.server.level.ServerLevel sl && sl.getEntity(building) instanceof HdfvVehicle v ? v : null;
    }

    /**
     * Starts building the laid-out vehicle: the room must be right for it and hold its materials (taken now); the
     * vehicle appears on the floor, nose to the door, unassembled. Returns why not (null when started).
     */
    public Component start() {
        if (building != null) { return Component.translatable("hdfv.build.busy"); }
        AssemblyPlan p = plan();
        if (p == null) { return Component.translatable(layout.isEmpty() ? "hdfv.layout.empty" : "hdfv.build.bad_layout"); }
        scan();
        if (!problemKeys().isEmpty()) { return Component.translatable("hdfv.build.room"); }
        List<net.neoforged.neoforge.items.IItemHandler> stores = stores();
        List<Component> missing = Staging.missing(p, stores);
        if (!missing.isEmpty()) {
            net.minecraft.network.chat.MutableComponent m = Component.translatable("hdfv.build.missing");
            for (int i = 0; i < missing.size() && i < 6; i++) { m.append("\n- ").append(missing.get(i)); }
            if (missing.size() > 6) { m.append(Component.translatable("hdfv.build.more", missing.size() - 6)); }
            return m;
        }
        Staging.take(p, stores);
        HdfvVehicle v = HdfvAssembly.VEHICLE.get().create(level);
        if (v == null) { return Component.literal("vehicle"); }
        RoomScan.Door door = RoomRules.door(scan, p);
        dev.mcmod.aerocore.api.Orientation q = facing(door);
        Vec3d boxCentre = p.min().add(p.max()).scale(0.5), turned = q.rotate(new Vec3d(boxCentre.x(), 0, boxCentre.z()));
        Vec3d roomCentre = new Vec3d(0.5 * (scan.minX() + scan.maxX() + 1), scan.minY(), 0.5 * (scan.minZ() + scan.maxZ() + 1));
        Vec3d origin = new Vec3d(roomCentre.x() - turned.x(), scan.minY() - p.min().y() + 0.02, roomCentre.z() - turned.z());
        v.setBlueprint(AutoWiring.wire(layout()), origin, q);
        for (int k = 0; k < v.tankCount(); k++) { v.setTank(k, "", 0, 1000); }   // it leaves the room with empty tanks (0.8)
        v.setBuilt(0);
        v.setRoom(worldPosition);
        level.addFreshEntity(v);
        building = v.getUUID();
        workDone = 0; cureDone = 0;
        workTotal = Math.max(1, (long) Math.ceil(p.laborHours(riveter) * dev.mcmod.hdfv.neoforge.HdfvConfig.ticksPerWorkHour()));
        cureTotal = (long) Math.ceil(p.cureHours() * dev.mcmod.hdfv.neoforge.HdfvConfig.ticksPerWorkHour());
        crew = crew();
        changed();
        return null;
    }

    /** A vehicle's turn with its nose (+x) out through a door. */
    static dev.mcmod.aerocore.api.Orientation facing(RoomScan.Door door) {
        return dev.mcmod.aerocore.api.Orientation.rotationVector(Vec3d.Y.scale(Math.atan2(-door.outZ(), door.outX())));
    }

    /** Welding stations in the room being worked (by a player at its screen or a welder beside it); a plain bolt-up needs only a person in the room. */
    int crew() {
        if (scan == null || level == null) { return 0; }
        int n = 0;
        for (int[] c : scan.stations()) {
            if (level.getBlockEntity(new BlockPos(c[0], c[1], c[2])) instanceof dev.mcmod.hdfv.neoforge.machine.MachineBlockEntity m && m.operators() > 0) { n++; }
        }
        AssemblyPlan p = plan();
        if (n == 0 && p != null && !p.needsWelding()) {
            for (Player pl : level.players()) { if (scan.holds(pl.getX(), pl.getY(), pl.getZ())) { n = 1; break; } }
        }
        return n;
    }

    public static void tick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, RoomControllerBlockEntity be) { be.tick(); }

    void tick() {
        tickBuild();
        dev.mcmod.hdfv.neoforge.sound.SoundSync.report(this, building != null && crew > 0 && workDone < workTotal ? dev.mcmod.hdfv.sound.MachineSound.plant(dev.mcmod.aerocore.api.acoustics.ProcessNoise.Process.FAN, 4000, 0.6, true) : null);
    }

    private void tickBuild() {
        if (building == null) { return; }
        if (launching) {
            BlockPos d = door();
            if (d == null || RoomDoorBlock.fullyOpen(level, d)) { launching = false; rollOut(); }
            return;
        }
        if (clock++ % 20 == 0) {
            HdfvVehicle v = vehicle();
            if (v == null && clock > 40) { building = null; changed(); return; }
            if (scan == null) { scan(); }
            crew = crew();
            if (v != null) { v.setBuilt(finished() ? 1f : Math.min(built(), 0.999f)); }
        }
        if (clock % 40 == 0) { changed(); }
        if (workDone < workTotal) { workDone += crew; if (crew > 0) { show(); } }
        else if (cureDone < cureTotal) { cureDone++; }
        else { return; }
        if (finished()) {
            HdfvVehicle v = vehicle();
            if (v != null) { v.setBuilt(1f); }
            changed();
        } else if (clock % 200 == 0) { setChanged(); }
    }

    /** The door the vehicle leaves by (a block of it; null: none). */
    BlockPos door() {
        RoomScan.Door door = scan == null ? null : RoomRules.door(scan, plan());
        return door == null ? null : new BlockPos(door.minX(), door.minY(), door.minZ());
    }

    /** Takes the finished vehicle out: opens the door and, once it is open, sets it beyond, on the water from a shipyard. Returns why not. */
    public Component launch() {
        HdfvVehicle v = vehicle();
        if (v == null || !finished()) { return Component.translatable("hdfv.build.not_done"); }
        if (scan == null) { scan(); }
        AssemblyPlan p = plan();
        RoomScan.Door door = scan == null ? null : RoomRules.door(scan, p);
        if (door == null || p == null) { return Component.translatable("hdfv.room.problem.no_door"); }
        BlockPos at = new BlockPos(door.minX(), door.minY(), door.minZ());
        if (!RoomDoorBlock.fullyOpen(level, at)) {
            RoomDoorBlock.setOpen(level, at, true);
            launching = true;
            return null;
        }
        rollOut();
        return null;
    }

    /** Sets the finished vehicle beyond the open door. */
    private void rollOut() {
        HdfvVehicle v = vehicle();
        AssemblyPlan p = plan();
        RoomScan.Door door = scan == null ? null : RoomRules.door(scan, p);
        if (v == null || door == null || p == null) { return; }
        // past the door by half the vehicle's length and a metre and a half
        double reach = 0.5 * p.lengthM() + 1.5;
        double cx = door.outX() != 0 ? (door.outX() > 0 ? door.maxX() + 1 : door.minX()) + door.outX() * reach : 0.5 * (door.minX() + door.maxX() + 1);
        double cz = door.outZ() != 0 ? (door.outZ() > 0 ? door.maxZ() + 1 : door.minZ()) + door.outZ() * reach : 0.5 * (door.minZ() + door.maxZ() + 1);
        double ground = door.minY();
        if (room == Room.SHIPYARD) {
            BlockPos at = BlockPos.containing(cx, door.minY(), cz);
            while (at.getY() > level.getMinBuildHeight() && !level.getFluidState(at).is(net.minecraft.tags.FluidTags.WATER)) { at = at.below(); }
            while (level.getFluidState(at.above()).is(net.minecraft.tags.FluidTags.WATER)) { at = at.above(); }
            ground = at.getY() + 0.9;
        }
        dev.mcmod.aerocore.api.Orientation q = facing(door);
        Vec3d boxCentre = p.min().add(p.max()).scale(0.5), turned = q.rotate(new Vec3d(boxCentre.x(), 0, boxCentre.z()));
        v.place(new Vec3d(cx - turned.x(), ground - p.min().y() + 0.02, cz - turned.z()), q);   // 0.14: keeps its tanks (setBlueprint filled them again)
        v.setRoom(null);
        building = null;
        changed();
    }

    /** The sparks and the sound of the work going on (every few ticks), where the build is. */
    private void show() {
        if (!(level instanceof net.minecraft.server.level.ServerLevel server) || clock % 4 != 0) { return; }
        HdfvVehicle v = vehicle();
        if (v == null || v.vehicle() == null || v.blueprint() == null) { return; }
        dev.mcmod.hdfv.assembly.BuildSequence seq = sequence(v.blueprint());
        dev.mcmod.hdfv.assembly.BuildSequence.Step step = seq.at(built());
        if (step == null || step.part() == null) { return; }
        var installed = v.vehicle().installed(step.part());
        if (installed == null) { return; }
        Vec3d local = installed.mount().position();
        if (step.kind() == dev.mcmod.hdfv.assembly.BuildSequence.Kind.SKIN || step.kind() == dev.mcmod.hdfv.assembly.BuildSequence.Kind.SKELETON) {
            AssemblyPlan p = plan();
            if (p != null) { local = new Vec3d(p.max().x() - step.within(built()) * p.lengthM(), local.y(), local.z()); }
        }
        Vec3d com = v.vehicle().properties().centerOfMassLocal();
        Vec3d world = v.visualOrientation(1).rotate(local.subtract(com)).add(new Vec3d(v.getX(), v.getY(), v.getZ()));
        switch (step.kind()) {
            case SKELETON, SKIN -> {
                server.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, world.x(), world.y(), world.z(), 8, 0.25, 0.25, 0.25, 0.2);
                if (clock % 12 == 0) { dev.mcmod.hdfv.neoforge.sound.SoundSync.event(level, new net.minecraft.world.phys.Vec3(world.x(), world.y(), world.z()), dev.mcmod.hdfv.sound.MachineSound.Event.WELD, 0); }
            }
            case FIT -> {
                if (clock % 16 == 0) { dev.mcmod.hdfv.neoforge.sound.SoundSync.event(level, new net.minecraft.world.phys.Vec3(world.x(), world.y(), world.z()), dev.mcmod.hdfv.sound.MachineSound.Event.RIVET, 0); }
                if (clock % 8 == 0) { server.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT, world.x(), world.y(), world.z(), 3, 0.2, 0.2, 0.2, 0.1); }
            }
            case HOIST -> {
                if (clock % 20 == 0) { dev.mcmod.hdfv.neoforge.sound.SoundSync.event(level, new net.minecraft.world.phys.Vec3(world.x(), world.y() + 2, world.z()), dev.mcmod.hdfv.sound.MachineSound.Event.HOIST, 0); }
            }
            default -> { }
        }
    }

    private static final java.util.Map<String, dev.mcmod.hdfv.assembly.BuildSequence> SEQUENCES = java.util.Collections.synchronizedMap(
            new java.util.LinkedHashMap<>(8, 0.75f, true) {
                @Override protected boolean removeEldestEntry(java.util.Map.Entry<String, dev.mcmod.hdfv.assembly.BuildSequence> e) { return size() > 16; }
            });

    /** A vehicle's build order (cached by its blueprint). */
    public static dev.mcmod.hdfv.assembly.BuildSequence sequence(VehicleBlueprint b) {
        return SEQUENCES.computeIfAbsent(b.toJsonText(), k -> dev.mcmod.hdfv.assembly.BuildSequence.of(b));
    }

    /** Takes apart the vehicle being built here (or one standing in the room): its parts back to the room's stores. Returns why not. */
    public Component dismantle() {
        if (scan == null) { scan(); }
        HdfvVehicle v = vehicle();
        if (v == null && scan != null && level != null) {
            var box = new net.minecraft.world.phys.AABB(scan.minX(), scan.minY(), scan.minZ(), scan.maxX() + 1, scan.maxY() + 1, scan.maxZ() + 1);
            v = level.getEntitiesOfClass(HdfvVehicle.class, box).stream().findFirst().orElse(null);
        }
        if (v == null || v.blueprint() == null) { return Component.translatable("hdfv.build.nothing"); }
        List<net.neoforged.neoforge.items.IItemHandler> stores = stores();
        for (ItemStack s : Staging.salvage(v.blueprint())) {
            Staging.give(s, stores, left -> net.minecraft.world.Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1, worldPosition.getZ() + 0.5, left));
        }
        v.ejectPassengers();
        v.discard();
        building = null;
        changed();
        return null;
    }

    // ---------------------------------------------------------------- layout edits

    /** What a player does to the layout (sent from the screen; the server checks it here). */
    public enum Op { ADD, REMOVE, ATTACH, NUDGE, CLEAR, SAVE, LOAD }

    public record Edit(Op op, int slot, String id, String part, String point, Vec3d by, double yawDeg, double pitchDeg, double rollDeg) {
        public static Edit of(Op op) { return new Edit(op, 0, "", "", "", Vec3d.ZERO, 0, 0, 0); }
    }

    /** Applies an edit; returns why it was refused (null when done). */
    public Component apply(Edit e) {
        VehicleBlueprint l = layout();
        VehicleBlueprint next;
        switch (e.op()) {
            case ADD -> {
                PartDesign d = e.slot() >= 0 && e.slot() < PART_DRAWINGS ? Workpieces.drawing(drawings.getItem(e.slot())) : null;
                if (d == null) { return Component.translatable("hdfv.layout.no_drawing"); }
                next = Placing.add(l, d);
            }
            case REMOVE -> {
                if (l.part(e.id()) == null) { return Component.translatable("hdfv.layout.no_part", e.id()); }
                try { next = Placing.remove(l, e.id()); } catch (IllegalArgumentException held) { return Component.translatable("hdfv.layout.holds", e.id(), held.getMessage()); }
            }
            case ATTACH -> {
                if (l.part(e.id()) == null) { return Component.translatable("hdfv.layout.no_part", e.id()); }
                if (!e.part().isEmpty() && (e.part().equals(e.id()) || l.part(e.part()) == null || hangsOn(l, e.part(), e.id()))) {
                    return Component.translatable("hdfv.layout.bad_mount", e.part());
                }
                next = Placing.attach(l, e.id(), e.part().isEmpty() ? null : e.part(), e.part().isEmpty() ? null : e.point());
            }
            case NUDGE -> {
                if (l.part(e.id()) == null) { return Component.translatable("hdfv.layout.no_part", e.id()); }
                next = Placing.nudge(l, e.id(), e.by(), e.yawDeg(), e.pitchDeg(), e.rollDeg());
            }
            case CLEAR -> next = new VehicleBlueprint(l.name(), List.of(), List.of());
            case SAVE -> {
                ItemStack s = drawings.getItem(VEHICLE_DRAWING);
                if (!s.is(HdfvAssembly.VEHICLE_BLUEPRINT.get())) { return Component.translatable("hdfv.layout.no_vehicle_blueprint"); }
                if (l.parts().isEmpty()) { return Component.translatable("hdfv.layout.empty"); }
                s.set(HdfvAssembly.VEHICLE_LAYOUT.get(), layout);
                drawings.setChanged();
                return null;
            }
            case LOAD -> {
                String json = drawings.getItem(VEHICLE_DRAWING).get(HdfvAssembly.VEHICLE_LAYOUT.get());
                if (json == null || json.isEmpty()) { return Component.translatable("hdfv.layout.no_vehicle_blueprint"); }
                try { next = AutoWiring.stripped(VehicleBlueprint.fromJsonText(json)); } catch (RuntimeException bad) { return Component.literal(String.valueOf(bad.getMessage())); }
            }
            default -> { return null; }
        }
        layout = next.parts().isEmpty() ? "" : next.toJsonText();
        if (level != null) { scan(); } else { changed(); }
        return null;
    }

    /** Whether part a hangs (directly or through others) on part b. */
    private static boolean hangsOn(VehicleBlueprint l, String a, String b) {
        for (String at = a; at != null; ) {
            if (at.equals(b)) { return true; }
            VehicleBlueprint.Entry e = l.part(at);
            at = e == null ? null : e.mount().attachPart();
        }
        return false;
    }

    private void changed() {
        setChanged();
        if (level != null) { level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS); }
    }

    // ---------------------------------------------------------------- saving and syncing

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Report", report);
        tag.putByteArray("Layout", Packed.pack(layout));
        ContainerHelper.saveAllItems(tag, drawings.getItems(), registries);
        if (building != null) {
            tag.putUUID("Building", building);
            tag.putLongArray("Work", new long[]{workDone, workTotal, cureDone, cureTotal});
            tag.putBoolean("Launching", launching);
        }
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        report = tag.getCompound("Report");
        layout = unpack(tag);
        NonNullList<ItemStack> list = NonNullList.withSize(DRAWINGS, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, list, registries);
        for (int i = 0; i < DRAWINGS; i++) { drawings.setItem(i, list.get(i)); }
        building = tag.hasUUID("Building") ? tag.getUUID("Building") : null;
        long[] w = tag.getLongArray("Work");
        if (w.length == 4) { workDone = w[0]; workTotal = w[1]; cureDone = w[2]; cureTotal = w[3]; }
        launching = tag.getBoolean("Launching");
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        if (scan != null && scan.closed()) {
            tag.putIntArray("Box", new int[]{scan.minX(), scan.minY(), scan.minZ(), scan.maxX(), scan.maxY(), scan.maxZ()});
            tag.putBoolean("Crane", scan.craneCells() > 0);
        }
        tag.putInt("State", building == null ? 0 : finished() ? 2 : 1);
        tag.putFloat("Built", built());
        tag.put("Report", report);
        tag.putByteArray("Layout", Packed.pack(layout));
        ListTag list = new ListTag();
        for (String i : issues()) { list.add(StringTag.valueOf(i)); }
        tag.put("Issues", list);
        return tag;
    }

    @Override public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        shownBox = tag.getIntArray("Box");
        shownCrane = tag.getBoolean("Crane");
        shownState = tag.getInt("State");
        shownBuilt = tag.getFloat("Built");
        report = tag.getCompound("Report");
        layout = unpack(tag);
        issues = tag.getList("Issues", Tag.TAG_STRING).stream().map(Tag::getAsString).toList();
        planFor = layout;   // the client shows the server's issues; it plans on its own only for the preview
    }

    private static String unpack(CompoundTag tag) {
        try { return Packed.unpack(tag.getByteArray("Layout")); } catch (IllegalArgumentException e) { return ""; }
    }

    @Override public void onDataPacket(net.minecraft.network.Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
        handleUpdateTag(pkt.getTag(), registries);
    }

    @Override public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @Override public Component getDisplayName() { return Component.translatable("block.hdfv." + room.name().toLowerCase(Locale.ROOT) + "_controller"); }

    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) { return new RoomMenu(id, inventory, this); }

    /** Keeps a drawing a drawing: part blueprints in the first eight slots, a vehicle blueprint in the last. */
    static boolean fits(int slot, ItemStack s) {
        return slot < PART_DRAWINGS ? s.is(HdfvRegistry.BLUEPRINT.get()) : s.is(HdfvAssembly.VEHICLE_BLUEPRINT.get());
    }
}
