package dev.mcmod.hdfv.part.component;

import java.util.Locale;

/**
 * The standard components (0.23; the user 2026-10-20: "新增中間件" and "機器也用設計與工序做"): fixed designs that go
 * through the same planner, workpieces and machines as the player's parts, and come off the last machine as stackable
 * items that machines are assembled from (ComponentDesign; their routes in manufacture.ComponentPlanner).
 *
 * <p>The grade says which machines the route needs (ComponentPlanner and the recipe tests hold it to that):
 * <ul>
 * <li>A: the foundry and the forging press only (castings, forgings, rolled sheet and drawn wire): what the first
 *     lathe, drill press and planer are built from, fitted by hand at the assembly bench.</li>
 * <li>B: the lathe, the drill press and the planer (turned shafts, threads, bored cylinders, planed ways).</li>
 * <li>C: the milling machine, the boring mill, the grinders, the gear cutters and the composite oven (cut gears, ground
 *     bearings and ways, spindles, ball screws, circuit boards).</li>
 * <li>D: the CNC (linear guides, precision spindles): what the CNC's own upgrades and the numerically controlled
 *     machines need.</li>
 * </ul>
 * 0.25: the friction lining (B), a stamped steel backing with its moulded lining, drilled for its rivets: the pads or
 * shoes of one wheel's brake, or one clutch disc's facings; the repair shop relines brakes and clutches with it.
 * Masses are the finished pieces' (kg); a component is one workpiece. They are real objects (a 0.5 m shaft, a
 * 1 m bed casting): machines take fewer and smaller of them than the real ones (the game scale, recipe.GameScale).
 */
public enum Component {
    STEEL_PLATE('A', 10), ELECTRICAL_STEEL('A', 5), COPPER_WIRE('A', 2), BED_CASTING('A', 40), FRAME_CASTING('A', 15),
    CYLINDER_CASTING('A', 12), CAST_GEAR('A', 3), CAST_PULLEY('A', 5), BRONZE_BUSHING('A', 0.5), FORGED_SHAFT('A', 6), FORGED_CRANK('A', 6),
    TURNED_SHAFT('B', 3), LEADSCREW('B', 3), FASTENER_KIT('B', 1), STEEL_TUBE('B', 6), PLANED_SLIDEWAY('B', 15), VALVE_BODY('B', 4),
    BORED_CYLINDER('B', 12),
    CUT_GEAR('C', 2), BALL_BEARING('C', 0.5), GROUND_SLIDEWAY('C', 10), SPINDLE('C', 5), BALL_SCREW('C', 2.5), CIRCUIT_BOARD('C', 0.3),
    LINEAR_GUIDE('D', 3), PRECISION_SPINDLE('D', 6),
    // 0.25 (the repair shop relines brakes and clutches with it)
    FRICTION_LINING('B', 1);

    /** The machines its route needs: 'A' to 'D' (see the class). */
    public final char grade;
    /** Finished mass, kg. */
    public final double kg;

    Component(char grade, double kg) {
        this.grade = grade;
        this.kg = kg;
    }

    /** Its id: the item's and the workpiece's key. */
    public String id() { return name().toLowerCase(Locale.ROOT); }

    public static Component ofId(String id) {
        for (Component c : values()) { if (c.id().equals(id)) { return c; } }
        return null;
    }
}
