package dev.mcmod.hdfv.manufacture;

import java.util.Locale;

/**
 * What a cutting step makes, where it decides which specialised machine can make it (0.17; the user 2026-10-18: "加入所有
 * 機加工機器", the routes made realistic while the old machines keep what they could do).
 *
 * A step that 0.16 already had keeps its process, grade, axes and faces and only gains a feature: the machines of 0.16
 * ignore features, so whatever could do the step still can, at the same grade and time; the new heads take a step only
 * when its feature is one they make (a hob takes MILL with GEAR_TEETH, a honing head GRIND with BORE).
 *
 * <ul>
 * <li>BORE: a bore's finish (a cylinder's, a pin's, a rod end's): honing.</li>
 * <li>GEAR_TEETH: gear teeth, cut (hobbed, shaped) or finished (shaved, ground) or chamfered.</li>
 * <li>SPLINE: splines, racks and keyways (broached, slotted, shaped, hobbed when outside).</li>
 * <li>FIR_TREE: a turbine or compressor disc's blade slots (broached, wire- or sink-EDM'd).</li>
 * <li>ORIFICE: holes of a few tenths of a millimetre to a tight grade (an injector's): EDM drilling.</li>
 * <li>COOLING_HOLE: film-cooling and cooling holes (combustors, liners, turbine blades): laser or EDM drilling.</li>
 * <li>AEROFOIL: blade and impeller surfaces (milled, electrochemically machined, ground).</li>
 * </ul>
 */
public enum Feature {
    NONE, BORE, GEAR_TEETH, SPLINE, FIR_TREE, ORIFICE, COOLING_HOLE, AEROFOIL;

    /** Its id in translation keys ("gear_teeth"). */
    public String id() { return name().toLowerCase(Locale.ROOT); }
}
