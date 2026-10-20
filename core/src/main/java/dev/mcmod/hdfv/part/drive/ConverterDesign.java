package dev.mcmod.hdfv.part.drive;

import dev.mcmod.aerocore.api.drive.TorqueConverter;
import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;
import dev.mcmod.hdfv.part.engine.EngineComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A torque converter (液力變扭器): its diameter sets its capacity (AeroCore: K at stall as D^-2.5 from a 0.26 m
 * passenger-car converter's 12.29 rad/s per sqrt(N m)), its stall torque ratio how much it multiplies (cars 1.8-2.2),
 * and its lock-up clutch's capacity.
 */
public record ConverterDesign(String name, double diameterM, double stallTorqueRatio, double lockupNm) implements EngineComponent {
    public static final String TYPE = "hdfv:converter";

    public ConverterDesign { Objects.requireNonNull(name, "name"); }

    @Override public String type() { return TYPE; }

    public TorqueConverter converter() { return new TorqueConverter(stallTorqueRatio, TorqueConverter.stallK(diameterM)); }

    /** The pump's and turbine's moments of inertia with their oil, kg m^2: 0.10 and 0.03 for a 0.26 m converter, as D^5 (rule). */
    public double pumpInertiaKgM2() { return 0.10 * Math.pow(diameterM / TorqueConverter.REFERENCE_DIAMETER, 5); }
    public double turbineInertiaKgM2() { return 0.03 * Math.pow(diameterM / TorqueConverter.REFERENCE_DIAMETER, 5); }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        if (!(diameterM > 0.12 && diameterM < 0.6)) { issues.add(DesignIssue.invalid("diameterM", "The converter must be 120 to 600 mm across")); }
        if (!(stallTorqueRatio >= 1 && stallTorqueRatio < 5)) { issues.add(DesignIssue.invalid("stallTorqueRatio", "The stall torque ratio must be 1 to 5")); }
        Rules.nonnegative(issues, "lockupNm", lockupNm);
        if (!issues.isEmpty()) { return issues; }
        DesignIssue.range(issues, "stallTorqueRatio", stallTorqueRatio, 1.6, 2.8, "converters' stall torque ratios (cars 1.8-2.2, heavy duty to 2.8)");
        return issues;
    }

    @Override public double massKg() { return 0; }

    @Override public Map<String, Object> parameters() { return Fields.object("diameterM", diameterM, "stallTorqueRatio", stallTorqueRatio, "lockupNm", lockupNm); }

    public static ConverterDesign read(String name, Fields f) {
        return new ConverterDesign(name, f.number("diameterM", 0.26), f.number("stallTorqueRatio", 2.0), f.number("lockupNm", 600));
    }
}
