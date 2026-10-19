package com.gtnhspeedrun.livesplit.audit;

import java.util.Queue;

import com.gtnhspeedrun.audit.api.AuditEvents;
import com.gtnhspeedrun.livesplit.split.MilestoneRules;
import com.gtnhspeedrun.livesplit.split.RunRecord;
import com.gtnhspeedrun.livesplit.split.RunSignal;

/**
 * Connect the milestone rules to the log events of the audit mod. Call this class only when the audit mod is loaded.
 */
public final class AuditLink {

    public static final String AUDIT_MODID = "gtnhspeedrunaudit";
    private static final int SUPPORTED_API_VERSION = 1;

    private AuditLink() {}

    public static boolean hasSupportedApi() {
        try {
            return AuditEvents.getApiVersion() == SUPPORTED_API_VERSION;
        } catch (NoClassDefFoundError error) { // an audit jar older than the API
            return false;
        }
    }

    /**
     * Queue a signal for each rule that a logged event matches, in every server from now on. Each world's history comes
     * first. A milestone's first reach goes into {@code runRecord}, and a later reach of the milestone is dropped.
     */
    public static void subscribe(MilestoneRules rules, RunRecord runRecord, Queue<RunSignal> signals) {
        AuditEvents.subscribeWithHistory(rules.collectEventTypes(), (type, data, igtTicks) -> {
            for (RunSignal signal : rules.collectSignals(type, data, igtTicks)) {
                if (!(signal instanceof RunSignal.MilestoneReached reached)
                    || runRecord.recordReach(reached.milestoneId(), reached.igtTicks())) {
                    signals.add(signal);
                }
            }
        });
    }

    public static long readIgtTicks() {
        return AuditEvents.readIgtTicks();
    }
}
