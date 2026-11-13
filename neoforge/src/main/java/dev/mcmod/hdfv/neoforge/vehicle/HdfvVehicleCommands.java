package dev.mcmod.hdfv.neoforge.vehicle;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import dev.mcmod.aerocore.neoforge.vehicle.PartVehicle;
import dev.mcmod.hdfv.neoforge.damage.VehicleConditions;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.Comparator;
import java.util.List;
import dev.mcmod.aerocore.api.vehicle.VehicleBlueprint;
import dev.mcmod.aerocore.neoforge.vehicle.VehicleCommands;
import dev.mcmod.hdfv.part.SampleBlueprints;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * /hdfv vehicle samples: writes the sample blueprints into AeroCore's blueprint folder (spawn them with /aerocore vehicle spawn).
 * 0.25, for operators and tests: /hdfv vehicle condition reports how used each part of a vehicle is (the one the source
 * rides, else the nearest within 16 blocks); condition set &lt;part&gt; &lt;condition&gt; &lt;0..1&gt; sets one; condition
 * repair sets them all back to new.
 */
public final class HdfvVehicleCommands {
    private HdfvVehicleCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> tree() {
        return Commands.literal("vehicle").requires(source -> source.hasPermission(2))
                .then(Commands.literal("samples").executes(c -> samples(c.getSource())))
                .then(Commands.literal("condition").executes(c -> condition(c.getSource()))
                        .then(Commands.literal("set").then(Commands.argument("part", StringArgumentType.string()).then(Commands.argument("condition", StringArgumentType.word())
                                .then(Commands.argument("used", DoubleArgumentType.doubleArg(0, 1)).executes(c -> set(c.getSource(), StringArgumentType.getString(c, "part"),
                                        StringArgumentType.getString(c, "condition"), DoubleArgumentType.getDouble(c, "used")))))))
                        .then(Commands.literal("repair").executes(c -> repair(c.getSource()))));
    }

    private static final SimpleCommandExceptionType NO_VEHICLE = new SimpleCommandExceptionType(Component.translatable("command.hdfv.vehicle.condition.none"));
    private static final double SEARCH = 16;

    /** The vehicle the source rides, else the nearest within 16 blocks of where it stands. */
    private static PartVehicle target(CommandSourceStack source) throws CommandSyntaxException {
        if (source.getEntity() != null && source.getEntity().getVehicle() instanceof PartVehicle riding) { return riding; }
        Vec3 at = source.getPosition();
        PartVehicle v = source.getLevel().getEntitiesOfClass(PartVehicle.class, new AABB(at, at).inflate(SEARCH)).stream()
                .filter(x -> x.vehicle() != null).min(Comparator.comparingDouble(x -> x.distanceToSqr(at))).orElse(null);
        if (v == null) { throw NO_VEHICLE.create(); }
        return v;
    }

    private static String name(PartVehicle v) { return v.blueprint() == null ? "?" : v.blueprint().name(); }

    /** Each part's conditions, in per cent used. */
    private static int condition(CommandSourceStack source) throws CommandSyntaxException {
        PartVehicle v = target(source);
        List<VehicleConditions.Entry> entries = VehicleConditions.of(v);
        long worst = Math.round(100 * v.worstUsed());
        source.sendSuccess(() -> Component.translatable("command.hdfv.vehicle.condition.header", name(v), worst), false);
        for (VehicleConditions.Entry e : entries) {
            MutableComponent line = Component.empty();
            for (int k = 0; k < e.items().size(); k++) {
                VehicleConditions.Item i = e.items().get(k);
                if (k > 0) { line.append(", "); }
                line.append(i.name()).append(" " + Math.round(100 * i.used()) + " %");
            }
            source.sendSuccess(() -> Component.translatable("command.hdfv.vehicle.condition.part", e.id(), e.name(), line), false);
        }
        return entries.size();
    }

    private static int set(CommandSourceStack source, String part, String condition, double used) throws CommandSyntaxException {
        PartVehicle v = target(source);
        if (!v.setUsed(part, condition, used)) {
            StringBuilder has = new StringBuilder();
            for (VehicleConditions.Entry e : VehicleConditions.of(v)) {
                for (VehicleConditions.Item i : e.items()) { has.append(has.isEmpty() ? "" : ", ").append(e.id()).append(' ').append(i.condition()); }
            }
            source.sendFailure(Component.translatable("command.hdfv.vehicle.condition.unknown", part, condition, has.toString()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.hdfv.vehicle.condition.set", name(v), part, condition, Math.round(100 * used)), true);
        return 1;
    }

    private static int repair(CommandSourceStack source) throws CommandSyntaxException {
        PartVehicle v = target(source);
        int mended = 0;
        for (VehicleConditions.Entry e : VehicleConditions.of(v)) {
            for (VehicleConditions.Item i : e.items()) { if (i.used() > 0 && v.setUsed(e.id(), i.condition(), 0)) { mended++; } }
        }
        int count = mended;
        source.sendSuccess(() -> Component.translatable("command.hdfv.vehicle.condition.repaired", name(v), count), true);
        return mended;
    }

    /** Writes the sample blueprints that are not there yet. */
    private static int samples(CommandSourceStack source) {
        Path dir = VehicleCommands.folder(source.getServer());
        int written = 0;
        try {
            Files.createDirectories(dir);
            for (Map.Entry<String, VehicleBlueprint> e : SampleBlueprints.all().entrySet()) {
                Path file = dir.resolve(e.getKey() + ".json");
                if (Files.exists(file)) { continue; }
                Files.writeString(file, e.getValue().toJsonText(), StandardCharsets.UTF_8);
                written++;
            }
        } catch (IOException e) {
            source.sendFailure(Component.translatable("command.hdfv.vehicle.io", e.getMessage()));
            return 0;
        }
        int count = written;
        source.sendSuccess(() -> Component.translatable("command.hdfv.vehicle.samples", count, dir.toAbsolutePath().toString()), false);
        return count;
    }
}
