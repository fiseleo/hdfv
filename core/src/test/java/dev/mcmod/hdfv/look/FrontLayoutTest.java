package dev.mcmod.hdfv.look;

import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.body.BodyShape;
import dev.mcmod.hdfv.design.Designs;
import dev.mcmod.hdfv.design.RecordEditor;
import dev.mcmod.hdfv.part.HdfvParts;
import dev.mcmod.hdfv.part.body.BodyDesign;
import dev.mcmod.hdfv.part.body.BodyFrontDesign;
import dev.mcmod.hdfv.part.body.SampleBodies;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 0.18: a car's front end (the user's 「車頭應該是長這樣」: an Astra-like saloon, a Panda-like box): FrontLayout's
 * automatic choices, its pieces on the body and its lamps.
 */
class FrontLayoutTest {
    static { HdfvParts.register(); }

    static BodySurface surface(BodyDesign d) { return new BodySurface(d.shape()); }

    static Set<String> roles(FrontLayout.Pieces p) { return p.panels().stream().map(KitLayout.Panel::role).collect(Collectors.toSet()); }

    @Test void aRoundedSaloonAndASquareBoxGetTheirOwnFronts() {
        BodyDesign sedan = SampleBodies.sedan(), box = SampleBodies.hatchback();
        FrontLayout.Resolved r = FrontLayout.resolve(sedan, surface(sedan));
        assertFalse(r.boxy());
        assertEquals(BodyFrontDesign.Headlamps.WRAP, r.headlamps());
        assertEquals(BodyFrontDesign.Grille.SLATS, r.grille());
        assertEquals(BodyFrontDesign.Bumper.BODY, r.bumper());
        assertEquals(BodyFrontDesign.Intake.WIDE, r.intake());
        assertEquals(BodyFrontDesign.Fog.ROUND, r.fog());
        assertTrue(r.emblem());
        FrontLayout.Resolved b = FrontLayout.resolve(box, surface(box));
        assertTrue(b.boxy(), "a 0.95 nose is square");
        assertEquals(BodyFrontDesign.Headlamps.SQUARE, b.headlamps());
        assertEquals(BodyFrontDesign.Grille.FULL_WIDTH, b.grille());
        assertEquals(BodyFrontDesign.Bumper.BLACK, b.bumper());
        assertEquals(BodyFrontDesign.Intake.SLOT, b.intake());
        assertEquals(BodyFrontDesign.Fog.SQUARE, b.fog());
        // all automatic writes no key: the sample's JSON as before (DesignJsonFreezeTest), the box's has none either
        assertFalse(Designs.write(sedan).contains("\"front\""));
        assertFalse(Designs.write(box).contains("\"front\""));
        assertNull(FrontLayout.resolve(SampleBodies.runabout(), surface(SampleBodies.runabout())), "a boat has no car's front");
    }

    @Test void thePiecesSitOnTheBody() {
        for (BodyDesign d : List.of(SampleBodies.sedan(), SampleBodies.hatchback())) {
            BodySurface s = surface(d);
            FrontLayout.Pieces p = FrontLayout.of(d, s);
            FrontLayout.Face f = p.face();
            assertTrue(roles(p).containsAll(Set.of("bumper", "bumper_edge", "grille_recess", "intake", "fog_recess")), d.name() + ": " + roles(p));
            assertTrue(f.bumper() && f.bumperOut() > 0.02);
            // the bumper's face stands bumperOut off the skin: on the flat front at the face's plane plus it, on the sides off the skin by it
            for (KitLayout.Panel panel : p.panels()) {
                if (!panel.role().equals("bumper")) { continue; }
                for (Vec3d[] q : panel.quads()) {
                    for (Vec3d v : q) {
                        assertTrue(v.y() <= f.bumperTop() + 1e-9, d.name() + ": no higher than its crease " + v);
                        if (v.x() > s.front() - 0.1 && v.x() <= s.front() + 0.5 * f.roll()) { continue; }   // round the tip's corner: no side to measure from
                        if (v.x() > s.front() + 0.5 * f.roll()) {
                            assertEquals(f.x() + f.bumperOut(), v.x(), 0.012, d.name() + ": the front of the bumper " + v);
                        } else {
                            double x = Math.clamp(v.x(), s.rear(), s.front());
                            double skin = Math.abs(s.point(x, s.portAngle(x, v.y())).z());
                            assertTrue(Math.abs(v.z()) - skin < f.bumperOut() + 0.02 && Math.abs(v.z()) - skin > -0.01, d.name() + ": its side off the skin " + v);
                        }
                    }
                }
            }
            // the plate in the bumper's front, under its crease
            assertNotNull(p.plate());
            assertEquals(f.x() + f.bumperOut(), p.plate().x(), 1e-9);
            assertTrue(p.plate().y() + 0.5 * FrontLayout.PLATE_H < f.bumperTop() && p.plate().y() > f.bottom(), d.name() + ": the plate " + p.plate());
            // the grille on the face above the bumper, inside its edges
            for (KitLayout.Panel panel : p.panels()) {
                if (!panel.role().equals("grille_recess")) { continue; }
                for (Vec3d[] q : panel.quads()) {
                    for (Vec3d v : q) {
                        assertTrue(v.y() > f.bumperTop() && v.y() <= f.faceTop() + 1e-9, d.name() + ": the grille above the bumper " + v);
                        assertTrue(Math.abs(v.z()) < f.half(d, s, v.y()), d.name() + ": inside the face " + v);
                    }
                }
            }
        }
    }

