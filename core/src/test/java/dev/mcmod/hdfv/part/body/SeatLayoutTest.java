package dev.mcmod.hdfv.part.body;

import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.body.BodyShape;
import dev.mcmod.hdfv.part.HdfvParts;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 0.14 (the user: 「前 2 後 3」): a car's passenger cell puts its short row in front on the outboard places; the body's
 * compiled seats start with the driver's (the helm, the pilot's), so the first rider drives. Aircraft and boat seats
 * stay where they were.
 */
class SeatLayoutTest {
    static { HdfvParts.register(); }

    @Test void theSedanSeatsTwoInFrontAndThreeBehind() {
        BodyDesign d = SampleBodies.sedan();
        BodyShape shape = d.shape();
        List<BodyDesign.Seat> seats = d.seats(shape);
        assertEquals(5, seats.size());
        double front = seats.stream().mapToDouble(s -> s.at().x()).max().orElseThrow();
        List<Double> frontRow = seats.stream().filter(s -> Math.abs(s.at().x() - front) < 1e-9).map(s -> s.at().z()).sorted().toList();
        List<Double> rearRow = seats.stream().filter(s -> Math.abs(s.at().x() - front) >= 1e-9).map(s -> s.at().z()).sorted().toList();
        assertEquals(2, frontRow.size());
        assertEquals(3, rearRow.size());
        double o = 0.5 * shape.widthAt(front) - BodyDesign.CAR_SEAT_EDGE_M;
        assertEquals(-o, frontRow.get(0), 1e-9);
        assertEquals(o, frontRow.get(1), 1e-9);
        assertEquals(0.39, o, 0.02, "real front seats' centres");
        assertEquals(0, rearRow.get(1), 1e-9);
        assertEquals(-rearRow.get(0), rearRow.get(2), 1e-9);
        // the driver front left, and the compiled body's first seat
        int driver = d.driverSeat(seats);
        assertEquals(front, seats.get(driver).at().x(), 1e-9);
        assertTrue(seats.get(driver).at().z() < 0);
        assertEquals(seats.get(driver).at(), d.compile().seats().get(0));
        assertEquals(5, d.compile().seats().size());
    }

    @Test void theOutboardPlacesComeFirst() {
        assertEquals(List.of(0, 2, 1), BodyDesign.outboardFirst(3));
        assertEquals(List.of(0, 3, 1, 2), BodyDesign.outboardFirst(4));
        assertEquals(List.of(0), BodyDesign.outboardFirst(1));
    }

    @Test void theHelmAndThePilotAreSeatZero() {
        BodyDesign boat = SampleBodies.runabout();
        List<BodyDesign.Seat> seats = boat.seats(boat.shape());
        Vec3d helm = seats.get(boat.driverSeat(seats)).at();
        assertTrue(helm.z() > 0, "the helm to starboard");
        assertEquals(helm, boat.compile().seats().get(0));
        BodyDesign plane = SampleBodies.seaplaneFuselage();
        List<BodyDesign.Seat> ps = plane.seats(plane.shape());
        assertEquals(ps.get(plane.driverSeat(ps)).at(), plane.compile().seats().get(0));
    }

    @Test void aircraftAndBoatSeatsStayWhereTheyWere() {
        for (BodyDesign d : List.of(SampleBodies.seaplaneFuselage(), SampleBodies.helicopterFuselage(), SampleBodies.runabout(), SampleBodies.flyingBoat())) {
            BodyShape shape = d.shape();
            List<BodyDesign.Seat> seats = d.seats(shape);
            int k = 0;
            for (int i = 0; i < d.modules().size(); i++) {
                BodyModuleDesign m = d.modules().get(i);
                int abreast = Math.max(m.seatsAbreast(), 1);
                for (int j = 0; j < m.seats(); j++) {
                    double x = shape.moduleFrontX(i) - (j / abreast + 0.5) * m.seatPitchM();
                    double z = (j % abreast - (abreast - 1) / 2.0) * Math.min(0.55, shape.widthAt(x) / abreast);
                    assertEquals(new Vec3d(x, shape.bottomAt(x) + 0.3, z), seats.get(k++).at(), d.name() + " seat " + j);
                }
            }
            assertEquals(k, seats.size());
            // every seat is in the compiled body once
            List<Vec3d> compiled = d.compile().seats().stream().sorted(Comparator.comparingDouble(Vec3d::x).thenComparingDouble(Vec3d::z)).toList();
            assertEquals(seats.stream().map(BodyDesign.Seat::at).sorted(Comparator.comparingDouble(Vec3d::x).thenComparingDouble(Vec3d::z)).toList(), compiled);
        }
    }
}
