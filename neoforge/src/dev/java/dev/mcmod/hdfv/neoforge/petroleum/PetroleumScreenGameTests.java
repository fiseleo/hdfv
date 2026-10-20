package dev.mcmod.hdfv.neoforge.petroleum;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import dev.mcmod.hdfv.manufacture.Alloy;
import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.neoforge.HdfvConfig;
import dev.mcmod.hdfv.neoforge.fluid.HdfvFluidRegistry;
import dev.mcmod.hdfv.neoforge.fuel.FuelDispenserBlockEntity;
import dev.mcmod.hdfv.neoforge.fuel.HdfvFuel;
import dev.mcmod.hdfv.neoforge.material.Stock;
import dev.mcmod.hdfv.neoforge.multiblock.HdfvMultiblocks;
import dev.mcmod.hdfv.neoforge.power.HdfvPower;
import dev.mcmod.hdfv.neoforge.power.PowerBlock;
import dev.mcmod.hdfv.petroleum.OilFields;
import dev.mcmod.hdfv.petroleum.Oilwell;
import dev.mcmod.hdfv.petroleum.Refining;
import dev.mcmod.hdfv.structure.Structures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The petroleum blocks' screens in a running server (0.9): each opens for a player beside it, takes its settings from
 * that player only, refuses values out of range, keeps them when saved; and the settings do what they say: a riser at
 * 500 °C makes AeroCore's share of light cycle oil, faster strokes lift more, a choke holds a well back, a rig stops at
 * its target depth, the column's cuts and routes. Ticked directly.
 */
@PrefixGameTestTemplate(false)
public final class PetroleumScreenGameTests {
    private PetroleumScreenGameTests() {}

    /**
     * A player for the screens, not logged in (a logged-in mock player would be sent every mod's payloads it cannot
     * take): it opens the menu it is given as the server would (a fake player opens none) and reads the screen's
     * opening data as a client would.
     */
    static final class Viewer extends FakePlayer {
        BlockPos sentPos;
        PetroleumMenu.Kind sentKind;

        Viewer(ServerLevel level) { super(level, new GameProfile(UUID.randomUUID(), "hdfv-screen-test")); }

