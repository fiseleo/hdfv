package dev.mcmod.hdfv.part.machine;

import dev.mcmod.aerocore.api.CollisionSphere;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.lifting.Reeving;
import dev.mcmod.aerocore.api.lifting.WireRope;
import dev.mcmod.aerocore.api.vehicle.DesignException;
import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;
import dev.mcmod.aerocore.api.vehicle.MassProperties;
import dev.mcmod.aerocore.api.vehicle.Mount;
import dev.mcmod.aerocore.api.vehicle.Part;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.part.HangPart;
import dev.mcmod.hdfv.part.air.Components;
import dev.mcmod.hdfv.part.engine.EngineComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * A crane's hook block (hdfv:hook, 0.26, 吊鉤): a forged hook (single, DIN 15401, or ramshorn, DIN 15402) under a block
 * of sheaves reeved with partsOfLine falls of a six-strand IWRC rope of ropeDiameterM in grade 1960, rated for
 * ratedLoadKg. Its frame: the origin and the mount point "rope" where the falls meet the block (its top), y up; "load"
 * the hook's saddle where a load's sling hangs, the block's height below. In the world it is AeroCore's HangPart, a body
 * of its own hanging on the rope from the trolley or the boom head the game gives it: the rope's E A is the parts of line
 * times WireRope.MODULUS_PA times the rope's metallic area, its damping dampingRatio of critical for the rated load and
 * the block on a REFERENCE_FALL_M fall.
 *
 * <p>The block: the sheaves are the parts of line over two (an odd count dead-ends on the block), each SHEAVE_RATIO
 * rope diameters across (ISO 4308-1 h2 for M4: 18) and a cast disc of SHEAVE_FILL of a solid one 2.5 rope diameters
 * wide; the hook HOOK_KG_PER_T kg per tonne rated (DIN 15401 hooks: about 3.5; a ramshorn RAMSHORN times that), its
 * length HOOK_LENGTH_M x (tonnes)^(1/3); the side plates, crosshead, pins and the weights that overhaul the rope as much
 * again as hook and sheaves (FRAME_SHARE). That puts blocks at 0.5 to 1.5 % of their rating, as hook block catalogues
 * have them (Crosby McKissick, Liebherr: a 40 t three-sheave block some 400 kg).
 *
 * <p>Checks: the rope's working load limit (ISO 4308-1 M4, Z_p 4) over the parts and the tackle's efficiency
 * (Reeving) should carry the rated load and the block (the derived "hookLoadN"): an extrapolation if not; INVALID if
 * the rope's minimum breaking force over the parts does not (it would break at the rating).
 */
public record HookDesign(String name, double ratedLoadKg, int partsOfLine, double ropeDiameterM, HookKind hookKind, double dampingRatio) implements PartDesign {
    public static final String TYPE = "hdfv:hook";

    /** Single (DIN 15401) or ramshorn (DIN 15402, two prongs: heavier, loaded from both sides). */
    public enum HookKind { SINGLE, RAMSHORN }

    public static final WireRope.Construction ROPE = WireRope.Construction.SIX_STRAND_IWRC;
    public static final double GRADE_PA = WireRope.GRADE_1960_PA;
    /** Sheaves: pitch diameter in rope diameters (ISO 4308-1 M4 h2), width in rope diameters, share of a solid disc. */
    public static final double SHEAVE_RATIO = 18, SHEAVE_WIDTH = 2.5, SHEAVE_FILL = 0.35;
    /** The hook: kg per tonne rated, a ramshorn's factor, length (m) per cube root of tonnes; the frame and weights' share. */
    public static final double HOOK_KG_PER_T = 3.5, RAMSHORN = 1.4, HOOK_LENGTH_M = 0.22, FRAME_SHARE = 1.0;
    /** The fall the damping ratio is set at, m. */
    public static final double REFERENCE_FALL_M = 10;

    public HookDesign {
        Objects.requireNonNull(name, "name"); Objects.requireNonNull(hookKind, "hookKind");
    }

    @Override public String type() { return TYPE; }

    public int sheaves() { return partsOfLine / 2; }

    public double sheaveDiameterM() { return SHEAVE_RATIO * ropeDiameterM; }

    public double sheaveKg() {
        double d = sheaveDiameterM();
        return MachineRules.STEEL_KG_PER_M3 * SHEAVE_FILL * Math.PI / 4 * d * d * SHEAVE_WIDTH * ropeDiameterM;
    }

    public double hookKg() { return HOOK_KG_PER_T * ratedLoadKg / 1000 * (hookKind == HookKind.RAMSHORN ? RAMSHORN : 1); }

    public double hookLengthM() { return HOOK_LENGTH_M * Math.cbrt(Math.max(ratedLoadKg / 1000, 0.1)); }

    /** The block's mass, kg. */
    public double massKg() { return (1 + FRAME_SHARE) * (hookKg() + sheaves() * sheaveKg()); }

