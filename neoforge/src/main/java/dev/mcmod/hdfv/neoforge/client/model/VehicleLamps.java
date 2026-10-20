package dev.mcmod.hdfv.neoforge.client.model;

import dev.mcmod.aerocore.api.Orientation;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.mesh.Finish;
import dev.mcmod.aerocore.api.mesh.Lamp;
import dev.mcmod.aerocore.api.mesh.Mesh;
import dev.mcmod.aerocore.api.mesh.MeshBuilder;
import dev.mcmod.aerocore.api.mesh.MeshOps;
import dev.mcmod.aerocore.neoforge.client.PartMesh;
import dev.mcmod.hdfv.look.Finishes;
import dev.mcmod.hdfv.look.LampRules;

/**
 * Lamps on a part's mesh (0.12): a lamp from the vehicle kit placed on the surface, its lens (the quads of a lamp
 * finish) tagged with the lamp's number and coloured, and AeroCore's Lamp added with its role (LampRules.Role's
 * ordinal), so the renderer's rules light it. Numbers run from the mesh's lamps so far.
 */
public final class VehicleLamps {
    private VehicleLamps() {}

    /** Colours: a headlight's warm white, a tail light's red, a reversing light's white, navigation red, green, white, a beacon's red. */
    public static final int HEAD = dev.mcmod.hdfv.look.LampLayout.HEAD, RED = dev.mcmod.hdfv.look.LampLayout.RED, WHITE = dev.mcmod.hdfv.look.LampLayout.WHITE,
            NAV_RED = dev.mcmod.hdfv.look.LampLayout.NAV_RED, NAV_GREEN = dev.mcmod.hdfv.look.LampLayout.NAV_GREEN, AMBER = dev.mcmod.hdfv.look.LampLayout.AMBER;

    /** The tags that make a quad lamp id's lens. */
    static int lensTags(int tags, int id) { return (tags & ~(MeshBuilder.LAMP | MeshBuilder.LAMP_ID)) | MeshBuilder.LAMP | (id << 12); }

    /** The next free lamp number on a mesh. */
    public static int next(PartMesh out) { return out.lamps().size(); }

    /**
     * A kit lamp standing on the surface at a point (its +y out along up, its length along along, its width the size
     * of the kit's reference), its lens quads of one finish made lamp id's (and coloured), its other quads as they are.
     * Returns the placed mesh's lens quads' middle (where the lamp's light comes from).
     */
    public static Vec3d place(PartMesh out, DetailKit.Part part, Vec3d at, Vec3d up, Vec3d along, double size, Finish lens, int id, int colour, Finish recolour) {
        double k = size / part.size;
        Mesh m = MeshOps.placed(DetailKit.mesh(part, true), new Vec3d(k, k, k), Details.basis(up, along), at);
        Vec3d sum = Vec3d.ZERO;
        int n = 0, tags = out.tags();
        for (MeshBuilder.Quad q : m.quads()) {
            if (q.finish() == lens) {
                MeshBuilder.Quad lit = q.withColor(colour).withFinish(recolour == null ? q.finish() : recolour);
                out.quads().add(lit.withTags(lensTags(tags, id)));
                sum = sum.add(q.a().add(q.b()).add(q.c()).add(q.d()).scale(0.25));
                n++;
            } else {
                out.quads().add(q.withTags(tags));
            }
        }
        return n == 0 ? at : sum.scale(1.0 / n);
    }

    /** A second lens on a placed lamp: the quads of another finish among those just added, from a list index on, made lamp id's. */
    public static Vec3d retag(PartMesh out, int from, Finish lens, int id, int colour) {
        Vec3d sum = Vec3d.ZERO;
        int n = 0;
        for (int i = from; i < out.quads().size(); i++) {
            MeshBuilder.Quad q = out.quads().get(i);
            if (q.finish() == lens && q.lamp() < 0) {
                out.quads().set(i, q.withColor(colour).withTags(lensTags(q.tags(), id)));
                sum = sum.add(q.a().add(q.b()).add(q.c()).add(q.d()).scale(0.25));
                n++;
            }
        }
        return n == 0 ? null : sum.scale(1.0 / n);
    }

    public static void point(PartMesh out, int id, Vec3d at, Vec3d axis, double radiusM, int argb, double sectorDeg, LampRules.Role role) {
        out.addLamp(Lamp.point(id, 0, at, axis, radiusM, argb, sectorDeg, role.ordinal()));
    }

    public static void beam(PartMesh out, int id, Vec3d at, Vec3d axis, double radiusM, int argb, double beamDeg, double rangeM, LampRules.Role role) {
        out.addLamp(Lamp.beam(id, 0, at, axis, radiusM, argb, beamDeg, rangeM, role.ordinal()));
    }

    /**
     * What a vehicle's lamps go by now: its "lights", "brake" and "selector" channels, whether anything turns fast (an
     * engine running: a rotor over 20 rad/s), the game's time and its id's hash (the flashes' phase).
     */
    public static LampRules.Inputs inputs(dev.mcmod.aerocore.neoforge.vehicle.PartVehicle e, float partialTick) {
        var c = e.controls();
        boolean running = false;
        if (e.vehicle() != null) {
            for (int i = 0; i < e.vehicle().rotors().size() && !running; i++) { running = Math.abs(e.rotorSpeed(i)) > 20; }
        }
        return new LampRules.Inputs(c.getOrDefault("lights", 0.0), c.getOrDefault("brake", 0.0), c.getOrDefault("selector", 0.0), running,
                e.level().getGameTime() + partialTick, e.getUUID().hashCode(), c.getOrDefault("indicators", 0.0));
    }

    /** Each lamp's level by its role's rule. */
    public static void levels(java.util.List<Lamp> lamps, float[] level, LampRules.Inputs in) {
        LampRules.Role[] roles = LampRules.Role.values();
        for (int i = 0; i < lamps.size() && i < level.length; i++) {
            int r = lamps.get(i).role();
            level[i] = r >= 0 && r < roles.length ? LampRules.level(roles[r], in) : 0;
        }
    }

    /** A direction in the horizontal plane turned from forward (+x) toward a side (-1 port, +1 starboard) by an angle. */
    public static Vec3d bearing(double degrees, int side) {
        double a = Math.toRadians(degrees);
        return new Vec3d(Math.cos(a), 0, side * Math.sin(a));
    }

    /**
     * Navigation lights at an aircraft's wingtip (a side: -1 port red, +1 starboard green, each 110 degrees from
     * dead ahead) with a white strobe beside it: the kit's light on the tip facing outboard.
     */
    public static void wingtip(PartMesh out, Vec3d tip, int side) {
        int nav = next(out);
        Vec3d at = place(out, DetailKit.Part.NAV_LIGHT, tip, new Vec3d(0, 0, side), Vec3d.X, 0.12, Finishes.LAMP_CLEAR, nav, side < 0 ? NAV_RED : NAV_GREEN,
                side < 0 ? Finishes.LAMP_RED : Finishes.LAMP_CLEAR);
        point(out, nav, at, bearing(55, side), 0.05, side < 0 ? NAV_RED : NAV_GREEN, 110, side < 0 ? LampRules.Role.NAV_RED : LampRules.Role.NAV_GREEN);
        int strobe = next(out);
        point(out, strobe, at.add(new Vec3d(-0.05, 0, 0)), new Vec3d(0, 0, side), 0.07, WHITE, 360, LampRules.Role.STROBE);
    }
}
