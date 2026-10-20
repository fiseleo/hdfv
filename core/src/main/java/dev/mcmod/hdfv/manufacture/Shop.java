package dev.mcmod.hdfv.manufacture;

import dev.mcmod.aerocore.api.manufacture.Casting;
import dev.mcmod.aerocore.api.manufacture.Forming;
import dev.mcmod.aerocore.api.manufacture.Joining;
import dev.mcmod.aerocore.api.manufacture.Laminating;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The planners' workshop rules (0.7): how a workpiece's route is built from its form and cuts, and how long the
 * forming steps take, from AeroCore's manufacturing physics (api.manufacture) and these stated rules:
 *
 * <ul>
 * <li>Casting: melting the stock in a FURNACE_W induction furnace (Casting.meltJ), MOULDING_S to ram the sand mould,
 *     three times Chvorinov's solidification time to cool for shake-out (a rule), FETTLING_S to cut off the gates and
 *     clean it.</li>
 * <li>Forging: heating the billet through (Forming.soakS at Fo 1 on half its equivalent cube) and the blows and
 *     handling, 2 min plus 2 min per 20 kg.</li>
 * <li>Stock (bar, plate, sheet, wire) from ingots at the press: 1 min plus 20 s per square-root kilogram.</li>
 * <li>Composites: plies of 0.25 mm laid by hand at LAYUP_M2_PER_H, cured in the oven (heated through, then
 *     OVEN_HOLD_S); rubber: a passenger tyre's 12 min for 20 mm of rubber, as the thickness squared.</li>
 * <li>Part-sized welds at a welding station: MIG fillets of 3 mm leg at the alloy's deposition rate by hand (operator
 *     factor 0.3).</li>
 * </ul>
 */
final class Shop {
    private Shop() {}

    static final double FURNACE_W = 150e3, MOULDING_S = 1200, FETTLING_S = 600, LAYUP_M2_PER_H = 1.5, PLY_M = 0.25e-3, OVEN_HOLD_S = 7200;
    /**
     * Electricity (0.8): the induction furnace delivers FURNACE_W to the melt at 65 % (coreless induction furnaces, 60-70 %);
     * billets heat in an induction heater at 60 % (up to HEATER_W) for forging at 0.7 of the melting point; the press
     * drive PRESS_W; stock is sheared, rolled and bent for FORM_J_PER_KG (a rule); the oven and vulcanizer hold their
     * heat at 40 and 50 % of OVEN_W and VULCANIZER_W; the arc draws ARC_W while it burns (HAND_FACTOR of a welder's time).
     */
    static final double FURNACE_EFFICIENCY = 0.65, HEATER_W = 100e3, HEATER_EFFICIENCY = 0.6, FORGING_FRACTION = 0.7, PRESS_W = 15e3, FORM_J_PER_KG = 50e3;
    static final double OVEN_W = 15e3, OVEN_DUTY = 0.4, VULCANIZER_W = 10e3, VULCANIZER_DUTY = 0.5, ARC_W = 6e3;
    static final double TYRE_S = 720, TYRE_RUBBER_M = 0.02, WELD_LEG_M = 3e-3, HAND_FACTOR = 0.3;
    /** 0.17: a saw's kerf, m (what sawing stock off turns to chips), and a rivet set by hand, s (drill, deburr, set and head it). */
    static final double SAW_KERF_M = 1.5e-3, RIVET_S = 20;

    /** Thermal conductivity of a material's family, W/m K (handbook values). */
    static double conductivity(Alloy a) {
        return switch (a.family) {
            case IRON -> 50; case STEEL -> 45; case STAINLESS -> 16; case ALUMINIUM -> 150; case MAGNESIUM -> 72; case TITANIUM -> 7;
            case NICKEL -> 11; case COPPER -> 60; case COMPOSITE -> 0.5; case WOOD -> 0.12; case RUBBER -> 0.15;
        };
    }

    /** Time to cast a mass of stock (kg) of a modulus V/A (m). */
    static double castS(Alloy a, double stockKg, double modulusM) {
        double melt = Casting.meltJ(stockKg, a.solidSpecificHeat, 293.15, a.meltingK, a.latentHeat, a.liquidSpecificHeat, a.meltingK + 100) / FURNACE_W;
        double solid = a.mouldConstant * modulusM * modulusM;
        return melt + MOULDING_S + 3 * solid + FETTLING_S;
    }

    /** Electricity to melt a casting's stock (kg), J. */
    static double meltJ(Alloy a, double stockKg) {
        return Casting.meltJ(stockKg, a.solidSpecificHeat, 293.15, a.meltingK, a.latentHeat, a.liquidSpecificHeat, a.meltingK + 100) / FURNACE_EFFICIENCY;
    }

