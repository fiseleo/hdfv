package dev.mcmod.hdfv.manufacture;

import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.part.TrackPart;
import dev.mcmod.hdfv.part.machine.DrumDesign;
import dev.mcmod.hdfv.part.machine.HookDesign;
import dev.mcmod.hdfv.part.machine.HydraulicMotorDesign;
import dev.mcmod.hdfv.part.machine.HydraulicPumpDesign;
import dev.mcmod.hdfv.part.machine.HydraulicUnit;
import dev.mcmod.hdfv.part.machine.LinkDesign;
import dev.mcmod.hdfv.part.machine.OutriggerDesign;
import dev.mcmod.hdfv.part.machine.TrackDesign;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 0.26: the workpieces of the heavy machinery's parts (dev.mcmod.hdfv.part.machine), made at the bench like an axle's:
 * the shares of each part's mass by piece follow the usual make-up of these parts (rules, the order of the makers'
 * breakdowns: a crawler's chain is forged links and ground pins, its shoes rolled plate, its rollers forged and its
 * sprockets cast with their teeth cut; a boom is welded plate with cast bosses bored for its pins; a cylinder's barrel
 * is honed tube, its rod ground and polished bar; a pump's housing is cast and its rotating group forged and lapped; a
 * hook is forged, its sheaves cast and grooved). The pieces add up to the part's mass.
 */
final class MachinePlanner {
    private MachinePlanner() {}

    /** The pieces of a machine's part (empty for a design that is not one). */
    static List<Workpiece> of(PartDesign d) {
        double kg = PartPlanner.massKg(d);
        return switch (d) {
            case TrackDesign t -> track(t, kg);
            case DrumDesign r -> drum(r, kg);
            case HydraulicPumpDesign p -> pump(p, kg);
            case HydraulicMotorDesign m -> motor(m, kg);
            case LinkDesign l -> link(l, kg);
            case HookDesign h -> hook(h, kg);
            case OutriggerDesign o -> outrigger(o, kg);
            case dev.mcmod.hdfv.part.machine.OperatorCabDesign c -> cab(c, kg);
            case dev.mcmod.hdfv.part.machine.EngineHoodDesign e -> hood(e, kg);
            case dev.mcmod.hdfv.part.machine.WorkToolDesign w -> tool(w, kg);
            default -> List.of();
        };
    }

    /** Most a plate or tube piece weighs, kg: heavier ones are cut into more pieces (what the press brake and the shop's crane take). */
    static final double MAX_PLATE_PIECE_KG = 300;

    /** A piece of share of the part's kg, split over count copies (plate and tube into pieces of at most MAX_PLATE_PIECE_KG). */
    private static Shop.Piece piece(String key, int count, Alloy alloy, Form form, double share, double kg) {
        int n = Math.max(count, 1);
        if ((form == Form.PLATE || form == Form.TUBE) && share * kg / n > MAX_PLATE_PIECE_KG) { n = (int) Math.ceil(share * kg / MAX_PLATE_PIECE_KG); }
        return Shop.piece(key, n, alloy, form, share * kg / n);
    }

