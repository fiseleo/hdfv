package dev.mcmod.hdfv.neoforge.machine;

import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.hdfv.design.Designs;
import dev.mcmod.hdfv.manufacture.Operation;
import dev.mcmod.hdfv.manufacture.Station;
import dev.mcmod.hdfv.neoforge.Hdfv;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The workshop (0.7): a block for every station of the plan but the assembly room (a structure), one block entity
 * and menu for all of them, the workpiece and finished part items.
 */
public final class HdfvMachines {
    private HdfvMachines() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Hdfv.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Hdfv.MOD_ID);
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Hdfv.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Hdfv.MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Hdfv.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Workpieces.Data>> WORKPIECE_DATA = COMPONENTS.registerComponentType("workpiece",
            b -> b.persistent(Workpieces.Data.CODEC).networkSynchronized(Workpieces.Data.STREAM_CODEC));
    /** 0.25: what a workpiece weighs, set when it is made (the item does not carry its drawing; storage.ItemMass reads it). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Workpieces.Kg>> WORKPIECE_KG = COMPONENTS.registerComponentType("workpiece_kg",
            b -> b.persistent(Workpieces.Kg.CODEC).networkSynchronized(Workpieces.Kg.STREAM_CODEC));

    public static final Map<Station, DeferredBlock<MachineBlock>> MACHINES = new LinkedHashMap<>();

    static {
        for (Station s : Station.values()) {
            if (s == Station.ASSEMBLY_ROOM) { continue; }
            boolean wood = s == Station.ASSEMBLY_BENCH;
            BlockBehaviour.Properties p = BlockBehaviour.Properties.of().mapColor(wood ? MapColor.WOOD : MapColor.METAL).strength(3.5f)
                    .sound(wood ? SoundType.WOOD : SoundType.METAL).noOcclusion();
            if (!wood) { p = p.requiresCorrectToolForDrops(); }
            BlockBehaviour.Properties props = p;
            DeferredBlock<MachineBlock> b = BLOCKS.register(MachineBlock.id(s), () -> new MachineBlock(props, s));
            MACHINES.put(s, b);
            ITEMS.registerSimpleBlockItem(MachineBlock.id(s), b);
        }
    }

    /** The manual machine tools' drives (0.9): the electric motor seat and the line shaft's pulley seat. */
    public static final Map<Boolean, DeferredBlock<DriveBlock>> DRIVES = new LinkedHashMap<>();

    static {
        for (boolean electric : new boolean[]{true, false}) {
            String id = electric ? dev.mcmod.hdfv.structure.Structures.MOTOR_DRIVE : dev.mcmod.hdfv.structure.Structures.PULLEY_DRIVE;
            DeferredBlock<DriveBlock> b = BLOCKS.register(id, () -> new DriveBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5f)
                    .sound(SoundType.METAL).requiresCorrectToolForDrops(), electric));
            DRIVES.put(electric, b);
            ITEMS.registerSimpleBlockItem(id, b);
        }
    }

    /** The NC drive (0.17): a motor seat with its numerical control; the machine needs no operator. */
    public static final DeferredBlock<DriveBlock> NC_DRIVE = BLOCKS.register(dev.mcmod.hdfv.structure.Structures.NC_DRIVE,
            () -> new DriveBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5f).sound(SoundType.METAL).requiresCorrectToolForDrops(), true, true));
    static { ITEMS.registerSimpleBlockItem(dev.mcmod.hdfv.structure.Structures.NC_DRIVE, NC_DRIVE); }

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DriveBlockEntity>> DRIVE_ENTITY = BLOCK_ENTITIES.register("drive",
            () -> BlockEntityType.Builder.of(DriveBlockEntity::new, DRIVES.get(true).get(), DRIVES.get(false).get(), NC_DRIVE.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MachineBlockEntity>> MACHINE_ENTITY = BLOCK_ENTITIES.register("machine",
            () -> BlockEntityType.Builder.of(MachineBlockEntity::new, MACHINES.values().stream().map(DeferredBlock::get).toArray(MachineBlock[]::new)).build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<MachineMenu>> MACHINE_MENU = MENUS.register("machine", () -> IMenuTypeExtension.create(MachineMenu::new));

    /** A workpiece: named by its design and component, its route's progress in the tooltip. */
    public static final class WorkpieceItem extends Item {
        public WorkpieceItem(Properties p) { super(p); }

        @Override public Component getName(ItemStack s) {
            Workpieces.Data d = s.get(WORKPIECE_DATA.get());
            if (d == null) { return super.getName(s); }
            return Component.translatable("item.hdfv.workpiece.named", d.design(), Component.translatable(dev.mcmod.hdfv.manufacture.Workpiece.nameKey(d.key())));
        }

        @Override public void appendHoverText(ItemStack s, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            Workpieces.Data d = s.get(WORKPIECE_DATA.get());
            if (d == null) { return; }
            tooltip.add(Component.translatable(d.finished() ? "hdfv.workpiece.finished" : "hdfv.workpiece.step", d.step(), d.steps())
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
            if (d.older()) { tooltip.add(Component.translatable("hdfv.workpiece.older").withStyle(net.minecraft.ChatFormatting.YELLOW)); }
        }
    }

    /** A finished part: named by its design. */
    public static final class PartItem extends Item {
        public PartItem(Properties p) { super(p); }

        @Override public Component getName(ItemStack s) {
            PartDesign d = Workpieces.partDesign(s);
            return d == null ? super.getName(s) : Component.translatable("item.hdfv.part.named", d.name());
        }

        @Override public void appendHoverText(ItemStack s, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            PartDesign d = Workpieces.partDesign(s);
            if (d != null) { tooltip.add(Component.translatable("hdfv.kind." + Designs.kindOf(d).id()).withStyle(net.minecraft.ChatFormatting.GRAY)); }
        }
    }

    public static final DeferredItem<WorkpieceItem> WORKPIECE = ITEMS.registerItem("workpiece", WorkpieceItem::new, new Item.Properties());
    public static final DeferredItem<PartItem> PART = ITEMS.registerItem("part", PartItem::new, new Item.Properties().stacksTo(1));

    /** The process a step is, for the screen (0.17: with its feature, "Grinding" on a bore is honing). */
    public static Component process(Operation op) {
        String p = "hdfv.process." + op.process().name().toLowerCase(Locale.ROOT);
        return op.feature() == dev.mcmod.hdfv.manufacture.Feature.NONE ? Component.translatable(p)
                : Component.translatable(p + "." + op.feature().name().toLowerCase(Locale.ROOT));
    }

    /** The workshop in the creative tab: the machines, a sample workpiece and part. */
    public static void tab(CreativeModeTab.Output out) {
        for (DeferredBlock<MachineBlock> b : MACHINES.values()) { out.accept(b.get()); }
        for (DeferredBlock<DriveBlock> b : DRIVES.values()) { out.accept(b.get()); }
        out.accept(NC_DRIVE.get());
        PartDesign sample = Designs.sample("inline_four");
        var plan = Workpieces.plan(sample);
        out.accept(Workpieces.start(sample, plan.workpieces().get(0), 1));
        out.accept(Workpieces.part(sample));
    }

    public static List<String> ids() {
        List<String> out = new ArrayList<>(List.of("workpiece", "part"));
        for (Station s : MACHINES.keySet()) { out.add(MachineBlock.id(s)); }
        out.add(dev.mcmod.hdfv.structure.Structures.MOTOR_DRIVE);
        out.add(dev.mcmod.hdfv.structure.Structures.PULLEY_DRIVE);
        out.add(dev.mcmod.hdfv.structure.Structures.NC_DRIVE);
        return out;
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        COMPONENTS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        modBus.addListener((net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent e) -> {
            e.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, MACHINE_ENTITY.get(), (be, side) -> be.handler());
            e.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK, MACHINE_ENTITY.get(), (be, side) -> be.energyHandler());
            e.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK, DRIVE_ENTITY.get(), (be, side) -> be.energy());
        });
    }
}
