package dev.mcmod.hdfv.neoforge.client.sound;

import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.acoustics.Surface;
import dev.mcmod.aerocore.neoforge.probe.BlockMaterials;
import dev.mcmod.aerocore.neoforge.vehicle.PartVehicle;
import dev.mcmod.hdfv.sound.SoundInputs;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A vehicle's sound inputs on a client (0.19): its synced shown values, rotor speeds and controls, its synced velocity,
 * and the ground: the first solid block under it (its material by AeroCore's block classes) and whether the road is
 * wet (rain falling there, or water). All wheels share the vehicle's ground (a rule: per-wheel blocks would cost a
 * lookup per wheel per tick for a difference only heard at a surface's edge).
 */
final class ClientInputs implements SoundInputs {
    private PartVehicle v;
    private Surface ground = Surface.ROCK;
    private boolean wet, inWater, raining;
    private double time;

    ClientInputs at(PartVehicle vehicle, ClientLevel level) {
        this.v = vehicle;
        ground = Surface.OPEN;
        wet = false;
        time = level.getGameTime() * 0.05;
        BlockPos at = BlockPos.containing(vehicle.getX(), vehicle.getY(), vehicle.getZ());
        inWater = level.getFluidState(at).is(FluidTags.WATER) || level.getFluidState(at.below()).is(FluidTags.WATER);
        raining = level.isRainingAt(at.above(2));
        BlockPos p = BlockPos.containing(vehicle.getX(), vehicle.getY() + 0.5, vehicle.getZ());
        for (int k = 0; k < 6; k++, p = p.below()) {
            BlockState s = level.getBlockState(p);
            if (s.getFluidState().is(FluidTags.WATER)) { wet = true; ground = Surface.WATER; break; }
            if (!s.isAir() && !s.getCollisionShape(level, p).isEmpty()) {
                ground = Surface.of(BlockMaterials.of(s));
                wet = level.isRainingAt(p.above()) || level.getBlockState(p.above()).getFluidState().is(FluidTags.WATER);
                break;
            }
        }
        return this;
    }

    @Override public double shown(String partId, int index) { return v.shown(partId, index, 1f); }
    @Override public double rotorSpeed(int rotor) { return v.rotorSpeed(rotor); }
    @Override public double input(String partId, String role) { return v.input(partId, role); }
    @Override public double control(String channel) { return v.controls().getOrDefault(channel, 0.0); }
    @Override public Vec3d velocity() { return v.velocity(); }
    @Override public Surface surface(String partId, int wheel) { return ground; }
    @Override public boolean wet() { return wet; }
    @Override public boolean inWater() { return inWater; }
    @Override public boolean raining() { return raining; }
    @Override public double time() { return time; }
    @Override public int blinkSeed() { return v.getUUID().hashCode(); }
    @Override public Scrape scrape() {
        var s = v.scrape();
        if (s == null) { return null; }
        var m = dev.mcmod.aerocore.neoforge.vehicle.VehicleContacts.material(s.material());
        return new Scrape(s.powerW(), s.slideMps(), m == null ? Surface.STEEL : Surface.of(m));
    }
}
