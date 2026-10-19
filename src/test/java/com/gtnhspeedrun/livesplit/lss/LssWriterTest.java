package com.gtnhspeedrun.livesplit.lss;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * The written file has the elements LiveSplit reads, in LiveSplit's order, and each name and icon reads back
 * unchanged.
 */
class LssWriterTest {

    private static final byte[] COKE_OVEN_PNG = { 1, 2, 3 };
    private static final byte[] GAME_PNG = { 9, 8, 7, 6 };

    @Test
    void fileHasTheElementsLiveSplitReads() throws Exception {
        final Element run = parse(LssWriter.formatRun(sampleRun()));

        assertEquals("Run", run.getTagName());
        assertEquals("1.7.0", run.getAttribute("version"));
        assertEquals(
            List.of(
                "GameIcon",
                "GameName",
                "CategoryName",
                "Metadata",
                "Offset",
                "AttemptCount",
                "AttemptHistory",
                "Segments",
                "AutoSplitterSettings"),
            childNames(run));
        assertEquals("00:00:00", text(run, "Offset"));
        assertEquals("0", text(run, "AttemptCount"));
        final Element metadata = child(run, "Metadata");
        assertEquals(List.of("Run", "Platform", "Region", "Variables"), childNames(metadata));
        assertEquals("False", child(metadata, "Platform").getAttribute("usesEmulator"));
        final Element segment = (Element) run.getElementsByTagName("Segment")
            .item(0);
        assertEquals(List.of("Name", "Icon", "SplitTimes", "BestSegmentTime", "SegmentHistory"), childNames(segment));
        assertEquals("Personal Best", child(child(segment, "SplitTimes"), "SplitTime").getAttribute("name"));
    }

    @Test
    void namesAndIconsReadBackInOrder() throws Exception {
        final Element run = parse(LssWriter.formatRun(sampleRun()));

        assertEquals("GregTech: New Horizons", text(run, "GameName"));
        assertEquals("Moon%", text(run, "CategoryName"));
        assertArrayEquals(GAME_PNG, readIcon(child(run, "GameIcon")));
        final NodeList segments = run.getElementsByTagName("Segment");
        assertEquals(3, segments.getLength());
        final List<String> names = new ArrayList<>();
        for (int index = 0; index < segments.getLength(); index++) {
            names.add(text((Element) segments.item(index), "Name"));
        }
        assertEquals(List.of("Coke Oven", "Ab & <c> ]]> d", "Ñether ★ 月"), names);
        assertArrayEquals(COKE_OVEN_PNG, readIcon(child((Element) segments.item(0), "Icon")));
        assertEquals(0, readIcon(child((Element) segments.item(1), "Icon")).length);
    }

    @ParameterizedTest
    @CsvSource(
        delimiter = '|',
        value = { "Moon%|Moon%.lss", "Any%|Any%.lss", "a/b\\c:d*e?f\"g<h>i|a_b_c_d_e_f_g_h_i.lss",
            "  Moon%. . |Moon%.lss", "...|splits.lss", "/:*|splits.lss", "Ñether ★|Ñether ★.lss" })
    void fileNameIsSafeOnEveryOperatingSystem(String category, String fileName) {
        assertEquals(fileName, LssWriter.formatFileName(category));
    }

    @Test
    void writeFileCreatesTheFolder(@TempDir Path root) throws IOException {
        final Path folder = root.resolve("livesplit");

        final Path file = LssWriter.writeFile(folder, sampleRun());

        assertEquals(folder.resolve("Moon%.lss"), file);
        assertTrue(
            new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
                .startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"));
    }

    @Test
    void writeFileKeepsAnExistingFile(@TempDir Path folder) throws IOException {
        final byte[] runnerTimes = "the runner's times".getBytes(StandardCharsets.UTF_8);
        Files.write(folder.resolve("Moon%.lss"), runnerTimes);

        final Path second = LssWriter.writeFile(folder, sampleRun());
        final Path third = LssWriter.writeFile(folder, sampleRun());

        assertArrayEquals(runnerTimes, Files.readAllBytes(folder.resolve("Moon%.lss")));
        assertEquals(folder.resolve("Moon% (2).lss"), second);
        assertEquals(folder.resolve("Moon% (3).lss"), third);
    }

    private static LssRun sampleRun() {
        return new LssRun(
            "GregTech: New Horizons",
            "Moon%",
            GAME_PNG,
            List.of(
                new LssRun.Segment("Coke Oven", COKE_OVEN_PNG),
                new LssRun.Segment("Ab & <c> ]]> d", null),
                new LssRun.Segment("Ñether ★ 月", new byte[] { 4 })));
    }

    /** Decode an icon element the way livesplit-core does, or return no bytes for an empty element. */
    private static byte[] readIcon(Element icon) {
        final String content = icon.getTextContent();
        if (content.length() < 216) {
            return new byte[0];
        }
        final byte[] decoded = Base64.getDecoder()
            .decode(content.substring(212));
        return Arrays.copyOfRange(decoded, 2, decoded.length - 1);
    }

    private static Element parse(String xml) throws Exception {
        final Document document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        return document.getDocumentElement();
    }

    private static List<String> childNames(Element parent) {
        final List<String> names = new ArrayList<>();
        final NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            if (children.item(index) instanceof Element element) {
                names.add(element.getTagName());
            }
        }
        return names;
    }

    private static Element child(Element parent, String name) {
        final NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            if (children.item(index) instanceof Element element && element.getTagName()
                .equals(name)) {
                return element;
            }
        }
        throw new AssertionError(parent.getTagName() + " has no " + name);
    }

    private static String text(Element parent, String name) {
        return child(parent, name).getTextContent();
    }
}
