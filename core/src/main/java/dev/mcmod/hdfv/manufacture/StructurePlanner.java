package dev.mcmod.hdfv.manufacture;

import dev.mcmod.aerocore.api.body.BodyShape;
import dev.mcmod.aerocore.api.body.BodyStructure;
import dev.mcmod.aerocore.api.manufacture.Joining;
import dev.mcmod.aerocore.api.manufacture.Laminating;
import dev.mcmod.aerocore.api.vehicle.MassSpec;
import dev.mcmod.hdfv.part.HullDesign;
import dev.mcmod.hdfv.part.PlaningHullDesign;
import dev.mcmod.hdfv.part.air.WingDesign;
import dev.mcmod.hdfv.part.body.BodyDesign;
import dev.mcmod.hdfv.part.body.BodyModuleDesign;

import java.util.ArrayList;
import java.util.List;

/**
 * How the big structures are built in an assembly room (0.7): bodies, wings and hulls. Their frames, ribs and spar
 * caps are formed or machined first (workpieces); skins, floors and webs come as stock; the room joins them.
 *
 * <ul>
 * <li>Joint by build and material (Joint): riveted aluminium semi-monocoques, spot-welded steel monocoques, MIG-welded
 *     steel and aluminium hulls and space frames, TIG for titanium and stainless, bonded composites, glued wood.</li>
 * <li>Seams of a body module of girth g (its stations' mean perimeter) and length L: each frame round the girth
 *     (floor(L / spacing) + 1 of them, as BodyStructure counts its mass), stringers every STRINGER_M round it in a
 *     semi-monocoque, skin panels of PANEL_W x PANEL_L edged with seams, the joint to the next module, the door
 *     frames.</li>
 * <li>Labour (rules from the trades): RIVET_S a rivet (drill, deburr, set) at RIVET_PITCH in two rows; SPOT_S a spot
 *     weld at SPOT_PITCH; arc welds by AeroCore's Joining (3 mm fillets by hand); BOND_S_PER_M and GLUE_S_PER_M of
 *     seam; PANEL_S_PER_M2 to fit and form skin; a laid-up skin's plies by AeroCore's Laminating at 1.5 m^2 an hour
 *     and a day's cure at room temperature (AMBIENT_CURE_S).</li>
 * </ul>
 */
final class StructurePlanner {
    private StructurePlanner() {}

    static final double STRINGER_M = 0.2, PANEL_W = 1.25, PANEL_L = 2.5, RIVET_PITCH = 0.025, SPOT_PITCH = 0.04, RIVET_S = 20, SPOT_S = 3,
            BOND_S_PER_M = 120, GLUE_S_PER_M = 90, PANEL_S_PER_M2 = 600, AMBIENT_CURE_S = 86400, RIVET_KG = 0.001, ADHESIVE_KG_PER_M = 0.012;

    static Joint joint(BodyStructure.Build build, Alloy a) {
        return switch (a.family) {
            case COMPOSITE -> Joint.BOND;
            case WOOD -> Joint.GLUE;
            case TITANIUM, STAINLESS, NICKEL -> Joint.TIG;
            case ALUMINIUM, MAGNESIUM -> build == BodyStructure.Build.SEMI_MONOCOQUE ? Joint.RIVET : Joint.MIG;
            default -> build == BodyStructure.Build.MONOCOQUE ? Joint.SPOT_WELD : Joint.MIG;
        };
    }

    /** The joining of seams of a length (m) in an alloy by a joint, with a skin of an area (m^2) to fit: a piece of structure work. */
    static StructureWork work(String key, Alloy a, Form form, double finishedKg, Joint joint, double seamM, double skinM2, double thicknessM) {
        double stock = finishedKg / form.yield(), filler = 0, labour = PANEL_S_PER_M2 * skinM2, cure = 0;
        int fasteners = 0;
        switch (joint) {
            case RIVET -> { fasteners = Joining.fasteners(seamM, RIVET_PITCH, 2); filler = fasteners * RIVET_KG; labour += fasteners * RIVET_S; }
            case SPOT_WELD -> { fasteners = Joining.fasteners(seamM, SPOT_PITCH, 1); labour += fasteners * SPOT_S; }
            case MIG, TIG -> {
                filler = Joining.metalKg(Joining.filletAreaM2(Shop.WELD_LEG_M), seamM, a.density);
                labour += Shop.weldS(a, seamM);
            }
            case BOND -> {
                filler = seamM * ADHESIVE_KG_PER_M;
                labour += seamM * BOND_S_PER_M;
                if (form == Form.LAMINATE) {
                    labour += Laminating.layupS(skinM2, Math.max(1, (int) Math.round(thicknessM / Shop.PLY_M)), Shop.LAYUP_M2_PER_H / 3600);
                    cure = AMBIENT_CURE_S;
                }
            }
            case GLUE -> { filler = seamM * ADHESIVE_KG_PER_M; labour += seamM * GLUE_S_PER_M; cure = AMBIENT_CURE_S; }
        }
        return new StructureWork(key, a, form, finishedKg, stock, joint, seamM, filler, fasteners, labour, cure);
    }

