package com.gtnhspeedrun.livesplit.lss;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;

/**
 * Encode RGBA pixels as a PNG, without AWT. A client reads the pixels of an icon from OpenGL and has no other use for
 * AWT's image classes.
 */
public final class PngEncoder {

    private static final byte[] SIGNATURE = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n' };
    private static final int BIT_DEPTH = 8;
    private static final int COLOR_TYPE_RGBA = 6;
    private static final int FILTER_NONE = 0;

    private PngEncoder() {}

    /**
     * @param rgba four bytes per pixel, red, green, blue and alpha, row by row from the top-left pixel
     */
    public static byte[] encode(int width, int height, byte[] rgba) {
        if (width <= 0 || height <= 0 || rgba.length != width * height * 4) {
            throw new IllegalArgumentException(
                "pixel data of " + rgba.length
                    + " bytes for "
                    + width
                    + "x"
                    + height
                    + " (expected width × height × 4)");
        }
        try {
            final ByteArrayOutputStream png = new ByteArrayOutputStream();
            final DataOutputStream out = new DataOutputStream(png);
            out.write(SIGNATURE);

            final ByteArrayOutputStream header = new ByteArrayOutputStream();
            final DataOutputStream headerData = new DataOutputStream(header);
            headerData.writeInt(width);
            headerData.writeInt(height);
            headerData.writeByte(BIT_DEPTH);
            headerData.writeByte(COLOR_TYPE_RGBA);
            headerData.writeByte(0); // compression: deflate
            headerData.writeByte(0); // filter method: adaptive
            headerData.writeByte(0); // no interlace
            writeChunk(out, "IHDR", header.toByteArray());

            final ByteArrayOutputStream compressed = new ByteArrayOutputStream();
            try (DeflaterOutputStream deflater = new DeflaterOutputStream(compressed)) {
                final int rowBytes = width * 4;
                for (int row = 0; row < height; row++) {
                    deflater.write(FILTER_NONE);
                    deflater.write(rgba, row * rowBytes, rowBytes);
                }
            }
            writeChunk(out, "IDAT", compressed.toByteArray());
            writeChunk(out, "IEND", new byte[0]);
            out.flush();
            return png.toByteArray();
        } catch (IOException exception) {
            // in-memory streams don't fail
            throw new IllegalStateException("can't encode a PNG: " + exception.getMessage(), exception);
        }
    }

    private static void writeChunk(DataOutputStream out, String type, byte[] data) throws IOException {
        final byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        final CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        out.writeInt(data.length);
        out.write(typeBytes);
        out.write(data);
        out.writeInt((int) crc.getValue());
    }
}
