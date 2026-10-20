package dev.mcmod.hdfv.neoforge.paint;

import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.paint.DecalImages;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * The stickers' pictures on the server (0.13): a good picture is kept and given back, through the upload's chunks too;
 * what is too large, too wide, not a PNG, damaged, over a quota or sent while uploads are off is refused; a removed or
 * purged picture is gone (file and all). Each test leaves the world's picture folder as it found it.
 */
@PrefixGameTestTemplate(false)
public final class DecalGameTests {
    private DecalGameTests() {}

    private static final DecalStore.Limits OPEN = new DecalStore.Limits(true, 8, 100_000, DecalImages.MAX_BYTES, DecalImages.MAX_SIDE);

    /** A picture of its own: stripes in colours from a seed, a transparent corner. */
    private static DecalImages.Prepared picture(int w, int h, long seed) {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        java.util.Random r = new java.util.Random(seed);
        int a = 0xff000000 | r.nextInt(0xffffff), b = 0xff000000 | r.nextInt(0xffffff);
        for (int y = 0; y < h; y++) { for (int x = 0; x < w; x++) { image.setRGB(x, y, x < 4 && y < 4 ? 0 : ((x + y) / 6) % 2 == 0 ? a : b); } }
        return DecalImages.prepare(image, DecalImages.MAX_SIDE);
    }

