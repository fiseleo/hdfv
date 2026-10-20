package dev.mcmod.hdfv.neoforge.client;

import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.hdfv.design.Designs;
import dev.mcmod.hdfv.neoforge.design.Blueprints;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;

/** Client-only entry points the common code calls on the client side (blocks and items open the design screen). */
public final class ClientAccess {
    private ClientAccess() {}

    /** Opens the design screen on the blueprint in a hand (main hand first), or on the last design. */
    public static void openDesignScreen(Player player) {
        for (InteractionHand hand : InteractionHand.values()) {
            String json = Blueprints.json(player.getItemInHand(hand));
            if (json == null) { continue; }
            Blueprints.Target target = hand == InteractionHand.MAIN_HAND ? Blueprints.Target.MAIN_HAND : Blueprints.Target.OFF_HAND;
            PartDesign design;
            try { design = Designs.read(json); }
            catch (RuntimeException e) {
                Minecraft.getInstance().setScreen(new DesignScreen(null, Blueprints.Target.NEW, Component.translatable("hdfv.blueprint.unreadable")));
                return;
            }
            Minecraft.getInstance().setScreen(new DesignScreen(design, target, null));
            return;
        }
        Minecraft.getInstance().setScreen(new DesignScreen(null, Blueprints.Target.NEW, null));
    }

    /** The server's answer to a save: on the design screen if it is open, else over the hotbar. */
    public static void saved(Blueprints.Result result) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof DesignScreen screen) { screen.saved(result); }
        else if (minecraft.player != null) { minecraft.player.displayClientMessage(Blueprints.message(result), true); }
    }
}
