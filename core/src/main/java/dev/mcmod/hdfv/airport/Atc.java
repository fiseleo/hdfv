package dev.mcmod.hdfv.airport;

import dev.mcmod.aerocore.api.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A control tower's traffic rules (0.20), one per tower, stepped by the server about twice a second with what it sees.
 * Pure rules: no game objects; the instructions and warnings it gives are sent to pilots by the game side.
 * <h2>Phases</h2>
 * Each vehicle in the tower's zone has a phase from where it is and what it did (an aircraft or a ground vehicle;
 * only aircraft get instructions): on the ground off the runways ({@link Phase#GROUND}), waiting near the runway in use
 * ({@link Phase#HOLDING}), on a runway slowly ({@link Phase#LINED_UP}), rolling for take-off faster than
 * {@link #ROLL_MPS} ({@link Phase#TAKEOFF_ROLL}), airborne after it ({@link Phase#DEPARTED}), in the air
 * ({@link Phase#AIRBORNE}), in the approach cone of the runway in use ({@link Phase#FINAL}), on a runway after touching
 * down ({@link Phase#ROLLOUT}), off it again ({@link Phase#VACATED}).
 * <h2>Requests</h2>
 * A pilot calls (one key); the tower decides what the call means from the phase: to depart, to land, a helicopter to
 * lift or land where it is, or a repeat of the instruction it already has. Cancel withdraws it. With nobody in the
 * tower (no controller villager on duty and no player at the console) a call gets the ATIS and "self-announce": there
 * are no clearances to break.
 * <h2>The controller's rules (a villager on duty; a player who takes control decides instead)</h2>
 * <ul>
 *   <li>Arrivals are numbered by when they will reach the threshold. Number 1 is cleared to land when it is on final
 *   within {@link #LAND_ETA_S}, the runway is clear of everyone else (any vehicle on it or below {@link #RUNWAY_HEIGHT_M}
 *   over it), no departure is cleared and still on the runway, and the landing before has left the runway.</li>
 *   <li>Departures wait their turn (in the order they called). The first is cleared for take-off when the runway is
 *   clear, no arrival is within {@link #ARRIVAL_GAP_S}, and the departure before is airborne and far or long enough
 *   gone (Separation); told to line up and wait when only the spacing holds it, otherwise to hold.</li>
 *   <li>A landing aircraft within {@link #GO_AROUND_ETA_S} of the threshold with the runway occupied is sent around.</li>
 *   <li>A take-off clearance not used in {@link #TAKEOFF_TIMEOUT_S} is cancelled; a landing clearance when the aircraft
 *   leaves the cone; a taxi instruction after {@link #TAXI_TIMEOUT_S}.</li>
 *   <li>Ground movements are only controlled where the cab sees them.</li>
 * </ul>
 * <h2>Warnings (never stopping anyone)</h2>
 * Under control (manned or a player): a take-off roll without a take-off clearance, a landing without a landing
 * clearance, entering a runway someone else is cleared to use (both are told), taking off or landing on the end not in
 * use, and a crosswind over {@link #CROSSWIND_MPS} given with a clearance.
 */
public final class Atc {
    public enum Phase { GROUND, HOLDING, LINED_UP, TAKEOFF_ROLL, DEPARTED, AIRBORNE, FINAL, ROLLOUT, VACATED }

    public enum Instruction {
        NONE, TAXI_TO_HOLD, HOLD_POSITION, LINE_UP_AND_WAIT, CLEARED_TAKEOFF, CONTINUE_APPROACH, CLEARED_LAND, GO_AROUND, TAXI_TO_APRON,
        TAKEOFF_HERE, LAND_HERE, SELF_ANNOUNCE, CANCELLED
    }

    public enum Warning { TAKEOFF_WITHOUT_CLEARANCE, LANDED_WITHOUT_CLEARANCE, RUNWAY_INCURSION, WRONG_RUNWAY, STRONG_CROSSWIND }

    public enum Request { CALL, CANCEL }

    /** What the pilot's call was understood to ask for. */
    public enum Intent { NONE, DEPART, ARRIVE, DEPART_HERE, LAND_HERE }

    public static final double ROLL_MPS = 15, STOPPED_MPS = 0.5, RUNWAY_MARGIN_M = 5, RUNWAY_HEIGHT_M = 10, HOLD_M = 60,
            LAND_ETA_S = 120, ARRIVAL_GAP_S = 60, LINE_UP_GAP_S = 90, GO_AROUND_ETA_S = 30, TAKEOFF_TIMEOUT_S = 90, TAXI_TIMEOUT_S = 300,
            ABSENT_S = 30, VACATED_S = 30, MIN_SPEED_MPS = 20, FINAL_M = 8_000, CROSSWIND_MPS = 15 * AviationUnits.KNOT_MPS;

    /** A vehicle the tower sees: id, name, kind, wake, centre and velocity, height above the ground, whether on the ground and whether the cab sees it. */
    public record Traffic(UUID id, String name, AircraftKind kind, WakeCategory wake, Vec3d position, Vec3d velocity, double heightM,
                          boolean onGround, boolean seen) {
        double groundSpeed() { return Math.hypot(velocity.x(), velocity.z()); }
    }

    /**
     * One look: the time (s), the runway end in use (null without runways), all runways, whether a controller is on
     * duty (a villager), whether a player has taken control, the wind (toward, m/s) and the traffic in the zone.
     */
    public record Snapshot(double timeS, Runway.Use inUse, List<Runway> runways, boolean manned, boolean human, Vec3d windToward, List<Traffic> traffic) {}

    /** A player's instruction to one aircraft. */
    public record Command(UUID id, Instruction instruction) {}

    /** A flight strip: the aircraft, its phase and instruction, the runway it is for, its number in the landing order, its estimate, warnings. */
    public record Flight(UUID id, String name, AircraftKind kind, Phase phase, Intent intent, Instruction instruction, String runway, int sequence,
                         double etaS, Set<Warning> warnings) {}

    /** Something said to one aircraft this step: an instruction (with runway and number) or a warning. */
    public record Transmission(UUID id, Instruction instruction, String runway, int sequence, Warning warning) {}

    /** Two aircraft whose clearances clash (shown to a player in control; the rules avoid them). */
    public record Conflict(UUID a, UUID b, String reason) {}

    public record Result(List<Flight> flights, List<Transmission> transmissions, List<Conflict> conflicts) {}

    private static final class Strip {
        final UUID id;
        Traffic last;
        Phase phase = Phase.GROUND, before = Phase.GROUND;
        boolean wasOver;
        Intent intent = Intent.NONE;
        Instruction instruction = Instruction.NONE;
        int sequence;
        double issuedS, calledS, seenS, stoppedSinceS, etaS = Double.POSITIVE_INFINITY;
        boolean overRunway;
        final Set<Warning> warnings = EnumSet.noneOf(Warning.class);

        Strip(UUID id) { this.id = id; }
    }

    private record Departure(UUID id, double rollS, WakeCategory wake) {}

    private final Map<UUID, Strip> strips = new LinkedHashMap<>();
    private Departure lastDeparture;

    /** Forgets everything (the tower dissolved, the runway changed shape). */
    public void clear() { strips.clear(); lastDeparture = null; }

    public Result step(Snapshot s, Map<UUID, Request> requests, List<Command> commands) {
        List<Transmission> said = new ArrayList<>();
        boolean control = s.manned() || s.human();
        // see everyone (all of them first: the warnings compare vehicles), forget those gone
        List<Strip> seen = new ArrayList<>();
        for (Traffic t : s.traffic()) {
            Strip st = strips.computeIfAbsent(t.id(), Strip::new);
            st.seenS = s.timeS();
            st.before = st.phase;
            st.wasOver = st.overRunway;
            st.overRunway = over(s.runways(), t);
            st.phase = phase(s, st, t);
            st.etaS = eta(s, t);
            st.last = t;
            if (t.groundSpeed() > STOPPED_MPS) { st.stoppedSinceS = s.timeS(); }
            seen.add(st);
        }
        for (Strip st : seen) {
            if (control && st.last.kind().flies()) { warn(s, st, st.before, said); }
            if (control && !st.wasOver && st.overRunway && st.last.onGround()) { incursion(s, st, said); }
        }
        strips.values().removeIf(st -> s.timeS() - st.seenS > ABSENT_S);
        // the calls
        for (Map.Entry<UUID, Request> r : requests.entrySet()) {
            Strip st = strips.get(r.getKey());
            if (st == null || st.last == null || !st.last.kind().flies()) { continue; }
            call(s, st, r.getValue(), control, said);
        }
        if (s.human()) {
            for (Command c : commands) {
                Strip st = strips.get(c.id());
                if (st != null && st.last != null && st.last.kind().flies()) { issue(s, st, c.instruction(), st.sequence, said); }
            }
        } else if (s.manned()) {
            control(s, said);
        }
        return new Result(flights(s), said, conflicts(s));
    }

    // ---------------------------------------------------------------- phases

    private static boolean over(List<Runway> runways, Traffic t) {
        if (t.heightM() > RUNWAY_HEIGHT_M) { return false; }
        for (Runway r : runways) { if (r.over(t.position().x(), t.position().z(), RUNWAY_MARGIN_M)) { return true; } }
        return false;
    }

    private Phase phase(Snapshot s, Strip st, Traffic t) {
        Phase p = st.phase;
        if (!t.onGround()) {
            if (p == Phase.TAKEOFF_ROLL || p == Phase.DEPARTED) { return Phase.DEPARTED; }
            return inCone(s.inUse(), t) ? Phase.FINAL : Phase.AIRBORNE;
        }
        if (st.overRunway) {
            if (p == Phase.FINAL || p == Phase.AIRBORNE || p == Phase.ROLLOUT || p == Phase.DEPARTED && t.groundSpeed() < ROLL_MPS) { return Phase.ROLLOUT; }
            if (t.groundSpeed() > ROLL_MPS) { return Phase.TAKEOFF_ROLL; }
            return p == Phase.TAKEOFF_ROLL && t.groundSpeed() > 3 ? Phase.TAKEOFF_ROLL : Phase.LINED_UP;
        }
        if (p == Phase.ROLLOUT || p == Phase.VACATED) {
            return s.timeS() - st.stoppedSinceS > VACATED_S && t.groundSpeed() < STOPPED_MPS ? Phase.GROUND : Phase.VACATED;
        }
        if (s.inUse() != null && t.groundSpeed() < 2) {
            Runway r = s.inUse().runway();
            if (r.over(t.position().x(), t.position().z(), HOLD_M)) { return Phase.HOLDING; }
        }
        return Phase.GROUND;
    }

    /** In the approach cone of the runway in use: before its threshold, near its extended centre line, below a steep slope, heading in. */
    static boolean inCone(Runway.Use u, Traffic t) {
        if (u == null) { return false; }
        double along = u.along(t.position().x(), t.position().z());
        if (along > 0 || along < -FINAL_M) { return false; }
        double off = Math.abs(u.runway().offsetFrom(u.end(), t.position().x(), t.position().z()));
        if (off > 150 + 0.15 * -along) { return false; }
        if (t.heightM() > 100 + 0.12 * -along) { return false; }
        Vec3d d = u.direction();
        return t.velocity().x() * d.x() + t.velocity().z() * d.z() > 0;
    }

    /** Seconds to the threshold of the runway in use: straight in on final, else the distance plus a minute to join. */
    private static double eta(Snapshot s, Traffic t) {
        if (s.inUse() == null || t.onGround()) { return Double.POSITIVE_INFINITY; }
        Vec3d th = s.inUse().threshold();
        double d = Math.hypot(t.position().x() - th.x(), t.position().z() - th.z()), v = Math.max(t.groundSpeed(), MIN_SPEED_MPS);
        return inCone(s.inUse(), t) ? d / v : d / v + 60;
    }

    private void warn(Snapshot s, Strip st, Phase before, List<Transmission> said) {
        if (st.phase == Phase.TAKEOFF_ROLL && before != Phase.TAKEOFF_ROLL) {
            if (st.instruction != Instruction.CLEARED_TAKEOFF && st.instruction != Instruction.TAKEOFF_HERE) { warning(st, Warning.TAKEOFF_WITHOUT_CLEARANCE, said); }
            if (wrongEnd(s, st.last)) { warning(st, Warning.WRONG_RUNWAY, said); }
            lastDeparture = new Departure(st.id, s.timeS(), st.last.wake());
        }
        if (st.phase == Phase.ROLLOUT && (before == Phase.FINAL || before == Phase.AIRBORNE)) {
            if (st.instruction != Instruction.CLEARED_LAND && st.instruction != Instruction.LAND_HERE) { warning(st, Warning.LANDED_WITHOUT_CLEARANCE, said); }
            if (wrongEnd(s, st.last)) { warning(st, Warning.WRONG_RUNWAY, said); }
        }
    }

    /** Moving along a runway against the end in use (or on another runway). */
    private static boolean wrongEnd(Snapshot s, Traffic t) {
        if (s.inUse() == null) { return false; }
        Runway r = s.inUse().runway();
        if (!r.over(t.position().x(), t.position().z(), RUNWAY_MARGIN_M)) { return true; }
        Vec3d d = s.inUse().direction();
        return t.velocity().x() * d.x() + t.velocity().z() * d.z() < 0;
    }

    private void incursion(Snapshot s, Strip entering, List<Transmission> said) {
        if (entering.instruction == Instruction.LINE_UP_AND_WAIT || entering.instruction == Instruction.CLEARED_TAKEOFF) { return; }
        for (Strip other : strips.values()) {
            if (other == entering || other.last == null) { continue; }
            boolean takingOff = other.instruction == Instruction.CLEARED_TAKEOFF && (other.phase == Phase.LINED_UP || other.phase == Phase.TAKEOFF_ROLL);
            boolean landing = other.instruction == Instruction.CLEARED_LAND && other.etaS < ARRIVAL_GAP_S;
            if (takingOff || landing) {
                warning(entering, Warning.RUNWAY_INCURSION, said);
                warning(other, Warning.RUNWAY_INCURSION, said);
                return;
            }
        }
    }

    private static void warning(Strip st, Warning w, List<Transmission> said) {
        st.warnings.add(w);
        said.add(new Transmission(st.id, null, null, 0, w));
    }

    // ---------------------------------------------------------------- calls and instructions

    private void call(Snapshot s, Strip st, Request r, boolean control, List<Transmission> said) {
        if (r == Request.CANCEL) {
            st.intent = Intent.NONE;
            st.sequence = 0;
            issue(s, st, Instruction.CANCELLED, 0, said);
            st.instruction = Instruction.NONE;
            return;
        }
        if (!control) { said.add(new Transmission(st.id, Instruction.SELF_ANNOUNCE, designator(s), 0, null)); return; }
        if (st.intent != Intent.NONE && st.instruction != Instruction.NONE) {
            said.add(new Transmission(st.id, st.instruction, designator(s), st.sequence, null));   // say it again
            return;
        }
        boolean heli = st.last.kind() == AircraftKind.ROTORCRAFT;
        st.calledS = s.timeS();
        st.intent = switch (st.phase) {
            case GROUND, HOLDING -> heli && !st.overRunway ? Intent.DEPART_HERE : Intent.DEPART;
            case LINED_UP -> Intent.DEPART;
            case AIRBORNE -> heli ? Intent.LAND_HERE : Intent.ARRIVE;
            case FINAL -> Intent.ARRIVE;
            case ROLLOUT, VACATED -> Intent.NONE;
            case TAKEOFF_ROLL, DEPARTED -> Intent.NONE;
        };
        if (st.phase == Phase.ROLLOUT || st.phase == Phase.VACATED) { issue(s, st, Instruction.TAXI_TO_APRON, 0, said); }
        else if (st.intent == Intent.NONE) { said.add(new Transmission(st.id, Instruction.NONE, designator(s), 0, null)); }
    }

    private void issue(Snapshot s, Strip st, Instruction i, int sequence, List<Transmission> said) {
        if (st.instruction == i && st.sequence == sequence) { return; }
        st.instruction = i;
        st.sequence = sequence;
        st.issuedS = s.timeS();
        said.add(new Transmission(st.id, i, designator(s), sequence, null));
        if ((i == Instruction.CLEARED_TAKEOFF || i == Instruction.CLEARED_LAND) && s.inUse() != null
                && Math.abs(RunwayChoice.crosswind(s.inUse(), s.windToward())) > CROSSWIND_MPS) {
            warning(st, Warning.STRONG_CROSSWIND, said);
        }
    }

    private static String designator(Snapshot s) { return s.inUse() == null ? "" : s.inUse().designator(); }

    /** Anyone but this one on (or low over) the runway in use. */
    private boolean occupiedExcept(Snapshot s, Strip me) {
        if (s.inUse() == null) { return false; }
        Runway r = s.inUse().runway();
        for (Strip o : strips.values()) {
            if (o == me || o.last == null || o.seenS != s.timeS()) { continue; }
            Traffic t = o.last;
            if (t.heightM() <= RUNWAY_HEIGHT_M && r.over(t.position().x(), t.position().z(), RUNWAY_MARGIN_M)) { return true; }
        }
        return false;
    }

    /** The soonest arrival on final (called or not), seconds. */
    private double nextArrival(Strip except) {
        double eta = Double.POSITIVE_INFINITY;
        for (Strip o : strips.values()) {
            if (o != except && o.phase == Phase.FINAL) { eta = Math.min(eta, o.etaS); }
        }
        return eta;
    }

    private boolean anyone(Phase phase) {
        for (Strip o : strips.values()) { if (o.phase == phase) { return true; } }
        return false;
    }

    private boolean departureCleared(Strip except) {
        for (Strip o : strips.values()) {
            if (o != except && o.instruction == Instruction.CLEARED_TAKEOFF && (o.phase == Phase.LINED_UP || o.phase == Phase.TAKEOFF_ROLL || o.phase == Phase.HOLDING)) { return true; }
        }
        return false;
    }

    private boolean spaced(Snapshot s, Strip next) {
        Departure d = lastDeparture;
        if (d == null || d.id().equals(next.id)) { return true; }
        Strip before = strips.get(d.id());
        if (before != null && before.last != null && before.last.onGround() && before.phase == Phase.TAKEOFF_ROLL) { return false; }
        if (s.timeS() - d.rollS() >= Separation.departureInterval(d.wake(), next.last.wake())) { return true; }
        if (Separation.wakeHolds(d.wake(), next.last.wake()) || before == null || before.last == null) { return false; }
        double dx = before.last.position().x() - next.last.position().x(), dz = before.last.position().z() - next.last.position().z();
        return Math.hypot(dx, dz) >= Separation.DEPARTURE_DISTANCE_M;
    }

    /** The controller villager's rules. */
    private void control(Snapshot s, List<Transmission> said) {
        // timeouts first
        for (Strip st : strips.values()) {
            if (st.instruction == Instruction.CLEARED_TAKEOFF && s.timeS() - st.issuedS > TAKEOFF_TIMEOUT_S
                    && st.phase != Phase.TAKEOFF_ROLL && st.phase != Phase.DEPARTED) {
                st.intent = Intent.NONE;   // it calls again when ready
                issue(s, st, Instruction.HOLD_POSITION, 0, said);
            }
            if (st.instruction == Instruction.CLEARED_LAND && st.phase == Phase.AIRBORNE) { issue(s, st, Instruction.CONTINUE_APPROACH, st.sequence, said); }
            if (st.instruction == Instruction.TAXI_TO_HOLD && s.timeS() - st.issuedS > TAXI_TIMEOUT_S) { st.intent = Intent.NONE; issue(s, st, Instruction.NONE, 0, said); }
            boolean gone = st.phase == Phase.DEPARTED || st.intent == Intent.DEPART_HERE && st.phase == Phase.AIRBORNE
                    || st.intent == Intent.LAND_HERE && (st.phase == Phase.GROUND || st.phase == Phase.HOLDING);
            if (gone && st.intent != Intent.NONE) { st.intent = Intent.NONE; st.instruction = Instruction.NONE; st.sequence = 0; }
            if (st.phase == Phase.VACATED && st.intent == Intent.ARRIVE) { st.intent = Intent.NONE; issue(s, st, Instruction.TAXI_TO_APRON, 0, said); }
        }
        // arrivals in order of their estimates
        List<Strip> arrivals = new ArrayList<>();
        for (Strip st : strips.values()) {
            if (st.intent == Intent.ARRIVE && (st.phase == Phase.FINAL || st.phase == Phase.AIRBORNE)) { arrivals.add(st); }
        }
        arrivals.sort(Comparator.comparingDouble((Strip st) -> st.instruction == Instruction.GO_AROUND ? Double.POSITIVE_INFINITY : st.etaS));
        for (int i = 0; i < arrivals.size(); i++) {
            Strip st = arrivals.get(i);
            int n = i + 1;
            if (st.instruction == Instruction.CLEARED_LAND) {
                if (st.etaS < GO_AROUND_ETA_S && occupiedExcept(s, st)) { issue(s, st, Instruction.GO_AROUND, 0, said); st.calledS = s.timeS(); continue; }
                if (n == 1) { continue; }
                // another arrival will be there first: only number 1 holds a landing clearance
            }
            if (st.instruction == Instruction.GO_AROUND && st.phase == Phase.FINAL && s.timeS() - st.issuedS < 30) { continue; }
            boolean clear = n == 1 && st.phase == Phase.FINAL && st.etaS <= LAND_ETA_S && !occupiedExcept(s, st)
                    && !departureCleared(st) && !anyone(Phase.ROLLOUT);
            issue(s, st, clear ? Instruction.CLEARED_LAND : Instruction.CONTINUE_APPROACH, clear ? 0 : n, said);
        }
        // departures in the order they called
        List<Strip> departures = new ArrayList<>();
        for (Strip st : strips.values()) {
            if ((st.intent == Intent.DEPART || st.intent == Intent.DEPART_HERE)
                    && (st.phase == Phase.GROUND || st.phase == Phase.HOLDING || st.phase == Phase.LINED_UP)) { departures.add(st); }
            if (st.intent == Intent.LAND_HERE && st.phase != Phase.GROUND) { issue(s, st, Instruction.LAND_HERE, 0, said); }
        }
        departures.sort(Comparator.comparingDouble((Strip st) -> st.calledS));
        boolean first = true;
        for (Strip st : departures) {
            if (!st.last.seen()) { continue; }   // the cab cannot see it: no ground control
            if (st.intent == Intent.DEPART_HERE) {
                if (nextArrival(st) > GO_AROUND_ETA_S) { issue(s, st, Instruction.TAKEOFF_HERE, 0, said); }
                continue;
            }
            if (st.phase == Phase.GROUND) {
                if (st.instruction == Instruction.NONE) { issue(s, st, Instruction.TAXI_TO_HOLD, 0, said); }
                continue;
            }
            if (st.instruction == Instruction.CLEARED_TAKEOFF) { first = false; continue; }
            if (!first) { issue(s, st, st.phase == Phase.LINED_UP ? Instruction.LINE_UP_AND_WAIT : Instruction.HOLD_POSITION, 0, said); continue; }
            first = false;
            boolean free = !occupiedExcept(s, st) && !anyone(Phase.ROLLOUT) && !departureCleared(st);
            double arrival = nextArrival(st);
            if (free && arrival > ARRIVAL_GAP_S && spaced(s, st)) { issue(s, st, Instruction.CLEARED_TAKEOFF, 0, said); }
            else if (free && arrival > LINE_UP_GAP_S) { issue(s, st, Instruction.LINE_UP_AND_WAIT, 0, said); }
            else { issue(s, st, Instruction.HOLD_POSITION, 0, said); }
        }
    }

    // ---------------------------------------------------------------- output

    private List<Flight> flights(Snapshot s) {
        List<Flight> out = new ArrayList<>();
        for (Strip st : strips.values()) {
            if (st.last == null) { continue; }
            out.add(new Flight(st.id, st.last.name(), st.last.kind(), st.phase, st.intent, st.instruction, designator(s), st.sequence, st.etaS, Set.copyOf(st.warnings)));
        }
        return out;
    }

    /** Clashing clearances: two take-offs, a landing onto an occupied runway or into a take-off, a take-off into a close arrival. */
    private List<Conflict> conflicts(Snapshot s) {
        List<Conflict> out = new ArrayList<>();
        List<Strip> all = new ArrayList<>(strips.values());
        for (Strip a : all) {
            if (a.instruction == Instruction.CLEARED_LAND) {
                for (Strip b : all) {
                    if (b == a || b.last == null) { continue; }
                    boolean onRunway = b.overRunway && b.last.onGround();
                    if (onRunway || b.instruction == Instruction.CLEARED_TAKEOFF) { out.add(new Conflict(a.id, b.id, onRunway ? "occupied" : "takeoff")); }
                }
            }
            if (a.instruction == Instruction.CLEARED_TAKEOFF) {
                for (Strip b : all) {
                    if (b == a) { continue; }
                    if (b.instruction == Instruction.CLEARED_TAKEOFF && a.id.compareTo(b.id) < 0) { out.add(new Conflict(a.id, b.id, "two_takeoffs")); }
                    if (b.phase == Phase.FINAL && b.etaS < ARRIVAL_GAP_S) { out.add(new Conflict(a.id, b.id, "arrival")); }
                }
            }
        }
        return out;
    }
}
