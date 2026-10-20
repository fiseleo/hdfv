package dev.mcmod.hdfv.manufacture;

import dev.mcmod.aerocore.api.manufacture.Tolerance;
import dev.mcmod.hdfv.part.engine.CylinderHeadDesign;
import dev.mcmod.hdfv.part.engine.EngineMaterial;
import dev.mcmod.hdfv.part.engine.FuelSystemDesign;
import dev.mcmod.hdfv.part.engine.JetEngineDesign;
import dev.mcmod.hdfv.part.engine.PistonEngineDesign;
import dev.mcmod.hdfv.part.engine.ShaftTurbineDesign;
import dev.mcmod.hdfv.part.engine.TurbofanDesign;

import java.util.ArrayList;
import java.util.List;

import static dev.mcmod.hdfv.manufacture.Operation.BOTTOM;
import static dev.mcmod.hdfv.manufacture.Operation.BOX;
import static dev.mcmod.hdfv.manufacture.Operation.FRONT;
import static dev.mcmod.hdfv.manufacture.Operation.LEFT;
import static dev.mcmod.hdfv.manufacture.Operation.RIGHT;
import static dev.mcmod.hdfv.manufacture.Operation.TOP;

/**
 * How engines are made (0.7): every component the engine's mass is counted from becomes a workpiece of the same mass
 * (so a plan's total is the engine's totalMassKg()), with the route a real one takes.
 *
 * <ul>
 * <li>Piston engine: the block and head cast and then planed, bored, honed and drilled (0.8: the block from all six
 *     faces, deck and pan planed, bores from the deck, mains line-bored from the front; the head from five); pistons cast (or forged from
 *     2618) and turned, their pin bores bored and honed; rods and the crankshaft forged, their bearings bored and honed
 *     or ground; valves
 *     forged and ground; camshafts cast (iron) or forged, their lobes profile-milled and journals ground; injectors
 *     turned and their orifices drilled on a CNC (complex: tenths of a millimetre); and the systems. Running fits get
 *     their grade from the design's own clearance: each of the two parts may take half of it (ISO 286); without a
 *     clearance in the design, typical ones (valve stems 40 micrometres, cam journals 50). A diesel's glow plugs are
 *     turned as plugs are; a turbocharger (0.8) has an investment-cast nickel turbine wheel ground on a CNC, a forged
 *     aluminium compressor wheel milled on a CNC (an impeller), a ground shaft, cast and machined housings and a welded
 *     sheet intercooler.</li>
 * <li>Gas turbines: discs and blades forged, the blades' aerofoils and impellers machined on a CNC; cast turbine
 *     blades (713C, CMSX-4) investment cast and ground on a CNC; casings and combustors from sheet, welded, the
 *     combustor's cooling holes drilled on a CNC; accessories cast and machined.</li>
 * <li>0.10: an afterburner's duct and liner rolled and welded, the liner's film-cooling holes drilled on a CNC, the
 *     variable nozzle's petals milled from plate; a ramjet's spike turned (or its ramps milled), its cowl, combustor and
 *     nozzle rolled and welded, the V-gutter flameholders bent and welded; a scramjet's ramps milled from plate and its
 *     fuel-cooled walls' channels milled on a CNC (complex), the panels welded into the duct; a pulsejet's tube rolled
 *     and welded, the valve grid's plate milled and drilled, the spring-steel reeds cut from thin sheet and ground; a
 *     solid rocket's casing turned from tube and its bulkheads welded, the nozzle turned (a graphite throat is laid up
 *     here: the game's graphite is a composite), the igniter and mounts turned and drilled. The grain is not made here:
 *     it is cast as an item and loaded.</li>
 * </ul>
 */
final class EnginePlanner {
    private EnginePlanner() {}

    /** The grade a running fit of a clearance (m) on a size (m) needs: half the clearance for each part. */
    static int fit(double sizeM, double clearanceM) {
        if (!(clearanceM > 0)) { return 6; }
        return Math.clamp(Tolerance.grade(sizeM, clearanceM / 2), 4, 12);
    }

    static Alloy alloy(EngineMaterial m) { return Alloy.of(m); }

