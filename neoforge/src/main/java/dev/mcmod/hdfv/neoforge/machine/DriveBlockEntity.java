package dev.mcmod.hdfv.neoforge.machine;

import dev.mcmod.hdfv.neoforge.multiblock.Multiblocks;
import dev.mcmod.hdfv.neoforge.power.ShaftEnd;
import dev.mcmod.hdfv.power.PowerPlant;
import dev.mcmod.hdfv.power.ShaftLine;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * A drive's block entity (0.9): stands for its machine (the controller of the multiblock it is formed in) on a line
 * shaft (the pulley seat) or a cable network (the motor seat). Alone, it gives and takes nothing.
 */
public final class DriveBlockEntity extends BlockEntity implements ShaftEnd {
    public DriveBlockEntity(BlockPos pos, BlockState s) { super(HdfvMachines.DRIVE_ENTITY.get(), pos, s); }

    private boolean electric() { return getBlockState().getBlock() instanceof DriveBlock d && d.electric; }

    /** The machine it drives (formed, driven by this cell's kind), or null. */
    public MachineBlockEntity machine() {
        if (!(level instanceof ServerLevel server)) { return null; }
        BlockPos c = Multiblocks.controllerOf(server, worldPosition);
        return c != null && server.getBlockEntity(c) instanceof MachineBlockEntity m && m.formed() && worldPosition.equals(m.drivePos()) ? m : null;
    }

    // the pulley seat on a line shaft
    @Override public boolean onShaft() { return !electric(); }

    @Override public ShaftLine.End lineEnd(double rpm) {
        MachineBlockEntity m = machine();
        return m == null ? new ShaftLine.End(0, 0, PowerPlant.MACHINE_INERTIA) : m.lineEnd(rpm);
    }

    @Override public void settle(double rpm, double givenW, double gotW, double standLoadW) {
        MachineBlockEntity m = machine();
        if (m != null) { m.settle(rpm, givenW, gotW, standLoadW); }
    }

    /** The motor seat's Forge Energy: into its machine's buffer (none when it is not formed or is the pulley seat). */
    public IEnergyStorage energy() {
        return new IEnergyStorage() {
            private IEnergyStorage target() {
                MachineBlockEntity m = electric() ? machine() : null;
                return m == null ? null : m.energy();
            }
            @Override public int receiveEnergy(int max, boolean simulate) { IEnergyStorage t = target(); return t == null ? 0 : t.receiveEnergy(max, simulate); }
            @Override public int extractEnergy(int max, boolean simulate) { return 0; }
            @Override public int getEnergyStored() { IEnergyStorage t = target(); return t == null ? 0 : t.getEnergyStored(); }
            @Override public int getMaxEnergyStored() { IEnergyStorage t = target(); return t == null ? 0 : t.getMaxEnergyStored(); }
            @Override public boolean canExtract() { return false; }
            @Override public boolean canReceive() { return electric(); }
        };
    }
}
