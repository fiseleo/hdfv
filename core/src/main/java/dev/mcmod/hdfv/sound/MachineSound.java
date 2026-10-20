package dev.mcmod.hdfv.sound;

import dev.mcmod.aerocore.api.acoustics.GasJetNoise;
import dev.mcmod.aerocore.api.acoustics.ImpactNoise;
import dev.mcmod.aerocore.api.acoustics.MotorNoise;
import dev.mcmod.aerocore.api.acoustics.ProcessNoise;
import dev.mcmod.aerocore.api.acoustics.Voice;
import dev.mcmod.hdfv.manufacture.Head;
import dev.mcmod.hdfv.manufacture.Process;
import dev.mcmod.hdfv.manufacture.Station;

/**
 * How the workshop and the plant sound (0.19): what a machine, a power unit or a petroleum unit is doing, as one of
 * AeroCore's machine processes ({@link ProcessNoise}) with its power, cutting teeth and speeds; and the short sounds
 * that happen there (a door running, a gusher, a weld, a rivet).
 * <ul>
 * <li>Machining: the step's process on the machine that does it; a family's head decides (a band saw saws, an impact
 *     riveter hammers, a plasma table's torch hisses). Speeds are typical ones: a lathe 480 rpm, a mill's 4-flute cutter
 *     600 rpm, a hob 120 rpm with 12 gashes, a wheel 1800 rpm, a band saw's teeth passing at 100 Hz, a cold saw's at 120,
 *     a shaper's ram once a second, a planer's table every 3 s, a broach every 5 s, a forging press every 3 s.</li>
 * <li>Forming: a foundry's furnace and a vulcanizer's steam roar while working, a composite oven's fans and heaters;
 *     an assembly bench's pneumatic tools.</li>
 * <li>Power: a generator or motor hums at its speed, a steam engine exhausts four times a turn (two double-acting
 *     cylinders), a treadle's belt slaps.</li>
 * <li>Petroleum and fluids: a drilling rig's rotary table and drawworks, a pump jack's stroke, a refinery's fired
 *     heaters and pumps, a fuel dispenser's or a pump's hum and flow, compressors in the liquefier and air separator.</li>
 * </ul>
 * A {@link Hum} goes from the server to the clients near (hdfv's sound sync); clients make the loops from it.
 */
public final class MachineSound {
    private MachineSound() {}

    /**
     * What a block sounds like now: a process (null for an engine stand, whose engine streams as a vehicle's does), its
     * power (W), teeth, the drive's speed (rev/s), its stroke rate (Hz), how hard it works (0-1) and whether it runs.
     */
    public record Hum(ProcessNoise.Process process, double powerW, int teeth, double revPerS, double strokeHz, double load, boolean running) {
        public Hum {
            powerW = Double.isFinite(powerW) ? Math.max(0, powerW) : 0;
            revPerS = Double.isFinite(revPerS) ? Math.max(0, revPerS) : 0;
            strokeHz = Double.isFinite(strokeHz) ? Math.max(0, strokeHz) : 0;
            load = Double.isFinite(load) ? Math.clamp(load, 0, 1) : 0;
            teeth = Math.clamp(teeth, 0, 255);
        }

        /** An engine stand running (its engine's own sound comes from the stand's synced speed and load). */
        public static Hum stand(boolean running, double load) { return new Hum(null, 0, 0, 0, 0, load, running); }

        /** The model's spec, a seed making each machine its own. */
        public ProcessNoise.Spec spec(long seed) { return new ProcessNoise.Spec(process, Math.max(50, powerW), teeth, revPerS, strokeHz, seed); }

        public ProcessNoise.State state() { return new ProcessNoise.State(load, running); }

        /** Its sound power level, dB (-100 when silent). */
        public double levelDb() { return process == null ? -100 : ProcessNoise.soundPowerDb(spec(0), state()); }

        /** Whether it differs enough from what was sent to send again: another process, teeth or running, the load 0.1 off, a speed or power 5 % off. */
        public boolean differs(Hum sent) {
            if (sent == null || sent.process != process || sent.teeth != teeth || sent.running != running) { return true; }
            return Math.abs(load - sent.load) >= 0.1 || off(revPerS, sent.revPerS, 0.05) || off(strokeHz, sent.strokeHz, 0.05) || off(powerW, sent.powerW, 0.1);
        }

        private static boolean off(double a, double b, double share) { return Math.abs(a - b) > share * Math.max(Math.abs(b), 1e-3) && Math.abs(a - b) > 1e-3; }
    }