    static List<Workpiece> piston(PistonEngineDesign e) {
        var block = e.block();
        var head = e.head();
        int n = block.cylinders(), banks = block.banks();
        double bore = e.bore(), len = block.length(), deck = block.deckHeightM();
        List<Workpiece> out = new ArrayList<>();
        // block: cast, deck and pan faces planed, bores bored and honed, mains line-bored, bolt and oil holes drilled
        Alloy blockAlloy = alloy(block.material());
        int boreGrade = fit(bore, bore - e.piston().diameterM());
        out.add(Shop.piece("block", 1, blockAlloy, Form.CASTING, block.massKg()).modulus(block.wallThicknessM() / 2)
                .size(len, banks > 1 ? 2.2 * deck : 1.6 * bore + 0.1, 1.5 * deck)
                .cut(Process.PLANE, 0.4, 2 * banks * len * 1.5 * bore, len, 9).on(TOP | BOTTOM)
                .cut(Process.BORE, 0.45, Math.PI * bore * deck * n, bore, Math.min(boreGrade + 1, 8)).on(TOP)
                .cut(Process.GRIND, 0.05, Math.PI * bore * deck * n, bore, boreGrade).on(TOP).as(Feature.BORE)
                .cut(Process.BORE, 0.1, Math.PI * e.crank().mainDiameterM() * e.crank().mainWidthM() * block.mainBearings(), e.crank().mainDiameterM(), 7).on(FRONT)
                .drill(5 * n + 2 * block.mainBearings(), 0.011, 0.7 * deck, 12).on(BOX).tap(4 * n + 2, 0.010, 0.025, 8)
                .deburr(2 * len * 1.5 * deck).build());
        // head: cast, deck face milled, valve guides and seats bored, plug and bolt holes drilled
        int valvesPerCylinder = head.intakeValves() + head.exhaustValves();
        out.add(Shop.piece("head", 1, alloy(head.material()), Form.CASTING, head.massFor(block)).modulus(0.004).size(len, 2.4 * bore * banks, 0.15 + bore)
                .cut(Process.MILL, 0.6, 2 * banks * len * 1.5 * bore, len, 8).on(TOP | BOTTOM)
                .cut(Process.BORE, 0.4, n * valvesPerCylinder * Math.PI * e.valves().intakeHeadM() * 0.01, e.valves().intakeHeadM(), 7).on(BOTTOM)
                .drill(n * (e.plugsPerCylinder() + 4), 0.012, 0.05, 12).on(TOP | BOTTOM | LEFT | RIGHT | FRONT)
                .tap(n * (e.plugsPerCylinder() + 4), 0.012, 0.02, 8).deburr(2 * len * 2.4 * bore * banks).build());
        // pistons: cast (or forged from 2618), skirt turned to the bore's fit, pin bore bored, ring grooves turned
        var p = e.piston();
        Alloy pistonAlloy = alloy(p.material());
        Form pistonForm = pistonAlloy.forgeable && !pistonAlloy.castable ? Form.FORGING : Form.CASTING;
        int smallEnd = fit(p.pinDiameterM(), e.rod().smallEndBoreM() - p.pinDiameterM());
        out.add(Shop.piece("piston", n, pistonAlloy, pistonForm, p.massKg()).modulus(0.003).turned(p.compressionHeightM() + p.skirtLengthM(), p.diameterM())
                .cut(Process.TURN, 0.6, Math.PI * p.diameterM() * p.skirtLengthM(), p.diameterM(), boreGrade)
                .cut(Process.TURN, 0.2, Math.PI * p.diameterM() * 0.003 * Math.max(p.rings(), 1), p.diameterM(), 9)
                .cut(Process.BORE, 0.15, Math.PI * p.pinDiameterM() * 0.8 * p.diameterM(), p.pinDiameterM(), 8).on(LEFT | RIGHT)
                .cut(Process.GRIND, 0.05, Math.PI * p.pinDiameterM() * 0.8 * p.diameterM(), p.pinDiameterM(), smallEnd).on(LEFT | RIGHT).as(Feature.BORE).build());
        // rods: forged, faces milled, big and small ends bored to their fits, bolt holes drilled
        var r = e.rod();
        int bigEnd = fit(r.bigEndBoreM(), r.bigEndBoreM() - e.crank().pinDiameterM());
        out.add(Shop.piece("rod", n, alloy(r.material()), Form.FORGING, r.massKg()).size(r.lengthM() + r.bigEndBoreM(), 1.8 * r.bigEndBoreM(), r.bigEndWidthM())
                .cut(Process.MILL, 0.5, 2 * r.bigEndWidthM() * (r.bigEndBoreM() + r.smallEndBoreM()) * 2, r.lengthM(), 9).on(TOP | BOTTOM)
                .cut(Process.BORE, 0.3, Math.PI * r.bigEndBoreM() * r.bigEndWidthM(), r.bigEndBoreM(), 8)
                .cut(Process.GRIND, 0.05, Math.PI * r.bigEndBoreM() * r.bigEndWidthM(), r.bigEndBoreM(), bigEnd).as(Feature.BORE)
                .cut(Process.BORE, 0.1, Math.PI * r.smallEndBoreM() * r.bigEndWidthM(), r.smallEndBoreM(), 8)
                .cut(Process.GRIND, 0.05, Math.PI * r.smallEndBoreM() * r.bigEndWidthM(), r.smallEndBoreM(), smallEnd).as(Feature.BORE)
                .drill(2, 0.009, r.bigEndBoreM() * 0.6, 11).on(LEFT | RIGHT).tap(2, 0.009, 0.02, 8).build());
        // crankshaft: forged, mains and pins turned and ground (the big end's fit), oil holes drilled
        var c = e.crank();
        int pins = n / (block.layout() == dev.mcmod.hdfv.part.engine.EngineBlockDesign.Layout.VEE ? 2 : 1);
        double journals = Math.PI * (c.mainDiameterM() * c.mainWidthM() * block.mainBearings() + c.pinDiameterM() * c.pinWidthM() * pins);
        out.add(Shop.piece("crank", 1, alloy(c.material()), Form.FORGING, c.massFor(block)).turned(len + 0.15, c.strokeM() + 1.6 * c.mainDiameterM())
                .cut(Process.TURN, 0.9, journals, c.mainDiameterM(), 8)
                .cut(Process.GRIND, 0.1, journals, c.pinDiameterM(), bigEnd)
                .drill(block.mainBearings() + pins, 0.006, c.mainDiameterM(), 11).lap(journals, c.pinDiameterM(), bigEnd).build());
        // valves: forged (upset), stems turned and ground to a typical 40 micrometre stem clearance, seats ground
        var v = e.valves();
        int stem = fit(v.intakeStemM(), 40e-6);
        out.add(Shop.piece("intake_valve", n * head.intakeValves(), alloy(v.intakeMaterial()), Form.FORGING, v.intakeValveMassKg()).turned(v.lengthM(), v.intakeHeadM())
                .cut(Process.TURN, 0.8, Math.PI * v.intakeStemM() * v.lengthM(), v.intakeStemM(), 8)
                .cut(Process.GRIND, 0.2, Math.PI * v.intakeStemM() * v.lengthM(), v.intakeStemM(), stem).build());
        out.add(Shop.piece("exhaust_valve", n * head.exhaustValves(), alloy(v.exhaustMaterial()), Form.FORGING, v.exhaustValveMassKg()).turned(v.lengthM(), v.exhaustHeadM())
                .cut(Process.TURN, 0.8, Math.PI * v.exhaustStemM() * v.lengthM(), v.exhaustStemM(), 8)
                .cut(Process.GRIND, 0.2, Math.PI * v.exhaustStemM() * v.lengthM(), v.exhaustStemM(), fit(v.exhaustStemM(), 40e-6)).build());
        // camshafts: cast iron or forged steel, lobes profile-milled, journals ground (50 micrometres typical clearance)
        var cam = e.camshaft();
        boolean dohc = head.layout() == CylinderHeadDesign.Layout.DOHC;
        int camshafts = head.layout() == CylinderHeadDesign.Layout.OHV ? 1 : banks * (dohc ? 2 : 1);
        double camKg = cam.massFor(block, dohc ? Math.max(head.intakeValves(), head.exhaustValves()) : valvesPerCylinder);
        Alloy camAlloy = alloy(cam.material());
        out.add(Shop.piece("camshaft", camshafts, camAlloy, camAlloy.castable && camAlloy.family == Alloy.Family.IRON ? Form.CASTING : Form.FORGING, camKg).turned(len + 0.1, 0.05)
                .cut(Process.MILL, 0.7, n * valvesPerCylinder * 0.03 * 0.015 * 2, 0.04, 9)
                .cut(Process.TURN, 0.2, Math.PI * 0.03 * 0.02 * cam.bearingsPerShaft(), 0.03, 8)
                .cut(Process.GRIND, 0.1, Math.PI * 0.03 * 0.02 * cam.bearingsPerShaft(), 0.03, fit(0.03, 50e-6))
                .lap(Math.PI * 0.03 * 0.02 * cam.bearingsPerShaft() + n * valvesPerCylinder * 0.03 * 0.015 * 2, 0.03, fit(0.03, 50e-6)).build());
        // spark plugs and injectors: turned; an injector's orifices drilled on a CNC
        out.add(Shop.piece(e.diesel() ? "glow_plug" : "spark_plug", n * e.plugsPerCylinder(), Alloy.STEEL, Form.BAR, e.sparkPlug().massKg())
                .cut(Process.TURN, 1, Math.PI * e.sparkPlug().threadDiameterM() * 0.02, e.sparkPlug().threadDiameterM(), 8)
                .thread(e.sparkPlug().threadDiameterM(), 0.019, 7).build());
        if (e.fuelSystem().delivery() != FuelSystemDesign.Delivery.CARBURETTOR && e.fuelSystem().injectorsPerCylinder() > 0) {
            out.add(Shop.piece("injector", n * e.fuelSystem().injectorsPerCylinder(), Alloy.STAINLESS_STEEL, Form.BAR, e.injector().massKg())
                    .cut(Process.TURN, 1, Math.PI * 0.01 * 0.05, 0.01, 7)
                    .drill(4, 0.0002, 0.001, 5, true).as(Feature.ORIFICE).build());
        }
        // the systems: valvetrain (springs coiled, rockers forged and ground), fuel system, ignition, lubrication, cooling, intake, exhaust
        out.add(Shop.piece("valvetrain", 1, Alloy.STEEL, Form.FORGING, e.valvetrain().massFor(n * valvesPerCylinder))
                .cut(Process.MILL, 0.8, n * valvesPerCylinder * 0.0005, 0.02, 9)
                .cut(Process.GRIND, 0.2, n * valvesPerCylinder * 0.0003, 0.02, 7).build());
        boolean carb = e.fuelSystem().delivery() == FuelSystemDesign.Delivery.CARBURETTOR;
        out.add(carb ? Shop.piece("fuel_system", 1, Alloy.ALUMINIUM_CAST, Form.CASTING, e.fuelSystem().massKg())
                        .cut(Process.MILL, 0.7, 0.01, 0.05, 9).drill(8, 0.001, 0.02, 8).build()
                : Shop.piece("fuel_system", 1, Alloy.STEEL, Form.SHEET, e.fuelSystem().massKg()).drill(4, 0.006, 0.003, 11).weld(0.6).build());
        out.add(Shop.piece("ignition", 1, Alloy.COPPER, Form.WIRE, e.ignition().massKg()).build());
        double oil = e.lubrication().massKg();
        out.add(Shop.piece("oil_pump", 1, Alloy.GREY_IRON, Form.CASTING, 0.4 * oil).cut(Process.MILL, 0.6, 0.01, 0.06, 8)
                .cut(Process.BORE, 0.4, 0.004, 0.04, 7).drill(4, 0.008, 0.02, 12).tap(4, 0.008, 0.015, 8).build());
        out.add(Shop.piece("oil_pan", 1, Alloy.STEEL, Form.SHEET, 0.6 * oil).weld(1.2 * (len + 0.3)).build());
        double cool = e.cooling().massKg();
        if (e.cooling().kind() == dev.mcmod.hdfv.part.engine.CoolingDesign.Kind.AIR) {
            out.add(Shop.piece("cooling_shroud", 1, Alloy.ALUMINIUM_2024, Form.SHEET, cool).weld(1.5).build());
        } else {
            out.add(Shop.piece("radiator", 1, Alloy.ALUMINIUM_2024, Form.SHEET, 0.7 * cool).weld(4 * Math.sqrt(Math.max(e.cooling().radiatorAreaM2(), 0.01))).build());
            out.add(Shop.piece("water_pump", 1, Alloy.ALUMINIUM_CAST, Form.CASTING, 0.3 * cool).cut(Process.MILL, 0.6, 0.006, 0.08, 8)
                    .cut(Process.BORE, 0.4, 0.002, 0.016, 7).drill(4, 0.008, 0.015, 12).tap(4, 0.008, 0.012, 8).build());
        }
        out.add(Shop.piece("intake", 1, Alloy.ALUMINIUM_CAST, Form.CASTING, e.intake().massFor(n)).modulus(0.003)
                .cut(Process.MILL, 1, 2 * n * 0.06 * 0.05, 0.06, 9).drill(2 * n + 2, 0.008, 0.015, 12).tap(2 * n + 2, 0.008, 0.012, 8).build());
        out.add(Shop.piece("exhaust", 1, Alloy.GREY_IRON, Form.CASTING, e.exhaust().massKg()).modulus(0.004)
                .cut(Process.MILL, 1, n * 0.05 * 0.05, 0.05, 9).drill(2 * n + 2, 0.009, 0.015, 12).tap(2 * n + 2, 0.009, 0.012, 8).build());
        if (e.turbo().fitted()) { turbo(out, e.turbo()); }
        return out;
    }

