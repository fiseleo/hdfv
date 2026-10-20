package dev.mcmod.hdfv.neoforge.assembly;

import dev.mcmod.hdfv.assembly.RoomScan;
import dev.mcmod.hdfv.manufacture.Station;
import dev.mcmod.hdfv.neoforge.machine.HdfvMachines;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The world as an assembly room sees it (0.7): water; the room's own blocks (floor, slipway, crane rail, doors,
 * controllers, welding stations); chests and anything else holding items as storage; anything a person walks through
 * as air (and unloaded chunks: open country); the rest as wall.
 */
public record WorldGrid(Level level) implements RoomScan.Grid {
    @Override public RoomScan.Cell at(int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        if (!level.isLoaded(pos)) { return RoomScan.Cell.AIR; }
        BlockState s = level.getBlockState(pos);
        if (s.getFluidState().is(FluidTags.WATER)) { return RoomScan.Cell.WATER; }
        Block b = s.getBlock();
        if (b == HdfvAssembly.ASSEMBLY_FLOOR.get()) { return RoomScan.Cell.FLOOR; }
        if (b == HdfvAssembly.SLIPWAY.get()) { return RoomScan.Cell.SLIPWAY; }
        if (b == HdfvAssembly.CRANE_RAIL.get()) { return RoomScan.Cell.CRANE; }
        if (b instanceof RoomDoorBlock) { return RoomScan.Cell.DOOR; }
        if (b instanceof RoomControllerBlock || b instanceof dev.mcmod.hdfv.neoforge.paint.PaintBoothBlock
                || b instanceof dev.mcmod.hdfv.neoforge.refit.RefitShopBlock) { return RoomScan.Cell.CONTROLLER; }
        if (b == HdfvMachines.MACHINES.get(Station.WELDING_STATION).get()) { return RoomScan.Cell.WELDING_STATION; }
        if (s.hasBlockEntity() && !(b instanceof dev.mcmod.hdfv.neoforge.machine.MachineBlock)
                && level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, pos, null) != null) {
            return RoomScan.Cell.STORAGE;
        }
        return s.getCollisionShape(level, pos).isEmpty() ? RoomScan.Cell.AIR : RoomScan.Cell.WALL;
    }
}