    /** Electricity to heat a billet (kg) to forging temperature, J. */
    static double heatJ(Alloy a, double kg) { return kg * a.solidSpecificHeat * Math.max(FORGING_FRACTION * a.meltingK - 293.15, 0) / HEATER_EFFICIENCY; }

    /** Time to forge a piece (kg). */
    static double forgeS(Alloy a, double kg) {
        double half = 0.5 * Math.cbrt(kg / a.density);
        return Forming.soakS(half, conductivity(a), a.density, a.solidSpecificHeat, 1) + 120 * (1 + kg / 20);
    }

    /** Time to roll, cut or bend stock of a mass (kg) at the press. */
    static double formS(double kg) { return 60 + 20 * Math.sqrt(Math.max(kg, 0)); }

    /** Lay-up and cure of a laminate of a mass (kg) and thickness (m): {lay-up s, cure s}. */
    static double[] laminateS(Alloy a, double kg, double thicknessM) {
        double area = kg / (a.density * thicknessM);
        int plies = Math.max(1, (int) Math.round(thicknessM / PLY_M));
        double layup = Laminating.layupS(area, plies, LAYUP_M2_PER_H / 3600);
        double cure = Laminating.cureS(thicknessM / 2, conductivity(a), a.density, a.solidSpecificHeat, OVEN_HOLD_S);
        return new double[]{layup, cure};
    }

    /** Vulcanizing rubber of a thickness (m). */
    static double vulcanizeS(double rubberM) { return Laminating.vulcanizeS(rubberM, TYRE_S, TYRE_RUBBER_M); }

    /** A welder's time for a length (m) of 3 mm fillet by hand in an alloy (TIG for the light alloys, MIG for the rest). */
    static double weldS(Alloy a, double seamM) {
        double metal = Joining.metalKg(Joining.filletAreaM2(WELD_LEG_M), seamM, a.density);
        return Joining.weldS(metal, Math.max(a.depositionKgPerH, 0.5) / 3600, HAND_FACTOR);
    }

    static Piece piece(String key, int count, Alloy alloy, Form form, double kg) { return new Piece(key, count, alloy, form, kg); }

    /** A workpiece being planned: its primary forming step from its form, its cuts, its steps after (welding). */
    static final class Piece {
        final String key;
        final int count;
        final Alloy alloy;
        final Form form;
        final double kg;
        double yield, modulus = Double.NaN, thickness = 0.003;
        Workpiece.Envelope envelope;
        private final List<Operation> cuts = new ArrayList<>();
        private final List<Double> shares = new ArrayList<>();
        private final List<Operation> after = new ArrayList<>();

        Piece(String key, int count, Alloy alloy, Form form, double kg) {
            this.key = key; this.count = Math.max(count, 1); this.alloy = alloy; this.form = form; this.kg = Math.max(kg, 0); this.yield = form.yield();
        }

        Piece yield(double y) { this.yield = y; return this; }
        /** A casting's modulus, volume over cooling area (m). */
        Piece modulus(double m) { this.modulus = m; return this; }
        /** The stock's size (m): length, width, height (0.9). */
        Piece size(double lengthM, double widthM, double heightM) {
            this.envelope = Workpiece.Envelope.box(Math.max(lengthM, 1e-3), Math.max(widthM, 1e-3), Math.max(heightM, 1e-3));
            return this;
        }
        /** A turned part's size (m): length and the diameter it swings (0.9). */
        Piece turned(double lengthM, double diameterM) {
            this.envelope = Workpiece.Envelope.turned(Math.max(lengthM, 1e-3), Math.max(diameterM, 1e-3));
            return this;
        }
        /** A laminate's thickness (m). */
        Piece thickness(double t) { this.thickness = t; return this; }

        /** A cut taking a share of the removed metal and finishing an area (m^2) on a size (m) to a grade. */
        Piece cut(Process p, double share, double areaM2, double sizeM, int grade) { return cut(p, share, areaM2, sizeM, grade, false); }

        Piece cut(Process p, double share, double areaM2, double sizeM, int grade, boolean complex) {
            cuts.add(Operation.cut(p, 0, Math.max(areaM2, 0), Math.max(sizeM, 1e-3), Math.clamp(grade, 1, 18), complex));
            shares.add(share);
            return this;
        }

        /** The faces the last cut is made from (Operation faces; else the default for its process and the part). */
        Piece on(int faces) {
            if (!cuts.isEmpty()) { cuts.set(cuts.size() - 1, cuts.get(cuts.size() - 1).on(faces)); }
            return this;
        }

        Piece drill(int holes, double diameterM, double depthM, int grade) { return drill(holes, diameterM, depthM, grade, false); }