    /** A turbocharger's pieces, their masses the design's (the core and the intercooler in the estimate's shares). */
    static void turbo(List<Workpiece> out, dev.mcmod.hdfv.part.engine.TurbochargerDesign t) {
        double tip = t.compressorTipM(), core = 7 * Math.pow(tip / 0.05, 2), cooler = 6 * t.intercoolerEffectiveness() / 0.7;
        double scale = t.massKg() / (core + cooler);
        core *= scale; cooler *= scale;
        double wheel = Math.PI / 4 * tip * tip;
        out.add(Shop.piece("turbine_wheel", 1, Alloy.INCONEL_713C, Form.CASTING, 0.15 * core).modulus(0.002)
                .cut(Process.GRIND, 1, 2 * wheel, 0.9 * tip, 6, true).as(Feature.AEROFOIL).build());
        out.add(Shop.piece("compressor_wheel", 1, Alloy.ALUMINIUM_PISTON, Form.FORGING, 0.08 * core)
                .cut(Process.MILL, 0.9, 3 * wheel, tip, 7, true).as(Feature.AEROFOIL).cut(Process.TURN, 0.1, wheel, tip, 7).polish(3 * wheel, tip).build());
        out.add(Shop.piece("turbo_shaft", 1, Alloy.ALLOY_STEEL, Form.BAR, 0.05 * core)
                .cut(Process.TURN, 0.8, Math.PI * 0.2 * tip * 1.5 * tip, 0.2 * tip, 7)
                .cut(Process.GRIND, 0.2, Math.PI * 0.2 * tip * 0.6 * tip, 0.2 * tip, 5).build());
        out.add(Shop.piece("turbine_housing", 1, Alloy.DUCTILE_IRON, Form.CASTING, 0.5 * core).modulus(0.004)
                .cut(Process.BORE, 0.7, Math.PI * tip * 0.5 * tip, tip, 8).cut(Process.MILL, 0.3, 4 * wheel, 2 * tip, 9).drill(8, 0.008, 0.015, 12).tap(8, 0.008, 0.012, 8).build());
        out.add(Shop.piece("compressor_housing", 1, Alloy.ALUMINIUM_CAST, Form.CASTING, 0.22 * core).modulus(0.003)
                .cut(Process.BORE, 0.7, Math.PI * tip * 0.5 * tip, tip, 8).cut(Process.MILL, 0.3, 4 * wheel, 2 * tip, 9).drill(6, 0.006, 0.012, 12).tap(6, 0.006, 0.01, 8).build());
        if (cooler > 0) {
            out.add(Shop.piece("intercooler", 1, Alloy.ALUMINIUM_2024, Form.SHEET, cooler).weld(4 * Math.sqrt(cooler / 6)).build());
        }
    }

