package dev.mcmod.hdfv.sound;

import dev.mcmod.aerocore.api.acoustics.Dsp;
import dev.mcmod.aerocore.api.acoustics.FlowNoise;
import dev.mcmod.aerocore.api.acoustics.ImpactNoise;
import dev.mcmod.aerocore.api.acoustics.MotorNoise;
import dev.mcmod.aerocore.api.acoustics.PistonEngineNoise;
import dev.mcmod.aerocore.api.acoustics.RotorNoise;
import dev.mcmod.aerocore.api.acoustics.Surface;
import dev.mcmod.aerocore.api.acoustics.TurbineNoise;
import dev.mcmod.aerocore.api.acoustics.TyreNoise;
import dev.mcmod.aerocore.api.vehicle.Part;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.Vehicle;
import dev.mcmod.aerocore.api.vehicle.part.AxlePart;
import dev.mcmod.hdfv.part.MotorDesign;
import dev.mcmod.hdfv.part.PropellerDesign;
import dev.mcmod.hdfv.part.air.AirPropellerDesign;
import dev.mcmod.hdfv.part.air.RotorDesign;
import dev.mcmod.hdfv.part.body.BodyDesign;
import dev.mcmod.hdfv.part.drive.GearboxDesign;
import dev.mcmod.hdfv.part.engine.Exhaust;
import dev.mcmod.hdfv.part.engine.JetEngineDesign;
import dev.mcmod.hdfv.part.engine.PistonEngineDesign;
import dev.mcmod.hdfv.part.engine.PulsejetDesign;
import dev.mcmod.hdfv.part.engine.RamjetDesign;
import dev.mcmod.hdfv.part.engine.ScramjetDesign;
import dev.mcmod.hdfv.part.engine.ShaftTurbineDesign;
import dev.mcmod.hdfv.part.engine.SolidRocketDesign;
import dev.mcmod.hdfv.part.engine.TurboRamjetDesign;
import dev.mcmod.hdfv.part.engine.TurbofanDesign;
import dev.mcmod.hdfv.part.wheel.AxleDesign;
import dev.mcmod.hdfv.part.wheel.LandingGearDesign;
import dev.mcmod.hdfv.part.wheel.SteerSchedule;
import dev.mcmod.hdfv.part.wheel.TyreDesign;

import java.util.Locale;

/**
 * Which parts sound how (0.19): each part's design and its synced state to AeroCore's sound models. Rules where the
 * state is not synced:
 * <ul>
 * <li>Jets: the jet's speed from the exhaust's shown temperature and Mach (static temperature, gamma 1.33); the
 *     nozzle's diameter 0.55 of a turbojet's compressor tip, 0.8 of a turbofan's fan tip (an equivalent mixed jet),
 *     a ramjet's or scramjet's capture diameter, a rocket's exit; the listener 60 degrees off the jet's axis (the
 *     vehicle's mix has one direction).</li>
 * <li>Propellers and rotors: the shaft power 0.05 rho n^3 D^5 (a typical power coefficient) in air, 0.03 in water;
 *     a helicopter rotor slaps while the vehicle sinks faster than half its induced velocity.</li>
 * <li>Electric motors: 4 poles, an inverter at 4 kHz; the load is the throttle.</li>
 * <li>Gearboxes: a gear's engagement clunks (a manual's louder), a grinding manual rasps; tyres and suspension: a
 *     wheel hitting its bump stop knocks; a retractable leg's motor whines while it folds and locks at the end.</li>
 * </ul>
 */
final class PartSounds {
    private PartSounds() {}

