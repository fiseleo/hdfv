package dev.mcmod.hdfv.neoforge.worksite;

import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.earthmoving.Asphalt;
import dev.mcmod.aerocore.api.vehicle.Vehicle;
import dev.mcmod.aerocore.neoforge.vehicle.PartVehicle;
import dev.mcmod.hdfv.neoforge.multiblock.ControllerBlockEntity;
import dev.mcmod.hdfv.structure.Structures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * 0.26: an asphalt or a concrete batching plant's controller (Structures.ASPHALT_PLANT_PATTERN, CONCRETE_PLANT_PATTERN):
 * it batches BATCH_M3 of mix every BATCH_TICKS of work hours while formed, powered (RATED_W from the cables) and fed,
 * into its silo (at most SILO_M3), and discharges the silo into the bulk hold of a vehicle standing in front of it
 * (a hold's centre within DISCHARGE_M of the silo's gate, CHUTE_HEIGHT_M up over the front, and under it) at DISCHARGE_M3_PER_S.
 * <ul>
 * <li>Asphalt (hot mix, 5 % bitumen by mass): AGGREGATE_ITEMS aggregate items (gravel, sand, cobblestone, blasted rock or
 *     reclaimed asphalt), a bitumen and the burner's fuel (a coal, charcoal or coke every FUEL_BATCHES batches) a batch;
 *     made at Asphalt.LAYING_K plus MIX_OVER_K (plants mix at 150 to 170 C), it cools in the truck (WorldWork).</li>
 * <li>Concrete: CONCRETE_ITEMS concrete powder items (the premix of cement, sand and gravel) and water from the
 *     cauldron behind the hopper (a level a batch: the plant refills it from a water bucket used on the controller);
 *     its maturity starts at 0 and it sets in the drum (WorldWork) unless poured in time.</li>
 * </ul>
 * Items go in by hoppers and pipes (its inventory) or by hand (used with them). Rates are rules of the order of a small
 * plant (60 t/h of asphalt, 30 m^3/h of concrete) at the server's work hours.
 */
public final class PlantBlockEntity extends ControllerBlockEntity {
    public enum Kind { ASPHALT, CONCRETE }

    public static final double BATCH_M3 = 0.1, SILO_M3 = 10, DISCHARGE_M = 5, DISCHARGE_M3_PER_S = 0.5, MIX_OVER_K = 10, CHUTE_HEIGHT_M = 4.5;
    public static final int AGGREGATE_ITEMS = 14, CONCRETE_ITEMS = 15, FUEL_BATCHES = 4;
    public static final double ASPHALT_W = 75_000, CONCRETE_W = 30_000;

    private final Kind kind;
    private final ItemStackHandler items = new ItemStackHandler(9) {
        @Override protected void onContentsChanged(int slot) { setChanged(); }
    };
    private double siloM3, siloT = 288.15, progress;
    private int batches, water;
    private String missing = "";