    // ---------------------------------------------------------------- gas turbines

    /** A compressor, fan or turbine's discs, blades and casing (a share each of its mass). */
    static void rotor(List<Workpiece> out, String key, EngineMaterial m, double kg, double tipDiameterM, int stages, boolean castBlades) {
        Alloy a = alloy(m);
        Alloy disc = a.forgeable ? a : Alloy.INCONEL_718;
        double area = Math.PI / 4 * tipDiameterM * tipDiameterM;
        out.add(Shop.piece(key + "_disc", Math.max(stages, 1), disc, Form.FORGING, 0.45 * kg / Math.max(stages, 1)).turned(Math.max(0.02, 0.06 * tipDiameterM), tipDiameterM)
                .cut(Process.TURN, 0.8, 2 * area, tipDiameterM, 7).cut(Process.BORE, 0.2, Math.PI * 0.2 * tipDiameterM * 0.05, 0.2 * tipDiameterM, 6)
                .drill(12, 0.008, 0.03, 9)
                .firTree(40, 40 * 0.05 * tipDiameterM * 0.01 * tipDiameterM * Math.max(0.02, 0.06 * tipDiameterM), 40 * 2 * 0.05 * tipDiameterM * Math.max(0.02, 0.06 * tipDiameterM),
                        Math.max(0.02, 0.06 * tipDiameterM), 0.05 * tipDiameterM, 7).build());
        Shop.Piece blades = Shop.piece(key + "_blades", Math.max(stages, 1), a, castBlades ? Form.CASTING : Form.FORGING, 0.3 * kg / Math.max(stages, 1));
        if (castBlades) {
            blades.modulus(0.001).cut(Process.GRIND, 1, 2 * area, 0.05 * tipDiameterM, 6, true).as(Feature.AEROFOIL).coolingHoles(20, 0.0005, 0.01, 8);
        }
        else {
            blades.cut(Process.MILL, 0.9, 2 * area, 0.05 * tipDiameterM, 7, true).as(Feature.AEROFOIL)
                    .cut(Process.GRIND, 0.1, area, 0.05 * tipDiameterM, 6, true).as(Feature.AEROFOIL).polish(2 * area, 0.05 * tipDiameterM);
        }
        out.add(blades.build());
        out.add(Shop.piece(key + "_casing", 1, a.castable ? a : Alloy.STAINLESS_STEEL, Form.SHEET, 0.25 * kg).turned(Math.max(0.05, 0.08 * Math.max(stages, 1)), 1.05 * tipDiameterM)
                .weld(2 * Math.PI * tipDiameterM * 2)
                .cut(Process.BORE, 1, Math.PI * tipDiameterM * 0.1 * Math.max(stages, 1), tipDiameterM, 7).build());
    }

