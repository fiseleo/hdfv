package dev.mcmod.hdfv.neoforge.petroleum;

import dev.mcmod.hdfv.neoforge.HdfvConfig;
import dev.mcmod.hdfv.neoforge.multiblock.ControllerBlockEntity;
import dev.mcmod.hdfv.petroleum.OilFields;
import dev.mcmod.hdfv.petroleum.Oilwell;
import dev.mcmod.hdfv.structure.Pattern;
import dev.mcmod.hdfv.structure.Structures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Locale;

/**
 * A beam pump jack (0.8; structure.Structures.PUMP_JACK_PATTERN): over a wellhead whose field no longer flows by
 * itself, it lifts what its rod pump displaces or the field lets in, whichever is less (Oilwell), on its motor's power
 * for the lift; the crude goes into the wellhead. Clients are told when it runs (its renderer rocks the beam).
 */
public final class PumpJackBlockEntity extends ControllerBlockEntity implements Configurable {
    public enum State { NOT_FORMED, NO_FIELD, IDLE, NO_POWER, FULL, PUMPING, STOPPED }

    /** Its settings (0.9): running, strokes a minute, the stroke's and plunger's sizes (indices into Oilwell.Jack's). */
    private boolean running = true;
    private double spm = Oilwell.STROKES_PER_MIN;
    private int stroke = 1, plunger = 2;

    private State state = State.NOT_FORMED;
    private double lastRate;
    private boolean shownPumping, sentPumping;
    /** 0.12: the strokes a minute the clients draw it at (sent with the pumping when either changes). */
    private double shownSpm = Oilwell.STROKES_PER_MIN, sentSpm = Double.NaN;

    public PumpJackBlockEntity(BlockPos pos, BlockState s) { super(HdfvPetroleum.JACK_ENTITY.get(), pos, s, Structures.PUMP_JACK_PATTERN, 15e3); }

    public State state() { return state; }
    public double lastRateM3PerS() { return lastRate; }
    public boolean shownPumping() { return shownPumping; }
    /** 0.12: its strokes a minute as the client was told. */
    public double shownSpm() { return shownSpm; }

    /** The wellhead it works (null when not formed). */
    public WellheadBlockEntity wellhead() {
        Pattern.Match m = multiblock.match();
        if (m == null || level == null) { return null; }
        for (Pattern.Cell c : m.cells()) {
            if (Structures.WELLHEAD.equals(c.kind()) && level.getBlockEntity(new BlockPos(c.x(), c.y(), c.z())) instanceof WellheadBlockEntity w) { return w; }
        }
        return null;
    }

    @Override protected dev.mcmod.hdfv.sound.MachineSound.Hum hum() { return state == State.PUMPING ? dev.mcmod.hdfv.sound.MachineSound.pumpJack(Math.max(lastW, 3000), spm, true) : null; }

    @Override protected void work(ServerLevel level, boolean formed) {
        lastRate = 0;
        if (!formed) { state = State.NOT_FORMED; sync(); return; }
        WellheadBlockEntity w = wellhead();
        OilFields.Field f = w == null ? null : w.field();
        if (f == null) { state = State.NO_FIELD; sync(); return; }
        if (w.flowing()) { state = State.IDLE; sync(); return; }
        if (!running) { state = State.STOPPED; sync(); return; }
        double produced = OilFieldData.of(level).produced(f.id());
        double rate = Oilwell.jackRateM3PerS(f, produced, jack());
        double share = draw(Oilwell.jackW(f, rate));
        if (share <= 0) { state = State.NO_POWER; sync(); return; }
        int put = w.lift(f, rate * share * HdfvConfig.workSecondsPerTick());
        w.pumped();
        lastRate = rate * share;
        // 0.12: full only when the wellhead has no room (under a litre a tick puts nothing in yet is pumping: the state
        // flickered between FULL and PUMPING, each flip an update to the clients and the cranks stopping)
        state = put == 0 && rate > 0 && w.full() ? State.FULL : State.PUMPING;
        sync();
    }

    /** Its settings as a pump (the nominal jack when they are 0.8's). */
    public Oilwell.Jack jack() {
        boolean nominal = spm == Oilwell.STROKES_PER_MIN && stroke == 1 && plunger == 2;
        return nominal ? Oilwell.Jack.NOMINAL : new Oilwell.Jack(spm, Oilwell.Jack.STROKES[stroke], Oilwell.Jack.PLUNGERS[plunger]);
    }