    static List<Workpiece> track(TrackDesign t, double kg) {
        List<Workpiece> out = new ArrayList<>();
        if (t.shoe() == TrackPart.Shoe.RUBBER) {
            out.add(piece("rubber_track", 2, Alloy.RUBBER, Form.RUBBER, 0.45, kg).size(3, 0.4, 0.08).build());
        } else {
            out.add(piece("track_shoe", 80, Alloy.STEEL, Form.PLATE, 0.22, kg).size(0.6, 0.19, 0.03).cut(Process.MILL, 1, 0.12, 0.6, 11)
                    .drill(4, 0.022, 0.03, 11).build());
            out.add(piece("track_link", 160, Alloy.ALLOY_STEEL, Form.FORGING, 0.15, kg).size(0.25, 0.06, 0.12)
                    .cut(Process.BORE, 1, Math.PI * 0.05 * 0.06 * 2, 0.05, 8).build());
            out.add(piece("track_pin", 80, Alloy.ALLOY_STEEL, Form.BAR, 0.08, kg).turned(0.3, 0.045)
                    .cut(Process.TURN, 0.6, Math.PI * 0.045 * 0.3, 0.045, 8).cut(Process.GRIND, 0.4, Math.PI * 0.045 * 0.3, 0.045, 6).build());
        }
        out.add(piece("track_roller", 14, Alloy.STEEL, Form.FORGING, 0.12, kg).turned(0.3, 0.25)
                .cut(Process.TURN, 0.6, Math.PI * 0.25 * 0.3, 0.25, 9).cut(Process.BORE, 0.4, Math.PI * 0.08 * 0.3, 0.08, 7).build());
        out.add(piece("track_idler", 2, Alloy.STEEL, Form.CASTING, 0.06, kg).modulus(0.02).turned(0.25, 0.6)
                .cut(Process.TURN, 0.7, Math.PI * 0.6 * 0.25, 0.6, 9).cut(Process.BORE, 0.3, Math.PI * 0.1 * 0.25, 0.1, 7).build());
        out.add(piece("track_sprocket", 2, Alloy.ALLOY_STEEL, Form.CASTING, 0.05, kg).modulus(0.02).turned(0.2, 0.7)
                .cut(Process.MILL, 0.6, 0.3, 0.7, 9).as(Feature.GEAR_TEETH).drill(12, 0.022, 0.05, 11).build());
        double frames = 0.32;
        out.add(piece("track_frame", 2, Alloy.STEEL, Form.PLATE, frames, kg).size(3.5, 0.5, 0.6).weld(14)
                .cut(Process.BORE, 1, Math.PI * 0.1 * 0.1 * 4, 0.1, 9).build());
        return out;
    }

    static List<Workpiece> drum(DrumDesign r, double kg) {
        double w = r.widthM(), dia = r.diameterM();
        return List.of(
                piece("drum_shell", 1, Alloy.STEEL, Form.PLATE, 0.55, kg).size(w, Math.PI * dia, 0.04).weld(w + Math.PI * dia)
                        .cut(Process.TURN, 1, Math.PI * dia * w, dia, 10).build(),
                piece("drum_end_plate", 2, Alloy.STEEL, Form.PLATE, 0.2, kg).size(dia, dia, 0.04).weld(Math.PI * dia)
                        .cut(Process.BORE, 1, Math.PI * 0.2 * 0.04, 0.2, 8).build(),
                piece("vibrator_shaft", 1, Alloy.ALLOY_STEEL, Form.BAR, 0.08, kg).turned(w, 0.1)
                        .cut(Process.TURN, 0.5, Math.PI * 0.1 * w, 0.1, 8).cut(Process.MILL, 0.05, 0.002, 0.1, 8).as(Feature.SPLINE).cut(Process.GRIND, 0.5, Math.PI * 0.1 * 0.3, 0.1, 6).build(),
                piece("eccentric_weight", 2, Alloy.DUCTILE_IRON, Form.CASTING, 0.1, kg).modulus(0.03).size(0.4, 0.3, 0.2)
                        .cut(Process.BORE, 1, Math.PI * 0.1 * 0.2, 0.1, 8).build(),
                piece("vibrator_housing", 2, Alloy.DUCTILE_IRON, Form.CASTING, 0.07, kg).modulus(0.015).size(0.5, 0.5, 0.3)
                        .cut(Process.BORE, 0.7, Math.PI * 0.2 * 0.1, 0.2, 7).drill(12, 0.018, 0.04, 11).build());
    }

