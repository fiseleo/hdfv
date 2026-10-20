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
 * An electric motor on a line shaft (0.8): PowerPlant.MOTOR (15 kW, IE3), governed to the line's nominal speed. It
 * gives the line what its Forge Energy buffer can pay for this tick, drawing the motor's input for what it gave.
 */
public final class MotorBlockEntity extends PowerBlockEntity implements ShaftEnd {
    private final Buffer buffer = new Buffer((int) Math.ceil(2 * fePerTick(PowerPlant.MOTOR.inputForOutput(PowerPlant.MOTOR_W))), Integer.MAX_VALUE, 0, this::setChanged);
    private double owedFe, lastW, rpm;

    public MotorBlockEntity(BlockPos pos, BlockState s) { super(HdfvPower.MOTOR_ENTITY.get(), pos, s); }

    public IEnergyStorage energy() { return buffer; }
    public double lastShaftW() { return lastW; }

    @Override public ShaftLine.End lineEnd(double lineRpm) {
        double electric = watts(buffer.getEnergyStored() - owedFe);
        double mech = electric > 0 ? Math.min(PowerPlant.MOTOR_W, PowerPlant.MOTOR.outputForInput(electric)) : 0;
        return new ShaftLine.End(mech, 0, PowerPlant.MOTOR_INERTIA);
    }

    @Override public void settle(double lineRpm, double givenW, double gotW, double standLoadW) {
        rpm = lineRpm;
        lastW = givenW;
        double fe = (givenW > 0 ? fePerTick(PowerPlant.MOTOR.inputForOutput(givenW)) : 0) + owedFe;
        int whole = (int) Math.floor(fe);
        buffer.use(whole);
        owedFe = fe - whole;
    }

    @Override public void tick() {
        if (level == null) { return; }
        EnergyNetworks.pull(level, worldPosition, buffer);
        ShaftNetworks.tick(level, worldPosition);
        dev.mcmod.hdfv.neoforge.sound.SoundSync.report(this, rpm > 5 ? dev.mcmod.hdfv.sound.MachineSound.electricMachine(rpm, lastW, PowerPlant.MOTOR_W) : null);
    }

    @Override public Component status() {
        return Component.translatable("hdfv.power.motor", String.format(Locale.ROOT, "%.1f", lastW / 1000), String.format(Locale.ROOT, "%.0f", rpm));
    }

    @Override protected void save(CompoundTag tag, HolderLookup.Provider registries) { tag.putInt("fe", buffer.getEnergyStored()); tag.putDouble("owed", owedFe); }
    @Override protected void load(CompoundTag tag, HolderLookup.Provider registries) { buffer.set(tag.getInt("fe")); owedFe = tag.getDouble("owed"); }
}
