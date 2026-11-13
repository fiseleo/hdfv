package dev.mcmod.hdfv.neoforge.repair;

import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.vehicle.Vehicle;
import dev.mcmod.aerocore.neoforge.vehicle.PartVehicle;
import dev.mcmod.hdfv.neoforge.damage.VehicleConditions;
import dev.mcmod.hdfv.repair.FieldRepair;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Using a field kit on a vehicle (0.25; core FieldRepair has the rules): right-click a vehicle with a kit and hold it.
 * The server looks at where on the vehicle the player looks (the look ray on the vehicle's box), and of the parts the
 * kit can do something for takes the one whose centre is nearest that point (centre); with none, it says
 * why for the nearest it could have worked on, and nothing starts. Started, the player holds the kit for the kit's
 * ticks (an item in use, as eating); looking away or going out of reach stops it, releasing the key too. At the end the
 * condition is set (PartVehicle.setUsed, which tells the clients), the kit loses a point of durability (not in creative
 * mode), and the action bar says what was mended and what is left of it. The vehicle is chosen again at the end: the
 * part is the one looked at then.
 */
public final class FieldKits {
    private FieldKits() {}

    /** Reach beyond the player's interaction range, m (the look ray meets the vehicle's box, not its parts). */
    public static final double REACH_MARGIN = 1.0;

    /** Who works on which vehicle with a kit (server: the player's id to the vehicle's entity id). */
    private static final Map<UUID, Integer> WORKING = new ConcurrentHashMap<>();

    /** A kit used on a vehicle: on the server, starts holding it if there is something to do; on both sides, the vehicle is not boarded. */
    static void interact(PlayerInteractEvent.EntityInteract e) {
        if (!(e.getTarget() instanceof PartVehicle v) || !(e.getItemStack().getItem() instanceof FieldKitItem)) { return; }
        Player player = e.getEntity();
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.CONSUME);
        if (player.level().isClientSide || player.isUsingItem()) { return; }
        Component why = begin(player, v, e.getHand());
        if (why != null) { player.displayClientMessage(why, true); }
    }

    /**
     * Server: starts using the kit in a hand on a vehicle when it can do something there (the client follows the
     * server's use); null when started, else why not.
     */
    public static Component begin(Player player, PartVehicle v, InteractionHand hand) {
        if (!(player.getItemInHand(hand).getItem() instanceof FieldKitItem kit) || v.vehicle() == null || v.vehicleState() == null) { return null; }
        FieldRepair.Choice c = choose(player, v, kit.kit);
        if (c == null) { return Component.translatable("hdfv.repair.field.away"); }
        if (!c.outcome().done()) { return refusal(v, kit.kit, c); }
        WORKING.put(player.getUUID(), v.getId());
        player.startUsingItem(hand);
        return null;
    }

    /** The vehicle a player works on with a kit, if it is still there. */
    static PartVehicle target(Player player) {
        Integer id = WORKING.get(player.getUUID());
        return id != null && player.level().getEntity(id) instanceof PartVehicle v && v.isAlive() ? v : null;
    }

    /** Whether a player working with a kit still looks at the vehicle within reach. */
    static boolean stillAt(Player player) {
        PartVehicle v = target(player);
        return v != null && lookPoint(player, v) != null;
    }

    /** The use given up (released, looked away). */
    static void stop(Player player) { WORKING.remove(player.getUUID()); }

    /** Where the player's look meets the vehicle's box within reach (a world point), or null when it does not. */
    public static Vec3d lookPoint(Player player, PartVehicle v) {
        Vec3 eye = player.getEyePosition(), look = player.getViewVector(1);
        AABB box = v.getBoundingBox().inflate(0.1);
        double reach = player.entityInteractionRange() + REACH_MARGIN;
        if (box.contains(eye)) { return new Vec3d(eye.x, eye.y, eye.z); }
        Optional<Vec3> hit = box.clip(eye, eye.add(look.scale(reach)));
        return hit.map(h -> new Vec3d(h.x, h.y, h.z)).orElse(null);
    }

    /** What a kit would do on a vehicle where the player looks (null when the player does not look at it within reach). */
    public static FieldRepair.Choice choose(Player player, PartVehicle v, FieldRepair.Kit kit) {
        Vec3d at = lookPoint(player, v);
        if (at == null || v.vehicle() == null) { return null; }
        List<FieldRepair.Candidate> parts = new ArrayList<>();
        for (Vehicle.Installed p : v.vehicle().installed()) {
            List<String> names = p.part().conditions();
            if (names.isEmpty()) { continue; }
            List<Double> used = new ArrayList<>();
            for (String c : names) { used.add(v.used(p.id(), c)); }
            parts.add(new FieldRepair.Candidate(p.id(), centre(v, p).subtract(at).length(), names, used));
        }
        return FieldRepair.choose(kit, parts);
    }

    /** Where a part's centre of mass is now (the vehicle's position this tick, not the last one's as VehicleDamage.world draws it). */
    public static Vec3d centre(PartVehicle v, Vehicle.Installed p) { return v.partPoint(p, p.part().mass().centre(), 1); }

    /** Server, at the end of a use: the repair done and the kit worn, or why not (said on the action bar). */
    public static Component finish(Player player, ItemStack stack, FieldKitItem item) {
        PartVehicle v = target(player);
        WORKING.remove(player.getUUID());
        if (v == null) { return Component.translatable("hdfv.repair.field.away"); }
        FieldRepair.Choice c = choose(player, v, item.kit);
        if (c == null) { return Component.translatable("hdfv.repair.field.away"); }
        if (!c.outcome().done()) { return refusal(v, item.kit, c); }
        v.setUsed(c.part(), c.condition(), c.outcome().after());
        if (player instanceof net.minecraft.server.level.ServerPlayer sp) { dev.mcmod.hdfv.neoforge.tutorial.Milestones.reach(sp, "repaired", "field"); }   // the tutorial
        EquipmentSlot slot = player.getUsedItemHand() == InteractionHand.OFF_HAND ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND;
        stack.hurtAndBreak(1, player, slot);
        return Component.translatable("hdfv.repair.field.done", part(v, c.part()), VehicleConditions.conditionName(c.condition()), left(c.outcome().before()),
                left(c.outcome().after()));
    }

    /** Why a kit did nothing, in words. */
    static Component refusal(PartVehicle v, FieldRepair.Kit kit, FieldRepair.Choice c) {
        if (c.part() == null) { return Component.translatable("hdfv.repair.field.nothing", Component.translatable("item.hdfv." + kit.id())); }
        Component part = part(v, c.part()), what = VehicleConditions.conditionName(c.condition());
        return Component.translatable("hdfv.repair.field." + c.outcome().refusal(), part, what, left(c.outcome().before()));
    }

    private static Component part(PartVehicle v, String id) {
        Vehicle.Installed p = v.vehicle() == null ? null : v.vehicle().installed(id);
        return p == null ? Component.literal(id) : VehicleConditions.partName(p);
    }

    /** What is left of a condition, whole per cent (the status screen's). */
    private static String left(double used) { return String.valueOf(Math.round(100 * (1 - Math.clamp(used, 0.0, 1.0)))); }
}
