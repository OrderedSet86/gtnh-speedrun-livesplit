package com.gtnhspeedrun.livesplit.lss;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Base64;

import javax.annotation.Nullable;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

/**
 * Write a LiveSplit splits file ({@code .lss}) for desktop LiveSplit and LiveSplit One. The file says format version
 * 1.7.0, and it has the elements that livesplit-core's reader (src/run/parser/livesplit.rs) reads for that version.
 * LiveSplit One reads files with livesplit-core.
 *
 * <pre>
 * &lt;Run version="1.7.0"&gt;
 *   &lt;GameIcon&gt;&lt;![CDATA[AAEAAAD/////…]]&gt;&lt;/GameIcon&gt;
 *   &lt;GameName&gt;GregTech: New Horizons&lt;/GameName&gt;
 *   &lt;CategoryName&gt;Moon%&lt;/CategoryName&gt;
 *   …
 *   &lt;Segments&gt;
 *     &lt;Segment&gt;
 *       &lt;Name&gt;Coke Oven&lt;/Name&gt;
 *       &lt;Icon&gt;&lt;![CDATA[AAEAAAD/////…]]&gt;&lt;/Icon&gt;
 *       &lt;SplitTimes&gt;&lt;SplitTime name="Personal Best" /&gt;&lt;/SplitTimes&gt;
 *       …
 * </pre>
 */
public final class LssWriter {

    public static final String FORMAT_VERSION = "1.7.0";

    // The .NET BinaryFormatter header of a System.Drawing.Bitmap, 156 bytes, which ends with the record of a byte
    // array. Desktop LiveSplit saves each image this way, and livesplit-core (src/run/saver/livesplit.rs) copies the
    // same bytes. The array's length, a byte-type marker, the PNG and an end-of-message byte follow
    private static final String IMAGE_HEADER_BASE64 = "AAEAAAD/////AQAAAAAAAAAMAgAAAFFTeXN0ZW0uRHJhd2luZywgVmVyc2lvbj00LjAuMC4wLCBDdWx0dXJlPW5ldXRyYWwsIFB1YmxpY0tleVRva2VuPWIwM2Y1ZjdmMTFkNTBhM2EFAQAAABVTeXN0ZW0uRHJhd2luZy5CaXRtYXABAAAABERhdGEHAgIAAAAJAwAAAA8DAAAA";
    private static final byte BYTE_TYPE_MARKER = 0x02;
    private static final byte MESSAGE_END = 0x0B;
    private static final String FALLBACK_FILE_STEM = "splits";

    private LssWriter() {}

    /** Encode a PNG as desktop LiveSplit stores an image: base64 of the BinaryFormatter bytes, on one line. */
    public static String encodeIcon(byte[] png) {
        final byte[] header = Base64.getDecoder()
            .decode(IMAGE_HEADER_BASE64);
        final ByteBuffer image = ByteBuffer.allocate(header.length + Integer.BYTES + 1 + png.length + 1)
            .order(ByteOrder.LITTLE_ENDIAN);
        image.put(header)
            .putInt(png.length)
            .put(BYTE_TYPE_MARKER)
            .put(png)
            .put(MESSAGE_END);
        return Base64.getEncoder()
            .encodeToString(image.array());
    }

