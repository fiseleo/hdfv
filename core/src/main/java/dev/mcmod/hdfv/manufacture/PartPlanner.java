package dev.mcmod.hdfv.manufacture;

import dev.mcmod.aerocore.api.vehicle.MassSpec;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.hdfv.part.BallastDesign;
import dev.mcmod.hdfv.part.BoxDesign;
import dev.mcmod.hdfv.part.ControlSurfaceDesign;
import dev.mcmod.hdfv.part.FloatDesign;
import dev.mcmod.hdfv.part.MotorDesign;
import dev.mcmod.hdfv.part.PropellerDesign;
import dev.mcmod.hdfv.part.SeatDesign;
import dev.mcmod.hdfv.part.air.AirPropellerDesign;
import dev.mcmod.hdfv.part.air.RotorDesign;
import dev.mcmod.hdfv.part.drive.GearboxDesign;
import dev.mcmod.hdfv.part.drive.Gearboxes;
import dev.mcmod.hdfv.part.drive.TransferCaseDesign;
import dev.mcmod.hdfv.part.wheel.AxleDesign;
import dev.mcmod.hdfv.part.wheel.BrakeDesign;
import dev.mcmod.hdfv.part.wheel.LandingGearDesign;
import dev.mcmod.hdfv.part.wheel.RimDesign;
import dev.mcmod.hdfv.part.wheel.StrutDesign;
import dev.mcmod.hdfv.part.wheel.TyreDesign;

import java.util.ArrayList;
import java.util.List;

/**
 * How the bench-built parts other than engines are made (0.7): wheels and legs, gearboxes and transfer cases,
 * propellers and rotors, motors, floats, boxes, control surfaces, seats, ballast. As with engines every mass the
 * design counts becomes a workpiece of that mass; where a design gives one lump (a gearbox's published weight) it is
 * shared between its components by the stated shares.
 *
 * <ul>
 * <li>Tyres vulcanized from rubber (its thickness 12 % of the section height); pressed steel wheels formed and
 *     welded, cast or forged ones turned and drilled; brake discs and drums cast iron, turned and ground, calipers cast,
 *     their piston bores bored.</li>
 * <li>A gearbox's published weight shared out: case 40 %; a manual's gears 45 %, clutch 10 %, synchronisers 5 %; a
 *     dual-clutch box's gears 35 %, clutches 20 %, synchronisers 5 %; an automatic's planetary sets 30 %, clutch packs
 *     10 %, converter 15 %, valve body 5 %; a CVT's pulleys 30 %, belt 10 %, converter 15 %, valve body 5 % (rules).</li>
 * <li>Gears forged and cut: a manual's spur and helical gears to IT8 (a milling machine with a dividing head), an
 *     automatic's planetary sets and aircraft gearing to IT7 or finer (gear cutting machines: CNC), shafts ground.</li>
 * <li>Metal propeller and rotor blades are aerofoils (CNC); wooden ones are carved (planed by hand); composite ones
 *     laid up and cured.</li>
 * </ul>
 */
final class PartPlanner {
    private PartPlanner() {}

    static List<Workpiece> axle(AxleDesign d) {
        List<Workpiece> out = new ArrayList<>();
        corner(out, d.tyre(), d.wheel(), d.brake(), 2);
        out.add(Shop.piece("suspension", 2, Alloy.STEEL, Form.FORGING, d.suspension().massKg()).cut(Process.MILL, 0.5, 0.02, 0.04, 9)
                .cut(Process.TURN, 0.3, 0.01, 0.02, 8).drill(6, 0.012, 0.03, 11).weld(0.5).build());
        double layout = d.layout().massKg();
        if (layout > 0) {
            out.add(Shop.piece("steering", 1, Alloy.STEEL, Form.BAR, layout).cut(Process.TURN, 0.6, 0.02, 0.025, 8)
                    .cut(Process.MILL, 0.4, 0.01, 0.025, 9).as(Feature.SPLINE).build());
        }
        double diff = d.differential().massKg(d.layout().trackM(), d.solid());
        if (diff > 0) {
            if (d.differential().driven()) {
                out.add(Shop.piece("final_drive", 1, Alloy.ALLOY_STEEL, Form.FORGING, 0.5 * diff).cut(Process.TURN, 0.4, 0.04, 0.15, 8)
                        .cut(Process.MILL, 0.5, 0.06, 0.15, 8).as(Feature.GEAR_TEETH).chamfer(0.06, 0.15)
                        .cut(Process.GRIND, 0.1, 0.02, 0.04, 7).as(Feature.GEAR_TEETH).lap(0.06, 0.15, 7).build());
                out.add(Shop.piece("axle_housing", 1, Alloy.DUCTILE_IRON, Form.CASTING, 0.5 * diff).modulus(0.006).cut(Process.MILL, 0.5, 0.05, 0.2, 9)
                        .cut(Process.BORE, 0.5, 0.02, 0.07, 7).drill(14, 0.01, 0.025, 11).tap(14, 0.01, 0.02, 8).build());
            } else {
                out.add(Shop.piece("axle_beam", 1, Alloy.STEEL, Form.BAR, diff).cut(Process.TURN, 1, 0.02, 0.06, 9).weld(1).build());
            }
        }
        return out;
    }