    static void add(VehicleSound s, Vehicle.Installed p, long seed) {
        Part part = p.part();
        PartDesign design = part.design();
        String id = p.id();
        int rotor = p.firstRotor();
        if (design instanceof PistonEngineDesign d) { piston(s, id, d, part.shownValues(), rotor, seed); }
        else if (design instanceof JetEngineDesign d) { jet(s, id, design, TurbineNoise.Kind.TURBOJET, 0.55 * d.compressor().tipDiameterM(), 0, 0, d.compressor().stages() > 0 ? 30 : 0, rotor, seed); }
        else if (design instanceof TurbofanDesign d) { jet(s, id, design, TurbineNoise.Kind.TURBOFAN, 0.8 * d.fan().tipDiameterM(), d.fan().blades(), d.fan().tipDiameterM(), 60, rotor, seed); }
        else if (design instanceof TurboRamjetDesign d) { jet(s, id, design, TurbineNoise.Kind.TURBOJET, 0.6 * d.compressor().tipDiameterM(), 0, 0, 30, rotor, seed); }
        else if (design instanceof ShaftTurbineDesign d) { jet(s, id, design, TurbineNoise.Kind.TURBOSHAFT, 0.5 * d.compressor().tipDiameterM(), 0, 0, 30, rotor + 1, seed); }
        else if (design instanceof RamjetDesign d) { jet(s, id, design, TurbineNoise.Kind.RAMJET, diameter(d.inlet().captureAreaM2()), 0, 0, 0, -1, seed); }
        else if (design instanceof ScramjetDesign d) { jet(s, id, design, TurbineNoise.Kind.RAMJET, diameter(d.inlet().captureAreaM2()), 0, 0, 0, -1, seed); }
        else if (design instanceof SolidRocketDesign d) { jet(s, id, design, TurbineNoise.Kind.ROCKET, d.nozzle().throatDiameterM() * Math.sqrt(Math.max(1, d.nozzle().expansionRatio())), 0, 0, 0, -1, seed); }
        else if (design instanceof PulsejetDesign d) { pulsejet(s, id, d, seed); }
        else if (design instanceof MotorDesign d) { motor(s, id, d, rotor, seed); }
        else if (design instanceof AirPropellerDesign d) { rotor(s, id, RotorNoise.Kind.PROPELLER, d.blades().blades(), d.blades().diameterM(), rotor, -1, seed); }
        else if (design instanceof RotorDesign d) { rotor(s, id, RotorNoise.Kind.ROTOR, d.blades().blades(), 2 * d.blades().radiusM(), rotor, 6, seed); }
        else if (design instanceof PropellerDesign d) { rotor(s, id, RotorNoise.Kind.MARINE, d.blades(), d.diameterM(), rotor, -1, seed); }
        else if (design instanceof AxleDesign a) { axle(s, p, a, seed); }
        else if (design instanceof LandingGearDesign g) { leg(s, p, g, seed); }
        else if (design instanceof GearboxDesign g) { gearbox(s, id, g, seed); }
        else if (design instanceof BodyDesign b) { body(s, id, b, seed); }
    }

    static double diameter(double areaM2) { return Math.sqrt(4 * Math.max(1e-4, areaM2) / Math.PI); }

    static void piston(VehicleSound s, String id, PistonEngineDesign d, int shownCount, int rotor, long seed) {
        PistonEngineNoise.Spec spec = SoundProfiles.piston(d, seed);
        PistonEngineNoise model = new PistonEngineNoise(spec);
        s.add(new VehicleSound.Source(id, VehicleSound.Group.ENGINES, model, false, (in, dd) -> {
            double rpm = Math.abs(in.rotorSpeed(rotor)) * 30 / Math.PI;
            boolean running = in.shown(id, PistonEngineDesign.SHOWN_RUNNING) > 0.5;
            boolean cranking = !running && rpm > 30 && in.input(id, "ignition") >= 0;
            double boost = shownCount > PistonEngineDesign.SHOWN_BOOST ? Math.max(1, in.shown(id, PistonEngineDesign.SHOWN_BOOST)) : 1;
            PistonEngineNoise.State st = new PistonEngineNoise.State(rpm, in.shown(id, PistonEngineDesign.SHOWN_LOAD),
                    Math.clamp(in.input(id, "throttle"), 0, 1), running, cranking, boost, 0);
            model.set(st);
            dd.append(String.format(Locale.ROOT, "%.0f rpm load %.2f thr %.2f boost %.2f%s%s", rpm, st.load(), st.throttle(), boost,
                    running ? "" : " off", cranking ? " cranking" : ""));
            return PistonEngineNoise.soundPowerDb(spec, st);
        }));
    }

