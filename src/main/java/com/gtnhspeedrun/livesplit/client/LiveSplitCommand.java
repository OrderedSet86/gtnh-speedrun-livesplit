package com.gtnhspeedrun.livesplit.client;

import java.util.List;
import java.util.function.IntFunction;

import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

import com.gtnhspeedrun.livesplit.split.RunSegments;
import com.gtnhspeedrun.livesplit.split.TimerCommand;

/**
 * The client command {@code /livesplit}: write a LiveSplit file of the runner's route, list the server's milestones,
 * or list the route with the run so far.
 *
 * <pre>
 *     /livesplit export
 *     /livesplit milestones 2
 *     /livesplit route
 * </pre>
 */
public final class LiveSplitCommand extends CommandBase {

    private static final String EXPORT = "export";
    private static final String MILESTONES = "milestones";
    private static final String ROUTE = "route";
    private static final int ROWS_PER_PAGE = 10;

    private final RunExport runExport;
    private final RouteTracker routeTracker;

    public LiveSplitCommand(RunExport runExport, RouteTracker routeTracker) {
        this.runExport = runExport;
        this.routeTracker = routeTracker;
    }

    @Override
    public String getCommandName() {
        return "livesplit";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "commands.gtnhspeedrunlivesplit.usage";
    }

    /**
     * Ask for an export, or show a page of milestones or segments. Before the catalog arrives, a list is one line about
     * the missing catalog.
     *
     * @throws WrongUsageException for arguments of no subcommand
     */
    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length == 1 && EXPORT.equals(args[0])) {
            runExport.request();
            return;
        }
        if (!(args.length >= 1 && args.length <= 2 && (MILESTONES.equals(args[0]) || ROUTE.equals(args[0])))) {
            throw new WrongUsageException(getCommandUsage(sender));
        }
        final RunSegments segments = routeTracker.readSegments();
        if (segments == null) {
            ChatLines
                .say(Minecraft.getMinecraft(), new ChatComponentTranslation("gtnhspeedrunlivesplit.list.no_catalog"));
            return;
        }
        if (MILESTONES.equals(args[0])) {
            final List<RunSegments.MilestoneRow> rows = segments.listMilestoneRows();
            sayPage(readPage(sender, args, rows.size()), "gtnhspeedrunlivesplit.milestones.header", index -> {
                final RunSegments.MilestoneRow row = rows.get(index);
                return row.reachTicks() == null
                    ? new ChatComponentTranslation("gtnhspeedrunlivesplit.milestones.unreached", row.id(), row.name())
                    : new ChatComponentTranslation(
                        "gtnhspeedrunlivesplit.milestones.reached",
                        row.id(),
                        row.name(),
                        TimerCommand.SetGameTime.formatGameTime(row.reachTicks()));
            });
            return;
        }
        final List<RunSegments.SegmentRow> rows = segments.listSegmentRows();
        sayPage(readPage(sender, args, rows.size()), "gtnhspeedrunlivesplit.route.header", index -> {
            final RunSegments.SegmentRow row = rows.get(index);
            return row.reachTicks() == null
                ? new ChatComponentTranslation(
                    "gtnhspeedrunlivesplit.route.unreached",
                    index + 1,
                    row.name(),
                    row.reachedCount(),
                    row.milestoneCount())
                : new ChatComponentTranslation(
                    "gtnhspeedrunlivesplit.route.reached",
                    index + 1,
                    row.name(),
                    TimerCommand.SetGameTime.formatGameTime(row.reachTicks()));
        });
    }

    /** Return the page in {@code args[1]}, or the first page. A page out of range throws a vanilla command error. */
    private static Page readPage(ICommandSender sender, String[] args, int rowCount) {
        final int pageCount = Math.max(1, (rowCount + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
        final int number = args.length == 2 ? parseIntBounded(sender, args[1], 1, pageCount) : 1;
        return new Page(number, pageCount, rowCount);
    }

    private static void sayPage(Page page, String headerKey, IntFunction<IChatComponent> formatRow) {
        final Minecraft minecraft = Minecraft.getMinecraft();
        ChatLines.say(minecraft, new ChatComponentTranslation(headerKey, page.number(), page.pageCount()));
        final int endRow = Math.min(page.rowCount(), page.number() * ROWS_PER_PAGE);
        for (int index = (page.number() - 1) * ROWS_PER_PAGE; index < endRow; index++) {
            ChatLines.say(minecraft, formatRow.apply(index));
        }
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    /** Every runner may run the command. The command changes nothing on the server. */
    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        return args.length == 1 ? getListOfStringsMatchingLastWord(args, EXPORT, MILESTONES, ROUTE) : null;
    }

    /** @param number from 1 */
    private record Page(int number, int pageCount, int rowCount) {}
}
