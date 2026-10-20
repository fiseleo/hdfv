package dev.mcmod.hdfv.neoforge.smelting;

import com.mojang.serialization.MapCodec;
import dev.mcmod.hdfv.neoforge.material.ElectricRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The electric arc furnace's and the electrolysis cell's block (0.23): faces the player who places it, lit while it
 * works (the arc's glare and sparks; the cell's bubbling and fume), cables join it, used it opens its menu.
 */
public final class SmelterBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final MapCodec<SmelterBlock> ARC_CODEC = simpleCodec(p -> new SmelterBlock(ElectricRecipe.Machine.ARC_FURNACE, p));
    public static final MapCodec<SmelterBlock> CELL_CODEC = simpleCodec(p -> new SmelterBlock(ElectricRecipe.Machine.ELECTROLYSIS, p));

    public final ElectricRecipe.Machine machine;

    public SmelterBlock(ElectricRecipe.Machine machine, Properties p) {
        super(p);
        this.machine = machine;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false));
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return machine == ElectricRecipe.Machine.ARC_FURNACE ? ARC_CODEC : CELL_CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING, LIT); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext c) { return defaultBlockState().setValue(FACING, c.getHorizontalDirection().getOpposite()); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new SmelterBlockEntity(pos, state); }

    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, HdfvSmelting.SMELTER_ENTITY.get(), (l, p, s, be) -> be.tick());
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof SmelterBlockEntity be) {
            sp.openMenu(be, buf -> buf.writeBlockPos(pos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Lit: the arc furnace sparks and smokes from its roof; the cell bubbles and fumes. */
    @Override public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) { return; }
        double x = pos.getX() + 0.5, y = pos.getY() + 1.0, z = pos.getZ() + 0.5;
        if (machine == ElectricRecipe.Machine.ARC_FURNACE) {
            for (int i = 0; i < 2; i++) { level.addParticle(ParticleTypes.LAVA, x + (random.nextDouble() - 0.5) * 0.4, y, z + (random.nextDouble() - 0.5) * 0.4, 0, 0, 0); }
            level.addParticle(ParticleTypes.LARGE_SMOKE, x, y + 0.1, z, 0, 0.05, 0);
        } else {
            level.addParticle(ParticleTypes.BUBBLE_POP, x + (random.nextDouble() - 0.5) * 0.6, y - 0.1, z + (random.nextDouble() - 0.5) * 0.6, 0, 0.02, 0);
            if (random.nextInt(3) == 0) { level.addParticle(ParticleTypes.WHITE_SMOKE, x, y, z, 0, 0.03, 0); }
        }
    }

    @Override protected void onPlace(BlockState s, Level level, BlockPos pos, BlockState old, boolean moved) {
        if (!old.is(this)) { dev.mcmod.hdfv.neoforge.power.EnergyNetworks.changed(level); }
    }

    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState now, boolean moved) {
        if (!state.is(now.getBlock())) {
            if (level.getBlockEntity(pos) instanceof SmelterBlockEntity be) { Containers.dropContents(level, pos, be.items); }
            dev.mcmod.hdfv.neoforge.power.EnergyNetworks.changed(level);
        }
        super.onRemove(state, level, pos, now, moved);
    }
}
