package dev.mcmod.hdfv.neoforge.machine;

import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.hdfv.design.Designs;
import dev.mcmod.hdfv.manufacture.Operation;
import dev.mcmod.hdfv.manufacture.PartPlan;
import dev.mcmod.hdfv.manufacture.Plans;
import dev.mcmod.hdfv.manufacture.Process;
import dev.mcmod.hdfv.manufacture.Station;
import dev.mcmod.hdfv.manufacture.StructureWork;
import dev.mcmod.hdfv.manufacture.Workpiece;
import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.neoforge.design.Blueprints;
import dev.mcmod.hdfv.neoforge.material.HdfvMaterials;
import dev.mcmod.hdfv.neoforge.material.Stock;
import dev.mcmod.hdfv.part.engine.SampleEngines;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The workshop in a running server (0.7): a manual machine runs only while someone works it, a lathe refuses a step
 * too fine for it, a piece goes through its route machine by machine, and an assembly bench makes the part. Machines
 * are ticked directly (an hour's work is a thousand ticks).
 */
@PrefixGameTestTemplate(false)
public final class MachineGameTests {
    private MachineGameTests() {}

    /** The carburetted inline four with an older engine's wider fits: every step on manual machines (as the core test). */
    public static PartDesign oldFour() {
        PartDesign d = Designs.set(SampleEngines.inlineFour(), "fuelSystem", "delivery", "carburettor");
        d = Designs.set(d, "piston", "diameterM", "85.92");
        d = Designs.set(d, "rod", "bigEndBoreM", "48.06");
        return Designs.set(d, "rod", "smallEndBoreM", "22.02");
    }

    /**
     * A manual machine tool built whole round its controller at a position (0.9), facing south (it runs east and north:
     * a lathe's bed away to the east of its headstock),
     * of a size (length or courses), driven by its motor seat or its pulley seat, with every accessory it has a cell for,
     * one to a cell; formed.
     */
    public static MachineBlockEntity tool(GameTestHelper h, BlockPos pos, Station s, int size, boolean electric) { return tool(h, pos, s, size, electric, true); }

    /** The same, with or without its accessories. */
    public static MachineBlockEntity tool(GameTestHelper h, BlockPos pos, Station s, int size, boolean electric, boolean accessories) {
        return tool(h, pos, s, size, electric ? "bare" : "bare_pulley", accessories);
    }

    /** The same with a variant of HdfvMultiblocks.machineChoices (0.17: "bare+nc", "bare_pulley+laser"...). */
    public static MachineBlockEntity tool(GameTestHelper h, BlockPos pos, Station s, int size, String variant, boolean accessories) {
        var p = dev.mcmod.hdfv.structure.Structures.machine(s);
        boolean across = p.maxLength() > 1;
        int repeats = across ? 1 : size, length = across ? size : 1;
        BlockPos at = h.absolutePos(pos);
        dev.mcmod.hdfv.neoforge.multiblock.HdfvMultiblocks.place(h.getLevel(), p, at, net.minecraft.core.Direction.SOUTH, repeats, length,
                dev.mcmod.hdfv.neoforge.multiblock.HdfvMultiblocks.machineChoices(p, variant));
        List<dev.mcmod.hdfv.manufacture.ToolConfig.Accessory> left = new ArrayList<>();
        for (var a : dev.mcmod.hdfv.manufacture.ToolConfig.Accessory.values()) { if (accessories && a.fits(s)) { left.add(a); } }
        for (var c : p.cells(at.getX(), at.getY(), at.getZ(), net.minecraft.core.Direction.SOUTH.get2DDataValue(), repeats, length)) {
            var slot = p.slot(c.symbol());
            if (slot == null) { continue; }
            for (var it = left.iterator(); it.hasNext(); ) {
                var a = it.next();
                if (slot.takes(a.kind)) {
                    h.getLevel().setBlock(new BlockPos(c.x(), c.y(), c.z()), dev.mcmod.hdfv.neoforge.multiblock.HdfvMultiblocks.block(a.kind).defaultBlockState(), 3);
                    it.remove();
                    break;
                }
            }
        }
        MachineBlockEntity be = (MachineBlockEntity) h.getLevel().getBlockEntity(at);
        be.multiblock().check(h.getLevel());
        supply(be);
        return be;
    }

    /**
     * Fills a machine's consumable slots (0.17) with what its steps use: cermet tools and a grinding wheel (a lathe, mill,
     * CNC...), honing stones on the boring mill, rivets at the welding station, a family's head's supplies.
     */
    public static void supply(MachineBlockEntity be) {
        List<dev.mcmod.hdfv.manufacture.Consumable.Supply> want = new ArrayList<>();
        var t = be.tool();
        if (t != null && t.head() != null && t.head().uses != null) {
            for (var s : dev.mcmod.hdfv.manufacture.Consumable.Supply.values()) { if (s.supplies == t.head().uses && s != dev.mcmod.hdfv.manufacture.Consumable.Supply.HSS_TOOL_SET) { want.add(s); } }
        } else if (be.station == Station.WELDING_STATION) {
            want.add(dev.mcmod.hdfv.manufacture.Consumable.Supply.STEEL_RIVETS); want.add(dev.mcmod.hdfv.manufacture.Consumable.Supply.ALUMINIUM_RIVETS);
        } else {
            want.add(dev.mcmod.hdfv.manufacture.Consumable.Supply.CERMET_TOOL_SET);
            want.add(be.station == Station.BORING_MILL ? dev.mcmod.hdfv.manufacture.Consumable.Supply.HONING_STONES : dev.mcmod.hdfv.manufacture.Consumable.Supply.GRINDING_WHEEL);
        }
        for (int i = 0; i < be.consumables && i < want.size(); i++) {
            be.items.setItem(be.firstConsumable() + i, dev.mcmod.hdfv.neoforge.machining.HdfvMachining.stack(want.get(i), want.get(i).durable ? 1 : 64));
        }
    }

    /**
     * A machine with a drawing and a creative power source under it (0.8: machines need power); a CNC built whole round
     * its cabinet (facing north: it runs east and south of it) with the 12-axis modules; a manual machine tool (0.9) built
     * whole, 2 long or high, on its motor seat with the creative source under the seat.
     */
    public static MachineBlockEntity machine(GameTestHelper h, BlockPos pos, Station s, PartDesign drawing) {
        if (dev.mcmod.hdfv.manufacture.ToolConfig.STATIONS.contains(s)) {
            MachineBlockEntity be = tool(h, pos, s, 2, true);
            h.getLevel().setBlock(be.drivePos().below(), dev.mcmod.hdfv.neoforge.power.HdfvPower.block(dev.mcmod.hdfv.neoforge.power.PowerBlock.Kind.CREATIVE_POWER)
                    .defaultBlockState(), 3);
            be.items.setItem(MachineBlockEntity.DRAWING, Blueprints.of(drawing));
            return be;
        }
        if (s == Station.CNC) {
            dev.mcmod.hdfv.neoforge.multiblock.HdfvMultiblocks.place(h.getLevel(), dev.mcmod.hdfv.structure.Structures.CNC, h.absolutePos(pos),
                    net.minecraft.core.Direction.NORTH, 1, dev.mcmod.hdfv.neoforge.multiblock.HdfvMultiblocks.cncModules("mill_turn_12"));
        } else {
            h.setBlock(pos, HdfvMachines.MACHINES.get(s).get());
        }
        MachineBlockEntity be = (MachineBlockEntity) h.getBlockEntity(pos);
        if (be.multiblock() != null) { be.multiblock().check(h.getLevel()); }
        h.setBlock(pos.below(), dev.mcmod.hdfv.neoforge.power.HdfvPower.block(dev.mcmod.hdfv.neoforge.power.PowerBlock.Kind.CREATIVE_POWER));
        be.items.setItem(MachineBlockEntity.DRAWING, Blueprints.of(drawing));
        supply(be);
        return be;
    }

    /** A player working a machine: standing by it with its screen open. */
    public static void work(GameTestHelper h, FakePlayer p, MachineBlockEntity be) {
        BlockPos at = be.getBlockPos();
        p.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 1.5);
        p.containerMenu = be.createMenu(1, p.getInventory(), p);
    }

    public static void leave(FakePlayer p) {
        p.containerMenu.removed(p);
        p.containerMenu = p.inventoryMenu;
    }

    /** Ticks a machine until a workpiece or part comes out (or a limit); returns it. */
    static ItemStack runUntilOutput(MachineBlockEntity be, int limit) {
        for (int i = 0; i < limit; i++) {
            be.tick();
            for (int o = be.firstOutput(); o < be.firstOutput() + be.outputs; o++) {
                ItemStack s = be.items.getItem(o);
                if (s.is(HdfvMachines.WORKPIECE.get()) || s.is(HdfvMachines.PART.get())) { be.items.setItem(o, ItemStack.EMPTY); return s; }
            }
        }
        return ItemStack.EMPTY;
    }

    // 0.10: a machine with none of a drawing's work says where it goes next
    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool")
    public static void aMachineWithNothingToDoSaysWhereTheWorkGoes(GameTestHelper h) {
        PartDesign knsb = dev.mcmod.hdfv.part.engine.SampleJets.knsbMotor();
        MachineBlockEntity be = machine(h, new BlockPos(2, 2, 2), Station.WELDING_STATION, knsb);
        be.tick();
        h.assertTrue(be.state() == MachineBlockEntity.State.NO_JOB, "Nothing to weld yet: " + be.state());
        PartPlan plan = Plans.of(knsb);
        String hash = Workpieces.drawingOf(be.items.getItem(MachineBlockEntity.DRAWING)).hash();
        MachineBlockEntity.Next start = MachineBlockEntity.whereNext(Station.WELDING_STATION, plan, hash, List.of());
        h.assertTrue(start.starting() && start.stations().contains(Station.FORGING_PRESS) && !start.stations().contains(Station.WELDING_STATION),
                "New pieces start at the press: " + start);
        ItemStack formed = Workpieces.start(knsb, Workpieces.piece(plan, "casing"), 1);
        MachineBlockEntity.Next next = MachineBlockEntity.whereNext(Station.WELDING_STATION, plan, hash, List.of(formed));
        h.assertTrue(!next.starting() && next.stations().contains(Station.LATHE), "A rolled casing goes to be turned: " + next);
        h.succeed();
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool")
    public static void aManualLatheRunsOnlyWhileSomeoneWorksIt(GameTestHelper h) {
        PartDesign four = oldFour();
        PartPlan plan = Plans.of(four);
        MachineBlockEntity lathe = machine(h, new BlockPos(2, 2, 2), Station.LATHE, four);
        // the first component with a turning step, cast or forged up to it
        Workpiece w = null;
        int at = -1;
        for (Workpiece c : plan.workpieces()) {
            for (int i = 0; i < c.route().size() && w == null; i++) { if (lathe.doable(c.route().get(i), c)) { w = c; at = i; } }
        }
        h.assertTrue(w != null, "Some of the engine is turned");
        lathe.items.setItem(MachineBlockEntity.FIRST_INPUT, Workpieces.start(four, w, at));
        for (int i = 0; i < 20; i++) { lathe.tick(); }
        h.assertTrue(lathe.state == MachineBlockEntity.State.NO_OPERATOR && lathe.progress == 0, "Nobody at the lathe: " + lathe.state + " " + lathe.progress);
        FakePlayer p = FakePlayerFactory.getMinecraft(h.getLevel());
        work(h, p, lathe);
        for (int i = 0; i < 20; i++) { lathe.tick(); }
        h.assertTrue(lathe.state == MachineBlockEntity.State.WORKING && lathe.progress == 20, "Worked 20 ticks: " + lathe.state + " " + lathe.progress);
        // walking away stops it
        p.moveTo(p.getX() + 12, p.getY(), p.getZ());
        lathe.tick();
        h.assertTrue(lathe.state == MachineBlockEntity.State.NO_OPERATOR && lathe.operators() == 0, "Walked away: " + lathe.state);
        work(h, p, lathe);
        ItemStack out = runUntilOutput(lathe, 200_000);
        leave(p);
        Workpieces.Data d = Workpieces.data(out);
        h.assertTrue(d != null && d.key().equals(w.key()) && d.step() == at + lathe.run(w, at), "The lathe's steps of " + w.key() + ": " + d);
        h.assertTrue(lathe.items.getItem(MachineBlockEntity.FIRST_INPUT).isEmpty(), "The piece moved on");
        h.succeed();
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool")
    public static void aLatheRefusesAStepOnlyACncCanDo(GameTestHelper h) {
        // a modern engine's injector: its orifices are drilled finer than a lathe can (and are complex)
        PartDesign four = SampleEngines.inlineFour();
        PartPlan plan = Plans.of(four);
        Workpiece injector = Workpieces.piece(plan, "injector");
        int at = -1;
        for (int i = 0; i < injector.route().size() && at < 0; i++) {
            Operation op = injector.route().get(i);
            if (op.process().cutting() && Station.LATHE.does(op.process()) && !Station.LATHE.can(op) && Station.CNC.can(op)) { at = i; }
        }
        h.assertTrue(at >= 0, "The injector has a step for a CNC only: " + injector.route());
        MachineBlockEntity lathe = machine(h, new BlockPos(2, 2, 2), Station.LATHE, four);
        lathe.items.setItem(MachineBlockEntity.FIRST_INPUT, Workpieces.start(four, injector, at));
        FakePlayer p = FakePlayerFactory.getMinecraft(h.getLevel());
        work(h, p, lathe);
        for (int i = 0; i < 40; i++) { lathe.tick(); }
        leave(p);
        h.assertTrue(lathe.state == MachineBlockEntity.State.CNC_ONLY && lathe.progress == 0, "The lathe refuses: " + lathe.state);
        // a CNC runs it with nobody there
        MachineBlockEntity cnc = machine(h, new BlockPos(4, 2, 2), Station.CNC, four);
        cnc.items.setItem(MachineBlockEntity.FIRST_INPUT, Workpieces.start(four, injector, at));
        for (int i = 0; i < 40; i++) { cnc.tick(); }
        h.assertTrue(cnc.state == MachineBlockEntity.State.WORKING && cnc.progress == 40, "The CNC works unattended: " + cnc.state + " " + cnc.progress);
        ItemStack out = runUntilOutput(cnc, 200_000);
        Workpieces.Data d = Workpieces.data(out);
        h.assertTrue(d != null && d.step() > at, "The CNC did it: " + d);
        h.succeed();
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool")
    public static void aPistonGoesThroughItsRouteMachineByMachine(GameTestHelper h) {
        PartDesign four = oldFour();
        PartPlan plan = Plans.of(four);
        Workpiece piston = Workpieces.piece(plan, "piston");
        h.assertTrue(piston.route().get(0).process() == Process.CAST, "A cast piston: " + piston.route());
        // the foundry pours it from stock (the piston of the list chosen with the buttons)
        MachineBlockEntity foundry = machine(h, new BlockPos(1, 2, 1), Station.FOUNDRY, four);
        List<Workpiece> poured = MachineBlockEntity.startable(Station.FOUNDRY, plan);
        foundry.select(poured.indexOf(piston));
        foundry.items.setItem(MachineBlockEntity.FIRST_INPUT, Stock.of(piston.alloy(), 1));
        ItemStack piece = runUntilOutput(foundry, 200_000);
        h.assertTrue(Workpieces.data(piece) != null && Workpieces.data(piece).key().equals("piston"), "A piston casting: " + piece);
        int bits = 0;
        for (int o = foundry.firstOutput(); o < foundry.firstOutput() + 2; o++) {
            if (foundry.items.getItem(o).is(HdfvMaterials.STOCK_BIT.get())) { bits += foundry.items.getItem(o).getCount(); }
        }
        h.assertTrue(bits == 10 - (int) Math.ceil(piston.stockKg() - 1e-9), "The rest of the stock back as bits: " + bits + " for " + piston.stockKg() + " kg");
        // then each step on the manual machine that does it, in order
        FakePlayer p = FakePlayerFactory.getMinecraft(h.getLevel());
        List<String> went = new ArrayList<>();
        java.util.Map<Station, MachineBlockEntity> built = new java.util.EnumMap<>(Station.class);
        while (!Workpieces.data(piece).finished()) {
            Workpieces.Data d = Workpieces.data(piece);
            Operation op = piston.route().get(d.step());
            Station s = null;
            for (Station c : Station.values()) {
                if (c != Station.CNC && c != Station.ASSEMBLY_ROOM && MachineBlockEntity.doable(c, op)) { s = c; break; }
            }
            h.assertTrue(s != null, "A manual machine for " + op);
            // each machine in its own place (0.9: the machine tools are multiblocks), reused when the route comes back to it
            int k = built.size();
            Station st = s;
            MachineBlockEntity m = built.computeIfAbsent(s, x -> machine(h, new BlockPos(2 + 7 * (k % 3), 3, 2 + 6 * (k / 3)), st, four));
            m.items.setItem(MachineBlockEntity.FIRST_INPUT, piece);
            if (s.manual) { work(h, p, m); }
            piece = runUntilOutput(m, 200_000);
            if (s.manual) { leave(p); }
            Workpieces.Data next = Workpieces.data(piece);
            h.assertTrue(next != null && next.step() == d.step() + m.run(piston, d.step()), s + " did its run of steps: " + d + " -> " + next);
            went.add(s.name().toLowerCase(Locale.ROOT) + "×" + (next.step() - d.step()));
        }
        h.assertTrue(went.size() >= 2, "Through more than one machine: " + went);
        h.succeed();
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool")
    public static void theAssemblyBenchMakesThePartFromEveryFinishedComponent(GameTestHelper h) {
        PartDesign four = oldFour();
        PartPlan plan = Plans.of(four);
        h.assertTrue(plan.workpieces().size() <= MachineBlockEntity.inputsOf(Station.ASSEMBLY_BENCH), "The bench holds every component: " + plan.workpieces().size());
        MachineBlockEntity bench = machine(h, new BlockPos(2, 2, 2), Station.ASSEMBLY_BENCH, four);
        int slot = MachineBlockEntity.FIRST_INPUT;
        for (Workpiece w : plan.workpieces()) {
            ItemStack s = Workpieces.start(four, w, w.route().size());
            s.setCount(w.count());
            bench.items.setItem(slot++, s);
        }
        // one crankshaft short: no engine
        ItemStack crank = bench.items.getItem(MachineBlockEntity.FIRST_INPUT + plan.workpieces().indexOf(Workpieces.piece(plan, "crank")));
        crank.shrink(1);
        FakePlayer p = FakePlayerFactory.getMinecraft(h.getLevel());
        work(h, p, bench);
        bench.tick();
        h.assertTrue(bench.state == MachineBlockEntity.State.MISSING_PARTS, "A component missing: " + bench.state);
        crank.grow(1);
        ItemStack part = runUntilOutput(bench, 500_000);
        leave(p);
        PartDesign made = Workpieces.partDesign(part);
        h.assertTrue(made != null && Designs.write(made).equals(Designs.write(four)), "The engine of the drawing: " + part);
        for (int i = MachineBlockEntity.FIRST_INPUT; i < bench.firstOutput(); i++) { h.assertTrue(bench.items.getItem(i).isEmpty(), "Components used"); }
        // a car body is built in an assembly room, not on a bench
        MachineBlockEntity other = machine(h, new BlockPos(4, 2, 2), Station.ASSEMBLY_BENCH, dev.mcmod.hdfv.part.body.SampleBodies.sedan());
        other.tick();
        h.assertTrue(other.state == MachineBlockEntity.State.ROOM_BUILT, "Bodies go to the room: " + other.state);
        h.succeed();
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool")
    public static void machinesAreCraftedDropThemselvesAndEveryPieceHasAName(GameTestHelper h) throws java.io.IOException {
        var recipes = h.getLevel().getRecipeManager().getRecipes().stream().filter(r -> r.id().getNamespace().equals(Hdfv.MOD_ID)).map(r -> r.id().getPath()).toList();
        BlockPos pos = new BlockPos(1, 2, 1);
        for (var e : HdfvMachines.MACHINES.entrySet()) {
            String id = MachineBlock.id(e.getKey());
            h.assertTrue(recipes.contains(id), "A recipe for the " + id);
            h.setBlock(pos, e.getValue().get());
            var drops = Block.getDrops(h.getBlockState(pos), h.getLevel(), h.absolutePos(pos), null);
            h.assertTrue(drops.size() == 1 && drops.get(0).is(e.getValue().get().asItem()), id + " drops itself: " + drops);
        }
        List<String> keys = new ArrayList<>();
        for (Process pr : Process.values()) { keys.add("hdfv.process." + pr.name().toLowerCase(Locale.ROOT)); }
        for (MachineBlockEntity.State st : MachineBlockEntity.State.values()) { keys.add("hdfv.machine.state." + st.name().toLowerCase(Locale.ROOT)); }
        List<PartDesign> all = new ArrayList<>();
        for (Designs.Kind k : Designs.KINDS) { all.add(k.fresh().get()); }
        for (Designs.Sample s : Designs.SAMPLES) { all.add(s.design().get()); }
        for (PartDesign d : all) {
            PartPlan plan = Plans.of(d);
            for (Workpiece w : plan.workpieces()) { keys.add(Workpiece.nameKey(w.key())); }
            for (StructureWork sw : plan.structure()) { keys.add(Workpiece.nameKey(sw.key())); }
        }
        for (String file : List.of("en_us", "zh_tw")) {
            try (var in = Hdfv.class.getResourceAsStream("/assets/hdfv/lang/" + file + ".json")) {
                h.assertTrue(in != null, "No " + file);
                var lang = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                for (String k : keys) { h.assertTrue(lang.has(k), file + " lacks " + k); }
            }
        }
        h.succeed();
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool")
    public static void aCncFinishesAForgedCrankshaftWithNobodyThere(GameTestHelper h) {
        PartDesign four = SampleEngines.inlineFour();
        PartPlan plan = Plans.of(four);
        Workpiece crank = Workpieces.piece(plan, "crank");
        h.assertTrue(crank.route().get(0).process() == Process.FORGE, "A forged crank: " + crank.route());
        MachineBlockEntity press = machine(h, new BlockPos(1, 2, 1), Station.FORGING_PRESS, four);
        press.select(MachineBlockEntity.startable(Station.FORGING_PRESS, plan).indexOf(crank));
        press.items.setItem(MachineBlockEntity.FIRST_INPUT, Stock.of(crank.alloy(), (int) Math.ceil(crank.stockKg() / 10) + 1));
        ItemStack forged = runUntilOutput(press, 200_000);
        h.assertTrue(Workpieces.data(forged) != null && Workpieces.data(forged).key().equals("crank"), "The press forged it unattended: " + forged);
        MachineBlockEntity cnc = machine(h, new BlockPos(3, 2, 1), Station.CNC, four);
        cnc.items.setItem(MachineBlockEntity.FIRST_INPUT, forged);
        ItemStack done = runUntilOutput(cnc, 500_000);
        Workpieces.Data d = Workpieces.data(done);
        h.assertTrue(d != null && d.step() == d.steps() - 1, "The CNC machined, ground and drilled it in one setting: " + d);
        // 0.17: the journals lapped last, on the finisher (its NC drive: nobody there either)
        MachineBlockEntity finisher = MachiningGameTests.powered(h, new BlockPos(14, 2, 10), Station.FINISHER, 2, "bare+lapping+nc");
        finisher.items.setItem(MachineBlockEntity.DRAWING, Blueprints.of(four));
        finisher.items.setItem(MachineBlockEntity.FIRST_INPUT, done);
        ItemStack lapped = runUntilOutput(finisher, 500_000);
        h.assertTrue(Workpieces.data(lapped) != null && Workpieces.data(lapped).finished(), "Lapped: " + Workpieces.data(lapped) + " " + finisher.state());
        h.succeed();
    }

    /** 0.19: a single-block machine tells the players near what it sounds like when it starts, nothing while steady, and that it stopped. */
    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool", timeoutTicks = 400)
    public static void aWorkingMachineTellsItsSoundOnlyWhenItChanges(GameTestHelper h) {
        PartDesign design = null;
        Workpiece piece = null;
        for (Designs.Sample s : Designs.SAMPLES) {
            for (Workpiece w : Plans.of(s.design().get()).workpieces()) {
                if (piece == null && !w.route().isEmpty() && MachineBlockEntity.doable(Station.VULCANIZER, w.route().get(0))) { design = s.design().get(); piece = w; }
            }
        }
        h.assertTrue(piece != null, "Something starts in the vulcanizer");
        BlockPos at = new BlockPos(1, 2, 3);
        MachineBlockEntity m = machine(h, at, Station.VULCANIZER, design);
        m.select(MachineBlockEntity.startable(Station.VULCANIZER, Plans.of(design)).indexOf(piece));
        m.items.setItem(MachineBlockEntity.FIRST_INPUT, Stock.of(piece.alloy(), Math.min((int) Math.ceil(piece.stockKg() / 10) + 1, 64)));
        BlockPos abs = h.absolutePos(at);
        int[] sends = {0};
        h.startSequence()
                .thenWaitUntil(() -> {
                    var hum = dev.mcmod.hdfv.neoforge.sound.SoundSync.sent(h.getLevel(), abs);
                    h.assertTrue(m.state == MachineBlockEntity.State.WORKING && hum != null && hum.running()
                            && hum.process() == dev.mcmod.aerocore.api.acoustics.ProcessNoise.Process.STEAM, "Tells it works (steam): " + m.state + " " + hum);
                    sends[0] = dev.mcmod.hdfv.neoforge.sound.SoundSync.sendsAt(h.getLevel(), abs);
                })
                .thenIdle(40)
                .thenExecute(() -> {
                    h.assertTrue(dev.mcmod.hdfv.neoforge.sound.SoundSync.sendsAt(h.getLevel(), abs) == sends[0], "Steady, it sends nothing more: "
                            + (dev.mcmod.hdfv.neoforge.sound.SoundSync.sendsAt(h.getLevel(), abs) - sends[0]));
                    m.items.setItem(MachineBlockEntity.DRAWING, ItemStack.EMPTY);
                })
                .thenWaitUntil(() -> h.assertTrue(dev.mcmod.hdfv.neoforge.sound.SoundSync.sent(h.getLevel(), abs) == null, "Silent once it stops: " + m.state))
                .thenSucceed();
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool")
    public static void theCompositeOvenAndTheVulcanizerRunOnTheirOwn(GameTestHelper h) {
        List<PartDesign> all = new ArrayList<>();
        for (Designs.Kind k : Designs.KINDS) { all.add(k.fresh().get()); }
        for (Designs.Sample s : Designs.SAMPLES) { all.add(s.design().get()); }
        int x = 1;
        for (Station st : List.of(Station.COMPOSITE_OVEN, Station.VULCANIZER)) {
            PartDesign design = null;
            Workpiece piece = null;
            for (PartDesign d : all) {
                for (Workpiece w : Plans.of(d).workpieces()) {
                    if (piece == null && !w.route().isEmpty() && MachineBlockEntity.doable(st, w.route().get(0))) { design = d; piece = w; }
                }
            }
            h.assertTrue(piece != null, "Something starts in the " + st);
            MachineBlockEntity m = machine(h, new BlockPos(x, 2, 3), st, design);
            x += 2;
            m.select(MachineBlockEntity.startable(st, Plans.of(design)).indexOf(piece));
            m.items.setItem(MachineBlockEntity.FIRST_INPUT, Stock.of(piece.alloy(), Math.min((int) Math.ceil(piece.stockKg() / 10) + 1, 64)));
            ItemStack out = runUntilOutput(m, 500_000);
            Workpieces.Data d = Workpieces.data(out);
            h.assertTrue(d != null && d.key().equals(piece.key()) && d.step() == m.run(piece, 0), st + " made " + piece.key() + " of " + design.name() + ": " + d);
        }
        h.succeed();
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool")
    public static void aWelderTradesAndWorksAWeldingStation(GameTestHelper h) throws java.io.IOException {
        var welder = dev.mcmod.hdfv.neoforge.villager.HdfvVillagers.WELDER.get();
        var trades = net.minecraft.world.entity.npc.VillagerTrades.TRADES.get(welder);
        h.assertTrue(trades != null, "The welder has trades");
        for (int level = 1; level <= 5; level++) { h.assertTrue(trades.get(level) != null && trades.get(level).length >= 2, "Trades at level " + level); }
        // the welding station is the welder's job site
        Block station = HdfvMachines.MACHINES.get(Station.WELDING_STATION).get();
        var poi = net.minecraft.world.entity.ai.village.poi.PoiTypes.forState(station.defaultBlockState());
        h.assertTrue(poi.isPresent() && poi.get().is(dev.mcmod.hdfv.neoforge.villager.HdfvVillagers.WELDER_POI), "The welding station is a job site: " + poi);
        // a welded piece: formed sheet, then welded
        PartDesign four = oldFour();
        PartPlan plan = Plans.of(four);
        Workpiece sheet = null;
        int at = -1;
        for (Workpiece w : plan.workpieces()) {
            for (int i = 0; i < w.route().size() && sheet == null; i++) { if (w.route().get(i).process() == Process.WELD) { sheet = w; at = i; } }
        }
        h.assertTrue(sheet != null, "Something of the engine is welded");
        MachineBlockEntity m = machine(h, new BlockPos(2, 2, 2), Station.WELDING_STATION, four);
        m.items.setItem(MachineBlockEntity.FIRST_INPUT, Workpieces.start(four, sheet, at));
        for (int i = 0; i < 25; i++) { m.tick(); }
        h.assertTrue(m.state == MachineBlockEntity.State.NO_OPERATOR, "Nobody welds: " + m.state);
        var v = h.spawn(net.minecraft.world.entity.EntityType.VILLAGER, new BlockPos(2, 2, 3));
        v.setVillagerData(v.getVillagerData().setProfession(welder).setLevel(2));
        h.assertTrue(!v.getOffers().isEmpty(), "A welder offers trades");
        ItemStack out = runUntilOutput(m, 200_000);
        Workpieces.Data d = Workpieces.data(out);
        h.assertTrue(d != null && d.step() > at, "The welder welded it: " + d);
        v.discard();
        m.items.setItem(MachineBlockEntity.FIRST_INPUT, Workpieces.start(four, sheet, at));
        for (int i = 0; i < 25; i++) { m.tick(); }
        h.assertTrue(m.state == MachineBlockEntity.State.NO_OPERATOR, "The welder left: " + m.state);
        for (String file : List.of("en_us", "zh_tw")) {
            try (var in = Hdfv.class.getResourceAsStream("/assets/hdfv/lang/" + file + ".json")) {
                var lang = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                h.assertTrue(lang.has("entity.minecraft.villager.hdfv.welder"), file + " names the welder");
            }
        }
        h.assertTrue(Hdfv.class.getResource("/assets/hdfv/textures/entity/villager/profession/welder.png") != null, "The welder's clothes");
        h.succeed();
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool", timeoutTicks = 200)
    public static void hoppersFeedStockInAndTakeWorkOut(GameTestHelper h) {
        PartDesign four = SampleEngines.inlineFour();
        MachineBlockEntity cnc = machine(h, new BlockPos(2, 2, 2), Station.CNC, four);
        var items = h.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, cnc.getBlockPos(), net.minecraft.core.Direction.UP);
        // 0.17: its tool magazine (the consumable slots) first, then the inputs and outputs
        int in = cnc.consumables, out = cnc.consumables + cnc.inputs;
        h.assertTrue(items != null && items.getSlots() == cnc.consumables + cnc.inputs + cnc.outputs, "The CNC has an item handler");
        h.assertTrue(items.insertItem(in, Blueprints.of(four), false).getCount() == 1, "Drawings are not piped in");
        h.assertTrue(items.insertItem(0, Stock.of(dev.mcmod.hdfv.manufacture.Alloy.STEEL, 1), true).getCount() == 1, "Stock does not go into the tool magazine");
        h.assertTrue(items.insertItem(out, Stock.of(dev.mcmod.hdfv.manufacture.Alloy.STEEL, 1), true).getCount() == 1, "Nothing goes into an output");
        cnc.items.setItem(cnc.firstOutput(), new ItemStack(HdfvMachines.WORKPIECE.get()));
        h.assertTrue(items.extractItem(0, 1, true).isEmpty() && items.extractItem(in, 1, true).isEmpty() && items.extractItem(out, 1, false).getCount() == 1,
                "Work comes out of the outputs only");
        // a real hopper on top
        BlockPos hopper = new BlockPos(2, 3, 2);
        h.setBlock(hopper, net.minecraft.world.level.block.Blocks.HOPPER);
        var hb = (net.minecraft.world.level.block.entity.HopperBlockEntity) h.getBlockEntity(hopper);
        hb.setItem(0, Stock.of(dev.mcmod.hdfv.manufacture.Alloy.STEEL, 2));
        h.succeedWhen(() -> {
            int n = 0;
            for (int i = MachineBlockEntity.FIRST_INPUT; i < cnc.firstOutput(); i++) {
                if (Stock.alloy(cnc.items.getItem(i)) == dev.mcmod.hdfv.manufacture.Alloy.STEEL) { n += cnc.items.getItem(i).getCount(); }
            }
            h.assertTrue(n == 2, "The hopper fed the CNC: " + n);
        });
    }
}
