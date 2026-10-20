package dev.mcmod.hdfv.paint;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Iterator;

/**
 * 0.13: the pictures players upload for stickers (痛車貼紙). The client makes one from an image file (PNG, JPEG or
 * anything ImageIO reads): shrunk to at most a longest side, kept with its transparency, written as PNG and named by
 * its SHA-256. The server takes only what checks out: a PNG (by its signature and its decoder) of at most a size in
 * bytes and a longest side, whose SHA-256 is the name it came with. Pure Java (javax.imageio): no game classes.
 */
public final class DecalImages {
    private DecalImages() {}

    /** The defaults: a longest side of 1024 px, a megabyte. */
    public static final int MAX_SIDE = 1024, MAX_BYTES = 1 << 20;
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};

    /** Why a picture is refused (null: taken). */
    public enum Refusal { NOT_PNG, TOO_LARGE, TOO_WIDE, HASH }

    /** A picture checked: its size, or why it is refused. */
    public record Checked(Refusal refusal, int width, int height) {
        public boolean ok() { return refusal == null; }
    }

    /** A picture made for upload: its PNG, name and size. */
    public record Prepared(byte[] png, String sha, int width, int height) {}

    /** The SHA-256 of bytes, as 64 lower-case hex digits (a sticker's image name). */
    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is part of every Java", e);
        }
    }

    /**
     * Checks a picture as the server takes it: the PNG signature, at most maxBytes, its SHA-256 the name (when one is
     * given), decoded by the PNG reader alone with its size read from the header first (a huge one is refused before
     * it is decoded), at most maxSide a side.
     */
    public static Checked check(byte[] png, String sha, int maxBytes, int maxSide) {
        if (png.length > maxBytes) { return new Checked(Refusal.TOO_LARGE, 0, 0); }
        if (png.length < PNG.length || !java.util.Arrays.equals(png, 0, PNG.length, PNG, 0, PNG.length)) { return new Checked(Refusal.NOT_PNG, 0, 0); }
        if (sha != null && !sha.equals(sha256(png))) { return new Checked(Refusal.HASH, 0, 0); }
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(png))) {
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("png");
            if (in == null || !readers.hasNext()) { return new Checked(Refusal.NOT_PNG, 0, 0); }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                int w = reader.getWidth(0), h = reader.getHeight(0);
                if (w < 1 || h < 1) { return new Checked(Refusal.NOT_PNG, w, h); }
                if (w > maxSide || h > maxSide) { return new Checked(Refusal.TOO_WIDE, w, h); }
                BufferedImage image = reader.read(0);
                if (image == null) { return new Checked(Refusal.NOT_PNG, w, h); }
                return new Checked(null, image.getWidth(), image.getHeight());
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            return new Checked(Refusal.NOT_PNG, 0, 0);
        }
    }

    /** A picture for upload from an image file (any format ImageIO reads); IOException when it is not one. */
    public static Prepared prepare(Path file, int maxSide) throws IOException {
        BufferedImage image;
        try (InputStream in = Files.newInputStream(file)) { image = ImageIO.read(in); }
        if (image == null) { throw new IOException("Not an image ImageIO reads: " + file.getFileName()); }
        return prepare(image, maxSide);
    }

    /** A picture for upload: shrunk (bicubic, kept in proportion) to at most maxSide a side, as ARGB, written as PNG. */
    public static Prepared prepare(BufferedImage image, int maxSide) {
        int w = image.getWidth(), h = image.getHeight();
        double scale = Math.min(1, (double) maxSide / Math.max(w, h));
        int tw = Math.max(1, (int) Math.round(w * scale)), th = Math.max(1, (int) Math.round(h * scale));
        BufferedImage out = new BufferedImage(tw, th, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(image, 0, 0, tw, th, null);
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            if (!ImageIO.write(out, "png", bytes)) { throw new IllegalStateException("No PNG writer"); }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        byte[] png = bytes.toByteArray();
        return new Prepared(png, sha256(png), tw, th);
    }
}
