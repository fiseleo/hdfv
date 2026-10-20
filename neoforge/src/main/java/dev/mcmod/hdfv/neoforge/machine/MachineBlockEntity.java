package dev.mcmod.hdfv.neoforge.machine;

import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.hdfv.design.Designs;
import dev.mcmod.hdfv.manufacture.Alloy;
import dev.mcmod.hdfv.manufacture.CncConfig;
import dev.mcmod.hdfv.manufacture.Consumable;
import dev.mcmod.hdfv.manufacture.Head;
import dev.mcmod.hdfv.manufacture.Machining;
import dev.mcmod.hdfv.manufacture.Operation;
import dev.mcmod.hdfv.manufacture.Pace;
import dev.mcmod.hdfv.manufacture.PartPlan;
import dev.mcmod.hdfv.manufacture.Plans;
import dev.mcmod.hdfv.manufacture.Station;
import dev.mcmod.hdfv.manufacture.ToolConfig;
import dev.mcmod.hdfv.manufacture.Wear;
import dev.mcmod.hdfv.manufacture.Workpiece;
import dev.mcmod.hdfv.neoforge.machining.HdfvMachining;
import dev.mcmod.hdfv.neoforge.material.Stock;
import dev.mcmod.hdfv.neoforge.power.Buffer;
import dev.mcmod.hdfv.neoforge.power.EnergyNetworks;
import dev.mcmod.hdfv.neoforge.power.PowerBlockEntity;
import dev.mcmod.hdfv.neoforge.power.ShaftEnd;
import dev.mcmod.hdfv.neoforge.power.ShaftNetworks;
import dev.mcmod.hdfv.neoforge.power.SteamEngineBlockEntity;
import dev.mcmod.hdfv.power.PowerPlant;
import dev.mcmod.hdfv.power.ShaftLine;
import dev.mcmod.hdfv.structure.Structures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A workshop machine (0.7): one station of the manufacturing plan (manufacture.Station), its drawing (a blueprint), its
 * inputs (stock and workpieces) and outputs.
 *
 * <ul>
 * <li>A job advances a workpiece through every consecutive step of its route this station can do (a lathe turns and
 *     then grinds; a CNC does everything it can in one go), or starts a new piece of the drawing's plan whose first step
 *     is done here (a casting at the foundry, a forging or stock at the press, a laminate at the oven, a tyre at the
 *     vulcanizer, timber at the planer), drawing its stock (kg) from the inputs. An assembly bench puts a part together
 *     from all its finished workpieces.</li>
 * <li>Time: the steps' working time (manufacture.Plans.seconds, setup once for a new kind of job) at the game's pace
 *     (an hour of work in a Minecraft hour).</li>
 * <li>Manual stations advance only while someone works them: a player with the machine's screen open within reach (and,
 *     from 0.7's villagers, a welder at a welding station). A CNC runs alone.</li>
 * <li>A CNC (0.8) is the control cabinet of a multiblock (structure.Structures.CNC): it works only while the machine
 *     is formed round it; dissolved, it keeps its work and waits. Its modules make its axes (CncConfig): what it can
 *     cut, its setups and its time (Plans.job).</li>
 * <li>Energy (0.8): a job draws its electricity (Plans.job: the spindle, the furnace, the arc, the base load) a tick
 *     at a time in work time from the machine's Forge Energy buffer, filled by cables and from batteries, generators
 *     or a source touching it. Lathes, mills, planers, boring mills, drill presses and the forging press can instead
 *     be belted to a line shaft (power.ShaftLine); the foundry, composite oven and vulcanizer can burn solid fuel put
 *     in their inputs. Short of power it works at the share it gets; with none it waits (NO_POWER). The assembly
 *     bench needs none.</li>
 * <li>Manual machine tools (0.9): the lathe, mill, planer, boring mill and drill press are each the controller of a
 *     multiblock too (Structures.machine): its size limits the work (TOO_BIG), its accessories what it can do and how
 *     long it takes (ToolConfig, Plans.jobOn; NEEDS_ACCESSORY), and its power comes only by its drive cell: Forge
 *     Energy by the motor seat, the line shaft by the pulley seat (DriveBlockEntity). An operator works it from near any
 *     of its blocks.</li>
 * <li>0.17 (all the machining machines): the families (grinder, finisher, gear cutter, ram machine, saw, electrical
 *     machines, cutting table, threader, riveter) are multiblocks too, their head block making the machine (Head;
 *     none or two kinds: not formed). An NC drive in the drive's cell runs a manual machine without an operator; a
 *     head driven by electricity only waits on a pulley seat (NEEDS_ELECTRIC). What a job uses up (Wear: tools, wheels,
 *     gas...) is drawn from its consumable slots as it works, a share each tick (NO_TOOL when there is none; the
 *     server's consumables option turns it off). The tool grinder regrinds dull tool sets in its inputs. A workpiece
 *     made on an older route moves onto the current one at the matching step when a machine with its drawing
 *     takes it (Workpieces.Data.current).</li>
 * </ul>
 */
public final class MachineBlockEntity extends BlockEntity implements MenuProvider, dev.mcmod.hdfv.neoforge.multiblock.Multiblocks.Holder, ShaftEnd {
    public static final int DRAWING = 0, FIRST_INPUT = 1;
    /** States the screen shows. */
    public enum State { IDLE, NO_DRAWING, BAD_DRAWING, NO_JOB, NO_OPERATOR, NO_MATERIAL, CNC_ONLY, OUTPUT_FULL, WORKING, ROOM_BUILT, MISSING_PARTS, NOT_FORMED,
        NO_POWER, NEEDS_AXES, TOO_BIG, NEEDS_ACCESSORY, NO_TOOL, NEEDS_ELECTRIC,
        /** 0.23: an assembly bench with no drawing whose inputs make none of its recipes. */
        NO_RECIPE }

    public final Station station;
    public final int inputs, outputs, consumables;
    final SimpleContainer items;
    int progress, total, selected, lastKey = -1, lastJob;
    State state = State.NO_DRAWING;
    /** Players working the machine (their screen open within reach). */
    final Set<ServerPlayer> operators = new HashSet<>();
    /** Welders working it (a welding station: welder villagers beside it, counted once a second). */
    int villagers;
    /** Others working it this tick (an assembly room's crew). */
    int extraOperators;
    private int clock;
    /** 0.19: what it sounds like: the step it works on, its power and its head (looked up now and then). */
    private dev.mcmod.hdfv.manufacture.Process soundStep;
    private double soundWatts;
    private Head soundHead;
    private int soundClock;

    /** What the screen sees; the menu sends shorts, so progress and total go as two halves each. */
    final ContainerData data = new ContainerData() {
        @Override public int get(int i) {
            return switch (i) {
                case 0 -> progress & 0xffff; case 1 -> progress >>> 16; case 2 -> total & 0xffff; case 3 -> total >>> 16;
                case 4 -> state.ordinal(); case 5 -> operators(); case 6 -> selected; case 7 -> missing == null ? 0 : missing.ordinal() + 1;
                case 8 -> missingConsumable == null ? 0 : missingConsumable.ordinal() + 1;
                default -> { ToolConfig t = tool(); yield t == null || t.head() == null ? 0 : t.head().ordinal() + 1; }
            };
        }
        @Override public void set(int i, int v) { }
        @Override public int getCount() { return DATA; }
    };
    public static final int DATA = 10;

    public MachineBlockEntity(BlockPos pos, BlockState state) {
        super(HdfvMachines.MACHINE_ENTITY.get(), pos, state);
        this.station = ((MachineBlock) state.getBlock()).station;
        this.inputs = inputsOf(station);
        this.outputs = 2;
        this.consumables = consumablesOf(station);
        this.items = new SimpleContainer(FIRST_INPUT + inputs + outputs + consumables) {
            @Override public void setChanged() { super.setChanged(); MachineBlockEntity.this.setChanged(); }
        };
        var pattern = station == Station.CNC ? Structures.CNC : Structures.machine(station);
        this.multiblock = pattern == null ? null : new dev.mcmod.hdfv.neoforge.multiblock.Multiblock(pattern, pos);
        double rated = station == Station.CNC ? CncConfig.MILL_TURN_12.ratedW() : station.ratedW();
        this.energy = new Buffer((int) Math.ceil(2 * PowerBlockEntity.fePerTick(rated)) + 10, Integer.MAX_VALUE, 0, this::setChanged);
    }

    // ---------------------------------------------------------------- energy (0.8)

    final Buffer energy;
    /** Work done on the current job, in ticks at full power (progress is its whole part). */
    double work;
    private double owedFe, fuelHeatJ, shaftAskW, shaftGotW, lastRate, lineRpm;

    /** Its Forge Energy handler (null for the assembly bench, which needs none). */
    public net.neoforged.neoforge.energy.IEnergyStorage energyHandler() { return needsPower(station) && !ToolConfig.STATIONS.contains(station) ? energy : null; }

    public static boolean needsPower(Station s) { return s != Station.ASSEMBLY_BENCH && s != Station.ASSEMBLY_ROOM; }
    public static boolean shaftDriven(Station s) {
        return s == Station.LATHE || s == Station.MILLING_MACHINE || s == Station.PLANER || s == Station.BORING_MILL || s == Station.DRILL_PRESS || s == Station.FORGING_PRESS;
    }
    public static boolean fuelFired(Station s) { return s == Station.FOUNDRY || s == Station.COMPOSITE_OVEN || s == Station.VULCANIZER; }
    /** Fuel heat over electricity for the same work: the induction furnace's 65 % over a crucible's 25 %; resistance heating's 100 % over a fired oven's 50 %. */
    static double fuelFactor(Station s) {
        return s == Station.FOUNDRY ? 0.65 / PowerPlant.FOUNDRY_FUEL_EFFICIENCY : 1 / PowerPlant.OVEN_FUEL_EFFICIENCY;
    }

    /** A manual machine tool's configuration (0.9; null: not formed, or another station). */
    public ToolConfig tool() {
        return multiblock == null || !multiblock.formed() || !ToolConfig.STATIONS.contains(station) ? null : Structures.toolConfig(station, multiblock.match());
    }

    /** A formed manual machine tool's drive cell (null: none). */
    public BlockPos drivePos() {
        if (multiblock == null || !multiblock.formed() || !ToolConfig.STATIONS.contains(station)) { return null; }
        for (var c : multiblock.match().cells()) { if (c.symbol() == 'D') { return new BlockPos(c.x(), c.y(), c.z()); } }
        return null;
    }

    /** Whether a formed manual machine tool is driven by its motor seat (else by its pulley seat). */
    public boolean electric() { return multiblock != null && multiblock.formed() && Structures.electric(multiblock.match()); }

    /** The CNC its modules make (null: not formed, or modules that do not go together). */
    public CncConfig cnc() { return multiblock == null || !multiblock.formed() ? null : Structures.cncConfig(multiblock.match()); }

    /** The share of the work this tick's power allows (0 to 1), drawing it: Forge Energy first, then the line shaft or fuel. */
    double power(double watts) {
        if (!needsPower(station) || !(watts > 0)) { shaftAskW = 0; lastRate = 1; return 1; }
        double need = PowerBlockEntity.fePerTick(watts);
        // a manual machine tool takes power by its drive only: Forge Energy by the motor seat, the shaft by the pulley seat
        boolean tool = ToolConfig.STATIONS.contains(station);
        BlockPos drive = tool ? drivePos() : null;
        boolean fe = !tool || drive != null && electric();
        if (level != null && fe) { EnergyNetworks.pull(level, tool ? drive : worldPosition, energy); }
        // Forge Energy: the whole tick's (fractions of an FE carried over), or the share there is
        double owed = need + owedFe, rate;
        if (!fe) {
            rate = 0;
        } else if (energy.getEnergyStored() >= Math.ceil(owed - 1e-9)) {
            int whole = (int) Math.floor(owed);
            energy.use(whole);
            owedFe = owed - whole;
            rate = 1;
        } else {
            int have = energy.getEnergyStored();
            energy.use(have);
            owedFe = 0;
            rate = have / need;
        }
        // a line shaft: what it gave for last tick's ask, and the ask for the rest next tick
        ToolConfig cfg = tool ? tool() : null;
        double lineW = PowerPlant.lineDemandW(watts, Math.max(cfg != null ? cfg.powerW() : station.powerW, 750));
        if (station == Station.FORGING_PRESS || tool && drive != null && !electric()) {
            double fromShaft = Math.min(1 - rate, shaftGotW / lineW);
            shaftAskW = lineW * (1 - rate);
            rate += Math.max(0, fromShaft);
        } else { shaftAskW = 0; }
        // a firebox: solid fuel from the inputs for the rest
        if (rate < 1 && fuelFired(station)) {
            double heat = watts * dev.mcmod.hdfv.neoforge.HdfvConfig.workSecondsPerTick() * fuelFactor(station) * (1 - rate);
            while (fuelHeatJ < heat && burnOne()) { }
            double covered = Math.min(fuelHeatJ, heat);
            fuelHeatJ -= covered;
            rate += (1 - rate) * (heat > 0 ? covered / heat : 1);
        }
        lastRate = Math.min(rate, 1);
        return lastRate;
    }

    /** Burns a solid fuel item from the inputs into the firebox's heat; whether there was one. */
    private boolean burnOne() {
        for (int i = FIRST_INPUT; i < FIRST_INPUT + inputs; i++) {
            ItemStack st = items.getItem(i);
            PowerPlant.FuelItem f = st.isEmpty() ? null : SteamEngineBlockEntity.fuelOf(st);
            if (f != null) { st.shrink(1); fuelHeatJ += f.heatJ(); setChanged(); return true; }
        }
        return false;
    }

    /** On the line itself: the forging press (a manual machine tool is on it by its pulley seat). */
    @Override public boolean onShaft() { return station == Station.FORGING_PRESS; }

    @Override public ShaftLine.End lineEnd(double rpm) { return new ShaftLine.End(0, shaftAskW * PowerPlant.speedFactor(rpm), PowerPlant.MACHINE_INERTIA); }

    @Override public void settle(double rpm, double givenW, double gotW, double standLoadW) { lineRpm = rpm; shaftGotW = gotW; }

    // ---------------------------------------------------------------- the client's view (0.8: the CNC's renderer)

    private boolean shownWorking, sentWorking;

    /** Whether the server says it is working (the client's copy). */
    public boolean shownWorking() { return shownWorking; }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag t = new CompoundTag();
        t.putBoolean("working", state == State.WORKING);
        return t;
    }

    @Override public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) { shownWorking = tag.getBoolean("working"); }

    @Override public void onDataPacket(net.minecraft.network.Connection net, net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket packet,
                                       HolderLookup.Provider registries) {
        shownWorking = packet.getTag().getBoolean("working");
    }

    /** Tells the clients when it starts or stops working (a CNC's renderer animates it). */
    private void syncWorking() {
        boolean w = state == State.WORKING;
        if (multiblock != null && w != sentWorking && level != null && !level.isClientSide) {
            sentWorking = w;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
    }

    /** The CNC its blocks make, seen from anywhere (the client's screen): the pattern matched round a cabinet. */
    public static CncConfig cncAt(Level level, net.minecraft.core.BlockPos pos) {
        var s = level.getBlockState(pos);
        int facing = s.hasProperty(MachineBlock.FACING) ? s.getValue(MachineBlock.FACING).get2DDataValue() : 0;
        var m = Structures.CNC.matchAnyFacing(dev.mcmod.hdfv.neoforge.multiblock.Multiblocks.world(level), pos.getX(), pos.getY(), pos.getZ(), facing);
        return m == null ? null : Structures.cncConfig(m);
    }

    /** A manual machine tool's blocks, seen from anywhere (the client's screen and renderer): its pattern matched round its controller. */
    public static dev.mcmod.hdfv.structure.Pattern.Match machineAt(Level level, net.minecraft.core.BlockPos pos, Station s) {
        var p = Structures.machine(s);
        if (p == null) { return null; }
        var bs = level.getBlockState(pos);
        int facing = bs.hasProperty(MachineBlock.FACING) ? bs.getValue(MachineBlock.FACING).get2DDataValue() : 0;
        return p.matchAnyFacing(dev.mcmod.hdfv.neoforge.multiblock.Multiblocks.world(level), pos.getX(), pos.getY(), pos.getZ(), facing);
    }

    /** The last tick's share of full power, and the line's speed it is belted to. */
    public double lastRate() { return lastRate; }
    public double lineRpm() { return lineRpm; }
    public Buffer energy() { return energy; }

    /** The multiblock it controls (CNC; 0.9 the manual machine tools; null for other stations). */
    final dev.mcmod.hdfv.neoforge.multiblock.Multiblock multiblock;
    @Override public dev.mcmod.hdfv.neoforge.multiblock.Multiblock multiblock() { return multiblock; }

    /** Whether it can work: formed, for a CNC or a manual machine tool. */
    public boolean formed() { return multiblock == null || multiblock.formed(); }

    /** For tests in other packages: its items, state and progress, and a tick. */
    public SimpleContainer items() { return items; }
    public State state() { return state; }
    public int progress() { return progress; }
    public void tickForTest() { tick(); }

    public static int inputsOf(Station s) { return s == Station.ASSEMBLY_BENCH ? 18 : 3; }
    public int firstOutput() { return FIRST_INPUT + inputs; }
    /** Consumable slots (0.17, after the outputs): a CNC's tool magazine four, a cutting machine's or welding station's two. */
    public static int consumablesOf(Station s) { return s == Station.CNC ? 4 : java.util.Arrays.asList(Station.CUTTING).contains(s) || s == Station.WELDING_STATION ? 2 : 0; }
    public int firstConsumable() { return firstOutput() + outputs; }

    public int operators() { return operators.size() + villagers + extraOperators; }

    /** Welder villagers awake within three blocks of a welding station. */
    int welders() {
        if (station != Station.WELDING_STATION || level == null) { return 0; }
        var prof = dev.mcmod.hdfv.neoforge.villager.HdfvVillagers.WELDER.get();
        return level.getEntitiesOfClass(net.minecraft.world.entity.npc.Villager.class, new net.minecraft.world.phys.AABB(worldPosition).inflate(3),
                v -> v.isAlive() && !v.isSleeping() && !v.isBaby() && v.getVillagerData().getProfession() == prof).size();
    }

    /** What hoppers and pipes see: the inputs take anything but drawings, the outputs give. */
    public net.neoforged.neoforge.items.IItemHandler handler() { return new MachineItems(this); }

    /** The pieces of a plan a station starts: those whose route begins with a step it does. */
    public static List<Workpiece> startable(Station station, PartPlan plan) {
        List<Workpiece> out = new ArrayList<>();
        for (Workpiece w : plan.workpieces()) {
            Operation first = w.route().isEmpty() ? null : w.route().get(0);
            if (first != null && doable(station, first)) { out.add(w); }
        }
        return out;
    }

    /** Where a drawing's work goes next (stations in order), and whether that is where new pieces start. */
    public record Next(java.util.Set<Station> stations, boolean starting) {}

    /**
     * Where a drawing's work goes when a machine has none of it (0.10, for its screen): the next step of each piece of
     * the drawing in the inputs, the assembly bench when they are all finished, else the stations its pieces start at
     * (their primary forming: foundry, forging press, composite oven, vulcanizer). The machine itself is left out.
     */
    public static Next whereNext(Station here, PartPlan plan, String hash, List<ItemStack> inputs) {
        java.util.Set<Station> next = new java.util.LinkedHashSet<>();
        boolean any = false, allDone = true;
        for (ItemStack s : inputs) {
            Workpieces.Data data = Workpieces.data(s);
            if (data == null || !data.hash().equals(hash)) { continue; }
            any = true;
            if (data.finished()) { continue; }
            allDone = false;
            Workpiece w = Workpieces.piece(plan, data.key());
            data = data.current(w);
            if (w == null || data.step() >= w.route().size()) { continue; }
            addDoers(next, here, w.route().get(data.step()));
        }
        if (any && allDone && here != Station.ASSEMBLY_BENCH) { next.add(Station.ASSEMBLY_BENCH); }
        if (!next.isEmpty()) { return new Next(next, false); }
        for (Workpiece w : plan.workpieces()) { if (!w.route().isEmpty()) { addDoers(next, here, w.route().get(0)); } }
        return new Next(next, true);
    }

    private static void addDoers(java.util.Set<Station> out, Station here, Operation op) {
        for (Station s : Station.values()) { if (s != here && s != Station.ASSEMBLY_ROOM && doable(s, op)) { out.add(s); } }
    }

    /** Whether a station does a step: a cut to its grade (and a complex one only on a CNC), or a process it has. */
    public static boolean doable(Station station, Operation op) { return op.process().cutting() ? station.can(op) : station.does(op.process()); }

    boolean doable(Operation op, Workpiece w) {
        if (station == Station.CNC && op.process().cutting()) { CncConfig c = cnc(); return c != null && c.can(op); }
        ToolConfig t = tool();
        if (t != null && op.process().cutting()) { return t.fits(w) && t.can(op, w); }
        return doable(station, op);
    }

    /** Whether a CNC lacks the axes (or a turning spindle) for a step a fuller one would cut. */
    boolean lacksAxes(Operation op) {
        if (station != Station.CNC || !op.process().cutting() || !Station.CNC.can(op)) { return false; }
        CncConfig c = cnc();
        return c == null || !c.can(op);
    }

    /** The consecutive steps from one this station does: their count. */
    int run(Workpiece w, int from) {
        int n = 0;
        while (from + n < w.route().size() && doable(w.route().get(from + n), w)) { n++; }
        return n;
    }

    /** Steps from..from+n of a workpiece, one piece (its batch's preparation too when it is a new kind of job): ticks and mean electric power (W). */
    Job job(int slot, Workpiece w, int from, int n) {
        ToolConfig t = tool();
        Plans.Job pj = t != null ? Plans.jobOn(w, from, n, t) : Plans.job(w, from, n, station, station == Station.CNC ? cnc() : null);
        boolean fresh = w.key().hashCode() != lastKey;
        // 0.23: plain carbon tool steel cuts at 0.4 of high-speed steel's speed: its cutting steps take that much longer
        double slow = cutsWithTools(w, from, n) && toolMaterial() == Wear.Tool.CARBON ? Wear.timeFactor(Wear.Tool.CARBON) : 1;
        double seconds = pj.pieceS() * slow + (fresh ? pj.prepareS() : 0), joules = pj.pieceJ() + (fresh ? pj.prepareJ() : 0);
        long ticks = Pace.ticks(seconds, dev.mcmod.hdfv.neoforge.HdfvConfig.ticksPerWorkHour());
        return new Job(slot, w, from, n, ticks, w.key().hashCode(), seconds > 0 ? joules / seconds : 0);
    }

    /** Whether steps from..from+n of a workpiece wear cutting tools here (0.23: carbon steel tools slow them). */
    boolean cutsWithTools(Workpiece w, int from, int n) {
        ToolConfig t = tool();
        Head h = t == null ? null : t.head();
        for (int i = from; i < from + n && i < w.route().size(); i++) {
            if (Wear.consumable(w.route().get(i), station, h) == Consumable.CUTTING_TOOLS) { return true; }
        }
        return false;
    }

    /** What the machine would do now. */
    record Job(int slot, Workpiece piece, int from, int steps, long ticks, int key, double watts) {}

    /** Whether the last look for a job found a workpiece whose next step is this machine's kind of cut but too fine for it. */
    boolean tooFine;
    /** Whether it found one its CNC lacks the axes for. */
    boolean axesShort;
    /** Whether it found one too big for this manual machine tool, and the accessory it lacked for one (0.9). */
    boolean tooBig;
    ToolConfig.Accessory missing;

    /** Notes why a manual machine tool cannot do a workpiece's step it does the kind of (too big, an accessory lacking). */
    private boolean refuses(Workpiece w, Operation op) {
        ToolConfig t = tool();
        if (t == null || !op.process().cutting() || !station.does(op.process())) { return false; }
        if (!t.fits(w)) { tooBig = true; return true; }
        ToolConfig.Accessory a = t.lacks(op, w);
        if (a != null) { missing = a; return true; }
        return false;
    }

    Job job(PartDesign d, PartPlan plan, String hash) {
        tooFine = false;
        axesShort = false;
        tooBig = false;
        missing = null;
        migrate(plan, hash);
        // advance a workpiece of this drawing in the inputs
        for (int i = FIRST_INPUT; i < FIRST_INPUT + inputs; i++) {
            Workpieces.Data data = Workpieces.data(items.getItem(i));
            if (data == null || !data.hash().equals(hash) || data.finished()) { continue; }
            Workpiece w = Workpieces.piece(plan, data.key());
            if (w == null) { continue; }
            int n = run(w, data.step());
            if (n > 0) { return job(i, w, data.step(), n); }
            Operation next = w.route().get(data.step());
            if (refuses(w, next)) { continue; }
            tooFine |= next.process().cutting() && station.does(next.process()) && Station.CNC.can(next) && station != Station.CNC;
            axesShort |= lacksAxes(next);
        }
        if (tooFine || axesShort || tooBig || missing != null) { return null; }
        // start a new piece of the selected kind
        List<Workpiece> start = new ArrayList<>();
        for (Workpiece w : startable(station, plan)) {
            if (w.route().isEmpty()) { continue; }
            if (doable(w.route().get(0), w)) { start.add(w); } else { refuses(w, w.route().get(0)); }
        }
        if (start.isEmpty() && (tooBig || missing != null)) { return null; }
        if (!start.isEmpty()) {
            Workpiece w = start.get(Math.floorMod(selected, start.size()));
            int n = run(w, 0);
            return job(-1, w, 0, n);
        }
        return null;
    }

    long setup(Workpiece w) { return w.key().hashCode() == lastKey ? 0 : Pace.ticks(station.setupS, dev.mcmod.hdfv.neoforge.HdfvConfig.ticksPerWorkHour()); }

    /** Stock of an alloy in the inputs, kg. */
    double stockKg(Alloy a) {
        double kg = 0;
        for (int i = FIRST_INPUT; i < FIRST_INPUT + inputs; i++) { if (Stock.alloy(items.getItem(i)) == a) { kg += Stock.kg(items.getItem(i)); } }
        return kg;
    }

    /** Takes a mass of an alloy's stock from the inputs (bits first, then whole stock, the rest back as bits). */
    boolean takeStock(Alloy a, double kg) {
        int bits = Pace.nuggets(kg, dev.mcmod.hdfv.neoforge.HdfvConfig.kgPerIngot());
        if (stockKg(a) < bits * dev.mcmod.hdfv.neoforge.HdfvConfig.kgPerIngot() / 10 - 1e-9) { return false; }
        for (int i = FIRST_INPUT; i < FIRST_INPUT + inputs && bits > 0; i++) {
            ItemStack s = items.getItem(i);
            if (s.is(dev.mcmod.hdfv.neoforge.material.HdfvMaterials.STOCK_BIT.get()) && Stock.alloy(s) == a) { int t = Math.min(bits, s.getCount()); s.shrink(t); bits -= t; }
        }
        for (int i = FIRST_INPUT; i < FIRST_INPUT + inputs && bits > 0; i++) {
            ItemStack s = items.getItem(i);
            while (bits > 0 && s.is(dev.mcmod.hdfv.neoforge.material.HdfvMaterials.STOCK.get()) && Stock.alloy(s) == a && !s.isEmpty()) {
                s.shrink(1);
                int left = 10 - bits;
                bits = Math.max(bits - 10, 0);
                if (left > 0) { put(Stock.bits(a, left)); }
            }
        }
        return true;
    }

    /** The bits whole stock leaves over when a mass of an alloy is taken (bits first, then whole stock: takeStock). */
    int leftoverBits(Alloy a, double kg) {
        int bits = Pace.nuggets(kg, dev.mcmod.hdfv.neoforge.HdfvConfig.kgPerIngot()), loose = 0;
        for (int i = FIRST_INPUT; i < FIRST_INPUT + inputs; i++) {
            ItemStack s = items.getItem(i);
            if (s.is(dev.mcmod.hdfv.neoforge.material.HdfvMaterials.STOCK_BIT.get()) && Stock.alloy(s) == a) { loose += s.getCount(); }
        }
        int lacking = Math.max(bits - loose, 0);
        return lacking == 0 ? 0 : (10 - lacking % 10) % 10;
    }

    /** Chips (0.23): metal (kg) owed back by alloy, carried between pieces. */
    final java.util.Map<Alloy, Double> chipsOwed = new java.util.EnumMap<>(Alloy.class);

    /** The whole stock bits a job's steps give back (taken off what is owed when take). */
    int chipBits(Job job, boolean take) {
        Workpiece w = job.piece();
        double kg = 0;
        for (int i = job.from(); i < job.from() + job.steps() && i < w.route().size(); i++) {
            kg += dev.mcmod.hdfv.manufacture.Chips.kg(w.route().get(i), w, dev.mcmod.hdfv.neoforge.HdfvConfig.chipRecovery());
        }
        double bitKg = dev.mcmod.hdfv.neoforge.HdfvConfig.kgPerIngot() / 10, owed = chipsOwed.getOrDefault(w.alloy(), 0.0) + kg;
        int bits = (int) Math.floor(owed / bitKg + 1e-9);
        if (take) {
            double rest = owed - bits * bitKg;
            if (rest > 1e-9) { chipsOwed.put(w.alloy(), rest); } else { chipsOwed.remove(w.alloy()); }
        }
        return bits;
    }

    /** Whether a stack and some more would all fit in the outputs at once. */
    boolean fitsAll(ItemStack first, List<ItemStack> more) {
        List<ItemStack> outs = new ArrayList<>();
        for (int i = firstOutput(); i < firstOutput() + outputs; i++) { outs.add(items.getItem(i).copy()); }
        List<ItemStack> all = new ArrayList<>();
        all.add(first);
        all.addAll(more);
        for (ItemStack add : all) {
            boolean placed = false;
            for (int i = 0; i < outs.size() && !placed; i++) {
                ItemStack s = outs.get(i);
                if (s.isEmpty()) { outs.set(i, add.copy()); placed = true; }
                else if (ItemStack.isSameItemSameComponents(s, add) && s.getCount() + add.getCount() <= s.getMaxStackSize()) {
                    s.grow(add.getCount());
                    placed = true;
                }
            }
            if (!placed) { return false; }
        }
        return true;
    }

    /** Puts a stack into the outputs (false when they are full). */
    boolean put(ItemStack add) {
        for (int i = firstOutput(); i < firstOutput() + outputs; i++) {
            ItemStack s = items.getItem(i);
            if (s.isEmpty()) { items.setItem(i, add.copy()); return true; }
            if (ItemStack.isSameItemSameComponents(s, add) && s.getCount() + add.getCount() <= s.getMaxStackSize()) { s.grow(add.getCount()); return true; }
        }
        return false;
    }

    boolean fits(ItemStack add) {
        for (int i = firstOutput(); i < firstOutput() + outputs; i++) {
            ItemStack s = items.getItem(i);
            if (s.isEmpty() || (ItemStack.isSameItemSameComponents(s, add) && s.getCount() + add.getCount() <= s.getMaxStackSize())) { return true; }
        }
        return false;
    }

    public static void tick(Level level, BlockPos pos, BlockState bs, MachineBlockEntity be) { be.tick(); }

    void tick() {
        tickWork();
        syncWorking();
        sound();
    }

    /** Tells the players near what it sounds like (0.19): while working, its step's process on its head at the share of power it gets. */
    private void sound() {
        if (level == null || level.isClientSide) { return; }
        if (state != State.WORKING) { dev.mcmod.hdfv.neoforge.sound.SoundSync.report(this, null); soundClock = 0; return; }
        if (soundClock++ % 40 == 0) { ToolConfig t = tool(); soundHead = t == null ? null : t.head(); }
        dev.mcmod.hdfv.neoforge.sound.SoundSync.report(this, dev.mcmod.hdfv.sound.MachineSound.machine(station, soundHead, soundStep, soundWatts, lastRate, true));
    }

    /** The step of a job being worked now (by its progress), or null. */
    private dev.mcmod.hdfv.manufacture.Process stepOf(Job job) {
        List<Operation> route = job.piece().route();
        if (route.isEmpty()) { return null; }
        int k = job.from() + (int) Math.floor(job.steps() * (double) progress / Math.max(1, total));
        return route.get(Math.clamp(k, 0, route.size() - 1)).process();
    }

    private void tickWork() {
        if (clock++ % 20 == 0) { villagers = welders(); }
        if (multiblock != null && level instanceof net.minecraft.server.level.ServerLevel server) {
            multiblock.tick(server);
            showFormed();
            if (!multiblock.formed() || station == Station.CNC && cnc() == null || station.family && tool() == null) { state = State.NOT_FORMED; extraOperators = 0; return; }
            ToolConfig cfg = tool();
            if (cfg != null && cfg.head() != null && cfg.head().electricOnly && !electric()) { idle(State.NEEDS_ELECTRIC); extraOperators = 0; return; }
        }
        if (level != null && station == Station.FORGING_PRESS) { ShaftNetworks.tick(level, worldPosition); }
        if (level != null && ToolConfig.STATIONS.contains(station) && !electric()) {
            BlockPos d = drivePos();
            if (d != null) { ShaftNetworks.tick(level, d); }
        }
        shaftAskW = 0;
        operators.removeIf(p -> p.isRemoved() || !(p.containerMenu instanceof MachineMenu m) || m.machine != this || !near(p));
        if (regrind()) { extraOperators = 0; return; }
        if (station == Station.ASSEMBLY_BENCH && items.getItem(DRAWING).isEmpty()) { assembleRecipe(); extraOperators = 0; return; }
        Workpieces.Drawing drawing = Workpieces.drawingOf(items.getItem(DRAWING));
        if (drawing == null) { idle(items.getItem(DRAWING).isEmpty() ? State.NO_DRAWING : State.BAD_DRAWING); extraOperators = 0; return; }
        PartDesign d = drawing.design();
        PartPlan plan = drawing.plan();
        String hash = drawing.hash();
        if (station == Station.ASSEMBLY_BENCH) { assemble(d, plan, hash); extraOperators = 0; return; }
        Job job = job(d, plan, hash);
        if (job == null) {
            idle(tooBig ? State.TOO_BIG : missing != null ? State.NEEDS_ACCESSORY : tooFine ? State.CNC_ONLY : axesShort ? State.NEEDS_AXES : State.NO_JOB);
            extraOperators = 0;
            return;
        }
        if (job.slot() < 0 && stockKg(job.piece().alloy()) < job.piece().stockKg() - 1e-9) { idle(State.NO_MATERIAL); extraOperators = 0; return; }
        Workpieces.Data done = new Workpieces.Data(hash, d.name(), d.type(), job.piece().key(), job.from() + job.steps(), job.piece().route().size());
        ItemStack result = new ItemStack(HdfvMachines.WORKPIECE.get());
        result.set(HdfvMachines.WORKPIECE_DATA.get(), done);
        // 0.23: a standard component's last step hands out the component
        if (d instanceof dev.mcmod.hdfv.part.component.ComponentDesign c && done.finished()) {
            result = dev.mcmod.hdfv.neoforge.material.HdfvComponents.stack(c.kind(), 1);
        }
        // 0.23: the stock left over and the chips go to the outputs too: all of it must fit before it starts (0.17 lost what did not)
        List<ItemStack> also = new ArrayList<>();
        if (job.slot() < 0) {
            int left = leftoverBits(job.piece().alloy(), job.piece().stockKg());
            if (left > 0) { also.add(Stock.bits(job.piece().alloy(), left)); }
        }
        int chips = chipBits(job, false);
        if (chips > 0) { also.add(Stock.bits(job.piece().alloy(), chips)); }
        if (!fitsAll(result, also)) { idle(State.OUTPUT_FULL); extraOperators = 0; return; }
        int sig = 31 * job.key() + 997 * job.slot() + job.from();
        if (sig != lastJob) { lastJob = sig; progress = 0; work = 0; }
        total = (int) Math.min(job.ticks(), Integer.MAX_VALUE);
        java.util.Map<Consumable, Double> use = wearOf(job, sig);
        missingConsumable = lacking(use, job.piece());
        if (missingConsumable != null) { state = State.NO_TOOL; extraOperators = 0; setChanged(); return; }
        if (needsOperator() && operators() == 0) { state = State.NO_OPERATOR; extraOperators = 0; setChanged(); return; }
        extraOperators = 0;
        double rate = power(job.watts());
        if (!(rate > 1e-9)) { state = State.NO_POWER; setChanged(); return; }
        state = State.WORKING;
        soundStep = stepOf(job);
        soundWatts = job.watts();
        work += rate;
        charge(use, job.piece(), total > 0 ? rate / total : 0);
        progress = (int) Math.floor(work + 1e-9);
        if (progress < total) { setChanged(); return; }
        if (job.slot() < 0) { takeStock(job.piece().alloy(), job.piece().stockKg()); }
        else { items.getItem(job.slot()).shrink(1); }
        put(result);
        int back = chipBits(job, true);
        if (back > 0) { put(Stock.bits(job.piece().alloy(), back)); }
        lastKey = job.key();
        progress = 0;
        work = 0;
        setChanged();
    }

    /** A manual machine tool's controller shows whether it is formed (its block then draws nothing: the renderer draws the machine). */
    private void showFormed() {
        if (!ToolConfig.STATIONS.contains(station) || level == null) { return; }
        BlockState s = getBlockState();
        boolean f = multiblock.formed();
        if (s.hasProperty(MachineBlock.FORMED) && s.getValue(MachineBlock.FORMED) != f) {
            level.setBlock(worldPosition, s.setValue(MachineBlock.FORMED, f), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
    }

    /** Whether a player is within 6 blocks of the machine (any of its blocks, when it is a formed multiblock). */
    boolean near(Player p) {
        if (p.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) <= 36) { return true; }
        if (multiblock == null || !multiblock.formed()) { return false; }
        for (var c : multiblock.match().cells()) { if (p.distanceToSqr(c.x() + 0.5, c.y() + 0.5, c.z() + 0.5) <= 36) { return true; } }
        return false;
    }

    /** An assembly bench: every finished workpiece of the plan in the inputs, then the part. */
    void assemble(PartDesign d, PartPlan plan, String hash) {
        if (plan.roomBuilt()) { idle(State.ROOM_BUILT); return; }
        for (Workpiece w : plan.workpieces()) {
            if (count(hash, w.key()) < w.count()) { idle(State.MISSING_PARTS); return; }
        }
        ItemStack part = Workpieces.part(d);
        if (!fits(part)) { idle(State.OUTPUT_FULL); return; }
        total = (int) Pace.ticks(plan.assembleS(), dev.mcmod.hdfv.neoforge.HdfvConfig.ticksPerWorkHour());
        if (operators() == 0) { state = State.NO_OPERATOR; setChanged(); return; }
        state = State.WORKING;
        soundStep = dev.mcmod.hdfv.manufacture.Process.ASSEMBLE;
        soundWatts = 1500;
        if (++progress < total) { setChanged(); return; }
        for (Workpiece w : plan.workpieces()) { take(hash, w.key(), w.count()); }
        put(part);
        progress = 0;
        setChanged();
    }

    /** 0.23: an assembly bench's inputs as an assembling recipe's input. */
    public dev.mcmod.hdfv.neoforge.material.AssemblingRecipe.Input recipeInput() {
        List<ItemStack> in = new ArrayList<>();
        for (int i = FIRST_INPUT; i < FIRST_INPUT + inputs; i++) { in.add(items.getItem(i)); }
        return new dev.mcmod.hdfv.neoforge.material.AssemblingRecipe.Input(in);
    }

    /**
     * The assembling recipes a bench's inputs make (the selected one is what it builds): the one taking the most parts
     * first (the parts of a lathe also make a tailstock: the lathe is meant), then by id.
     */
    public static List<net.minecraft.world.item.crafting.RecipeHolder<dev.mcmod.hdfv.neoforge.material.AssemblingRecipe>> recipes(
            net.minecraft.world.level.Level level, dev.mcmod.hdfv.neoforge.material.AssemblingRecipe.Input in) {
        List<net.minecraft.world.item.crafting.RecipeHolder<dev.mcmod.hdfv.neoforge.material.AssemblingRecipe>> out = new ArrayList<>(
                level.getRecipeManager().getRecipesFor(dev.mcmod.hdfv.neoforge.material.HdfvMaterials.ASSEMBLING.get(), in, level));
        out.sort(java.util.Comparator.<net.minecraft.world.item.crafting.RecipeHolder<dev.mcmod.hdfv.neoforge.material.AssemblingRecipe>>comparingInt(
                r -> -r.value().ingredients().stream().mapToInt(net.neoforged.neoforge.common.crafting.SizedIngredient::count).sum())
                .thenComparing(r -> r.id().toString()));
        return out;
    }

    /**
     * 0.23: with no drawing an assembly bench builds by its recipes (machines from standard components): the selected
     * one of those its inputs make, over the recipe's fitting time, with a fitter at it (no power, as the parts).
     */
    void assembleRecipe() {
        if (level == null) { return; }
        var input = recipeInput();
        var found = recipes(level, input);
        if (found.isEmpty()) { idle(State.NO_RECIPE); return; }
        var r = found.get(Math.floorMod(selected, found.size()));
        ItemStack out = r.value().result().copy();
        if (!fits(out)) { idle(State.OUTPUT_FULL); return; }
        int sig = r.id().hashCode();
        if (sig != lastJob) { lastJob = sig; progress = 0; work = 0; }
        total = (int) Pace.ticks(r.value().seconds(), dev.mcmod.hdfv.neoforge.HdfvConfig.ticksPerWorkHour());
        if (operators() == 0) { state = State.NO_OPERATOR; setChanged(); return; }
        state = State.WORKING;
        soundStep = dev.mcmod.hdfv.manufacture.Process.ASSEMBLE;
        soundWatts = 1500;
        if (++progress < total) { setChanged(); return; }
        int[] take = r.value().takes(input);
        if (take == null) { progress = 0; setChanged(); return; }
        for (int i = 0; i < take.length; i++) { if (take[i] > 0) { items.getItem(FIRST_INPUT + i).shrink(take[i]); } }
        put(out);
        progress = 0;
        setChanged();
    }

    int count(String hash, String key) {
        int n = 0;
        for (int i = FIRST_INPUT; i < FIRST_INPUT + inputs; i++) {
            Workpieces.Data data = Workpieces.data(items.getItem(i));
            if (data != null && data.hash().equals(hash) && data.key().equals(key) && data.finished()) { n += items.getItem(i).getCount(); }
        }
        return n;
    }

    void take(String hash, String key, int n) {
        for (int i = FIRST_INPUT; i < FIRST_INPUT + inputs && n > 0; i++) {
            Workpieces.Data data = Workpieces.data(items.getItem(i));
            if (data != null && data.hash().equals(hash) && data.key().equals(key) && data.finished()) {
                int t = Math.min(n, items.getItem(i).getCount());
                items.getItem(i).shrink(t);
                n -= t;
            }
        }
    }

    private void idle(State s) {
        if (state != s || progress != 0 || total != 0) { state = s; progress = 0; work = 0; total = 0; setChanged(); }
    }

    /** The menu's buttons: 0 previous, 1 next piece to start. */
    void select(int delta) {
        Workpieces.Drawing d = Workpieces.drawingOf(items.getItem(DRAWING));
        int n = d == null ? 1 : Math.max(startable(station, d.plan()).size(), 1);
        if (station == Station.ASSEMBLY_BENCH && items.getItem(DRAWING).isEmpty() && level != null) { n = Math.max(recipes(level, recipeInput()).size(), 1); }
        selected = Math.floorMod(selected + delta, n);
        progress = 0;
        work = 0;
        setChanged();
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items.getItems(), registries);
        tag.putInt("Progress", progress);
        tag.putDouble("Work", work);
        tag.putInt("Energy", energy.getEnergyStored());
        tag.putDouble("OwedFe", owedFe);
        tag.putDouble("FuelHeatJ", fuelHeatJ);
        tag.putInt("Selected", selected);
        tag.putInt("LastKey", lastKey);
        for (int k = 0; k < owed.length; k++) { if (owed[k] > 0) { tag.putDouble("Owed_" + Consumable.values()[k].id(), owed[k]); } }
        tag.putDouble("RegrindWork", regrindWork);
        for (var e : chipsOwed.entrySet()) { tag.putDouble("Chips_" + e.getKey().id(), e.getValue()); }
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        NonNullList<ItemStack> list = NonNullList.withSize(items.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, list, registries);
        for (int i = 0; i < list.size(); i++) { items.setItem(i, list.get(i)); }
        progress = tag.getInt("Progress");
        work = tag.contains("Work") ? tag.getDouble("Work") : progress;
        energy.set(tag.getInt("Energy"));
        owedFe = tag.getDouble("OwedFe");
        fuelHeatJ = tag.getDouble("FuelHeatJ");
        selected = tag.getInt("Selected");
        lastKey = tag.getInt("LastKey");
        for (int k = 0; k < owed.length; k++) { owed[k] = tag.getDouble("Owed_" + Consumable.values()[k].id()); }
        regrindWork = tag.getDouble("RegrindWork");
        chipsOwed.clear();
        for (Alloy a : Alloy.values()) { if (tag.contains("Chips_" + a.id())) { chipsOwed.put(a, tag.getDouble("Chips_" + a.id())); } }
    }

    // ---------------------------------------------------------------- 0.17: operators, consumables, regrinding, older workpieces

    /** Whether it needs an operator: a manual station, unless its NC drive runs it. */
    boolean needsOperator() {
        ToolConfig t = tool();
        return t != null ? t.manual() : station.manual;
    }

    /** The consumable it lacks for its job (the screen's NO_TOOL), and what the current job owes of each (items). */
    Consumable missingConsumable;
    final double[] owed = new double[Consumable.values().length];
    private int wearSig = Integer.MIN_VALUE;
    private java.util.Map<Consumable, Double> wearUse = java.util.Map.of();

    /** What one piece of a job uses up (items), worked out once a job: each step's time on this machine, its tools' material. */
    java.util.Map<Consumable, Double> wearOf(Job job, int sig) {
        if (!dev.mcmod.hdfv.neoforge.HdfvConfig.consumables()) { return java.util.Map.of(); }
        if (sig == wearSig) { return wearUse; }
        java.util.Map<Consumable, Double> use = new java.util.EnumMap<>(Consumable.class);
        ToolConfig t = tool();
        Head h = t == null ? null : t.head();
        double speed = t != null ? t.speedFactor() : station.speedFactor;
        Wear.Tool material = toolMaterial();
        Workpiece w = job.piece();
        for (int i = job.from(); i < job.from() + job.steps() && i < w.route().size(); i++) {
            Operation op = w.route().get(i);
            double sec = h != null ? Machining.seconds(op, h, w) * t.timeFactor(op) : Plans.seconds(op, station, w);
            if (!Double.isFinite(sec)) { continue; }
            Wear.of(op, w, station, h, speed, sec, material).forEach((c, x) -> use.merge(c, x, Double::sum));
        }
        wearSig = sig;
        wearUse = use;
        return use;
    }

    /** The tool material of its cutting tools: the best set it has (cermet, high-speed steel, 0.23 plain carbon steel; high-speed steel when none). */
    Wear.Tool toolMaterial() {
        Wear.Tool best = null;
        for (int i = firstConsumable(); i < firstConsumable() + consumables; i++) {
            Wear.Tool t = toolOf(HdfvMachining.supplyOf(items.getItem(i)));
            if (t == null || dull(items.getItem(i))) { continue; }
            if (best == null || rank(t) > rank(best)) { best = t; }
        }
        return best == null ? Wear.Tool.HSS : best;
    }

    private static int rank(Wear.Tool t) { return switch (t) { case CARBON -> 0; case HSS -> 1; case CERMET -> 2; }; }

    /** The tool material a supply is (null: not a tool set). */
    static Wear.Tool toolOf(Consumable.Supply s) {
        if (s == null) { return null; }
        return switch (s) {
            case HSS_TOOL_SET -> Wear.Tool.HSS;
            case CERMET_TOOL_SET -> Wear.Tool.CERMET;
            case CARBON_TOOL_SET -> Wear.Tool.CARBON;
            default -> null;
        };
    }

    /** Whether a durable supply is worn out (a dull tool set waits for the tool grinder). */
    static boolean dull(ItemStack s) { return s.isDamageableItem() && s.getDamageValue() >= s.getMaxDamage(); }

    /** The consumable slot that supplies a consumable for a workpiece (aluminium rivets for the light alloys; -1: none). */
    int supplySlot(Consumable c, Workpiece w) {
        for (int i = firstConsumable(); i < firstConsumable() + consumables; i++) {
            ItemStack s = items.getItem(i);
            Consumable.Supply sup = HdfvMachining.supplyOf(s);
            if (sup == null || sup.supplies != c || s.isEmpty() || dull(s)) { continue; }
            if (c == Consumable.RIVETS) {
                boolean light = w.alloy().family == dev.mcmod.hdfv.manufacture.Alloy.Family.ALUMINIUM || w.alloy().family == dev.mcmod.hdfv.manufacture.Alloy.Family.MAGNESIUM;
                if ((sup == Consumable.Supply.ALUMINIUM_RIVETS) != light) { continue; }
            }
            if (c == Consumable.CUTTING_TOOLS && toolOf(sup) != toolMaterial()) { continue; }
            return i;
        }
        return -1;
    }

    /** The first consumable a job uses that it has no supply of (null: it has them all). */
    Consumable lacking(java.util.Map<Consumable, Double> use, Workpiece w) {
        for (var e : use.entrySet()) { if (e.getValue() > 0 && supplySlot(e.getKey(), w) < 0) { return e.getKey(); } }
        return null;
    }

    /** Charges a share (of a piece) of a job's use to its supplies: a durable one's damage, a counted one a whole item at a time. */
    void charge(java.util.Map<Consumable, Double> use, Workpiece w, double share) {
        for (var e : use.entrySet()) {
            int k = e.getKey().ordinal();
            owed[k] += e.getValue() * share;
            int slot = supplySlot(e.getKey(), w);
            if (slot < 0) { continue; }
            ItemStack s = items.getItem(slot);
            Consumable.Supply sup = HdfvMachining.supplyOf(s);
            if (sup.durable) {
                int units = (int) Math.floor(owed[k] * Consumable.LIFE_UNITS);
                if (units <= 0) { continue; }
                owed[k] -= units / (double) Consumable.LIFE_UNITS;
                int damage = s.getDamageValue() + units;
                if (damage < s.getMaxDamage()) { s.setDamageValue(damage); }
                else if (sup.regrinds > 0) { s.setDamageValue(s.getMaxDamage()); }   // dull: it waits for the tool grinder
                else if (sup.supplies == Consumable.ASSIST_GAS) {
                    // the empty cylinder goes to the outputs (to be refilled at the air separation unit), or stays where it was
                    ItemStack empty = new ItemStack(HdfvMachining.EMPTY_CYLINDER.get());
                    items.setItem(slot, ItemStack.EMPTY);
                    if (!put(empty)) { items.setItem(slot, empty); }
                }
                else { s.shrink(1); }
            } else {
                while (owed[k] >= 1 && !s.isEmpty()) { s.shrink(1); owed[k] -= 1; }
            }
            setChanged();
        }
    }

    /** Moves the drawing's workpieces made on an older route onto the current one, where they are (inputs and outputs). */
    void migrate(PartPlan plan, String hash) {
        for (int i = FIRST_INPUT; i < firstConsumable(); i++) {
            ItemStack s = items.getItem(i);
            Workpieces.Data d = Workpieces.data(s);
            if (d == null || !d.older() || !d.hash().equals(hash)) { continue; }
            Workpieces.Data now = d.current(Workpieces.piece(plan, d.key()));
            if (now != d) { s.set(HdfvMachines.WORKPIECE_DATA.get(), now); setChanged(); }
        }
    }

    /** A tool set's regrinding on the tool grinder, s of work: its ten edges ground at about a minute and a half each (a rule). */
    public static final double REGRIND_S = 900;
    double regrindWork;

    /**
     * The tool grinder regrinds a dull (or worn) tool set from its inputs: its edges back to new, one regrind more; one
     * past its regrinds is scrapped. Whether it is doing that (it then does nothing else this tick).
     */
    boolean regrind() {
        ToolConfig t = tool();
        if (t == null || t.head() != Head.TOOL_GRINDING) { return false; }
        int slot = -1;
        for (int i = FIRST_INPUT; i < FIRST_INPUT + inputs; i++) {
            ItemStack s = items.getItem(i);
            Consumable.Supply sup = HdfvMachining.supplyOf(s);
            if (sup != null && sup.regrinds > 0 && s.getDamageValue() > 0) { slot = i; break; }
        }
        if (slot < 0) { regrindWork = 0; return false; }
        ItemStack worn = items.getItem(slot);
        int times = worn.getOrDefault(HdfvMachining.REGRINDS.get(), 0) + 1;
        ItemStack sharp = worn.copyWithCount(1);
        sharp.setDamageValue(0);
        sharp.set(HdfvMachining.REGRINDS.get(), times);
        boolean scrap = times > HdfvMachining.supplyOf(worn).regrinds;
        if (!scrap && !fits(sharp)) { idle(State.OUTPUT_FULL); return true; }
        total = (int) Math.min(Pace.ticks(REGRIND_S * t.timeFactor(Operation.fixed(dev.mcmod.hdfv.manufacture.Process.GRIND, 0)),
                dev.mcmod.hdfv.neoforge.HdfvConfig.ticksPerWorkHour()), Integer.MAX_VALUE);
        if (needsOperator() && operators() == 0) { state = State.NO_OPERATOR; setChanged(); return true; }
        double rate = power(0.5 * t.powerW() + t.baseW());
        if (!(rate > 1e-9)) { state = State.NO_POWER; setChanged(); return true; }
        state = State.WORKING;
        regrindWork += rate;
        progress = (int) Math.floor(regrindWork + 1e-9);
        if (progress < total) { setChanged(); return true; }
        worn.shrink(1);
        if (!scrap) { put(sharp); }
        regrindWork = 0;
        progress = 0;
        setChanged();
        return true;
    }

    @Override public Component getDisplayName() { return Component.translatable("block.hdfv." + MachineBlock.id(station)); }

    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        MachineMenu menu = new MachineMenu(id, inventory, this);
        if (player instanceof ServerPlayer sp) { operators.add(sp); }
        return menu;
    }
}
