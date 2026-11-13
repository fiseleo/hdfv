package dev.mcmod.hdfv.damage;

import dev.mcmod.aerocore.api.damage.Corrosion;
import dev.mcmod.aerocore.api.damage.ImpactDamage;
import dev.mcmod.aerocore.api.vehicle.MassProperties;
import dev.mcmod.aerocore.api.vehicle.MassSpec;
import dev.mcmod.aerocore.api.vehicle.Part;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.part.AxlePart;
import dev.mcmod.aerocore.api.vehicle.part.LegPart;
import dev.mcmod.aerocore.api.vehicle.part.MotorPart;
import dev.mcmod.aerocore.api.vehicle.part.TransmissionPart;
import dev.mcmod.hdfv.manufacture.Alloy;
import dev.mcmod.hdfv.part.ControlSurfaceDesign;
import dev.mcmod.hdfv.part.FuelTankDesign;
import dev.mcmod.hdfv.part.MotorDesign;
import dev.mcmod.hdfv.part.PropellerDesign;
import dev.mcmod.hdfv.part.air.AirPropellerDesign;
import dev.mcmod.hdfv.part.air.RotorDesign;
import dev.mcmod.hdfv.part.air.WingDesign;
import dev.mcmod.hdfv.part.body.BodyDesign;
import dev.mcmod.hdfv.part.body.BodyModuleDesign;
import dev.mcmod.hdfv.part.chassis.ChassisDesign;
import dev.mcmod.hdfv.part.drive.GearboxDesign;
import dev.mcmod.hdfv.part.drive.TransferCaseDesign;
import dev.mcmod.hdfv.part.engine.EngineMaterial;
import dev.mcmod.hdfv.part.engine.JetEngineDesign;
import dev.mcmod.hdfv.part.engine.PistonEngineDesign;
import dev.mcmod.hdfv.part.engine.PulsejetDesign;
import dev.mcmod.hdfv.part.engine.RamjetDesign;
import dev.mcmod.hdfv.part.engine.ScramjetDesign;
import dev.mcmod.hdfv.part.engine.ShaftTurbineDesign;
import dev.mcmod.hdfv.part.engine.SolidRocketDesign;
import dev.mcmod.hdfv.part.engine.TurboRamjetDesign;
import dev.mcmod.hdfv.part.engine.TurbofanDesign;
import dev.mcmod.hdfv.part.wheel.AxleDesign;
import dev.mcmod.hdfv.part.wheel.LandingGearDesign;

import java.util.List;
import java.util.Map;

/**
 * 0.25: what about each part wears out or gets damaged, and how damage from outside reaches it. The one table the game's
 * damage (neoforge's damage package), the status screen and the repairs read. A part's conditions themselves are what
 * its compiled part declares (AeroCore's Part.conditions; kept by the vehicle by part id and name, 0 new .. 1 failed);
 * this adds, by the part's design:
 *
 * <ul>
 * <li>which conditions a crash reaches and how hard (Hit: the part's damage, a share of the whole part from AeroCore's
 *     ImpactDamage, times the factor; the weakest thing takes it whole, 1, sturdier things a part of it);</li>
 * <li>its toughness, the energy per kg that uses the part up: AeroCore's body-shell figure (SHELL_STEEL_J_PER_KG) times
 *     a factor for what kind of thing it is (KIND factors below, set so the sample sedan's wall crashes come out as
 *     ConditionsTest pins them) and one for what it is made of (Material);</li>
 * <li>its reach (m), from its size: the radius of gyration of its mass about its centre times the square root of three
 *     (half a uniform box's diagonal), at least MIN_REACH_M;</li>
 * <li>what metal corrodes, how thick its wall is (mm) and which condition the corrosion uses up (null: nothing
 *     corrodes: sealed machinery in oil, composites);</li>
 * <li>which conditions fire reaches and how fast (FIRE: a share per point of vanilla fire or lava damage).</li>
 * </ul>
 *
 * Parts without conditions (boxes, ballast, seats, floats and plain hulls, fifth wheels, landing legs, standard
 * components) have no profile: nothing harms them.
 */
public final class Conditions {
    private Conditions() {}

    /** From this much used a condition cannot be repaired: the part (or that piece of it) is replaced. */
    public static final double REPLACE_FROM = 0.8;

    // the names (AeroCore's for its runtime parts, hdfv's own engines' and tanks')
    public static final String TYRES = AxlePart.TYRES, BRAKES = AxlePart.BRAKES, SUSPENSION = AxlePart.SUSPENSION, STRUT = LegPart.STRUT,
            GEARS = TransmissionPart.GEARS, CLUTCH = TransmissionPart.CLUTCH, MOTOR = MotorPart.MOTOR, STRUCTURE = "structure", BLADES = "blades",
            ENGINE = "engine", ANCILLARIES = "ancillaries", HOT_SECTION = "hot_section", TANK = "tank";

