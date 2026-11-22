package dev.mcmod.hdfv.neoforge.worksite;

import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.earthmoving.Material;
import dev.mcmod.aerocore.api.earthmoving.Piling;
import dev.mcmod.aerocore.api.earthmoving.RockCutting;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.Vehicle;
import dev.mcmod.aerocore.api.vehicle.VehicleState;
import dev.mcmod.aerocore.neoforge.vehicle.PartVehicle;
import dev.mcmod.hdfv.part.machine.LinkDesign;
import dev.mcmod.hdfv.part.machine.WorkToolDesign;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

/**
 * 0.26: the world work of the work tools that do not cut (WorldWork does the cutting, heaping and pouring), each at its
 * tool's work point (WorkToolDesign.workPoint) while its function's channel says so (WorkInputs):
 * <ul>
 * <li>A hydraulic hammer ("hammer" on) breaks the block at its chisel: BLOWS_HZ blows a second of its impact energy,
 *     each breaking RockCutting.blowM3 of the block's material at its specific energy (the typical efficiency) of the rock
 *     mass, not the intact rock (a hammer breaks it along its joints: Hoek-Brown's factor at HAMMER_GSI, a blocky face;
 *     about 25 MJ/m^3 for 150 MPa stone, within the 5 to 30 MJ/m^3 of breakers in practice); a whole
 *     cubic metre broken leaves the block's broken form (EarthMaterials.brokenForm: rock to blasted rock).</li>
 * <li>A pile hammer ("hammer" on) drives the pile under its helmet: PILE_BLOWS_HZ blows, each setting the pile by
 *     Hiley's formula (ram, drop, PILE_EFFICIENCY, a PILE_SIDE_M square concrete pile of its driven length, restitution
 *     PILE_RESTITUTION, PILE_QUAKE_M) against the static resistance of its embedment in the ground under its toe
 *     (Piling.staticResistanceN); each metre driven turns the cell under the toe into pile.</li>
 * <li>A plate compactor ("vibration" on) and a screed's tamper compact what is under them: loose fill a pass every
 *     PASS_TICKS, compacted base after PLATE_PASSES (a rule: 4 to 8 passes for a plate on a 0.3 m lift), hot asphalt
 *     rolled (HotAsphaltBlock).</li>
 * <li>A spray bar ("spray" on) waters along its width: farmland moist, fire out.</li>
 * <li>A screed paves while the machine moves and a bulk hold carries hot mix or concrete: each cell its paving line
 *     crosses gets its top block replaced by the hot mat or fresh concrete (a pavement replaces the surface, it does not
 *     rise a block), taking the mat's thickness over a square metre from the hold.</li>
 * <li>A conveyor ("conveyor" on) carries what the machine's other bulk holds hold to its head at its belt's rate (a
 *     troughed belt's load area CONVEYOR_LOAD_SHARE of its width squared, a rule) and pours it there (WorldWork.pourFrom).</li>
 * <li>A mixer drum ("mixer" on, its chute's channel over 0.3) and a concrete pump's placing boom ("pump" on) pour concrete
 *     from the holds at the chute's end or the boom's tip at MIXER_M3_PER_S and PUMP_M3_PER_S (rules: a truck's chute about
 *     half a cubic metre a minute, a boom pump 90 m^3 an hour).</li>
 * <li>A segment erector ("erector" over 0.5) builds a ring of lining against the tunnel wall round the cutterhead's axis
 *     at its point, every RING_TICKS: each open cell on the ring with rock or soil beyond it becomes tunnel lining.</li>
 * </ul>
 * Forks, grapples, work platforms and quick couplers work by their physics (their contact spheres, decks and joints).
 */
public final class WorkRules {
    private WorkRules() {}

