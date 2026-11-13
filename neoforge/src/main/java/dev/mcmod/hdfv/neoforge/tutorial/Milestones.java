package dev.mcmod.hdfv.neoforge.tutorial;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Where the game tells the tutorial a player did something (0.24; the advancements under data/hdfv/advancement/tutorial
 * listen for these, tools/tutorial.py writes them). Events:
 * <ul>
 * <li>design_saved (kind: the part's family, Designs.family) when a design is saved onto a blueprint;</li>
 * <li>formed (kind: the machine's station, lower case) when a player's use forms a multiblock machine;</li>
 * <li>machined (kind: part, component or workpiece) when a player takes a machine's output;</li>
 * <li>pedalled when a player turns a treadle; engine_running when an engine a player started runs on its stand;</li>
 * <li>rolled_out (kind: car, aircraft or boat) when a player rolls a finished vehicle out of an assembly room;</li>
 * <li>driven (metres so far), speed (km/h), height (m above the ground), mach (DriveTracker; kind as rolled_out);</li>
 * <li>fuelled; refit_started; painted; coupled; atc_cleared; prospected;</li>
 * <li>gusher and refined, which have no player of their own: the players near the rig or the unit;</li>
 * <li>0.25: repaired (kind: field for a kit's use that mended something, FieldKits.finish; shop for a repair shop's job
 *     done, which has no player of its own: the players near its controller, RepairShopBlockEntity.finish); loaded when
 *     a player closes a vehicle's hold with more in it than when they opened it (VehicleCargo.Hold.stopOpen).</li>
 * </ul>
 */
public final class Milestones {
    private Milestones() {}

    public static void reach(ServerPlayer player, String event) { reach(player, event, "", 0); }
    public static void reach(ServerPlayer player, String event, String kind) { reach(player, event, kind, 0); }

    public static void reach(ServerPlayer player, String event, String kind, double value) {
        HdfvTutorial.MILESTONE.get().trigger(player, event, kind, value);
    }

    /** For an entity's rider or player: the player, if it is one. */
    public static void reach(Entity who, String event, String kind) {
        if (who instanceof ServerPlayer player) { reach(player, event, kind, 0); }
    }

    /** Every player within the radius of a place (events without a player of their own). */
    public static void near(ServerLevel level, BlockPos at, double radius, String event) { near(level, at, radius, event, ""); }

    /** Every player within the radius of a place, with a kind. */
    public static void near(ServerLevel level, BlockPos at, double radius, String event, String kind) {
        for (ServerPlayer p : level.players()) {
            if (p.blockPosition().distSqr(at) <= radius * radius) { reach(p, event, kind, 0); }
        }
    }
}
