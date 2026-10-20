package dev.mcmod.hdfv.manufacture;

import dev.mcmod.aerocore.api.manufacture.Broaching;
import dev.mcmod.aerocore.api.manufacture.Cutting;
import dev.mcmod.aerocore.api.manufacture.Ecm;
import dev.mcmod.aerocore.api.manufacture.Edm;
import dev.mcmod.aerocore.api.manufacture.Finishing;
import dev.mcmod.aerocore.api.manufacture.GearCutting;
import dev.mcmod.aerocore.api.manufacture.Grinding;
import dev.mcmod.aerocore.api.manufacture.Sawing;
import dev.mcmod.aerocore.api.manufacture.ThermalCutting;
import dev.mcmod.aerocore.api.manufacture.Threading;
import dev.mcmod.aerocore.api.manufacture.Waterjet;

/**
 * How long a step takes on a head, and the new processes on the old machines (0.17), from AeroCore's machining
 * calculations (api.manufacture 0.35) and the shop settings stated here (rules: what speeds, feeds and currents the
 * machines run at).
 *
 * <ul>
 * <li>Grinding: the stock by power at five times the alloy's cutting energy (as 0.7), then finishing passes over the
 *     area at the head's area rate a pass: cylindrical 1 / (f_a v_w) with 10 mm a revolution at 0.4 m/s, 4 passes;
 *     internal 5 mm at 0.3 m/s, 6 passes; surface 5 mm crossfeed at 0.25 m/s, 4 passes (Grinding); centreless
 *     through-feed past a 0.3 m regulating wheel at 0.5 rev/s tilted 3 degrees, 3 passes, with almost no clamping.</li>
 * <li>Gears: area rates from a reference gear (module 3, 40 teeth, 30 mm wide: 0.0162 m^2 of flanks): hobbed with an
 *     80 mm single-start hob at 90 m/min and 2 mm a revolution (GearCutting.hobbingS, 3.2 min); shaped at 0.3 mm a
 *     stroke, 10 strokes a second, 2 cuts (4.2 min); shaved at 0.5 mm a revolution, 3 rev/s, 3 passes (1 min);
 *     profile ground at 0.05 m/s, 6 strokes a gap (3.2 min); chamfered at a second a tooth end. Cutting speeds scale
 *     with the alloy's carbide speed over steel's.</li>
 * <li>Honing: 2 micrometres a second radially (Finishing.honingS) a bore, 15 s each to enter and gauge; a bore is as
 *     long as it is wide. Lapping: 2 micrometres at Preston's K 1e-13 /Pa, 0.1 MPa, 1 m/s (20 s), and 200 s a square
 *     metre to load and wipe. Polishing at a buff: 600 s a square metre. Deburring: 60 s and 300 s a square metre.</li>
 * <li>Broaching: a stroke a slot (the step's holes, at least one) of a 0.5 m broach past the work at 6 m/min, back at
 *     30 m/min (Broaching.strokeS), 15 s to load. Shaping and slotting: the 0.7 cutting model at a third of the
 *     carbide speed (the tool cuts on one stroke of two).</li>
 * <li>Sawing (Sawing): through the stock's smaller side, band 3 micrometres a tooth at 60 m/min, 6.35 mm pitch, 1.3 mm
 *     kerf; cold saw 50 micrometres a tooth at 30 m/min, 10 mm pitch, 2.5 mm kerf; hack saw 0.1 mm a stroke at 1.5
 *     strokes a second; all no faster than their power allows; 10 s to load.</li>
 * <li>EDM (Edm, Weller): sinking at 40 A, holes at 10 A (orifices) or 20 A, wire at 20 A through a 0.29 mm kerf.
 *     ECM (Ecm, Faraday): 2000 A at 85 % current efficiency, each family dissolving at its usual valence.</li>
 * <li>Cutting (ThermalCutting, Waterjet): a profile's cut length is its edge area over the plate's thickness; the laser
 *     4 kW at 15 % through a 0.3 mm kerf with nitrogen; plasma 39 kW at 20 % through 3 mm; the waterjet 380 MPa, 0.33
 *     mm orifice, 0.4 kg/min of garnet, 1 mm kerf, eroding each family at its specific energy (a fitted number); a
 *     second to pierce each start. Laser-drilled cooling holes: a second each.</li>
 * <li>Threads (Threading): rolled in 15 revolutions at 2 rev/s (cylindrical dies) or one 0.25 m stroke at 0.5 m/s
 *     (flat dies), 5 s to load; tapped at 0.2 m/s (high-speed steel on steel, scaled by the alloy), reversing at twice,
 *     3 s a hole to place; thread-milled at 2 mm/s at the edge with a cutter three quarters of the hole; single-point
 *     on a lathe in 8 passes at 0.5 m/s. Pitch: ISO coarse, about 0.15 of the diameter.</li>
 * <li>Riveting: 3 s a rivet squeezed, 8 s hammered, 6 s drilled and set by the automatic head; by hand at a bench
 *     (the step's own time) 20 s.</li>
 * </ul>
 */