    public static final double BLOWS_HZ = 8, PILE_BLOWS_HZ = 0.8, PILE_EFFICIENCY = 0.8, PILE_SIDE_M = 0.4, PILE_RESTITUTION = 0.4, PILE_QUAKE_M = 0.01,
            PILE_KG_PER_M = 2400 * PILE_SIDE_M * PILE_SIDE_M, CONVEYOR_LOAD_SHARE = 0.1, MIXER_M3_PER_S = 0.5 / 60, PUMP_M3_PER_S = 90 / 3600.0;
    public static final int PASS_TICKS = 20, PLATE_PASSES = 6, RING_TICKS = 40;
    /** The rock mass a hammer breaks: a blocky face's geological strength index, undisturbed (a rule). */
    public static final double HAMMER_GSI = 50;
    private static final double TICK = 0.05;
    /** A plate's passes by cell (transient). */
    private static final Map<Long, Integer> PASSES = new HashMap<>();

    /** A blade's capacity (loose m^3) as its design gives it, or 0 when it does not say. */
    static double bladeCapacityM3(PartDesign design) {
        return design instanceof WorkToolDesign w ? w.bladeCapacityM3() : 0;
    }

    /** Which way a bulk hold's part opens (part frame): up for a dump body or a hopper. */
    static Vec3d openingPart(PartDesign design) { return Vec3d.Y; }

    /** Whether a hold's part pours when tipped (a mixer drum pours by its own chute, a hopper feeds its screed, a conveyor its head). */
    static boolean pours(PartDesign design, PartVehicle v, String partId) {
        return !(design instanceof WorkToolDesign w) || switch (w.toolKind()) {
            case MIXER_DRUM, PAVER_HOPPER, CONVEYOR, MILLING_DRUM, BROOM, CUTTERHEAD, BUCKET_WHEEL -> false;
            default -> true;
        };
    }

    /** The kinds' own work this tick; whether any bulk changed. */
    static boolean work(ServerLevel level, PartVehicle v, Vehicle vehicle, VehicleState st, BulkCargo bulk) {
        boolean changed = false;
        Map<String, Double> c = v.controls();
        long now = level.getGameTime();
        for (Vehicle.Installed p : vehicle.installed()) {
            PartDesign d = p.part().design();
            if (d instanceof LinkDesign l && l.linkKind() == LinkDesign.LinkKind.PLACING_BOOM && on(c, "pump") && !hasChildBoom(vehicle, p)) {
                Vec3d tip = v.partPoint(p, new Vec3d(l.lengthM(), 0, 0), 1f);
                changed |= pourConcrete(level, v, bulk, tip, PUMP_M3_PER_S * TICK);
                continue;
            }
            if (!(d instanceof WorkToolDesign w)) { continue; }
            WorkToolDesign.WorkPoint wp = w.workPoint();
            Vec3d at = v.partPoint(p, wp.point(), 1f);
            switch (w.toolKind()) {
                case HYDRAULIC_HAMMER -> { if (on(c, "hammer")) { hammer(level, v, w, at, v.partDirection(p, wp.direction(), 1f)); } }
                case PILE_HAMMER -> { if (on(c, "hammer") && now % Math.round(20 / PILE_BLOWS_HZ) == 0) { pile(level, v, w, at); } }
                case COMPACTOR_PLATE -> { if (on(c, "vibration") && now % PASS_TICKS == 0) { compact(level, v, at, wp.widthM()); } }
                case SPRAY_BAR -> { if (on(c, "spray") && now % 10 == 0) { spray(level, v, at, v.partDirection(p, Vec3d.Z, 1f), wp.widthM()); } }
                case SCREED -> changed |= pave(level, v, bulk, at, v.partDirection(p, Vec3d.Z, 1f), wp.widthM(), w.matThicknessM());
                case CONVEYOR -> { if (on(c, "conveyor")) { changed |= convey(level, v, vehicle, bulk, p, w, at); } }
                case MIXER_DRUM -> { if (on(c, "mixer") && c.getOrDefault("chute", 0.0) > 0.3) { changed |= pourConcrete(level, v, bulk, at, MIXER_M3_PER_S * TICK); } }
                case SEGMENT_ERECTOR -> { if (c.getOrDefault("erector", 0.0) > 0.5 && now % RING_TICKS == 0) { ring(level, v, vehicle, at); } }
                default -> { }
            }
        }
        return changed;
    }

