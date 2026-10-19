package com.gtnhspeedrun.livesplit.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/** A TCP server that answers the commands of desktop LiveSplit's command server from a {@link FakeTimer}. */
final class FakeDesktopServer implements AutoCloseable {

    private final ServerSocket serverSocket;
    private final FakeTimer timer;

    FakeDesktopServer(FakeTimer timer) throws IOException {
        this.timer = timer;
        serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        final Thread acceptor = new Thread(this::serve, "FakeDesktopServer");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    int port() {
        return serverSocket.getLocalPort();
    }

    @Override
    public void close() throws IOException {
        serverSocket.close();
    }

    private void serve() {
        while (!serverSocket.isClosed()) {
            try (Socket socket = serverSocket.accept();
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                Writer writer = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)) {
                for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                    final String reply = answer(line);
                    if (reply != null) {
                        writer.write(reply + "\n");
                        writer.flush();
                    }
                }
            } catch (IOException exception) {
                // the client went away, or the server closed. Accept the next client
            }
        }
    }

    private String answer(String line) {
        final String[] parts = line.split(" ", 2);
        switch (parts[0]) {
            case "starttimer" -> timer.start();
            case "split" -> timer.split();
            case "skipsplit" -> timer.skipSplit();
            case "setgametime" -> timer.setGameTime(parts[1]);
            case "getcurrenttimerphase" -> {
                return timer.phase();
            }
            case "getsplitindex" -> {
                return Integer.toString(timer.splitIndex());
            }
            default -> {}
        }
        return null;
    }
}