    /** What a step of a process sounds like on a station (with its head when it is a family's machine). */
    public static ProcessNoise.Process process(Station station, Head head, Process step) {
        if (head != null) {
            switch (head) {
                case CYLINDRICAL_GRINDING, INTERNAL_GRINDING, SURFACE_GRINDING, CENTRELESS_GRINDING, TOOL_GRINDING, GEAR_GRINDING -> { return ProcessNoise.Process.GRINDING; }
                case HONING, LAPPING, POLISHING, DEBURRING, CHAMFERING -> { return ProcessNoise.Process.FINISHING; }
                case HOBBING, GEAR_SHAPING, SHAVING -> { return ProcessNoise.Process.GEAR_CUTTING; }
                case SHAPER, SLOTTER, BROACHING -> { return ProcessNoise.Process.STROKE; }
                case BAND_SAW, COLD_SAW, HACKSAW -> { return ProcessNoise.Process.SAWING; }
                case SINKER_EDM, WIRE_EDM, HOLE_EDM -> { return ProcessNoise.Process.SPARK_EROSION; }
                case ECM -> { return ProcessNoise.Process.ELECTROCHEMICAL; }
                case LASER -> { return ProcessNoise.Process.LASER; }
                case PLASMA -> { return ProcessNoise.Process.PLASMA; }
                case WATERJET -> { return ProcessNoise.Process.WATERJET; }
                case THREAD_ROLLING, FLAT_DIE_ROLLING, TAPPING -> { return ProcessNoise.Process.THREADING; }
                case THREAD_MILLING -> { return ProcessNoise.Process.MILLING; }
                case SQUEEZE_RIVETER -> { return ProcessNoise.Process.RIVET_SQUEEZE; }
                case IMPACT_RIVETER -> { return ProcessNoise.Process.RIVET_IMPACT; }
                case DRILL_RIVETER -> { return ProcessNoise.Process.RIVET_DRILL; }
            }
        }
        if (station == Station.FOUNDRY) { return ProcessNoise.Process.BURNER; }
        if (station == Station.VULCANIZER) { return ProcessNoise.Process.STEAM; }
        if (station == Station.COMPOSITE_OVEN) { return step == Process.LAYUP ? ProcessNoise.Process.FAN : ProcessNoise.Process.CURING; }
        if (step == null) { return ProcessNoise.Process.HUM; }
        return switch (step) {
            case CAST -> ProcessNoise.Process.BURNER;
            case FORGE, FORM -> ProcessNoise.Process.PRESS;
            case LAYUP -> ProcessNoise.Process.FAN;
            case CURE -> ProcessNoise.Process.CURING;
            case VULCANIZE -> ProcessNoise.Process.STEAM;
            case TURN -> ProcessNoise.Process.TURNING;
            case MILL -> ProcessNoise.Process.MILLING;
            case PLANE -> ProcessNoise.Process.STROKE;
            case BORE -> ProcessNoise.Process.BORING;
            case DRILL -> ProcessNoise.Process.DRILLING;
            case GRIND -> ProcessNoise.Process.GRINDING;
            case WELD -> ProcessNoise.Process.WELD_MIG;
            case ASSEMBLE -> ProcessNoise.Process.COMPRESSOR;
            case SAW -> ProcessNoise.Process.SAWING;
            case LAP, POLISH, DEBURR -> ProcessNoise.Process.FINISHING;
            case TAP, THREAD -> ProcessNoise.Process.THREADING;
            case PROFILE -> station == Station.WELDING_STATION ? ProcessNoise.Process.BURNER : ProcessNoise.Process.MILLING;
            case RIVET -> ProcessNoise.Process.RIVET_IMPACT;
        };
    }

    /**
     * A machine at work: its station, head (null unless a family's), the step it does (null: idle), its rated power
     * (W), the share of it it gets (0-1) and whether it works now.
     */
    public static Hum machine(Station station, Head head, Process step, double ratedW, double share, boolean working) {
        ProcessNoise.Process p = process(station, head, step);
        double power = ratedW > 0 ? ratedW : step == Process.ASSEMBLE ? 1500 : 3000;
        int teeth = 0;
        double rev = 0, stroke = 0;
        switch (p) {
            case TURNING, BORING -> { teeth = 1; rev = 8; }
            case MILLING -> { teeth = 4; rev = 10; }
            case GEAR_CUTTING -> { teeth = 12; rev = 2; }
            case DRILLING, THREADING, RIVET_DRILL -> { teeth = 2; rev = 12; }
            case GRINDING, FINISHING -> rev = 30;
            case SAWING -> {
                if (head == Head.COLD_SAW) { teeth = 120; rev = 1; }
                else if (head == Head.HACKSAW) { teeth = 60; rev = 0.5; }
                else { teeth = 200; rev = 0.5; }
            }
            case STROKE -> stroke = head == Head.BROACHING ? 0.2 : head == Head.SHAPER || head == Head.SLOTTER ? 1 : 0.33;
            case PRESS -> stroke = 0.33;
            case RIVET_SQUEEZE -> stroke = 0.5;
            case COMPRESSOR, PUMP -> { teeth = 2; rev = 24; }
            case FAN -> { teeth = 6; rev = 16; }
            default -> { }
        }
        return new Hum(p, power, teeth, rev, stroke, working ? Math.clamp(share, 0.2, 1) : 0, working);
    }

