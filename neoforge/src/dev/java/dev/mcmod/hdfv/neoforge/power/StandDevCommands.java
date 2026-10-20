package dev.mcmod.hdfv.neoforge.power;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.mcmod.hdfv.neoforge.fluid.HdfvFluidRegistry;
import dev.mcmod.hdfv.neoforge.machine.Workpieces;
import dev.mcmod.hdfv.part.engine.PistonEngineDesign;
import dev.mcmod.hdfv.part.engine.SampleEngines;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Development (0.11): /hdfv dev stand &lt;pos&gt; &lt;engine&gt; [load W] places an engine stand facing east with a
 * sample piston engine on it, its tank full of the engine's fuel, started, and (for looking at a loaded engine
 * without building a line) a test load on its crank. Waits for the engine's tables on the server thread.
 */
public final class StandDevCommands {
    private StandDevCommands() {}

    private static final Map<String, Supplier<PistonEngineDesign>> ENGINES = Map.of(
            "inline_four", SampleEngines::inlineFour, "inline_four_sohc", SampleEngines::inlineFourSohc, "v8_ohv", SampleEngines::vEightOhv,
            "turbo_diesel", SampleEngines::turboDiesel, "generator_diesel", SampleEngines::generatorDiesel);

    public static LiteralArgumentBuilder<CommandSourceStack> tree() {
        return Commands.literal("stand").then(Commands.argument("pos", BlockPosArgument.blockPos())
                .then(Commands.argument("engine", StringArgumentType.word())
                        .executes(c -> stand(c.getSource(), BlockPosArgument.getLoadedBlockPos(c, "pos"), StringArgumentType.getString(c, "engine"), 0))
                        .then(Commands.argument("load", DoubleArgumentType.doubleArg(0, 2e6))
                                .executes(c -> stand(c.getSource(), BlockPosArgument.getLoadedBlockPos(c, "pos"), StringArgumentType.getString(c, "engine"),
                                        DoubleArgumentType.getDouble(c, "load"))))));
    }

    private static int stand(CommandSourceStack source, BlockPos pos, String name, double loadW) {
        Supplier<PistonEngineDesign> engine = ENGINES.get(name);
        if (engine == null) { source.sendFailure(Component.literal("Engines: " + ENGINES.keySet())); return 0; }
        var level = source.getLevel();
        level.setBlockAndUpdate(pos, HdfvPower.block(PowerBlock.Kind.ENGINE_STAND).defaultBlockState().setValue(PowerBlock.FACING, Direction.EAST));
        if (!(level.getBlockEntity(pos) instanceof EngineStandBlockEntity stand)) { return 0; }
        PistonEngineDesign d = engine.get();
        stand.install(Workpieces.part(d));
        String fuel = d.acceptedFluids().contains("diesel") ? "diesel" : d.acceptedFluids().contains("gasoline") ? "gasoline" : d.acceptedFluids().iterator().next();
        stand.tank().fill(new FluidStack(HdfvFluidRegistry.fluid(fuel), EngineStandBlockEntity.TANK_LITRES), IFluidHandler.FluidAction.EXECUTE);
        stand.awaitReady();
        stand.testLoadW = loadW;
        stand.setRunning(true);
        source.sendSuccess(() -> Component.literal("Engine stand with " + name + " running" + (loadW > 0 ? " against " + Math.round(loadW) + " W" : "")), true);
        return 1;
    }
}
