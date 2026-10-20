package dev.mcmod.hdfv.neoforge.machine;

import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.hdfv.manufacture.Consumable;
import dev.mcmod.hdfv.manufacture.Head;
import dev.mcmod.hdfv.manufacture.MachineFrame;
import dev.mcmod.hdfv.manufacture.PartPlan;
import dev.mcmod.hdfv.manufacture.Plans;
import dev.mcmod.hdfv.manufacture.Process;
import dev.mcmod.hdfv.manufacture.Routes;
import dev.mcmod.hdfv.manufacture.Station;
import dev.mcmod.hdfv.manufacture.ToolConfig;
import dev.mcmod.hdfv.manufacture.Workpiece;
import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.neoforge.HdfvConfig;
import dev.mcmod.hdfv.neoforge.machining.AirSeparatorBlockEntity;
import dev.mcmod.hdfv.neoforge.machining.HdfvMachining;
import dev.mcmod.hdfv.neoforge.multiblock.HdfvMultiblocks;
import dev.mcmod.hdfv.neoforge.power.HdfvPower;
import dev.mcmod.hdfv.neoforge.power.PowerBlock;
import dev.mcmod.hdfv.structure.Structures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * The machining families, their consumables and the air separation unit in a running server (0.17): every family formed
 * with each of its heads, a step done with its supply worn, an old lathe stopping for want of tools, the NC drive running
 * alone, an electric-only head on a pulley seat, an older workpiece moved onto the new route, the tool grinder's regrinds.
 */
@PrefixGameTestTemplate(false)
public final class MachiningGameTests {
    private MachiningGameTests() {}

    /** A family or machine tool built whole with a variant, on creative power under its drive. */
    static MachineBlockEntity powered(GameTestHelper h, BlockPos pos, Station s, int size, String variant) {
        MachineBlockEntity be = MachineGameTests.tool(h, pos, s, size, variant, true);
        if (be.drivePos() != null) {
            h.getLevel().setBlock(be.drivePos().below(), HdfvPower.block(PowerBlock.Kind.CREATIVE_POWER).defaultBlockState(), Block.UPDATE_ALL);
        }
        return be;
    }

    /** The cell of a machine's head. */
    static BlockPos headCell(MachineBlockEntity m) {
        var c = m.multiblock().match().cells().stream().filter(x -> x.symbol() == 'h').findFirst().orElseThrow();
        return new BlockPos(c.x(), c.y(), c.z());
    }

