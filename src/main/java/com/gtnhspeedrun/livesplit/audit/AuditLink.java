package com.gtnhspeedrun.livesplit.audit;

import java.util.Queue;

import com.gtnhspeedrun.audit.api.AuditEvents;
import com.gtnhspeedrun.livesplit.split.SplitRules;
import com.gtnhspeedrun.livesplit.split.TimerSignal;

/** Connect the split rules to the log events of the audit mod. Call this class only when the audit mod is loaded. */
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

    /** Queue a signal for each rule that a logged event matches, in every server from now on. */
    public static void subscribe(SplitRules rules, Queue<TimerSignal> signals) {
        AuditEvents.subscribe(
            rules.collectEventTypes(),
            (type, data, igtTicks) -> signals.addAll(rules.collectSignals(type, data, igtTicks)));
    }

    public static long readIgtTicks() {
        return AuditEvents.readIgtTicks();
    }
}
