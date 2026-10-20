package dev.mcmod.hdfv.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * hdfv's client-only entry point (0.19): the mod list's "Config" button opens NeoForge's own settings screen for
 * hdfv-client.toml (and, in a world, the server's), with the sound volumes as sliders.
 */
@Mod(value = Hdfv.MOD_ID, dist = Dist.CLIENT)
public final class HdfvClient {
    public HdfvClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
