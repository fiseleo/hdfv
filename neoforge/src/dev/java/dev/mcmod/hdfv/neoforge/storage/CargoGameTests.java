package dev.mcmod.hdfv.neoforge.storage;

import com.mojang.authlib.GameProfile;
import dev.mcmod.aerocore.api.Orientation;
import dev.mcmod.aerocore.api.RigidBodyState;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.vehicle.VehicleBlueprint;
import dev.mcmod.aerocore.neoforge.vehicle.PartVehicle;
import dev.mcmod.aerocore.neoforge.vehicle.VehicleRegistry;
import dev.mcmod.hdfv.assembly.AutoWiring;
import dev.mcmod.hdfv.assembly.Placing;
import dev.mcmod.hdfv.assembly.SampleLayouts;
import dev.mcmod.hdfv.design.RecordEditor;
import dev.mcmod.hdfv.neoforge.assembly.HdfvAssembly;
import dev.mcmod.hdfv.neoforge.assembly.HdfvVehicle;
import dev.mcmod.hdfv.part.SampleBlueprints;
import dev.mcmod.hdfv.part.StorageBoxDesign;
import dev.mcmod.hdfv.part.body.BodyDesign;
import dev.mcmod.hdfv.part.body.BodyKitDesign;
import dev.mcmod.hdfv.part.body.SampleBodies;
import dev.mcmod.hdfv.storage.CargoRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.function.Consumer;

/**
 * 0.25: vehicle cargo in the world (VehicleCargo, HdfvCargo, CargoLoaderBlockEntity): a sedan with 200 kg in its boot
 * is 200 kg heavier and sits lower at the rear; what goes through the menu weighs the hold; a player too far or a
 * vehicle not finished is refused; cargo is saved with the vehicle (and weighed again on load); a hopper through a cargo
 * loader fills a parked six-wheeler's hold and stops when it drives off; a vehicle killed or discarded drops its cargo,
 * one fallen out of the world does not, one unloaded into a taker gives it there; a roof box on a roof rack carries its
 * cargo and its mass, and dropping it (cast off) drops its cargo. Refitting a hold smaller is RefitShopGameTests', an
 * assembly room's dismantling AssemblyGameTests'.
 */
@PrefixGameTestTemplate(false)
public final class CargoGameTests {
    private CargoGameTests() {}

    /** A stone floor over the pad; the height of its top. */
    static double floor(GameTestHelper h) {
        for (int x = 0; x < 28; x++) for (int z = 0; z < 28; z++) { h.setBlock(new BlockPos(x, 0, z), Blocks.STONE); }
        return h.absolutePos(new BlockPos(0, 1, 0)).getY();
    }

    /** A vehicle of a blueprint set down facing +x with its frame's origin at x, z of the pad, a hand's breadth over the floor, its handbrake on. */
    static PartVehicle spawn(GameTestHelper h, VehicleBlueprint blueprint, double x, double z, double floor) {
        blueprint.compile().warmUp();
        BlockPos o = h.absolutePos(BlockPos.ZERO);
        PartVehicle v = VehicleRegistry.PART_VEHICLE.get().create(h.getLevel());
        if (v == null) { throw new IllegalStateException("Vehicle factory failed"); }
        v.setBlueprint(blueprint, new Vec3d(o.getX() + x, floor + 0.1, o.getZ() + z), Orientation.IDENTITY);
        v.setControls(Map.of("selector", 0.0, "throttle", 0.0, "handbrake", 1.0, "brake", 0.0));
        h.getLevel().addFreshEntity(v);
        return v;
    }

    /** The height of the body's rear end (its frame origin) in the world. */
    static double rearY(PartVehicle v) { return v.partPoint(v.vehicle().installed("body"), Vec3d.ZERO, 0).y(); }

    /** Everything a vehicle has aboard, kg (its compiled mass, its fuel and its cargo). */
    static double laden(PartVehicle v) { return v.vehicle().properties().massKg() + v.vehicleState().fuelKg() + v.vehicleState().cargoKg(); }

    static void near(GameTestHelper h, double is, double want, String what) {
        h.assertTrue(Math.abs(is - want) <= 1e-6 * Math.max(1, Math.abs(want)), what + ": " + is + ", not " + want);
    }

