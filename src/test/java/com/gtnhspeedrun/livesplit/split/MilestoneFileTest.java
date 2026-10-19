package com.gtnhspeedrun.livesplit.split;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** The milestone file gate: the sample is created and read, and each bad file is rejected with the expected form. */
class MilestoneFileTest {

    private static final String START = "\"start\":{\"event\":\"a\"}";

    @Test
    void missingFileGetsTheSampleAndTheSampleMatchesItsEvents(@TempDir Path configDir) throws SplitFileException {
        final MilestoneRules rules = MilestoneFile.readOrCreate(configDir);

        assertTrue(Files.exists(configDir.resolve(MilestoneFile.FILE_NAME)));
        assertEquals(
            List.of(new RunSignal.RunStarted()),
            rules.collectSignals("timing_started", parse("{\"uuid\":\"x\",\"dim\":0}"), 5));
        assertEquals(
            List.of(new RunSignal.MilestoneReached("coke_oven", 11)),
            rules.collectSignals("multiblock_formed", formed("MTECokeOven"), 11));
        assertEquals(
            List.of(new RunSignal.MilestoneReached("nether", 22)),
            rules.collectSignals("dim_first_visit", parse("{\"dim\":-1,\"cause\":\"travel\"}"), 22));
        assertEquals(
            List.of(new RunSignal.MilestoneReached("bricked_blast_furnace", 33)),
            rules.collectSignals("multiblock_formed", formed("MTEBrickedBlastFurnace"), 33));
        assertEquals(
            List.of(new RunSignal.MilestoneReached("electric_blast_furnace", 44)),
            rules.collectSignals("multiblock_formed", formed("MTEElectricBlastFurnace"), 44));
        assertEquals(
            List.of(new RunSignal.MilestoneReached("moon", 77)),
            rules.collectSignals("dim_first_visit", parse("{\"dim\":28,\"cause\":\"travel\"}"), 77));
        assertEquals(List.of(), rules.collectSignals("dim_first_visit", parse("{\"dim\":\"28\"}"), 77));
        assertEquals(List.of(), rules.collectSignals("dim_change", parse("{\"dim\":28}"), 77));
    }

    @Test
    void sampleCatalogHasTheMoonRouteWithIcons(@TempDir Path configDir) throws SplitFileException {
        final MilestoneCatalog catalog = MilestoneFile.readOrCreate(configDir)
            .catalog();

        assertEquals(
            new SplitList.Title("GregTech: New Horizons", "Moon%", ItemKey.parse("gregtech:gt.blockmachines@1000")),
            catalog.title());
        assertEquals(
            new MilestoneCatalog.Milestone("moon", "Moon", ItemKey.parse("GalacticraftCore:tile.moonBlock@4")),
            catalog.milestones()
                .get(4));
        final List<String> routeIds = catalog.defaultSplits()
            .stream()
            .map(
                segment -> segment.milestoneIds()
                    .get(0))
            .toList();
        assertEquals(
            List.of("coke_oven", "nether", "bricked_blast_furnace", "electric_blast_furnace", "moon"),
            routeIds);
    }

    @Test
    void fileWithoutGameIconsAndDefaultSplitsGetsDefaultsAndEveryMilestoneAsTheRoute(@TempDir Path configDir)
        throws IOException, SplitFileException {
        write(
            configDir,
            "{" + START
                + ",\"milestones\":[{\"id\":\"nether\",\"name\":\"Nether\",\"event\":\"b\"},"
                + "{\"id\":\"moon\",\"name\":\"Moon\",\"event\":\"b\"}]}");

        final MilestoneCatalog catalog = MilestoneFile.readOrCreate(configDir)
            .catalog();

        assertEquals(new SplitList.Title("GregTech: New Horizons", "Any%", null), catalog.title());
        assertEquals(
            List.of(
                new SegmentSpec(null, null, SegmentSpec.Mode.ALL_OF, List.of("nether")),
                new SegmentSpec(null, null, SegmentSpec.Mode.ALL_OF, List.of("moon"))),
            catalog.defaultSplits());
    }

