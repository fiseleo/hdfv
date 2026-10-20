package dev.mcmod.hdfv.neoforge.fuel;

import dev.mcmod.aerocore.api.vehicle.Vehicle;
import dev.mcmod.aerocore.neoforge.vehicle.PartVehicle;
import dev.mcmod.hdfv.fluid.FuelGrades;
import dev.mcmod.hdfv.fluid.HdfvFluids;
import dev.mcmod.hdfv.neoforge.fluid.HdfvFluidRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Putting fuel in a vehicle (0.8): into its tanks that are empty or hold the same fuel, up to their brims at the fuel's
 * density. Refused when it is not a fuel, when the vehicle has no tank, and when it has engines and none of them burns
 * it (the user: 拒絕錯油; a vehicle with no engines takes any fuel, as cargo). The dispenser and cans poured into a
 * vehicle both go through here. 0.10: hydrogen goes only into its kind of tank (liquid into a cryogenic tank, 700 bar
 * into a high-pressure cylinder) and nothing into a part's own store (a booster's grain); core
 * fluid.HydrogenStorage.tankTakes.
 */
public final class Fuelling {
    private Fuelling() {}

    /** The fluids a vehicle's engines burn (empty: it has none). */
    public static Set<String> takes(PartVehicle v) {
        Set<String> out = new LinkedHashSet<>();
        Vehicle vehicle = v.vehicle();
        if (vehicle == null) { return out; }
        for (Vehicle.Installed p : vehicle.installed()) {
            Set<String> t = FuelGrades.takenBy(p.part().design());
            if (t != null) { out.addAll(t); }
        }
        return out;
    }

    /** A fuel's density, kg/m^3. */
    public static double density(String fluid) { HdfvFluids.Spec s = HdfvFluids.of(fluid); return s == null ? 1000 : s.densityKgPerM3(); }

    public static MutableComponent name(String fluid) { return Component.translatable("fluid_type.hdfv." + fluid); }

    /** Whether tank k of a vehicle takes a fluid (its kind; never a part's own store). */
    static boolean accepts(PartVehicle v, int k, String fluid) {
        Vehicle vehicle = v.vehicle();
        if (vehicle == null || k >= vehicle.tanks().size()) { return false; }
        Vehicle.Tank t = vehicle.tanks().get(k);
        return dev.mcmod.hdfv.fluid.HydrogenStorage.tankTakes(vehicle.installed().get(t.part()).part().design(), t.tank().own(), fluid);
    }

    /** Why a vehicle will not take a fluid now (null: it will). */
    public static Component refusal(PartVehicle v, String fluid) {
        if (fluid == null || FuelGrades.of(fluid) == null) { return Component.translatable("hdfv.fuel.not_fuel", fluid == null ? Component.literal("?") : name(fluid)); }
        if (v.tankCount() == 0) { return Component.translatable("hdfv.fuel.no_tank"); }
        Set<String> takes = takes(v);
        if (!takes.isEmpty() && !takes.contains(fluid)) {
            MutableComponent list = Component.empty();
            int i = 0;
            for (String t : takes) { if (i++ > 0) { list.append(", "); } list.append(name(t)); }
            return Component.translatable("hdfv.fuel.wrong", name(fluid), list);
        }
        boolean any = false;
        for (int k = 0; k < v.tankCount(); k++) { any |= accepts(v, k, fluid); }
        if (!any) { return Component.translatable("hdfv.fuel.wrong_tank", name(fluid)); }
        if (room(v, fluid) < 1) {
            for (int k = 0; k < v.tankCount(); k++) {
                String now = v.tankFuel(k);
                if (!now.isEmpty() && !now.equals(fluid)) { return Component.translatable("hdfv.fuel.other", name(now)); }
            }
            return Component.translatable("hdfv.fuel.full");
        }
        return null;
    }