    /** Items of a kind lying in a box round a point (m). */
    static int lying(GameTestHelper h, Vec3d at, double reach, Item item) {
        int n = 0;
        for (ItemEntity e : h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(at.x(), at.y(), at.z(), at.x(), at.y(), at.z()).inflate(reach))) {
            if (e.getItem().is(item)) { n += e.getItem().getCount(); }
        }
        return n;
    }

    static void clearItems(GameTestHelper h) {
        BlockPos o = h.absolutePos(BlockPos.ZERO);
        for (ItemEntity e : h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(o).inflate(40))) { e.discard(); }
    }

    /** A player of the test's own whose menus open without a client (a FakePlayer's openMenu does nothing). */
    static final class MenuPlayer extends FakePlayer {
        MenuPlayer(ServerLevel level, String name) { super(level, new GameProfile(java.util.UUID.nameUUIDFromBytes(name.getBytes()), name)); }

        @Override public OptionalInt openMenu(MenuProvider provider, Consumer<RegistryFriendlyByteBuf> extra) {
            if (provider == null) { return OptionalInt.empty(); }
            AbstractContainerMenu m = provider.createMenu(1, getInventory(), this);
            if (m == null) { return OptionalInt.empty(); }
            containerMenu = m;
            return OptionalInt.of(1);
        }
    }

    /**
     * Two sample sedans side by side, one with two iron blocks and two ingots (200 kg) in its boot: it weighs 200 kg
     * more (the state carries the hold as a point mass), the hold reports it, and once both have settled it sits lower
     * at its rear than the empty one does by more than a centimetre.
     */
    @GameTest(templateNamespace = "hdfv", template = "helipad", timeoutTicks = 1200, batch = "hdfv_cargo_physics")
    public static void aSedanWithLoadInItsBootIsHeavierAndSitsLowerAtTheRear(GameTestHelper h) {
        double floor = floor(h);
        PartVehicle empty = spawn(h, SampleBlueprints.sedan(), 3, 6, floor), loaded = spawn(h, SampleBlueprints.sedan(), 3, 20, floor);
        double[] before = new double[2];
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(empty.ready() && loaded.ready(), "Tables still building"))
                .thenIdle(60)
                .thenExecute(() -> {
                    h.assertTrue(loaded.holdCount() == 1 && loaded.vehicle().holds().get(0).id().equals("body.m5_cargo"), "The boot is a hold");
                    near(h, loaded.holdKg(0), 0, "An empty boot");
                    before[0] = rearY(empty);
                    before[1] = rearY(loaded);
                    h.assertTrue(Math.abs(laden(loaded) - laden(empty)) < 0.05, "The two weigh the same empty: " + laden(loaded) + ", " + laden(empty));
                    VehicleCargo.Hold boot = VehicleCargo.hold(loaded, 0);
                    h.assertTrue(boot != null && boot.getContainerSize() == 12, "Twelve slots in a 0.45 m3 boot");
                    boot.setItem(0, new ItemStack(Items.IRON_BLOCK, 2));
                    boot.setItem(5, new ItemStack(Items.IRON_INGOT, 2));
                    near(h, loaded.holdKg(0), 200, "Two iron blocks and two ingots in the boot");
                    near(h, loaded.vehicleState().cargoKg(), 200, "The state carries it");
                    h.assertTrue(Math.abs(laden(loaded) - laden(empty) - 200) < 0.05, "The laden sedan is heavier by its cargo: " + (laden(loaded) - laden(empty)));
                    h.assertTrue(CargoRules.overloaded(loaded.holdKg(0), VehicleCargo.limitKg(loaded.vehicle(), 0)), "200 kg is over the boot's 90 kg rating (shown, not enforced)");
                })
                .thenIdle(100)
                .thenExecute(() -> {
                    double sankEmpty = rearY(empty) - before[0], sankLoaded = rearY(loaded) - before[1];
                    System.out.println("hdfv cargo: rear end moved " + sankEmpty + " m empty, " + sankLoaded + " m with 200 kg in the boot");
                    h.assertTrue(sankLoaded < sankEmpty - 0.01, "The laden sedan sits lower at the rear: " + sankLoaded + " against " + sankEmpty);
                    VehicleCargo.unload(loaded, s -> {});
                    empty.discard();
                    loaded.discard();
                })
                .thenSucceed();
    }

    /**
     * Through the menu: a player behind the boot opens it (twelve slots, the boot's 90 kg rating as its limit),
     * shift-clicks five ingots in (50 kg in the hold) and takes them back; walked off ten metres the menu is no longer
     * valid and the hold will not open; the rider's key needs a rider; an assembly room's unfinished car will not open.
     */
    @GameTest(templateNamespace = "hdfv", template = "helipad", timeoutTicks = 600, batch = "hdfv_cargo")
    public static void itemsThroughTheMenuWeighTheHoldAndAFarPlayerIsRefused(GameTestHelper h) {
        double floor = floor(h);
        PartVehicle v = spawn(h, SampleBlueprints.sedan(), 6, 8, floor);
        MenuPlayer p = new MenuPlayer(h.getLevel(), "hdfv_cargo_menu");
        h.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    Vec3d boot = VehicleCargo.point(v, 0);
                    p.moveTo(boot.x() - 1.5, floor, boot.z());
                    h.assertTrue(VehicleCargo.open(p, v, 0) == null && p.containerMenu instanceof StorageMenu, "Opened from behind the car");
                    StorageMenu m = (StorageMenu) p.containerMenu;
                    h.assertTrue(m.size == 12 && m.filter == StorageMenu.Filter.ANY, "Twelve slots of anything: " + m.size);
                    near(h, m.limitKg(), 90, "The boot's rating");
                    p.getInventory().setItem(0, new ItemStack(Items.IRON_INGOT, 5));
                    m.quickMoveStack(p, m.size + 27);
                    near(h, v.holdKg(0), 50, "Five ingots in the boot");
                    h.assertTrue(p.getInventory().getItem(0).isEmpty(), "Out of the player's hand");
                    m.broadcastChanges();
                    near(h, m.massKg(), 50, "The footer's mass");
                    h.assertTrue(m.usedSlots() == 1, "One slot used");
                    m.quickMoveStack(p, 0);
                    near(h, v.holdKg(0), 0, "Taken back");
                    h.assertTrue(p.getInventory().countItem(Items.IRON_INGOT) == 5, "Back with the player");
                    h.assertTrue(m.stillValid(p), "Still in reach");
                    p.moveTo(boot.x() - 10, floor, boot.z());
                    h.assertTrue(!m.stillValid(p), "Ten metres off, the menu closes");
                    p.containerMenu = p.inventoryMenu;
                    h.assertTrue(VehicleCargo.open(p, v, 0) != null && p.containerMenu == p.inventoryMenu, "Too far to open");
                    h.assertTrue(CargoNetwork.open(p, v, -1) != null, "The rider's key needs a rider");
                    h.assertTrue(VehicleCargo.open(p, v, 3) != null, "No fourth hold");
                    // an assembly room's car before it is finished
                    HdfvVehicle building = HdfvAssembly.VEHICLE.get().create(h.getLevel());
                    BlockPos o = h.absolutePos(BlockPos.ZERO);
                    building.setBlueprint(SampleBlueprints.sedan(), new Vec3d(o.getX() + 6, floor + 0.1, o.getZ() + 20), Orientation.IDENTITY);
                    building.setBuilt(0.5f);
                    h.getLevel().addFreshEntity(building);
                    p.moveTo(o.getX() + 4, floor, o.getZ() + 20);
                    h.assertTrue(VehicleCargo.open(p, building, 0) != null && VehicleCargo.hold(building, 0) == null, "Not while it is being built");
                    building.discard();
                    v.discard();
                })
                .thenSucceed();
    }

    /**
     * Saved and loaded: the stacks come back in their slots and the hold's mass with them; a mass the state had wrong
     * is weighed again from the stacks once the copy is laid out; a container's 324 slots keep their places.
     */
    @GameTest(templateNamespace = "hdfv", template = "helipad", timeoutTicks = 600, batch = "hdfv_cargo")
    public static void cargoIsSavedWithTheVehicle(GameTestHelper h) {
        double floor = floor(h);
        PartVehicle v = spawn(h, SampleBlueprints.sedan(), 6, 14, floor);
        h.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    VehicleCargo.Hold boot = VehicleCargo.hold(v, 0);
                    boot.setItem(3, new ItemStack(Items.IRON_BLOCK, 2));
                    boot.setItem(11, new ItemStack(Items.OAK_PLANKS, 10));
                    near(h, v.holdKg(0), 190, "Two iron blocks and ten planks");
                    CompoundTag tag = new CompoundTag();
                    v.saveWithoutId(tag);
                    PartVehicle copy = VehicleRegistry.PART_VEHICLE.get().create(h.getLevel());
                    copy.load(tag);
                    near(h, copy.vehicleState().holdKg(0), 190, "The state's hold, saved");
                    NonNullList<ItemStack> items = VehicleCargo.of(copy).holds.get("body.m5_cargo");
                    h.assertTrue(items != null && items.size() == 12 && items.get(3).is(Items.IRON_BLOCK) && items.get(3).getCount() == 2
                            && items.get(11).is(Items.OAK_PLANKS) && items.get(11).getCount() == 10, "The stacks in their slots: " + items);
                    // a mass that does not match the stacks is weighed again when the copy is laid out (as on loading a world)
                    copy.vehicleState().setHold(0, 999);
                    VehicleCargo.check(copy);
                    near(h, copy.vehicleState().holdKg(0), 190, "Weighed again");
                    // slots past 255 (a container's) keep their places
                    VehicleCargo c = new VehicleCargo();
                    NonNullList<ItemStack> big = NonNullList.withSize(CargoRules.MAX_CONTAINER_SLOTS, ItemStack.EMPTY);
                    big.set(300, new ItemStack(Items.COBBLESTONE, 7));
                    c.holds.put("box.hold", big);
                    VehicleCargo.Serializer s = new VehicleCargo.Serializer();
                    VehicleCargo back = s.read(copy, s.write(c, h.getLevel().registryAccess()), h.getLevel().registryAccess());
                    h.assertTrue(back.holds.get("box.hold").size() == 324 && back.holds.get("box.hold").get(300).getCount() == 7, "Slot 300 saved");
                    h.assertTrue(s.write(new VehicleCargo(), h.getLevel().registryAccess()) == null, "Nothing saved for empty holds");
                    VehicleCargo.unload(v, x -> {});
                    v.discard();
                })
                .thenSucceed();
    }

    /**
     * A cargo loader beside a parked six-wheeler's hold, a hopper of six ingots on it: the hopper fills the hold
     * through it (60 kg); released and rolling at 5 m/s the truck is no longer served (no slots), and four more ingots
     * stay in the hopper.
     */
    @GameTest(templateNamespace = "hdfv", template = "helipad", timeoutTicks = 1200, batch = "hdfv_cargo_loader")
    public static void aHopperThroughTheLoaderFillsAParkedTruckAndStopsWhenItDrivesOff(GameTestHelper h) {
        double floor = floor(h);
        PartVehicle truck = spawn(h, dev.mcmod.hdfv.drive.TestCars.sixBySix(), 3, 10, floor);
        BlockPos[] at = new BlockPos[1];
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(truck.ready(), "Tables still building"))
                .thenIdle(60)
                .thenExecute(() -> {
                    h.assertTrue(truck.holdCount() == 1, "Its hold: " + truck.holdCount());
                    Vec3d hold = VehicleCargo.point(truck, 0);
                    BlockPos o = h.absolutePos(BlockPos.ZERO);
                    at[0] = new BlockPos((int) Math.floor(hold.x()) - o.getX(), 1, (int) Math.floor(hold.z() + 2.2) - o.getZ());
                    h.setBlock(at[0], HdfvCargo.LOADER.get().defaultBlockState().setValue(CargoLoaderBlock.FACING, Direction.NORTH));
                    h.setBlock(at[0].above(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
                    ((HopperBlockEntity) h.getBlockEntity(at[0].above())).setItem(0, new ItemStack(Items.IRON_INGOT, 6));
                    CargoLoaderBlockEntity loader = (CargoLoaderBlockEntity) h.getBlockEntity(at[0]);
                    h.assertTrue(loader.target() != null && loader.handler().getSlots() == 12, "The loader serves the parked truck's hold: "
                            + (loader.target() == null ? "none, the hold at " + hold + ", the face at " + loader.face() + ", at " + truck.velocity().length() + " m/s" : loader.handler().getSlots()));
                })
                .thenWaitUntil(() -> near(h, truck.holdKg(0), 60, "The hopper's ingots through the loader"))
                .thenExecute(() -> {
                    h.assertTrue(((HopperBlockEntity) h.getBlockEntity(at[0].above())).isEmpty(), "The hopper is empty");
                    truck.setControls(Map.of("handbrake", 0.0, "brake", 0.0));
                    RigidBodyState b = truck.vehicleState().body();
                    truck.vehicleState().setBody(new RigidBodyState(b.position(), b.orientation().rotate(Vec3d.X).scale(5), b.orientation(), b.angularMomentum()));
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    CargoLoaderBlockEntity loader = (CargoLoaderBlockEntity) h.getBlockEntity(at[0]);
                    h.assertTrue(loader.target() == null && loader.handler().getSlots() == 0, "Rolling at " + truck.velocity().length() + " m/s, it is not served");
                    ((HopperBlockEntity) h.getBlockEntity(at[0].above())).setItem(0, new ItemStack(Items.IRON_INGOT, 4));
                })
                .thenIdle(40)
                .thenExecute(() -> {
                    near(h, truck.holdKg(0), 60, "Nothing more went in");
                    h.assertTrue(((HopperBlockEntity) h.getBlockEntity(at[0].above())).getItem(0).getCount() == 4, "The four ingots stay in the hopper");
                    VehicleCargo.unload(truck, s -> {});
                    truck.discard();
                })
                .thenSucceed();
    }

    /**
     * Where cargo goes when a vehicle goes: discarded or killed, at its hold; fallen out of the world, nowhere (it is
     * gone with it); unloaded into a taker first (what a room does), it drops nothing.
     */
    @GameTest(templateNamespace = "hdfv", template = "helipad", timeoutTicks = 600, batch = "hdfv_cargo")
    public static void aVehicleRemovedDropsItsCargoUnlessItFellOutOfTheWorld(GameTestHelper h) {
        double floor = floor(h);
        PartVehicle discarded = spawn(h, SampleBlueprints.sedan(), 3, 4, floor), killed = spawn(h, SampleBlueprints.sedan(), 3, 12, floor),
                fallen = spawn(h, SampleBlueprints.sedan(), 3, 20, floor), unloaded = spawn(h, SampleBlueprints.sedan(), 14, 12, floor);
        h.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    clearItems(h);
                    for (PartVehicle v : List.of(discarded, killed, fallen, unloaded)) {
                        VehicleCargo.Hold boot = VehicleCargo.hold(v, 0);
                        boot.setItem(0, new ItemStack(Items.IRON_INGOT, 64));
                        boot.setItem(1, new ItemStack(Items.IRON_INGOT, 16));
                    }
                    Vec3d a = VehicleCargo.point(discarded, 0), b = VehicleCargo.point(killed, 0), c = VehicleCargo.point(unloaded, 0);
                    discarded.discard();
                    h.assertTrue(lying(h, a, 2, Items.IRON_INGOT) == 80, "Discarded: its 80 ingots by its boot (" + lying(h, a, 2, Items.IRON_INGOT) + ")");
                    killed.kill();
                    h.assertTrue(lying(h, b, 2, Items.IRON_INGOT) == 80, "Killed: its 80 ingots by its boot");
                    List<ItemStack> taken = new ArrayList<>();
                    VehicleCargo.unload(unloaded, taken::add);
                    near(h, unloaded.holdKg(0), 0, "Unloaded, the boot weighs nothing");
                    h.assertTrue(taken.stream().mapToInt(ItemStack::getCount).sum() == 80, "The taker has the 80 ingots");
                    unloaded.discard();
                    h.assertTrue(lying(h, c, 3, Items.IRON_INGOT) == 0, "Nothing dropped by the unloaded car");
                    // below the world's bottom (where vanilla discards what falls), the cargo goes with it
                    double y = h.getLevel().getMinBuildHeight() - 70;
                    fallen.setPos(fallen.getX(), y, fallen.getZ());
                    fallen.discard();
                    h.assertTrue(lying(h, new Vec3d(fallen.getX(), y, fallen.getZ()), 8, Items.IRON_INGOT) == 0, "Fallen out of the world, nothing drops");
                    clearItems(h);
                })
                .thenSucceed();
    }

    /**
     * A roof box on the sedan's roof rack: the vehicle weighs the box and the rack more, has the box's hold up on the
     * roof as its second, which carries what is put in it (40 kg, its rating about 49 kg); cast off, the box drops its
     * cargo where it fell, and the car keeps its boot's.
     */
    @GameTest(templateNamespace = "hdfv", template = "helipad", timeoutTicks = 1200, batch = "hdfv_cargo_roof")
    public static void aRoofBoxOnARoofRackCarriesCargoAndItsMass(GameTestHelper h) {
        double floor = floor(h);
        BodyDesign racked = SampleBodies.sedan().withKit(RecordEditor.with(BodyKitDesign.NONE, "roofRack", BodyKitDesign.RoofRack.BARS));
        VehicleBlueprint layout = SampleLayouts.sedan();
        layout = new VehicleBlueprint("roof box sedan", layout.parts().stream()
                .map(e -> e.id().equals("body") ? new VehicleBlueprint.Entry("body", racked, e.mount()) : e).toList(), List.of());
        StorageBoxDesign box = StorageBoxDesign.roofBox();
        VehicleBlueprint withBox = AutoWiring.wire(Placing.add(layout, box));
        String boxId = withBox.parts().getLast().id();
        PartVehicle v = spawn(h, withBox, 3, 12, floor), plain = spawn(h, SampleBlueprints.sedan(), 3, 22, floor);
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(v.ready() && plain.ready(), "Tables still building"))
                .thenIdle(40)
                .thenExecute(() -> {
                    clearItems(h);
                    double extra = v.vehicle().properties().massKg() - plain.vehicle().properties().massKg();
                    h.assertTrue(extra > box.massKg() && extra < box.massKg() + 25, "The box (" + box.massKg() + " kg) and its rack: " + extra + " kg more");
                    h.assertTrue(v.holdCount() == 2 && v.vehicle().holds().get(1).id().equals(boxId + ".hold"), "The box's hold is the second");
                    h.assertTrue(VehicleCargo.point(v, 1).y() > VehicleCargo.point(v, 0).y() + 0.8, "Up on the roof");
                    VehicleCargo.Hold roof = VehicleCargo.hold(v, 1), boot = VehicleCargo.hold(v, 0);
                    h.assertTrue(roof.getContainerSize() == 12, "A 450 l box's twelve slots");
                    near(h, VehicleCargo.limitKg(v.vehicle(), 1), box.ratedLoadKg(), "Rated as the design says");
                    roof.setItem(0, new ItemStack(Items.IRON_INGOT, 4));
                    boot.setItem(0, new ItemStack(Items.IRON_INGOT, 2));
                    near(h, v.holdKg(1), 40, "Four ingots on the roof");
                    near(h, v.vehicleState().cargoKg(), 60, "And two in the boot");
                })
                .thenIdle(20)
                .thenExecute(() -> {
                    h.assertTrue(v.fault() == null && !v.isRemoved(), "It stands with its load: " + v.fault());
                    Vec3d top = VehicleCargo.point(v, 1);
                    h.assertTrue(v.castOff(List.of(boxId)), "The box cast off");
                    h.assertTrue(v.holdCount() == 1, "Only the boot is left");
                    h.assertTrue(lying(h, top, 3, Items.IRON_INGOT) == 4, "The box's four ingots where it fell: " + lying(h, top, 3, Items.IRON_INGOT));
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    near(h, v.holdKg(0), 20, "The boot keeps its two");
                    VehicleCargo.unload(v, s -> {});
                    v.discard();
                    plain.discard();
                    clearItems(h);
                })
                .thenSucceed();
    }
}
