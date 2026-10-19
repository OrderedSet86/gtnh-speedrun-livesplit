package com.gtnhspeedrun.livesplit.client;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import net.minecraft.util.ChatComponentTranslation;

import org.apache.logging.log4j.Logger;

import com.gtnhspeedrun.livesplit.split.MilestoneCatalog;
import com.gtnhspeedrun.livesplit.split.RunSegments;
import com.gtnhspeedrun.livesplit.split.RunSignal;
import com.gtnhspeedrun.livesplit.split.SegmentSpec;
import com.gtnhspeedrun.livesplit.split.SelectionFile;
import com.gtnhspeedrun.livesplit.split.SplitFileException;
import com.gtnhspeedrun.livesplit.split.TimerSignal;

/**
 * Follow the run on one server in terms of the runner's route. The server's catalog arrives on join. The route is the
 * runner's selection file, or the server's default route when the client has no file. Each run signal goes on to the
 * timers as a signal about the runner's segments.
 */
public final class RouteTracker {

    private final Logger log;
    private final Path configDir;
    private final Consumer<TimerSignal> timerSignals;
    // set on a netty thread when the catalog arrives, cleared on disconnect
    private volatile @Nullable RunSegments segments;

    public RouteTracker(Logger log, Path configDir, Consumer<TimerSignal> timerSignals) {
        this.log = log;
        this.configDir = configDir;
        this.timerSignals = timerSignals;
    }

    /**
     * Resolve the runner's route against {@code catalog}, and follow the run on that route from now on. The selection
     * file is read on each call. A file that can't be read is an empty route: no timer splits on a route that the
     * runner didn't write.
     */
    public RunSegments resolveRoute(MilestoneCatalog catalog) {
        List<SegmentSpec> route;
        String routeSource;
        try {
            final List<SegmentSpec> selection = SelectionFile.read(configDir);
            route = selection != null ? selection : catalog.defaultSplits();
            routeSource = selection != null ? SelectionFile.FILE_NAME : "server";
        } catch (SplitFileException exception) {
            log.warn("route.rejected reason={}", exception.getMessage());
            ChatLines
                .sayLater(new ChatComponentTranslation("gtnhspeedrunlivesplit.route.rejected", exception.getMessage()));
            route = List.of();
            routeSource = "none";
        }
        final RunSegments resolved = RunSegments.resolve(catalog, route);
        segments = resolved;
        log.info(
            "route.resolved source={} segments={} milestones={} unknownIds={}",
            routeSource,
            resolved.countSegments(),
            catalog.milestones()
                .size(),
            resolved.listUnknownIds());
        if (!resolved.listUnknownIds()
            .isEmpty()) {
            ChatLines.sayLater(
                new ChatComponentTranslation(
                    "gtnhspeedrunlivesplit.route.unknown_ids",
                    String.join(", ", resolved.listUnknownIds())));
        }
        return resolved;
    }

    public void acceptSignal(RunSignal signal) {
        final RunSegments current = segments;
        switch (signal) {
            case RunSignal.RunStarted started -> timerSignals.accept(new TimerSignal.RunStarted());
            case RunSignal.GameTime gameTime -> timerSignals.accept(new TimerSignal.GameTime(gameTime.igtTicks()));
            case RunSignal.MilestoneReached reached -> {
                if (current == null) {
                    return;
                }
                final List<TimerSignal.SplitReached> splits = current
                    .acceptReach(reached.milestoneId(), reached.igtTicks());
                splits.forEach(timerSignals);
                if (!splits.isEmpty() && current.hasGap()) {
                    log.info("split.out_of_order milestone={}", reached.milestoneId());
                }
            }
            case RunSignal.CaughtUp caughtUp -> timerSignals
                .accept(new TimerSignal.CaughtUp(current == null ? 0 : current.countSegments()));
        }
    }

    /** Return the route and the run so far on this server, or null before the catalog arrives. */
    @Nullable
    public RunSegments readSegments() {
        return segments;
    }

    public void clear() {
        segments = null;
    }
}
