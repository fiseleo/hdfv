package dev.mcmod.hdfv.neoforge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.vehicle.Part;
import dev.mcmod.aerocore.api.wheel.WheelCorner;
import dev.mcmod.aerocore.neoforge.client.PartLayers;
import dev.mcmod.aerocore.neoforge.client.PartMesh;
import dev.mcmod.aerocore.neoforge.client.PartRenderer;
import dev.mcmod.aerocore.neoforge.vehicle.PartVehicle;
import dev.mcmod.hdfv.look.Finishes;
import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.neoforge.client.model.Looks;
import dev.mcmod.hdfv.part.truck.FifthWheelDesign;
import dev.mcmod.hdfv.part.truck.LandingLegsDesign;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 0.18: a truck's parts. A fifth wheel: a black-painted plate on its rocker bracket, greased on top, with the V of its
 * jaw opening to the rear and a red release handle at its side. A landing leg: a square outer tube on its bracket and a
 * zinc-plated inner tube with its foot that slides up into it as the leg is drawn up (the leg's shown share).
 */
@EventBusSubscriber(modid = Hdfv.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class TruckLooks {
    private TruckLooks() {}

    private static final int PLATE = 0xff1c1d20, GREASE = 0xff3a3a2c, BRACKET = 0xff2a2c30, HANDLE = 0xffb3261e, TUBE = 0xff2e3a46, INNER = 0xffaab2b8,
            FOOT = 0xff26282b, CRANK = 0xff8f969c;

    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        Looks.register(FifthWheelDesign.class, new PartRenderer() {
            @Override public void mesh(Part part, PartMesh out) { fifthWheel((FifthWheelDesign) part.design(), out); }
        });
        Looks.register(LandingLegsDesign.class, new PartRenderer() {
            @Override public void mesh(Part part, PartMesh out) { leg((LandingLegsDesign) part.design(), out); }

            @Override public void animate(Part part, PartVehicle entity, String partId, int firstRotor, float partialTick, PoseStack stack, PartLayers layers, int light,
                                          Drawn drawn) {
                if (drawn == null) { return; }
                LandingLegsDesign d = (LandingLegsDesign) part.design();
                double up = Math.clamp(entity.shown(partId, WheelCorner.SHOWN + WheelCorner.EXTRA, partialTick), 0, 1) * d.strokeM();
                stack.pushPose();
                stack.translate(0, up, 0);
                layers.draw(drawn.piece(1), stack, light);
                stack.popPose();
            }
        });
    }

    /** The fifth wheel: its base at the part's origin, the plate's face heightM up (the jaw), the jaw's V opening to the rear (-x). */
    static void fifthWheel(FifthWheelDesign d, PartMesh out) {
        double r = d.plateDiameterM() / 2, h = d.heightM(), plate = 0.04;
        out.finish(Finishes.PAINT_MATTE);
        // the mounting plate and the two rocker brackets
        out.box(new Vec3d(-0.45 * r, 0, -0.85 * r), new Vec3d(0.45 * r, 0.02, 0.85 * r), BRACKET);
        for (double z : new double[]{-0.7 * r, 0.7 * r}) { out.box(new Vec3d(-0.25 * r, 0.02, z - 0.04), new Vec3d(0.25 * r, h - plate, z + 0.04), BRACKET); }
        // the plate: a disc with the V cut away behind the jaw (two halves either side of the slot)
        int sides = 32;
        double slot = 0.07;
        for (int i = 0; i < sides; i++) {
            double a0 = 2 * Math.PI * i / sides, a1 = 2 * Math.PI * (i + 1) / sides;
            double x0 = r * Math.cos(a0), z0 = r * Math.sin(a0), x1 = r * Math.cos(a1), z1 = r * Math.sin(a1);
            // the slot runs from the centre to the rear edge: leave out the wedges it crosses
            if (Math.max(x0, x1) < 0 && Math.abs(0.5 * (z0 + z1)) < slot + 0.12 * Math.abs(0.5 * (x0 + x1))) { continue; }
            Vec3d outward = new Vec3d(0.5 * (x0 + x1), 0, 0.5 * (z0 + z1)).normalized();
            out.quad(new Vec3d(0, h, 0), new Vec3d(x1, h, z1), new Vec3d(x0, h, z0), new Vec3d(0, h, 0), GREASE, Vec3d.Y);
            out.quad(new Vec3d(x0, h - plate, z0), new Vec3d(x1, h - plate, z1), new Vec3d(x1, h, z1), new Vec3d(x0, h, z0), PLATE, outward);
            out.quad(new Vec3d(0, h - plate, 0), new Vec3d(x0, h - plate, z0), new Vec3d(x1, h - plate, z1), new Vec3d(0, h - plate, 0), PLATE, Vec3d.Y.scale(-1));
        }
        // the jaw's lock in the middle and the release handle at the side
        out.finish(Finishes.FORGED_STEEL).cylinder(new Vec3d(0, h - plate - 0.06, 0), new Vec3d(0, h - plate, 0), 0.07, 0.07, 16, BRACKET);
        out.finish(Finishes.PAINT_GLOSS).box(new Vec3d(-0.05, h - 0.5 * plate - 0.015, r - 0.02), new Vec3d(0.05, h - 0.5 * plate + 0.015, r + 0.18), HANDLE);
        out.finish(null);
    }

    /** A landing leg: the outer tube from the mount, the inner tube and foot (piece 1) let down to lengthM. */
    static void leg(LandingLegsDesign d, PartMesh out) {
        double l = d.lengthM(), s = d.strokeM(), outer = 0.07, inner = 0.055, foot = d.footDiameterM() / 2;
        double outerLength = Math.max(l - s - foot, 0.2);
        out.finish(Finishes.PAINT_MATTE);
        out.box(new Vec3d(-0.09, -0.05, -0.09), new Vec3d(0.09, 0.02, 0.09), BRACKET);
        out.box(new Vec3d(-outer, -outerLength, -outer), new Vec3d(outer, -0.05, outer), TUBE);
        // the crank's gearbox on the outer tube
        out.box(new Vec3d(-0.06, -0.3, outer), new Vec3d(0.06, -0.15, outer + 0.08), TUBE);
        out.finish(Finishes.BRUSHED_STEEL).cylinder(new Vec3d(0, -0.22, outer + 0.08), new Vec3d(0, -0.22, outer + 0.2), 0.012, 0.012, 8, CRANK);
        // the inner tube, from inside the outer one down to the foot, and the foot (a shoe on its pin)
        out.piece(1).finish(Finishes.STAINLESS);
        out.box(new Vec3d(-inner, -(l - foot * 0.6), -inner), new Vec3d(inner, -outerLength + 0.15, inner), INNER);
        out.finish(Finishes.CAST_IRON).cylinder(new Vec3d(0, -l, -0.12), new Vec3d(0, -l, 0.12), foot, foot, 20, FOOT);
        out.box(new Vec3d(-foot, -l - foot, -0.13), new Vec3d(foot, -l - foot + 0.03, 0.13), FOOT);
        out.pens(null, 0);
    }
}
