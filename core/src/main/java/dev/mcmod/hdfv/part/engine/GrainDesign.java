package dev.mcmod.hdfv.part.engine;

import dev.mcmod.aerocore.api.propellant.Propellant;
import dev.mcmod.aerocore.api.propellant.SolidRocket;
import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A solid rocket's propellant grain (藥柱, 0.10; AeroCore's SolidRocket.Grain): the propellant (KNSB, the sugar and
 * saltpetre of amateur motors; APCP, ammonium perchlorate composite), the grain's form (BATES segments burning on their
 * cores and ends, a tube burning on its core, an end burner burning like a cigarette), its outer and core diameters,
 * each segment's length and how many there are. The grain is what the booster burns, carried as its own tank (the
 * propellant's mass rides with the vehicle and goes as it burns); the inhibitor and liner are the casing's.
 */
public record GrainDesign(String name, String propellant, SolidRocket.GrainKind kind, double outerDiameterM, double coreDiameterM, double segmentLengthM,
                          int segments) implements EngineComponent, dev.mcmod.hdfv.design.RecordEditor.Shown {
    public static final String TYPE = "hdfv:grain";

    /** An end burner has no core. */
    @Override public boolean shows(String parameter) { return !(parameter.equals("coreDiameterM") && kind == SolidRocket.GrainKind.END_BURNER); }
    public static final List<String> PROPELLANTS = List.of("knsb", "apcp");

    public GrainDesign { Objects.requireNonNull(name, "name"); Objects.requireNonNull(propellant, "propellant"); Objects.requireNonNull(kind, "kind"); }

    @Override public String type() { return TYPE; }

    public Propellant propellantModel() { return Propellant.byName(propellant); }
    public SolidRocket.Grain grain() { return new SolidRocket.Grain(kind, outerDiameterM, kind == SolidRocket.GrainKind.END_BURNER ? 0 : coreDiameterM, segmentLengthM, segments); }

    /** The propellant's volume (m^3) and mass (kg) of a full grain. */
    public double volumeM3() { return grain().volume(0); }
    public double propellantKg() { return volumeM3() * propellantModel().densityKgPerM3(); }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        if (!PROPELLANTS.contains(propellant)) { issues.add(DesignIssue.invalid("propellant", "The propellant is KNSB or APCP")); }
        EngineComponent.Rules.positive(issues, "outerDiameterM", outerDiameterM);
        EngineComponent.Rules.positive(issues, "segmentLengthM", segmentLengthM);
        EngineComponent.Rules.count(issues, "segments", segments, 1, 40);
        if (kind != SolidRocket.GrainKind.END_BURNER && !(coreDiameterM > 0 && coreDiameterM < outerDiameterM)) {
            issues.add(DesignIssue.invalid("coreDiameterM", "The core is narrower than the grain"));
        }
        if (issues.isEmpty()) {
            try { grain(); } catch (IllegalArgumentException e) { issues.add(DesignIssue.invalid("coreDiameterM", e.getMessage())); }
        }
        if (issues.isEmpty() && kind == SolidRocket.GrainKind.BATES) {
            // Nakka: a BATES segment burns neutrally near L = (3 D + D_core) / 2
            DesignIssue.range(issues, "segmentLengthOverNeutral", segmentLengthM / ((3 * outerDiameterM + coreDiameterM) / 2), 0.8, 1.2, "neutral-burning BATES segments");
        }
        return issues;
    }

    @Override public double massKg() { return 0; }

    @Override public Map<String, Object> parameters() {
        return Fields.object("propellant", propellant, "kind", kind.name().toLowerCase(java.util.Locale.ROOT), "outerDiameterM", outerDiameterM,
                "coreDiameterM", coreDiameterM, "segmentLengthM", segmentLengthM, "segments", segments);
    }

    static GrainDesign read(String name, Fields f) {
        return new GrainDesign(name, f.string("propellant", "knsb"), EngineComponent.Rules.choice(f, "kind", SolidRocket.GrainKind.class, SolidRocket.GrainKind.BATES),
                f.number("outerDiameterM", 0.075), f.number("coreDiameterM", 0.025), f.number("segmentLengthM", 0.125), (int) f.number("segments", 4));
    }
}
