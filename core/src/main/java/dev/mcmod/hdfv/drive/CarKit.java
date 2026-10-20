package dev.mcmod.hdfv.drive;

import dev.mcmod.aerocore.api.vehicle.Binding;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.VehicleBlueprint;
import dev.mcmod.aerocore.api.vehicle.part.TransferPart;
import dev.mcmod.hdfv.part.MotorDesign;
import dev.mcmod.hdfv.part.drive.GearboxDesign;
import dev.mcmod.hdfv.part.drive.Gearboxes;
import dev.mcmod.hdfv.part.drive.TransferCaseDesign;
import dev.mcmod.hdfv.part.engine.PistonEngineDesign;
import dev.mcmod.hdfv.part.engine.ShaftTurbineDesign;
import dev.mcmod.hdfv.part.wheel.AxleDesign;

import java.util.List;

/**
 * What a vehicle has for a driver's keys and dashboard (0.14), read from its blueprint: the bindings say which channels
 * move what (AutoWiring's names), the designs what the gearbox, transfer case and engine are.
 *
 * <ul>
 * <li>car: it has an axle (the car keys, not the flight keys, are then the driver's); steers, handbrake: something
 *     reads "steer", "handbrake"; (0.15) rideHeight: an axle reads "ride_height" (air springs with a levelling valve).</li>
 * <li>gearbox: the kind of the box whose lever or selector is bound ("gear" or "selector"), null when none; its
 *     forward gears; autoClutch: a manual's automatic clutch is bound.</li>
 * <li>transfer: the kind of a transfer case with something to switch (a part-time case's "mode", a full-time or
 *     on-demand case's "range", a full-time case's centre lock), null otherwise; lowRange: it has a low range;
 *     centreLock: the channel its centre lock is bound to (null: none).</li>
 * <li>diffLocks: how many steps of "diff_lock" lock something (0 none, 1 rear, 2 rear then front); lock: an axle
 *     bound to the old shared "lock" channel (before 0.14).</li>
 * <li>engine: the id of the part whose speed the tachometer shows (a piston engine, a motor or a turbine), null when
 *     none; its idle, the speed where its red zone starts and the tachometer's full scale, rpm; piston: whether it
 *     shows whether it runs (PistonEngineDesign.SHOWN_RUNNING).</li>
 * <li>topSpeedMps: the speed at the engine's red line in the highest gear that is not an overdrive (ratio 0.95 or more; a CVT at
 *     1:1) on the driven wheels: the speedometer's reach (overdrives are not reached at the red line), 0 when unknown.</li>
 * <li>0.18: fifthWheel: it has a fifth wheel (the coupling key does something).</li>
 * </ul>
 */