    /** A body: per module its frames formed at the press (workpieces), skin and floor joined in the room. */
    static void body(BodyDesign d, List<Workpiece> pieces, List<StructureWork> works) {
        BodyShape shape = d.shape();
        List<BodyStructure.ModuleMass> masses = BodyStructure.masses(shape, d.structure());
        for (int i = 0; i < d.modules().size(); i++) {
            BodyModuleDesign m = d.modules().get(i);
            BodyStructure.ModuleMass mm = masses.get(i);
            Alloy a = Alloy.of(m.material());
            String n = "m" + (i + 1);
            double girth = girth(shape, i), len = m.lengthM();
            int frames = Math.max(1, (int) Math.floor(len / Math.max(m.frameSpacingM(), 0.05)) + 1);
            boolean composite = a.family == Alloy.Family.COMPOSITE, wood = a.family == Alloy.Family.WOOD;
            if (mm.framesKg() > 0) {
                Shop.Piece frame = composite ? Shop.piece(n + "_frame", frames, a, Form.LAMINATE, mm.framesKg() / frames).thickness(Math.max(2 * m.skinM(), 0.002))
                        : wood ? Shop.piece(n + "_frame", frames, a, Form.TIMBER, mm.framesKg() / frames).cut(Process.PLANE, 1, girth * 0.05, girth / 4, 11)
                        : Shop.piece(n + "_frame", frames, a, Form.BAR, mm.framesKg() / frames).yield(0.9).drill(8, 0.005, 0.003, 11);
                pieces.add(frame.build());
            }
            Joint joint = joint(m.build(), a);
            double skinArea = girth * len, stringers = m.build() == BodyStructure.Build.SEMI_MONOCOQUE ? girth / STRINGER_M * len : 0;
            double panels = skinArea / (PANEL_W * PANEL_L), seams = frames * girth + stringers + panels * (PANEL_W + PANEL_L) + girth
                    + m.doors() * 2 * (m.doorWidthM() + m.doorHeightM());
            works.add(work(n + "_skin", a, composite ? Form.LAMINATE : wood ? Form.TIMBER : Form.SHEET, mm.skinKg() + mm.openingsKg(), joint, seams, skinArea,
                    Math.max(m.skinM(), 0.001)));
            if (mm.floorKg() > 0) {
                double floorArea = 0.8 * len * Math.min(m.widthM(), girth / 3);
                works.add(work(n + "_floor", a, composite ? Form.LAMINATE : wood ? Form.TIMBER : Form.PLATE, mm.floorKg(), joint, 2 * (len + floorArea / Math.max(len, 0.1)),
                        floorArea, 0.003));
            }
        }
        // 0.16: a car's kit, moulded or formed from its material and fitted to the shell
        if (d.kitted() && !d.kit().none()) {
            dev.mcmod.hdfv.look.KitLayout k = dev.mcmod.hdfv.look.KitLayout.of(d, shape);
            if (k.massKg > 0) {
                Alloy a = Alloy.of(d.kit().material());
                boolean composite = a.family == Alloy.Family.COMPOSITE;
                double seams = 4 * Math.sqrt(k.areaM2) + k.boxes.size() * 0.5;
                works.add(work("kit", a, composite ? Form.LAMINATE : Form.SHEET, k.massKg, composite ? Joint.BOND : Joint.MIG, seams, k.areaM2,
                        dev.mcmod.hdfv.look.KitLayout.KIT_THICKNESS_M));
            }
        }
    }