    /** Sheet-metal ducts (inlet, nozzle, mixer, combustor) of a mass and girth, welded along and round. */
    static Workpiece duct(String key, EngineMaterial m, double kg, double diameterM, boolean coolingHoles) {
        Alloy a = alloy(m);
        Shop.Piece p = Shop.piece(key, 1, a.family == Alloy.Family.COMPOSITE ? Alloy.ALUMINIUM_2024 : a, Form.SHEET, kg).turned(1.5 * Math.max(diameterM, 0.05), Math.max(diameterM, 0.05))
                .weld(3 * Math.PI * Math.max(diameterM, 0.05));
        if (coolingHoles) { p.drill(400, 0.0008, 0.0015, 7, true).as(Feature.COOLING_HOLE); }
        return p.build();
    }

    /** Accessories (gearbox, pumps, controls) cast and machined, and the fuel nozzles turned and drilled on a CNC. */
    static void accessories(List<Workpiece> out, double kg) {
        out.add(Shop.piece("accessories", 1, Alloy.ALUMINIUM_CAST, Form.CASTING, 0.8 * kg).cut(Process.MILL, 0.6, 0.05, 0.1, 8)
                .cut(Process.BORE, 0.4, 0.01, 0.03, 7).drill(24, 0.008, 0.02, 11).tap(24, 0.008, 0.015, 8).build());
        out.add(Shop.piece("fuel_nozzles", 1, Alloy.STAINLESS_STEEL, Form.BAR, 0.2 * kg).cut(Process.TURN, 1, 0.01, 0.01, 7).drill(12, 0.0005, 0.002, 5, true).as(Feature.ORIFICE).build());
    }

    static List<Workpiece> jet(JetEngineDesign e) {
        List<Workpiece> out = new ArrayList<>();
        double a8 = e.sizing().nozzleThroatM2(), d = e.compressor().tipDiameterM();
        out.add(duct("inlet", e.inlet().material(), e.inlet().massKg(), d, false));
        rotor(out, "compressor", e.compressor().material(), e.compressor().massKg(), d, e.compressor().stages(), false);
        out.add(duct("combustor", e.combustor().material(), e.combustor().massKg(), e.combustor().casingDiameterM(), true));
        rotor(out, "turbine", e.turbine().material(), e.turbine().massKg(), e.turbine().tipDiameterM(), e.turbine().stages(), !alloy(e.turbine().material()).forgeable);
        out.add(duct("nozzle", e.nozzle().material(), e.nozzle().massFor(a8), Math.sqrt(4 * a8 / Math.PI), false));
        afterburner(out, e.afterburner(), a8);
        accessories(out, e.control().massFor(e.compressor().airflowKgPerS()));
        return out;
    }

