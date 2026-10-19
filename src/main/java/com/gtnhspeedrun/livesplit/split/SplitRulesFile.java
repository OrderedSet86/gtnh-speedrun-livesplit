package com.gtnhspeedrun.livesplit.split;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

/**
 * Read the splits file. A missing file is created from the sample in the jar first.
 *
 * <pre>
 * {
 *   "start": {"event": "timing_started"},
 *   "splits": [
 *     {"name": "Nether", "event": "dim_first_visit", "match": {"dim": -1}},
 *     {"name": "Moon", "event": "dim_first_visit", "match": {"dim": 28}}
 *   ]
 * }
 * </pre>
 */
public final class SplitRulesFile {

    public static final String FILE_NAME = "gtnhspeedrunlivesplit-splits.json";
    private static final String SAMPLE_RESOURCE = "/assets/gtnhspeedrunlivesplit/sample-splits.json";

    private SplitRulesFile() {}

    public static SplitRules readOrCreate(Path configDir) throws SplitRulesException {
        final Path rulesPath = configDir.resolve(FILE_NAME);
        if (!Files.exists(rulesPath)) {
            writeSample(rulesPath);
        }
        try (Reader reader = Files.newBufferedReader(rulesPath, StandardCharsets.UTF_8)) {
            final JsonElement root = new JsonParser().parse(reader);
            if (!root.isJsonObject()) {
                throw new SplitRulesException(
                    rulesPath + " is not a JSON object (expected {\"start\": …, \"splits\": [ … ]})");
            }
            return parseRules(root.getAsJsonObject(), rulesPath);
        } catch (IOException | JsonParseException exception) {
            throw new SplitRulesException(rulesPath + " can't be read: " + exception.getMessage(), exception);
        }
    }

    private static void writeSample(Path rulesPath) throws SplitRulesException {
        try (InputStream sample = SplitRulesFile.class.getResourceAsStream(SAMPLE_RESOURCE)) {
            if (sample == null) {
                throw new SplitRulesException("the jar has no " + SAMPLE_RESOURCE);
            }
            Files.createDirectories(rulesPath.getParent());
            Files.copy(sample, rulesPath);
        } catch (IOException exception) {
            throw new SplitRulesException("can't create " + rulesPath + ": " + exception.getMessage(), exception);
        }
    }

    private static SplitRules parseRules(JsonObject root, Path rulesPath) throws SplitRulesException {
        final JsonElement rawStart = root.get("start");
        final JsonElement rawSplits = root.get("splits");
        if (rawStart == null || !rawStart.isJsonObject()) {
            throw new SplitRulesException(
                rulesPath + ": \"start\" is missing (expected {\"event\": \"timing_started\"})");
        }
        if (rawSplits == null || !rawSplits.isJsonArray()) {
            throw new SplitRulesException(rulesPath + ": \"splits\" is missing (expected a list of splits)");
        }
        final EventMatch start = parseEventMatch(rawStart.getAsJsonObject(), "start", rulesPath);
        final List<EventMatch> splits = new ArrayList<>();
        int index = 0;
        for (JsonElement rawSplit : rawSplits.getAsJsonArray()) {
            final String where = "splits[" + index++ + "]";
            if (!rawSplit.isJsonObject()) {
                throw new SplitRulesException(rulesPath + ": " + where + " is not an object (expected {\"name\": …})");
            }
            splits.add(parseEventMatch(rawSplit.getAsJsonObject(), where, rulesPath));
        }
        return new SplitRules(start, splits);
    }

    /** Parse one object of {@code name}, {@code event} and {@code match}. A missing name becomes {@code where}. */
    private static EventMatch parseEventMatch(JsonObject rawMatch, String where, Path rulesPath)
        throws SplitRulesException {
        final String eventType = readString(rawMatch, "event");
        if (eventType == null) {
            throw new SplitRulesException(
                rulesPath + ": "
                    + where
                    + ".event is missing (expected an audit event type such as \"dim_first_visit\")");
        }
        final String rawName = readString(rawMatch, "name");
        final String name = rawName == null ? where : rawName;
        final Map<String, JsonPrimitive> fields = new HashMap<>();
        final JsonElement rawFields = rawMatch.get("match");
        if (rawFields != null) {
            if (!rawFields.isJsonObject()) {
                throw new SplitRulesException(
                    rulesPath + ": " + where + ".match is not an object (expected field values such as {\"dim\": 28})");
            }
            for (Map.Entry<String, JsonElement> field : rawFields.getAsJsonObject()
                .entrySet()) {
                if (!field.getValue()
                    .isJsonPrimitive()) {
                    throw new SplitRulesException(
                        rulesPath + ": "
                            + where
                            + ".match."
                            + field.getKey()
                            + " is not a string, number or boolean (expected a value such as 28)");
                }
                fields.put(
                    field.getKey(),
                    field.getValue()
                        .getAsJsonPrimitive());
            }
        }
        return new EventMatch(name, eventType, fields);
    }

    @Nullable
    private static String readString(JsonObject object, String key) {
        final JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive()
            && value.getAsJsonPrimitive()
                .isString() ? value.getAsString() : null;
    }
}
