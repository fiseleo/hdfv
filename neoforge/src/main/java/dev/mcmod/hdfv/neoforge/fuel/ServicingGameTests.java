package dev.mcmod.hdfv.neoforge.fuel;

import dev.mcmod.aerocore.api.Orientation;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.vehicle.Binding;
import dev.mcmod.aerocore.api.vehicle.MassSpec;
import dev.mcmod.aerocore.api.vehicle.Mount;
import dev.mcmod.aerocore.api.vehicle.VehicleBlueprint;
import dev.mcmod.aerocore.neoforge.vehicle.PartVehicle;
import dev.mcmod.aerocore.neoforge.vehicle.VehicleRegistry;
import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.neoforge.machine.Workpieces;
import dev.mcmod.hdfv.part.BoxDesign;
import dev.mcmod.hdfv.part.engine.PulsejetDesign;
import dev.mcmod.hdfv.part.engine.SampleJets;
import dev.mcmod.hdfv.part.engine.SolidRocketDesign;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** Servicing in a running server (0.10): boosters loaded by hand, fired, burnt out and cast off as items; worn pulsejet reeds replaced. */
@PrefixGameTestTemplate(false)
public final class ServicingGameTests {
    private ServicingGameTests() {}

    /** Thrust straight down: +X of the part turned to -Y, so the boosters press the vehicle into the floor. */
    static final Orientation DOWN = Orientation.rotationVector(new Vec3d(0, 0, -Math.PI / 2));

