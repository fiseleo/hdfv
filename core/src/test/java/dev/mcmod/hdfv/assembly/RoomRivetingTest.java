package dev.mcmod.hdfv.assembly;

import dev.mcmod.hdfv.manufacture.Head;
import dev.mcmod.hdfv.part.HdfvParts;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** A riveting machine at an assembly room sets the structure's rivets sooner (0.17); a welded car gains nothing. */
class RoomRivetingTest {
    static { HdfvParts.register(); }

    @Test void aRivetingMachineAtTheRoomShortensTheRivetedWork() {
        AssemblyPlan sea = AssemblyPlan.of(AutoWiring.wire(SampleLayouts.seaplane()));
        double hand = sea.laborHours(), rivets = sea.rivetingHours();
        assertTrue(rivets > 0 && rivets < hand, "the seaplane's riveted skins: " + rivets + " of " + hand + " h");
        assertEquals(hand, sea.laborHours(null), 1e-12);
        // a rivet gun is how the hand rule sets them; a squeeze riveter still wants the holes drilled; a drilling and riveting head does it all
        assertEquals(hand, sea.laborHours(Head.IMPACT_RIVETER), 1e-12);
        assertEquals(hand - rivets * (1 - 15.0 / 20), sea.laborHours(Head.SQUEEZE_RIVETER), 1e-9);
        assertEquals(hand - rivets * (1 - 6.0 / 20), sea.laborHours(Head.DRILL_RIVETER), 1e-9);
        AssemblyPlan car = AssemblyPlan.of(AutoWiring.wire(SampleLayouts.sedan()));
        assertEquals(0, car.rivetingHours(), 1e-12, "a spot-welded car");
        assertEquals(car.laborHours(), car.laborHours(Head.DRILL_RIVETER), 1e-12);
    }
}
