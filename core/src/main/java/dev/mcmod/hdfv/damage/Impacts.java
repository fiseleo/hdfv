package dev.mcmod.hdfv.damage;

import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.damage.Casualty;
import dev.mcmod.aerocore.api.damage.ImpactDamage;
import dev.mcmod.aerocore.api.vehicle.Vehicle;

import java.util.ArrayList;
import java.util.List;

/**
 * 0.25: what a hit does to a vehicle's parts, by the table (Conditions) and AeroCore's ImpactDamage: the energy a hit
 * leaves is shared between all the installed parts (by mass and distance from the hit, each with its reach; a part
 * without conditions, a ballast block or a test stand's bed, takes its share and shows nothing) and used against each
 * part's toughness; the part's damage then goes to the conditions a crash reaches, times their factors. Pure Java, so the game (neoforge's VehicleDamage) and the offline calibration (ConditionsTest) run the
 * same numbers.
 */
public final class Impacts {
    private Impacts() {}

    /** Damage to one condition of one part: what to add to it (0..1 and beyond; the vehicle keeps it within 1). */
    public record Dealt(String partId, String condition, double amount) {}

    /** A part's centre of mass in the vehicle frame. */
    public static Vec3d centre(Vehicle.Installed installed) {
        return installed.mount().position().add(installed.rotation().rotate(installed.part().mass().centre()));
    }

    /** The installed parts as ImpactDamage sees them (vehicle frame), and their profiles (same order; null for a part nothing harms). */
    private static void targets(Vehicle vehicle, List<Vehicle.Installed> parts, List<Conditions.Profile> profiles, List<ImpactDamage.Target> targets) {
        for (Vehicle.Installed i : vehicle.installed()) {
            Conditions.Profile p = Conditions.of(i.part());
            double mass = i.part().mass().massKg();
            if (!(mass > 0) || !Double.isFinite(mass)) { continue; }
            parts.add(i);
            profiles.add(p);
            targets.add(new ImpactDamage.Target(centre(i), mass, p == null ? ImpactDamage.SHELL_STEEL_J_PER_KG : p.toughnessJPerKg(), p == null ? Conditions.reach(i.part().mass()) : p.reachM()));
        }
    }

    /**
     * What an energy (J) left at a point of the vehicle (vehicle frame) does: for each part reached, its conditions'
     * shares. Amounts under a thousandth are left out (a scratch).
     */
    public static List<Dealt> hit(Vehicle vehicle, Vec3d point, double energyJ) {
        List<Dealt> out = new ArrayList<>();
        if (!(energyJ > 0) || !Double.isFinite(energyJ)) { return out; }
        List<Vehicle.Installed> parts = new ArrayList<>();
        List<Conditions.Profile> profiles = new ArrayList<>();
        List<ImpactDamage.Target> targets = new ArrayList<>();
        targets(vehicle, parts, profiles, targets);
        double[] damage = ImpactDamage.damage(energyJ, point, targets);
        for (int k = 0; k < damage.length; k++) {
            if (profiles.get(k) == null) { continue; }
            for (Conditions.Hit h : profiles.get(k).crash()) {
                double amount = damage[k] * h.factor();
                if (amount >= SCRATCH && parts.get(k).part().conditions().contains(h.condition())) { out.add(new Dealt(parts.get(k).id(), h.condition(), Math.min(amount, 1))); }
            }
        }
        return out;
    }

    /** Damage under this share of a condition is not kept. */
    public static final double SCRATCH = 1e-3;

