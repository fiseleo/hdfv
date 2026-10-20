package dev.mcmod.hdfv.part.chassis;

import dev.mcmod.hdfv.part.engine.EngineMaterial;

import java.util.List;

/**
 * Sample chassis parts (0.15; parts, not sample cars): a pickup's ladder frame, a three-axle truck's, a sports car's
 * backbone and a racer's tube frame; 0.18: a 6x4 tractor's (a fifth wheel) and a three-axle gooseneck low-loader's (a
 * kingpin and landing legs). Positions from the frame's rear end.
 */
public final class SampleChassis {
    private SampleChassis() {}

    static ChassisStationDesign station(ChassisStationDesign.Kind kind, double x, double height, ChassisSettingsDesign.RailSection section, double h, double w, double t,
                                        ChassisStationDesign.Brace brace) {
        return new ChassisStationDesign(kind.name().toLowerCase(java.util.Locale.ROOT), kind, x, height, section, h, w, t, brace, 0.5, 0.9, false);
    }

    static ChassisStationDesign bay(ChassisStationDesign.Kind kind, double x, double height, double bayHeight, double topWidth, boolean openTop,
                                    ChassisStationDesign.Brace brace) {
        return new ChassisStationDesign(kind.name().toLowerCase(java.util.Locale.ROOT), kind, x, height, ChassisSettingsDesign.RailSection.ROUND_TUBE, 0.038, 0.038,
                0.0016, brace, bayHeight, topWidth, openTop);
    }

    /**
     * A mid-size pickup's ladder frame: 4.8 m, boxed 160 x 70 x 4 mm steel rails 0.9 m apart in front and 1.0 m at the
     * rear, kicked up 0.12 m over the rear axle; a 3.05 m wheelbase, an X-member under the cab, 2200 kg gross.
     */
    public static ChassisDesign ladderPickup() {
        var box = ChassisSettingsDesign.RailSection.RECT_TUBE;
        var f = new ChassisSettingsDesign("pickup ladder frame", ChassisSettingsDesign.Kind.LADDER, EngineMaterial.STEEL_1045, 4.8, 0.9, 1.0,
                ChassisSettingsDesign.RailSection.BOX, 0.16, 0.07, 0.004, 0.12, 1.75, ChassisSettingsDesign.Joint.WELDED, 0.038, 0.0016, 2200, "");
        return new ChassisDesign("pickup ladder frame", f, List.of(
                station(ChassisStationDesign.Kind.CROSSMEMBER, 0.05, 0, box, 0.1, 0.05, 0.003, ChassisStationDesign.Brace.NONE),
                station(ChassisStationDesign.Kind.AXLE, 1.2, -0.12, box, 0.12, 0.06, 0.004, ChassisStationDesign.Brace.NONE),
                station(ChassisStationDesign.Kind.BODY_MOUNT, 2.0, 0, box, 0.1, 0.05, 0.003, ChassisStationDesign.Brace.X),
                station(ChassisStationDesign.Kind.CROSSMEMBER, 3.0, 0, box, 0.1, 0.05, 0.003, ChassisStationDesign.Brace.NONE),
                station(ChassisStationDesign.Kind.ENGINE_MOUNT, 3.75, 0.05, box, 0.1, 0.06, 0.004, ChassisStationDesign.Brace.NONE),
                station(ChassisStationDesign.Kind.AXLE, 4.25, -0.08, box, 0.12, 0.06, 0.004, ChassisStationDesign.Brace.NONE),
                station(ChassisStationDesign.Kind.CROSSMEMBER, 4.75, 0, box, 0.1, 0.05, 0.003, ChassisStationDesign.Brace.NONE)));
    }

    /**
     * A three-axle truck's ladder frame: 7.5 m, riveted 300 x 90 x 8 mm channel rails 0.86 m apart (the European
     * standard width), 200 x 100 x 6 mm tubular crossmembers, a tandem at 1.0 and 2.35 m and the front axle at 6.4 m,
     * 12 t gross: ~710 kg, ~4 kN m per degree.
     */
    public static ChassisDesign ladderTruck() {
        var channel = ChassisSettingsDesign.RailSection.CHANNEL;
        var tube = ChassisSettingsDesign.RailSection.RECT_TUBE;
        var f = new ChassisSettingsDesign("truck ladder frame", ChassisSettingsDesign.Kind.LADDER, EngineMaterial.STEEL_1045, 7.5, 0.86, 0.86,
                channel, 0.3, 0.09, 0.008, 0, 0, ChassisSettingsDesign.Joint.RIVETED, 0.038, 0.0016, 12000, "");
        var none = ChassisStationDesign.Brace.NONE;
        return new ChassisDesign("truck ladder frame", f, List.of(
                station(ChassisStationDesign.Kind.CROSSMEMBER, 0.1, 0, tube, 0.2, 0.1, 0.006, none),
                station(ChassisStationDesign.Kind.AXLE, 1.0, -0.15, channel, 0.22, 0.08, 0.008, none),
                station(ChassisStationDesign.Kind.CROSSMEMBER, 1.68, -0.15, channel, 0.22, 0.08, 0.008, none),
                station(ChassisStationDesign.Kind.AXLE, 2.35, -0.15, channel, 0.22, 0.08, 0.008, none),
                station(ChassisStationDesign.Kind.BODY_MOUNT, 3.4, 0, tube, 0.2, 0.1, 0.006, none),
                station(ChassisStationDesign.Kind.CROSSMEMBER, 4.0, 0, tube, 0.2, 0.1, 0.006, none),
                station(ChassisStationDesign.Kind.CROSSMEMBER, 4.6, 0, tube, 0.2, 0.1, 0.006, none),
                station(ChassisStationDesign.Kind.ENGINE_MOUNT, 5.8, 0.05, tube, 0.2, 0.1, 0.006, none),
                station(ChassisStationDesign.Kind.AXLE, 6.4, -0.12, channel, 0.22, 0.08, 0.008, none),
                station(ChassisStationDesign.Kind.CROSSMEMBER, 7.4, 0, tube, 0.2, 0.1, 0.006, none)));
    }

