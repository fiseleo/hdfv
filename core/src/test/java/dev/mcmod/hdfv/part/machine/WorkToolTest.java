package dev.mcmod.hdfv.part.machine;

import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.vehicle.DesignException;
import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;
import dev.mcmod.aerocore.api.vehicle.Json;
import dev.mcmod.aerocore.api.vehicle.Part;
import dev.mcmod.aerocore.api.vehicle.part.LinkPart;
import dev.mcmod.hdfv.design.Designs;
import dev.mcmod.hdfv.design.RecordEditor;
import dev.mcmod.hdfv.part.machine.WorkToolDesign.Kind;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 0.26: the work tools (WorkToolDesign, SampleTools): every sample and every kind's starting design checks clean and
 * compiles to a LinkPart, reads back from its JSON, weighs what the real part does, holds its rated capacity; the rams
 * reach their joints' ranges; the tools, holds, cutters, deck and seats are the kind's; the checks stop the unphysical.
 * Built directly (no part registry).
 */
class WorkToolTest {
    private static boolean invalid(WorkToolDesign d) { return d.check().stream().anyMatch(i -> i.kind() == DesignIssue.Kind.INVALID); }

    private static boolean invalid(WorkToolDesign d, String parameter) {
        return d.check().stream().anyMatch(i -> i.kind() == DesignIssue.Kind.INVALID && i.parameter().equals(parameter));
    }

    private static boolean extrapolated(WorkToolDesign d, String parameter) {
        return d.check().stream().anyMatch(i -> i.kind() == DesignIssue.Kind.EXTRAPOLATION && i.parameter().equals(parameter));
    }

    private static WorkToolDesign again(WorkToolDesign d) {
        return (WorkToolDesign) WorkToolDesign.read(Fields.of(Json.parse(Json.write(d.toJson())), "design"));
    }

    private static List<String> shown(WorkToolDesign d) { return RecordEditor.parameters(d).stream().map(RecordEditor.Parameter::name).toList(); }

    @Test void everySampleAndEveryKindIsAWorkingDesign() {
        assertEquals(36, SampleTools.ENTRIES.size());
        Set<Kind> sampled = EnumSet.noneOf(Kind.class);
        for (SampleTools.Entry e : SampleTools.ENTRIES) {
            WorkToolDesign d = e.design().get();
            sampled.add(d.kind());
            assertFalse(invalid(d), e.id() + ": " + d.check());
            LinkPart p = assertInstanceOf(LinkPart.class, d.compile(), e.id());
            assertEquals(d.massKg(), p.mass().massKg(), 1e-6 * d.massKg(), e.id() + ": its mass");
            assertNotNull(p.joint(), e.id());
            System.out.printf(Locale.ROOT, "hdfv work tool %s: %.0f kg (tool %.0f, rams %.0f), capacity %.3f m3, %d tools, %d spheres%n", e.id(), d.massKg(), d.toolKg(),
                    d.ramKg(), d.capacityM3(), p.tools().size(), p.collision().size());
        }
        for (Kind k : Kind.values()) {
            WorkToolDesign d = WorkToolDesign.of(k);
            assertFalse(invalid(d), k + ": " + d.check());
            assertInstanceOf(LinkPart.class, d.compile(), k.name());
            assertFalse(d.figures().isEmpty(), k + " has figures");
            assertEquals("mass", d.figures().get(0).key());
        }
        // every kind but the trench bucket has a sample
        assertEquals(EnumSet.complementOf(EnumSet.of(Kind.TRENCH_BUCKET)), sampled);
        assertEquals(WorkToolDesign.TYPE, WorkToolDesign.standard().type());
        assertEquals("work_tool", SampleTools.designSamples().get(0).kind());
    }

