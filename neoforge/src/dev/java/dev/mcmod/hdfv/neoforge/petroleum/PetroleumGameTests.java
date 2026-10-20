package dev.mcmod.hdfv.neoforge.petroleum;

import dev.mcmod.aerocore.api.petroleum.Lift;
import dev.mcmod.hdfv.manufacture.Alloy;
import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.neoforge.HdfvConfig;
import dev.mcmod.hdfv.neoforge.material.Stock;
import dev.mcmod.hdfv.neoforge.multiblock.HdfvMultiblocks;
import dev.mcmod.hdfv.neoforge.power.HdfvPower;
import dev.mcmod.hdfv.neoforge.power.PowerBlock;
import dev.mcmod.hdfv.petroleum.OilFields;
import dev.mcmod.hdfv.petroleum.Oilwell;
import dev.mcmod.hdfv.structure.Structures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;

/** The oil field in a running server (0.8): a rig drills to a field in Teale's time, a well flows, a drawn-down one needs a pump jack. Ticked directly. */
@PrefixGameTestTemplate(false)
public final class PetroleumGameTests {
    private PetroleumGameTests() {}

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool")
    public static void aRigDrillsDownToTheFieldInItsTimeAndSetsAWellhead(GameTestHelper h) {
        BlockPos at = h.absolutePos(new BlockPos(4, 2, 4));
        HdfvMultiblocks.place(h.getLevel(), Structures.DRILLING_RIG, at, Direction.NORTH, 3, Map.of());
        h.getLevel().setBlock(at.below(), HdfvPower.block(PowerBlock.Kind.CREATIVE_POWER).defaultBlockState(), Block.UPDATE_ALL);
        DrillingRigBlockEntity rig = (DrillingRigBlockEntity) h.getLevel().getBlockEntity(at);
        rig.multiblock().check(h.getLevel());
        BlockPos well = rig.rotary();
        h.assertTrue(well != null, "The rig formed round its rotary table");
        OilFieldCommands.force(h.getLevel(), well, 60, 1.2, true, 50e6);
        for (int i = 0; i < rig.casing().getSlots(); i++) { rig.casing().insertItem(i, Stock.of(Alloy.STEEL, 64), false); }
        rig.setRunning(true);
        double perTick = Lift.penetrationMPerS(Oilwell.RIG_W, Oilwell.BIT_M, 50e6) * HdfvConfig.workSecondsPerTick();
        int expected = (int) Math.ceil(60 / perTick), ticks = 0;
        while (rig.state() != DrillingRigBlockEntity.State.STRUCK_OIL && ticks < 10 * expected) { rig.tick(); ticks++; }
        h.assertTrue(rig.state() == DrillingRigBlockEntity.State.STRUCK_OIL, "Struck oil: " + rig.state() + " at " + rig.drilledM() + " m");
        h.assertTrue(Math.abs(ticks - expected) <= 2, "Drilled 60 m of 50 MPa rock in " + ticks + " ticks; Teale says " + expected);
        h.assertTrue(h.getLevel().getBlockState(well).is(HdfvPetroleum.WELLHEAD.get()), "A wellhead where the rotary table was");
        int steel = 0;
        for (int i = 0; i < rig.casing().getSlots(); i++) { steel += (int) Math.round(Stock.kg(rig.casing().getStackInSlot(i))); }
        double used = 3 * 64 * 10 - steel;
        h.assertTrue(used >= 60 * Oilwell.CASING_KG_PER_M && used < 60 * Oilwell.CASING_KG_PER_M + 10.5, "Cased with " + used + " kg of steel");
        h.succeed();
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool")
    public static void aWellFlowsUntilTheFieldIsDrawnDownThenAPumpJackLiftsIt(GameTestHelper h) {
        // the jack round its wellhead, a creative source under its motor
        BlockPos c = h.absolutePos(new BlockPos(2, 2, 4));
        HdfvMultiblocks.place(h.getLevel(), Structures.PUMP_JACK_PATTERN, c, Direction.NORTH, 1, Map.of());
        h.getLevel().setBlock(c.below(), HdfvPower.block(PowerBlock.Kind.CREATIVE_POWER).defaultBlockState(), Block.UPDATE_ALL);
        PumpJackBlockEntity jack = (PumpJackBlockEntity) h.getLevel().getBlockEntity(c);
        jack.multiblock().check(h.getLevel());
        WellheadBlockEntity w = jack.wellhead();
        h.assertTrue(w != null, "The jack formed over its wellhead");
        OilFieldCommands.force(h.getLevel(), w.getBlockPos(), 100, 1.3, true, 80e6);
        OilFields.Field f = w.field();
        double seconds = HdfvConfig.workSecondsPerTick();
        // it flows by itself: the jack stands idle
        for (int i = 0; i < 50; i++) { w.tick(); jack.tick(); }
        double flowed = 50 * f.naturalRate(0) * seconds * 1000;
        h.assertTrue(w.state() == WellheadBlockEntity.State.FLOWING && jack.state() == PumpJackBlockEntity.State.IDLE, "Flowing: " + w.state() + ", jack " + jack.state());
        h.assertTrue(Math.abs(w.fluid().getAmount() - flowed) <= 1.0, "Flowed " + w.fluid().getAmount() + " l; Vogel says " + flowed);
        h.assertTrue(w.fluid().getFluid() == dev.mcmod.hdfv.neoforge.fluid.HdfvFluidRegistry.fluid("crude_oil"), "Crude oil");
        // drawn down past what lifts it: it stops, and the jack takes over
        OilFieldData.of(h.getLevel()).setProduced(f.id(), 1.01 * f.flowsUntilM3());
        int before = w.fluid().getAmount();
        w.tick();
        h.assertTrue(w.state() == WellheadBlockEntity.State.NEEDS_PUMP && w.fluid().getAmount() == before, "Needs a pump: " + w.state());
        for (int i = 0; i < 50; i++) { jack.tick(); w.tick(); }
        double pumped = 50 * Oilwell.jackRateM3PerS(f, 1.01 * f.flowsUntilM3()) * seconds * 1000;
        h.assertTrue(jack.state() == PumpJackBlockEntity.State.PUMPING, "Pumping: " + jack.state());
        h.assertTrue(Math.abs(w.fluid().getAmount() - before - pumped) <= 1.0, "Pumped " + (w.fluid().getAmount() - before) + " l; the rod pump gives " + pumped);
        h.succeed();
    }

    /**
     * 0.12: a pump jack tells the clients when it starts pumping and when its strokes a minute change (the renderer turns
     * its cranks at them), and nothing while it pumps on as it was.
     */
    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool")
    public static void pumpJackSyncsStrokes(GameTestHelper h) {
        BlockPos c = h.absolutePos(new BlockPos(2, 2, 4));
        HdfvMultiblocks.place(h.getLevel(), Structures.PUMP_JACK_PATTERN, c, Direction.NORTH, 1, Map.of());
        h.getLevel().setBlock(c.below(), HdfvPower.block(PowerBlock.Kind.CREATIVE_POWER).defaultBlockState(), Block.UPDATE_ALL);
        PumpJackBlockEntity jack = (PumpJackBlockEntity) h.getLevel().getBlockEntity(c);
        jack.multiblock().check(h.getLevel());
        WellheadBlockEntity w = jack.wellhead();
        h.assertTrue(w != null, "The jack formed over its wellhead");
        OilFieldCommands.force(h.getLevel(), w.getBlockPos(), 100, 1.3, true, 80e6);
        OilFields.Field f = w.field();
        OilFieldData.of(h.getLevel()).setProduced(f.id(), 1.01 * f.flowsUntilM3());
        // the crude drawn off as it comes (a full wellhead would stop the jack: FULL, then PUMPING again, each a change to send)
        var out = w.handler();
        for (int i = 0; i < 5; i++) { w.tick(); jack.tick(); out.drain(Integer.MAX_VALUE, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE); }
        h.assertTrue(jack.state() == PumpJackBlockEntity.State.PUMPING, "Pumping: " + jack.state());
        int started = jack.updatesSent;
        h.assertTrue(started >= 1, "It told the clients it pumps");
        for (int i = 0; i < 40; i++) {
            w.tick();
            jack.tick();
            out.drain(Integer.MAX_VALUE, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
            h.assertTrue(jack.state() == PumpJackBlockEntity.State.PUMPING, "Still pumping: " + jack.state());
        }
        h.assertTrue(jack.updatesSent == started, "Nothing more while it pumps on: " + (jack.updatesSent - started));
        h.assertTrue(jack.set("spm", 8) == null, "8 strokes a minute");
        w.tick();
        jack.tick();
        out.drain(Integer.MAX_VALUE, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        h.assertTrue(jack.updatesSent == started + 1, "One update for the new speed: " + (jack.updatesSent - started));
        var tag = jack.getUpdateTag(h.getLevel().registryAccess());
        PumpJackBlockEntity client = new PumpJackBlockEntity(c, jack.getBlockState());
        client.handleUpdateTag(tag, h.getLevel().registryAccess());
        h.assertTrue(client.shownPumping() && client.shownSpm() == 8, "The client draws it pumping at 8: " + client.shownPumping() + " " + client.shownSpm());
        h.succeed();
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool")
    public static void theOilFieldsBlocksAreMadeDroppedAndNamed(GameTestHelper h) throws java.io.IOException {
        var recipes = h.getLevel().getRecipeManager().getRecipes().stream().filter(r -> r.id().getNamespace().equals(Hdfv.MOD_ID)).map(r -> r.id().getPath()).toList();
        BlockPos p = new BlockPos(6, 2, 6);
        for (String id : HdfvPetroleum.ids()) {
            var block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Hdfv.MOD_ID, id));
            if (!id.equals("oil_seep")) { h.assertTrue(recipes.contains(id), "A recipe for the " + id); }
            h.setBlock(p, block);
            var drops = Block.getDrops(h.getBlockState(p), h.getLevel(), h.absolutePos(p), h.getLevel().getBlockEntity(h.absolutePos(p)));
            h.assertTrue(drops.size() == 1 && drops.get(0).is(block.asItem()), id + " drops itself: " + drops);
        }
        h.assertTrue(recipes.contains("geophone"), "A recipe for the geophone");
        for (String file : List.of("en_us", "zh_tw")) {
            try (var in = Hdfv.class.getResourceAsStream("/assets/hdfv/lang/" + file + ".json")) {
                var lang = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                for (String id : HdfvPetroleum.ids()) { h.assertTrue(lang.has("block.hdfv." + id), file + " lacks " + id); }
                for (var s : DrillingRigBlockEntity.State.values()) { h.assertTrue(lang.has("hdfv.rig.state." + s.name().toLowerCase(java.util.Locale.ROOT)), file + " rig " + s); }
                for (var s : PumpJackBlockEntity.State.values()) { h.assertTrue(lang.has("hdfv.jack.state." + s.name().toLowerCase(java.util.Locale.ROOT)), file + " jack " + s); }
                for (var s : WellheadBlockEntity.State.values()) { h.assertTrue(lang.has("hdfv.wellhead.state." + s.name().toLowerCase(java.util.Locale.ROOT)), file + " well " + s); }
                for (String k : List.of("item.hdfv.geophone", "hdfv.geophone.none", "hdfv.geophone.field", "hdfv.geophone.flows", "hdfv.geophone.pump", "hdfv.geophone.use",
                        "hdfv.geophone.east", "hdfv.geophone.west", "hdfv.geophone.north", "hdfv.geophone.south", "hdfv.rig.status", "hdfv.jack.status", "hdfv.wellhead.status")) {
                    h.assertTrue(lang.has(k), file + " lacks " + k);
                }
            }
        }
        h.succeed();
    }
}
