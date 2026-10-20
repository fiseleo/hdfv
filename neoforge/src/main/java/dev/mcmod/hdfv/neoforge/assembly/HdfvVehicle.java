package dev.mcmod.hdfv.neoforge.assembly;

import dev.mcmod.aerocore.neoforge.vehicle.PartVehicle;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * A vehicle built in an assembly room (0.7, the user: "載具是組裝才能開"): until its assembly is done it stands where it is
 * being built, is not simulated, carries nobody and takes no controls. Vehicles spawned by command are AeroCore's
 * PartVehicle and run at once (for admins and tests).
 */
public class HdfvVehicle extends PartVehicle {
    private static final EntityDataAccessor<Float> BUILT = SynchedEntityData.defineId(HdfvVehicle.class, EntityDataSerializers.FLOAT);
    /** The controller of the room building it (null once launched or when unknown). */
    private BlockPos room;

    public HdfvVehicle(EntityType<? extends HdfvVehicle> type, Level level) { super(type, level); }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(BUILT, 0f);
    }

    /** How much of its assembly is done, 0..1. */
    public float built() { return entityData.get(BUILT); }
    public boolean assembled() { return built() >= 1; }
    public void setBuilt(float share) {
        boolean was = assembled();
        entityData.set(BUILT, Math.clamp(share, 0f, 1f));
        if (!was && assembled()) { warmUp(); }
    }
    public BlockPos room() { return room; }
    public void setRoom(BlockPos pos) { room = pos; }

    @Override public boolean beginTick(ServerLevel server) { return assembled() && super.beginTick(server); }
    /** Its parts' tables wait until it is assembled (hours of building: no use holding the table builder meanwhile). */
    @Override protected boolean warmsUp() { return assembled(); }
    @Override public boolean canControl(Player player) { return assembled() && super.canControl(player); }
    @Override protected boolean canAddPassenger(Entity passenger) { return assembled() && super.canAddPassenger(passenger); }

    @Override public InteractionResult interact(Player player, InteractionHand hand) {
        if (assembled() || player.isSecondaryUseActive()) { return super.interact(player, hand); }
        if (hand == InteractionHand.MAIN_HAND && !level().isClientSide) {
            player.displayClientMessage(Component.translatable("hdfv.vehicle.unassembled", Math.round(100 * built())), true);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("Built", built());
        if (room != null) { tag.put("Room", NbtUtils.writeBlockPos(room)); }
    }

    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setBuilt(tag.contains("Built") ? tag.getFloat("Built") : 1f);
        room = NbtUtils.readBlockPos(tag, "Room").orElse(null);
    }
}
