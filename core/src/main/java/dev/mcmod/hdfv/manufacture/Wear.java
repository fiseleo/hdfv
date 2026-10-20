package dev.mcmod.hdfv.manufacture;

import dev.mcmod.aerocore.api.manufacture.Gases;
import dev.mcmod.aerocore.api.manufacture.ToolLife;

import java.util.EnumMap;
import java.util.Map;

/**
 * What a job uses up (0.17), in items (Consumable): a share of a durable item's life or of a counted item, for one
 * piece. AeroCore's physics where it says how a tool wears, and the shop rules stated here.
 *
 * <ul>
 * <li>Cutting tools (turning, milling, planing, boring, drilling, tapping, threading, hobbing, shaping, shaving,
 *     broaching, chamfering): an edge lasts Taylor's life (AeroCore ToolLife) at the speed the machine cuts, the alloy's
 *     carbide speed times the machine's speed factor; the one-minute speed is Groover's for steel (cermet 500 m/min,
 *     high-speed steel 70) scaled by the alloy's carbide speed over steel's. A high-speed steel set wears as if
 *     cutting at a third of that speed (the times stay the machine's: a rule). A set has ten edges; the step's cutting
 *     time over an edge's life is the edges it takes. Thread rolling dies wear as a tenth of that (they cut nothing).</li>
 * <li>Grinding wheels: the removed volume over the grinding ratio (steel 60, alloy and stainless steel 30, cast iron
 *     80, aluminium and magnesium 20 (they load the wheel), titanium 5, nickel alloys 10, copper 30, composites 50;
 *     Kalpakjian and Schmid's range 2-200) and a light dressing a piece (20 micrometres off a 400 x 50 mm wheel).</li>
 * <li>Honing stones: the removed volume over 5. Lapping and polishing compound: 20 g a square metre.</li>
 * <li>Saw blades: the section cut, scaled by the alloy's cutting energy over carbon steel's, over a blade's 2 m^2.</li>
 * <li>Electrodes: sinking wears a twentieth of the removed volume (graphite), hole drilling half of it (tube
 *     electrodes); a plasma torch's electrode and nozzle last an hour of arc. Brass wire runs at 0.15 m/s while the
 *     wire EDM cuts. Electrolyte: 20 kg a kilogram of metal dissolved (it goes to sludge). Garnet: 0.4 kg a minute.</li>
 * <li>Assist gas: nitrogen through a 1.5 mm nozzle from 16 bar while the laser cuts (AeroCore Gases, choked flow).</li>
 * <li>Rivets: one a rivet (the step's holes).</li>
 * </ul>
 */
public final class Wear {
    private Wear() {}

    static final double WHEEL_M3 = Math.PI * (0.2 * 0.2 - 0.13 * 0.13) * 0.05, DRESS_M3 = Math.PI * 0.4 * 0.05 * 20e-6;
    static final double STONES_M3 = 32e-6, STONE_RATIO = 5, COMPOUND_KG_PER_M2 = 0.02, COMPOUND_KG = 0.25, BLADE_M2 = 2.0;
    static final double ELECTRODE_M3 = 0.2e-3, SINK_WEAR = 0.05, TUBE_WEAR = 0.5, ARC_S = 3600, WIRE_MPS = 0.15, SPOOL_M = 12_000;
    static final double ELECTROLYTE_PER_KG = 20, ELECTROLYTE_KG = 10, GARNET_KG_PER_S = 0.4 / 60, GARNET_KG = 25, RIVETS_A_BOX = 100;
    static final double NITROGEN_KG_PER_S = Gases.chokedKgPerS(Gases.Gas.NITROGEN, 1.5e-3, 16e5, 293.15, 0.85);
    static final double CYLINDER_KG = Gases.cylinderKg(Gases.Gas.NITROGEN, 0.05, 2e7, 288.15, 1);

    /** A tool material for the cutting tools. */
    public enum Tool { HSS, CERMET, CARBON }

    /** 0.23: plain carbon tool steel cuts at this share of high-speed steel's speed (Groover table 23.2: 20 against 70 m/min). */
    public static final double CARBON_SPEED = 0.4;

    /** How much longer a cut takes with a tool material than the machine's own speed (carbon steel: 1 / CARBON_SPEED). */
    public static double timeFactor(Tool tool) { return tool == Tool.CARBON ? 1 / CARBON_SPEED : 1; }

