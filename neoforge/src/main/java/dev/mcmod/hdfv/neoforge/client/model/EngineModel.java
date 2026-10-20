package dev.mcmod.hdfv.neoforge.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.mcmod.aerocore.api.Orientation;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.neoforge.client.PartMesh;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleFunction;

/**
 * A drawn engine: the parts that stay put, moving pieces, each a mesh built once in its own frame and placed by the
 * crank angle (or the spool angle of a jet) every frame (a crank turns, a rod swings, a piston slides, a valve lifts
 * and its spring shortens), and in a cutaway the casings cut in half by a plane: the half towards the viewer is left
 * out, so the cut follows the view round.
 */
public final class EngineModel {
    /** Where a piece is at an angle: its frame's origin and rotation in the engine's frame, and a stretch of its own y axis (a spring). */
    public record Placement(Vec3d offset, Orientation rotation, double stretch) {
        public Placement(Vec3d offset, Orientation rotation) { this(offset, rotation, 1); }
    }

    /**
     * A mesh in its own frame and where it is at an angle (rad: the preview's; a piston engine's crank angle). 0.11: in
     * the world it turns with the part's rotor of that index, at the preview's angle times rate being that rotor's
     * angle (a jet's spool turns three times the preview's angle: rate 3; a crank's pieces: rotor 0, rate 1).
     */
    public record Piece(PartMesh mesh, DoubleFunction<Placement> at, int rotor, double rate) {}

    /** Casings cut by a plane through point with a normal: the part behind the plane (kept when the viewer is on the normal's side) and the part in front. */
    public record Section(Vec3d point, Vec3d normal, PartMesh behind, PartMesh front, PartMesh whole) {}

    public final PartMesh fixed;
    public final List<Piece> pieces = new ArrayList<>();
    public final List<Section> sections = new ArrayList<>();
    /** 0.11: where the exhaust leaves (a point and the way out) for its smoke, where a model knows it (a piston engine's pipes). */
    public final List<Vec3d[]> exhausts = new ArrayList<>();
    /**
     * 0.12: pieces baked into the fixed mesh (MeshBuilder.piece) and how each is posed from named values: a body's
     * doors ("doors", 0 shut to 1 open), its steering wheel, yokes, pedals ("steer", "pitch", "roll"... as the
     * vehicle's channels), its wipers ("wipe"). A piece without an entry stays where it was built.
     */
    public final java.util.Map<Integer, java.util.function.Function<java.util.Map<String, Double>, Placement>> posed = new java.util.HashMap<>();

    EngineModel(PartMesh fixed) { this.fixed = fixed; }

    /** 0.12: a model that is all one fixed mesh (a part drawn in the design screen as the world draws it). */
    public static EngineModel of(PartMesh fixed) { return new EngineModel(fixed); }

    void piece(PartMesh mesh, DoubleFunction<Placement> at) { piece(mesh, 0, 1, at); }

    void piece(PartMesh mesh, int rotor, double rate, DoubleFunction<Placement> at) {
        if (!mesh.quads().isEmpty() && rate != 0) { pieces.add(new Piece(mesh, at, rotor, rate)); }
    }

    /** 0.11: a mesh's quads all marked as inside (a vehicle in the world leaves them out; a cutaway shows them): an engine's crank and valves. */
    static PartMesh inside(PartMesh mesh) {
        var quads = mesh.quads();
        for (int i = 0; i < quads.size(); i++) { quads.set(i, quads.get(i).withTags(quads.get(i).tags() | dev.mcmod.aerocore.api.mesh.MeshBuilder.INTERIOR)); }
        return mesh;
    }

    void section(PartMesh casings, Vec3d point, Vec3d normal) {
        if (casings.quads().isEmpty()) { return; }
        sections.add(new Section(point, normal, casings.clipped(point, normal), casings.clipped(point, normal.scale(-1)), casings));
    }

    /** Everything at one angle as a single mesh (a vehicle in the world, which draws one static mesh per part). */
    public PartMesh at(double angle) {
        PartMesh out = new PartMesh().add(fixed, Orientation.IDENTITY, Vec3d.ZERO);
        for (Piece p : pieces) {
            Placement place = p.at().apply(angle);
            if (place.stretch() == 1) { out.add(p.mesh(), place.rotation(), place.offset()); }
        }
        return out;
    }

    /** The box around everything at an angle (min, max); null when there is nothing. */
    public Vec3d[] bounds() {
        PartMesh all = at(0);
        for (Section s : sections) { all.add(s.whole(), Orientation.IDENTITY, Vec3d.ZERO); }
        return all.bounds();
    }

    /**
     * Emits the fixed parts, the pieces at an angle and the cut casings (the halves away from a viewer in the
     * direction toViewer, in the engine's frame), the opaque quads or the translucent ones.
     */
    public void emit(VertexConsumer out, PoseStack pose, int light, double angle, Vec3d toViewer, boolean translucent) {
        fixed.emit(out, pose, light, translucent);
        for (Section s : sections) { (s.normal().dot(toViewer) > 0 ? s.behind() : s.front()).emit(out, pose, light, translucent); }
        for (Piece p : pieces) {
            Placement place = p.at().apply(angle);
            pose.pushPose();
            pose.translate(place.offset().x(), place.offset().y(), place.offset().z());
            Orientation r = place.rotation();
            pose.mulPose(new Quaternionf((float) r.x(), (float) r.y(), (float) r.z(), (float) r.w()));
            if (place.stretch() != 1) { pose.scale(1, (float) place.stretch(), 1); }
            p.mesh().emit(out, pose, light, translucent);
            pose.popPose();
        }
    }

    /** Number of quads drawn per frame. */
    public int size() {
        int n = fixed.quads().size();
        for (Section s : sections) { n += s.behind().quads().size(); }
        for (Piece p : pieces) { n += p.mesh().quads().size(); }
        return n;
    }
}