    public static String formatRun(LssRun run) {
        final StringWriter text = new StringWriter();
        try {
            final IndentedXml xml = new IndentedXml(
                XMLOutputFactory.newInstance()
                    .createXMLStreamWriter(text));
            xml.raw.writeStartDocument("UTF-8", "1.0");
            xml.open("Run");
            xml.raw.writeAttribute("version", FORMAT_VERSION);
            xml.image("GameIcon", run.gameIconPng());
            xml.leaf("GameName", run.gameName());
            xml.leaf("CategoryName", run.category());
            xml.open("Metadata");
            xml.empty("Run");
            xml.raw.writeAttribute("id", "");
            xml.empty("Platform");
            xml.raw.writeAttribute("usesEmulator", "False");
            xml.empty("Region");
            xml.empty("Variables");
            xml.close();
            xml.leaf("Offset", "00:00:00");
            xml.leaf("AttemptCount", "0");
            xml.empty("AttemptHistory");
            xml.open("Segments");
            for (LssRun.Segment segment : run.segments()) {
                xml.open("Segment");
                xml.leaf("Name", segment.name());
                xml.image("Icon", segment.iconPng());
                xml.open("SplitTimes");
                xml.empty("SplitTime");
                xml.raw.writeAttribute("name", "Personal Best");
                xml.close();
                xml.empty("BestSegmentTime");
                xml.empty("SegmentHistory");
                xml.close();
            }
            xml.close();
            xml.empty("AutoSplitterSettings");
            xml.close();
            xml.raw.writeCharacters("\n");
            xml.raw.writeEndDocument();
            xml.raw.close();
        } catch (XMLStreamException exception) {
            // a StringWriter doesn't fail, and the texts were checked when the splits file was read
            throw new IllegalStateException("can't write the LiveSplit file: " + exception.getMessage(), exception);
        }
        return text.toString();
    }

    /**
     * Return a file name for the category that every operating system accepts, such as {@code Moon%.lss}. A category
     * with no usable character gets {@code splits.lss}.
     */
    public static String formatFileName(String category) {
        return formatFileStem(category) + ".lss";
    }

    private static String formatFileStem(String category) {
        final int MAX_STEM_LENGTH = 100;
        String stem = category.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_")
            .trim();
        // Windows drops trailing dots and spaces
        stem = stem.replaceAll("[. ]+$", "");
        if (stem.length() > MAX_STEM_LENGTH) {
            stem = stem.substring(0, MAX_STEM_LENGTH)
                .trim();
        }
        if (stem.replace("_", "")
            .isEmpty()) {
            stem = FALLBACK_FILE_STEM;
        }
        return stem;
    }

    /** Write the run to {@code folder}, which is created when missing, and return the file's path. */
    /**
     * Write the run to a new file in {@code folder}, which is created when missing, and return the file's path. An
     * existing file holds the runner's times, so it is never replaced: the second export of {@code Moon%} goes to
     * {@code Moon% (2).lss}.
     */
    public static Path writeFile(Path folder, LssRun run) throws IOException {
        final int MAX_NUMBER = 1_000;
        Files.createDirectories(folder);
        final String stem = formatFileStem(run.category());
        final byte[] content = formatRun(run).getBytes(StandardCharsets.UTF_8);
        for (int number = 1; number <= MAX_NUMBER; number++) {
            final Path file = folder.resolve(number == 1 ? stem + ".lss" : stem + " (" + number + ").lss");
            try {
                Files.write(file, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                return file;
            } catch (FileAlreadyExistsException exception) {
                // try the next number
            }
        }
        throw new IOException(
            folder + " already has "
                + MAX_NUMBER
                + " files for "
                + stem
                + " (expected a free name such as "
                + stem
                + " (2).lss)");
    }

    /** An XML writer that puts each element on its own line, indented two spaces per level. */
    private static final class IndentedXml {

        private final XMLStreamWriter raw;
        private int depth;

        private IndentedXml(XMLStreamWriter raw) {
            this.raw = raw;
        }

        private void open(String name) throws XMLStreamException {
            newLine();
            raw.writeStartElement(name);
            depth++;
        }

        private void close() throws XMLStreamException {
            depth--;
            newLine();
            raw.writeEndElement();
        }

        private void empty(String name) throws XMLStreamException {
            newLine();
            raw.writeEmptyElement(name);
        }

        private void leaf(String name, String text) throws XMLStreamException {
            newLine();
            raw.writeStartElement(name);
            raw.writeCharacters(text);
            raw.writeEndElement();
        }

        private void image(String name, @Nullable byte[] png) throws XMLStreamException {
            if (png == null) {
                empty(name);
                return;
            }
            newLine();
            raw.writeStartElement(name);
            raw.writeCData(encodeIcon(png));
            raw.writeEndElement();
        }

        private void newLine() throws XMLStreamException {
            final StringBuilder indent = new StringBuilder("\n");
            for (int level = 0; level < depth; level++) {
                indent.append("  ");
            }
            raw.writeCharacters(indent.toString());
        }
    }
}