public final class Machining {
    private Machining() {}

    static final double CYL_AREA_S = 1 / (0.01 * 0.4), INT_AREA_S = 1 / (0.005 * 0.3), SURF_AREA_S = 1 / (0.005 * 0.25);
    static final int CYL_PASSES = 4, INT_PASSES = 6, SURF_PASSES = 4, CENTRELESS_PASSES = 3;
    static final double THROUGHFEED = Grinding.throughfeedMps(0.3, 0.5, Math.toRadians(3)), CENTRELESS_CLAMP = 0.05;

    /** The reference gear's flank area, m^2: two flanks of 40 teeth 30 mm wide and 2.25 modules deep. */
    static final double GEAR_REF_M2 = 2 * 40 * 0.03 * 2.25 * 0.003;
    static final double HOB_S_PER_M2 = GearCutting.hobbingS(40, 0.03, GearCutting.approachM(0.08, 2.25 * 0.003), 0.005, 1, GearCutting.hobRevPerS(1.5, 0.08), 0.002) / GEAR_REF_M2;
    static final double SHAPE_S_PER_M2 = GearCutting.shapingS(0.12, 0.3e-3, 10, 2) / GEAR_REF_M2;
    static final double SHAVE_S_PER_M2 = GearCutting.shavingS(0.03, 0.5e-3, 3, 3) / GEAR_REF_M2;
    static final double GEAR_GRIND_S_PER_M2 = GearCutting.profileGrindingS(40, 0.03, 0.005, 0.05, 6) / GEAR_REF_M2;
    static final double CHAMFER_S_PER_M2 = 80 / GEAR_REF_M2;

    static final double HONE_MPS = 2e-6, HONE_BORE_S = 15, LAP_S = Finishing.prestonS(2e-6, 1e-13, 1e5, 1.0), LAP_S_PER_M2 = 200, POLISH_S_PER_M2 = 600,
            DEBURR_S = 60, DEBURR_S_PER_M2 = 300;

    /** Rivet times: squeezed, hammered, drilled and set, by hand (StructurePlanner's RIVET_S); a hole drilled and deburred by hand (a rule). */
    static final double SQUEEZE_S = 3, IMPACT_S = 8, DRILL_RIVET_S = 6, HAND_RIVET_S = 20, HAND_DRILL_S = 12;

    /**
     * A rivet's time in an assembly room with a riveting machine at it (0.17), s: a drilling and riveting head does the
     * whole of it; a squeeze riveter sets what was drilled by hand; a rivet gun is what the hand rule sets them with anyway.
     * Null: none (by hand).
     */
    public static double roomRivetS(Head h) {
        if (h == null) { return HAND_RIVET_S; }
        return switch (h) { case DRILL_RIVETER -> DRILL_RIVET_S; case SQUEEZE_RIVETER -> HAND_DRILL_S + SQUEEZE_S; default -> HAND_RIVET_S; };
    }