    @Test void theJsonReadsBack() {
        for (SampleTools.Entry e : SampleTools.ENTRIES) {
            WorkToolDesign d = e.design().get();
            assertEquals(d, again(d), e.id());
        }
        for (Kind k : Kind.values()) { assertEquals(WorkToolDesign.of(k), again(WorkToolDesign.of(k)), k.name()); }
        // blank values (automatic) stay blank, given ones are kept
        WorkToolDesign given = WorkToolDesign.builder(Kind.BACKHOE_BUCKET).capacity(1.3).mass(1000).base(2.0, 110).hold(38e6).build();
        assertEquals(given, again(given));
        // what a blueprint leaves out is its kind's default: an old or hand-written one reads
        Map<String, Object> bare = new HashMap<>(Map.of("type", WorkToolDesign.TYPE, "name", "bare", "toolKind", "dump_body"));
        WorkToolDesign read = (WorkToolDesign) WorkToolDesign.read(Fields.of(bare, "design"));
        assertEquals(WorkToolDesign.builder(Kind.DUMP_BODY).name("bare").build(), read);
    }

    @Test void massesAreTheRealPartsWithinHalfAgain() {
        for (SampleTools.Entry e : SampleTools.ENTRIES) {
            if (!(e.referenceKg() > 0)) { continue; }
            double kg = e.design().get().toolKg(), ratio = kg / e.referenceKg();
            assertTrue(ratio > 1 / 1.5 && ratio < 1.5, e.id() + ": " + kg + " kg against " + e.referenceKg() + " (" + e.source() + ")");
        }
        // the makers' figures the plan names, closer
        assertEquals(900, SampleTools.excavatorBucket1m3().toolKg(), 0.15 * 900, "CAT 320 GP bucket");
        assertEquals(2000, SampleTools.dozerSuBlade().toolKg(), 0.15 * 2000, "D6 SU blade");
        assertEquals(14000, SampleTools.haulTruckBody60m3().toolKg(), 0.15 * 14000, "777 body");
        assertEquals(3000, SampleTools.millingDrum2m().toolKg(), 0.15 * 3000, "W 200 drum");
        // a 120 mm bucket cylinder of about 1.06 m stroke weighs some 130 to 220 kg (the links' rule, MachineRules)
        double ram = SampleTools.excavatorBucket1m3().ramKg();
        assertTrue(ram > 100 && ram < 250, "bucket cylinder " + ram);
        // a given mass replaces the estimate, its pieces scaled
        WorkToolDesign given = WorkToolDesign.builder(Kind.LOADER_BUCKET).mass(2000).build();
        assertEquals(2000, given.massKg(), 1e-9);
        assertEquals(2000, given.massBreakdownKg().values().stream().mapToDouble(Double::doubleValue).sum(), 1e-6);
        assertEquals(2000, given.compile().mass().massKg(), 1e-6);
    }

    @Test void bucketsBodiesAndBowlsHoldTheirRating() {
        int rated = 0;
        for (SampleTools.Entry e : SampleTools.ENTRIES) {
            if (!(e.nominalM3() > 0)) { continue; }
            WorkToolDesign d = e.design().get();
            assertEquals(e.nominalM3(), d.capacityM3(), 0.15 * e.nominalM3(), e.id() + " (" + e.source() + ")");
            if (d.heapSlope() > 0) { assertTrue(d.heapedM3() > d.struckM3(), e.id() + " heaps over its struck capacity"); }
            rated++;
        }
        assertTrue(rated >= 12, "rated samples " + rated);
        // excavator buckets heap 1:1 (ISO 7451), loader buckets and bodies 2:1 (SAE J742, J1363), draglines are rated struck
        assertEquals(1.0, SampleTools.excavatorBucket1m3().heapSlope());
        assertEquals(0.5, SampleTools.loaderBucket3m3().heapSlope());
        assertEquals(0.5, SampleTools.haulTruckBody60m3().heapSlope());
        assertEquals(SampleTools.draglineBucket().struckM3(), SampleTools.draglineBucket().capacityM3(), 1e-12);
        // a given capacity overrides the geometry's
        assertEquals(1.5, WorkToolDesign.builder(Kind.BACKHOE_BUCKET).capacity(1.5).build().capacityM3(), 1e-12);
        // blades: SAE J1265 against the Caterpillar handbook's D6 SU 5.6 m3 and D8 U 11.7 m3
        assertEquals(5.6, SampleTools.dozerSuBlade().bladeCapacityM3(), 0.15 * 5.6);
        assertEquals(11.7, SampleTools.dozerUBlade().bladeCapacityM3(), 0.15 * 11.7);
        assertTrue(SampleTools.snowPlow().bladeCapacityM3() > 0 && SampleTools.graderMoldboard().bladeCapacityM3() > 0);
    }

