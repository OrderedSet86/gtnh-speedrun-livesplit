package com.gtnhspeedrun.livesplit.split;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

/**
 * Read the server's milestone file. A missing file is created from the sample in the jar first.
 *
 * <pre>
 * {
 *   "game": {"name": "GregTech: New Horizons", "category": "Moon%", "icon": "gregtech:gt.blockmachines@1000"},
 *   "start": {"event": "timing_started"},
 *   "defaultSplits": ["nether", "moon"],
 *   "milestones": [
 *     {"id": "nether", "name": "Nether", "icon": "minecraft:netherrack@0", "event": "dim_first_visit", "match": {"dim": -1}},
 *     {"id": "moon", "name": "Moon", "event": "dim_first_visit", "match": {"dim": 28}}
 *   ]
 * }
 * </pre>
 *
 * {@code game}, {@code defaultSplits} and each {@code icon} are optional. An icon is an item, {@code modid:name@meta}.
 * Each entry of {@code defaultSplits} has the form of {@link JsonFields#readSegments}. Without {@code defaultSplits},
 * the route is every milestone in file order.
 */
public final class MilestoneFile {

    public static final String FILE_NAME = "gtnhspeedrunlivesplit-milestones.json";
    // the splits file of protocol 2 and older
    private static final String OLD_FILE_NAME = "gtnhspeedrunlivesplit-splits.json";
    private static final String SAMPLE_RESOURCE = "/assets/gtnhspeedrunlivesplit/sample-milestones.json";
    private static final String DEFAULT_GAME_NAME = "GregTech: New Horizons";
    private static final String DEFAULT_CATEGORY = "Any%";

    private MilestoneFile() {}

    /**
     * Read the milestone file in {@code configDir}. A missing file is created from the sample, except beside the splits
     * file of an older version, which is rejected with the way to move the splits.
     */
    public static MilestoneRules readOrCreate(Path configDir) throws SplitFileException {
        final Path rulesPath = configDir.resolve(FILE_NAME);
        if (!Files.exists(rulesPath)) {
            final Path oldPath = configDir.resolve(OLD_FILE_NAME);
            if (Files.exists(oldPath)) {
                throw new SplitFileException(
                    oldPath + " is the splits file of an older version. Move its splits into \"milestones\" of "
                        + rulesPath
                        + ", each with an \"id\", then delete the old file (expected the format in the README)");
            }
            writeSample(rulesPath);
        }
        try (Reader reader = Files.newBufferedReader(rulesPath, StandardCharsets.UTF_8)) {
            final JsonElement root = new JsonParser().parse(reader);
            if (!root.isJsonObject()) {
                throw new SplitFileException(
                    rulesPath + " is not a JSON object (expected {\"start\": …, \"milestones\": [ … ]})");
            }
            return parseRules(root.getAsJsonObject(), rulesPath);
        } catch (IOException | JsonParseException exception) {
            throw new SplitFileException(rulesPath + " can't be read: " + exception.getMessage(), exception);
        }
    }

    private static void writeSample(Path rulesPath) throws SplitFileException {
        try (InputStream sample = MilestoneFile.class.getResourceAsStream(SAMPLE_RESOURCE)) {
            if (sample == null) {
                throw new SplitFileException("the jar has no " + SAMPLE_RESOURCE);
            }
            Files.createDirectories(rulesPath.getParent());
            Files.copy(sample, rulesPath);
        } catch (IOException exception) {
            throw new SplitFileException("can't create " + rulesPath + ": " + exception.getMessage(), exception);
        }
    }

    /** Parse the file's object. The first bad field is rejected. */
    private static MilestoneRules parseRules(JsonObject root, Path rulesPath) throws SplitFileException {
        final JsonElement rawStart = root.get("start");
        final JsonElement rawMilestones = root.get("milestones");
        if (rawStart == null || !rawStart.isJsonObject()) {
            throw new SplitFileException(
                rulesPath + ": \"start\" is missing (expected {\"event\": \"timing_started\"})");
        }
        if (rawMilestones == null || !rawMilestones.isJsonArray()) {
            throw new SplitFileException(
                rulesPath + ": \"milestones\" is missing (expected a list of milestones such as [{\"id\": \"nether\", "
                    + "\"name\": \"Nether\", \"event\": \"dim_first_visit\", \"match\": {\"dim\": -1}}])");
        }
        if (rawMilestones.getAsJsonArray()
            .size() > MilestoneCatalog.MAX_MILESTONES) {
            throw new SplitFileException(
                rulesPath + ": \"milestones\" has "
                    + rawMilestones.getAsJsonArray()
                        .size()
                    + " milestones (expected at most "
                    + MilestoneCatalog.MAX_MILESTONES
                    + ")");
        }
        final SplitList.Title title = parseTitle(root.get("game"), rulesPath);
        final EventMatch start = parseEventMatch(rawStart.getAsJsonObject(), rulesPath + ": start");
        final List<MilestoneRules.Rule> rules = new ArrayList<>();
        final List<MilestoneCatalog.Milestone> milestones = new ArrayList<>();
        int index = 0;
        for (JsonElement rawMilestone : rawMilestones.getAsJsonArray()) {
            final String where = rulesPath + ": milestones[" + index++ + "]";
            if (!rawMilestone.isJsonObject()) {
                throw new SplitFileException(where + " is not an object (expected {\"id\": …})");
            }
            final JsonObject milestoneObject = rawMilestone.getAsJsonObject();
            final JsonElement rawId = milestoneObject.get("id");
            if (rawId == null) {
                throw new SplitFileException(
                    where + ".id is missing (expected " + MilestoneCatalog.EXPECTED_ID_FORM + ")");
            }
            final String id = JsonFields.readMilestoneId(rawId, where + ".id");
            final String name = JsonFields.readText(milestoneObject, "name", where);
            if (name == null) {
                throw new SplitFileException(where + ".name is missing (expected a milestone name such as \"Nether\")");
            }
            milestones.add(new MilestoneCatalog.Milestone(id, name, JsonFields.readIcon(milestoneObject, where)));
            rules.add(new MilestoneRules.Rule(id, parseEventMatch(milestoneObject, where)));
        }
        rejectDuplicateIds(milestones, rulesPath);
        final List<SegmentSpec> defaultSplits = parseDefaultSplits(root.get("defaultSplits"), milestones, rulesPath);
        return new MilestoneRules(start, rules, new MilestoneCatalog(title, milestones, defaultSplits));
    }

