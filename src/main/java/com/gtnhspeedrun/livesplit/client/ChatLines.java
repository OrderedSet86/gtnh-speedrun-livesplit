package com.gtnhspeedrun.livesplit.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

/** Show the mod's lines in the runner's chat, after a gold {@code [LiveSplit]} prefix. */
public final class ChatLines {

    private ChatLines() {}

    /** Show {@code message} from any thread, on the next client tick. */
    public static void sayLater(IChatComponent message) {
        final Minecraft minecraft = Minecraft.getMinecraft();
        minecraft.func_152344_a(() -> say(minecraft, message));
    }

    /** Show {@code message}. Call on the client thread. Without a player, nothing is shown. */
    public static void say(Minecraft minecraft, IChatComponent message) {
        if (minecraft.thePlayer == null) {
            return;
        }
        final IChatComponent prefix = new ChatComponentTranslation("gtnhspeedrunlivesplit.chat.prefix");
        prefix.getChatStyle()
            .setColor(EnumChatFormatting.GOLD);
        minecraft.thePlayer
            .addChatMessage(new ChatComponentTranslation("gtnhspeedrunlivesplit.chat.line", prefix, message));
    }
}
