package dev.mcmod.hdfv.neoforge.fluid;

import dev.mcmod.hdfv.fluid.HdfvFluids;
import dev.mcmod.hdfv.fluid.HydrogenStorage;
import dev.mcmod.hdfv.neoforge.HdfvConfig;
import dev.mcmod.hdfv.neoforge.multiblock.Multiblock;
import dev.mcmod.hdfv.neoforge.multiblock.Multiblocks;
import dev.mcmod.hdfv.structure.Structures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/**
 * A storage tank's valve (0.8): the controller of the storage tank multiblock (structure.Structures.STORAGE_TANK). While
 * formed it holds 9 m^3 a course of one fluid; dissolved, it keeps what it holds but lets nothing in or out; formed
 * again smaller, what no longer fits is lost (spilt).
 */
public final class StorageValveBlockEntity extends BlockEntity implements Multiblocks.Holder {
    private final Multiblock multiblock;
    /** 0.10: no 700 bar hydrogen; liquid hydrogen boils off its bare walls (core fluid.HydrogenStorage). */
    final FluidTank tank = new FluidTank(0, f -> HydrogenStorage.takes(HydrogenStorage.Store.STORAGE_TANK, HdfvFluidRegistry.id(f.getFluid()))) {
        @Override protected void onContentsChanged() { setChanged(); }
    };
    private double boiled;

    public StorageValveBlockEntity(BlockPos pos, BlockState s) {
        super(HdfvFluidRegistry.VALVE_ENTITY.get(), pos, s);
        multiblock = new Multiblock(Structures.STORAGE_TANK, pos);
    }

    @Override public Multiblock multiblock() { return multiblock; }

    public void tick() {
        if (!(level instanceof ServerLevel server)) { return; }
        boolean was = multiblock.formed();
        multiblock.tick(server);
        if (multiblock.formed()) {
            int litres = Structures.storageLitres(multiblock.match());
            if (tank.getCapacity() != litres) {
                tank.setCapacity(litres);
                if (tank.getFluidAmount() > litres) { tank.setFluid(tank.getFluid().copyWithAmount(litres)); }
                setChanged();
            }
        }
        if (was != multiblock.formed()) { PipeNetworks.changed(level); }
        if (!tank.isEmpty() && tank.getFluid().getFluid() == HdfvFluidRegistry.fluid(HdfvFluids.LIQUID_HYDROGEN)) {
            double kg = HydrogenStorage.boilOffKgPerS(HydrogenStorage.Store.STORAGE_TANK, tank.getCapacity() / 1000.0) * HdfvConfig.workSecondsPerTick();
            boiled += kg / HdfvFluids.of(HdfvFluids.LIQUID_HYDROGEN).densityKgPerM3() * 1000;
            int whole = (int) Math.floor(boiled);
            if (whole > 0) { boiled -= tank.drain(whole, IFluidHandler.FluidAction.EXECUTE).getAmount(); if (tank.isEmpty()) { boiled = 0; } }
        }
    }

    /** Its tank, open only while formed. */
    public IFluidHandler handler() {
        return new IFluidHandler() {
            private boolean open() { return multiblock.formed() && tank.getCapacity() > 0; }
            @Override public int getTanks() { return 1; }
            @Override public FluidStack getFluidInTank(int i) { return tank.getFluid(); }
            @Override public int getTankCapacity(int i) { return tank.getCapacity(); }
            @Override public boolean isFluidValid(int i, FluidStack s) { return open() && tank.isFluidValid(s); }
            @Override public int fill(FluidStack s, FluidAction a) { return open() ? tank.fill(s, a) : 0; }
            @Override public FluidStack drain(FluidStack s, FluidAction a) { return open() ? tank.drain(s, a) : FluidStack.EMPTY; }
            @Override public FluidStack drain(int n, FluidAction a) { return open() ? tank.drain(n, a) : FluidStack.EMPTY; }
        };
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tank.writeToNBT(registries, tag);
        tag.putInt("capacity", tank.getCapacity());
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        tank.setCapacity(Math.max(tag.getInt("capacity"), 0));
        tank.readFromNBT(registries, tag);
    }
}