    /** Every condition name there is, in the order screens list them. */
    public static final List<String> NAMES = List.of(STRUCTURE, ENGINE, HOT_SECTION, ANCILLARIES, MOTOR, GEARS, CLUTCH, BLADES, TYRES, BRAKES, SUSPENSION, STRUT, TANK);

    /** The language key of a condition's name ("hdfv.condition.tyres"). */
    public static String key(String condition) { return "hdfv.condition." + condition; }

    /** The smallest reach, m (a part whose mass is a point). */
    public static final double MIN_REACH_M = 0.15;

    /**
     * What a kind of part takes per kg of its whole mass, against AeroCore's energy per kg of body shell used up (toughness
     * factors; rules, calibrated together on the sample sedan's wall crashes: ConditionsTest). They are small because a
     * part's mass is not all structure in the load path, and because a condition at 1 means "to be replaced", not "every
     * kg folded":
     * <ul>
     * <li>SHELL, a body: the part is the shell and all it carries (trim, glass, seats and the design load of people and
     *     cargo: the sample sedan's 910 kg, of which the shell is about a third), and it is a write-off once some two
     *     fifths of the shell have folded.</li>
     * <li>FRAME, a chassis: all of it is structure in heavy sections that fold nearer the ideal; AIRFRAME, wings and
     *     control surfaces: all structure, written off as a shell is.</li>
     * <li>MACHINERY, engines: what a crash uses up is the ancillaries, a few kg of radiator, pipes and wiring round a
     *     block or a core that hardly yields; per kg of the whole engine that is little. MOTOR_CASE, an electric motor:
     *     its terminals and cooling. GEARCASE: gears are harmed only once the case itself breaks.</li>
     * <li>RUNNING_GEAR, axles and landing gear: arms, struts and rims bend early for their weight.</li>
     * <li>BLADE, propellers and rotors: thin and fast. DUCT, ramjets, pulsejets and rocket casings: thin tubes.
     *     TANK_SHELL: a tank is used up when it is holed, long before it is crushed.</li>
     * </ul>
     */
    public static final double SHELL = 0.14, FRAME = 0.8, AIRFRAME = 0.4, MACHINERY = 0.065, MOTOR_CASE = 0.3, GEARCASE = 2.5, RUNNING_GEAR = 0.275, BLADE = 0.2, DUCT = 0.4, TANK_SHELL = 0.3;

    /**
     * What a material takes per kg against mild steel, as built (the ratios of specific energy absorption in crushing:
     * Lu and Yu, Energy Absorption of Structures and Materials, 2003: steel tubes 15 to 30 kJ/kg, aluminium 20 to 40,
     * carbon composites 50 to 100, glass composites about half of carbon's; castings and wood break rather than fold),
     * and the metal that corrodes.
     */
    public enum Material {
        STEEL(1.0, Corrosion.Metal.CARBON_STEEL), ALLOY_STEEL(1.2, Corrosion.Metal.LOW_ALLOY_STEEL), STAINLESS(1.3, Corrosion.Metal.STAINLESS_STEEL),
        CAST_IRON(0.4, Corrosion.Metal.CARBON_STEEL), ALUMINIUM(1.35, Corrosion.Metal.ALUMINIUM), MAGNESIUM(1.0, Corrosion.Metal.MAGNESIUM),
        TITANIUM(1.6, Corrosion.Metal.TITANIUM), NICKEL(1.3, Corrosion.Metal.STAINLESS_STEEL), COPPER(0.8, Corrosion.Metal.COPPER_ALLOY),
        CARBON_COMPOSITE(3.0, Corrosion.Metal.NONE), GLASS_COMPOSITE(1.6, Corrosion.Metal.NONE), WOOD(0.6, Corrosion.Metal.NONE), RUBBER(1.0, Corrosion.Metal.NONE);

        public final double toughness;
        public final Corrosion.Metal metal;
        Material(double toughness, Corrosion.Metal metal) { this.toughness = toughness; this.metal = metal; }

        public static Material of(Alloy alloy) {
            return switch (alloy.family) {
                case IRON -> CAST_IRON;
                case STEEL -> alloy == Alloy.STEEL ? STEEL : ALLOY_STEEL;
                case STAINLESS -> STAINLESS;
                case ALUMINIUM -> ALUMINIUM;
                case MAGNESIUM -> MAGNESIUM;
                case TITANIUM -> TITANIUM;
                case NICKEL -> NICKEL;
                case COPPER -> COPPER;
                case COMPOSITE -> alloy == Alloy.CFRP ? CARBON_COMPOSITE : GLASS_COMPOSITE;
                case WOOD -> WOOD;
                case RUBBER -> RUBBER;
            };
        }
        public static Material of(EngineMaterial m) { return of(Alloy.of(m)); }
        public static Material of(MassSpec spec) { return spec instanceof MassSpec.Derived d ? of(Alloy.of(d.material())) : STEEL; }
    }