    /** One piece's time for a step on a head of a family of a size, s (infinite: it cannot). The NC drive's grade is the caller's. */
    public static double seconds(Operation op, Head h, Workpiece w) {
        Alloy a = w.alloy();
        double speed = a.carbideSpeed / Alloy.STEEL.carbideSpeed;
        return switch (h) {
            case CYLINDRICAL_GRINDING -> grind(op, h, a, CYL_AREA_S, CYL_PASSES);
            case INTERNAL_GRINDING, TOOL_GRINDING -> grind(op, h, a, INT_AREA_S, INT_PASSES);
            case SURFACE_GRINDING -> grind(op, h, a, SURF_AREA_S, SURF_PASSES);
            case CENTRELESS_GRINDING -> {
                double length = op.finishedM2() / (Math.PI * Math.max(op.sizeM(), 1e-3));
                yield rough(op, h, a, 5) + CENTRELESS_PASSES * length / THROUGHFEED;
            }
            case HONING -> {
                double d = Math.max(op.sizeM(), 1e-3), bores = Math.max(1, Math.round(op.finishedM2() / (Math.PI * d * d)));
                double stock = op.finishedM2() > 0 ? op.removedM3() / op.finishedM2() : 0;
                yield bores * (Finishing.honingS(stock, HONE_MPS) + HONE_BORE_S);
            }
            case LAPPING -> LAP_S + LAP_S_PER_M2 * op.finishedM2();
            case POLISHING -> POLISH_S_PER_M2 * op.finishedM2();
            case DEBURRING -> DEBURR_S + DEBURR_S_PER_M2 * op.finishedM2();
            case HOBBING -> rough(op, h, a, 1) + HOB_S_PER_M2 * op.finishedM2() / speed;
            case GEAR_SHAPING -> rough(op, h, a, 1) + SHAPE_S_PER_M2 * op.finishedM2() / speed;
            case SHAVING -> SHAVE_S_PER_M2 * op.finishedM2() / speed;
            case GEAR_GRINDING -> rough(op, h, a, 5) + GEAR_GRIND_S_PER_M2 * op.finishedM2();
            case CHAMFERING -> CHAMFER_S_PER_M2 * op.finishedM2();
            case SHAPER, SLOTTER -> Plans.cuttingSeconds(op, h.powerW, 0.3, false, a);
            case BROACHING -> {
                double length = op.holeDepthM() > 0 ? op.holeDepthM() : Math.max(op.sizeM(), 0.02);
                yield Math.max(op.holes(), 1) * (Broaching.strokeS(0.5, length, 0.1 * speed, 0.5) + 15) + rough(op, h, a, 1);
            }
            case BAND_SAW -> saw(w, h, Sawing.feedMps(3e-6 * speed, 1.0, 6.35e-3), 1.3e-3);
            case COLD_SAW -> saw(w, h, Sawing.feedMps(5e-5 * speed, 0.5, 10e-3), 2.5e-3);
            case HACKSAW -> saw(w, h, Sawing.hacksawFeedMps(1e-4 * speed, 1.5), 1.0e-3);
            case SINKER_EDM -> op.process() == Process.DRILL
                    ? Edm.drillS(op.holes(), op.holeDiameterM(), op.holeDepthM(), Edm.removalRate(20, a.meltingK))
                    : Edm.sinkS(op.removedM3(), Edm.removalRate(40, a.meltingK));
            case HOLE_EDM -> Edm.drillS(op.holes(), op.holeDiameterM(), op.holeDepthM(), Edm.removalRate(op.feature() == Feature.ORIFICE ? 10 : 20, a.meltingK))
                    + 5.0 * op.holes();
            case WIRE_EDM -> op.finishedM2() * Edm.kerfM(0.25e-3, 0.02e-3) / Edm.removalRate(20, a.meltingK);
            case ECM -> Ecm.machineS(op.removedM3(), Ecm.removalRate(2000, ecmRemoval(a), 0.85));
            case LASER -> op.process() == Process.DRILL ? 1.0 * op.holes()
                    : ThermalCutting.cutS(cutLength(op, w), ThermalCutting.speedMps(4e3, 0.15, 0.3e-3, thickness(w), a.density, melt(a)), Math.max(op.holes(), 1), 1);
            case PLASMA -> ThermalCutting.cutS(cutLength(op, w), ThermalCutting.speedMps(ThermalCutting.arcW(150, 260), 0.2, 3e-3, thickness(w), a.density, melt(a)),
                    Math.max(op.holes(), 1), 1);
            case WATERJET -> {
                double v = Waterjet.jetMps(380e6, 1000, 0.9), q = Waterjet.waterM3PerS(0.33e-3, 380e6, 1000, 0.7), garnet = 0.4 / 60;
                double removal = Waterjet.removalRate(garnet, Waterjet.abrasiveMps(v, garnet / (q * 1000), 0.75), erosion(a));
                yield ThermalCutting.cutS(cutLength(op, w), Waterjet.cutMps(removal, 1e-3, thickness(w)), Math.max(op.holes(), 1), 1);
            }
            case THREAD_ROLLING -> Threading.inFeedRollingS(15, 2) + 5;
            case FLAT_DIE_ROLLING -> Threading.flatDieS(0.25, 0.5) + 3;
            case TAPPING -> tap(op, a, 1);
            case THREAD_MILLING -> op.holes() > 0
                    ? op.holes() * (Threading.threadMillingS(op.holeDiameterM(), 0.75 * op.holeDiameterM(), op.holeDepthM(), pitch(op.holeDiameterM()), 2e-3 * speed, true) + 3)
                    : Threading.threadMillingS(op.sizeM(), 0.02, threadLength(op), pitch(op.sizeM()), 2e-3 * speed, false);
            case SQUEEZE_RIVETER -> SQUEEZE_S * Math.max(op.holes(), 1);
            case IMPACT_RIVETER -> IMPACT_S * Math.max(op.holes(), 1);
            case DRILL_RIVETER -> op.process() == Process.DRILL ? Plans.cuttingSeconds(op, h.powerW, 0.8, false, a) : DRILL_RIVET_S * Math.max(op.holes(), 1);
        };
    }

