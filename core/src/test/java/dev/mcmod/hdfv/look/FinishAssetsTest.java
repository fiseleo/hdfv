package dev.mcmod.hdfv.look;

import dev.mcmod.aerocore.api.mesh.Finish;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The finishes baked in Blender (0.11): the spec (tools/blender/finishes_spec.json), the runtime list
 * (assets/hdfv/finishes.json), the textures and the code's {@link Finishes} agree, and every texture is square, a power
 * of two, tiles without a seam, has the mean brightness part colours were tuned for, and keeps gloss in alpha.
 */
class FinishAssetsTest {
    private static final Path ROOT = Path.of(System.getProperty("hdfv.root", "..")).toAbsolutePath().normalize();
    private static final Path ASSETS = ROOT.resolve("neoforge/src/main/resources/assets/hdfv");

    @Test void specRuntimeListAndCodeAgree() throws IOException {
        String spec = Files.readString(ROOT.resolve("tools/blender/finishes_spec.json"));
        String runtime = Files.readString(ASSETS.resolve("finishes.json"));
        Set<String> inSpec = new HashSet<>();
        Matcher m = Pattern.compile("\"id\": \"([a-z_]+)\",\\s*\"tile\": ([0-9.]+)").matcher(spec);
        while (m.find()) {
            String id = "hdfv:" + m.group(1);
            inSpec.add(id);
            Finish code = Finishes.ALL.stream().filter(f -> f.id().equals(id)).findFirst().orElseThrow(() -> new AssertionError("no code finish " + id));
            assertEquals(Double.parseDouble(m.group(2)), code.tileM(), 1e-12, "tile of " + id);
            Matcher r = Pattern.compile("\"" + Pattern.quote(id) + "\": \\{[^}]*\"tile\": ([0-9.]+)").matcher(runtime);
            assertTrue(r.find(), "finishes.json has " + id);
            assertEquals(code.tileM(), Double.parseDouble(r.group(1)), 1e-12, "runtime tile of " + id);
        }
        assertEquals(Finishes.ALL.size(), inSpec.size(), "the spec and the code list the same finishes");
    }