    static List<Workpiece> pump(HydraulicPumpDesign p, double kg) {
        if (p.pumpKind() == HydraulicUnit.GEAR) {
            return List.of(
                    piece("pump_housing", 1, Alloy.ALUMINIUM_CAST, Form.CASTING, 0.6, kg).modulus(0.008).size(0.2, 0.12, 0.12)
                            .cut(Process.BORE, 1, 0.02, 0.06, 7).drill(4, 0.012, 0.03, 11).build(),
                    piece("unit_gears", 2, Alloy.ALLOY_STEEL, Form.BAR, 0.3, kg).turned(0.05, 0.06)
                            .cut(Process.MILL, 0.7, 0.01, 0.06, 7).as(Feature.GEAR_TEETH).cut(Process.GRIND, 0.3, 0.01, 0.06, 6).build(),
                    piece("unit_shaft", 1, Alloy.ALLOY_STEEL, Form.BAR, 0.1, kg).turned(0.15, 0.025)
                            .cut(Process.TURN, 0.6, 0.012, 0.025, 8).cut(Process.MILL, 0.05, 0.002, 0.025, 8).as(Feature.SPLINE).cut(Process.GRIND, 0.4, 0.008, 0.025, 6).build());
        }
        return List.of(
                piece("pump_housing", 1, Alloy.DUCTILE_IRON, Form.CASTING, 0.55, kg).modulus(0.01).size(0.3, 0.2, 0.2)
                        .cut(Process.BORE, 0.7, 0.04, 0.12, 7).drill(8, 0.014, 0.03, 11).build(),
                piece("rotating_group", 1, Alloy.ALLOY_STEEL, Form.FORGING, 0.25, kg).turned(0.12, 0.12)
                        .cut(Process.BORE, 0.6, 9 * Math.PI * 0.02 * 0.06, 0.02, 6).lap(0.01, 0.12, 5).build(),
                piece("swash_plate", 1, Alloy.ALLOY_STEEL, Form.FORGING, 0.1, kg).size(0.15, 0.15, 0.04)
                        .cut(Process.MILL, 0.6, 0.02, 0.15, 8).cut(Process.GRIND, 0.4, 0.02, 0.15, 6).build(),
                piece("unit_shaft", 1, Alloy.ALLOY_STEEL, Form.BAR, 0.1, kg).turned(0.25, 0.035)
                        .cut(Process.TURN, 0.6, 0.027, 0.035, 8).cut(Process.MILL, 0.05, 0.002, 0.035, 8).as(Feature.SPLINE).cut(Process.GRIND, 0.4, 0.015, 0.035, 6).build());
    }

    static List<Workpiece> motor(HydraulicMotorDesign m, double kg) {
        List<Workpiece> out = new ArrayList<>();
        double unit = m.winch() ? 0.35 : 0.45, drum = m.winch() ? 0.35 : 0;
        out.add(piece("motor_housing", 1, Alloy.DUCTILE_IRON, Form.CASTING, 0.55 * unit, kg).modulus(0.01).size(0.3, 0.25, 0.25)
                .cut(Process.BORE, 0.7, 0.05, 0.14, 7).drill(8, 0.016, 0.03, 11).build());
        out.add(piece("rotating_group", 1, Alloy.ALLOY_STEEL, Form.FORGING, 0.3 * unit, kg).turned(0.14, 0.14)
                .cut(Process.BORE, 0.6, 9 * Math.PI * 0.025 * 0.07, 0.025, 6).lap(0.015, 0.14, 5).build());
        out.add(piece("unit_shaft", 1, Alloy.ALLOY_STEEL, Form.BAR, 0.15 * unit, kg).turned(0.3, 0.05)
                .cut(Process.TURN, 0.6, 0.05, 0.05, 8).cut(Process.MILL, 0.05, 0.002, 0.05, 8).as(Feature.SPLINE).cut(Process.GRIND, 0.4, 0.02, 0.05, 6).build());
        double reducer = 1 - unit - drum;
        out.add(piece("final_drive_gears", 3, Alloy.ALLOY_STEEL, Form.FORGING, 0.45 * reducer, kg).turned(0.08, 0.25)
                .cut(Process.MILL, 0.7, 0.06, 0.25, 7).as(Feature.GEAR_TEETH).cut(Process.GRIND, 0.3, 0.03, 0.25, 6).build());
        out.add(piece("final_drive_housing", 1, Alloy.DUCTILE_IRON, Form.CASTING, 0.55 * reducer, kg).modulus(0.02).turned(0.35, 0.6)
                .cut(Process.TURN, 0.5, 0.6, 0.6, 9).cut(Process.BORE, 0.5, 0.2, 0.4, 7).drill(16, 0.018, 0.04, 11).build());
        if (m.winch()) {
            out.add(piece("winch_drum", 1, Alloy.STEEL, Form.PLATE, 0.65 * drum, kg).size(0.8, 1.5, 0.03).weld(2.5)
                    .cut(Process.TURN, 1, 1.2, 0.5, 9).build());
            out.add(piece("winch_flange", 2, Alloy.STEEL, Form.PLATE, 0.2 * drum, kg).size(0.8, 0.8, 0.03).weld(1.5).build());
            out.add(piece("wire_rope", 1, Alloy.STEEL, Form.WIRE, 0.15 * drum, kg).build());
        }
        return out;
    }

