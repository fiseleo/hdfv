package dev.mcmod.hdfv.part.engine;

import dev.mcmod.aerocore.api.engine.FreeTurbine;
import dev.mcmod.aerocore.api.engine.Turbofan;
import dev.mcmod.aerocore.api.engine.Turbojet;
import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Part;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.hdfv.design.Bench;
import dev.mcmod.hdfv.design.Designs;
import dev.mcmod.hdfv.design.RecordEditor;
import dev.mcmod.hdfv.part.HdfvParts;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The turbofan, turboprop and turboshaft built from components: samples against the engines they are modelled on, checks, bench runs. */
class TurbineEnginesTest {
    static { HdfvParts.register(); }

    private static final Turbojet.Flight STATIC = Turbojet.Flight.sea(0);

    private static List<PartDesign> samples() {
        return List.of(SampleEngines.turbofan(), SampleEngines.businessTurbofan(), SampleEngines.turboprop(), SampleEngines.turboshaft());
    }

    private static boolean invalid(PartDesign d, String parameter) {
        return d.check().stream().anyMatch(i -> i.kind() == DesignIssue.Kind.INVALID && i.parameter().equals(parameter));
    }
    private static <D extends PartDesign> D with(D d, String page, Record component) {
        @SuppressWarnings("unchecked") D out = (D) Designs.withPage(d, page, component);
        return out;
    }

    @Test void theSamplesAreCleanDesignsAndKeepTheirJson() {
        for (PartDesign d : samples()) {
            assertEquals(List.of(), d.check(), d.name() + " has no issues, not even extrapolations");
            assertEquals(d, Designs.read(Designs.write(d)), d.name());
            assertEquals(List.copyOf(Designs.components(d).keySet()), Designs.pages(d));
        }
        assertEquals(TurbofanDesign.TYPE, SampleEngines.turbofan().type());
        assertEquals(ShaftTurbineDesign.TURBOPROP, SampleEngines.turboprop().type());
        assertEquals(ShaftTurbineDesign.TURBOSHAFT, SampleEngines.turboshaft().type());
    }

    @Test void theSamplesPerformAsTheEnginesTheyAreModelledOn() {
        TurbofanDesign cfm = SampleEngines.turbofan();
        Turbofan.Point p = cfm.sizing().point();
        assertEquals(Turbofan.size(cfm.cycle(), STATIC).point().thrustN(), p.thrustN(), 1e-9);
        // CFM56-7B27: 121.4 kN, bypass ratio 5.1, overall pressure ratio 32.6 (Boeing 737NG data), static TSFC 0.33-0.40 lb/(lbf h)
        assertEquals(121.4e3, p.thrustN(), 0.05 * 121.4e3);
        assertEquals(32.6, p.overallPressureRatio(), 0.1);
        assertTrue(p.tsfc() > 9.3e-6 && p.tsfc() < 11.3e-6, "TSFC " + p.tsfc());
        // FJ44-1A: 8.45 kN (1,900 lbf)
        assertEquals(8.45e3, SampleEngines.businessTurbofan().sizing().point().thrustN(), 0.1 * 8.45e3);
        // PT6A-27: 680 shp (507 kW) at the propeller shaft, 2,200 rpm, 386 g/kWh (0.603 lb per equivalent shp-hour, 90 lbf of jet thrust)
        ShaftTurbineDesign pt6 = SampleEngines.turboprop();
        Bench.ShaftPoint full = Bench.powerSweep(pt6, STATIC, 1).get(0);
        assertEquals(507e3, full.powerW(), 0.08 * 507e3);
        assertEquals(386, full.psfcGPerKWh(), 0.08 * 386);
        assertEquals(2200, pt6.outputRpm(), 1e-9);
        // T700-GE-701C: 1,890 shp (1,409 kW) at 0.462 lb/(shp h) (281 g/kWh), output at 20,900 rpm
        ShaftTurbineDesign t700 = SampleEngines.turboshaft();
        Bench.ShaftPoint t = Bench.powerSweep(t700, STATIC, 1).get(0);
        assertEquals(1409e3, t.powerW(), 0.08 * 1409e3);
        assertEquals(281, t.psfcGPerKWh(), 0.1 * 281);
        FreeTurbine.Point design = t700.sizing().point();
        assertEquals(design.shaftTorqueNm(), t700.designOutputTorque(design), 1e-9, "a direct drive loses nothing");
    }

