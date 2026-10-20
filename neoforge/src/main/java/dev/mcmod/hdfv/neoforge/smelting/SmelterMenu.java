package dev.mcmod.hdfv.neoforge.smelting;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
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

/** The arc furnace's and the electrolysis cell's menu (0.23): inputs in a 2 x 2 square, the result and two by-products to the right, the inventory below. */
public final class SmelterMenu extends AbstractContainerMenu {
    public static final int INPUT_X = 26, INPUT_Y = 22, OUT_X = 116, OUT_Y = 17;
    final Container container;
    final ContainerData data;
    public final BlockPos pos;
    private final ContainerLevelAccess access;

    /** The client's copy. */
    public SmelterMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, new SimpleContainer(SmelterBlockEntity.SLOTS), new SimpleContainerData(4), buf.readBlockPos());
    }

    SmelterMenu(int id, Inventory inventory, Container container, ContainerData data, BlockPos pos) {
        super(HdfvSmelting.SMELTER_MENU.get(), id);
        this.container = container;
        this.data = data;
        this.pos = pos;
        this.access = inventory.player.level().isClientSide ? ContainerLevelAccess.NULL : ContainerLevelAccess.create(inventory.player.level(), pos);
        for (int i = 0; i < SmelterBlockEntity.INPUTS; i++) { addSlot(new Slot(container, i, INPUT_X + 18 * (i % 2), INPUT_Y + 18 * (i / 2))); }
        for (int k = 0; k < 3; k++) {
            addSlot(new Slot(container, SmelterBlockEntity.RESULT + k, OUT_X + (k == 0 ? 0 : 18 * (k - 1) + 0), OUT_Y + (k == 0 ? 0 : 26)) {
                @Override public boolean mayPlace(ItemStack s) { return false; }
            });
        }
        for (int r = 0; r < 3; r++) { for (int c = 0; c < 9; c++) { addSlot(new Slot(inventory, c + r * 9 + 9, 8 + c * 18, 84 + r * 18)); } }
        for (int c = 0; c < 9; c++) { addSlot(new Slot(inventory, c, 8 + c * 18, 142)); }
        addDataSlots(data);
    }

    /** The batch's progress, 0..1000; the state; last tick's draw (0..1000 of the rated power); the batch's energy, 0.1 kWh. */
    public int progress() { return data.get(0); }
    public SmelterBlockEntity.State state() { return SmelterBlockEntity.State.values()[Math.clamp(data.get(1), 0, SmelterBlockEntity.State.values().length - 1)]; }
    public int drawn() { return data.get(2); }
    public double batchKwh() { return data.get(3) / 10.0; }

    @Override public boolean stillValid(Player player) {
        return access.evaluate((level, p) -> level.getBlockState(p).getBlock() instanceof SmelterBlock && player.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5) <= 64, true);
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) { return ItemStack.EMPTY; }
        ItemStack s = slot.getItem(), copy = s.copy();
        int machine = SmelterBlockEntity.SLOTS;
        if (index < machine) {
            if (!moveItemStackTo(s, machine, slots.size(), true)) { return ItemStack.EMPTY; }
        } else if (!moveItemStackTo(s, 0, SmelterBlockEntity.INPUTS, false)) {
            return ItemStack.EMPTY;
        }
        if (s.isEmpty()) { slot.setByPlayer(ItemStack.EMPTY); } else { slot.setChanged(); }
        return copy;
    }
}
