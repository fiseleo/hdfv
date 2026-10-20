package dev.mcmod.hdfv.neoforge.material;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The alloy furnace (0.7): four input slots, a fuel slot and two outputs (result, by-product). It burns fuel as a
 * furnace does and works through the hdfv:alloying recipe its inputs make, one batch at a time.
 */
public final class AlloyFurnaceBlockEntity extends BlockEntity implements MenuProvider {
    public static final int INPUTS = 4, FUEL = 4, RESULT = 5, BYPRODUCT = 6, SLOTS = 7;

    final SimpleContainer items = new SimpleContainer(SLOTS) {
        @Override public void setChanged() { super.setChanged(); AlloyFurnaceBlockEntity.this.setChanged(); }
    };
    int burn, burnMax, progress, total;

    final ContainerData data = new ContainerData() {
        @Override public int get(int i) { return switch (i) { case 0 -> burn; case 1 -> burnMax; case 2 -> progress; default -> total; }; }
        @Override public void set(int i, int v) {
            switch (i) { case 0 -> burn = v; case 1 -> burnMax = v; case 2 -> progress = v; default -> total = v; }
        }
        @Override public int getCount() { return 4; }
    };

    public AlloyFurnaceBlockEntity(BlockPos pos, BlockState state) { super(HdfvMaterials.ALLOY_FURNACE_ENTITY.get(), pos, state); }

    AlloyingRecipe.Input input() {
        List<ItemStack> in = new ArrayList<>();
        for (int i = 0; i < INPUTS; i++) { in.add(items.getItem(i)); }
        return new AlloyingRecipe.Input(in);
    }

    Optional<RecipeHolder<AlloyingRecipe>> recipe(Level level) {
        return level.getRecipeManager().getRecipeFor(HdfvMaterials.ALLOYING.get(), input(), level);
    }

    /** Whether a stack fits into an output slot. */
    private boolean fits(int slot, ItemStack add) {
        if (add.isEmpty()) { return true; }
        ItemStack s = items.getItem(slot);
        return s.isEmpty() || (ItemStack.isSameItemSameComponents(s, add) && s.getCount() + add.getCount() <= s.getMaxStackSize());
    }

    public static void tick(Level level, BlockPos pos, BlockState state, AlloyFurnaceBlockEntity be) {
        boolean wasLit = be.burn > 0, changed = false;
        if (be.burn > 0) { be.burn--; }
        Optional<RecipeHolder<AlloyingRecipe>> holder = be.recipe(level);
        AlloyingRecipe r = holder.map(RecipeHolder::value).orElse(null);
        boolean can = r != null && be.fits(RESULT, r.result()) && be.fits(BYPRODUCT, r.byproduct());
        if (can && be.burn == 0) {
            ItemStack fuel = be.items.getItem(FUEL);
            int t = fuel.isEmpty() ? 0 : fuel.getBurnTime(RecipeType.SMELTING);
            if (t > 0) {
                be.burn = be.burnMax = t;
                ItemStack remainder = fuel.getCraftingRemainingItem();
                fuel.shrink(1);
                if (fuel.isEmpty() && !remainder.isEmpty()) { be.items.setItem(FUEL, remainder); }
                changed = true;
            }
        }
        if (can && be.burn > 0) {
            be.total = r.ticks();
            if (++be.progress >= r.ticks()) {
                int[] slots = r.slots(be.input());
                for (int k = 0; k < slots.length; k++) { be.items.getItem(slots[k]).shrink(r.ingredients().get(k).count()); }
                grow(be, RESULT, r.result());
                grow(be, BYPRODUCT, r.byproduct());
                be.progress = 0;
            }
            changed = true;
        } else if (be.progress > 0) {
            be.progress = Math.max(0, be.progress - 2);
            changed = true;
        }
        if (wasLit != be.burn > 0) {
            level.setBlock(pos, state.setValue(AlloyFurnaceBlock.LIT, be.burn > 0), 3);
        }
        if (changed) { be.setChanged(); }
        dev.mcmod.hdfv.neoforge.sound.SoundSync.report(be, be.burn > 0 ? dev.mcmod.hdfv.sound.MachineSound.plant(dev.mcmod.aerocore.api.acoustics.ProcessNoise.Process.BURNER, 15e3, 0.6, true) : null);
    }

    private static void grow(AlloyFurnaceBlockEntity be, int slot, ItemStack add) {
        if (add.isEmpty()) { return; }
        ItemStack s = be.items.getItem(slot);
        if (s.isEmpty()) { be.items.setItem(slot, add.copy()); } else { s.grow(add.getCount()); }
    }

    /**
     * 0.23 (the user chose automation with other mods): hoppers and pipes put fuel into the fuel slot (from the sides)
     * and anything else into the inputs, and take the result and the by-product out (from below).
     */
    public net.neoforged.neoforge.items.IItemHandler handler(net.minecraft.core.Direction side) {
        boolean below = side == net.minecraft.core.Direction.DOWN;
        return new net.neoforged.neoforge.items.IItemHandler() {
            @Override public int getSlots() { return SLOTS; }
            @Override public ItemStack getStackInSlot(int i) { return items.getItem(i); }
            @Override public ItemStack insertItem(int i, ItemStack s, boolean simulate) {
                if (below || s.isEmpty() || !isItemValid(i, s)) { return s; }
                ItemStack cur = items.getItem(i);
                if (!cur.isEmpty() && !ItemStack.isSameItemSameComponents(cur, s)) { return s; }
                int n = Math.min(s.getMaxStackSize() - cur.getCount(), s.getCount());
                if (n <= 0) { return s; }
                if (!simulate) { if (cur.isEmpty()) { items.setItem(i, s.copyWithCount(n)); } else { cur.grow(n); setChanged(); } }
                return s.copyWithCount(s.getCount() - n);
            }
            @Override public ItemStack extractItem(int i, int n, boolean simulate) {
                if (i != RESULT && i != BYPRODUCT || n <= 0) { return ItemStack.EMPTY; }
                ItemStack cur = items.getItem(i);
                if (cur.isEmpty()) { return ItemStack.EMPTY; }
                ItemStack out = cur.copyWithCount(Math.min(n, cur.getCount()));
                if (!simulate) { cur.shrink(out.getCount()); setChanged(); }
                return out;
            }
            @Override public int getSlotLimit(int i) { return 64; }
            @Override public boolean isItemValid(int i, ItemStack s) {
                boolean fuel = s.getBurnTime(RecipeType.SMELTING) > 0;
                boolean top = side == net.minecraft.core.Direction.UP || side == null;
                if (i == FUEL) { return fuel && !top; }
                return i < INPUTS && (top || !fuel);
            }
        };
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items.getItems(), registries);
        tag.putInt("Burn", burn);
        tag.putInt("BurnMax", burnMax);
        tag.putInt("Progress", progress);
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        NonNullList<ItemStack> list = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, list, registries);
        for (int i = 0; i < SLOTS; i++) { items.setItem(i, list.get(i)); }
        burn = tag.getInt("Burn");
        burnMax = tag.getInt("BurnMax");
        progress = tag.getInt("Progress");
    }

    @Override public Component getDisplayName() { return Component.translatable("block.hdfv.alloy_furnace"); }

    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new AlloyFurnaceMenu(id, inventory, items, data, worldPosition);
    }
}
