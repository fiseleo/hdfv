package dev.mcmod.hdfv.neoforge.fluid;

import dev.mcmod.hdfv.fluid.HdfvFluids;
import dev.mcmod.hdfv.fluid.HydrogenStorage;
import dev.mcmod.hdfv.neoforge.HdfvConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/**
 * A hydrogen liquefier (氫液化機, 0.10): 700 bar hydrogen piped in becomes liquid hydrogen, HydrogenStorage
 * .LIQUEFIER_KG_PER_HOUR an hour of work time, for LIQUEFACTION_J_PER_KG of electricity (11.9 kWh/kg; Forge Energy into
 * its buffer, 1 FE = HdfvConfig.joulesPerFE). It holds 500 l of each; pumps fill its gas side and draw its liquid side
 * (its one fluid handler takes only hydrogen in and gives only the liquid out). Fractions of a litre and an FE carry.
 */
public final class HydrogenLiquefierBlockEntity extends BlockEntity {
    public enum State { IDLE, NO_POWER, NO_HYDROGEN, FULL, RUNNING }

    public static final int BUFFER_FE = 20000, LITRES = 500;

    final FluidTank gas = new FluidTank(LITRES, f -> HdfvFluids.HYDROGEN.equals(HdfvFluidRegistry.id(f.getFluid()))) {
        @Override protected void onContentsChanged() { setChanged(); }
    };
    final FluidTank liquid = new FluidTank(LITRES, f -> HdfvFluids.LIQUID_HYDROGEN.equals(HdfvFluidRegistry.id(f.getFluid()))) {
        @Override protected void onContentsChanged() { setChanged(); }
    };
    private final PumpBlockEntity.Buffer energy = new PumpBlockEntity.Buffer(BUFFER_FE, this::setChanged);
    private double gasOwed, liquidOwed, feOwed, madeKg;
    private State state = State.IDLE;

    public HydrogenLiquefierBlockEntity(BlockPos pos, BlockState s) { super(HdfvFluidRegistry.LIQUEFIER_ENTITY.get(), pos, s); }

    public IEnergyStorage energy() { return energy; }
    public State state() { return state; }
    public FluidStack gas() { return gas.getFluid(); }
    public FluidStack liquid() { return liquid.getFluid(); }
    /** Liquid hydrogen made since placed, kg. */
    public double madeKg() { return madeKg; }

    /** In: hydrogen to the gas side; out: the liquid side. */
    public IFluidHandler handler() {
        return new IFluidHandler() {
            @Override public int getTanks() { return 2; }
            @Override public FluidStack getFluidInTank(int i) { return i == 0 ? gas.getFluid() : liquid.getFluid(); }
            @Override public int getTankCapacity(int i) { return LITRES; }
            @Override public boolean isFluidValid(int i, FluidStack s) { return i == 0 ? gas.isFluidValid(s) : liquid.isFluidValid(s); }
            @Override public int fill(FluidStack s, FluidAction a) { return gas.fill(s, a); }
            @Override public FluidStack drain(FluidStack s, FluidAction a) { return liquid.drain(s, a); }
            @Override public FluidStack drain(int n, FluidAction a) { return liquid.drain(n, a); }
        };
    }

    public void tick() {
        tickWork();
        dev.mcmod.hdfv.neoforge.sound.SoundSync.report(this, hum());
    }

    /** Its compressors and expanders while it runs (0.19). */
    private dev.mcmod.hdfv.sound.MachineSound.Hum hum() { return state == State.RUNNING ? dev.mcmod.hdfv.sound.MachineSound.plant(dev.mcmod.aerocore.api.acoustics.ProcessNoise.Process.COMPRESSOR, 30e3, 0.9, true) : null; }

    private void tickWork() {
        if (level == null) { return; }
        double seconds = HdfvConfig.workSecondsPerTick();
        double kg = HydrogenStorage.LIQUEFIER_KG_PER_HOUR / 3600 * seconds;
        double gasL = kg / HdfvFluids.of(HdfvFluids.HYDROGEN).densityKgPerM3() * 1000 + gasOwed;
        double liquidL = kg / HdfvFluids.of(HdfvFluids.LIQUID_HYDROGEN).densityKgPerM3() * 1000 + liquidOwed;
        double fe = kg * HydrogenStorage.LIQUEFACTION_J_PER_KG / HdfvConfig.joulesPerFE() + feOwed;
        if (gas.getFluidAmount() < Math.ceil(gasL)) { state = State.NO_HYDROGEN; return; }
        if (liquid.getSpace() < Math.ceil(liquidL)) { state = State.FULL; return; }
        if (energy.getEnergyStored() < Math.ceil(fe)) { state = State.NO_POWER; return; }
        int g = (int) Math.floor(gasL), l = (int) Math.floor(liquidL), e = (int) Math.floor(fe);
        if (g > 0) { gas.drain(g, IFluidHandler.FluidAction.EXECUTE); }
        if (l > 0) { liquid.fill(new FluidStack(HdfvFluidRegistry.fluid(HdfvFluids.LIQUID_HYDROGEN), l), IFluidHandler.FluidAction.EXECUTE); }
        energy.use(e);
        gasOwed = gasL - g; liquidOwed = liquidL - l; feOwed = fe - e;
        madeKg += kg;
        state = State.RUNNING;
        setChanged();
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("gas", gas.writeToNBT(registries, new CompoundTag()));
        tag.put("liquid", liquid.writeToNBT(registries, new CompoundTag()));
        tag.put("energy", energy.serializeNBT(registries));
        tag.putDouble("gasOwed", gasOwed); tag.putDouble("liquidOwed", liquidOwed); tag.putDouble("feOwed", feOwed); tag.putDouble("madeKg", madeKg);
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        gas.readFromNBT(registries, tag.getCompound("gas"));
        liquid.readFromNBT(registries, tag.getCompound("liquid"));
        if (tag.contains("energy")) { energy.deserializeNBT(registries, tag.get("energy")); }
        gasOwed = tag.getDouble("gasOwed"); liquidOwed = tag.getDouble("liquidOwed"); feOwed = tag.getDouble("feOwed"); madeKg = tag.getDouble("madeKg");
    }
}
