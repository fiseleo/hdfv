package dev.mcmod.hdfv.neoforge.client.sound;

import dev.mcmod.aerocore.api.acoustics.Dsp;
import dev.mcmod.aerocore.api.acoustics.Loudness;
import dev.mcmod.aerocore.api.acoustics.Noise;
import dev.mcmod.aerocore.api.acoustics.SonicBoom;
import dev.mcmod.aerocore.api.acoustics.Voice;
import dev.mcmod.aerocore.neoforge.client.sound.RuntimeSounds;
import dev.mcmod.aerocore.neoforge.client.sound.SoundHooks;
import dev.mcmod.aerocore.neoforge.client.sound.SynthInstance;
import dev.mcmod.hdfv.neoforge.Hdfv;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Sounds of a moment besides the mixes (0.19): sonic booms and subtitles.
 * <ul>
 * <li>A boom when a supersonic vehicle's Mach cone reaches the listener (AeroCore's SourceTrack): Carlson's N-wave for
 *     its length, Mach number and distance (shape factor 0.1, ground reflection 1.9), its two shocks rising over
 *     1.5 ms, then a rumble of 0.5 s (the ground's echoes and the turbulence the shock went through; a listening rule,
 *     at a quarter of the peak). Its gain is the loudness map of the peak level less 5 dB (the N-wave's RMS).</li>
 * <li>Subtitles for what the mixes and loops play, which Minecraft never sees start: a vehicle's engine, its horn,
 *     doors, impacts, a boom, and the workshop's sounds (keys {@code subtitles.hdfv.*}).</li>
 * </ul>
 */
final class Cues {
    private Cues() {}

    /** A sonic boom at a point (where the cone came from), of a body of a length at a Mach number, heard at a distance. */
    static void boom(ClientLevel level, Vec3 at, double lengthM, double mach, double distanceM, double soundMps, double volume) {
        SonicBoom.NWave wave = SonicBoom.nWave(Math.max(2, lengthM), mach, Math.max(distanceM, 1), 101_325, 101_325, soundMps, SonicBoom.SHAPE, SonicBoom.GROUND);
        if (!(wave.overpressurePa() > 0)) { return; }
        int ms = (int) Math.clamp(Math.round(wave.durationS() * 1000 / 5) * 5, 5, 600);
        ResourceLocation id = RuntimeSounds.id(Hdfv.MOD_ID, "boom/n" + ms);
        if (!RuntimeSounds.defined(id)) { RuntimeSounds.define(id, boomSamples(ms / 1000.0)); }
        double peakDb = Dsp.splDb(wave.overpressurePa());
        SynthInstance s = SynthInstance.buffered(id, SoundSource.NEUTRAL, false);
        s.at(at.x, at.y, at.z);
        s.volume((float) Math.min(1, Loudness.gain(peakDb - 5) * volume));
        Minecraft.getInstance().getSoundManager().play(s);
        subtitle("boom", at, 2000);
        SoundDebug.log("boom: {} Pa, {} ms at {} m (Mach {})", Math.round(wave.overpressurePa() * 10) / 10.0, ms, Math.round(distanceM), Math.round(mach * 100) / 100.0);
    }

    /** An N-wave of a duration with its rumble, full scale at the peak. */
    static float[] boomSamples(double durationS) {
        float[] n = SonicBoom.samples(new SonicBoom.NWave(1, durationS), 0.0015, 1);
        int tail = (int) (0.5 * Voice.RATE);
        float[] out = new float[n.length + tail];
        System.arraycopy(n, 0, out, 0, n.length);
        Noise noise = new Noise(Double.doubleToLongBits(durationS));
        double low = 0;
        for (int i = 0; i < out.length; i++) {
            low += (noise.white() - low) * 0.01;
            double env = i < n.length ? (double) i / n.length : Math.exp(-(double) (i - n.length) / (0.15 * Voice.RATE));
            out[i] += (float) (0.25 * 6 * low * env);
        }
        return out;
    }

    /** A subtitle for an hdfv sound at a point (when subtitles are on), heard within a range (blocks). */
    static void subtitle(String key, Vec3 at, double range) {
        SoundHooks.subtitle("subtitles.hdfv." + key, at.x, at.y, at.z, (float) range);
    }

    /** The subtitle of a machine's process. */
    static String machine(dev.mcmod.aerocore.api.acoustics.ProcessNoise.Process p) {
        return switch (p) {
            case TURNING, MILLING, DRILLING, BORING, GRINDING, FINISHING, GEAR_CUTTING, STROKE, SAWING, THREADING -> "machine";
            case SPARK_EROSION, ELECTROCHEMICAL, LASER, PLASMA, WATERJET -> "cutting";
            case RIVET_SQUEEZE, RIVET_IMPACT, RIVET_DRILL -> "rivet";
            case WELD_MIG, WELD_TIG -> "weld";
            case BURNER -> "burner";
            case PRESS -> "press";
            case STEAM -> "steam";
            case PUMP -> "pump";
            case COMPRESSOR -> "compressor";
            case FAN -> "fan";
            case SPRAY -> "spray";
            case CRANE -> "hoist";
            case HUM, BELT, CURING -> "motor";
        };
    }

    /** The subtitle of a workshop event. */
    static String event(dev.mcmod.hdfv.sound.MachineSound.Event e) {
        return switch (e) {
            case DOOR -> "door_motor";
            case WELD -> "weld";
            case RIVET -> "rivet";
            case HOIST -> "hoist";
            case GUSHER -> "gusher";
            case DONE -> "buzzer";
        };
    }
}