    static List<Workpiece> landingGear(LandingGearDesign d) {
        List<Workpiece> out = new ArrayList<>();
        corner(out, d.tyre(), d.wheel(), d.brake(), 1);
        StrutDesign s = d.strut();
        double kg = s.massKg();
        switch (s.kind()) {
            case OLEO -> {
                double pd = s.pistonDiameterM();
                out.add(Shop.piece("oleo_cylinder", 1, Alloy.ALUMINIUM_7075, Form.FORGING, 0.5 * kg).cut(Process.TURN, 0.5, Math.PI * (pd + 0.02) * s.strokeM() * 1.5, pd + 0.02, 8)
                        .cut(Process.BORE, 0.4, Math.PI * pd * s.strokeM() * 1.5, pd, 7).cut(Process.GRIND, 0.1, Math.PI * pd * s.strokeM() * 1.5, pd, 6).as(Feature.BORE).build());
                out.add(Shop.piece("oleo_piston", 1, Alloy.ALLOY_STEEL, Form.BAR, 0.35 * kg).cut(Process.TURN, 0.8, Math.PI * pd * s.strokeM() * 1.5, pd, 7)
                        .cut(Process.GRIND, 0.2, Math.PI * pd * s.strokeM() * 1.5, pd, 6).drill(1, Math.max(s.orificeDiameterM(), 0.001), 0.01, 8).build());
                out.add(Shop.piece("gear_fittings", 1, Alloy.ALLOY_STEEL, Form.FORGING, 0.15 * kg).cut(Process.MILL, 1, 0.01, 0.03, 9).drill(6, 0.01, 0.03, 9).build());
            }
            case SPRING_STEEL -> out.add(Shop.piece("spring_leg", 1, Alloy.ALLOY_STEEL, Form.PLATE, kg).yield(0.85)
                    .cut(Process.MILL, 1, 0.02, 0.05, 10).drill(4, 0.012, 0.02, 9).build());
            case RIGID -> out.add(Shop.piece("rigid_fork", 1, Alloy.STEEL, Form.FORGING, kg).cut(Process.MILL, 1, 0.02, 0.04, 9).drill(4, 0.012, 0.02, 9).build());
        }
        return out;
    }

