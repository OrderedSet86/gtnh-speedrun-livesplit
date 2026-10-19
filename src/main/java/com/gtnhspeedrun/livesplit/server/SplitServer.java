package com.gtnhspeedrun.livesplit.server;

import java.nio.file.Path;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.annotation.Nullable;

import net.minecraft.server.MinecraftServer;

import org.apache.logging.log4j.Logger;

import com.gtnhspeedrun.livesplit.audit.AuditLink;
import com.gtnhspeedrun.livesplit.net.SplitChannel;
import com.gtnhspeedrun.livesplit.split.SplitRules;
import com.gtnhspeedrun.livesplit.split.SplitRulesException;
import com.gtnhspeedrun.livesplit.split.SplitRulesFile;
import com.gtnhspeedrun.livesplit.split.TimerSignal;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;

/**
 * Run the server side of the mod: read the split rules once, link them to the audit mod, and broadcast timer signals
 * while a server runs. In singleplayer one game opens many worlds, and each world gets a new broadcast.
 */
public final class SplitServer {

    private final Logger log;
    // audit listeners add signals on any thread. SplitBroadcast drains the queue on the server tick
    private final Queue<TimerSignal> pendingSignals = new ConcurrentLinkedQueue<>();
    private boolean linked;
    private @Nullable SplitBroadcast broadcast;

    public SplitServer(Logger log) {
        this.log = log;
    }

    /**
     * Read the rules file and subscribe to the audit. Without the audit mod, or with a bad rules file, nothing runs.
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
        final SplitRules rules;
        try {
            rules = SplitRulesFile.readOrCreate(configDir);
        } catch (SplitRulesException exception) {
            log.error("rules.rejected reason={}", exception.getMessage(), exception);
            return;
        }
        AuditLink.subscribe(rules, pendingSignals);
        linked = true;
        log.info(
            "audit.linked splits={}",
            rules.splits()
                .size());
    }

    public void start(MinecraftServer server) {
        if (!linked) {
            return;
        }
        pendingSignals.clear();
        SplitChannel.clearSubscribers();
        broadcast = new SplitBroadcast(server, pendingSignals, AuditLink::readIgtTicks);
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
    }
}
