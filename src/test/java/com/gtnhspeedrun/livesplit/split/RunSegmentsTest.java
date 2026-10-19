package com.gtnhspeedrun.livesplit.split;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Milestone reaches become segment reaches on a runner's route: an {@code allOf} group in any order, an {@code anyOf}
 * group, and a plain milestone. {@link SelectionFile} reads the entry forms of the server's route.
 */
class RunSegmentsTest {

    private static final ItemKey STEEL_ICON = ItemKey.parse("gregtech:gt.metaitem.01@11305");
    private static final MilestoneCatalog CATALOG = new MilestoneCatalog(
        new SplitList.Title("GTNH", "Moon%", null),
        List.of(
            new MilestoneCatalog.Milestone("steel", "Steel", STEEL_ICON),
            new MilestoneCatalog.Milestone("tin", "Tin", null),
            new MilestoneCatalog.Milestone("nether", "Nether", null),
            new MilestoneCatalog.Milestone("moon", "Moon", null)),
        List.of(segment("nether"), segment("moon")));

    @Test
    void allOfGroupIsReachedByItsLastMilestoneInEitherOrder() {
        final int CASES = 200;
        final Random random = new Random(5L);
        final List<String> parts = new ArrayList<>(List.of("steel", "tin", "nether"));
        for (int caseNumber = 0; caseNumber < CASES; caseNumber++) {
            Collections.shuffle(parts, random);
            final RunSegments segments = RunSegments.resolve(
                CATALOG,
                List.of(new SegmentSpec("Early", null, SegmentSpec.Mode.ALL_OF, List.of("steel", "tin", "nether"))));
            final List<TimerSignal.SplitReached> reaches = new ArrayList<>();
            long ticks = 0;
            for (String part : parts) {
                ticks += 1 + random.nextInt(100);
                reaches.addAll(segments.acceptReach(part, ticks));
            }

            assertEquals(List.of(new TimerSignal.SplitReached(0, ticks)), reaches, "order " + parts);
        }
    }

    @Test
    void anyOfGroupIsReachedByItsFirstMilestoneOnce() {
        final RunSegments segments = RunSegments.resolve(
            CATALOG,
            List.of(new SegmentSpec("Metal", null, SegmentSpec.Mode.ANY_OF, List.of("steel", "tin"))));

        assertEquals(List.of(new TimerSignal.SplitReached(0, 7)), segments.acceptReach("tin", 7));
        assertEquals(List.of(), segments.acceptReach("steel", 9));
    }

    @Test
    void milestoneReachedTwiceKeepsItsFirstTime() {
        final RunSegments segments = RunSegments.resolve(CATALOG, List.of(segment("nether")));

        assertEquals(List.of(new TimerSignal.SplitReached(0, 5)), segments.acceptReach("nether", 5));
        assertEquals(List.of(), segments.acceptReach("nether", 3));
        assertEquals(
            5L,
            segments.listMilestoneRows()
                .get(2)
                .reachTicks());
    }

    @Test
    void oneMilestoneInTwoSegmentsReachesBoth() {
        final RunSegments segments = RunSegments.resolve(
            CATALOG,
            List.of(
                segment("steel"),
                new SegmentSpec("Metal", null, SegmentSpec.Mode.ANY_OF, List.of("steel", "tin"))));

        assertEquals(
            List.of(new TimerSignal.SplitReached(0, 4), new TimerSignal.SplitReached(1, 4)),
            segments.acceptReach("steel", 4));
    }

    @Test
    void segmentWithAnUnknownMilestoneIsLeftOutAndTheIdListed() {
        final RunSegments segments = RunSegments.resolve(
            CATALOG,
            List.of(
                segment("nether"),
                new SegmentSpec("Mix", null, SegmentSpec.Mode.ALL_OF, List.of("steel", "lead")),
                segment("bronze"),
                segment("moon")));

        assertEquals(List.of("lead", "bronze"), segments.listUnknownIds());
        assertEquals(2, segments.countSegments());
        assertEquals(List.of(new TimerSignal.SplitReached(1, 9)), segments.acceptReach("moon", 9));
    }

