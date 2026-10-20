package dev.mcmod.hdfv.paint;

import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.VehicleBlueprint;
import dev.mcmod.hdfv.look.BodySurface;
import dev.mcmod.hdfv.look.Sticker;
import dev.mcmod.hdfv.part.air.PlanformDesign;
import dev.mcmod.hdfv.part.air.WingDesign;
import dev.mcmod.hdfv.part.body.BodyDesign;
import dev.mcmod.hdfv.part.wheel.AxleDesign;
import dev.mcmod.hdfv.part.wheel.LandingGearDesign;

import java.util.ArrayList;
import java.util.List;

/**
 * 0.13: what a paint booth's job takes, from the vehicle as it is and as it is to be (Repaint.looksOnly holds between
 * them). A part whose looks change is repainted whole: a body's skin, a wing's both faces, a wheel's rim. New stickers
 * are film laid on (their area, a pair twice). The figures are the booth's defaults, of a real refinishing booth's order:
 * <ul>
 *   <li>a can of paint (a litre) covers about CAN_M2 in two coats at about half the paint reaching the panel;</li>
 *   <li>spraying SPRAY_MIN_PER_M2 minutes a square metre, laying film FILM_MIN_PER_M2, a common refinish's bake BAKE_HOURS
 *       (about 60 C, half an hour) when anything was painted;</li>
 *   <li>SPRAY_KW while spraying and laying film (fans, lights, compressor), BAKE_KW while baking (the heaters).</li>
 * </ul>
 * Removed stickers cost nothing. Hours are work hours (the server's ticksPerWorkHour).
 */
public record PaintJob(double repaintM2, double stickerM2, int cans, int films, double sprayHours, double filmHours, double bakeHours) {
    public static final double CAN_M2 = 4, FILM_M2 = 1, SPRAY_MIN_PER_M2 = 4, FILM_MIN_PER_M2 = 10, BAKE_HOURS = 0.5, SPRAY_KW = 40, BAKE_KW = 120;
    /** A wheel's rim, m^2. */
    public static final double RIM_M2 = 0.2;

    public static final PaintJob NONE = new PaintJob(0, 0, 0, 0, 0, 0, 0);

    public double hours() { return sprayHours + filmHours + bakeHours; }

    public double kWh() { return SPRAY_KW * (sprayHours + filmHours) + BAKE_KW * bakeHours; }

    public boolean empty() { return repaintM2 <= 0 && stickerM2 <= 0; }

    /** The job from before to after (the same parts, by id). */
    public static PaintJob of(VehicleBlueprint before, VehicleBlueprint after) {
        double repaint = 0, film = 0;
        for (VehicleBlueprint.Entry next : after.parts()) {
            PartDesign was = before.parts().stream().filter(e -> e.id().equals(next.id())).map(VehicleBlueprint.Entry::design).findFirst().orElse(null);
            PartDesign now = next.design();
            if (was == null || was.equals(now)) { continue; }
            // its looks changed if it differs with the stickers put back
            PartDesign lookOnly = Repaint.takesStickers(now) ? Repaint.withStickers(now, Repaint.stickers(was)) : now;
            if (!lookOnly.equals(was)) { repaint += area(now); }
            List<Sticker> old = new ArrayList<>(Repaint.stickers(was));
            for (Sticker s : Repaint.stickers(now)) {
                if (old.remove(s)) { continue; }
                film += s.widthM() * s.widthM() * s.aspect() * (s.pair() ? 2 : 1);
            }
        }
        if (repaint <= 0 && film <= 0) { return NONE; }
        int cans = repaint > 0 ? (int) Math.ceil(repaint / CAN_M2 - 1e-9) : 0, films = film > 0 ? (int) Math.ceil(film / FILM_M2 - 1e-9) : 0;
        return new PaintJob(repaint, film, cans, films, repaint * SPRAY_MIN_PER_M2 / 60, film * FILM_MIN_PER_M2 / 60, repaint > 0 ? BAKE_HOURS : 0);
    }

    /** A part's painted area, m^2: a body's skin, a wing's two faces, the rims. */
    public static double area(PartDesign d) {
        if (d instanceof BodyDesign b) { return skin(new BodySurface(b.shape())); }
        if (d instanceof WingDesign w) {
            PlanformDesign p = w.planform();
            return 2 * p.spanM() * 0.5 * (p.rootChordM() + p.tipChordM());
        }
        if (d instanceof AxleDesign) { return 2 * RIM_M2; }
        if (d instanceof LandingGearDesign) { return RIM_M2; }
        return 0;
    }

    /** A body's skin area from its lofted rings (each quad as two triangles). */
    static double skin(BodySurface s) {
        double a = 0;
        Vec3d[][] r = s.rings;
        for (int i = 0; i + 1 < r.length; i++) {
            int n = r[i].length;
            for (int k = 0; k < n; k++) {
                Vec3d p = r[i][k], q = r[i][(k + 1) % n], u = r[i + 1][(k + 1) % n], v = r[i + 1][k];
                a += 0.5 * q.subtract(p).cross(u.subtract(p)).length() + 0.5 * u.subtract(p).cross(v.subtract(p)).length();
            }
        }
        return a;
    }
}
