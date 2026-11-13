package dev.mcmod.hdfv.neoforge.storage;

import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.hdfv.manufacture.Alloy;
import dev.mcmod.hdfv.manufacture.Consumable;
import dev.mcmod.hdfv.manufacture.GasSupply;
import dev.mcmod.hdfv.manufacture.Workpiece;
import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.neoforge.HdfvConfig;
import dev.mcmod.hdfv.neoforge.HdfvRegistry;
import dev.mcmod.hdfv.neoforge.design.Blueprints;
import dev.mcmod.hdfv.neoforge.fluid.HdfvFluidRegistry;
import dev.mcmod.hdfv.neoforge.fluid.JerryCanItem;
import dev.mcmod.hdfv.neoforge.fuel.HdfvFuel;
import dev.mcmod.hdfv.neoforge.machine.HdfvMachines;
import dev.mcmod.hdfv.neoforge.machine.Workpieces;
import dev.mcmod.hdfv.neoforge.machining.HdfvMachining;
import dev.mcmod.hdfv.neoforge.material.HdfvComponents;
import dev.mcmod.hdfv.neoforge.material.HdfvMaterials;
import dev.mcmod.hdfv.neoforge.material.Stock;
import dev.mcmod.hdfv.neoforge.material.TechStages;
import dev.mcmod.hdfv.part.component.Component;
import dev.mcmod.hdfv.part.engine.SampleEngines;
import dev.mcmod.hdfv.part.engine.SolidRocketDesign;
import dev.mcmod.hdfv.power.PowerPlant;
import dev.mcmod.hdfv.recipe.Intermediate;
import dev.mcmod.hdfv.recipe.TechTree;
import dev.mcmod.hdfv.storage.CargoMass;
import dev.mcmod.hdfv.storage.StorageKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;
import java.util.Map;

/**
 * Storage in the world (0.25): what items weigh (each of ItemMass's rules, the data map's tags and single items, the
 * fallback, contents and the server's scale); the four storage blocks with their recipes, drops (themselves named,
 * their contents beside them), names and tech stages; a hopper filling and emptying a crate; the parts rack refusing
 * what is not a part from a hopper and from its menu; the menu's footer and pages. (A car factory fed from a rack and a
 * locker is AssemblyGameTests'.)
 */
@PrefixGameTestTemplate(false)
public final class StorageGameTests {
    private StorageGameTests() {}

    private static final String BATCH = "hdfv_storage";

    static void floor(GameTestHelper h) {
        for (int x = 0; x < 12; x++) for (int z = 0; z < 12; z++) { h.setBlock(new BlockPos(x, 0, z), Blocks.STONE); }
    }

    private static void near(GameTestHelper h, double is, double want, String what) {
        h.assertTrue(Math.abs(is - want) <= 1e-6 * Math.max(1, Math.abs(want)), what + ": " + is + " kg, not " + want);
    }

