package dev.mcmod.hdfv.damage;

import dev.mcmod.aerocore.api.vehicle.part.MotorPart;
import dev.mcmod.aerocore.api.vehicle.part.TransferPart;
import dev.mcmod.aerocore.api.vehicle.part.TransmissionPart;
import dev.mcmod.aerocore.api.wheel.Tyre;
import dev.mcmod.aerocore.api.wheel.WheelCorner;
import dev.mcmod.hdfv.part.MotorDesign;
import dev.mcmod.hdfv.part.drive.ClutchDesign;
import dev.mcmod.hdfv.part.drive.GearboxDesign;
import dev.mcmod.hdfv.part.drive.TransferCaseDesign;
import dev.mcmod.hdfv.part.wheel.BrakeDesign;
import dev.mcmod.hdfv.part.wheel.TyreDesign;

/**
 * 0.25: what wears AeroCore's runtime parts (their Wearing records), worked out from hdfv's designs when they are
 * compiled: no new parameters, so designs, blueprints and workpieces are as before. Volumes and lives are real figures
 * divided by the game's pace (DamagePace.paced: 72 times faster at the defaults, nothing at all when wear is off).
 *
 * <ul>
 * <li>Tyres: the tread rubber from the tyre's size (AeroCore's helper) at a usable depth and land ratio by its tread.
 *     Tyres run on a sixth of the pace (TYRE_PACE: 12 times real at the defaults, not 72): a player on a keyboard
 *     brakes and launches flat out every time, and at the full pace a set would last some 70 km of that; so it lasts
 *     about 400 km of launch, cruise and hard stop, and thousands of km of steady cruising (WearPaceTest). The rubber
 *     a J of gentle slip takes is AeroCore's typical figure, paced; what hard sliding adds on top (a locked wheel, a
 *     burnout: AeroCore's severity, 1 + (v / v0)^2) is not paced again: the reference speed is raised by the square
 *     root of the tyres' pace, so that part of the wear runs in real time, as fast as it really is: a burnout takes a
 *     pair of tyres in well under a minute, as AeroCore's typical numbers have a real one do. Slower is not to be had
 *     together with a life of a few hundred km: the formula's wear per J only rises with the sliding speed.</li>
 * <li>Brake linings: no pad is designed, so its size follows the rotor's: a disc's two pads each 0.068 D^2 (0.17 D
 *     high over an arc of 0.4 D: 53 cm^2 on a 280 mm disc) with 8 mm to wear (10 to 12 mm new, changed at 2 to 3), a
 *     drum's two shoes each 0.17 D^2 (110 degrees of arc, 0.18 D wide) with 4 mm.</li>
 * <li>Suspension: a wheel's bump stop is rated at four times the corner's design load (with the spring's share about
 *     the 5 g abuse case suspensions take without a permanent set); beyond it AeroCore's rule damages it. Not paced:
 *     it is damage. A car dropped from half a metre keeps most of its suspension; from a metre and more it does not.</li>
 * <li>Gears: GEAR_LIFE_H at the box's rated torque and RATED_INPUT_RAD_S (car boxes are sized for a few hundred hours at
 *     full engine torque; with ISO 281's cube law that is many thousand at a cruise's third of it). Friction facings:
 *     the clutch's faces (the annulus between its diameters) with FACING_WEAR_M to wear.</li>
 * <li>Electric motors: MOTOR_LIFE_H (bearing life of traction and industrial motors) at the speed where the power
 *     limit begins and half the peak torque (about a motor's continuous rating).</li>
 * </ul>
 */
public final class Wearings {
    private Wearings() {}

    /** Usable tread depth, m: a road tyre's 8 mm new to the 1.6 mm limit; a slick's 4 mm of rubber; an off-road tyre's deeper blocks. */
    public static final double ROAD_TREAD_M = 0.0064, SLICK_TREAD_M = 0.004, BLOCK_TREAD_M = 0.010;
    /** The share of the tread that is rubber, not groove. */
    public static final double ROAD_LAND = 0.7, SLICK_LAND = 1.0, BLOCK_LAND = 0.6;
    /** The share of the game's pace tyres wear at (see the class comment). */
    public static final double TYRE_PACE = 1.0 / 6;
    /** A disc pad's and a drum shoe's area over the rotor diameter squared, and the lining to wear, m. */
    public static final double PAD_AREA = 0.068, SHOE_AREA = 0.17, PAD_WEAR_M = 0.008, SHOE_WEAR_M = 0.004;
    /** A wheel's bump stop rating over its design load. */
    public static final double STOP_RATING = 4;
    /** Hours a gearbox's gears last at its rated torque and RATED_INPUT_RAD_S (3000 rpm). */
    public static final double GEAR_LIFE_H = 200, RATED_INPUT_RAD_S = 3000 * Math.PI / 30;
    /** Facing to wear on each friction face, m (a dry disc's 3.5 mm facing down to its rivets). */
    public static final double FACING_WEAR_M = 0.0015;
    /** Hours an electric motor lasts at its continuous rating, and that rating's torque over the peak. */
    public static final double MOTOR_LIFE_H = 15000, MOTOR_CONTINUOUS = 0.5;

