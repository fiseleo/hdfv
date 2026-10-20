package dev.mcmod.hdfv.neoforge.fluid;

import dev.mcmod.aerocore.api.Orientation;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.vehicle.Mount;
import dev.mcmod.aerocore.api.vehicle.VehicleBlueprint;
import dev.mcmod.aerocore.neoforge.vehicle.PartVehicle;
import dev.mcmod.aerocore.neoforge.vehicle.VehicleRegistry;
import dev.mcmod.hdfv.fluid.HdfvFluids;
import dev.mcmod.hdfv.fluid.HydrogenStorage;
import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.neoforge.fuel.Fuelling;
import dev.mcmod.hdfv.part.FuelTankDesign;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** Hydrogen in a running server (0.10): which store takes which form, boil-off, the liquefier, fuelling a vehicle (the reformer's: RefineryGameTests). */
@PrefixGameTestTemplate(false)
public final class HydrogenGameTests {
    private HydrogenGameTests() {}

    static FluidStack gas(int litres) { return new FluidStack(HdfvFluidRegistry.fluid(HdfvFluids.HYDROGEN), litres); }
    static FluidStack liquid(int litres) { return new FluidStack(HdfvFluidRegistry.fluid(HdfvFluids.LIQUID_HYDROGEN), litres); }

    static IFluidHandler place(GameTestHelper h, BlockPos at, Block block) {
        h.setBlock(at, block);
        return h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(at), null);
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool")
    public static void eachStoreTakesItsKindOfHydrogen(GameTestHelper h) {
        IFluidHandler tank = place(h, new BlockPos(2, 2, 2), HdfvFluidRegistry.TANK.get());
        IFluidHandler cylinders = place(h, new BlockPos(5, 2, 2), HdfvFluidRegistry.HYDROGEN_CYLINDERS.get());
        IFluidHandler cryo = place(h, new BlockPos(8, 2, 2), HdfvFluidRegistry.CRYOGENIC_TANK.get());
        h.assertTrue(tank.fill(gas(100), IFluidHandler.FluidAction.EXECUTE) == 0, "A tank refuses 700 bar hydrogen");
        h.assertTrue(cryo.fill(gas(100), IFluidHandler.FluidAction.EXECUTE) == 0, "So does a cryogenic tank");
        h.assertTrue(cylinders.fill(liquid(100), IFluidHandler.FluidAction.EXECUTE) == 0, "Cylinders refuse the liquid");
        h.assertTrue(cylinders.fill(gas(100), IFluidHandler.FluidAction.EXECUTE) == 100 && cylinders.getTankCapacity(0) == 500, "Cylinders take the gas");
        h.assertTrue(cryo.fill(liquid(100), IFluidHandler.FluidAction.EXECUTE) == 100 && cryo.getTankCapacity(0) == 800, "The cryogenic tank takes the liquid");
        h.assertTrue(tank.fill(liquid(100), IFluidHandler.FluidAction.EXECUTE) == 100, "A bare tank takes it too (and loses it)");
        // a cryogenic tank on a bare one is not one column
        IFluidHandler above = place(h, new BlockPos(2, 3, 2), HdfvFluidRegistry.CRYOGENIC_TANK.get());
        h.assertTrue(above.getTankCapacity(0) == 800 && tank.getTankCapacity(0) == 1000, "Each stacks with its own kind: " + above.getTankCapacity(0));
        ItemStack can = new ItemStack(HdfvFluidRegistry.JERRY_CAN.get());
        IFluidHandler canHandler = FluidUtil.getFluidHandler(can).orElseThrow();
        h.assertTrue(canHandler.fill(liquid(10), IFluidHandler.FluidAction.EXECUTE) == 0 && canHandler.fill(gas(10), IFluidHandler.FluidAction.EXECUTE) == 0,
                "A can holds no hydrogen");
        h.succeed();
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool", timeoutTicks = 400)
    public static void liquidHydrogenBoilsAwayInABareTankAndKeepsInACryogenicOne(GameTestHelper h) {
        IFluidHandler bare = place(h, new BlockPos(2, 2, 2), HdfvFluidRegistry.TANK.get());
        IFluidHandler cryo = place(h, new BlockPos(6, 2, 2), HdfvFluidRegistry.CRYOGENIC_TANK.get());
        bare.fill(liquid(1000), IFluidHandler.FluidAction.EXECUTE);
        cryo.fill(liquid(800), IFluidHandler.FluidAction.EXECUTE);
        // 300 ticks at 3.6 s of work: over 18 minutes, longer than a bare cubic metre lasts (about 13)
        h.runAfterDelay(300, () -> {
            h.assertTrue(bare.getFluidInTank(0).isEmpty(), "The bare tank boiled dry: " + bare.getFluidInTank(0).getAmount());
            h.assertTrue(cryo.getFluidInTank(0).getAmount() >= 799, "The cryogenic tank kept it: " + cryo.getFluidInTank(0).getAmount());
            h.succeed();
        });
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool", timeoutTicks = 200)
    public static void theLiquefierTurnsGasAndPowerIntoLiquid(GameTestHelper h) {
        h.setBlock(new BlockPos(3, 2, 3), HdfvFluidRegistry.LIQUEFIER.get());
        HydrogenLiquefierBlockEntity be = (HydrogenLiquefierBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(3, 2, 3)));
        h.assertTrue(be.handler().fill(gas(400), IFluidHandler.FluidAction.EXECUTE) == 400, "Gas in");
        h.assertTrue(be.handler().fill(liquid(10), IFluidHandler.FluidAction.EXECUTE) == 0, "Not the liquid");
        be.energy().receiveEnergy(HydrogenLiquefierBlockEntity.BUFFER_FE, false);
        h.runAfterDelay(40, () -> {
            double kg = be.madeKg(), fe = HydrogenLiquefierBlockEntity.BUFFER_FE - be.energy().getEnergyStored();
            // 10 kg an hour of work, 3.6 s a tick: 0.01 kg a tick
            h.assertTrue(Math.abs(kg - 0.4) <= 0.02, "0.4 kg in 40 ticks: " + kg);
            h.assertTrue(Math.abs(fe - kg * HydrogenStorage.LIQUEFACTION_J_PER_KG / 1000) <= 0.03 * fe, "11.9 kWh a kg: " + fe + " FE");
            double out = kg / HdfvFluids.of(HdfvFluids.LIQUID_HYDROGEN).densityKgPerM3() * 1000, in = kg / HdfvFluids.of(HdfvFluids.HYDROGEN).densityKgPerM3() * 1000;
            h.assertTrue(Math.abs(be.liquid().getAmount() - out) <= 1.01, "Liquid out: " + be.liquid().getAmount() + " of " + out);
            h.assertTrue(Math.abs(400 - be.gas().getAmount() - in) <= 1.01, "Gas used: " + (400 - be.gas().getAmount()) + " of " + in);
            h.assertTrue(be.handler().drain(100, IFluidHandler.FluidAction.SIMULATE).getAmount() == be.liquid().getAmount(), "Pumps draw the liquid");
            h.succeed();
        });
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool")
    public static void aVehicleTakesHydrogenOnlyIntoItsKindOfTank(GameTestHelper h) {
        FuelTankDesign c = FuelTankDesign.hydrogenCylinder(), t = FuelTankDesign.liquidHydrogenTank();
        FuelTankDesign cylinder = new FuelTankDesign(c.name(), c.lengthM(), c.widthM(), c.heightM(), c.wallM(), c.material(), c.fuel(), 0, c.kind(), c.insulation(),
                c.insulationM(), c.pressurePa(), c.safetyFactor());
        FuelTankDesign cold = new FuelTankDesign(t.name(), t.lengthM(), t.widthM(), t.heightM(), t.wallM(), t.material(), t.fuel(), 0, t.kind(), t.insulation(),
                t.insulationM(), t.pressurePa(), t.safetyFactor());
        VehicleBlueprint b = new VehicleBlueprint("hydrogen tanks", List.of(new VehicleBlueprint.Entry("cylinder", cylinder, Mount.ORIGIN),
                new VehicleBlueprint.Entry("cold", cold, Mount.at(new Vec3d(0, 0, 1)))), List.of());
        BlockPos o = h.absolutePos(BlockPos.ZERO);
        PartVehicle v = VehicleRegistry.PART_VEHICLE.get().create(h.getLevel());
        v.setBlueprint(b, new Vec3d(o.getX() + 8, o.getY() + 3, o.getZ() + 8), Orientation.IDENTITY);
        h.getLevel().addFreshEntity(v);
        h.assertTrue(Fuelling.refusal(v, HdfvFluids.HYDROGEN) == null && Fuelling.refusal(v, HdfvFluids.LIQUID_HYDROGEN) == null, "Both forms have their tank");
        int gas = Fuelling.fill(v, HdfvFluids.HYDROGEN, 30);
        h.assertTrue(gas == 30 && v.tankFuel(0).equals(HdfvFluids.HYDROGEN) && v.tankKg(1) == 0, "The gas into the cylinder only: " + gas);
        int cold2 = Fuelling.fill(v, HdfvFluids.LIQUID_HYDROGEN, 40);
        h.assertTrue(cold2 == 40 && v.tankFuel(1).equals(HdfvFluids.LIQUID_HYDROGEN), "The liquid into the cryogenic tank: " + cold2);
        h.assertTrue(Math.abs(v.tankKg(0) - 0.030 * HdfvFluids.of(HdfvFluids.HYDROGEN).densityKgPerM3()) < 1e-6, "30 l of 700 bar gas: " + v.tankKg(0));
        h.assertTrue(Fuelling.refusal(v, "diesel") != null, "No room for diesel: the cylinder takes none and the cryogenic tank holds hydrogen");
        v.discard();
        h.succeed();
    }

}