    /** The grinding ratio of an alloy's family. */
    static double grindingRatio(Alloy a) {
        return switch (a.family) {
            case STEEL -> a == Alloy.STEEL ? 60 : 30; case STAINLESS -> 30; case IRON -> 80; case ALUMINIUM, MAGNESIUM -> 20; case TITANIUM -> 5;
            case NICKEL -> 10; case COPPER -> 30; case COMPOSITE -> 50; case WOOD, RUBBER -> 100;
        };
    }

    /** An edge's life cutting an alloy with a tool material at a machine's speed factor, s. */
    public static double edgeLifeS(Alloy a, Tool tool, double speedFactor) {
        double scale = a.carbideSpeed / Alloy.STEEL.carbideSpeed, v = a.carbideSpeed * speedFactor;
        return switch (tool) {
            case CERMET -> ToolLife.lifeS(v, ToolLife.C_CARBIDE_STEEL * scale, ToolLife.N_CARBIDE);
            case HSS -> ToolLife.lifeS(v / 3, ToolLife.C_HSS_STEEL * scale, ToolLife.N_HSS);
            case CARBON -> ToolLife.lifeS(v / 3 * CARBON_SPEED, ToolLife.C_CARBON_STEEL * scale, ToolLife.N_CARBON);
        };
    }

    /**
     * What one piece of a step uses, in items, on a head (null: one of 0.16's stations) at a speed factor, taking a time
     * (s) to do, with a tool material for cutting tools.
     */
    public static Map<Consumable, Double> of(Operation op, Workpiece w, Station s, Head h, double speedFactor, double seconds, Tool tool) {
        Map<Consumable, Double> out = new EnumMap<>(Consumable.class);
        Consumable c = consumable(op, s, h);
        if (c == null || !(seconds > 0) || !Double.isFinite(seconds)) { return out; }
        Alloy a = w.alloy();
        double used = switch (c) {
            case CUTTING_TOOLS -> seconds / edgeLifeS(a, tool, speedFactor) / Consumable.EDGES * (op.process() == Process.THREAD && h != null ? 0.1 : 1);
            case GRINDING_WHEEL -> (op.removedM3() / grindingRatio(a) + DRESS_M3) / WHEEL_M3;
            case HONING_STONES -> op.removedM3() / STONE_RATIO / STONES_M3;
            case LAPPING_COMPOUND -> op.finishedM2() * COMPOUND_KG_PER_M2 / COMPOUND_KG;
            case SAW_BLADE -> section(w) * a.specificEnergy / Alloy.STEEL.specificEnergy / BLADE_M2;
            case ELECTRODES -> h == Head.PLASMA ? seconds / ARC_S
                    : op.process() == Process.DRILL ? Math.PI / 4 * op.holeDiameterM() * op.holeDiameterM() * op.holeDepthM() * op.holes() * TUBE_WEAR / ELECTRODE_M3
                    : op.removedM3() * SINK_WEAR / ELECTRODE_M3;
            case BRASS_WIRE -> WIRE_MPS * seconds / SPOOL_M;
            case ELECTROLYTE -> op.removedM3() * a.density * ELECTROLYTE_PER_KG / ELECTROLYTE_KG;
            case GARNET -> GARNET_KG_PER_S * seconds / GARNET_KG;
            case ASSIST_GAS -> NITROGEN_KG_PER_S * seconds / CYLINDER_KG;
            case RIVETS -> Math.max(op.holes(), 1) / RIVETS_A_BOX;
        };
        if (used > 0) { out.put(c, used); }
        return out;
    }

    /** What a step uses on a head, or on one of 0.16's stations by its process (null: nothing wears). */
    public static Consumable consumable(Operation op, Station s, Head h) {
        if (h != null) { return h.uses; }
        return switch (op.process()) {
            case TURN, MILL, PLANE, BORE, DRILL, TAP, THREAD, SAW, DEBURR -> Consumable.CUTTING_TOOLS;
            case GRIND -> s == Station.BORING_MILL ? Consumable.HONING_STONES : Consumable.GRINDING_WHEEL;
            case LAP, POLISH -> Consumable.LAPPING_COMPOUND;
            case PROFILE -> s == Station.WELDING_STATION ? null : Consumable.CUTTING_TOOLS;
            case RIVET -> Consumable.RIVETS;
            default -> null;
        };
    }

    /** A bar's section (its smaller sides, or round), m^2. */
    static double section(Workpiece w) {
        Workpiece.Envelope e = w.envelope();
        if (e.turned()) { return Math.PI / 4 * e.diameterM() * e.diameterM(); }
        double[] s = e.sorted();
        return s[1] * s[2];
    }
}
