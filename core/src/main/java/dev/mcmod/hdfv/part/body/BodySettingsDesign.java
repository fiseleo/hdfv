package dev.mcmod.hdfv.part.body;

import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;
import dev.mcmod.aerocore.api.vehicle.part.BodyPart;
import dev.mcmod.hdfv.look.Sticker;
import dev.mcmod.hdfv.part.engine.EngineComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A body's own settings (機體): what it is for (an aircraft's fuselage is held up at its wing, a boat's hull by the
 * water, a car's body at its axles), how blunt its nose is (0 a point, 1 as wide as the first module), which water model
 * its bottom gets, the limit load factor its structure is checked at (times 1.5), the loading it is weighed and checked
 * in (each occupant, the cargo's density, how full the holds and tanks are and the tanks' fuel: one of hdfv's fuels,
 * which gives its density and is what a vehicle made from the blueprint starts with), drag its smooth shape leaves out
 * (aerials, steps, fairings; m^2 of drag area), and for a hull's bottom ISO 12215-5's design speed and category (A ocean
 * to D sheltered waters), and the mass it carries for other parts (a float's share of its aircraft, a hull's engine;
 * kg) and where along it (a share of its length from the rear end), for its structure, its bottom and the bench; and its
 * paint: a main colour (an aircraft's fuselage, a hull's topsides, a car's body; a module may have its own), an accent
 * (an aircraft's cheat line, a hull's boot stripe, a car's roof) and a lower colour (an aircraft's belly, a hull's
 * antifouling, a car's sills), each #RRGGBB.
 * <p>
 * 0.12, looks only (none of these change the body's mass, drag or anything else it computes; a blueprint without
 * them reads back with the defaults, which draw it as 0.11 did but for real glass): the paint's finish (standard,
 * gloss, metallic, pearl, matte), the glass's tint (blank: the usual grey-blue) and how opaque it is (0.35 shows the
 * inside), the registration, name or plate (A-Z, 0-9, hyphens and spaces, 10 at most; Lettering), the stripes
 * (automatic: an aircraft's cheat line and a boat's boot stripe as before; none; a side stripe; two; a cheat line)
 * and their colour (blank: the accent), and a car's wheel arches' radius.
 * <p>
 * 0.13, looks only: the stickers (Sticker, placed by x and t; at most Sticker.MAX). The design screen does not edit
 * them (RecordEditor leaves a list out); the paint booth does. A body without stickers writes no "stickers" key, so
 * its JSON is 0.12's, and a blueprint without the key reads back with none.
 * <p>
 * 0.16, looks only: the body's own lamps (LampSpec, placed by LampLayout; at most LampSpec.MAX): null (no "lamps" key)
 * is the kit's lamps as before, an empty list (an empty "lamps") no lamps at all. The design screen does not edit them;
 * the refit shop does.
 */
