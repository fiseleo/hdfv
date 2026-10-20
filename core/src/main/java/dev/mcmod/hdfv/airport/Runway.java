package dev.mcmod.hdfv.airport;

import dev.mcmod.aerocore.api.AirData;
import dev.mcmod.aerocore.api.Vec3d;

/**
 * A runway (0.20): a strip along the world's X or Z axis (block grid; a diagonal runway of blocks would be a
 * staircase), its centre line at a fixed coordinate across, from one threshold to the other along, at a surface
 * height, so wide. Each end is named for the compass heading of a take-off from it, in tens of degrees (north is -Z:
 * a runway along Z is 18/36, along X 09/27), with a letter when parallel runways share the number.
 * <p>
 * End LOW is at the smaller coordinate (take-offs from it head toward +axis), HIGH at the larger.
 */
public record Runway(Axis axis, double cross, double low, double high, double y, double widthM, String suffix) {
    public enum Axis { X, Z }

    public enum End { LOW, HIGH;
        public End other() { return this == LOW ? HIGH : LOW; }
    }

    public Runway {
        if (!(high > low) || !(widthM > 0)) { throw new IllegalArgumentException("A runway needs length and width"); }
        suffix = suffix == null ? "" : suffix;
    }

    public double lengthM() { return high - low; }

    /** How far a point (block x, z) is from the runway's pavement (m; 0 on it): towers keep to runways within 1 km (0.21). */
    public double distanceTo(double x, double z) {
        double a = axis == Axis.X ? x : z, c = axis == Axis.X ? z : x;
        double da = Math.max(0, Math.max(low - a, a - high)), dc = Math.max(0, Math.abs(c - cross) - widthM / 2);
        return Math.hypot(da, dc);
    }

    /** The compass heading of a take-off from an end (degrees: X-runway LOW 90, HIGH 270; Z-runway LOW 180, HIGH 360). */
    public double headingDeg(End end) {
        Vec3d d = direction(end);
        double h = AirData.compassDeg(d.x(), d.z());
        return h == 0 ? 360 : h;
    }

    /** An end's number: its heading in tens of degrees, 1 to 36 ("09", "27", "36"), with the parallel letter. */
    public String designator(End end) {
        int n = (int) Math.round(headingDeg(end) / 10);
        if (n == 0) { n = 36; }
        return String.format(java.util.Locale.ROOT, "%02d", n) + parallelLetter(end);
    }

    /** L, C or R as seen from that end (the suffix is given for the LOW end: L there is R from HIGH). */
    private String parallelLetter(End end) {
        if (suffix.isEmpty() || end == End.LOW || suffix.equals("C")) { return suffix; }
        return suffix.equals("L") ? "R" : "L";
    }

    /** Both ends' names ("09/27"). */
    public String name() { return designator(End.LOW) + "/" + designator(End.HIGH); }

    /** The unit direction of a take-off (or landing) from an end. */
    public Vec3d direction(End end) {
        double s = end == End.LOW ? 1 : -1;
        return axis == Axis.X ? new Vec3d(s, 0, 0) : new Vec3d(0, 0, s);
    }

    /** An end's threshold on the centre line (at the surface). */
    public Vec3d threshold(End end) {
        double a = end == End.LOW ? low : high;
        return axis == Axis.X ? new Vec3d(a, y, cross) : new Vec3d(cross, y, a);
    }

    /** The middle of the runway on its centre line. */
    public Vec3d centre() {
        double a = 0.5 * (low + high);
        return axis == Axis.X ? new Vec3d(a, y, cross) : new Vec3d(cross, y, a);
    }

    /** Distance (m) along the runway from an end's threshold toward the other end (negative before the threshold). */
    public double alongFrom(End end, double x, double z) {
        double a = axis == Axis.X ? x : z;
        return end == End.LOW ? a - low : high - a;
    }

    /** Distance (m) from the centre line, positive to the right of a take-off from LOW. */
    public double offset(double x, double z) {
        // LOW take-off heads +X (east; right is +Z, south) or +Z (south; right is -X, west)
        return axis == Axis.X ? z - cross : cross - x;
    }

    /** Distance (m) from the centre line, positive to the right as seen taking off from an end. */
    public double offsetFrom(End end, double x, double z) { return end == End.LOW ? offset(x, z) : -offset(x, z); }

    /** Whether a point is over the runway's rectangle, widened by margin (m) each side and at each end. */
    public boolean over(double x, double z, double margin) {
        double a = axis == Axis.X ? x : z;
        return a >= low - margin && a <= high + margin && Math.abs(offset(x, z)) <= widthM / 2 + margin;
    }

    /** The end nearer a point (along the runway). */
    public End nearerEnd(double x, double z) { return alongFrom(End.LOW, x, z) <= alongFrom(End.HIGH, x, z) ? End.LOW : End.HIGH; }

    /** An end by its designator, or null. */
    public End end(String designator) {
        if (designator(End.LOW).equals(designator)) { return End.LOW; }
        return designator(End.HIGH).equals(designator) ? End.HIGH : null;
    }

    /** A runway and one of its ends: the one in use (landings toward the other end, take-offs from it). */
    public record Use(Runway runway, End end) {
        public String designator() { return runway.designator(end); }
        public Vec3d threshold() { return runway.threshold(end); }
        public Vec3d direction() { return runway.direction(end); }
        public double headingDeg() { return runway.headingDeg(end); }
        public double along(double x, double z) { return runway.alongFrom(end, x, z); }
    }
}
