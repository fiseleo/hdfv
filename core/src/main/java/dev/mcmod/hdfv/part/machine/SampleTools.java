package dev.mcmod.hdfv.part.machine;

import dev.mcmod.hdfv.part.machine.WorkToolDesign.BladeStyle;
import dev.mcmod.hdfv.part.machine.WorkToolDesign.Kind;

import java.util.List;
import java.util.function.Supplier;

/**
 * The sample work tools (0.26, hdfv:work_tool), each sized after a maker's part, with the capacity and mass the real one
 * has. Most are WorkToolDesign.of(kind) (the kind's starting design); the others change it. Like every sample they show
 * the format and the physics, not balanced game content.
 *
 * <p>Reference masses: the excavator bucket (CAT 320 GP, 1.19 m^3, about 900 kg), the D6 SU blade (3.4 x 1.3 m, about
 * 2 t), the 777's 60 m^3 body (about 14 t) and the Wirtgen W 200's 2 m drum (about 3 t) are the makers' figures the
 * 0.26 plan names; the others (estimate = true) are typical figures for the class, rounded, from the makers' ranges as
 * remembered here and not checked against a data sheet in this session. The capacities are the makers' ratings (SAE /
 * ISO, see WorkToolDesign) for the buckets, bowls, bodies and the mixer.
 */
public final class SampleTools {
    private SampleTools() {}

    /** A sample: its id, its design, its rated capacity (m^3, 0: none), the real part's mass (kg, without its rams), whether that is an estimate, and where it is from. */
    public record Entry(String id, Supplier<WorkToolDesign> design, double nominalM3, double referenceKg, boolean estimate, String source) {}

    private static WorkToolDesign named(Kind kind, String name) { return WorkToolDesign.builder(kind).name(name).build(); }

    public static WorkToolDesign excavatorBucket1m3() { return named(Kind.BACKHOE_BUCKET, "excavator bucket 1.2 m3"); }

    public static WorkToolDesign miniExcavatorBucket() {
        return WorkToolDesign.builder(Kind.BACKHOE_BUCKET).name("mini excavator bucket 600 mm").size(0.6, 0.42, 0.58).plate(0.007).teeth(4)
                .ram(0.07, 0.04, 0.5, 1, 0.32, 30).hydraulics(24.5e6, 0.0006, WorkToolDesign.MAIN_CIRCUIT).build();
    }

    public static WorkToolDesign loaderBucket3m3() { return named(Kind.LOADER_BUCKET, "loader bucket 3 m3"); }
    public static WorkToolDesign miningDipper30m3() { return named(Kind.FACE_SHOVEL_BUCKET, "face shovel bucket 30 m3"); }
    public static WorkToolDesign draglineBucket() { return named(Kind.DRAGLINE_BUCKET, "dragline bucket 2.3 m3"); }
    public static WorkToolDesign clamshell() { return named(Kind.CLAMSHELL, "clamshell 1.5 m3"); }
    public static WorkToolDesign dozerSuBlade() { return named(Kind.DOZER_BLADE, "D6 class SU blade"); }

    public static WorkToolDesign dozerUBlade() {
        return WorkToolDesign.builder(Kind.DOZER_BLADE).name("D8 class U blade").style(BladeStyle.U).size(4.26, 1.74, 0.6).plate(0.025).fixed().build();
    }

    public static WorkToolDesign graderMoldboard() { return named(Kind.GRADER_MOLDBOARD, "grader moldboard 12 ft"); }
    public static WorkToolDesign snowPlow() { return named(Kind.SNOW_PLOW, "truck snow plow 3 m"); }
    public static WorkToolDesign ripperSingle() { return named(Kind.RIPPER, "single-shank ripper"); }
    public static WorkToolDesign hydraulicHammer() { return named(Kind.HYDRAULIC_HAMMER, "hydraulic breaker 3600 J"); }
    public static WorkToolDesign grapple() { return named(Kind.GRAPPLE, "demolition grapple"); }
    public static WorkToolDesign palletForks() { return WorkToolDesign.builder(Kind.FORKS).name("pallet forks 4 t").rated(4000).build(); }
    public static WorkToolDesign quickCoupler() { return named(Kind.QUICK_COUPLER, "quick coupler (20 t class)"); }
    public static WorkToolDesign plateCompactor() { return named(Kind.COMPACTOR_PLATE, "excavator plate compactor"); }
    public static WorkToolDesign scraperBowl15m3() { return named(Kind.SCRAPER_BOWL, "scraper bowl 15 m3"); }
    public static WorkToolDesign haulTruckBody60m3() { return named(Kind.DUMP_BODY, "haul truck body 60 m3"); }

    public static WorkToolDesign articulatedTruckBody18m3() {
        return WorkToolDesign.builder(Kind.DUMP_BODY).name("articulated truck body 18 m3").size(2.95, 1.0, 5.0).plate(0.016).joint(0, 70)
                .ram(0.16, 0.1, 1.3, 2, 2.0, -4).hydraulics(21e6, 0.004, WorkToolDesign.MAIN_CIRCUIT).build();
    }

