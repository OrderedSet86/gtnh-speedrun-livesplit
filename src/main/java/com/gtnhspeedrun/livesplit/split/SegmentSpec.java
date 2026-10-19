package com.gtnhspeedrun.livesplit.split;

import java.util.List;

import javax.annotation.Nullable;

/**
 * One LiveSplit segment of a route: the milestones that reach the segment, and the segment's name and icon. A null
 * name or icon on a segment of one milestone is the milestone's name or icon.
 *
 * <pre>
 *     "coke_oven"
 *     {"id": "nether", "name": "-Nether"}
 *     {"name": "Steel + Tin", "allOf": ["steel", "tin"]}
 * </pre>
 */
public record SegmentSpec(@Nullable String name, @Nullable ItemKey icon, Mode mode, List<String> milestoneIds) {

    private static final @Nullable String MILESTONE_NAME = null;
    private static final @Nullable ItemKey MILESTONE_ICON = null;

    public SegmentSpec {
        milestoneIds = List.copyOf(milestoneIds);
        if (milestoneIds.isEmpty()) {
            throw new IllegalArgumentException("a segment needs at least one milestone (expected [\"coke_oven\"])");
        }
    }

    /** Return the segment of one milestone, with the milestone's name and icon. */
    public static SegmentSpec createPlainSegment(String milestoneId) {
        return new SegmentSpec(MILESTONE_NAME, MILESTONE_ICON, Mode.ALL_OF, List.of(milestoneId));
    }

    /** How the milestones of a segment reach the segment. */
    public enum Mode {

        /** The last milestone to be reached reaches the segment, at that milestone's time. */
        ALL_OF,
        /** The first milestone to be reached reaches the segment. */
        ANY_OF
    }
}
