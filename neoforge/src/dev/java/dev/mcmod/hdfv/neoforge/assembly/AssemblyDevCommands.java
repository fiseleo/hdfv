package dev.mcmod.hdfv.neoforge.assembly;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.mcmod.aerocore.api.vehicle.VehicleBlueprint;
import dev.mcmod.hdfv.assembly.SampleLayouts;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Operators' and screenshots' shortcuts for the workshop and the assembly rooms (0.7), under /hdfv dev: open a block's
 * screen as if used, load a sample layout into a room controller, start its build without materials at some share
 * done (for looking at a vehicle half built); place a multiblock whole (0.8).
 */
public final class AssemblyDevCommands {
    private AssemblyDevCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> tree() {
        return Commands.literal("dev").requires(s -> s.hasPermission(2))
                .then(dev.mcmod.hdfv.neoforge.multiblock.MultiblockDevCommands.place())
                .then(dev.mcmod.hdfv.neoforge.airport.AirportDevCommands.tree())
                .then(dev.mcmod.hdfv.neoforge.power.StandDevCommands.tree())
                .then(dev.mcmod.hdfv.neoforge.sound.SoundDevServerCommands.tree())
                .then(dev.mcmod.hdfv.neoforge.petroleum.OilFieldCommands.tree())
                .then(dev.mcmod.hdfv.neoforge.paint.StickerDevCommands.tree())
                .then(dev.mcmod.hdfv.neoforge.paint.StickerDevCommands.booth())
                .then(dev.mcmod.hdfv.neoforge.refit.RefitDevCommands.refit())
                .then(dev.mcmod.hdfv.neoforge.vehicle.DriveDevCommands.tree())
                .then(Commands.literal("open").then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(c -> {
                    var player = c.getSource().getPlayerOrException();
                    BlockPos pos = BlockPosArgument.getLoadedBlockPos(c, "pos");
                    var state = player.level().getBlockState(pos);
                    state.useWithoutItem(player.level(), player, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
                    return 1;
                })))
                .then(Commands.literal("layout").then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .then(Commands.argument("sample", StringArgumentType.word()).executes(c -> {
                            if (!(c.getSource().getLevel().getBlockEntity(BlockPosArgument.getLoadedBlockPos(c, "pos")) instanceof RoomControllerBlockEntity be)) { return 0; }
                            String name = StringArgumentType.getString(c, "sample");
                            VehicleBlueprint l = SampleLayouts.all().get(name);
                            // 0.14: test layouts too, in development only (big_engine: what a room refuses)
                            if (l == null && !net.neoforged.fml.loading.FMLEnvironment.production && name.equals("big_engine")) { l = dev.mcmod.hdfv.drive.TestCars.tooBigEngine(); }
                            // 0.15: the six-wheeler on its frame; its shell alone (body_needs_frame); its axles hung 0.4 m high (touches_ground)
                            if (l == null && !net.neoforged.fml.loading.FMLEnvironment.production && name.equals("6x6")) { l = dev.mcmod.hdfv.drive.TestCars.sixBySix(); }
                            if (l == null && !net.neoforged.fml.loading.FMLEnvironment.production && name.equals("shell")) { l = dev.mcmod.hdfv.drive.TestCars.shellAlone(); }
                            if (l == null && !net.neoforged.fml.loading.FMLEnvironment.production && name.equals("sunk_6x6")) { l = dev.mcmod.hdfv.drive.TestCars.sunkSixBySix(); }
                            if (l == null) { c.getSource().sendFailure(Component.literal("Samples: " + SampleLayouts.all().keySet())); return 0; }
                            be.layout = l.toJsonText();
                            be.scan();
                            return 1;
                        }))))
                .then(Commands.literal("build").then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .then(Commands.argument("share", FloatArgumentType.floatArg(0, 1)).executes(c -> {
                            if (!(c.getSource().getLevel().getBlockEntity(BlockPosArgument.getLoadedBlockPos(c, "pos")) instanceof RoomControllerBlockEntity be)) { return 0; }
                            var plan = be.plan();
                            if (plan == null) { c.getSource().sendFailure(Component.literal("No layout")); return 0; }
                            // stock the room's stores with what the plan needs, then start as a player would
                            be.scan();
                            var stores = be.stores();
                            if (stores.isEmpty()) { c.getSource().sendFailure(Component.literal("No chest in the room")); return 0; }
                            for (var n : plan.parts()) { for (int i = 0; i < n.count(); i++) { Staging.give(dev.mcmod.hdfv.neoforge.machine.Workpieces.part(n.design()), stores, null); } }
                            for (var p : plan.pieces()) { Staging.give(Staging.workpieceOf(p.design(), p.workpiece()), stores, null); }
                            plan.stockKg().forEach((a, kg) -> {
                                int n = (int) Math.ceil(kg / dev.mcmod.hdfv.neoforge.HdfvConfig.kgPerIngot() - 1e-9);
                                while (n > 0) { int k = Math.min(n, 64); Staging.give(dev.mcmod.hdfv.neoforge.material.Stock.of(a, k), stores, null); n -= k; }
                            });
                            Component refused = be.start();
                            if (refused != null) { c.getSource().sendFailure(refused); return 0; }
                            be.workDone = (long) (FloatArgumentType.getFloat(c, "share") * be.workTotal);
                            HdfvVehicle v = be.vehicle();
                            if (v != null) { v.setBuilt(Math.min(be.built(), 0.999f)); }
                            return 1;
                        }))));
    }
}
