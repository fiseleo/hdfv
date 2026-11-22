package dev.mcmod.hdfv.part.machine;

import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.vehicle.DesignException;
import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;
import dev.mcmod.aerocore.api.vehicle.Json;
import dev.mcmod.aerocore.api.vehicle.Part;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.part.BoxPart;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 2026-11-22: the engine hood (EngineHoodDesign): its samples' masses from their sheet metal, its shape (the top falling
 * toward the grille, the rounded tail), its JSON, the compiled box and the checks.
 */
class EngineHoodDesignTest {
    private static boolean invalid(PartDesign d, String parameter) {
        return d.check().stream().anyMatch(i -> i.kind() == DesignIssue.Kind.INVALID && i.parameter().equals(parameter));
    }

    private static boolean extrapolated(PartDesign d, String parameter) {
        return d.check().stream().anyMatch(i -> i.kind() == DesignIssue.Kind.EXTRAPOLATION && i.parameter().equals(parameter));
    }

    private static EngineHoodDesign with(EngineHoodDesign d, double drop, double tail, EngineHoodDesign.Grille grille, double stack) {
        return new EngineHoodDesign(d.name(), d.lengthM(), d.widthM(), d.heightM(), drop, tail, grille, stack);
    }

    @Test void theSamplesWeighWhatTheirSheetMetalDoes() {
        EngineHoodDesign excavator = SampleMachinery.excavatorHood(), mini = SampleMachinery.miniExcavatorHood(), loader = SampleMachinery.loaderHood();
        for (EngineHoodDesign d : List.of(excavator, mini, loader)) {
            assertTrue(d.check().isEmpty(), d.name() + ": " + d.check());
            System.out.printf(Locale.ROOT, "hdfv hood %s: %.1f kg, %.2f m2 of louvres, %s%n", d.name(), d.massKg(), d.grilleM2(), d.massBreakdownKg());
            double sum = d.massBreakdownKg().values().stream().mapToDouble(Double::doubleValue).sum();
            assertEquals(d.massKg(), sum, 1e-9);
            assertEquals(d, EngineHoodDesign.read(Fields.of(Json.parse(Json.write(d.toJson())), "design")), "JSON round trip");
        }
        // 2 mm panels, a channel frame, louvres, lining, a stack: a 20 t excavator's rear covers some 0.4-0.5 t
        assertEquals(457.6, excavator.massKg(), 0.5);
        assertEquals(116.5, mini.massKg(), 0.5);
        assertEquals(301.6, loader.massKg(), 0.5);
        // a hood without a stack has no stack's piece
        assertFalse(mini.massBreakdownKg().containsKey("hood_stack"));
        // the excavator's tail rounded by 0.9 m: 0.35 m^2 less to cover than the rectangle
        assertEquals(2.6 * 2.5 - 0.348, excavator.planM2(), 0.001);
    }

    @Test void theTopFallsTowardTheGrilleAndTheTailIsRound() {
        EngineHoodDesign loader = SampleMachinery.loaderHood();
        double l = loader.lengthM(), h = loader.heightM();
        // a rear grille: full height at the front, the drop off at the rear
        assertEquals(h, loader.topAt(l / 2), 1e-12);
        assertEquals(h - loader.topDropM(), loader.topAt(-l / 2), 1e-12);
        // a front grille (a dozer's): the other way round
        EngineHoodDesign dozer = with(loader, 0.25, 0.15, EngineHoodDesign.Grille.FRONT, 0.5);
        assertEquals(h - 0.25, dozer.topAt(l / 2), 1e-12);
        assertEquals(h, dozer.topAt(-l / 2), 1e-12);
        // the excavator's tail: narrowest at its very end, full width a tail radius in
        EngineHoodDesign excavator = SampleMachinery.excavatorHood();
        double el = excavator.lengthM(), w = excavator.widthM(), r = excavator.tailRadiusM();
        assertEquals(w / 2 - r, excavator.halfWidthAt(-el / 2), 1e-12);
        assertEquals(w / 2, excavator.halfWidthAt(-el / 2 + r), 1e-12);
        assertEquals(w / 2, excavator.halfWidthAt(el / 2), 1e-12);
        assertTrue(excavator.halfWidthAt(-el / 2 + 0.3) < w / 2);
    }

    @Test void itCompilesToABoxOfItsSizeAndMass() {
        EngineHoodDesign d = SampleMachinery.excavatorHood();
        Part p = d.compile();
        assertInstanceOf(BoxPart.class, p);
        assertEquals(d.massKg(), p.mass().massKg(), 1e-9);
        assertEquals(new Vec3d(2.6, 1.3, 2.5), d.sizeM());
        assertFalse(p.collision().isEmpty(), "contact spheres round it");
        assertTrue(p.conditions().isEmpty(), "it wears nothing yet");
    }

    @Test void theChecksStopWhatIsNoHood() {
        EngineHoodDesign d = SampleMachinery.excavatorHood();
        EngineHoodDesign flat = new EngineHoodDesign("flat", d.lengthM(), d.widthM(), 0, 0, 0, d.grille(), 0);
        assertTrue(invalid(flat, "heightM"));
        assertThrows(DesignException.class, flat::compile);
        assertTrue(invalid(with(d, 0.9, d.tailRadiusM(), d.grille(), d.stackM()), "topDropM"), "falling more than 0.6 of its height");
        assertTrue(invalid(with(d, -0.1, d.tailRadiusM(), d.grille(), d.stackM()), "topDropM"));
        assertTrue(invalid(with(d, 0, 1.4, d.grille(), d.stackM()), "tailRadiusM"), "a tail rounder than half its width");
        assertTrue(invalid(with(d, 0, d.tailRadiusM(), d.grille(), -0.2), "stackM"));
        // beyond makers' machines: extrapolated, still built
        EngineHoodDesign big = new EngineHoodDesign("big", 4.5, 3.8, 2.2, 0.7, 1.8, EngineHoodDesign.Grille.REAR, 1.8);
        for (String p : List.of("lengthM", "widthM", "heightM", "topDropM", "tailRadiusM", "stackM")) { assertTrue(extrapolated(big, p), p); }
        assertTrue(big.check().stream().noneMatch(i -> i.kind() == DesignIssue.Kind.INVALID), big.check().toString());
        assertNotNull(big.compile());
    }
}