    @Test
    void segmentTakesTheMilestoneNameAndIconUnlessItHasItsOwn() {
        final SplitList splits = RunSegments
            .resolve(
                CATALOG,
                List.of(segment("steel"), new SegmentSpec("-Tin", STEEL_ICON, SegmentSpec.Mode.ALL_OF, List.of("tin"))))
            .describeSplits();

        assertEquals(CATALOG.title(), splits.title());
        assertEquals(
            List.of(new SplitList.Label("Steel", STEEL_ICON), new SplitList.Label("-Tin", STEEL_ICON)),
            splits.labels());
    }

    @Test
    void gapIsAReachedSegmentAfterAnUnreachedOne() {
        final RunSegments segments = RunSegments.resolve(CATALOG, List.of(segment("nether"), segment("moon")));

        segments.acceptReach("moon", 3);
        assertTrue(segments.hasGap());
        segments.acceptReach("nether", 4);
        assertFalse(segments.hasGap());
    }

    @Test
    void segmentRowsCountTheReachedMilestonesOfAGroup() {
        final RunSegments segments = RunSegments.resolve(
            CATALOG,
            List.of(new SegmentSpec("Steel + Tin", null, SegmentSpec.Mode.ALL_OF, List.of("steel", "tin"))));
        segments.acceptReach("tin", 2);

        assertEquals(List.of(new RunSegments.SegmentRow("Steel + Tin", 1, 2, null)), segments.listSegmentRows());
        segments.acceptReach("steel", 6);
        assertEquals(List.of(new RunSegments.SegmentRow("Steel + Tin", 2, 2, 6L)), segments.listSegmentRows());
    }

    @Test
    void noSelectionFileReadsAsNull(@TempDir Path configDir) throws SplitFileException {
        assertNull(SelectionFile.read(configDir));
    }

    @Test
    void selectionFileReadsEachEntryForm(@TempDir Path configDir) throws IOException, SplitFileException {
        writeSelection(
            configDir,
            "{\"splits\":[\"nether\",{\"id\":\"moon\",\"name\":\"-Moon\"},"
                + "{\"name\":\"Steel + Tin\",\"allOf\":[\"steel\",\"tin\"]}]}");

        assertEquals(
            List.of(
                segment("nether"),
                new SegmentSpec("-Moon", null, SegmentSpec.Mode.ALL_OF, List.of("moon")),
                new SegmentSpec("Steel + Tin", null, SegmentSpec.Mode.ALL_OF, List.of("steel", "tin"))),
            SelectionFile.read(configDir));
    }

    @Test
    void badSelectionFileIsRejectedWithTheExpectedForm(@TempDir Path configDir) throws IOException {
        final Map<String, String> expectedForms = new HashMap<>();
        expectedForms.put("[]", "expected {\"splits\": [\"coke_oven\", \"nether\"]}");
        expectedForms.put("{\"splits\":{}}", "expected {\"splits\"");
        expectedForms.put("{\"splits\":[\"Nether\"]}", "splits[0]: milestone id 'Nether' can't be read");
        expectedForms.put("{\"splits\":[{\"name\":\"n\",\"allOf\":[\"a\"],\"anyOf\":[\"b\"]}]}", "needs one of");
        expectedForms.put("{bad", "can't be read");
        for (Map.Entry<String, String> entry : expectedForms.entrySet()) {
            writeSelection(configDir, entry.getKey());

            final SplitFileException exception = assertThrows(
                SplitFileException.class,
                () -> SelectionFile.read(configDir));
            assertTrue(
                exception.getMessage()
                    .contains(entry.getValue()),
                exception.getMessage());
        }
    }

    private static SegmentSpec segment(String milestoneId) {
        return new SegmentSpec(null, null, SegmentSpec.Mode.ALL_OF, List.of(milestoneId));
    }

    private static void writeSelection(Path configDir, String fileText) throws IOException {
        Files.write(configDir.resolve(SelectionFile.FILE_NAME), fileText.getBytes(StandardCharsets.UTF_8));
    }
}
