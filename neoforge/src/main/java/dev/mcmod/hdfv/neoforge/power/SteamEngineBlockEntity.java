package dev.mcmod.hdfv.neoforge.power;

import dev.mcmod.hdfv.neoforge.HdfvConfig;
import dev.mcmod.hdfv.power.PowerPlant;
import dev.mcmod.hdfv.power.ShaftLine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.Locale;

/**
 * The mill steam engine (0.8): PowerPlant.STEAM (AeroCore's SteamEngine.MILL: 1 MPa saturated, about 6 % of the fuel's
 * heat to the shaft), up to 15 kW, governed, with a heavy flywheel. Its firebox takes coal, charcoal, blocks of coal,
 * logs, planks and sticks (FuelItem masses) from its slot (hoppers too), burning for what it gives in work time. Its
 * boiler is taken as fed with water.
 */
public final class SteamEngineBlockEntity extends PowerBlockEntity implements ShaftEnd {
    private final ItemStackHandler fuel = new ItemStackHandler(1) {
        @Override public boolean isItemValid(int slot, ItemStack s) { return fuelOf(s) != null; }
        @Override protected void onContentsChanged(int slot) { setChanged(); }
    };
    private double heatJ, lastW, rpm, burntKg;

    public SteamEngineBlockEntity(BlockPos pos, BlockState s) { super(HdfvPower.STEAM_ENTITY.get(), pos, s); }

    public ItemStackHandler items() { return fuel; }
    public double lastShaftW() { return lastW; }
    /** Fuel burnt so far, kg (for tests and the status line). */
    public double burntKg() { return burntKg; }

    /** 0.23: coke, hdfv's and other mods' (the common tag). */
    public static final net.minecraft.tags.TagKey<net.minecraft.world.item.Item> COKE_TAG = net.minecraft.tags.ItemTags.create(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("c", "coal_coke"));

    /** A solid fuel item's fuel and mass, or null. */
    public static PowerPlant.FuelItem fuelOf(ItemStack s) {
        if (s.is(Items.COAL)) { return PowerPlant.COAL; }
        if (s.is(Items.CHARCOAL)) { return PowerPlant.CHARCOAL; }
        if (s.is(Items.COAL_BLOCK)) { return PowerPlant.COAL_BLOCK; }
        if (s.is(ItemTags.LOGS_THAT_BURN)) { return PowerPlant.LOG; }
        if (s.is(ItemTags.PLANKS)) { return PowerPlant.PLANK; }
        if (s.is(Items.STICK)) { return PowerPlant.STICK; }
        if (s.is(COKE_TAG)) { return PowerPlant.COKE; }
        return null;
    }

    private double fuelHeatNeeded(double shaftW) { return shaftW * HdfvConfig.workSecondsPerTick() / PowerPlant.STEAM.overallEfficiency(); }

    @Override public ShaftLine.End lineEnd(double lineRpm) {
        boolean fired = heatJ > 0 || !fuel.getStackInSlot(0).isEmpty();
        return new ShaftLine.End(fired ? PowerPlant.STEAM_W : 0, 0, PowerPlant.STEAM_FLYWHEEL_INERTIA);
    }

    @Override public void settle(double lineRpm, double givenW, double gotW, double standLoadW) {
        rpm = lineRpm;
        lastW = givenW;
        double need = fuelHeatNeeded(givenW);
        while (heatJ < need && !fuel.getStackInSlot(0).isEmpty()) {
            PowerPlant.FuelItem f = fuelOf(fuel.getStackInSlot(0));
            if (f == null) { break; }
            fuel.extractItem(0, 1, false);
            heatJ += f.heatJ();
            burntKg += f.kg();
        }
        heatJ = Math.max(0, heatJ - need);
        setChanged();
    }

    @Override public void tick() {
        if (level == null) { return; }
        ShaftNetworks.tick(level, worldPosition);
        dev.mcmod.hdfv.neoforge.sound.SoundSync.report(this, rpm > 5 && lastW > 1 ? dev.mcmod.hdfv.sound.MachineSound.steamEngine(rpm, lastW, PowerPlant.STEAM_W) : null);
    }

    @Override public boolean use(Player player, InteractionHand hand, ItemStack held) {
        if (fuelOf(held) == null) { return false; }
        ItemStack left = fuel.insertItem(0, held.copy(), false);
        player.setItemInHand(hand, left);
        return true;
    }

    @Override public Component status() {
        return Component.translatable("hdfv.power.steam", String.format(Locale.ROOT, "%.1f", lastW / 1000), String.format(Locale.ROOT, "%.0f", rpm),
                fuel.getStackInSlot(0).getCount());
    }

    @Override protected void save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("fuel", fuel.serializeNBT(registries));
        tag.putDouble("heatJ", heatJ);
        tag.putDouble("burntKg", burntKg);
    }
    @Override protected void load(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.contains("fuel")) { fuel.deserializeNBT(registries, tag.getCompound("fuel")); }
        heatJ = tag.getDouble("heatJ");
        burntKg = tag.getDouble("burntKg");
    }
}
