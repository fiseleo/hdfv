package dev.mcmod.hdfv.neoforge.paint;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.VehicleBlueprint;
import dev.mcmod.aerocore.neoforge.vehicle.PartVehicle;
import dev.mcmod.hdfv.look.Sticker;
import dev.mcmod.hdfv.paint.Repaint;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Development commands for stickers (0.13), under /hdfv dev: put a sticker on a vehicle's part without the paint booth
 * (for tests and screenshots), or clear a part's. The picture is named by at least 8 digits of its SHA-256 or "latest" (uploaded
 * first); its height over width comes from the server's record. A body's sticker is placed by x and t, a wing's by
 * eta and the chord fraction (upper surface, port; its pair on the other half). Taken through replaceBlueprint: the
 * vehicle keeps its place and motion.
 * <pre>
 * /hdfv dev sticker add &lt;vehicle&gt; &lt;part&gt; &lt;picture&gt; &lt;width&gt; &lt;x|eta&gt; &lt;t|chord&gt; [rotation] [pair]
 * /hdfv dev sticker clear &lt;vehicle&gt; &lt;part&gt;
 * </pre>
 */
public final class StickerDevCommands {
    private StickerDevCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> tree() {
        return Commands.literal("sticker")
                .then(Commands.literal("add").then(Commands.argument("vehicle", EntityArgument.entity()).then(Commands.argument("part", StringArgumentType.word())
                        .then(Commands.argument("picture", StringArgumentType.word()).then(Commands.argument("width", DoubleArgumentType.doubleArg(0.02, 20))
                                .then(Commands.argument("a", DoubleArgumentType.doubleArg()).then(Commands.argument("b", DoubleArgumentType.doubleArg())
                                        .executes(c -> add(c, 0, false))
                                        .then(Commands.argument("rotation", DoubleArgumentType.doubleArg(-180, 180))
                                                .executes(c -> add(c, DoubleArgumentType.getDouble(c, "rotation"), false))
                                                .then(Commands.argument("pair", BoolArgumentType.bool())
                                                        .executes(c -> add(c, DoubleArgumentType.getDouble(c, "rotation"), BoolArgumentType.getBool(c, "pair"))))))))))))
                .then(Commands.literal("clear").then(Commands.argument("vehicle", EntityArgument.entity()).then(Commands.argument("part", StringArgumentType.word())
                        .executes(StickerDevCommands::clear))));
    }

    /**
     * The paint booth for tests and screenshots: open a booth's screen for the player running it, or finish its job now.
     * <pre>
     * /hdfv dev booth &lt;pos&gt; open|finish
     * </pre>
     */
    public static LiteralArgumentBuilder<CommandSourceStack> booth() {
        return Commands.literal("booth").then(Commands.argument("pos", net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                .then(Commands.literal("open").executes(c -> {
                    var pos = net.minecraft.commands.arguments.coordinates.BlockPosArgument.getLoadedBlockPos(c, "pos");
                    if (!(c.getSource().getLevel().getBlockEntity(pos) instanceof PaintBoothBlockEntity be)) { c.getSource().sendFailure(Component.literal("No paint booth there")); return 0; }
                    be.scan();
                    c.getSource().getPlayerOrException().openMenu(be, buf -> buf.writeBlockPos(pos));
                    return 1;
                }))
                .then(Commands.literal("finish").executes(c -> {
                    var pos = net.minecraft.commands.arguments.coordinates.BlockPosArgument.getLoadedBlockPos(c, "pos");
                    if (!(c.getSource().getLevel().getBlockEntity(pos) instanceof PaintBoothBlockEntity be) || !be.working) { c.getSource().sendFailure(Component.literal("No job going there")); return 0; }
                    be.workDone = be.workTotal;
                    return 1;
                })));
    }

    private static int add(CommandContext<CommandSourceStack> c, double rotation, boolean pair) throws CommandSyntaxException {
        CommandSourceStack s = c.getSource();
        String name = StringArgumentType.getString(c, "picture").toLowerCase(Locale.ROOT);
        // "latest": the picture uploaded last (a test's, whose name it need not know)
        List<DecalIndex.Entry> found = name.equals("latest")
                ? DecalIndex.of(s.getServer()).entries(null).stream().max(java.util.Comparator.comparingLong(DecalIndex.Entry::time)).map(List::of).orElse(List.of())
                : name.length() >= 8 && name.matches("[0-9a-f]+") ? DecalIndex.of(s.getServer()).starting(name) : List.of();
        if (found.size() != 1) { s.sendFailure(Component.literal(found.size() + " pictures match " + name)); return 0; }
        DecalIndex.Entry picture = found.get(0);
        return change(c, d -> {
            double a = DoubleArgumentType.getDouble(c, "a"), b = DoubleArgumentType.getDouble(c, "b"), width = DoubleArgumentType.getDouble(c, "width");
            List<Sticker> next = new ArrayList<>(Repaint.stickers(d));
            Sticker.Place place = d instanceof dev.mcmod.hdfv.part.air.WingDesign
                    ? new Sticker.OnWing(Math.clamp(a, 0, 1), Math.clamp(b, 0, 1), Sticker.Surface.UPPER, Sticker.Side.PORT) : new Sticker.OnBody(a, b);
            next.add(new Sticker(picture.sha(), (double) picture.height() / picture.width(), place, width, rotation, false, pair, next.size() % Sticker.LAYERS));
            return Repaint.withStickers(d, next);
        });
    }

    private static int clear(CommandContext<CommandSourceStack> c) throws CommandSyntaxException { return change(c, d -> Repaint.withStickers(d, List.of())); }

    private static int change(CommandContext<CommandSourceStack> c, java.util.function.UnaryOperator<PartDesign> how) throws CommandSyntaxException {
        CommandSourceStack s = c.getSource();
        Entity e = EntityArgument.getEntity(c, "vehicle");
        if (!(e instanceof PartVehicle v) || v.blueprint() == null) { s.sendFailure(Component.literal("Not a part vehicle")); return 0; }
        String part = StringArgumentType.getString(c, "part");
        VehicleBlueprint bp = v.blueprint();
        PartDesign d = bp.parts().stream().filter(p -> p.id().equals(part)).map(VehicleBlueprint.Entry::design).findFirst().orElse(null);
        if (d == null || !Repaint.takesStickers(d)) { s.sendFailure(Component.literal("No body or wing " + part + " (parts: " + bp.parts().stream().map(VehicleBlueprint.Entry::id).toList() + ")")); return 0; }
        VehicleBlueprint next;
        try {
            next = Repaint.withPart(bp, part, how.apply(d));
        } catch (RuntimeException ex) {
            s.sendFailure(Component.literal(String.valueOf(ex.getMessage())));
            return 0;
        }
        if (!v.replaceBlueprint(next)) { s.sendFailure(Component.literal("The vehicle did not take it (see the log)")); return 0; }
        s.sendSuccess(() -> Component.literal(part + ": " + Repaint.stickers(next.parts().stream().filter(p -> p.id().equals(part)).findFirst().orElseThrow().design()).size() + " stickers"), true);
        return 1;
    }
}
