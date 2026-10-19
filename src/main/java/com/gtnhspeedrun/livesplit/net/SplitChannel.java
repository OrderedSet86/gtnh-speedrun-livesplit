package com.gtnhspeedrun.livesplit.net;

import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import com.gtnhspeedrun.livesplit.split.TimerSignal;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

/**
 * The network channel between the server and the clients that have this mod. Timer signals go only to the players with
 * a {@link HelloMessage} from their client. Clients without the mod get nothing.
 */
public final class SplitChannel {

    // Minecraft 1.7.10 disconnects a player on a custom payload channel name over 20 characters, and the modid
    // gtnhspeedrunlivesplit has 21. Name the channel apart from the modid
    private static final String CHANNEL_NAME = "gtnhsrlivesplit";
    private static final int PROTOCOL_VERSION = 1;

    // Each message handler is created through its no-argument constructor, so the handlers read the fields below.
    // The fields are static for that reason
    private static SimpleNetworkWrapper channel;
    private static final Set<UUID> SUBSCRIBERS = ConcurrentHashMap.newKeySet(); // static: read by the handlers
    private static final Queue<UUID> NEW_SUBSCRIBERS = new ConcurrentLinkedQueue<>(); // static: read by the handlers
    private static volatile Consumer<TimerSignal> clientReceiver = signal -> {};

    private SplitChannel() {}

    /** Create the channel. Call during preInit. */
    public static void register() {
        channel = NetworkRegistry.INSTANCE.newSimpleChannel(CHANNEL_NAME);
        channel.registerMessage(HelloMessage.Handler.class, HelloMessage.class, 0, Side.SERVER);
        channel.registerMessage(TimerMessage.Handler.class, TimerMessage.class, 1, Side.CLIENT);
    }

    // ------------------------------------------------------------------ client side

    /** Pass each received signal to {@code receiver}. The call runs on a netty thread. */
    public static void setClientReceiver(Consumer<TimerSignal> receiver) {
        clientReceiver = receiver;
    }

    public static void sendHello() {
        channel.sendToServer(new HelloMessage(PROTOCOL_VERSION));
    }

    public static void deliverToClient(TimerSignal signal) {
        clientReceiver.accept(signal);
    }

    // ------------------------------------------------------------------ server side

    public static void acceptHello(UUID playerId, int protocolVersion) {
        if (protocolVersion == PROTOCOL_VERSION && SUBSCRIBERS.add(playerId)) {
            NEW_SUBSCRIBERS.add(playerId);
        }
    }

    /** Return the next player who subscribed since the last call, or null. */
    @Nullable
    public static UUID pollNewSubscriber() {
        return NEW_SUBSCRIBERS.poll();
    }

    public static void unsubscribe(UUID playerId) {
        SUBSCRIBERS.remove(playerId);
    }

    public static void clearSubscribers() {
        SUBSCRIBERS.clear();
        NEW_SUBSCRIBERS.clear();
    }

    public static void sendToSubscribers(MinecraftServer server, TimerSignal signal) {
        if (SUBSCRIBERS.isEmpty()) {
            return;
        }
        final TimerMessage message = new TimerMessage(signal);
        for (Object entry : server.getConfigurationManager().playerEntityList) {
            final EntityPlayerMP player = (EntityPlayerMP) entry;
            if (SUBSCRIBERS.contains(
                player.getGameProfile()
                    .getId())) {
                channel.sendTo(message, player);
            }
        }
    }

    public static void sendTo(EntityPlayerMP player, TimerSignal signal) {
        channel.sendTo(new TimerMessage(signal), player);
    }
}
