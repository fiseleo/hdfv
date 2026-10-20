package dev.mcmod.hdfv.refit;

import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.VehicleBlueprint;
import dev.mcmod.hdfv.design.Designs;
import dev.mcmod.hdfv.design.RecordEditor;
import dev.mcmod.hdfv.paint.Repaint;
import dev.mcmod.hdfv.part.body.BodyDesign;
import dev.mcmod.hdfv.part.chassis.ChassisDesign;
import dev.mcmod.hdfv.part.drive.GearboxDesign;
import dev.mcmod.hdfv.part.drive.TransferCaseDesign;
import dev.mcmod.hdfv.part.engine.JetEngineDesign;
import dev.mcmod.hdfv.part.engine.PistonEngineDesign;
import dev.mcmod.hdfv.part.engine.ShaftTurbineDesign;
import dev.mcmod.hdfv.part.engine.TurbofanDesign;
import dev.mcmod.hdfv.part.wheel.AxleDesign;
import dev.mcmod.hdfv.part.wheel.LandingGearDesign;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 0.16: what a refit shop may change on a vehicle that is built (the user's 「載具調整系統」): its parts' shape, size,
 * aero kit, rims and tuning, parameter by parameter (adjustables, as Repaint.looks is for the paint booth), and the
 * whole vehicle's scale (Scaling). Adding or removing parts, another kind of part, an engine's insides other than by
 * scaling, materials: those are the assembly room's. Edits are applied on the server to the vehicle's pending
 * blueprint, one at a time, each refused when it leaves a part INVALID.
 */
public final class Refit {
    private Refit() {}

    /** What an adjustable is for (the shop's pages). */
    public enum Category { SHAPE, SIZE, KIT, RIM, TUNE }

    /** One parameter the shop may change: its page, name and category. */
    public record Adjustable(String page, String param, Category category) {}

    private record Rule(String page, Set<String> params, Category category) {
        boolean matches(String p) { return page.endsWith("*") ? p.startsWith(page.substring(0, page.length() - 1)) : page.equals(p); }
    }

    private static Rule rule(String page, Category c, String... params) { return new Rule(page, Set.of(params), c); }

    private static final List<Rule> BODY = List.of(
            rule(BodyDesign.SETTINGS, Category.SHAPE, "noseBluntness", "archRadiusM"),
            rule(BodyDesign.MODULE + "*", Category.SHAPE, "section", "upperExponent", "lowerExponent", "deadriseDeg", "flareDeg", "beltFraction",
                    "roofWidthRatio", "cornerRadiusM", "fullness", "stepM"),
            rule(BodyDesign.MODULE + "*", Category.SIZE, "lengthM", "widthM", "heightM", "bottomM", "fuelM3", "cargoM3", "seats", "seatsAbreast", "seatPitchM"),
            rule(dev.mcmod.hdfv.part.body.BodyKitDesign.PAGE, Category.KIT, "bumper", "damM", "splitterM", "skirtM", "flareM", "diffuserDeg",
                    "diffuserLengthM", "spoilerM", "wingSpanM", "wingChordM", "wingAngleDeg", "wingHeightM", "endplateM", "roofRack", "scoopM",
                    "exhaustTips", "exhaustDiameterM", "material", "paintColor"),
            // 0.18: the front end (looks only)
            rule(dev.mcmod.hdfv.part.body.BodyFrontDesign.PAGE, Category.SHAPE, "grille", "grilleWidth", "grilleHeight", "grilleSlats", "grilleColor",
                    "emblem", "headlamps", "lampWidth", "lampHeight", "lampWrapM", "bumper", "bumperHeight", "bumperOutM", "bumperColor", "intake",
                    "intakeHeightM", "fog", "bulgeM"));
    private static final List<Rule> CHASSIS = List.of(
            rule(ChassisDesign.FRAME, Category.SIZE, "lengthM", "railSpacingM", "railSpacingRearM"),
            rule("station*", Category.SIZE, "xM"));
    private static final List<Rule> AXLE = List.of(
            rule("tyre", Category.SIZE, "sectionWidthM", "aspectRatio", "rimDiameterIn"),
            rule("wheel", Category.SIZE, "rimWidthIn"),
            rule("layout", Category.SIZE, "trackM"),
            rule("wheel", Category.RIM, "rimFinish", "rimStyle", "rimColor", "lipDepthM", "spokes"),
            rule("tyre", Category.RIM, "sideText", "sideTextColor"),
            rule("tyre", Category.TUNE, "pressurePa"),
            rule("layout", Category.TUNE, "hubDropM", "antiRollNPerM"),
            rule("suspension", Category.TUNE, "designCompressionM", "springRateNPerM", "bumpNsPerM", "reboundNsPerM", "camberDeg", "toeDeg", "rideLowM", "rideHighM", "levelRatePerS"),
            rule("differential", Category.TUNE, "finalRatio", "preloadNm", "lockFactor"));
    private static final List<Rule> GEAR = List.of(
            rule("tyre", Category.SIZE, "sectionWidthM", "aspectRatio", "rimDiameterIn"),
            rule("wheel", Category.SIZE, "rimWidthIn"),
            rule("wheel", Category.RIM, "rimFinish", "rimStyle", "rimColor", "lipDepthM", "spokes"),
            rule("tyre", Category.RIM, "sideText", "sideTextColor"),
            rule("tyre", Category.TUNE, "pressurePa"));
    private static final List<Rule> GEARBOX = List.of(
            rule("gears", Category.TUNE, "forwardRatios", "reverseRatio"),
            rule("control", Category.TUNE, "shiftTimeS", "upLightRpm", "upFullRpm", "downLightRpm", "downFullRpm", "lockupRpm", "launchRpm"),
            rule("variator", Category.TUNE, "lowRatio", "highRatio"),
            rule("cvtControl", Category.TUNE, "lightRpm", "fullRpm", "lockupRpm"));
    private static final List<Rule> TRANSFER = List.of(rule(Designs.DESIGN_PAGE, Category.TUNE, "lowRatio", "frontShare", "preloadShare"));
    private static final List<Rule> PISTON = List.of(
            rule("ignition", Category.TUNE, "knockControl", "maxAdvanceDeg", "revLimitRpm", "idleRpm"),
            rule("fuelSystem", Category.TUNE, "fullLoadLambda", "partLoadLambda"),
            rule("camshaft", Category.TUNE, "intakeCentrelineDeg", "exhaustCentrelineDeg"),
            rule("turbo", Category.TUNE, "maxBoostRatio"));
    private static final List<Rule> JET = List.of(
            rule("control", Category.TUNE, "turbineInletK", "idleFraction", "maxSpeedFraction", "accelerationSchedule", "surgeReserve"));

    private static List<Rule> rules(PartDesign d) {
        return switch (d) {
            case BodyDesign b -> BODY;
            case ChassisDesign c -> CHASSIS;
            case AxleDesign a -> AXLE;
            case LandingGearDesign l -> GEAR;
            case GearboxDesign g -> GEARBOX;
            case TransferCaseDesign t -> TRANSFER;
            case PistonEngineDesign p -> PISTON;
            case JetEngineDesign j -> JET;
            case TurbofanDesign t -> JET;
            case ShaftTurbineDesign s -> JET;
            default -> List.of();
        };
    }

    /** The parameters of a design the shop may change, in page order (those a page hides in its present state left out). */
    public static List<Adjustable> adjustables(PartDesign d) {
        List<Adjustable> out = new ArrayList<>();
        List<Rule> rules = rules(d);
        if (rules.isEmpty()) { return out; }
        for (String page : Designs.pages(d)) {
            for (RecordEditor.Parameter p : Designs.parameters(d, page)) {
                for (Rule r : rules) {
                    if (r.matches(page) && r.params().contains(p.name())) { out.add(new Adjustable(page, p.name(), r.category())); break; }
                }
            }
        }
        return out;
    }

    /** The adjustables of one category. */
    public static List<Adjustable> adjustables(PartDesign d, Category c) {
        return adjustables(d).stream().filter(a -> a.category() == c).toList();
    }

    /** Whether the shop may change anything of a design. */
    public static boolean refittable(PartDesign d) { return !adjustables(d).isEmpty() || Repaint.paintable(d); }

    /**
     * A blueprint with one adjustable of one part set from an editor's text. IllegalArgumentException when it is not an
     * adjustable of that part, the text is not a value of it, or the part comes out INVALID (with the issue's text).
     */
    public static VehicleBlueprint set(VehicleBlueprint b, String part, String page, String param, String text) {
        VehicleBlueprint.Entry e = b.part(part);
        if (e == null) { throw new IllegalArgumentException("No part " + part); }
        boolean allowed = adjustables(e.design()).stream().anyMatch(a -> a.page().equals(page) && a.param().equals(param));
        if (!allowed) { throw new IllegalArgumentException(page + "." + param + " is not adjustable on a " + e.design().type()); }
        PartDesign next = Designs.set(e.design(), page, param, text);
        refuseInvalid(next);
        return Repaint.withPart(b, part, next);
    }

    /**
     * 0.16: a body's lamps as the shop edits them: its own, or (none yet) the kit's as they are, so the player starts
     * from what the body shows.
     */
    public static List<dev.mcmod.hdfv.look.LampSpec> lamps(BodyDesign d) {
        if (d.settings().ownLamps()) { return d.settings().lamps(); }
        var shape = d.shape();
        var s = new dev.mcmod.hdfv.look.BodySurface(shape);
        return dev.mcmod.hdfv.look.LampLayout.auto(d, s, new dev.mcmod.hdfv.look.BodyLayout(d, shape, s).waterline);
    }

    /** A blueprint with a body's lamps replaced (null: the kit's again; empty: none); refused when the body comes out INVALID (too many, a bad place). */
    public static VehicleBlueprint withLamps(VehicleBlueprint b, String part, List<dev.mcmod.hdfv.look.LampSpec> lamps) {
        VehicleBlueprint.Entry e = b.part(part);
        if (e == null || !(e.design() instanceof BodyDesign d)) { throw new IllegalArgumentException("No body " + part); }
        BodyDesign next = d.withSettings(d.settings().withLamps(lamps));
        refuseInvalid(next);
        return Repaint.withPart(b, part, next);
    }

    /** The whole vehicle rescaled from the scale it has (now) to another (to); each part checked as set is. */
    public static VehicleBlueprint rescale(VehicleBlueprint b, double now, double to) {
        if (!(now > 0) || !(to > 0) || !Double.isFinite(now) || !Double.isFinite(to)) { throw new IllegalArgumentException("A scale is positive"); }
        if (now == to) { return b; }
        VehicleBlueprint next = Scaling.scaled(b, to / now);
        for (VehicleBlueprint.Entry e : next.parts()) { refuseInvalid(e.design()); }
        return next;
    }

    static void refuseInvalid(PartDesign d) {
        for (DesignIssue i : d.check()) {
            if (i.kind() == DesignIssue.Kind.INVALID) { throw new IllegalArgumentException(d.name() + ": " + i.parameter() + ": " + i.message()); }
        }
    }

    /**
     * Whether next is before refitted: the same parts (ids, types, order), each mounted on the same point at the same
     * angles (offsets may be scaled), the same bindings and connections. What the shop's edits keep by construction;
     * checked again when the work starts.
     */
    public static boolean sameVehicle(VehicleBlueprint before, VehicleBlueprint next) {
        if (before.parts().size() != next.parts().size() || !before.connections().equals(next.connections())) { return false; }
        for (int i = 0; i < before.parts().size(); i++) {
            VehicleBlueprint.Entry a = before.parts().get(i), b = next.parts().get(i);
            if (!a.id().equals(b.id()) || !a.design().type().equals(b.design().type()) || !a.bindings().equals(b.bindings())) { return false; }
            var m = a.mount();
            var n = b.mount();
            if (!Objects.equals(m.attachPart(), n.attachPart()) || !Objects.equals(m.attachPoint(), n.attachPoint())
                    || m.yawDeg() != n.yawDeg() || m.pitchDeg() != n.pitchDeg() || m.rollDeg() != n.rollDeg()) { return false; }
        }
        return true;
    }

    /** Which parts differ between two blueprints of the same vehicle, by id. */
    public static List<String> changed(VehicleBlueprint before, VehicleBlueprint next) {
        List<String> out = new ArrayList<>();
        for (VehicleBlueprint.Entry e : next.parts()) {
            VehicleBlueprint.Entry was = before.part(e.id());
            if (was == null || !Designs.write(was.design()).equals(Designs.write(e.design())) || !was.mount().equals(e.mount())) { out.add(e.id()); }
        }
        return out;
    }

    /** The adjustables of every part of a blueprint, by part id (parts with none left out). */
    public static Map<String, List<Adjustable>> adjustables(VehicleBlueprint b) {
        Map<String, List<Adjustable>> out = new java.util.LinkedHashMap<>();
        for (VehicleBlueprint.Entry e : b.parts()) {
            List<Adjustable> a = adjustables(e.design());
            if (!a.isEmpty()) { out.put(e.id(), a); }
        }
        return out;
    }
}
