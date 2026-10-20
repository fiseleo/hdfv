package dev.mcmod.hdfv.part.body;

import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.body.RoadAero;
import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.part.BodyPart;
import dev.mcmod.hdfv.design.Designs;
import dev.mcmod.hdfv.look.KitLayout;
import dev.mcmod.hdfv.manufacture.Plans;
import dev.mcmod.hdfv.part.HdfvParts;
import dev.mcmod.hdfv.part.engine.EngineMaterial;
import dev.mcmod.hdfv.refit.Refit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 0.16: a car body's aero kit (BodyKitDesign, KitLayout): JSON, mass, manufacture and the air loads it hands AeroCore. */
class BodyKitTest {
    static { HdfvParts.register(); }

    /** The sample sedan with a track-day kit: dam, splitter, skirts, flares, a 10 degree diffuser, a lip and a wing. */
    static BodyDesign kitted() {
        BodyKitDesign k = new BodyKitDesign(BodyKitDesign.PAGE, BodyKitDesign.Bumper.SPORT, 0.05, 0.06, 0.06, 0.04, 10, 0.6, 0.03, 1.5, 0.28, 10, 0.3, 0.18,
                BodyKitDesign.RoofRack.NONE, 0.05, 2, 0.09, EngineMaterial.CFRP, "#1A1A1C");
        return SampleBodies.sedan().withKit(k);
    }

    @Test void noKitIsTheBodyAsBefore() {
        BodyDesign plain = SampleBodies.sedan();
        assertTrue(plain.kit().none());
        assertFalse(Designs.write(plain).contains("\"kit\""), "no kit key: the JSON, the workpieces' hashes and the caches as before");
        assertNull(((BodyPart) plain.compile()).kit());
        assertTrue(KitLayout.of(plain).empty());
        assertTrue(Designs.pages(plain).contains(BodyKitDesign.PAGE), "a car's body has the page");
        BodyDesign aircraft = BodyDesign.fresh(BodySettingsDesign.Purpose.AIRCRAFT);
        assertFalse(Designs.pages(aircraft).contains(BodyKitDesign.PAGE), "an aircraft's does not");
    }

    @Test void aKitReadsBackAndWeighs() {
        BodyDesign plain = SampleBodies.sedan(), k = kitted();
        assertEquals(List.of(), k.check().stream().filter(i -> i.kind() == DesignIssue.Kind.INVALID).toList());
        assertEquals(k, Designs.read(Designs.write(k)), "the same design back from its JSON");
        KitLayout layout = KitLayout.of(k);
        assertTrue(layout.massKg > 5 && layout.massKg < 60, "carbon add-ons: " + layout.massKg + " kg");
        assertEquals(plain.mass().massKg() + layout.massKg, k.mass().massKg(), 1e-6);
        assertNotNull(layout.wing);
        assertNotNull(layout.diffuser);
        assertEquals(4, layout.flares.size(), "two axles, both sides");
        assertEquals(2, layout.tips.size());
        assertTrue(layout.panels.stream().anyMatch(p -> p.role().equals("splitter")));
        // the wing over the trunk, behind the cabin, above the deck
        assertTrue(layout.wing.quarterChord().x() < 0.6 && layout.wing.quarterChord().y() > layout.wing.deckY() + 0.25);
        // its manufacture: a kit's moulding in the room
        assertTrue(Plans.of(k).structure().stream().anyMatch(w -> w.key().equals("kit")));
        assertFalse(Plans.of(plain).structure().stream().anyMatch(w -> w.key().equals("kit")));
    }

