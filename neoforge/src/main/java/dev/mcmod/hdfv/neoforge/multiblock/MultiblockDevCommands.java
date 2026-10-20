package dev.mcmod.hdfv.neoforge.multiblock;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.mcmod.hdfv.structure.Pattern;
import dev.mcmod.hdfv.structure.Structures;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

/**
 * /hdfv dev place pattern [variant] [repeats] (0.8): a multiblock built whole three blocks in front of the player, its
 * controller facing them; variant names the CNC's modules (vmc_3 ... mill_turn_12) or (0.9) a machine tool's drive and
 * accessories (motor, pulley, bare, bare_pulley); the number is its repeats (a tank's courses, a column's height) or a
 * machine tool's length.
 */
public final class MultiblockDevCommands {
    private MultiblockDevCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> place() {
        return Commands.literal("place").then(Commands.argument("pattern", StringArgumentType.word())
                .executes(c -> place(c.getSource(), StringArgumentType.getString(c, "pattern"), "", 1))
                .then(Commands.argument("variant", StringArgumentType.word())
                        .executes(c -> place(c.getSource(), StringArgumentType.getString(c, "pattern"), StringArgumentType.getString(c, "variant"), 1))
                        .then(Commands.argument("repeats", IntegerArgumentType.integer(1, 64))
                                .executes(c -> place(c.getSource(), StringArgumentType.getString(c, "pattern"), StringArgumentType.getString(c, "variant"),
                                        IntegerArgumentType.getInteger(c, "repeats"))))));
    }

    private static int place(CommandSourceStack source, String name, String variant, int repeats) {
        Pattern p = Structures.ALL.get(name);
        if (p == null) { source.sendFailure(Component.literal("Patterns: " + Structures.ALL.keySet())); return 0; }
        Map<Character, String> choices;
        try {
            choices = name.equals("cnc") && !variant.isEmpty() ? HdfvMultiblocks.cncModules(variant)
                    : Structures.ALL.get(name) != null && dev.mcmod.hdfv.manufacture.ToolConfig.STATIONS.stream().anyMatch(s -> Structures.machine(s) == p)
                    ? HdfvMultiblocks.machineChoices(p, variant) : Map.of();
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
        Vec3 look = source.getRotation() == null ? Vec3.ZERO : Vec3.directionFromRotation(0, source.getRotation().y);
        Direction toward = Direction.getNearest(look.x, 0, look.z);
        BlockPos at = BlockPos.containing(source.getPosition()).relative(toward, 3);
        // a pattern that lengthens across takes the number as its length; one that grows up, as its repeats
        boolean across = p.maxLength() > 1;
        HdfvMultiblocks.place(source.getLevel(), p, at, toward.getOpposite(), across ? 1 : Math.clamp(repeats, p.minRepeats(), p.maxRepeats()),
                across ? Math.clamp(repeats, p.minLength(), p.maxLength()) : 1, choices);
        source.sendSuccess(() -> Component.literal("Placed " + name + " " + variant + " at " + at.toShortString()), false);
        return 1;
    }
}