    static List<Workpiece> link(LinkDesign l, double kg) {
        double len = Math.max(l.lengthM(), 0.1), h = Math.max(l.sectionHeightM(), 0.05), w = Math.max(l.sectionWidthM(), 0.05);
        List<Workpiece> out = new ArrayList<>();
        switch (l.linkKind()) {
            case LATTICE -> {
                out.add(piece("lattice_chord", 4, Alloy.STEEL, Form.TUBE, 0.6, kg).size(len, 0.15, 0.15).weld(2 * len).build());
                out.add(piece("lattice_lacing", 1, Alloy.STEEL, Form.TUBE, 0.3, kg).size(len, w, h).weld(4 * len).build());
                out.add(piece("link_pin", 2, Alloy.ALLOY_STEEL, Form.BAR, 0.1, kg).turned(w, 0.1)
                        .cut(Process.TURN, 0.6, Math.PI * 0.1 * w, 0.1, 8).cut(Process.GRIND, 0.4, Math.PI * 0.1 * w, 0.1, 6).build());
            }
            case SLEW_RING -> {
                out.add(piece("slewing_ring", 2, Alloy.ALLOY_STEEL, Form.FORGING, 0.75, kg).turned(h, len)
                        .cut(Process.TURN, 0.4, Math.PI * len * h, len, 8).cut(Process.MILL, 0.4, Math.PI * len * h, len, 8).as(Feature.GEAR_TEETH)
                        .cut(Process.GRIND, 0.2, Math.PI * len * 0.05, len, 6).drill(36, 0.022, 0.08, 11).build());
                out.add(piece("swing_reducer", 1, Alloy.DUCTILE_IRON, Form.CASTING, 0.25, kg).modulus(0.02).turned(0.5, 0.4)
                        .cut(Process.BORE, 0.6, 0.2, 0.3, 7).cut(Process.MILL, 0.4, 0.05, 0.2, 7).as(Feature.GEAR_TEETH).build());
            }
            default -> {
                boolean ram = l.actuation() == LinkDesign.Actuation.RAM;
                double cylinders = ram ? 0.25 : 0, pins = 0.08, bosses = 0.1, plates = 1 - cylinders - pins - bosses;
                out.add(piece("link_plates", 1, Alloy.STEEL, Form.PLATE, plates, kg).size(len, w, h).weld(4 * len).build());
                out.add(piece("link_boss", 4, Alloy.STEEL, Form.CASTING, bosses, kg).modulus(0.02).turned(w, 0.25)
                        .cut(Process.BORE, 1, Math.PI * 0.1 * w, 0.1, 7).weld(0.8).build());
                out.add(piece("link_pin", 2, Alloy.ALLOY_STEEL, Form.BAR, pins, kg).turned(w + 0.1, 0.1)
                        .cut(Process.TURN, 0.6, Math.PI * 0.1 * w, 0.1, 8).cut(Process.GRIND, 0.4, Math.PI * 0.1 * w, 0.1, 6).build());
                if (ram) {
                    double stroke = Math.max(0.3, 0.3 * len);
                    out.add(piece("cylinder_barrel", 1, Alloy.STEEL, Form.TUBE, 0.45 * cylinders, kg).size(stroke + 0.5, 0.16, 0.16)
                            .cut(Process.BORE, 0.6, Math.PI * 0.12 * stroke, 0.12, 8).cut(Process.GRIND, 0.4, Math.PI * 0.12 * stroke, 0.12, 6).weld(0.8).build());
                    out.add(piece("cylinder_rod", 1, Alloy.ALLOY_STEEL, Form.BAR, 0.4 * cylinders, kg).turned(stroke + 0.3, 0.09)
                            .cut(Process.TURN, 0.5, Math.PI * 0.09 * stroke, 0.09, 8).cut(Process.GRIND, 0.5, Math.PI * 0.09 * stroke, 0.09, 6)
                            .polish(Math.PI * 0.09 * stroke, 0.09).build());
                    out.add(piece("cylinder_head", 1, Alloy.STEEL, Form.BAR, 0.15 * cylinders, kg).turned(0.15, 0.16)
                            .cut(Process.TURN, 0.5, 0.08, 0.16, 8).cut(Process.BORE, 0.5, 0.04, 0.09, 7).thread(0.16, 0.05, 8).build());
                }
            }
        }
        return out;
    }

