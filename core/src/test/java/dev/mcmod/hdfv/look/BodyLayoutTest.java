package dev.mcmod.hdfv.look;

import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.hdfv.part.HdfvParts;
import dev.mcmod.hdfv.part.body.BodyDesign;
import dev.mcmod.hdfv.part.body.BodyModuleDesign;
import dev.mcmod.hdfv.part.body.SampleBodies;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Where the sample bodies' openings go (0.12): each lies in the body (doors, cabin windows and portholes in their own
 * module), none overlaps another but a window in its door (which lies wholly inside it), every seat in a glazed module
 * looks out of a window near it, and the drivers sit where they should.
 */
class BodyLayoutTest {
    static { HdfvParts.register(); }

    private static final List<BodyDesign> BODIES = List.of(SampleBodies.seaplaneFuselage(), SampleBodies.seaplaneFloat(), SampleBodies.helicopterFuselage(),
            SampleBodies.runabout(), SampleBodies.sedan(), SampleBodies.flyingBoat());

    /** Points spread over an opening's inside (a little in from its edges). */
    private static double[][] inside(BodyLayout.Opening o) {
        double[][] out = new double[49][];
        int n = 0;
        for (int i = 1; i <= 7; i++) {
            double x = o.x0() + (o.x1() - o.x0()) * i / 8;
            double lo = o.loAt(x), hi = o.hiAt(x);
            for (int k = 1; k <= 7; k++) { out[n++] = new double[]{x, lo + (hi - lo) * k / 8}; }
        }
        return out;
    }

    @Test void openingsLieInTheBodyAndDoNotOverlap() {
        for (BodyDesign d : BODIES) {
            BodyLayout l = BodyLayout.of(d);
            BodySurface s = l.surface;
            for (int k = 0; k < l.openings.size(); k++) {
                BodyLayout.Opening o = l.openings.get(k);
                String what = d.name() + " " + o.type() + " " + k;
                assertEquals(k, o.id(), what + " is its index");
                assertTrue(o.x0() >= s.rear() - 1e-9 && o.x1() <= s.front() + 1e-9, what + " lies along the body");
                for (int r = 0; r < o.xs().length; r++) {
                    assertTrue(o.lo()[r] <= o.hi()[r] + 1e-12 && o.lo()[r] >= -1e-9 && o.hi()[r] <= 2 * Math.PI + 1e-9, what + " row " + r);
                    if (r > 0) { assertTrue(o.xs()[r] > o.xs()[r - 1], what + " rows run forward"); }
                }
                assertTrue(o.area() > 0, what + " has an area");
                // a car's doors follow its glasshouse over the passenger modules; an aircraft's or a boat's stay in their module
                boolean car = d.modules().get(o.module()).kind().family == BodyModuleDesign.Family.CAR;
                if (!car && (o.type() == BodyLayout.Type.DOOR || o.type() == BodyLayout.Type.CABIN_WINDOW || o.type() == BodyLayout.Type.PORTHOLE)) {
                    assertTrue(o.x0() >= d.shape().moduleRearX(o.module()) - 1e-6 && o.x1() <= d.shape().moduleFrontX(o.module()) + 1e-6, what + " in its module");
                }
                if (o.type() == BodyLayout.Type.DOOR) { assertNotNull(o.hinge(), what + " has a hinge"); }
                if (o.door() >= 0) {
                    BodyLayout.Opening door = l.openings.get(o.door());
                    assertEquals(BodyLayout.Type.DOOR, door.type(), what + " is in a door");
                    for (double[] p : inside(o)) { assertTrue(door.contains(p[0], p[1]), what + " lies in its door at " + p[0] + ", " + p[1]); }
                }
                for (int j = 0; j < l.openings.size(); j++) {
                    if (j == k || j == o.door() || l.openings.get(j).door() == k) { continue; }
                    BodyLayout.Opening other = l.openings.get(j);
                    for (double[] p : inside(o)) {
                        assertFalse(other.contains(p[0], p[1]), what + " overlaps " + other.type() + " " + j + " at " + p[0] + ", " + p[1]);
                    }
                }
            }
        }
    }

    @Test void openingsAreCutAsFewConvexParts() {
        for (BodyDesign d : BODIES) {
            BodyLayout l = BodyLayout.of(d);
            for (BodyLayout.Opening o : l.openings) {
                double sum = 0;
                for (double[][] part : o.parts()) {
                    double area = 0;
                    for (int i = 0; i < part.length; i++) {
                        double[] a = part[i], b = part[(i + 1) % part.length];
                        area += 0.5 * (a[0] * b[1] - b[0] * a[1]);
                    }
                    assertTrue(BodyLayout.Opening.convex(part), d.name() + " " + o.type() + " part is convex");
                    assertTrue(area > -1e-12, d.name() + " " + o.type() + " part wound counter-clockwise");
                    sum += area;
                }
                assertEquals(o.area(), sum, 1e-9 + 1e-9 * o.area(), d.name() + " " + o.type() + ": the parts make up the opening");
                if (o.type() == BodyLayout.Type.CABIN_WINDOW || o.type() == BodyLayout.Type.PORTHOLE) {
                    assertTrue(o.parts().size() <= 3, d.name() + " " + o.type() + " in " + o.parts().size() + " parts");
                }
            }
        }
    }

