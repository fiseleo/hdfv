package dev.mcmod.hdfv.neoforge.fluid;

import dev.mcmod.hdfv.fluid.HdfvFluids;
import dev.mcmod.hdfv.fluid.Piping;
import dev.mcmod.hdfv.neoforge.HdfvConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * An electric pump (0.8): each tick of work (HdfvConfig.workSecondsPerTick) it moves what its duty flow carries
 * (fluid.Piping: the pump's curve against the lift from where it draws to where it delivers and the pipe between) of
 * the first fluid its inlet side can give into whatever on its outlet side takes it, drawing its motor's power from
 * its Forge Energy (a buffer of BUFFER_FE; 1 FE = HdfvConfig.joulesPerFE of work). Fractions of a litre or an FE carry
 * over.
 */
public final class PumpBlockEntity extends BlockEntity {
    public enum State { IDLE, NO_POWER, NO_SOURCE, NO_ROOM, TOO_HIGH, PUMPING }

    public static final int BUFFER_FE = 400;
    /** A buffer filled from outside (its capability takes, never gives); the pump spends it. */
    static final class Buffer extends EnergyStorage {
        private final Runnable changed;
        Buffer(int capacity, Runnable changed) { super(capacity, capacity, 0); this.changed = changed; }
        @Override public int receiveEnergy(int amount, boolean simulate) {
            int r = super.receiveEnergy(amount, simulate);
            if (r > 0 && !simulate) { changed.run(); }
            return r;
        }
        void use(int fe) { energy = Math.max(0, energy - fe); changed.run(); }
    }

    private final Buffer energy = new Buffer(BUFFER_FE, this::setChanged);
    private PipeNetworks.Network in, out;
    private int epochSeen = -1, sinceScan;
    private double litresOwed, feOwed, lastFlow, lastPower;
    private State state = State.IDLE;

    public PumpBlockEntity(BlockPos pos, BlockState s) { super(HdfvFluidRegistry.PUMP_ENTITY.get(), pos, s); }

    public IEnergyStorage energy() { return energy; }
    public State state() { return state; }
    public double lastFlowM3PerS() { return lastFlow; }
    public double lastPowerW() { return lastPower; }

    private void idle(State s) { state = s; lastFlow = 0; lastPower = 0; }

    public void tick() {
        tickWork();
        dev.mcmod.hdfv.neoforge.sound.SoundSync.report(this, hum());
    }

    /** Its motor and the flow while it pumps (0.19). */
    private dev.mcmod.hdfv.sound.MachineSound.Hum hum() { return state == State.PUMPING ? dev.mcmod.hdfv.sound.MachineSound.plant(dev.mcmod.aerocore.api.acoustics.ProcessNoise.Process.PUMP, 1500, 0.8, true) : null; }

    private void tickWork() {
        if (level == null) { return; }
        Direction outlet = getBlockState().getValue(PumpBlock.FACING);
        int epoch = PipeNetworks.epoch(level);
        if (in == null || epoch != epochSeen || ++sinceScan >= PipeNetworks.RESCAN_TICKS) {
            in = PipeNetworks.scan(level, worldPosition, outlet.getOpposite());
            out = PipeNetworks.scan(level, worldPosition, outlet);
            epochSeen = epoch;
            sinceScan = 0;
        }
        List<IFluidHandler> sources = new ArrayList<>(), sinks = new ArrayList<>();
        Set<BlockPos> drawn = new HashSet<>();
        for (PipeNetworks.End e : in.ends()) { IFluidHandler h = e.handler(level); if (h != null) { sources.add(h); drawn.add(e.pos()); } }
        for (PipeNetworks.End e : out.ends()) { IFluidHandler h = e.handler(level); if (h != null && !drawn.contains(e.pos())) { sinks.add(h); } }
        FluidStack fluid = FluidStack.EMPTY;
        for (IFluidHandler h : sources) {
            FluidStack d = h.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.SIMULATE);
            if (!d.isEmpty()) { fluid = d; break; }
        }
        if (fluid.isEmpty()) { idle(State.NO_SOURCE); return; }
        HdfvFluids.Spec spec = HdfvFluidRegistry.spec(fluid.getFluid());
        double flow = Piping.flowM3PerS(spec, in.pipes() + out.pipes() + 1, out.meanY() - in.meanY());
        if (!(flow > 0)) { idle(State.TOO_HIGH); return; }
        double seconds = HdfvConfig.workSecondsPerTick();
        double litres = Piping.litresPerTick(flow, seconds) + litresOwed;
        int want = (int) Math.floor(litres);
        double fe = Piping.pumpW(spec, flow) * seconds / HdfvConfig.joulesPerFE();
        if (energy.getEnergyStored() < Math.ceil(fe + feOwed)) { idle(State.NO_POWER); return; }
        int moved = want > 0 ? move(fluid, want, sources, sinks) : 0;
        if (want > 0 && moved == 0) { idle(State.NO_ROOM); litresOwed = 0; return; }
        double share = want > 0 ? (double) moved / want : 1;
        litresOwed = moved == want ? litres - want : 0;
        double pay = fe * share + feOwed;
        int whole = (int) Math.floor(pay);
        energy.use(whole);
        feOwed = pay - whole;
        state = State.PUMPING;
        lastFlow = flow * share;
        lastPower = Piping.pumpW(spec, flow) * share;
        setChanged();
    }

    /** Moves up to a number of litres of a fluid from the sources into the sinks; what was moved. */
    private static int move(FluidStack fluid, int want, List<IFluidHandler> sources, List<IFluidHandler> sinks) {
        int room = 0, have = 0;
        for (IFluidHandler h : sinks) { if (room < want) { room += h.fill(fluid.copyWithAmount(want - room), IFluidHandler.FluidAction.SIMULATE); } }
        for (IFluidHandler h : sources) { if (have < want) { have += h.drain(fluid.copyWithAmount(want - have), IFluidHandler.FluidAction.SIMULATE).getAmount(); } }
        int n = Math.min(want, Math.min(room, have));
        if (n <= 0) { return 0; }
        int drained = 0;
        for (IFluidHandler h : sources) { if (drained < n) { drained += h.drain(fluid.copyWithAmount(n - drained), IFluidHandler.FluidAction.EXECUTE).getAmount(); } }
        int filled = 0;
        for (IFluidHandler h : sinks) { if (filled < drained) { filled += h.fill(fluid.copyWithAmount(drained - filled), IFluidHandler.FluidAction.EXECUTE); } }
        if (filled < drained) {
            int back = drained - filled;
            for (IFluidHandler h : sources) { if (back > 0) { back -= h.fill(fluid.copyWithAmount(back), IFluidHandler.FluidAction.EXECUTE); } }
        }
        return filled;
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("energy", energy.serializeNBT(registries));
        tag.putDouble("litresOwed", litresOwed);
        tag.putDouble("feOwed", feOwed);
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("energy")) { energy.deserializeNBT(registries, tag.get("energy")); }
        litresOwed = tag.getDouble("litresOwed");
        feOwed = tag.getDouble("feOwed");
    }
}
