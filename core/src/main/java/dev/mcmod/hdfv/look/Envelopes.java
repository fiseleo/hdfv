package dev.mcmod.hdfv.look;

import dev.mcmod.aerocore.api.Orientation;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.part.TransferPart;
import dev.mcmod.hdfv.part.MotorDesign;
import dev.mcmod.hdfv.part.drive.GearboxDesign;
import dev.mcmod.hdfv.part.drive.Gearboxes;
import dev.mcmod.hdfv.part.drive.TransferCaseDesign;
import dev.mcmod.hdfv.part.engine.JetEngineDesign;
import dev.mcmod.hdfv.part.engine.PistonEngineDesign;
import dev.mcmod.hdfv.part.engine.PulsejetDesign;
import dev.mcmod.hdfv.part.engine.RamjetDesign;
import dev.mcmod.hdfv.part.engine.ScramjetDesign;
import dev.mcmod.hdfv.part.engine.ShaftTurbineDesign;
import dev.mcmod.hdfv.part.engine.SolidRocketDesign;
import dev.mcmod.hdfv.part.engine.TurboRamjetDesign;
import dev.mcmod.hdfv.part.engine.TurbofanDesign;

import java.util.ArrayList;
import java.util.List;

/**
 * 0.14: the envelopes of the parts that go in a body (Envelope), each from the numbers its model is drawn with: a
 * piston engine's from PistonEngineLayout; a gearbox's bell, case and tail (GearboxModel's proportions; a transverse
 * box turned); a transfer case's case and lever; an electric motor's finned frame (PartLooks); a gas turbine's or a jet's
 * a cylinder of its length and diameter, with room for its accessories. Null for parts with none.
 */
public final class Envelopes {
    private Envelopes() {}

    /** A transverse gearbox's turn: its input faces -z (GearboxModel). */
    public static final Orientation GEARBOX_TURN = Orientation.rotationVector(Vec3d.Y.scale(-Math.PI / 2));

    public static Envelope of(PartDesign d) {
        return switch (d) {
            case PistonEngineDesign e -> PistonEngineLayout.of(e).envelope();
            case GearboxDesign g -> gearbox(g);
            case TransferCaseDesign t -> transfer(t);
            case MotorDesign m -> motor(m.massKg());
            case JetEngineDesign j -> cylinder(j.length(), j.diameter(), 0.1);
            case TurbofanDesign t -> cylinder(t.length(), t.diameter(), 0.1);
            case ShaftTurbineDesign t -> cylinder(t.length(), t.diameter(), 0.25);
            case TurboRamjetDesign t -> cylinder(t.length(), t.diameter(), 0.1);
            case RamjetDesign r -> cylinder(r.length(), r.diameter(), 0);
            case ScramjetDesign sc -> cylinder(sc.length(), sc.width(), 0);
            case PulsejetDesign p -> cylinder(p.length(), p.diameter(), 0);
            case SolidRocketDesign r -> cylinder(r.length(), r.diameter(), 0);
            default -> null;
        };
    }

    /** A gearbox's scale (GearboxModel): the cube root of its rating over 300 N m. */
    public static double gearboxScale(GearboxDesign g) { return Math.cbrt(g.gearbox().scale()); }

    /** The bell's face (where an engine's flywheel face bolts) and the tail flange's, along +x before a transverse turn. */
    public static double gearboxFront(GearboxDesign g) { return 0.32 * gearboxScale(g); }
    public static double gearboxRear(GearboxDesign g) { return (g.kind() == Gearboxes.Kind.MANUAL ? -0.38 : -0.48) * gearboxScale(g); }