    /**
     * 0.25: a freight container (StorageBoxDesign CONTAINER): its eight corner castings cast and their apertures milled,
     * its rails, end rails and corner posts pressed from plate and its floor's cross members from sheet (each its share
     * of StorageBoxDesign.FRAME_KG_PER_M, CROSS_KG_PER_M) at the shop; the room welds the corrugated walls, roof and doors
     * (panels PANEL_W wide, welded to each other and all round to the frame: MIG) and lays the plywood floor on the cross
     * members (glued and screwed).
     */
    static void container(dev.mcmod.hdfv.part.StorageBoxDesign d, List<Workpiece> pieces, List<StructureWork> works) {
        double l = d.lengthM(), w = d.widthM(), h = d.heightM(), kg = dev.mcmod.hdfv.part.StorageBoxDesign.FRAME_KG_PER_M;
        double[] in = d.inside();
        Alloy a = Alloy.of(d.material());
        pieces.add(Shop.piece("container_corner", 8, Alloy.STEEL, Form.CASTING, dev.mcmod.hdfv.part.StorageBoxDesign.CASTING_KG).modulus(0.01).size(0.178, 0.162, 0.118)
                .cut(Process.MILL, 1, 0.06, 0.18, 10).build());
        pieces.add(Shop.piece("container_side_rail", 4, Alloy.STEEL, Form.PLATE, l * kg).size(l, 0.16, 0.05).weld(0.5).build());
        pieces.add(Shop.piece("container_end_rail", 4, Alloy.STEEL, Form.PLATE, w * kg).size(w, 0.16, 0.05).weld(0.5).build());
        pieces.add(Shop.piece("container_post", 4, Alloy.STEEL, Form.PLATE, h * kg).size(h, 0.16, 0.12).weld(0.5).build());
        int cross = (int) Math.floor(Math.max(in[0], 0) / dev.mcmod.hdfv.part.StorageBoxDesign.CROSS_PITCH_M) + 1;
        pieces.add(Shop.piece("container_cross_member", cross, Alloy.STEEL, Form.SHEET, Math.max(in[2], 0) * dev.mcmod.hdfv.part.StorageBoxDesign.CROSS_KG_PER_M)
                .profile().build());
        double panels = 2 * l * h + 2 * w * h + l * w, sheetKg = panels * d.wallM() * a.density * dev.mcmod.hdfv.part.StorageBoxDesign.CORRUGATION;
        double seams = 2 * 4 * (l + w + h) + (2 * l / PANEL_W) * h + (l / PANEL_W) * w;
        boolean composite = a.family == Alloy.Family.COMPOSITE;
        works.add(work("container_walls", a, composite ? Form.LAMINATE : Form.SHEET, sheetKg, composite ? Joint.BOND : Joint.MIG, seams, panels * dev.mcmod.hdfv.part.StorageBoxDesign.CORRUGATION,
                Math.max(d.wallM(), 0.001)));
        double floorArea = Math.max(in[0], 0) * Math.max(in[2], 0);
        works.add(work("container_floor", Alloy.PLYWOOD, Form.TIMBER, floorArea * dev.mcmod.hdfv.part.StorageBoxDesign.FLOOR_M * dev.mcmod.hdfv.part.StorageBoxDesign.FLOOR_KG_PER_M3,
                Joint.GLUE, cross * Math.max(in[2], 0), floorArea, dev.mcmod.hdfv.part.StorageBoxDesign.FLOOR_M));
    }

