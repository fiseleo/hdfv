package dev.mcmod.hdfv.neoforge.tutorial;

import dev.mcmod.aerocore.neoforge.probe.BodyWorld;
import dev.mcmod.aerocore.neoforge.probe.CoupledBody;
import dev.mcmod.aerocore.neoforge.vehicle.PartVehicle;
import dev.mcmod.aerocore.neoforge.vehicle.VehicleTracks;
import dev.mcmod.hdfv.assembly.Room;
import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.neoforge.assembly.HdfvVehicle;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * The tutorial's driving milestones (0.24): once a second, every assembled vehicle with a player at the controls
 * counts the metres it went for that player (TutorialData, by kind: car, aircraft, boat) and reports driven (metres so
 * far), speed (km/h), height (m above the ground, aircraft) and mach. Vehicles spawned by command do not count.
 */
@EventBusSubscriber(modid = Hdfv.MOD_ID)
public final class DriveTracker {
    private DriveTracker() {}

    private static final int EVERY = 20;
    /** The speed of sound at sea level in the standard atmosphere (m/s): Mach is reported against it. */
    private static final double SOUND_MPS = 340.3;

    @SubscribeEvent static void tick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.getGameTime() % EVERY != 0) { return; }
        for (CoupledBody b : BodyWorld.bodies(level)) {
            if (!(b instanceof HdfvVehicle v) || v.isRemoved() || !v.assembled() || v.blueprint() == null) { continue; }
            if (!(v.getFirstPassenger() instanceof ServerPlayer driver)) { continue; }
            drove(driver, v);
        }
    }

    private static void drove(ServerPlayer driver, PartVehicle v) {
        String kind = HdfvTutorial.kind(Room.of(v.blueprint()));
        double speed = v.velocity().length();
        if (speed < 0.5) { return; }
        TutorialData data = driver.getData(HdfvTutorial.DATA).drove(kind, speed * EVERY / 20.0);
        driver.setData(HdfvTutorial.DATA, data);
        Milestones.reach(driver, "driven", kind, data.metres().getOrDefault(kind, 0.0));
        Milestones.reach(driver, "speed", kind, speed * 3.6);
        Milestones.reach(driver, "mach", kind, speed / SOUND_MPS);
        if (kind.equals("aircraft")) {
            double height = VehicleTracks.of(v).heightAboveGroundM();
            if (Double.isFinite(height)) { Milestones.reach(driver, "height", kind, height); }
        }
    }
}