    /** An afterburner fitted to a throat: duct and liner (four fifths), the variable nozzle's petals (the rest). */
    static void afterburner(List<Workpiece> out, dev.mcmod.hdfv.part.engine.AfterburnerDesign a, double throatAreaM2) {
        if (!a.fitted()) { return; }
        double kg = a.massFor(throatAreaM2), d = 1.4 * Math.sqrt(4 * throatAreaM2 / Math.PI);
        out.add(duct("afterburner", a.material(), 0.8 * kg, d, true));
        Alloy al = alloy(a.material());
        out.add(Shop.piece("nozzle_petals", 16, al.family == Alloy.Family.COMPOSITE ? Alloy.INCONEL_718 : al, Form.PLATE, 0.2 * kg / 16).size(0.4 * d, 0.2 * d, 0.01)
                .cut(Process.MILL, 1, 2 * 0.4 * d * 0.2 * d, 0.4 * d, 8).drill(2, 0.008, 0.01, 9).build());
    }

    /** A piece of a material in its usual form: a laminate for a composite, else from sheet, welded along a seam. */
    static Workpiece sheet(String key, EngineMaterial m, double kg, double sizeM, double seamM) {
        Alloy a = alloy(m);
        if (a.family == Alloy.Family.COMPOSITE) { return Shop.piece(key, 1, a, Form.LAMINATE, kg).thickness(0.004).build(); }
        return Shop.piece(key, 1, a, Form.SHEET, kg).size(sizeM, sizeM, 0.2 * sizeM).weld(seamM).build();
    }

    static List<Workpiece> ramjet(dev.mcmod.hdfv.part.engine.RamjetDesign e) {
        List<Workpiece> out = new ArrayList<>();
        var s = e.model();
        var in = e.inlet();
        double d = in.captureDiameterM(), inletKg = in.massKg();
        Alloy inAlloy = alloy(in.material());
        if (in.kind() == dev.mcmod.aerocore.api.engine.SupersonicInlet.Kind.PITOT) {
            out.add(duct("inlet", in.material(), inletKg, d, false));
        } else {
            // the spike (or ramps) carries the compression; the cowl is a sheet duct
            boolean cone = in.kind() == dev.mcmod.aerocore.api.engine.SupersonicInlet.Kind.CONE;
            Alloy spike = inAlloy.family == Alloy.Family.COMPOSITE ? Alloy.ALUMINIUM_2024 : inAlloy;
            Shop.Piece p = Shop.piece(cone ? "inlet_spike" : "inlet_ramps", 1, spike, cone ? Form.FORGING : Form.PLATE, 0.4 * inletKg);
            if (cone) { p.turned(0.6 * in.lengthM(), 0.5 * d).cut(Process.TURN, 1, Math.PI * 0.25 * d * 0.6 * in.lengthM(), 0.5 * d, 8); }
            else { p.size(0.6 * in.lengthM(), d, 0.02).cut(Process.MILL, 1, 2 * d * 0.6 * in.lengthM(), in.lengthM(), 8, true); }
            out.add(p.drill(8, 0.008, 0.02, 10).build());
            out.add(duct("inlet_cowl", in.material(), 0.6 * inletKg, d, false));
        }
        double combustorKg = e.combustor().massFor(in.captureAreaM2()), dc = Math.sqrt(4 * e.combustor().areaRatio() * in.captureAreaM2() / Math.PI);
        double shells = combustorKg / 1.1;   // the flameholders are a tenth over the shells
        out.add(duct("combustor", e.combustor().material(), shells, dc, true));
        out.add(sheet("flameholders", e.combustor().material(), combustorKg - shells, dc, 3 * Math.PI * dc));
        double a8 = s.throatAreaM2();
        out.add(duct("nozzle", e.nozzle().material(), e.nozzle().massFor(a8), Math.sqrt(4 * a8 / Math.PI), false));
        accessories(out, e.control().massFor(s.point().airflowKgPerS()));
        return out;
    }

    static List<Workpiece> scramjet(dev.mcmod.hdfv.part.engine.ScramjetDesign e) {
        List<Workpiece> out = new ArrayList<>();
        var in = e.inlet();
        double w = in.widthM(), inletKg = in.massKg();
        Alloy a = alloy(in.material());
        Alloy plate = a.family == Alloy.Family.COMPOSITE ? Alloy.INCONEL_718 : a;
        // the ramps and cowl milled from plate (five-axis: the ramps' angles and the leading edges)
        out.add(Shop.piece("inlet_ramps", 1, plate, Form.PLATE, inletKg).size(in.lengthM(), w, 0.03)
                .cut(Process.MILL, 1, 2 * w * in.lengthM(), in.lengthM(), 8, true).drill(12, 0.006, 0.01, 10).build());
        var c = e.combustor();
        double kg = c.massFor(in.throatAreaM2()), side = Math.sqrt(in.throatAreaM2() * c.areaRatio());
        Alloy ca = alloy(c.material());
        Alloy wall = ca.family == Alloy.Family.COMPOSITE ? Alloy.INCONEL_718 : ca;
        Shop.Piece p = Shop.piece("combustor_panels", 4, wall, c.fuelCooled() ? Form.PLATE : Form.SHEET, kg / 4).size(2 * c.lengthM(), Math.max(side, 0.05), 0.01);
        if (c.fuelCooled()) { p.cut(Process.MILL, 1, 2 * c.lengthM() * side, 2 * c.lengthM(), 8, true); }   // the cooling channels
        if (!c.fuelCooled()) { p.profile(); }
        out.add(p.drill(40, 0.001, 0.005, 8, true).as(Feature.ORIFICE).weld(2 * 2 * c.lengthM()).build());   // the injectors' holes; the panels welded at their edges
        double air = 0.0184 * 7 * 295 * in.captureAreaM2();
        accessories(out, e.control().massFor(air));
        return out;
    }