    static List<Workpiece> hook(HookDesign h, double kg) {
        int sheaves = Math.max(h.sheaves(), 1);
        return List.of(
                piece("hook_forging", 1, Alloy.ALLOY_STEEL, Form.FORGING, 0.3, kg).size(0.6, 0.3, 0.15)
                        .cut(Process.TURN, 0.6, 0.05, 0.12, 9).thread(0.1, 0.12, 8).build(),
                piece("hook_sheave", sheaves, Alloy.STEEL, Form.CASTING, 0.3, kg).modulus(0.015).turned(0.1, 0.6)
                        .cut(Process.TURN, 0.6, 0.2, 0.6, 9).cut(Process.BORE, 0.4, 0.02, 0.1, 7).build(),
                piece("hook_side_plate", 2, Alloy.STEEL, Form.PLATE, 0.24, kg).size(0.9, 0.7, 0.03)
                        .cut(Process.BORE, 1, 0.02, 0.1, 8).profile().build(),
                piece("hook_crosshead", 1, Alloy.ALLOY_STEEL, Form.FORGING, 0.16, kg).size(0.5, 0.15, 0.15)
                        .cut(Process.BORE, 0.6, 0.02, 0.1, 8).cut(Process.MILL, 0.4, 0.05, 0.15, 9).build());
    }

