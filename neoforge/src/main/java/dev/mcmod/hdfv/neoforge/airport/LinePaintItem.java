package dev.mcmod.hdfv.neoforge.airport;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Line paint for pavement (0.20): used on the top of an asphalt or concrete block it paints (or, painted already in
 * this colour, clears) the quarter of the block it was used on, a 0.5 m cell; sneaking paints or clears the whole block.
 * Threshold paint marks the block as part of a runway's threshold, facing the way the player looks (into the runway);
 * that is what a tower finds runways by. White paint keeps a threshold block a threshold. Survival uses one durability a
 * cell.
 */
public final class LinePaintItem extends Item {
    private final MarkedSurfaceBlock.Paint paint;

    public LinePaintItem(Properties p, MarkedSurfaceBlock.Paint paint) {
        super(p);
        this.paint = paint;
    }

    @Override public InteractionResult useOn(UseOnContext c) {
        Level level = c.getLevel();
        BlockPos pos = c.getClickedPos();
        BlockState s = level.getBlockState(pos);
        if (!(s.getBlock() instanceof MarkedSurfaceBlock) || c.getClickedFace() != Direction.UP) { return InteractionResult.PASS; }
        if (level.isClientSide) { return InteractionResult.SUCCESS; }
        Player player = c.getPlayer();
        int cell = (c.getClickLocation().x - pos.getX() >= 0.5 ? 1 : 0) | (c.getClickLocation().z - pos.getZ() >= 0.5 ? 2 : 0);
        int bit = 1 << cell;
        boolean whole = player != null && player.isSecondaryUseActive();
        MarkedSurfaceBlock.Paint now = s.getValue(MarkedSurfaceBlock.PAINT);
        int mask = s.getValue(MarkedSurfaceBlock.MASK);
        BlockState next;
        if (paint == MarkedSurfaceBlock.Paint.THRESHOLD) {
            Direction facing = player == null ? Direction.NORTH : player.getDirection();
            boolean clear = now == MarkedSurfaceBlock.Paint.THRESHOLD && s.getValue(MarkedSurfaceBlock.FACING) == facing && !whole;
            next = clear ? s.setValue(MarkedSurfaceBlock.PAINT, mask == 0 ? MarkedSurfaceBlock.Paint.NONE : MarkedSurfaceBlock.Paint.WHITE)
                    : s.setValue(MarkedSurfaceBlock.PAINT, MarkedSurfaceBlock.Paint.THRESHOLD).setValue(MarkedSurfaceBlock.FACING, facing);
            if (player != null) { player.displayClientMessage(Component.translatable(clear ? "hdfv.paint.threshold_cleared" : "hdfv.paint.threshold_set",
                    Component.translatable("hdfv.direction." + facing.getName())), true); }
        } else {
            // a threshold block takes white as its own colour
            boolean same = now == paint || (now == MarkedSurfaceBlock.Paint.THRESHOLD && paint == MarkedSurfaceBlock.Paint.WHITE);
            int target = whole ? 15 : bit;
            int nextMask = same ? ((mask & target) == target ? mask & ~target : mask | target) : target;
            MarkedSurfaceBlock.Paint nextPaint = nextMask == 0 && now != MarkedSurfaceBlock.Paint.THRESHOLD ? MarkedSurfaceBlock.Paint.NONE
                    : now == MarkedSurfaceBlock.Paint.THRESHOLD && paint == MarkedSurfaceBlock.Paint.WHITE ? MarkedSurfaceBlock.Paint.THRESHOLD : paint;
            next = s.setValue(MarkedSurfaceBlock.PAINT, nextPaint).setValue(MarkedSurfaceBlock.MASK, nextMask);
        }
        if (next != s) {
            level.setBlock(pos, next, net.minecraft.world.level.block.Block.UPDATE_ALL);
            if (player != null && !player.getAbilities().instabuild) { c.getItemInHand().hurtAndBreak(1, player, net.minecraft.world.entity.LivingEntity.getSlotForHand(c.getHand())); }
        }
        return InteractionResult.CONSUME;
    }
}
