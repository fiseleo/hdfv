package dev.mcmod.hdfv.manufacture;

import java.util.List;

/**
 * How a part design is made (0.7): its manufactured components (each with its route), where they come together (an
 * assembly bench for engines, gearboxes, axles; an assembly room for bodies, wings and hulls), the assembling time
 * and, for a room-built structure, its joining work.
 */
public record PartPlan(String type, List<Workpiece> workpieces, Station assembly, double assembleS, List<StructureWork> structure) {
    public PartPlan {
        workpieces = List.copyOf(workpieces);
        structure = List.copyOf(structure);
    }

    /** The finished mass of its components and structure, kg. */
    public double finishedKg() {
        double kg = 0;
        for (Workpiece w : workpieces) { kg += w.count() * w.finishedKg(); }
        for (StructureWork s : structure) { kg += s.finishedKg(); }
        return kg;
    }

    /** Whether every component can be made on manual machines. */
    public boolean manual() { return workpieces.stream().allMatch(Workpiece::manual); }

    /** Whether it is built in an assembly room rather than at a bench. */
    public boolean roomBuilt() { return assembly == Station.ASSEMBLY_ROOM; }
}