    @Test void massesComeWithinFifteenPercentOfTheRealEngines() {
        // CFM56-7B 2,366 kg; FJ44-1A 202 kg (445 lb); PT6A-27 131 kg (289 lb); T700-GE-701C 207 kg (456 lb)
        assertEquals(2366, SampleEngines.turbofan().totalMassKg(), 0.15 * 2366);
        assertEquals(202, SampleEngines.businessTurbofan().totalMassKg(), 0.15 * 202);
        assertEquals(131, SampleEngines.turboprop().totalMassKg(), 0.15 * 131);
        assertEquals(207, SampleEngines.turboshaft().totalMassKg(), 0.15 * 207);
        // model-size cores carry next to none of a certified engine's fixed equipment
        TurbofanDesign small = SampleEngines.businessTurbofan();
        TurbofanDesign tiny = with(small, "compressor", RecordEditor.with(small.compressor(), "airflowKgPerS", 0.2));
        assertTrue(tiny.accessoriesMassKg() < 2, "accessories of a 0.2 kg/s core: " + tiny.accessoriesMassKg());
    }

    @Test void checksCatchWhatCannotWork() {
        TurbofanDesign cfm = SampleEngines.turbofan();
        // the fan turbine's cooling air is not modelled
        TurbineDesign lpt = cfm.lpTurbine();
        assertTrue(invalid(with(cfm, "lpTurbine", RecordEditor.with(lpt, "coolingBleed", 0.05)), "lpTurbine.coolingBleed"));
        // a fan at half its speed cannot do the work in one stage
        assertTrue(invalid(with(cfm, "fan", RecordEditor.with(cfm.fan(), "designRpm", 0.5 * cfm.fan().designRpm())), "fan.designRpm"));
        // twice the core flow chokes the core compressor's face behind the booster
        assertTrue(invalid(with(cfm, "compressor", RecordEditor.with(cfm.compressor(), "airflowKgPerS", 2.5 * cfm.compressor().airflowKgPerS())), "compressor.tipDiameterM"));
        // a fan too weak to push its bypass air into the mixer against the core stream
        TurbofanDesign small = SampleEngines.businessTurbofan();
        assertTrue(invalid(with(small, "fan", RecordEditor.with(small.fan(), "pressureRatio", 1.2)), "cycle"));
        ShaftTurbineDesign pt6 = SampleEngines.turboprop();
        // a gearbox rated below the engine's torque
        assertTrue(invalid(with(pt6, "gearbox", RecordEditor.with(pt6.gearbox(), "ratedTorqueNm", 1500.0)), "gearbox.ratedTorqueNm"));
        // fifteen to one in one mesh is past what a gear pair does
        assertTrue(invalid(with(pt6, "gearbox", RecordEditor.with(pt6.gearbox(), "meshes", 1)), "gearbox.meshes"));
        // without the gearbox a turboprop's shaft turns far faster than propellers do
        ShaftTurbineDesign direct = with(pt6, "gearbox", new ReductionGearboxDesign("g", ReductionGearboxDesign.Kind.PLANETARY, 1, 0, 300, EngineMaterial.STEEL_4340, Double.NaN));
        assertTrue(direct.check().stream().anyMatch(i -> i.parameter().equals("outputRpm")));
        // an aluminium power turbine melts in the gas between the turbines
        ShaftTurbineDesign t700 = SampleEngines.turboshaft();
        assertTrue(invalid(with(t700, "powerTurbine", RecordEditor.with(t700.powerTurbine(), "material", EngineMaterial.ALUMINIUM_2618)), "powerTurbine.material"));
    }

    @Test void compiledEnginesHaveTheirRotorsOutputFirst() {
        Part fan = SampleEngines.turbofan().compile();
        assertEquals(2, fan.rotors().size());
        assertEquals(SampleEngines.turbofan().lowInertia(), fan.rotors().get(0).inertiaKgM2(), 1e-12, "the fan's spool first");
        assertEquals(List.of("throttle", "ignition"), fan.inputs());
        ShaftTurbineDesign pt6 = SampleEngines.turboprop();
        Part prop = pt6.compile();
        assertEquals(pt6.outputInertia(), prop.rotors().get(0).inertiaKgM2(), 1e-12, "the output shaft first: a vehicle's shaft takes it to the propeller");
        // the power turbine's inertia seen through a 15:1 gearbox dominates the output's
        assertTrue(pt6.outputInertia() > 100 * pt6.powerTurbine().rotorInertia());
    }

