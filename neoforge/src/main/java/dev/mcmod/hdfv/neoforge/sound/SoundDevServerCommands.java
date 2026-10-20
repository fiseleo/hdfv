package dev.mcmod.hdfv.neoforge.sound;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.mcmod.aerocore.api.acoustics.ProcessNoise;
import dev.mcmod.hdfv.sound.MachineSound;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.Locale;

/**
 * Development (0.19, AutoShot): {@code /hdfv dev sound hum <pos> <process> <power W> <load>} tells the players near that
 * the block entity at pos sounds as that process (any block entity: a chest stands in for a machine; a real machine
 * overwrites it on its next tick), {@code off} instead of a process silences it; {@code /hdfv dev sound event <pos>
 * <event> [argument]} plays a workshop event there.
 */
public final class SoundDevServerCommands {
    private SoundDevServerCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> tree() {
        return Commands.literal("sound")
                .then(Commands.literal("hum").then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .then(Commands.argument("process", StringArgumentType.word())
                                .executes(c -> hum(c.getSource(), BlockPosArgument.getLoadedBlockPos(c, "pos"), StringArgumentType.getString(c, "process"), 5000, 1))
                                .then(Commands.argument("power", DoubleArgumentType.doubleArg(0, 1e7)).then(Commands.argument("load", DoubleArgumentType.doubleArg(0, 1))
                                        .executes(c -> hum(c.getSource(), BlockPosArgument.getLoadedBlockPos(c, "pos"), StringArgumentType.getString(c, "process"),
                                                DoubleArgumentType.getDouble(c, "power"), DoubleArgumentType.getDouble(c, "load"))))))))
                .then(Commands.literal("event").then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .then(Commands.argument("event", StringArgumentType.word())
                                .executes(c -> event(c.getSource(), BlockPosArgument.getLoadedBlockPos(c, "pos"), StringArgumentType.getString(c, "event"), 1.6))
                                .then(Commands.argument("argument", DoubleArgumentType.doubleArg(0, 60))
                                        .executes(c -> event(c.getSource(), BlockPosArgument.getLoadedBlockPos(c, "pos"), StringArgumentType.getString(c, "event"),
                                                DoubleArgumentType.getDouble(c, "argument")))))));
    }

    private static int hum(CommandSourceStack source, BlockPos pos, String process, double powerW, double load) {
        var be = source.getLevel().getBlockEntity(pos);
        if (be == null) { source.sendFailure(Component.literal("No block entity at " + pos.toShortString())); return 0; }
        if (process.equals("off")) { SoundSync.report(be, null); source.sendSuccess(() -> Component.literal("Silent at " + pos.toShortString()), false); return 1; }
        ProcessNoise.Process p;
        try { p = ProcessNoise.Process.valueOf(process.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) { source.sendFailure(Component.literal("Processes: " + Arrays.toString(ProcessNoise.Process.values()).toLowerCase(Locale.ROOT))); return 0; }
        MachineSound.Hum hum = MachineSound.plant(p, powerW, load, true);
        if (p == ProcessNoise.Process.MILLING || p == ProcessNoise.Process.TURNING || p == ProcessNoise.Process.SAWING || p == ProcessNoise.Process.GRINDING
                || p == ProcessNoise.Process.STROKE || p == ProcessNoise.Process.GEAR_CUTTING || p == ProcessNoise.Process.DRILLING) {
            hum = new MachineSound.Hum(p, powerW, p == ProcessNoise.Process.MILLING ? 4 : p == ProcessNoise.Process.SAWING ? 200 : p == ProcessNoise.Process.GEAR_CUTTING ? 12 : 1,
                    p == ProcessNoise.Process.SAWING ? 0.5 : p == ProcessNoise.Process.GRINDING ? 30 : p == ProcessNoise.Process.GEAR_CUTTING ? 2 : 10,
                    p == ProcessNoise.Process.STROKE ? 1 : 0, load, true);
        }
        SoundSync.report(be, hum);
        MachineSound.Hum told = hum;
        source.sendSuccess(() -> Component.literal("Sounding at " + pos.toShortString() + ": " + told), false);
        return 1;
    }

    private static int event(CommandSourceStack source, BlockPos pos, String name, double argument) {
        MachineSound.Event e;
        try { e = MachineSound.Event.valueOf(name.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ex) { source.sendFailure(Component.literal("Events: " + Arrays.toString(MachineSound.Event.values()).toLowerCase(Locale.ROOT))); return 0; }
        SoundSync.event(source.getLevel(), Vec3.atCenterOf(pos), e, argument);
        source.sendSuccess(() -> Component.literal("Event " + name + " at " + pos.toShortString()), false);
        return 1;
    }
}