    static List<Workpiece> pulsejet(dev.mcmod.hdfv.part.engine.PulsejetDesign e) {
        List<Workpiece> out = new ArrayList<>();
        var t = e.tube();
        double length = t.chamberLengthM() + t.taperLengthM() + t.tailLengthM();
        Alloy ta = alloy(t.material());
        out.add(Shop.piece("tube", 1, ta.family == Alloy.Family.COMPOSITE ? Alloy.STAINLESS_STEEL : ta, Form.SHEET, t.massKg()).turned(length, t.chamberDiameterM())
                .weld(length + 3 * Math.PI * t.chamberDiameterM()).build());
        var v = e.valve();
        double valveKg = v.massFor(t.chamberDiameterM());
        if (v.valveless()) {
            out.add(Shop.piece("intake_pipe", 1, Alloy.STEEL, Form.SHEET, valveKg).turned(v.intakeLengthM(), v.intakeDiameterM())
                    .weld(v.intakeLengthM() + Math.PI * v.intakeDiameterM()).build());
        } else {
            double reeds = Double.isNaN(v.massGivenKg()) ? v.valveAreaM2() * v.reedThicknessM() * v.reedMaterial().densityKgPerM3() : 0.1 * valveKg;
            double gridD = t.chamberDiameterM();
            out.add(Shop.piece("valve_grid", 1, Alloy.STEEL, Form.PLATE, valveKg - reeds).size(gridD, gridD, 0.02)
                    .cut(Process.MILL, 1, 2 * Math.PI / 4 * gridD * gridD, gridD, 9).drill(24, 0.006, 0.01, 10).build());
            int count = Math.max(1, (int) Math.round(v.valveAreaM2() / Math.max(v.reedLengthM() * 0.015, 1e-5)));
            out.add(Shop.piece("reeds", count, alloy(v.reedMaterial()), Form.SHEET, reeds / count).size(v.reedLengthM(), 0.015, v.reedThicknessM()).profile()
                    .cut(Process.GRIND, 1, 2 * v.reedLengthM() * 0.015, v.reedLengthM(), 7).build());
        }
        accessories(out, e.control().massFor());
        return out;
    }

    static List<Workpiece> solidRocket(dev.mcmod.hdfv.part.engine.SolidRocketDesign e) {
        List<Workpiece> out = new ArrayList<>();
        var g = e.grain();
        double length = e.grainLengthM(), d = e.diameter();
        var cs = e.casing();
        Alloy ca = alloy(cs.material());
        double casingKg = cs.massFor(g.outerDiameterM(), length);
        if (ca.family == Alloy.Family.COMPOSITE) { out.add(Shop.piece("casing", 1, ca, Form.LAMINATE, casingKg).thickness(cs.wallThicknessM()).build()); }
        else {
            // a tube turned and threaded, its bulkheads welded in
            out.add(Shop.piece("casing", 1, ca, Form.SHEET, casingKg).turned(length, d)
                    .cut(Process.TURN, 1, Math.PI * d * 0.04, d, 8).thread(d, 0.04, 8).weld(2 * Math.PI * d + length).drill(12, 0.005, cs.wallThicknessM(), 10).build());
        }
        var n = e.nozzle();
        Alloy na = alloy(n.material());
        double de = n.throatDiameterM() * Math.sqrt(n.expansionRatio());
        if (na.family == Alloy.Family.COMPOSITE) { out.add(Shop.piece("rocket_nozzle", 1, na, Form.LAMINATE, n.massKg()).thickness(Math.max(0.003, n.throatDiameterM() / 5)).build()); }
        else {
            out.add(Shop.piece("rocket_nozzle", 1, na, Form.BAR, n.massKg()).turned(2 * de, de)
                    .cut(Process.TURN, 0.9, Math.PI * de * 2 * de, de, 8).cut(Process.GRIND, 0.1, Math.PI * n.throatDiameterM() * n.throatDiameterM(), n.throatDiameterM(), 7).build());
        }
        out.add(Shop.piece("igniter_mounts", 1, Alloy.STEEL, Form.BAR, e.booster().massFor(g.propellantKg())).turned(0.1, 0.3 * d)
                .cut(Process.TURN, 1, 0.01, 0.3 * d, 9).drill(6, 0.006, 0.01, 11).tap(6, 0.006, 0.01, 8).build());
        return out;
    }

