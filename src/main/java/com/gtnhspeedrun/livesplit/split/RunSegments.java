package com.gtnhspeedrun.livesplit.split;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * The runner's segments on one server, resolved against the server's catalog, and the milestones that the run has
 * reached. {@link #acceptReach} turns a milestone reach into the reach of each segment that is complete after the
 * milestone reach. The network thread records reaches, and the client thread reads the rows for chat.
 */
public final class RunSegments {

    private final MilestoneCatalog catalog;
    private final List<Segment> segments;
    private final List<String> unknownIds;
    // the indexes of the segments that each milestone of the route belongs to
    private final Map<String, List<Integer>> segmentsByMilestone = new HashMap<>();
    // every reached milestone of the catalog, in or out of the route
    private final Map<String, Long> reachTicks = new HashMap<>();
    private final Map<Integer, Long> segmentReachTicks = new HashMap<>();

    private RunSegments(MilestoneCatalog catalog, List<Segment> segments, List<String> unknownIds) {
        this.catalog = catalog;
        this.segments = segments;
        this.unknownIds = unknownIds;
        for (int index = 0; index < segments.size(); index++) {
            for (String id : segments.get(index)
                .milestoneIds()) {
                segmentsByMilestone.computeIfAbsent(id, key -> new ArrayList<>())
                    .add(index);
            }
        }
    }

    /**
     * Resolve {@code route} against {@code catalog}. A segment with a milestone id that the catalog lacks is left out,
     * and {@link #listUnknownIds} returns the id. A null name or icon is the name or icon of the segment's first
     * milestone.
     */
    public static RunSegments resolve(MilestoneCatalog catalog, List<SegmentSpec> route) {
        final Map<String, MilestoneCatalog.Milestone> milestonesById = new HashMap<>();
        for (MilestoneCatalog.Milestone milestone : catalog.milestones()) {
            milestonesById.put(milestone.id(), milestone);
        }
        final List<Segment> segments = new ArrayList<>();
        final Set<String> unknownIds = new LinkedHashSet<>();
        for (SegmentSpec spec : route) {
            final List<String> missing = new ArrayList<>();
            for (String id : spec.milestoneIds()) {
                if (!milestonesById.containsKey(id)) {
                    missing.add(id);
                }
            }
            if (!missing.isEmpty()) {
                unknownIds.addAll(missing);
                continue;
            }
            final MilestoneCatalog.Milestone first = milestonesById.get(
                spec.milestoneIds()
                    .get(0));
            segments.add(
                new Segment(
                    spec.name() != null ? spec.name() : first.name(),
                    spec.icon() != null ? spec.icon() : first.icon(),
                    spec.mode(),
                    spec.milestoneIds()));
        }
        return new RunSegments(catalog, List.copyOf(segments), List.copyOf(unknownIds));
    }

    /** Return the LiveSplit file's game, category, and segment names and icons. */
    public SplitList describeSplits() {
        final List<SplitList.Label> labels = new ArrayList<>();
        for (Segment segment : segments) {
            labels.add(new SplitList.Label(segment.name(), segment.icon()));
        }
        return new SplitList(catalog.title(), labels);
    }

    public List<String> listUnknownIds() {
        return unknownIds;
    }

    public int countSegments() {
        return segments.size();
    }

    /**
     * Record a milestone reach, and return the reach of each segment that is complete after the reach. A second reach
     * of a milestone returns no segment.
     */
    public synchronized List<TimerSignal.SplitReached> acceptReach(String milestoneId, long igtTicks) {
        if (reachTicks.putIfAbsent(milestoneId, igtTicks) != null) {
            return List.of();
        }
        final List<TimerSignal.SplitReached> completed = new ArrayList<>();
        for (int index : segmentsByMilestone.getOrDefault(milestoneId, List.of())) {
            if (!segmentReachTicks.containsKey(index) && isComplete(segments.get(index))) {
                segmentReachTicks.put(index, igtTicks);
                completed.add(new TimerSignal.SplitReached(index, igtTicks));
            }
        }
        return completed;
    }

    private boolean isComplete(Segment segment) {
        return switch (segment.mode()) {
            case ALL_OF -> reachTicks.keySet()
                .containsAll(segment.milestoneIds());
            case ANY_OF -> true;
        };
    }

    /** Return whether an unreached segment comes before a reached segment. */
    public synchronized boolean hasGap() {
        for (int index : segmentReachTicks.keySet()) {
            for (int earlier = 0; earlier < index; earlier++) {
                if (!segmentReachTicks.containsKey(earlier)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Return each milestone of the catalog, in catalog order, with the milestone's first reach. */
    public synchronized List<MilestoneRow> listMilestoneRows() {
        final List<MilestoneRow> rows = new ArrayList<>();
        for (MilestoneCatalog.Milestone milestone : catalog.milestones()) {
            rows.add(new MilestoneRow(milestone.id(), milestone.name(), reachTicks.get(milestone.id())));
        }
        return rows;
    }

    /** Return each segment of the route, in route order, with a count of the segment's reached milestones. */
    public synchronized List<SegmentRow> listSegmentRows() {
        final List<SegmentRow> rows = new ArrayList<>();
        for (int index = 0; index < segments.size(); index++) {
            final Segment segment = segments.get(index);
            int reachedCount = 0;
            for (String id : segment.milestoneIds()) {
                if (reachTicks.containsKey(id)) {
                    reachedCount++;
                }
            }
            rows.add(
                new SegmentRow(
                    segment.name(),
                    reachedCount,
                    segment.milestoneIds()
                        .size(),
                    segmentReachTicks.get(index)));
        }
        return rows;
    }

    /** One milestone for chat. {@code reachTicks} is null for a milestone that the run hasn't reached. */
    public record MilestoneRow(String id, String name, @Nullable Long reachTicks) {}

    /** One segment for chat. {@code reachTicks} is null for a segment that the run hasn't reached. */
    public record SegmentRow(String name, int reachedCount, int milestoneCount, @Nullable Long reachTicks) {}

    private record Segment(String name, @Nullable ItemKey icon, SegmentSpec.Mode mode, List<String> milestoneIds) {}
}
