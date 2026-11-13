package dev.mcmod.hdfv.part.body;

import dev.mcmod.aerocore.api.InertiaTensor;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.body.BodyModule;
import dev.mcmod.aerocore.api.body.BodyShape;
import dev.mcmod.aerocore.api.body.BodyStructure;
import dev.mcmod.aerocore.api.body.SectionShape;
import dev.mcmod.aerocore.api.vehicle.DesignException;
import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;
import dev.mcmod.aerocore.api.vehicle.MassProperties;
import dev.mcmod.aerocore.api.vehicle.Mount;
import dev.mcmod.aerocore.api.vehicle.Part;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.part.BodyPart;
import dev.mcmod.hdfv.part.engine.EngineComponent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A body made of modules (機體: a fuselage, a hull, a car body, or a mix: a flying boat), the modules strung from the
 * nose back (BodyModuleDesign), each starting from the rear section of the one ahead. Its shape, air and water loads
 * come from AeroCore (BodyShape, BodyPart); its structure's mass and stresses from BodyStructure at the settings' load
 * factor, held up where its purpose says (an aircraft at its wing mount, a boat by buoyancy, a car at its axles); its
 * mass is the structure, the seats with their occupants, the cargo and the fuel in the settings' loading. Its origin is
 * the rear end on the reference line (y = 0, usually the keel or the floor).
 * <p>Mount points (Mount.on): every module's "mN_top", "mN_bottom", "mN_port", "mN_starboard" (N from 1 at the nose,
 * at the module's middle), and by kind: a wing ("wing", "wing_2"...) where a module has one, "mast", a nose's "engine",
 * a tail cone's or boom's "tail" (a boom's "tail_rotor" on its starboard side), a bow's "bow", a stern's "transom" and
 * "rudder" (also a boat module's at the rear end, a float's afterbody), a hood's "front_axle" and "engine_bay", a
 * trunk's "rear_axle"; and (0.6) every engine bay module's "mN_engine" (low in the bay, on the centreline) and every
 * axle module's "mN_axle" (axleAt back from its front end, 0.1 m above its bottom), so an engine goes in front, in the
 * middle or behind the cabin and an axle under any module.
 * <p>0.16: a car's kit (BodyKitDesign, the "kit" page: bumpers, dam, splitter, skirts, flares, diffuser, spoiler, wing,
 * roof rack, scoop, exhaust tips; KitLayout places it): its mass joins the body's and its air loads the body part's
 * (AeroCore's RoadAero). Only a car's body has the page; without a kit its JSON has no "kit" key and it is as before.
 * <p>0.18: a car's front end (BodyFrontDesign, the "front" page: grille, headlamps, bumper, intake, fog lamps, a bulge on
 * the hood; look/FrontLayout places it, automatic from the shape): looks only. All automatic writes no "front" key.
 * <p>0.25: every module with a hold's volume (cargoM3) is a cargo hold of the compiled body (Part.Hold "m3_cargo": a
 * car's boot, a lorry's box, an aircraft's or a boat's hold) whose cargo the vehicle carries at its centre; the design
 * cargo (cargoFill of it at cargoDensityKgM3) stays the load the structure is checked with and the bench's laden mass,
 * and is no longer in the compiled body's mass (dryMass). A module's hold is rated for its volume at cargoDensityKgM3
 * (holdLimitKg; storage.CargoRules).
 */
