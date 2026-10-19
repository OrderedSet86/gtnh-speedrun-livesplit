package com.gtnhspeedrun.livesplit.lss;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Random;

import org.junit.jupiter.api.Test;

/**
 * {@link LssWriter#encodeIcon} writes the bytes desktop LiveSplit writes, and livesplit-core's reader gets the PNG back
 * at its fixed offsets: it skips 212 base64 characters, decodes the rest, drops 2 bytes in front and 1 at the end
 * (livesplit-core src/util/xml/helper.rs, {@code image}).
 */
class LssWriterIconTest {

    private static final int HEADER_BYTES = 156;
    private static final int READER_SKIPPED_CHARS = 212;

    @Test
    void iconBytesAreTheBitmapHeaderTheLengthTheMarkerThePngAndTheEnd() {
        final byte[] png = "not really a png".getBytes(StandardCharsets.US_ASCII);

        final byte[] image = Base64.getDecoder()
            .decode(LssWriter.encodeIcon(png));

        final String header = new String(Arrays.copyOf(image, HEADER_BYTES), StandardCharsets.ISO_8859_1);
        assertTrue(header.contains("System.Drawing, Version=4.0.0.0"), header);
        assertTrue(header.contains("System.Drawing.Bitmap"), header);
        assertEquals(
            png.length,
            ByteBuffer.wrap(image, HEADER_BYTES, 4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .getInt());
        assertEquals(0x02, image[HEADER_BYTES + 4]);
        assertArrayEquals(png, Arrays.copyOfRange(image, HEADER_BYTES + 5, image.length - 1));
        assertEquals(0x0B, image[image.length - 1]);
    }

    @Test
    void liveSplitCoreReaderGetsThePngBackForEveryLength() {
        final int MAX_LENGTH = 2_000;
        final Random random = new Random(1019L);
        for (int length = 0; length <= MAX_LENGTH; length++) {
            final byte[] png = new byte[length];
            random.nextBytes(png);

            final String text = LssWriter.encodeIcon(png);

            assertFalse(text.contains("\n") || text.contains("\r"), "line break at length " + length);
            assertTrue(text.length() >= 216, "the reader skips texts under 216 characters");
            final byte[] decoded = Base64.getDecoder()
                .decode(text.substring(READER_SKIPPED_CHARS));
            assertArrayEquals(png, Arrays.copyOfRange(decoded, 2, decoded.length - 1), "length " + length);
        }
    }
}