    static List<Workpiece> turboRamjet(dev.mcmod.hdfv.part.engine.TurboRamjetDesign e) {
        List<Workpiece> out = new ArrayList<>();
        double a8 = e.sizing().core().nozzleThroatM2(), d = e.compressor().tipDiameterM();
        out.add(duct("inlet", e.inlet().material(), e.inlet().massKg(), e.inlet().captureDiameterM(), false));
        rotor(out, "compressor", e.compressor().material(), e.compressor().massKg(), d, e.compressor().stages(), false);
        out.add(duct("combustor", e.combustor().material(), e.combustor().massKg(), e.combustor().casingDiameterM(), true));
        rotor(out, "turbine", e.turbine().material(), e.turbine().massKg(), e.turbine().tipDiameterM(), e.turbine().stages(), !alloy(e.turbine().material()).forgeable);
        out.add(duct("nozzle", e.nozzle().material(), e.nozzle().massFor(a8), Math.sqrt(4 * a8 / Math.PI), false));
        afterburner(out, e.afterburner(), a8);
        out.add(duct("bypass_ducts", e.bypass().material(), e.bypass().massFor(d, e.coreLengthM()), 0.1 * d, false));
        accessories(out, e.control().massFor(e.compressor().airflowKgPerS()));
        return out;
    }

    static List<Workpiece> turbofan(TurbofanDesign e) {
        List<Workpiece> out = new ArrayList<>();
        double a8 = e.sizing().nozzleThroatM2(), d8 = Math.sqrt(4 * a8 / Math.PI);
        out.add(duct("inlet", e.inlet().material(), e.inlet().massKg(), e.fan().tipDiameterM(), false));
        rotor(out, "fan", e.fan().material(), e.fan().massKg(), e.fan().tipDiameterM(), 1 + e.fan().boosterStages(), false);
        rotor(out, "compressor", e.compressor().material(), e.compressor().massKg(), e.compressor().tipDiameterM(), e.compressor().stages(), false);
        out.add(duct("combustor", e.combustor().material(), e.combustor().massKg(), e.combustor().casingDiameterM(), true));
        rotor(out, "hp_turbine", e.hpTurbine().material(), e.hpTurbine().massKg(), e.hpTurbine().tipDiameterM(), e.hpTurbine().stages(), !alloy(e.hpTurbine().material()).forgeable);
        rotor(out, "lp_turbine", e.lpTurbine().material(), e.lpTurbine().massKg(), e.lpTurbine().tipDiameterM(), e.lpTurbine().stages(), !alloy(e.lpTurbine().material()).forgeable);
        out.add(duct("mixer", e.mixer().material(), e.mixer().massFor(1.4 * d8), 1.4 * d8, false));
        out.add(duct("nozzle", e.nozzle().material(), e.nozzle().massFor(a8), d8, false));
        afterburner(out, e.afterburner(), a8);
        accessories(out, e.accessoriesMassKg());
        return out;
    }

    static List<Workpiece> shaftTurbine(ShaftTurbineDesign e) {
        List<Workpiece> out = new ArrayList<>();
        double a8 = e.sizing().exhaustAreaM2(), d = e.compressor().tipDiameterM();
        out.add(duct("inlet", e.inlet().material(), e.inlet().massKg(), d, false));
        rotor(out, "compressor", e.compressor().material(), e.compressor().massKg(), d, e.compressor().stages(), false);
        out.add(duct("combustor", e.combustor().material(), e.combustor().massKg(), e.combustor().casingDiameterM(), true));
        rotor(out, "turbine", e.turbine().material(), e.turbine().massKg(), e.turbine().tipDiameterM(), e.turbine().stages(), !alloy(e.turbine().material()).forgeable);
        rotor(out, "power_turbine", e.powerTurbine().material(), e.powerTurbine().massKg(), e.powerTurbine().tipDiameterM(), e.powerTurbine().stages(), false);
        // the reduction gearbox: gears forged and cut to IT6 (aircraft gearing), a cast magnesium or aluminium case
        double gb = e.gearbox().massKg();
        out.add(Shop.piece("reduction_gears", 1, alloy(e.gearbox().material()), Form.FORGING, 0.5 * gb).cut(Process.TURN, 0.4, 0.05, 0.1, 7)
                .spline(2e-6, 0.004, 0.03, 7, true)
                .cut(Process.MILL, 0.5, 0.08, 0.1, 6).as(Feature.GEAR_TEETH).chamfer(0.08, 0.1).cut(Process.GRIND, 0.1, 0.05, 0.1, 6).as(Feature.GEAR_TEETH).build());
        out.add(Shop.piece("gearbox_case", 1, Alloy.MAGNESIUM, Form.CASTING, 0.5 * gb).cut(Process.MILL, 0.6, 0.1, 0.3, 8).cut(Process.BORE, 0.4, 0.02, 0.08, 7)
                .drill(20, 0.008, 0.02, 11).tap(20, 0.008, 0.015, 8).build());
        out.add(duct("exhaust", e.exhaust().material(), e.exhaust().massFor(a8), Math.sqrt(4 * a8 / Math.PI), false));
        accessories(out, e.accessoriesMassKg());
        return out;
    }
}
