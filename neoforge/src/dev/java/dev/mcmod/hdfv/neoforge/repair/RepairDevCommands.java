package dev.mcmod.hdfv.neoforge.repair;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.network.chat.Component;

/**
 * The repair shop's development commands (0.25, AutoShot): /hdfv dev repair &lt;pos&gt; open | all | pick &lt;part/condition&gt; |
 * start | finish (a job going is done at once).
 */
public final class RepairDevCommands {
    private RepairDevCommands() {}

    private static RepairShopBlockEntity shop(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var pos = BlockPosArgument.getLoadedBlockPos(c, "pos");
        if (!(c.getSource().getLevel().getBlockEntity(pos) instanceof RepairShopBlockEntity be)) { c.getSource().sendFailure(Component.literal("No repair shop there")); return null; }
        return be;
    }

    private static int act(CommandContext<CommandSourceStack> c, RepairShopBlockEntity.Edit e) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        RepairShopBlockEntity be = shop(c);
        if (be == null) { return 0; }
        be.scan();
        Component refused = be.apply(e, c.getSource().getPlayer());
        if (refused != null) { c.getSource().sendFailure(refused); return 0; }
        return 1;
    }

    public static LiteralArgumentBuilder<CommandSourceStack> repair() {
        return Commands.literal("repair").then(Commands.argument("pos", BlockPosArgument.blockPos())
                .then(Commands.literal("open").executes(c -> {
                    RepairShopBlockEntity be = shop(c);
                    if (be == null) { return 0; }
                    be.scan();
                    c.getSource().getPlayerOrException().openMenu(be, buf -> buf.writeBlockPos(be.getBlockPos()));
                    return 1;
                }))
                .then(Commands.literal("all").executes(c -> act(c, RepairShopBlockEntity.Edit.of(RepairShopBlockEntity.Op.ALL))))
                .then(Commands.literal("start").executes(c -> act(c, RepairShopBlockEntity.Edit.of(RepairShopBlockEntity.Op.START))))
                .then(Commands.literal("pick").then(Commands.argument("key", StringArgumentType.greedyString())
                        .executes(c -> act(c, new RepairShopBlockEntity.Edit(RepairShopBlockEntity.Op.TOGGLE, StringArgumentType.getString(c, "key"))))))
                .then(Commands.literal("finish").executes(c -> {
                    RepairShopBlockEntity be = shop(c);
                    if (be == null || !be.working) { c.getSource().sendFailure(Component.literal("No job going there")); return 0; }
                    be.workDone = be.workTotal;
                    return 1;
                })));
    }
}
