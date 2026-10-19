package com.gtnhspeedrun.livesplit.client;

import java.nio.ByteBuffer;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.item.ItemStack;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.gtnhspeedrun.livesplit.lss.PngEncoder;

/**
 * Draw items as an inventory slot draws them, into a framebuffer off the screen, and read each one back as a PNG with
 * a transparent background. Call on the client thread while no other drawing is in progress, and close the canvas
 * after the last item: the game's own framebuffer is bound again on close.
 */
final class IconCanvas implements AutoCloseable {

    static final int SIZE_PX = 64;
    private static final int SLOT_UNITS = 16;
    private static final float ITEM_Z_LEVEL = 100;

    private final Minecraft minecraft;
    private final Framebuffer framebuffer;
    private final ByteBuffer pixels = BufferUtils.createByteBuffer(SIZE_PX * SIZE_PX * 4);

    private IconCanvas(Minecraft minecraft, Framebuffer framebuffer) {
        this.minecraft = minecraft;
        this.framebuffer = framebuffer;
    }

    /** Return a canvas, or null when framebuffers are off (Video Settings, Enable FBOs) or not supported. */
    @Nullable
    static IconCanvas open(Minecraft minecraft) {
        if (!OpenGlHelper.isFramebufferEnabled()) {
            return null;
        }
        final Framebuffer framebuffer = new Framebuffer(SIZE_PX, SIZE_PX, true);
        framebuffer.setFramebufferColor(0, 0, 0, 0);
        return new IconCanvas(minecraft, framebuffer);
    }

    /** Draw one item and return it as a PNG of {@code SIZE_PX} square. */
    byte[] draw(ItemStack stack) {
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        final RenderItem renderItem = RenderItem.getInstance();
        final float previousZLevel = renderItem.zLevel;
        try {
            framebuffer.bindFramebuffer(true);
            GL11.glClearColor(0, 0, 0, 0);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
            // one slot fills the canvas. The near and far planes match the game's GUI projection
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glLoadIdentity();
            GL11.glOrtho(0, SLOT_UNITS, SLOT_UNITS, 0, 1000, 3000);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glLoadIdentity();
            GL11.glTranslatef(0, 0, -2000);

            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glAlphaFunc(GL11.GL_GREATER, 0.1F);
            GL11.glEnable(GL11.GL_BLEND);
            // blend the colour as usual, and add up the alpha, so the canvas keeps the item's coverage
            OpenGlHelper
                .glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glColor4f(1, 1, 1, 1);
            RenderHelper.enableGUIStandardItemLighting();
            GL11.glEnable(GL12.GL_RESCALE_NORMAL);
            GL11.glEnable(GL11.GL_DEPTH_TEST);

            renderItem.zLevel = ITEM_Z_LEVEL;
            renderItem.renderItemAndEffectIntoGUI(minecraft.fontRenderer, minecraft.getTextureManager(), stack, 0, 0);

            pixels.clear();
            GL11.glReadPixels(0, 0, SIZE_PX, SIZE_PX, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        } finally {
            renderItem.zLevel = previousZLevel;
            RenderHelper.disableStandardItemLighting();
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
        return PngEncoder.encode(SIZE_PX, SIZE_PX, readTopDownStraightAlpha());
    }

    /** Delete the framebuffer and bind the game's own framebuffer again. */
    @Override
    public void close() {
        framebuffer.deleteFramebuffer();
        minecraft.getFramebuffer()
            .bindFramebuffer(true);
    }

    /**
     * Return the read pixels from the top row down. OpenGL reads from the bottom row up. A pixel drawn once over the
     * transparent background holds its colour times its alpha, and PNG wants the colour alone, so divide by the alpha.
     */
    private byte[] readTopDownStraightAlpha() {
        final int MAX_CHANNEL = 255;
        final byte[] rgba = new byte[SIZE_PX * SIZE_PX * 4];
        for (int row = 0; row < SIZE_PX; row++) {
            final int sourceRow = SIZE_PX - 1 - row;
            for (int column = 0; column < SIZE_PX; column++) {
                final int source = (sourceRow * SIZE_PX + column) * 4;
                final int target = (row * SIZE_PX + column) * 4;
                final int alpha = pixels.get(source + 3) & 0xFF;
                for (int channel = 0; channel < 3; channel++) {
                    final int value = pixels.get(source + channel) & 0xFF;
                    final int straight = alpha == 0 || alpha == MAX_CHANNEL ? value
                        : Math.min(MAX_CHANNEL, value * MAX_CHANNEL / alpha);
                    rgba[target + channel] = (byte) straight;
                }
                rgba[target + 3] = (byte) alpha;
            }
        }
        return rgba;
    }
}
