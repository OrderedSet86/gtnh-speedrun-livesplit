package com.gtnhspeedrun.livesplit.split;

import java.util.List;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

/**
 * The run's milestones for the clients: the game, the category, each milestone's id, name and icon, and the server's
 * default route. Each milestone of a runner's route is an id of the catalog.
 *
 * @param defaultSplits the route of a client with no selection file
 */
public record MilestoneCatalog(SplitList.Title title, List<Milestone> milestones, List<SegmentSpec> defaultSplits) {

    public static final int MAX_MILESTONES = 1_000;
    public static final int MAX_ID_LENGTH = 64;
    public static final String EXPECTED_ID_FORM = "an id of a-z, 0-9, '_', '.' and '-' such as \"coke_oven\"";

    // matches:
    // `coke_oven`
    // `gt.ebf-1`
    // rejects:
    // `Coke Oven`
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]{1," + MAX_ID_LENGTH + "}");

    public MilestoneCatalog {
        milestones = List.copyOf(milestones);
        defaultSplits = List.copyOf(defaultSplits);
    }

    /** Return {@code rawId}, or throw an IllegalArgumentException with the expected form in the message. */
    public static String requireValidId(String rawId) {
        if (!ID.matcher(rawId)
            .matches()) {
            throw new IllegalArgumentException(
                "milestone id '" + rawId + "' can't be read (expected " + EXPECTED_ID_FORM + ")");
        }
        return rawId;
    }

    /** One milestone's id, name and icon. The icon is null for a milestone with no icon. */
    public record Milestone(String id, String name, @Nullable ItemKey icon) {

        public Milestone {
            requireValidId(id);
        }
    }
}