    /**
     * A new process's time on one of 0.16's machines (a lathe threading or parting off, a drill press tapping, a mill
     * profiling, a welding station cutting with oxy-fuel, a bench riveting by hand), s; NaN when it is not one of them.
     */
    public static double stationSeconds(Operation op, Station s, Workpiece w) {
        Alloy a = w.alloy();
        return switch (op.process()) {
            case THREAD -> s == Station.LATHE ? Threading.singlePointS(threadLength(op), pitch(op.sizeM()), 0.5 / (Math.PI * Math.max(op.sizeM(), 1e-3)), 8)
                    : Threading.threadMillingS(op.sizeM(), 0.02, threadLength(op), pitch(op.sizeM()), 2e-3, false);
            case TAP -> tap(op, a, s.manual ? 1.5 : 1);
            case RIVET -> HAND_RIVET_S * Math.max(op.holes(), 1);
            case PROFILE -> s == Station.WELDING_STATION
                    ? ThermalCutting.cutS(cutLength(op, w), ThermalCutting.speedMps(25e3, 0.1, 2e-3, thickness(w), a.density, ThermalCutting.withOxygenJPerKg(melt(a), 0.15)), 1, 30)
                    : Double.NaN;
            default -> Double.NaN;
        };
    }

    // ------------------------------------------------------------------------------------------------ pieces

