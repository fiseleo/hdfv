package dev.mcmod.hdfv.airport;

import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.light.FieldLamp;
import dev.mcmod.aerocore.api.light.LightSignal;
import dev.mcmod.aerocore.api.light.SectorColours;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Airfield lights (0.20): each fitting a player placed becomes one or two AeroCore field lamps, coloured and aimed by
 * where it stands against the runways (ICAO Annex 14 chapter 5.3, simplified):
 * <ul>
 *   <li>runway edge: white both ways, yellow toward an end over the last 600 m (or third) before it;</li>
 *   <li>centre line: white, alternate red and white from 900 m to 300 m before the end ahead, red over the last 300 m;</li>
 *   <li>threshold and end: green toward the approach, red toward the runway;</li>
 *   <li>PAPI: a row of units beside the runway near an end, the one nearest the runway set highest: four at 3 deg 30',
 *   3 deg 10', 2 deg 50', 2 deg 30' (a 3 degree path), two (APAPI) at 3 deg 15' and 2 deg 45'; red below, white above,
 *   a 3' transition; 10 degrees either side of the approach;</li>
 *   <li>approach: before an end on its extended centre line, aimed up the approach; flashers fire from the farthest to
 *   the nearest twice a second;</li>
 *   <li>taxiway edge blue, centre line green, obstruction red, all round; the beacon white and green flashes.</li>
 * </ul>
 * Intensities (cd) are of high-intensity fittings at full brightness; the tower's step dims them (circuit level).
 * Circuits: {@link #RUNWAY}, {@link #APPROACH}, {@link #PAPI}, {@link #TAXIWAY}, {@link #OBSTRUCTION}, {@link #BEACON}.
 */
public final class AirfieldLighting {
    private AirfieldLighting() {}

    public static final int RUNWAY = 0, APPROACH = 1, PAPI = 2, TAXIWAY = 3, OBSTRUCTION = 4, BEACON = 5, CIRCUITS = 6;

    public static final int WHITE = 0xfffff4e0, YELLOW = 0xffffc020, GREEN = 0xff30ff60, RED = 0xffff2010, BLUE = 0xff3050ff, BEACON_GREEN = 0xff40ff80;

    /** How far a runway's edge, threshold or approach lights may stand from it and still belong to it (m). */
    public static final double EDGE_REACH_M = 6, THRESHOLD_REACH_M = 8, APPROACH_REACH_M = 900, APPROACH_WIDTH_M = 25, PAPI_REACH_M = 60;

    /** The intensity of each step of the tower's brightness control (step 5 full), as runway lighting steps go. */
    public static final double[] STEPS = {0, 0.01, 0.03, 0.1, 0.3, 1.0};

    public static List<FieldLamp> lamps(List<Runway> runways, List<Fixture> fixtures) {
        List<FieldLamp> out = new ArrayList<>();
        List<Fixture> papis = new ArrayList<>(), flashers = new ArrayList<>();
        for (Fixture f : fixtures) {
            switch (f.kind()) {
                case RUNWAY_EDGE -> edge(out, f, runways);
                case RUNWAY_CENTRE -> centre(out, f, runways);
                case THRESHOLD -> threshold(out, f, runways);
                case PAPI -> papis.add(f);
                case APPROACH -> approach(out, f, runways);
                case APPROACH_FLASHER -> flashers.add(f);
                case TAXI_EDGE -> out.add(FieldLamp.omni(at(f, 0.3), 50, BLUE, TAXIWAY, 0.06));
                case TAXI_CENTRE -> out.add(FieldLamp.omni(at(f, 0.05), 100, GREEN, TAXIWAY, 0.05));
                case OBSTRUCTION -> out.add(FieldLamp.omni(at(f, 0.6), 32, RED, OBSTRUCTION, 0.08));
                case BEACON -> out.add(FieldLamp.beam(at(f, 0.9), Vec3d.X, 360, 20, 5, 20_000, WHITE, BEACON, 0.25)
                        .with(LightSignal.alternate(2.0, 0.3, WHITE, BEACON_GREEN)));
            }
        }
        papi(out, papis, runways);
        flashers(out, flashers, runways);
        return out;
    }

    private static Vec3d at(Fixture f, double height) { return new Vec3d(f.x() + 0.5, f.y() + height, f.z() + 0.5); }

    /** The runway a fitting at a point stands by (over it widened by the reach), or null. */
    private static Runway near(List<Runway> runways, double x, double z, double reach) {
        Runway best = null;
        double bestOff = Double.MAX_VALUE;
        for (Runway r : runways) {
            if (!r.over(x, z, reach)) { continue; }
            double off = Math.abs(r.offset(x, z));
            if (off < bestOff) { best = r; bestOff = off; }
        }
        return best;
    }

    private static double caution(Runway r) { return Math.min(600, r.lengthM() / 3); }

    private static void edge(List<FieldLamp> out, Fixture f, List<Runway> runways) {
        Vec3d p = at(f, 0.35);
        Runway r = near(runways, p.x(), p.z(), EDGE_REACH_M);
        if (r == null) { out.add(FieldLamp.omni(p, 10_000, WHITE, RUNWAY, 0.07)); return; }
        for (Runway.End toward : Runway.End.values()) {
            // seen by traffic heading toward an end (moving as a take-off from the other end does): the lamp faces back at it
            boolean yellow = r.alongFrom(toward, p.x(), p.z()) < caution(r);
            out.add(FieldLamp.beam(p, r.direction(toward), 180, 180, 0, 10_000, yellow ? YELLOW : WHITE, RUNWAY, 0.07));
        }
    }

    private static void centre(List<FieldLamp> out, Fixture f, List<Runway> runways) {
        Vec3d p = at(f, 0.03);
        Runway r = near(runways, p.x(), p.z(), 0);
        if (r == null) { out.add(FieldLamp.omni(p, 5_000, WHITE, RUNWAY, 0.05)); return; }
        for (Runway.End toward : Runway.End.values()) {
            double left = r.alongFrom(toward, p.x(), p.z());
            int colour = left < 300 ? RED : left < 900 ? (((int) Math.floor(left / 15)) % 2 == 0 ? RED : WHITE) : WHITE;
            // aimed back at traffic heading toward that end
            out.add(FieldLamp.beam(p, r.direction(toward), 180, 180, 0, 5_000, colour, RUNWAY, 0.05));
        }
    }

    private static void threshold(List<FieldLamp> out, Fixture f, List<Runway> runways) {
        Vec3d p = at(f, 0.35);
        for (Runway r : runways) {
            for (Runway.End end : Runway.End.values()) {
                double along = r.alongFrom(end, p.x(), p.z());
                if (Math.abs(along) > THRESHOLD_REACH_M || Math.abs(r.offset(p.x(), p.z())) > r.widthM() / 2 + THRESHOLD_REACH_M) { continue; }
                Vec3d in = r.direction(end);
                out.add(FieldLamp.beam(p, in.scale(-1), 180, 180, 0, 10_000, GREEN, RUNWAY, 0.08));   // to the approach
                out.add(FieldLamp.beam(p, in, 180, 180, 0, 2_500, RED, RUNWAY, 0.08));               // to the runway: its end
                return;
            }
        }
        out.add(FieldLamp.omni(p, 10_000, GREEN, RUNWAY, 0.08));
    }

    private static void approach(List<FieldLamp> out, Fixture f, List<Runway> runways) {
        Vec3d p = at(f, 0.8);
        Runway.Use u = approachOf(runways, p);
        Vec3d axis = u == null ? Vec3d.X : u.direction().scale(-1);
        out.add(FieldLamp.beam(p, axis, u == null ? 360 : 30, 14, 4, 20_000, WHITE, APPROACH, 0.12));
    }

    /** The runway end whose approach a point stands in (before its threshold, near its extended centre line), or null. */
    static Runway.Use approachOf(List<Runway> runways, Vec3d p) {
        Runway.Use best = null;
        double bestOff = Double.MAX_VALUE;
        for (Runway r : runways) {
            for (Runway.End end : Runway.End.values()) {
                double along = r.alongFrom(end, p.x(), p.z()), off = Math.abs(r.offset(p.x(), p.z()));
                if (along > 0 || along < -APPROACH_REACH_M || off > APPROACH_WIDTH_M) { continue; }
                if (off < bestOff) { best = new Runway.Use(r, end); bestOff = off; }
            }
        }
        return best;
    }

    private static void flashers(List<FieldLamp> out, List<Fixture> flashers, List<Runway> runways) {
        // each approach's flashers in order: the farthest from the threshold fires first
        java.util.Map<Runway.Use, List<Vec3d>> byEnd = new java.util.LinkedHashMap<>();
        for (Fixture f : flashers) {
            Vec3d p = at(f, 0.8);
            Runway.Use u = approachOf(runways, p);
            if (u == null) { out.add(FieldLamp.omni(p, 20_000, WHITE, APPROACH, 0.12).with(LightSignal.flash(1.0, 0.025, 0))); continue; }
            byEnd.computeIfAbsent(u, k -> new ArrayList<>()).add(p);
        }
        for (java.util.Map.Entry<Runway.Use, List<Vec3d>> e : byEnd.entrySet()) {
            Runway.Use u = e.getKey();
            List<Vec3d> list = new ArrayList<>(e.getValue());
            list.sort(Comparator.comparingDouble(p -> u.along(p.x(), p.z())));   // most negative (farthest) first
            for (int i = 0; i < list.size(); i++) {
                out.add(FieldLamp.beam(list.get(i), u.direction().scale(-1), 30, 14, 4, 20_000, WHITE, APPROACH, 0.12)
                        .with(LightSignal.sequence(0.5, i, list.size())));
            }
        }
    }

    /** The settings (degrees above the horizontal) of a row of n PAPI units, nearest the runway first. */
    public static double[] papiSettingsDeg(int n) {
        if (n == 4) { return new double[]{3.5, 3 + 10 / 60.0, 2 + 50 / 60.0, 2.5}; }
        if (n == 2) { return new double[]{3.25, 2.75}; }
        double[] out = new double[n];
        for (int i = 0; i < n; i++) { out[i] = n == 1 ? 3 : 3.5 - i / (n - 1.0); }
        return out;
    }

    private static void papi(List<FieldLamp> out, List<Fixture> units, List<Runway> runways) {
        // rows: units within 12 m of each other, by the runway end nearest them
        List<List<Fixture>> rows = new ArrayList<>();
        for (Fixture f : units) {
            List<Fixture> row = null;
            for (List<Fixture> r : rows) {
                for (Fixture g : r) { if (Math.abs(g.x() - f.x()) + Math.abs(g.z() - f.z()) <= 12) { row = r; break; } }
                if (row != null) { break; }
            }
            if (row == null) { row = new ArrayList<>(); rows.add(row); }
            row.add(f);
        }
        for (List<Fixture> row : rows) {
            Vec3d first = at(row.get(0), 0.6);
            Runway r = near(runways, first.x(), first.z(), PAPI_REACH_M);
            if (r == null) {
                for (Fixture f : row) { out.add(FieldLamp.omni(at(f, 0.6), 15_000, RED, PAPI, 0.1)); }
                continue;
            }
            Runway.End end = r.nearerEnd(first.x(), first.z());
            List<Fixture> ordered = new ArrayList<>(row);
            ordered.sort(Comparator.comparingDouble(f -> Math.abs(r.offset(f.x() + 0.5, f.z() + 0.5))));
            double[] settings = papiSettingsDeg(ordered.size());
            for (int i = 0; i < ordered.size(); i++) {
                out.add(FieldLamp.beam(at(ordered.get(i), 0.6), r.direction(end).scale(-1), 20, 12, 3, 15_000, WHITE, PAPI, 0.1)
                        .with(SectorColours.papi(Math.toRadians(settings[i]), Math.toRadians(3.0 / 60), RED, WHITE)));
            }
        }
    }
}
