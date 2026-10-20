package dev.mcmod.hdfv.neoforge.refit;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.mcmod.hdfv.look.LampRules;
import dev.mcmod.hdfv.look.LampSpec;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * The refit shop's development commands (0.16, AutoShot): /hdfv dev refit &lt;pos&gt; open|finish, and a lamp put on the
 * body by its place (lamp &lt;role&gt; &lt;style&gt; side &lt;x&gt; &lt;t&gt; ..., or front|rear &lt;across&gt; &lt;up&gt; ...) where a
 * screenshot cannot aim a click.
 */
public final class RefitDevCommands {
    private RefitDevCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> refit() {
        return Commands.literal("refit").then(Commands.argument("pos", BlockPosArgument.blockPos())
                .then(Commands.literal("open").executes(c -> {
                    var pos = BlockPosArgument.getLoadedBlockPos(c, "pos");
                    if (!(c.getSource().getLevel().getBlockEntity(pos) instanceof RefitShopBlockEntity be)) { c.getSource().sendFailure(Component.literal("No refit shop there")); return 0; }
                    be.scan();
                    c.getSource().getPlayerOrException().openMenu(be, buf -> buf.writeBlockPos(pos));
                    return 1;
                }))
                .then(Commands.literal("finish").executes(c -> {
                    var pos = BlockPosArgument.getLoadedBlockPos(c, "pos");
                    if (!(c.getSource().getLevel().getBlockEntity(pos) instanceof RefitShopBlockEntity be) || !be.working) { c.getSource().sendFailure(Component.literal("No job going there")); return 0; }
                    be.workDone = be.workTotal;
                    return 1;
                }))
                .then(Commands.literal("unlamp").then(Commands.argument("index", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0)).executes(c -> {
                    var pos = BlockPosArgument.getLoadedBlockPos(c, "pos");
                    if (!(c.getSource().getLevel().getBlockEntity(pos) instanceof RefitShopBlockEntity be)) { c.getSource().sendFailure(Component.literal("No refit shop there")); return 0; }
                    Component refused = be.apply(new RefitShopBlockEntity.Edit(RefitShopBlockEntity.Op.LAMP_REMOVE, "body", "", "", "",
                            com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "index"), 0, null), c.getSource().getPlayer());
                    if (refused != null) { c.getSource().sendFailure(refused); return 0; }
                    return 1;
                })))
                .then(Commands.literal("lamp").then(Commands.argument("role", StringArgumentType.word()).then(Commands.argument("style", StringArgumentType.word())
                        .then(Commands.argument("face", StringArgumentType.word()).then(Commands.argument("a", DoubleArgumentType.doubleArg()).then(Commands.argument("b", DoubleArgumentType.doubleArg())
                        .then(Commands.argument("pair", BoolArgumentType.bool()).then(Commands.argument("size", DoubleArgumentType.doubleArg(LampSpec.MIN_SIZE_M, LampSpec.MAX_SIZE_M)).executes(RefitDevCommands::lamp))))))))));
    }

    private static int lamp(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var pos = BlockPosArgument.getLoadedBlockPos(c, "pos");
        if (!(c.getSource().getLevel().getBlockEntity(pos) instanceof RefitShopBlockEntity be)) { c.getSource().sendFailure(Component.literal("No refit shop there")); return 0; }
        LampSpec.Face face = LampSpec.Face.valueOf(StringArgumentType.getString(c, "face").toUpperCase(Locale.ROOT));
        double a = DoubleArgumentType.getDouble(c, "a"), b = DoubleArgumentType.getDouble(c, "b");
        LampSpec l = new LampSpec(LampRules.Role.valueOf(StringArgumentType.getString(c, "role").toUpperCase(Locale.ROOT)),
                LampSpec.Style.valueOf(StringArgumentType.getString(c, "style").toUpperCase(Locale.ROOT)), face,
                face == LampSpec.Face.SIDE ? a : 0, face == LampSpec.Face.SIDE ? b : 0, face == LampSpec.Face.SIDE ? 0 : a, face == LampSpec.Face.SIDE ? 0 : b, 0,
                BoolArgumentType.getBool(c, "pair"), DoubleArgumentType.getDouble(c, "size"), "", "", 0, 0);
        Component refused = be.apply(new RefitShopBlockEntity.Edit(RefitShopBlockEntity.Op.LAMP_ADD, "body", "", "", "", -1, 0, l), c.getSource().getPlayer());
        if (refused != null) { c.getSource().sendFailure(refused); return 0; }
        return 1;
    }
}
