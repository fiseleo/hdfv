package dev.mcmod.hdfv.part.body;

import dev.mcmod.aerocore.api.body.BodyStructure;
import dev.mcmod.aerocore.api.vehicle.part.BodyPart;
import dev.mcmod.hdfv.design.RecordEditor;
import dev.mcmod.hdfv.part.engine.EngineMaterial;

import java.util.ArrayList;
import java.util.List;

/**
 * Sample bodies (the proportions of known types, not their drawings): a Turbo Otter-class seaplane's fuselage and its
 * stepped floats, a UH-60-class helicopter's fuselage with its tail boom, a planing runabout's hull, a family car's
 * body and a small flying boat's hull (boat bottom and aircraft cabin in one body).
 */
public final class SampleBodies {
    private SampleBodies() {}

    /** A module of a kind following the last one in the list, its defaults changed by name, value pairs. */
    private static BodyModuleDesign module(List<BodyModuleDesign> list, BodyModuleDesign.Kind kind, String name, Object... changes) {
        BodyModuleDesign m = BodyModuleDesign.defaults(kind, list.isEmpty() ? null : list.get(list.size() - 1));
        m = RecordEditor.with(m, "name", name);
        for (int i = 0; i < changes.length; i += 2) { m = RecordEditor.with(m, (String) changes[i], changes[i + 1]); }
        list.add(m);
        return m;
    }

    private static BodySettingsDesign settings(BodySettingsDesign.Purpose purpose, Object... changes) {
        BodySettingsDesign s = BodySettingsDesign.of(purpose);
        for (int i = 0; i < changes.length; i += 2) { s = RecordEditor.with(s, (String) changes[i], changes[i + 1]); }
        return s;
    }

    /**
     * A Turbo Otter-class fuselage, 12.4 m: the nose with the engine mount, a two-seat cockpit carrying the high wing,
     * an eight-seat cabin (1.6 m wide, 1.6 m high) with a cargo door and a 150 l belly tank, and an upswept tail cone carrying the tail.
     */
    public static BodyDesign seaplaneFuselage() {
        List<BodyModuleDesign> m = new ArrayList<>();
        module(m, BodyModuleDesign.Kind.NOSE, "nose", "lengthM", 1.8, "widthM", 1.3, "heightM", 1.45, "bottomM", 0.05, "upperExponent", 2.5, "lowerExponent", 2.5,
                "fullness", 2.0, "skinM", 0.0012);
        module(m, BodyModuleDesign.Kind.COCKPIT, "cockpit", "lengthM", 1.8, "widthM", 1.6, "heightM", 1.65, "bottomM", 0.0, "upperExponent", 3.0, "lowerExponent", 3.0,
                "seats", 2, "seatsAbreast", 2, "seatPitchM", 1.2, "doors", 2, "doorWidthM", 0.9, "doorHeightM", 1.1, "windowShare", 0.35,
                "wing", BodyModuleDesign.WingMount.HIGH, "skinM", 0.001);
        module(m, BodyModuleDesign.Kind.CABIN, "cabin", "lengthM", 3.9, "seats", 8, "seatsAbreast", 2, "seatPitchM", 0.85, "doors", 1, "doorWidthM", 1.2,
                "doorHeightM", 1.3, "windowShare", 0.15, "fuelM3", 0.15, "skinM", 0.001);   // 0.8: its tank in place of 0.6 m3 of cargo, the same 60 kg half full
        module(m, BodyModuleDesign.Kind.TAIL_CONE, "tail cone", "lengthM", 4.9, "widthM", 0.35, "heightM", 0.55, "bottomM", 1.0, "fullness", 1.4, "skinM", 0.0008);
        return new BodyDesign("seaplane fuselage", settings(BodySettingsDesign.Purpose.AIRCRAFT, "noseBluntness", 0.3), m);
    }

