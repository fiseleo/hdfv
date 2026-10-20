package dev.mcmod.hdfv.neoforge.power;

import dev.mcmod.hdfv.neoforge.HdfvConfig;
import dev.mcmod.hdfv.power.Grid;
import dev.mcmod.hdfv.power.PowerPlant;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Electric networks (0.8): cables joined to one another and the Forge Energy handlers beside them (any mod's), found
 * from a producer (a breadth-first search, at most MAX_CABLES; a producer with no cable feeds what touches it), kept
 * until a cable or an energy block in the level changes (an epoch). Each producer of ours ticking asks its network's
 * share once a game tick (power.Grid): consumers first, batteries make up for the generators, batteries charge from
 * what is left; through the smallest cable's rating over the tick, with its I^2 R losses.
 */
public final class EnergyNetworks {
    private EnergyNetworks() {}

    public static final int MAX_CABLES = 4096;
    /** A handler offering this much or more in a tick is a source without end (the creative source). */
    public static final int ENDLESS = 1_000_000;

    private static final Map<Level, Integer> EPOCH = new WeakHashMap<>();
    private static final Map<Level, Map<Long, Network>> CACHE = new WeakHashMap<>();
    private static final Map<Level, Integer> CACHED_EPOCH = new WeakHashMap<>();

    /** A cable or an energy block came or went. */
    public static synchronized void changed(Level level) { EPOCH.merge(level, 1, Integer::sum); }

    /** A handler at a position, seen from a side. */
    public record End(BlockPos pos, Direction side) {
        IEnergyStorage handler(Level level) { return level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, side); }
    }

    /** A network: its ends, how many cable blocks, its smallest cable (null: none). */
    public static final class Network {
        public final List<End> ends;
        public final int cables;
        public final PowerPlant.CableKind weakest;
        long ticked = Long.MIN_VALUE;
        double lastCarriedFe, lastLostFe;

        Network(List<End> ends, int cables, PowerPlant.CableKind weakest) { this.ends = ends; this.cables = cables; this.weakest = weakest; }

        /** Most it carries in a tick, FE (no limit without cables). */
        public double capacityFe() {
            return weakest == null ? Double.MAX_VALUE : weakest.ratedW() * HdfvConfig.workSecondsPerTick() / HdfvConfig.joulesPerFE();
        }
        public double lastCarriedFe() { return lastCarriedFe; }
        public double lastLostFe() { return lastLostFe; }

        void tick(Level level) {
            List<IEnergyStorage> producers = new ArrayList<>(), consumers = new ArrayList<>();
            List<Grid.Party> offers = new ArrayList<>(), asks = new ArrayList<>();
            Set<IEnergyStorage> seen = new HashSet<>();
            for (End e : ends) {
                IEnergyStorage h = e.handler(level);
                if (h == null || !seen.add(h)) { continue; }
                boolean store = h instanceof BatteryBlockEntity.Store || h.canExtract() && h.canReceive();
                if (h.canExtract()) {
                    int o = h.extractEnergy(Integer.MAX_VALUE, true);
                    if (o > 0) { producers.add(h); offers.add(new Grid.Party(Math.min(o, ENDLESS), store)); }
                }
                if (h.canReceive()) {
                    int a = h.receiveEnergy(Integer.MAX_VALUE, true);
                    if (a > 0) { consumers.add(h); asks.add(new Grid.Party(a, store)); }
                }
            }
            if (producers.isEmpty() || consumers.isEmpty()) { lastCarriedFe = 0; lastLostFe = 0; return; }
            double loss = weakest == null ? 0 : weakest.lossAtRated(cables);
            Grid.Flows f = Grid.share(offers, asks, capacityFe(), loss);
            for (int i = 0; i < producers.size(); i++) {
                int take = (int) Math.round(f.given()[i]);
                if (take > 0) { producers.get(i).extractEnergy(take, false); }
            }
            for (int i = 0; i < consumers.size(); i++) {
                int put = (int) Math.floor(f.got()[i]);
                if (put > 0) { consumers.get(i).receiveEnergy(put, false); }
            }
            lastCarriedFe = f.totalGiven();
            lastLostFe = f.lost();
        }
    }

    public static synchronized Network of(Level level, BlockPos origin) {
        int epoch = EPOCH.getOrDefault(level, 0);
        Map<Long, Network> cache = CACHE.computeIfAbsent(level, l -> new HashMap<>());
        if (CACHED_EPOCH.getOrDefault(level, -1) != epoch) { cache.clear(); CACHED_EPOCH.put(level, epoch); }
        return cache.computeIfAbsent(origin.asLong(), k -> scan(level, origin));
    }

    /** A producer's tick: its network shares the tick, once a game tick. */
    public static void tick(Level level, BlockPos origin) {
        Network n = of(level, origin);
        long now = level.getGameTime();
        if (n.ticked != now) { n.ticked = now; n.tick(level); }
    }

    static Network scan(Level level, BlockPos origin) {
        List<End> ends = new ArrayList<>();
        Set<Long> endsSeen = new HashSet<>();
        ends.add(new End(origin.immutable(), null));
        endsSeen.add(origin.asLong());
        Set<BlockPos> cables = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        PowerPlant.CableKind weakest = null;
        queue.add(origin.immutable());
        while (!queue.isEmpty() && cables.size() <= MAX_CABLES) {
            BlockPos p = queue.poll();
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d).immutable();
                if (level.getBlockState(n).getBlock() instanceof CableBlock cable) {
                    if (cables.add(n)) {
                        queue.add(n);
                        if (weakest == null || cable.kind.ratedW() < weakest.ratedW()) { weakest = cable.kind; }
                    }
                } else if (!endsSeen.contains(n.asLong()) && level.getCapability(Capabilities.EnergyStorage.BLOCK, n, d.getOpposite()) != null) {
                    endsSeen.add(n.asLong());
                    ends.add(new End(n, d.getOpposite()));
                }
            }
        }
        return new Network(ends, cables.size(), weakest);
    }

    /** Draws Forge Energy from the blocks touching a position into a buffer, up to its room (a machine, a pump or a motor beside a battery or source). */
    public static int pull(Level level, BlockPos pos, IEnergyStorage into) {
        int got = 0;
        for (Direction d : Direction.values()) {
            int room = into.receiveEnergy(Integer.MAX_VALUE, true);
            if (room <= 0) { break; }
            IEnergyStorage n = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos.relative(d), d.getOpposite());
            if (n == null || !n.canExtract()) { continue; }
            int e = n.extractEnergy(room, false);
            got += into.receiveEnergy(e, false);
        }
        return got;
    }
}