        Piece drill(int holes, double diameterM, double depthM, int grade, boolean complex) {
            if (holes > 0) { cuts.add(Operation.drill(holes, Math.max(diameterM, 1e-4), Math.max(depthM, 1e-4), Math.clamp(grade, 1, 18), complex)); shares.add(0.0); }
            return this;
        }

        /** A welded seam (m) at the welding station after the cuts. */
        Piece weld(double seamM) { after.add(Operation.fixed(Process.WELD, weldS(alloy, seamM))); return this; }

        // ---------------------------------------------------------------- 0.17: features and inserted steps

        private final Map<Integer, Feature> features = new HashMap<>();
        /** Steps inserted after the cut of each index (-1: after the forming step). */
        private final Map<Integer, List<Operation>> inserted = new HashMap<>();
        private boolean profiled;

        /** The last cut makes a feature (it keeps its process: the machines of 0.16 still take it). */
        Piece as(Feature f) {
            if (!cuts.isEmpty()) { features.put(cuts.size() - 1, f); }
            return this;
        }

        /** A step inserted after the last cut so far (after the forming step when there is none); faces 0 take the last cut's. */
        Piece then(Operation op) {
            inserted.computeIfAbsent(cuts.size() - 1, k -> new ArrayList<>()).add(op.inserted());
            return this;
        }

        /** Tapped holes (count, diameter and depth, m) to a grade, from the faces of the cut before (its drilling). */
        Piece tap(int holes, double diameterM, double depthM, int grade) {
            if (holes <= 0) { return this; }
            return then(new Operation(Process.TAP, 0, holes * Math.PI * diameterM * depthM, holes, diameterM, depthM, diameterM, Math.clamp(grade, 1, 18), Operation.PLAIN_AXES, 0, 0));
        }

        /** An outside thread of a diameter and length (m) to a grade, round the part. */
        Piece thread(double diameterM, double lengthM, int grade) {
            return then(new Operation(Process.THREAD, 0, Math.PI * diameterM * lengthM, 0, 0, 0, diameterM, Math.clamp(grade, 1, 18), Operation.PLAIN_AXES, Operation.AROUND, 0));
        }

        /** Lapping or superfinishing an area (m^2) on a size (m) to a grade, from the faces of the cut before. */
        Piece lap(double areaM2, double sizeM, int grade) {
            return then(new Operation(Process.LAP, 0, areaM2, 0, 0, 0, sizeM, Math.clamp(grade, 1, 18), Operation.PLAIN_AXES, 0, 0));
        }

        /** Polishing an area (m^2) (a blade's or a propeller's surface). */
        Piece polish(double areaM2, double sizeM) {
            return then(new Operation(Process.POLISH, 0, areaM2, 0, 0, 0, sizeM, 9, Operation.PLAIN_AXES, 0, 0));
        }

        /** Deburring a machined part's edges and passages (their area, m^2). */
        Piece deburr(double areaM2) { return then(new Operation(Process.DEBURR, 0, areaM2, 0, 0, 0, 0.01, 12, Operation.PLAIN_AXES, 0, 0)); }

        /** Chamfering and deburring gear teeth (their flank area, m^2). */
        Piece chamfer(double areaM2, double sizeM) {
            return then(new Operation(Process.DEBURR, 0, areaM2, 0, 0, 0, sizeM, 12, Operation.PLAIN_AXES, Operation.AROUND, 0).as(Feature.GEAR_TEETH));
        }

        /** Splines or a keyway (volume and area, m^3 and m^2) on a size (m) to a grade, inside a bore or outside. */
        Piece spline(double removedM3, double areaM2, double sizeM, int grade, boolean inside) {
            return then(new Operation(Process.MILL, removedM3, areaM2, 1, 0, 0, sizeM, Math.clamp(grade, 1, 18), Operation.PLAIN_AXES,
                    inside ? Operation.FRONT : Operation.AROUND, 0).as(Feature.SPLINE));
        }

        /** A disc's blade slots: their count, the volume and area they take (m^3, m^2), the disc's width (m), a grade. */
        Piece firTree(int slots, double removedM3, double areaM2, double widthM, double sizeM, int grade) {
            return then(new Operation(Process.MILL, removedM3, areaM2, Math.max(slots, 1), 0, widthM, sizeM, Math.clamp(grade, 1, 18), Operation.PLAIN_AXES,
                    Operation.AROUND, 0).as(Feature.FIR_TREE));
        }

        /** Cooling holes (count, diameter and depth, m) to a grade, at angles (five axes). */
        Piece coolingHoles(int holes, double diameterM, double depthM, int grade) {
            if (holes <= 0) { return this; }
            return then(Operation.drill(holes, diameterM, depthM, Math.clamp(grade, 1, 18), true).as(Feature.COOLING_HOLE));
        }