    private static boolean on(Map<String, Double> c, String channel) { return c.getOrDefault(channel, 0.0) > 0.5; }

    private static boolean hasChildBoom(Vehicle vehicle, Vehicle.Installed p) {
        for (Vehicle.Installed o : vehicle.installed()) {
            if (o != p && p.id().equals(o.mount().attachPart()) && o.part().design() instanceof LinkDesign l && l.linkKind() == LinkDesign.LinkKind.PLACING_BOOM) { return true; }
        }
        return false;
    }

    // ---------------------------------------------------------------- the kinds

    public static void hammer(ServerLevel level, PartVehicle v, WorkToolDesign w, Vec3d at, Vec3d down) {
        BlockPos pos = BlockPos.containing(at.x() + 0.2 * down.x(), at.y() + 0.2 * down.y(), at.z() + 0.2 * down.z());
        BlockState s = level.getBlockState(pos);
        Material m = EarthMaterials.material(s);
        Block broken = EarthMaterials.brokenForm(s);
        if (m == null || broken == null || EarthMaterials.undiggable(level, pos, s) || !WorldWork.may(level, v, pos, s)) { return; }
        double strength = m.isRock() ? m.compressivePa() * RockCutting.rockMassFactor(HAMMER_GSI, 0) : Math.max(m.compressivePa(), 1e6);
        double m3 = BLOWS_HZ * TICK * RockCutting.blowM3(Math.max(w.impactEnergyJ(), 0), RockCutting.specificEnergyPa(strength, RockCutting.TYPICAL_EFFICIENCY));
        DigProgress dig = DigProgress.of(level);
        if (dig.add(pos, s, m3) >= 1) {
            level.setBlock(pos, broken.defaultBlockState(), Block.UPDATE_ALL);
            dig.clear(pos);
        }
    }

    static void pile(ServerLevel level, PartVehicle v, WorkToolDesign w, Vec3d at) {
        BlockPos top = BlockPos.containing(at.x(), at.y() - 0.3, at.z());
        BlockPos toe = top;
        int driven = 0;
        while (level.getBlockState(toe).is(HdfvWorksite.PILE.get()) && driven < 64) { toe = toe.below(); driven++; }
        BlockState ground = level.getBlockState(toe);
        Material m = EarthMaterials.material(ground);
        if (m == null || EarthMaterials.undiggable(level, toe, ground) || !WorldWork.may(level, v, toe, ground)) { return; }
        double resistance = Piling.staticResistanceN(m, 4 * PILE_SIDE_M, PILE_SIDE_M * PILE_SIDE_M, driven + 0.5);
        double set = Piling.hileySetM(resistance, Math.max(w.ramMassKg(), 1), Math.max(w.dropM(), 0.01), PILE_EFFICIENCY,
                PILE_KG_PER_M * (driven + 1), PILE_RESTITUTION, PILE_QUAKE_M);
        if (!(set > 0)) { return; }   // refusal
        DigProgress dig = DigProgress.of(level);
        if (dig.add(toe, ground, Math.min(set, 1)) >= 1) {
            level.setBlock(toe, HdfvWorksite.PILE.get().defaultBlockState(), Block.UPDATE_ALL);
            dig.clear(toe);
        }
    }

    public static void compact(ServerLevel level, PartVehicle v, Vec3d at, double widthM) {
        BlockPos pos = BlockPos.containing(at.x(), at.y() - 0.3, at.z());
        BlockState s = level.getBlockState(pos);
        if (s.is(HdfvWorksite.HOT_ASPHALT.get())) { HotAsphaltBlock.rolled(level, pos, s); return; }
        if (!s.is(HdfvWorksite.LOOSE_FILL.get()) || !WorldWork.may(level, v, pos, s)) { return; }
        int n = PASSES.merge(pos.asLong(), 1, Integer::sum);
        if (n >= PLATE_PASSES) {
            PASSES.remove(pos.asLong());
            level.setBlock(pos, HdfvWorksite.COMPACTED_BASE.get().defaultBlockState(), Block.UPDATE_ALL);
        }
        if (PASSES.size() > 1 << 14) { PASSES.clear(); }
    }

