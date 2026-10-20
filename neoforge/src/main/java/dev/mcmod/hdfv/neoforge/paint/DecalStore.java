package dev.mcmod.hdfv.neoforge.paint;

import com.mojang.logging.LogUtils;
import dev.mcmod.hdfv.look.Sticker;
import dev.mcmod.hdfv.neoforge.HdfvConfig;
import dev.mcmod.hdfv.paint.DecalImages;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

/**
 * The stickers' pictures on the server (0.13): PNG files in the world's hdfv/decals folder named by their SHA-256,
 * recorded in DecalIndex. A picture is taken only as DecalImages.check allows (a PNG of at most the size limits whose
 * SHA-256 is its name), within the player's and the server's quotas, while uploads are allowed. Operators remove a
 * picture or everything a player uploaded (DecalCommands). Server thread.
 */
public final class DecalStore {
    private DecalStore() {}

    private static final Logger LOGGER = LogUtils.getLogger();

    /** The limits a picture is taken under (the server settings, or a test's). */
    public record Limits(boolean uploads, int perPlayer, int total, int maxBytes, int maxSide) {
        public static Limits config() {
            return new Limits(HdfvConfig.decalUploads(), HdfvConfig.decalsPerPlayer(), HdfvConfig.decalsTotal(), HdfvConfig.decalBytes(), HdfvConfig.decalSide());
        }
    }

    /** Why a picture is refused. */
    public enum Refusal { DISABLED, NOT_PNG, TOO_LARGE, TOO_WIDE, HASH, PLAYER_QUOTA, SERVER_QUOTA, FAILED }

    /** What became of an upload: refusal null when taken (or already there), with its size. */
    public record Result(String sha, Refusal refusal, int width, int height) {
        public boolean ok() { return refusal == null; }
    }

    public static Path folder(MinecraftServer server) { return server.getWorldPath(LevelResource.ROOT).resolve("hdfv").resolve("decals"); }

    /** A picture's file (only for a proper name: no path can be made of anything else). */
    private static Path file(MinecraftServer server, String sha) {
        if (!Sticker.isImage(sha)) { throw new IllegalArgumentException("Not a picture's name"); }
        return folder(server).resolve(sha + ".png");
    }

    /** Takes a picture a player uploaded (the whole PNG). */
    public static Result store(MinecraftServer server, UUID user, String name, String sha, byte[] png, Limits limits) {
        if (!Sticker.isImage(sha)) { return new Result(sha, Refusal.HASH, 0, 0); }
        if (!limits.uploads()) { return new Result(sha, Refusal.DISABLED, 0, 0); }
        DecalIndex index = DecalIndex.of(server);
        DecalIndex.Entry known = index.get(sha);
        if (known != null && known.users().contains(user)) { return new Result(sha, null, known.width(), known.height()); }
        if (index.count(user) >= limits.perPlayer()) { return new Result(sha, Refusal.PLAYER_QUOTA, 0, 0); }
        if (known == null && index.size() >= limits.total()) { return new Result(sha, Refusal.SERVER_QUOTA, 0, 0); }
        DecalImages.Checked c = DecalImages.check(png, sha, limits.maxBytes(), limits.maxSide());
        if (!c.ok()) {
            return new Result(sha, switch (c.refusal()) {
                case NOT_PNG -> Refusal.NOT_PNG;
                case TOO_LARGE -> Refusal.TOO_LARGE;
                case TOO_WIDE -> Refusal.TOO_WIDE;
                case HASH -> Refusal.HASH;
            }, c.width(), c.height());
        }
        Path target = file(server, sha);
        if (known == null || !Files.isRegularFile(target)) {
            try {
                Files.createDirectories(target.getParent());
                Path part = target.resolveSibling(sha + ".part");
                Files.write(part, png);
                Files.move(part, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                LOGGER.error("Could not save sticker picture {}", sha, e);
                return new Result(sha, Refusal.FAILED, 0, 0);
            }
        }
        index.add(sha, c.width(), c.height(), png.length, System.currentTimeMillis(), user, name);
        LOGGER.info("Sticker picture {} ({}x{}, {} bytes) uploaded by {}", sha, c.width(), c.height(), png.length, name);
        return new Result(sha, null, c.width(), c.height());
    }

    /** A picture's PNG, or null when there is none (never uploaded, or removed). */
    public static byte[] read(MinecraftServer server, String sha) {
        if (!Sticker.isImage(sha) || DecalIndex.of(server).get(sha) == null) { return null; }
        try {
            return Files.readAllBytes(file(server, sha));
        } catch (IOException e) {
            LOGGER.warn("Sticker picture {} is recorded but cannot be read: {}", sha, e.toString());
            return null;
        }
    }

    /** Removes a picture (its record and file); false when there was none. */
    public static boolean remove(MinecraftServer server, String sha) {
        if (!Sticker.isImage(sha) || DecalIndex.of(server).remove(sha) == null) { return false; }
        delete(server, sha);
        return true;
    }

    /** Takes a player off every picture's uploaders, deleting those left with none; returns how many pictures they were among. */
    public static int purge(MinecraftServer server, UUID user) {
        DecalIndex index = DecalIndex.of(server);
        int among = index.count(user);
        for (String sha : index.drop(user)) { delete(server, sha); }
        return among;
    }

    public static List<DecalIndex.Entry> list(MinecraftServer server, UUID user) { return DecalIndex.of(server).entries(user); }

    private static void delete(MinecraftServer server, String sha) {
        try {
            Files.deleteIfExists(file(server, sha));
        } catch (IOException e) {
            LOGGER.warn("Could not delete sticker picture {}: {}", sha, e.toString());
        }
    }
}
