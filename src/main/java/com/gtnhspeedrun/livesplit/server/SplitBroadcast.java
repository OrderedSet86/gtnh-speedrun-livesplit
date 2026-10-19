package com.gtnhspeedrun.livesplit.server;

import java.util.Queue;
import java.util.UUID;
import java.util.function.LongSupplier;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import com.gtnhspeedrun.livesplit.net.SplitChannel;
import com.gtnhspeedrun.livesplit.split.MilestoneCatalog;
import com.gtnhspeedrun.livesplit.split.RunRecord;
import com.gtnhspeedrun.livesplit.split.RunSegments;
import com.gtnhspeedrun.livesplit.split.RunSignal;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Send run signals to the subscribed clients at the end of each server tick: each first reach of a milestone, a welcome
 * for each new subscriber, and the game time once a second. The welcome has the catalog and the run so far.
 */
public final class SplitBroadcast {

    private final MinecraftServer server;
    private final RunFeed feed;
    private int ticksSinceGameTime;

    public SplitBroadcast(MinecraftServer server, RunFeed feed) {
        this.server = server;
        this.feed = feed;
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        final int TICKS_PER_GAME_TIME = 20;
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        welcomeNewSubscribers();
        for (RunSignal signal = feed.pendingSignals()
            .poll(); signal != null; signal = feed.pendingSignals()
                .poll()) {
            SplitChannel.sendToSubscribers(server, signal);
        }
        if (++ticksSinceGameTime >= TICKS_PER_GAME_TIME) {
            ticksSinceGameTime = 0;
            final long igtTicks = feed.igtClock()
                .getAsLong();
            if (igtTicks > 0) {
                SplitChannel.sendToSubscribers(server, new RunSignal.GameTime(igtTicks));
            }
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        SplitChannel.unsubscribe(
            event.player.getGameProfile()
                .getId());
    }

    /**
     * Send each new subscriber the catalog, the run start once timing has started, each milestone's first reach in
     * reach order, the game time, and the end of the catch-up. A reach that is also in the queue goes out twice, and
     * {@link RunSegments#acceptReach} returns no segment for the second copy.
     */
    private void welcomeNewSubscribers() {
        for (UUID playerId = SplitChannel.pollNewSubscriber(); playerId
            != null; playerId = SplitChannel.pollNewSubscriber()) {
            final EntityPlayerMP player = findPlayer(playerId);
            if (player == null) {
                continue;
            }
            SplitChannel.sendCatalog(player, feed.catalog());
            final long igtTicks = feed.igtClock()
                .getAsLong();
            if (igtTicks > 0) {
                SplitChannel.sendTo(player, new RunSignal.RunStarted());
            }
            for (RunSignal.MilestoneReached reach : feed.runRecord()
                .listReaches()) {
                SplitChannel.sendTo(player, reach);
            }
            if (igtTicks > 0) {
                SplitChannel.sendTo(player, new RunSignal.GameTime(igtTicks));
            }
            SplitChannel.sendTo(player, new RunSignal.CaughtUp());
        }
    }

    @Nullable
    private EntityPlayerMP findPlayer(UUID playerId) {
        for (Object entry : server.getConfigurationManager().playerEntityList) {
            final EntityPlayerMP player = (EntityPlayerMP) entry;
            if (playerId.equals(
                player.getGameProfile()
                    .getId())) {
                return player;
            }
        }
        return null;
    }

    /**
     * What the broadcast sends from.
     *
     * @param pendingSignals the signals since the last tick, from audit listeners on any thread
     */
    public record RunFeed(Queue<RunSignal> pendingSignals, RunRecord runRecord, LongSupplier igtClock,
        MilestoneCatalog catalog) {}
}
