package dev.mcmod.hdfv.refit;

import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.VehicleBlueprint;
import dev.mcmod.hdfv.assembly.AssemblyPlan;
import dev.mcmod.hdfv.design.Designs;
import dev.mcmod.hdfv.manufacture.Alloy;
import dev.mcmod.hdfv.manufacture.PartPlan;
import dev.mcmod.hdfv.manufacture.Plans;
import dev.mcmod.hdfv.manufacture.StructureWork;
import dev.mcmod.hdfv.manufacture.Workpiece;
import dev.mcmod.hdfv.paint.PaintJob;
import dev.mcmod.hdfv.part.wheel.AxleDesign;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 0.16: what a refit takes, part by part (by id) from the vehicle as it is to as it is to be (the user's 「照實花費」):
 * <ul>
 * <li>A part made at a bench (an engine, a gearbox, an axle): its plan's components before and after are compared one by
 *   one (Workpiece records; the same component of the same size and route is kept). The new ones are brought, made on
 *   the machines from the refitted part's blueprint; the ones taken off are given back as finished components. Putting
 *   it back together: the room's install time and a minute a component and 3 s a kilogram of what changed.</li>
 * <li>A part built in the room (a body, a chassis, a wing): each structure work (a module's skin, a station's members)
 *   that changed is built again from stock (its stock and filler) at its labour, the old one's metal coming back as
 *   half its mass (as a dismantled vehicle's does); its components likewise. Welded work needs a welding station.</li>
 * <li>A part whose components and structure are all the same (tuning: ignition, boost, dampers, alignment, ride height,
 *   the shift schedule; looks): ADJUST_HOURS of work, an axle's alignment ALIGN_HOURS.</li>
 * <li>A body whose shape changed is repainted whole (cans and spraying and baking as PaintJob's).</li>
 * <li>Lamps (a body's own, LampSpec; the kit's count as they are when it has none): a lamp unit for each new one of a
 *   style and size the body did not have (lampUnits), the ones taken off given back; a lamp only moved, re-aimed,
 *   rewired or recoloured keeps its unit.</li>
 * </ul>
 * The shop draws SHOP_KW while working and the paint booth's power while painting.
 */
public record RefitJob(List<AssemblyPlan.Piece> pieces, Map<Alloy, Double> stockKg, List<AssemblyPlan.Piece> returned, Map<Alloy, Double> refundKg,
                       double repaintM2, int cans, int lampUnits, int lampsBack, double laborHours, double paintHours, double cureHours, boolean welding,
                       List<String> rebuilt, List<String> adjusted) {
    /** Tuning a part without new components, h; an axle's alignment, h; the shop's tools and lifts while working, kW. */
    public static final double ADJUST_HOURS = 0.5, ALIGN_HOURS = 1, SHOP_KW = 8;
    /** Reassembling a bench part's changed components: a minute a component, 3 s a kilogram (Plans' assembling rule). */
    public static final double PIECE_S = 60, PIECE_S_PER_KG = 3;

    public static final RefitJob NONE = new RefitJob(List.of(), Map.of(), List.of(), Map.of(), 0, 0, 0, 0, 0, 0, 0, false, List.of(), List.of());

    public RefitJob {
        pieces = List.copyOf(pieces); returned = List.copyOf(returned); rebuilt = List.copyOf(rebuilt); adjusted = List.copyOf(adjusted);
        stockKg = Map.copyOf(stockKg); refundKg = Map.copyOf(refundKg);
    }

    public boolean empty() { return rebuilt.isEmpty() && adjusted.isEmpty() && repaintM2 <= 0; }

    /** All the hours: the work, then painting, then curing. */
    public double hours() { return laborHours + paintHours + cureHours; }

    /** The energy, kWh: the shop while working, the booth's spraying and baking while painting. */
    public double kWh() {
        double bake = repaintM2 > 0 ? PaintJob.BAKE_HOURS : 0;
        return SHOP_KW * laborHours + PaintJob.SPRAY_KW * (paintHours - bake) + PaintJob.BAKE_KW * bake;
    }

    public static RefitJob of(VehicleBlueprint before, VehicleBlueprint after) {
        List<AssemblyPlan.Piece> pieces = new ArrayList<>(), returned = new ArrayList<>();
        Map<Alloy, Double> stock = new EnumMap<>(Alloy.class), refund = new EnumMap<>(Alloy.class);
        List<String> rebuilt = new ArrayList<>(), adjusted = new ArrayList<>();
        double laborS = 0, cureS = 0, repaint = 0;
        int lampUnits = 0, lampsBack = 0;
        boolean welding = false;
        for (VehicleBlueprint.Entry next : after.parts()) {
            VehicleBlueprint.Entry was = before.part(next.id());
            if (was == null) { continue; }
            PartDesign a = was.design(), b = next.design();
            if (Designs.write(a).equals(Designs.write(b))) { continue; }
            if (a instanceof dev.mcmod.hdfv.part.body.BodyDesign ba && b instanceof dev.mcmod.hdfv.part.body.BodyDesign bb
                    && !Objects.equals(ba.settings().lamps(), bb.settings().lamps())) {
                Map<String, Integer> units = new HashMap<>();
                lampUnits(bb).forEach((k, n) -> units.merge(k, n, Integer::sum));
                lampUnits(ba).forEach((k, n) -> units.merge(k, -n, Integer::sum));
                for (int n : units.values()) { if (n > 0) { lampUnits += n; } else { lampsBack -= n; } }
            }
            PartPlan pa = Plans.of(a), pb = Plans.of(b);
            // components: what the new plan has more of is made, what the old had more of comes off
            Map<Workpiece, Integer> diff = new LinkedHashMap<>();
            for (Workpiece w : pb.workpieces()) { diff.merge(one(w), w.count(), Integer::sum); }
            for (Workpiece w : pa.workpieces()) { diff.merge(one(w), -w.count(), Integer::sum); }
            int made = 0;
            double madeKg = 0;
            for (var e : diff.entrySet()) {
                Workpiece w = e.getKey();
                if (e.getValue() > 0) {
                    pieces.add(new AssemblyPlan.Piece(b, counted(w, e.getValue())));
                    made += e.getValue();
                    madeKg += e.getValue() * w.finishedKg();
                } else if (e.getValue() < 0) {
                    if (pa.roomBuilt()) { refund.merge(w.alloy(), -0.5 * e.getValue() * w.finishedKg(), Double::sum); }
                    else { returned.add(new AssemblyPlan.Piece(a, counted(w, -e.getValue()))); }
                }
            }
            // structure: each changed work built again, the old one's metal half back
            Map<String, StructureWork> old = new LinkedHashMap<>();
            for (StructureWork s : pa.structure()) { old.put(s.key(), s); }
            boolean shaped = false;
            for (StructureWork s : pb.structure()) {
                StructureWork o = old.remove(s.key());
                if (s.equals(o)) { continue; }
                shaped = true;
                stock.merge(s.alloy(), s.stockKg() + s.fillerKg(), Double::sum);
                laborS += s.laborS();
                cureS = Math.max(cureS, s.cureS());
                welding |= s.seamM() > 0 || s.fasteners() > 0;
                if (o != null) { refund.merge(o.alloy(), 0.5 * o.finishedKg(), Double::sum); }
            }
            for (StructureWork o : old.values()) { refund.merge(o.alloy(), 0.5 * o.finishedKg(), Double::sum); shaped = true; }
            if (made > 0 || shaped) {
                rebuilt.add(next.id());
                laborS += pa.roomBuilt() ? PIECE_S * made + PIECE_S_PER_KG * madeKg : AssemblyPlan.INSTALL_S + PIECE_S * made + PIECE_S_PER_KG * madeKg;
                if (shaped && b instanceof dev.mcmod.hdfv.part.body.BodyDesign) { repaint += PaintJob.area(b); }
            } else {
                adjusted.add(next.id());
                laborS += 3600 * (b instanceof AxleDesign ? ALIGN_HOURS : ADJUST_HOURS);
            }
        }
        if (rebuilt.isEmpty() && adjusted.isEmpty()) { return NONE; }
        int cans = repaint > 0 ? (int) Math.ceil(repaint / PaintJob.CAN_M2 - 1e-9) : 0;
        double paintH = repaint > 0 ? repaint * PaintJob.SPRAY_MIN_PER_M2 / 60 + PaintJob.BAKE_HOURS : 0;
        return new RefitJob(pieces, stock, returned, refund, repaint, cans, lampUnits, lampsBack, laborS / 3600, paintH, cureS / 3600, welding, rebuilt, adjusted);
    }

    /** How many lamps a body has: its own (a pair is two), or the kit's as they are. */
    public static int lamps(dev.mcmod.hdfv.part.body.BodyDesign d) {
        int n = 0;
        for (dev.mcmod.hdfv.look.LampSpec l : Refit.lamps(d)) { n += l.count(); }
        return n;
    }

    /**
     * A body's lamp units by kind (style and size to the centimetre; a pair is two): a lamp moved, re-aimed, rewired to
     * another role or given another lens or housing colour keeps its unit; another style or size is another unit.
     */
    static Map<String, Integer> lampUnits(dev.mcmod.hdfv.part.body.BodyDesign d) {
        Map<String, Integer> out = new HashMap<>();
        for (dev.mcmod.hdfv.look.LampSpec l : Refit.lamps(d)) { out.merge(l.style() + "/" + Math.round(l.sizeM() * 100), l.count(), Integer::sum); }
        return out;
    }

    /** A component taken as one (the comparison key: the same component whatever its count). */
    private static Workpiece one(Workpiece w) { return counted(w, 1); }

    private static Workpiece counted(Workpiece w, int count) {
        return new Workpiece(w.key(), count, w.alloy(), w.form(), w.finishedKg(), w.stockKg(), w.route(), w.envelope());
    }
}