    private static double rough(Operation op, Head h, Alloy a, double energyFactor) {
        return Cutting.roughS(op.removedM3(), h.powerW, Plans.DRIVE_EFFICIENCY, a.specificEnergy * energyFactor, 0);
    }

    private static double grind(Operation op, Head h, Alloy a, double secondsPerM2, int passes) {
        return rough(op, h, a, 5) + passes * secondsPerM2 * op.finishedM2();
    }

    private static double saw(Workpiece w, Head h, double feed, double kerf) {
        double[] s = w.envelope().sorted();
        double height = w.envelope().turned() ? w.envelope().diameterM() : s[2], across = w.envelope().turned() ? w.envelope().diameterM() : s[1];
        double f = Math.min(feed, Sawing.powerLimitedFeedMps(h.powerW, 0.8, w.alloy().specificEnergy, kerf, across));
        return Sawing.cutS(height, f) + 10;
    }

    private static double tap(Operation op, Alloy a, double factor) {
        double d = Math.max(op.holeDiameterM(), 1e-3), v = 0.2 * a.carbideSpeed / Alloy.STEEL.carbideSpeed;
        return factor * op.holes() * (Threading.tappingS(op.holeDepthM(), pitch(d), v / (Math.PI * d), 2) + 3);
    }

    /** ISO coarse pitch, about 0.15 of the diameter (M10 1.5, M6 1.0, M20 2.5), m. */
    static double pitch(double diameterM) { return Math.max(0.15 * diameterM, 2e-4); }

    /** A thread's length: its finished area over its circumference. */
    static double threadLength(Operation op) { return op.finishedM2() / (Math.PI * Math.max(op.sizeM(), 1e-3)); }

    /** A plate's thickness: its stock's smallest side. */
    static double thickness(Workpiece w) { double[] s = w.envelope().sorted(); return Math.max(s[2], 5e-4); }

    /** A profile's cut length: its edge area over the plate's thickness. */
    static double cutLength(Operation op, Workpiece w) { return op.finishedM2() / thickness(w); }

    /** Energy to melt a kilogram of an alloy from 20 C (its latent heat and solid specific heat), J/kg. */
    static double melt(Alloy a) {
        double m = a.meltingK > 293.15 ? a.meltingK : 900;
        return ThermalCutting.meltJPerKg(a.solidSpecificHeat, 293.15, Math.max(m, 300), Math.max(a.latentHeat, 1e5), 0, 0);
    }

    /** Specific erosion energy of a family under an abrasive waterjet, J/m^3 (fitted so 10 mm of steel cuts at about 120 mm/min). */
    static double erosion(Alloy a) {
        return switch (a.family) {
            case IRON -> 40e9; case STEEL -> 45e9; case STAINLESS -> 55e9; case ALUMINIUM -> 15e9; case MAGNESIUM -> 12e9; case TITANIUM -> 50e9;
            case NICKEL -> 70e9; case COPPER -> 30e9; case COMPOSITE -> 8e9; case WOOD -> 1e9; case RUBBER -> 2e9;
        };
    }

    /** Volume a coulomb dissolves of a family at its usual valence (iron and nickel 2, aluminium 3, titanium 4, copper 2), m^3/C. */
    static double ecmRemoval(Alloy a) {
        return switch (a.family) {
            case IRON, STEEL, STAINLESS -> Ecm.specificRemoval(0.055845, 2, a.density);
            case NICKEL -> Ecm.specificRemoval(0.058693, 2, a.density);
            case ALUMINIUM -> Ecm.specificRemoval(0.026982, 3, a.density);
            case MAGNESIUM -> Ecm.specificRemoval(0.024305, 2, a.density);
            case TITANIUM -> Ecm.specificRemoval(0.047867, 4, a.density);
            case COPPER -> Ecm.specificRemoval(0.063546, 2, a.density);
            default -> Ecm.specificRemoval(0.055845, 2, Math.max(a.density, 1000));
        };
    }
}