    public static Envelope gearbox(GearboxDesign g) {
        double s = gearboxScale(g), caseR = 0.12 * s;
        boolean converter = g.kind() == Gearboxes.Kind.AUTOMATIC || g.kind() == Gearboxes.Kind.CVT;
        double bell = (converter ? 0.19 : 0.17) * s * 1.08, front = gearboxFront(g), rear = gearboxRear(g) - 0.025 * s;
        List<Envelope.Box> boxes = new ArrayList<>();
        boxes.add(new Envelope.Box("bell", new Vec3d(0.1 * s, -bell, -bell), new Vec3d(front, bell, bell)));
        boxes.add(new Envelope.Box("case", new Vec3d(rear, -1.1 * caseR, -1.1 * caseR), new Vec3d(0.1 * s, 1.1 * caseR, 1.1 * caseR)));
        switch (g.kind()) {
            case MANUAL -> boxes.add(new Envelope.Box("lever", new Vec3d(-0.25 * s, caseR * 0.95, -0.04 * s), new Vec3d(-0.13 * s, caseR * 0.95 + 0.32 * s + 0.025, 0.04 * s)));
            case AUTOMATIC -> {
                boxes.add(new Envelope.Box("pan", new Vec3d(-0.3 * s, -caseR * 1.25, -caseR * 1.65), new Vec3d(0.3 * s, -caseR * 0.75, caseR * 1.65)));
                // the cooler lines along the case's sides up to the bell's face (/hdfvdev envelope)
                boxes.add(new Envelope.Box("cooler_lines", new Vec3d(0.05 * s - 0.006, -0.03, -caseR * 1.6 - 0.006), new Vec3d(0.3 * s + 0.006, 0.06, caseR * 1.6 + 0.006)));
            }
            case DUAL_CLUTCH -> boxes.add(new Envelope.Box("unit", new Vec3d(-0.15 * s, -0.05 * s, caseR * 0.85), new Vec3d(0.12 * s, 0.08 * s, caseR * 1.35)));
            // the pulleys' housings (the rear one reaches 0.18 s down: /hdfvdev envelope) and the pan
            case CVT -> boxes.add(new Envelope.Box("pulleys", new Vec3d(-0.32 * s, -0.185 * s, -caseR * 0.7), new Vec3d(0.11 * s, 0.18 * s, caseR * 0.7)));
        }
        Envelope e = new Envelope(boxes, front, rear);
        return g.gearbox().transverse() ? e.turned(GEARBOX_TURN) : e;
    }

    /** A transfer case's scale (GearboxModel): the cube root of its rating over 400 N m. */
    public static double transferScale(TransferCaseDesign t) { return Math.cbrt(t.scale()); }

    public static Envelope transfer(TransferCaseDesign t) {
        double s = transferScale(t);
        List<Envelope.Box> boxes = new ArrayList<>();
        boxes.add(new Envelope.Box("case", new Vec3d(-0.26 * s, -0.22 * s, -0.13 * s), new Vec3d(0.23 * s, 0.13 * s, 0.14 * s)));
        if (t.kind() == TransferPart.Kind.PART_TIME || t.lowRatio() > 1) {
            boxes.add(new Envelope.Box("lever", new Vec3d(-0.02, 0.12 * s, -0.025), new Vec3d(0.045 * s + 0.025, 0.46 * s + 0.025, 0.025)));
        }
        if (t.kind() == TransferPart.Kind.ON_DEMAND) { boxes.add(new Envelope.Box("unit", new Vec3d(-0.08 * s, 0.12 * s, -0.08 * s), new Vec3d(0.06 * s, 0.2 * s, 0.08 * s))); }
        return new Envelope(boxes, 0.15 * s, -0.26 * s);
    }

    /** An electric motor's frame radius (4000 kg/m^3 overall, three radii long), as PartLooks draws it. */
    public static double motorRadius(double massKg) { return Math.cbrt(Math.max(massKg, 1e-3) / 4000 / (Math.PI * 3)); }

    public static Envelope motor(double massKg) {
        double r = motorRadius(massKg);
        return new Envelope(List.of(new Envelope.Box("motor", new Vec3d(-1.65 * r, -1.12 * r, -1.12 * r), new Vec3d(2.1 * r, 1.12 * r, 1.12 * r))), Double.NaN, 2.1 * r);
    }

    /** A cylinder along x of a length and diameter about the origin, with a share of its diameter for accessories underneath. */
    static Envelope cylinder(double length, double diameter, double accessories) {
        double l = Math.max(length, 0.05), rad = Math.max(diameter, 0.02) / 2;
        List<Envelope.Box> boxes = new ArrayList<>();
        boxes.add(new Envelope.Box("core", new Vec3d(-l / 2, -rad, -rad), new Vec3d(l / 2, rad, rad)));
        if (accessories > 0) { boxes.add(new Envelope.Box("accessories", new Vec3d(-0.3 * l, -rad * (1 + 2 * accessories), -rad * 0.6), new Vec3d(0.3 * l, -rad, rad * 0.6))); }
        return new Envelope(boxes, Double.NaN, Double.NaN);
    }
}
