package dev.mcmod.hdfv.neoforge.petroleum;

import dev.mcmod.hdfv.neoforge.HdfvConfig;
import dev.mcmod.hdfv.neoforge.fluid.HdfvFluidRegistry;
import dev.mcmod.hdfv.petroleum.OilFields;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import java.util.Locale;

/**
 * A wellhead (0.8): the top of a well into the field under it. While the field's pressure lifts the oil it flows into
 * the wellhead's 1000 l of storage by itself (its natural rate, in work time); after that it waits for a pump jack.
 * Pipes draw the crude from it (a pump: nothing goes in). Every well into a field shares what the field has given
 * (OilFieldData).
 */
public final class WellheadBlockEntity extends BlockEntity implements Configurable {
    public enum State { NO_FIELD, FLOWING, NEEDS_PUMP, PUMPED, FULL }

    public static final int LITRES = 1000;
    private final FluidTank tank = new FluidTank(LITRES) {
        @Override protected void onContentsChanged() { setChanged(); }
    };
    private double owedL;
    private State state = State.NO_FIELD;
    private long pumpedAt = -100;
    /** Its choke (0.9): an index into CHOKES_64THS (0: none, wide open); the last tick's natural flow, m^3/s. */
    private int choke;
    private double lastRate;
    /** Choke bores, 64ths of an inch (0: no choke). */
    public static final int[] CHOKES_64THS = {0, 8, 12, 16, 24, 32, 48, 64};

    public WellheadBlockEntity(BlockPos pos, BlockState s) { super(HdfvPetroleum.WELLHEAD_ENTITY.get(), pos, s); }

    public State state() { return state; }
    public FluidStack fluid() { return tank.getFluid(); }

    /** What pipes see: crude to draw, nothing to fill. */
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

    /** The field under it (null: none). */
    public OilFields.Field field() { return level instanceof ServerLevel s ? OilFieldData.of(s).field(s, worldPosition) : null; }

    /** Whether it flows by itself now. */
    public boolean flowing() { return state == State.FLOWING; }

    public void tick() {
        tickWork();
        dev.mcmod.hdfv.neoforge.sound.SoundSync.report(this, hum());
    }

    /** A free-flowing well rushes through its choke (0.19). */
    private dev.mcmod.hdfv.sound.MachineSound.Hum hum() { return state == State.FLOWING ? dev.mcmod.hdfv.sound.MachineSound.plant(dev.mcmod.aerocore.api.acoustics.ProcessNoise.Process.PUMP, 2000, 0.5, true) : null; }

    private void tickWork() {
        if (!(level instanceof ServerLevel server)) { return; }
        OilFields.Field f = field();
        if (f == null) { state = State.NO_FIELD; return; }
        OilFieldData data = OilFieldData.of(server);
        double rate = f.chokedRate(chokeBoreM(), data.produced(f.id()));
        lastRate = Math.max(rate, 0);
        if (rate > 0) {
            int got = lift(f, rate * HdfvConfig.workSecondsPerTick());
            state = got == 0 && tank.getFluidAmount() >= LITRES ? State.FULL : State.FLOWING;
        } else {
            state = server.getGameTime() - pumpedAt <= 1 ? State.PUMPED : State.NEEDS_PUMP;
        }
    }

    /** The choke's bore, m (0: none). */
    public double chokeBoreM() { return CHOKES_64THS[choke] * 0.0254 / 64; }

    @Override public PetroleumMenu.Kind kind() { return PetroleumMenu.Kind.WELLHEAD; }

    @Override public Component set(String key, double value) {
        if (!key.equals("choke")) { return Component.translatable("hdfv.gui.no_setting"); }
        if (!(value >= 0 && value < CHOKES_64THS.length)) { return Configurable.outOfRange(key, 0, CHOKES_64THS.length - 1); }
        choke = (int) value;
        setChanged();
        return null;
    }

    @Override public CompoundTag view() {
        CompoundTag t = new CompoundTag();
        t.putString("State", state.name().toLowerCase(Locale.ROOT));
        t.putInt("Choke", choke);
        t.putDouble("Rate", state == State.FLOWING || state == State.FULL ? lastRate : 0);
        FluidStack s = tank.getFluid();
        t.putString("Fluid", s.isEmpty() ? "" : HdfvFluidRegistry.id(s.getFluid()));
        t.putInt("Litres", s.getAmount());
        OilFields.Field f = field();
        if (f != null && level instanceof ServerLevel server) {
            double produced = OilFieldData.of(server).produced(f.id());
            t.putDouble("Open", Math.max(f.naturalRate(produced), 0));
            t.putDouble("PressureBar", f.reservoir().pressurePa(produced) / 1e5);
            t.putDouble("Depth", f.depthM());
        }
        return t;
    }

    private CompoundTag shown = new CompoundTag();
    @Override public CompoundTag shown() { return shown; }
    @Override public void sendView() {
        if (level != null && !level.isClientSide) { level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS); }
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { CompoundTag t = new CompoundTag(); t.put("View", view()); return t; }
    @Override public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) { shown = tag.getCompound("View"); }
    @Override public void onDataPacket(net.minecraft.network.Connection net, net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket packet,
                                       HolderLookup.Provider registries) { handleUpdateTag(packet.getTag(), registries); }
    @Override public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    /** Lifts a volume (m^3) of the field's crude into its storage (what fits), counting it against the field; litres put in. */
    public int lift(OilFields.Field f, double m3) {
        double litres = m3 * 1000 + owedL;
        int whole = (int) Math.floor(litres);
        int put = whole > 0 ? tank.fill(new FluidStack(HdfvFluidRegistry.fluid(f.fluid()), whole), IFluidHandler.FluidAction.EXECUTE) : 0;
        double taken;
        if (put == whole) { owedL = litres - whole; taken = m3 * 1000; }   // all of it came up (a fraction of a litre waits in the pipe)
        else { owedL = 0; taken = put; }                                  // the storage is full: the rest stays in the ground
        if (level instanceof ServerLevel s) { OilFieldData.of(s).add(f.id(), taken / 1000); }
        return put;
    }

    /** 0.12: whether its storage has no room left (a jack's lift of under a litre a tick also puts nothing in: not full). */
    public boolean full() { return tank.getSpace() <= 0; }

    /** A pump jack lifted for it this tick. */
    void pumped() { if (level != null) { pumpedAt = level.getGameTime(); state = State.PUMPED; } }

    public Component status() {
        OilFields.Field f = field();
        FluidStack s = tank.getFluid();
        return Component.translatable("hdfv.wellhead.status", Component.translatable("hdfv.wellhead.state." + state.name().toLowerCase(Locale.ROOT)),
                s.isEmpty() ? Component.translatable("hdfv.fluid.empty") : s.getHoverName(), s.getAmount(),
                f == null ? "-" : String.format(Locale.ROOT, "%.0f", f.depthM()));
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tank.writeToNBT(registries, tag);
        tag.putDouble("OwedL", owedL);
        tag.putInt("Choke", choke);
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        tank.readFromNBT(registries, tag);
        owedL = tag.getDouble("OwedL");
        choke = Math.floorMod(tag.getInt("Choke"), CHOKES_64THS.length);
    }
}