    /** From its top (the rope point) to the hook's saddle, m. */
    public double heightM() { return (sheaves() > 0 ? 1.2 * sheaveDiameterM() : 0.3 * hookLengthM()) + hookLengthM(); }

    /** The rope's working load limit over the parts and the tackle, N (ISO 4308-1 M4). */
    public double ropeCapacityN() {
        return Reeving.hookLoadN(partsOfLine, WireRope.workingLoadLimitN(ROPE, ropeDiameterM, GRADE_PA, WireRope.Group.M4), Reeving.SHEAVE_ROLLING, 0);
    }

    /** What the rope's minimum breaking force carries over the parts, N. */
    public double ropeBreakingN() {
        return Reeving.hookLoadN(partsOfLine, WireRope.minBreakingForceN(ROPE, ropeDiameterM, GRADE_PA), Reeving.SHEAVE_ROLLING, 0);
    }

    /** The falls' E A together, N. */
    public double stiffnessN() { return partsOfLine * WireRope.MODULUS_PA * WireRope.metalAreaM2(ROPE, ropeDiameterM); }

    /** The rope's damping, N s/m: dampingRatio of critical for the rated load and the block on REFERENCE_FALL_M. */
    public double dampingNs() { return 2 * dampingRatio * Math.sqrt(stiffnessN() / REFERENCE_FALL_M * (ratedLoadKg + massKg())); }

    public MassProperties mass() {
        double h = heightM(), w = Math.max(sheaveDiameterM(), 0.3 * hookLengthM());
        return Components.box(massKg(), new Vec3d(0, -0.45 * h, 0), new Vec3d(SHEAVE_WIDTH * ropeDiameterM * Math.max(sheaves(), 1) + 0.1, h, w));
    }

    public List<CollisionSphere> collision() {
        double h = heightM(), hook = hookLengthM();
        List<CollisionSphere> out = new ArrayList<>();
        if (sheaves() > 0) { out.add(new CollisionSphere(new Vec3d(0, -0.6 * sheaveDiameterM(), 0), 0.6 * sheaveDiameterM())); }
        out.add(new CollisionSphere(new Vec3d(0, -h + 0.35 * hook, 0), 0.3 * hook));
        return out;
    }

    public Map<String, Mount> mountPoints() { return Map.of("rope", Mount.at(Vec3d.ZERO), "load", Mount.at(new Vec3d(0, -heightM(), 0))); }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        EngineComponent.Rules.positive(issues, "ratedLoadKg", ratedLoadKg);
        EngineComponent.Rules.count(issues, "partsOfLine", partsOfLine, 1, 64);
        EngineComponent.Rules.positive(issues, "ropeDiameterM", ropeDiameterM);
        EngineComponent.Rules.nonnegative(issues, "dampingRatio", dampingRatio);
        if (!issues.isEmpty()) { return issues; }
        double load = (ratedLoadKg + massKg()) * MachineRules.G;
        if (ropeBreakingN() < load) { issues.add(DesignIssue.invalid("ropeDiameterM", "The rope over its parts breaks below the rated load")); return issues; }
        DesignIssue.range(issues, "ratedLoadKg", ratedLoadKg, 500, 1.2e6, "hook blocks (Crosby, Liebherr catalogues)");
        DesignIssue.range(issues, "ropeDiameterM", ropeDiameterM, 0.008, 0.06, "EN 12385-4 crane ropes");
        DesignIssue.range(issues, "hookLoadN", load, 0, ropeCapacityN(), "ISO 4308-1 M4: the rope's working load limit over its parts");
        DesignIssue.range(issues, "dampingRatio", dampingRatio, 0.01, 0.2, "steel wire rope's damping");
        return issues;
    }

    @Override public Part compile() {
        List<DesignIssue> issues = check();
        DesignException.throwIfInvalid(issues);
        return new HangPart(this, mass(), collision(), mountPoints(), stiffnessN(), dampingNs(), Components.extrapolations(issues));
    }

    @Override public Map<String, Object> parameters() {
        return Fields.object("ratedLoadKg", ratedLoadKg, "partsOfLine", partsOfLine, "ropeDiameterM", ropeDiameterM, "hookKind", hookKind.name().toLowerCase(Locale.ROOT),
                "dampingRatio", dampingRatio);
    }

    public static PartDesign read(Fields f) {
        HookDesign d = standard();
        return new HookDesign(f.string("name", d.name), f.number("ratedLoadKg", d.ratedLoadKg), f.integer("partsOfLine", d.partsOfLine), f.number("ropeDiameterM", d.ropeDiameterM),
                EngineComponent.Rules.choice(f, "hookKind", HookKind.class, d.hookKind), f.number("dampingRatio", d.dampingRatio));
    }

    /** The standard block: 40 t on six falls of 22 mm (SampleMachinery.craneHook40t). */
    public static HookDesign standard() { return SampleMachinery.craneHook40t(); }
}
