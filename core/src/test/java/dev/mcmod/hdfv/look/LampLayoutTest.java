package dev.mcmod.hdfv.look;

import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.body.BodyShape;
import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.hdfv.design.Designs;
import dev.mcmod.hdfv.part.HdfvParts;
import dev.mcmod.hdfv.part.body.BodyDesign;
import dev.mcmod.hdfv.part.body.SampleBodies;
import dev.mcmod.hdfv.refit.Refit;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 0.16: a body's lamps: the kit's as 0.12 placed them, a player's own anywhere on it (LampSpec, LampLayout). */
class LampLayoutTest {
    static { HdfvParts.register(); }

    private static List<LampLayout.Placed> placed(BodyDesign d) {
        BodyShape shape = d.shape();
        BodySurface s = new BodySurface(shape);
        return LampLayout.of(d, s, new BodyLayout(d, shape, s).waterline);
    }

    @Test void theKitsLampsAreWhereTheyWere() {
        BodyDesign sedan = SampleBodies.sedan();
        BodyShape shape = sedan.shape();
        BodySurface s = new BodySurface(shape);
        List<LampLayout.Placed> lamps = placed(sedan);
        assertEquals(6, lamps.size(), "0.18: the front's headlamp clusters and fog lamps (FrontLayout), and two tail lamps");
        // the front's: its headlamps on the flat front (the rolled edge's radius ahead of the last station), port first
        int last = s.xs.length - 1;
        BodyShape.Station front = s.stations.get(last);
        double roll = LampLayout.rollOver(sedan, front);
        LampLayout.Placed head = lamps.get(0);
        assertEquals(LampRules.Role.HEAD_LOW, head.role());
        assertEquals(LampSpec.Style.CLUSTER, head.style());
        assertEquals(front.xM() + roll, head.at().x(), 1e-12);
        assertTrue(head.at().z() < 0 && lamps.get(1).at().z() > 0, "port first, its pair to starboard");
        assertEquals(new Vec3d(1, 0, 0), head.out());
        assertTrue(head.beam() && head.spreadDeg() == 22 && head.rangeM() == 45);
        // the tail lamps as 0.12 put them: the rear face 62 % up, 72 % out
        LampLayout.Placed tail = lamps.get(4);
        assertEquals(LampRules.Role.TAIL_BRAKE, tail.role());
        assertEquals(new Vec3d(-1, 0, 0), tail.out());
        assertEquals(new Vec3d(0, 0, -1), tail.along(), "a tail cluster lies along its side");
        assertEquals(LampLayout.RED, tail.light());
    }

    @Test void aircraftAndBoatsKeepTheirLights() {
        List<LampRules.Role> heli = placed(SampleBodies.helicopterFuselage()).stream().map(LampLayout.Placed::role).toList();
        assertTrue(heli.contains(LampRules.Role.BEACON), "beacons on the cabin: " + heli);
        List<LampLayout.Placed> boat = placed(SampleBodies.runabout());
        List<LampRules.Role> roles = boat.stream().map(LampLayout.Placed::role).toList();
        assertTrue(roles.containsAll(List.of(LampRules.Role.SIDE_RED, LampRules.Role.SIDE_GREEN, LampRules.Role.MASTHEAD, LampRules.Role.STERN)), roles.toString());
        LampLayout.Placed red = boat.get(roles.indexOf(LampRules.Role.SIDE_RED)), green = boat.get(roles.indexOf(LampRules.Role.SIDE_GREEN));
        assertTrue(red.at().z() < 0 && green.at().z() > 0, "red to port, green to starboard");
        LampLayout.Placed mast = boat.get(roles.indexOf(LampRules.Role.MASTHEAD));
        assertEquals(0.8, mast.at().y() - mast.foot().y(), 1e-12, "on its pole");
    }

