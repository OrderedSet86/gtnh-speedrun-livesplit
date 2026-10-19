package com.gtnhspeedrun.livesplit.net;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** A subscription to the run's timer signals, sent by a client with this mod. */
public final class HelloMessage implements IMessage {

    private int protocolVersion;

    /** FML decodes into an instance made with this constructor. */
    public HelloMessage() {}

    public HelloMessage(int protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(protocolVersion);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        protocolVersion = buf.readInt();
    }

    /** Subscribe the sender on the server. */
    public static final class Handler implements IMessageHandler<HelloMessage, IMessage> {

        @Override
        public IMessage onMessage(HelloMessage message, MessageContext context) {
            SplitChannel.acceptHello(
                context.getServerHandler().playerEntity.getGameProfile()
                    .getId(),
                message.protocolVersion);
            return null;
        }
    }
}
