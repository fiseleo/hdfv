package dev.mcmod.hdfv.refit;

import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.VehicleBlueprint;
import dev.mcmod.hdfv.assembly.AssemblyPlan;
import dev.mcmod.hdfv.assembly.AutoWiring;
import dev.mcmod.hdfv.assembly.SampleLayouts;
import dev.mcmod.hdfv.design.Designs;
import dev.mcmod.hdfv.part.HdfvParts;
import dev.mcmod.hdfv.part.body.BodyDesign;
import dev.mcmod.hdfv.part.drive.GearboxDesign;
import dev.mcmod.hdfv.part.engine.PistonEngineDesign;
import dev.mcmod.hdfv.part.wheel.AxleDesign;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

/** 0.16: a whole vehicle scaled by geometric similarity at the same speeds (Scaling). */
class ScaleTest {
    static { HdfvParts.register(); }

    /** Every numeric component reachable from every kind's new design and every sample, as "Owner.name" without a rule. */
    @Test void everyNumericParameterHasARule() {
        Set<String> missing = new TreeSet<>();
        for (Designs.Kind k : Designs.KINDS) { walk(k.fresh().get(), missing); }
        for (Designs.Sample s : Designs.SAMPLES) { walk(s.design().get(), missing); }
        assertEquals(Set.of(), missing, "parameters Scaling has no rule for");
    }

