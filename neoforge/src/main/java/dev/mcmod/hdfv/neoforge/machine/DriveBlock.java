package dev.mcmod.hdfv.neoforge.machine;

import com.mojang.serialization.MapCodec;
import dev.mcmod.hdfv.neoforge.multiblock.Multiblocks;
import dev.mcmod.hdfv.neoforge.multiblock.PatternBlock;
import dev.mcmod.hdfv.neoforge.power.EnergyNetworks;
import dev.mcmod.hdfv.neoforge.power.ShaftNetworks;
import dev.mcmod.hdfv.structure.Structures;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A manual machine tool's drive (0.9): the cell of its multiblock its power comes in by. The electric motor seat takes
 * Forge Energy from cables (and what touches it) for its machine; the pulley seat is belted to a line shaft running
 * past it. A machine takes power only by its drive: swap the block, swap the power.
 * 0.17: the NC drive is a motor seat with its numerical control: the machine runs on its own (no operator), one grade
 * finer (ToolConfig.nc).
 */
public final class DriveBlock extends BaseEntityBlock implements PatternBlock {
    public static final MapCodec<DriveBlock> CODEC = simpleCodec(p -> new DriveBlock(p, true));
    /** Whether it is the motor seat or the NC drive (else the pulley seat). */
    public final boolean electric;
    /** Whether it is the NC drive (0.17). */
    public final boolean numerical;

    public DriveBlock(Properties p, boolean electric) { this(p, electric, false); }

    public DriveBlock(Properties p, boolean electric, boolean numerical) { super(p); this.electric = electric || numerical; this.numerical = numerical; }

    @Override public String kind() { return numerical ? Structures.NC_DRIVE : electric ? Structures.MOTOR_DRIVE : Structures.PULLEY_DRIVE; }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected RenderShape getRenderShape(BlockState s) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState s) { return new DriveBlockEntity(pos, s); }

    @Override protected InteractionResult useWithoutItem(BlockState s, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof DriveBlockEntity be) {
            MachineBlockEntity m = be.machine();
            player.displayClientMessage(m == null ? Component.translatable("hdfv.drive.alone") : Component.translatable("hdfv.drive.drives",
                    m.getDisplayName(), Component.translatable(numerical ? "hdfv.drive.numerical" : electric ? "hdfv.drive.electric" : "hdfv.drive.shaft")), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override protected void onPlace(BlockState s, Level level, BlockPos pos, BlockState old, boolean moved) {
        if (!old.is(this)) { ShaftNetworks.changed(level); EnergyNetworks.changed(level); }
    }

    @Override protected void onRemove(BlockState s, Level level, BlockPos pos, BlockState now, boolean moved) {
        if (!now.is(this)) {
            ShaftNetworks.changed(level);
            EnergyNetworks.changed(level);
            if (level instanceof ServerLevel server) { Multiblocks.memberRemoved(server, pos); }
        }
        super.onRemove(s, level, pos, now, moved);
    }
}