    /**
     * A float for a 2.6 t seaplane, 7.6 m, chine beam 1.1 m, deadrise 25 deg: an elliptical bow, the forebody, a 5 cm
     * step, the afterbody whose keel rises 7 degrees to the stern post; aluminium.
     */
    public static BodyDesign seaplaneFloat() {
        List<BodyModuleDesign> m = new ArrayList<>();
        EngineMaterial alu = EngineMaterial.ALUMINIUM_2024;
        BodyStructure.Build semi = BodyStructure.Build.SEMI_MONOCOQUE;
        module(m, BodyModuleDesign.Kind.BOW, "bow", "lengthM", 1.6, "widthM", 1.1, "heightM", 0.85, "bottomM", 0.0, "deadriseDeg", 30, "flareDeg", 5,
                "build", semi, "material", alu, "skinM", 0.0016, "stringerShare", 0.3, "frameSpacingM", 0.35, "frameAreaM2", 0.8e-4, "stiffenerSpacingM", 0.08);
        module(m, BodyModuleDesign.Kind.FOREBODY, "forebody", "lengthM", 2.4, "deadriseDeg", 25, "build", semi, "material", alu, "skinM", 0.0016,
                "stringerShare", 0.3, "frameSpacingM", 0.35, "frameAreaM2", 0.8e-4, "stiffenerSpacingM", 0.08);
        module(m, BodyModuleDesign.Kind.AFTERBODY, "afterbody", "lengthM", 3.6, "widthM", 0.55, "heightM", 0.4, "bottomM", 0.5, "deadriseDeg", 22,
                "stepM", 0.05, "build", semi, "material", alu, "skinM", 0.0014, "stringerShare", 0.3, "frameSpacingM", 0.35, "frameAreaM2", 0.6e-4,
                "stiffenerSpacingM", 0.08);
        return new BodyDesign("seaplane float", settings(BodySettingsDesign.Purpose.BOAT, "noseBluntness", 0.05, "hydro", BodyPart.Hydro.PLANING,
                "loadFactor", 3.0, "designSpeedMps", 28.0, "category", BodySettingsDesign.Category.D, "supportedKg", 1250.0), m);
    }

    /**
     * A UH-60-class fuselage, 15.3 m with its tail boom, 2.36 m wide: the nose and cockpit, an eleven-seat cabin with a
     * door each side under the rotor mast, the aft fuselage, and the tail boom carrying the tail rotor.
     */
    public static BodyDesign helicopterFuselage() {
        List<BodyModuleDesign> m = new ArrayList<>();
        module(m, BodyModuleDesign.Kind.NOSE, "nose", "lengthM", 1.4, "widthM", 1.8, "heightM", 1.6, "bottomM", 0.2, "fullness", 2.2, "skinM", 0.0012);
        module(m, BodyModuleDesign.Kind.COCKPIT, "cockpit", "lengthM", 1.9, "widthM", 2.3, "heightM", 1.9, "bottomM", 0.0, "seats", 2, "seatsAbreast", 2,
                "seatPitchM", 1.4, "doors", 2, "doorWidthM", 1.0, "doorHeightM", 1.3, "windowShare", 0.4, "skinM", 0.0012);
        module(m, BodyModuleDesign.Kind.CABIN, "cabin", "lengthM", 3.2, "widthM", 2.36, "heightM", 2.0, "seats", 11, "seatsAbreast", 4, "seatPitchM", 0.8,
                "doors", 2, "doorWidthM", 1.75, "doorHeightM", 1.35, "windowShare", 0.1, "mast", true, "skinM", 0.0014, "floorKgM2", 18);
        module(m, BodyModuleDesign.Kind.CARGO, "aft fuselage", "lengthM", 2.3, "widthM", 1.4, "heightM", 1.4, "bottomM", 0.6, "cargoM3", 1.2, "doors", 0,
                "fuelM3", 1.4, "skinM", 0.0012);
        module(m, BodyModuleDesign.Kind.TAIL_BOOM, "tail boom", "lengthM", 6.5, "widthM", 0.6, "heightM", 0.7, "bottomM", 1.2, "fullness", 1, "skinM", 0.0016);
        return new BodyDesign("helicopter fuselage", settings(BodySettingsDesign.Purpose.AIRCRAFT, "noseBluntness", 0.35, "loadFactor", 3.5,
                "extraDragAreaM2", 1.2), m);
    }

