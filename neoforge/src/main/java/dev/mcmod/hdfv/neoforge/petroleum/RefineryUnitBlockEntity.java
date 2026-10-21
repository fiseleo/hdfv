package dev.mcmod.hdfv.neoforge.petroleum;

import dev.mcmod.aerocore.api.petroleum.Conversion;
import dev.mcmod.aerocore.api.petroleum.CrudeAssay;
import dev.mcmod.aerocore.api.petroleum.Distillation;
import dev.mcmod.hdfv.fluid.HdfvFluids;
import dev.mcmod.hdfv.neoforge.HdfvConfig;
import dev.mcmod.hdfv.neoforge.fluid.HdfvFluidRegistry;
import dev.mcmod.hdfv.neoforge.multiblock.ControllerBlockEntity;
import dev.mcmod.hdfv.petroleum.Refining;
import dev.mcmod.hdfv.structure.Pattern;
import dev.mcmod.hdfv.structure.Structures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * A refinery unit's controller (0.8): the crude distillation column or a process unit (its reactor blocks say which).
 * Each tick of work it runs as much feed as its capacity, its feeds (in the proportions its conversion takes) and the
 * room in its outlets let it, by mass (core petroleum.Refining): the fluid products into the outlets, the solid ones
 * (bitumen, polymers, fibre) into its output for hoppers, what it burns for its heat gone up the stack; its pumps and
 * compressors on Forge Energy. Feeds come in by pipe or can (two tanks of 4000 l). The column runs up to what a 1 m
 * column carries at 80 % of flooding (Souders and Brown) over the share of the crude that flashes; the reformer and
 * FCC 2 kg/s, a cracker and the lube plant 1, the polymer plant 0.5.
 * <p>Its screen (0.9) sets it running or not, its throughput (a share of its capacity), which outlet each product
 * leaves by, and its process: the column's cut points and whether its vacuum section runs, the FCC's riser
 * temperature, the reformer's octane, the cracker's coil temperature, the polymer plant's product (Refining's yields at
 * those conditions).</p>
 */
public class RefineryUnitBlockEntity extends ControllerBlockEntity implements Configurable {
    public enum State { NOT_FORMED, BAD_REACTOR, NO_FEED, NO_POWER, OUTLETS_FULL, RUNNING, STOPPED, NO_ROUTE }

    public static final int FEED_LITRES = 4000;
    public static final double COLUMN_DIAMETER_M = 1.0, VAPOUR_DENSITY = 4.3, LIQUID_DENSITY = 700;
    private final boolean column;
    final FluidTank[] feeds = {tank(), tank()};
    private final ItemStackHandler output = new ItemStackHandler(4) {
        @Override protected void onContentsChanged(int slot) { setChanged(); }
    };
    private final Map<String, Double> owed = new HashMap<>();
    private int polymer;
    /** Its settings (0.9). */
    private boolean running = true, vacuumOn = true;
    private double throughput = 1, naphthaCut = Refining.Cuts.NOMINAL.naphthaEndC(), keroseneCut = Refining.Cuts.NOMINAL.keroseneEndC(),
            dieselCut = Refining.Cuts.NOMINAL.dieselEndC(), riserC = Conversion.NOMINAL_RISER_C, octane = Conversion.NOMINAL_OCTANE, coilC = Conversion.NOMINAL_COIL_C;
    /** Which product each outlet takes, bottom to top (null: as it comes; "": none). */
    private final String[] routing = new String[8];
    private State state = State.NOT_FORMED;
    /** Totals since placed, kg: feed run, products made, burnt. */
    double feedKg, productKg, burntKg;

