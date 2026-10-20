package dev.mcmod.hdfv.neoforge.power;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;

/** A creative power source (0.8): Forge Energy without end, to what touches it and down its cables. */
public final class CreativePowerBlockEntity extends PowerBlockEntity {
    public CreativePowerBlockEntity(BlockPos pos, BlockState s) { super(HdfvPower.CREATIVE_ENTITY.get(), pos, s); }

    private final IEnergyStorage endless = new IEnergyStorage() {
        @Override public int receiveEnergy(int n, boolean simulate) { return 0; }
        @Override public int extractEnergy(int n, boolean simulate) { return Math.max(n, 0); }
        @Override public int getEnergyStored() { return EnergyNetworks.ENDLESS; }
        @Override public int getMaxEnergyStored() { return EnergyNetworks.ENDLESS; }
        @Override public boolean canExtract() { return true; }
        @Override public boolean canReceive() { return false; }
    };

    public IEnergyStorage energy() { return endless; }

    @Override public void tick() { if (level != null) { EnergyNetworks.tick(level, worldPosition); } }

    @Override public Component status() { return Component.translatable("hdfv.power.creative"); }
}
