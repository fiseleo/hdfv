package dev.mcmod.hdfv.neoforge.machine;

import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.hdfv.manufacture.PartPlan;
import dev.mcmod.hdfv.manufacture.Plans;
import dev.mcmod.hdfv.manufacture.Process;
import dev.mcmod.hdfv.manufacture.Station;
import dev.mcmod.hdfv.manufacture.ToolConfig;
import dev.mcmod.hdfv.manufacture.Workpiece;
import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.neoforge.design.Blueprints;
import dev.mcmod.hdfv.neoforge.power.HdfvPower;
import dev.mcmod.hdfv.neoforge.power.PowerBlock;
import dev.mcmod.hdfv.part.engine.SampleEngines;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** The manual machine tools as multiblocks in a running server (0.9): formed, sized, fitted with accessories, driven by their drive cell. */
@PrefixGameTestTemplate(false)
public final class ToolGameTests {
    private ToolGameTests() {}

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool")
    public static void everyMachineToolFormsAndFallsApart(GameTestHelper h) {
        int k = 0;
        for (Station s : ToolConfig.STATIONS) {
            if (s.family) { continue; }   // 0.17's families: MachiningGameTests
            MachineBlockEntity m = MachineGameTests.tool(h, new BlockPos(2 + 7 * (k % 3), 3, 2 + 7 * (k / 3)), s, 2, k % 2 == 0);
            k++;
            ToolConfig t = m.tool();
            h.assertTrue(t != null && t.size() == 2, s + " formed, size 2: " + t);
            h.assertTrue(!t.accessories().isEmpty(), s + " has its accessories: " + t);
            h.assertTrue(m.drivePos() != null && m.electric() == (k % 2 == 1), s + " drive");
            // a bed, table or column taken away: it falls apart
            String members = dev.mcmod.hdfv.manufacture.MachineFrame.of(s).members;
            var cell = m.multiblock().match().cells().stream().filter(c -> members.indexOf(c.symbol()) >= 0).findFirst().orElseThrow();
            h.getLevel().setBlock(new BlockPos(cell.x(), cell.y(), cell.z()), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            m.tickForTest();
            h.assertTrue(!m.formed() && m.state() == MachineBlockEntity.State.NOT_FORMED, s + " fell apart: " + m.state());
        }
        h.succeed();
    }

    /** A workpiece of a design at its first step of a process. */
    static Object[] at(PartDesign d, String key, Process p) {
        PartPlan plan = Plans.of(d);
        Workpiece w = plan.workpieces().stream().filter(x -> x.key().equals(key)).findFirst().orElseThrow();
        for (int i = 0; i < w.route().size(); i++) { if (w.route().get(i).process() == p) { return new Object[]{w, i}; } }
        throw new IllegalStateException("No " + p + " in " + key);
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool")
    public static void aLongCrankNeedsALongLatheAndGrindingAGrinder(GameTestHelper h) {
        // the 11.9 l generator diesel's crank is over a metre long: not on a 1 m lathe, on a 2 m one
        PartDesign big = SampleEngines.generatorDiesel();
        Object[] crank = at(big, "crank", Process.TURN);
        Workpiece w = (Workpiece) crank[0];
        h.assertTrue(w.envelope().lengthM() > 1 && w.envelope().lengthM() < 2, "A crank 1-2 m long: " + w.envelope());
        FakePlayer p = FakePlayerFactory.getMinecraft(h.getLevel());
        MachineBlockEntity shortLathe = MachineGameTests.machine(h, new BlockPos(2, 3, 2), Station.LATHE, big);
        shortLathe.items.setItem(MachineBlockEntity.FIRST_INPUT, Workpieces.start(big, w, (int) crank[1]));
        // (machine() builds it 2 m: take a segment off by rebuilding it 1 m)
        MachineBlockEntity oneMetre = MachineGameTests.tool(h, new BlockPos(2, 3, 9), Station.LATHE, 1, true);
        h.getLevel().setBlock(oneMetre.drivePos().below(), HdfvPower.block(PowerBlock.Kind.CREATIVE_POWER).defaultBlockState(), Block.UPDATE_ALL);
        oneMetre.items.setItem(MachineBlockEntity.DRAWING, Blueprints.of(big));
        oneMetre.items.setItem(MachineBlockEntity.FIRST_INPUT, Workpieces.start(big, w, (int) crank[1]));
        MachineGameTests.work(h, p, oneMetre);
        for (int i = 0; i < 5; i++) { oneMetre.tick(); }
        h.assertTrue(oneMetre.state() == MachineBlockEntity.State.TOO_BIG, "Too big for 1 m: " + oneMetre.state());
        MachineGameTests.leave(p);
        MachineGameTests.work(h, p, shortLathe);
        for (int i = 0; i < 5; i++) { shortLathe.tick(); }
        h.assertTrue(shortLathe.state() == MachineBlockEntity.State.WORKING, "A 2 m lathe takes it: " + shortLathe.state());
        MachineGameTests.leave(p);
        // a bare lathe cannot grind the crank's journals: it needs the tool-post grinder
        PartDesign four = MachineGameTests.oldFour();
        Object[] grind = at(four, "crank", Process.GRIND);
        MachineBlockEntity bare = MachineGameTests.tool(h, new BlockPos(14, 3, 2), Station.LATHE, 1, true, false);
        h.getLevel().setBlock(bare.drivePos().below(), HdfvPower.block(PowerBlock.Kind.CREATIVE_POWER).defaultBlockState(), Block.UPDATE_ALL);
        bare.items.setItem(MachineBlockEntity.DRAWING, Blueprints.of(four));
        bare.items.setItem(MachineBlockEntity.FIRST_INPUT, Workpieces.start(four, (Workpiece) grind[0], (int) grind[1]));
        MachineGameTests.work(h, p, bare);
        for (int i = 0; i < 5; i++) { bare.tick(); }
        h.assertTrue(bare.state() == MachineBlockEntity.State.NEEDS_ACCESSORY && bare.missing == ToolConfig.Accessory.TOOLPOST_GRINDER,
                "Needs the grinder: " + bare.state() + " " + bare.missing);
        MachineGameTests.leave(p);
        h.succeed();
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool", timeoutTicks = 200)
    public static void aLatheOnItsMotorSeatRunsOnACable(GameTestHelper h) {
        // creative power, 3 m of cable, the motor seat: the lathe works; on the pulley seat the cable does nothing
        h.setBlock(new BlockPos(2, 2, 2), HdfvPower.block(PowerBlock.Kind.CREATIVE_POWER));
        for (int x = 3; x <= 5; x++) { h.setBlock(new BlockPos(x, 2, 2), HdfvPower.CABLE.get()); }
        PartDesign four = MachineGameTests.oldFour();
        MachineBlockEntity lathe = MachineGameTests.tool(h, new BlockPos(6, 3, 2), Station.LATHE, 2, true);
        h.assertTrue(h.absolutePos(new BlockPos(6, 2, 2)).equals(lathe.drivePos()), "The motor seat at the cable's end");
        lathe.items.setItem(MachineBlockEntity.DRAWING, Blueprints.of(four));
        Object[] turn = at(four, "piston", Process.TURN);
        lathe.items.setItem(MachineBlockEntity.FIRST_INPUT, Workpieces.start(four, (Workpiece) turn[0], (int) turn[1]));
        // a player of its own (the shared fake player is busy in the other tests of the batch)
        FakePlayer p = FakePlayerFactory.get(h.getLevel(), new com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes("cable lathe".getBytes()), "turner"));
        MachineGameTests.work(h, p, lathe);
        h.runAfterDelay(40, () -> {
            h.assertTrue(lathe.state() == MachineBlockEntity.State.WORKING && lathe.lastRate() > 0.99, "Working on the cable's power: " + lathe.state() + " " + lathe.lastRate());
            // swap the motor seat for the pulley seat: no shaft, no power
            h.getLevel().setBlock(lathe.drivePos(), HdfvMachines.DRIVES.get(false).get().defaultBlockState(), Block.UPDATE_ALL);
            lathe.multiblock().check(h.getLevel());
            h.runAfterDelay(20, () -> {
                h.assertTrue(lathe.formed() && !lathe.electric() && lathe.state() == MachineBlockEntity.State.NO_POWER, "On the pulley seat the cable does nothing: " + lathe.state());
                MachineGameTests.leave(p);
                h.succeed();
            });
        });
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool")
    public static void theDrivesAreMadeDroppedAndNamed(GameTestHelper h) throws java.io.IOException {
        var recipes = h.getLevel().getRecipeManager().getRecipes().stream().filter(r -> r.id().getNamespace().equals(Hdfv.MOD_ID)).map(r -> r.id().getPath()).toList();
        BlockPos p = new BlockPos(4, 2, 4);
        for (var d : HdfvMachines.DRIVES.values()) {
            String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(d.get()).getPath();
            h.assertTrue(recipes.contains(id), "A recipe for the " + id);
            h.setBlock(p, d.get());
            var drops = Block.getDrops(h.getBlockState(p), h.getLevel(), h.absolutePos(p), h.getLevel().getBlockEntity(h.absolutePos(p)));
            h.assertTrue(drops.size() == 1 && drops.get(0).is(d.get().asItem()), id + " drops itself: " + drops);
        }
        for (String file : List.of("en_us", "zh_tw")) {
            try (var in = Hdfv.class.getResourceAsStream("/assets/hdfv/lang/" + file + ".json")) {
                var lang = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                for (String k : List.of("block.hdfv.motor_drive", "block.hdfv.pulley_drive", "hdfv.machine.needs", "hdfv.machine.tool", "hdfv.machine.tool_high",
                        "hdfv.machine.no_accessories", "hdfv.drive.electric", "hdfv.drive.shaft", "hdfv.drive.drives", "hdfv.drive.alone")) {
                    h.assertTrue(lang.has(k), file + " lacks " + k);
                }
                for (ToolConfig.Accessory a : ToolConfig.Accessory.values()) { h.assertTrue(lang.has("block.hdfv." + a.kind), file + " lacks " + a.kind); }
            }
        }
        h.succeed();
    }
}