        @Override public OptionalInt openMenu(MenuProvider provider, Consumer<RegistryFriendlyByteBuf> extra) {
            AbstractContainerMenu m = provider == null ? null : provider.createMenu(1, getInventory(), this);
            if (m == null) { return OptionalInt.empty(); }
            if (extra != null) {
                RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registryAccess());
                extra.accept(buf);
                PetroleumMenu client = new PetroleumMenu(1, getInventory(), buf);
                sentPos = client.pos;
                sentKind = client.kind;
            }
            containerMenu = m;
            return OptionalInt.of(1);
        }
    }

    /** Opens a block's screen for the player, standing by it. */
    static void open(GameTestHelper h, Viewer p, BlockEntity be, PetroleumMenu.Kind kind) {
        BlockPos pos = be.getBlockPos();
        p.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 2.5);
        PetroleumMenu.open(p, be, Component.literal("test"));
        h.assertTrue(p.containerMenu instanceof PetroleumMenu m && m.pos.equals(pos) && m.kind == kind, "Its " + kind + " screen opened: " + p.containerMenu);
        h.assertTrue(pos.equals(p.sentPos) && p.sentKind == kind, "The client is told which block and screen: " + p.sentPos + " " + p.sentKind);
        h.assertFalse(((Configurable) be).view().isEmpty(), "Its screen has something to show");
    }

    /** A setting sent from the player's screen. */
    static boolean send(ServerPlayer p, BlockEntity be, String key, double value) {
        return PetroleumNetwork.apply(p, new PetroleumNetwork.SetPayload(be.getBlockPos(), key, value));
    }

    /** Its settings survive a save: saved, changed, loaded, as before. */
    static void keeps(GameTestHelper h, BlockEntity be, Consumer<Configurable> change, String key) {
        var registries = h.getLevel().registryAccess();
        Configurable c = (Configurable) be;
        CompoundTag before = c.view(), saved = be.saveWithoutMetadata(registries);
        change.accept(c);
        h.assertFalse(c.view().get(key).equals(before.get(key)), "The change took: " + key);
        be.loadWithComponents(saved, registries);
        h.assertTrue(c.view().get(key).equals(before.get(key)), "Saved and loaded: " + key + " " + c.view().get(key) + " was " + before.get(key));
    }

    static void drain(WellheadBlockEntity w) { w.handler().drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.EXECUTE); }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool")
    public static void theJackAndWellheadScreensSetAStrokeAndAChoke(GameTestHelper h) {
        BlockPos c = h.absolutePos(new BlockPos(2, 2, 4));
        HdfvMultiblocks.place(h.getLevel(), Structures.PUMP_JACK_PATTERN, c, Direction.NORTH, 1, Map.of());
        h.getLevel().setBlock(c.below(), HdfvPower.block(PowerBlock.Kind.CREATIVE_POWER).defaultBlockState(), Block.UPDATE_ALL);
        PumpJackBlockEntity jack = (PumpJackBlockEntity) h.getLevel().getBlockEntity(c);
        jack.multiblock().check(h.getLevel());
        WellheadBlockEntity w = jack.wellhead();
        h.assertTrue(w != null, "The jack formed over its wellhead");
        OilFieldCommands.force(h.getLevel(), w.getBlockPos(), 100, 1.3, true, 80e6);
        OilFields.Field f = w.field();
        double seconds = HdfvConfig.workSecondsPerTick();
        Viewer p = new Viewer(h.getLevel());
        // the wellhead: a choke holds the flow back to the orifice's rate
        open(h, p, w, PetroleumMenu.Kind.WELLHEAD);
        for (int i = 0; i < 20; i++) { w.tick(); }
        int open = w.fluid().getAmount();
        drain(w);
        h.assertTrue(send(p, w, "choke", 1) && w.view().getInt("Choke") == 1, "Choked to 8/64 in");
        double produced = OilFieldData.of(h.getLevel()).produced(f.id());
        for (int i = 0; i < 20; i++) { w.tick(); }
        double choked = 20 * f.chokedRate(w.chokeBoreM(), produced) * seconds * 1000;
        h.assertTrue(w.fluid().getAmount() < open && Math.abs(w.fluid().getAmount() - choked) <= 1 + 0.01 * choked,
                "Choked " + w.fluid().getAmount() + " l, the orifice says " + choked + "; wide open " + open);
        h.assertTrue(w.set("choke", WellheadBlockEntity.CHOKES_64THS.length) != null && w.view().getInt("Choke") == 1, "A choke it has not: refused");
        keeps(h, w, x -> x.set("choke", 5), "Choke");
        h.assertTrue(send(p, w, "choke", 0), "Wide open again");
        // the jack: drawn down, twice the strokes lift twice the oil while the field gives it
        OilFieldData.of(h.getLevel()).setProduced(f.id(), 1.01 * f.flowsUntilM3());
        open(h, p, jack, PetroleumMenu.Kind.JACK);
        h.assertTrue(send(p, jack, "stroke", 0) && send(p, jack, "plunger", 0) && send(p, jack, "spm", 4), "Short strokes, a small plunger, slow");
        double drawn = OilFieldData.of(h.getLevel()).produced(f.id());
        Oilwell.Jack slow = new Oilwell.Jack(4, Oilwell.Jack.STROKES[0], Oilwell.Jack.PLUNGERS[0]), fast = new Oilwell.Jack(8, Oilwell.Jack.STROKES[0], Oilwell.Jack.PLUNGERS[0]);
        h.assertTrue(f.pumpedInflow(drawn) > fast.displacementM3PerS(), "The field gives more than the fast jack lifts: " + f.pumpedInflow(drawn));
        w.tick();
        drain(w);
        for (int i = 0; i < 200; i++) { jack.tick(); w.tick(); }
        int a = w.fluid().getAmount();
        double expectA = 200 * Oilwell.jackRateM3PerS(f, drawn, slow) * seconds * 1000;
        h.assertTrue(jack.state() == PumpJackBlockEntity.State.PUMPING && Math.abs(a - expectA) <= 1.5, "4 strokes a minute: " + a + " l, the pump gives " + expectA);
        drain(w);
        h.assertTrue(send(p, jack, "spm", 8), "8 strokes a minute");
        for (int i = 0; i < 200; i++) { jack.tick(); w.tick(); }
        int b = w.fluid().getAmount();
        h.assertTrue(Math.abs(b - 2.0 * a) <= 3, "Twice the strokes, twice the oil: " + a + " then " + b);
        // refused, stopped, kept
        h.assertTrue(jack.set("spm", 30) != null && jack.view().getDouble("Spm") == 8, "30 strokes a minute: refused");
        h.assertTrue(jack.set("plunger", 9) != null, "A plunger it has not: refused");
        h.assertTrue(send(p, jack, "running", 0), "Stopped from its screen");
        jack.tick();
        h.assertTrue(jack.state() == PumpJackBlockEntity.State.STOPPED, "Stopped: " + jack.state());
        keeps(h, jack, x -> x.set("spm", 12), "Spm");
        keeps(h, jack, x -> x.set("stroke", 3), "Stroke");
        // only from a player with its screen open
        p.closeContainer();
        h.assertFalse(send(p, jack, "spm", 15), "No screen open: ignored");
        h.assertTrue(jack.view().getDouble("Spm") == 8, "Still 8");
        h.succeed();
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool")
    public static void theRigScreenSetsADepthItStopsAt(GameTestHelper h) {
        BlockPos at = h.absolutePos(new BlockPos(4, 2, 4));
        HdfvMultiblocks.place(h.getLevel(), Structures.DRILLING_RIG, at, Direction.NORTH, 3, Map.of());
        h.getLevel().setBlock(at.below(), HdfvPower.block(PowerBlock.Kind.CREATIVE_POWER).defaultBlockState(), Block.UPDATE_ALL);
        DrillingRigBlockEntity rig = (DrillingRigBlockEntity) h.getLevel().getBlockEntity(at);
        rig.multiblock().check(h.getLevel());
        BlockPos well = rig.rotary();
        h.assertTrue(well != null, "The rig formed");
        OilFieldCommands.force(h.getLevel(), well, 300, 1.2, true, 50e6);
        for (int i = 0; i < rig.casing().getSlots(); i++) { rig.casing().insertItem(i, Stock.of(Alloy.STEEL, 64), false); }
        Viewer p = new Viewer(h.getLevel());
        open(h, p, rig, PetroleumMenu.Kind.RIG);
        h.assertTrue(send(p, rig, "target", 100) && send(p, rig, "running", 1), "100 m, and drill");
        for (int i = 0; i < 20000 && rig.state() != DrillingRigBlockEntity.State.DRY_HOLE; i++) { rig.tick(); }
        h.assertTrue(rig.state() == DrillingRigBlockEntity.State.DRY_HOLE && Math.abs(rig.drilledM() - 100) < 1e-6,
                "Stopped at 100 m: " + rig.state() + " at " + rig.drilledM());
        h.assertTrue(rig.set("target", 80) != null && rig.targetM() == 100, "Shallower than drilled: refused");
        h.assertTrue(rig.set("target", 5000) != null && rig.targetM() == 100, "Deeper than it goes: refused");
        h.assertTrue(send(p, rig, "target", 150) && send(p, rig, "running", 1), "150 m, drill on");
        for (int i = 0; i < 20000 && (rig.state() != DrillingRigBlockEntity.State.DRY_HOLE || rig.drilledM() < 100.5); i++) { rig.tick(); }
        h.assertTrue(rig.state() == DrillingRigBlockEntity.State.DRY_HOLE && Math.abs(rig.drilledM() - 150) < 1e-6, "On to 150 m: " + rig.drilledM());
        h.assertFalse(h.getLevel().getBlockState(well).is(HdfvPetroleum.WELLHEAD.get()), "No oil above the field: no wellhead");
        keeps(h, rig, x -> x.set("target", 400), "Target");
        h.assertTrue(((PetroleumMenu) p.containerMenu).slots.get(0).mayPlace(Stock.of(Alloy.STEEL, 1)), "Its screen takes casing steel");
        h.succeed();
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool")
    public static void theColumnScreenMovesTheCutsAndRoutesTheOutlets(GameTestHelper h) {
        RefineryUnitBlockEntity col = RefineryGameTests.unit(h, new BlockPos(4, 2, 4), Structures.DISTILLATION_COLUMN, Map.of());
        col.feedHandler().fill(new FluidStack(HdfvFluidRegistry.fluid("crude_oil"), 2000), IFluidHandler.FluidAction.EXECUTE);
        Viewer p = new Viewer(h.getLevel());
        open(h, p, col, PetroleumMenu.Kind.COLUMN);
        h.assertTrue(send(p, col, "running", 0), "Stopped");
        col.tick();
        h.assertTrue(col.view().getString("State").equals("stopped") && col.feedKg() == 0, "Stopped: " + col.view().getString("State"));
        for (String[] s : new String[][]{{"naphtha", "170"}, {"kerosene", "250"}, {"diesel", "360"}, {"vacuum", "0"}, {"throughput", "0.5"}}) {
            h.assertTrue(send(p, col, s[0], Double.parseDouble(s[1])), s[0]);
        }
        CompoundTag v = col.view();
        Refining.Yield expect = Refining.distillation("crude_oil", false, new Refining.Cuts(170, 250, 360));
        for (var e : expect.products().entrySet()) {
            h.assertTrue(Math.abs(v.getCompound("Yields").getDouble(e.getKey()) - e.getValue()) < 1e-9, "Its yields at its cuts: " + e.getKey());
        }
        h.assertTrue(col.set("naphtha", 120) != null && col.set("diesel", 400) != null && col.set("throughput", 0) != null, "Cuts and throughput out of range: refused");
        h.assertTrue(col.view().getDouble("Naphtha") == 170 && col.view().getDouble("Diesel") == 360, "Its cuts as they were");
        double half = col.view().getDouble("CapacityKgPerS");
        col.set("throughput", 1);
        h.assertTrue(Math.abs(col.view().getDouble("CapacityKgPerS") - 2 * half) < 1e-9, "Half the throughput, half the capacity");
        // the fuel oil routed to the second outlet instead of the first
        List<String> names = new ArrayList<>();
        for (Tag t : col.view().getList("Routable", Tag.TAG_STRING)) { names.add(t.getAsString()); }
        h.assertTrue(send(p, col, "route.1", names.indexOf("fuel_oil")), "Fuel oil to the second outlet");
        var outs = col.view().getList("Outlets", Tag.TAG_COMPOUND);
        h.assertTrue(outs.getCompound(1).getString("Route").equals("fuel_oil") && outs.getCompound(1).getBoolean("Set") && outs.getCompound(0).getString("Route").isEmpty(),
                "Routed: " + outs);
        h.assertTrue(send(p, col, "running", 1), "Running");
        for (int i = 0; i < 40; i++) { col.tick(); }
        h.assertTrue(col.outlets().get(0).fluid().isEmpty() && col.outlets().get(1).fluid().getFluid() == HdfvFluidRegistry.fluid("fuel_oil"),
                "The residue in the second outlet: " + col.outlets().get(1).fluid().getFluid());
        h.assertTrue(col.set("route.1", -2) == null && col.view().getList("Outlets", Tag.TAG_COMPOUND).getCompound(0).getString("Route").equals("fuel_oil"), "Back to as it comes");
        keeps(h, col, x -> x.set("naphtha", 190), "Naphtha");
        keeps(h, col, x -> x.set("vacuum", 1), "VacuumOn");
        h.succeed();
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool")
    public static void anFccRiserAt500CMakesMoreLightCycleOil(GameTestHelper h) {
        RefineryUnitBlockEntity fcc = RefineryGameTests.unit(h, new BlockPos(2, 2, 2), Structures.PROCESS, Map.of('R', Structures.FCC));
        BlockPos d = new BlockPos(11, 2, 11);
        h.setBlock(d, HdfvFuel.DISPENSER.get());
        FuelDispenserBlockEntity dispenser = (FuelDispenserBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(d));
        Viewer p = new Viewer(h.getLevel());
        open(h, p, fcc, PetroleumMenu.Kind.UNIT);
        h.assertTrue(send(p, fcc, "riser", 500) && fcc.view().getDouble("Riser") == 500, "The riser at 500 °C");
        h.assertTrue(fcc.set("riser", 600) != null && fcc.set("polymer", 1) != null, "600 °C, or a polymer: refused");
        fcc.feedHandler().fill(new FluidStack(HdfvFluidRegistry.fluid("gas_oil"), 1000), IFluidHandler.FluidAction.EXECUTE);
        for (int i = 0; i < 200 && fcc.feeds[0].getFluidAmount() > 0; i++) { fcc.tick(); }
        double share = Refining.fcc(500).products().get("diesel"), diesel = 0;
        for (UnitOutletBlockEntity o : fcc.outlets()) {
            if (!o.fluid().isEmpty() && HdfvFluidRegistry.id(o.fluid().getFluid()).equals("diesel")) { diesel += o.fluid().getAmount() * RefineryUnitBlockEntity.density("diesel") / 1000; }
        }
        h.assertTrue(fcc.feedKg() > 800, "The gas oil ran: " + fcc.feedKg());
        h.assertTrue(Math.abs(diesel - share * fcc.feedKg()) <= 0.01 * fcc.feedKg(), "Light cycle oil at 500 °C: " + diesel + " of " + fcc.feedKg() + ", AeroCore says " + share);
        h.assertTrue(share > Refining.FCC.products().get("diesel"), "More than at 530 °C: " + share);
        h.assertTrue(Math.abs(RefineryGameTests.made(fcc) + fcc.burntKg() - fcc.feedKg()) <= 0.01 * fcc.feedKg(), "Mass kept");
        keeps(h, fcc, x -> x.set("riser", 545), "Riser");
        // every outlet to nothing: a product has nowhere to go
        for (int i = 0; i < fcc.outlets().size(); i++) { h.assertTrue(send(p, fcc, "route." + i, -1), "Outlet " + i + " to nothing"); }
        fcc.feedHandler().fill(new FluidStack(HdfvFluidRegistry.fluid("gas_oil"), 100), IFluidHandler.FluidAction.EXECUTE);
        fcc.tick();
        h.assertTrue(fcc.view().getString("State").equals("no_route"), "No outlet for its products: " + fcc.view().getString("State"));
        // the dispenser's screen
        open(h, p, dispenser, PetroleumMenu.Kind.DISPENSER);
        h.assertTrue(send(p, dispenser, "amount", 2) && dispenser.limitLitres() == 20, "20 litres");
        h.assertTrue(dispenser.set("amount", 7) != null && dispenser.limitLitres() == 20, "An amount it has not: refused");
        h.assertTrue(dispenser.set("start", 1) != null && !dispenser.fuelling(), "Dry, and nothing by it: not started");
        keeps(h, dispenser, x -> x.set("amount", 3), "Amount");
        h.succeed();
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool")
    public static void thePetroleumScreensAreNamed(GameTestHelper h) throws java.io.IOException {
        List<String> keys = new ArrayList<>(List.of("hdfv.gui.refused", "hdfv.gui.no_setting", "hdfv.gui.rig.shallower", "hdfv.gui.on", "hdfv.gui.off",
                "hdfv.gui.page.process", "hdfv.gui.page.yields", "hdfv.gui.page.outlets", "hdfv.gui.preset", "hdfv.gui.preset.diesel", "hdfv.gui.preset.gasoline",
                "hdfv.gui.preset.olefins", "hdfv.gui.no_vacuum", "hdfv.gui.celsius", "hdfv.gui.ron", "hdfv.gui.metres", "hdfv.gui.inches", "hdfv.gui.litres",
                "hdfv.gui.spm", "hdfv.gui.choke", "hdfv.gui.choke.open", "hdfv.gui.amount.full", "hdfv.gui.outlet", "hdfv.gui.route.none", "hdfv.gui.route.auto",
                "hdfv.gui.unit", "hdfv.gui.yields", "hdfv.gui.burnt", "hdfv.gui.feed", "hdfv.gui.capacity", "hdfv.gui.processed", "hdfv.gui.burnt_total",
                "hdfv.gui.power", "hdfv.gui.drilled", "hdfv.gui.casing_steel", "hdfv.gui.rate", "hdfv.gui.displacement", "hdfv.gui.inflow", "hdfv.gui.open_flow",
                "hdfv.gui.pressure", "hdfv.gui.depth", "hdfv.gui.produced", "hdfv.gui.pumped"));
        for (String s : List.of("running", "throughput", "naphtha", "kerosene", "diesel", "vacuum", "riser", "octane", "coil", "polymer", "route", "target",
                "spm", "stroke", "plunger", "choke", "amount", "start")) { keys.add("hdfv.gui.setting." + s); }
        for (var s : FuelDispenserBlockEntity.State.values()) { keys.add("hdfv.dispenser.state." + s.name().toLowerCase(java.util.Locale.ROOT)); }
        for (String file : List.of("en_us", "zh_tw")) {
            try (var in = Hdfv.class.getResourceAsStream("/assets/hdfv/lang/" + file + ".json")) {
                var lang = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                for (String k : keys) { h.assertTrue(lang.has(k), file + " lacks " + k); }
            }
        }
        h.succeed();
    }
}