    /** A wheel's tyre, wheel and brake, a number of times. */
    static void corner(List<Workpiece> out, TyreDesign t, RimDesign w, BrakeDesign b, int count) {
        double rubber = 0.12 * t.sectionWidthM() * t.aspectRatio();
        out.add(Shop.piece("tyre", count, Alloy.RUBBER, Form.RUBBER, t.massKg()).thickness(Math.max(rubber, 0.005)).build());
        double rimD = t.rimDiameterM(), rimKg = w.massKg(rimD), rimR = rimD / 2;
        Shop.Piece rim = switch (w.material()) {
            case STEEL -> Shop.piece("wheel", count, Alloy.STEEL, Form.SHEET, rimKg).profile().weld(2 * Math.PI * rimR);
            case CAST_ALLOY -> Shop.piece("wheel", count, Alloy.ALUMINIUM_CAST, Form.CASTING, rimKg).modulus(0.004);
            case FORGED_ALLOY -> Shop.piece("wheel", count, Alloy.ALUMINIUM_7075, Form.FORGING, rimKg).cut(Process.MILL, 0.4, 0.05, rimD, 9);
            case MAGNESIUM -> Shop.piece("wheel", count, Alloy.MAGNESIUM, Form.CASTING, rimKg).modulus(0.004);
        };
        out.add(rim.cut(Process.TURN, 1, 2 * Math.PI * rimR * w.widthM(), rimD, 8).drill(5, 0.014, 0.012, 10).build());
        if (b.kind() == BrakeDesign.Kind.NONE || !(b.massKg() > 0)) { return; }
        double rotor = Double.isNaN(b.massGivenKg()) ? b.rotorMassKg() : 0.7 * b.massKg(), rest = b.massKg() - rotor, d = b.rotorDiameterM();
        out.add(Shop.piece(b.kind().drum() ? "brake_drum" : "brake_disc", count, Alloy.GREY_IRON, Form.CASTING, rotor).modulus(Math.max(b.rotorThicknessM() / 4, 0.002))
                .cut(Process.TURN, 0.8, b.kind().drum() ? Math.PI * d * b.rotorThicknessM() : 2 * Math.PI / 4 * d * d * 0.7, d, 8)
                .cut(Process.GRIND, 0.2, b.kind().drum() ? Math.PI * d * b.rotorThicknessM() : 2 * Math.PI / 4 * d * d * 0.7, d, 8).drill(5, 0.013, 0.008, 11).build());
        if (rest > 0) {
            double pd = b.pistonDiameterM();
            out.add(Shop.piece(b.kind().drum() ? "brake_shoes" : "caliper", count, Alloy.DUCTILE_IRON, Form.CASTING, rest).modulus(0.005)
                    .cut(Process.MILL, 0.6, 0.01, 0.1, 9).cut(Process.BORE, 0.4, Math.max(b.pistons(), 1) * Math.PI * pd * 0.04, pd, 7)
                    .drill(4, 0.01, 0.03, 11).rivet(b.kind().drum() ? 8 : 0).build());
        }
    }

    static List<Workpiece> gearbox(GearboxDesign d) {
        List<Workpiece> out = new ArrayList<>();
        double kg = d.totalMassKg();
        double s = Math.cbrt(d.gearbox().scale());
        boolean planetary = d.kind() == Gearboxes.Kind.AUTOMATIC;
        out.add(Shop.piece("gearbox_case", 1, Alloy.ALUMINIUM_CAST, Form.CASTING, 0.4 * kg).modulus(0.005).cut(Process.MILL, 0.5, 0.15 * s * s, 0.3 * s, 8)
                .cut(Process.BORE, 0.5, 0.03 * s * s, 0.06 * s, 7).drill(30, 0.008, 0.02, 11).tap(30, 0.008, 0.015, 8).build());
        switch (d.kind()) {
            case MANUAL, DUAL_CLUTCH -> {
                int gears = d.forwardGears() + 1;
                out.add(Shop.piece("gears", 2 * gears, Alloy.ALLOY_STEEL, Form.FORGING, (d.kind() == Gearboxes.Kind.MANUAL ? 0.45 : 0.35) * kg / (2 * gears))
                        .cut(Process.TURN, 0.4, 0.01 * s * s, 0.08 * s, 8).spline(1e-6 * s * s * s, 0.002 * s * s, 0.03 * s, 8, true)
                        .cut(Process.MILL, 0.5, 0.02 * s * s, 0.08 * s, 8).as(Feature.GEAR_TEETH).chamfer(0.02 * s * s, 0.08 * s)
                        .cut(Process.GRIND, 0.1, 0.004 * s * s, 0.03 * s, 7).as(Feature.GEAR_TEETH).build());
                out.add(Shop.piece("clutch", d.kind() == Gearboxes.Kind.MANUAL ? 1 : 2, Alloy.STEEL, Form.SHEET,
                        (d.kind() == Gearboxes.Kind.MANUAL ? 0.1 : 0.2) * kg / (d.kind() == Gearboxes.Kind.MANUAL ? 1 : 2)).profile().cut(Process.TURN, 1, 0.05 * s * s, 0.2 * s, 9)
                        .rivet(16).build());
                out.add(Shop.piece("synchros", d.forwardGears(), Alloy.BRONZE, Form.BAR, 0.05 * kg / d.forwardGears()).cut(Process.TURN, 0.6, 0.005, 0.07, 8)
                        .cut(Process.MILL, 0.4, 0.002, 0.07, 9).as(Feature.SPLINE).build());
            }
            case AUTOMATIC -> {
                out.add(Shop.piece("planetary_sets", Math.max(2, (d.forwardGears() + 1) / 2), Alloy.ALLOY_STEEL, Form.FORGING, 0.3 * kg / Math.max(2, (d.forwardGears() + 1) / 2))
                        .cut(Process.TURN, 0.4, 0.02 * s * s, 0.15 * s, 7).cut(Process.MILL, 0.5, 0.03 * s * s, 0.15 * s, 7).as(Feature.GEAR_TEETH)
                        .chamfer(0.03 * s * s, 0.15 * s).cut(Process.GRIND, 0.1, 0.01 * s * s, 0.05 * s, 6).as(Feature.GEAR_TEETH).build());
                out.add(Shop.piece("clutch_packs", 1, Alloy.STEEL, Form.SHEET, 0.1 * kg).build());
                out.add(Shop.piece("torque_converter", 1, Alloy.STEEL, Form.SHEET, 0.15 * kg).weld(3 * Math.PI * d.converter().diameterM()).build());
                out.add(Shop.piece("valve_body", 1, Alloy.ALUMINIUM_CAST, Form.CASTING, 0.05 * kg).cut(Process.MILL, 0.4, 0.04, 0.2, 8)
                        .cut(Process.BORE, 0.6, 0.01, 0.012, 6).deburr(0.04).build());
            }
            case CVT -> {
                out.add(Shop.piece("pulleys", 2, Alloy.ALLOY_STEEL, Form.FORGING, 0.15 * kg).cut(Process.TURN, 0.8, 0.06 * s * s, 0.2 * s, 7)
                        .cut(Process.GRIND, 0.2, 0.06 * s * s, 0.2 * s, 6).build());
                out.add(Shop.piece("push_belt", 1, Alloy.ALLOY_STEEL, Form.SHEET, 0.1 * kg).build());
                out.add(Shop.piece("torque_converter", 1, Alloy.STEEL, Form.SHEET, 0.15 * kg).weld(3 * Math.PI * d.converter().diameterM()).build());
                out.add(Shop.piece("valve_body", 1, Alloy.ALUMINIUM_CAST, Form.CASTING, 0.05 * kg).cut(Process.MILL, 0.4, 0.04, 0.2, 8)
                        .cut(Process.BORE, 0.6, 0.01, 0.012, 6).deburr(0.04).build());
            }
        }
        return out;
    }

