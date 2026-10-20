package dev.mcmod.hdfv.paint;

import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.VehicleBlueprint;
import dev.mcmod.hdfv.assembly.RoomScan;
import dev.mcmod.hdfv.design.Designs;
import dev.mcmod.hdfv.look.Sticker;
import dev.mcmod.hdfv.part.HdfvParts;
import dev.mcmod.hdfv.part.SampleBlueprints;
import dev.mcmod.hdfv.part.body.BodyDesign;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The paint booth's rules (0.13): only looks and stickers may change, what a job takes (paint by area, film by the
 * stickers, power and time), and what a booth needs.
 */
class PaintBoothTest {
    static { HdfvParts.register(); }

    private static final String IMAGE = "fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210";

    private static PartDesign part(VehicleBlueprint b, String id) { return b.parts().stream().filter(e -> e.id().equals(id)).findFirst().orElseThrow().design(); }

    @Test void onlyLooksAndStickersPass() {
        VehicleBlueprint sedan = SampleBlueprints.sedan();
        PartDesign body = part(sedan, "body");
        PartDesign red = Repaint.setLook(body, BodyDesign.SETTINGS, "paintColor", "#B3161C");
        assertTrue(Repaint.looksOnly(body, red), "a colour is a look");
        PartDesign stickered = Repaint.withStickers(red, List.of(new Sticker(IMAGE, 0.5, new Sticker.OnBody(2, 1.4), 1.5, 0, false, true, 0)));
        assertTrue(Repaint.looksOnly(body, stickered), "and a sticker");
        assertTrue(Repaint.looksOnly(sedan, Repaint.withPart(sedan, "body", stickered)));
        // the shape is not the booth's: a looks' check refuses it, and so does setLook
        PartDesign longer = Designs.set(body, "module1", "lengthM", "1.9");
        assertNotEquals(body, longer);
        assertFalse(Repaint.looksOnly(body, longer), "a module's length is not a look");
        assertFalse(Repaint.looksOnly(sedan, Repaint.withPart(sedan, "body", longer)));
        assertThrows(IllegalArgumentException.class, () -> Repaint.setLook(body, "module1", "lengthM", "1.9"));
        assertThrows(IllegalArgumentException.class, () -> Repaint.setLook(body, BodyDesign.SETTINGS, "archRadiusM", "0.5"), "nor the arches");
        // a wing's paint, a wheel's rim
        VehicleBlueprint seaplane = SampleBlueprints.seaplane();
        PartDesign wing = part(seaplane, "wing");
        assertTrue(Repaint.looksOnly(wing, Repaint.setLook(wing, "structure", "paintColor", "#2E7D32")));
        PartDesign axle = part(sedan, "front_axle");
        assertTrue(Repaint.looksOnly(axle, Repaint.setLook(axle, "wheel", "rimFinish", "chrome")));
        assertFalse(Repaint.paintable(part(sedan, "engine")), "an engine is not painted here");
    }

    @Test void aJobTakesPaintByAreaAndFilmByStickers() {
        VehicleBlueprint sedan = SampleBlueprints.sedan();
        PartDesign body = part(sedan, "body");
        assertSame(PaintJob.NONE, PaintJob.of(sedan, sedan), "nothing to do");
        double skin = PaintJob.area(body);
        assertTrue(skin > 10 && skin < 40, "a family car's skin, m^2: " + skin);
        VehicleBlueprint red = Repaint.withPart(sedan, "body", Repaint.setLook(body, BodyDesign.SETTINGS, "paintColor", "#B3161C"));
        PaintJob paint = PaintJob.of(sedan, red);
        assertEquals(skin, paint.repaintM2(), 1e-9);
        assertEquals((int) Math.ceil(skin / PaintJob.CAN_M2), paint.cans());
        assertEquals(0, paint.films());
        assertEquals(skin * PaintJob.SPRAY_MIN_PER_M2 / 60 + PaintJob.BAKE_HOURS, paint.hours(), 1e-9, "sprayed, then baked");
        assertEquals(PaintJob.SPRAY_KW * skin * PaintJob.SPRAY_MIN_PER_M2 / 60 + PaintJob.BAKE_KW * PaintJob.BAKE_HOURS, paint.kWh(), 1e-9);
        // a sticker 1.8 m by 0.9 m with its pair: 3.24 m^2 of film, no paint, no bake
        Sticker s = new Sticker(IMAGE, 0.5, new Sticker.OnBody(2.3, 1.45), 1.8, 0, false, true, 0);
        VehicleBlueprint stickered = Repaint.withPart(sedan, "body", Repaint.withStickers(body, List.of(s)));
        PaintJob film = PaintJob.of(sedan, stickered);
        assertEquals(0, film.repaintM2());
        assertEquals(3.24, film.stickerM2(), 1e-9);
        assertEquals(4, film.films());
        assertEquals(0, film.bakeHours());
        assertEquals(3.24 * PaintJob.FILM_MIN_PER_M2 / 60, film.hours(), 1e-9);
        // taking a sticker off costs nothing
        assertSame(PaintJob.NONE, PaintJob.of(stickered, sedan));
        // a whole family car painted with a big sticker: about an hour and a half of work
        PaintJob both = PaintJob.of(sedan, Repaint.withPart(red, "body", Repaint.withStickers(part(red, "body"), List.of(s))));
        assertTrue(both.hours() > 1 && both.hours() < 3, "hours: " + both.hours());
    }

    private static RoomScan.Scan room(boolean closed, int doorWidth, int doorHeight) {
        List<RoomScan.Door> doors = doorWidth <= 0 ? List.of() : List.of(new RoomScan.Door(0, 0, 0, 0, doorHeight - 1, doorWidth - 1, 1, 0, true, false, true));
        return new RoomScan.Scan(closed, 3, 4, 5, 200, 0, 0, 0, 9, 4, 6, 0, doors, 0, 0, List.of(), List.of());
    }

    private static List<String> keys(PaintBoothRules.Booth b) { return PaintBoothRules.check(b).stream().map(PaintBoothRules.Problem::key).toList(); }

    @Test void aBoothNeedsAirLightPowerAndOneVehicleThatFits() {
        assertEquals(List.of(), keys(new PaintBoothRules.Booth(room(true, 3, 3), 1, 2, 1, 1.8, 1.5, false, true)), "a good booth");
        assertEquals(List.of("open"), keys(new PaintBoothRules.Booth(room(false, 3, 3), 1, 2, 1, 1.8, 1.5, false, true)), "open says only that");
        assertEquals(List.of("no_door"), keys(new PaintBoothRules.Booth(room(true, 0, 0), 1, 2, 1, 1.8, 1.5, false, true)));
        assertEquals(List.of("door_small"), keys(new PaintBoothRules.Booth(room(true, 2, 3), 1, 2, 1, 12, 3, false, true)), "a seaplane's span");
        assertEquals(List.of("no_exhaust", "few_lights"), keys(new PaintBoothRules.Booth(room(true, 3, 3), 0, 1, 1, 1.8, 1.5, false, true)));
        assertEquals(List.of("no_vehicle"), keys(new PaintBoothRules.Booth(room(true, 3, 3), 1, 2, 0, 0, 0, false, true)));
        assertEquals(List.of("vehicles"), keys(new PaintBoothRules.Booth(room(true, 3, 3), 1, 2, 2, 0, 0, false, true)));
        assertEquals(List.of("building", "no_power"), keys(new PaintBoothRules.Booth(room(true, 3, 3), 1, 2, 1, 1.8, 1.5, true, false)));
    }
}