    public static WorkToolDesign dumpTruckBody10m3() {
        return WorkToolDesign.builder(Kind.DUMP_BODY).name("tipper body 10 m3").size(2.35, 0.8, 4.5).plate(0.01).joint(0, 50)
                .ram(0.14, 0.1, 1.9, 1, 3.6, -3).hydraulics(18e6, 0.0015, WorkToolDesign.MAIN_CIRCUIT).build();
    }

    public static WorkToolDesign conveyor1m() { return named(Kind.CONVEYOR, "discharge conveyor 1 m belt"); }
    public static WorkToolDesign paverHopper() { return named(Kind.PAVER_HOPPER, "paver hopper"); }
    public static WorkToolDesign screed3m() { return named(Kind.SCREED, "extending screed 3 to 6 m"); }
    public static WorkToolDesign millingDrum2m() { return named(Kind.MILLING_DRUM, "milling drum 2 m"); }
    public static WorkToolDesign tbmCutterhead6m() { return named(Kind.CUTTERHEAD, "TBM cutterhead 6 m"); }
    public static WorkToolDesign roadheaderHead() { return named(Kind.ROADHEADER_HEAD, "roadheader transverse heads"); }
    public static WorkToolDesign bucketWheel10m() { return named(Kind.BUCKET_WHEEL, "bucket wheel 10 m"); }
    public static WorkToolDesign trencherChain() { return named(Kind.TRENCHER_CHAIN, "trencher chain boom 1.8 m"); }
    public static WorkToolDesign drillAuger() { return named(Kind.AUGER, "drill auger 600 mm"); }
    public static WorkToolDesign pileHammer5t() { return named(Kind.PILE_HAMMER, "hydraulic pile hammer 5 t"); }
    public static WorkToolDesign broom() { return named(Kind.BROOM, "pickup broom 1.8 m"); }
    public static WorkToolDesign snowBlower() { return named(Kind.SNOW_BLOWER, "snow blower 1.8 m"); }
    public static WorkToolDesign waterSprayBar() { return named(Kind.SPRAY_BAR, "water spray bar"); }
    public static WorkToolDesign mixerDrum9m3() { return named(Kind.MIXER_DRUM, "mixer drum 9 m3"); }
    public static WorkToolDesign workPlatformBasket() { return named(Kind.WORK_PLATFORM, "work platform basket"); }
    public static WorkToolDesign segmentErector() { return named(Kind.SEGMENT_ERECTOR, "segment erector"); }