    /** Whole litres of a fuel its tanks still take. */
    public static int room(PartVehicle v, String fluid) {
        double rho = density(fluid), litres = 0;
        for (int k = 0; k < v.tankCount(); k++) {
            String now = v.tankFuel(k);
            if (!now.isEmpty() && !now.equals(fluid) || !accepts(v, k, fluid)) { continue; }
            litres += Math.floor(Math.max(0, v.tankCapacityM3(k) * rho - v.tankKg(k)) / rho * 1000 + 1e-9);
        }
        return (int) litres;
    }

    /** Puts up to some litres of a fuel in (server; the caller checked refusal), tank by tank; the litres it took. */
    public static int fill(PartVehicle v, String fluid, int litres) {
        double rho = density(fluid);
        int left = litres;
        for (int k = 0; k < v.tankCount() && left > 0; k++) {
            String now = v.tankFuel(k);
            if (!now.isEmpty() && !now.equals(fluid) || !accepts(v, k, fluid)) { continue; }
            double kg = v.tankKg(k);
            int can = (int) Math.floor(Math.max(0, v.tankCapacityM3(k) * rho - kg) / rho * 1000 + 1e-9);
            int put = Math.min(can, left);
            if (put > 0) {
                v.setTank(k, fluid, kg + put * rho / 1000, rho);
                left -= put;
            }
        }
        return litres - left;
    }

    /** The fuel aboard: litres in all tanks (each at its own fuel's density). */
    public static double litres(PartVehicle v) {
        double l = 0;
        for (int k = 0; k < v.tankCount(); k++) {
            String f = v.tankFuel(k);
            if (!f.isEmpty()) { l += v.tankKg(k) / density(f) * 1000; }
        }
        return l;
    }

    /** What its tanks hold when full, litres. */
    public static double capacityLitres(PartVehicle v) {
        double l = 0;
        for (int k = 0; k < v.tankCount(); k++) { l += v.tankCapacityM3(k) * 1000; }
        return l;
    }

    /** A can or bucket used on a vehicle pours its fuel in (a bucket only whole). */
    static void interact(PlayerInteractEvent.EntityInteract e) {
        if (!(e.getTarget() instanceof PartVehicle v)) { return; }
        ItemStack held = e.getItemStack();
        if (held.isEmpty()) { return; }
        Optional<IFluidHandlerItem> handler = FluidUtil.getFluidHandler(held.copyWithCount(1));
        if (handler.isEmpty()) { return; }
        FluidStack in = handler.get().drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.SIMULATE);
        if (in.isEmpty()) { return; }
        Player player = e.getEntity();
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.sidedSuccess(player.level().isClientSide));
        if (player.level().isClientSide) { return; }
        String fluid = HdfvFluidRegistry.id(in.getFluid());
        Component no = refusal(v, fluid);
        if (no != null) { player.displayClientMessage(no, true); return; }
        IFluidHandlerItem h = handler.get();
        FluidStack out = h.drain(Math.min(in.getAmount(), room(v, fluid)), IFluidHandler.FluidAction.SIMULATE);
        if (out.isEmpty()) { player.displayClientMessage(Component.translatable("hdfv.fuel.full"), true); return; }
        int put = fill(v, fluid, out.getAmount());
        h.drain(put, IFluidHandler.FluidAction.EXECUTE);
        give(player, e.getHand(), held, h.getContainer());
        player.displayClientMessage(Component.translatable("hdfv.fuel.poured", put, name(fluid), Math.round(litres(v)), Math.round(capacityLitres(v))), true);
    }

    /** One of the held stack becomes the emptied container. */
    private static void give(Player player, InteractionHand hand, ItemStack held, ItemStack container) {
        if (player.getAbilities().instabuild && !held.is(container.getItem())) { return; }   // creative keeps its full bucket
        if (held.getCount() == 1) { player.setItemInHand(hand, container); return; }
        held.shrink(1);
        if (!player.getInventory().add(container)) { player.drop(container, false); }
    }
}
