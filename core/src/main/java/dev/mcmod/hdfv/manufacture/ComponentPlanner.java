package dev.mcmod.hdfv.manufacture;

import dev.mcmod.hdfv.part.component.Component;

import java.util.List;

/**
 * The standard components' routes (0.23): each one workpiece whose finished mass is the component's, through the same
 * forming steps and cuts as the player's parts (Shop.piece). The grade (Component.grade) is what the route needs:
 * A only the foundry and the press, B the lathe, drill press and planer, C the milling machine, boring mill, grinders,
 * gear cutters and composite oven, D the CNC. ComponentRouteTest holds each route to its grade.
 *
 * <ul>
 * <li>Sizes are typical of the part: a 0.5 x 0.5 m plate of 5 mm, a 1 m lathe bed casting, a 35 mm shaft half a metre
 *     long, an 80 mm bearing, a 28 mm ball screw 0.6 m long.</li>
 * <li>Grades: what a machine of that kind holds (a turned shaft IT8, a lathe leadscrew IT8, a bearing ground to IT5,
 *     a rolled ball screw's thread IT7 (the C7 class) and its journals IT5, a linear guide's rail ground to IT4).</li>
 * </ul>
 */
final class ComponentPlanner {
    private ComponentPlanner() {}

    static List<Workpiece> plan(Component c) {
        String k = c.id();
        Shop.Piece p = switch (c) {
            case STEEL_PLATE -> Shop.piece(k, 1, Alloy.STEEL, Form.SHEET, c.kg).size(0.5, 0.5, 0.005);
            case ELECTRICAL_STEEL -> Shop.piece(k, 1, Alloy.STEEL, Form.SHEET, c.kg).size(0.3, 0.3, 0.007);
            case COPPER_WIRE -> Shop.piece(k, 1, Alloy.COPPER, Form.WIRE, c.kg).size(0.3, 0.3, 0.05);
            case BED_CASTING -> Shop.piece(k, 1, Alloy.GREY_IRON, Form.CASTING, c.kg).size(1.0, 0.3, 0.2).modulus(0.012);
            case FRAME_CASTING -> Shop.piece(k, 1, Alloy.GREY_IRON, Form.CASTING, c.kg).size(0.4, 0.3, 0.3).modulus(0.01);
            case CYLINDER_CASTING -> Shop.piece(k, 1, Alloy.GREY_IRON, Form.CASTING, c.kg).size(0.3, 0.25, 0.25).modulus(0.012);
            case CAST_GEAR -> Shop.piece(k, 1, Alloy.GREY_IRON, Form.CASTING, c.kg).turned(0.05, 0.25).modulus(0.01);
            case CAST_PULLEY -> Shop.piece(k, 1, Alloy.GREY_IRON, Form.CASTING, c.kg).turned(0.08, 0.3).modulus(0.008);
            case BRONZE_BUSHING -> Shop.piece(k, 1, Alloy.BRONZE, Form.CASTING, c.kg).turned(0.06, 0.06).modulus(0.006);
            case FORGED_SHAFT -> Shop.piece(k, 1, Alloy.STEEL, Form.FORGING, c.kg).turned(0.8, 0.05);
            case FORGED_CRANK -> Shop.piece(k, 1, Alloy.STEEL, Form.FORGING, c.kg).size(0.4, 0.15, 0.06);
            case TURNED_SHAFT -> Shop.piece(k, 1, Alloy.STEEL, Form.BAR, c.kg).turned(0.5, 0.035)
                    .cut(Process.TURN, 1, Math.PI * 0.035 * 0.5, 0.035, 8);
            case LEADSCREW -> Shop.piece(k, 1, Alloy.STEEL, Form.BAR, c.kg).turned(1.0, 0.025)
                    .cut(Process.TURN, 1, Math.PI * 0.025 * 1.0, 0.025, 8).thread(0.025, 0.9, 8);
            case FASTENER_KIT -> Shop.piece(k, 1, Alloy.STEEL, Form.BAR, c.kg).turned(0.12, 0.016)
                    .cut(Process.TURN, 1, Math.PI * 0.016 * 0.12, 0.016, 10).thread(0.012, 0.08, 9);
            case STEEL_TUBE -> Shop.piece(k, 1, Alloy.STEEL, Form.TUBE, c.kg).turned(1.0, 0.06);
            case PLANED_SLIDEWAY -> Shop.piece(k, 1, Alloy.GREY_IRON, Form.CASTING, c.kg).size(1.0, 0.15, 0.1).modulus(0.012)
                    .cut(Process.PLANE, 1, 0.25, 1.0, 9);
            case VALVE_BODY -> Shop.piece(k, 1, Alloy.STEEL, Form.CASTING, c.kg).size(0.15, 0.12, 0.12).modulus(0.008)
                    .cut(Process.TURN, 1, 0.03, 0.12, 9).drill(4, 0.012, 0.03, 11).tap(4, 0.012, 0.02, 10);
            case BORED_CYLINDER -> Shop.piece(k, 1, Alloy.GREY_IRON, Form.CASTING, c.kg).turned(0.3, 0.2).modulus(0.012)
                    .cut(Process.TURN, 1, Math.PI * 0.12 * 0.3, 0.12, 7);
            case CUT_GEAR -> Shop.piece(k, 1, Alloy.STEEL, Form.FORGING, c.kg).turned(0.04, 0.15)
                    .cut(Process.TURN, 0.5, 0.03, 0.15, 8).cut(Process.MILL, 0.5, 0.04, 0.15, 8).as(Feature.GEAR_TEETH);
            case BALL_BEARING -> Shop.piece(k, 1, Alloy.ALLOY_STEEL, Form.BAR, c.kg).turned(0.025, 0.08)
                    .cut(Process.TURN, 0.8, 0.012, 0.08, 8).cut(Process.GRIND, 0.2, 0.012, 0.08, 5);
            case GROUND_SLIDEWAY -> Shop.piece(k, 1, Alloy.GREY_IRON, Form.CASTING, c.kg).size(1.0, 0.1, 0.08).modulus(0.01)
                    .cut(Process.PLANE, 0.8, 0.2, 1.0, 9).cut(Process.GRIND, 0.2, 0.1, 1.0, 5);
            case SPINDLE -> Shop.piece(k, 1, Alloy.ALLOY_STEEL, Form.FORGING, c.kg).turned(0.5, 0.08)
                    .cut(Process.TURN, 0.8, 0.12, 0.08, 7).cut(Process.GRIND, 0.2, 0.05, 0.08, 5);
            case BALL_SCREW -> Shop.piece(k, 1, Alloy.ALLOY_STEEL, Form.BAR, c.kg).turned(0.6, 0.028)
                    .cut(Process.TURN, 0.8, Math.PI * 0.028 * 0.6, 0.028, 7).thread(0.028, 0.5, 7)
                    .cut(Process.GRIND, 0.2, Math.PI * 0.028 * 0.1, 0.028, 5);
            case CIRCUIT_BOARD -> Shop.piece(k, 1, Alloy.GFRP, Form.LAMINATE, c.kg).thickness(0.0016).size(0.2, 0.15, 0.0016)
                    .drill(100, 0.001, 0.0016, 10);
            case LINEAR_GUIDE -> Shop.piece(k, 1, Alloy.ALLOY_STEEL, Form.BAR, c.kg).size(1.0, 0.035, 0.03)
                    .cut(Process.MILL, 0.8, 0.13, 1.0, 6).cut(Process.GRIND, 0.2, 0.1, 1.0, 4);
            case PRECISION_SPINDLE -> Shop.piece(k, 1, Alloy.ALLOY_STEEL, Form.FORGING, c.kg).turned(0.5, 0.1)
                    .cut(Process.TURN, 0.8, 0.15, 0.1, 5).cut(Process.GRIND, 0.2, 0.06, 0.1, 4);
            // 0.25: a backing stamped from sheet at the press, drilled for the rivets that hold its lining (the drill press: B)
            case FRICTION_LINING -> Shop.piece(k, 1, Alloy.STEEL, Form.SHEET, c.kg).size(0.2, 0.06, 0.012).drill(8, 0.005, 0.006, 11);
        };
        return List.of(p.build());
    }
}
