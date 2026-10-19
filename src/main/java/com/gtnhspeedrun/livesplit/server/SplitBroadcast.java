package com.gtnhspeedrun.livesplit.server;

import java.util.Queue;
import java.util.UUID;
import java.util.function.LongSupplier;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import com.gtnhspeedrun.livesplit.net.SplitChannel;
import com.gtnhspeedrun.livesplit.split.TimerSignal;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Send timer signals to the subscribed clients at the end of each server tick: each matched audit event, a welcome for
 * each new subscriber, and the game time once a second.
 */
public final class SplitBroadcast {

    private final MinecraftServer server;
    private final Queue<TimerSignal> pendingSignals;
    private final LongSupplier igtClock;
    private int ticksSinceGameTime;

    public SplitBroadcast(MinecraftServer server, Queue<TimerSignal> pendingSignals, LongSupplier igtClock) {
        this.server = server;
        this.pendingSignals = pendingSignals;
        this.igtClock = igtClock;
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        final int TICKS_PER_GAME_TIME = 20;
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        welcomeNewSubscribers();
        for (TimerSignal signal = pendingSignals.poll(); signal != null; signal = pendingSignals.poll()) {
            SplitChannel.sendToSubscribers(server, signal);
        }
        if (++ticksSinceGameTime >= TICKS_PER_GAME_TIME) {
            ticksSinceGameTime = 0;
            final long igtTicks = igtClock.getAsLong();
            if (igtTicks > 0) {
                SplitChannel.sendToSubscribers(server, new TimerSignal.GameTime(igtTicks));
            }
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        SplitChannel.unsubscribe(
            event.player.getGameProfile()
                .getId());
    }

    /** Send each new subscriber the run start once timing has started, so the client's timer starts on join. */
    private void welcomeNewSubscribers() {
        for (UUID playerId = SplitChannel.pollNewSubscriber(); playerId
            != null; playerId = SplitChannel.pollNewSubscriber()) {
            final long igtTicks = igtClock.getAsLong();
            final EntityPlayerMP player = findPlayer(playerId);
            if (igtTicks > 0 && player != null) {
                SplitChannel.sendTo(player, new TimerSignal.RunStarted());
                SplitChannel.sendTo(player, new TimerSignal.GameTime(igtTicks));
            }
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
}
