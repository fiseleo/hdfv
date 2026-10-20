package dev.mcmod.hdfv.manufacture;

import java.util.Locale;

/**
 * What machines use up (0.17; the user 2026-10-18: consumables for every machine, the old lathes and CNCs too, the
 * worn tools reground on the tool grinder). A job's use is counted in items (Wear): a fraction of a durable item's
 * life (its damage) or of a counted item (a tin, a bag, a box), the fractions carried by the machine.
 *
 * <ul>
 * <li>CUTTING_TOOLS: a set of inserts, drills, taps, hobs and cutters (one item a tool material, not a shape): ten
 *     cutting edges, each lasting Taylor's life at the speed the machine cuts (Wear). A dull set is reground on the
 *     tool grinder: high-speed steel five times, cermet three; (0.23) plain carbon tool steel, which the first lathes
 *     cut with before there is chromium, eight (Wear: it cuts at 0.4 of high-speed steel's speed and wears faster).</li>
 * <li>GRINDING_WHEEL: a 400 x 50 mm vitrified wheel worn from 400 to 260 mm across: 3.6 litres of wheel.</li>
 * <li>HONING_STONES: a set of six 150 x 12 x 6 mm stones, half of them usable: 32 cm^3.</li>
 * <li>LAPPING_COMPOUND: a 0.25 kg tin of abrasive paste (lapping and polishing).</li>
 * <li>SAW_BLADE: a bimetal blade that cuts 2 m^2 of carbon steel (more of softer metal).</li>
 * <li>ELECTRODES: a graphite (or copper) electrode block of 0.2 litre for sinking, a bundle of tube electrodes for
 *     hole drilling, or a plasma torch's electrode and nozzle for an hour of arc.</li>
 * <li>BRASS_WIRE: a 5 kg spool of 0.25 mm brass wire, 12 km.</li>
 * <li>ELECTROLYTE: a 10 kg can of sodium nitrate electrolyte.</li>
 * <li>GARNET: a 25 kg bag of 80 mesh garnet.</li>
 * <li>ASSIST_GAS: a 50 litre cylinder at 200 bar: 13.4 kg of oxygen or 11.7 kg of nitrogen (AeroCore Gases).</li>
 * <li>RIVETS: a box of 100 solid rivets (steel, or aluminium for the light alloys).</li>
 * </ul>
 */
public enum Consumable {
    CUTTING_TOOLS, GRINDING_WHEEL, HONING_STONES, LAPPING_COMPOUND, SAW_BLADE, ELECTRODES, BRASS_WIRE, ELECTROLYTE, GARNET, ASSIST_GAS, RIVETS;

    public String id() { return name().toLowerCase(Locale.ROOT); }

    /** The items that supply consumables: what each supplies and whether it wears (a durable item's damage) or is counted. */
    public enum Supply {
        HSS_TOOL_SET("hss_tool_set", CUTTING_TOOLS, true, 5), CERMET_TOOL_SET("cermet_tool_set", CUTTING_TOOLS, true, 3),
        GRINDING_WHEEL("grinding_wheel", Consumable.GRINDING_WHEEL, true, 0), HONING_STONES("honing_stones", Consumable.HONING_STONES, true, 0),
        LAPPING_COMPOUND("lapping_compound", Consumable.LAPPING_COMPOUND, false, 0), SAW_BLADE("saw_blade", Consumable.SAW_BLADE, true, 0),
        ELECTRODE("electrode", ELECTRODES, true, 0), BRASS_WIRE("brass_wire_spool", Consumable.BRASS_WIRE, true, 0),
        ELECTROLYTE("electrolyte", Consumable.ELECTROLYTE, false, 0), GARNET("garnet_abrasive", Consumable.GARNET, false, 0),
        OXYGEN("oxygen_cylinder", ASSIST_GAS, true, 0), NITROGEN("nitrogen_cylinder", ASSIST_GAS, true, 0), ARGON("argon_cylinder", null, true, 0),
        STEEL_RIVETS("steel_rivets", RIVETS, false, 0), ALUMINIUM_RIVETS("aluminium_rivets", RIVETS, false, 0),
        // 0.23: plain carbon tool steel, the first machines' tools before there is chromium for high-speed steel
        CARBON_TOOL_SET("carbon_tool_set", CUTTING_TOOLS, true, 8);

        /** The item's id. */
        public final String id;
        /** What it supplies (null: nothing yet: argon waits for TIG). */
        public final Consumable supplies;
        /** Whether it wears (damage; a gas cylinder empties) rather than being counted. */
        public final boolean durable;
        /** How many times a dull one can be reground (0: it cannot). */
        public final int regrinds;

        Supply(String id, Consumable supplies, boolean durable, int regrinds) { this.id = id; this.supplies = supplies; this.durable = durable; this.regrinds = regrinds; }

        public static Supply ofId(String id) {
            for (Supply s : values()) { if (s.id.equals(id)) { return s; } }
            return null;
        }
    }

    /** The cutting edges in a tool set; the units of a durable item's life (its maximum damage). */
    public static final int EDGES = 10, LIFE_UNITS = 1000;
}
