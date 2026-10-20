package dev.mcmod.hdfv.neoforge.airport;

import dev.mcmod.aerocore.neoforge.vehicle.VehicleTracks;
import dev.mcmod.hdfv.airport.AircraftKind;
import dev.mcmod.hdfv.airport.Atc;
import dev.mcmod.hdfv.airport.Runway;
import dev.mcmod.hdfv.structure.Structures;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** What a tower's screen shows (0.20), as one tag: state, power, ATIS, lights, runways, strips, radar returns, conflicts, the log. */
final class TowerView {
    private TowerView() {}

    static CompoundTag of(ServerLevel server, TowerBlockEntity t) {
        CompoundTag v = new CompoundTag();
        v.putBoolean("Formed", t.formed());
        v.putBoolean("Powered", t.powered());
        v.putBoolean("Manned", t.manned());
        if (t.holder() != null) {
            v.putUUID("Holder", t.holder());
            ServerPlayer p = server.getServer().getPlayerList().getPlayer(t.holder());
            v.putString("HolderName", p == null ? "?" : p.getGameProfile().getName());
        }
        Structures.TowerHeights h = t.heights();
        if (h != null) { v.putDouble("EyeM", h.eyeM()); v.putDouble("AntennaM", h.antennaM()); }
        v.putBoolean("Radar", t.hasRadar());
        v.putDouble("ZoneM", t.zoneM());
        v.putDouble("PowerW", t.lastPowerW());
        v.putInt("Energy", t.energy().getEnergyStored());
        v.putInt("EnergyMax", t.energy().getMaxEnergyStored());
        ListTag runways = new ListTag();
        for (Runway r : t.runways(server)) { runways.add(StringTag.valueOf(r.name())); }
        v.put("Runways", runways);
        if (t.inUse() != null) { v.putString("InUse", t.inUse().designator()); }
        if (t.atis() != null) { v.put("Atis", AirportNetwork.atis(t.atis())); }
        int[] steps = new int[6];
        int auto = 0;
        for (int i = 0; i < steps.length; i++) { steps[i] = t.step(i); if (t.auto(i)) { auto |= 1 << i; } }
        v.putIntArray("Steps", steps);
        v.putInt("Auto", auto);
        double[] lit = t.intensities();
        int[] litPermille = new int[lit.length];
        for (int i = 0; i < lit.length; i++) { litPermille[i] = (int) Math.round(lit[i] * 1000); }
        v.putIntArray("Lit", litPermille);
        v.putInt("Fixtures", t.fixtures(server).size());
        ListTag flights = new ListTag();
        for (Atc.Flight f : t.result().flights()) {
            if (f.kind() == AircraftKind.GROUND) { continue; }
            CompoundTag c = new CompoundTag();
            c.putUUID("Id", f.id());
            c.putString("Name", f.name());
            c.putString("Kind", f.kind().name());
            c.putString("Phase", f.phase().name());
            c.putString("Intent", f.intent().name());
            c.putString("Instruction", f.instruction().name());
            c.putInt("Sequence", f.sequence());
            c.putDouble("Eta", Double.isFinite(f.etaS()) ? f.etaS() : -1);
            ListTag w = new ListTag();
            for (Atc.Warning x : f.warnings()) { w.add(StringTag.valueOf(x.name())); }
            c.put("Warnings", w);
            flights.add(c);
        }
        v.put("Flights", flights);
        ListTag tracks = new ListTag();
        for (VehicleTracks.Track r : t.tracks()) {
            CompoundTag c = new CompoundTag();
            c.putUUID("Id", r.id());
            c.putString("Name", r.blueprint().name());
            c.putDouble("Dx", r.position().x() - t.getBlockPos().getX());
            c.putDouble("Dz", r.position().z() - t.getBlockPos().getZ());
            c.putDouble("Msl", r.position().y() - server.getSeaLevel());
            c.putDouble("Agl", Double.isNaN(r.heightAboveGroundM()) ? -1 : r.heightAboveGroundM());
            c.putDouble("Gs", r.groundSpeedMps());
            c.putDouble("Vs", r.verticalSpeedMps());
            c.putDouble("Track", r.trackDeg());
            tracks.add(c);
        }
        v.put("Tracks", tracks);
        ListTag conflicts = new ListTag();
        for (Atc.Conflict c : t.result().conflicts()) {
            CompoundTag x = new CompoundTag();
            x.putUUID("A", c.a());
            x.putUUID("B", c.b());
            x.putString("Reason", c.reason());
            conflicts.add(x);
        }
        v.put("Conflicts", conflicts);
        ListTag log = new ListTag();
        for (String l : t.log()) { log.add(StringTag.valueOf(l)); }
        v.put("Log", log);
        v.putLong("Time", server.getGameTime());
        return v;
    }
}
