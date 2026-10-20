package dev.mcmod.hdfv.neoforge.multiblock;

import dev.mcmod.hdfv.structure.Pattern;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Which controller each formed member belongs to, per level (0.8): filled as machines form (and again as their
 * controllers load), emptied as they dissolve, so a member taken away finds its machine at once.
 */
public final class Multiblocks {
    private Multiblocks() {}

    private static final Map<ServerLevel, Map<Long, Long>> MEMBERS = new WeakHashMap<>();

    /** Something that holds a multiblock (a controller's block entity). */
    public interface Holder { Multiblock multiblock(); }

    static synchronized void put(ServerLevel level, BlockPos member, BlockPos controller) { MEMBERS.computeIfAbsent(level, l -> new HashMap<>()).put(member.asLong(), controller.asLong()); }

    static synchronized void remove(ServerLevel level, BlockPos member, BlockPos controller) {
        Map<Long, Long> m = MEMBERS.get(level);
        if (m != null) { m.remove(member.asLong(), controller.asLong()); }
    }

    /** The controller a member belongs to, or null. */
    public static synchronized BlockPos controllerOf(ServerLevel level, BlockPos member) {
        Map<Long, Long> m = MEMBERS.get(level);
        Long c = m == null ? null : m.get(member.asLong());
        return c == null ? null : BlockPos.of(c);
    }

    /** A member is gone: its machine dissolves. */
    public static void memberRemoved(ServerLevel level, BlockPos member) {
        BlockPos c = controllerOf(level, member);
        if (c != null && level.getBlockEntity(c) instanceof Holder h) { h.multiblock().dissolve(level); }
    }

    /** What a pattern sees in a level: a pattern block's kind; nothing for air and plants; the block's id otherwise. */
    public static Pattern.World world(Level level) {
        return (x, y, z) -> {
            BlockState s = level.getBlockState(new BlockPos(x, y, z));
            if (s.isAir() || s.canBeReplaced() && s.getFluidState().isEmpty()) { return null; }
            if (s.getBlock() instanceof PatternBlock p) { return p.kind(); }
            return BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString();
        };
    }
}