    /**
     * 0.18: a 6x4 tractor's ladder frame: 5.9 m of riveted 300 x 90 x 8 mm high-strength steel channel 0.86 m apart, a
     * tandem at 0.9 and 2.25 m, the fifth wheel 0.3 m ahead of the tandem's middle (its base on a 30 mm plate), the engine
     * low between the rails under the cab and the front axle at 5.18 m (a 3.6 m wheelbase to the tandem's middle; SampleBodies.cabOver's arch
     * over it with the cab's front at the frame's), 26 t gross (with the kingpin's load).
     */
    public static ChassisDesign tractor6x4() {
        var channel = ChassisSettingsDesign.RailSection.CHANNEL;
        var tube = ChassisSettingsDesign.RailSection.RECT_TUBE;
        var f = new ChassisSettingsDesign("6x4 tractor frame", ChassisSettingsDesign.Kind.LADDER, EngineMaterial.STEEL_4340, 5.9, 0.86, 0.86,
                channel, 0.3, 0.09, 0.008, 0, 0, ChassisSettingsDesign.Joint.RIVETED, 0.038, 0.0016, 26000, "");
        var none = ChassisStationDesign.Brace.NONE;
        return new ChassisDesign("6x4 tractor frame", f, List.of(
                station(ChassisStationDesign.Kind.CROSSMEMBER, 0.1, 0, tube, 0.2, 0.1, 0.006, none),
                station(ChassisStationDesign.Kind.AXLE, 0.9, -0.15, channel, 0.22, 0.08, 0.008, none),
                station(ChassisStationDesign.Kind.FIFTH_WHEEL, 1.875, 0.03, tube, 0.25, 0.12, 0.008, none),
                station(ChassisStationDesign.Kind.AXLE, 2.25, -0.15, channel, 0.22, 0.08, 0.008, none),
                station(ChassisStationDesign.Kind.CROSSMEMBER, 3.2, 0, tube, 0.2, 0.1, 0.006, none),
                station(ChassisStationDesign.Kind.CROSSMEMBER, 4.0, 0, tube, 0.2, 0.1, 0.006, none),
                station(ChassisStationDesign.Kind.ENGINE_MOUNT, 4.6, -0.2, tube, 0.2, 0.1, 0.006, none),
                station(ChassisStationDesign.Kind.AXLE, 5.175, -0.12, channel, 0.22, 0.08, 0.008, none),
                station(ChassisStationDesign.Kind.CROSSMEMBER, 5.8, 0, tube, 0.2, 0.1, 0.006, none)));
    }

