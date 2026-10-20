package dev.mcmod.hdfv.neoforge.petroleum;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/** A process unit's outlet (0.8): its product waits here (4000 l) for a pump to draw it; nothing is put in from outside. */
public final class UnitOutletBlockEntity extends BlockEntity {
    public static final int LITRES = 4000;
    final FluidTank tank = new FluidTank(LITRES) {
        @Override protected void onContentsChanged() { setChanged(); }
    };

    public UnitOutletBlockEntity(BlockPos pos, BlockState s) { super(HdfvRefinery.OUTLET_ENTITY.get(), pos, s); }

    public FluidStack fluid() { return tank.getFluid(); }

    /** Room for a fluid, litres. */
    public int room(FluidStack f) { return tank.fill(f.copyWithAmount(LITRES), IFluidHandler.FluidAction.SIMULATE); }

    /** The unit puts its product in; what went in. */
    public int put(FluidStack f) { return tank.fill(f, IFluidHandler.FluidAction.EXECUTE); }

    public IFluidHandler handler() {
        return new IFluidHandler() {
            @Override public int getTanks() { return 1; }
            @Override public FluidStack getFluidInTank(int i) { return tank.getFluid(); }
            @Override public int getTankCapacity(int i) { return LITRES; }
            @Override public boolean isFluidValid(int i, FluidStack s) { return false; }
            @Override public int fill(FluidStack s, FluidAction a) { return 0; }
            @Override public FluidStack drain(FluidStack s, FluidAction a) { return tank.drain(s, a); }
            @Override public FluidStack drain(int n, FluidAction a) { return tank.drain(n, a); }
        };
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) { super.saveAdditional(tag, registries); tank.writeToNBT(registries, tag); }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) { super.loadAdditional(tag, registries); tank.readFromNBT(registries, tag); }
}
