package dev.mcmod.hdfv.neoforge.fuel;

import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.Vehicle;
import dev.mcmod.aerocore.api.vehicle.VehicleState;
import dev.mcmod.aerocore.neoforge.vehicle.PartVehicle;
import dev.mcmod.hdfv.neoforge.machine.Workpieces;
import dev.mcmod.hdfv.part.engine.PulsejetDesign;
import dev.mcmod.hdfv.part.engine.SolidRocketDesign;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Servicing a vehicle by hand (0.10).
 * <ul>
 * <li>A propellant grain (KNSB or APCP, 1 kg an item: SolidRocketDesign.GRAIN_ITEM_KG) used on a vehicle loads its
 *     empty boosters of that propellant, each taking as many grains as its grain weighs, rounded up; a booster that is
 *     burning or still holds propellant takes none, nor one the held stack cannot fill.</li>
 * <li>A reed set replaces a valved pulsejet's reeds (one set each) once they have used a tenth of their life or more;
 *     the reeds' wear is the part's state (PulsejetDesign.STATE_CYCLES), set back to new (AeroCore's VehicleState.setInternal).</li>
 * <li>A part cast off (AeroCore's PartVehicle.Jettisoned, a booster that burnt out) drops as its part item where it left,
 *     moving as it did (at most 2 blocks a tick).</li>
 * </ul>
 */
public final class Servicing {
    private Servicing() {}

    /** The share of reed life used from which a set is fitted. */
    public static final double REEDS_WORN = 0.1;
    private static final double MAX_DROP_SPEED = 2;

    static void interact(PlayerInteractEvent.EntityInteract e) {
        if (!(e.getTarget() instanceof PartVehicle v)) { return; }
        ItemStack held = e.getItemStack();
        String propellant = held.is(HdfvFuel.KNSB_GRAIN.get()) ? "knsb" : held.is(HdfvFuel.APCP_GRAIN.get()) ? "apcp" : null;
        boolean reeds = held.is(HdfvFuel.REED_SET.get());
        if (propellant == null && !reeds) { return; }
        Player player = e.getEntity();
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.sidedSuccess(player.level().isClientSide));
        if (player.level().isClientSide) { return; }
        player.displayClientMessage(propellant != null ? load(v, propellant, held, player.isCreative()) : reed(v, held, player.isCreative()), true);
    }

    /** Loads a vehicle's empty boosters of a propellant from a held stack of grains; what happened. */
    public static Component load(PartVehicle v, String propellant, ItemStack held, boolean creative) {
        Vehicle vehicle = v.vehicle();
        VehicleState st = v.vehicleState();
        if (vehicle == null || st == null) { return Component.translatable("hdfv.service.no_booster"); }
        int loaded = 0, short_ = 0;
        double kg = 0;
        for (int k = 0; k < vehicle.tanks().size(); k++) {
            Vehicle.Tank t = vehicle.tanks().get(k);
            Vehicle.Installed part = vehicle.installed().get(t.part());
            if (!t.tank().own() || !(part.part().design() instanceof SolidRocketDesign s) || !s.grain().propellant().equals(propellant)) { continue; }
            if (v.tankKg(k) > 0.01 * s.grain().propellantKg() || st.internal(part.id(), SolidRocketDesign.STATE_BURNING) > 0.5) { continue; }
            int items = s.grainItems();
            if (!creative && held.getCount() < items) { short_ = Math.max(short_, items); continue; }
            if (!creative) { held.shrink(items); }
            v.setTank(k, propellant, s.grain().propellantKg(), s.grain().propellantModel().densityKgPerM3());
            loaded++;
            kg += s.grain().propellantKg();
        }
        if (loaded > 0) { return Component.translatable("hdfv.service.loaded", loaded, Math.round(kg * 10) / 10.0); }
        if (short_ > 0) { return Component.translatable("hdfv.service.short", short_); }
        return Component.translatable("hdfv.service.no_booster");
    }

    /** Fits new reeds to a vehicle's worn pulsejets from a held stack of reed sets; what happened. */
    public static Component reed(PartVehicle v, ItemStack held, boolean creative) {
        Vehicle vehicle = v.vehicle();
        VehicleState st = v.vehicleState();
        int fitted = 0;
        if (vehicle != null && st != null) {
            for (Vehicle.Installed p : vehicle.installed()) {
                if (!(p.part().design() instanceof PulsejetDesign j) || j.valve().valveless()) { continue; }
                double life = j.valve().reedLifeCycles();
                if (!(st.internal(p.id(), PulsejetDesign.STATE_CYCLES) >= REEDS_WORN * life) || (!creative && held.isEmpty())) { continue; }
                if (!creative) { held.shrink(1); }
                st.setInternal(p.id(), PulsejetDesign.STATE_CYCLES, 0);
                fitted++;
            }
        }
        return fitted > 0 ? Component.translatable("hdfv.service.reeds", fitted) : Component.translatable("hdfv.service.no_reeds");
    }

    /** Parts cast off drop as their part items. */
    static void jettisoned(PartVehicle.Jettisoned e) {
        Level level = e.vehicle().level();
        if (level.isClientSide) { return; }
        for (PartVehicle.Jettisoned.Dropped d : e.parts()) {
            PartDesign design = d.entry().design();
            ItemEntity item = new ItemEntity(level, d.position().x(), d.position().y(), d.position().z(), Workpieces.part(design));
            Vec3d per = d.velocity().scale(1 / 20.0);
            double speed = per.length();
            if (speed > MAX_DROP_SPEED) { per = per.scale(MAX_DROP_SPEED / speed); }
            item.setDeltaMovement(per.x(), per.y(), per.z());
            level.addFreshEntity(item);
        }
    }
}