    /** A condition a crash reaches, and the share of the part's damage it takes (1: all of it). */
    public record Hit(String condition, double factor) {}

    /**
     * A part as damage sees it: what a crash reaches, its toughness (J/kg) and reach (m), what it is made of, the wall
     * (mm) that corrodes and the condition that uses up (null: nothing corrodes).
     */
    public record Profile(List<Hit> crash, double toughnessJPerKg, double reachM, Material material, double wallMm, String corrodes) {
        /** The metal that corrodes (NONE when nothing does). */
        public Corrosion.Metal metal() { return corrodes == null ? Corrosion.Metal.NONE : material.metal; }
    }

    /**
     * How much of a condition a point of vanilla fire damage uses (rules: a vehicle on fire takes one point a second,
     * so tyres and wiring last under a minute and a painted shell some minutes; lava's four points every half second
     * are eight times that, and LAVA_FACTOR more for its heat).
     */
    public static final Map<String, Double> FIRE = Map.of(ANCILLARIES, 0.025, TYRES, 0.03, TANK, 0.02, STRUCTURE, 0.004, MOTOR, 0.02, BLADES, 0.004,
            SUSPENSION, 0.004, STRUT, 0.004, BRAKES, 0.01);
    public static final double LAVA_FACTOR = 2;

    /** Walls where a design gives none (mm): suspension arms and struts, an engine's sheet-metal ancillaries, a marine propeller's blades. */
    public static final double ARM_WALL_MM = 4, ANCILLARY_WALL_MM = 1.5, BLADE_WALL_MM = 8, DUCT_WALL_MM = 2;

    /** A compiled part's profile, or null when it has no conditions. */
    public static Profile of(Part part) {
        if (part == null || part.conditions().isEmpty()) { return null; }
        return of(part.design(), part.mass());
    }

