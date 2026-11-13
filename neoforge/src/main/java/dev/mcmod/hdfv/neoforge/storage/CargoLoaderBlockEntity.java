package dev.mcmod.hdfv.neoforge.storage;

import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.neoforge.vehicle.PartVehicle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.InvWrapper;

/**
 * 0.25: a cargo loader (placed beside where vehicles park, its front toward them): on every side an item handler that is
 * the hold, of the vehicles standing still (under STOPPED_MPS) near its front, whose centre is nearest the front face's
 * middle and within REACH_M of it; nothing (no slots) when there is none. So a hopper, a Create funnel or an hdfv room
 * fills or empties a parked lorry's bed, and stops when it drives off. Looked up again each game tick it is asked.
 */
public final class CargoLoaderBlockEntity extends BlockEntity {
    public static final double REACH_M = 4, STOPPED_MPS = 0.3;

    private final IItemHandler handler = new Handler();
    private long lookedAt = Long.MIN_VALUE;
    private VehicleCargo.Hold target;
    private IItemHandler targetHandler = EMPTY;

    public CargoLoaderBlockEntity(BlockPos pos, BlockState state) { super(HdfvCargo.LOADER_ENTITY.get(), pos, state); }

    /** Its items for hoppers, pipes and rooms (every side). */
    public IItemHandler handler() { return handler; }

    /** The middle of its front face. */
    public Vec3d face() {
        Direction f = getBlockState().hasProperty(CargoLoaderBlock.FACING) ? getBlockState().getValue(CargoLoaderBlock.FACING) : Direction.NORTH;
        return new Vec3d(worldPosition.getX() + 0.5 + 0.5 * f.getStepX(), worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5 + 0.5 * f.getStepZ());
    }

    /** The hold it serves now (null: none), looked up once a game tick. */
    public VehicleCargo.Hold target() {
        if (level == null || level.isClientSide) { return null; }
        long now = level.getGameTime();
        if (now != lookedAt || target != null && !target.live()) {
            lookedAt = now;
            target = find();
            targetHandler = target == null ? EMPTY : new InvWrapper(target);
        }
        return target;
    }

    private VehicleCargo.Hold find() {
        Vec3d face = face();
        AABB box = new AABB(face.x(), face.y(), face.z(), face.x(), face.y(), face.z()).inflate(24);
        VehicleCargo.Hold best = null;
        double nearest = REACH_M * REACH_M;
        for (PartVehicle v : level.getEntitiesOfClass(PartVehicle.class, box, VehicleCargo::usable)) {
            if (v.holdCount() == 0 || !(v.velocity().length() < STOPPED_MPS)) { continue; }
            for (int k = 0; k < v.holdCount(); k++) {
                Vec3d at = VehicleCargo.point(v, k);
                double d = at.subtract(face).lengthSquared();
                if (d <= nearest) {
                    VehicleCargo.Hold h = VehicleCargo.hold(v, k);
                    if (h != null) { nearest = d; best = h; }
                }
            }
        }
        return best;
    }

    private IItemHandler current() { target(); return targetHandler; }

    /** Every call goes to the hold served now. */
    private final class Handler implements IItemHandler {
        @Override public int getSlots() { return current().getSlots(); }
        @Override public ItemStack getStackInSlot(int slot) { return current().getStackInSlot(slot); }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { return current().insertItem(slot, stack, simulate); }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) { return current().extractItem(slot, amount, simulate); }
        @Override public int getSlotLimit(int slot) { return current().getSlotLimit(slot); }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return current().isItemValid(slot, stack); }
    }

    /** No slots. */
    static final IItemHandler EMPTY = new IItemHandler() {
        @Override public int getSlots() { return 0; }
        @Override public ItemStack getStackInSlot(int slot) { return ItemStack.EMPTY; }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { return stack; }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) { return ItemStack.EMPTY; }
        @Override public int getSlotLimit(int slot) { return 0; }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return false; }
    };
}
