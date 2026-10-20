package dev.mcmod.hdfv.neoforge.client.sound;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.mcmod.aerocore.api.acoustics.Voice;
import dev.mcmod.aerocore.api.acoustics.Wav;
import dev.mcmod.aerocore.neoforge.client.sound.SoundRecorder;
import dev.mcmod.aerocore.neoforge.vehicle.PartVehicle;
import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.sound.SoundBench;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;

/**
 * Development sound commands (0.19, not registered in production), under /hdfvdev sound:
 * <ul>
 * <li>hud on|off, log on|off: SoundDebug.</li>
 * <li>record &lt;seconds&gt;: what hdfv streams, as sent to OpenAL (AeroCore's SoundRecorder), to
 *     {@code %USERPROFILE%\.mcmod\exports\sound\hdfv-<time>\} (one WAV per stream, a mix, index.json).</li>
 * <li>render &lt;rpm&gt; &lt;load&gt; &lt;seconds&gt;: the nearest vehicle's sound at an operating point (standing, every
 *     engine at rpm and load), heard at 7.5 m, offline (SoundBench) to {@code exports\sound\render-<name>-<rpm>.wav}.</li>
 * </ul>
 */
@EventBusSubscriber(modid = Hdfv.MOD_ID, value = Dist.CLIENT)
public final class SoundDevCommands {
    private SoundDevCommands() {}

    static Path exports() { return Path.of(System.getProperty("user.home"), ".mcmod", "exports", "sound"); }

    @SubscribeEvent static void register(RegisterClientCommandsEvent event) {
        if (FMLEnvironment.production) { return; }
        event.getDispatcher().register(Commands.literal("hdfvdev").then(Commands.literal("sound")
                .then(Commands.literal("hud").then(Commands.argument("on", StringArgumentType.word()).executes(c -> {
                    SoundDebug.hud = StringArgumentType.getString(c, "on").equals("on");
                    return 1;
                })))
                .then(Commands.literal("log").then(Commands.argument("on", StringArgumentType.word()).executes(c -> {
                    SoundDebug.log = StringArgumentType.getString(c, "on").equals("on");
                    return 1;
                })))
                .then(Commands.literal("record").then(Commands.argument("seconds", DoubleArgumentType.doubleArg(0.1, 60)).executes(c -> {
                    double s = DoubleArgumentType.getDouble(c, "seconds");
                    if (!SoundRecorder.start(s, "hdfv", dir -> say("hdfv sound recorded to " + dir))) { say("already recording"); }
                    return 1;
                })))
                .then(Commands.literal("render").then(Commands.argument("rpm", DoubleArgumentType.doubleArg(0, 30000))
                        .then(Commands.argument("load", DoubleArgumentType.doubleArg(0, 3)).then(Commands.argument("seconds", DoubleArgumentType.doubleArg(0.1, 30)).executes(c -> {
                            Minecraft mc = Minecraft.getInstance();
                            if (mc.player == null || mc.level == null) { return 0; }
                            PartVehicle v = mc.level.getEntitiesOfClass(PartVehicle.class, mc.player.getBoundingBox().inflate(32)).stream()
                                    .filter(x -> x.vehicle() != null).min(Comparator.comparingDouble(x -> x.distanceToSqr(mc.player))).orElse(null);
                            if (v == null) { say("no vehicle within 32 blocks"); return 0; }
                            double rpm = DoubleArgumentType.getDouble(c, "rpm"), load = DoubleArgumentType.getDouble(c, "load"), seconds = DoubleArgumentType.getDouble(c, "seconds");
                            String name = v.blueprint().name().replaceAll("[^A-Za-z0-9_.-]", "_");
                            float[] samples = SoundBench.render(v.vehicle(), new SoundBench.Point(rpm, load, Math.min(1, load), true, 1, 0, 0), 7.5, false, seconds,
                                    v.getUUID().getLeastSignificantBits() ^ v.getUUID().getMostSignificantBits());
                            Path file = exports().resolve(String.format(Locale.ROOT, "render-%s-%.0frpm-%.2f.wav", name, rpm, load));
                            try {
                                Wav.write(file, samples, Voice.RATE);
                                say("rendered " + file);
                            } catch (java.io.IOException e) {
                                say("could not write " + file + ": " + e.getMessage());
                            }
                            return 1;
                        })))))));
    }

    private static void say(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) { mc.player.displayClientMessage(Component.literal(text), false); }
    }
}
