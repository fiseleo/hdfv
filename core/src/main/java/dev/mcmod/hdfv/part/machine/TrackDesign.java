package dev.mcmod.hdfv.part.machine;

import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.vehicle.DesignException;
import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;
import dev.mcmod.aerocore.api.vehicle.MassProperties;
import dev.mcmod.aerocore.api.vehicle.Part;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.part.TrackPart;
import dev.mcmod.hdfv.damage.DamagePace;
import dev.mcmod.hdfv.part.air.Components;
import dev.mcmod.hdfv.part.engine.EngineComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * A crawler undercarriage (hdfv:track, 0.26, 履帶行走機構): two tracks gaugeM apart (left at -z, right at +z, x
 * forward, y up, the origin between them at the road wheels' hubs), each on roadWheels road wheels spread over its
 * contact length, a front idler and a rear sprocket whose runs clear the ground by runLiftM, shod with steel shoes
 * (grousers of grouserHeightM on a chain of pitchM) or a rubber belt. It is sized for a machine of designMassKg. In the
 * world it is AeroCore's TrackPart (Wong's shear-displacement traction, lateral resistance, Bekker sinkage and internal
 * resistance), its sprockets the rotors "left" and "right" that the travel motors or a transmission drive.
 *
 * <p>Parameters and their rules:
 * <ul>
 * <li>The shoe's thickness under the road wheels (the rollers' radius over the ground less their own): steel, the link's
 *     height LINK_HEIGHT pitches, the shoe plate PLATE pitches and the grouser; rubber, its carcass RUBBER_CARCASS_M and
 *     its lugs.</li>
 * <li>Each roller's stiffness and damping: given, or 0 for the design mass on the road wheels of both sides sinking
 *     STATIC_SINK_M (25 mm) and ROLLER_DAMPING_RATIO of critical on that share of the mass.</li>
 * <li>Lateral resistance mu_t 0.5 to 0.8 on soils (Wong, Theory of Ground Vehicles, 4th ed., ch. 7; 0.6 taken), internal
 *     resistance 0.03 to 0.06 of the weight for steel tracks at low speed (Wong ch. 2; 0.05 taken).</li>
 * <li>Brakes at each sprocket (N m): the service brake on "brake" (and "brake_left" / "brake_right", a steering
 *     brake), the parking brake on "park". A hydrostatic machine brakes with its motors and needs only the parking
 *     brake.</li>
 * </ul>
 *
 * <p>Its mass (massKg): the two belts (steel: a shoe of the plate and grousers, GROUSER_FILL of the grouser's height
 * over the pitch, and a link pair with its pin and bushing of CHAIN_KG_PER_M3 pitch^3 for every pitch; rubber: a belt
 * of the carcass and half the lugs at RUBBER_BELT_KG_PER_M3), the road wheels (solid discs of ROLLER_FILL, 2.5 radii
 * wide), carrier rollers (carriers(): two a side on steel, one on rubber; half a road wheel each), the idlers and sprockets (webbed discs), and the frames: two
 * side frames (welded boxes as long as the belt's bottom run, their plate from bending: half the design weight at the
 * middle of a side simply supported at its ends, times DYNAMIC, at FRAME_STRESS of S355, at least FRAME_MIN_M, with
 * FRAME_FITTINGS for roller brackets, the recoil spring and the guards) and a car body between them (a closed box of
 * twice the side frame's plate, CAR_BODY_DEPTH of the frame's length). A 20 t excavator's comes to about a third of
 * its operating weight, as the lower structures of the CAT 320 and Komatsu PC200 class are.
 *
 * <p>Checked ranges (EXTRAPOLATION): the ground pressure from a 1.5 t mini excavator on rubber (about 25 kPa) to large
 * dozers and mining shovels (150 to 250 kPa; CAT D11 about 160 kPa, a 20 t excavator on 600 mm shoes about 50 kPa:
 * Caterpillar and Komatsu specification sheets), the design mass 1 to 1000 t, the gauge, contact length and shoe
 * width of those machines. INVALID: what is no undercarriage (tracks overlapping, road wheels overlapping each other,
 * shoes no wider than nothing).
 *
 * <p>Wear (TrackPart.Wearing, condition "tracks"): links, pins, bushings and rollers by the distance run, LIFE_KM at
 * RATED_SPEED_MPS (a dozer's undercarriage lasts some 3000 to 6000 h of which about half is travel at 1 to 1.5 m/s:
 * Caterpillar Performance Handbook's undercarriage life), on the game's paced clock (DamagePace).
 */
public record TrackDesign(String name, double designMassKg, double gaugeM, double contactLengthM, double shoeWidthM, int roadWheels, double roadWheelRadiusM,
                          TrackPart.Shoe shoe, double grouserHeightM, double pitchM, double idlerRadiusM, double sprocketRadiusM, double runLiftM,
                          double rollerStiffnessNPerM, double rollerDampingNsPerM, double lateralResistance, double internalResistance, double brakeNm, double parkNm)
        implements PartDesign {
    public static final String TYPE = "hdfv:track";

    /** The auto stiffness's static sinkage (m) and the auto damping's share of critical. */
    public static final double STATIC_SINK_M = 0.025, ROLLER_DAMPING_RATIO = 0.3;
    /** A steel track's link height and shoe plate, in pitches; the grousers' share of the pitch at their height. */
    public static final double LINK_HEIGHT = 0.55, PLATE = 0.065, GROUSER_FILL = 0.2;
    /** A link pair with its pin and bushing: CHAIN_KG_PER_M3 x pitch^3 (about 19 kg at 190 mm, 23 kg at 203 mm). */
    public static final double CHAIN_KG_PER_M3 = 2800;
    /** A rubber track's carcass (m) and its density with the embedded steel cores and cords (kg/m^3). */
    public static final double RUBBER_CARCASS_M = 0.035, RUBBER_BELT_KG_PER_M3 = 2000;
    /** Road wheels: solid discs of ROLLER_FILL, ROLLER_WIDTH radii wide; idlers and sprockets: webbed discs of IDLER_FILL, SPROCKET_FILL. */
    public static final double ROLLER_FILL = 0.6, ROLLER_WIDTH = 2.5, IDLER_FILL = 0.35, SPROCKET_FILL = 0.3;
    /** Idler and sprocket width: WHEEL_WIDTH_M plus WHEEL_WIDTH_SHOE of the shoe's width. */
    public static final double WHEEL_WIDTH_M = 0.12, WHEEL_WIDTH_SHOE = 0.25;
    /** The side frames' bending: dynamic factor, share of S355, least plate (m), brackets and fittings. */
    public static final double DYNAMIC = 2.5, FRAME_STRESS = 0.5, FRAME_MIN_M = 0.006, FRAME_FITTINGS = 1.4;
    /** The car body's plan depth as a share of the frame's length and its plate in side-frame plates. */
    public static final double CAR_BODY_DEPTH = 0.35, CAR_BODY_PLATES = 2;
    /** Wear: the distance a track runs to be worn out (km) and the speed its life is rated at (m/s). */
    public static final double LIFE_KM = 8000, RATED_SPEED_MPS = 1.5;

    public TrackDesign {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(shoe, "shoe");
    }

    @Override public String type() { return TYPE; }

    // ---------------------------------------------------------------- geometry

    /** The shoe's thickness under the road wheels, m (TrackPart.Spec's shoeThicknessM). */
    public double shoeThicknessM() {
        return shoe == TrackPart.Shoe.STEEL ? (LINK_HEIGHT + PLATE) * pitchM + grouserHeightM : RUBBER_CARCASS_M + grouserHeightM;
    }

    /** Ground pressure of the design mass on both tracks' contact (shoe width x contact length), Pa. */
    public double groundPressurePa() { return designMassKg * MachineRules.G / (2 * shoeWidthM * contactLengthM); }

    /** One belt's length round its idler and sprocket, m. */
    public double beltLengthM() { return 2 * (contactLengthM + idlerRadiusM + sprocketRadiusM) + Math.PI * (idlerRadiusM + sprocketRadiusM); }

    /** Shoes (or the rubber belt's cores) on one side. */
    public int shoesPerSide() { return (int) Math.ceil(beltLengthM() / pitchM - 1e-9); }

    /** Each road wheel's stiffness on the ground, N/m: given, or the design weight shared by all road wheels at STATIC_SINK_M. */
    public double stationStiffnessNPerM() {
        return rollerStiffnessNPerM > 0 ? rollerStiffnessNPerM : designMassKg * MachineRules.G / (2 * roadWheels * STATIC_SINK_M);
    }

    /** Each road wheel's damping, N s/m: given, or ROLLER_DAMPING_RATIO of critical on its share of the design mass. */
    public double stationDampingNsPerM() {
        return rollerDampingNsPerM > 0 ? rollerDampingNsPerM : 2 * ROLLER_DAMPING_RATIO * Math.sqrt(stationStiffnessNPerM() * designMassKg / (2 * roadWheels));
    }

    /** The resisting moment of a skid turn on the spot, N m: mu_t W L / 4 (Wong eq. 7.7) with the design weight. */
    public double turningMomentNm() { return lateralResistance * designMassKg * MachineRules.G * contactLengthM / 4; }

    /** The idler's and sprocket's width, m. */
    double wheelWidthM() { return WHEEL_WIDTH_M + WHEEL_WIDTH_SHOE * shoeWidthM; }

    // ---------------------------------------------------------------- mass

    /** One belt's mass, kg. */
    public double beltKg() {
        double rho = MachineRules.STEEL_KG_PER_M3;
        if (shoe == TrackPart.Shoe.RUBBER) { return beltLengthM() * shoeWidthM * (RUBBER_CARCASS_M + 0.5 * grouserHeightM) * RUBBER_BELT_KG_PER_M3; }
        double shoeKg = rho * shoeWidthM * pitchM * (PLATE * pitchM + GROUSER_FILL * grouserHeightM);
        double chainKg = CHAIN_KG_PER_M3 * pitchM * pitchM * pitchM;
        return shoesPerSide() * (shoeKg + chainKg);
    }

    /** One road wheel's mass, kg. */
    public double roadWheelKg() {
        return MachineRules.STEEL_KG_PER_M3 * ROLLER_FILL * Math.PI * roadWheelRadiusM * roadWheelRadiusM * ROLLER_WIDTH * roadWheelRadiusM;
    }

    /** Carrier rollers on each side. */
    public int carriers() { return shoe == TrackPart.Shoe.STEEL ? 2 : 1; }

    public double idlerKg() { return MachineRules.STEEL_KG_PER_M3 * IDLER_FILL * Math.PI * idlerRadiusM * idlerRadiusM * wheelWidthM(); }

    public double sprocketKg() { return MachineRules.STEEL_KG_PER_M3 * SPROCKET_FILL * Math.PI * sprocketRadiusM * sprocketRadiusM * wheelWidthM(); }

    /** One side's rollers, idler and sprocket, kg. */
    public double wheelsKg() { return (roadWheels + 0.5 * carriers()) * roadWheelKg() + idlerKg() + sprocketKg(); }

    /** A side frame's height and width (m) and length (m). */
    double frameHeightM() { return 2.2 * roadWheelRadiusM + 0.1; }
    double frameWidthM() { return 0.15 + 0.5 * shoeWidthM; }
    double frameLengthM() { return contactLengthM + idlerRadiusM + sprocketRadiusM; }

    /** A side frame's plate, m: from its bending (see the class). */
    public double framePlateM() {
        double h = frameHeightM(), w = frameWidthM();
        double moment = DYNAMIC * 0.5 * designMassKg * MachineRules.G * frameLengthM() / 4;
        return Math.max(FRAME_MIN_M, moment / (FRAME_STRESS * MachineRules.S355_PA * h * (h / 3 + w)));
    }

    /** One side frame, kg. */
    public double sideFrameKg() { return FRAME_FITTINGS * MachineRules.boxKg(frameHeightM(), frameWidthM(), framePlateM(), frameLengthM(), MachineRules.STEEL_KG_PER_M3); }

    /** The car body between the side frames, kg. */
    public double carBodyKg() {
        double span = Math.max(gaugeM - frameWidthM(), 0.1), depth = CAR_BODY_DEPTH * frameLengthM(), h = frameHeightM() + 0.15;
        return MachineRules.STEEL_KG_PER_M3 * CAR_BODY_PLATES * framePlateM() * 2 * (span * depth + span * h + depth * h);
    }

    /** The undercarriage's mass, kg: two belts, two sides of rollers, two side frames and the car body. */
    public double massKg() { return 2 * (beltKg() + wheelsKg() + sideFrameKg()) + carBodyKg(); }

    /**
     * Each side's inertia at its sprocket, kg m^2: everything the belt turns, at the sprocket's radius (the belt at
     * belt speed; the sprocket, idler and rollers as discs, their spin at belt speed: half their mass).
     */
    public double sideInertiaKgM2() {
        double r = sprocketRadiusM;
        return r * r * (beltKg() + 0.5 * (sprocketKg() + idlerKg() + (roadWheels + carriers()) * roadWheelKg()));
    }

    /** The two sides and the car body, part frame. */
    public MassProperties mass() {
        double top = idlerRadiusM - roadWheelRadiusM + runLiftM + idlerRadiusM, bottom = -(roadWheelRadiusM + shoeThicknessM());
        double y = 0.5 * (top + bottom), height = Math.max(top - bottom, 0.05), x = idlerRadiusM - sprocketRadiusM;
        double length = contactLengthM + 2 * (idlerRadiusM + sprocketRadiusM);
        double side = beltKg() + wheelsKg() + sideFrameKg();
        List<MassProperties> parts = new ArrayList<>();
        for (int k = -1; k <= 1; k += 2) {
            parts.add(Components.box(side, new Vec3d(x, y, k * gaugeM / 2), new Vec3d(length, height, shoeWidthM)));
        }
        double span = Math.max(gaugeM - frameWidthM(), 0.1);
        parts.add(Components.box(carBodyKg(), new Vec3d(0, 0.5 * frameHeightM(), 0), new Vec3d(CAR_BODY_DEPTH * frameLengthM(), frameHeightM() + 0.15, span)));
        return MassProperties.combine(parts);
    }

    // ---------------------------------------------------------------- checks

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        EngineComponent.Rules.positive(issues, "designMassKg", designMassKg);
        EngineComponent.Rules.positive(issues, "gaugeM", gaugeM);
        EngineComponent.Rules.positive(issues, "contactLengthM", contactLengthM);
        EngineComponent.Rules.positive(issues, "shoeWidthM", shoeWidthM);
        EngineComponent.Rules.count(issues, "roadWheels", roadWheels, 2, 64);
        EngineComponent.Rules.positive(issues, "roadWheelRadiusM", roadWheelRadiusM);
        EngineComponent.Rules.nonnegative(issues, "grouserHeightM", grouserHeightM);
        EngineComponent.Rules.positive(issues, "pitchM", pitchM);
        EngineComponent.Rules.positive(issues, "idlerRadiusM", idlerRadiusM);
        EngineComponent.Rules.positive(issues, "sprocketRadiusM", sprocketRadiusM);
        EngineComponent.Rules.nonnegative(issues, "runLiftM", runLiftM);
        EngineComponent.Rules.nonnegative(issues, "rollerStiffnessNPerM", rollerStiffnessNPerM);
        EngineComponent.Rules.nonnegative(issues, "rollerDampingNsPerM", rollerDampingNsPerM);
        EngineComponent.Rules.nonnegative(issues, "lateralResistance", lateralResistance);
        EngineComponent.Rules.nonnegative(issues, "internalResistance", internalResistance);
        EngineComponent.Rules.nonnegative(issues, "brakeNm", brakeNm);
        EngineComponent.Rules.nonnegative(issues, "parkNm", parkNm);
        if (!issues.isEmpty()) { return issues; }
        if (!(gaugeM > shoeWidthM)) { issues.add(DesignIssue.invalid("gaugeM", "The tracks overlap: the gauge must be more than the shoes' width")); }
        if (contactLengthM / (roadWheels - 1) < 2 * roadWheelRadiusM) { issues.add(DesignIssue.invalid("roadWheels", "The road wheels overlap: they are wider than their spacing")); }
        if (!(pitchM < contactLengthM)) { issues.add(DesignIssue.invalid("pitchM", "A shoe longer than the track's contact")); }
        if (!issues.isEmpty()) { return issues; }
        String machines = "crawler machines from a 1.5 t mini excavator to mining shovels (Caterpillar, Komatsu specification sheets)";
        DesignIssue.range(issues, "designMassKg", designMassKg, 1000, 1.0e6, machines);
        DesignIssue.range(issues, "gaugeM", gaugeM, 0.7, 7.5, machines);
        DesignIssue.range(issues, "contactLengthM", contactLengthM, 1.0, 10, machines);
        DesignIssue.range(issues, "shoeWidthM", shoeWidthM, 0.18, 2.0, machines);
        DesignIssue.range(issues, "pitchM", pitchM, shoe == TrackPart.Shoe.STEEL ? 0.09 : 0.05, shoe == TrackPart.Shoe.STEEL ? 0.7 : 0.2, machines);
        DesignIssue.range(issues, "grouserHeightM", grouserHeightM, 0, 0.15, machines);
        DesignIssue.range(issues, "roadWheels", roadWheels, 3, 14, machines);
        DesignIssue.range(issues, "groundPressurePa", groundPressurePa(), 20e3, 250e3, "crawlers' ground pressure: 25 kPa (mini excavators) to 250 kPa (mining shovels)");
        DesignIssue.range(issues, "lateralResistance", lateralResistance, 0.4, 0.9, "Wong, Theory of Ground Vehicles, ch. 7: 0.5 to 0.8 on soils");
        DesignIssue.range(issues, "internalResistance", internalResistance, 0.02, 0.08, "Wong, Theory of Ground Vehicles, ch. 2: 0.03 to 0.06 for steel tracks");
        return issues;
    }

    // ---------------------------------------------------------------- compiling

    /** AeroCore's track spec of this design. */
    public TrackPart.Spec spec() {
        return new TrackPart.Spec(gaugeM, contactLengthM, shoeWidthM, roadWheels, roadWheelRadiusM, shoeThicknessM(), idlerRadiusM, sprocketRadiusM, runLiftM, shoe,
                stationStiffnessNPerM(), stationDampingNsPerM(), lateralResistance, internalResistance, sideInertiaKgM2(), brakeNm, parkNm);
    }

    /** How its tracks wear: LIFE_KM at RATED_SPEED_MPS on the game's clock. */
    public static TrackPart.Wearing wearing() { return new TrackPart.Wearing(DamagePace.paced(LIFE_KM * 1000 / RATED_SPEED_MPS), RATED_SPEED_MPS); }

    @Override public Part compile() {
        List<DesignIssue> issues = check();
        DesignException.throwIfInvalid(issues);
        return new TrackPart(this, spec(), mass(), Components.extrapolations(issues)).wearing(wearing());
    }

    // ---------------------------------------------------------------- JSON

    @Override public Map<String, Object> parameters() {
        return Fields.object("designMassKg", designMassKg, "gaugeM", gaugeM, "contactLengthM", contactLengthM, "shoeWidthM", shoeWidthM, "roadWheels", roadWheels,
                "roadWheelRadiusM", roadWheelRadiusM, "shoe", shoe.name().toLowerCase(Locale.ROOT), "grouserHeightM", grouserHeightM, "pitchM", pitchM,
                "idlerRadiusM", idlerRadiusM, "sprocketRadiusM", sprocketRadiusM, "runLiftM", runLiftM, "rollerStiffnessNPerM", rollerStiffnessNPerM,
                "rollerDampingNsPerM", rollerDampingNsPerM, "lateralResistance", lateralResistance, "internalResistance", internalResistance, "brakeNm", brakeNm,
                "parkNm", parkNm);
    }

    public static PartDesign read(Fields f) {
        TrackPart.Shoe shoe = EngineComponent.Rules.choice(f, "shoe", TrackPart.Shoe.class, TrackPart.Shoe.STEEL);
        TrackDesign d = shoe == TrackPart.Shoe.RUBBER ? SampleMachinery.miniExcavatorTracks() : standard();
        return new TrackDesign(f.string("name", d.name), f.number("designMassKg", d.designMassKg), f.number("gaugeM", d.gaugeM), f.number("contactLengthM", d.contactLengthM),
                f.number("shoeWidthM", d.shoeWidthM), f.integer("roadWheels", d.roadWheels), f.number("roadWheelRadiusM", d.roadWheelRadiusM), shoe,
                f.number("grouserHeightM", d.grouserHeightM), f.number("pitchM", d.pitchM), f.number("idlerRadiusM", d.idlerRadiusM), f.number("sprocketRadiusM", d.sprocketRadiusM),
                f.number("runLiftM", d.runLiftM), f.number("rollerStiffnessNPerM", d.rollerStiffnessNPerM), f.number("rollerDampingNsPerM", d.rollerDampingNsPerM),
                f.number("lateralResistance", d.lateralResistance), f.number("internalResistance", d.internalResistance), f.number("brakeNm", d.brakeNm), f.number("parkNm", d.parkNm));
    }

    /** The standard design: a 20 t excavator's (SampleMachinery.excavatorTracks20t). */
    public static TrackDesign standard() { return SampleMachinery.excavatorTracks20t(); }
}
