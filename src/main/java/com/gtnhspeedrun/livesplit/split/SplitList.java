package com.gtnhspeedrun.livesplit.split;

import java.util.List;

import javax.annotation.Nullable;

/**
 * What a LiveSplit file needs from the splits file: the game, the category, and each split's name and icon. The
 * server sends this to each client, and the client's {@code /livesplit export} writes it as a LiveSplit file.
 *
 * @param labels in the order of the splits
 */
public record SplitList(Title title, List<Label> labels) {

    public static final int MAX_TEXT_LENGTH = 200;
    public static final int MAX_SPLITS = 1_000;

    public SplitList {
        labels = List.copyOf(labels);
    }

    /** The run's game and category. A null icon leaves LiveSplit's game icon empty. */
    public record Title(String gameName, String category, @Nullable ItemKey icon) {}

    /** One split's name and icon. A null icon leaves the split without one. */
    public record Label(String name, @Nullable ItemKey icon) {}
}
