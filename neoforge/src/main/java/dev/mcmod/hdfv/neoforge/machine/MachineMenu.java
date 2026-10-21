package dev.mcmod.hdfv.neoforge.machine;

import dev.mcmod.hdfv.manufacture.Station;
import dev.mcmod.hdfv.neoforge.HdfvRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A machine's menu (0.7): the drawing slot, the inputs (three; an assembly bench's eighteen), two outputs and the
 * player's inventory. Opening it on the server makes the player one of the machine's operators while it stays open
 * and they stay within reach. Buttons 0 and 1 choose which piece of the drawing a forming station starts.
 * 0.17: a row of consumable slots (tools, wheels, gas...; a CNC's four) over the player's inventory, which moves down.
 */
public final class MachineMenu extends AbstractContainerMenu {
    public static final int WIDTH = 176, DRAWING_X = 8, DRAWING_Y = 18, INPUT_X = 44, INPUT_Y = 18, OUTPUT_X = 134, OUTPUT_Y = 18;
    public final Station station;
    public final BlockPos pos;
    final MachineBlockEntity machine;
    final Container container;
    final ContainerData data;
    private final ContainerLevelAccess access;
    public final int inputs, consumables, consumableY, playerTop, height;

    /** The client's copy. */
    public MachineMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, null, buf.readBlockPos(), Station.values()[buf.readVarInt()]);
    }

    MachineMenu(int id, Inventory inventory, MachineBlockEntity be) { this(id, inventory, be, be.getBlockPos(), be.station); }

    private MachineMenu(int id, Inventory inventory, MachineBlockEntity be, BlockPos pos, Station station) {
        super(HdfvMachines.MACHINE_MENU.get(), id);
        this.station = station;
        this.pos = pos;
        this.machine = be;
        this.inputs = MachineBlockEntity.inputsOf(station);
        this.consumables = MachineBlockEntity.consumablesOf(station);
        this.container = be != null ? be.items : new SimpleContainer(MachineBlockEntity.FIRST_INPUT + inputs + 2 + consumables);
        this.data = be != null ? be.data : new SimpleContainerData(MachineBlockEntity.DATA);
        this.access = be != null ? ContainerLevelAccess.create(be.getLevel(), pos) : ContainerLevelAccess.NULL;
        addSlot(new Slot(container, MachineBlockEntity.DRAWING, DRAWING_X, DRAWING_Y) {
            @Override public boolean mayPlace(ItemStack s) { return s.is(HdfvRegistry.BLUEPRINT.get()); }
            @Override public int getMaxStackSize() { return 1; }
        });
        int perRow = station == Station.ASSEMBLY_BENCH ? 9 : 3, inX = station == Station.ASSEMBLY_BENCH ? 8 : INPUT_X, inY = station == Station.ASSEMBLY_BENCH ? 40 : INPUT_Y;
        for (int i = 0; i < inputs; i++) { addSlot(new Slot(container, MachineBlockEntity.FIRST_INPUT + i, inX + 18 * (i % perRow), inY + 18 * (i / perRow))); }
        int out = MachineBlockEntity.FIRST_INPUT + inputs;
        for (int i = 0; i < 2; i++) {
            addSlot(new Slot(container, out + i, OUTPUT_X + 18 * i, OUTPUT_Y) {
                @Override public boolean mayPlace(ItemStack s) { return false; }
                @Override public void onTake(net.minecraft.world.entity.player.Player player, ItemStack taken) {
                    super.onTake(player, taken);
                    // 0.24: the tutorial's "machined" milestone (a finished part, a standard component, or a workpiece between steps)
                    String kind = taken.is(HdfvMachines.PART.get()) ? "part" : taken.is(HdfvMachines.WORKPIECE.get()) ? "workpiece"
                            : dev.mcmod.hdfv.neoforge.material.HdfvComponents.COMPONENTS.values().stream().anyMatch(c -> taken.is(c.get())) ? "component" : "other";
                    dev.mcmod.hdfv.neoforge.tutorial.Milestones.reach(player, "machined", kind);
                }
            });
        }
        // the CNC and (0.9) the manual machine tools have two lines more (axes; size, drive, accessories)
        int top = station == Station.ASSEMBLY_BENCH ? 108 : station == Station.CNC || dev.mcmod.hdfv.manufacture.ToolConfig.STATIONS.contains(station) ? 110 : 84;
        consumableY = top;
        for (int i = 0; i < consumables; i++) {
            addSlot(new Slot(container, out + 2 + i, INPUT_X + 18 * i, consumableY) {
                @Override public boolean mayPlace(ItemStack s) { return dev.mcmod.hdfv.neoforge.machining.HdfvMachining.supplyOf(s) != null; }
            });
        }
        playerTop = consumables > 0 ? top + 24 : top;
        height = playerTop + 82;
        for (int r = 0; r < 3; r++) { for (int c = 0; c < 9; c++) { addSlot(new Slot(inventory, c + r * 9 + 9, 8 + c * 18, playerTop + r * 18)); } }
        for (int c = 0; c < 9; c++) { addSlot(new Slot(inventory, c, 8 + c * 18, playerTop + 58)); }
        addDataSlots(data);
    }

    public int progress() { return (data.get(0) & 0xffff) | (data.get(1) & 0xffff) << 16; }
    public int total() { return (data.get(2) & 0xffff) | (data.get(3) & 0xffff) << 16; }
    public MachineBlockEntity.State state() { return MachineBlockEntity.State.values()[Math.clamp(data.get(4), 0, MachineBlockEntity.State.values().length - 1)]; }
    public int operators() { return data.get(5); }
    public int selected() { return data.get(6); }
    /** The accessory a manual machine tool lacks for its next step (0.9; null: none). */
    public dev.mcmod.hdfv.manufacture.ToolConfig.Accessory missing() {
        int i = data.get(7) - 1;
        var all = dev.mcmod.hdfv.manufacture.ToolConfig.Accessory.values();
        return i >= 0 && i < all.length ? all[i] : null;
    }
    /** The consumable a job lacks (0.17; null: none). */
    public dev.mcmod.hdfv.manufacture.Consumable missingConsumable() {
        int i = data.get(8) - 1;
        var all = dev.mcmod.hdfv.manufacture.Consumable.values();
        return i >= 0 && i < all.length ? all[i] : null;
    }
    /** A family's head (0.17; null: none). */
    public dev.mcmod.hdfv.manufacture.Head head() {
        int i = data.get(9) - 1;
        var all = dev.mcmod.hdfv.manufacture.Head.values();
        return i >= 0 && i < all.length ? all[i] : null;
    }
    public ItemStack drawing() { return getSlot(0).getItem(); }

    @Override public boolean clickMenuButton(Player player, int id) {
        if (machine == null) { return false; }
        machine.select(id == 0 ? -1 : 1);
        return true;
    }

    @Override public void removed(Player player) {
        super.removed(player);
        if (machine != null && player instanceof ServerPlayer sp) { machine.operators.remove(sp); }
    }

    @Override public boolean stillValid(Player player) {
        return access.evaluate((level, p) -> level.getBlockEntity(p) instanceof MachineBlockEntity m
                && (player.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5) <= 64 || m.near(player)), true);
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) { return ItemStack.EMPTY; }
        ItemStack s = slot.getItem(), copy = s.copy();
        int machineSlots = MachineBlockEntity.FIRST_INPUT + inputs + 2 + consumables;
        if (index < machineSlots) {
            if (!moveItemStackTo(s, machineSlots, slots.size(), true)) { return ItemStack.EMPTY; }
        } else if (consumables > 0 && dev.mcmod.hdfv.neoforge.machining.HdfvMachining.supplyOf(s) != null && station != Station.GRINDER) {
            if (!moveItemStackTo(s, MachineBlockEntity.FIRST_INPUT + inputs + 2, machineSlots, false)) { return ItemStack.EMPTY; }
        } else if (s.is(HdfvRegistry.BLUEPRINT.get())) {
            if (!moveItemStackTo(s, 0, 1, false)) { return ItemStack.EMPTY; }
        } else if (!moveItemStackTo(s, 1, 1 + inputs, false)) {
            return ItemStack.EMPTY;
        }
        if (s.isEmpty()) { slot.setByPlayer(ItemStack.EMPTY); } else { slot.setChanged(); }
        return copy;
    }
}
