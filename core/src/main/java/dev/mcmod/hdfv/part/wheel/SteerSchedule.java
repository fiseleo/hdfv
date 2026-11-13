package dev.mcmod.hdfv.part.wheel;

import dev.mcmod.aerocore.api.CollisionSphere;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.MassProperties;
import dev.mcmod.aerocore.api.vehicle.Part;
import dev.mcmod.aerocore.api.vehicle.PartAfter;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.PartStep;
import dev.mcmod.aerocore.api.vehicle.part.AxlePart;

import java.util.List;

/**
 * 0.15: an axle steered by a share of the driver's steering that changes with speed (a rule: AxleLayoutDesign's
 * steerRatio below the crossover, steerRatioHigh above it, blended over 6 m/s about it): a rear axle turning against the
 * front ones at walking pace (a tighter turn) and with them at speed (steadier), a truck's second front axle turning
 * less than the first. Wraps AeroCore's AxlePart, setting its "steer" input (PartStep.override) in its probe and loads.
 */
public final class SteerSchedule implements Part {
    /** The speed band the ratio blends over, m/s. */
    public static final double BLEND_MPS = 6;

    private final PartDesign design;
    private final AxlePart inner;
    private final double low, high, crossoverMps;

    public SteerSchedule(PartDesign design, AxlePart inner, double low, double high, double crossoverMps) {
        this.design = design; this.inner = inner; this.low = low; this.high = high; this.crossoverMps = crossoverMps;
    }

    /** The axle inside. */
    public AxlePart inner() { return inner; }

    /** An axle part, wrapped or not (null for other parts). */
    public static AxlePart axleOf(Part p) { return p instanceof AxlePart a ? a : p instanceof SteerSchedule s ? s.inner : null; }

    /** The share of the steering at a speed (m/s). */
    public double ratio(double speedMps) {
        if (!(crossoverMps > 0)) { return low; }
        double t = Math.clamp((speedMps - (crossoverMps - BLEND_MPS / 2)) / BLEND_MPS, 0, 1);
        return low + t * (high - low);
    }

    private void steer(PartStep step) {
        double speed = Math.abs(step.state().velocity().dot(step.axis(Vec3d.X)));
        step.override("steer", Math.clamp(step.input("steer", 0) * ratio(speed), -1, 1));
    }

    @Override public PartDesign design() { return design; }
    @Override public MassProperties mass() { return inner.mass(); }
    @Override public List<CollisionSphere> collision() { return inner.collision(); }
    @Override public List<DesignIssue> issues() { return inner.issues(); }
    @Override public List<Rotor> rotors() { return inner.rotors(); }
    @Override public List<Link> links() { return inner.links(); }
    @Override public List<String> inputs() { return inner.inputs(); }
    @Override public int internalStates() { return inner.internalStates(); }
    @Override public double[] initialInternals() { return inner.initialInternals(); }
    @Override public int shownValues() { return inner.shownValues(); }
    /** 0.25: the axle's conditions (tyres, brakes, suspension), read and worn by the axle inside. */
    @Override public List<String> conditions() { return inner.conditions(); }
    @Override public boolean probes() { return inner.probes(); }
    @Override public void probe(PartStep step) { steer(step); inner.probe(step); }
    @Override public void loads(PartStep step) { steer(step); inner.loads(step); }
    @Override public void after(PartAfter step) { inner.after(step); }
    @Override public void warmUp() { inner.warmUp(); }
}
