package dev.mcmod.hdfv.neoforge.fuel;

import dev.mcmod.aerocore.neoforge.vehicle.PartVehicle;
import dev.mcmod.hdfv.fluid.FuelGrades;
import dev.mcmod.hdfv.neoforge.fluid.HdfvFluidRegistry;
import dev.mcmod.hdfv.neoforge.petroleum.Configurable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import java.util.Comparator;
import java.util.Locale;
import java.util.UUID;

/**
 * A fuel dispenser (0.8, 加油機): a 2 m^3 tank of one fuel, filled by pipe or can, that fuels the vehicle parked by it
 * at 50 litres a minute (real time, as a forecourt pump), refusing a fuel the vehicle's engines do not burn (Fuelling).
 * Used: starts on the nearest vehicle within 6 m, or stops; it stops by itself when the vehicle is full or drives
 * off, or when it runs dry. Its screen (0.9) starts and stops it and sets how much it puts in: the vehicle full, or
 * 10, 20 or 50 litres.
 */
public final class FuelDispenserBlockEntity extends BlockEntity implements Configurable {
    public enum State { IDLE, FUELLING, FULL, EMPTY, GONE, REFUSED, DONE }

    public static final int LITRES = 2000;
    /** The amounts its screen offers, litres (0: until the vehicle is full). */
    public static final int[] AMOUNTS = {0, 10, 20, 50};
    public static final double LITRES_PER_MINUTE = 50, REACH_M = 6;
    final FluidTank tank = new FluidTank(LITRES, f -> FuelGrades.of(HdfvFluidRegistry.id(f.getFluid())) != null) {
        @Override protected void onContentsChanged() {
            if (state == State.EMPTY && !isEmpty()) { state = State.IDLE; }
            setChanged();
        }
    };
    private UUID target;
    private double owed;
    private int pumped, amount;
    private State state = State.IDLE;

    public FuelDispenserBlockEntity(BlockPos pos, BlockState s) { super(HdfvFuel.DISPENSER_ENTITY.get(), pos, s); }

    public State state() { return state; }
    /** Litres put in the present (or last) vehicle. */
    public int pumped() { return pumped; }
    public FluidStack fluid() { return tank.getFluid(); }
    public boolean fuelling() { return target != null; }

    /** What pipes and cans see: fills with fuel, drains to fill cans. */
    public IFluidHandler handler() { return tank; }

    private String fuel() { return tank.isEmpty() ? null : HdfvFluidRegistry.id(tank.getFluid().getFluid()); }

    /** The nearest vehicle within reach, or null. */
    PartVehicle nearest() {
        if (level == null) { return null; }
        Vec3 c = Vec3.atCenterOf(worldPosition);
        return level.getEntitiesOfClass(PartVehicle.class, new AABB(worldPosition).inflate(REACH_M), v -> v.distanceToSqr(c) <= REACH_M * REACH_M)
                .stream().min(Comparator.comparingDouble(v -> v.distanceToSqr(c))).orElse(null);
    }

    /** Starts on a vehicle (or says why not). */
    public Component start(PartVehicle v) {
        if (tank.isEmpty()) { state = State.EMPTY; return Component.translatable("hdfv.dispenser.state.empty"); }
        if (v == null) { return Component.translatable("hdfv.dispenser.no_vehicle", (int) REACH_M); }
        Component no = Fuelling.refusal(v, fuel());
        if (no != null) { state = State.REFUSED; return no; }
        target = v.getUUID();
        owed = 0;
        pumped = 0;
        state = State.FUELLING;
        setChanged();
        return Component.translatable("hdfv.dispenser.started", Fuelling.name(fuel()), Math.round(Fuelling.litres(v)), Math.round(Fuelling.capacityLitres(v)));
    }

    /** Used: starts on the nearest vehicle, or stops. */
    public Component use() {
        if (target != null) { stop(State.IDLE); return status(); }
        return start(nearest());
    }

    private void stop(State why) {
        target = null;
        owed = 0;
        state = why;
        setChanged();
    }

    public void tick() {
        tickWork();
        dev.mcmod.hdfv.neoforge.sound.SoundSync.report(this, hum());
    }

    /** Its pump while it fuels (0.19). */
    private dev.mcmod.hdfv.sound.MachineSound.Hum hum() { return state == State.FUELLING ? dev.mcmod.hdfv.sound.MachineSound.plant(dev.mcmod.aerocore.api.acoustics.ProcessNoise.Process.PUMP, 750, 0.8, true) : null; }

