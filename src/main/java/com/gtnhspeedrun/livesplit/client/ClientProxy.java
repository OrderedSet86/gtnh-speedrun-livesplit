package com.gtnhspeedrun.livesplit.client;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;

import com.gtnhspeedrun.livesplit.Config;
import com.gtnhspeedrun.livesplit.GtnhSpeedrunLiveSplit;
import com.gtnhspeedrun.livesplit.SideProxy;
import com.gtnhspeedrun.livesplit.net.SplitChannel;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;

/**
 * Start the bridge to the runner's LiveSplit timers, and subscribe to timer signals on each server the client joins.
 */
public final class ClientProxy implements SideProxy {

    private LiveSplitBridge bridge;

    @Override
    public void preInit() {
        final List<TimerSink> sinks = createSinks(Config.timerTarget);
        if (sinks.isEmpty()) {
            return;
        }
        bridge = new LiveSplitBridge(sinks, Config.skipMissedSplits, GtnhSpeedrunLiveSplit.LOG);
        bridge.start();
        SplitChannel.setClientReceiver(bridge::offer);
        MinecraftForge.EVENT_BUS.register(this);
        FMLCommonHandler.instance()
            .bus()
            .register(this);
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
        bridge.clearRun();
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