    @Test void texturesTileSeamlesslyAtTheTunedBrightness() throws IOException {
        for (Finish f : Finishes.ALL) {
            Path png = ASSETS.resolve("textures/part/" + f.id().substring("hdfv:".length()) + ".png");
            int[][] px = readPng(Files.readAllBytes(png));
            int h = px.length, w = px[0].length;
            assertEquals(w, h, f.id() + " is square");
            assertEquals(0, w & (w - 1), f.id() + " is a power of two");
            double sum = 0, inside = 0, across = 0;
            int minAlpha = 255;
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                sum += luma(px[y][x]);
                minAlpha = Math.min(minAlpha, px[y][x] >>> 24);
                if (x + 1 < w) { inside += Math.abs(luma(px[y][x]) - luma(px[y][x + 1])); }
            }
            double mid = 0;
            for (int y = 0; y < h; y++) {
                across += Math.abs(luma(px[y][w - 1]) - luma(px[y][0]));
                mid += Math.abs(luma(px[y][w / 2 - 1]) - luma(px[y][w / 2]));
            }
            double mean = sum / (w * h), stepInside = inside / (h * (w - 1)), stepAcross = across / h, stepMid = mid / h;
            assertEquals(0.82, mean, 0.02, f.id() + " mean brightness");
            // against the mean step and the step through the middle column: a weave's tow boundary falls at the edge
            // and (with an even count across) in the middle too, and is not a seam
            assertTrue(stepAcross <= 3 * Math.max(stepInside, stepMid) + 0.01, f.id() + " has a seam: " + stepAcross + " across the edge against " + stepInside + " inside");
            assertTrue(minAlpha >= 127, f.id() + " gloss alpha stays at 0.5 or more, so cutout shaders never drop pixels");
        }
    }

    /**
     * 0.22: every finish has a beautified look (AeroCore 0.39 lays it over the rest while beautified shading is on) with
     * shading 2, and every one with a relief (all but glass) its normal map: there, the size of its texture, unbiased
     * (red and green averaging 128: a face is not tilted on the whole), inside the unit circle, tiling without a seam.
     */
    @Test void everyFinishHasABeautifiedLookAndItsNormalMap() throws IOException {
        String runtime = Files.readString(ASSETS.resolve("finishes.json"));
        int maps = 0;
        for (Finish f : Finishes.ALL) {
            String name = f.id().substring("hdfv:".length());
            Matcher block = Pattern.compile("\"" + Pattern.quote(f.id()) + "\": \\{(.*?)\\R  \\}", Pattern.DOTALL).matcher(runtime);
            assertTrue(block.find(), f.id());
            String body = block.group(1);
            Matcher b = Pattern.compile("\"beautify\": \\{([^}]*)\\}").matcher(body);
            assertTrue(b.find(), f.id() + " has a beautified look");
            assertTrue(b.group(1).contains("\"shading\": 2"), f.id() + " is beautified with shading 2");
            Matcher n = Pattern.compile("\"normal\": \"hdfv:textures/part/([a-z_]+)\\.png\"").matcher(body);
            if (!n.find()) { assertTrue(name.contains("glass"), f.id() + " has a normal map (only glass is flat)"); continue; }
            maps++;
            int[][] base = readPng(Files.readAllBytes(ASSETS.resolve("textures/part/" + name + ".png")));
            int[][] px = readPng(Files.readAllBytes(ASSETS.resolve("textures/part/" + n.group(1) + ".png")));
            int h = px.length, w = px[0].length;
            assertEquals(base.length, h, f.id() + "'s normal map is the size of its texture");
            double r = 0, g = 0, inside = 0, across = 0, mid = 0;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    double nx = ((px[y][x] >> 16) & 255) / 127.5 - 1, ny = ((px[y][x] >> 8) & 255) / 127.5 - 1;
                    r += (px[y][x] >> 16) & 255;
                    g += (px[y][x] >> 8) & 255;
                    assertTrue(nx * nx + ny * ny <= 1.03, f.id() + " normal inside the unit circle at " + x + " " + y);
                    if (x + 1 < w) { inside += slopeStep(px[y][x], px[y][x + 1]); }
                }
                across += slopeStep(px[y][w - 1], px[y][0]);
                mid += slopeStep(px[y][w / 2 - 1], px[y][w / 2]);
            }
            assertEquals(128, r / (w * h), 6, f.id() + " normal map unbiased in x");
            assertEquals(128, g / (w * h), 6, f.id() + " normal map unbiased in y");
            assertTrue(across / h <= 3 * Math.max(inside / (h * (w - 1)), mid / h) + 2, f.id() + "'s normal map has a seam");
        }
        assertTrue(maps >= 40, "normal maps: " + maps);
    }

    private static double slopeStep(int a, int b) {
        return Math.abs(((a >> 16) & 255) - ((b >> 16) & 255)) + Math.abs(((a >> 8) & 255) - ((b >> 8) & 255));
    }

    private static double luma(int argb) {
        return (0.2126 * ((argb >> 16) & 255) + 0.7152 * ((argb >> 8) & 255) + 0.0722 * (argb & 255)) / 255.0;
    }

    /** An 8-bit RGBA, non-interlaced PNG (as the Blender scripts write) as ARGB rows. */
    private static int[][] readPng(byte[] data) throws IOException {
        ByteBuffer b = ByteBuffer.wrap(data);
        b.position(8);
        int w = 0, h = 0;
        java.io.ByteArrayOutputStream idat = new java.io.ByteArrayOutputStream();
        while (b.remaining() > 12) {
            int n = b.getInt();
            byte[] tag = new byte[4];
            b.get(tag);
            byte[] body = new byte[n];
            b.get(body);
            b.getInt();
            String t = new String(tag, java.nio.charset.StandardCharsets.US_ASCII);
            if (t.equals("IHDR")) {
                ByteBuffer hb = ByteBuffer.wrap(body);
                w = hb.getInt(); h = hb.getInt();
                assertEquals(8, body[8], "8-bit");
                assertEquals(6, body[9], "RGBA");
            } else if (t.equals("IDAT")) {
                idat.write(body);
            }
        }
        Inflater inf = new Inflater();
        inf.setInput(idat.toByteArray());
        byte[] raw = new byte[h * (4 * w + 1)];
        try { int got = 0; while (got < raw.length && !inf.finished()) { got += inf.inflate(raw, got, raw.length - got); } }
        catch (DataFormatException e) { throw new IOException(e); }
        int stride = 4 * w;
        byte[] prev = new byte[stride], line = new byte[stride];
        int[][] out = new int[h][w];
        for (int y = 0; y < h; y++) {
            int filter = raw[y * (stride + 1)] & 255;
            System.arraycopy(raw, y * (stride + 1) + 1, line, 0, stride);
            for (int i = 0; i < stride; i++) {
                int a = i >= 4 ? line[i - 4] & 255 : 0, up = prev[i] & 255, c = i >= 4 ? prev[i - 4] & 255 : 0;
                int pred = switch (filter) {
                    case 0 -> 0;
                    case 1 -> a;
                    case 2 -> up;
                    case 3 -> (a + up) >> 1;
                    default -> { int p = a + up - c, pa = Math.abs(p - a), pb = Math.abs(p - up), pc = Math.abs(p - c); yield pa <= pb && pa <= pc ? a : pb <= pc ? up : c; }
                };
                line[i] = (byte) ((line[i] & 255) + pred);
            }
            for (int x = 0; x < w; x++) {
                out[y][x] = (line[4 * x + 3] & 255) << 24 | (line[4 * x] & 255) << 16 | (line[4 * x + 1] & 255) << 8 | (line[4 * x + 2] & 255);
            }
            byte[] t = prev; prev = line; line = t;
        }
        return out;
    }
}