    @Test
    void defaultSplitsReadGroupsAndNameOverrides(@TempDir Path configDir) throws IOException, SplitFileException {
        write(
            configDir,
            "{" + START
                + ",\"defaultSplits\":[{\"id\":\"steel\",\"name\":\"-Steel\"},"
                + "{\"name\":\"Steel + Tin\",\"icon\":\"minecraft:iron_ingot@0\",\"allOf\":[\"steel\",\"tin\"]},"
                + "{\"name\":\"Any\",\"anyOf\":[\"tin\"]}],"
                + "\"milestones\":[{\"id\":\"steel\",\"name\":\"Steel\",\"event\":\"b\"},"
                + "{\"id\":\"tin\",\"name\":\"Tin\",\"event\":\"b\"}]}");

        assertEquals(
            List.of(
                new SegmentSpec("-Steel", null, SegmentSpec.Mode.ALL_OF, List.of("steel")),
                new SegmentSpec(
                    "Steel + Tin",
                    ItemKey.parse("minecraft:iron_ingot@0"),
                    SegmentSpec.Mode.ALL_OF,
                    List.of("steel", "tin")),
                new SegmentSpec("Any", null, SegmentSpec.Mode.ANY_OF, List.of("tin"))),
            MilestoneFile.readOrCreate(configDir)
                .catalog()
                .defaultSplits());
    }

    @ParameterizedTest
    @CsvSource(
        delimiter = '|',
        value = { "[]|expected {\"start\"", "{\"milestones\":[]}|expected {\"event\": \"timing_started\"}",
            "{\"start\":{\"event\":\"timing_started\"}}|expected a list of milestones",
            "{\"start\":{\"event\":\"a\"},\"splits\":[]}|expected a list of milestones",
            "{\"start\":{},\"milestones\":[]}|expected an audit event type",
            "{\"start\":{\"event\":\"a\"},\"milestones\":[3]}|expected {\"id\"",
            "{\"start\":{\"event\":\"a\"},\"milestones\":[{\"name\":\"n\",\"event\":\"b\"}]}|milestones[0].id is missing",
            "{\"start\":{\"event\":\"a\"},\"milestones\":[{\"id\":\"Coke Oven\",\"name\":\"n\",\"event\":\"b\"}]}|expected an id of a-z",
            "{\"start\":{\"event\":\"a\"},\"milestones\":[{\"id\":7,\"name\":\"n\",\"event\":\"b\"}]}|milestones[0].id is not a text",
            "{\"start\":{\"event\":\"a\"},\"milestones\":[{\"id\":\"x\",\"event\":\"b\"}]}|milestones[0].name is missing",
            "{\"start\":{\"event\":\"a\"},\"milestones\":[{\"id\":\"x\",\"name\":\"n\"}]}|milestones[0].event is missing",
            "{\"start\":{\"event\":\"a\"},\"milestones\":[{\"id\":\"x\",\"name\":\"n\",\"event\":\"b\"},{\"id\":\"x\",\"name\":\"m\",\"event\":\"b\"}]}|'x' is used twice",
            "{\"start\":{\"event\":\"a\"},\"milestones\":[{\"id\":\"x\",\"name\":\"n\",\"event\":\"b\",\"match\":[1]}]}|expected field values",
            "{\"start\":{\"event\":\"a\"},\"milestones\":[{\"id\":\"x\",\"name\":\"n\",\"event\":\"b\",\"match\":{\"dim\":{}}}]}|expected a value",
            "{\"start\":{\"event\":\"a\"},\"milestones\":[{\"id\":\"x\",\"name\":\" \",\"event\":\"b\"}]}|milestones[0].name is not a text",
            "{\"start\":{\"event\":\"a\"},\"milestones\":[{\"id\":\"x\",\"name\":\"a\\nb\",\"event\":\"b\"}]}|expected one line",
            "{\"start\":{\"event\":\"a\"},\"milestones\":[{\"id\":\"x\",\"name\":\"n\",\"icon\":\"netherrack\",\"event\":\"b\"}]}|milestones[0].icon: item 'netherrack' can't be read (expected modid:name@meta",
            "{\"start\":{\"event\":\"a\"},\"milestones\":[{\"id\":\"x\",\"name\":\"n\",\"icon\":\"a:b@40000\",\"event\":\"b\"}]}|expected 0 to 32767",
            "{\"start\":{\"event\":\"a\"},\"defaultSplits\":{},\"milestones\":[]}|\"defaultSplits\" is not a list",
            "{\"start\":{\"event\":\"a\"},\"defaultSplits\":[\"moon\"],\"milestones\":[]}|names milestone 'moon', which \"milestones\" has not",
            "{\"start\":{\"event\":\"a\"},\"defaultSplits\":[3],\"milestones\":[]}|defaultSplits[0] is not a milestone id or an object",
            "{\"start\":{\"event\":\"a\"},\"defaultSplits\":[{\"name\":\"n\"}],\"milestones\":[]}|defaultSplits[0] needs one of id, allOf and anyOf",
            "{\"start\":{\"event\":\"a\"},\"defaultSplits\":[{\"id\":\"a\",\"allOf\":[\"b\"]}],\"milestones\":[]}|needs one of id, allOf and anyOf",
            "{\"start\":{\"event\":\"a\"},\"defaultSplits\":[{\"allOf\":[\"b\"]}],\"milestones\":[]}|defaultSplits[0].name is missing",
            "{\"start\":{\"event\":\"a\"},\"defaultSplits\":[{\"name\":\"n\",\"anyOf\":[]}],\"milestones\":[]}|defaultSplits[0].anyOf is not a list of milestone ids",
            "{\"start\":{\"event\":\"a\"},\"defaultSplits\":[{\"name\":\"n\",\"allOf\":[[\"b\"]]}],\"milestones\":[]}|defaultSplits[0].allOf[0] is not a text",
            "{\"game\":[],\"start\":{\"event\":\"a\"},\"milestones\":[]}|\"game\" is not an object",
            "{\"game\":{\"category\":false},\"start\":{\"event\":\"a\"},\"milestones\":[]}|game.category is not a text",
            "{not json|can't be read" })
    void badFileIsRejectedWithTheExpectedForm(String fileText, String expectedForm, @TempDir Path configDir)
        throws IOException {
        write(configDir, fileText);

        assertRejected(configDir, expectedForm);
    }