    @Override public PetroleumMenu.Kind kind() { return PetroleumMenu.Kind.JACK; }

    @Override public Component set(String key, double value) {
        switch (key) {
            case "running" -> running = value > 0.5;
            case "spm" -> {
                if (!(value >= Oilwell.Jack.MIN_SPM && value <= Oilwell.Jack.MAX_SPM)) { return Configurable.outOfRange(key, Oilwell.Jack.MIN_SPM, Oilwell.Jack.MAX_SPM); }
                spm = Math.rint(value);
            }
            case "stroke" -> {
                if (!(value >= 0 && value < Oilwell.Jack.STROKES.length)) { return Configurable.outOfRange(key, 0, Oilwell.Jack.STROKES.length - 1); }
                stroke = (int) value;
            }
            case "plunger" -> {
                if (!(value >= 0 && value < Oilwell.Jack.PLUNGERS.length)) { return Configurable.outOfRange(key, 0, Oilwell.Jack.PLUNGERS.length - 1); }
                plunger = (int) value;
            }
            default -> { return Component.translatable("hdfv.gui.no_setting"); }
        }
        setChanged();
        return null;
    }

    @Override public CompoundTag view() {
        CompoundTag t = new CompoundTag();
        t.putString("State", state.name().toLowerCase(Locale.ROOT));
        t.putBoolean("Running", running);
        t.putDouble("Spm", spm);
        t.putInt("Stroke", stroke);
        t.putInt("Plunger", plunger);
        t.putDouble("Rate", lastRate);
        t.putDouble("Displacement", jack().displacementM3PerS());
        t.putDouble("PowerW", lastW);
        WellheadBlockEntity w = wellhead();
        OilFields.Field f = w == null ? null : w.field();
        if (f != null && level instanceof ServerLevel s) {
            double produced = OilFieldData.of(s).produced(f.id());
            t.putDouble("Inflow", f.pumpedInflow(produced));
            t.putDouble("PressureBar", f.reservoir().pressurePa(produced) / 1e5);
            t.putDouble("Produced", produced);
            t.putDouble("Depth", f.depthM());
        }
        return t;
    }

    @Override protected void save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("Running", running);
        tag.putDouble("Spm", spm);
        tag.putInt("Stroke", stroke);
        tag.putInt("Plunger", plunger);
    }

    @Override protected void load(CompoundTag tag, HolderLookup.Provider registries) {
        running = !tag.contains("Running") || tag.getBoolean("Running");
        spm = tag.contains("Spm") ? Math.clamp(tag.getDouble("Spm"), Oilwell.Jack.MIN_SPM, Oilwell.Jack.MAX_SPM) : Oilwell.STROKES_PER_MIN;
        stroke = tag.contains("Stroke") ? Math.floorMod(tag.getInt("Stroke"), Oilwell.Jack.STROKES.length) : 1;
        plunger = tag.contains("Plunger") ? Math.floorMod(tag.getInt("Plunger"), Oilwell.Jack.PLUNGERS.length) : 2;
    }

    /** Tells the clients when it starts or stops pumping, or (0.12) its strokes a minute change; nothing otherwise. */
    private void sync() {
        boolean on = state == State.PUMPING;
        if ((on != sentPumping || spm != sentSpm) && level != null) {
            sentPumping = on;
            sentSpm = spm;
            updatesSent++;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** 0.12: how many updates it has sent the clients (a test's count). */
    int updatesSent;

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag t = super.getUpdateTag(registries);
        t.putBoolean("Pumping", state == State.PUMPING);
        t.putDouble("Spm", spm);
        return t;
    }

    @Override public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.handleUpdateTag(tag, registries);
        shownPumping = tag.getBoolean("Pumping");
        if (tag.contains("Spm")) { shownSpm = tag.getDouble("Spm"); }
    }

    @Override public Component status() {
        return Component.translatable("hdfv.jack.status", Component.translatable("hdfv.jack.state." + state.name().toLowerCase(Locale.ROOT)),
                String.format(Locale.ROOT, "%.1f", lastRate * 86400));
    }
}
