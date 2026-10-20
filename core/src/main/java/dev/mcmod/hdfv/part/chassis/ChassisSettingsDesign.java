package dev.mcmod.hdfv.part.chassis;

import dev.mcmod.aerocore.api.frame.Section;
import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;
import dev.mcmod.hdfv.design.RecordEditor;
import dev.mcmod.hdfv.part.engine.EngineComponent;
import dev.mcmod.hdfv.part.engine.EngineMaterial;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A chassis's frame (0.15, the "frame" page): its kind, material, length (x from its rear end), the rails' spacing at
 * the front and rear end (a ladder's rails and a tube frame's floor; a backbone's outriggers), the rails' section (a
 * ladder's two rails, a backbone's one tube), a ladder's kick-up over the rear axle (the rails rise kickUpM behind
 * kickUpX), the joints (welded, riveted, bolted: how an open section's warping is held), a tube frame's tubes, the
 * vehicle's gross mass it is designed for (its load cases) and its paint. 0.18: a ladder's gooseneck (a semi-trailer's:
 * the rails rise neckM ahead of neckX, over the 0.4 m behind it), written only when it has one, so older designs read
 * and write as before.
 */
public record ChassisSettingsDesign(String name, Kind kind, EngineMaterial material, double lengthM, double railSpacingM, double railSpacingRearM,
                                    RailSection railSection, double railHeightM, double railWidthM, double railWallM, double kickUpM, double kickUpX,
                                    Joint joint, double tubeDiameterM, double tubeWallM, double designGrossKg, String paintColor, double neckM, double neckX)
        implements EngineComponent, RecordEditor.Shown {
    public static final String TYPE = "hdfv:chassis_frame";

    /** Frame kinds: two rails and crossmembers; one central tube with outriggers; a triangulated cage of tubes. */
    public enum Kind { LADDER, BACKBONE, SPACE_FRAME }

    /** Sections for rails and crossmembers (height, width, wall): pressed channel, fabricated box, tube, round tube, I beam. */
    public enum RailSection {
        CHANNEL, BOX, RECT_TUBE, ROUND_TUBE, I_BEAM;

        /** The section of a height, width and wall (m); a round tube's diameter is its height. */
        public Section section(double heightM, double widthM, double wallM) {
            return switch (this) {
                case CHANNEL -> Section.channel(heightM, widthM, wallM);
                case BOX -> Section.box(widthM, heightM, wallM, wallM);
                case RECT_TUBE -> Section.rectTube(widthM, heightM, wallM);
                case ROUND_TUBE -> Section.roundTube(heightM, wallM);
                case I_BEAM -> Section.iBeam(heightM, widthM, wallM, wallM);
            };
        }

        /** Whether it is closed (a box or tube: stiff in twist). */
        public boolean closed() { return this == BOX || this == RECT_TUBE || this == ROUND_TUBE; }
    }

    /** How members are joined: the warping restraint an open section gets (api.frame.Frame's joint factors). */
    public enum Joint {
        WELDED(dev.mcmod.aerocore.api.frame.Frame.WELDED), RIVETED(dev.mcmod.aerocore.api.frame.Frame.RIVETED), BOLTED(dev.mcmod.aerocore.api.frame.Frame.BOLTED);

        public final double factor;

        Joint(double factor) { this.factor = factor; }
    }

    public ChassisSettingsDesign {
        Objects.requireNonNull(name, "name"); Objects.requireNonNull(kind, "kind"); Objects.requireNonNull(material, "material");
        Objects.requireNonNull(railSection, "railSection"); Objects.requireNonNull(joint, "joint");
        paintColor = paintColor == null ? "" : paintColor;
    }

    /** As before 0.18: no gooseneck. */
    public ChassisSettingsDesign(String name, Kind kind, EngineMaterial material, double lengthM, double railSpacingM, double railSpacingRearM,
                                 RailSection railSection, double railHeightM, double railWidthM, double railWallM, double kickUpM, double kickUpX,
                                 Joint joint, double tubeDiameterM, double tubeWallM, double designGrossKg, String paintColor) {
        this(name, kind, material, lengthM, railSpacingM, railSpacingRearM, railSection, railHeightM, railWidthM, railWallM, kickUpM, kickUpX, joint,
                tubeDiameterM, tubeWallM, designGrossKg, paintColor, 0, 0);
    }

    @Override public String type() { return TYPE; }

    @Override public boolean shows(String parameter) {
        return switch (parameter) {
            case "railSpacingRearM" -> kind != Kind.BACKBONE;
            case "kickUpM", "kickUpX", "neckM", "neckX" -> kind == Kind.LADDER;
            case "railSection", "railHeightM", "railWidthM", "railWallM" -> kind != Kind.SPACE_FRAME;
            case "tubeDiameterM", "tubeWallM" -> kind == Kind.SPACE_FRAME;
            default -> true;
        };
    }

    /** The rails' (a backbone's tube's) section. */
    public Section rails() { return railSection.section(railHeightM, railWidthM, railWallM); }
    /** A tube frame's tubes. */
    public Section tubes() { return Section.roundTube(tubeDiameterM, tubeWallM); }
    /** The rails' spacing at x (m from the rear end): from the rear's to the front's along the length. */
    public double spacingAt(double x) {
        double t = Math.clamp(x / lengthM, 0, 1);
        return kind == Kind.BACKBONE ? railSpacingM : railSpacingRearM + t * (railSpacingM - railSpacingRearM);
    }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        if (!(lengthM >= 1 && lengthM <= 20)) { issues.add(DesignIssue.invalid("lengthM", "A frame is 1 to 20 m long")); }
        for (String p : List.of("railSpacingM", "railSpacingRearM")) {
            double v = p.equals("railSpacingM") ? railSpacingM : railSpacingRearM;
            if (!(v >= 0.2 && v <= 3)) { issues.add(DesignIssue.invalid(p, "The rails stand 0.2 to 3 m apart")); }
        }
        if (kind != Kind.SPACE_FRAME) {
            Rules.positive(issues, "railHeightM", railHeightM);
            Rules.positive(issues, "railWidthM", railWidthM);
            if (!(railWallM > 0 && railWallM < 0.5 * Math.min(railHeightM, railWidthM))) { issues.add(DesignIssue.invalid("railWallM", "The wall is thinner than half the section")); }
        } else {
            Rules.positive(issues, "tubeDiameterM", tubeDiameterM);
            if (!(tubeWallM > 0 && tubeWallM < 0.5 * tubeDiameterM)) { issues.add(DesignIssue.invalid("tubeWallM", "The wall is thinner than half the tube")); }
        }
        if (kind == Kind.LADDER) {
            if (!(kickUpM >= 0 && kickUpM <= 1)) { issues.add(DesignIssue.invalid("kickUpM", "A kick-up rises 0 to 1 m")); }
            if (!(kickUpX >= 0 && kickUpX <= lengthM)) { issues.add(DesignIssue.invalid("kickUpX", "The kick-up is along the frame")); }
            if (!(neckM >= 0 && neckM <= 1.5)) { issues.add(DesignIssue.invalid("neckM", "A gooseneck rises 0 to 1.5 m")); }
            if (neckM > 0 && !(neckX >= 0 && neckX <= lengthM - 0.5)) { issues.add(DesignIssue.invalid("neckX", "The gooseneck starts on the frame, 0.5 m short of its front or more")); }
        }
        if (!(designGrossKg > 0)) { issues.add(DesignIssue.invalid("designGrossKg", "The gross mass is positive")); }
        if (issues.isEmpty() && kind != Kind.SPACE_FRAME) {
            DesignIssue.range(issues, "railWallM", railWallM, 0.002, 0.012, "pressed and fabricated frame rails (cars 2-4 mm, trucks 6-10 mm)");
        }
        return issues;
    }

    /** No mass of its own as a page: the chassis's frame model gives it. */
    @Override public double massKg() { return 0; }

    @Override public Map<String, Object> parameters() {
        Map<String, Object> m = Fields.object("kind", lower(kind), "lengthM", lengthM, "railSpacingM", railSpacingM, "railSpacingRearM", railSpacingRearM,
                "railSection", lower(railSection), "railHeightM", railHeightM, "railWidthM", railWidthM, "railWallM", railWallM, "kickUpM", kickUpM,
                "kickUpX", kickUpX, "joint", lower(joint), "tubeDiameterM", tubeDiameterM, "tubeWallM", tubeWallM, "designGrossKg", designGrossKg,
                "paintColor", paintColor);
        m.put("material", material.toJson());
        if (neckM != 0) { m.put("neckM", neckM); m.put("neckX", neckX); }
        return m;
    }

    private static String lower(Enum<?> e) { return e.name().toLowerCase(java.util.Locale.ROOT); }

    static ChassisSettingsDesign read(String name, Fields f) {
        ChassisSettingsDesign d = defaults(Rules.choice(f, "kind", Kind.class, Kind.LADDER));
        return new ChassisSettingsDesign(name, d.kind, Rules.material(f, "material", d.material), f.number("lengthM", d.lengthM), f.number("railSpacingM", d.railSpacingM),
                f.number("railSpacingRearM", d.railSpacingRearM), Rules.choice(f, "railSection", RailSection.class, d.railSection), f.number("railHeightM", d.railHeightM),
                f.number("railWidthM", d.railWidthM), f.number("railWallM", d.railWallM), f.number("kickUpM", d.kickUpM), f.number("kickUpX", d.kickUpX),
                Rules.choice(f, "joint", Joint.class, d.joint), f.number("tubeDiameterM", d.tubeDiameterM), f.number("tubeWallM", d.tubeWallM),
                f.number("designGrossKg", d.designGrossKg), f.string("paintColor", ""), f.number("neckM", 0), f.number("neckX", 0));
    }

    /**
     * A kind's own frame: a pickup's ladder (4.8 m, boxed 160 x 70 x 4 mm steel rails 0.9 m apart, welded), a sports
     * car's backbone (a 200 x 160 x 3 mm box), a racer's tube frame (38 x 1.6 mm alloy steel tubes).
     */
    public static ChassisSettingsDesign defaults(Kind kind) {
        return switch (kind) {
            case LADDER -> new ChassisSettingsDesign("frame", kind, EngineMaterial.STEEL_1045, 4.8, 0.9, 1.0, RailSection.BOX, 0.16, 0.07, 0.004, 0.12, 1.6,
                    Joint.WELDED, 0.038, 0.0016, 2200, "");
            case BACKBONE -> new ChassisSettingsDesign("frame", kind, EngineMaterial.STEEL_1045, 3.8, 1.1, 1.1, RailSection.BOX, 0.2, 0.16, 0.003, 0, 0,
                    Joint.WELDED, 0.038, 0.0016, 1100, "");
            case SPACE_FRAME -> new ChassisSettingsDesign("frame", kind, EngineMaterial.STEEL_4340, 4.0, 1.2, 1.2, RailSection.ROUND_TUBE, 0.038, 0.038, 0.0016, 0, 0,
                    Joint.WELDED, 0.038, 0.0016, 900, "");
        };
    }
}