    /** A generator or electric motor on a line shaft: its speed (rpm) and power (W). */
    public static Hum electricMachine(double rpm, double powerW, double ratedW) {
        boolean running = rpm > 5;
        return new Hum(ProcessNoise.Process.HUM, Math.max(ratedW, 500), 0, rpm / 60, 0, ratedW > 0 ? powerW / ratedW : 0, running);
    }

    /** A steam engine: two double-acting cylinders exhaust four times a turn. */
    public static Hum steamEngine(double rpm, double powerW, double ratedW) {
        boolean running = rpm > 5;
        return new Hum(ProcessNoise.Process.STEAM, Math.max(ratedW, 500), 0, rpm / 60, 0, running ? Math.max(0.3, ratedW > 0 ? powerW / ratedW : 0.5) : 0, running);
    }

    /** A treadle and its belt. */
    public static Hum treadle(double rpm, boolean pedalled) {
        return new Hum(ProcessNoise.Process.BELT, 150, 0, rpm / 60, 0, pedalled ? 0.6 : 0, pedalled && rpm > 5);
    }

    /** A plant's unit doing its work: a process (burner, pump, compressor, fan, curing...) at a power, working or idle. */
    public static Hum plant(ProcessNoise.Process process, double powerW, double load, boolean running) {
        int teeth = process == ProcessNoise.Process.FAN ? 6 : process == ProcessNoise.Process.PUMP || process == ProcessNoise.Process.COMPRESSOR ? 2 : 0;
        double rev = process == ProcessNoise.Process.FAN ? 16 : process == ProcessNoise.Process.PUMP || process == ProcessNoise.Process.COMPRESSOR ? 24 : 0;
        return new Hum(process, powerW, teeth, rev, 0, running ? load : 0, running);
    }

    /** A drilling rig turning its string (120 rpm, a three-cone bit) on its power. */
    public static Hum drillingRig(double powerW, boolean drilling) {
        return new Hum(ProcessNoise.Process.DRILLING, powerW, 3, 2, 0, drilling ? 0.8 : 0, drilling);
    }

    /** A pump jack: a stroke every 6 s or so while it pumps. */
    public static Hum pumpJack(double powerW, double strokesPerMinute, boolean pumping) {
        return new Hum(ProcessNoise.Process.STROKE, powerW, 0, 0, strokesPerMinute / 60, pumping ? 0.5 : 0, pumping);
    }

    /** The short sounds of the workshop and the plant. */
    public enum Event {
        /** A powered door running (the argument: its travel time, s): the motor, the chain, and its stop at the end. */
        DOOR,
        /** A welding torch's bead in an assembly room. */
        WELD,
        /** A rivet hammered in, or a part bolted (impact wrench). */
        RIVET,
        /** The room's crane hoisting. */
        HOIST,
        /** Oil struck: the well blows. */
        GUSHER,
        /** A job finished: a buzzer. */
        DONE
    }

    /** An event's samples (about full scale) and its sound power level, dB. */
    public record Sound(float[] samples, double levelDb) {}

