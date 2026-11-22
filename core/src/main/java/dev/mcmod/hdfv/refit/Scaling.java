package dev.mcmod.hdfv.refit;

import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.vehicle.MassSpec;
import dev.mcmod.aerocore.api.vehicle.Mount;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.VehicleBlueprint;
import dev.mcmod.hdfv.design.RecordEditor;
import dev.mcmod.hdfv.part.engine.EngineMaterial;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 0.16: a whole vehicle scaled by one factor s (the user's 「整台等比例縮放」, the refit shop's size page), every part's
 * design by geometric similarity at the same speeds: lengths go s times, so do tip and piston speeds' arms, and shafts
 * turn 1/s as fast, so the same gears give the same road speed for the same engine speed share; pressures, temperatures,
 * angles, ratios, materials and counts stay. What follows from it is the physics' own (the square-cube law: an engine's
 * power grows as s^2, its mass as s^3; a structure's stresses grow with s; a tyre's rated load at its pressure as s^2,
 * so its pressure is raised with s to carry s^3).
 * <ul>
 * <li>By a parameter's unit (its name's SI suffix, RecordEditor.unit): m, in s; m^2 s^2; m^3 s^3; kg m^2 s^5; kg s^3
 *   (masses given, design loads and gross masses, ballast); kg/s, m^3/s, cm^3/min s^2 (flows at the same velocities);
 *   N s^2; N m s^3; N/m (springs, anti-roll bars) s^2 and N s/m (dampers) s^2.5: the ride frequency goes as s^-0.5,
 *   the damping ratio stays (Froude); N m s/rad (viscous couplings) s^4; rpm and Hz 1/s; Pa, K, C, degrees and ratios
 *   stay.</li>
 * <li>Exceptions by name (EXPONENTS): an occupant's mass stays (people do not scale), densities and areal densities
 *   are not volumes or areas, a design altitude is not a size, a tyre's and an air strut's and a brake's pressures go
 *   with s (load over area).</li>
 * <li>Vectors (a box's size, a given mass's centre, hardpoints) and mounts' offsets go s times; a shell's thickness s
 *   times, a given mass s^3; lists (a body's modules, a chassis's stations, a hull's stations, stickers) element by
 *   element. A body's mount points and seats that are fixed in metres in its design (an axle's place in a short hood,
 *   a seat's height above the floor) stay: people do not get smaller, so a car scaled small enough fails its seats'
 *   width check, which is the point.</li>
 * </ul>
 * s = 1 gives back the same designs (the same JSON). Every numeric parameter of every kind has a rule (ScaleTest).
 */
public final class Scaling {
    private Scaling() {}

    /** The range the rules are meant for; outside it the shop warns (the designs' own checks still apply). */
    public static final double MIN = 0.5, MAX = 2;

    /** The exponent of a unit (RecordEditor.unit). */
    private static final Map<String, Double> UNITS = Map.ofEntries(
            Map.entry("m", 1.0), Map.entry("in", 1.0), Map.entry("m²", 2.0), Map.entry("m³", 3.0), Map.entry("kg·m²", 5.0), Map.entry("kg", 3.0),
            Map.entry("kg/s", 2.0), Map.entry("m³/s", 2.0), Map.entry("cm³/min", 2.0), Map.entry("N", 2.0), Map.entry("N·m", 3.0),
            Map.entry("N/m", 2.0), Map.entry("N·s/m", 2.5), Map.entry("N·m·s/rad", 4.0), Map.entry("rpm", -1.0),
            Map.entry("Pa", 0.0), Map.entry("K", 0.0), Map.entry("°C", 0.0), Map.entry("°", 0.0));

    /** Exponents by "Owner.name" or "name" where the suffix says otherwise or says nothing. */
    private static final Map<String, Double> EXPONENTS = Map.ofEntries(
            Map.entry("occupantKg", 0.0), Map.entry("cargoDensityKgM3", 0.0), Map.entry("densityKgPerM3", 0.0), Map.entry("floorKgM2", 1.0),
            Map.entry("designAltitudeM", 0.0), Map.entry("kickUpX", 1.0), Map.entry("neckX", 1.0), Map.entry("dValueKn", 2.0), Map.entry("crankTimeS", 0.0), Map.entry("maxPowerW", 2.0), Map.entry("flapSpringNmPerRad", 3.0),
            Map.entry("reedFrequencyHz", -1.0), Map.entry("TyreDesign.pressurePa", 1.0), Map.entry("TyreDesign.ratedPressurePa", 1.0),
            Map.entry("StrutDesign.extendedPressurePa", 1.0), Map.entry("BrakeDesign.maxPressurePa", 1.0),
            Map.entry("Point.y", 1.0), Map.entry("Point.z", 1.0), Map.entry("OnBody.x", 1.0), Map.entry("LampSpec.x", 1.0), Map.entry("GearsetDesign.forwardRatios", 0.0),
            // 0.26: the machines (a drum's frequency as a shaft's speed; pins are lengths; ratios, resistances and the jack's time stay)
            Map.entry("DrumDesign.frequencyHz", -1.0), Map.entry("HookDesign.dampingRatio", 0.0), Map.entry("HydraulicMotorDesign.reductionRatio", 0.0),
            Map.entry("HydraulicPumpDesign.powerLimitW", 2.0), Map.entry("LinkDesign.basePinXM", 1.0), Map.entry("LinkDesign.basePinYM", 1.0),
            Map.entry("LinkDesign.basePinZM", 1.0), Map.entry("LinkDesign.rodPinXM", 1.0), Map.entry("LinkDesign.rodPinYM", 1.0), Map.entry("LinkDesign.rodPinZM", 1.0),
            Map.entry("LinkDesign.freeFriction", 3.0), Map.entry("LinkDesign.slewRatio", 0.0), Map.entry("OutriggerDesign.extensionTimeS", 0.0),
            Map.entry("TrackDesign.internalResistance", 0.0), Map.entry("TrackDesign.lateralResistance", 0.0),
            Map.entry("WorkToolDesign.beltSpeedMps", 0.0), Map.entry("WorkToolDesign.impactEnergyJ", 3.0));

    /** Parameters without a unit that are ratios, shares, coefficients, counts of a kind, times or speeds: they stay. */
    static final Set<String> DIMENSIONLESS = Set.of("ackermann", "areaRatio", "aspectRatio", "axialTipRatio", "axleAt", "backPressureFactor",
            "beltFraction", "biasRatio", "bleedShare", "boosterPressureRatio", "bypassPressureLoss", "bypassRatio", "camber", "camberPosition",
            "cargoFill", "characteristicVelocityEfficiency", "chordFraction", "combustionEfficiency", "compoundGrip", "compressionRatio",
            "compressorEfficiency", "contractionRatio", "coolingBleed", "coolingEffectiveness", "counterweightRatio", "creepShare", "designMach",
            "designSpeedMps", "dragCoefficient", "ductRecovery", "efficiency", "exhaustPressureRatio", "exhaustThroatRatio", "exitAreaRatio",
            "expansionRatio", "fill", "filterLoss", "finalRatio", "flameholderLoss", "frictionCoefficient", "frictionFactor", "fromSpan",
            "frontShare", "frontSpar", "fuelFill", "fullLoadLambda", "fullMach", "fullness", "glassOpacity", "handbrakeShare", "highRatio",
            "hingeOffset", "hubRatio", "hubTipRatio", "idleFraction", "injectantVelocityRatio", "inletMach", "intakeThroatRatio",
            "intercoolerEffectiveness", "internalContraction", "isolatorLengthOverHeight", "kineticEnergyEfficiency", "leanLimit", "levelRatePerS",
            "lightMach", "lightOffFraction", "linerRoughness", "loadFactor", "lockFactor", "lowRatio", "lower1", "lower2", "lower3", "lower4",
            "lowerExponent", "maxBoostRatio", "maxEquivalenceRatio", "maxMach", "maxRatio", "maxSpeedFraction", "maxThroatRatio",
            "mechanicalEfficiency", "minMach", "mixingTimeS", "motorOctane", "noseBluntness", "nozzleAreaRatio", "nozzleVelocityCoefficient",
            "openMach", "partLoadLambda", "pitchDamping", "pitchRatio", "polytropicEfficiency", "portFlowCoefficient", "preloadShare",
            "pressureLoss", "pressureRatio", "pressureRetention", "ratio", "ratioRatePerS", "rearSpar", "reboundMultiple", "recovery",
            "researchOctane", "retractTimeS", "reverseRatio", "ringTensionRatio", "rollDamping", "roofWidthRatio", "rootChordRatio", "rootCutout",
            "rootThickness", "safetyFactor", "shiftTimeS", "stallTorqueRatio", "startRate", "steerCrossoverMps", "steerRatio", "steerRatioHigh",
            "sternShape", "stringerShare", "subsonicRecovery", "supercriticalMargin", "supportedAt", "surgeMargin", "surgeReserve", "thickness",
            "thicknessRatio", "tipChordRatio", "tipTaper", "tipThickness", "toSpan", "trailingEdgeThickness", "turbineEfficiency", "upper1",
            "upper2", "upper3", "upper4", "upperExponent", "windowShare", "yawDamping", "aspect", "t", "eta", "chord", "across", "up",
            "grilleWidth", "grilleHeight", "lampWidth", "lampHeight", "bumperHeight");

    /** The exponent of a numeric parameter of a record type, or null when there is no rule for it. */
    public static Double exponent(Class<?> owner, String name) {
        Double e = EXPONENTS.get(owner.getSimpleName() + "." + name);
        if (e == null) { e = EXPONENTS.get(name); }
        if (e != null) { return e; }
        if (owner == dev.mcmod.hdfv.part.wheel.HardpointsDesign.class) { return 1.0; }   // points relative to the hub
        String unit = RecordEditor.unit(name);
        if (!unit.isEmpty()) { return UNITS.get(unit); }
        return DIMENSIONLESS.contains(name) ? 0.0 : null;
    }

    /** Whether s is in the validated range. */
    public static boolean validated(double s) { return s >= MIN - 1e-12 && s <= MAX + 1e-12; }

    /** A blueprint scaled: every part's design, every mount's offset (its angles and what it is attached to stay). */
    public static VehicleBlueprint scaled(VehicleBlueprint b, double s) {
        if (!(s > 0) || !Double.isFinite(s)) { throw new IllegalArgumentException("A scale is positive"); }
        List<VehicleBlueprint.Entry> parts = new ArrayList<>(b.parts().size());
        for (VehicleBlueprint.Entry e : b.parts()) {
            Mount m = e.mount();
            Mount moved = new Mount(tidy(m.position().scale(s)), m.yawDeg(), m.pitchDeg(), m.rollDeg(), m.attachPart(), m.attachPoint());
            parts.add(new VehicleBlueprint.Entry(e.id(), scaled(e.design(), s), s == 1 ? m : moved, e.bindings()));
        }
        return new VehicleBlueprint(b.name(), parts, b.connections());
    }

    /** A part's design scaled (IllegalArgumentException when a scaled value is refused by the design's own records). */
    public static PartDesign scaled(PartDesign d, double s) {
        if (!(s > 0) || !Double.isFinite(s)) { throw new IllegalArgumentException("A scale is positive"); }
        if (s == 1) { return d; }
        if (!(d instanceof Record r)) { throw new IllegalArgumentException("Not a record: " + d.type()); }
        return (PartDesign) record(r, s);
    }

    private static Record record(Record r, double s) {
        RecordComponent[] components = r.getClass().getRecordComponents();
        Object[] args = new Object[components.length];
        Class<?>[] types = new Class<?>[components.length];
        for (int i = 0; i < components.length; i++) {
            RecordComponent c = components[i];
            types[i] = c.getType();
            args[i] = value(r.getClass(), c.getName(), read(c, r), s);
        }
        try {
            Constructor<?> constructor = r.getClass().getDeclaredConstructor(types);
            constructor.setAccessible(true);
            return (Record) constructor.newInstance(args);
        } catch (InvocationTargetException e) {
            throw e.getCause() instanceof RuntimeException x ? x : new IllegalArgumentException(e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Object value(Class<?> owner, String name, Object v, double s) {
        return switch (v) {
            case null -> null;
            case Double d -> { double f = factor(owner, name, s); yield f == 1 ? d : tidy(d * f); }
            case double[] a -> {
                double f = factor(owner, name, s);
                double[] out = a.clone();
                if (f != 1) { for (int i = 0; i < out.length; i++) { out[i] = tidy(out[i] * f); } }
                yield out;
            }
            case Vec3d p -> tidy(p.scale(s));
            case MassSpec.Given g -> new MassSpec.Given(tidy(g.massKg() * s * s * s), g.centre() == null ? null : tidy(g.centre().scale(s)));
            case MassSpec.Derived m -> new MassSpec.Derived(m.material(), tidy(m.thicknessM() * s));
            case EngineMaterial m -> m;
            case List<?> list -> {
                List<Object> out = new ArrayList<>(list.size());
                for (Object o : list) { out.add(o instanceof Record x && own(x) ? record(x, s) : o); }
                yield List.copyOf(out);
            }
            case Record x when own(x) -> record(x, s);
            default -> v;
        };
    }

    /** A scaled value to 12 significant digits: 2.34, not 2.3400000000000003 (what the screens show and the JSON keeps). */
    static double tidy(double v) {
        if (v == 0 || !Double.isFinite(v)) { return v; }
        return new java.math.BigDecimal(v).round(new java.math.MathContext(12)).doubleValue();
    }

    private static Vec3d tidy(Vec3d p) { return new Vec3d(tidy(p.x()), tidy(p.y()), tidy(p.z())); }

    /** hdfv's own records (pages, modules, stations, stickers) are scaled through; AeroCore's and the JDK's are values. */
    private static boolean own(Record r) { return r.getClass().getName().startsWith("dev.mcmod.hdfv."); }

    private static double factor(Class<?> owner, String name, double s) {
        Double e = exponent(owner, name);
        return e == null || e == 0 ? 1 : Math.pow(s, e);
    }

    private static Object read(RecordComponent c, Record r) {
        try {
            c.getAccessor().setAccessible(true);
            return c.getAccessor().invoke(r);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
