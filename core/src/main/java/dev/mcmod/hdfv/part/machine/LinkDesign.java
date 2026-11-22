package dev.mcmod.hdfv.part.machine;

import dev.mcmod.aerocore.api.CollisionSphere;
import dev.mcmod.aerocore.api.Orientation;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.hydraulic.Cylinder;
import dev.mcmod.aerocore.api.hydraulic.Hydraulics;
import dev.mcmod.aerocore.api.hydraulic.Motor;
import dev.mcmod.aerocore.api.vehicle.DesignException;
import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;
import dev.mcmod.aerocore.api.vehicle.MassProperties;
import dev.mcmod.aerocore.api.vehicle.Mount;
import dev.mcmod.aerocore.api.vehicle.Part;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.part.LinkPart;
import dev.mcmod.hdfv.design.RecordEditor;
import dev.mcmod.hdfv.part.air.Components;
import dev.mcmod.hdfv.part.engine.EngineComponent;
import dev.mcmod.hdfv.part.engine.EngineMaterial;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * One jointed link of a machine's work equipment or frame (hdfv:link, 0.26, 連桿／作業臂): it moves on its joint against
 * what it is mounted on, driven by a hydraulic ram, a geared motor (a slew), a free pin or held; everything mounted on
 * its mount points rides on it. In the world it is AeroCore's LinkPart.
 *
 * <p>Its kind (linkKind) sets its shape, its joint and its mount points:
 * <ul>
 * <li>BOOM (an excavator's mono boom, a crane's base section), STICK (an excavator's arm), LOADER_ARM (a wheel loader's
 *     lift arms), PLATFORM_BOOM and PLACING_BOOM (knuckle sections of an aerial work platform or a concrete pump):
 *     welded box sections running along +x from the pin at the origin to the "tip" at lengthM, turning about z (q up:
 *     the tip rises), driven by rams.</li>
 * <li>TELESCOPIC (a crane's or telehandler's telescopic section, sliding along x), MAST (a forklift's, piling rig's or
 *     drill's mast stage, along y: "tip" at its top), OUTRIGGER_BEAM (along z: its jack at the "tip"): uniform box
 *     sections on a sliding joint, their ram along the slide. BOOM, TELESCOPIC and MAST also have a "nest" NEST of
 *     their length along them, where the next section sits inside (its tip sticking out past this one's).</li>
 * <li>LATTICE (a crawler crane's lattice boom): four chord tubes and their lacing, luffed about z by its boom hoist,
 *     taken as a geared drive (a slew of the hoist motor through the winch's reducer and the luffing tackle: the rope's
 *     changing lever over the angle is not modelled).</li>
 * <li>SLEW_RING (a slewing bearing, its diameter lengthM and its races sectionWidthM x sectionHeightM): turning about y,
 *     endless by default, by a swing motor through its reducer and the ring's teeth; the house rides on "upper".</li>
 * <li>ARTICULATION (a loader's or dumper's centre hinge): the front frame turns about y on "front" (lengthM ahead of the
 *     hinge), steered by rams across the hinge.</li>
 * </ul>
 * The joint's type (jointType) and axis (jointAxis) are the kind's when AUTO; its range is lowerDeg..upperDeg
 * (revolute; endless: none) or lowerM..upperM (sliding), the new machine's position startDeg or startM.
 *
 * <p>The ram (actuation RAM): ramCount cylinders of ramBoreM / ramRodM and ramStrokeM acting together from the base pin
 * (basePin*, in the frame of what it is mounted on, which the joint does not move) to the rod pin (rodPin*, in the
 * link's own frame); relieved at reliefPa, held centred by load-holding valves up to holdPa, through a valve of
 * valveFlowM3PerS on its circuit. Its pin-to-pin length over the whole range must stay within its closed length (the
 * stroke and MachineRules' dead length) and its open length, and it must not pass its dead centre (its lever, dL/dq,
 * keeps one sign and at least MIN_LEVER): INVALID otherwise, as LinkPart.ramLength would have it. The slew (SLEW): an
 * axial piston motor of slewDisplacementM3 through slewRatio at SLEW_EFFICIENCY, braked centred with slewBrakeNm at the
 * link. FREE: a pin with freeFriction (N m, or N sliding).
 *
 * <p>Its mass: a welded box (two webs and two flanges of plateM, MachineRules.boxKg) as long as the link, TAPER of it
 * for the kinds whose given section is the deepest (booms, sticks and arms taper to their pins), BOSS_SHARE more for
 * pin bosses, brackets and diaphragms; a lattice of four chords CHORD_OD of the section across and plateM thick and
 * their lacing (LACING_OD, LACING_WALL of the chords', diagonals at 45 degrees on all four faces) with LATTICE_FITTINGS
 * for the connectors; a slewing ring RING_FILL of its races' solid ring. Its pins (PIN_SHARE of the section across, as
 * long as it is wide), its cylinders (MachineRules.cylinderKg at the relief) or its slew motor and reducer
 * (MachineRules; a slewing ring's reducer takes the ratio over PINION_RATIO, the rest is the ring's teeth; a luffing
 * drive's at most MAX_GEARED, the rest is the tackle). Contact
 * spheres along it, a quarter of the section's height and width across.
 *
 * <p>The defaults (SampleMachinery) are a 20 t excavator's front (CAT 320 / Komatsu PC200 class: a 5.7 m boom on two
 * 120 mm cylinders of 1.335 m, a 2.9 m stick on a 135 mm cylinder of 1.49 m, 350 bar).
 */
public record LinkDesign(String name, LinkKind linkKind, double lengthM, double sectionHeightM, double sectionWidthM, double plateM, EngineMaterial material,
                         JointType jointType, JointAxis jointAxis, boolean endless, double lowerDeg, double upperDeg, double startDeg,
                         double lowerM, double upperM, double startM, Actuation actuation, double ramBoreM, double ramRodM, double ramStrokeM, int ramCount,
                         double basePinXM, double basePinYM, double basePinZM, double rodPinXM, double rodPinYM, double rodPinZM,
                         double reliefPa, double holdPa, double valveFlowM3PerS, String circuit,
                         double slewDisplacementM3, double slewRatio, double slewBrakeNm, double freeFriction) implements PartDesign, RecordEditor.Shown {
    public static final String TYPE = "hdfv:link";

    /** What the link is (see the class). */
    public enum LinkKind { BOOM, STICK, LOADER_ARM, TELESCOPIC, LATTICE, MAST, SLEW_RING, ARTICULATION, PLATFORM_BOOM, PLACING_BOOM, OUTRIGGER_BEAM }

    /** The joint's type: the kind's (AUTO), turning or sliding. */
    public enum JointType { AUTO, REVOLUTE, PRISMATIC }

    /** The joint's axis in the part frame: the kind's (AUTO) or one of the frame's axes. */
    public enum JointAxis { AUTO, X, Y, Z }

    /** What drives the joint: a ram, a geared motor, nothing (a free pin), or the joint is held (moved by the game only). */
    public enum Actuation { RAM, SLEW, FREE, HELD }

    /** Box kinds whose section tapers to their pins: the share of a uniform box. */
    public static final double TAPER = 0.8;
    /** Pin bosses, brackets and diaphragms on a box. */
    public static final double BOSS_SHARE = 0.15;
    /** Pins: diameter as a share of the section's larger side. */
    public static final double PIN_SHARE = 0.15;
    /** A lattice: chord diameter over the section's smaller side, lacing diameter and wall over the chords', the connectors. */
    public static final double CHORD_OD = 0.07, LACING_OD = 0.4, LACING_WALL = 0.4, LATTICE_FITTINGS = 0.1;
    /** A slewing ring's races: share of their solid ring (rolling elements, bolt holes and teeth). */
    public static final double RING_FILL = 0.6;
    /**
     * The slew's gear efficiency (a planetary reducer and the ring's or tackle's pinion); the most of the ratio a
     * reducer takes (the rest the luffing tackle's); a slewing ring's pinion-to-ring ratio (a pinion of some 12 teeth on
     * a ring of about 100: the reducer takes the ratio over it).
     */
    public static final double SLEW_EFFICIENCY = 0.9, MAX_GEARED = 200, PINION_RATIO = 8;
    /** Where the next section nests in a BOOM, TELESCOPIC or MAST, as a share of its length. */
    public static final double NEST = 0.12;
    /** Ram geometry: samples over the range, tolerance on the lengths (m), least lever (m per rad, or per m sliding). */
    public static final int RAM_SAMPLES = 64;
    public static final double RAM_TOLERANCE_M = 0.001, MIN_LEVER = 0.02;

    public LinkDesign {
        Objects.requireNonNull(name, "name"); Objects.requireNonNull(linkKind, "linkKind"); Objects.requireNonNull(material, "material");
        Objects.requireNonNull(jointType, "jointType"); Objects.requireNonNull(jointAxis, "jointAxis"); Objects.requireNonNull(actuation, "actuation");
        circuit = circuit == null ? "main" : circuit;
    }

    @Override public String type() { return TYPE; }

    // ---------------------------------------------------------------- joint

    /** The direction the link runs along (its length), part frame. */
    public Vec3d longAxis() {
        return switch (linkKind) { case MAST -> Vec3d.Y; case OUTRIGGER_BEAM -> Vec3d.Z; default -> Vec3d.X; };
    }

    public Part.Joint.Kind jointKind() {
        return switch (jointType) {
            case REVOLUTE -> Part.Joint.Kind.REVOLUTE;
            case PRISMATIC -> Part.Joint.Kind.PRISMATIC;
            case AUTO -> switch (linkKind) { case TELESCOPIC, MAST, OUTRIGGER_BEAM -> Part.Joint.Kind.PRISMATIC; default -> Part.Joint.Kind.REVOLUTE; };
        };
    }

    public boolean revolute() { return jointKind() == Part.Joint.Kind.REVOLUTE; }

    /** The joint's axis, part frame: turning about it or sliding along it. */
    public Vec3d axis() {
        return switch (jointAxis) {
            case X -> Vec3d.X;
            case Y -> Vec3d.Y;
            case Z -> Vec3d.Z;
            case AUTO -> !revolute() ? longAxis() : linkKind == LinkKind.SLEW_RING || linkKind == LinkKind.ARTICULATION ? Vec3d.Y : Vec3d.Z;
        };
    }

    /** The range's ends and the start in the joint's units (rad or m); endless: infinite ends. */
    public double lowerQ() { return revolute() ? (endless ? Double.NEGATIVE_INFINITY : Math.toRadians(lowerDeg)) : lowerM; }
    public double upperQ() { return revolute() ? (endless ? Double.POSITIVE_INFINITY : Math.toRadians(upperDeg)) : upperM; }
    public double startQ() { return revolute() ? Math.toRadians(startDeg) : startM; }

    public Part.Joint joint() {
        return revolute() ? Part.Joint.revolute(Vec3d.ZERO, axis(), lowerQ(), upperQ(), startQ()) : Part.Joint.prismatic(axis(), lowerQ(), upperQ(), startQ());
    }

    // ---------------------------------------------------------------- ram geometry

    public Vec3d basePin() { return new Vec3d(basePinXM, basePinYM, basePinZM); }
    public Vec3d rodPin() { return new Vec3d(rodPinXM, rodPinYM, rodPinZM); }
    public Cylinder cylinder() { return new Cylinder(ramBoreM, ramRodM, ramStrokeM, ramCount); }

    /** The ram's pin-to-pin length closed and open, m. */
    public double closedLengthM() { return MachineRules.closedLengthM(ramBoreM, ramStrokeM); }
    public double openLengthM() { return closedLengthM() + ramStrokeM; }

    /** Where the joint at q puts a point of the link's own frame, in its mount's frame (LinkPart.moved: the joint's origin is the part's). */
    public Vec3d moved(Vec3d partPoint, double q) {
        if (!revolute()) { return partPoint.add(axis().scale(q)); }
        return Orientation.rotationVector(axis().scale(q)).rotate(partPoint);
    }

    /** The ram's pin-to-pin length at q, m (LinkPart.ramLength). */
    public double ramLengthM(double q) { return moved(rodPin(), q).subtract(basePin()).length(); }

    /** dL/dq at q (m per rad, or per m). */
    public double leverM(double q) {
        double h = 1e-4;
        return (ramLengthM(q + h) - ramLengthM(q - h)) / (2 * h);
    }

    /** The ram's least and most length and least |lever| over the joint's range, and whether its lever changes sign. */
    public record RamReach(double leastM, double mostM, double leastLever, boolean overCentre) {}

    public RamReach ramReach() {
        if (revolute() && endless) { return new RamReach(Double.NaN, Double.NaN, 0, true); }
        double lo = lowerQ(), hi = upperQ(), least = Double.POSITIVE_INFINITY, most = Double.NEGATIVE_INFINITY, lever = Double.POSITIVE_INFINITY;
        boolean up = false, down = false;
        for (int i = 0; i <= RAM_SAMPLES; i++) {
            double q = lo + (hi - lo) * i / RAM_SAMPLES, l = ramLengthM(q), d = leverM(q);
            least = Math.min(least, l); most = Math.max(most, l); lever = Math.min(lever, Math.abs(d));
            if (d > 0) { up = true; }
            if (d < 0) { down = true; }
        }
        return new RamReach(least, most, lever, up && down);
    }

    /** What the ram pushes the joint with at its relief at its least lever, N m (or N sliding): what the boom lifts, the arm crowds. */
    public double ramEffort() {
        if (actuation != Actuation.RAM || (revolute() && endless)) { return 0; }
        return cylinder().maxPushN(reliefPa) * ramReach().leastLever();
    }

    /** The slew's most torque at the link at its relief, N m. */
    public double slewTorqueNm() {
        return actuation == Actuation.SLEW ? Motor.axialPiston(slewDisplacementM3).maxTorqueNm(reliefPa) * slewRatio * SLEW_EFFICIENCY : 0;
    }

    /** The slew's top rate at its valve's full flow, rad/s. */
    public double slewRateRadPerS() {
        return actuation == Actuation.SLEW ? Motor.axialPiston(slewDisplacementM3).speedRadPerS(valveFlowM3PerS, 0) / slewRatio : 0;
    }

    // ---------------------------------------------------------------- mass

    double taper() {
        return switch (linkKind) { case BOOM, STICK, LOADER_ARM, PLATFORM_BOOM, PLACING_BOOM -> TAPER; default -> 1; };
    }

    public double chordDiameterM() { return CHORD_OD * Math.min(sectionHeightM, sectionWidthM); }

    /** The structure, kg. */
    public double structureKg() {
        double rho = material.densityKgPerM3();
        return switch (linkKind) {
            case LATTICE -> {
                double d = chordDiameterM(), t = plateM, dl = LACING_OD * d, tl = LACING_WALL * t;
                double chords = 4 * Math.PI * (d - t) * t, lacing = 4 * Math.sqrt(2) * Math.PI * (dl - tl) * tl;
                yield (1 + LATTICE_FITTINGS) * rho * lengthM * (chords + lacing);
            }
            case SLEW_RING -> RING_FILL * rho * Math.PI * lengthM * sectionWidthM * sectionHeightM;
            default -> (1 + BOSS_SHARE) * taper() * MachineRules.boxKg(sectionHeightM, sectionWidthM, plateM, lengthM, rho);
        };
    }

    public double pinDiameterM() { return PIN_SHARE * Math.max(sectionHeightM, sectionWidthM); }

    /** Pins: root and tip of a turning link, the tip of a sliding one, the hinge's two, none on a ring. */
    public int pins() {
        return switch (linkKind) {
            case SLEW_RING -> 0;
            case ARTICULATION -> 2;
            default -> revolute() ? 2 : 1;
        };
    }

    /** The pins, kg: as long as the section is wide (a lattice's: four, at its chords' feet and head, a chord across and three long). */
    public double pinsKg() {
        if (linkKind == LinkKind.LATTICE) { double d = chordDiameterM(); return 4 * MachineRules.STEEL_KG_PER_M3 * Math.PI / 4 * d * d * 3 * d; }
        double d = pinDiameterM();
        return pins() * MachineRules.STEEL_KG_PER_M3 * Math.PI / 4 * d * d * (sectionWidthM + 0.1);
    }

    /** The cylinders, or the slew's motor and reducer, kg. */
    public double actuatorKg() {
        return switch (actuation) {
            case RAM -> ramCount * MachineRules.cylinderKg(ramBoreM, ramRodM, ramStrokeM, reliefPa);
            case SLEW -> {
                Motor m = Motor.axialPiston(slewDisplacementM3);
                double geared = linkKind == LinkKind.SLEW_RING ? slewRatio / PINION_RATIO : Math.min(slewRatio, MAX_GEARED);
                yield MachineRules.unitKg(HydraulicUnit.AXIAL_PISTON, false, slewDisplacementM3 / Hydraulics.CM3_M3)
                        + MachineRules.REDUCER_KG_PER_NM * m.maxTorqueNm(reliefPa) * geared;
            }
            case FREE, HELD -> 0;
        };
    }

    public double massKg() { return structureKg() + pinsKg() + actuatorKg(); }

    /** The structure's and pins' box: centre and size in the part frame. */
    Vec3d structureSize() {
        double l = lengthM, h = sectionHeightM, w = sectionWidthM;
        return switch (linkKind) { case MAST -> new Vec3d(h, l, w); case OUTRIGGER_BEAM -> new Vec3d(w, h, l); default -> new Vec3d(l, h, w); };
    }

    public MassProperties mass() {
        List<MassProperties> parts = new ArrayList<>();
        double body = structureKg() + pinsKg();
        if (linkKind == LinkKind.SLEW_RING) {
            double r = lengthM / 2, h = sectionHeightM, m = Math.max(body, 1e-6), across = m * (r * r / 2 + h * h / 12);
            parts.add(new MassProperties(m, new Vec3d(0, h / 2, 0), across, m * r * r, across, 0, 0, 0));
        } else {
            parts.add(Components.box(body, longAxis().scale(lengthM / 2), structureSize()));
        }
        double drive = actuatorKg();
        if (drive > 0) {
            Vec3d at = switch (actuation) {
                case RAM -> basePin().add(rodPin()).scale(0.5);
                case SLEW -> linkKind == LinkKind.SLEW_RING ? new Vec3d(0.5 * lengthM, sectionHeightM, 0) : Vec3d.ZERO;
                default -> Vec3d.ZERO;
            };
            double s = actuation == Actuation.RAM ? 2 * ramBoreM : 0.3;
            parts.add(Components.box(drive, at, new Vec3d(s, s, s)));
        }
        return MassProperties.combine(parts);
    }

    public List<CollisionSphere> collision() {
        if (linkKind == LinkKind.SLEW_RING || linkKind == LinkKind.ARTICULATION) { return List.of(); }
        return MachineRules.along(longAxis(), lengthM, 0.25 * (sectionHeightM + sectionWidthM));
    }

    public Map<String, Mount> mountPoints() {
        Map<String, Mount> m = new LinkedHashMap<>();
        switch (linkKind) {
            case SLEW_RING -> m.put("upper", Mount.at(new Vec3d(0, sectionHeightM, 0)));
            case ARTICULATION -> m.put("front", Mount.at(new Vec3d(lengthM, 0, 0)));
            default -> m.put("tip", Mount.at(longAxis().scale(lengthM)));
        }
        if (linkKind == LinkKind.BOOM || linkKind == LinkKind.TELESCOPIC || linkKind == LinkKind.MAST) { m.put("nest", Mount.at(longAxis().scale(NEST * lengthM))); }
        return m;
    }

    // ---------------------------------------------------------------- the screen

    @Override public boolean shows(String parameter) {
        boolean turning = revolute();
        return switch (parameter) {
            case "endless", "startDeg" -> turning;
            case "lowerDeg", "upperDeg" -> turning && !endless;
            case "lowerM", "upperM", "startM" -> !turning;
            case "ramBoreM", "ramRodM", "ramStrokeM", "ramCount", "basePinXM", "basePinYM", "basePinZM", "rodPinXM", "rodPinYM", "rodPinZM", "holdPa" ->
                    actuation == Actuation.RAM;
            case "reliefPa", "valveFlowM3PerS", "circuit" -> actuation == Actuation.RAM || actuation == Actuation.SLEW;
            case "slewDisplacementM3", "slewRatio", "slewBrakeNm" -> actuation == Actuation.SLEW;
            case "freeFriction" -> actuation == Actuation.FREE;
            default -> true;
        };
    }

    // ---------------------------------------------------------------- checks

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        EngineComponent.Rules.positive(issues, "lengthM", lengthM);
        EngineComponent.Rules.positive(issues, "sectionHeightM", sectionHeightM);
        EngineComponent.Rules.positive(issues, "sectionWidthM", sectionWidthM);
        EngineComponent.Rules.positive(issues, "plateM", plateM);
        material.check(issues, "material");
        if (revolute()) {
            if (!MachineRules.finite(startDeg)) { issues.add(DesignIssue.invalid("startDeg", "startDeg must be a number")); }
            if (!endless && !(MachineRules.finite(lowerDeg, upperDeg) && lowerDeg <= startDeg && startDeg <= upperDeg)) {
                issues.add(DesignIssue.invalid("startDeg", "The joint's range runs from its lower to its upper end and holds its start"));
            }
        } else {
            if (endless) { issues.add(DesignIssue.invalid("endless", "A sliding joint has ends")); }
            if (!(MachineRules.finite(lowerM, upperM, startM) && lowerM <= startM && startM <= upperM)) {
                issues.add(DesignIssue.invalid("startM", "The joint's range runs from its lower to its upper end and holds its start"));
            }
        }
        switch (actuation) {
            case RAM -> {
                EngineComponent.Rules.positive(issues, "ramBoreM", ramBoreM);
                EngineComponent.Rules.positive(issues, "ramRodM", ramRodM);
                EngineComponent.Rules.positive(issues, "ramStrokeM", ramStrokeM);
                EngineComponent.Rules.count(issues, "ramCount", ramCount, 1, 16);
                EngineComponent.Rules.positive(issues, "reliefPa", reliefPa);
                EngineComponent.Rules.positive(issues, "valveFlowM3PerS", valveFlowM3PerS);
                if (!MachineRules.finite(basePinXM, basePinYM, basePinZM, rodPinXM, rodPinYM, rodPinZM)) { issues.add(DesignIssue.invalid("basePinXM", "The pins must be numbers")); }
                if (circuit.isBlank()) { issues.add(DesignIssue.invalid("circuit", "A ram runs on a circuit: give it a name")); }
                if (issues.isEmpty() && !(ramRodM < ramBoreM)) { issues.add(DesignIssue.invalid("ramRodM", "The rod must be thinner than the bore")); }
                if (issues.isEmpty() && !(holdPa >= reliefPa)) { issues.add(DesignIssue.invalid("holdPa", "The load-holding valves are set at or above the relief")); }
            }
            case SLEW -> {
                if (!revolute()) { issues.add(DesignIssue.invalid("actuation", "A slew turns a revolute joint")); }
                EngineComponent.Rules.positive(issues, "slewDisplacementM3", slewDisplacementM3);
                EngineComponent.Rules.positive(issues, "slewRatio", slewRatio);
                EngineComponent.Rules.nonnegative(issues, "slewBrakeNm", slewBrakeNm);
                EngineComponent.Rules.positive(issues, "reliefPa", reliefPa);
                EngineComponent.Rules.positive(issues, "valveFlowM3PerS", valveFlowM3PerS);
                if (circuit.isBlank()) { issues.add(DesignIssue.invalid("circuit", "A slew runs on a circuit: give it a name")); }
            }
            case FREE -> EngineComponent.Rules.nonnegative(issues, "freeFriction", freeFriction);
            case HELD -> { }
        }
        if (!issues.isEmpty()) { return issues; }
        // the structure: plates that meet in the middle are no box, a chord's wall no tube
        if (linkKind == LinkKind.LATTICE) {
            if (!(2 * plateM < chordDiameterM())) { issues.add(DesignIssue.invalid("plateM", "The chords' wall is thicker than their radius")); }
        } else if (linkKind != LinkKind.SLEW_RING && !(2 * plateM < Math.min(sectionHeightM, sectionWidthM))) {
            issues.add(DesignIssue.invalid("plateM", "Plates that meet in the middle are no box section"));
        }
        if (!revolute() && upperM >= lengthM) { issues.add(DesignIssue.invalid("upperM", "The section would slide out of its sleeve")); }
        if (actuation == Actuation.RAM) {
            if (revolute() && endless) { issues.add(DesignIssue.invalid("endless", "A ram cannot turn a joint round and round")); }
            else {
                RamReach r = ramReach();
                if (r.leastM() < closedLengthM() - RAM_TOLERANCE_M) {
                    issues.add(DesignIssue.invalid("ramStrokeM", String.format(Locale.ROOT, "The ram closes to %.3f m but the range asks %.3f m", closedLengthM(), r.leastM())));
                }
                if (r.mostM() > openLengthM() + RAM_TOLERANCE_M) {
                    issues.add(DesignIssue.invalid("ramStrokeM", String.format(Locale.ROOT, "The ram opens to %.3f m but the range asks %.3f m", openLengthM(), r.mostM())));
                }
                if (r.overCentre() || r.leastLever() < MIN_LEVER) {
                    issues.add(DesignIssue.invalid("basePinXM", "The ram passes its dead centre in the joint's range: it cannot drive the joint there"));
                }
            }
        }
        if (!issues.isEmpty()) { return issues; }
        String machines = switch (linkKind) {
            case BOOM, STICK, LOADER_ARM -> "excavators and loaders from mini (CAT 301.5) to 100 t class (Caterpillar, Komatsu specification sheets)";
            case TELESCOPIC -> "telescopic crane and telehandler sections (Liebherr, Grove, JCB data sheets)";
            case LATTICE -> "crawler cranes' lattice booms (Liebherr LR, Manitowoc data sheets)";
            case MAST -> "forklift, piling and drill masts";
            case SLEW_RING -> "slewing bearings (Rothe Erde, Kaydon catalogues)";
            case ARTICULATION -> "wheel loaders' and articulated dumpers' centre hinges";
            case PLATFORM_BOOM, PLACING_BOOM -> "aerial platforms' and concrete pumps' booms (Genie, JLG, Schwing, Putzmeister)";
            case OUTRIGGER_BEAM -> "mobile cranes' outrigger beams (Liebherr, Grove data sheets)";
        };
        double[] length = switch (linkKind) {
            case BOOM, STICK, LOADER_ARM -> new double[]{1.0, 14};
            case TELESCOPIC -> new double[]{2, 20};
            case LATTICE -> new double[]{3, 150};
            case MAST -> new double[]{1, 15};
            case SLEW_RING -> new double[]{0.4, 6};
            case ARTICULATION -> new double[]{0.1, 1.5};
            case PLATFORM_BOOM, PLACING_BOOM -> new double[]{1, 20};
            case OUTRIGGER_BEAM -> new double[]{0.8, 8};
        };
        DesignIssue.range(issues, "lengthM", lengthM, length[0], length[1], machines);
        DesignIssue.range(issues, "plateM", plateM, 0.003, 0.05, machines);
        if (!revolute() && linkKind != LinkKind.SLEW_RING) { DesignIssue.range(issues, "upperM", upperM, -lengthM, 0.85 * lengthM, machines + ": at least 15 % of a section stays in its sleeve"); }
        if (actuation == Actuation.RAM || actuation == Actuation.SLEW) {
            DesignIssue.range(issues, "reliefPa", reliefPa, 100e5, 450e5, "mobile hydraulics (100 to 450 bar)");
            DesignIssue.range(issues, "valveFlowM3PerS", valveFlowM3PerS, 10 * Hydraulics.LITRE_PER_MIN_M3_PER_S, 1500 * Hydraulics.LITRE_PER_MIN_M3_PER_S,
                    "mobile control valves (10 to 1500 L/min)");
        }
        if (actuation == Actuation.RAM) {
            DesignIssue.range(issues, "ramBoreM", ramBoreM, 0.03, 0.5, "mobile machinery cylinders");
            DesignIssue.range(issues, "holdPa", holdPa, reliefPa, 1.5 * reliefPa, "load-holding valves set some 10 to 30 % over the relief");
        }
        return issues;
    }

    // ---------------------------------------------------------------- compiling

    /** AeroCore's actuator of this design, or null for a held joint. */
    public LinkPart.Actuator actuator() {
        return switch (actuation) {
            case RAM -> new LinkPart.Ram(cylinder(), basePin(), rodPin(), circuit, reliefPa, holdPa, valveFlowM3PerS);
            case SLEW -> new LinkPart.Slew(Motor.axialPiston(slewDisplacementM3), slewRatio, SLEW_EFFICIENCY, circuit, reliefPa, valveFlowM3PerS, slewBrakeNm);
            case FREE -> new LinkPart.Free(freeFriction);
            case HELD -> null;
        };
    }

    @Override public Part compile() {
        List<DesignIssue> issues = check();
        DesignException.throwIfInvalid(issues);
        return new LinkPart(this, mass(), collision(), mountPoints(), joint(), actuator(), List.of(), List.of(), List.of(), Components.extrapolations(issues));
    }

    // ---------------------------------------------------------------- JSON

    @Override public Map<String, Object> parameters() {
        Map<String, Object> m = Fields.object("linkKind", key(linkKind), "lengthM", lengthM, "sectionHeightM", sectionHeightM, "sectionWidthM", sectionWidthM, "plateM", plateM);
        m.put("material", material.toJson());
        m.putAll(Fields.object("jointType", key(jointType), "jointAxis", key(jointAxis), "endless", endless, "lowerDeg", lowerDeg, "upperDeg", upperDeg, "startDeg", startDeg,
                "lowerM", lowerM, "upperM", upperM, "startM", startM, "actuation", key(actuation), "ramBoreM", ramBoreM, "ramRodM", ramRodM, "ramStrokeM", ramStrokeM,
                "ramCount", ramCount, "basePinXM", basePinXM, "basePinYM", basePinYM, "basePinZM", basePinZM, "rodPinXM", rodPinXM, "rodPinYM", rodPinYM, "rodPinZM", rodPinZM,
                "reliefPa", reliefPa, "holdPa", holdPa, "valveFlowM3PerS", valveFlowM3PerS, "circuit", circuit, "slewDisplacementM3", slewDisplacementM3, "slewRatio", slewRatio,
                "slewBrakeNm", slewBrakeNm, "freeFriction", freeFriction));
        return m;
    }

    private static String key(Enum<?> e) { return e.name().toLowerCase(Locale.ROOT); }

    public static PartDesign read(Fields f) {
        LinkKind kind = EngineComponent.Rules.choice(f, "linkKind", LinkKind.class, LinkKind.BOOM);
        LinkDesign d = standard(kind);
        return new LinkDesign(f.string("name", d.name), kind, f.number("lengthM", d.lengthM), f.number("sectionHeightM", d.sectionHeightM),
                f.number("sectionWidthM", d.sectionWidthM), f.number("plateM", d.plateM), EngineComponent.Rules.material(f, "material", d.material),
                EngineComponent.Rules.choice(f, "jointType", JointType.class, d.jointType), EngineComponent.Rules.choice(f, "jointAxis", JointAxis.class, d.jointAxis),
                f.bool("endless", d.endless), f.number("lowerDeg", d.lowerDeg), f.number("upperDeg", d.upperDeg), f.number("startDeg", d.startDeg),
                f.number("lowerM", d.lowerM), f.number("upperM", d.upperM), f.number("startM", d.startM),
                EngineComponent.Rules.choice(f, "actuation", Actuation.class, d.actuation), f.number("ramBoreM", d.ramBoreM), f.number("ramRodM", d.ramRodM),
                f.number("ramStrokeM", d.ramStrokeM), f.integer("ramCount", d.ramCount), f.number("basePinXM", d.basePinXM), f.number("basePinYM", d.basePinYM),
                f.number("basePinZM", d.basePinZM), f.number("rodPinXM", d.rodPinXM), f.number("rodPinYM", d.rodPinYM), f.number("rodPinZM", d.rodPinZM),
                f.number("reliefPa", d.reliefPa), f.number("holdPa", d.holdPa), f.number("valveFlowM3PerS", d.valveFlowM3PerS), f.string("circuit", d.circuit),
                f.number("slewDisplacementM3", d.slewDisplacementM3), f.number("slewRatio", d.slewRatio), f.number("slewBrakeNm", d.slewBrakeNm),
                f.number("freeFriction", d.freeFriction));
    }

    /** A kind's standard link (SampleMachinery). */
    public static LinkDesign standard(LinkKind kind) {
        return switch (kind) {
            case BOOM -> SampleMachinery.excavatorBoom();
            case STICK -> SampleMachinery.excavatorStick();
            case LOADER_ARM -> SampleMachinery.loaderArm();
            case TELESCOPIC -> SampleMachinery.craneBoomSection();
            case LATTICE -> SampleMachinery.latticeBoom();
            case MAST -> SampleMachinery.mastStage();
            case SLEW_RING -> SampleMachinery.swingRing();
            case ARTICULATION -> SampleMachinery.articulationHinge();
            case PLATFORM_BOOM -> SampleMachinery.platformBoom();
            case PLACING_BOOM -> SampleMachinery.placingBoom();
            case OUTRIGGER_BEAM -> SampleMachinery.outriggerBeam();
        };
    }

    /** The standard link: a 20 t excavator's boom. */
    public static LinkDesign standard() { return standard(LinkKind.BOOM); }
}
