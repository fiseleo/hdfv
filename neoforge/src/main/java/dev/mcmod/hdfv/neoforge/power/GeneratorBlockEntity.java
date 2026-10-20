package dev.mcmod.hdfv.neoforge.power;

import dev.mcmod.hdfv.power.PowerPlant;
import dev.mcmod.hdfv.power.ShaftLine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;

import java.util.Locale;

/**
 * A generator on a line shaft (0.8): PowerPlant.GENERATOR (50 kW, IE3). It asks the line for the power that would
 * fill its buffer this tick (as its electric network draws on it), from 90 % of the line's nominal speed up, and turns
 * what it gets into Forge Energy at its efficiency.
 */
public final class GeneratorBlockEntity extends PowerBlockEntity implements ShaftEnd {
    private final Buffer buffer = new Buffer((int) Math.ceil(2 * fePerTick(PowerPlant.GENERATOR_W)), 0, Integer.MAX_VALUE, this::setChanged);
    private double owedFe, lastW, rpm;

    public GeneratorBlockEntity(BlockPos pos, BlockState s) { super(HdfvPower.GENERATOR_ENTITY.get(), pos, s); }

    public IEnergyStorage energy() { return buffer; }
    public double lastElectricW() { return lastW; }

    @Override public ShaftLine.End lineEnd(double lineRpm) {
        double room = watts(buffer.room() - owedFe);
        double electric = Math.min(Math.max(room, 0), PowerPlant.GENERATOR_W);
        double mech = lineRpm >= 0.9 * PowerPlant.NOMINAL_RPM && electric > 0 ? PowerPlant.GENERATOR.inputForOutput(electric) : 0;
        return new ShaftLine.End(0, mech, PowerPlant.GENERATOR_INERTIA);
    }

    @Override public void settle(double lineRpm, double givenW, double gotW, double standLoadW) {
        rpm = lineRpm;
        lastW = gotW > 0 ? PowerPlant.GENERATOR.outputForInput(gotW) : 0;
        double fe = fePerTick(lastW) + owedFe;
        int whole = (int) Math.floor(fe);
        buffer.add(whole);
        owedFe = fe - whole;
    }

    @Override public void tick() {
        if (level == null) { return; }
        ShaftNetworks.tick(level, worldPosition);
        EnergyNetworks.tick(level, worldPosition);
        dev.mcmod.hdfv.neoforge.sound.SoundSync.report(this, rpm > 5 ? dev.mcmod.hdfv.sound.MachineSound.electricMachine(rpm, lastW, PowerPlant.GENERATOR_W) : null);
    }

    @Override public Component status() {
        return Component.translatable("hdfv.power.generator", String.format(Locale.ROOT, "%.1f", lastW / 1000), String.format(Locale.ROOT, "%.0f", rpm));
    }

    @Override protected void save(CompoundTag tag, HolderLookup.Provider registries) { tag.putInt("fe", buffer.getEnergyStored()); tag.putDouble("owed", owedFe); }
    @Override protected void load(CompoundTag tag, HolderLookup.Provider registries) { buffer.set(tag.getInt("fe")); owedFe = tag.getDouble("owed"); }
}
