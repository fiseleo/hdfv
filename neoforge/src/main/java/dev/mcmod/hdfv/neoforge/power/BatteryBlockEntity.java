package dev.mcmod.hdfv.neoforge.power;

import dev.mcmod.aerocore.api.power.Battery;
import dev.mcmod.hdfv.neoforge.HdfvConfig;
import dev.mcmod.hdfv.power.PowerPlant;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;

import java.util.Locale;

/**
 * A battery bank (0.8): PowerPlant.BATTERY, nickel-iron, 50 kWh. It holds chemical energy: charging stores what
 * comes in at its charging efficiency, discharging gives out at its discharging efficiency; at most C/5 either way.
 * Its network draws on it for what the generators lack and charges it from what they have over.
 */
public final class BatteryBlockEntity extends PowerBlockEntity {
    /** Marks a battery's handler (a store) for the networks. */
    public interface Store {}

    private final Battery battery = PowerPlant.BATTERY;
    private double storedJ;
    private long tickOf = Long.MIN_VALUE;
    private int inThisTick, outThisTick;

    public BatteryBlockEntity(BlockPos pos, BlockState s) { super(HdfvPower.BATTERY_ENTITY.get(), pos, s); }

    private int perTick() { return (int) Math.floor(fePerTick(battery.maxPowerW())); }

    private void roll() {
        long now = level == null ? 0 : level.getGameTime();
        if (now != tickOf) { tickOf = now; inThisTick = 0; outThisTick = 0; }
    }

    private final class Handler implements IEnergyStorage, Store {
        @Override public int receiveEnergy(int n, boolean simulate) {
            roll();
            double j = HdfvConfig.joulesPerFE();
            int room = (int) Math.floor((battery.capacityJ() - storedJ) / (j * battery.chargeEfficiency()));
            int take = Math.max(0, Math.min(n, Math.min(room, perTick() - inThisTick)));
            if (!simulate && take > 0) { storedJ += take * j * battery.chargeEfficiency(); inThisTick += take; setChanged(); }
            return take;
        }
        @Override public int extractEnergy(int n, boolean simulate) {
            roll();
            double j = HdfvConfig.joulesPerFE();
            int has = (int) Math.floor(storedJ * battery.dischargeEfficiency() / j);
            int give = Math.max(0, Math.min(n, Math.min(has, perTick() - outThisTick)));
            if (!simulate && give > 0) { storedJ = Math.max(0, storedJ - give * j / battery.dischargeEfficiency()); outThisTick += give; setChanged(); }
            return give;
        }
        @Override public int getEnergyStored() { return (int) Math.floor(storedJ * battery.dischargeEfficiency() / HdfvConfig.joulesPerFE()); }
        @Override public int getMaxEnergyStored() { return (int) Math.floor(battery.capacityJ() * battery.dischargeEfficiency() / HdfvConfig.joulesPerFE()); }
        @Override public boolean canExtract() { return true; }
        @Override public boolean canReceive() { return true; }
    }

    private final Handler handler = new Handler();

    public IEnergyStorage energy() { return handler; }
    public double storedJ() { return storedJ; }
    public void setStoredJ(double j) { storedJ = Math.clamp(j, 0, battery.capacityJ()); setChanged(); }
    public double capacityJ() { return battery.capacityJ(); }

    @Override public void tick() { if (level != null) { EnergyNetworks.tick(level, worldPosition); } }

    @Override public Component status() {
        return Component.translatable("hdfv.power.battery", String.format(Locale.ROOT, "%.1f", storedJ / 3.6e6), String.format(Locale.ROOT, "%.0f", battery.capacityJ() / 3.6e6));
    }

    @Override protected void save(CompoundTag tag, HolderLookup.Provider registries) { tag.putDouble("storedJ", storedJ); }
    @Override protected void load(CompoundTag tag, HolderLookup.Provider registries) { storedJ = tag.getDouble("storedJ"); }
}
