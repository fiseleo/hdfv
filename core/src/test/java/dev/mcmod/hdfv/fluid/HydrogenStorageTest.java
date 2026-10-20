package dev.mcmod.hdfv.fluid;

import dev.mcmod.aerocore.api.Atmosphere;
import dev.mcmod.aerocore.api.FluidState;
import dev.mcmod.aerocore.api.Orientation;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Mount;
import dev.mcmod.aerocore.api.vehicle.PartTypes;
import dev.mcmod.aerocore.api.vehicle.Vehicle;
import dev.mcmod.aerocore.api.vehicle.VehicleBlueprint;
import dev.mcmod.aerocore.api.vehicle.VehicleDynamics;
import dev.mcmod.aerocore.api.vehicle.VehicleEnvironment;
import dev.mcmod.aerocore.api.vehicle.VehicleState;
import dev.mcmod.hdfv.part.FuelTankDesign;
import dev.mcmod.hdfv.part.HdfvParts;
import dev.mcmod.hdfv.part.engine.EngineMaterial;
import dev.mcmod.hdfv.petroleum.Refining;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static dev.mcmod.hdfv.fluid.HydrogenStorage.Store.*;
import static org.junit.jupiter.api.Assertions.*;

/** Hydrogen in hdfv (0.10): its two forms, where each is kept, how fast the liquid boils off, the reformer's hydrogen. */
class HydrogenStorageTest {
    static { HdfvParts.register(); }

    private static boolean invalid(FuelTankDesign d, String parameter) {
        return d.check().stream().anyMatch(i -> i.kind() == DesignIssue.Kind.INVALID && i.parameter().equals(parameter));
    }

    @Test void theTwoFormsAreFuelsWithTheirDensities() {
        assertEquals(70.85, HdfvFluids.of(HdfvFluids.LIQUID_HYDROGEN).densityKgPerM3(), 0.01);
        // 700 bar at 15 C: about 40 kg/m^3 (NIST 40.2)
        assertEquals(40.2, HdfvFluids.of(HdfvFluids.HYDROGEN).densityKgPerM3(), 0.03 * 40.2);
        assertEquals("hydrogen", FuelGrades.of(HdfvFluids.LIQUID_HYDROGEN).family());
        assertEquals("hydrogen", FuelGrades.of(HdfvFluids.HYDROGEN).family());
        assertFalse(FuelGrades.ambient().contains(HdfvFluids.HYDROGEN));
        assertFalse(FuelGrades.ambient().contains(HdfvFluids.LIQUID_HYDROGEN));
        assertTrue(FuelGrades.ambient().contains("diesel"));
    }

    @Test void eachStoreTakesWhatItCanHold() {
        String gas = HdfvFluids.HYDROGEN, liquid = HdfvFluids.LIQUID_HYDROGEN;
        assertTrue(HydrogenStorage.takes(CYLINDERS, gas));
        for (HydrogenStorage.Store s : List.of(TANK, STORAGE_TANK, CAN, CRYOGENIC_TANK)) { assertFalse(HydrogenStorage.takes(s, gas), s + " refuses 700 bar"); }
        for (HydrogenStorage.Store s : List.of(TANK, STORAGE_TANK, CRYOGENIC_TANK, PIPE)) { assertTrue(HydrogenStorage.takes(s, liquid), s + " holds the liquid"); }
        assertFalse(HydrogenStorage.takes(CAN, liquid));
        assertFalse(HydrogenStorage.takes(CYLINDERS, liquid));
        assertFalse(HydrogenStorage.takes(CYLINDERS, "diesel"));
        assertTrue(HydrogenStorage.takes(TANK, "diesel") && HydrogenStorage.takes(CAN, "diesel"));
    }

    @Test void liquidHydrogenBoilsAwayInABareTankAndKeepsInACryogenicOne() {
        double full = HdfvFluids.of(HdfvFluids.LIQUID_HYDROGEN).densityKgPerM3();
        double bare = full / HydrogenStorage.blockBoilOffKgPerS(TANK), kept = HydrogenStorage.blockBoilOffKgPerS(CRYOGENIC_TANK) * 86400 / full;
        assertTrue(bare < 3600, "a bare cubic metre boils away within the hour: " + bare + " s");
        assertTrue(kept < 0.01, "a cryogenic block keeps all but a percent a day: " + kept);
        assertEquals(0, HydrogenStorage.blockBoilOffKgPerS(CYLINDERS));
    }

    @Test void theElectricityIsWhatPlantsSpend() {
        // compressing to 700 bar 2.2-2.8 kWh/kg (filling stations); liquefying 11.9 kWh/kg (Leuna)
        assertEquals(2.4, HydrogenStorage.COMPRESSION_J_PER_KG / 3.6e6, 0.3);
        assertEquals(11.9, HydrogenStorage.LIQUEFACTION_J_PER_KG / 3.6e6, 0.2);
    }

