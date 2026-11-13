package dev.mcmod.hdfv.part;

import dev.mcmod.aerocore.api.AppliedForce;
import dev.mcmod.aerocore.api.CollisionSphere;
import dev.mcmod.aerocore.api.FluidState;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.Wrench;
import dev.mcmod.aerocore.api.body.RoadAero;
import dev.mcmod.aerocore.api.vehicle.DesignException;
import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;
import dev.mcmod.aerocore.api.vehicle.MassProperties;
import dev.mcmod.aerocore.api.vehicle.MassShapes;
import dev.mcmod.aerocore.api.vehicle.Part;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.PartStep;
import dev.mcmod.hdfv.design.RecordEditor;
import dev.mcmod.hdfv.part.engine.EngineComponent;
import dev.mcmod.hdfv.part.engine.EngineMaterial;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * A storage box (0.25, 儲物箱: the user's "player-designed storage-box part"): a box that carries cargo on a vehicle,
 * its origin at the middle of its bottom (it sits on a body module's or a deck's top point), its length along x. Three
 * kinds:
 * <ul>
 * <li>ROOF_BOX: a roof box's streamlined clamshell (moulded composite or plastic; the lid hinged along one side): it
 *     holds ROOF_SHAPE_VOLUME of its outer box less the walls and has ROOF_SHAPE_AREA of the box's area (fitted to
 *     catalogue boxes: 450 l in 195 x 89 x 43 cm), its hinges, locks and four rack clamps ROOF_FITTINGS_KG. It rides on
 *     a roof and its bars, rated for ROOF_LOAD_KG with the box (the usual roof load): the box's own mass should leave
 *     ROOF_MIN_PAYLOAD_KG of it (an extrapolation if not), and it is rated for what is left or what its floor carries,
 *     whichever is less.</li>
 * <li>BED_BOX: a pickup bed's steel box, its sheet stiffened by ribs (BED_RIBS of the sheet's mass, every RIB_PITCH_M),
 *     a lid with hinges, gas struts and latches (BED_FITTINGS_KG); rated for what its floor carries between the ribs.</li>
 * <li>CONTAINER: an ISO-style freight container: corrugated walls and roof (CORRUGATION times the sheet a flat panel
 *     would take), a frame of rails and corner posts (FRAME_KG_PER_M of its twelve edges), eight corner castings, a
 *     plywood floor on cross members every CROSS_PITCH_M, the doors at its rear end (-x); inside it is ISO 668's 20 ft
 *     box less the rails, the posts and the floor (the insets). Rated for ISO 668's gross (GROSS_KG_PER_M of its length,
 *     at most ISO_GROSS_KG) less its tare. A 20 ft one of 1.6 mm steel: about 2.4 t, 33 m^3.</li>
 * </ul>
 * A floor's rating: a strip of its plate simply supported over a span, at 0.6 of its material's yield in bending,
 * q = 4 (0.6 yield) t^2 / (3 s^2), over the inside's floor. One hold (Part.Hold "hold") of the inside's volume at its
 * middle: its cargo rides with the vehicle (the game loads it). Air: one RoadAero.Bluff at its middle of its frontal
 * area (width times height) and its kind's drag coefficient (ROOF_BOX 0.35 streamlined, BED_BOX RoadAero.CD_SHIELDED
 * behind a cab, CONTAINER RoadAero.CD_BLUFF), as a car's roof rack's bars are. Not buoyant (none of them is
 * watertight). Its structure wears as a body's does (Conditions STRUCTURE). The colour blank is the kind's own (black,
 * grey steel, container red).
 */