    private void tickWork() {
        if (target == null || !(level instanceof ServerLevel server)) { return; }
        Entity e = server.getEntity(target);
        if (!(e instanceof PartVehicle v) || e.distanceToSqr(Vec3.atCenterOf(worldPosition)) > REACH_M * REACH_M) { stop(State.GONE); return; }
        if (tank.isEmpty()) { stop(State.EMPTY); return; }
        String fuel = fuel();
        if (Fuelling.refusal(v, fuel) != null) { stop(Fuelling.room(v, fuel) < 1 ? State.FULL : State.REFUSED); return; }
        owed += LITRES_PER_MINUTE / 60 / 20;
        int whole = (int) Math.floor(owed);
        if (whole < 1) { return; }
        int limit = limitLitres(), want = limit > 0 ? Math.min(whole, limit - pumped) : whole;
        int put = Fuelling.fill(v, fuel, Math.min(want, tank.getFluidAmount()));
        tank.drain(put, IFluidHandler.FluidAction.EXECUTE);
        owed -= whole;
        pumped += put;
        if (limit > 0 && pumped >= limit) { stop(State.DONE); return; }
        if (put < want) { stop(tank.isEmpty() ? State.EMPTY : State.FULL); }
    }

    /** How much it puts in a vehicle, litres (0: until full). */
    public int limitLitres() { return AMOUNTS[Math.floorMod(amount, AMOUNTS.length)]; }

    // ---------------------------------------------------------------- the screen (0.9)

    @Override public dev.mcmod.hdfv.neoforge.petroleum.PetroleumMenu.Kind kind() { return dev.mcmod.hdfv.neoforge.petroleum.PetroleumMenu.Kind.DISPENSER; }

    @Override public Component set(String key, double value) {
        switch (key) {
            case "amount" -> {
                int i = (int) value;
                if (i < 0 || i >= AMOUNTS.length || i != value) { return Configurable.outOfRange("amount", 0, AMOUNTS.length - 1); }
                amount = i;
            }
            case "start" -> {
                if (value > 0.5) {
                    if (target != null) { return null; }
                    Component said = start(nearest());
                    return target != null ? null : said;
                }
                if (target != null) { stop(State.IDLE); }
            }
            default -> { return Component.translatable("hdfv.gui.no_setting"); }
        }
        setChanged();
        return null;
    }

    @Override public CompoundTag view() {
        CompoundTag t = new CompoundTag();
        t.putString("State", state.name().toLowerCase(Locale.ROOT));
        t.putString("Fluid", tank.isEmpty() ? "" : HdfvFluidRegistry.id(tank.getFluid().getFluid()));
        t.putInt("Litres", tank.getFluidAmount());
        t.putInt("Capacity", LITRES);
        t.putInt("Pumped", pumped);
        t.putInt("Amount", amount);
        t.putBoolean("Fuelling", target != null);
        PartVehicle v = target != null && level instanceof ServerLevel server && server.getEntity(target) instanceof PartVehicle p ? p : nearest();
        if (v != null) {
            t.putString("Vehicle", v.blueprint() != null && !v.blueprint().name().isEmpty() ? v.blueprint().name() : v.getName().getString());
            t.putDouble("VehicleLitres", Fuelling.litres(v));
            t.putDouble("VehicleCapacity", Fuelling.capacityLitres(v));
        }
        return t;
    }

    private CompoundTag shown = new CompoundTag();
    @Override public CompoundTag shown() { return shown; }
    @Override public void sendView() {
        if (level != null && !level.isClientSide) { level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS); }
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { CompoundTag t = new CompoundTag(); t.put("View", view()); return t; }
    @Override public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) { shown = tag.getCompound("View"); }
    @Override public void onDataPacket(net.minecraft.network.Connection net, net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket packet,
                                       HolderLookup.Provider registries) { handleUpdateTag(packet.getTag(), registries); }
    @Override public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    public Component status() {
        FluidStack f = tank.getFluid();
        return Component.translatable("hdfv.dispenser.status", Component.translatable("hdfv.dispenser.state." + state.name().toLowerCase(Locale.ROOT)),
                f.isEmpty() ? Component.translatable("hdfv.dispenser.dry") : f.getHoverName(), f.getAmount(), LITRES, pumped);
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tank.writeToNBT(registries, tag);
        if (target != null) { tag.putUUID("Target", target); }
        tag.putDouble("Owed", owed);
        tag.putInt("Pumped", pumped);
        tag.putInt("Amount", amount);
        tag.putString("State", state.name());
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        tank.readFromNBT(registries, tag);
        target = tag.hasUUID("Target") ? tag.getUUID("Target") : null;
        owed = tag.getDouble("Owed");
        pumped = tag.getInt("Pumped");
        amount = Math.floorMod(tag.getInt("Amount"), AMOUNTS.length);
        try { state = State.valueOf(tag.getString("State")); } catch (IllegalArgumentException ex) { state = State.IDLE; }
        if (state == State.EMPTY && !tank.isEmpty()) { state = State.IDLE; }
    }
}
