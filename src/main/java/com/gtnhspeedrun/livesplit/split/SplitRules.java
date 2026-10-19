package com.gtnhspeedrun.livesplit.split;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.gson.JsonObject;

/**
 * The events that start the timer and reach each split.
 *
 * @param splits in the order of the splits in the runner's LiveSplit file
 */
public record SplitRules(EventMatch start, List<EventMatch> splits) {

    public SplitRules {
        splits = List.copyOf(splits);
    }

    public Set<String> collectEventTypes() {
        final Set<String> types = new HashSet<>();
        types.add(start.eventType());
        for (EventMatch split : splits) {
            types.add(split.eventType());
        }
        return types;
    }

    /** Return a signal for each rule that one logged event matches. Most events match none. */
    public List<TimerSignal> collectSignals(String type, JsonObject data, long igtTicks) {
        final List<TimerSignal> signals = new ArrayList<>();
        if (start.matches(type, data)) {
            signals.add(new TimerSignal.RunStarted());
        }
        for (int index = 0; index < splits.size(); index++) {
            if (splits.get(index)
                .matches(type, data)) {
                signals.add(new TimerSignal.SplitReached(index, igtTicks));
            }
        }
        return signals;
    }
}
