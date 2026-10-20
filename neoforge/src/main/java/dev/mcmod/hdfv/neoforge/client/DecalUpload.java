package dev.mcmod.hdfv.neoforge.client;

import com.mojang.logging.LogUtils;
import dev.mcmod.hdfv.neoforge.paint.DecalNetwork;
import dev.mcmod.hdfv.neoforge.paint.DecalStore;
import dev.mcmod.hdfv.paint.DecalImages;
import net.minecraft.client.Minecraft;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Uploading a picture for stickers (0.13), from the player's own computer only: picked in the system's file dialog
 * (LWJGL's tinyfd, on a thread of its own, since it waits for the player) or dropped on the paint booth's screen. The
 * picture is read (PNG, JPEG, BMP, GIF), shrunk to the server's longest side, written as PNG and named by its SHA-256
 * there too, then sent in chunks; the server's word comes back to {@link #uploaded}. One upload at a time. In full
 * screen the dialog may open behind the game: dropping a file on the screen works there.
 */
public final class DecalUpload {
    private DecalUpload() {}

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ExecutorService WORK = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "hdfv sticker upload");
        t.setDaemon(true);
        return t;
    });
    private static final AtomicBoolean BUSY = new AtomicBoolean();
    private static volatile Component status = Component.empty();
    private static volatile DecalNetwork.PicturesPayload pictures;
    private static volatile String sending;

    /** What the last upload came to (for the booth's screen). */
    public static Component status() { return status; }

    /** Whether an upload is going. */
    public static boolean busy() { return BUSY.get(); }

    /** The player's pictures and the server's limits, as last heard (null before the first list). */
    public static DecalNetwork.PicturesPayload pictures() { return pictures; }

    /** Asks the server for the player's pictures. */
    public static void refresh() { DecalNetwork.list(); }

    /** Opens the system's file dialog and uploads the picture picked. */
    public static void pick() {
        if (!BUSY.compareAndSet(false, true)) { return; }
        if (Minecraft.getInstance().getWindow().isFullscreen()) {
            status = Component.translatableWithFallback("hdfv.decal.fullscreen", "The file dialog may open behind the game in full screen: you can drop a picture on this screen instead");
        }
        String title = Language.getInstance().getOrDefault("hdfv.decal.pick", "Pick a picture for stickers");
        WORK.execute(() -> {
            String chosen = null;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                String[] patterns = {"*.png", "*.jpg", "*.jpeg", "*.bmp", "*.gif"};
                PointerBuffer filters = stack.mallocPointer(patterns.length);
                for (String p : patterns) { filters.put(stack.UTF8(p)); }
                filters.flip();
                chosen = TinyFileDialogs.tinyfd_openFileDialog(title, null, filters, "PNG, JPEG, BMP, GIF", false);
            } catch (Throwable t) {
                LOGGER.warn("The file dialog could not open: {}", t.toString());
                status = Component.translatableWithFallback("hdfv.decal.no_dialog", "The file dialog could not open: drop a picture on this screen instead");
            }
            if (chosen == null) { BUSY.set(false); return; }
            send(Path.of(chosen));
        });
    }

    /** Uploads the first picture among files dropped on a screen; false when none is a file. */
    public static boolean dropped(List<Path> files) {
        for (Path f : files) {
            if (!Files.isRegularFile(f)) { continue; }
            upload(f);
            return true;
        }
        return false;
    }

    /** Uploads a picture file (a dropped one, a development command's). */
    public static void upload(Path file) {
        if (!BUSY.compareAndSet(false, true)) { return; }
        WORK.execute(() -> send(file));
    }

    /** On the worker: reads, shrinks and names the picture, then sends it from the game's thread. */
    private static void send(Path file) {
        DecalNetwork.PicturesPayload limits = pictures;
        int side = limits != null ? limits.maxSide() : DecalImages.MAX_SIDE, bytes = limits != null ? limits.maxBytes() : DecalImages.MAX_BYTES;
        try {
            DecalImages.Prepared p = DecalImages.prepare(file, side);
            if (p.png().length > bytes) {
                status = refused(DecalStore.Refusal.TOO_LARGE.ordinal());
                BUSY.set(false);
                return;
            }
            Minecraft.getInstance().execute(() -> {
                sending = p.sha();
                status = Component.translatableWithFallback("hdfv.decal.sending", "Uploading %s (%s x %s)...", file.getFileName().toString(), p.width(), p.height());
                DecalNetwork.upload(p.sha(), p.png());
            });
        } catch (IOException | RuntimeException e) {
            LOGGER.info("Not a picture for stickers: {} ({})", file, e.toString());
            status = Component.translatableWithFallback("hdfv.decal.not_image", "%s is not a picture", file.getFileName().toString());
            BUSY.set(false);
        }
    }

    /** The server's word on an upload (refusal -1: taken). */
    public static void uploaded(String sha, int refusal, int width, int height) {
        if (sha.equals(sending)) { sending = null; BUSY.set(false); }
        status = refusal < 0 ? Component.translatableWithFallback("hdfv.decal.uploaded", "Uploaded (%s x %s)", width, height) : refused(refusal);
        if (Minecraft.getInstance().player != null) { Minecraft.getInstance().player.displayClientMessage(status, true); }
        if (refusal < 0) { refresh(); }
    }

    /** The player's pictures and the limits. */
    public static void listed(DecalNetwork.PicturesPayload payload) { pictures = payload; }

    private static Component refused(int refusal) {
        DecalStore.Refusal[] all = DecalStore.Refusal.values();
        DecalStore.Refusal r = refusal >= 0 && refusal < all.length ? all[refusal] : DecalStore.Refusal.FAILED;
        String fallback = switch (r) {
            case DISABLED -> "This server does not take pictures";
            case NOT_PNG -> "The picture could not be read";
            case TOO_LARGE -> "The picture is too large";
            case TOO_WIDE -> "The picture is too wide";
            case HASH -> "The picture was damaged on the way";
            case PLAYER_QUOTA -> "You have uploaded as many pictures as the server allows";
            case SERVER_QUOTA -> "The server holds as many pictures as it allows";
            case FAILED -> "The server could not keep the picture";
        };
        return Component.translatableWithFallback("hdfv.decal.refused." + r.name().toLowerCase(Locale.ROOT), fallback);
    }
}
