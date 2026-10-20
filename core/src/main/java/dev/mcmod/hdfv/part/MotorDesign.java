package dev.mcmod.hdfv.part;

import dev.mcmod.aerocore.api.ElectricMotor;
import dev.mcmod.aerocore.api.InertiaTensor;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.vehicle.DesignException;
import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;
import dev.mcmod.aerocore.api.vehicle.MassProperties;
import dev.mcmod.aerocore.api.vehicle.Part;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.part.MotorPart;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * An electric motor whose rotor turns about the part's +X axis: the textbook torque-speed envelope of a field-weakened
 * machine -- constant torque up to the base speed where torque times speed reaches the rated power, constant power
 * above it, nothing beyond the top speed -- times the "throttle" input (-1..1; negative drives in reverse, against the
 * rotation it brakes with the same envelope). Electrical power is shaft power over efficiency when driving and times
 * it when braking (regeneration); no battery, heating or iron losses. Its housing mass is given; the housing's
 * inertia takes it as a cube of 4000 kg/m^3 (a rough overall density for motors, an estimate).
 * Specific power above 5.2 kW/kg (Siemens SP260D, 2015, an aircraft motor of record) is reported as an extrapolation.
 */
public record MotorDesign(String name, double maxTorqueNm, double maxPowerW, double maxSpeedRpm, double rotorInertiaKgM2,
                          double efficiency, double massKg) implements PartDesign {
    public static final String TYPE = "hdfv:electric_motor";
    /** Best published specific power of a production-intent electric aircraft motor (Siemens SP260D, 2015), W/kg. */
    public static final double RECORD_SPECIFIC_POWER = 5200;
    private static final double HOUSING_DENSITY = 4000;

    public MotorDesign { Objects.requireNonNull(name, "name"); }

    @Override public String type() { return TYPE; }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        positive(issues, "maxTorqueNm", maxTorqueNm);
        positive(issues, "maxPowerW", maxPowerW);
        positive(issues, "maxSpeedRpm", maxSpeedRpm);
        positive(issues, "rotorInertiaKgM2", rotorInertiaKgM2);
        positive(issues, "massKg", massKg);
        if (!(efficiency > 0 && efficiency <= 1)) { issues.add(DesignIssue.invalid("efficiency", "Efficiency must be in (0, 1]")); }
        if (massKg > 0 && maxPowerW > 0) { DesignIssue.range(issues, "specificPowerWPerKg", maxPowerW / massKg, 0, RECORD_SPECIFIC_POWER, "Siemens SP260D 5.2 kW/kg"); }
        return issues;
    }

    static void positive(List<DesignIssue> issues, String name, double value) {
        if (!(value > 0) || !Double.isFinite(value)) { issues.add(DesignIssue.invalid(name, name + " must be positive")); }
    }

    @Override public Part compile() {
        List<DesignIssue> issues = check();
        DesignException.throwIfInvalid(issues);
        double side = Math.cbrt(massKg / HOUSING_DENSITY);
        MassProperties mass = MassProperties.of(massKg, Vec3d.ZERO, InertiaTensor.box(massKg, new Vec3d(side, side, side)));
        return new MotorPart(this, motor(), rotorInertiaKgM2, efficiency, mass,
                issues.stream().filter(i -> i.kind() == DesignIssue.Kind.EXTRAPOLATION).toList());
    }

    @Override public Map<String, Object> parameters() {
        return Fields.object("maxTorqueNm", maxTorqueNm, "maxPowerW", maxPowerW, "maxSpeedRpm", maxSpeedRpm,
                "rotorInertiaKgM2", rotorInertiaKgM2, "efficiency", efficiency, "massKg", massKg);
    }

    public static PartDesign read(Fields f) {
        return new MotorDesign(f.string("name", "motor"), f.number("maxTorqueNm"), f.number("maxPowerW"), f.number("maxSpeedRpm"),
                f.number("rotorInertiaKgM2"), f.number("efficiency", 0.9), f.number("massKg"));
    }

    /** The torque-speed envelope. */
    public ElectricMotor motor() { return new ElectricMotor(maxTorqueNm, maxPowerW, maxSpeedRpm); }

    /** Torque on the rotor (N m) for a throttle -1..1 at rotor speed (rad/s). */
    public double torque(double throttle, double speed) { return motor().torque(throttle, speed); }
}
