package dev.mcmod.hdfv.neoforge.power;

import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.hdfv.design.Bench;
import dev.mcmod.hdfv.fluid.FuelGrades;
import dev.mcmod.hdfv.neoforge.HdfvConfig;
import dev.mcmod.hdfv.neoforge.fluid.HdfvFluidRegistry;
import dev.mcmod.hdfv.neoforge.machine.Workpieces;
import dev.mcmod.hdfv.part.engine.PistonEngineDesign;
import dev.mcmod.hdfv.power.EngineUnit;
import dev.mcmod.hdfv.power.ShaftLine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * An engine stand (0.8): a finished piston engine (the part item) on a bed with a 200 l day tank, geared to the line
 * shaft beside it (power.EngineUnit). Used with the engine it takes it; with a can or bucket it fills its tank (the
 * engine's fuels only); empty-handed it starts or stops it, sneaking it steps the governor (50 to 80 % of the rev
 * limit, while stopped). It burns the engine's fuel flow in work time; it stops when the tank runs dry, and trips if
 * the line stalls it.
 * <p>
 * 0.11: clients see the engine on it (EngineStandRenderer): the part, whether it runs, its speed (to 50 rpm) and its
 * load (to 0.05), sent only when one of them changes, so an engine running steadily sends nothing.
 */
public final class EngineStandBlockEntity extends PowerBlockEntity implements ShaftEnd {
    public enum State { EMPTY, WARMING, STOPPED, STARTING, RUNNING, OUT_OF_FUEL, TRIPPED }

    public static final int TANK_LITRES = 200;
    public static final double[] GOVERNOR = {0.5, 0.6, 0.7, 0.8};

    private ItemStack engine = ItemStack.EMPTY;
    private final FluidTank tank = new FluidTank(TANK_LITRES, this::accepts) {
        @Override protected void onContentsChanged() { setChanged(); }
    };
    private EngineUnit unit;
    private volatile EngineUnit ready;
    private int governor = 2;
    private boolean wantRunning;
    private double pendingLoadW, owedLitres, burntLitres, ratedW;
    private State state = State.EMPTY;
    /** 0.11: the view last sent, and how many were sent (a steady engine sends none: GameTest). */
    private String sentView = "";
    /** The last change sent, "was -> now" (for a GameTest's message). */
    public String lastChange = "";
    private CompoundTag sentTag;
    private int sends;
    /** 0.11, clients: what the server shows; and the crank's angle as drawn (EngineStandRenderer turns it at the shown speed). */
    private boolean shownRunning;
    private double shownRpm, shownLoad;
    public double drawnAngle;
    public long drawnNanos;
    /** Development (StandDevCommands): a load on the crank besides the line's, W (not saved). */
    public double testLoadW;

    public EngineStandBlockEntity(BlockPos pos, BlockState s) { super(HdfvPower.STAND_ENTITY.get(), pos, s); }

    public IFluidHandler tank() { return tank; }
    public State state() { return state; }
    public EngineUnit unit() { return ready; }
    public double burntLitres() { return burntLitres; }
    public ItemStack engine() { return engine; }
    public boolean shownRunning() { return shownRunning; }
    public double shownRpm() { return shownRpm; }
    public double shownLoad() { return shownLoad; }
    public int sends() { return sends; }

    public PistonEngineDesign design() {
        PartDesign d = engine.isEmpty() ? null : Workpieces.partDesign(engine);
        return d instanceof PistonEngineDesign p ? p : null;
    }

    private boolean accepts(FluidStack f) {
        String id = HdfvFluidRegistry.id(f.getFluid());
        PistonEngineDesign d = design();
        return d == null ? FuelGrades.of(id) != null : d.acceptedFluids().contains(id);
    }

    /** Puts an engine on the stand (a piston engine part); whether it took it. */
    public boolean install(ItemStack part) {
        if (!engine.isEmpty() || !(Workpieces.partDesign(part) instanceof PistonEngineDesign)) { return false; }
        engine = part.copyWithCount(1);
        build();
        setChanged();
        return true;
    }

