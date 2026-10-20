package dev.mcmod.hdfv.neoforge.villager;

import com.google.common.collect.ImmutableSet;
import dev.mcmod.hdfv.airport.AirportPlanner;
import dev.mcmod.hdfv.airport.Fixture;
import dev.mcmod.hdfv.manufacture.Alloy;
import dev.mcmod.hdfv.manufacture.Station;
import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.neoforge.airport.AirportWorldgen;
import dev.mcmod.hdfv.neoforge.airport.HdfvAirports;
import dev.mcmod.hdfv.neoforge.fluid.HdfvFluidRegistry;
import dev.mcmod.hdfv.neoforge.fluid.JerryCanItem;
import dev.mcmod.hdfv.neoforge.machine.HdfvMachines;
import dev.mcmod.hdfv.neoforge.material.HdfvMaterials;
import dev.mcmod.hdfv.neoforge.material.Stock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.ItemLike;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.Optional;

/**
 * The welder (0.7, the user: "村民要有新職業：焊工"): a villager whose job site is the welding station. Welders trade
 * filler metal and stock for the metals a shop runs short of, and a welder by a welding station (or, later, in an
 * assembly room) works it as a player would.
 */
public final class HdfvVillagers {
    private HdfvVillagers() {}

    public static final DeferredRegister<PoiType> POIS = DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE, Hdfv.MOD_ID);
    public static final DeferredRegister<VillagerProfession> PROFESSIONS = DeferredRegister.create(Registries.VILLAGER_PROFESSION, Hdfv.MOD_ID);

    public static final ResourceKey<PoiType> WELDER_POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, ResourceLocation.fromNamespaceAndPath(Hdfv.MOD_ID, "welder"));
    public static final DeferredHolder<PoiType, PoiType> WELDER_SITE = POIS.register("welder",
            () -> new PoiType(ImmutableSet.copyOf(HdfvMachines.MACHINES.get(Station.WELDING_STATION).get().getStateDefinition().getPossibleStates()), 1, 1));
    public static final DeferredHolder<VillagerProfession, VillagerProfession> WELDER = PROFESSIONS.register("welder",
            () -> new VillagerProfession("welder", p -> p.is(WELDER_POI), p -> p.is(WELDER_POI), ImmutableSet.of(), ImmutableSet.of(), SoundEvents.VILLAGER_WORK_ARMORER));

    /**
     * The tower controller (0.20): a villager whose job site is a control tower's controller block, the duty desk in the
     * tower's base. On duty (awake, near the desk) it runs the tower's traffic; it trades airfield lights, line paint,
     * windsocks, fuel and, at the top, a map to the nearest generated airport.
     */
    public static final ResourceKey<PoiType> TOWER_POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, ResourceLocation.fromNamespaceAndPath(Hdfv.MOD_ID, "tower_controller"));
    public static final DeferredHolder<PoiType, PoiType> TOWER_SITE = POIS.register("tower_controller",
            () -> new PoiType(ImmutableSet.copyOf(HdfvAirports.TOWER.get().getStateDefinition().getPossibleStates()), 1, 1));
    public static final DeferredHolder<VillagerProfession, VillagerProfession> TOWER_CONTROLLER = PROFESSIONS.register("tower_controller",
            () -> new VillagerProfession("tower_controller", p -> p.is(TOWER_POI), p -> p.is(TOWER_POI), ImmutableSet.of(), ImmutableSet.of(), SoundEvents.VILLAGER_WORK_CARTOGRAPHER));

    /** A trade: what the villager takes, what it gives, how often, the experience. */
    static VillagerTrades.ItemListing trade(ItemLike cost, int n, ItemStack give, int uses, int xp) {
        return (trader, random) -> new MerchantOffer(new ItemCost(cost, n), give.copy(), uses, xp, 0.05f);
    }

    private static ItemStack fixtures(Fixture.Kind kind, int n) { return new ItemStack(HdfvAirports.FIXTURES.get(kind).get(), n); }

    /** A map to the nearest generated airport (none offered when none within two regions round the villager is known yet). */
    static VillagerTrades.ItemListing airportMap(int emeralds) {
        return (trader, random) -> {
            if (!(trader.level() instanceof ServerLevel level) || !AirportWorldgen.planning(level)) { return null; }
            AirportPlanner.Plan p = AirportWorldgen.nearest(level, trader.getX(), trader.getZ(), 2, false);
            if (p == null) { return null; }
            var c = p.runway().centre();
            BlockPos at = BlockPos.containing(c.x(), p.surfaceY(), c.z());
            ItemStack map = MapItem.create(level, at.getX(), at.getZ(), (byte) 2, true, true);
            MapItem.renderBiomePreviewMap(level, map);
            MapItemSavedData.addTargetDecoration(map, at, "+", MapDecorationTypes.TARGET_POINT);
            map.set(DataComponents.ITEM_NAME, Component.translatable("filled_map.hdfv.airport"));
            return new MerchantOffer(new ItemCost(Items.EMERALD, emeralds), Optional.of(new ItemCost(Items.COMPASS)), map, 4, 30, 0.2f);
        };
    }

    static void trades(VillagerTradesEvent event) {
        if (event.getType() == TOWER_CONTROLLER.get()) { towerTrades(event); return; }
        if (event.getType() != WELDER.get()) { return; }
        var t = event.getTrades();
        ItemStack emerald = new ItemStack(Items.EMERALD);
        // novice: buys the shop's coal and iron, sells filler wire (steel bits)
        t.get(1).addAll(List.of(trade(Items.COAL, 15, emerald, 16, 2), trade(Items.IRON_INGOT, 4, emerald, 12, 2),
                trade(Items.EMERALD, 1, Stock.bits(Alloy.STEEL, 4), 12, 1)));
        // apprentice: copper (welding cables), steel stock
        t.get(2).addAll(List.of(trade(Items.COPPER_INGOT, 8, emerald, 12, 10), trade(Items.EMERALD, 3, Stock.of(Alloy.STEEL, 1), 12, 5)));
        // journeyman: stainless for exhausts and hulls, coke
        t.get(3).addAll(List.of(trade(HdfvMaterials.MATERIAL_ITEMS.get("coke").get(), 6, emerald, 12, 20),
                trade(Items.EMERALD, 6, Stock.of(Alloy.STAINLESS_STEEL, 1), 8, 10)));
        // expert: aluminium for airframes
        t.get(4).addAll(List.of(trade(HdfvMaterials.MATERIAL_ITEMS.get("aluminium_ingot").get(), 3, emerald, 12, 30),
                trade(Items.EMERALD, 8, Stock.of(Alloy.ALUMINIUM_2024, 1), 6, 15)));
        // master: titanium, TIG work
        t.get(5).addAll(List.of(trade(Items.EMERALD, 14, Stock.of(Alloy.TITANIUM, 1), 4, 30),
                trade(Items.EMERALD, 20, new ItemStack(HdfvMachines.MACHINES.get(Station.WELDING_STATION).get()), 2, 30)));
    }

    static void towerTrades(VillagerTradesEvent event) {
        var t = event.getTrades();
        ItemStack emerald = new ItemStack(Items.EMERALD);
        // novice: buys glass and copper (lamp lenses, cables), sells runway edge lights and white paint
        t.get(1).addAll(List.of(trade(Items.GLASS, 12, emerald, 16, 2), trade(Items.COPPER_INGOT, 8, emerald, 12, 2),
                trade(Items.EMERALD, 2, fixtures(Fixture.Kind.RUNWAY_EDGE, 4), 12, 1), trade(Items.EMERALD, 3, new ItemStack(HdfvAirports.WHITE_PAINT.get()), 8, 1)));
        // apprentice: taxiway lights, yellow paint, holding signs
        t.get(2).addAll(List.of(trade(Items.EMERALD, 2, fixtures(Fixture.Kind.TAXI_EDGE, 4), 12, 5), trade(Items.EMERALD, 3, new ItemStack(HdfvAirports.YELLOW_PAINT.get()), 8, 5),
                trade(Items.EMERALD, 2, new ItemStack(HdfvAirports.HOLD_SIGN.get(), 2), 8, 5)));
        // journeyman: windsock, threshold paint and lights, fuel
        t.get(3).addAll(List.of(trade(Items.EMERALD, 4, new ItemStack(HdfvAirports.WINDSOCK.get()), 6, 10),
                trade(Items.EMERALD, 4, new ItemStack(HdfvAirports.THRESHOLD_PAINT.get()), 6, 10), trade(Items.EMERALD, 3, fixtures(Fixture.Kind.THRESHOLD, 4), 12, 10),
                trade(Items.EMERALD, 5, JerryCanItem.filled(HdfvFluidRegistry.fluid("kerosene"), HdfvFluidRegistry.JERRY_CAN_LITRES), 6, 10)));
        // expert: PAPI units, approach flashers, floodlights, the beacon
        t.get(4).addAll(List.of(trade(Items.EMERALD, 6, fixtures(Fixture.Kind.PAPI, 4), 6, 15), trade(Items.EMERALD, 5, fixtures(Fixture.Kind.APPROACH_FLASHER, 4), 6, 15),
                trade(Items.EMERALD, 4, new ItemStack(HdfvAirports.FLOODLIGHT.get()), 6, 15), trade(Items.EMERALD, 6, fixtures(Fixture.Kind.BEACON, 1), 4, 15)));
        // master: the way to an airport, and a tower's controller
        t.get(5).addAll(List.of(airportMap(12), trade(Items.EMERALD, 24, new ItemStack(HdfvAirports.TOWER.get()), 2, 30)));
    }

    public static void register(IEventBus modBus) {
        POIS.register(modBus);
        PROFESSIONS.register(modBus);
        NeoForge.EVENT_BUS.addListener(HdfvVillagers::trades);
    }
}
