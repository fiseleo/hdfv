package dev.mcmod.hdfv.neoforge.road;

import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.road.RampShape;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Roads (0.25): the ramp blocks, in asphalt (the refinery's, HdfvRefinery.ASPHALT) and in concrete (the airfield's
 * apron concrete), rising a block over 2, 4 or 8 of run. Their assets are tools/road_assets.py's, their recipes
 * tools/recipes.py's.
 */
public final class HdfvRoads {
    private HdfvRoads() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Hdfv.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Hdfv.MOD_ID);

    /** As the pavement the ramp is made of (asphalt: HdfvRefinery.ASPHALT; concrete: HdfvAirports.CONCRETE). */
    private static BlockBehaviour.Properties pavement(String material) {
        return material.equals("asphalt")
                ? BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(1.5f, 6f).sound(SoundType.STONE).requiresCorrectToolForDrops()
                : BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GRAY).strength(2f, 6f).sound(SoundType.STONE).requiresCorrectToolForDrops();
    }

    /** The ramps by id (asphalt_ramp_2 ... concrete_ramp_8). */
    public static final Map<String, DeferredBlock<RoadRampBlock>> RAMPS = new LinkedHashMap<>();

    static {
        for (String material : RampShape.MATERIALS) {
            for (int gradient : RampShape.GRADIENTS) {
                String id = RampShape.id(material, gradient);
                DeferredBlock<RoadRampBlock> b = BLOCKS.register(id, () -> RoadRampBlock.of(gradient, pavement(material)));
                ITEMS.registerSimpleBlockItem(id, b);
                RAMPS.put(id, b);
            }
        }
    }

    public static DeferredBlock<RoadRampBlock> ramp(String material, int gradient) { return RAMPS.get(RampShape.id(material, gradient)); }

    public static void tab(CreativeModeTab.Output out) {
        for (DeferredBlock<RoadRampBlock> b : RAMPS.values()) { out.accept(b.get()); }
    }

    /** The ids of the blocks (each has an item, a recipe, a drop and names). */
    public static List<String> ids() { return List.copyOf(RAMPS.keySet()); }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
    }
}