    /** The governed speed for the current step, rpm (to 50 rpm). */
    public double governorRpm() {
        PistonEngineDesign d = design();
        return d == null ? 0 : Math.round(GOVERNOR[governor] * d.ignition().revLimitRpm() / 50) * 50.0;
    }

    private void build() {
        PistonEngineDesign d = design();
        ready = null;
        unit = d == null ? null : new EngineUnit(d, governorRpm());
        if (unit == null) { return; }
        EngineUnit u = unit;
        CompletableFuture.runAsync(() -> {
            u.warmUp();
            double rated = Bench.fullLoad(d).peakPower().powerW();
            if (unit == u) { ratedW = rated; ready = u; }
        });
    }

    /** Waits for the engine's tables (tests). */
    public void awaitReady() {
        if (unit != null && ready == null) {
            unit.warmUp();
            ratedW = Bench.fullLoad(unit.engine).peakPower().powerW();
            ready = unit;
        }
    }

    public void setRunning(boolean on) { wantRunning = on; }

    @Override public boolean onShaft() { return ready != null; }

    @Override public ShaftLine.End lineEnd(double lineRpm) { return new ShaftLine.End(0, 0, 0); }

    @Override public double standRpm() { return ready != null && ready.engaged() ? ready.lineRpm() : Double.NaN; }

    @Override public double standRatedW() { return ratedW; }

    @Override public void settle(double lineRpm, double givenW, double gotW, double standLoadW) { pendingLoadW = standLoadW; }

    @Override public void tick() {
        step();
        publish();
        EngineUnit u = ready;
        dev.mcmod.hdfv.neoforge.sound.SoundSync.report(this, u != null && u.running() ? dev.mcmod.hdfv.sound.MachineSound.Hum.stand(true, viewLoad) : null);
    }

    private void step() {
        if (level == null) { return; }
        EngineUnit u = ready;
        if (engine.isEmpty()) { state = State.EMPTY; return; }
        if (u == null) { state = State.WARMING; return; }
        if (u.tripped() && wantRunning) { wantRunning = false; state = State.TRIPPED; }
        if (wantRunning && tank.isEmpty()) { wantRunning = false; state = State.OUT_OF_FUEL; }
        if (wantRunning != u.running()) { u.setRunning(wantRunning); }
        u.run(ShaftNetworks.TICK_S, pendingLoadW + testLoadW);
        // the fuel it burnt, in work time
        FluidStack f = tank.getFluid();
        if (!f.isEmpty() && u.fuelKgPerS() > 0) {
            double litres = u.fuelKgPerS() * HdfvConfig.workSecondsPerTick() / HdfvFluidRegistry.spec(f.getFluid()).densityKgPerM3() * 1000 + owedLitres;
            int whole = (int) Math.floor(litres);
            int drained = tank.drain(whole, IFluidHandler.FluidAction.EXECUTE).getAmount();
            burntLitres += litres - owedLitres - (whole - drained);
            owedLitres = litres - whole;
            if (drained < whole) { wantRunning = false; u.setRunning(false); state = State.OUT_OF_FUEL; }
        }
        if (state != State.OUT_OF_FUEL && state != State.TRIPPED || wantRunning) {
            state = !u.running() ? State.STOPPED : u.engaged() ? State.RUNNING : State.STARTING;
        }
        ShaftNetworks.tick(level, worldPosition);
        setChanged();
    }

    /** What clients see (0.11): the engine, whether it runs, its speed to 50 rpm, its load (power over rated) to 0.05. */
    private CompoundTag view(HolderLookup.Provider registries) {
        CompoundTag t = new CompoundTag();
        if (!engine.isEmpty()) { t.put("engine", engine.save(registries)); }
        EngineUnit u = ready;
        t.putBoolean("running", u != null && u.running());
        t.putDouble("rpm", viewRpm);
        t.putDouble("load", viewLoad);
        return t;
    }

    /** The speed and load shown, to 50 rpm and 0.05, moved only when the engine's are three quarters of a step away (no flicker across a step). */
    private double viewRpm, viewLoad;