    /**
     * A 6.3 m planing runabout of glass fibre: bow, an open cockpit (sides 0.55 m above the keel), the stern with its
     * transom; it carries a 300 kg sterndrive engine and drive near the transom (the engine is its own part in a boat).
     */
    public static BodyDesign runabout() {
        List<BodyModuleDesign> m = new ArrayList<>();
        module(m, BodyModuleDesign.Kind.BOW, "bow", "lengthM", 1.8, "widthM", 2.0, "heightM", 1.0, "deadriseDeg", 26, "skinM", 0.008);
        module(m, BodyModuleDesign.Kind.FOREBODY, "cockpit", "lengthM", 2.8, "deadriseDeg", 21, "seats", 4, "seatsAbreast", 2, "seatPitchM", 1.0,
                "open", true, "sillM", 0.75, "fuelM3", 0.15, "skinM", 0.008);
        module(m, BodyModuleDesign.Kind.STERN, "stern", "lengthM", 1.7, "deadriseDeg", 19, "skinM", 0.008);
        return new BodyDesign("runabout hull", settings(BodySettingsDesign.Purpose.BOAT, "noseBluntness", 0.05, "designSpeedMps", 18.0,
                "supportedKg", 300.0, "supportedAt", 0.08), m);
    }

    /**
     * A family car's body, a notchback 4.85 m long, 1.82 m wide, 1.45 m high: the hood with the front axle, the windshield's rise,
     * the four-door cabin under its flat roof, the rear window sloping about 25 degrees down to the trunk's deck, the trunk with the rear axle.
     */
    public static BodyDesign sedan() {
        List<BodyModuleDesign> m = new ArrayList<>();
        module(m, BodyModuleDesign.Kind.HOOD, "hood", "lengthM", 1.0, "widthM", 1.8, "heightM", 0.78, "bottomM", 0.15, "beltFraction", 1.0,
                "roofWidthRatio", 0.9, "cornerRadiusM", 0.22, "fullness", 1.8);
        module(m, BodyModuleDesign.Kind.PASSENGER_CELL, "windshield", "lengthM", 0.8, "widthM", 1.82, "heightM", 1.3, "bottomM", 0.15, "beltFraction", 0.6,
                "roofWidthRatio", 0.72, "cornerRadiusM", 0.15, "fullness", 1.4, "windowShare", 0.35, "seats", 0, "doors", 0);
        module(m, BodyModuleDesign.Kind.PASSENGER_CELL, "cabin", "lengthM", 1.65, "widthM", 1.82, "heightM", 1.3, "bottomM", 0.15, "beltFraction", 0.6,
                "roofWidthRatio", 0.72, "cornerRadiusM", 0.15, "fullness", 1.0, "seats", 5, "seatsAbreast", 3, "seatPitchM", 0.8, "doors", 4,
                "doorWidthM", 0.8, "doorHeightM", 1.05, "windowShare", 0.35, "fuelM3", 0.05);
        module(m, BodyModuleDesign.Kind.PASSENGER_CELL, "rear window", "lengthM", 0.6, "widthM", 1.78, "heightM", 0.95, "bottomM", 0.18, "beltFraction", 0.95,
                "roofWidthRatio", 0.85, "cornerRadiusM", 0.13, "fullness", 1.0, "seats", 0, "doors", 0);
        module(m, BodyModuleDesign.Kind.TRUNK, "trunk", "lengthM", 0.8, "widthM", 1.74, "heightM", 0.9, "bottomM", 0.2, "beltFraction", 1.0,
                "roofWidthRatio", 0.85, "cornerRadiusM", 0.2, "fullness", 1.0, "cargoM3", 0.45);
        return new BodyDesign("sedan body", settings(BodySettingsDesign.Purpose.CAR, "noseBluntness", 0.55), m);
    }

