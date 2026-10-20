package dev.mcmod.hdfv.drive;

import dev.mcmod.aerocore.api.vehicle.part.TransferPart;
import dev.mcmod.hdfv.part.drive.Gearboxes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * What a car's dashboard shows (0.14), from its kit and what the client knows: the speed and engine dials' scales,
 * the gear box in the middle (the selector's letters with the one in use, the gear) and the warning lamps. Drawing is
 * the client's (DashboardHud); this decides what is on it.
 */
public final class Dashboard {
    private Dashboard() {}

    /** Under this share of its tanks the fuel lamp comes on. */
    public static final double LOW_FUEL = 0.1;

    /** A gearbox's shown values (TransmissionPart's three, then ShiftRules' selector or lever). */
    public record Box(double gear, double state, double selector) {}
    /** A transfer case's shown values: its range (1 high, 2 low) and its mode, lock or coupling. */
    public record Transfer(double range, double second) {}

    /** What the client reads for a dashboard this frame (NaN: unknown). */
    public record Readings(double speedMps, double rpm, Box box, Transfer transfer, Map<String, Double> controls, double fuelShare, boolean engineRunning) {}

    /** 0.19: TURN_LEFT, TURN_RIGHT (the indicators' arrows, first in the row; they flash with the lamps, LampRules). */
    public enum Lamp { HANDBRAKE, MODE, CENTRE_LOCK, REAR_LOCK, FRONT_LOCK, AUTO_CLUTCH, LOW_FUEL, ENGINE, RIDE_HEIGHT, TRAILER, TURN_LEFT, TURN_RIGHT }

    /** A lamp lit (blinking while the car has not yet done what was asked, as a range change waiting for it to stop), and its label. */
    public record Lit(Lamp lamp, String label, boolean blinking) {}

    /**
     * The gear box: the selector's letters ("PRNDM", "PRND" for a CVT, "" for a manual), which one is in use (-1 none),
     * the big text (P, R, N, D3, M2; a manual's R, N, 1..n), whether it is changing (dim) or grinding (red).
     */
    public record Gear(String letters, int lit, String text, boolean changing, boolean grinding) {}

    public record Face(int speedScale, String speedUnit, double speed, double rpmScale, double redline, double rpm, Gear gear, List<Lit> lamps, double fuelShare) {}

    public static Face of(CarKit kit, Readings r, boolean mph) {
        int scale = kit.speedScaleKmh();
        double factor = 3.6;
        if (mph) { scale = (int) (Math.round(scale / 1.609344 / 10.0) * 10); factor = 3.6 / 1.609344; }
        return new Face(scale, mph ? "mph" : "km/h", Math.abs(r.speedMps()) * factor, kit.fullScaleRpm(), kit.redlineRpm(), Math.abs(r.rpm()),
                gear(kit, r), lamps(kit, r), r.fuelShare());
    }

    public static Gear gear(CarKit kit, Readings r) {
        if (kit.gearbox() == null || r.box() == null) { return new Gear("", -1, "", false, false); }
        boolean changing = r.box().state() > 0.5 && r.box().state() < 1.5, grinding = r.box().state() > 1.5;
        if (kit.gearbox() == Gearboxes.Kind.MANUAL) {
            return new Gear("", -1, DriverInputs.gearName((int) Math.round(r.box().selector())), changing || Math.round(r.box().gear()) == 0 && Math.round(r.box().selector()) != 0, grinding);
        }
        int sel = (int) Math.round(r.box().selector());
        String letters = kit.manualMode() ? "PRNDM" : "PRND";
        int lit = Math.clamp(sel + 2, 0, letters.length() - 1);
        int engaged = (int) Math.round(r.box().gear());
        String text = switch (sel) {
            case Gearboxes.PARK -> "P";
            case Gearboxes.REVERSE -> "R";
            case Gearboxes.NEUTRAL -> "N";
            case Gearboxes.MANUAL_MODE -> "M" + Math.max(engaged, 1);
            default -> kit.gearbox() == Gearboxes.Kind.CVT || engaged < 1 ? "D" : "D" + engaged;
        };
        return new Gear(letters, lit, text, changing, grinding);
    }

    public static List<Lit> lamps(CarKit kit, Readings r) {
        List<Lit> out = new ArrayList<>();
        Map<String, Double> c = r.controls();
        // 0.19: the indicators (-1 left, 1 right, 2 hazards)
        long indicators = Math.round(c.getOrDefault("indicators", 0.0));
        if (indicators == -1 || indicators == 2) { out.add(new Lit(Lamp.TURN_LEFT, "", false)); }
        if (indicators == 1 || indicators == 2) { out.add(new Lit(Lamp.TURN_RIGHT, "", false)); }
        if (kit.handbrake() && c.getOrDefault("handbrake", 0.0) > 0.5) { out.add(new Lit(Lamp.HANDBRAKE, "P", false)); }
        List<DriveMode> modes = kit.modes();
        if (!modes.isEmpty()) {
            DriveMode asked = asked(kit, c);
            boolean low = r.transfer() != null && r.transfer().range() > 1.5;
            out.add(new Lit(Lamp.MODE, asked.label, r.transfer() != null && asked.low() != low));
            if (asked.centreLocked) { out.add(new Lit(Lamp.CENTRE_LOCK, "C", false)); }
        }
        int locks = (int) Math.round(c.getOrDefault("diff_lock", 0.0));
        if (kit.diffLocks() > 0 && locks >= 1) { out.add(new Lit(Lamp.REAR_LOCK, kit.diffLocks() > 1 ? "R" : "L", false)); }
        if (kit.diffLocks() > 1 && locks >= 2) { out.add(new Lit(Lamp.FRONT_LOCK, "F", false)); }
        if (kit.autoClutch() && c.getOrDefault("auto_clutch", 0.0) > 0.5) { out.add(new Lit(Lamp.AUTO_CLUTCH, "A", false)); }
        // 0.15: an air-sprung car off its design height
        double ride = c.getOrDefault("ride_height", 0.0);
        if (kit.rideHeight() && Math.abs(ride) > 0.25) { out.add(new Lit(Lamp.RIDE_HEIGHT, ride > 0 ? "HI" : "LO", false)); }
        // 0.18: a tractor's trailer: coupled, or blinking while it is let down on its legs
        double coupling = c.getOrDefault("coupling", 0.0);
        if (kit.fifthWheel() && coupling > 0.5) { out.add(new Lit(Lamp.TRAILER, "TRL", coupling > 1.5)); }
        if (r.fuelShare() >= 0 && r.fuelShare() < LOW_FUEL) { out.add(new Lit(Lamp.LOW_FUEL, "", false)); }
        if (kit.piston() && !r.engineRunning()) { out.add(new Lit(Lamp.ENGINE, "", false)); }
        return out;
    }

    /** The drive mode the driver's channels ask for (the first when they match none). */
    static DriveMode asked(CarKit kit, Map<String, Double> c) {
        List<DriveMode> modes = kit.modes();
        for (DriveMode m : modes) {
            boolean same = kit.transfer() == TransferPart.Kind.PART_TIME ? m.mode == Math.round(c.getOrDefault("mode", 0.0))
                    : m.range == Math.round(c.getOrDefault("range", 0.0))
                    && (kit.centreLock() == null || m.centreLocked == c.getOrDefault(kit.centreLock(), 0.0) > 0.5);
            if (same) { return m; }
        }
        return modes.getFirst();
    }
}