    static List<Workpiece> transferCase(TransferCaseDesign d) {
        double kg = d.totalMassKg(), s = Math.cbrt(d.scale());
        return List.of(
                Shop.piece("transfer_case", 1, Alloy.ALUMINIUM_CAST, Form.CASTING, 0.5 * kg).modulus(0.005).cut(Process.MILL, 0.5, 0.1 * s * s, 0.3 * s, 8)
                        .cut(Process.BORE, 0.5, 0.02 * s * s, 0.06 * s, 7).drill(20, 0.008, 0.02, 11).tap(20, 0.008, 0.015, 8).build(),
                Shop.piece("transfer_gears", 4, Alloy.ALLOY_STEEL, Form.FORGING, 0.4 * kg / 4).cut(Process.TURN, 0.4, 0.01 * s * s, 0.1 * s, 8).spline(1e-6 * s * s * s, 0.002 * s * s, 0.03 * s, 8, true)
                        .cut(Process.MILL, 0.5, 0.02 * s * s, 0.1 * s, 8).as(Feature.GEAR_TEETH).chamfer(0.02 * s * s, 0.1 * s)
                        .cut(Process.GRIND, 0.1, 0.004 * s * s, 0.04 * s, 7).as(Feature.GEAR_TEETH).build(),
                Shop.piece("transfer_chain", 1, Alloy.ALLOY_STEEL, Form.SHEET, 0.1 * kg).build());
    }

    static List<Workpiece> airPropeller(AirPropellerDesign d) {
        List<Workpiece> out = new ArrayList<>();
        var b = d.blades();
        double bladesKg = b.massKg(), r = b.radiusM();
        Alloy a = Alloy.of(b.material());
        double area = r * (1 - b.hubRatio()) * b.diameterM() * 0.5 * (b.rootChordRatio() + b.tipChordRatio());
        out.add(blade("blade", b.blades(), a, bladesKg / b.blades(), area, r));
        out.add(Shop.piece("propeller_hub", 1, Alloy.ALLOY_STEEL, Form.FORGING, d.hub().massKg(bladesKg)).cut(Process.TURN, 0.5, 0.03, 0.1, 7)
                .cut(Process.MILL, 0.3, 0.02, 0.1, 8).cut(Process.BORE, 0.2, 0.01, 0.05, 7).drill(6, 0.012, 0.03, 9).build());
        return out;
    }

