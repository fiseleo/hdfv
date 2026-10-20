package dev.mcmod.hdfv.neoforge.fluid;

import dev.mcmod.hdfv.fluid.HdfvFluids;
import dev.mcmod.hdfv.fluid.Piping;
import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.neoforge.HdfvConfig;
import dev.mcmod.hdfv.neoforge.multiblock.HdfvMultiblocks;
import dev.mcmod.hdfv.structure.Pattern;
import dev.mcmod.hdfv.structure.Structures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;

/** Fluids in a running server (0.8): stacked tanks, a pump on power through pipes, a storage tank, a jerry can. Blocks are ticked directly. */
@PrefixGameTestTemplate(false)
public final class FluidGameTests {
    private FluidGameTests() {}

    private static IFluidHandler handler(GameTestHelper h, BlockPos rel) {
        return h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(rel), Direction.UP);
    }

    private static FluidStack diesel(int litres) { return new FluidStack(HdfvFluidRegistry.fluid("diesel"), litres); }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool")
    public static void stackedTanksAreOneTankAndATakenBlockSpillsItsShare(GameTestHelper h) {
        for (int y = 2; y <= 4; y++) { h.setBlock(new BlockPos(2, y, 2), HdfvFluidRegistry.TANK.get()); }
        IFluidHandler t = handler(h, new BlockPos(2, 3, 2));
        h.assertTrue(t.getTankCapacity(0) == 3 * Piping.TANK_LITRES, "Three blocks, one tank: " + t.getTankCapacity(0));
        h.assertTrue(t.fill(diesel(2500), IFluidHandler.FluidAction.EXECUTE) == 2500, "Filled");
        h.assertTrue(t.fill(new FluidStack(HdfvFluidRegistry.fluid("gasoline"), 100), IFluidHandler.FluidAction.EXECUTE) == 0, "One fluid to a tank");
        h.assertTrue(handler(h, new BlockPos(2, 2, 2)).getFluidInTank(0).getAmount() == 2500, "Seen from any block");
        h.setBlock(new BlockPos(2, 4, 2), Blocks.AIR);
        IFluidHandler two = handler(h, new BlockPos(2, 2, 2));
        h.assertTrue(two.getTankCapacity(0) == 2000 && two.getFluidInTank(0).getAmount() == 2000, "The top held 500 l and spilt it: " + two.getFluidInTank(0).getAmount());
        h.assertTrue(two.drain(300, IFluidHandler.FluidAction.EXECUTE).getAmount() == 300, "Drawn");
        h.assertTrue(((TankBlockEntity) h.getBlockEntity(new BlockPos(2, 2, 2))).fluid().getAmount() == 1000, "Drawn from the top: the bottom stays full");
        h.succeed();
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool")
    public static void aPumpOnPowerDrivesDieselThroughPipes(GameTestHelper h) {
        BlockPos from = new BlockPos(2, 2, 2), pump = new BlockPos(3, 2, 2), to = new BlockPos(9, 2, 2);
        h.setBlock(from, HdfvFluidRegistry.TANK.get());
        h.setBlock(to, HdfvFluidRegistry.TANK.get());
        h.setBlock(pump, HdfvFluidRegistry.PUMP.get().defaultBlockState().setValue(PumpBlock.FACING, Direction.EAST));
        for (int x = 4; x <= 8; x++) { h.setBlock(new BlockPos(x, 2, 2), HdfvFluidRegistry.PIPE.get()); }
        handler(h, from).fill(diesel(1000), IFluidHandler.FluidAction.EXECUTE);
        PumpBlockEntity p = (PumpBlockEntity) h.getBlockEntity(pump);
        p.tick();
        h.assertTrue(p.state() == PumpBlockEntity.State.NO_POWER && handler(h, to).getFluidInTank(0).isEmpty(), "No power, no flow: " + p.state());
        var energy = h.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, h.absolutePos(pump), Direction.UP);
        h.assertTrue(energy != null && energy.receiveEnergy(PumpBlockEntity.BUFFER_FE, false) == PumpBlockEntity.BUFFER_FE, "It takes Forge Energy");
        h.assertTrue(energy.extractEnergy(10, false) == 0, "and gives none");
        int ticks = 10;
        for (int i = 0; i < ticks; i++) { p.tick(); }
        HdfvFluids.Spec spec = HdfvFluids.of("diesel");
        double flow = Piping.flowM3PerS(spec, 6, 0), perTick = Piping.litresPerTick(flow, HdfvConfig.workSecondsPerTick());
        int moved = handler(h, to).getFluidInTank(0).getAmount();
        h.assertTrue(p.state() == PumpBlockEntity.State.PUMPING, "Pumping: " + p.state());
        h.assertTrue(Math.abs(moved - Math.min(1000, ticks * perTick)) <= 1.0, "Moved " + moved + " l; the duty gives " + ticks * perTick);
        h.assertTrue(handler(h, from).getFluidInTank(0).getAmount() == 1000 - moved, "Nothing lost");
        double fe = Piping.pumpW(spec, flow) * HdfvConfig.workSecondsPerTick() / HdfvConfig.joulesPerFE() * ticks;
        int used = PumpBlockEntity.BUFFER_FE - energy.getEnergyStored();
        h.assertTrue(Math.abs(used - fe) <= 1.0, "Used " + used + " FE for " + fe);
        h.succeed();
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool")
    public static void aStorageTankHoldsWhileFormedAndKeepsItsOilWhenNot(GameTestHelper h) {
        BlockPos valve = h.absolutePos(new BlockPos(8, 3, 4));
        HdfvMultiblocks.place(h.getLevel(), Structures.STORAGE_TANK, valve, Direction.NORTH, 2, Map.of());
        StorageValveBlockEntity be = (StorageValveBlockEntity) h.getLevel().getBlockEntity(valve);
        be.tick();
        h.assertTrue(be.multiblock().formed() && be.handler().getTankCapacity(0) == 27000, "Three courses: " + be.handler().getTankCapacity(0));
        IFluidHandler t = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, valve, Direction.NORTH);
        h.assertTrue(t.fill(diesel(10000), IFluidHandler.FluidAction.EXECUTE) == 10000, "Filled through its valve");
        BlockPos wall = null;
        for (Pattern.Cell c : be.multiblock().match().cells()) { if (Structures.TANK_WALL.equals(c.kind()) && c.y() == valve.getY() + 1) { wall = new BlockPos(c.x(), c.y(), c.z()); break; } }
        h.getLevel().setBlock(wall, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        h.assertFalse(be.multiblock().formed(), "Opened up");
        h.assertTrue(t.fill(diesel(100), IFluidHandler.FluidAction.EXECUTE) == 0 && t.drain(100, IFluidHandler.FluidAction.EXECUTE).isEmpty(), "Shut while open");
        h.assertTrue(t.getFluidInTank(0).getAmount() == 10000, "It kept its oil");
        h.getLevel().setBlock(wall, HdfvFluidRegistry.TANK_WALL.get().defaultBlockState(), Block.UPDATE_ALL);
        for (int i = 0; i <= dev.mcmod.hdfv.neoforge.multiblock.Multiblock.CHECK_TICKS; i++) { be.tick(); }
        h.assertTrue(be.multiblock().formed() && t.drain(4000, IFluidHandler.FluidAction.EXECUTE).getAmount() == 4000, "Whole again, drawn from");
        h.succeed();
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool")
    public static void aJerryCanFillsAndPoursAndEverythingIsMadeAndNamed(GameTestHelper h) throws java.io.IOException {
        h.setBlock(new BlockPos(2, 2, 2), HdfvFluidRegistry.TANK.get());
        IFluidHandler t = handler(h, new BlockPos(2, 2, 2));
        t.fill(diesel(100), IFluidHandler.FluidAction.EXECUTE);
        ItemStack can = new ItemStack(HdfvFluidRegistry.JERRY_CAN.get());
        var filled = FluidUtil.tryFillContainer(can, t, Integer.MAX_VALUE, null, true);
        h.assertTrue(filled.isSuccess() && JerryCanItem.content(filled.getResult()).getAmount() == HdfvFluidRegistry.JERRY_CAN_LITRES, "20 l in the can");
        h.assertTrue(t.getFluidInTank(0).getAmount() == 80, "taken from the tank");
        var poured = FluidUtil.tryEmptyContainer(filled.getResult(), t, Integer.MAX_VALUE, null, true);
        h.assertTrue(poured.isSuccess() && JerryCanItem.content(poured.getResult()).isEmpty() && t.getFluidInTank(0).getAmount() == 100, "and back");
        var recipes = h.getLevel().getRecipeManager().getRecipes().stream().filter(r -> r.id().getNamespace().equals(Hdfv.MOD_ID)).map(r -> r.id().getPath()).toList();
        BlockPos p = new BlockPos(6, 2, 6);
        for (String id : HdfvFluidRegistry.ids()) {
            h.assertTrue(recipes.contains(id), "A recipe for the " + id);
            if (id.equals("jerry_can")) { continue; }
            var block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Hdfv.MOD_ID, id));
            h.setBlock(p, block);
            var drops = Block.getDrops(h.getBlockState(p), h.getLevel(), h.absolutePos(p), h.getLevel().getBlockEntity(h.absolutePos(p)));
            h.assertTrue(drops.size() == 1 && drops.get(0).is(block.asItem()), id + " drops itself: " + drops);
        }
        for (String file : List.of("en_us", "zh_tw")) {
            try (var in = Hdfv.class.getResourceAsStream("/assets/hdfv/lang/" + file + ".json")) {
                var lang = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                for (HdfvFluids.Spec s : HdfvFluids.all()) { h.assertTrue(lang.has("fluid_type.hdfv." + s.id()), file + " lacks the fluid " + s.id()); }
                for (String id : HdfvFluidRegistry.ids()) { h.assertTrue(lang.has("block.hdfv." + id) || lang.has("item.hdfv." + id), file + " lacks " + id); }
                for (PumpBlockEntity.State st : PumpBlockEntity.State.values()) {
                    h.assertTrue(lang.has("hdfv.pump.state." + st.name().toLowerCase(java.util.Locale.ROOT)), file + " lacks pump state " + st);
                }
                for (String k : List.of("hdfv.pump.status", "hdfv.fluid.contents", "hdfv.fluid.empty", "hdfv.fluid.not_formed", "hdfv.fluid.can", "hdfv.fluid.can_empty", "hdfv.fluid.can_use")) {
                    h.assertTrue(lang.has(k), file + " lacks " + k);
                }
            }
        }
        h.succeed();
    }
}
