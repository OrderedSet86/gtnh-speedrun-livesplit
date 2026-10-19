package com.gtnhspeedrun.livesplit.net;

import com.gtnhspeedrun.livesplit.split.MilestoneCatalog;
import com.gtnhspeedrun.livesplit.split.RunSignal;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** One {@link RunSignal} from the server to a client. The signal kind goes on the wire by name. */
public final class RunSignalMessage implements IMessage {

    private static final String KIND_RUN_STARTED = "runStarted";
    private static final String KIND_MILESTONE_REACHED = "milestoneReached";
    private static final String KIND_GAME_TIME = "gameTime";
    private static final String KIND_CAUGHT_UP = "caughtUp";

    private RunSignal signal;

    /** FML decodes into an instance made with this constructor. */
    public RunSignalMessage() {}

    public RunSignalMessage(RunSignal signal) {
        this.signal = signal;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        switch (signal) {
            case RunSignal.RunStarted started -> ByteBufUtils.writeUTF8String(buf, KIND_RUN_STARTED);
            case RunSignal.MilestoneReached reached -> {
                ByteBufUtils.writeUTF8String(buf, KIND_MILESTONE_REACHED);
                ByteBufUtils.writeUTF8String(buf, reached.milestoneId());
                buf.writeLong(reached.igtTicks());
            }
            case RunSignal.GameTime gameTime -> {
                ByteBufUtils.writeUTF8String(buf, KIND_GAME_TIME);
                buf.writeLong(gameTime.igtTicks());
            }
            case RunSignal.CaughtUp caughtUp -> ByteBufUtils.writeUTF8String(buf, KIND_CAUGHT_UP);
        }
    }

    /** Decode one signal. An unknown kind or a bad milestone id throws an IllegalArgumentException. */
    @Override
    public void fromBytes(ByteBuf buf) {
        final String kind = ByteBufUtils.readUTF8String(buf);
        signal = switch (kind) {
            case KIND_RUN_STARTED -> new RunSignal.RunStarted();
            case KIND_MILESTONE_REACHED -> new RunSignal.MilestoneReached(
                MilestoneCatalog.requireValidId(ByteBufUtils.readUTF8String(buf)),
                buf.readLong());
            case KIND_GAME_TIME -> new RunSignal.GameTime(buf.readLong());
            case KIND_CAUGHT_UP -> new RunSignal.CaughtUp();
            default -> throw new IllegalArgumentException(
                "unknown run signal '" + kind + "' (expected runStarted, milestoneReached, gameTime or caughtUp)");
        };
    }

    /** Pass the signal to the client's receiver. */
    public static final class Handler implements IMessageHandler<RunSignalMessage, IMessage> {

        @Override
        public IMessage onMessage(RunSignalMessage message, MessageContext context) {
            SplitChannel.deliverToClient(message.signal);
            return null;
        }
    }
}
