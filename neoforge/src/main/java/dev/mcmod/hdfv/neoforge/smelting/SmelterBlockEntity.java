package dev.mcmod.hdfv.neoforge.smelting;

import dev.mcmod.aerocore.api.acoustics.ProcessNoise;
import dev.mcmod.hdfv.neoforge.HdfvConfig;
import dev.mcmod.hdfv.neoforge.material.AlloyingRecipe;
import dev.mcmod.hdfv.neoforge.material.ElectricRecipe;
import dev.mcmod.hdfv.neoforge.material.HdfvMaterials;
import dev.mcmod.hdfv.neoforge.power.Buffer;
import dev.mcmod.hdfv.recipe.Metallurgy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * An electric arc furnace or an electrolysis cell (0.23): four inputs, a result and two by-product outputs, a Forge
 * Energy buffer. It works through the recipe its inputs make (hdfv:electric_furnace or hdfv:electrolysis, as the
 * block is), drawing up to its rated power (Metallurgy.ARC_FURNACE_W, CELL_W) each work tick until the batch's energy
 * is in (1 FE = HdfvConfig.joulesPerFE of work time). Hoppers put into the inputs and take from the outputs.
 */
public final class SmelterBlockEntity extends BlockEntity implements MenuProvider {
    public enum State { IDLE, NO_RECIPE, NO_POWER, OUTPUT_FULL, WORKING }

    public static final int INPUTS = 4, RESULT = 4, BYPRODUCT = 5, SLOTS = 7;

    final SimpleContainer items = new SimpleContainer(SLOTS) {
        @Override public void setChanged() { super.setChanged(); SmelterBlockEntity.this.setChanged(); }
    };
    private final Buffer energy;
    /** Energy into the batch so far, J; the recipe it is for. */
    private double doneJ;
    private ResourceLocation batch;
    private State state = State.IDLE;
    /** Last tick's draw as a share of the rated power (0..1000) and the batch's energy (0.1 kWh), for the screen. */
    private int drawn, batchKwh10;

    final ContainerData data = new ContainerData() {
        @Override public int get(int i) {
            return switch (i) { case 0 -> progress(); case 1 -> state.ordinal(); case 2 -> drawn; default -> batchKwh10; };
        }
        @Override public void set(int i, int v) { }
        @Override public int getCount() { return 4; }
    };

    public SmelterBlockEntity(BlockPos pos, BlockState s) {
        super(HdfvSmelting.SMELTER_ENTITY.get(), pos, s);
        int perTick = (int) Math.ceil(ratedW() * HdfvConfig.workSecondsPerTick() / HdfvConfig.joulesPerFE());
        this.energy = new Buffer(Math.max(2 * perTick, 1000), Integer.MAX_VALUE, 0, this::setChanged);
    }

    public ElectricRecipe.Machine machine() {
        return getBlockState().getBlock() instanceof SmelterBlock b ? b.machine : ElectricRecipe.Machine.ARC_FURNACE;
    }

    /** Its rated power, W. */
    public double ratedW() { return machine() == ElectricRecipe.Machine.ARC_FURNACE ? Metallurgy.ARC_FURNACE_W : Metallurgy.CELL_W; }

    public IEnergyStorage energy() { return energy; }
    public State state() { return state; }
    public SimpleContainer items() { return items; }

    /** The batch's progress, 0..1000. */
    int progress() {
        Optional<RecipeHolder<ElectricRecipe>> r = recipe();
        return r.isEmpty() || !r.get().id().equals(batch) ? 0 : (int) Math.min(1000, 1000 * doneJ / r.get().value().joules());
    }

    AlloyingRecipe.Input input() {
        List<ItemStack> in = new ArrayList<>();
        for (int i = 0; i < INPUTS; i++) { in.add(items.getItem(i)); }
        return new AlloyingRecipe.Input(in);
    }

    Optional<RecipeHolder<ElectricRecipe>> recipe() {
        if (level == null) { return Optional.empty(); }
        var type = machine() == ElectricRecipe.Machine.ARC_FURNACE ? HdfvMaterials.ARC_FURNACE.get() : HdfvMaterials.ELECTROLYSIS.get();
        return level.getRecipeManager().getRecipeFor(type, input(), level);
    }

    private boolean fits(int slot, ItemStack add) {
        if (add.isEmpty()) { return true; }
        ItemStack s = items.getItem(slot);
        return s.isEmpty() || ItemStack.isSameItemSameComponents(s, add) && s.getCount() + add.getCount() <= s.getMaxStackSize();
    }

    private void grow(int slot, ItemStack add) {
        if (add.isEmpty()) { return; }
        ItemStack s = items.getItem(slot);
        if (s.isEmpty()) { items.setItem(slot, add.copy()); } else { s.grow(add.getCount()); }
    }