    static void spray(ServerLevel level, PartVehicle v, Vec3d at, Vec3d across, double widthM) {
        int n = Math.max(1, (int) Math.round(widthM));
        for (int i = 0; i < n; i++) {
            double t = n == 1 ? 0 : -0.5 * widthM + widthM * i / (n - 1);
            BlockPos column = BlockPos.containing(at.x() + t * across.x(), at.y(), at.z() + t * across.z());
            for (int k = 0; k < 4; k++) {
                BlockPos pos = column.below(k);
                BlockState s = level.getBlockState(pos);
                if (s.getBlock() instanceof BaseFireBlock) { level.removeBlock(pos, false); continue; }
                if (s.getBlock() instanceof FarmBlock && s.getValue(FarmBlock.MOISTURE) < FarmBlock.MAX_MOISTURE) {
                    level.setBlock(pos, s.setValue(FarmBlock.MOISTURE, FarmBlock.MAX_MOISTURE), Block.UPDATE_CLIENTS);
                }
                if (!s.isAir()) { break; }
            }
        }
    }

    /** What a hold's form lays as a pavement (null: not a paving mix). */
    static Block paving(String form) {
        Block b = WorldWork.block(form);
        if (b == HdfvWorksite.HOT_ASPHALT.get()) { return b; }
        if (b == HdfvWorksite.FRESH_CONCRETE.get()) { return b; }
        return null;
    }

    static boolean pave(ServerLevel level, PartVehicle v, BulkCargo bulk, Vec3d at, Vec3d across, double widthM, double matM) {
        if (v.velocity().length() < 0.02) { return false; }
        boolean changed = false;
        int n = Math.max(1, (int) Math.ceil(widthM));
        for (int i = 0; i < n; i++) {
            double t = n == 1 ? 0 : -0.5 * widthM + widthM * (i + 0.5) / n;
            BlockPos column = BlockPos.containing(at.x() + t * across.x(), at.y() + 0.5, at.z() + t * across.z());
            BlockPos top = null;
            for (int k = 0; k < 3; k++) {
                BlockPos pos = column.below(k);
                if (!level.getBlockState(pos).isAir()) { top = pos; break; }
            }
            if (top == null) { continue; }
            BlockState s = level.getBlockState(top);
            if (s.is(HdfvWorksite.HOT_ASPHALT.get()) || s.is(HdfvWorksite.FRESH_CONCRETE.get()) || s.hasBlockEntity() || !WorldWork.may(level, v, top, s)) { continue; }
            // the mix from a bulk hold that carries one
            for (Map.Entry<String, BulkCargo.Load> e : bulk.holds().entrySet()) {
                BulkCargo.Load l = e.getValue();
                if (l.isEmpty()) { continue; }
                Block lay = paving(l.main());
                if (lay == null) { continue; }
                double need = Math.max(matM, 0.02);
                if (l.bankM3() < need) { continue; }
                // 0.26: hot mix lays at its temperature (cold: it crumbles), concrete past its initial set does not flow
                BlockState laid = lay.defaultBlockState();
                if (lay == HdfvWorksite.HOT_ASPHALT.get()) {
                    double share = (l.temperatureK - dev.mcmod.aerocore.api.earthmoving.Asphalt.CESSATION_K)
                            / (dev.mcmod.aerocore.api.earthmoving.Asphalt.LAYING_K - dev.mcmod.aerocore.api.earthmoving.Asphalt.CESSATION_K);
                    int heat = (int) Math.round(Math.clamp(share, 0, 1) * 7);
                    laid = heat <= 0 ? HdfvWorksite.RECLAIMED_ASPHALT.get().defaultBlockState() : laid.setValue(HotAsphaltBlock.HEAT, heat);
                } else if (l.maturityKs >= dev.mcmod.aerocore.api.earthmoving.Concrete.INITIAL_SET_KS) {
                    continue;
                }
                l.take(need);
                level.setBlock(top, laid, Block.UPDATE_ALL);
                changed = true;
                break;
            }
        }
        return changed;
    }