    /**
     * 0.15: a chassis: its rails (a ladder's two, a backbone's tube) and each station's crossmember and braces made at
     * the shop, from the stock their section is (a pressed channel or I beam from sheet, a fabricated box from plate,
     * tubes from tube; ChassisLayout's members), a tube frame's tubes cut and mitred; the room joins them (MIG or TIG
     * welds round each joint, or rivets and bolts through it: the smaller section's perimeter a joint) with the gussets
     * and brackets (ChassisDesign.FITTINGS of the members' mass, from plate).
     */
    static void chassis(dev.mcmod.hdfv.part.chassis.ChassisDesign d, List<Workpiece> pieces, List<StructureWork> works) {
        var f = d.frame();
        var layout = d.analysis().layout();
        Alloy a = Alloy.of(f.material());
        var frame = layout.frame;
        double rails = 0, total = 0, seam = 0;
        double[] cross = new double[d.stations().size()], brace = new double[d.stations().size()];
        int[] braces = new int[d.stations().size()];
        for (int m = 0; m < frame.memberCount(); m++) {
            var member = frame.member(m);
            double kg = member.section().areaM2() * frame.lengthM(m) * member.material().densityKgM3();
            total += kg;
            var role = layout.members.get(m);
            switch (role.role()) {
                case RAIL -> rails += kg;
                case CROSS -> cross[role.station()] += kg;
                case BRACE -> { brace[role.station()] += kg; braces[role.station()]++; }
            }
            seam += perimeter(member.section());
        }
        boolean tubes = f.kind() == dev.mcmod.hdfv.part.chassis.ChassisSettingsDesign.Kind.SPACE_FRAME;
        if (tubes) {
            int n = frame.memberCount();
            pieces.add(Shop.piece("chassis_tube", n, a, Form.TUBE, total / n).yield(0.85).build());
        } else {
            int count = f.kind() == dev.mcmod.hdfv.part.chassis.ChassisSettingsDesign.Kind.LADDER ? 2 : 1;
            boolean holes = f.joint() != dev.mcmod.hdfv.part.chassis.ChassisSettingsDesign.Joint.WELDED;
            Shop.Piece rail = Shop.piece("chassis_rail", count, a, form(f.railSection()), rails / count).yield(0.9).profile();
            if (holes) { rail.drill(8 * d.stations().size(), 0.012, f.railWallM(), 11); }
            pieces.add(rail.build());
            for (int i = 0; i < d.stations().size(); i++) {
                var st = d.stations().get(i);
                String key = "station" + (i + 1);
                if (cross[i] > 0) { pieces.add(Shop.piece(key + "_cross", 1, a, form(st.crossSection()), cross[i]).yield(0.9).profile().build()); }
                if (braces[i] > 0) { pieces.add(Shop.piece(key + "_brace", braces[i], a, form(st.crossSection()), brace[i] / braces[i]).yield(0.9).profile().build()); }
            }
        }
        Joint joint = switch (f.joint()) {
            case WELDED -> a.family == Alloy.Family.STEEL || a.family == Alloy.Family.ALUMINIUM ? Joint.MIG : Joint.TIG;
            case RIVETED, BOLTED -> Joint.RIVET;
        };
        works.add(work("chassis_joints", a, Form.PLATE, total * dev.mcmod.hdfv.part.chassis.ChassisDesign.FITTINGS, joint, seam, 0, 0.004));
    }

    /** The stock a chassis section is made from. */
    static Form form(dev.mcmod.hdfv.part.chassis.ChassisSettingsDesign.RailSection s) {
        return switch (s) {
            case CHANNEL, I_BEAM -> Form.SHEET;
            case BOX -> Form.PLATE;
            case RECT_TUBE, ROUND_TUBE -> Form.TUBE;
        };
    }

    /** A section's perimeter from its outline (m). */
    static double perimeter(dev.mcmod.aerocore.api.frame.Section s) {
        double[][] o = s.outline();
        double p = 0;
        for (int i = 0; i < o.length; i++) {
            double[] u = o[i], v = o[(i + 1) % o.length];
            p += Math.hypot(v[0] - u[0], v[1] - u[1]);
        }
        return p > 0 ? p : 4 * Math.sqrt(s.areaM2());
    }

    /** A module's girth: the mean perimeter of its stations, m. */
    static double girth(BodyShape shape, int module) {
        double sum = 0;
        int n = 0;
        for (BodyShape.Station st : shape.stations()) { if (st.module() == module) { sum += st.perimeterM(); n++; } }
        return n > 0 ? sum / n : 1;
    }

    /** A wing: spar caps machined and ribs pressed (workpieces), webs and skin joined in the hangar. */
    static void wing(WingDesign w, List<Workpiece> pieces, List<StructureWork> works) {
        var p = w.planform().planform();
        var s = w.structure();
        Alloy box = Alloy.of(s.boxMaterial()), skin = Alloy.of(s.skinMaterial());
        Joint joint = joint(BodyStructure.Build.SEMI_MONOCOQUE, skin);
        double span = p.spanM(), chord = p.meanChordM(), area = p.areaM2();
        int ribs = Math.max(2, (int) Math.ceil(span / Math.max(s.ribSpacingM(), 0.1)) + 1);
        double seams = 4 * span + ribs * 2 * chord + 2 * area / (PANEL_W * PANEL_L) * (PANEL_W + PANEL_L);
        if (!Double.isNaN(s.massGivenKg())) {
            works.add(work("wing_structure", skin, formOf(skin), s.massGivenKg(), joint, seams, 2 * area, Math.max(s.skinThicknessM(), 0.001)));
            return;
        }
        var r = w.structureResult();
        pieces.add(cap(box, r.capsKg() / 4, span));
        pieces.add(Shop.piece("rib", ribs, box, formOf(box) == Form.SHEET ? Form.SHEET : formOf(box), r.ribsKg() / ribs).thickness(0.003).profile().drill(3, 0.03, 0.002, 12).build());
        works.add(work("spar_webs", box, formOf(box), r.websKg(), joint, 2 * span, 2 * span * 0.12 * chord, Math.max(s.webThicknessM(), 0.001)));
        works.add(work("wing_skin", skin, formOf(skin), r.skinKg(), joint, seams, 2 * area, Math.max(s.skinThicknessM(), 0.001)));
    }