public record BodyDesign(String name, BodySettingsDesign settings, List<BodyModuleDesign> modules, BodyKitDesign kit, BodyFrontDesign front)
        implements PartDesign, dev.mcmod.hdfv.design.ListedDesign {
    public static final String TYPE = "hdfv:body";
    public static final String SETTINGS = "settings", MODULE = "module";
    /** Each seat's own mass (the occupant's comes from the settings), kg. */
    public static final double SEAT_KG = 12;

    public BodyDesign {
        Objects.requireNonNull(name, "name"); Objects.requireNonNull(settings, "settings");
        modules = List.copyOf(modules);
        if (kit == null) { kit = BodyKitDesign.NONE; }
        if (front == null) { front = BodyFrontDesign.AUTO; }
    }

    /** A body without a kit (as before 0.16). */
    public BodyDesign(String name, BodySettingsDesign settings, List<BodyModuleDesign> modules) { this(name, settings, modules, BodyKitDesign.NONE, BodyFrontDesign.AUTO); }

    /** 0.16's body: its front automatic. */
    public BodyDesign(String name, BodySettingsDesign settings, List<BodyModuleDesign> modules, BodyKitDesign kit) { this(name, settings, modules, kit, BodyFrontDesign.AUTO); }

    /** Whether it has the kit page (a car's body). */
    public boolean kitted() { return settings.purpose() == BodySettingsDesign.Purpose.CAR; }

    @Override public String type() { return TYPE; }

    // ---------------------------------------------------------------- pages and editing

    /** The settings page and one page per module ("module1" at the nose). */
    public Map<String, EngineComponent> components() {
        Map<String, EngineComponent> m = new LinkedHashMap<>();
        m.put(SETTINGS, settings);
        for (int i = 0; i < modules.size(); i++) { m.put(page(i), modules.get(i)); }
        if (kitted()) { m.put(BodyFrontDesign.PAGE, front); m.put(BodyKitDesign.PAGE, kit); }
        return m;
    }

    public static String page(int index) { return MODULE + (index + 1); }

    /** The module index of a page, or -1. */
    public static int index(String page) {
        if (page == null || !page.startsWith(MODULE)) { return -1; }
        try { return Integer.parseInt(page.substring(MODULE.length())) - 1; } catch (NumberFormatException e) { return -1; }
    }

    public BodyDesign withSettings(BodySettingsDesign s) { return new BodyDesign(name, s, modules, kit, front); }

    public BodyDesign withKit(BodyKitDesign k) { return new BodyDesign(name, settings, modules, k, front); }

    public BodyDesign withFront(BodyFrontDesign f) { return new BodyDesign(name, settings, modules, kit, f); }

    public BodyDesign withModule(int index, BodyModuleDesign m) {
        List<BodyModuleDesign> list = new ArrayList<>(modules);
        list.set(index, m);
        return new BodyDesign(name, settings, list, kit, front);
    }

    /** A new module of a kind after the one at index (-1: at the nose), sized to follow it. */
    public BodyDesign added(int after, BodyModuleDesign.Kind kind) {
        List<BodyModuleDesign> list = new ArrayList<>(modules);
        list.add(after + 1, BodyModuleDesign.defaults(kind, after >= 0 && after < list.size() ? list.get(after) : null));
        return new BodyDesign(name, settings, list, kit, front);
    }

    public BodyDesign removed(int index) {
        if (modules.size() <= 1) { return this; }
        List<BodyModuleDesign> list = new ArrayList<>(modules);
        list.remove(index);
        return new BodyDesign(name, settings, list, kit, front);
    }

    public BodyDesign moved(int index, int by) {
        int to = index + by;
        if (to < 0 || to >= modules.size()) { return this; }
        List<BodyModuleDesign> list = new ArrayList<>(modules);
        list.add(to, list.remove(index));
        return new BodyDesign(name, settings, list, kit, front);
    }

    public BodyDesign duplicated(int index) {
        List<BodyModuleDesign> list = new ArrayList<>(modules);
        list.add(index + 1, modules.get(index));
        return new BodyDesign(name, settings, list, kit, front);
    }

    // 0.15: the design screen's list tools (ListedDesign): the modules, the page names as before
    @Override public String listName() { return MODULE; }
    @Override public int items() { return modules.size(); }
    @Override public List<BodyModuleDesign.Kind> itemKinds() { return List.of(BodyModuleDesign.Kind.values()); }
    @Override public Enum<?> itemKind(int index) { return modules.get(index).kind(); }
    @Override public BodyDesign added(int after, Enum<?> kind) { return added(after, (BodyModuleDesign.Kind) kind); }

    @Override public BodyDesign withPage(String page, Record value) {
        if (page.equals(SETTINGS)) { return withSettings((BodySettingsDesign) value); }
        if (page.equals(BodyKitDesign.PAGE)) { return withKit((BodyKitDesign) value); }
        if (page.equals(BodyFrontDesign.PAGE)) { return withFront((BodyFrontDesign) value); }
        int i = index(page);
        if (i < 0 || i >= modules.size()) { throw new IllegalArgumentException("No page " + page); }
        return withModule(i, (BodyModuleDesign) value);
    }

    // ---------------------------------------------------------------- geometry, structure and mass

    public BodyShape shape() {
        List<BodyModule> list = new ArrayList<>();
        for (int i = 0; i < modules.size(); i++) {
            BodyModuleDesign m = modules.get(i);
            SectionShape rear = m.rear();
            // an open module's edge above its lowest bottom: the rear's, or the front's (the module ahead's rear, plus a step)
            double front = i > 0 ? modules.get(i - 1).rear().bottomM() + m.stepM() : Double.POSITIVE_INFINITY;
            double sill = m.open() ? Math.min(rear.bottomM(), front) + m.sillM() : Double.POSITIVE_INFINITY;
            list.add(new BodyModule(m.name(), m.lengthM(), rear, m.fullness(), i > 0 ? m.stepM() : 0, sill));
        }
        return new BodyShape(modules.get(0).rear().scaled(Math.max(settings.noseBluntness(), 0.02)), list);
    }

    public List<BodyStructure.Module> structure() {
        List<BodyStructure.Module> out = new ArrayList<>();
        for (BodyModuleDesign m : modules) { out.add(m.structure()); }
        return out;
    }

    /** One seat: where its occupant sits (body frame) and its module. */
    public record Seat(Vec3d at, int module) {}

    /**
     * Every seat, row by row from each module's front, the rows abreast across its width. 0.14 (the user: 「前 2 後 3」):
     * a car's passenger cell puts a short row in front, on its outboard places (five three abreast: two in front, three
     * behind, rather than a bench of three and two behind), its places spread to CAR_SEAT_EDGE_M inside its sides (the
     * door, half a seat and room; at most 0.55 m apart). Aircraft and boat modules as before. 0.18: a cab-over cab's first
     * row sits CAB_SEAT_BACK_M behind its flat front (over the front axle, the dashboard and wheel ahead of it).
     */
    public List<Seat> seats(BodyShape shape) {
        List<Seat> out = new ArrayList<>();
        for (int i = 0; i < modules.size(); i++) {
            BodyModuleDesign m = modules.get(i);
            int abreast = Math.max(m.seatsAbreast(), 1);
            if (m.kind() == BodyModuleDesign.Kind.PASSENGER_CELL || m.kind() == BodyModuleDesign.Kind.CAB_OVER) {
                int rows = (m.seats() + abreast - 1) / abreast;
                for (int row = 0; row < rows; row++) {
                    int count = row == 0 ? m.seats() - (rows - 1) * abreast : abreast;
                    double x = shape.moduleFrontX(i) - (m.kind() == BodyModuleDesign.Kind.CAB_OVER ? CAB_SEAT_BACK_M + row * m.seatPitchM()
                            : (row + 0.5) * m.seatPitchM());
                    double o = abreast > 1 ? Math.clamp(0.5 * shape.widthAt(x) - CAR_SEAT_EDGE_M, 0, 0.55 * (abreast - 1) / 2) : 0;
                    List<Integer> cols = new ArrayList<>(outboardFirst(abreast).subList(0, count));
                    java.util.Collections.sort(cols);
                    for (int col : cols) {
                        double z = abreast > 1 ? -o + col * 2 * o / (abreast - 1) : 0;
                        out.add(new Seat(new Vec3d(x, shape.bottomAt(x) + 0.3, z), i));
                    }
                }
                continue;
            }
            for (int k = 0; k < m.seats(); k++) {
                int row = k / abreast, col = k % abreast;
                double x = shape.moduleFrontX(i) - (row + 0.5) * m.seatPitchM();
                double z = (col - (abreast - 1) / 2.0) * Math.min(0.55, shape.widthAt(x) / abreast);
                out.add(new Seat(new Vec3d(x, shape.bottomAt(x) + 0.3, z), i));
            }
        }
        return out;
    }

    /** 0.18: how far behind a cab-over cab's front its first row of seats is, m. */
    public static final double CAB_SEAT_BACK_M = 1.1;

    /** A car seat's centre from its side, m (0.14): real front seats' centres are 0.37-0.40 m out in a 1.8 m wide car. */
    public static final double CAR_SEAT_EDGE_M = 0.52;

    /** Places across a row, outermost first, port before starboard: 0, n-1, 1, n-2... */
    static List<Integer> outboardFirst(int n) {
        List<Integer> out = new ArrayList<>();
        for (int a = 0, b = n - 1; a <= b; a++, b--) { out.add(a); if (b != a) { out.add(b); } }
        return out;
    }

    /**
     * The driver's (pilot's, helmsman's) seat among seats (index), -1 without: a car's front left seat in its passenger
     * cell (left-hand drive), an aircraft's front left seat (the pilot in command; a flying boat's too: by the body's
     * purpose), a boat's front right one (the helm to starboard). 0.14: here from BodyLayout, and the body's compiled
     * seats start with it, so the first to get in drives.
     */
    public int driverSeat(List<Seat> all) {
        if (all.isEmpty()) { return -1; }
        // the module the controls are in: a car's glazed cell, an aircraft's cockpit, else the frontmost with seats
        int controls = -1;
        for (int i = 0; i < modules.size() && controls < 0; i++) {
            BodyModuleDesign m = modules.get(i);
            if (m.seats() > 0 && (m.kind() == BodyModuleDesign.Kind.PASSENGER_CELL || m.kind() == BodyModuleDesign.Kind.COCKPIT
                    || m.kind() == BodyModuleDesign.Kind.CAB_OVER)) { controls = i; }
        }
        if (controls < 0) { controls = all.get(0).module(); }
        boolean boat = settings.purpose() == BodySettingsDesign.Purpose.BOAT;
        int best = -1;
        for (int k = 0; k < all.size(); k++) {
            Seat st = all.get(k);
            if (st.module() != controls) { continue; }
            if (best < 0) { best = k; continue; }
            Seat b = all.get(best);
            boolean ahead = st.at().x() > b.at().x() + 1e-6, abreast = Math.abs(st.at().x() - b.at().x()) <= 1e-6;
            boolean nearer = boat ? st.at().z() > b.at().z() : st.at().z() < b.at().z();
            if (ahead || (abreast && nearer)) { best = k; }
        }
        return best;
    }

    /** Things carried, as point masses (body frame): occupants in their seats, cargo and fuel low in their modules. */
    public List<BodyStructure.PointMass> carried(BodyShape shape) {
        List<BodyStructure.PointMass> out = new ArrayList<>();
        for (Seat s : seats(shape)) { out.add(new BodyStructure.PointMass(s.at().x(), SEAT_KG + settings.occupantKg())); }
        for (int i = 0; i < modules.size(); i++) {
            double x = 0.5 * (shape.moduleFrontX(i) + shape.moduleRearX(i));
            double load = cargoKg(i) + fuelKg(i);
            if (load > 0) { out.add(new BodyStructure.PointMass(x, load)); }
        }
        if (settings.supportedKg() > 0) { out.add(new BodyStructure.PointMass(settings.supportedAt() * shape.lengthM(), settings.supportedKg())); }
        return out;
    }

    /**
     * A module's design cargo, kg: its hold at the settings' density and fill. What the structure is checked with and the
     * design screen's laden figures (mass(), the bench); from 0.25 not in the compiled body (dryMass): what a vehicle
     * carries is what is in its holds (Part.Hold, the game's items).
     */
    public double cargoKg(int i) { return modules.get(i).cargoM3() * settings.cargoDensityKgM3() * settings.cargoFill(); }
    public double fuelKg(int i) { return modules.get(i).fuelM3() * settings.fuelDensity() * settings.fuelFill(); }

    /** 0.25: what a module's hold is rated to carry, kg: its volume full at the settings' cargo density (0 without a hold). */
    public double holdLimitKg(int i) { return modules.get(i).cargoM3() * settings.cargoDensityKgM3(); }

    /** 0.25: the design payload, kg: every hold full at the settings' cargo density. */
    public double payloadKg() {
        double kg = 0;
        for (int i = 0; i < modules.size(); i++) { kg += holdLimitKg(i); }
        return kg;
    }

    /** 0.25: the holds' volume, m^3. */
    public double holdM3() {
        double m3 = 0;
        for (BodyModuleDesign m : modules) { m3 += Math.max(m.cargoM3(), 0); }
        return m3;
    }

    /**
     * 0.25: a module's cargo hold as BodyModel draws it (body frame: min and max corners): in the rear part of the
     * module's middle 80 %, from 5 % of its depth up, a box of its volume in the room of half that length, 60 % of the
     * depth and 70 % of the width (shrunk alike on every side when the volume is smaller, never grown past it); null
     * without a hold.
     */
    public Vec3d[] cargoBox(BodyShape shape, int i) {
        BodyModuleDesign m = modules.get(i);
        if (!(m.cargoM3() > 0)) { return null; }
        double front = shape.moduleFrontX(i), rear = shape.moduleRearX(i), x = 0.5 * (front + rear), len = 0.8 * (front - rear);
        double bottom = shape.bottomAt(x), h = shape.topAt(x) - bottom, w = 0.7 * shape.widthAt(x);
        double rl = 0.5 * len, rh = 0.6 * h, room = Math.max(rl * rh * w, 1e-6), f = Math.min(Math.cbrt(m.cargoM3() / room), 1);
        double l = rl * f, hh = rh * f, ww = w * f, cx = x + 0.25 * len, y0 = bottom + 0.05 * h;
        return new Vec3d[]{new Vec3d(cx - l / 2, y0, -ww / 2), new Vec3d(cx + l / 2, y0 + hh, ww / 2)};
    }

    /** 0.25: its cargo holds (Part.Hold): one for each module with a hold's volume, named CargoRules.holdName, at the middle of its cargoBox. */
    public List<Part.Hold> holds(BodyShape shape) {
        List<Part.Hold> out = new ArrayList<>();
        for (int i = 0; i < modules.size(); i++) {
            Vec3d[] box = cargoBox(shape, i);
            if (box == null) { continue; }
            out.add(new Part.Hold(dev.mcmod.hdfv.storage.CargoRules.holdName(i), modules.get(i).cargoM3(), box[0].add(box[1]).scale(0.5)));
        }
        return out;
    }

    /** Where a module's tank is (body frame): low in it, mid-length, on the centre line. */
    private static Vec3d tankAt(BodyShape shape, int i) {
        double front = shape.moduleFrontX(i), rear = shape.moduleRearX(i), x = 0.5 * (front + rear);
        double h = Math.max(shape.topAt(x) - shape.bottomAt(x), 0.05);
        return new Vec3d(x, shape.bottomAt(x) + 0.25 * h, 0);
    }

    /** Its fuel tanks (0.8): one for each module with a tank's volume, of the settings' fuel, filled as the settings say. */
    public List<Part.Tank> tanks(BodyShape shape) {
        List<Part.Tank> out = new ArrayList<>();
        for (int i = 0; i < modules.size(); i++) {
            if (modules.get(i).fuelM3() > 0) { out.add(new Part.Tank(page(i), modules.get(i).fuelM3(), tankAt(shape, i), settings.fuel(), fuelKg(i))); }
        }
        return out;
    }

    /**
     * Mass, centre and inertia laden as designed: each module's structure as a box of its size, the seats, occupants,
     * design cargo (cargoKg) and fuel as points. The design screen's and the bench's.
     */
    public MassProperties mass() { return mass(true, true); }

    /**
     * What the compiled body weighs: the same without the fuel (the vehicle carries that in the tanks) and, from 0.25,
     * without the design cargo (the vehicle carries what is loaded in its holds; before 0.25 the design cargo was part
     * of the body and a vehicle could not carry more or less).
     */
    public MassProperties dryMass() { return mass(false, false); }

    private MassProperties mass(boolean fuel, boolean cargo) {
        BodyShape shape = shape();
        List<BodyStructure.ModuleMass> masses = BodyStructure.masses(shape, structure());
        List<MassProperties> parts = new ArrayList<>();
        for (int i = 0; i < modules.size(); i++) {
            double front = shape.moduleFrontX(i), rear = shape.moduleRearX(i), x = 0.5 * (front + rear);
            double h = Math.max(shape.topAt(x) - shape.bottomAt(x), 0.05), w = Math.max(shape.widthAt(x), 0.05), kg = Math.max(masses.get(i).totalKg(), 1e-3);
            parts.add(MassProperties.of(kg, new Vec3d(x, shape.centreAt(x), 0), InertiaTensor.box(kg, new Vec3d(Math.max(front - rear, 0.05), h, w))));
            double load = (cargo ? cargoKg(i) : 0) + (fuel ? fuelKg(i) : 0);
            if (load > 0) { parts.add(MassProperties.point(load, tankAt(shape, i))); }
        }
        for (Seat s : seats(shape)) { parts.add(MassProperties.point(SEAT_KG + settings.occupantKg(), s.at())); }
        if (kitted()) {
            dev.mcmod.hdfv.look.KitLayout k = dev.mcmod.hdfv.look.KitLayout.of(this, shape);
            if (k.massKg > 0) { parts.add(MassProperties.point(k.massKg, k.centre)); }
        }
        return MassProperties.combine(parts);
    }

    /** The structure's own mass (skin, frames, floor, openings), kg. */
    public double structureKg() {
        double kg = 0;
        for (BodyStructure.ModuleMass m : BodyStructure.masses(shape(), structure())) { kg += m.totalKg(); }
        return kg;
    }

    /**
     * A new body of a purpose, its modules at their kinds' own proportions: an aircraft's nose, cockpit,
     * cabin and tail cone; a boat's bow, forebody, afterbody and stern; a car's hood, passenger cell and trunk.
     */
    public static BodyDesign fresh(BodySettingsDesign.Purpose purpose) {
        List<BodyModuleDesign.Kind> kinds = switch (purpose) {
            case AIRCRAFT -> List.of(BodyModuleDesign.Kind.NOSE, BodyModuleDesign.Kind.COCKPIT, BodyModuleDesign.Kind.CABIN, BodyModuleDesign.Kind.TAIL_CONE);
            case BOAT -> List.of(BodyModuleDesign.Kind.BOW, BodyModuleDesign.Kind.FOREBODY, BodyModuleDesign.Kind.AFTERBODY, BodyModuleDesign.Kind.STERN);
            case CAR -> List.of(BodyModuleDesign.Kind.HOOD, BodyModuleDesign.Kind.PASSENGER_CELL, BodyModuleDesign.Kind.TRUNK);
        };
        List<BodyModuleDesign> modules = new ArrayList<>();
        for (BodyModuleDesign.Kind k : kinds) { modules.add(BodyModuleDesign.defaults(k, null)); }
        return new BodyDesign("new body", BodySettingsDesign.of(purpose), modules);
    }

    /** Named mount points in the body frame (see the class description). */
    public Map<String, Mount> mountPoints(BodyShape shape) {
        Map<String, Mount> out = new LinkedHashMap<>();
        for (int i = 0; i < modules.size(); i++) {
            BodyModuleDesign m = modules.get(i);
            double front = shape.moduleFrontX(i), rear = shape.moduleRearX(i), x = 0.5 * (front + rear), len = front - rear;
            double top = shape.topAt(x), bottom = shape.bottomAt(x), centre = shape.centreAt(x), half = 0.5 * shape.widthAt(x);
            String n = "m" + (i + 1);
            out.put(n + "_top", Mount.at(new Vec3d(x, top, 0)));
            out.put(n + "_bottom", Mount.at(new Vec3d(x, bottom, 0)));
            out.put(n + "_port", Mount.at(new Vec3d(x, centre, -half)));
            out.put(n + "_starboard", Mount.at(new Vec3d(x, centre, half)));
            if (m.wing() != BodyModuleDesign.WingMount.NONE) {
                double y = switch (m.wing()) { case LOW -> bottom + 0.1 * (top - bottom); case MID -> centre; default -> top; };
                put(out, "wing", Mount.at(new Vec3d(x, y, 0)));
            }
            if (m.mast()) { put(out, "mast", Mount.at(new Vec3d(x, top, 0))); }
            if (m.engineBay()) { out.put(n + "_engine", Mount.at(new Vec3d(x, bottom + 0.35 * (top - bottom), 0))); }
            if (m.axle()) {
                double ax = front - m.axleAt() * len;
                out.put(n + "_axle", Mount.at(new Vec3d(ax, shape.bottomAt(ax) + 0.1, 0)));
            }
            switch (m.kind()) {
                case NOSE -> put(out, "engine", Mount.at(new Vec3d(front, shape.centreAt(front), 0)));
                case TAIL_CONE, TAIL_BOOM -> {
                    put(out, "tail", Mount.at(new Vec3d(rear, shape.centreAt(rear), 0)));
                    if (m.kind() == BodyModuleDesign.Kind.TAIL_BOOM) { put(out, "tail_rotor", Mount.at(new Vec3d(rear, shape.centreAt(rear), 0.5 * shape.widthAt(rear)))); }
                }
                case BOW -> put(out, "bow", Mount.at(new Vec3d(front, shape.topAt(front), 0)));
                case STERN -> stern(out, shape, rear);
                case HOOD -> {
                    put(out, "front_axle", Mount.at(new Vec3d(front - Math.min(0.85, 0.7 * len), bottom + 0.1, 0)));
                    put(out, "engine_bay", Mount.at(new Vec3d(x, bottom + 0.35 * (top - bottom), 0)));
                }
                case TRUNK -> put(out, "rear_axle", Mount.at(new Vec3d(rear + Math.min(0.9, 0.7 * len), bottom + 0.1, 0)));
                case CAB_OVER -> put(out, "front_axle", Mount.at(new Vec3d(front - m.axleAt() * len, bottom + 0.1, 0)));   // 0.18
                default -> {
                    if (i == modules.size() - 1 && m.kind().family == BodyModuleDesign.Family.BOAT) { stern(out, shape, rear); }
                }
            }
        }
        return out;
    }

    private static void stern(Map<String, Mount> out, BodyShape shape, double rear) {
        put(out, "transom", Mount.at(new Vec3d(rear, shape.bottomAt(rear) + 0.25 * (shape.topAt(rear) - shape.bottomAt(rear)), 0)));
        put(out, "rudder", Mount.at(new Vec3d(rear, shape.bottomAt(rear), 0)));
    }

    private static void put(Map<String, Mount> out, String name, Mount m) {
        String key = name;
        for (int k = 2; out.containsKey(key); k++) { key = name + "_" + k; }
        out.put(key, m);
    }

    /** Drag the smooth shape leaves out: door seams, window frames, open cockpits, and the settings' own, m^2. */
    public double extraDragAreaM2(BodyShape shape) {
        double area = settings.extraDragAreaM2();
        for (int i = 0; i < modules.size(); i++) {
            BodyModuleDesign m = modules.get(i);
            double len = shape.moduleFrontX(i) - shape.moduleRearX(i), x = 0.5 * (shape.moduleFrontX(i) + shape.moduleRearX(i));
            area += m.doors() * 2 * (m.doorWidthM() + m.doorHeightM()) * 4e-4;       // a seam's drag, about 0.4 cm^2 per metre
            area += m.windowShare() * len * (shape.topAt(x) - shape.bottomAt(x)) * 2e-3;   // frames round flush windows
            if (m.open()) { area += 0.15 * len * shape.widthAt(x); }                  // an open cockpit or boat (Hoerner)
        }
        return area;
    }

    // ---------------------------------------------------------------- checks

    /** Where the structure is held up for its check: the wing, the axles, or (null) the water along the length. */
    public List<double[]> supports(BodyShape shape) {
        Map<String, Mount> points = mountPoints(shape);
        List<double[]> s = new ArrayList<>();
        switch (settings.purpose()) {
            case AIRCRAFT -> {
                for (Map.Entry<String, Mount> e : points.entrySet()) { if (e.getKey().startsWith("wing") || e.getKey().equals("mast")) { s.add(new double[]{e.getValue().position().x(), 1}); } }
                if (s.isEmpty()) { s.add(new double[]{shape.particulars().xMaxAreaM(), 1}); }
            }
            case CAR -> {
                for (Map.Entry<String, Mount> e : points.entrySet()) {
                    if (e.getKey().equals("front_axle") || e.getKey().equals("rear_axle") || e.getKey().endsWith("_axle") && e.getKey().startsWith("m")) {
                        double x = e.getValue().position().x();
                        if (s.stream().noneMatch(p -> Math.abs(p[0] - x) < 1e-6)) { s.add(new double[]{x, 1}); }
                    }
                }
                if (s.size() < 2) { s.clear(); s.add(new double[]{0.15 * shape.lengthM(), 1}); s.add(new double[]{0.85 * shape.lengthM(), 1}); }
            }
            case BOAT -> { return null; }
        }
        return s;
    }

    /** The structure at the ultimate load (1.5 times the settings' limit load factor), loaded as checked. */
    public BodyStructure.Result structuralCheck() {
        BodyShape shape = shape();
        return BodyStructure.analyze(shape, structure(), carried(shape), 1.5 * settings.loadFactor(), 9.80665, supports(shape));
    }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        if (modules.isEmpty()) { issues.add(DesignIssue.invalid(MODULE, "A body needs at least one module")); return issues; }
        for (Map.Entry<String, EngineComponent> e : components().entrySet()) {
            for (DesignIssue i : e.getValue().check()) { issues.add(i.under(e.getKey())); }
        }
        if (issues.stream().anyMatch(i -> i.kind() == DesignIssue.Kind.INVALID)) { return issues; }
        BodyShape shape;
        try { shape = shape(); } catch (IllegalArgumentException bad) {
            issues.add(DesignIssue.invalid(MODULE, "The modules do not make a body: " + bad.getMessage()));
            return issues;
        }
        BodyStructure.Result r = BodyStructure.analyze(shape, structure(), carried(shape), 1.5 * settings.loadFactor(), 9.80665, supports(shape));
        BodyModuleDesign worst = modules.get(r.maxBendingModule());
        // 0.15: a body on a chassis's frame (onFrame) has the frame carry it: its shell's bending is not checked
        if (!settings.onFrame() && r.maxBendingPa() > worst.material().yieldPa()) {
            issues.add(DesignIssue.invalid(page(r.maxBendingModule()) + ".skinM", String.format(java.util.Locale.ROOT,
                    "At %.1f g (limit %.1f g times 1.5) the skin bends to %.0f MPa, above the %s's yield of %.0f MPa",
                    1.5 * settings.loadFactor(), settings.loadFactor(), r.maxBendingPa() / 1e6, worst.material().name(), worst.material().yieldPa() / 1e6)));
        }
        if (r.maxHoopPa() > 0) {
            BodyModuleDesign p = modules.get(r.maxHoopModule());
            if (1.5 * r.maxHoopPa() > p.material().yieldPa()) {
                issues.add(DesignIssue.invalid(page(r.maxHoopModule()) + ".pressurePa", String.format(java.util.Locale.ROOT,
                        "The cabin pressure stretches the skin to %.0f MPa at 1.5 times, above its yield of %.0f MPa", 1.5 * r.maxHoopPa() / 1e6, p.material().yieldPa() / 1e6)));
            }
        }
        // cargo and fuel must fit in what the seats leave of the module (70 % of its volume, half a cubic metre a seat)
        for (int i = 0; i < modules.size(); i++) {
            BodyModuleDesign m = modules.get(i);
            double volume = moduleVolume(shape, i), free = 0.7 * volume - 0.5 * m.seats();
            if (m.cargoM3() + m.fuelM3() > free + 1e-9) {
                issues.add(DesignIssue.invalid(page(i) + ".cargoM3", String.format(java.util.Locale.ROOT,
                        "Cargo and fuel (%.2f m3) do not fit in the %.2f m3 the module has room for", m.cargoM3() + m.fuelM3(), Math.max(free, 0))));
            }
        }
        // a hull's bottom plating against ISO 12215-5's slamming pressure at the design speed (design stress 0.6 yield)
        double bottom = bottomPressurePa(shape);
        if (bottom > 0) {
            for (int i = 0; i < modules.size(); i++) {
                BodyModuleDesign m = modules.get(i);
                if (m.section() != BodyModuleDesign.Section.CHINED) { continue; }
                double panel = Math.min(m.stiffenerSpacingM(), m.frameSpacingM());
                double need = BodyStructure.plateThicknessM(bottom, panel, 0.6 * m.material().yieldPa());
                if (m.skinM() < need) {
                    issues.add(DesignIssue.invalid(page(i) + ".skinM", String.format(java.util.Locale.ROOT,
                            "The bottom must take %.0f kPa slamming at %.0f m/s (ISO 12215-5): %.1f mm of %s on stiffeners %.2f m apart", bottom / 1000,
                            settings.designSpeedMps(), need * 1000, m.material().name(), panel)));
                }
            }
        }
        BodyShape.Particulars p = shape.particulars();
        DesignIssue.range(issues, "fineness", p.finenessRatio(), 2, 15, "bodies the drag was checked on (Hoerner, Ahmed)");
        // 0.16: the body's own lamps: navigation lights on the right sides
        if (settings.ownLamps()) { dev.mcmod.hdfv.look.LampLayout.check(issues, SETTINGS + ".lamps", this, new dev.mcmod.hdfv.look.BodySurface(shape)); }
        return issues;
    }

    /**
     * ISO 12215-5's bottom design pressure for a hard-chined hull (Pa; 0 if no module is chined): the loaded mass with
     * what it supports, 90 % of the length as the waterline, the widest chined module as the chine beam, the deadrise of
     * the module at 0.4 of the length from the stern, the design speed.
     */
    public double bottomPressurePa(BodyShape shape) {
        double beam = 0, deadrise = 0;
        for (BodyModuleDesign m : modules) {
            if (m.section() == BodyModuleDesign.Section.CHINED && m.widthM() > beam) { beam = m.widthM(); deadrise = m.deadriseDeg(); }
        }
        if (!(beam > 0)) { return 0; }
        for (int i = 0; i < modules.size(); i++) {
            double x = 0.4 * shape.lengthM();
            if (x <= shape.moduleFrontX(i) && x >= shape.moduleRearX(i) && modules.get(i).section() == BodyModuleDesign.Section.CHINED) { deadrise = modules.get(i).deadriseDeg(); }
        }
        double kg = mass().massKg() + settings.supportedKg(), knots = settings.designSpeedMps() / 0.514444;
        return BodyStructure.bottomPressurePa(kg, 0.9 * shape.lengthM(), beam, deadrise, knots, settings.category().factor);
    }

    /** A module's volume, m^3. */
    public static double moduleVolume(BodyShape shape, int i) {
        double v = 0;
        List<BodyShape.Station> st = shape.stations();
        double front = shape.moduleFrontX(i), rear = shape.moduleRearX(i);
        for (int k = 1; k < st.size(); k++) {
            double a = Math.max(st.get(k - 1).xM(), rear), b = Math.min(st.get(k).xM(), front);
            if (b > a) { v += 0.5 * (st.get(k - 1).areaM2() + st.get(k).areaM2()) * (b - a); }
        }
        return v;
    }

    @Override public Part compile() { return compile(true); }

    /** Compiled with its fuel in tanks (as a vehicle carries it), or in its own mass (as before 0.8). */
    Part compile(boolean tanks) {
        List<DesignIssue> issues = check();
        DesignException.throwIfInvalid(issues);
        BodyShape shape = shape();
        List<Vec3d> seats = new ArrayList<>();
        List<Seat> all = seats(shape);
        int driver = driverSeat(all);
        if (driver >= 0) { seats.add(all.get(driver).at()); }   // 0.14: the driver's first (the first rider drives)
        for (int k = 0; k < all.size(); k++) { if (k != driver) { seats.add(all.get(k).at()); } }
        List<DesignIssue> extrapolated = issues.stream().filter(i -> i.kind() == DesignIssue.Kind.EXTRAPOLATION).toList();
        if (!tanks) { return new BodyPart(this, shape, extraDragAreaM2(shape), settings.waterModel(), 6, mass(), mountPoints(shape), seats, extrapolated); }
        dev.mcmod.aerocore.api.body.RoadAero aero = kitted() ? dev.mcmod.hdfv.look.KitLayout.of(this, shape).aero : null;
        // 0.25: the shell can be damaged (its structure; a dented body drags more); its holds carry what is loaded (dryMass has no design cargo)
        return new BodyPart(this, shape, extraDragAreaM2(shape), settings.waterModel(), 6, dryMass(), mountPoints(shape), seats, tanks(shape), extrapolated, twist(shape), aero,
                holds(shape)).wearing(BodyPart.Wearing.typical());
    }

    /**
     * 0.15 (the user: a unibody twists too): a car body that carries its own axles twists between them as its shell lets
     * it (AeroCore's BodyStructure.torsion: Bredt cells, the doors and windows framed bands), the physics then sharing a
     * twist of the ground between its suspensions and its shell. None for a body on a chassis's frame (the frame twists),
     * for aircraft and boats, or with fewer than two axle stations (5 cm apart or more).
     */
    public dev.mcmod.aerocore.api.vehicle.FrameTwist twist(BodyShape shape) {
        if (settings.purpose() != BodySettingsDesign.Purpose.CAR || settings.onFrame()) { return null; }
        List<Double> xs = new ArrayList<>();
        for (Map.Entry<String, Mount> e : mountPoints(shape).entrySet()) {
            String k = e.getKey();
            if (!(k.equals("front_axle") || k.equals("rear_axle") || k.matches("m\\d+_axle"))) { continue; }
            double x = e.getValue().position().x();
            if (xs.stream().noneMatch(v -> Math.abs(v - x) < 0.05)) { xs.add(x); }
        }
        if (xs.size() < 2) { return null; }
        xs.sort(Double::compare);
        double[] at = xs.stream().mapToDouble(Double::doubleValue).toArray();
        BodyStructure.Torsion t = BodyStructure.torsion(shape, structure(), at);
        return new dev.mcmod.aerocore.api.vehicle.FrameTwist(t.stationXM(), t.stiffnessNmPerRad());
    }

    /** 0.15: the body's twist stiffness between its end axles, N m per degree (NaN without a twist). */
    public double twistNmPerDeg() {
        var t = twist(shape());
        return t == null ? Double.NaN : Math.toRadians(1) / t.endToEndFlexibility();
    }

    @Override public Map<String, Object> parameters() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(SETTINGS, settings.toJson());
        List<Object> list = new ArrayList<>();
        for (BodyModuleDesign d : modules) { list.add(d.toJson()); }
        m.put("modules", list);
        if (!kit.none()) { m.put(BodyKitDesign.PAGE, kit.toJson()); }
        if (!front.auto()) { m.put(BodyFrontDesign.PAGE, front.toJson()); }
        return m;
    }

    public static PartDesign read(Fields f) {
        Fields s = f.child(SETTINGS);
        BodySettingsDesign settings = BodySettingsDesign.read(s.string("name", SETTINGS), s.has("parameters") ? s.child("parameters") : s);
        List<BodyModuleDesign> modules = new ArrayList<>();
        for (Fields m : f.objects("modules")) {
            modules.add(BodyModuleDesign.read(m.string("name", MODULE), m.has("parameters") ? m.child("parameters") : m));
        }
        BodyKitDesign kit = BodyKitDesign.NONE;
        if (f.has(BodyKitDesign.PAGE)) {
            Fields k = f.child(BodyKitDesign.PAGE);
            kit = BodyKitDesign.read(k.string("name", BodyKitDesign.PAGE), k.has("parameters") ? k.child("parameters") : k);
        }
        BodyFrontDesign front = BodyFrontDesign.AUTO;
        if (f.has(BodyFrontDesign.PAGE)) {
            Fields k = f.child(BodyFrontDesign.PAGE);
            front = BodyFrontDesign.read(k.string("name", BodyFrontDesign.PAGE), k.has("parameters") ? k.child("parameters") : k);
        }
        return new BodyDesign(f.string("name", "body"), settings, modules, kit, front);
    }
}