    @Test void everySeatInAGlazedModuleLooksOut() {
        for (BodyDesign d : BODIES) {
            BodyLayout l = BodyLayout.of(d);
            for (int k = 0; k < l.seats.size(); k++) {
                BodyDesign.Seat seat = l.seats.get(k);
                BodyModuleDesign m = d.modules().get(seat.module());
                if (m.open() || m.windowShare() <= 0) { continue; }
                BodyLayout.Opening view = l.view(k);
                assertNotNull(view, d.name() + " seat " + k + " has a window");
                double distance = view.centre(l.surface).subtract(BodyLayout.eye(seat)).length();
                assertTrue(distance < 1.6, d.name() + " seat " + k + " is " + distance + " m from its nearest window");
            }
        }
    }

    @Test void driversSitWhereTheyShould() {
        BodyLayout car = BodyLayout.of(SampleBodies.sedan());
        BodyDesign.Seat driver = car.seats.get(car.driver);
        assertTrue(driver.at().z() < -0.2, "left-hand drive: the driver sits to port");
        for (BodyDesign.Seat s : car.seats) { assertTrue(s.at().x() <= driver.at().x() + 1e-9, "in the front row"); }
        Vec3d eye = BodyLayout.eye(driver);
        BodyLayout.Opening screen = car.openings.stream().filter(o -> o.type() == BodyLayout.Type.WINDSHIELD).findFirst().orElseThrow();
        assertTrue(screen.x0() > eye.x() - 0.1, "the windscreen is ahead of the driver's eyes");
        assertTrue(screen.centre(car.surface).y() > eye.y() - 0.3 && screen.centre(car.surface).y() < eye.y() + 0.4, "at the height they look");
        assertEquals(4, car.openings.stream().filter(o -> o.type() == BodyLayout.Type.DOOR).count(), "a four-door car");
        assertEquals(4, car.openings.stream().filter(o -> o.type() == BodyLayout.Type.ARCH).count(), "an arch each side of two axles");
        assertEquals(4, car.openings.stream().filter(o -> o.door() >= 0).count(), "a window in each door");
        assertTrue(car.belt > 0.4 && car.belt < 1.2, "a belt line " + car.belt);

        BodyLayout plane = BodyLayout.of(SampleBodies.seaplaneFuselage());
        BodyDesign.Seat pilot = plane.seats.get(plane.driver);
        assertEquals(BodyModuleDesign.Kind.COCKPIT, SampleBodies.seaplaneFuselage().modules().get(pilot.module()).kind(), "the pilot is in the cockpit");
        assertTrue(pilot.at().z() < 0, "in the left seat");
        assertTrue(plane.openings.stream().filter(o -> o.type() == BodyLayout.Type.WINDSHIELD).count() >= 2, "two windscreen panes");

        BodyLayout boat = BodyLayout.of(SampleBodies.runabout());
        assertTrue(boat.seats.get(boat.driver).at().z() > 0, "the helm to starboard");
        assertEquals(1, boat.openings.stream().filter(o -> o.type() == BodyLayout.Type.OPEN).count(), "the open cockpit");
        assertTrue(boat.lined[1] == false, "an open module is not lined: its inside is drawn as it is");
        assertTrue(car.lined[2], "the car's cabin is lined");
        assertFalse(car.lined[0], "its hood is not");
    }

    @Test void theSedansTrunkIsPaintedAndItsDashIsAheadOfTheDriver() {
        BodyDesign d = SampleBodies.sedan();
        BodyLayout car = BodyLayout.of(d);
        var shape = d.shape();
        // 0.14: the rear window stops at the rear window module's rear; the trunk is not lined (its lid is painted, not glass)
        assertFalse(car.lined[4], "the trunk is not lined");
        for (BodyLayout.Opening o : car.openings) {
            if (o.type().glazed()) { assertTrue(o.x0() >= shape.moduleRearX(3) - 1e-6 && o.x1() <= shape.moduleFrontX(1) + 1e-6, o.type() + " " + o.x0() + ".." + o.x1()); }
        }
        // the dash: ahead of the driver's knees, behind the firewall, across the belt
        BodyLayout.Dash dash = car.dash();
        BodyDesign.Seat driver = car.seats.get(car.driver);
        assertTrue(dash.rearX() > driver.at().x() + 0.5 && dash.frontX() <= shape.moduleFrontX(1) - 0.01 + 1e-9, dash.toString());
        assertTrue(dash.frontX() - dash.rearX() >= 0.15 - 1e-9);
        assertTrue(dash.bottomY() < car.belt && dash.topY() > car.belt);
        assertEquals(shape.moduleFrontX(1) - 0.01, car.firewall(dash.rearX()), 1e-9);
        assertNull(BodyLayout.of(SampleBodies.seaplaneFuselage()).dash(), "an aircraft has a panel, not a car's dash");
    }
}
