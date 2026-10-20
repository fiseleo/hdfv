package dev.mcmod.hdfv.look;

import dev.mcmod.hdfv.look.GroundEffects.Effect;
import dev.mcmod.hdfv.look.GroundEffects.Ground;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 0.12: what wheels leave on the ground and throw up, by the block under them. */
class GroundEffectsTest {
    @Test void blocksAreSortedByTheirGround() {
        Map<String, Ground> expected = Map.ofEntries(Map.entry("minecraft:stone", Ground.HARD), Map.entry("hdfv:asphalt", Ground.HARD),
                Map.entry("minecraft:sandstone", Ground.HARD), Map.entry("minecraft:packed_mud", Ground.HARD), Map.entry("minecraft:sand", Ground.SAND),
                Map.entry("minecraft:red_sand", Ground.SAND), Map.entry("minecraft:gravel", Ground.GRAVEL), Map.entry("minecraft:dirt", Ground.DIRT),
                Map.entry("minecraft:coarse_dirt", Ground.DIRT), Map.entry("minecraft:dirt_path", Ground.DIRT), Map.entry("minecraft:grass_block", Ground.GRASS),
                Map.entry("minecraft:mud", Ground.MUD), Map.entry("minecraft:clay", Ground.MUD), Map.entry("minecraft:soul_sand", Ground.MUD),
                Map.entry("minecraft:snow", Ground.SNOW), Map.entry("minecraft:snow_block", Ground.SNOW), Map.entry("minecraft:powder_snow", Ground.SNOW),
                Map.entry("minecraft:packed_ice", Ground.ICE), Map.entry("minecraft:air", Ground.NONE), Map.entry("minecraft:water", Ground.NONE));
        expected.forEach((id, g) -> assertEquals(g, GroundEffects.ground(id), id));
    }

    @Test void aWheelAtRestOrOffTheGroundDoesNothing() {
        for (Ground g : Ground.values()) {
            for (boolean wet : new boolean[]{false, true}) {
                // at rest nothing is thrown up (a soft ground's rut is laid only as the wheel moves on)
                Effect still = GroundEffects.of(g, wet, 0, 4000, 0);
                assertEquals(0, still.smoke() + still.dust() + still.mud() + still.snow() + still.spray(), g + " at rest");
                assertFalse(GroundEffects.of(g, wet, 20, 0, 30).any(), g + " in the air");
            }
        }
        // rolling on a dry road: no smoke, no marks
        assertFalse(GroundEffects.of(Ground.HARD, false, 0.3, 4000, 30).any());
        assertFalse(GroundEffects.of(Ground.ICE, false, 10, 4000, 30).any(), "ice neither marks nor smokes");
    }

    @Test void aSlidingTyreSmokesAndMarksTheRoad() {
        Effect light = GroundEffects.of(Ground.HARD, false, 1, 4000, 10), burnout = GroundEffects.of(Ground.HARD, false, 12, 4000, 1);
        assertEquals(0, light.smoke());
        assertEquals(0, light.markArgb());
        assertTrue(burnout.smoke() > 20, "smoke " + burnout.smoke());
        assertTrue((burnout.markArgb() >>> 24) > 80 && (burnout.markArgb() & 0xffffff) == 0x101012, "a black mark");
        // less loaded, less smoke
        assertTrue(GroundEffects.of(Ground.HARD, false, 12, 1000, 1).smoke() < burnout.smoke());
        // wet: spray instead
        Effect wet = GroundEffects.of(Ground.HARD, true, 0.3, 4000, 25);
        assertTrue(wet.spray() > 10 && wet.smoke() == 0 && wet.markArgb() == 0, "wet road " + wet);
    }

    @Test void softGroundDustsMudsAndRuts() {
        Effect dirt = GroundEffects.of(Ground.DIRT, false, 0.3, 4000, 15);
        assertTrue(dirt.dust() > 5 && dirt.markArgb() != 0, "dirt " + dirt);
        Effect mud = GroundEffects.of(Ground.MUD, false, 4, 4000, 5);
        assertTrue(mud.mud() > 10 && (mud.markArgb() >>> 24) > (dirt.markArgb() >>> 24), "mud ruts deeper " + mud);
        Effect snow = GroundEffects.of(Ground.SNOW, false, 1, 4000, 10);
        assertTrue(snow.snow() > 10 && snow.markArgb() != 0, "snow " + snow);
        assertTrue(GroundEffects.of(Ground.DIRT, true, 0.3, 4000, 15).dust() < dirt.dust(), "wet ground dusts less");
    }
}
