package dev.mcmod.hdfv.neoforge.multiblock;

import net.minecraft.server.level.ServerPlayer;

/** A controller with a screen of its own (0.20: the control tower): used empty-handed (not sneaking) it opens it. */
public interface Screened {
    void openScreen(ServerPlayer player);
}