    private static ItemStack holding(Item box, ItemStack... in) {
        ItemStack s = new ItemStack(box);
        s.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(in)));
        return s;
    }

    @GameTest(templateNamespace = "hdfv", template = "helipad", batch = BATCH)
    public static void itemsWeighWhatTheirRulesSay(GameTestHelper h) {
        double ingot = HdfvConfig.kgPerIngot();
        // hdfv's own rules
        near(h, ItemMass.kg(Stock.of(Alloy.STEEL, 3)), 3 * ingot, "Three steel stock");
        near(h, ItemMass.kg(Stock.bits(Alloy.BRONZE, 4)), 4 * ingot / 10, "Four bronze bits");
        near(h, ItemMass.kgEach(new ItemStack(HdfvMaterials.MATERIAL_ITEMS.get("aluminium_ingot").get())), ingot, "An aluminium ingot");
        near(h, ItemMass.kgEach(new ItemStack(HdfvMaterials.MATERIAL_ITEMS.get("bauxite").get())), ingot, "Bauxite");
        near(h, ItemMass.kgEach(new ItemStack(HdfvMaterials.MATERIAL_ITEMS.get("coke").get())), PowerPlant.COKE.kg(), "Coke");
        near(h, ItemMass.kgEach(new ItemStack(HdfvMaterials.MATERIAL_ITEMS.get("epoxy_resin").get())), dev.mcmod.hdfv.petroleum.Refining.ITEM_KG.get("epoxy_resin"), "Epoxy resin");
        near(h, ItemMass.kgEach(new ItemStack(dev.mcmod.hdfv.neoforge.petroleum.HdfvRefinery.BITUMEN.get())), dev.mcmod.hdfv.petroleum.Refining.ITEM_KG.get("bitumen"), "Bitumen");
        for (Component c : Component.values()) { near(h, ItemMass.kgEach(HdfvComponents.stack(c, 1)), c.kg, "The component " + c.id()); }
        for (Intermediate i : Intermediate.values()) { near(h, ItemMass.kgEach(HdfvComponents.stack(i, 1)), i.kg, "The intermediate " + i.id()); }
        near(h, ItemMass.kg(HdfvComponents.stack(Component.BALL_BEARING, 16)), 16 * Component.BALL_BEARING.kg, "Sixteen bearings");
        near(h, ItemMass.kgEach(new ItemStack(HdfvFuel.KNSB_GRAIN.get())), SolidRocketDesign.GRAIN_ITEM_KG, "A propellant grain");
        // a finished part: its design's mass
        PartDesign engine = SampleEngines.inlineFour();
        double engineKg = engine.compile().mass().massKg();
        h.assertTrue(engineKg > 50, "The sample engine weighs something: " + engineKg);
        near(h, ItemMass.kgEach(Workpieces.part(engine)), engineKg, "The engine as a part");
        near(h, ItemMass.kgEach(Workpieces.part(engine)), engineKg, "The engine again (from the kept masses)");
        // a workpiece: its stock after the first step, its finished mass at the end, evenly between; an unmarked one a stock
        Workpiece piece = null;
        for (Workpiece w : Workpieces.plan(engine).workpieces()) { if (w.route().size() >= 3 && w.stockKg() > w.finishedKg() + 0.01) { piece = w; break; } }
        h.assertTrue(piece != null, "A workpiece of the engine with three steps that loses metal");
        int steps = piece.route().size();
        near(h, ItemMass.kgEach(Workpieces.start(engine, piece, 1)), (float) piece.stockKg(), piece.key() + " after its first step");
        near(h, ItemMass.kgEach(Workpieces.start(engine, piece, steps)), (float) piece.finishedKg(), piece.key() + " finished");
        double mid = ItemMass.kgEach(Workpieces.start(engine, piece, 2));
        h.assertTrue(mid < (float) piece.stockKg() && mid > (float) piece.finishedKg(), piece.key() + " between: " + mid);
        near(h, mid, CargoMass.workpieceKg((float) piece.stockKg(), (float) piece.finishedKg(), 2, steps), piece.key() + " at its second step");
        ItemStack unmarked = Workpieces.start(engine, piece, 1);
        unmarked.remove(HdfvMachines.WORKPIECE_KG.get());
        near(h, ItemMass.kgEach(unmarked), ingot, "A workpiece made before 0.25");
        // a gas cylinder: the empty one and the gas left; a jerry can: what it holds, a litre a millibucket; a vanilla
        // bucket: a real bucket's 10 litres (storage.bucketLitres), not the cubic metre hdfv's count would make it
        double cylinder = ItemMass.kgEach(new ItemStack(HdfvMachining.EMPTY_CYLINDER.get())), oxygen = GasSupply.Product.OXYGEN.cylinderKg();
        h.assertTrue(cylinder > 1 && oxygen > 10, "An empty cylinder " + cylinder + " kg, its oxygen " + oxygen);
        ItemStack full = HdfvMachining.stack(Consumable.Supply.OXYGEN, 1), half = full.copy();
        half.setDamageValue(half.getMaxDamage() / 2);
        near(h, ItemMass.kgEach(full), cylinder + oxygen, "A full oxygen cylinder");
        near(h, ItemMass.kgEach(half), cylinder + oxygen / 2, "A half-used oxygen cylinder");
        double can = ItemMass.kgEach(new ItemStack(HdfvFluidRegistry.JERRY_CAN.get()));
        double diesel = dev.mcmod.hdfv.fluid.HdfvFluids.of("diesel").densityKgPerM3();
        near(h, ItemMass.kgEach(JerryCanItem.filled(HdfvFluidRegistry.fluid("diesel"), 20)), can + 0.020 * diesel, "A jerry can of diesel");
        double water = dev.mcmod.hdfv.fluid.HdfvFluids.WATER.densityKgPerM3(), bucket = ItemMass.kgEach(new ItemStack(Items.BUCKET));
        h.assertTrue(HdfvConfig.bucketLitres() == HdfvConfig.DEFAULT_BUCKET_LITRES && HdfvConfig.DEFAULT_BUCKET_LITRES == 10, "A bucket is 10 litres by default");
        near(h, ItemMass.kgEach(new ItemStack(Items.WATER_BUCKET)), bucket + 0.010 * water, "A bucket of water: the bucket and 10 litres");
        h.assertTrue(ItemMass.kgEach(new ItemStack(Items.WATER_BUCKET)) < 50, "A bucket of water is something a player carries: " + ItemMass.kgEach(new ItemStack(Items.WATER_BUCKET)));
        @SuppressWarnings("unchecked")
        ModConfigSpec.ConfigValue<Double> litres = (ModConfigSpec.ConfigValue<Double>) HdfvConfig.SPEC.getValues().get("storage.bucketLitres");
        h.assertTrue(litres != null, "The server's storage.bucketLitres");
        double was = litres.get();
        try {
            litres.set(1000.0);
            near(h, ItemMass.kgEach(new ItemStack(Items.WATER_BUCKET)), bucket + water, "At 1000 litres a bucket is a cubic metre");
            near(h, ItemMass.kgEach(JerryCanItem.filled(HdfvFluidRegistry.fluid("diesel"), 20)), can + 0.020 * diesel, "hdfv's jerry can is not scaled");
            litres.set(0.0);
            near(h, ItemMass.kgEach(new ItemStack(Items.WATER_BUCKET)), bucket, "At 0 litres a bucket's water weighs nothing");
        } finally {
            litres.set(was);
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "hdfv", template = "helipad", batch = BATCH)
    public static void theDataMapWeighsTheRest(GameTestHelper h) {
        // tags, on the workshop's scale (the steam engine's fuels are the same numbers)
        near(h, ItemMass.kgEach(new ItemStack(Items.IRON_INGOT)), 10, "An iron ingot (#c:ingots)");
        near(h, ItemMass.kgEach(new ItemStack(Items.RAW_COPPER)), 10, "Raw copper (#c:raw_materials)");
        near(h, ItemMass.kgEach(new ItemStack(Items.IRON_NUGGET)), 1.1, "An iron nugget (#c:nuggets)");
        near(h, ItemMass.kgEach(new ItemStack(Items.IRON_BLOCK)), 90, "A block of iron (#c:storage_blocks)");
        near(h, ItemMass.kgEach(new ItemStack(Items.OAK_LOG)), PowerPlant.LOG.kg(), "A log (#minecraft:logs)");
        near(h, ItemMass.kgEach(new ItemStack(Items.BIRCH_PLANKS)), PowerPlant.PLANK.kg(), "A plank (#minecraft:planks)");
        near(h, ItemMass.kgEach(new ItemStack(Items.STICK)), PowerPlant.STICK.kg(), "A stick (#c:rods/wooden)");
        near(h, ItemMass.kgEach(new ItemStack(Items.COAL)), PowerPlant.COAL.kg(), "Coal");
        near(h, ItemMass.kgEach(new ItemStack(Items.CHARCOAL)), PowerPlant.CHARCOAL.kg(), "Charcoal");
        near(h, ItemMass.kgEach(new ItemStack(Items.STONE)), 20, "Stone (#c:stones)");
        near(h, ItemMass.kgEach(new ItemStack(Items.SAND)), 15, "Sand (#c:sands)");
        near(h, ItemMass.kgEach(new ItemStack(Items.GLASS)), 10, "Glass (#c:glass_blocks)");
        near(h, ItemMass.kgEach(new ItemStack(Items.WHITE_WOOL)), 1, "Wool (#minecraft:wool)");
        near(h, ItemMass.kgEach(new ItemStack(Items.DIAMOND)), 2, "A diamond (#c:gems)");
        near(h, ItemMass.kgEach(new ItemStack(Items.REDSTONE)), 1, "Redstone (#c:dusts)");
        near(h, ItemMass.kgEach(new ItemStack(Items.BREAD)), 0.3, "Bread (#c:foods)");
        // a narrower tag after a wider one, a single item after its tag
        near(h, ItemMass.kgEach(new ItemStack(Items.COAL_BLOCK)), PowerPlant.COAL_BLOCK.kg(), "A block of coal (#c:storage_blocks/coal over #c:storage_blocks)");
        near(h, ItemMass.kgEach(new ItemStack(Items.STONE_SLAB)), 10, "A stone slab (#minecraft:slabs)");
        near(h, ItemMass.kgEach(new ItemStack(Items.OAK_SLAB)), 0.5, "A wooden slab (#minecraft:wooden_slabs over #minecraft:slabs)");
        near(h, ItemMass.kgEach(new ItemStack(Items.IRON_PICKAXE)), 30, "An iron pickaxe (#minecraft:pickaxes: three ingots)");
        near(h, ItemMass.kgEach(new ItemStack(Items.WOODEN_PICKAXE)), 3, "A wooden pickaxe (its own entry over #minecraft:pickaxes)");
        near(h, ItemMass.kgEach(new ItemStack(Items.IRON_CHESTPLATE)), 80, "An iron chestplate (eight ingots)");
        near(h, ItemMass.kgEach(new ItemStack(Items.HOPPER)), 58, "A hopper (its own entry)");
        near(h, ItemMass.kgEach(new ItemStack(Items.BUCKET)), 30, "A bucket (#c:buckets: three ingots)");
        // hdfv's blocks and other items: from their recipes (tools/item_mass.py)
        near(h, ItemMass.kgEach(new ItemStack(HdfvStorage.block(StorageKind.STEEL_LOCKER).get())), 5 * Component.STEEL_PLATE.kg + Component.FASTENER_KIT.kg, "A steel locker (five plates and a fastener kit)");
        near(h, ItemMass.kgEach(new ItemStack(HdfvStorage.block(StorageKind.STORAGE_CRATE).get())), 8.2, "A crate (six planks and two nuggets)");
        near(h, ItemMass.kgEach(new ItemStack(HdfvMachines.MACHINES.get(dev.mcmod.hdfv.manufacture.Station.LATHE).get())), 90, "A lathe");
        near(h, ItemMass.kgEach(new ItemStack(HdfvMaterials.ORE_BLOCKS.get("bauxite_ore").get())), 20, "Bauxite ore");
        // every hdfv item weighs something by a rule or the map (the fallback is for other mods' things)
        for (Item item : BuiltInRegistries.ITEM) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (!id.getNamespace().equals(Hdfv.MOD_ID)) { continue; }
            boolean listed = item.builtInRegistryHolder().getData(ItemMass.DATA_MAP) != null;
            h.assertTrue(listed || ItemMass.ruled(item), id + " has neither a rule nor an entry in hdfv:item_mass");
            h.assertTrue(ItemMass.kgEach(new ItemStack(item)) > 0, id + " weighs nothing");
        }
        // what nothing lists
        h.assertTrue(Items.NETHER_STAR.builtInRegistryHolder().getData(ItemMass.DATA_MAP) == null && Items.BEDROCK.builtInRegistryHolder().getData(ItemMass.DATA_MAP) == null,
                "Neither a nether star nor bedrock is listed");
        near(h, ItemMass.kgEach(new ItemStack(Items.NETHER_STAR)), CargoMass.FALLBACK_ITEM_KG, "An unlisted item");
        near(h, ItemMass.kgEach(new ItemStack(Items.BEDROCK)), CargoMass.FALLBACK_BLOCK_KG, "An unlisted block");
        near(h, ItemMass.kg(new ItemStack(Items.OAK_PLANKS, 64)), 64, "A stack of planks");
        near(h, ItemMass.kg(ItemStack.EMPTY), 0, "Nothing");
        h.succeed();
    }

    @GameTest(templateNamespace = "hdfv", template = "helipad", batch = BATCH)
    public static void itemsWeighWhatTheyHoldAndTheServersScaleApplies(GameTestHelper h) {
        double box = ItemMass.kgEach(new ItemStack(Items.SHULKER_BOX)), bundle = ItemMass.kgEach(new ItemStack(Items.BUNDLE));
        near(h, box, 9, "An empty shulker box");
        ItemStack sack = new ItemStack(Items.BUNDLE);
        sack.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(List.of(new ItemStack(Items.COAL, 3))));
        near(h, ItemMass.kgEach(sack), bundle + 3, "A bundle of three coal");
        ItemStack packed = holding(Items.SHULKER_BOX, new ItemStack(Items.IRON_INGOT, 2), sack, Stock.of(Alloy.STEEL, 5));
        near(h, ItemMass.kgEach(packed), box + 20 + bundle + 3 + 5 * HdfvConfig.kgPerIngot(), "A shulker box of ingots, a bundle and stock");
        // a storage block picked with what is in it (creative), stacked: each weighs its contents
        ItemStack crates = holding(HdfvStorage.block(StorageKind.STORAGE_CRATE).get().asItem(), new ItemStack(Items.OAK_LOG, 10));
        crates.setCount(2);
        near(h, ItemMass.kg(crates), 2 * (8.2 + 40), "Two crates of ten logs");
        // contents are weighed CargoMass.MAX_DEPTH deep: of seven chests one in the other, the first five
        double chest = ItemMass.kgEach(new ItemStack(Items.CHEST));
        ItemStack nested = new ItemStack(Items.CHEST);
        for (int i = 0; i < 6; i++) { nested = holding(Items.CHEST, nested); }
        near(h, ItemMass.kgEach(nested), (CargoMass.MAX_DEPTH + 1) * chest, "Seven chests in one another");
        // the server's scale
        h.assertTrue(HdfvConfig.itemMassScale() == HdfvConfig.DEFAULT_ITEM_MASS_SCALE, "The scale starts at 1: " + HdfvConfig.itemMassScale());
        @SuppressWarnings("unchecked")
        ModConfigSpec.ConfigValue<Double> scale = (ModConfigSpec.ConfigValue<Double>) HdfvConfig.SPEC.getValues().get("storage.itemMassScale");
        h.assertTrue(scale != null, "The server's storage.itemMassScale");
        double before = scale.get();
        try {
            scale.set(2.5);
            near(h, ItemMass.kgEach(packed), 2.5 * (box + 20 + bundle + 3 + 5 * HdfvConfig.kgPerIngot()), "At 2.5 times");
            near(h, ItemMass.kg(new ItemStack(Items.IRON_INGOT, 8)), 200, "Eight ingots at 2.5 times");
            scale.set(0.0);
            near(h, ItemMass.kg(packed), 0, "Weightless");
            near(h, ItemMass.kg(Workpieces.part(SampleEngines.inlineFour())), 0, "A weightless engine");
        } finally {
            scale.set(before);
        }
        near(h, ItemMass.kg(new ItemStack(Items.IRON_INGOT, 8)), 80, "Eight ingots again");
        h.succeed();
    }

    @GameTest(templateNamespace = "hdfv", template = "helipad", batch = BATCH)
    public static void theStorageBlocksAreMadeDroppedAndNamed(GameTestHelper h) throws java.io.IOException {
        floor(h);
        var recipes = h.getLevel().getRecipeManager().getRecipes().stream().filter(r -> r.id().getNamespace().equals(Hdfv.MOD_ID)).map(r -> r.id().getPath()).toList();
        h.assertTrue(HdfvStorage.ids().equals(List.of("storage_crate", "steel_locker", "parts_rack", "shipping_container")), "The storage blocks: " + HdfvStorage.ids());
        CreativeModeTab tab = HdfvRegistry.TAB.get();
        tab.buildContents(new CreativeModeTab.ItemDisplayParameters(h.getLevel().enabledFeatures(), false, h.getLevel().registryAccess()));
        BlockPos p = new BlockPos(4, 2, 4);
        Map<StorageKind, Integer> slots = Map.of(StorageKind.STORAGE_CRATE, 27, StorageKind.STEEL_LOCKER, 54, StorageKind.PARTS_RACK, 36, StorageKind.SHIPPING_CONTAINER, 108);
        for (StorageKind kind : StorageKind.values()) {
            String id = kind.id();
            Block b = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath(Hdfv.MOD_ID, id));
            h.assertTrue(b instanceof StorageBlock sb && sb.kind == kind && b == HdfvStorage.block(kind).get(), "A storage block " + id);
            h.assertTrue(recipes.contains(id), "A recipe for the " + id);
            h.assertTrue(tab.getDisplayItems().stream().anyMatch(s -> s.is(b.asItem())), "The hdfv tab shows the " + id);
            BlockState state = b.defaultBlockState().setValue(StorageBlock.FACING, Direction.EAST);
            h.setBlock(p, state);
            h.assertTrue(state.is(kind.wooden ? BlockTags.MINEABLE_WITH_AXE : BlockTags.MINEABLE_WITH_PICKAXE), id + " has its tool");
            h.assertTrue(state.isCollisionShapeFullBlock(h.getLevel(), h.absolutePos(p)), id + " is a whole block");
            StorageBlockEntity be = (StorageBlockEntity) h.getBlockEntity(p);
            h.assertTrue(be.kind == kind && be.getContainerSize() == slots.get(kind) && kind.slots == slots.get(kind), id + " holds " + be.getContainerSize());
            // the same items from every side, and from none (as the rooms ask)
            for (Direction side : new Direction[]{null, Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
                IItemHandler handler = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(p), side);
                h.assertTrue(handler != null && handler.getSlots() == kind.slots, id + " gives its items from " + side);
            }
            // named as a chest is: the item's name is the store's, and comes back on the item it drops
            ItemStack named = new ItemStack(b);
            named.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Spares " + id));
            be.applyComponentsFromItemStack(named);
            h.assertTrue(be.getDisplayName().getString().equals("Spares " + id), id + " shows its name: " + be.getDisplayName().getString());
            h.assertTrue(state.getAnalogOutputSignal(h.getLevel(), h.absolutePos(p)) == 0, id + " empty: no comparator signal");
            ItemStack first = HdfvComponents.stack(Component.CAST_GEAR, 7), last = Workpieces.part(SampleEngines.inlineFour());
            be.setItem(0, first.copy());
            be.setItem(kind.slots - 1, last.copy());
            h.assertTrue(state.getAnalogOutputSignal(h.getLevel(), h.absolutePos(p)) > 0, id + " holding something: a comparator signal");
            near(h, be.massKg(), ItemMass.kg(first) + ItemMass.kg(last), id + "'s contents");
            var drops = Block.getDrops(h.getBlockState(p), h.getLevel(), h.absolutePos(p), be);
            h.assertTrue(drops.size() == 1 && drops.get(0).is(b.asItem()), id + " drops itself: " + drops);
            h.assertTrue(drops.get(0).getHoverName().getString().equals("Spares " + id), id + " keeps its name: " + drops.get(0).getHoverName().getString());
            h.assertTrue(!drops.get(0).getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).nonEmptyItems().iterator().hasNext(), id + "'s item is empty");
            // saved and read back
            var tag = be.saveWithoutMetadata(h.getLevel().registryAccess());
            StorageBlockEntity copy = new StorageBlockEntity(be.getBlockPos(), state);
            copy.loadWithComponents(tag, h.getLevel().registryAccess());
            h.assertTrue(ItemStack.matches(copy.getItem(0), first) && ItemStack.matches(copy.getItem(kind.slots - 1), last) && copy.getDisplayName().getString().equals("Spares " + id),
                    id + " is saved with its items and its name");
            // broken, what is in it lies beside it
            h.getLevel().destroyBlock(h.absolutePos(p), false);
            List<ItemEntity> lying = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(p)).inflate(2));
            int gears = 0, engines = 0;
            for (ItemEntity e : lying) {
                if (ItemStack.isSameItemSameComponents(e.getItem(), first)) { gears += e.getItem().getCount(); }
                if (ItemStack.isSameItemSameComponents(e.getItem(), last)) { engines += e.getItem().getCount(); }
                e.discard();
            }
            h.assertTrue(gears == 7 && engines == 1, id + " broken leaves its contents: " + gears + " gears, " + engines + " engines");
        }
        // the tech tree: the crate from the start, the steel ones once there are plate, tube and fasteners
        Map<String, TechTree.Stage> stages = TechStages.of(h.getLevel().getRecipeManager(), h.getLevel().registryAccess());
        int crate = stages.get("hdfv:storage_crate").tier(), locker = stages.get("hdfv:steel_locker").tier(), rack = stages.get("hdfv:parts_rack").tier(),
                container = stages.get("hdfv:shipping_container").tier();
        h.assertTrue(crate == 0 && locker > crate && rack >= locker && container >= locker && container >= stages.get("hdfv:steel_plate").tier(),
                "Stages: crate " + crate + ", locker " + locker + ", rack " + rack + ", container " + container);
        for (String file : List.of("en_us", "zh_tw", "zh_cn")) {
            try (var in = Hdfv.class.getResourceAsStream("/assets/hdfv/lang/" + file + ".json")) {
                var lang = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                for (String id : HdfvStorage.ids()) {
                    h.assertTrue(lang.has("block.hdfv." + id), file + " lacks block " + id);
                    h.assertTrue(lang.has("hdfv.jei." + id), file + " lacks the JEI line of " + id);
                }
                for (String k : List.of("hdfv.storage.footer", "hdfv.storage.footer.limit", "hdfv.storage.page", "hdfv.storage.mass", "hdfv.storage.mass.stack",
                        "hdfv.configuration.storage", "hdfv.configuration.itemMassScale", "hdfv.configuration.bucketLitres")) {
                    h.assertTrue(lang.has(k), file + " lacks " + k);
                }
            }
        }
        h.succeed();
    }

    private static HopperBlockEntity hopper(GameTestHelper h, BlockPos at) {
        h.setBlock(at, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        return (HopperBlockEntity) h.getBlockEntity(at);
    }

    private static int count(net.minecraft.world.Container c, Item item) {
        int n = 0;
        for (int i = 0; i < c.getContainerSize(); i++) { if (c.getItem(i).is(item)) { n += c.getItem(i).getCount(); } }
        return n;
    }

    /** A hopper over a crate fills it; a hopper under it empties it. */
    @GameTest(templateNamespace = "hdfv", template = "helipad", batch = BATCH, timeoutTicks = 400)
    public static void aHopperFillsAndEmptiesACrate(GameTestHelper h) {
        floor(h);
        BlockPos at = new BlockPos(8, 3, 8);
        h.setBlock(at, HdfvStorage.block(StorageKind.STORAGE_CRATE).get());
        StorageBlockEntity crate = (StorageBlockEntity) h.getBlockEntity(at);
        crate.setItem(26, new ItemStack(Items.GOLD_INGOT, 2));
        HopperBlockEntity above = hopper(h, at.above());
        above.setItem(0, new ItemStack(Items.IRON_INGOT, 5));
        boolean[] filled = new boolean[1];
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(count(above, Items.IRON_INGOT) == 0 && count(crate, Items.IRON_INGOT) == 5, "The hopper's ingots are in the crate: " + count(crate, Items.IRON_INGOT)))
                .thenExecute(() -> { filled[0] = true; hopper(h, at.below()); })
                .thenWaitUntil(() -> {
                    HopperBlockEntity below = (HopperBlockEntity) h.getBlockEntity(at.below());
                    h.assertTrue(crate.isEmpty() && count(below, Items.IRON_INGOT) == 5 && count(below, Items.GOLD_INGOT) == 2,
                            "The crate's items are in the hopper below: " + count(below, Items.IRON_INGOT) + " iron, " + count(below, Items.GOLD_INGOT) + " gold");
                })
                .thenSucceed();
    }

    /** The parts rack takes parts, workpieces, components and drawings and nothing else: from a hopper, a pipe's handler and its menu. */
    @GameTest(templateNamespace = "hdfv", template = "helipad", batch = BATCH, timeoutTicks = 400)
    public static void thePartsRackRefusesWhatIsNotAPart(GameTestHelper h) {
        floor(h);
        BlockPos at = new BlockPos(2, 3, 8);
        h.setBlock(at, HdfvStorage.block(StorageKind.PARTS_RACK).get());
        StorageBlockEntity rack = (StorageBlockEntity) h.getBlockEntity(at);
        PartDesign engine = SampleEngines.inlineFour();
        ItemStack part = Workpieces.part(engine), workpiece = Workpieces.start(engine, Workpieces.plan(engine).workpieces().get(0), 1);
        List<ItemStack> taken = List.of(part, workpiece, HdfvComponents.stack(Component.STEEL_PLATE, 4), HdfvComponents.stack(Intermediate.ELECTRIC_MOTOR_UNIT, 1),
                Blueprints.of(engine), new ItemStack(HdfvRegistry.BLANK_BLUEPRINT.get(), 3), new ItemStack(dev.mcmod.hdfv.neoforge.assembly.HdfvAssembly.VEHICLE_BLUEPRINT.get()));
        List<ItemStack> refused = List.of(new ItemStack(Items.DIRT, 3), Stock.of(Alloy.STEEL, 2), new ItemStack(Items.IRON_INGOT), HdfvComponents.stack(Intermediate.ALUMINA, 1),
                new ItemStack(HdfvStorage.block(StorageKind.PARTS_RACK).get()));
        IItemHandler handler = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(at), Direction.NORTH);
        for (ItemStack s : taken) {
            h.assertTrue(StorageMenu.Filter.PARTS.test(s) && rack.canPlaceItem(0, s) && handler.isItemValid(0, s), "The rack takes " + s);
            h.assertTrue(handler.insertItem(0, s, true).isEmpty(), "A pipe may put it in: " + s);
        }
        for (ItemStack s : refused) {
            h.assertTrue(!StorageMenu.Filter.PARTS.test(s) && !rack.canPlaceItem(0, s) && !handler.isItemValid(0, s), "The rack refuses " + s);
            h.assertTrue(handler.insertItem(0, s, true).getCount() == s.getCount(), "A pipe cannot put it in: " + s);
        }
        // its menu: neither by hand nor by shift-click
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(h.absolutePos(at).getX() + 0.5, h.absolutePos(at).getY() + 1, h.absolutePos(at).getZ() + 0.5);
        StorageMenu menu = (StorageMenu) rack.createMenu(1, player.getInventory(), player);
        h.assertTrue(menu != null && menu.filter == StorageMenu.Filter.PARTS && menu.size == 36 && menu.rows == 4 && menu.pages == 1, "The rack's menu");
        h.assertTrue(menu.stillValid(player), "Open beside the rack");
        h.assertTrue(!menu.getSlot(0).mayPlace(new ItemStack(Items.DIRT)) && menu.getSlot(0).mayPlace(part), "Its slots take parts only");
        player.getInventory().setItem(9, new ItemStack(Items.DIRT, 3));
        player.getInventory().setItem(10, part.copy());
        menu.quickMoveStack(player, menu.size);
        menu.quickMoveStack(player, menu.size + 1);
        h.assertTrue(player.getInventory().getItem(9).is(Items.DIRT) && player.getInventory().getItem(9).getCount() == 3, "Shift-clicked dirt stays with the player");
        h.assertTrue(player.getInventory().getItem(10).isEmpty() && ItemStack.matches(rack.getItem(0), part), "A shift-clicked part goes on the rack");
        menu.quickMoveStack(player, 0);
        h.assertTrue(rack.isEmpty() && count(player.getInventory(), HdfvMachines.PART.get()) == 1, "And back to the player");
        player.getInventory().clearContent();
        menu.removed(player);
        // a hopper over it: the plates go in, the dirt stays in the hopper
        HopperBlockEntity above = hopper(h, at.above());
        above.setItem(0, new ItemStack(Items.DIRT, 3));
        above.setItem(1, HdfvComponents.stack(Component.STEEL_PLATE, 4));
        Item plate = HdfvComponents.COMPONENTS.get(Component.STEEL_PLATE).get();
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(count(rack, plate) == 4, "The plates are on the rack: " + count(rack, plate)))
                .thenIdle(40)
                .thenExecute(() -> {
                    h.assertTrue(count(above, Items.DIRT) == 3 && count(rack, Items.DIRT) == 0, "The dirt is still in the hopper: " + count(above, Items.DIRT));
                    h.assertTrue(count(rack, plate) == 4 && count(above, plate) == 0, "Only the plates went in");
                })
                .thenSucceed();
    }

    /** A store's menu: its footer is the slots used and ItemMass of its contents, shift-click moves stacks, the container's slots are two pages. */
    @GameTest(templateNamespace = "hdfv", template = "helipad", batch = BATCH)
    public static void aStoresMenuCountsItsSlotsAndItsMass(GameTestHelper h) {
        floor(h);
        BlockPos at = new BlockPos(8, 3, 2);
        h.setBlock(at, HdfvStorage.block(StorageKind.SHIPPING_CONTAINER).get());
        StorageBlockEntity box = (StorageBlockEntity) h.getBlockEntity(at);
        List<ItemStack> in = List.of(new ItemStack(Items.IRON_INGOT, 64), Stock.of(Alloy.STEEL, 12), Workpieces.part(SampleEngines.inlineFour()),
                HdfvComponents.stack(Component.BALL_BEARING, 5), new ItemStack(Items.WATER_BUCKET), new ItemStack(Items.FEATHER, 3));
        double kg = 0;
        for (int i = 0; i < in.size(); i++) { box.setItem(i * 20, in.get(i).copy()); kg += ItemMass.kg(in.get(i)); }
        near(h, box.massKg(), kg, "The container's contents");
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(h.absolutePos(at).getX() + 0.5, h.absolutePos(at).getY() + 1, h.absolutePos(at).getZ() + 0.5);
        StorageMenu menu = (StorageMenu) box.createMenu(1, player.getInventory(), player);
        h.assertTrue(menu.size == 108 && menu.rows == 6 && menu.pages == 2 && menu.filter == StorageMenu.Filter.ANY, "The container's menu: " + menu.size + " slots, " + menu.pages + " pages");
        h.assertTrue(menu.slots.size() == 108 + 36 && menu.playerTop == 139 && menu.height == menu.playerTop + 82, "Its slots and the player's under six rows: " + menu.playerTop + ", " + menu.height);
        // the footer, as the screen reads it (a tenth of a kilogram)
        h.assertTrue(menu.usedSlots() == in.size(), "Slots used: " + menu.usedSlots());
        h.assertTrue(Math.abs(menu.massKg() - kg) <= 0.05 + 1e-9, "The footer's mass " + menu.massKg() + " is ItemMass's " + kg);
        h.assertTrue(menu.limitKg() == 0, "No limit");
        // shift-click a stack in: the footer follows on the next tick's count
        player.getInventory().setItem(9, new ItemStack(Items.IRON_INGOT, 10));
        menu.quickMoveStack(player, menu.size);
        menu.broadcastChanges();
        h.assertTrue(player.getInventory().getItem(9).isEmpty() && count(box, Items.IRON_INGOT) == 74, "Ten more ingots in the container: " + count(box, Items.IRON_INGOT));
        h.assertTrue(menu.usedSlots() == in.size() + 1 && Math.abs(menu.massKg() - (kg + 100)) <= 0.05 + 1e-9, "The footer follows: " + menu.usedSlots() + " slots, " + menu.massKg() + " kg");
        near(h, box.massKg(), kg + 100, "And the container's contents");
        // two pages of six rows: a slot of the second page sits where its fellow of the first does and is live only on its page
        h.assertTrue(menu.getSlot(54).x == menu.getSlot(0).x && menu.getSlot(54).y == menu.getSlot(0).y && menu.getSlot(107).x == menu.getSlot(53).x, "The pages share their places");
        h.assertTrue(menu.page() == 0 && menu.getSlot(0).isActive() && !menu.getSlot(54).isActive(), "The first page");
        menu.page(1);
        h.assertTrue(menu.page() == 1 && !menu.getSlot(0).isActive() && menu.getSlot(54).isActive() && menu.getSlot(108).isActive(), "The second page (the player's slots stay)");
        menu.page(5);
        h.assertTrue(menu.page() == 1, "No third page");
        // a menu of any container with a limit (a vehicle's hold later): the limit reaches the footer
        net.minecraft.world.SimpleContainer hold = new net.minecraft.world.SimpleContainer(18);
        hold.setItem(3, new ItemStack(Items.IRON_BLOCK, 4));
        StorageMenu limited = new StorageMenu(2, player.getInventory(), hold, StorageMenu.Filter.ANY, p -> true, () -> 250.0);
        h.assertTrue(limited.size == 18 && limited.rows == 2 && limited.pages == 1 && limited.usedSlots() == 1, "A hold of 18 slots");
        h.assertTrue(limited.massKg() == 360 && limited.limitKg() == 250, "Its footer: " + limited.massKg() + " of " + limited.limitKg() + " kg");
        // the menu closes when the block goes
        h.assertTrue(menu.stillValid(player), "Open beside the container");
        box.clearContent();
        h.setBlock(at, Blocks.AIR);
        h.assertTrue(!menu.stillValid(player), "Closed once the container is gone");
        h.succeed();
    }
}
