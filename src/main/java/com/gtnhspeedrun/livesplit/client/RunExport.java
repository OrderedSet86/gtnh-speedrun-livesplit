package com.gtnhspeedrun.livesplit.client;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.event.ClickEvent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

import org.apache.logging.log4j.Logger;

import com.gtnhspeedrun.livesplit.lss.LssRun;
import com.gtnhspeedrun.livesplit.lss.LssWriter;
import com.gtnhspeedrun.livesplit.split.ItemKey;
import com.gtnhspeedrun.livesplit.split.SplitList;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.registry.GameData;

/**
 * Write a LiveSplit file of the runner's route on this server. Each icon is drawn once, from the client's copy of the
 * item. {@link #acceptSplitList} gets the route's names and icons on join, and {@code /livesplit export} asks for a
 * file. The file is written at the end of the next frame: drawing into a separate framebuffer is safe there. Nothing
 * is drawn for icons during a run.
 *
 * <p>
 * With the JVM property {@code -Dgtnhspeedrunlivesplit.exportOnJoin=true}, {@link #acceptSplitList} asks for a file
 * too. The property is for tests without keyboard input.
 */
public final class RunExport {

    private static final String EXPORT_ON_JOIN_PROPERTY = "gtnhspeedrunlivesplit.exportOnJoin";
    private static final String FOLDER_NAME = "livesplit";

    private final Logger log;
    // set on a netty thread when the catalog arrives, cleared on disconnect
    private volatile @Nullable SplitList splitList;
    private volatile boolean requested;

    public RunExport(Logger log) {
        this.log = log;
    }

    public void acceptSplitList(SplitList received) {
        splitList = received;
        log.info(
            "splits.received splits={} category={}",
            received.labels()
                .size(),
            received.title()
                .category());
        if (Boolean.getBoolean(EXPORT_ON_JOIN_PROPERTY)) {
            requested = true;
        }
    }

    public void clear() {
        splitList = null;
    }

    /** Ask for a file after the next frame. */
    public void request() {
        requested = true;
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !requested) {
            return;
        }
        requested = false;
        export(Minecraft.getMinecraft());
    }

    private void export(Minecraft minecraft) {
        final SplitList list = splitList;
        if (list == null) {
            ChatLines.say(minecraft, new ChatComponentTranslation("gtnhspeedrunlivesplit.export.no_splits"));
            return;
        }
        final IconSet icons = drawIcons(minecraft, list);
        final List<LssRun.Segment> segments = new ArrayList<>();
        for (int index = 0; index < list.labels()
            .size(); index++) {
            segments.add(
                new LssRun.Segment(
                    list.labels()
                        .get(index)
                        .name(),
                    icons.splitPngs.get(index)));
        }
        final SplitList.Title title = list.title();
        final LssRun run = new LssRun(title.gameName(), title.category(), icons.gamePng, segments);
        final Path file;
        try {
            file = LssWriter.writeFile(
                minecraft.mcDataDir.toPath()
                    .resolve(FOLDER_NAME),
                run);
        } catch (IOException exception) {
            log.error("export.failed reason={}", exception.getMessage(), exception);
            ChatLines.say(
                minecraft,
                new ChatComponentTranslation("gtnhspeedrunlivesplit.export.failed", exception.getMessage()));
            return;
        }
        log.info(
            "export.written file={} splits={} icons={} unknownItems={} failedIcons={}",
            file,
            segments.size(),
            icons.drawn,
            icons.unknown,
            icons.failed);
        final IChatComponent fileLink = new ChatComponentText(
            file.getFileName()
                .toString());
        fileLink.getChatStyle()
            .setChatClickEvent(
                new ClickEvent(
                    ClickEvent.Action.OPEN_FILE,
                    file.toAbsolutePath()
                        .toString()))
            .setUnderlined(true);
        ChatLines.say(minecraft, new ChatComponentTranslation("gtnhspeedrunlivesplit.export.written", fileLink));
        if (!icons.unknown.isEmpty()) {
            ChatLines.say(
                minecraft,
                new ChatComponentTranslation(
                    "gtnhspeedrunlivesplit.export.icons_unknown",
                    String.join(", ", icons.unknown)));
        }
        if (!icons.failed.isEmpty()) {
            ChatLines.say(
                minecraft,
                new ChatComponentTranslation(
                    "gtnhspeedrunlivesplit.export.icons_failed",
                    String.join(", ", icons.failed)));
        }
        if (icons.noFramebuffer) {
            ChatLines.say(minecraft, new ChatComponentTranslation("gtnhspeedrunlivesplit.export.no_framebuffer"));
        }
        ChatLines.say(minecraft, new ChatComponentTranslation("gtnhspeedrunlivesplit.export.import_hint"));
    }

    /**
     * Draw the game icon and each split icon. An item the client doesn't have, or an item whose drawing throws, gets no
     * icon and is named in chat. Each item is drawn once, however many splits show it.
     */
    private IconSet drawIcons(Minecraft minecraft, SplitList list) {
        final IconSet icons = new IconSet();
        final IconCanvas canvas = IconCanvas.open(minecraft);
        if (canvas == null) {
            icons.noFramebuffer = true;
            for (int index = 0; index < list.labels()
                .size(); index++) {
                icons.splitPngs.add(null);
            }
            return icons;
        }
        final Map<ItemKey, byte[]> drawnByKey = new HashMap<>();
        try (canvas) {
            icons.gamePng = drawOnce(
                canvas,
                list.title()
                    .icon(),
                drawnByKey,
                icons);
            for (SplitList.Label label : list.labels()) {
                icons.splitPngs.add(drawOnce(canvas, label.icon(), drawnByKey, icons));
            }
        }
        icons.drawn = (int) drawnByKey.values()
            .stream()
            .filter(Objects::nonNull)
            .count();
        return icons;
    }

    @Nullable
    private byte[] drawOnce(IconCanvas canvas, @Nullable ItemKey key, Map<ItemKey, byte[]> drawnByKey, IconSet icons) {
        if (key == null) {
            return null;
        }
        if (drawnByKey.containsKey(key)) {
            return drawnByKey.get(key);
        }
        final Item item = GameData.getItemRegistry()
            .getObject(key.registryName());
        if (item == null) {
            icons.unknown.add(key.format());
            drawnByKey.put(key, null);
            return null;
        }
        byte[] png;
        try {
            png = canvas.draw(new ItemStack(item, 1, key.meta()));
        } catch (RuntimeException exception) {
            log.warn("export.icon_failed item={} reason={}", key.format(), exception.toString(), exception);
            icons.failed.add(key.format());
            png = null;
        }
        drawnByKey.put(key, png);
        return png;
    }

    /** The drawn icons of one export, and what went wrong. */
    private static final class IconSet {

        private @Nullable byte[] gamePng;
        private final List<byte[]> splitPngs = new ArrayList<>();
        private final List<String> unknown = new ArrayList<>();
        private final List<String> failed = new ArrayList<>();
        private boolean noFramebuffer;
        private int drawn;
    }
}