    /** A jet engine of any kind; spoolRotor -1 for one without a spool (its flame drives it). */
    private static void jet(VehicleSound s, String id, PartDesign design, TurbineNoise.Kind kind, double nozzleM, int fanBlades, double fanTipM, int turbineBlades, int spoolRotor, long seed) {
        int first = Exhaust.first(design);
        TurbineNoise.Spec spec = new TurbineNoise.Spec(kind, Math.max(0.02, nozzleM), fanBlades, fanTipM, turbineBlades, 0, seed);
        TurbineNoise model = new TurbineNoise(spec);
        s.add(new VehicleSound.Source(id, VehicleSound.Group.ENGINES, model, false, (in, dd) -> {
            double rev = spoolRotor >= 0 ? Math.abs(in.rotorSpeed(spoolRotor)) / (2 * Math.PI) : 0;
            double total = first >= 0 ? in.shown(id, first + Exhaust.KELVIN) : 0, mach = first >= 0 ? in.shown(id, first + Exhaust.MACH) : 0;
            double staticK = total / (1 + 0.5 * (Exhaust.GAMMA - 1) * mach * mach);
            double jet = mach > 0 && staticK > 0 ? mach * Math.sqrt(Exhaust.GAMMA * 287 * staticK) : 0;
            double flame = kind == TurbineNoise.Kind.RAMJET || kind == TurbineNoise.Kind.ROCKET ? in.shown(id, 0) : first == 2 ? in.shown(id, JetEngineDesign.SHOWN_REHEAT) : 0;
            boolean lit = jet > 1 || flame > 0.02;
            TurbineNoise.State st = new TurbineNoise.State(rev, jet, Math.max(288, total), flame, Math.toRadians(60), 0, lit);
            model.set(st);
            dd.append(String.format(Locale.ROOT, "%.0f rev/s jet %.0f m/s %.0f K flame %.2f", rev, jet, total, flame));
            return TurbineNoise.soundPowerDb(spec, st);
        }));
    }

    private static void pulsejet(VehicleSound s, String id, PulsejetDesign d, long seed) {
        TurbineNoise.Spec spec = new TurbineNoise.Spec(TurbineNoise.Kind.PULSEJET, Math.max(0.03, d.tube().tailDiameterM()), 0, 0, 0, 0, seed);
        TurbineNoise model = new TurbineNoise(spec);
        s.add(new VehicleSound.Source(id, VehicleSound.Group.ENGINES, model, false, (in, dd) -> {
            boolean lit = in.shown(id, PulsejetDesign.SHOWN_LIT) > 0.5;
            double hz = in.shown(id, PulsejetDesign.SHOWN_FREQUENCY);
            TurbineNoise.State st = new TurbineNoise.State(0, lit ? 100 : 0, 900, lit ? 1 : 0, Math.toRadians(60), hz, lit);
            model.set(st);
            dd.append(String.format(Locale.ROOT, "%.0f Hz swing %.0f Pa%s", hz, in.shown(id, PulsejetDesign.SHOWN_AMPLITUDE), lit ? "" : " out"));
            return TurbineNoise.soundPowerDb(spec, st) + (lit ? 10 * Math.log10(Math.max(0.1, in.shown(id, PulsejetDesign.SHOWN_AMPLITUDE) / 20_000)) : 0);
        }));
    }

    private static void motor(VehicleSound s, String id, MotorDesign d, int rotor, long seed) {
        MotorNoise.Spec spec = MotorNoise.Spec.traction(d.maxPowerW(), seed);
        MotorNoise model = new MotorNoise(spec);
        s.add(new VehicleSound.Source(id, VehicleSound.Group.ENGINES, model, false, (in, dd) -> {
            double rev = Math.abs(in.rotorSpeed(rotor)) / (2 * Math.PI);
            double load = Math.abs(in.input(id, "throttle"));
            MotorNoise.State st = new MotorNoise.State(rev, Math.clamp(load, 0, 1), 0);
            model.set(st);
            dd.append(String.format(Locale.ROOT, "%.0f rev/s load %.2f", rev, load));
            return MotorNoise.soundPowerDb(spec, st);
        }));
    }