    /**
     * A work tool from its pieces' masses (WorkToolDesign.massBreakdownKg, scaled to the part's mass): wear plate (lips,
     * edges, liners) profiled and bevelled; teeth, tips, picks and chain teeth cast of alloy steel and ground; disc
     * cutters forged; bosses, brackets, locks, hubs and mounts cast and bored; tines, shanks, chisels and weights forged
     * and machined; belts and bristles of rubber; idlers, pulleys, exciters, gearboxes and nozzles turned and bored; the
     * ram as a cylinder's barrel, rod and head; the rest welded plate.
     */
    static List<Workpiece> tool(dev.mcmod.hdfv.part.machine.WorkToolDesign w, double kg) {
        java.util.Map<String, Double> parts = w.massBreakdownKg();
        double sum = 0;
        for (double x : parts.values()) { sum += Math.max(x, 0); }
        if (!(sum > 0)) { return List.of(piece("tool_structure", 1, Alloy.STEEL, Form.PLATE, 1, kg).size(1, 1, 0.02).weld(4).build()); }
        double width = Math.max(w.toolWidthM(), 0.1);
        List<Workpiece> out = new ArrayList<>();
        for (java.util.Map.Entry<String, Double> e : parts.entrySet()) {
            double share = Math.max(e.getValue(), 0) / sum;
            if (share <= 0) { continue; }
            String key = e.getKey(), name = key.startsWith("tool_") ? key.substring(5) : key;
            switch (name) {
                case "cutting_edge", "lip" -> out.add(piece(key, 1, Alloy.ALLOY_STEEL, Form.PLATE, share, kg).size(width, 0.3, 0.05).profile()
                        .cut(Process.MILL, 1, 0.05 * width, 0.05, 10).build());
                case "teeth", "tips", "picks", "chain_teeth", "bit" -> out.add(piece(key, 1, Alloy.ALLOY_STEEL, Form.CASTING, share, kg).modulus(0.01)
                        .size(0.3, 0.1, 0.1).cut(Process.GRIND, 1, 0.02, 0.1, 9).build());
                case "disc_cutters" -> out.add(piece(key, 1, Alloy.ALLOY_STEEL, Form.FORGING, share, kg).turned(0.1, 0.43)
                        .cut(Process.TURN, 0.6, 0.15, 0.43, 8).cut(Process.GRIND, 0.4, 0.03, 0.43, 7).build());
                case "brackets", "lock", "kelly_box", "hub", "mounts", "rotator_mount", "carriage", "head", "housing", "helmet" ->
                        out.add(piece(key, 1, Alloy.STEEL, Form.CASTING, share, kg).modulus(0.02).size(0.6, 0.4, 0.3)
                                .cut(Process.BORE, 0.7, Math.PI * 0.1 * 0.2, 0.1, 8).drill(8, 0.022, 0.04, 11).build());
                case "tines", "shanks", "chisel", "drop_weight" -> out.add(piece(key, 1, Alloy.ALLOY_STEEL, Form.FORGING, share, kg).size(1.2, 0.15, 0.1)
                        .cut(Process.MILL, 1, 0.3, 0.15, 9).build());
                case "belt", "bristles" -> out.add(piece(key, 1, Alloy.RUBBER, Form.RUBBER, share, kg).size(width, 1, 0.02).build());
                case "idlers", "pulleys", "exciter", "gearbox", "nozzles", "power_cell", "fittings", "impeller" ->
                        out.add(piece(key, 1, Alloy.STEEL, Form.BAR, share, kg).turned(0.3, 0.2)
                                .cut(Process.TURN, 0.6, 0.2, 0.2, 9).cut(Process.BORE, 0.4, 0.03, 0.06, 8).build());
                case "ram" -> {
                    out.add(piece("cylinder_barrel", 1, Alloy.STEEL, Form.TUBE, 0.45 * share, kg).size(1, 0.16, 0.16)
                            .cut(Process.BORE, 0.6, 0.3, 0.12, 8).cut(Process.GRIND, 0.4, 0.3, 0.12, 6).weld(0.8).build());
                    out.add(piece("cylinder_rod", 1, Alloy.ALLOY_STEEL, Form.BAR, 0.4 * share, kg).turned(1, 0.09)
                            .cut(Process.TURN, 0.5, 0.25, 0.09, 8).cut(Process.GRIND, 0.5, 0.25, 0.09, 6).polish(0.25, 0.09).build());
                    out.add(piece("cylinder_head", 1, Alloy.STEEL, Form.BAR, 0.15 * share, kg).turned(0.15, 0.16)
                            .cut(Process.TURN, 0.5, 0.08, 0.16, 8).cut(Process.BORE, 0.5, 0.04, 0.09, 7).thread(0.16, 0.05, 8).build());
                }
                default -> out.add(piece(key, 1, Alloy.STEEL, Form.PLATE, share, kg).size(width, 1, 0.03).weld(2 * width).build());
            }
        }
        return out;
    }

    /**
     * An operator cab (2026-11-22): its frame of tube bent and welded at its joints, a FOPS guard of plate, the floor
     * plate, and the panels pressed from sheet (the glass and the fittings with them, as a body's skin carries its
     * openings: StructurePlanner).
     */
    static List<Workpiece> cab(dev.mcmod.hdfv.part.machine.OperatorCabDesign c, double kg) {
        Map<String, Double> parts = c.massBreakdownKg();
        double sum = 0;
        for (double x : parts.values()) { sum += x; }
        double l = Math.max(c.lengthM(), 0.1), w = Math.max(c.widthM(), 0.1), h = Math.max(c.heightM(), 0.1);
        double frame = parts.getOrDefault("cab_frame", 0.0) / sum, guard = parts.getOrDefault("cab_fops_guard", 0.0) / sum,
                floor = parts.getOrDefault("cab_floor", 0.0) / sum, panels = 1 - frame - guard - floor;
        List<Workpiece> out = new ArrayList<>();
        out.add(piece("cab_frame", 1, Alloy.STEEL, Form.TUBE, frame, kg).size(h, 0.12, 0.08).weld(16 * 0.4).build());
        if (guard > 0) { out.add(piece("cab_fops_guard", 1, Alloy.STEEL, Form.PLATE, guard, kg).size(l, w, 0.008).profile().weld(2 * (l + w)).build()); }
        out.add(piece("cab_floor", 1, Alloy.STEEL, Form.PLATE, floor, kg).size(l, w, 0.006).profile().drill(12, 0.012, 0.006, 11).build());
        out.add(piece("cab_panels", 4, Alloy.STEEL, Form.PLATE, panels, kg).size(l, h, 0.003).profile().weld(l + h).build());
        return out;
    }

