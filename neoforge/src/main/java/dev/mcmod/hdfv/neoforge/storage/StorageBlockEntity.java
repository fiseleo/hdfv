package dev.mcmod.hdfv.neoforge.storage;

import dev.mcmod.hdfv.storage.StorageKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.InvWrapper;

/**
 * A storage block's inventory (0.25): as many stacks as its kind holds, named as a chest is (a renamed item's name is
 * kept and shown as the screen's title), taking only what its kind takes (the parts rack: the item tag hdfv:parts_rack)
 * whether by hand, by hopper or by another mod's pipe. Every side is the same item handler, so assembly rooms, the
 * refit shop and the paint booth draw their materials from it as from a chest.
 */
public final class StorageBlockEntity extends BaseContainerBlockEntity {
    public final StorageKind kind;
    private NonNullList<ItemStack> items;
    private final IItemHandler handler = new InvWrapper(this);

    public StorageBlockEntity(BlockPos pos, BlockState state) {
        super(HdfvStorage.ENTITY.get(), pos, state);
        this.kind = state.getBlock() instanceof StorageBlock b ? b.kind : StorageKind.STORAGE_CRATE;
        this.items = NonNullList.withSize(kind.slots, ItemStack.EMPTY);
    }

    /** What it takes. */
    public StorageMenu.Filter filter() { return kind.partsOnly ? StorageMenu.Filter.PARTS : StorageMenu.Filter.ANY; }

    /** Its items for hoppers, pipes and rooms (any side). */
    public IItemHandler handler() { return handler; }

    /** The mass of what is in it, kg (ItemMass). */
    public double massKg() { return ItemMass.kg(items); }

    @Override public int getContainerSize() { return kind.slots; }
    @Override protected Component getDefaultName() { return Component.translatable("block.hdfv." + kind.id()); }
    @Override protected NonNullList<ItemStack> getItems() { return items; }
    @Override protected void setItems(NonNullList<ItemStack> items) { this.items = items; }
    @Override public boolean canPlaceItem(int slot, ItemStack s) { return filter().test(s); }

    @Override protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return new StorageMenu(id, inventory, this, filter(), p -> Container.stillValidBlockEntity(this, p), null);
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items = NonNullList.withSize(kind.slots, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
    }
}