    /**
     * 0.18: a small square hatchback's body (the user's Panda-like reference), 3.6 m long, 1.5 m wide, 1.45 m high: a
     * short hood whose front is nearly its full section (bluntness 0.95), so its front end comes out square (square
     * lamps flush with a grille across the front, a black bumper: FrontLayout's automatic choice), an upright windshield,
     * a two-door cabin for four, a steep hatch window, a short tail with the rear axle.
     */
    public static BodyDesign hatchback() {
        List<BodyModuleDesign> m = new ArrayList<>();
        module(m, BodyModuleDesign.Kind.HOOD, "hood", "lengthM", 0.7, "widthM", 1.48, "heightM", 0.78, "bottomM", 0.17, "beltFraction", 1.0,
                "roofWidthRatio", 0.94, "cornerRadiusM", 0.08, "fullness", 1.2);
        module(m, BodyModuleDesign.Kind.PASSENGER_CELL, "windshield", "lengthM", 0.55, "widthM", 1.5, "heightM", 1.28, "bottomM", 0.17, "beltFraction", 0.62,
                "roofWidthRatio", 0.86, "cornerRadiusM", 0.08, "fullness", 1.2, "windowShare", 0.4, "seats", 0, "doors", 0);
        module(m, BodyModuleDesign.Kind.PASSENGER_CELL, "cabin", "lengthM", 1.6, "widthM", 1.5, "heightM", 1.3, "bottomM", 0.17, "beltFraction", 0.62,
                "roofWidthRatio", 0.86, "cornerRadiusM", 0.08, "fullness", 1.0, "seats", 4, "seatsAbreast", 2, "seatPitchM", 0.8, "doors", 2,
                "doorWidthM", 1.0, "doorHeightM", 1.05, "windowShare", 0.4, "fuelM3", 0.035);
        module(m, BodyModuleDesign.Kind.PASSENGER_CELL, "hatch", "lengthM", 0.45, "widthM", 1.48, "heightM", 1.24, "bottomM", 0.2, "beltFraction", 0.66,
                "roofWidthRatio", 0.86, "cornerRadiusM", 0.08, "fullness", 1.0, "seats", 0, "doors", 0);
        module(m, BodyModuleDesign.Kind.TRUNK, "tail", "lengthM", 0.3, "widthM", 1.46, "heightM", 0.95, "bottomM", 0.22, "beltFraction", 1.0,
                "roofWidthRatio", 0.9, "cornerRadiusM", 0.08, "fullness", 1.0, "cargoM3", 0.1);
        return new BodyDesign("hatchback body", settings(BodySettingsDesign.Purpose.CAR, "noseBluntness", 0.95), m);
    }

    /**
     * 0.18: a tractor's cab-over sleeper cab (the user's Scania-like reference), 2.25 m long, 2.45 m wide, 2.6 m tall
     * over its floor: one CAB_OVER module whose front is all but its full section (bluntness 0.97), so FrontLayout gives
     * it the windscreen, the grille panel, a black stepped bumper with lamps in its corners and steps under the doors.
     * It rides on a chassis's cab mount (onFrame); the front axle a third back under it, the engine under its floor.
     */
    public static BodyDesign cabOver() {
        List<BodyModuleDesign> m = new ArrayList<>();
        module(m, BodyModuleDesign.Kind.CAB_OVER, "cab", "lengthM", 2.25, "widthM", 2.45, "heightM", 2.6, "bottomM", 0.35, "beltFraction", 0.42,
                "roofWidthRatio", 0.94, "cornerRadiusM", 0.15, "fullness", 1.0, "seats", 2, "seatsAbreast", 2, "seatPitchM", 0.9, "doors", 2,
                "doorWidthM", 1.0, "doorHeightM", 1.35, "windowShare", 0.3, "axleAt", 0.32);
        return new BodyDesign("cab-over cab", settings(BodySettingsDesign.Purpose.CAR, "noseBluntness", 0.97, "onFrame", true, "paintColor", "#1F3A8A",
                "accentColor", "#1F3A8A", "lowerColor", "#1D1F22"), m);
    }