    @Test void aPlayersLampsGoWhereTheyAreClicked() {
        BodyDesign sedan = SampleBodies.sedan();
        BodyShape shape = sedan.shape();
        BodySurface s = new BodySurface(shape);
        LampSpec base = new LampSpec(LampRules.Role.HEAD_HIGH, LampSpec.Style.BAR, LampSpec.Face.SIDE, 0, 0, 0, 0, 0, false, 0.6, "", "", 0, 0);
        // on the roof
        Vec3d roof = s.point(2.4, Math.PI);
        LampSpec onRoof = LampLayout.placeAt(sedan, s, roof, base);
        assertEquals(LampSpec.Face.SIDE, onRoof.face());
        LampLayout.Placed bar = LampLayout.place(sedan, s, onRoof, false);
        assertEquals(0, bar.at().subtract(roof.add(new Vec3d(0, 0.5 * LampLayout.housingHeight(LampSpec.Style.BAR, 0.6), 0))).length(), 1e-3,
                "back where it was clicked, standing on the roof");
        assertEquals(Vec3d.X, bar.out(), "a light bar on the roof lights the road ahead, not the sky");
        assertEquals(Vec3d.Z, bar.along(), "across the roof");
        LampSpec beacon = LampLayout.placeAt(sedan, s, roof, new LampSpec(LampRules.Role.BEACON, LampSpec.Style.BEACON, LampSpec.Face.SIDE, 0, 0, 0, 0, 0, false, 0.12, "", "", 0, 0));
        assertEquals(Vec3d.Y, LampLayout.place(sedan, s, beacon, false).out(), "a beacon stands up on the roof");
        // on the front face (above its bumper): across and up from the click
        LampSpec there = new LampSpec(LampRules.Role.HEAD_LOW, LampSpec.Style.ROUND, LampSpec.Face.FRONT, 0, 0, 0.6, 0.62, 0, false, 0.12, "", "", 0, 0);
        LampSpec onFront = LampLayout.placeAt(sedan, s, LampLayout.place(sedan, s, there, false).at(), base);
        assertEquals(LampSpec.Face.FRONT, onFront.face());
        assertEquals(0.6, onFront.across(), 1e-6);
        assertEquals(0.62, onFront.up(), 1e-6);
        // aimed: yaw turns it, its mirror image the other way
        LampSpec aimed = new LampSpec(LampRules.Role.HEAD_LOW, LampSpec.Style.ROUND, LampSpec.Face.FRONT, 0, 0, -0.5, 0.5, 0, true, 0.15, "", "", 20, 0);
        assertTrue(LampLayout.place(sedan, s, aimed, false).out().z() * LampLayout.place(sedan, s, aimed, true).out().z() < 0);
    }

    @Test void aBodysOwnLampsReplaceTheKitsAndFollowItsShape() {
        BodyDesign sedan = SampleBodies.sedan();
        List<LampSpec> own = new ArrayList<>(Refit.lamps(sedan));
        assertEquals(3, own.size(), "the shop starts from the kit's three pairs (0.18: headlamp clusters, fog lamps, tail lamps)");
        own.add(new LampSpec(LampRules.Role.HEAD_HIGH, LampSpec.Style.BAR, LampSpec.Face.SIDE, 2.4, Math.PI, 0, 0, 0, false, 0.6, "#FFE0A0", "#1A1A1C", 0, -5));
        BodyDesign lit = sedan.withSettings(sedan.settings().withLamps(own));
        assertEquals(7, placed(lit).size());
        assertEquals(lit, Designs.read(Designs.write(lit)), "the lamps read back from the JSON");
        assertFalse(Designs.write(sedan).contains("\"lamps\""), "a body without its own writes no lamps key");
        // a longer hood: the headlamps stay on the front face
        BodyDesign longer = lit.withModule(0, dev.mcmod.hdfv.design.RecordEditor.with(lit.modules().get(0), "lengthM", lit.modules().get(0).lengthM() + 0.3));
        assertTrue(placed(longer).get(0).at().x() > placed(lit).get(0).at().x() + 0.29, "the lamps went forward with the face");
    }

