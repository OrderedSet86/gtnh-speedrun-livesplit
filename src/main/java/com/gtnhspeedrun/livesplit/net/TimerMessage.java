package com.gtnhspeedrun.livesplit.net;

import com.gtnhspeedrun.livesplit.split.TimerSignal;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** One {@link TimerSignal} from the server to a client. The signal kind goes on the wire by name. */
public final class TimerMessage implements IMessage {

    private static final String KIND_RUN_STARTED = "runStarted";
    private static final String KIND_SPLIT_REACHED = "splitReached";
    private static final String KIND_GAME_TIME = "gameTime";

    private TimerSignal signal;

    /** FML decodes into an instance made with this constructor. */
    public TimerMessage() {}

    public TimerMessage(TimerSignal signal) {
        this.signal = signal;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        switch (signal) {
            case TimerSignal.RunStarted started -> ByteBufUtils.writeUTF8String(buf, KIND_RUN_STARTED);
            case TimerSignal.SplitReached reached -> {
                ByteBufUtils.writeUTF8String(buf, KIND_SPLIT_REACHED);
                buf.writeInt(reached.splitIndex());
                buf.writeLong(reached.igtTicks());
            }
            case TimerSignal.GameTime gameTime -> {
                ByteBufUtils.writeUTF8String(buf, KIND_GAME_TIME);
                buf.writeLong(gameTime.igtTicks());
            }
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        final String kind = ByteBufUtils.readUTF8String(buf);
        signal = switch (kind) {
            case KIND_RUN_STARTED -> new TimerSignal.RunStarted();
            case KIND_SPLIT_REACHED -> new TimerSignal.SplitReached(buf.readInt(), buf.readLong());
            case KIND_GAME_TIME -> new TimerSignal.GameTime(buf.readLong());
            default -> throw new IllegalArgumentException(
                "unknown timer signal '" + kind + "' (expected runStarted, splitReached or gameTime)");
        };
    }

    /** Pass the signal to the client's receiver. */
    public static final class Handler implements IMessageHandler<TimerMessage, IMessage> {

        @Override
        public IMessage onMessage(TimerMessage message, MessageContext context) {
            SplitChannel.deliverToClient(message.signal);
            return null;
        }
    }
}
