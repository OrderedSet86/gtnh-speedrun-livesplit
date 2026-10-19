package com.gtnhspeedrun.livesplit.lss;

import java.util.List;

import javax.annotation.Nullable;

/**
 * The content of a new LiveSplit file: the game, the category, and each segment's name and icon. The file has no
 * times. The runner's first run sets the Personal Best.
 *
 * @param gameIconPng a PNG image, or null for no game icon
 */
public record LssRun(String gameName, String category, @Nullable byte[] gameIconPng, List<Segment> segments) {

    public LssRun {
        segments = List.copyOf(segments);
    }

    /** @param iconPng a PNG image, or null for no icon */
    public record Segment(String name, @Nullable byte[] iconPng) {}
}