    private static void walk(Object o, Set<String> missing) {
        if (o instanceof List<?> l) { for (Object x : l) { walk(x, missing); } return; }
        if (!(o instanceof Record r) || !r.getClass().getName().startsWith("dev.mcmod.hdfv.")) { return; }
        if (r instanceof dev.mcmod.hdfv.part.engine.EngineMaterial) { return; }
        for (RecordComponent c : r.getClass().getRecordComponents()) {
            Class<?> t = c.getType();
            if ((t == double.class || t == double[].class) && Scaling.exponent(r.getClass(), c.getName()) == null) {
                missing.add(r.getClass().getSimpleName() + "." + c.getName());
            }
            try {
                c.getAccessor().setAccessible(true);
                walk(c.getAccessor().invoke(r), missing);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    @Test void oneIsTheSameVehicle() {
        for (var e : SampleLayouts.all().entrySet()) {
            VehicleBlueprint b = e.getValue(), same = Scaling.scaled(b, 1);
            assertEquals(dev.mcmod.aerocore.api.vehicle.Json.write(b.toJson()), dev.mcmod.aerocore.api.vehicle.Json.write(same.toJson()), e.getKey());
        }
        for (Designs.Sample s : Designs.SAMPLES) {
            PartDesign d = s.design().get();
            assertEquals(Designs.write(d), Designs.write(Scaling.scaled(d, 1)), s.id());
        }
    }

    @Test void aSedanAFifthBigger() {
        double s = 1.2;
        VehicleBlueprint sedan = SampleLayouts.sedan(), big = Scaling.scaled(sedan, s);
        BodyDesign body = (BodyDesign) sedan.part("body").design(), bigBody = (BodyDesign) big.part("body").design();
        assertEquals(s * body.shape().particulars().lengthM(), bigBody.shape().particulars().lengthM(), 1e-9, "the body's length");
        PistonEngineDesign engine = (PistonEngineDesign) sedan.part("engine").design(), bigEngine = (PistonEngineDesign) big.part("engine").design();
        assertEquals(s * s * s * engine.displacement(), bigEngine.displacement(), 1e-9 * engine.displacement(), "displacement as s^3");
        assertEquals(engine.compressionRatio(), bigEngine.compressionRatio(), 1e-9, "the compression ratio stays");
        assertEquals(engine.ignition().revLimitRpm() / s, bigEngine.ignition().revLimitRpm(), 1e-9, "the same piston speed: rpm 1/s");
        GearboxDesign box = (GearboxDesign) sedan.part("gearbox").design(), bigBox = (GearboxDesign) big.part("gearbox").design();
        assertArrayEquals(box.gears().forwardRatios(), bigBox.gears().forwardRatios(), "the same gears");
        AxleDesign axle = (AxleDesign) sedan.part("front_axle").design(), bigAxle = (AxleDesign) big.part("front_axle").design();
        assertEquals(s * axle.layout().trackM(), bigAxle.layout().trackM(), 1e-12);
        assertEquals(s * axle.tyre().rimDiameterIn(), bigAxle.tyre().rimDiameterIn(), 1e-12);
        assertEquals(s * s * axle.suspension().springRateNPerM(), bigAxle.suspension().springRateNPerM(), 1e-6);
        assertEquals(s * axle.tyre().pressurePa(), bigAxle.tyre().pressurePa(), 1e-6, "the tyre carries s^3 on s^2 of patch");
        assertEquals(body.settings().occupantKg(), bigBody.settings().occupantKg(), 0, "people do not scale");

        // it is still a vehicle: it compiles and the assembly room's checks pass
        for (VehicleBlueprint.Entry e : big.parts()) {
            assertEquals(List.of(), e.design().check().stream().filter(i -> i.kind() == DesignIssue.Kind.INVALID).toList(), e.id());
        }
        AssemblyPlan plan = AssemblyPlan.of(AutoWiring.wire(big)), before = AssemblyPlan.of(AutoWiring.wire(sedan));
        assertEquals(s * before.lengthM(), plan.lengthM(), 0.1 * before.lengthM(), "about a fifth longer");
        double mass = AutoWiring.wire(sedan).compile().properties().massKg(), bigMass = AutoWiring.wire(big).compile().properties().massKg();
        // each design's own mass estimate: shells and blocks as s^3, the fittings that stay (seats, glass, a gearbox's
        // correlation with torque) slower, so the whole between s^1.5 and s^3
        assertTrue(bigMass > Math.pow(s, 1.5) * mass && bigMass < s * s * s * mass, "mass: " + bigMass / mass);
    }

    @Test void halfACarHasNoRoomForItsPeople() {
        VehicleBlueprint small = Scaling.scaled(SampleLayouts.sedan(), 0.5);
        BodyDesign body = (BodyDesign) small.part("body").design();
        assertTrue(body.check().stream().anyMatch(i -> i.kind() == DesignIssue.Kind.INVALID), "two abreast in a 0.9 m cabin");
        assertFalse(Scaling.validated(0.4));
        assertTrue(Scaling.validated(0.5) && Scaling.validated(2));
        assertThrows(IllegalArgumentException.class, () -> Scaling.scaled(small, 0));
    }

    @Test void scaledValuesAreTidyAndFewerSeatsLetACarShrink() {
        VehicleBlueprint b = AutoWiring.wire(SampleLayouts.sedan());
        BodyDesign big = (BodyDesign) Scaling.scaled(b, 1.3).part("body").design();
        assertEquals(2.34, big.modules().get(0).widthM(), 0, "1.8 m x 1.3, without a binary tail");
        java.util.regex.Pattern tail = java.util.regex.Pattern.compile("\\d\\.\\d{13,}");
        assertEquals(tail.matcher(Designs.write(b.part("body").design())).results().count(), tail.matcher(Designs.write(big)).results().count(),
                "no new 17-digit numbers in the JSON: " + tail.matcher(Designs.write(big)).results().map(java.util.regex.MatchResult::group).toList());
        // five people do not shrink: the sedan's rear cell is full; with three it goes to 0.9
        assertThrows(IllegalArgumentException.class, () -> Refit.rescale(b, 1, 0.9));
        VehicleBlueprint three = Refit.set(b, "body", "module3", "seats", "3");
        VehicleBlueprint smaller = Refit.rescale(three, 1, 0.9);
        assertEquals(List.of(), dev.mcmod.hdfv.assembly.RoomRules.vehicle(AssemblyPlan.of(smaller)));
    }
}
