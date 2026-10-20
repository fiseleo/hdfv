package dev.mcmod.hdfv.neoforge.fluid;

import dev.mcmod.hdfv.fluid.HdfvFluids;
import dev.mcmod.hdfv.fluid.HydrogenStorage;
import dev.mcmod.hdfv.fluid.Piping;
import dev.mcmod.hdfv.neoforge.HdfvConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import java.util.ArrayList;
import java.util.List;

/**
 * A tank block (0.8): a cubic metre of one fluid. Tank blocks stacked are one tank (column()): one fluid, filled from
 * the bottom up and drawn from the top down as a liquid settles; each block keeps its own litres, so taking one away
 * spills only what was in it. 0.10: its store (the block's: tank, cryogenic tank, cylinder bank) decides what it takes
 * and how fast liquid hydrogen boils away in it (core fluid.HydrogenStorage, per tick of work time); a cryogenic tank
 * holds 800 l inside its insulation, a cylinder bank 500 l of 700 bar gas (its cylinders take half the block).
 */
public final class TankBlockEntity extends BlockEntity {
    private final HydrogenStorage.Store store;
    final FluidTank tank;
    /** Litres boiled off and not yet taken (fractions carry). */
    private double boiled;

    public TankBlockEntity(BlockPos pos, BlockState state) {
        super(HdfvFluidRegistry.TANK_ENTITY.get(), pos, state);
        store = state.getBlock() instanceof TankBlock b ? b.store() : HydrogenStorage.Store.TANK;
        int litres = switch (store) { case CRYOGENIC_TANK -> 800; case CYLINDERS -> 500; default -> Piping.TANK_LITRES; };
        tank = new FluidTank(litres, f -> HydrogenStorage.takes(store, HdfvFluidRegistry.id(f.getFluid()))) {
            @Override protected void onContentsChanged() { setChanged(); }
        };
    }

    public FluidStack fluid() { return tank.getFluid(); }
    public HydrogenStorage.Store store() { return store; }

    /** Liquid hydrogen boils away (server, each tick). */
    void tick() {
        if (tank.isEmpty() || tank.getFluid().getFluid() != HdfvFluidRegistry.fluid(HdfvFluids.LIQUID_HYDROGEN)) { return; }
        double kg = HydrogenStorage.boilOffKgPerS(store, tank.getCapacity() / 1000.0) * HdfvConfig.workSecondsPerTick();
        boiled += kg / HdfvFluids.of(HdfvFluids.LIQUID_HYDROGEN).densityKgPerM3() * 1000;
        int whole = (int) Math.floor(boiled);
        if (whole > 0) {
            boiled -= tank.drain(whole, IFluidHandler.FluidAction.EXECUTE).getAmount();
            if (tank.isEmpty()) { boiled = 0; }
        }
    }

    /** The tank blocks of this one's column (of its own kind), bottom first. */
    List<TankBlockEntity> stack() {
        List<TankBlockEntity> out = new ArrayList<>();
        if (level == null) { out.add(this); return out; }
        BlockPos p = worldPosition;
        while (level.getBlockEntity(p.below()) instanceof TankBlockEntity t && t.store == store) { p = p.below(); }
        while (level.getBlockEntity(p) instanceof TankBlockEntity t && t.store == store) { out.add(t); p = p.above(); }
        return out;
    }

    /** The column as one tank. */
    public IFluidHandler column() { return new Column(stack()); }

    private static final class Column implements IFluidHandler {
        private final List<TankBlockEntity> tanks;

        Column(List<TankBlockEntity> tanks) { this.tanks = tanks; }

        private FluidStack held() {
            for (TankBlockEntity t : tanks) { if (!t.tank.isEmpty()) { return t.tank.getFluid(); } }
            return FluidStack.EMPTY;
        }

        @Override public int getTanks() { return 1; }

        @Override public FluidStack getFluidInTank(int i) {
            FluidStack h = held();
            if (h.isEmpty()) { return FluidStack.EMPTY; }
            int n = 0;
            for (TankBlockEntity t : tanks) { n += t.tank.getFluidAmount(); }
            return h.copyWithAmount(n);
        }

        @Override public int getTankCapacity(int i) { int n = 0; for (TankBlockEntity t : tanks) { n += t.tank.getCapacity(); } return n; }

        @Override public boolean isFluidValid(int i, FluidStack s) {
            FluidStack h = held();
            return tanks.getFirst().tank.isFluidValid(s) && (h.isEmpty() || FluidStack.isSameFluidSameComponents(h, s));
        }

        @Override public int fill(FluidStack s, FluidAction action) {
            if (s.isEmpty() || !isFluidValid(0, s)) { return 0; }
            int left = s.getAmount();
            for (TankBlockEntity t : tanks) {
                if (left <= 0) { break; }
                left -= t.tank.fill(s.copyWithAmount(left), action);
            }
            return s.getAmount() - left;
        }

        @Override public FluidStack drain(FluidStack s, FluidAction action) {
            FluidStack h = held();
            if (s.isEmpty() || h.isEmpty() || !FluidStack.isSameFluidSameComponents(h, s)) { return FluidStack.EMPTY; }
            return drain(s.getAmount(), action);
        }

        @Override public FluidStack drain(int amount, FluidAction action) {
            FluidStack h = held();
            if (amount <= 0 || h.isEmpty()) { return FluidStack.EMPTY; }
            int left = amount;
            for (int i = tanks.size() - 1; i >= 0 && left > 0; i--) { left -= tanks.get(i).tank.drain(left, action).getAmount(); }
            return h.copyWithAmount(amount - left);
        }
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tank.writeToNBT(registries, tag);
        tag.putDouble("boiled", boiled);
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        tank.readFromNBT(registries, tag);
        boiled = tag.getDouble("boiled");
    }
}
