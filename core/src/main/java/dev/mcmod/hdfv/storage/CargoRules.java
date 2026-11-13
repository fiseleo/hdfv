package dev.mcmod.hdfv.storage;

import dev.mcmod.aerocore.api.vehicle.Part;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.hdfv.part.StorageBoxDesign;
import dev.mcmod.hdfv.part.body.BodyDesign;

/**
 * The rules of a vehicle's cargo (0.25) that need no game: how many slots a hold has, what it is rated to carry, and
 * the names of a body's holds. What the cargo weighs is ItemMass's (neoforge); AeroCore carries it as a point mass at
 * the hold's centre with its weight (Part.Hold, VehicleState.setHold).
 *
 * <ul>
 * <li>Slots: SLOTS_PER_M3 a cubic metre (a chest's 27 stacks in its one cubic metre), rounded, at least MIN_SLOTS (a
 *     glovebox-sized hold is still one row), at most MAX_SLOTS (four double chests: a screen of four pages) for a body's
 *     hold and MAX_CONTAINER_SLOTS (six pages) for a container. A 20 ft container's 33 m^3 would be 896 stacks; it is
 *     capped, the volume beyond being for what a stack does not show (pallets, dunnage).</li>
 * <li>Rated load: a body's hold its volume at the design's cargo density (BodyDesign.holdLimitKg: the design payload
 *     shared by volume, holds full); a storage box what its walls and size rate it for (StorageBoxDesign.ratedLoadKg).
 *     Shown, not enforced: more weighs more, and the physics answers (the vehicle sits lower, accelerates and brakes
 *     worse); the store's footer and the status screen draw it red.</li>
 * </ul>
 */
public final class CargoRules {
    private CargoRules() {}

    public static final double SLOTS_PER_M3 = 27;
    public static final int MIN_SLOTS = 9, MAX_SLOTS = 216, MAX_CONTAINER_SLOTS = 324;

    /** The slots of a hold of a volume (m^3), at most max; none for no volume. */
    public static int slots(double m3, int max) {
        if (!(m3 > 0) || Double.isInfinite(m3)) { return 0; }
        return (int) Math.clamp(Math.round(SLOTS_PER_M3 * m3), MIN_SLOTS, Math.max(max, MIN_SLOTS));
    }

    /** The most slots a hold of a part has: a container's MAX_CONTAINER_SLOTS, any other's MAX_SLOTS. */
    public static int maxSlots(PartDesign holder) {
        return holder instanceof StorageBoxDesign s && s.kind() == StorageBoxDesign.Kind.CONTAINER ? MAX_CONTAINER_SLOTS : MAX_SLOTS;
    }

    /** The slots of a part's hold. */
    public static int slots(PartDesign holder, Part.Hold hold) { return slots(hold.capacityM3(), maxSlots(holder)); }

    /** What a part's hold is rated to carry, kg (0: no rating). */
    public static double limitKg(PartDesign holder, Part.Hold hold) {
        if (holder instanceof StorageBoxDesign s) { return s.ratedLoadKg(); }
        if (holder instanceof BodyDesign b) {
            int i = moduleOf(hold.name());
            return i >= 0 && i < b.modules().size() ? b.holdLimitKg(i) : 0;
        }
        return 0;
    }

    /** Whether a hold carries more than it is rated for. */
    public static boolean overloaded(double kg, double limitKg) { return limitKg > 0 && kg > limitKg + 1e-9; }

    /** The name of a body module's hold (index from 0): "m3_cargo" for the third, as its mount points are named. */
    public static String holdName(int module) { return "m" + (module + 1) + "_cargo"; }

    /** The module (from 0) a body's hold is in, or -1 for a name that is not a body hold's. */
    public static int moduleOf(String holdName) {
        if (holdName == null || !holdName.startsWith("m") || !holdName.endsWith("_cargo")) { return -1; }
        try { return Integer.parseInt(holdName.substring(1, holdName.length() - "_cargo".length())) - 1; } catch (NumberFormatException e) { return -1; }
    }
}
