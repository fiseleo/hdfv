package dev.mcmod.hdfv.neoforge.material;

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
import net.minecraft.world.item.crafting.RecipeType;

/** The alloy furnace's menu: inputs in a 2 x 2 square, fuel under them, result and by-product to the right, the inventory below. */
public final class AlloyFurnaceMenu extends AbstractContainerMenu {
    public static final int INPUT_X = 26, INPUT_Y = 17, FUEL_X = 35, FUEL_Y = 59, RESULT_X = 116, RESULT_Y = 26, BYPRODUCT_X = 116, BYPRODUCT_Y = 52;
    final Container container;
    final ContainerData data;
    private final ContainerLevelAccess access;

    /** The client's copy. */
    public AlloyFurnaceMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, new SimpleContainer(AlloyFurnaceBlockEntity.SLOTS), new SimpleContainerData(4), buf.readBlockPos());
    }

    AlloyFurnaceMenu(int id, Inventory inventory, Container container, ContainerData data, BlockPos pos) {
        super(HdfvMaterials.ALLOY_FURNACE_MENU.get(), id);
        this.container = container;
        this.data = data;
        this.access = inventory.player.level().isClientSide ? ContainerLevelAccess.NULL : ContainerLevelAccess.create(inventory.player.level(), pos);
        for (int i = 0; i < AlloyFurnaceBlockEntity.INPUTS; i++) { addSlot(new Slot(container, i, INPUT_X + 18 * (i % 2), INPUT_Y + 18 * (i / 2))); }
        addSlot(new Slot(container, AlloyFurnaceBlockEntity.FUEL, FUEL_X, FUEL_Y) {
            @Override public boolean mayPlace(ItemStack s) { return s.getBurnTime(RecipeType.SMELTING) > 0; }
        });
        addSlot(new Slot(container, AlloyFurnaceBlockEntity.RESULT, RESULT_X, RESULT_Y) { @Override public boolean mayPlace(ItemStack s) { return false; } });
        addSlot(new Slot(container, AlloyFurnaceBlockEntity.BYPRODUCT, BYPRODUCT_X, BYPRODUCT_Y) { @Override public boolean mayPlace(ItemStack s) { return false; } });
        playerSlots(this, inventory, 84);
        addDataSlots(data);
    }

    /** The player's inventory and hotbar from a top edge. */
    static void playerSlots(AbstractContainerMenu menu, Inventory inventory, int top) {
        for (int r = 0; r < 3; r++) { for (int c = 0; c < 9; c++) { ((AlloyFurnaceMenu) menu).addSlot(new Slot(inventory, c + r * 9 + 9, 8 + c * 18, top + r * 18)); } }
        for (int c = 0; c < 9; c++) { ((AlloyFurnaceMenu) menu).addSlot(new Slot(inventory, c, 8 + c * 18, top + 58)); }
    }

    public int burn() { return data.get(0); }
    public int burnMax() { return data.get(1); }
    public int progress() { return data.get(2); }
    public int total() { return data.get(3); }

    @Override public boolean stillValid(Player player) { return stillValid(access, player, HdfvMaterials.ALLOY_FURNACE.get()); }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) { return ItemStack.EMPTY; }
        ItemStack s = slot.getItem(), copy = s.copy();
        int machine = AlloyFurnaceBlockEntity.SLOTS;
        if (index < machine) {
            if (!moveItemStackTo(s, machine, slots.size(), true)) { return ItemStack.EMPTY; }
        } else if (s.getBurnTime(RecipeType.SMELTING) > 0 && !s.is(net.minecraft.world.item.Items.COAL)) {
            if (!moveItemStackTo(s, AlloyFurnaceBlockEntity.FUEL, AlloyFurnaceBlockEntity.FUEL + 1, false)) { return ItemStack.EMPTY; }
        } else if (!moveItemStackTo(s, 0, AlloyFurnaceBlockEntity.INPUTS, false)) {
            return ItemStack.EMPTY;
        }
        if (s.isEmpty()) { slot.setByPlayer(ItemStack.EMPTY); } else { slot.setChanged(); }
        return copy;
    }
}
