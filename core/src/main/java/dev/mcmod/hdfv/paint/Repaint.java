package dev.mcmod.hdfv.paint;

import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.VehicleBlueprint;
import dev.mcmod.hdfv.design.Designs;
import dev.mcmod.hdfv.design.RecordEditor;
import dev.mcmod.hdfv.look.Sticker;
import dev.mcmod.hdfv.part.air.WingDesign;
import dev.mcmod.hdfv.part.body.BodyDesign;

import java.util.ArrayList;
import java.util.List;

/**
 * 0.13: a vehicle's looks changed in place (the paint booth, development commands): a part's stickers replaced, a part
 * of a blueprint replaced by its repainted design. Bodies and wings carry stickers; other parts none. Looks only: the
 * physics of what is changed stays the same (BodyTest.looksChangeNothingElse), so a vehicle takes it without a reset
 * (PartVehicle.replaceBlueprint).
 */
public final class Repaint {
    private Repaint() {}

    /** Whether a design carries stickers (a body, a wing). */
    public static boolean takesStickers(PartDesign d) { return d instanceof BodyDesign || d instanceof WingDesign; }

    /** A design's stickers (none for a part that carries none). */
    public static List<Sticker> stickers(PartDesign d) {
        return d instanceof BodyDesign b ? b.settings().stickers() : d instanceof WingDesign w ? w.structure().stickers() : List.of();
    }

    /** The design with its stickers replaced; IllegalArgumentException for a part that carries none. */
    public static PartDesign withStickers(PartDesign d, List<Sticker> stickers) {
        if (d instanceof BodyDesign b) { return Designs.withPage(b, BodyDesign.SETTINGS, RecordEditor.with(b.settings(), "stickers", List.copyOf(stickers))); }
        if (d instanceof WingDesign w) { return Designs.withPage(w, "structure", RecordEditor.with(w.structure(), "stickers", List.copyOf(stickers))); }
        throw new IllegalArgumentException("A " + d.type() + " carries no stickers");
    }

    /** The blueprint with a part's design replaced (its id, mount and bindings kept); IllegalArgumentException for an unknown id. */
    public static VehicleBlueprint withPart(VehicleBlueprint b, String id, PartDesign design) {
        List<VehicleBlueprint.Entry> parts = new ArrayList<>(b.parts().size());
        boolean found = false;
        for (VehicleBlueprint.Entry e : b.parts()) {
            if (e.id().equals(id)) { parts.add(new VehicleBlueprint.Entry(e.id(), design, e.mount(), e.bindings())); found = true; }
            else { parts.add(e); }
        }
        if (!found) { throw new IllegalArgumentException("No part " + id); }
        return new VehicleBlueprint(b.name(), parts, b.connections());
    }

    /** A look the paint booth may change: a parameter of a page (Designs' pages and RecordEditor's names). */
    public record Look(String page, String param) {}

    private static final List<String> BODY_LOOKS = List.of("paintColor", "accentColor", "lowerColor", "paintFinish", "glassColor", "glassOpacity",
            "registrationText", "stripes", "stripeColor");

    /**
     * The looks of a design the paint booth may change: a body's paint, glass, registration and stripes and each module's
     * colour; a wing's paint; a wheel's rim finish (0.16: and its face's style, colour and depth, and the tyres' lettering).
     * Nothing else (its shape, its arches' size) is the booth's.
     */
    public static List<Look> looks(PartDesign d) {
        List<Look> out = new ArrayList<>();
        if (d instanceof BodyDesign b) {
            for (String p : BODY_LOOKS) { out.add(new Look(BodyDesign.SETTINGS, p)); }
            for (int i = 0; i < b.modules().size(); i++) { out.add(new Look(BodyDesign.MODULE + (i + 1), "paintColor")); }
            if (b.kitted()) {
                out.add(new Look(dev.mcmod.hdfv.part.body.BodyKitDesign.PAGE, "paintColor"));   // 0.16: the kit's colour
                // 0.18: the front's grille and bumper colours
                out.add(new Look(dev.mcmod.hdfv.part.body.BodyFrontDesign.PAGE, "grilleColor"));
                out.add(new Look(dev.mcmod.hdfv.part.body.BodyFrontDesign.PAGE, "bumperColor"));
            }
        } else if (d instanceof WingDesign) {
            out.add(new Look("structure", "paintColor"));
            out.add(new Look("structure", "paintFinish"));
        } else if (d instanceof dev.mcmod.hdfv.part.wheel.AxleDesign || d instanceof dev.mcmod.hdfv.part.wheel.LandingGearDesign) {
            out.add(new Look("wheel", "rimFinish"));
            // 0.16: the face's style, colour and depth, the sidewalls' lettering
            for (String p : new String[]{"rimStyle", "rimColor", "lipDepthM"}) { out.add(new Look("wheel", p)); }
            for (String p : new String[]{"sideText", "sideTextColor"}) { out.add(new Look("tyre", p)); }
        }
        return out;
    }

    /** Whether a part has anything the booth changes (looks or stickers). */
    public static boolean paintable(PartDesign d) { return !looks(d).isEmpty(); }

    /** A look set from an editor's text; IllegalArgumentException for what is not a look of it or a bad value. */
    public static PartDesign setLook(PartDesign d, String page, String param, String text) {
        if (!looks(d).contains(new Look(page, param))) { throw new IllegalArgumentException(page + "." + param + " is not a look of a " + d.type()); }
        return Designs.set(d, page, param, text);
    }

    /** A look's value now. */
    public static Object look(PartDesign d, Look look) { return value(Designs.page(d, look.page()), look.param()); }

    /**
     * Whether b differs from a in looks and stickers alone: b with a's looks and stickers put back writes the same JSON as a
     * (same type and name too). What the paint booth sends is checked with it.
     */
    public static boolean looksOnly(PartDesign a, PartDesign b) {
        if (!a.type().equals(b.type())) { return false; }
        PartDesign back = b;
        try {
            for (Look l : looks(a)) { back = Designs.withPage(back, l.page(), RecordEditor.with(Designs.page(back, l.page()), l.param(), look(a, l))); }
            if (takesStickers(a)) { back = withStickers(back, stickers(a)); }
        } catch (RuntimeException e) {
            return false;
        }
        return Designs.write(back).equals(Designs.write(a));
    }

    /** Whether next differs from before in looks and stickers alone: the same parts (ids, mounts, bindings, order) and connections. */
    public static boolean looksOnly(VehicleBlueprint before, VehicleBlueprint next) {
        if (before.parts().size() != next.parts().size() || !before.connections().equals(next.connections())) { return false; }
        for (int i = 0; i < before.parts().size(); i++) {
            VehicleBlueprint.Entry a = before.parts().get(i), b = next.parts().get(i);
            if (!a.id().equals(b.id()) || !a.mount().equals(b.mount()) || !a.bindings().equals(b.bindings())) { return false; }
            if (!a.design().equals(b.design()) && !looksOnly(a.design(), b.design())) { return false; }
        }
        return true;
    }

    private static Object value(Record r, String name) {
        for (java.lang.reflect.RecordComponent c : r.getClass().getRecordComponents()) {
            if (!c.getName().equals(name)) { continue; }
            try {
                return c.getAccessor().invoke(r);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
        throw new IllegalArgumentException("No parameter " + name);
    }

    /** How many stickers a blueprint's parts carry together. */
    public static int count(VehicleBlueprint b) {
        int n = 0;
        for (VehicleBlueprint.Entry e : b.parts()) { n += stickers(e.design()).size(); }
        return n;
    }
}