    /** A propeller or rotor; inducedShown the index of a helicopter rotor's induced velocity (-1: none). */
    private static void rotor(VehicleSound s, String id, RotorNoise.Kind kind, int blades, double diameterM, int rotor, int inducedShown, long seed) {
        RotorNoise.Spec spec = new RotorNoise.Spec(kind, Math.max(1, blades), Math.max(0.05, diameterM), seed);
        RotorNoise model = new RotorNoise(spec);
        double rho = kind == RotorNoise.Kind.MARINE ? 1025 : 1.2, cp = kind == RotorNoise.Kind.MARINE ? 0.03 : 0.05;
        s.add(new VehicleSound.Source(id, VehicleSound.Group.ENGINES, model, false, (in, dd) -> {
            double rev = Math.abs(in.rotorSpeed(rotor)) / (2 * Math.PI);
            double power = cp * rho * Math.pow(rev, 3) * Math.pow(spec.diameterM(), 5);
            double slap = 0;
            if (inducedShown >= 0) {
                double induced = Math.abs(in.shown(id, inducedShown)), sink = -in.velocity().y();
                slap = induced > 0.5 && sink > 0.5 * induced ? Math.min(1, (sink - 0.5 * induced) / induced) : 0;
            }
            double tipMach = RotorNoise.tipMach(spec, rev);
            double cavitation = kind == RotorNoise.Kind.MARINE ? Math.clamp((tipMach * 343 - 25) / 15, 0, 1) : 0;
            if (kind == RotorNoise.Kind.MARINE && !in.inWater()) { model.set(RotorNoise.State.STILL); return -100; }
            RotorNoise.State st = new RotorNoise.State(rev, power, slap, cavitation);
            model.set(st);
            dd.append(String.format(Locale.ROOT, "%.1f rev/s tip M %.2f %.0f kW%s", rev, tipMach, power / 1000, slap > 0 ? String.format(Locale.ROOT, " slap %.2f", slap) : ""));
            return RotorNoise.soundPowerDb(spec, st);
        }));
    }

    private static void axle(VehicleSound s, Vehicle.Installed p, AxleDesign a, long seed) {
        AxlePart axle = SteerSchedule.axleOf(p.part());
        if (axle == null) { return; }
        String id = p.id();
        TyreDesign tyre = a.tyre();
        double radius = SoundProfiles.tyreRadius(tyre), rated = SoundProfiles.ratedLoadN(tyre);
        int extra = axle.extraShown(), rotors = p.part().rotors().size(), first = p.firstRotor();
        for (int side = 0; side < 2; side++) {
            int wheel = side, r = first + rotors - 2 + side;
            TyreNoise.Spec spec = SoundProfiles.tyre(tyre, seed * 7 + side);
            TyreNoise model = new TyreNoise(spec);
            s.add(new VehicleSound.Source(id, VehicleSound.Group.DRIVING, model, false, (in, dd) -> VehicleSound.tyre(in, dd, id, wheel, model, spec, in.rotorSpeed(r) * radius,
                    in.shown(id, extra + 2 * wheel), in.shown(id, extra + 2 * wheel + 1) * 1000, rated)));
        }
        // the bump stops: a knock when a wheel's compression comes up to the travel
        double travel = Math.max(0.02, a.suspension().travelM());
        EventVoice knocks = new EventVoice();
        ImpactNoise.Spec stop = ImpactNoise.Spec.steelPart(0.25);
        double[] was = {0, 0};
        s.add(new VehicleSound.Source(id, VehicleSound.Group.DRIVING, knocks, false, (in, dd) -> {
            for (int side = 0; side < 2; side++) {
                double c = in.shown(id, 3 * side + 2);
                if (c >= travel * 0.98 && was[side] < travel * 0.98 && in.velocity().length() > 1) {
                    double v = Math.min(3, Math.abs(c - was[side]) * 20);
                    knocks.play(ImpactNoise.render(stop, Math.min(1, 0.3 + v / 3), seed + side * 13L), 95 + 10 * Math.log10(Math.max(0.1, v)));
                }
                was[side] = c;
            }
            return knocks.levelDb();
        }));
    }