    @Test void ramsReachTheirJointsRanges() {
        int rams = 0;
        for (SampleTools.Entry e : SampleTools.ENTRIES) {
            WorkToolDesign d = e.design().get();
            LinkPart p = (LinkPart) d.compile();
            if (!d.rammed()) {
                if (d.kind() == Kind.DRAGLINE_BUCKET) { assertInstanceOf(LinkPart.Free.class, p.actuator(), "a dragline hangs free on its hitch"); }
                else { assertNull(p.actuator(), e.id() + " is held"); assertEquals(0, p.joint().maximum() - p.joint().minimum(), 1e-12, e.id()); }
                continue;
            }
            rams++;
            LinkPart.Ram ram = assertInstanceOf(LinkPart.Ram.class, p.actuator(), e.id());
            Part.Joint j = p.joint();
            double closed = MachineRules.closedLengthM(d.ramBoreM(), d.ramStrokeM()), open = closed + d.ramStrokeM();
            assertEquals(closed, d.ramClosedM(), 1e-12, "the links' cylinder rule");
            for (double q : new double[]{j.minimum(), j.maximum()}) {
                double length = p.ramLength(ram, q);
                assertTrue(length > closed - 1e-3 && length < open + 1e-3, e.id() + " at " + q + ": " + length + " within " + closed + ".." + open);
            }
            // no dead centre within the range: the lever keeps its sign and stays clear of zero
            double sign = Math.signum(p.lever(ram, j.minimum()));
            for (int i = 0; i <= 40; i++) {
                double q = j.minimum() + (j.maximum() - j.minimum()) * i / 40;
                assertTrue(p.lever(ram, q) * sign >= LinkDesign.MIN_LEVER * 0.99, e.id() + " lever at " + q + ": " + p.lever(ram, q));
            }
            assertTrue(j.initial() >= j.minimum() && j.initial() <= j.maximum());
            assertEquals(d.circuit(), ram.circuit());
            assertTrue(ram.holdPa() >= ram.reliefPa());
            assertTrue(p.inputs().contains("command"), e.id());
        }
        assertTrue(rams >= 15, "rammed samples " + rams);
        // a backhoe curls in with q up (the game's "bucket" channel), its lip moving toward the machine
        WorkToolDesign bucket = SampleTools.excavatorBucket1m3();
        Vec3d lip = bucket.workPoint().point(), curled = bucket.moved(lip, 0.1);
        assertTrue(curled.x() < lip.x(), "curling moves the tip toward -x: " + lip + " -> " + curled);
        // a clamshell closes and a trencher's boom rises with q up, a dump body tips with q up
        WorkToolDesign clam = SampleTools.clamshell();
        assertEquals(0, clam.joint().maximum(), 1e-12, "a clamshell is closed at its upper limit");
        Vec3d open = clam.moved(new Vec3d(0, -clam.toolHeightM(), 0), clam.joint().minimum());
        assertTrue(open.x() > 0.3, "open, its moving shell's lip swings out: " + open);
        WorkToolDesign body = SampleTools.haulTruckBody60m3();
        assertTrue(body.moved(new Vec3d(body.toolLengthM(), 0, 0), 0.5).y() > 1, "the front of a body rises as it tips");
        // a given base pin that does not reach is INVALID; the solved one is as good as any the circles give
        WorkToolDesign tooShort = WorkToolDesign.builder(Kind.BACKHOE_BUCKET).base(0.5, 100).build();
        assertTrue(invalid(tooShort, "ramBaseM"), "a base pin too near: " + tooShort.check());
        WorkToolDesign.RamGeometry g = bucket.ramGeometry();
        WorkToolDesign atSolved = bucket.toBuilder().base(g.basePin().length(), Math.toDegrees(Math.atan2(g.basePin().y(), g.basePin().x()))).build();
        assertFalse(invalid(atSolved), "the solved base pin given: " + atSolved.check());
        // a stroke far too short for the range: no base pin will do
        WorkToolDesign stub = WorkToolDesign.builder(Kind.BACKHOE_BUCKET).ram(0.12, 0.085, 0.2, 1, 0.65, 30).build();
        assertTrue(invalid(stub, "ramStrokeM"), "a 0.2 m stroke over 120 degrees: " + stub.check());
        // the erector slides: its ram along its axis covers the travel only when its stroke does
        assertFalse(invalid(SampleTools.segmentErector()));
        assertTrue(invalid(SampleTools.segmentErector().toBuilder().travel(1.2).build(), "ramStrokeM"));
    }

