package dev.mcmod.hdfv.manufacture;

/**
 * The work of putting a structure together in an assembly room (0.7), for one piece of it in one material (a
 * module's skin, its frames, a wing's spar caps): its finished mass and the stock delivered for it (kg, in a form),
 * how it is joined, the seams' length, the filler (weld wire, rivets' or adhesive's mass, kg) and fasteners, the
 * labour (s) and any cure that just needs time (s).
 */
public record StructureWork(String key, Alloy alloy, Form form, double finishedKg, double stockKg, Joint joint, double seamM, double fillerKg, int fasteners,
                            double laborS, double cureS) {}
