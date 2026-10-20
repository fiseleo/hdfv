package dev.mcmod.hdfv.neoforge.truck;

import dev.mcmod.aerocore.api.vehicle.Coupling;
import dev.mcmod.aerocore.api.vehicle.VehicleBlueprint;
import dev.mcmod.aerocore.neoforge.probe.BodyWorld;
import dev.mcmod.aerocore.neoforge.probe.CoupledBody;
import dev.mcmod.aerocore.neoforge.vehicle.Couplings;
import dev.mcmod.aerocore.neoforge.vehicle.PartVehicle;
import dev.mcmod.hdfv.drive.CarKit;
import dev.mcmod.hdfv.drive.Hitch;
import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.part.truck.LandingLegsDesign;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Coupling and uncoupling semi-trailers in the world (0.18), for any vehicle with a fifth wheel (CarKit.fifthWheel):
 * <ul>
 *   <li>Each new count on the tractor's "couple" channel (the driver's coupling key, DriverInputs) acts once. Not
 *   coupled: the nearest trailer within SEARCH_M whose kingpin Hitch.check finds in the jaw is coupled (AeroCore
 *   PartVehicle.couple with Hitch.coupling), its legs drawn up ("landing_gear" 1); none: the driver hears why of the
 *   nearest. Coupled: the trailer's legs come down ("landing_gear" 0), and once they carry a quarter of its kingpin's
 *   load (or their cranking time and a second have passed) it is let go with its parking brake on. Pressed again
 *   while they come down, the legs go back up and it stays coupled.</li>
 *   <li>The tractor's "coupling" channel says where it is: 0 none, 1 coupled, 2 letting go (saved and synced with its
 *   controls; the dashboard shows it). A link ended any other way (a fault, a trailer removed) puts it back to 0 and
 *   the trailer down on its legs with its brake on.</li>
 *   <li>coupleWhenReady couples a pair as soon as both have built their tables (the development command's semi).</li>
 * </ul>
 */
@EventBusSubscriber(modid = Hdfv.MOD_ID)
public final class Hitching {
    private Hitching() {}

    /** How far from the jaw a trailer is looked for, m. */
    public static final double SEARCH_M = 20;
    /** Ticks waited past a leg's cranking time before letting go anyway. */
    public static final int LEGS_EXTRA_TICKS = 20;
    /** Ticks a pair waits to be coupled when ready (a busy world builds a new vehicle's tables in a minute). */
    public static final int READY_TICKS = 2400;

    private static final Map<PartVehicle, Double> SEEN = new WeakHashMap<>();
    private static final Map<PartVehicle, Long> LOWERING = new WeakHashMap<>();
    private static final Map<PartVehicle, PartVehicle> PENDING = new WeakHashMap<>();
    private static final Map<PartVehicle, Long> PENDING_UNTIL = new WeakHashMap<>();
    private static final Map<VehicleBlueprint, CarKit> KITS = new WeakHashMap<>();

    static CarKit kit(PartVehicle v) {
        VehicleBlueprint b = v.blueprint();
        if (b == null) { return CarKit.NONE; }
        synchronized (KITS) { return KITS.computeIfAbsent(b, CarKit::of); }
    }

    /** Couples a trailer to a tractor once both are ready (their tables built), within READY_TICKS. */
    public static void coupleWhenReady(PartVehicle tractor, PartVehicle trailer) {
        PENDING.put(tractor, trailer);
        PENDING_UNTIL.put(tractor, tractor.level().getGameTime() + READY_TICKS);
    }

    @SubscribeEvent public static void tick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) { return; }
        for (CoupledBody b : BodyWorld.bodies(level)) {
            if (!(b instanceof PartVehicle v) || v.isRemoved() || v.vehicle() == null || !kit(v).fifthWheel()) { continue; }
            double count = v.controls().getOrDefault("couple", 0.0);
            Double seen = SEEN.put(v, count);
            if (seen != null && seen != count) { press(level, v); }
            lowering(level, v);
            PartVehicle waiting = PENDING.get(v);
            if (waiting != null) {
                if (waiting.isRemoved() || level.getGameTime() > PENDING_UNTIL.getOrDefault(v, 0L)) { PENDING.remove(v); }
                else if (v.ready() && waiting.ready() && couple(v, waiting, false) == Couplings.Result.OK) { PENDING.remove(v); }
            }
        }
    }

    /** A new press of the coupling key on a tractor. */
    static void press(ServerLevel level, PartVehicle tractor) {
        if (LOWERING.containsKey(tractor)) {
            // pressed again while the legs come down: keep the trailer
            LOWERING.remove(tractor);
            for (PartVehicle f : tractor.followers()) { f.setControl("landing_gear", 1); }
            tractor.setControl("coupling", 1);
            say(tractor, "hdfv.hitch.kept");
            return;
        }
        if (!tractor.followers().isEmpty()) {
            for (PartVehicle f : tractor.followers()) { f.setControl("landing_gear", 0); }
            LOWERING.put(tractor, level.getGameTime());
            tractor.setControl("coupling", 2);
            say(tractor, "hdfv.hitch.lowering");
            return;
        }
        Hitch.Jaw jaw = Hitch.jaw(tractor.blueprint(), tractor.vehicle());
        if (jaw == null || tractor.vehicleState() == null) { say(tractor, "hdfv.hitch.no_fifth_wheel"); return; }
        var at = tractor.vehicleState().body().worldPoint(tractor.vehicle().properties(), jaw.point());
        PartVehicle nearest = null;
        Hitch.Result why = null;
        double best = Double.POSITIVE_INFINITY;
        AABB box = new AABB(at.x() - SEARCH_M, at.y() - SEARCH_M, at.z() - SEARCH_M, at.x() + SEARCH_M, at.y() + SEARCH_M, at.z() + SEARCH_M);
        for (PartVehicle r : level.getEntitiesOfClass(PartVehicle.class, box)) {
            if (r == tractor || r.vehicle() == null || r.vehicleState() == null || r.coupling() != null) { continue; }
            Hitch.Kingpin pin = Hitch.kingpin(r.blueprint(), r.vehicle());
            if (pin == null) { continue; }
            Hitch.Result result = Hitch.check(jaw, pin, Hitch.stance(tractor.vehicle(), tractor.vehicleState().body(), jaw, r.vehicle(), r.vehicleState().body(), pin));
            double d = r.vehicleState().body().worldPoint(r.vehicle().properties(), pin.point()).subtract(at).length();
            if (result == Hitch.Result.OK) {
                Couplings.Result done = couple(tractor, r, true);
                if (done != Couplings.Result.OK) { say(tractor, "hdfv.hitch.refused", done.name()); }
                return;
            }
            if (d < best) { best = d; nearest = r; why = result; }
        }
        if (nearest == null) { say(tractor, "hdfv.hitch.none"); }
        else { say(tractor, "hdfv.hitch." + why.name().toLowerCase(java.util.Locale.ROOT)); }
    }

    /** Couples a trailer to a tractor (its legs up, the tractor's "coupling" 1); said to the driver when asked. */
    static Couplings.Result couple(PartVehicle tractor, PartVehicle trailer, boolean tell) {
        Hitch.Jaw jaw = Hitch.jaw(tractor.blueprint(), tractor.vehicle());
        Hitch.Kingpin pin = trailer.vehicle() == null ? null : Hitch.kingpin(trailer.blueprint(), trailer.vehicle());
        if (jaw == null || pin == null) { return Couplings.Result.NOT_READY; }
        Coupling c = Hitch.coupling(tractor.blueprint(), tractor.vehicle(), jaw, trailer.blueprint(), trailer.vehicle(), pin);
        Couplings.Result r = tractor.couple(trailer, c);
        if (r != Couplings.Result.OK) { return r; }
        trailer.setControl("landing_gear", 1);
        trailer.setControl("handbrake", 0);
        tractor.setControl("coupling", 1);
        if (tell) { say(tractor, "hdfv.hitch.coupled"); }
        return r;
    }

    /** A tractor letting its trailer down: let go once the legs carry it, or their time is up. */
    static void lowering(ServerLevel level, PartVehicle tractor) {
        Long started = LOWERING.get(tractor);
        if (started == null) { return; }
        var followers = tractor.followers();
        if (followers.isEmpty()) { LOWERING.remove(tractor); tractor.setControl("coupling", 0); return; }
        for (PartVehicle f : followers) {
            double carried = 0, crank = 0;
            for (var i : f.vehicle().installed()) {
                if (!(i.part().design() instanceof LandingLegsDesign legs)) { continue; }
                crank = Math.max(crank, legs.crankTimeS());
                var d = f.vehicleState() == null ? null : f.vehicleState().diagnostics().get(i.id());
                if (d != null) { carried += d.getOrDefault("wheelLoadN", 0.0); }
            }
            Hitch.Kingpin pin = Hitch.kingpin(f.blueprint(), f.vehicle());
            double need = pin == null ? 0 : 0.25 * pin.staticLoadKg() * 9.80665;
            long elapsed = level.getGameTime() - started;
            if (carried >= need && need > 0 || elapsed > crank * 20 + LEGS_EXTRA_TICKS) {
                f.uncouple();
                f.setControl("handbrake", 1);
            }
        }
        if (tractor.followers().isEmpty()) {
            LOWERING.remove(tractor);
            tractor.setControl("coupling", 0);
            say(tractor, "hdfv.hitch.uncoupled");
        }
    }

    /** A link ended some other way: the tractor shows none, the trailer stands on its legs with its brake on. */
    @SubscribeEvent public static void uncoupled(Couplings.Uncoupled event) {
        if (event.reason() == Couplings.Reason.COMMAND) { return; }
        if (event.leader() != null && kit(event.leader()).fifthWheel()) {
            LOWERING.remove(event.leader());
            event.leader().setControl("coupling", event.leader().followers().isEmpty() ? 0 : 1);
        }
        if (event.follower() != null) {
            event.follower().setControl("landing_gear", 0);
            event.follower().setControl("handbrake", 1);
        }
    }

    private static void say(PartVehicle tractor, String key, Object... args) {
        if (tractor.getFirstPassenger() instanceof ServerPlayer p) { p.displayClientMessage(Component.translatable(key, args), true); }
    }
}