    public PlantBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState s, Kind kind) {
        super(type, pos, s, kind == Kind.ASPHALT ? Structures.ASPHALT_PLANT_PATTERN : Structures.CONCRETE_PLANT_PATTERN, kind == Kind.ASPHALT ? ASPHALT_W : CONCRETE_W);
        this.kind = kind;
    }

    public static PlantBlockEntity asphalt(BlockPos pos, BlockState s) { return new PlantBlockEntity(HdfvWorksite.ASPHALT_PLANT_ENTITY.get(), pos, s, Kind.ASPHALT); }
    public static PlantBlockEntity concrete(BlockPos pos, BlockState s) { return new PlantBlockEntity(HdfvWorksite.CONCRETE_PLANT_ENTITY.get(), pos, s, Kind.CONCRETE); }

    public ItemStackHandler items() { return items; }
    public double siloM3() { return siloM3; }

    /** What the plant's mix is carried as (a block form). */
    public String form() { return WorldWork.id(kind == Kind.ASPHALT ? HdfvWorksite.HOT_ASPHALT.get() : HdfvWorksite.FRESH_CONCRETE.get()); }

    /** A failure in the controller's own work is logged once and stops its work, not the server. */
    private boolean failed;

    @Override protected void work(ServerLevel level, boolean formed) {
        if (failed) { return; }
        try { run(level, formed); }
        catch (RuntimeException e) {
            failed = true;
            com.mojang.logging.LogUtils.getLogger().error("hdfv: {} at {} stopped", getClass().getSimpleName(), worldPosition, e);
        }
    }

    private void run(ServerLevel level, boolean formed) {
        if (!formed) { missing = "hdfv.plant.not_formed"; return; }
        if (siloM3 + BATCH_M3 <= SILO_M3 + 1e-9) {
            String lack = lacking();
            if (lack != null) { missing = lack; }
            else {
                double share = draw(kind == Kind.ASPHALT ? ASPHALT_W : CONCRETE_W);
                if (share < 1) { missing = "hdfv.plant.no_power"; }
                else {
                    missing = "";
                    progress += dev.mcmod.hdfv.neoforge.HdfvConfig.workSecondsPerTick();
                    if (progress >= batchSeconds()) { progress = 0; batch(); }
                }
            }
        }
        if (siloM3 > 1e-9 && level.getGameTime() % 10 == 0) { discharge(level); }
    }

    /** Work seconds a batch takes: 0.1 m^3 at 60 t/h of asphalt (2.35 t/m^3) or 30 m^3/h of concrete. */
    double batchSeconds() { return kind == Kind.ASPHALT ? BATCH_M3 * 2350 / (60_000 / 3600.0) : BATCH_M3 / (30 / 3600.0); }

    /** What a batch lacks (a language key), or null. */
    String lacking() {
        if (kind == Kind.ASPHALT) {
            if (count(PlantBlockEntity::aggregate) < AGGREGATE_ITEMS) { return "hdfv.plant.no_aggregate"; }
            if (count(st -> st.is(dev.mcmod.hdfv.neoforge.petroleum.HdfvRefinery.BITUMEN.get())) < 1) { return "hdfv.plant.no_bitumen"; }
            if (batches % FUEL_BATCHES == 0 && count(PlantBlockEntity::fuel) < 1) { return "hdfv.plant.no_fuel"; }
            return null;
        }
        if (count(PlantBlockEntity::premix) < CONCRETE_ITEMS) { return "hdfv.plant.no_premix"; }
        if (water < 1 && !cauldronWater()) { return "hdfv.plant.no_water"; }
        return null;
    }

    static boolean aggregate(ItemStack s) {
        return s.is(Tags.Items.GRAVELS) || s.is(Tags.Items.SANDS) || s.is(Tags.Items.COBBLESTONES) || s.is(HdfvWorksite.BLASTED_ROCK.get().asItem())
                || s.is(HdfvWorksite.RECLAIMED_ASPHALT.get().asItem());
    }

    static boolean fuel(ItemStack s) { return s.is(Items.COAL) || s.is(Items.CHARCOAL) || s.is(ItemTags.COALS) || s.getItem().toString().endsWith("coke"); }

    static boolean premix(ItemStack s) { return s.getItem().toString().endsWith("concrete_powder"); }

    private int count(java.util.function.Predicate<ItemStack> what) {
        int n = 0;
        for (int i = 0; i < items.getSlots(); i++) { ItemStack s = items.getStackInSlot(i); if (!s.isEmpty() && what.test(s)) { n += s.getCount(); } }
        return n;
    }

    private void take(java.util.function.Predicate<ItemStack> what, int n) {
        for (int i = 0; i < items.getSlots() && n > 0; i++) {
            ItemStack s = items.getStackInSlot(i);
            if (s.isEmpty() || !what.test(s)) { continue; }
            int k = Math.min(n, s.getCount());
            items.extractItem(i, k, false);
            n -= k;
        }
    }

    /** The cauldron behind the hopper gives a level of water (the formed pattern's cell). */
    private boolean cauldronWater() {
        if (level == null || multiblock.match() == null) { return false; }
        for (var c : multiblock.match().cells()) {
            BlockPos p = new BlockPos(c.x(), c.y(), c.z());
            BlockState s = level.getBlockState(p);
            if (s.is(net.minecraft.world.level.block.Blocks.WATER_CAULDRON)) {
                int lv = s.getValue(net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL);
                level.setBlock(p, lv <= 1 ? net.minecraft.world.level.block.Blocks.CAULDRON.defaultBlockState()
                        : s.setValue(net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL, lv - 1), 3);
                water += 1;
                return true;
            }
        }
        return false;
    }

    private void batch() {
        if (kind == Kind.ASPHALT) {
            take(PlantBlockEntity::aggregate, AGGREGATE_ITEMS);
            take(st -> st.is(dev.mcmod.hdfv.neoforge.petroleum.HdfvRefinery.BITUMEN.get()), 1);
            if (batches % FUEL_BATCHES == 0) { take(PlantBlockEntity::fuel, 1); }
            siloT = siloM3 > 0 ? (siloT * siloM3 + (Asphalt.LAYING_K + MIX_OVER_K) * BATCH_M3) / (siloM3 + BATCH_M3) : Asphalt.LAYING_K + MIX_OVER_K;
        } else {
            take(PlantBlockEntity::premix, CONCRETE_ITEMS);
            water = Math.max(water - 1, 0);
            siloT = 288.15;
        }
        siloM3 += BATCH_M3;
        batches++;
        setChanged();
    }

    /** Pours from the silo into the bulk hold of a vehicle standing in front. */
    private void discharge(ServerLevel level) {
        Direction front = getBlockState().hasProperty(BlockStateProperties.HORIZONTAL_FACING) ? getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING) : Direction.NORTH;
        BlockPos p = worldPosition;
        // the silo's gate over the front of the plant's top (a truck drives under it: real plants load from above)
        Vec3d chute = new Vec3d(p.getX() + 0.5 + front.getStepX() * 1.5, p.getY() + CHUTE_HEIGHT_M, p.getZ() + 0.5 + front.getStepZ() * 1.5);
        var box = new net.minecraft.world.phys.AABB(chute.x() - DISCHARGE_M, chute.y() - 5, chute.z() - DISCHARGE_M, chute.x() + DISCHARGE_M, chute.y() + 1, chute.z() + DISCHARGE_M);
        for (PartVehicle v : level.getEntitiesOfClass(PartVehicle.class, box)) {
            Vehicle vehicle = v.vehicle();
            if (vehicle == null || v.vehicleState() == null || v.vehicleState().body().velocity().length() > 0.3) { continue; }
            BulkCargo bulk = BulkCargo.of(v);
            for (int k = 0; k < vehicle.holds().size(); k++) {
                Vehicle.Hold h = vehicle.holds().get(k);
                if (!BulkCargo.bulk(h)) { continue; }
                Vec3d centre = v.vehicleState().body().worldPoint(v.vehicleState().properties(), v.vehicleState().pose().holdCentre(k));
                if (Math.hypot(centre.x() - chute.x(), centre.z() - chute.z()) > DISCHARGE_M || centre.y() > chute.y()) { continue; }
                BulkCargo.Load l = bulk.hold(h.id());
                double room = h.hold().capacityM3() - l.looseM3();
                double m3 = Math.min(Math.min(siloM3, DISCHARGE_M3_PER_S * 0.5), Math.max(room, 0) / Math.max(BulkCargo.material(form()).swell(), 1));
                if (m3 <= 1e-6) { continue; }
                BulkCargo.Load add = new BulkCargo.Load();
                add.add(form(), m3);
                add.temperatureK = siloT;
                add.maturityKs = 0;
                l.add(add);
                siloM3 -= m3;
                bulk.weigh(v);
                setChanged();
                return;
            }
        }
    }

    @Override public boolean use(Player player, InteractionHand hand, ItemStack held) {
        if (held.is(Items.WATER_BUCKET) && kind == Kind.CONCRETE) {
            water += 3;
            if (!player.isCreative()) { player.setItemInHand(hand, new ItemStack(Items.BUCKET)); }
            setChanged();
            return true;
        }
        if (held.isEmpty()) { return false; }
        ItemStack rest = held.copy();
        for (int i = 0; i < items.getSlots() && !rest.isEmpty(); i++) { rest = items.insertItem(i, rest, false); }
        if (rest.getCount() == held.getCount()) { return false; }
        if (!player.isCreative()) { player.setItemInHand(hand, rest); }
        return true;
    }

    @Override public Component status() {
        Component state = missing.isEmpty() ? Component.translatable("hdfv.plant.working") : Component.translatable(missing);
        return Component.translatable(kind == Kind.ASPHALT ? "hdfv.plant.asphalt_status" : "hdfv.plant.concrete_status",
                String.format(java.util.Locale.ROOT, "%.1f", siloM3), batches, state);
    }

    @Override public List<ItemStack> drops() {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < items.getSlots(); i++) { if (!items.getStackInSlot(i).isEmpty()) { out.add(items.getStackInSlot(i).copy()); } }
        return out;
    }

    @Override protected void save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("Items", items.serializeNBT(registries));
        tag.putDouble("Silo", siloM3);
        tag.putDouble("SiloT", siloT);
        tag.putDouble("Progress", progress);
        tag.putInt("Batches", batches);
        tag.putInt("Water", water);
    }

    @Override protected void load(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.contains("Items")) { items.deserializeNBT(registries, tag.getCompound("Items")); }
        siloM3 = tag.getDouble("Silo");
        siloT = tag.contains("SiloT") ? tag.getDouble("SiloT") : 288.15;
        progress = tag.getDouble("Progress");
        batches = tag.getInt("Batches");
        water = tag.getInt("Water");
    }
}
