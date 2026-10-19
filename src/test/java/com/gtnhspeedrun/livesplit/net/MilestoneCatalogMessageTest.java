package com.gtnhspeedrun.livesplit.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.annotation.Nullable;

import org.junit.jupiter.api.Test;

import com.gtnhspeedrun.livesplit.split.ItemKey;
import com.gtnhspeedrun.livesplit.split.MilestoneCatalog;
import com.gtnhspeedrun.livesplit.split.RunSignal;
import com.gtnhspeedrun.livesplit.split.SegmentSpec;
import com.gtnhspeedrun.livesplit.split.SplitList;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/** A catalog and each run signal read back from the wire unchanged, and a value out of range is rejected. */
class MilestoneCatalogMessageTest {

    @Test
    void catalogReadsBackUnchanged() {
        final int CASES = 300;
        final Random random = new Random(3L);
        for (int caseNumber = 0; caseNumber < CASES; caseNumber++) {
            final int milestoneCount = caseNumber == 0 ? MilestoneCatalog.MAX_MILESTONES
                : caseNumber == 1 ? 0 : random.nextInt(30);
            final List<MilestoneCatalog.Milestone> milestones = new ArrayList<>();
            for (int index = 0; index < milestoneCount; index++) {
                milestones.add(new MilestoneCatalog.Milestone("m" + index, randomText(random), randomIcon(random)));
            }
            final List<SegmentSpec> route = new ArrayList<>();
            final int segmentCount = milestoneCount == 0 ? 0 : random.nextInt(Math.min(milestoneCount, 40) + 1);
            for (int index = 0; index < segmentCount; index++) {
                final List<String> ids = new ArrayList<>();
                final int idCount = 1 + random.nextInt(3);
                for (int idIndex = 0; idIndex < idCount; idIndex++) {
                    ids.add("m" + random.nextInt(milestoneCount));
                }
                route.add(
                    new SegmentSpec(
                        random.nextBoolean() ? null : randomText(random),
                        randomIcon(random),
                        random.nextBoolean() ? SegmentSpec.Mode.ALL_OF : SegmentSpec.Mode.ANY_OF,
                        ids));
            }
            final MilestoneCatalog catalog = new MilestoneCatalog(
                new SplitList.Title(randomText(random), randomText(random), randomIcon(random)),
                milestones,
                route);
            final ByteBuf buf = Unpooled.buffer();

            MilestoneCatalogMessage.writeCatalog(buf, catalog);

            assertEquals(catalog, MilestoneCatalogMessage.readCatalog(buf));
            assertEquals(0, buf.readableBytes());
        }
    }

    @Test
    void everyRunSignalReadsBackUnchanged() {
        final String LONGEST_ID = "a".repeat(MilestoneCatalog.MAX_ID_LENGTH);
        for (RunSignal signal : List.of(
            new RunSignal.RunStarted(),
            new RunSignal.MilestoneReached(LONGEST_ID, Long.MAX_VALUE),
            new RunSignal.MilestoneReached("x", 0),
            new RunSignal.GameTime(1),
            new RunSignal.CaughtUp())) {
            final ByteBuf buf = Unpooled.buffer();
            new RunSignalMessage(signal).toBytes(buf);
            final RunSignalMessage read = new RunSignalMessage();
            read.fromBytes(buf);
            final ByteBuf again = Unpooled.buffer();
            read.toBytes(again);

            final ByteBuf expected = Unpooled.buffer();
            new RunSignalMessage(signal).toBytes(expected);
            assertEquals(expected, again);
        }
    }

    @Test
    void badMilestoneIdInARunSignalIsRejected() {
        final ByteBuf buf = Unpooled.buffer();
        ByteBufUtils.writeUTF8String(buf, "milestoneReached");
        ByteBufUtils.writeUTF8String(buf, "Coke Oven");
        buf.writeLong(1);

        assertThrows(IllegalArgumentException.class, () -> new RunSignalMessage().fromBytes(buf));
    }

    @Test
    void negativeMetaIsRejected() {
        final ByteBuf buf = Unpooled.buffer();
        ByteBufUtils.writeUTF8String(buf, "game");
        ByteBufUtils.writeUTF8String(buf, "category");
        buf.writeBoolean(true);
        ByteBufUtils.writeUTF8String(buf, "minecraft:netherrack");
        buf.writeShort(-1);

        assertThrows(IllegalArgumentException.class, () -> MilestoneCatalogMessage.readCatalog(buf));
    }

    @Test
    void tooManyMilestonesAreRejected() {
        final ByteBuf buf = Unpooled.buffer();
        ByteBufUtils.writeUTF8String(buf, "game");
        ByteBufUtils.writeUTF8String(buf, "category");
        buf.writeBoolean(false);
        buf.writeInt(MilestoneCatalog.MAX_MILESTONES + 1);

        assertThrows(IllegalArgumentException.class, () -> MilestoneCatalogMessage.readCatalog(buf));
    }

    @Test
    void unknownSegmentModeIsRejected() {
        final ByteBuf buf = Unpooled.buffer();
        ByteBufUtils.writeUTF8String(buf, "game");
        ByteBufUtils.writeUTF8String(buf, "category");
        buf.writeBoolean(false);
        buf.writeInt(0);
        buf.writeInt(1);
        buf.writeBoolean(false);
        buf.writeBoolean(false);
        ByteBufUtils.writeUTF8String(buf, "SOME_OF");

        assertThrows(IllegalArgumentException.class, () -> MilestoneCatalogMessage.readCatalog(buf));
    }

    /** A text of up to the length limit, with characters of one, two, three and four UTF-8 bytes. */
    private static String randomText(Random random) {
        final String[] PIECES = { "a", "Z", " ", "%", "é", "Ñ", "★", "月", "🔥" };
        final StringBuilder text = new StringBuilder();
        final int length = random.nextInt(SplitList.MAX_TEXT_LENGTH / 2);
        while (text.length() < length) {
            text.append(PIECES[random.nextInt(PIECES.length)]);
        }
        return text.toString();
    }

    @Nullable
    private static ItemKey randomIcon(Random random) {
        if (random.nextInt(4) == 0) {
            return null;
        }
        return new ItemKey(
            "mod" + random.nextInt(100) + ":item.n" + random.nextInt(),
            random.nextInt(ItemKey.MAX_META + 1));
    }
}
