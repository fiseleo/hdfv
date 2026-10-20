package dev.mcmod.hdfv.fluid;

import dev.mcmod.aerocore.api.power.ElectricMachine;
import dev.mcmod.aerocore.api.power.PipeFlow;
import dev.mcmod.aerocore.api.power.Pump;

/**
 * Pipes, pumps and tanks (0.8): a pipe block is a metre of DN50 steel pipe (50 mm bore), a pump block a small process
 * pump (best at 5 l/s against 40 m, 65 %, on a 4 kW motor), a tank block a cubic metre, a storage tank's inside 9 m^3 a
 * course. Flow happens only where a pump drives it: its duty against the lift between where it draws and where it
 * delivers and the friction of the pipe between (AeroCore PipeFlow and Pump), with an entry, an exit and a fitting
 * every four metres (K 2 plus 0.3 every four pipes, Crane TP-410 values for elbows and tees). The industry's clock:
 * a tick is Pace.workSecondsPerTick of work.
 */
public final class Piping {
    private Piping() {}

    public static final double PIPE_DIAMETER_M = 0.05, PIPE_LENGTH_M = 1;
    public static final Pump PUMP = Pump.forDuty(0.005, 40, 0.65);
    public static final double PUMP_MOTOR_W = 4e3;
    /** Litres a tank block and a storage tank's course hold. */
    public static final int TANK_LITRES = 1000, STORAGE_COURSE_LITRES = 9000;

    /** Loss coefficient of a run of pipe blocks: entry and exit, and a fitting every four. */
    public static double fittingsK(int pipes) { return 2 + 0.3 * Math.max(pipes, 0) / 4.0; }

    /** The pump's flow of a fluid through a run of pipe blocks against a lift (m, negative downhill), m^3/s. */
    public static double flowM3PerS(HdfvFluids.Spec f, int pipes, double liftM) {
        double rho = f.densityKgPerM3();
        double length = Math.max(pipes, 1) * PIPE_LENGTH_M, k = fittingsK(pipes);
        return PUMP.dutyFlowM3PerS(q -> liftM + PipeFlow.pressureDropPa(q, PIPE_DIAMETER_M, length, PipeFlow.STEEL_ROUGHNESS_M, rho, f.viscosityPaS(), k) / (rho * Pump.G));
    }

    /** Electrical power the pump draws for a flow of a fluid, W. */
    public static double pumpW(HdfvFluids.Spec f, double flowM3PerS) {
        double shaft = PUMP.shaftPowerW(flowM3PerS, f.densityKgPerM3());
        return shaft <= 0 ? 0 : ElectricMachine.of(PUMP_MOTOR_W).inputForOutput(Math.min(shaft, PUMP_MOTOR_W));
    }

    /** Litres a flow (m^3/s) moves in a tick of work. */
    public static double litresPerTick(double flowM3PerS, double workSecondsPerTick) { return flowM3PerS * 1000 * workSecondsPerTick; }
}
