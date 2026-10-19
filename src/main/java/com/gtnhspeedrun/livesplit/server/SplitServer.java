package com.gtnhspeedrun.livesplit.server;

import java.nio.file.Path;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.annotation.Nullable;

import net.minecraft.server.MinecraftServer;

import org.apache.logging.log4j.Logger;

import com.gtnhspeedrun.livesplit.audit.AuditLink;
import com.gtnhspeedrun.livesplit.net.SplitChannel;
import com.gtnhspeedrun.livesplit.split.MilestoneCatalog;
import com.gtnhspeedrun.livesplit.split.MilestoneFile;
import com.gtnhspeedrun.livesplit.split.MilestoneRules;
import com.gtnhspeedrun.livesplit.split.RunRecord;
import com.gtnhspeedrun.livesplit.split.RunSignal;
import com.gtnhspeedrun.livesplit.split.SplitFileException;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;

/**
 * Run the server side of the mod: read the milestone rules once, link them to the audit mod, and broadcast run signals
 * while a server runs. In singleplayer one game opens many worlds, and each world gets a new broadcast.
 */
public final class SplitServer {

    private final Logger log;
    // audit listeners add signals on any thread. SplitBroadcast drains the queue on the server tick
    private final Queue<RunSignal> pendingSignals = new ConcurrentLinkedQueue<>();
    private final RunRecord runRecord = new RunRecord();
    private @Nullable MilestoneCatalog catalog;
    private @Nullable SplitBroadcast broadcast;

    public SplitServer(Logger log) {
        this.log = log;
    }

    /**
     * Read the milestone file and subscribe to the audit. Without the audit mod, or with a bad milestone file, nothing
     * runs.
     */
    public void link(Path configDir) {
        if (!Loader.isModLoaded(AuditLink.AUDIT_MODID)) {
            log.info("audit.absent");
            return;
        }
        if (!AuditLink.hasSupportedApi()) {
            log.warn("audit.unsupported_api");
            return;
        }
        final MilestoneRules rules;
        try {
            rules = MilestoneFile.readOrCreate(configDir);
        } catch (SplitFileException exception) {
            log.error("rules.rejected reason={}", exception.getMessage(), exception);
            return;
        }
        AuditLink.subscribe(rules, runRecord, pendingSignals);
        catalog = rules.catalog();
        log.info(
            "audit.linked milestones={}",
            rules.rules()
                .size());
    }

    public void start(MinecraftServer server) {
        if (catalog == null) {
            return;
        }
        // The audit replays the world's history in the audit's serverStarting, which can run before this method.
        // The run is cleared at stop(), never here.
        SplitChannel.clearSubscribers();
        broadcast = new SplitBroadcast(
            server,
            new SplitBroadcast.RunFeed(pendingSignals, runRecord, AuditLink::readIgtTicks, catalog));
        FMLCommonHandler.instance()
            .bus()
            .register(broadcast);
    }

    public void stop() {
        if (broadcast != null) {
            FMLCommonHandler.instance()
                .bus()
                .unregister(broadcast);
            broadcast = null;
        }
        SplitChannel.clearSubscribers();
        pendingSignals.clear();
        runRecord.clear();
    }
}
