package com.gtnhspeedrun.livesplit.lss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Random;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

/** A PNG from {@link PngEncoder} decodes to the same pixels, alpha included. */
class PngEncoderTest {

    @Test
    void decodedPngHasTheSamePixels() throws IOException {
        final int CASES = 50;
        final int MAX_SIDE = 70;
        final Random random = new Random(64L);
        for (int caseNumber = 0; caseNumber < CASES; caseNumber++) {
            final int width = 1 + random.nextInt(MAX_SIDE);
            final int height = 1 + random.nextInt(MAX_SIDE);
            final byte[] rgba = new byte[width * height * 4];
            random.nextBytes(rgba);

            final BufferedImage image = ImageIO.read(new ByteArrayInputStream(PngEncoder.encode(width, height, rgba)));

            assertNotNull(image);
            assertEquals(width, image.getWidth());
            assertEquals(height, image.getHeight());
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    final int index = (y * width + x) * 4;
                    final int expected = (rgba[index + 3] & 0xFF) << 24 | (rgba[index] & 0xFF) << 16
                        | (rgba[index + 1] & 0xFF) << 8
                        | (rgba[index + 2] & 0xFF);
                    assertEquals(expected, image.getRGB(x, y), "pixel " + x + "," + y + " of " + width + "x" + height);
                }
            }
        }
    }

    @Test
    void pixelDataOfTheWrongSizeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> PngEncoder.encode(2, 2, new byte[15]));
        assertThrows(IllegalArgumentException.class, () -> PngEncoder.encode(0, 2, new byte[0]));
    }
}
