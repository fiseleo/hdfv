package dev.mcmod.hdfv.part;

import dev.mcmod.aerocore.api.vehicle.PartTypes;
import dev.mcmod.hdfv.part.air.AirPropellerDesign;
import dev.mcmod.hdfv.part.air.RotorDesign;
import dev.mcmod.hdfv.part.air.WingDesign;
import dev.mcmod.hdfv.part.engine.JetEngineDesign;
import dev.mcmod.hdfv.part.engine.PistonEngineDesign;
import dev.mcmod.hdfv.part.engine.ShaftTurbineDesign;
import dev.mcmod.hdfv.part.engine.TurbofanDesign;

/** The part types hdfv adds to AeroCore's registry (PartTypes), so blueprints naming them read back. */
public final class HdfvParts {
    private HdfvParts() {}
    private static boolean registered;

    /** Registers every hdfv part type once (the mod at start-up, tests before reading blueprints). */
    public static synchronized void register() {
        if (registered) { return; }
        PartTypes.register(FloatDesign.TYPE, FloatDesign::read);
        PartTypes.register(HullDesign.TYPE, HullDesign::read);
        PartTypes.register(PlaningHullDesign.TYPE, PlaningHullDesign::read);
        PartTypes.register(PropellerDesign.TYPE, PropellerDesign::read);
        PartTypes.register(ControlSurfaceDesign.TYPE, ControlSurfaceDesign::read);
        PartTypes.register(BoxDesign.TYPE, BoxDesign::read);
        PartTypes.register(MotorDesign.TYPE, MotorDesign::read);
        PartTypes.register(BallastDesign.TYPE, BallastDesign::read);
        PartTypes.register(FuelTankDesign.TYPE, FuelTankDesign::read);
        PartTypes.register(SeatDesign.TYPE, SeatDesign::read);
        PartTypes.register(PistonEngineDesign.TYPE, PistonEngineDesign::read);
        PartTypes.register(JetEngineDesign.TYPE, JetEngineDesign::read);
        PartTypes.register(TurbofanDesign.TYPE, TurbofanDesign::read);
        PartTypes.register(ShaftTurbineDesign.TURBOPROP, ShaftTurbineDesign::readTurboprop);
        PartTypes.register(ShaftTurbineDesign.TURBOSHAFT, ShaftTurbineDesign::readTurboshaft);
        PartTypes.register(WingDesign.TYPE, WingDesign::read);
        PartTypes.register(AirPropellerDesign.TYPE, AirPropellerDesign::read);
        PartTypes.register(RotorDesign.TYPE, RotorDesign::read);
        PartTypes.register(dev.mcmod.hdfv.part.body.BodyDesign.TYPE, dev.mcmod.hdfv.part.body.BodyDesign::read);
        PartTypes.register(dev.mcmod.hdfv.part.chassis.ChassisDesign.TYPE, dev.mcmod.hdfv.part.chassis.ChassisDesign::read);
        PartTypes.register(dev.mcmod.hdfv.part.wheel.AxleDesign.TYPE, dev.mcmod.hdfv.part.wheel.AxleDesign::read);
        PartTypes.register(dev.mcmod.hdfv.part.wheel.LandingGearDesign.TYPE, dev.mcmod.hdfv.part.wheel.LandingGearDesign::read);
        // 0.18: trucks
        PartTypes.register(dev.mcmod.hdfv.part.truck.FifthWheelDesign.TYPE, dev.mcmod.hdfv.part.truck.FifthWheelDesign::read);
        PartTypes.register(dev.mcmod.hdfv.part.truck.LandingLegsDesign.TYPE, dev.mcmod.hdfv.part.truck.LandingLegsDesign::read);
        for (dev.mcmod.hdfv.part.drive.Gearboxes.Kind k : dev.mcmod.hdfv.part.drive.Gearboxes.Kind.values()) {
            PartTypes.register(dev.mcmod.hdfv.part.drive.GearboxDesign.typeOf(k), f -> dev.mcmod.hdfv.part.drive.GearboxDesign.read(k, f));
        }
        PartTypes.register(dev.mcmod.hdfv.part.drive.TransferCaseDesign.TYPE, dev.mcmod.hdfv.part.drive.TransferCaseDesign::read);
        // 0.10: the ramjet family, pulsejets and boosters
        PartTypes.register(dev.mcmod.hdfv.part.engine.RamjetDesign.TYPE, dev.mcmod.hdfv.part.engine.RamjetDesign::read);
        PartTypes.register(dev.mcmod.hdfv.part.engine.ScramjetDesign.TYPE, dev.mcmod.hdfv.part.engine.ScramjetDesign::read);
        PartTypes.register(dev.mcmod.hdfv.part.engine.PulsejetDesign.TYPE, dev.mcmod.hdfv.part.engine.PulsejetDesign::read);
        PartTypes.register(dev.mcmod.hdfv.part.engine.SolidRocketDesign.TYPE, dev.mcmod.hdfv.part.engine.SolidRocketDesign::read);
        PartTypes.register(dev.mcmod.hdfv.part.engine.TurboRamjetDesign.TYPE, dev.mcmod.hdfv.part.engine.TurboRamjetDesign::read);
        // 0.23: the standard components
        PartTypes.register(dev.mcmod.hdfv.part.component.ComponentDesign.TYPE, dev.mcmod.hdfv.part.component.ComponentDesign::read);
        // 0.25: the storage box (roof box, bed box, container)
        PartTypes.register(StorageBoxDesign.TYPE, StorageBoxDesign::read);
        // 0.26: heavy machinery (undercarriage, hydraulics, work links, hooks)
        PartTypes.register(dev.mcmod.hdfv.part.machine.TrackDesign.TYPE, dev.mcmod.hdfv.part.machine.TrackDesign::read);
        PartTypes.register(dev.mcmod.hdfv.part.machine.DrumDesign.TYPE, dev.mcmod.hdfv.part.machine.DrumDesign::read);
        PartTypes.register(dev.mcmod.hdfv.part.machine.OutriggerDesign.TYPE, dev.mcmod.hdfv.part.machine.OutriggerDesign::read);
        PartTypes.register(dev.mcmod.hdfv.part.machine.OperatorCabDesign.TYPE, dev.mcmod.hdfv.part.machine.OperatorCabDesign::read);   // 2026-11-22
        PartTypes.register(dev.mcmod.hdfv.part.machine.EngineHoodDesign.TYPE, dev.mcmod.hdfv.part.machine.EngineHoodDesign::read);   // 2026-11-22
        PartTypes.register(dev.mcmod.hdfv.part.machine.HydraulicPumpDesign.TYPE, dev.mcmod.hdfv.part.machine.HydraulicPumpDesign::read);
        PartTypes.register(dev.mcmod.hdfv.part.machine.HydraulicMotorDesign.TYPE, dev.mcmod.hdfv.part.machine.HydraulicMotorDesign::read);
        PartTypes.register(dev.mcmod.hdfv.part.machine.LinkDesign.TYPE, dev.mcmod.hdfv.part.machine.LinkDesign::read);
        PartTypes.register(dev.mcmod.hdfv.part.machine.HookDesign.TYPE, dev.mcmod.hdfv.part.machine.HookDesign::read);
        PartTypes.register(dev.mcmod.hdfv.part.machine.WorkToolDesign.TYPE, dev.mcmod.hdfv.part.machine.WorkToolDesign::read);
        registered = true;
    }
}
