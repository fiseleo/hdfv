package dev.mcmod.hdfv.neoforge.vehicle;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
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

/** /hdfv vehicle samples: writes the sample blueprints into AeroCore's blueprint folder (spawn them with /aerocore vehicle spawn). */
public final class HdfvVehicleCommands {
    private HdfvVehicleCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> tree() {
        return Commands.literal("vehicle").requires(source -> source.hasPermission(2))
                .then(Commands.literal("samples").executes(c -> samples(c.getSource())));
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