    public void tick() {
        State was = state;
        tickWork();
        if ((was == State.WORKING) != (state == State.WORKING) && level != null && getBlockState().hasProperty(SmelterBlock.LIT)) {
            level.setBlock(worldPosition, getBlockState().setValue(SmelterBlock.LIT, state == State.WORKING), 3);
        }
        ProcessNoise.Process sound = machine() == ElectricRecipe.Machine.ARC_FURNACE ? ProcessNoise.Process.PLASMA : ProcessNoise.Process.HUM;
        dev.mcmod.hdfv.neoforge.sound.SoundSync.report(this, state == State.WORKING ? dev.mcmod.hdfv.sound.MachineSound.plant(sound, ratedW(), drawn / 1000.0, true) : null);
    }

    private void tickWork() {
        drawn = 0;
        // as the machines: it draws from what is beside it (a cable, a battery, a generator) into its buffer
        if (level != null) { dev.mcmod.hdfv.neoforge.power.EnergyNetworks.pull(level, worldPosition, energy); }
        Optional<RecipeHolder<ElectricRecipe>> holder = recipe();
        if (holder.isEmpty()) { idle(State.NO_RECIPE); batchKwh10 = 0; return; }
        ElectricRecipe r = holder.get().value();
        if (!holder.get().id().equals(batch)) { batch = holder.get().id(); doneJ = 0; }
        batchKwh10 = (int) Math.round(r.kwh() * 10);
        boolean room = fits(RESULT, r.result());
        // the by-products: the first in its slot, the second in the last (or with the first when it is the same)
        List<ItemStack> by = r.byproducts();
        if (!by.isEmpty()) { room &= fits(BYPRODUCT, by.get(0)); }
        if (by.size() > 1) { room &= fits(BYPRODUCT + 1, by.get(1)); }
        if (!room) { state = State.OUTPUT_FULL; setChanged(); return; }
        double perTickJ = ratedW() * HdfvConfig.workSecondsPerTick(), wantJ = Math.min(perTickJ, r.joules() - doneJ);
        int fe = Math.min(energy.getEnergyStored(), (int) Math.ceil(wantJ / HdfvConfig.joulesPerFE()));
        if (fe <= 0) { state = State.NO_POWER; setChanged(); return; }
        energy.use(fe);
        doneJ += fe * HdfvConfig.joulesPerFE();
        drawn = (int) Math.min(1000, Math.round(1000 * fe * HdfvConfig.joulesPerFE() / perTickJ));
        state = State.WORKING;
        if (doneJ + 1e-6 >= r.joules()) {
            int[] slots = r.slots(input());
            if (slots != null) {
                for (int k = 0; k < slots.length; k++) { items.getItem(slots[k]).shrink(r.ingredients().get(k).count()); }
                grow(RESULT, r.result());
                if (!by.isEmpty()) { grow(BYPRODUCT, by.get(0)); }
                if (by.size() > 1) { grow(BYPRODUCT + 1, by.get(1)); }
            }
            doneJ = 0;
        }
        setChanged();
    }

    private void idle(State s) {
        if (state != s || doneJ != 0) { state = s; doneJ = 0; setChanged(); }
    }

    /** Hoppers and pipes of items: into the inputs (any side), out of the outputs. */
    public IItemHandler handler() {
        return new IItemHandler() {
            @Override public int getSlots() { return SLOTS; }
            @Override public ItemStack getStackInSlot(int i) { return items.getItem(i); }
            @Override public ItemStack insertItem(int i, ItemStack s, boolean simulate) {
                if (i >= INPUTS || s.isEmpty()) { return s; }
                ItemStack cur = items.getItem(i);
                if (!cur.isEmpty() && !ItemStack.isSameItemSameComponents(cur, s)) { return s; }
                int n = Math.min(s.getMaxStackSize() - cur.getCount(), s.getCount());
                if (n <= 0) { return s; }
                if (!simulate) { if (cur.isEmpty()) { items.setItem(i, s.copyWithCount(n)); } else { cur.grow(n); setChanged(); } }
                return s.copyWithCount(s.getCount() - n);
            }
            @Override public ItemStack extractItem(int i, int n, boolean simulate) {
                if (i < RESULT || n <= 0) { return ItemStack.EMPTY; }
                ItemStack cur = items.getItem(i);
                if (cur.isEmpty()) { return ItemStack.EMPTY; }
                ItemStack out = cur.copyWithCount(Math.min(n, cur.getCount()));
                if (!simulate) { cur.shrink(out.getCount()); setChanged(); }
                return out;
            }
            @Override public int getSlotLimit(int i) { return 64; }
            @Override public boolean isItemValid(int i, ItemStack s) { return i < INPUTS; }
        };
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items.getItems(), registries);
        tag.putInt("Energy", energy.getEnergyStored());
        tag.putDouble("DoneJ", doneJ);
        if (batch != null) { tag.putString("Batch", batch.toString()); }
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        NonNullList<ItemStack> list = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, list, registries);
        for (int i = 0; i < SLOTS; i++) { items.setItem(i, list.get(i)); }
        energy.set(tag.getInt("Energy"));
        doneJ = tag.getDouble("DoneJ");
        batch = tag.contains("Batch") ? ResourceLocation.tryParse(tag.getString("Batch")) : null;
    }

    @Override public Component getDisplayName() { return getBlockState().getBlock().getName(); }

    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new SmelterMenu(id, inventory, items, data, worldPosition);
    }
}
