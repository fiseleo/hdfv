package dev.mcmod.hdfv.part.wheel;

import dev.mcmod.hdfv.design.Designs;
import dev.mcmod.hdfv.paint.Repaint;
import dev.mcmod.hdfv.part.HdfvParts;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 0.16: a wheel's style, colour and lip and a tyre's lettering are looks: old axles read and write as before. */
class RimLookTest {
    static { HdfvParts.register(); }

    @Test void oldWheelsAreAsBefore() {
        AxleDesign a = SampleWheels.sedanFront();
        String json = Designs.write(a);
        for (String key : new String[]{"rimStyle", "rimColor", "lipDepthM", "sideText", "sideTextColor"}) { assertFalse(json.contains(key), key + " is not written at its default"); }
        assertEquals(a, Designs.read(json));
    }

    @Test void theLooksAreLooks() {
        AxleDesign a = SampleWheels.sedanFront();
        AxleDesign styled = (AxleDesign) Repaint.setLook(a, "wheel", "rimStyle", "beadlock");
        styled = (AxleDesign) Repaint.setLook(styled, "wheel", "rimColor", "#C9A04A");
        styled = (AxleDesign) Repaint.setLook(styled, "wheel", "lipDepthM", "20");
        styled = (AxleDesign) Repaint.setLook(styled, "tyre", "sideText", "HDFV RACING");
        assertEquals(RimDesign.Style.BEADLOCK, styled.wheel().rimStyle());
        assertEquals(0.02, styled.wheel().lipDepthM(), 1e-12);
        assertEquals("HDFV RACIN", styled.tyre().sideText(), "lettering is ten characters at most");
        assertTrue(Repaint.looksOnly(a, styled), "only looks: the paint booth may change them");
        assertEquals(styled, Designs.read(Designs.write(styled)));
        assertEquals(a.compile().mass().massKg(), styled.compile().mass().massKg(), 1e-12, "the same wheel to the physics");
        assertTrue(((AxleDesign) Designs.set(a, "wheel", "lipDepthM", "900")).check().stream()
                .anyMatch(i -> i.kind() == dev.mcmod.aerocore.api.vehicle.DesignIssue.Kind.INVALID), "a lip deeper than the rim is refused");
    }
}