        /** Rivets set into the part (a lining's), each RIVET_S by hand. */
        Piece rivet(int count) {
            if (count <= 0) { return this; }
            return then(new Operation(Process.RIVET, 0, 0, count, 0.005, 0, 0.005, 12, 0, 0, count * RIVET_S));
        }

        /** Its outline cut from sheet (laser, plasma, waterjet, oxy-fuel or a mill), after the stock is made; plate always is. */
        Piece profile() { this.profiled = true; return this; }

        Workpiece build() {
            double stock = form == Form.LAMINATE || form == Form.RUBBER ? kg / form.yield() : kg / Math.clamp(yield, 0.02, 1);
            List<Operation> route = new ArrayList<>();
            switch (form) {
                case CASTING -> {
                    double m = Double.isNaN(modulus) ? Math.cbrt(3 * stock / alloy.density / (4 * Math.PI)) / 3 * 0.5 : modulus;
                    route.add(Operation.fixed(Process.CAST, castS(alloy, stock, Math.max(m, 1e-3))));
                }
                case FORGING -> route.add(Operation.fixed(Process.FORGE, forgeS(alloy, stock)));
                case BAR, PLATE, SHEET, WIRE, TUBE -> route.add(Operation.fixed(Process.FORM, formS(stock)));
                case LAMINATE -> {
                    double[] t = laminateS(alloy, kg, thickness);
                    route.add(Operation.fixed(Process.LAYUP, t[0]));
                    route.add(Operation.fixed(Process.CURE, t[1]));
                }
                case RUBBER -> route.add(Operation.fixed(Process.VULCANIZE, vulcanizeS(thickness)));
                case TIMBER -> { }
            }
            double drilled = 0;
            boolean turned = false;
            for (Operation op : cuts) { if (op.process() == Process.DRILL) { drilled += op.removedM3(); } turned |= op.process() == Process.TURN; }
            double removed = Math.max((stock - kg) / alloy.density - drilled, 0), total = 0;
            for (double s : shares) { total += s; }
            Workpiece.Envelope size = envelope != null ? envelope : Workpiece.Envelope.estimate(stock / alloy.density, turned);
            // 0.17: the stock is cut off (bar, tube) or its outline cut (plate; sheet when asked) after it is made
            List<Operation> first = new ArrayList<>();
            if (form == Form.BAR || form == Form.TUBE) { first.add(sawOff(size).inserted()); }
            if (form == Form.PLATE || profiled && form == Form.SHEET) { first.add(outline(size).inserted()); }
            first.addAll(resolve(inserted.get(-1), null, turned));
            route.addAll(first);
            for (int i = 0; i < cuts.size(); i++) {
                Operation op = cuts.get(i);
                if (op.process() != Process.DRILL) {
                    op = new Operation(op.process(), total > 0 ? removed * shares.get(i) / total : 0, op.finishedM2(), 0, 0, 0, op.sizeM(), op.grade(), op.axes(), op.faces(), 0);
                }
                if (op.faces() == 0) { op = op.on(Operation.defaultFaces(op.process(), turned)); }
                if (features.containsKey(i)) { op = op.as(features.get(i)); }
                route.add(op);
                route.addAll(resolve(inserted.get(i), op, turned));
            }
            route.addAll(after);
            return new Workpiece(key, count, alloy, form, kg, stock, route, size);
        }

        /** Inserted steps with no faces take the faces of the cut they follow (else their process's default). */
        private static List<Operation> resolve(List<Operation> ops, Operation before, boolean turned) {
            if (ops == null) { return List.of(); }
            List<Operation> out = new ArrayList<>();
            for (Operation op : ops) {
                if (op.faces() == 0 && op.process().cutting()) {
                    op = op.on(before != null && before.faces() != 0 ? before.faces() : Operation.defaultFaces(op.process(), turned));
                }
                out.add(op);
            }
            return out;
        }

        /** Sawing the stock off: its section (round, or its two smaller sides), a 1.5 mm kerf. */
        private Operation sawOff(Workpiece.Envelope e) {
            double[] s = e.sorted();
            double d = e.turned() ? e.diameterM() : Math.max(s[1], s[2]), section = e.turned() ? Math.PI / 4 * d * d : s[1] * s[2];
            return new Operation(Process.SAW, section * SAW_KERF_M, section, 0, 0, 0, d, 13, Operation.PLAIN_AXES, Operation.FRONT, 0);
        }

        /** Cutting a plate's outline: its perimeter (round its two larger sides) times its thickness (its smallest side). */
        private Operation outline(Workpiece.Envelope e) {
            double[] s = e.sorted();
            return new Operation(Process.PROFILE, 0, 2 * (s[0] + s[1]) * s[2], 1, 0, 0, s[0], 13, Operation.PLAIN_AXES, Operation.TOP, 0);
        }
    }
}
