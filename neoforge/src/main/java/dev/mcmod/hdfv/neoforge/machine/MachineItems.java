package dev.mcmod.hdfv.neoforge.machine;

import dev.mcmod.hdfv.manufacture.Station;
import dev.mcmod.hdfv.neoforge.HdfvRegistry;
import dev.mcmod.hdfv.neoforge.machining.HdfvMachining;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * A machine's items for hoppers and pipes (0.7): the input slots, then the output slots. Stock and workpieces go in;
 * workpieces, parts and leftover bits come out. The drawing is only set by hand.
 * 0.17: the consumable slots come first and take only supplies (tools, wheels, gas...), which the inputs refuse (but
 * the grinder's: a dull tool set goes there to be reground); nothing is taken out of them.
 */
record MachineItems(MachineBlockEntity be) implements IItemHandler {
    @Override public int getSlots() { return be.consumables + be.inputs + be.outputs; }

    /** The container slot of a handler slot. */
    private int at(int slot) { return slot < be.consumables ? be.firstConsumable() + slot : MachineBlockEntity.FIRST_INPUT + slot - be.consumables; }

    @Override public ItemStack getStackInSlot(int slot) { return be.items.getItem(at(slot)); }

    @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty() || !isItemValid(slot, stack)) { return stack; }
        ItemStack cur = getStackInSlot(slot);
        if (!cur.isEmpty() && !ItemStack.isSameItemSameComponents(cur, stack)) { return stack; }
        int n = Math.min(Math.min(getSlotLimit(slot), stack.getMaxStackSize()) - cur.getCount(), stack.getCount());
        if (n <= 0) { return stack; }
        if (!simulate) {
            if (cur.isEmpty()) { be.items.setItem(at(slot), stack.copyWithCount(n)); } else { cur.grow(n); be.setChanged(); }
        }
        return stack.copyWithCount(stack.getCount() - n);
    }

    @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (slot < be.consumables + be.inputs || amount <= 0) { return ItemStack.EMPTY; }
        ItemStack cur = getStackInSlot(slot);
        if (cur.isEmpty()) { return ItemStack.EMPTY; }
        int n = Math.min(amount, cur.getCount());
        ItemStack out = cur.copyWithCount(n);
        if (!simulate) { cur.shrink(n); be.setChanged(); }
        return out;
    }

    @Override public int getSlotLimit(int slot) { return 64; }

    @Override public boolean isItemValid(int slot, ItemStack stack) {
        boolean supply = HdfvMachining.supplyOf(stack) != null;
        if (slot < be.consumables) { return supply; }
        return slot < be.consumables + be.inputs && !stack.is(HdfvRegistry.BLUEPRINT.get()) && (!supply || be.station == Station.GRINDER);
    }
}
