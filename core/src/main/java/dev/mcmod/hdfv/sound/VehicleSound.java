package dev.mcmod.hdfv.sound;

import dev.mcmod.aerocore.api.acoustics.Dsp;
import dev.mcmod.aerocore.api.acoustics.Filters;
import dev.mcmod.aerocore.api.acoustics.GasJetNoise;
import dev.mcmod.aerocore.api.acoustics.Horn;
import dev.mcmod.aerocore.api.acoustics.ImpactNoise;
import dev.mcmod.aerocore.api.acoustics.Loudness;
import dev.mcmod.aerocore.api.acoustics.Mix;
import dev.mcmod.aerocore.api.acoustics.MotorNoise;
import dev.mcmod.aerocore.api.acoustics.Noise;
import dev.mcmod.aerocore.api.acoustics.Propagation;
import dev.mcmod.aerocore.api.acoustics.Surface;
import dev.mcmod.aerocore.api.acoustics.Transmission;
import dev.mcmod.aerocore.api.acoustics.TyreNoise;
import dev.mcmod.aerocore.api.acoustics.Voice;
import dev.mcmod.aerocore.api.vehicle.Vehicle;
import dev.mcmod.hdfv.look.LampRules;
import dev.mcmod.hdfv.part.body.BodyDesign;
import dev.mcmod.hdfv.part.body.BodySettingsDesign;
import dev.mcmod.hdfv.part.chassis.ChassisDesign;
import dev.mcmod.hdfv.part.chassis.ChassisStationDesign;
import dev.mcmod.hdfv.part.truck.FifthWheelDesign;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A vehicle's sound (0.19): a source for each part that makes one (PartSounds: engines, tyres, wind, water, gears,
 * doors...) and for what the whole vehicle does (its horn, its indicators' relay inside, its handbrake or air brakes,
 * a trailer coupling, wipers, impacts and scraping), all mixed into one voice, the vehicle's stream. Each tick
 * {@link #update} reads the vehicle's state into the sources' models and their sound power levels, and {@link #balance}
 * sets how loud each is at a listener: the total level there (spreading; the cabin's insulation for a listener inside)
 * goes through AeroCore's loudness map to one playback gain, and the sources share it by their levels, so a quiet tyre
 * stays quiet beside a loud engine. Air absorption and a closed cabin's walls are one low-pass on the outside sounds;
 * sounds only heard inside (the relay) skip it.
 * <p>
 * Rules: a vehicle with a fifth wheel or a trailer's kingpin, or over 7.5 t, has air brakes and an air horn; a boat
 * a whistle for its length (COLREGs); an aircraft no horn; a car a pair of electric horns. Built on the game thread
 * from a compiled vehicle; rendered on the synthesis thread (the models keep their parameters in volatile snapshots).
 */
public final class VehicleSound {
    /** Volume groups (hdfv's own settings). */
    public enum Group { ENGINES, DRIVING, CONTROLS, INTERIOR }

    /** One source: its part, group, the model behind it and its level now (dB sound power). */
    public static final class Source {
        private final String partId;
        private final Group group;
        private final Voice voice;
        private final boolean inside;
        private final Updater updater;
        private Mix.Input input;
        private double levelDb = -100;
        private String detail = "";

        Source(String partId, Group group, Voice voice, boolean inside, Updater updater) {
            this.partId = partId; this.group = group; this.voice = voice; this.inside = inside; this.updater = updater;
        }
        public String partId() { return partId; }
        public Group group() { return group; }
        public Voice voice() { return voice; }
        /** Heard only inside the vehicle. */
        public boolean inside() { return inside; }
        public double levelDb() { return levelDb; }
        public double gain() { return input == null ? 0 : input.gain(); }
        /** What its model was set to this tick (for the debug overlay). */
        public String detail() { return detail; }
    }

    /** Sets a source's model from the inputs and gives its sound power level, dB; notes what it set in the detail. */
    interface Updater { double update(SoundInputs in, StringBuilder detail); }

    private final List<Source> sources = new ArrayList<>();
    private final Mix outside = new Mix(), insideMix = new Mix();
    private final Filters.OnePole muffle = new Filters.OnePole(20_000);
    private volatile double muffleHz = 20_000;
    private final Voice voice;
    private final boolean openBody;
    private final Transmission.Insulation cabin;
    private final EventVoice impacts = new EventVoice();
    private long lastImpactNanos = Long.MIN_VALUE / 2;
    private double lastImpactDb = -100;
    private final long seed;

    private VehicleSound(boolean openBody, Transmission.Insulation cabin, long seed) {
        this.openBody = openBody;
        this.cabin = cabin;
        this.seed = seed;
        float[][] scratch = {new float[0]};
        this.voice = (out, offset, n) -> {
            if (scratch[0].length < n) { scratch[0] = new float[n]; }
            outside.render(out, offset, n);
            muffle.tune(muffleHz);
            for (int i = 0; i < n; i++) { out[offset + i] = (float) muffle.lowpass(out[offset + i]); }
            insideMix.render(scratch[0], 0, n);
            for (int i = 0; i < n; i++) { out[offset + i] = (float) Dsp.softClip(out[offset + i] + scratch[0][i]); }
        };
    }

    /** The sound of a compiled vehicle (null when nothing in it makes one); seed sets its individual sound. */
    public static VehicleSound of(Vehicle vehicle, long seed) {
        boolean open = false, boat = false, aircraft = false, heavy = vehicle.properties().massKg() > 7500, cabinSeats = false;
        double length = 0;
        List<Transmission.Element> walls = new ArrayList<>();
        for (Vehicle.Installed p : vehicle.installed()) {
            if (p.part().design() instanceof BodyDesign b) {
                open |= SoundProfiles.open(b);
                walls.addAll(Cabin.walls(b));
                boat |= b.settings().purpose() == BodySettingsDesign.Purpose.BOAT;
                aircraft |= b.settings().purpose() == BodySettingsDesign.Purpose.AIRCRAFT;
                for (var m : b.modules()) { length += m.lengthM(); cabinSeats |= m.seats() > 0; }
            }
            if (p.part().design() instanceof FifthWheelDesign) { heavy = true; }
            if (p.part().design() instanceof ChassisDesign c) {
                for (ChassisStationDesign st : c.stations()) { if (st.kind() == ChassisStationDesign.Kind.KINGPIN) { heavy = true; } }
            }
        }
        VehicleSound s = new VehicleSound(open, walls.isEmpty() ? Transmission.Insulation.NONE : Transmission.insulation(walls), seed);
        int k = 0;
        for (Vehicle.Installed p : vehicle.installed()) { PartSounds.add(s, p, seed * 31 + (k++)); }
        if (s.sources.isEmpty()) { return null; }
        s.vehicleWide(boat, aircraft, heavy, length, cabinSeats && !open);
        return s;
    }

    /** The part id of an engine on a stand ({@link #engine}): its inputs answer for it, its crank is rotor 0. */
    public static final String STAND_ENGINE = "engine";

    /** A piston engine alone on a test stand: its sound in the open, nothing else. */
    public static VehicleSound engine(dev.mcmod.hdfv.part.engine.PistonEngineDesign design, long seed) {
        VehicleSound s = new VehicleSound(true, Transmission.Insulation.NONE, seed);
        PartSounds.piston(s, STAND_ENGINE, design, dev.mcmod.hdfv.part.engine.PistonEngineDesign.SHOWN_BOOST + 1, 0, seed);
        return s;
    }

    /** The sounds of the vehicle as a whole. */
    private void vehicleWide(boolean boat, boolean aircraft, boolean airBraked, double lengthM, boolean closedCabin) {
        // the horn
        if (!aircraft) {
            Horn.Spec spec = boat ? Horn.ship(lengthM, seed) : airBraked ? Horn.truck(seed) : Horn.car(seed);
            Horn horn = new Horn(spec);
            add(new Source("horn", Group.CONTROLS, horn, false, (in, d) -> {
                boolean on = in.control("horn") > 0.5;
                horn.sound(on);
                if (on) { d.append(spec.kind().name().toLowerCase(Locale.ROOT)); }
                return Horn.soundPowerDb(spec, on);
            }));
        }
        // the indicators' relay, inside a closed cabin: a click as each flash starts and ends
        if (closedCabin) {
            EventVoice relay = new EventVoice();
            ImpactNoise.Spec click = new ImpactNoise.Spec(0.0005, 0.02, 0.015, 5200, 0.01, Surface.STEEL);
            float[] on = ImpactNoise.render(click, 0.6, seed), off = ImpactNoise.render(click, 0.4, seed + 1);
            boolean[] was = {false};
            add(new Source("relay", Group.CONTROLS, relay, true, (in, d) -> {
                double indicators = in.control("indicators");
                boolean lit = Math.abs(indicators) > 0.5 && LampRules.blinkOn(new LampRules.Inputs(0, 0, 0, false, in.time() * 20, in.blinkSeed(), indicators));
                if (lit != was[0] && Math.abs(indicators) > 0.5) { relay.play(lit ? on : off, 60); }
                was[0] = lit;
                return relay.levelDb();
            }));
        }
        // the handbrake: a ratchet on a car, a hiss of air on an air-braked one; a trailer coupling's clunk and hiss
        EventVoice brakes = new EventVoice();
        GasJetNoise.Spec chamber = new GasJetNoise.Spec(0.006, 8.5e5, 0.015, 293, seed);
        ImpactNoise.Spec pawl = new ImpactNoise.Spec(0.003, 0.05, 0.04, 5200, 0.004, Surface.STEEL);
        double[] was = {Double.NaN, Double.NaN};
        int[] braking = {0};
        add(new Source("brakes", Group.CONTROLS, brakes, false, (in, d) -> {
            double handbrake = in.control("handbrake"), brake = in.control("brake"), coupling = in.control("coupling");
            if (!Double.isNaN(was[0]) && (handbrake > 0.5) != (was[0] > 0.5)) {
                if (airBraked) { brakes.play(release(chamber, handbrake > 0.5 ? 1 : 0.4, seed), handbrake > 0.5 ? 118 : 108); }
                else if (handbrake > 0.5) { brakes.play(ratchet(pawl, seed), 78); }
                else { brakes.play(ImpactNoise.render(pawl, 0.5, seed + 3), 74); }
            }
            // an air-braked vehicle lets the service brake's air go when the pedal comes up after a stop
            if (brake > 0.5) { braking[0]++; }
            else {
                if (airBraked && braking[0] > 20) { brakes.play(release(chamber, 0.3, seed + 7), 104); }
                braking[0] = 0;
            }
            if (!Double.isNaN(was[1]) && Math.rint(coupling) != Math.rint(was[1])) {
                ImpactNoise.Spec jaws = ImpactNoise.Spec.steelPart(0.6);
                if (Math.rint(coupling) == 1) { brakes.play(ImpactNoise.render(jaws, 1, seed + 11), 112); brakes.play(release(chamber, 0.2, seed + 12), 100); }
                else if (Math.rint(coupling) == 0) { brakes.play(ImpactNoise.render(jaws, 0.7, seed + 13), 106); brakes.play(release(chamber, 0.5, seed + 14), 108); }
            }
            was[0] = handbrake;
            was[1] = coupling;
            return brakes.levelDb();
        }));
        // the wipers: a small motor while switched on, or on automatic in the rain
        MotorNoise.Spec wiperSpec = MotorNoise.Spec.small(60, seed);
        MotorNoise wiper = new MotorNoise(wiperSpec);
        add(new Source("wipers", Group.CONTROLS, wiper, false, (in, d) -> {
            double mode = Math.rint(in.control("wipers"));
            boolean on = !aircraft && (mode == 1 || (mode == 0 && in.raining() && closedCabin));
            MotorNoise.State st = new MotorNoise.State(on ? 45 : 0, on ? 0.6 : 0, 0);
            wiper.set(st);
            return on ? MotorNoise.soundPowerDb(wiperSpec, st) - 8 : -100;
        }));
        // impacts (the server tells) and scraping
        add(new Source("impacts", Group.DRIVING, impacts, false, (in, d) -> impacts.levelDb()));
        Scrape scrape = new Scrape(seed);
        add(new Source("scrape", Group.DRIVING, scrape, false, (in, d) -> {
            SoundInputs.Scrape sc = in.scrape();
            scrape.set(sc);
            if (sc == null) { return -100; }
            d.append(String.format(Locale.ROOT, "%.0f W at %.1f m/s on %s", sc.powerW(), sc.slideMps(), sc.surface()));
            return Dsp.powerLevelDb(3e-5 * sc.powerW()) + (sc.surface().rings() ? 0 : -6);
        }));
    }

    /** A chamber of air let out (a share of the full release's length). */
    private static float[] release(GasJetNoise.Spec chamber, double share, long seed) {
        GasJetNoise g = new GasJetNoise(new GasJetNoise.Spec(chamber.volumeM3() * Math.max(0.05, share), chamber.pressurePa(), chamber.orificeM(), chamber.temperatureK(), seed));
        g.release();
        return Voice.render(g, 0.15 + 0.9 * share);
    }

    /** A handbrake's pawl clicking over its ratchet as the lever comes up. */
    private static float[] ratchet(ImpactNoise.Spec pawl, long seed) {
        float[] out = new float[(int) (0.45 * Voice.RATE)];
        for (int c = 0; c < 6; c++) {
            float[] click = ImpactNoise.render(pawl, 0.4 + 0.05 * c, seed + c);
            int at = (int) (c * 0.07 * Voice.RATE);
            for (int i = 0; i < click.length && at + i < out.length; i++) { out[at + i] += click[i]; }
        }
        return out;
    }

    /** The server's report of an impact on this vehicle: impulse (N s), closing speed (m/s), what it hit. */
    public void impact(double impulseNs, double closingMps, Surface against) {
        ImpactNoise.Spec panel = ImpactNoise.Spec.carPanel(against == null ? Surface.ROCK : against);
        double level = ImpactNoise.soundPowerDb(panel, impulseNs, closingMps);
        // a rule: impacts within 0.12 s of the last are heard only when 6 dB louder (the panel is still ringing)
        long now = System.nanoTime();
        if (now - lastImpactNanos < 120_000_000L && level < lastImpactDb + 6) { return; }
        lastImpactNanos = now;
        lastImpactDb = level;
        double strength = Math.min(1, 0.25 + 0.15 * closingMps);
        impacts.play(ImpactNoise.render(panel, strength, seed ^ (long) (impulseNs * 13)), level);
    }

    static double tyre(SoundInputs in, StringBuilder d, String id, int wheel, TyreNoise model, TyreNoise.Spec spec, double wheelSpeed, double slip, double load, double rated) {
        double ground = in.velocity().length();
        d.append(String.format(Locale.ROOT, "v %.1f wheel %.1f slip %.1f load %.1fk", ground, wheelSpeed, slip, load / 1000));
        if (load <= 1) { model.set(TyreNoise.State.STILL); return -100; }   // in the air
        boolean locked = Math.abs(wheelSpeed) < 0.3 && ground > 2;
        Surface surface = in.surface(id, wheel);
        TyreNoise.State st = new TyreNoise.State(Math.max(Math.abs(wheelSpeed), ground), slip, load, rated, surface == null ? Surface.ROCK : surface, in.wet(), locked);
        model.set(st);
        return TyreNoise.soundPowerDb(spec, st);
    }

    void add(Source s) {
        s.input = (s.inside ? insideMix : outside).add(s.voice, 0);
        sources.add(s);
    }

    public List<Source> sources() { return sources; }
    /** The vehicle's whole sound: render on the synthesis thread. */
    public Voice voice() { return voice; }
    public boolean openBody() { return openBody; }
    /** What the closed cabin's walls leave of the outside. */
    public Transmission.Insulation cabin() { return cabin; }

    /** Reads the vehicle's state into the sources; the loudest source's level is returned (dB sound power). */
    public double update(SoundInputs in) {
        double loudest = -100;
        StringBuilder d = new StringBuilder();
        for (Source s : sources) {
            double l;
            d.setLength(0);
            try { l = s.updater.update(in, d); } catch (RuntimeException e) { l = -100; d.append(e); }
            s.detail = d.toString();
            s.levelDb = Double.isFinite(l) ? l : -100;
            loudest = Math.max(loudest, s.levelDb);
        }
        return loudest;
    }

    /** The sound pressure level (dB) of all sources at a distance, outside or within the closed cabin. */
    public double levelAt(double distanceM, boolean insideCabin) {
        double sum = 0;
        for (Source s : sources) {
            if (s.inside && !insideCabin) { continue; }
            sum += Math.pow(10, heardDb(s, distanceM, insideCabin) / 10);
        }
        return 10 * Math.log10(Math.max(sum, 1e-30));
    }

    private double heardDb(Source s, double distanceM, boolean insideCabin) {
        double l = Propagation.levelAt(s.levelDb, 0, Math.max(distanceM, insideCabin ? 1.5 : 0.5));
        if (insideCabin && !s.inside && !openBody) { l += Dsp.gainToDb(cabin.gain()); }
        return l;
    }

    /**
     * Sets the mix for a listener at a distance (outside, or inside the closed cabin), with the volume of each group
     * (1 = as heard), and the air's state for its absorption; returns the stream's playback gain (0-1) for OpenAL.
     * The sources share the total by their levels; a group's volume above 1 is applied in the mix.
     */
    public double balance(double distanceM, boolean insideCabin, Map<Group, Double> volumes, double temperatureK, double humidityPercent) {
        double total = levelAt(distanceM, insideCabin);
        double gain = Loudness.gain(total);
        for (Source s : sources) {
            if (s.input == null) { continue; }
            if (s.inside && !insideCabin) { s.input.gain(0); continue; }
            double share = Math.pow(10, (heardDb(s, distanceM, insideCabin) - total) / 20);
            s.input.gain(share * volumes.getOrDefault(s.group, 1.0) * (insideCabin ? volumes.getOrDefault(Group.INTERIOR, 1.0) : 1));
        }
        double air = Propagation.airCutoffHz(distanceM, temperatureK, humidityPercent, 101_325);
        double walls = insideCabin && !openBody ? 4000 / Math.sqrt(Math.max(1e-6, 1 / (cabin.gainHf() * cabin.gainHf()) - 1)) : 20_000;
        muffleHz = Math.min(air, walls);
        return gain;
    }

    /**
     * A seamless loop of this sound as set now (seconds long): its voice rendered a little longer, the end crossfaded
     * into the start. Render it from one thread only, on a sound not also streaming (a copy made for the loop).
     */
    public float[] bakeLoop(double seconds) { return loop(voice, seconds); }

    /** A seamless loop of any voice as set now: rendered a little longer, its end crossfaded into its start. */
    public static float[] loop(Voice voice, double seconds) {
        Voice.render(voice, 0.25);   // let the models settle on their state
        int length = (int) (seconds * Voice.RATE), fade = Math.max(1, (int) (0.2 * Voice.RATE));
        float[] x = Voice.render(voice, (double) (length + fade) / Voice.RATE), y = new float[length];
        for (int i = 0; i < length; i++) {
            double g = i < fade ? (double) i / fade : 1;
            y[i] = (float) (x[i] * g + (i < fade ? x[length + i] * (1 - g) : 0));
        }
        return y;
    }

    /** Kinds of sound heard from afar (AeroCore's FarSounds kind numbers are these ordinals). */
    public enum Far { PISTON, TURBINE, ROTOR, PROPELLER, JET, MOTOR }

    /**
     * Sets only the engines' sources from the inputs (the server's far signature: no events are made, nothing is
     * rendered); returns the loudest engine's level, dB of sound power (-100 when none sounds).
     */
    public double updateEngines(SoundInputs in) {
        double loudest = -100;
        StringBuilder d = new StringBuilder();
        for (Source s : sources) {
            if (s.group != Group.ENGINES) { continue; }
            d.setLength(0);
            double l;
            try { l = s.updater.update(in, d); } catch (RuntimeException e) { l = -100; }
            s.levelDb = Double.isFinite(l) ? l : -100;
            loudest = Math.max(loudest, s.levelDb);
        }
        return loudest;
    }

    /** What the loudest engine source is as heard from afar (null when no engine sounds). */
    public Far farKind() {
        Source top = null;
        for (Source s : sources) { if (s.group == Group.ENGINES && s.levelDb > -90 && (top == null || s.levelDb > top.levelDb)) { top = s; } }
        if (top == null) { return null; }
        Voice v = top.voice;
        if (v instanceof dev.mcmod.aerocore.api.acoustics.PistonEngineNoise) { return Far.PISTON; }
        if (v instanceof MotorNoise) { return Far.MOTOR; }
        if (v instanceof dev.mcmod.aerocore.api.acoustics.RotorNoise r) { return r.spec().kind() == dev.mcmod.aerocore.api.acoustics.RotorNoise.Kind.ROTOR ? Far.ROTOR : Far.PROPELLER; }
        if (v instanceof dev.mcmod.aerocore.api.acoustics.TurbineNoise t) {
            return switch (t.spec().kind()) { case TURBOJET, TURBOFAN, TURBOSHAFT -> Far.TURBINE; default -> Far.JET; };
        }
        return Far.TURBINE;
    }

    /** The rotor whose speed is a vehicle's engine speed (its first engine's crank or spool; -1 when it has none). */
    public static int engineRotor(Vehicle vehicle) {
        for (Vehicle.Installed p : vehicle.installed()) {
            Object d = p.part().design();
            if (d instanceof dev.mcmod.hdfv.part.engine.ShaftTurbineDesign) { return p.firstRotor() + 1; }
            if (d instanceof dev.mcmod.hdfv.part.engine.PistonEngineDesign || d instanceof dev.mcmod.hdfv.part.engine.JetEngineDesign
                    || d instanceof dev.mcmod.hdfv.part.engine.TurbofanDesign || d instanceof dev.mcmod.hdfv.part.engine.TurboRamjetDesign
                    || d instanceof dev.mcmod.hdfv.part.MotorDesign) { return p.firstRotor(); }
        }
        return -1;
    }

    /**
     * A far vehicle's voice: a typical one of its kind with its engine at a speed (rev/s): a big V8, a turbofan
     * (22 blades, 1.5 m), a 4-blade 12 m rotor (slapping a little), a 3-blade 2.6 m propeller, a rocket's jet, a
     * 100 kW traction motor. At a kilometre only their low notes are left (the listener's filters take the rest).
     */
    public static Voice far(Far kind, double revPerS, long seed) {
        double rev = Math.max(0, revPerS);
        return switch (kind) {
            case PISTON -> {
                dev.mcmod.aerocore.api.acoustics.PistonEngineNoise m = new dev.mcmod.aerocore.api.acoustics.PistonEngineNoise(
                        SoundProfiles.piston(dev.mcmod.hdfv.part.engine.SampleEngines.vEightOhv(), seed));
                m.set(new dev.mcmod.aerocore.api.acoustics.PistonEngineNoise.State(Math.max(600, rev * 60), 0.8, 0.8, true, false, 1, 0));
                yield m;
            }
            case TURBINE -> {
                dev.mcmod.aerocore.api.acoustics.TurbineNoise m = new dev.mcmod.aerocore.api.acoustics.TurbineNoise(
                        new dev.mcmod.aerocore.api.acoustics.TurbineNoise.Spec(dev.mcmod.aerocore.api.acoustics.TurbineNoise.Kind.TURBOFAN, 0.9, 22, 1.5, 60, 0, seed));
                m.set(new dev.mcmod.aerocore.api.acoustics.TurbineNoise.State(Math.max(20, rev), 380, 700, 0, Math.toRadians(60), 0, true));
                yield m;
            }
            case ROTOR, PROPELLER -> {
                boolean rotor = kind == Far.ROTOR;
                dev.mcmod.aerocore.api.acoustics.RotorNoise m = new dev.mcmod.aerocore.api.acoustics.RotorNoise(new dev.mcmod.aerocore.api.acoustics.RotorNoise.Spec(
                        rotor ? dev.mcmod.aerocore.api.acoustics.RotorNoise.Kind.ROTOR : dev.mcmod.aerocore.api.acoustics.RotorNoise.Kind.PROPELLER, rotor ? 4 : 3, rotor ? 12 : 2.6, seed));
                m.set(new dev.mcmod.aerocore.api.acoustics.RotorNoise.State(Math.max(rotor ? 3 : 20, rev), rotor ? 600e3 : 300e3, rotor ? 0.3 : 0, 0));
                yield m;
            }
            case JET -> {
                dev.mcmod.aerocore.api.acoustics.TurbineNoise m = new dev.mcmod.aerocore.api.acoustics.TurbineNoise(
                        new dev.mcmod.aerocore.api.acoustics.TurbineNoise.Spec(dev.mcmod.aerocore.api.acoustics.TurbineNoise.Kind.ROCKET, 0.6, 0, 0, 0, 0, seed));
                m.set(new dev.mcmod.aerocore.api.acoustics.TurbineNoise.State(0, 1500, 2500, 1, Math.toRadians(60), 0, true));
                yield m;
            }
            case MOTOR -> {
                MotorNoise m = new MotorNoise(MotorNoise.Spec.traction(100e3, seed));
                m.set(new MotorNoise.State(Math.max(5, rev), 0.8, 0));
                yield m;
            }
        };
    }

    /** Group volumes all at 1. */
    public static Map<Group, Double> unity() {
        Map<Group, Double> m = new EnumMap<>(Group.class);
        for (Group g : Group.values()) { m.put(g, 1.0); }
        return m;
    }

    /** A closed cabin's walls, from a body's modules (for its insulation). */
    static final class Cabin {
        private Cabin() {}

        /** The closed modules with seats: their skin, glass and a seal leak. */
        static List<Transmission.Element> walls(BodyDesign b) {
            List<Transmission.Element> walls = new ArrayList<>();
            for (var m : b.modules()) {
                if (m.open() || m.seats() <= 0) { continue; }
                double side = 2 * m.lengthM() * m.heightM() + m.lengthM() * m.widthM() * 2;
                double glass = side * Math.clamp(m.windowShare(), 0, 0.9);
                double skinDensity = m.material().densityKgPerM3() * Math.max(5e-4, m.skinM());
                walls.add(new Transmission.Element(side - glass, skinDensity));
                walls.add(new Transmission.Element(glass, 10));   // 4 mm glass
                walls.add(Transmission.Element.opening(1e-4 * Math.max(1, m.doors())));   // door seals
            }
            return walls;
        }
    }

    /** Something scraping along: friction's broadband rasp with grit and squeaks, as loud as its power says. */
    static final class Scrape implements Voice {
        private final Noise noise;
        private final Filters.Svf band = new Filters.Svf(1800, 0.6);
        private volatile SoundInputs.Scrape now;
        private double level, crack;

        Scrape(long seed) { this.noise = new Noise(seed); }

        void set(SoundInputs.Scrape s) { now = s; }

        @Override public void render(float[] out, int offset, int n) {
            SoundInputs.Scrape s = now;
            double target = s == null ? 0 : Math.min(1, s.slideMps() / 5 + 0.2);
            double rings = s != null && s.surface().rings() ? 1 : 0.3;
            for (int i = 0; i < n; i++) {
                level += (target - level) * 0.002;
                band.process(noise.white());
                if (noise.chance(level * 300.0 / RATE)) { crack = 0.6 * rings; }
                crack *= 0.95;
                out[offset + i] = (float) Dsp.softClip(level * (band.band * 0.9 + crack * noise.white()));
            }
        }
    }
}