    private static void check(GameTestHelper helper, boolean ok, String what) { if (!ok) { helper.fail(what); } }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool")
    public static void aGoodPictureIsKeptAndGivenBack(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID me = UUID.randomUUID();
        DecalImages.Prepared p = picture(96, 48, me.getMostSignificantBits());
        try {
            DecalStore.Result r = DecalStore.store(server, me, "tester", p.sha(), p.png(), OPEN);
            check(helper, r.ok() && r.width() == 96 && r.height() == 48, "kept: " + r);
            check(helper, Arrays.equals(DecalStore.read(server, p.sha()), p.png()), "given back as it came");
            check(helper, Files.isRegularFile(DecalStore.folder(server).resolve(p.sha() + ".png")), "a file named by its SHA-256 in the world");
            check(helper, DecalStore.list(server, me).size() == 1, "listed as the player's");
            check(helper, DecalStore.store(server, me, "tester", p.sha(), p.png(), OPEN).ok(), "the same again is fine");
            check(helper, DecalStore.list(server, me).size() == 1, "and counted once");
            // a second player uploading the same picture is among its uploaders; purging the first leaves it
            UUID other = UUID.randomUUID();
            check(helper, DecalStore.store(server, other, "other", p.sha(), p.png(), OPEN).ok(), "the other's upload");
            check(helper, DecalStore.purge(server, me) == 1 && DecalStore.read(server, p.sha()) != null, "kept for the other");
            check(helper, DecalStore.purge(server, other) == 1 && DecalStore.read(server, p.sha()) == null, "gone with its last uploader");
            check(helper, !Files.exists(DecalStore.folder(server).resolve(p.sha() + ".png")), "its file deleted");
        } finally {
            DecalStore.remove(server, p.sha());
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool")
    public static void aPictureComesInChunksInOrder(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID me = UUID.randomUUID();
        DecalImages.Prepared p = picture(300, 200, me.getLeastSignificantBits());
        byte[] png = p.png();
        int chunk = 1000;
        try {
            DecalStore.Result last = null;
            for (int offset = 0; offset < png.length; offset += chunk) {
                byte[] part = Arrays.copyOfRange(png, offset, Math.min(png.length, offset + chunk));
                last = DecalNetwork.receive(server, me, "tester", new DecalNetwork.UploadPayload(p.sha(), png.length, offset, part), OPEN);
                if (offset + chunk < png.length) { check(helper, last == null, "still coming at " + offset); }
            }
            check(helper, last != null && last.ok(), "taken once all here: " + last);
            check(helper, Arrays.equals(DecalStore.read(server, p.sha()), png), "the same bytes");
            // a chunk out of order is refused and the upload dropped
            DecalImages.Prepared q = picture(64, 64, me.getMostSignificantBits() ^ 7);
            check(helper, DecalNetwork.receive(server, me, "tester", new DecalNetwork.UploadPayload(q.sha(), q.png().length, 0, Arrays.copyOf(q.png(), 100)), OPEN) == null, "started");
            DecalStore.Result skipped = DecalNetwork.receive(server, me, "tester", new DecalNetwork.UploadPayload(q.sha(), q.png().length, 500, new byte[10]), OPEN);
            check(helper, skipped != null && !skipped.ok(), "a skipped chunk is refused");
            check(helper, DecalStore.read(server, q.sha()) == null, "and nothing kept");
            DecalStore.Result big = DecalNetwork.receive(server, me, "tester", new DecalNetwork.UploadPayload(q.sha(), DecalImages.MAX_BYTES + 1, 0, new byte[10]), OPEN);
            check(helper, big != null && big.refusal() == DecalStore.Refusal.TOO_LARGE, "a size over the limit is refused at once: " + big);
        } finally {
            DecalStore.remove(server, p.sha());
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool")
    public static void whatIsNotAGoodPictureIsRefused(GameTestHelper helper) throws IOException {
        MinecraftServer server = helper.getLevel().getServer();
        UUID me = UUID.randomUUID();
        List<String> made = new ArrayList<>();
        try {
            DecalImages.Prepared p = picture(200, 100, me.getMostSignificantBits());
            made.add(p.sha());
            refused(helper, DecalStore.store(server, me, "t", p.sha(), p.png(), new DecalStore.Limits(true, 8, 100_000, p.png().length - 1, 1024)), DecalStore.Refusal.TOO_LARGE);
            refused(helper, DecalStore.store(server, me, "t", p.sha(), p.png(), new DecalStore.Limits(true, 8, 100_000, DecalImages.MAX_BYTES, 64)), DecalStore.Refusal.TOO_WIDE);
            refused(helper, DecalStore.store(server, me, "t", "0".repeat(64), p.png(), OPEN), DecalStore.Refusal.HASH);
            refused(helper, DecalStore.store(server, me, "t", p.sha(), p.png(), new DecalStore.Limits(false, 8, 100_000, DecalImages.MAX_BYTES, 1024)), DecalStore.Refusal.DISABLED);
            ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB), "jpg", jpeg);
            String jsha = DecalImages.sha256(jpeg.toByteArray());
            made.add(jsha);
            refused(helper, DecalStore.store(server, me, "t", jsha, jpeg.toByteArray(), OPEN), DecalStore.Refusal.NOT_PNG);
            refused(helper, DecalStore.store(server, me, "t", "../../level", p.png(), OPEN), DecalStore.Refusal.HASH);
            // quotas: one picture a player; as many on the server as there are now
            DecalStore.Limits one = new DecalStore.Limits(true, 1, 100_000, DecalImages.MAX_BYTES, 1024);
            check(helper, DecalStore.store(server, me, "t", p.sha(), p.png(), one).ok(), "the first");
            DecalImages.Prepared q = picture(50, 50, me.getLeastSignificantBits());
            made.add(q.sha());
            refused(helper, DecalStore.store(server, me, "t", q.sha(), q.png(), one), DecalStore.Refusal.PLAYER_QUOTA);
            int now = DecalIndex.of(server).size();
            refused(helper, DecalStore.store(server, UUID.randomUUID(), "u", q.sha(), q.png(), new DecalStore.Limits(true, 8, now, DecalImages.MAX_BYTES, 1024)), DecalStore.Refusal.SERVER_QUOTA);
            for (String sha : List.of(q.sha(), jsha)) { check(helper, DecalStore.read(server, sha) == null, "nothing refused is kept"); }
            check(helper, DecalStore.remove(server, p.sha()) && DecalStore.read(server, p.sha()) == null, "removed, it is gone");
            check(helper, !DecalStore.remove(server, p.sha()), "and cannot be removed twice");
        } finally {
            for (String sha : made) { DecalStore.remove(server, sha); }
        }
        helper.succeed();
    }

    private static void refused(GameTestHelper helper, DecalStore.Result r, DecalStore.Refusal expected) {
        if (r.ok() || r.refusal() != expected) { helper.fail("expected " + expected + ", got " + r); }
    }
}
