package dev.mcmod.hdfv.neoforge.storage;

import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.Vehicle;
import dev.mcmod.aerocore.neoforge.vehicle.PartVehicle;
import dev.mcmod.hdfv.neoforge.assembly.HdfvVehicle;
import dev.mcmod.hdfv.part.body.BodyDesign;
import dev.mcmod.hdfv.storage.CargoRules;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 0.25: what a vehicle carries in its holds (the user: "storage on vehicles"; cargo has weight). A NeoForge data
 * attachment on the vehicle entity (HdfvCargo.CARGO), so it works on any AeroCore PartVehicle, spawned by command or
 * rolled out of a room: the stacks of each hold by the hold's id (AeroCore's "part.hold": "body.m5_cargo",
 * "storage_box_1.hold"), saved with the entity. Server side only; clients see each hold's mass as AeroCore syncs it
 * (whole kg) and ask for the slots used (CargoNetwork).
 *
 * <ul>
 * <li>Laid out for the vehicle's holds (CargoRules.slots) whenever its compiled vehicle changes (a blueprint set,
 *     restyled, refitted, parts cast off, the entity loaded; HdfvCargo looks every tick): a hold keeps its stacks by
 *     id; what no longer fits (a smaller hold, one gone) is overflow, dropped where the vehicle is unless the caller
 *     takes it (the refit shop's stores: relayout). A vehicle whose blueprint does not compile keeps everything.</li>
 * <li>Every change to a hold (a menu, a hopper through a cargo loader, a pipe) weighs it again (ItemMass) and gives
 *     AeroCore its mass (PartVehicle.setHold): the vehicle carries it at the hold's centre. Also after every layout.</li>
 * <li>Opening (open): the server checks the vehicle is there and simulated (not an assembly room's unfinished one), the
 *     hold exists, and the player rides the vehicle or is within REACH_M of the hold's centre; the menu (StorageMenu,
 *     any item, the hold's rating as its limit) stays open while that holds and the layout stands.</li>
 * </ul>
 */
public final class VehicleCargo {
    /** How near a player on foot must be to a hold's centre to use it, m. */
    public static final double REACH_M = 6;

    /** The stacks by hold id (laid out: as many as the hold's slots). */
    final Map<String, NonNullList<ItemStack>> holds = new LinkedHashMap<>();
    /** The compiled vehicle the stacks were last laid out for (not saved: a loaded vehicle is laid out on its first tick). */
    private Vehicle laidOutFor;
    /** The containers of the layout, by hold index. */
    private List<Hold> containers = List.of();

    public VehicleCargo() {}

    /** The cargo of a vehicle (made empty on first use). */
    public static VehicleCargo of(PartVehicle v) { return v.getData(HdfvCargo.CARGO.get()); }

    /** Whether nothing is in any hold. */
    public boolean isEmpty() {
        for (NonNullList<ItemStack> l : holds.values()) { for (ItemStack s : l) { if (!s.isEmpty()) { return false; } } }
        return true;
    }

    // ---------------------------------------------------------------- one hold

    /** A hold as a Container (what menus, item handlers and rooms use); it weighs itself into the vehicle on every change. */
    public static final class Hold implements Container {
        public final PartVehicle vehicle;
        public final String id;
        public final int index;
        final NonNullList<ItemStack> items;
        private boolean live = true;

        Hold(PartVehicle vehicle, String id, int index, NonNullList<ItemStack> items) { this.vehicle = vehicle; this.id = id; this.index = index; this.items = items; }

        /** Whether it is still the vehicle's (a new layout or the vehicle gone ends it). */
        public boolean live() { return live && !vehicle.isRemoved(); }

        @Override public int getContainerSize() { return items.size(); }
        @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
        @Override public ItemStack getItem(int slot) { return slot >= 0 && slot < items.size() ? items.get(slot) : ItemStack.EMPTY; }

        @Override public ItemStack removeItem(int slot, int amount) {
            if (!live()) { return ItemStack.EMPTY; }
            ItemStack s = ContainerHelper.removeItem(items, slot, amount);
            if (!s.isEmpty()) { setChanged(); }
            return s;
        }

        @Override public ItemStack removeItemNoUpdate(int slot) {
            if (!live()) { return ItemStack.EMPTY; }
            ItemStack s = ContainerHelper.takeItem(items, slot);
            if (!s.isEmpty()) { setChanged(); }
            return s;
        }

        @Override public void setItem(int slot, ItemStack stack) {
            if (!live()) { drop(vehicle, stack, null); return; }   // a stale hold (a new layout): nothing is lost
            if (slot < 0 || slot >= items.size()) { return; }
            items.set(slot, stack);
            stack.limitSize(getMaxStackSize(stack));
            setChanged();
        }

        /** Weighs the hold into the vehicle. */
        @Override public void setChanged() { if (live()) { vehicle.setHold(index, ItemMass.kg(items)); } }

        /** Whether a player may keep using it: still the vehicle's, and the player aboard or near it. */
        @Override public boolean stillValid(Player player) { return live() && reaches(player, vehicle, index); }

        /** The tutorial: how many items were in it when each player opened its menu (by the player's id). */
        private final Map<java.util.UUID, Integer> openedWith = new java.util.HashMap<>();

        @Override public void startOpen(Player player) { openedWith.put(player.getUUID(), count()); }

        /** A menu closed: a player who leaves more in it than there was has loaded a vehicle (the tutorial's "loaded"). */
        @Override public void stopOpen(Player player) {
            Integer was = openedWith.remove(player.getUUID());
            if (was != null && count() > was && player instanceof ServerPlayer sp) { dev.mcmod.hdfv.neoforge.tutorial.Milestones.reach(sp, "loaded"); }
        }

        /** Items in it, counted. */
        int count() {
            int n = 0;
            for (ItemStack s : items) { n += s.getCount(); }
            return n;
        }

        @Override public void clearContent() {
            if (!live()) { return; }
            items.clear();
            setChanged();
        }

        /** The mass of what is in it, kg. */
        public double massKg() { return ItemMass.kg(items); }
        /** Stacks holding something. */
        public int used() { return (int) items.stream().filter(s -> !s.isEmpty()).count(); }
    }

    // ---------------------------------------------------------------- layout

    /** Whether the vehicle is one whose holds can be used: compiled, on the server, not an assembly room's unfinished one. */
    public static boolean usable(PartVehicle v) {
        return v != null && !v.level().isClientSide && !v.isRemoved() && v.vehicle() != null && v.vehicleState() != null && !(v instanceof HdfvVehicle h && !h.assembled());
    }

    /** Lays the cargo out for the vehicle's holds when it changed (overflow dropped where the vehicle is). */
    public static void check(PartVehicle v) {
        if (v.level().isClientSide || v.vehicle() == null || v.vehicleState() == null) { return; }
        if (!v.hasData(HdfvCargo.CARGO.get())) {
            if (v.holdCount() == 0) { return; }
        }
        VehicleCargo c = of(v);
        if (c.laidOutFor != v.vehicle()) { c.layout(v, s -> drop(v, s, null)); }
    }

    /** Lays the cargo out for the vehicle's holds now, the overflow to a taker (the refit shop's stores). */
    public static void relayout(PartVehicle v, Consumer<ItemStack> overflow) {
        if (v.level().isClientSide || v.vehicle() == null || v.vehicleState() == null) { return; }
        of(v).layout(v, overflow);
    }

    private void layout(PartVehicle v, Consumer<ItemStack> overflow) {
        Vehicle vehicle = v.vehicle();
        for (Hold h : containers) { h.live = false; }
        Map<String, NonNullList<ItemStack>> old = new LinkedHashMap<>(holds);
        holds.clear();
        List<Hold> made = new ArrayList<>();
        for (int k = 0; k < vehicle.holds().size(); k++) {
            Vehicle.Hold h = vehicle.holds().get(k);
            if (dev.mcmod.hdfv.neoforge.worksite.BulkCargo.bulk(h)) { continue; }   // 0.26: bulk holds carry loose material (BulkCargo), not items
            int slots = CargoRules.slots(holder(vehicle, k), h.hold());
            NonNullList<ItemStack> was = old.remove(h.id()), now = NonNullList.withSize(slots, ItemStack.EMPTY);
            if (was != null) {
                List<ItemStack> moved = new ArrayList<>();
                for (int i = 0; i < was.size(); i++) {
                    ItemStack s = was.get(i);
                    if (s.isEmpty()) { continue; }
                    if (i < slots) { now.set(i, s); } else { moved.add(s); }
                }
                for (ItemStack s : moved) {
                    int free = now.indexOf(ItemStack.EMPTY);
                    if (free >= 0) { now.set(free, s); } else { overflow.accept(s); }
                }
            }
            holds.put(h.id(), now);
            made.add(new Hold(v, h.id(), k, now));
        }
        for (NonNullList<ItemStack> gone : old.values()) { for (ItemStack s : gone) { if (!s.isEmpty()) { overflow.accept(s); } } }
        containers = List.copyOf(made);
        laidOutFor = vehicle;
        for (Hold h : containers) { v.setHold(h.index, h.massKg()); }
    }

    /** The design of the part a hold is in. */
    public static PartDesign holder(Vehicle vehicle, int hold) { return vehicle.installed().get(vehicle.holds().get(hold).part()).part().design(); }

    /** A hold of a usable vehicle as a container (laid out first when it changed), or null. */
    public static Hold hold(PartVehicle v, int index) {
        if (!usable(v) || index < 0 || index >= v.holdCount()) { return null; }
        check(v);
        VehicleCargo c = of(v);
        return index < c.containers.size() ? c.containers.get(index) : null;
    }

    /** What a hold is rated to carry, kg (0: no rating). */
    public static double limitKg(Vehicle vehicle, int hold) { return CargoRules.limitKg(holder(vehicle, hold), vehicle.holds().get(hold).hold()); }

    /** A hold's slots. */
    public static int slots(Vehicle vehicle, int hold) { return CargoRules.slots(holder(vehicle, hold), vehicle.holds().get(hold).hold()); }

    /** A hold's name: its body module's (a car's "trunk"), or the storage box's design name. */
    public static Component name(Vehicle vehicle, int hold) {
        PartDesign d = holder(vehicle, hold);
        if (d instanceof BodyDesign b) {
            int i = CargoRules.moduleOf(vehicle.holds().get(hold).hold().name());
            if (i >= 0 && i < b.modules().size()) { return Component.literal(b.modules().get(i).name()); }
        }
        return Component.literal(d.name());
    }

    /** A hold's centre in the world (either side; as drawn on a client). */
    public static Vec3d point(PartVehicle v, int hold) {
        Vehicle vehicle = v.vehicle();
        Vehicle.Hold h = vehicle.holds().get(hold);
        return v.partPoint(vehicle.installed().get(h.part()), h.hold().centre(), 0);
    }

    /** Whether a player can use a hold: riding the vehicle, or within REACH_M of the hold's centre. */
    public static boolean reaches(Player player, PartVehicle v, int hold) {
        if (v.isRemoved() || v.vehicle() == null || hold < 0 || hold >= v.holdCount()) { return false; }
        if (player.getVehicle() == v) { return true; }
        Vec3d at = point(v, hold);
        return player.getEyePosition().distanceToSqr(at.x(), at.y(), at.z()) <= REACH_M * REACH_M;
    }

    /** The hold nearest a player (the one a rider opens with the inventory key), or -1. */
    public static int nearest(Player player, PartVehicle v) {
        int best = -1;
        double d = Double.POSITIVE_INFINITY;
        for (int k = 0; k < v.holdCount(); k++) {
            Vec3d at = point(v, k);
            double e = player.getEyePosition().distanceToSqr(at.x(), at.y(), at.z());
            if (e < d) { d = e; best = k; }
        }
        return best;
    }

    /** Opens a hold's menu for a player; returns why not (null when opened). */
    public static Component open(ServerPlayer player, PartVehicle v, int index) {
        if (!usable(v)) { return Component.translatable("hdfv.cargo.unavailable"); }
        if (index < 0 || index >= v.holdCount()) { return Component.translatable("hdfv.cargo.no_hold"); }
        if (!reaches(player, v, index)) { return Component.translatable("hdfv.cargo.too_far"); }
        Hold h = hold(v, index);
        if (h == null) { return Component.translatable("hdfv.cargo.no_hold"); }
        Vehicle vehicle = v.vehicle();
        double limit = limitKg(vehicle, index);
        Component title = Component.translatable("hdfv.cargo.title", name(vehicle, index));
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new StorageMenu(id, inventory, h, StorageMenu.Filter.ANY, h::stillValid, () -> limit), title),
                buf -> StorageMenu.write(buf, h.getContainerSize(), StorageMenu.Filter.ANY));
        return null;
    }

    // ---------------------------------------------------------------- unloading

    /** Takes everything out of a vehicle's holds (to a taker: a room's stores), the holds weighing nothing after. */
    public static void unload(PartVehicle v, Consumer<ItemStack> taker) {
        if (v.level().isClientSide || !v.hasData(HdfvCargo.CARGO.get())) { return; }
        VehicleCargo c = of(v);
        for (NonNullList<ItemStack> l : c.holds.values()) {
            for (int i = 0; i < l.size(); i++) {
                if (!l.get(i).isEmpty()) { taker.accept(l.get(i)); l.set(i, ItemStack.EMPTY); }
            }
        }
        if (v.vehicle() != null && v.vehicleState() != null) {
            for (int k = 0; k < v.holdCount(); k++) { if (!dev.mcmod.hdfv.neoforge.worksite.BulkCargo.bulk(v.vehicle().holds().get(k))) { v.setHold(k, 0); } }
        }
    }

    /** Takes the stacks of the holds of a part (by its id) out, to a taker (a part cast off: where it fell). */
    public static void unloadPart(PartVehicle v, String partId, Consumer<ItemStack> taker) {
        if (v.level().isClientSide || !v.hasData(HdfvCargo.CARGO.get())) { return; }
        VehicleCargo c = of(v);
        for (Map.Entry<String, NonNullList<ItemStack>> e : c.holds.entrySet()) {
            if (!e.getKey().startsWith(partId + ".")) { continue; }
            for (int i = 0; i < e.getValue().size(); i++) {
                ItemStack s = e.getValue().get(i);
                if (!s.isEmpty()) { taker.accept(s); e.getValue().set(i, ItemStack.EMPTY); }
            }
        }
    }

    /** Drops a stack where a vehicle is (at a point, or the vehicle's position). */
    static void drop(PartVehicle v, ItemStack s, Vec3d at) {
        if (s.isEmpty()) { return; }
        double x = at == null ? v.getX() : at.x(), y = at == null ? v.getY() + 0.5 : at.y(), z = at == null ? v.getZ() : at.z();
        Containers.dropItemStack(v.level(), x, y, z, s);
    }

    /** Drops all of a vehicle's cargo at its holds (a vehicle killed or removed). */
    static void dropAll(PartVehicle v) {
        if (v.level().isClientSide || !v.hasData(HdfvCargo.CARGO.get())) { return; }
        VehicleCargo c = of(v);
        if (c.isEmpty()) { return; }
        Vehicle vehicle = v.vehicle();
        Map<String, Vec3d> where = new LinkedHashMap<>();
        if (vehicle != null) { for (int k = 0; k < vehicle.holds().size(); k++) { where.put(vehicle.holds().get(k).id(), point(v, k)); } }
        for (Map.Entry<String, NonNullList<ItemStack>> e : c.holds.entrySet()) {
            Vec3d at = where.get(e.getKey());
            for (int i = 0; i < e.getValue().size(); i++) {
                drop(v, e.getValue().get(i), at);
                e.getValue().set(i, ItemStack.EMPTY);
            }
        }
    }

    // ---------------------------------------------------------------- saving

    /** The attachment's NBT: each hold's id, its size and its stacks (slots kept); nothing when every hold is empty. */
    static final class Serializer implements IAttachmentSerializer<CompoundTag, VehicleCargo> {
        @Override public VehicleCargo read(IAttachmentHolder holder, CompoundTag tag, HolderLookup.Provider provider) {
            VehicleCargo c = new VehicleCargo();
            for (Tag t : tag.getList("Holds", Tag.TAG_COMPOUND)) {
                CompoundTag h = (CompoundTag) t;
                NonNullList<ItemStack> items = NonNullList.withSize(Math.clamp(h.getInt("Size"), 0, 4096), ItemStack.EMPTY);
                for (Tag st : h.getList("Items", Tag.TAG_COMPOUND)) {
                    CompoundTag one = (CompoundTag) st;
                    int slot = one.getInt("Slot");
                    if (slot >= 0 && slot < items.size()) { ItemStack.parse(provider, one).ifPresent(stack -> items.set(slot, stack)); }
                }
                c.holds.put(h.getString("Id"), items);
            }
            return c;
        }

        @Override public CompoundTag write(VehicleCargo c, HolderLookup.Provider provider) {
            if (c.isEmpty()) { return null; }
            ListTag list = new ListTag();
            c.holds.forEach((id, items) -> {
                if (items.stream().allMatch(ItemStack::isEmpty)) { return; }
                CompoundTag h = new CompoundTag();
                h.putString("Id", id);
                h.putInt("Size", items.size());
                // slots as ints (ContainerHelper's are bytes: a container's 324 would wrap)
                ListTag stacks = new ListTag();
                for (int i = 0; i < items.size(); i++) {
                    if (items.get(i).isEmpty()) { continue; }
                    CompoundTag one = new CompoundTag();
                    one.putInt("Slot", i);
                    stacks.add(items.get(i).save(provider, one));
                }
                h.put("Items", stacks);
                list.add(h);
            });
            CompoundTag tag = new CompoundTag();
            tag.put("Holds", list);
            return tag;
        }
    }
}
