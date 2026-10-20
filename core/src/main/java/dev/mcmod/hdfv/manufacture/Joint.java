package dev.mcmod.hdfv.manufacture;

/**
 * How a structure's pieces are joined in an assembly room (0.7), chosen from its build and material: aluminium
 * semi-monocoques are riveted (as aircraft are), steel monocoques spot-welded (as car bodies are), steel and aluminium
 * hulls and space frames MIG-welded, titanium and stainless TIG-welded, composites bonded, wood glued.
 */
public enum Joint {
    RIVET, SPOT_WELD, MIG, TIG, BOND, GLUE;

    /** Whether it is arc or resistance welding (a welding station or a welder does it). */
    public boolean welding() { return this == SPOT_WELD || this == MIG || this == TIG; }
}