    static boolean convey(ServerLevel level, PartVehicle v, Vehicle vehicle, BulkCargo bulk, Vehicle.Installed self, WorkToolDesign w, Vec3d head) {
        double rate = CONVEYOR_LOAD_SHARE * w.toolWidthM() * w.toolWidthM() * Math.max(w.beltSpeedMps(), 0) * TICK;
        if (!(rate > 0)) { return false; }
        for (Vehicle.Hold h : vehicle.holds()) {
            if (!BulkCargo.bulk(h) || h.id().startsWith(self.id() + ".")) { continue; }
            BulkCargo.Load from = bulk.holds().get(h.id());
            if (from == null || from.isEmpty()) { continue; }
            BulkCargo.Load moving = from.take(Math.min(rate, from.bankM3()));
            BulkCargo.Load back = new BulkCargo.Load();
            WorldWork.pourFrom(level, v, head, moving, back);
            from.add(back);
            return true;
        }
        return false;
    }

    static boolean pourConcrete(ServerLevel level, PartVehicle v, BulkCargo bulk, Vec3d at, double m3) {
        for (BulkCargo.Load l : bulk.holds().values()) {
            if (l.isEmpty() || WorldWork.block(l.main()) != HdfvWorksite.FRESH_CONCRETE.get()) { continue; }
            if (l.maturityKs >= dev.mcmod.aerocore.api.earthmoving.Concrete.INITIAL_SET_KS) { continue; }   // set in the drum
            BulkCargo.Load moving = l.take(Math.min(m3, l.bankM3()));
            BulkCargo.Load back = new BulkCargo.Load();
            WorldWork.pourFrom(level, v, at, moving, back);
            l.add(back);
            return true;
        }
        return false;
    }

    static void ring(ServerLevel level, PartVehicle v, Vehicle vehicle, Vec3d at) {
        double radius = 0;
        Vehicle.Installed head = null;
        for (Vehicle.Installed p : vehicle.installed()) {
            if (p.part().design() instanceof WorkToolDesign w && w.toolKind() == WorkToolDesign.Kind.CUTTERHEAD) { radius = w.cutterRadiusM(); head = p; }
        }
        if (head == null || !(radius > 0.5)) { return; }
        Vec3d axis = v.partDirection(head, Vec3d.X, 1f).normalized();
        Vec3d u = Math.abs(axis.y()) < 0.9 ? axis.cross(Vec3d.Y).normalized() : axis.cross(Vec3d.X).normalized(), w2 = axis.cross(u).normalized();
        Vec3d centre = v.partPoint(head, Vec3d.ZERO, 1f);
        // the ring at the erector's place along the tunnel
        double along = at.subtract(centre).dot(axis);
        Vec3d c = centre.add(axis.scale(along));
        int steps = (int) Math.ceil(2 * Math.PI * radius * 2);
        for (int i = 0; i < steps; i++) {
            double a = 2 * Math.PI * i / steps;
            Vec3d dir = u.scale(Math.cos(a)).add(w2.scale(Math.sin(a)));
            Vec3d p = c.add(dir.scale(radius - 0.5)), beyond = c.add(dir.scale(radius + 0.5));
            BlockPos pos = BlockPos.containing(p.x(), p.y(), p.z()), out = BlockPos.containing(beyond.x(), beyond.y(), beyond.z());
            BlockState s = level.getBlockState(pos), o = level.getBlockState(out);
            if (!s.isAir() || o.isAir() || o.liquid() || !WorldWork.may(level, v, pos, s)) { continue; }
            level.setBlock(pos, HdfvWorksite.TUNNEL_LINING.get().defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    /** Blocks the rules here place (for the tests). */
    static boolean placed(BlockState s) {
        return s.is(HdfvWorksite.PILE.get()) || s.is(HdfvWorksite.TUNNEL_LINING.get()) || s.is(HdfvWorksite.COMPACTED_BASE.get()) || s.is(Blocks.FARMLAND);
    }
}