    static List<Workpiece> rotor(RotorDesign d) {
        List<Workpiece> out = new ArrayList<>();
        var b = d.blades();
        double bladesKg = b.massKg(), r = b.radiusM();
        Alloy a = Alloy.of(b.material());
        double area = r * (1 - b.rootCutout()) * 0.5 * (b.rootChordM() + b.tipChordM());
        out.add(blade("rotor_blade", b.blades(), a, bladesKg / b.blades(), area, r));
        out.add(Shop.piece("rotor_hub", 1, Alloy.TITANIUM, Form.FORGING, d.hub().massKg(bladesKg)).cut(Process.TURN, 0.4, 0.05, 0.2, 7)
                .cut(Process.MILL, 0.4, 0.05, 0.2, 8).cut(Process.BORE, 0.2, 0.02, 0.06, 6).drill(8, 0.014, 0.04, 8).build());
        return out;
    }

    /** A blade of a planform area (m^2, one face) and radius: an aerofoil machined on a CNC, carved from wood, or laid up. */
    static Workpiece blade(String key, int count, Alloy a, double kg, double areaM2, double radiusM) {
        return switch (a.family) {
            case WOOD -> Shop.piece(key, count, a, Form.TIMBER, kg).yield(0.5).cut(Process.PLANE, 1, 2 * areaM2, radiusM, 11).build();
            case COMPOSITE -> Shop.piece(key, count, a, Form.LAMINATE, kg).thickness(0.004).build();
            default -> Shop.piece(key, count, a, Form.FORGING, kg).cut(Process.MILL, 0.85, 2 * areaM2, 0.1 * radiusM, 8, true).as(Feature.AEROFOIL)
                    .cut(Process.GRIND, 0.15, 2 * areaM2, 0.1 * radiusM, 7, true).as(Feature.AEROFOIL).drill(2, 0.01, 0.04, 8)
                    .polish(2 * areaM2, 0.1 * radiusM).build();
        };
    }

    /** The material a MassSpec names, or a default for a given mass. */
    static Alloy alloy(MassSpec m, Alloy fallback) { return m instanceof MassSpec.Derived d ? Alloy.of(d.material()) : fallback; }

    static double massKg(PartDesign d) { return d.compile().mass().massKg(); }