public record BodySettingsDesign(String name, Purpose purpose, double noseBluntness, BodyPart.Hydro hydro, double loadFactor, double occupantKg,
                                 double cargoDensityKgM3, double cargoFill, double fuelFill, String fuel, double extraDragAreaM2,
                                 double designSpeedMps, Category category, double supportedKg, double supportedAt, String paintColor, String accentColor,
                                 String lowerColor, PaintFinish paintFinish, String glassColor, double glassOpacity, String registrationText, Stripes stripes,
                                 String stripeColor, double archRadiusM, boolean onFrame, List<Sticker> stickers, List<dev.mcmod.hdfv.look.LampSpec> lamps)
        implements EngineComponent, dev.mcmod.hdfv.design.RecordEditor.Shown {
    public static final String TYPE = "hdfv:body_settings";

    /** 0.12: the paint's finish. */
    public enum PaintFinish {
        STANDARD, GLOSS, METALLIC, PEARL, MATTE;

        public dev.mcmod.aerocore.api.mesh.Finish finish() {
            return switch (this) {
                case STANDARD -> dev.mcmod.hdfv.look.Finishes.PAINT;
                case GLOSS -> dev.mcmod.hdfv.look.Finishes.PAINT_GLOSS;
                case METALLIC -> dev.mcmod.hdfv.look.Finishes.PAINT_METALLIC;
                case PEARL -> dev.mcmod.hdfv.look.Finishes.PAINT_PEARL;
                case MATTE -> dev.mcmod.hdfv.look.Finishes.PAINT_MATTE;
            };
        }
    }

    /** 0.12: the stripes: as before (by family), none, one along the side, two, an aircraft's cheat line on any body. */
    public enum Stripes { AUTO, NONE, SIDE, TWIN, CHEAT }

    /** 0.12: the looks' defaults (a 0.11 blueprint reads back with these). */
    public static final double GLASS_OPACITY = 0.35, ARCH_RADIUS_M = 0.37;

    /** 0.11's settings, the 0.12 looks at their defaults. */
    public BodySettingsDesign(String name, Purpose purpose, double noseBluntness, BodyPart.Hydro hydro, double loadFactor, double occupantKg,
                              double cargoDensityKgM3, double cargoFill, double fuelFill, String fuel, double extraDragAreaM2, double designSpeedMps,
                              Category category, double supportedKg, double supportedAt, String paintColor, String accentColor, String lowerColor) {
        this(name, purpose, noseBluntness, hydro, loadFactor, occupantKg, cargoDensityKgM3, cargoFill, fuelFill, fuel, extraDragAreaM2, designSpeedMps, category,
                supportedKg, supportedAt, paintColor, accentColor, lowerColor, PaintFinish.STANDARD, "", GLASS_OPACITY, "", Stripes.AUTO, "", ARCH_RADIUS_M);
    }

    /** 0.12's settings and looks, without stickers. */
    public BodySettingsDesign(String name, Purpose purpose, double noseBluntness, BodyPart.Hydro hydro, double loadFactor, double occupantKg,
                              double cargoDensityKgM3, double cargoFill, double fuelFill, String fuel, double extraDragAreaM2, double designSpeedMps,
                              Category category, double supportedKg, double supportedAt, String paintColor, String accentColor, String lowerColor,
                              PaintFinish paintFinish, String glassColor, double glassOpacity, String registrationText, Stripes stripes, String stripeColor,
                              double archRadiusM) {
        this(name, purpose, noseBluntness, hydro, loadFactor, occupantKg, cargoDensityKgM3, cargoFill, fuelFill, fuel, extraDragAreaM2, designSpeedMps, category,
                supportedKg, supportedAt, paintColor, accentColor, lowerColor, paintFinish, glassColor, glassOpacity, registrationText, stripes, stripeColor,
                archRadiusM, false, List.of());
    }

    /** 0.15's settings: the kit's lamps. */
    public BodySettingsDesign(String name, Purpose purpose, double noseBluntness, BodyPart.Hydro hydro, double loadFactor, double occupantKg,
                              double cargoDensityKgM3, double cargoFill, double fuelFill, String fuel, double extraDragAreaM2, double designSpeedMps,
                              Category category, double supportedKg, double supportedAt, String paintColor, String accentColor, String lowerColor,
                              PaintFinish paintFinish, String glassColor, double glassOpacity, String registrationText, Stripes stripes, String stripeColor,
                              double archRadiusM, boolean onFrame, List<Sticker> stickers) {
        this(name, purpose, noseBluntness, hydro, loadFactor, occupantKg, cargoDensityKgM3, cargoFill, fuelFill, fuel, extraDragAreaM2, designSpeedMps, category,
                supportedKg, supportedAt, paintColor, accentColor, lowerColor, paintFinish, glassColor, glassOpacity, registrationText, stripes, stripeColor,
                archRadiusM, onFrame, stickers, null);
    }

    public enum Purpose { AIRCRAFT, BOAT, CAR }

    /** ISO 12215-5's design categories and their factor k_DC. */
    public enum Category {
        A(1.0), B(0.8), C(0.6), D(0.4);
        public final double factor;
        Category(double factor) { this.factor = factor; }
    }

    public BodySettingsDesign {
        Objects.requireNonNull(name, "name"); Objects.requireNonNull(purpose, "purpose"); Objects.requireNonNull(hydro, "hydro");
        Objects.requireNonNull(category, "category"); Objects.requireNonNull(fuel, "fuel");
        Objects.requireNonNull(paintFinish, "paintFinish"); Objects.requireNonNull(glassColor, "glassColor"); Objects.requireNonNull(registrationText, "registrationText");
        Objects.requireNonNull(stripes, "stripes"); Objects.requireNonNull(stripeColor, "stripeColor");
        stickers = List.copyOf(Objects.requireNonNull(stickers, "stickers"));
        if (lamps != null) { lamps = List.copyOf(lamps); }
    }

    /** 0.16: whether the body has its own lamps (else the kit's: LampLayout.auto). */
    public boolean ownLamps() { return lamps != null; }

    /** 0.16: these settings with other lamps (null: the kit's; empty: none). */
    public BodySettingsDesign withLamps(List<dev.mcmod.hdfv.look.LampSpec> l) {
        return new BodySettingsDesign(name, purpose, noseBluntness, hydro, loadFactor, occupantKg, cargoDensityKgM3, cargoFill, fuelFill, fuel, extraDragAreaM2,
                designSpeedMps, category, supportedKg, supportedAt, paintColor, accentColor, lowerColor, paintFinish, glassColor, glassOpacity, registrationText,
                stripes, stripeColor, archRadiusM, onFrame, stickers, l);
    }

    /** A purpose's usual settings: a utility aircraft at 3.8 g, a planing boat at 4 g in slamming, a car at 3 g over bumps. */
    public static BodySettingsDesign of(Purpose purpose) {
        return switch (purpose) {
            case AIRCRAFT -> new BodySettingsDesign("settings", purpose, 0.25, BodyPart.Hydro.AUTO, 3.8, 85, 200, 0.5, 0.5, "kerosene", 0, 25, Category.C, 0, 0.5,
                    "#F3F4F6", "#1F3B73", "#B3BAC2");
            case BOAT -> new BodySettingsDesign("settings", purpose, 0.05, BodyPart.Hydro.AUTO, 4, 85, 200, 0.5, 0.5, "gasoline", 0, 15, Category.C, 0, 0.5,
                    "#F4F4F0", "#1C2F52", "#8E2F2A");
            case CAR -> new BodySettingsDesign("settings", purpose, 0.6, BodyPart.Hydro.DISPLACEMENT, 3, 75, 200, 0.5, 0.5, "gasoline", 0, 15, Category.D, 0, 0.5,
                    "#1D4F91", "#1D4F91", "#1D1F22");
        };
    }

    @Override public String type() { return TYPE; }

    /** A car body has no water model or hull bottom (it is a displacement body if it ever drives into water). */
    @Override public boolean shows(String parameter) {
        return switch (parameter) {
            case "hydro", "designSpeedMps", "category" -> purpose != Purpose.CAR;
            case "archRadiusM", "onFrame" -> purpose == Purpose.CAR;
            case "stripeColor" -> stripes != Stripes.AUTO && stripes != Stripes.NONE;
            default -> true;
        };
    }

    /** The tanks' fuel density, kg/m^3. */
    public double fuelDensity() { var s = dev.mcmod.hdfv.fluid.HdfvFluids.of(fuel); return s == null ? 800 : s.densityKgPerM3(); }

    /** The water model the body uses: a car's is always displacement. */
    public BodyPart.Hydro waterModel() { return purpose == Purpose.CAR ? BodyPart.Hydro.DISPLACEMENT : hydro; }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        if (!(noseBluntness >= 0 && noseBluntness <= 1)) { issues.add(DesignIssue.invalid("noseBluntness", "Nose bluntness is 0 to 1")); }
        if (!(loadFactor >= 1 && loadFactor <= 12)) { issues.add(DesignIssue.invalid("loadFactor", "The load factor is 1 to 12")); }
        Rules.nonnegative(issues, "occupantKg", occupantKg);
        Rules.positive(issues, "cargoDensityKgM3", cargoDensityKgM3);
        if (!(cargoFill >= 0 && cargoFill <= 1)) { issues.add(DesignIssue.invalid("cargoFill", "The holds are 0 to 1 full")); }
        if (!(fuelFill >= 0 && fuelFill <= 1)) { issues.add(DesignIssue.invalid("fuelFill", "The tanks are 0 to 1 full")); }
        if (dev.mcmod.hdfv.fluid.FuelGrades.of(fuel) == null) { issues.add(DesignIssue.invalid("fuel", "Not a fuel: " + fuel)); }
        else if (!dev.mcmod.hdfv.fluid.FuelGrades.ambient().contains(fuel)) {
            issues.add(DesignIssue.invalid("fuel", "Integral tanks hold fuel at ambient temperature and pressure: hydrogen needs a cryogenic or high-pressure tank"));
        }
        Rules.nonnegative(issues, "extraDragAreaM2", extraDragAreaM2);
        Rules.nonnegative(issues, "designSpeedMps", designSpeedMps);
        Rules.nonnegative(issues, "supportedKg", supportedKg);
        if (!(supportedAt >= 0 && supportedAt <= 1)) { issues.add(DesignIssue.invalid("supportedAt", "Where it is carried is 0 (the rear end) to 1 (the nose)")); }
        for (String[] c : new String[][]{{"paintColor", paintColor}, {"accentColor", accentColor}, {"lowerColor", lowerColor}}) {
            if (!dev.mcmod.hdfv.design.RecordEditor.isColor(c[1], false)) { issues.add(DesignIssue.invalid(c[0], "A colour is #RRGGBB")); }
        }
        for (String[] c : new String[][]{{"glassColor", glassColor}, {"stripeColor", stripeColor}}) {
            if (!dev.mcmod.hdfv.design.RecordEditor.isColor(c[1], true)) { issues.add(DesignIssue.invalid(c[0], "A colour is #RRGGBB, or blank")); }
        }
        if (!(glassOpacity >= 0.05 && glassOpacity <= 1)) { issues.add(DesignIssue.invalid("glassOpacity", "The glass is 0.05 (almost clear) to 1 (opaque)")); }
        if (!registrationText.equals(dev.mcmod.hdfv.look.Lettering.clean(registrationText))) {
            issues.add(DesignIssue.invalid("registrationText", "Letters A-Z, digits, hyphens and spaces, 10 at most"));
        }
        if (!(archRadiusM >= 0.2 && archRadiusM <= 1.2)) { issues.add(DesignIssue.invalid("archRadiusM", "A wheel arch's radius is 0.2 to 1.2 m")); }
        Sticker.check(issues, "stickers", stickers, false);
        if (lamps != null) { dev.mcmod.hdfv.look.LampSpec.check(issues, "lamps", lamps); }
        return issues;
    }

    @Override public double massKg() { return 0; }

    @Override public Map<String, Object> parameters() {
        Map<String, Object> m = Fields.object("purpose", purpose.name().toLowerCase(java.util.Locale.ROOT), "noseBluntness", noseBluntness,
                "hydro", hydro.name().toLowerCase(java.util.Locale.ROOT), "loadFactor", loadFactor, "occupantKg", occupantKg,
                "cargoDensityKgM3", cargoDensityKgM3, "cargoFill", cargoFill, "fuelFill", fuelFill, "fuel", fuel,
                "extraDragAreaM2", extraDragAreaM2, "designSpeedMps", designSpeedMps, "category", category.name().toLowerCase(java.util.Locale.ROOT),
                "supportedKg", supportedKg, "supportedAt", supportedAt, "paintColor", paintColor, "accentColor", accentColor, "lowerColor", lowerColor,
                "paintFinish", paintFinish.name().toLowerCase(java.util.Locale.ROOT), "glassColor", glassColor, "glassOpacity", glassOpacity,
                "registrationText", registrationText, "stripes", stripes.name().toLowerCase(java.util.Locale.ROOT), "stripeColor", stripeColor, "archRadiusM", archRadiusM);
        if (onFrame) { m.put("onFrame", true); }
        if (!stickers.isEmpty()) { m.put("stickers", Sticker.toJson(stickers)); }
        if (lamps != null) { m.put("lamps", dev.mcmod.hdfv.look.LampSpec.toJson(lamps)); }
        return m;
    }

    static BodySettingsDesign read(String name, Fields f) {
        Purpose purpose = Rules.choice(f, "purpose", Purpose.class, Purpose.AIRCRAFT);
        BodySettingsDesign d = of(purpose);
        return new BodySettingsDesign(name, purpose, f.number("noseBluntness", d.noseBluntness), Rules.choice(f, "hydro", BodyPart.Hydro.class, d.hydro),
                f.number("loadFactor", d.loadFactor), f.number("occupantKg", d.occupantKg), f.number("cargoDensityKgM3", d.cargoDensityKgM3),
                f.number("cargoFill", d.cargoFill), f.number("fuelFill", d.fuelFill), f.string("fuel", d.fuel),
                f.number("extraDragAreaM2", d.extraDragAreaM2), f.number("designSpeedMps", d.designSpeedMps), Rules.choice(f, "category", Category.class, d.category),
                f.number("supportedKg", d.supportedKg), f.number("supportedAt", d.supportedAt), f.string("paintColor", d.paintColor),
                f.string("accentColor", d.accentColor), f.string("lowerColor", d.lowerColor), Rules.choice(f, "paintFinish", PaintFinish.class, PaintFinish.STANDARD),
                f.string("glassColor", ""), f.number("glassOpacity", GLASS_OPACITY), f.string("registrationText", ""), Rules.choice(f, "stripes", Stripes.class, Stripes.AUTO),
                f.string("stripeColor", ""), f.number("archRadiusM", ARCH_RADIUS_M), f.bool("onFrame", false), Sticker.list(f, "stickers"),
                dev.mcmod.hdfv.look.LampSpec.list(f, "lamps"));
    }
}
