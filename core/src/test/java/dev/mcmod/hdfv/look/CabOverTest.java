package dev.mcmod.hdfv.look;

import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.hdfv.part.HdfvParts;
import dev.mcmod.hdfv.part.body.BodyDesign;
import dev.mcmod.hdfv.part.body.BodyFrontDesign;
import dev.mcmod.hdfv.part.body.SampleBodies;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** 0.18: a truck's cab-over cab (CAB_OVER) and a trailer's decks (DECK): their fronts, seats, lamps and checks. */
class CabOverTest {
    static { HdfvParts.register(); }

    @Test void aCabOverHasATrucksFront() {
        BodyDesign cab = SampleBodies.cabOver();
        assertEquals(List.of(), cab.check().stream().filter(i -> i.kind() == DesignIssue.Kind.INVALID).toList());
        BodySurface s = new BodySurface(cab.shape());
        FrontLayout.Resolved r = FrontLayout.resolve(cab, s);
        assertTrue(r.cab());
        assertEquals(BodyFrontDesign.Grille.PANEL, r.grille());
        assertEquals(BodyFrontDesign.Bumper.STEP, r.bumper());
        assertEquals(BodyFrontDesign.Intake.NONE, r.intake());
        FrontLayout.Pieces p = FrontLayout.of(cab, s);
        Set<String> roles = p.panels().stream().map(KitLayout.Panel::role).collect(Collectors.toSet());
        assertTrue(roles.containsAll(Set.of("windscreen", "windscreen_frame", "wiper", "grille_recess", "step", "bumper")), roles.toString());
        FrontLayout.Face f = p.face();
        double[] screen = FrontLayout.windscreenBand(cab, s, f);
        assertTrue(screen[1] - screen[0] > 1.0, "a tall windscreen: " + (screen[1] - screen[0]));
        // the grille panel under the windscreen, over the bumper
        for (KitLayout.Panel panel : p.panels()) {
            if (!panel.role().equals("grille_recess")) { continue; }
            for (var q : panel.quads()) { for (var v : q) { assertTrue(v.y() > f.bumperTop() && v.y() < screen[0], "the grille panel " + v); } }
        }
        // the headlamps in the bumper's corners, sitting on its face
        List<LampSpec> lamps = LampLayout.auto(cab, s, new BodyLayout(cab, cab.shape(), s).waterline);
        LampSpec head = lamps.stream().filter(l -> l.role() == LampRules.Role.HEAD_LOW).findFirst().orElseThrow();
        LampLayout.Placed placed = LampLayout.place(cab, s, head, false);
        assertTrue(placed.at().y() <= f.bumperTop(), "in the bumper: " + placed.at());
        assertEquals(f.x() + f.bumperOut(), placed.at().x(), 1e-9);
        // two seats, the driver's first; the front axle under the cab
        assertEquals(2, cab.seats(cab.shape()).size());
        assertTrue(cab.mountPoints(cab.shape()).containsKey("front_axle"));
        assertTrue(cab.compile() != null);
    }

    @Test void aDeckHasNoFrontButItsTailLamps() {
        BodyDesign deck = SampleBodies.lowLoaderDeck();
        assertEquals(List.of(), deck.check().stream().filter(i -> i.kind() == DesignIssue.Kind.INVALID).toList());
        BodySurface s = new BodySurface(deck.shape());
        assertNull(FrontLayout.resolve(deck, s), "a trailer's deck has no car's front");
        List<LampSpec> lamps = LampLayout.auto(deck, s, new BodyLayout(deck, deck.shape(), s).waterline);
        assertTrue(lamps.stream().noneMatch(l -> l.role() == LampRules.Role.HEAD_LOW), lamps.toString());
        assertTrue(lamps.stream().anyMatch(l -> l.role() == LampRules.Role.TAIL_BRAKE), lamps.toString());
        assertEquals(0, deck.seats(deck.shape()).size());
        assertEquals(13.5, deck.shape().lengthM(), 1e-9);
    }
}
