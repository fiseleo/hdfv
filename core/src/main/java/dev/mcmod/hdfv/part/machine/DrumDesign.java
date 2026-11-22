package dev.mcmod.hdfv.part.machine;

import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.vehicle.DesignException;
import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;
import dev.mcmod.aerocore.api.vehicle.MassProperties;
import dev.mcmod.aerocore.api.vehicle.Part;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.part.DrumPart;
import dev.mcmod.hdfv.part.air.Components;
import dev.mcmod.hdfv.part.engine.EngineComponent;
import dev.mcmod.hdfv.part.engine.EngineMaterial;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * A road roller's drum (hdfv:drum, 0.26, 壓路機滾筒): a steel shell widthM wide and diameterM across (its axis along the
 * part's z, the centre at the origin), smooth or padfoot, with a vibrator: eccentric weights spinning at frequencyHz
 * whose moment m e gives the drum its nominal amplitude amplitudeM (A = m e / M, M the vibrating mass: the drum
 * itself, its frame isolated on rubber mounts). It carries designLoadKg (its share of the machine, itself included:
 * a single-drum roller's static linear load). In the world it is AeroCore's DrumPart (rigid-roller slices across its
 * width, soft-ground compaction and traction, the dynamic force reported for the game's compaction), its rotor "drum"
 * driven by a drum-drive motor or not.
 *
 * <p>Rules: slices every SLICE_M of its width; each slice's stiffness and damping given, or 0 for the design load
 * sinking STATIC_SINK_M (10 mm) at DAMPING_RATIO of critical. Mass (massKg): the shell (its mean circumference x width x
 * thickness), padfoot pads (PAD_HEIGHT_M high, PAD_FILL of the shell's area), two end plates (END_PLATE shells thick,
 * END_FILL for their holes), the vibrator (VIBRATOR_SHARE of the shell and ends: eccentric shaft, weights, bearings and
 * their housings) and the ballast. Its inertia about its axis: the shell and pads at their radius, the end plates as
 * discs, the vibrator and ballast at half the radius.
 *
 * <p>Checked against the Caterpillar CS56B (11 t single drum: 2.13 m wide, 1.53 m across, 1.9 / 0.9 mm at 31 / 34 Hz,
 * 266 / 133 kN): with a 28 mm shell its drum comes to about 3.3 t and its eccentric to 241 kN at 1.9 mm and 31 Hz.
 * Ranges (EXTRAPOLATION) from Bomag, Caterpillar and Dynapac single-drum and tandem rollers: 0.6 to 2.5 m wide, 0.5 to
 * 1.8 m across, 12 to 50 mm shells, 0.2 to 2.5 mm at 25 to 70 Hz, 5 to 45 N/mm static linear load.
 */
public record DrumDesign(String name, double widthM, double diameterM, double shellM, DrumPart.Shell surface, double amplitudeM, double frequencyHz,
                         double designLoadKg, double ballastKg, double sliceStiffnessNPerM, double sliceDampingNsPerM, double brakeNm, double parkNm,
                         EngineMaterial material) implements PartDesign {
    public static final String TYPE = "hdfv:drum";

    /** Slice width (m), the auto stiffness's static sinkage (m) and the auto damping's share of critical. */
    public static final double SLICE_M = 0.25, STATIC_SINK_M = 0.01, DAMPING_RATIO = 0.3;
    /** Padfoot pads: height (m) and their volume's share of the shell's area times it (about 120 pads of 127 cm^2 on an 11 t drum). */
    public static final double PAD_HEIGHT_M = 0.09, PAD_FILL = 0.12;
    /** End plates: thickness in shells, share of solid; the vibrator's share of shell and ends. */
    public static final double END_PLATE = 1.2, END_FILL = 0.8, VIBRATOR_SHARE = 0.12;

    public DrumDesign {
        Objects.requireNonNull(name, "name"); Objects.requireNonNull(surface, "surface"); Objects.requireNonNull(material, "material");
    }

    @Override public String type() { return TYPE; }

    // ---------------------------------------------------------------- mass

    public double shellKg() { return material.densityKgPerM3() * Math.PI * (diameterM - shellM) * widthM * shellM; }

    public double padsKg() { return surface == DrumPart.Shell.PADFOOT ? material.densityKgPerM3() * Math.PI * diameterM * widthM * PAD_HEIGHT_M * PAD_FILL : 0; }

    public double endPlatesKg() { return material.densityKgPerM3() * 2 * Math.PI / 4 * diameterM * diameterM * END_PLATE * shellM * END_FILL; }

    public double vibratorKg() { return VIBRATOR_SHARE * (shellKg() + endPlatesKg()); }

    /** The drum's mass, its vibrating mass, kg: shell, pads, end plates, vibrator and ballast. */
    public double massKg() { return shellKg() + padsKg() + endPlatesKg() + vibratorKg() + ballastKg; }

    /** The eccentric's moment m e, kg m: the nominal amplitude times the vibrating mass. */
    public double eccentricKgM() { return amplitudeM * massKg(); }

    /** The centrifugal force at full vibration, N: m e (2 pi f)^2. */
    public double dynamicForceN() { double w = 2 * Math.PI * frequencyHz; return eccentricKgM() * w * w; }

    /** The static linear load of the design load, N/m of width. */
    public double linearLoadNPerM() { return designLoadKg * MachineRules.G / widthM; }

    /** Slices across the width. */
    public int slices() { return (int) Math.max(1, Math.round(widthM / SLICE_M)); }

    public double sliceStiffness() { return sliceStiffnessNPerM > 0 ? sliceStiffnessNPerM : designLoadKg * MachineRules.G / (slices() * STATIC_SINK_M); }

    public double sliceDamping() {
        return sliceDampingNsPerM > 0 ? sliceDampingNsPerM : 2 * DAMPING_RATIO * Math.sqrt(sliceStiffness() * designLoadKg / slices());
    }

    /** The drum's inertia about its axis, kg m^2. */
    public double inertiaKgM2() {
        double r = diameterM / 2, rm = (diameterM - shellM) / 2, rp = r + PAD_HEIGHT_M / 2;
        return shellKg() * rm * rm + padsKg() * rp * rp + 0.5 * endPlatesKg() * r * r + (vibratorKg() + ballastKg) * 0.25 * r * r;
    }

    public MassProperties mass() {
        double m = Math.max(massKg(), 1e-6), r = diameterM / 2, across = m * (0.5 * r * r + widthM * widthM / 12);
        return new MassProperties(m, Vec3d.ZERO, across, across, Math.max(inertiaKgM2(), 1e-6), 0, 0, 0);
    }

    // ---------------------------------------------------------------- checks

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        EngineComponent.Rules.positive(issues, "widthM", widthM);
        EngineComponent.Rules.positive(issues, "diameterM", diameterM);
        EngineComponent.Rules.positive(issues, "shellM", shellM);
        EngineComponent.Rules.nonnegative(issues, "amplitudeM", amplitudeM);
        EngineComponent.Rules.nonnegative(issues, "frequencyHz", frequencyHz);
        EngineComponent.Rules.positive(issues, "designLoadKg", designLoadKg);
        EngineComponent.Rules.nonnegative(issues, "ballastKg", ballastKg);
        EngineComponent.Rules.nonnegative(issues, "sliceStiffnessNPerM", sliceStiffnessNPerM);
        EngineComponent.Rules.nonnegative(issues, "sliceDampingNsPerM", sliceDampingNsPerM);
        EngineComponent.Rules.nonnegative(issues, "brakeNm", brakeNm);
        EngineComponent.Rules.nonnegative(issues, "parkNm", parkNm);
        material.check(issues, "material");
        if (!issues.isEmpty()) { return issues; }
        if (!(2 * shellM < diameterM)) { issues.add(DesignIssue.invalid("shellM", "A shell as thick as the drum's radius leaves no drum")); return issues; }
        String rollers = "Bomag, Caterpillar and Dynapac single-drum and tandem rollers";
        DesignIssue.range(issues, "widthM", widthM, 0.6, 2.5, rollers);
        DesignIssue.range(issues, "diameterM", diameterM, 0.5, 1.8, rollers);
        DesignIssue.range(issues, "shellM", shellM, 0.012, 0.05, rollers);
        if (amplitudeM > 0) { DesignIssue.range(issues, "amplitudeM", amplitudeM, 0.0002, 0.0025, rollers); }
        if (amplitudeM > 0) { DesignIssue.range(issues, "frequencyHz", frequencyHz, 25, 70, rollers); }
        DesignIssue.range(issues, "linearLoadNPerM", linearLoadNPerM(), 5e3, 45e3, rollers + ": static linear load");
        return issues;
    }

    // ---------------------------------------------------------------- compiling

    public DrumPart.Spec spec() {
        return new DrumPart.Spec(widthM, diameterM / 2, slices(), surface, sliceStiffness(), sliceDamping(), Math.max(inertiaKgM2(), 1e-6), eccentricKgM(),
                frequencyHz, brakeNm, parkNm);
    }

    @Override public Part compile() {
        List<DesignIssue> issues = check();
        DesignException.throwIfInvalid(issues);
        return new DrumPart(this, spec(), mass(), Components.extrapolations(issues));
    }

    // ---------------------------------------------------------------- JSON

    @Override public Map<String, Object> parameters() {
        Map<String, Object> m = Fields.object("widthM", widthM, "diameterM", diameterM, "shellM", shellM, "surface", surface.name().toLowerCase(Locale.ROOT),
                "amplitudeM", amplitudeM, "frequencyHz", frequencyHz, "designLoadKg", designLoadKg, "ballastKg", ballastKg, "sliceStiffnessNPerM", sliceStiffnessNPerM,
                "sliceDampingNsPerM", sliceDampingNsPerM, "brakeNm", brakeNm, "parkNm", parkNm);
        m.put("material", material.toJson());
        return m;
    }

    public static PartDesign read(Fields f) {
        DrumDesign d = standard();
        return new DrumDesign(f.string("name", d.name), f.number("widthM", d.widthM), f.number("diameterM", d.diameterM), f.number("shellM", d.shellM),
                EngineComponent.Rules.choice(f, "surface", DrumPart.Shell.class, d.surface), f.number("amplitudeM", d.amplitudeM), f.number("frequencyHz", d.frequencyHz),
                f.number("designLoadKg", d.designLoadKg), f.number("ballastKg", d.ballastKg), f.number("sliceStiffnessNPerM", d.sliceStiffnessNPerM),
                f.number("sliceDampingNsPerM", d.sliceDampingNsPerM), f.number("brakeNm", d.brakeNm), f.number("parkNm", d.parkNm),
                EngineComponent.Rules.material(f, "material", d.material));
    }

    /** The standard drum: an 11 t single-drum roller's (SampleMachinery.rollerDrum11t). */
    public static DrumDesign standard() { return SampleMachinery.rollerDrum11t(); }
}
