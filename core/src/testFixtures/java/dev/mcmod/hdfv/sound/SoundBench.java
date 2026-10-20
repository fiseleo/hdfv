package dev.mcmod.hdfv.sound;

import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.acoustics.Surface;
import dev.mcmod.aerocore.api.acoustics.Voice;
import dev.mcmod.aerocore.api.vehicle.Vehicle;
import dev.mcmod.hdfv.part.engine.PistonEngineDesign;
import dev.mcmod.hdfv.part.wheel.AxleDesign;
import dev.mcmod.hdfv.part.wheel.LandingGearDesign;

import java.util.HashMap;
import java.util.Map;

/**
 * A vehicle's sound offline (0.19): set to an operating point (engine speed, load, throttle, road speed, tyre slip)
 * and heard from a distance, outside or inside its cabin, through the same VehicleSound the game plays. For tests and
 * the development export (/hdfvdev sound render), which writes it as a WAV file.
 */
public final class SoundBench {
    private SoundBench() {}

    /** An operating point: every engine at rpm and load, the road speed (m/s) and each tyre's slip speed (m/s). */
    public record Point(double rpm, double load, double throttle, boolean running, double boostRatio, double speedMps, double slipMps) {
        public static Point idle(double rpm) { return new Point(rpm, 0.25, 0, true, 1, 0, 0); }
    }

    /** The inputs a vehicle at a point gives its sound (every wheel turning with the road, on stone, dry). */
    public static SoundInputs inputs(Vehicle vehicle, Point p) {
        Map<Integer, Double> rotors = new HashMap<>();
        Map<String, Map<Integer, Double>> shown = new HashMap<>();
        for (Vehicle.Installed i : vehicle.installed()) {
            int n = i.part().rotors().size();
            Map<Integer, Double> s = new HashMap<>();
            if (i.part().design() instanceof PistonEngineDesign) {
                rotors.put(i.firstRotor(), p.rpm() * Math.PI / 30);
                s.put(PistonEngineDesign.SHOWN_LOAD, p.running() ? p.load() : 0);
                s.put(PistonEngineDesign.SHOWN_RUNNING, p.running() ? 1.0 : 0);
                s.put(PistonEngineDesign.SHOWN_BOOST, p.boostRatio());
            } else if (i.part().design() instanceof AxleDesign a) {
                double w = p.speedMps() / SoundProfiles.tyreRadius(a.tyre());
                rotors.put(i.firstRotor() + n - 2, w);
                rotors.put(i.firstRotor() + n - 1, w);
                int extra = dev.mcmod.hdfv.part.wheel.SteerSchedule.axleOf(i.part()).extraShown();
                s.put(extra, p.slipMps()); s.put(extra + 1, 4.0); s.put(extra + 2, p.slipMps()); s.put(extra + 3, 4.0);
            } else if (i.part().design() instanceof LandingGearDesign g) {
                rotors.put(i.firstRotor(), p.speedMps() / SoundProfiles.tyreRadius(g.tyre()));
                s.put(3, p.slipMps()); s.put(4, 4.0);
            }
            shown.put(i.id(), s);
        }
        Vec3d velocity = new Vec3d(p.speedMps(), 0, 0);
        return new SoundInputs() {
            @Override public double shown(String partId, int index) { return shown.getOrDefault(partId, Map.of()).getOrDefault(index, 0.0); }
            @Override public double rotorSpeed(int rotor) { return rotors.getOrDefault(rotor, 0.0); }
            @Override public double input(String partId, String role) { return role.equals("throttle") ? p.throttle() : role.equals("ignition") ? 1 : 0; }
            @Override public double control(String channel) { return 0; }
            @Override public Vec3d velocity() { return velocity; }
            @Override public Surface surface(String partId, int wheel) { return Surface.ROCK; }
            @Override public boolean wet() { return false; }
        };
    }

    /** The vehicle's sound at a point, heard at a distance (m) outside or inside, for seconds: samples, full scale 1. */
    public static float[] render(Vehicle vehicle, Point p, double distanceM, boolean inside, double seconds, long seed) {
        VehicleSound sound = VehicleSound.of(vehicle, seed);
        if (sound == null) { return new float[(int) (seconds * Voice.RATE)]; }
        SoundInputs in = inputs(vehicle, p);
        sound.update(in);
        double gain = sound.balance(distanceM, inside, VehicleSound.unity(), 293.15, 70);
        // let the models settle on the point (smoothing), then record
        Voice.render(sound.voice(), 0.5);
        float[] out = Voice.render(sound.voice(), seconds);
        for (int i = 0; i < out.length; i++) { out[i] *= (float) gain; }
        return out;
    }
}