    @Test void toolsHoldsAndCuttersAreTheKinds() {
        Set<Kind> rotating = WorkToolDesign.ROTATING;
        for (SampleTools.Entry e : SampleTools.ENTRIES) {
            WorkToolDesign d = e.design().get();
            LinkPart p = (LinkPart) d.compile();
            Kind k = d.kind();
            // a cutter exactly for the rotating kinds, on its rotor "cutter"
            assertEquals(rotating.contains(k), p.cutter() != null, e.id() + " cutter");
            if (p.cutter() != null) {
                assertEquals("cutter", p.rotors().get(0).name());
                assertTrue(p.cutter().radiusM() > 0 && p.cutter().inertiaKgM2() > 0);
            } else { assertTrue(p.rotors().isEmpty(), e.id()); }
            // tools: none for the kinds the game works by their kind
            List<Part.Tool> tools = p.tools();
            assertEquals(WorkToolDesign.CUTTING.contains(k), !tools.isEmpty(), e.id() + " tools");
            for (Part.Tool t : tools) {
                Part.Tool.Kind expected = switch (k) {
                    case DOZER_BLADE, GRADER_MOLDBOARD, SNOW_PLOW, SCRAPER_BOWL -> Part.Tool.Kind.EDGE;
                    case RIPPER -> Part.Tool.Kind.TOOTH;
                    case CUTTERHEAD -> Part.Tool.Kind.FACE;
                    case MILLING_DRUM, ROADHEADER_HEAD, BUCKET_WHEEL, TRENCHER_CHAIN, AUGER, BROOM, SNOW_BLOWER -> Part.Tool.Kind.MILL;
                    default -> t.name().startsWith("tooth") ? Part.Tool.Kind.TOOTH : Part.Tool.Kind.EDGE;   // buckets: lips and teeth
                };
                assertEquals(expected, t.kind(), e.id() + " " + t.name());
                // a tool fills a "bulk..." hold the part has, or none
                if (!t.hold().isEmpty()) {
                    assertTrue(t.hold().startsWith("bulk"), e.id() + " " + t.hold());
                    assertTrue(p.holds().stream().anyMatch(h -> h.name().equals(t.hold())), e.id() + " has its tool's hold");
                }
            }
            if (!tools.isEmpty()) { assertTrue(p.inputs().containsAll(List.of("full", "pushed")), e.id()); }
            // material in "bulk", unit loads under other names
            if (WorkToolDesign.BULK_KINDS.contains(k)) {
                assertEquals(1, p.holds().size(), e.id());
                assertEquals(WorkToolDesign.BULK, p.holds().get(0).name());
                assertTrue(p.holds().get(0).capacityM3() > 0);
            } else { assertTrue(p.holds().stream().noneMatch(h -> h.name().startsWith("bulk")), e.id()); }
        }
        // a toothed bucket's lip is cut once: its teeth and lips together span its width
        WorkToolDesign bucket = SampleTools.excavatorBucket1m3();
        double span = bucket.tools().stream().mapToDouble(Part.Tool::widthM).sum();
        assertEquals(bucket.toolWidthM(), span, 1e-9);
        assertEquals(bucket.teeth(), bucket.tools().stream().filter(t -> t.kind() == Part.Tool.Kind.TOOTH).count());
        // blades cut along +x pushing what they cut; buckets fill their bowl
        Part.Tool blade = SampleTools.dozerSuBlade().tools().get(0);
        assertEquals(1, blade.cut().x(), 1e-12);
        assertEquals("", blade.hold());
        // the cutterhead's discs: 40 x 17 in
        Part.Tool face = SampleTools.tbmCutterhead6m().tools().get(0);
        assertEquals(40, face.face().discs());
        assertEquals(0.432, face.face().discDiameterM(), 1e-12);
        assertEquals(3.0, face.widthM(), 1e-12, "its radius");
        // an auger's two flights cut downward on opposite radii
        List<Part.Tool> flights = SampleTools.drillAuger().tools();
        assertEquals(2, flights.size());
        assertEquals(-1, flights.get(0).cut().y(), 1e-12);
        assertEquals(0, flights.get(0).b().add(flights.get(1).b()).x(), 1e-12);
        // the trencher's chain cuts along the travel at its boom's digging angle
        WorkToolDesign trencher = SampleTools.trencherChain();
        Part.Tool chain = trencher.tools().get(0);
        Vec3d atDigging = trencher.moved(chain.cut(), trencher.joint().minimum()).subtract(trencher.moved(Vec3d.ZERO, trencher.joint().minimum()));
        assertEquals(1, atDigging.x(), 1e-9);
        // a quick coupler carries a tool on its "coupler" point; a conveyor has its head
        assertTrue(SampleTools.quickCoupler().compile().mountPoints().containsKey(WorkToolDesign.COUPLER_POINT));
        assertTrue(SampleTools.conveyor1m().compile().mountPoints().containsKey("head"));
    }

