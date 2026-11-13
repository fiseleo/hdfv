package dev.mcmod.hdfv.neoforge.damage;

import dev.mcmod.aerocore.api.ContactMaterial;
import dev.mcmod.aerocore.api.RigidBodyState;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.damage.Corrosion;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.Vehicle;
import dev.mcmod.aerocore.api.vehicle.VehicleState;
import dev.mcmod.aerocore.neoforge.probe.SeaSurface;
import dev.mcmod.aerocore.neoforge.vehicle.PartVehicle;
import dev.mcmod.aerocore.neoforge.vehicle.VehicleContacts;
import dev.mcmod.hdfv.damage.Conditions;
import dev.mcmod.hdfv.damage.DamagePace;
import dev.mcmod.hdfv.damage.Elements;
import dev.mcmod.hdfv.damage.EngineWear;
import dev.mcmod.hdfv.damage.Impacts;
import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.neoforge.HdfvConfig;
import dev.mcmod.hdfv.neoforge.assembly.HdfvVehicle;
import dev.mcmod.hdfv.neoforge.fuel.Fuelling;
import dev.mcmod.hdfv.part.FloatDesign;
import dev.mcmod.hdfv.part.HullDesign;
import dev.mcmod.hdfv.part.MotorDesign;
import dev.mcmod.hdfv.part.PlaningHullDesign;
import dev.mcmod.hdfv.part.body.BodyDesign;
import dev.mcmod.hdfv.part.body.BodySettingsDesign;
import dev.mcmod.hdfv.part.chassis.ChassisDesign;
import dev.mcmod.hdfv.part.engine.PistonEngineDesign;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * 0.25: what damages vehicles in the world (the user 2026-11-12: collisions, wear in use, attacks, explosions and fire,
 * water and corrosion; realistic by default; those aboard are hurt). Server side; it works on AeroCore's PartVehicle, so
 * on vehicles spawned by command as on assembled ones. The rules and their numbers are core's (damage.Conditions,
 * Impacts, Elements, EngineWear); here they meet the world:
 *
 * <ul>
 * <li>Collisions (AeroCore's VehicleContacts.Contacted, a tick's summed impact): the energy the hit leaves (the share
 *     1 - e of the contact's impulse, e its restitution at that speed: a rebound's energy came back) times
 *     collisionScale goes to the parts by Impacts.crash. Those aboard lose health by their change of velocity
 *     (Impacts.injury; damage type hdfv:crash).</li>
 * <li>Wear: the parts' own (compiled in: Wearings, EngineWear); here only the pace reaches them, set from the server's
 *     settings when they load (DamagePace).</li>
 * <li>Attacks (AeroCore's PartVehicle.Hurt): an explosion's damage as a blast's energy at its place (Elements.blastJ);
 *     a blow's or a shot's as a small energy on the part nearest whoever or whatever struck (Elements.blowJ), a shot by
 *     a wheel now and then letting its tyre down (the world's random numbers).</li>
 * <li>Fire and lava use up what burns (Conditions.FIRE) and burn those aboard. Vehicles' entity types (AeroCore's and
 *     hdfv's) are immune to fire, so vanilla never tells them of it: the once-a-second look finds the parts standing in
 *     lava or a fire block itself, and keeps its own count of how long a vehicle burns after (Elements; water and rain
 *     put it out). Should a vehicle's type not be immune, vanilla's fire and lava damage arrive as attacks do and are
 *     taken the same way. Fuel aboard explodes once (no blocks broken) when fire meets a leaking tank or a blast or
 *     fire bursts one; the vehicle is then burnt out.</li>
 * <li>Once a second for each vehicle (elements): fire as above, engines and motors under water (machinery in something
 *     that floats stays dry down to Elements.DRY_HULL_M while its hull is sound), corrosion where each part stands (kept
 *     with the vehicle and added in steps of Elements.CORROSION_STEP), leaking tanks, and the wreck rule (a body or
 *     chassis used up takes its engines with it).</li>
 * </ul>
 *
 * With damage off (HdfvConfig.damage) none of this happens; what is damaged stays so. Tests set their own settings with
 * override().
 */
public final class VehicleDamage {
    private VehicleDamage() {}

    /** The server's damage settings. */
    public record Settings(boolean enabled, double collision, double attack, double corrosion, boolean occupants, boolean explosions) {
        public static final Settings DEFAULT = new Settings(true, 1, 1, 1, true, true), OFF = new Settings(false, 1, 1, 1, true, true);
    }

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static volatile Settings override;

    /** Development and tests: settings in place of the server's (null: the server's again). */
    public static void override(Settings settings) { override = settings; }

    public static Settings settings() {
        Settings o = override;
        return o != null ? o : new Settings(HdfvConfig.damage(), HdfvConfig.collisionScale(), HdfvConfig.attackScale(), HdfvConfig.corrosionScale(),
                HdfvConfig.occupantInjury(), HdfvConfig.explosions());
    }

    /** The damage type of a crash's injuries (data/hdfv/damage_type/crash.json; it passes armour as flying into a wall does). */
    public static final ResourceKey<DamageType> CRASH = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(Hdfv.MOD_ID, "crash"));

    public static void register(IEventBus modBus) {
        modBus.addListener((ModConfigEvent.Loading e) -> pace(e));
        modBus.addListener((ModConfigEvent.Reloading e) -> pace(e));
        NeoForge.EVENT_BUS.addListener(VehicleDamage::contacted);
        NeoForge.EVENT_BUS.addListener(VehicleDamage::hurt);
        NeoForge.EVENT_BUS.addListener(VehicleDamage::tick);
        NeoForge.EVENT_BUS.addListener(VehicleDamage::interact);
    }

    /** The server's settings give core's clock its pace (parts compiled from now on wear by it; corrosion follows at once). */
    private static void pace(ModConfigEvent e) {
        if (e.getConfig().getSpec() != HdfvConfig.SPEC) { return; }
        boolean on = HdfvConfig.damage();
        DamagePace.set(HdfvConfig.ticksPerWorkHour(), on ? HdfvConfig.wearScale() : 0, on ? HdfvConfig.corrosionScale() : 0);
    }

    // ----- where things are -----

    /** A world point in the vehicle's frame (the frame parts are mounted in). */
    public static Vec3d local(PartVehicle v, Vec3d world) {
        RigidBodyState b = v.vehicleState().body();
        return b.orientation().inverse().rotate(world.subtract(b.position())).add(v.vehicle().properties().centerOfMassLocal());
    }

    /** Where a part's centre of mass is in the world. */
    public static Vec3d world(PartVehicle v, Vehicle.Installed part) { return v.partPoint(part, part.part().mass().centre(), 0); }

    private static boolean simulated(PartVehicle v) {
        return !v.level().isClientSide && !v.isRemoved() && v.vehicle() != null && v.vehicleState() != null && !(v instanceof HdfvVehicle h && !h.assembled());
    }

    // ----- applying -----

    /** Adds damage to a vehicle's conditions and tells its clients; then the wreck rule. True when anything was added. */
    public static boolean apply(PartVehicle v, List<Impacts.Dealt> dealt) {
        if (dealt.isEmpty() || !simulated(v)) { return false; }
        VehicleState st = v.vehicleState();
        Impacts.Dealt last = null;
        for (Impacts.Dealt d : dealt) {
            Vehicle.Installed p = v.vehicle().installed(d.partId());
            int i = p == null ? -1 : p.part().conditions().indexOf(d.condition());
            if (i < 0 || !(d.amount() > 0) || !Double.isFinite(d.amount())) { continue; }
            st.addUsed(d.partId(), i, d.amount());
            last = d;
        }
        if (last == null) { return false; }
        wreck(v);
        v.addUsed(last.partId(), last.condition(), 0);   // publishes the lot
        return true;
    }

    /** Vehicles whose wreck rule has been applied (so a later repair of an engine is not undone while the body waits for its own). */
    private static final Map<PartVehicle, Boolean> WRECKED = new WeakHashMap<>();

    /**
     * The wreck rule: once a body's or a chassis's structure is used up, the vehicle is a wreck: every engine's
     * ancillaries (a duct engine's or booster's structure, a motor) are set to failed, once. It can still be towed,
     * taken apart, and repaired by replacing what failed.
     */
    private static boolean wreck(PartVehicle v) {
        VehicleState st = v.vehicleState();
        boolean wrecked = false;
        for (Vehicle.Installed p : v.vehicle().installed()) {
            PartDesign d = p.part().design();
            if ((d instanceof BodyDesign || d instanceof ChassisDesign) && st.used(p.id(), Conditions.STRUCTURE) >= 1) { wrecked = true; }
        }
        synchronized (WRECKED) {
            if (!wrecked) { WRECKED.remove(v); return false; }
            if (WRECKED.put(v, Boolean.TRUE) != null) { return false; }
        }
        boolean changed = false;
        for (Vehicle.Installed p : v.vehicle().installed()) {
            String c = engineKill(p);
            if (c != null && st.used(p.id(), c) < 1) { st.setUsed(p.id(), p.part().conditions().indexOf(c), 1); changed = true; }
        }
        return changed;
    }

    /** A crash's damage source (flying into a wall's, should the data pack's type be missing). */
    private static DamageSource crash(PartVehicle v) {
        try { return v.damageSources().source(CRASH); }
        catch (RuntimeException missing) { return v.damageSources().flyIntoWall(); }
    }

    /** The condition whose failure stops a part that drives the vehicle (null for parts that are not engines). */
    public static String engineKill(Vehicle.Installed p) { return dev.mcmod.hdfv.repair.RepairRules.engineCondition(p.part()); }   // 0.25: core's, the repairs read it too

    // ----- collisions -----

    private static void contacted(VehicleContacts.Contacted e) {
        VehicleContacts.Impact impact = e.impact();
        PartVehicle v = e.vehicle();
        if (impact == null || !simulated(v)) { return; }
        Settings s = settings();
        if (!s.enabled()) { return; }
        double approach = impact.approachMps(), impulse = impact.impulseNs();
        if (!(approach > Impacts.ELASTIC_MPS) || !(impulse > 0) || !Double.isFinite(impulse)) { return; }
        ContactMaterial material = VehicleContacts.material(impact.material());
        double rebound = material == null ? 0 : Math.clamp(material.restitutionAt(approach), 0.0, 1.0);
        Vehicle vehicle = v.vehicle();
        VehicleState st = v.vehicleState();
        // the body as it was when it hit is what protected those aboard
        double body = 0;
        for (Vehicle.Installed p : vehicle.installed()) { if (p.part().design() instanceof BodyDesign) { body = Math.max(body, st.used(p.id(), Conditions.STRUCTURE)); } }
        if (s.collision() > 0) {
            List<Impacts.Dealt> dealt = Impacts.crash(vehicle, local(v, impact.point()), impulse * (1 - rebound), approach, s.collision());
            if (apply(v, dealt) && LOGGER.isDebugEnabled()) {
                LOGGER.debug("Vehicle {} hit at {} m/s with {} N s: {}", v.blueprint() == null ? "?" : v.blueprint().name(), approach, impulse, dealt);
            }
        }
        if (s.occupants() && !v.getPassengers().isEmpty()) {
            double health = Impacts.injury(impulse, vehicle.properties().massKg() + st.fuelKg() + st.cargoKg(), body);
            if (health > 0) {
                DamageSource source = crash(v);
                for (Entity rider : List.copyOf(v.getPassengers())) { if (rider instanceof LivingEntity) { rider.hurt(source, (float) health); } }
            }
        }
    }

    // ----- attacks, explosions, fire -----

    /** Set while a vehicle's own fuel explodes: what that blast does to it is the burnt-out rule's, not another hit. */
    private static final ThreadLocal<Boolean> EXPLODING = ThreadLocal.withInitial(() -> false);

    private static void hurt(PartVehicle.Hurt e) {
        PartVehicle v = e.vehicle();
        Settings s = settings();
        if (!s.enabled() || !(s.attack() > 0) || !simulated(v) || EXPLODING.get()) { return; }
        DamageSource source = e.source();
        double amount = e.amount();
        if (!(amount > 0) || !Double.isFinite(amount)) { return; }
        Vehicle vehicle = v.vehicle();
        VehicleState st = v.vehicleState();
        if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            net.minecraft.world.phys.Vec3 at = source.getSourcePosition();
            Vec3d point = at == null ? vehicle.properties().centerOfMassLocal() : local(v, new Vec3d(at.x, at.y, at.z));
            apply(v, Impacts.hit(vehicle, point, s.attack() * Elements.blastJ(amount)));
            e.take();
            burst(v, s, false);
        } else if (source.is(DamageTypeTags.IS_FIRE)) {
            // only a vehicle whose type is not immune to fire hears of it this way (see burn and the once-a-second look)
            List<Impacts.Dealt> dealt = new ArrayList<>();
            for (Vehicle.Installed p : vehicle.installed()) { burn(p, s.attack() * amount * (source.is(DamageTypes.LAVA) ? Conditions.LAVA_FACTOR : 1), dealt); }
            apply(v, dealt);
            e.take();
            scorch(v, s, Math.min(amount, 4));
            burst(v, s, true);
        } else if (source.getDirectEntity() != null) {
            Entity by = source.getDirectEntity();
            net.minecraft.world.phys.Vec3 at = by.getBoundingBox().getCenter();
            Vehicle.Installed part = Impacts.nearest(vehicle, local(v, new Vec3d(at.x, at.y, at.z)));
            if (part == null) { return; }
            List<Impacts.Dealt> dealt = new ArrayList<>(Impacts.blow(part, s.attack() * Elements.blowJ(amount)));
            if (source.is(DamageTypeTags.IS_PROJECTILE) && part.part().conditions().contains(Conditions.TYRES) && v.level().random.nextInt(Elements.PUNCTURE_ONE_IN) == 0) {
                dealt.add(new Impacts.Dealt(part.id(), Conditions.TYRES, 1));
            }
            apply(v, dealt);
            e.take();
        }
    }

    /** What so many points of fire damage use of a part's conditions that burn (Conditions.FIRE), added to dealt. */
    private static void burn(Vehicle.Installed p, double points, List<Impacts.Dealt> dealt) {
        if (!(points > 0)) { return; }
        for (String c : p.part().conditions()) {
            Double rate = Conditions.FIRE.get(c);
            if (rate != null) { dealt.add(new Impacts.Dealt(p.id(), c, rate * points)); }
        }
    }

    /** Those aboard a burning vehicle burn with it. */
    private static void scorch(PartVehicle v, Settings s, double points) {
        if (!s.occupants() || !(points > 0) || v.getPassengers().isEmpty()) { return; }
        DamageSource burning = v.damageSources().onFire();
        for (Entity rider : List.copyOf(v.getPassengers())) { if (rider instanceof LivingEntity) { rider.hurt(burning, (float) points); } }
    }

    /** Seconds each vehicle still burns by the game's own count (vehicles immune to vanilla's fire). */
    private static final Map<PartVehicle, double[]> BURNING = new WeakHashMap<>();

    /** Whether a vehicle burns: by the game's own count, or by vanilla's when its type is not immune to fire. */
    public static boolean burning(PartVehicle v) {
        synchronized (BURNING) {
            double[] left = BURNING.get(v);
            if (left != null && left[0] > 0) { return true; }
        }
        return v.isOnFire();
    }

    /** Sets a vehicle burning for some seconds at least (0 puts it out). */
    public static void ignite(PartVehicle v, double seconds) {
        synchronized (BURNING) {
            if (!(seconds > 0)) { BURNING.remove(v); return; }
            double[] left = BURNING.computeIfAbsent(v, k -> new double[1]);
            left[0] = Math.max(left[0], seconds);
        }
        v.igniteForSeconds((float) seconds);   // shows on a vehicle whose type is not immune
    }

    /**
     * Fire where a fire-immune vehicle stands, for some seconds: each part in lava or in a fire block burns by vanilla's
     * points and sets the vehicle burning; a burning vehicle burns all over, and those aboard with it, until its time
     * is up or water or rain put it out.
     */
    private static boolean fire(PartVehicle v, Settings s, double seconds) {
        if (!(s.attack() > 0) || !v.getType().fireImmune()) { return false; }
        Level level = v.level();
        List<Impacts.Dealt> dealt = new ArrayList<>();
        double lit = 0;
        for (Vehicle.Installed p : v.vehicle().installed()) {
            if (p.part().conditions().isEmpty()) { continue; }
            Vec3d at = world(v, p);
            BlockPos pos = BlockPos.containing(at.x(), at.y(), at.z());
            if (!level.isLoaded(pos)) { continue; }
            if (level.getFluidState(pos).is(FluidTags.LAVA)) {
                burn(p, s.attack() * Elements.LAVA_POINTS_PER_S * Conditions.LAVA_FACTOR * seconds, dealt);
                lit = Math.max(lit, Elements.LAVA_IGNITED_S);
            } else if (level.getBlockState(pos).is(net.minecraft.tags.BlockTags.FIRE)) {
                burn(p, s.attack() * Elements.FIRE_POINTS_PER_S * seconds, dealt);
                lit = Math.max(lit, Elements.IGNITED_S);
            }
        }
        boolean was = burning(v);
        if (was && lit == 0 && v.isInWaterOrRain()) { ignite(v, 0); was = false; }
        if (was) {
            for (Vehicle.Installed p : v.vehicle().installed()) { burn(p, s.attack() * Elements.BURNING_POINTS_PER_S * seconds, dealt); }
            scorch(v, s, Elements.BURNING_POINTS_PER_S);
            synchronized (BURNING) {
                double[] left = BURNING.get(v);
                if (left != null && (left[0] -= seconds) <= 0) { BURNING.remove(v); }
            }
        }
        if (lit > 0) { ignite(v, lit); }
        return apply(v, dealt);
    }

    /** How used the thing that holds a tank's fuel is: the tank's own condition, or the body's structure the tank is in (0 when it has neither). */
    public static double tankUsed(PartVehicle v, int tank) {
        Vehicle.Tank t = v.vehicle().tanks().get(tank);
        Vehicle.Installed p = v.vehicle().installed().get(t.part());
        List<String> c = p.part().conditions();
        return c.contains(Conditions.TANK) ? v.used(p.id(), Conditions.TANK) : c.contains(Conditions.STRUCTURE) ? v.used(p.id(), Conditions.STRUCTURE) : 0;
    }

    /** Whether a tank holds fuel that leaks and burns (not a part's own store: a booster's grain). */
    private static boolean liquid(PartVehicle v, int tank) { return !v.vehicle().tanks().get(tank).tank().own() && v.tankKg(tank) > 0; }

    /**
     * The fuel's explosion: when fire meets a leaking tank (used past EngineWear.TANK_KNEE) or a blast or fire has burst
     * one (used up) with fuel aboard. Once: the tanks are emptied, the blast hurts whoever is near (those aboard too)
     * and breaks no blocks, and everything about the vehicle is used to Elements.BURNT_OUT at least.
     */
    private static void burst(PartVehicle v, Settings s, boolean fire) {
        if (!s.explosions() || !simulated(v)) { return; }
        Vehicle vehicle = v.vehicle();
        double fuel = 0;
        int burst = -1;
        for (int k = 0; k < vehicle.tanks().size(); k++) {
            if (!liquid(v, k)) { continue; }
            fuel += v.tankKg(k);
            double used = tankUsed(v, k);
            if (burst < 0 && (used >= 1 || fire && used > EngineWear.TANK_KNEE)) { burst = k; }
        }
        if (burst < 0 || fuel < Elements.LEAST_EXPLOSIVE_KG) { return; }
        Vehicle.Tank t = vehicle.tanks().get(burst);
        Vec3d at = v.partPoint(vehicle.installed().get(t.part()), t.tank().centre(), 0);
        for (int k = 0; k < vehicle.tanks().size(); k++) { if (liquid(v, k)) { v.setTank(k, "", 0, 1000); } }
        VehicleState st = v.vehicleState();
        for (Vehicle.Installed p : vehicle.installed()) {
            for (int i = 0; i < p.part().conditions().size(); i++) { if (st.used(p.id(), i) < Elements.BURNT_OUT) { st.setUsed(p.id(), i, Elements.BURNT_OUT); } }
        }
        wreck(v);
        v.setTank(burst, "", 0, 1000);   // publishes
        EXPLODING.set(true);
        try {
            v.level().explode(v, at.x(), at.y(), at.z(), (float) Elements.tankBlastPower(fuel), false, Level.ExplosionInteraction.NONE);
        } finally {
            EXPLODING.set(false);
        }
        ignite(v, Elements.IGNITED_S);
    }

    // ----- water, corrosion, leaks: once a second -----

    private static void tick(EntityTickEvent.Post e) {
        if (!(e.getEntity() instanceof PartVehicle v) || (v.tickCount + v.getId()) % 20 != 0 || !simulated(v)) { return; }
        elements(v, settings(), 1);
    }

    /** Where a vehicle keeps the corrosion short of a step (its persistent data: part id to share owed). */
    private static final String RUST = "hdfv_rust";

    /** Parts that were under water at the last look, by vehicle (lost on reload: a drowned engine is then not hydrolocked again). */
    private static final Map<PartVehicle, Set<String>> WET = new WeakHashMap<>();

    /** How far under water a world point is, m (0: not): a sea region's moving surface, else the top of the water column over it (looked up 8 blocks). */
    public static double depth(Level level, Vec3d p) {
        BlockPos pos = BlockPos.containing(p.x(), p.y(), p.z());
        if (!level.isLoaded(pos)) { return 0; }
        FluidState here = level.getFluidState(pos);
        double sea = SeaSurface.surfaceNear(level, p.x(), p.z(), p.y(), p.y(), SeaSurface.time(level));
        if (!Double.isNaN(sea)) { return here.is(FluidTags.WATER) || sea - p.y() < 1 ? Math.max(0, sea - p.y()) : 0; }
        if (!here.is(FluidTags.WATER)) { return 0; }
        double top = pos.getY() + here.getHeight(level, pos);
        for (int n = 1; n <= 8; n++) {
            BlockPos up = pos.above(n);
            FluidState f = level.getFluidState(up);
            if (!f.is(FluidTags.WATER)) { break; }
            top = up.getY() + f.getHeight(level, up);
        }
        return Math.max(0, top - p.y());
    }

    /** Where a point stands, for corrosion: under sea or fresh water (by the biome), under a roof (dry), rained on, in sea air or inland air. */
    public static Corrosion.Exposure exposure(Level level, Vec3d p, double depthM) {
        BlockPos pos = BlockPos.containing(p.x(), p.y(), p.z());
        Holder<Biome> biome = level.getBiome(pos);
        boolean sea = biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_DEEP_OCEAN) || biome.is(BiomeTags.IS_BEACH);
        if (depthM > 0) { return sea ? Corrosion.Exposure.SEA_WATER : Corrosion.Exposure.FRESH_WATER; }
        if (!level.canSeeSky(pos.above())) { return Corrosion.Exposure.DRY; }
        if (sea) { return Corrosion.Exposure.SEA_AIR; }
        return level.isRainingAt(pos.above()) ? Corrosion.Exposure.RAIN : Corrosion.Exposure.AIR;
    }

    /** Whether a part keeps what is aboard dry: a hull, a float, a boat's or an aircraft's body. */
    private static boolean floats(PartDesign d) {
        return d instanceof HullDesign || d instanceof PlaningHullDesign || d instanceof FloatDesign
                || d instanceof BodyDesign b && b.settings().purpose() != BodySettingsDesign.Purpose.CAR;
    }

    /**
     * What some seconds where it stands do to a vehicle (server; called once a second with 1): fire, water in its engines
     * and motors, corrosion, leaks, and the wreck rule.
     */
    public static void elements(PartVehicle v, Settings s, double seconds) {
        if (!s.enabled() || !simulated(v) || !(seconds > 0)) { return; }
        Vehicle vehicle = v.vehicle();
        VehicleState st = v.vehicleState();
        Level level = v.level();
        boolean changed = fire(v, s, seconds);
        net.minecraft.nbt.CompoundTag rust = v.getPersistentData().getCompound(RUST);
        // what floats keeps its machinery dry while its hull is sound
        boolean floating = false, breached = false;
        for (Vehicle.Installed p : vehicle.installed()) {
            if (!floats(p.part().design())) { continue; }
            floating = true;
            if (p.part().conditions().contains(Conditions.STRUCTURE) && st.used(p.id(), Conditions.STRUCTURE) >= Elements.HULL_BREACHED) { breached = true; }
        }
        double dry = floating && !breached ? Elements.DRY_HULL_M : 0;
        Set<String> wet;
        synchronized (WET) { wet = WET.computeIfAbsent(v, k -> new HashSet<>()); }
        for (Vehicle.Installed p : vehicle.installed()) {
            List<String> conditions = p.part().conditions();
            if (conditions.isEmpty()) { continue; }
            Conditions.Profile profile = Conditions.of(p.part());
            String drowns = conditions.contains(Conditions.ANCILLARIES) ? Conditions.ANCILLARIES : p.part().design() instanceof MotorDesign ? Conditions.MOTOR : null;
            boolean rusts = profile != null && profile.corrodes() != null && DamagePace.corrosion() > 0;
            if (drowns == null && !rusts) { continue; }
            Vec3d at = world(v, p);
            double depth = depth(level, at);
            if (drowns != null && s.corrosion() > 0) {
                boolean under = depth > dry;
                if (under) {
                    if (wet.add(p.id()) && p.part().design() instanceof PistonEngineDesign && st.internal(p.id(), PistonEngineDesign.STATE_RUNNING) > 0.5) {
                        st.addUsed(p.id(), PistonEngineDesign.CONDITION_ENGINE, Elements.HYDROLOCK);
                    }
                    st.addUsed(p.id(), conditions.indexOf(drowns), Elements.DROWNING_PER_S * seconds * s.corrosion());
                    changed = true;
                } else {
                    wet.remove(p.id());
                }
            }
            if (rusts) {
                int i = conditions.indexOf(profile.corrodes());
                double amount = Elements.corrosion(profile.metal(), exposure(level, at, depth), st.used(p.id(), i), profile.wallMm(), seconds);
                if (amount > 0) {
                    // kept with the vehicle until it makes a step: a new vehicle stays exactly new for hours
                    double owed = rust.getDouble(p.id()) + amount;
                    double steps = Math.floor(owed / Elements.CORROSION_STEP);
                    if (steps >= 1) {
                        st.addUsed(p.id(), i, steps * Elements.CORROSION_STEP);
                        owed -= steps * Elements.CORROSION_STEP;
                        changed = true;
                    }
                    rust.putDouble(p.id(), owed);
                }
            }
        }
        if (!rust.isEmpty()) { v.getPersistentData().put(RUST, rust); }
        // leaking tanks; fire at a leak
        boolean leaking = false;
        for (int k = 0; k < vehicle.tanks().size(); k++) {
            if (!liquid(v, k)) { continue; }
            double share = EngineWear.leakPerS(tankUsed(v, k));
            if (!(share > 0)) { continue; }
            leaking = true;
            String fuel = v.tankFuel(k);
            double density = Fuelling.density(fuel), kg = v.tankKg(k);
            double left = share >= 1 ? 0 : Math.max(0, kg - share * seconds * v.tankCapacityM3(k) * density);
            v.setTank(k, left > 0 ? fuel : "", left, density);
        }
        if (leaking && burning(v)) { burst(v, s, true); }
        changed |= wreck(v);   // also for a structure used up by a command or by wear between looks
        if (changed) {
            Vehicle.Installed first = null;
            for (Vehicle.Installed p : vehicle.installed()) { if (!p.part().conditions().isEmpty()) { first = p; break; } }
            if (first != null) { v.addUsed(first.id(), first.part().conditions().get(0), 0); }
        }
    }

    // ----- the status screen -----

    /**
     * Sneaking and using an empty hand on a vehicle opens its status screen (client) in place of AeroCore's report in
     * the chat (which /aerocore vehicle info and sneak-using with something in the hand still give).
     */
    private static void interact(PlayerInteractEvent.EntityInteract e) {
        if (!(e.getTarget() instanceof PartVehicle v) || e.getHand() != InteractionHand.MAIN_HAND) { return; }
        Player player = e.getEntity();
        if (!player.isSecondaryUseActive() || !e.getItemStack().isEmpty()) { return; }
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.sidedSuccess(player.level().isClientSide));
        if (player.level().isClientSide) { dev.mcmod.hdfv.neoforge.client.ClientAccess.openVehicleStatus(v); }
    }
}