    @Test void navigationLightsOnTheWrongSideAreWarned() {
        BodyDesign boat = SampleBodies.runabout();
        List<LampSpec> own = new ArrayList<>(Refit.lamps(boat));
        int red = -1;
        for (int i = 0; i < own.size(); i++) { if (own.get(i).role() == LampRules.Role.SIDE_RED) { red = i; } }
        LampSpec r = own.get(red);
        own.set(red, r.moved(LampSpec.Face.SIDE, r.x(), 2 * Math.PI - r.t(), 0, 0));   // to starboard
        BodyDesign wrong = boat.withSettings(boat.settings().withLamps(own));
        assertTrue(wrong.check().stream().anyMatch(i -> i.kind() == DesignIssue.Kind.EXTRAPOLATION && i.message().contains("red light to starboard")), "warned");
        assertTrue(boat.withSettings(boat.settings().withLamps(Refit.lamps(boat))).check().stream().noneMatch(i -> i.message().contains("light to")), "the kit's are right");
        // too many lamps are refused
        List<LampSpec> many = new ArrayList<>();
        for (int i = 0; i < 17; i++) { many.add(new LampSpec(LampRules.Role.STROBE, LampSpec.Style.ROUND, LampSpec.Face.SIDE, 1, 1, 0, 0, 0, true, 0.1, "", "", 0, 0)); }
        BodyDesign crowded = SampleBodies.sedan().withSettings(SampleBodies.sedan().settings().withLamps(many));
        assertTrue(crowded.check().stream().anyMatch(i -> i.kind() == DesignIssue.Kind.INVALID), "34 lamps are more than 32");
    }

    @Test void everyLampTakenOffLeavesNoneNotTheKits() {
        BodyDesign sedan = SampleBodies.sedan();
        BodyDesign dark = sedan.withSettings(sedan.settings().withLamps(List.of()));
        assertTrue(dark.settings().ownLamps());
        assertEquals(List.of(), placed(dark), "no lamps at all");
        assertTrue(Designs.write(dark).contains("\"lamps\":[]"), Designs.write(dark));
        BodyDesign back = (BodyDesign) Designs.read(Designs.write(dark));
        assertEquals(dark, back, "an empty list reads back as none");
        assertEquals(List.of(), Refit.lamps(back));
        // null is the kit's again
        assertEquals(6, placed(dark.withSettings(dark.settings().withLamps(null))).size());
        // a light bar up to 3 m (a truck's, or a car rescaled)
        LampSpec bar = new LampSpec(LampRules.Role.HEAD_HIGH, LampSpec.Style.BAR, LampSpec.Face.SIDE, 2.4, Math.PI, 0, 0, 0, false, 2.5, "", "", 0, 0);
        assertTrue(sedan.withSettings(sedan.settings().withLamps(List.of(bar))).check().stream().noneMatch(i -> i.kind() == DesignIssue.Kind.INVALID));
    }

    @Test void aPointedNoseKeepsItsHeadlampsOnItsSkin() {
        BodyDesign sedan = SampleBodies.sedan();
        // a nose blunted to 0.45 still has a face for them (FACE_SHARE)
        BodyDesign blunt = sedan.withSettings(dev.mcmod.hdfv.design.RecordEditor.with(sedan.settings(), "noseBluntness", 0.45));
        assertTrue(Refit.lamps(blunt).stream().anyMatch(l -> l.role() == LampRules.Role.HEAD_LOW && l.face() == LampSpec.Face.FRONT), Refit.lamps(blunt).toString());
        BodyDesign pointed = sedan.withSettings(dev.mcmod.hdfv.design.RecordEditor.with(sedan.settings(), "noseBluntness", 0.15));
        BodyShape shape = pointed.shape();
        BodySurface s = new BodySurface(shape);
        List<LampLayout.Placed> lamps = placed(pointed);
        List<LampLayout.Placed> heads = lamps.stream().filter(l -> l.role() == LampRules.Role.HEAD_LOW).toList();
        assertEquals(2, heads.size(), "a pair of headlamps: " + lamps.stream().map(LampLayout.Placed::role).toList());
        for (LampLayout.Placed h : heads) {
            assertTrue(h.at().x() > s.front() - 0.5, "near the nose: " + h.at());
            assertTrue(h.out().x() > 0.4, "turned forward: " + h.out());
        }
        assertTrue(heads.get(0).at().z() * heads.get(1).at().z() < 0, "one each side");
        assertEquals(placed(sedan).stream().filter(l -> l.role() == LampRules.Role.TAIL_BRAKE).toList(),
                lamps.stream().filter(l -> l.role() == LampRules.Role.TAIL_BRAKE).toList(), "the tail lamps as they were");
    }
}