    @Test void aPlatformHasADeckAndPlacesToStand() {
        WorkToolDesign basket = SampleTools.workPlatformBasket();
        Part p = basket.compile();
        assertEquals(1, p.deck().size());
        assertEquals(2, basket.persons(), "230 kg: two people (EN 280)");
        assertEquals(2, p.seats().size());
        for (Vec3d s : p.seats()) { assertTrue(s.y() > p.deck().get(0).max().y(), "riders over the floor"); }
        assertTrue(p.collision().stream().allMatch(c -> c.radiusM() > 0));
        // other kinds have neither
        assertTrue(SampleTools.excavatorBucket1m3().compile().deck().isEmpty());
        assertTrue(SampleTools.excavatorBucket1m3().compile().seats().isEmpty());
        // blank: rated from its floor
        WorkToolDesign auto = basket.toBuilder().rated(Double.NaN).build();
        assertEquals(WorkToolDesign.PERSON_KG * Math.floor(basket.deckAreaM2() / WorkToolDesign.PERSON_AREA_M2) + WorkToolDesign.PLATFORM_TOOLS_KG, auto.ratedKg(), 1e-9);
    }

    @Test void theScreenShowsWhatTheKindUses() {
        List<String> bucket = shown(SampleTools.excavatorBucket1m3()), conveyor = shown(SampleTools.conveyor1m()), hammer = shown(SampleTools.hydraulicHammer());
        assertTrue(bucket.containsAll(List.of("toolKind", "toolWidthM", "teeth", "capacityGivenM3", "jointed", "ramBoreM", "ramCount", "circuit", "reliefPa")));
        assertFalse(bucket.contains("beltSpeedMps") || bucket.contains("discs") || bucket.contains("cutterRadiusM") || bucket.contains("bladeStyle"));
        assertTrue(conveyor.contains("beltSpeedMps"));
        assertTrue(hammer.contains("impactEnergyJ") && !hammer.contains("ramBoreM") && !hammer.contains("jointed") && !hammer.contains("plateM"));
        assertTrue(shown(SampleTools.dozerSuBlade()).contains("bladeStyle"));
        assertTrue(shown(SampleTools.tbmCutterhead6m()).containsAll(List.of("discs", "discDiameterM", "discTipM", "discSpacingM", "cutterRadiusM")));
        assertTrue(shown(SampleTools.segmentErector()).contains("travelM") && !shown(SampleTools.segmentErector()).contains("jointMaxDeg"));
        // without its joint, a bucket hides its ram
        assertFalse(shown(SampleTools.excavatorBucket1m3().toBuilder().fixed().build()).contains("ramBoreM"));
        // every number is an estimate (blank: automatic), the circuit a choice of the pumps' circuits
        for (RecordEditor.Parameter p : RecordEditor.parameters(SampleTools.excavatorBucket1m3())) {
            if (p.name().endsWith("M") || p.name().endsWith("Deg") || p.name().endsWith("Pa")) { assertEquals(RecordEditor.Type.ESTIMATE, p.type(), p.name()); }
        }
        assertTrue(RecordEditor.parameters(SampleTools.clamshell()).stream().anyMatch(p -> p.name().equals("circuit") && p.choices().contains("auxiliary")));
        // the figures the screen shows
        Map<String, Double> f = new HashMap<>();
        for (Designs.Figure x : SampleTools.excavatorBucket1m3().figures()) { f.put(x.key(), x.value()); }
        assertTrue(f.containsKey("heapedCapacity") && f.containsKey("tipForce") && f.containsKey("jointRange"), f.toString());
        // ISO 6015 bucket digging force of a 20 t excavator: some 100 to 160 kN
        assertTrue(f.get("tipForce") > 60 && f.get("tipForce") < 250, "tip force " + f.get("tipForce"));
    }

