package dev.mcmod.hdfv.part.wheel;

import dev.mcmod.aerocore.api.wheel.BrakeControl;

/**
 * Anti-lock braking on one wheel (ABS; hdfv's rule, as Bosch's slip-threshold control works): the brake's pressure is
 * the driver's demand times a level between 0 and 1. When the wheel's slip passes RELEASE (braking) the level falls at
 * DUMP per second (the outlet valve dumps pressure); once the slip is back under APPLY it climbs again at BUILD per
 * second (pressure built up in steps), and between them it holds. The tyre's peak lies near 0.1-0.15 slip on tarmac,
 * so the wheel cycles about it. Below OFF m/s it is out of the way (the wheel may lock to stop). Memory: how far the
 * level is cut (0: the driver's full demand, so a new wheel brakes at once).
 */
public final class AntiLock implements BrakeControl {
    public static final double RELEASE = 0.18, APPLY = 0.10, DUMP = 12, BUILD = 4, OFF = 1.5;

    @Override public double demand(double driverDemand, double slipRatio, double speedMps, double[] memory, double dt) {
        double cut = memory[0];
        if (speedMps < OFF || driverDemand <= 0) { cut = 0; }
        else if (-slipRatio > RELEASE) { cut += DUMP * dt; }
        else if (-slipRatio < APPLY) { cut -= BUILD * dt; }
        cut = Math.clamp(cut, 0, 1);
        memory[0] = cut;
        return driverDemand * (1 - cut);
    }

    @Override public int memory() { return 1; }
}
