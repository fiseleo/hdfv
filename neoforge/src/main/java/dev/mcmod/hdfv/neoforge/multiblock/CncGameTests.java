package dev.mcmod.hdfv.neoforge.multiblock;

import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.hdfv.manufacture.Operation;
import dev.mcmod.hdfv.manufacture.Pace;
import dev.mcmod.hdfv.manufacture.PartPlan;
import dev.mcmod.hdfv.manufacture.Plans;
import dev.mcmod.hdfv.manufacture.Station;
import dev.mcmod.hdfv.manufacture.Workpiece;
import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.neoforge.HdfvConfig;
import dev.mcmod.hdfv.neoforge.design.Blueprints;
import dev.mcmod.hdfv.neoforge.machine.HdfvMachines;
import dev.mcmod.hdfv.neoforge.machine.MachineBlockEntity;
import dev.mcmod.hdfv.neoforge.machine.Workpieces;
import dev.mcmod.hdfv.neoforge.power.HdfvPower;
import dev.mcmod.hdfv.neoforge.power.PowerBlock;
import dev.mcmod.hdfv.part.engine.SampleEngines;
import dev.mcmod.hdfv.structure.Pattern;
import dev.mcmod.hdfv.structure.Structures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The CNC's modules at work (0.8): axes it lacks, axes it has, a whole crank in one clamping, a guard taken off. Ticked directly. */
@PrefixGameTestTemplate(false)
public final class CncGameTests {
    private CncGameTests() {}

    /** A CNC of a preset built round a cabinet (facing north), a creative source under it, a drawing in it. */
    static MachineBlockEntity cnc(GameTestHelper h, BlockPos rel, String preset, PartDesign drawing) {
        BlockPos at = h.absolutePos(rel);
        HdfvMultiblocks.place(h.getLevel(), Structures.CNC, at, Direction.NORTH, 1, HdfvMultiblocks.cncModules(preset));
        h.setBlock(rel.below(), HdfvPower.block(PowerBlock.Kind.CREATIVE_POWER));
        MachineBlockEntity be = (MachineBlockEntity) h.getLevel().getBlockEntity(at);
        be.multiblock().check(h.getLevel());
        be.items().setItem(MachineBlockEntity.DRAWING, Blueprints.of(drawing));
        dev.mcmod.hdfv.neoforge.machine.MachineGameTests.supply(be);
        return be;
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool")
    public static void threeAxesLackWhatBladesNeedFiveCutThemAndAGuardOffStopsIt(GameTestHelper h) {
        PartDesign jet = SampleEngines.turbojet();
        PartPlan plan = Plans.of(jet);
        Workpiece blades = Workpieces.piece(plan, "compressor_blades");
        int at = -1;
        for (int i = 0; i < blades.route().size() && at < 0; i++) { if (blades.route().get(i).complex()) { at = i; } }
        h.assertTrue(at > 0 && blades.route().get(at).axes() == Operation.COMPLEX_AXES, "Blades need five axes: " + blades.route());
        MachineBlockEntity three = cnc(h, new BlockPos(2, 2, 2), "vmc_3", jet);
        three.items().setItem(MachineBlockEntity.FIRST_INPUT, Workpieces.start(jet, blades, at));
        for (int i = 0; i < 20; i++) { three.tickForTest(); }
        h.assertTrue(three.state() == MachineBlockEntity.State.NEEDS_AXES && three.progress() == 0, "Three axes: " + three.state());
        MachineBlockEntity five = cnc(h, new BlockPos(2, 2, 9), "vmc_5", jet);
        five.items().setItem(MachineBlockEntity.FIRST_INPUT, Workpieces.start(jet, blades, at));
        for (int i = 0; i < 20; i++) { five.tickForTest(); }
        h.assertTrue(five.state() == MachineBlockEntity.State.WORKING && five.progress() == 20, "Five axes: " + five.state() + " " + five.progress());
        // a guard taken off: it stops, and keeps its work
        BlockPos guard = null;
        for (Pattern.Cell c : five.multiblock().match().cells()) { if (Structures.GUARD.equals(c.kind())) { guard = new BlockPos(c.x(), c.y(), c.z()); break; } }
        h.getLevel().setBlock(guard, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        five.tickForTest();
        h.assertTrue(five.state() == MachineBlockEntity.State.NOT_FORMED && five.progress() == 20, "Guard off, it stops: " + five.state());
        h.succeed();
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool")
    public static void twelveAxesFinishACrankInOneClampingFasterThanTheManualMachines(GameTestHelper h) {
        PartDesign four = SampleEngines.inlineFour();
        Workpiece crank = Workpieces.piece(Plans.of(four), "crank");
        MachineBlockEntity cnc = cnc(h, new BlockPos(2, 2, 2), "mill_turn_12", four);
        cnc.items().setItem(MachineBlockEntity.FIRST_INPUT, Workpieces.start(four, crank, 1));
        int ticks = 0;
        ItemStack out = ItemStack.EMPTY;
        while (out.isEmpty() && ticks < 200_000) {
            cnc.tickForTest();
            ticks++;
            for (int o = cnc.firstOutput(); o < cnc.firstOutput() + cnc.outputs; o++) {
                if (cnc.items().getItem(o).is(HdfvMachines.WORKPIECE.get())) { out = cnc.items().getItem(o); }
            }
        }
        Workpieces.Data d = Workpieces.data(out);
        // 0.17: all but the lapping inserted last (the finisher's)
        int last = crank.route().size() - 1;
        h.assertTrue(d != null && d.step() == last && crank.route().get(last).process() == dev.mcmod.hdfv.manufacture.Process.LAP && crank.route().get(last).added(),
                "Finished in one go up to the lapping: " + d);
        // the manual way: each cutting step at the manual machine that does it soonest, its setup and the piece
        double manual = 0;
        for (int i = 1; i < last; i++) {
            double best = Double.POSITIVE_INFINITY;
            for (Station s : Station.CUTTING) {
                if (!s.manual) { continue; }
                Plans.Job j = Plans.job(crank, i, 1, s, null);
                if (j.possible()) { best = Math.min(best, j.prepareS() + j.pieceS()); }
            }
            manual += best;
        }
        long manualTicks = Pace.ticks(manual, HdfvConfig.ticksPerWorkHour());
        h.assertTrue(ticks < manualTicks, "Twelve axes in " + ticks + " ticks; the lathe, the drill and the grinder " + manualTicks);
        h.succeed();
    }
}
