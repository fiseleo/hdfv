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
 * 2026-11-22: the operator cab (OperatorCabDesign): its samples' masses against makers' cabs, the protection's weight,
 * its glazing, its JSON, the compiled box and the checks.
 */
class OperatorCabDesignTest {
    private static boolean invalid(PartDesign d, String parameter) {
        return d.check().stream().anyMatch(i -> i.kind() == DesignIssue.Kind.INVALID && i.parameter().equals(parameter));
    }

    private static boolean extrapolated(PartDesign d, String parameter) {
        return d.check().stream().anyMatch(i -> i.kind() == DesignIssue.Kind.EXTRAPOLATION && i.parameter().equals(parameter));
    }

    @Test void theSamplesWeighWhatMakersCabsDo() {
        OperatorCabDesign excavator = SampleMachinery.excavatorCab(), mini = SampleMachinery.miniExcavatorCab(), loader = SampleMachinery.loaderCab();
        for (OperatorCabDesign d : List.of(excavator, mini, loader)) {
            assertTrue(d.check().isEmpty(), d.name() + ": " + d.check());
            System.out.printf(Locale.ROOT, "hdfv cab %s: %.0f kg, %.2f m2 of glass, %s%n", d.name(), d.massKg(), d.glassM2(), d.massBreakdownKg());
            double sum = d.massBreakdownKg().values().stream().mapToDouble(Double::doubleValue).sum();
            assertEquals(d.massKg(), sum, 1e-9);
            assertEquals(d, OperatorCabDesign.read(Fields.of(Json.parse(Json.write(d.toJson())), "design")), "JSON round trip");
        }
        // a 20 t excavator's ROPS cab some 0.6-0.8 t, a mini excavator's 0.25-0.4 t, an 18 t loader's ROPS/FOPS cab 0.9-1.3 t
        assertEquals(709, excavator.massKg(), 10);
        assertEquals(331, mini.massKg(), 10);
        assertEquals(1075, loader.massKg(), 15);
        // the glass: the front to the floor, the sides and rear above the sill
        assertEquals(5.12, excavator.glassM2(), 0.01);
    }

    @Test void protectionWeighs() {
        OperatorCabDesign rops = SampleMachinery.excavatorCab();
        OperatorCabDesign plain = new OperatorCabDesign("plain", rops.lengthM(), rops.widthM(), rops.heightM(), rops.frontRakeDeg(), OperatorCabDesign.Protection.CAB, rops.doorSide());
        OperatorCabDesign fops = new OperatorCabDesign("fops", rops.lengthM(), rops.widthM(), rops.heightM(), rops.frontRakeDeg(), OperatorCabDesign.Protection.ROPS_FOPS, rops.doorSide());
        assertTrue(plain.massKg() < rops.massKg() && rops.massKg() < fops.massKg());
        assertEquals(rops.frameM() * (OperatorCabDesign.ROPS_FRAME_KG_PER_M - OperatorCabDesign.CAB_FRAME_KG_PER_M), rops.massKg() - plain.massKg(), 1e-9);
        assertEquals(OperatorCabDesign.FOPS_KG_PER_M2 * rops.lengthM() * rops.widthM(), fops.massKg() - rops.massKg(), 1e-9);
    }

    @Test void itCompilesToAClosedBoxOfItsSizeAndMass() {
        OperatorCabDesign d = SampleMachinery.loaderCab();
        Part p = d.compile();
        assertInstanceOf(BoxPart.class, p);
        assertEquals(d.massKg(), p.mass().massKg(), 1e-9);
        assertEquals(new Vec3d(1.7, 1.9, 1.4), d.sizeM());
        assertFalse(p.collision().isEmpty(), "contact spheres round it");
        assertTrue(p.conditions().isEmpty(), "it wears nothing yet");
    }

    @Test void theChecksStopWhatIsNoCab() {
        OperatorCabDesign d = SampleMachinery.excavatorCab();
        OperatorCabDesign flat = new OperatorCabDesign("flat", d.lengthM(), d.widthM(), 0, d.frontRakeDeg(), d.protection(), d.doorSide());
        assertTrue(invalid(flat, "heightM"));
        assertThrows(DesignException.class, flat::compile);
        assertTrue(invalid(new OperatorCabDesign("over", d.lengthM(), d.widthM(), d.heightM(), 50, d.protection(), d.doorSide()), "frontRakeDeg"));
        assertTrue(invalid(new OperatorCabDesign("back", d.lengthM(), d.widthM(), d.heightM(), -5, d.protection(), d.doorSide()), "frontRakeDeg"));
        // beyond makers' cabs: extrapolated, still built
        OperatorCabDesign big = new OperatorCabDesign("big", 3.0, 2.2, 2.6, 35, d.protection(), d.doorSide());
        for (String p : List.of("lengthM", "widthM", "heightM", "frontRakeDeg")) { assertTrue(extrapolated(big, p), p); }
        assertNotNull(big.compile());
    }
}
