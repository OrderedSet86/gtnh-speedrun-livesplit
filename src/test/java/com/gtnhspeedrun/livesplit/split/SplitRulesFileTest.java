package com.gtnhspeedrun.livesplit.split;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

/** The splits file gate: the sample is created and read, and each bad file is rejected with the expected form. */
class SplitRulesFileTest {

    @Test
    void missingFileGetsTheSampleAndTheSampleMatchesItsEvents(@TempDir Path configDir) throws SplitRulesException {
        final SplitRules rules = SplitRulesFile.readOrCreate(configDir);

        assertTrue(Files.exists(configDir.resolve(SplitRulesFile.FILE_NAME)));
        assertEquals(
            List.of(new TimerSignal.RunStarted()),
            rules.collectSignals("timing_started", parse("{\"uuid\":\"x\",\"dim\":0}"), 5));
        assertEquals(
            List.of(new TimerSignal.SplitReached(1, 77)),
            rules.collectSignals("dim_first_visit", parse("{\"dim\":28,\"cause\":\"travel\"}"), 77));
        assertEquals(List.of(), rules.collectSignals("dim_first_visit", parse("{\"dim\":\"28\"}"), 77));
        assertEquals(List.of(), rules.collectSignals("dim_change", parse("{\"dim\":28}"), 77));
    }

    @ParameterizedTest
    @CsvSource(
        delimiter = '|',
        value = { "[]|expected {\"start\"", "{\"splits\":[]}|expected {\"event\": \"timing_started\"}",
            "{\"start\":{\"event\":\"timing_started\"}}|expected a list of splits",
            "{\"start\":{},\"splits\":[]}|expected an audit event type",
            "{\"start\":{\"event\":\"a\"},\"splits\":[3]}|expected {\"name\"",
            "{\"start\":{\"event\":\"a\"},\"splits\":[{\"event\":\"b\",\"match\":[1]}]}|expected field values",
            "{\"start\":{\"event\":\"a\"},\"splits\":[{\"event\":\"b\",\"match\":{\"dim\":{}}}]}|expected a value",
            "{not json|can't be read" })
    void badFileIsRejectedWithTheExpectedForm(String fileText, String expectedForm, @TempDir Path configDir)
        throws IOException {
        Files.write(configDir.resolve(SplitRulesFile.FILE_NAME), fileText.getBytes(StandardCharsets.UTF_8));

        final SplitRulesException exception = assertThrows(
            SplitRulesException.class,
            () -> SplitRulesFile.readOrCreate(configDir));

        assertTrue(
            exception.getMessage()
                .contains(expectedForm),
            exception.getMessage());
    }

    private static JsonObject parse(String json) {
        return new JsonParser().parse(json)
            .getAsJsonObject();
    }
}
