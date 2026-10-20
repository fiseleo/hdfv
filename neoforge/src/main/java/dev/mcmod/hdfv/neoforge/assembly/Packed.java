package dev.mcmod.hdfv.neoforge.assembly;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * A vehicle's JSON packed (0.7): a layout holds whole part designs (a body alone runs to tens of kilobytes) and a
 * saved string may not pass 64 KiB, so layouts are kept and sent deflated, at most MAX characters.
 */
public final class Packed {
    private Packed() {}

    public static final int MAX = 1 << 20, MAX_BYTES = 1 << 18;

    public static byte[] pack(String json) {
        if (json.length() > MAX) { throw new IllegalArgumentException("Vehicle JSON longer than " + MAX + " characters"); }
        Deflater d = new Deflater(Deflater.BEST_COMPRESSION);
        d.setInput(json.getBytes(StandardCharsets.UTF_8));
        d.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        while (!d.finished()) { out.write(buf, 0, d.deflate(buf)); }
        d.end();
        return out.toByteArray();
    }

    public static String unpack(byte[] packed) {
        if (packed.length == 0) { return ""; }
        Inflater i = new Inflater();
        i.setInput(packed);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        try {
            while (!i.finished()) {
                int n = i.inflate(buf);
                if (n == 0 && (i.needsInput() || i.needsDictionary())) { break; }
                out.write(buf, 0, n);
                if (out.size() > 4 * MAX) { throw new IllegalArgumentException("Vehicle JSON too long"); }
            }
        } catch (DataFormatException e) {
            throw new IllegalArgumentException("Damaged vehicle data", e);
        } finally {
            i.end();
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    public static final Codec<String> CODEC = Codec.BYTE_BUFFER.xmap(b -> {
        byte[] a = new byte[b.remaining()];
        b.duplicate().get(a);
        return unpack(a);
    }, s -> ByteBuffer.wrap(pack(s)));

    public static final StreamCodec<ByteBuf, String> STREAM_CODEC = ByteBufCodecs.byteArray(MAX_BYTES).map(Packed::unpack, Packed::pack);
}