    @Test void aMiraiLikeCylinderAndABmwLikeTankWeighAndKeepLikeTheirs() {
        FuelTankDesign c = FuelTankDesign.hydrogenCylinder();
        assertTrue(c.check().stream().noneMatch(i -> i.kind() == DesignIssue.Kind.INVALID), c.check().toString());
        // Toyota Mirai (2014): 60 and 62.4 l cylinders, 87.5 kg the pair with fittings
        assertEquals(43.75, c.massKg(), 0.2 * 43.75);
        assertEquals(60, c.capacityM3() * 1000, 0.1 * 60);
        assertEquals(c.capacityM3() * HdfvFluids.of(HdfvFluids.HYDROGEN).densityKgPerM3(), c.compile().tanks().get(0).initialKg(), 1e-9);
        FuelTankDesign t = FuelTankDesign.liquidHydrogenTank();
        assertTrue(t.check().stream().noneMatch(i -> i.kind() == DesignIssue.Kind.INVALID), t.check().toString());
        // BMW Hydrogen 7: about 170 l and 8 kg of liquid, boiling off 1-3 % a day once its pressure rises
        assertEquals(170, t.capacityM3() * 1000, 0.15 * 170);
        double perDay = t.boilOffKgPerS() * 86400 / (t.capacityM3() * t.fuelDensity());
        assertTrue(perDay > 0.005 && perDay < 0.04, "boil-off " + perDay * 100 + " % a day");
        // round trips
        assertEquals(c, PartTypes.read(c.toJson(), "t"));
        assertEquals(t, PartTypes.read(t.toJson(), "t"));
    }

    @Test void hydrogenNeedsItsKindOfTank() {
        FuelTankDesign car = FuelTankDesign.car("gasoline");
        assertTrue(invalid(new FuelTankDesign("t", 0.6, 0.4, 0.25, 0.0015, EngineMaterial.STEEL_1045, HdfvFluids.HYDROGEN, 1), "kind"));
        assertTrue(invalid(new FuelTankDesign("t", 0.6, 0.4, 0.25, 0.0015, EngineMaterial.STEEL_1045, HdfvFluids.LIQUID_HYDROGEN, 1), "kind"));
        FuelTankDesign c = FuelTankDesign.hydrogenCylinder();
        assertTrue(invalid(new FuelTankDesign(c.name(), c.lengthM(), c.widthM(), c.heightM(), c.wallM(), c.material(), "diesel", 1, c.kind(), c.insulation(),
                c.insulationM(), c.pressurePa(), c.safetyFactor()), "fuel"));
        // an old blueprint's tank reads back as an ordinary one
        assertEquals(car, PartTypes.read(Map.of("type", FuelTankDesign.TYPE, "name", "fuel tank", "lengthM", 0.6, "widthM", 0.4, "heightM", 0.25, "wallM", 0.0015,
                "material", "steel_1045", "fuel", "gasoline", "fill", 1.0), "t"));
        // a body's integral tanks take no hydrogen
        var settings = dev.mcmod.hdfv.part.body.SampleBodies.runabout().settings();
        var hydrogen = new dev.mcmod.hdfv.part.body.BodySettingsDesign(settings.name(), settings.purpose(), settings.noseBluntness(), settings.hydro(),
                settings.loadFactor(), settings.occupantKg(), settings.cargoDensityKgM3(), settings.cargoFill(), settings.fuelFill(), HdfvFluids.LIQUID_HYDROGEN,
                settings.extraDragAreaM2(), settings.designSpeedMps(), settings.category(), settings.supportedKg(), settings.supportedAt(), settings.paintColor(),
                settings.accentColor(), settings.lowerColor());
        assertTrue(hydrogen.check().stream().anyMatch(i -> i.kind() == DesignIssue.Kind.INVALID && i.parameter().equals("fuel")));
    }

    @Test void aCryogenicTankVentsWhatBoilsOff() {
        FuelTankDesign t = FuelTankDesign.liquidHydrogenTank();
        Vehicle v = new VehicleBlueprint("tank", List.of(new VehicleBlueprint.Entry("tank", t, Mount.ORIGIN)), List.of()).compile();
        VehicleState st = v.newState(Vec3d.ZERO, Orientation.IDENTITY, 0.05);
        double before = st.tankKg(0);
        VehicleEnvironment env = VehicleEnvironment.calm(-100, FluidState.FRESH_WATER_20C, Atmosphere.atAltitude(0, Vec3d.ZERO).fluid());
        for (int i = 0; i < 2000; i++) { VehicleDynamics.step(v, st, env, Map.of(), Vec3d.ZERO, List.of(), null, i * 0.05, 0.05); }
        assertEquals(t.boilOffKgPerS() * 100, before - st.tankKg(0), 0.01 * t.boilOffKgPerS() * 100, "100 s of boil-off vented");
    }

    @Test void theReformersHydrogenIsAProductOnlyWhenRouted() {
        Refining.Yield r = Refining.REFORMER;
        assertEquals(0.025, r.products().get("hydrogen"), 1e-12);
        assertEquals(0, r.imbalance(), 1e-12);
        Refining.Yield flared = r.flaring("hydrogen");
        assertNull(flared.products().get("hydrogen"));
        assertEquals(r.burnt() + 0.025, flared.burnt(), 1e-12);
        assertEquals(0, flared.imbalance(), 1e-12);
        assertEquals(r.gridJPerKg(), flared.gridJPerKg(), 0, "unrouted: as before 0.10");
        Refining.Yield compressed = r.compressing("hydrogen", HydrogenStorage.COMPRESSION_J_PER_KG);
        assertEquals(r.gridJPerKg() + 0.025 * HydrogenStorage.COMPRESSION_J_PER_KG, compressed.gridJPerKg(), 1e-6);
        assertTrue(Refining.ROUTED_ONLY.contains("hydrogen"));
        assertNull(Refining.NAPHTHA_CRACKER.products().get("hydrogen"), "the cracker burns its hydrogen in its furnace");
    }
}