    @Test void theChecksStopTheUnphysicalAndFlagTheUnusual() {
        WorkToolDesign bucket = SampleTools.excavatorBucket1m3();
        assertTrue(invalid(bucket.toBuilder().size(-1, 0.9, 1.25).build(), "toolWidthM"));
        assertTrue(invalid(bucket.toBuilder().plate(0.7).build(), "plateM"), "side plates wider than the bucket");
        assertTrue(invalid(bucket.toBuilder().ram(0.12, 0.13, 1.06, 1, 0.65, 30).build(), "ramRodM"), "a rod thicker than the bore");
        assertTrue(invalid(bucket.toBuilder().ram(0.12, 0.085, 1.06, 0, 0.65, 30).build(), "ramCount"));
        assertTrue(invalid(bucket.toBuilder().joint(40, 10).build(), "jointMaxDeg"));
        assertTrue(invalid(bucket.toBuilder().rake(0).build(), "rakeDeg"));
        assertTrue(invalid(bucket.toBuilder().capacity(-1).build(), "capacityGivenM3"));
        assertTrue(invalid(bucket.toBuilder().hold(20e6).build(), "holdPa"), "load-holding valves under the relief");
        assertTrue(invalid(bucket.toBuilder().hydraulics(34.3e6, 0.0036, " ").build(), "circuit"));
        assertTrue(invalid(bucket.toBuilder().mass(-5).build(), "massKg"));
        assertTrue(invalid(bucket.toBuilder().size(Double.NaN, 0.9, 1.25).build(), "toolWidthM"), "a blank width is not automatic");
        assertTrue(invalid(SampleTools.millingDrum2m().toBuilder().cutter(0.12).build(), "cutterRadiusM"), "picks round no drum");
        assertTrue(invalid(SampleTools.tbmCutterhead6m().toBuilder().discs(0, 0.432, 0.019, 0.075).build(), "discs"));
        assertTrue(invalid(SampleTools.ripperSingle().toBuilder().teeth(0).build(), "teeth"));
        assertThrows(DesignException.class, () -> bucket.toBuilder().size(-1, 0.9, 1.25).build().compile());
        // outside the makers' ranges: allowed, flagged
        assertTrue(extrapolated(bucket.toBuilder().capacity(12).build(), "capacityM3"), "a 12 m3 backhoe bucket");
        assertTrue(extrapolated(SampleTools.hydraulicHammer().toBuilder().impact(50000).build(), "impactEnergyJ"));
        assertTrue(extrapolated(SampleTools.palletForks().toBuilder().rated(20000).build(), "forkRatingKg"), "forks rated past their bending");
        assertFalse(invalid(bucket.toBuilder().capacity(12).build()));
    }
}