    /**
     * 0.18: a three-axle gooseneck low-loader's frame: 13.5 m of welded 450 x 200 x 12 mm high-strength steel I-beams
     * 0.9 m apart, the rails raised 0.6 m ahead of 10 m (the gooseneck, matching SampleBodies.lowLoaderDeck), a tridem
     * at 0.9, 2.2 and 3.5 m, landing legs at 11.2 m and the kingpin's plate 8 cm under the neck at 12.7 m (a few cm under
     * the sample tractor's fifth wheel, as backing under wants), 30 t gross.
     */
    public static ChassisDesign lowLoader3() {
        var beam = ChassisSettingsDesign.RailSection.I_BEAM;
        var tube = ChassisSettingsDesign.RailSection.RECT_TUBE;
        var f = new ChassisSettingsDesign("gooseneck low-loader frame", ChassisSettingsDesign.Kind.LADDER, EngineMaterial.STEEL_4340, 13.5, 0.9, 0.9,
                beam, 0.45, 0.2, 0.012, 0, 0, ChassisSettingsDesign.Joint.WELDED, 0.038, 0.0016, 30000, "", 0.6, 10.0);
        var none = ChassisStationDesign.Brace.NONE;
        return new ChassisDesign("gooseneck low-loader frame", f, List.of(
                station(ChassisStationDesign.Kind.CROSSMEMBER, 0.1, 0, tube, 0.2, 0.1, 0.006, none),
                station(ChassisStationDesign.Kind.AXLE, 0.9, -0.15, tube, 0.22, 0.1, 0.008, none),
                station(ChassisStationDesign.Kind.AXLE, 2.2, -0.15, tube, 0.22, 0.1, 0.008, none),
                station(ChassisStationDesign.Kind.AXLE, 3.5, -0.15, tube, 0.22, 0.1, 0.008, none),
                station(ChassisStationDesign.Kind.CROSSMEMBER, 5.0, 0, tube, 0.2, 0.1, 0.006, ChassisStationDesign.Brace.X),
                station(ChassisStationDesign.Kind.CROSSMEMBER, 6.5, 0, tube, 0.2, 0.1, 0.006, none),
                station(ChassisStationDesign.Kind.CROSSMEMBER, 8.0, 0, tube, 0.2, 0.1, 0.006, ChassisStationDesign.Brace.X),
                station(ChassisStationDesign.Kind.CROSSMEMBER, 9.5, 0, tube, 0.2, 0.1, 0.006, none),
                station(ChassisStationDesign.Kind.LANDING_LEGS, 11.2, -0.1, tube, 0.15, 0.08, 0.005, none),
                station(ChassisStationDesign.Kind.KINGPIN, 12.7, -0.08, tube, 0.25, 0.15, 0.01, none),
                station(ChassisStationDesign.Kind.CROSSMEMBER, 13.4, 0, tube, 0.2, 0.1, 0.006, none)));
    }

    /** A small sports car's backbone: a 200 x 160 x 3 mm steel box 3.8 m long, outriggers 1.1 m across, a 2.2 m wheelbase, 1100 kg gross. */
    public static ChassisDesign backbone() {
        var box = ChassisSettingsDesign.RailSection.RECT_TUBE;
        var f = new ChassisSettingsDesign("sports backbone", ChassisSettingsDesign.Kind.BACKBONE, EngineMaterial.STEEL_1045, 3.8, 1.1, 1.1,
                ChassisSettingsDesign.RailSection.RECT_TUBE, 0.2, 0.16, 0.003, 0, 0, ChassisSettingsDesign.Joint.WELDED, 0.038, 0.0016, 1100, "");
        return new ChassisDesign("sports backbone", f, List.of(
                station(ChassisStationDesign.Kind.AXLE, 0.55, -0.05, box, 0.1, 0.06, 0.003, ChassisStationDesign.Brace.NONE),
                station(ChassisStationDesign.Kind.BODY_MOUNT, 1.5, 0, box, 0.08, 0.05, 0.002, ChassisStationDesign.Brace.NONE),
                station(ChassisStationDesign.Kind.ENGINE_MOUNT, 2.4, 0.05, box, 0.1, 0.06, 0.003, ChassisStationDesign.Brace.NONE),
                station(ChassisStationDesign.Kind.AXLE, 2.75, -0.05, box, 0.1, 0.06, 0.003, ChassisStationDesign.Brace.NONE)));
    }

    /**
     * A racer's tube frame: 4.0 m of 38 x 1.6 mm alloy-steel tube 1.0 m wide at the floor, rings 0.45 to 0.9 m high, a
     * cockpit open on top, the bays braced with X's, the engine behind the driver; a 3.3 m wheelbase, 900 kg gross.
     */
    public static ChassisDesign tubeFrame() {
        var f = new ChassisSettingsDesign("racer tube frame", ChassisSettingsDesign.Kind.SPACE_FRAME, EngineMaterial.STEEL_4340, 4.0, 1.0, 1.0,
                ChassisSettingsDesign.RailSection.ROUND_TUBE, 0.038, 0.038, 0.0016, 0, 0, ChassisSettingsDesign.Joint.WELDED, 0.038, 0.0016, 900, "");
        var x = ChassisStationDesign.Brace.X;
        return new ChassisDesign("racer tube frame", f, List.of(
                bay(ChassisStationDesign.Kind.AXLE, 0.3, -0.05, 0.45, 0.8, false, x),
                bay(ChassisStationDesign.Kind.ENGINE_MOUNT, 0.9, 0.05, 0.6, 0.9, false, x),
                bay(ChassisStationDesign.Kind.BODY_MOUNT, 1.7, 0, 0.9, 1.0, true, ChassisStationDesign.Brace.SINGLE),
                bay(ChassisStationDesign.Kind.CROSSMEMBER, 2.6, 0, 0.85, 0.9, false, x),
                bay(ChassisStationDesign.Kind.AXLE, 3.6, -0.05, 0.5, 0.7, false, x),
                bay(ChassisStationDesign.Kind.CROSSMEMBER, 3.95, 0, 0.45, 0.6, false, ChassisStationDesign.Brace.NONE)));
    }
}