public record CarKit(boolean car, boolean steers, boolean handbrake, String gearboxId, Gearboxes.Kind gearbox, int forwardGears, boolean autoClutch,
                     String transferId, TransferPart.Kind transfer, boolean lowRange, String centreLock, int diffLocks, boolean lock,
                     String engine, boolean piston, double idleRpm, double redlineRpm, double fullScaleRpm, double topSpeedMps, boolean rideHeight,
                     boolean fifthWheel) {
    public static final CarKit NONE = new CarKit(false, false, false, null, null, 0, false, null, null, false, null, 0, false, null, false, 0, 0, 0, 0, false, false);

    /** A kit without a fifth wheel (before 0.18). */
    public CarKit(boolean car, boolean steers, boolean handbrake, String gearboxId, Gearboxes.Kind gearbox, int forwardGears, boolean autoClutch,
                  String transferId, TransferPart.Kind transfer, boolean lowRange, String centreLock, int diffLocks, boolean lock,
                  String engine, boolean piston, double idleRpm, double redlineRpm, double fullScaleRpm, double topSpeedMps, boolean rideHeight) {
        this(car, steers, handbrake, gearboxId, gearbox, forwardGears, autoClutch, transferId, transfer, lowRange, centreLock, diffLocks, lock, engine, piston,
                idleRpm, redlineRpm, fullScaleRpm, topSpeedMps, rideHeight, false);
    }

    /** A kit without air suspension (before 0.15). */
    public CarKit(boolean car, boolean steers, boolean handbrake, String gearboxId, Gearboxes.Kind gearbox, int forwardGears, boolean autoClutch,
                  String transferId, TransferPart.Kind transfer, boolean lowRange, String centreLock, int diffLocks, boolean lock,
                  String engine, boolean piston, double idleRpm, double redlineRpm, double fullScaleRpm, double topSpeedMps) {
        this(car, steers, handbrake, gearboxId, gearbox, forwardGears, autoClutch, transferId, transfer, lowRange, centreLock, diffLocks, lock, engine, piston,
                idleRpm, redlineRpm, fullScaleRpm, topSpeedMps, false);
    }

    public static CarKit of(VehicleBlueprint blueprint) {
        if (blueprint == null) { return NONE; }
        boolean car = false, steers = false, handbrake = false, autoClutch = false, lowRange = false, lock = false, piston = false, ride = false, fifth = false;
        String gearboxId = null, transferId = null, centreLock = null, engine = null;
        Gearboxes.Kind gearbox = null;
        TransferPart.Kind transfer = null;
        int gears = 0, diffLocks = 0;
        double idle = 0, redline = 0, topRatio = 0, finalRatio = 0, tyreRadius = 0;
        for (VehicleBlueprint.Entry e : blueprint.parts()) {
            PartDesign d = e.design();
            List<Binding> b = e.bindings();
            if (bound(b, "steer")) { steers = true; }
            if (bound(b, "handbrake")) { handbrake = true; }
            if (bound(b, "ride_height")) { ride = true; }
            if (d instanceof dev.mcmod.hdfv.part.truck.FifthWheelDesign) { fifth = true; }
            if (d instanceof AxleDesign a) {
                car = true;
                for (Binding x : b) {
                    if (!x.role().equals("lock")) { continue; }
                    if (x.channel().equals("lock")) { lock = true; diffLocks = Math.max(diffLocks, 1); }
                    else if (x.channel().equals("diff_lock") && x.scale() > 0) {
                        diffLocks = Math.max(diffLocks, Math.max(1, (int) Math.ceil((0.5 - x.offset()) / x.scale() - 1e-9)));
                    }
                }
                if (a.differential().driven() && tyreRadius == 0) { tyreRadius = a.tyre().tyre().radiusM(); finalRatio = a.differential().finalRatio(); }
            } else if (d instanceof GearboxDesign g && gearbox == null && (boundRole(b, "gear") || boundRole(b, "selector"))) {
                gearboxId = e.id(); gearbox = g.kind(); gears = g.forwardGears();
                double[] r = g.ratios();
                topRatio = g.kind() == Gearboxes.Kind.CVT ? 1 : java.util.Arrays.stream(r).filter(x -> x >= 0.95).min().orElse(r[0]);
                autoClutch = g.kind() == Gearboxes.Kind.MANUAL && boundRole(b, "auto_clutch");
            } else if (d instanceof TransferCaseDesign t && transfer == null) {
                String cl = channelOf(b, "lock");
                if (boundRole(b, "mode") || boundRole(b, "range") || cl != null) {
                    transferId = e.id(); transfer = t.kind(); lowRange = t.lowRatio() > 1;
                    centreLock = t.kind() == TransferPart.Kind.FULL_TIME ? cl : null;
                }
            } else if (engine == null) {
                if (d instanceof PistonEngineDesign p) { engine = e.id(); piston = true; idle = p.ignition().idleRpm(); redline = p.ignition().revLimitRpm(); }
                else if (d instanceof MotorDesign m) { engine = e.id(); redline = m.maxSpeedRpm(); }
                else if (d instanceof ShaftTurbineDesign s) { engine = e.id(); redline = s.outputRpm(); }
            }
        }
        double fullScale = redline > 0 ? Math.ceil((redline + 1) / 1000) * 1000 : 0;
        double top = redline > 0 && topRatio > 0 && finalRatio > 0 && tyreRadius > 0 ? redline * Math.PI / 30 / (topRatio * finalRatio) * tyreRadius : 0;
        if (!car) { return NONE; }
        return new CarKit(true, steers, handbrake, gearboxId, gearbox, gears, autoClutch, transferId, transfer, lowRange, centreLock, diffLocks, lock,
                engine, piston, idle, redline, fullScale, top, ride, fifth);
    }

    /** Whether the gearbox has a selector (an automatic, dual-clutch box or CVT) rather than a lever. */
    public boolean selector() { return gearbox != null && gearbox != Gearboxes.Kind.MANUAL; }
    /** Whether its selector has M (not a CVT). */
    public boolean manualMode() { return gearbox == Gearboxes.Kind.AUTOMATIC || gearbox == Gearboxes.Kind.DUAL_CLUTCH; }

    /** The drive modes G steps through (DriveModes), empty when there is nothing to switch. */
    public List<DriveMode> modes() {
        if (transfer == null) { return List.of(); }
        return switch (transfer) {
            case PART_TIME -> lowRange ? List.of(DriveMode.TWO_HIGH, DriveMode.FOUR_HIGH, DriveMode.FOUR_LOW) : List.of(DriveMode.TWO_HIGH, DriveMode.FOUR_HIGH);
            case FULL_TIME -> centreLock != null
                    ? (lowRange ? List.of(DriveMode.HIGH, DriveMode.HIGH_LOCKED, DriveMode.LOW_LOCKED) : List.of(DriveMode.HIGH, DriveMode.HIGH_LOCKED))
                    : (lowRange ? List.of(DriveMode.HIGH, DriveMode.LOW) : List.of());
            case ON_DEMAND -> lowRange ? List.of(DriveMode.AUTO, DriveMode.LOW) : List.of();
        };
    }

    /** The speed dial's full scale, km/h: the top speed (with a twentieth more) rounded up to 120, 160, 200, 260, 320 or 400. */
    public int speedScaleKmh() {
        double top = topSpeedMps * 3.6 * 1.05;
        if (!(top > 0)) { return 200; }
        for (int s : new int[]{120, 160, 200, 260, 320}) { if (top <= s) { return s; } }
        return 400;
    }

    private static boolean bound(List<Binding> b, String channel) {
        for (Binding x : b) { if (x.channel().equals(channel)) { return true; } }
        return false;
    }

    private static boolean boundRole(List<Binding> b, String role) { return channelOf(b, role) != null; }

    private static String channelOf(List<Binding> b, String role) {
        for (Binding x : b) { if (x.role().equals(role)) { return x.channel(); } }
        return null;
    }

}
