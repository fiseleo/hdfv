package dev.mcmod.hdfv.part;

import dev.mcmod.aerocore.api.CollisionSphere;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.storage.Insulation;
import dev.mcmod.aerocore.api.storage.PressureVessel;
import dev.mcmod.aerocore.api.vehicle.DesignException;
import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;
import dev.mcmod.aerocore.api.vehicle.MassProperties;
import dev.mcmod.aerocore.api.vehicle.MassShapes;
import dev.mcmod.aerocore.api.vehicle.Part;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.PartStep;
import dev.mcmod.hdfv.fluid.FuelGrades;
import dev.mcmod.hdfv.fluid.HdfvFluids;
import dev.mcmod.hdfv.fluid.HydrogenStorage;
import dev.mcmod.hdfv.part.engine.EngineComponent;
import dev.mcmod.hdfv.part.engine.EngineMaterial;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A fuel tank (0.8, 油箱) for a vehicle without a body to hold its fuel (a hull, a frame): a welded box of a length,
 * width and height (its origin at the centre) with walls of a material and thickness, holding one of hdfv's fuels,
 * filled to a share when a vehicle is made from the blueprint (a vehicle out of an assembly room starts empty). Its mass
 * is the empty box; the fuel is the vehicle's (AeroCore's Part.Tank, a point at the centre) and the engines that take
 * it burn it. It holds the inside less 3 % (baffles and the pickup).
 * <p>
 * 0.10 (hydrogen; the user chose both liquid and 700 bar): three kinds.
 * <ul>
 * <li>Ordinary: the box above, for fuels kept at ambient temperature and pressure.</li>
 * <li>Cryogenic (低溫): an inner box inside an outer one, both of the walls' thickness, with insulation between
 *     (AeroCore's Insulation: vacuum multilayer, foam, none) of a thickness all round; it holds the inner box. The heat
 *     that leaks through the insulation over the inner box's area boils liquid hydrogen off, vented as the vehicle runs
 *     (Hydrogen.boilOffKgPerS). Insulation mass: multilayer blankets about 40 kg/m^3 of the gap, foam 35.</li>
 * <li>High pressure (高壓): a cylinder along the length with hemispherical ends, as wide as the narrower of width and
 *     height, for 700 bar hydrogen. Its wall carries the working pressure times a safety factor (2.25 for carbon, UN GTR
 *     13): a composite's filament-wound laminate by netting analysis (PressureVessel; along its fibres a laminate carries
 *     about 3.3 times the quasi-isotropic strength the material lists: 2.0 GPa for carbon, Argonne ANL-10/24) on a 5 mm
 *     polyethylene liner under a 2 mm glass-fibre layer (type IV); a metal's own wall (type I). Fittings 4 kg. A
 *     Mirai-like 62 l cylinder weighs within 20 % of Toyota's (87.5 kg the pair).</li>
 * </ul>
 */
