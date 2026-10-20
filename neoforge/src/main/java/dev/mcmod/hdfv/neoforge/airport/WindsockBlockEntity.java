package dev.mcmod.hdfv.neoforge.airport;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A windsock's block entity (0.20): nothing stored; it is there for its renderer, which turns the sock with the wind
 * at its top (AeroCore's WorldAir: mean and gust) and fills it, fully out at 15 kt as a real one (ICAO Annex 14 5.1.1).
 * The sock's swing is the renderer's own smoothing, client side.
 */
public final class WindsockBlockEntity extends BlockEntity {
    /** The sock's smoothed direction (radians, the way it streams) and extension (0 to 1), kept by the renderer. */
    public double shownAngle = Double.NaN, shownFill;

    public WindsockBlockEntity(BlockPos pos, BlockState s) { super(HdfvAirports.WINDSOCK_ENTITY.get(), pos, s); }
}
