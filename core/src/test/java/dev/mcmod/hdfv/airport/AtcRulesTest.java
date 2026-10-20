package dev.mcmod.hdfv.airport;

import dev.mcmod.aerocore.api.Vec3d;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** The tower's rules (Atc) through scenarios on an 800 m runway along X, landings and take-offs toward +X (runway 09). */
class AtcRulesTest {
    private static final Runway RUNWAY = new Runway(Runway.Axis.X, 0, 0, 800, 64, 23, "");
    private static final Runway.Use IN_USE = new Runway.Use(RUNWAY, Runway.End.LOW);
    private static final UUID A = new UUID(0, 1), B = new UUID(0, 2), C = new UUID(0, 3), CAR = new UUID(0, 9);

    private final Atc atc = new Atc();
    private final Map<UUID, Atc.Traffic> traffic = new HashMap<>();
    private double time;
    private boolean manned = true, human;

    private static Atc.Traffic plane(UUID id, double x, double z, double vx, double height, boolean ground) {
        return new Atc.Traffic(id, "plane " + id.getLeastSignificantBits(), AircraftKind.FIXED_WING, WakeCategory.LIGHT,
                new Vec3d(x, 64 + height, z), new Vec3d(vx, 0, 0), height, ground, true);
    }

    private void put(Atc.Traffic t) { traffic.put(t.id(), t); }

    private Atc.Result step(Map<UUID, Atc.Request> requests, List<Atc.Command> commands) {
        return atc.step(new Atc.Snapshot(time, IN_USE, List.of(RUNWAY), manned, human, new Vec3d(-3, 0, 0), new ArrayList<>(traffic.values())), requests, commands);
    }

    private Atc.Result step() { return step(Map.of(), List.of()); }

    private Atc.Result call(UUID id) { return step(Map.of(id, Atc.Request.CALL), List.of()); }

    private static Atc.Flight flight(Atc.Result r, UUID id) { return r.flights().stream().filter(f -> f.id().equals(id)).findFirst().orElseThrow(); }

    private static boolean told(Atc.Result r, UUID id, Atc.Instruction i) {
        return r.transmissions().stream().anyMatch(t -> t.id().equals(id) && t.instruction() == i);
    }

    private static boolean warned(Atc.Result r, UUID id, Atc.Warning w) {
        return r.transmissions().stream().anyMatch(t -> t.id().equals(id) && t.warning() == w);
    }

    @Test void aDepartureIsClearedRollsAndTheNextLinesUpThenGoes() {
        put(plane(A, 10, 25, 0, 0, true));
        put(plane(B, 30, 30, 0, 0, true));
        step();
        assertEquals(Atc.Phase.HOLDING, flight(step(), A).phase(), "near the runway, stopped: holding");
        Atc.Result r = call(A);
        assertTrue(told(r, A, Atc.Instruction.CLEARED_TAKEOFF), "cleared for take-off: " + r.transmissions());
        assertEquals("09", flight(r, A).runway());
        time = 5; put(plane(A, 10, 0, 5, 0, true));
        assertEquals(Atc.Phase.LINED_UP, flight(step(), A).phase());
        time = 10; put(plane(A, 200, 0, 30, 0, true));
        r = step();
        assertEquals(Atc.Phase.TAKEOFF_ROLL, flight(r, A).phase());
        assertFalse(warned(r, A, Atc.Warning.TAKEOFF_WITHOUT_CLEARANCE));
        time = 15; put(plane(A, 450, 0, 40, 20, false));
        r = call(B);
        assertEquals(Atc.Phase.DEPARTED, flight(r, A).phase());
        assertTrue(told(r, B, Atc.Instruction.LINE_UP_AND_WAIT), "spacing holds it, the runway is free: line up and wait " + r.transmissions());
        time = 30; put(plane(A, 1000, 0, 45, 120, false));
        assertEquals(Atc.Instruction.LINE_UP_AND_WAIT, flight(step(), B).instruction(), "not a minute yet, and A is 970 m away");
        time = 71;
        r = step();
        assertTrue(told(r, B, Atc.Instruction.CLEARED_TAKEOFF), "a minute after A's roll: cleared " + r.transmissions());
    }

