package com.gtnhspeedrun.livesplit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.gtnhspeedrun.livesplit.net.SplitChannel;
import com.gtnhspeedrun.livesplit.server.SplitServer;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppedEvent;

/**
 * Split the runner's LiveSplit timers on audit log milestones. Install the mod beside the audit mod on the server, and
 * on each client whose LiveSplit should split. Clients without the mod can still join.
 */
@Mod(
    modid = GtnhSpeedrunLiveSplit.MODID,
    name = "GTNH Speedrun LiveSplit",
    version = Tags.VERSION,
    acceptedMinecraftVersions = "[1.7.10]",
    acceptableRemoteVersions = "*")
public class GtnhSpeedrunLiveSplit {

    public static final String MODID = "gtnhspeedrunlivesplit";
    public static final Logger LOG = LogManager.getLogger(MODID);

    @SidedProxy(
        clientSide = "com.gtnhspeedrun.livesplit.client.ClientProxy",
        serverSide = "com.gtnhspeedrun.livesplit.ServerProxy")
    public static SideProxy proxy;

    private final SplitServer splitServer = new SplitServer(LOG);

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        Config.synchronizeConfiguration(event.getSuggestedConfigurationFile());
        SplitChannel.register();
        proxy.preInit();
        if (Config.enabled) {
            splitServer.link(
                event.getModConfigurationDirectory()
                    .toPath());
        }
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        splitServer.start(event.getServer());
    }

    @Mod.EventHandler
    public void serverStopped(FMLServerStoppedEvent event) {
        splitServer.stop();
    }
}
