package com.gtnhspeedrun.livesplit.client;

import java.util.ArrayList;
import java.util.List;

/** A model of a LiveSplit timer with a fixed number of splits, for the fake timer servers of the tests. */
final class FakeTimer {

    private final int splitCount;
    private final List<String> history = new ArrayList<>();
    private String phase = "NotRunning";
    private int splitIndex = -1;
    private String gameTime = "";

    FakeTimer(int splitCount) {
        this.splitCount = splitCount;
    }

    synchronized void start() {
        history.add("start");
        if (phase.equals("NotRunning")) {
            phase = "Running";
            splitIndex = 0;
        }
    }

    synchronized void split() {
        history.add("split@" + gameTime);
        advance();
    }

    synchronized void skipSplit() {
        history.add("skip");
        advance();
    }

    synchronized void setGameTime(String time) {
        gameTime = time;
    }

    synchronized void reset() {
        phase = "NotRunning";
        splitIndex = -1;
    }

    synchronized String phase() {
        return phase;
    }

    synchronized int splitIndex() {
        return splitIndex;
    }

    synchronized String gameTime() {
        return gameTime;
    }

    /** Return the starts, splits with their game time, and skips, in order. */
    synchronized List<String> history() {
        return new ArrayList<>(history);
    }

    private void advance() {
        if (!phase.equals("Running")) {
            return;
        }
        splitIndex++;
        if (splitIndex == splitCount) {
            phase = "Ended";
        }
    }
}
