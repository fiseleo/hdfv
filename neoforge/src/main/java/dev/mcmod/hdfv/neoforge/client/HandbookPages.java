package dev.mcmod.hdfv.neoforge.client;

import com.mojang.logging.LogUtils;
import dev.mcmod.hdfv.handbook.HandbookText;
import dev.mcmod.hdfv.neoforge.Hdfv;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The handbook's entries in the language in use (0.24): the page files of the resource packs,
 * assets/hdfv/handbook/&lt;language&gt;/*.md (HandbookText), English where a language lacks one; the main line first,
 * then the branches, each by its order. Read again after a resource reload or a change of language.
 */
@EventBusSubscriber(modid = Hdfv.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
final class HandbookPages {
    private HandbookPages() {}

    private static final Logger LOGGER = LogUtils.getLogger();
    private static List<HandbookText.Entry> entries;
    private static String language;

    static synchronized List<HandbookText.Entry> get() {
        String lang = Minecraft.getInstance().getLanguageManager().getSelected();
        if (entries == null || !lang.equals(language)) {
            entries = load(Minecraft.getInstance().getResourceManager(), lang);
            language = lang;
        }
        return entries;
    }

    static HandbookText.Entry find(String id) {
        for (HandbookText.Entry e : get()) { if (e.id().equals(id)) { return e; } }
        return null;
    }

    private static List<HandbookText.Entry> load(ResourceManager resources, String lang) {
        Map<String, HandbookText.Entry> byId = new LinkedHashMap<>();
        read(resources, "en_us", byId);
        if (!lang.equals("en_us")) { read(resources, lang, byId); }
        List<HandbookText.Entry> out = new ArrayList<>(byId.values());
        out.sort(Comparator.comparing(HandbookText.Entry::section).thenComparingInt(HandbookText.Entry::order));
        return List.copyOf(out);
    }

    private static void read(ResourceManager resources, String lang, Map<String, HandbookText.Entry> into) {
        var found = resources.listResources("handbook/" + lang, p -> p.getNamespace().equals(Hdfv.MOD_ID) && p.getPath().endsWith(".md"));
        for (var e : found.entrySet()) {
            try (BufferedReader r = e.getValue().openAsReader()) {
                HandbookText.Entry entry = HandbookText.parse(r.lines().collect(Collectors.joining("\n")));
                into.put(entry.id(), entry);
            } catch (Exception ex) {
                LOGGER.warn("hdfv handbook: {} not read: {}", e.getKey(), ex.getMessage());
            }
        }
    }

    @SubscribeEvent static void reloads(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) resources -> {
            synchronized (HandbookPages.class) { entries = null; }
        });
    }
}