    /** A design's profile with its compiled mass (null for designs without conditions). */
    public static Profile of(PartDesign design, MassProperties mass) {
        double reach = reach(mass);
        return switch (design) {
            case AxleDesign a -> profile(List.of(new Hit(SUSPENSION, 1), new Hit(TYRES, 0.25), new Hit(BRAKES, 0.15)), RUNNING_GEAR, Material.STEEL, reach, ARM_WALL_MM, SUSPENSION);
            case LandingGearDesign g -> profile(List.of(new Hit(STRUT, 1), new Hit(TYRES, 0.25), new Hit(BRAKES, 0.15)), RUNNING_GEAR, Material.ALLOY_STEEL, reach, ARM_WALL_MM, STRUT);
            case GearboxDesign g -> profile(List.of(new Hit(GEARS, 1), new Hit(CLUTCH, 0.3)), GEARCASE, Material.ALUMINIUM, reach, 0, null);
            case TransferCaseDesign t -> profile(List.of(new Hit(GEARS, 1)), GEARCASE, Material.ALUMINIUM, reach, 0, null);
            case MotorDesign m -> profile(List.of(new Hit(MOTOR, 1)), MOTOR_CASE, Material.ALUMINIUM, reach, 0, null);
            case WingDesign w -> profile(List.of(new Hit(STRUCTURE, 1)), AIRFRAME, Material.of(w.structure().skinMaterial()), reach, w.structure().skinThicknessM() * 1000, STRUCTURE);
            case ControlSurfaceDesign c -> {
                Material m = Material.of(c.mass());
                double wall = c.mass() instanceof MassSpec.Derived d && d.thicknessM() > 0 ? d.thicknessM() * 1000 : 0.5 * c.thicknessRatio() * c.chordM() * 1000;
                yield profile(List.of(new Hit(STRUCTURE, 1)), AIRFRAME, m, reach, wall, STRUCTURE);
            }
            case AirPropellerDesign p -> profile(List.of(new Hit(BLADES, 1)), BLADE, Material.of(p.blades().material()), reach, BLADE_WALL_MM, BLADES);
            case RotorDesign r -> profile(List.of(new Hit(BLADES, 1)), BLADE, Material.of(r.blades().material()), reach, BLADE_WALL_MM, BLADES);
            case PropellerDesign p -> {
                Material m = p.mass() instanceof MassSpec.Derived ? Material.of(p.mass()) : Material.COPPER;
                yield profile(List.of(new Hit(BLADES, 1)), BLADE, m, reach, BLADE_WALL_MM, BLADES);
            }
            case BodyDesign b -> {
                // what the shell is mostly made of: the material of the module with the most skin (length x skin thickness; a shell is of one material as a rule)
                BodyModuleDesign most = b.modules().get(0);
                double skin = Double.MAX_VALUE;
                for (BodyModuleDesign m : b.modules()) {
                    skin = Math.min(skin, m.skinM());
                    if (m.lengthM() * m.skinM() > most.lengthM() * most.skinM()) { most = m; }
                }
                yield profile(List.of(new Hit(STRUCTURE, 1)), SHELL, Material.of(most.material()), reach, skin * 1000, STRUCTURE);
            }
            // a ladder's or a backbone's rails, a space frame's tubes
            case ChassisDesign c -> profile(List.of(new Hit(STRUCTURE, 1)), FRAME, Material.of(c.frame().material()), reach,
                    (c.frame().kind() == dev.mcmod.hdfv.part.chassis.ChassisSettingsDesign.Kind.SPACE_FRAME ? c.frame().tubeWallM() : c.frame().railWallM()) * 1000, STRUCTURE);
            // what a crash uses up is the ancillaries (radiator, pipes, wiring: as tough as aluminium sheet and tube whatever
            // the block is cast of); what rusts is the block's metal (its water jacket and what is bolted to it)
            case PistonEngineDesign e -> profile(List.of(new Hit(ANCILLARIES, 1), new Hit(ENGINE, 0.3)), MACHINERY * Material.ALUMINIUM.toughness, Material.of(e.block().material()),
                    reach, ANCILLARY_WALL_MM, ANCILLARIES, false);
            case JetEngineDesign e -> turbine(reach);
            case TurbofanDesign e -> turbine(reach);
            case ShaftTurbineDesign e -> turbine(reach);
            case TurboRamjetDesign e -> turbine(reach);
            case RamjetDesign e -> profile(List.of(new Hit(STRUCTURE, 1)), DUCT, Material.STAINLESS, reach, DUCT_WALL_MM, STRUCTURE);
            case ScramjetDesign e -> profile(List.of(new Hit(STRUCTURE, 1)), DUCT, Material.NICKEL, reach, DUCT_WALL_MM, STRUCTURE);
            case PulsejetDesign e -> profile(List.of(new Hit(STRUCTURE, 1)), DUCT, Material.STEEL, reach, DUCT_WALL_MM, STRUCTURE);
            case SolidRocketDesign e -> profile(List.of(new Hit(STRUCTURE, 1)), DUCT, Material.of(e.casing().material()), reach, e.casing().wallThicknessM() * 1000, STRUCTURE);
            case FuelTankDesign t -> profile(List.of(new Hit(TANK, 1)), TANK_SHELL, Material.of(t.material()), reach, t.wallM() * 1000, TANK);
            // 0.25: a storage box is a shell as a body is (its walls; what it carries is not part of it)
            case dev.mcmod.hdfv.part.StorageBoxDesign s -> profile(List.of(new Hit(STRUCTURE, 1)), SHELL, Material.of(s.material()), reach, s.wallM() * 1000, STRUCTURE);
            default -> null;
        };
    }

    /** A gas turbine: thin casings and accessories round a tough core; stainless and nickel, which hardly corrode. */
    private static Profile turbine(double reach) {
        return profile(List.of(new Hit(ANCILLARIES, 1), new Hit(HOT_SECTION, 0.3)), MACHINERY, Material.STAINLESS, reach, ANCILLARY_WALL_MM, ANCILLARIES);
    }

    private static Profile profile(List<Hit> crash, double kind, Material material, double reach, double wallMm, String corrodes) {
        return profile(crash, kind, material, reach, wallMm, corrodes, true);
    }

    /** The same; byMaterial false: the kind's factor is the whole of its toughness (the material only says what rusts). */
    private static Profile profile(List<Hit> crash, double kind, Material material, double reach, double wallMm, String corrodes, boolean byMaterial) {
        boolean rusts = corrodes != null && wallMm > 0 && material.metal != Corrosion.Metal.NONE;
        return new Profile(crash, ImpactDamage.SHELL_STEEL_J_PER_KG * kind * (byMaterial ? material.toughness : 1), reach, material, rusts ? wallMm : 0, rusts ? corrodes : null);
    }

    /** A mass's reach, m: sqrt(3) times its radius of gyration about its centre, at least MIN_REACH_M. */
    public static double reach(MassProperties mass) {
        double m = mass.massKg(), trace = mass.xx() + mass.yy() + mass.zz();
        if (!(m > 0) || !(trace > 0)) { return MIN_REACH_M; }
        return Math.max(MIN_REACH_M, Math.sqrt(1.5 * trace / m));
    }
}