    /** An event made (a seed making each different); argument per event (a door's travel time, s). */
    public static Sound event(Event e, double argument, long seed) {
        return switch (e) {
            case DOOR -> door(Math.clamp(argument, 0.5, 12), seed);
            case WELD -> new Sound(loop(new ProcessNoise(new ProcessNoise.Spec(ProcessNoise.Process.WELD_MIG, 6000, 0, 0, 0, seed)), new ProcessNoise.State(1, true), 0.7), 92);
            case RIVET -> {
                ProcessNoise gun = new ProcessNoise(new ProcessNoise.Spec(ProcessNoise.Process.RIVET_IMPACT, 600, 0, 0, 0, seed));
                yield new Sound(loop(gun, new ProcessNoise.State(1, true), 0.45), 104);
            }
            case HOIST -> {
                MotorNoise hoist = new MotorNoise(MotorNoise.Spec.small(4000, seed));
                hoist.set(new MotorNoise.State(25, 0.7, 0));
                float[] s = Voice.render(hoist, 1.0);
                fade(s, 0.15);
                yield new Sound(s, 84);
            }
            case GUSHER -> {
                GasJetNoise jet = new GasJetNoise(new GasJetNoise.Spec(2.0, 3e6, 0.12, 330, seed));
                jet.release();
                float[] s = Voice.render(jet, 4);
                fade(s, 0.3);
                yield new Sound(s, 128);
            }
            case DONE -> new Sound(buzzer(seed), 86);
        };
    }

    /** A door: its motor's whine and the chain's clatter for the travel, a clunk at the end. */
    private static Sound door(double seconds, long seed) {
        MotorNoise motor = new MotorNoise(MotorNoise.Spec.small(1500, seed));
        motor.set(new MotorNoise.State(24, 0.8, 0));
        float[] run = Voice.render(motor, seconds);
        dev.mcmod.aerocore.api.acoustics.Noise noise = new dev.mcmod.aerocore.api.acoustics.Noise(seed + 1);
        ImpactNoise.Spec link = ImpactNoise.Spec.steelPart(0.05);
        float[] clink = ImpactNoise.render(link, 0.3, seed + 2);
        for (int at = 0; at < run.length; at += (int) (Voice.RATE * (0.05 + 0.04 * noise.uniform()))) {
            for (int i = 0; i < clink.length && at + i < run.length; i++) { run[at + i] += 0.3f * clink[i]; }
        }
        fade(run, 0.1);
        float[] stop = ImpactNoise.render(ImpactNoise.Spec.steelPart(0.8), 0.8, seed + 3);
        float[] out = new float[run.length + stop.length];
        System.arraycopy(run, 0, out, 0, run.length);
        for (int i = 0; i < stop.length; i++) { out[run.length - Math.min(run.length, Voice.RATE / 20) + i] += stop[i]; }
        return new Sound(out, 96);
    }

    /** An industrial buzzer: 0.6 s of a rough 400 Hz tone. */
    private static float[] buzzer(long seed) {
        float[] s = new float[(int) (0.6 * Voice.RATE)];
        for (int i = 0; i < s.length; i++) {
            double t = (double) i / Voice.RATE, p = (t * 400) % 1;
            s[i] = (float) (0.5 * (p < 0.5 ? 1 : -1) * (0.7 + 0.3 * Math.sin(2 * Math.PI * 50 * t)));
        }
        fade(s, 0.02);
        return s;
    }

    /** A model's sound for a while at a state, faded in and out. */
    private static float[] loop(ProcessNoise model, ProcessNoise.State state, double seconds) {
        model.set(state);
        float[] s = Voice.render(model, seconds);
        fade(s, 0.05);
        return s;
    }

    private static void fade(float[] s, double seconds) {
        int n = Math.min(s.length / 2, (int) (seconds * Voice.RATE));
        for (int i = 0; i < n; i++) {
            float g = (float) i / n;
            s[i] *= g;
            s[s.length - 1 - i] *= g;
        }
    }

    /**
     * A seamless loop of a hum (seconds long): the model rendered a little longer, its end crossfaded into its start,
     * scaled to an RMS of 0.25 (the level is set by the playback gain).
     */
    public static float[] bake(Hum hum, double seconds, long seed) {
        ProcessNoise model = new ProcessNoise(hum.spec(seed));
        model.set(hum.state());
        Voice.render(model, 0.3);   // settle its smoothing
        int length = (int) (seconds * Voice.RATE), fade = Math.max(1, (int) (0.2 * Voice.RATE));
        float[] x = Voice.render(model, (double) (length + fade) / Voice.RATE);
        float[] y = new float[length];
        for (int i = 0; i < length; i++) {
            if (i < fade) {
                double g = (double) i / fade;
                y[i] = (float) (x[i] * g + x[length + i] * (1 - g));
            } else {
                y[i] = x[i];
            }
        }
        double sum = 0;
        for (float v : y) { sum += v * v; }
        double rms = Math.sqrt(sum / Math.max(1, y.length));
        if (rms > 1e-6) {
            float g = (float) Math.min(20, 0.25 / rms);
            for (int i = 0; i < y.length; i++) { y[i] *= g; }
        }
        return y;
    }
}