    /**
     * A blow or a shot: an energy (J) landing on one part alone, used against its toughness and mass; its conditions'
     * shares as hit() gives them.
     */
    public static List<Dealt> blow(Vehicle.Installed part, double energyJ) {
        List<Dealt> out = new ArrayList<>();
        Conditions.Profile p = Conditions.of(part.part());
        double mass = part.part().mass().massKg();
        if (p == null || !(energyJ > 0) || !(mass > 0)) { return out; }
        double damage = energyJ / (p.toughnessJPerKg() * mass);
        for (Conditions.Hit h : p.crash()) {
            double amount = damage * h.factor();
            if (amount >= SCRATCH && part.part().conditions().contains(h.condition())) { out.add(new Dealt(part.id(), h.condition(), Math.min(amount, 1))); }
        }
        return out;
    }

    /**
     * The closing speed under which a hit only springs back, m/s: 8 km/h, the 5 mph of the bumper standards cars were
     * built to (US FMVSS 215, 1973), twice AeroCore's default (UN R42's 4 km/h): hdfv's vehicles are bumped about by
     * players, and the sedan's 15 km/h wall test (ConditionsTest) is to leave every condition under 3 %.
     */
    public static final double ELASTIC_MPS = 8 / 3.6;

    /** The energy (J) a hit of a normal impulse (N s) closing at a speed (m/s) leaves in the structure, with ELASTIC_MPS. */
    public static double energyJ(double impulseNs, double approachMps) {
        if (!(impulseNs > 0) || !(approachMps > ELASTIC_MPS) || !Double.isFinite(impulseNs) || !Double.isFinite(approachMps)) { return 0; }
        return ImpactDamage.energyJ(impulseNs, approachMps, ELASTIC_MPS);
    }

    /**
     * A crash: the energy of a hit's normal impulse (N s) closing at a speed (m/s), scaled (the server's collision scale).
     * The impulse is the one that stops the body (m v for a dead stop); a caller whose impulse includes a rebound (the
     * game's contacts: m v (1 + e)) takes the share 1 - e of it first, which is the energy the rebound did not give back.
     */
    public static List<Dealt> crash(Vehicle vehicle, Vec3d point, double impulseNs, double approachMps, double scale) {
        if (!(scale > 0)) { return List.of(); }
        return hit(vehicle, point, scale * energyJ(impulseNs, approachMps));
    }

    /**
     * The part nearest a point (vehicle frame) that damage can reach, or null: where a blade or an arrow lands.
     * Distances count in each part's reach, so a small part beside the point wins over a large one's far centre.
     */
    public static Vehicle.Installed nearest(Vehicle vehicle, Vec3d point) {
        Vehicle.Installed best = null;
        double least = Double.MAX_VALUE;
        for (Vehicle.Installed i : vehicle.installed()) {
            Conditions.Profile p = Conditions.of(i.part());
            if (p == null) { continue; }
            double d = centre(i).subtract(point).length() / p.reachM();
            if (d < least) { least = d; best = i; }
        }
        return best;
    }

    // ----- occupants -----

    /** Health a certain serious injury costs (a player's whole 20), and the least risk that hurts at all. */
    public static final double INJURY_HEALTH = 20, LEAST_RISK = 0.02;
    /** Rule: the share of a crash's harm a sound body lets through to its occupants (its crush zone and belts work), rising to all of it as its structure is used up. */
    public static final double SOUND_BODY = 0.6;

    /**
     * Health an occupant loses in a crash of a normal impulse (N s) on a vehicle of a mass (kg, with fuel and cargo)
     * whose body structure is so much used (0 without a body: as a sound one): 20 x the risk of serious injury at the
     * change of velocity (AeroCore's Casualty) x SOUND_BODY..1; nothing under LEAST_RISK.
     */
    public static double injury(double impulseNs, double massKg, double bodyUsed) {
        if (!(impulseNs > 0) || !(massKg > 0)) { return 0; }
        double risk = Casualty.seriousRisk(Casualty.deltaV(impulseNs, massKg));
        if (risk < LEAST_RISK) { return 0; }
        return INJURY_HEALTH * risk * (SOUND_BODY + (1 - SOUND_BODY) * Math.clamp(bodyUsed, 0.0, 1.0));
    }
}
