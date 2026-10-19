package com.gtnhspeedrun.livesplit.client;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;

import com.gtnhspeedrun.livesplit.Config;
import com.gtnhspeedrun.livesplit.GtnhSpeedrunLiveSplit;
import com.gtnhspeedrun.livesplit.SideProxy;
import com.gtnhspeedrun.livesplit.net.SplitChannel;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;

/**
 * Start the bridge to the runner's LiveSplit timers, and follow the run on each server the client joins, in terms of
 * the runner's route. {@code /livesplit} works with every timer target, OFF included.
 */
public final class ClientProxy implements SideProxy {

    private final RunExport runExport = new RunExport(GtnhSpeedrunLiveSplit.LOG);
    private @Nullable LiveSplitBridge bridge;
    private @Nullable RouteTracker routeTracker;

    /**
     * Start the bridge when {@link Config#timerTarget} names a timer, and route the channel's catalog and signals
     * through a {@link RouteTracker}. Register {@code /livesplit} and the event handlers.
     */
    @Override
    public void preInit() {
        final List<TimerSink> sinks = createSinks(Config.timerTarget);
        if (!sinks.isEmpty()) {
            bridge = new LiveSplitBridge(sinks, Config.skipMissedSplits, GtnhSpeedrunLiveSplit.LOG);
            bridge.start();
        }
        final LiveSplitBridge timerBridge = bridge;
        final RouteTracker tracker = new RouteTracker(
            GtnhSpeedrunLiveSplit.LOG,
            Loader.instance()
                .getConfigDir()
                .toPath(),
            timerBridge == null ? signal -> {} : timerBridge::offer);
        routeTracker = tracker;
        SplitChannel.setCatalogReceiver(
            catalog -> runExport.acceptSplitList(
                tracker.resolveRoute(catalog)
                    .describeSplits()));
        SplitChannel.setClientReceiver(tracker::acceptSignal);
        ClientCommandHandler.instance.registerCommand(new LiveSplitCommand(runExport, tracker));
        MinecraftForge.EVENT_BUS.register(this);
        FMLCommonHandler.instance()
            .bus()
            .register(this);
        FMLCommonHandler.instance()
            .bus()
            .register(runExport);
    }

    /** Say hello once the client player is in a world, when the network channel is ready. */
    @SubscribeEvent
    public void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (event.world.isRemote && event.entity == Minecraft.getMinecraft().thePlayer) {
            SplitChannel.sendHello();
        }
    }

    @SubscribeEvent
    public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        if (bridge != null) {
            bridge.clearRun();
        }
        runExport.clear();
        if (routeTracker != null) {
            routeTracker.clear();
        }
    }

    private static List<TimerSink> createSinks(Config.TimerTarget target) {
        return switch (target) {
            case OFF -> List.of();
            case DESKTOP -> List.of(new DesktopSink(Config.desktopPort));
            case ONE -> List.of(new LsoSink(Config.lsoPort));
            case BOTH -> List.of(new DesktopSink(Config.desktopPort), new LsoSink(Config.lsoPort));
        };
    }
}
