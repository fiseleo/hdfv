package dev.mcmod.hdfv.neoforge.machine;

import com.mojang.serialization.MapCodec;
import dev.mcmod.hdfv.manufacture.Station;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Locale;

/** A workshop machine's block (0.7): one per station, facing the player who places it, opening its screen. */
public final class MachineBlock extends BaseEntityBlock implements dev.mcmod.hdfv.neoforge.multiblock.PatternBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public final Station station;
    private final MapCodec<MachineBlock> codec;

    public MachineBlock(Properties p, Station station) {
        super(p);
        this.station = station;
        this.codec = simpleCodec(q -> new MachineBlock(q, station));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(FORMED, false));
    }

    /** The block id of a station ("lathe", "cnc_machining_centre"). */
    public static String id(Station s) {
        return switch (s) {
            case CNC -> "cnc_machining_centre";
            default -> s.name().toLowerCase(Locale.ROOT);
        };
    }

    /** Its kind in a multiblock pattern (the CNC's cabinet): its id. */
    @Override public String kind() { return id(station); }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return codec; }
    /** Whether it is the controller of a formed manual machine tool (0.9): then its renderer draws the whole machine and the block nothing. */
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty FORMED = dev.mcmod.hdfv.neoforge.multiblock.MemberBlock.FORMED;

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING, FORMED); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext c) { return defaultBlockState().setValue(FACING, c.getHorizontalDirection().getOpposite()); }

    /** 0.12: a working machine's smoke, steam and sparks (MachineParticles), on the client. */
    @Override public void animateTick(BlockState state, Level level, BlockPos pos, net.minecraft.util.RandomSource random) {
        if (level.getBlockEntity(pos) instanceof MachineBlockEntity be && be.shownWorking()) { MachineParticles.working(level, pos, station, state.getValue(FACING), random); }
    }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new MachineBlockEntity(pos, state); }

    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, HdfvMachines.MACHINE_ENTITY.get(), MachineBlockEntity::tick);
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof MachineBlockEntity be) {
            if (be.multiblock() != null) { be.multiblock().check(sp.serverLevel()); }
            sp.openMenu(be, buf -> { buf.writeBlockPos(pos); buf.writeVarInt(station.ordinal()); });
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof MachineBlockEntity be) {
            Containers.dropContents(level, pos, be.items);
            if (be.multiblock() != null && level instanceof net.minecraft.server.level.ServerLevel server) { be.multiblock().dissolve(server); }
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
