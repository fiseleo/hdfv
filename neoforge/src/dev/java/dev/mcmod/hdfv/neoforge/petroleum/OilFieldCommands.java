package dev.mcmod.hdfv.neoforge.petroleum;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * /hdfv dev oilfield depth pressure light (0.8): sets a field 60 m across its radius at the operator's feet, for
 * trying the rig and the jack (pressure: times the water column's; light: light sweet crude). /hdfv dev oilfield
 * produced m3 sets what the field there has given.
 */
public final class OilFieldCommands {
    private OilFieldCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> tree() {
        return Commands.literal("oilfield")
                .then(Commands.literal("produced").then(Commands.argument("m3", DoubleArgumentType.doubleArg(0)).executes(c -> {
                    var level = c.getSource().getLevel();
                    BlockPos at = BlockPos.containing(c.getSource().getPosition());
                    var f = OilFieldData.of(level).field(level, at);
                    if (f == null) { c.getSource().sendFailure(Component.literal("No field here")); return 0; }
                    OilFieldData.of(level).setProduced(f.id(), DoubleArgumentType.getDouble(c, "m3"));
                    return 1;
                })))
                .then(Commands.argument("depth", DoubleArgumentType.doubleArg(20, 3000))
                        .then(Commands.argument("pressure", DoubleArgumentType.doubleArg(0.5, 2))
                                .then(Commands.argument("light", BoolArgumentType.bool()).executes(c -> {
                                    var level = c.getSource().getLevel();
                                    BlockPos at = BlockPos.containing(c.getSource().getPosition());
                                    double depth = DoubleArgumentType.getDouble(c, "depth");
                                    force(level, at, depth, DoubleArgumentType.getDouble(c, "pressure"), BoolArgumentType.getBool(c, "light"), 80e6);
                                    c.getSource().sendSuccess(() -> Component.literal("Oil field set under " + at.toShortString()), false);
                                    return 1;
                                }))));
    }

    /** Sets a field centred at a block (radius 60 m, 15 m of pay, porosity 0.2, saturation 0.75, 100 mD). */
    public static void force(net.minecraft.server.level.ServerLevel level, BlockPos at, double depthM, double pressureFactor, boolean light, double rockPa) {
        OilFieldData.of(level).force(new OilFieldData.Forced(at.getX(), at.getZ(), 60, depthM, 15, 0.2, 0.75, 100, light, 1e4 * depthM * pressureFactor, rockPa));
    }
}