    static List<Workpiece> simple(PartDesign d) {
        return switch (d) {
            case FloatDesign f -> {
                Alloy a = alloy(f.mass(), Alloy.STEEL);
                yield List.of(shell("float_shell", a, massKg(d), 4 * Math.PI * f.radiusM()));
            }
            case BoxDesign b -> {
                Alloy a = alloy(b.mass(), Alloy.STEEL);
                yield List.of(shell("box_shell", a, massKg(d), 4 * (b.sizeM().x() + b.sizeM().y() + b.sizeM().z())));
            }
            case ControlSurfaceDesign c -> {
                Alloy a = alloy(c.mass(), Alloy.ALUMINIUM_2024);
                Shop.Piece p = a.family == Alloy.Family.COMPOSITE ? Shop.piece("control_surface", 1, a, Form.LAMINATE, massKg(d)).thickness(0.003)
                        : Shop.piece("control_surface", 1, a, a.family == Alloy.Family.WOOD ? Form.TIMBER : Form.SHEET, massKg(d)).drill(3, 0.008, 0.02, 9);
                if (a.family.metal()) { p.weld(2 * (c.chordM() + c.spanM())); }
                yield List.of(p.build());
            }
            case PropellerDesign p -> List.of(Shop.piece("screw", 1, alloy(p.mass(), Alloy.BRONZE), Form.CASTING, massKg(d)).modulus(0.006)
                    .cut(Process.MILL, 0.8, p.areaRatio() * Math.PI / 4 * p.diameterM() * p.diameterM() * 2, p.diameterM(), 9).as(Feature.AEROFOIL)
                    .cut(Process.BORE, 0.2, Math.PI * p.hubRatio() * p.diameterM() * 0.1, p.hubRatio() * p.diameterM() * 0.5, 7)
                    .polish(p.areaRatio() * Math.PI / 4 * p.diameterM() * p.diameterM() * 2, p.diameterM()).build());
            case MotorDesign m -> List.of(
                    Shop.piece("stator", 1, Alloy.STEEL, Form.SHEET, 0.35 * m.massKg()).build(),
                    Shop.piece("windings", 1, Alloy.COPPER, Form.WIRE, 0.3 * m.massKg()).build(),
                    Shop.piece("motor_rotor", 1, Alloy.STEEL, Form.BAR, 0.2 * m.massKg()).cut(Process.TURN, 0.8, 0.02, 0.1, 7).cut(Process.GRIND, 0.2, 0.004, 0.03, 6).build(),
                    Shop.piece("motor_case", 1, Alloy.ALUMINIUM_CAST, Form.CASTING, 0.15 * m.massKg()).cut(Process.MILL, 0.5, 0.03, 0.2, 8)
                            .cut(Process.BORE, 0.5, 0.005, 0.06, 7).build());
            case dev.mcmod.hdfv.part.FuelTankDesign t -> List.of(shell("fuel_tank_shell", Alloy.of(t.material()), massKg(d),
                    4 * (t.lengthM() + t.widthM() + t.heightM())));
            case BallastDesign b -> List.of(Shop.piece("ballast", 1, Alloy.GREY_IRON, Form.CASTING, b.massKg()).modulus(0.03).build());
            case SeatDesign s -> List.of(Shop.piece("seat_frame", 1, Alloy.STEEL, Form.BAR, s.massKg()).yield(0.9).weld(3).build());
            // 0.18 (the truck parts): a fifth wheel's ductile iron top plate (its face milled, the jaws' throat bored), its welded
            // steel mounting, its turned lock; a landing leg's welded tube, its turned and threaded screw with the bevel gears, its cast foot
            case dev.mcmod.hdfv.part.truck.FifthWheelDesign f -> {
                double kg = massKg(d), dia = f.plateDiameterM(), pin = f.kingpin().diameterM;
                yield List.of(
                        Shop.piece("fifth_wheel_plate", 1, Alloy.DUCTILE_IRON, Form.CASTING, 0.6 * kg).modulus(0.015).size(dia, 0.8 * dia, 0.15)
                                .cut(Process.MILL, 0.7, Math.PI / 4 * dia * dia, dia, 9).cut(Process.BORE, 0.3, Math.PI * pin * 0.08, pin, 8)
                                .drill(8, 0.017, 0.03, 11).deburr(Math.PI / 4 * dia * dia).build(),
                        Shop.piece("fifth_wheel_mount", 1, Alloy.STEEL, Form.PLATE, 0.3 * kg).size(dia, 0.8 * dia, f.heightM()).weld(4 * dia)
                                .drill(8, 0.017, 0.012, 11).build(),
                        Shop.piece("fifth_wheel_lock", 1, Alloy.ALLOY_STEEL, Form.BAR, 0.1 * kg).turned(0.35, 0.08)
                                .cut(Process.TURN, 0.7, Math.PI * 0.08 * 0.35, 0.08, 8).cut(Process.MILL, 0.3, 0.01, 0.08, 8).build());
            }
            case dev.mcmod.hdfv.part.truck.LandingLegsDesign l -> {
                double kg = massKg(d), len = l.lengthM(), foot = l.footDiameterM();
                yield List.of(
                        Shop.piece("landing_leg_tube", 1, Alloy.STEEL, Form.TUBE, 0.7 * kg).size(len, 0.15, 0.15).weld(1.2 * len).drill(6, 0.013, 0.006, 11).build(),
                        Shop.piece("landing_leg_screw", 1, Alloy.ALLOY_STEEL, Form.BAR, 0.2 * kg).turned(len, 0.04)
                                .cut(Process.TURN, 0.6, Math.PI * 0.04 * len, 0.04, 8).thread(0.04, 0.7 * len, 8)
                                .cut(Process.MILL, 0.4, 0.01, 0.08, 8).as(Feature.GEAR_TEETH).build(),
                        Shop.piece("landing_leg_foot", 1, Alloy.DUCTILE_IRON, Form.CASTING, 0.1 * kg).modulus(0.01).size(foot, foot, 0.08)
                                .cut(Process.BORE, 1, Math.PI * 0.03 * 0.05, 0.03, 9).build());
            }
            default -> List.of();
        };
    }

    /** A welded sheet shell (or a laid-up one, or wood) of a mass with seams of a length. */
    static Workpiece shell(String key, Alloy a, double kg, double seamM) {
        return switch (a.family) {
            case COMPOSITE -> Shop.piece(key, 1, a, Form.LAMINATE, kg).thickness(0.004).build();
            case WOOD -> Shop.piece(key, 1, a, Form.TIMBER, kg).cut(Process.PLANE, 1, 0.2, 0.2, 11).build();
            default -> Shop.piece(key, 1, a, Form.SHEET, kg).weld(seamM).build();
        };
    }
}