    private void follow() {
        EngineUnit u = ready;
        boolean running = u != null && u.running();
        double rpm = running ? u.engineRpm() : 0, load = running && ratedW > 0 ? Math.clamp(u.powerW() / ratedW, 0, 1.5) : 0;
        if (Math.abs(rpm - viewRpm) > 37.5 || !running) { viewRpm = Math.round(rpm / 50) * 50.0; }
        if (Math.abs(load - viewLoad) > 0.0375 || !running) { viewLoad = Math.round(load / 0.05) * 0.05; }
    }

    /** Sends the view when it has changed. */
    private void publish() {
        if (level == null || level.isClientSide) { return; }
        follow();
        String now = view(level.registryAccess()).toString();
        if (now.equals(sentView)) { return; }
        CompoundTag was = sentTag;
        sentTag = view(level.registryAccess());
        lastChange = was == null ? "first" : "engine " + (was.get("engine") == null ? "none" : was.get("engine").equals(sentTag.get("engine")) ? "same" : "changed")
                + ", running " + was.getBoolean("running") + "/" + sentTag.getBoolean("running") + ", rpm " + was.getDouble("rpm") + "/" + sentTag.getDouble("rpm")
                + ", load " + was.getDouble("load") + "/" + sentTag.getDouble("load");
        sentView = now;
        sends++;
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return view(registries); }

    @Override public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        if (level == null || !level.isClientSide) { return; }
        ItemStack sent = tag.contains("engine") ? ItemStack.parseOptional(registries, tag.getCompound("engine")) : ItemStack.EMPTY;
        if (!ItemStack.isSameItemSameComponents(sent, engine)) { engine = sent; }   // the same engine keeps its stack (its drawing is cached by it)
        shownRunning = tag.getBoolean("running");
        shownRpm = tag.getDouble("rpm");
        shownLoad = tag.getDouble("load");
    }

    @Override public void onDataPacket(net.minecraft.network.Connection net, net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket packet,
                                       HolderLookup.Provider registries) { handleUpdateTag(packet.getTag(), registries); }

    @Override public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override public boolean use(Player player, InteractionHand hand, ItemStack held) {
        if (!held.isEmpty() && install(held)) { held.shrink(1); return true; }
        if (!held.isEmpty() && FluidUtil.getFluidHandler(held).isPresent()) { return FluidUtil.interactWithFluidHandler(player, hand, tank); }
        if (!held.isEmpty()) { return false; }
        if (player.isShiftKeyDown()) {
            if (!wantRunning && (ready == null || !ready.running())) { governor = (governor + 1) % GOVERNOR.length; build(); }
        } else {
            wantRunning = !wantRunning;
            if (wantRunning) { state = State.STOPPED; }
        }
        setChanged();
        return true;
    }

    @Override public Component status() {
        EngineUnit u = ready;
        FluidStack f = tank.getFluid();
        Component fuel = f.isEmpty() ? Component.translatable("hdfv.fluid.empty") : f.getHoverName();
        return Component.translatable("hdfv.power.stand", Component.translatable("hdfv.power.stand.state." + state.name().toLowerCase(Locale.ROOT)),
                String.format(Locale.ROOT, "%.0f", u == null ? 0 : u.engineRpm()), String.format(Locale.ROOT, "%.0f", governorRpm()),
                String.format(Locale.ROOT, "%.1f", u == null ? 0 : u.powerW() / 1000), fuel, f.getAmount());
    }

    @Override protected void save(CompoundTag tag, HolderLookup.Provider registries) {
        if (!engine.isEmpty()) { tag.put("engine", engine.save(registries)); }
        tank.writeToNBT(registries, tag);
        tag.putInt("governor", governor);
        tag.putBoolean("running", wantRunning);
        tag.putDouble("owed", owedLitres);
        tag.putDouble("burnt", burntLitres);
    }

    @Override protected void load(CompoundTag tag, HolderLookup.Provider registries) {
        engine = tag.contains("engine") ? ItemStack.parseOptional(registries, tag.getCompound("engine")) : ItemStack.EMPTY;
        tank.readFromNBT(registries, tag);
        governor = Math.floorMod(tag.getInt("governor"), GOVERNOR.length);
        wantRunning = tag.getBoolean("running");
        owedLitres = tag.getDouble("owed");
        burntLitres = tag.getDouble("burnt");
        build();
    }
}
