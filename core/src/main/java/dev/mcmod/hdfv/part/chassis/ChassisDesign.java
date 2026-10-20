package dev.mcmod.hdfv.part.chassis;

import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.frame.FrameAnalysis;
import dev.mcmod.aerocore.api.frame.FrameDisconnectedException;
import dev.mcmod.aerocore.api.vehicle.DesignException;
import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;
import dev.mcmod.aerocore.api.vehicle.FrameTwist;
import dev.mcmod.aerocore.api.vehicle.MassProperties;
import dev.mcmod.aerocore.api.vehicle.Mount;
import dev.mcmod.aerocore.api.vehicle.Part;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.part.FramePart;
import dev.mcmod.hdfv.design.ListedDesign;
import dev.mcmod.hdfv.look.ChassisLayout;
import dev.mcmod.hdfv.part.engine.EngineComponent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A chassis (0.15; the user 2026-10-17: 「底盤也要高度自由化」): a frame the player designs (ChassisSettingsDesign: a
 * ladder, a backbone or a tube frame) and stations along it (ChassisStationDesign), on which the axles ("axle_1" the
 * frontmost...), the engine ("engine_bay") and a body ("body": its rear end on the rails' top at the frame's rear end)
 * are mounted. A unibody car has no chassis part: its body is its frame. 0.18: a cab-over cab ("cab": the rails' top at
 * the frame's front end, where the cab's front stands), a tractor's fifth wheel ("fifth_wheel"), a semi-trailer's
 * kingpin ("kingpin") and landing legs ("legs_1", "legs_2"...). A kingpin holds the frame up like a wheel in the
 * standard cases (FrameAnalysis's pins, on the tractor's springs); a fifth wheel carries half the frame's load (the
 * trailer's kingpin load).
 *
 * <p>Its structure is a beam model (ChassisLayout, AeroCore's api.frame): its mass (the members, plus 8 % for gussets,
 * brackets and welds), its twist between the axle stations (FrameTwist: the physics twists it, FrameFlex) and bending,
 * and the standard cases (a 2.5 g bump, a ditch, 0.8 g braking and cornering) at its design gross mass, the load spread
 * over the stations (a fifth at the engine's), the wheels on springs of a 1.4 Hz ride: no member may reach its yield
 * at 1.5 times those (INVALID); twist outside 1 to 60 kN m per degree is a warning (trucks 2-10, cars 10-40).
 * The analysis is kept per design (a room compiles its parts at every edit).
 */
public record ChassisDesign(String name, ChassisSettingsDesign frame, List<ChassisStationDesign> stations) implements PartDesign, ListedDesign,
        dev.mcmod.hdfv.design.RecordEditor.ShownOnPage {
    public static final String TYPE = "hdfv:chassis";
    public static final String FRAME = "frame", STATION = "station";
    /** Gussets, brackets and welds on top of the members, as a share of their mass (estimate). */
    public static final double FITTINGS = 0.08;
    /** The standard cases' margin to yield. */
    public static final double ULTIMATE = 1.5;
    /** The ride frequency the standard cases' wheel springs give, Hz. */
    public static final double RIDE_HZ = 1.4;
    /** The most axle stations (the physics twists between at most eight). */
    public static final int MAX_AXLES = 8;
    /** 0.18: how far outside the rails a landing leg stands, m. */
    public static final double LEG_OUTSIDE_M = 0.12;

    public ChassisDesign {
        Objects.requireNonNull(name, "name"); Objects.requireNonNull(frame, "frame");
        stations = List.copyOf(stations);
    }

    @Override public String type() { return TYPE; }

    // ---------------------------------------------------------------- pages and editing (ListedDesign)

    public Map<String, EngineComponent> components() {
        Map<String, EngineComponent> m = new LinkedHashMap<>();
        m.put(FRAME, frame);
        for (int i = 0; i < stations.size(); i++) { m.put(listPage(i), stations.get(i)); }
        return m;
    }

    @Override public String listName() { return STATION; }
    @Override public int items() { return stations.size(); }
    @Override public int minItems() { return 2; }
    @Override public List<ChassisStationDesign.Kind> itemKinds() { return List.of(ChassisStationDesign.Kind.values()); }
    @Override public Enum<?> itemKind(int index) { return stations.get(index).kind(); }

    public ChassisDesign withFrame(ChassisSettingsDesign f) { return new ChassisDesign(name, f, stations); }

    public ChassisDesign withStation(int index, ChassisStationDesign s) {
        List<ChassisStationDesign> list = new ArrayList<>(stations);
        list.set(index, s);
        return new ChassisDesign(name, frame, list);
    }

    /** A new station of a kind after the one at index (-1: first): halfway to the next one along the frame, or 0.5 m beyond it. */
    @Override public ChassisDesign added(int after, Enum<?> kind) {
        List<ChassisStationDesign> list = new ArrayList<>(stations);
        double x;
        if (after < 0 || after >= list.size()) { x = list.isEmpty() ? 0 : Math.max(list.get(0).xM() - 0.5, 0); after = Math.min(after, list.size() - 1); }
        else {
            double here = list.get(after).xM(), next = Double.POSITIVE_INFINITY;
            for (ChassisStationDesign s : list) { if (s.xM() > here + 1e-9) { next = Math.min(next, s.xM()); } }
            x = Double.isFinite(next) ? 0.5 * (here + next) : Math.min(here + 0.5, frame.lengthM());
        }
        list.add(after + 1, ChassisStationDesign.defaults((ChassisStationDesign.Kind) kind, x));
        return new ChassisDesign(name, frame, list);
    }

    @Override public ChassisDesign removed(int index) {
        if (stations.size() <= minItems()) { return this; }
        List<ChassisStationDesign> list = new ArrayList<>(stations);
        list.remove(index);
        return new ChassisDesign(name, frame, list);
    }

    @Override public ChassisDesign moved(int index, int by) {
        int to = index + by;
        if (to < 0 || to >= stations.size()) { return this; }
        List<ChassisStationDesign> list = new ArrayList<>(stations);
        list.add(to, list.remove(index));
        return new ChassisDesign(name, frame, list);
    }

    /** A copy 0.3 m ahead of it (within the frame). */
    @Override public ChassisDesign duplicated(int index) {
        List<ChassisStationDesign> list = new ArrayList<>(stations);
        ChassisStationDesign s = stations.get(index);
        list.add(index + 1, s.at(Math.min(s.xM() + 0.3, frame.lengthM())));
        return new ChassisDesign(name, frame, list);
    }

    @Override public ChassisDesign withPage(String page, Record value) {
        if (page.equals(FRAME)) { return withFrame((ChassisSettingsDesign) value); }
        int i = listIndex(page);
        if (i < 0 || i >= stations.size()) { throw new IllegalArgumentException("No page " + page); }
        return withStation(i, (ChassisStationDesign) value);
    }

    // ---------------------------------------------------------------- what it carries

    /** The axle stations, frontmost first (their mounts "axle_1", "axle_2"...). */
    public List<Integer> axleStations() {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < stations.size(); i++) { if (stations.get(i).kind() == ChassisStationDesign.Kind.AXLE) { out.add(i); } }
        out.sort((a, b) -> Double.compare(stations.get(b).xM(), stations.get(a).xM()));
        return out;
    }

    /** The rails' height at x above the rails' bottom (m): their own plus a ladder's kick-up. */
    public double railTopAt(double x) {
        double h = frame.kind() == ChassisSettingsDesign.Kind.SPACE_FRAME ? frame.tubeDiameterM() : frame.railHeightM();
        return h + ChassisLayout.lift(frame, x);
    }

    /** Mount points (part frame): each axle station's "axle_N", the first engine mount's "engine_bay", "body". */
    public Map<String, Mount> mountPoints() {
        Map<String, Mount> out = new LinkedHashMap<>();
        List<Integer> axles = axleStations();
        for (int k = 0; k < axles.size(); k++) {
            ChassisStationDesign s = stations.get(axles.get(k));
            out.put("axle_" + (k + 1), Mount.at(new Vec3d(s.xM(), ChassisLayout.lift(frame, s.xM()) + s.heightM(), 0)));
        }
        ChassisStationDesign engine = null;
        for (ChassisStationDesign s : stations) {
            if (s.kind() == ChassisStationDesign.Kind.ENGINE_MOUNT && (engine == null || s.xM() > engine.xM())) { engine = s; }
        }
        if (engine != null) { out.put("engine_bay", Mount.at(new Vec3d(engine.xM(), railTopAt(engine.xM()) + engine.heightM(), 0))); }
        out.put("body", Mount.at(new Vec3d(0, railTopAt(Math.min(frame.lengthM(), Math.max(0, frame.kickUpX()))), 0)));
        // 0.18: trucks
        out.put("cab", Mount.at(new Vec3d(frame.lengthM(), railTopAt(frame.lengthM()), 0)));
        int legs = 0;
        for (int i : ChassisLayout.byX(this).reversed()) {
            ChassisStationDesign s = stations.get(i);
            double x = s.xM(), bottom = ChassisLayout.lift(frame, x);
            switch (s.kind()) {
                case FIFTH_WHEEL -> out.putIfAbsent("fifth_wheel", Mount.at(new Vec3d(x, railTopAt(x) + s.heightM(), 0)));
                case KINGPIN -> out.putIfAbsent("kingpin", Mount.at(new Vec3d(x, bottom + s.heightM(), 0)));
                case LANDING_LEGS -> {
                    double half = frame.spacingAt(x) / 2 + LEG_OUTSIDE_M;
                    out.put("legs_" + (++legs), Mount.at(new Vec3d(x, bottom + s.heightM(), -half)));
                    out.put("legs_" + (++legs), Mount.at(new Vec3d(x, bottom + s.heightM(), half)));
                }
                default -> { }
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- structure

    /**
     * The structure's figures: the layout, mass (kg, fittings in), the twist stiffness between the axle stations (rear
     * first; null with fewer than two), between the end axles (N m/deg), the standard cases' greatest usage at 1.5 times
     * and the member; or why there is none (the frame in pieces). And (for the bench page and the model) each member's
     * greatest usage at 1.5 times over the cases, and the bending stiffness (N/m: a load at the station nearest midway
     * between the end axles over its deflection there, the frame on those axles; NaN with no station between them).
     */
    public record Analysis(ChassisLayout layout, double massKg, double[] twistX, double[][] twist, double endTwistNmPerDeg, double usage, int worstMember,
                           String failure, double[] memberUsage, double bendingNPerM) {}

    private static final Map<ChassisDesign, Analysis> ANALYSES = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<ChassisDesign, Analysis> eldest) { return size() > 32; }
    });

    /** The structure (worked out once per design). */
    public Analysis analysis() { return ANALYSES.computeIfAbsent(this, ChassisDesign::analyse); }

    private static Analysis analyse(ChassisDesign d) {
        ChassisLayout layout = ChassisLayout.of(d);
        double mass = FrameAnalysis.massKg(layout.frame) * (1 + FITTINGS);
        List<Integer> axles = new ArrayList<>(d.axleStations());
        Collections.reverse(axles);
        double[] tx = null;
        double[][] k = null;
        double perDeg = Double.NaN;
        try {
            if (axles.size() >= 2) {
                List<FrameAnalysis.Station> st = new ArrayList<>();
                for (int a : axles) { st.add(layout.twistStation(a)); }
                k = clean(FrameAnalysis.twistStiffness(layout.frame, st));
                tx = new double[axles.size()];
                for (int i = 0; i < tx.length; i++) { tx[i] = st.get(i).xM(); }
                perDeg = Math.toRadians(1) / new FrameTwist(tx, k).endToEndFlexibility();
            }
            double usage = 0;
            int worst = -1;
            double[] members = new double[layout.frame.memberCount()];
            double bending = Double.NaN;
            if (axles.size() >= 2) {
                int rear = axles.getFirst(), front = axles.getLast();
                double mid = 0.5 * (d.stations().get(rear).xM() + d.stations().get(front).xM()), best = Double.POSITIVE_INFINITY;
                int loaded = -1;
                for (int i = 0; i < d.stations().size(); i++) {
                    double x = d.stations().get(i).xM();
                    if (i == rear || i == front || x <= d.stations().get(rear).xM() + 0.05 || x >= d.stations().get(front).xM() - 0.05) { continue; }
                    if (Math.abs(x - mid) < best) { best = Math.abs(x - mid); loaded = i; }
                }
                if (loaded >= 0) {
                    try { bending = FrameAnalysis.bendingStiffness(layout.frame, layout.twistStation(rear), layout.twistStation(front), layout.twistStation(loaded)); }
                    catch (RuntimeException e) { bending = Double.NaN; } // a figure for the bench page only
                }
            }
            if (axles.size() >= 2) {
                List<FrameAnalysis.Mass> carried = new ArrayList<>();
                List<FrameAnalysis.Wheel> wheels = new ArrayList<>();
                double load = Math.max(d.frame().designGrossKg() - mass, 0);
                int nodes = 2 * d.stations().size();
                List<Integer> engines = new ArrayList<>();
                for (int i = 0; i < d.stations().size(); i++) { if (d.stations().get(i).kind() == ChassisStationDesign.Kind.ENGINE_MOUNT) { engines.add(i); } }
                double engine = engines.isEmpty() ? 0 : 0.2 * load;
                // 0.18: a fifth wheel's half of the load at its station; a kingpin holds the frame up as a pin on the tractor's
                // springs (twice a wheel's rate a node: its share of the load is about twice a wheel's)
                List<Integer> fifth = new ArrayList<>(), kingpins = new ArrayList<>();
                for (int i = 0; i < d.stations().size(); i++) {
                    if (d.stations().get(i).kind() == ChassisStationDesign.Kind.FIFTH_WHEEL) { fifth.add(i); }
                    if (d.stations().get(i).kind() == ChassisStationDesign.Kind.KINGPIN) { kingpins.add(i); }
                }
                double coupled = fifth.isEmpty() ? 0 : 0.5 * (load - engine);
                for (int i = 0; i < d.stations().size(); i++) {
                    double share = (load - engine - coupled) / nodes + (engines.contains(i) ? engine / (2 * engines.size()) : 0)
                            + (fifth.contains(i) ? coupled / (2 * fifth.size()) : 0);
                    for (int node : layout.stationNodes[i]) { carried.add(new FrameAnalysis.Mass(node, share)); }
                }
                double corner = d.frame().designGrossKg() / (2 * axles.size()), rate = corner * Math.pow(2 * Math.PI * RIDE_HZ, 2);
                for (int a : axles) { for (int node : layout.stationNodes[a]) { wheels.add(new FrameAnalysis.Wheel(node, rate)); } }
                List<FrameAnalysis.Wheel> pins = new ArrayList<>();
                for (int pin : kingpins) { for (int node : layout.stationNodes[pin]) { pins.add(new FrameAnalysis.Wheel(node, 2 * rate)); } }
                FrameAnalysis.StandardCases cases = pins.isEmpty() ? FrameAnalysis.standardCases(layout.frame, carried, wheels, 0.45, 9.80665)
                        : FrameAnalysis.standardCases(layout.frame, carried, wheels, pins, 0.45, 9.80665);
                for (FrameAnalysis.LoadResult r : new FrameAnalysis.LoadResult[]{cases.bump(), cases.ditch(), cases.braking(), cases.cornering()}) {
                    if (r == null) { continue; }
                    if (r.maxUsage() > usage) { usage = r.maxUsage(); worst = r.worstMember(); }
                    for (FrameAnalysis.MemberResult m : r.members()) { members[m.member()] = Math.max(members[m.member()], ULTIMATE * m.usage()); }
                }
            }
            return new Analysis(layout, mass, tx, k, perDeg, ULTIMATE * usage, worst, null, members, bending);
        } catch (FrameDisconnectedException e) {
            return new Analysis(layout, mass, null, null, Double.NaN, Double.NaN, -1, e.getMessage(), new double[layout.frame.memberCount()], Double.NaN);
        } catch (IllegalArgumentException | IllegalStateException e) {
            // 0.18: also a frame that cannot stand on its wheels at all (a trailer's frame without its kingpin: no ditch stance)
            return new Analysis(layout, mass, null, null, Double.NaN, Double.NaN, -1, e.getMessage(), new double[layout.frame.memberCount()], Double.NaN);
        }
    }

    /** The twist matrix made exactly symmetric with rows summing to zero (the solve leaves round-off in both). */
    static double[][] clean(double[][] k) {
        int n = k.length;
        double[][] out = new double[n][n];
        for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) { out[i][j] = 0.5 * (k[i][j] + k[j][i]); }
        for (int i = 0; i < n; i++) {
            double off = 0;
            for (int j = 0; j < n; j++) { if (j != i) { off += out[i][j]; } }
            out[i][i] = -off;
        }
        return out;
    }

    /**
     * 0.15: a station's parameters by the frame's kind: a tube frame's bay (its height, top width, open top) only on a
     * tube frame, whose rings are its tubes (no crossmember section); braces between stations on a ladder or a tube
     * frame (a backbone's outriggers are not braced).
     */
    @Override public boolean shows(String page, String parameter) {
        if (!page.startsWith(STATION)) { return true; }
        boolean tubes = frame.kind() == ChassisSettingsDesign.Kind.SPACE_FRAME;
        return switch (parameter) {
            case "bayHeightM", "bayTopWidthM", "openTop" -> tubes;
            case "crossSection", "crossHeightM", "crossWidthM", "crossWallM" -> !tubes;
            case "brace" -> frame.kind() != ChassisSettingsDesign.Kind.BACKBONE;
            default -> true;
        };
    }

    /** The parameter a member's size is set by ("frame.railWallM", "station3.crossWallM", "frame.tubeWallM"). */
    public String parameterOf(int member) {
        ChassisLayout.Member m = analysis().layout().members.get(member);
        if (frame.kind() == ChassisSettingsDesign.Kind.SPACE_FRAME) { return FRAME + ".tubeWallM"; }
        return m.role() == ChassisLayout.Role.RAIL ? FRAME + ".railWallM" : listPage(m.station()) + ".crossWallM";
    }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        if (stations.size() < 2) { issues.add(DesignIssue.invalid(STATION, "A chassis needs two stations or more")); return issues; }
        for (Map.Entry<String, EngineComponent> e : components().entrySet()) {
            for (DesignIssue i : e.getValue().check()) { issues.add(i.under(e.getKey())); }
        }
        for (int i = 0; i < stations.size(); i++) {
            double x = stations.get(i).xM();
            if (!(x >= 0 && x <= frame.lengthM())) { issues.add(DesignIssue.invalid(listPage(i) + ".xM", "The station is on the frame: 0 to its length")); }
        }
        int axles = axleStations().size();
        if (axles < 2) { issues.add(DesignIssue.invalid(STATION, "A chassis carries two axles or more")); }
        if (axles > MAX_AXLES) { issues.add(DesignIssue.invalid(STATION, "A chassis carries at most " + MAX_AXLES + " axles")); }
        for (int i = 0; i < stations.size(); i++) {
            for (int j = 0; j < i; j++) {
                if (Math.abs(stations.get(i).xM() - stations.get(j).xM()) < 0.05 && (stations.get(i).kind() == ChassisStationDesign.Kind.AXLE
                        || stations.get(j).kind() == ChassisStationDesign.Kind.AXLE)) {
                    issues.add(DesignIssue.invalid(listPage(i) + ".xM", "Stations with an axle stand 5 cm apart or more"));
                }
            }
        }
        if (issues.stream().anyMatch(i -> i.kind() == DesignIssue.Kind.INVALID)) { return issues; }
        Analysis a = analysis();
        if (a.failure() != null) { issues.add(DesignIssue.invalid(FRAME, "The frame does not hold together: " + a.failure())); return issues; }
        if (a.usage() > 1) {
            issues.add(DesignIssue.invalid(parameterOf(a.worstMember()), String.format(java.util.Locale.ROOT,
                    "Under the standard cases at 1.5 times (a 2.5 g bump, a ditch, 0.8 g braking and cornering at %.0f kg) a member reaches %.0f %% of the %s's yield",
                    frame.designGrossKg(), 100 * a.usage(), frame.material().name())));
        }
        if (Double.isFinite(a.endTwistNmPerDeg())) {
            DesignIssue.range(issues, frame.kind() == ChassisSettingsDesign.Kind.SPACE_FRAME ? FRAME + ".tubeDiameterM" : FRAME + ".railHeightM",
                    a.endTwistNmPerDeg() / 1000, 1, 60, "frames' twist stiffness between the axles, kN m per degree (trucks 2-10, cars 10-40)");
        }
        return issues;
    }

    /** The frame's mass, kg (members and fittings). */
    public double massKg() { return analysis().massKg(); }

    /** Mass properties: the members' mass at the frame's nodes (each member half to each end), fittings in. */
    public MassProperties mass() {
        ChassisLayout layout = analysis().layout();
        double[] node = FrameAnalysis.nodeMassesKg(layout.frame);
        List<MassProperties> points = new ArrayList<>();
        for (int i = 0; i < node.length; i++) { if (node[i] > 0) { points.add(MassProperties.point(node[i] * (1 + FITTINGS), layout.frame.position(i))); } }
        return MassProperties.combine(points);
    }

    @Override public Part compile() {
        DesignException.throwIfInvalid(check());
        Analysis a = analysis();
        FrameTwist twist = a.twist() == null ? null : new FrameTwist(a.twistX(), a.twist());
        List<DesignIssue> warnings = check().stream().filter(i -> i.kind() == DesignIssue.Kind.EXTRAPOLATION).toList();
        return new FramePart(this, mass(), mountPoints(), List.of(), twist, warnings);
    }

    @Override public Map<String, Object> parameters() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(FRAME, frame.toJson());
        List<Object> list = new ArrayList<>();
        for (ChassisStationDesign s : stations) { list.add(s.toJson()); }
        m.put("stations", list);
        return m;
    }

    public static PartDesign read(Fields f) {
        Fields s = f.child(FRAME);
        ChassisSettingsDesign frame = ChassisSettingsDesign.read(s.string("name", FRAME), s.has("parameters") ? s.child("parameters") : s);
        List<ChassisStationDesign> stations = new ArrayList<>();
        for (Fields m : f.objects("stations")) { stations.add(ChassisStationDesign.read(m.string("name", STATION), m.has("parameters") ? m.child("parameters") : m)); }
        return new ChassisDesign(f.string("name", "chassis"), frame, stations);
    }

    /** A new chassis of a kind (SampleChassis's of each kind). */
    public static ChassisDesign fresh(ChassisSettingsDesign.Kind kind) {
        return switch (kind) {
            case LADDER -> SampleChassis.ladderPickup();
            case BACKBONE -> SampleChassis.backbone();
            case SPACE_FRAME -> SampleChassis.tubeFrame();
        };
    }
}
