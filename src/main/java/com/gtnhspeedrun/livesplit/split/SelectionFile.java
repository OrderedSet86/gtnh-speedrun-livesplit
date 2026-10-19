package com.gtnhspeedrun.livesplit.split;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

/**
 * Read the runner's route from the client's config directory: which milestones of the server's catalog are the segments
 * of the runner's LiveSplit file.
 *
 * <pre>
 *     {"splits": ["coke_oven", {"id": "nether", "name": "-Nether"}, {"name": "Steel + Tin", "allOf": ["steel", "tin"]}]}
 * </pre>
 */
public final class SelectionFile {

    public static final String FILE_NAME = "gtnhspeedrunlivesplit-mysplits.json";

    private SelectionFile() {}

    /** Return the route of the file, or null when the client has no file. */
    @Nullable
    public static List<SegmentSpec> read(Path configDir) throws SplitFileException {
        final Path selectionPath = configDir.resolve(FILE_NAME);
        if (!Files.exists(selectionPath)) {
            return null;
        }
        try (Reader reader = Files.newBufferedReader(selectionPath, StandardCharsets.UTF_8)) {
            final JsonElement root = new JsonParser().parse(reader);
            final JsonElement rawSplits = root.isJsonObject() ? root.getAsJsonObject()
                .get("splits") : null;
            if (rawSplits == null || !rawSplits.isJsonArray()) {
                throw new SplitFileException(
                    selectionPath + ": \"splits\" is missing (expected {\"splits\": [\"coke_oven\", \"nether\"]})");
            }
            return JsonFields.readSegments(rawSplits.getAsJsonArray(), selectionPath + ": splits");
        } catch (IOException | JsonParseException exception) {
            throw new SplitFileException(selectionPath + " can't be read: " + exception.getMessage(), exception);
        }
    }
}
