package com.gtnhspeedrun.livesplit.client;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.annotation.Nullable;

import com.gtnhspeedrun.livesplit.split.TimerCommand;
import com.gtnhspeedrun.livesplit.split.TimerProtocolException;
import com.gtnhspeedrun.livesplit.split.TimerState;

/**
 * Talk to desktop LiveSplit through its TCP command server. The runner starts the server in LiveSplit under Control,
 * Start TCP/WS Server. Each command is one line, and only a query gets a reply line.
 *
 * <pre>
 *     getcurrenttimerphase   -> Running
 *     getsplitindex          -> 2
 *     setgametime 1:02:03.45
 * </pre>
 */
public final class DesktopSink implements TimerSink {

    private final int port;
    private @Nullable Connection connection;

    public DesktopSink(int port) {
        this.port = port;
    }

    @Override
    public String describe() {
        return "desktop:" + port;
    }

    @Override
    public boolean isConnected() {
        return connection != null;
    }

    @Override
    public void connect() throws IOException {
        final int CONNECT_TIMEOUT_MS = 1_000;
        final int REPLY_TIMEOUT_MS = 2_000;
        reset();
        final Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(REPLY_TIMEOUT_MS);
            connection = new Connection(
                socket,
                new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)),
                new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)));
        } catch (IOException exception) {
            socket.close();
            throw exception;
        }
        // LiveSplit then moves the game time only on setgametime, across timer starts too
        writeLines(List.of("alwayspausegametime"));
    }

    @Override
    public TimerState readState() throws IOException {
        final TimerState.Phase phase = TimerState.Phase.parse(query("getcurrenttimerphase"));
        final String rawIndex = query("getsplitindex");
        final int splitIndex;
        try {
            splitIndex = Integer.parseInt(rawIndex);
        } catch (NumberFormatException exception) {
            throw new TimerProtocolException(
                "bad split index '" + rawIndex + "' (expected a whole number such as 2)",
                exception);
        }
        final boolean hasSplit = phase == TimerState.Phase.RUNNING || phase == TimerState.Phase.PAUSED;
        return new TimerState(phase, hasSplit ? splitIndex : -1);
    }

    @Override
    public void send(TimerCommand command) throws IOException {
        writeLines(renderLines(command));
    }

    @Override
    public void reset() {
        if (connection == null) {
            return;
        }
        try {
            connection.socket()
                .close();
        } catch (IOException exception) {
            // the socket is unusable either way
        }
        connection = null;
    }

    private static List<String> renderLines(TimerCommand command) {
        return switch (command) {
            case TimerCommand.Start start -> List.of("starttimer");
            case TimerCommand.Split split -> List.of("split");
            case TimerCommand.SkipSplit skip -> List.of("skipsplit");
            // a runner can unpause the game time by hand. LiveSplit counts unpaused game time up with real time
            case TimerCommand.SetGameTime gameTime -> List.of("pausegametime", "setgametime " + gameTime.timeText());
        };
    }

    private String query(String command) throws IOException {
        writeLines(List.of(command));
        final String reply = requireConnection().reader()
            .readLine();
        if (reply == null) {
            throw new EOFException("LiveSplit closed the connection");
        }
        return reply.trim();
    }

    private void writeLines(List<String> lines) throws IOException {
        final Writer writer = requireConnection().writer();
        for (String line : lines) {
            writer.write(line);
            writer.write('\n');
        }
        writer.flush();
    }

    private Connection requireConnection() throws IOException {
        if (connection == null) {
            throw new IOException("not connected to " + describe());
        }
        return connection;
    }

    private record Connection(Socket socket, BufferedReader reader, Writer writer) {}
}
