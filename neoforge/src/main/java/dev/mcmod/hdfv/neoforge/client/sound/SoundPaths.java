package dev.mcmod.hdfv.neoforge.client.sound;

import dev.mcmod.aerocore.api.acoustics.RoomAcoustics;
import dev.mcmod.aerocore.api.acoustics.Transmission;
import dev.mcmod.aerocore.neoforge.client.sound.Efx;
import dev.mcmod.aerocore.neoforge.client.sound.Listening;
import dev.mcmod.aerocore.neoforge.client.sound.SynthInstance;
import dev.mcmod.hdfv.neoforge.HdfvClientConfig;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;

/**
 * What lies between a sound and the listener (0.19), for AeroCore's EFX filters: the blocks in the way (counted again
 * every {@link #OCCLUSION_TICKS} ticks per sound), the air (for sounds not filtered in their own mix), the cabin of
 * a closed vehicle the listener rides in, and how much of the sound the listener's room returns (its reverb send:
 * a quarter of AeroCore's reverberant-to-direct ratio for a source of directivity 2, none outdoors or in a cabin).
 * With EFX missing or the reverb setting off, nothing is filtered.
 */
final class SoundPaths {
    private SoundPaths() {}

    static final int OCCLUSION_TICKS = 5;

    /** One sound's path, with its occlusion remembered between counts. */
    static final class Cached {
        private int blocks, age = Integer.MAX_VALUE / 2;
        Efx.Path last = Efx.Path.CLEAR;

        /**
         * The path now: from where the sound is (at distance m), with the air's high loss when the sound is not already
         * filtered for it, through the listener's cabin (NONE when not in one).
         */
        Efx.Path update(ClientLevel level, Vec3 ear, Vec3 at, double distanceM, boolean air, double temperatureK, double humidity,
                        Transmission.Insulation cabin) {
            if (++age >= OCCLUSION_TICKS) { age = 0; blocks = cabin == Transmission.Insulation.NONE ? Listening.between(level, ear, at) : 0; }
            float[] o = Listening.occlusion(blocks);
            float hf = o[1] * (air ? Listening.airHf(distanceM, temperatureK, humidity) : 1);
            float g = o[0] * (float) cabin.gain();
            hf *= (float) cabin.gainHf();
            RoomAcoustics.Room room = Listening.room();
            float send = cabin != Transmission.Insulation.NONE ? 0 : (float) Math.min(1, 0.25 * RoomAcoustics.wetOverDry(room, distanceM, 2)) * o[0];
            last = new Efx.Path(g, hf, send);
            return last;
        }

        /** Applies it to a playing sound. */
        void apply(SynthInstance sound) {
            if (sound != null && Efx.available()) { Efx.path(sound, last); }
        }

        int blocks() { return blocks; }
    }

    private static Boolean wanted;

    /** Follows hdfv's reverb setting (once per change). */
    static void settings() {
        boolean on = HdfvClientConfig.reverb();
        if (wanted == null || wanted != on) {
            wanted = on;
            Efx.want(on);
            Listening.scan(on);
        }
    }
}
