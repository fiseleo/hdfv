package dev.mcmod.hdfv.neoforge.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import dev.mcmod.aerocore.neoforge.client.PartFinishes;
import dev.mcmod.aerocore.neoforge.client.RuntimeTexture;
import dev.mcmod.hdfv.look.Finishes;
import dev.mcmod.hdfv.look.Sticker;
import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.neoforge.paint.DecalNetwork;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The stickers' pictures on a client (0.13). A picture becomes a finish (PartFinishes.define, under Finishes.decal's
 * id) the first time a sticker showing it is drawn: transparent until it comes from the server, which it is asked for
 * then (again after ASK_AGAIN without an answer). Its texture's alpha is what the part shader reads as gloss and cut-out:
 * pixels under half opaque are cut out, the rest get the film's gloss. A picture the server has not (or no longer)
 * stays transparent: its stickers do not show. At most MOST are kept; beyond that, the least lately drawn (not within
 * KEEP) are let go and asked for again if drawn again. All are let go when leaving a world. Render thread.
 */
@EventBusSubscriber(modid = Hdfv.MOD_ID, value = Dist.CLIENT)
public final class DecalCache {
    private DecalCache() {}

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MOST = 128, LARGEST = 8 << 20;
    private static final long ASK_AGAIN = 30_000_000_000L, KEEP = 10_000_000_000L;
    /** A sticker film's gloss (vinyl), and the texture's alpha where the picture is opaque: 128 + 127 g. */
    public static final double GLOSS = 0.6;
    private static final int OPAQUE = 128 + (int) Math.round(127 * GLOSS);

    public enum State { ASKED, SHOWN, GONE }

    private static final class Entry {
        final RuntimeTexture texture;
        State state = State.ASKED;
        long touched, asked;
        byte[] incoming;
        int got, width, height;
        Entry(RuntimeTexture texture) { this.texture = texture; }
    }

    private static final Map<String, Entry> ENTRIES = new HashMap<>();

    /** Makes sure a picture can be drawn (transparent until it comes) and marks it drawn now. */
    public static void want(String sha) {
        if (!Sticker.isImage(sha)) { return; }
        long now = System.nanoTime();
        Entry e = ENTRIES.get(sha);
        if (e == null) {
            e = new Entry(new RuntimeTexture(transparent(), true));
            PartFinishes.define(Finishes.decal(sha).id(), look(sha), e.texture);
            ENTRIES.put(sha, e);
            e.asked = now;
            DecalNetwork.ask(sha);
        }
        e.touched = now;
    }

    public static void want(Iterable<String> shas) { for (String sha : shas) { want(sha); } }

    /** The pictures each design's stickers show (a body's, a wing's; none for others), worked out once a design. */
    private static final Map<dev.mcmod.aerocore.api.vehicle.PartDesign, List<String>> IMAGES =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    /** Wants the pictures a design's stickers show (a renderer drawing a part of it, a preview). */
    public static void wantFor(dev.mcmod.aerocore.api.vehicle.PartDesign d) {
        if (d == null) { return; }
        want(IMAGES.computeIfAbsent(d, DecalCache::images));
    }

    private static List<String> images(dev.mcmod.aerocore.api.vehicle.PartDesign d) {
        List<Sticker> stickers = d instanceof dev.mcmod.hdfv.part.body.BodyDesign b ? b.settings().stickers()
                : d instanceof dev.mcmod.hdfv.part.air.WingDesign w ? w.structure().stickers() : List.of();
        return stickers.stream().map(Sticker::image).distinct().toList();
    }

    /** Where a picture stands (null: not asked for). */
    public static State state(String sha) {
        Entry e = ENTRIES.get(sha);
        return e == null ? null : e.state;
    }

    /** A shown picture's size in pixels, or null. */
    public static int[] size(String sha) {
        Entry e = ENTRIES.get(sha);
        return e == null || e.state != State.SHOWN ? null : new int[]{e.width, e.height};
    }

    /** The texture a picture is drawn with (for the booth's picture list), or null when it was not asked for. */
    public static ResourceLocation texture(String sha) { return ENTRIES.containsKey(sha) ? key(sha) : null; }

    /** A chunk of a picture from the server. */
    public static void received(String sha, int total, int offset, byte[] data) {
        Entry e = ENTRIES.get(sha);
        if (e == null || e.state == State.SHOWN || total <= 0 || total > LARGEST) { return; }
        if (offset == 0 || e.incoming == null || e.incoming.length != total) { e.incoming = new byte[total]; e.got = 0; }
        if (offset != e.got || offset + data.length > total) { e.incoming = null; return; }
        System.arraycopy(data, 0, e.incoming, offset, data.length);
        e.got += data.length;
        if (e.got < total) { return; }
        byte[] png = e.incoming;
        e.incoming = null;
        try {
            // 0.14: from a stream (off-heap memory): NativeImage.read(byte[]) copies onto LWJGL's 64 KB thread stack, and
            // every picture bigger than that failed with "Out of stack space" (a player's 584 KB picture never showed)
            NativeImage image = NativeImage.read(new java.io.ByteArrayInputStream(png));
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int p = image.getPixelRGBA(x, y);
                    image.setPixelRGBA(x, y, (p & 0x00ffffff) | ((p >>> 24) >= 128 ? OPAQUE << 24 : 0));
                }
            }
            e.width = image.getWidth();
            e.height = image.getHeight();
            e.texture.replace(image);
            e.state = State.SHOWN;
        } catch (IOException | RuntimeException ex) {
            LOGGER.warn("Sticker picture {} could not be read: {}", sha, ex.toString());
            e.state = State.GONE;
        }
    }

    /** The server has no such picture (or an operator removed it): its stickers do not show. */
    public static void gone(String sha) {
        Entry e = ENTRIES.get(sha);
        if (e == null) { return; }
        e.state = State.GONE;
        e.incoming = null;
        e.texture.replace(transparent());
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (ENTRIES.isEmpty()) { return; }
        long now = System.nanoTime();
        for (Map.Entry<String, Entry> m : ENTRIES.entrySet()) {
            Entry e = m.getValue();
            if (e.state == State.ASKED && now - e.asked > ASK_AGAIN && now - e.touched < KEEP) { e.asked = now; DecalNetwork.ask(m.getKey()); }
        }
        if (ENTRIES.size() <= MOST) { return; }
        List<Map.Entry<String, Entry>> old = new ArrayList<>(ENTRIES.entrySet());
        old.sort(Comparator.comparingLong(m -> m.getValue().touched));
        for (Map.Entry<String, Entry> m : old) {
            if (ENTRIES.size() <= MOST || now - m.getValue().touched < KEEP) { break; }
            let(m.getKey());
        }
    }

    @SubscribeEvent public static void leave(ClientPlayerNetworkEvent.LoggingOut event) {
        for (String sha : List.copyOf(ENTRIES.keySet())) { let(sha); }
    }

    private static void let(String sha) {
        ENTRIES.remove(sha);
        PartFinishes.undefine(Finishes.decal(sha).id());
    }

    private static ResourceLocation key(String sha) { return ResourceLocation.fromNamespaceAndPath(Hdfv.MOD_ID, "decal/" + sha); }

    /** A sticker's finish: its picture, tile 1 (texture coordinates are the picture's), the film's gloss, cut out at a quarter. */
    private static PartFinishes.Look look(String sha) { return new PartFinishes.Look(key(sha), 1.0, GLOSS, 0, 0xffffffff, 0, 0, 0.25, 0); }

    private static NativeImage transparent() {
        NativeImage image = new NativeImage(1, 1, true);
        image.setPixelRGBA(0, 0, 0);
        return image;
    }
}