public record StorageBoxDesign(String name, Kind boxKind, double lengthM, double widthM, double heightM, double wallM, EngineMaterial material, String paintColor)
        implements PartDesign {
    public static final String TYPE = "hdfv:storage_box";

    public enum Kind { ROOF_BOX, BED_BOX, CONTAINER }

    public static final double ROOF_SHAPE_VOLUME = 0.62, ROOF_SHAPE_AREA = 0.82, ROOF_FITTINGS_KG = 3, ROOF_LOAD_KG = 75, ROOF_MIN_PAYLOAD_KG = 20;
    public static final double BED_RIBS = 0.15, RIB_PITCH_M = 0.3, BED_FITTINGS_KG = 2.5;
    public static final double CORRUGATION = 1.15, FRAME_KG_PER_M = 18, CASTING_KG = 10, FLOOR_M = 0.028, FLOOR_KG_PER_M3 = 700, CROSS_PITCH_M = 0.3,
            CROSS_KG_PER_M = 8;
    /** A container's inside less its outside (ISO 668 1CC: 6.058 x 2.438 x 2.591 m outside, 5.898 x 2.352 x 2.393 inside): each side, each end, floor, roof. */
    public static final double SIDE_INSET_M = 0.043, END_INSET_M = 0.080, FLOOR_INSET_M = 0.160, ROOF_INSET_M = 0.038;
    public static final double ISO_GROSS_KG = 30480, GROSS_KG_PER_M = 5032;
    /** The share of yield a floor is rated at in bending. */
    public static final double FLOOR_STRESS_SHARE = 0.6;

    public StorageBoxDesign {
        Objects.requireNonNull(name, "name"); Objects.requireNonNull(boxKind, "boxKind"); Objects.requireNonNull(material, "material");
        if (paintColor == null) { paintColor = ""; }
    }

    /** A roof box of about 450 l: 1.95 x 0.89 x 0.43 m of 2.5 mm glass fibre. */
    public static StorageBoxDesign roofBox() { return new StorageBoxDesign("roof box", Kind.ROOF_BOX, 1.95, 0.89, 0.43, 0.0025, EngineMaterial.GFRP, ""); }

    /** A pickup's crossover bed box: 1.78 x 0.51 x 0.43 m of 1.2 mm steel. */
    public static StorageBoxDesign bedBox() { return new StorageBoxDesign("bed box", Kind.BED_BOX, 1.78, 0.51, 0.43, 0.0012, EngineMaterial.STEEL_1045, ""); }

    /** A 20 ft ISO container: 6.058 x 2.438 x 2.591 m, 1.6 mm corrugated steel. */
    public static StorageBoxDesign container20() { return new StorageBoxDesign("20 ft container", Kind.CONTAINER, 6.058, 2.438, 2.591, 0.0016, EngineMaterial.STEEL_1045, ""); }

    @Override public String type() { return TYPE; }

    /** Its kind (the parameter is boxKind: "kind" names a standard component on the design page). */
    public Kind kind() { return boxKind; }

    // ---------------------------------------------------------------- geometry and mass

    /** The inside: length, height, width (m) and the floor's height over the bottom. */
    public double[] inside() {
        if (boxKind == Kind.CONTAINER) {
            return new double[]{lengthM - 2 * END_INSET_M, heightM - FLOOR_INSET_M - ROOF_INSET_M, widthM - 2 * SIDE_INSET_M, FLOOR_INSET_M};
        }
        return new double[]{lengthM - 2 * wallM, heightM - 2 * wallM, widthM - 2 * wallM, wallM};
    }

    /** What it holds, m^3. */
    public double capacityM3() {
        double[] in = inside();
        if (!(in[0] > 0 && in[1] > 0 && in[2] > 0)) { return 0; }
        return in[0] * in[1] * in[2] * (boxKind == Kind.ROOF_BOX ? ROOF_SHAPE_VOLUME : 1);
    }

    /** The middle of the inside (its hold's centre, part frame). */
    public Vec3d holdCentre() {
        double[] in = inside();
        return new Vec3d(0, in[3] + 0.5 * Math.max(in[1], 0), 0);
    }

    private double boxAreaM2() { return 2 * (lengthM * widthM + lengthM * heightM + widthM * heightM); }

    /** Its empty mass (tare), kg. */
    public double massKg() {
        double rho = material.densityKgPerM3();
        return switch (boxKind) {
            case ROOF_BOX -> ROOF_SHAPE_AREA * boxAreaM2() * wallM * rho + ROOF_FITTINGS_KG;
            case BED_BOX -> boxAreaM2() * wallM * rho * (1 + BED_RIBS) + BED_FITTINGS_KG;
            case CONTAINER -> {
                double[] in = inside();
                double panels = 2 * lengthM * heightM + 2 * widthM * heightM + lengthM * widthM;
                double frame = 4 * (lengthM + widthM + heightM) * FRAME_KG_PER_M;
                double floor = Math.max(in[0], 0) * Math.max(in[2], 0) * FLOOR_M * FLOOR_KG_PER_M3;
                double cross = (Math.floor(Math.max(in[0], 0) / CROSS_PITCH_M) + 1) * Math.max(in[2], 0) * CROSS_KG_PER_M;
                yield panels * wallM * rho * CORRUGATION + frame + 8 * CASTING_KG + floor + cross;
            }
        };
    }

    /** What its floor carries, kg: a strip of its plate over a span at FLOOR_STRESS_SHARE of yield, over the inside's floor. */
    double floorKg(double spanM) {
        double[] in = inside();
        if (!(spanM > 0) || !(in[0] > 0) || !(in[2] > 0)) { return 0; }
        double q = 4 * FLOOR_STRESS_SHARE * material.yieldPa() * wallM * wallM / (3 * spanM * spanM);
        return q * in[0] * in[2] / 9.80665;
    }

    /** The gross ISO 668 rates a container of its length for, kg. */
    public double isoGrossKg() { return Math.min(ISO_GROSS_KG, GROSS_KG_PER_M * lengthM); }

    /** What it is rated to carry, kg (storage.CargoRules: shown, not enforced). */
    public double ratedLoadKg() {
        return switch (boxKind) {
            case ROOF_BOX -> Math.max(0, Math.min(floorKg(inside()[2]), ROOF_LOAD_KG - massKg()));
            case BED_BOX -> floorKg(RIB_PITCH_M);
            case CONTAINER -> Math.max(0, isoGrossKg() - massKg());
        };
    }

    /** Its drag coefficient on its frontal area. */
    public double dragCoefficient() {
        return switch (boxKind) { case ROOF_BOX -> 0.35; case BED_BOX -> RoadAero.CD_SHIELDED; case CONTAINER -> RoadAero.CD_BLUFF; };
    }

    /** Its frontal area, m^2. */
    public double frontalAreaM2() { return widthM * heightM; }

    /** Its colour: the design's, or its kind's (#RRGGBB). */
    public String colour() {
        if (!paintColor.isBlank()) { return paintColor; }
        return switch (boxKind) { case ROOF_BOX -> "#1E2023"; case BED_BOX -> "#8C9196"; case CONTAINER -> "#9A4A2F"; };
    }

    // ---------------------------------------------------------------- checks

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        EngineComponent.Rules.positive(issues, "lengthM", lengthM);
        EngineComponent.Rules.positive(issues, "widthM", widthM);
        EngineComponent.Rules.positive(issues, "heightM", heightM);
        EngineComponent.Rules.positive(issues, "wallM", wallM);
        if (!RecordEditor.isColor(paintColor, true)) { issues.add(DesignIssue.invalid("paintColor", "A colour is #RRGGBB, or blank for the kind's own")); }
        if (!issues.isEmpty()) { return issues; }
        double[] in = inside();
        if (!(in[0] > 0 && in[1] > 0 && in[2] > 0)) {
            issues.add(DesignIssue.invalid(boxKind == Kind.CONTAINER ? "heightM" : "wallM", "The walls leave no room inside"));
            return issues;
        }
        if (boxKind != Kind.CONTAINER && 4 * wallM > Math.min(widthM, heightM)) { issues.add(DesignIssue.invalid("wallM", "Walls of a quarter of the box are not a box's")); return issues; }
        switch (boxKind) {
            case ROOF_BOX -> {
                DesignIssue.range(issues, "lengthM", lengthM, 1.0, 2.4, "roof boxes (Thule, Kamei)");
                DesignIssue.range(issues, "widthM", widthM, 0.5, 1.0, "roof boxes (Thule, Kamei)");
                DesignIssue.range(issues, "heightM", heightM, 0.25, 0.6, "roof boxes (Thule, Kamei)");
                DesignIssue.range(issues, "wallM", wallM, 0.0015, 0.005, "moulded roof boxes");
                DesignIssue.range(issues, "roofLoadKg", massKg(), 0, ROOF_LOAD_KG - ROOF_MIN_PAYLOAD_KG, "a car roof's 75 kg with 20 kg left to carry");
            }
            case BED_BOX -> {
                DesignIssue.range(issues, "lengthM", lengthM, 0.6, 2.0, "pickup bed boxes");
                DesignIssue.range(issues, "widthM", widthM, 0.3, 1.6, "pickup bed boxes");
                DesignIssue.range(issues, "heightM", heightM, 0.25, 0.8, "pickup bed boxes");
                DesignIssue.range(issues, "wallM", wallM, 0.0008, 0.004, "pickup bed boxes");
            }
            case CONTAINER -> {
                DesignIssue.range(issues, "lengthM", lengthM, 2.9, 13.8, "ISO 668 containers (10 to 45 ft)");
                DesignIssue.range(issues, "widthM", widthM, 2.3, 2.6, "ISO 668 containers");
                DesignIssue.range(issues, "heightM", heightM, 2.2, 2.9, "ISO 668 containers");
                DesignIssue.range(issues, "wallM", wallM, 0.0012, 0.003, "ISO containers' corrugated walls");
            }
        }
        return issues;
    }

    // ---------------------------------------------------------------- compiling

    private static final List<String> CONDITIONS = List.of(dev.mcmod.hdfv.damage.Conditions.STRUCTURE);

    @Override public Part compile() {
        DesignException.throwIfInvalid(check());
        Vec3d size = new Vec3d(lengthM, heightM, widthM), middle = new Vec3d(0, heightM / 2, 0);
        MassProperties shell = MassShapes.box(size, Math.max(Math.min(wallM, Math.min(lengthM, Math.min(widthM, heightM)) / 4), 1e-4));
        MassProperties mass = shell.scaled(massKg() / shell.massKg()).at(middle);
        List<CollisionSphere> spheres = new ArrayList<>();
        for (CollisionSphere s : MassShapes.boxSpheres(size)) { spheres.add(new CollisionSphere(s.centerLocal().add(middle), s.radiusM())); }
        RoadAero air = new RoadAero(List.of(new RoadAero.Bluff(middle, frontalAreaM2(), dragCoefficient())));
        List<DesignIssue> extrapolated = check().stream().filter(i -> i.kind() == DesignIssue.Kind.EXTRAPOLATION).toList();
        return new Compiled(this, mass, List.copyOf(spheres), extrapolated, List.of(new Part.Hold("hold", capacityM3(), holdCentre())), air, middle);
    }

    /** The compiled box: its mass, collision, hold, air drag at its middle, and its structure's condition. */
    record Compiled(StorageBoxDesign design, MassProperties mass, List<CollisionSphere> collision, List<DesignIssue> issues, List<Part.Hold> holds, RoadAero air,
                    Vec3d middle) implements Part {
        @Override public List<String> conditions() { return CONDITIONS; }

        @Override public void loads(PartStep step) {
            FluidState a = step.environment().point(step.point(middle)).air();
            Vec3d velocity = step.partDirection(step.velocity(middle).subtract(a.velocity()));
            RoadAero.Loads l = air.loads(velocity.scale(-1), a.densityKgPerM3(), p -> Double.POSITIVE_INFINITY);
            if (l.force().equals(Vec3d.ZERO)) { step.diagnostic("airDragN", 0); return; }
            // a damaged box (dented, its lid sprung) drags as a body does: up to 0.3 more when used up
            double used = Math.min(step.used(0), 1);
            Vec3d partForce = used > 0 ? l.force().scale(1 + 0.3 * used) : l.force(), partMoment = used > 0 ? l.moment().scale(1 + 0.3 * used) : l.moment();
            Vec3d force = step.axis(partForce), origin = step.offset(Vec3d.ZERO);
            step.load(new Wrench(force, step.axis(partMoment).add(origin.cross(force))), AppliedForce.Kind.DRAG);
            step.diagnostic("airDragN", used > 0 ? l.dragN() * (1 + 0.3 * used) : l.dragN());
        }
    }

    // ---------------------------------------------------------------- JSON

    @Override public Map<String, Object> parameters() {
        Map<String, Object> m = Fields.object("boxKind", boxKind.name().toLowerCase(Locale.ROOT), "lengthM", lengthM, "widthM", widthM, "heightM", heightM, "wallM", wallM);
        m.put("material", material.toJson());
        if (!paintColor.isBlank()) { m.put("paintColor", paintColor); }
        return m;
    }

    public static PartDesign read(Fields f) {
        Kind kind = EngineComponent.Rules.choice(f, "boxKind", Kind.class, Kind.ROOF_BOX);
        StorageBoxDesign d = switch (kind) { case ROOF_BOX -> roofBox(); case BED_BOX -> bedBox(); case CONTAINER -> container20(); };
        return new StorageBoxDesign(f.string("name", d.name), kind, f.number("lengthM", d.lengthM), f.number("widthM", d.widthM), f.number("heightM", d.heightM),
                f.number("wallM", d.wallM), EngineComponent.Rules.material(f, "material", d.material), f.string("paintColor", ""));
    }
}