    /** One tyre's usable tread rubber, m^3 (real, not paced). */
    public static double treadVolumeM3(TyreDesign tyre) {
        Tyre t = tyre.tyre();
        return switch (tyre.tread()) {
            case SLICK -> WheelCorner.Wearing.treadVolumeM3(t, SLICK_TREAD_M, SLICK_LAND);
            case ALL_TERRAIN, MUD_TERRAIN -> WheelCorner.Wearing.treadVolumeM3(t, BLOCK_TREAD_M, BLOCK_LAND);
            default -> WheelCorner.Wearing.treadVolumeM3(t, ROAD_TREAD_M, ROAD_LAND);   // road, performance, winter, an aircraft's ribs
        };
    }

    /** One brake's usable lining, m^3 (real; a little for a wheel without a brake, which never wears it). */
    public static double liningVolumeM3(BrakeDesign brake) {
        double d = brake.rotorDiameterM();
        if (brake.kind() == BrakeDesign.Kind.NONE || !(d > 0)) { return 1e-5; }
        return brake.kind().drum() ? WheelCorner.Wearing.liningVolumeM3(SHOE_AREA * d * d, SHOE_WEAR_M, 2) : WheelCorner.Wearing.liningVolumeM3(PAD_AREA * d * d, PAD_WEAR_M, 2);
    }

    /** What wears a part's wheels: so many tyres and brakes of these designs, each corner made for a load (kg). */
    public static WheelCorner.Wearing wheels(TyreDesign tyre, BrakeDesign brake, int wheels, double designLoadKg) {
        double tyrePace = TYRE_PACE * DamagePace.wear();
        double tread = tyrePace > 0 ? wheels * treadVolumeM3(tyre) / tyrePace : Double.POSITIVE_INFINITY, lining = DamagePace.paced(wheels * liningVolumeM3(brake));
        WheelCorner.Wearing typical = WheelCorner.Wearing.typical(tread, lining, STOP_RATING * Math.max(designLoadKg, 1) * 9.80665);
        // hard sliding's extra wear in real time: v0 raised by sqrt(pace) (see the class comment)
        double reference = typical.slideReferenceMps() * Math.sqrt(Math.max(tyrePace, 1));
        return new WheelCorner.Wearing(typical.treadVolumeM3(), typical.abradabilityM3PerJ(), reference, typical.liningVolumeM3(), typical.liningWearM3PerJ(),
                typical.liningKneeK(), typical.liningDoublingK(), typical.stopRatedN(), typical.stopLifeS());
    }

    /** A clutch's or a pack of shift elements' facing to wear, m^3 (real). */
    public static double facingVolumeM3(ClutchDesign clutch) {
        double area = Math.PI / 4 * Math.max(clutch.outerDiameterM() * clutch.outerDiameterM() - clutch.innerDiameterM() * clutch.innerDiameterM(), 1e-4);
        return Math.max(clutch.faces(), 1) * area * FACING_WEAR_M;
    }

    /** What wears a gearbox. */
    public static TransmissionPart.Wearing gearbox(GearboxDesign design) {
        return TransmissionPart.Wearing.typical(DamagePace.paced(GEAR_LIFE_H * 3600), Math.max(design.gearbox().torqueRatingNm(), 1), RATED_INPUT_RAD_S,
                DamagePace.paced(facingVolumeM3(design.clutch())));
    }

    /** What wears a transfer case. */
    public static TransferPart.Wearing transfer(TransferCaseDesign design) {
        return TransferPart.Wearing.typical(DamagePace.paced(GEAR_LIFE_H * 3600), Math.max(design.torqueRatingNm(), 1), RATED_INPUT_RAD_S);
    }

    /** What wears an electric motor. */
    public static MotorPart.Wearing motor(MotorDesign design) {
        double base = design.maxPowerW() / Math.max(design.maxTorqueNm(), 1e-6);
        return MotorPart.Wearing.typical(DamagePace.paced(MOTOR_LIFE_H * 3600), Math.max(base, 1), Math.max(MOTOR_CONTINUOUS * design.maxTorqueNm(), 1e-6));
    }
}
