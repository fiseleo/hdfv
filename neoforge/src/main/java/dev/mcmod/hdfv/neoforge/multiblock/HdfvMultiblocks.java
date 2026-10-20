package dev.mcmod.hdfv.neoforge.multiblock;

import dev.mcmod.hdfv.manufacture.Station;
import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.neoforge.machine.HdfvMachines;
import dev.mcmod.hdfv.structure.Pattern;
import dev.mcmod.hdfv.structure.Structures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Multiblock members (0.8): the CNC's bed, guard and frame and its modules (structure.Structures), (0.9) the manual
 * machine tools' beds, tables, columns, heads and accessories, and placing a pattern whole (the dev command, GameTests).
 * 0.17: the machine families' members and heads (manufacture.Head: the head block makes the machine).
 */
public final class HdfvMultiblocks {
    private HdfvMultiblocks() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Hdfv.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Hdfv.MOD_ID);

    /** Members by kind. */
    public static final Map<String, DeferredBlock<MemberBlock>> MEMBERS = new LinkedHashMap<>();

    private static BlockBehaviour.Properties metal(MapColor colour) {
        return BlockBehaviour.Properties.of().mapColor(colour).strength(4f, 6f).sound(SoundType.METAL).requiresCorrectToolForDrops();
    }

    static {
        member(Structures.BED, MapColor.COLOR_GRAY);
        member(Structures.GUARD, MapColor.SNOW);
        member(Structures.FRAME, MapColor.COLOR_LIGHT_GRAY);
        member(Structures.SPINDLE_HEAD, MapColor.METAL);
        member(Structures.B_AXIS_HEAD, MapColor.METAL);
        member(Structures.ROTARY_TABLE, MapColor.METAL);
        member(Structures.TRUNNION, MapColor.METAL);
        member(Structures.TURNING_SPINDLE, MapColor.METAL);
        member(Structures.SUB_SPINDLE, MapColor.METAL);
        member(Structures.TURRET, MapColor.METAL);
        member(Structures.Y_TURRET, MapColor.METAL);
        for (String k : List.of(Structures.LATHE_BED, Structures.MILL_TABLE, Structures.MILL_HEAD, Structures.PLANER_BED, Structures.PLANER_UPRIGHT,
                Structures.CROSS_RAIL, Structures.BORING_TABLE, Structures.BORING_COLUMN, Structures.BORING_HEAD, Structures.DRILL_TABLE,
                Structures.DRILL_COLUMN, Structures.DRILL_HEAD, Structures.DRILL_SPINDLE)) { member(k, MapColor.COLOR_GREEN); }
        for (dev.mcmod.hdfv.manufacture.ToolConfig.Accessory a : dev.mcmod.hdfv.manufacture.ToolConfig.Accessory.values()) { member(a.kind, MapColor.METAL); }
        for (String k : List.of(Structures.GRINDER_BED, Structures.FINISHER_TABLE, Structures.FINISHER_COLUMN, Structures.FINISHER_TOP, Structures.GEAR_TABLE,
                Structures.GEAR_COLUMN, Structures.GEAR_TOP, Structures.RAM_BED, Structures.SAW_BED, Structures.EDM_TANK, Structures.CUTTING_SLATS,
                Structures.THREADER_BASE, Structures.RIVETER_FRAME)) { member(k, MapColor.COLOR_BLUE); }
        for (dev.mcmod.hdfv.manufacture.Head h : dev.mcmod.hdfv.manufacture.Head.values()) { member(h.kind, MapColor.METAL); }
    }

    private static void member(String kind, MapColor colour) {
        DeferredBlock<MemberBlock> b = BLOCKS.register(kind, () -> new MemberBlock(metal(colour), kind, true));
        ITEMS.registerSimpleBlockItem(kind, b);
        MEMBERS.put(kind, b);
    }

    private static Map<String, Block> kinds;

    /** The block of a pattern kind (any block of ours that is a PatternBlock: members, controllers, wellheads). */
    public static synchronized Block block(String kind) {
        if (kinds == null) {
            kinds = new java.util.HashMap<>();
            for (Block b : net.minecraft.core.registries.BuiltInRegistries.BLOCK) { if (b instanceof PatternBlock p) { kinds.putIfAbsent(p.kind(), b); } }
        }
        Block b = kinds.get(kind);
        if (b == null) { throw new IllegalArgumentException("No block for " + kind); }
        return b;
    }

    /**
     * Places a pattern whole: the controller at a position facing a way, with a number of repeats, each symbol as its
     * first kind unless chosen otherwise (symbol to kind); the working envelope cleared.
     */
    public static void place(ServerLevel level, Pattern p, BlockPos controller, Direction facing, int repeats, Map<Character, String> choices) {
        place(level, p, controller, facing, repeats, 1, choices);
    }

    /** The same with a length (0.9: the columns repeated across); a choice of AIR leaves the cell empty (an optional one). */
    public static void place(ServerLevel level, Pattern p, BlockPos controller, Direction facing, int repeats, int length, Map<Character, String> choices) {
        for (Pattern.Cell c : p.cells(controller.getX(), controller.getY(), controller.getZ(), facing.get2DDataValue(), repeats, length)) {
            BlockPos at = new BlockPos(c.x(), c.y(), c.z());
            if (c.symbol() == '.' || AIR.equals(choices.get(c.symbol()))) { level.setBlock(at, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL); continue; }
            BlockState s = block(choices.getOrDefault(c.symbol(), c.kind())).defaultBlockState();
            if (s.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) { s = s.setValue(BlockStateProperties.HORIZONTAL_FACING, facing); }
            level.setBlock(at, s, Block.UPDATE_ALL);
        }
    }

    /** A choice that leaves a cell empty. */
    public static final String AIR = "air";

    /**
     * Choices for a manual machine tool (0.9): "" or "motor" (the motor seat and the first accessory in every cell),
     * "pulley" (the pulley seat), "bare" or "bare_pulley" (no accessories); 0.17: "nc" (the NC drive), and a family's
     * head by its kind or its kind's first word ("hobbing", "laser"), joined by "+" ("laser+nc", "hobbing+bare").
     */
    public static Map<Character, String> machineChoices(Pattern p, String variant) {
        Map<Character, String> m = new java.util.HashMap<>();
        if (variant.contains("pulley")) { m.put('D', Structures.PULLEY_DRIVE); }
        if (java.util.Arrays.asList(variant.split("\\+")).contains("nc")) { m.put('D', Structures.NC_DRIVE); }
        for (dev.mcmod.hdfv.manufacture.Head h : dev.mcmod.hdfv.manufacture.Head.values()) {
            String word = h.kind.replace("_head", "");
            if (p.slot('h') != null && p.slot('h').takes(h.kind) && (variant.contains(h.kind) || java.util.Arrays.asList(variant.split("\\+")).contains(word))) { m.put('h', h.kind); }
        }
        if (variant.contains("bare")) {
            for (Pattern.Cell c : p.cells(0, 0, 0, 0, 1, 1)) {
                Pattern.Slot slot = p.slot(c.symbol());
                if (slot != null && slot.optional()) { m.put(c.symbol(), AIR); }
            }
        }
        return m;
    }

    /** Module choices for a CNC preset (symbol to kind). */
    public static Map<Character, String> cncModules(String preset) {
        return switch (preset) {
            case "vmc_3" -> Map.of();
            case "vmc_4" -> Map.of('W', Structures.ROTARY_TABLE);
            case "vmc_5" -> Map.of('W', Structures.TRUNNION);
            case "mill_turn_5" -> Map.of('W', Structures.TURNING_SPINDLE, 'H', Structures.B_AXIS_HEAD);
            case "mill_turn_9" -> Map.of('W', Structures.TURNING_SPINDLE, 'H', Structures.B_AXIS_HEAD, 'S', Structures.SUB_SPINDLE, 'X', Structures.TURRET);
            case "mill_turn_12" -> Map.of('W', Structures.TURNING_SPINDLE, 'H', Structures.B_AXIS_HEAD, 'S', Structures.SUB_SPINDLE, 'X', Structures.TURRET,
                    'R', Structures.Y_TURRET);
            default -> throw new IllegalArgumentException("Presets: vmc_3, vmc_4, vmc_5, mill_turn_5, mill_turn_9, mill_turn_12");
        };
    }

    public static void tab(CreativeModeTab.Output out) { for (DeferredBlock<MemberBlock> b : MEMBERS.values()) { out.accept(b.get()); } }

    public static List<String> ids() { return new ArrayList<>(MEMBERS.keySet()); }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
    }
}