    private static Workpiece cap(Alloy a, double kg, double span) {
        return switch (a.family) {
            case COMPOSITE -> Shop.piece("spar_cap", 4, a, Form.LAMINATE, kg).thickness(0.006).build();
            case WOOD -> Shop.piece("spar_cap", 4, a, Form.TIMBER, kg).cut(Process.PLANE, 1, span * 0.1, 0.05, 11).build();
            default -> Shop.piece("spar_cap", 4, a, Form.BAR, kg).yield(0.6).cut(Process.MILL, 1, span * 0.1, 0.05, 9).build();
        };
    }

    private static Form formOf(Alloy a) {
        return switch (a.family) { case COMPOSITE -> Form.LAMINATE; case WOOD -> Form.TIMBER; default -> Form.SHEET; };
    }

    /** A hull of a mass, length, beam and depth: frames every half metre (a sixth of its mass, as bars) and plating joined along keel, chines, sheer and frames. */
    static void hull(String key, Alloy a, double kg, double lengthM, double beamM, double depthM, List<Workpiece> pieces, List<StructureWork> works) {
        int frames = Math.max(2, (int) Math.round(lengthM / 0.5));
        double girth = beamM + 2 * depthM, plating = 1.3 * lengthM * girth;
        boolean composite = a.family == Alloy.Family.COMPOSITE, wood = a.family == Alloy.Family.WOOD;
        double framesKg = kg / 6;
        pieces.add((composite ? Shop.piece(key + "_frame", frames, a, Form.LAMINATE, framesKg / frames).thickness(0.008)
                : wood ? Shop.piece(key + "_frame", frames, a, Form.TIMBER, framesKg / frames).cut(Process.PLANE, 1, girth * 0.05, girth / 4, 11)
                : Shop.piece(key + "_frame", frames, a, Form.BAR, framesKg / frames).yield(0.9)).build());
        double seams = 4 * lengthM + 2 * beamM + frames * girth + plating / (PANEL_W * PANEL_L) * (PANEL_W + PANEL_L);
        works.add(work(key + "_plating", a, composite ? Form.LAMINATE : wood ? Form.TIMBER : Form.PLATE, kg - framesKg,
                joint(BodyStructure.Build.MONOCOQUE, a) == Joint.SPOT_WELD ? Joint.MIG : joint(BodyStructure.Build.MONOCOQUE, a), seams, plating, 0.006));
    }

    static void planingHull(PlaningHullDesign d, List<Workpiece> pieces, List<StructureWork> works) {
        Alloy a = d.mass() instanceof MassSpec.Derived m ? Alloy.of(m.material()) : Alloy.GFRP;
        hull("hull", a, PartPlanner.massKg(d), d.lengthM(), d.chineBeamM(), d.depthM(), pieces, works);
    }

    static void displacementHull(HullDesign d, List<Workpiece> pieces, List<StructureWork> works) {
        Alloy a = d.mass() instanceof MassSpec.Derived m ? Alloy.of(m.material()) : Alloy.STEEL;
        double x0 = Double.POSITIVE_INFINITY, x1 = Double.NEGATIVE_INFINITY, beam = 0.5, depth = 0.5;
        for (HullDesign.Station st : d.stations()) {
            x0 = Math.min(x0, st.xM()); x1 = Math.max(x1, st.xM());
            for (var pt : st.points()) { beam = Math.max(beam, 2 * Math.abs(pt.z())); depth = Math.max(depth, Math.abs(pt.y())); }
        }
        hull("hull", a, PartPlanner.massKg(d), Math.max(x1 - x0, 1), beam, depth, pieces, works);
    }
}
