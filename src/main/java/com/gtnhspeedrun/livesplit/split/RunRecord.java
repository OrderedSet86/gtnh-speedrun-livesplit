package com.gtnhspeedrun.livesplit.split;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The first time the run reached each milestone, in reach order. Audit listeners record from any thread. */
public final class RunRecord {

    private final Map<String, Long> firstReachTicks = new LinkedHashMap<>();

    /** Record a reach, and return whether the reach is the milestone's first. */
    public synchronized boolean recordReach(String milestoneId, long igtTicks) {
        return firstReachTicks.putIfAbsent(milestoneId, igtTicks) == null;
    }

    public synchronized List<RunSignal.MilestoneReached> listReaches() {
        final List<RunSignal.MilestoneReached> reaches = new ArrayList<>();
        for (Map.Entry<String, Long> reach : firstReachTicks.entrySet()) {
            reaches.add(new RunSignal.MilestoneReached(reach.getKey(), reach.getValue()));
        }
        return reaches;
    }

    public synchronized void clear() {
        firstReachTicks.clear();
    }
}