    @Test void benchSweepsEndAtTheDesignPointAndTheFreeTurbineGivesTwiceItsTorqueHeld() {
        for (TurbofanDesign e : List.of(SampleEngines.turbofan(), SampleEngines.businessTurbofan())) {
            List<Bench.FanPoint> sweep = Bench.throttleSweep(e, STATIC, 9);
            assertEquals(e.control().idleFraction(), sweep.get(0).fanSpeed(), 1e-9);
            assertEquals(e.sizing().point().thrustN(), sweep.get(8).thrustN(), 0.01 * e.sizing().point().thrustN(), e.name());
            for (int i = 1; i < sweep.size(); i++) {
                assertTrue(sweep.get(i).thrustN() > sweep.get(i - 1).thrustN() && sweep.get(i).coreSpeed() > sweep.get(i - 1).coreSpeed(), e.name() + " at " + i);
            }
            assertTrue(sweep.get(0).coreSpeed() > 0.45 && sweep.get(0).coreSpeed() < 0.7, e.name() + ": the core idles at about half speed, " + sweep.get(0).coreSpeed());
        }
        for (ShaftTurbineDesign e : List.of(SampleEngines.turboprop(), SampleEngines.turboshaft())) {
            // driving the bench's propeller-like load the power turbine settles where its torque meets the load's: slow at idle
            // (as a propeller at ground idle), at its design speed at full power, and the power is positive throughout
            List<Bench.ShaftPoint> sweep = Bench.powerSweep(e, STATIC, 6);
            assertTrue(sweep.getFirst().powerW() > 0 && sweep.getFirst().powerTurbineSpeed() < 0.6, e.name() + " at idle: " + sweep.getFirst());
            assertEquals(1, sweep.getLast().powerTurbineSpeed(), 0.03, e.name());
            for (int i = 1; i < sweep.size(); i++) {
                assertTrue(sweep.get(i).powerW() > sweep.get(i - 1).powerW() && sweep.get(i).powerTurbineSpeed() > sweep.get(i - 1).powerTurbineSpeed(), e.name() + " at " + i);
            }
            List<Bench.TorquePoint> curve = Bench.torqueCurve(e, STATIC, 7);
            double design = sweep.getLast().torqueNm();
            assertEquals(0, curve.getFirst().powerW(), 1e-9);
            assertEquals(2, curve.getFirst().torqueNm() / design, 0.1, e.name() + ": stall torque");
            for (int i = 1; i < curve.size(); i++) { assertTrue(curve.get(i).torqueNm() < curve.get(i - 1).torqueNm(), e.name() + " torque falls with speed at " + i); }
        }
    }

    @Test void theyStartIdleAndRunUpOnTheStand() {
        for (TurbofanDesign e : List.of(SampleEngines.turbofan(), SampleEngines.businessTurbofan())) {
            Bench.JetRun r = Bench.startAndRunUp(e);
            // as real engines start: light-off within seconds, idle after tens of seconds
            assertTrue(r.lightOffS() > 0.5 && r.lightOffS() < 10 && r.idleS() > 15 && r.idleS() < 60, e.name() + " light-off " + r.lightOffS() + " idle " + r.idleS());
            double runUp = r.fullThrustS() - r.throttleS();
            // an airliner's engines must go from 15 % to 95 % thrust within 5 s (14 CFR 33.73); from idle a little longer
            assertTrue(runUp > 1 && runUp < 8, e.name() + " run-up " + runUp);
        }
        for (ShaftTurbineDesign e : List.of(SampleEngines.turboprop(), SampleEngines.turboshaft())) {
            Bench.ShaftRun r = Bench.startAndRunUp(e);
            assertTrue(r.lightOffS() > 0.5 && r.lightOffS() < 10 && r.idleS() > 15 && r.idleS() < 60, e.name() + " light-off " + r.lightOffS() + " idle " + r.idleS());
            double runUp = r.fullPowerS() - r.throttleS();
            assertTrue(runUp > 0.5 && runUp < 15, e.name() + " run-up " + runUp);
            double last = r.powerTurbineSpeed()[r.powerTurbineSpeed().length - 1];
            assertTrue(last > 0.8 && last < 1.06, e.name() + ": the load comes up towards the power turbine's design speed, " + last);
        }
    }
}
