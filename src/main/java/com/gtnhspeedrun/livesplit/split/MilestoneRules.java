package com.gtnhspeedrun.livesplit.split;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.gson.JsonObject;

/** The events that start the timer and reach each milestone, with the catalog that the clients get. */
public record MilestoneRules(EventMatch start, List<Rule> rules, MilestoneCatalog catalog) {

    public MilestoneRules {
        rules = List.copyOf(rules);
    }

    public Set<String> collectEventTypes() {
        final Set<String> types = new HashSet<>();
        types.add(start.eventType());
        for (Rule rule : rules) {
            types.add(
                rule.match()
                    .eventType());
        }
        return types;
    }

    /** Return a signal for each rule that one logged event matches. Most events match none. */
    public List<RunSignal> collectSignals(String type, JsonObject data, long igtTicks) {
        final List<RunSignal> signals = new ArrayList<>();
        if (start.matches(type, data)) {
            signals.add(new RunSignal.RunStarted());
        }
        for (Rule rule : rules) {
            if (rule.match()
                .matches(type, data)) {
                signals.add(new RunSignal.MilestoneReached(rule.milestoneId(), igtTicks));
            }
        }
        return signals;
    }

    /** The event that reaches one milestone. */
    public record Rule(String milestoneId, EventMatch match) {}
}
