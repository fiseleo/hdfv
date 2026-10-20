package dev.mcmod.hdfv.neoforge.multiblock;

import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.hdfv.manufacture.CncConfig;
import dev.mcmod.hdfv.manufacture.Operation;
import dev.mcmod.hdfv.manufacture.PartPlan;
import dev.mcmod.hdfv.manufacture.Plans;
import dev.mcmod.hdfv.manufacture.Station;
import dev.mcmod.hdfv.manufacture.Workpiece;
import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.neoforge.machine.MachineBlockEntity;
import dev.mcmod.hdfv.neoforge.machine.MachineGameTests;
import dev.mcmod.hdfv.neoforge.machine.Workpieces;
import dev.mcmod.hdfv.part.engine.SampleEngines;
import dev.mcmod.hdfv.structure.Pattern;
import dev.mcmod.hdfv.structure.Structures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** Multiblocks in a running server (0.8): forming, dissolving with the work kept, forming again; every CNC preset; a lone cabinet waits. */
@PrefixGameTestTemplate(false)
public final class MultiblockGameTests {
    private MultiblockGameTests() {}

    /** An injector piece at its first step only a CNC does, for a CNC to work on. */
    static Workpieces.Data injectorAtCncStep(MachineBlockEntity cnc, PartDesign four) {
        PartPlan plan = Plans.of(four);
        Workpiece injector = plan.workpieces().stream().filter(w -> w.key().equals("injector")).findFirst().orElseThrow();
        int at = -1;
        for (int i = 0; i < injector.route().size() && at < 0; i++) {
            Operation op = injector.route().get(i);
            if (op.process().cutting() && !Station.LATHE.can(op) && Station.CNC.can(op)) { at = i; }
        }
        cnc.items().setItem(MachineBlockEntity.FIRST_INPUT, Workpieces.start(four, injector, at));
        return Workpieces.data(cnc.items().getItem(MachineBlockEntity.FIRST_INPUT));
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool")
    public static void aCncFormsDissolvesKeepsItsWorkAndFormsAgain(GameTestHelper h) {
        PartDesign four = SampleEngines.inlineFour();
        MachineBlockEntity cnc = MachineGameTests.machine(h, new BlockPos(2, 2, 2), Station.CNC, four);
        h.assertTrue(cnc.formed(), "The CNC formed round its cabinet");
        Pattern.Match m = cnc.multiblock().match();
        h.assertTrue(Structures.cncConfig(m) != null && Structures.cncConfig(m).axes() == 12, "Twelve axes: " + m.counts());
        BlockPos frame = null;
        for (Pattern.Cell c : m.cells()) {
            BlockPos p = new BlockPos(c.x(), c.y(), c.z());
            if (p.equals(cnc.getBlockPos())) { continue; }
            BlockState s = h.getLevel().getBlockState(p);
            h.assertTrue(s.getValue(MemberBlock.FORMED), "Formed: " + c);
            h.assertTrue(cnc.getBlockPos().equals(Multiblocks.controllerOf(h.getLevel(), p)), "Known to belong: " + c);
            if (frame == null && Structures.FRAME.equals(c.kind())) { frame = p; }
        }
        injectorAtCncStep(cnc, four);
        for (int i = 0; i < 40; i++) { cnc.tickForTest(); }
        h.assertTrue(cnc.state() == MachineBlockEntity.State.WORKING && cnc.progress() == 40, "Working: " + cnc.state() + " " + cnc.progress());
        // a frame taken away: dissolved at once, the members no longer formed, the work kept
        h.getLevel().setBlock(frame, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        h.assertFalse(cnc.formed(), "Dissolved");
        for (Pattern.Cell c : m.cells()) {
            BlockPos p = new BlockPos(c.x(), c.y(), c.z());
            BlockState s = h.getLevel().getBlockState(p);
            if (s.hasProperty(MemberBlock.FORMED)) { h.assertFalse(s.getValue(MemberBlock.FORMED), "No longer formed: " + c); }
        }
        for (int i = 0; i < 40; i++) { cnc.tickForTest(); }
        h.assertTrue(cnc.state() == MachineBlockEntity.State.NOT_FORMED && cnc.progress() == 40, "Waits with its work: " + cnc.state() + " " + cnc.progress());
        // put back: formed again within a look, and the work goes on
        h.getLevel().setBlock(frame, HdfvMultiblocks.block(Structures.FRAME).defaultBlockState(), Block.UPDATE_ALL);
        for (int i = 0; i <= Multiblock.CHECK_TICKS; i++) { cnc.tickForTest(); }
        h.assertTrue(cnc.formed() && cnc.state() == MachineBlockEntity.State.WORKING && cnc.progress() > 40, "Formed again and working: " + cnc.state() + " " + cnc.progress());
        h.succeed();
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool")
    public static void everyPresetFormsWithItsAxesFacingAnyWay(GameTestHelper h) {
        List<String> presets = List.of("vmc_3", "vmc_4", "vmc_5", "mill_turn_5", "mill_turn_9", "mill_turn_12");
        Direction[] facings = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST};
        for (int i = 0; i < presets.size(); i++) {
            // spaced 8 apart so no two overlap whichever way they face
            BlockPos at = h.absolutePos(new BlockPos(4 + 8 * (i % 3), 2, 4 + 7 * (i / 3)));
            HdfvMultiblocks.place(h.getLevel(), Structures.CNC, at, facings[i], 1, HdfvMultiblocks.cncModules(presets.get(i)));
            MachineBlockEntity be = (MachineBlockEntity) h.getLevel().getBlockEntity(at);
            h.assertTrue(be != null && be.multiblock().check(h.getLevel()), presets.get(i) + " formed facing " + facings[i]);
            CncConfig c = Structures.cncConfig(be.multiblock().match());
            h.assertTrue(c != null && c.id().equals(presets.get(i)), presets.get(i) + " is " + (c == null ? null : c.id()));
        }
        h.succeed();
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool")
    public static void aLoneCabinetWaitsAndMembersAreMadeAndNamed(GameTestHelper h) throws java.io.IOException {
        PartDesign four = SampleEngines.inlineFour();
        BlockPos pos = new BlockPos(2, 2, 2);
        h.setBlock(pos, HdfvMultiblocks.block(Structures.CNC_CONTROLLER));
        MachineBlockEntity cnc = (MachineBlockEntity) h.getBlockEntity(pos);
        cnc.items().setItem(MachineBlockEntity.DRAWING, dev.mcmod.hdfv.neoforge.design.Blueprints.of(four));
        injectorAtCncStep(cnc, four);
        for (int i = 0; i < 40; i++) { cnc.tickForTest(); }
        h.assertTrue(cnc.state() == MachineBlockEntity.State.NOT_FORMED && cnc.progress() == 0, "A cabinet alone does nothing: " + cnc.state());
        var recipes = h.getLevel().getRecipeManager().getRecipes().stream().filter(r -> r.id().getNamespace().equals(Hdfv.MOD_ID)).map(r -> r.id().getPath()).toList();
        BlockPos p = new BlockPos(6, 2, 6);
        for (String id : HdfvMultiblocks.ids()) {
            h.assertTrue(recipes.contains(id), "A recipe for the " + id);
            h.setBlock(p, HdfvMultiblocks.block(id));
            var drops = Block.getDrops(h.getBlockState(p), h.getLevel(), h.absolutePos(p), null);
            h.assertTrue(drops.size() == 1 && drops.get(0).is(HdfvMultiblocks.block(id).asItem()), id + " drops itself: " + drops);
        }
        for (String file : List.of("en_us", "zh_tw")) {
            try (var in = Hdfv.class.getResourceAsStream("/assets/hdfv/lang/" + file + ".json")) {
                var lang = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                for (String id : HdfvMultiblocks.ids()) { h.assertTrue(lang.has("block.hdfv." + id), file + " lacks " + id); }
                h.assertTrue(lang.has("hdfv.machine.state.not_formed"), file + " lacks the unformed state");
            }
        }
        h.succeed();
    }
}
