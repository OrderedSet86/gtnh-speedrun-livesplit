package com.gtnhspeedrun.livesplit.client;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.gtnhspeedrun.livesplit.split.TimerCommand;
import com.gtnhspeedrun.livesplit.split.TimerProtocolException;
import com.gtnhspeedrun.livesplit.split.TimerState;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;

/**
 * Serve LiveSplit One on a WebSocket on the loopback address. The runner opens LiveSplit One, picks Connect to Server,
 * and enters {@code ws://localhost:<port>}. Each JSON command gets one reply. Timer events arrive between the replies,
 * and the sink skips them. The newest LiveSplit One connection replaces an older one.
 *
 * <pre>
 *     {"command":"getCurrentState"}  ->  {"success":{"state":"Running","index":2}}
 *     {"command":"split"}            ->  {"event":"Splitted"}  {"success":null}
 * </pre>
 */
public final class LsoSink implements TimerSink {

    private final int port;
    private final BlockingQueue<String> replies = new LinkedBlockingQueue<>();
    private @Nullable EventLoopGroup eventLoop;
    // set on the netty thread when a LiveSplit One handshake completes
    private volatile @Nullable Channel timerChannel;

    public LsoSink(int port) {
        this.port = port;
    }

    @Override
    public String describe() {
        return "liveSplitOne:" + port;
    }

    @Override
    public boolean isConnected() {
        final Channel channel = timerChannel;
        return channel != null && channel.isActive();
    }

    /** Listen on the port, once. LiveSplit One doesn't retry a refused connection, so listen before the run starts. */
    @Override
    public void prepare() throws IOException {
        if (eventLoop == null) {
            eventLoop = listen();
        }
    }

    /** Listen on the port, once, and throw until LiveSplit One has connected. */
    @Override
    public void connect() throws IOException {
        prepare();
        if (!isConnected()) {
            throw new IOException("no LiveSplit One connected to ws://localhost:" + port);
        }
    }

    @Override
    public TimerState readState() throws IOException {
        final JsonObject reply = request(command("getCurrentState"));
        final JsonElement success = reply.get("success");
        if (success == null || !success.isJsonObject()
            || !success.getAsJsonObject()
                .has("state")) {
            throw new TimerProtocolException(
                "unexpected reply " + reply + " to getCurrentState (expected {\"success\":{\"state\":…}})");
        }
        final JsonObject rawState = success.getAsJsonObject();
        final TimerState.Phase phase = TimerState.Phase.parse(
            rawState.get("state")
                .getAsString());
        final JsonElement rawIndex = rawState.get("index");
        return new TimerState(phase, rawIndex == null ? -1 : rawIndex.getAsInt());
    }

    /** Send the command. An error reply, such as the reply to a split after the run ended, is skipped. */
    @Override
    public void send(TimerCommand command) throws IOException {
        for (JsonObject json : renderJson(command)) {
            request(json);
        }
    }

    /** Drop stale replies and keep the channel open. LiveSplit One doesn't reconnect by itself. */
    @Override
    public void reset() {
        replies.clear();
    }

    /** Listen for LiveSplit One on the loopback address. Throw when the port is taken. */
    private EventLoopGroup listen() throws IOException {
        final int MAX_HANDSHAKE_BYTES = 65_536;
        final NioEventLoopGroup group = new NioEventLoopGroup(1, runnable -> {
            final Thread thread = new Thread(runnable, "LiveSplitOne-WebSocket");
            thread.setDaemon(true);
            return thread;
        });
        final ChannelFuture bound = new ServerBootstrap().group(group)
            .channel(NioServerSocketChannel.class)
            .childHandler(new ChannelInitializer<SocketChannel>() {

                @Override
                protected void initChannel(SocketChannel channel) {
                    channel.pipeline()
                        .addLast(
                            new HttpServerCodec(),
                            new HttpObjectAggregator(MAX_HANDSHAKE_BYTES),
                            new WebSocketServerProtocolHandler("/"),
                            new ReplyHandler());
                }
            })
            .bind(InetAddress.getLoopbackAddress(), port)
            .awaitUninterruptibly();
        if (!bound.isSuccess()) {
            group.shutdownGracefully();
            throw new IOException("can't listen on port " + port, bound.cause());
        }
        return group;
    }

    private static List<JsonObject> renderJson(TimerCommand command) {
        return switch (command) {
            case TimerCommand.Start start -> List.of(command("start"));
            case TimerCommand.Split split -> List.of(command("split"));
            case TimerCommand.SkipSplit skip -> List.of(command("skipSplit"));
            case TimerCommand.SetGameTime gameTime -> {
                // a new attempt starts with the game time running. The game time stays running after a setGameTime
                final JsonObject setGameTime = command("setGameTime");
                setGameTime.addProperty("time", gameTime.timeText());
                yield List.of(command("pauseGameTime"), setGameTime);
            }
        };
    }

    private static JsonObject command(String name) {
        final JsonObject json = new JsonObject();
        json.addProperty("command", name);
        return json;
    }

    /**
     * Send one command and return the command's reply, past any event frames.
     *
     * @throws SocketTimeoutException after {@code REPLY_TIMEOUT_MS} with no reply
     * @throws InterruptedIOException when the thread is interrupted during the wait
     */
    private JsonObject request(JsonObject json) throws IOException {
        final long REPLY_TIMEOUT_MS = 2_000;
        final Channel channel = timerChannel;
        if (channel == null || !channel.isActive()) {
            throw new IOException("LiveSplit One disconnected");
        }
        channel.writeAndFlush(new TextWebSocketFrame(json.toString()));
        final long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(REPLY_TIMEOUT_MS);
        while (true) {
            final String rawReply;
            try {
                rawReply = replies.poll(deadlineNanos - System.nanoTime(), TimeUnit.NANOSECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread()
                    .interrupt();
                final InterruptedIOException interrupted = new InterruptedIOException(
                    "interrupted while waiting for LiveSplit One");
                interrupted.initCause(exception);
                throw interrupted;
            }
            if (rawReply == null) {
                throw new SocketTimeoutException("LiveSplit One sent no reply to " + json);
            }
            final JsonObject reply = parseReply(rawReply);
            if (!reply.has("event")) {
                return reply;
            }
        }
    }

    private static JsonObject parseReply(String rawReply) throws TimerProtocolException {
        final JsonElement reply;
        try {
            reply = new JsonParser().parse(rawReply);
        } catch (JsonParseException exception) {
            throw new TimerProtocolException("unreadable reply '" + rawReply + "' (expected a JSON object)", exception);
        }
        if (!reply.isJsonObject()) {
            throw new TimerProtocolException("unreadable reply '" + rawReply + "' (expected a JSON object)");
        }
        return reply.getAsJsonObject();
    }

    /** Set {@link #timerChannel} to the newest LiveSplit One channel, and queue the channel's text frames. */
    private final class ReplyHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

        @Override
        public void userEventTriggered(ChannelHandlerContext context, Object event) throws Exception {
            if (event != WebSocketServerProtocolHandler.ServerHandshakeStateEvent.HANDSHAKE_COMPLETE) {
                super.userEventTriggered(context, event);
                return;
            }
            final Channel previous = timerChannel;
            replies.clear();
            timerChannel = context.channel();
            if (previous != null) {
                previous.close();
            }
        }

        @Override
        protected void channelRead0(ChannelHandlerContext context, TextWebSocketFrame frame) {
            if (context.channel() == timerChannel) {
                replies.add(frame.text());
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext context) throws Exception {
            if (context.channel() == timerChannel) {
                timerChannel = null;
            }
            super.channelInactive(context);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
            context.close();
        }
    }
}
