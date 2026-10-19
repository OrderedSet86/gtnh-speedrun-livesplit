package com.gtnhspeedrun.livesplit.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.Logger;

import com.gtnhspeedrun.livesplit.split.RunProgress;
import com.gtnhspeedrun.livesplit.split.SplitGuard;
import com.gtnhspeedrun.livesplit.split.TimerCommand;
import com.gtnhspeedrun.livesplit.split.TimerSignal;
import com.gtnhspeedrun.livesplit.split.TimerState;

/**
 * Keep each LiveSplit timer up to the run's progress. Signals from the server queue from any thread. A worker thread
 * reads the signals, reads each timer's state, and sends the commands of {@link SplitGuard#plan}. A timer that can't be
 * reached is tried again after a delay that doubles up to {@code MAX_RETRY_DELAY_MS}.
 */
public final class LiveSplitBridge {

    private static final long FIRST_RETRY_DELAY_MS = 500;
    private static final long MAX_RETRY_DELAY_MS = 5_000;

    private final List<SinkLink> links = new ArrayList<>();
    private final boolean skipMissedSplits;
    private final Logger log;
    private final BlockingQueue<TimerSignal> signals = new LinkedBlockingQueue<>();
    private final AtomicBoolean clearRequested = new AtomicBoolean();
    // the fields below belong to the worker thread
    private final SortedMap<Integer, Long> reachedSplits = new TreeMap<>();
    private boolean runStarted;
    private long igtTicks;
    private boolean caughtUp;
    private int segmentCount;

    public LiveSplitBridge(List<TimerSink> sinks, boolean skipMissedSplits, Logger log) {
        for (TimerSink sink : sinks) {
            links.add(new SinkLink(sink));
        }
        this.skipMissedSplits = skipMissedSplits;
        this.log = log;
    }

    public void start() {
        final Thread worker = new Thread(this::runWorker, "LiveSplitBridge");
        worker.setDaemon(true);
        worker.start();
    }

    public void offer(TimerSignal signal) {
        signals.add(signal);
    }

    /** Forget the run. Each timer's state is unchanged, and the next run's signals start over. */
    public void clearRun() {
        clearRequested.set(true);
    }

    /**
     * Prepare the sinks, then apply each batch of signals and bring each timer to the run's progress. No timer is
     * reconciled before the {@link TimerSignal.CaughtUp} signal. A loop with no signal reconciles after
     * {@code IDLE_POLL_MS}.
     */
    private void runWorker() {
        final long IDLE_POLL_MS = 1_000;
        prepareSinks();
        try {
            while (true) {
                final TimerSignal first = signals.poll(IDLE_POLL_MS, TimeUnit.MILLISECONDS);
                if (clearRequested.getAndSet(false)) {
                    clearProgress();
                }
                for (TimerSignal signal = first; signal != null; signal = signals.poll()) {
                    apply(signal);
                }
                // before the catch-up ends, a timer can be ahead of the signals so far, and must not be unsplit
                if (caughtUp && (runStarted || igtTicks > 0 || !reachedSplits.isEmpty())) {
                    for (SinkLink link : links) {
                        reconcile(link);
                    }
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread()
                .interrupt();
        }
    }

    /** Prepare each sink once. A failure is logged, and {@link TimerSink#connect} tries again once the run starts. */
    private void prepareSinks() {
        for (SinkLink link : links) {
            try {
                link.sink.prepare();
            } catch (IOException exception) {
                log.warn("livesplit.unreachable sink={} reason={}", link.sink.describe(), exception.getMessage());
            }
        }
    }

    private void clearProgress() {
        reachedSplits.clear();
        runStarted = false;
        igtTicks = 0;
        caughtUp = false;
        segmentCount = 0;
        for (SinkLink link : links) {
            link.mayStart = false;
        }
    }

    private void apply(TimerSignal signal) {
        switch (signal) {
            case TimerSignal.RunStarted started -> {
                runStarted = true;
                for (SinkLink link : links) {
                    link.mayStart = true;
                }
            }
            // the first time a split was reached is the split's time. The split can be newer than the last game time
            // signal, and the game time must not go back below the split's time
            case TimerSignal.SplitReached reached -> {
                reachedSplits.putIfAbsent(reached.splitIndex(), reached.igtTicks());
                igtTicks = Math.max(igtTicks, reached.igtTicks());
            }
            case TimerSignal.GameTime gameTime -> igtTicks = gameTime.igtTicks();
            case TimerSignal.CaughtUp caughtUpSignal -> {
                caughtUp = true;
                segmentCount = caughtUpSignal.segmentCount();
            }
        }
    }

    /**
     * Bring one timer up to the run's progress. In the first round, a stopped timer gets a start. In the second round,
     * the split index after the start is read, and the splits are sent. After an IOException, {@link TimerSink#reset}
     * is called, and the next try comes after the retry delay.
     */
    private void reconcile(SinkLink link) {
        final int MAX_ROUNDS = 2;
        final long nowMs = System.currentTimeMillis();
        if (nowMs < link.retryAtMs) {
            return;
        }
        try {
            if (!link.sink.isConnected()) {
                link.sink.connect();
                link.mayStart = runStarted;
                log.info("livesplit.connected sink={}", link.sink.describe());
            }
            for (int round = 0; round < MAX_ROUNDS; round++) {
                final TimerState state = link.sink.readState();
                if (state.phase() != TimerState.Phase.NOT_RUNNING) {
                    link.mayStart = false;
                }
                final List<TimerCommand> commands = SplitGuard.plan(
                    state,
                    new RunProgress(link.mayStart, reachedSplits, igtTicks, segmentCount),
                    skipMissedSplits);
                for (TimerCommand command : commands) {
                    link.sink.send(command);
                }
                if (!commands.contains(new TimerCommand.Start())) {
                    break;
                }
                link.mayStart = false;
            }
            link.retryDelayMs = FIRST_RETRY_DELAY_MS;
            link.reachable = true;
        } catch (IOException exception) {
            link.sink.reset();
            if (link.reachable) {
                log.warn("livesplit.unreachable sink={} reason={}", link.sink.describe(), exception.getMessage());
            } else {
                log.debug("livesplit.unreachable sink={} reason={}", link.sink.describe(), exception.getMessage());
            }
            link.reachable = false;
            link.retryAtMs = nowMs + link.retryDelayMs;
            link.retryDelayMs = Math.min(link.retryDelayMs * 2, MAX_RETRY_DELAY_MS);
        }
    }

    /** One timer, with the bridge's state for that timer. */
    private static final class SinkLink {

        private final TimerSink sink;
        private boolean mayStart;
        // starts true, so the first failure logs at WARN, and so does the first failure after each success
        private boolean reachable = true;
        private long retryAtMs;
        private long retryDelayMs = FIRST_RETRY_DELAY_MS;

        private SinkLink(TimerSink sink) {
            this.sink = sink;
        }
    }
}