    /**
     * An engine hood (2026-11-22): its frame of pressed channel welded round its edges, its panels and doors pressed from
     * sheet (the lining, hinges and latches with them), the louvres punched, the stack rolled from tube.
     */
    static List<Workpiece> hood(dev.mcmod.hdfv.part.machine.EngineHoodDesign e, double kg) {
        Map<String, Double> parts = e.massBreakdownKg();
        double sum = 0;
        for (double x : parts.values()) { sum += x; }
        double l = Math.max(e.lengthM(), 0.1), w = Math.max(e.widthM(), 0.1), h = Math.max(e.heightM(), 0.1);
        double frame = parts.getOrDefault("hood_frame", 0.0) / sum, grille = parts.getOrDefault("hood_grille", 0.0) / sum,
                stack = parts.getOrDefault("hood_stack", 0.0) / sum, panels = 1 - frame - grille - stack;
        List<Workpiece> out = new ArrayList<>();
        out.add(piece("hood_frame", 1, Alloy.STEEL, Form.TUBE, frame, kg).size(l, 0.08, 0.04).weld(8 * 0.3 + 0.1 * e.perimeterM()).build());
        out.add(piece("hood_panels", 5, Alloy.STEEL, Form.PLATE, panels, kg).size(l, Math.max(w, h), 0.002).profile().weld(0.5 * (l + h)).build());
        out.add(piece("hood_grille", e.grille() == dev.mcmod.hdfv.part.machine.EngineHoodDesign.Grille.SIDES ? 2 : 1, Alloy.STEEL, Form.PLATE, grille, kg)
                .size(Math.max(0.3, 0.5 * l), Math.max(0.3, 0.6 * h), 0.003).profile().drill(40, 0.012, 0.003, 11).build());
        if (stack > 0) { out.add(piece("hood_stack", 1, Alloy.STEEL, Form.TUBE, stack, kg).size(e.stackM() + 0.3, 0.125, 0.125).weld(0.4).build()); }
        return out;
    }

    static List<Workpiece> outrigger(OutriggerDesign o, double kg) {
        double stroke = Math.max(o.strokeM(), 0.1), pad = Math.max(o.padDiameterM(), 0.1);
        return List.of(
                piece("cylinder_barrel", 1, Alloy.STEEL, Form.TUBE, 0.25, kg).size(stroke + 0.4, 0.2, 0.2)
                        .cut(Process.BORE, 0.6, Math.PI * 0.16 * stroke, 0.16, 8).cut(Process.GRIND, 0.4, Math.PI * 0.16 * stroke, 0.16, 6).build(),
                piece("cylinder_rod", 1, Alloy.ALLOY_STEEL, Form.BAR, 0.22, kg).turned(stroke + 0.2, 0.12)
                        .cut(Process.TURN, 0.5, Math.PI * 0.12 * stroke, 0.12, 8).cut(Process.GRIND, 0.5, Math.PI * 0.12 * stroke, 0.12, 6)
                        .polish(Math.PI * 0.12 * stroke, 0.12).build(),
                piece("cylinder_head", 1, Alloy.STEEL, Form.BAR, 0.08, kg).turned(0.15, 0.2)
                        .cut(Process.TURN, 0.5, 0.1, 0.2, 8).thread(0.2, 0.05, 8).build(),
                piece("jack_housing", 1, Alloy.STEEL, Form.PLATE, 0.3, kg).size(stroke + 0.4, 0.3, 0.3).weld(4 * (stroke + 0.4)).build(),
                piece("outrigger_pad", 1, Alloy.STEEL, Form.PLATE, 0.15, kg).size(pad, pad, 0.04).weld(Math.PI * pad)
                        .cut(Process.BORE, 1, 0.02, 0.1, 9).profile().build());
    }
}
