package dev.mcmod.hdfv.neoforge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.mcmod.aerocore.api.Orientation;
import dev.mcmod.aerocore.api.vehicle.Part;
import dev.mcmod.aerocore.neoforge.client.MeshBakery;
import dev.mcmod.aerocore.neoforge.client.PartLayers;
import dev.mcmod.aerocore.neoforge.client.PartMesh;
import dev.mcmod.hdfv.neoforge.client.model.EngineModel;
import dev.mcmod.hdfv.neoforge.client.model.Looks;
import org.joml.Quaternionf;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;

/**
 * An engine's model in the world (0.11): the fixed parts are the part's static mesh; the moving pieces (a jet's
 * spinner, first rotor and last turbine, a turbofan's fan, a piston engine's fan and pulleys) are each baked once
 * (finishes, levels; what lies deep inside is left out, EngineModel.inside) and turned every frame with the part's
 * rotor they belong to, at their gearing.
 */
final class EnginePieces {
    private EnginePieces() {}

    /** Pieces are lit by their engine's light and not occluded (they move against the rest), what is inside left out. */
    static final MeshBakery.Spec PIECES = new MeshBakery.Spec(null, false, 0, true);

    private static final Map<Part, EngineModel> MODELS = Collections.synchronizedMap(new WeakHashMap<>());

    /** A part's model, built once (on the bake thread or the render thread, whichever asks first). */
    static EngineModel model(Part part, Function<Part, EngineModel> build) { return MODELS.computeIfAbsent(part, build); }

    /** The fixed parts, for the part's static mesh. */
    static PartMesh fixed(Part part, Function<Part, EngineModel> build) { return model(part, build).fixed; }

    /** Draws the moving pieces at their rotors' angles (the part's rotors from firstRotor). */
    static void draw(Part part, Function<Part, EngineModel> build, dev.mcmod.aerocore.neoforge.vehicle.PartVehicle entity, int firstRotor, float partialTick,
                     PoseStack stack, PartLayers layers, int light) {
        draw(model(part, build), part.rotors().size(), r -> entity.rotorAngle(firstRotor + r, partialTick), stack, layers, light);
    }

    /** Draws a model's moving pieces at the angles of its rotors (rad, by index; those of rotors beyond a count are left out). */
    static void draw(EngineModel model, int rotors, java.util.function.IntToDoubleFunction rotorAngle, PoseStack stack, PartLayers layers, int light) {
        for (EngineModel.Piece piece : model.pieces) {
            if (piece.rotor() >= rotors) { continue; }
            MeshBakery.Baked baked = MeshBakery.get(piece, () -> Looks.finish(new PartMesh().add(piece.mesh(), Orientation.IDENTITY, dev.mcmod.aerocore.api.Vec3d.ZERO)), PIECES);
            if (baked == null || baked.level(0).isEmpty()) { continue; }
            double angle = rotorAngle.applyAsDouble(piece.rotor()) / piece.rate();
            EngineModel.Placement place = piece.at().apply(angle);
            stack.pushPose();
            stack.translate(place.offset().x(), place.offset().y(), place.offset().z());
            Orientation r = place.rotation();
            stack.mulPose(new Quaternionf((float) r.x(), (float) r.y(), (float) r.z(), (float) r.w()));
            if (place.stretch() != 1) { stack.scale(1, (float) place.stretch(), 1); }
            layers.draw(baked.level(0), stack, light);
            stack.popPose();
        }
    }
}
