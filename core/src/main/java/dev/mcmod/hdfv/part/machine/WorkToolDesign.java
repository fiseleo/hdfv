package dev.mcmod.hdfv.part.machine;

import dev.mcmod.aerocore.api.CollisionSphere;
import dev.mcmod.aerocore.api.Orientation;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.WorldBox;
import dev.mcmod.aerocore.api.earthmoving.Blade;
import dev.mcmod.aerocore.api.earthmoving.Bucket;
import dev.mcmod.aerocore.api.earthmoving.Material;
import dev.mcmod.aerocore.api.earthmoving.RockCutting;
import dev.mcmod.aerocore.api.hydraulic.Cylinder;
import dev.mcmod.aerocore.api.vehicle.DesignException;
import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;
import dev.mcmod.aerocore.api.vehicle.MassProperties;
import dev.mcmod.aerocore.api.vehicle.MassShapes;
import dev.mcmod.aerocore.api.vehicle.Mount;
import dev.mcmod.aerocore.api.vehicle.Part;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.part.LinkPart;
import dev.mcmod.hdfv.design.Designs;
import dev.mcmod.hdfv.design.RecordEditor;
import dev.mcmod.hdfv.part.engine.EngineComponent;
import dev.mcmod.hdfv.part.engine.EngineMaterial;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 0.26: a work tool (作業工具): what a machine digs, pushes, carries, breaks, mills, bores, lifts or paves with, one design
 * of many kinds (Kind), compiled to AeroCore's LinkPart (api.vehicle.part): its joint (a curl, tilt, tip, angle, jaw,
 * boom or lift; a fixed zero-range joint for the kinds that have none), the hydraulic ram that drives it (LinkPart.Ram)
 * or a free pin (a dragline's hitch, LinkPart.Free), its cutting tools (Part.Tool), its hold (Part.Hold: "bulk" for
 * material, other names for unit loads), the rotor of a rotating tool (LinkPart.Cutter "cutter", driven by a shaft from a
 * hydraulic motor), its collision spheres, mount points, and a work platform's deck and standing places.
 *
 * <h2>Frames (part frame at q = 0; x forward, y up, z starboard)</h2>
 * <ul>
 * <li>Backhoe and trench buckets and the quick coupler hang below their pin (the origin) with their opening toward -x
 *     (the machine): the curl joint turns them about -z, positive curling the tip toward the machine and up; the lip
 *     cuts along -x. A face-shovel dipper and a loader bucket open forward (+x) behind their pin, curling (racking back)
 *     about +z, the lip cutting along +x. A dragline bucket hangs from its hitch (the origin) open toward the machine
 *     (-x); a clamshell or a grapple from its head's hinge, its moving shell turning about -z (0 closed, negative open:
    positive q closes, as the game's channels do).</li>
 * <li>Blades (dozer, grader, snow plow): the origin at the middle of the cutting edge at ground level, the blade rising
 *     up and back from it, cutting along +x. A dozer's tilt turns about +x through mid-height, an angle blade's angling
 *     about +y; a grader's tip about +z on its circle's arms; a snow plow angles about +y on its pivot behind it.</li>
 * <li>Bodies, bowls and booms: a dump body and a scraper bowl from their rear hinge (the origin) forward along +x, tipping
 *     about +z (positive lifts the front; a bowl's negative lowers its cutting edge); a conveyor from its tail pulley to
 *     its head at +x, elevating about +z; a trencher's boom from its top pivot back along -x about -z (0 level, negative
    lowered into the cut: positive raises).</li>
 * <li>Rotating tools at their axis: a milling drum, a roadheader's (transverse) heads, a bucket wheel, a broom and a snow
 *     blower's auger turn about z, a cutterhead about x (its face at x = 0, the head behind), an auger about y (its tip
 *     at y = -length), a mixer drum about x at its middle.</li>
 * <li>Hanging tools (hammer, pile hammer, plate compactor, quick coupler, auger) below their bracket at the origin; forks
 *     and a work platform's basket forward from their carriage or rotator; a segment erector up from its base (+y, its
 *     lift a sliding joint).</li>
 * </ul>
 *
 * <h2>Parameters</h2>
 * Every number is open; only the unphysical is INVALID, what lies outside the makers' ranges is EXTRAPOLATION. Every
 * number may be blank (RecordEditor.BlankIsAuto); blank means automatic for the heaped capacity (SAE / ISO from the
 * geometry), the mass (estimated), the ram's base pin (placed so the ram's closed and extended lengths are the joint's
 * limits), the hold pressure (HOLD_OVER_RELIEF of the relief) and the rated load of forks (their bending) and of a
 * platform (EN 280 from its floor); any other blank is INVALID. The design screen shows only what the chosen kind uses
 * (RecordEditor.Shown). The meaning of the three sizes by kind:
 * <ul>
 * <li>Buckets (and a bucket wheel's buckets): toolWidthM across, toolLengthM the strike plane from the lip to the back's
 *     top (b), toolHeightM the bowl's depth under it (h); a clamshell's or grapple's length is its closed jaws' span and its
 *     height its shells' hinge-to-lip depth.</li>
 * <li>Blades: width and height; length unused. A ripper: width its beam, height its shanks' penetration, length a
 *     shank's chord (front to back), the plate its shank's thickness (the tip's width).</li>
 * <li>Bodies, bowls, hoppers: inside length, width and side height; conveyors: belt width, length, frame depth; a screed:
 *     its basic width, plate depth (front to back), height; a broom or snow blower: width, hood height, hopper or housing
 *     depth; a milling drum: milling width and greatest depth; a trencher: chain (trench) width, greatest depth, boom
 *     length; hammers: housing width and depth, length with the chisel or helmet.</li>
 * </ul>
 *
 * <h2>Capacity (SAE / ISO)</h2>
 * The bowl's side area inside its strike plane is a parabolic segment on the chord, PROFILE b h (a clamshell's closed
 * shells a half ellipse, pi b h / 4); struck capacity = side area x inner width (Bucket.struckM3, SAE J296 / ISO 7451)
 * and heaped = struck + the heap on the opening (Bucket.heapedM3) at the rating's slope: 1:1 for excavator and shovel
 * buckets (SAE J296, ISO 7451) and scraper bowls (SAE J741), 2:1 for loader buckets (SAE J742, ISO 7546) and haulage
 * bodies (SAE J1363). Dragline buckets and clamshells are rated struck (water level), as their makers rate them. A body's
 * inside is BODY_SHAPE of its box (its sloped front and dual-slope floor), a bowl's BOWL_SHAPE. Blades (bladeCapacityM3):
 * SAE J1265's 0.8 W H^2 (Blade.saeJ1265M3) times the style's factor (STYLE_*: fitted to the Caterpillar Performance
 * Handbook's D6 SU 5.6 m^3 and D8 U 11.7 m^3 against their blades' sizes), a snow plow's loose prism of snow at its
 * repose (Blade.prismM3). A mixer's capacity is MIX_SHARE (ASTM C94's 63 % of the drum's gross volume when mixing) of its
 * double-cone drum, MIXER_SHAPE of its enclosing cylinder.
 *
 * <h2>Mass (rules; makers' parts in SampleTools)</h2>
 * From plate geometry and the material's density: a bucket's wrapper (the parabolic arc of the profile, or the half
 * ellipse's), its two side plates, its lip (LIP_THICKNESS plates, LIP_HEIGHT_SHARE of its depth), wear liners
 * (LINER_SHARE of the wrapper), bosses, ears and top bar (BOSS_* of the shell), and teeth with their adapters (a block of
 * TOOTH_LENGTH widths, each TOOTH_SHARE of the width over teeth + 1 wide). Blades: the curved moldboard (BLADE_ARC of
 * its height), its ribs and back (RIB_*), cutting edge and end bits, SU and U wings (WING_*), trunnions or slides
 * (BRACKET_*). Bodies and bowls: floor, walls, canopy, apron, ejector, their ribs and longitudinals. Rotating tools: the
 * drum's tube and ends, picks with their holders, hood; a cutterhead's face, rim, back structure, disc cutters
 * (DISC_KG at 17 in, as the cube of the diameter) and muck buckets; a bucket wheel's buckets, box rim and hub. A hammer
 * is HAMMER_KG_PER_J of its impact energy (Epiroc HB / SB line), a pile hammer its ram and PILE_HOUSING_SHARE more. The
 * rams the design drives are counted too (ramKg: MachineRules.cylinderKg at the relief, as the links' are); toolKg is
 * the tool without them.
 *
 * <h2>Rams</h2>
 * Cylinders from the base pin on the mount (the part frame at q = 0) to the rod pin on the tool, both in the joint's
 * plane (polar: distance from the joint's origin and angle from +x toward +y for a pin across the machine, from +z toward
 * +y for a tilt about x, from +x toward +z for an angle about y; a sliding joint's base is that far behind the rod pin
 * along its axis). The links' rules (LinkDesign, MachineRules): closed length = stroke + MachineRules.deadLengthM
 * (0.15 m + 3 bores), extended = closed + stroke. The ram must reach the joint's range (its length at both limits within
 * [closed, extended], LinkDesign.RAM_TOLERANCE_M) without passing or nearing its dead centre (the length monotonic over
 * the range, its lever dL/dq at least LinkDesign.MIN_LEVER), or the design is INVALID: a cylinder longer than extended,
 * or one pushed through its toggle, is not a machine. A blank base pin is solved: the two circles about the rod pin at
 * the limits with the closed and extended lengths meet in up to four ways; the one that drives the range with the
 * largest least lever is taken. The circuit is a name (HydraulicPumpDesign.CIRCUITS on the screen; "main" by default).
 *
 * <h2>Tools</h2>
 * Buckets: the lip as Part.Tool.Kind.EDGE between the teeth, each tooth a TOOTH TOOTH_PROJECTION widths ahead of it
 * (the lip's width cut once), depth the bowl's, into "bulk". Blades and moldboards one EDGE along +x their height deep,
 * pushing what they cut ("": the game's heap ahead, "pushed"); a ripper's shanks TOOTH, leaving it loosened (""); a
 * scraper's edge EDGE into "bulk". Mills (MILL) along their axis at the lowest line: a milling drum into its hood's
 * "bulk", a roadheader's heads and a snow blower's auger leaving it (the apron or the chute takes it), a bucket wheel into
 * "bulk", a trencher's chain at its boom's tip (its cut along the travel at the boom's digging angle, "": the spoil
 * auger casts it aside), an auger's two flights at its tip (cutting downward, their rim forces cancelling), a broom's
 * bristles BROOM_DEPTH_M deep into its hopper. A cutterhead's FACE with its discs into "bulk". The other kinds carry no
 * cutting tool; the game works them by their kind (workPoint).
 */
public record WorkToolDesign(String name, Kind toolKind, BladeStyle bladeStyle, double toolWidthM, double toolHeightM, double toolLengthM, double plateM,
                             EngineMaterial material, int teeth, double rakeDeg, double capacityGivenM3, boolean jointed, double jointMinDeg, double jointMaxDeg,
                             double travelM, double ramBoreM, double ramRodM, double ramStrokeM, int ramCount, double ramArmM, double ramArmDeg, double ramBaseM,
                             double ramBaseDeg, double reliefPa, double holdPa, double valveFlowM3PerS, String circuit, double cutterRadiusM, int discs,
                             double discDiameterM, double discTipM, double discSpacingM, double impactEnergyJ, double ramMassKg, double dropM,
                             double beltSpeedMps, double extensionM, double matThicknessM, double centrifugalN, double vibrationRpm, double sprayM3PerS,
                             double ratedLoadKg, double massGivenKg)
        implements PartDesign, RecordEditor.Shown, RecordEditor.ShownOnPage, RecordEditor.BlankIsAuto {
    public static final String TYPE = "hdfv:work_tool";

    /** What the tool is. */
    public enum Kind {
        BACKHOE_BUCKET, LOADER_BUCKET, FACE_SHOVEL_BUCKET, DRAGLINE_BUCKET, CLAMSHELL, TRENCH_BUCKET, DOZER_BLADE, GRADER_MOLDBOARD, SNOW_PLOW, RIPPER,
        HYDRAULIC_HAMMER, GRAPPLE, FORKS, QUICK_COUPLER, COMPACTOR_PLATE, SCRAPER_BOWL, DUMP_BODY, CONVEYOR, PAVER_HOPPER, SCREED, MILLING_DRUM,
        CUTTERHEAD, ROADHEADER_HEAD, BUCKET_WHEEL, TRENCHER_CHAIN, AUGER, PILE_HAMMER, BROOM, SNOW_BLOWER, SPRAY_BAR, MIXER_DRUM, WORK_PLATFORM,
        SEGMENT_ERECTOR
    }

    /** A dozer blade's style: straight, semi-U, universal (U), angle. */
    public enum BladeStyle { S, SU, U, ANGLE }

    /** The hold material goes into: a tool that fills it names it (Part.Tool.hold). */
    public static final String BULK = "bulk";
    /** The mount point a quick coupler carries another tool on. */
    public static final String COUPLER_POINT = "coupler";
    /** The circuit a ram is on when none is named (the pumps' default, HydraulicPumpDesign). */
    public static final String MAIN_CIRCUIT = "main", AUXILIARY_CIRCUIT = "auxiliary";

    // ---------------------------------------------------------------- kinds' groups

    private static Set<Kind> kinds(Kind first, Kind... rest) { return Collections.unmodifiableSet(EnumSet.of(first, rest)); }

    public static final Set<Kind> BUCKETS = kinds(Kind.BACKHOE_BUCKET, Kind.LOADER_BUCKET, Kind.FACE_SHOVEL_BUCKET, Kind.DRAGLINE_BUCKET, Kind.CLAMSHELL,
            Kind.TRENCH_BUCKET);
    public static final Set<Kind> BLADES = kinds(Kind.DOZER_BLADE, Kind.GRADER_MOLDBOARD, Kind.SNOW_PLOW);
    /** The kinds that turn on a rotor "cutter" (LinkPart.Cutter). */
    public static final Set<Kind> ROTATING = kinds(Kind.MILLING_DRUM, Kind.CUTTERHEAD, Kind.ROADHEADER_HEAD, Kind.BUCKET_WHEEL, Kind.TRENCHER_CHAIN, Kind.AUGER,
            Kind.BROOM, Kind.SNOW_BLOWER, Kind.MIXER_DRUM);
    /** The kinds with cutting tools (Part.Tool); the rest the game works by their kind. */
    public static final Set<Kind> CUTTING = kinds(Kind.BACKHOE_BUCKET, Kind.LOADER_BUCKET, Kind.FACE_SHOVEL_BUCKET, Kind.DRAGLINE_BUCKET, Kind.CLAMSHELL,
            Kind.TRENCH_BUCKET, Kind.DOZER_BLADE, Kind.GRADER_MOLDBOARD, Kind.SNOW_PLOW, Kind.RIPPER, Kind.SCRAPER_BOWL, Kind.MILLING_DRUM, Kind.CUTTERHEAD,
            Kind.ROADHEADER_HEAD, Kind.BUCKET_WHEEL, Kind.TRENCHER_CHAIN, Kind.AUGER, Kind.BROOM, Kind.SNOW_BLOWER);
    /** The kinds that may have a joint of their own (jointed). */
    public static final Set<Kind> JOINTABLE = kinds(Kind.BACKHOE_BUCKET, Kind.LOADER_BUCKET, Kind.FACE_SHOVEL_BUCKET, Kind.DRAGLINE_BUCKET, Kind.CLAMSHELL,
            Kind.TRENCH_BUCKET, Kind.DOZER_BLADE, Kind.GRADER_MOLDBOARD, Kind.SNOW_PLOW, Kind.RIPPER, Kind.GRAPPLE, Kind.QUICK_COUPLER, Kind.SCRAPER_BOWL,
            Kind.DUMP_BODY, Kind.CONVEYOR, Kind.TRENCHER_CHAIN, Kind.WORK_PLATFORM, Kind.SEGMENT_ERECTOR);
    /** The kinds whose holds take material ("bulk"). */
    public static final Set<Kind> BULK_KINDS = kinds(Kind.BACKHOE_BUCKET, Kind.LOADER_BUCKET, Kind.FACE_SHOVEL_BUCKET, Kind.DRAGLINE_BUCKET, Kind.CLAMSHELL,
            Kind.TRENCH_BUCKET, Kind.SCRAPER_BOWL, Kind.DUMP_BODY, Kind.CONVEYOR, Kind.PAVER_HOPPER, Kind.MIXER_DRUM, Kind.MILLING_DRUM, Kind.CUTTERHEAD,
            Kind.BUCKET_WHEEL, Kind.AUGER, Kind.BROOM);
    /** Kinds with teeth (buckets' teeth, rippers' shanks, drums' picks, a wheel's buckets, a chain's or an auger's teeth). */
    public static final Set<Kind> TOOTHED = kinds(Kind.BACKHOE_BUCKET, Kind.LOADER_BUCKET, Kind.FACE_SHOVEL_BUCKET, Kind.DRAGLINE_BUCKET, Kind.CLAMSHELL,
            Kind.TRENCH_BUCKET, Kind.RIPPER, Kind.MILLING_DRUM, Kind.ROADHEADER_HEAD, Kind.BUCKET_WHEEL, Kind.TRENCHER_CHAIN, Kind.AUGER);
    /** Kinds whose capacity may be given (blank: from the geometry). */
    public static final Set<Kind> CAPACITY_GIVEN = kinds(Kind.BACKHOE_BUCKET, Kind.LOADER_BUCKET, Kind.FACE_SHOVEL_BUCKET, Kind.DRAGLINE_BUCKET, Kind.CLAMSHELL,
            Kind.TRENCH_BUCKET, Kind.SCRAPER_BOWL, Kind.DUMP_BODY, Kind.PAVER_HOPPER, Kind.MIXER_DRUM, Kind.BUCKET_WHEEL, Kind.BROOM);
    /** Kinds with a rated load. */
    public static final Set<Kind> RATED = kinds(Kind.FORKS, Kind.WORK_PLATFORM, Kind.SEGMENT_ERECTOR);

    // ---------------------------------------------------------------- rules (documented in the class note)

    /** A bowl's side area over b h: a parabolic segment on its strike chord. */
    public static final double PROFILE = 2.0 / 3;
    /** The pin's offset from the bowl's back along the chord, over b. */
    public static final double HINGE_SHARE = 0.15;
    /** A loader bucket's and a dragline bucket's strike plane from the horizontal. */
    public static final double LOADER_CHORD_DEG = 50, DRAGLINE_CHORD_DEG = 50;
    public static final double LIP_HEIGHT_SHARE = 0.2, LIP_THICKNESS = 3, LINER_SHARE = 0.3;
    public static final double BOSS_BACKHOE = 0.15, BOSS_LOADER = 0.15, BOSS_SHOVEL = 0.25, BOSS_DRAGLINE = 0.4, BOSS_CLAMSHELL = 0.5;
    /** Teeth: each TOOTH_SHARE of the width over teeth + 1 wide, a block TOOTH_LENGTH widths long (tip and adapter), TOOTH_PROJECTION widths ahead of the lip. */
    public static final double TOOTH_SHARE = 0.55, TOOTH_LENGTH = 3, TOOTH_PROJECTION = 2;
    /** Heap slopes (rise over run): SAE J296 / ISO 7451 and SAE J741 1:1, SAE J742 / ISO 7546 and SAE J1363 2:1. */
    public static final double HEAP_1_TO_1 = Bucket.SAE_HEAP_SLOPE, HEAP_2_TO_1 = Bucket.CECE_HEAP_SLOPE;
    public static final double BODY_SHAPE = 0.88, BOWL_SHAPE = 0.85, HOPPER_SHAPE = 0.7, BROOM_HOPPER_SHAPE = 0.5;
    public static final double BODY_WALL_SHARE = 0.5, BODY_CANOPY_LENGTH = 0.25, BODY_CANOPY_SHARE = 0.35, BODY_STRUCTURE = 0.35;
    public static final double BOWL_WALL_SHARE = 0.8, BOWL_APRON_ARC = 1.3, BOWL_STRUCTURE = 0.6;
    /** Blades: the moldboard's arc over its height, the cutting edge's height share and thickness (plates), the end bits. */
    public static final double BLADE_ARC = 1.25, EDGE_HEIGHT_SHARE = 0.2, EDGE_THICKNESS = 1.2, END_BITS = 0.15;
    public static final double RIB_DOZER = 0.8, RIB_GRADER = 0.3, RIB_PLOW = 0.5, BRACKET_DOZER = 0.08, BRACKET_GRADER = 0.25, BRACKET_PLOW = 0.4;
    public static final double WING_SU = 0.12, WING_U = 0.2, STYLE_S = 1.0, STYLE_SU = 1.1, STYLE_U = 1.25, STYLE_ANGLE = 0.8;
    /** A ripper's shank over its penetration (the pocket and the curve), its beam's box section and wall (m), the beam's fittings. */
    public static final double SHANK_LENGTH = 1.9, BEAM_SECTION_M = 0.35, BEAM_WALL_M = 0.025, BEAM_FITTINGS = 0.1;
    /** A hydraulic breaker's service mass per joule of impact (Epiroc HB / SB line, 0.5 to 0.6). */
    public static final double HAMMER_KG_PER_J = 0.5;
    /** A pile hammer's housing, cushion and helmet over its ram (impact hammers of 3 to 20 t rams). */
    public static final double PILE_HOUSING_SHARE = 0.8, PILE_EFFICIENCY = 0.85;
    /** A milling drum's picks with holders: reach beyond the tube (m) and mass (kg); a roadheader's. */
    public static final double PICK_REACH_M = 0.11, PICK_KG = 4.0, HEAD_PICK_REACH_M = 0.1, HEAD_PICK_KG = 5.0, HOOD_PLATE_M = 0.016, HEAD_GEARBOX = 0.3;
    /** A 17 in (432 mm) disc cutter with its hub and housing, kg; the face's solid share; the rim's plate share; the back structure; muck buckets. */
    public static final double DISC_KG = 160, DISC_REFERENCE_M = 0.432, FACE_SOLID = 0.7, RIM_PLATE = 0.6, FACE_BACK = 0.5, FACE_BUCKETS = 0.05,
            CUTTERHEAD_MUCK = 0.15;
    /** A bucket wheel's teeth per bucket, its rim's height over the bucket's depth, its hub and spokes over its rim. */
    public static final int WHEEL_BUCKET_TEETH = 5;
    public static final double WHEEL_RIM_HEIGHT = 0.6, WHEEL_HUB = 0.6;
    /** A trencher: boom depth (m), chain kg per m of length and m of width, a tooth (kg), its fittings; its fixed boom's digging angle. */
    public static final double BOOM_DEPTH_M = 0.35, CHAIN_KG_PER_M2 = 130, CHAIN_TOOTH_KG = 2.5, TRENCHER_FITTINGS = 0.2, TRENCHER_DIG_DEG = 60;
    /** An auger: its pipe over the diameter (at least AUGER_PIPE_MIN_M), its flights' pitch over the diameter, a tooth (kg). */
    public static final double AUGER_PIPE = 0.25, AUGER_PIPE_MIN_M = 0.1, AUGER_PITCH = 0.8, AUGER_TOOTH_KG = 3;
    /** A broom's core over its radius, its bristles' packing and density (polypropylene), what it sweeps (m). */
    public static final double BROOM_CORE = 0.4, BRISTLE_PACKING = 0.08, BRISTLE_KG_M3 = 950, BROOM_DEPTH_M = 0.05;
    /** A snow blower's auger, impeller and frame over its housing. */
    public static final double BLOWER_AUGER = 0.6, BLOWER_IMPELLER = 0.8, BLOWER_FRAME = 0.4;
    /** A spray bar's pipe (m), a nozzle every SPRAY_PITCH_M of NOZZLE_KG, its valves and brackets (kg). */
    public static final double SPRAY_PIPE_M = 0.076, SPRAY_PITCH_M = 0.3, NOZZLE_KG = 0.4, SPRAY_FITTINGS_KG = 15;
    /** A mixer drum: its shell's area over pi D L, blades and rings over its shell; its gross volume over its cylinder; ASTM C94's mixing share. */
    public static final double MIXER_TAPER = 0.78, MIXER_BLADES = 0.4, MIXER_RINGS = 0.15, MIXER_SHAPE = 0.62, MIX_SHARE = 0.63;
    /** Forks: a tine's width over its thickness (ISO 2328's 125 x 50), its shank over the backrest's height; ISO 2330's test load over the rating. */
    public static final double TINE_WIDTH = 2.5, TINE_SHANK = 0.5, FORK_TEST_LOAD = 2.5;
    /** A coupler's hooks, wedge and locking cylinder over its frame. */
    public static final double COUPLER_LOCK = 0.25;
    /** A plate compactor's exciter, kg per N of centrifugal force; its mounts over its plate and housing. */
    public static final double EXCITER_KG_PER_N = 0.002, PLATE_MOUNTS = 0.2;
    /** A conveyor: belt thickness (m) and density, idler sets every IDLER_PITCH_M of IDLER_KG_PER_M per m of width (returns half as often, RETURN_KG_PER_M), a pulley's kg per m of width. */
    public static final double BELT_M = 0.012, BELT_KG_M3 = 1150, IDLER_PITCH_M = 1.2, IDLER_KG_PER_M = 25, RETURN_KG_PER_M = 10, PULLEY_KG_PER_M = 150;
    public static final double HOPPER_STRUCTURE = 0.4, HOPPER_FEEDER_KG = 150, SCREED_BODY = 2.5;
    /** EN 280: a person 80 kg, tools at least 40 kg; a person's floor (m^2, rule); a person's hip over the floor when riding (PartVehicle's seat height). */
    public static final double PERSON_KG = 80, PLATFORM_TOOLS_KG = 40, PERSON_AREA_M2 = 0.25, RIDER_HIP_M = 0.72;
    /** A platform's railing tube (48.3 mm diameter, 2.5 mm wall), its rotator and mount over the rest. */
    public static final double RAIL_TUBE_M = 0.048, RAIL_WALL_M = 0.0025, PLATFORM_MOUNT = 0.25;
    /** A segment erector's gripper ribs; its fittings; the segment's thickness envelope (m). */
    public static final double GRIPPER_RIBS = 1.5, ERECTOR_FITTINGS = 1.3, SEGMENT_DEPTH_M = 0.4;
    /** Load-holding (port relief) valves over the main relief: some 10 % (an excavator's 34.3 MPa main, 37.3 MPa line reliefs). */
    public static final double HOLD_OVER_RELIEF = 1.1;
    /** A dragline hitch's friction: 0.15 on a pin of 3 % of the bucket's length, of its weight. */
    public static final double HITCH_FRICTION = 0.0045;
    /** At most this many collision spheres; a ram checked at this many steps over its range (LinkDesign's). */
    public static final int MAX_SPHERES = 24, RAM_STEPS = LinkDesign.RAM_SAMPLES;
    private static final double STEEL_KG_M3 = MachineRules.STEEL_KG_PER_M3, G = MachineRules.G;

    public WorkToolDesign {
        Objects.requireNonNull(name, "name"); Objects.requireNonNull(toolKind, "toolKind"); Objects.requireNonNull(material, "material");
        if (bladeStyle == null) { bladeStyle = BladeStyle.SU; }
        if (circuit == null) { circuit = MAIN_CIRCUIT; }
    }

    /** The excavator bucket (Designs' new design of the kind). */
    public static WorkToolDesign standard() { return of(Kind.BACKHOE_BUCKET); }

    @Override public String type() { return TYPE; }

    /** Its kind (the parameter is toolKind: "kind" names a standard component on the design page). */
    public Kind kind() { return toolKind; }

    public boolean isBucket() { return BUCKETS.contains(toolKind); }
    public boolean isBlade() { return BLADES.contains(toolKind); }
    public boolean rotating() { return ROTATING.contains(toolKind); }
    public boolean cuts() { return CUTTING.contains(toolKind); }
    public boolean jointable() { return JOINTABLE.contains(toolKind); }
    /** Whether it has a working joint (jointed, of a kind that has one). */
    public boolean hasJoint() { return jointable() && jointed; }
    /** Whether a ram drives its joint (every jointed kind but a dragline's free hitch). */
    public boolean rammed() { return hasJoint() && toolKind != Kind.DRAGLINE_BUCKET; }
    /** Whether its joint slides (a segment erector's lift). */
    public boolean prismatic() { return toolKind == Kind.SEGMENT_ERECTOR; }

    // ---------------------------------------------------------------- the screen

    @Override public boolean shows(String parameter) {
        Kind k = toolKind;
        return switch (parameter) {
            case "bladeStyle" -> k == Kind.DOZER_BLADE;
            case "toolWidthM" -> k != Kind.CUTTERHEAD && k != Kind.AUGER && k != Kind.MIXER_DRUM;
            case "toolHeightM" -> k != Kind.SPRAY_BAR && k != Kind.CUTTERHEAD && k != Kind.AUGER && k != Kind.ROADHEADER_HEAD && k != Kind.MIXER_DRUM;
            case "toolLengthM" -> !BLADES.contains(k) && k != Kind.SPRAY_BAR && k != Kind.MILLING_DRUM && k != Kind.ROADHEADER_HEAD;
            case "plateM" -> k != Kind.HYDRAULIC_HAMMER && k != Kind.PILE_HAMMER;
            case "teeth" -> TOOTHED.contains(k);
            case "rakeDeg" -> cuts();
            case "capacityGivenM3" -> CAPACITY_GIVEN.contains(k);
            case "jointed" -> jointable();
            case "jointMinDeg", "jointMaxDeg" -> hasJoint() && !prismatic();
            case "travelM" -> hasJoint() && prismatic();
            case "ramBoreM", "ramRodM", "ramStrokeM", "ramCount", "ramArmM", "ramArmDeg", "ramBaseM", "ramBaseDeg", "reliefPa", "holdPa", "valveFlowM3PerS",
                 "circuit" -> rammed();
            case "cutterRadiusM" -> rotating();
            case "discs", "discDiameterM", "discTipM", "discSpacingM" -> k == Kind.CUTTERHEAD;
            case "impactEnergyJ" -> k == Kind.HYDRAULIC_HAMMER;
            case "ramMassKg", "dropM" -> k == Kind.PILE_HAMMER;
            case "beltSpeedMps" -> k == Kind.CONVEYOR;
            case "extensionM", "matThicknessM" -> k == Kind.SCREED;
            case "centrifugalN", "vibrationRpm" -> k == Kind.COMPACTOR_PLATE;
            case "sprayM3PerS" -> k == Kind.SPRAY_BAR;
            case "ratedLoadKg" -> RATED.contains(k);
            default -> true;
        };
    }

    @Override public boolean shows(String page, String parameter) { return !Designs.DESIGN_PAGE.equals(page) || shows(parameter); }

    // ---------------------------------------------------------------- geometry and capacity

    /** A bucket's strike plane at q = 0: its lip's middle, the direction from the lip to the back's top, the opening's outward normal. */
    public record Chord(Vec3d lip, Vec3d up, Vec3d out) {}

    /** A bucket's strike plane (the bowl, its capacity and its lip are laid out on it; the look draws its profile from it too). */
    public Chord chord() {
        double b = toolLengthM, h = toolHeightM, e = HINGE_SHARE * b;
        switch (toolKind) {
            case FACE_SHOVEL_BUCKET -> { return new Chord(new Vec3d(h + e, -0.65 * b, 0), Vec3d.Y, Vec3d.X); }
            case LOADER_BUCKET -> {
                double c = Math.cos(Math.toRadians(LOADER_CHORD_DEG)), s = Math.sin(Math.toRadians(LOADER_CHORD_DEG));
                Vec3d up = new Vec3d(-c, s, 0), out = new Vec3d(s, c, 0);
                // the hinge pin a hinge offset behind the bowl's deepest point
                return new Chord(new Vec3d(0.5 * b * c, -0.5 * b * s, 0).add(out.scale(h + e)), up, out);
            }
            case DRAGLINE_BUCKET -> {
                double c = Math.cos(Math.toRadians(DRAGLINE_CHORD_DEG)), s = Math.sin(Math.toRadians(DRAGLINE_CHORD_DEG));
                Vec3d up = new Vec3d(c, s, 0), out = new Vec3d(-s, c, 0);
                // the bowl's centroid under the hitch, its back's top 0.1 b below it
                double cy = 0.5 * b * s + 0.4 * h * c + 0.1 * b;
                return new Chord(new Vec3d(-0.5 * b * c - 0.4 * h * s, -cy - 0.5 * b * s + 0.4 * h * c, 0), up, out);
            }
            default -> { return new Chord(new Vec3d(0, -(b + e), 0), Vec3d.Y, Vec3d.X.scale(-1)); }   // backhoe, trench (and a wheel's buckets)
        }
    }

    /** The bowl's centroid (a bucket's; part frame at q = 0). */
    private Vec3d bowlCentre() {
        if (toolKind == Kind.CLAMSHELL || toolKind == Kind.GRAPPLE) { return new Vec3d(0, -4 * toolHeightM / (3 * Math.PI), 0); }
        Chord c = chord();
        return c.lip().add(c.up().scale(0.5 * toolLengthM)).subtract(c.out().scale(0.4 * toolHeightM));
    }

    /** The direction a bucket's lip cuts in (part frame): toward its open side as it curls or is crowded or dragged. */
    private Vec3d lipCut() {
        return switch (toolKind) {
            case BACKHOE_BUCKET, TRENCH_BUCKET, DRAGLINE_BUCKET, CLAMSHELL -> Vec3d.X.scale(-1);
            default -> Vec3d.X;
        };
    }

    /** The inside width, m: a bucket's or bowl's between its side plates, a body's between its walls. */
    public double innerWidthM() {
        return switch (toolKind) {
            case BACKHOE_BUCKET, LOADER_BUCKET, FACE_SHOVEL_BUCKET, DRAGLINE_BUCKET, CLAMSHELL, TRENCH_BUCKET, BUCKET_WHEEL, GRAPPLE -> toolWidthM - 2 * plateM;
            case DUMP_BODY -> toolWidthM - 2 * BODY_WALL_SHARE * plateM;
            case SCRAPER_BOWL -> toolWidthM - 2 * BOWL_WALL_SHARE * plateM;
            default -> toolWidthM;
        };
    }

    /** The heap's slope of its rating (0: rated struck). */
    public double heapSlope() {
        return switch (toolKind) {
            case BACKHOE_BUCKET, TRENCH_BUCKET, FACE_SHOVEL_BUCKET, SCRAPER_BOWL -> HEAP_1_TO_1;
            case LOADER_BUCKET, DUMP_BODY -> HEAP_2_TO_1;
            default -> 0;
        };
    }

    /** Struck capacity, m^3 (a bucket wheel's: one bucket's; a mixer's: what it mixes). */
    public double struckM3() {
        double w = Math.max(innerWidthM(), 0), b = Math.max(toolLengthM, 0), h = Math.max(toolHeightM, 0);
        return switch (toolKind) {
            case BACKHOE_BUCKET, TRENCH_BUCKET, FACE_SHOVEL_BUCKET, LOADER_BUCKET, DRAGLINE_BUCKET, BUCKET_WHEEL -> Bucket.struckM3(PROFILE * b * h, w);
            case CLAMSHELL -> Bucket.struckM3(Math.PI / 4 * b * h, w);
            case DUMP_BODY -> b * w * h * BODY_SHAPE;
            case SCRAPER_BOWL -> b * w * h * BOWL_SHAPE;
            case PAVER_HOPPER -> toolWidthM * b * h * HOPPER_SHAPE;
            case BROOM -> toolWidthM * b * h * BROOM_HOPPER_SHAPE;
            case MIXER_DRUM -> MIX_SHARE * drumVolumeM3();
            default -> 0;
        };
    }

    /** Heaped capacity at its rating's slope, m^3 (struck when rated struck). */
    public double heapedM3() {
        double s = struckM3(), slope = heapSlope();
        if (slope == 0) { return s; }
        return Bucket.heapedM3(s, Math.max(toolLengthM, 0), Math.max(innerWidthM(), 0), slope);
    }

    /** A mixer drum's gross (geometric) volume, m^3: MIXER_SHAPE of its enclosing cylinder. */
    public double drumVolumeM3() { return toolKind == Kind.MIXER_DRUM ? MIXER_SHAPE * Math.PI * cutterRadiusM * cutterRadiusM * toolLengthM : 0; }

    /** A conveyor's load cross-section, m^2: CEMA's 0.1 (0.9 b - 0.05)^2 for 35 deg troughing and a 20 deg surcharge. */
    public double beltAreaM2() {
        double w = 0.9 * toolWidthM - 0.05;
        return toolKind == Kind.CONVEYOR && w > 0 ? 0.1 * w * w : 0;
    }

    /**
     * What it holds, m^3: the given capacity, or heaped (buckets, bowls and bodies at their slope), struck (draglines,
     * clamshells, hoppers, a wheel's bucket), a mixer's nominal (MIX_SHARE of its drum), a conveyor's belt load, a
     * milling drum's hood, a cutterhead's muck buckets, an auger's flights half full, a broom's hopper, a unit load's
     * envelope; 0 for the kinds that hold nothing.
     */
    public double capacityM3() {
        if (CAPACITY_GIVEN.contains(toolKind) && !Double.isNaN(capacityGivenM3)) { return capacityGivenM3; }
        double r = cutterRadiusM;
        return switch (toolKind) {
            case BACKHOE_BUCKET, TRENCH_BUCKET, FACE_SHOVEL_BUCKET, LOADER_BUCKET, SCRAPER_BOWL, DUMP_BODY -> heapedM3();
            case DRAGLINE_BUCKET, CLAMSHELL, BUCKET_WHEEL, PAVER_HOPPER, MIXER_DRUM, BROOM -> struckM3();
            case CONVEYOR -> beltAreaM2() * toolLengthM;
            case MILLING_DRUM -> {
                double tube = r - PICK_REACH_M;
                yield Math.max(toolWidthM * (2 * r) * (1.2 * r) - Math.PI * tube * tube * toolWidthM, 0);
            }
            case CUTTERHEAD -> CUTTERHEAD_MUCK * Math.PI * r * r * toolLengthM;
            case AUGER -> {
                double d = 2 * r, pipe = augerPipeM();
                yield 0.5 * Math.PI / 4 * Math.max(d * d - pipe * pipe, 0) * toolLengthM;
            }
            case FORKS -> toolWidthM * toolLengthM * toolHeightM;
            case GRAPPLE -> Math.PI / 4 * toolLengthM * toolHeightM * Math.max(innerWidthM(), 0);
            case SEGMENT_ERECTOR -> toolWidthM * toolLengthM * SEGMENT_DEPTH_M;
            default -> 0;
        };
    }

    /** The hold's volume, m^3: capacityM3, a bucket wheel's rising half of its buckets. */
    public double holdM3() { return toolKind == Kind.BUCKET_WHEEL ? 0.5 * Math.max(teeth, 0) * capacityM3() : capacityM3(); }

    /** The hold's name: "bulk" for material, "pallet", "load", "segment" for unit loads; null for none. */
    public String holdName() {
        if (BULK_KINDS.contains(toolKind)) { return BULK; }
        return switch (toolKind) { case FORKS -> "pallet"; case GRAPPLE -> "load"; case SEGMENT_ERECTOR -> "segment"; default -> null; };
    }

    private Vec3d holdCentre() {
        double h = toolHeightM, l = toolLengthM, r = cutterRadiusM;
        return switch (toolKind) {
            case BACKHOE_BUCKET, TRENCH_BUCKET, FACE_SHOVEL_BUCKET, LOADER_BUCKET, DRAGLINE_BUCKET, CLAMSHELL, GRAPPLE -> bowlCentre();
            case SCRAPER_BOWL, DUMP_BODY, PAVER_HOPPER -> new Vec3d(0.5 * l, 0.5 * h, 0);
            case CONVEYOR -> new Vec3d(0.5 * l, 0.1, 0);
            case MIXER_DRUM -> new Vec3d(0, -0.3 * r, 0);
            case CUTTERHEAD -> new Vec3d(-0.5 * l, 0, 0);
            case BUCKET_WHEEL -> new Vec3d(0, 0.3 * r, 0);
            case AUGER -> new Vec3d(0, -0.5 * l, 0);
            case BROOM -> new Vec3d(-r - 0.5 * l, 0.5 * h - r, 0);
            case FORKS -> new Vec3d(0.5 * l, 0.5 * h, 0);
            case SEGMENT_ERECTOR -> new Vec3d(0, h + 0.5 * SEGMENT_DEPTH_M, 0);
            default -> Vec3d.ZERO;
        };
    }

    /** A dozer blade's capacity over SAE J1265's straight blade (STYLE_*). */
    public double styleFactor() {
        return switch (bladeStyle) { case S -> STYLE_S; case SU -> STYLE_SU; case U -> STYLE_U; case ANGLE -> STYLE_ANGLE; };
    }

    /** A dozer blade's wings, each a share of its width (WING_*: SU and U blades). */
    private double wingShare() {
        if (toolKind != Kind.DOZER_BLADE) { return 0; }
        return switch (bladeStyle) { case SU -> WING_SU; case U -> WING_U; default -> 0; };
    }

    /** A blade's capacity, m^3: SAE J1265's 0.8 W H^2 times the style's factor (a grader's plain), a snow plow's prism of snow at its repose; 0 for the rest. */
    public double bladeCapacityM3() {
        return switch (toolKind) {
            case DOZER_BLADE -> Blade.saeJ1265M3(toolWidthM, toolHeightM) * styleFactor();
            case GRADER_MOLDBOARD -> Blade.saeJ1265M3(toolWidthM, toolHeightM);
            case SNOW_PLOW -> Blade.prismM3(toolWidthM, toolHeightM, Material.SNOW.reposeRad());
            default -> 0;
        };
    }

    /** A tooth's width, m: TOOTH_SHARE of the width over teeth + 1; a ripper's its shank's thickness; 0 without teeth. */
    public double toothWidthM() {
        if (teeth <= 0) { return 0; }
        return toolKind == Kind.RIPPER ? plateM : TOOTH_SHARE * toolWidthM / (teeth + 1);
    }

    private double augerPipeM() { return Math.max(AUGER_PIPE * 2 * cutterRadiusM, AUGER_PIPE_MIN_M); }

    /** A screed's paving width, m: its basic width and both extensions. */
    public double pavingWidthM() { return toolKind == Kind.SCREED ? toolWidthM + 2 * extensionM : 0; }

    /** A work platform's floor, m^2. */
    public double deckAreaM2() { return toolKind == Kind.WORK_PLATFORM ? toolWidthM * toolLengthM : 0; }

    /** The rated load, kg: given, or forks' by ISO 2330's bending rule, a platform's by EN 280 from its floor; 0 for the kinds without one. */
    public double ratedKg() {
        if (!RATED.contains(toolKind)) { return 0; }
        if (!Double.isNaN(ratedLoadKg)) { return ratedLoadKg; }
        return switch (toolKind) {
            case FORKS -> forkRatingKg();
            case WORK_PLATFORM -> PERSON_KG * Math.floor(deckAreaM2() / PERSON_AREA_M2) + PLATFORM_TOOLS_KG;
            default -> 0;
        };
    }

    /**
     * What a pair of forks carries at a load centre of half their length, kg: each tine (TINE_WIDTH thicknesses wide) at
     * its heel at the material's yield over ISO 2330's test load, sigma = M / (b t^2 / 6).
     */
    public double forkRatingKg() {
        if (toolKind != Kind.FORKS || !(toolLengthM > 0)) { return 0; }
        double t = plateM, z = TINE_WIDTH * t * t * t / 6;
        return 2 * (material.yieldPa() / FORK_TEST_LOAD) * z / (G * 0.5 * toolLengthM);
    }

    /** How many people a platform carries (EN 280: 80 kg each over 40 kg of tools, within its floor). */
    public int persons() {
        if (toolKind != Kind.WORK_PLATFORM) { return 0; }
        double byLoad = Math.floor((ratedKg() - PLATFORM_TOOLS_KG) / PERSON_KG), byFloor = Math.floor(deckAreaM2() / PERSON_AREA_M2);
        return (int) Math.max(0, Math.min(byLoad, byFloor));
    }

    // ---------------------------------------------------------------- mass

    private static double asinh(double x) { return Math.log(x + Math.sqrt(x * x + 1)); }

    /** The arc of a parabola on a chord b with a sagitta h, m. */
    static double parabolaArcM(double b, double h) {
        if (!(b > 0)) { return 0; }
        double p = 4 * h / b;
        if (p < 1e-9) { return b; }
        return 0.5 * b * (Math.sqrt(1 + p * p) + asinh(p) / p);
    }

    /** Half the perimeter of an ellipse of semi-axes a and c (Ramanujan), m. */
    static double halfEllipseM(double a, double c) { return Math.PI / 2 * (3 * (a + c) - Math.sqrt((3 * a + c) * (a + 3 * c))); }

    private double bossShare() {
        return switch (toolKind) {
            case LOADER_BUCKET -> BOSS_LOADER; case FACE_SHOVEL_BUCKET -> BOSS_SHOVEL; case DRAGLINE_BUCKET -> BOSS_DRAGLINE; case CLAMSHELL -> BOSS_CLAMSHELL;
            default -> BOSS_BACKHOE;
        };
    }

    /** A bucket's pieces (kg) into m, with a prefix; its teeth if any. */
    private void bucketPieces(Map<String, Double> m, String prefix, int teethCount) {
        double rho = material.densityKgPerM3(), w = toolWidthM, b = toolLengthM, h = toolHeightM, t = plateM;
        boolean clam = toolKind == Kind.CLAMSHELL;
        double side = clam ? Math.PI * b * h / 4 : PROFILE * b * h, arc = clam ? halfEllipseM(0.5 * b, h) : parabolaArcM(b, h);
        double wrapper = arc * w * t * rho, sides = 2 * side * t * rho, lip = (clam ? 2 : 1) * w * LIP_HEIGHT_SHARE * h * LIP_THICKNESS * t * rho;
        double liners = LINER_SHARE * wrapper, shell = wrapper + sides + lip + liners;
        m.merge(prefix + "wrapper", wrapper, Double::sum);
        m.merge(prefix + "side_plates", sides, Double::sum);
        m.merge(prefix + "lip", lip, Double::sum);
        m.merge(prefix + "wear_liners", liners, Double::sum);
        m.merge(prefix + (clam ? "head" : "bosses"), (toolKind == Kind.BUCKET_WHEEL ? BOSS_BACKHOE : bossShare()) * shell, Double::sum);
        if (teethCount > 0) {
            double tw = TOOTH_SHARE * w / (teethCount + 1);
            m.merge(prefix + "teeth", teethCount * rho * TOOTH_LENGTH * tw * tw * tw, Double::sum);
        }
    }

    /**
     * The tool's pieces and their masses, kg, in order (the workpieces PartPlanner makes), the rams included as
     * "tool_ram": estimated, scaled to the given mass when there is one.
     */
    public Map<String, Double> massBreakdownKg() {
        Map<String, Double> m = estimatedPiecesKg();
        if (!Double.isNaN(massGivenKg)) {
            double sum = m.values().stream().mapToDouble(Double::doubleValue).sum();
            if (sum > 0) { m.replaceAll((k, v) -> v * massGivenKg / sum); }
        }
        return m;
    }

    /** The pieces' estimated masses, kg (massBreakdownKg before a given mass). */
    private Map<String, Double> estimatedPiecesKg() {
        Map<String, Double> m = new LinkedHashMap<>();
        double rho = material.densityKgPerM3(), t = plateM, w = toolWidthM, h = toolHeightM, l = toolLengthM, r = cutterRadiusM;
        switch (toolKind) {
            case BACKHOE_BUCKET, LOADER_BUCKET, FACE_SHOVEL_BUCKET, DRAGLINE_BUCKET, CLAMSHELL, TRENCH_BUCKET -> bucketPieces(m, "tool_", teeth);
            case DOZER_BLADE, GRADER_MOLDBOARD, SNOW_PLOW -> {
                double rib = switch (toolKind) { case DOZER_BLADE -> RIB_DOZER; case GRADER_MOLDBOARD -> RIB_GRADER; default -> RIB_PLOW; };
                double bracket = switch (toolKind) { case DOZER_BLADE -> BRACKET_DOZER; case GRADER_MOLDBOARD -> BRACKET_GRADER; default -> BRACKET_PLOW; };
                double wing = wingShare();
                double mold = w * BLADE_ARC * h * t * rho, ribs = rib * mold, wings = 2 * wing * w * h * t * rho * (1 + rib);
                m.put("tool_moldboard", mold);
                m.put("tool_ribs", ribs);
                m.put("tool_cutting_edge", w * EDGE_HEIGHT_SHARE * h * EDGE_THICKNESS * t * rho * (1 + END_BITS));
                if (wings > 0) { m.put("tool_wings", wings); }
                m.put("tool_brackets", bracket * (mold + ribs + wings));
            }
            case RIPPER -> {
                int n = Math.max(teeth, 0);
                m.put("tool_shanks", n * SHANK_LENGTH * h * l * t * rho);
                m.put("tool_tips", n * rho * TOOTH_LENGTH * t * t * t);
                m.put("tool_beam", 4 * BEAM_SECTION_M * BEAM_WALL_M * w * rho * (1 + BEAM_FITTINGS));
            }
            case HYDRAULIC_HAMMER -> {
                double kg = HAMMER_KG_PER_J * impactEnergyJ;
                m.put("tool_power_cell", 0.7 * kg);
                m.put("tool_housing", 0.25 * kg);
                m.put("tool_chisel", 0.05 * kg);
            }
            case PILE_HAMMER -> {
                m.put("tool_drop_weight", ramMassKg);
                m.put("tool_housing", 0.7 * PILE_HOUSING_SHARE * ramMassKg);
                m.put("tool_helmet", 0.3 * PILE_HOUSING_SHARE * ramMassKg);
            }
            case GRAPPLE -> {
                double jaws = 2 * w * 1.3 * h * t * rho * 0.8, ribs = 0.5 * jaws;
                m.put("tool_jaws", jaws);
                m.put("tool_ribs", ribs);
                m.put("tool_head", 0.5 * (jaws + ribs));
            }
            case FORKS -> {
                double tine = 2 * (TINE_WIDTH * t) * t * (l + TINE_SHANK * h) * rho;
                double bars = 2 * w * 0.15 * 0.8 * t * rho, uprights = 3 * h * 0.1 * 0.8 * t * rho, backrest = w * h * 0.006 * 0.3 * rho;
                m.put("tool_tines", tine);
                m.put("tool_carriage", (bars + uprights + backrest) * 1.15);
            }
            case QUICK_COUPLER -> {
                double frame = (2 * l * h * t + 2 * l * w * 0.8 * t) * rho;
                m.put("tool_frame", frame);
                m.put("tool_lock", COUPLER_LOCK * frame);
            }
            case COMPACTOR_PLATE -> {
                double base = w * l * t * rho, housing = (2 * w * l + 2 * (w + l) * h) * 0.3 * t * rho;
                m.put("tool_base_plate", base);
                m.put("tool_housing", housing);
                m.put("tool_exciter", EXCITER_KG_PER_N * centrifugalN);
                m.put("tool_mounts", PLATE_MOUNTS * (base + housing));
            }
            case SCRAPER_BOWL -> {
                double floor = l * w * t * rho, sides = 2 * l * h * BOWL_WALL_SHARE * t * rho, apron = w * BOWL_APRON_ARC * h * BOWL_WALL_SHARE * t * rho;
                double ejector = w * h * t * rho, edge = w * 0.3 * 2 * t * rho, sum = floor + sides + apron + ejector + edge;
                m.put("tool_floor", floor);
                m.put("tool_side_plates", sides);
                m.put("tool_apron", apron);
                m.put("tool_ejector", ejector);
                m.put("tool_cutting_edge", edge);
                m.put("tool_structure", BOWL_STRUCTURE * sum);
            }
            case DUMP_BODY -> {
                double floor = l * w * t * rho, walls = (2 * l * h + w * h) * BODY_WALL_SHARE * t * rho;
                double canopy = w * BODY_CANOPY_LENGTH * l * BODY_CANOPY_SHARE * t * rho;
                m.put("tool_floor", floor);
                m.put("tool_walls", walls);
                m.put("tool_canopy", canopy);
                m.put("tool_structure", BODY_STRUCTURE * (floor + walls + canopy));
            }
            case CONVEYOR -> {
                double sets = l / IDLER_PITCH_M;
                m.put("tool_belt", 2 * l * 1.1 * w * BELT_M * BELT_KG_M3);
                m.put("tool_idlers", sets * IDLER_KG_PER_M * w + 0.5 * sets * RETURN_KG_PER_M * w);
                m.put("tool_frame", (2 * l * h * t + sets * w * 0.1 * t) * rho);
                m.put("tool_pulleys", 2 * PULLEY_KG_PER_M * w);
            }
            case PAVER_HOPPER -> {
                double shell = (w * l + 2 * l * h + w * h) * t * rho;
                m.put("tool_hopper_shell", shell);
                m.put("tool_structure", HOPPER_STRUCTURE * shell);
                m.put("tool_feeder", HOPPER_FEEDER_KG);
            }
            case SCREED -> {
                double width = w + 2 * extensionM;
                m.put("tool_screed_plate", width * l * t * rho);
                m.put("tool_screed_body", width * 2 * (l + h) * 0.6 * t * rho * SCREED_BODY);
            }
            case MILLING_DRUM, ROADHEADER_HEAD -> {
                boolean mill = toolKind == Kind.MILLING_DRUM;
                double ro = r - (mill ? PICK_REACH_M : HEAD_PICK_REACH_M), ri = Math.max(ro - t, 0);
                double tube = Math.PI * (ro * ro - ri * ri) * w * rho, ends = 2 * Math.PI * ri * ri * t * rho;
                m.put("tool_drum_tube", tube);
                m.put("tool_drum_ends", ends);
                m.put("tool_picks", Math.max(teeth, 0) * (mill ? PICK_KG : HEAD_PICK_KG));
                if (mill) { m.put("tool_hood", (2 * (2 * r) * (1.2 * r) + w * Math.PI * r) * HOOD_PLATE_M * STEEL_KG_M3); }
                else { m.put("tool_gearbox", HEAD_GEARBOX * (tube + ends)); }
            }
            case CUTTERHEAD -> {
                double face = Math.PI * r * r * t * rho * FACE_SOLID;
                m.put("tool_face", face);
                m.put("tool_rim", 2 * Math.PI * r * l * RIM_PLATE * t * rho);
                m.put("tool_back_structure", FACE_BACK * face);
                m.put("tool_disc_cutters", discs * DISC_KG * Math.pow(discDiameterM / DISC_REFERENCE_M, 3));
                m.put("tool_muck_buckets", FACE_BUCKETS * face);
            }
            case BUCKET_WHEEL -> {
                int n = Math.max(teeth, 0);
                Map<String, Double> one = new LinkedHashMap<>();
                bucketPieces(one, "", WHEEL_BUCKET_TEETH);
                double each = one.values().stream().mapToDouble(Double::doubleValue).sum(), rim = 2 * Math.PI * (r - h) * 2 * (w + WHEEL_RIM_HEIGHT * h) * t * rho;
                m.put("tool_buckets", n * each);
                m.put("tool_rim", rim);
                m.put("tool_hub", WHEEL_HUB * rim);
            }
            case TRENCHER_CHAIN -> {
                double boom = 2 * (w + BOOM_DEPTH_M) * l * t * rho, chain = 2 * l * CHAIN_KG_PER_M2 * w, teethKg = Math.max(teeth, 0) * CHAIN_TOOTH_KG;
                double sprockets = 2 * Math.PI * r * r * 0.05 * STEEL_KG_M3 * 0.4;
                m.put("tool_boom", boom);
                m.put("tool_chain", chain);
                m.put("tool_chain_teeth", teethKg);
                m.put("tool_sprockets", sprockets);
                m.put("tool_fittings", TRENCHER_FITTINGS * (boom + chain + teethKg + sprockets));
            }
            case AUGER -> {
                double d = 2 * r, pipe = augerPipeM(), pitch = AUGER_PITCH * d, turns = l / pitch;
                double area = Math.PI / 4 * Math.max(d * d - pipe * pipe, 0) * Math.sqrt(1 + Math.pow(pitch / (Math.PI * 0.5 * (d + pipe)), 2));
                double flights = turns * area * t * rho, pipeKg = Math.PI * pipe * t * l * rho, bit = Math.max(teeth, 0) * AUGER_TOOTH_KG + 0.1 * flights;
                m.put("tool_auger_pipe", pipeKg);
                m.put("tool_flights", flights);
                m.put("tool_bit", bit);
                m.put("tool_kelly_box", 0.05 * (pipeKg + flights + bit));
            }
            case BROOM -> {
                double rc = BROOM_CORE * r, core = Math.PI * 2 * rc * w * 0.005 * STEEL_KG_M3, bristles = Math.PI * (r * r - rc * rc) * w * BRISTLE_PACKING * BRISTLE_KG_M3;
                double hood = (w * (Math.PI * r + 2 * l) + 2 * (2 * r) * h) * t * rho;
                m.put("tool_broom_core", core);
                m.put("tool_bristles", bristles);
                m.put("tool_hood", hood);
                m.put("tool_frame", 0.3 * (core + bristles + hood));
            }
            case SNOW_BLOWER -> {
                double housing = (w * 1.2 * h + 2 * l * h) * t * rho;
                m.put("tool_housing", housing);
                m.put("tool_auger", BLOWER_AUGER * housing);
                m.put("tool_impeller", BLOWER_IMPELLER * housing);
                m.put("tool_frame", BLOWER_FRAME * housing);
            }
            case SPRAY_BAR -> {
                m.put("tool_spray_pipe", Math.PI * SPRAY_PIPE_M * t * w * rho);
                m.put("tool_nozzles", w / SPRAY_PITCH_M * NOZZLE_KG);
                m.put("tool_fittings", SPRAY_FITTINGS_KG);
            }
            case MIXER_DRUM -> {
                double shell = Math.PI * 2 * r * l * MIXER_TAPER * t * rho;
                m.put("tool_drum_shell", shell);
                m.put("tool_mixing_blades", MIXER_BLADES * shell);
                m.put("tool_drum_rings", MIXER_RINGS * shell);
            }
            case WORK_PLATFORM -> {
                double floor = w * l * t * rho, tube = rho * Math.PI * RAIL_TUBE_M * RAIL_WALL_M;
                double rails = (3 * 2 * (w + l) + (4 + 2 * Math.floor(w)) * h) * tube;
                m.put("tool_floor", floor);
                m.put("tool_railing", rails);
                m.put("tool_rotator_mount", PLATFORM_MOUNT * (floor + rails));
            }
            case SEGMENT_ERECTOR -> {
                double grip = w * l * t * rho * GRIPPER_RIBS, frame = 2 * (w * l + w * h + l * h) * 0.5 * t * rho;
                m.put("tool_gripper", grip);
                m.put("tool_frame", frame * ERECTOR_FITTINGS + (ERECTOR_FITTINGS - 1) * grip);
            }
        }
        if (rammed()) { m.put("tool_ram", ramKg()); }
        return m;
    }

    /** The rams' mass, kg: MachineRules.cylinderKg at the relief, each (the links' rule). */
    public double ramKg() {
        if (!rammed() || !(ramBoreM > 0) || !(ramStrokeM > 0) || !(ramRodM > 0) || !MachineRules.finite(ramBoreM, ramRodM, ramStrokeM, reliefPa)) { return 0; }
        return Math.max(ramCount, 0) * MachineRules.cylinderKg(ramBoreM, ramRodM, ramStrokeM, reliefPa);
    }

    /** The tool's own estimated mass without its rams, kg. */
    public double toolKg() {
        double sum = 0;
        for (Map.Entry<String, Double> e : estimatedPiecesKg().entrySet()) { if (!e.getKey().equals("tool_ram")) { sum += e.getValue(); } }
        return sum;
    }

    /** The whole part's mass, kg: given, or the tool and its rams estimated. */
    public double massKg() {
        if (!Double.isNaN(massGivenKg)) { return massGivenKg; }
        return estimatedPiecesKg().values().stream().mapToDouble(Double::doubleValue).sum();
    }

    // ---------------------------------------------------------------- joint and ram

    /**
     * The joint's axis (part frame), positive as the game's channels move (curling in, closing, raising): a backhoe's curl
     * and a clamshell's or grapple's closing (q 0 closed, negative open) and a trencher's boom (0 level, negative down)
     * about -z.
     */
    public Vec3d jointAxis() {
        return switch (toolKind) {
            case BACKHOE_BUCKET, TRENCH_BUCKET, QUICK_COUPLER, CLAMSHELL, GRAPPLE, TRENCHER_CHAIN -> Vec3d.Z.scale(-1);
            case DOZER_BLADE -> bladeStyle == BladeStyle.ANGLE ? Vec3d.Y : Vec3d.X;
            case SNOW_PLOW, WORK_PLATFORM, SEGMENT_ERECTOR -> Vec3d.Y;
            default -> Vec3d.Z;
        };
    }

    /** The joint's origin (part frame; a sliding joint's is the origin). */
    public Vec3d jointOrigin() {
        return switch (toolKind) {
            case DOZER_BLADE -> bladeStyle == BladeStyle.ANGLE ? Vec3d.ZERO : new Vec3d(0, 0.5 * toolHeightM, 0);
            case GRADER_MOLDBOARD -> new Vec3d(-0.2, 0.6 * toolHeightM, 0);
            case SNOW_PLOW -> new Vec3d(-0.4, 0.3, 0);
            default -> Vec3d.ZERO;
        };
    }

    private double jointLow() { return prismatic() ? 0 : Math.toRadians(jointMinDeg); }
    private double jointHigh() { return prismatic() ? travelM : Math.toRadians(jointMaxDeg); }

    /** The compiled joint: the kind's working joint, or a fixed one of no range (held). */
    public Part.Joint joint() {
        if (!hasJoint()) { return Part.Joint.revolute(Vec3d.ZERO, Vec3d.Z, 0, 0, 0); }
        double lo = jointLow(), hi = jointHigh(), initial = Math.clamp(0, lo, hi);
        return prismatic() ? Part.Joint.prismatic(jointAxis(), lo, hi, initial) : Part.Joint.revolute(jointOrigin(), jointAxis(), lo, hi, initial);
    }

    /** The plane's axes a pin's polar position is given in: (x, y) about z, (z, y) about x, (x, z) about y. */
    private static Vec3d[] plane(Vec3d axis) {
        if (Math.abs(axis.z()) > 0.5) { return new Vec3d[]{Vec3d.X, Vec3d.Y}; }
        if (Math.abs(axis.x()) > 0.5) { return new Vec3d[]{Vec3d.Z, Vec3d.Y}; }
        return new Vec3d[]{Vec3d.X, Vec3d.Z};
    }

    private Vec3d inPlane(double distanceM, double deg) {
        Vec3d[] e = plane(jointAxis());
        double a = Math.toRadians(deg);
        return jointOrigin().add(e[0].scale(distanceM * Math.cos(a))).add(e[1].scale(distanceM * Math.sin(a)));
    }

    /** The ram's rod pin on the tool (part frame at q = 0). */
    public Vec3d rodPin() { return inPlane(ramArmM, ramArmDeg); }

    /** Where the joint at q puts a point of the tool's frame (as LinkPart.moved). */
    public Vec3d moved(Vec3d p, double q) {
        if (prismatic()) { return p.add(jointAxis().scale(q)); }
        Vec3d o = jointOrigin();
        return o.add(Orientation.rotationVector(jointAxis().scale(q)).rotate(p.subtract(o)));
    }

    /** The ram's length closed, m: its stroke and MachineRules' dead length (the links' rule). */
    public double ramClosedM() { return MachineRules.closedLengthM(ramBoreM, ramStrokeM); }
    /** The ram's length fully extended, m. */
    public double ramExtendedM() { return ramClosedM() + ramStrokeM; }
    /** The load-holding valves' pressure, Pa: given, or HOLD_OVER_RELIEF of the relief. */
    public double holdPressurePa() { return Double.isNaN(holdPa) ? HOLD_OVER_RELIEF * reliefPa : holdPa; }
    /** The rams as one Cylinder (api.hydraulic). */
    public Cylinder cylinder() { return new Cylinder(ramBoreM, ramRodM, ramStrokeM, ramCount); }

    /** Whether the base pin is solved (blank). */
    public boolean autoBase() { return Double.isNaN(ramBaseM) || (!prismatic() && Double.isNaN(ramBaseDeg)); }

    /**
     * A ram's pins (part frame at q = 0; the base is on the mount), its closed and extended lengths, its least lever over
     * the range (m per rad or m per m) and whether extending it moves the joint up.
     */
    public record RamGeometry(Vec3d basePin, Vec3d rodPin, double closedM, double extendedM, double leastLeverM, boolean extendsUp) {}

    private double[] lengths(Vec3d base, Vec3d rod, double lo, double hi) {
        double[] out = new double[RAM_STEPS + 1];
        for (int i = 0; i <= RAM_STEPS; i++) { out[i] = moved(rod, lo + (hi - lo) * i / RAM_STEPS).subtract(base).length(); }
        return out;
    }

    /**
     * "reach" when the lengths leave the cylinder's, "centre" when they turn back or the lever falls below LinkDesign's
     * MIN_LEVER (at or near a dead centre), null when the ram drives the range (the links' rules).
     */
    private String ramProblem(double[] ls, double lo, double hi) {
        boolean up = ls[ls.length - 1] > ls[0];
        double step = (hi - lo) / (ls.length - 1);
        for (int i = 0; i + 1 < ls.length; i++) {
            double d = ls[i + 1] - ls[i];
            if (!(up ? d > 0 : d < 0) || Math.abs(d) / step < LinkDesign.MIN_LEVER) { return "centre"; }
        }
        double least = Math.min(ls[0], ls[ls.length - 1]), most = Math.max(ls[0], ls[ls.length - 1]);
        if (least < ramClosedM() - LinkDesign.RAM_TOLERANCE_M || most > ramExtendedM() + LinkDesign.RAM_TOLERANCE_M) { return "reach"; }
        return null;
    }

    private RamGeometry fit(Vec3d base, Vec3d rod, double[] ls, double lo, double hi) {
        double least = Double.POSITIVE_INFINITY, step = (hi - lo) / RAM_STEPS;
        for (int i = 0; i + 1 < ls.length; i++) { least = Math.min(least, Math.abs(ls[i + 1] - ls[i]) / step); }
        return new RamGeometry(base, rod, ramClosedM(), ramExtendedM(), least, ls[ls.length - 1] > ls[0]);
    }

    /** The given base pin (part frame at q = 0): polar in the joint's plane, or behind the rod pin along a sliding joint's axis. */
    private Vec3d givenBase() { return prismatic() ? rodPin().subtract(jointAxis().scale(ramBaseM)) : inPlane(ramBaseM, ramBaseDeg); }

    /** The ram's geometry: the given base pin's, or the solved one's; null when it does not drive the joint's range (or nothing is rammed). */
    public RamGeometry ramGeometry() {
        if (!rammed() || !ramValuesValid() || !(jointHigh() > jointLow())) { return null; }
        double lo = jointLow(), hi = jointHigh();
        Vec3d rod = rodPin();
        if (!autoBase()) {
            Vec3d base = givenBase();
            double[] ls = lengths(base, rod, lo, hi);
            return ramProblem(ls, lo, hi) == null ? fit(base, rod, ls, lo, hi) : null;
        }
        if (prismatic()) {
            Vec3d base = rod.subtract(jointAxis().scale(ramClosedM() - lo));
            double[] ls = lengths(base, rod, lo, hi);
            return ramProblem(ls, lo, hi) == null ? fit(base, rod, ls, lo, hi) : null;
        }
        // the base on both circles about the rod pin at the limits (closed at one, extended at the other)
        Vec3d o = jointOrigin();
        Vec3d[] e = plane(jointAxis());
        Vec3d p1 = moved(rod, lo).subtract(o), p2 = moved(rod, hi).subtract(o);
        double x1 = p1.dot(e[0]), y1 = p1.dot(e[1]), dx = p2.dot(e[0]) - x1, dy = p2.dot(e[1]) - y1, d = Math.hypot(dx, dy);
        RamGeometry best = null;
        double closed = ramClosedM(), extended = ramExtendedM();
        for (double[] r : new double[][]{{closed, extended}, {extended, closed}}) {
            double r1 = r[0], r2 = r[1];
            if (d < 1e-9 || d > r1 + r2 || d < Math.abs(r1 - r2)) { continue; }
            double a = (r1 * r1 - r2 * r2 + d * d) / (2 * d), h = Math.sqrt(Math.max(r1 * r1 - a * a, 0));
            double mx = x1 + a * dx / d, my = y1 + a * dy / d;
            for (int sign = 1; sign >= -1; sign -= 2) {
                Vec3d base = o.add(e[0].scale(mx - sign * h * dy / d)).add(e[1].scale(my + sign * h * dx / d));
                double[] ls = lengths(base, rod, lo, hi);
                if (ramProblem(ls, lo, hi) != null) { continue; }
                RamGeometry g = fit(base, rod, ls, lo, hi);
                if (best == null || g.leastLeverM() > best.leastLeverM()) { best = g; }
            }
        }
        return best;
    }

    private boolean ramValuesValid() {
        return ramBoreM > 0 && Double.isFinite(ramBoreM) && ramRodM > 0 && ramRodM < ramBoreM && ramStrokeM > 0 && Double.isFinite(ramStrokeM) && ramCount >= 1
                && (prismatic() ? ramArmM >= 0 : ramArmM > 0) && Double.isFinite(ramArmM) && Double.isFinite(ramArmDeg)
                && Double.isFinite(jointLow()) && Double.isFinite(jointHigh()) && (autoBase() || (Double.isFinite(ramBaseM) && (prismatic() || Double.isFinite(ramBaseDeg))));
    }

    /** The compiled actuator: the ram, a dragline's free hitch, or none (held). */
    private LinkPart.Actuator actuator() {
        if (!hasJoint()) { return null; }
        if (toolKind == Kind.DRAGLINE_BUCKET) { return new LinkPart.Free(HITCH_FRICTION * massKg() * G * toolLengthM); }
        RamGeometry g = ramGeometry();
        return new LinkPart.Ram(cylinder(), g.basePin(), g.rodPin(), circuit, reliefPa, holdPressurePa(), valveFlowM3PerS);
    }

    // ---------------------------------------------------------------- tools, holds, cutter

    private static Vec3d at(Vec3d p, double z) { return new Vec3d(p.x(), p.y(), z); }

    private static double[] toothCentres(double w, double tw, int n) {
        double[] c = new double[n];
        if (n == 1) { return c; }
        for (int i = 0; i < n; i++) { c[i] = -0.5 * w + 0.5 * tw + i * (w - tw) / (n - 1); }
        return c;
    }

    /** A lip across w: EDGE pieces between the teeth, each tooth a TOOTH ahead of it. */
    private void lipAndTeeth(List<Part.Tool> out, Vec3d lip, Vec3d ahead, Vec3d cut, double w, double depth, double rake, String hold) {
        if (teeth <= 0) {
            out.add(new Part.Tool("lip", at(lip, -0.5 * w), at(lip, 0.5 * w), cut, depth, rake, Part.Tool.Kind.EDGE, hold));
            return;
        }
        double tw = toothWidthM();
        Vec3d tip = lip.add(ahead.scale(TOOTH_PROJECTION * tw));
        double[] centres = toothCentres(w, tw, teeth);
        double from = -0.5 * w;
        int lips = 0;
        for (int i = 0; i < teeth; i++) {
            double z0 = centres[i] - 0.5 * tw, z1 = centres[i] + 0.5 * tw;
            if (z0 - from > 1e-3) { out.add(new Part.Tool("lip_" + (++lips), at(lip, from), at(lip, z0), cut, depth, rake, Part.Tool.Kind.EDGE, hold)); }
            out.add(new Part.Tool("tooth_" + (i + 1), at(tip, z0), at(tip, z1), cut, depth, rake, Part.Tool.Kind.TOOTH, hold));
            from = z1;
        }
        if (0.5 * w - from > 1e-3) { out.add(new Part.Tool("lip_" + (++lips), at(lip, from), at(lip, 0.5 * w), cut, depth, rake, Part.Tool.Kind.EDGE, hold)); }
    }

    /** Where a trencher's chain cuts, at its boom's tip (part frame at q = 0). */
    private Vec3d boomTip() {
        double a = hasJoint() ? 0 : Math.toRadians(TRENCHER_DIG_DEG);
        return new Vec3d(-toolLengthM * Math.cos(a), -toolLengthM * Math.sin(a), 0);
    }

    /** The cutting tools (none for the kinds the game works by their kind). */
    public List<Part.Tool> tools() {
        if (!cuts()) { return List.of(); }
        double rake = Math.toRadians(rakeDeg), w = toolWidthM, h = toolHeightM, l = toolLengthM, r = cutterRadiusM;
        List<Part.Tool> out = new ArrayList<>();
        Vec3d x = Vec3d.X;
        switch (toolKind) {
            case BACKHOE_BUCKET, TRENCH_BUCKET, FACE_SHOVEL_BUCKET, LOADER_BUCKET, DRAGLINE_BUCKET -> {
                Chord c = chord();
                Vec3d ahead = toolKind == Kind.BACKHOE_BUCKET || toolKind == Kind.TRENCH_BUCKET ? c.up().scale(-1) : lipCut();
                lipAndTeeth(out, c.lip(), ahead, lipCut(), w, h, rake, BULK);
            }
            case CLAMSHELL -> lipAndTeeth(out, new Vec3d(0, -h, 0), lipCut(), lipCut(), w, h, rake, BULK);
            case DOZER_BLADE, GRADER_MOLDBOARD, SNOW_PLOW -> out.add(new Part.Tool("edge", new Vec3d(0, 0, -0.5 * w), new Vec3d(0, 0, 0.5 * w), x, h, rake,
                    Part.Tool.Kind.EDGE, ""));
            case RIPPER -> {
                int n = Math.max(teeth, 1);
                double t = plateM;
                for (int i = 0; i < n; i++) {
                    double z = n == 1 ? 0 : -0.5 * w + (i + 0.5) * w / n;
                    out.add(new Part.Tool("shank_" + (i + 1), new Vec3d(0.5 * l, -h, z - 0.5 * t), new Vec3d(0.5 * l, -h, z + 0.5 * t), x, h, rake,
                            Part.Tool.Kind.TOOTH, ""));
                }
            }
            case SCRAPER_BOWL -> {
                double wi = innerWidthM();
                out.add(new Part.Tool("edge", new Vec3d(l, 0, -0.5 * wi), new Vec3d(l, 0, 0.5 * wi), x, h, rake, Part.Tool.Kind.EDGE, BULK));
            }
            case MILLING_DRUM -> out.add(mill("drum", w, r, h, rake, BULK));
            case ROADHEADER_HEAD -> out.add(mill("head", w, r, r, rake, ""));
            case BUCKET_WHEEL -> out.add(mill("wheel", w, r, r, rake, BULK));
            case BROOM -> out.add(mill("broom", w, r, BROOM_DEPTH_M, rake, BULK));
            case SNOW_BLOWER -> out.add(mill("auger", w, r, h, rake, ""));
            case TRENCHER_CHAIN -> {
                Vec3d tip = boomTip();
                // the chain's cut along the travel (+x) at its digging angle: the boom's lowest limit when it is jointed
                Vec3d cut = hasJoint() ? Orientation.rotationVector(jointAxis().scale(-jointLow())).rotate(Vec3d.X) : Vec3d.X;
                out.add(new Part.Tool("chain", at(tip, -0.5 * w), at(tip, 0.5 * w), cut, h, rake, Part.Tool.Kind.MILL, ""));
            }
            case AUGER -> {
                Vec3d tip = new Vec3d(0, -l, 0), down = Vec3d.Y.scale(-1);
                // two flights on opposite radii: their rim forces cancel, their torques add
                out.add(new Part.Tool("flight_a", tip, tip.add(Vec3d.X.scale(r)), down, 0.5 * Math.PI * r, rake, Part.Tool.Kind.MILL, BULK));
                out.add(new Part.Tool("flight_b", tip, tip.add(Vec3d.X.scale(-r)), down, 0.5 * Math.PI * r, rake, Part.Tool.Kind.MILL, BULK));
            }
            case CUTTERHEAD -> out.add(new Part.Tool("face", Vec3d.ZERO, new Vec3d(0, r, 0), x, discDiameterM, rake, Part.Tool.Kind.FACE, BULK,
                    new Part.Face(discs, discDiameterM, discTipM, discSpacingM)));
            default -> { }
        }
        return out;
    }

    /** A drum's MILL tool along its axis (z) at its lowest line, cutting along +x. */
    private static Part.Tool mill(String name, double w, double r, double depth, double rake, String hold) {
        return new Part.Tool(name, new Vec3d(0, -r, -0.5 * w), new Vec3d(0, -r, 0.5 * w), Vec3d.X, depth, rake, Part.Tool.Kind.MILL, hold);
    }

    /** The hold (none when the kind holds nothing). */
    public List<Part.Hold> holds() {
        String hold = holdName();
        double v = holdM3();
        if (hold == null || !(v > 0) || !Double.isFinite(v)) { return List.of(); }
        return List.of(new Part.Hold(hold, v, holdCentre()));
    }

    /** The rotor's axis (part frame). */
    public Vec3d cutterAxis() {
        return switch (toolKind) { case CUTTERHEAD, MIXER_DRUM -> Vec3d.X; case AUGER -> Vec3d.Y; default -> Vec3d.Z; };
    }

    /** The rotor's inertia about its axis, kg m^2, from the turning pieces' masses at their radii. */
    public double cutterInertiaKgM2() {
        if (!rotating()) { return 0; }
        Map<String, Double> m = massBreakdownKg();
        double r = cutterRadiusM, h = toolHeightM, i;
        switch (toolKind) {
            case MILLING_DRUM, ROADHEADER_HEAD -> {
                double ro = r - (toolKind == Kind.MILLING_DRUM ? PICK_REACH_M : HEAD_PICK_REACH_M), ri = Math.max(ro - plateM, 0);
                i = m.getOrDefault("tool_drum_tube", 0.0) * 0.5 * (ro * ro + ri * ri) + m.getOrDefault("tool_drum_ends", 0.0) * 0.5 * ri * ri
                        + m.getOrDefault("tool_picks", 0.0) * Math.pow(r - 0.05, 2);
            }
            case CUTTERHEAD -> i = (m.get("tool_face") + m.get("tool_back_structure") + m.get("tool_muck_buckets")) * 0.5 * r * r + m.get("tool_rim") * r * r
                    + m.get("tool_disc_cutters") * Math.pow(0.6 * r, 2);
            case BUCKET_WHEEL -> i = m.get("tool_buckets") * Math.pow(r - 0.5 * h, 2) + m.get("tool_rim") * Math.pow(r - h, 2) + m.get("tool_hub") * Math.pow(0.15 * r, 2);
            case TRENCHER_CHAIN -> i = (m.get("tool_chain") + m.get("tool_chain_teeth")) * r * r + m.get("tool_sprockets") * 0.5 * r * r;
            case AUGER -> {
                double pipe = 0.5 * augerPipeM();
                i = m.get("tool_auger_pipe") * pipe * pipe + m.get("tool_flights") * 0.5 * (r * r + pipe * pipe) + m.get("tool_bit") * 0.5 * r * r;
            }
            case BROOM -> {
                double rc = BROOM_CORE * r;
                i = m.get("tool_broom_core") * rc * rc + m.get("tool_bristles") * 0.5 * (r * r + rc * rc);
            }
            case SNOW_BLOWER -> i = (m.get("tool_auger") + m.get("tool_impeller")) * Math.pow(0.6 * r, 2);
            case MIXER_DRUM -> i = (m.get("tool_drum_shell") + m.get("tool_mixing_blades") + m.get("tool_drum_rings")) * Math.pow(0.85 * r, 2);
            default -> i = 0;
        }
        return Math.max(i, 1e-3);
    }

    /** The rotor a shaft or a hydraulic motor turns ("cutter"), or null for a kind that does not turn. */
    public LinkPart.Cutter cutter() { return rotating() ? new LinkPart.Cutter(cutterRadiusM, cutterInertiaKgM2(), cutterAxis()) : null; }

    // ---------------------------------------------------------------- shape: envelope, mass, collision, mount points

    /** An axis-aligned box (part frame at q = 0). */
    public record Box(Vec3d centre, Vec3d size) {}

    private static Box span(Vec3d... points) {
        double x0 = Double.POSITIVE_INFINITY, y0 = x0, z0 = x0, x1 = Double.NEGATIVE_INFINITY, y1 = x1, z1 = x1;
        for (Vec3d p : points) {
            x0 = Math.min(x0, p.x()); y0 = Math.min(y0, p.y()); z0 = Math.min(z0, p.z());
            x1 = Math.max(x1, p.x()); y1 = Math.max(y1, p.y()); z1 = Math.max(z1, p.z());
        }
        return new Box(new Vec3d(0.5 * (x0 + x1), 0.5 * (y0 + y1), 0.5 * (z0 + z1)), new Vec3d(Math.max(x1 - x0, 0.02), Math.max(y1 - y0, 0.02), Math.max(z1 - z0, 0.02)));
    }

    private static Box box(double cx, double cy, double cz, double sx, double sy, double sz) {
        return new Box(new Vec3d(cx, cy, cz), new Vec3d(Math.max(sx, 0.02), Math.max(sy, 0.02), Math.max(sz, 0.02)));
    }

    /** The tool's bounding box at q = 0 (its mass is spread over it). */
    public Box envelope() {
        double w = toolWidthM, h = toolHeightM, l = toolLengthM, r = cutterRadiusM, hw = 0.5 * w;
        switch (toolKind) {
            case BACKHOE_BUCKET, TRENCH_BUCKET, FACE_SHOVEL_BUCKET, LOADER_BUCKET, DRAGLINE_BUCKET -> {
                Chord c = chord();
                Vec3d back = c.lip().add(c.up().scale(l)), deep = c.lip().add(c.up().scale(0.5 * l)).subtract(c.out().scale(h));
                Vec3d lo = at(c.lip(), -hw), hi = at(back, hw);
                return span(lo, hi, at(deep, hw), at(Vec3d.ZERO, -hw));
            }
            case CLAMSHELL, GRAPPLE -> { return box(0, -0.5 * h, 0, l, h, w); }
            case DOZER_BLADE, GRADER_MOLDBOARD, SNOW_PLOW -> { return box(-0.175 * h, 0.5 * h, 0, 0.35 * h, h, w); }
            case RIPPER -> { return box(0, 0.2 - 0.5 * (h + 0.4), 0, l, h + 0.4, Math.max(w, plateM)); }
            case HYDRAULIC_HAMMER, PILE_HAMMER -> { return box(0, -0.5 * l, 0, h, l, w); }
            case FORKS -> { return box(0.5 * l - 0.05, 0.5 * h, 0, l + 0.1, h, w); }
            case QUICK_COUPLER, COMPACTOR_PLATE -> { return box(0, -0.5 * h, 0, l, h, w); }
            case SCRAPER_BOWL, PAVER_HOPPER, WORK_PLATFORM -> { return box(0.5 * l, 0.5 * h, 0, l, h, w); }
            case DUMP_BODY -> { return box(0.5 * l * (1 + BODY_CANOPY_LENGTH), 0.5 * h, 0, l * (1 + BODY_CANOPY_LENGTH), h, w); }
            case CONVEYOR -> { return box(0.5 * l, 0.1 - 0.5 * (h + 0.1), 0, l, h + 0.1, w); }
            case SCREED -> { return box(-0.5 * l, 0.5 * h, 0, l, h, w + 2 * extensionM); }
            case MILLING_DRUM -> { return box(0, 0.1 * r, 0, 2.4 * r, 2.2 * r, w); }
            case CUTTERHEAD -> { return box(-0.5 * l, 0, 0, l, 2 * r, 2 * r); }
            case ROADHEADER_HEAD, BUCKET_WHEEL -> { return box(0, 0, 0, 2 * r, 2 * r, w); }
            case TRENCHER_CHAIN -> {
                Vec3d tip = boomTip();
                double pad = 0.5 * BOOM_DEPTH_M + r;
                return span(new Vec3d(pad, pad, -hw - 0.1), new Vec3d(tip.x() - pad, tip.y() - pad, hw + 0.1));
            }
            case AUGER -> { return box(0, -0.5 * l, 0, 2 * r, l, 2 * r); }
            case BROOM -> { return box(0.5 * (r - r - l), 0.5 * (h - r - r), 0, 2 * r + l, h, w); }
            case SNOW_BLOWER -> { return box(0.5 * (r - l), 0.5 * (h - 2 * r), 0, r + l, h, w); }
            case SPRAY_BAR -> { return box(0, 0, 0, SPRAY_PIPE_M, SPRAY_PIPE_M, w); }
            case MIXER_DRUM -> { return box(0, 0, 0, l, 2 * r, 2 * r); }
            case SEGMENT_ERECTOR -> { return box(0, 0.5 * h, 0, l, h, w); }
        }
        return box(0, 0, 0, w, h, l);
    }

    /** Where its mass is centred (part frame at q = 0): a bucket's bowl, forks' carriage-heavy third, else its envelope's middle. */
    public Vec3d massCentre() {
        if (BUCKETS.contains(toolKind) || toolKind == Kind.GRAPPLE) { return bowlCentre(); }
        if (toolKind == Kind.FORKS) { return new Vec3d(0.25 * toolLengthM, 0.35 * toolHeightM, 0); }
        return envelope().centre();
    }

    /** The region the collision spheres fill: a cutting tool's back, away from its edge (spheres there would hold the edge out of the cut), else its envelope; null for none. */
    private Box collisionRegion() {
        double w = toolWidthM, h = toolHeightM, l = toolLengthM, r = cutterRadiusM, e = HINGE_SHARE * l;
        if (!cuts()) { return envelope(); }
        switch (toolKind) {
            case BACKHOE_BUCKET, TRENCH_BUCKET -> { return box(0.4 * h, -0.5 * (e + 0.25 * l), 0, 0.8 * h, e + 0.25 * l, w); }
            case FACE_SHOVEL_BUCKET -> { return box(0.5 * (e + 0.5 * h), 0.225 * l, 0, e + 0.5 * h, 0.25 * l, w); }
            case LOADER_BUCKET -> {
                Chord c = chord();
                Vec3d back = c.lip().add(c.up().scale(0.85 * l)).subtract(c.out().scale(0.25 * h));
                return box(back.x(), back.y(), 0, 0.5 * h, 0.3 * l, w);
            }
            case DRAGLINE_BUCKET -> { return box(0, -0.1 * l - 0.15 * h, 0, 0.6 * l, 0.3 * h, w); }
            case CLAMSHELL -> { return box(0, -0.15 * h, 0, 0.5 * l, 0.3 * h, w); }
            case DOZER_BLADE, GRADER_MOLDBOARD, SNOW_PLOW -> { return box(-0.15 * h, 0.85 * h, 0, 0.3 * h, 0.3 * h, w); }
            case RIPPER -> { return box(0, 0.2, 0, 0.4, 0.4, Math.max(w, 0.4)); }
            case SCRAPER_BOWL -> { return box(0.5 * l, 0.85 * h, 0, l, 0.3 * h, w); }
            case MILLING_DRUM -> { return box(0, r + 0.1, 0, 2 * r, 0.2, w); }
            case CUTTERHEAD -> { return box(-0.75 * l, 0, 0, 0.5 * l, 1.6 * r, 1.6 * r); }
            case BUCKET_WHEEL -> { return box(0, 0, 0, 0.4 * r, 0.4 * r, w); }
            case TRENCHER_CHAIN -> { return box(-0.2, 0.1, 0, 0.4, 0.3, w + 0.2); }
            case AUGER -> { return box(0, -0.25, 0, 0.3, 0.5, 0.3); }
            case BROOM -> { return box(-0.5 * (r + l), h - r - 0.1, 0, r + l, 0.2, w); }
            case SNOW_BLOWER -> { return box(-0.5 * l, h - r - 0.1, 0, l, 0.2, w); }
            default -> { return null; }   // a roadheader's heads cut all round
        }
    }

    private static int count(double side, double r) { return Math.max(1, (int) Math.ceil((side - 2 * r) / r - 1e-9) + 1); }

    private static double spot(double side, double r, int i, int n) { return n == 1 ? 0 : -0.5 * side + r + (side - 2 * r) * i / (n - 1); }

    /** Spheres filling a box (MassShapes.boxSpheres' layout), the shorter sides cut to their two edges and the longest thinned when there would be more than MAX_SPHERES. */
    static List<CollisionSphere> fill(Box b) {
        double[] side = {b.size().x(), b.size().y(), b.size().z()};
        double r = Math.max(0.5 * Math.min(side[0], Math.min(side[1], side[2])), 0.05);
        int[] n = {count(side[0], r), count(side[1], r), count(side[2], r)};
        Integer[] order = {0, 1, 2};
        java.util.Arrays.sort(order, (p, q) -> Double.compare(side[p], side[q]));
        if (n[0] * n[1] * n[2] > MAX_SPHERES) { n[order[0]] = Math.min(n[order[0]], 2); }
        if (n[0] * n[1] * n[2] > MAX_SPHERES) { n[order[1]] = Math.min(n[order[1]], 2); }
        if (n[0] * n[1] * n[2] > MAX_SPHERES) { n[order[2]] = Math.max(1, MAX_SPHERES / (n[order[0]] * n[order[1]])); }
        List<CollisionSphere> out = new ArrayList<>();
        for (int i = 0; i < n[0]; i++) for (int j = 0; j < n[1]; j++) for (int k = 0; k < n[2]; k++) {
            Vec3d at = new Vec3d(spot(side[0], Math.min(r, 0.5 * side[0]), i, n[0]), spot(side[1], Math.min(r, 0.5 * side[1]), j, n[1]),
                    spot(side[2], Math.min(r, 0.5 * side[2]), k, n[2]));
            out.add(new CollisionSphere(b.centre().add(at), r));
        }
        return out;
    }

    /** The mount points other parts go on: a coupler's "coupler" (the carried tool's pin), a conveyor's "head", a milling drum's "conveyor", the hoist ropes' "hoist". */
    public Map<String, Mount> mountPoints() {
        return switch (toolKind) {
            case QUICK_COUPLER -> Map.of(COUPLER_POINT, Mount.at(new Vec3d(0, -toolHeightM, 0)));
            case CONVEYOR -> Map.of("head", Mount.at(new Vec3d(toolLengthM, 0, 0)));
            case MILLING_DRUM -> Map.of("conveyor", Mount.at(new Vec3d(1.2 * cutterRadiusM, 0.3 * cutterRadiusM, 0)));
            case DRAGLINE_BUCKET, PILE_HAMMER -> Map.of("hoist", Mount.at(Vec3d.ZERO));
            case CLAMSHELL -> Map.of("hoist", Mount.at(new Vec3d(0, 0.25 * toolHeightM, 0)));
            default -> Map.of();
        };
    }

    /** A work platform's floor people stand on. */
    public List<WorldBox> deck() {
        if (toolKind != Kind.WORK_PLATFORM) { return List.of(); }
        return List.of(new WorldBox(new Vec3d(0, 0, -0.5 * toolWidthM), new Vec3d(toolLengthM, plateM, 0.5 * toolWidthM)));
    }

    /** A work platform's places for its persons (riders' hips over the floor, along its width). */
    public List<Vec3d> seats() {
        int n = persons();
        List<Vec3d> out = new ArrayList<>();
        for (int i = 0; i < n; i++) { out.add(new Vec3d(0.5 * toolLengthM, plateM + RIDER_HIP_M, -0.5 * toolWidthM + (i + 0.5) * toolWidthM / n)); }
        return out;
    }

    /** Where a kind the game works by its kind acts, the way it acts, and across how much (m). */
    public record WorkPoint(Vec3d point, Vec3d direction, double widthM) {}

    /**
     * Where the game works the tool (part frame at q = 0): a hammer's chisel and a pile hammer's helmet down, a plate's
     * bottom down, forks' and a grapple's load, a spray bar's line down, a screed's paving line (its width the paving
     * width) behind, a conveyor's head and a hopper's rear (where they discharge), a mixer's opening, an erector's
     * gripper up, a platform's floor; for a cutting tool its first edge's middle along its cut.
     */
    public WorkPoint workPoint() {
        double w = toolWidthM, h = toolHeightM, l = toolLengthM, r = cutterRadiusM;
        Vec3d down = Vec3d.Y.scale(-1);
        return switch (toolKind) {
            case HYDRAULIC_HAMMER, PILE_HAMMER -> new WorkPoint(new Vec3d(0, -l, 0), down, h);
            case COMPACTOR_PLATE -> new WorkPoint(new Vec3d(0, -h, 0), down, w);
            case FORKS -> new WorkPoint(new Vec3d(0.5 * l, 0, 0), Vec3d.X, w);
            case GRAPPLE -> new WorkPoint(bowlCentre(), down, w);
            case SPRAY_BAR -> new WorkPoint(Vec3d.ZERO, down, w);
            case SCREED -> new WorkPoint(new Vec3d(-l, 0, 0), Vec3d.X.scale(-1), pavingWidthM());
            case CONVEYOR -> new WorkPoint(new Vec3d(l, 0, 0), Vec3d.X, w);
            case PAVER_HOPPER -> new WorkPoint(new Vec3d(0, 0, 0), Vec3d.X.scale(-1), 0.5 * w);
            case MIXER_DRUM -> new WorkPoint(new Vec3d(-0.5 * l, 0, 0), Vec3d.X.scale(-1), 0.45 * r);
            case SEGMENT_ERECTOR -> new WorkPoint(new Vec3d(0, h, 0), Vec3d.Y, w);
            case WORK_PLATFORM -> new WorkPoint(new Vec3d(0.5 * l, plateM, 0), Vec3d.Y, w);
            case QUICK_COUPLER -> new WorkPoint(new Vec3d(0, -h, 0), down, w);
            case DUMP_BODY -> new WorkPoint(Vec3d.ZERO, Vec3d.X.scale(-1), innerWidthM());
            default -> {
                List<Part.Tool> tools = tools();
                if (tools.isEmpty()) { yield new WorkPoint(Vec3d.ZERO, Vec3d.X, w); }
                Part.Tool t = tools.get(tools.size() / 2);
                yield new WorkPoint(t.a().add(t.b()).scale(0.5), t.cut(), w);
            }
        };
    }

    private MassProperties massProperties() {
        Box env = envelope();
        Vec3d s = env.size();
        double wall = Math.max(Math.min(plateM > 0 && Double.isFinite(plateM) ? plateM : 0.01, 0.5 * Math.min(s.x(), Math.min(s.y(), s.z()))), 1e-3);
        MassProperties shell = MassShapes.box(s, wall);
        double total = massKg(), ram = ramKg(), estimated = toolKg() + ram, toolShare = estimated > 0 ? toolKg() / estimated : 1;
        List<MassProperties> parts = new ArrayList<>();
        parts.add(shell.scaled(total * toolShare / shell.massKg()).at(massCentre()));
        if (ram > 0 && toolShare < 1) {
            RamGeometry g = ramGeometry();
            Vec3d mid = g == null ? rodPin() : g.basePin().add(g.rodPin()).scale(0.5);
            parts.add(MassProperties.point(total * (1 - toolShare), mid));
        }
        return MassProperties.combine(parts);
    }

    // ---------------------------------------------------------------- checks

    private void need(List<DesignIssue> issues, String parameter, double value) { EngineComponent.Rules.positive(issues, parameter, value); }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        Kind k = toolKind;
        if (shows("toolWidthM")) { need(issues, "toolWidthM", toolWidthM); }
        if (shows("toolHeightM")) { need(issues, "toolHeightM", toolHeightM); }
        if (shows("toolLengthM")) { need(issues, "toolLengthM", toolLengthM); }
        if (shows("plateM")) { need(issues, "plateM", plateM); }
        material.check(issues, "material");
        EngineComponent.Rules.mass(issues, massGivenKg);
        if (shows("teeth") && teeth < (k == Kind.RIPPER || k == Kind.BUCKET_WHEEL ? 1 : 0)) {
            issues.add(DesignIssue.invalid("teeth", k == Kind.RIPPER ? "A ripper has a shank" : k == Kind.BUCKET_WHEEL ? "A bucket wheel has buckets" : "Teeth cannot be fewer than none"));
        }
        if (shows("rakeDeg") && !(rakeDeg > 0 && rakeDeg < 180)) { issues.add(DesignIssue.invalid("rakeDeg", "A rake lies between 0 and 180 degrees")); }
        if (shows("capacityGivenM3") && !Double.isNaN(capacityGivenM3) && !(capacityGivenM3 > 0 && Double.isFinite(capacityGivenM3))) {
            issues.add(DesignIssue.invalid("capacityGivenM3", "A given capacity must be positive"));
        }
        if (rotating()) { need(issues, "cutterRadiusM", cutterRadiusM); }
        switch (k) {
            case CUTTERHEAD -> {
                if (discs < 1) { issues.add(DesignIssue.invalid("discs", "A cutterhead has disc cutters")); }
                need(issues, "discDiameterM", discDiameterM);
                need(issues, "discTipM", discTipM);
                need(issues, "discSpacingM", discSpacingM);
            }
            case HYDRAULIC_HAMMER -> need(issues, "impactEnergyJ", impactEnergyJ);
            case PILE_HAMMER -> { need(issues, "ramMassKg", ramMassKg); need(issues, "dropM", dropM); }
            case CONVEYOR -> need(issues, "beltSpeedMps", beltSpeedMps);
            case SCREED -> {
                EngineComponent.Rules.nonnegative(issues, "extensionM", extensionM);
                need(issues, "matThicknessM", matThicknessM);
            }
            case COMPACTOR_PLATE -> { EngineComponent.Rules.nonnegative(issues, "centrifugalN", centrifugalN); need(issues, "vibrationRpm", vibrationRpm); }
            case SPRAY_BAR -> need(issues, "sprayM3PerS", sprayM3PerS);
            default -> { }
        }
        if (RATED.contains(k) && (k == Kind.SEGMENT_ERECTOR || !Double.isNaN(ratedLoadKg)) && !(ratedLoadKg > 0 && Double.isFinite(ratedLoadKg))) {
            issues.add(DesignIssue.invalid("ratedLoadKg", k == Kind.SEGMENT_ERECTOR ? "An erector is rated for its segments" : "A given rated load must be positive"));
        }
        if (hasJoint()) {
            if (prismatic()) { need(issues, "travelM", travelM); }
            else if (!(Double.isFinite(jointMinDeg) && Double.isFinite(jointMaxDeg) && jointMaxDeg > jointMinDeg)) {
                issues.add(DesignIssue.invalid("jointMaxDeg", "A joint's range runs from its lower limit up to a higher one"));
            } else if (jointMaxDeg - jointMinDeg >= 360) { issues.add(DesignIssue.invalid("jointMaxDeg", "A ram cannot turn a joint round once or more")); }
        }
        if (rammed()) {
            need(issues, "ramBoreM", ramBoreM);
            need(issues, "ramRodM", ramRodM);
            need(issues, "ramStrokeM", ramStrokeM);
            if (ramRodM > 0 && ramBoreM > 0 && !(ramRodM < ramBoreM)) { issues.add(DesignIssue.invalid("ramRodM", "A ram's rod is thinner than its bore")); }
            if (ramCount < 1) { issues.add(DesignIssue.invalid("ramCount", "A ram has at least one cylinder")); }
            if (!prismatic()) { need(issues, "ramArmM", ramArmM); } else { EngineComponent.Rules.nonnegative(issues, "ramArmM", ramArmM); }
            if (!Double.isFinite(ramArmDeg)) { issues.add(DesignIssue.invalid("ramArmDeg", "The rod pin needs its angle")); }
            if (!autoBase() && !(ramBaseM > 0 && Double.isFinite(ramBaseM))) { issues.add(DesignIssue.invalid("ramBaseM", "The base pin lies some way from the joint")); }
            if (!autoBase() && !prismatic() && !Double.isFinite(ramBaseDeg)) { issues.add(DesignIssue.invalid("ramBaseDeg", "The base pin needs its angle")); }
            need(issues, "reliefPa", reliefPa);
            need(issues, "valveFlowM3PerS", valveFlowM3PerS);
            if (circuit.isBlank()) { issues.add(DesignIssue.invalid("circuit", "A ram runs on a circuit: give it a name")); }
            if (!Double.isNaN(holdPa) && !(holdPa >= reliefPa && Double.isFinite(holdPa))) {
                issues.add(DesignIssue.invalid("holdPa", "The load-holding valves hold at least the relief pressure"));
            }
        }
        if (!issues.isEmpty()) { return issues; }

        // the geometry
        if ((isBucket() || k == Kind.BUCKET_WHEEL || k == Kind.GRAPPLE || k == Kind.DUMP_BODY || k == Kind.SCRAPER_BOWL) && innerWidthM() <= 0) {
            issues.add(DesignIssue.invalid("plateM", "The side plates leave no room inside"));
        }
        if ((k == Kind.MILLING_DRUM && !(cutterRadiusM - PICK_REACH_M - plateM > 0)) || (k == Kind.ROADHEADER_HEAD && !(cutterRadiusM - HEAD_PICK_REACH_M - plateM > 0))) {
            issues.add(DesignIssue.invalid("cutterRadiusM", "The drum's tube does not fit inside its picks' reach"));
        }
        if (k == Kind.CUTTERHEAD && !(discDiameterM < cutterRadiusM)) { issues.add(DesignIssue.invalid("discDiameterM", "A disc cutter is smaller than its cutterhead")); }
        if (k == Kind.CONVEYOR && !(beltAreaM2() > 0)) { issues.add(DesignIssue.invalid("toolWidthM", "A belt this narrow carries nothing")); }
        if (k == Kind.TRENCHER_CHAIN && !(toolLengthM > cutterRadiusM)) { issues.add(DesignIssue.invalid("toolLengthM", "The boom is longer than its sprocket")); }
        if (k == Kind.BUCKET_WHEEL && !(cutterRadiusM > toolHeightM)) { issues.add(DesignIssue.invalid("cutterRadiusM", "The buckets fit inside the wheel's radius")); }
        if (k == Kind.RIPPER && teeth > 1 && !(teeth * plateM < toolWidthM)) { issues.add(DesignIssue.invalid("teeth", "The shanks do not fit on the beam")); }
        if (k == Kind.AUGER && !(2 * cutterRadiusM > augerPipeM())) { issues.add(DesignIssue.invalid("cutterRadiusM", "The flights stand out from the auger's pipe")); }
        if (CAPACITY_GIVEN.contains(k) && !(capacityM3() > 0)) { issues.add(DesignIssue.invalid("capacityGivenM3", "It holds nothing")); }
        if (rammed()) {
            RamGeometry g = ramGeometry();
            if (g == null) {
                if (autoBase()) {
                    issues.add(DesignIssue.invalid("ramStrokeM", prismatic() ? "The ram's stroke is shorter than the travel"
                            : "No base pin lets the ram drive the joint through its range: lengthen its stroke or its arm, or narrow the range"));
                } else {
                    String problem = ramProblem(lengths(givenBase(), rodPin(), jointLow(), jointHigh()), jointLow(), jointHigh());
                    issues.add(DesignIssue.invalid("ramBaseM", "centre".equals(problem) ? "The ram passes (or comes too near) its dead centre within the joint's range"
                            : "The ram is too short or too long for the joint's range (closed " + Math.round(ramClosedM() * 1000) + " mm, extended "
                            + Math.round(ramExtendedM() * 1000) + " mm)"));
                }
            }
        }
        if (!issues.isEmpty()) { return issues; }

        // the makers' ranges
        double cap = capacityM3();
        switch (k) {
            case BACKHOE_BUCKET -> {
                DesignIssue.range(issues, "capacityM3", cap, 0.02, 8, "excavator buckets of mini to 100 t machines (ISO 7451)");
                DesignIssue.range(issues, "toolWidthM", toolWidthM, 0.2, 2.6, "excavator buckets");
            }
            case TRENCH_BUCKET -> {
                DesignIssue.range(issues, "capacityM3", cap, 0.02, 1.2, "trenching buckets");
                DesignIssue.range(issues, "toolWidthM", toolWidthM, 0.15, 0.9, "trenching buckets");
            }
            case FACE_SHOVEL_BUCKET -> DesignIssue.range(issues, "capacityM3", cap, 2, 61, "face-shovel buckets and rope-shovel dippers (to the P&H 4100's)");
            case LOADER_BUCKET -> DesignIssue.range(issues, "capacityM3", cap, 0.2, 40, "loader buckets, compact to the LeTourneau L-2350's");
            case DRAGLINE_BUCKET -> DesignIssue.range(issues, "capacityM3", cap, 0.5, 170, "dragline buckets (to Big Muskie's 168 m3)");
            case CLAMSHELL -> DesignIssue.range(issues, "capacityM3", cap, 0.1, 30, "clamshell buckets");
            case DOZER_BLADE -> {
                DesignIssue.range(issues, "toolWidthM", toolWidthM, 1.8, 7.5, "dozer blades, D3 to D11 and D575");
                DesignIssue.range(issues, "toolHeightM", toolHeightM, 0.5, 3.3, "dozer blades, D3 to D575 SD");
            }
            case GRADER_MOLDBOARD -> {
                DesignIssue.range(issues, "toolWidthM", toolWidthM, 2.4, 7.4, "motor grader moldboards (8 to 24 ft)");
                DesignIssue.range(issues, "toolHeightM", toolHeightM, 0.45, 1.1, "motor grader moldboards");
            }
            case SNOW_PLOW -> DesignIssue.range(issues, "toolWidthM", toolWidthM, 1.5, 4.6, "truck and loader snow plows");
            case RIPPER -> {
                DesignIssue.range(issues, "toolHeightM", toolHeightM, 0.3, 2.6, "ripper penetrations, D3 to D11");
                DesignIssue.range(issues, "teeth", teeth, 1, 5, "single- and multi-shank rippers");
            }
            case HYDRAULIC_HAMMER -> DesignIssue.range(issues, "impactEnergyJ", impactEnergyJ, 100, 20000, "hydraulic breakers (Epiroc SB 52 to HB 10000 class)");
            case PILE_HAMMER -> {
                DesignIssue.range(issues, "ramMassKg", ramMassKg, 500, 40000, "hydraulic impact hammers");
                DesignIssue.range(issues, "dropM", dropM, 0.2, 2, "hydraulic impact hammers' strokes");
            }
            case FORKS -> {
                DesignIssue.range(issues, "toolLengthM", toolLengthM, 0.6, 2.4, "ISO 2328 fork arms");
                DesignIssue.range(issues, "plateM", plateM, 0.03, 0.1, "ISO 2328 fork arms");
                DesignIssue.range(issues, "forkRatingKg", ratedKg(), 0, forkRatingKg(), "ISO 2330's test load at the tines' heels");
            }
            case COMPACTOR_PLATE -> {
                DesignIssue.range(issues, "centrifugalN", centrifugalN, 5e3, 200e3, "plate compactors");
                DesignIssue.range(issues, "vibrationRpm", vibrationRpm, 1500, 6000, "plate compactors' vibrations per minute");
            }
            case SCRAPER_BOWL -> DesignIssue.range(issues, "capacityM3", cap, 5, 40, "scraper bowls (CAT 613 to 657, SAE J741)");
            case DUMP_BODY -> DesignIssue.range(issues, "capacityM3", cap, 2, 270, "tipper, articulated and haul truck bodies (SAE J1363)");
            case CONVEYOR -> {
                DesignIssue.range(issues, "toolWidthM", toolWidthM, 0.3, 2.4, "ISO 251 belt widths");
                DesignIssue.range(issues, "beltSpeedMps", beltSpeedMps, 0.5, 6, "CEMA's belt speeds");
            }
            case PAVER_HOPPER -> DesignIssue.range(issues, "capacityM3", cap, 1, 9, "paver hoppers (of 5 to 18 t of mix)");
            case SCREED -> {
                DesignIssue.range(issues, "pavingWidthM", pavingWidthM(), 1, 16, "extending and fixed-width screeds");
                DesignIssue.range(issues, "matThicknessM", matThicknessM, 0.02, 0.3, "asphalt mats");
            }
            case MILLING_DRUM -> {
                DesignIssue.range(issues, "toolWidthM", toolWidthM, 0.3, 4.4, "cold planers' milling widths (W 35 to W 250)");
                DesignIssue.range(issues, "toolHeightM", toolHeightM, 0.02, 0.35, "cold planers' milling depths");
            }
            case CUTTERHEAD -> {
                DesignIssue.range(issues, "cutterRadiusM", cutterRadiusM, 0.5, 8.8, "tunnel boring machines (to 17.6 m)");
                DesignIssue.range(issues, "discDiameterM", discDiameterM, 0.3, 0.51, "disc cutters (12 to 20 in)");
                DesignIssue.range(issues, "discSpacingM", discSpacingM, 0.065, 0.1, "disc spacings (Rostami and Ozdemir)");
                DesignIssue.range(issues, "discTipM", discTipM, 0.012, 0.025, "constant-section disc tips");
                DesignIssue.range(issues, "faceCoverage", discs * discSpacingM / cutterRadiusM, 0.8, 1.6, "discs' kerfs covering the face");
            }
            case ROADHEADER_HEAD -> DesignIssue.range(issues, "cutterRadiusM", cutterRadiusM, 0.25, 0.75, "roadheaders' cutting heads");
            case BUCKET_WHEEL -> {
                DesignIssue.range(issues, "cutterRadiusM", cutterRadiusM, 1, 11, "bucket wheels (to Bagger 288's 21.6 m)");
                DesignIssue.range(issues, "teeth", teeth, 6, 20, "bucket wheels' buckets");
            }
            case TRENCHER_CHAIN -> {
                DesignIssue.range(issues, "toolHeightM", toolHeightM, 0.3, 3, "chain trenchers' depths");
                DesignIssue.range(issues, "toolWidthM", toolWidthM, 0.1, 0.9, "chain trenchers' widths");
            }
            case AUGER -> DesignIssue.range(issues, "cutterRadiusM", cutterRadiusM, 0.075, 1.5, "earth augers (0.15 to 3 m)");
            case BROOM -> DesignIssue.range(issues, "toolWidthM", toolWidthM, 0.6, 3.5, "sweepers' brooms");
            case SNOW_BLOWER -> DesignIssue.range(issues, "toolWidthM", toolWidthM, 0.6, 3, "snow blowers");
            case SPRAY_BAR -> DesignIssue.range(issues, "toolWidthM", toolWidthM, 1, 4.5, "water trucks' and distributors' spray bars");
            case MIXER_DRUM -> DesignIssue.range(issues, "capacityM3", cap, 1, 16, "truck mixers (EN 12609)");
            case WORK_PLATFORM -> {
                DesignIssue.range(issues, "persons", persons(), 1, 4, "EN 280 platforms");
                DesignIssue.range(issues, "ratedLoadKg", ratedKg(), 120, 1000, "EN 280 platforms");
            }
            case SEGMENT_ERECTOR -> DesignIssue.range(issues, "ratedLoadKg", ratedLoadKg, 500, 30000, "segment erectors");
            default -> { }
        }
        if (shows("rakeDeg") && k != Kind.CUTTERHEAD) { DesignIssue.range(issues, "rakeDeg", rakeDeg, 15, 75, "rakes of the wedge model's checks (McKyes 1985)"); }
        if (rammed()) {
            DesignIssue.range(issues, "reliefPa", reliefPa, 7e6, 45e6, "mobile hydraulics' working pressures");
            DesignIssue.range(issues, "rodToBore", ramRodM / ramBoreM, 0.4, 0.8, "rod-to-bore ratios (ISO 3320)");
            // the ram's speed extending at the valve's full flow
            DesignIssue.range(issues, "ramSpeedMps", cylinder().extendingVelocityMps(valveFlowM3PerS), 0.02, 1.5, "mobile machines' cylinder speeds");
        }
        return issues;
    }

    // ---------------------------------------------------------------- compiling

    @Override public Part compile() {
        List<DesignIssue> all = check();
        DesignException.throwIfInvalid(all);
        List<DesignIssue> extrapolated = all.stream().filter(i -> i.kind() == DesignIssue.Kind.EXTRAPOLATION).toList();
        Box region = collisionRegion();
        List<CollisionSphere> collision = region == null ? List.of() : fill(region);
        return new LinkPart(this, massProperties(), collision, mountPoints(), joint(), actuator(), holds(), deck(), seats(), extrapolated, tools(), cutter());
    }

    // ---------------------------------------------------------------- figures

    /** What the design screen shows (Designs.figures): mass, capacity, the ram's reach and force, a rotor's inertia, the kind's own. */
    public List<Designs.Figure> figures() {
        List<Designs.Figure> out = new ArrayList<>();
        try {
            out.add(new Designs.Figure("mass", massKg(), "kg"));
            if (check().stream().anyMatch(i -> i.kind() == DesignIssue.Kind.INVALID)) { return out; }
            Kind k = toolKind;
            if (heapSlope() > 0) {
                out.add(new Designs.Figure("heapedCapacity", capacityM3(), "m³"));
                out.add(new Designs.Figure("struckCapacity", struckM3(), "m³"));
            } else if (holdM3() > 0) { out.add(new Designs.Figure("capacity", capacityM3(), "m³")); }
            if (isBlade()) { out.add(new Designs.Figure("bladeCapacity", bladeCapacityM3(), "m³")); }
            if ((isBucket() || k == Kind.RIPPER) && teeth > 0) { out.add(new Designs.Figure("toothWidth", toothWidthM() * 1000, "mm")); }
            if (rammed()) {
                RamGeometry g = ramGeometry();
                double lo = jointLow(), hi = jointHigh();
                out.add(prismatic() ? new Designs.Figure("jointRange", (hi - lo) * 1000, "mm") : new Designs.Figure("jointRange", Math.toDegrees(hi - lo), "°"));
                Cylinder c = cylinder();
                double force = g.extendsUp() ? c.maxPushN(reliefPa) : c.maxPullN(reliefPa);
                out.add(new Designs.Figure("ramPush", c.maxPushN(reliefPa) / 1000, "kN"));
                if (!prismatic()) {
                    Vec3d edge = workPoint().point().subtract(jointOrigin());
                    Vec3d axis = jointAxis();
                    double radius = edge.subtract(axis.scale(edge.dot(axis))).length();
                    double mid = 0.5 * (lo + hi), lever = Math.abs(moved(g.rodPin(), mid + 1e-4).subtract(g.basePin()).length()
                            - moved(g.rodPin(), mid - 1e-4).subtract(g.basePin()).length()) / 2e-4;
                    if (radius > 0) { out.add(new Designs.Figure("tipForce", force * lever / radius / 1000, "kN")); }
                } else { out.add(new Designs.Figure("tipForce", force / 1000, "kN")); }
            }
            if (rotating()) { out.add(new Designs.Figure("cutterInertia", cutterInertiaKgM2(), "kg·m²")); }
            switch (k) {
                case CUTTERHEAD -> out.add(new Designs.Figure("faceCoverage", discs * discSpacingM / cutterRadiusM, ""));
                case HYDRAULIC_HAMMER -> {
                    double se = RockCutting.specificEnergyPa(Material.HARD_ROCK.compressivePa(), RockCutting.TYPICAL_EFFICIENCY);
                    out.add(new Designs.Figure("brokenPerBlow", RockCutting.blowM3(impactEnergyJ, se) * 1000, "L"));
                }
                case PILE_HAMMER -> out.add(new Designs.Figure("blowEnergy", PILE_EFFICIENCY * ramMassKg * G * dropM / 1000, "kJ"));
                case CONVEYOR -> out.add(new Designs.Figure("beltCapacity", beltAreaM2() * beltSpeedMps * 3600, "m³/h"));
                case SCREED -> out.add(new Designs.Figure("pavingWidth", pavingWidthM(), "m"));
                case WORK_PLATFORM -> {
                    out.add(new Designs.Figure("persons", persons(), ""));
                    out.add(new Designs.Figure("deckArea", deckAreaM2(), "m²"));
                }
                case COMPACTOR_PLATE -> out.add(new Designs.Figure("staticPressure", massKg() * G / (toolWidthM * toolLengthM) / 1000, "kPa"));
                case MIXER_DRUM -> out.add(new Designs.Figure("drumVolume", drumVolumeM3(), "m³"));
                case BUCKET_WHEEL -> out.add(new Designs.Figure("wheelCapacity", teeth * capacityM3(), "m³"));
                default -> { }
            }
            if (RATED.contains(k)) { out.add(new Designs.Figure("ratedLoad", ratedKg(), "kg")); }
        } catch (RuntimeException ignored) {
            // a design too broken to derive figures from; its issues say why
        }
        return out;
    }

    // ---------------------------------------------------------------- JSON

    private static String lower(Enum<?> e) { return e.name().toLowerCase(Locale.ROOT); }

    private static void put(Map<String, Object> m, String key, double value) { if (!Double.isNaN(value)) { m.put(key, value); } }

    @Override public Map<String, Object> parameters() {
        Map<String, Object> m = Fields.object("toolKind", lower(toolKind), "bladeStyle", lower(bladeStyle));
        put(m, "toolWidthM", toolWidthM); put(m, "toolHeightM", toolHeightM); put(m, "toolLengthM", toolLengthM); put(m, "plateM", plateM);
        m.put("material", material.toJson());
        m.put("teeth", teeth);
        put(m, "rakeDeg", rakeDeg); put(m, "capacityGivenM3", capacityGivenM3);
        m.put("jointed", jointed);
        put(m, "jointMinDeg", jointMinDeg); put(m, "jointMaxDeg", jointMaxDeg); put(m, "travelM", travelM);
        put(m, "ramBoreM", ramBoreM); put(m, "ramRodM", ramRodM); put(m, "ramStrokeM", ramStrokeM);
        m.put("ramCount", ramCount);
        put(m, "ramArmM", ramArmM); put(m, "ramArmDeg", ramArmDeg); put(m, "ramBaseM", ramBaseM); put(m, "ramBaseDeg", ramBaseDeg);
        put(m, "reliefPa", reliefPa); put(m, "holdPa", holdPa); put(m, "valveFlowM3PerS", valveFlowM3PerS);
        m.put("circuit", circuit);
        put(m, "cutterRadiusM", cutterRadiusM);
        m.put("discs", discs);
        put(m, "discDiameterM", discDiameterM); put(m, "discTipM", discTipM); put(m, "discSpacingM", discSpacingM);
        put(m, "impactEnergyJ", impactEnergyJ); put(m, "ramMassKg", ramMassKg); put(m, "dropM", dropM); put(m, "beltSpeedMps", beltSpeedMps);
        put(m, "extensionM", extensionM); put(m, "matThicknessM", matThicknessM); put(m, "centrifugalN", centrifugalN); put(m, "vibrationRpm", vibrationRpm);
        put(m, "sprayM3PerS", sprayM3PerS); put(m, "ratedLoadKg", ratedLoadKg);
        put(m, "massKg", massGivenKg);
        return m;
    }

    /** A design from its JSON; what is missing is its kind's default (of). */
    public static PartDesign read(Fields f) {
        Kind kind = EngineComponent.Rules.choice(f, "toolKind", Kind.class, Kind.BACKHOE_BUCKET);
        WorkToolDesign d = of(kind);
        return new WorkToolDesign(f.string("name", d.name), kind, EngineComponent.Rules.choice(f, "bladeStyle", BladeStyle.class, d.bladeStyle),
                f.number("toolWidthM", d.toolWidthM), f.number("toolHeightM", d.toolHeightM), f.number("toolLengthM", d.toolLengthM), f.number("plateM", d.plateM),
                EngineComponent.Rules.material(f, "material", d.material), f.integer("teeth", d.teeth), f.number("rakeDeg", d.rakeDeg),
                f.number("capacityGivenM3", d.capacityGivenM3), f.bool("jointed", d.jointed), f.number("jointMinDeg", d.jointMinDeg),
                f.number("jointMaxDeg", d.jointMaxDeg), f.number("travelM", d.travelM), f.number("ramBoreM", d.ramBoreM), f.number("ramRodM", d.ramRodM),
                f.number("ramStrokeM", d.ramStrokeM), f.integer("ramCount", d.ramCount), f.number("ramArmM", d.ramArmM), f.number("ramArmDeg", d.ramArmDeg),
                f.number("ramBaseM", d.ramBaseM), f.number("ramBaseDeg", d.ramBaseDeg), f.number("reliefPa", d.reliefPa), f.number("holdPa", d.holdPa),
                f.number("valveFlowM3PerS", d.valveFlowM3PerS), f.string("circuit", d.circuit),
                f.number("cutterRadiusM", d.cutterRadiusM), f.integer("discs", d.discs), f.number("discDiameterM", d.discDiameterM), f.number("discTipM", d.discTipM),
                f.number("discSpacingM", d.discSpacingM), f.number("impactEnergyJ", d.impactEnergyJ), f.number("ramMassKg", d.ramMassKg), f.number("dropM", d.dropM),
                f.number("beltSpeedMps", d.beltSpeedMps), f.number("extensionM", d.extensionM), f.number("matThicknessM", d.matThicknessM),
                f.number("centrifugalN", d.centrifugalN), f.number("vibrationRpm", d.vibrationRpm), f.number("sprayM3PerS", d.sprayM3PerS),
                f.number("ratedLoadKg", d.ratedLoadKg), EngineComponent.Rules.massOr(f));
    }

    // ---------------------------------------------------------------- defaults

    /** A new design's name for a kind. */
    static String defaultName(Kind kind) { return kind.name().toLowerCase(Locale.ROOT).replace('_', ' '); }

    /**
     * Each kind's starting design (and the default of what its JSON leaves out): a mid-size one of its kind, sized as the
     * samples in SampleTools say (most samples are these).
     */
    public static WorkToolDesign of(Kind kind) {
        Builder b = new Builder(kind, defaultName(kind));
        switch (kind) {
            case BACKHOE_BUCKET -> b.size(1.25, 0.9, 1.25).plate(0.012).teeth(5).rake(35).joint(-45, 75).ram(0.12, 0.085, 1.06, 1, 0.65, 30).hydraulics(34.3e6, 0.0036, MAIN_CIRCUIT);
            case TRENCH_BUCKET -> b.size(0.45, 0.7, 0.95).plate(0.01).teeth(3).rake(35).joint(-45, 75).ram(0.1, 0.07, 0.85, 1, 0.55, 30).hydraulics(30e6, 0.0025, MAIN_CIRCUIT);
            case FACE_SHOVEL_BUCKET -> b.size(4.6, 2.2, 3.4).plate(0.05).teeth(6).rake(40).joint(-30, 60).ram(0.28, 0.2, 2.0, 2, 1.5, -60).hydraulics(31e6, 0.03, MAIN_CIRCUIT);
            case LOADER_BUCKET -> b.size(2.95, 0.66, 1.75).plate(0.016).rake(30).joint(-50, 45).ram(0.16, 0.09, 0.75, 1, 0.6, 95).hydraulics(31e6, 0.0045, MAIN_CIRCUIT);
            case DRAGLINE_BUCKET -> b.size(1.6, 1.2, 1.8).plate(0.016).teeth(4).rake(35).joint(-60, 60);
            case CLAMSHELL -> b.size(1.2, 0.9, 1.8).plate(0.016).rake(40).joint(-55, 0).ram(0.12, 0.07, 0.35, 1, 0.45, 120).hydraulics(30e6, 0.0015, AUXILIARY_CIRCUIT);
            case DOZER_BLADE -> b.style(BladeStyle.SU).size(3.4, 1.3, 0.5).plate(0.02).rake(55).joint(-8, 8).ram(0.13, 0.08, 0.25, 1, 1.25, 18).hydraulics(24e6, 0.002, MAIN_CIRCUIT);
            case GRADER_MOLDBOARD -> b.size(3.66, 0.61, 0.3).plate(0.022).rake(45).joint(-15, 15).ram(0.1, 0.06, 0.12, 1, 0.3, 75).hydraulics(24e6, 0.001, MAIN_CIRCUIT).fixed();
            case SNOW_PLOW -> b.size(3.0, 0.95, 0.4).plate(0.006).material(EngineMaterial.STEEL_1045).rake(50).joint(-30, 30).ram(0.07, 0.04, 0.3, 1, 0.63, 71.6)
                    .hydraulics(17e6, 0.0005, MAIN_CIRCUIT);
            case RIPPER -> b.size(0.8, 0.75, 0.35).plate(0.08).teeth(1).rake(45).joint(-15, 15).ram(0.14, 0.09, 0.2, 1, 0.46, 102.5).hydraulics(24e6, 0.002, MAIN_CIRCUIT);
            case HYDRAULIC_HAMMER -> b.size(0.45, 0.45, 2.2).impact(3600);
            case GRAPPLE -> b.size(1.2, 1.0, 1.6).plate(0.03).joint(-50, 0).ram(0.11, 0.06, 0.3, 1, 0.4, 120).hydraulics(30e6, 0.0015, AUXILIARY_CIRCUIT);
            case FORKS -> b.size(1.9, 1.0, 1.2).plate(0.05);
            case QUICK_COUPLER -> b.size(0.5, 0.45, 0.8).plate(0.03).joint(-45, 75).ram(0.12, 0.085, 1.06, 1, 0.65, 30).hydraulics(34.3e6, 0.0036, MAIN_CIRCUIT);
            case COMPACTOR_PLATE -> b.size(0.75, 0.6, 0.95).plate(0.03).material(EngineMaterial.STEEL_1045).vibrator(60e3, 2100);
            case SCRAPER_BOWL -> b.size(3.0, 1.25, 3.2).plate(0.02).rake(30).joint(-6, 12).ram(0.14, 0.09, 0.6, 2, 2.98, 24.8).hydraulics(21e6, 0.004, MAIN_CIRCUIT);
            case DUMP_BODY -> b.size(5.3, 1.3, 7.0).plate(0.025).joint(0, 47).ram(0.25, 0.18, 2.0, 2, 2.8, -3).hydraulics(18e6, 0.008, MAIN_CIRCUIT);
            case CONVEYOR -> b.size(1.0, 0.35, 8.0).plate(0.006).material(EngineMaterial.STEEL_1045).belt(3.0).joint(0, 20).ram(0.08, 0.05, 0.8, 1, 3.2, -3.6)
                    .hydraulics(20e6, 0.0005, AUXILIARY_CIRCUIT);
            case PAVER_HOPPER -> b.size(3.3, 1.0, 2.4).plate(0.008).material(EngineMaterial.STEEL_1045);
            case SCREED -> b.size(3.0, 0.6, 0.4).plate(0.02).material(EngineMaterial.STEEL_1045).screed(1.5, 0.1);
            case MILLING_DRUM -> b.size(2.0, 0.33, 0.4).plate(0.04).cutter(0.51).teeth(162).rake(45);
            case CUTTERHEAD -> b.size(6.0, 6.0, 0.9).plate(0.12).cutter(3.0).discs(40, 0.432, 0.019, 0.075).rake(60);
            case ROADHEADER_HEAD -> b.size(1.8, 1.1, 1.1).plate(0.08).cutter(0.55).teeth(92).rake(45);
            case BUCKET_WHEEL -> b.size(1.3, 0.95, 1.2).plate(0.025).cutter(5.0).teeth(10).rake(40);
            case TRENCHER_CHAIN -> b.size(0.3, 1.8, 2.6).plate(0.012).cutter(0.22).teeth(40).rake(40).joint(-65, 0).ram(0.1, 0.06, 0.45, 1, 0.5, 143)
                    .hydraulics(21e6, 0.001, MAIN_CIRCUIT);
            case AUGER -> b.size(0.6, 0.6, 6.0).plate(0.012).cutter(0.3).teeth(6).rake(40);
            case PILE_HAMMER -> b.size(0.8, 0.8, 5.0).pile(5000, 1.2);
            case BROOM -> b.size(1.83, 0.7, 0.5).plate(0.004).material(EngineMaterial.STEEL_1045).cutter(0.33).rake(60);
            case SNOW_BLOWER -> b.size(1.83, 0.75, 0.8).plate(0.006).material(EngineMaterial.STEEL_1045).cutter(0.3).rake(45);
            case SPRAY_BAR -> b.size(2.4, 0.1, 0.1).plate(0.004).material(EngineMaterial.STAINLESS_321).spray(0.01);
            case MIXER_DRUM -> b.size(2.3, 2.3, 5.55).plate(0.007).material(EngineMaterial.STEEL_1045).cutter(1.15);
            case WORK_PLATFORM -> b.size(2.44, 1.1, 0.91).plate(0.003).material(EngineMaterial.STEEL_1045).rated(230).joint(-45, 45).ram(0.05, 0.03, 0.3, 1, 0.25, 0)
                    .hydraulics(21e6, 0.0002, AUXILIARY_CIRCUIT).fixed();
            case SEGMENT_ERECTOR -> b.size(1.6, 0.8, 1.2).plate(0.04).rated(4000).travel(0.8).ram(0.1, 0.07, 0.85, 2, 0.4, 90).hydraulics(25e6, 0.001, MAIN_CIRCUIT);
        }
        return b.build();
    }

    /** A builder starting from a kind's design (of). */
    public static Builder builder(Kind kind) { return new Builder(of(kind)); }

    /** A builder starting from this design. */
    public Builder toBuilder() { return new Builder(this); }

    /** Sets a design's values in groups (the samples are built with it); every value as in the record. */
    public static final class Builder {
        private String name;
        private final Kind kind;
        private BladeStyle style = BladeStyle.SU;
        private double width = 1, height = 1, length = 1, plate = 0.01;
        private EngineMaterial material = EngineMaterial.STEEL_4340;
        private int teeth;
        private double rake = 40, capacity = Double.NaN;
        private boolean jointed;
        private double jointMin = -30, jointMax = 30, travel = 0.5;
        private double bore = 0.1, rod = 0.07, stroke = 0.5;
        private int ramCount = 1;
        private double arm = 0.5, armDeg = 90, base = Double.NaN, baseDeg = Double.NaN, relief = 30e6, hold = Double.NaN, flow = 0.002;
        private String circuit = MAIN_CIRCUIT;
        private double cutterRadius = 0.5;
        private int discs = 40;
        private double discDiameter = 0.432, discTip = 0.019, discSpacing = 0.08;
        private double impact = 3000, ramMass = 5000, drop = 1.0, beltSpeed = 3, extension = 1, mat = 0.1, centrifugal = 60e3, vibration = 2100, spray = 0.01;
        private double rated = Double.NaN, massGiven = Double.NaN;

        Builder(Kind kind, String name) { this.kind = kind; this.name = name; }

        Builder(WorkToolDesign d) {
            name = d.name; kind = d.toolKind; style = d.bladeStyle; width = d.toolWidthM; height = d.toolHeightM; length = d.toolLengthM; plate = d.plateM;
            material = d.material; teeth = d.teeth; rake = d.rakeDeg; capacity = d.capacityGivenM3; jointed = d.jointed; jointMin = d.jointMinDeg;
            jointMax = d.jointMaxDeg; travel = d.travelM; bore = d.ramBoreM; rod = d.ramRodM; stroke = d.ramStrokeM; ramCount = d.ramCount; arm = d.ramArmM;
            armDeg = d.ramArmDeg; base = d.ramBaseM; baseDeg = d.ramBaseDeg; relief = d.reliefPa; hold = d.holdPa; flow = d.valveFlowM3PerS; circuit = d.circuit;
            cutterRadius = d.cutterRadiusM; discs = d.discs; discDiameter = d.discDiameterM; discTip = d.discTipM; discSpacing = d.discSpacingM;
            impact = d.impactEnergyJ; ramMass = d.ramMassKg; drop = d.dropM; beltSpeed = d.beltSpeedMps; extension = d.extensionM; mat = d.matThicknessM;
            centrifugal = d.centrifugalN; vibration = d.vibrationRpm; spray = d.sprayM3PerS; rated = d.ratedLoadKg; massGiven = d.massGivenKg;
        }

        public Builder name(String v) { name = v; return this; }
        public Builder style(BladeStyle v) { style = v; return this; }
        /** Width, height and length, m. */
        public Builder size(double w, double h, double l) { width = w; height = h; length = l; return this; }
        public Builder plate(double m) { plate = m; return this; }
        public Builder material(EngineMaterial m) { material = m; return this; }
        public Builder teeth(int n) { teeth = n; return this; }
        public Builder rake(double deg) { rake = deg; return this; }
        public Builder capacity(double m3) { capacity = m3; return this; }
        /** A working joint over a range (deg). */
        public Builder joint(double minDeg, double maxDeg) { jointed = true; jointMin = minDeg; jointMax = maxDeg; return this; }
        /** A sliding joint's travel (m), working. */
        public Builder travel(double m) { jointed = true; travel = m; return this; }
        /** No working joint (its values kept). */
        public Builder fixed() { jointed = false; return this; }
        /** The ram: bore, rod, stroke (m), cylinders, its rod pin's distance (m) and angle (deg) in the joint's plane. */
        public Builder ram(double boreM, double rodM, double strokeM, int count, double armM, double armDegrees) {
            bore = boreM; rod = rodM; stroke = strokeM; ramCount = count; arm = armM; armDeg = armDegrees; return this;
        }
        /** The ram's base pin (m, deg; NaN: solved). */
        public Builder base(double m, double deg) { base = m; baseDeg = deg; return this; }
        public Builder hydraulics(double reliefPa, double flowM3PerS, String c) { relief = reliefPa; flow = flowM3PerS; circuit = c; return this; }
        public Builder hold(double pa) { hold = pa; return this; }
        public Builder cutter(double radiusM) { cutterRadius = radiusM; return this; }
        public Builder discs(int n, double diameterM, double tipM, double spacingM) { discs = n; discDiameter = diameterM; discTip = tipM; discSpacing = spacingM; return this; }
        public Builder impact(double joules) { impact = joules; return this; }
        public Builder pile(double ramKg, double dropMetres) { ramMass = ramKg; drop = dropMetres; return this; }
        public Builder belt(double mps) { beltSpeed = mps; return this; }
        public Builder screed(double extensionMetres, double matMetres) { extension = extensionMetres; mat = matMetres; return this; }
        public Builder vibrator(double forceN, double rpm) { centrifugal = forceN; vibration = rpm; return this; }
        public Builder spray(double m3PerS) { spray = m3PerS; return this; }
        public Builder rated(double kg) { rated = kg; return this; }
        public Builder mass(double kg) { massGiven = kg; return this; }

        public WorkToolDesign build() {
            return new WorkToolDesign(name, kind, style, width, height, length, plate, material, teeth, rake, capacity, jointed, jointMin, jointMax, travel, bore, rod,
                    stroke, ramCount, arm, armDeg, base, baseDeg, relief, hold, flow, circuit, cutterRadius, discs, discDiameter, discTip, discSpacing, impact, ramMass,
                    drop, beltSpeed, extension, mat, centrifugal, vibration, spray, rated, massGiven);
        }
    }
}