    /** A workpiece of a plan and the step a machine can do next on it, of a process (null: none). */
    static Object[] next(MachineBlockEntity be, PartPlan plan, Process p) {
        for (Workpiece w : plan.workpieces()) {
            for (int i = 0; i < w.route().size(); i++) {
                if (w.route().get(i).process() == p && be.run(w, i) > 0) { return new Object[]{w, i}; }
            }
        }
        return null;
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "helipad")
    public static void everyFamilyFormsWithEachHeadAndFallsApart(GameTestHelper h) {
        for (int x = 0; x < 28; x++) for (int z = 0; z < 28; z++) { h.setBlock(new BlockPos(x, 0, z), Blocks.STONE); }
        int k = 0;
        for (Station s : ToolConfig.STATIONS) {
            if (!s.family) { continue; }
            MachineFrame f = MachineFrame.of(s);
            int size = Math.min(2, f.maxSize);
            MachineBlockEntity m = MachineGameTests.tool(h, new BlockPos(2 + 7 * (k % 4), 2, 2 + 7 * (k / 4)), s, size, k % 2 == 0 ? "bare" : "bare+nc", true);
            k++;
            h.assertTrue(m.formed() && m.tool() != null, s + " formed: " + m.state());
            h.assertTrue(m.tool().size() == size && m.tool().nc() == (k % 2 == 0), s + " size " + size + " and drive: " + m.tool());
            BlockPos head = headCell(m);
            for (Head hd : Head.of(s)) {
                h.getLevel().setBlock(head, HdfvMultiblocks.block(hd.kind).defaultBlockState(), Block.UPDATE_ALL);
                m.multiblock().check(h.getLevel());
                ToolConfig t = m.tool();
                h.assertTrue(t != null && t.head() == hd, s + " takes the " + hd + ": " + t);
                h.assertTrue(t.powerW() == hd.powerW && t.manual() == (s.manual && !t.nc()), hd + " runs as its head says: " + t);
            }
            // a member taken away: it falls apart
            String members = f.members;
            var cell = m.multiblock().match().cells().stream().filter(c -> members.indexOf(c.symbol()) >= 0).findFirst().orElseThrow();
            h.getLevel().setBlock(new BlockPos(cell.x(), cell.y(), cell.z()), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            m.tickForTest();
            h.assertTrue(!m.formed() && m.state() == MachineBlockEntity.State.NOT_FORMED, s + " fell apart: " + m.state());
        }
        h.assertTrue(k == 9, "Nine families: " + k);
        h.succeed();
    }

    // as /hdfv dev place puts them: its default drive and accessories, the head by name
    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool")
    public static void cuttingTablesPlacedByNameForm(GameTestHelper h) {
        int k = 0;
        for (String head : List.of("laser", "plasma", "waterjet")) {
            BlockPos at = h.absolutePos(new BlockPos(2 + 6 * k, 3, 3));
            var p = Structures.machine(Station.CUTTING_TABLE);
            HdfvMultiblocks.place(h.getLevel(), p, at, net.minecraft.core.Direction.SOUTH, 1, 2, HdfvMultiblocks.machineChoices(p, head));
            MachineBlockEntity be = (MachineBlockEntity) h.getLevel().getBlockEntity(at);
            be.multiblock().check(h.getLevel());
            h.assertTrue(be.formed() && be.tool() != null && be.tool().head().kind.equals(head + "_head"),
                    head + " table formed: " + be.formed() + " " + be.tool() + " " + HdfvMultiblocks.machineChoices(p, head));
            k++;
        }
        h.succeed();
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool")
    public static void aGrinderGrindsAndWearsItsWheel(GameTestHelper h) {
        PartDesign four = MachineGameTests.oldFour();
        PartPlan plan = Plans.of(four);
        MachineBlockEntity grinder = MachineGameTests.machine(h, new BlockPos(2, 3, 2), Station.GRINDER, four);
        h.assertTrue(grinder.tool() != null && grinder.tool().head() == Head.CYLINDRICAL_GRINDING, "A cylindrical grinder: " + grinder.tool());
        Object[] at = next(grinder, plan, Process.GRIND);
        h.assertTrue(at != null, "Something of the four to grind");
        Workpiece w = (Workpiece) at[0];
        grinder.items.setItem(MachineBlockEntity.FIRST_INPUT, Workpieces.start(four, w, (int) at[1]));
        grinder.tick();
        h.assertTrue(grinder.state() == MachineBlockEntity.State.NO_OPERATOR, "A manual grinder waits for its operator: " + grinder.state());
        FakePlayer p = FakePlayerFactory.getMinecraft(h.getLevel());
        MachineGameTests.work(h, p, grinder);
        ItemStack out = MachineGameTests.runUntilOutput(grinder, 50000);
        MachineGameTests.leave(p);
        h.assertTrue(!out.isEmpty(), "The " + w.key() + " ground: " + grinder.state());
        Workpieces.Data d = Workpieces.data(out);
        h.assertTrue(d != null && d.step() > (int) at[1], "A step further: " + d);
        ItemStack wheel = grinder.items.getItem(grinder.firstConsumable());
        h.assertTrue(HdfvMachining.supplyOf(wheel) == Consumable.Supply.GRINDING_WHEEL, "The wheel is where it was: " + wheel);
        h.assertTrue(wheel.getDamageValue() > 0 || grinder.owed[Consumable.GRINDING_WHEEL.ordinal()] > 0,
                "The wheel wore: " + wheel.getDamageValue() + ", owed " + grinder.owed[Consumable.GRINDING_WHEEL.ordinal()]);
        h.succeed();
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool")
    public static void anOldLatheStopsWithoutToolsAndGoesOnWithThem(GameTestHelper h) {
        PartDesign four = MachineGameTests.oldFour();
        PartPlan plan = Plans.of(four);
        MachineBlockEntity lathe = MachineGameTests.machine(h, new BlockPos(2, 3, 2), Station.LATHE, four);
        Object[] at = next(lathe, plan, Process.TURN);
        h.assertTrue(at != null, "Something of the four to turn");
        lathe.items.setItem(MachineBlockEntity.FIRST_INPUT, Workpieces.start(four, (Workpiece) at[0], (int) at[1]));
        for (int i = lathe.firstConsumable(); i < lathe.firstConsumable() + lathe.consumables; i++) { lathe.items.setItem(i, ItemStack.EMPTY); }
        FakePlayer p = FakePlayerFactory.getMinecraft(h.getLevel());
        MachineGameTests.work(h, p, lathe);
        lathe.tick();
        h.assertTrue(lathe.state() == MachineBlockEntity.State.NO_TOOL && lathe.missingConsumable == Consumable.CUTTING_TOOLS,
                "Out of tools: " + lathe.state() + ", " + lathe.missingConsumable);
        // a hopper's way in: a tool set goes to the consumable slots, and does not come out again
        var handler = lathe.handler();
        ItemStack left = handler.insertItem(0, HdfvMachining.stack(Consumable.Supply.HSS_TOOL_SET, 1), false);
        h.assertTrue(left.isEmpty() && HdfvMachining.supplyOf(lathe.items.getItem(lathe.firstConsumable())) == Consumable.Supply.HSS_TOOL_SET, "The tool set went in: " + left);
        h.assertTrue(handler.extractItem(0, 1, true).isEmpty(), "Tools are not taken out by a hopper");
        h.assertTrue(!handler.insertItem(lathe.consumables + 1, HdfvMachining.stack(Consumable.Supply.CERMET_TOOL_SET, 1), true).isEmpty(),
                "Tools do not go in with the work");
        lathe.tick();
        h.assertTrue(lathe.state() == MachineBlockEntity.State.WORKING, "Turning again: " + lathe.state());
        MachineGameTests.leave(p);
        h.succeed();
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool")
    public static void theNcDriveRunsAMachineWithNoOneAtIt(GameTestHelper h) {
        PartDesign four = MachineGameTests.oldFour();
        PartPlan plan = Plans.of(four);
        MachineBlockEntity grinder = powered(h, new BlockPos(2, 3, 2), Station.GRINDER, 2, "bare+nc");
        h.assertTrue(grinder.tool() != null && grinder.tool().nc() && !grinder.tool().manual() && grinder.electric(), "An NC grinder: " + grinder.tool());
        grinder.items.setItem(MachineBlockEntity.DRAWING, dev.mcmod.hdfv.neoforge.design.Blueprints.of(four));
        Object[] at = next(grinder, plan, Process.GRIND);
        h.assertTrue(at != null, "Something of the four to grind");
        grinder.items.setItem(MachineBlockEntity.FIRST_INPUT, Workpieces.start(four, (Workpiece) at[0], (int) at[1]));
        for (int i = 0; i < 3; i++) { grinder.tick(); }
        h.assertTrue(grinder.state() == MachineBlockEntity.State.WORKING, "Working with no one at it: " + grinder.state());
        // the 0.9 lathe on the NC drive too
        MachineBlockEntity lathe = powered(h, new BlockPos(2, 3, 9), Station.LATHE, 2, "bare+nc");
        h.assertTrue(lathe.tool() != null && lathe.tool().nc() && !lathe.tool().manual(), "An NC lathe: " + lathe.tool());
        h.succeed();
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool")
    public static void anElectricHeadOnAPulleySeatAsksForElectricity(GameTestHelper h) {
        MachineBlockEntity table = MachineGameTests.tool(h, new BlockPos(2, 3, 2), Station.CUTTING_TABLE, 2, "bare_pulley+laser", true);
        h.assertTrue(table.formed() && table.tool() != null && table.tool().head() == Head.LASER && !table.electric(), "A laser table on a pulley seat: " + table.tool());
        table.tickForTest();
        h.assertTrue(table.state() == MachineBlockEntity.State.NEEDS_ELECTRIC, "The laser wants electricity: " + table.state());
        h.getLevel().setBlock(headCell(table), HdfvMultiblocks.block(Head.WATERJET.kind).defaultBlockState(), Block.UPDATE_ALL);
        table.multiblock().check(h.getLevel());
        table.tickForTest();
        h.assertTrue(table.tool().head() == Head.WATERJET && table.state() != MachineBlockEntity.State.NEEDS_ELECTRIC, "A waterjet's pump runs off a shaft: " + table.state());
        // the EDM's drive cell takes no pulley seat at all
        MachineBlockEntity edm = MachineGameTests.tool(h, new BlockPos(2, 3, 9), Station.ELECTRO_MACHINE, 2, "bare_pulley", true);
        h.assertTrue(!edm.formed(), "No EDM on a pulley seat");
        h.succeed();
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool")
    public static void anOlderWorkpieceMovesOntoTheNewRoute(GameTestHelper h) {
        // a sample with a step inserted before its last old one (a bar sawn off after it is formed, a hole tapped after it is drilled)
        PartDesign design = null;
        Workpiece w = null;
        int s = -1, old = 0;
        for (var sample : dev.mcmod.hdfv.design.Designs.SAMPLES) {
            PartDesign d = sample.design().get();
            for (Workpiece c : Plans.of(d).workpieces()) {
                int n = Routes.legacy(c).size();
                for (int i = 1; i < n && w == null; i++) { if (Routes.migrate(c, i, n) != i) { design = d; w = c; s = i; old = n; } }
            }
            if (w != null) { break; }
        }
        h.assertTrue(w != null, "A sample piece with a step inserted");
        MachineBlockEntity lathe = MachineGameTests.machine(h, new BlockPos(2, 3, 2), Station.LATHE, design);
        Workpieces.Data now = Workpieces.data(Workpieces.start(design, w, s));
        Workpieces.Data before = new Workpieces.Data(now.hash(), now.design(), now.type(), now.key(), s, old, 0);
        h.assertTrue(before.older(), "Made on 0.16's route");
        ItemStack piece = new ItemStack(HdfvMachines.WORKPIECE.get());
        piece.set(HdfvMachines.WORKPIECE_DATA.get(), before);
        lathe.items.setItem(MachineBlockEntity.FIRST_INPUT, piece);
        lathe.tick();
        Workpieces.Data after = Workpieces.data(lathe.items.getItem(MachineBlockEntity.FIRST_INPUT));
        h.assertTrue(after != null && !after.older() && after.route() == Routes.VERSION, "On the new route: " + after);
        h.assertTrue(after.step() == Routes.migrate(w, s, old) && after.steps() == w.route().size(),
                w.key() + ": old step " + s + " of " + old + " is " + after.step() + " of " + after.steps());
        h.succeed();
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool")
    public static void theToolGrinderRegrindsADullSetUpToItsRegrinds(GameTestHelper h) {
        MachineBlockEntity grinder = powered(h, new BlockPos(2, 3, 2), Station.GRINDER, 2, "bare+tool_grinding");
        h.assertTrue(grinder.tool() != null && grinder.tool().head() == Head.TOOL_GRINDING, "A tool grinder: " + grinder.tool());
        ItemStack dull = HdfvMachining.stack(Consumable.Supply.HSS_TOOL_SET, 1);
        dull.setDamageValue(Consumable.LIFE_UNITS - 1);
        dull.set(HdfvMachining.REGRINDS.get(), 2);
        grinder.items.setItem(MachineBlockEntity.FIRST_INPUT, dull);
        FakePlayer p = FakePlayerFactory.getMinecraft(h.getLevel());
        MachineGameTests.work(h, p, grinder);
        ItemStack sharp = ItemStack.EMPTY;
        for (int i = 0; i < 20000 && sharp.isEmpty(); i++) {
            grinder.tick();
            for (int o = grinder.firstOutput(); o < grinder.firstOutput() + grinder.outputs; o++) {
                if (HdfvMachining.supplyOf(grinder.items.getItem(o)) == Consumable.Supply.HSS_TOOL_SET) { sharp = grinder.items.getItem(o).copy(); grinder.items.setItem(o, ItemStack.EMPTY); }
            }
        }
        h.assertTrue(!sharp.isEmpty() && sharp.getDamageValue() == 0 && sharp.getOrDefault(HdfvMachining.REGRINDS.get(), 0) == 3,
                "Reground a third time: " + sharp + " " + sharp.getDamageValue());
        h.assertTrue(grinder.items.getItem(MachineBlockEntity.FIRST_INPUT).isEmpty(), "The dull set was taken");
        // one past its regrinds: scrapped
        ItemStack spent = HdfvMachining.stack(Consumable.Supply.HSS_TOOL_SET, 1);
        spent.setDamageValue(Consumable.LIFE_UNITS - 1);
        spent.set(HdfvMachining.REGRINDS.get(), Consumable.Supply.HSS_TOOL_SET.regrinds);
        grinder.items.setItem(MachineBlockEntity.FIRST_INPUT, spent);
        for (int i = 0; i < 20000 && !grinder.items.getItem(MachineBlockEntity.FIRST_INPUT).isEmpty(); i++) { grinder.tick(); }
        h.assertTrue(grinder.items.getItem(MachineBlockEntity.FIRST_INPUT).isEmpty(), "The spent set was used up");
        for (int o = grinder.firstOutput(); o < grinder.firstOutput() + grinder.outputs; o++) {
            h.assertTrue(HdfvMachining.supplyOf(grinder.items.getItem(o)) == null, "Nothing came of it: " + grinder.items.getItem(o));
        }
        MachineGameTests.leave(p);
        h.succeed();
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool")
    public static void theAirSeparationUnitFillsCylindersForItsElectricity(GameTestHelper h) {
        BlockPos pos = new BlockPos(3, 2, 3);
        h.setBlock(pos, HdfvMachining.AIR_SEPARATOR.get());
        AirSeparatorBlockEntity be = (AirSeparatorBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(pos));
        be.tick();
        h.assertTrue(be.state() == AirSeparatorBlockEntity.State.NO_CYLINDER, "No cylinder yet: " + be.state());
        h.assertTrue(be.handler().insertItem(AirSeparatorBlockEntity.EMPTIES, new ItemStack(HdfvMachining.EMPTY_CYLINDER.get(), 2), false).isEmpty(), "Two empties in");
        be.tick();
        h.assertTrue(be.state() == AirSeparatorBlockEntity.State.NO_POWER, "No power: " + be.state());
        long fe = 0;
        int ticks = 0;
        while (be.items().getItem(AirSeparatorBlockEntity.FULLS).isEmpty() && ticks < 100000) {
            be.energy().receiveEnergy(AirSeparatorBlockEntity.BUFFER_FE, false);
            int before = be.energy().getEnergyStored();
            be.tick();
            fe += before - be.energy().getEnergyStored();
            ticks++;
        }
        var o2 = dev.mcmod.hdfv.manufacture.GasSupply.Product.OXYGEN;
        ItemStack full = be.items().getItem(AirSeparatorBlockEntity.FULLS);
        h.assertTrue(HdfvMachining.supplyOf(full) == Consumable.Supply.OXYGEN && full.getDamageValue() == 0, "A full oxygen cylinder: " + full);
        double wantTicks = o2.cylinderKg() / o2.kgPerHour() * HdfvConfig.ticksPerWorkHour();
        double wantFe = o2.cylinderKg() * o2.joulesPerKg() / HdfvConfig.joulesPerFE();
        h.assertTrue(Math.abs(ticks - wantTicks) < 0.02 * wantTicks + 2, "Its hours: " + ticks + " ticks, want " + wantTicks);
        h.assertTrue(Math.abs(fe - wantFe) < 0.02 * wantFe + 2, "Its electricity: " + fe + " FE, want " + wantFe);
        be.tick();
        h.assertTrue(be.state() == AirSeparatorBlockEntity.State.FULL, "Waits for the full one to be taken: " + be.state());
        h.assertTrue(be.handler().extractItem(AirSeparatorBlockEntity.FULLS, 16, false).getCount() == 1, "Taken by a hopper");
        be.switchGas();
        h.assertTrue(be.product() == dev.mcmod.hdfv.manufacture.GasSupply.Product.NITROGEN, "Switched to nitrogen");
        h.succeed();
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool")
    public static void theMachiningBlocksAndSuppliesAreMadeDroppedAndNamed(GameTestHelper h) throws java.io.IOException {
        var recipes = h.getLevel().getRecipeManager().getRecipes().stream().filter(r -> r.id().getNamespace().equals(Hdfv.MOD_ID)).map(r -> r.id().getPath()).toList();
        List<String> blocks = new ArrayList<>();
        for (Station s : ToolConfig.STATIONS) { if (s.family) { blocks.add(MachineBlock.id(s)); } }
        for (Head hd : Head.values()) { blocks.add(hd.kind); }
        blocks.addAll(List.of(Structures.GRINDER_BED, Structures.FINISHER_TABLE, Structures.FINISHER_COLUMN, Structures.FINISHER_TOP, Structures.GEAR_TABLE,
                Structures.GEAR_COLUMN, Structures.GEAR_TOP, Structures.RAM_BED, Structures.SAW_BED, Structures.EDM_TANK, Structures.CUTTING_SLATS,
                Structures.THREADER_BASE, Structures.RIVETER_FRAME, Structures.NC_DRIVE, "air_separation_unit"));
        for (ToolConfig.Accessory a : ToolConfig.Accessory.values()) { blocks.add(a.kind); }
        BlockPos p = new BlockPos(4, 2, 4);
        for (String id : blocks) {
            Block b = BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Hdfv.MOD_ID, id));
            h.assertTrue(b != Blocks.AIR, "A block " + id);
            h.assertTrue(recipes.contains(id), "A recipe for the " + id);
            h.setBlock(p, b);
            var drops = Block.getDrops(h.getBlockState(p), h.getLevel(), h.absolutePos(p), h.getLevel().getBlockEntity(h.absolutePos(p)));
            h.assertTrue(drops.stream().anyMatch(s -> s.is(b.asItem())), id + " drops itself: " + drops);
            h.setBlock(p, Blocks.AIR);
        }
        for (String id : HdfvMachining.ids()) {
            boolean filled = id.endsWith("_cylinder") && !id.equals("empty_gas_cylinder");
            h.assertTrue(filled != recipes.contains(id), id + (filled ? " is filled at the air separation unit" : " has a recipe"));
        }
        h.setBlock(p, BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Hdfv.MOD_ID, "garnet_ore")));
        var ore = Block.getDrops(h.getBlockState(p), h.getLevel(), h.absolutePos(p), null);
        h.assertTrue(ore.stream().anyMatch(s -> BuiltInRegistries.ITEM.getKey(s.getItem()).getPath().equals("raw_garnet")), "Garnet ore gives raw garnet: " + ore);
        for (String file : List.of("en_us", "zh_tw")) {
            try (var in = Hdfv.class.getResourceAsStream("/assets/hdfv/lang/" + file + ".json")) {
                var lang = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                for (String id : blocks) { h.assertTrue(lang.has("block.hdfv." + id), file + " lacks block " + id); }
                for (Consumable.Supply s : Consumable.Supply.values()) { h.assertTrue(lang.has("item.hdfv." + s.id), file + " lacks " + s.id); }
                for (Consumable c : Consumable.values()) { h.assertTrue(lang.has("hdfv.consumable." + c.id()), file + " lacks consumable " + c.id()); }
                for (String k : List.of("item.hdfv.empty_gas_cylinder", "item.hdfv.raw_garnet", "block.hdfv.garnet_ore", "block.hdfv.deepslate_garnet_ore",
                        "hdfv.machine.state.no_tool", "hdfv.machine.state.needs_electric", "hdfv.machine.no_head", "hdfv.machine.family", "hdfv.machine.family_high",
                        "hdfv.machine.consumables", "hdfv.machine.needs_consumable", "hdfv.drive.numerical", "hdfv.workpiece.older", "hdfv.consumable.for",
                        "hdfv.consumable.regrinds", "hdfv.air_separator.status", "hdfv.gas.oxygen", "hdfv.gas.nitrogen", "hdfv.gas.argon", "hdfv.jei.machining",
                        "hdfv.jei.machining.grade", "hdfv.jei.heads", "hdfv.jei.supplies", "hdfv.jei.air_separation_unit", "hdfv.jei.drive.nc_drive")) {
                    h.assertTrue(lang.has(k), file + " lacks " + k);
                }
                for (Process pr : Process.values()) { h.assertTrue(lang.has("hdfv.process." + pr.name().toLowerCase(java.util.Locale.ROOT)), file + " lacks process " + pr); }
            }
        }
        h.succeed();
    }
}