    @Test void theKitPullsTheCarDownAtSpeed() {
        BodyPart part = (BodyPart) kitted().compile();
        RoadAero aero = part.kit();
        assertNotNull(aero);
        double v = 200 / 3.6;
        RoadAero.Loads l = aero.loads(new Vec3d(-v, 0, 0), 1.225, p -> 0.12);
        double q = 0.5 * 1.225 * v * v;
        assertTrue(l.downforceN() > 1000, "over a kilonewton at 200 km/h: " + l.downforceN());
        assertTrue(l.downforceN() / q < 2.5, "a road car's kit, not a race car's floor: C_L A " + l.downforceN() / q);
        assertTrue(l.dragN() > 0);
        // more of it at the rear (the wing, the diffuser) than the front: its centre (about the rear end) is behind the middle
        double centre = -l.moment().z() / l.downforceN(), length = kitted().shape().lengthM();
        assertTrue(centre > 0 && centre < 0.5 * length, "rear-biased: " + centre + " m from the rear end of " + length);
        // off the ground the floor gives nothing, the wing still does
        RoadAero.Loads air = aero.loads(new Vec3d(-v, 0, 0), 1.225, p -> Double.POSITIVE_INFINITY);
        assertTrue(air.downforceN() > 0 && air.downforceN() < l.downforceN());
    }

    @Test void aMildKitIsAProductionTrackCars() {
        // 0.16.1 (AeroCore 0.35.1's calibration): the refit shop's mild kit on the sample sedan at 200 km/h, floor 0.12 m up.
        // 0.16.0 gave 4.0 kN, 3.0 kN of it from the floor; production track cars make 1.4 (911 GT3 RS 991.2) to 2.4 kN
        // (488 Pista) for the whole car, and the sedan's own body lifts some 0.7 kN.
        BodyKitDesign k = new BodyKitDesign(BodyKitDesign.PAGE, BodyKitDesign.Bumper.SPORT, 0.05, 0.08, 0.06, 0, 10, 0.5, 0.03, 1.5, 0.25, 8, 0.25, 0.15,
                BodyKitDesign.RoofRack.NONE, 0, 0, 0.08, EngineMaterial.CFRP, "");
        KitLayout layout = KitLayout.of(SampleBodies.sedan().withKit(k));
        double v = 200 / 3.6;
        RoadAero.Loads all = layout.aero.loads(new Vec3d(-v, 0, 0), 1.225, p -> 0.12);
        RoadAero.Underbody floor = layout.aero.elements().stream().filter(e -> e instanceof RoadAero.Underbody).map(e -> (RoadAero.Underbody) e).findFirst().orElseThrow();
        double floorN = new RoadAero(List.of(floor)).loads(new Vec3d(-v, 0, 0), 1.225, p -> 0.12).downforceN();
        assertTrue(all.downforceN() > 1000 && all.downforceN() < 2000, "the whole kit: " + all.downforceN());
        assertTrue(floorN > 300 && floorN < 1000, "its floor, dam and splitter: " + floorN);
        // the skirts run between the arches only: they close their gap's share there, not along the whole floor
        assertTrue(floor.sealing() > 0.1 && floor.sealing() < 0.8 * k.sealing(), "sealing " + floor.sealing() + " of " + k.sealing());
    }

    @Test void theShopFitsIt() {
        var kit = Refit.adjustables(SampleBodies.sedan(), Refit.Category.KIT);
        assertTrue(kit.stream().anyMatch(a -> a.param().equals("wingSpanM")));
        assertTrue(kit.stream().anyMatch(a -> a.param().equals("bumper")));
        var b = dev.mcmod.hdfv.assembly.AutoWiring.wire(dev.mcmod.hdfv.assembly.SampleLayouts.sedan());
        var next = Refit.set(b, "body", BodyKitDesign.PAGE, "wingSpanM", "1400");   // mm, as the editor shows it
        assertTrue(((BodyDesign) next.part("body").design()).kit().wing());
        var job = dev.mcmod.hdfv.refit.RefitJob.of(b, next);
        assertEquals(List.of("body"), job.rebuilt());
        assertTrue(job.stockKg().containsKey(dev.mcmod.hdfv.manufacture.Alloy.of(EngineMaterial.GFRP)), "the kit's moulding: " + job.stockKg());
    }
}
