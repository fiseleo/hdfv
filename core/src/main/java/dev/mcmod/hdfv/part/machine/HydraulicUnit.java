package dev.mcmod.hdfv.part.machine;

import dev.mcmod.aerocore.api.hydraulic.Motor;
import dev.mcmod.aerocore.api.hydraulic.Pump;

/**
 * What a hydraulic pump or motor is built as (0.26): an axial piston unit (swash plate or bent axis, the main pumps,
 * travel and swing motors of mobile machinery: volumetric 0.95 and hydromechanical 0.92 at 280 bar) or an external gear
 * unit (pilot, fan and steering pumps, small motors: 0.90 and 0.85 at 200 bar). The efficiencies are AeroCore's
 * (Hydraulics.AXIAL_PISTON_*, GEAR_*).
 */
public enum HydraulicUnit {
    AXIAL_PISTON, GEAR;

    /** AeroCore's motor of this kind and a displacement (m^3 per revolution). */
    public Motor motor(double displacementM3) {
        return this == AXIAL_PISTON ? Motor.axialPiston(displacementM3) : Motor.gear(displacementM3);
    }

    /** The highest pressure catalogue units of this kind take (peak), Pa: axial piston 450 bar, gear 300 bar. */
    public double peakPa() { return this == AXIAL_PISTON ? 450e5 : 300e5; }

    /** The efficiencies' rated pressure, Pa. */
    public double ratedPa() {
        return this == AXIAL_PISTON ? dev.mcmod.aerocore.api.hydraulic.Hydraulics.AXIAL_PISTON_RATED_PA : dev.mcmod.aerocore.api.hydraulic.Hydraulics.GEAR_RATED_PA;
    }

    /** AeroCore's pump of this kind (a gear pump is FIXED: its control is the design's check). */
    public Pump pump(double displacementM3, Pump.Control control, double maxPressurePa, double powerLimitW) {
        return this == AXIAL_PISTON ? Pump.axialPiston(displacementM3, control, maxPressurePa, control == Pump.Control.POWER_LIMITED ? powerLimitW : 0)
                : Pump.gear(displacementM3, maxPressurePa);
    }
}
