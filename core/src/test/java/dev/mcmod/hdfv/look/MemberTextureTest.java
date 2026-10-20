package dev.mcmod.hdfv.look;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 0.22: the member blocks' textures (tools/blender/machines.py redraws each 16 px source in tools/blender/blocks16 at
 * 128 px): each is 128 x 128, open exactly where its source is (a grating's holes stay holes), and as bright on the
 * whole as its source (the opaque pixels' mean within 6 %: the relief lights and shades, it does not wash a panel out).
 */
class MemberTextureTest {
    private static final Path SOURCES = Path.of("..", "tools", "blender", "blocks16");
    private static final Path TEXTURES = Path.of("..", "neoforge", "src", "main", "resources", "assets", "hdfv", "textures", "block");
    private static final int SIZE = 128;

    @Test void everyMemberTextureKeepsItsSourcesHolesAndBrightness() throws IOException {
        List<Path> sources;
        try (Stream<Path> s = Files.list(SOURCES)) { sources = s.filter(p -> p.toString().endsWith(".png")).sorted().toList(); }
        assertTrue(sources.size() >= 300, "16 px sources: " + sources.size());
        for (Path source : sources) {
            String name = source.getFileName().toString();
            BufferedImage src = ImageIO.read(source.toFile()), out = ImageIO.read(TEXTURES.resolve(name).toFile());
            assertNotNull(out, name);
            assertEquals(SIZE, out.getWidth(), name);
            assertEquals(SIZE, out.getHeight(), name);
            int k = SIZE / src.getWidth();
            double srcSum = 0, outSum = 0;
            int srcCount = 0, outCount = 0;
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    int s = src.getRGB(x / k, y / k), o = out.getRGB(x, y);
                    boolean sOpaque = (s >>> 24) >= 128, oOpaque = (o >>> 24) >= 128;
                    assertEquals(sOpaque, oOpaque, name + ": open at " + x + ", " + y + " where its source is " + (sOpaque ? "not" : ""));
                    if (oOpaque) { outSum += luma(o); outCount++; }
                    if (sOpaque) { srcSum += luma(s); srcCount++; }
                }
            }
            if (srcCount == 0) { continue; }
            double a = srcSum / srcCount, b = outSum / outCount;
            assertTrue(Math.abs(b - a) <= 0.06 * Math.max(a, 8), name + ": mean brightness " + b + " against its source's " + a);
        }
    }

    private static double luma(int argb) { return 0.2126 * ((argb >> 16) & 255) + 0.7152 * ((argb >> 8) & 255) + 0.0722 * (argb & 255); }
}
