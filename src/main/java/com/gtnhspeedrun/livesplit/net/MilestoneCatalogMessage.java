package com.gtnhspeedrun.livesplit.net;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.gtnhspeedrun.livesplit.split.ItemKey;
import com.gtnhspeedrun.livesplit.split.MilestoneCatalog;
import com.gtnhspeedrun.livesplit.split.SegmentSpec;
import com.gtnhspeedrun.livesplit.split.SplitList;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** The run's milestone catalog, from the server to each client after the client's {@link HelloMessage}. */
public final class MilestoneCatalogMessage implements IMessage {

    private MilestoneCatalog catalog;

    /** FML decodes into an instance made with this constructor. */
    public MilestoneCatalogMessage() {}

    public MilestoneCatalogMessage(MilestoneCatalog catalog) {
        this.catalog = catalog;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        writeCatalog(buf, catalog);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        catalog = readCatalog(buf);
    }

    /** Write the title, each milestone, and each segment of the default route. A count comes before each list. */
    public static void writeCatalog(ByteBuf buf, MilestoneCatalog catalog) {
        final SplitList.Title title = catalog.title();
        ByteBufUtils.writeUTF8String(buf, title.gameName());
        ByteBufUtils.writeUTF8String(buf, title.category());
        writeIcon(buf, title.icon());
        buf.writeInt(
            catalog.milestones()
                .size());
        for (MilestoneCatalog.Milestone milestone : catalog.milestones()) {
            ByteBufUtils.writeUTF8String(buf, milestone.id());
            ByteBufUtils.writeUTF8String(buf, milestone.name());
            writeIcon(buf, milestone.icon());
        }
        buf.writeInt(
            catalog.defaultSplits()
                .size());
        for (SegmentSpec segment : catalog.defaultSplits()) {
            writeOptionalText(buf, segment.name());
            writeIcon(buf, segment.icon());
            ByteBufUtils.writeUTF8String(
                buf,
                segment.mode()
                    .name());
            buf.writeInt(
                segment.milestoneIds()
                    .size());
            for (String id : segment.milestoneIds()) {
                ByteBufUtils.writeUTF8String(buf, id);
            }
        }
    }

    /** Read a catalog. A count, an id, a mode or an icon out of range throws an IllegalArgumentException. */
    public static MilestoneCatalog readCatalog(ByteBuf buf) {
        final String gameName = ByteBufUtils.readUTF8String(buf);
        final String category = ByteBufUtils.readUTF8String(buf);
        final SplitList.Title title = new SplitList.Title(gameName, category, readIcon(buf));
        final int milestoneCount = readCount(buf, MilestoneCatalog.MAX_MILESTONES, "milestones");
        final List<MilestoneCatalog.Milestone> milestones = new ArrayList<>();
        for (int index = 0; index < milestoneCount; index++) {
            final String id = ByteBufUtils.readUTF8String(buf);
            final String name = ByteBufUtils.readUTF8String(buf);
            milestones.add(new MilestoneCatalog.Milestone(id, name, readIcon(buf)));
        }
        final int segmentCount = readCount(buf, SplitList.MAX_SPLITS, "default segments");
        final List<SegmentSpec> defaultSplits = new ArrayList<>();
        for (int index = 0; index < segmentCount; index++) {
            final String name = readOptionalText(buf);
            final ItemKey icon = readIcon(buf);
            final SegmentSpec.Mode mode = SegmentSpec.Mode.valueOf(ByteBufUtils.readUTF8String(buf));
            final int idCount = readCount(buf, MilestoneCatalog.MAX_MILESTONES, "segment milestones");
            final List<String> ids = new ArrayList<>();
            for (int idIndex = 0; idIndex < idCount; idIndex++) {
                ids.add(MilestoneCatalog.requireValidId(ByteBufUtils.readUTF8String(buf)));
            }
            defaultSplits.add(new SegmentSpec(name, icon, mode, ids));
        }
        return new MilestoneCatalog(title, milestones, defaultSplits);
    }

    private static int readCount(ByteBuf buf, int maxCount, String what) {
        final int count = buf.readInt();
        if (count < 0 || count > maxCount) {
            throw new IllegalArgumentException(
                "catalog with " + count + " " + what + " (expected 0 to " + maxCount + ")");
        }
        return count;
    }

    private static void writeOptionalText(ByteBuf buf, @Nullable String text) {
        buf.writeBoolean(text != null);
        if (text != null) {
            ByteBufUtils.writeUTF8String(buf, text);
        }
    }

    @Nullable
    private static String readOptionalText(ByteBuf buf) {
        return buf.readBoolean() ? ByteBufUtils.readUTF8String(buf) : null;
    }

    private static void writeIcon(ByteBuf buf, @Nullable ItemKey icon) {
        buf.writeBoolean(icon != null);
        if (icon != null) {
            ByteBufUtils.writeUTF8String(buf, icon.registryName());
            buf.writeShort(icon.meta());
        }
    }

    @Nullable
    private static ItemKey readIcon(ByteBuf buf) {
        if (!buf.readBoolean()) {
            return null;
        }
        final String registryName = ByteBufUtils.readUTF8String(buf);
        return new ItemKey(registryName, buf.readShort());
    }

    /** Pass the catalog to the client's receiver. */
    public static final class Handler implements IMessageHandler<MilestoneCatalogMessage, IMessage> {

        @Override
        public IMessage onMessage(MilestoneCatalogMessage message, MessageContext context) {
            SplitChannel.deliverCatalogToClient(message.catalog);
            return null;
        }
    }
}
