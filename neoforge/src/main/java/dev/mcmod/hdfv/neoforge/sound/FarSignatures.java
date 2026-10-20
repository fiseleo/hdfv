package dev.mcmod.hdfv.neoforge.sound;

import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.acoustics.Surface;
import dev.mcmod.aerocore.api.vehicle.Vehicle;
import dev.mcmod.aerocore.neoforge.vehicle.FarSounds;
import dev.mcmod.aerocore.neoforge.vehicle.PartVehicle;
import dev.mcmod.hdfv.sound.SoundInputs;
import dev.mcmod.hdfv.sound.VehicleSound;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * hdfv's vehicles as heard from afar (0.19, server): each vehicle's engines set from its state (the same sound
 * models the clients play, only their levels: {@link VehicleSound#updateEngines} renders nothing and makes no
 * events), the loudest one's level and kind, and its engine's speed as the rate. Registered with AeroCore's
 * {@link FarSounds} at start-up.
 */
public final class FarSignatures {
    private FarSignatures() {}

    private record Known(Vehicle compiled, VehicleSound sound, int rotor) {}
    private static final Map<PartVehicle, Known> KNOWN = new WeakHashMap<>();

    public static void register() { FarSounds.provide(FarSignatures::of); }

    /** A vehicle's far signature now (null: nothing in it sounds). */
    public static FarSounds.Signature of(PartVehicle v) {
        Vehicle compiled = v.vehicle();
        if (compiled == null) { return null; }
        Known k = KNOWN.get(v);
        if (k == null || k.compiled() != compiled) {
            k = new Known(compiled, VehicleSound.of(compiled, v.getUUID().getLeastSignificantBits() ^ v.getUUID().getMostSignificantBits()), VehicleSound.engineRotor(compiled));
            KNOWN.put(v, k);
        }
        if (k.sound() == null) { return null; }
        double db = k.sound().updateEngines(new Inputs(v));
        VehicleSound.Far kind = k.sound().farKind();
        if (kind == null) { return null; }
        double rev = k.rotor() >= 0 ? Math.abs(v.rotorSpeed(k.rotor())) / (2 * Math.PI) : 0;
        return new FarSounds.Signature(db, kind.ordinal(), rev);
    }

    /** A vehicle's state as the server has it. */
    private record Inputs(PartVehicle v) implements SoundInputs {
        @Override public double shown(String partId, int index) { return v.shown(partId, index, 1f); }
        @Override public double rotorSpeed(int rotor) { return v.rotorSpeed(rotor); }
        @Override public double input(String partId, String role) { return v.input(partId, role); }
        @Override public double control(String channel) { return v.controls().getOrDefault(channel, 0.0); }
        @Override public Vec3d velocity() { return v.velocity(); }
        @Override public Surface surface(String partId, int wheel) { return Surface.ROCK; }
        @Override public boolean wet() { return false; }
        @Override public boolean inWater() { return v.isInWater(); }
    }
}