    /** Every sample in the order the design screen lists them. */
    public static final List<Entry> ENTRIES = List.of(
            new Entry("excavator_bucket_1m3", SampleTools::excavatorBucket1m3, 1.19, 900, false, "CAT 320 GP bucket, 1.19 m3 (ISO 7451), about 900 kg"),
            new Entry("mini_excavator_bucket", SampleTools::miniExcavatorBucket, 0.12, 100, true, "600 mm bucket of a 3.5 t mini excavator, 0.10-0.13 m3, 80-110 kg"),
            new Entry("loader_bucket_3m3", SampleTools::loaderBucket3m3, 3.0, 1700, true, "CAT 950 class 3 m3 general purpose bucket (SAE J742), 1.6-1.8 t"),
            new Entry("mining_dipper_30m3", SampleTools::miningDipper30m3, 30, 33000, true, "29-30 m3 face-shovel bucket of a 500 t hydraulic shovel, 30-35 t"),
            new Entry("dragline_bucket", SampleTools::draglineBucket, 2.3, 1900, true, "3 yd3 (2.3 m3) crane dragline bucket, 1.8-2.0 t"),
            new Entry("clamshell", SampleTools::clamshell, 1.5, 2000, true, "1.5 m3 rehandling clamshell, about 2 t"),
            new Entry("dozer_su_blade", SampleTools::dozerSuBlade, 0, 2000, false, "CAT D6 SU blade, 3.4 m x 1.3 m, about 2 t (5.6 m3 by the handbook)"),
            new Entry("dozer_u_blade", SampleTools::dozerUBlade, 0, 5000, true, "CAT D8 class 8U blade, 4.26 m x 1.74 m, about 5 t (11.7 m3 by the handbook)"),
            new Entry("grader_moldboard", SampleTools::graderMoldboard, 0, 700, true, "12 ft (3.66 m) x 610 mm x 22 mm grader moldboard with edge and end bits, about 0.7 t"),
            new Entry("snow_plow", SampleTools::snowPlow, 0, 400, true, "3 m truck plow with its pivot frame, about 0.4 t"),
            new Entry("ripper_single", SampleTools::ripperSingle, 0, 600, true, "D6 class single shank with its tip and beam, about 0.6 t"),
            new Entry("hydraulic_hammer", SampleTools::hydraulicHammer, 0, 1800, true, "breaker for 18-32 t carriers (Epiroc HB 2000-2500 class), 1.6-2.5 t"),
            new Entry("grapple", SampleTools::grapple, 0, 1400, true, "demolition and sorting grapple for 20 t carriers, 1.2-1.6 t"),
            new Entry("pallet_forks", SampleTools::palletForks, 0, 450, true, "loader fork carriage with 1.2 m 50 x 125 forks, 0.4-0.5 t"),
            new Entry("quick_coupler", SampleTools::quickCoupler, 0, 350, true, "pin-grabber coupler for 20 t excavators, 0.3-0.4 t"),
            new Entry("plate_compactor", SampleTools::plateCompactor, 0, 800, true, "excavator-mounted plate compactor for 12-25 t carriers, 0.6-0.9 t"),
            new Entry("scraper_bowl_15m3", SampleTools::scraperBowl15m3, 15.3, 8000, true, "CAT 621K bowl, 15.3 m3 heaped (SAE J741); bowl, apron and ejector about 8 t"),
            new Entry("haul_truck_body_60m3", SampleTools::haulTruckBody60m3, 60, 14000, false, "CAT 777 dual-slope body, 60 m3 heaped (SAE 2:1), about 14 t"),
            new Entry("articulated_truck_body_18m3", SampleTools::articulatedTruckBody18m3, 17.5, 4500, true, "CAT 730 class body, 17.5 m3 heaped (SAE 2:1), about 4.5 t"),
            new Entry("dump_truck_body_10m3", SampleTools::dumpTruckBody10m3, 10, 1800, true, "6x4 tipper body of wear steel, 10 m3 heaped, about 1.8 t"),
            new Entry("conveyor_1m", SampleTools::conveyor1m, 0, 1200, true, "8 m troughed conveyor with a 1 m belt (a cold planer's discharge conveyor), about 1.2 t"),
            new Entry("paver_hopper", SampleTools::paverHopper, 0, 2000, true, "hopper of a 2.5-3 m paver (13 t of mix, Voegele Super 1800 class), about 2 t"),
            new Entry("screed_3m", SampleTools::screed3m, 0, 4000, true, "extending screed, 3 m basic to 6 m, about 4 t"),
            new Entry("milling_drum_2m", SampleTools::millingDrum2m, 0, 3000, false, "Wirtgen W 200 milling drum, 2 m, 162 picks, about 3 t with its hood"),
            new Entry("tbm_cutterhead_6m", SampleTools::tbmCutterhead6m, 0, 50000, true, "Herrenknecht 6 m cutterhead with about 40 x 17 in discs, some 45-55 t"),
            new Entry("roadheader_head", SampleTools::roadheaderHead, 0, 5000, true, "transverse cutting heads of a 100-130 t roadheader with their gearbox, about 5 t"),
            new Entry("bucket_wheel_10m", SampleTools::bucketWheel10m, 1.0, 50000, true, "10 m bucket wheel with ten 1 m3 buckets, some 50 t"),
            new Entry("trencher_chain", SampleTools::trencherChain, 0, 800, true, "1.8 m deep, 300 mm chain trencher boom, about 0.8 t"),
            new Entry("drill_auger", SampleTools::drillAuger, 0, 600, true, "600 mm x 6 m earth auger, 0.5-0.7 t"),
            new Entry("pile_hammer_5t", SampleTools::pileHammer5t, 0, 9000, true, "hydraulic impact hammer with a 5 t ram (Junttan HHK 5 class), about 9 t"),
            new Entry("broom", SampleTools::broom, 0, 360, true, "1.8 m pickup broom with its hopper (skid-steer attachment), about 0.36 t"),
            new Entry("snow_blower", SampleTools::snowBlower, 0, 420, true, "1.8 m two-stage snow blower (skid-steer attachment), about 0.42 t"),
            new Entry("water_spray_bar", SampleTools::waterSprayBar, 0, 40, true, "2.4 m rear spray bar of a water truck with its nozzles and valves, about 40 kg"),
            new Entry("mixer_drum_9m3", SampleTools::mixerDrum9m3, 9, 2800, true, "9 m3 truck mixer drum (ASTM C94's 63 % of 14.3 m3), about 2.8 t"),
            new Entry("work_platform_basket", SampleTools::workPlatformBasket, 0, 130, true, "2.44 x 0.91 m steel basket, 230 kg (two people), about 0.13 t"),
            new Entry("segment_erector", SampleTools::segmentErector, 0, 3500, true, "lifting head of a 6 m TBM's segment erector, about 3.5 t"));

    /** A sample's design by id (IllegalArgumentException when there is none). */
    public static WorkToolDesign sample(String id) {
        for (Entry e : ENTRIES) { if (e.id().equals(id)) { return e.design().get(); } }
        throw new IllegalArgumentException("No work tool sample " + id);
    }

    /** The samples as the design screen lists them (Designs.SAMPLES), all of the kind "work_tool". */
    public static List<dev.mcmod.hdfv.design.Designs.Sample> designSamples() {
        return ENTRIES.stream().map(e -> new dev.mcmod.hdfv.design.Designs.Sample(e.id(), "work_tool", () -> e.design().get())).toList();
    }
}
