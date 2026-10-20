package dev.mcmod.hdfv.part.truck;

import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.vehicle.Coupling;
import dev.mcmod.aerocore.api.vehicle.DesignException;
import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;
import dev.mcmod.aerocore.api.vehicle.MassProperties;
import dev.mcmod.aerocore.api.vehicle.Mount;
import dev.mcmod.aerocore.api.vehicle.Part;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.part.FramePart;
import dev.mcmod.hdfv.part.air.Components;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A fifth wheel (hdfv:fifth_wheel, 0.18, 第五輪): the coupling plate on a tractor's frame (a chassis's "fifth_wheel"
 * point) that a semi-trailer's kingpin locks into. Its jaw ("jaw", heightM over its base, the plate's centre) is the
 * coupling's point on the tractor: the plate pitches about its hinge up to pitchLimitDeg, holds the trailer's roll
 * (rollLimitDeg 0) or lets it roll within the limit (an oscillating plate, for rough ground), and the trailer swings
 * about the kingpin as far as the two vehicles' own shapes allow (drive.Hitch works out the jackknife).
 * <p>
 * Its rating: the vertical load it carries (kg) and the D value (kN, ISO 3842 / UN R55: the horizontal force rating,
 * D = g T R / (T + R - U) for a tractor's and a trailer's gross masses T and R and the load on it U, in tonnes); a 2"
 * kingpin takes up to 162 kN, a 3.5" one more. Its mass: 40 kg of base and bracket and 0.9 kg per kN of D on a 0.9 m
 * plate (a 2" plate of 152 kN weighs 140-180 kg).
 */
public record FifthWheelDesign(String name, double plateDiameterM, double heightM, double pitchLimitDeg, double rollLimitDeg, Kingpin kingpin,
                               double ratedLoadKg, double dValueKn, double captureM) implements PartDesign {
    public static final String TYPE = "hdfv:fifth_wheel";
    /** The most D a 2" kingpin takes (ISO 337), kN. */
    public static final double TWO_INCH_MAX_D_KN = 162;

    /** Kingpin sizes (ISO 337 2", ISO 4086 3.5"). */
    public enum Kingpin {
        TWO_INCH(0.0508), THREE_AND_HALF_INCH(0.0889);

        public final double diameterM;

        Kingpin(double diameterM) { this.diameterM = diameterM; }
    }

    public FifthWheelDesign {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(kingpin, "kingpin");
    }

    @Override public String type() { return TYPE; }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        if (!(plateDiameterM >= 0.6 && plateDiameterM <= 1.2)) { issues.add(DesignIssue.invalid("plateDiameterM", "A fifth wheel's plate is 0.6 to 1.2 m across")); }
        if (!(heightM >= 0.1 && heightM <= 0.4)) { issues.add(DesignIssue.invalid("heightM", "A fifth wheel stands 0.1 to 0.4 m over its base")); }
        if (!(pitchLimitDeg >= 3 && pitchLimitDeg <= 20)) { issues.add(DesignIssue.invalid("pitchLimitDeg", "The plate pitches 3 to 20 degrees either way")); }
        if (!(rollLimitDeg >= 0 && rollLimitDeg <= 6)) { issues.add(DesignIssue.invalid("rollLimitDeg", "An oscillating plate rolls at most 6 degrees (0: it holds the roll)")); }
        if (!(ratedLoadKg >= 1000 && ratedLoadKg <= 40000)) { issues.add(DesignIssue.invalid("ratedLoadKg", "A fifth wheel carries 1 to 40 t")); }
        if (!(dValueKn >= 20 && dValueKn <= 300)) { issues.add(DesignIssue.invalid("dValueKn", "A D value is 20 to 300 kN")); }
        else if (kingpin == Kingpin.TWO_INCH && dValueKn > TWO_INCH_MAX_D_KN) { issues.add(DesignIssue.invalid("dValueKn", "A 2\" kingpin takes at most 162 kN")); }
        if (!(captureM >= 0.02 && captureM <= 0.5)) { issues.add(DesignIssue.invalid("captureM", "The jaw catches the kingpin 2 to 50 cm away")); }
        return issues;
    }

    public double massKg() { return 40 + 0.9 * dValueKn * Math.pow(plateDiameterM / 0.9, 2); }

    public MassProperties mass() {
        return Components.box(massKg(), new Vec3d(0, 0.5 * heightM, 0), new Vec3d(0.8 * plateDiameterM, heightM, plateDiameterM));
    }

    /** The jaw: the plate's centre, where the trailer's kingpin plate sits, in the part's frame. */
    public Vec3d jaw() { return new Vec3d(0, heightM, 0); }

    @Override public Part compile() {
        List<DesignIssue> issues = check();
        DesignException.throwIfInvalid(issues);
        return new FramePart(this, mass(), Map.of("jaw", Mount.at(jaw())), List.of(), null, Components.extrapolations(issues));
    }

    /**
     * The coupling on this fifth wheel, its jaw at leaderPoint (the tractor's frame) and the trailer's kingpin at
     * followerPoint (the trailer's), the jackknife limit given: the plate's pitch limit, its roll held or with play.
     */
    public Coupling coupling(Vec3d leaderPoint, Vec3d followerPoint, double jackknifeRad) {
        Coupling c = Coupling.fifthWheel(leaderPoint, followerPoint, Math.toRadians(pitchLimitDeg), jackknifeRad).withCapture(captureM);
        return rollLimitDeg > 0 ? c.withRoll(Math.toRadians(rollLimitDeg), 0, 0) : c;
    }

    @Override public Map<String, Object> parameters() {
        return Fields.object("plateDiameterM", plateDiameterM, "heightM", heightM, "pitchLimitDeg", pitchLimitDeg, "rollLimitDeg", rollLimitDeg,
                "kingpin", kingpin.name().toLowerCase(java.util.Locale.ROOT), "ratedLoadKg", ratedLoadKg, "dValueKn", dValueKn, "captureM", captureM);
    }

    public static PartDesign read(Fields f) {
        FifthWheelDesign d = standard();
        return new FifthWheelDesign(f.string("name", d.name), f.number("plateDiameterM", d.plateDiameterM), f.number("heightM", d.heightM),
                f.number("pitchLimitDeg", d.pitchLimitDeg), f.number("rollLimitDeg", d.rollLimitDeg),
                Kingpin.valueOf(f.string("kingpin", "two_inch").toUpperCase(java.util.Locale.ROOT)), f.number("ratedLoadKg", d.ratedLoadKg),
                f.number("dValueKn", d.dValueKn), f.number("captureM", d.captureM));
    }

    /** A standard 2" fifth wheel: a 0.9 m plate 0.2 m high, 12 degrees of pitch, roll held, 20 t, D 152 kN, catching at 15 cm. */
    public static FifthWheelDesign standard() {
        return new FifthWheelDesign("fifth wheel", 0.9, 0.2, 12, 0, Kingpin.TWO_INCH, 20000, 152, Coupling.CAPTURE_M);
    }
}
