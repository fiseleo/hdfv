package dev.mcmod.hdfv.paint;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/** The pictures for stickers (0.13): named by SHA-256, shrunk with their transparency, and only good PNGs taken. */
class DecalImagesTest {
    /** A test picture: a checkerboard, its top-left quarter transparent. */
    static BufferedImage checker(int w, int h) {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                boolean dark = ((x / 8) + (y / 8)) % 2 == 0;
                int argb = x < w / 2 && y < h / 2 ? 0x00000000 : dark ? 0xff202060 : 0xffe0c040;
                image.setRGB(x, y, argb);
            }
        }
        return image;
    }

    @Test void theNameIsTheSha256() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", DecalImages.sha256("abc".getBytes(StandardCharsets.US_ASCII)),
                "FIPS 180-2's example");
    }

    @Test void aBigPictureIsShrunkInProportionWithItsTransparency() throws IOException {
        DecalImages.Prepared p = DecalImages.prepare(checker(2000, 1000), DecalImages.MAX_SIDE);
        assertEquals(1024, p.width());
        assertEquals(512, p.height());
        assertEquals(DecalImages.sha256(p.png()), p.sha());
        BufferedImage back = ImageIO.read(new ByteArrayInputStream(p.png()));
        assertEquals(1024, back.getWidth());
        assertEquals(0, back.getRGB(10, 10) >>> 24, "the transparent corner stays transparent");
        assertEquals(255, back.getRGB(1000, 500) >>> 24, "the rest opaque");
        assertTrue(DecalImages.check(p.png(), p.sha(), DecalImages.MAX_BYTES, DecalImages.MAX_SIDE).ok());
        DecalImages.Prepared small = DecalImages.prepare(checker(64, 40), DecalImages.MAX_SIDE);
        assertEquals(64, small.width(), "a small picture keeps its size");
        assertEquals(40, small.height());
    }

    @Test void onlyGoodPngsAreTaken() throws IOException {
        DecalImages.Prepared p = DecalImages.prepare(checker(300, 200), DecalImages.MAX_SIDE);
        DecalImages.Checked ok = DecalImages.check(p.png(), p.sha(), DecalImages.MAX_BYTES, DecalImages.MAX_SIDE);
        assertTrue(ok.ok());
        assertEquals(300, ok.width());
        assertEquals(DecalImages.Refusal.HASH, DecalImages.check(p.png(), "0".repeat(64), DecalImages.MAX_BYTES, DecalImages.MAX_SIDE).refusal());
        assertEquals(DecalImages.Refusal.TOO_LARGE, DecalImages.check(p.png(), p.sha(), p.png().length - 1, DecalImages.MAX_SIDE).refusal());
        assertEquals(DecalImages.Refusal.TOO_WIDE, DecalImages.check(p.png(), p.sha(), DecalImages.MAX_BYTES, 256).refusal());
        // a JPEG, and a PNG's signature over garbage
        ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
        BufferedImage rgb = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(rgb, "jpg", jpeg);
        assertEquals(DecalImages.Refusal.NOT_PNG, DecalImages.check(jpeg.toByteArray(), null, DecalImages.MAX_BYTES, DecalImages.MAX_SIDE).refusal());
        byte[] fake = p.png().clone();
        for (int i = 16; i < fake.length; i++) { fake[i] = (byte) (i * 31); }
        assertEquals(DecalImages.Refusal.NOT_PNG, DecalImages.check(fake, null, DecalImages.MAX_BYTES, DecalImages.MAX_SIDE).refusal());
        assertEquals(DecalImages.Refusal.NOT_PNG, DecalImages.check(new byte[3], null, DecalImages.MAX_BYTES, DecalImages.MAX_SIDE).refusal());
    }
}
