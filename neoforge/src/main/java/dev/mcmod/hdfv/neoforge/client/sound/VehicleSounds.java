package dev.mcmod.hdfv.neoforge.client.sound;

import com.mojang.logging.LogUtils;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.acoustics.Propagation;
import dev.mcmod.aerocore.api.acoustics.SourceTrack;
import dev.mcmod.aerocore.api.vehicle.Vehicle;
import dev.mcmod.aerocore.neoforge.client.sound.LiveSound;
import dev.mcmod.aerocore.neoforge.client.sound.SoundHooks;
import dev.mcmod.aerocore.neoforge.vehicle.PartVehicle;
import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.neoforge.HdfvClientConfig;
import dev.mcmod.hdfv.neoforge.power.EngineStandBlockEntity;
import dev.mcmod.hdfv.sound.VehicleSound;
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
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Vehicles' sound on a client (0.19). Every part vehicle within {@link #RANGE} has a {@link VehicleSound} (its parts'
 * models mixed) and a {@link SourceTrack} (where it has been, for the sound's travel time and Doppler shift). Each tick
 * its sound reads its synced state, and the listener's hearing of it is worked out (the newest sound to arrive: from
 * where, how late, how shifted). The loudest few at the listener (hdfv's "streams" setting, never leaving Minecraft
 * fewer than two free streaming channels) play their mix live through AeroCore's {@link LiveSound}: positioned where
 * they are heard from, at the pitch of the Doppler factor, as loud as AeroCore's loudness map makes their level there.
 * A rider in a closed cabin hears the inside mix (the walls' insulation) head-locked. Streams change hands with some
 * reluctance: one playing keeps its channel for at least {@link #HOLD_TICKS} and until a waiting vehicle is
 * {@link #SWAP_DB} louder (else two vehicles near the cut swap every tick, each start a new stream). They are ranked
 * by a level that rises with the heard one at once, holds its peak {@link #RANK_HOLD_TICKS} and then falls
 * {@link #RANK_FALL_DB} a tick: an automatic idling in gear swings some 15 dB over several seconds as its load comes
 * and goes, and ranked by their levels of the moment two such cars in the QA world traded the stream every 2-6 s.
 * <p>
 * An engine running on a test stand competes for the streams too: its sound is the engine alone in the open
 * ({@link VehicleSound#engine}) from the stand's synced speed (eased: the server sends it to 50 rpm) and load.
 * <p>
 * The next {@link #MAX_LOOPS} loudest play a loop instead: a copy of their sound set to their state now, but with the
 * engine at a speed on a grid of three a octave, rendered on a background thread (1.5 s, its end crossfaded into its
 * start) and looped on an ordinary channel; its pitch makes up the speed between grid points and the Doppler factor,
 * its gain is the loudness map of its level. A loop is made again when the speed leaves its third of an octave or
 * the level its 6 dB; the old one fades out over a quarter of a second as the new one comes in.
 */
@EventBusSubscriber(modid = Hdfv.MOD_ID, value = Dist.CLIENT)
public final class VehicleSounds {
    private VehicleSounds() {}

    private static final Logger LOGGER = LogUtils.getLogger();
    /** How far vehicles are followed (blocks); fainter than {@link #QUIET_DB} at the listener they get no stream. */
    static final double RANGE = 320, QUIET_DB = 18, SWAP_DB = 6, RANK_FALL_DB = 0.25;
    static final int RANK_HOLD_TICKS = 160;
    static final int HOLD_TICKS = 40, MAX_LOOPS = 12, LOOP_FADE_TICKS = 5;
    static final double LOOP_SECONDS = 1.5;
    private static final ResourceLocation STREAM = ResourceLocation.fromNamespaceAndPath(Hdfv.MOD_ID, "vehicle_stream");

    /** One vehicle's (or engine stand's) sound and how it is heard now. */
    static final class Entry {
        final PartVehicle vehicle;
        final EngineStandBlockEntity stand;
        final StandInputs standInputs;
        Object built;
        Vehicle compiled;
        VehicleSound sound;
        final SourceTrack track = new SourceTrack();
        LiveSound live;
        SourceTrack.Hearing hearing;
        double heardDb = -100, gain, distance;
        /** What it is ranked by for a stream or a loop: the heard level's peak, held RANK_HOLD_TICKS (counted in rankHeld), then falling. */
        double rankDb = -100;
        int rankHeld;
        boolean inside, playing, ridden;
        int playedTicks;
        /** 0.19 loops: the one playing and its id, the engine's rotor (-1: none; -2: not looked up), its pitch. */
        LiveSound loop;
        ResourceLocation loopId;
        /** What lies between it and the listener (EFX). */
        final SoundPaths.Cached path = new SoundPaths.Cached();
        /** Subtitles: the horn and doors last tick; when it last boomed (game time). */
        boolean wasHorn, wasOpen;
        long boomedAt = Long.MIN_VALUE / 2;
        int engineRotor = -2;
        float loopPitch = 1;
        /** The playing loop's engine speed (rev/s, 0: none) and level (dB at 1 m) it was made at. */
        double loopBin, loopLevel;
        Entry(PartVehicle vehicle) { this.vehicle = vehicle; this.stand = null; this.standInputs = null; }
        Entry(EngineStandBlockEntity stand) { this.vehicle = null; this.stand = stand; this.standInputs = new StandInputs(); }
        String name() {
            if (stand != null) { return "engine stand " + stand.getBlockPos().toShortString(); }
            return vehicle.blueprint() == null ? "vehicle" : vehicle.blueprint().name();
        }
        String id() { return stand != null ? stand.getBlockPos().toShortString() : String.valueOf(vehicle.getId()); }

        /** The point last given to the track and when (a fast vehicle's is eased). */
        private Vec3d fed;
        private double fedTime;

        /**
         * Where the track has the vehicle now. A fast one (AeroCore's FAST_MPS) is carried on by its synced velocity and
         * eased a share {@link #FAST_EASE} a tick toward where the client draws it: a client a few ticks ahead of a
         * lagging server runs it on, then puts it back tens of metres, and a put-back toward the listener closes faster
         * than sound (a boom of its own, seen in the 0.19 multiplayer check).
         */
        Vec3d trackPoint(PartVehicle v, double time) {
            Vec3d at = new Vec3d(v.getX(), v.getY(), v.getZ()), velocity = v.velocity();
            if (fed != null && time > fedTime && velocity.length() >= PartVehicle.FAST_MPS) {
                Vec3d carried = fed.add(velocity.scale(time - fedTime));
                at = carried.add(at.subtract(carried).scale(FAST_EASE));
            }
            fed = at;
            fedTime = time;
            return at;
        }
    }

    /** The share of the difference a fast vehicle's track point closes each tick toward where it is drawn. */
    static final double FAST_EASE = 0.3;

    /** Vehicles by entity id, stands by position. */
    private static final Map<Object, Entry> ENTRIES = new HashMap<>();
    private static final ClientInputs INPUTS = new ClientInputs();
    /** Loops fading out (each with the ticks it has left and its gain), loops being made, when each loop was last wanted. */
    private static final List<Object[]> FADING = new ArrayList<>();
    private static final java.util.Set<ResourceLocation> BAKING = new java.util.HashSet<>();
    private static final Map<ResourceLocation, Long> LOOP_USED = new HashMap<>();
    private static long ticks;
    /** The cabin of the closed vehicle the listener rides in (NONE: not in one), for every other hdfv sound. */
    private static dev.mcmod.aerocore.api.acoustics.Transmission.Insulation cabin = dev.mcmod.aerocore.api.acoustics.Transmission.Insulation.NONE;

    /** The insulation of the closed cabin the listener sits in now (NONE when outside or in an open one). */
    static dev.mcmod.aerocore.api.acoustics.Transmission.Insulation listenerCabin() { return cabin; }
    static {
        // the server tells of impacts; each goes into its vehicle's mix
        dev.mcmod.aerocore.neoforge.vehicle.VehicleContacts.listen((vehicle, impact) -> {
            Entry e = ENTRIES.get(vehicle.getId());
            if (e == null || e.sound == null) { return; }
            var m = dev.mcmod.aerocore.neoforge.vehicle.VehicleContacts.material(impact.material());
            e.sound.impact(impact.impulseNs(), impact.approachMps(), impact.body() ? dev.mcmod.aerocore.api.acoustics.Surface.STEEL
                    : m == null ? dev.mcmod.aerocore.api.acoustics.Surface.ROCK : dev.mcmod.aerocore.api.acoustics.Surface.of(m));
            Cues.subtitle("impact", vehicle.position(), 32);
            SoundDebug.log("impact {} ({}): {} N s at {} m/s", e.name(), e.id(), Math.round(impact.impulseNs()), String.format(java.util.Locale.ROOT, "%.1f", impact.approachMps()));
        });
    }

    /** The vehicles followed now (for the debug overlay), loudest first. */
    static List<Entry> entries() {
        List<Entry> list = new ArrayList<>(ENTRIES.values());
        list.sort(Comparator.comparingDouble((Entry e) -> -e.rankDb));
        return list;
    }

    @SubscribeEvent static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) { stopAll(); return; }
        if (mc.isPaused()) { return; }
        SoundPaths.settings();
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        Vec3d listener = new Vec3d(camera.x, camera.y, camera.z);
        Vec3d listenerVelocity = mc.player.getVehicle() instanceof PartVehicle ridden ? ridden.velocity()
                : new Vec3d(mc.player.getDeltaMovement().x * 20, mc.player.getDeltaMovement().y * 20, mc.player.getDeltaMovement().z * 20);
        double time = level.getGameTime() * 0.05;
        BlockPos here = BlockPos.containing(camera);
        double temperatureK = 273.15 + Math.clamp(-10 + 30 * level.getBiome(here).value().getBaseTemperature(), -30, 45);
        double humidity = level.isRainingAt(here) ? 100 : 70;
        double c = Propagation.speedOfSound(temperatureK);
        boolean doppler = HdfvClientConfig.doppler();

        java.util.Set<Object> seen = new java.util.HashSet<>();
        List<PartVehicle> near = new ArrayList<>(level.getEntitiesOfClass(PartVehicle.class, mc.player.getBoundingBox().inflate(RANGE)));
        if (mc.player.getVehicle() instanceof PartVehicle ridden && !near.contains(ridden)) { near.add(ridden); }
        for (PartVehicle v : near) {
            Vehicle compiled = v.vehicle();
            if (compiled == null) { continue; }
            Entry e = ENTRIES.computeIfAbsent(v.getId(), k -> new Entry(v));
            seen.add(v.getId());
            if (e.compiled != compiled) {
                e.compiled = compiled;
                e.sound = VehicleSound.of(compiled, v.getUUID().getLeastSignificantBits() ^ v.getUUID().getMostSignificantBits());
            }
            if (e.sound == null) { e.heardDb = -100; continue; }
            e.sound.update(INPUTS.at(v, level));
            e.track.record(time, e.trackPoint(v, time));
            e.ridden = mc.player.getVehicle() == v;
            e.inside = e.ridden && !e.sound.openBody() && v.controls().getOrDefault("doors", 0.0) < 0.5;
            e.hearing = e.track.hear(listener, listenerVelocity, time, c, doppler, doppler);
            e.distance = e.inside ? 1.5 : e.hearing.heard() ? e.hearing.distanceM() : listener.subtract(e.track.latest()).length();
            e.heardDb = e.inside || e.hearing.heard() ? e.sound.levelAt(e.distance, e.inside) : -100;
            cues(e, v, level, c);
            if (SoundDebug.log && v.velocity().length() > c) {
                Vec3d p = e.track.latest();
                SoundDebug.log("supersonic {} ({}): t {} at {} {} {}, {} m/s; {} arrivals{}", e.name(), e.id(), String.format(java.util.Locale.ROOT, "%.2f", time),
                        Math.round(p.x()), Math.round(p.y()), Math.round(p.z()), Math.round(v.velocity().length()), e.hearing.arrivals(), e.hearing.boom() ? ", boom" : "");
            }
        }
        // engines running on stands, as the server says
        for (var it = dev.mcmod.hdfv.neoforge.sound.SoundSync.heard().long2ObjectEntrySet().iterator(); it.hasNext(); ) {
            var h = it.next();
            if (h.getValue().process() != null) { continue; }
            BlockPos pos = BlockPos.of(h.getLongKey());
            if (!(level.getBlockEntity(pos) instanceof EngineStandBlockEntity stand) || listener.subtract(new Vec3d(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5)).length() > RANGE) { continue; }
            var design = stand.design();
            if (design == null) { continue; }
            Entry e = ENTRIES.computeIfAbsent(pos, k -> new Entry(stand));
            if (e.stand != stand) { continue; }
            seen.add(pos);
            if (e.built != design) { e.built = design; e.sound = VehicleSound.engine(design, pos.asLong()); }
            e.sound.update(e.standInputs.at(stand, h.getValue()));
            e.track.record(time, new Vec3d(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5));
            e.ridden = false;
            e.inside = false;
            e.hearing = e.track.hear(listener, listenerVelocity, time, c, doppler, doppler);
            e.distance = e.hearing.heard() ? e.hearing.distanceM() : listener.subtract(e.track.latest()).length();
            e.heardDb = e.hearing.heard() ? e.sound.levelAt(e.distance, false) : -100;
        }
        for (Iterator<Map.Entry<Object, Entry>> it = ENTRIES.entrySet().iterator(); it.hasNext(); ) {
            var m = it.next();
            Entry e = m.getValue();
            boolean gone = e.vehicle != null ? e.vehicle.isRemoved() : e.stand.isRemoved();
            if (!seen.contains(m.getKey()) || gone) { stop(e, "gone"); stopLoop(e); it.remove(); }
        }

        cabin = dev.mcmod.aerocore.api.acoustics.Transmission.Insulation.NONE;
        for (Entry e : ENTRIES.values()) {
            if (e.inside && e.sound != null) { cabin = e.sound.cabin(); }
            if (e.heardDb >= e.rankDb) { e.rankDb = e.heardDb; e.rankHeld = 0; }
            else if (++e.rankHeld > RANK_HOLD_TICKS) { e.rankDb = Math.max(e.heardDb, e.rankDb - RANK_FALL_DB); }
        }

        // the loudest few get a live stream (the one ridden first), changing hands reluctantly
        int allowed = HdfvClientConfig.streams();
        Map<VehicleSound.Group, Double> volumes = new EnumMap<>(VehicleSound.Group.class);
        for (VehicleSound.Group g : VehicleSound.Group.values()) { volumes.put(g, HdfvClientConfig.volume(g)); }
        List<Entry> chosen = choose(entries(), allowed);
        for (Entry e : ENTRIES.values()) {
            if (!chosen.contains(e)) { stop(e, e.rankDb > QUIET_DB ? "outranked" : "quiet"); continue; }
            e.playedTicks++;
            e.gain = e.sound.balance(e.distance, e.inside, volumes, temperatureK, humidity);
            SoundDebug.controls(e);
            if (e.live == null) {
                e.live = LiveSound.streamed(STREAM, SoundSource.NEUTRAL, e.sound.voice());
                SoundDebug.log("start {} ({}) at {} dB, {} m", e.name(), e.id(), Math.round(e.heardDb), Math.round(e.distance));
                Vec3d s = e.track.latest();
                Cues.subtitle(e.stand != null ? "engine_stand" : "engine", new Vec3(s.x(), s.y(), s.z()), 48);
            }
            Vec3d at = e.inside ? Vec3d.ZERO : e.hearing.apparent();
            float pitch = (float) (e.inside || !doppler ? 1 : e.hearing.doppler());
            e.live.update(at.x(), at.y(), at.z(), (float) Math.min(1, e.gain), pitch, e.inside);
            e.playing = e.live.playing();
            // the mix filters for the air itself; EFX adds the walls, the listener's cabin (another vehicle's) and the room
            if (e.inside) { e.path.last = dev.mcmod.aerocore.neoforge.client.sound.Efx.Path.CLEAR; }
            else { e.path.update(level, camera, new Vec3(at.x(), at.y(), at.z()), e.distance, false, temperatureK, humidity, cabin); }
            e.path.apply(e.live.instance());
            stopLoop(e);
        }

        // the rest that are heard: loops
        ticks++;
        int looping = 0;
        double engines = HdfvClientConfig.volume(VehicleSound.Group.ENGINES);
        for (Entry e : entries()) {
            if (chosen.contains(e)) { continue; }
            if (e.sound == null || e.rankDb < QUIET_DB || looping >= MAX_LOOPS || engines <= 0 || e.hearing == null) { stopLoop(e); continue; }
            looping++;
            loop(e, level, doppler, engines, temperatureK, humidity);
            if (e.loop != null) {
                Vec3d at = e.hearing.apparent();
                e.path.update(level, camera, new Vec3(at.x(), at.y(), at.z()), e.distance, true, temperatureK, humidity, cabin);
                e.path.apply(e.loop.instance());
            }
        }
        for (java.util.Iterator<Object[]> it = FADING.iterator(); it.hasNext(); ) {
            Object[] f = it.next();
            LiveSound s = (LiveSound) f[0];
            int left = (int) f[1] - 1;
            if (left <= 0) { s.stop(); it.remove(); continue; }
            f[1] = left;
            float g = (float) f[2] * left / LOOP_FADE_TICKS;
            double[] at = (double[]) f[3];
            s.update(at[0], at[1], at[2], g, (float) f[4], false);
        }
        // and those beyond the tracking range that the server tells of
        java.util.Set<Integer> tracked = new java.util.HashSet<>();
        for (PartVehicle v : near) { tracked.add(v.getUUID().hashCode()); }
        FarVehicles.tick(level, tracked, listener, listenerVelocity, time, c, doppler, temperatureK, humidity, cabin);
        if (ticks % 200 == 0) {
            for (var it = LOOP_USED.entrySet().iterator(); it.hasNext(); ) {
                var u = it.next();
                if (ticks - u.getValue() > 1200) { dev.mcmod.aerocore.neoforge.client.sound.RuntimeSounds.undefine(u.getKey()); it.remove(); }
            }
        }
    }

    /** The engine's speed for a vehicle's loop, rev/s (0 when it has no turning engine). */
    private static double engineRev(Entry e) {
        if (e.stand != null) { return e.standInputs.rpm / 60; }
        if (e.engineRotor == -2) { e.engineRotor = VehicleSound.engineRotor(e.compiled); }
        return e.engineRotor >= 0 ? Math.abs(e.vehicle.rotorSpeed(e.engineRotor)) / (2 * Math.PI) : 0;
    }

    /** Plays (making it first if need be) the loop for an entry's sound now. */
    private static void loop(Entry e, ClientLevel level, boolean doppler, double volume, double temperatureK, double humidity) {
        double rev = engineRev(e), levelDb = e.sound.levelAt(1, false);
        Vec3d at = e.hearing.apparent();
        float gain = (float) Math.min(1, dev.mcmod.aerocore.api.acoustics.Loudness.gain(e.heardDb) * volume);
        // the loop playing serves while it can be pitched to the speed (half an octave either way) and is within 9 dB of the level
        boolean serves = e.loop != null && (e.loopBin > 0 ? rev > 1 && Math.abs(Math.log(rev / e.loopBin) / Math.log(2)) <= 0.5 : rev <= 1)
                && Math.abs(levelDb - e.loopLevel) <= 9;
        if (!serves) {
            double bin = rev > 1 ? Math.pow(2, Math.round(Math.log(rev) / Math.log(2) * 3) / 3.0) : 0;
            int levelBin = (int) Math.round(levelDb / 6);
            String design = e.stand != null ? "stand" + Integer.toHexString(e.built.hashCode()) : Integer.toHexString(e.vehicle.blueprint() == null ? 0 : e.vehicle.blueprint().hashCode());
            ResourceLocation id = dev.mcmod.aerocore.neoforge.client.sound.RuntimeSounds.id(Hdfv.MOD_ID,
                    "vehicle/" + design + "_r" + Math.round(bin * 100) + "_l" + levelBin + "_s" + Math.floorMod(e.id().hashCode(), 3));
            LOOP_USED.put(id, ticks);
            if (!dev.mcmod.aerocore.neoforge.client.sound.RuntimeSounds.defined(id)) {
                bakeLoop(e, id, bin > 0 && rev > 0 ? bin / rev : 1, level, temperatureK, humidity);
                if (e.loop == null) { return; }   // (the old one, if any, plays on meanwhile)
            } else {
                if (e.loop != null) { FADING.add(new Object[]{e.loop, LOOP_FADE_TICKS, gain, new double[]{at.x(), at.y(), at.z()}, e.loopPitch}); }
                e.loop = LiveSound.looped(id, SoundSource.NEUTRAL);
                e.loopId = id;
                e.loopBin = bin;
                e.loopLevel = levelBin * 6.0;
                SoundDebug.log("loop {} ({}): {}", e.name(), e.id(), id.getPath());
            }
        } else if (e.loopId != null) {
            LOOP_USED.put(e.loopId, ticks);
        }
        float pitch = (float) ((e.loopBin > 0 && rev > 0 ? Math.clamp(rev / e.loopBin, 0.5, 2) : 1) * (doppler ? e.hearing.doppler() : 1));
        e.loopPitch = pitch;
        e.gain = gain;
        e.loop.update(at.x(), at.y(), at.z(), gain, pitch, false);
    }

    /** Makes a loop from a copy of an entry's sound set to its state now, its engine at the bin's speed (scale x its speed). */
    private static void bakeLoop(Entry e, ResourceLocation id, double scale, ClientLevel level, double temperatureK, double humidity) {
        if (BAKING.size() >= 2 || !BAKING.add(id)) { return; }
        VehicleSound copy = e.stand != null ? VehicleSound.engine((dev.mcmod.hdfv.part.engine.PistonEngineDesign) e.built, e.stand.getBlockPos().asLong())
                : VehicleSound.of(e.compiled, e.vehicle.getUUID().getLeastSignificantBits() ^ e.vehicle.getUUID().getMostSignificantBits());
        if (copy == null) { BAKING.remove(id); return; }
        dev.mcmod.hdfv.sound.SoundInputs now = e.stand != null ? e.standInputs : INPUTS.at(e.vehicle, level);
        copy.update(new Scaled(now, scale));
        copy.balance(7.5, false, VehicleSound.unity(), temperatureK, humidity);
        java.util.concurrent.CompletableFuture.supplyAsync(() -> copy.bakeLoop(LOOP_SECONDS), net.minecraft.Util.backgroundExecutor())
                .whenCompleteAsync((samples, error) -> {
                    BAKING.remove(id);
                    if (samples != null) { dev.mcmod.aerocore.neoforge.client.sound.RuntimeSounds.define(id, samples); }
                    else if (error != null) { LOGGER.warn("hdfv: could not make the loop {}", id, error); }
                }, Minecraft.getInstance());
    }

    private static void stopLoop(Entry e) {
        if (e.loop != null) {
            var inst = e.loop.instance();
            double[] at = inst == null ? new double[]{0, 0, 0} : new double[]{inst.getX(), inst.getY(), inst.getZ()};
            FADING.add(new Object[]{e.loop, LOOP_FADE_TICKS, (float) Math.min(1, e.gain), at, e.loopPitch});
            e.loop = null;
            e.loopId = null;
        }
    }

    /** A vehicle's inputs with its rotors at a scaled speed (a loop made at its grid speed). */
    private record Scaled(dev.mcmod.hdfv.sound.SoundInputs in, double scale) implements dev.mcmod.hdfv.sound.SoundInputs {
        @Override public double shown(String partId, int index) { return in.shown(partId, index); }
        @Override public double rotorSpeed(int rotor) { return in.rotorSpeed(rotor) * scale; }
        @Override public double input(String partId, String role) { return in.input(partId, role); }
        @Override public double control(String channel) { return in.control(channel); }
        @Override public Vec3d velocity() { return in.velocity(); }
        @Override public dev.mcmod.aerocore.api.acoustics.Surface surface(String partId, int wheel) { return in.surface(partId, wheel); }
        @Override public boolean wet() { return in.wet(); }
        @Override public boolean inWater() { return in.inWater(); }
        @Override public boolean raining() { return in.raining(); }
        @Override public double time() { return in.time(); }
        @Override public int blinkSeed() { return in.blinkSeed(); }
        @Override public Scrape scrape() { return null; }
    }

    /** A vehicle's moments: its boom reaching the listener; subtitles for its horn and doors. */
    private static void cues(Entry e, PartVehicle v, ClientLevel level, double c) {
        Vec3d at = e.hearing.heard() ? e.hearing.apparent() : e.track.latest();
        Vec3 here = new Vec3(at.x(), at.y(), at.z());
        if (e.hearing.boom() && !e.ridden && HdfvClientConfig.booms() && level.getGameTime() - e.boomedAt > 40) {
            e.boomedAt = level.getGameTime();
            var box = v.getBoundingBox();
            double length = Math.max(box.getXsize(), Math.max(box.getYsize(), box.getZsize()));
            Cues.boom(level, here, length, e.hearing.sourceVelocity().length() / c, e.hearing.distanceM(), c, HdfvClientConfig.volume(VehicleSound.Group.ENGINES));
        }
        boolean horn = v.controls().getOrDefault("horn", 0.0) > 0.5, open = v.controls().getOrDefault("doors", 0.0) > 0.5;
        if (horn && !e.wasHorn && e.heardDb > QUIET_DB) { Cues.subtitle("horn", here, 64); }
        if (open != e.wasOpen && e.heardDb > QUIET_DB - 10) { Cues.subtitle(open ? "door_open" : "door_close", here, 16); }
        e.wasHorn = horn;
        e.wasOpen = open;
    }

    /** Which entries stream (by rankDb): those playing stay unless quiet or outdone by SWAP_DB after HOLD_TICKS; the ridden one always. */
    static List<Entry> choose(List<Entry> loudestFirst, int allowed) {
        List<Entry> playing = new ArrayList<>(), waiting = new ArrayList<>();
        for (Entry e : loudestFirst) {
            if (e.sound == null) { continue; }
            if (e.ridden && e.heardDb > -90) { playing.add(0, e); continue; }
            if (e.live != null && e.rankDb > QUIET_DB - 3) { playing.add(e); }
            else if (e.rankDb > QUIET_DB) { waiting.add(e); }
        }
        while (playing.size() > allowed) { playing.removeLast(); }
        for (Entry w : waiting) {
            if (playing.size() < allowed) { playing.add(w); continue; }
            Entry quietest = null;
            for (Entry p : playing) { if (!p.ridden && p.playedTicks >= HOLD_TICKS && (quietest == null || p.rankDb < quietest.rankDb)) { quietest = p; } }
            if (quietest != null && w.rankDb > quietest.rankDb + SWAP_DB) { playing.remove(quietest); playing.add(w); }
        }
        return playing;
    }

    private static void stop(Entry e, String why) {
        e.playedTicks = 0;
        if (e.live != null) {
            e.live.stop();
            e.live = null;
            SoundDebug.log("stop {} ({}): {} at {} dB (ranked {})", e.name(), e.id(), why, Math.round(e.heardDb), Math.round(e.rankDb));
        }
        e.playing = false;
    }

    private static void stopAll() {
        for (Entry e : ENTRIES.values()) { stop(e, "left"); stopLoop(e); }
        ENTRIES.clear();
        for (Object[] f : FADING) { ((LiveSound) f[0]).stop(); }
        FADING.clear();
        FarVehicles.stopAll();
    }

    /** Free streaming channels as AeroCore counts them (for the overlay). */
    static int freeStreams() { return SoundHooks.freeStreams(); }

    static Logger logger() { return LOGGER; }

    /** An engine stand's inputs: the synced speed eased toward (it comes to 50 rpm), its load, running or not. */
    static final class StandInputs implements dev.mcmod.hdfv.sound.SoundInputs {
        double rpm, load;
        private boolean running;

        StandInputs at(EngineStandBlockEntity stand, dev.mcmod.hdfv.sound.MachineSound.Hum hum) {
            running = hum.running() && stand.shownRunning();
            double target = running ? stand.shownRpm() : 0;
            rpm += (target - rpm) * (rpm == 0 && running ? 1 : 0.18);
            load = stand.shownLoad();
            return this;
        }

        @Override public double shown(String partId, int index) {
            if (index == dev.mcmod.hdfv.part.engine.PistonEngineDesign.SHOWN_RUNNING) { return running ? 1 : 0; }
            if (index == dev.mcmod.hdfv.part.engine.PistonEngineDesign.SHOWN_LOAD) { return load; }
            return index == dev.mcmod.hdfv.part.engine.PistonEngineDesign.SHOWN_BOOST ? 1 : 0;
        }
        @Override public double rotorSpeed(int rotor) { return rpm * Math.PI / 30; }
        @Override public double input(String partId, String role) { return "throttle".equals(role) ? Math.clamp(0.1 + load, 0, 1) : running ? 1 : 0; }
        @Override public double control(String channel) { return 0; }
        @Override public Vec3d velocity() { return Vec3d.ZERO; }
        @Override public dev.mcmod.aerocore.api.acoustics.Surface surface(String partId, int wheel) { return dev.mcmod.aerocore.api.acoustics.Surface.ROCK; }
        @Override public boolean wet() { return false; }
    }
}
