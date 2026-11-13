package dev.mcmod.hdfv.neoforge.storage;

import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.neoforge.vehicle.PartVehicle;
import dev.mcmod.hdfv.neoforge.Hdfv;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.List;
import java.util.function.Supplier;

/**
 * Vehicle cargo (0.25): the attachment that holds what each vehicle carries (VehicleCargo), the cargo loader block,
 * the network of the cargo screens (CargoNetwork) and where cargo goes when a vehicle changes or goes:
 * <ul>
 * <li>every server tick a vehicle whose compiled vehicle changed is laid out again (VehicleCargo.check: overflow drops
 *     where it is; the refit shop takes it into its stores itself);</li>
 * <li>parts cast off (PartVehicle.Jettisoned: a storage box) drop their holds' cargo where they fell;</li>
 * <li>a vehicle killed or discarded drops its cargo at its holds; one that fell out of the world (below the world's
 *     bottom by 64 blocks, where vanilla discards entities) drops nothing; one unloaded or changing dimension keeps
 *     it. An assembly room dismantling a vehicle takes its cargo into the room's stores first.</li>
 * </ul>
 * The loader's assets are tools/cargo_assets.py's, its recipe tools/recipes.py's.
 */
public final class HdfvCargo {
    private HdfvCargo() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Hdfv.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Hdfv.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Hdfv.MOD_ID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Hdfv.MOD_ID);

    /** What a vehicle carries in its holds. */
    public static final Supplier<AttachmentType<VehicleCargo>> CARGO = ATTACHMENTS.register("vehicle_cargo",
            () -> AttachmentType.builder(VehicleCargo::new).serialize(new VehicleCargo.Serializer()).build());

    public static final DeferredBlock<CargoLoaderBlock> LOADER = BLOCKS.register("cargo_loader", () -> new CargoLoaderBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5f, 6f).sound(SoundType.METAL).requiresCorrectToolForDrops()));
    static { ITEMS.registerSimpleBlockItem("cargo_loader", LOADER); }

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CargoLoaderBlockEntity>> LOADER_ENTITY = BLOCK_ENTITIES.register("cargo_loader",
            () -> BlockEntityType.Builder.of(CargoLoaderBlockEntity::new, LOADER.get()).build(null));

    public static void tab(CreativeModeTab.Output out) { out.accept(LOADER.get()); }

    /** The ids of the blocks (each has an item, a recipe, a drop and names). */
    public static List<String> ids() { return List.of("cargo_loader"); }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        ATTACHMENTS.register(modBus);
        modBus.addListener((RegisterCapabilitiesEvent e) -> e.registerBlockEntity(Capabilities.ItemHandler.BLOCK, LOADER_ENTITY.get(), (be, side) -> be.handler()));
        modBus.addListener(CargoNetwork::register);
        NeoForge.EVENT_BUS.addListener(HdfvCargo::tick);
        NeoForge.EVENT_BUS.addListener(HdfvCargo::left);
        NeoForge.EVENT_BUS.addListener(HdfvCargo::jettisoned);
    }

    private static void tick(EntityTickEvent.Post e) {
        if (e.getEntity() instanceof PartVehicle v && !v.level().isClientSide) { VehicleCargo.check(v); }
    }

    /** Whether an entity went below the world's bottom (where vanilla discards it). */
    static boolean fellOut(Entity e) { return e.getY() < e.level().getMinBuildHeight() - 64; }

    private static void left(EntityLeaveLevelEvent e) {
        if (!(e.getEntity() instanceof PartVehicle v) || e.getLevel().isClientSide) { return; }
        Entity.RemovalReason why = v.getRemovalReason();
        if (why == null || !why.shouldDestroy() || fellOut(v)) { return; }
        VehicleCargo.dropAll(v);
    }

    private static void jettisoned(PartVehicle.Jettisoned e) {
        PartVehicle v = e.vehicle();
        if (v.level().isClientSide) { return; }
        for (PartVehicle.Jettisoned.Dropped d : e.parts()) {
            Vec3d at = d.position();
            VehicleCargo.unloadPart(v, d.entry().id(), s -> VehicleCargo.drop(v, s, at));
        }
        VehicleCargo.check(v);
    }
}
