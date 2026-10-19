package com.gtnhspeedrun.livesplit.client;

import java.net.URI;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;

/**
 * A WebSocket client that acts as LiveSplit One in Connect to Server mode: it answers each JSON command from a
 * {@link FakeTimer}, and sends an event frame before each reply, as LiveSplit One does.
 */
final class FakeLiveSplitOne implements AutoCloseable {

    private final FakeTimer timer;
    private final EventLoopGroup group = new NioEventLoopGroup(1);
    private final Channel channel;

    FakeLiveSplitOne(FakeTimer timer, int port) throws InterruptedException {
        this.timer = timer;
        final URI uri = URI.create("ws://localhost:" + port + "/");
        channel = new Bootstrap().group(group)
            .channel(NioSocketChannel.class)
            .handler(new ChannelInitializer<SocketChannel>() {

                @Override
                protected void initChannel(SocketChannel socketChannel) {
                    socketChannel.pipeline()
                        .addLast(
                            new HttpClientCodec(),
                            new HttpObjectAggregator(65_536),
                            new WebSocketClientProtocolHandler(
                                WebSocketClientHandshakerFactory
                                    .newHandshaker(uri, WebSocketVersion.V13, null, false, new DefaultHttpHeaders())),
                            new CommandHandler());
                }
            })
            .connect("localhost", port)
            .sync()
            .channel();
    }

    @Override
    public void close() {
        channel.close();
        group.shutdownGracefully();
    }

    private String answer(JsonObject command) {
        final JsonObject reply = new JsonObject();
        switch (command.get("command")
            .getAsString()) {
            case "start" -> timer.start();
            case "split" -> timer.split();
            case "skipSplit" -> timer.skipSplit();
            case "undoSplit" -> timer.unsplit();
            case "setGameTime" -> timer.setGameTime(
                command.get("time")
                    .getAsString());
            case "getCurrentState" -> {
                final JsonObject state = new JsonObject();
                state.addProperty("state", timer.phase());
                if (timer.phase()
                    .equals("Running")) {
                    state.addProperty("index", timer.splitIndex());
                }
                reply.add("success", state);
                return reply.toString();
            }
            default -> {}
        }
        return "{\"success\":null}";
    }

    private final class CommandHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

        @Override
        protected void channelRead0(ChannelHandlerContext context, TextWebSocketFrame frame) {
            final JsonObject command = new JsonParser().parse(frame.text())
                .getAsJsonObject();
            final String reply = answer(command);
            context.writeAndFlush(new TextWebSocketFrame("{\"event\":\"GameTimeSet\"}"));
            context.writeAndFlush(new TextWebSocketFrame(reply));
        }
    }
}