    /** A heavy box on a stone floor with the parts on it, their inputs bound to channels of their own names. */
    static PartVehicle rig(GameTestHelper h, List<VehicleBlueprint.Entry> parts) {
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) { h.setBlock(new BlockPos(x, 0, z), Blocks.STONE); }
        List<VehicleBlueprint.Entry> all = new java.util.ArrayList<>();
        all.add(new VehicleBlueprint.Entry("box", new BoxDesign("box", new Vec3d(2, 1, 2), 1.05, true, 2, MassSpec.given(3000)), Mount.ORIGIN));
        all.addAll(parts);
        VehicleBlueprint b = new VehicleBlueprint("rig", all, List.of());
        b.compile().warmUp();
        BlockPos o = h.absolutePos(BlockPos.ZERO);
        PartVehicle v = VehicleRegistry.PART_VEHICLE.get().create(h.getLevel());
        v.setBlueprint(b, new Vec3d(o.getX() + 8, o.getY() + 1.6, o.getZ() + 8), Orientation.IDENTITY);
        h.getLevel().addFreshEntity(v);
        return v;
    }

    static VehicleBlueprint.Entry booster(String id, SolidRocketDesign d, Vec3d at) {
        return new VehicleBlueprint.Entry(id, d, Mount.of(at, DOWN), List.of(Binding.of("fire", "fire_" + id)));
    }

    // Its own batch: the boosters shake the box.
    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool", timeoutTicks = 300, batch = "hdfv_boosters")
    public static void boostersAreLoadedByHandFiredAndTheApcpOneIsCastOff(GameTestHelper h) {
        SolidRocketDesign knsb = SampleJets.knsbMotor(), apcp = SampleJets.apcpBooster();
        PartVehicle v = rig(h, List.of(booster("knsb", knsb, new Vec3d(0.6, 0.8, 0)), booster("apcp", apcp, new Vec3d(-0.6, 0.8, 0))));
        // as out of an assembly room: the grains empty
        for (int k = 0; k < v.tankCount(); k++) { v.setTank(k, "", 0, 1000); }
        int kTank = 0, aTank = 1;
        ItemStack k10 = new ItemStack(HdfvFuel.KNSB_GRAIN.get(), 10), a20 = new ItemStack(HdfvFuel.APCP_GRAIN.get(), 20);
        Servicing.load(v, "knsb", k10, false);
        h.assertTrue(k10.getCount() == 10 - knsb.grainItems() && Math.abs(v.tankKg(kTank) - knsb.grain().propellantKg()) < 1e-6, "KNSB loaded: " + k10.getCount() + " left");
        Servicing.load(v, "apcp", a20, false);
        h.assertTrue(a20.getCount() == 20 && v.tankKg(aTank) == 0, "20 APCP grains are short of the " + apcp.grainItems() + " it takes");
        ItemStack a30 = new ItemStack(HdfvFuel.APCP_GRAIN.get(), 30);
        Servicing.load(v, "apcp", a30, false);
        h.assertTrue(a30.getCount() == 30 - apcp.grainItems() && v.tankKg(aTank) > 0, "APCP loaded");
        v.setControl("fire_knsb", 1);
        v.setControl("fire_apcp", 1);
        double burn = Math.max(knsb.performance().burnTimeS(), apcp.performance().burnTimeS());
        h.runAfterDelay((long) Math.ceil(burn * 20) + 40, () -> {
            h.assertTrue(v.tankKg(kTank) < 0.01 * knsb.grain().propellantKg(), "The KNSB grain burnt: " + v.tankKg(kTank));
            h.assertTrue(v.vehicle().installed("apcp") == null && v.vehicle().installed("knsb") != null, "The APCP booster went, the KNSB motor stayed");
            List<ItemEntity> drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(BlockPos.ZERO)).inflate(40),
                    i -> Workpieces.partDesign(i.getItem()) instanceof SolidRocketDesign s && s.booster().jettisonWhenBurnt());
            h.assertTrue(drops.size() == 1, "It dropped as a part: " + drops.size());
            // reloaded, the KNSB motor fires again (on a fresh press)
            v.setControl("fire_knsb", 0);
            ItemStack more = new ItemStack(HdfvFuel.KNSB_GRAIN.get(), knsb.grainItems());
            Servicing.load(v, "knsb", more, false);
            h.assertTrue(more.isEmpty() && v.tankKg(0) > 0, "Reloaded");
            drops.forEach(ItemEntity::discard);
            v.discard();
            h.succeed();
        });
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool")
    public static void wornReedsAreReplacedWithAReedSet(GameTestHelper h) {
        PulsejetDesign argus = SampleJets.argus();
        PartVehicle v = rig(h, List.of(new VehicleBlueprint.Entry("pulsejet", argus, Mount.at(new Vec3d(0, 1, 0)))));
        double life = argus.valve().reedLifeCycles();
        v.vehicleState().setInternal("pulsejet", PulsejetDesign.STATE_CYCLES, 0.05 * life);
        ItemStack sets = new ItemStack(HdfvFuel.REED_SET.get(), 2);
        Servicing.reed(v, sets, false);
        h.assertTrue(sets.getCount() == 2, "Barely used reeds are left alone");
        v.vehicleState().setInternal("pulsejet", PulsejetDesign.STATE_CYCLES, 1.1 * life);
        Servicing.reed(v, sets, false);
        h.assertTrue(sets.getCount() == 1 && v.vehicleState().internal("pulsejet", PulsejetDesign.STATE_CYCLES) == 0, "Worn reeds replaced with one set");
        v.discard();
        h.succeed();
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool")
    public static void propellantAndReedsAreMadeAndNamed(GameTestHelper h) throws java.io.IOException {
        var recipes = h.getLevel().getRecipeManager().getRecipes();
        for (var e : HdfvFuel.items().entrySet()) {
            var r = recipes.stream().filter(x -> x.id().getNamespace().equals(Hdfv.MOD_ID) && x.id().getPath().equals(e.getValue())).findFirst();
            h.assertTrue(r.isPresent(), "A recipe for " + e.getKey());
            var result = r.get().value().getResultItem(h.getLevel().registryAccess());
            h.assertTrue(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(result.getItem()).getPath().equals(e.getKey()), e.getKey() + " is what it makes");
        }
        for (String file : List.of("en_us", "zh_tw")) {
            try (var in = Hdfv.class.getResourceAsStream("/assets/hdfv/lang/" + file + ".json")) {
                var lang = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                for (String id : HdfvFuel.items().keySet()) { h.assertTrue(lang.has("item.hdfv." + id), file + " lacks " + id); }
                for (String k : List.of("hdfv.service.loaded", "hdfv.service.short", "hdfv.service.no_booster", "hdfv.service.reeds", "hdfv.service.no_reeds")) {
                    h.assertTrue(lang.has(k), file + " lacks " + k);
                }
            }
        }
        h.succeed();
    }
}
