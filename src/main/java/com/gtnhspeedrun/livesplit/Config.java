package com.gtnhspeedrun.livesplit;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

/** Read the settings of the Forge config file into static fields. */
public final class Config {

    public static boolean enabled = true;
    public static TimerTarget timerTarget = TimerTarget.BOTH;
    public static int desktopPort = 16834;
    public static int lsoPort = 16835;
    public static boolean skipMissedSplits = true;

    private Config() {}

    /** Read every setting into its static field, with the initial value of the field as the default. */
    public static void synchronizeConfiguration(File configFile) {
        final int MAX_PORT = 65_535;
        final Configuration config = new Configuration(configFile);

        enabled = config.getBoolean(
            "enabled",
            "server",
            enabled,
            "Send split signals to the clients that have this mod. Needs the audit mod on the server.");
        final String rawTarget = config.getString(
            "timerTarget",
            "client",
            timerTarget.name(),
            "The LiveSplit timers to drive: OFF, DESKTOP (LiveSplit's TCP server), ONE (LiveSplit One) or BOTH.",
            new String[] { "OFF", "DESKTOP", "ONE", "BOTH" });
        timerTarget = TimerTarget.parse(rawTarget);
        desktopPort = config.getInt(
            "desktopPort",
            "client",
            desktopPort,
            1,
            MAX_PORT,
            "The port of desktop LiveSplit's TCP server (LiveSplit: Settings, Server Port).");
        lsoPort = config.getInt(
            "lsoPort",
            "client",
            lsoPort,
            1,
            MAX_PORT,
            "The port for LiveSplit One. In LiveSplit One, Connect to Server with ws://localhost:<this port>.");
        skipMissedSplits = config.getBoolean(
            "skipMissedSplits",
            "client",
            skipMissedSplits,
            "When a later split is reached first, skip the splits before it. Off: wait for each split in order.");

        if (config.hasChanged()) {
            config.save();
        }
    }

    /** The LiveSplit timers the client drives. */
    public enum TimerTarget {

        OFF,
        DESKTOP,
        ONE,
        BOTH;

        /** Return the target of {@code rawTarget}, or BOTH for a value that isn't a target. */
        public static TimerTarget parse(String rawTarget) {
            for (TimerTarget target : values()) {
                if (target.name()
                    .equals(rawTarget)) {
                    return target;
                }
            }
            return BOTH;
        }
    }
}