    protected RefineryUnitBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState s, boolean column) {
        super(type, pos, s, column ? Structures.DISTILLATION_COLUMN : Structures.PROCESS, 200e3);
        this.column = column;
    }

    public static RefineryUnitBlockEntity column(BlockPos pos, BlockState s) { return new RefineryUnitBlockEntity(HdfvRefinery.COLUMN_ENTITY.get(), pos, s, true); }
    public static RefineryUnitBlockEntity unit(BlockPos pos, BlockState s) { return new RefineryUnitBlockEntity(HdfvRefinery.UNIT_ENTITY.get(), pos, s, false); }

    private FluidTank tank() {
        return new FluidTank(FEED_LITRES, f -> accepts(HdfvFluidRegistry.id(f.getFluid()))) {
            @Override protected void onContentsChanged() { setChanged(); }
        };
    }

    public State state() { return state; }
    /** Sets the polymer plant's product (an index into Refining.POLYMERS). */
    public void setPolymer(int i) { polymer = Math.floorMod(i, Refining.POLYMERS.size()); setChanged(); }
    public ItemStackHandler output() { return output; }
    public double feedKg() { return feedKg; }
    public double productKg() { return productKg; }
    public double burntKg() { return burntKg; }

    /** The products it has made but not yet put out (fractions of a litre, part of an item), kg. */
    double owedKg() {
        double kg = 0;
        for (Map.Entry<String, Double> e : owed.entrySet()) {
            if (e.getKey().startsWith("feed:")) { continue; }
            kg += Refining.item(e.getKey()) ? e.getValue() : e.getValue() * density(e.getKey()) / 1000;
        }
        return kg;
    }

    /** What it takes in: crude for the column; the feeds of its reactor's conversions for a unit. */
    boolean accepts(String fluid) {
        if (column) { return fluid.equals("crude_oil") || fluid.equals("heavy_crude_oil"); }
        String r = reactor();
        if (r == null) { return false; }
        for (Refining.Yield y : yields(r)) { if (y.feeds().containsKey(fluid)) { return true; } }
        return false;
    }

    /** Its reactor kind (a unit's), null when not formed or mixed. */
    String reactor() { return column || multiblock.match() == null ? null : Structures.reactor(multiblock.match()); }

    static List<Refining.Yield> yields(String reactor) {
        return switch (reactor) {
            // 0.23: with naphtha in its feeds it reforms; with LPG and water it makes methanol
            case Structures.REFORMER -> List.of(Refining.REFORMER, Refining.METHANOL);
            case Structures.FCC -> List.of(Refining.FCC);
            case Structures.CRACKER -> List.of(Refining.NAPHTHA_CRACKER, Refining.LPG_CRACKER);
            case Structures.LUBE -> List.of(Refining.LUBE);
            case Structures.POLYMER -> Refining.POLYMERS;
            default -> List.of();
        };
    }

    /** What feed it can hold now, litres of a fluid. */
    private double litres(String fluid) {
        double l = 0;
        for (FluidTank t : feeds) { if (!t.isEmpty() && HdfvFluidRegistry.id(t.getFluid().getFluid()).equals(fluid)) { l += t.getFluidAmount(); } }
        return l;
    }

    /** Whether the column's vacuum section is there and running. */
    boolean vacuum() { return vacuumOn && multiblock.match() != null && multiblock.match().has(Structures.VACUUM_COLUMN); }

    /** The column's cut points. */
    public Refining.Cuts cuts() { return new Refining.Cuts(naphthaCut, keroseneCut, dieselCut); }

    /** A unit's yield at its settings. */
    private Refining.Yield atConditions(Refining.Yield y) {
        if (y == Refining.FCC) { return Refining.fcc(riserC); }
        if (y == Refining.REFORMER) { return Refining.reformer(octane); }
        if (y == Refining.NAPHTHA_CRACKER) { return Refining.cracker("naphtha", coilC); }
        if (y == Refining.LPG_CRACKER) { return Refining.cracker("lpg", coilC); }
        return y;
    }

    /** The conversion it would run now (null: none). */
    public Refining.Yield conversion() {
        if (column) {
            for (FluidTank t : feeds) {
                if (!t.isEmpty()) { return Refining.distillation(HdfvFluidRegistry.id(t.getFluid().getFluid()), vacuum(), cuts()); }
            }
            return null;
        }
        String r = reactor();
        if (r == null) { return null; }
        if (r.equals(Structures.POLYMER)) { return Refining.POLYMERS.get(Math.floorMod(polymer, Refining.POLYMERS.size())); }
        for (Refining.Yield y : yields(r)) { for (String f : y.feeds().keySet()) { if (litres(f) > 0) { return atConditions(y); } } }
        return yields(r).isEmpty() ? null : atConditions(yields(r).get(0));
    }

    /** Most feed it runs a second, kg. */
    double capacityKgPerS() {
        if (column) {
            Refining.Yield y = conversion();
            CrudeAssay a = y != null && y.feeds().containsKey("heavy_crude_oil") ? CrudeAssay.HEAVY_SOUR : CrudeAssay.LIGHT_SWEET;
            return throughput * Distillation.capacityKgPerS(COLUMN_DIAMETER_M, LIQUID_DENSITY, VAPOUR_DENSITY) / Math.max(0.3, a.massBoiledBelow(cuts().flashC()));
        }
        String r = reactor();
        return r == null ? 0 : throughput * switch (r) { case Structures.REFORMER, Structures.FCC -> 2.0; case Structures.POLYMER -> 0.5; default -> 1.0; };
    }

    /** The outlets, bottom to top and left to right as the pattern lists them. */
    List<UnitOutletBlockEntity> outlets() {
        List<UnitOutletBlockEntity> out = new ArrayList<>();
        Pattern.Match m = multiblock.match();
        if (m == null || level == null) { return out; }
        for (Pattern.Cell c : m.cells()) {
            if (Structures.UNIT_OUTLET.equals(c.kind()) && level.getBlockEntity(new BlockPos(c.x(), c.y(), c.z())) instanceof UnitOutletBlockEntity o) { out.add(o); }
        }
        return out;
    }

    /** Which outlet each fluid product leaves by. */
    Map<String, UnitOutletBlockEntity> routes(Refining.Yield y, List<UnitOutletBlockEntity> outlets) {
        List<String> plan = outletPlan(y, outlets.size());
        Map<String, UnitOutletBlockEntity> r = new HashMap<>();
        for (int i = 0; i < plan.size(); i++) { if (!plan.get(i).isEmpty()) { r.put(plan.get(i), outlets.get(i)); } }
        return r;
    }

    /** The product each outlet takes (bottom to top; "" none): as it comes, then as the screen routed it (0.9). */
    List<String> outletPlan(Refining.Yield y, int outlets) {
        List<String> plan = new ArrayList<>();
        for (int i = 0; i < outlets; i++) { plan.add(""); }
        if (column) {
            List<String> names = Refining.distillationOutlets(vacuum());
            for (int i = 0; i < names.size() && i < outlets; i++) { plan.set(i, names.get(i)); }
        } else if (y != null) {
            int i = 0;
            for (String p : new TreeMap<>(y.products()).keySet()) { if (!Refining.item(p) && !Refining.ROUTED_ONLY.contains(p) && i < outlets) { plan.set(i++, p); } }
        }
        for (int i = 0; i < outlets && i < routing.length; i++) {
            if (routing[i] == null) { continue; }
            if (!routing[i].isEmpty()) { for (int j = 0; j < outlets; j++) { if (plan.get(j).equals(routing[i])) { plan.set(j, ""); } } }
            plan.set(i, routing[i]);
        }
        return plan;
    }

    /** The fluid products an outlet can be routed to: what any conversion of this unit makes. */
    List<String> routable() {
        java.util.TreeSet<String> out = new java.util.TreeSet<>();
        List<Refining.Yield> all = new ArrayList<>();
        if (column) {
            for (String crude : List.of("crude_oil", "heavy_crude_oil")) { for (boolean v : new boolean[]{false, true}) { all.add(Refining.distillation(crude, v)); } }
        } else if (reactor() != null) {
            all.addAll(yields(reactor()));
        }
        for (Refining.Yield y : all) { for (String p : y.products().keySet()) { if (!Refining.item(p)) { out.add(p); } } }
        return new ArrayList<>(out);
    }

    static double density(String fluid) { HdfvFluids.Spec s = HdfvFluids.of(fluid); return s == null ? 1000 : s.densityKgPerM3(); }

    /** A column's fired heater roars; a unit's compressors and pumps run. */
    @Override protected dev.mcmod.hdfv.sound.MachineSound.Hum hum() {
        return state == State.RUNNING ? dev.mcmod.hdfv.sound.MachineSound.plant(column ? dev.mcmod.aerocore.api.acoustics.ProcessNoise.Process.BURNER : dev.mcmod.aerocore.api.acoustics.ProcessNoise.Process.COMPRESSOR, Math.max(lastW, 30e3), 0.8, true) : null;
    }

    @Override protected void work(ServerLevel level, boolean formed) {
        lastW = 0;
        if (!formed) { state = State.NOT_FORMED; return; }
        Refining.Yield y = conversion();
        if (!column && reactor() == null) { state = State.BAD_REACTOR; return; }
        if (!running) { state = State.STOPPED; return; }
        if (y == null) { state = State.NO_FEED; return; }
        double seconds = HdfvConfig.workSecondsPerTick();
        double kg = capacityKgPerS() * seconds;
        for (Map.Entry<String, Double> f : y.feeds().entrySet()) { kg = Math.min(kg, litres(f.getKey()) * density(f.getKey()) / 1000 / f.getValue()); }
        if (!(kg > 1e-6)) { state = State.NO_FEED; return; }
        List<UnitOutletBlockEntity> outlets = outlets();
        Map<String, UnitOutletBlockEntity> routes = routes(y, outlets);
        // 0.10: hydrogen leaves only by an outlet routed for it, compressed to 700 bar; unrouted it goes to the fuel gas
        for (String h : Refining.ROUTED_ONLY) {
            if (y.products().containsKey(h)) { y = routes.containsKey(h) ? y.compressing(h, dev.mcmod.hdfv.fluid.HydrogenStorage.COMPRESSION_J_PER_KG) : y.flaring(h); }
        }
        for (Map.Entry<String, Double> p : y.products().entrySet()) {
            if (Refining.item(p.getKey())) { continue; }
            UnitOutletBlockEntity o = routes.get(p.getKey());
            if (o == null) { state = State.NO_ROUTE; return; }
            double room = o.room(new FluidStack(HdfvFluidRegistry.fluid(p.getKey()), 1)) - Math.ceil(owed.getOrDefault(p.getKey(), 0.0));
            kg = Math.min(kg, Math.max(0, room) * density(p.getKey()) / 1000 / p.getValue());
        }
        if (!(kg > 1e-6)) { state = State.OUTLETS_FULL; return; }
        double share = draw(y.gridJPerKg() * kg / seconds);
        if (share <= 0) { state = State.NO_POWER; return; }
        kg *= share;
        // feeds out, products in (fractions of a litre carried), solids to the output in whole items
        for (Map.Entry<String, Double> f : y.feeds().entrySet()) { drain(f.getKey(), kg * f.getValue() * 1000 / density(f.getKey())); }
        double made = 0;
        for (Map.Entry<String, Double> p : y.products().entrySet()) {
            double pkg = kg * p.getValue();
            made += pkg;
            if (Refining.item(p.getKey())) {
                double have = owed.getOrDefault(p.getKey(), 0.0) + pkg, each = Refining.ITEM_KG.get(p.getKey());
                while (have >= each - 1e-9) {
                    ItemStack item = new ItemStack(HdfvRefinery.product(p.getKey()));
                    ItemStack left = item;
                    for (int i = 0; i < output.getSlots() && !left.isEmpty(); i++) { left = output.insertItem(i, left, false); }
                    if (!left.isEmpty()) { break; }
                    have -= each;
                }
                owed.put(p.getKey(), have);
            } else {
                double litres = pkg * 1000 / density(p.getKey()) + owed.getOrDefault(p.getKey(), 0.0);
                int whole = (int) Math.floor(litres);
                int put = whole > 0 ? routes.get(p.getKey()).put(new FluidStack(HdfvFluidRegistry.fluid(p.getKey()), whole)) : 0;
                owed.put(p.getKey(), litres - put);
            }
        }
        feedKg += kg;
        productKg += made;
        burntKg += kg * y.burnt();
        state = State.RUNNING;
        setChanged();
        // 0.24: the tutorial's "refined" milestone, for whoever is near a unit making something (every 5 s)
        if (made > 0 && level instanceof net.minecraft.server.level.ServerLevel server && server.getGameTime() % 100 == 0) {
            dev.mcmod.hdfv.neoforge.tutorial.Milestones.near(server, worldPosition, 16, "refined");
        }
    }

    private void drain(String fluid, double litres) {
        String key = "feed:" + fluid;
        double want = litres + owed.getOrDefault(key, 0.0);
        int whole = (int) Math.floor(want);
        int got = 0;
        for (FluidTank t : feeds) {
            if (got >= whole || t.isEmpty() || !HdfvFluidRegistry.id(t.getFluid().getFluid()).equals(fluid)) { continue; }
            got += t.drain(whole - got, IFluidHandler.FluidAction.EXECUTE).getAmount();
        }
        owed.put(key, want - got);
    }

    /** Feeds in: by pipe or can, into whichever tank takes the fluid. */
    public IFluidHandler feedHandler() {
        return new IFluidHandler() {
            @Override public int getTanks() { return feeds.length; }
            @Override public FluidStack getFluidInTank(int i) { return feeds[i].getFluid(); }
            @Override public int getTankCapacity(int i) { return FEED_LITRES; }
            @Override public boolean isFluidValid(int i, FluidStack s) { return feeds[i].isFluidValid(s); }
            @Override public int fill(FluidStack s, FluidAction a) {
                for (FluidTank t : feeds) { if (!t.isEmpty() && FluidStack.isSameFluidSameComponents(t.getFluid(), s)) { return t.fill(s, a); } }
                for (FluidTank t : feeds) { if (t.isEmpty() && t.isFluidValid(s)) { return t.fill(s, a); } }
                return 0;
            }
            @Override public FluidStack drain(FluidStack s, FluidAction a) { return FluidStack.EMPTY; }
            @Override public FluidStack drain(int n, FluidAction a) { return FluidStack.EMPTY; }
        };
    }

    /** What hoppers see: the solid products to take. */
    public net.neoforged.neoforge.items.IItemHandler outputHandler() {
        return new net.neoforged.neoforge.items.wrapper.RangedWrapper(output, 0, output.getSlots()) {
            @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { return stack; }
        };
    }

    // ---------------------------------------------------------------- the screen (0.9)

    @Override public PetroleumMenu.Kind kind() { return column ? PetroleumMenu.Kind.COLUMN : PetroleumMenu.Kind.UNIT; }
    @Override public net.neoforged.neoforge.items.IItemHandler menuItems() { return output; }

    /** Ranges of the numeric settings: key, least, most. */
    private static double[] range(String key) {
        return switch (key) {
            case "throughput" -> new double[]{0.2, 1};
            case "naphtha" -> new double[]{Refining.Cuts.NAPHTHA_MIN, Refining.Cuts.NAPHTHA_MAX};
            case "kerosene" -> new double[]{Refining.Cuts.KEROSENE_MIN, Refining.Cuts.KEROSENE_MAX};
            case "diesel" -> new double[]{Refining.Cuts.DIESEL_MIN, Refining.Cuts.DIESEL_MAX};
            case "riser" -> new double[]{Conversion.RISER_MIN_C, Conversion.RISER_MAX_C};
            case "octane" -> new double[]{Conversion.OCTANE_MIN, Conversion.OCTANE_MAX};
            case "coil" -> new double[]{Conversion.COIL_MIN_C, Conversion.COIL_MAX_C};
            default -> null;
        };
    }

    @Override public Component set(String key, double value) {
        if (key.equals("running")) { running = value > 0.5; setChanged(); return null; }
        if (key.equals("vacuum")) { vacuumOn = value > 0.5; setChanged(); return null; }
        if (key.equals("polymer")) {
            if (!Structures.POLYMER.equals(reactor())) { return Component.translatable("hdfv.gui.no_setting"); }
            polymer = Math.floorMod((int) value, Refining.POLYMERS.size());
            setChanged();
            return null;
        }
        if (key.startsWith("route.")) {
            int i;
            try { i = Integer.parseInt(key.substring(6)); } catch (NumberFormatException e) { return Component.translatable("hdfv.gui.no_setting"); }
            if (i < 0 || i >= routing.length) { return Component.translatable("hdfv.gui.no_setting"); }
            List<String> names = routable();
            int v = (int) value;
            if (v == -2) { routing[i] = null; }
            else if (v == -1) { routing[i] = ""; }
            else if (v >= 0 && v < names.size()) { routing[i] = names.get(v); }
            else { return Configurable.outOfRange("route", -2, names.size() - 1); }
            setChanged();
            return null;
        }
        double[] r = range(key);
        if (r == null) { return Component.translatable("hdfv.gui.no_setting"); }
        if (!(value >= r[0] - 1e-9 && value <= r[1] + 1e-9)) { return Configurable.outOfRange(key, r[0], r[1]); }
        switch (key) {
            case "throughput" -> throughput = value;
            case "naphtha", "kerosene", "diesel" -> {
                double n = key.equals("naphtha") ? value : naphthaCut, k = key.equals("kerosene") ? value : keroseneCut, d = key.equals("diesel") ? value : dieselCut;
                new Refining.Cuts(n, k, d);
                naphthaCut = n; keroseneCut = k; dieselCut = d;
            }
            case "riser" -> riserC = value;
            case "octane" -> octane = value;
            case "coil" -> coilC = value;
            default -> { }
        }
        setChanged();
        return null;
    }

    @Override public CompoundTag view() {
        CompoundTag t = new CompoundTag();
        t.putString("State", state.name().toLowerCase(Locale.ROOT));
        t.putBoolean("Running", running);
        t.putDouble("Throughput", throughput);
        t.putString("Reactor", column ? "" : String.valueOf(reactor()));
        t.putBoolean("HasVacuum", column && multiblock.match() != null && multiblock.match().has(Structures.VACUUM_COLUMN));
        t.putBoolean("VacuumOn", vacuumOn);
        t.putDouble("Naphtha", naphthaCut); t.putDouble("Kerosene", keroseneCut); t.putDouble("Diesel", dieselCut);
        t.putDouble("Riser", riserC); t.putDouble("Octane", octane); t.putDouble("Coil", coilC);
        t.putInt("Polymer", polymer);
        Refining.Yield y = conversion();
        if (y != null) {
            t.putString("Unit", y.name());
            CompoundTag yields = new CompoundTag();
            y.products().forEach(yields::putDouble);
            t.put("Yields", yields);
            t.putDouble("Burnt", y.burnt());
        }
        net.minecraft.nbt.ListTag feedList = new net.minecraft.nbt.ListTag();
        for (FluidTank f : feeds) {
            CompoundTag c = new CompoundTag();
            c.putString("Fluid", f.isEmpty() ? "" : HdfvFluidRegistry.id(f.getFluid().getFluid()));
            c.putInt("Litres", f.getFluidAmount());
            feedList.add(c);
        }
        t.put("Feeds", feedList);
        List<UnitOutletBlockEntity> outs = outlets();
        List<String> plan = outletPlan(y, outs.size());
        net.minecraft.nbt.ListTag outList = new net.minecraft.nbt.ListTag();
        for (int i = 0; i < outs.size(); i++) {
            CompoundTag c = new CompoundTag();
            FluidStack f = outs.get(i).fluid();
            c.putString("Route", plan.get(i));
            c.putBoolean("Set", i < routing.length && routing[i] != null);
            c.putString("Fluid", f.isEmpty() ? "" : HdfvFluidRegistry.id(f.getFluid()));
            c.putInt("Litres", f.getAmount());
            outList.add(c);
        }
        t.put("Outlets", outList);
        net.minecraft.nbt.ListTag names = new net.minecraft.nbt.ListTag();
        for (String n : routable()) { names.add(net.minecraft.nbt.StringTag.valueOf(n)); }
        t.put("Routable", names);
        t.putDouble("FeedKg", feedKg);
        t.putDouble("BurntKg", burntKg);
        t.putDouble("CapacityKgPerS", capacityKgPerS());
        t.putDouble("PowerW", lastW);
        return t;
    }

    @Override public boolean use(Player player, InteractionHand hand, ItemStack held) {
        if (!held.isEmpty() && FluidUtil.getFluidHandler(held).isPresent()) { return FluidUtil.interactWithFluidHandler(player, hand, feedHandler()); }
        if (held.isEmpty() && Structures.POLYMER.equals(reactor())) { polymer = Math.floorMod(polymer + 1, Refining.POLYMERS.size()); setChanged(); return true; }
        return false;
    }

    @Override public List<ItemStack> drops() {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < output.getSlots(); i++) { if (!output.getStackInSlot(i).isEmpty()) { out.add(output.getStackInSlot(i)); } }
        return out;
    }

    @Override public Component status() {
        Refining.Yield y = conversion();
        Component what = y == null ? Component.literal("-") : Component.translatable("hdfv.refinery.unit." + y.name());
        return Component.translatable("hdfv.refinery.status", Component.translatable("hdfv.refinery.state." + state.name().toLowerCase(Locale.ROOT)), what,
                String.format(Locale.ROOT, "%.0f", feedKg));
    }

    @Override protected void save(CompoundTag tag, HolderLookup.Provider registries) {
        for (int i = 0; i < feeds.length; i++) { CompoundTag t = new CompoundTag(); feeds[i].writeToNBT(registries, t); tag.put("Feed" + i, t); }
        tag.put("Output", output.serializeNBT(registries));
        CompoundTag o = new CompoundTag();
        owed.forEach(o::putDouble);
        tag.put("Owed", o);
        tag.putInt("Polymer", polymer);
        tag.putDouble("FeedKg", feedKg); tag.putDouble("ProductKg", productKg); tag.putDouble("BurntKg", burntKg);
        tag.putBoolean("Running", running); tag.putBoolean("VacuumOn", vacuumOn); tag.putDouble("Throughput", throughput);
        tag.putDouble("NaphthaCut", naphthaCut); tag.putDouble("KeroseneCut", keroseneCut); tag.putDouble("DieselCut", dieselCut);
        tag.putDouble("Riser", riserC); tag.putDouble("Octane", octane); tag.putDouble("Coil", coilC);
        CompoundTag r = new CompoundTag();
        for (int i = 0; i < routing.length; i++) { if (routing[i] != null) { r.putString(String.valueOf(i), routing[i]); } }
        tag.put("Routing", r);
    }

    @Override protected void load(CompoundTag tag, HolderLookup.Provider registries) {
        for (int i = 0; i < feeds.length; i++) { feeds[i].readFromNBT(registries, tag.getCompound("Feed" + i)); }
        if (tag.contains("Output")) { output.deserializeNBT(registries, tag.getCompound("Output")); }
        CompoundTag o = tag.getCompound("Owed");
        owed.clear();
        for (String k : o.getAllKeys()) { owed.put(k, o.getDouble(k)); }
        polymer = tag.getInt("Polymer");
        feedKg = tag.getDouble("FeedKg"); productKg = tag.getDouble("ProductKg"); burntKg = tag.getDouble("BurntKg");
        running = !tag.contains("Running") || tag.getBoolean("Running");
        vacuumOn = !tag.contains("VacuumOn") || tag.getBoolean("VacuumOn");
        throughput = tag.contains("Throughput") ? Math.clamp(tag.getDouble("Throughput"), 0.2, 1) : 1;
        if (tag.contains("NaphthaCut")) {
            try {
                Refining.Cuts c = new Refining.Cuts(tag.getDouble("NaphthaCut"), tag.getDouble("KeroseneCut"), tag.getDouble("DieselCut"));
                naphthaCut = c.naphthaEndC(); keroseneCut = c.keroseneEndC(); dieselCut = c.dieselEndC();
            } catch (IllegalArgumentException e) { /* keep the nominal cuts */ }
        }
        riserC = tag.contains("Riser") ? Math.clamp(tag.getDouble("Riser"), Conversion.RISER_MIN_C, Conversion.RISER_MAX_C) : Conversion.NOMINAL_RISER_C;
        octane = tag.contains("Octane") ? Math.clamp(tag.getDouble("Octane"), Conversion.OCTANE_MIN, Conversion.OCTANE_MAX) : Conversion.NOMINAL_OCTANE;
        coilC = tag.contains("Coil") ? Math.clamp(tag.getDouble("Coil"), Conversion.COIL_MIN_C, Conversion.COIL_MAX_C) : Conversion.NOMINAL_COIL_C;
        CompoundTag r = tag.getCompound("Routing");
        for (int i = 0; i < routing.length; i++) { routing[i] = r.contains(String.valueOf(i)) ? r.getString(String.valueOf(i)) : null; }
    }
}