    @Test void theLampsAreTheFronts() {
        BodyDesign sedan = SampleBodies.sedan();
        BodySurface s = surface(sedan);
        BodyShape shape = sedan.shape();
        List<LampSpec> lamps = LampLayout.auto(sedan, s, new BodyLayout(sedan, shape, s).waterline);
        LampSpec head = lamps.stream().filter(l -> l.role() == LampRules.Role.HEAD_LOW).findFirst().orElseThrow();
        assertEquals(LampSpec.Style.CLUSTER, head.style());
        assertTrue(head.pair());
        LampSpec fog = lamps.stream().filter(l -> l.role() == LampRules.Role.FOG).findFirst().orElseThrow();
        assertTrue(lamps.stream().anyMatch(l -> l.role() == LampRules.Role.TAIL_BRAKE), "the tail lamps as before");
        // the cluster wraps round the corner onto the side, its lens just off the skin
        FrontLayout.Cluster c = FrontLayout.cluster(sedan, s, head, false);
        boolean onSide = false;
        for (Vec3d[] q : c.lens()) {
            for (Vec3d v : q) {
                if (v.x() < s.front() - 0.05) {
                    onSide = true;
                    double skin = Math.abs(s.point(v.x(), s.portAngle(v.x(), v.y())).z());
                    assertTrue(Math.abs(v.z()) - skin > 0.005 && Math.abs(v.z()) - skin < 0.03, "the lens off the side's skin " + v);
                }
            }
        }
        assertTrue(onSide, "it wraps round onto the side");
        assertFalse(c.bulbs().isEmpty());
        assertTrue(c.bulbs().get(0).z() < 0, "the port one first");
        // the fog lamp sits on the bumper's face; its mirror image to starboard
        FrontLayout.Face f = FrontLayout.face(sedan, s);
        LampLayout.Placed placed = LampLayout.place(sedan, s, fog, false);
        assertEquals(f.x() + f.bumperOut(), placed.at().x(), 1e-9);
        assertTrue(LampLayout.place(sedan, s, fog, true).at().z() > 0);
        assertEquals(1, LampRules.level(LampRules.Role.FOG, new LampRules.Inputs(2, 0, 0, false, 0, 0)));
        assertEquals(0, LampRules.level(LampRules.Role.FOG, new LampRules.Inputs(1, 0, 0, false, 0, 0)));
        // the box's headlamps are square, on its face
        BodyDesign box = SampleBodies.hatchback();
        BodySurface bs = surface(box);
        assertTrue(LampLayout.auto(box, bs, new BodyLayout(box, box.shape(), bs).waterline).stream()
                .anyMatch(l -> l.role() == LampRules.Role.HEAD_LOW && l.style() == LampSpec.Style.SQUARE && l.face() == LampSpec.Face.FRONT));
    }

    @Test void aChosenFrontReadsBackAndTheKitHangsUnderItsBumper() {
        BodyDesign sedan = SampleBodies.sedan();
        BodyFrontDesign mesh = RecordEditor.with(RecordEditor.with(RecordEditor.with(BodyFrontDesign.AUTO, "grille", BodyFrontDesign.Grille.MESH),
                "bumper", BodyFrontDesign.Bumper.CHROME), "bulgeM", 0.03);
        BodyDesign d = sedan.withFront(mesh);
        assertTrue(d.check().stream().noneMatch(i -> i.kind() == dev.mcmod.aerocore.api.vehicle.DesignIssue.Kind.INVALID), d.check().toString());
        String json = Designs.write(d);
        assertTrue(json.contains("\"front\"") && json.contains("\"mesh\"") && json.contains("\"chrome\"") && !json.contains("grilleWidth"), json);
        assertEquals(d, Designs.read(json), "the same design back");
        BodySurface s = surface(d);
        FrontLayout.Pieces p = FrontLayout.of(d, s);
        assertTrue(roles(p).contains("bulge"));
        assertEquals(BodyFrontDesign.Grille.MESH, p.resolved().grille());
        // a blank number is automatic, a share outside 0..1 is not a share
        assertTrue(RecordEditor.with(BodyFrontDesign.AUTO, "grilleWidth", 1.5).check().stream().anyMatch(i -> i.kind() == dev.mcmod.aerocore.api.vehicle.DesignIssue.Kind.INVALID));
        // the kit's dam hangs from the bumper's foot, not inside it
        BodyDesign kitted = sedan.withKit(KitLayoutTest.KIT);
        BodySurface ks = surface(kitted);
        FrontLayout.Face f = FrontLayout.face(kitted, ks);
        double frontMost = KitLayout.of(kitted).panels.stream().filter(q -> q.role().equals("dam")).flatMap(q -> q.quads().stream())
                .flatMap(java.util.Arrays::stream).mapToDouble(Vec3d::x).max().orElseThrow();
        assertEquals(f.x() + f.bumperOut() - 0.01, frontMost, 0.03, "the dam's front at the bumper's");
    }
}