    private static void leg(VehicleSound s, Vehicle.Installed p, LandingGearDesign g, long seed) {
        String id = p.id();
        TyreDesign tyre = g.tyre();
        double radius = SoundProfiles.tyreRadius(tyre), rated = SoundProfiles.ratedLoadN(tyre);
        TyreNoise.Spec spec = SoundProfiles.tyre(tyre, seed);
        TyreNoise model = new TyreNoise(spec);
        int r = p.firstRotor();
        s.add(new VehicleSound.Source(id, VehicleSound.Group.DRIVING, model, false, (in, dd) -> VehicleSound.tyre(in, dd, id, 0, model, spec, in.rotorSpeed(r) * radius,
                in.shown(id, 3), in.shown(id, 4) * 1000, rated)));
        if (g.leg().retractTimeS() <= 0) { return; }
        // a retractable leg: its motor while it moves, a clunk as it locks
        MotorNoise.Spec motorSpec = MotorNoise.Spec.small(2000, seed);
        MotorNoise motor = new MotorNoise(motorSpec);
        EventVoice locks = new EventVoice();
        ImpactNoise.Spec lock = ImpactNoise.Spec.steelPart(0.4);
        double[] was = {Double.NaN};
        s.add(new VehicleSound.Source(id, VehicleSound.Group.CONTROLS, motor, false, (in, dd) -> {
            double f = in.shown(id, 5);
            boolean moving = !Double.isNaN(was[0]) && Math.abs(f - was[0]) > 1e-4;
            if (!Double.isNaN(was[0]) && (f >= 0.999 && was[0] < 0.999 || f <= 0.001 && was[0] > 0.001)) {
                locks.play(ImpactNoise.render(lock, 0.8, seed + 5), 100);
            }
            was[0] = f;
            MotorNoise.State st = new MotorNoise.State(moving ? 60 : 0, moving ? 0.8 : 0, 0);
            motor.set(st);
            dd.append(String.format(Locale.ROOT, "folded %.2f%s", f, moving ? " moving" : ""));
            return MotorNoise.soundPowerDb(motorSpec, st);
        }));
        s.add(new VehicleSound.Source(id, VehicleSound.Group.CONTROLS, locks, false, (in, dd) -> locks.levelDb()));
    }

    private static void gearbox(VehicleSound s, String id, GearboxDesign g, long seed) {
        boolean manual = g.kind() == dev.mcmod.hdfv.part.drive.Gearboxes.Kind.MANUAL;
        EventVoice clunks = new EventVoice();
        ImpactNoise.Spec gear = ImpactNoise.Spec.steelPart(manual ? 0.15 : 0.2);
        double[] was = {Double.NaN, 0};
        int[] grindTicks = {0};
        dev.mcmod.aerocore.api.acoustics.Noise noise = new dev.mcmod.aerocore.api.acoustics.Noise(seed);
        s.add(new VehicleSound.Source(id, VehicleSound.Group.CONTROLS, clunks, false, (in, dd) -> {
            double engaged = Math.rint(in.shown(id, 0)), state = in.shown(id, 1);
            if (!Double.isNaN(was[0]) && engaged != was[0] && Math.abs(engaged) < 20) {
                clunks.play(ImpactNoise.render(gear, manual ? 0.7 : 0.35, seed + (long) engaged), manual ? 82 : 72);
            }
            // a grinding manual: a rasp of teeth every few ticks
            if (state > 1.5 && ++grindTicks[0] % 3 == 0) {
                float[] rasp = new float[2205];
                for (int i = 0; i < rasp.length; i++) { rasp[i] = (float) (0.4 * noise.white() * Math.sin(Math.PI * i / rasp.length) * (0.5 + 0.5 * Math.sin(2 * Math.PI * 900 * i / 44100.0))); }
                clunks.play(rasp, 88);
            }
            was[0] = engaged;
            dd.append(String.format(Locale.ROOT, "gear %.0f state %.0f", engaged, state));
            return clunks.levelDb();
        }));
    }

