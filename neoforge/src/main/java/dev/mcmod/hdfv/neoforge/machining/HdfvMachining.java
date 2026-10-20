package dev.mcmod.hdfv.neoforge.machining;

import com.mojang.serialization.Codec;
import dev.mcmod.hdfv.manufacture.Consumable;
import dev.mcmod.hdfv.neoforge.Hdfv;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * What the machines use up (0.17; the user 2026-10-18: consumables for every machine): an item for each supply
 * (manufacture.Consumable.Supply). Durable ones (tool sets, wheels, stones, blades, electrodes, wire, gas cylinders)
 * wear by their damage (Consumable.LIFE_UNITS of life) and cannot be mended at an anvil or grindstone (a dull tool set
 * is reground on the tool grinder, up to its count of regrinds, kept as REGRINDS); counted ones (compound, electrolyte,
 * garnet, rivets) are used up a whole item at a time. A gas cylinder emptied leaves an empty cylinder.
 */
public final class HdfvMachining {
    private HdfvMachining() {}

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Hdfv.MOD_ID);
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Hdfv.MOD_ID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Hdfv.MOD_ID);
    public static final DeferredRegister<net.minecraft.world.level.block.entity.BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Hdfv.MOD_ID);

    /** How many times a tool set has been reground. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> REGRINDS = COMPONENTS.registerComponentType("regrinds",
            b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    /** A supply item: its life or its use in the tooltip. */
    public static final class SupplyItem extends Item {
        public final Consumable.Supply supply;

        public SupplyItem(Properties p, Consumable.Supply supply) { super(p); this.supply = supply; }

        @Override public void appendHoverText(ItemStack s, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            if (supply.supplies != null) {
                tooltip.add(Component.translatable("hdfv.consumable.for", Component.translatable("hdfv.consumable." + supply.supplies.id()))
                        .withStyle(net.minecraft.ChatFormatting.GRAY));
            }
            if (supply.regrinds > 0) {
                tooltip.add(Component.translatable("hdfv.consumable.regrinds", s.getOrDefault(REGRINDS.get(), 0), supply.regrinds)
                        .withStyle(net.minecraft.ChatFormatting.GRAY));
            }
        }
    }

    public static final Map<Consumable.Supply, DeferredItem<SupplyItem>> SUPPLIES = new EnumMap<>(Consumable.Supply.class);

    static {
        for (Consumable.Supply s : Consumable.Supply.values()) {
            Item.Properties p = new Item.Properties();
            p = s.durable ? p.durability(Consumable.LIFE_UNITS).setNoRepair() : p.stacksTo(64);
            Item.Properties props = p;
            SUPPLIES.put(s, ITEMS.registerItem(s.id, q -> new SupplyItem(props, s), props));
        }
    }

    /** What a gas cylinder leaves when it is empty (refilled at the air separation unit). */
    public static final DeferredItem<Item> EMPTY_CYLINDER = ITEMS.registerSimpleItem("empty_gas_cylinder", new Item.Properties().stacksTo(16));

    /** The air separation unit: empty cylinders to oxygen, nitrogen or argon (GasSupply). */
    public static final net.neoforged.neoforge.registries.DeferredBlock<AirSeparatorBlock> AIR_SEPARATOR = BLOCKS.registerBlock("air_separation_unit", AirSeparatorBlock::new,
            net.minecraft.world.level.block.state.BlockBehaviour.Properties.of().mapColor(net.minecraft.world.level.material.MapColor.METAL).strength(3.5f, 6f)
                    .sound(net.minecraft.world.level.block.SoundType.METAL).requiresCorrectToolForDrops().noOcclusion());
    static { ITEMS.registerSimpleBlockItem("air_separation_unit", AIR_SEPARATOR); }
    public static final DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>, net.minecraft.world.level.block.entity.BlockEntityType<AirSeparatorBlockEntity>>
            AIR_SEPARATOR_ENTITY = BLOCK_ENTITIES.register("air_separation_unit",
            () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(AirSeparatorBlockEntity::new, AIR_SEPARATOR.get()).build(null));

    /** The supply a stack is (null: none). */
    public static Consumable.Supply supplyOf(ItemStack s) { return s.getItem() instanceof SupplyItem i ? i.supply : null; }

    /** A stack of a supply, new. */
    public static ItemStack stack(Consumable.Supply s, int count) { return new ItemStack(SUPPLIES.get(s).get(), count); }

    public static void tab(CreativeModeTab.Output out) {
        out.accept(AIR_SEPARATOR.get());
        for (DeferredItem<SupplyItem> i : SUPPLIES.values()) { out.accept(i.get()); }
        out.accept(EMPTY_CYLINDER.get());
    }

    /** Every item id (the recipes, names and tests). */
    public static List<String> ids() {
        List<String> out = new ArrayList<>();
        for (Consumable.Supply s : Consumable.Supply.values()) { out.add(s.id); }
        out.add("empty_gas_cylinder");
        out.add("air_separation_unit");
        return out;
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        COMPONENTS.register(modBus);
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener((net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent e) -> {
            e.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK, AIR_SEPARATOR_ENTITY.get(), (be, side) -> be.energy());
            e.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, AIR_SEPARATOR_ENTITY.get(), (be, side) -> be.handler());
        });
    }
}