    @Test void arrivalsAreNumberedAndTheFirstIsCleared() {
        put(plane(A, -5000, 0, 50, 260, false));
        put(plane(B, -3000, 0, 50, 150, false));
        step();
        call(A);
        Atc.Result r = call(B);
        assertEquals(Atc.Phase.FINAL, flight(r, B).phase());
        assertTrue(told(r, B, Atc.Instruction.CLEARED_LAND), "B, 60 s out, first: cleared to land " + r.transmissions());
        assertEquals(Atc.Instruction.CONTINUE_APPROACH, flight(r, A).instruction());
        assertEquals(2, flight(r, A).sequence(), "A is number 2");
        // B lands and leaves the runway; A is then cleared
        time = 60; put(plane(B, 300, 0, 30, 0, true)); put(plane(A, -2000, 0, 50, 100, false));
        r = step();
        assertEquals(Atc.Phase.ROLLOUT, flight(r, B).phase());
        assertFalse(warned(r, B, Atc.Warning.LANDED_WITHOUT_CLEARANCE));
        assertEquals(Atc.Instruction.CONTINUE_APPROACH, flight(r, A).instruction(), "not while B is on the runway");
        time = 75; put(plane(B, 500, 40, 5, 0, true)); put(plane(A, -1300, 0, 50, 70, false));
        r = step();
        assertEquals(Atc.Phase.VACATED, flight(r, B).phase());
        assertTrue(told(r, B, Atc.Instruction.TAXI_TO_APRON));
        assertTrue(told(r, A, Atc.Instruction.CLEARED_LAND), "B off the runway: A cleared " + r.transmissions());
    }

    @Test void aDepartureWaitsForACloseArrival() {
        put(plane(A, 10, 25, 0, 0, true));
        put(plane(B, -2000, 0, 50, 100, false));   // 40 s out, not even calling
        step();
        Atc.Result r = call(A);
        assertTrue(told(r, A, Atc.Instruction.HOLD_POSITION), "an arrival 40 s out: hold " + r.transmissions());
        time = 50; put(plane(B, 300, 0, 30, 0, true));
        assertEquals(Atc.Instruction.HOLD_POSITION, flight(step(), A).instruction(), "it landed and is on the runway");
        time = 60; put(plane(B, 600, 60, 5, 0, true));
        r = step();
        assertTrue(told(r, A, Atc.Instruction.CLEARED_TAKEOFF), "the runway is free again " + r.transmissions());
    }

    @Test void aCarOnTheRunwaySendsALandingAircraftAround() {
        put(plane(A, -3000, 0, 50, 150, false));
        put(new Atc.Traffic(CAR, "car", AircraftKind.GROUND, WakeCategory.LIGHT, new Vec3d(100, 65, 40), new Vec3d(0, 0, -5), 0.5, true, true));
        step();
        assertTrue(told(call(A), A, Atc.Instruction.CLEARED_LAND));
        time = 10; put(new Atc.Traffic(CAR, "car", AircraftKind.GROUND, WakeCategory.LIGHT, new Vec3d(100, 65, 0), new Vec3d(0, 0, -5), 0.5, true, true));
        put(plane(A, -2500, 0, 50, 130, false));
        Atc.Result r = step();
        assertTrue(warned(r, CAR, Atc.Warning.RUNWAY_INCURSION) && warned(r, A, Atc.Warning.RUNWAY_INCURSION), "both told " + r.transmissions());
        time = 40; put(plane(A, -1400, 0, 50, 80, false));
        r = step();
        assertTrue(told(r, A, Atc.Instruction.GO_AROUND), "28 s out with the runway occupied: go around " + r.transmissions());
        assertTrue(r.flights().stream().noneMatch(f -> f.id().equals(CAR) && f.instruction() != Atc.Instruction.NONE), "the car gets no instructions");
    }

    @Test void takingOffOrLandingWithoutAClearanceIsOnlyAWarning() {
        put(plane(A, 10, 0, 5, 0, true));
        step();
        time = 5; put(plane(A, 200, 0, 30, 0, true));
        Atc.Result r = step();
        assertTrue(warned(r, A, Atc.Warning.TAKEOFF_WITHOUT_CLEARANCE));
        assertEquals(Atc.Phase.TAKEOFF_ROLL, flight(r, A).phase(), "and it rolls all the same");
        put(plane(B, -1000, 0, 50, 50, false));
        time = 20; step();
        time = 40; put(plane(B, 100, 0, 30, 0, true));
        assertTrue(warned(step(), B, Atc.Warning.LANDED_WITHOUT_CLEARANCE));
    }

