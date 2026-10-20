package dev.mcmod.hdfv.neoforge.client.sound;

import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.acoustics.Loudness;
import dev.mcmod.aerocore.api.acoustics.Propagation;
import dev.mcmod.aerocore.api.acoustics.SourceTrack;
import dev.mcmod.aerocore.api.acoustics.Transmission;
import dev.mcmod.aerocore.neoforge.client.sound.Efx;
import dev.mcmod.aerocore.neoforge.client.sound.Listening;
import dev.mcmod.aerocore.neoforge.client.sound.LiveSound;
import dev.mcmod.aerocore.neoforge.client.sound.RuntimeSounds;
import dev.mcmod.aerocore.neoforge.vehicle.FarSounds;
import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.neoforge.HdfvClientConfig;
import dev.mcmod.hdfv.sound.VehicleSound;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Vehicles heard beyond the tracking range (0.19): what AeroCore's FarSounds tells, each played as a loop of a typical
 * voice of its kind ({@link VehicleSound#far}) at its engine's speed on a grid of three an octave (pitch makes up the
 * rest and the Doppler factor). Where it is between the server's reports is extrapolated from its velocity; a
 * {@link SourceTrack} gives the travel time and Doppler shift, so a far jet is heard where it was seconds ago. Its
 * level is spherical spreading from the server's sound power; the air takes its high notes (EFX low-pass) and a closed
 * cabin the listener sits in its share. At most {@link #MAX} play; a vehicle that comes within tracking range is
 * played by VehicleSounds instead.
 */
final class FarVehicles {
    private FarVehicles() {}

    static final int MAX = 8;
    static final double QUIET_DB = 20;

    static final class Far {
        final int key;
        final SourceTrack track = new SourceTrack();
        LiveSound loop;
        ResourceLocation id;
        SourceTrack.Hearing hearing;
        double heardDb = -100, gain;
        float pitch = 1;
        FarSounds.Far last;
        long boomedAt = Long.MIN_VALUE / 2;
        Far(int key) { this.key = key; }
    }

    private static final Map<Integer, Far> FAR = new HashMap<>();
    private static final Set<ResourceLocation> BAKING = new HashSet<>();

    /** The far vehicles heard now (the debug overlay). */
    static List<Far> heard() { return new ArrayList<>(FAR.values()); }

    /** Each tick, after the near vehicles: keys of the vehicles tracked (they are not far), the listener and the air. */
    static void tick(ClientLevel level, Set<Integer> tracked, Vec3d listener, Vec3d listenerVelocity, double time, double c, boolean doppler,
                     double temperatureK, double humidity, Transmission.Insulation cabin) {
        if (!HdfvClientConfig.farSounds()) { stopAll(); return; }
        double volume = HdfvClientConfig.volume(VehicleSound.Group.ENGINES);
        Set<Integer> now = new HashSet<>();
        List<Far> heard = new ArrayList<>();
        for (FarSounds.Far f : FarSounds.heard()) {
            if (tracked.contains(f.key())) { continue; }
            Far e = FAR.computeIfAbsent(f.key(), Far::new);
            now.add(f.key());
            double age = Math.min(FarSounds.KEEP_TICKS, FarSounds.age(f.key())) * 0.05;
            Vec3d at = f.position().add(f.velocity().scale(age));
            e.last = f;
            e.track.record(time, at);
            e.hearing = e.track.hear(listener, listenerVelocity, time, c, doppler, doppler);
            e.heardDb = e.hearing.heard() ? Propagation.levelAt(f.signature().levelDb(), 0, Math.max(1, e.hearing.distanceM())) + 20 * Math.log10(Math.max(1e-3, cabin.gain())) : -100;
            if (e.hearing.boom() && HdfvClientConfig.booms() && level.getGameTime() - e.boomedAt > 40) {
                e.boomedAt = level.getGameTime();
                Vec3d b = e.hearing.apparent();
                Cues.boom(level, new Vec3(b.x(), b.y(), b.z()), 15, e.hearing.sourceVelocity().length() / c, e.hearing.distanceM(), c, volume);
            }
            heard.add(e);
        }
        FAR.entrySet().removeIf(m -> {
            if (now.contains(m.getKey())) { return false; }
            if (m.getValue().loop != null) { SoundDebug.log("far ({}) gone", m.getKey()); }
            stop(m.getValue());
            return true;
        });
        heard.sort((a, b) -> Double.compare(b.heardDb, a.heardDb));
        for (int i = 0; i < heard.size(); i++) {
            Far e = heard.get(i);
            if (i >= MAX || e.heardDb < QUIET_DB || volume <= 0) { stop(e); continue; }
            // a vehicle no longer in the server's latest reports (kept only for KEEP_TICKS) starts no loop: one report of
            // a car idling near LOUD_DB would otherwise play for a few ticks once its loop is made
            if (e.loop == null && FarSounds.age(e.key) > FarSounds.EVERY_TICKS) { continue; }
            play(e, doppler, volume, temperatureK, humidity, cabin);
        }
    }

    private static void play(Far e, boolean doppler, double volume, double temperatureK, double humidity, Transmission.Insulation cabin) {
        FarSounds.Signature s = e.last.signature();
        VehicleSound.Far[] kinds = VehicleSound.Far.values();
        VehicleSound.Far kind = kinds[Math.clamp(s.kind(), 0, kinds.length - 1)];
        double rev = s.rateHz();
        double bin = rev > 1 ? Math.pow(2, Math.round(Math.log(rev) / Math.log(2) * 3) / 3.0) : 0;
        ResourceLocation id = RuntimeSounds.id(Hdfv.MOD_ID, "far/" + kind.name().toLowerCase(java.util.Locale.ROOT) + "_r" + Math.round(bin * 100));
        if (!RuntimeSounds.defined(id)) {
            if (BAKING.size() < 2 && BAKING.add(id)) {
                double at = bin > 0 ? bin : rev;
                CompletableFuture.supplyAsync(() -> VehicleSound.loop(VehicleSound.far(kind, at, kind.ordinal() * 31L + 7), 1.5), Util.backgroundExecutor())
                        .whenCompleteAsync((samples, error) -> {
                            BAKING.remove(id);
                            if (samples != null) { RuntimeSounds.define(id, samples); }
                        }, Minecraft.getInstance());
            }
            return;
        }
        if (e.loop == null || !id.equals(e.id)) {
            if (e.loop != null) { e.loop.stop(); }
            e.loop = LiveSound.looped(id, SoundSource.NEUTRAL);
            e.id = id;
            SoundDebug.log("far {} ({}): {} dB at {} m", kind, e.key, Math.round(e.heardDb), Math.round(e.hearing.distanceM()));
        }
        Vec3d at = e.hearing.apparent();
        e.pitch = (float) ((bin > 0 ? Math.clamp(rev / bin, 0.5, 2) : 1) * (doppler ? e.hearing.doppler() : 1));
        e.gain = Math.min(1, Loudness.gain(e.heardDb) * volume);
        e.loop.update(at.x(), at.y(), at.z(), (float) e.gain, e.pitch, false);
        if (Efx.available() && e.loop.instance() != null) {
            float hf = Listening.airHf(e.hearing.distanceM(), temperatureK, humidity) * (float) cabin.gainHf();
            Efx.path(e.loop.instance(), new Efx.Path(1, hf, 0));   // the cabin's broadband share is in the level
        }
    }

    private static void stop(Far e) {
        if (e.loop != null) { e.loop.stop(); e.loop = null; e.id = null; }
        e.gain = 0;
    }

    static void stopAll() {
        for (Far e : FAR.values()) { stop(e); }
        FAR.clear();
    }
}