    private static void body(VehicleSound s, String id, BodyDesign b, long seed) {
        FlowNoise.Spec spec = SoundProfiles.flow(b, seed);
        FlowNoise model = new FlowNoise(spec);
        double opening = SoundProfiles.open(b) ? 1 : 0;
        s.add(new VehicleSound.Source(id, VehicleSound.Group.DRIVING, model, false, (in, dd) -> {
            FlowNoise.State st = new FlowNoise.State(in.velocity().length(), Math.max(opening, in.control("doors") > 0.5 ? 0.6 : 0), 0.3);
            model.set(st);
            dd.append(String.format(Locale.ROOT, "air %.1f m/s", st.airspeedMps()));
            return FlowNoise.soundPowerDb(spec, st);
        }));
        // doors: a latch and a swing opening, a slam shutting (the doors move together on one channel)
        int doors = 0;
        for (var m : b.modules()) { doors += m.doors(); }
        if (doors > 0) {
            EventVoice door = new EventVoice();
            ImpactNoise.Spec panel = ImpactNoise.Spec.carPanel(Surface.STEEL);
            double[] was = {Double.NaN};
            int count = doors;
            s.add(new VehicleSound.Source(id, VehicleSound.Group.CONTROLS, door, false, (in, dd) -> {
                double now = in.control("doors");
                if (!Double.isNaN(was[0]) && (now > 0.5) != (was[0] > 0.5)) {
                    boolean opening2 = now > 0.5;
                    door.play(ImpactNoise.render(panel, opening2 ? 0.35 : 0.9, seed + (opening2 ? 1 : 2)), (opening2 ? 78 : 92) + 10 * Math.log10(Math.min(4, count)));
                }
                was[0] = now;
                return door.levelDb();
            }));
        }
        // water: a boat's hull
        if (b.settings().purpose() == dev.mcmod.hdfv.part.body.BodySettingsDesign.Purpose.BOAT || b.modules().stream().anyMatch(m -> m.deadriseDeg() > 0)) {
            double beam = 0, deadrise = 15;
            for (var m : b.modules()) { beam = Math.max(beam, m.widthM()); if (m.deadriseDeg() > 0) { deadrise = m.deadriseDeg(); } }
            dev.mcmod.aerocore.api.acoustics.WaterNoise.Spec ws = new dev.mcmod.aerocore.api.acoustics.WaterNoise.Spec(Math.max(0.5, beam), deadrise, seed);
            dev.mcmod.aerocore.api.acoustics.WaterNoise water = new dev.mcmod.aerocore.api.acoustics.WaterNoise(ws);
            double[] wasVy = {0};
            boolean[] wasIn = {false};
            s.add(new VehicleSound.Source(id, VehicleSound.Group.DRIVING, water, false, (in, dd) -> {
                boolean wet = in.inWater();
                double v = Math.hypot(in.velocity().x(), in.velocity().z()), vy = in.velocity().y();
                if (wet && !wasIn[0] && wasVy[0] < -1.5) { water.slam(Math.min(1, -wasVy[0] / 6)); }
                wasIn[0] = wet;
                wasVy[0] = vy;
                dev.mcmod.aerocore.api.acoustics.WaterNoise.State st = new dev.mcmod.aerocore.api.acoustics.WaterNoise.State(v, Math.max(0, v * 0.6), Math.min(1, v / 20), wet ? 1 : 0);
                water.set(st);
                dd.append(String.format(Locale.ROOT, "water %.1f m/s%s", v, wet ? "" : " dry"));
                return dev.mcmod.aerocore.api.acoustics.WaterNoise.soundPowerDb(ws, st);
            }));
        }
    }

    /** dB of sound power from W. */
    static double db(double watts) { return Dsp.powerLevelDb(watts); }
}