    @Test
    void oldSplitsFileWithoutAMilestoneFileIsRejectedWithTheWayToMoveIt(@TempDir Path configDir) throws IOException {
        Files.write(configDir.resolve("gtnhspeedrunlivesplit-splits.json"), "{}".getBytes(StandardCharsets.UTF_8));

        assertRejected(configDir, "is the splits file of an older version");
        assertFalse(Files.exists(configDir.resolve(MilestoneFile.FILE_NAME)));
    }

    @Test
    void tooLongNameIsRejected(@TempDir Path configDir) throws IOException {
        write(
            configDir,
            "{" + START
                + ",\"milestones\":[{\"id\":\"x\",\"name\":\""
                + "n".repeat(SplitList.MAX_TEXT_LENGTH + 1)
                + "\",\"event\":\"b\"}]}");

        assertRejected(configDir, "expected one line of at most 200 characters");
    }

    @Test
    void tooLongIdIsRejectedAndTheLongestIdIsRead(@TempDir Path configDir) throws IOException, SplitFileException {
        final String longestId = "a".repeat(MilestoneCatalog.MAX_ID_LENGTH);
        write(
            configDir,
            "{" + START + ",\"milestones\":[{\"id\":\"" + longestId + "\",\"name\":\"n\",\"event\":\"b\"}]}");
        assertEquals(
            longestId,
            MilestoneFile.readOrCreate(configDir)
                .catalog()
                .milestones()
                .get(0)
                .id());

        write(
            configDir,
            "{" + START + ",\"milestones\":[{\"id\":\"" + longestId + "a\",\"name\":\"n\",\"event\":\"b\"}]}");
        assertRejected(configDir, "expected an id of a-z");
    }

    @Test
    void tooManyMilestonesAreRejected(@TempDir Path configDir) throws IOException {
        final StringBuilder fileText = new StringBuilder("{" + START + ",\"milestones\":[");
        for (int index = 0; index <= MilestoneCatalog.MAX_MILESTONES; index++) {
            fileText.append(index == 0 ? "" : ",")
                .append("{\"id\":\"m")
                .append(index)
                .append("\",\"name\":\"n\",\"event\":\"b\"}");
        }
        write(
            configDir,
            fileText.append("]}")
                .toString());

        assertRejected(configDir, "has 1001 milestones (expected at most 1000)");
    }

    private static void assertRejected(Path configDir, String expectedForm) {
        final SplitFileException exception = assertThrows(
            SplitFileException.class,
            () -> MilestoneFile.readOrCreate(configDir));

        assertTrue(
            exception.getMessage()
                .contains(expectedForm),
            exception.getMessage());
    }

    private static void write(Path configDir, String fileText) throws IOException {
        Files.write(configDir.resolve(MilestoneFile.FILE_NAME), fileText.getBytes(StandardCharsets.UTF_8));
    }

    private static JsonObject formed(String className) {
        return parse(
            "{\"kind\":\"gt\",\"class\":\"" + className
                + "\",\"dim\":0,\"x\":1,\"y\":2,\"z\":3,\"firstOfClass\":true}");
    }

    private static JsonObject parse(String json) {
        return new JsonParser().parse(json)
            .getAsJsonObject();
    }
}