public record FuelTankDesign(String name, double lengthM, double widthM, double heightM, double wallM, EngineMaterial material, String fuel,
                             double fill, Kind kind, Insulation insulation, double insulationM, double pressurePa, double safetyFactor)
        implements PartDesign, dev.mcmod.hdfv.design.RecordEditor.Shown {
    public static final String TYPE = "hdfv:fuel_tank";
    public static final double USABLE = 0.97;
    /** A composite's strength along its fibres over the strength it lists; liner and outer layer, m; their densities; fittings, kg. */
    static final double FIBRE = 10.0 / 3, LINER_M = 0.005, OUTER_M = 0.002, LINER_KG_PER_M3 = 950, OUTER_KG_PER_M3 = 1900, FITTINGS_KG = 4;

    public enum Kind { ORDINARY, CRYOGENIC, HIGH_PRESSURE }

    public FuelTankDesign {
        Objects.requireNonNull(name, "name"); Objects.requireNonNull(material, "material"); Objects.requireNonNull(fuel, "fuel");
        Objects.requireNonNull(kind, "kind"); Objects.requireNonNull(insulation, "insulation");
    }

    /** An ordinary tank (as before 0.10). */
    public FuelTankDesign(String name, double lengthM, double widthM, double heightM, double wallM, EngineMaterial material, String fuel, double fill) {
        this(name, lengthM, widthM, heightM, wallM, material, fuel, fill, Kind.ORDINARY, Insulation.VACUUM_MLI, 0.03, HdfvFluids.HYDROGEN_PA, 2.25);
    }

    /** A 56 l steel tank of a car (0.6 by 0.4 by 0.25 m, 1.5 mm walls), of a fuel, full. */
    public static FuelTankDesign car(String fuel) { return new FuelTankDesign("fuel tank", 0.6, 0.4, 0.25, 0.0015, EngineMaterial.STEEL_1045, fuel, 1); }

    /** A Toyota Mirai-like type IV cylinder: 0.89 m long, 0.38 m across, carbon, 700 bar, full. */
    public static FuelTankDesign hydrogenCylinder() {
        return new FuelTankDesign("hydrogen cylinder", 0.89, 0.38, 0.38, 0.002, EngineMaterial.CFRP, HdfvFluids.HYDROGEN, 1, Kind.HIGH_PRESSURE, Insulation.NONE, 0,
                HdfvFluids.HYDROGEN_PA, 2.25);
    }

    /** A BMW Hydrogen 7-like liquid hydrogen tank: about 170 l inside 30 mm of vacuum multilayer insulation, 2 mm stainless walls, full. */
    public static FuelTankDesign liquidHydrogenTank() {
        return new FuelTankDesign("liquid hydrogen tank", 1.0, 0.5, 0.5, 0.002, EngineMaterial.STAINLESS_321, HdfvFluids.LIQUID_HYDROGEN, 1, Kind.CRYOGENIC,
                Insulation.VACUUM_MLI, 0.03, HdfvFluids.HYDROGEN_PA, 2.25);
    }

    @Override public String type() { return TYPE; }

    /** Only the parameters its kind uses. */
    @Override public boolean shows(String parameter) {
        return switch (parameter) {
            case "insulation", "insulationM" -> kind == Kind.CRYOGENIC;
            case "pressurePa", "safetyFactor" -> kind == Kind.HIGH_PRESSURE;
            case "wallM" -> kind != Kind.HIGH_PRESSURE;
            default -> true;
        };
    }

    /** The cylinder's outer radius (high pressure), m. */
    double radiusM() { return Math.min(widthM, heightM) / 2; }

    /** The cylinder's load-bearing wall for its burst pressure (high pressure), m. */
    public double cylinderWallM() {
        double burst = safetyFactor * pressurePa, r = radiusM();
        return composite() ? PressureVessel.compositeCylinderWallM(burst, r, FIBRE * material.yieldPa())
                : PressureVessel.metalCylinderWallM(burst, r, material.yieldPa());
    }

    private boolean composite() { return material.name().equals("cfrp") || material.name().equals("gfrp"); }

    /** The inside's dimensions (length, width, height), m. */
    private double[] inside() {
        double t = switch (kind) { case ORDINARY -> wallM; case CRYOGENIC -> 2 * wallM + insulationM; case HIGH_PRESSURE -> 0; };
        return new double[]{lengthM - 2 * t, widthM - 2 * t, heightM - 2 * t};
    }

    /** What it holds, m^3. */
    public double capacityM3() {
        if (kind == Kind.HIGH_PRESSURE) {
            double r = radiusM() - cylinderWallM() - (composite() ? LINER_M + OUTER_M : 0), length = lengthM - 2 * (radiusM() - r);
            if (!(r > 0) || length < 2 * r) { return 0; }
            return USABLE * (Math.PI * r * r * (length - 2 * r) + 4.0 / 3 * Math.PI * r * r * r);
        }
        double[] in = inside();
        return USABLE * Math.max(0, in[0]) * Math.max(0, in[1]) * Math.max(0, in[2]);
    }

    /** The empty tank's mass, kg. */
    public double massKg() {
        return switch (kind) {
            case ORDINARY -> MassShapes.box(new Vec3d(lengthM, heightM, widthM), wallM).scaled(material.densityKgPerM3()).massKg();
            case CRYOGENIC -> {
                double t = wallM + insulationM;
                double outer = MassShapes.box(new Vec3d(lengthM, heightM, widthM), wallM).scaled(material.densityKgPerM3()).massKg();
                double inner = MassShapes.box(new Vec3d(lengthM - 2 * t, heightM - 2 * t, widthM - 2 * t), wallM).scaled(material.densityKgPerM3()).massKg();
                double gap = (lengthM - 2 * wallM) * (widthM - 2 * wallM) * (heightM - 2 * wallM) - (lengthM - 2 * t) * (widthM - 2 * t) * (heightM - 2 * t);
                yield outer + inner + gap * (insulation == Insulation.FOAM ? 35 : insulation == Insulation.VACUUM_MLI ? 40 : 0);
            }
            case HIGH_PRESSURE -> {
                double r = radiusM(), area = 2 * Math.PI * r * Math.max(0, lengthM - 2 * r) + 4 * Math.PI * r * r;
                double perArea = cylinderWallM() * material.densityKgPerM3() + (composite() ? LINER_M * LINER_KG_PER_M3 + OUTER_M * OUTER_KG_PER_M3 : 0);
                yield area * perArea + FITTINGS_KG;
            }
        };
    }

    /** Liquid hydrogen boiled off a full tank, kg/s (none for another fuel or kind). */
    public double boilOffKgPerS() {
        if (kind != Kind.CRYOGENIC || !HdfvFluids.cryogenic(fuel)) { return 0; }
        double[] in = inside();
        double area = 2 * (in[0] * in[1] + in[1] * in[2] + in[0] * in[2]);
        return HydrogenStorage.boilOffKgPerS(insulation, insulationM, Math.max(0, area));
    }

    /** The fuel's density, kg/m^3. */
    public double fuelDensity() { HdfvFluids.Spec s = HdfvFluids.of(fuel); return s == null ? 800 : s.densityKgPerM3(); }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        EngineComponent.Rules.positive(issues, "lengthM", lengthM);
        EngineComponent.Rules.positive(issues, "widthM", widthM);
        EngineComponent.Rules.positive(issues, "heightM", heightM);
        if (FuelGrades.of(fuel) == null) { issues.add(DesignIssue.invalid("fuel", "Not a fuel: " + fuel)); }
        if (!(fill >= 0 && fill <= 1)) { issues.add(DesignIssue.invalid("fill", "The tank is 0 to 1 full")); }
        if (HdfvFluids.compressedGas(fuel) && kind != Kind.HIGH_PRESSURE) { issues.add(DesignIssue.invalid("kind", "700 bar hydrogen needs a high-pressure cylinder")); }
        if (HdfvFluids.cryogenic(fuel) && kind != Kind.CRYOGENIC) { issues.add(DesignIssue.invalid("kind", "Liquid hydrogen needs a cryogenic tank: in a bare one it boils away")); }
        if (kind == Kind.HIGH_PRESSURE && !HdfvFluids.compressedGas(fuel)) { issues.add(DesignIssue.invalid("fuel", "A high-pressure cylinder holds compressed hydrogen")); }
        if (!issues.isEmpty()) { return issues; }
        if (kind == Kind.HIGH_PRESSURE) {
            if (!(pressurePa > 0 && pressurePa <= 100e6)) { issues.add(DesignIssue.invalid("pressurePa", "The working pressure is 0 to 1000 bar")); return issues; }
            if (!(safetyFactor >= 1 && safetyFactor <= 4)) { issues.add(DesignIssue.invalid("safetyFactor", "The safety factor is 1 to 4")); return issues; }
            DesignIssue.range(issues, "safetyFactor", safetyFactor, 2.0, 3.5, "UN GTR 13 burst ratios (2.25 carbon, 3.5 glass)");
            if (lengthM < 2 * radiusM()) { issues.add(DesignIssue.invalid("lengthM", "A cylinder is at least as long as it is wide")); return issues; }
            if (!(capacityM3() > 0)) { issues.add(DesignIssue.invalid("pressurePa", "The wall for this pressure leaves no room inside")); }
            return issues;
        }
        EngineComponent.Rules.positive(issues, "wallM", wallM);
        double t = kind == Kind.CRYOGENIC ? 2 * wallM + insulationM : wallM;
        if (wallM > 0 && 2 * t >= Math.min(lengthM, Math.min(widthM, heightM))) { issues.add(DesignIssue.invalid(kind == Kind.CRYOGENIC ? "insulationM" : "wallM", "The walls leave no room inside")); }
        if (kind == Kind.CRYOGENIC) {
            if (!(insulationM >= 0)) { issues.add(DesignIssue.invalid("insulationM", "The insulation is at least 0 thick")); }
            else if (insulation != Insulation.NONE) { DesignIssue.range(issues, "insulationM", insulationM, 0.02, 0.15, "cryogenic tanks' insulation"); }
        }
        return issues;
    }

    @Override public Part compile() {
        DesignException.throwIfInvalid(check());
        Vec3d size = new Vec3d(lengthM, heightM, widthM);
        MassProperties mass;
        if (kind == Kind.ORDINARY) { mass = MassShapes.box(size, wallM).scaled(material.densityKgPerM3()); }
        else {
            MassProperties shell = MassShapes.box(size, Math.max(wallM, 0.002));   // a shell's inertia, scaled to the tank's mass
            mass = shell.scaled(massKg() / shell.massKg());
        }
        Part.Tank tank = new Part.Tank("tank", capacityM3(), Vec3d.ZERO, fuel, fill * capacityM3() * fuelDensity());
        return new Compiled(this, mass, List.of(tank), boilOffKgPerS(), Set.of(fuel));
    }

    private static final List<String> TANK_CONDITIONS = List.of(dev.mcmod.hdfv.damage.Conditions.TANK);

    /** The compiled tank: mass and its tank; a cryogenic one vents what boils off. */
    record Compiled(FuelTankDesign design, MassProperties mass, List<Tank> tanks, double boilOffKgPerS, Set<String> fuels) implements Part {
        @Override public List<CollisionSphere> collision() { return List.of(); }
        @Override public List<DesignIssue> issues() { return List.of(); }
        /** 0.25: the tank can be holed (Conditions.TANK): from half used it leaks, which the game drains (neoforge's damage package). */
        @Override public List<String> conditions() { return TANK_CONDITIONS; }
        @Override public void loads(PartStep step) {
            if (boilOffKgPerS > 0) {
                step.burn(fuels, boilOffKgPerS);
                step.diagnostic("boilOffKgPerS", boilOffKgPerS);
            }
        }
    }

    @Override public Map<String, Object> parameters() {
        return Fields.object("lengthM", lengthM, "widthM", widthM, "heightM", heightM, "wallM", wallM, "material", material.toJson(), "fuel", fuel, "fill", fill,
                "kind", kind.name().toLowerCase(java.util.Locale.ROOT), "insulation", insulation.name().toLowerCase(java.util.Locale.ROOT), "insulationM", insulationM,
                "pressurePa", pressurePa, "safetyFactor", safetyFactor);
    }

    public static PartDesign read(Fields f) {
        FuelTankDesign d = car("gasoline");
        return new FuelTankDesign(f.string("name", "fuel tank"), f.number("lengthM", d.lengthM), f.number("widthM", d.widthM), f.number("heightM", d.heightM),
                f.number("wallM", d.wallM), EngineComponent.Rules.material(f, "material", d.material), f.string("fuel", d.fuel), f.number("fill", d.fill),
                EngineComponent.Rules.choice(f, "kind", Kind.class, Kind.ORDINARY), EngineComponent.Rules.choice(f, "insulation", Insulation.class, d.insulation),
                f.number("insulationM", d.insulationM), f.number("pressurePa", d.pressurePa), f.number("safetyFactor", d.safetyFactor));
    }
}
