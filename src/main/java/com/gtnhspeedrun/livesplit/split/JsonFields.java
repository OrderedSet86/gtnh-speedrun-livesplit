package com.gtnhspeedrun.livesplit.split;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Read the fields of the milestone file and the selection file. A {@code where} argument is the file and the field,
 * such as {@code config/gtnhspeedrunlivesplit-milestones.json: milestones[3]}. The message of each exception starts
 * with {@code where} and ends with the expected form.
 */
public final class JsonFields {

    private JsonFields() {}

    /**
     * Read a route: a list of segments, each a milestone id or an object.
     *
     * <pre>
     *     ["coke_oven", {"id": "nether", "name": "-Nether"}, {"name": "Steel + Tin", "allOf": ["steel", "tin"]}]
     * </pre>
     *
     * An object has one of {@code id}, {@code allOf} and {@code anyOf}, and an optional {@code name} and {@code icon}.
     * A group of {@code allOf} or {@code anyOf} needs a name. The ids are not checked against a catalog here.
     */
    public static List<SegmentSpec> readSegments(JsonArray rawSegments, String where) throws SplitFileException {
        if (rawSegments.size() > SplitList.MAX_SPLITS) {
            throw new SplitFileException(
                where + " has " + rawSegments.size() + " segments (expected at most " + SplitList.MAX_SPLITS + ")");
        }
        final List<SegmentSpec> segments = new ArrayList<>();
        int index = 0;
        for (JsonElement rawSegment : rawSegments) {
            segments.add(readSegment(rawSegment, where + "[" + index++ + "]"));
        }
        return segments;
    }

    /**
     * Read one entry of a route. A text is a milestone id. An object has one of {@code id}, {@code allOf} and
     * {@code anyOf}.
     */
    private static SegmentSpec readSegment(JsonElement rawSegment, String where) throws SplitFileException {
        if (rawSegment.isJsonPrimitive() && rawSegment.getAsJsonPrimitive()
            .isString()) {
            return SegmentSpec.createPlainSegment(readMilestoneId(rawSegment, where));
        }
        if (!rawSegment.isJsonObject()) {
            throw new SplitFileException(where + " is not a milestone id or an object (expected \"coke_oven\")");
        }
        final JsonObject segment = rawSegment.getAsJsonObject();
        final String name = readText(segment, "name", where);
        final ItemKey icon = readIcon(segment, where);
        final int keyCount = (segment.has("id") ? 1 : 0) + (segment.has("allOf") ? 1 : 0)
            + (segment.has("anyOf") ? 1 : 0);
        if (keyCount != 1) {
            throw new SplitFileException(
                where
                    + " needs one of id, allOf and anyOf (expected {\"id\": \"nether\"} or {\"name\": \"Steel + Tin\", \"allOf\": [\"steel\", \"tin\"]})");
        }
        if (segment.has("id")) {
            return new SegmentSpec(
                name,
                icon,
                SegmentSpec.Mode.ALL_OF,
                List.of(readMilestoneId(segment.get("id"), where + ".id")));
        }
        final SegmentSpec.Mode mode = segment.has("allOf") ? SegmentSpec.Mode.ALL_OF : SegmentSpec.Mode.ANY_OF;
        final String groupKey = mode == SegmentSpec.Mode.ALL_OF ? "allOf" : "anyOf";
        if (name == null) {
            throw new SplitFileException(where + ".name is missing (expected a segment name such as \"Steel + Tin\")");
        }
        final JsonElement rawIds = segment.get(groupKey);
        if (!(rawIds.isJsonArray() && rawIds.getAsJsonArray()
            .size() > 0
            && rawIds.getAsJsonArray()
                .size() <= MilestoneCatalog.MAX_MILESTONES)) {
            throw new SplitFileException(
                where + "." + groupKey + " is not a list of milestone ids (expected [\"steel\", \"tin\"])");
        }
        final List<String> ids = new ArrayList<>();
        int index = 0;
        for (JsonElement rawId : rawIds.getAsJsonArray()) {
            ids.add(readMilestoneId(rawId, where + "." + groupKey + "[" + index++ + "]"));
        }
        return new SegmentSpec(name, icon, mode, ids);
    }

    public static String readMilestoneId(JsonElement rawId, String where) throws SplitFileException {
        if (!(rawId.isJsonPrimitive() && rawId.getAsJsonPrimitive()
            .isString())) {
            throw new SplitFileException(where + " is not a text (expected " + MilestoneCatalog.EXPECTED_ID_FORM + ")");
        }
        try {
            return MilestoneCatalog.requireValidId(rawId.getAsString());
        } catch (IllegalArgumentException exception) {
            throw new SplitFileException(where + ": " + exception.getMessage(), exception);
        }
    }

    /**
     * Read an optional text that goes to LiveSplit, or null when the text is missing. A text of control characters, or
     * over {@link SplitList#MAX_TEXT_LENGTH} characters, is rejected. FML packets and LiveSplit files then fit every
     * text.
     */
    @Nullable
    public static String readText(JsonObject object, String key, String where) throws SplitFileException {
        final JsonElement value = object.get(key);
        if (value == null) {
            return null;
        }
        final String text = readString(object, key);
        if (text == null || text.trim()
            .isEmpty()) {
            throw new SplitFileException(where + "." + key + " is not a text (expected a text such as \"Nether\")");
        }
        if (text.length() > SplitList.MAX_TEXT_LENGTH || text.chars()
            .anyMatch(Character::isISOControl)) {
            throw new SplitFileException(
                where + "."
                    + key
                    + " has control characters or is too long (expected one line of at most "
                    + SplitList.MAX_TEXT_LENGTH
                    + " characters)");
        }
        return text;
    }

    /** Read the optional {@code icon}, or null when {@code object} has no icon. */
    @Nullable
    public static ItemKey readIcon(JsonObject object, String where) throws SplitFileException {
        final JsonElement value = object.get("icon");
        if (value == null) {
            return null;
        }
        final String text = readString(object, "icon");
        if (text == null) {
            throw new SplitFileException(where + ".icon is not a text (expected " + ItemKey.EXPECTED_FORM + ")");
        }
        try {
            return ItemKey.parse(text);
        } catch (IllegalArgumentException exception) {
            throw new SplitFileException(where + ".icon: " + exception.getMessage(), exception);
        }
    }

    @Nullable
    public static String readString(JsonObject object, String key) {
        final JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive()
            && value.getAsJsonPrimitive()
                .isString() ? value.getAsString() : null;
    }
}