    /**
     * 0.18: a gooseneck low-loader's decks (the user's reference), 13.5 m on its trailer chassis: the neck deck over the
     * kingpin, a drop to the well deck between the frame's rails, a rise to the rear deck over the three axles. DECK
     * modules: flat, no glass, no seats; the tail lamps on its rear as any car body's.
     */
    public static BodyDesign lowLoaderDeck() {
        List<BodyModuleDesign> m = new ArrayList<>();
        module(m, BodyModuleDesign.Kind.DECK, "neck", "lengthM", 3.0, "widthM", 2.5, "heightM", 0.25, "bottomM", 0.85);
        module(m, BodyModuleDesign.Kind.DECK, "drop", "lengthM", 0.5, "widthM", 2.5, "heightM", 0.25, "bottomM", 0.05);
        module(m, BodyModuleDesign.Kind.DECK, "well", "lengthM", 6.5, "widthM", 2.5, "heightM", 0.2, "bottomM", 0.0);
        module(m, BodyModuleDesign.Kind.DECK, "rise", "lengthM", 0.4, "widthM", 2.5, "heightM", 0.22, "bottomM", 0.2);
        module(m, BodyModuleDesign.Kind.DECK, "rear deck", "lengthM", 3.1, "widthM", 2.5, "heightM", 0.25, "bottomM", 0.2);
        return new BodyDesign("low-loader decks", settings(BodySettingsDesign.Purpose.CAR, "noseBluntness", 0.95, "onFrame", true, "paintColor", "#2B2D31",
                "accentColor", "#2B2D31", "lowerColor", "#1D1F22"), m);
    }

    /**
     * A small flying boat's hull, 10 m: a boat's bow and stepped bottom under an aircraft's cabin (the wing above it),
     * then an upswept tail cone. One body of both families.
     */
    public static BodyDesign flyingBoat() {
        List<BodyModuleDesign> m = new ArrayList<>();
        EngineMaterial alu = EngineMaterial.ALUMINIUM_2024;
        module(m, BodyModuleDesign.Kind.BOW, "bow", "lengthM", 1.8, "widthM", 1.5, "heightM", 1.8, "deadriseDeg", 28, "flareDeg", 8, "material", alu,
                "skinM", 0.0016, "build", BodyStructure.Build.SEMI_MONOCOQUE, "stringerShare", 0.3, "frameSpacingM", 0.4, "frameAreaM2", 1.0e-4, "stiffenerSpacingM", 0.1);
        module(m, BodyModuleDesign.Kind.FOREBODY, "cabin", "lengthM", 3.4, "deadriseDeg", 22, "material", alu, "skinM", 0.0016,
                "build", BodyStructure.Build.SEMI_MONOCOQUE, "stringerShare", 0.3, "frameSpacingM", 0.4, "frameAreaM2", 1.0e-4, "stiffenerSpacingM", 0.1, "seats", 4, "seatsAbreast", 2,
                "seatPitchM", 1.0, "doors", 1, "doorWidthM", 0.9, "doorHeightM", 1.1, "windowShare", 0.25, "wing", BodyModuleDesign.WingMount.HIGH, "floorKgM2", 8);
        module(m, BodyModuleDesign.Kind.AFTERBODY, "afterbody", "lengthM", 2.2, "heightM", 1.6, "bottomM", 0.25, "deadriseDeg", 20, "stepM", 0.07,
                "material", alu, "skinM", 0.0012, "build", BodyStructure.Build.SEMI_MONOCOQUE, "stringerShare", 0.3, "frameSpacingM", 0.4, "frameAreaM2", 0.8e-4, "stiffenerSpacingM", 0.1);
        module(m, BodyModuleDesign.Kind.TAIL_CONE, "tail cone", "lengthM", 2.6, "section", BodyModuleDesign.Section.ROUNDED, "widthM", 0.35, "heightM", 0.5,
                "bottomM", 1.3, "fullness", 1.3, "skinM", 0.0008);
        return new BodyDesign("flying boat hull", settings(BodySettingsDesign.Purpose.AIRCRAFT, "noseBluntness", 0.08, "hydro", BodyPart.Hydro.PLANING,
                "designSpeedMps", 25.0), m);
    }
}
