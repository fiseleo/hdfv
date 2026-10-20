package dev.mcmod.hdfv.part.air;

import dev.mcmod.aerocore.api.aero.WingStructure;
import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;
import dev.mcmod.hdfv.look.Sticker;
import dev.mcmod.hdfv.part.engine.EngineComponent;
import dev.mcmod.hdfv.part.engine.EngineMaterial;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A wing's structure (結構): a spar box between the front and rear spar stations (fractions of the chord) of a
 * material, its caps (the skin panels over the box) and webs thinning to tipTaper of their root thickness at the tip;
 * the skin outside the box and the ribs of another material. The design load: the mass the wing carries times the
 * limit load factor times 1.5 (the ultimate factor of FAR 23.303 and 25.303). The stresses and mass come from
 * AeroCore's WingStructure (WingDesign checks them); a given mass replaces the estimate.
 * <p>
 * 0.12, looks only: the wing's paint (blank: the light grey it had) and its finish.
 * <p>
 * 0.13, looks only: the stickers (Sticker, placed by eta, chord, surface and side; at most Sticker.MAX), edited in the
 * paint booth, not the design screen. A wing without stickers writes no "stickers" key (0.12's JSON); a blueprint
 * without the key reads back with none.
 */
public record WingStructureDesign(String name, EngineMaterial boxMaterial, EngineMaterial skinMaterial, double frontSpar, double rearSpar,
                                  double capThicknessM, double webThicknessM, double tipTaper, double skinThicknessM, double ribSpacingM,
                                  double ribThicknessM, double supportedMassKg, double loadFactor, double massGivenKg, String paintColor,
                                  dev.mcmod.hdfv.part.body.BodySettingsDesign.PaintFinish paintFinish, List<Sticker> stickers) implements EngineComponent {
    public static final String TYPE = "hdfv:wing_structure";
    /** Ultimate over limit load (FAR 23.303, 25.303). */
    public static final double ULTIMATE = 1.5;

    public WingStructureDesign {
        Objects.requireNonNull(name, "name"); Objects.requireNonNull(boxMaterial, "boxMaterial"); Objects.requireNonNull(skinMaterial, "skinMaterial");
        Objects.requireNonNull(paintColor, "paintColor"); Objects.requireNonNull(paintFinish, "paintFinish");
        stickers = List.copyOf(Objects.requireNonNull(stickers, "stickers"));
    }

    /** 0.11's structure, painted as it was. */
    public WingStructureDesign(String name, EngineMaterial boxMaterial, EngineMaterial skinMaterial, double frontSpar, double rearSpar, double capThicknessM,
                               double webThicknessM, double tipTaper, double skinThicknessM, double ribSpacingM, double ribThicknessM, double supportedMassKg,
                               double loadFactor, double massGivenKg) {
        this(name, boxMaterial, skinMaterial, frontSpar, rearSpar, capThicknessM, webThicknessM, tipTaper, skinThicknessM, ribSpacingM, ribThicknessM,
                supportedMassKg, loadFactor, massGivenKg, "", dev.mcmod.hdfv.part.body.BodySettingsDesign.PaintFinish.STANDARD);
    }

    /** 0.12's structure and paint, without stickers. */
    public WingStructureDesign(String name, EngineMaterial boxMaterial, EngineMaterial skinMaterial, double frontSpar, double rearSpar, double capThicknessM,
                               double webThicknessM, double tipTaper, double skinThicknessM, double ribSpacingM, double ribThicknessM, double supportedMassKg,
                               double loadFactor, double massGivenKg, String paintColor, dev.mcmod.hdfv.part.body.BodySettingsDesign.PaintFinish paintFinish) {
        this(name, boxMaterial, skinMaterial, frontSpar, rearSpar, capThicknessM, webThicknessM, tipTaper, skinThicknessM, ribSpacingM, ribThicknessM,
                supportedMassKg, loadFactor, massGivenKg, paintColor, paintFinish, List.of());
    }

    @Override public String type() { return TYPE; }

    public WingStructure.Box box() {
        return new WingStructure.Box(frontSpar, rearSpar, capThicknessM, webThicknessM, tipTaper, skinThicknessM, ribSpacingM, ribThicknessM);
    }
    public WingStructure.Materials materials() {
        return new WingStructure.Materials(boxMaterial.densityKgPerM3(), boxMaterial.modulusPa(), skinMaterial.densityKgPerM3());
    }
    /** The ultimate design lift, N. */
    public double designLiftN() { return ULTIMATE * loadFactor * supportedMassKg * 9.80665; }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        if (!(frontSpar >= 0.05 && frontSpar <= 0.4)) { issues.add(DesignIssue.invalid("frontSpar", "The front spar must be at 5 to 40 % of the chord")); }
        if (!(rearSpar <= 0.85 && rearSpar > frontSpar + 0.1)) { issues.add(DesignIssue.invalid("rearSpar", "The rear spar must be at least 10 % of the chord behind the front one, at most at 85 %")); }
        EngineComponent.Rules.positive(issues, "capThicknessM", capThicknessM);
        EngineComponent.Rules.positive(issues, "webThicknessM", webThicknessM);
        EngineComponent.Rules.positive(issues, "skinThicknessM", skinThicknessM);
        EngineComponent.Rules.positive(issues, "ribThicknessM", ribThicknessM);
        if (!(tipTaper > 0 && tipTaper <= 1)) { issues.add(DesignIssue.invalid("tipTaper", "The tip's thickness must be a share (0 to 1) of the root's")); }
        if (!(ribSpacingM > 0.05)) { issues.add(DesignIssue.invalid("ribSpacingM", "Ribs must be at least 50 mm apart")); }
        EngineComponent.Rules.nonnegative(issues, "supportedMassKg", supportedMassKg);
        if (!(loadFactor >= 1 && loadFactor <= 15)) { issues.add(DesignIssue.invalid("loadFactor", "The load factor must be 1 to 15")); }
        else { DesignIssue.range(issues, "loadFactor", loadFactor, 2.5, 9, "limit load factors of FAR 23 and 25 categories to aerobatic and fighter aircraft"); }
        boxMaterial.check(issues, "boxMaterial");
        skinMaterial.check(issues, "skinMaterial");
        EngineComponent.Rules.mass(issues, massGivenKg);
        if (!dev.mcmod.hdfv.design.RecordEditor.isColor(paintColor, true)) { issues.add(DesignIssue.invalid("paintColor", "A colour is #RRGGBB, or blank")); }
        Sticker.check(issues, "stickers", stickers, true);
        return issues;
    }

    /** The given mass, or 0: the wing estimates it from its planform (WingDesign.structure()). */
    @Override public double massKg() { return Double.isNaN(massGivenKg) ? 0 : massGivenKg; }

    @Override public Map<String, Object> parameters() {
        Map<String, Object> m = Fields.object("boxMaterial", boxMaterial.toJson(), "skinMaterial", skinMaterial.toJson(), "frontSpar", frontSpar, "rearSpar", rearSpar,
                "capThicknessM", capThicknessM, "webThicknessM", webThicknessM, "tipTaper", tipTaper, "skinThicknessM", skinThicknessM,
                "ribSpacingM", ribSpacingM, "ribThicknessM", ribThicknessM, "supportedMassKg", supportedMassKg, "loadFactor", loadFactor,
                "massKg", EngineComponent.Rules.mass(massGivenKg), "paintColor", paintColor, "paintFinish", paintFinish.name().toLowerCase(java.util.Locale.ROOT));
        if (!stickers.isEmpty()) { m.put("stickers", Sticker.toJson(stickers)); }
        return m;
    }

    static WingStructureDesign read(String name, Fields f) {
        return new WingStructureDesign(name, EngineComponent.Rules.material(f, "boxMaterial", EngineMaterial.ALUMINIUM_2024),
                EngineComponent.Rules.material(f, "skinMaterial", EngineMaterial.ALUMINIUM_2024), f.number("frontSpar", 0.15), f.number("rearSpar", 0.65),
                f.number("capThicknessM", 0.003), f.number("webThicknessM", 0.0015), f.number("tipTaper", 0.4), f.number("skinThicknessM", 0.0006),
                f.number("ribSpacingM", 0.4), f.number("ribThicknessM", 0.0006), f.number("supportedMassKg", 1000), f.number("loadFactor", 3.8),
                EngineComponent.Rules.massOr(f), f.string("paintColor", ""),
                EngineComponent.Rules.choice(f, "paintFinish", dev.mcmod.hdfv.part.body.BodySettingsDesign.PaintFinish.class, dev.mcmod.hdfv.part.body.BodySettingsDesign.PaintFinish.STANDARD),
                Sticker.list(f, "stickers"));
    }
}