    /** Parse the optional {@code game} object. Each missing field gets a default. */
    private static SplitList.Title parseTitle(@Nullable JsonElement rawGame, Path rulesPath) throws SplitFileException {
        if (rawGame == null) {
            return new SplitList.Title(DEFAULT_GAME_NAME, DEFAULT_CATEGORY, null);
        }
        if (!rawGame.isJsonObject()) {
            throw new SplitFileException(
                rulesPath + ": \"game\" is not an object (expected {\"name\": …, \"category\": …, \"icon\": …})");
        }
        final JsonObject game = rawGame.getAsJsonObject();
        final String name = JsonFields.readText(game, "name", rulesPath + ": game");
        final String category = JsonFields.readText(game, "category", rulesPath + ": game");
        return new SplitList.Title(
            name == null ? DEFAULT_GAME_NAME : name,
            category == null ? DEFAULT_CATEGORY : category,
            JsonFields.readIcon(game, rulesPath + ": game"));
    }

    /** Parse one object of {@code event} and {@code match}. */
    private static EventMatch parseEventMatch(JsonObject rawMatch, String where) throws SplitFileException {
        final String eventType = JsonFields.readString(rawMatch, "event");
        if (eventType == null) {
            throw new SplitFileException(
                where + ".event is missing (expected an audit event type such as \"dim_first_visit\")");
        }
        final Map<String, JsonPrimitive> fields = new HashMap<>();
        final JsonElement rawFields = rawMatch.get("match");
        if (rawFields != null) {
            if (!rawFields.isJsonObject()) {
                throw new SplitFileException(
                    where + ".match is not an object (expected field values such as {\"dim\": 28})");
            }
            for (Map.Entry<String, JsonElement> field : rawFields.getAsJsonObject()
                .entrySet()) {
                if (!field.getValue()
                    .isJsonPrimitive()) {
                    throw new SplitFileException(
                        where + ".match."
                            + field.getKey()
                            + " is not a string, number or boolean (expected a value such as 28)");
                }
                fields.put(
                    field.getKey(),
                    field.getValue()
                        .getAsJsonPrimitive());
            }
        }
        return new EventMatch(eventType, fields);
    }

    private static void rejectDuplicateIds(List<MilestoneCatalog.Milestone> milestones, Path rulesPath)
        throws SplitFileException {
        final Set<String> ids = new HashSet<>();
        for (MilestoneCatalog.Milestone milestone : milestones) {
            if (!ids.add(milestone.id())) {
                throw new SplitFileException(
                    rulesPath + ": milestone id '" + milestone.id() + "' is used twice (expected each id once)");
            }
        }
    }

    /** Parse the optional {@code defaultSplits}. Each id must be a milestone of the file. */
    private static List<SegmentSpec> parseDefaultSplits(@Nullable JsonElement rawSplits,
        List<MilestoneCatalog.Milestone> milestones, Path rulesPath) throws SplitFileException {
        if (rawSplits == null) {
            final List<SegmentSpec> everyMilestone = new ArrayList<>();
            for (MilestoneCatalog.Milestone milestone : milestones) {
                everyMilestone.add(SegmentSpec.createPlainSegment(milestone.id()));
            }
            return everyMilestone;
        }
        if (!rawSplits.isJsonArray()) {
            throw new SplitFileException(
                rulesPath
                    + ": \"defaultSplits\" is not a list (expected milestone ids such as [\"nether\", \"moon\"])");
        }
        final List<SegmentSpec> segments = JsonFields
            .readSegments(rawSplits.getAsJsonArray(), rulesPath + ": defaultSplits");
        final Set<String> ids = new HashSet<>();
        for (MilestoneCatalog.Milestone milestone : milestones) {
            ids.add(milestone.id());
        }
        for (SegmentSpec segment : segments) {
            for (String id : segment.milestoneIds()) {
                if (!ids.contains(id)) {
                    throw new SplitFileException(
                        rulesPath + ": \"defaultSplits\" names milestone '"
                            + id
                            + "', which \"milestones\" has not (expected an id of \"milestones\")");
                }
            }
        }
        return segments;
    }
}
