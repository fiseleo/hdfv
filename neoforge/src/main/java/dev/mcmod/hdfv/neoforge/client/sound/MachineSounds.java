package dev.mcmod.hdfv.neoforge.client.sound;

import dev.mcmod.aerocore.api.acoustics.Loudness;
import dev.mcmod.aerocore.api.acoustics.Propagation;
import dev.mcmod.aerocore.neoforge.client.sound.LiveSound;
import dev.mcmod.aerocore.neoforge.client.sound.RuntimeSounds;
import dev.mcmod.aerocore.neoforge.client.sound.SynthInstance;
import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.neoforge.HdfvClientConfig;
import dev.mcmod.hdfv.neoforge.sound.SoundSync;
import dev.mcmod.hdfv.sound.MachineSound;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * The workshop and the plant on a client (0.19): what the server says hdfv's blocks are doing (SoundSync) played as
 * loops on ordinary channels, and their short sounds as they come.
 * <ul>
 * <li>A loop is made once for a kind (the process, its power to 3 dB, teeth, speed to a third of an octave, idle or
 *     working, one of four seeds) on a background thread, then played looped: its pitch makes up the speed within the
 *     third, its gain is AeroCore's loudness map of its level at the listener (spherical spreading from its sound
 *     power) times hdfv's machine volume. The {@link #MAX_LOOPS} loudest within {@link #RANGE} play.</li>
 * <li>Loops not played for a minute are dropped (their samples; OpenAL's copy goes at the next resource reload).</li>
 * <li>Events (a door, a weld, a rivet, the hoist, a gusher, a buzzer) are made the first time each variant is needed,
 *     then played once where they happen, if they were told less than a second ago.</li>
 * </ul>
 */
@EventBusSubscriber(modid = Hdfv.MOD_ID, value = Dist.CLIENT)
public final class MachineSounds {
    private MachineSounds() {}

    /** How far machines are heard (blocks), how many loops and events play at once, the quietest heard (dB at the listener). */
    static final double RANGE = 48, EVENT_RANGE = 96, QUIET_DB = 15;
    static final int MAX_LOOPS = 24, MAX_EVENTS = 16, LOOP_SECONDS = 2;

    /** One block's loop. */
    static final class Loop {
        final BlockPos pos;
        MachineSound.Hum hum;
        ResourceLocation id;
        LiveSound live;
        double heardDb = -100, gain, distance;
        float pitch = 1;
        final SoundPaths.Cached path = new SoundPaths.Cached();
        Loop(BlockPos pos) { this.pos = pos; }
    }

    private static final Map<Long, Loop> LOOPS = new HashMap<>();
    private static final Set<ResourceLocation> BAKING = new HashSet<>();
    private static final Map<ResourceLocation, Long> LAST_USED = new HashMap<>();
    /** Events playing, and the tick each is over. */
    private static final Map<SynthInstance, Long> EVENTS = new HashMap<>();
    private static ClientLevel level;
    private static long ticks;
    private static double temperatureK = 293, humidity = 70;

    /** The loops followed now, loudest first (the debug overlay). */
    static List<Loop> loops() {
        List<Loop> l = new ArrayList<>(LOOPS.values());
        l.sort(Comparator.comparingDouble((Loop x) -> -x.heardDb));
        return l;
    }

    @SubscribeEvent static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != level) { stopAll(); SoundSync.forget(); level = mc.level; }
        if (level == null || mc.player == null || mc.isPaused()) { return; }
        ticks++;
        Vec3 ear = mc.gameRenderer.getMainCamera().getPosition();
        double volume = HdfvClientConfig.machinesVolume();
        BlockPos here = BlockPos.containing(ear);
        temperatureK = 273.15 + Math.clamp(-10 + 30 * level.getBiome(here).value().getBaseTemperature(), -30, 45);
        humidity = level.isRainingAt(here) ? 100 : 70;

        // what is sounding, as told; blocks gone are forgotten
        Set<Long> heard = new HashSet<>();
        for (var it = SoundSync.heard().long2ObjectEntrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            BlockPos pos = BlockPos.of(e.getLongKey());
            MachineSound.Hum hum = e.getValue();
            if (level.isLoaded(pos) && level.getBlockEntity(pos) == null) { it.remove(); continue; }
            if (hum.process() == null) { continue; }   // an engine stand: VehicleSounds streams it
            double d = ear.distanceTo(Vec3.atCenterOf(pos));
            if (d > RANGE) { continue; }
            Loop loop = LOOPS.computeIfAbsent(e.getLongKey(), k -> new Loop(pos));
            loop.hum = hum;
            loop.distance = d;
            loop.heardDb = Propagation.levelAt(hum.levelDb(), 0, Math.max(1, d));
            heard.add(e.getLongKey());
        }
        List<Loop> order = new ArrayList<>();
        for (Iterator<Map.Entry<Long, Loop>> it = LOOPS.entrySet().iterator(); it.hasNext(); ) {
            Loop l = it.next().getValue();
            if (!heard.contains(l.pos.asLong())) { stop(l); it.remove(); continue; }
            order.add(l);
        }
        order.sort(Comparator.comparingDouble((Loop x) -> -x.heardDb));
        for (int i = 0; i < order.size(); i++) {
            Loop l = order.get(i);
            if (i >= MAX_LOOPS || l.heardDb < QUIET_DB || volume <= 0) { stop(l); continue; }
            play(l, volume);
        }

        // short sounds as they come
        for (SoundSync.EventPayload p; (p = SoundSync.nextEvent()) != null; ) { event(p, ear, volume); }
        EVENTS.values().removeIf(end -> end < ticks);
        if (ticks % 200 == 0) { forgetUnused(); }
    }

    private static void play(Loop l, double volume) {
        MachineSound.Hum h = l.hum;
        double rev = h.revPerS() > 0 ? h.revPerS() : h.strokeHz();
        double bin = rev > 0 ? Math.pow(2, Math.round(Math.log(rev) / Math.log(2) * 3) / 3.0) : 0;
        int powerBin = (int) Math.round(10 * Math.log10(Math.max(50, h.powerW())) / 3);
        String loadBin = h.load() < 0.15 ? "idle" : h.load() < 0.6 ? "half" : "work";
        int variant = (int) Math.floorMod(l.pos.asLong() * 0x9E3779B97F4A7C15L >>> 40, 4);
        ResourceLocation id = RuntimeSounds.id(Hdfv.MOD_ID, String.format(Locale.ROOT, "machine/%s_p%d_t%d_r%s_%s_%d",
                h.process().name().toLowerCase(Locale.ROOT), powerBin, h.teeth(), bin > 0 ? String.format(Locale.ROOT, "%.0f", bin * 1000) : "0", loadBin, variant));
        LAST_USED.put(id, ticks);
        if (!RuntimeSounds.defined(id)) {
            bake(id, h, bin, rev, variant);
            return;
        }
        if (l.live == null || !id.equals(l.id)) {
            if (l.live != null) { l.live.stop(); }
            l.live = LiveSound.looped(id, SoundSource.BLOCKS);
            l.id = id;
            SoundDebug.log("machine {} at {}: {} dB", id.getPath(), l.pos.toShortString(), Math.round(l.heardDb));
            Cues.subtitle(Cues.machine(h.process()), Vec3.atCenterOf(l.pos), 24);
        }
        l.pitch = bin > 0 ? (float) Math.clamp(rev / bin, 0.5, 2) : 1;
        l.gain = Math.min(1, Loudness.gain(l.heardDb) * volume);
        Vec3 at = Vec3.atCenterOf(l.pos);
        l.live.update(at.x, at.y, at.z, (float) l.gain, l.pitch, false);
        Vec3 ear = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        l.path.update(level, ear, at, l.distance, true, temperatureK, humidity, VehicleSounds.listenerCabin());
        l.path.apply(l.live.instance());
    }

    /** Makes a loop on a background thread (its hum at the bin's speed). */
    private static void bake(ResourceLocation id, MachineSound.Hum h, double bin, double rev, int variant) {
        if (!BAKING.add(id)) { return; }
        double scale = bin > 0 && rev > 0 ? bin / rev : 1;
        MachineSound.Hum at = new MachineSound.Hum(h.process(), h.powerW(), h.teeth(), h.revPerS() * scale, h.strokeHz() * scale,
                h.load() < 0.15 ? 0 : h.load() < 0.6 ? 0.5 : 1, true);
        CompletableFuture.supplyAsync(() -> MachineSound.bake(at, LOOP_SECONDS, 7919L * (variant + 1) + h.process().ordinal()), Util.backgroundExecutor())
                .whenCompleteAsync((samples, error) -> {
                    BAKING.remove(id);
                    if (samples != null) { RuntimeSounds.define(id, samples); }
                    else if (error != null) { VehicleSounds.logger().warn("hdfv: could not make the sound {}", id, error); }
                }, Minecraft.getInstance());
    }

    private static void event(SoundSync.EventPayload p, Vec3 ear, double volume) {
        double d = ear.distanceTo(p.at());
        if (d > EVENT_RANGE || volume <= 0 || EVENTS.size() >= MAX_EVENTS) { return; }
        int variant = p.event() == MachineSound.Event.DOOR ? (int) Math.round(p.argument() * 2) : (int) (ticks % 4);
        ResourceLocation id = RuntimeSounds.id(Hdfv.MOD_ID, "event/" + p.event().name().toLowerCase(Locale.ROOT) + "_" + variant);
        long told = ticks;
        Runnable start = () -> {
            if (ticks - told > 20 || EVENTS.size() >= MAX_EVENTS) { return; }
            Made made = EVENT_LEVELS.get(id);
            if (made == null) { return; }
            Vec3 now = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            double heardDb = Propagation.levelAt(made.levelDb(), 0, Math.max(1, now.distanceTo(p.at())));
            SynthInstance s = SynthInstance.buffered(id, SoundSource.BLOCKS, false);
            s.at(p.at().x, p.at().y, p.at().z);
            s.volume((float) Math.min(1, Loudness.gain(heardDb) * HdfvClientConfig.machinesVolume()));
            Minecraft.getInstance().getSoundManager().play(s);
            if (level != null) {
                SoundPaths.Cached path = new SoundPaths.Cached();
                path.update(level, now, p.at(), now.distanceTo(p.at()), true, temperatureK, humidity, VehicleSounds.listenerCabin());
                path.apply(s);
            }
            EVENTS.put(s, ticks + (long) Math.ceil(made.seconds() * 20) + 10);
            SoundDebug.log("event {} at {} m: {} dB", id.getPath(), Math.round(d), Math.round(heardDb));
            Cues.subtitle(Cues.event(p.event()), p.at(), 32);
        };
        LAST_USED.put(id, ticks);
        if (RuntimeSounds.defined(id) && EVENT_LEVELS.containsKey(id)) { start.run(); return; }
        if (!BAKING.add(id)) { return; }
        double argument = p.event() == MachineSound.Event.DOOR ? variant / 2.0 : 0;
        CompletableFuture.supplyAsync(() -> MachineSound.event(p.event(), argument, 104729L * (variant + 1)), Util.backgroundExecutor())
                .whenCompleteAsync((sound, error) -> {
                    BAKING.remove(id);
                    if (sound == null) { return; }
                    RuntimeSounds.define(id, sound.samples());
                    EVENT_LEVELS.put(id, new Made(sound.levelDb(), sound.samples().length / (double) dev.mcmod.aerocore.api.acoustics.Voice.RATE));
                    start.run();
                }, Minecraft.getInstance());
    }

    /** An event's sound made: its level (dB of sound power) and length (s). */
    private record Made(double levelDb, double seconds) {}
    private static final Map<ResourceLocation, Made> EVENT_LEVELS = new HashMap<>();

    /** Drops sounds not used for a minute (not those playing). */
    private static void forgetUnused() {
        Set<ResourceLocation> playing = new HashSet<>();
        for (Loop l : LOOPS.values()) { if (l.id != null) { playing.add(l.id); } }
        for (Iterator<Map.Entry<ResourceLocation, Long>> it = LAST_USED.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            if (ticks - e.getValue() > 1200 && !playing.contains(e.getKey())) {
                RuntimeSounds.undefine(e.getKey());
                EVENT_LEVELS.remove(e.getKey());
                it.remove();
            }
        }
    }

    private static void stop(Loop l) {
        if (l.live != null) { l.live.stop(); l.live = null; l.id = null; }
        l.gain = 0;
    }

    private static void stopAll() {
        for (Loop l : LOOPS.values()) { stop(l); }
        LOOPS.clear();
        for (SynthInstance s : EVENTS.keySet()) { s.finish(); }
        EVENTS.clear();
    }

    /** How many loops play now. */
    static int playing() {
        int n = 0;
        for (Loop l : LOOPS.values()) { if (l.live != null && l.live.playing()) { n++; } }
        return n;
    }
}
