package dev.mcmod.hdfv.neoforge.power;

import dev.mcmod.hdfv.neoforge.HdfvConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** What the power blocks' entities share (0.8): a tick, a status line, being used, and the work clock's conversions. */
public abstract class PowerBlockEntity extends BlockEntity {
    protected PowerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState s) { super(type, pos, s); }

    public abstract void tick();

    /** Its status, shown when used empty-handed. */
    public abstract Component status();

    /** Used with an item (or empty-handed); whether it did something with it. */
    public boolean use(Player player, InteractionHand hand, ItemStack held) { return false; }

    /** FE a power (W) is over a tick of work. */
    public static double fePerTick(double watts) { return watts * HdfvConfig.workSecondsPerTick() / HdfvConfig.joulesPerFE(); }

    /** The power (W) an amount of FE a tick is. */
    public static double watts(double fePerTick) { return fePerTick * HdfvConfig.joulesPerFE() / HdfvConfig.workSecondsPerTick(); }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) { super.saveAdditional(tag, registries); save(tag, registries); }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) { super.loadAdditional(tag, registries); load(tag, registries); }
    protected void save(CompoundTag tag, HolderLookup.Provider registries) {}
    protected void load(CompoundTag tag, HolderLookup.Provider registries) {}
}
