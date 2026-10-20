package dev.mcmod.hdfv.assembly;

import java.util.ArrayList;
import java.util.List;

/**
 * Whether a scanned room can build a vehicle (0.7): it closes, stands on the assembly floor, is the right kind (a
 * hangar for aircraft, a shipyard for boats, a car factory for cars), holds the vehicle and has a door it leaves by
 * (lengthwise, as an aircraft is towed out or a boat slides down the ways), a crane for parts too heavy to lift by
 * hand, a welding station if anything is welded or riveted; a hangar's door opens onto a clear apron, a shipyard's
 * onto water down a slipway, a car factory's at ground level; and (0.8) a vehicle whose engines burn fuel has a tank.
 * Problems carry a key ("hdfv.room.problem.<key>") and
 * the numbers for its message.
 */
public final class RoomRules {
    private RoomRules() {}

    public record Problem(String key, List<Object> args) {
        public Problem(String key, Object... args) { this(key, List.of(args)); }
    }

    /** One crane rail cell's share of the crane's capacity, kg. */
    public static final double CRANE_KG_PER_CELL = 5000;

    /** The door a vehicle leaves by: the widest the vehicle fits through, else the largest (with no vehicle, the largest). */
    public static RoomScan.Door door(RoomScan.Scan s, AssemblyPlan p) {
        RoomScan.Door best = null;
        for (RoomScan.Door d : s.doors()) {
            if (best == null || fits(d, p) && !fits(best, p) || fits(d, p) == fits(best, p) && d.widthM() * d.heightM() > best.widthM() * best.heightM()) { best = d; }
        }
        return best;
    }

    static boolean fits(RoomScan.Door d, AssemblyPlan p) { return p == null || d.widthM() >= p.widthM() && d.heightM() >= p.heightM(); }

    /** The room's problems for a vehicle (null: the room alone, before a vehicle is laid out in it). */
    public static List<Problem> check(Room room, RoomScan.Scan s, AssemblyPlan p) {
        List<Problem> out = new ArrayList<>();
        if (!s.closed()) { out.add(new Problem("open", s.leakX(), s.leakY(), s.leakZ())); return out; }
        if (p != null && room != p.room()) { out.add(new Problem("wrong_room", p.room().name().toLowerCase(java.util.Locale.ROOT))); }
        if (s.floorMissing() > 0) { out.add(new Problem("floor", s.floorMissing())); }
        RoomScan.Door door = door(s, p);
        if (door == null) {
            out.add(new Problem("no_door"));
        } else if (p != null) {
            if (!fits(door, p)) { out.add(new Problem("door_small", door.widthM(), door.heightM(), ceil(p.widthM()), ceil(p.heightM()))); }
            // the vehicle stands lengthwise to the door
            int along = door.outX() != 0 ? s.lengthX() : s.widthZ(), across = door.outX() != 0 ? s.widthZ() : s.lengthX();
            if (along < p.lengthM() || across < p.widthM() || s.heightY() < p.heightM()) {
                out.add(new Problem("room_small", along, across, s.heightY(), ceil(p.lengthM()), ceil(p.widthM()), ceil(p.heightM())));
            }
        }
        if (door != null) {
            switch (room) {
                case HANGAR -> { if (!door.apronClear()) { out.add(new Problem("apron", RoomScan.APRON)); } }
                case SHIPYARD -> {
                    if (s.slipwayCells() == 0) { out.add(new Problem("slipway")); }
                    if (!door.waterOutside()) { out.add(new Problem("water", RoomScan.WATER)); }
                }
                case CAR_FACTORY -> { if (door.minY() != s.minY() || !door.groundOutside()) { out.add(new Problem("door_ground")); } }
            }
        }
        if (p == null) { return out; }
        if (p.needsCrane() && s.craneCells() * CRANE_KG_PER_CELL < p.heaviestKg()) {
            out.add(new Problem("crane", Math.round(p.heaviestKg()), Math.round(s.craneCells() * CRANE_KG_PER_CELL)));
        }
        if (p.needsWelding() && s.weldingStations() == 0) { out.add(new Problem("welding_station")); }
        out.addAll(vehicle(p));
        return out;
    }

    /**
     * 0.16: the problems of the vehicle itself, whatever the room (the refit shop checks a refitted vehicle with them):
     * engines without a tank, parts that do not fit their bay, seat parts in a body's way, an overloaded frame, a body
     * made for a frame without one, axles without an arch, an underside below the ground.
     */
    public static List<Problem> vehicle(AssemblyPlan p) {
        List<Problem> out = new ArrayList<>();
        if (!p.tankless().isEmpty()) { out.add(new Problem("no_tank", String.join(", ", p.tankless()))); }
        // 0.14: what does not fit in its body's engine bay (the side it sticks out farthest, cm), seat parts in the body's way
        for (Fitting.Misfit m : p.misfits()) { out.add(new Problem("part_does_not_fit", m.part(), m.side(), m.cm())); }
        if (!p.seatClashes().isEmpty()) { out.add(new Problem("seat_clash", String.join(", ", p.seatClashes()))); }
        // 0.15: a chassis's design gross mass, a body made for a frame, the wheels' arches, the underside over the ground
        for (ChassisRules.Overload o : p.overloads()) { out.add(new Problem("frame_overloaded", o.part(), Math.round(o.vehicleKg()), Math.round(o.designKg()))); }
        if (!p.bodiesNeedingFrame().isEmpty()) { out.add(new Problem("body_needs_frame", String.join(", ", p.bodiesNeedingFrame()))); }
        if (!p.missingArches().isEmpty()) { out.add(new Problem("no_wheel_arch", String.join(", ", p.missingArches()))); }
        if (p.clearance() != null && p.clearance().minClearanceM() < 0) {
            out.add(new Problem("touches_ground", (int) Math.ceil(-100 * p.clearance().minClearanceM())));
        }
        // 0.18: a fifth wheel off the frame, a trailer's kingpin without landing legs, an engine up in a cab-over cab
        for (ChassisRules.TruckIssue t : p.truck()) { out.add(new Problem(t.key(), t.parts())); }
        return out;
    }

    private static int ceil(double m) { return (int) Math.ceil(m - 1e-9); }
}