    @Test void anUnmannedTowerGivesNoClearancesAndNoWarnings() {
        manned = false;
        put(plane(A, 10, 25, 0, 0, true));
        step();
        Atc.Result r = call(A);
        assertTrue(told(r, A, Atc.Instruction.SELF_ANNOUNCE));
        time = 5; put(plane(A, 200, 0, 30, 0, true));
        r = step();
        assertTrue(r.transmissions().isEmpty(), "no warnings without a controller: " + r.transmissions());
    }

    @Test void aPlayerInControlDecidesAndSeesConflicts() {
        human = true;
        put(plane(A, 10, 25, 0, 0, true));
        put(plane(B, -3000, 0, 50, 150, false));
        step();
        Atc.Result r = step(Map.of(A, Atc.Request.CALL, B, Atc.Request.CALL), List.of());
        assertFalse(told(r, A, Atc.Instruction.CLEARED_TAKEOFF) || told(r, B, Atc.Instruction.CLEARED_LAND), "nothing given by itself");
        r = step(Map.of(), List.of(new Atc.Command(A, Atc.Instruction.CLEARED_TAKEOFF), new Atc.Command(B, Atc.Instruction.CLEARED_LAND)));
        assertTrue(told(r, A, Atc.Instruction.CLEARED_TAKEOFF) && told(r, B, Atc.Instruction.CLEARED_LAND));
        assertTrue(r.conflicts().stream().anyMatch(c -> c.a().equals(B) && c.b().equals(A)), "a landing into a take-off: " + r.conflicts());
    }

    @Test void unusedClearancesLapse() {
        put(plane(A, 10, 25, 0, 0, true));
        step();
        assertTrue(told(call(A), A, Atc.Instruction.CLEARED_TAKEOFF));
        time = 95;
        assertTrue(told(step(), A, Atc.Instruction.HOLD_POSITION), "90 s without rolling: cancelled");
    }

    @Test void aHelicopterLiftsAndLandsWhereItIs() {
        put(new Atc.Traffic(C, "heli", AircraftKind.ROTORCRAFT, WakeCategory.LIGHT, new Vec3d(300, 64, 200), Vec3d.ZERO, 0, true, true));
        step();
        assertTrue(told(call(C), C, Atc.Instruction.TAKEOFF_HERE));
        time = 30; put(new Atc.Traffic(C, "heli", AircraftKind.ROTORCRAFT, WakeCategory.LIGHT, new Vec3d(300, 120, 400), new Vec3d(0, 0, 10), 56, false, true));
        Atc.Result r = step();
        assertEquals(Atc.Instruction.NONE, flight(r, C).instruction(), "airborne: that clearance is done");
        r = call(C);
        assertTrue(told(r, C, Atc.Instruction.LAND_HERE), "airborne away from the runway: land where it is " + r.transmissions());
        time = 60; put(new Atc.Traffic(C, "heli", AircraftKind.ROTORCRAFT, WakeCategory.LIGHT, new Vec3d(300, 64, 450), Vec3d.ZERO, 0, true, true));
        assertEquals(Atc.Instruction.NONE, flight(step(), C).instruction(), "down: done");
    }

    @Test void aRepeatedCallSaysItAgainAndCancelWithdraws() {
        put(plane(A, 10, 25, 0, 0, true));
        step();
        call(A);
        Atc.Result r = call(A);
        assertTrue(told(r, A, Atc.Instruction.CLEARED_TAKEOFF), "said again");
        r = step(Map.of(A, Atc.Request.CANCEL), List.of());
        assertTrue(told(r, A, Atc.Instruction.CANCELLED));
        assertEquals(Atc.Instruction.NONE, flight(r, A).instruction());
    }

    @Test void aVehicleGoneForHalfAMinuteIsForgotten() {
        put(plane(A, 10, 25, 0, 0, true));
        step();
        traffic.clear();
        time = 20;
        assertEquals(1, step().flights().size());
        time = 40;
        assertEquals(0, step().flights().size());
    }
}
