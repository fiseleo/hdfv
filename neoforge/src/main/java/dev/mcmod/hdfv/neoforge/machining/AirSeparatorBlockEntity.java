package dev.mcmod.hdfv.neoforge.machining;

import dev.mcmod.hdfv.manufacture.Consumable;
import dev.mcmod.hdfv.manufacture.GasSupply;
import dev.mcmod.hdfv.neoforge.HdfvConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * An air separation unit (空氣分離機, 0.17): empty gas cylinders in, full ones of the gas it is set to out (oxygen,
 * nitrogen or argon, GasSupply), at its rate an hour of work time for its electricity (Forge Energy into its buffer,
 * 1 FE = HdfvConfig.joulesPerFE). A cylinder fills as the gas comes (a full one is its supply item at no damage);
 * fractions of an FE carry. Used empty-handed it tells its state; sneaking it switches the gas (with the cylinder
 * being filled emptied back).
 */
public final class AirSeparatorBlockEntity extends BlockEntity {
    public enum State { IDLE, NO_POWER, NO_CYLINDER, FULL, RUNNING }

    public static final int BUFFER_FE = 20000, EMPTIES = 0, FULLS = 1;

    final SimpleContainer items = new SimpleContainer(2) {
        @Override public void setChanged() { super.setChanged(); AirSeparatorBlockEntity.this.setChanged(); }
    };
    private final dev.mcmod.hdfv.neoforge.power.Buffer energy = new dev.mcmod.hdfv.neoforge.power.Buffer(BUFFER_FE, Integer.MAX_VALUE, 0, this::setChanged);
    private GasSupply.Product product = GasSupply.Product.OXYGEN;
    private double filledKg, feOwed;
    private State state = State.IDLE;

    public AirSeparatorBlockEntity(BlockPos pos, BlockState s) { super(HdfvMachining.AIR_SEPARATOR_ENTITY.get(), pos, s); }

    public IEnergyStorage energy() { return energy; }
    public State state() { return state; }
    public GasSupply.Product product() { return product; }
    /** The cylinder being filled, kg so far. */
    public double filledKg() { return filledKg; }
    public SimpleContainer items() { return items; }

    /** Switches to the next gas; the cylinder being filled starts again. */
    public void switchGas() { product = product.next(); filledKg = 0; setChanged(); }

    /** Hoppers: empty cylinders in, full ones out. */
    public IItemHandler handler() {
        return new IItemHandler() {
            @Override public int getSlots() { return 2; }
            @Override public ItemStack getStackInSlot(int i) { return items.getItem(i); }
            @Override public ItemStack insertItem(int i, ItemStack s, boolean simulate) {
                if (i != EMPTIES || !isItemValid(i, s)) { return s; }
                ItemStack cur = items.getItem(EMPTIES);
                int n = Math.min(16 - cur.getCount(), s.getCount());
                if (n <= 0 || !cur.isEmpty() && !ItemStack.isSameItemSameComponents(cur, s)) { return s; }
                if (!simulate) { if (cur.isEmpty()) { items.setItem(EMPTIES, s.copyWithCount(n)); } else { cur.grow(n); setChanged(); } }
                return s.copyWithCount(s.getCount() - n);
            }
            @Override public ItemStack extractItem(int i, int n, boolean simulate) {
                if (i != FULLS || n <= 0) { return ItemStack.EMPTY; }
                ItemStack cur = items.getItem(FULLS);
                if (cur.isEmpty()) { return ItemStack.EMPTY; }
                ItemStack out = cur.copyWithCount(Math.min(n, cur.getCount()));
                if (!simulate) { cur.shrink(out.getCount()); setChanged(); }
                return out;
            }
            @Override public int getSlotLimit(int i) { return 16; }
            @Override public boolean isItemValid(int i, ItemStack s) { return i == EMPTIES && s.is(HdfvMachining.EMPTY_CYLINDER.get()); }
        };
    }

    public void tick() {
        tickWork();
        dev.mcmod.hdfv.neoforge.sound.SoundSync.report(this, hum());
    }

    /** Its air compressor while it runs (0.19). */
    private dev.mcmod.hdfv.sound.MachineSound.Hum hum() { return state == State.RUNNING ? dev.mcmod.hdfv.sound.MachineSound.plant(dev.mcmod.aerocore.api.acoustics.ProcessNoise.Process.COMPRESSOR, 15e3, 0.9, true) : null; }

    private void tickWork() {
        if (level == null) { return; }
        if (items.getItem(EMPTIES).isEmpty() && filledKg <= 0) { state = State.NO_CYLINDER; return; }
        if (!items.getItem(FULLS).isEmpty()) { state = State.FULL; return; }
        double kg = product.kgPerHour() / 3600 * HdfvConfig.workSecondsPerTick();
        double fe = kg * product.joulesPerKg() / HdfvConfig.joulesPerFE() + feOwed;
        if (energy.getEnergyStored() < Math.ceil(fe)) { state = State.NO_POWER; return; }
        int e = (int) Math.floor(fe);
        energy.use(e);
        feOwed = fe - e;
        if (filledKg <= 0) { items.getItem(EMPTIES).shrink(1); }
        filledKg += kg;
        if (filledKg >= product.cylinderKg()) {
            items.setItem(FULLS, HdfvMachining.stack(product.supply(), 1));
            filledKg = 0;
        }
        state = State.RUNNING;
        setChanged();
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items.getItems(), registries);
        tag.putInt("energy", energy.getEnergyStored());
        tag.putString("product", product.id());
        tag.putDouble("filledKg", filledKg);
        tag.putDouble("feOwed", feOwed);
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        NonNullList<ItemStack> list = NonNullList.withSize(2, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, list, registries);
        for (int i = 0; i < 2; i++) { items.setItem(i, list.get(i)); }
        energy.set(tag.getInt("energy"));
        for (GasSupply.Product p : GasSupply.Product.values()) { if (p.id().equals(tag.getString("product"))) { product = p; } }
        filledKg = tag.getDouble("filledKg");
        feOwed = tag.getDouble("feOwed");
    }

    /** The gas a supply item holds, for tests (null: none). */
    static GasSupply.Product productOf(Consumable.Supply s) {
        for (GasSupply.Product p : GasSupply.Product.values()) { if (p.supply() == s) { return p; } }
        return null;
    }
}
